import { NavLink, Outlet } from 'react-router-dom'

/**
 * Panel del albergue: perfil institucional, registro de mascotas,
 * estados y consulta autorizada de perfiles (Sprint 1, Persona A).
 */
export default function AlbergueLayout() {
  return (
    <div className="layout">
      <nav className="layout__nav">
        <NavLink to="/albergue" end>
          Panel
        </NavLink>
      </nav>
      <Outlet />
    </div>
  )
}
