import { useEffect, useState, type FormEvent } from 'react';
import Feedback from '../../../../components/Feedback';
import { errorMessage } from '../../../../services/http';
import {
  getPreferenciasNotificacion,
  guardarPreferenciasNotificacion,
} from '../../../../services/notificacionesApi';
import type { PreferenciasNotificacion } from '../../../../services/types';

const canales = [
  { clave: 'email', etiqueta: 'Correo electrónico' },
  { clave: 'whatsapp', etiqueta: 'WhatsApp' },
  { clave: 'push', etiqueta: 'Notificaciones push' },
] as const;

export default function PreferenciasNotificacionPage() {
  const [preferencias, setPreferencias] = useState<PreferenciasNotificacion | null>(null);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    let activo = true;
    getPreferenciasNotificacion()
      .then(data => {
        if (activo) setPreferencias(data);
      })
      .catch(e => {
        if (activo) setError(errorMessage(e));
      });
    return () => {
      activo = false;
    };
  }, []);

  async function guardar(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!preferencias) return;
    setBusy(true);
    setError('');
    setSuccess('');
    try {
      setPreferencias(await guardarPreferenciasNotificacion(preferencias));
      setSuccess('Preferencias guardadas.');
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="card">
      <h1>Preferencias de notificación</h1>
      <p>Elige por qué canales quieres recibir alertas de adopción y seguimiento.</p>
      <Feedback error={error} success={success} />
      {!preferencias ? (
        <p>Cargando preferencias...</p>
      ) : (
        <form onSubmit={event => void guardar(event)}>
          <fieldset disabled={busy}>
            {canales.map(({ clave, etiqueta }) => (
              <label key={clave}>
                <input
                  type="checkbox"
                  checked={preferencias[clave]}
                  onChange={e => setPreferencias({ ...preferencias, [clave]: e.target.checked })}
                />
                {etiqueta}
              </label>
            ))}
            <button>Guardar preferencias</button>
          </fieldset>
        </form>
      )}
    </section>
  );
}
