'use strict';

const { spawn } = require('node:child_process');
const { createHash } = require('node:crypto');
const { mkdtemp, readFile, writeFile, rm } = require('node:fs/promises');
const { tmpdir } = require('node:os');
const { join } = require('node:path');
const { setTimeout: esperar } = require('node:timers/promises');

const PROMPT = 'Usa la herramienta integrada de generación de imágenes. Crea un fondo vertical 4:5 para un afiche de adopción, inspirado en los colores de la foto. Deja espacio arriba, en el centro y abajo para colocar la foto original y el texto. Genera solo el fondo sin texto, marcos ni animales. Guarda un PNG válido de proporción exacta 4:5 como fondo.png en esta carpeta. Si no puedes generar la imagen con la herramienta integrada, informa el error sin crear un sustituto.';
const BASE = (process.env.AFICHE_DJANGO_URL || 'http://127.0.0.1:8000').replace(/\/$/, '');
const TOKEN = process.env.AFICHE_WORKER_TOKEN;
const PYTHON = process.env.AFICHE_PYTHON || join(__dirname, 'backend-admin', '.venv', 'bin', 'python');
const PLAZO_MS = Number(process.env.AFICHE_CODEX_TIMEOUT_MS || 180_000);
const PARADA = new AbortController();
const MAX_BYTES = 12 * 1024 * 1024;
const ENTORNO_CODEX = Object.fromEntries([
  'PATH', 'HOME', 'USER', 'LOGNAME', 'LANG', 'LC_ALL', 'TMPDIR', 'TERM', 'CODEX_HOME',
  'XDG_RUNTIME_DIR', 'XDG_CONFIG_HOME', 'XDG_DATA_HOME', 'DBUS_SESSION_BUS_ADDRESS',
  'SSL_CERT_FILE', 'SSL_CERT_DIR', 'HTTPS_PROXY', 'HTTP_PROXY', 'NO_PROXY',
].filter(clave => process.env[clave] !== undefined).map(clave => [clave, process.env[clave]]));

class Fallo extends Error {
  constructor(codigo, temporal = false) {
    super(codigo);
    this.codigo = codigo;
    this.temporal = temporal;
  }
}

function registrar(evento, datos = {}) {
  console.log(JSON.stringify({ fecha: new Date().toISOString(), evento, ...datos }));
}

function ejecutar(programa, argumentos, cwd, plazo) {
  return new Promise((resolve, reject) => {
    PARADA.signal.throwIfAborted();
    const grupo = process.platform !== 'win32';
    const hijo = spawn(programa, argumentos, {
      cwd, env: ENTORNO_CODEX, detached: grupo, stdio: ['ignore', 'pipe', 'pipe'],
    });
    let salida = '';
    let vencio = false;
    const detener = () => {
      if (!hijo.pid) return;
      try {
        if (grupo) process.kill(-hijo.pid, 'SIGKILL');
        else hijo.kill('SIGKILL');
      } catch (error) {
        if (error.code !== 'ESRCH') registrar('error_al_detener_proceso', { codigo: error.code });
      }
    };
    const timer = setTimeout(() => { vencio = true; detener(); }, plazo);
    PARADA.signal.addEventListener('abort', detener, { once: true });
    const guardarSalida = datos => { salida = (salida + datos.toString()).slice(-32768); };
    hijo.stdout.on('data', guardarSalida);
    hijo.stderr.on('data', guardarSalida);
    hijo.once('error', reject);
    hijo.once('close', code => {
      clearTimeout(timer);
      PARADA.signal.removeEventListener('abort', detener);
      if (PARADA.signal.aborted) return reject(PARADA.signal.reason);
      if (vencio) return reject(new Fallo('PLAZO', true));
      resolve({ code, salida });
    });
  });
}

function detectarFalloCodex(salida) {
  if (/usage limit|quota exceeded|insufficient_quota|out of (?:credits|quota)|rate.limit|\b429\b|cupo agotado/i.test(salida))
    return new Fallo('CUPO');
  if (/codex login|token.{0,30}expired|refresh token|unauthorized|\b401\b|not logged in|sesi[oó]n caduc/i.test(salida))
    return new Fallo('SESION');
  return null;
}

