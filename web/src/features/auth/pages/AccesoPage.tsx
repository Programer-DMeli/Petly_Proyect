import { useEffect, useId, useState, useSyncExternalStore } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import Feedback from '../../../components/Feedback';
import { spring, json, errorMessage, ApiError } from '../../../services/http';
import {
  loginSession,
  sessionHeaders,
  subscribe,
  getSessionNotice,
} from '../../../services/session';
import type { SessionReply } from '../../../services/session';
import { accessDestination } from '../accessDestination';
export default function AccesoPage({ register = false }: { register?: boolean }) {
  const formId = useId();
  const notice = useSyncExternalStore(subscribe, getSessionNotice);
  const [correo, setCorreo] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [confirm, setConfirm] = useState('');
  const [showConfirm, setShowConfirm] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const navigate = useNavigate();
  const accessState: unknown = useLocation().state;
  const [locks, setLocks] = useState<Record<string, number>>({});
  const [rateUntil, setRateUntil] = useState(0);
  const [now, setNow] = useState(Date.now());
  const [errorStatus, setErrorStatus] = useState(0);
  const normalizedEmail = correo.trim().toLowerCase();
  const accountRemaining = Math.max(0, Math.ceil(((locks[normalizedEmail] ?? 0) - now) / 1000));
  const rateRemaining = Math.max(0, Math.ceil((rateUntil - now) / 1000));
  const remaining = Math.max(register ? 0 : accountRemaining, rateRemaining);
  const waitLabel = `${Math.floor(remaining / 60)}:${String(remaining % 60).padStart(2, '0')}`;
  useEffect(() => {
    if (remaining === 0) return;
    const timer = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(timer);
  }, [remaining]);
  useEffect(() => {
    if (
      (errorStatus === 423 && accountRemaining === 0) ||
      (errorStatus === 429 && rateRemaining === 0)
    ) {
      setError('');
      setErrorStatus(0);
    }
  }, [accountRemaining, rateRemaining, errorStatus]);
  async function submit() {
    if (busy || remaining > 0) return;
    setErrorStatus(0);
    setError('');
    setSuccess('');
    setBusy(true);
    try {
      if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(normalizedEmail))
        throw new Error('Ingresa un correo válido, por ejemplo nombre@dominio.com.');
      if (!password.trim()) throw new Error('Ingresa tu contraseña.');
      if (register) {
        if (password.length < 8 || new TextEncoder().encode(password).length > 72)
          throw new Error('Usa al menos 8 caracteres y hasta 72 bytes UTF-8.');
        if (password !== confirm) throw new Error('Las contraseñas no coinciden.');
        await spring(
          '/api/auth/registro',
          { method: 'POST', body: json({ correo: normalizedEmail, password }) },
          false
        );
        setSuccess('Cuenta creada. Ya puedes iniciar sesión.');
        setPassword('');
        setConfirm('');
      } else {
        const { identity } = await loginSession(() =>
          spring<SessionReply>(
            '/api/auth/login',
            {
              method: 'POST',
              credentials: 'include',
              headers: sessionHeaders,
              body: json({ correo: normalizedEmail, password }),
            },
            false
          )
        );
        await navigate(accessDestination(identity.rol, accessState), { replace: true });
      }
    } catch (e) {
      setError(errorMessage(e));
      if (e instanceof ApiError && [423, 429].includes(e.status)) {
        const deadline = Date.now() + (e.retryAfterSeconds || 60) * 1000;
        setNow(Date.now());
        setErrorStatus(e.status);
        if (e.status === 423) setLocks(previous => ({ ...previous, [normalizedEmail]: deadline }));
        else setRateUntil(deadline);
      }
    } finally {
      setBusy(false);
    }
  }
  return (
    <section
      className={register ? 'card auth auth--register' : 'card auth auth--embedded'}
      id={register ? undefined : 'iniciar-sesion'}
      aria-labelledby={`${formId}-heading`}
    >
      {register && (
        <aside className="auth-register__aside" aria-label="Qué puedes hacer en Petly">
          <img
            className="auth-register__image"
            src="/images/mascotas-portada.webp"
            alt="Un perro y un gato que esperan encontrar un hogar"
          />
          <p className="auth-register__eyebrow">Adopción responsable</p>
          <h2>Una nueva historia puede empezar contigo</h2>
          <p className="auth-register__description">
            Crea tu cuenta y encuentra un compañero que encaje con tu estilo de vida.
          </p>
          <ul className="auth-register__benefits">
            <li><AuthIcon name="arrow" /><span>Explora mascotas de distintos albergues</span></li>
            <li><AuthIcon name="heart" /><span>Guarda tus mascotas favoritas</span></li>
            <li><AuthIcon name="check" /><span>Consulta el avance de tus solicitudes</span></li>
          </ul>
        </aside>
      )}
      <div className={register ? 'auth-register__content' : undefined}>
      {register ? (
        <>
          <h1 id={`${formId}-heading`}>Crear cuenta de adoptante</h1>
          <p className="muted">
            Regístrate con tu correo y crea una contraseña segura.
          </p>
        </>
      ) : (
        <h2 id={`${formId}-heading`}>Inicia sesión</h2>
      )}
      <Feedback error={error || (register ? '' : notice)} success={success} />
      {remaining > 0 && (
        <p role="status" className="feedback error">
          {rateRemaining > 0 ? 'Límite de peticiones alcanzado' : 'Cuenta bloqueada temporalmente'}.
          Puedes volver a intentar en {waitLabel}.
        </p>
      )}
      <form
        noValidate
        onSubmit={e => {
          e.preventDefault();
          void submit();
        }}
      >
        <fieldset disabled={busy}>
          <label htmlFor={`${formId}-email`}>{register ? 'Correo' : 'Correo electrónico'}</label>
          <div className={register ? undefined : 'auth__input auth__input--icon'}>
            {!register && <AuthIcon name="mail" />}
            <input
              id={`${formId}-email`}
              type="email"
              autoComplete="email"
              placeholder={register ? undefined : 'Ej. tucorreo@correo.com'}
              required
              value={correo}
              onChange={e => {
                setCorreo(e.target.value);
                setNow(Date.now());
              }}
            />
          </div>
          <label htmlFor={`${formId}-password`}>Contraseña</label>
          <div className={register ? 'auth__input auth__input--register' : 'auth__input auth__input--icon'}>
            {!register && <AuthIcon name="lock" />}
            <input
              id={`${formId}-password`}
              type={showPassword ? 'text' : 'password'}
              autoComplete={register ? 'new-password' : 'current-password'}
              required
              minLength={register ? 8 : undefined}
              value={password}
              onChange={e => setPassword(e.target.value)}
              aria-describedby={register ? `${formId}-password-hint` : undefined}
            />
            <button
              className="auth__visibility"
              type="button"
              aria-label={showPassword ? 'Ocultar contraseña' : 'Mostrar contraseña'}
              aria-controls={`${formId}-password`}
              aria-pressed={showPassword}
              onClick={() => setShowPassword(visible => !visible)}
            >
              <AuthIcon name={showPassword ? 'eye-off' : 'eye'} />
            </button>
          </div>
          {register && <small className="auth-register__hint" id={`${formId}-password-hint`}>Mínimo 8 caracteres</small>}
          {register && (
            <>
              <label htmlFor={`${formId}-confirm`}>Confirmar contraseña</label>
              <div className="auth__input auth__input--register">
                <input
                  id={`${formId}-confirm`}
                  type={showConfirm ? 'text' : 'password'}
                  autoComplete="new-password"
                  required
                  value={confirm}
                  onChange={e => setConfirm(e.target.value)}
                />
                <button
                  className="auth__visibility"
                  type="button"
                  aria-label={showConfirm ? 'Ocultar confirmación' : 'Mostrar confirmación'}
                  aria-controls={`${formId}-confirm`}
                  aria-pressed={showConfirm}
                  onClick={() => setShowConfirm(visible => !visible)}
                >
                  <AuthIcon name={showConfirm ? 'eye-off' : 'eye'} />
                </button>
              </div>
            </>
          )}
          <button className="auth__submit" type="submit" disabled={remaining > 0}>
            {busy ? 'Procesando…' : register ? 'Crear cuenta' : 'Entrar'}
            {!register && !busy && <AuthIcon name="arrow" />}
          </button>
        </fieldset>
      </form>
      {register ? (
        <div className="auth-register__actions">
          <p>¿Ya tienes una cuenta?</p>
          <Link className="auth-register__login" to="/acceso" state={accessState}>Iniciar sesión</Link>
          <Link className="auth-register__recovery" to="/recuperacion" state={accessState}>
            <AuthIcon name="lock" /> ¿Olvidaste tu contraseña?
          </Link>
        </div>
      ) : (
        <div className="actions">
          <Link to="/recuperacion" state={accessState}>Recuperar contraseña</Link>
          <Link to="/registro" state={accessState}>Crear cuenta</Link>
        </div>
      )}
      </div>
    </section>
  );
}

