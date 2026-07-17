import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import {
  ApiError,
  planificarViaje,
  planificarViajeDesde,
  tramoDeLinea,
  ubicacionActual,
  type Viaje,
} from '../api/stm'
import { useRecientes } from '../hooks/almacenamiento'
import { CampoParada } from './CampoParada'
import { Mapa, type PuntoMapa, type TrazoMapa } from './Mapa'

/** Lo que se escribe en el campo cuando el origen lo pone el GPS y no el teclado. */
const TEXTO_GPS = '📍 Mi ubicación'

function metros(total: number): string {
  return total < 1000 ? `${total} m` : `${(total / 1000).toFixed(1)} km`
}

/** Una forma de hacer el viaje, con sus tramos contados como se los contarías a alguien. */
function TarjetaViaje({
  viaje,
  elegido,
  onElegir,
}: {
  viaje: Viaje
  elegido: boolean
  onElegir: () => void
}) {
  return (
    <li className={elegido ? 'viaje viaje--elegido' : 'viaje'}>
      <button type="button" className="viaje__boton" aria-pressed={elegido} onClick={onElegir}>
        <span className="viaje__lineas">
          {viaje.lineas.map((linea, i) => (
            <span key={`${linea}-${i}`} className="viaje__paso">
              {i > 0 && <span className="viaje__flecha">→</span>}
              <span className="arribo__linea">{linea}</span>
            </span>
          ))}
        </span>
        <span className="viaje__resumen">
          {viaje.transbordos === 0 ? 'Directo' : '1 transbordo'} · caminás{' '}
          {metros(viaje.metrosCaminando)}
        </span>
      </button>

      <ol className="tramos">
        {viaje.tramos.map((tramo, i) => (
          <li key={`${tramo.linea}-${i}`} className="tramo">
            <Link
              className="tramo__linea"
              to={`/lineas/${encodeURIComponent(tramo.linea)}`}
              title={`Ver el recorrido y los coches de la ${tramo.linea}`}
            >
              {tramo.linea}
            </Link>
            <span className="tramo__texto">
              Tomalo en <strong>{tramo.subida.descripcion}</strong>
              <br />
              Bajate en <strong>{tramo.bajada.descripcion}</strong>
            </span>
          </li>
        ))}
      </ol>
    </li>
  )
}

