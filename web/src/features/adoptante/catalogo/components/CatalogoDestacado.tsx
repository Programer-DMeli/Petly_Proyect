import { useEffect, useId, useRef, useState, type CSSProperties } from 'react';
import { Link } from 'react-router-dom';
import { FavoriteHeart } from '../../../../components/FavoritesProvider';
import SolicitarAdopcion from '../../../../components/SolicitarAdopcion';
import { mediaUrl } from '../../../../services/http';
import { formatAge } from '../../../../services/formatAge';
import type { Pet } from '../../../../services/types';
import FichaCompletaMascota from './FichaCompletaMascota';

function FotoDestacada({ mascota }: { mascota: Pet }) {
  const fotos = [...new Set([mascota.fotografiaUrl, ...(mascota.fotografiasAdicionales ?? [])].filter(Boolean))];
  const [indice, setIndice] = useState(0);
  const actual = indice % Math.max(1, fotos.length);

  function mover(direccion: number) {
    setIndice(indiceActual => (indiceActual + direccion + fotos.length) % fotos.length);
  }

  return (
    <div className="catalog-featured__media">
      {fotos.length ? (
        <img src={mediaUrl(fotos[actual])} alt={fotos.length === 1 ? mascota.nombre : `${mascota.nombre}, foto ${actual + 1}`} />
      ) : (
        <div className="pet-card__placeholder">
          <span aria-hidden="true">♡</span>
          <span>Fotografía por añadir</span>
        </div>
      )}
      <span className="catalog-featured__state">
        {mascota.estado === 'Disponible' ? 'En adopción' : mascota.estado}
      </span>
      {fotos.length > 1 && (
        <>
          <button className="catalog-featured__arrow catalog-featured__arrow--prev" type="button"
            aria-label="Foto anterior" onClick={() => mover(-1)}>‹</button>
          <button className="catalog-featured__arrow catalog-featured__arrow--next" type="button"
            aria-label="Foto siguiente" onClick={() => mover(1)}>›</button>
          <div className="catalog-featured__photo-dots" role="group" aria-label="Elegir foto">
            {fotos.map((foto, index) => (
              <button key={foto} type="button" aria-label={`Mostrar foto ${index + 1}`}
                aria-current={actual === index ? 'true' : undefined}
                onClick={() => setIndice(index)} />
            ))}
          </div>
          <span className="visually-hidden" role="status">
            Foto {actual + 1} de {fotos.length}
          </span>
        </>
      )}
    </div>
  );
}

interface Props {
  mascotas: Pet[];
  seleccionada: Pet;
  elegir: (id: number) => void;
  verTodos: string;
  browseId: string;
}

function known(value?: string | null) {
  return value && value !== 'Por evaluar' ? value : null;
}

function FichaDestacada({ seleccionada }: { seleccionada: Pet }) {
  const [girada, setGirada] = useState(false);
  const reverso = useId();
  const ficha = useRef<HTMLDivElement>(null);
  const conocerme = useRef<HTMLButtonElement>(null);
  const titulo = useRef<HTMLHeadingElement>(null);
  const restaurarFoco = useRef(false);
  const compatibilidad = seleccionada.compatibilidad;
  const porcentaje = compatibilidad?.porcentaje;
  const requierePerfil = compatibilidad?.mensaje?.startsWith('Completa tu perfil');

  function volver() {
    restaurarFoco.current = true;
    setGirada(false);
  }

  useEffect(() => {
    if (girada) {
      ficha.current?.scrollIntoView({ block: 'start', inline: 'nearest' });
      titulo.current?.focus({ preventScroll: true });
    } else if (restaurarFoco.current) {
      conocerme.current?.focus({ preventScroll: true });
      restaurarFoco.current = false;
    }
  }, [girada]);

  return (
    <div className="catalog-featured__flip" data-flipped={girada} ref={ficha}>
      <div className="catalog-featured__flip-inner">
        <div className="catalog-featured__sheet" inert={girada} aria-hidden={girada}>
          <header className="catalog-featured__title">
            <div>
              <p className="catalog-featured__eyebrow"><span aria-hidden="true">♡</span> Tu nuevo mejor amigo</p>
              <h2 aria-live="polite">{seleccionada.nombre}</h2>
            </div>
            <div className={`catalog-featured__compat${porcentaje == null ? ' is-pending' : ''}`}>
              <strong style={{ '--compat': `${porcentaje ?? 0}%` } as CSSProperties}>
                {porcentaje == null ? <span aria-hidden="true">—</span> : `${porcentaje}%`}
              </strong>
              <span>Compatibilidad</span>
              {porcentaje == null && (requierePerfil
                ? <Link to="/adoptante/cuestionario">Completar perfil</Link>
                : <span className="catalog-featured__compat-note" title={compatibilidad?.mensaje}>Sin calcular</span>)}
            </div>
          </header>
          <ul className="catalog-featured__facts" aria-label="Características">
            {seleccionada.edadMeses != null && <li>{formatAge(seleccionada.edadMeses)}</li>}
            {known(seleccionada.sexo) && <li>{seleccionada.sexo}</li>}
            {known(seleccionada.tamano) && <li>{seleccionada.tamano}</li>}
            {seleccionada.saludConfirmada && <li>Sano</li>}
          </ul>
          {seleccionada.descripcion && (
            <div>
              <h3>Descripción</h3>
              <p>{seleccionada.descripcion}</p>
            </div>
          )}
          {known(seleccionada.temperamento) && (
            <div>
              <h3>Temperamento</h3>
              <ul className="catalog-featured__facts catalog-featured__temperament">
                <li>{seleccionada.temperamento}</li>
              </ul>
            </div>
          )}
          <div className="catalog-featured__actions">
            <SolicitarAdopcion mascota={seleccionada} />
            <button
              className="button secondary" type="button" ref={conocerme}
              aria-controls={reverso} aria-expanded={girada} onClick={() => setGirada(true)}
            >
              Conocerme
            </button>
          </div>
        </div>
        <div className="catalog-featured__sheet catalog-featured__sheet--reverse" id={reverso}
          inert={!girada} aria-hidden={!girada} onKeyDown={event => { if (event.key === 'Escape') volver(); }}>
          <header className="catalog-featured__title">
            <h2 ref={titulo} tabIndex={-1}>Más sobre {seleccionada.nombre}</h2>
            <FavoriteHeart pet={seleccionada} />
          </header>
          <section className="catalog-featured__reverse-content" aria-label="Información de la mascota" tabIndex={0}>
            <FichaCompletaMascota mascota={seleccionada} mostrarFoto={false} />
          </section>
          <div className="catalog-featured__actions">
            <SolicitarAdopcion mascota={seleccionada} />
            <button className="button secondary" type="button" onClick={volver}>Volver a la ficha</button>
          </div>
        </div>
      </div>
    </div>
  );
}

