import { useCallback, useEffect, useState } from 'react';
import Feedback from '../../../../components/Feedback';
import { django, json, errorMessage } from '../../../../services/http';

type Rule = {
  id: number;
  pregunta_clave: string;
  disparadores: string[];
  respuesta: string;
  activa: boolean;
};
type Draft = { pregunta_clave: string; disparadores: string; respuesta: string; activa: boolean };
type QuestionMetric = { pregunta_clave: string; consultas: number };
type MetricsSummary = {
  desde: string;
  hasta: string;
  total_consultas: number;
  preguntas: QuestionMetric[];
};
const empty: Draft = { pregunta_clave: '', disparadores: '', respuesta: '', activa: true };

function localDate(date: Date) {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

function defaultPeriod() {
  const hasta = new Date();
  const desde = new Date(hasta);
  desde.setDate(desde.getDate() - 29);
  return { desde: localDate(desde), hasta: localDate(hasta) };
}

export default function RespuestasChatbotPage() {
  const [rules, setRules] = useState<Rule[]>([]);
  const [draft, setDraft] = useState<Draft>(empty);
  const [editing, setEditing] = useState<number | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [initialPeriod] = useState(defaultPeriod);
  const [metricsFrom, setMetricsFrom] = useState(initialPeriod.desde);
  const [metricsTo, setMetricsTo] = useState(initialPeriod.hasta);
  const [metrics, setMetrics] = useState<MetricsSummary | null>(null);
  const [metricsBusy, setMetricsBusy] = useState(false);
  const [metricsError, setMetricsError] = useState('');

  async function load() {
    try {
      const result = await django<Rule[] | { results: Rule[] }>('/api/albergues/respuestas-chatbot/');
      setRules(Array.isArray(result) ? result : result.results);
      setError('');
    } catch (cause) { setError(errorMessage(cause)); }
  }
  useEffect(() => { void load(); }, []);

  const loadMetrics = useCallback(async (desde: string, hasta: string) => {
    if (!desde || !hasta) {
      setMetricsError('Selecciona las dos fechas del periodo.');
      return;
    }
    setMetricsBusy(true);
    setMetricsError('');
    try {
      const params = new URLSearchParams({ desde, hasta });
      setMetrics(await django<MetricsSummary>(
        `/api/albergues/respuestas-chatbot/metricas/?${params.toString()}`
      ));
    } catch (cause) {
      setMetricsError(errorMessage(cause));
    } finally {
      setMetricsBusy(false);
    }
  }, []);

  useEffect(() => {
    void loadMetrics(initialPeriod.desde, initialPeriod.hasta);
  }, [initialPeriod, loadMetrics]);

  async function save() {
    setBusy(true); setError(''); setSuccess('');
    try {
      const body = {
        pregunta_clave: draft.pregunta_clave.trim(),
        disparadores: draft.disparadores.split('\n').map(value => value.trim()).filter(Boolean),
        respuesta: draft.respuesta.trim(),
        activa: draft.activa,
      };
      await django(editing
        ? `/api/albergues/respuestas-chatbot/${editing}/`
        : '/api/albergues/respuestas-chatbot/', {
        method: editing ? 'PATCH' : 'POST', body: json(body),
      });
      setDraft(empty); setEditing(null); setSuccess('Respuesta automática guardada.'); await load();
    } catch (cause) { setError(errorMessage(cause)); }
    finally { setBusy(false); }
  }

  async function remove(rule: Rule) {
    if (!window.confirm(`¿Eliminar la respuesta “${rule.pregunta_clave}”?`)) return;
    setBusy(true); setError(''); setSuccess('');
    try {
      await django(`/api/albergues/respuestas-chatbot/${rule.id}/`, { method: 'DELETE' });
      setSuccess('Respuesta eliminada.'); await load();
    } catch (cause) { setError(errorMessage(cause)); }
    finally { setBusy(false); }
  }

  return <section className="card">
    <h1>Respuestas automáticas del chatbot</h1>
    <p>Configura respuestas públicas para preguntas frecuentes. Cuando la consulta coincide de forma clara, el chatbot muestra tu respuesta y el nombre del albergue; si hay varias coincidencias, responde con su orientación general.</p>
    <Feedback error={error} success={success} />
    <form onSubmit={event => { event.preventDefault(); void save(); }}>
      <label>Pregunta clave
        <input required minLength={4} maxLength={180} value={draft.pregunta_clave}
          onChange={event => setDraft({ ...draft, pregunta_clave: event.target.value })}
          placeholder="¿Cuál es el horario de atención?" />
      </label>
      <label>Frases que también activan la respuesta <span className="muted">(una por línea, opcional)</span>
        <textarea maxLength={1000} rows={4} value={draft.disparadores}
          onChange={event => setDraft({ ...draft, disparadores: event.target.value })}
          placeholder={'horario de visitas\ncuándo puedo visitar'} />
      </label>
      <label>Respuesta pública
        <textarea required maxLength={1200} rows={5} value={draft.respuesta}
          onChange={event => setDraft({ ...draft, respuesta: event.target.value })}
          placeholder="Atendemos de lunes a viernes de 9 a. m. a 5 p. m. Coordina tu visita por..." />
      </label>
      <label className="checkbox-label">
        <input type="checkbox" checked={draft.activa}
          onChange={event => setDraft({ ...draft, activa: event.target.checked })} />
        Activar esta respuesta
      </label>
      <button disabled={busy}>{editing ? 'Guardar cambios' : 'Agregar respuesta'}</button>
      {editing !== null && <button type="button" className="secondary" disabled={busy}
        onClick={() => { setEditing(null); setDraft(empty); }}>Cancelar edición</button>}
    </form>

    <section aria-labelledby="chatbot-metricas-titulo">
      <h2 id="chatbot-metricas-titulo">Preguntas más consultadas</h2>
      <p className="muted">
        Se cuentan las consultas que activaron una respuesta configurada por tu albergue. Se guarda el conteo diario y la pregunta clave, no el mensaje libre ni la identidad del adoptante.
      </p>
      <form className="actions" onSubmit={event => {
        event.preventDefault();
        void loadMetrics(metricsFrom, metricsTo);
      }}>
        <label>
          Desde
          <input type="date" value={metricsFrom} max={metricsTo || undefined}
            onChange={event => setMetricsFrom(event.target.value)} />
        </label>
        <label>
          Hasta
          <input type="date" value={metricsTo} min={metricsFrom || undefined}
            onChange={event => setMetricsTo(event.target.value)} />
        </label>
        <button disabled={metricsBusy}>
          {metricsBusy ? 'Consultando…' : 'Actualizar métricas'}
        </button>
      </form>
      {metricsError && <p role="alert">{metricsError}</p>}
      {metrics && !metricsBusy && (
        <>
          <p>
            <strong>{new Intl.NumberFormat('es-PE').format(metrics.total_consultas)}</strong>{' '}
            consultas respondidas entre {metrics.desde} y {metrics.hasta}.
          </p>
          {metrics.preguntas.length === 0 ? (
            <p>Aún no hay consultas coincidentes en este periodo.</p>
          ) : (
            <ol className="chatbot-metrics-ranking">
              {metrics.preguntas.map((metric, index) => (
                <li key={metric.pregunta_clave}>
                  <div>
                    <strong>{metric.pregunta_clave}</strong>
                    <span>{new Intl.NumberFormat('es-PE').format(metric.consultas)} consultas</span>
                  </div>
                  <progress
                    value={metric.consultas}
                    max={metrics.preguntas[0].consultas}
                    aria-label={`Frecuencia ${index + 1}: ${metric.pregunta_clave}`}
                  />
                </li>
              ))}
            </ol>
          )}
        </>
      )}
    </section>

    <h2>Respuestas configuradas</h2>
    {rules.length === 0 ? <p>Aún no configuraste respuestas. Solo tu albergue puede ver y editar estas reglas.</p> :
      <ul>{rules.map(rule => <li key={rule.id}>
        <strong>{rule.pregunta_clave}</strong> · {rule.activa ? 'Activa' : 'Desactivada'}
        {rule.disparadores.length > 0 && <p>También coincide con: {rule.disparadores.join(' · ')}</p>}
        <p style={{ whiteSpace: 'pre-wrap' }}>{rule.respuesta}</p>
        <button type="button" disabled={busy} onClick={() => {
          setEditing(rule.id);
          setDraft({ pregunta_clave: rule.pregunta_clave, disparadores: rule.disparadores.join('\n'), respuesta: rule.respuesta, activa: rule.activa });
          setError(''); setSuccess('');
        }}>Editar</button>
        <button type="button" className="secondary" disabled={busy} onClick={() => void remove(rule)}>Eliminar</button>
      </li>)}</ul>}
  </section>;
}
