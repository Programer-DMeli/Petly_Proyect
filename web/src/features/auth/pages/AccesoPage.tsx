import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import Feedback from '../../../components/Feedback';
import { spring, json, errorMessage, ApiError } from '../../../services/http';
import { setSession, roleHome } from '../../../services/session';
import type { Identity } from '../../../services/session';
export default function AccesoPage({ register = false }: { register?: boolean }) {
  const [correo, setCorreo] = useState('');
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const navigate = useNavigate();
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
        const result = await spring<{ token: string; expiresIn: number }>(
          '/api/auth/login',
          { method: 'POST', body: json({ correo: normalizedEmail, password }) },
          false
        );
        const identity = await spring<Identity>(
          '/api/auth/me',
          { headers: { Authorization: `Bearer ${result.token}` } },
          false
        );
        if (!['ADMIN', 'ALBERGUE', 'ADOPTANTE'].includes(identity.rol))
          throw new Error('Rol no reconocido.');
        setSession({
          token: result.token,
          identity,
          expires: Date.now() + result.expiresIn * 1000,
        });
        await navigate(roleHome(identity.rol));
      }
    } catch (e) {
      setError(errorMessage(e));
      if (e instanceof ApiError && [423, 429].includes(e.status)) {
        const deadline = Date.now() + (e.retryAfterSeconds || (60)) * 1000;
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
    <section className="card auth">
      <h1>{register ? 'Crear cuenta de adoptante' : 'Bienvenido a Petly'}</h1>
      <p className="muted">
        {register
          ? 'Regístrate con tu correo y una contraseña de al menos 8 caracteres.'
          : 'Accede a tu espacio con tu cuenta Petly.'}
      </p>
      <Feedback error={error} success={success} />
      {!register && (
        <p className="muted">
          Dos contraseñas incorrectas consecutivas bloquean una cuenta registrada durante 1 minuto.
          Los errores de formato no cuentan como intentos de acceso.
        </p>
      )}
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
          <label>
            Correo
            <input
              type="email"
              autoComplete="email"
              required
              value={correo}
              onChange={e => {
                setCorreo(e.target.value);
                setNow(Date.now());
              }}
            />
          </label>
          <label>
            Contraseña
            <input
              type="password"
              autoComplete={register ? 'new-password' : 'current-password'}
              required
              minLength={register ? 8 : undefined}
              value={password}
              onChange={e => setPassword(e.target.value)}
            />
          </label>
          {register && (
            <label>
              Confirmar contraseña
              <input
                type="password"
                autoComplete="new-password"
                required
                value={confirm}
                onChange={e => setConfirm(e.target.value)}
              />
            </label>
          )}
          <button disabled={remaining > 0}>
            {busy ? 'Procesando…' : register ? 'Crear cuenta' : 'Entrar'}
          </button>
        </fieldset>
      </form>
      <div className="actions">
        <Link to="/recuperacion">Recuperar contraseña</Link>
        <Link to={register ? '/acceso' : '/registro'}>
          {register ? 'Iniciar sesión' : 'Crear cuenta'}
        </Link>
      </div>
    </section>
  );
}
