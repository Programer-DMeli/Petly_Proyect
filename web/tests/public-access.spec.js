import { expect, test } from '@playwright/test';

const origin = 'http://localhost:5173';
const apiHeaders = {
  'Access-Control-Allow-Origin': origin,
  'Access-Control-Allow-Credentials': 'true',
  'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
  'Access-Control-Allow-Headers': 'authorization, content-type, x-petly-client',
};

async function mockPublicSession(page, handleLogin) {
  await page.route('**/api/**', async route => {
    const request = route.request();
    const path = new URL(request.url()).pathname;
    const headers = apiHeaders;
    if (request.method() === 'OPTIONS') return route.fulfill({ status: 204, headers });
    if (path === '/api/auth/refresh') return route.fulfill({ status: 401, headers, json: {} });
    if (path === '/api/auth/login' && handleLogin) {
      return route.fulfill({ headers, ...(await handleLogin(request)) });
    }
    return route.fulfill({ headers, json: [] });
  });
}

async function noHorizontalOverflow(page) {
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(
    true
  );
}

test('la portada organiza bienvenida y acceso, y Sobre Petly conserva su navegación', async ({
  page,
}) => {
  await mockPublicSession(page);
  await page.setViewportSize({ width: 1440, height: 900 });
  await page.goto('/');
  const welcome = page.getByRole('heading', { name: 'Un abrazo que cambia vidas' });
  const access = page.getByRole('region', { name: 'Inicia sesión' });
  const lane = page.locator('.landing__kitten-lane');
  await expect(welcome).toBeVisible();
  await expect(access).toBeVisible();
  const desktopWelcome = await welcome.boundingBox();
  const desktopAccess = await access.boundingBox();
  expect(desktopWelcome.x + desktopWelcome.width).toBeLessThan(desktopAccess.x);
  await noHorizontalOverflow(page);

  await page.setViewportSize({ width: 390, height: 844 });
  const mobileWelcome = await welcome.boundingBox();
  const mobileAccess = await access.boundingBox();
  expect(mobileWelcome.y + mobileWelcome.height).toBeLessThan(mobileAccess.y);
  expect((await lane.boundingBox()).y).toBeGreaterThanOrEqual(mobileAccess.y + mobileAccess.height);
  await noHorizontalOverflow(page);

  const nav = page.getByRole('navigation', { name: 'Navegación principal' });
  await nav.getByRole('link', { name: 'Sobre Petly' }).click();
  await expect(page).toHaveURL('/sobre-petly');
  await expect(page.getByRole('heading', { name: 'Un hogar cambia una vida' })).toBeVisible();
  await expect(
    page.getByRole('img', { name: 'Un perro y un gato buscando un hogar' })
  ).toBeVisible();
  for (const name of ['¿Qué es Petly?', 'Beneficios', 'Misión', 'Visión']) {
    await expect(page.getByRole('heading', { name, exact: true })).toBeVisible();
  }
  const panel = page.getByRole('region', { name: 'Un hogar cambia una vida' });
  for (const name of ['Iniciar sesión', 'Crear cuenta de adoptante', 'Historias de adopción']) {
    await expect(panel.getByRole('link', { name, exact: true })).toHaveCount(0);
    await expect(panel.getByRole('button', { name, exact: true })).toHaveCount(0);
  }
  await expect(page.getByRole('contentinfo')).toContainText(
    'Cada encuentro merece una nueva oportunidad.'
  );
  await nav.getByRole('link', { name: 'Cómo funciona' }).click();
  await expect(page).toHaveURL('/sobre-petly#como-funciona');
  await expect(page.getByRole('heading', { name: 'Cómo funciona' })).toBeInViewport();
  await noHorizontalOverflow(page);
  await page.getByRole('link', { name: 'Encuentra a tu compañero' }).click();
  await expect(page).toHaveURL('/#iniciar-sesion');
  await expect(access.getByRole('heading')).toBeInViewport();
});

