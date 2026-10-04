/** Tipos compartidos del dominio de Petly (contratos previstos). */

/** Estados de una mascota en el catálogo (US-13). */
export type EstadoMascota = 'Disponible' | 'En Proceso' | 'Adoptado'

/** Roles de cuenta reconocidos. El administrador no es personal del albergue (PLANNING v2 §5). */
export type Rol = 'adoptante' | 'albergue' | 'administrador'

/** Temperamento registrado por el albergue; «Por evaluar» cuando se desconoce (US-12). */
export const TEMPERAMENTOS = [
  'Por evaluar',
  'Tranquilo',
  'Sociable',
  'Energico',
  'Cauteloso',
] as const

export type Temperamento = (typeof TEMPERAMENTOS)[number]

/** Respuesta de los endpoints de salud de ambos backends. */
export interface HealthResponse {
  status: string
}
