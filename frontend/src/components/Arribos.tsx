import { useEffect, useState } from 'react'
import type { Arribo, ParadaBreve } from '../api/stm'
import { useArribos, useHace } from '../hooks/useArribos'
import { HorariosLinea } from './HorariosLinea'

/** Los que están por llegar se destacan: son los únicos que se pueden alcanzar corriendo. */
const MINUTOS_INMINENTE = 3

export function Arribos({
  parada,
  onArribos,
}: {
  parada: ParadaBreve
  /** Avisa cada vez que llegan arribos nuevos: es lo que deja dibujar los buses en el mapa. */
  onArribos?: (arribos: Arribo[]) => void
}) {
  const { datos, error, cargando, refrescando, actualizado, refrescar } = useArribos(parada.codigo)
  const hace = useHace(actualizado)
  const [lineaAbierta, setLineaAbierta] = useState<string | null>(null)

  useEffect(() => {
    if (datos !== null) onArribos?.(datos.arribos)
  }, [datos, onArribos])

  if (cargando) return <p className="estado">Consultando arribos…</p>
  if (datos === null) return <p className="estado estado--error">{error}</p>

  return (
    <div className="arribos-panel">
      {/* Con datos viejos en pantalla el error va como aviso y no los reemplaza. */}
      {error && <p className="estado estado--error">{error}</p>}

      {datos.arribos.length > 0 ? (
        <ul className="arribos">
          {datos.arribos.map((arribo, i) => (
            <li
              key={`${arribo.linea}-${arribo.destino}-${i}`}
              className={
                arribo.esperaEnMinutos <= MINUTOS_INMINENTE ? 'arribo arribo--ya' : 'arribo'
              }
            >
              <span className="arribo__linea">{arribo.linea}</span>
              <span className="arribo__destino">
                <span className="arribo__destino-nombre">{arribo.destino}</span>
                {arribo.distanciaMetros !== null && (
                  <span className="arribo__distancia">a {arribo.distanciaMetros} m de acá</span>
                )}
              </span>

              {/* El número va suelto de su unidad para poder agrandarlo solo a él: "8" grande y
                  "min" chico se lee de un vistazo, "8 min" todo junto y grande grita. */}
              {arribo.esperaEnMinutos <= 0 ? (
                <span className="arribo__ya">Llegando</span>
              ) : (
                <span className="arribo__espera">
                  <span className="arribo__minutos">{arribo.esperaEnMinutos}</span>
                  <span className="arribo__unidad">min</span>
                </span>
              )}
            </li>
          ))}
        </ul>
      ) : (
        <p className="estado">No viene ningún ómnibus en este momento.</p>
      )}

      {/* Las líneas salen del GTFS: están aunque no venga ningún bus, que es justo cuando
          más sirven para decidir si esperar o caminar hasta otra parada. */}
      {datos.lineasQuePasan.length > 0 && (
        <>
          <div className="lineas">
            <span className="lineas__titulo">
              {datos.arribos.length > 0 ? 'Todas las líneas de esta parada' : 'Por acá pasan'}
              <span className="lineas__ayuda"> — tocá una para ver sus horarios</span>
            </span>
            <span className="lineas__lista">
              {datos.lineasQuePasan.map((linea) => {
                const abierta = linea === lineaAbierta
                return (
                  <button
                    key={linea}
                    type="button"
                    className={abierta ? 'lineas__linea lineas__linea--abierta' : 'lineas__linea'}
                    aria-expanded={abierta}
                    onClick={() => setLineaAbierta(abierta ? null : linea)}
                  >
                    {linea}
                  </button>
                )
              })}
            </span>
          </div>

          {lineaAbierta !== null && (
            <HorariosLinea
              codigoParada={parada.codigo}
              linea={lineaAbierta}
              onCerrar={() => setLineaAbierta(null)}
            />
          )}
        </>
      )}

      <div className="arribos-panel__pie">
        <span className="arribos-panel__sello">
          {refrescando ? 'Actualizando…' : hace && `Actualizado ${hace}`}
        </span>
        <button
          type="button"
          className="boton-tenue"
          onClick={refrescar}
          disabled={refrescando}
          aria-label="Actualizar arribos"
        >
          ↻ Actualizar
        </button>
      </div>
    </div>
  )
}
