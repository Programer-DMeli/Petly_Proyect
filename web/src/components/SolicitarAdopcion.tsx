import { Link } from 'react-router-dom';
import type { Pet } from '../services/types';

export default function SolicitarAdopcion({ mascota, className = 'button' }: {
  mascota: Pick<Pet, 'id' | 'nombre' | 'estado'>;
  className?: string;
}) {
  const label = `Solicitar adopción de ${mascota.nombre}`;

  if (mascota.estado.trim().toLowerCase() !== 'disponible') {
    return (
      <button className={className} type="button" disabled aria-label={`${label} (${mascota.estado})`}>
        Solicitar adopción
      </button>
    );
  }

  return (
    <Link className={className} to={`/adoptante/catalogo/${mascota.id}/solicitud`} aria-label={label}>
      Solicitar adopción
    </Link>
  );
}
