import { useCallback, useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { avisosConectados, observarAvisos } from '../services/avisosEnVivo';
import { errorMessage } from '../services/http';
import {
  getAvisos,
  INTERVALO_AVISOS_MS,
  getAlertasCompatibilidadAlbergue,
  marcarAlertaCompatibilidadComoLeida,
  marcarTodasLasAlertasCompatibilidadComoLeidas,
  marcarAvisoComoLeido,
  marcarTodosLosAvisosComoLeidos,
} from '../services/notificacionesApi';
import { fecha as fechaSeguimiento } from '../features/seguimiento/cronogramaDatos';
import type { AvisoAdoptante } from '../services/types';

type AvisoBandeja = Pick<AvisoAdoptante, 'id' | 'solicitud_id' | 'mascota_id' | 'mascota_nombre' |
  'tipo' | 'titulo' | 'mensaje' | 'ocurrido_en' | 'leido_en' | 'seguimiento_id' | 'programado_para' | 'fecha_limite'>;

type Props = {
  role: 'ADOPTANTE' | 'ALBERGUE';
  open: boolean;
  onToggle: (button: HTMLButtonElement) => void;
};

function fecha(value: string) {
  return new Date(value).toLocaleString('es-PE', { dateStyle: 'medium', timeStyle: 'short' });
}

export default function NotificacionesMenu({ role, open, onToggle }: Props) {
  const navigate = useNavigate();
  const [bandeja, setBandeja] = useState<{ results: AvisoBandeja[]; no_leidas: number } | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [markingAll, setMarkingAll] = useState(false);

  const carga = useRef(0);

  const cargarAvisos = useCallback(async () => {
    const version = ++carga.current;
    try {
      const [solicitudes, compatibilidad] = await Promise.all([
        getAvisos(),
        role === 'ALBERGUE' ? getAlertasCompatibilidadAlbergue() : null,
      ]);
      if (version !== carga.current) return;
      const alertas: AvisoBandeja[] = (compatibilidad?.results ?? []).map(alerta => ({
        id: alerta.id,
        solicitud_id: alerta.solicitudId,
        mascota_id: alerta.mascotaId,
        mascota_nombre: alerta.mascotaNombre,
        tipo: 'COMPATIBILIDAD',
        titulo: `${alerta.porcentaje}% de compatibilidad`,
        mensaje: `${alerta.adoptanteNombre} postuló para ${alerta.mascotaNombre}.`,
        ocurrido_en: `${alerta.ocurridoEn}-05:00`,
        leido_en: alerta.leidoEn,
      }));
      setBandeja({
        results: [...solicitudes.results, ...alertas]
          .sort((a, b) => Date.parse(b.ocurrido_en) - Date.parse(a.ocurrido_en)).slice(0, 30),
        no_leidas: solicitudes.no_leidas + (compatibilidad?.no_leidas ?? 0),
      });
      setError('');
    } catch (cause) {
      if (version === carga.current) setError(errorMessage(cause));
    } finally {
      if (version === carga.current) setLoading(false);
    }
  }, [role]);

  useEffect(() => {
    const actualizar = () => {
      if (!document.hidden && (role === 'ALBERGUE' || !avisosConectados())) void cargarAvisos();
    };
    const dejarDeObservar = role === 'ADOPTANTE' ? observarAvisos(() => void cargarAvisos()) : undefined;
    void cargarAvisos();
    const interval = window.setInterval(actualizar, INTERVALO_AVISOS_MS);
    document.addEventListener('visibilitychange', actualizar);
    return () => {
      dejarDeObservar?.();
      window.clearInterval(interval);
      document.removeEventListener('visibilitychange', actualizar);
      carga.current += 1;
    };
  }, [cargarAvisos, role]);

  useEffect(() => {
    if (open) void cargarAvisos();
  }, [open, cargarAvisos]);

  const noLeidas = bandeja?.no_leidas ?? 0;
  const avisos = bandeja?.results ?? [];

  async function marcarTodasComoLeidas() {
    if (markingAll || noLeidas === 0) return;
    setMarkingAll(true);
    const operaciones = [marcarTodosLosAvisosComoLeidos()];
    if (role === 'ALBERGUE') operaciones.push(marcarTodasLasAlertasCompatibilidadComoLeidas());
    const resultados = await Promise.allSettled(operaciones);
    await cargarAvisos();
    if (resultados.some(resultado => resultado.status === 'rejected')) {
      setError('No se pudieron marcar todos los avisos como leídos.');
    }
    setMarkingAll(false);
  }

  async function abrirAviso(aviso: AvisoBandeja, destino: string) {
    void navigate(destino);
    if (aviso.leido_en !== null) return;
    try {
      if (aviso.tipo === 'COMPATIBILIDAD') await marcarAlertaCompatibilidadComoLeida(aviso.id);
      else await marcarAvisoComoLeido(aviso.id);
      await cargarAvisos();
    } catch (cause) {
      setError(errorMessage(cause));
    }
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
                  {avisos.map(aviso => {
                    const programacion = aviso.tipo === 'SEGUIMIENTO_PROGRAMADO';
                    const destino = role === 'ALBERGUE' && aviso.solicitud_id
                      ? `/albergue/solicitudes?solicitud=${aviso.solicitud_id}`
                      : aviso.seguimiento_id
                      ? `/adoptante/seguimientos-postadopcion?${programacion ? 'seguimiento' : 'entrega'}=${aviso.seguimiento_id}#seguimiento-${aviso.seguimiento_id}`
                      : aviso.tipo === 'NUEVA_COINCIDENCIA' && aviso.mascota_id
                        ? `/adoptante/catalogo/${aviso.mascota_id}` : null;
                    const clase = `workspace-notifications__item${aviso.leido_en ? '' : ' is-unread'}`;
                    const contenido = <>
                      <strong>{aviso.titulo}</strong>
                      <p>{aviso.mensaje}</p>
                      {aviso.tipo === 'COMPATIBILIDAD' && <span>Ver solicitud</span>}
                      {aviso.mascota_nombre && aviso.tipo !== 'COMPATIBILIDAD' && <span>Mascota: {aviso.mascota_nombre}</span>}
                      {programacion ? aviso.programado_para && <span>Fecha programada: {fechaSeguimiento(aviso.programado_para)} (Lima)</span>
                        : aviso.fecha_limite && <span>Fecha límite: {fechaSeguimiento(aviso.fecha_limite)} (Lima)</span>}
                      <time dateTime={aviso.ocurrido_en}>{fecha(aviso.ocurrido_en)}</time>
                    </>;
                    return destino ? <button type="button" className={`${clase} workspace-notifications__item--action`}
                      key={`${aviso.tipo}-${aviso.id}`} onClick={() => void abrirAviso(aviso, destino)}>{contenido}</button>
                      : <article className={clase} key={`${aviso.tipo}-${aviso.id}`}>{contenido}</article>;
                  })}
                </div>}
        </div>
      )}
    </div>
  );
}
