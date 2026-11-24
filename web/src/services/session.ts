import { ApiError, springBase } from './apiConfig';

export type Role = 'ADMIN' | 'ALBERGUE' | 'ADOPTANTE';
export interface Identity {
  id: string;
  correo: string;
  rol: Role;
}
export interface Session {
  token: string;
  identity: Identity;
  expires: number;
  generation: string;
}
export interface SessionReply {
  token: string;
  expiresIn: number;
  user: Omit<Identity, 'id'> & { id: string | number };
}
type RestoreStatus = 'loading' | 'ready' | 'retry';
type SessionEvent = { type: 'session'; session: Session }
  | { type: 'logout'; message?: string }
  | { type: 'request' };
const margin = 60_000;
const pendingKey = 'petly.refresh.pending';
const activeKey = 'petly.session.active';
const retryKey = 'petly.refresh.retryAt';
const lockName = 'petly.spring.session';
const expired = 'Tu sesión venció o fue revocada. Inicia sesión nuevamente.';
const channel = typeof BroadcastChannel === 'undefined' ? null : new BroadcastChannel(lockName);

let current: Session | null = null;
let status: RestoreStatus = 'loading';
let notice = '';
let renewal: Promise<Session> | null = null;
let restore: Promise<void> | null = null;
const listeners = new Set<() => void>();

export const getSession = () => current;
export const getRestoreStatus = () => status;
export const getSessionNotice = () => notice;
export const sessionHeaders = { 'X-Petly-Client': 'web' };

function publish(session: Session | null, message = '') {
  current = session;
  status = 'ready';
  notice = message;
  listeners.forEach(fn => fn());
}

channel?.addEventListener('message', ({ data }: MessageEvent<SessionEvent>) => {
  if (data.type === 'session' && localStorage.getItem(activeKey) === data.session.generation) {
    publish(data.session);
  } else if (data.type === 'logout' && !localStorage.getItem(activeKey)) {
    publish(null, data.message ?? '');
  } else if (data.type === 'request' && usable()) {
    channel.postMessage({ type: 'session', session: current });
  }
});

export function subscribe(fn: () => void) {
  listeners.add(fn);
  return () => { listeners.delete(fn); };
}

function usable(rejected?: string): boolean {
  return !!current && current.generation === localStorage.getItem(activeKey) && current.token !== rejected && Date.now() + margin < current.expires;
}

async function locked<T>(action: () => Promise<T>): Promise<T> {
  if (!navigator.locks || !channel) {
    return Promise.reject(new Error('Este navegador no permite sincronizar la sesión. Usa un navegador actualizado.'));
  }
  return await navigator.locks.request(lockName, action);
}

function accept(reply: SessionReply): Session {
  if (!reply.token || reply.expiresIn !== 3600 || !reply.user?.id ||
      !['ADMIN', 'ALBERGUE', 'ADOPTANTE'].includes(reply.user.rol)) {
    throw new Error('La respuesta de sesión es inválida. Intenta nuevamente.');
  }
  // El JWT y la identidad se comparten solo en memoria entre pestañas del mismo origen.
  const session = {
    token: reply.token,
    identity: { ...reply.user, id: String(reply.user.id) },
    expires: Date.now() + reply.expiresIn * 1000,
    generation: crypto.randomUUID(),
  };
  localStorage.removeItem(pendingKey);
  localStorage.removeItem(retryKey);
  localStorage.setItem(activeKey, session.generation);
  publish(session);
  channel?.postMessage({ type: 'session', session });
  return session;
}

async function postSession(path: '/refresh' | '/logout', body: object) {
  try {
    return await fetch(springBase + '/api/auth' + path, {
      method: 'POST',
      credentials: 'include',
      cache: 'no-store',
      headers: { ...sessionHeaders, 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
      signal: AbortSignal.timeout(15_000),
    });
  } catch {
    throw new Error('No se pudo conectar para actualizar la sesión. Comprueba tu conexión e intenta nuevamente.');
  }
}

// Tras adquirir el bloqueo, permite que la pestaña que ya renovó entregue su JWT por el canal.
function askTabs(rejected?: string) {
  if (!channel || !localStorage.getItem(activeKey) || usable(rejected)) return Promise.resolve();
  const bus = channel;
  return new Promise<void>(resolve => {
    const done = () => { window.clearTimeout(timer); unsubscribe(); resolve(); };
    const unsubscribe = subscribe(() => { if (usable(rejected)) done(); });
    const timer = window.setTimeout(done, 150);
    bus.postMessage({ type: 'request' });
  });
}

export function renewSession(rejected?: string): Promise<Session> {
  if (renewal) return renewal;
  if (usable(rejected)) return Promise.resolve(current!);
  renewal = locked(async () => {
    if (usable(rejected)) return current!;
    await askTabs(rejected);
    if (usable(rejected)) return current!;
    if (Number(localStorage.getItem(retryKey)) > Date.now()) {
      throw new Error('No se pudo renovar la sesión. Intenta nuevamente en unos segundos.');
    }
    const hadSession = !!current || !!localStorage.getItem(activeKey);
    const requestId = localStorage.getItem(pendingKey) ?? crypto.randomUUID();
    localStorage.setItem(pendingKey, requestId);
    let response: Response;
    try {
      response = await postSession('/refresh', { requestId });
    } catch (error) {
      localStorage.setItem(retryKey, String(Date.now() + 2_000));
      throw error;
    }
    if (response.status === 401) {
      localStorage.removeItem(pendingKey);
      localStorage.removeItem(activeKey);
      const message = hadSession ? expired : '';
      publish(null, message);
      channel?.postMessage({ type: 'logout', message });
      throw new ApiError(401, expired);
    }
    if (!response.ok) {
      const retryAfter = Math.min(60, Number(response.headers.get('Retry-After')) || 0);
      localStorage.setItem(retryKey, String(Date.now() + Math.max(2, retryAfter) * 1_000));
      throw new ApiError(response.status, 'No se pudo renovar la sesión. Intenta nuevamente.');
    }
    return accept(await response.json() as SessionReply);
  }).finally(() => { renewal = null; });
  return renewal;
}

export function restoreSession() {
  if (restore) return restore;
  if (current || status === 'ready') return Promise.resolve();
  status = 'loading';
  listeners.forEach(fn => fn());
  restore = renewSession().then(() => undefined, () => {
    if (status === 'loading') {
      status = 'retry';
      listeners.forEach(fn => fn());
    }
  }).finally(() => { restore = null; });
  return restore;
}

export function loginSession(login: () => Promise<SessionReply>) {
  return locked(async () => accept(await login()));
}

export function logoutSession() {
  return locked(async () => {
    const response = await postSession('/logout', {});
    if (!response.ok && response.status !== 401) {
      throw new ApiError(response.status, 'No se pudo cerrar la sesión. Intenta nuevamente.');
    }
    forgetSession();
  });
}

export function resetWebPassword(confirm: () => Promise<unknown>) {
  return locked(async () => {
    await confirm();
    forgetSession();
  });
}

function forgetSession() {
  localStorage.removeItem(pendingKey);
  localStorage.removeItem(activeKey);
  localStorage.removeItem(retryKey);
  publish(null);
  channel?.postMessage({ type: 'logout' });
}

export function clearSessionLocally() {
  forgetSession();
}

export function roleHome(role: Role) {
  return role === 'ADMIN'
    ? '/administracion/albergues'
    : role === 'ALBERGUE'
      ? '/albergue'
      : '/adoptante/catalogo';
}
