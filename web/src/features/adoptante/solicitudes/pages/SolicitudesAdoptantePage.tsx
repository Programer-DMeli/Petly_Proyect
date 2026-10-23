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
  const [cancellingId, setCancellingId] = useState<number | null>(null);
  const [refresh, setRefresh] = useState(0);
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
    try { await downloadDjango(`/api/contratos/${contract.public_id}/descargar/`, `contrato-adopcion-${contract.solicitud_id}.pdf`); }
    catch (e) { setError(errorMessage(e)); }
  }
  async function cancelarSolicitud(solicitud: SolicitudAdopcion) {
    const confirmar = window.confirm(
      `¿Deseas cancelar la solicitud de ${solicitud.mascota_nombre || `la solicitud #${solicitud.id}`}? Esta acción no se puede deshacer.`
    );
    if (!confirmar) return;

    setCancellingId(solicitud.id);
    setError('');
    setSuccess('');
    try {
      const actualizada = await spring<SolicitudAdopcion>(
        `/api/solicitudes/${solicitud.id}/cancelar`,
        { method: 'POST' }
      );
      setItems(current => current.map(item => item.id === actualizada.id ? actualizada : item));
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
        {contracts.filter(contract => contract.solicitud_id === item.id).map(contract => <div key={contract.public_id}>
          <p>Contrato: {contract.estado_display}</p>
          {contract.url_firma && <a href={contract.url_firma} target="_blank" rel="noreferrer">Abrir firma electrónica</a>}
          {contract.documento_disponible && <button className="secondary" onClick={() => void downloadContract(contract)}>Descargar contrato firmado</button>}
        </div>)}
        <Link className="button" to={`/adoptante/solicitudes/${item.id}`}>Ver seguimiento</Link>
        {(item.estado === 'PENDIENTE' || item.estado === 'OBSERVADA') && <button
          type="button"
          className="danger"
          disabled={busy || cancellingId !== null}
          onClick={() => void cancelarSolicitud(item)}
        >
          {cancellingId === item.id ? 'Cancelando…' : 'Cancelar solicitud'}
        </button>}
      </article>)}
  </section>;
}
