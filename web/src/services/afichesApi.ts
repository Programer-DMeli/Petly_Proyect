import { django, fetchDjangoFile } from './http';

export type DatosAfiche = { nombre: string; telefono: string; email: string };
export type PedidoAfiche = {
  id: number;
  mascota: number;
  estado: 'PENDIENTE' | 'RESERVADO' | 'COMPLETADO' | 'FALLIDO';
  datos_afiche: Partial<DatosAfiche>;
  foto_url: string;
  fondo_url: string | null;
  error_codigo: string;
};

export function contactoAfiche(shelter: DatosAfiche) {
  const telefono = shelter.telefono.trim();
  const email = shelter.email.trim();
  const digitos = telefono.replace(/\D/g, '').length;
  return {
    nombre: shelter.nombre.trim(),
    telefono: /^\+?[0-9 ().-]+$/.test(telefono) && digitos >= 7 && digitos <= 15 ? telefono : '',
    email: /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email) ? email : '',
  };
}

export const crearAfiche = (mascota: number) =>
  django<PedidoAfiche>(`/api/mascotas/${mascota}/afiches/`, { method: 'POST' });
export const consultarAfiche = (pedido: number, signal: AbortSignal) =>
  django<PedidoAfiche>(`/api/mascotas/afiches/${pedido}/`, { signal, cache: 'no-store' });
export const cargarFondo = async (ruta: string, signal: AbortSignal) =>
  (await fetchDjangoFile(ruta, { signal })).blob();
