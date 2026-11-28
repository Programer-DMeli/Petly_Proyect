import { expect, test } from '@playwright/test';

const origin = 'http://localhost:5173';
const apiHeaders = {
  'Access-Control-Allow-Origin': origin,
  'Access-Control-Allow-Credentials': 'true',
  'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
  'Access-Control-Allow-Headers': 'authorization, content-type, x-petly-client',
};

async function mockPublicSession(page, handleLogin, handleFeatured) {
  await page.route('**/api/**', async route => {
    const request = route.request();
    const path = new URL(request.url()).pathname;
    const headers = apiHeaders;
    if (request.method() === 'OPTIONS') return route.fulfill({ status: 204, headers });
    if (path === '/api/auth/refresh') return route.fulfill({ status: 401, headers, json: {} });
    if (path === '/api/catalogo/destacadas') {
      return route.fulfill({
        headers,
        ...(handleFeatured ? await handleFeatured() : { json: [] }),
      });
    }
    if (path === '/api/auth/login' && handleLogin) {
      return route.fulfill({ headers, ...(await handleLogin(request)) });
    }
    if (path === '/api/catalogo') {
      return route.fulfill({ headers, json: { items: [], total: 0 } });
    }
    if (path === '/api/catalogo/opciones') {
      return route.fulfill({ headers, json: { especies: [], tamanos: [], temperamentos: [] } });
    }
    const id = path.match(/^\/api\/catalogo\/(\d+)$/)?.[1];
    if (id) {
      const pet = featured.find(item => String(item.id) === id);
      if (!pet) return route.fulfill({ status: 404, headers, json: {} });
      return route.fulfill({
        headers,
        json: {
          ...pet,
          estado: 'Disponible',
          especie: 'Gato',
          tamano: 'Mediano',
          temperamento: 'Sociable',
          energia: 'Media',
          albergue: 'Refugio de prueba',
          ubicacion: 'Lima',
        },
      });
    }
    if (path === '/api/albergues/') {
      return route.fulfill({
        headers,
        json: { results: [], count: 0, next: null, previous: null },
      });
    }
    return route.fulfill({ headers, json: [] });
  });
}

async function noHorizontalOverflow(page) {
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(
    true
  );
}

test('el acceso conserva su composición en escritorio y móvil', async ({ page }) => {
  await mockPublicSession(page);
  await page.setViewportSize({ width: 1440, height: 900 });
  await page.goto('/acceso');
  const welcome = page.getByRole('heading', { name: 'Un abrazo que cambia vidas' });
  const access = page.getByRole('region', { name: 'Inicia sesión' });
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
  const pets = await page.locator('.landing__pets').boundingBox();
  expect(pets.y).toBeGreaterThanOrEqual(mobileAccess.y + mobileAccess.height);
  await noHorizontalOverflow(page);
});

for (const viewport of [
  { width: 1440, height: 900 },
  { width: 390, height: 844 },
  { width: 320, height: 720 },
]) {
  test(`ambos laterales conectan Bienvenida y el acceso en ${viewport.width}px`, async ({ page }) => {
    await mockPublicSession(page, undefined, () => ({ json: featured }));
    await page.setViewportSize(viewport);
    await page.goto('/');
    const switcher = page.getByRole('navigation', { name: 'Cambiar pantalla' });
    const forward = switcher.getByRole('link', { name: 'Ir a iniciar sesión' });
    const back = switcher.getByRole('link', { name: 'Volver a Bienvenida' });
    const header = page.getByRole('banner');

    async function expectCenteredPair(links, content) {
      await expect(links).toHaveCount(2);
      const [left, right] = await Promise.all(
        [links.first(), links.last()].map(async link => {
          await expect(link).toBeInViewport();
          const box = await link.boundingBox();
          expect(box.y + box.height / 2).toBeCloseTo(viewport.height / 2, 0);
          expect(box.width).toBeGreaterThanOrEqual(44);
          expect(box.height).toBeGreaterThanOrEqual(44);
          return box;
        })
      );
      expect(left.x).toBeCloseTo(viewport.width - right.x - right.width, 0);
      await expect(content).toBeVisible();
      const box = await content.boundingBox();
      expect(left.x + left.width).toBeLessThan(box.x);
      expect(right.x).toBeGreaterThan(box.x + box.width);
      await noHorizontalOverflow(page);
    }

    await expect(header).toHaveCount(0);
    await expectCenteredPair(forward, page.getByRole('article'));

    await page.keyboard.press('Tab');
    await expect(page.getByRole('link', { name: 'Saltar al contenido principal' })).toBeFocused();
    await page.keyboard.press('Tab');
    await expect(forward.first()).toBeFocused();
    await page.keyboard.press('Enter');
    await expect(page).toHaveURL('/acceso');
    const access = page.getByRole('region', { name: 'Inicia sesión' });
    await expect(header).toHaveCount(0);
    await expectCenteredPair(back, access);

    await back.last().click();
    await expect(page).toHaveURL('/');
    await forward.last().click();
    await expect(page).toHaveURL('/acceso');
    await back.first().click();
    await expect(page).toHaveURL('/');
    await expect(page.getByRole('heading', { name: 'Tu próximo mejor amigo te espera' })).toBeVisible();
    await page.goBack();
    await expect(page).toHaveURL('/acceso');
    await expect(access).toBeVisible();

    await access.getByRole('link', { name: 'Recuperar contraseña' }).click();
    await expect(page).toHaveURL('/recuperacion');
    await page.getByRole('link', { name: 'Volver al acceso' }).click();
    await expect(access).toBeVisible();
    await access.getByRole('link', { name: 'Crear cuenta', exact: true }).click();
    await expect(page).toHaveURL('/registro');
    await expect(page.getByRole('heading', { name: 'Crear cuenta de adoptante' })).toBeVisible();
  });
}

