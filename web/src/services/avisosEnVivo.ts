import { fetchDjangoFile } from './http';

type EventoAvisos = { tipo: 'sincronizar' } | { tipo: 'avisos'; solicitudes: number[] };
const suscriptores = new Set<(evento: EventoAvisos) => void>();
let conectado = false;

export function avisosConectados() {
  return conectado;
}

export function observarAvisos(fn: (evento: EventoAvisos) => void) {
  suscriptores.add(fn);
  return () => { suscriptores.delete(fn); };
}

function publicar(trama: string) {
  const lineas = trama.split('\n');
  const evento = lineas.find(linea => linea.startsWith('event: '))?.slice(7);
  if (evento === 'sincronizar') {
    conectado = true;
    suscriptores.forEach(fn => fn({ tipo: 'sincronizar' }));
  } else if (evento === 'avisos' && conectado) {
    const contenido = lineas.filter(linea => linea.startsWith('data: ')).map(linea => linea.slice(6)).join('\n');
    const datos = JSON.parse(contenido) as { solicitudes?: unknown };
    if (!Array.isArray(datos.solicitudes)) return;
    const solicitudes = datos.solicitudes.filter((id: unknown): id is number =>
      typeof id === 'number' && Number.isSafeInteger(id) && id > 0);
    suscriptores.forEach(fn => fn({ tipo: 'avisos', solicitudes }));
  }
}

// fetch permite autenticar el flujo con el mismo Bearer que las API.
async function recibir(signal: AbortSignal, alRecibir: () => void) {
  const respuesta = await fetchDjangoFile('/api/acceso/notificaciones/eventos/', {
    headers: { Accept: 'text/event-stream' },
    cache: 'no-store',
    signal,
  });
  if (!respuesta.ok || !respuesta.body ||
      !respuesta.headers.get('Content-Type')?.startsWith('text/event-stream')) {
    await respuesta.body?.cancel();
    return;
  }

  const lector = respuesta.body.pipeThrough(new TextDecoderStream()).getReader();
  let pendiente = '';
  try {
    while (!signal.aborted) {
      const { value, done } = await lector.read();
      if (done || signal.aborted) break;
      alRecibir();
      pendiente += value;
      let fin = pendiente.indexOf('\n\n');
      while (fin >= 0) {
        publicar(pendiente.slice(0, fin));
        pendiente = pendiente.slice(fin + 2);
        fin = pendiente.indexOf('\n\n');
      }
      if (pendiente.length > 16_384) break;
    }
  } finally {
    try { await lector.cancel(); } catch { /* El transporte ya puede estar cerrado. */ }
    lector.releaseLock();
  }
}

export function conectarAvisos() {
  let cerrado = false;
  let intento: AbortController | undefined;
  let reintento: number | undefined;
  let demora = 1000;

  async function conectar() {
    intento = new AbortController();
    const conexion = intento;
    // Si el transporte se atasca, volver al respaldo aunque fetch siga pendiente.
    let ultimoDato = Date.now();
    const vigilar = window.setInterval(() => {
      if (Date.now() - ultimoDato > 25_000) conexion.abort();
    }, 1000);
    try {
      await recibir(conexion.signal, () => { ultimoDato = Date.now(); });
    } catch {
      // Las consultas REST siguen actualizando las pantallas hasta reconectar.
    } finally {
      window.clearInterval(vigilar);
      if (!cerrado) {
        demora = conectado ? 1000 : Math.min(demora * 2, 5000);
        conectado = false;
        reintento = window.setTimeout(() => void conectar(), demora);
      }
    }
  }

  void conectar();
  return () => {
    cerrado = true;
    conectado = false;
    intento?.abort();
    window.clearTimeout(reintento);
  };
}
