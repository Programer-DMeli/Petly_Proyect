import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { errorMessage } from '../services/http';
import {
  getAlertasCompatibilidadAlbergue,
  marcarAlertaCompatibilidadComoLeida,
} from '../services/notificacionesApi';
import type { AlertaCompatibilidadAlbergue, BandejaCompatibilidadAlbergue } from '../services/types';

type Props = { open: boolean; onToggle: (button: HTMLButtonElement) => void };

function fecha(value: string) {
  return new Date(value).toLocaleString('es-PE', { dateStyle: 'medium', timeStyle: 'short' });
}

export default function AlertasCompatibilidadAlbergueMenu({ open, onToggle }: Props) {
  const navigate = useNavigate();
  const [bandeja, setBandeja] = useState<BandejaCompatibilidadAlbergue | null>(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);

  const cargar = useCallback(async () => {
    try {
      setBandeja(await getAlertasCompatibilidadAlbergue());
      setError('');
    } catch (cause) {
      setError(errorMessage(cause));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void cargar();
    const interval = window.setInterval(() => void cargar(), 60_000);
    return () => window.clearInterval(interval);
  }, [cargar]);

  async function abrir(alerta: AlertaCompatibilidadAlbergue) {
    if (!alerta.leidoEn) {
      try {
        const actualizada = await marcarAlertaCompatibilidadComoLeida(alerta.id);
        setBandeja(actual => actual ? {
          ...actual,
          no_leidas: Math.max(0, actual.no_leidas - 1),
          results: actual.results.map(item => item.id === alerta.id ? actualizada : item),
        } : actual);
      } catch (cause) {
        setError(errorMessage(cause));
      }
    }
    navigate(`/albergue/solicitudes?solicitud=${alerta.solicitudId}`);
  }

  const noLeidas = bandeja?.no_leidas ?? 0;
  return (
    <div className="workspace-dropdown workspace-notifications">
      <button
        type="button"
        className="workspace-notifications__trigger"
        aria-label={`Alertas de compatibilidad${noLeidas ? `, ${noLeidas} sin leer` : ''}`}
        aria-expanded={open}
        aria-controls="workspace-compatibility-alerts"
        onClick={event => onToggle(event.currentTarget)}
      >
        <span aria-hidden="true">🔔</span>
        <span>Compatibilidad</span>
        {noLeidas > 0 && <span className="workspace-notifications__count">{noLeidas}</span>}
      </button>
      {open && (
        <div id="workspace-compatibility-alerts" className="workspace-menu workspace-menu--notifications">
          <p>Candidatos con alta compatibilidad</p>
          {loading ? <p role="status">Cargando alertas…</p>
            : error ? <p role="alert">No se pudieron cargar las alertas.</p>
              : !bandeja?.results.length ? <p>No tienes alertas por ahora.</p>
                : <div className="workspace-notifications__list">
                  {bandeja.results.map(alerta => (
                    <button
                      type="button"
                      key={alerta.id}
                      className={`workspace-notifications__item workspace-notifications__item--action${alerta.leidoEn ? '' : ' is-unread'}`}
                      onClick={() => void abrir(alerta)}
                    >
                      <strong>{alerta.porcentaje}% de compatibilidad</strong>
                      <p>{alerta.adoptanteNombre} postuló para {alerta.mascotaNombre}.</p>
                      <span>Ver solicitud</span>
                      <time dateTime={alerta.ocurridoEn}>{fecha(alerta.ocurridoEn)}</time>
                    </button>
                  ))}
                </div>}
        </div>
      )}
    </div>
  );
}
