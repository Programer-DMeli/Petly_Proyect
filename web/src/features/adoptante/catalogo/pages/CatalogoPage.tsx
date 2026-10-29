import ConsejosCompatibilidad from '../../../../components/ConsejosCompatibilidad';
import PetCard from '../../../../components/PetCard';
import { FavoriteHeart } from '../../../../components/FavoritesProvider';
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
  const pageSize = 12;
  const [filtersOpen, setFiltersOpen] = useState(
    () => window.matchMedia('(min-width: 960px)').matches
  );
  useEffect(() => {
    const media = window.matchMedia('(min-width: 960px)');
    const resize = () => setFiltersOpen(media.matches);
    media.addEventListener('change', resize);
    return () => media.removeEventListener('change', resize);
  }, []);
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
    const query = new URLSearchParams({ page: String(page), pageSize: String(pageSize) });
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
    <section className="catalog-page" id="catalogo" aria-labelledby="catalog-title">

      <div className="catalog-layout">
        <aside className="catalog-filter-sidebar">
          <details
            className="catalog-filters"
            open={filtersOpen}
            onToggle={event => setFiltersOpen(event.currentTarget.open)}
          >
            <summary>
              <span>
                <span aria-hidden="true">☷</span> Filtrar mascotas
              </span>
              <span className="nav-chevron" aria-hidden="true" />
            </summary>
            <form
              onSubmit={e => {
                e.preventDefault();
                setFilters({ ...draft });
                setPage(1);
              }}
            >
              <fieldset disabled={busy}>
                <div className="catalog-filter-fields">
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
                          key === 'tamano'
                            ? 'Tamaño'
                            : key === 'especie'
                              ? 'Especie'
                              : 'Temperamento'
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
          </details>
          <div className="catalog-care-note">
            <span aria-hidden="true">♡</span>
            <strong>Un compromiso para toda la vida</strong>
            <p>Conoce su historia, conversa con el albergue y elige con responsabilidad.</p>
          </div>
        </aside>
        <div className="catalog-results" aria-busy={busy}>
          <div className="catalog-results__top">
            <p role="status">{busy ? 'Buscando compañeros…' : `${total} mascotas encontradas`}</p>
            <span>Compatibilidad y disponibilidad según tu perfil</span>
          </div>
          <Feedback error={error} />
          {busy ? (
            <div className="catalog-loading" role="status">
              Cargando mascotas…
            </div>
          ) : error ? (
            <button onClick={() => setRefresh(v => v + 1)}>Reintentar</button>
          ) : (
            <>
              {items.length === 0 && (
                <div className="workspace-empty">
                  <span aria-hidden="true">⌕</span>
                  <h2>Sigamos buscando</h2>
                  <p>No hay coincidencias. Prueba otros filtros.</p>
                </div>
              )}
              <div className="pet-card-grid">
                {items.map(p => (
                  <PetCard key={p.id} pet={p} />
                ))}
              </div>
              <nav className="catalog-pagination" aria-label="Páginas del catálogo">
                <button
                  className="secondary"
                  disabled={page === 1}
                  onClick={() => setPage(v => v - 1)}
                >
                  Anterior
                </button>
                <span>
                  Página {page} de {Math.max(1, Math.ceil(total / pageSize))}
                </span>
                <button
                  className="secondary"
                  disabled={page * pageSize >= total}
                  onClick={() => setPage(v => v + 1)}
                >
                  Siguiente
                </button>
                <button
                  className="catalog-refresh secondary"
                  onClick={() => setRefresh(v => v + 1)}
                >
                  Actualizar
                </button>
              </nav>
            </>
          )}
        </div>
      </div>
    </section>
  );
}
export function DetallePage() {
  const { id } = useParams();
  const [pet, setPet] = useState<Pet | null>(null);
  const [error, setError] = useState('');
  const [eligibilityError, setEligibilityError] = useState('');
  const [profileFinalizado, setProfileFinalizado] = useState<boolean | null>(null);
  const [applicationProfile, setApplicationProfile] = useState<Profile | null>(null);
  const [autorizacionActiva, setAutorizacionActiva] = useState<boolean | null>(null);
  const [checkingEligibility, setCheckingEligibility] = useState(false);
  const [mensajePostulante, setMensajePostulante] = useState('');
  const [planCuidado, setPlanCuidado] = useState('');
  const [sending, setSending] = useState(false);
  const [submitted, setSubmitted] = useState(false);
  const [submittedRequestId, setSubmittedRequestId] = useState<number | null>(null);
  const [success, setSuccess] = useState('');
  const load = useCallback(async () => {
    setError('');
    setPet(null);
    setProfileFinalizado(null);
    setAutorizacionActiva(null);
    setSubmitted(false);
    setSubmittedRequestId(null);
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
      setApplicationProfile(profile);
      setProfileFinalizado(profile.finalizado);
      setAutorizacionActiva(
        consents.some(
          consent => consent.albergueId === pet.albergueId && consent.estado === 'ACTIVA'
        )
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
      respuestasCuestionario: {
        ...(applicationProfile ?? {}),
        planCuidado: planCuidado.trim(),
      },
    };
    try {
      const created = await spring<SolicitudAdopcion>('/api/solicitudes', {
        method: 'POST',
        body: json(payload),
      });
      setSubmitted(true);
      setSubmittedRequestId(created.id);
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
          <div className="detail-title">
            <h1>{pet.nombre}</h1>
            <FavoriteHeart pet={pet} />
          </div>
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
          {pet.compatibilidad && (
            <section
              className="card compatibility-breakdown"
              aria-labelledby="compatibility-heading"
            >
              <h2 id="compatibility-heading">¿Cómo se calcula tu compatibilidad?</h2>
              <p>{pet.compatibilidad.mensaje}</p><p>Reglas: {pet.compatibilidad.versionReglas}</p>
              <p>
                Puntaje:{' '}
                <strong>
                  {pet.compatibilidad.porcentaje === null
                    ? 'Información insuficiente'
                    : `${pet.compatibilidad.porcentaje}%`}
                </strong>
                {' · '}Cobertura de datos: {pet.compatibilidad.cobertura}%
              </p>
              <ul>
                {pet.compatibilidad.criterios.map(criterion => (
                  <li key={criterion.id}>
                    <strong>{criterion.nombre}</strong> · peso {criterion.peso}% ·{' '}
                    {criterion.aporte === null
                      ? 'aporte no calculable por falta de datos'
                      : `aporta ${criterion.aporte.toFixed(2)} puntos`}
                    <p>{criterion.explicacion}</p>
                  </li>
                ))}
              </ul>
              <p className="muted">
                El total ponderado se redondea al entero más cercano. Es una orientación; el
                albergue decide cada adopción.
              </p>
            </section>
          )}
          <ConsejosCompatibilidad key={pet.id} id={pet.id} />
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
              <p className="muted">
                Cuéntale al albergue por qué quieres adoptar y cómo cubrirás sus cuidados. Se
                enviará también el cuestionario que ya completaste.
              </p>
              <label>
                ¿Por qué quieres adoptar a {pet.nombre}? <span aria-hidden="true">*</span>
                <textarea
                  maxLength={2000}
                  required
                  value={mensajePostulante}
                  onChange={event => setMensajePostulante(event.target.value)}
                  placeholder="Cuéntanos qué te motivó a elegirle…"
                />
              </label>
              <label>
                ¿Cómo organizarás sus cuidados diarios? <span aria-hidden="true">*</span>
                <textarea
                  maxLength={2000}
                  required
                  value={planCuidado}
                  onChange={event => setPlanCuidado(event.target.value)}
                  placeholder="Describe su espacio, compañía, paseos y atención veterinaria…"
                />
              </label>
              <button disabled={sending || !mensajePostulante.trim() || !planCuidado.trim()}>
                {sending ? 'Enviando…' : 'Enviar solicitud'}
              </button>
            </form>
          )}
          {submitted && (
            <p className="feedback success" role="status">
              La solicitud quedó pendiente de revisión. La mascota no se reserva al enviarla.{' '}
              {submittedRequestId !== null && (
                <Link to={`/adoptante/solicitudes/${submittedRequestId}`}>Ver seguimiento</Link>
              )}
            </p>
          )}
        </>
      ) : (
        <button onClick={() => void load()}>Cargar detalle</button>
      )}
    </section>
  );
}
