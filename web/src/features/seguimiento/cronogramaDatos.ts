export type Bienestar = 'EXCELENTE' | 'BUENO' | 'REGULAR' | 'PREOCUPANTE';
export type Evaluacion = {
  calificacion_adaptacion: number;
  observaciones_cierre: string;
  cerrado_en: string | null;
};
export type Seguimiento = {
  id: number;
  solicitud_id: number;
  mascota_nombre: string;
  albergue_nombre: string;
  programado_para: string;
  cancelado_en?: string | null;
  modalidad: 'VISITA' | 'LLAMADA' | 'REPORTE';
  estado: string;
  notas: string;
  estado_entrega: 'PENDIENTE' | 'VENCIDA' | 'ENVIADA' | 'FUERA_DE_PLAZO' | 'CANCELADA' | 'SIN_ENTREGA' | null;
  expediente_cierre: Evaluacion | null;
  alerta_incumplimiento: { emitida_en: string | null } | null;
  reporte_bienestar: {
    id: number;
    estado_bienestar_display: string;
    observaciones: string;
    enviado_en: string;
    fotos: { id: number; descarga_url: string }[];
  } | null;
  asesorias_veterinarias: {
    id: number;
    problema: string;
    estado: 'PENDIENTE' | 'EN_ATENCION' | 'ATENDIDA';
    creado_en: string;
  }[];
};

export const modalidad = { VISITA: 'Visita', LLAMADA: 'Llamada', REPORTE: 'Entrega de reporte' };

export function fecha(value: string) {
  // Spring devuelve los recordatorios en hora local de Lima, sin desplazamiento.
  const instant = /(?:Z|[+-]\d{2}:\d{2})$/.test(value) ? value : `${value}-05:00`;
  return new Date(instant).toLocaleString('es-PE', {
    dateStyle: 'medium', timeStyle: 'short', timeZone: 'America/Lima',
  });
}
