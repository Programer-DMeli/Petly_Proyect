import AyudaParaAdoptar from '../components/AyudaParaAdoptar';
import CatalogoResultados from '../components/CatalogoResultados';
import ConsejosCompatibilidad from '../../../../components/ConsejosCompatibilidad';
import { FavoriteHeart } from '../../../../components/FavoritesProvider';
import { useCallback, useEffect, useMemo, useState } from 'react';
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
const speciesCards = [
  { species: 'Gato', label: 'Ver gatos', title: 'Gatos', style: 'cat', id: 'catalogo-gatos' },
  { species: 'Perro', label: 'Ver perros', title: 'Perros', style: 'dog', id: 'catalogo-perros' },
] as const;
const filterKeys = Object.keys(empty) as (keyof Filters)[];

function catalogParams(filters: Filters, page: number) {
  const params = new URLSearchParams();
  for (const key of filterKeys) {
    if (filters[key]) params.set(key, filters[key]);
  }
  if (page > 1) params.set('page', String(page));
  return params;
}

function catalogPath(filters: Filters, page = 1, grid = false) {
  const params = catalogParams(filters, page);
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
  const grid = params.get('vista') === 'todos';

  const sections = filters.especie === 'Otro'
    ? [...speciesCards, { species: 'Otro', title: 'Otras mascotas', id: 'catalogo-otras' }]
    : speciesCards;
  const target = sections.find(section => section.species === filters.especie)?.id;
  const requestedPage = Number(params.get('page'));
  const page = grid && Number.isInteger(requestedPage) && requestedPage >= 1 && requestedPage <= 1000000
    ? requestedPage
    : 1;
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
  const [options, setOptions] = useState<{
    especies: string[];
    tamanos: string[];
    temperamentos: string[];
  } | null>(null);
  const [optionsError, setOptionsError] = useState('');
  const [refresh, setRefresh] = useState(0);
  const [previews, setPreviews] = useState<Record<string, Pet[] | undefined>>({});
  const updatePreview = useCallback((id: string, items?: Pet[]) => {
    setPreviews(current => current[id] === items ? current : { ...current, [id]: items });
  }, []);
  const helpItems = sections.flatMap(section => previews[section.id] ?? []);

  function applyFilters(next: Filters) {
    const sameSpecies = next.especie === filters.especie;
    navigate(catalogPath(next, 1, grid && sameSpecies), {
      state: next.especie && !sameSpecies ? { focusResults: true } : undefined,
    });
  }

  function clearFilters() {
    const next = { ...empty, especie: filters.especie };
    setDraft(next);
    applyFilters(next);
  }
  useEffect(() => setDraft(filters), [filters]);
  useEffect(() => {
    if (target && location.state?.focusResults) {
      const section = document.getElementById(target);
      section?.focus({ preventScroll: true });
      section?.scrollIntoView({ block: 'start' });
    }
  }, [location.key, location.state, target]);

  useEffect(() => {
    const controller = new AbortController();
    setOptionsError('');
    void spring<{ especies: string[]; tamanos: string[]; temperamentos: string[] }>(
      '/api/catalogo/opciones',
      { signal: controller.signal }
    )
      .then(data => {
        if (!controller.signal.aborted) setOptions(data);
      })
      .catch(error => {
        if (!controller.signal.aborted) setOptionsError(errorMessage(error));
      });
    return () => controller.abort();
  }, [refresh]);
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
            <fieldset>
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
        <Feedback error={optionsError} />
        {sections.map(({ species, title, id }) => {
          const sectionFilters = { ...filters, especie: species };
          const currentGrid = grid && filters.especie === species;
          return (
            <CatalogoResultados
              key={id}
              id={id}
              titulo={title}
              request={number => {
                const query = catalogParams(sectionFilters, number);
                query.set('page', String(number));
                query.set('pageSize', String(pageSize));
                return '/api/catalogo?' + query.toString();
              }}
              href={(number, showGrid) => catalogPath(sectionFilters, number, showGrid)}
              grid={currentGrid}
              page={currentGrid ? page : 1}
              pageSize={pageSize}
              revision={refresh}
              onPreview={updatePreview}
            />
          );
        })}
        <aside className="catalog-care-note">
          <span aria-hidden="true">♡</span>
          <strong>Un compromiso para toda la vida</strong>
          <p>Conoce su historia, conversa con el albergue y elige con responsabilidad.</p>
        </aside>
      </div>
      <AyudaParaAdoptar mascotas={helpItems} />
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
