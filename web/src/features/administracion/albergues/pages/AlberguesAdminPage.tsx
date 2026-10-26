import { useCallback, useEffect, useState } from 'react';
import Feedback from '../../../../components/Feedback';
import { django, json, errorMessage } from '../../../../services/http';
import type { Shelter, Page } from '../../../../services/types';
interface Account {
  id: number;
  correo: string;
  asignada: boolean;
}
const initial = {
  nombre: '',
  direccion: '',
  descripcion: '',
  telefono: '',
  email: '',
  sitio_web: '',
  horario_atencion: '',
  capacidad_maxima: 0,
  responsable_nombre: '',
  responsable_email: '',
  responsable_telefono: '',
  propietario_spring_id: '',
  activo: true,
};
const labels = {
  nombre: 'Nombre del albergue',
  direccion: 'Ubicación / dirección',
  telefono: 'Teléfono de contacto',
  email: 'Correo institucional',
  sitio_web: 'Sitio web',
  horario_atencion: 'Horario de atención',
  responsable_nombre: 'Nombre del responsable',
  responsable_email: 'Correo del responsable',
  responsable_telefono: 'Teléfono del responsable',
};
export default function AlberguesAdminPage() {
  const [items, setItems] = useState<Shelter[]>([]);
  const [accounts, setAccounts] = useState<Account[]>([]);
  const [page, setPage] = useState(1);
  const [next, setNext] = useState(false);
  const [total, setTotal] = useState(0);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [busy, setBusy] = useState(false);
  const [ready, setReady] = useState(false);
  const [editing, setEditing] = useState<number | null>(null);
  const [open, setOpen] = useState(false);
  const [draft, setDraft] = useState(initial);
  const [detail, setDetail] = useState<Shelter | null>(null);
  const [deleting, setDeleting] = useState<Shelter | null>(null);
  const [search, setSearch] = useState('');
  const [query, setQuery] = useState('');
  const load = useCallback(async () => {
    setBusy(true);
    setError('');
    try {
      const [data, owners] = await Promise.all([
        django<Page<Shelter>>(`/api/albergues/?page=${page}&search=${encodeURIComponent(query)}`),
        django<Account[]>('/api/albergues/cuentas-disponibles/'),
      ]);
      setItems(data.results);
      setNext(data.next !== null);
      setTotal(data.count);
      setAccounts(owners);
      setReady(true);
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }, [page, query]);
  useEffect(() => {
    void load();
  }, [load]);
  useEffect(() => {
    if (!detail) return;
    function closeOnEscape(event: KeyboardEvent) {
      if (event.key === 'Escape') setDetail(null);
    }
    window.addEventListener('keydown', closeOnEscape);
    return () => window.removeEventListener('keydown', closeOnEscape);
  }, [detail]);
  function create() {
    setEditing(null);
    setDraft(initial);
    setOpen(true);
    setSuccess('');
    setError('');
  }
  function edit(s: Shelter) {
    setEditing(s.id);
    setDraft({
      nombre: s.nombre,
      direccion: s.direccion,
      descripcion: s.descripcion,
      telefono: s.telefono,
      email: s.email,
      sitio_web: s.sitio_web,
      horario_atencion: s.horario_atencion,
      capacidad_maxima: s.capacidad_maxima,
      responsable_nombre: s.responsable_nombre,
      responsable_email: s.responsable_email,
      responsable_telefono: s.responsable_telefono,
      propietario_spring_id:
        s.propietario_spring_id === null ? '' : String(s.propietario_spring_id),
      activo: s.activo,
    });
    setOpen(true);
    setError('');
    setSuccess('');
  }
  async function save() {
    setBusy(true);
    setError('');
    setSuccess('');
    try {
      const { propietario_spring_id, ...fields } = draft;
      const body = editing
        ? fields
        : { ...fields, propietario_spring_id: Number(propietario_spring_id) };
      await django(editing ? `/api/albergues/${editing}/` : '/api/albergues/', {
        method: editing ? 'PATCH' : 'POST',
        body: json(body),
      });
      setOpen(false);
      setSuccess(
        editing ? 'Albergue actualizado.' : 'Albergue registrado y vinculado a su propietario.'
      );
      await load();
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }
  async function remove() {
    if (!deleting) return;
    setBusy(true);
    setError('');
    try {
      await django(`/api/albergues/${deleting.id}/`, { method: 'DELETE' });
      setDeleting(null);
      setSuccess('Albergue eliminado. La cuenta del propietario se conserva.');
      if (items.length === 1 && page > 1) setPage(p => p - 1);
      else await load();
    } catch (e) {
      setDeleting(null);
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }
  return (
    <>
      <section className="card admin-intro">
        <div>
          <span className="eyebrow">ADMINISTRACIÓN PETLY</span>
          <h1>Albergues y responsables</h1>
          <p className="muted">
            Organiza los refugios que dan una nueva oportunidad a cada mascota.
          </p>
        </div>
        <button disabled={!ready || busy} onClick={create}>
          + Registrar albergue
        </button>
      </section>
      <div className="admin-summary">
        <strong>{total}</strong>
        <span>albergues registrados</span>
        <span className="badge">Responsables vinculados</span>
      </div>
      <Feedback error={error} success={success} />
      <form
        className="actions"
        onSubmit={e => {
          e.preventDefault();
          setPage(1);
          setQuery(search);
        }}
      >
        <label className="search-label">
          Buscar albergue o responsable
          <input value={search} onChange={e => setSearch(e.target.value)} maxLength={120} />
        </label>
        <button disabled={busy}>Buscar albergues</button>
        <button className="secondary" type="button" disabled={busy} onClick={() => void load()}>
          Actualizar lista
        </button>
      </form>
      {open && (
        <section className="card admin-form" aria-label="Formulario de albergue">
          <div className="admin-intro">
            <h2>{editing ? 'Editar albergue' : 'Registrar albergue'}</h2>
            <button className="secondary" disabled={busy} onClick={() => setOpen(false)}>
              Cancelar
            </button>
          </div>
          <form
            onSubmit={e => {
              e.preventDefault();
              void save();
            }}
          >
            <fieldset disabled={busy}>
              <h3>Cuenta e información institucional</h3>
              <label>
                Cuenta propietaria ALBERGUE
                <select
                  aria-label="Cuenta propietaria ALBERGUE"
                  required
                  disabled={editing !== null}
                  value={draft.propietario_spring_id}
                  onChange={e => setDraft({ ...draft, propietario_spring_id: e.target.value })}
                >
                  <option value="">Selecciona una cuenta existente</option>
                  {accounts
                    .filter(a => !a.asignada || String(a.id) === draft.propietario_spring_id)
                    .map(a => (
                      <option key={a.id} value={a.id}>
                        {a.correo}
                      </option>
                    ))}
                </select>
              </label>
              <p className="muted">
                Selecciona una cuenta existente del responsable. Cada cuenta ALBERGUE administra una
                institución.
              </p>
              <div className="grid">
                {(Object.keys(labels) as (keyof typeof labels)[]).map(key => (
                  <label key={key}>
                    {labels[key]}
                    <input
                      type={key.includes('email') ? 'email' : key === 'sitio_web' ? 'url' : 'text'}
                      required={key === 'nombre' || key === 'direccion'}
                      maxLength={
                        key.includes('telefono')
                          ? 20
                          : key === 'direccion'
                            ? 255
                            : key === 'horario_atencion'
                              ? 100
                              : 150
                      }
                      value={draft[key]}
                      onChange={e => setDraft({ ...draft, [key]: e.target.value })}
                    />
                  </label>
                ))}
                <label>
                  Capacidad máxima
                  <input
                    type="number"
                    min={0}
                    step={1}
                    required
                    value={draft.capacidad_maxima}
                    onChange={e => setDraft({ ...draft, capacidad_maxima: Number(e.target.value) })}
                  />
                </label>
              </div>
              <label>
                Descripción institucional
                <textarea
                  value={draft.descripcion}
                  onChange={e => setDraft({ ...draft, descripcion: e.target.value })}
                />
              </label>
              <label>
                <input
                  type="checkbox"
                  checked={draft.activo}
                  onChange={e => setDraft({ ...draft, activo: e.target.checked })}
                />{' '}
                Albergue activo y visible en el catálogo
              </label>
              <button>
                {busy ? 'Guardando…' : editing ? 'Guardar albergue' : 'Crear albergue'}
              </button>
            </fieldset>
          </form>
        </section>
      )}
      {busy ? (
        <p role="status">Cargando…</p>
      ) : (
        <div className="grid">
          {items.map(s => (
            <article className="card shelter-card" key={s.id}>
              <div className="shelter-mark" aria-hidden="true">
                ⌂
              </div>
              <span className="badge">{s.activo ? 'Activo' : 'Inactivo'}</span>
              <h3>{s.nombre}</h3>
              <p className="muted">{s.direccion}</p>
              <p>{s.descripcion || 'Información institucional pendiente.'}</p>
              <dl>
                <dt>Capacidad</dt>
                <dd>{s.capacidad_maxima} mascotas</dd>
                <dt>Responsable</dt>
                <dd>{s.responsable_nombre || 'Sin indicar'}</dd>
                <dt>Cuenta propietaria</dt>
                <dd>{s.propietario_correo || 'Pendiente de vinculación'}</dd>
              </dl>
              <div className="actions">
                <button className="secondary" onClick={() => setDetail(s)}>
                  Ver albergue
                </button>
                <button onClick={() => edit(s)}>Editar albergue</button>
                <button className="danger" onClick={() => setDeleting(s)}>
                  Eliminar albergue
                </button>
              </div>
            </article>
          ))}
        </div>
      )}
      {!busy && !error && items.length === 0 && (
        <section className="card">
          <h2>No hay albergues para mostrar</h2>
          <p>Registra un albergue o cambia tu búsqueda.</p>
        </section>
      )}
      <div className="actions">
        <button disabled={busy || page === 1} onClick={() => setPage(v => v - 1)}>
          Anterior
        </button>
        <span>Página {page}</span>
        <button disabled={busy || !next} onClick={() => setPage(v => v + 1)}>
          Siguiente
        </button>
      </div>
      {detail && (
        <div className="shelter-detail-backdrop">
          <section
            className="shelter-detail-dialog"
            role="dialog"
            aria-modal="true"
            aria-labelledby="shelter-detail-title"
          >
            <h2 id="shelter-detail-title">{detail.nombre}</h2>
            <p>{detail.descripcion || 'Sin descripción institucional.'}</p>
            <dl>
              <dt>Dirección</dt>
              <dd>{detail.direccion}</dd>
              <dt>Contacto</dt>
              <dd>
                {detail.telefono || 'Sin teléfono'} · {detail.email || 'Sin correo'}
              </dd>
              <dt>Responsable</dt>
              <dd>
                {detail.responsable_nombre || 'Sin indicar'} ·{' '}
                {detail.responsable_telefono || 'Sin teléfono'} ·{' '}
                {detail.responsable_email || 'Sin correo'}
              </dd>
              <dt>Horario</dt>
              <dd>{detail.horario_atencion || 'Sin indicar'}</dd>
              <dt>Infraestructura</dt>
              <dd>
                {detail.infraestructura.map(i => i.nombre).join(', ') || 'Sin áreas registradas'}
              </dd>
            </dl>
            <button onClick={() => setDetail(null)}>Cerrar detalle</button>
          </section>
        </div>
      )}
      {deleting && (
        <section
          className="confirmation"
          role="dialog"
          aria-modal="true"
          aria-label="Confirmar eliminación"
        >
          <h2>¿Eliminar {deleting.nombre}?</h2>
          <p>
            Se eliminarán sus datos institucionales y áreas. La cuenta del propietario se conserva.
            Si tiene mascotas, primero debes gestionarlas o desactivar el albergue.
          </p>
          <div className="actions">
            <button className="danger" disabled={busy} onClick={() => void remove()}>
              Confirmar eliminación
            </button>
            <button className="secondary" disabled={busy} onClick={() => setDeleting(null)}>
              Conservar albergue
            </button>
          </div>
        </section>
      )}
    </>
  );
}
