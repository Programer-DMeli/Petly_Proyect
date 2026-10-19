import { Link, Navigate } from 'react-router-dom';
import { getSession, roleHome } from '../../../services/session';
export default function HomePage() {
  const session = getSession();
  if (session) return <Navigate to={roleHome(session.identity.rol)} replace />;
  return (
    <section className="hero">
      <div className="hero__content">
        <span className="hero__eyebrow">Adopta con el corazón</span>
        <h1>
          Un hogar cambia
          <br />
          una vida <span aria-hidden="true">♡</span>
        </h1>
        <p>Conoce mascotas de albergues y prepara tu perfil para una adopción responsable.</p>
        <div className="actions">
          <Link className="button" to="/acceso">
            Iniciar sesión
          </Link>
          <Link className="button secondary" to="/registro">
            Crear cuenta de adoptante
          </Link>
        </div>
      </div>
      <img className="hero__pets" src="/images/mascotas-portada.webp" alt="Un perro y un gato buscando un hogar" />
    </section>
  );
}
