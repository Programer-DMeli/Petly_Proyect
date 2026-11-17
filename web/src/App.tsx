import { useEffect, useSyncExternalStore } from 'react';
import { Link, Outlet, useLocation } from 'react-router-dom';
import { getSession, subscribe, getRestoreStatus, restoreSession } from './services/session';
import { conectarAvisos } from './services/avisosEnVivo';
import WorkspaceHeader from './components/WorkspaceHeader';
import './styles/workspace.css';

export default function App() {
  const session = useSyncExternalStore(subscribe, getSession);
  const location = useLocation();
  const isLanding = location.pathname === '/' && !session;
  const restoreStatus = useSyncExternalStore(subscribe, getRestoreStatus);
  const adoptante = session?.identity.rol === 'ADOPTANTE' ? session.identity.id : null;
  useEffect(() => { void restoreSession(); }, []);
  useEffect(() => { if (adoptante) return conectarAvisos(); }, [adoptante]);
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
        {restoreStatus === 'loading' ? <p role="status">Recuperando sesión…</p>
          : restoreStatus === 'retry' ? (
            <section className="card">
              <p role="alert">No se pudo comprobar tu sesión. Comprueba tu conexión e intenta nuevamente.</p>
              <button onClick={() => void restoreSession()}>Reintentar</button>
            </section>
          ) : <Outlet />}
      </main>
      <footer className="app__footer">
        <strong>Petly</strong> · Adopción responsable <span aria-hidden="true"> · </span>
        <span>Cada encuentro merece una nueva oportunidad.</span>
      </footer>
    </div>
  );
}
