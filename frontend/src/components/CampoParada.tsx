import { useEffect, useId, useMemo, useRef, useState } from 'react'
import { MIN_CARACTERES, useBusquedaParadas } from '../hooks/useBusquedaParadas'

/** Cuántas sugerencias mostrar: la lista tiene que caber sin tapar la pantalla. */
const MAXIMAS_SUGERENCIAS = 6

/** Una opción de la lista: la dirección que se ubicó, o una parada que coincide por nombre. */
type Sugerencia = {
  clave: string
  /** Lo que se ve, y lo que queda escrito en el campo al elegirla. */
  texto: string
  /** Lo que va en gris a la derecha: el código de la parada, o qué clase de cosa es. */
  detalle: string
}

/**
 * Campo de dirección con sugerencias de paradas reales.
 *
 * El backend resuelve el origen y el destino desde texto libre, así que técnicamente esto podría
 * ser un input pelado. Pero entonces uno se entera de que escribió mal la esquina recién al
 * apretar "Buscar viaje"; sugiriendo paradas de verdad mientras tipea, el error se ve antes.
 */
export function CampoParada({
  etiqueta,
  valor,
  onCambiar,
  placeholder,
  sugerir = true,
  autoFocus = false,
  accion,
}: {
  etiqueta: string
  valor: string
  onCambiar: (valor: string) => void
  placeholder?: string
  /** En false el campo no sugiere nada: el valor ya está resuelto (por ejemplo, el GPS). */
  sugerir?: boolean
  autoFocus?: boolean
  /** Botón al costado del campo, como "usar mi ubicación". */
  accion?: React.ReactNode
}) {
  const id = useId()
  const [abierto, setAbierto] = useState(false)
  const [resaltada, setResaltada] = useState(-1)
  const contenedor = useRef<HTMLDivElement>(null)

  const { busqueda, buscando } = useBusquedaParadas(valor, sugerir && abierto)

  const sugerencias = useMemo<Sugerencia[]>(() => {
    const punto = busqueda?.punto
    // El lugar ubicado va primero y muchas veces solo: es exactamente lo que se pidió, no algo
    // que se llama parecido. Sin esto, escribir "gabriel pereira 2470" no ofrecía nada, porque la
    // búsqueda ya no devuelve coincidencias de texto cuando ubicó el lugar.
    const lista: Sugerencia[] =
      punto?.nombre == null
        ? []
        : [
            {
              clave: 'punto',
              texto: punto.nombre,
              detalle: punto.tipo === 'LUGAR' ? 'lugar' : 'dirección',
            },
          ]

    for (const parada of busqueda?.paradas ?? []) {
      lista.push({
        clave: `parada-${parada.codigo}`,
        texto: parada.descripcion,
        detalle: `#${parada.codigo}`,
      })
    }
    return lista.slice(0, MAXIMAS_SUGERENCIAS)
  }, [busqueda])

  useEffect(() => setResaltada(-1), [valor])

  function elegir(descripcion: string) {
    onCambiar(descripcion)
    setAbierto(false)
    setResaltada(-1)
  }

  function alTeclear(evento: React.KeyboardEvent<HTMLInputElement>) {
    if (evento.key === 'Escape') {
      setAbierto(false)
      return
    }
    if (sugerencias.length === 0) return

    if (evento.key === 'ArrowDown' || evento.key === 'ArrowUp') {
      evento.preventDefault()
      const paso = evento.key === 'ArrowDown' ? 1 : -1
      // Da la vuelta en las dos puntas: llegar al final de la lista y quedarse trabado es peor
      // que volver al principio.
      setResaltada((actual) => (actual + paso + sugerencias.length) % sugerencias.length)
      return
    }
    if (evento.key === 'Enter' && resaltada >= 0) {
      // Sin esto el form se manda con lo tipeado y se pierde la sugerencia elegida.
      evento.preventDefault()
      elegir(sugerencias[resaltada].texto)
    }
  }

  return (
    <div
      className="campo"
      ref={contenedor}
      // Cierra al salir del campo, pero no cuando el foco cae en la propia lista de sugerencias.
      onBlur={(evento) => {
        if (!contenedor.current?.contains(evento.relatedTarget as Node | null)) setAbierto(false)
      }}
    >
      <label className="campo__etiqueta" htmlFor={id}>
        {etiqueta}
      </label>

      <div className="campo__fila">
        <input
          id={id}
          type="text"
          className="campo__input"
          value={valor}
          placeholder={placeholder}
          autoComplete="off"
          autoFocus={autoFocus}
          role="combobox"
          aria-expanded={abierto && sugerencias.length > 0}
          aria-controls={`${id}-sugerencias`}
          aria-autocomplete="list"
          onChange={(e) => {
            onCambiar(e.target.value)
            setAbierto(true)
          }}
          onFocus={() => setAbierto(true)}
          onKeyDown={alTeclear}
        />
        {accion}
      </div>

      {abierto && sugerir && valor.trim().length >= MIN_CARACTERES && (
        <ul className="sugerencias" id={`${id}-sugerencias`} role="listbox">
          {sugerencias.map((sugerencia, i) => (
            <li key={sugerencia.clave}>
              <button
                type="button"
                role="option"
                aria-selected={i === resaltada}
                className={i === resaltada ? 'sugerencia sugerencia--resaltada' : 'sugerencia'}
                // onMouseDown y no onClick: el click llega después del blur, que ya cerró la lista.
                onMouseDown={(e) => e.preventDefault()}
                onClick={() => elegir(sugerencia.texto)}
                onMouseEnter={() => setResaltada(i)}
              >
                <span className="sugerencia__texto">{sugerencia.texto}</span>
                <span className="sugerencia__detalle">{sugerencia.detalle}</span>
              </button>
            </li>
          ))}
          {sugerencias.length === 0 && (
            <li className="sugerencia sugerencia--vacia">
              {buscando ? 'Buscando…' : 'Sin coincidencias. Se va a intentar igual al buscar.'}
            </li>
          )}
        </ul>
      )}
    </div>
  )
}
