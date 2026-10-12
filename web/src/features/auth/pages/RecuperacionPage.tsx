import { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import Feedback from '../../../components/Feedback';
import { spring, json, errorMessage } from '../../../services/http';
import { setSession } from '../../../services/session';
export default function RecuperacionPage() {
  const [params] = useSearchParams();
  const incoming = params.get('token') ?? '';
  const [token, setToken] = useState(incoming);
  const [correo, setCorreo] = useState('');
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  useEffect(() => {
    if (incoming) {
      setSession(null);
      window.history.replaceState(null, '', '/recuperacion');
    }
  }, [incoming]);
  async function submit(reset: boolean) {
    setBusy(true);
    setError('');
    setSuccess('');
    try {
      if (reset && password !== confirm) throw new Error('Las contraseñas no coinciden.');
      await spring(
        reset ? '/api/auth/password/reset/confirm' : '/api/auth/password/reset?canal=web',
        {
          method: 'POST',
          body: json(
            reset ? { token, nuevaPassword: password, nuevaPasswordConfirm: confirm } : { correo }
          ),
        },
        false
      );
      setSuccess(
        reset
          ? 'Contraseña actualizada. Inicia sesión con tu nueva contraseña.'
          : 'Si el correo existe, recibirás instrucciones. El enlace vence en 15 minutos.'
      );
      if (reset) {
        setSession(null);
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
  return (
    <section className="card auth">
      <h1>Recuperar contraseña</h1>
      <Feedback error={error} success={success} />
      <form
        onSubmit={e => {
          e.preventDefault();
          void submit(false);
        }}
      >
        <fieldset disabled={busy}>
          <label>
            Correo de tu cuenta
            <input type="email" required value={correo} onChange={e => setCorreo(e.target.value)} />
          </label>
          <button>Enviar enlace</button>
        </fieldset>
      </form>
      <hr />
      <h2>Establecer nueva contraseña</h2>
      <p className="muted">
        Usa el enlace recibido o pega su código. Es válido durante 15 minutos y una sola vez.
      </p>
      <form
        onSubmit={e => {
          e.preventDefault();
          void submit(true);
        }}
      >
        <fieldset disabled={busy}>
          <label>
            Código de recuperación
            <input
              required
              value={token}
              onChange={e => setToken(e.target.value)}
              autoComplete="off"
            />
          </label>
          <label>
            Nueva contraseña
            <input
              type="password"
              minLength={8}
              required
              value={password}
              onChange={e => setPassword(e.target.value)}
              autoComplete="new-password"
            />
          </label>
          <label>
            Confirmar nueva contraseña
            <input
              type="password"
              required
              value={confirm}
              onChange={e => setConfirm(e.target.value)}
              autoComplete="new-password"
            />
          </label>
          <button>Cambiar contraseña</button>
        </fieldset>
      </form>
      <Link to="/acceso">Volver al acceso</Link>
    </section>
  );
}
