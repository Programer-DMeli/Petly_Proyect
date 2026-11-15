import { useCallback, useEffect, useState, useSyncExternalStore } from 'react';
import type { FormEvent } from 'react';
import { Link, useParams } from 'react-router-dom';
import Feedback from '../../components/Feedback';
import CasoExitoInteracciones from './CasoExitoInteracciones';
import { getSession, subscribe } from '../../services/session';
import { djangoBase, errorMessage, json, mediaUrl, request, springBase } from '../../services/http';

type PerfilAlberguePublico = {
  id: number;
  nombre: string;
  descripcion: string;
  direccion: string;
  telefono: string;
  email: string;
  sitio_web: string;
  capacidad_maxima: number;
  horario_atencion: string;
};
type FotoTestimonio = { id: number; descarga_url: string; mime_type: string };
type Testimonio = {
  id: number;
  albergue_id: number;
  mascota_nombre: string;
  albergue_nombre: string;
  historia: string;
  publicado_en: string;
  destacado: boolean;
  fotos: FotoTestimonio[];
};
type Pagina<T> = { results: T[]; next: string | null };
type Coleccion<T> = T[] | Pagina<T>;
type Resena = {
  id: number;
  calificacion: number;
  comentario: string;
  respuesta_albergue: string | null;
  creado_en: string;
};
type ResumenResenas = {
  albergue_id: number;
  promedio: number;
  total: number;
  resenas: Resena[];
};

const API = '/api/contratos/casos-exito/destacados/';

function rows<T>(data: Coleccion<T>) {
  return Array.isArray(data) ? data : data.results;
}

async function cargarDestacados(albergueId: number, signal?: AbortSignal) {
  const todos: Testimonio[] = [];
  let pagina = 1;
  let siguiente: string | null;
  do {
    const respuesta = await request<Coleccion<Testimonio>>(
      djangoBase,
      `${API}?albergue_id=${albergueId}&page=${pagina}&page_size=100`,
      { signal },
      false,
    );
    todos.push(...rows(respuesta));
    siguiente = Array.isArray(respuesta) ? null : respuesta.next;
    pagina += 1;
  } while (siguiente);
  return todos;
}

function fecha(value: string) {
  const date = new Date(value);
  return Number.isNaN(date.getTime())
    ? value
    : new Intl.DateTimeFormat('es-PE', { dateStyle: 'long' }).format(date);
}

