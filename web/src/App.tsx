import { useEffect, useRef, useState, useSyncExternalStore } from 'react';
import { Link, NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { getSession, subscribe, setSession } from './services/session';
export default function App() {
  const session = useSyncExternalStore(subscribe, getSession);
  const navigate = useNavigate();
  const location = useLocation();
  const headerRef = useRef<HTMLElement>(null);
  const [openPanel, setOpenPanel] = useState<'navigation' | 'profile' | null>(null);
  const isLanding = location.pathname === '/' && !session;
  useEffect(() => {
    if (!session) return;
    const timer = setTimeout(() => setSession(null), Math.max(0, session.expires - Date.now()));
    return () => clearTimeout(timer);
  }, [session]);
  useEffect(() => {
    const closeOnOutsideClick = (event: PointerEvent) => {
      if (headerRef.current && !headerRef.current.contains(event.target as Node)) {
        setOpenPanel(null);
      }
    };
    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setOpenPanel(null);
    };
    document.addEventListener('pointerdown', closeOnOutsideClick);
    document.addEventListener('keydown', closeOnEscape);
    return () => {
      document.removeEventListener('pointerdown', closeOnOutsideClick);
      document.removeEventListener('keydown', closeOnEscape);
    };
  }, []);

  const menuItems = session?.identity.rol === 'ADOPTANTE'
    ? [
        { label: 'Catálogo de mascotas', to: '/adoptante/catalogo' },
        { label: 'Mi cuestionario', to: '/adoptante/cuestionario' },
        { label: 'Autorizaciones', to: '/adoptante/autorizaciones' },
      ]
    : session?.identity.rol === 'ALBERGUE'
      ? [
          { label: 'Panel del albergue', to: '/albergue' },
          { label: 'Solicitudes', to: '/albergue/solicitudes' },
        ]
      : session?.identity.rol === 'ADMIN'
        ? [{ label: 'Administración de albergues', to: '/administracion/albergues' }]
        : [];

  return (
    <div className={`app${isLanding ? ' app--landing' : ''}`}>
      <header className="app__header" ref={headerRef}>
        <Link className="app__logo" to="/">
          <img src="/images/petly-logo.png" alt="Petly" />
        </Link>
        <span className="app__tagline">
          <span className="app__tagline-heart" aria-hidden="true">♥</span>
          Encuentra a tu compañero
        </span>
        {session && (
          <div className="app__account-actions">
            <div className="header-popover-wrap">
              <button
                className="header-action"
                type="button"
                aria-label="Abrir menú de navegación"
                aria-expanded={openPanel === 'navigation'}
                aria-controls={openPanel === 'navigation' ? 'petly-navigation-menu' : undefined}
                onClick={() => setOpenPanel(openPanel === 'navigation' ? null : 'navigation')}
              >
                <svg aria-hidden="true" viewBox="0 0 24 24" fill="none">
                  <path d="M4 6h16M4 12h16M4 18h16" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
                </svg>
                <span>Menú</span>
              </button>
              {openPanel === 'navigation' && (
                <nav className="header-popover header-popover--navigation" id="petly-navigation-menu" aria-label="Navegación principal">
                  <p className="header-popover__eyebrow">Ir a</p>
                  {menuItems.map(item => (
                    <NavLink key={item.to} to={item.to} onClick={() => setOpenPanel(null)}>
                      {item.label}
                    </NavLink>
                  ))}
                </nav>
              )}
            </div>
            <div className="header-popover-wrap">
              <button
                className="header-action header-action--user"
                type="button"
                aria-label="Abrir menú de usuario"
                aria-expanded={openPanel === 'profile'}
                aria-controls={openPanel === 'profile' ? 'petly-user-menu' : undefined}
                onClick={() => setOpenPanel(openPanel === 'profile' ? null : 'profile')}
              >
                <svg aria-hidden="true" viewBox="0 0 24 24" fill="none">
                  <circle cx="12" cy="8" r="3.5" stroke="currentColor" strokeWidth="1.8" />
                  <path d="M5 20c.5-3.4 3.2-5.3 7-5.3s6.5 1.9 7 5.3" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
                </svg>
                <span className="header-action__user-label">Mi cuenta</span>
              </button>
              {openPanel === 'profile' && (
                <div className="header-popover header-popover--profile" id="petly-user-menu" aria-label="Cuenta de usuario">
                  <p className="header-popover__eyebrow">Sesión iniciada como</p>
                  <p className="header-popover__email">{session.identity.correo}</p>
                  <span className="header-popover__role">{session.identity.rol}</span>
                  <button
                    className="header-popover__logout"
                    type="button"
                    onClick={() => {
                      setOpenPanel(null);
                      setSession(null);
                      void navigate('/acceso');
                    }}
                  >
                    Cerrar sesión
                  </button>
                </div>
              )}
            </div>
          </div>
        )}
      </header>
      <main className="app__main">
        <Outlet />
      </main>
      <footer className="app__footer">Petly · Adopción responsable · Proyecto académico</footer>
    </div>
  );
}
