import { useCallback, useEffect, useState } from 'react'
import { ApiError, consultarArribos, type ArribosDeParada } from '../api/stm'

/** Cada cuánto se repiten los arribos solos. */
const REFRESCO_MS = 30_000

/** Cada cuánto se recalcula el "hace X" del pie. */
const RELOJ_MS = 10_000

export type EstadoArribos = {
  datos: ArribosDeParada | null
  error: string | null
  /** Primera carga: todavía no hay nada que mostrar. */
  cargando: boolean
  /** Hay datos en pantalla y se están pidiendo unos nuevos. */
  refrescando: boolean
  actualizado: Date | null
  refrescar: () => void
}

/**
 * Arribos de una parada, al día solos.
 *
 * Un arribo a 3 minutos deja de ser cierto en 3 minutos: si esto no se refrescara, la pantalla
 * quedaría mintiendo con cara de dato en vivo. Al refrescar se mantienen los arribos viejos en
 * pantalla hasta que llegan los nuevos; parpadear a "Consultando…" cada 30 segundos haría la
 * lista imposible de leer.
 */
export function useArribos(codigo: number): EstadoArribos {
  const [datos, setDatos] = useState<ArribosDeParada | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [refrescando, setRefrescando] = useState(false)
  const [actualizado, setActualizado] = useState<Date | null>(null)
  // Tocarlo dispara un pedido nuevo. Lo tocan el botón, el intervalo y el volver a la pestaña.
  const [pedido, setPedido] = useState(0)

  const refrescar = useCallback(() => setPedido((n) => n + 1), [])

  useEffect(() => {
    const controller = new AbortController()
    setRefrescando(true)

    consultarArribos(codigo, controller.signal)
      .then((nuevos) => {
        setDatos(nuevos)
        setError(null)
        setActualizado(new Date())
      })
      .catch((causa: unknown) => {
        if (causa instanceof DOMException && causa.name === 'AbortError') return
        // Si ya había datos, el error no los tapa: unos arribos de hace 30 segundos valen más
        // que un cartel rojo.
        setError(causa instanceof ApiError ? causa.message : 'No se pudieron obtener los arribos.')
      })
      .finally(() => {
        if (!controller.signal.aborted) setRefrescando(false)
      })

    return () => controller.abort()
  }, [codigo, pedido])

  useEffect(() => {
    // Con la pestaña en segundo plano no hay nadie mirando: no gastamos pedidos contra la API de
    // la Intendencia. Al volver refrescamos enseguida, porque lo que quedó en pantalla ya venció.
    const alVolver = () => {
      if (document.visibilityState === 'visible') refrescar()
    }
    const intervalo = setInterval(alVolver, REFRESCO_MS)
    document.addEventListener('visibilitychange', alVolver)

    return () => {
      clearInterval(intervalo)
      document.removeEventListener('visibilitychange', alVolver)
    }
  }, [refrescar])

  return {
    datos,
    error,
    cargando: datos === null && error === null,
    refrescando,
    actualizado,
    refrescar,
  }
}

/** Texto del tipo "hace 40 s", que se actualiza solo mientras esté en pantalla. */
export function useHace(momento: Date | null): string | null {
  const [, tic] = useState(0)

  useEffect(() => {
    if (momento === null) return
    const intervalo = setInterval(() => tic((n) => n + 1), RELOJ_MS)
    return () => clearInterval(intervalo)
  }, [momento])

  if (momento === null) return null

  const segundos = Math.max(0, Math.round((Date.now() - momento.getTime()) / 1000))
  if (segundos < 10) return 'recién'
  if (segundos < 60) return `hace ${segundos} s`
  const minutos = Math.round(segundos / 60)
  return minutos === 1 ? 'hace 1 min' : `hace ${minutos} min`
}
