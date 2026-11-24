import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import Feedback from '../../../../components/Feedback';
import { errorMessage, json, request, springBase, djangoBase } from '../../../../services/http';

type Resena = {
  id: number;
  calificacion: number;
  comentario: string;
  respuesta_albergue: string | null;
  estado_moderacion: 'VISIBLE' | 'OCULTA';
  creado_en: string;
  moderada_en: string | null;
};
type Albergue = { id: number; nombre: string; descripcion?: string; foto_url?: string };
const API = '/api/albergue/resenas';

function fecha(value: string | null) {
  if (!value) return 'Sin fecha';
  const date = new Date(value);
  return Number.isNaN(date.getTime())
    ? value
    : new Intl.DateTimeFormat('es-PE', { dateStyle: 'medium', timeStyle: 'short' }).format(date);
}

export default function ResenasPage() {
  const [albergue, setAlbergue] = useState<Albergue | null>(null);
  const [resenas, setResenas] = useState<Resena[]>([]);
  const [respuestas, setRespuestas] = useState<Record<number, string>>({});
  const [loading, setLoading] = useState(true);
  const [busyId, setBusyId] = useState<number | null>(null);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [fotoFallida, setFotoFallida] = useState(false);
  const cantidades = [5, 4, 3, 2, 1].map(estrellas => resenas.filter(item => item.calificacion === estrellas).length);
  const promedio = resenas.length
    ? resenas.reduce((suma, item) => suma + item.calificacion, 0) / resenas.length : 0;

  const cargar = useCallback(async (signal?: AbortSignal) => {
    setLoading(true);
    setError('');
    try {
      const [perfil, lista] = await Promise.all([
        request<Albergue>(djangoBase, '/api/albergues/mi-perfil/', { signal }),
        request<Resena[]>(springBase, API, { signal }),
      ]);
      if (!signal?.aborted) {
        setAlbergue(perfil);
        setResenas(lista);
        setRespuestas(Object.fromEntries(lista.map(item => [item.id, item.respuesta_albergue ?? ''])));
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

  async function guardarRespuesta(resena: Resena) {
    const respuesta = (respuestas[resena.id] ?? '').trim();
    if (!respuesta) {
      setError('Escribe una respuesta antes de guardarla.');
      return;
    }
    setBusyId(resena.id);
    setError('');
    setSuccess('');
    try {
      await request<void>(springBase, API + '/' + resena.id + '/respuesta', {
        method: 'PUT',
        body: json({ respuesta }),
      });
      setResenas(actuales => actuales.map(item =>
        item.id === resena.id ? { ...item, respuesta_albergue: respuesta } : item));
      setSuccess('La respuesta quedó guardada y se mostrará en el perfil público.');
    } catch (cause) {
      setError(errorMessage(cause));
    } finally {
      setBusyId(null);
    }
  }

  async function cambiarVisibilidad(resena: Resena) {
    const visible = resena.estado_moderacion !== 'VISIBLE';
    setBusyId(resena.id);
    setError('');
    setSuccess('');
    try {
      await request<void>(springBase, API + '/' + resena.id + '/moderacion', {
        method: 'PATCH',
        body: json({ visible }),
      });
      setResenas(actuales => actuales.map(item =>
        item.id === resena.id
          ? { ...item, estado_moderacion: visible ? 'VISIBLE' : 'OCULTA', moderada_en: new Date().toISOString() }
          : item));
      setSuccess(visible ? 'La reseña volvió a ser pública.' : 'La reseña se ocultó del perfil público.');
    } catch (cause) {
      setError(errorMessage(cause));
    } finally {
      setBusyId(null);
    }
  }

  return <section className="card shelter-reviews-management" aria-labelledby="gestion-resenas-titulo">
    <h1 id="gestion-resenas-titulo">Reseñas recibidas</h1>
    {albergue && <div className="resenas-perfil">
      {albergue.foto_url && !fotoFallida
        ? <img src={albergue.foto_url} alt={`Foto de ${albergue.nombre}`} referrerPolicy="no-referrer"
          onError={() => setFotoFallida(true)} />
        : <div className="resenas-perfil__sin-foto">Foto del albergue pendiente</div>}
      <div>
        <h2>{albergue.nombre}</h2>
        <p>{albergue.descripcion?.trim() || 'Este albergue todavía no tiene una descripción.'}</p>
        <Link to={'/albergues/' + albergue.id}>Ver perfil público de {albergue.nombre}</Link>
      </div>
    </div>}
    <Feedback error={error} />
    {success && <p className="shelter-reviews-management__success" role="status">{success}</p>}
    {!loading && albergue && <section className="resenas-resumen" aria-label="Calificación del albergue">
      <div className="resenas-resumen__puntaje">
        <strong>{resenas.length ? promedio.toFixed(1) : '—'}</strong>
        <span className="shelter-review__stars" aria-label={`${promedio.toFixed(1)} de 5 estrellas`}>
          {'★'.repeat(Math.round(promedio))}{'☆'.repeat(5 - Math.round(promedio))}
        </span>
        <span className="muted">{resenas.length} {resenas.length === 1 ? 'reseña recibida' : 'reseñas recibidas'}</span>
      </div>
      <div className="resenas-resumen__barras">
        {cantidades.map((cantidad, indice) => {
          const estrellas = 5 - indice;
          return <div key={estrellas}>
            <span aria-hidden="true">{estrellas} ★</span>
            <progress value={cantidad} max={resenas.length || 1}
              aria-label={`${cantidad} ${cantidad === 1 ? 'opinión' : 'opiniones'} de ${estrellas} ${estrellas === 1 ? 'estrella' : 'estrellas'}`} />
            <span aria-hidden="true">{cantidad}</span>
          </div>;
        })}
      </div>
    </section>}
    {loading ? <p role="status">Cargando reseñas…</p>
      : resenas.length === 0 ? <p className="success-stories-empty">Todavía no has recibido reseñas.</p>
        : <div className="shelter-reviews-management__list">
          {resenas.map(resena => <article className="shelter-review-management" key={resena.id}>
            <header className="shelter-review-management__heading">
              <span className="shelter-review__stars" aria-label={resena.calificacion + ' de 5 estrellas'}>
                {'★'.repeat(resena.calificacion)}{'☆'.repeat(5 - resena.calificacion)}
              </span>
              <time dateTime={resena.creado_en}>{fecha(resena.creado_en)}</time>
              <span className={'shelter-review-management__status' + (resena.estado_moderacion === 'VISIBLE' ? ' is-visible' : '')}>
                {resena.estado_moderacion === 'VISIBLE' ? 'Pública' : 'Oculta'}
              </span>
            </header>
            <p className="shelter-review-management__comment">{resena.comentario}</p>
            <label htmlFor={'respuesta-resena-' + resena.id}>Respuesta del albergue</label>
            <textarea
              id={'respuesta-resena-' + resena.id}
              value={respuestas[resena.id] ?? ''}
              onChange={event => setRespuestas(actuales => ({ ...actuales, [resena.id]: event.target.value }))}
              maxLength={1000}
              rows={3}
              placeholder="Agradece y responde con respeto…"
            />
            <small>{(respuestas[resena.id] ?? '').length}/1000 caracteres</small>
            <div className="shelter-review-management__actions">
              <button type="button" onClick={() => void guardarRespuesta(resena)}
                disabled={busyId === resena.id || !(respuestas[resena.id] ?? '').trim()}>
                {busyId === resena.id ? 'Guardando…' : resena.respuesta_albergue ? 'Actualizar respuesta' : 'Publicar respuesta'}
              </button>
              <button type="button" className="secondary" onClick={() => void cambiarVisibilidad(resena)}
                disabled={busyId === resena.id}>
                {resena.estado_moderacion === 'VISIBLE' ? 'Ocultar reseña' : 'Mostrar reseña'}
              </button>
            </div>
            {resena.moderada_en && <small>Moderada el {fecha(resena.moderada_en)}</small>}
          </article>)}
        </div>}
  </section>;
}
