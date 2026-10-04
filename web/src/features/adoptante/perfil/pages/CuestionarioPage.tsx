import PendingBadge from '../../../../components/PendingBadge'

/**
 * Cuestionario de estilo de vida (US-16) y datos de convivencia (US-17).
 * Portal del adoptante en React: Sprint 2 (PLANNING v2 §9.2).
 */
export default function CuestionarioPage() {
  return (
    <section className="card">
      <h1>
        Cuestionario de estilo de vida <PendingBadge sprint="Sprint 1 (Android) · Sprint 2 (web)" />
      </h1>
      <p className="muted">
        Vivienda, horas fuera de casa, presupuesto y convivencia con niños u otras mascotas.
        Guardado y recuperación de respuestas desde la cuenta del adoptante. Pendiente de
        implementación.
      </p>
    </section>
  )
}
