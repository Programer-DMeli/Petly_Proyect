import { useCallback, useEffect, useMemo, useState } from 'react';
import Feedback from '../../../../components/Feedback';
import { errorMessage, json, spring } from '../../../../services/http';
import { getSession, type Role } from '../../../../services/session';
import { useDialogFocus } from '../../../../services/useDialogFocus';

type UsuarioAdmin = {
  id: number;
  username: string | null;
  correo: string;
  nombres: string | null;
  apellidos: string | null;
  rol: Role;
  creadoEn: string | null;
  cuentaSuspendida: boolean;
  motivoSuspension: string | null;
  suspendidaEn: string | null;
  suspensionHasta: string | null;
  suspendidaPorId: number | null;
};

const ROLES: Role[] = ['ADOPTANTE', 'ALBERGUE', 'ADMIN'];
const ETIQUETAS: Record<Role, string> = {
  ADOPTANTE: 'Adoptante',
  ALBERGUE: 'Albergue',
  ADMIN: 'Administrador',
};

export default function RolesUsuariosPage() {
  const [usuarios, setUsuarios] = useState<UsuarioAdmin[]>([]);
  const [roles, setRoles] = useState<Record<number, Role>>({});
  const [busqueda, setBusqueda] = useState('');
  const [cargando, setCargando] = useState(true);
  const [guardandoId, setGuardandoId] = useState<number | null>(null);
  const [pendiente, setPendiente] = useState<UsuarioAdmin | null>(null);
  const [suspensionPendiente, setSuspensionPendiente] = useState<UsuarioAdmin | null>(null);
  const [motivoSuspension, setMotivoSuspension] = useState('');
  const [hastaSuspension, setHastaSuspension] = useState('');
  const [suspendiendoId, setSuspendiendoId] = useState<number | null>(null);
  const [error, setError] = useState('');
  const [mensaje, setMensaje] = useState('');
  const miId = Number(getSession()?.identity.id);
  useDialogFocus(pendiente !== null, () => {
    if (guardandoId === null) {
      setPendiente(null);
      setError('');
    }
  });
  useDialogFocus(suspensionPendiente !== null, () => {
    if (suspendiendoId === null) {
      setSuspensionPendiente(null);
      setMotivoSuspension('');
      setHastaSuspension('');
      setError('');
    }
  });

  const cargar = useCallback(async (signal?: AbortSignal) => {
    setCargando(true);
    setError('');
    try {
      const resultado = await spring<UsuarioAdmin[]>('/api/admin/usuarios', { signal });
      if (signal?.aborted) return;
      setUsuarios(resultado);
      setRoles(Object.fromEntries(resultado.map(usuario => [usuario.id, usuario.rol])));
    } catch (causa) {
      if (!signal?.aborted) setError(errorMessage(causa));
    } finally {
      if (!signal?.aborted) setCargando(false);
    }
  }, []);

  useEffect(() => {
    const controller = new AbortController();
    void cargar(controller.signal);
    return () => controller.abort();
  }, [cargar]);

  const filtrados = useMemo(() => {
    const consulta = busqueda.trim().toLocaleLowerCase('es-PE');
    if (!consulta) return usuarios;
    return usuarios.filter(usuario => [
      usuario.username,
      usuario.correo,
      usuario.nombres,
      usuario.apellidos,
      ETIQUETAS[usuario.rol],
    ].some(valor => valor?.toLocaleLowerCase('es-PE').includes(consulta)));
  }, [busqueda, usuarios]);

  async function guardar(usuario: UsuarioAdmin) {
    const nuevoRol = roles[usuario.id];
    if (!nuevoRol || nuevoRol === usuario.rol || guardandoId !== null || suspendiendoId !== null) return;
    setGuardandoId(usuario.id);
    setError('');
    setMensaje('');
    try {
      const actualizado = await spring<UsuarioAdmin>(`/api/admin/usuarios/${usuario.id}/rol`, {
        method: 'PATCH',
        body: json({ rol: nuevoRol }),
      });
      setUsuarios(actuales => actuales.map(item => item.id === actualizado.id ? actualizado : item));
      setRoles(actuales => ({ ...actuales, [actualizado.id]: actualizado.rol }));
      setMensaje(`El rol de ${actualizado.correo} se actualizó. La cuenta deberá iniciar sesión nuevamente.`);
      setPendiente(null);
    } catch (causa) {
      setError(errorMessage(causa));
    } finally {
      setGuardandoId(null);
    }
  }

  async function suspender(usuario: UsuarioAdmin) {
    const motivo = motivoSuspension.trim();
    if (!motivo || motivo.length > 500 || suspendiendoId !== null || guardandoId !== null) return;
    if (hastaSuspension && new Date(hastaSuspension).getTime() <= Date.now()) {
      setError('La fecha de vencimiento debe ser futura.');
      return;
    }

    setSuspendiendoId(usuario.id);
    setError('');
    setMensaje('');
    try {
      const actualizado = await spring<UsuarioAdmin>(`/api/admin/usuarios/${usuario.id}/suspension`, {
        method: 'PATCH',
        body: json({ motivo, hasta: hastaSuspension || null }),
      });
      setUsuarios(actuales => actuales.map(item => item.id === actualizado.id ? actualizado : item));
      setMensaje(`La cuenta ${actualizado.correo} quedó suspendida y sus sesiones fueron cerradas.`);
      setSuspensionPendiente(null);
      setMotivoSuspension('');
      setHastaSuspension('');
    } catch (causa) {
      setError(errorMessage(causa));
    } finally {
      setSuspendiendoId(null);
    }
  }

  return <main className="admin-user-roles">
    <header className="admin-user-roles__heading">
      <p className="eyebrow">Administración · Accesos</p>
      <h1>Roles y cuentas</h1>
      <p className="muted">Consulta las cuentas Petly y administra el rol que define sus permisos.</p>
    </header>

    <Feedback error={pendiente || suspensionPendiente ? '' : error} success={mensaje} />
    <section className="card admin-user-roles__panel" aria-labelledby="roles-cuentas-titulo">
      <div className="admin-user-roles__toolbar">
        <div>
          <h2 id="roles-cuentas-titulo">Cuentas registradas</h2>
          <p className="muted">Los cambios de rol cierran las sesiones activas de esa cuenta.</p>
        </div>
        <button type="button" className="secondary" onClick={() => void cargar()} disabled={cargando || guardandoId !== null || suspendiendoId !== null}>
          Actualizar lista
        </button>
      </div>

      <label className="admin-user-roles__search">
        Buscar cuenta
        <input
          type="search"
          value={busqueda}
          onChange={event => setBusqueda(event.target.value)}
          placeholder="Correo, nombre o rol"
        />
      </label>

      {cargando ? <p role="status">Cargando cuentas…</p> : error && usuarios.length === 0 ? null
        : filtrados.length === 0 ? <p className="admin-user-roles__empty">No hay cuentas que coincidan con la búsqueda.</p>
          : <div className="admin-user-roles__table-wrap">
            <table className="admin-user-roles__table">
              <thead><tr>
                <th scope="col">Cuenta</th>
                <th scope="col">Nombre</th>
                <th scope="col">Rol actual</th>
                <th scope="col">Cambiar rol</th>
                <th scope="col">Estado de cuenta</th>
                <th scope="col"><span className="admin-user-roles__visually-hidden">Acciones</span></th>
              </tr></thead>
              <tbody>{filtrados.map(usuario => {
                const seleccionado = roles[usuario.id] ?? usuario.rol;
                const propio = usuario.id === miId;
                const cambiado = seleccionado !== usuario.rol;
                return <tr key={usuario.id}>
                  <td>
                    <strong>{usuario.correo}</strong>
                    {usuario.username && <small>@{usuario.username}</small>}
                  </td>
                  <td>{[usuario.nombres, usuario.apellidos].filter(Boolean).join(' ') || '—'}</td>
                  <td><span className={`admin-user-roles__badge admin-user-roles__badge--${usuario.rol.toLowerCase()}`}>
                    {ETIQUETAS[usuario.rol]}
                  </span></td>
                  <td>
                    <label className="admin-user-roles__visually-hidden" htmlFor={`rol-${usuario.id}`}>Nuevo rol para {usuario.correo}</label>
                    <select
                      id={`rol-${usuario.id}`}
                      value={seleccionado}
                      disabled={propio || guardandoId !== null}
                      onChange={event => setRoles(actuales => ({ ...actuales, [usuario.id]: event.target.value as Role }))}
                    >{ROLES.map(rol => <option key={rol} value={rol}>{ETIQUETAS[rol]}</option>)}</select>
                  </td>
                  <td>
                    <span className={`admin-user-roles__badge ${usuario.cuentaSuspendida ? 'admin-user-roles__badge--suspendida' : 'admin-user-roles__badge--activa'}`}>
                      {usuario.cuentaSuspendida ? 'Suspendida' : 'Activa'}
                    </span>
                    {usuario.cuentaSuspendida && usuario.motivoSuspension && <small className="admin-user-roles__suspension-detail">
                      {usuario.motivoSuspension} · {usuario.suspensionHasta
                        ? `Hasta ${new Date(usuario.suspensionHasta).toLocaleString('es-PE')}`
                        : 'Sin vencimiento'}
                    </small>}
                  </td>
                  <td>
                    <button
                      type="button"
                      className="button"
                      disabled={!cambiado || propio || guardandoId !== null || suspendiendoId !== null}
                      onClick={() => { setError(''); setPendiente(usuario); }}
                    >Guardar rol</button>
                    <button
                      type="button"
                      className="secondary admin-user-roles__suspend-button"
                      disabled={propio || usuario.cuentaSuspendida || guardandoId !== null || suspendiendoId !== null}
                      onClick={() => { setError(''); setMotivoSuspension(''); setHastaSuspension(''); setSuspensionPendiente(usuario); }}
                    >{usuario.cuentaSuspendida ? 'Suspendida' : 'Suspender'}</button>
                  </td>
                </tr>;
              })}</tbody>
            </table>
          </div>}
      {!cargando && usuarios.length > 0 && <p className="admin-user-roles__count" aria-live="polite">
        {filtrados.length} de {usuarios.length} cuentas
      </p>}
    </section>
    {pendiente && <div className="admin-user-roles__overlay">
      <section
        className="card admin-user-roles__dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby="confirmar-cambio-rol-titulo"
        aria-describedby="confirmar-cambio-rol-detalle"
      >
        <p className="eyebrow">Confirmar permisos</p>
        <h2 id="confirmar-cambio-rol-titulo">¿Cambiar el rol de esta cuenta?</h2>
        <p id="confirmar-cambio-rol-detalle">
          <strong>{pendiente.correo}</strong> pasará de <strong>{ETIQUETAS[pendiente.rol]}</strong> a{' '}
          <strong>{ETIQUETAS[roles[pendiente.id] ?? pendiente.rol]}</strong>. El cambio se sincronizará con Django
          y revocará las sesiones activas de la cuenta.
        </p>
        {error && <p className="feedback error" role="alert">{error}</p>}
        <div className="admin-user-roles__dialog-actions">
          <button
            type="button"
            className="secondary"
            disabled={guardandoId !== null}
            onClick={() => { setPendiente(null); setError(''); }}
          >Cancelar</button>
          <button
            type="button"
            className="button"
            disabled={guardandoId !== null}
            onClick={() => void guardar(pendiente)}
          >{guardandoId === pendiente.id ? 'Aplicando cambio…' : 'Confirmar cambio'}</button>
        </div>
      </section>
    </div>}
    {suspensionPendiente && <div className="admin-user-roles__overlay">
      <section
        className="card admin-user-roles__dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby="confirmar-suspension-titulo"
        aria-describedby="confirmar-suspension-detalle"
      >
        <p className="eyebrow">Seguridad de la plataforma</p>
        <h2 id="confirmar-suspension-titulo">Suspender cuenta</h2>
        <p id="confirmar-suspension-detalle">
          La cuenta <strong>{suspensionPendiente.correo}</strong> perderá acceso y sus sesiones activas se cerrarán.
          Si dejas la fecha vacía, la suspensión no tendrá vencimiento.
        </p>
        <label className="admin-user-roles__dialog-field" htmlFor="motivo-suspension">Motivo (obligatorio)
          <textarea
            id="motivo-suspension"
            value={motivoSuspension}
            maxLength={500}
            rows={4}
            required
            onChange={event => setMotivoSuspension(event.target.value)}
            placeholder="Describe por qué se restringe esta cuenta"
          />
          <small>{motivoSuspension.length}/500 caracteres</small>
        </label>
        <label className="admin-user-roles__dialog-field" htmlFor="hasta-suspension">Vigencia (opcional)
          <input
            id="hasta-suspension"
            type="datetime-local"
            value={hastaSuspension}
            onChange={event => setHastaSuspension(event.target.value)}
          />
        </label>
        {error && <p className="feedback error" role="alert">{error}</p>}
        <div className="admin-user-roles__dialog-actions">
          <button
            type="button"
            className="secondary"
            disabled={suspendiendoId !== null}
            onClick={() => { setSuspensionPendiente(null); setMotivoSuspension(''); setHastaSuspension(''); setError(''); }}
          >Cancelar</button>
          <button
            type="button"
            className="button"
            disabled={suspendiendoId !== null || !motivoSuspension.trim() || motivoSuspension.length > 500}
            onClick={() => void suspender(suspensionPendiente)}
          >{suspendiendoId === suspensionPendiente.id ? 'Aplicando suspensión…' : 'Confirmar suspensión'}</button>
        </div>
      </section>
    </div>}
  </main>;
}
