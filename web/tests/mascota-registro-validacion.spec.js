import { test, expect } from '@playwright/test';

test('muestra el error de Django, conserva la mascota y permite corregir la foto', async ({ page }) => {
  let attempts = 0;
  await page.route('**/api/**', route => {
    const path = new URL(route.request().url()).pathname;
    if (path === '/api/auth/login') return route.fulfill({ json: { token: 'prueba', expiresIn: 3600 } });
    if (path === '/api/auth/me') return route.fulfill({ json: { id: '8', correo: 'albergue@example.test', rol: 'ALBERGUE' } });
    if (path === '/api/mascotas/' && route.request().method() === 'POST') {
      attempts++;
      return attempts === 1
        ? route.fulfill({ status: 400, json: { foto_principal: ['Usa una fotografía JPEG o PNG válida.'] } })
        : route.fulfill({ status: 201, json: { id: 15, nombre: 'Lazy' } });
    }
    return route.fulfill({ json: { results: [], count: 0, next: null } });
  });
  await page.goto('/acceso');
  await page.getByLabel('Correo', { exact: true }).fill('albergue@example.test');
  await page.getByLabel('Contraseña', { exact: true }).fill('prueba-local');
  await page.getByRole('button', { name: 'Entrar', exact: true }).click();
  await expect(page).toHaveURL(/\/albergue$/);
  await page.evaluate(() => {
    history.pushState({}, '', '/albergue/mascotas');
    dispatchEvent(new PopStateEvent('popstate'));
  });
  await page.getByLabel('Nombre de la mascota').fill('Lazy');
  await page.getByLabel('Sexo', { exact: true }).selectOption('HEMBRA');
  await page.getByLabel('Edad: años', { exact: true }).fill('1');
  await page.getByLabel('Edad: meses adicionales').fill('2');
  const photo = page.getByLabel('Fotografía JPEG/PNG, máximo 5 MB');
  await photo.setInputFiles({ name: 'lazy.png', mimeType: 'image/png', buffer: Buffer.from('foto-invalida') });
  await page.getByRole('button', { name: 'Registrar mascota', exact: true }).click();
  await expect(page.getByRole('alert')).toContainText('Fotografía: Usa una fotografía JPEG o PNG válida.');
  await expect(page.getByLabel('Nombre de la mascota')).toHaveValue('Lazy');
  await expect(page.getByLabel('Edad: años', { exact: true })).toHaveValue('1');
  await expect(page.getByLabel('Edad: meses adicionales')).toHaveValue('2');
  await photo.setInputFiles({ name: 'lazy.png', mimeType: 'image/png', buffer: Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jWZkAAAAASUVORK5CYII=', 'base64') });
  await page.getByRole('button', { name: 'Registrar mascota', exact: true }).click();
  await expect(page.getByText('Mascota guardada.', { exact: true })).toBeVisible();
  expect(attempts).toBe(2);
});