async function api(ruta, datos, plazo = 15_000) {
  const headers = { 'X-Afiche-Worker-Token': TOKEN };
  const form = datos instanceof FormData;
  if (datos && !form) headers['Content-Type'] = 'application/json';
  const respuesta = await fetch(BASE + ruta, {
    method: 'POST', headers, body: form ? datos : datos && JSON.stringify(datos),
    redirect: 'error', signal: AbortSignal.any([PARADA.signal, AbortSignal.timeout(plazo)]),
  });
  if (!respuesta.ok) {
    const error = new Fallo('ENTREGA', respuesta.status >= 500);
    error.estadoHttp = respuesta.status;
    throw error;
  }
  return respuesta.status === 204 ? null : respuesta.json();
}

async function descargarFoto(pedido, carpeta) {
  try {
    const respuesta = await fetch(pedido.foto_url, {
      signal: AbortSignal.any([PARADA.signal, AbortSignal.timeout(15_000)]),
    });
    if (!respuesta.ok || !respuesta.body) throw new Fallo('FOTO', respuesta.status >= 500);
    const bloques = [];
    let tamano = 0;
    for await (const bloque of respuesta.body) {
      tamano += bloque.length;
      if (tamano > MAX_BYTES) throw new Fallo('FOTO');
      bloques.push(bloque);
    }
    const foto = Buffer.concat(bloques);
    if (createHash('sha256').update(foto).digest('hex') !== pedido.foto_version) throw new Fallo('FOTO');
    const nombre = foto[0] === 0xff && foto[1] === 0xd8 ? 'mascota.jpg' : 'mascota.png';
    await writeFile(join(carpeta, nombre), foto, { mode: 0o600 });
    return nombre;
  } catch (error) {
    throw error instanceof Fallo ? error : new Fallo('FOTO', true);
  }
}

async function validarFondo(carpeta, plazo) {
  const script = [
    'from pathlib import Path',
    'from PIL import Image',
    'path = Path("fondo.png")',
    'assert 0 < path.stat().st_size <= 12 * 1024 * 1024',
    'with Image.open(path) as im:',
    '    assert im.format == "PNG" and im.width * 5 == im.height * 4 and im.height <= 4096',
    '    im.verify()',
    'with Image.open(path) as im:',
    '    im.load()',
  ].join('\n');
  const { code } = await ejecutar(PYTHON, ['-c', script], carpeta, plazo);
  if (code !== 0) throw new Fallo('PNG_INVALIDO', true);
  return readFile(join(carpeta, 'fondo.png'));
}

function tiempoRestante(pedido) {
  return Date.parse(pedido.reserva_vence_en) - Date.now();
}

async function generar(pedido, carpeta) {
  let foto;
  for (let intento = 1; intento <= 2; intento++) {
    const inicio = performance.now();
    try {
      const plazo = Math.min(PLAZO_MS, tiempoRestante(pedido) - 5_000);
      if (plazo <= 0) throw new Fallo('PLAZO');
      foto ??= await descargarFoto(pedido, carpeta);
      await rm(join(carpeta, 'fondo.png'), { force: true });
      const plazoCodex = Math.min(plazo, tiempoRestante(pedido) - 5_000);
      if (plazoCodex <= 0) throw new Fallo('PLAZO');
      registrar('generacion_iniciada', { pedido: pedido.id, intento });
      const { code, salida } = await ejecutar('codex', [
        'exec', '--skip-git-repo-check', '--sandbox', 'workspace-write', '-i', foto, '--', PROMPT,
      ], carpeta, plazoCodex);
      if (code !== 0) throw detectarFalloCodex(salida) ?? new Fallo('GENERACION', true);
      let fondo;
      try {
        fondo = await validarFondo(carpeta, 10_000);
      } catch (error) {
        throw detectarFalloCodex(salida) ?? error;
      }
      registrar('generacion', { pedido: pedido.id, intento, ms: Math.round(performance.now() - inicio), estado: 'OK' });
      return fondo;
    } catch (error) {
      if (PARADA.signal.aborted) throw error;
      const fallo = error instanceof Fallo ? error : new Fallo('GENERACION');
      registrar('generacion', { pedido: pedido.id, intento, ms: Math.round(performance.now() - inicio), codigo: fallo.codigo });
      if (!fallo.temporal || intento === 2 || tiempoRestante(pedido) <= 6_000) throw fallo;
      await esperar(1000, undefined, { signal: PARADA.signal });
    }
  }
}

