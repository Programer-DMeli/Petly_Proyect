import { test, expect } from '@playwright/test';
import fs from 'node:fs';

const fixturePath = process.env.PETLY_V17_FIXTURES;
const fixture = fixturePath ? JSON.parse(fs.readFileSync(fixturePath, 'utf8')) : null;
const web = 'http://127.0.0.1:5175';
const spring = 'http://127.0.0.1:8082';
const django = 'http://127.0.0.1:8002';

test.skip(!fixture, 'Requiere la base local sintética de V17 y PETLY_V17_FIXTURES.');

async function login(page, account, destination) {
  await page.goto(web + '/acceso');
  await page.getByLabel('Correo', { exact: true }).fill(account.correo);
  await page.getByLabel('Contraseña', { exact: true }).fill(account.password);
  const response = page.waitForResponse(r => r.url() === spring + '/api/auth/login' && r.request().method() === 'POST');
  await page.getByRole('button', { name: 'Entrar', exact: true }).click();
  const loginResponse = await response;
  expect(loginResponse.status()).toBe(200);
  const login = await loginResponse.json();
  expect(login).toMatchObject({ expiresIn: 3600, user: { correo: account.correo } });
  expect(login).not.toHaveProperty('refreshToken');
  await expect(page).toHaveURL(web + destination);
  return login.token;
}

async function sendMessage(page, text) {
  await page.getByLabel('Mensaje', { exact: true }).fill(text);
  const sent = page.waitForResponse(r => r.url().startsWith(django + '/api/mensajeria/conversaciones/') &&
    r.url().endsWith('/mensajes/') && r.request().method() === 'POST');
  await page.getByRole('button', { name: 'Enviar', exact: true }).click();
  const response = await sent;
  expect(response.status()).toBe(201);
  expect(await response.json()).toMatchObject({ texto: text, propio: true });
}

test('V17 real: formulario, persistencia Spring–Django y chat bidireccional', async ({ browser }) => {
  const adoptante = await browser.newPage();
  const albergue = await browser.newPage();
  try {
    await login(adoptante, fixture.ADOPTANTE, '/adoptante/catalogo');
    await expect(adoptante.getByText('Luna V17 Web', { exact: true })).toBeVisible();

    const formPath = '/adoptante/catalogo/' + fixture.mascotaWeb + '/solicitud';
    const refreshed = adoptante.waitForResponse(r => r.url() === spring + '/api/auth/refresh' && r.request().method() === 'POST');
    await adoptante.goto(web + formPath);
    const refresh = await refreshed;
    expect(refresh.status()).toBe(200);
    const form = adoptante.locator('.adoption-form');
    await form.getByLabel('Nombre completo').fill('Adoptante de prueba V17');
    await form.getByLabel('DNI declarado (8 dígitos)').fill('00000000');
    await form.getByLabel('Dirección del hogar').fill('Lima, dirección de prueba');
    await form.getByLabel('Teléfono de contacto').fill('999000111');
    await form.getByLabel('Propiedad de vivienda').selectOption('Alquilada');
    await form.getByLabel(/permiso del propietario/).fill('Autorización de prueba');
    await form.getByLabel(/espacios tendrá/).fill('Sala y patio');
    await form.getByLabel(/alergia a los animales/).selectOption('false');
    await form.getByLabel(/requieren cuidados especiales/).selectOption('false');
    await form.getByLabel(/Por qué quieres adoptar/).fill('Prueba de adopción con datos sintéticos.');
    await form.getByLabel(/organizarás sus cuidados/).fill('Paseos y controles veterinarios.');
    await form.getByLabel(/Has cuidado mascotas/).selectOption('false');
    await form.getByLabel(/Aceptas la esterilización/).selectOption('true');
    await form.getByLabel(/Aceptas el seguimiento/).selectOption('true');
    await form.getByLabel(/cubrir los gastos/).selectOption('true');
    const submitted = adoptante.waitForResponse(r => r.url() === spring + '/api/solicitudes' && r.request().method() === 'POST');
    await form.getByRole('button', { name: 'Enviar solicitud' }).click();
    const response = await submitted;
    expect(response.status()).toBe(201);
    const persisted = await response.json();
    expect(persisted).toMatchObject({ mascota: fixture.mascotaWeb, estado: 'PENDIENTE' });
    await expect(adoptante).toHaveURL(web + '/adoptante/solicitudes/' + persisted.id);

    const ownerToken = await login(albergue, fixture.ALBERGUE, '/albergue');
    const ownerRead = await albergue.request.get(django + '/api/acceso/solicitudes/' + persisted.id + '/',
      { headers: { Authorization: 'Bearer ' + ownerToken } });
    expect(ownerRead.status()).toBe(200);
    expect(await ownerRead.json()).toMatchObject({ id: persisted.id, estado: 'PENDIENTE' });
    await albergue.goto(web + '/albergue/mensajes?solicitud=' + persisted.id);
    await expect(albergue).toHaveURL(/\/albergue\/mensajes\/\d+/);
    const chatId = new URL(albergue.url()).pathname.split('/').at(-1);
    await adoptante.goto(web + '/adoptante/mensajes/' + chatId);
    await expect(adoptante.getByLabel('Mensaje', { exact: true })).toBeVisible();
    const fromShelter = 'Mensaje de prueba del albergue V17';
    const fromAdopter = 'Respuesta de prueba del adoptante V17';
    await sendMessage(albergue, fromShelter);
    await expect(adoptante.locator('.chat-directo-messages')).toContainText(fromShelter, { timeout: 18_000 });
    await sendMessage(adoptante, fromAdopter);
    await expect(albergue.locator('.chat-directo-messages')).toContainText(fromAdopter, { timeout: 18_000 });
    await albergue.reload();
    await expect(albergue.locator('.chat-directo-messages')).toContainText(fromShelter);
    await expect(albergue.locator('.chat-directo-messages')).toContainText(fromAdopter);
    console.log('V17: creación de solicitud React HTTP 201, consulta en Django HTTP 200 y chat persistido en ambas direcciones.');
  } finally {
    await Promise.all([adoptante.close(), albergue.close()]);
  }
});

test('V17 real: el resultado del proveedor llega al asistente de React', async ({ page }) => {
  await login(page, fixture.ADOPTANTE, '/adoptante/catalogo');
  await page.getByRole('button', { name: 'Abrir asistente Petly' }).click();
  await page.getByLabel('Tu consulta', { exact: true }).fill('¿Qué debo preparar para recibir por primera vez a un perro adoptado?');
  const result = page.waitForResponse(r => r.url() === spring + '/api/asistente/chat' && r.request().method() === 'POST',
    { timeout: 65_000 });
  await page.getByRole('button', { name: 'Enviar', exact: true }).click();
  const response = await result;
  const body = await response.json();
  if (response.status() === 200) {
    expect(body.reply.trim()).not.toBe('');
    await expect(page.getByRole('log')).toContainText(body.reply);
    console.log('Asistente real: HTTP 200, respuesta visible en la interfaz.');
  } else {
    expect(response.status()).toBe(503);
    await expect(page.getByRole('dialog', { name: 'Asistente Petly' }).getByRole('alert')).toBeVisible();
    await expect(page.getByRole('button', { name: 'Volver a intentar', exact: true })).toBeVisible();
    console.log('Asistente real: HTTP 503; React muestra el error y permite reintentar.');
  }
});
