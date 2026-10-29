import { useEffect, useState, type FormEvent } from 'react';
import { django, json, errorMessage } from '../../../../services/http';
import Feedback from '../../../../components/Feedback';

type Necesidad = {
  id: number; albergue_nombre: string; titulo: string; descripcion: string;
  cantidad_objetivo: string; unidad: string; estado: 'ABIERTA' | 'CUBIERTA';
};
type Donacion = {
  id: number; necesidad: number | null; necesidad_titulo: string; tipo: string;
  descripcion: string; cantidad: string; unidad: string; fecha: string;
};
type NecesidadDraft = Pick<Necesidad, 'titulo' | 'descripcion' | 'cantidad_objetivo' | 'unidad'>;
type DonacionDraft = {
  necesidad: string; tipo: string; descripcion: string; cantidad: string; unidad: string; fecha: string;
};
const emptyNeed: NecesidadDraft = { titulo: '', descripcion: '', cantidad_objetivo: '', unidad: '' };
const localDate = () => {
  const now = new Date();
  return new Date(now.getTime() - now.getTimezoneOffset() * 60_000).toISOString().slice(0, 10);
};
const emptyDonation = (): DonacionDraft => ({ necesidad: '', tipo: 'INSUMO', descripcion: '', cantidad: '', unidad: '', fecha: localDate() });

