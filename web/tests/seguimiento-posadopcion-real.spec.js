import { test, expect } from '@playwright/test';
import { readFileSync } from 'node:fs';

const accountsPath = process.env.PETLY_UI_ACCOUNTS;
const expediente = Number(process.env.PETLY_SEGUIMIENTO_EXPEDIENTE);
const accounts = accountsPath ? JSON.parse(readFileSync(accountsPath, 'utf8')) : null;
test.skip(!accounts || !expediente, 'Se requiere una adopción entregada sin seguimientos en una base de prueba.');

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
    await albergue.getByRole('button', { name: 'Añadir al cronograma' }).click();
    await expect(albergue.getByText('Entrega pendiente')).toBeVisible();

    await adoptante.goto(`/adoptante/seguimientos-postadopcion?solicitud=${expediente}`);
    await iniciarSesion(adoptante, 'ADOPTANTE');
    await expect(adoptante.getByText('Entrega pendiente')).toBeVisible();
    await expect(adoptante.getByText('Visita', { exact: true })).toBeVisible();
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
      await expect(vencida.getByText('Recibido fuera de plazo', { exact: true }).first()).toBeVisible();
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
