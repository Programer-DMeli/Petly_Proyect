import { useCallback, useEffect, useRef, useState } from 'react';
import Feedback from '../../../../components/Feedback';
import { spring, json, errorMessage } from '../../../../services/http';
import type { Profile } from '../../../../services/types';

type QuestionnaireGuide = { campo: string; pregunta: string; orientacion: string; orden: number };
const GUIDE_FIELDS = ['vivienda', 'horas_fuera', 'presupuesto', 'ninos', 'otras_mascotas'];

export default function CuestionarioPage() {
  const [profile, setProfile] = useState<Profile | null>(null);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [busy, setBusy] = useState(false);
  const [showGuide, setShowGuide] = useState(true);
  const [guides, setGuides] = useState<QuestionnaireGuide[]>([]);
  const [guideError, setGuideError] = useState('');
  const [guideLoading, setGuideLoading] = useState(true);
  const [guideIndex, setGuideIndex] = useState(0);
  const formRef = useRef<HTMLFormElement>(null);
  const load = useCallback(async () => {
    setError('');
    try {
      setProfile(await spring<Profile>('/api/perfil'));
    } catch (e) {
      setError(errorMessage(e));
    }
  }, []);
  useEffect(() => {
    void load();
  }, [load]);
  useEffect(() => {
    const controller = new AbortController();
    void spring<QuestionnaireGuide[]>('/api/asistente/cuestionario/guia', { signal: controller.signal })
      .then(items => {
        const available = new Map(items.map(item => [item.campo, item]));
        setGuides(GUIDE_FIELDS.flatMap(field => {
          const item = available.get(field);
          return item ? [item] : [];
        }));
      })
      .catch(cause => {
        if (!controller.signal.aborted) setGuideError(errorMessage(cause));
      })
      .finally(() => {
        if (!controller.signal.aborted) setGuideLoading(false);
      });
    return () => controller.abort();
  }, []);
  async function save(finalizado: boolean) {
    if (!profile) return;
    setBusy(true);
    setError('');
    setSuccess('');
    try {
      setProfile(
        await spring<Profile>('/api/perfil', {
          method: 'PATCH',
          body: json({ ...profile, finalizado }),
        })
      );
      setSuccess(finalizado ? 'Cuestionario finalizado.' : 'Respuestas guardadas.');
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }
  const update = (patch: Partial<Profile>) => {
    if (profile) {
      setProfile({ ...profile, ...patch, finalizado: false });
      setSuccess('');
    }
  };
  const currentGuide = guides[guideIndex];
  function moveGuide(nextIndex: number) {
    const boundedIndex = Math.max(0, Math.min(guides.length - 1, nextIndex));
    setGuideIndex(boundedIndex);
    const field = guides[boundedIndex]?.campo;
    if (field) {
      formRef.current
        ?.querySelector<HTMLElement>(`[data-guide-field="${field}"] input, [data-guide-field="${field}"] select`)
        ?.focus();
    }
  }
  return (
    <section className="card">
      <h1>Mi cuestionario</h1>
      <p className="muted">
        Puedes guardar respuestas parciales. Tu información es privada y solo la compartes con los
        albergues que autorices.
      </p>
      <Feedback error={error} success={success} />
      {!profile ? (
        <button onClick={() => void load()}>Cargar cuestionario</button>
      ) : (
        <>
        <aside className="card" aria-labelledby="cuestionario-guia-titulo">
          <h2 id="cuestionario-guia-titulo">Guía para completar el cuestionario</h2>
          <p className="muted">
            Avanza campo por campo. Tus respuestas se conservan mientras completas el formulario;
            puedes guardar un borrador y continuar después.
          </p>
          <button
            type="button"
            className="secondary"
            aria-expanded={showGuide}
            aria-controls="cuestionario-pasos-guia"
            onClick={() => setShowGuide(value => !value)}
          >
            {showGuide ? 'Ocultar guía' : 'Mostrar guía'}
          </button>
          {showGuide && (
            <div id="cuestionario-pasos-guia" aria-live="polite">
              {guideLoading && <p role="status">Cargando orientación…</p>}
              {guideError && <p role="alert">No se pudo cargar la guía: {guideError}</p>}
              {!guideLoading && !guideError && currentGuide && (
                <div className="questionnaire-guide-step">
                  <p><strong>Paso {guideIndex + 1} de {guides.length}</strong></p>
                  <h3>{currentGuide.pregunta}</h3>
                  <p>{currentGuide.orientacion}</p>
                  <div className="actions">
                    <button
                      type="button"
                      className="secondary"
                      disabled={guideIndex === 0}
                      onClick={() => moveGuide(guideIndex - 1)}
                    >
                      Anterior
                    </button>
                    <button
                      type="button"
                      className="secondary"
                      disabled={guideIndex === guides.length - 1}
                      onClick={() => moveGuide(guideIndex + 1)}
                    >
                      Siguiente
                    </button>
                  </div>
                </div>
              )}
              {!guideLoading && !guideError && !currentGuide && (
                <p>No hay orientaciones disponibles para este cuestionario.</p>
              )}
            </div>
          )}
        </aside>
        <form
          ref={formRef}
          onSubmit={e => {
            e.preventDefault();
            void save(true);
          }}
        >
          <fieldset disabled={busy}>
            <label data-guide-field="vivienda">
              Vivienda
              <span className="muted">Describe casa o departamento y el espacio accesible para la mascota.</span>
              <input
                maxLength={120}
                value={profile.vivienda ?? ''}
                onChange={e => update({ vivienda: e.target.value || null })}
                placeholder="Casa, departamento…"
              />
            </label>
            <label data-guide-field="horas_fuera">
              Horas fuera de casa por día
              <span className="muted">Cuenta el tiempo habitual en que no habrá una persona en casa.</span>
              <input
                type="number"
                min={0}
                max={24}
                step="0.01"
                value={profile.horasFuera ?? ''}
                onChange={e =>
                  update({ horasFuera: e.target.value === '' ? null : Number(e.target.value) })
                }
              />
            </label>
            <label data-guide-field="presupuesto">
              Presupuesto mensual (S/)
              <span className="muted">Incluye alimento, higiene y cuidados veterinarios previsibles.</span>
              <input
                type="number"
                min={0}
                max={999999999.99}
                step="0.01"
                value={profile.presupuesto ?? ''}
                onChange={e =>
                  update({ presupuesto: e.target.value === '' ? null : Number(e.target.value) })
                }
              />
            </label>
            {(['tieneNinos', 'tieneOtrasMascotas'] as const).map(key => (
              <label key={key} data-guide-field={key === 'tieneNinos' ? 'ninos' : 'otras_mascotas'}>
                {key === 'tieneNinos' ? '¿Viven niños en tu hogar?' : '¿Tienes otras mascotas?'}
                <span className="muted">Responde según las personas y animales que convivirán con la mascota.</span>
                <select
                  value={profile[key] === null ? '' : String(profile[key])}
                  onChange={e =>
                    update({ [key]: e.target.value === '' ? null : e.target.value === 'true' })
                  }
                >
                  <option value="">Sin responder</option>
                  <option value="true">Sí</option>
                  <option value="false">No</option>
                </select>
              </label>
            ))}
            <div className="actions">
              <button type="button" className="secondary" onClick={() => void save(false)}>
                Guardar borrador
              </button>
              <button>Finalizar cuestionario</button>
            </div>
            <p>Estado: {profile.finalizado ? 'Finalizado' : 'Borrador'}</p>
          </fieldset>
        </form>
        </>
      )}
    </section>
  );
}
