import PesosCompatibilidad from '../components/PesosCompatibilidad';
import EventosPage from '../features/albergue/perfil/pages/EventosPage';
import NecesidadesPage from '../features/albergue/perfil/pages/NecesidadesPage';
import FavoritosPage from '../features/adoptante/catalogo/pages/FavoritosPage';
import DisponibilidadPage from '../features/adoptante/perfil/pages/DisponibilidadPage';
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
import ReportesPostAdopcionPage from '../features/adoptante/solicitudes/pages/ReportesPostAdopcionPage';
import SolicitudesAdoptantePage from '../features/adoptante/solicitudes/pages/SolicitudesAdoptantePage';
import PanelAlberguePage from '../features/albergue/perfil/pages/PanelAlberguePage';
import MascotasPanel from '../features/albergue/perfil/pages/MascotasPanel';
import SolicitudesPage from '../features/albergue/perfil/pages/SolicitudesPage';
import SeguimientosPage from '../features/albergue/perfil/pages/SeguimientosPage';
import RespuestasChatbotPage from '../features/albergue/perfil/pages/RespuestasChatbotPage';
import AlberguesAdminPage from '../features/administracion/albergues/pages/AlberguesAdminPage';
import VerificacionKycPage from '../features/adoptante/perfil/pages/VerificacionKycPage';
import PreferenciasNotificacionPage from '../features/adoptante/perfil/pages/PreferenciasNotificacionPage';
import VerificacionesKycAdminPage from '../features/administracion/kyc/pages/VerificacionesKycAdminPage';
import MensajesPage from '../features/mensajeria/MensajesPage';

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
          { path: 'eventos', element: <EventosPage /> },
          { path: 'necesidades', element: <NecesidadesPage /> },
          { path: 'favoritos', element: <FavoritosPage /> },
          { path: 'disponibilidad', element: <DisponibilidadPage /> },
          { path: 'catalogo', element: <CatalogoPage /> },
          { path: 'catalogo/:id', element: <DetallePage /> },
          { path: 'autorizaciones', element: <AutorizacionesPage /> },
          { path: 'cuestionario', element: <CuestionarioPage /> },
          { path: 'verificacion-identidad', element: <VerificacionKycPage /> },
          { path: 'preferencias-notificacion', element: <PreferenciasNotificacionPage /> },
          { path: 'solicitudes', element: <SolicitudesAdoptantePage /> },
          { path: 'solicitudes/:solicitudId', element: <SeguimientoSolicitudPage /> },
          { path: 'seguimientos-postadopcion', element: <ReportesPostAdopcionPage /> },
          { path: 'mensajes', element: <MensajesPage /> },
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
          {
            path: 'mascotas',
            element: (
              <section className="card">
                <h1>Mascotas registradas</h1>
                <p className="muted">Consulta y administra las mascotas de tu albergue.</p>
                <MascotasPanel />
              </section>
            ),
          },
          { path: 'solicitudes', element: <SolicitudesPage /> },
          { path: 'seguimientos', element: <SeguimientosPage /> },
          { path: 'respuestas-chatbot', element: <RespuestasChatbotPage /> },
          { path: 'mensajes', element: <MensajesPage /> },
          { path: 'eventos', element: <EventosPage gestionar /> },
          { path: 'necesidades', element: <NecesidadesPage gestionar /> },
        ],
      },
      {
        path: 'administracion',
        element: (
          <ProtectedRoute role="ADMIN">
            <AdminLayout />
          </ProtectedRoute>
        ),
        children: [
          { path: 'albergues', element: <AlberguesAdminPage /> },
          { path: 'compatibilidad', element: <PesosCompatibilidad /> },
          { path: 'verificaciones-kyc', element: <VerificacionesKycAdminPage /> },
        ],
      },
      { path: '*', element: <HomePage /> },
    ],
  },
]);
