import type { ReactNode } from 'react';
import PrivateDjangoImage from '../../components/PrivateDjangoImage';
import './seguimiento.css';

import { fecha, modalidad, type Seguimiento } from './cronogramaDatos';

const entregas = {
  PENDIENTE: 'Entrega pendiente',
  VENCIDA: 'Entrega vencida',
  ENVIADA: 'Reporte enviado',
  FUERA_DE_PLAZO: 'Recibido fuera de plazo',
  CANCELADA: 'Entrega cancelada',
  SIN_ENTREGA: 'Cerrado sin entrega',
};
const actividades: Record<string, string> = { PROGRAMADO: 'Programada', REALIZADO: 'Realizada', CANCELADO: 'Cancelada' };

export function Hito({ row, children, adoptante = false }: { row: Seguimiento; children?: ReactNode; adoptante?: boolean }) {
  const vencido = row.estado_entrega === 'VENCIDA' || row.estado_entrega === 'FUERA_DE_PLAZO';
  return <li id={`seguimiento-${row.id}`} className={`seguimiento-hito${vencido ? ' seguimiento-hito--vencido' : ''}`}>
    <div className="seguimiento-hito__encabezado">
      <strong>{modalidad[row.modalidad]}</strong>
      <span className="seguimiento-estado">
        {row.estado_entrega === 'FUERA_DE_PLAZO' && adoptante ? 'Enviado fuera de plazo'
          : row.estado_entrega ? entregas[row.estado_entrega] : actividades[row.estado]}
      </span>
    </div>
    <p>{row.modalidad === 'REPORTE' ? 'Fecha límite' : 'Fecha programada'} · {fecha(row.programado_para)} (Lima)</p>
    {row.cancelado_en && <p>Cancelado el {fecha(row.cancelado_en)} (Lima)</p>}
    {row.notas && <p className="seguimiento-texto">
      {adoptante && row.estado_entrega && <strong>Qué incluir en tu reporte: </strong>}{row.notas}
    </p>}
    {row.alerta_incumplimiento?.emitida_en && <FeedbackIncumplimiento fechaEmision={row.alerta_incumplimiento.emitida_en} />}
    {children}
  </li>;
}

function FeedbackIncumplimiento({ fechaEmision }: { fechaEmision: string }) {
  return <p className="feedback error">Alerta roja de incumplimiento emitida el {fecha(fechaEmision)}</p>;
}

export function Reporte({ row, adoptante = false }: { row: Seguimiento; adoptante?: boolean }) {
  const reporte = row.reporte_bienestar;
  if (!reporte) return null;
  return <article>
    <h3>Reporte de bienestar</h3>
    <p><strong>{reporte.estado_bienestar_display}</strong> · Enviado el {fecha(reporte.enviado_en)}</p>
    {row.estado_entrega === 'FUERA_DE_PLAZO' && <p className="feedback error">{adoptante ? 'Enviado fuera de plazo' : entregas.FUERA_DE_PLAZO}</p>}
    {reporte.observaciones && <p className="seguimiento-texto">{reporte.observaciones}</p>}
    <div className="seguimiento-fotos">
      {reporte.fotos.map((foto, index) => <PrivateDjangoImage key={foto.id}
        src={foto.descarga_url} alt={`Foto ${index + 1} del reporte de ${row.mascota_nombre}`} />)}
    </div>
  </article>;
}