async function entregar(ruta, datos, pedido) {
  for (let intento = 1; ; intento++) {
    const restante = tiempoRestante(pedido);
    if (restante <= 0) {
      const error = new Fallo('RESERVA_VENCIDA');
      error.estadoHttp = 409;
      throw error;
    }
    try {
      return await api(ruta, datos, Math.min(15_000, restante));
    } catch (error) {
      const temporal = error instanceof Fallo ? error.temporal : true;
      if (PARADA.signal.aborted || !temporal || intento === 2 || tiempoRestante(pedido) <= 1500) throw error;
      await esperar(1000, undefined, { signal: PARADA.signal });
    }
  }
}

async function procesar(pedido) {
  registrar('cola', {
    pedido: pedido.id, reserva_intento: pedido.intentos,
    ms: Math.max(0, Date.now() - Date.parse(pedido.creado_en)),
  });
  const carpeta = await mkdtemp(join(tmpdir(), 'petly-afiche-'));
  const ruta = `/api/mascotas/interno/afiches/${pedido.id}/entregar/`;
  try {
    let fondo;
    let fallo;
    try {
      fondo = await generar(pedido, carpeta);
    } catch (error) {
      if (PARADA.signal.aborted) throw error;
      fallo = error;
    }
    const inicio = performance.now();
    const datos = new FormData();
    datos.set('reserva', pedido.reserva);
    datos.set('estado', fondo ? 'COMPLETADO' : 'FALLIDO');
    if (fondo) datos.set('fondo', new Blob([fondo], { type: 'image/png' }), 'fondo.png');
    else {
      datos.set('error_codigo', fallo.codigo);
      datos.set('reintentar', 'false');
    }
    try {
      const resultado = await entregar(ruta, datos, pedido);
      registrar('entrega', { pedido: pedido.id, ms: Math.round(performance.now() - inicio), estado: resultado.estado, codigo: fallo?.codigo });
    } catch (error) {
      if (PARADA.signal.aborted) throw error;
      registrar('entrega', { pedido: pedido.id, ms: Math.round(performance.now() - inicio), codigo: error.estadoHttp === 409 ? 'RESERVA_VENCIDA' : 'ENTREGA', http: error.estadoHttp });
    }
  } finally {
    await rm(carpeta, { recursive: true, force: true });
  }
}

async function iniciar() {
  if (!TOKEN || !Number.isSafeInteger(PLAZO_MS) || PLAZO_MS <= 0) {
    throw new Error('Configura AFICHE_WORKER_TOKEN y un AFICHE_CODEX_TIMEOUT_MS positivo.');
  }
  registrar('trabajador_iniciado');
  while (!PARADA.signal.aborted) {
    try {
      const pedido = await api('/api/mascotas/interno/afiches/tomar/');
      if (pedido) {
        await procesar(pedido);
        continue;
      }
    } catch (error) {
      if (PARADA.signal.aborted) break;
      registrar('error_trabajador', { codigo: error.codigo || 'CONEXION', http: error.estadoHttp });
    }
    await esperar(5000, undefined, { signal: PARADA.signal }).catch(error => {
      if (!PARADA.signal.aborted) throw error;
    });
  }
}

for (const senal of ['SIGINT', 'SIGTERM']) {
  process.once(senal, () => PARADA.abort());
}
iniciar().catch(error => { console.error(error.message); process.exitCode = 1; });
