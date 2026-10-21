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
  estado: string;
  fecha_solicitud: string;
  mascota_nombre: string;
}
export interface SeguimientoSolicitud {
  solicitudId: number;
  estadoActual: string;
  eventos: Array<{ estadoAnterior: string | null; estadoNuevo: string; descripcion: string; ocurridoEn: string }>;
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
