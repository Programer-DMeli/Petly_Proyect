import { test, expect } from '@playwright/test';

test.use({ actionTimeout: 30000 });

async function navegar(page, path) {
  await page.evaluate(path => {
    window.history.pushState({}, '', path);
    window.dispatchEvent(new PopStateEvent('popstate'));
  }, path);
}

async function login(page, role) {
  await page.route('**/api/auth/login', route => route.fulfill({
    json: { token: `test-${role}`, expiresIn: 3600, user: { id: '42', correo: 'chat@example.test', rol: role } },
  }));
  await page.route('**/api/auth/me', route => route.fulfill({
    json: { id: '42', correo: 'chat@example.test', rol: role },
  }));
  await page.goto('/acceso');
  await page.getByLabel('Correo', { exact: true }).fill('chat@example.test');
  await page.getByLabel('Contraseña', { exact: true }).fill('clave-de-prueba');
  await page.getByRole('button', { name: 'Entrar', exact: true }).click();
  await expect(page).toHaveURL(role === 'ALBERGUE' ? /\/albergue$/ : /\/adoptante\/catalogo$/, { timeout: 15000 });
}

test.beforeEach(async ({ page }) => {
  // Isolated UI checks: backend and provider are deliberately simulated.
  await page.route('**/api/**', route => route.fulfill({ status: 404, json: { message: 'Sin datos de prueba' } }));
  await page.route('**/api/auth/refresh', route => route.fulfill({ status: 401 }));
  await page.route('**/api/auth/logout', route => route.fulfill({ status: 204 }));
  await page.goto('/acceso');
});

for (const role of ['ADOPTANTE', 'ALBERGUE']) {
  test(`${role}: uses Spring with authentication, retains context and clears on logout`, async ({ page }) => {
    const bodies = [];
    await page.route('**/api/asistente/chat', async route => {
      expect(new URL(route.request().url()).port).toBe('8080');
      expect(route.request().headers().authorization).toBe(`Bearer test-${role}`);
      bodies.push(route.request().postDataJSON());
      await route.fulfill({ json: { reply: bodies.length === 1 ? '¿Qué necesitas saber?' : 'Continuemos con tu consulta.' } });
    });
    await login(page, role);
    await page.getByRole('button', { name: 'Abrir asistente Petly' }).click();
    const quickQuestion = role === 'ALBERGUE' ? '¿Cómo registro una mascota?' : '¿Cómo empiezo una adopción?';
    const suggestions = page.getByRole('list', { name: 'Preguntas sugeridas' });
    await expect(page.getByRole('heading', { name: '¡Hola! Soy Petly' })).toBeVisible();
    await expect(suggestions.getByRole('button')).toHaveCount(4);
    await expect(page.getByRole('button', { name: 'Enviar', exact: true })).toBeDisabled();
    await suggestions.getByRole('button', { name: quickQuestion, exact: true }).click();
    await expect(page.getByRole('log')).toContainText('¿Qué necesitas saber?');
    await expect(suggestions).toHaveCount(0);
    await page.getByRole('button', { name: 'Cerrar asistente' }).click();
    await navegar(page, role === 'ALBERGUE' ? '/albergue/solicitudes' : '/adoptante/disponibilidad');
    await page.getByRole('button', { name: 'Abrir asistente Petly' }).click();
    await expect(page.getByRole('log')).toContainText('¿Qué necesitas saber?');
    await page.getByLabel('Tu consulta', { exact: true }).fill('¿Y el siguiente paso?');
    await page.getByRole('button', { name: 'Enviar', exact: true }).click();
    await expect(page.getByRole('log')).toContainText('Continuemos con tu consulta.');
    expect(bodies[1]).toEqual({ platform: 'web', messages: [
      { role: 'user', text: quickQuestion },
      { role: 'model', text: '¿Qué necesitas saber?' },
      { role: 'user', text: '¿Y el siguiente paso?' },
    ] });
    await page.getByRole('button', { name: 'Cerrar asistente' }).click();
    await page.getByRole('button', { name: 'Abrir menú de usuario' }).click();
    await page.getByRole('button', { name: 'Cerrar sesión' }).click();
    await expect(page.getByRole('dialog', { name: 'Asistente Petly' })).toHaveCount(0);
    await login(page, role);
    await page.getByRole('button', { name: 'Abrir asistente Petly' }).click();
    await expect(page.getByRole('log')).toBeEmpty();
    await expect(suggestions.getByRole('button', { name: quickQuestion, exact: true })).toBeVisible();
  });
}

test('failed requests can be retried without duplicating the question; new conversation clears history', async ({ page }) => {
  const requests = [];
  await page.route('**/api/asistente/chat', async route => {
    requests.push(route.request().postDataJSON());
    await route.fulfill(requests.length === 1
      ? { status: 503, json: { message: 'El asistente está ocupado.' } }
      : { json: { reply: 'Ahora podemos continuar.' } });
  });
  await login(page, 'ADOPTANTE');
  await page.getByRole('button', { name: 'Abrir asistente Petly' }).click();
  await page.getByLabel('Tu consulta', { exact: true }).fill('¿Cómo lleno mi formulario?');
  await page.getByRole('button', { name: 'Enviar', exact: true }).click();
  await expect(page.getByRole('dialog').getByRole('alert')).toContainText('ocupado');
  await page.getByRole('button', { name: 'Volver a intentar', exact: true }).click();
  await expect(page.getByRole('log')).toContainText('Ahora podemos continuar.');
  expect(requests[0]).toEqual(requests[1]);
  await expect(page.getByRole('log').getByText('¿Cómo lleno mi formulario?', { exact: false })).toHaveCount(1);
  await page.getByRole('button', { name: 'Nueva conversación' }).click();
  await expect(page.getByRole('log')).toBeEmpty();
  await expect(page.getByRole('heading', { name: '¡Hola! Soy Petly' })).toBeVisible();
  await expect(page.getByRole('textbox', { name: 'Tu consulta' })).toBeFocused();
  await page.keyboard.press('Escape');
  await expect(page.getByRole('button', { name: 'Abrir asistente Petly' })).toBeFocused();
});

