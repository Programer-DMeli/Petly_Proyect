import { Link } from 'react-router-dom';
export default function AdminWelcome() {
  return (
    <section className="admin-welcome" aria-labelledby="admin-welcome-title">
      <div>
        <span className="workspace-eyebrow">PETLY · CENTRO DE GESTIÓN</span>
        <h2 id="admin-welcome-title">
          Una comunidad mejor organizada.<em>Más oportunidades para ellos.</em>
        </h2>
        <p>
          Acompaña a los albergues y mantén la información de la comunidad clara, actualizada y en
          buenas manos.
        </p>
        <a className="button" href="#herramientas-admin">
          Ir a las herramientas <span aria-hidden="true">↗</span>
        </a>
      </div>
      <div className="admin-welcome__tools">
        <span className="admin-welcome__stamp" aria-hidden="true">
          ✦
        </span>
        <Link to="/administracion/albergues">
          <span className="admin-tool-icon" aria-hidden="true">
            <svg viewBox="0 0 24 24" focusable="false">
              <path d="m3 10 9-7 9 7M5 9v11a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1V9" />
              <path d="M9 21v-5a3 3 0 0 1 6 0v5" />
            </svg>
          </span>
          <span>
            <strong>Albergues y responsables</strong>
            <small>Organiza la red que hace posible cada encuentro.</small>
          </span>
          <span aria-hidden="true">↗</span>
        </Link>
        <Link to="/administracion/usuarios">
          <span className="admin-tool-icon" aria-hidden="true">
            <svg viewBox="0 0 24 24" focusable="false">
              <circle cx="9" cy="8" r="3" />
              <path d="M3 20v-2a4 4 0 0 1 4-4h4a4 4 0 0 1 4 4v2M16 5.2a3 3 0 0 1 0 5.6M21 20v-2a4 4 0 0 0-3-3.9" />
            </svg>
          </span>
          <span>
            <strong>Roles y cuentas</strong>
            <small>Administra los accesos de la comunidad Petly.</small>
          </span>
          <span aria-hidden="true">↗</span>
        </Link>
        <Link to="/administracion/verificaciones-kyc">
          <span className="admin-tool-icon" aria-hidden="true">
            <svg viewBox="0 0 24 24" focusable="false">
              <path d="M12 22s7-3.5 7-10V6l-7-3-7 3v6c0 6.5 7 10 7 10Z" />
              <path d="m9 12 2 2 4-4" />
            </svg>
          </span>
          <span>
            <strong>Revisión de identidad</strong>
            <small>Consulta los casos y sus documentos privados.</small>
          </span>
          <span aria-hidden="true">↗</span>
        </Link>
        <p>La confianza también se construye con una buena gestión.</p>
      </div>
    </section>
  );
}
