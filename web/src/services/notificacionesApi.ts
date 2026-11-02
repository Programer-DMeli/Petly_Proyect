import { spring } from './http';
import type { PreferenciasNotificacion } from './types';

export function getPreferenciasNotificacion() {
  return spring<PreferenciasNotificacion>('/api/notificaciones/preferencias');
}

export function guardarPreferenciasNotificacion(preferencias: PreferenciasNotificacion) {
  return spring<PreferenciasNotificacion>('/api/notificaciones/preferencias', {
    method: 'PUT',
    body: JSON.stringify(preferencias),
  });
}
