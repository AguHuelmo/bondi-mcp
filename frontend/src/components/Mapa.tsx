/**
 * Mapa de paradas.
 *
 * Una lista de paradas con nombres de esquinas no dice si la parada está en tu vereda o cruzando
 * la avenida; el mapa sí. Es la misma info que la lista, mirada de la única forma que sirve para
 * decidir hacia dónde caminar.
 */

import { useEffect, useMemo, useState } from 'react'
import L from 'leaflet'
import { MapContainer, Marker, Polyline, Popup, TileLayer, useMap } from 'react-leaflet'
import 'leaflet/dist/leaflet.css'

/** Centro de Montevideo: dónde se para el mapa cuando todavía no hay nada que mostrar. */
const CENTRO: [number, number] = [-34.9011, -56.1645]

const ZOOM_INICIAL = 13

/** Encuadrar un solo punto llevaría el zoom al máximo y se perdería el contexto de la cuadra. */
const ZOOM_MAXIMO = 17

export type TipoPunto =
  | 'parada'
  | 'seleccionada'
  | 'favorita'
  | 'subida'
  | 'bajada'
  | 'yo'
  | 'bus'
  /** El lugar que se buscó: la dirección o el cruce, no una parada. */
  | 'lugar'

export type PuntoMapa = {
  clave: string
  latitud: number
  longitud: number
  /** Lo que va escrito adentro del pin: una línea, una letra, un ícono. Corto o no entra. */
  etiqueta: string
  titulo: string
  detalle?: string
  tipo: TipoPunto
  onClick?: () => void
}

export type TrazoMapa = {
  clave: string
  puntos: [number, number][]
  /**
   * Sólido cuando los puntos siguen las paradas reales del recorrido; punteado cuando son
   * solo los extremos unidos en línea recta y no hay que prometer que el bondi va derecho.
   */
  solido?: boolean
}

/**
 * Pin dibujado con CSS.
 *
 * Con `divIcon` en vez de los íconos de imagen de Leaflet: se estilan con las mismas variables de
 * color que el resto de la app, cambian solos en modo oscuro y no hay assets que empaquetar.
 */
function icono(punto: PuntoMapa): L.DivIcon {
  return L.divIcon({
    className: 'pin-envoltorio',
    html: `<span class="pin pin--${punto.tipo}">${escaparHtml(punto.etiqueta)}</span>`,
    iconSize: [30, 30],
    iconAnchor: [15, 15],
    popupAnchor: [0, -15],
  })
}

function escaparHtml(texto: string): string {
  const div = document.createElement('div')
  div.textContent = texto
  return div.innerHTML
}

/** Mueve el mapa para que entre todo lo que hay que mostrar. */
function Encuadre({ firma, puntos }: { firma: string; puntos: PuntoMapa[] }) {
  const mapa = useMap()

  useEffect(() => {
    if (puntos.length === 0) return
    mapa.fitBounds(
      L.latLngBounds(puntos.map((p) => [p.latitud, p.longitud] as [number, number])),
      { padding: [40, 40], maxZoom: ZOOM_MAXIMO },
    )
    // Depende de la firma y no del array: `puntos` es nuevo en cada render del padre y el mapa
    // saltaría solo cada vez que se refrescan los arribos.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [mapa, firma])

  return null
}

function useModoOscuro(): boolean {
  const [oscuro, setOscuro] = useState(
    () => window.matchMedia?.('(prefers-color-scheme: dark)').matches ?? false,
  )

  useEffect(() => {
    const consulta = window.matchMedia('(prefers-color-scheme: dark)')
    const alCambiar = (e: MediaQueryListEvent) => setOscuro(e.matches)
    consulta.addEventListener('change', alCambiar)
    return () => consulta.removeEventListener('change', alCambiar)
  }, [])

  return oscuro
}

export function Mapa({
  puntos,
  trazos = [],
  alto = '18rem',
}: {
  puntos: PuntoMapa[]
  trazos?: TrazoMapa[]
  alto?: string
}) {
  const oscuro = useModoOscuro()
  const firma = useMemo(() => puntos.map((p) => p.clave).join('|'), [puntos])

  const tiles = oscuro
    ? 'https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png'
    : 'https://{s}.basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}{r}.png'

  return (
    <div className="mapa" style={{ height: alto }}>
      <MapContainer center={CENTRO} zoom={ZOOM_INICIAL} scrollWheelZoom className="mapa__lienzo">
        {/* La key fuerza a Leaflet a recargar los tiles al cambiar de tema; sin esto quedan los viejos. */}
        <TileLayer
          key={oscuro ? 'oscuro' : 'claro'}
          url={tiles}
          attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> &copy; <a href="https://carto.com/attributions">CARTO</a>'
        />

        {trazos.map((trazo) => (
          <Polyline
            key={trazo.clave}
            positions={trazo.puntos}
            pathOptions={
              trazo.solido
                ? { color: '#1d4ed8', weight: 4, opacity: 0.85 }
                : { color: '#1d4ed8', weight: 4, opacity: 0.7, dashArray: '8 8' }
            }
          />
        ))}

        {puntos.map((punto) => (
          <Marker
            key={punto.clave}
            position={[punto.latitud, punto.longitud]}
            icon={icono(punto)}
            eventHandlers={punto.onClick ? { click: punto.onClick } : undefined}
          >
            <Popup>
              <strong>{punto.titulo}</strong>
              {punto.detalle && <div className="mapa__detalle">{punto.detalle}</div>}
            </Popup>
          </Marker>
        ))}

        <Encuadre firma={firma} puntos={puntos} />
      </MapContainer>
    </div>
  )
}
