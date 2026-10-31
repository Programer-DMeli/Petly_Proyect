import { useEffect, useSyncExternalStore } from 'react';
import { Link, Outlet, useLocation } from 'react-router-dom';
import { getSession, subscribe, setSession } from './services/session';
import WorkspaceHeader from './components/WorkspaceHeader';
import './styles/workspace.css';

export default function App() {
  const session = useSyncExternalStore(subscribe, getSession);
  const location = useLocation();
  const isLanding = location.pathname === '/' && !session;
  useEffect(() => {
    if (!session) return;
    const timer = setTimeout(() => setSession(null), Math.max(0, session.expires - Date.now()));
    return () => clearTimeout(timer);
  }, [session]);
  return (
    <div className={`app${isLanding ? ' app--landing' : ''}${session ? ' app--workspace' : ''}`}>
      <a className="skip-link" href="#contenido-principal">
        Saltar al contenido principal
      </a>
      {session ? (
        <WorkspaceHeader session={session} />
      ) : (
        <header className="app__header">
          <Link className="app__logo" to="/">
            <img src="/images/petly-logo.png" alt="Petly" />
          </Link>
          <span className="app__tagline">
            <span className="app__tagline-heart" aria-hidden="true">
              ♥
            </span>
            Encuentra a tu compañero
          </span>
        </header>
      )}
      <main id="contenido-principal" tabIndex={-1} className="app__main">
        <Outlet />
      </main>
      <footer className="app__footer">
        <strong>Petly</strong> · Adopción responsable <span aria-hidden="true"> · </span>
        <span>Cada encuentro merece una nueva oportunidad.</span>
      </footer>
    </div>
  );
}
