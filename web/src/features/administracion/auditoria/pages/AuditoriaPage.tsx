import { useCallback, useEffect, useState, type FormEvent } from 'react';
import Feedback from '../../../../components/Feedback';
import { errorMessage, spring } from '../../../../services/http';

type EventoAuditoria = {
  eventoId: string;
  actorUsuarioId: number | null;
  actorRol: string;
  accion: string;
  origen: string;
  recursoTipo: string | null;
  recursoId: string | null;
  ocurridoEn: string;
};

type Pagina<T> = {
  content: T[];
  number: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
  empty: boolean;
};

type Filtros = { actorId: string; accion: string; desde: string; hasta: string };

const FILTROS_VACIOS: Filtros = { actorId: '', accion: '', desde: '', hasta: '' };
const TAMANO_PAGINA = 20;
const FECHA_HORA = new Intl.DateTimeFormat('es-PE', {
  dateStyle: 'medium',
  timeStyle: 'short',
  timeZone: 'America/Lima',
});

function fechaHora(value: string) {
  // LocalDateTime del backend se guarda en la zona operativa de Petly (Lima).
  const parsed = new Date(`${value}-05:00`);
  return Number.isNaN(parsed.getTime()) ? value : FECHA_HORA.format(parsed);
}

export default function AuditoriaPage() {
  const [filtrosFormulario, setFiltrosFormulario] = useState<Filtros>(FILTROS_VACIOS);
  const [filtros, setFiltros] = useState<Filtros>(FILTROS_VACIOS);
  const [pagina, setPagina] = useState(0);
  const [resultado, setResultado] = useState<Pagina<EventoAuditoria> | null>(null);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState('');
  const [recarga, setRecarga] = useState(0);

  const cargar = useCallback(async (signal: AbortSignal) => {
    setCargando(true);
    setError('');
    const params = new URLSearchParams({ pagina: String(pagina), tamano: String(TAMANO_PAGINA) });
    if (filtros.actorId) params.set('actorId', filtros.actorId);
    if (filtros.accion) params.set('accion', filtros.accion.trim());
    if (filtros.desde) params.set('desde', filtros.desde);
    if (filtros.hasta) params.set('hasta', filtros.hasta);

    try {
      const data = await spring<Pagina<EventoAuditoria>>(`/api/admin/auditoria?${params}`, { signal });
      if (!signal.aborted) setResultado(data);
    } catch (cause) {
      if (!signal.aborted) setError(errorMessage(cause));
    } finally {
      if (!signal.aborted) setCargando(false);
    }
  }, [filtros, pagina]);

  useEffect(() => {
    const controller = new AbortController();
    void cargar(controller.signal);
    return () => controller.abort();
  }, [cargar, recarga]);

  function aplicarFiltros(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setPagina(0);
    setFiltros({ ...filtrosFormulario, accion: filtrosFormulario.accion.trim() });
  }

  function limpiarFiltros() {
    setFiltrosFormulario(FILTROS_VACIOS);
    setFiltros(FILTROS_VACIOS);
    setPagina(0);
  }

  const total = resultado?.totalElements ?? 0;
  const totalPaginas = resultado?.totalPages ?? 0;

  return <main className="admin-audit">
    <header className="admin-audit__heading">
      <p className="eyebrow">Administración · Seguridad</p>
      <h1>Registro de actividad</h1>
      <p className="muted">Consulta las acciones sensibles registradas en Petly. Este registro es de solo lectura.</p>
    </header>

    <Feedback error={error} />
    {error && <button type="button" className="secondary" onClick={() => setRecarga(value => value + 1)}>
      Reintentar
    </button>}

    <section className="card admin-audit__panel" aria-labelledby="auditoria-filtros-titulo">
      <div className="admin-audit__toolbar">
        <div>
          <h2 id="auditoria-filtros-titulo">Filtrar eventos</h2>
          <p className="muted">Busca por identificador del actor, acción y rango de fechas.</p>
        </div>
        <button type="button" className="secondary" onClick={() => setRecarga(value => value + 1)} disabled={cargando}>
          Actualizar
        </button>
      </div>

      <form className="admin-audit__filters" onSubmit={aplicarFiltros}>
        <label>
          ID del actor
          <input
            type="number"
            min="1"
            step="1"
            inputMode="numeric"
            value={filtrosFormulario.actorId}
            onChange={event => setFiltrosFormulario(value => ({ ...value, actorId: event.target.value }))}
            placeholder="Cualquier actor"
          />
        </label>
        <label>
          Acción
          <input
            type="search"
            maxLength={80}
            value={filtrosFormulario.accion}
            onChange={event => setFiltrosFormulario(value => ({ ...value, accion: event.target.value }))}
            placeholder="Ej. SOLICITUD o KYC"
          />
        </label>
        <label>
          Desde
          <input
            type="date"
            value={filtrosFormulario.desde}
            max={filtrosFormulario.hasta || undefined}
            onChange={event => setFiltrosFormulario(value => ({ ...value, desde: event.target.value }))}
          />
        </label>
        <label>
          Hasta
          <input
            type="date"
            value={filtrosFormulario.hasta}
            min={filtrosFormulario.desde || undefined}
            onChange={event => setFiltrosFormulario(value => ({ ...value, hasta: event.target.value }))}
          />
        </label>
        <div className="admin-audit__filter-actions">
          <button type="submit" disabled={cargando}>Aplicar filtros</button>
          <button type="button" className="secondary" onClick={limpiarFiltros} disabled={cargando}>
            Limpiar
          </button>
        </div>
      </form>
    </section>

    <section className="card admin-audit__results" aria-labelledby="auditoria-resultados-titulo" aria-busy={cargando}>
      <div className="admin-audit__toolbar">
        <div>
          <h2 id="auditoria-resultados-titulo">Eventos registrados</h2>
          <p className="muted" aria-live="polite">
            {cargando ? 'Cargando actividad…' : error ? 'No se pudieron cargar los eventos.'
              : `${total.toLocaleString('es-PE')} eventos encontrados`}
          </p>
        </div>
      </div>

      {cargando ? <p role="status">Cargando registros…</p>
        : !error && resultado?.empty ? <p className="admin-audit__empty">No hay eventos que coincidan con estos filtros.</p>
          : !error && resultado && <>
            <div className="admin-audit__table-wrap">
              <table className="admin-audit__table">
                <thead><tr>
                  <th scope="col">Fecha y hora</th>
                  <th scope="col">Actor</th>
                  <th scope="col">Acción</th>
                  <th scope="col">Origen</th>
                  <th scope="col">Recurso</th>
                </tr></thead>
                <tbody>{resultado.content.map(evento => <tr key={evento.eventoId}>
                  <td><time dateTime={evento.ocurridoEn}>{fechaHora(evento.ocurridoEn)}</time></td>
                  <td>
                    <strong>{evento.actorUsuarioId == null ? 'Sistema' : `Usuario #${evento.actorUsuarioId}`}</strong>
                    <small>{evento.actorRol}</small>
                  </td>
                  <td><code>{evento.accion}</code></td>
                  <td>{evento.origen}</td>
                  <td>{[evento.recursoTipo, evento.recursoId && `#${evento.recursoId}`].filter(Boolean).join(' ') || '—'}</td>
                </tr>)}</tbody>
              </table>
            </div>

            <nav className="admin-audit__pagination" aria-label="Páginas del registro de actividad">
              <span>Página {pagina + 1} de {Math.max(totalPaginas, 1)}</span>
              <div>
                <button type="button" className="secondary" onClick={() => setPagina(value => Math.max(0, value - 1))}
                  disabled={cargando || pagina === 0}>
                  Anterior
                </button>
                <button type="button" className="secondary" onClick={() => setPagina(value => value + 1)}
                  disabled={cargando || resultado.last}>
                  Siguiente
                </button>
              </div>
            </nav>
          </>}
    </section>
  </main>;
}
