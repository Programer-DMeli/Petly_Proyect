import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import Feedback from '../../../../components/Feedback';
import { errorMessage, spring, django, downloadDjango } from '../../../../services/http';
import type { ContratoAdopcion, SolicitudAdopcion } from '../../../../services/types';

type Page<T> = T[] | { results: T[] };

export default function SolicitudesAdoptantePage() {
  const [items, setItems] = useState<SolicitudAdopcion[]>([]);
  const [contracts, setContracts] = useState<ContratoAdopcion[]>([]);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [busy, setBusy] = useState(true);
  const [refresh, setRefresh] = useState(0);
  const [cancellingId, setCancellingId] = useState<number | null>(null);
  const load = useCallback(async () => {
    setBusy(true); setError(''); setSuccess('');
    try {
      const [result, contractResult] = await Promise.all([
        spring<Page<SolicitudAdopcion>>('/api/solicitudes'), django<Page<ContratoAdopcion>>('/api/contratos/'),
      ]);
      setItems(Array.isArray(result) ? result : result.results);
      setContracts(Array.isArray(contractResult) ? contractResult : contractResult.results);
    } catch (e) { setError(errorMessage(e)); }
    finally { setBusy(false); }
  }, []);
  useEffect(() => { void load(); }, [load, refresh]);
  async function downloadContract(contract: ContratoAdopcion) {
    const extension = contract.proveedor === 'email-approval' ? 'html' : 'pdf';
    try { await downloadDjango(`/api/contratos/${contract.public_id}/descargar/`, `contrato-adopcion-${contract.solicitud_id}.${extension}`); }
    catch (e) { setError(errorMessage(e)); }
  }
  async function cancelRequest(item: SolicitudAdopcion) {
    const confirmar = window.confirm(
      `¿Deseas cancelar la solicitud de ${item.mascota_nombre || `la solicitud #${item.id}`}? Esta acción no se puede deshacer.`
    );
    if (!confirmar) return;
    setCancellingId(item.id); setError(''); setSuccess('');
    try {
      const actualizada = await spring<SolicitudAdopcion>(`/api/solicitudes/${item.id}/cancelar`, { method: 'POST' });
      setItems(current => current.map(currentItem => currentItem.id === actualizada.id ? actualizada : currentItem));
      setSuccess(`La solicitud #${actualizada.id} quedó cancelada.`);
    } catch (e) {
      setError(`${errorMessage(e)} Actualiza la lista para consultar el estado actual.`);
    } finally {
      setCancellingId(null);
    }
  }
  return <section className="card">
    <h1>Mis solicitudes</h1>
    <Feedback error={error} success={success} />
    <button className="secondary" disabled={busy} onClick={() => setRefresh(v => v + 1)}>Actualizar</button>
    {busy ? <p role="status">Cargando solicitudes…</p> : items.length === 0
      ? <p>Aún no has enviado solicitudes.</p>
      : items.map(item => <article className="card" key={item.id}>
        <h2>{item.mascota_nombre || `Solicitud #${item.id}`}</h2>
        <p>Estado: {item.estado} · {new Date(item.fecha_solicitud).toLocaleString('es-PE')}</p>
        {['PENDIENTE', 'OBSERVADA'].includes(item.estado) && <button
          type="button" className="danger" disabled={cancellingId !== null}
          onClick={() => void cancelRequest(item)}
        >{cancellingId === item.id ? 'Cancelando…' : 'Cancelar solicitud'}</button>}
        {contracts.filter(contract => contract.solicitud_id === item.id).map(contract => <div key={contract.public_id}>
          <p>Contrato: {contract.estado_display}</p>
          {contract.url_firma && <a href={contract.url_firma} target="_blank" rel="noreferrer">Abrir firma electrónica</a>}
          {contract.documento_disponible && <button className="secondary" onClick={() => void downloadContract(contract)}>
            {contract.proveedor === 'email-approval' ? 'Descargar constancia de aprobación' : 'Descargar contrato firmado'}
          </button>}
        </div>)}
        <Link className="button" to={`/adoptante/solicitudes/${item.id}`}>Ver seguimiento</Link>
      </article>)}
  </section>;
}
