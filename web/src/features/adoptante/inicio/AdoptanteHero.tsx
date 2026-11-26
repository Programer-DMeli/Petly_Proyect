import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import './adoptante-hero.css';

const accesos = [
  { titulo: 'Conoce la', detalle: 'adopción responsable', ruta: '#ayuda-adopcion-titulo', icono: 'hogar' },
  { titulo: 'Encuentra tu', detalle: 'compatibilidad ideal', ruta: '/adoptante/cuestionario', icono: 'corazon' },
  { titulo: 'Participa en', detalle: 'próximos eventos', ruta: '/adoptante/eventos', icono: 'calendario' },
] as const;

function Icono({ tipo }: { tipo: (typeof accesos)[number]['icono'] | 'flecha' | 'destello' }) {
  const trazos = {
    hogar: 'M2 10.5 12 2l10 8.5M5 8v12h14V8m-3-2V3h3v5',
    corazon: 'M8 16.5C4.7 14 2 11.1 2 7.8A4.8 4.8 0 0 1 12 6a4.8 4.8 0 0 1 10 1.8c0 2.5-1.5 4.7-3.5 6.7',
    calendario: 'M7 3v4m10-4v4M4 10h16M4 6h16v14H4zM8 14h1m3 0h1m3 0h1m-9 3h1m3 0h1',
    flecha: 'M4 12h15m-6-6 6 6-6 6',
    destello: 'M12 2c1.4 6.1 3.9 8.6 10 10-6.1 1.4-8.6 3.9-10 10-1.4-6.1-3.9-8.6-10-10 6.1-1.4 8.6-3.9 10-10Z',
  };

  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7"
      strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d={trazos[tipo]} />
      {(tipo === 'hogar' || tipo === 'corazon') && (
        <g transform={tipo === 'hogar' ? 'translate(7 8) scale(.42)' : 'translate(7 8) scale(.62)'}>
          <path d="M12 12c-2 0-3 2-4.5 3.5-2 2-.5 5 2 4l2.5-1 2.5 1c2.5 1 4-2 2-4C15 14 14 12 12 12Z" />
          <ellipse cx="5" cy="10" rx="1.6" ry="2.2" transform="rotate(-25 5 10)" />
          <ellipse cx="9.5" cy="6.5" rx="1.6" ry="2.2" />
          <ellipse cx="14.5" cy="6.5" rx="1.6" ry="2.2" />
          <ellipse cx="19" cy="10" rx="1.6" ry="2.2" transform="rotate(25 19 10)" />
        </g>
      )}
    </svg>
  );
}

