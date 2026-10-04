import PendingBadge from '../../../../components/PendingBadge'

/**
 * Catálogo de mascotas con filtros (US-12). El portal del adoptante en
 * React se desarrolla en el Sprint 2 (PLANNING v2 §9.2); la experiencia
 * completa del adoptante de este sprint es Android.
 */
export default function CatalogoPage() {
  return (
    <section className="card">
      <h1>
        Catálogo de mascotas <PendingBadge sprint="Sprint 2 · US-12" />
      </h1>
      <p className="muted">
        Búsqueda y filtros por especie, tamaño, edad y temperamento (incluido «Por evaluar»),
        con paginación que conserva los filtros. Pendiente de implementación.
      </p>
    </section>
  )
}
