import { useCallback, useEffect, useState, type FormEvent } from 'react';
import Feedback from '../../../../components/Feedback';
import { Link, useSearchParams } from 'react-router-dom';
import { Hito, Reporte } from '../../../seguimiento/Cronograma';
import { fecha, type Bienestar, type Seguimiento } from '../../../seguimiento/cronogramaDatos';
import { django, errorMessage, json } from '../../../../services/http';
import { useMaxImagenMb } from '../../../../services/useMaxImagenMb';

type Asesoria = Seguimiento['asesorias_veterinarias'][number];
type Pagina = { results: Seguimiento[]; next: string | null };

const TAMANO_PAGINA = 6;
const MAX_PHOTOS = 5;
const MAX_TOTAL_BYTES = 20 * 1024 * 1024;

export default function ReportesPostAdopcionPage() {
  const maxImagenMb = useMaxImagenMb();
  const [params, setParams] = useSearchParams();
  const solicitud = params.get('solicitud');
  const [pagina, setPagina] = useState(0);
  const [ocultando, setOcultando] = useState<number | null>(null);
  const [rows, setRows] = useState<Seguimiento[]>([]);
  const activeId = Number(params.get('entrega'));
  const programacionId = Number(params.get('seguimiento'));
  const objetivoId = activeId || programacionId;
  const setActiveId = (id: number | null) => {
    const next = new URLSearchParams(params);
    if (id === null) next.delete('entrega'); else next.set('entrega', String(id));
    setParams(next, { replace: true });
  };
  const [activeAsesoriaId, setActiveAsesoriaId] = useState<number | null>(null);
  const [problemaAsesoria, setProblemaAsesoria] = useState('');
  const [bienestar, setBienestar] = useState<Bienestar>('BUENO');
  const [observaciones, setObservaciones] = useState('');
  const [fotos, setFotos] = useState<File[]>([]);
  const [busy, setBusy] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  const load = useCallback(async () => {
    try {
      let page = 1;
      let next: string | null;
      const all: Seguimiento[] = [];
      do {
        const result = await django<Pagina | Seguimiento[]>(`/api/contratos/seguimientos/?page=${page++}`);
        all.push(...(Array.isArray(result) ? result : result.results));
        next = Array.isArray(result) ? null : result.next;
      } while (next);
      setRows(all);
      setError('');
    } catch (cause) {
      setError(errorMessage(cause));
    } finally { setLoading(false); }
  }, []);
  useEffect(() => { setLoading(true); void load(); }, [load, programacionId]);
  useEffect(() => {
    setFotos([]);
    setObservaciones('');
    setBienestar('BUENO');
  }, [activeId]);

  function selectPhotos(list: FileList | null) {
    const selected = Array.from(list ?? []);
    const total = selected.reduce((sum, file) => sum + file.size, 0);
    if (selected.length > MAX_PHOTOS) { setError('Puedes adjuntar hasta 5 fotos por reporte.'); setFotos([]); return; }
    if (selected.some(file => !['image/jpeg', 'image/png'].includes(file.type) || file.size === 0 || file.size > maxImagenMb * 1024 * 1024)) {
      setError(`Cada foto debe ser JPEG o PNG y pesar hasta ${maxImagenMb} MB.`); setFotos([]); return;
    }
    if (total > MAX_TOTAL_BYTES) { setError('Las fotos juntas no pueden superar 20 MB.'); setFotos([]); return; }
    setError(''); setFotos(selected);
  }

  async function submit(event: FormEvent<HTMLFormElement>, row: Seguimiento) {
    event.preventDefault();
    if (fotos.length === 0) { setError('Adjunta al menos una foto.'); return; }
    if (!window.confirm(`¿Enviar el reporte de bienestar de ${row.mascota_nombre}?`)) return;
    setBusy(true); setError(''); setSuccess('');
    try {
      const body = new FormData();
      body.append('seguimiento_id', String(row.id));
      body.append('estado_bienestar', bienestar);
      body.append('observaciones', observaciones.trim());
      fotos.forEach(file => body.append('fotos', file));
      await django('/api/contratos/reportes-seguimiento/', { method: 'POST', body });
      setActiveId(null); setFotos([]); setBienestar('BUENO'); setObservaciones('');
      setSuccess('Reporte enviado al albergue.'); await load();
    } catch (cause) { setError(errorMessage(cause)); }
    finally { setBusy(false); }
  }

  async function solicitarAsesoria(event: FormEvent<HTMLFormElement>, row: Seguimiento) {
    event.preventDefault();
    const problema = problemaAsesoria.trim();
    if (!problema) { setError('Describe el problema de adaptación para solicitar asesoría.'); return; }
    setBusy(true); setError(''); setSuccess('');
    try {
      await django('/api/contratos/asesorias-veterinarias/', {
        method: 'POST',
        body: json({ seguimiento: row.id, problema }),
      });
      setActiveAsesoriaId(null); setProblemaAsesoria('');
      setSuccess(`Solicitud de asesoría gratuita enviada al albergue de ${row.mascota_nombre}.`);
      await load();
    } catch (cause) { setError(errorMessage(cause)); }
    finally { setBusy(false); }
  }

  async function ocultar(row: Seguimiento) {
    if (!window.confirm(`¿Eliminar la entrega de ${row.mascota_nombre} de tu cronograma?`)) return;
    setOcultando(row.id); setError(''); setSuccess('');
    try {
      await django(`/api/contratos/seguimientos/${row.id}/ocultar/`, { method: 'POST' });
      setRows(current => current.filter(item => item.id !== row.id));
      if (objetivoId === row.id) limpiarEnlace();
      if (activeAsesoriaId === row.id) setActiveAsesoriaId(null);
      setSuccess('Entrega eliminada de tu cronograma.');
    } catch (cause) {
      setError(errorMessage(cause));
    } finally {
      setOcultando(null);
    }
  }

  const visibles = solicitud ? rows.filter(row => String(row.solicitud_id) === solicitud) : rows;
  const totalPaginas = Math.ceil(visibles.length / TAMANO_PAGINA);
  const indiceEntrega = objetivoId ? visibles.findIndex(row => row.id === objetivoId) : -1;
  const paginaActual = indiceEntrega >= 0 ? Math.floor(indiceEntrega / TAMANO_PAGINA)
    : Math.min(pagina, Math.max(0, totalPaginas - 1));
  const tarjetas = visibles.slice(paginaActual * TAMANO_PAGINA, (paginaActual + 1) * TAMANO_PAGINA);

  useEffect(() => {
    if (!loading && objetivoId) document.getElementById(`seguimiento-${objetivoId}`)?.scrollIntoView({ block: 'start' });
  }, [objetivoId, loading, paginaActual]);

  function limpiarEnlace() {
    const next = new URLSearchParams(params);
    next.delete('entrega');
    next.delete('seguimiento');
    setParams(next, { replace: true });
  }

  function cambiarPagina(nueva: number) {
    setPagina(nueva);
    if (objetivoId) limpiarEnlace();
  }

  const entregaNoDisponible = !loading && !error && objetivoId > 0 && indiceEntrega < 0;
  return <section className="card seguimiento-panel">
    <header className="seguimiento-portada">
      <h1>Cronograma posadopción</h1>
      <p>Consulta tus visitas, llamadas y entregas de reportes de bienestar.</p>
    </header>
    <div className="actions">
      {solicitud && <Link to="/adoptante/seguimientos-postadopcion">Ver todos mis seguimientos</Link>}
      <button type="button" className="secondary" disabled={loading || busy || ocultando !== null}
        onClick={() => { setLoading(true); void load(); }}>Actualizar cronograma</button>
    </div>
    <Feedback error={error || (entregaNoDisponible ? 'Esta entrega no está disponible.' : '')} success={success} />
    {loading ? <p role="status">Cargando tu cronograma…</p> : error && rows.length === 0 ? <button type="button" onClick={() => void load()}>Reintentar</button>
      : visibles.length === 0 ? <p>Aún no tienes seguimientos postadopción programados.</p>
      : <ol className="seguimiento-cronograma">{tarjetas.map(row => <Hito row={row} key={row.id} adoptante>
        <strong>{row.mascota_nombre}</strong> · Solicitud #{row.solicitud_id} · {row.albergue_nombre}
        <section aria-labelledby={`asesorias-title-${row.id}`}>
          <h3 id={`asesorias-title-${row.id}`}>Asesoría técnica o veterinaria gratuita</h3>
          {row.asesorias_veterinarias?.length > 0 && <ul>
            {row.asesorias_veterinarias.map(asesoria => <li key={asesoria.id}>
              <strong>{estadoAsesoria(asesoria.estado)}</strong> · {fecha(asesoria.creado_en)}
              <p style={{ whiteSpace: 'pre-wrap' }}>{asesoria.problema}</p>
            </li>)}
          </ul>}
          {row.estado !== 'CANCELADO' && !row.expediente_cierre && <>
            {activeAsesoriaId === row.id ? <form onSubmit={event => void solicitarAsesoria(event, row)}>
              <label htmlFor={`problema-asesoria-${row.id}`}>¿Qué dificultad tienes durante la adaptación?</label>
              <textarea id={`problema-asesoria-${row.id}`} required maxLength={2000} value={problemaAsesoria}
                onChange={event => setProblemaAsesoria(event.target.value)}
                placeholder="Describe qué está ocurriendo y desde cuándo." />
              <p className="muted">La solicitud se enviará al albergue responsable de este seguimiento. No tiene costo.</p>
              <button disabled={busy || !problemaAsesoria.trim()}>Enviar solicitud de asesoría</button>
              <button type="button" className="secondary" disabled={busy} onClick={() => {
                setActiveAsesoriaId(null); setProblemaAsesoria(''); setError('');
              }}>Cancelar</button>
            </form> : <button type="button" className="secondary" disabled={busy} onClick={() => {
              setActiveAsesoriaId(row.id); setProblemaAsesoria(''); setError(''); setSuccess('');
            }}>Solicitar asesoría gratuita</button>}
          </>}
        </section>
        {row.reporte_bienestar ? <Reporte row={row} adoptante /> :
          (!row.expediente_cierre && (row.estado_entrega === 'PENDIENTE' || row.estado_entrega === 'VENCIDA')) && <>
          {activeId === row.id ? <form key={row.id} onSubmit={event => void submit(event, row)}>
            <h3>Reporte de {row.mascota_nombre}</h3>
            <p>Fecha límite: {fecha(row.programado_para)} (Lima)</p>
            <label>Estado de bienestar
              <select value={bienestar} onChange={event => setBienestar(event.target.value as Bienestar)}>
                <option value="EXCELENTE">Excelente</option>
                <option value="BUENO">Bueno</option>
                <option value="REGULAR">Requiere atención</option>
                <option value="PREOCUPANTE">Preocupante</option>
              </select>
            </label>
            <label>Observaciones<textarea maxLength={2000} value={observaciones}
              onChange={event => setObservaciones(event.target.value)}
              placeholder="Cuéntale al albergue cómo se está adaptando." /></label>
            <label>Fotos JPEG o PNG, hasta 5 fotos de {maxImagenMb} MB cada una
              <input type="file" accept="image/jpeg,image/png" multiple required
                onChange={event => selectPhotos(event.currentTarget.files)} />
            </label>
            {fotos.length > 0 && <p>{fotos.map(file => file.name).join(' · ')}</p>}
            <button disabled={busy || fotos.length === 0}>Enviar reporte</button>
            <button type="button" className="secondary" disabled={busy} onClick={() => {
              setActiveId(null); setFotos([]); setObservaciones(''); setError('');
            }}>Cancelar</button>
          </form> : <button type="button" className="secondary" onClick={() => {
            setActiveId(row.id); setFotos([]); setObservaciones(''); setBienestar('BUENO'); setError(''); setSuccess('');
          }}>Preparar reporte</button>}
        </>}
        {row.estado_entrega && <button type="button" className="secondary" disabled={busy || ocultando !== null}
          onClick={() => void ocultar(row)}>{ocultando === row.id ? 'Eliminando…' : 'Eliminar'}</button>}
      </Hito>)}</ol>}
    {totalPaginas > 1 && <nav className="actions" aria-label="Páginas del cronograma">
      <button className="secondary" type="button" disabled={paginaActual === 0} onClick={() => cambiarPagina(paginaActual - 1)}>Anterior</button>
      <span>Página {paginaActual + 1} de {totalPaginas}</span>
      <button className="secondary" type="button" disabled={paginaActual + 1 >= totalPaginas} onClick={() => cambiarPagina(paginaActual + 1)}>Siguiente</button>
    </nav>}
  </section>;
}

function estadoAsesoria(estado: Asesoria['estado']) {
  switch (estado) {
    case 'PENDIENTE': return 'Pendiente de atención';
    case 'EN_ATENCION': return 'En atención';
    case 'ATENDIDA': return 'Atendida';
  }
}
