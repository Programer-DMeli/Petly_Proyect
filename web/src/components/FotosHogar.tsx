import { useState } from 'react';
import { django, downloadDjango, errorMessage } from '../services/http';
export default function FotosHogar({ solicitudId }: { solicitudId: number }) {
  const [rows, setRows] = useState<Array<{ id: number; url: string; tipo: string }> | null>(null),
    [error, setError] = useState('');
  return (
    <section>
      <button
        onClick={() => {
          setError('');
          void django<Array<{ id: number; url: string; tipo: string }>>(
            `/api/acceso/solicitudes/${solicitudId}/fotos-hogar/`
          )
            .then(setRows)
            .catch(e => {
              setRows(null);
              setError(errorMessage(e));
            });
        }}
      >
        Consultar fotos privadas del hogar
      </button>
      {error && <p role="alert">{error}</p>}
      {rows?.length === 0 && <p>Sin fotos del hogar.</p>}
      {rows?.map((f, i) => (
        <button
          key={f.id}
          onClick={() =>
            void downloadDjango(f.url, `hogar-${i + 1}.${f.tipo === 'PNG' ? 'png' : 'jpg'}`).catch(
              e => setError(errorMessage(e))
            )
          }
        >
          Descargar foto {i + 1}
        </button>
      ))}
    </section>
  );
}
