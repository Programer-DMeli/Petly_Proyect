import { useCallback, useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import Feedback from '../../../../components/Feedback';
import { spring, json, mediaUrl, errorMessage } from '../../../../services/http';
import type {
  CrearSolicitudAdopcion,
  Pet,
  Profile,
  SolicitudAdopcion,
} from '../../../../services/types';
interface Filters {
  search: string;
  especie: string;
  tamano: string;
  temperamento: string;
  edadMinimaMeses: string;
  edadMaximaMeses: string;
}
const empty: Filters = {
  search: '',
  especie: '',
  tamano: '',
  temperamento: '',
  edadMinimaMeses: '',
  edadMaximaMeses: '',
};
export default function CatalogoPage() {
  const [draft, setDraft] = useState(empty);
  const [filters, setFilters] = useState(empty);
  const [page, setPage] = useState(1);
  const [options, setOptions] = useState<{
    especies: string[];
    tamanos: string[];
    temperamentos: string[];
  } | null>(null);
  const [items, setItems] = useState<Pet[]>([]);
  const [total, setTotal] = useState(0);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [refresh, setRefresh] = useState(0);
  useEffect(() => {
    const controller = new AbortController();
    setBusy(true);
    setError('');
    const query = new URLSearchParams({ page: String(page), pageSize: '6' });
    (Object.keys(filters) as (keyof Filters)[]).forEach(k => {
      const value = filters[k];
      if (value) query.set(k, value);
    });
    void Promise.all([
      spring<{ items: Pet[]; total: number }>('/api/catalogo?' + query.toString(), {
        signal: controller.signal,
      }),
      spring<{ especies: string[]; tamanos: string[]; temperamentos: string[] }>(
        '/api/catalogo/opciones',
        { signal: controller.signal }
      ),
    ])
      .then(([result, opts]) => {
        if (!controller.signal.aborted) {
          setItems(result.items);
          setTotal(result.total);
          setOptions(opts);
        }
      })
      .catch((e: unknown) => {
        if (!controller.signal.aborted) setError(errorMessage(e));
      })
      .finally(() => {
        if (!controller.signal.aborted) setBusy(false);
      });
    return () => controller.abort();
  }, [filters, page, refresh]);
  useEffect(() => {
    const focus = () => setRefresh(v => v + 1);
    window.addEventListener('focus', focus);
    return () => window.removeEventListener('focus', focus);
  }, []);
  return (
    <>
      <section className="card">
        <h1>Encuentra a tu compañero</h1>
        <p className="muted">Conoce las mascotas de los albergues.</p>
        <form
          onSubmit={e => {
            e.preventDefault();
            setFilters({ ...draft });
            setPage(1);
          }}
        >
          <fieldset disabled={busy}>
            <div className="grid">
              <label>
                Buscar mascota o albergue
                <input
                  maxLength={120}
                  value={draft.search}
                  onChange={e => setDraft({ ...draft, search: e.target.value })}
                />
              </label>
              {(['especie', 'tamano', 'temperamento'] as const).map(key => (
                <label key={key}>
                  {key === 'tamano' ? 'Tamaño' : key === 'especie' ? 'Especie' : 'Temperamento'}
                  <select
                    aria-label={
                      key === 'tamano' ? 'Tamaño' : key === 'especie' ? 'Especie' : 'Temperamento'
                    }
                    value={draft[key]}
                    onChange={e => setDraft({ ...draft, [key]: e.target.value })}
                  >
                    <option value="">Todos</option>
                    {(
                      options?.[
                        key === 'especie'
                          ? 'especies'
                          : key === 'tamano'
                            ? 'tamanos'
                            : 'temperamentos'
                      ] ?? []
                    ).map(v => (
                      <option key={v}>{v}</option>
                    ))}
                  </select>
                </label>
              ))}
              <label>
                Edad mínima (meses)
                <input
                  type="number"
                  min={0}
                  max={600}
                  value={draft.edadMinimaMeses}
                  onChange={e => setDraft({ ...draft, edadMinimaMeses: e.target.value })}
                />
              </label>
              <label>
                Edad máxima (meses)
                <input
                  type="number"
                  min={0}
                  max={600}
                  value={draft.edadMaximaMeses}
                  onChange={e => setDraft({ ...draft, edadMaximaMeses: e.target.value })}
                />
              </label>
            </div>
            <div className="actions">
              <button>Buscar</button>
              <button
                type="button"
                className="secondary"
                onClick={() => {
                  setDraft(empty);
                  setFilters({ ...empty });
                  setPage(1);
                }}
              >
                Limpiar filtros
              </button>
            </div>
          </fieldset>
        </form>
      </section>
      <Feedback error={error} />
      {busy ? (
        <p role="status">Cargando mascotas…</p>
      ) : error ? (
        <button onClick={() => setRefresh(v => v + 1)}>Reintentar</button>
      ) : (
        <>
          <p>{total} mascotas encontradas</p>
          {items.length === 0 && (
            <section className="card">No hay coincidencias. Prueba otros filtros.</section>
          )}
          <div className="grid">
            {items.map(p => (
              <article className="card pet" key={p.id}>
                {p.fotografiaUrl && <img src={mediaUrl(p.fotografiaUrl)} alt={p.nombre} />}
                <span className="badge">{p.estado}</span>
                <h2>{p.nombre}</h2>
                <p>
                  {p.especie} · {p.tamano} ·{' '}
                  {p.edadMeses === null ? 'Edad por evaluar' : `${p.edadMeses} meses`}
                </p>
                <p>
                  {p.temperamento} · {p.albergue}
                </p>
                <Link className="button" to={`/adoptante/catalogo/${p.id}`}>
                  Ver detalle
                </Link>
              </article>
            ))}
          </div>
          <div className="actions">
            <button disabled={page === 1} onClick={() => setPage(v => v - 1)}>
              Anterior
            </button>
            <span>
              Página {page} de {Math.max(1, Math.ceil(total / 6))}
            </span>
            <button disabled={page * 6 >= total} onClick={() => setPage(v => v + 1)}>
              Siguiente
            </button>
            <button className="secondary" onClick={() => setRefresh(v => v + 1)}>
              Actualizar
            </button>
          </div>
        </>
      )}
    </>
  );
}
export function DetallePage() {
  const { id } = useParams();
  const [pet, setPet] = useState<Pet | null>(null);
  const [error, setError] = useState('');
  const [eligibilityError, setEligibilityError] = useState('');
  const [profileFinalizado, setProfileFinalizado] = useState<boolean | null>(null);
  const [autorizacionActiva, setAutorizacionActiva] = useState<boolean | null>(null);
  const [checkingEligibility, setCheckingEligibility] = useState(false);
  const [mensajePostulante, setMensajePostulante] = useState('');
  const [sending, setSending] = useState(false);
  const [submitted, setSubmitted] = useState(false);
  const [success, setSuccess] = useState('');
  const load = useCallback(async () => {
    setError('');
    setPet(null);
    setProfileFinalizado(null);
    setAutorizacionActiva(null);
    setSubmitted(false);
    setSuccess('');
    try {
      setPet(await spring<Pet>('/api/catalogo/' + String(id)));
    } catch (e) {
      setError(errorMessage(e));
    }
  }, [id]);
  useEffect(() => {
    void load();
  }, [load]);
  const loadEligibility = useCallback(async () => {
    if (!pet) return;
    setCheckingEligibility(true);
    setEligibilityError('');
    try {
      const [profile, consents] = await Promise.all([
        spring<Profile>('/api/perfil'),
        spring<{ albergueId: number; estado: string }[]>('/api/autorizaciones'),
      ]);
      setProfileFinalizado(profile.finalizado);
      setAutorizacionActiva(
        consents.some(consent => consent.albergueId === pet.albergueId && consent.estado === 'ACTIVA')
      );
    } catch (e) {
      setEligibilityError(errorMessage(e));
    } finally {
      setCheckingEligibility(false);
    }
  }, [pet]);
  useEffect(() => {
    void loadEligibility();
  }, [loadEligibility]);

  const disponible = pet?.estado.trim().toLocaleLowerCase('es') === 'disponible';
  const canApply = Boolean(
    pet && disponible && profileFinalizado === true && autorizacionActiva === true
  );

  async function submitApplication() {
    if (!pet || !canApply || sending || submitted) return;
    setSending(true);
    setError('');
    setSuccess('');
    const payload: CrearSolicitudAdopcion = {
      mascotaId: pet.id,
      mensajePostulante: mensajePostulante.trim(),
      respuestasCuestionario: {},
    };
    try {
      const created = await spring<SolicitudAdopcion>('/api/solicitudes', {
        method: 'POST',
        body: json(payload),
      });
      setSubmitted(true);
      setSuccess(`Solicitud enviada. Estado: ${created.estado}.`);
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setSending(false);
    }
  }
  return (
    <section className="card">
      <Link to="/adoptante/catalogo">Volver al catálogo</Link>
      <Feedback error={error} />
      {pet ? (
        <>
          <h1>{pet.nombre}</h1>
          {pet.fotografiaUrl && (
            <img className="detail-photo" src={mediaUrl(pet.fotografiaUrl)} alt={pet.nombre} />
          )}
          <p>
            <span className="badge">{pet.estado}</span>
          </p>
          <p>
            {pet.especie} · {pet.tamano} ·{' '}
            {pet.edadMeses === null ? 'Edad por evaluar' : `${pet.edadMeses} meses`}
          </p>
          <p>
            Temperamento: {pet.temperamento} · Energía: {pet.energia}
          </p>
          <p>{pet.descripcion}</p>
          <h2>{pet.albergue}</h2>
          <p>{pet.ubicacion}</p>
          <Link to="/adoptante/autorizaciones">Administrar autorizaciones de mi perfil</Link>
          <hr />
          <h2>Solicitar adopción</h2>
          <Feedback error={eligibilityError} success={success} />
          {!disponible && <p role="status">Esta mascota no está disponible para solicitudes.</p>}
          {checkingEligibility && <p role="status">Comprobando los requisitos de solicitud…</p>}
          {!checkingEligibility && profileFinalizado === false && (
            <p>
              Finaliza tu cuestionario antes de solicitar.{' '}
              <Link to="/adoptante/cuestionario">Completar perfil</Link>
            </p>
          )}
          {!checkingEligibility && profileFinalizado && autorizacionActiva === false && (
            <p>
              Necesitas una autorización vigente para este albergue.{' '}
              <Link to="/adoptante/autorizaciones">Gestionar autorización</Link>
            </p>
          )}
          {eligibilityError && (
            <button className="secondary" onClick={() => void loadEligibility()}>
              Reintentar comprobación
            </button>
          )}
          {canApply && !submitted && (
            <form
              onSubmit={event => {
                event.preventDefault();
                void submitApplication();
              }}
            >
              <label>
                Mensaje para el albergue (opcional)
                <textarea
                  maxLength={2000}
                  value={mensajePostulante}
                  onChange={event => setMensajePostulante(event.target.value)}
                />
              </label>
              <button disabled={sending}>{sending ? 'Enviando…' : 'Enviar solicitud'}</button>
            </form>
          )}
          {submitted && (
            <p role="status">
              La solicitud quedó pendiente de revisión. La mascota no se reserva al enviarla.
            </p>
          )}
        </>
      ) : (
        <button onClick={() => void load()}>Cargar detalle</button>
      )}
    </section>
  );
}
