import { Link } from 'react-router-dom';
import { FavoriteHeart } from './FavoritesProvider';
import { mediaUrl } from '../services/http';
import type { Pet } from '../services/types';
export default function PetCard({ pet }: { pet: Pet }) {
  return (
    <article className="pet-card pet">
      <div className="pet-card__media">
        {pet.fotografiaUrl ? (
          <img src={mediaUrl(pet.fotografiaUrl)} alt={pet.nombre} loading="lazy" />
        ) : (
          <div className="pet-card__placeholder">
            <span aria-hidden="true">♡</span>
            <span>Fotografía por añadir</span>
          </div>
        )}
        <FavoriteHeart pet={pet} />
        <span className={`pet-card__state${pet.estado === 'Disponible' ? ' is-available' : ''}`}>
          {pet.estado}
        </span>
      </div>
      <div className="pet-card__body">
        <div className="pet-card__name">
          <h2>{pet.nombre}</h2>
          <span>{pet.especie}</span>
        </div>
        <p className="pet-card__facts">
          {pet.tamano} <span aria-hidden="true">·</span>{' '}
          {pet.edadMeses === null ? 'Edad por evaluar' : `${pet.edadMeses} meses`}
        </p>
        <p className="pet-card__shelter">
          <span aria-hidden="true">⌂</span> {pet.albergue}
        </p>
        <p className="pet-card__temperament">{pet.temperamento}</p>
        {pet.compatibilidad && (
          <p
            className={`pet-card__compatibility${pet.compatibilidad.porcentaje === null ? ' is-unknown' : ''}`}
            aria-label="Compatibilidad"
          >
            {pet.compatibilidad.porcentaje === null
              ? 'Información insuficiente'
              : `${pet.compatibilidad.porcentaje}% compatible`}
          </p>
        )}
        <Link
          className="button pet-card__link"
          to={`/adoptante/catalogo/${pet.id}`}
          aria-label={`Ver detalle de ${pet.nombre}`}
        >
          <span>Ver detalle</span>
          <span aria-hidden="true">↗</span>
        </Link>
      </div>
    </article>
  );
}
