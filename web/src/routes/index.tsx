import { createBrowserRouter } from 'react-router-dom';
import App from '../App';
import AdoptanteLayout from '../layouts/AdoptanteLayout';
import AlbergueLayout from '../layouts/AlbergueLayout';
import AdminLayout from '../layouts/AdminLayout';
import ProtectedRoute from './ProtectedRoute';
import HomePage from '../features/auth/pages/HomePage';
import AccesoPage from '../features/auth/pages/AccesoPage';
import RecuperacionPage from '../features/auth/pages/RecuperacionPage';
import AutorizacionesPage from '../features/adoptante/perfil/pages/AutorizacionesPage';
import CatalogoPage, { DetallePage } from '../features/adoptante/catalogo/pages/CatalogoPage';
import CuestionarioPage from '../features/adoptante/perfil/pages/CuestionarioPage';
import SeguimientoSolicitudPage from '../features/adoptante/solicitudes/pages/SeguimientoSolicitudPage';
import PanelAlberguePage from '../features/albergue/perfil/pages/PanelAlberguePage';
import SolicitudesPage from '../features/albergue/perfil/pages/SolicitudesPage';
import AlberguesAdminPage from '../features/administracion/albergues/pages/AlberguesAdminPage';

/**
 * Rutas de Petly web. Estructura prevista según PLANNING v2 §9.2:
 *
 * - Sprint 1 React: áreas ADOPTANTE, ALBERGUE y ADMIN.
 * - Spring autentica todos los roles; Django gestiona recursos administrativos.
 *
 * Las páginas consumen APIs reales y validan acceso por rol.
 */
export const router = createBrowserRouter([
  {
    path: '/',
    element: <App />,
    children: [
      { index: true, element: <HomePage /> },
      { path: 'acceso', element: <AccesoPage /> },
      { path: 'registro', element: <AccesoPage register /> },
      { path: 'recuperacion', element: <RecuperacionPage /> },
      {
        path: 'adoptante',
        element: (
          <ProtectedRoute role="ADOPTANTE">
            <AdoptanteLayout />
          </ProtectedRoute>
        ),
        children: [
          { index: true, element: <CatalogoPage /> },
          { path: 'catalogo', element: <CatalogoPage /> },
          { path: 'catalogo/:id', element: <DetallePage /> },
          { path: 'solicitudes/:solicitudId', element: <SeguimientoSolicitudPage /> },
          { path: 'autorizaciones', element: <AutorizacionesPage /> },
          { path: 'cuestionario', element: <CuestionarioPage /> },
        ],
      },
      {
        path: 'albergue',
        element: (
          <ProtectedRoute role="ALBERGUE">
            <AlbergueLayout />
          </ProtectedRoute>
        ),
        children: [
          { index: true, element: <PanelAlberguePage /> },
          { path: 'solicitudes', element: <SolicitudesPage /> },
        ],
      },
      {
        path: 'administracion',
        element: (
          <ProtectedRoute role="ADMIN">
            <AdminLayout />
          </ProtectedRoute>
        ),
        children: [{ path: 'albergues', element: <AlberguesAdminPage /> }],
      },
      { path: '*', element: <HomePage /> },
    ],
  },
]);
