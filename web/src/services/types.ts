export interface Profile {
  vivienda: string | null;
  horasFuera: number | null;
  presupuesto: number | null;
  tieneNinos: boolean | null;
  tieneOtrasMascotas: boolean | null;
  finalizado: boolean;
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
}

/** Estados de una solicitud de adopción (US-06). */
export type EstadoSolicitudAdopcion =
  | 'PENDIENTE'
  | 'EN_REVISION'
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
  postulante: number;
  mascota: number;
  albergue: number;
  fecha_solicitud: string;
  estado: EstadoSolicitudAdopcion;
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
}
export interface Page<T> {
  results: T[];
  next: string | null;
  previous: string | null;
  count: number;
}
