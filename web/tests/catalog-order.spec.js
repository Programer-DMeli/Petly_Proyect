import { test, expect } from '@playwright/test';

test.beforeEach(async ({ page }) => {
  await page.route('**/api/**', route => route.fulfill({ status: 404, json: { detail: 'Sin fixture' } }));
  await page.route('**/api/auth/refresh', route => route.fulfill({ status: 401 }));
});

const pets = [
  {
    id: 4,
    nombre: 'Mejor puntaje',
    especie: 'Perro',
    tamano: 'Mediano',
    edadMeses: 24,
    temperamento: 'Sociable',
    energia: 'Media',
    descripcion: '',
    albergue: 'Hogar Petly',
    albergueId: 1,
    ubicacion: 'Lima',
    estado: 'Adoptado',
    fotografiaUrl: 'http://localhost:5173/images/acceso/perro.webp',
    compatibilidad: { porcentaje: 100, puntaje: 100, cobertura: 100, datosInsuficientes: false, mensaje: '' },
  },
  {
    id: 7,
    nombre: 'Segundo puntaje',
    especie: 'Perro',
    tamano: 'Pequeño',
    edadMeses: 12,
    temperamento: 'Tranquilo',
    energia: 'Baja',
    descripcion: 'Un compañero tranquilo.',
    albergue: 'Hogar Petly',
    albergueId: 1,
    ubicacion: 'Lima',
    estado: 'Disponible',
    fotografiaUrl: 'http://localhost:5173/images/acceso/perro.webp',
    fotografiasAdicionales: ['http://localhost:5173/images/mascotas-portada.webp'],
    sexo: 'Macho',
    saludConfirmada: true,
    compatibilidad: { porcentaje: 72, puntaje: 72, cobertura: 100, datosInsuficientes: false, mensaje: '' },
  },
  {
    id: 9,
    nombre: 'Datos incompletos',
    especie: 'Gato',
    tamano: 'Por evaluar',
    edadMeses: null,
    temperamento: 'Por evaluar',
    energia: 'Por evaluar',
    descripcion: '',
    albergue: 'Hogar Petly',
    albergueId: 1,
    ubicacion: 'Lima',
    estado: 'Disponible',
    fotografiaUrl: null,
    compatibilidad: { porcentaje: null, puntaje: null, cobertura: 0, datosInsuficientes: true, mensaje: 'Información insuficiente' },
  },
];

test('el catálogo React conserva el orden recibido y marca compatibilidad incompleta', async ({ page }) => {
  await page.route('http://localhost:8080/api/perfil/favoritos', route => route.fulfill({ json: [] }));
  await page.route('http://localhost:8080/api/auth/login', route =>
    route.fulfill({ json: { token: 'token-de-prueba', expiresIn: 3600, user: { id: '7', correo: 'angie@example.com', rol: 'ADOPTANTE' } } })
  );
  await page.route('http://localhost:8080/api/auth/me', route =>
    route.fulfill({ json: { id: '7', correo: 'angie@example.com', rol: 'ADOPTANTE' } })
  );
  await page.route('http://localhost:8080/api/catalogo/opciones', route =>
    route.fulfill({ json: { especies: ['Perro', 'Gato'], tamanos: ['Mediano'], temperamentos: ['Sociable'] } })
  );
  await page.route('http://localhost:8080/api/catalogo?**', route => {
    const species = new URL(route.request().url()).searchParams.get('especie');
    const items = pets.filter(pet => pet.especie === species);
    return route.fulfill({ json: { items, total: items.length } });
  });

  await page.goto('/acceso');
  await page.getByLabel('Correo electrónico', { exact: true }).fill('angie@example.com');
  await page.getByLabel('Contraseña', { exact: true }).fill('password-de-prueba');
  await page.getByRole('button', { name: 'Entrar', exact: true }).click();

  const dogs = page.getByRole('region', { name: 'Resultados de perros' });
  const cats = page.getByRole('region', { name: 'Resultados de gatos' });
  const dogCards = dogs.locator('article.pet');
  await page.getByRole('link', { name: 'Ver perros' }).click();
  await dogs.getByRole('link', { name: 'Ver todos' }).click();
  await expect(dogCards).toHaveCount(2);
  await expect(dogCards.locator('h2')).toHaveText([
    'Mejor puntaje',
    'Segundo puntaje',
  ]);
  await page.getByRole('link', { name: 'Ver gatos' }).click();
  await cats.getByRole('link', { name: 'Ver todos' }).click();
  await expect(cats.locator('article.pet h2')).toHaveText(['Datos incompletos']);
  await expect(cats.getByText('Información insuficiente', { exact: true })).toBeVisible();
});