/** Cómo llego de un lado al otro. */
export function PantallaViaje() {
  /**
   * El viaje vive en la URL: buscar es escribir `?origen=…&destino=…`, y el efecto de abajo
   * la ejecuta. Así el mismo camino cubre el link compartido, el botón del form y el ir y
   * volver con el historial.
   */
  const [params, setParams] = useSearchParams()
  const origenUrl = params.get('origen') ?? ''
  const destinoUrl = params.get('destino') ?? ''

  // Los campos arrancan con lo que diga el link: puede venir solo el destino (los viajes desde
  // el GPS se comparten así) y en ese caso hay que prellenarlo aunque no se busque nada.
  const [origen, setOrigen] = useState(origenUrl)
  /** Cuando el origen sale del GPS guardamos el punto: es más preciso que cualquier texto. */
  const [origenGps, setOrigenGps] = useState<[number, number] | null>(null)
  const [destino, setDestino] = useState(destinoUrl)
  const [viajes, setViajes] = useState<Viaje[] | null>(null)
  const [elegido, setElegido] = useState(0)
  const [buscando, setBuscando] = useState(false)
  const [ubicando, setUbicando] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const destinos = useRecientes('destinos')
  const recordarDestino = destinos.recordar

  const ejecutar = useCallback(
    async (desde: string, hasta: string, gps: [number, number] | null) => {
      setBuscando(true)
      setError(null)
      setViajes(null)
      try {
        const encontrados = gps
          ? await planificarViajeDesde(gps[0], gps[1], hasta)
          : await planificarViaje(desde, hasta)
        setViajes(encontrados)
        setElegido(0)
        recordarDestino(hasta)
      } catch (causa) {
        setError(causa instanceof ApiError ? causa.message : 'No se pudo planificar el viaje.')
      } finally {
        setBuscando(false)
      }
    },
    [recordarDestino],
  )

  useEffect(() => {
    if (origenUrl.trim() === '' || destinoUrl.trim() === '') return
    // Los campos copian lo que dice la URL: con un link compartido llegan vacíos.
    setOrigen(origenUrl)
    setDestino(destinoUrl)
    setOrigenGps(null)
    void ejecutar(origenUrl, destinoUrl, null)
  }, [origenUrl, destinoUrl, ejecutar])

  function escribirOrigen(texto: string) {
    setOrigen(texto)
    // Tipear encima del origen del GPS lo invalida: el texto pasa a mandar.
    setOrigenGps(null)
  }

  async function usarMiUbicacion() {
    setUbicando(true)
    setError(null)
    try {
      const { latitude, longitude } = await ubicacionActual()
      setOrigenGps([latitude, longitude])
      setOrigen(TEXTO_GPS)
    } catch (causa) {
      setError(causa instanceof ApiError ? causa.message : 'No se pudo obtener tu ubicación.')
    } finally {
      setUbicando(false)
    }
  }

  function intercambiar() {
    setOrigen(destino)
    setDestino(origen)
    setOrigenGps(null)
  }

  function buscar(evento: React.FormEvent) {
    evento.preventDefault()
    if (destino.trim() === '' || origen.trim() === '') return

    if (origenGps !== null) {
      // El GPS no viaja en el link: tu ubicación no le sirve a quien lo reciba. En la URL va
      // solo el destino, que abre el form medio pronto para que el otro complete su origen.
      void ejecutar(origen, destino, origenGps)
      setParams({ destino })
      return
    }
    if (origen === origenUrl && destino === destinoUrl) {
      // La URL ya dice esto, así que el efecto no se va a disparar: reintento directo.
      void ejecutar(origen, destino, null)
      return
    }
    setParams({ origen, destino })
  }

  const viaje = viajes?.[elegido] ?? null

  /**
   * El camino real de cada tramo del viaje elegido: las paradas intermedias del recorrido.
   * Se pide solo para el viaje que se está mirando, no para las cinco opciones. Un tramo que
   * falla queda en null y se dibuja recto punteado, como antes de que esto existiera.
   */
  const [caminos, setCaminos] = useState<([number, number][] | null)[] | null>(null)

  useEffect(() => {
    if (viaje === null) {
      setCaminos(null)
      return
    }
    const controller = new AbortController()
    setCaminos(null)

    Promise.all(
      viaje.tramos.map((tramo) =>
        tramoDeLinea(tramo.linea, tramo.subida.codigo, tramo.bajada.codigo, controller.signal)
          .then((paradas) => {
            const camino = paradas
              .filter((p) => p.latitud !== null && p.longitud !== null)
              .map((p) => [p.latitud, p.longitud] as [number, number])
            return camino.length >= 2 ? camino : null
          })
          .catch(() => null),
      ),
    ).then((resultado) => {
      if (!controller.signal.aborted) setCaminos(resultado)
    })

    return () => controller.abort()
  }, [viaje])

  const puntos = useMemo<PuntoMapa[]>(() => {
    if (viaje === null) return []

    const marcas: PuntoMapa[] = []
    viaje.tramos.forEach((tramo, i) => {
      if (tramo.subida.latitud !== null && tramo.subida.longitud !== null) {
        marcas.push({
          clave: `sub-${i}`,
          latitud: tramo.subida.latitud,
          longitud: tramo.subida.longitud,
          etiqueta: tramo.linea,
          titulo: `Tomá el ${tramo.linea}`,
          detalle: tramo.subida.descripcion,
          tipo: 'subida',
        })
      }
      if (tramo.bajada.latitud !== null && tramo.bajada.longitud !== null) {
        marcas.push({
          clave: `baj-${i}`,
          latitud: tramo.bajada.latitud,
          longitud: tramo.bajada.longitud,
          etiqueta: '',
          titulo: `Bajate del ${tramo.linea}`,
          detalle: tramo.bajada.descripcion,
          tipo: 'bajada',
        })
      }
    })

    if (origenGps !== null) {
      marcas.push({
        clave: 'yo',
        latitud: origenGps[0],
        longitud: origenGps[1],
        etiqueta: '',
        titulo: 'Salís de acá',
        tipo: 'yo',
      })
    }
    return marcas
  }, [viaje, origenGps])

  const trazos = useMemo<TrazoMapa[]>(() => {
    if (viaje === null) return []
    return viaje.tramos
      .map((t, i): TrazoMapa | null => {
        // Con el camino real, trazo sólido siguiendo las paradas del recorrido.
        const camino = caminos?.[i] ?? null
        if (camino !== null) {
          return { clave: `tramo-${i}`, puntos: camino, solido: true }
        }
        // Sin él (todavía cargando, o la línea no une esas paradas), recto punteado.
        if (
          t.subida.latitud === null ||
          t.subida.longitud === null ||
          t.bajada.latitud === null ||
          t.bajada.longitud === null
        ) {
          return null
        }
        return {
          clave: `tramo-${i}`,
          puntos: [
            [t.subida.latitud, t.subida.longitud],
            [t.bajada.latitud, t.bajada.longitud],
          ],
        }
      })
      .filter((t): t is TrazoMapa => t !== null)
  }, [viaje, caminos])

  return (
    <>
      <form className="viaje-form" onSubmit={buscar}>
        <CampoParada
          etiqueta="Desde"
          valor={origen}
          onCambiar={escribirOrigen}
          placeholder="Gabriel Pereira y Berro"
          sugerir={origenGps === null}
          accion={
            <button
              type="button"
              className="boton"
              onClick={usarMiUbicacion}
              disabled={ubicando}
              title="Usar mi ubicación como origen"
            >
              {ubicando ? '…' : '📍'}
            </button>
          }
        />

        <button
          type="button"
          className="intercambiar"
          onClick={intercambiar}
          disabled={origenGps !== null}
          title={
            origenGps !== null
              ? 'No se puede invertir con tu ubicación como origen'
              : 'Invertir origen y destino'
          }
          aria-label="Invertir origen y destino"
        >
          ⇅
        </button>

        <CampoParada
          etiqueta="Hasta"
          valor={destino}
          onCambiar={setDestino}
          placeholder="18 de julio y ejido"
        />

        <button
          type="submit"
          className="boton boton--principal"
          disabled={buscando || origen.trim() === '' || destino.trim() === ''}
        >
          {buscando ? 'Buscando…' : 'Cómo llego'}
        </button>
      </form>

      {destinos.recientes.length > 0 && viajes === null && !buscando && (
        <section className="seccion">
          <h2 className="seccion__titulo">
            Destinos recientes
            <button type="button" className="boton-tenue" onClick={destinos.limpiar}>
              Limpiar
            </button>
          </h2>
          <div className="fichas">
            {destinos.recientes.map((texto) => (
              <button key={texto} type="button" className="ficha" onClick={() => setDestino(texto)}>
                {texto}
              </button>
            ))}
          </div>
        </section>
      )}

      {error && <p className="estado estado--error">{error}</p>}

      {viajes !== null && viajes.length === 0 && (
        <p className="estado">
          No encontramos ningún ómnibus que te lleve, ni con un transbordo. Puede que el trayecto
          sea de los que se hacen caminando, o que convenga partirlo en dos tramos vos mismo.
        </p>
      )}

      {viajes !== null && viajes.length > 0 && (
        <>
          {puntos.length > 0 && <Mapa puntos={puntos} trazos={trazos} alto="16rem" />}
          {/* Solo cuando algún tramo quedó recto: el trazo sólido ya se explica solo. */}
          {trazos.some((t) => !t.solido) && (
            <p className="nota">
              El punteado une dónde subís con dónde bajás: es la referencia de por dónde va el
              viaje, no la calle exacta que hace el bondi.
            </p>
          )}
          <ul className="viajes">
            {viajes.map((v, i) => (
              <TarjetaViaje
                key={v.lineas.join('>')}
                viaje={v}
                elegido={i === elegido}
                onElegir={() => setElegido(i)}
              />
            ))}
          </ul>
        </>
      )}
    </>
  )
}
