export interface Profile {
  vivienda: string | null;
  horasFuera: number | null;
  presupuesto: number | null;
  tieneNinos: boolean | null;
  tieneOtrasMascotas: boolean | null;
  finalizado: boolean;
  zonaHoraria?: string;
  disponibilidad?: Array<{ id: number; diaSemana: string; horaInicio: string; horaFin: string; descripcion: string }>;
  solicitudesPrevias?: Array<{
    id: number;
    estado: EstadoSolicitudAdopcion;
    fecha_solicitud: string;
    fecha_revision: string | null;
    mascotaNombre: string;
  }>;
}
export interface Pet {
  id: number;
  nombre: string;
  especie: string;
  tamano: string;
  edadMeses: number | null;
  temperamento: string;
  energia: string;
  descripcion: string;
  albergue: string;
  albergueId: number;
  ubicacion: string;
  estado: string;
  fotografiaUrl: string | null;
  compatibilidad?: {
    porcentaje: number | null;
    puntaje: number | null;
    cobertura: number;
    datosInsuficientes: boolean;
    mensaje: string;
    versionReglas: string;
    criterios: Array<{ id: string; nombre: string; peso: number; puntaje: number | null; aporte: number | null; explicacion: string }>;
  };
}
export interface SolicitudAdopcion {
  id: number;
  estado: EstadoSolicitudAdopcion;
  fecha_solicitud: string;
  mascota_nombre: string;
  postulante?: number;
  mascota?: number;
  albergue?: number;
}
export interface SeguimientoSolicitud {
  solicitudId: number;
  estadoActual: EstadoSolicitudAdopcion;
  eventos: EventoSeguimientoSolicitud[];
}
export interface ContratoAdopcion {
  public_id: string;
  solicitud_id: number;
  estado: string;
  estado_display: string;
  url_firma: string;
  documento_disponible: boolean;
  generado_en: string;
  firmado_en: string | null;
  error_integracion: string;
}

/** Estados de una solicitud de adopción (US-06). */
export type EstadoSolicitudAdopcion =
  | 'PENDIENTE'
  | 'OBSERVADA'
  | 'APROBADA'
  | 'RECHAZADA'
  | 'CANCELADA'
  | 'ENTREGADA';

/** Datos que el adoptante envía para solicitar una mascota. */
export interface CrearSolicitudAdopcion {
  mascotaId: number;
  mensajePostulante?: string;
  respuestasCuestionario?: Record<string, unknown>;
}

/** Respuesta Django de una solicitud persistida. */
export interface SolicitudAdopcion {
  id: number;
  postulante?: number;
  mascota?: number;
  albergue?: number;
  fecha_solicitud: string;
  estado: EstadoSolicitudAdopcion;
}

/** Evento persistido que registra un cambio en el estado de una solicitud (US-08). */
export interface EventoSeguimientoSolicitud {
  id: number;
  tipo: string;
  tipo_display: string;
  estado_anterior: EstadoSolicitudAdopcion | null;
  estado_nuevo: EstadoSolicitudAdopcion | null;
  descripcion: string;
  ocurrido_en: string;
  actor_nombre?: string;
}

/** Historial de estados devuelto por Spring para el adoptante titular. */
export interface SeguimientoSolicitud {
  solicitudId: number;
  estadoActual: EstadoSolicitudAdopcion;
  eventos: EventoSeguimientoSolicitud[];
}

export interface Shelter {
  id: number;
  nombre: string;
  direccion: string;
  descripcion: string;
  telefono: string;
  email: string;
  capacidad_maxima: number;
  responsable_nombre: string;
  responsable_email: string;
  responsable_telefono: string;
  infraestructura: Infrastructure[];
  propietario_spring_id: number | null;
  propietario_correo: string;
  activo: boolean;
  sitio_web: string;
  horario_atencion: string;
}
export interface Infrastructure {
  id: number;
  tipo: string;
  nombre: string;
  descripcion: string;
  capacidad: number | null;
  metros_cuadrados: string | null;
  equipamiento: string;
  activo: boolean;
}
export interface AdminPet {
  id: number;
  nombre: string;
  especie: string;
  sexo: string;
  tamanio: string;
  temperamento: string;
  energia: string;
  edad_anos: number | null;
  edad_meses: number | null;
  descripcion: string;
  estado: string;
  foto_principal: string | null;
  edad_texto?: string;
}
export interface Page<T> {
  results: T[];
  next: string | null;
  previous: string | null;
  count: number;
}
