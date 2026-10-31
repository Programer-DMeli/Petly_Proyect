import { Outlet, useLocation } from 'react-router-dom';
import AdoptionCarousel from '../components/AdoptionCarousel';
export default function AlbergueLayout() {
  const { pathname } = useLocation();
  return (
    <div className="workspace workspace--albergue">
      {pathname === '/albergue' && <AdoptionCarousel role="albergue" />}
      <Outlet />
    </div>
  );
}
