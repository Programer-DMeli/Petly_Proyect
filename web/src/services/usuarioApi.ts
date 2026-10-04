/**
 * Cliente HTTP para Spring Boot (`:8080`), backend de usuarios:
 * cuentas, perfiles, catálogo y solicitudes del adoptante.
 *
 * Este backend nunca se sustituye con llamadas directas a la base de datos.
 */

const BASE_URL = import.meta.env.VITE_API_USUARIO_URL ?? 'http://localhost:8080'

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${BASE_URL}${path}`, {
    headers: { 'Content-Type': 'application/json', ...init?.headers },
    ...init,
  })
  if (!response.ok) {
    throw new Error(`Error ${response.status} en ${path}`)
  }
  return (await response.json()) as T
}

/** Comprobación básica del backend de usuarios (sin información sensible). */
export function getHealthUsuario(): Promise<{ status: string }> {
  return request<{ status: string }>('/api/health/')
}

// Endpoints previstos (PLANNING v2 §9) — pendientes de implementar en los sprints:
// POST /api/auth/registro, POST /api/auth/acceso, POST /api/auth/recuperacion (US-21, US-24)
// GET  /api/catalogo/mascotas con filtros y paginación (US-12)
// GET/PUT /api/perfiles/cuestionario (US-16, US-17)
// POST /api/perfiles/autorizaciones (US-18)
