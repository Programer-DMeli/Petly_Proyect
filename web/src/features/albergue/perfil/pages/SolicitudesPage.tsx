import { useCallback, useEffect, useState } from 'react';
import Feedback from '../../../../components/Feedback';
import { django, json, errorMessage } from '../../../../services/http';

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
  const [reasons, setReasons] = useState<Record<number, string>>({});
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [busy, setBusy] = useState<number | null>(null);
  const load = useCallback(async () => {
    try {
      const response = await django<Page<Solicitud>>('/api/acceso/solicitudes/');
      setRows(Array.isArray(response) ? response : response.results);
      setError('');
    } catch (e) { setError(errorMessage(e)); }
  }, []);
  useEffect(() => { void load(); }, [load]);

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
