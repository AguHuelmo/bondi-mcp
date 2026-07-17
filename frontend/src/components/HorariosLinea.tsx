import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router'
import { ApiError, consultarHorarios, type HorariosDeLinea, type TipoDia } from '../api/stm'

const DIAS: { id: TipoDia; titulo: string }[] = [
  { id: 'HABIL', titulo: 'Hábiles' },
  { id: 'SABADO', titulo: 'Sábados' },
  { id: 'DOMINGO', titulo: 'Domingos' },
]

function tipoDiaDeHoy(): TipoDia {
  const dia = new Date().getDay()
  if (dia === 6) return 'SABADO'
  if (dia === 0) return 'DOMINGO'
  return 'HABIL'
}

/** "HH:mm". Los minutos de trasnoche (>= 1440) vuelven a empezar: 1470 es "00:30". */
function aHora(minuto: number): string {
  const horas = Math.floor(minuto / 60) % 24
  const minutos = minuto % 60
  return `${String(horas).padStart(2, '0')}:${String(minutos).padStart(2, '0')}`
}

/**
 * Horarios teóricos de una línea en una parada.
 *
 * Es la respuesta cuando los arribos no alcanzan: de noche, con la línea sin unidades en la
 * calle, o para planificar mañana. Programados, no tiempo real: el bondi puede pasar unos
 * minutos antes o después.
 */
export function HorariosLinea({
  codigoParada,
  linea,
  onCerrar,
}: {
  codigoParada: number
  linea: string
  onCerrar: () => void
}) {
  const [datos, setDatos] = useState<HorariosDeLinea | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [dia, setDia] = useState<TipoDia>(tipoDiaDeHoy)

  useEffect(() => {
    const controller = new AbortController()
    setDatos(null)
    setError(null)

    consultarHorarios(codigoParada, linea, controller.signal)
      .then(setDatos)
      .catch((causa: unknown) => {
        if (causa instanceof DOMException && causa.name === 'AbortError') return
        setError(causa instanceof ApiError ? causa.message : 'No se pudieron obtener los horarios.')
      })

    return () => controller.abort()
  }, [codigoParada, linea])

  const disponibles = useMemo(
    () => DIAS.filter((d) => datos?.porDia.some((p) => p.tipoDia === d.id && p.minutos.length > 0)),
    [datos],
  )

  // El día elegido puede no tener horarios (una línea que no corre domingos): cae al primero
  // que sí tenga, sin perder la preferencia por "hoy" cuando existe.
  const diaVisible = disponibles.some((d) => d.id === dia) ? dia : disponibles[0]?.id
  const minutos = datos?.porDia.find((p) => p.tipoDia === diaVisible)?.minutos ?? []

  // El próximo solo tiene sentido mirando el día de hoy: mañana "todos" son próximos.
  const ahora = new Date().getHours() * 60 + new Date().getMinutes()
  const proximo = diaVisible === tipoDiaDeHoy() ? minutos.find((m) => m >= ahora) : undefined

  return (
    <div className="horarios">
      <div className="horarios__cabecera">
        <span className="horarios__titulo">
          Horarios de la <span className="arribo__linea">{linea}</span>
        </span>
        <span className="horarios__acciones">
          <Link className="boton-tenue" to={`/lineas/${encodeURIComponent(linea)}`}>
            🗺 Recorrido
          </Link>
          <button type="button" className="boton-tenue" onClick={onCerrar}>
            ✕ Cerrar
          </button>
        </span>
      </div>

      {error && <p className="estado estado--error">{error}</p>}
      {!error && datos === null && <p className="estado">Consultando horarios…</p>}

      {datos !== null && disponibles.length === 0 && (
        <p className="estado">
          No tenemos horarios de la {linea} en esta parada. Puede que todavía no se hayan
          importado, o que la línea no pase por acá.
        </p>
      )}

      {disponibles.length > 0 && (
        <>
          <div className="horarios__dias" role="tablist" aria-label="Tipo de día">
            {disponibles.map((d) => (
              <button
                key={d.id}
                type="button"
                role="tab"
                aria-selected={d.id === diaVisible}
                className={d.id === diaVisible ? 'horarios__dia horarios__dia--activo' : 'horarios__dia'}
                onClick={() => setDia(d.id)}
              >
                {d.titulo}
              </button>
            ))}
          </div>

          <ul className="horarios__grilla">
            {minutos.map((minuto) => (
              <li
                key={minuto}
                className={
                  minuto === proximo ? 'horarios__hora horarios__hora--proxima' : 'horarios__hora'
                }
              >
                {aHora(minuto)}
              </li>
            ))}
          </ul>

          <p className="horarios__nota">
            Horarios programados: el bondi puede pasar unos minutos antes o después.
            {minutos.some((m) => m >= 1440) &&
              ' Las horas chicas del final (00:xx) son la trasnoche de ese día.'}
          </p>
        </>
      )}
    </div>
  )
}
