import { useCallback, useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import Feedback from '../../../../components/Feedback';
import { errorMessage, spring } from '../../../../services/http';
import type { SeguimientoSolicitud } from '../../../../services/types';

const labels: Record<string, string> = { PENDIENTE: 'Pendiente', OBSERVADA: 'Observada', EN_REVISION: 'En revisión', APROBADA: 'Aprobada', RECHAZADA: 'Rechazada', CANCELADA: 'Cancelada', ENTREGADA: 'Entregada' };
export default function SeguimientoSolicitudPage() {
  const { id } = useParams();
  const [data, setData] = useState<SeguimientoSolicitud | null>(null);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [refresh, setRefresh] = useState(0);
  const load = useCallback(async () => {
    const requestId = Number(id);
    if (!Number.isSafeInteger(requestId) || requestId < 1) { setError('Solicitud no válida.'); return; }
    setBusy(true); setError('');
    try { setData(await spring<SeguimientoSolicitud>(`/api/solicitudes/${requestId}/seguimiento`)); }
    catch (e) { setError(errorMessage(e)); }
    finally { setBusy(false); }
  }, [id]);
  useEffect(() => { void load(); }, [load, refresh]);
  return <section className="card">
    <Link to="/adoptante/solicitudes">Volver a mis solicitudes</Link>
    <h1>Seguimiento de solicitud</h1>
    <Feedback error={error} />
    {busy && <p role="status">Cargando eventos…</p>}
    {data && <>
      <p>Solicitud #{data.solicitudId} · Estado actual: <strong>{labels[data.estadoActual] ?? data.estadoActual}</strong></p>
      {data.eventos.length === 0 ? <p>Aún no hay eventos registrados.</p> : <ol className="tracking-timeline">
        {data.eventos.map((event, index) => <li key={`${event.ocurridoEn}-${index}`}>
          <time dateTime={event.ocurridoEn}>{new Date(event.ocurridoEn).toLocaleString('es-PE')}</time>
          <strong> {labels[event.estadoNuevo] ?? event.estadoNuevo}</strong>
          {event.descripcion && <p>{event.descripcion}</p>}
        </li>)}
      </ol>}
    </>}
    <button className="secondary" disabled={busy} onClick={() => setRefresh(v => v + 1)}>Actualizar historial</button>
  </section>;
}
