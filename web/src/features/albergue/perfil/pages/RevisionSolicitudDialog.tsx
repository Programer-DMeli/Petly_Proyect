import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import Feedback from '../../../../components/Feedback';
import FotosHogar from '../../../../components/FotosHogar';
import { django, errorMessage, json } from '../../../../services/http';
import { seccionesFormularioAdopcion } from '../../../../services/formularioAdopcion';
import type { EstadoSolicitudAdopcion, SolicitudAdopcion } from '../../../../services/types';

export type SolicitudAlbergue = SolicitudAdopcion & {
  mascota: number;
  estado_display?: string;
  postulante_nombre: string;
  observaciones_revision: string;
};

type DetalleSolicitud = SolicitudAlbergue & {
  datos_compartidos?: boolean;
  mensaje_postulante?: string;
  respuestas_cuestionario?: Record<string, unknown>;
  estado_revision_veterinaria?: 'PENDIENTE' | 'APTA' | 'OBSERVADA' | 'RECHAZADA';
  observaciones_revision_veterinaria?: string;
  ficha_veterinaria?: Record<string, string | boolean | null> | null;
  fecha_recojo_programado?: string | null;
  entrega_satisfaccion?: number | null;
};

type Decision = Extract<EstadoSolicitudAdopcion, 'APROBADA' | 'RECHAZADA' | 'OBSERVADA'>;

function respuesta(valor: unknown) {
  if (typeof valor === 'boolean') return valor ? 'Sí' : 'No';
  if (typeof valor === 'string' && valor.trim()) return valor;
  return typeof valor === 'number' ? String(valor) : 'Sin responder';
}

function RespuestasAdoptante({ solicitud }: { solicitud: DetalleSolicitud }) {
  if (solicitud.datos_compartidos === false) {
    return <p>La autorización vigente del adoptante es necesaria para consultar sus respuestas.</p>;
  }
  const respuestas = solicitud.respuestas_cuestionario ?? {};
  return (
    <div className="adoption-answers">
      {seccionesFormularioAdopcion.map(({ titulo, campos }) => (
        <section className="adoption-section" key={titulo}>
          <h3>{titulo}</h3>
          <dl className="adoption-fields">
            {campos.map(campo => (
              <div
                key={campo.nombre}
                className={campo.tipo === 'area' ? 'adoption-field--wide' : undefined}
              >
                <dt>{campo.etiqueta}</dt>
                <dd>
                  {respuesta(
                    respuestas[campo.nombre] ??
                      (campo.nombre === 'motivoAdopcion' ? solicitud.mensaje_postulante : undefined)
                  )}
                </dd>
              </div>
            ))}
          </dl>
        </section>
      ))}
    </div>
  );
}