test('el formulario integrado conserva validación, carga, bloqueo y navegación', async ({
  page,
}) => {
  const calls = [];
  let finishLogin;
  await mockPublicSession(page, async request => {
    const credentials = request.postDataJSON();
    calls.push(credentials);
    if (credentials.correo === 'otro@example.test') {
      return {
        status: 200,
        json: {
          token: 'fixture-access-token',
          expiresIn: 3600,
          user: { id: 4, rol: 'ADMIN', correo: credentials.correo },
        },
      };
    }
    await new Promise(resolve => {
      finishLogin = resolve;
    });
    return {
      status: 423,
      json: { error: 'Cuenta bloqueada temporalmente', reintentarEnSegundos: 60 },
    };
  });
  await page.goto('/');
  const email = page.getByLabel('Correo electrónico', { exact: true });
  const password = page.getByLabel('Contraseña', { exact: true });
  const submit = page.getByRole('button', { name: 'Entrar', exact: true });
  await expect(page.getByRole('link', { name: 'Recuperar contraseña' })).toHaveAttribute(
    'href',
    '/recuperacion'
  );
  await expect(page.getByRole('link', { name: 'Crear cuenta', exact: true })).toHaveAttribute(
    'href',
    '/registro'
  );
  await email.fill('correo-invalido');
  await password.fill('clave de prueba');
  await page.getByRole('button', { name: 'Mostrar contraseña' }).click();
  await expect(password).toHaveAttribute('type', 'text');
  await page.getByRole('button', { name: 'Ocultar contraseña' }).click();
  await expect(password).toHaveAttribute('type', 'password');
  await submit.click();
  await expect(page.getByRole('alert')).toContainText('Ingresa un correo válido');
  expect(calls).toHaveLength(0);

  await email.fill('TEST@EXAMPLE.TEST');
  await submit.click();
  try {
    await expect(page.getByRole('button', { name: 'Procesando…' })).toBeDisabled();
    await expect
      .poll(() => calls)
      .toEqual([{ correo: 'test@example.test', password: 'clave de prueba' }]);
  } finally {
    finishLogin?.();
  }
  await expect(page.getByRole('status')).toContainText('Cuenta bloqueada temporalmente');
  await expect(submit).toBeDisabled();
  await email.fill('otro@example.test');
  await expect(submit).toBeEnabled();
  await submit.click();
  await expect(page).toHaveURL('/administracion/albergues');
});

for (const viewport of [
  { width: 1440, height: 900 },
  { width: 390, height: 844 },
]) {
  test(`el gato y las plantas completan sus ciclos en ${viewport.width} px`, async ({ page }) => {
    await mockPublicSession(page);
    await page.setViewportSize(viewport);
    const start = new Date('2026-11-26T13:00:00Z');
    await page.clock.install({ time: start });
    await page.clock.pauseAt(new Date(start.getTime() + 1000));
    await page.goto('/');
    const kitten = page.locator('.landing__kitten');
    const foliage = page.locator('.landing__foliage');
    const pots = page.locator('.landing__pot');
    const lane = page.locator('.landing__kitten-lane');
    await expect(kitten).toBeAttached();
    const ground = await lane.boundingBox();
    const form = await page.getByRole('region', { name: 'Inicia sesión' }).boundingBox();
    expect(ground.y).toBeGreaterThanOrEqual(form.y + form.height);
    const firstPots = await pots.evaluateAll(items =>
      items.map(item => getComputedStyle(item).transform)
    );
    await page.clock.runFor(35);
    const first = await kitten.boundingBox();
    await page.clock.runFor(1600);
    const walking = await kitten.boundingBox();
    expect(walking.x).toBeGreaterThan(first.x + 20);
    const leafTransforms = await foliage.evaluateAll(items =>
      items.map(item => getComputedStyle(item).transform)
    );
    expect(leafTransforms.every(value => value !== 'none')).toBe(true);
    expect(leafTransforms[0]).not.toBe(leafTransforms[1]);
    await page.clock.runFor(4500);
    const paused = await kitten.boundingBox();
    await expect(kitten).toHaveCSS('background-position', '0% 100%');
    expect(paused.x + paused.width).toBeLessThanOrEqual(ground.x + ground.width + 1);
    expect(paused.y + paused.height).toBeCloseTo(ground.y + ground.height, 0);
    await page.clock.runFor(550);
    await expect(kitten).toHaveCSS('background-position', '66.6667% 100%');
    expect((await kitten.boundingBox()).x).toBeCloseTo(paused.x, 0);
    await page.clock.runFor(900);
    const returning = await kitten.boundingBox();
    expect(returning.x).toBeLessThan(paused.x);
    await page.clock.runFor(6150);
    expect((await kitten.boundingBox()).x).toBeLessThan(first.x + 15);
    expect(
      await pots.evaluateAll(items => items.map(item => getComputedStyle(item).transform))
    ).toEqual(firstPots);
    await noHorizontalOverflow(page);

    await page.emulateMedia({ reducedMotion: 'reduce' });
    const staticX = ground.x + (ground.width - first.width) * 0.3;
    await expect
      .poll(async () => {
        await page.clock.runFor(40);
        return (await kitten.boundingBox()).x;
      })
      .toBeCloseTo(staticX, 0);
    const still = await kitten.boundingBox();
    await page.clock.runFor(15000);
    expect((await kitten.boundingBox()).x).toBe(still.x);
    await expect(kitten).toHaveCSS('background-position', '0% 100%');
    expect(
      await foliage.evaluateAll(items => items.map(item => getComputedStyle(item).transform))
    ).toEqual(['none', 'none']);
  });
}
