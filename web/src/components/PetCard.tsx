import { Link } from 'react-router-dom';
import { FavoriteHeart } from './FavoritesProvider';
import { mediaUrl } from '../services/http';
import { formatAge } from '../services/petPresentation';
import type { Pet } from '../services/types';

export default function PetCard({ pet }: { pet: Pet }) {
  const compatibility = pet.compatibilidad;
  const needsProfile = compatibility?.mensaje.trim().startsWith('Completa tu perfil');
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
          {formatAge(pet.edadMeses)}
        </p>
        <p className="pet-card__shelter">
          <span aria-hidden="true">⌂</span> {pet.albergue}
        </p>
        <p className="pet-card__temperament">{pet.temperamento}</p>
        {compatibility && (
          <div
            className={`pet-card__compatibility${compatibility.porcentaje === null ? ' is-unknown' : compatibility.porcentaje >= 80 ? ' is-high' : compatibility.porcentaje >= 60 ? ' is-medium' : ' is-low'}`}
          >
            {compatibility.porcentaje === null ? (
              <>
                <strong>{needsProfile ? 'Completa tu perfil' : 'Aún no disponible'}</strong>
                <span>{compatibility.mensaje}</span>
                {needsProfile && (
                  <Link to="/adoptante/cuestionario">Completar perfil <span aria-hidden="true">↗</span></Link>
                )}
              </>
            ) : (
              `${compatibility.porcentaje}% compatible`
            )}
          </div>
        )}
        <Link
          className="button pet-card__link"
          to={`/adoptante/catalogo/${pet.id}`}
          aria-label={`Ver detalle de ${pet.nombre}`}
        >
          <span>Ver detalle</span>
          <span aria-hidden="true">↗</span>
        </Link>
        {pet.estado === 'Disponible' && (
          <Link
            className="button secondary pet-card__link"
            to={`/adoptante/catalogo/${pet.id}/solicitud`}
            aria-label={`Solicitar adopción de ${pet.nombre}`}
          >
            Solicitar adopción
          </Link>
        )}
      </div>
    </article>
  );
}