export default function PerfilPublicoAlberguePage() {
  const { albergueId: parametro } = useParams();
  const albergueId = Number(parametro);
  const session = useSyncExternalStore(subscribe, getSession);
  const [perfil, setPerfil] = useState<PerfilAlberguePublico | null>(null);
  const [testimonios, setTestimonios] = useState<Testimonio[]>([]);
  const [indice, setIndice] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [resumenResenas, setResumenResenas] = useState<ResumenResenas | null>(null);
  const [resenasCargando, setResenasCargando] = useState(true);
  const [resenasError, setResenasError] = useState('');
  const [calificacion, setCalificacion] = useState(0);
  const [comentario, setComentario] = useState('');
  const [enviandoResena, setEnviandoResena] = useState(false);
  const [resenaEnviada, setResenaEnviada] = useState(false);

  const cargar = useCallback(async (signal?: AbortSignal) => {
    if (!Number.isSafeInteger(albergueId) || albergueId < 1) {
      setError('El perfil del albergue no es válido.');
      setLoading(false);
      setResenasCargando(false);
      return;
    }
    setLoading(true);
    setError('');
    setPerfil(null);
    setTestimonios([]);
    setResumenResenas(null);
    setResenasCargando(true);
    setResenasError('');
    try {
      const [perfilResult, destacadosResult, resenasResult] = await Promise.allSettled([
        request<PerfilAlberguePublico>(djangoBase, `/api/albergues/${albergueId}/publico/`, { signal }, false),
        cargarDestacados(albergueId, signal),
        request<ResumenResenas>(springBase, `/api/albergues/${albergueId}/resenas`, { signal }, false),
      ]);
      if (!signal?.aborted) {
        if (perfilResult.status === 'rejected') throw perfilResult.reason;
        setPerfil(perfilResult.value);
        if (destacadosResult.status === 'fulfilled') setTestimonios(destacadosResult.value);
        else setError(errorMessage(destacadosResult.reason));
        if (resenasResult.status === 'fulfilled') setResumenResenas(resenasResult.value);
        else setResenasError(errorMessage(resenasResult.reason));
        setIndice(0);
      }
    } catch (cause) {
      if (!signal?.aborted) setError(errorMessage(cause));
    } finally {
      if (!signal?.aborted) setLoading(false);
      if (!signal?.aborted) setResenasCargando(false);
    }
  }, [albergueId]);

  useEffect(() => {
    const controller = new AbortController();
    void cargar(controller.signal);
    return () => controller.abort();
  }, [cargar]);

  const testimonio = testimonios[indice];
  const anterior = () => setIndice(actual => (actual - 1 + testimonios.length) % testimonios.length);
  const siguiente = () => setIndice(actual => (actual + 1) % testimonios.length);

  const enviarResena = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setEnviandoResena(true);
    setResenasError('');
    setResenaEnviada(false);
    try {
      const actualizada = await request<ResumenResenas>(
        springBase,
        `/api/albergues/${albergueId}/resenas`,
        { method: 'POST', body: json({ calificacion, comentario: comentario.trim() }) },
      );
      setResumenResenas(actualizada);
      setComentario('');
      setCalificacion(0);
      setResenaEnviada(true);
    } catch (cause) {
      setResenasError(errorMessage(cause));
    } finally {
      setEnviandoResena(false);
    }
  };

  const fechaResena = (value: string) => {
    const date = new Date(value);
    return Number.isNaN(date.getTime())
      ? value
      : new Intl.DateTimeFormat('es-PE', { dateStyle: 'medium' }).format(date);
  };

  return <section className="public-shelter-profile">
    <Feedback error={error} />
    {loading ? <p role="status">Cargando perfil del albergue…</p>
      : perfil && <>
        <header className="public-shelter-profile__hero">
          <p className="eyebrow">Perfil del albergue</p>
          <h1>{perfil.nombre}</h1>
          <p>{perfil.descripcion || 'Comprometidos con el cuidado y la adopción responsable.'}</p>
          <div className="public-shelter-profile__details">
            {perfil.direccion && <span>{perfil.direccion}</span>}
            {perfil.telefono && <a href={`tel:${perfil.telefono}`}>{perfil.telefono}</a>}
            {perfil.email && <a href={`mailto:${perfil.email}`}>{perfil.email}</a>}
            {perfil.sitio_web && <a href={perfil.sitio_web} target="_blank" rel="noreferrer">Sitio web</a>}
            {perfil.horario_atencion && <span>Atención: {perfil.horario_atencion}</span>}
          </div>
        </header>

        <section className="card" aria-labelledby="testimonios-destacados-titulo">
          <p className="eyebrow">Historias de adopción</p>
          <h2 id="testimonios-destacados-titulo">Testimonios destacados</h2>
          {testimonio ? <div className="testimonials-carousel" aria-roledescription="carrusel">
            <button type="button" className="secondary testimonials-carousel__arrow"
              aria-label="Ver testimonio anterior" disabled={testimonios.length < 2} onClick={anterior}>
              <span aria-hidden="true">←</span>
            </button>
            <article className="testimonials-carousel__content" aria-live="polite">
              <div className="success-story-card__heading">
                <div>
                  <h3>{testimonio.mascota_nombre}</h3>
                  <p className="muted">Adoptada con {perfil.nombre}</p>
                </div>
                <time dateTime={testimonio.publicado_en}>{fecha(testimonio.publicado_en)}</time>
              </div>
              <p className="case-story">{testimonio.historia}</p>
              {testimonio.fotos.length > 0 && <div className="case-story-photos">
                {testimonio.fotos.map((foto, fotoIndex) => <figure key={foto.id}>
                  <img src={mediaUrl(foto.descarga_url)}
                    alt={`Foto ${fotoIndex + 1} de ${testimonio.mascota_nombre}`} loading="lazy" />
                </figure>)}
              </div>}
              <CasoExitoInteracciones casoId={testimonio.id} />
              <p className="testimonials-carousel__position" aria-label={`Testimonio ${indice + 1} de ${testimonios.length}`}>
                {indice + 1} de {testimonios.length}
              </p>
            </article>
            <button type="button" className="secondary testimonials-carousel__arrow"
              aria-label="Ver siguiente testimonio" disabled={testimonios.length < 2} onClick={siguiente}>
              <span aria-hidden="true">→</span>
            </button>
          </div> : <p className="success-stories-empty">
            Este albergue todavía no tiene testimonios destacados.
          </p>}
        </section>
        <section className="card shelter-reviews" aria-labelledby="resenas-albergue-titulo">
          <div className="shelter-reviews__heading">
            <div>
              <p className="eyebrow">Opiniones de la comunidad</p>
              <h2 id="resenas-albergue-titulo">Reseñas de {perfil.nombre}</h2>
            </div>
            <div className="shelter-reviews__summary" aria-live="polite">
              {resenasCargando ? <span role="status">Cargando reseñas…</span>
                : resumenResenas && <>
                  <strong>{Number(resumenResenas.promedio).toFixed(2)} <span aria-hidden="true">★</span></strong>
                  <span>{resumenResenas.total} {resumenResenas.total === 1 ? 'reseña' : 'reseñas'}</span>
                </>}
            </div>
          </div>
          {resenasError && <Feedback error={resenasError} />}
          {resenaEnviada && <p className="shelter-reviews__success" role="status">Tu reseña se publicó correctamente.</p>}

          {session?.identity.rol === 'ADOPTANTE' ? <form className="shelter-review-form" onSubmit={enviarResena}>
            <h3>Cuéntanos cómo fue la atención</h3>
            <fieldset className="shelter-review-form__rating" disabled={enviandoResena}>
              <legend>Tu calificación</legend>
              <div className="shelter-review-form__stars">
                {[1, 2, 3, 4, 5].map(valor => <button
                  key={valor}
                  type="button"
                  className={`shelter-review-form__star${valor <= calificacion ? ' is-selected' : ''}`}
                  aria-label={`${valor} ${valor === 1 ? 'estrella' : 'estrellas'}`}
                  aria-pressed={valor === calificacion}
                  onClick={() => setCalificacion(valor)}>
                  ★
                </button>)}
              </div>
            </fieldset>
            <label htmlFor="comentario-resena">Comentario</label>
            <textarea
              id="comentario-resena"
              value={comentario}
              onChange={event => setComentario(event.target.value)}
              maxLength={1000}
              rows={4}
              required
              placeholder="Comparte tu experiencia con este albergue…"
            />
            <div className="shelter-review-form__footer">
              <small>{comentario.length}/1000 caracteres. Solo pueden reseñar adoptantes con una adopción entregada.</small>
              <button type="submit" disabled={enviandoResena || calificacion < 1 || !comentario.trim()}>
                {enviandoResena ? 'Enviando…' : 'Publicar reseña'}
              </button>
            </div>
          </form> : session ? <p className="muted shelter-reviews__login-hint">
            Solo los adoptantes con una adopción entregada pueden publicar una reseña.
          </p> : <p className="muted shelter-reviews__login-hint">
            <Link to="/acceso">Inicia sesión como adoptante</Link> para compartir tu experiencia.
          </p>}

          {resenasCargando ? null : resumenResenas?.resenas.length ? <div className="shelter-reviews__list">
            {resumenResenas.resenas.map(resena => <article className="shelter-review" key={resena.id}>
              <div className="shelter-review__heading">
                <span aria-label={`${resena.calificacion} de 5 estrellas`} className="shelter-review__stars">
                  {'★'.repeat(resena.calificacion)}{'☆'.repeat(5 - resena.calificacion)}
                </span>
                <time dateTime={resena.creado_en}>{fechaResena(resena.creado_en)}</time>
              </div>
              <p>{resena.comentario}</p>
              {resena.respuesta_albergue && <aside className="shelter-review__reply">
                <strong>Respuesta de {perfil.nombre}</strong>
                <p>{resena.respuesta_albergue}</p>
              </aside>}
            </article>)}
          </div> : resumenResenas && <p className="success-stories-empty">Todavía no hay reseñas para este albergue.</p>}
        </section>
        <p><Link to="/historias-exito">Ver todas las historias de adopción</Link></p>
      </>}
  </section>;
}
