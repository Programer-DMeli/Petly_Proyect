import PendingBadge from '../../../components/PendingBadge'

/**
 * Recuperación de contraseña (US-24): solo por correo, enlace con token
 * de un solo uso de 15 minutos. SMS queda pendiente para una versión
 * posterior. Pendiente de implementación.
 */
export default function RecuperacionPage() {
  return (
    <section className="card">
      <h1>
        Recuperar contraseña <PendingBadge sprint="Sprint 1 · US-24" />
      </h1>
      <p className="muted">
        Formulario pendiente de implementación: envío de enlace por correo con token de un
        solo uso que vence a los 15 minutos. La respuesta pública no revelará si el correo
        está registrado.
      </p>
    </section>
  )
}
