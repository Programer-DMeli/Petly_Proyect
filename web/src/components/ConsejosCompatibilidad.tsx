import { useState } from 'react';
import { spring, json, errorMessage } from '../services/http';
export default function ConsejosCompatibilidad({ id }: { id: number }) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [result, setResult] = useState<{ estado: string; explicacion: string; recomendaciones: string[] } | null>(null);
  async function load() {
    setBusy(true); setError('');
    try { setResult(await spring(`/api/v1/matches/mascota/${id}/explicacion`, { method: 'POST', body: json({}) })); }
    catch (e) { setError(errorMessage(e)); }
    finally { setBusy(false); }
  }
  return <section aria-label="Consejos complementarios de Gemini">
    <h3>Consejos complementarios de Gemini</h3>
    <p>La IA aporta sugerencias para revisar. El puntaje y el orden se calculan con las reglas de Petly.</p>
    <button type="button" disabled={busy} onClick={() => void load()}>{busy ? 'Consultando…' : 'Consultar o reintentar consejos IA'}</button>
    {error && <p role="alert">{error}</p>}
    {result && <div role="status"><p>{result.estado === 'DISPONIBLE' ? 'Sugerencia IA para revisión' : 'IA no disponible'}</p><p>{result.explicacion}</p><ul>{result.recomendaciones.map((text, i) => <li key={i}>{text}</li>)}</ul></div>}
  </section>;
}
