import { Navigate, NavLink, Route, Routes, useLocation } from 'react-router'
import { useFavoritos } from './hooks/almacenamiento'
import { PantallaLinea } from './components/PantallaLinea'
import { PantallaParadas } from './components/PantallaParadas'
import { PantallaViaje } from './components/PantallaViaje'
import './App.css'

/**
 * Las dos preguntas que le hace alguien parado en la calle a esta app: "¿cuándo pasa?" y
 * "¿cómo llego?". Una ruta cada una, así el link de un viaje se puede mandar por WhatsApp.
 */
const PESTANAS = [
  { ruta: '/paradas', titulo: 'Paradas', bajada: 'Buscá una parada y mirá los próximos ómnibus.' },
  { ruta: '/viaje', titulo: 'Cómo llego', bajada: 'Decinos desde dónde y hasta dónde vas.' },
] as const

export default function App() {
  const { pathname } = useLocation()
  // Vive acá arriba y no adentro de la pantalla de paradas: las favoritas tienen que sobrevivir
  // a ir y volver de "Cómo llego".
  const favoritos = useFavoritos()

  const actual = PESTANAS.find((p) => pathname.startsWith(p.ruta)) ?? PESTANAS[0]
  // La pantalla de línea no es una pestaña: se llega desde los horarios o desde un viaje.
  const bajada = pathname.startsWith('/lineas')
    ? 'El recorrido completo y los coches en la calle, en vivo.'
    : actual.bajada

  return (
    <main className="app">
      <header className="cabecera">
        <h1>Bondis de Montevideo</h1>
        <p>{bajada}</p>
      </header>

      <nav className="pestanas" aria-label="Secciones">
        {PESTANAS.map((p) => (
          <NavLink
            key={p.ruta}
            to={p.ruta}
            className={({ isActive }) => (isActive ? 'pestana pestana--activa' : 'pestana')}
          >
            {p.titulo}
          </NavLink>
        ))}
      </nav>

      <Routes>
        <Route path="/paradas" element={<PantallaParadas favoritos={favoritos} />} />
        <Route path="/viaje" element={<PantallaViaje />} />
        <Route path="/lineas/:linea" element={<PantallaLinea />} />
        <Route path="*" element={<Navigate to="/paradas" replace />} />
      </Routes>
    </main>
  )
}
