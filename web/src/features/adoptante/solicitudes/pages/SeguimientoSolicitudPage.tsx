import FotosHogar from '../../../../components/FotosHogar';
import { useEffect, useState } from 'react';
import { Link, useLocation, useParams } from 'react-router-dom';
import Feedback from '../../../../components/Feedback';
import { errorMessage, spring } from '../../../../services/http';
import { avisosConectados, observarAvisos } from '../../../../services/avisosEnVivo';
import { INTERVALO_AVISOS_MS } from '../../../../services/notificacionesApi';
import type { SeguimientoSolicitud } from '../../../../services/types';
import OpinionesAlbergue from '../../../shared/OpinionesAlbergue';

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
  const state = useLocation().state as { recienEnviada?: boolean } | null;
  const [seguimiento, setSeguimiento] = useState<SeguimientoSolicitud | null>(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);
  const [refresh, setRefresh] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    const id = Number(solicitudId);

    if (!Number.isSafeInteger(id) || id < 1) {
      setSeguimiento(null);
      setError('El número de solicitud no es válido.');
      setLoading(false);
      return () => controller.abort();
    }

    setLoading(true);
    setError('');
    setSeguimiento(actual => actual?.solicitudId === id ? actual : null);

    let pendiente = false;
    let refrescarDespues = false;
    async function consultar() {
      if (pendiente) {
        refrescarDespues = true;
        return;
      }
      pendiente = true;
      try {
        const datos = await spring<SeguimientoSolicitud>(`/api/solicitudes/${id}/seguimiento`, {
          signal: controller.signal,
          cache: 'no-store',
        });
        if (!controller.signal.aborted) {
          setSeguimiento(datos);
          setError('');
        }
      } catch (reason) {
        if (!controller.signal.aborted) setError(errorMessage(reason));
      } finally {
        pendiente = false;
        if (!controller.signal.aborted) {
          setLoading(false);
          if (refrescarDespues) {
            refrescarDespues = false;
            void consultar();
          }
        }
      }
    }

    const actualizar = () => { if (!document.hidden && !avisosConectados()) void consultar(); };
    const dejarDeObservar = observarAvisos(evento => {
      if (evento.tipo === 'sincronizar' || evento.solicitudes.includes(id)) void consultar();
    });
    void consultar();
    const timer = window.setInterval(actualizar, INTERVALO_AVISOS_MS);
    const alVolver = () => { if (!document.hidden) void consultar(); };
    document.addEventListener('visibilitychange', alVolver);
    return () => {
      controller.abort();
      dejarDeObservar();
      window.clearInterval(timer);
      document.removeEventListener('visibilitychange', alVolver);
    };
  }, [solicitudId, refresh]);

  return (
    <section className="card">
      <Link to="/adoptante/solicitudes">Volver a Mis solicitudes</Link>
      <h1>Seguimiento de solicitud</h1>
      {state?.recienEnviada === true && <Feedback success="Solicitud enviada al albergue." />}
      {loading && <p role="status">Cargando el historial de tu solicitud…</p>}
      {error && (
        <>
          <p className="feedback error" role="alert">{error}</p>
          <button type="button" onClick={() => setRefresh(value => value + 1)}>
            Reintentar
          </button>
        </>
      )}
      {seguimiento && (
        <>
          <p className="muted">Solicitud #{seguimiento.solicitudId}</p>
          <p className="tracking-current">
            Estado actual{' '}
            <span className="tracking-badge" aria-live="polite">
              {estadoLabel[seguimiento.estadoActual] ?? seguimiento.estadoActual}
            </span>
          </p>
          {seguimiento.estadoActual === 'ENTREGADA' && (
            <div className="feedback success">
              <strong>{seguimiento.cerradoEn ? 'Cierre de adopción validado' : 'Expediente de adopción abierto'}</strong>
              {' · '}{seguimiento.cerradoEn ? formatDate(seguimiento.cerradoEn) : 'La entrega fue confirmada.'}
              {' '}<Link to={`/adoptante/seguimientos-postadopcion?solicitud=${seguimiento.solicitudId}`}>Ver cronograma posadopción</Link>
            </div>
          )}
          {seguimiento.estadoActual === 'ENTREGADA' && seguimiento.cerradoEn && seguimiento.albergueId > 0 &&
            <OpinionesAlbergue key={seguimiento.albergueId} albergueId={seguimiento.albergueId} />}
          <FotosHogar solicitudId={seguimiento.solicitudId}/>
          <h2>Historial de cambios</h2>
          {seguimiento.eventos.length === 0 ? (
            <p className="feedback tracking-empty">
              Aún no hay eventos registrados para esta solicitud. Vuelve a consultar más tarde para ver si el albergue actualizó su estado.
            </p>
          ) : (
            <ol className="tracking-timeline">
              {[...seguimiento.eventos]
                .sort((first, second) => Date.parse(first.ocurrido_en) - Date.parse(second.ocurrido_en))
                .map((evento, index, eventos) => (
                  <li
                    className={`tracking-timeline__event${index === eventos.length - 1 ? ' tracking-timeline__event--latest' : ''}`}
                    key={`${evento.id}-${index}`}
                  >
                    <span className="tracking-timeline__marker" aria-hidden="true" />
                    <div>
                      <p className="tracking-timeline__date">{formatDate(evento.ocurrido_en)}</p>
                      <strong>{evento.tipo_display || estadoLabel[evento.estado_nuevo ?? ''] || evento.tipo}</strong>
                      {evento.descripcion && <p className="tracking-timeline__reason">{evento.descripcion}</p>}
                    </div>
                  </li>
                ))}
            </ol>
          )}
          <div className="actions">
            <button
              type="button"
              className="secondary"
              disabled={loading}
              onClick={() => setRefresh(value => value + 1)}
            >
              Actualizar historial
            </button>
          </div>
        </>
      )}
    </section>
  );
}
