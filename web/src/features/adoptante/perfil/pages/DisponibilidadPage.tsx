import { useEffect, useState } from 'react';
import { spring, json, errorMessage } from '../../../../services/http';
import Feedback from '../../../../components/Feedback';
type Franja = {
  id?: number;
  diaSemana: string;
  horaInicio: string;
  horaFin: string;
  descripcion: string;
};
const dias = ['Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes', 'Sábado', 'Domingo'];
export default function DisponibilidadPage() {
  const [rows, setRows] = useState<Franja[]>([]),
    [error, setError] = useState(''),
    [success, setSuccess] = useState(''),
    [busy, setBusy] = useState(false);
  const [editing, setEditing] = useState<number | null>(null);
  const [draft, setDraft] = useState<Franja>({
    diaSemana: 'Lunes',
    horaInicio: '09:00',
    horaFin: '10:00',
    descripcion: '',
  });
  async function load() {
    setBusy(true);
    try {
      setRows(await spring<Franja[]>('/api/perfil/disponibilidad'));
      setError('');
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }
  useEffect(() => {
    void load();
  }, []);
  async function save(next: Franja[]) {
    setBusy(true);
    setError('');
    setSuccess('');
    try {
      setRows(
        await spring<Franja[]>('/api/perfil/disponibilidad', { method: 'POST', body: json(next) })
      );
      setSuccess('Disponibilidad guardada.');
      setEditing(null);
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }
  return (
    <section className="card">
      <h1>Disponibilidad semanal</h1>
      <p>Zona horaria: America/Lima. Estos horarios son para coordinar visitas y entrevistas.</p>
      <Feedback error={error} success={success} />
      <button disabled={busy} onClick={() => void load()}>
        Recargar horarios
      </button>
      {rows.length === 0 && <p>Sin horarios registrados.</p>}
      {rows.map((f, i) => (
        <article className="card" key={f.id ?? i}>
          <p>
            {f.diaSemana}: {f.horaInicio}–{f.horaFin} · {f.descripcion}
          </p>
          <button
            disabled={busy}
            onClick={() => {
              setDraft(f);
              setEditing(i);
            }}
          >
            Editar franja
          </button>
          <button disabled={busy} onClick={() => void save(rows.filter((_, index) => index !== i))}>
            Quitar franja
          </button>
        </article>
      ))}
      <form
        onSubmit={e => {
          e.preventDefault();
          void save(
            editing === null ? [...rows, draft] : rows.map((f, i) => (i === editing ? draft : f))
          );
        }}
      >
        <fieldset disabled={busy}>
          <label>
            Día
            <select
              aria-label="Día"
              value={draft.diaSemana}
              onChange={e => setDraft({ ...draft, diaSemana: e.target.value })}
            >
              {dias.map(d => (
                <option key={d}>{d}</option>
              ))}
            </select>
          </label>
          <label>
            Hora de inicio
            <input
              type="time"
              required
              value={draft.horaInicio}
              onChange={e => setDraft({ ...draft, horaInicio: e.target.value })}
            />
          </label>
          <label>
            Hora de fin
            <input
              type="time"
              required
              value={draft.horaFin}
              onChange={e => setDraft({ ...draft, horaFin: e.target.value })}
            />
          </label>
          <label>
            Descripción de disponibilidad
            <input
              maxLength={300}
              value={draft.descripcion}
              onChange={e => setDraft({ ...draft, descripcion: e.target.value })}
            />
          </label>
          <button>Guardar franja</button>
          {editing !== null && (
            <button type="button" onClick={() => setEditing(null)}>
              Cancelar edición
            </button>
          )}
        </fieldset>
      </form>
    </section>
  );
}
