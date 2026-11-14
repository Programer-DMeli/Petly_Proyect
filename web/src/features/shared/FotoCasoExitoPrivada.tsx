import { useEffect, useState } from 'react';
import { errorMessage, fetchDjangoFile } from '../../services/http';

export type FotoCasoExito = {
  id: number;
  mime_type: string;
  tamaño_bytes: number;
  descarga_url: string;
};

export default function FotoCasoExitoPrivada({ foto, alt }: { foto: FotoCasoExito; alt: string }) {
  const [src, setSrc] = useState('');
  const [error, setError] = useState('');

  useEffect(() => {
    let cancelado = false;
    let urlTemporal = '';
    setSrc('');
    setError('');
    void fetchDjangoFile(foto.descarga_url)
      .then(response => response.blob())
      .then(blob => {
        urlTemporal = URL.createObjectURL(blob);
        if (cancelado) URL.revokeObjectURL(urlTemporal);
        else setSrc(urlTemporal);
      })
      .catch(cause => { if (!cancelado) setError(errorMessage(cause)); });
    return () => {
      cancelado = true;
      if (urlTemporal) URL.revokeObjectURL(urlTemporal);
    };
  }, [foto.descarga_url]);

  return <figure>
    {src ? <img src={src} alt={alt} loading="lazy" />
      : error ? <p role="alert">{error}</p> : <p role="status">Cargando foto…</p>}
  </figure>;
}
