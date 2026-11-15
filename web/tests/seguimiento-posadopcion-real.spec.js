import { test, expect } from '@playwright/test';
import { readFileSync } from 'node:fs';

const accountsPath = process.env.PETLY_UI_ACCOUNTS;
const expediente = Number(process.env.PETLY_SEGUIMIENTO_EXPEDIENTE);
const accounts = accountsPath ? JSON.parse(readFileSync(accountsPath, 'utf8')) : null;
test.skip(!accounts, 'Se requieren cuentas de albergue y adoptante en una base de prueba.');

async function iniciarSesion(page, role) {
  await page.getByLabel('Correo', { exact: true }).fill(accounts[role].correo);
  await page.getByLabel('Contraseña', { exact: true }).fill(accounts[role].password);
  await page.getByRole('button', { name: 'Entrar', exact: true }).click();
  await page.waitForURL(url => url.pathname !== '/acceso');
}

async function ingresar(browser, role, viewport) {
  const page = await browser.newPage({ viewport });
  page.on('dialog', dialog => dialog.accept());
  await page.goto('/acceso');
  await iniciarSesion(page, role);
  return page;
}

async function adjuntarFoto(page) {
  await page.getByLabel(/Fotos JPEG o PNG/).setInputFiles({
    name: 'adaptacion.png',
    mimeType: 'image/png',
    buffer: Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAQAAAAECAIAAAAmkwkpAAAAFElEQVR4nGMUSQtmgAEmBiSAmwMAKM8A1R4JqD0AAAAASUVORK5CYII=', 'base64'),
  });
}