test('muestra una guía larga desde el primer paso y mantiene visible el formulario', async ({ page }) => {
  const steps = Array.from({ length: 12 }, (_, index) =>
    `${index + 1}. ${index === 0 ? '🐾 ' : ''}Revisa la información de la mascota y confirma los detalles con el albergue antes de continuar.`
  );
  await page.route('**/api/asistente/chat', route => route.fulfill({
    json: { reply: ['¡Vamos paso a paso!', ...steps, 'Puedes revisar la guía cuando quieras.'].join('\n\n') },
  }));
  await login(page, 'ALBERGUE');
  await page.getByRole('button', { name: 'Abrir asistente Petly' }).click();
  const panel = page.getByRole('dialog', { name: 'Asistente Petly' });
  expect((await panel.boundingBox()).width).toBe(500);

  await page.setViewportSize({ width: 506, height: 828 });
  expect((await panel.boundingBox()).width).toBe(490);
  await panel.getByLabel('Tu consulta').fill('¿Me explicas el proceso paso a paso?');
  await panel.getByRole('button', { name: 'Enviar', exact: true }).click();
  const log = panel.getByRole('log');
  const items = log.getByRole('list').getByRole('listitem');
  await expect(items).toHaveCount(12);
  await expect(items.first()).toContainText('🐾');
  await expect(log.getByText('¡Vamos paso a paso!')).toBeInViewport();
  await expect(items.first()).toBeInViewport();
  await expect(panel.getByLabel('Tu consulta')).toBeInViewport();
  await log.evaluate(element => { element.scrollTop = element.scrollHeight; });
  await expect(items.last()).toBeInViewport();
  await expect(log.getByText('Puedes revisar la guía cuando quieras.')).toBeInViewport();
});

test('el catálogo reúne preguntas y cuidados de la ficha y el asistente enlaza la guía', async ({ page }) => {
  const mascota = {
    id: 7, nombre: 'Luna', especie: 'Perro', raza: 'Labrador Retriever', albergue: 'Huellitas',
    estado: 'Disponible', tamano: 'Grande', fotografiaUrl: null,
  };
  await page.route('**/api/catalogo?**', route => route.fulfill({ json: { items: [mascota], total: 1 } }));
  await page.route('**/api/catalogo/opciones', route => route.fulfill({ json: {} }));
  await page.route('**/api/asistente/preguntas-frecuentes', route => route.fulfill({
    json: [{ id: 'proceso', nombre: 'Proceso de adopción', preguntas: [{
      id: 'inicio', pregunta: '¿Cómo empiezo una adopción?', respuesta: 'Abre el catálogo.',
    }] }],
  }));
  const consultas = [];
  await page.route('**/api/asistente/chat', async route => {
    consultas.push(route.request().postDataJSON());
    await route.fulfill({ json: { reply: 'El primer paso es revisar las mascotas disponibles.' } });
  });
  await page.route('**/api/asistente/cuidados?**', async route => {
    const params = new URL(route.request().url()).searchParams;
    expect(params.get('especie')).toBe('perro');
    expect(params.get('raza')).toBe('Labrador Retriever');
    await route.fulfill({ json: { mensaje: 'Recomendaciones de especie y raza.', consejos: [{
      categoria: 'actividad', titulo: 'Actividad para un Labrador Retriever', contenido: 'Adapta los paseos a su edad.',
    }] } });
  });

  await login(page, 'ADOPTANTE');
  const ayuda = page.getByRole('region', { name: 'Ayuda para adoptar' });
  await expect(ayuda.getByRole('heading', { name: 'Proceso de adopción' })).toBeVisible();
  expect(consultas).toHaveLength(0);
  const pregunta = ayuda.getByText('¿Cómo empiezo una adopción?', { exact: true });
  await pregunta.click();
  await expect(ayuda.getByText('El primer paso es revisar las mascotas disponibles.')).toBeVisible();
  await pregunta.click();
  await pregunta.click();
  expect(consultas).toHaveLength(1);
  await ayuda.getByLabel('Mascota del catálogo').selectOption('7');
  await expect(ayuda.getByText('Actividad para un Labrador Retriever')).toBeVisible();

  await page.getByRole('button', { name: 'Abrir asistente Petly' }).click();
  const panel = page.getByRole('dialog', { name: 'Asistente Petly' });
  await expect(panel.getByText('Preguntas frecuentes', { exact: true })).toHaveCount(0);
  await expect(panel.getByText('Cuidados por mascota', { exact: true })).toHaveCount(0);
  await panel.getByRole('link', { name: 'Guía del cuestionario' }).click();
  await expect(page).toHaveURL(/\/adoptante\/cuestionario$/);
});
