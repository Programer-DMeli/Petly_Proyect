import { useEffect, useState, type FormEvent } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import Feedback from '../../../../components/Feedback';
import { django, errorMessage, json } from '../../../../services/http';
import { Hito, Reporte } from '../../../seguimiento/Cronograma';
import { fecha, modalidad, type Evaluacion, type Seguimiento } from '../../../seguimiento/cronogramaDatos';

type Expediente = {
  id: number;
  mascota_nombre: string;
  adoptante_nombre: string;
  adoptante_email: string | null;
  evaluacion: Evaluacion | null;
  cronograma: Seguimiento[];
};
type Pagina = { results: Expediente[]; next: string | null };
type Ejecutar = (operacion: () => Promise<unknown>, mensaje: string) => Promise<boolean>;
const ruta = '/api/contratos/expedientes/';
const actividades = '/api/contratos/seguimientos/';
const secciones = ['Cronograma', 'Reportes', 'Evaluación y cierre'] as const;

function vencimientos(expediente: Expediente) {
  return expediente.cronograma.filter(row => row.estado_entrega === 'VENCIDA');
}

export default function SeguimientosPage() {
  const [params] = useSearchParams();
  const id = params.get('expediente');
  const [pagina, setPagina] = useState(1);
  const [lista, setLista] = useState<Pagina | null>(null);
  const [detalle, setDetalle] = useState<Expediente | null>(null);
  const [version, setVersion] = useState(0);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setError('');
    if (id !== null && !/^[1-9]\d*$/.test(id)) {
      setError('El número de expediente no es válido.');
      setLoading(false);
      return () => controller.abort();
    }
    const init = { signal: controller.signal };
    const consulta = id
      ? django<Expediente>(`${ruta}${encodeURIComponent(id)}/`, init).then(value => {
        if (!controller.signal.aborted) setDetalle(value);
      })
      : django<Pagina>(`${ruta}?page=${pagina}`, init).then(value => {
        if (!controller.signal.aborted) setLista(value);
      });
    void consulta.catch(cause => {
      if (!controller.signal.aborted) setError(errorMessage(cause));
    }).finally(() => {
      if (!controller.signal.aborted) setLoading(false);
    });
    return () => controller.abort();
  }, [id, pagina, version]);

  const ejecutar: Ejecutar = async (operacion, mensaje) => {
    setBusy(true); setError(''); setSuccess('');
    try {
      await operacion();
      if (id) setDetalle(await django<Expediente>(`${ruta}${encodeURIComponent(id)}/`));
      setSuccess(mensaje);
      return true;
    } catch (cause) {
      setError(errorMessage(cause));
      return false;
    } finally {
      setBusy(false);
    }
  };

  return <section className="seguimiento-panel">
    <header className="seguimiento-portada">
      <h1>Seguimiento posadopción</h1>
      <p>Programa visitas y revisa reportes de adaptación</p>
    </header>
    <div className="actions">
      {id && <Link to="/albergue/seguimientos" onClick={() => setSuccess('')}>Volver a expedientes</Link>}
      <button type="button" className="secondary" disabled={busy || loading}
        onClick={() => { setSuccess(''); setVersion(value => value + 1); }}>
        {id ? 'Actualizar expediente' : 'Actualizar expedientes'}
      </button>
    </div>
    <Feedback error={error} success={success} />
    {loading ? <p role="status">Cargando expedientes…</p> : id
      ? detalle && String(detalle.id) === id && <Detalle key={id} expediente={detalle} busy={busy} ejecutar={ejecutar} />
      : lista && <section className="card">
        <h2>Expedientes de adopción</h2>
        {lista.results.length === 0 ? <p>Aún no hay adopciones entregadas para hacer seguimiento.</p> :
          <div className="seguimiento-lista">
            {lista.results.map(expediente => <Link className="seguimiento-expediente"
              key={expediente.id} to={`?expediente=${expediente.id}`}>
              <strong>{expediente.mascota_nombre}</strong>
              <span>{expediente.adoptante_nombre} · Expediente #{expediente.id}</span>
              <span>{expediente.evaluacion?.cerrado_en ? 'Cerrado' :
                vencimientos(expediente).length ? `⚠ Entregas vencidas: ${vencimientos(expediente).length}` : 'Abierto'}</span>
            </Link>)}
          </div>}
        <div className="actions">
          {pagina > 1 && <button type="button" className="secondary" onClick={() => setPagina(pagina - 1)}>Anterior</button>}
          {lista.next && <button type="button" className="secondary" onClick={() => setPagina(pagina + 1)}>Siguiente</button>}
        </div>
      </section>}
  </section>;
}

