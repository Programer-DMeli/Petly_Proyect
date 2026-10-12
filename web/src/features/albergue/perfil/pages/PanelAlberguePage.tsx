import { useCallback, useEffect, useState } from 'react';
import Feedback from '../../../../components/Feedback';
import { django, json, errorMessage, ApiError } from '../../../../services/http';
import type { Shelter, Profile } from '../../../../services/types';
import MascotasPanel from './MascotasPanel';
import InfraestructuraPanel from './InfraestructuraPanel';
type ShelterDraft = Pick<
  Shelter,
  | 'nombre'
  | 'direccion'
  | 'descripcion'
  | 'telefono'
  | 'email'
  | 'capacidad_maxima'
  | 'responsable_nombre'
  | 'responsable_email'
  | 'responsable_telefono'
>;
const initial: ShelterDraft = {
  nombre: '',
  direccion: '',
  descripcion: '',
  telefono: '',
  email: '',
  capacidad_maxima: 0,
  responsable_nombre: '',
  responsable_email: '',
  responsable_telefono: '',
};
export default function PanelAlberguePage() {
  const [shelter, setShelter] = useState<Shelter | null>(null);
  const [draft, setDraft] = useState(initial);
  const [loaded, setLoaded] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const load = useCallback(async () => {
    setError('');
    try {
      const value = await django<Shelter>('/api/albergues/mi-perfil/');
      setShelter(value);
      setDraft({
        nombre: value.nombre,
        direccion: value.direccion,
        descripcion: value.descripcion,
        telefono: value.telefono,
        email: value.email,
        capacidad_maxima: value.capacidad_maxima,
        responsable_nombre: value.responsable_nombre,
        responsable_email: value.responsable_email,
        responsable_telefono: value.responsable_telefono,
      });
      setLoaded(true);
    } catch (e) {
      if (e instanceof ApiError && e.status === 404) setLoaded(true);
      else setError(errorMessage(e));
    }
  }, []);
  useEffect(() => {
    void load();
  }, [load]);
  async function save() {
    setBusy(true);
    setError('');
    setSuccess('');
    try {
      const value = await django<Shelter>(
        shelter ? `/api/albergues/${shelter.id}/` : '/api/albergues/',
        { method: shelter ? 'PATCH' : 'POST', body: json(draft) }
      );
      setShelter(value);
      setSuccess('Información del albergue guardada.');
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }
  return (
    <>
      <section className="card">
        <h1>Mi albergue</h1>
        <Feedback error={error} success={success} />
        {!loaded ? (
          <button onClick={() => void load()}>Cargar información</button>
        ) : (
          <form
            onSubmit={e => {
              e.preventDefault();
              void save();
            }}
          >
            <fieldset disabled={busy}>
              <div className="grid">
                {(
                  [
                    'nombre',
                    'direccion',
                    'telefono',
                    'email',
                    'responsable_nombre',
                    'responsable_telefono',
                    'responsable_email',
                  ] as const
                ).map(key => (
                  <label key={key}>
                    {
                      {
                        nombre: 'Nombre del albergue',
                        direccion: 'Dirección',
                        telefono: 'Teléfono',
                        email: 'Correo de contacto',
                        responsable_nombre: 'Nombre del responsable',
                        responsable_telefono: 'Teléfono del responsable',
                        responsable_email: 'Correo del responsable',
                      }[key]
                    }
                    <input
                      type={key.includes('email') ? 'email' : 'text'}
                      required={key === 'nombre' || key === 'direccion'}
                      maxLength={key === 'direccion' ? 255 : key.includes('telefono') ? 20 : 150}
                      value={draft[key]}
                      onChange={e => setDraft({ ...draft, [key]: e.target.value })}
                    />
                  </label>
                ))}
                <label>
                  Capacidad máxima
                  <input
                    type="number"
                    required
                    min={0}
                    step={1}
                    value={draft.capacidad_maxima}
                    onChange={e => setDraft({ ...draft, capacidad_maxima: Number(e.target.value) })}
                  />
                </label>
              </div>
              <label>
                Descripción
                <textarea
                  value={draft.descripcion}
                  onChange={e => setDraft({ ...draft, descripcion: e.target.value })}
                />
              </label>
              <button>
                {busy ? 'Guardando…' : shelter ? 'Actualizar albergue' : 'Registrar albergue'}
              </button>
            </fieldset>
          </form>
        )}
      </section>
      {shelter && (
        <>
          <InfraestructuraPanel shelterId={shelter.id} />
          <MascotasPanel />
          <PerfilesCompartidos />
        </>
      )}
    </>
  );
}
function PerfilesCompartidos() {
  const [profiles, setProfiles] = useState<{ adoptanteId: number; correo: string }[]>([]);
  const [selected, setSelected] = useState<Profile | null>(null);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [name, setName] = useState('');
  const load = useCallback(async () => {
    setBusy(true);
    setSelected(null);
    setError('');
    try {
      setProfiles(await django('/api/acceso/perfiles-compartidos/'));
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }, []);
  useEffect(() => {
    void load();
  }, [load]);
  async function show(id: number, correo: string) {
    setBusy(true);
    setSelected(null);
    setError('');
    try {
      setSelected(await django<Profile>(`/api/acceso/perfiles-compartidos/${id}/`));
      setName(correo);
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }
  return (
    <section className="card">
      <h2>Perfiles autorizados</h2>
      <p className="muted">
        La autorización se comprueba en cada consulta. No representa una solicitud de adopción.
      </p>
      <Feedback error={error} />
      <button disabled={busy} onClick={() => void load()}>
        Actualizar autorizaciones
      </button>
      {profiles.length === 0 && !busy && <p>No hay perfiles autorizados.</p>}
      {profiles.map(p => (
        <div className="list-row" key={p.adoptanteId}>
          <span>{p.correo}</span>
          <button disabled={busy} onClick={() => void show(p.adoptanteId, p.correo)}>
            Consultar perfil
          </button>
        </div>
      ))}
      {selected && (
        <article className="confirmation">
          <h3>Perfil de {name}</h3>
          <dl>
            <dt>Vivienda</dt>
            <dd>{selected.vivienda ?? 'Sin responder'}</dd>
            <dt>Horas fuera</dt>
            <dd>{selected.horasFuera ?? 'Sin responder'}</dd>
            <dt>Presupuesto mensual (S/)</dt>
            <dd>{selected.presupuesto ?? 'Sin responder'}</dd>
            <dt>Niños</dt>
            <dd>
              {selected.tieneNinos === null ? 'Sin responder' : selected.tieneNinos ? 'Sí' : 'No'}
            </dd>
            <dt>Otras mascotas</dt>
            <dd>
              {selected.tieneOtrasMascotas === null
                ? 'Sin responder'
                : selected.tieneOtrasMascotas
                  ? 'Sí'
                  : 'No'}
            </dd>
          </dl>
          <button className="secondary" onClick={() => setSelected(null)}>
            Cerrar perfil
          </button>
        </article>
      )}
    </section>
  );
}
