import MensajesFlotante from '../components/MensajesFlotante';
import AsistenteChat from '../components/AsistenteChat';
import { Outlet, useLocation, useOutletContext } from 'react-router-dom';
import AdoptionCarousel from '../components/AdoptionCarousel';
import { FavoritesProvider } from '../components/FavoritesProvider';
export default function AdoptanteLayout() {
  const { pathname } = useLocation();
  const footerContent = useOutletContext<HTMLDivElement | null>();
  const overview = pathname === '/adoptante' || pathname === '/adoptante/catalogo';
  return (
    <FavoritesProvider>
      <div className="workspace workspace--adoptante">
        {overview && <AdoptionCarousel role="adoptante" />}
        <Outlet context={footerContent} />
        <MensajesFlotante espacio="adoptante" />
        <AsistenteChat />
      </div>
    </FavoritesProvider>
  );
}
