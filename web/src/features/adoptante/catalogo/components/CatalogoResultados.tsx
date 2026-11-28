import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import Feedback from '../../../../components/Feedback';
import PetCard from '../../../../components/PetCard';
import { errorMessage, spring } from '../../../../services/http';
import type { Pet } from '../../../../services/types';
import CatalogoDestacado from './CatalogoDestacado';

type Resultado = { items: Pet[]; total: number };

function useResultados(request: string | null, revision: number) {
  const [state, setState] = useState<{
    request: string;
    revision: number;
    data?: Resultado;
    error?: string;
  }>();

  useEffect(() => {
    if (!request) return;
    const controller = new AbortController();
    void spring<Resultado>(request, { signal: controller.signal })
      .then(data => {
        if (!controller.signal.aborted) setState({ request, revision, data });
      })
      .catch(error => {
        if (!controller.signal.aborted) setState({ request, revision, error: errorMessage(error) });
      });
    return () => controller.abort();
  }, [request, revision]);

  return state?.request === request && state.revision === revision ? state : undefined;
}

interface Props {
  id: string;
  titulo: string;
  request: (page: number) => string;
  href: (page: number, grid: boolean) => string;
  grid: boolean;
  page: number;
  pageSize: number;
  revision: number;
  onPreview: (id: string, items?: Pet[]) => void;
}

export default function CatalogoResultados({ id, titulo, request, href, grid, page, pageSize, revision, onPreview }: Props) {
  const [retry, setRetry] = useState(0);
  const [selection, setSelection] = useState<number>();
  const preview = useResultados(request(1), revision + retry);
  const otherPage = useResultados(grid && page > 1 ? request(page) : null, revision + retry);
  const result = grid && page > 1 ? otherPage : preview;
  const initialItems = preview?.data?.items;
  const selected = initialItems?.find(pet => pet.id === selection);
  const featured = selected ?? (selection == null
    ? initialItems?.find(pet => pet.estado === 'Disponible') ?? initialItems?.[0]
    : initialItems?.[0]);

  useEffect(() => {
    if (preview?.data) setSelection(featured?.id);
  }, [featured?.id, preview?.data]);
  useEffect(() => {
    onPreview(id, initialItems);
    return () => onPreview(id);
  }, [id, initialItems, onPreview]);

  const busy = !result;
  const total = result?.data?.total ?? 0;
  const items = result?.data?.items ?? [];
  const focusResults = { focusResults: true };

  return (
    <section className="catalog-results catalog-results--species" id={id}
      aria-label={`Resultados de ${titulo.toLowerCase()}`} aria-busy={busy} tabIndex={-1}>
      <div className="catalog-results__top">
        <h2>{titulo}</h2>
        <p role="status">
          {busy ? 'Buscando compañeros…' : result.error ? 'Resultados no disponibles'
            : total === 1 ? '1 mascota encontrada' : `${total} mascotas encontradas`}
        </p>
      </div>
      <Feedback error={result?.error} />
      {busy ? (
        <div className="catalog-loading" role="status">Cargando mascotas…</div>
      ) : result.error ? (
        <button type="button" onClick={() => setRetry(value => value + 1)}>Reintentar</button>
      ) : (
        <>
          {grid && (
            <Link className="catalog-featured__all" to={href(1, false)} state={focusResults}>
              Volver a destacados
            </Link>
          )}
          {items.length === 0 ? (
            <div className="workspace-empty">
              <span aria-hidden="true">⌕</span>
              <h3>Sigamos buscando</h3>
              <p>No hay coincidencias. Prueba otros filtros.</p>
            </div>
          ) : grid ? (
            <div className="pet-card-grid">{items.map(pet => <PetCard key={pet.id} pet={pet} />)}</div>
          ) : featured && (
            <CatalogoDestacado
              mascotas={items}
              seleccionada={featured}
              elegir={setSelection}
              browseId={`${id}-carrusel`}
            />
          )}
          {grid && (
            <nav className="catalog-pagination" aria-label={`Páginas de ${titulo.toLowerCase()}`}>
              {page === 1
                ? <button className="secondary" disabled>Anterior</button>
                : <Link className="button secondary" to={href(page - 1, true)} state={focusResults}>Anterior</Link>}
              <span>Página {page} de {Math.max(1, Math.ceil(total / pageSize))}</span>
              {page * pageSize >= total
                ? <button className="secondary" disabled>Siguiente</button>
                : <Link className="button secondary" to={href(page + 1, true)} state={focusResults}>Siguiente</Link>}
              <button className="catalog-refresh secondary" type="button"
                onClick={() => setRetry(value => value + 1)}>Actualizar</button>
            </nav>
          )}
        </>
      )}
    </section>
  );
}
