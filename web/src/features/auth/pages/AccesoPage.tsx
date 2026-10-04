import PendingBadge from '../../../components/PendingBadge'

/**
 * Pantalla de acceso (login). El alcance aprobado (US-21) está en
 * PLANNING.md v2 §9.1: registro con correo y contraseña, bloqueo de
 * 5 minutos tras 2 intentos fallidos y limitación de peticiones.
 * Esta pantalla está preparada, pero la historia aún no se ha implementado.
 */
export default function AccesoPage() {
  return (
    <section className="card">
      <h1>
        Acceso <PendingBadge sprint="Sprint 1 · US-21" />
      </h1>
      <p className="muted">
        Formulario pendiente de implementación: inicio de sesión con correo y contraseña,
        bloqueo temporal tras intentos fallidos y validación de permisos por rol en el backend.
      </p>
      <p>
        <a href="/recuperacion">¿Olvidaste tu contraseña?</a>
      </p>
    </section>
  )
}
