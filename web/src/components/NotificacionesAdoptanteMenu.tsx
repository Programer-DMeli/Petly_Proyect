import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { errorMessage } from '../services/http';
import {
  getAvisosAdoptante,
  marcarAvisoComoLeido,
  marcarTodosLosAvisosComoLeidos,
} from '../services/notificacionesApi';
import type { AvisoAdoptante, BandejaAvisosAdoptante } from '../services/types';

type Props = {
  open: boolean;
  onToggle: (button: HTMLButtonElement) => void;
};

function fecha(value: string) {
  return new Date(value).toLocaleString('es-PE', { dateStyle: 'medium', timeStyle: 'short' });
}

export default function NotificacionesAdoptanteMenu({ open, onToggle }: Props) {
  const navigate = useNavigate();
  const [bandeja, setBandeja] = useState<BandejaAvisosAdoptante | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [markingAll, setMarkingAll] = useState(false);

  const cargarAvisos = useCallback(async () => {
    try {
      setBandeja(await getAvisosAdoptante());
      setError('');
    } catch (cause) {
      setError(errorMessage(cause));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void cargarAvisos();
    const interval = window.setInterval(() => void cargarAvisos(), 60_000);
    return () => window.clearInterval(interval);
  }, [cargarAvisos]);

  const noLeidas = bandeja?.no_leidas ?? 0;
  const avisos = bandeja?.results ?? [];

  async function marcarTodasComoLeidas() {
    if (markingAll || noLeidas === 0) return;
    setMarkingAll(true);
    setError('');
    try {
      await marcarTodosLosAvisosComoLeidos();
      const ahora = new Date().toISOString();
      setBandeja(actual => actual ? {
        ...actual,
        no_leidas: 0,
        results: actual.results.map(aviso => ({ ...aviso, leido_en: aviso.leido_en ?? ahora })),
      } : actual);
    } catch (cause) {
      setError(errorMessage(cause));
    } finally {
      setMarkingAll(false);
    }
  }

  async function abrirCoincidencia(aviso: AvisoAdoptante) {
    if (!aviso.mascota_id) return;
    if (aviso.leido_en === null) {
      try {
        const actualizado = await marcarAvisoComoLeido(aviso.id);
        setBandeja(actual => actual ? {
          ...actual,
          no_leidas: Math.max(0, actual.no_leidas - 1),
          results: actual.results.map(item => item.id === aviso.id
            ? { ...item, leido_en: actualizado.leido_en ?? new Date().toISOString() }
            : item),
        } : actual);
      } catch (cause) {
        setError(errorMessage(cause));
      }
    }
    navigate(`/adoptante/catalogo/${aviso.mascota_id}`);
  }

  return (
    <div className="workspace-dropdown workspace-notifications">
      <button
        type="button"
        className="workspace-notifications__trigger"
        aria-label={`Avisos${noLeidas ? `, ${noLeidas} sin leer` : ''}`}
        aria-expanded={open}
        aria-controls="workspace-notifications-menu"
        onClick={event => onToggle(event.currentTarget)}
      >
        <span aria-hidden="true">🔔</span>
        <span>Avisos</span>
        {noLeidas > 0 && <span className="workspace-notifications__count" aria-label={`${noLeidas} sin leer`}>{noLeidas}</span>}
      </button>
      {open && (
        <div id="workspace-notifications-menu" className="workspace-menu workspace-menu--notifications">
          <p>Avisos de adopción</p>
          {noLeidas > 0 && <button
            type="button"
            className="workspace-notifications__mark-all"
            disabled={markingAll}
            onClick={() => void marcarTodasComoLeidas()}
          >
            {markingAll ? 'Actualizando…' : 'Marcar todos como leídos'}
          </button>}
          {loading ? <p role="status">Cargando avisos…</p>
            : error ? <p role="alert">No se pudieron cargar los avisos.</p>
              : avisos.length === 0 ? <p>No tienes avisos por ahora.</p>
                : <div className="workspace-notifications__list">
                  {avisos.map((aviso: AvisoAdoptante) => (
                    aviso.tipo === 'NUEVA_COINCIDENCIA' && aviso.mascota_id ? <button
                      type="button"
                      className={`workspace-notifications__item workspace-notifications__item--action${aviso.leido_en ? '' : ' is-unread'}`}
                      key={aviso.id}
                      onClick={() => void abrirCoincidencia(aviso)}
                    >
                      <strong>{aviso.titulo}</strong>
                      <p>{aviso.mensaje}</p>
                      {aviso.mascota_nombre && <span>Mascota: {aviso.mascota_nombre}</span>}
                      <time dateTime={aviso.ocurrido_en}>{fecha(aviso.ocurrido_en)}</time>
                    </button> : <article
                      className={`workspace-notifications__item${aviso.leido_en ? '' : ' is-unread'}`}
                      key={aviso.id}
                    >
                      <strong>{aviso.titulo}</strong>
                      <p>{aviso.mensaje}</p>
                      {aviso.mascota_nombre && <span>Mascota: {aviso.mascota_nombre}</span>}
                      <time dateTime={aviso.ocurrido_en}>{fecha(aviso.ocurrido_en)}</time>
                    </article>
                  ))}
                </div>}
        </div>
      )}
    </div>
  );
}
