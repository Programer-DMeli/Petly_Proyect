import { useEffect, useSyncExternalStore } from 'react';
import { Outlet, ScrollRestoration, useLocation } from 'react-router-dom';
import { getSession, subscribe, getRestoreStatus, restoreSession } from './services/session';
import { conectarAvisos } from './services/avisosEnVivo';
import WorkspaceHeader from './components/WorkspaceHeader';
import PublicHeader from './components/PublicHeader';
import './styles/workspace.css';

export default function App() {
  const session = useSyncExternalStore(subscribe, getSession);
  const location = useLocation();
  const isLanding = location.pathname === '/' && !session;
  const isAbout = location.pathname === '/sobre-petly';
  const isWorkspace = session && !isAbout;
  const restoreStatus = useSyncExternalStore(subscribe, getRestoreStatus);
  const adoptante = session?.identity.rol === 'ADOPTANTE' ? session.identity.id : null;
  useEffect(() => { void restoreSession(); }, []);
  useEffect(() => { if (adoptante) return conectarAvisos(); }, [adoptante]);
  return (
    <div className={`app${isLanding ? ' app--welcome' : ''}${isAbout ? ' app--landing' : ''}${isWorkspace ? ' app--workspace' : ''}`}>
      <a className="skip-link" href="#contenido-principal">
        Saltar al contenido principal
      </a>
      {isWorkspace ? <WorkspaceHeader session={session} /> : <PublicHeader />}
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
      <ScrollRestoration />
    </div>
  );
}
