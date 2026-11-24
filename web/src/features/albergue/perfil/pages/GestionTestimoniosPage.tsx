import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import Feedback from '../../../../components/Feedback';
import { django, errorMessage, json, mediaUrl } from '../../../../services/http';
import RevisionCasosExitoPage, { type CasoPublicado } from '../../../shared/RevisionCasosExitoPage';

type Albergue = { id: number; nombre: string };
type Coleccion<T> = T[] | { results: T[]; next: string | null };

const API = '/api/contratos/casos-exito/';

function rows<T>(data: Coleccion<T>) {
  return Array.isArray(data) ? data : data.results;
}

async function cargarPublicados(albergueId: number, signal?: AbortSignal) {
  const todos: CasoPublicado[] = [];
  let pagina = 1;
  let siguiente: string | null;
  do {
    const respuesta = await django<Coleccion<CasoPublicado>>(
      `${API}publicados/?albergue_id=${albergueId}&page=${pagina}&page_size=100`,
      { signal },
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
    : new Intl.DateTimeFormat('es-PE', { dateStyle: 'medium' }).format(date);
}

export default function GestionTestimoniosPage() {
  const [albergue, setAlbergue] = useState<Albergue | null>(null);
  const [testimonios, setTestimonios] = useState<CasoPublicado[]>([]);
  const [loading, setLoading] = useState(true);
  const [busyId, setBusyId] = useState<number | null>(null);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  const cargar = useCallback(async (signal?: AbortSignal) => {
    setLoading(true);
    setError('');
    try {
      const perfil = await django<Albergue>('/api/albergues/mi-perfil/', { signal });
      const publicados = await cargarPublicados(perfil.id, signal);
      if (!signal?.aborted) {
        setAlbergue(perfil);
        setTestimonios(publicados);
      }
    } catch (cause) {
      if (!signal?.aborted) setError(errorMessage(cause));
    } finally {
      if (!signal?.aborted) setLoading(false);
    }
  }, []);

  useEffect(() => {
    const controller = new AbortController();
    void cargar(controller.signal);
    return () => controller.abort();
  }, [cargar]);

  async function cambiarDestacado(testimonio: CasoPublicado) {
    const nuevoEstado = !testimonio.destacado;
    setBusyId(testimonio.id);
    setError('');
    setSuccess('');
    try {
      await django<{ id: number; destacado: boolean }>(`${API}${testimonio.id}/destacar/`, {
        method: 'POST',
        body: json({ destacado: nuevoEstado }),
      });
      setTestimonios(actuales => actuales.map(actual => actual.id === testimonio.id
        ? { ...actual, destacado: nuevoEstado }
        : actual));
      setSuccess(nuevoEstado
        ? `La historia de ${testimonio.mascota_nombre} ahora aparece en tu perfil público.`
        : `La historia de ${testimonio.mascota_nombre} sigue publicada, pero ya no aparece en el carrusel.`);
    } catch (cause) {
      setError(errorMessage(cause));
    } finally {
      setBusyId(null);
    }
  }

  return <section className="card testimonials-management">
    <header>
      <p className="eyebrow">Comunidad Petly</p>
      <h1>Testimonios destacados</h1>
      <p className="muted">Revisa las historias que recibiste y elige cuáles destacar en tu perfil público.</p>
      {albergue && <p><Link to={`/albergues/${albergue.id}`}>Ver perfil público de {albergue.nombre}</Link></p>}
    </header>
    <RevisionCasosExitoPage role="ALBERGUE" onAprobada={caso => {
      setTestimonios(actuales => [caso, ...actuales.filter(actual => actual.id !== caso.id)]);
    }} />
    <h2>Historias publicadas</h2>
    <Feedback error={error} success={success} />
    <div className="actions">
      <button type="button" className="secondary" disabled={loading || busyId !== null}
        onClick={() => void cargar()}>
        Actualizar testimonios
      </button>
    </div>
    {loading ? <p role="status">Cargando historias publicadas…</p>
      : testimonios.length === 0 ? <p className="success-stories-empty">
        Todavía no hay historias aprobadas para destacar. Cuando se apruebe una historia, podrás elegirla aquí.
      </p> : <div className="success-stories-grid">
        {testimonios.map(testimonio => <article className="card success-story-card" key={testimonio.id}>
          <div className="success-story-card__heading">
            <div>
              <h3>{testimonio.mascota_nombre}</h3>
              <p className="muted">Publicada {fecha(testimonio.publicado_en)}</p>
            </div>
            <strong>{testimonio.destacado ? 'Destacada' : 'Publicada'}</strong>
          </div>
          <p className="case-story">{testimonio.historia}</p>
          <div className="case-story-photos">
            {testimonio.fotos.map((foto, index) => <figure key={foto.id}>
              <img src={mediaUrl(foto.descarga_url)}
                alt={`Foto ${index + 1} de ${testimonio.mascota_nombre}`} loading="lazy" />
            </figure>)}
          </div>
          <button type="button" className={testimonio.destacado ? 'secondary testimonial-toggle' : 'testimonial-toggle'}
            aria-pressed={testimonio.destacado} disabled={busyId !== null}
            onClick={() => void cambiarDestacado(testimonio)}>
            {busyId === testimonio.id ? 'Guardando…' : testimonio.destacado ? 'Quitar del perfil público' : 'Destacar en el perfil público'}
          </button>
        </article>)}
      </div>}
  </section>;
}
