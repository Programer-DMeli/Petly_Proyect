import { useSyncExternalStore } from 'react';
import { Navigate } from 'react-router-dom';
import type { ReactNode } from 'react';
import { getSession, subscribe, roleHome } from '../services/session';
import type { Role } from '../services/session';
export default function ProtectedRoute({ children, role }: { children: ReactNode; role: Role }) {
  const session = useSyncExternalStore(subscribe, getSession);
  if (!session || session.expires <= Date.now()) return <Navigate to="/acceso" replace />;
  if (session.identity.rol !== role)
    return <Navigate to={roleHome(session.identity.rol)} replace />;
  return <>{children}</>;
}
