import { test, expect } from '@playwright/test';
import fs from 'node:fs';
const source = process.env.PETLY_UI_ACCOUNTS;
const accounts = source && fs.existsSync(source) ? JSON.parse(fs.readFileSync(source, 'utf8')) : null;
test.skip(!accounts, 'Definir PETLY_UI_ACCOUNTS para revisar las APIs locales existentes sin modificar datos.');
test('paneles con APIs reales: solo lectura en tres tamaños', async ({ browser }) => {
  const dimensions = [{ name: 'escritorio', width: 1440, height: 1000 }, { name: 'tableta', width: 834, height: 1112 }, { name: 'movil', width: 390, height: 844 }];
  for (const role of ['ADOPTANTE', 'ALBERGUE', 'ADMIN']) {
    const context = await browser.newContext({ baseURL: 'http://localhost:5173', viewport: dimensions[0] });
    try {
      const page = await context.newPage(); const blocked = [], errors = [];
      page.on('pageerror', e => errors.push(e.message));
      await page.route('**/api/**', async route => {
        const r = route.request();
        if (['GET', 'OPTIONS'].includes(r.method()) || (r.method() === 'POST' && new URL(r.url()).pathname === '/api/auth/login')) return route.continue();
        blocked.push(`${r.method()} ${new URL(r.url()).pathname}`); return route.abort();
      });
      await page.goto('/acceso'); await page.getByLabel('Correo', { exact: true }).fill(accounts[role].correo); await page.getByLabel('Contraseña', { exact: true }).fill(accounts[role].password);
      await page.getByRole('button', { name: 'Entrar', exact: true }).click();
      await page.waitForURL(url => !url.pathname.includes('/acceso'));
      await page.locator('h1').first().waitFor();
      if (role === 'ADOPTANTE') await expect(page.locator('.catalog-results')).toHaveAttribute('aria-busy', 'false');
      if (role === 'ALBERGUE') await page.getByRole('button', { name: 'Actualizar albergue', exact: true }).waitFor();
      if (role === 'ADMIN') await page.getByRole('button', { name: '+ Registrar albergue', exact: true }).waitFor({ state: 'visible' });
      for (const size of dimensions) {
        await page.setViewportSize(size);
        await page.evaluate(() => document.fonts.ready);
        expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
        await page.screenshot({ path: `output/redisenio/real-${role.toLowerCase()}-${size.name}.png`, fullPage: true });
      }
      expect(blocked).toEqual([]); expect(errors).toEqual([]);
    } finally { await context.close(); }
  }
});
