import { useCallback, useEffect, useState } from 'react';
import Feedback from '../../../../components/Feedback';
import { django, json, errorMessage, downloadDjango } from '../../../../services/http';
import type { ContratoAdopcion } from '../../../../services/types';

type RequestItem = { id: number; estado: string; fecha_solicitud: string; mensaje_postulante: string; observaciones_revision: string; postulante_nombre: string; mascota_nombre: string };
type Page<T> = { results: T[] } | T[];
export default function SolicitudesPage() {
  const [items, setItems] = useState<RequestItem[]>([]);
  const [contracts, setContracts] = useState<ContratoAdopcion[]>([]);
  const [reasons, setReasons] = useState<Record<number, string>>({});
  const [error, setError] = useState(''); const [success, setSuccess] = useState('');
  const [busy, setBusy] = useState<number | null>(null);
  const load = useCallback(async () => {
    try {
      const [result, contractResult] = await Promise.all([
        django<Page<RequestItem>>('/api/acceso/solicitudes/'),
        django<Page<ContratoAdopcion>>('/api/contratos/'),
      ]);
      setItems(Array.isArray(result) ? result : result.results);
      setContracts(Array.isArray(contractResult) ? contractResult : contractResult.results);
      setError('');
    }
    catch (e) { setError(errorMessage(e)); }
  }, []);
  useEffect(() => { void load(); }, [load]);
  async function decide(id: number, estado: 'OBSERVADA' | 'APROBADA' | 'RECHAZADA') {
    const reason = reasons[id]?.trim() ?? '';
    if ((estado === 'OBSERVADA' || estado === 'RECHAZADA') && !reason) { setError('Indica el motivo antes de observar o rechazar.'); return; }
    setBusy(id); setError(''); setSuccess('');
    try { await django(`/api/acceso/solicitudes/${id}/cambiar-estado/`, { method: 'POST', body: json({ estado, observaciones_revision: reason }) }); setSuccess('Decisión guardada.'); await load(); }
    catch (e) { setError(errorMessage(e)); }
    finally { setBusy(null); }
  }
  async function createContract(id: number) {
    setBusy(id); setError(''); setSuccess('');
    try {
      await django(`/api/contratos/solicitudes/${id}/generar/`, { method: 'POST', body: json({}) });
      setSuccess('Contrato enviado al proveedor de firma.'); await load();
    } catch (e) {
      setError(errorMessage(e));
      try { const result = await django<Page<ContratoAdopcion>>('/api/contratos/'); setContracts(Array.isArray(result) ? result : result.results); } catch { /* conserva el error inicial */ }
    } finally { setBusy(null); }
  }
  async function registerDelivery(id: number) {
    setBusy(id); setError('');
    try { await django(`/api/contratos/solicitudes/${id}/entrega/`, { method: 'POST', body: json({}) }); setSuccess('Entrega registrada; la mascota figura como Adoptada.'); await load(); }
    catch (e) { setError(errorMessage(e)); }
    finally { setBusy(null); }
  }
  async function downloadContract(contract: ContratoAdopcion) {
    try { await downloadDjango(`/api/contratos/${contract.public_id}/descargar/`, `contrato-adopcion-${contract.solicitud_id}.pdf`); }
    catch (e) { setError(errorMessage(e)); }
  }
  return <section className="card">
    <h1>Solicitudes de adopción</h1><Feedback error={error} success={success} />
    <button className="secondary" onClick={() => void load()}>Actualizar</button>
    {items.length === 0 ? <p>No hay solicitudes para revisar.</p> : items.map(item => <article className="card" key={item.id}>
      <h2>{item.mascota_nombre} · {item.postulante_nombre}</h2>
      <p>Solicitud #{item.id} · {item.estado} · {new Date(item.fecha_solicitud).toLocaleString('es-PE')}</p>
      {item.mensaje_postulante && <p>Mensaje: {item.mensaje_postulante}</p>}
      {item.observaciones_revision && <p>Último motivo: {item.observaciones_revision}</p>}
      {item.estado === 'APROBADA' && <button disabled={busy !== null} onClick={() => void createContract(item.id)}>Generar y enviar contrato a firma</button>}
      {contracts.filter(c => c.solicitud_id === item.id).map(contract => <div key={contract.public_id}>
        <p>Contrato: {contract.estado_display}{contract.error_integracion && ` · ${contract.error_integracion}`}</p>
        {contract.url_firma && <p><a href={contract.url_firma} target="_blank" rel="noreferrer">Abrir firma electrónica</a></p>}
        {contract.documento_disponible && <button className="secondary" onClick={() => void downloadContract(contract)}>Descargar contrato firmado</button>}
        {contract.estado === 'FIRMADO' && contract.documento_disponible && <button disabled={busy !== null} onClick={() => void registerDelivery(item.id)}>Registrar entrega de mascota</button>}
      </div>)}
      {['PENDIENTE', 'OBSERVADA', 'EN_REVISION'].includes(item.estado) && <>
        <label>Motivo de observación o rechazo<textarea value={reasons[item.id] ?? ''} onChange={e => setReasons(old => ({ ...old, [item.id]: e.target.value }))} /></label>
        <div className="actions">
          <button disabled={busy !== null} onClick={() => void decide(item.id, 'OBSERVADA')}>Observar</button>
          <button disabled={busy !== null} onClick={() => void decide(item.id, 'APROBADA')}>Aprobar</button>
          <button disabled={busy !== null} onClick={() => void decide(item.id, 'RECHAZADA')}>Rechazar</button>
        </div>
      </>}
    </article>)}
  </section>;
}
