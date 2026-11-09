import { django, spring } from './http';
import type {
  AvisoAdoptante,
  AvisosLeidosResponse,
  BandejaAvisosAdoptante,
  PreferenciasNotificacion,
} from './types';

export function getAvisosAdoptante() {
  return django<BandejaAvisosAdoptante>('/api/acceso/notificaciones/');
}

export function marcarAvisoComoLeido(id: number) {
  return django<AvisoAdoptante>(`/api/acceso/notificaciones/${id}/leer/`, { method: 'POST' });
}

export function marcarTodosLosAvisosComoLeidos() {
  return django<AvisosLeidosResponse>('/api/acceso/notificaciones/leer-todas/', { method: 'POST' });
}

export function getPreferenciasNotificacion() {
  return spring<PreferenciasNotificacion>('/api/notificaciones/preferencias');
}

export function guardarPreferenciasNotificacion(preferencias: PreferenciasNotificacion) {
  return spring<PreferenciasNotificacion>('/api/notificaciones/preferencias', {
    method: 'PUT',
    body: JSON.stringify(preferencias),
  });
}
