import { NavLink, Outlet, useLocation } from 'react-router-dom';
import AdminWelcome from '../components/AdminWelcome';
export default function AdminLayout() {
  const { pathname } = useLocation();
  return (
    <div className="workspace workspace--admin">
      {pathname === '/administracion/albergues' && <AdminWelcome />}
      <div id="herramientas-admin">
        <details key={pathname} className="admin-settings-actions">
          <summary className="button">
            Configuración <span className="nav-chevron" aria-hidden="true" />
          </summary>
          <nav aria-label="Configuración de administración">
            <NavLink to="/administracion/compatibilidad">Configurar pesos de compatibilidad</NavLink>
            <NavLink to="/administracion/configuracion-global">Configuración global</NavLink>
          </nav>
        </details>
        <Outlet />
      </div>
    </div>
  );
}
