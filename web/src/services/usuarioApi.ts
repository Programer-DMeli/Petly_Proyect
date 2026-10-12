import { spring } from './http';
export function getHealthUsuario() {
  return spring<{ status: string }>('/api/health/', undefined, false);
}
