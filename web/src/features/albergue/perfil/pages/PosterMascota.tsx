import { useEffect, useRef, useState } from 'react';
import { ApiError, errorMessage, mediaUrl } from '../../../../services/http';
import { cargarFondo, consultarAfiche, contactoAfiche, crearAfiche, type PedidoAfiche } from '../../../../services/afichesApi';
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

const estados = {
  PENDIENTE: 'En cola',
  RESERVADO: 'Generando fondo con IA…',
  COMPLETADO: 'Fondo generado',
  FALLIDO: 'No se pudo generar el fondo',
};

const errores: Record<string, string> = {
  SESION: 'La sesión local de Codex caducó.',
  CUPO: 'Codex agotó el cupo de generación.',
  PLAZO: 'La generación superó el tiempo máximo.',
  PNG_INVALIDO: 'Codex no devolvió un PNG válido.',
  FOTO: 'No se pudo usar la foto original. Comprueba la foto principal.',
  RESERVA_VENCIDA: 'Se agotaron los intentos de generación.',
};

export default function PosterMascota({ pet, shelter, pedidoId, onPedido, onClose }: {
  pet: AdminPet;
  shelter: Shelter;
  pedidoId: number | null;
  onPedido: (id: number) => void;
  onClose: () => void;
}) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const headingRef = useRef<HTMLHeadingElement>(null);
  const [pedido, setPedido] = useState<PedidoAfiche | null>(null);
  const [errorPedido, setErrorPedido] = useState('');
  const [errorCanvas, setErrorCanvas] = useState('');
  const [listo, setListo] = useState('');
  const [creando, setCreando] = useState(!pedidoId);
  const iniciar = useRef<Promise<PedidoAfiche> | null>(null);
  const reintento = useRef<AbortController | null>(null);
  const onPedidoRef = useRef(onPedido);

  useEffect(() => { onPedidoRef.current = onPedido; }, [onPedido]);
  useEffect(() => {
    headingRef.current?.focus();
    return () => reintento.current?.abort();
  }, []);

  async function solicitar(pendiente: Promise<PedidoAfiche>, signal?: AbortSignal) {
    setCreando(true);
    setErrorPedido('');
    try {
      const nuevo = await pendiente;
      if (signal?.aborted) return;
      setPedido(nuevo);
      onPedidoRef.current(nuevo.id);
    } catch (error) {
      if (!signal?.aborted) setErrorPedido(errorMessage(error));
    } finally {
      if (!signal?.aborted) setCreando(false);
    }
  }

  function volverAIntentar() {
    reintento.current?.abort();
    const controller = new AbortController();
    reintento.current = controller;
    void solicitar(crearAfiche(pet.id), controller.signal);
  }

  useEffect(() => {
    if (pedidoId) return;
    iniciar.current ??= crearAfiche(pet.id);
    const controller = new AbortController();
    void solicitar(iniciar.current, controller.signal);
    return () => controller.abort();
  }, [pedidoId, pet.id]);

  useEffect(() => {
    if (!pedidoId) return;
    const controller = new AbortController();
    let timer: number | undefined;
    async function consultar(id: number) {
      let demora = 2500;
      try {
        const actual = await consultarAfiche(id, controller.signal);
        if (controller.signal.aborted) return;
        if (actual.mascota !== pet.id) throw new ApiError(404, 'El pedido no pertenece a esta mascota.');
        setPedido(actual);
        setErrorPedido('');
        if (actual.estado === 'COMPLETADO' || actual.estado === 'FALLIDO') return;
      } catch (error) {
        if (controller.signal.aborted) return;
        setErrorPedido(errorMessage(error));
        if (error instanceof ApiError && [401, 403, 404].includes(error.status)) return;
        if (error instanceof ApiError && error.status === 429)
          demora = Math.max(demora, error.retryAfterSeconds * 1000);
      }
      timer = window.setTimeout(() => void consultar(id), demora);
    }
    void consultar(pedidoId);
    return () => {
      controller.abort();
      window.clearTimeout(timer);
    };
  }, [pedidoId, pet.id]);

  const perfilActual = contactoAfiche(shelter);
  const shelterName = pedido?.datos_afiche.nombre || perfilActual.nombre;
  const shelterPhone = pedido?.datos_afiche.telefono ?? perfilActual.telefono;
  const shelterEmail = pedido?.datos_afiche.email ?? perfilActual.email;
  const foto = pedido?.foto_url || pet.foto_principal;
  const fondo = pedido?.fondo_url;
  const version = [foto, fondo, shelterName, shelterPhone, shelterEmail].join('|');
  const loading = listo !== version;

  useEffect(() => {
    const controller = new AbortController();
    const { signal } = controller;
    async function renderPoster() {
      setErrorCanvas('');
      const canvas = canvasRef.current;
      if (!canvas || !foto) return;
      let bitmap: ImageBitmap | undefined;
      let fondoBitmap: ImageBitmap | undefined;
      try {
        const contact = [shelterPhone, shelterEmail].filter(Boolean);
        const response = await fetch(mediaUrl(foto)!, { signal });
        if (!response.ok) throw new Error('No se pudo cargar la foto de la mascota.');
        bitmap = await createImageBitmap(await response.blob());
        if (fondo) {
          try {
            fondoBitmap = await createImageBitmap(await cargarFondo(fondo, signal));
          } catch {
            if (!signal.aborted) setErrorCanvas('No se pudo cargar el fondo generado.');
          }
        }
        if (signal.aborted) return;
        const context = canvas.getContext('2d');
        if (!context) throw new Error('El navegador no pudo preparar la vista previa.');

        context.fillStyle = '#f2f8f4'; context.fillRect(0, 0, WIDTH, HEIGHT);
        if (fondoBitmap) coverImage(context, fondoBitmap, 0, 0, WIDTH, HEIGHT);
        context.fillStyle = fondoBitmap ? 'rgba(20,91,70,.92)' : '#145b46';
        context.fillRect(0, 0, WIDTH, 185);
        if (fondoBitmap) {
          context.fillStyle = 'rgba(242,248,244,.92)';
          context.beginPath(); context.roundRect(44, 908, 992, 396, 28); context.fill();
        }
        context.fillStyle = '#ffffff'; context.font = '700 30px Arial';
        context.fillText('PETLY  ·  ADOPCIÓN RESPONSABLE', 72, 66);
        context.font = '700 54px Arial'; context.fillText('BUSCA UN HOGAR', 72, 137);

        context.save(); context.beginPath(); context.roundRect(72, 220, 936, 650, 28); context.clip();
        coverImage(context, bitmap, 72, 220, 936, 650); context.restore();

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
      } catch (reason) {
        if (!signal.aborted) setErrorCanvas(errorMessage(reason));
      } finally {
        bitmap?.close();
        fondoBitmap?.close();
        if (!signal.aborted) setListo(version);
      }
    }
    void renderPoster();
    return () => controller.abort();
  }, [pet, foto, fondo, shelterEmail, shelterName, shelterPhone, version]);

  const canDownload = pedido?.estado === 'COMPLETADO' && Boolean(fondo) && !loading && !errorCanvas;

  function download() {
    const canvas = canvasRef.current;
    if (!canvas || !canDownload) return;
    canvas.toBlob(blob => {
      if (!blob) { setErrorCanvas('No se pudo preparar el PNG. Intenta de nuevo.'); return; }
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement('a');
      anchor.href = url; anchor.download = `petly-afiche-${pet.id}.png`; anchor.click();
      URL.revokeObjectURL(url);
    }, 'image/png');
  }

  return <section className="poster-preview" aria-labelledby="poster-heading">
    <h3 id="poster-heading" ref={headingRef} tabIndex={-1}>Borrador de afiche · 1080 × 1350 px</h3>
    <p className="muted">Revisa la composición antes de descargar. El afiche no se publica en redes.</p>
    <p role="status">{creando ? 'Registrando pedido…' : pedido ? estados[pedido.estado] : 'Consultando pedido…'}</p>
    {loading && <p>Preparando vista previa…</p>}
    {(errorPedido || errorCanvas || pedido?.estado === 'FALLIDO') && (
      <p className="feedback error" role="alert">{errorPedido || errorCanvas || errores[pedido?.error_codigo ?? ''] || 'Intenta generar el fondo nuevamente.'}</p>
    )}
    <canvas ref={canvasRef} width={WIDTH} height={HEIGHT} role="img"
      aria-label={`Vista previa del afiche de ${pet.nombre}`} />
    <div className="actions">
      <button type="button" disabled={!canDownload} onClick={download}>Descargar PNG</button>
      {(pedido?.estado === 'FALLIDO' || errorPedido || errorCanvas) && (
        <button type="button" disabled={creando} onClick={volverAIntentar}>Reintentar</button>
      )}
      <button type="button" className="secondary" onClick={onClose}>Cerrar vista previa</button>
    </div>
  </section>;
}
