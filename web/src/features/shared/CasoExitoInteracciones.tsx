import { useEffect, useState, useSyncExternalStore } from 'react';
import type { FormEvent } from 'react';
import { Link } from 'react-router-dom';
import Feedback from '../../components/Feedback';
import { errorMessage, json, request, springBase } from '../../services/http';
import { getSession, subscribe } from '../../services/session';

type ComentarioCaso = {
  id: number;
  autor: string;
  comentario: string;
  creado_en: string;
};

type Interacciones = {
  caso_id: number;
  total_me_gusta: number;
  me_gusta_usuario: boolean;
  total_comentarios: number;
  comentarios: ComentarioCaso[];
};

function fecha(value: string) {
  const date = new Date(value);
  return Number.isNaN(date.getTime())
    ? value
    : new Intl.DateTimeFormat('es-PE', { dateStyle: 'medium', timeStyle: 'short' }).format(date);
}

export default function CasoExitoInteracciones({ casoId }: { casoId: number }) {
  const session = useSyncExternalStore(subscribe, getSession);
  const autenticado = Boolean(session && session.expires > Date.now());
  const token = session?.token;
  const [datos, setDatos] = useState<Interacciones | null>(null);
  const [cargando, setCargando] = useState(true);
  const [procesando, setProcesando] = useState(false);
  const [comentario, setComentario] = useState('');
  const [error, setError] = useState('');
  const [aviso, setAviso] = useState('');

  useEffect(() => {
    const controller = new AbortController();
    setCargando(true);
    setError('');
    void request<Interacciones>(
      springBase,
      `/api/casos-exito/${casoId}/interacciones`,
      { signal: controller.signal },
      autenticado,
    ).then(setDatos).catch(cause => {
      if (!controller.signal.aborted) setError(errorMessage(cause));
    }).finally(() => {
      if (!controller.signal.aborted) setCargando(false);
    });
    return () => controller.abort();
  }, [casoId, autenticado, token]);

  const cambiarMeGusta = async () => {
    if (!datos || !autenticado || procesando) return;
    setProcesando(true);
    setError('');
    setAviso('');
    try {
      const actualizados = await request<Interacciones>(
        springBase,
        `/api/casos-exito/${casoId}/interacciones/me-gusta`,
        { method: datos.me_gusta_usuario ? 'DELETE' : 'PUT' },
      );
      setDatos(actualizados);
    } catch (cause) {
      setError(errorMessage(cause));
    } finally {
      setProcesando(false);
    }
  };

  const publicarComentario = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!autenticado || procesando || !comentario.trim()) return;
    setProcesando(true);
    setError('');
    setAviso('');
    try {
      const actualizados = await request<Interacciones>(
        springBase,
        `/api/casos-exito/${casoId}/interacciones/comentarios`,
        { method: 'POST', body: json({ comentario: comentario.trim() }) },
      );
      setDatos(actualizados);
      setComentario('');
      setAviso('Tu comentario se publicó.');
    } catch (cause) {
      setError(errorMessage(cause));
    } finally {
      setProcesando(false);
    }
  };

  return <section className="case-interactions" aria-label="Interacciones de la historia">
    {error && <Feedback error={error} />}
    {aviso && <p className="case-interactions__notice" role="status">{aviso}</p>}
    {cargando ? <p className="muted" role="status">Cargando interacciones…</p> : datos && <>
      <div className="case-interactions__actions">
        {autenticado ? <button
          type="button"
          className={`secondary case-interactions__like${datos.me_gusta_usuario ? ' is-active' : ''}`}
          aria-pressed={datos.me_gusta_usuario}
          disabled={procesando}
          onClick={() => void cambiarMeGusta()}>
          <span aria-hidden="true">♥</span>
          {datos.me_gusta_usuario ? 'Te gusta' : 'Me gusta'}
          <span className="case-interactions__count">{datos.total_me_gusta}</span>
        </button> : <span className="case-interactions__count-label">
          <span aria-hidden="true">♥</span> {datos.total_me_gusta} Me gusta
        </span>}
        <span className="case-interactions__count-label">
          {datos.total_comentarios} {datos.total_comentarios === 1 ? 'comentario' : 'comentarios'}
        </span>
      </div>

      {datos.comentarios.length > 0 && <div className="case-interactions__comments" aria-live="polite">
        {datos.comentarios.map(item => <article className="case-interactions__comment" key={item.id}>
          <div className="case-interactions__comment-heading">
            <strong>{item.autor}</strong>
            <time dateTime={item.creado_en}>{fecha(item.creado_en)}</time>
          </div>
          <p>{item.comentario}</p>
        </article>)}
      </div>}

      {autenticado ? <form className="case-interactions__form" onSubmit={publicarComentario}>
        <label className="visually-hidden" htmlFor={`comentario-caso-${casoId}`}>Escribe un comentario</label>
        <textarea
          id={`comentario-caso-${casoId}`}
          value={comentario}
          onChange={event => setComentario(event.target.value)}
          maxLength={1000}
          rows={2}
          placeholder="Escribe un comentario…"
          required
        />
        <div className="case-interactions__form-footer">
          <small>{comentario.length}/1000</small>
          <button type="submit" disabled={procesando || !comentario.trim()}>
            {procesando ? 'Guardando…' : 'Comentar'}
          </button>
        </div>
      </form> : <p className="muted case-interactions__login">
        <Link to="/acceso">Inicia sesión</Link> para dar Me gusta o comentar.
      </p>}
    </>}
  </section>;
}
