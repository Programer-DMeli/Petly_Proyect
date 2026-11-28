import { useEffect, useRef, useState } from 'react';
import AdoptLink from '../../../components/AdoptLink';
import { formatAge } from '../../../services/formatAge';
import { mediaUrl, spring } from '../../../services/http';
import type { Pet } from '../../../services/types';
import { landingArt, LandingPlant } from '../LandingScene';
import '../../../styles/welcome.css';

type FeaturedPet = Pick<Pet, 'id' | 'nombre' | 'edadMeses' | 'descripcion' | 'fotografiaUrl'>;
type Catalog = { status: 'loading' | 'error' } | { status: 'ready'; pets: FeaturedPet[] };

export default function HomePage() {
  const [catalog, setCatalog] = useState<Catalog>({ status: 'loading' });
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    const request = new AbortController();
    void spring<FeaturedPet[]>('/api/catalogo/destacadas', { signal: request.signal }, false)
      .then(pets => {
        if (!request.signal.aborted) setCatalog({ status: 'ready', pets });
      })
      .catch(() => {
        if (!request.signal.aborted) setCatalog({ status: 'error' });
      });
    return () => request.abort();
  }, [attempt]);

  return (
    <section className="petly-welcome" aria-labelledby="bienvenida-petly">
      <LandingPlant side="left" />
      <LandingPlant side="right" />
      <div className="petly-welcome__props" aria-hidden="true">
        <img src={`${landingArt}/plato.webp`} alt="" />
        <img src={`${landingArt}/pelota.webp`} alt="" />
      </div>
      <div className="petly-welcome__intro">
        <h1 id="bienvenida-petly">Tu próximo mejor amigo te espera</h1>
        <p>Conoce historias reales, conecta con un albergue y comienza una adopción responsable.</p>
      </div>
      {catalog.status === 'ready' && catalog.pets.length > 0 ? (
        <PetCarousel pets={catalog.pets} />
      ) : (
        <div className="petly-welcome__status">
          {catalog.status === 'loading' && <p role="status">Cargando mascotas…</p>}
          {catalog.status === 'ready' && (
            <p role="status">Por ahora no hay mascotas disponibles para adopción.</p>
          )}
          {catalog.status === 'error' && (
            <>
              <p role="alert">No pudimos cargar las mascotas en este momento.</p>
              <button
                type="button"
                onClick={() => {
                  setCatalog({ status: 'loading' });
                  setAttempt(previous => previous + 1);
                }}
              >
                Reintentar
              </button>
            </>
          )}
        </div>
      )}
    </section>
  );
}

function PetCarousel({ pets }: { pets: FeaturedPet[] }) {
  const [active, setActive] = useState(0);
  const touch = useRef<{ x: number; y: number } | null>(null);
  const count = pets.length;
  const slots = [0, 1, -1, 2, -2].slice(0, count);
  const step = (offset: number) => setActive(index => (index + offset + count) % count);

  return (
    <section
      className="welcome-carousel"
      aria-label="Mascotas disponibles para adopción"
      aria-roledescription="carrusel"
      tabIndex={count > 1 ? 0 : undefined}
      onKeyDown={event => {
        if (count < 2 || event.altKey || event.ctrlKey || event.metaKey || event.shiftKey) return;
        if (event.key === 'ArrowLeft' || event.key === 'ArrowRight') {
          event.preventDefault();
          step(event.key === 'ArrowLeft' ? -1 : 1);
        }
      }}
    >
      <div
        className="welcome-carousel__stage"
        onPointerDown={event => {
          if (event.pointerType === 'touch' && event.isPrimary) {
            touch.current = { x: event.clientX, y: event.clientY };
          }
        }}
        onPointerCancel={() => {
          touch.current = null;
        }}
        onPointerUp={event => {
          if (!touch.current || event.pointerType !== 'touch' || !event.isPrimary) return;
          const dx = event.clientX - touch.current.x;
          const dy = event.clientY - touch.current.y;
          touch.current = null;
          if (count > 1 && Math.abs(dx) >= 42 && Math.abs(dx) > Math.abs(dy)) {
            step(dx > 0 ? -1 : 1);
          }
        }}
      >
        {slots.map(offset => {
          const index = (active + offset + count) % count;
          const pet = pets[index];
          return (
            <article
              className="welcome-carousel__slide"
              data-position={offset}
              aria-hidden={offset !== 0}
              key={pet.id}
            >
              <CarouselPhoto pet={pet} active={offset === 0} />
              {offset === 0 && (
                <div className="welcome-carousel__caption">
                  <h2>
                    <strong>{pet.nombre}</strong> <span>· {formatAge(pet.edadMeses)}</span>
                  </h2>
                  <div className="welcome-carousel__heart" aria-hidden="true">
                    ♥
                  </div>
                  {pet.descripcion.trim() && <p>{pet.descripcion}</p>}
                </div>
              )}
            </article>
          );
        })}
        {count > 1 && (
          <>
            <CarouselArrow previous onClick={() => step(-1)} />
            <CarouselArrow onClick={() => step(1)} />
          </>
        )}
      </div>
      <div className="welcome-carousel__controls">
        {count > 1 && (
          <div className="welcome-carousel__dots" role="group" aria-label="Elegir mascota">
            {pets.map((pet, index) => (
              <button
                key={pet.id}
                type="button"
                aria-label={`Mostrar a ${pet.nombre} (${index + 1} de ${count})`}
                aria-current={index === active ? 'true' : undefined}
                onClick={() => setActive(index)}
              />
            ))}
          </div>
        )}
        <AdoptLink className="welcome-carousel__adopt" petId={pets[active].id}>
          Adoptar
        </AdoptLink>
      </div>
      <span className="welcome-carousel__announcement" aria-live="polite" aria-atomic="true">
        {pets[active].nombre}, mascota {active + 1} de {count}
      </span>
    </section>
  );
}

function CarouselArrow({ previous = false, onClick }: { previous?: boolean; onClick: () => void }) {
  return (
    <button
      className={`welcome-carousel__arrow welcome-carousel__arrow--${previous ? 'left' : 'right'}`}
      aria-label={previous ? 'Mascota anterior' : 'Mascota siguiente'}
      type="button"
      onClick={onClick}
    >
      <svg viewBox="0 0 24 24" aria-hidden="true">
        <path d={previous ? 'm15 4-8 8 8 8' : 'm9 4 8 8-8 8'} />
      </svg>
    </button>
  );
}

function CarouselPhoto({ pet, active }: { pet: FeaturedPet; active: boolean }) {
  const url = mediaUrl(pet.fotografiaUrl);
  const [failed, setFailed] = useState<string>();
  if (!url || url === failed) {
    return (
      <div className="pet-card__placeholder welcome-carousel__placeholder">
        <span aria-hidden="true">♡</span>
        <span>Fotografía por añadir</span>
      </div>
    );
  }
  return (
    <img
      src={url}
      alt={active ? pet.nombre : ''}
      draggable={false}
      onError={() => setFailed(url)}
    />
  );
}