export default function NecesidadesPage({ gestionar = false }: { gestionar?: boolean }) {
  const [needs, setNeeds] = useState<Necesidad[]>([]);
  const [donations, setDonations] = useState<Donacion[]>([]);
  const [draft, setDraft] = useState<NecesidadDraft>(emptyNeed);
  const [donation, setDonation] = useState<DonacionDraft>(emptyDonation());
  const [editing, setEditing] = useState<number | null>(null);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [busy, setBusy] = useState(false);

  async function load() {
    try {
      const [needsResponse, donationsResponse] = await Promise.all([
        django<Necesidad[] | { results: Necesidad[] }>('/api/necesidades/'),
        gestionar ? django<Donacion[] | { results: Donacion[] }>('/api/donaciones/') : Promise.resolve([] as Donacion[]),
      ]);
      setNeeds(Array.isArray(needsResponse) ? needsResponse : needsResponse.results);
      setDonations(Array.isArray(donationsResponse) ? donationsResponse : donationsResponse.results);
      setError('');
    } catch (e) { setError(errorMessage(e)); }
  }
  useEffect(() => { void load(); }, [gestionar]);

  async function saveNeed(event: FormEvent) {
    event.preventDefault(); setBusy(true); setError(''); setSuccess('');
    try {
      await django(editing ? `/api/necesidades/${editing}/` : '/api/necesidades/', {
        method: editing ? 'PATCH' : 'POST', body: json(draft),
      });
      setDraft(emptyNeed); setEditing(null); setSuccess('Necesidad guardada.'); await load();
    } catch (e) { setError(errorMessage(e)); } finally { setBusy(false); }
  }
  async function changeStatus(need: Necesidad) {
    setBusy(true); setError(''); setSuccess('');
    try {
      const estado = need.estado === 'ABIERTA' ? 'CUBIERTA' : 'ABIERTA';
      await django(`/api/necesidades/${need.id}/`, { method: 'PATCH', body: json({ estado }) });
      setSuccess(`Necesidad marcada como ${estado === 'ABIERTA' ? 'abierta' : 'cubierta'}.`); await load();
    } catch (e) { setError(errorMessage(e)); } finally { setBusy(false); }
  }
  async function saveDonation(event: FormEvent) {
    event.preventDefault(); setBusy(true); setError(''); setSuccess('');
    const body = { ...donation, necesidad: donation.necesidad ? Number(donation.necesidad) : null };
    try {
      await django('/api/donaciones/', { method: 'POST', body: json(body) });
      setDonation(emptyDonation()); setSuccess('Aporte registrado de forma privada.'); await load();
    } catch (e) { setError(errorMessage(e)); } finally { setBusy(false); }
  }

  return <section className="card">
    <h1>{gestionar ? 'Necesidades y donaciones' : 'Necesidades de los albergues'}</h1>
    <p>{gestionar ? 'Publica necesidades y registra aportes recibidos. No se recopilan datos personales de donantes.' : 'Consulta los insumos y apoyos que los albergues activos necesitan.'}</p>
    <Feedback error={error} success={success} />
    <button type="button" disabled={busy} onClick={() => void load()}>Actualizar</button>
    {gestionar && <>
      <form onSubmit={saveNeed}>
        <h2>{editing ? 'Editar necesidad' : 'Publicar necesidad'}</h2>
        <fieldset disabled={busy}>
          <label>Título<input required maxLength={150} value={draft.titulo} onChange={e => setDraft({ ...draft, titulo: e.target.value })} /></label>
          <label>Descripción<textarea maxLength={2000} value={draft.descripcion} onChange={e => setDraft({ ...draft, descripcion: e.target.value })} /></label>
          <label>Cantidad objetivo<input required type="number" min="0.01" step="0.01" value={draft.cantidad_objetivo} onChange={e => setDraft({ ...draft, cantidad_objetivo: e.target.value })} /></label>
          <label>Unidad<input required maxLength={40} placeholder="kg, unidades, soles…" value={draft.unidad} onChange={e => setDraft({ ...draft, unidad: e.target.value })} /></label>
          <button>{editing ? 'Guardar cambios' : 'Publicar necesidad'}</button>
          {editing && <button type="button" onClick={() => { setEditing(null); setDraft(emptyNeed); }}>Cancelar edición</button>}
        </fieldset>
      </form>
      <form onSubmit={saveDonation}>
        <h2>Registrar aporte recibido</h2>
        <p className="muted">Registro interno del albergue. No incluye ni publica nombre, correo o teléfono del donante.</p>
        <fieldset disabled={busy}>
          <label>Necesidad relacionada<select value={donation.necesidad} onChange={e => {
            const need = needs.find(n => n.id === Number(e.target.value));
            setDonation({ ...donation, necesidad: e.target.value, unidad: need?.unidad ?? donation.unidad });
          }}><option value="">Aporte general</option>{needs.filter(n => n.estado === 'ABIERTA').map(n => <option key={n.id} value={n.id}>{n.titulo}</option>)}</select></label>
          <label>Tipo<select value={donation.tipo} onChange={e => setDonation({ ...donation, tipo: e.target.value })}><option value="DINERO">Dinero</option><option value="INSUMO">Insumo</option><option value="SERVICIO">Servicio</option><option value="OTRO">Otro</option></select></label>
          <label>Descripción<input required maxLength={200} value={donation.descripcion} onChange={e => setDonation({ ...donation, descripcion: e.target.value })} /></label>
          <label>Cantidad<input required type="number" min="0.01" step="0.01" value={donation.cantidad} onChange={e => setDonation({ ...donation, cantidad: e.target.value })} /></label>
          <label>Unidad<input required maxLength={40} value={donation.unidad} onChange={e => setDonation({ ...donation, unidad: e.target.value })} /></label>
          <label>Fecha<input required type="date" value={donation.fecha} onChange={e => setDonation({ ...donation, fecha: e.target.value })} /></label>
          <button>Registrar aporte</button>
        </fieldset>
      </form>
    </>}
    <h2>{gestionar ? 'Necesidades publicadas' : 'Necesidades abiertas'}</h2>
    {!needs.length && <p>No hay necesidades abiertas por el momento.</p>}
    {needs.map(need => <article className="card" key={need.id}>
      <h3>{need.titulo}</h3><p><strong>{need.albergue_nombre}</strong> · {need.estado === 'ABIERTA' ? 'Abierta' : 'Cubierta'}</p>
      {need.descripcion && <p>{need.descripcion}</p>}<p>Objetivo: {need.cantidad_objetivo} {need.unidad}</p>
      {gestionar && <div className="actions">
        <button type="button" disabled={busy} onClick={() => { setEditing(need.id); setDraft({ titulo: need.titulo, descripcion: need.descripcion, cantidad_objetivo: need.cantidad_objetivo, unidad: need.unidad }); }}>Editar</button>
        <button type="button" disabled={busy} onClick={() => void changeStatus(need)}>{need.estado === 'ABIERTA' ? 'Marcar cubierta' : 'Reabrir necesidad'}</button>
      </div>}
    </article>)}
    {gestionar && <>
      <h2>Historial de aportes (privado)</h2>
      {!donations.length && <p>Aún no hay aportes registrados.</p>}
      {donations.map(item => <article className="card" key={item.id}><h3>{item.descripcion}</h3>
        <p>{item.fecha} · {item.cantidad} {item.unidad} · {item.tipo.toLowerCase()}</p>
        {item.necesidad_titulo && <p>Asociado a: {item.necesidad_titulo}</p>}
      </article>)}
    </>}
  </section>;
}
