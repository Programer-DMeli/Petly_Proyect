import { useEffect, useRef, useState, useSyncExternalStore } from 'react';
import { Link } from 'react-router-dom';
import { getSession, subscribe } from '../services/session';
import { errorMessage, json, spring } from '../services/http';
import { useDialogFocus } from '../services/useDialogFocus';
import AsistenteRespuesta from './AsistenteRespuesta';
import '../styles/asistente-chat.css';

type Message = { role: 'user' | 'model'; text: string };
const avatar = '/images/asistente-petly.png';
const suggestions = {
  shelter: [
    '¿Cómo registro una mascota?',
    '¿Dónde reviso las solicitudes?',
    '¿Cómo actualizo los datos del albergue?',
    '¿Cómo redacto el perfil de una mascota?',
  ],
  adopter: [
    '¿Cómo empiezo una adopción?',
    '¿Cómo completo mi cuestionario?',
    '¿Dónde veo mis solicitudes?',
    '¿Cómo preparo mi hogar para una mascota?',
  ],
};

/** A new session mounts a new conversation; transcripts never leave memory except to answer. */
export default function AsistenteChat() {
  const session = useSyncExternalStore(subscribe, getSession);
  if (!session || !['ADOPTANTE', 'ALBERGUE'].includes(session.identity.rol)) return null;
  return <Conversation key={session.token} shelter={session.identity.rol === 'ALBERGUE'} />;
}
function Conversation({ shelter }: { shelter: boolean }) {
  const [open, setOpen] = useState(false);
  const [draft, setDraft] = useState('');
  const [messages, setMessages] = useState<Message[]>([]);
  const [sending, setSending] = useState(false);
  const [error, setError] = useState('');
  const controller = useRef<AbortController | null>(null);
  const log = useRef<HTMLDivElement>(null);
  const lastMessage = useRef<HTMLDivElement>(null);
  const input = useRef<HTMLTextAreaElement>(null);
  const hasConversation = messages.length > 0;
  useDialogFocus(open, () => setOpen(false));
  useEffect(() => () => controller.current?.abort(), []);
  useEffect(() => {
    const history = log.current;
    if (!history) return;
    const latest = lastMessage.current;
    if (!sending && !error && messages.at(-1)?.role === 'model' && latest) {
      // Start at the top of a reply that cannot fit in the visible history.
      history.scrollTop = Math.min(latest.offsetTop, history.scrollHeight - history.clientHeight);
    } else {
      history.scrollTop = history.scrollHeight;
    }
  }, [messages, sending, error, open]);

  async function send(question: string | null) {
    if (controller.current || question === '') return;
    const history: Message[] =
      question === null ? messages : [...messages, { role: 'user', text: question }];
    setMessages(history);
    setDraft('');
    setError('');
    setSending(true);
    const active = new AbortController();
    controller.current = active;
    let timedOut = false;
    const timeout = window.setTimeout(() => {
      timedOut = true;
      active.abort();
    }, 65000);
    // Trim complete pairs, keeping the current question and alternating roles.
    const context = history.slice(-21);
    while (context.reduce((size, message) => size + message.text.length, 0) > 16000)
      context.splice(0, 2);
    try {
      const result = await spring<{ reply: string }>('/api/asistente/chat', {
        method: 'POST',
        body: json({ messages: context, platform: 'web' }),
        signal: active.signal,
      });
      if (!active.signal.aborted) {
        if (typeof result.reply !== 'string' || !result.reply.trim() || result.reply.length > 2000)
          throw new Error('No pudimos completar la respuesta. Vuelve a intentar.');
        setMessages(
          [...history, { role: 'model' as const, text: result.reply.trim() }].slice(-100)
        );
      }
    } catch (cause) {
      if (!active.signal.aborted || timedOut)
        setError(
          timedOut ? 'La respuesta tardó demasiado. Puedes volver a intentar.' : errorMessage(cause)
        );
    } finally {
      window.clearTimeout(timeout);
      if (controller.current === active) {
        controller.current = null;
        setSending(false);
      }
    }
  }

  function reset() {
    controller.current?.abort();
    controller.current = null;
    setMessages([]);
    setDraft('');
    setSending(false);
    setError('');
    input.current?.focus();
  }

  return (
    <>
      <button
        className="petly-chat-launcher"
        aria-label="Abrir asistente Petly"
        aria-expanded={open}
        aria-controls="petly-chat"
        onClick={() => setOpen(true)}
      >
        <img src={avatar} alt="" />
      </button>
      {open && (
        <div className="petly-chat-backdrop">
          <section
            className="petly-chat"
            id="petly-chat"
            role="dialog"
            aria-modal="true"
            aria-labelledby="petly-chat-title"
          >
            <header className="petly-chat-header">
              <img className="petly-chat-avatar" src={avatar} alt="" />
              <h2 id="petly-chat-title">Asistente Petly</h2>
              <button
                className="petly-chat-close"
                type="button"
                aria-label="Cerrar asistente"
                onClick={() => setOpen(false)}
              >
                <span aria-hidden="true">×</span>
              </button>
            </header>
            {!hasConversation && (
              <div className="petly-chat-welcome-scroll">
                <div className="petly-chat-welcome">
                  <img
                    className="petly-chat-avatar petly-chat-avatar--welcome"
                    src={avatar}
                    alt=""
                  />
                  <h3>¡Hola! Soy Petly</h3>
                  <p>
                    {shelter
                      ? '¿En qué te ayudo con tu albergue?'
                      : '¿En qué te ayudo con tu adopción?'}
                  </p>
                  <ul className="petly-chat-suggestions" aria-label="Preguntas sugeridas">
                    {suggestions[shelter ? 'shelter' : 'adopter'].map(question => (
                      <li key={question}>
                        <button
                          type="button"
                          onClick={() => {
                            log.current?.focus({ preventScroll: true });
                            void send(question);
                          }}
                        >
                          {question}
                          <span aria-hidden="true">↗</span>
                        </button>
                      </li>
                    ))}
                  </ul>
                </div>
              </div>
            )}
            <div
              className="petly-chat-messages"
              ref={log}
              role="log"
              aria-label="Conversación"
              aria-live="polite"
              aria-relevant="additions text"
              tabIndex={hasConversation ? 0 : -1}
            >
              {messages.map((message, index) => (
                <div
                  key={index}
                  ref={index === messages.length - 1 ? lastMessage : undefined}
                  className={`petly-chat-message petly-chat-message--${message.role}`}
                >
                  <strong className="petly-chat-author">
                    {message.role === 'user' ? 'Tú' : 'Petly'}
                  </strong>
                  {message.role === 'model' ? (
                    <AsistenteRespuesta text={message.text} />
                  ) : (
                    <p>{message.text}</p>
                  )}
                </div>
              ))}
              {sending && (
                <p className="petly-chat-status" role="status">
                  Petly está preparando tu respuesta…
                </p>
              )}
            </div>
            {error && (
              <div className="petly-chat-error" role="alert">
                <p>{error}</p>
                <div className="petly-chat-error-actions">
                  <button
                    type="button"
                    onClick={() => {
                      log.current?.focus({ preventScroll: true });
                      void send(null);
                    }}
                  >
                    Volver a intentar
                  </button>
                  <button
                    type="button"
                    onClick={() => {
                      setDraft(messages.at(-1)?.text ?? '');
                      setMessages(messages.slice(0, -1));
                      setError('');
                      input.current?.focus();
                    }}
                  >
                    Editar consulta
                  </button>
                </div>
              </div>
            )}
            <footer className="petly-chat-footer">
              {(!shelter || hasConversation) && (
                <div className="petly-chat-actions">
                  {!shelter && (
                    <Link to="/adoptante/cuestionario" onClick={() => setOpen(false)}>
                      Guía del cuestionario
                    </Link>
                  )}
                  {hasConversation && (
                    <button type="button" onClick={reset}>
                      Nueva conversación
                    </button>
                  )}
                </div>
              )}
              <form
                className="petly-chat-composer"
                onSubmit={event => {
                  event.preventDefault();
                  if (sending || error || !draft.trim()) return;
                  input.current?.focus({ preventScroll: true });
                  void send(draft.trim());
                }}
              >
                <label className="visually-hidden" htmlFor="petly-chat-input">
                  Tu consulta
                </label>
                <textarea
                  ref={input}
                  id="petly-chat-input"
                  placeholder="Escribe tu pregunta…"
                  maxLength={2000}
                  rows={1}
                  value={draft}
                  readOnly={sending || !!error}
                  onChange={event => setDraft(event.target.value)}
                />
                <button
                  type="submit"
                  className="petly-chat-send"
                  aria-label="Enviar"
                  disabled={sending || !!error || !draft.trim()}
                >
                  <span aria-hidden="true">↑</span>
                </button>
              </form>
              <small>No compartas datos sensibles.</small>
            </footer>
          </section>
        </div>
      )}
    </>
  );
}
