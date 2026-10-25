import { useCallback, useEffect, useState } from 'react';
import Feedback from '../../../../components/Feedback';
import { django, downloadDjango, errorMessage, json } from '../../../../services/http';

interface Kyc {
  id: number; dni: string; nombres: string; apellidos: string; estado: string;
  coincide: boolean | null; proveedor: string; referencia_reniec: string;
  fecha_consulta: string | null; error_servicio: string; motivo_revision: string;
}
interface Page<T> { results: T[] }

export default function VerificacionesKycAdminPage() {
  const [items, setItems] = useState<Kyc[]>([]);
  const [reasons, setReasons] = useState<Record<number, string>>({});
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [busy, setBusy] = useState(false);
  const load = useCallback(async () => {
    setError(''); setBusy(true);
    try {
      const page = await django<Page<Kyc> | Kyc[]>('/api/acceso/verificaciones-kyc/');
      setItems(Array.isArray(page) ? page : page.results);
    } catch (e) { setError(errorMessage(e)); }
    finally { setBusy(false); }
  }, []);
  useEffect(() => { void load(); }, [load]);

  async function decide(item: Kyc, decision: 'APROBADO_MANUAL' | 'RECHAZADO_MANUAL') {
    const motivo = (reasons[item.id] ?? '').trim();
    if (!motivo) { setError('Escribe el motivo de la decisión antes de continuar.'); return; }
    setBusy(true); setError(''); setSuccess('');
    try {
      await django(`/api/acceso/verificaciones-kyc/${item.id}/revision/`, { method: 'POST', body: json({ decision, motivo }) });
      setSuccess(`Caso ${item.dni} actualizado: ${decision === 'APROBADO_MANUAL' ? 'aprobado' : 'rechazado'} manualmente.`);
      await load();
    } catch (e) { setError(errorMessage(e)); }
    finally { setBusy(false); }
  }

  async function download(item: Kyc, side: 'anverso' | 'reverso') {
    setError('');
    try {
      await downloadDjango(`/api/acceso/verificaciones-kyc/${item.id}/documentos/${side}/`, `dni-${item.dni}-${side}`);
    } catch (e) { setError(errorMessage(e)); }
  }

  return <section className="card">
    <h1>Revisión de identidad</h1>
    <p className="muted">Solo los casos pendientes de revisión requieren decisión manual. La revisión no se registra como consulta automática de RENIEC.</p>
    <Feedback error={error} success={success} />
    <button type="button" className="secondary" onClick={() => void load()} disabled={busy}>Actualizar lista</button>
    {items.length === 0 && !busy ? <p>No hay verificaciones registradas.</p> : <div className="admin-kyc-list">
      {items.map(item => <article className="card" key={item.id}>
        <h2>{item.nombres} {item.apellidos}</h2>
        <p>DNI: {item.dni} · Estado: {item.estado.replaceAll('_', ' ')}</p>
        <p>RENIEC: {item.coincide === null ? 'Sin resultado' : item.coincide ? 'Coincide' : 'No coincide'}{item.proveedor ? ` · ${item.proveedor}` : ''}</p>
        {item.fecha_consulta && <p>Consulta: {new Date(item.fecha_consulta).toLocaleString('es-PE')} · Ref.: {item.referencia_reniec}</p>}
        {item.error_servicio && <p role="alert">Error de servicio: {item.error_servicio}</p>}
        <div className="actions">
          <button type="button" className="secondary" onClick={() => void download(item, 'anverso')}>Descargar anverso</button>
          <button type="button" className="secondary" onClick={() => void download(item, 'reverso')}>Descargar reverso</button>
        </div>
        {item.estado === 'PENDIENTE_REVISION' && <>
          <label>Motivo de la decisión<textarea maxLength={500} required value={reasons[item.id] ?? ''} onChange={e => setReasons(current => ({ ...current, [item.id]: e.target.value }))} /></label>
          <div className="actions">
            <button type="button" onClick={() => void decide(item, 'APROBADO_MANUAL')} disabled={busy}>Aprobar manualmente</button>
            <button type="button" className="secondary" onClick={() => void decide(item, 'RECHAZADO_MANUAL')} disabled={busy}>Rechazar</button>
          </div>
        </>}
        {item.motivo_revision && <p>Decisión manual: {item.motivo_revision}</p>}
      </article>)}
    </div>}
  </section>;
}
