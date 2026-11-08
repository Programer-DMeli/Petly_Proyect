import { useEffect, useState } from 'react';
import { getSession } from '../services/session';
import { djangoBase } from '../services/http';

export default function PrivateDjangoImage({ src, alt }: { src: string; alt: string }) {
  const [objectUrl, setObjectUrl] = useState('');
  const [unavailable, setUnavailable] = useState(false);

  useEffect(() => {
    let active = true;
    let createdUrl = '';
    const session = getSession();
    setObjectUrl('');
    setUnavailable(false);
    if (!session || Date.now() >= session.expires) {
      setUnavailable(true);
      return () => { active = false; };
    }
    void fetch(`${djangoBase}${src}`, { headers: { Authorization: `Bearer ${session.token}` } })
      .then(response => {
        if (!response.ok) throw new Error('Foto no disponible');
        return response.blob();
      })
      .then(blob => {
        createdUrl = URL.createObjectURL(blob);
        if (active) setObjectUrl(createdUrl);
        else URL.revokeObjectURL(createdUrl);
      })
      .catch(() => { if (active) setUnavailable(true); });
    return () => {
      active = false;
      if (createdUrl) URL.revokeObjectURL(createdUrl);
    };
  }, [src]);

  if (objectUrl) return <img src={objectUrl} alt={alt} loading="lazy" style={{ maxWidth: '100%', height: 'auto', borderRadius: 12 }} />;
  return <span role="status">{unavailable ? 'Foto privada no disponible.' : 'Cargando foto…'}</span>;
}
