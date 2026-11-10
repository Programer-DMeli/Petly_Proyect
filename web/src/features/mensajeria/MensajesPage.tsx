import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import Feedback from '../../components/Feedback';
import { django, errorMessage, json } from '../../services/http';
import { getSession } from '../../services/session';
import './MensajesPage.css';

type Conversation = {
  id: number;
  albergue_id: number;
  albergue_nombre: string;
  interlocutor: string;
  ultimo_mensaje: string;
  creada_en: string;
  actualizada_en: string;
};
type Message = {
  id: number;
  remitente_spring_id: number | null;
  remitente_nombre: string;
  contenido: string;
  creado_en: string;
};

function formatTime(value: string) {
  return new Intl.DateTimeFormat('es-PE', { dateStyle: 'short', timeStyle: 'short' }).format(new Date(value));
}

export default function MensajesPage() {
  const session = getSession();
  const role = session?.identity.rol;
  const [searchParams, setSearchParams] = useSearchParams();
  const shelterParam = searchParams.get('albergue');
  const shelterId = shelterParam && /^\d+$/.test(shelterParam) ? Number(shelterParam) : null;
  const [conversations, setConversations] = useState<Conversation[]>([]);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [messages, setMessages] = useState<Message[]>([]);
  const [draft, setDraft] = useState('');
  const [loading, setLoading] = useState(true);
  const [sending, setSending] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const bottomRef = useRef<HTMLDivElement>(null);

  const loadConversations = useCallback(async () => {
    try {
      const result = await django<Conversation[]>('/api/albergues/conversaciones/');
      setConversations(result);
      setSelectedId(current => current ?? result[0]?.id ?? null);
      setError('');
    } catch (cause) {
      setError(errorMessage(cause));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadConversations();
    const timer = window.setInterval(() => void loadConversations(), 15000);
    return () => window.clearInterval(timer);
  }, [loadConversations]);

  useEffect(() => {
    if (role !== 'ADOPTANTE' || !shelterId) return;
    let active = true;
    void django<Conversation>('/api/albergues/conversaciones/', {
      method: 'POST',
      body: json({ albergue_id: shelterId }),
    }).then(conversation => {
      if (!active) return;
      setSelectedId(conversation.id);
      setNotice(`Conversación con ${conversation.albergue_nombre} lista.`);
      setSearchParams({}, { replace: true });
      void loadConversations();
    }).catch(cause => {
      if (active) setError(errorMessage(cause));
    });
    return () => { active = false; };
  }, [loadConversations, role, setSearchParams, shelterId]);

  useEffect(() => {
    if (!selectedId) {
      setMessages([]);
      return;
    }
    let active = true;
    const loadMessages = async () => {
      try {
        const result = await django<Message[]>(`/api/albergues/conversaciones/${selectedId}/mensajes/`);
        if (active) setMessages(result);
      } catch (cause) {
        if (active) setError(errorMessage(cause));
      }
    };
    void loadMessages();
    const timer = window.setInterval(() => void loadMessages(), 2500);
    return () => {
      active = false;
      window.clearInterval(timer);
    };
  }, [selectedId]);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth', block: 'end' });
  }, [messages]);

  async function sendMessage(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const contenido = draft.trim();
    if (!contenido || !selectedId || sending) return;
    setSending(true);
    setError('');
    try {
      await django<Message>(`/api/albergues/conversaciones/${selectedId}/mensajes/`, {
        method: 'POST',
        body: json({ contenido }),
      });
      setDraft('');
      const updated = await django<Message[]>(`/api/albergues/conversaciones/${selectedId}/mensajes/`);
      setMessages(updated);
      await loadConversations();
    } catch (cause) {
      setError(errorMessage(cause));
    } finally {
      setSending(false);
    }
  }

  if (!role || role === 'ADMIN') {
    return <section className="card"><h1>Mensajes</h1><p>La mensajería está disponible para adoptantes y albergues.</p></section>;
  }

  const selected = conversations.find(conversation => conversation.id === selectedId);
  return (
    <section className="card messages-page">
      <header className="messages-page__heading">
        <div>
          <p className="messages-page__eyebrow">Comunicación directa</p>
          <h1>Mensajes</h1>
          <p className="muted">Coordina entrevistas y visitas con el albergue de forma privada.</p>
        </div>
        {role === 'ADOPTANTE' && <Link className="button secondary" to="/adoptante/catalogo">Explorar mascotas</Link>}
      </header>
      <Feedback error={error} success={notice} />
      <div className="messages-layout">
        <aside className="messages-list" aria-label="Conversaciones">
          <h2>Conversaciones</h2>
          {loading ? <p role="status">Cargando conversaciones…</p> : conversations.length === 0 ? (
            <p className="messages-empty">Aún no tienes conversaciones. Abre una mascota del catálogo y selecciona «Escribir al albergue».</p>
          ) : conversations.map(conversation => (
            <button
              type="button"
              key={conversation.id}
              className={`messages-list__item${selectedId === conversation.id ? ' is-selected' : ''}`}
              onClick={() => { setSelectedId(conversation.id); setNotice(''); }}
            >
              <strong>{conversation.interlocutor}</strong>
              <span>{conversation.ultimo_mensaje || 'Inicia la conversación'}</span>
              <time dateTime={conversation.actualizada_en}>{formatTime(conversation.actualizada_en)}</time>
            </button>
          ))}
        </aside>
        <section className="messages-thread" aria-label="Mensajes de la conversación">
          {selected ? <>
            <header className="messages-thread__header">
              <div className="messages-avatar" aria-hidden="true">{selected.interlocutor.charAt(0).toLocaleUpperCase()}</div>
              <div><h2>{selected.interlocutor}</h2><p>{role === 'ALBERGUE' ? 'Adoptante' : 'Albergue de adopción'}</p></div>
            </header>
            <div className="messages-thread__body" aria-live="polite" aria-relevant="additions text">
              {messages.length === 0 ? (
                <div className="messages-thread__welcome"><span aria-hidden="true">♡</span><p>Comienza la conversación con un mensaje amable.</p></div>
              ) : messages.map(message => {
                const ownMessage = message.remitente_spring_id === Number(session.identity.id);
                return <article key={message.id} className={`message-bubble${ownMessage ? ' is-own' : ''}`}>
                  <p>{message.contenido}</p>
                  <time dateTime={message.creado_en}>{formatTime(message.creado_en)}</time>
                </article>;
              })}
              <div ref={bottomRef} />
            </div>
            <form className="messages-composer" onSubmit={event => void sendMessage(event)}>
              <label className="messages-sr-only" htmlFor="mensaje-contenido">Escribe tu mensaje</label>
              <textarea
                id="mensaje-contenido"
                rows={2}
                maxLength={4000}
                value={draft}
                onChange={event => setDraft(event.target.value)}
                placeholder="Escribe un mensaje…"
                required
              />
              <button type="submit" disabled={sending || !draft.trim()}>{sending ? 'Enviando…' : 'Enviar'}</button>
            </form>
          </> : <div className="messages-thread__empty">
            <span aria-hidden="true">✉</span>
            <h2>Elige una conversación</h2>
            <p>Los mensajes nuevos aparecerán aquí automáticamente.</p>
          </div>}
        </section>
      </div>
      <p className="messages-page__note">La conversación se actualiza automáticamente mientras esta página está abierta.</p>
    </section>
  );
}
