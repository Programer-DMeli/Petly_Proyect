import { Link } from 'react-router-dom';
import AdoptLink from './AdoptLink';

export default function PublicHeader() {
  return (
    <header className="public-header">
      <div className="public-header__inner">
        <Link className="public-header__brand" to="/" aria-label="Petly, inicio">
          <svg aria-hidden="true" viewBox="0 0 36 34" width="41" height="39" fill="currentColor">
            <ellipse cx="6" cy="12" rx="3.8" ry="5.4" transform="rotate(-28 6 12)" />
            <ellipse cx="14" cy="6.7" rx="3.9" ry="5.6" transform="rotate(-13 14 6.7)" />
            <ellipse cx="23" cy="6.7" rx="3.9" ry="5.6" transform="rotate(13 23 6.7)" />
            <ellipse cx="30" cy="13" rx="3.8" ry="5.4" transform="rotate(28 30 13)" />
            <path d="M6.5 25.5C9 20.9 12.9 15 18.1 15s9.1 5.9 11.6 10.5c2.9 6-2.3 8.2-7.4 6.4-3-1.1-5.4-1.1-8.4 0-5.1 1.8-10.3-.4-7.4-6.4Z" />
          </svg>
          <span>Petly</span>
        </Link>
        <nav className="public-header__nav" aria-label="Navegación principal">
          <AdoptLink>Adopta</AdoptLink>
        </nav>
        <AdoptLink className="public-header__cta">
          <svg aria-hidden="true" viewBox="0 0 24 24" width="20" height="20" fill="currentColor">
            <path d="M12 21 3.8 13a5.3 5.3 0 0 1 7.5-7.5l.7.7.7-.7a5.3 5.3 0 1 1 7.5 7.5Z" />
          </svg>
          Encuentra a tu compañero
        </AdoptLink>
      </div>
    </header>
  );
}
