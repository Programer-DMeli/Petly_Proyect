import { useEffect, useState, type FormEvent } from 'react';
import Feedback from '../../../../components/Feedback';
import PrivateDjangoImage from '../../../../components/PrivateDjangoImage';
import { errorMessage, spring } from '../../../../services/http';

type Bienestar = 'EXCELENTE' | 'BUENO' | 'REGULAR' | 'PREOCUPANTE';
type Foto = { id: number; mime_type: string; tamaño_bytes: number; descarga_url: string };
type Reporte = {
  id: number;
  estado_bienestar: Bienestar;
  estado_bienestar_display: string;
  observaciones: string;
  enviado_en: string;
  fotos: Foto[];
};
type Seguimiento = {
  id: number;
  solicitud_id: number;
  mascota_nombre: string;
  albergue_nombre: string;
  programado_para: string;
  modalidad: 'VISITA' | 'LLAMADA';
  estado: string;
  notas: string;
  expediente_cierre: { cerrado_en: string } | null;
  reporte_bienestar: Reporte | null;
};

const MAX_PHOTOS = 5;
const MAX_PHOTO_BYTES = 5 * 1024 * 1024;
const MAX_TOTAL_BYTES = 20 * 1024 * 1024;

function fecha(value: string) {
  return new Date(value).toLocaleString('es-PE', { dateStyle: 'medium', timeStyle: 'short' });
}

export default function ReportesPostAdopcionPage() {
  const [rows, setRows] = useState<Seguimiento[]>([]);
  const [activeId, setActiveId] = useState<number | null>(null);
  const [bienestar, setBienestar] = useState<Bienestar>('BUENO');
  const [observaciones, setObservaciones] = useState('');
  const [fotos, setFotos] = useState<File[]>([]);
  const [busy, setBusy] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  async function load() {
    try {
      const result = await spring<Seguimiento[] | { results: Seguimiento[] }>('/api/seguimientos/adoptante');
      setRows(Array.isArray(result) ? result : result.results);
      setError('');
    } catch (cause) { setError(errorMessage(cause)); }
    finally { setLoading(false); }
  }
  useEffect(() => { void load(); }, []);

  function selectPhotos(list: FileList | null) {
    const selected = Array.from(list ?? []);
    const total = selected.reduce((sum, file) => sum + file.size, 0);
    if (selected.length > MAX_PHOTOS) { setError('Puedes adjuntar hasta 5 fotos por reporte.'); setFotos([]); return; }
    if (selected.some(file => !['image/jpeg', 'image/png'].includes(file.type) || file.size === 0 || file.size > MAX_PHOTO_BYTES)) {
      setError('Cada foto debe ser JPEG o PNG y pesar hasta 5 MB.'); setFotos([]); return;
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
      await spring('/api/seguimientos/adoptante', { method: 'POST', body });
      setActiveId(null); setFotos([]); setBienestar('BUENO'); setObservaciones('');
      setSuccess('Reporte enviado al albergue.'); await load();
    } catch (cause) { setError(errorMessage(cause)); }
    finally { setBusy(false); }
  }

  return <section className="card">
    <h1>Reportes de bienestar postadopción</h1>
    <p>Envía una actualización con fotos para cada seguimiento programado. El reporte solo lo ven tú y el albergue responsable.</p>
    <Feedback error={error} success={success} />
    {loading ? <p role="status">Cargando tu cronograma…</p> : rows.length === 0
      ? <p>Aún no tienes seguimientos postadopción programados.</p>
      : <ul>{rows.map(row => <li key={row.id}>
        <strong>{row.mascota_nombre}</strong> · Solicitud #{row.solicitud_id} · {row.albergue_nombre}
        <p>{row.modalidad === 'VISITA' ? 'Visita' : 'Llamada'} · {fecha(row.programado_para)} · {row.estado}</p>
        {row.notas && <p>{row.notas}</p>}
        {row.reporte_bienestar ? <article>
          <h2>Reporte enviado · {fecha(row.reporte_bienestar.enviado_en)}</h2>
          <p>Estado de bienestar: <strong>{row.reporte_bienestar.estado_bienestar_display}</strong></p>
          {row.reporte_bienestar.observaciones && <p style={{ whiteSpace: 'pre-wrap' }}>{row.reporte_bienestar.observaciones}</p>}
          <div className="actions">{row.reporte_bienestar.fotos.map(photo => <div key={photo.id} style={{ maxWidth: 220 }}>
            <PrivateDjangoImage src={photo.descarga_url} alt={`Foto adjunta al reporte de ${row.mascota_nombre}`} />
          </div>)}</div>
        </article> : row.estado !== 'CANCELADO' && !row.expediente_cierre ? <>
          {activeId === row.id ? <form onSubmit={event => void submit(event, row)}>
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
            <label>Fotos JPEG o PNG, hasta 5 fotos de 5 MB cada una
              <input type="file" accept="image/jpeg,image/png" multiple required
                onChange={event => selectPhotos(event.currentTarget.files)} />
            </label>
            {fotos.length > 0 && <p>{fotos.map(file => file.name).join(' · ')}</p>}
            <button disabled={busy || fotos.length === 0}>Enviar reporte</button>
            <button type="button" className="secondary" disabled={busy} onClick={() => {
              setActiveId(null); setFotos([]); setObservaciones(''); setError('');
            }}>Cancelar</button>
          </form> : <button type="button" className="secondary" onClick={() => {
            setActiveId(row.id); setError(''); setSuccess('');
          }}>Preparar reporte</button>}
        </> : <p>Este seguimiento no admite nuevos reportes.</p>}
      </li>)}</ul>}
  </section>;
}
