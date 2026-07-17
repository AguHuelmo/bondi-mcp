import { useEffect, useState } from 'react'
import { ApiError, busesDeLinea, type BusVivo } from '../api/stm'

/**
 * Más seguido que los arribos (30 s): acá lo que se mira es el bus moviéndose, y con updates
 * más lentos parece clavado.
 */
const REFRESCO_MS = 15_000

export type EstadoBuses = {
  /** null mientras carga la primera vez; [] es "no hay ningún coche en la calle". */
  buses: BusVivo[] | null
  error: string | null
}

/**
 * Los coches de una línea en la calle, al día solos.
 *
 * Si un refresco falla, los buses anteriores quedan en pantalla: una posición de hace 15
 * segundos sigue siendo mejor que un mapa vacío. Con la pestaña en segundo plano no se pide
 * nada; al volver se refresca enseguida.
 */
export function useBusesEnVivo(linea: string | null): EstadoBuses {
  const [buses, setBuses] = useState<BusVivo[] | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (linea === null) {
      setBuses(null)
      setError(null)
      return
    }

    const controller = new AbortController()

    const pedir = () => {
      busesDeLinea(linea, controller.signal)
        .then((nuevos) => {
          setBuses(nuevos)
          setError(null)
        })
        .catch((causa: unknown) => {
          if (causa instanceof DOMException && causa.name === 'AbortError') return
          setError(
            causa instanceof ApiError ? causa.message : 'No se pudo saber dónde están los coches.',
          )
        })
    }

    const siVisible = () => {
      if (document.visibilityState === 'visible') pedir()
    }

    pedir()
    const intervalo = setInterval(siVisible, REFRESCO_MS)
    document.addEventListener('visibilitychange', siVisible)

    return () => {
      controller.abort()
      clearInterval(intervalo)
      document.removeEventListener('visibilitychange', siVisible)
    }
  }, [linea])

  return { buses, error }
}
