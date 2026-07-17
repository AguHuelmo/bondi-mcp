import { useEffect, useMemo, useState } from 'react'
import { useNavigate, useParams } from 'react-router'
import { ApiError, recorridoDeLinea, type RecorridoDeLinea } from '../api/stm'
import { useBusesEnVivo } from '../hooks/useBusesEnVivo'
import { Mapa, type PuntoMapa, type TrazoMapa } from './Mapa'

/**
 * Una línea entera: por dónde va y dónde están sus coches ahora.
 *
 * El recorrido sale del GTFS y se pide una vez; los buses son la parte viva y se refrescan
 * solos. Se muestra un sentido por vez (los dos superpuestos son ilegibles: van por calles
 * paralelas), pero los coches se ven todos, porque distinguir el sentido de un bus por su
 * posición no es confiable.
 */
export function PantallaLinea() {
  const { linea = '' } = useParams()
  const navegar = useNavigate()

  const [recorrido, setRecorrido] = useState<RecorridoDeLinea | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [sentidoElegido, setSentidoElegido] = useState(0)
  const { buses, error: errorBuses } = useBusesEnVivo(linea === '' ? null : linea)

  const lineaMostrada = linea.toUpperCase()

  useEffect(() => {
    const controller = new AbortController()
    setRecorrido(null)
    setError(null)
    setSentidoElegido(0)

    recorridoDeLinea(linea, controller.signal)
      .then(setRecorrido)
      .catch((causa: unknown) => {
        if (causa instanceof DOMException && causa.name === 'AbortError') return
        setError(causa instanceof ApiError ? causa.message : 'No se pudo obtener el recorrido.')
      })

    return () => controller.abort()
  }, [linea])

  const sentido = recorrido?.sentidos[sentidoElegido] ?? recorrido?.sentidos[0] ?? null

  const puntos = useMemo<PuntoMapa[]>(() => {
    const marcas: PuntoMapa[] = []

    if (sentido !== null) {
      const conUbicacion = sentido.paradas.filter((p) => p.latitud !== null && p.longitud !== null)
      conUbicacion.forEach((parada, i) => {
        const terminal = i === conUbicacion.length - 1
        marcas.push({
          clave: `parada-${parada.codigo}`,
          latitud: parada.latitud as number,
          longitud: parada.longitud as number,
          etiqueta: '',
          titulo: terminal ? `Terminal: ${parada.descripcion}` : parada.descripcion,
          detalle: `Parada #${parada.codigo}`,
          tipo: terminal ? 'bajada' : 'parada',
        })
      })
    }

    const enCalle = buses ?? []
    enCalle.forEach((bus, i) => {
      marcas.push({
        clave: `bus-${bus.id ?? i}`,
        latitud: bus.latitud,
        longitud: bus.longitud,
        etiqueta: bus.linea,
        titulo: bus.destino ? `${bus.linea} → ${bus.destino}` : `Línea ${bus.linea}`,
        detalle: 'Posición de hace unos segundos',
        tipo: 'bus',
      })
    })

    return marcas
  }, [sentido, buses])

  const trazos = useMemo<TrazoMapa[]>(() => {
    if (sentido === null) return []
    const camino = sentido.paradas
      .filter((p) => p.latitud !== null && p.longitud !== null)
      .map((p) => [p.latitud, p.longitud] as [number, number])
    return camino.length >= 2 ? [{ clave: `recorrido-${sentidoElegido}`, puntos: camino, solido: true }] : []
  }, [sentido, sentidoElegido])

  return (
    <>
      <div className="linea-cabecera">
        <button type="button" className="boton-tenue" onClick={() => navegar(-1)}>
          ‹ Volver
        </button>
        <h2 className="linea-cabecera__titulo">
          Línea <span className="arribo__linea">{lineaMostrada}</span>
        </h2>
      </div>

      {error && <p className="estado estado--error">{error}</p>}
      {!error && recorrido === null && <p className="estado">Buscando el recorrido…</p>}

      {recorrido !== null && recorrido.sentidos.length === 0 && (
        <p className="estado">
          No conocemos el recorrido de la {lineaMostrada}. Puede que el GTFS todavía no se haya
          importado, o que la línea no exista.
        </p>
      )}

      {recorrido !== null && recorrido.sentidos.length > 0 && (
        <>
          {recorrido.sentidos.length > 1 && (
            <div className="sentidos" role="tablist" aria-label="Sentido del recorrido">
              {recorrido.sentidos.map((s, i) => (
                <button
                  key={`${s.destino}-${i}`}
                  type="button"
                  role="tab"
                  aria-selected={i === sentidoElegido}
                  className={i === sentidoElegido ? 'sentido sentido--activo' : 'sentido'}
                  onClick={() => setSentidoElegido(i)}
                >
                  → {s.destino}
                </button>
              ))}
            </div>
          )}

          <p className="estado">
            {buses === null && 'Buscando los coches en la calle…'}
            {buses !== null && buses.length === 0 && 'Ningún coche en la calle en este momento.'}
            {buses !== null && buses.length === 1 && '1 coche en la calle ahora, en ambos sentidos.'}
            {buses !== null && buses.length > 1 &&
              `${buses.length} coches en la calle ahora, contando ambos sentidos.`}
          </p>
          {errorBuses && buses === null && <p className="estado estado--error">{errorBuses}</p>}

          <Mapa puntos={puntos} trazos={trazos} alto="24rem" />

          {sentido !== null && (
            <details className="paradas-plegadas">
              <summary>Las {sentido.paradas.length} paradas hacia {sentido.destino}</summary>
              <ol className="paradas-plegadas__lista">
                {sentido.paradas.map((parada) => (
                  <li key={parada.codigo}>
                    {parada.descripcion} <span className="paradas-plegadas__codigo">#{parada.codigo}</span>
                  </li>
                ))}
              </ol>
            </details>
          )}
        </>
      )}
    </>
  )
}
