import { useEffect, useState } from 'react';
import { fetchDjangoFile } from '../services/http';

export default function PrivateDjangoImage({ src, alt }: { src: string; alt: string }) {
  const [objectUrl, setObjectUrl] = useState('');
  const [unavailable, setUnavailable] = useState(false);

  useEffect(() => {
    let active = true;
    let createdUrl = '';
    const controller = new AbortController();
    setObjectUrl('');
    setUnavailable(false);
    void fetchDjangoFile(src, { signal: controller.signal }).then(response => response.blob())
      .then(blob => {
        createdUrl = URL.createObjectURL(blob);
        if (active) setObjectUrl(createdUrl);
        else URL.revokeObjectURL(createdUrl);
      })
      .catch(() => { if (active) setUnavailable(true); });
    return () => {
      active = false;
      controller.abort();
      if (createdUrl) URL.revokeObjectURL(createdUrl);
    };
  }, [src]);

  if (objectUrl) return <img src={objectUrl} alt={alt} loading="lazy" style={{ maxWidth: '100%', height: 'auto', borderRadius: 12 }} />;
  return <span role="status">{unavailable ? 'Foto privada no disponible.' : 'Cargando foto…'}</span>;
}
