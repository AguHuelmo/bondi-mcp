/**
 * Cliente del backend REST.
 *
 * El frontend habla REST, no MCP: MCP es para agentes, esto es para humanos. Ambos caminos
 * terminan en la misma capa de servicio del backend.
 */

/** Pide la ubicación al navegador. Rechaza con un mensaje ya mostrable si no se puede. */
export function ubicacionActual(): Promise<GeolocationCoordinates> {
  if (!navigator.geolocation) {
    return Promise.reject(new ApiError('Tu navegador no soporta geolocalización.'))
  }
  return new Promise((resolver, rechazar) => {
    navigator.geolocation.getCurrentPosition(
      (posicion) => resolver(posicion.coords),
      (error) => {
        // Los mensajes del navegador son crípticos; traducimos a algo accionable.
        const mensajes: Record<number, string> = {
          [error.PERMISSION_DENIED]: 'No nos diste permiso para usar tu ubicación.',
          [error.POSITION_UNAVAILABLE]: 'No se pudo determinar tu ubicación.',
          [error.TIMEOUT]: 'Tardó demasiado en obtener tu ubicación.',
        }
        rechazar(new ApiError(mensajes[error.code] ?? 'No se pudo obtener tu ubicación.'))
      },
      { enableHighAccuracy: true, timeout: 10_000 },
    )
  })
}

/**
 * Lo mínimo para mostrar una parada y ubicarla en el mapa.
 *
 * Todo lo que la app hace con una parada —abrirle los arribos, marcarla favorita, pincharla en
 * el mapa— necesita solo esto, así que los componentes piden `ParadaBreve` y les sirve igual una
 * parada de la búsqueda que una de las cercanas.
 */
export type ParadaBreve = {
  codigo: number
  descripcion: string
  latitud: number | null
  longitud: number | null
}

export type Parada = ParadaBreve & {
  calle: string
  esquina: string | null
}

export type Arribo = {
  linea: string
  destino: string
  esperaEnMinutos: number
  distanciaMetros: number | null
  empresa: string | null
  /** Posición actual del bus, para verlo venir en el mapa. Puede faltar. */
  latitud: number | null
  longitud: number | null
}

/**
 * Respuesta de arribos.
 *
 * `lineasQuePasan` viene siempre, aunque `arribos` esté vacío: sale del GTFS y no de los buses
 * en circulación, así que a las 3 AM sigue diciendo qué líneas paran ahí.
 */
export type ArribosDeParada = {
  codigoParada: number
  arribos: Arribo[]
  lineasQuePasan: string[]
}

/** Parada con su distancia a un punto de referencia. */
export type ParadaCercana = ParadaBreve & {
  distanciaMetros: number
  distanciaLegible: string
}

/** El lugar que se buscó, ya ubicado: la referencia contra la que se miden las paradas cercanas. */
export type Punto = {
  latitud: number
  longitud: number
  /**
   * Cómo lo llama el padrón ("GABRIEL A. PEREIRA 2470", "ESTADIO CENTENARIO"), que casi nunca es
   * como se escribió. Es null cuando se buscó un cruce, que no tiene nombre oficial.
   */
  nombre: string | null
  /**
   * Qué clase de lugar es. `CRUCE` es el único que estimamos nosotros y puede errarle; los otros
   * dos salen del padrón.
   */
  tipo: 'DIRECCION' | 'LUGAR' | 'CRUCE'
}

/**
 * Resultado de buscar.
 *
 * `cercanasAlPunto` y `punto` solo traen algo cuando no hay parada en el lugar pedido pero se pudo
 * ubicar dónde queda: son las alternativas reales, mucho más útiles que las coincidencias de texto.
 */
export type Busqueda = {
  paradas: Parada[]
  soloAproximadas: boolean
  cercanasAlPunto: ParadaCercana[]
  punto: Punto | null
}

/** Un tramo del viaje arriba de una línea. */
export type Tramo = {
  linea: string
  subida: Parada
  bajada: Parada
}

/**
 * Una forma de hacer el viaje.
 *
 * El backend devuelve directos si los hay, y recién si no hay ninguno busca con transbordo:
 * `transbordos` es 0 o 1. `metrosCaminando` es el total a pie, puntas incluidas.
 */
export type Viaje = {
  lineas: string[]
  transbordos: number
  tramos: Tramo[]
  metrosCaminando: number
}

/** Falla que ya viene con un mensaje mostrable al usuario. */
export class ApiError extends Error {}

type ErrorBody = { mensaje?: string }

/**
 * @param errores mensajes propios por status, para las respuestas que el backend manda sin cuerpo
 *                y que en esta pantalla significan algo más preciso que "falló".
 */
