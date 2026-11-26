import { Navigate, useLocation } from 'react-router-dom';
import { getSession } from '../../../services/session';
import { LandingPlant, WalkingKitten, landingArt } from '../LandingScene';
import AccesoPage from './AccesoPage';
import { accessDestination } from '../accessDestination';

export default function HomePage() {
  const session = getSession();
  const location = useLocation();
  if (session)
    return <Navigate to={accessDestination(session.identity.rol, location.state)} replace />;

  return (
    <section className="landing" aria-labelledby="bienvenida-petly">
      <LandingPlant side="left" />
      <LandingPlant side="right" />
      <div className="landing__grid">
        <div className="landing__welcome">
          <div className="landing__headline">
            <h1 id="bienvenida-petly">
              Un abrazo que
              <br />
              cambia vidas
            </h1>
            <p>Bienvenido a Petly</p>
          </div>
          <div
            className="landing__pets"
            role="img"
            aria-label="Un perro golden retriever y un gato atigrado"
          >
            <div className="landing__rug" aria-hidden="true" />
            <img className="landing__dog" src={`${landingArt}/perro.webp`} alt="" />
            <img className="landing__cat" src={`${landingArt}/gato.webp`} alt="" />
            <img className="landing__bowl" src={`${landingArt}/plato.webp`} alt="" />
            <img className="landing__ball" src={`${landingArt}/pelota.webp`} alt="" />
          </div>
        </div>
        <div className="landing__access">
          <AccesoPage />
          <WalkingKitten />
        </div>
      </div>
    </section>
  );
}
