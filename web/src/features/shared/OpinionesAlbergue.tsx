import { useEffect, useId, useRef, useState, type FormEvent } from 'react';
import { ApiError, errorMessage, json, spring } from '../../services/http';

type EstadoResena = { registrada: boolean; calificacion: number | null };

export default function OpinionesAlbergue({ albergueId, permitirPublicar = true }: {
  albergueId: number;
  permitirPublicar?: boolean;
}) {
  const ruta = `/api/albergues/${albergueId}/resenas`;
  const [estado, setEstado] = useState<EstadoResena | null>(null);
  const [abierto, setAbierto] = useState(false);
  const [calificacion, setCalificacion] = useState(0);
  const [comentario, setComentario] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [intento, setIntento] = useState(0);
  const dialogo = useRef<HTMLDialogElement>(null);
  const titulo = useId();

  useEffect(() => {
    const controller = new AbortController();
    setError('');
    void spring<EstadoResena>(`${ruta}/mi-resena`, { signal: controller.signal, cache: 'no-store' })
      .then(datos => { if (!controller.signal.aborted) setEstado(datos); })
      .catch(cause => { if (!controller.signal.aborted) setError(errorMessage(cause)); });
    return () => controller.abort();
  }, [ruta, intento]);

  useEffect(() => {
    if (abierto) dialogo.current?.showModal();
  }, [abierto]);

  async function enviar(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (busy || !calificacion || !comentario.trim()) return;
    setBusy(true);
    setError('');
    try {
      await spring(ruta, { method: 'POST', body: json({ calificacion, comentario: comentario.trim() }) });
      setEstado({ registrada: true, calificacion });
      setAbierto(false);
    } catch (cause) {
      if (cause instanceof ApiError && cause.status === 409) {
        setEstado({ registrada: true, calificacion: null });
        setAbierto(false);
        setIntento(actual => actual + 1);
      } else {
        setError(errorMessage(cause));
      }
    } finally {
      setBusy(false);
    }
  }

  if (estado?.registrada) return <p className="resena-registrada" role="status">
    <strong>Reseña registrada</strong>
    {estado.calificacion != null && <span className="shelter-review__stars"
      aria-label={`Tu calificación de ${estado.calificacion} de 5 estrellas`}>
      {'★'.repeat(estado.calificacion)}{'☆'.repeat(5 - estado.calificacion)}
    </span>}
  </p>;
  if (!permitirPublicar) return null;
  if (!estado) return error ? <div>
    <p role="alert" className="feedback error">{error}</p>
    <button type="button" className="secondary" onClick={() => setIntento(actual => actual + 1)}>Reintentar consulta de reseña</button>
  </div> : <p role="status">Consultando tu reseña…</p>;

  return <>
    <button type="button" className="secondary" onClick={() => setAbierto(true)}>Opiniones y reseñas</button>
    {abierto && <dialog className="resena-dialog" ref={dialogo} aria-labelledby={titulo}
      onCancel={event => { event.preventDefault(); if (!busy) setAbierto(false); }}>
      <form className="shelter-review-form" onSubmit={event => void enviar(event)}>
        <h2 id={titulo}>Calificar y opinar</h2>
        <fieldset className="shelter-review-form__rating" disabled={busy}>
          <legend>¿Cómo fue la atención del albergue?</legend>
          <div className="shelter-review-form__stars">
            {[1, 2, 3, 4, 5].map(valor => <button key={valor} type="button"
              className={`shelter-review-form__star${valor <= calificacion ? ' is-selected' : ''}`}
              aria-label={`${valor} ${valor === 1 ? 'estrella' : 'estrellas'}`}
              aria-pressed={valor === calificacion} onClick={() => setCalificacion(valor)}>
              {valor <= calificacion ? '★' : '☆'}
            </button>)}
          </div>
        </fieldset>
        <label>Tu opinión
          <textarea value={comentario} onChange={event => setComentario(event.target.value)}
            maxLength={1000} rows={4} required disabled={busy} />
        </label>
        {error && <p role="alert" className="feedback error">{error}</p>}
        <div className="actions">
          <button type="button" className="secondary" disabled={busy} onClick={() => { setAbierto(false); setError(''); }}>Cancelar</button>
          <button disabled={busy || !calificacion || !comentario.trim()}>{busy ? 'Enviando…' : 'Publicar reseña'}</button>
        </div>
      </form>
    </dialog>}
  </>;
}
