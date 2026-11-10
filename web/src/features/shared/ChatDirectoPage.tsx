import { useCallback, useEffect, useState } from 'react';
import { useNavigate, useParams, useSearchParams } from 'react-router-dom';
import Feedback from '../../components/Feedback';
import { django, downloadDjango, errorMessage, json } from '../../services/http';

type Message = { id: number; remitente_id: number; propio: boolean; texto: string; creado_en: string; bienvenida_automatica: boolean; leido: boolean; adjuntos: { id: number; nombre_original: string; descarga: string }[] };
type Conversation = { id: number; solicitud_id: number | null; mascota: string; nombre_contacto: string; no_leidos: number; promedio_respuesta_horas: number | null; ultimo_mensaje: Message | null; bloqueada: boolean; puede_enviar: boolean };
type Thread = { conversacion: Conversation; mensajes: Message[] };

export default function ChatDirectoPage({ role }: { role: 'ADOPTANTE' | 'ALBERGUE' }) {
  const { id } = useParams();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const shelterParam = searchParams.get('albergue');
  const shelterId = shelterParam && /^\d+$/.test(shelterParam) ? Number(shelterParam) : null;
  const [conversations, setConversations] = useState<Conversation[]>([]);
  const [thread, setThread] = useState<Thread | null>(null);
  const [draft, setDraft] = useState('');
  const [file, setFile] = useState<File | null>(null);
  const [welcome, setWelcome] = useState('');
  const [reason, setReason] = useState('ACOSO');
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [busy, setBusy] = useState(false);
  const loadList = useCallback(async () => {
    try {
      const result = await django<Conversation[]>('/api/mensajeria/conversaciones/');
      setConversations(result);
      if (!id && !shelterId && result.length) void navigate(`/${role === 'ADOPTANTE' ? 'adoptante' : 'albergue'}/mensajes/${result[0].id}`, { replace: true });
    } catch (e) { setError(errorMessage(e)); }
  }, [id, navigate, role, shelterId]);

  const loadThread = useCallback(async () => {
    if (!id) { setThread(null); return; }
    try {
      const result = await django<Thread>(`/api/mensajeria/conversaciones/${id}/`);
      setThread(result);
      await django(`/api/mensajeria/conversaciones/${id}/leer/`, { method: 'POST' });
      setError('');
    } catch (e) { setError(errorMessage(e)); }
  }, [id]);

  useEffect(() => { void loadList(); }, [loadList]);
  useEffect(() => {
    if (role !== 'ADOPTANTE' || shelterId == null) return;
    let active = true;
    void django<Conversation>('/api/mensajeria/conversaciones/', {
      method: 'POST', body: json({ albergue_id: shelterId }),
    }).then(conversation => {
      if (active) void navigate(`/adoptante/mensajes/${conversation.id}`, { replace: true });
    }).catch(e => { if (active) setError(errorMessage(e)); });
    return () => { active = false; };
  }, [navigate, role, shelterId]);
  useEffect(() => {
    if (role !== 'ALBERGUE') return;
    void django<{ texto: string }>('/api/mensajeria/bienvenida/').then(v => setWelcome(v.texto)).catch(e => setError(errorMessage(e)));
  }, [role]);
  useEffect(() => {
    void loadThread();
    const timer = window.setInterval(() => { void loadList(); void loadThread(); }, 5000);
    return () => window.clearInterval(timer);
  }, [loadList, loadThread]);

  async function send(event: React.FormEvent) {
    event.preventDefault();
    if ((!draft.trim() && !file) || !id || busy) return;
    setBusy(true); setError('');
    try {
      const body = new FormData();
      if (draft.trim()) body.set('texto', draft.trim());
      if (file) body.set('archivo', file);
      await django(`/api/mensajeria/conversaciones/${id}/mensajes/`, { method: 'POST', body });
      setDraft(''); setFile(null);
      await loadThread(); await loadList();
    } catch (e) { setError(errorMessage(e)); }
    finally { setBusy(false); }
  }

  async function moderate(action: 'bloquear' | 'reportar') {
    if (!id) return;
    setError(''); setSuccess('');
    try {
      if (action === 'bloquear') {
        if (!window.confirm('¿Bloquear a esta persona? Ya no podrá enviarte nuevos mensajes.')) return;
        await django(`/api/mensajeria/conversaciones/${id}/bloquear/`, { method: 'POST', body: json({}) });
        setSuccess('Conversación bloqueada. La otra persona no podrá enviarte nuevos mensajes.');
        await loadThread(); await loadList();
      } else {
        await django(`/api/mensajeria/conversaciones/${id}/reportar/`, { method: 'POST', body: json({ motivo: reason }) });
        setSuccess('El reporte quedó registrado para revisión.');
      }
    } catch (e) { setError(errorMessage(e)); }
  }

  async function saveWelcome() {
    try { await django('/api/mensajeria/bienvenida/', { method: 'PUT', body: json({ texto: welcome }) }); setSuccess('Mensaje de bienvenida guardado. Se enviará una vez al abrir nuevas conversaciones.'); }
    catch (e) { setError(errorMessage(e)); }
  }

  async function downloadAttachment(url: string, filename: string) {
    try { await downloadDjango(url, filename); }
    catch (e) { setError(errorMessage(e)); }
  }

  return <section className="card">
    <h1>Mensajes</h1>
    <Feedback error={error} success={success} />
    {role === 'ALBERGUE' && <details className="card"><summary>Mensaje de bienvenida automática</summary><label>Texto que recibirán los adoptantes al abrir el chat<textarea maxLength={1000} value={welcome} onChange={e => setWelcome(e.target.value)} /></label><button type="button" onClick={() => void saveWelcome()}>Guardar bienvenida</button></details>}
    <div className="chat-directo-layout">
      <nav aria-label="Conversaciones" className="chat-directo-list">{conversations.map(item => <button type="button" key={item.id} aria-current={String(item.id) === id} onClick={() => void navigate(`/${role === 'ADOPTANTE' ? 'adoptante' : 'albergue'}/mensajes/${item.id}`)}><strong>{item.nombre_contacto}</strong><span>{item.mascota} · solicitud #{item.solicitud_id}</span><small>{item.ultimo_mensaje?.texto || item.ultimo_mensaje?.adjuntos[0]?.nombre_original || 'Sin mensajes'}{item.no_leidos ? ` · ${item.no_leidos} sin leer` : ''}</small></button>)}{conversations.length === 0 && <p>Aún no hay conversaciones. Abre una desde una solicitud de adopción.</p>}</nav>
      <div className="chat-directo-thread">
        {thread ? <>
          <header><h2>{thread.conversacion.nombre_contacto} · {thread.conversacion.mascota}</h2><p>Tiempo promedio de respuesta: {thread.conversacion.promedio_respuesta_horas == null ? 'sin datos' : `${thread.conversacion.promedio_respuesta_horas} h`}</p></header>
          {thread.conversacion.bloqueada && <p role="status">Esta conversación tiene un bloqueo. {thread.conversacion.puede_enviar ? 'La otra persona no puede enviarte mensajes nuevos.' : 'Esta persona te bloqueó y no puedes enviarle mensajes.'}</p>}
          <div className="chat-directo-messages" aria-live="polite">{thread.mensajes.map(message => <article key={message.id} className={message.propio ? 'chat-directo-message mine' : 'chat-directo-message'}><p>{message.texto}</p>{message.adjuntos.map(fileItem => <button key={fileItem.id} type="button" className="secondary" onClick={() => void downloadAttachment(fileItem.descarga, fileItem.nombre_original)}>{fileItem.nombre_original} · descargar</button>)}<small>{message.bienvenida_automatica ? 'Bienvenida automática · ' : ''}{new Date(message.creado_en).toLocaleString('es-PE')}{message.propio && message.leido ? ' · Leído' : ''}</small></article>)}</div>
          <form onSubmit={e => void send(e)}><label htmlFor="chat-directo-draft">Mensaje</label><textarea id="chat-directo-draft" maxLength={4000} value={draft} onChange={e => setDraft(e.target.value)} disabled={!thread.conversacion.puede_enviar} /><label>Adjunto (JPG, PNG o PDF; máximo 10 MB)<input type="file" accept="image/jpeg,image/png,application/pdf" onChange={e => setFile(e.target.files?.[0] ?? null)} disabled={!thread.conversacion.puede_enviar} /></label>{file && <p>{file.name}</p>}<button disabled={!thread.conversacion.puede_enviar || busy || (!draft.trim() && !file)}>{busy ? 'Enviando…' : 'Enviar'}</button></form>
          <div className="chat-directo-actions"><label>Motivo del reporte<select value={reason} onChange={e => setReason(e.target.value)}><option value="ACOSO">Acoso o amenazas</option><option value="FRAUDE">Fraude o suplantación</option><option value="CONTENIDO">Contenido inapropiado</option><option value="OTRO">Otro motivo</option></select></label><button type="button" className="secondary" onClick={() => void moderate('reportar')}>Reportar conversación</button><button type="button" className="danger" disabled={thread.conversacion.bloqueada} onClick={() => void moderate('bloquear')}>{thread.conversacion.bloqueada ? 'Conversación bloqueada' : 'Bloquear conversación'}</button></div>
        </> : <p>Selecciona una conversación para ver los mensajes.</p>}
      </div>
    </div>
  </section>;
}
