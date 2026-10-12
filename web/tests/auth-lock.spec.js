import { test, expect } from '@playwright/test';
import crypto from 'node:crypto';
test('Correo inválido no se envía; dos claves incorrectas bloquean y persiste al recargar', async ({
  page,
  request,
}) => {
  const email = 'lock-web-' + crypto.randomBytes(6).toString('hex') + '@example.test';
  const password = crypto.randomBytes(16).toString('hex');
  const created = await request.post('http://localhost:8080/api/auth/registro', {
    data: { correo: email, password },
  });
  expect(created.status()).toBe(201);
  await page.goto('/acceso');
  let attempts = 0;
  page.on('request', r => {
    if (r.url().endsWith('/api/auth/login') && r.method() === 'POST') attempts++;
  });
  await page.getByLabel('Correo', { exact: true }).fill('nombre@gmail');
  await page.getByLabel('Contraseña', { exact: true }).fill('x');
  await page.getByRole('button', { name: 'Entrar', exact: true }).click();
  await expect(page.getByRole('alert')).toContainText('correo válido');
  expect(attempts).toBe(0);
  await page.getByLabel('Correo', { exact: true }).fill(email);
  const first = page.waitForResponse(r => r.url().endsWith('/api/auth/login'));
  await page.getByRole('button', { name: 'Entrar', exact: true }).click();
  expect((await first).status()).toBe(401);
  await expect(page.getByRole('alert')).toContainText('inválid');
  const second = page.waitForResponse(r => r.url().endsWith('/api/auth/login'));
  await page.getByRole('button', { name: 'Entrar', exact: true }).click();
  const locked = await second;
  expect(locked.status()).toBe(423);
  const seconds = (await locked.json()).reintentarEnSegundos;
  expect(seconds).toBeGreaterThan(50);
  expect(seconds).toBeLessThanOrEqual(60);
  await expect(page.getByRole('status')).toContainText('Cuenta bloqueada');
  await expect(page.getByRole('button', { name: 'Entrar', exact: true })).toBeDisabled();
  expect(attempts).toBe(2);
  await page.getByLabel('Contraseña', { exact: true }).fill(password);
  await expect(page.getByRole('button', { name: 'Entrar', exact: true })).toBeDisabled();
  // Se elimina el estado local; Spring conserva y devuelve el bloqueo con su tiempo restante.
  await page.reload();
  await page.getByLabel('Correo', { exact: true }).fill(email);
  await page.getByLabel('Contraseña', { exact: true }).fill(password);
  const again = page.waitForResponse(r => r.url().endsWith('/api/auth/login'));
  await page.getByRole('button', { name: 'Entrar', exact: true }).click();
  expect((await again).status()).toBe(423);
  await expect(page.getByRole('button', { name: 'Entrar', exact: true })).toBeDisabled();
  await expect(page.getByRole('button', { name: 'Entrar', exact: true })).toBeEnabled({ timeout: 65000 });
  const unlocked = page.waitForResponse(r => r.url().endsWith('/api/auth/login'));
  await page.getByRole('button', { name: 'Entrar', exact: true }).click();
  expect((await unlocked).status()).toBe(200);
});
test('Cuota real 429 muestra espera y evita nuevas peticiones desde React', async ({
  page,
  request,
}) => {
  // Este test usa requests reales, sin simular respuestas; esperar una ventana limpia.
  await page.waitForTimeout(61000);
  const email = 'missing-rate-' + crypto.randomBytes(6).toString('hex') + '@example.test';
  let limited = false;
  for (let i = 0; i < 11; i++) {
    const response = await request.post('http://localhost:8080/api/auth/login', {
      data: { correo: email, password: 'x' },
    });
    if (response.status() === 429) {
      limited = true;
      break;
    }
    expect(response.status()).toBe(401);
  }
  expect(limited).toBe(true);
  await page.goto('/acceso');
  await page.getByLabel('Correo', { exact: true }).fill(email);
  await page.getByLabel('Contraseña', { exact: true }).fill('x');
  const attempt = page.waitForResponse(r => r.url().endsWith('/api/auth/login'));
  await page.getByRole('button', { name: 'Entrar', exact: true }).click();
  expect((await attempt).status()).toBe(429);
  await expect(page.getByRole('status')).toContainText('Límite de peticiones');
  await expect(page.getByRole('button', { name: 'Entrar', exact: true })).toBeDisabled();
});
