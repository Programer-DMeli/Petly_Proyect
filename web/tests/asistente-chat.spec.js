import { test, expect } from '@playwright/test';

async function login(page, role) {
  await page.evaluate(async role => {
    const { setSession } = await import('/src/services/session.ts');
    const { router } = await import('/src/routes/index.tsx');
    setSession({ token: `test-${role}`, expires: Date.now() + 3600000,
      identity: { id: '42', correo: 'chat@example.test', rol: role } });
    await router.navigate(role === 'ALBERGUE' ? '/albergue/mascotas' : '/adoptante/cuestionario');
  }, role);
}

test.beforeEach(async ({ page }) => {
  // Isolated UI checks: backend and provider are deliberately simulated.
  await page.route('**/api/**', route => route.fulfill({ status: 404, json: { message: 'Sin datos de prueba' } }));
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
    await page.getByLabel('Tu consulta', { exact: true }).fill('Hola, necesito orientación');
    await page.getByRole('button', { name: 'Enviar', exact: true }).click();
    await expect(page.getByRole('log')).toContainText('¿Qué necesitas saber?');
    await page.getByRole('button', { name: 'Cerrar asistente' }).click();
    await page.evaluate(async role => {
      const { router } = await import('/src/routes/index.tsx');
      await router.navigate(role === 'ALBERGUE' ? '/albergue/solicitudes' : '/adoptante/disponibilidad');
    }, role);
    await page.getByRole('button', { name: 'Abrir asistente Petly' }).click();
    await expect(page.getByRole('log')).toContainText('¿Qué necesitas saber?');
    await page.getByLabel('Tu consulta', { exact: true }).fill('¿Y el siguiente paso?');
    await page.getByRole('button', { name: 'Enviar', exact: true }).click();
    await expect(page.getByRole('log')).toContainText('Continuemos con tu consulta.');
    expect(bodies[1]).toEqual({ platform: 'web', messages: [
      { role: 'user', text: 'Hola, necesito orientación' },
      { role: 'model', text: '¿Qué necesitas saber?' },
      { role: 'user', text: '¿Y el siguiente paso?' },
    ] });
    await page.evaluate(async () => (await import('/src/services/session.ts')).setSession(null));
    await expect(page.getByRole('dialog', { name: 'Asistente Petly' })).toHaveCount(0);
    await login(page, role);
    await page.getByRole('button', { name: 'Abrir asistente Petly' }).click();
    await expect(page.getByRole('log')).not.toContainText('Hola, necesito orientación');
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
  await page.keyboard.press('Escape');
  await expect(page.getByRole('button', { name: 'Abrir asistente Petly' })).toBeFocused();
});
