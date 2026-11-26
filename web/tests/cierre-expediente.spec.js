import { test, expect } from '@playwright/test';
import { readFile } from 'node:fs/promises';

const expediente = '/api/contratos/expedientes/42/';
const archivo = Buffer.from('%PDF-1.4\nCertificado simulado para verificar la descarga\n');

test('React descarga el certificado del expediente cerrado con la sesión del albergue', async ({ page }) => {
  const usuario = { id: '8', correo: 'albergue@example.test', rol: 'ALBERGUE' };
  let descargas = 0;
  await page.route('**/api/**', async route => {
    const path = new URL(route.request().url()).pathname;
    if (path === '/api/auth/refresh') return route.fulfill({ status: 401 });
    if (path === '/api/auth/login') return route.fulfill({ json: { token: 'token-albergue', expiresIn: 3600, user: usuario } });
    if (path === '/api/auth/me') return route.fulfill({ json: usuario });
    if (path === expediente) return route.fulfill({ json: {
      id: 42, mascota_nombre: 'Luna', adoptante_nombre: 'Ana', cronograma: [],
      evaluacion: { calificacion_adaptacion: 4, cerrado_en: '2026-12-10T17:00:00Z', observaciones_cierre: 'Bien adaptada' },
    } });
    if (path === `${expediente}certificado/`) {
      descargas++;
      expect(route.request().headers().authorization).toBe('Bearer token-albergue');
      return route.fulfill({ contentType: 'application/pdf', body: archivo });
    }
    return route.fulfill({ json: { results: [], next: null } });
  });

  await page.goto('/acceso');
  await page.getByLabel('Correo electrónico', { exact: true }).fill(usuario.correo);
  await page.getByLabel('Contraseña', { exact: true }).fill('clave-de-prueba');
  await page.getByRole('button', { name: 'Entrar', exact: true }).click();
  await expect(page).toHaveURL(/\/albergue$/);
  await page.evaluate(() => {
    window.history.pushState({}, '', '/albergue/seguimientos?expediente=42');
    window.dispatchEvent(new PopStateEvent('popstate'));
  });

  await page.getByRole('button', { name: 'Evaluación y cierre', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Expediente cerrado' })).toBeVisible();
  await expect(page.getByText('4/5', { exact: true })).toBeVisible();
  const evento = page.waitForEvent('download');
  await page.getByRole('button', { name: 'Descargar certificado PDF' }).click();
  const descarga = await evento;
  expect(descarga.suggestedFilename()).toBe('certificado-seguimiento-42.pdf');
  expect(await readFile(await descarga.path())).toEqual(archivo);
  expect(descargas).toBe(1);
});
