import { useSyncExternalStore, type ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { accessDestination } from '../features/auth/accessDestination';
import { getSession, subscribe } from '../services/session';

export default function AdoptLink({
  children,
  className,
  petId,
}: {
  children: ReactNode;
  className?: string;
  petId?: number;
}) {
  const session = useSyncExternalStore(subscribe, getSession);
  const state = petId === undefined ? undefined : { mascotaId: String(petId) };
  return (
    <Link
      className={className}
      to={session ? accessDestination(session.identity.rol, state) : '/acceso'}
      state={state}
    >
      {children}
    </Link>
  );
}
