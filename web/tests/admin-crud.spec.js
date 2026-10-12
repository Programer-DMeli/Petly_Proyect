import { test, expect } from '@playwright/test';
import fs from 'node:fs';
const demo = JSON.parse(
  fs.readFileSync(process.env['PETLY_DEMO_FILE'] ?? '/tmp/petly-demo-cuentas.json', 'utf8')
);
const owner = JSON.parse(
  fs.readFileSync(process.env['PETLY_CRUD_OWNER_FILE'] ?? '/tmp/petly-crud-owner.json', 'utf8')
);
test('ADMIN registra, consulta, edita, desactiva y elimina albergue conservando cuenta Spring', async ({
  page,
}) => {
  await page.goto('/acceso');
  await page.getByLabel('Correo', { exact: true }).fill(demo.ADMIN.correo);
  await page.getByLabel('Contraseña', { exact: true }).fill(demo.ADMIN.password);
  let response = page.waitForResponse(r => r.url().includes('/api/auth/login'));
  await page.getByRole('button', { name: 'Entrar', exact: true }).click();
  let login = await response;
  if (login.status() === 429) {
    await page.waitForTimeout(61000);
    response = page.waitForResponse(r => r.url().includes('/api/auth/login'));
    await page.getByRole('button', { name: 'Entrar', exact: true }).click();
    login = await response;
  }
  expect(login.status()).toBe(200);
  const { token } = await login.json();
  const suffix = Date.now().toString();
  const name = 'Refugio ADMIN ' + suffix;
  await page.getByRole('button', { name: '+ Registrar albergue', exact: true }).click();
  await page
    .getByLabel('Cuenta propietaria ALBERGUE', { exact: true })
    .selectOption(String(owner.id));
  await page.getByLabel('Nombre del albergue', { exact: true }).fill(name);
  await page.getByLabel('Ubicación / dirección', { exact: true }).fill('Arequipa, Perú');
  await page.getByLabel('Capacidad máxima', { exact: true }).fill('40');
  await page
    .getByLabel('Nombre del responsable', { exact: true })
    .fill('Responsable de demostración');
  await page
    .getByLabel('Descripción institucional')
    .fill('Institución registrada desde la administración de Petly.');
  await page.getByRole('button', { name: 'Crear albergue', exact: true }).click();
  await expect(page.getByText('Albergue registrado y vinculado a su propietario.')).toBeVisible();
  await page.getByLabel('Buscar albergue o responsable', { exact: true }).fill(name);
  await page.getByRole('button', { name: 'Buscar albergues', exact: true }).click();
  const row = page
    .locator('article.shelter-card')
    .filter({ has: page.getByRole('heading', { name, exact: true }) });
  await row.getByRole('button', { name: 'Ver albergue', exact: true }).click();
  await expect(
    page.getByRole('dialog', { name: 'Detalle del albergue' }).getByText('Arequipa, Perú')
  ).toBeVisible();
  await page.getByRole('button', { name: 'Cerrar detalle', exact: true }).click();
  await row.getByRole('button', { name: 'Editar albergue', exact: true }).click();
  await page.getByLabel('Capacidad máxima', { exact: true }).fill('45');
  await page.getByLabel('Albergue activo y visible en el catálogo').uncheck();
  await page.getByRole('button', { name: 'Guardar albergue', exact: true }).click();
  await expect(row.getByText('Inactivo', { exact: true })).toBeVisible();
  await expect(row.getByText('45 mascotas', { exact: true })).toBeVisible();
  await row.getByRole('button', { name: 'Eliminar albergue', exact: true }).click();
  await page.getByRole('button', { name: 'Confirmar eliminación', exact: true }).click();
  await expect(
    page.getByText('Albergue eliminado. La cuenta del propietario se conserva.')
  ).toBeVisible();
  await expect(row).toHaveCount(0);
  const authority = await page.request.get(
    'http://localhost:8080/api/admin/cuentas-albergue/' + owner.id,
    { headers: { Authorization: 'Bearer ' + token } }
  );
  expect(authority.status()).toBe(200);
});
