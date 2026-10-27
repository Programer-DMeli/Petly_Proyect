import { useCallback, useEffect, useState } from 'react';
import Feedback from '../../../../components/Feedback';
import { django, json, errorMessage, ApiError } from '../../../../services/http';
import type { AdminPet, Page, Shelter, Profile } from '../../../../services/types';
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
  const [step, setStep] = useState(0);
  const [highestStep, setHighestStep] = useState(0);
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
      setHighestStep(current => Math.max(current, 1));
      setStep(current => (current === 0 ? 1 : current));
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
  const advanceAfterCompletion = useCallback((completedStep: number, force = false) => {
    if (completedStep !== step || (!force && completedStep < highestStep)) return;
    const nextStep = completedStep + 1;
    setHighestStep(current => Math.max(current, nextStep));
    setStep(nextStep);
  }, [highestStep, step]);
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
      setSuccess('Información del albergue guardada. Continuemos con la infraestructura.');
      advanceAfterCompletion(0, true);
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }
  const steps = ['Datos del albergue', 'Infraestructura', 'Mascotas', 'Perfiles autorizados'];
  return (
    <section className="card shelter-wizard">
      <h1>Panel del albergue</h1>
      <p className="muted">Completa cada paso para organizar la información de tu albergue.</p>
      <Feedback error={error} success={success} />
      {shelter && (
        <nav className="shelter-steps" aria-label="Pasos del panel del albergue">
          {steps.map((label, index) => (
            <button
              key={label}
              type="button"
              className={`shelter-step${step === index ? ' shelter-step--active' : ''}`}
              aria-current={step === index ? 'step' : undefined}
              disabled={index > step}
              onClick={() => setStep(index)}
            >
              <span className="shelter-step__number">{index + 1}</span>
              {label}
            </button>
          ))}
        </nav>
      )}
      {step === 0 && (
        <>
          <h2>Mi albergue</h2>
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
                      onChange={e =>
                        setDraft({ ...draft, capacidad_maxima: Number(e.target.value) })
                      }
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
        </>
      )}
      {shelter && step === 1 && (
        <>
          <p className="muted">Registra al menos un área. Al guardarla, avanzaremos automáticamente al paso de mascotas.</p>
          <InfraestructuraPanel
            shelterId={shelter.id}
            onComplete={force => advanceAfterCompletion(1, force)}
          />
          <div className="shelter-wizard__actions">
            <button type="button" className="secondary" onClick={() => setStep(0)}>
              Anterior
            </button>
          </div>
        </>
      )}
      {shelter && step === 2 && (
        <>
          <p className="muted">Registra al menos una mascota. Al guardarla, avanzaremos automáticamente al último paso.</p>
          <MascotasPanel
            shelter={shelter}
            onComplete={force => advanceAfterCompletion(2, force)}
          />
          <div className="shelter-wizard__actions">
            <button type="button" className="secondary" onClick={() => setStep(1)}>
              Anterior
            </button>
          </div>
        </>
      )}
      {shelter && step === 3 && (
        <>
          <PerfilesCompartidos />
          <ResumenRegistroAlbergue shelter={shelter} />
          <div className="shelter-wizard__actions">
            <button type="button" className="secondary" onClick={() => setStep(2)}>
              Anterior
            </button>
          </div>
        </>
      )}
    </section>
  );
}
function ResumenRegistroAlbergue({ shelter: initialShelter }: { shelter: Shelter }) {
  const [shelter, setShelter] = useState(initialShelter);
  const [pets, setPets] = useState<AdminPet[]>([]);
  const [petCount, setPetCount] = useState(0);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);
  useEffect(() => {
    let active = true;
    async function load() {
      try {
        const [currentShelter, petPage] = await Promise.all([
          django<Shelter>('/api/albergues/mi-perfil/'),
          django<Page<AdminPet>>('/api/mascotas/?page=1'),
        ]);
        if (!active) return;
        setShelter(currentShelter);
        setPets(petPage.results);
        setPetCount(petPage.count);
      } catch (e) {
        if (active) setError(errorMessage(e));
      } finally {
        if (active) setLoading(false);
      }
    }
    void load();
    return () => { active = false; };
  }, []);
  return (
    <section className="card">
      <h2>Resumen de la información registrada</h2>
      {loading && <p className="muted">Cargando el resumen…</p>}
      {error && <Feedback error={error} />}
      <h3>Datos del albergue</h3>
      <dl>
        <dt>Nombre</dt><dd>{shelter.nombre}</dd>
        <dt>Dirección</dt><dd>{shelter.direccion}</dd>
        <dt>Correo de contacto</dt><dd>{shelter.email || 'Sin registrar'}</dd>
        <dt>Teléfono</dt><dd>{shelter.telefono || 'Sin registrar'}</dd>
        <dt>Capacidad máxima</dt><dd>{shelter.capacidad_maxima}</dd>
      </dl>
      <h3>Infraestructura registrada ({shelter.infraestructura.length})</h3>
      {shelter.infraestructura.length === 0 ? (
        <p>No hay áreas registradas.</p>
      ) : (
        <ul>
          {shelter.infraestructura.map(area => (
            <li key={area.id}>{area.nombre} · {area.tipo.replaceAll('_', ' ')}</li>
          ))}
        </ul>
      )}
      <h3>Mascotas registradas ({petCount})</h3>
      {pets.length === 0 ? (
        <p>No hay mascotas registradas.</p>
      ) : (
        <ul>
          {pets.map(pet => <li key={pet.id}>{pet.nombre} · {pet.especie} · {pet.estado}</li>)}
        </ul>
      )}
    </section>
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
          <h4>Solicitudes anteriores a este albergue</h4>
          {!selected.solicitudesPrevias?.length ? <p>Sin solicitudes previas.</p> : <ul>
            {selected.solicitudesPrevias.map(solicitud => <li key={solicitud.id}>
              {solicitud.mascotaNombre} · {solicitud.estado.replaceAll('_', ' ')} · {new Date(solicitud.fecha_solicitud).toLocaleDateString('es-PE')}
            </li>)}
          </ul>}
          <button className="secondary" onClick={() => setSelected(null)}>
            Cerrar perfil
          </button>
        </article>
      )}
    </section>
  );
}
