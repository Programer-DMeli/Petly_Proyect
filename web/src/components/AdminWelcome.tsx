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
            ⌂
          </span>
          <span>
            <strong>Albergues y responsables</strong>
            <small>Organiza la red que hace posible cada encuentro.</small>
          </span>
          <span aria-hidden="true">↗</span>
        </Link>
        <Link to="/administracion/verificaciones-kyc">
          <span className="admin-tool-icon" aria-hidden="true">
            ✓
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
