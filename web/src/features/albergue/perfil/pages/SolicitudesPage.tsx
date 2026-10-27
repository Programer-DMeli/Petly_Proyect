import { useCallback, useEffect, useState } from 'react';
import Feedback from '../../../../components/Feedback';
import { django, json, errorMessage } from '../../../../services/http';
import type { ContratoAdopcion } from '../../../../services/types';

type Solicitud = {
  id: number;
  estado: 'PENDIENTE' | 'OBSERVADA' | 'APROBADA' | 'RECHAZADA' | 'CANCELADA' | 'ENTREGADA';
  fecha_solicitud: string;
  mensaje_postulante: string;
  observaciones_revision: string;
  postulante_nombre: string;
  mascota_nombre: string;
};
type Page<T> = { results: T[] } | T[];

export default function SolicitudesPage() {
  const [rows, setRows] = useState<Solicitud[]>([]);
  const [contracts, setContracts] = useState<ContratoAdopcion[]>([]);
  const [codes, setCodes] = useState<Record<number, string>>({});
  const [reasons, setReasons] = useState<Record<number, string>>({});
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [busy, setBusy] = useState<number | null>(null);
  const load = useCallback(async () => {
    try {
      const [response, contractResponse] = await Promise.all([
        django<Page<Solicitud>>('/api/acceso/solicitudes/'),
        django<Page<ContratoAdopcion>>('/api/contratos/'),
      ]);
      setRows(Array.isArray(response) ? response : response.results);
      setContracts(Array.isArray(contractResponse) ? contractResponse : contractResponse.results);
      setError('');
    } catch (e) { setError(errorMessage(e)); }
  }, []);
  useEffect(() => { void load(); }, [load]);

  async function enviarCodigo(id: number) {
    setBusy(id); setError(''); setSuccess('');
    try {
      await django(`/api/contratos/solicitudes/${id}/generar/`, { method: 'POST' });
      setSuccess('Se envió un código temporal al correo del adoptante. El adoptante debe compartirlo con el albergue después de revisar el contrato.');
      await load();
    } catch (e) { setError(errorMessage(e)); }
    finally { setBusy(null); }
  }

  async function aprobarConCodigo(id: number) {
    const token = (codes[id] ?? '').trim();
    if (!/^\d{8}$/.test(token)) { setError('Ingresa el código de 8 dígitos que recibió el adoptante.'); return; }
    setBusy(id); setError(''); setSuccess('');
    try {
      await django(`/api/contratos/solicitudes/${id}/aprobar-token/`, { method: 'POST', body: json({ token }) });
      setCodes(values => ({ ...values, [id]: '' }));
      setSuccess('La aprobación del contrato quedó registrada con la cuenta del albergue.');
      await load();
    } catch (e) { setError(errorMessage(e)); }
    finally { setBusy(null); }
  }

  async function registrarEntrega(id: number) {
    setBusy(id); setError(''); setSuccess('');
    try {
      await django(`/api/contratos/solicitudes/${id}/entrega/`, { method: 'POST' });
      setSuccess('La entrega quedó registrada y la adopción se completó.');
      await load();
    } catch (e) { setError(errorMessage(e)); }
    finally { setBusy(null); }
  }

  async function decidir(id: number, estado: 'OBSERVADA' | 'APROBADA' | 'RECHAZADA') {
    const motivo = reasons[id]?.trim() ?? '';
    if ((estado === 'OBSERVADA' || estado === 'RECHAZADA') && !motivo) {
      setError('Escribe el motivo antes de observar o rechazar la solicitud.'); return;
    }
    setBusy(id); setError(''); setSuccess('');
    try {
      await django(`/api/acceso/solicitudes/${id}/cambiar-estado/`, {
        method: 'POST', body: json({ estado, observaciones_revision: motivo }),
      });
      setSuccess('Decisión guardada.'); await load();
    } catch (e) { setError(errorMessage(e)); }
    finally { setBusy(null); }
  }

  return <section className="card">
    <h1>Solicitudes de adopción</h1>
    <Feedback error={error} success={success} />
    <button onClick={() => void load()}>Actualizar</button>
    {rows.length === 0 ? <p>No hay solicitudes para revisar.</p> : rows.map(row => <article className="card" key={row.id}>
      <h2>{row.mascota_nombre} · {row.postulante_nombre}</h2>
      <p>Estado: {row.estado} · {new Date(row.fecha_solicitud).toLocaleDateString()}</p>
      {row.mensaje_postulante && <p>Mensaje: {row.mensaje_postulante}</p>}
      {row.estado === 'APROBADA' && (() => {
        const contract = contracts.find(item => item.solicitud_id === row.id);
        const expired = Boolean(contract?.aprobacion_correo?.expira_en)
          && Date.parse(contract?.aprobacion_correo?.expira_en ?? '') <= Date.now();
        const needsResend = ['INVALIDADO', 'EXPIRADO'].includes(contract?.aprobacion_correo?.estado ?? '');
        return <div className="card">
          <h3>Contrato de adopción</h3>
          {!contract || ['EXPIRADO', 'ERROR'].includes(contract.estado) || needsResend || expired ? (
            <button type="button" disabled={busy !== null} onClick={() => void enviarCodigo(row.id)}>
              {busy === row.id ? 'Enviando…' : contract ? 'Enviar un nuevo código al adoptante' : 'Enviar código al adoptante'}
            </button>
          ) : <>
            <p>Estado: {contract.estado_display}</p>
            {contract.aprobacion_correo?.correo && <p>Enviado a: {contract.aprobacion_correo.correo}</p>}
            {contract.aprobacion_correo?.expira_en && contract.estado === 'EN_FIRMA' && (
              <p>El código vence: {new Date(contract.aprobacion_correo.expira_en).toLocaleString('es-PE')}</p>
            )}
            {contract.estado === 'EN_FIRMA' && <>
              <label>Código de 8 dígitos entregado por el adoptante
                <input
                  type="text" inputMode="numeric" autoComplete="one-time-code" maxLength={8}
                  value={codes[row.id] ?? ''}
                  onChange={event => setCodes(values => ({ ...values, [row.id]: event.target.value.replace(/\D/g, '').slice(0, 8) }))}
                />
              </label>
              <button type="button" disabled={busy !== null} onClick={() => void aprobarConCodigo(row.id)}>
                {busy === row.id ? 'Validando…' : 'Registrar aprobación con código'}
              </button>
            </>}
            {contract.estado === 'APROBADO' && <>
              <p>El albergue registró la aprobación del contrato.</p>
              {row.estado !== 'ENTREGADA' && <button type="button" disabled={busy !== null} onClick={() => void registrarEntrega(row.id)}>
                {busy === row.id ? 'Registrando…' : 'Registrar entrega de la mascota'}
              </button>}
            </>}
          </>}
        </div>;
      })()}
      {row.observaciones_revision && <p>Observación: {row.observaciones_revision}</p>}
      {['PENDIENTE', 'OBSERVADA'].includes(row.estado) && <>
        <label>Motivo de observación o rechazo
          <textarea value={reasons[row.id] ?? ''} onChange={e => setReasons(v => ({ ...v, [row.id]: e.target.value }))} />
        </label>
        <div className="grid">
          <button disabled={busy !== null} onClick={() => void decidir(row.id, 'OBSERVADA')}>Observar</button>
          <button disabled={busy !== null} onClick={() => void decidir(row.id, 'APROBADA')}>Aprobar</button>
          <button disabled={busy !== null} onClick={() => void decidir(row.id, 'RECHAZADA')}>Rechazar</button>
        </div>
      </>}
    </article>)}
  </section>;
}
