import { createContext, useContext } from 'react';
import type { Pet } from './types';
export type FavoritesState = {
  items: Pet[];
  loading: boolean;
  pending: Set<number>;
  refresh: () => Promise<void>;
  toggle: (pet: Pet) => Promise<void>;
};
export const FavoritesContext = createContext<FavoritesState | null>(null);

export function useFavorites() {
  const value = useContext(FavoritesContext);
  if (!value) throw new Error('Favoritos requiere el área del adoptante.');
  return value;
}
