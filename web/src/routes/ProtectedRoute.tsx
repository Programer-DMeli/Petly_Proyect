import { Navigate, useLocation } from 'react-router-dom'
import type { ReactNode } from 'react'

/**
 * Guarda de rutas. La autenticación real llega con US-21 (Sprint 1):
 * mientras no exista sesión, el guard deriva a `/acceso`.
 *
 * Importante (PLANNING v2 §5): ocultar una pantalla no sustituye la
 * validación de permisos en el backend. Este guard solo organiza la
 * navegación; la autorización real se comprueba en cada API.
 */
export default function ProtectedRoute({ children }: { children: ReactNode }) {
  const location = useLocation()
  const isAuthenticated = false // TODO US-21: leer de la sesión real

  if (!isAuthenticated) {
    return <Navigate to="/acceso" replace state={{ from: location }} />
  }

  return <>{children}</>
}