for (const viewport of [{ width: 1280, height: 900 }, { width: 390, height: 844 }]) {
  test(`las tarjetas de especie filtran y mantienen el diseño a ${viewport.width}px`, async ({ page }) => {
    await page.setViewportSize(viewport);
    await page.route('**/api/perfil/favoritos', route => route.fulfill({ json: [] }));
    await page.route('**/api/auth/login', route => route.fulfill({
      json: { token: 'token-de-prueba', expiresIn: 3600, user: { id: '7', correo: 'prueba@example.test', rol: 'ADOPTANTE' } },
    }));
    await page.route('**/api/auth/me', route => route.fulfill({
      json: { id: '7', correo: 'prueba@example.test', rol: 'ADOPTANTE' },
    }));
    await page.route('**/api/catalogo/opciones', route => route.fulfill({
      json: { especies: ['Perro', 'Gato'], tamanos: ['Mediano'], temperamentos: [] },
    }));
    const requests = [];
    await page.route('**/api/catalogo?**', route => {
      const params = new URL(route.request().url()).searchParams;
      requests.push(Object.fromEntries(params));
      const especie = params.get('especie');
      const search = params.get('search')?.toLowerCase();
      const items = pets.filter(pet =>
        (!especie || pet.especie === especie) &&
        (!search || pet.nombre.toLowerCase().includes(search) || pet.albergue.toLowerCase().includes(search))
      );
      const total = especie === 'Perro' && search === 'hogar petly' ? 24 : items.length;
      return route.fulfill({ json: { items, total } });
    });

    await page.goto('/acceso');
    await page.getByLabel('Correo electrónico', { exact: true }).fill('prueba@example.test');
    await page.getByLabel('Contraseña', { exact: true }).fill('password-de-prueba');
    await page.getByRole('button', { name: 'Entrar', exact: true }).click();

    const cats = page.getByRole('link', { name: 'Ver gatos' });
    const dogs = page.getByRole('link', { name: 'Ver perros' });
    const results = page.getByRole('region', { name: 'Resultados de perros' });
    const catResults = page.getByRole('region', { name: 'Resultados de gatos' });
    const catHero = catResults.getByRole('region', { name: 'Mascota destacada' });
    const hero = results.getByRole('region', { name: 'Mascota destacada' });
    const filters = page.locator('.catalog-filters');
    await expect(dogs).toBeVisible();
    await expect(catHero.getByRole('heading', { name: 'Datos incompletos' })).toBeVisible();
    await expect(hero.getByRole('heading', { name: 'Segundo puntaje' })).toBeVisible();
    await expect(catHero.getByText('Sin calcular', { exact: true })).toBeVisible();
    await expect(hero.getByText('72%', { exact: true })).toBeVisible();
    await expect(page.locator('article.pet')).toHaveCount(0);
    expect(requests.map(request => request.especie).sort()).toEqual(['Gato', 'Perro']);
    await dogs.click();
    await expect(page).toHaveURL(/\/adoptante\/catalogo\?especie=Perro$/);
    await expect(results.getByText('2 mascotas encontradas')).toBeVisible();
    await expect(results).toBeFocused();
    await expect(catHero).toBeVisible();
    await expect(page.getByText('Filtros activos', { exact: true })).toHaveCount(0);
    expect(requests).toHaveLength(2);
    const [catBox, dogBox, filterBox, resultBox] = await Promise.all([
      cats.boundingBox(), dogs.boundingBox(), filters.boundingBox(), catResults.boundingBox(),
    ]);
    expect(catBox.y + catBox.height).toBeLessThan(resultBox.y);
    expect(dogBox.y + dogBox.height).toBeLessThan(filterBox.y);
    expect(filterBox.y + filterBox.height).toBeLessThan(resultBox.y);
    if (viewport.width > 640) {
      expect(catBox.y).toBeCloseTo(dogBox.y);
      expect(catBox.x + catBox.width).toBeLessThan(dogBox.x);
    } else {
      expect(catBox.x).toBeCloseTo(dogBox.x);
      expect(catBox.y + catBox.height).toBeLessThan(dogBox.y);
      await filters.locator('summary').click();
    }
    const [speciesBox, sizeBox, maxAgeBox] = await Promise.all([
      filters.getByLabel('Especie', { exact: true }).boundingBox(),
      filters.getByLabel('Tamaño', { exact: true }).boundingBox(),
      filters.getByLabel('Edad máxima (meses)').boundingBox(),
    ]);
    expect(speciesBox.x + speciesBox.width).toBeLessThan(sizeBox.x);
    expect(speciesBox.y + speciesBox.height).toBeCloseTo(sizeBox.y + sizeBox.height);
    if (viewport.width > 640) {
      expect(maxAgeBox.y + maxAgeBox.height).toBeCloseTo(speciesBox.y + speciesBox.height);
    } else {
      expect(maxAgeBox.y).toBeGreaterThan(sizeBox.y + sizeBox.height);
    }
    for (const card of [cats, dogs]) {
      await expect.poll(() => card.locator('img').evaluate(img => img.naturalWidth)).toBeGreaterThan(0);
    }

    await page.getByLabel('Buscar mascota o albergue').fill('Hogar Petly');
    await filters.getByRole('button', { name: 'Buscar', exact: true }).click();
    await results.getByRole('link', { name: 'Ver todos' }).click();
    await expect(page.getByText('Página 1 de 2')).toBeVisible();
    await results.getByRole('link', { name: 'Siguiente', exact: true }).click();
    await expect(page.getByText('Página 2 de 2')).toBeVisible();
    const requestsBeforeNavigation = requests.length;

    if (viewport.width > 640) {
      await cats.click({ position: { x: 24, y: 24 } });
    } else {
      await cats.focus();
      await page.keyboard.press('Enter');
    }
    await expect(catResults).toBeFocused();
    await expect(catResults.getByText('1 mascota encontrada')).toBeVisible();
    await expect(catResults).toBeInViewport();
    await expect(page).toHaveURL(/\/adoptante\/catalogo\?search=Hogar\+Petly&especie=Gato$/);
    await expect(catHero.getByRole('heading', { name: 'Datos incompletos' })).toBeVisible();
    await expect(page.getByLabel('Especie', { exact: true })).toHaveValue('Gato');
    await expect(cats).toHaveAttribute('aria-current', 'true');
    expect(requests).toHaveLength(requestsBeforeNavigation);
    expect(requests).toContainEqual(expect.objectContaining({ especie: 'Gato', page: '1', search: 'Hogar Petly' }));

    if (viewport.width > 640) {
      await cats.focus();
      await page.keyboard.press('Tab');
      await expect(dogs).toBeFocused();
      await page.keyboard.press('Enter');
    } else {
      await dogs.click({ position: { x: 24, y: 24 } });
    }
    await expect(results).toBeFocused();
    await expect(results.getByText('24 mascotas encontradas')).toBeVisible();
    await expect(results).toBeInViewport();
    await expect(page).toHaveURL(/\/adoptante\/catalogo\?search=Hogar\+Petly&especie=Perro$/);
    await expect(hero.getByRole('heading', { name: 'Segundo puntaje' })).toBeVisible();
    await expect(hero.getByText('Sano', { exact: true })).toBeVisible();
    await expect(hero.getByText('Macho', { exact: true })).toBeVisible();
    await expect(hero.getByRole('link', { name: 'Solicitar adopción' })).toHaveAttribute('href', '/adoptante/catalogo/7/solicitud');
    await expect(hero.getByRole('link', { name: 'Conocerme' })).toHaveAttribute('href', '/adoptante/catalogo/7');
    await hero.getByRole('button', { name: 'Foto siguiente' }).click();
    await expect(hero.getByRole('img')).toHaveAttribute('src', /mascotas-portada/);
    await results.getByRole('button', { name: 'Destacar a Mejor puntaje' }).press('Space');
    await expect(hero.getByRole('heading', { name: 'Mejor puntaje' })).toBeVisible();
    await expect(hero.getByText('100%', { exact: true })).toBeVisible();
    await expect(hero.getByRole('img')).toHaveAttribute('src', /perro\.webp$/);
    await expect(hero.getByRole('button', { name: 'Foto siguiente' })).toHaveCount(0);
    await expect(hero.getByRole('link', { name: 'Solicitar adopción' })).toHaveCount(0);
    await expect(hero.getByText('Sano', { exact: true })).toHaveCount(0);
    await expect(results.getByRole('button', { name: 'Destacar a Mejor puntaje' })).toHaveAttribute('aria-pressed', 'true');
    await page.route('**/api/perfil/favoritos/7', route => route.fulfill({ status: 204 }));
    await results.getByRole('button', { name: 'Guardar Segundo puntaje en favoritos' }).click();
    await expect(results.getByRole('button', { name: 'Quitar Segundo puntaje de favoritos' })).toHaveAttribute('aria-pressed', 'true');
    await expect(hero.getByRole('heading', { name: 'Mejor puntaje' })).toBeVisible();

    const [media, sheet] = await Promise.all([
      hero.locator('.catalog-featured__media').boundingBox(),
      hero.locator('.catalog-featured__sheet').boundingBox(),
    ]);
    if (viewport.width > 640) {
      expect(media.x + media.width).toBeLessThan(sheet.x);
      expect(media.y).toBeCloseTo(sheet.y);
    } else {
      expect(media.x).toBeCloseTo(sheet.x);
      expect(media.y + media.height).toBeLessThan(sheet.y);
    }
    await expect(page.getByLabel('Especie', { exact: true })).toHaveValue('Perro');
    await expect(dogs).toHaveAttribute('aria-current', 'true');
    expect(requests).toHaveLength(requestsBeforeNavigation);

    await results.getByRole('link', { name: 'Ver todos' }).click();
    await expect(page).toHaveURL(/[?&]vista=todos$/);
    await expect(results.locator('article.pet h2')).toHaveText(['Mejor puntaje', 'Segundo puntaje']);
    await expect(page.getByText('Página 1 de 2')).toBeVisible();

    await page.goBack();
    await expect(hero).toBeVisible();
    await page.goBack();
    await expect(page).toHaveURL(/[?&]especie=Gato$/);
    await expect(catHero.getByRole('heading', { name: 'Datos incompletos' })).toBeVisible();
    await page.goForward();
    await expect(page).toHaveURL(/[?&]especie=Perro$/);
    await page.route('**/api/auth/refresh', route => route.fulfill({
      json: { token: 'token-de-prueba', expiresIn: 3600, user: { id: '7', correo: 'prueba@example.test', rol: 'ADOPTANTE' } },
    }));
    await page.reload();
    await expect(results.getByText('24 mascotas encontradas')).toBeVisible();
    if (viewport.width <= 640 && !await filters.getByLabel('Especie', { exact: true }).isVisible()) {
      await filters.locator('summary').click();
    }
    await expect(page.getByLabel('Especie', { exact: true })).toHaveValue('Perro');
    await results.getByRole('button', { name: 'Destacar a Mejor puntaje' }).click();
    const search = filters.getByLabel('Buscar mascota o albergue');
    await search.fill('Segundo puntaje');
    await filters.getByRole('button', { name: 'Buscar', exact: true }).click();
    await expect(hero.getByRole('heading', { name: 'Segundo puntaje' })).toBeVisible();
    await expect(results.getByText('1 mascota encontrada')).toBeVisible();
    await search.fill('Sin coincidencias');
    await filters.getByRole('button', { name: 'Buscar', exact: true }).click();
    await expect(results.getByRole('heading', { name: 'Sigamos buscando' })).toBeVisible();
    await expect(hero).toHaveCount(0);
  });
}