export default function AdoptanteHero() {
  const banner = useRef<HTMLElement>(null);
  const [enVista, setEnVista] = useState(false);
  const [paginaVisible, setPaginaVisible] = useState(() => !document.hidden);
  const [pausado, setPausado] = useState(false);
  const [reposoCargado, setReposoCargado] = useState(false);
  const [usarGatoLocal, setUsarGatoLocal] = useState(true);
  const [usarPerroLocal, setUsarPerroLocal] = useState(true);
  const movimientoActivo = enVista && paginaVisible && !pausado;

  useEffect(() => {
    const elemento = banner.current;
    if (!elemento) return;

    const observador = new IntersectionObserver(([entrada]) => setEnVista(entrada.isIntersecting));
    const actualizarVisibilidad = () => setPaginaVisible(!document.hidden);
    observador.observe(elemento);
    document.addEventListener('visibilitychange', actualizarVisibilidad);

    return () => {
      observador.disconnect();
      document.removeEventListener('visibilitychange', actualizarVisibilidad);
    };
  }, []);

  return (
    <section ref={banner} className="adoptante-hero" aria-labelledby="adoptante-hero-titulo"
      data-movimiento={movimientoActivo ? 'activo' : 'pausado'}>
      <div className="adoptante-hero__grid">
        <div className="adoptante-hero__contenido">
          <p className="adoptante-hero__intro"><Icono tipo="destello" /> Encuentros que cambian vidas</p>
          <h1 id="adoptante-hero-titulo">
            Un hogar cambia su mundo. <span>Y también el tuyo.</span>
          </h1>
          <p className="adoptante-hero__descripcion">
            Detrás de cada mirada hay una historia. Descubre a tu próximo compañero y construyan juntos un nuevo comienzo.
          </p>
          <div className="adoptante-hero__accion">
            <div className="adoptante-hero__gatito" aria-hidden="true" data-imagen-local={usarGatoLocal}>
              <img src={usarGatoLocal ? '/images/adoptante/gatito-banner.webp' : '/images/acceso/gato.webp'}
                alt="" width="460" height="374" onError={() => setUsarGatoLocal(false)} />
            </div>
            <a href="#catalogo" className="adoptante-hero__boton">
              Explorar mascotas <Icono tipo="flecha" />
            </a>
          </div>
        </div>

        <div className="adoptante-hero__mascota" aria-hidden="true">
          <div className="adoptante-hero__perro"
            data-reposo-cargado={usarPerroLocal && reposoCargado} data-imagen-local={usarPerroLocal}>
            <img className="adoptante-hero__pose-saludo"
              src={usarPerroLocal ? '/images/adoptante/perrito-saludo.webp' : '/images/acceso/perro.webp'}
              alt="" width="1050" height="960" fetchPriority="high" onError={() => setUsarPerroLocal(false)} />
            {usarPerroLocal && (
              <img className="adoptante-hero__pose-reposo"
                src="/images/adoptante/perrito-reposo.webp" alt="" width="1050" height="960"
                onLoad={() => setReposoCargado(true)} />
            )}
          </div>
        </div>

        <ul className="adoptante-hero__accesos" aria-label="Recursos para tu adopción">
          {accesos.map(({ titulo, detalle, ruta, icono }) => (
            <li key={ruta}>
              <Link to={ruta}>
                <Icono tipo={icono} />
                <span>{titulo}<strong>{detalle}</strong></span>
              </Link>
            </li>
          ))}
        </ul>
      </div>

      <svg className="adoptante-hero__decoracion" viewBox="0 0 1440 635" preserveAspectRatio="none" aria-hidden="true">
        <defs>
          <g id="adoptante-hero-huella">
            <ellipse cx="12" cy="27" rx="6" ry="9" transform="rotate(-25 12 27)" />
            <ellipse cx="24" cy="15" rx="6" ry="9" transform="rotate(-10 24 15)" />
            <ellipse cx="39" cy="15" rx="6" ry="9" transform="rotate(10 39 15)" />
            <ellipse cx="52" cy="27" rx="6" ry="9" transform="rotate(25 52 27)" />
            <path d="M32 30c-8 0-11 8-17 13-6 5-5 13 2 15 6 2 10-3 15-3s9 5 15 3c7-2 8-10 2-15-6-5-9-13-17-13Z" />
          </g>
          <path id="adoptante-hero-corazon" d="M23 39C14 33 1 22 1 12 1 0 16-2 23 9 30-2 45 0 45 12c0 10-13 21-22 27Z" />
        </defs>
        <use href="#adoptante-hero-huella" transform="translate(518 23) scale(1.3)" className="adoptante-hero__huella-borde" />
        <use href="#adoptante-hero-huella" transform="translate(1120 36)" className="adoptante-hero__huella" />
        <use href="#adoptante-hero-huella" transform="translate(1174 480) scale(.92)" className="adoptante-hero__huella" />
        <use href="#adoptante-hero-corazon" transform="translate(1289 36) rotate(18)" className="adoptante-hero__corazon" />
        <use href="#adoptante-hero-corazon" transform="translate(543 404) scale(1.36)" className="adoptante-hero__corazon" />
      </svg>
      <div className="adoptante-hero__cierre">
        <svg viewBox="0 0 1440 105" preserveAspectRatio="none" aria-hidden="true">
          <path d="M0 16C104 56 147 84 235 74 373 59 466 20 592 13s225 23 325 37 153 9 249-10 180-46 274-10v75H0Z" />
        </svg>
        <a href="#catalogo" className="adoptante-hero__bajar" aria-label="Ir al catálogo de mascotas">
          <Icono tipo="flecha" />
        </a>
        <button
          type="button"
          className="adoptante-hero__movimiento"
          aria-label={pausado ? 'Reanudar animaciones del banner' : 'Pausar animaciones del banner'}
          title={pausado ? 'Reanudar animaciones' : 'Pausar animaciones'}
          onClick={() => setPausado(anterior => !anterior)}
        >
          <svg viewBox="0 0 24 24" aria-hidden="true">
            <path d={pausado ? 'M8 5v14l11-7Z' : 'M7 5h3v14H7zm7 0h3v14h-3z'} />
          </svg>
        </button>
        <h2><span aria-hidden="true" />Tus amigos te esperan<span aria-hidden="true" /></h2>
      </div>
    </section>
  );
}
