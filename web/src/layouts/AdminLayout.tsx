import { Link, Outlet, useLocation } from 'react-router-dom';
import AdminWelcome from '../components/AdminWelcome';
export default function AdminLayout() {
  const { pathname } = useLocation();
  return (
    <div className="workspace workspace--admin">
      {pathname === '/administracion/albergues' && <AdminWelcome />}
      <div id="herramientas-admin">
        <Link to="/administracion/compatibilidad">Configurar pesos de compatibilidad</Link>
        <Outlet />
      </div>
    </div>
  );
}
