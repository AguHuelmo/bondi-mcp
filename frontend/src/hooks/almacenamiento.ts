/**
 * Lo que la app recuerda entre visitas.
 *
 * Todo vive en localStorage: no hay usuarios ni backend donde guardarlo, y para favoritos y
 * búsquedas recientes alcanza de sobra.
 */

import { useCallback, useEffect, useState } from 'react'
import type { ParadaBreve } from '../api/stm'

/** Cuántas búsquedas recientes recordar. Más que esto deja de ser un atajo y pasa a ser una lista. */
const MAXIMAS_RECIENTES = 5

/**
 * Estado sincronizado con localStorage.
 *
 * Lee una sola vez al montar. Si lo guardado está corrupto o es de una versión vieja del formato,
 * se descarta en silencio: perder favoritos es molesto, pero romper la app al abrirla es peor.
 */
function usePersistido<T>(clave: string, inicial: T): [T, (valor: T | ((previo: T) => T)) => void] {
  const [valor, setValor] = useState<T>(() => {
    try {
      const crudo = localStorage.getItem(clave)
      return crudo === null ? inicial : (JSON.parse(crudo) as T)
    } catch {
      return inicial
    }
  })

  useEffect(() => {
    try {
      localStorage.setItem(clave, JSON.stringify(valor))
    } catch {
      // Modo privado o cuota llena: seguir andando sin persistir es mejor que explotar.
    }
  }, [clave, valor])

  return [valor, setValor]
}

export type Favoritos = {
  favoritos: ParadaBreve[]
  esFavorita: (codigo: number) => boolean
  alternar: (parada: ParadaBreve) => void
}

export function useFavoritos(): Favoritos {
  const [favoritos, setFavoritos] = usePersistido<ParadaBreve[]>('bondis.favoritos', [])

  const esFavorita = useCallback(
    (codigo: number) => favoritos.some((f) => f.codigo === codigo),
    [favoritos],
  )

  const alternar = useCallback(
    (parada: ParadaBreve) =>
      setFavoritos((previos) =>
        previos.some((f) => f.codigo === parada.codigo)
          ? previos.filter((f) => f.codigo !== parada.codigo)
          : [...previos, parada],
      ),
    [setFavoritos],
  )

  return { favoritos, esFavorita, alternar }
}

export type Recientes = {
  recientes: string[]
  recordar: (texto: string) => void
  limpiar: () => void
}

export function useRecientes(clave: string): Recientes {
  const [recientes, setRecientes] = usePersistido<string[]>(`bondis.recientes.${clave}`, [])

  const recordar = useCallback(
    (texto: string) => {
      const limpio = texto.trim()
      if (limpio === '') return
      // Sin distinguir mayúsculas: "18 de Julio" y "18 de julio" son la misma búsqueda, y verla
      // dos veces en la lista no le sirve a nadie. Gana la escritura más reciente.
      setRecientes((previos) => {
        const otros = previos.filter((p) => p.toLowerCase() !== limpio.toLowerCase())
        return [limpio, ...otros].slice(0, MAXIMAS_RECIENTES)
      })
    },
    [setRecientes],
  )

  const limpiar = useCallback(() => setRecientes([]), [setRecientes])

  return { recientes, recordar, limpiar }
}
