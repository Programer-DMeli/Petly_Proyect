import { request, djangoBase } from './http';
export function getHealthAdministracion() {
  return request<{ status: string }>(djangoBase, '/api/health/', undefined, false);
}
