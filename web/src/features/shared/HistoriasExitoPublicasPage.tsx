import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import Feedback from '../../components/Feedback';
import { djangoBase, errorMessage, mediaUrl, request } from '../../services/http';

type FotoPublicada = { id: number; descarga_url: string; mime_type: string };
type HistoriaPublicada = {
  id: number;
  mascota_nombre: string;
  albergue_nombre: string;
  historia: string;
  publicado_en: string;
  fotos: FotoPublicada[];
};
type Page = { results: HistoriaPublicada[]; next: string | null };

const API = '/api/contratos/casos-exito/publicados/';
const PAGE_SIZE = 12;

function paginaSiguiente(next: string | null) {
  if (!next) return null;
  const page = Number(new URL(next, djangoBase).searchParams.get('page'));
  return Number.isSafeInteger(page) && page > 0 ? page : null;
}

function fechaPublicacion(value: string) {
  const fecha = new Date(value);
  if (Number.isNaN(fecha.getTime())) return value;
  return new Intl.DateTimeFormat('es-PE', { dateStyle: 'long' }).format(fecha);
}

export default function HistoriasExitoPublicasPage() {
  const [historias, setHistorias] = useState<HistoriaPublicada[]>([]);
  const [siguientePagina, setSiguientePagina] = useState<number | null>(1);
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [error, setError] = useState('');

  const cargar = useCallback(async (pagina: number, anexar: boolean, signal?: AbortSignal) => {
    if (anexar) setLoadingMore(true);
    else setLoading(true);
    setError('');
    try {
      const response = await request<Page>(
        djangoBase,
        `${API}?page=${pagina}&page_size=${PAGE_SIZE}`,
        { signal },
        false,
      );
      setHistorias(actuales => anexar ? [...actuales, ...response.results] : response.results);
      setSiguientePagina(paginaSiguiente(response.next));
    } catch (cause) {
      if (!signal?.aborted) setError(errorMessage(cause));
    } finally {
      if (!signal?.aborted) {
        setLoading(false);
        setLoadingMore(false);
      }
    }
  }, []);

  useEffect(() => {
    const controller = new AbortController();
    void cargar(1, false, controller.signal);
    return () => controller.abort();
  }, [cargar]);

  return <section className="success-stories-page">
    <header className="success-stories-heading">
      <p className="eyebrow">Historias que unen</p>
      <h1>Una adopción, una nueva historia</h1>
      <p className="muted">Conoce las experiencias que adoptantes y albergues comparten con la comunidad Petly.</p>
      <div className="actions">
        <Link className="button" to="/registro">Quiero adoptar</Link>
        <Link className="button secondary" to="/">Volver al inicio</Link>
      </div>
    </header>

    <Feedback error={error} />
    {loading ? <p role="status">Cargando historias…</p>
      : historias.length === 0 ? <p className="success-stories-empty">Todavía no hay historias publicadas. Pronto podrás conocer nuevas experiencias de adopción.</p>
        : <div className="success-stories-grid">
          {historias.map(historia => <article className="card success-story-card" key={historia.id}>
            <div className="success-story-card__heading">
              <div>
                <h2>{historia.mascota_nombre}</h2>
                <p className="muted">Adopción con {historia.albergue_nombre}</p>
              </div>
              <time dateTime={historia.publicado_en}>{fechaPublicacion(historia.publicado_en)}</time>
            </div>
            <p className="case-story">{historia.historia}</p>
            {historia.fotos.length > 0 && <div className="case-story-photos">
              {historia.fotos.map((foto, index) => <figure key={foto.id}>
                <img src={mediaUrl(foto.descarga_url)}
                  alt={`Foto ${index + 1} de ${historia.mascota_nombre}`} loading="lazy" />
              </figure>)}
            </div>}
          </article>)}
        </div>}
    {siguientePagina !== null && !loading && <button type="button" className="secondary"
      disabled={loadingMore} onClick={() => void cargar(siguientePagina, true)}>
      {loadingMore ? 'Cargando…' : 'Ver más historias'}
    </button>}
  </section>;
}
