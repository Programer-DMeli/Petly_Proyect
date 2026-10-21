import { NavLink, Outlet } from 'react-router-dom';

/**
 * Área del adoptante (catálogo, cuestionario). Portal del adoptante incluido en Sprint 1 por la actualización del usuario.
 */
export default function AdoptanteLayout() {
  return (
    <div className="layout">
      <nav className="layout__nav">
        <NavLink to="/adoptante/catalogo">Catálogo</NavLink>
        <NavLink to="/adoptante/cuestionario">Cuestionario</NavLink>
        <NavLink to="/adoptante/autorizaciones">Autorizaciones</NavLink>
        <NavLink to="/adoptante/solicitudes">Mis solicitudes</NavLink>
      </nav>
      <Outlet />
    </div>
  );
}
