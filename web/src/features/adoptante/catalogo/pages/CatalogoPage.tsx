import AyudaParaAdoptar from '../components/AyudaParaAdoptar';
import ConsejosCompatibilidad from '../../../../components/ConsejosCompatibilidad';
import PetCard from '../../../../components/PetCard';
import { FavoriteHeart } from '../../../../components/FavoritesProvider';
import { useCallback, useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import Feedback from '../../../../components/Feedback';
import { spring, mediaUrl, errorMessage } from '../../../../services/http';
import type { Pet } from '../../../../services/types';
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
const filterLabels: Record<Exclude<keyof Filters, 'search'>, string> = {
  especie: 'Especie',
  tamano: 'Tamaño',
  temperamento: 'Temperamento',
  edadMinimaMeses: 'Edad mínima',
  edadMaximaMeses: 'Edad máxima',
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
  const activeFilters = (Object.keys(filters) as (keyof Filters)[])
    .filter(key => Boolean(filters[key]))
    .map(key => ({
      key,
      label: key === 'search'
        ? `Búsqueda: ${filters[key]}`
        : `${filterLabels[key]}: ${filters[key]}${key === 'edadMinimaMeses' || key === 'edadMaximaMeses' ? ' meses' : ''}`,
    }));

  function removeFilter(key: keyof Filters) {
    const nextFilters = { ...filters, [key]: '' };
    setFilters(nextFilters);
    setDraft(nextFilters);
    setPage(1);
  }

  function clearFilters() {
    setDraft({ ...empty });
    setFilters({ ...empty });
    setPage(1);
  }
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
                    onClick={clearFilters}
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
          {activeFilters.length > 0 && (
            <div className="catalog-active-filters" role="group" aria-label="Filtros aplicados">
              <span className="catalog-active-filters__label">Filtros activos</span>
              {activeFilters.map(filter => (
                <button
                  className="catalog-active-filters__chip"
                  type="button"
                  key={filter.key}
                  aria-label={`Quitar filtro: ${filter.label}`}
                  onClick={() => removeFilter(filter.key)}
                >
                  {filter.label}<span aria-hidden="true">×</span>
                </button>
              ))}
              <button className="catalog-active-filters__clear" type="button" onClick={clearFilters}>
                Limpiar todos
              </button>
            </div>
          )}
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
      <AyudaParaAdoptar mascotas={items} />
    </section>
  );
}
export function DetallePage() {
  const { id } = useParams();
  const [pet, setPet] = useState<Pet | null>(null);
  const [error, setError] = useState('');
  const load = useCallback(
    async (signal?: AbortSignal) => {
      setError('');
      setPet(null);
      try {
        const detalle = await spring<Pet>('/api/catalogo/' + String(id), { signal });
        if (!signal?.aborted) setPet(detalle);
      } catch (e) {
        if (!signal?.aborted) setError(errorMessage(e));
      }
    },
    [id]
  );
  useEffect(() => {
    const controller = new AbortController();
    void load(controller.signal);
    return () => controller.abort();
  }, [load]);

  const disponible = pet?.estado.trim().toLowerCase() === 'disponible';

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
          <div className="actions">
            {disponible && (
              <Link className="button" to={`/adoptante/catalogo/${pet.id}/solicitud`}>
                Llenar formulario de adopción
              </Link>
            )}
            <Link
              className="button secondary"
              to={`/adoptante/mensajes?albergue=${pet.albergueId}`}
            >
              Escribir al albergue
            </Link>
            <Link to="/adoptante/autorizaciones">Administrar autorizaciones de mi perfil</Link>
          </div>
          {!disponible && <p role="status">Esta mascota no está disponible para solicitudes.</p>}
        </>
      ) : (
        <button onClick={() => void load()}>Cargar detalle</button>
      )}
    </section>
  );
}
