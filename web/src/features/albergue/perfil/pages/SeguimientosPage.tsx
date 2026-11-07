import { useEffect, useState } from 'react';
import Feedback from '../../../../components/Feedback';
import { django, json, errorMessage } from '../../../../services/http';

type Seguimiento = { id: number; solicitud_id: number; mascota_nombre: string; adoptante_email: string | null; programado_para: string; modalidad: 'VISITA' | 'LLAMADA'; estado: string; notas: string; asesorias_veterinarias?: { id: number; problema: string; creado_en: string }[] };
type Draft = { solicitud_id: string; programado_para: string; modalidad: 'VISITA' | 'LLAMADA'; notas: string };
const empty: Draft = { solicitud_id: '', programado_para: '', modalidad: 'VISITA', notas: '' };

export default function SeguimientosPage() {
  const [rows, setRows] = useState<Seguimiento[]>([]);
  const [draft, setDraft] = useState<Draft>(empty);
  const [editing, setEditing] = useState<number | null>(null);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [busy, setBusy] = useState(false);
  async function load() {
    try {
      const result = await django<Seguimiento[] | { results: Seguimiento[] }>('/api/contratos/seguimientos/');
      setRows(Array.isArray(result) ? result : result.results);
      setError('');
    } catch (e) { setError(errorMessage(e)); }
  }
  useEffect(() => { void load(); }, []);
  async function save() {
    setBusy(true); setError(''); setSuccess('');
    try {
      const body = { ...draft, solicitud_id: Number(draft.solicitud_id), programado_para: `${draft.programado_para}:00-05:00` };
      await django(editing ? `/api/contratos/seguimientos/${editing}/` : '/api/contratos/seguimientos/', {
        method: editing ? 'PATCH' : 'POST', body: json(body),
      });
      setDraft(empty); setEditing(null); setSuccess('Seguimiento guardado.'); await load();
    } catch (e) { setError(errorMessage(e)); }
    finally { setBusy(false); }
  }
  async function remove(id: number) {
    if (!window.confirm('¿Cancelar este seguimiento programado?')) return;
    try {
      await django(`/api/contratos/seguimientos/${id}/`, { method: 'DELETE' });
      setSuccess('Seguimiento cancelado.'); await load();
    } catch (e) { setError(errorMessage(e)); }
  }
  return <section className="card">
    <h1>Seguimientos postadopción</h1>
    <p>Programa visitas o llamadas para adopciones entregadas. Ingresa el número de solicitud.</p>
    <Feedback error={error} success={success} />
    <form onSubmit={e => { e.preventDefault(); void save(); }}>
      <label>Solicitud entregada<input required type="number" min="1" value={draft.solicitud_id} disabled={editing !== null} onChange={e => setDraft({ ...draft, solicitud_id: e.target.value })} /></label>
      <label>Fecha y hora<input required type="datetime-local" value={draft.programado_para} onChange={e => setDraft({ ...draft, programado_para: e.target.value })} /></label>
      <label>Modalidad<select value={draft.modalidad} onChange={e => setDraft({ ...draft, modalidad: e.target.value as Draft['modalidad'] })}><option value="VISITA">Visita</option><option value="LLAMADA">Llamada</option></select></label>
      <label>Notas<textarea maxLength={500} value={draft.notas} onChange={e => setDraft({ ...draft, notas: e.target.value })} /></label>
      <button disabled={busy}>{editing ? 'Guardar cambios' : 'Programar seguimiento'}</button>
      {editing !== null && <button type="button" className="secondary" onClick={() => { setEditing(null); setDraft(empty); }}>Cancelar edición</button>}
    </form>
    <button type="button" className="secondary" onClick={() => void load()}>Actualizar agenda</button>
    {rows.length === 0 ? <p>No tienes seguimientos programados.</p> : <ul>{rows.map(row => <li key={row.id}>
      <strong>{row.mascota_nombre}</strong> · Solicitud #{row.solicitud_id} · {row.modalidad === 'VISITA' ? 'Visita' : 'Llamada'} · {new Date(row.programado_para).toLocaleString('es-PE')} · {row.estado}
      {row.notas && <p>{row.notas}</p>}
      {row.asesorias_veterinarias?.map(asesoria => <div key={asesoria.id}>
        <strong>Asesoría veterinaria gratuita solicitada · {new Date(asesoria.creado_en).toLocaleString('es-PE')}</strong>
        <p style={{ whiteSpace: 'pre-wrap' }}>{asesoria.problema}</p>
      </div>)}
      {row.estado === 'PROGRAMADO' && <><button type="button" onClick={() => { setEditing(row.id); setDraft({ solicitud_id: String(row.solicitud_id), programado_para: new Date(Date.parse(row.programado_para) - 5 * 3600000).toISOString().slice(0, 16), modalidad: row.modalidad, notas: row.notas }); }}>Editar</button><button type="button" className="secondary" onClick={() => void remove(row.id)}>Cancelar</button></>}
    </li>)}</ul>}
  </section>;
}
