import AyudaParaAdoptar from '../components/AyudaParaAdoptar';
import CatalogoDestacado from '../components/CatalogoDestacado';
import ConsejosCompatibilidad from '../../../../components/ConsejosCompatibilidad';
import PetCard from '../../../../components/PetCard';
import { FavoriteHeart } from '../../../../components/FavoritesProvider';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Link, useLocation, useNavigate, useParams, useSearchParams } from 'react-router-dom';
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
const speciesCards = [
  { species: 'Gato', label: 'Ver gatos', style: 'cat' },
  { species: 'Perro', label: 'Ver perros', style: 'dog' },
] as const;
const filterKeys = Object.keys(empty) as (keyof Filters)[];

function catalogPath(filters: Filters, page = 1, grid = false) {
  const params = new URLSearchParams();
  for (const key of filterKeys) {
    if (filters[key]) params.set(key, filters[key]);
  }
  if (page > 1) params.set('page', String(page));
  if (grid) params.set('vista', 'todos');
  const query = params.toString();
  return '/adoptante/catalogo' + (query ? '?' + query : '');
}

export default function CatalogoPage() {
  const pageSize = 12;
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const location = useLocation();
  const filters = useMemo(() => {
    const values = { ...empty };
    for (const key of filterKeys) values[key] = params.get(key) ?? '';
    return values;
  }, [params]);
  const speciesView = Boolean(filters.especie);
  const grid = params.get('vista') === 'todos';
  const featured = speciesView && !grid;
  const requestedPage = Number(params.get('page'));
  const page = !featured && Number.isInteger(requestedPage) && requestedPage >= 1 && requestedPage <= 1000000
    ? requestedPage
    : 1;
  const catalogRequest = catalogPath(filters, page, grid);
  const [filtersOpen, setFiltersOpen] = useState(
    () => window.matchMedia('(min-width: 960px)').matches
  );
  useEffect(() => {
    const media = window.matchMedia('(min-width: 960px)');
    const resize = () => setFiltersOpen(media.matches);
    media.addEventListener('change', resize);
    return () => media.removeEventListener('change', resize);
  }, []);
  const [draft, setDraft] = useState(filters);
  const [selection, setSelection] = useState<{ species: string; id: number } | null>(null);
  const [options, setOptions] = useState<{
    especies: string[];
    tamanos: string[];
    temperamentos: string[];
  } | null>(null);
  const [items, setItems] = useState<Pet[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [settledRequest, setSettledRequest] = useState('');
  const busy = loading || settledRequest !== catalogRequest;
  const [error, setError] = useState('');
  const [refresh, setRefresh] = useState(0);
  const resultsRef = useRef<HTMLDivElement>(null);
  const activeFilters = filterKeys
    .filter(key => Boolean(filters[key]))
    .map(key => ({
      key,
      label: key === 'search'
        ? `Búsqueda: ${filters[key]}`
        : `${filterLabels[key]}: ${filters[key]}${key === 'edadMinimaMeses' || key === 'edadMaximaMeses' ? ' meses' : ''}`,
    }));

  function applyFilters(next: Filters) {
    navigate(catalogPath(next, 1, grid && next.especie === filters.especie));
  }

  function clearFilters() {
    const next = { ...empty, especie: filters.especie };
    setDraft(next);
    applyFilters(next);
  }
  useEffect(() => setDraft(filters), [filters]);
  useEffect(() => {
    if (location.state?.focusResults) {
      resultsRef.current?.focus({ preventScroll: true });
      resultsRef.current?.scrollIntoView({ block: 'start' });
    }
  }, [location.key, location.state]);
  useEffect(() => {
    if (!speciesView) {
      setItems([]);
      setTotal(0);
      setLoading(false);
      setSelection(null);
      return;
    }
    const controller = new AbortController();
    setLoading(true);
    setError('');
    const query = new URLSearchParams({ page: String(page), pageSize: String(pageSize) });
    filterKeys.forEach(k => {
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
          if (featured) {
            setSelection(current => {
              const matchesSpecies = current?.species === filters.especie;
              if (matchesSpecies && result.items.some(pet => pet.id === current.id)) return current;
              const next = matchesSpecies ? result.items[0]
                : result.items.find(pet => pet.estado === 'Disponible') ?? result.items[0];
              return next ? { species: filters.especie, id: next.id } : null;
            });
          }
        }
      })
      .catch((e: unknown) => {
        if (!controller.signal.aborted) setError(errorMessage(e));
      })
      .finally(() => {
        if (!controller.signal.aborted) {
          setSettledRequest(catalogRequest);
          setLoading(false);
        }
      });
    return () => controller.abort();
  }, [catalogRequest, featured, filters, page, refresh, speciesView]);
  useEffect(() => {
    const focus = () => setRefresh(v => v + 1);
    window.addEventListener('focus', focus);
    return () => window.removeEventListener('focus', focus);
  }, []);
  return (
    <section className="catalog-page" id="catalogo" aria-label="Catálogo de mascotas">
      <div className="catalog-species" role="group" aria-label="Explorar por especie">
        {speciesCards.map(({ species, label, style }) => (
          <Link
            key={species}
            className={`catalog-species__card catalog-species__card--${style}`}
            aria-label={label}
            aria-describedby={`catalog-species-${style}-description`}
            aria-current={filters.especie === species ? 'true' : undefined}
            to={catalogPath({ ...filters, especie: species })}
            state={{ focusResults: true }}
          >
            <img src="/images/catalogo-especies.png" alt="" />
            <span id={`catalog-species-${style}-description`} className="visually-hidden">
              Dale una segunda oportunidad
            </span>
          </Link>
        ))}
      </div>
      {speciesView && (
        <>
          <div className="catalog-layout">
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
                  applyFilters(draft);
                }}
              >
                <fieldset disabled={busy}>
                  <div className="catalog-filter-fields">
                    <label className="catalog-filter-fields__search">
                      Buscar mascota o albergue
                      <input
                        maxLength={120}
                        value={draft.search}
                        onChange={e => setDraft({ ...draft, search: e.target.value })}
                      />
                    </label>
                    {(['especie', 'tamano', 'temperamento'] as const).map(key => (
                      <label key={key} className={key === 'temperamento' ? 'catalog-filter-fields__temperament' : undefined}>
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
                          <option value="">{key === 'especie' ? 'Elegir especie' : 'Todos'}</option>
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
            <div className="catalog-results" role="region" aria-label="Resultados del catálogo"
              aria-busy={busy} tabIndex={-1} ref={resultsRef}>
              <div className="catalog-results__top">
                <p role="status">
                  {busy ? 'Buscando compañeros…' : total === 1 ? '1 mascota encontrada' : `${total} mascotas encontradas`}
                </p>
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
                      onClick={() => applyFilters({ ...filters, [filter.key]: '' })}
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
              ) : featured && items.length > 0 ? (
                <CatalogoDestacado
                  mascotas={items}
                  seleccionada={items.find(pet => pet.id === selection?.id) ?? items[0]}
                  elegir={id => setSelection({ species: filters.especie, id })}
                  verTodos={catalogPath(filters, 1, true)}
                />
              ) : (
                <>
                  {items.length === 0 && (
                    <div className="workspace-empty">
                      <span aria-hidden="true">⌕</span>
                      <h2>Sigamos buscando</h2>
                      <p>No hay coincidencias. Prueba otros filtros.</p>
                    </div>
                  )}
                  {speciesView && grid && (
                    <Link className="catalog-featured__all" to={catalogPath(filters)} state={{ focusResults: true }}>
                      Volver a destacados
                    </Link>
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
                      onClick={() => navigate(catalogPath(filters, page - 1, grid))}
                    >
                      Anterior
                    </button>
                    <span>
                      Página {page} de {Math.max(1, Math.ceil(total / pageSize))}
                    </span>
                    <button
                      className="secondary"
                      disabled={page * pageSize >= total}
                      onClick={() => navigate(catalogPath(filters, page + 1, grid))}
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
            <aside className="catalog-care-note">
              <span aria-hidden="true">♡</span>
              <strong>Un compromiso para toda la vida</strong>
              <p>Conoce su historia, conversa con el albergue y elige con responsabilidad.</p>
            </aside>
          </div>
          <AyudaParaAdoptar mascotas={busy || error ? [] : items} />
        </>
      )}
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
