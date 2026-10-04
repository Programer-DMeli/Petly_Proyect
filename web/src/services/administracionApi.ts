/**
 * Cliente HTTP para Django (`:8000`), backend administrativo:
 * albergues, mascotas y administración general.
 */

const BASE_URL = import.meta.env.VITE_API_ADMINISTRACION_URL ?? 'http://localhost:8000'

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

/** Comprobación básica del backend administrativo (sin información sensible). */
export function getHealthAdministracion(): Promise<{ status: string }> {
  return request<{ status: string }>('/api/health/')
}

// Endpoints previstos (PLANNING v2 §9) — pendientes de implementar en los sprints:
// CRUD /api/albergues/ (US-46) y /api/mascotas/ con fotografía JPEG/PNG ≤ 5 MB (US-11)
// PATCH /api/mascotas/<id>/estado/ (US-13)
// GET /api/postulantes/<id>/ (solo con autorización vigente de Spring Boot, US-18)
