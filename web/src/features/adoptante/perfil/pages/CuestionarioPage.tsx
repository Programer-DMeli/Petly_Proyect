import { useCallback, useEffect, useState } from 'react';
import Feedback from '../../../../components/Feedback';
import { spring, json, errorMessage } from '../../../../services/http';
import type { Profile } from '../../../../services/types';
export default function CuestionarioPage() {
  const [profile, setProfile] = useState<Profile | null>(null);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [busy, setBusy] = useState(false);
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
        <form
          onSubmit={e => {
            e.preventDefault();
            void save(true);
          }}
        >
          <fieldset disabled={busy}>
            <label>
              Vivienda
              <input
                maxLength={120}
                value={profile.vivienda ?? ''}
                onChange={e => update({ vivienda: e.target.value || null })}
                placeholder="Casa, departamento…"
              />
            </label>
            <label>
              Horas fuera de casa por día
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
            <label>
              Presupuesto mensual (S/)
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
              <label key={key}>
                {key === 'tieneNinos' ? '¿Viven niños en tu hogar?' : '¿Tienes otras mascotas?'}
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
      )}
    </section>
  );
}
