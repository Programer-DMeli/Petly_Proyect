import { useEffect, useState } from 'react';
import { obtenerConfiguracionGlobal } from './configuracionGlobal';

const DEFAULT_MAX_IMAGEN_MB = 5;

export function useMaxImagenMb() {
  const [maxImagenMb, setMaxImagenMb] = useState(DEFAULT_MAX_IMAGEN_MB);

  useEffect(() => {
    let active = true;
    void obtenerConfiguracionGlobal()
      .then(configuracion => { if (active) setMaxImagenMb(configuracion.maxImagenMb); })
      .catch(() => { /* El servidor aplica el límite real aunque la consulta no esté disponible. */ });
    return () => { active = false; };
  }, []);

  return maxImagenMb;
}