function Detalle({ expediente, busy, ejecutar }: { expediente: Expediente; busy: boolean; ejecutar: Ejecutar }) {
  const [seccion, setSeccion] = useState<(typeof secciones)[number]>('Cronograma');
  const [editing, setEditing] = useState<Seguimiento | null>(null);
  const { cronograma, evaluacion } = expediente;
  const abierto = !evaluacion?.cerrado_en;
  const reportes = cronograma.filter(row => row.reporte_bienestar);
  const vencidas = vencimientos(expediente);
  const alertasPorEmitir = abierto ? vencidas.filter(row => !row.alerta_incumplimiento?.emitida_en) : [];
  const proximo = abierto && cronograma.find(row => row.estado === 'PROGRAMADO'
    && !row.reporte_bienestar && Date.parse(row.programado_para) > Date.now());

  async function cancelar(row: Seguimiento) {
    if (!window.confirm('¿Cancelar esta actividad del cronograma?')) return;
    if (await ejecutar(() => django(`${actividades}${row.id}/`, { method: 'DELETE' }), 'Actividad cancelada.')) {
      setEditing(null);
    }
  }

  return <section className="card">
    <h2>{expediente.mascota_nombre} · Expediente #{expediente.id}</h2>
    <p>Adoptante: <strong>{expediente.adoptante_nombre}</strong>
      {expediente.adoptante_email && expediente.adoptante_email !== expediente.adoptante_nombre &&
        <> · {expediente.adoptante_email}</>}</p>
    {proximo && <p className="feedback success"><strong>Próximo hito</strong> · {modalidad[proximo.modalidad]} · {fecha(proximo.programado_para)}</p>}
    {vencidas.length > 0 && <p className="feedback error">⚠ Entregas vencidas: {vencidas.length}
      {alertasPorEmitir.length > 0 && <> · Alertas por emitir: {alertasPorEmitir.length}</>}
    </p>}
    {abierto && reportes.length > 0 && !evaluacion && <p>El expediente ya tiene reportes y está pendiente de evaluación.</p>}
    <nav className="seguimiento-secciones" aria-label="Secciones del expediente">
      {secciones.map(label => <button className="secondary" type="button" key={label}
        aria-pressed={label === seccion} onClick={() => setSeccion(label)}>{label}</button>)}
    </nav>
    {seccion === 'Cronograma' && <>
      <h3>Cronograma</h3>
      {abierto && <Programar key={editing?.id ?? 'nueva'} expedienteId={expediente.id} editing={editing} busy={busy}
        ejecutar={ejecutar} limpiar={() => setEditing(null)} />}
      {cronograma.length === 0 ? <p>Este expediente aún no tiene actividades programadas.</p> :
        <ol className="seguimiento-cronograma">{cronograma.map(row => <Hito row={row} key={row.id}>
          <div className="actions">
            {abierto && row.estado === 'PROGRAMADO' && !row.reporte_bienestar && <>
              {!row.alerta_incumplimiento?.emitida_en && <button type="button" className="secondary"
                disabled={busy} onClick={() => setEditing(row)}>Editar</button>}
              <button type="button" className="secondary" disabled={busy} onClick={() => void cancelar(row)}>Cancelar</button>
            </>}
            {abierto && row.estado_entrega === 'VENCIDA' && !row.alerta_incumplimiento?.emitida_en &&
              <button type="button" className="seguimiento-alertar" disabled={busy} onClick={() => {
                if (window.confirm('¿Enviar una alerta roja de incumplimiento al adoptante?')) {
                  void ejecutar(() => django(`${actividades}${row.id}/emitir-incumplimiento/`, {
                    method: 'POST',
                  }), 'Alerta roja emitida.');
                }
              }}>Emitir alerta roja de incumplimiento</button>}
          </div>
          {row.asesorias_veterinarias.map(asesoria => <div key={asesoria.id}>
            <strong>Asesoría veterinaria solicitada · {fecha(asesoria.creado_en)}</strong>
            <p className="seguimiento-texto">{asesoria.problema}</p>
          </div>)}
        </Hito>)}</ol>}
    </>}
    {seccion === 'Reportes' && <section>
      <h3>Reportes recibidos</h3>
      {reportes.length === 0 ? <p>Aún no se han recibido reportes de bienestar.</p> :
        <ol className="seguimiento-cronograma">{reportes.map(row => <Hito key={row.id} row={row}>
          <details>
            <summary>Revisar reporte y fotos</summary>
            <Reporte row={row} />
          </details>
        </Hito>)}</ol>}
    </section>}
    {seccion === 'Evaluación y cierre' && <Evaluar expediente={expediente} primerReporte={reportes[0]}
      busy={busy} ejecutar={ejecutar} />}
  </section>;
}

