import { json, spring } from './http';

export type ConfiguracionGlobal = {
  maxImagenMb: number;
  expiracionSesionDias: number;
  actualizadoEn: string;
};

const API = '/api/admin/configuracion-global';
let cache: ConfiguracionGlobal | null = null;
let pending: Promise<ConfiguracionGlobal> | null = null;

export function obtenerConfiguracionGlobal(force = false): Promise<ConfiguracionGlobal> {
  if (!force && cache) return Promise.resolve(cache);
  if (!force && pending) return pending;
  pending = spring<ConfiguracionGlobal>(API)
    .then(configuracion => {
      cache = configuracion;
      return configuracion;
    })
    .finally(() => { pending = null; });
  return pending;
}

export async function actualizarConfiguracionGlobal(
  configuracion: Pick<ConfiguracionGlobal, 'maxImagenMb' | 'expiracionSesionDias'>,
): Promise<ConfiguracionGlobal> {
  const actualizada = await spring<ConfiguracionGlobal>(API, {
    method: 'PUT',
    body: json(configuracion),
  });
  cache = actualizada;
  return actualizada;
}
