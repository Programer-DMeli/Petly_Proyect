import { expect, test } from '@playwright/test';

test('el albergue revisa las historias recibidas desde Testimonios destacados', async ({ page }) => {
  const usuario = { id: '8', correo: 'albergue@example.test', rol: 'ALBERGUE' };
  const api = '/api/contratos/casos-exito/';
  const luna = {
    id: 21, solicitud_id: 41, mascota_nombre: 'Luna', albergue_nombre: 'Huellitas',
    historia: 'Luna se adaptó a su nuevo hogar.', creado_en: '2026-11-22T12:00:00Z',
    fotos: [{ id: 1, descarga_url: `${api}21/fotos/1/`, mime_type: 'image/png' }],
  };
  const nube = { ...luna, id: 22, solicitud_id: 42, mascota_nombre: 'Nube', fotos: [] };
  const decisiones = [];
  let fotoPrivadaConsultada = false;

  await page.route('**/api/**', async route => {
    const request = route.request();
    const path = new URL(request.url()).pathname;
    if (path === '/api/auth/refresh') return route.fulfill({ status: 401 });
    if (path === '/api/auth/login') return route.fulfill({ json: { token: 'prueba', expiresIn: 3600, user: usuario } });
    if (path === '/api/auth/me') return route.fulfill({ json: usuario });
    if (path === '/api/albergues/mi-perfil/') return route.fulfill({ json: { id: 8, nombre: 'Huellitas' } });
    if (path === `${api}pendientes/`) return route.fulfill({ json: { results: [luna, nube], next: null } });
    if (path === `${api}publicados/`) return route.fulfill({ json: { results: [], next: null } });
    if (path === luna.fotos[0].descarga_url) {
      if (!decisiones.some(([, datos]) => datos.decision === 'APROBAR')) {
        expect(request.headers().authorization).toBe('Bearer prueba');
        fotoPrivadaConsultada = true;
      }
      return route.fulfill({
        contentType: 'image/png',
        body: Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jWZkAAAAASUVORK5CYII=', 'base64'),
      });
    }
    if (path.endsWith('/revisar/')) {
      const datos = request.postDataJSON();
      decisiones.push([path, datos]);
      return route.fulfill({ json: datos.decision === 'APROBAR'
        ? { ...luna, destacado: false, publicado_en: '2026-11-23T12:00:00Z' }
        : { ...nube, estado: 'RECHAZADO', motivo_revision: datos.motivo, publicado_en: null } });
    }
    if (path === `${api}21/destacar/`) {
      expect(request.postDataJSON()).toEqual({ destacado: true });
      return route.fulfill({ json: { id: 21, destacado: true } });
    }
    return route.fulfill({ json: { results: [], next: null } });
  });

  await page.goto('/acceso');
  await page.getByLabel('Correo electrónico', { exact: true }).fill(usuario.correo);
  await page.getByLabel('Contraseña', { exact: true }).fill('clave-de-prueba');
  await page.getByRole('button', { name: 'Entrar', exact: true }).click();
  await expect(page).toHaveURL(/\/albergue$/);
  await page.getByRole('button', { name: 'Comunidad', exact: true }).click();
  await page.getByRole('link', { name: /Testimonios destacados/ }).click();

  await expect(page.getByRole('heading', { name: 'Testimonios destacados' })).toBeVisible();
  const tarjetaNube = page.getByRole('article').filter({ has: page.getByRole('heading', { name: 'Nube' }) });
  await tarjetaNube.getByRole('button', { name: 'Rechazar historia' }).click();
  await expect(page.getByText('Escribe el motivo del rechazo antes de continuar.')).toBeVisible();
  expect(decisiones).toHaveLength(0);
  await tarjetaNube.getByRole('textbox', { name: /Motivo del rechazo/ }).fill('La foto muestra datos personales.');
  await tarjetaNube.getByRole('button', { name: 'Rechazar historia' }).click();
  await expect(page.getByText('La historia de Nube fue rechazada.')).toBeVisible();

  await expect(page.getByRole('img', { name: 'Foto de la historia de Luna' })).toBeVisible();
  expect(fotoPrivadaConsultada).toBe(true);
  await page.getByRole('button', { name: 'Aprobar y publicar' }).click();
  await expect(page.getByText('No hay historias pendientes de revisión.')).toBeVisible();
  await page.getByRole('button', { name: 'Destacar en el perfil público' }).click();
  await expect(page.getByRole('button', { name: 'Quitar del perfil público' })).toBeVisible();
  expect(decisiones).toEqual([
    [`${api}22/revisar/`, { decision: 'RECHAZAR', motivo: 'La foto muestra datos personales.' }],
    [`${api}21/revisar/`, { decision: 'APROBAR', motivo: '' }],
  ]);
});
