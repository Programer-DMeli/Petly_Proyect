/** Etiqueta de estado para pantallas todavía no desarrolladas. */
export default function PendingBadge({ sprint }: { sprint: string }) {
  return <span className="badge badge--pending">Pendiente · {sprint}</span>
}
