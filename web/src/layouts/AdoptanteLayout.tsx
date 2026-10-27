import { Outlet, useLocation } from 'react-router-dom';
import AdoptionCarousel from '../components/AdoptionCarousel';
import { FavoritesProvider } from '../components/FavoritesProvider';
export default function AdoptanteLayout() {
  const { pathname } = useLocation();
  const overview = pathname === '/adoptante' || pathname === '/adoptante/catalogo';
  return (
    <FavoritesProvider>
      <div className="workspace workspace--adoptante">
        {overview && <AdoptionCarousel role="adoptante" />}
        <Outlet />
      </div>
    </FavoritesProvider>
  );
}