test('albergue programa, adoptante entrega una foto y albergue evalúa y cierra', async ({ browser }) => {
  test.skip(!expediente, 'Se requiere una adopción entregada sin seguimientos en una base de prueba.');
  const albergue = await ingresar(browser, 'ALBERGUE', { width: 1440, height: 960 });
  const adoptante = await ingresar(browser, 'ADOPTANTE', { width: 390, height: 844 });
  try {
    await albergue.getByRole('button', { name: 'Adopciones', exact: true }).click();
    await albergue.getByRole('link', { name: /Seguimiento Programa visitas/ }).click();
    await albergue.getByRole('link', { name: new RegExp(`Expediente #${expediente}(?: |$)`) }).click();
    const detalle = albergue.getByRole('button', { name: 'Evaluación y cierre', exact: true });
    await detalle.click();
    await expect(albergue.getByRole('button', { name: 'Guardar evaluación' })).toBeDisabled();
    await expect(albergue.getByText('La evaluación se habilita cuando el adoptante envíe el primer reporte.')).toBeVisible();

    await albergue.getByRole('button', { name: 'Cronograma', exact: true }).click();
    const fecha = days => new Date(Date.now() + days * 86400000 - 5 * 3600000).toISOString().slice(0, 16);
    await albergue.getByLabel('Fecha y hora (Lima)').fill(fecha(3));
    await albergue.getByRole('button', { name: 'Añadir al cronograma' }).click();
    await expect(albergue.getByText('Actividad añadida al cronograma.')).toBeVisible();
    await albergue.getByLabel('Tipo de actividad').selectOption('REPORTE');
    await albergue.getByLabel('Fecha y hora límite (Lima)').fill(fecha(1));
    await albergue.getByLabel('Qué debe incluir el reporte').fill('Incluye una foto de su lugar de descanso.');
    await albergue.getByRole('button', { name: 'Añadir al cronograma' }).click();
    await expect(albergue.getByText('Entrega pendiente')).toBeVisible();

    await adoptante.goto(`/adoptante/seguimientos-postadopcion?solicitud=${expediente}`);
    await iniciarSesion(adoptante, 'ADOPTANTE');
    await expect(adoptante.getByText('Entrega pendiente')).toBeVisible();
    await expect(adoptante.getByText('Visita', { exact: true })).toBeVisible();
    await expect(adoptante.getByText(/Incluye una foto de su lugar de descanso/)).toBeVisible();
    expect(await adoptante.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
    await adoptante.getByRole('button', { name: 'Preparar reporte' }).click();
    await adoptante.getByLabel('Observaciones').fill('Come bien y ya descansa tranquila.');
    await adjuntarFoto(adoptante);
    await adoptante.getByRole('button', { name: 'Enviar reporte', exact: true }).click();
    await expect(adoptante.getByText('Reporte enviado al albergue.')).toBeVisible();

    await albergue.reload();
    await iniciarSesion(albergue, 'ALBERGUE');
    await albergue.getByRole('button', { name: 'Reportes', exact: true }).click();
    await albergue.getByText('Revisar reporte y fotos').click();
    await expect(albergue.getByText('Come bien y ya descansa tranquila.')).toBeVisible();
    const foto = albergue.getByRole('img', { name: /Foto 1 del reporte/ });
    await expect(foto).toBeVisible();
    await expect.poll(() => foto.evaluate(img => img.naturalWidth)).toBeGreaterThan(0);
    await albergue.setViewportSize({ width: 390, height: 844 });
    expect(await albergue.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);

    await detalle.click();
    await expect(albergue.getByRole('button', { name: 'Cerrar expediente' })).toBeDisabled();
    await albergue.getByLabel('Nivel de adaptación').selectOption('4');
    await albergue.getByRole('button', { name: 'Guardar evaluación' }).click();
    await expect(albergue.getByText('Evaluación guardada.')).toBeVisible();
    await albergue.getByRole('button', { name: 'Cerrar expediente' }).click();
    await expect(albergue.getByRole('heading', { name: 'Expediente cerrado' })).toBeVisible();
    await albergue.reload();
    await iniciarSesion(albergue, 'ALBERGUE');
    await detalle.click();
    await expect(albergue.getByRole('heading', { name: 'Expediente cerrado' })).toBeVisible();
    await expect(albergue.getByText('4/5', { exact: true })).toBeVisible();
  } finally {
    await albergue.close();
    await adoptante.close();
  }
});

test('la bandeja enlaza a entregas próximas y la alerta vencida se conserva tras el envío tardío', async ({ browser }) => {
  const expedienteVencido = Number(process.env.PETLY_SEGUIMIENTO_VENCIDO);
  const mascota = process.env.PETLY_SEGUIMIENTO_MASCOTA ?? 'Canela de prueba';
  test.skip(!expedienteVencido, 'Se requiere un expediente de prueba con una entrega vencida y otra dentro de la ventana de aviso.');
  const albergue = await browser.newPage();
  albergue.on('dialog', dialog => dialog.accept());
  try {
    await albergue.goto(`/albergue/seguimientos?expediente=${expedienteVencido}`);
    await iniciarSesion(albergue, 'ALBERGUE');
    await expect(albergue.getByText('Entrega vencida', { exact: true })).toBeVisible();
    await albergue.getByRole('button', { name: 'Emitir alerta roja de incumplimiento' }).click();
    await expect(albergue.getByText('Alerta roja emitida.')).toBeVisible();

    const adoptante = await ingresar(browser, 'ADOPTANTE', { width: 390, height: 844 });
    try {
      const abrirAvisos = () => adoptante.getByRole('button', { name: /^Avisos/ }).click();
      await abrirAvisos();
      const recordatorio = adoptante.getByRole('button', { name: /^Próxima entrega de reporte/ })
        .filter({ hasText: mascota });
      await expect(recordatorio).toBeVisible({ timeout: 70_000 });
      await expect(recordatorio).toContainText('Fecha límite');
      await recordatorio.click();
      const proxima = adoptante.getByRole('listitem').filter({ hasText: 'Entrega para probar el recordatorio.' }).filter({ hasText: mascota });
      await expect(proxima.getByRole('heading', { name: `Reporte de ${mascota}` })).toBeVisible();

      await abrirAvisos();
      const alerta = adoptante.getByRole('button', { name: /^Alerta roja de incumplimiento/ })
        .filter({ hasText: mascota });
      await expect(alerta).toHaveCount(1);
      await alerta.click();
      const vencida = adoptante.getByRole('listitem').filter({ hasText: 'Entrega para probar incumplimiento.' }).filter({ hasText: mascota });
      await expect(vencida.getByRole('heading', { name: `Reporte de ${mascota}` })).toBeVisible();
      await adjuntarFoto(adoptante);
      await adoptante.getByRole('button', { name: 'Enviar reporte', exact: true }).click();
      await expect(adoptante.getByText('Reporte enviado al albergue.')).toBeVisible();
      await expect(vencida.getByText('Enviado fuera de plazo', { exact: true }).first()).toBeVisible();
    } finally {
      await adoptante.close();
    }

    await albergue.reload();
    await iniciarSesion(albergue, 'ALBERGUE');
    await expect(albergue.getByText('Recibido fuera de plazo', { exact: true })).toBeVisible();
    await expect(albergue.getByText(/Alerta roja de incumplimiento emitida el/)).toBeVisible();
    await expect(albergue.getByRole('button', { name: 'Emitir alerta roja de incumplimiento' })).toHaveCount(0);
  } finally {
    await albergue.close();
  }
});

test('cada solicitud abre su propio chat y el albergue ve el contexto', async ({ browser }) => {
  const casos = JSON.parse(process.env.PETLY_AJUSTES_SOLICITUDES ?? '[]');
  test.skip(casos.length !== 2, 'Se requieren dos solicitudes del mismo albergue para mascotas distintas.');
  const adoptante = await ingresar(browser, 'ADOPTANTE', { width: 1440, height: 960 });
  const albergue = await ingresar(browser, 'ALBERGUE', { width: 1440, height: 960 });
  try {
    const conversaciones = new Set();
    for (const caso of casos) {
      await adoptante.getByRole('button', { name: 'Mi adopción', exact: true }).click();
      await adoptante.getByRole('link', { name: /Mis solicitudes/ }).click();
      const solicitud = adoptante.locator('article').filter({ has: adoptante.getByRole('heading', { name: caso.nombre, exact: true }) });
      await solicitud.getByRole('link', { name: 'Abrir chat con el albergue' }).click();
      await expect(adoptante).toHaveURL(new RegExp(`/adoptante/mensajes/\\d+\\?mascota=${caso.mascota}&solicitud=${caso.id}$`));
      await expect(adoptante.getByRole('heading', { name: new RegExp(`${caso.nombre} · Solicitud #${caso.id}$`) })).toBeVisible();
      conversaciones.add(new URL(adoptante.url()).pathname);
    }
    expect(conversaciones.size).toBe(2);

    for (const caso of casos) {
      await albergue.getByRole('button', { name: 'Adopciones', exact: true }).click();
      await albergue.getByRole('link', { name: /^Solicitudes/ }).click();
      const solicitud = albergue.locator(`#solicitud-${caso.id}`);
      await solicitud.getByRole('link', { name: 'Conversar con el adoptante' }).click();
      await expect(albergue).toHaveURL(new RegExp(`/albergue/mensajes/\\d+\\?mascota=${caso.mascota}&solicitud=${caso.id}$`));
      await expect(albergue.getByRole('heading', { name: new RegExp(`${caso.nombre} · Solicitud #${caso.id}$`) })).toBeVisible();
    }
  } finally {
    await adoptante.close();
    await albergue.close();
  }
});

test('el adoptante puede quitar una entrega tardía y el albergue conserva su reporte', async ({ browser }) => {
  const entrega = Number(process.env.PETLY_AJUSTES_ENTREGA);
  const expediente = Number(process.env.PETLY_AJUSTES_EXPEDIENTE);
  test.skip(!entrega || !expediente, 'Se requiere una entrega tardía visible en una base de prueba.');
  const adoptante = await browser.newPage();
  const albergue = await browser.newPage();
  adoptante.on('dialog', dialog => dialog.accept());
  try {
    await adoptante.goto(`/adoptante/seguimientos-postadopcion?entrega=${entrega}`);
    await iniciarSesion(adoptante, 'ADOPTANTE');
    const hito = adoptante.locator(`#seguimiento-${entrega}`);
    await expect(hito.getByText('Enviado fuera de plazo', { exact: true })).toHaveCount(2);
    await expect(hito.getByText(/Qué incluir en tu reporte:/)).toBeVisible();
    await expect(hito).toContainText(process.env.PETLY_AJUSTES_DESCRIPCION ?? 'Incluye');
    await expect(adoptante.locator('.seguimiento-hito')).toHaveCount(6);

    const api = `**/api/contratos/seguimientos/${entrega}/ocultar/`;
    await adoptante.route(api, route => route.fulfill({ status: 500, json: { detail: 'Error al eliminar la entrega.' } }));
    await hito.getByRole('button', { name: 'Eliminar', exact: true }).click();
    await expect(adoptante.getByRole('alert')).toContainText('Error al eliminar la entrega.');
    await expect(hito).toBeVisible();
    await adoptante.unroute(api);
    await hito.getByRole('button', { name: 'Eliminar', exact: true }).click();
    await expect(adoptante.getByText('Entrega eliminada de tu cronograma.')).toBeVisible();
    await adoptante.goto(`/adoptante/seguimientos-postadopcion?entrega=${entrega}`);
    await iniciarSesion(adoptante, 'ADOPTANTE');
    await expect(adoptante.getByText('Esta entrega no está disponible.')).toBeVisible();
    await expect(hito).toHaveCount(0);

    await albergue.goto(`/albergue/seguimientos?expediente=${expediente}`);
    await iniciarSesion(albergue, 'ALBERGUE');
    await albergue.getByRole('button', { name: 'Reportes', exact: true }).click();
    const reporte = albergue.locator(`#seguimiento-${entrega}`);
    await expect(reporte.locator('.seguimiento-estado')).toHaveText('Recibido fuera de plazo');
    await reporte.getByText('Revisar reporte y fotos').click();
    await expect(reporte.getByRole('img', { name: /Foto del reporte|Foto 1 del reporte/ })).toBeVisible();
  } finally {
    await adoptante.close();
    await albergue.close();
  }
});

test('una programación genera un aviso que apunta a su tarjeta tras editar y cancelar', async ({ browser }) => {
  const solicitud = Number(process.env.PETLY_PROGRAMACION_EXPEDIENTE);
  test.skip(!solicitud, 'Se requiere un expediente entregado sin actividades en una base de prueba.');
  const marcador = 'Prueba de programación y aviso de seguimiento.';
  const notasNuevas = 'Prueba de programación y aviso de seguimiento: horario actualizado.';
  const albergue = await browser.newPage({ viewport: { width: 1440, height: 900 } });
  const adoptante = await browser.newPage({ viewport: { width: 390, height: 844 } });
  albergue.on('dialog', dialog => dialog.accept());
  try {
    await albergue.goto(`/albergue/seguimientos?expediente=${solicitud}`);
    await iniciarSesion(albergue, 'ALBERGUE');
    await adoptante.goto(`/adoptante/seguimientos-postadopcion?solicitud=${solicitud}`);
    await iniciarSesion(adoptante, 'ADOPTANTE');
    await expect(adoptante.getByText('Aún no tienes seguimientos postadopción programados.')).toBeVisible();
    await expect(adoptante.locator('.seguimiento-hito')).toHaveCount(0);

    const fecha = dias => new Date(Date.now() + dias * 86400000 - 5 * 3600000).toISOString().slice(0, 16);
    await albergue.getByLabel('Tipo de actividad').selectOption('LLAMADA');
    await albergue.getByLabel('Fecha y hora (Lima)').fill(fecha(10));
    await albergue.getByLabel('Notas').fill(marcador);
    const creada = albergue.waitForResponse(response => response.status() === 201
      && new URL(response.url()).pathname === '/api/contratos/seguimientos/');
    await albergue.getByRole('button', { name: 'Añadir al cronograma' }).click();
    const actividad = await (await creada).json();
    const aviso = () => adoptante.getByRole('button', { name: /^Nuevo seguimiento postadopción/ })
      .filter({ hasText: actividad.mascota_nombre });

    await adoptante.getByRole('button', { name: /^Avisos/ }).click();
    await expect(aviso()).toHaveCount(1);
    await expect(aviso()).toContainText('Fecha programada:');
    await aviso().click();
    await expect(adoptante).toHaveURL(new RegExp(`seguimiento=${actividad.id}#seguimiento-${actividad.id}$`));
    const tarjeta = adoptante.locator(`#seguimiento-${actividad.id}`);
    await expect(tarjeta).toContainText(marcador);
    await expect(tarjeta.locator('.seguimiento-estado')).toHaveText('Programada');
    await expect(tarjeta).toBeInViewport();
    const fechaOriginal = await tarjeta.getByText(/^Fecha programada/).textContent();

    const agenda = albergue.locator(`#seguimiento-${actividad.id}`);
    await agenda.getByRole('button', { name: 'Editar', exact: true }).click();
    await albergue.getByLabel('Fecha y hora (Lima)').fill(fecha(11));
    await albergue.getByLabel('Notas').fill(notasNuevas);
    await albergue.getByRole('button', { name: 'Guardar cambios' }).click();
    await expect(agenda).toContainText(notasNuevas);
    await adoptante.getByRole('button', { name: 'Actualizar cronograma' }).click();
    await expect(tarjeta).toContainText(notasNuevas);
    await expect(tarjeta.getByText(/^Fecha programada/)).not.toHaveText(fechaOriginal);

    await agenda.getByRole('button', { name: 'Cancelar', exact: true }).click();
    await expect(agenda.locator('.seguimiento-estado')).toHaveText('Cancelada');
    await adoptante.goto('/adoptante/seguimientos-postadopcion');
    await iniciarSesion(adoptante, 'ADOPTANTE');
    await adoptante.getByRole('button', { name: /^Avisos/ }).click();
    await expect(aviso()).toHaveCount(1);
    await aviso().click();
    await expect(tarjeta.locator('.seguimiento-estado')).toHaveText('Cancelada');
    await expect(tarjeta.getByText(/^Cancelado el/)).toBeVisible();
    await expect(tarjeta).toBeInViewport();
  } finally {
    await adoptante.close();
    await albergue.close();
  }
});
