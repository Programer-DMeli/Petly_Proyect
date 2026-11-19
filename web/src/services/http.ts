import { getSession, renewSession } from './session';
import { ApiError, springBase } from './apiConfig';
export { ApiError, springBase };
export const djangoBase = (
  (import.meta.env['VITE_API_ADMINISTRACION_URL'] as string | undefined) ?? 'http://localhost:8000'
).replace(/\/$/, '');
async function send(base: string, path: string, init: RequestInit, authenticated: boolean) {
  const headers = new Headers(init.headers);
  if (init.body && !(init.body instanceof FormData)) headers.set('Content-Type', 'application/json');
  init.signal?.throwIfAborted();
  const previous = getSession();
  if (authenticated && !previous) throw new ApiError(401, 'Inicia sesión para continuar.');
  const session = authenticated ? await renewSession() : null;
  if (previous && session && previous.identity.id !== session.identity.id) {
    throw new ApiError(401, 'La cuenta activa cambió. Vuelve a intentar.');
  }
  init.signal?.throwIfAborted();
  if (session) headers.set('Authorization', `Bearer ${session.token}`);
  const fetchOnce = async () => {
    try {
      return await fetch(base + path, { ...init, headers });
    } catch (error) {
      if (init.signal?.aborted) throw error;
      throw new Error('No se pudo conectar. Comprueba tu conexión e intenta nuevamente.');
    }
  };
  const response = await fetchOnce();
  if (response.status !== 401 || !session) return response;
  const renewed = await renewSession(session.token).catch(async error => {
    await response.body?.cancel();
    throw error;
  });
  const method = (init.method ?? 'GET').toUpperCase();
  if (renewed.identity.id !== session.identity.id || !['GET', 'HEAD'].includes(method)) return response;
  init.signal?.throwIfAborted();
  await response.body?.cancel();
  headers.set('Authorization', `Bearer ${renewed.token}`);
  return fetchOnce();
}
export async function request<T>(
  base: string,
  path: string,
  init: RequestInit = {},
  authenticated = true
): Promise<T> {
  const response = await send(base, path, init, authenticated);
  if (!response.ok) {
    let message = `No se pudo completar la operación (${response.status}).`;
    let retryAfter = Number(response.headers.get('Retry-After')) || 0;
    try {
      const data = (await response.json()) as {
        error?: string;
        message?: string;
        detail?: string;
        campos?: Record<string, unknown>;
        reintentarEnSegundos?: number;
        [field: string]: unknown;
      };
      retryAfter = Number(data.reintentarEnSegundos) || retryAfter;
      const labels: Record<string, string> = {
        foto_principal: 'Fotografía', nombre: 'Nombre de la mascota', especie: 'Especie',
        sexo: 'Sexo', tamanio: 'Tamaño', temperamento: 'Temperamento', energia: 'Energía',
        edad_anos: 'Edad: años', edad_meses: 'Edad: meses adicionales', raza: 'Raza',
        descripcion: 'Descripción', estado_salud_general: 'Estado de salud general',
        evidencia_vacunacion: 'Evidencia de vacunación', evidencia_esterilizacion: 'Evidencia de esterilización',
        registros_clinicos: 'Historial clínico',
        non_field_errors: 'Formulario',
      };
      const validationFields = data.campos ?? (response.status === 400 ?
        Object.fromEntries(Object.entries(data).filter(([, value]) => Array.isArray(value))) : {});
      const fields = Object.entries(validationFields)
        .map(([key, value]) => `${labels[key] ?? key}: ${Array.isArray(value) ? value.join(', ') : String(value)}`)
        .join(' · ');
      message = [data.message ?? data.error ?? data.detail ?? (fields ? '' : message), fields].filter(Boolean).join(' · ');
    } catch {
      /* conservar mensaje sin divulgar respuesta HTML */
    }
    if (response.status === 429)
      message = 'Demasiados intentos. Espera un minuto y vuelve a intentar.';
    throw new ApiError(response.status, message, Math.max(0, Math.min(86400, retryAfter)));
  }
  if (response.status === 204) return undefined as T;
  return (await response.json()) as T;
}
export const spring = <T>(path: string, init?: RequestInit, auth = true) =>
  request<T>(springBase, path, init, auth);
export const django = <T>(path: string, init?: RequestInit) => request<T>(djangoBase, path, init);
export async function fetchDjangoFile(path: string, init?: RequestInit) {
  const response = await send(djangoBase, path, init ?? {}, true);
  if (!response.ok) {
    let message = `No se pudo descargar el documento (${response.status}).`;
    try { const body = (await response.json()) as { detail?: string }; message = body.detail ?? message; } catch { /* conservar mensaje */ }
    throw new ApiError(response.status, message);
  }
  return response;
}
export async function downloadDjango(path: string, filename: string) {
  const response = await fetchDjangoFile(path);
  const url = URL.createObjectURL(await response.blob());
  const anchor = document.createElement('a'); anchor.href = url; anchor.download = filename;
  document.body.append(anchor); anchor.click(); anchor.remove(); URL.revokeObjectURL(url);
}
export const json = (value: unknown) => JSON.stringify(value);
export function mediaUrl(value: string | null | undefined) {
  if (!value) return undefined;
  const url = new URL(value, djangoBase);
  if (url.hostname === '10.0.2.2') {
    const base = new URL(djangoBase);
    url.host = base.host;
    url.protocol = base.protocol;
  }
  return url.href;
}
export function errorMessage(error: unknown) {
  return error instanceof Error ? error.message : 'No se pudo completar la operación.';
}