export default function CatalogoDestacado({ mascotas, seleccionada, elegir, verTodos, browseId }: Props) {
  const track = useRef<HTMLUListElement>(null);
  const [limits, setLimits] = useState({ prev: false, next: false });

  useEffect(() => {
    const list = track.current;
    if (!list) return;
    const update = () => setLimits({
      prev: list.scrollLeft > 1,
      next: list.scrollLeft + list.clientWidth < list.scrollWidth - 1,
    });
    const observer = new ResizeObserver(update);
    observer.observe(list);
    if (list.firstElementChild) observer.observe(list.firstElementChild);
    list.addEventListener('scroll', update, { passive: true });
    update();
    return () => {
      observer.disconnect();
      list.removeEventListener('scroll', update);
    };
  }, [mascotas]);

  function desplazar(direccion: number) {
    const list = track.current;
    if (!list) return;
    const smooth = !window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    list.scrollBy({ left: direccion * list.clientWidth * 0.85, behavior: smooth ? 'smooth' : 'instant' });
  }

  return (
    <div className="catalog-featured">
      <section className="catalog-featured__hero" aria-label="Mascota destacada">
        <FotoDestacada key={seleccionada.id} mascota={seleccionada} />
        <FichaDestacada key={`ficha-${seleccionada.id}`} seleccionada={seleccionada} />
      </section>

      <section className="catalog-featured__browse" aria-labelledby={browseId}>
        <div className="catalog-featured__browse-heading">
          <div>
            <h2 id={browseId}>Encuentra a tu compañero ideal</h2>
            <p>Selecciona una mascota para ver su ficha.</p>
          </div>
          <Link className="catalog-featured__all" to={verTodos} state={{ focusResults: true }}>
            Ver todos <span aria-hidden="true">→</span>
          </Link>
        </div>
        <div className="catalog-featured__rail">
          <button className="catalog-featured__arrow" type="button" aria-label="Mascotas anteriores"
            disabled={!limits.prev} onClick={() => desplazar(-1)}>‹</button>
          <ul ref={track} className="catalog-featured__track" aria-label="Mascotas de la especie seleccionada">
            {mascotas.map(mascota => {
              const selected = mascota.id === seleccionada.id;
              return (
                <li key={mascota.id} className={selected ? 'is-selected' : undefined}>
                  <div className="catalog-featured__thumbnail">
                    <button className="catalog-featured__choice" type="button" aria-label={`Destacar a ${mascota.nombre}`}
                      aria-pressed={selected} onClick={() => elegir(mascota.id)}>
                      {mascota.fotografiaUrl && <img src={mediaUrl(mascota.fotografiaUrl)} alt="" loading="lazy" />}
                      <span className="catalog-featured__choice-copy">
                        <strong>{mascota.nombre}</strong>
                        <span>{[formatAge(mascota.edadMeses), known(mascota.tamano)].filter(Boolean).join(' · ')}</span>
                      </span>
                    </button>
                    <FavoriteHeart pet={mascota} />
                  </div>
                  <SolicitarAdopcion mascota={mascota} className="button catalog-featured__apply" />
                </li>
              );
            })}
          </ul>
          <button className="catalog-featured__arrow" type="button" aria-label="Mascotas siguientes"
            disabled={!limits.next} onClick={() => desplazar(1)}>›</button>
        </div>
      </section>
    </div>
  );
}
