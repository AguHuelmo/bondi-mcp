/**
 * Cliente del backend REST.
 *
 * El frontend habla REST, no MCP: MCP es para agentes, esto es para humanos. Ambos caminos
 * terminan en la misma capa de servicio del backend.
 */

export type Parada = {
  codigo: number
  descripcion: string
  calle: string
  esquina: string | null
  latitud: number | null
  longitud: number | null
}

export type Arribo = {
  linea: string
  destino: string
  esperaEnMinutos: number
  distanciaMetros: number | null
  empresa: string | null
}

/** Falla que ya viene con un mensaje mostrable al usuario. */
export class ApiError extends Error {}

type ErrorBody = { mensaje?: string }

async function pedir<T>(url: string, signal?: AbortSignal): Promise<T> {
  let response: Response
  try {
    response = await fetch(url, { signal, headers: { Accept: 'application/json' } })
  } catch (causa) {
    if (causa instanceof DOMException && causa.name === 'AbortError') throw causa
    throw new ApiError('No se pudo conectar con el servidor. ¿Está levantado el backend?')
  }

  if (!response.ok) {
    // El backend manda {codigo, mensaje} en los errores; si no, texto genérico.
    const cuerpo: ErrorBody = await response.json().catch(() => ({}))
    throw new ApiError(cuerpo.mensaje ?? `El servidor respondió ${response.status}.`)
  }
  return response.json() as Promise<T>
}

export function buscarParadas(query: string, signal?: AbortSignal): Promise<Parada[]> {
  return pedir<Parada[]>(`/api/paradas?query=${encodeURIComponent(query)}`, signal)
}

export function consultarArribos(codigo: number, signal?: AbortSignal): Promise<Arribo[]> {
  return pedir<Arribo[]>(`/api/paradas/${codigo}/arribos`, signal)
}
