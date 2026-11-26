import { test, expect } from '@playwright/test';

test('el adoptante solo puede descargar fichas de adopciones entregadas y puede reintentar', async ({ page }) => {
  const usuario = { id: '17', correo: 'adoptante@example.test', rol: 'ADOPTANTE' };
  const estados = ['ENTREGADA', 'APROBADA', 'PENDIENTE', 'OBSERVADA', 'RECHAZADA', 'CANCELADA'];
  const solicitudes = estados.map((estado, index) => ({
    id: 42 + index, mascota_nombre: `Mascota ${estado}`, estado, fecha_solicitud: '2026-11-13T10:30:00Z',
  }));
  const descargas = [];
  await page.route('**/api/**', async route => {
    const request = route.request();
    const path = new URL(request.url()).pathname;
    if (path === '/api/auth/refresh') return route.fulfill({ status: 401 });
    if (path === '/api/auth/login') return route.fulfill({ json: { token: 'token-ficha', expiresIn: 3600, user: usuario } });
    if (path === '/api/auth/me') return route.fulfill({ json: usuario });
    if (path === '/api/solicitudes') return route.fulfill({ json: solicitudes });
    if (path === '/api/contratos/') return route.fulfill({ json: [] });
    if (path === '/api/acceso/solicitudes/42/ficha-veterinaria/') {
      descargas.push({ metodo: request.method(), autenticacion: request.headers().authorization });
      if (descargas.length === 1) {
        return route.fulfill({ status: 503, json: { detail: 'Intenta nuevamente.' } });
      }
      return route.fulfill({ contentType: 'application/pdf', body: '%PDF-1.4\n%%EOF' });
    }
    return route.fulfill({ json: { items: [], total: 0 } });
  });

  await page.goto('/acceso');
  await page.getByLabel('Correo electrónico', { exact: true }).fill(usuario.correo);
  await page.getByLabel('Contraseña', { exact: true }).fill('clave-local');
  await page.getByRole('button', { name: 'Entrar', exact: true }).click();
  await expect(page).toHaveURL(/\/adoptante\/catalogo$/);
  await page.evaluate(() => {
    window.history.pushState({}, '', '/adoptante/solicitudes');
    window.dispatchEvent(new PopStateEvent('popstate'));
  });

  const boton = page.getByRole('button', { name: 'Descargar ficha veterinaria', exact: true });
  await expect(page.getByRole('article')).toHaveCount(estados.length);
  await expect(boton).toHaveCount(1);
  const entregada = page.getByRole('article').filter({ has: page.getByRole('heading', { name: 'Mascota ENTREGADA' }) });
  await expect(entregada.getByRole('button', { name: 'Descargar ficha veterinaria', exact: true })).toBeVisible();

  await boton.click();
  await expect(page.getByRole('alert')).toHaveText('No se pudo descargar la ficha veterinaria. Intenta nuevamente.');
  const eventoDescarga = page.waitForEvent('download');
  await boton.click();
  const descarga = await eventoDescarga;
  expect(descarga.suggestedFilename()).toBe('ficha_veterinaria_solicitud_42.pdf');
  expect(await descarga.failure()).toBeNull();
  await expect(page.getByRole('alert')).toHaveCount(0);
  expect(descargas).toEqual(Array(2).fill({ metodo: 'GET', autenticacion: 'Bearer token-ficha' }));
});
