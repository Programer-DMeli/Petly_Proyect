import { createBrowserRouter } from 'react-router-dom'
import App from '../App'
import AdoptanteLayout from '../layouts/AdoptanteLayout'
import AlbergueLayout from '../layouts/AlbergueLayout'
import AdminLayout from '../layouts/AdminLayout'
import ProtectedRoute from './ProtectedRoute'
import HomePage from '../features/auth/pages/HomePage'
import AccesoPage from '../features/auth/pages/AccesoPage'
import RecuperacionPage from '../features/auth/pages/RecuperacionPage'
import CatalogoPage from '../features/adoptante/catalogo/pages/CatalogoPage'
import CuestionarioPage from '../features/adoptante/perfil/pages/CuestionarioPage'
import PanelAlberguePage from '../features/albergue/perfil/pages/PanelAlberguePage'
import AlberguesAdminPage from '../features/administracion/albergues/pages/AlberguesAdminPage'

/**
 * Rutas de Petly web. Estructura prevista según PLANNING v2 §9.2:
 *
 * - Sprint 1 React: acceso, recuperación y panel del albergue.
 * - Sprint 2 React: portal del adoptante (catálogo y cuestionario).
 *
 * Las páginas marcadas como pendientes muestran su estado real; no se
 * presentan como terminadas.
 */
export const router = createBrowserRouter([
  {
    path: '/',
    element: <App />,
    children: [
      { index: true, element: <HomePage /> },
      { path: 'acceso', element: <AccesoPage /> },
      { path: 'recuperacion', element: <RecuperacionPage /> },
      {
        path: 'adoptante',
        element: (
          <ProtectedRoute>
            <AdoptanteLayout />
          </ProtectedRoute>
        ),
        children: [
          { path: 'catalogo', element: <CatalogoPage /> },
          { path: 'cuestionario', element: <CuestionarioPage /> },
        ],
      },
      {
        path: 'albergue',
        element: (
          <ProtectedRoute>
            <AlbergueLayout />
          </ProtectedRoute>
        ),
        children: [{ index: true, element: <PanelAlberguePage /> }],
      },
      {
        path: 'administracion',
        element: (
          <ProtectedRoute>
            <AdminLayout />
          </ProtectedRoute>
        ),
        children: [{ path: 'albergues', element: <AlberguesAdminPage /> }],
      },
      { path: '*', element: <HomePage /> },
    ],
  },
])
