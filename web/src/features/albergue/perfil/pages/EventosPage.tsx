import { useEffect, useState } from 'react';
import { django, json, errorMessage } from '../../../../services/http';
import Feedback from '../../../../components/Feedback';
type Evento = {
  id: number;
  titulo: string;
  descripcion: string;
  inicio: string;
  fin: string;
  ubicacion: string;
  contacto_publico: string;
  cancelado: boolean;
  albergue_nombre: string;
};
type Draft = {
  titulo: string;
  descripcion: string;
  inicio: string;
  fin: string;
  ubicacion: string;
  contacto_publico: string;
};
const empty: Draft = {
  titulo: '',
  descripcion: '',
  inicio: '',
  fin: '',
  ubicacion: '',
  contacto_publico: '',
};
const fecha = (v: string) =>
  new Intl.DateTimeFormat('es-PE', {
    dateStyle: 'medium',
    timeStyle: 'short',
    timeZone: 'America/Lima',
  }).format(new Date(v));
const local = (v: string) => new Date(Date.parse(v) - 5 * 3600000).toISOString().slice(0, 16);
export default function EventosPage({ gestionar = false }: { gestionar?: boolean }) {
  const [rows, setRows] = useState<Evento[]>([]),
    [draft, setDraft] = useState<Draft>(empty),
    [editing, setEditing] = useState<number | null>(null),
    [error, setError] = useState(''),
    [success, setSuccess] = useState(''),
    [busy, setBusy] = useState(false);
  async function load() {
    try {
      const response = await django<Evento[] | { results: Evento[] }>('/api/eventos/');
      setRows(Array.isArray(response) ? response : response.results);
      setError('');
    } catch (e) {
      setError(errorMessage(e));
    }
  }
  useEffect(() => {
    void load();
  }, []);
  async function save() {
    setBusy(true);
    setError('');
    setSuccess('');
    try {
      await django(editing ? `/api/eventos/${editing}/` : '/api/eventos/', {
        method: editing ? 'PATCH' : 'POST',
        body: json({ ...draft, inicio: draft.inicio + ':00-05:00', fin: draft.fin + ':00-05:00' }),
      });
      setDraft(empty);
      setEditing(null);
      setSuccess('Evento guardado.');
      await load();
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }
  return (
    <section className="card">
      <h1>Eventos de adopción</h1>
      <p>Fechas y horarios en America/Lima.</p>
      <Feedback error={error} success={success} />
      <button disabled={busy} onClick={() => void load()}>
        Actualizar eventos
      </button>
      {gestionar && (
        <form
          onSubmit={e => {
            e.preventDefault();
            void save();
          }}
        >
          <h2>{editing ? 'Editar evento' : 'Publicar evento'}</h2>
          <fieldset disabled={busy}>
            <label>
              Título del evento
              <input
                required
                maxLength={150}
                value={draft.titulo}
                onChange={e => setDraft({ ...draft, titulo: e.target.value })}
              />
            </label>
            <label>
              Descripción del evento
              <textarea
                required
                maxLength={3000}
                value={draft.descripcion}
                onChange={e => setDraft({ ...draft, descripcion: e.target.value })}
              />
            </label>
            <label>
              Inicio del evento
              <input
                required
                type="datetime-local"
                value={draft.inicio}
                onChange={e => setDraft({ ...draft, inicio: e.target.value })}
              />
            </label>
            <label>
              Fin del evento
              <input
                required
                type="datetime-local"
                value={draft.fin}
                onChange={e => setDraft({ ...draft, fin: e.target.value })}
              />
            </label>
            <label>
              Ubicación del evento
              <input
                required
                maxLength={255}
                value={draft.ubicacion}
                onChange={e => setDraft({ ...draft, ubicacion: e.target.value })}
              />
            </label>
            <label>
              Contacto público del evento
              <input
                required
                maxLength={150}
                value={draft.contacto_publico}
                onChange={e => setDraft({ ...draft, contacto_publico: e.target.value })}
              />
            </label>
            <button>{editing ? 'Guardar evento' : 'Publicar evento'}</button>
            {editing && (
              <button
                type="button"
                onClick={() => {
                  setEditing(null);
                  setDraft(empty);
                }}
              >
                Descartar edición
              </button>
            )}
          </fieldset>
        </form>
      )}
      {!rows.length && <p>No hay próximos eventos.</p>}
      {rows.map(v => (
        <article className="card" key={v.id}>
          <h2>{v.titulo}</h2>
          <strong>{v.cancelado ? 'CANCELADO' : 'PROGRAMADO'}</strong>
          <p>{v.descripcion}</p>
          <p>
            {fecha(v.inicio)} – {fecha(v.fin)}
          </p>
          <p>
            {v.ubicacion} · {v.albergue_nombre}
          </p>
          <p>Contacto: {v.contacto_publico}</p>
          {gestionar && !v.cancelado && (
            <div className="actions">
              <button
                disabled={busy}
                onClick={() => {
                  setEditing(v.id);
                  setDraft({ ...v, inicio: local(v.inicio), fin: local(v.fin) });
                }}
              >
                Editar evento
              </button>
              <button
                disabled={busy}
                onClick={() => {
                  if (!window.confirm('¿Cancelar este evento? Se conservará su registro.')) return;
                  setBusy(true);
                  void django(`/api/eventos/${v.id}/`, {
                    method: 'PATCH',
                    body: json({ cancelado: true }),
                  })
                    .then(load)
                    .catch(e => setError(errorMessage(e)))
                    .finally(() => setBusy(false));
                }}
              >
                Cancelar evento
              </button>
            </div>
          )}
        </article>
      ))}
    </section>
  );
}
