import { roleHome, type Role } from '../../services/session';

type AccessState = {
  mascotaId?: unknown;
  solicitud?: unknown;
  destinoSeguimiento?: unknown;
  destinoAfiche?: unknown;
};

export function accessDestination(role: Role, state: unknown) {
  const { mascotaId, solicitud, destinoSeguimiento, destinoAfiche } =
    typeof state === 'object' && state !== null ? (state as AccessState) : {};
  if (
    role === 'ALBERGUE' &&
    typeof destinoAfiche === 'string' &&
    /^\/albergue\?/.test(destinoAfiche)
  ) {
    return destinoAfiche;
  }

  const seguimiento =
    role === 'ADOPTANTE'
      ? '/adoptante/seguimientos-postadopcion'
      : role === 'ALBERGUE'
        ? '/albergue/seguimientos'
        : '';
  if (
    seguimiento &&
    typeof destinoSeguimiento === 'string' &&
    destinoSeguimiento.split(/[?#]/, 1)[0] === seguimiento
  ) {
    return destinoSeguimiento;
  }

  if (role === 'ADOPTANTE' && typeof solicitud === 'string' && /^\d+$/.test(solicitud)) {
    return `/adoptante/solicitudes/${solicitud}`;
  }
  if (role === 'ADOPTANTE' && typeof mascotaId === 'string' && /^[1-9]\d*$/.test(mascotaId)) {
    return `/adoptante/catalogo/${mascotaId}`;
  }
  return roleHome(role);
}
