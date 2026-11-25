import { useCallback, useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import Feedback from '../../../../components/Feedback';
import { spring, json, errorMessage } from '../../../../services/http';
import type { Profile } from '../../../../services/types';

type QuestionnaireGuide = { campo: string; pregunta: string; orientacion: string; orden: number };
const GUIDE_FIELDS = ['vivienda', 'horas_fuera', 'presupuesto', 'ninos', 'otras_mascotas'] as const;
type GuideField = (typeof GUIDE_FIELDS)[number];
const FIELD_LABELS: Record<GuideField, string> = {
  vivienda: 'Vivienda',
  horas_fuera: 'Horas fuera de casa',
  presupuesto: 'Presupuesto mensual',
  ninos: 'Niños en el hogar',
  otras_mascotas: 'Otras mascotas',
};

function hasAnswer(profile: Profile, field: GuideField) {
  if (field === 'vivienda') return Boolean(profile.vivienda?.trim());
  if (field === 'horas_fuera') return profile.horasFuera !== null;
  if (field === 'presupuesto') return profile.presupuesto !== null;
  if (field === 'ninos') return profile.tieneNinos !== null;
  return profile.tieneOtrasMascotas !== null;
}

function fieldValidationMessage(profile: Profile, field: GuideField) {
  if (field === 'horas_fuera' && profile.horasFuera !== null) {
    if (!Number.isFinite(profile.horasFuera) || profile.horasFuera < 0 || profile.horasFuera > 24)
      return 'Ingresa un valor entre 0 y 24 horas.';
  }
  if (field === 'presupuesto' && profile.presupuesto !== null) {
    if (!Number.isFinite(profile.presupuesto) || profile.presupuesto < 0 || profile.presupuesto > 999999999.99)
      return 'Ingresa un presupuesto válido igual o mayor que S/ 0.';
  }
  return '';
}

const FALLBACK_GUIDES: Record<GuideField, Pick<QuestionnaireGuide, 'pregunta' | 'orientacion'>> = {
  vivienda: {
    pregunta: '¿En qué tipo de vivienda vivirá la mascota?',
    orientacion: 'Describe si es casa o departamento y qué espacios estarán disponibles. Responde según tu vivienda real.',
  },
  horas_fuera: {
    pregunta: '¿Cuántas horas estará sola la mascota en un día habitual?',
    orientacion: 'Considera el tiempo en que no habrá una persona en casa para acompañarla.',
  },
  presupuesto: {
    pregunta: '¿Qué presupuesto mensual puedes destinar a sus cuidados?',
    orientacion: 'Incluye alimento, higiene y cuidados veterinarios previsibles.',
  },
  ninos: {
    pregunta: '¿Viven niños en el hogar?',
    orientacion: 'Responde pensando en las personas que convivirán con la mascota.',
  },
  otras_mascotas: {
    pregunta: '¿Tienes otras mascotas en casa?',
    orientacion: 'Considera a los animales que compartirán los espacios con el nuevo compañero.',
  },
};

export default function CuestionarioPage() {
  const [profile, setProfile] = useState<Profile | null>(null);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [busy, setBusy] = useState(false);
  const [dirty, setDirty] = useState(false);
  const [stepErrors, setStepErrors] = useState<Partial<Record<GuideField, string>>>({});
  const [reviewFields, setReviewFields] = useState<GuideField[]>([]);
  const [guides, setGuides] = useState<QuestionnaireGuide[]>([]);
  const [guideError, setGuideError] = useState('');
  const [guideIndex, setGuideIndex] = useState(0);
  const [editingFinalized, setEditingFinalized] = useState(false);
  const questionRef = useRef<HTMLHeadingElement>(null);

  const load = useCallback(async () => {
    setError('');
    try {
      setProfile(await spring<Profile>('/api/perfil'));
      setDirty(false);
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
      .catch(() => {
        if (!controller.signal.aborted) setGuideError('Mostramos una orientación general para que puedas continuar.');
      });
    return () => controller.abort();
  }, []);

  async function save(finalizado: boolean) {
    if (!profile || busy) return;
    setBusy(true);
    setError('');
    setSuccess('');
    try {
      const updated = await spring<Profile>('/api/perfil', {
        method: 'PATCH',
        body: json({ ...profile, finalizado }),
      });
      setProfile(updated);
      setDirty(false);
      setSuccess(finalizado ? 'Cuestionario finalizado.' : 'Borrador guardado. Puedes continuar después.');
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }

  const update = (patch: Partial<Profile>) => {
    if (!profile) return;
    const updatedProfile = { ...profile, ...patch, finalizado: false };
    setProfile(updatedProfile);
    setDirty(true);
    setSuccess('');
    setStepErrors(previous => ({ ...previous, [stepField]: undefined }));
    setReviewFields(previous =>
      previous.filter(field => field !== stepField || !hasAnswer(updatedProfile, field) || Boolean(fieldValidationMessage(updatedProfile, field)))
    );
  };

  function validateCurrentStep() {
    if (!profile) return false;
    const message = fieldValidationMessage(profile, stepField) ||
      (reviewFields.includes(stepField) && !hasAnswer(profile, stepField)
        ? 'Completa esta respuesta para finalizar el cuestionario.'
        : '');
    setStepErrors(previous => ({ ...previous, [stepField]: message || undefined }));
    setError('');
    return !message;
  }

  function nextStep() {
    if (!validateCurrentStep()) return;
    moveGuide(guideIndex + 1);
  }

  function finishQuestionnaire() {
    if (!profile) return;
    const issues = GUIDE_FIELDS.filter(field =>
      !hasAnswer(profile, field) || Boolean(fieldValidationMessage(profile, field))
    );
    setReviewFields(issues);
    setStepErrors(Object.fromEntries(GUIDE_FIELDS.map(field => [
      field,
      fieldValidationMessage(profile, field) || (!hasAnswer(profile, field)
        ? 'Completa esta respuesta para finalizar el cuestionario.'
        : undefined),
    ])));
    setError('');
    setSuccess('');
    if (issues.length > 0) return;
    void save(true);
  }

  function moveGuide(nextIndex: number, focusAnswer = false) {
    const boundedIndex = Math.max(0, Math.min(GUIDE_FIELDS.length - 1, nextIndex));
    setError('');
    if (boundedIndex === guideIndex) {
      if (focusAnswer) focusCurrentAnswer(GUIDE_FIELDS[boundedIndex]);
      return;
    }
    setGuideIndex(boundedIndex);
    window.requestAnimationFrame(() => {
      if (focusAnswer) focusCurrentAnswer(GUIDE_FIELDS[boundedIndex]);
      else questionRef.current?.focus();
    });
  }

  function focusCurrentAnswer(field: GuideField) {
    const container = document.querySelector<HTMLElement>(`[data-guide-field="${field}"]`);
    const answer = container?.querySelector<HTMLElement>(
      'input:not([type="range"]), select, input[type="range"]'
    );
    (answer ?? questionRef.current)?.focus();
  }

  const stepField = GUIDE_FIELDS[guideIndex];
  const currentGuide = guides.find(item => item.campo === stepField) ?? FALLBACK_GUIDES[stepField];
  const answeredCount = profile
    ? [profile.vivienda?.trim(), profile.horasFuera, profile.presupuesto, profile.tieneNinos, profile.tieneOtrasMascotas]
        .filter(value => value !== null && value !== undefined && value !== '').length
    : 0;
  const progress = Math.round(((guideIndex + 1) / GUIDE_FIELDS.length) * 100);
  const finalStep = guideIndex === GUIDE_FIELDS.length - 1;

  return (
    <section className="card questionnaire-page" aria-labelledby="questionnaire-title">
      <header className="questionnaire-page__header">
        <p className="workspace-eyebrow">MI PERFIL</p>
        <h1 id="questionnaire-title">Cuestionario de estilo de vida</h1>
        <p className="muted">
          Tus respuestas orientan la compatibilidad con las mascotas; no garantizan la aprobación de una adopción.
          Puedes guardar un borrador y continuar después.
        </p>
        <p className="questionnaire-page__privacy">Tu información es privada y solo la compartes con los albergues que autorices.</p>
      </header>

      {!profile && <Feedback error={error} success={success} />}

      {!profile ? (
        <button type="button" onClick={() => void load()}>Cargar cuestionario</button>
      ) : (
        <>
          {profile.finalizado && !dirty && !editingFinalized ? (
            <section className="questionnaire-complete" role="status" aria-labelledby="questionnaire-complete-title">
              <span className="questionnaire-complete__icon" aria-hidden="true">✓</span>
              <p className="workspace-eyebrow">MI PERFIL</p>
              <h2 id="questionnaire-complete-title">¡Cuestionario completado!</h2>
              <p>
                Tus respuestas están guardadas y pueden orientar la compatibilidad con las mascotas.
                Cuando presentes una solicitud, podrás autorizar al albergue para consultarlas.
              </p>
              <div className="questionnaire-complete__actions">
                <Link className="button" to="/adoptante/catalogo">Explorar mascotas</Link>
                <button type="button" className="secondary" onClick={() => setEditingFinalized(true)}>
                  Revisar mis respuestas
                </button>
              </div>
            </section>
          ) : (
          <>
          <div className="questionnaire-progress" aria-label="Avance del cuestionario">
            <div className="questionnaire-progress__summary">
              <strong>Paso {guideIndex + 1} de {GUIDE_FIELDS.length}</strong>
              <span>{answeredCount} de {GUIDE_FIELDS.length} respuestas completadas</span>
            </div>
            <div
              className="questionnaire-progress__track"
              role="progressbar"
              aria-label="Progreso por pasos"
              aria-valuemin={0}
              aria-valuemax={GUIDE_FIELDS.length}
              aria-valuenow={guideIndex + 1}
            >
              <span style={{ width: `${progress}%` }} />
            </div>
            {dirty && <p className="questionnaire-progress__save-state">Tienes cambios sin guardar</p>}
            {guideError && <p className="questionnaire-progress__save-state" role="status">{guideError}</p>}
          </div>

          <form
            className="questionnaire-step"
            noValidate
            onSubmit={event => {
              event.preventDefault();
              if (finalStep) finishQuestionnaire();
              else nextStep();
            }}
          >
            <fieldset disabled={busy}>
              <div className="questionnaire-step__heading" aria-live="polite">
                <span className="questionnaire-step__number">{String(guideIndex + 1).padStart(2, '0')}</span>
                <div>
                  <h2 ref={questionRef} tabIndex={-1}>{currentGuide.pregunta}</h2>
                  <p>{currentGuide.orientacion}</p>
                </div>
              </div>

              {stepField === 'vivienda' && (
                <div className="questionnaire-answer" data-guide-field="vivienda">
                  <label htmlFor="questionnaire-vivienda">Describe tu vivienda</label>
                  <input
                    id="questionnaire-vivienda"
                    maxLength={120}
                    value={profile.vivienda ?? ''}
                    onChange={event => update({ vivienda: event.target.value || null })}
                    placeholder="Casa, departamento y espacios disponibles…"
                    autoComplete="off"
                  />
                  {stepErrors.vivienda && <small className="questionnaire-field-error" role="alert">{stepErrors.vivienda}</small>}
                </div>
              )}

              {stepField === 'horas_fuera' && (
                <div className="questionnaire-answer" data-guide-field="horas_fuera">
                  <label htmlFor="questionnaire-horas-slider">Horas fuera de casa por día</label>
                  <div className="questionnaire-hours">
                    <output className="questionnaire-hours__value" htmlFor="questionnaire-horas-slider">
                      {profile.horasFuera === null
                        ? 'Elige tus horas'
                        : `${profile.horasFuera} ${profile.horasFuera === 1 ? 'hora' : 'horas'} al día`}
                    </output>
                    <input
                      id="questionnaire-horas-slider"
                      className="questionnaire-hours__slider"
                      type="range"
                      min={0}
                      max={24}
                      step="0.5"
                      value={profile.horasFuera ?? 0}
                      aria-valuetext={profile.horasFuera === null ? 'Sin responder; mueve el control para elegir' : `${profile.horasFuera} horas al día`}
                      onChange={event => update({ horasFuera: Number(event.target.value) })}
                    />
                    <div className="questionnaire-hours__scale" aria-hidden="true">
                      <span>0</span><span>6</span><span>12</span><span>18</span><span>24 horas</span>
                    </div>
                  </div>
                  <label className="questionnaire-hours__manual-label" htmlFor="questionnaire-horas-number">
                    O escribe una cantidad exacta
                  </label>
                  <div className="questionnaire-answer__with-unit questionnaire-hours__number">
                    <input
                      id="questionnaire-horas-number"
                      type="number"
                      min={0}
                      max={24}
                      step="0.01"
                      value={profile.horasFuera ?? ''}
                      onChange={event => update({ horasFuera: event.target.value === '' ? null : Number(event.target.value) })}
                      placeholder="Por ejemplo, 8"
                    />
                    <span>horas al día</span>
                  </div>
                  {stepErrors.horas_fuera && <small className="questionnaire-field-error" role="alert">{stepErrors.horas_fuera}</small>}
                </div>
              )}

              {stepField === 'presupuesto' && (
                <div className="questionnaire-answer questionnaire-answer--budget" data-guide-field="presupuesto">
                  <label htmlFor="questionnaire-presupuesto">Presupuesto mensual para sus cuidados</label>
                  <div className="questionnaire-answer__with-unit">
                    <span>S/</span>
                    <input
                      id="questionnaire-presupuesto"
                      type="number"
                      min={0}
                      max={999999999.99}
                      step="0.01"
                      value={profile.presupuesto ?? ''}
                      onChange={event => update({ presupuesto: event.target.value === '' ? null : Number(event.target.value) })}
                      placeholder="Monto mensual"
                    />
                  </div>
                  {stepErrors.presupuesto && <small className="questionnaire-field-error" role="alert">{stepErrors.presupuesto}</small>}
                </div>
              )}

              {(stepField === 'ninos' || stepField === 'otras_mascotas') && (() => {
                const key = stepField === 'ninos' ? 'tieneNinos' : 'tieneOtrasMascotas';
                const label = stepField === 'ninos' ? '¿Viven niños en tu hogar?' : '¿Tienes otras mascotas?';
                return (
                  <div className="questionnaire-answer questionnaire-answer--choice" data-guide-field={stepField}>
                    <label htmlFor={`questionnaire-${stepField}`}>{label}</label>
                    <select
                      id={`questionnaire-${stepField}`}
                      value={profile[key] === null ? '' : String(profile[key])}
                      onChange={event => {
                        const value = event.target.value === '' ? null : event.target.value === 'true';
                        if (key === 'tieneNinos') update({ tieneNinos: value });
                        else update({ tieneOtrasMascotas: value });
                      }}
                    >
                      <option value="">Selecciona una opción</option>
                      <option value="true">Sí</option>
                      <option value="false">No</option>
                    </select>
                    {stepErrors[stepField] && <small className="questionnaire-field-error" role="alert">{stepErrors[stepField]}</small>}
                  </div>
                );
              })()}

              {(error || success) && <Feedback error={error} success={success} />}

              {reviewFields.length > 0 && (
                <div className="questionnaire-review" role="alert" aria-live="assertive">
                  <div className="questionnaire-review__heading">
                    <span className="questionnaire-review__icon" aria-hidden="true">!</span>
                    <div>
                      <strong>Revisa estas respuestas para finalizar</strong>
                      <p>Completa los campos pendientes o corrige los valores indicados.</p>
                    </div>
                  </div>
                  <ul>
                    {reviewFields.map(field => (
                      <li key={field}>
                        <button type="button" onClick={() => moveGuide(GUIDE_FIELDS.indexOf(field), true)}>
                          <span className="questionnaire-review__field">{FIELD_LABELS[field]}</span>
                          <span className="questionnaire-review__message">
                            {fieldValidationMessage(profile, field) || stepErrors[field] || 'Respuesta pendiente'}
                          </span>
                          <span className="questionnaire-review__arrow" aria-hidden="true">↗</span>
                        </button>
                      </li>
                    ))}
                  </ul>
                </div>
              )}

              <div className="questionnaire-step__actions">
                <button
                  type="button"
                  className="secondary"
                  disabled={guideIndex === 0 || busy}
                  onClick={() => moveGuide(guideIndex - 1)}
                >
                  Anterior
                </button>
                <button
                  type="button"
                  className="secondary questionnaire-step__save"
                  disabled={busy || !dirty}
                  onClick={() => void save(false)}
                >
                  {busy ? 'Guardando…' : 'Guardar borrador'}
                </button>
                <button type="submit" disabled={busy}>
                  {busy ? 'Procesando…' : finalStep ? 'Finalizar cuestionario' : 'Siguiente'}
                </button>
              </div>
              <p className="questionnaire-step__status" role="status">
                {profile.finalizado && !dirty ? 'Cuestionario finalizado' : dirty ? 'Recuerda guardar tus cambios antes de salir' : 'Tus respuestas están guardadas'}
              </p>
            </fieldset>
          </form>
          </>
          )}
        </>
      )}
    </section>
  );
}
