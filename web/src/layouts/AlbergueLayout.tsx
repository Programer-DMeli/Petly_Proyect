import { Outlet } from 'react-router-dom';

/**
 * Panel del albergue: perfil institucional, registro de mascotas,
 * estados y consulta autorizada de perfiles (Sprint 1, Persona A).
 */
export default function AlbergueLayout() {
  return (
    <div className="layout">
      <Outlet />
    </div>
  );
}
