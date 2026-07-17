import type { Arribo, ParadaBreve } from '../api/stm'
import type { Favoritos } from '../hooks/almacenamiento'
import { Arribos } from './Arribos'

/** Una parada de cualquier origen: las de las cercanas traen distancia, las de la búsqueda no. */
export type ParadaListable = ParadaBreve & { distanciaLegible?: string }

/**
 * Lista de paradas que se abren para ver los arribos.
 *
 * La usan todas las pantallas —búsqueda, cercanas, favoritos— para que una parada se vea y se
 * comporte igual sin importar de dónde salió.
 */
export function ListaParadas({
  paradas,
  seleccionada,
  onSeleccionar,
  favoritos,
  onArribos,
}: {
  paradas: ParadaListable[]
  seleccionada: ParadaBreve | null
  onSeleccionar: (parada: ParadaBreve | null) => void
  favoritos: Favoritos
  /** Se reenvía a los arribos de la parada abierta, para pintar sus buses en el mapa. */
  onArribos?: (arribos: Arribo[]) => void
}) {
  return (
    <ul className="paradas">
      {paradas.map((parada) => {
        const abierta = seleccionada?.codigo === parada.codigo
        const favorita = favoritos.esFavorita(parada.codigo)

        return (
          <li key={parada.codigo} className={abierta ? 'parada parada--abierta' : 'parada'}>
            <div className="parada__fila">
              <button
                type="button"
                className="parada__boton"
                aria-expanded={abierta}
                onClick={() => onSeleccionar(abierta ? null : parada)}
              >
                <span className="parada__descripcion">{parada.descripcion}</span>
                {/* Antes era la distancia O el código. Van los dos: la distancia decide a qué
                    parada ir, y el código es lo que se compara con el cartel del refugio. */}
                <span className="parada__meta">
                  {parada.distanciaLegible !== undefined && (
                    <span className="parada__distancia">a {parada.distanciaLegible}</span>
                  )}
                  <span>Parada {parada.codigo}</span>
                </span>
              </button>

              {/* Fuera del botón que despliega: si estuviera adentro, marcar favorita abriría
                  los arribos de yapa. */}
              <button
                type="button"
                className={favorita ? 'estrella estrella--marcada' : 'estrella'}
                aria-pressed={favorita}
                aria-label={favorita ? 'Quitar de favoritas' : 'Guardar como favorita'}
                title={favorita ? 'Quitar de favoritas' : 'Guardar como favorita'}
                onClick={() => favoritos.alternar(parada)}
              >
                {favorita ? '★' : '☆'}
              </button>
            </div>

            {abierta && <Arribos parada={parada} onArribos={onArribos} />}
          </li>
        )
      })}
    </ul>
  )
}
