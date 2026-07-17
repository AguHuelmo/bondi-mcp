import { useCallback, useEffect, useMemo, useState } from 'react'
import { useSearchParams } from 'react-router'
import {
  ApiError,
  paradasCercanas,
  ubicacionActual,
  type Arribo,
  type ParadaBreve,
  type ParadaCercana,
} from '../api/stm'
import type { Favoritos } from '../hooks/almacenamiento'
import { useRecientes } from '../hooks/almacenamiento'
import { MIN_CARACTERES, useBusquedaParadas } from '../hooks/useBusquedaParadas'
import { ListaParadas, type ParadaListable } from './ListaParadas'
import { Mapa, type PuntoMapa } from './Mapa'

/** Qué parada de ómnibus tengo cerca y cuándo pasa el próximo bondi. */
export function PantallaParadas({ favoritos }: { favoritos: Favoritos }) {
  // La búsqueda se refleja en `?q=` para que el link sea compartible; el estado local sigue
  // mandando mientras se tipea y la URL solo lo copia.
  const [params, setParams] = useSearchParams()
  const [consulta, setConsulta] = useState(() => params.get('q') ?? '')
  const [cercanas, setCercanas] = useState<ParadaCercana[] | null>(null)
  const [miUbicacion, setMiUbicacion] = useState<[number, number] | null>(null)
  const [seleccionada, setSeleccionada] = useState<ParadaBreve | null>(null)
  const [ubicando, setUbicando] = useState(false)
  const [errorUbicacion, setErrorUbicacion] = useState<string | null>(null)
  const [conMapa, setConMapa] = useState(true)

  // Los arribos de la parada abierta, que ahora traen dónde viene cada bus. Los sube el
  // componente de arribos con cada refresco, así el mapa se mueve al mismo ritmo que la lista.
  const [queVienen, setQueVienen] = useState<Arribo[]>([])
  const alRecibirArribos = useCallback((arribos: Arribo[]) => setQueVienen(arribos), [])

  // Al cambiar de parada (o cerrarla) los buses de la anterior no tienen nada que hacer acá.
  useEffect(() => setQueVienen([]), [seleccionada])

  const recientes = useRecientes('paradas')
  const { busqueda, buscando, error: errorBusqueda } = useBusquedaParadas(consulta)

  const buscandoTexto = consulta.trim().length >= MIN_CARACTERES

  async function buscarCerca() {
    setUbicando(true)
    setErrorUbicacion(null)
    try {
      const { latitude, longitude } = await ubicacionActual()
      setCercanas(await paradasCercanas(latitude, longitude))
      setMiUbicacion([latitude, longitude])
      setConsulta('')
      setParams({}, { replace: true })
      setSeleccionada(null)
    } catch (causa) {
      setErrorUbicacion(causa instanceof ApiError ? causa.message : 'No se pudo buscar cerca tuyo.')
    } finally {
      setUbicando(false)
    }
  }

  function escribir(texto: string) {
    setConsulta(texto)
    // Lo que se tipea manda: dejar abajo las cercanas de la búsqueda anterior sería mostrar
    // resultados de dos preguntas distintas al mismo tiempo.
    setCercanas(null)
    setMiUbicacion(null)
    // `replace` para que cada tecla no sea una entrada del historial: el botón "atrás" tiene
    // que salir de la pantalla, no destipear.
    setParams(texto.trim() === '' ? {} : { q: texto }, { replace: true })
  }

  function abrir(parada: ParadaBreve | null) {
    setSeleccionada(parada)
    // Recién acá se guarda la búsqueda: si abrió una parada, el texto sirvió. Guardar cada tecla
    // llenaría la lista de prefijos a medio escribir.
    if (parada !== null && buscandoTexto) recientes.recordar(consulta)
  }

  /** Lo que hay en pantalla, sea de donde venga: es lo que tiene que dibujar el mapa. */
  const enPantalla = useMemo<ParadaListable[]>(() => {
    if (cercanas !== null) return cercanas
    if (busqueda !== null) {
      // Cuando ubicamos el lugar, las coincidencias de texto son ruido en el mapa: están
      // desparramadas por toda la calle y el encuadre terminaría abarcando kilómetros, con las
      // paradas que importan apretadas en un punto. En la lista siguen apareciendo igual.
      if (busqueda.cercanasAlPunto.length > 0) return busqueda.cercanasAlPunto
      return busqueda.paradas
    }
    return favoritos.favoritos
  }, [cercanas, busqueda, favoritos.favoritos])

  const puntos = useMemo<PuntoMapa[]>(() => {
    const marcas: PuntoMapa[] = enPantalla
      .filter((p) => p.latitud !== null && p.longitud !== null)
      .map((p) => ({
        clave: `parada-${p.codigo}`,
        latitud: p.latitud as number,
        longitud: p.longitud as number,
        etiqueta: '',
        titulo: p.descripcion,
        detalle: p.distanciaLegible ?? `Parada #${p.codigo}`,
        tipo:
          seleccionada?.codigo === p.codigo
            ? 'seleccionada'
            : favoritos.esFavorita(p.codigo)
              ? 'favorita'
              : 'parada',
        onClick: () => setSeleccionada(p),
      }))

    if (miUbicacion !== null) {
      marcas.push({
        clave: 'yo',
        latitud: miUbicacion[0],
        longitud: miUbicacion[1],
        etiqueta: '',
        titulo: 'Estás acá',
        tipo: 'yo',
      })
    }

    // El lugar buscado. Sin esto, las paradas "a 27 m" no dicen a 27 m de qué, y no se ve para
    // qué lado hay que caminar.
    if (busqueda?.punto != null) {
      marcas.push({
        clave: 'lugar',
        latitud: busqueda.punto.latitud,
        longitud: busqueda.punto.longitud,
        etiqueta: '',
        titulo: busqueda.punto.nombre ?? 'Lo que buscaste',
        detalle: busqueda.punto.tipo === 'CRUCE' ? 'Cruce estimado: puede errarle' : undefined,
        tipo: 'lugar',
      })
    }

    // Los bondis que vienen a la parada abierta, con su posición real.
    if (seleccionada !== null) {
      queVienen
        .filter((a) => a.latitud !== null && a.longitud !== null)
        .forEach((arribo, i) => {
          marcas.push({
            clave: `bus-${arribo.linea}-${i}`,
            latitud: arribo.latitud as number,
            longitud: arribo.longitud as number,
            etiqueta: arribo.linea,
            titulo: `${arribo.linea} → ${arribo.destino}`,
            detalle:
              arribo.esperaEnMinutos <= 0 ? 'Llegando' : `Llega en ${arribo.esperaEnMinutos} min`,
            tipo: 'bus',
          })
        })
    }
    return marcas
  }, [enPantalla, seleccionada, miUbicacion, favoritos, queVienen, busqueda])

  const sinResultados =
    busqueda !== null && busqueda.paradas.length === 0 && busqueda.cercanasAlPunto.length === 0

  return (
    <>
      <div className="buscador">
        <input
          type="search"
          className="buscador__input"
          value={consulta}
          onChange={(e) => escribir(e.target.value)}
          placeholder="gabriel pereira 2470"
          aria-label="Dirección con número, cruce de calles o código de parada"
          autoFocus
        />
        <button type="button" className="boton" onClick={buscarCerca} disabled={ubicando}>
          {ubicando ? 'Ubicando…' : '📍 Cerca mío'}
        </button>
      </div>

      {buscando && <p className="estado">Buscando…</p>}
      {(errorBusqueda ?? errorUbicacion) && (
        <p className="estado estado--error">{errorBusqueda ?? errorUbicacion}</p>
      )}

      {/* El mapa está desde el arranque, aunque todavía no haya nada que marcar: arranca sobre
          Montevideo y es la forma más rápida de ubicarse antes de escribir nada. */}
      <div className="mapa-bloque">
        <button type="button" className="boton-tenue" onClick={() => setConMapa((v) => !v)}>
          {conMapa ? 'Ocultar mapa' : 'Ver en el mapa'}
        </button>
        {conMapa && <Mapa puntos={puntos} />}
      </div>

      {/* Cuando el lugar buscado no tiene parada pero pudimos ubicarlo, esto es la respuesta
          útil: dónde está la parada más cerca. Va antes que las coincidencias de texto. */}
      {busqueda !== null && busqueda.cercanasAlPunto.length > 0 && (
        <>
          {/* Mostrar a qué lo resolvimos no es adorno: el usuario escribió "gabriel pereira 2470"
              y el padrón la llama "GABRIEL A. PEREIRA 2470". Con un lugar es todavía más
              importante, porque lo encontramos por parecido de nombre. */}
          <p className="estado">
            {busqueda.punto?.nombre != null
              ? `${busqueda.punto.nombre}. Las paradas más cercanas:`
              : 'No hay ninguna parada en ese cruce. Las más cercanas:'}
          </p>
          <ListaParadas
            paradas={busqueda.cercanasAlPunto}
            seleccionada={seleccionada}
            onSeleccionar={abrir}
            favoritos={favoritos}
            onArribos={alRecibirArribos}
          />
        </>
      )}

      {busqueda !== null && busqueda.paradas.length > 0 && (
        <>
          {busqueda.soloAproximadas && busqueda.cercanasAlPunto.length === 0 && (
            <p className="estado">Ninguna coincide del todo. Quizás quisiste decir alguna de estas:</p>
          )}
          <ListaParadas
            paradas={busqueda.paradas}
            seleccionada={seleccionada}
            onSeleccionar={abrir}
            favoritos={favoritos}
            onArribos={alRecibirArribos}
          />
        </>
      )}

      {sinResultados && !buscando && (
        <p className="estado">
          No se encontraron paradas. Probá con el nombre de una de las calles del cruce.
        </p>
      )}

      {cercanas !== null &&
        (cercanas.length > 0 ? (
          <ListaParadas
            paradas={cercanas}
            seleccionada={seleccionada}
            onSeleccionar={abrir}
            favoritos={favoritos}
            onArribos={alRecibirArribos}
          />
        ) : (
          <p className="estado">No hay paradas cerca tuyo.</p>
        ))}

      {/* Pantalla de inicio: sin búsqueda ni ubicación, lo más probable es que quiera una parada
          que ya usó antes. */}
      {!buscandoTexto && cercanas === null && (
        <>
          {favoritos.favoritos.length > 0 && (
            <section className="seccion">
              <h2 className="seccion__titulo">★ Tus paradas</h2>
              <ListaParadas
                paradas={favoritos.favoritos}
                seleccionada={seleccionada}
                onSeleccionar={setSeleccionada}
                favoritos={favoritos}
                onArribos={alRecibirArribos}
              />
            </section>
          )}

          {recientes.recientes.length > 0 && (
            <section className="seccion">
              <h2 className="seccion__titulo">
                Búsquedas recientes
                <button type="button" className="boton-tenue" onClick={recientes.limpiar}>
                  Limpiar
                </button>
              </h2>
              <div className="fichas">
                {recientes.recientes.map((texto) => (
                  <button
                    key={texto}
                    type="button"
                    className="ficha"
                    onClick={() => escribir(texto)}
                  >
                    {texto}
                  </button>
                ))}
              </div>
            </section>
          )}

          {favoritos.favoritos.length === 0 && recientes.recientes.length === 0 && (
            <p className="estado">
              Buscá una esquina, o tocá <strong>Cerca mío</strong>. Con la ★ guardás las paradas
              que uses siempre y te aparecen acá al abrir.
            </p>
          )}
        </>
      )}
    </>
  )
}