export default function RevisionSolicitudDialog({
  id,
  onCerrar,
  onDecision,
}: {
  id: number;
  onCerrar: () => void;
  onDecision: (solicitud: SolicitudAlbergue) => void;
}) {
  const dialogRef = useRef<HTMLDialogElement>(null);
  const motivoRef = useRef<HTMLTextAreaElement>(null);
  const [solicitud, setSolicitud] = useState<DetalleSolicitud | null>(null);
  const [motivo, setMotivo] = useState('');
  const [error, setError] = useState('');
  const [exito, setExito] = useState('');
  const [intento, setIntento] = useState(0);
  const [guardando, setGuardando] = useState(false);
  const [notaVeterinaria, setNotaVeterinaria] = useState('');
  const [fechaRecojo, setFechaRecojo] = useState('');
  const [mensajeRecojo, setMensajeRecojo] = useState('');
  const [satisfaccion, setSatisfaccion] = useState('5');
  const [notasEntrega, setNotasEntrega] = useState('');
  const [confirmada, setConfirmada] = useState(false);
  const ruta = `/api/acceso/solicitudes/${id}/`;

  useEffect(() => {
    const dialog = dialogRef.current;
    dialog?.showModal();
    return () => dialog?.close();
  }, []);

  useEffect(() => {
    const controller = new AbortController();
    void django<DetalleSolicitud>(ruta, { signal: controller.signal, cache: 'no-store' })
      .then(detalle => {
        if (!controller.signal.aborted) setSolicitud(detalle);
      })
      .catch(cause => {
        if (!controller.signal.aborted) setError(errorMessage(cause));
      });
    return () => controller.abort();
  }, [ruta, intento]);

  async function decidir(estado: Decision) {
    if (guardando) return;
    const observaciones = motivo.trim();
    if (estado !== 'APROBADA' && !observaciones) {
      setError('Escribe el motivo antes de observar o rechazar la solicitud.');
      motivoRef.current?.focus();
      return;
    }
    setGuardando(true);
    setError('');
    setExito('');
    try {
      const actualizada = await django<DetalleSolicitud>(ruta + 'cambiar-estado/', {
        method: 'POST',
        body: json({ estado, observaciones_revision: observaciones }),
      });
      setSolicitud(actualizada);
      setMotivo('');
      setExito('Decisión guardada.');
      onDecision(actualizada);
    } catch (cause) {
      setError(errorMessage(cause));
    } finally {
      setGuardando(false);
    }
  }

  async function actualizarHito(accion: 'revisar-veterinaria' | 'programar-recojo' | 'confirmar-entrega', body: unknown, mensaje: string) {
    if (guardando) return;
    setGuardando(true); setError(''); setExito('');
    try {
      const actualizada = await django<DetalleSolicitud>(ruta + accion + '/', { method: 'POST', body: json(body) });
      setSolicitud(actualizada); setExito(mensaje); onDecision(actualizada);
    } catch (cause) { setError(errorMessage(cause)); }
    finally { setGuardando(false); }
  }

  const revisable = solicitud?.estado === 'PENDIENTE' || solicitud?.estado === 'OBSERVADA';
  function cerrar() {
    if (guardando) return;
    dialogRef.current?.close();
    onCerrar();
  }

  return (
    <dialog
      ref={dialogRef}
      className="adoption-dialog"
      aria-labelledby="revision-solicitud-titulo"
      onCancel={event => {
        event.preventDefault();
        cerrar();
      }}
    >
      <header className="adoption-dialog__header">
        <div>
          <h2 id="revision-solicitud-titulo">Solicitud de adopción</h2>
          {solicitud && (
            <p className="muted">
              {solicitud.mascota_nombre} · {solicitud.postulante_nombre}
            </p>
          )}
        </div>
        <button className="secondary" type="button" disabled={guardando} onClick={cerrar}>
          Cerrar
        </button>
      </header>
      <div className="adoption-dialog__body">
        {!solicitud ? (
          error ? (
            <>
              <Feedback error={error} />
              <button
                type="button"
                onClick={() => {
                  setError('');
                  setIntento(actual => actual + 1);
                }}
              >
                Reintentar
              </button>
            </>
          ) : (
            <p role="status">Cargando la solicitud…</p>
          )
        ) : (
          <>
            <p>
              Estado actual{' '}
              <span className="tracking-badge">{solicitud.estado_display ?? solicitud.estado}</span>
            </p>
            <Feedback error={error} success={exito} />
            {solicitud.observaciones_revision && (
              <p className="adoption-comment">
                <strong>Motivo de la última decisión</strong>
                <br />
                {solicitud.observaciones_revision}
              </p>
            )}
            <h3>Formulario del adoptante</h3>
            <RespuestasAdoptante solicitud={solicitud} />
            {solicitud.estado === 'APROBADA' && <section className="adoption-section">
              <h3>Revisión veterinaria</h3>
              <details>
                <summary>Consultar ficha veterinaria de {solicitud.mascota_nombre}</summary>
                {solicitud.ficha_veterinaria ? <dl className="adoption-fields">
                  {Object.entries(solicitud.ficha_veterinaria).filter(([key]) => key !== 'revisada_en').map(([key, value]) =>
                    <div key={key}><dt>{key.replaceAll('_', ' ')}</dt><dd>{typeof value === 'boolean' ? (value ? 'Sí' : 'No') : (value || 'Sin dato')}</dd></div>)}
                </dl> : <p>No hay datos veterinarios registrados para esta mascota.</p>}
              </details>
              <p>Estado: <strong>{solicitud.estado_revision_veterinaria ?? 'PENDIENTE'}</strong></p>
              {solicitud.observaciones_revision_veterinaria && <p>{solicitud.observaciones_revision_veterinaria}</p>}
              {!solicitud.fecha_recojo_programado && <>
                <label>Observaciones veterinarias<textarea maxLength={1000} value={notaVeterinaria} disabled={guardando} onChange={e => setNotaVeterinaria(e.target.value)} /></label>
                <div className="actions">
                  {(['APTA', 'OBSERVADA', 'RECHAZADA'] as const).map(estado => <button key={estado} type="button" className={estado === 'RECHAZADA' ? 'danger' : 'secondary'} disabled={guardando || (estado !== 'APTA' && !notaVeterinaria.trim())}
                    onClick={() => void actualizarHito('revisar-veterinaria', { estado_revision_veterinaria: estado, observaciones_revision_veterinaria: notaVeterinaria }, `Revisión veterinaria: ${estado.toLowerCase()}.`)}>{estado === 'APTA' ? 'Mascota apta' : estado === 'OBSERVADA' ? 'Observar ficha' : 'Rechazar aptitud'}</button>)}
                </div>
              </>}
            </section>}
            {solicitud.estado === 'APROBADA' && solicitud.estado_revision_veterinaria === 'APTA' && <section className="adoption-section">
              <h3>Recojo</h3>
              {solicitud.fecha_recojo_programado && <p>Programado: {new Date(solicitud.fecha_recojo_programado).toLocaleString('es-PE')}</p>}
              <label>Fecha y hora acordada<input type="datetime-local" value={fechaRecojo} disabled={guardando} onChange={e => setFechaRecojo(e.target.value)} /></label>
              <label>Mensaje para coordinar<textarea maxLength={1000} value={mensajeRecojo} disabled={guardando} onChange={e => setMensajeRecojo(e.target.value)} placeholder="Punto de encuentro e indicaciones" /></label>
              <button type="button" disabled={guardando || !fechaRecojo || !mensajeRecojo.trim()} onClick={() => void actualizarHito('programar-recojo', { fecha_recojo_programado: `${fechaRecojo}:00-05:00`, mensaje_recojo: mensajeRecojo }, 'Recojo programado y mensaje enviado al adoptante.')}>{solicitud.fecha_recojo_programado ? 'Actualizar recojo' : 'Programar recojo'}</button>
            </section>}
            {solicitud.estado === 'APROBADA' && solicitud.estado_revision_veterinaria === 'APTA' && solicitud.fecha_recojo_programado && <section className="adoption-section">
              <h3>Confirmar entrega</h3>
              {solicitud.entrega_satisfaccion ? <p>Entrega registrada · satisfacción {solicitud.entrega_satisfaccion}/5</p> : <>
                <label>Satisfacción con la adopción<select value={satisfaccion} disabled={guardando} onChange={e => setSatisfaccion(e.target.value)}>{[5, 4, 3, 2, 1].map(n => <option key={n} value={n}>{n}/5</option>)}</select></label>
                <label>Comentario breve (opcional)<textarea maxLength={1000} value={notasEntrega} disabled={guardando} onChange={e => setNotasEntrega(e.target.value)} /></label>
                <label><input type="checkbox" checked={confirmada} disabled={guardando} onChange={e => setConfirmada(e.target.checked)} /> Confirmo que la entrega se realizó y la adopción fue exitosa.</label>
                <p className="muted">Se requiere el contrato firmado con su documento final.</p>
                <button type="button" disabled={guardando || !confirmada} onClick={() => void actualizarHito('confirmar-entrega', { confirma_entrega: true, satisfaccion_entrega: Number(satisfaccion), entrega_observaciones: notasEntrega }, 'Entrega confirmada. El expediente ya está disponible en Seguimiento.')}>Confirmar entrega y cerrar adopción</button>
              </>}
            </section>}
            <div className="actions">
              {solicitud.datos_compartidos !== false && <FotosHogar solicitudId={id} />}
              <Link
                className="button secondary"
                to={`/albergue/mensajes?solicitud=${id}&mascota=${solicitud.mascota}`}
              >
                Conversar con el adoptante
              </Link>
            </div>
          </>
        )}
      </div>
      {revisable && (
        <footer className="adoption-dialog__decision">
          <h3>Decisión del albergue</h3>
          <label>
            Motivo de la decisión
            <textarea
              ref={motivoRef}
              maxLength={2000}
              value={motivo}
              disabled={guardando}
              aria-describedby="revision-solicitud-motivo"
              onChange={event => setMotivo(event.target.value)}
            />
          </label>
          <p className="muted" id="revision-solicitud-motivo">
            Obligatorio para observar o rechazar. El adoptante verá el motivo en su seguimiento.
          </p>
          <Feedback error={error} success={exito} />
          <div className="actions">
            <button type="button" disabled={guardando} onClick={() => void decidir('APROBADA')}>
              Aprobar
            </button>
            {solicitud?.estado === 'PENDIENTE' && (
              <button
                type="button"
                className="secondary"
                disabled={guardando}
                onClick={() => void decidir('OBSERVADA')}
              >
                Observar
              </button>
            )}
            <button
              type="button"
              className="danger"
              disabled={guardando}
              onClick={() => void decidir('RECHAZADA')}
            >
              Rechazar
            </button>
          </div>
        </footer>
      )}
    </dialog>
  );
}
