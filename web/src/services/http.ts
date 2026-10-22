import { getSession, setSession } from './session';
export const springBase = (
  (import.meta.env['VITE_API_USUARIO_URL'] as string | undefined) ?? 'http://localhost:8080'
).replace(/\/$/, '');
export const djangoBase = (
  (import.meta.env['VITE_API_ADMINISTRACION_URL'] as string | undefined) ?? 'http://localhost:8000'
).replace(/\/$/, '');
export class ApiError extends Error {
  constructor(
    public status: number,
    message: string,
    public retryAfterSeconds = 0
  ) {
    super(message);
  }
}
export async function request<T>(
  base: string,
  path: string,
  init: RequestInit = {},
  authenticated = true
): Promise<T> {
  const headers = new Headers(init.headers);
  if (init.body && !(init.body instanceof FormData))
    headers.set('Content-Type', 'application/json');
  const session = getSession();
  if (authenticated) {
    if (!session || Date.now() >= session.expires) {
      setSession(null);
      throw new ApiError(401, 'Tu sesión venció. Inicia sesión nuevamente.');
    }
    headers.set('Authorization', `Bearer ${session.token}`);
  }
  let response: Response;
  try {
    response = await fetch(base + path, { ...init, headers });
  } catch {
    throw new Error('No se pudo conectar. Comprueba tu conexión e intenta nuevamente.');
  }
  if (response.status === 401 && authenticated && getSession() === session) setSession(null);
  if (!response.ok) {
    let message = `No se pudo completar la operación (${response.status}).`;
    let retryAfter = Number(response.headers.get('Retry-After')) || 0;
    try {
      const data = (await response.json()) as {
        error?: string;
        detail?: string;
        campos?: Record<string, unknown>;
        reintentarEnSegundos?: number;
      };
      retryAfter = Number(data.reintentarEnSegundos) || retryAfter;
      const fields = Object.entries(data.campos ?? {})
        .map(([key, value]) => `${key}: ${Array.isArray(value) ? value.join(', ') : String(value)}`)
        .join(' · ');
      message = [data.error ?? data.detail ?? message, fields].filter(Boolean).join(' · ');
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
export async function downloadDjango(path: string, filename: string) {
  const session = getSession();
  if (!session || Date.now() >= session.expires) {
    setSession(null);
    throw new ApiError(401, 'Tu sesión venció. Inicia sesión nuevamente.');
  }
  const response = await fetch(djangoBase + path, { headers: { Authorization: `Bearer ${session.token}` } });
  if (!response.ok) {
    let message = `No se pudo descargar el documento (${response.status}).`;
    try { const body = (await response.json()) as { detail?: string }; message = body.detail ?? message; } catch { /* conservar mensaje */ }
    throw new ApiError(response.status, message);
  }
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
