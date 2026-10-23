import { useEffect, useRef, useState } from 'react';
import { errorMessage, mediaUrl } from '../../../../services/http';
import type { AdminPet, Shelter } from '../../../../services/types';

const WIDTH = 1080;
const HEIGHT = 1350;

function coverImage(context: CanvasRenderingContext2D, image: ImageBitmap, x: number, y: number, width: number, height: number) {
  const scale = Math.max(width / image.width, height / image.height);
  const sourceWidth = width / scale;
  const sourceHeight = height / scale;
  const sourceX = (image.width - sourceWidth) / 2;
  const sourceY = (image.height - sourceHeight) / 2;
  context.drawImage(image, sourceX, sourceY, sourceWidth, sourceHeight, x, y, width, height);
}

export default function PosterMascota({ pet, shelter, onClose }: {
  pet: AdminPet;
  shelter: Shelter;
  onClose: () => void;
}) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);
  const shelterName = shelter.nombre.trim();
  const shelterPhone = shelter.telefono.trim();
  const shelterEmail = shelter.email.trim();
  const contactReady = Boolean(shelterPhone || shelterEmail);
  const canPublish = Boolean(shelterName && contactReady && pet.foto_principal);

  useEffect(() => {
    let cancelled = false;
    async function renderPoster() {
      setLoading(true); setError('');
      const canvas = canvasRef.current;
      if (!canvas || !pet.foto_principal || !shelterName || !contactReady) {
        setLoading(false);
        setError('Se necesita una foto y un nombre y contacto público del albergue.');
        return;
      }
      try {
        const contact = [shelterPhone, shelterEmail].filter(Boolean);
        const response = await fetch(mediaUrl(pet.foto_principal)!);
        if (!response.ok) throw new Error('No se pudo cargar la foto de la mascota.');
        const bitmap = await createImageBitmap(await response.blob());
        if (cancelled) { bitmap.close(); return; }
        const context = canvas.getContext('2d');
        if (!context) throw new Error('El navegador no pudo preparar la vista previa.');

        context.fillStyle = '#f2f8f4'; context.fillRect(0, 0, WIDTH, HEIGHT);
        context.fillStyle = '#145b46'; context.fillRect(0, 0, WIDTH, 185);
        context.fillStyle = '#ffffff'; context.font = '700 30px Arial';
        context.fillText('PETLY  ·  ADOPCIÓN RESPONSABLE', 72, 66);
        context.font = '700 54px Arial'; context.fillText('BUSCA UN HOGAR', 72, 137);

        context.save(); context.beginPath(); context.roundRect(72, 220, 936, 650, 28); context.clip();
        coverImage(context, bitmap, 72, 220, 936, 650); context.restore(); bitmap.close();

        context.fillStyle = '#145b46'; context.font = '700 64px Arial';
        const name = pet.nombre.trim();
        while (context.measureText(name).width > 930 && context.font !== '700 38px Arial') {
          const size = Number(context.font.match(/\d+/)?.[0] ?? 64) - 2;
          context.font = `700 ${Math.max(38, size)}px Arial`;
        }
        context.fillText(name, 72, 965, 936);
        context.fillStyle = '#253b32'; context.font = '600 30px Arial';
        const details = [pet.especie, pet.tamanio, pet.edad_anos === null || pet.edad_anos === undefined
          ? 'Edad por evaluar' : `${pet.edad_anos} años${pet.edad_meses ? ` y ${pet.edad_meses} meses` : ''}`]
          .filter(Boolean).join('  ·  ');
        context.fillText(details, 72, 1025, 936);
        context.strokeStyle = '#c4d9ce'; context.lineWidth = 2;
        context.beginPath(); context.moveTo(72, 1080); context.lineTo(1008, 1080); context.stroke();
        context.fillStyle = '#145b46'; context.font = '700 30px Arial';
        context.fillText(shelterName, 72, 1135, 936);
        context.fillStyle = '#253b32'; context.font = '26px Arial';
        context.fillText(contact.join('  ·  '), 72, 1190, 936);
        context.fillStyle = '#5c7067'; context.font = '22px Arial';
        context.fillText('Conoce más y coordina directamente con el albergue.', 72, 1270, 936);
        setLoading(false);
      } catch (reason) {
        if (!cancelled) { setError(errorMessage(reason)); setLoading(false); }
      }
    }
    void renderPoster();
    return () => { cancelled = true; };
  }, [pet, shelterEmail, shelterName, shelterPhone, contactReady]);

  function download() {
    const canvas = canvasRef.current;
    if (!canvas || !canPublish || loading || error) return;
    canvas.toBlob(blob => {
      if (!blob) { setError('No se pudo preparar el PNG. Intenta de nuevo.'); return; }
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement('a');
      anchor.href = url; anchor.download = `petly-afiche-${pet.id}.png`; anchor.click();
      URL.revokeObjectURL(url);
    }, 'image/png');
  }

  return <section className="poster-preview" aria-labelledby="poster-heading">
    <h3 id="poster-heading">Borrador de afiche · 1080 × 1350 px</h3>
    <p className="muted">Revisa la composición antes de descargar. El afiche no se publica en redes.</p>
    {loading && <p role="status">Preparando vista previa…</p>}
    {error && <p className="feedback error" role="alert">{error}</p>}
    <canvas ref={canvasRef} width={WIDTH} height={HEIGHT} role="img"
      aria-label={`Vista previa del afiche de ${pet.nombre}`} />
    <div className="actions">
      <button type="button" disabled={!canPublish || loading || Boolean(error)} onClick={download}>Descargar PNG</button>
      <button type="button" className="secondary" onClick={onClose}>Cerrar vista previa</button>
    </div>
  </section>;
}
