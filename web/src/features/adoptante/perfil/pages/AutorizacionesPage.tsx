import { useCallback, useEffect, useRef, useState } from 'react';
import Feedback from '../../../../components/Feedback';
import { spring, json, errorMessage } from '../../../../services/http';
import { useDialogFocus } from '../../../../services/useDialogFocus';
import './autorizaciones.css';

interface Shelter {
  id: number;
  nombre: string;
  direccion: string;
}

interface Consent {
  albergueId: number;
  estado: string;
}

function searchable(text: string) {
  return text.normalize('NFD').replace(/\p{Diacritic}/gu, '').toLocaleLowerCase('es');
}

export default function AutorizacionesPage() {
  const [shelters, setShelters] = useState<Shelter[]>([]);
  const [access, setAccess] = useState<Set<number>>(() => new Set());
  const [view, setView] = useState<'active' | 'available'>('active');
  const [privacyHovered, setPrivacyHovered] = useState(false);
  const [privacyPinned, setPrivacyPinned] = useState(false);
  const [query, setQuery] = useState('');
  const [status, setStatus] = useState<'loading' | 'ready' | 'error'>('loading');
  const [loadError, setLoadError] = useState('');
  const [actionError, setActionError] = useState('');
  const [pending, setPending] = useState<number | null>(null);
  const [selected, setSelected] = useState<Shelter | null>(null);
  const [result, setResult] = useState<{ shelter: Shelter; allowed: boolean } | null>(null);
  const filters = useRef<HTMLDivElement>(null);
  const busy = pending !== null;
  const privacyOpen = privacyHovered || privacyPinned;

  const closePrivacy = useCallback(() => {
    setPrivacyHovered(false);
    setPrivacyPinned(false);
  }, []);

  useEffect(() => {
    if (!privacyOpen) return;
    const dismiss = (event: KeyboardEvent) => {
      if (event.key === 'Escape') closePrivacy();
    };
    document.addEventListener('keydown', dismiss);
    return () => document.removeEventListener('keydown', dismiss);
  }, [privacyOpen, closePrivacy]);

  function closeDialog() {
    if (busy) return;
    setSelected(null);
    setActionError('');
  }

  useDialogFocus(selected !== null, closeDialog);

  const load = useCallback(async () => {
    setStatus('loading');
    setLoadError('');
    try {
      const [available, consents] = await Promise.all([
        spring<Shelter[]>('/api/catalogo/albergues'),
        spring<Consent[]>('/api/autorizaciones'),
      ]);
      setShelters(available);
      setAccess(new Set(consents.filter(consent => consent.estado === 'ACTIVA').map(consent => consent.albergueId)));
      setStatus('ready');
    } catch (error) {
      setLoadError(errorMessage(error));
      setStatus('error');
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  function openDialog(shelter: Shelter) {
    setActionError('');
    setSelected(shelter);
  }

  async function change(shelter: Shelter, allow: boolean) {
    setPending(shelter.id);
    setActionError('');
    setResult(null);
    try {
      await spring(allow ? '/api/autorizaciones' : `/api/autorizaciones/${shelter.id}`,
        allow ? { method: 'POST', body: json({ albergueId: shelter.id }) } : { method: 'DELETE' });
      setAccess(previous => {
        const next = new Set(previous);
        if (allow) next.add(shelter.id);
        else next.delete(shelter.id);
        return next;
      });
      setSelected(null);
      setResult({ shelter, allowed: allow });
      filters.current?.focus();
    } catch (error) {
      setActionError(errorMessage(error));
    } finally {
      setPending(null);
    }
  }

  const activeCount = shelters.filter(shelter => access.has(shelter.id)).length;
  const search = searchable(query.trim());
  const visible = shelters.filter(shelter =>
    access.has(shelter.id) === (view === 'active')
    && searchable(`${shelter.nombre} ${shelter.direccion ?? ''}`).includes(search));

  return (
    <section className="profile-permissions" aria-labelledby="permissions-title">
      <header className="profile-permissions__intro">
        <div>
          <h1 id="permissions-title">Permisos de mi perfil</h1>
          <p>Decide qué albergues pueden consultar tu cuestionario.</p>
        </div>
        <div className="profile-permissions__privacy"
          onPointerEnter={event => { if (event.pointerType === 'mouse') setPrivacyHovered(true); }}
          onPointerLeave={() => setPrivacyHovered(false)}
          onBlur={closePrivacy}>
          <button type="button" className="profile-permissions__info-trigger"
            aria-expanded={privacyOpen} aria-controls="permissions-privacy-info"
            onClick={() => (privacyPinned ? closePrivacy() : setPrivacyPinned(true))}>
            <svg aria-hidden="true" viewBox="0 0 24 24" fill="none" stroke="currentColor"
              strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round">
              <path d="M12 3 5 6v5c0 4.5 2.7 7.7 7 10 4.3-2.3 7-5.5 7-10V6l-7-3Z" />
              <path d="m9 12 2 2 4-4" />
            </svg>
            Qué información compartes
          </button>
          <div className="profile-permissions__info-popover" id="permissions-privacy-info" hidden={!privacyOpen}>
            <div>
              <p>Vivienda, rutina, presupuesto y convivencia. Puedes quitar el acceso en cualquier momento.</p>
              <p>Compartir tu perfil no envía una solicitud de adopción.</p>
            </div>
          </div>
        </div>
      </header>

      <div className="profile-permissions__panel">
        <div className="profile-permissions__toolbar" ref={filters} tabIndex={-1}>
          <div className="profile-permissions__tabs" role="group" aria-label="Filtrar por permiso">
            <button type="button" aria-pressed={view === 'active'} onClick={() => setView('active')}>
              Con acceso{status === 'ready' && <span className="profile-permissions__count">{activeCount}</span>}
            </button>
            <button type="button" aria-pressed={view === 'available'} onClick={() => setView('available')}>Sin acceso</button>
          </div>
          <label className="profile-permissions__search">
            <span>Buscar albergue</span>
            <input type="search" value={query} placeholder="Nombre o ubicación"
              onChange={event => setQuery(event.target.value)} />
          </label>
        </div>

        {result && (
          <div className="profile-permissions__notice">
            <p role="status">
              {result.allowed
                ? `${result.shelter.nombre} ahora tiene acceso a tu cuestionario.`
                : `${result.shelter.nombre} ya no tiene acceso a tu cuestionario.`}
            </p>
            {!result.allowed && (
              <button type="button" className="secondary" disabled={busy} onClick={() => openDialog(result.shelter)}>
                Volver a dar acceso
              </button>
            )}
          </div>
        )}
        {!selected && <Feedback error={actionError} />}

        {status === 'loading' && <p role="status" className="profile-permissions__empty">Cargando albergues…</p>}
        {status === 'error' && (
          <div className="profile-permissions__empty">
            <Feedback error={loadError} />
            <button type="button" className="secondary" onClick={() => void load()}>Reintentar</button>
          </div>
        )}
        {status === 'ready' && visible.length === 0 && (
          <div className="profile-permissions__empty">
            <p>{search ? 'No encontramos albergues que coincidan con tu búsqueda.'
              : shelters.length === 0 ? 'No hay albergues disponibles.'
              : view === 'active' ? 'Ningún albergue tiene acceso a tu perfil.'
              : 'Todos los albergues disponibles tienen acceso a tu perfil.'}</p>
            {!search && view === 'active' && shelters.length > 0 && (
              <button type="button" className="secondary" onClick={() => setView('available')}>Ver albergues sin acceso</button>
            )}
          </div>
        )}
        {status === 'ready' && visible.length > 0 && (
          <ul className="profile-permissions__list" aria-label={view === 'active' ? 'Albergues con acceso' : 'Albergues sin acceso'}>
            {visible.map(shelter => {
              const allowed = access.has(shelter.id);
              const saving = pending === shelter.id;
              return (
                <li className="profile-permissions__row" key={shelter.id}>
                  <div className="profile-permissions__shelter">
                    <h2>{shelter.nombre}</h2>
                    {shelter.direccion && <p>{shelter.direccion}</p>}
                  </div>
                  <span className="profile-permissions__badge" data-active={allowed}>
                    {allowed ? 'Tiene acceso' : 'Sin acceso'}
                  </span>
                  <button type="button"
                    disabled={busy} aria-label={saving ? undefined : `${allowed ? 'Quitar acceso a' : 'Dar acceso a'} ${shelter.nombre}`}
                    onClick={() => (allowed ? void change(shelter, false) : openDialog(shelter))}>
                    {saving ? 'Guardando…' : allowed ? 'Quitar acceso' : 'Dar acceso'}
                  </button>
                </li>
              );
            })}
          </ul>
        )}
      </div>

      {selected && (
        <div className="consent-backdrop">
          <div role="dialog" aria-modal="true" aria-labelledby="permissions-confirm-title"
            aria-describedby="permissions-confirm-detail" className="confirmation profile-permissions__dialog">
            <h2 id="permissions-confirm-title">¿Compartir tu cuestionario con {selected.nombre}?</h2>
            <p id="permissions-confirm-detail">
              Podrá consultar tu vivienda, rutina, presupuesto y convivencia hasta que quites el acceso.
              Compartir tu perfil no envía una solicitud de adopción.
            </p>
            <Feedback error={actionError} />
            <div className="actions">
              <button type="button" className="secondary" disabled={busy} onClick={closeDialog}>Cancelar</button>
              <button type="button" disabled={busy} onClick={() => void change(selected, true)}>
                {busy ? 'Guardando…' : 'Dar acceso'}
              </button>
            </div>
          </div>
        </div>
      )}
    </section>
  );
}
