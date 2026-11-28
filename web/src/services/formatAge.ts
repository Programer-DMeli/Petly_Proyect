export function formatAge(months: number | null) {
  if (months === null) return 'Edad por evaluar';
  const years = Math.floor(months / 12);
  const remainingMonths = months % 12;
  if (years === 0) return `${months} ${months === 1 ? 'mes' : 'meses'}`;
  const yearLabel = `${years} ${years === 1 ? 'año' : 'años'}`;
  if (remainingMonths === 0) return yearLabel;
  return `${yearLabel} y ${remainingMonths} ${remainingMonths === 1 ? 'mes' : 'meses'}`;
}
