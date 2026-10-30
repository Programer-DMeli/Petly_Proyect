import { test, expect } from '@playwright/test';
import fs from 'node:fs';
import crypto from 'node:crypto';

const source = process.env.PETLY_CATALOGO_FIXTURE;
const fixture = source && fs.existsSync(source) ? JSON.parse(fs.readFileSync(source, 'utf8')) : null;
test.skip(!fixture, 'Requiere la evidencia privada generada por CatalogoRealJourneyTest en Android.');

test('React muestra la misma foto y porcentajes que el recorrido Android real', async ({ page }) => {
  await page.goto('/acceso');
  await page.getByLabel('Correo', { exact: true }).fill(fixture.correo);
  await page.getByLabel('Contraseña', { exact: true }).fill(fixture.password);
  await page.getByRole('button', { name: 'Entrar', exact: true }).click();
  await expect(page).toHaveURL(/adoptante/);
  const navigate = async path => {
    await page.evaluate(path => {
      history.pushState({}, '', path);
      window.dispatchEvent(new PopStateEvent('popstate'));
    }, path);
    await expect(page).toHaveURL(new RegExp(path));
  };
  const findCard = async score => {
    await page.getByLabel('Buscar mascota o albergue').fill(fixture.nombre);
    await page.getByRole('button', { name: 'Buscar', exact: true }).click();
    const card = page.locator('article.pet-card').filter({ has: page.getByRole('heading', { name: fixture.nombre, exact: true }) });
    await expect(card.getByText(`${score}% compatible`, { exact: true })).toBeVisible();
    return card;
  };
  const card = await findCard(fixture.despues);
  const photo = card.getByRole('img', { name: fixture.nombre, exact: true });
  await expect(photo).toBeVisible();
  await expect.poll(() => photo.evaluate(img => img.complete && img.naturalWidth > 0)).toBe(true);
  const url = await photo.getAttribute('src');
  expect(new URL(url).pathname).toBe(new URL(fixture.fotoUrlAndroid).pathname);
  const response = await page.request.get(url);
  expect(response.ok()).toBe(true);
  const hash = crypto.createHash('sha256').update(await response.body()).digest('hex');
  expect(hash).toBe(fixture.fotoSha256);
  await page.screenshot({ path: '../docs/evidencias/react-catalogo-paridad-android.png', fullPage: true });

  // Changes go through the real profile form; no mocked routes or API responses.
  for (const [home, hours, score] of [['Casa', '3', fixture.antes], ['Departamento', '10', fixture.despues]]) {
    await navigate('/adoptante/cuestionario');
    await page.getByLabel('Vivienda', { exact: true }).fill(home);
    await page.getByLabel('Horas fuera de casa por día', { exact: true }).fill(hours);
    await page.getByRole('button', { name: 'Finalizar cuestionario', exact: true }).click();
    await expect(page.getByText('Cuestionario finalizado.', { exact: true })).toBeVisible();
    await navigate('/adoptante/catalogo');
    await findCard(score);
  }
  fs.writeFileSync('../docs/evidencias/catalogo-paridad-real.json', JSON.stringify({
    fecha: new Date().toISOString(), mascotaId: fixture.mascotaId, nombre: fixture.nombre,
    antes: fixture.antes, despues: fixture.despues, fotoSha256: hash,
    fotografiaIdentica: true, androidFormularioReal: true, reactFormularioReal: true,
    servicios: 'Spring, Django y MariaDB locales; sin respuestas simuladas',
  }, null, 2) + '\n');
});
