import { useEffect, useState, useSyncExternalStore } from 'react';
import { Link, Outlet, ScrollRestoration, useLocation } from 'react-router-dom';
import { getSession, subscribe, getRestoreStatus, restoreSession } from './services/session';
import { conectarAvisos } from './services/avisosEnVivo';
import WorkspaceHeader from './components/WorkspaceHeader';
import PublicHeader from './components/PublicHeader';
import './styles/workspace.css';

export default function App() {
  const session = useSyncExternalStore(subscribe, getSession);
  const [footerContent, setFooterContent] = useState<HTMLDivElement | null>(null);
  const location = useLocation();
  const pathname = location.pathname.replace(/\/+$/, '').toLowerCase() || '/';
  const isHome = pathname === '/';
  const isLanding = isHome || pathname === '/acceso';
  const isWorkspace = session && !isLanding;
  const switchLabel = isHome ? 'Ir a iniciar sesión' : 'Volver a Bienvenida';
  const restoreStatus = useSyncExternalStore(subscribe, getRestoreStatus);
  const adoptante = session?.identity.rol === 'ADOPTANTE' ? session.identity.id : null;
  useEffect(() => { void restoreSession(); }, []);
  useEffect(() => { if (adoptante) return conectarAvisos(); }, [adoptante]);
  return (
    <div className={`app${isLanding ? ' app--welcome' : ''}${isWorkspace ? ' app--workspace' : ''}`}>
      <a className="skip-link" href="#contenido-principal">
        Saltar al contenido principal
      </a>
      {!isLanding && (isWorkspace ? <WorkspaceHeader session={session} /> : <PublicHeader />)}
      <main id="contenido-principal" tabIndex={-1} className="app__main">
        {isLanding && (
          <nav aria-label="Cambiar pantalla">
            {(['left', 'right'] as const).map(side => (
              <Link
                key={side}
                className={`public-page-arrow public-page-arrow--${side}`}
                to={isHome ? '/acceso' : '/'}
                aria-label={switchLabel}
                title={switchLabel}
              >
                <svg
                  aria-hidden="true"
                  viewBox="0 0 24 24"
                  width="24"
                  height="24"
                  fill="none"
                  stroke="currentColor"
                  strokeWidth="2.5"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                >
                  <path d={side === 'left' ? 'm15 5-7 7 7 7' : 'm9 5 7 7-7 7'} />
                </svg>
              </Link>
            ))}
          </nav>
        )}
        {!isHome && restoreStatus === 'loading' ? <p role="status">Recuperando sesión…</p>
          : !isHome && restoreStatus === 'retry' ? (
            <section className="card">
              <p role="alert">No se pudo comprobar tu sesión. Comprueba tu conexión e intenta nuevamente.</p>
              <button onClick={() => void restoreSession()}>Reintentar</button>
            </section>
          ) : <Outlet context={footerContent} />}
      </main>
      <footer className="app__footer">
        <div className="app__footer-content" ref={setFooterContent} />
        <strong>Petly</strong> · Adopción responsable <span aria-hidden="true"> · </span>
        <span>Cada encuentro merece una nueva oportunidad.</span>
      </footer>
      <ScrollRestoration />
    </div>
  );
}
