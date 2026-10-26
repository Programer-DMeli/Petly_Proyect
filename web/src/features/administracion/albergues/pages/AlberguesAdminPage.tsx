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
  const [registeredTotal, setRegisteredTotal] = useState(0);
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
      const listRequest = django<Page<Shelter>>(
        `/api/albergues/?page=${page}&search=${encodeURIComponent(query)}`
      );
      const allSheltersRequest = query
        ? django<Page<Shelter>>('/api/albergues/?page=1&search=')
        : listRequest;
      const [data, owners, allShelters] = await Promise.all([
        listRequest,
        django<Account[]>('/api/albergues/cuentas-disponibles/'),
        allSheltersRequest,
      ]);
      setItems(data.results);
      setNext(data.next !== null);
      setTotal(data.count);
      setRegisteredTotal(allShelters.count);
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
  useEffect(() => {
    if (!deleting) return;
    function closeOnEscape(event: KeyboardEvent) {
      if (event.key === 'Escape' && !busy) setDeleting(null);
    }
    window.addEventListener('keydown', closeOnEscape);
    return () => window.removeEventListener('keydown', closeOnEscape);
  }, [busy, deleting]);
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
      setSuccess('Albergue eliminado correctamente');
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
      <div className="shelter-overview">
        <div className="shelter-statistics">
          <article className="shelter-stat-card">
            <svg aria-hidden="true" viewBox="0 0 24 24">
              <path d="m3 10 9-7 9 7M5 9v12h14V9M9 21v-7h6v7" />
            </svg>
            <strong>{registeredTotal}</strong>
            <span>Albergues registrados</span>
          </article>
          <article className="shelter-stat-card">
            <svg aria-hidden="true" viewBox="0 0 24 24">
              <path d="M16 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2M10 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8ZM20 21v-2a4 4 0 0 0-3-3.87M16 3.13a4 4 0 0 1 0 7.75" />
            </svg>
            <strong>{accounts.filter(account => account.asignada).length}</strong>
            <span>Responsables vinculados</span>
          </article>
        </div>
        {success === 'Albergue eliminado correctamente' && (
          <div className="shelter-success" role="status">
            <svg aria-hidden="true" viewBox="0 0 24 24">
              <circle cx="12" cy="12" r="9" />
              <path d="m8 12 2.5 2.5L16 9" />
            </svg>
            <div>
              <h2>Albergue eliminado correctamente</h2>
              <p>La cuenta del propietario se conserva.</p>
            </div>
          </div>
        )}
        <Feedback
          error={error}
          success={success === 'Albergue eliminado correctamente' ? undefined : success}
        />
        <form
          className="shelter-search"
          onSubmit={e => {
            e.preventDefault();
            setPage(1);
            setQuery(search);
          }}
        >
          <h2>Buscar albergues</h2>
          <label className="shelter-search__field">
            <svg aria-hidden="true" viewBox="0 0 24 24">
              <circle cx="10.8" cy="10.8" r="6.8" />
              <path d="m16 16 4.5 4.5" />
            </svg>
            <span className="visually-hidden">Nombre del albergue o responsable</span>
            <input
              aria-label="Nombre del albergue o responsable"
              placeholder="Nombre del albergue o responsable..."
              value={search}
              onChange={e => setSearch(e.target.value)}
              maxLength={120}
            />
          </label>
          <div className="shelter-search__actions">
            <button type="submit" disabled={busy}>
              Buscar
            </button>
            <button
              className="secondary"
              type="button"
              disabled={busy}
              onClick={() => void load()}
            >
              Actualizar lista
            </button>
          </div>
        </form>
      </div>
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
      <section className="card shelter-list" aria-labelledby="shelter-list-title">
        <header className="shelter-list__header">
          <h2 id="shelter-list-title">Albergues registrados</h2>
          <span>
            {total} {total === 1 ? 'registro' : 'registros'}
          </span>
        </header>
        <div className="shelter-list__content">
          {busy ? (
            <p className="shelter-empty" role="status">
              Cargando albergues…
            </p>
          ) : items.length > 0 ? (
            <div className="grid shelter-card-grid">
              {items.map(s => (
                <article className="card shelter-card" key={s.id}>
                  <div className="shelter-card__top">
                    <span className="shelter-card__icon" aria-hidden="true">
                      <svg viewBox="0 0 24 24"><ellipse cx="12" cy="15.5" rx="5" ry="4" /><circle cx="6.5" cy="9" r="1.8" /><circle cx="10.2" cy="6.7" r="1.8" /><circle cx="14" cy="6.7" r="1.8" /><circle cx="17.5" cy="9" r="1.8" /></svg>
                    </span>
                    <span className={`shelter-card__status${s.activo ? '' : ' is-inactive'}`}>{s.activo ? 'Activo' : 'Inactivo'}</span>
                  </div>
                  <h3>{s.nombre}</h3>
                  <p className="shelter-card__location">
                    <svg aria-hidden="true" viewBox="0 0 24 24"><path d="M20 10c0 5-8 12-8 12S4 15 4 10a8 8 0 1 1 16 0Z" /><circle cx="12" cy="10" r="2.5" /></svg>
                    <span>{s.direccion}</span>
                  </p>
                  <div className="shelter-card__facts">
                    <div><span>Capacidad</span><strong>{s.capacidad_maxima} mascotas</strong></div>
                    <div><span>Responsable</span><strong>{s.responsable_nombre || 'Sin indicar'}</strong></div>
                  </div>
                  <p className="shelter-card__description">{s.descripcion || 'Información institucional pendiente.'}</p>
                  <p className="shelter-card__owner"><span>Cuenta propietaria</span>{s.propietario_correo || 'Pendiente de vinculación'}</p>
                  <div className="shelter-card__divider" />
                  <div className="actions">
                    <button className="shelter-action shelter-action--view" onClick={() => setDetail(s)}>
                      <svg aria-hidden="true" viewBox="0 0 24 24"><path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12Z" /><circle cx="12" cy="12" r="3" /></svg>Ver
                    </button>
                    <button className="shelter-action shelter-action--edit" onClick={() => edit(s)}>
                      <svg aria-hidden="true" viewBox="0 0 24 24"><path d="m15 5 4 4M4 20l4-.8L19 8a2.1 2.1 0 0 0-3-3L5 16l-1 4Z" /></svg>Editar
                    </button>
                    <button className="shelter-action shelter-action--delete" onClick={() => setDeleting(s)}>
                      <svg aria-hidden="true" viewBox="0 0 24 24"><path d="M3 6h18M8 6V4h8v2m3 0-1 14H6L5 6m4 4v6m6-6v6" /></svg>Eliminar
                    </button>
                  </div>
                </article>
              ))}
            </div>
          ) : (
            <div className="shelter-empty">
              <h3>
                {query
                  ? 'No encontramos albergues con esa búsqueda'
                  : 'Aún no hay albergues registrados'}
              </h3>
              <p>
                {query
                  ? 'Prueba con otro nombre de albergue o responsable.'
                  : 'Los albergues registrados aparecerán aquí.'}
              </p>
            </div>
          )}
          <div className="shelter-list__pagination">
            <button disabled={busy || page === 1} onClick={() => setPage(v => v - 1)}>
              Anterior
            </button>
            <span>Página {page}</span>
            <button disabled={busy || !next} onClick={() => setPage(v => v + 1)}>
              Siguiente
            </button>
          </div>
        </div>
      </section>
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
        <div className="shelter-delete-backdrop">
          <section
            className="shelter-delete-dialog"
            role="dialog"
            aria-modal="true"
            aria-labelledby="shelter-delete-title"
          >
            <h2 id="shelter-delete-title">¿Eliminar {deleting.nombre}?</h2>
            <p>
              Se eliminarán sus datos institucionales y áreas. La cuenta del propietario se conserva.
              Si tiene mascotas, primero debes gestionarlas o desactivar el albergue.
            </p>
            <div className="actions">
              <button className="danger" disabled={busy} onClick={() => void remove()}>
                {busy ? 'Eliminando…' : 'Confirmar eliminación'}
              </button>
              <button className="secondary" disabled={busy} onClick={() => setDeleting(null)}>
                Conservar albergue
              </button>
            </div>
          </section>
        </div>
      )}
    </>
  );
}
