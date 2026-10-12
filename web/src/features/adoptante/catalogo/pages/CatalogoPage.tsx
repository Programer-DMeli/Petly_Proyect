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
  const load = useCallback(async () => {
    setError('');
    try {
      setPet(await spring<Pet>('/api/catalogo/' + String(id)));
    } catch (e) {
      setError(errorMessage(e));
    }
  }, [id]);
  useEffect(() => {
    void load();
  }, [load]);
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
        </>
      ) : (
        <button onClick={() => void load()}>Cargar detalle</button>
      )}
    </section>
  );
}
