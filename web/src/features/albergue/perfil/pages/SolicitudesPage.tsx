import { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import Feedback from '../../../../components/Feedback';
import { django, errorMessage } from '../../../../services/http';
import RevisionSolicitudDialog from './RevisionSolicitudDialog';
import type { SolicitudAlbergue } from './RevisionSolicitudDialog';

type Pagina = { results: SolicitudAlbergue[]; next?: string | null } | SolicitudAlbergue[];
const SOLICITUDES = '/api/acceso/solicitudes/';

export default function SolicitudesPage() {
  const [params] = useSearchParams();
  const idDestacado = Number(params.get('solicitud'));
  const [rows, setRows] = useState<SolicitudAlbergue[]>([]);
  const [pagina, setPagina] = useState(1);
  const [haySiguiente, setHaySiguiente] = useState(false);
  const [actualizar, setActualizar] = useState(0);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState('');
  const [abierta, setAbierta] = useState<number | null>(null);
  useEffect(() => {
    const controller = new AbortController();
    const options = { signal: controller.signal, cache: 'no-store' } as const;
    async function cargar() {
      setCargando(true);
      setError('');
      try {
        const datos = await django<Pagina>(`${SOLICITUDES}?vista=resumen&page=${pagina}`, options);
        if (controller.signal.aborted) return;
        const solicitudes = Array.isArray(datos) ? datos : datos.results;
        setRows(solicitudes);
        setHaySiguiente(!Array.isArray(datos) && Boolean(datos.next));

        if (
          pagina === 1 &&
          Number.isSafeInteger(idDestacado) &&
          idDestacado > 0 &&
          !solicitudes.some(item => item.id === idDestacado)
        ) {
          const destacada = await django<SolicitudAlbergue>(
            `${SOLICITUDES}${idDestacado}/?vista=resumen`,
            options
          );
          if (!controller.signal.aborted) setRows([destacada, ...solicitudes]);
        }
      } catch (cause) {
        if (!controller.signal.aborted) setError(errorMessage(cause));
      } finally {
        if (!controller.signal.aborted) setCargando(false);
      }
    }
    void cargar();
    return () => controller.abort();
  }, [idDestacado, pagina, actualizar]);

  useEffect(() => {
    if (!cargando && pagina === 1 && idDestacado > 0) {
      document.getElementById(`solicitud-${idDestacado}`)?.scrollIntoView({ block: 'nearest' });
    }
  }, [cargando, idDestacado, pagina]);

  function actualizarFila(solicitud: SolicitudAlbergue) {
    setRows(actuales =>
      actuales.map(item =>
        item.id === solicitud.id
          ? {
              ...item,
              estado: solicitud.estado,
              estado_display: solicitud.estado_display,
              observaciones_revision: solicitud.observaciones_revision,
            }
          : item
      )
    );
  }

  return (
    <section className="card">
      <header className="adoption-list-header">
        <h1>Solicitudes de adopción</h1>
        <button
          className="secondary"
          type="button"
          disabled={cargando}
          onClick={() => setActualizar(actual => actual + 1)}
        >
          Actualizar
        </button>
      </header>
      <Feedback error={error} />
      {cargando && <p role="status">Cargando solicitudes…</p>}
      {!cargando && !error && rows.length === 0 && <p>No hay solicitudes para revisar.</p>}
      {!cargando && (
        <div className="adoption-list">
          {rows.map(row => (
            <article className="adoption-list-row" id={`solicitud-${row.id}`} key={row.id}>
              <div>
                <h2>{row.mascota_nombre}</h2>
                <p className="muted">
                  {row.postulante_nombre} ·{' '}
                  {new Date(row.fecha_solicitud).toLocaleDateString('es-PE')}
                </p>
                <span className="tracking-badge">{row.estado_display ?? row.estado}</span>
              </div>
              <button type="button" onClick={() => setAbierta(row.id)}>
                Ver solicitud de adopción
              </button>
            </article>
          ))}
        </div>
      )}
      {(pagina > 1 || haySiguiente) && (
        <nav className="actions" aria-label="Páginas de solicitudes">
          <button
            type="button"
            className="secondary"
            disabled={cargando || pagina === 1}
            onClick={() => setPagina(actual => actual - 1)}
          >
            Anterior
          </button>
          <span>Página {pagina}</span>
          <button
            type="button"
            className="secondary"
            disabled={cargando || !haySiguiente}
            onClick={() => setPagina(actual => actual + 1)}
          >
            Siguiente
          </button>
        </nav>
      )}
      {abierta !== null && (
        <RevisionSolicitudDialog
          key={abierta}
          id={abierta}
          onCerrar={() => setAbierta(null)}
          onDecision={actualizarFila}
        />
      )}
    </section>
  );
}