function AuthIcon({ name }: { name: 'mail' | 'lock' | 'eye' | 'eye-off' | 'arrow' | 'heart' | 'check' }) {
  return (
    <svg
      viewBox="0 0 24 24"
      width="24"
      height="24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.7"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      {name === 'mail' && <path d="M3 5h18v14H3zM3 6l9 7 9-7" />}
      {name === 'lock' && <path d="M5 10h14v11H5zM8 10V7a4 4 0 0 1 8 0v3m-4 4v3" />}
      {(name === 'eye' || name === 'eye-off') && (
        <>
          <path d="M2 12s3.5-6 10-6 10 6 10 6-3.5 6-10 6-10-6-10-6Z" />
          <circle cx="12" cy="12" r="2.5" />
          {name === 'eye-off' && <path d="m3 3 18 18" />}
        </>
      )}
      {name === 'arrow' && <path d="M3 12h17m-7-7 7 7-7 7" />}
      {name === 'heart' && <path d="M20.8 4.6a5.5 5.5 0 0 0-7.8 0L12 5.7l-1.1-1.1a5.5 5.5 0 0 0-7.8 7.8l1.1 1.1L12 21l7.8-7.5 1.1-1.1a5.5 5.5 0 0 0-.1-7.8Z" />}
      {name === 'check' && <path d="m5 12 4 4L19 6" />}
    </svg>
  );
}
