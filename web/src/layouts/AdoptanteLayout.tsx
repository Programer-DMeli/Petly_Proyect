import { NavLink, Outlet } from 'react-router-dom'

/**
 * Área del adoptante (catálogo, cuestionario). El portal completo del
 * adoptante se desarrolla en el Sprint 2 según PLANNING v2 §9.2.
 */
export default function AdoptanteLayout() {
  return (
    <div className="layout">
      <nav className="layout__nav">
        <NavLink to="/adoptante/catalogo">Catálogo</NavLink>
        <NavLink to="/adoptante/cuestionario">Cuestionario</NavLink>
      </nav>
      <Outlet />
    </div>
  )
}