async function pedir<T>(
  url: string,
  signal?: AbortSignal,
  errores?: Record<number, string>,
): Promise<T> {
  let response: Response
  try {
    response = await fetch(url, { signal, headers: { Accept: 'application/json' } })
  } catch (causa) {
    if (causa instanceof DOMException && causa.name === 'AbortError') throw causa
    // En desarrollo la causa casi siempre es el backend apagado y conviene decirlo; a un usuario
    // real eso no le sirve de nada: lo único accionable para él es su conexión y reintentar.
    throw new ApiError(
      import.meta.env.DEV
        ? 'No se pudo conectar con el servidor. ¿Está levantado el backend?'
        : 'No pudimos conectarnos. Revisá tu conexión y probá de nuevo.',
    )
  }

  if (!response.ok) {
    // El backend manda {codigo, mensaje} en los errores; si no, texto genérico.
    const cuerpo: ErrorBody = await response.json().catch(() => ({}))
    throw new ApiError(
      errores?.[response.status] ?? cuerpo.mensaje ?? `El servidor respondió ${response.status}.`,
    )
  }
  return response.json() as Promise<T>
}

export function buscarParadas(query: string, signal?: AbortSignal): Promise<Busqueda> {
  return pedir<Busqueda>(`/api/paradas?query=${encodeURIComponent(query)}`, signal)
}

/** Paradas más cercanas a un punto, ordenadas por distancia en línea recta. */
export function paradasCercanas(
  latitud: number,
  longitud: number,
  signal?: AbortSignal,
): Promise<ParadaCercana[]> {
  return pedir<ParadaCercana[]>(`/api/paradas/cercanas?lat=${latitud}&lon=${longitud}`, signal)
}

export function consultarArribos(codigo: number, signal?: AbortSignal): Promise<ArribosDeParada> {
  return pedir<ArribosDeParada>(`/api/paradas/${codigo}/arribos`, signal)
}

/** Un bus en circulación con su posición. Solo llegan los que la traen. */
export type BusVivo = {
  /** Identificador del coche, para seguirlo entre refrescos. Puede faltar. */
  id: number | null
  linea: string
  destino: string | null
  latitud: number
  longitud: number
}

/** Un sentido del recorrido, nombrado por su última parada ("hacia Portones"). */
export type SentidoDeLinea = {
  destino: string
  paradas: Parada[]
}

/** El recorrido de una línea: normalmente ida y vuelta. Vacío si no la conocemos. */
export type RecorridoDeLinea = {
  linea: string
  sentidos: SentidoDeLinea[]
}

export function recorridoDeLinea(linea: string, signal?: AbortSignal): Promise<RecorridoDeLinea> {
  return pedir<RecorridoDeLinea>(`/api/lineas/${encodeURIComponent(linea)}/recorridos`, signal)
}

/** Todos los coches de la línea en la calle ahora, en ambos sentidos. */
export function busesDeLinea(linea: string, signal?: AbortSignal): Promise<BusVivo[]> {
  return pedir<BusVivo[]>(`/api/lineas/${encodeURIComponent(linea)}/buses`, signal)
}

/**
 * Las paradas entre subida y bajada, en orden: el camino real de un tramo del planificador.
 * Vacío si la línea no une esas paradas en ese orden.
 */
export function tramoDeLinea(
  linea: string,
  subida: number,
  bajada: number,
  signal?: AbortSignal,
): Promise<Parada[]> {
  const params = new URLSearchParams({ subida: String(subida), bajada: String(bajada) })
  return pedir<Parada[]>(`/api/lineas/${encodeURIComponent(linea)}/tramo?${params}`, signal)
}

export type TipoDia = 'HABIL' | 'SABADO' | 'DOMINGO'

/**
 * Horarios teóricos de una línea en una parada.
 *
 * Los minutos cuentan desde la medianoche del día de servicio y pueden pasar de 1440: un 1470
 * es el bondi de la 00:30 que pertenece a la trasnoche del día anterior. Por eso van crudos y
 * no como "HH:mm": el orden y el "cuál es el próximo" se calculan con el número.
 */
export type HorariosDeLinea = {
  codigoParada: number
  linea: string
  porDia: { tipoDia: TipoDia; minutos: number[] }[]
}

export function consultarHorarios(
  codigo: number,
  linea: string,
  signal?: AbortSignal,
): Promise<HorariosDeLinea> {
  return pedir<HorariosDeLinea>(
    `/api/paradas/${codigo}/lineas/${encodeURIComponent(linea)}/horarios`,
    signal,
  )
}

/**
 * El 404 acá no es una falla: es que no se pudo ubicar alguna de las dos puntas. Un viaje sin
 * resultados, en cambio, responde 200 con lista vacía (existen los dos lugares, no hay bondi).
 */
const SIN_UBICAR = {
  404: 'No pudimos ubicar el origen o el destino. Probá con un cruce de calles, como "18 de julio y ejido".',
}

export function planificarViaje(
  origen: string,
  destino: string,
  signal?: AbortSignal,
): Promise<Viaje[]> {
  const params = new URLSearchParams({ origen, destino })
  return pedir<Viaje[]>(`/api/viajes?${params}`, signal, SIN_UBICAR)
}

/** Variante con el origen en coordenadas, para cuando sale del GPS. */
export function planificarViajeDesde(
  latitud: number,
  longitud: number,
  destino: string,
  signal?: AbortSignal,
): Promise<Viaje[]> {
  const params = new URLSearchParams({ lat: String(latitud), lon: String(longitud), destino })
  return pedir<Viaje[]>(`/api/viajes/desde-punto?${params}`, signal, SIN_UBICAR)
}
