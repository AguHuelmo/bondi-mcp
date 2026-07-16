import { useEffect, useState } from 'react'
import { ApiError, buscarParadas, consultarArribos, type Arribo, type Parada } from './api/stm'
import './App.css'

function textoDeEspera(minutos: number): string {
  if (minutos <= 0) return 'llegando'
  if (minutos === 1) return '1 min'
  return `${minutos} min`
}

function Arribos({ parada }: { parada: Parada }) {
  const [arribos, setArribos] = useState<Arribo[] | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    const controller = new AbortController()
    setArribos(null)
    setError(null)

    consultarArribos(parada.codigo, controller.signal)
      .then(setArribos)
      .catch((causa: unknown) => {
        if (causa instanceof DOMException && causa.name === 'AbortError') return
        setError(causa instanceof ApiError ? causa.message : 'No se pudieron obtener los arribos.')
      })

    return () => controller.abort()
  }, [parada.codigo])

  if (error) return <p className="estado estado--error">{error}</p>
  if (arribos === null) return <p className="estado">Consultando arribos…</p>
  if (arribos.length === 0) return <p className="estado">No hay próximos arribos para esta parada.</p>

  return (
    <ul className="arribos">
      {arribos.map((arribo, i) => (
        <li key={`${arribo.linea}-${arribo.destino}-${i}`} className="arribo">
          <span className="arribo__linea">{arribo.linea}</span>
          <span className="arribo__destino">{arribo.destino}</span>
          <span className="arribo__espera">{textoDeEspera(arribo.esperaEnMinutos)}</span>
        </li>
      ))}
    </ul>
  )
}

export default function App() {
  const [consulta, setConsulta] = useState('')
  const [paradas, setParadas] = useState<Parada[] | null>(null)
  const [seleccionada, setSeleccionada] = useState<Parada | null>(null)
  const [buscando, setBuscando] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function buscar(evento: React.FormEvent) {
    evento.preventDefault()
    if (!consulta.trim()) return

    setBuscando(true)
    setError(null)
    setSeleccionada(null)
    try {
      setParadas(await buscarParadas(consulta))
    } catch (causa) {
      setParadas(null)
      setError(causa instanceof ApiError ? causa.message : 'Falló la búsqueda.')
    } finally {
      setBuscando(false)
    }
  }

  return (
    <main className="app">
      <header className="cabecera">
        <h1>Bondis de Montevideo</h1>
        <p>Buscá una parada por dirección o cruce de calles y mirá los próximos ómnibus.</p>
      </header>

      <form className="buscador" onSubmit={buscar}>
        <input
          type="search"
          value={consulta}
          onChange={(e) => setConsulta(e.target.value)}
          placeholder="18 de julio y ejido"
          aria-label="Dirección, cruce de calles o código de parada"
        />
        <button type="submit" disabled={buscando || !consulta.trim()}>
          {buscando ? 'Buscando…' : 'Buscar'}
        </button>
      </form>

      {error && <p className="estado estado--error">{error}</p>}

      {paradas?.length === 0 && !error && (
        <p className="estado">
          No se encontraron paradas. Probá con el nombre de las dos calles del cruce.
        </p>
      )}

      {paradas && paradas.length > 0 && (
        <ul className="paradas">
          {paradas.map((parada) => {
            const abierta = seleccionada?.codigo === parada.codigo
            return (
              <li key={parada.codigo} className="parada">
                <button
                  type="button"
                  className="parada__boton"
                  aria-expanded={abierta}
                  onClick={() => setSeleccionada(abierta ? null : parada)}
                >
                  <span className="parada__descripcion">{parada.descripcion}</span>
                  <span className="parada__codigo">#{parada.codigo}</span>
                </button>
                {abierta && <Arribos parada={parada} />}
              </li>
            )
          })}
        </ul>
      )}
    </main>
  )
}
