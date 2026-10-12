import { useEffect, useSyncExternalStore } from 'react';
import { Link, Outlet, useNavigate } from 'react-router-dom';
import { getSession, subscribe, setSession } from './services/session';
export default function App() {
  const session = useSyncExternalStore(subscribe, getSession);
  const navigate = useNavigate();
  useEffect(() => {
    if (!session) return;
    const timer = setTimeout(() => setSession(null), Math.max(0, session.expires - Date.now()));
    return () => clearTimeout(timer);
  }, [session]);
  return (
    <div className="app">
      <header className="app__header">
        <Link className="app__logo" to="/">
          ♧ Petly
        </Link>
        <span className="app__tagline">Encuentra a tu compañero</span>
        {session && (
          <>
            <span className="account">
              {session.identity.correo} · {session.identity.rol}
            </span>
            <button
              className="secondary"
              onClick={() => {
                setSession(null);
                void navigate('/acceso');
              }}
            >
              Cerrar sesión
            </button>
          </>
        )}
      </header>
      <main className="app__main">
        <Outlet />
      </main>
      <footer className="app__footer">Petly · Adopción responsable · Proyecto académico</footer>
    </div>
  );
}
