import { useCallback, useEffect, useRef, useState, type ReactNode } from 'react';
import { spring, errorMessage } from '../services/http';
import { FavoritesContext, useFavorites } from '../services/favorites';
import type { Pet } from '../services/types';
export function FavoritesProvider({ children }: { children: ReactNode }) {
  const [items, setItems] = useState<Pet[]>([]);
  const [loading, setLoading] = useState(true);
  const [pending, setPending] = useState(new Set<number>());
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const locks = useRef(new Set<number>());
  const revision = useRef(0);
  const refresh = useCallback(async (signal?: AbortSignal) => {
    if (locks.current.size) return;
    const current = revision.current;
    setLoading(true);
    try {
      const rows = await spring<Pet[]>('/api/perfil/favoritos', { signal });
      if (!signal?.aborted && current === revision.current) {
        setItems(rows);
        setError('');
      }
    } catch (reason) {
      if (!signal?.aborted) setError(errorMessage(reason));
    } finally {
      if (!signal?.aborted) setLoading(false);
    }
  }, []);
  useEffect(() => {
    const controller = new AbortController();
    void refresh(controller.signal);
    return () => controller.abort();
  }, [refresh]);
  useEffect(() => {
    const focus = () => {
      void refresh();
    };
    window.addEventListener('focus', focus);
    return () => window.removeEventListener('focus', focus);
  }, [refresh]);
  const toggle = useCallback(
    async (pet: Pet) => {
      if (locks.current.has(pet.id) || loading) return;
      const saved = items.some(item => item.id === pet.id);
      locks.current.add(pet.id);
      revision.current++;
      setPending(new Set(locks.current));
      setError('');
      try {
        await spring(`/api/perfil/favoritos/${pet.id}`, { method: saved ? 'DELETE' : 'PUT' });
        setItems(current =>
          saved
            ? current.filter(item => item.id !== pet.id)
            : [...current.filter(item => item.id !== pet.id), pet]
        );
        setNotice(
          saved ? `${pet.nombre} se quitó de favoritos.` : `${pet.nombre} se guardó en favoritos.`
        );
      } catch (reason) {
        setError(errorMessage(reason));
      } finally {
        locks.current.delete(pet.id);
        revision.current++;
        setPending(new Set(locks.current));
      }
    },
    [items, loading]
  );
  return (
    <FavoritesContext.Provider value={{ items, loading, pending, refresh, toggle }}>
      {error && (
        <div className="favorite-feedback" role="alert">
          <span>Favoritos: {error}</span>
          <button type="button" onClick={() => void refresh()}>
            Reintentar favoritos
          </button>
        </div>
      )}
      <span className="visually-hidden" role="status" aria-live="polite">
        {notice}
      </span>
      {children}
    </FavoritesContext.Provider>
  );
}
export function FavoriteHeart({ pet }: { pet: Pet }) {
  const { items, loading, pending, toggle } = useFavorites();
  const saved = items.some(item => item.id === pet.id);
  const label = `${saved ? 'Quitar' : 'Guardar'} ${pet.nombre} ${saved ? 'de' : 'en'} favoritos`;
  return (
    <button
      type="button"
      className={`favorite-heart${saved ? ' is-saved' : ''}`}
      aria-label={label}
      title={label}
      aria-pressed={saved}
      disabled={loading || pending.has(pet.id)}
      aria-busy={pending.has(pet.id)}
      onClick={() => void toggle(pet)}
    >
      <svg aria-hidden="true" viewBox="0 0 24 24">
        <path d="M20.8 4.6a5.5 5.5 0 0 0-7.8 0L12 5.7l-1.1-1.1a5.5 5.5 0 0 0-7.8 7.8L12 21l8.8-8.6a5.5 5.5 0 0 0 0-7.8Z" />
      </svg>
    </button>
  );
}
