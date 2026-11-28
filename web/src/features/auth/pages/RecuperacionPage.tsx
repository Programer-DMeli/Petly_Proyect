import { useEffect, useId, useState } from 'react';
import { Link, useLocation, useSearchParams } from 'react-router-dom';
import Feedback from '../../../components/Feedback';
import { spring, json, errorMessage } from '../../../services/http';
import { resetWebPassword, sessionHeaders } from '../../../services/session';

type RecoveryStep = 'request' | 'reset';

export default function RecuperacionPage() {
  const formId = useId();
  const accessState: unknown = useLocation().state;
  const [params] = useSearchParams();
  const incoming = params.get('token') ?? '';
  const [step, setStep] = useState<RecoveryStep>(() => (incoming ? 'reset' : 'request'));
  const [token, setToken] = useState(incoming);
  const [correo, setCorreo] = useState('');
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirm, setShowConfirm] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  useEffect(() => {
    if (incoming) window.history.replaceState(null, '', '/recuperacion');
  }, [incoming]);

  function changeStep(next: RecoveryStep) {
    setStep(next);
    setError('');
    setSuccess('');
  }

  async function submit(reset: boolean) {
    if (busy) return;
    setBusy(true);
    setError('');
    setSuccess('');
    try {
      if (reset && password !== confirm) throw new Error('Las contraseñas no coinciden.');
      const enviar = () => spring(
        reset ? '/api/auth/password/reset/confirm' : '/api/auth/password/reset?canal=web',
        {
          method: 'POST',
          ...(reset ? { credentials: 'include' as const, headers: sessionHeaders } : {}),
          body: json(
            reset ? { token, nuevaPassword: password, nuevaPasswordConfirm: confirm } : { correo }
          ),
        },
        false
      );
      if (reset) await resetWebPassword(enviar);
      else await enviar();
      setSuccess(
        reset
          ? 'Tu contraseña se actualizó. Ya puedes iniciar sesión con la nueva contraseña.'
          : 'Si el correo está registrado, recibirás instrucciones para continuar. El enlace vence en 15 minutos.'
      );
      if (reset) {
        setToken('');
        setPassword('');
        setConfirm('');
      }
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }

  const isReset = step === 'reset';

  return (
    <section className="card auth auth--recovery" aria-labelledby={`${formId}-heading`}>
      <aside className="auth-recovery__aside" aria-label="Ayuda para recuperar tu cuenta">
        <img
          className="auth-recovery__image"
          src="/images/mascotas-portada.webp"
          alt="Un perro y un gato juntos"
        />
        <p className="auth-recovery__eyebrow">Tu cuenta Petly</p>
        <h2>Volver a encontrar el camino también es parte de la historia.</h2>
        <p>Te guiaremos para recuperar el acceso de forma segura y crear una contraseña nueva.</p>
        <ol className="auth-recovery__steps" aria-label="Pasos para recuperar la cuenta">
          <li className={!isReset ? 'is-active' : 'is-complete'}>
            <span>1</span><span>Solicita el enlace</span>
          </li>
          <li className={isReset ? 'is-active' : ''}>
            <span>2</span><span>Elige una contraseña nueva</span>
          </li>
        </ol>
      </aside>

      <div className="auth-recovery__content">
        <div className="auth-recovery__title-row">
          <span className="auth-recovery__icon" aria-hidden="true">
            <RecoveryIcon name={isReset ? 'key' : 'mail'} />
          </span>
          <div>
            <p className="auth-recovery__step-label">Paso {isReset ? '2 de 2' : '1 de 2'}</p>
            <h1 id={`${formId}-heading`}>
              {isReset ? 'Crea una contraseña nueva' : 'Recupera el acceso a tu cuenta'}
            </h1>
          </div>
        </div>
        <p className="auth-recovery__intro">
          {isReset
            ? 'Ingresa el código del correo y define una contraseña para volver a entrar a Petly.'
            : 'Escribe el correo asociado a tu cuenta y te enviaremos instrucciones para continuar.'}
        </p>

        <Feedback error={error} success={success} />

        {!isReset ? (
          <form
            onSubmit={event => {
              event.preventDefault();
              void submit(false);
            }}
          >
            <fieldset disabled={busy}>
              <label htmlFor={`${formId}-email`}>Correo de tu cuenta</label>
              <input
                id={`${formId}-email`}
                type="email"
                autoComplete="email"
                required
                value={correo}
                onChange={event => setCorreo(event.target.value)}
              />
              <button className="auth-recovery__primary" type="submit">
                {busy ? 'Enviando…' : 'Enviar instrucciones'}
              </button>
            </fieldset>
          </form>
        ) : (
          <form
            onSubmit={event => {
              event.preventDefault();
              void submit(true);
            }}
          >
            <fieldset disabled={busy}>
              <label htmlFor={`${formId}-token`}>Código o enlace de recuperación</label>
              <input
                id={`${formId}-token`}
                required
                value={token}
                onChange={event => setToken(event.target.value)}
                autoComplete="off"
                aria-describedby={`${formId}-token-hint`}
              />
              <small className="auth-recovery__hint" id={`${formId}-token-hint`}>
                Vence en 15 minutos y solo se puede usar una vez.
              </small>

              <label htmlFor={`${formId}-password`}>Nueva contraseña</label>
              <div className="auth-recovery__password">
                <input
                  id={`${formId}-password`}
                  type={showPassword ? 'text' : 'password'}
                  minLength={8}
                  required
                  value={password}
                  onChange={event => setPassword(event.target.value)}
                  autoComplete="new-password"
                  aria-describedby={`${formId}-password-hint`}
                />
                <button
                  className="auth-recovery__visibility"
                  type="button"
                  aria-label={showPassword ? 'Ocultar contraseña' : 'Mostrar contraseña'}
                  aria-pressed={showPassword}
                  onClick={() => setShowPassword(value => !value)}
                >
                  <RecoveryIcon name={showPassword ? 'eye-off' : 'eye'} />
                </button>
              </div>
              <small className="auth-recovery__hint" id={`${formId}-password-hint`}>
                Usa al menos 8 caracteres.
              </small>

              <label htmlFor={`${formId}-confirm`}>Confirmar contraseña nueva</label>
              <div className="auth-recovery__password">
                <input
                  id={`${formId}-confirm`}
                  type={showConfirm ? 'text' : 'password'}
                  required
                  value={confirm}
                  onChange={event => setConfirm(event.target.value)}
                  autoComplete="new-password"
                />
                <button
                  className="auth-recovery__visibility"
                  type="button"
                  aria-label={showConfirm ? 'Ocultar confirmación' : 'Mostrar confirmación'}
                  aria-pressed={showConfirm}
                  onClick={() => setShowConfirm(value => !value)}
                >
                  <RecoveryIcon name={showConfirm ? 'eye-off' : 'eye'} />
                </button>
              </div>
              <button className="auth-recovery__primary" type="submit">
                {busy ? 'Actualizando…' : 'Cambiar contraseña'}
              </button>
            </fieldset>
          </form>
        )}

        {success && isReset && (
          <Link className="auth-recovery__login" to="/acceso" state={accessState}>Ir a iniciar sesión</Link>
        )}
        <div className="auth-recovery__footer">
          {isReset ? (
            <button type="button" className="auth-recovery__text-button" onClick={() => changeStep('request')}>
              Solicitar otro enlace
            </button>
          ) : (
            <button type="button" className="auth-recovery__text-button" onClick={() => changeStep('reset')}>
              Ya tengo un código
            </button>
          )}
          <Link to="/acceso" state={accessState}>Volver al acceso</Link>
        </div>
      </div>
    </section>
  );
}

function RecoveryIcon({ name }: { name: 'mail' | 'key' | 'eye' | 'eye-off' }) {
  return (
    <svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      {name === 'mail' && <><path d="M3 5h18v14H3z" /><path d="m3 6 9 7 9-7" /></>}
      {name === 'key' && <><circle cx="8" cy="15" r="4" /><path d="m11 12 8-8 2 2-2 2 2 2-3 3-2-2-3 3" /></>}
      {(name === 'eye' || name === 'eye-off') && <><path d="M2 12s3.5-6 10-6 10 6 10 6-3.5 6-10 6-10-6-10-6Z" /><circle cx="12" cy="12" r="2.5" />{name === 'eye-off' && <path d="m3 3 18 18" />}</>}
    </svg>
  );
}