function Programar({ expedienteId, editing, busy, ejecutar, limpiar }: {
  expedienteId: number; editing: Seguimiento | null; busy: boolean; ejecutar: Ejecutar; limpiar: () => void;
}) {
  const [tipo, setTipo] = useState<Seguimiento['modalidad']>(editing?.modalidad ?? 'VISITA');
  const [dia, setDia] = useState(editing
    ? new Date(Date.parse(editing.programado_para) - 5 * 3600000).toISOString().slice(0, 16) : '');
  const [notas, setNotas] = useState(editing?.notas ?? '');

  async function guardar(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const guardado = await ejecutar(() => django(editing ? `${actividades}${editing.id}/` : actividades, {
      method: editing ? 'PATCH' : 'POST',
      body: json({
        solicitud_id: expedienteId, modalidad: tipo, programado_para: `${dia}:00-05:00`, notas: notas.trim(),
        ...(editing ? {} : { requiere_reporte: tipo === 'REPORTE' }),
      }),
    }), editing ? 'Actividad actualizada.' : 'Actividad añadida al cronograma.');
    if (guardado) { setDia(''); setNotas(''); limpiar(); }
  }

  return <form onSubmit={event => void guardar(event)}>
    <h4>{editing ? 'Editar actividad' : 'Programar actividad'}</h4>
    <label>Tipo de actividad
      <select value={tipo} disabled={Boolean(editing)} onChange={event => setTipo(event.target.value as Seguimiento['modalidad'])}>
        {Object.entries(modalidad).map(([valor, label]) => <option key={valor} value={valor}>{label}</option>)}
      </select>
    </label>
    <label>{tipo === 'REPORTE' ? 'Fecha y hora límite (Lima)' : 'Fecha y hora (Lima)'}
      <input required type="datetime-local" value={dia} onChange={event => setDia(event.target.value)} />
    </label>
    <label>Notas<textarea maxLength={500} value={notas} onChange={event => setNotas(event.target.value)} /></label>
    <div className="actions">
      <button disabled={busy}>{editing ? 'Guardar cambios' : 'Añadir al cronograma'}</button>
      {editing && <button className="secondary" type="button" onClick={limpiar}>Cancelar edición</button>}
    </div>
  </form>;
}

function Evaluar({ expediente, primerReporte, busy, ejecutar }: {
  expediente: Expediente; primerReporte?: Seguimiento; busy: boolean; ejecutar: Ejecutar;
}) {
  const evaluacion = expediente.evaluacion;
  const [rating, setRating] = useState(evaluacion ? String(evaluacion.calificacion_adaptacion) : '');
  const [notas, setNotas] = useState(evaluacion?.observaciones_cierre ?? '');
  const guardada = evaluacion && String(evaluacion.calificacion_adaptacion) === rating
    && evaluacion.observaciones_cierre === notas.trim();

  if (evaluacion?.cerrado_en) return <section>
    <h3>Expediente cerrado</h3>
    <p>Adaptación: <strong>{evaluacion.calificacion_adaptacion}/5</strong> · {fecha(evaluacion.cerrado_en)}</p>
    {evaluacion.observaciones_cierre && <p className="seguimiento-texto">{evaluacion.observaciones_cierre}</p>}
  </section>;

  const destino = `${actividades}${primerReporte?.id}/`;
  return <section>
    <h3>Evaluación y cierre</h3>
    {!primerReporte && <p>La evaluación se habilita cuando el adoptante envíe el primer reporte.</p>}
    <form onSubmit={event => {
      event.preventDefault();
      void ejecutar(() => django(`${destino}evaluar-expediente/`, {
        method: 'POST', body: json({ calificacion_adaptacion: Number(rating), observaciones_cierre: notas.trim() }),
      }), 'Evaluación guardada.');
    }}>
      <fieldset disabled={!primerReporte || busy}>
        <label>Nivel de adaptación
          <select required value={rating} onChange={event => setRating(event.target.value)}>
            <option value="">Selecciona una calificación</option>
            {['Requiere atención', 'Adaptación difícil', 'Adaptación en proceso', 'Buena adaptación', 'Adaptación excelente']
              .map((label, index) => <option value={index + 1} key={label}>{index + 1} · {label}</option>)}
          </select>
        </label>
        <label>Observaciones<textarea maxLength={2000} value={notas} onChange={event => setNotas(event.target.value)} /></label>
        <button disabled={!rating}>Guardar evaluación</button>
      </fieldset>
    </form>
    <button type="button" className="secondary" disabled={!guardada || busy} onClick={() => {
      if (window.confirm(`¿Cerrar formalmente el expediente de ${expediente.mascota_nombre}?`)) {
        void ejecutar(() => django(`${destino}cerrar-expediente/`, {
          method: 'POST', body: json({ confirmar: true }),
        }), 'Expediente cerrado.');
      }
    }}>Cerrar expediente</button>
  </section>;
}
