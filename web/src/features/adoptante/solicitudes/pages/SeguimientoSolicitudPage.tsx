import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { errorMessage, spring } from '../../../../services/http';
import type { SeguimientoSolicitud } from '../../../../services/types';

export default function SeguimientoSolicitudPage() {
  const { solicitudId } = useParams();
  const [seguimiento, setSeguimiento] = useState<SeguimientoSolicitud | null>(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const controller = new AbortController();
    const id = Number(solicitudId);

    if (!Number.isSafeInteger(id) || id < 1) {
      setError('El número de solicitud no es válido.');
      setLoading(false);
      return () => controller.abort();
    }

    setLoading(true);
    setError('');
    void spring<SeguimientoSolicitud>(`/api/solicitudes/${id}/seguimiento`, {
      signal: controller.signal,
    })
      .then(result => {
        if (!controller.signal.aborted) setSeguimiento(result);
      })
      .catch((reason: unknown) => {
        if (!controller.signal.aborted) setError(errorMessage(reason));
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });

    return () => controller.abort();
  }, [solicitudId]);

  return (
    <section className="card">
      <Link to="/adoptante/catalogo">Volver al catálogo</Link>
      <h1>Seguimiento de solicitud</h1>
      {loading && <p role="status">Cargando el historial de tu solicitud…</p>}
      {error && <p className="feedback error" role="alert">{error}</p>}
      {seguimiento && (
        <>
          <p>Solicitud #{seguimiento.solicitudId}</p>
          <p>Estado actual: <strong>{seguimiento.estadoActual}</strong></p>
          <h2>Historial de cambios</h2>
          <ol>
            {seguimiento.eventos.map((evento, index) => (
              <li key={`${evento.fecha}-${evento.estado}-${index}`}>
                <strong>{evento.estado}</strong> · {evento.fecha}
                {evento.motivo && <p>{evento.motivo}</p>}
              </li>
            ))}
          </ol>
        </>
      )}
    </section>
  );
}
