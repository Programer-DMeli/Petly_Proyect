import { useState } from 'react';
import { Link } from 'react-router-dom';
const messages = {
  adoptante: [
    {
      title: 'Un hogar cambia su mundo.',
      highlight: 'Y también el tuyo.',
      text: 'Detrás de cada mirada hay una historia. Descubre a tu próximo compañero y construyan juntos un nuevo comienzo.',
      label: 'Explorar mascotas',
      to: '#catalogo',
      tag: 'Encuentros que cambian vidas',
      image: 'imagen_1.png',
      imageAlt: 'Un cachorro en brazos, listo para encontrar un hogar',
    },
    {
      title: 'El amor empieza',
      highlight: 'con una decisión responsable.',
      text: 'Tu rutina, tu hogar y tus cuidados cuentan. Completa tu perfil para dar el primer paso con confianza.',
      label: 'Completar mi perfil',
      to: '/adoptante/cuestionario',
      tag: 'Adoptar es un compromiso',
      image: 'imagen_2.png',
      imageAlt: 'Una familia disfruta al aire libre junto a su perro',
    },
    {
      title: 'Un pequeño encuentro.',
      highlight: 'Una gran oportunidad.',
      text: 'Acércate a la comunidad, conoce a los albergues y descubre los próximos encuentros de adopción.',
      label: 'Ver próximos eventos',
      to: '/adoptante/eventos',
      tag: 'Conectados por su bienestar',
      image: 'imagen_3.png',
      imageAlt: 'Un niño abraza a su perro en el jardín',
    },
  ],
  albergue: [
    {
      title: 'Ustedes cuidan.',
      highlight: 'Juntos encontramos hogares.',
      text: 'Cada ficha, cada fotografía y cada solicitud acercan una nueva oportunidad a quienes más la necesitan.',
      label: 'Gestionar mascotas',
      to: '/albergue/mascotas',
      tag: 'Tu labor deja huella',
      image: 'imagen_6.png',
      imageAlt: 'Una cuidadora comparte un momento con los perros del albergue',
    },
    {
      title: 'Una adopción responsable',
      highlight: 'empieza escuchando.',
      text: 'Conoce a cada postulante, revisa su información autorizada y acompaña el proceso con cercanía.',
      label: 'Revisar solicitudes',
      to: '/albergue/solicitudes',
      tag: 'Cada historia merece cuidado',
      image: 'imagen_5.png',
      imageAlt: 'Un cachorro descansa protegido en los brazos de una persona',
    },
    {
      title: 'Haz que tu comunidad',
      highlight: 'se encuentre por ellos.',
      text: 'Comparte los próximos eventos de tu albergue y crea más oportunidades para conocer a un nuevo compañero.',
      label: 'Organizar un evento',
      to: '/albergue/eventos',
      tag: 'Más encuentros, más oportunidades',
      image: 'imagen_4.png',
      imageAlt: 'Una persona sostiene a un cachorro durante un encuentro al aire libre',
    },
  ],
};
export default function AdoptionCarousel({ role }: { role: 'adoptante' | 'albergue' }) {
  const [index, setIndex] = useState(0);
  const slides = messages[role];
  const slide = slides[index];
  function move(next: number) {
    setIndex((next + slides.length) % slides.length);
  }
  return (
    <section
      className="adoption-carousel"
      role="region"
      aria-roledescription="carrusel"
      aria-label="Mensajes de adopción responsable"
    >
      <div className="adoption-carousel__content" aria-live="polite" aria-atomic="true">
        <span className="workspace-eyebrow">
          <span aria-hidden="true">✦</span> {slide.tag}
        </span>
        <h2>
          {slide.title}
          <em>{slide.highlight}</em>
        </h2>
        <p>{slide.text}</p>
        {slide.to.startsWith('#') ? (
          <a className="button carousel-cta" href={slide.to}>
            {slide.label}
            <span aria-hidden="true">↗</span>
          </a>
        ) : (
          <Link className="button carousel-cta" to={slide.to}>
            {slide.label}
            <span aria-hidden="true">↗</span>
          </Link>
        )}
      </div>
      <div className="adoption-carousel__visual">
        <img
          src={`/images/carruseles/${slide.image}`}
          alt={slide.imageAlt}
          fetchPriority="high"
        />
        <span className="carousel-note">
          <span aria-hidden="true">♥</span> Un vínculo para toda la vida.
        </span>
      </div>
      <div className="carousel-controls">
        <span className="carousel-counter">
          0{index + 1}
          <span> / 0{slides.length}</span>
        </span>
        <div className="carousel-dots">
          {slides.map((item, n) => (
            <button
              type="button"
              key={item.tag}
              aria-label={`Ver mensaje ${n + 1}: ${item.tag}`}
              aria-pressed={n === index}
              onClick={() => move(n)}
            >
              <span />
            </button>
          ))}
        </div>
        <div className="carousel-arrows">
          <button type="button" aria-label="Mensaje anterior" onClick={() => move(index - 1)}>
            ←
          </button>
          <button type="button" aria-label="Mensaje siguiente" onClick={() => move(index + 1)}>
            →
          </button>
        </div>
      </div>
    </section>
  );
}
