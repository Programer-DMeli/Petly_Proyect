import { spring } from './http';
import { json } from './http';

export type EstadoOperacionPrivacidad =
  | 'PENDIENTE_DJANGO'
  | 'PENDIENTE_ARCHIVOS'
  | 'COMPLETADA'
  | 'ERROR';

export interface OperacionPrivacidad {
  operacion_id: string;
  tipo: 'EXPORTACION' | 'BAJA';
  estado: EstadoOperacionPrivacidad;
  creado_en?: string;
  actualizado_en?: string;
  completado_en?: string | null;
  mensaje?: string;
  retenciones: string[];
}

export interface ExportacionDatos {
  formato: string;
  operacion_id: string;
  generado_en: string;
  spring_boot: Record<string, unknown>;
  django: Record<string, unknown>;
}

export function crearIdOperacion() {
  return crypto.randomUUID();
}

export function exportarDatos(operacionId: string) {
  return spring<ExportacionDatos>('/api/privacidad/exportacion', {
    method: 'POST',
    body: json({ operacionId }),
  });
}

export function solicitarBaja(operacionId: string) {
  return spring<OperacionPrivacidad>('/api/privacidad/baja', {
    method: 'POST',
    body: json({ operacionId, confirmar: true }),
  });
}

export function consultarOperacion(operacionId: string) {
  return spring<OperacionPrivacidad>(`/api/privacidad/operaciones/${encodeURIComponent(operacionId)}`);
}

export function descargarJson(datos: ExportacionDatos) {
  const blob = new Blob([JSON.stringify(datos, null, 2)], { type: 'application/json;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = `petly-datos-${new Date().toISOString().slice(0, 10)}.json`;
  document.body.append(anchor);
  anchor.click();
  anchor.remove();
  window.setTimeout(() => URL.revokeObjectURL(url), 1_000);
}
