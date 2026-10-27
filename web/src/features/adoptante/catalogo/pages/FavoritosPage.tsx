import { useFavorites } from '../../../../services/favorites';
import PetCard from '../../../../components/PetCard';
import { Link } from 'react-router-dom';
export default function FavoritosPage() {
  const { items, loading, refresh } = useFavorites();
  return (
    <section className="favorites-page">
      <header className="workspace-page-heading">
        <div>
          <span className="workspace-eyebrow">MI ADOPCIÓN</span>
          <h1>Mis favoritos</h1>
          <p>Pequeñas miradas que ya tienen un lugar en tu corazón.</p>
        </div>
        <button className="secondary" disabled={loading} onClick={() => void refresh()}>
          Actualizar favoritos
        </button>
      </header>
      {loading ? (
        <p role="status">Cargando favoritos…</p>
      ) : items.length ? (
        <div className="pet-card-grid">
          {items.map(pet => (
            <PetCard key={pet.id} pet={pet} />
          ))}
        </div>
      ) : (
        <div className="workspace-empty">
          <span aria-hidden="true">♡</span>
          <h2>Tu próximo compañero está por descubrir</h2>
          <p>Aún no tienes favoritos. Guarda las mascotas que quieras volver a visitar.</p>
          <Link className="button" to="/adoptante/catalogo">
            Explorar mascotas
          </Link>
        </div>
      )}
    </section>
  );
}
