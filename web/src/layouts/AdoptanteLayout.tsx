import { Outlet } from 'react-router-dom';

/**
 * Área del adoptante (catálogo, cuestionario). Portal del adoptante incluido en Sprint 1 por la actualización del usuario.
 */
export default function AdoptanteLayout() {
  return (
    <div className="layout">
      <Outlet />
    </div>
  );
}
