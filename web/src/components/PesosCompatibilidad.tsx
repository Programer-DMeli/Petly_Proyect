import { useEffect, useState } from 'react';
import { spring, json, errorMessage } from '../services/http';
type Reglas = { pesos: { vivienda: number; rutina: number; convivencia: number }; versionReglas: string };
export default function PesosCompatibilidad() {
  const [reglas, setReglas] = useState<Reglas | null>(null);
  const [error, setError] = useState(''); const [success, setSuccess] = useState('');
  const [busy, setBusy] = useState(false);
  const load = () => { setError(''); void spring<Reglas>('/api/admin/compatibilidad/reglas').then(setReglas).catch(e => setError(errorMessage(e))); };
  useEffect(load, []);
  async function save() {
    if (!reglas) return;
    setBusy(true); setError(''); setSuccess('');
    try { setReglas(await spring('/api/admin/compatibilidad/reglas', { method: 'PUT', body: json({ pesos: reglas.pesos }) })); setSuccess('Pesos guardados. Las nuevas consultas usan esta versión.'); }
    catch (e) { setError(errorMessage(e)); } finally { setBusy(false); }
  }
  const valid = reglas && Object.values(reglas.pesos).every(n => Number.isInteger(n) && n >= 0 && n <= 100) && Object.values(reglas.pesos).reduce((a,b) => a+b,0) === 100;
  return <section className="card"><h1>Pesos de compatibilidad</h1>
    {error && <p role="alert">{error}</p>}{success && <p role="status">{success}</p>}
    {!reglas ? <button onClick={load}>Recargar configuración</button> : <fieldset disabled={busy}><form onSubmit={e => { e.preventDefault(); void save(); }}>
      <p>{reglas.versionReglas}</p>
      {(['vivienda', 'rutina', 'convivencia'] as const).map(key => <label key={key}>{key}<input type="number" min="0" max="100" step="1" required value={reglas.pesos[key]} onChange={e => setReglas({ ...reglas, pesos: { ...reglas.pesos, [key]: Number(e.target.value) } })} /></label>)}
      <p>Los tres pesos deben sumar 100. Cada cambio registra fecha, responsable y nueva versión.</p>
      <button disabled={busy || !valid}>Guardar pesos</button>
    </form></fieldset>}
  </section>;
}
