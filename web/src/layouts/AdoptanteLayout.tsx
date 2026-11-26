import MensajesFlotante from '../components/MensajesFlotante';
import AsistenteChat from '../components/AsistenteChat';
import { Outlet, useLocation, useOutletContext } from 'react-router-dom';
import AdoptanteHero from '../features/adoptante/inicio/AdoptanteHero';
import { FavoritesProvider } from '../components/FavoritesProvider';
export default function AdoptanteLayout() {
  const { pathname } = useLocation();
  const footerContent = useOutletContext<HTMLDivElement | null>();
  const overview = pathname === '/adoptante' || pathname === '/adoptante/catalogo';
  return (
    <FavoritesProvider>
      <div className="workspace workspace--adoptante">
        {overview && <AdoptanteHero />}
        <Outlet context={footerContent} />
        <MensajesFlotante espacio="adoptante" />
        <AsistenteChat />
      </div>
    </FavoritesProvider>
  );
}
