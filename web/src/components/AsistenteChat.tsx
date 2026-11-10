import { useEffect, useRef, useState, useSyncExternalStore } from 'react';
import { getSession, subscribe } from '../services/session';
import { errorMessage, json, spring } from '../services/http';
import { useDialogFocus } from '../services/useDialogFocus';
import '../styles/asistente-chat.css';

type Message = { role: 'user' | 'model'; text: string };
type Faq = { id: string; pregunta: string; respuesta: string };
type FaqCategory = { id: string; nombre: string; preguntas: Faq[] };
type CareAdvice = { categoria: string; titulo: string; contenido: string };
type CareResponse = {
  especie: string;
  raza: string;
  recomendacionEspecifica: boolean;
  mensaje: string;
  consejos: CareAdvice[];
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
  const [careSpecies, setCareSpecies] = useState('');
  const [careBreed, setCareBreed] = useState('');
  const [careAdvice, setCareAdvice] = useState<CareResponse | null>(null);
  const [careLoading, setCareLoading] = useState(false);
  const [careError, setCareError] = useState('');
  const [faqCategories, setFaqCategories] = useState<FaqCategory[]>([]);
  const [faqLoading, setFaqLoading] = useState(false);
  const [faqLoaded, setFaqLoaded] = useState(false);
  const [faqError, setFaqError] = useState('');
  const [faqSearch, setFaqSearch] = useState('');
  const [faqCategoryId, setFaqCategoryId] = useState('');
  const [selectedFaq, setSelectedFaq] = useState<Faq | null>(null);
  const [messages, setMessages] = useState<Message[]>([]);
  const [sending, setSending] = useState(false);
  const [error, setError] = useState('');
  const controller = useRef<AbortController | null>(null);
  const end = useRef<HTMLDivElement>(null);
  useDialogFocus(open, () => setOpen(false));
  useEffect(() => () => controller.current?.abort(), []);
  useEffect(() => {
    if (!open || shelter || faqLoaded) return;
    const request = new AbortController();
    setFaqLoading(true);
    setFaqError('');
    void spring<FaqCategory[]>('/api/asistente/preguntas-frecuentes', { signal: request.signal })
      .then(setFaqCategories)
      .catch(cause => {
        if (!request.signal.aborted) setFaqError(errorMessage(cause));
      })
      .finally(() => {
        if (!request.signal.aborted) {
          setFaqLoading(false);
          setFaqLoaded(true);
        }
      });
    return () => request.abort();
  }, [open, shelter, faqLoaded]);
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

  async function loadCareAdvice() {
    if (!careSpecies || careLoading) return;
    setCareLoading(true);
    setCareError('');
    setCareAdvice(null);
    const query = new URLSearchParams({ especie: careSpecies });
    if (careBreed.trim()) query.set('raza', careBreed.trim());
    try {
      setCareAdvice(await spring<CareResponse>(`/api/asistente/cuidados?${query.toString()}`));
    } catch (cause) {
      setCareError(errorMessage(cause));
    } finally {
      setCareLoading(false);
    }
  }

  const matchingFaqs = faqCategories.map(category => ({
    ...category,
    preguntas: category.preguntas.filter(item =>
      `${item.pregunta} ${item.respuesta}`.toLocaleLowerCase().includes(faqSearch.trim().toLocaleLowerCase())
    ),
  })).filter(category =>
    category.preguntas.length > 0 && (!faqCategoryId || category.id === faqCategoryId)
  );

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
              <section className="petly-chat-faq" aria-labelledby="petly-chat-faq-title">
                <h3 id="petly-chat-faq-title">Preguntas frecuentes</h3>
                <label htmlFor="petly-chat-faq-search">Buscar una pregunta</label>
                <input
                  id="petly-chat-faq-search"
                  value={faqSearch}
                  maxLength={120}
                  onChange={event => {
                    setFaqSearch(event.target.value);
                    setFaqCategoryId('');
                    setSelectedFaq(null);
                  }}
                  placeholder="Ej. solicitud, cuestionario"
                />
                <div className="petly-chat-faq-categories" aria-label="Categorías de preguntas">
                  {faqCategories.map(category => (
                    <button
                      type="button"
                      key={category.id}
                      aria-pressed={faqCategoryId === category.id}
                      onClick={() => {
                        setFaqCategoryId(faqCategoryId === category.id ? '' : category.id);
                        setSelectedFaq(null);
                      }}
                    >
                      {category.nombre}
                    </button>
                  ))}
                </div>
                {faqLoading && <p role="status">Cargando preguntas…</p>}
                {faqError && (
                  <div className="petly-chat-inline-error" role="alert">
                    <p>{faqError}</p>
                    <button type="button" onClick={() => setFaqLoaded(false)}>Reintentar</button>
                  </div>
                )}
                {!faqLoading && !faqError && matchingFaqs.length === 0 && (
                  <p role="status">
                    No encontré una pregunta coincidente. Prueba otra palabra o elige una categoría disponible arriba.
                  </p>
                )}
                {matchingFaqs.map(category => (
                  <div className="petly-chat-faq-category" key={category.id}>
                    <h4>{category.nombre}</h4>
                    {category.preguntas.map(item => (
                      <button
                        className="petly-chat-faq-question"
                        type="button"
                        key={item.id}
                        aria-pressed={selectedFaq?.id === item.id}
                        onClick={() => setSelectedFaq(item)}
                      >
                        {item.pregunta}
                      </button>
                    ))}
                  </div>
                ))}
                {selectedFaq && (
                  <article className="petly-chat-faq-answer" aria-live="polite">
                    <h4>{selectedFaq.pregunta}</h4>
                    <p>{selectedFaq.respuesta}</p>
                  </article>
                )}
              </section>
            )}
            {!shelter && (
              <fieldset className="petly-chat-care" disabled={sending || careLoading}>
                <legend>Cuidados según especie y raza</legend>
                <label htmlFor="petly-care-species">Especie</label>
                <select
                  id="petly-care-species"
                  value={careSpecies}
                  onChange={event => {
                    setCareSpecies(event.target.value);
                    setCareAdvice(null);
                    setCareError('');
                  }}
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
                  onChange={event => {
                    setCareBreed(event.target.value);
                    setCareAdvice(null);
                    setCareError('');
                  }}
                  placeholder="Ej. mestizo, labrador"
                />
                <button type="button" disabled={!careSpecies} onClick={() => void loadCareAdvice()}>
                  {careLoading ? 'Buscando cuidados…' : 'Consultar cuidados'}
                </button>
                {careError && <p className="petly-chat-inline-error" role="alert">{careError}</p>}
                {careAdvice && (
                  <div className="petly-chat-care-results" aria-live="polite">
                    <p>{careAdvice.mensaje}</p>
                    {careAdvice.consejos.map(advice => (
                      <article key={`${advice.categoria}-${advice.titulo}`}>
                        <h4>{advice.titulo}</h4>
                        <p>{advice.contenido}</p>
                      </article>
                    ))}
                  </div>
                )}
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
