import { useCallback, useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import Feedback from '../../components/Feedback';
import { djangoBase, errorMessage, mediaUrl, request } from '../../services/http';

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
  const [perfil, setPerfil] = useState<PerfilAlberguePublico | null>(null);
  const [testimonios, setTestimonios] = useState<Testimonio[]>([]);
  const [indice, setIndice] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const cargar = useCallback(async (signal?: AbortSignal) => {
    if (!Number.isSafeInteger(albergueId) || albergueId < 1) {
      setError('El perfil del albergue no es válido.');
      setLoading(false);
      return;
    }
    setLoading(true);
    setError('');
    try {
      const [datos, destacados] = await Promise.all([
        request<PerfilAlberguePublico>(djangoBase, `/api/albergues/${albergueId}/publico/`, { signal }, false),
        cargarDestacados(albergueId, signal),
      ]);
      if (!signal?.aborted) {
        setPerfil(datos);
        setTestimonios(destacados);
        setIndice(0);
      }
    } catch (cause) {
      if (!signal?.aborted) setError(errorMessage(cause));
    } finally {
      if (!signal?.aborted) setLoading(false);
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
        <p><Link to="/historias-exito">Ver todas las historias de adopción</Link></p>
      </>}
  </section>;
}
