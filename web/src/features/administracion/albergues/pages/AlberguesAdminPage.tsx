import PendingBadge from '../../../../components/PendingBadge'

/**
 * Administración general mínima: gestión de albergues (US-46).
 * Área del administrador de Petly, distinta del personal del albergue
 * (PLANNING v2 §5). Pendiente de implementación.
 */
export default function AlberguesAdminPage() {
  return (
    <section className="card">
      <h1>
        Albergues <PendingBadge sprint="Sprint 1 · US-46" />
      </h1>
      <p className="muted">
        Alta y gestión de albergues por parte del administrador de Petly. Las cuentas de
        albergue y administrador las crea el equipo mediante un procedimiento documentado;
        no hay registro público ni contraseñas en Git.
      </p>
    </section>
  )
}
