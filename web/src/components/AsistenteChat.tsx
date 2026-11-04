import { useEffect, useRef, useState, useSyncExternalStore } from 'react';
import { getSession, subscribe } from '../services/session';
import { errorMessage, json, spring } from '../services/http';
import { useDialogFocus } from '../services/useDialogFocus';
import '../styles/asistente-chat.css';

type Message = { role: 'user' | 'model'; text: string };

/** A new session mounts a new conversation; transcripts never leave memory except to answer. */
export default function AsistenteChat() {
  const session = useSyncExternalStore(subscribe, getSession);
  if (!session || !['ADOPTANTE', 'ALBERGUE'].includes(session.identity.rol)) return null;
  return <Conversation key={session.token} shelter={session.identity.rol === 'ALBERGUE'} />;
}

function Conversation({ shelter }: { shelter: boolean }) {
  const [open, setOpen] = useState(false);
  const [draft, setDraft] = useState('');
  const [careSpecies, setCareSpecies] = useState('');
  const [careBreed, setCareBreed] = useState('');
  const [messages, setMessages] = useState<Message[]>([]);
  const [sending, setSending] = useState(false);
  const [error, setError] = useState('');
  const controller = useRef<AbortController | null>(null);
  const end = useRef<HTMLDivElement>(null);
  useDialogFocus(open, () => setOpen(false));
  useEffect(() => () => controller.current?.abort(), []);
  useEffect(() => {
    end.current?.scrollIntoView({ block: 'nearest' });
  }, [messages, sending, error, open]);

  async function send(retry = false) {
    if (controller.current || (!retry && !draft.trim())) return;
    const history: Message[] = retry
      ? messages
      : [...messages, { role: 'user', text: draft.trim() }];
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
  }

  function askForCare() {
    const especie = careSpecies === 'perro' ? 'perro' : 'gato';
    const raza = careBreed.trim();
    setDraft(
      `¿Qué cuidados básicos debo considerar para un ${especie}${raza ? ` de raza ${raza}` : ''}? Incluye alimentación general, actividad, adaptación del hogar y cuándo consultar al veterinario. No diagnostiques ni indiques medicamentos.`
    );
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
        Asistente Petly
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
            <header>
              <h2 id="petly-chat-title">Asistente Petly</h2>
              <button aria-label="Cerrar asistente" onClick={() => setOpen(false)}>
                Cerrar
              </button>
            </header>
            <p className="petly-chat-intro">
              {shelter
                ? 'Te ayudo con tu panel, solicitudes y cuidados de las mascotas.'
                : 'Te ayudo con el catálogo, tu cuestionario, adopción y cuidados básicos.'}
            </p>
            {!shelter && (
              <fieldset className="petly-chat-care" disabled={sending || !!error}>
                <legend>Cuidados según especie y raza</legend>
                <label htmlFor="petly-care-species">Especie</label>
                <select
                  id="petly-care-species"
                  value={careSpecies}
                  onChange={event => setCareSpecies(event.target.value)}
                >
                  <option value="">Selecciona</option>
                  <option value="perro">Perro</option>
                  <option value="gato">Gato</option>
                </select>
                <label htmlFor="petly-care-breed">Raza (opcional)</label>
                <input
                  id="petly-care-breed"
                  value={careBreed}
                  maxLength={80}
                  onChange={event => setCareBreed(event.target.value)}
                  placeholder="Ej. mestizo, labrador"
                />
                <button type="button" disabled={!careSpecies} onClick={askForCare}>
                  Preparar consulta de cuidados
                </button>
              </fieldset>
            )}
            <div
              className="petly-chat-messages"
              role="log"
              aria-label="Conversación"
              aria-live="polite"
              aria-relevant="additions text"
            >
              {messages.map((message, index) => (
                <p key={index} className={`petly-chat-message petly-chat-message--${message.role}`}>
                  <strong>{message.role === 'user' ? 'Tú' : 'Petly'}</strong>
                  {message.text}
                </p>
              ))}
              {sending && <p role="status">Petly está preparando tu respuesta…</p>}
              <div ref={end} />
            </div>
            {error && (
              <div className="petly-chat-error" role="alert">
                <p>{error}</p>
                <button onClick={() => void send(true)}>Volver a intentar</button>
                <button
                  onClick={() => {
                    setDraft(messages.at(-1)?.text ?? '');
                    setMessages(messages.slice(0, -1));
                    setError('');
                  }}
                >
                  Editar consulta
                </button>
              </div>
            )}
            <form
              onSubmit={event => {
                event.preventDefault();
                void send();
              }}
            >
              <label htmlFor="petly-chat-input">Tu consulta</label>
              <textarea
                id="petly-chat-input"
                maxLength={2000}
                rows={2}
                value={draft}
                disabled={sending || !!error}
                onChange={event => setDraft(event.target.value)}
              />
              <div className="petly-chat-actions">
                <button type="button" onClick={reset}>
                  Nueva conversación
                </button>
                <button disabled={sending || !!error || !draft.trim()}>Enviar</button>
              </div>
            </form>
            <small>
              No compartas datos sensibles. El asistente orienta; las gestiones se realizan en los
              formularios de Petly.
            </small>
          </section>
        </div>
      )}
    </>
  );
}
