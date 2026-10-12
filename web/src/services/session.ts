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
}
let current: Session | null = null;
const listeners = new Set<() => void>();
export function getSession() {
  return current;
}
export function setSession(session: Session | null) {
  current = session;
  listeners.forEach(fn => fn());
}
export function subscribe(fn: () => void) {
  listeners.add(fn);
  return () => {
    listeners.delete(fn);
  };
}
export function roleHome(role: Role) {
  return role === 'ADMIN'
    ? '/administracion/albergues'
    : role === 'ALBERGUE'
      ? '/albergue'
      : '/adoptante/catalogo';
}
