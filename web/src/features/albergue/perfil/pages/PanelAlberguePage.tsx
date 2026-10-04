import PendingBadge from '../../../../components/PendingBadge'

/**
 * Panel del albergue (Sprint 1, Persona A): perfil institucional (US-46),
 * registro y estados de mascotas (US-11, US-13) y consulta autorizada de
 * postulantes (US-18). Pendiente de implementación.
 */
export default function PanelAlberguePage() {
  return (
    <section className="card">
      <h1>
        Panel del albergue <PendingBadge sprint="Sprint 1 · US-11, US-46, US-13, US-18" />
      </h1>
      <p className="muted">
        Funciones previstas: información institucional del albergue, alta y edición de
        mascotas con una fotografía (JPEG/PNG, máximo 5 MB), cambio de estado entre
        Disponible, En Proceso y Adoptado, y consulta de perfiles de postulantes solo con
        autorización expresa y vigente del adoptante.
      </p>
      <p className="muted">
        Cada albergue modifica únicamente sus propios recursos; los permisos se validan en
        el backend, no solo en esta pantalla.
      </p>
    </section>
  )
}
