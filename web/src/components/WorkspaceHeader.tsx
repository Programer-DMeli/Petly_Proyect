import { useEffect, useRef, useState } from 'react';
import { Link, NavLink, useLocation, useNavigate } from 'react-router-dom';
import { setSession, type Session } from '../services/session';

type Item = { label: string; to: string; description: string };
type Group = { label: string; items: Item[] };
const groups: Record<string, Group[]> = {
  ADOPTANTE: [
    {
      label: 'Mi adopción',
      items: [
        {
          label: 'Enviar solicitud de adopción',
          to: '/adoptante/catalogo',
          description: 'Elige una mascota disponible y completa tu solicitud.',
        },
        {
          label: 'Mis solicitudes',
          to: '/adoptante/solicitudes',
          description: 'Consulta el estado de cada encuentro.',
        },
        {
          label: 'Favoritos',
          to: '/adoptante/favoritos',
          description: 'Los compañeros que te robaron el corazón.',
        },
        {
          label: 'Eventos',
          to: '/adoptante/eventos',
          description: 'Encuentros de adopción presencial.',
        },
      ],
    },
    {
      label: 'Mi perfil',
      items: [
        {
          label: 'Mi cuestionario',
          to: '/adoptante/cuestionario',
          description: 'Tu estilo de vida y convivencia.',
        },
        {
          label: 'Disponibilidad',
          to: '/adoptante/disponibilidad',
          description: 'Organiza tus horarios de visita.',
        },
        {
          label: 'Autorizaciones',
          to: '/adoptante/autorizaciones',
          description: 'Decide con quién compartir tu perfil.',
        },
        {
          label: 'Verificación de identidad',
          to: '/adoptante/verificacion-identidad',
          description: 'Consulta y gestiona tus documentos.',
        },
      ],
    },
  ],
  ALBERGUE: [
    {
      label: 'Mi albergue',
      items: [
        {
          label: 'Datos del albergue',
          to: '/albergue?seccion=datos',
          description: 'Información institucional y contacto.',
        },
        {
          label: 'Infraestructura',
          to: '/albergue?seccion=infraestructura',
          description: 'Organiza tus áreas y capacidad.',
        },
      ],
    },
    {
      label: 'Adopciones',
      items: [
        {
          label: 'Mascotas registradas',
          to: '/albergue/mascotas',
          description: 'Fotografías, fichas y afiches.',
        },
        {
          label: 'Solicitudes',
          to: '/albergue/solicitudes',
          description: 'Acompaña y evalúa cada postulación.',
        },
        {
          label: 'Perfiles autorizados',
          to: '/albergue?seccion=perfiles',
          description: 'Consulta información con consentimiento.',
        },
      ],
    },
    {
      label: 'Comunidad',
      items: [
        {
          label: 'Eventos',
          to: '/albergue/eventos',
          description: 'Publica y organiza encuentros.',
        },
      ],
    },
  ],
  ADMIN: [
    {
      label: 'Gestión',
      items: [
        {
          label: 'Albergues y responsables',
          to: '/administracion/albergues',
          description: 'Registro, consulta y administración.',
        },
        {
          label: 'Revisión de identidad',
          to: '/administracion/verificaciones-kyc',
          description: 'Revisa los casos y sus documentos.',
        },
      ],
    },
  ],
};
const labels: Record<string, string> = {
  ADOPTANTE: 'Espacio del adoptante',
  ALBERGUE: 'Espacio del albergue',
  ADMIN: 'Administración',
};
export default function WorkspaceHeader({ session }: { session: Session }) {
  const [open, setOpen] = useState<string | null>(null);
  const [mobile, setMobile] = useState(false);
  const ref = useRef<HTMLElement>(null);
  const trigger = useRef<HTMLButtonElement | null>(null);
  const location = useLocation();
  const navigate = useNavigate();
  const role = session.identity.rol;
  const home =
    role === 'ADMIN'
      ? '/administracion/albergues'
      : role === 'ALBERGUE'
        ? '/albergue'
        : '/adoptante/catalogo';
  useEffect(() => {
    setOpen(null);
    setMobile(false);
  }, [location.key]);
  useEffect(() => {
    function outside(event: PointerEvent) {
      if (!ref.current?.contains(event.target as Node)) {
        setOpen(null);
        setMobile(false);
      }
    }
    function escape(event: KeyboardEvent) {
      if (event.key === 'Escape') {
        if (open) {
          setOpen(null);
          trigger.current?.focus();
        } else if (mobile) {
          setMobile(false);
          ref.current?.querySelector<HTMLButtonElement>('.workspace-mobile-toggle')?.focus();
        }
      }
    }
    document.addEventListener('pointerdown', outside);
    document.addEventListener('keydown', escape);
    return () => {
      document.removeEventListener('pointerdown', outside);
      document.removeEventListener('keydown', escape);
    };
  }, [open, mobile]);
  function toggle(name: string, button: HTMLButtonElement) {
    trigger.current = button;
    setOpen(current => (current === name ? null : name));
  }
  return (
    <header
      className="workspace-header"
      ref={ref}
      onBlur={event => {
        if (event.relatedTarget && !event.currentTarget.contains(event.relatedTarget)) {
          setOpen(null);
          setMobile(false);
        }
      }}
    >
      <div className="workspace-header__inner">
        <Link to={home} className="workspace-brand" aria-label="Petly, ir al inicio">
          <img src="/images/petly-logo.png" alt="Petly" />
          <span>{labels[role]}</span>
        </Link>
        <button
          type="button"
          className="workspace-mobile-toggle"
          aria-label="Abrir menú de navegación"
          aria-expanded={mobile}
          aria-controls="workspace-navigation"
          onClick={event => {
            trigger.current = event.currentTarget;
            setMobile(!mobile);
            setOpen(null);
          }}
        >
          <span aria-hidden="true">☰</span> Menú
        </button>
        <nav
          id="workspace-navigation"
          className={`workspace-nav${mobile ? ' is-open' : ''}`}
          aria-label="Navegación principal"
        >
          <NavLink end to={home} className="workspace-nav__home">
            {role === 'ADOPTANTE' ? 'Explorar mascotas' : 'Inicio'}
          </NavLink>
          {groups[role]?.map((group, index) => (
            <div className="workspace-dropdown" key={group.label}>
              <button
                type="button"
                className={`workspace-nav__trigger${group.items.some(item => location.pathname + location.search === item.to) ? ' is-current' : ''}`}
                aria-expanded={open === group.label}
                aria-controls={`workspace-group-${index}`}
                onClick={event => toggle(group.label, event.currentTarget)}
                onKeyDown={event => {
                  if (event.key === 'ArrowDown') {
                    event.preventDefault();
                    trigger.current = event.currentTarget;
                    setOpen(group.label);
                    requestAnimationFrame(() =>
                      document
                        .querySelector<HTMLAnchorElement>(`#workspace-group-${index} a`)
                        ?.focus()
                    );
                  }
                }}
              >
                {group.label}
                <span className="nav-chevron" aria-hidden="true" />
              </button>
              {open === group.label && (
                <div id={`workspace-group-${index}`} className="workspace-menu">
                  <p>{group.label}</p>
                  {group.items.map(item => (
                    <NavLink key={item.to} to={item.to}>
                      <strong>{item.label}</strong>
                      <span>{item.description}</span>
                    </NavLink>
                  ))}
                </div>
              )}
            </div>
          ))}
        </nav>
        <div className="workspace-account workspace-dropdown">
          <button
            type="button"
            className="workspace-account__trigger"
            aria-label="Abrir menú de usuario"
            aria-expanded={open === 'cuenta'}
            aria-controls="workspace-account-menu"
            onClick={event => toggle('cuenta', event.currentTarget)}
          >
            <span className="workspace-avatar" aria-hidden="true">
              {session.identity.correo.slice(0, 1).toUpperCase()}
            </span>
            <span className="workspace-account__label">Mi cuenta</span>
            <span className="nav-chevron" aria-hidden="true" />
          </button>
          {open === 'cuenta' && (
            <div id="workspace-account-menu" className="workspace-menu workspace-menu--account">
              <p>Sesión iniciada como</p>
              <strong className="workspace-account__email">{session.identity.correo}</strong>
              <span className="badge">{labels[role]}</span>
              <button
                type="button"
                onClick={() => {
                  setSession(null);
                  void navigate('/acceso');
                }}
              >
                Cerrar sesión <span aria-hidden="true">↗</span>
              </button>
            </div>
          )}
        </div>
      </div>
    </header>
  );
}
