import { Outlet } from 'react-router-dom'

/**
 * Componente raíz de la aplicación: envuelve todas las rutas con el
 * encabezado y pie comunes de Petly. Las áreas concretas viven en `layouts/`.
 */
export default function App() {
  return (
    <div className="app">
      <header className="app__header">
        <span className="app__logo">Petly</span>
        <span className="app__tagline">Adopción responsable de mascotas</span>
      </header>
      <main className="app__main">
        <Outlet />
      </main>
      <footer className="app__footer">
        <small>Petly — proyecto académico. Los datos mostrados son de demostración.</small>
      </footer>
    </div>
  )
}
