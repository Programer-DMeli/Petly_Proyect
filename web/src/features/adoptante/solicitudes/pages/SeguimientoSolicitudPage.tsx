import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { errorMessage, spring } from '../../../../services/http';
import type { SeguimientoSolicitud } from '../../../../services/types';

const estadoLabel: Record<string, string> = {
  PENDIENTE: 'Pendiente',
  OBSERVADA: 'Observada',
  APROBADA: 'Aprobada',
  RECHAZADA: 'Rechazada',
  CANCELADA: 'Cancelada',
  ENTREGADA: 'Entregada',
};

function formatDate(value: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat('es-PE', {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(date);
}

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
          <p className="muted">Solicitud #{seguimiento.solicitudId}</p>
          <p className="tracking-current">
            Estado actual{' '}
            <span className="tracking-badge">
              {estadoLabel[seguimiento.estadoActual] ?? seguimiento.estadoActual}
            </span>
          </p>
          <h2>Historial de cambios</h2>
          <ol className="tracking-timeline">
            {[...seguimiento.eventos]
              .sort((first, second) => Date.parse(first.fecha) - Date.parse(second.fecha))
              .map((evento, index, eventos) => (
                <li
                  className={`tracking-timeline__event${index === eventos.length - 1 ? ' tracking-timeline__event--latest' : ''}`}
                  key={`${evento.fecha}-${evento.estado}-${index}`}
                >
                  <span className="tracking-timeline__marker" aria-hidden="true" />
                  <div>
                    <p className="tracking-timeline__date">{formatDate(evento.fecha)}</p>
                    <strong>{estadoLabel[evento.estado] ?? evento.estado}</strong>
                    {evento.motivo && <p className="tracking-timeline__reason">{evento.motivo}</p>}
                  </div>
                </li>
              ))}
          </ol>
        </>
      )}
    </section>
  );
}