test('las rutas informativas retiradas redirigen a Bienvenida', async ({ page }) => {
  await mockPublicSession(page);
  for (const path of ['/conoce-petly', '/sobre-petly']) {
    await page.goto(path);
    await expect(page).toHaveURL('/');
    await expect(page.getByRole('heading', { name: 'Tu próximo mejor amigo te espera' })).toBeVisible();
  }
  const artwork = await page.request.get('/images/mascotas-portada.webp');
  expect(artwork.ok()).toBe(true);
  expect(artwork.headers()['content-type']).toContain('image/webp');
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
  await page.goto('/acceso');
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

for (const viewport of [{ width: 1440, height: 900 }]) {
  test(`el gato y las plantas completan sus ciclos en ${viewport.width} px`, async ({ page }) => {
    await mockPublicSession(page);
    await page.setViewportSize(viewport);
    const start = new Date('2026-11-26T13:00:00Z');
    await page.clock.install({ time: start });
    await page.clock.pauseAt(new Date(start.getTime() + 1000));
    await page.goto('/acceso');
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

const featured = ['Luna', 'Milo', 'Nube', 'Sol', 'Roco'].map((nombre, index) => ({
  id: 40 + index,
  nombre,
  edadMeses: index === 0 ? 24 : 8,
  descripcion: `Descripción registrada de ${nombre}`,
  fotografiaUrl: index === 0 ? origin + '/images/acceso/perro.webp' : null,
}));

async function adopterLogin(request) {
  return {
    status: 200,
    json: {
      token: 'fixture-adopter-token',
      expiresIn: 3600,
      user: { id: 5, rol: 'ADOPTANTE', correo: request.postDataJSON().correo },
    },
  };
}

async function signIn(page) {
  await page.getByLabel('Correo electrónico', { exact: true }).fill('adoptante@example.test');
  await page.getByLabel('Contraseña', { exact: true }).fill('clave de prueba');
  await page.getByRole('button', { name: 'Entrar', exact: true }).click();
}

test('la portada muestra cinco tarjetas y conserva la mascota elegida hasta el acceso', async ({
  page,
}) => {
  await mockPublicSession(page, adopterLogin, () => ({ json: featured }));
  await page.setViewportSize({ width: 1440, height: 900 });
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await page.goto('/');
  await expect(
    page.getByRole('heading', { name: 'Tu próximo mejor amigo te espera' })
  ).toBeVisible();
  await expect(page.getByRole('region', { name: 'Inicia sesión' })).toHaveCount(0);
  const carousel = page.getByRole('region', { name: 'Mascotas disponibles para adopción' });
  const slides = carousel.locator('.welcome-carousel__slide');
  await expect(slides).toHaveCount(5);
  await expect(slides.first()).toHaveCSS('transition-duration', '0s');
  await expect(carousel.getByRole('heading', { name: 'Luna · 2 años' })).toBeVisible();
  await expect(carousel.getByRole('img', { name: 'Luna' })).toHaveAttribute('src', /perro\.webp$/);
  await carousel.getByRole('button', { name: 'Mascota siguiente' }).click();
  await expect(carousel.getByRole('heading', { name: /Milo/ })).toBeVisible();
  await expect(carousel.getByRole('article').getByText('Fotografía por añadir')).toBeVisible();
  await carousel.getByRole('button', { name: 'Mascota anterior' }).click();
  await carousel.focus();
  await page.keyboard.press('ArrowLeft');
  await expect(carousel.getByRole('heading', { name: /Roco/ })).toBeVisible();
  await carousel.getByRole('button', { name: /Mostrar a Nube/ }).click();
  await expect(
    carousel.getByRole('article').getByText('Descripción registrada de Nube')
  ).toBeVisible();
  await noHorizontalOverflow(page);
  await carousel.getByRole('link', { name: 'Adoptar' }).click();
  await expect(page).toHaveURL('/acceso');
  await page.getByRole('link', { name: 'Recuperar contraseña' }).click();
  await page.getByRole('link', { name: 'Volver al acceso' }).click();
  await page.reload();
  await signIn(page);
  await expect(page).toHaveURL('/adoptante/catalogo/42');
  await expect(page.getByRole('heading', { name: 'Nube', exact: true })).toBeVisible();
});

test('la flecha de Bienvenida permite abrir el catálogo tras iniciar sesión', async ({ page }) => {
  await mockPublicSession(page, adopterLogin);
  await page.goto('/');
  await page.getByRole('link', { name: 'Ir a iniciar sesión' }).last().click();
  await expect(page).toHaveURL('/acceso');
  await signIn(page);
  await expect(page).toHaveURL('/adoptante/catalogo');
});

test.describe('portada táctil', () => {
  test.use({ hasTouch: true, isMobile: true, viewport: { width: 390, height: 844 } });
  test('deslizar cambia la mascota en pantallas táctiles', async ({
    page,
  }) => {
    await mockPublicSession(page, undefined, () => ({ json: featured }));
    await page.goto('/');
    const carousel = page.getByRole('region', { name: 'Mascotas disponibles para adopción' });
    await expect(carousel.getByRole('heading', { name: /Luna/ })).toBeVisible();
    const area = await carousel.locator('.welcome-carousel__stage').boundingBox();
    const cdp = await page.context().newCDPSession(page);
    const y = Math.round(area.y + area.height / 3);
    const x = Math.round(area.x + area.width * 0.75);
    await cdp.send('Input.dispatchTouchEvent', { type: 'touchStart', touchPoints: [{ x, y }] });
    await cdp.send('Input.dispatchTouchEvent', {
      type: 'touchMove',
      touchPoints: [{ x: x - 120, y }],
    });
    await cdp.send('Input.dispatchTouchEvent', { type: 'touchEnd', touchPoints: [] });
    await expect(carousel.getByRole('heading', { name: /Milo/ })).toBeVisible();
    await noHorizontalOverflow(page);
  });
});

test('la bienvenida muestra carga, error, reintento y catálogo vacío', async ({ page }) => {
  let release;
  const firstRequests = new Promise(resolve => {
    release = resolve;
  });
  let retrying = false;
  await mockPublicSession(page, undefined, async () => {
    if (retrying) return { json: [] };
    await firstRequests;
    return { status: 503, json: {} };
  });
  await page.goto('/');
  await expect(page.getByText('Cargando mascotas…')).toBeVisible();
  release();
  await expect(page.getByRole('alert')).toContainText('No pudimos cargar');
  retrying = true;
  await page.getByRole('button', { name: 'Reintentar' }).click();
  await expect(
    page.getByText('Por ahora no hay mascotas disponibles para adopción.')
  ).toBeVisible();
  await expect(page.getByRole('button', { name: 'Mascota siguiente' })).toHaveCount(0);
});

test('una sola mascota con foto inaccesible conserva su tarjeta y usa el marcador neutro', async ({
  page,
}) => {
  const pet = { ...featured[0], fotografiaUrl: origin + '/imagen-no-disponible.webp' };
  await mockPublicSession(page, undefined, () => ({ json: [pet] }));
  await page.route(pet.fotografiaUrl, route => route.fulfill({ status: 404 }));
  await page.goto('/');
  await expect(page.getByRole('heading', { name: 'Luna · 2 años' })).toBeVisible();
  await expect(page.getByText('Fotografía por añadir')).toBeVisible();
  await expect(page.getByRole('group', { name: 'Elegir mascota' })).toHaveCount(0);
  await expect(page.getByRole('button', { name: 'Mascota siguiente' })).toHaveCount(0);
  await expect(page.getByRole('link', { name: 'Adoptar', exact: true })).toHaveAttribute(
    'href',
    '/acceso'
  );
});
