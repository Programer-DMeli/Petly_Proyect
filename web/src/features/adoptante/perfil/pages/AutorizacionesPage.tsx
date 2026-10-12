import { useCallback, useEffect, useState } from 'react';
import Feedback from '../../../../components/Feedback';
import { spring, errorMessage } from '../../../../services/http';
interface Shelter {
  id: number;
  nombre: string;
  direccion: string;
}
interface Consent {
  albergueId: number;
  nombre: string;
}
export default function AutorizacionesPage() {
  const [shelters, setShelters] = useState<Shelter[]>([]);
  const [consents, setConsents] = useState<Consent[]>([]);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [loaded, setLoaded] = useState(false);
  const [selected, setSelected] = useState<Shelter | null>(null);
  const load = useCallback(async () => {
    setError('');
    try {
      const [s, c] = await Promise.all([
        spring<Shelter[]>('/api/catalogo/albergues'),
        spring<Consent[]>('/api/autorizaciones'),
      ]);
      setShelters(s);
      setConsents(c);
      setLoaded(true);
    } catch (e) {
      setError(errorMessage(e));
    }
  }, []);
  useEffect(() => {
    void load();
  }, [load]);
  async function change(id: number, allow: boolean) {
    setBusy(true);
    setError('');
    try {
      await spring(`/api/autorizaciones/${id}`, { method: allow ? 'PUT' : 'DELETE' });
      setSelected(null);
      await load();
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }
  return (
    <section className="card">
      <h1>Compartir mi perfil</h1>
      <p>
        Elige a qué albergue permites consultar tu vivienda, rutina, presupuesto y convivencia.
        Puedes revocar el permiso. Esto no crea una solicitud de adopción.
      </p>
      <Feedback error={error} />
      {!loaded && <button onClick={() => void load()}>Cargar albergues</button>}
      {loaded && shelters.length === 0 && <p>No hay albergues disponibles.</p>}
      {shelters.map(s => {
        const allowed = consents.some(c => c.albergueId === s.id);
        return (
          <article className="list-row" key={s.id}>
            <div>
              <h2>{s.nombre}</h2>
              <p>{s.direccion}</p>
              <span className="badge">{allowed ? 'Autorizado' : 'Sin autorización'}</span>
            </div>
            <button
              disabled={busy}
              onClick={() => (allowed ? void change(s.id, false) : setSelected(s))}
            >
              {allowed ? 'Revocar' : 'Autorizar'}
            </button>
          </article>
        );
      })}
      {selected && (
        <div
          role="dialog"
          aria-modal="true"
          aria-label="Confirmar autorización"
          className="confirmation"
        >
          <h2>¿Compartir con {selected.nombre}?</h2>
          <p>
            Este albergue podrá consultar los campos de tu cuestionario mientras mantengas el
            permiso.
          </p>
          <div className="actions">
            <button disabled={busy} onClick={() => void change(selected.id, true)}>
              Sí, autorizo compartir
            </button>
            <button className="secondary" onClick={() => setSelected(null)}>
              Cancelar
            </button>
          </div>
        </div>
      )}
    </section>
  );
}
