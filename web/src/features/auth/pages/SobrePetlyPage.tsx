export default function SobrePetlyPage() {
  return (
    <section className="about-petly" aria-labelledby="sobre-petly-titulo">
      <div className="hero">
        <div className="hero__content">
          <span className="hero__eyebrow">Adopta con el corazón</span>
          <h1 id="sobre-petly-titulo">
            Un hogar cambia
            <br />
            una vida <span aria-hidden="true">♡</span>
          </h1>
          <p>Conoce mascotas de albergues y prepara tu perfil para una adopción responsable.</p>
        </div>
        <img
          className="hero__pets"
          src="/images/mascotas-portada.webp"
          alt="Un perro y un gato buscando un hogar"
        />
      </div>
      <div className="about-petly__body">
        <div className="about-petly__intro">
          <h2>¿Qué es Petly?</h2>
          <p>
            Imaginar cómo sería compartir tu día con una mascota es un buen comienzo. Petly conecta
            a personas que quieren adoptar con albergues y les ayuda a dar el siguiente paso con
            calma, desde conocer a cada animal hasta preparar una solicitud.
          </p>
        </div>
        <div className="about-petly__values">
          <section>
            <h3>Beneficios</h3>
            <p>
              Conoce las necesidades de cada mascota, guarda tus favoritas y consulta el estado de
              tu solicitud en un mismo lugar.
            </p>
          </section>
          <section>
            <h3>Misión</h3>
            <p>
              Facilitar adopciones responsables que tengan en cuenta el bienestar de los animales y
              la vida cotidiana de quienes los reciben.
            </p>
          </section>
          <section>
            <h3>Visión</h3>
            <p>
              Que cada mascota de un albergue tenga la oportunidad de encontrar un hogar seguro y
              preparado para acompañarla toda su vida.
            </p>
          </section>
        </div>
        <section id="como-funciona" className="about-petly__steps" aria-labelledby="pasos-petly">
          <h2 id="pasos-petly">Cómo funciona</h2>
          <ol>
            <li>Completa tu perfil y cuéntanos cómo es tu hogar.</li>
            <li>Explora las mascotas y conoce sus necesidades.</li>
            <li>Envía tu solicitud y sigue su avance junto al albergue.</li>
          </ol>
        </section>
      </div>
    </section>
  );
}
