import { useCallback, useEffect, useState } from 'react';
import Feedback from '../../components/Feedback';
import { django, errorMessage, json } from '../../services/http';
import FotoCasoExitoPrivada, { type FotoCasoExito } from './FotoCasoExitoPrivada';

type Role = 'ALBERGUE' | 'ADMIN';
export type CasoPublicado = {
  id: number;
  mascota_nombre: string;
  historia: string;
  publicado_en: string;
  destacado: boolean;
  fotos: FotoCasoExito[];
};
type CasoPendiente = Pick<CasoPublicado, 'id' | 'mascota_nombre' | 'historia' | 'fotos'> & {
  solicitud_id: number;
  albergue_nombre: string;
  creado_en: string;
};
type Page = { results: CasoPendiente[]; next: string | null };

const API = '/api/contratos/casos-exito/';

export default function RevisionCasosExitoPage({ role, onAprobada }: {
  role: Role;
  onAprobada?: (caso: CasoPublicado) => void;
}) {
  const Titulo = onAprobada ? 'h2' : 'h1';
  const NombreMascota = onAprobada ? 'h3' : 'h2';
  const [casos, setCasos] = useState<CasoPendiente[]>([]);
  const [motivos, setMotivos] = useState<Record<number, string>>({});
  const [loading, setLoading] = useState(true);
  const [busyId, setBusyId] = useState<number | null>(null);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  const cargar = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const resultados: CasoPendiente[] = [];
      let page = 1;
      let next: string | null;
      do {
        const respuesta = await django<Page>(`${API}pendientes/?page=${page}&page_size=100`);
        resultados.push(...respuesta.results);
        next = respuesta.next;
        page += 1;
      } while (next);
      setCasos(resultados);
    } catch (cause) {
      setError(errorMessage(cause));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { void cargar(); }, [cargar]);

  async function revisar(caso: CasoPendiente, decision: 'APROBAR' | 'RECHAZAR') {
    const motivo = motivos[caso.id]?.trim() ?? '';
    if (decision === 'RECHAZAR' && !motivo) {
      setError('Escribe el motivo del rechazo antes de continuar.');
      return;
    }
    setBusyId(caso.id);
    setError('');
    setSuccess('');
    try {
      const revisada = await django<Omit<CasoPublicado, 'publicado_en'> & { publicado_en: string | null }>(`${API}${caso.id}/revisar/`, {
        method: 'POST',
        body: json({ decision, motivo }),
      });
      setCasos(actuales => actuales.filter(item => item.id !== caso.id));
      if (decision === 'APROBAR' && revisada.publicado_en) {
        onAprobada?.({ ...revisada, publicado_en: revisada.publicado_en });
      }
      setSuccess(decision === 'APROBAR'
        ? `La historia de ${caso.mascota_nombre} fue aprobada y publicada.`
        : `La historia de ${caso.mascota_nombre} fue rechazada.`);
      setMotivos(actuales => {
        const copia = { ...actuales };
        delete copia[caso.id];
        return copia;
      });
    } catch (cause) {
      setError(errorMessage(cause));
    } finally {
      setBusyId(null);
    }
  }

  return <section className="card">
    <header>
      {!onAprobada && <p className="eyebrow">Comunidad Petly</p>}
      <Titulo>{onAprobada ? 'Historias pendientes de revisión' : 'Revisión de historias de adopción'}</Titulo>
      <p className="muted">Revisa el relato y las fotos antes de publicar. Las historias rechazadas no aparecen en la sección pública.</p>
    </header>
    <Feedback error={error} success={success} />
    <div className="actions">
      <button type="button" className="secondary" onClick={() => void cargar()} disabled={loading || busyId !== null}>
        Actualizar pendientes
      </button>
    </div>
    {loading ? <p role="status">Cargando historias pendientes…</p>
      : casos.length === 0 ? <p>No hay historias pendientes de revisión.</p>
        : <div className="list-stack">
          {casos.map(caso => <article className="card" key={caso.id}>
            <NombreMascota>{caso.mascota_nombre}</NombreMascota>
            <p className="muted">Albergue: {caso.albergue_nombre} · Solicitud #{caso.solicitud_id} · Enviada {new Date(caso.creado_en).toLocaleDateString('es-PE')}</p>
            <p className="case-story">{caso.historia}</p>
            <h3>Fotos adjuntas ({caso.fotos.length})</h3>
            <div className="case-story-photos">
              {caso.fotos.map(foto => <FotoCasoExitoPrivada key={foto.id} foto={foto}
                alt={`Foto de la historia de ${caso.mascota_nombre}`} />)}
            </div>
            {role === 'ALBERGUE' && <p className="muted">Como albergue, solo puedes revisar historias de tus adopciones.</p>}
            <label htmlFor={`motivo-caso-${caso.id}`}>Motivo del rechazo (obligatorio para rechazar)
              <textarea id={`motivo-caso-${caso.id}`} maxLength={500} value={motivos[caso.id] ?? ''}
                onChange={event => setMotivos(actuales => ({ ...actuales, [caso.id]: event.target.value }))}
                disabled={busyId !== null} />
            </label>
            <div className="actions">
              <button type="button" disabled={busyId !== null}
                onClick={() => void revisar(caso, 'APROBAR')}>
                {busyId === caso.id ? 'Guardando…' : 'Aprobar y publicar'}
              </button>
              <button type="button" className="danger" disabled={busyId !== null}
                onClick={() => void revisar(caso, 'RECHAZAR')}>
                Rechazar historia
              </button>
            </div>
          </article>)}
        </div>}
  </section>;
}
