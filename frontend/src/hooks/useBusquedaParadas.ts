import { useEffect, useState } from 'react'
import { ApiError, buscarParadas, type Busqueda } from '../api/stm'

/** Espera a que el usuario deje de tipear antes de buscar. */
const DEBOUNCE_MS = 300

/** Con menos letras que esto, cualquier búsqueda devuelve ruido. */
export const MIN_CARACTERES = 3

export type EstadoBusqueda = {
  busqueda: Busqueda | null
  buscando: boolean
  error: string | null
}

/**
 * Busca paradas mientras se tipea.
 *
 * @param activa en false no pide nada y limpia lo anterior; sirve para el autocompletado, que
 *               tiene que callarse apenas se elige una sugerencia y no volver a abrirse solo.
 */
export function useBusquedaParadas(consulta: string, activa = true): EstadoBusqueda {
  const [busqueda, setBusqueda] = useState<Busqueda | null>(null)
  const [buscando, setBuscando] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    const termino = consulta.trim()
    if (!activa || termino.length < MIN_CARACTERES) {
      setBusqueda(null)
      setError(null)
      setBuscando(false)
      return
    }

    const controller = new AbortController()
    const temporizador = setTimeout(async () => {
      setBuscando(true)
      try {
        setBusqueda(await buscarParadas(termino, controller.signal))
        setError(null)
      } catch (causa) {
        if (causa instanceof DOMException && causa.name === 'AbortError') return
        setBusqueda(null)
        setError(causa instanceof ApiError ? causa.message : 'Falló la búsqueda.')
      } finally {
        if (!controller.signal.aborted) setBuscando(false)
      }
    }, DEBOUNCE_MS)

    // Cada tecla cancela la búsqueda anterior: ni pedidos de más ni resultados fuera de orden.
    return () => {
      clearTimeout(temporizador)
      controller.abort()
    }
  }, [consulta, activa])

  return { busqueda, buscando, error }
}
