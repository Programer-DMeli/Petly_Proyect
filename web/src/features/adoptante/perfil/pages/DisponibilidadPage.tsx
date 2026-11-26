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
    [formError, setFormError] = useState(''),
    [success, setSuccess] = useState(''),
    [busy, setBusy] = useState(false);
  const [editing, setEditing] = useState<number | null>(null);
  const [formOpen, setFormOpen] = useState(false);
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
  async function save(next: Franja[], successMessage: string, closeForm = false) {
    setBusy(true);
    setError('');
    setSuccess('');
    try {
      setRows(
        await spring<Franja[]>('/api/perfil/disponibilidad', { method: 'POST', body: json(next) })
      );
      setSuccess(successMessage);
      if (closeForm) {
        setEditing(null);
        setFormOpen(false);
        setDraft({ diaSemana: 'Lunes', horaInicio: '09:00', horaFin: '10:00', descripcion: '' });
      }
    } catch (e) {
      const message = errorMessage(e);
      if (/solap|inicio debe ser anterior|franja horaria inválida|hora inválida/i.test(message)) {
        setFormError(message);
      } else {
        setError(message);
      }
    } finally {
      setBusy(false);
    }
  }
  function openForm(franja?: Franja, index: number | null = null) {
    setFormError('');
    setDraft(franja ?? { diaSemana: 'Lunes', horaInicio: '09:00', horaFin: '10:00', descripcion: '' });
    setEditing(index);
    setFormOpen(true);
    window.requestAnimationFrame(() => {
      document.getElementById('availability-form')?.scrollIntoView({ behavior: 'smooth', block: 'center' });
      document.getElementById('availability-day')?.focus();
    });
  }
  return (
    <section className="card availability-page">
      <header className="availability-page__header">
        <p className="workspace-eyebrow">MI PERFIL</p>
        <h1>Disponibilidad semanal</h1>
        <p>Indica los días y horas en que puedes atender visitas o entrevistas.</p>
        <p className="availability-page__note">
          Zona horaria: America/Lima. Estas franjas sirven para coordinar; no reservan una cita automáticamente.
        </p>
      </header>
      <Feedback error={error} success={success} />
      <div className="availability-page__toolbar">
        <h2>Mis horarios</h2>
        <div className="availability-page__toolbar-actions">
          {rows.length > 0 && !formOpen && (
            <button type="button" disabled={busy} onClick={() => openForm()}>Agregar horario</button>
          )}
          <button type="button" className="secondary" disabled={busy} onClick={() => void load()}>
            {busy ? 'Actualizando…' : 'Recargar horarios'}
          </button>
        </div>
      </div>
      {rows.length === 0 ? (
        <div className="availability-empty" role="status">
          <span className="availability-empty__icon" aria-hidden="true">◷</span>
          <div>
            <h3>Aún no tienes horarios disponibles</h3>
            <p>Agrega una franja semanal para que los albergues puedan coordinar contigo una visita o entrevista.</p>
          </div>
          {!formOpen && <button type="button" onClick={() => openForm()}>
            Agregar mi primer horario
          </button>}
        </div>
      ) : (
        <div className="availability-list" aria-label="Horarios registrados">
          {rows.map((f, i) => (
            <article className="availability-slot" key={f.id ?? `${f.diaSemana}-${i}`}>
              <div className="availability-slot__day">{f.diaSemana}</div>
              <div className="availability-slot__details">
                <strong>{f.horaInicio}–{f.horaFin}</strong>
                <span>{f.descripcion.trim() || 'Disponible para coordinar una visita o entrevista.'}</span>
              </div>
              <div className="availability-slot__actions">
                <button
                  type="button"
                  className="secondary"
                  disabled={busy}
                  onClick={() => {
                    setDraft(f);
                    setEditing(i);
                    setFormOpen(true);
                    window.requestAnimationFrame(() => {
                      document.getElementById('availability-form')?.scrollIntoView({ behavior: 'smooth', block: 'center' });
                      document.getElementById('availability-day')?.focus();
                    });
                  }}
                >
                  Editar
                </button>
                <button
                  type="button"
                  className="secondary"
                  disabled={busy}
                  onClick={() => void save(
                    rows.filter((_, index) => index !== i),
                    `Franja del ${f.diaSemana} (${f.horaInicio}–${f.horaFin}) eliminada correctamente.`,
                  )}
                >
                  Quitar
                </button>
              </div>
            </article>
          ))}
        </div>
      )}
      {formOpen && <>
      <div className="availability-form__heading" id="availability-form">
        <h2>{editing === null ? 'Agregar una franja' : 'Editar franja'}</h2>
        <p>Elige el día y un rango de horas en que normalmente podrías atender.</p>
      </div>
      <form
        className="availability-form"
        onSubmit={e => {
          e.preventDefault();
          setError('');
          setSuccess('');
          setFormError('');
          if (draft.horaInicio >= draft.horaFin) {
            setFormError('La hora de inicio debe ser anterior a la hora de fin.');
            return;
          }
          const overlaps = rows.some((franja, index) =>
            index !== editing &&
            franja.diaSemana === draft.diaSemana &&
            draft.horaInicio < franja.horaFin &&
            franja.horaInicio < draft.horaFin
          );
          if (overlaps) {
            setFormError(`Ya tienes una franja el ${draft.diaSemana} que se cruza con ese horario. Elige otro rango.`);
            return;
          }
          void save(
            editing === null ? [...rows, draft] : rows.map((f, i) => (i === editing ? draft : f)),
            editing === null
              ? `Franja del ${draft.diaSemana} (${draft.horaInicio}–${draft.horaFin}) agregada correctamente.`
              : `Franja del ${draft.diaSemana} (${draft.horaInicio}–${draft.horaFin}) editada correctamente.`,
            true,
          );
        }}
      >
        <fieldset disabled={busy}>
          <label htmlFor="availability-day">
            Día
            <select
              id="availability-day"
              aria-label="Día"
              value={draft.diaSemana}
              onChange={e => {
                setDraft({ ...draft, diaSemana: e.target.value });
                setFormError('');
              }}
            >
              {dias.map(d => (
                <option key={d}>{d}</option>
              ))}
            </select>
          </label>
          <label htmlFor="availability-start">
            Hora de inicio
            <input
              id="availability-start"
              type="time"
              required
              value={draft.horaInicio}
              onChange={e => {
                setDraft({ ...draft, horaInicio: e.target.value });
                setFormError('');
              }}
            />
          </label>
          <label htmlFor="availability-end">
            Hora de fin
            <input
              id="availability-end"
              type="time"
              required
              value={draft.horaFin}
              onChange={e => {
                setDraft({ ...draft, horaFin: e.target.value });
                setFormError('');
              }}
            />
          </label>
          {formError && <p className="availability-form__error" role="alert">{formError}</p>}
          <label htmlFor="availability-description">
            Descripción de disponibilidad
            <input
              id="availability-description"
              maxLength={300}
              value={draft.descripcion}
              onChange={e => setDraft({ ...draft, descripcion: e.target.value })}
            />
          </label>
          <div className="availability-form__actions">
            <button>{editing === null ? 'Guardar franja' : 'Guardar cambios'}</button>
            <button type="button" className="secondary" onClick={() => {
              setEditing(null);
              setFormOpen(false);
              setDraft({ diaSemana: 'Lunes', horaInicio: '09:00', horaFin: '10:00', descripcion: '' });
            }}>
              Cancelar
            </button>
          </div>
        </fieldset>
      </form>
      </>}
    </section>
  );
}
