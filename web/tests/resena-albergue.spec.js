import { expect, test } from '@playwright/test';

async function ingresar(page, rol, responder) {
  const usuario = { id: rol === 'ADOPTANTE' ? '12' : '7', correo: 'cuenta@example.test', rol };
  await page.route('**/api/**', route => {
    const path = new URL(route.request().url()).pathname;
    if (path === '/api/auth/refresh') return route.fulfill({ status: 401 });
    if (path === '/api/auth/login') return route.fulfill({ json: { token: 'resena-prueba', expiresIn: 3600, user: usuario } });
    if (path === '/api/auth/me') return route.fulfill({ json: usuario });
    return responder(route, path);
  });
  await page.goto('/acceso');
  await page.getByLabel('Correo electrónico', { exact: true }).fill(usuario.correo);
  await page.getByLabel('Contraseña', { exact: true }).fill('clave-de-prueba');
  await page.getByRole('button', { name: 'Entrar', exact: true }).click();
  await expect(page).toHaveURL(rol === 'ADOPTANTE' ? /\/adoptante\/catalogo$/ : /\/albergue$/);
}

async function navegar(page, ruta) {
  await page.evaluate(path => {
    window.history.pushState({}, '', path);
    window.dispatchEvent(new PopStateEvent('popstate'));
  }, ruta);
}

test('el adoptante ve el cierre, envía su opinión y recupera su reseña al regresar', async ({ page }) => {
  let cerradoEn = null;
  let registrada = null;
  let consultas = 0;
  const historial = '/adoptante/solicitudes/42';
  await ingresar(page, 'ADOPTANTE', (route, path) => {
    if (path === '/api/solicitudes/42/seguimiento') return route.fulfill({
      json: { solicitudId: 42, estadoActual: 'ENTREGADA', eventos: [], albergueId: 9, cerradoEn },
    });
    if (path === '/api/albergues/9/resenas/mi-resena') {
      consultas++;
      return route.fulfill({ json: { registrada: registrada !== null, calificacion: registrada?.calificacion ?? null } });
    }
    if (path === '/api/albergues/9/resenas' && route.request().method() === 'POST') {
      expect(cerradoEn).not.toBeNull();
      expect(registrada).toBeNull();
      registrada = route.request().postDataJSON();
      return route.fulfill({ status: 201, json: { total: 1 } });
    }
    return route.fulfill({ json: { results: [], next: null } });
  });
  await navegar(page, historial);
  await expect(page.getByText('Expediente de adopción abierto')).toBeVisible();
  await expect(page.getByRole('button', { name: 'Opiniones y reseñas' })).toHaveCount(0);
  expect(consultas).toBe(0);

  cerradoEn = '2026-11-24T10:00:00-05:00';
  await page.getByRole('button', { name: 'Actualizar historial' }).click();
  await expect(page.getByText('Cierre de adopción validado')).toBeVisible();
  await page.getByRole('button', { name: 'Opiniones y reseñas' }).click();
  const dialogo = page.getByRole('dialog', { name: 'Calificar y opinar' });
  await dialogo.getByRole('button', { name: '4 estrellas' }).click();
  await dialogo.getByLabel('Tu opinión').fill('Atención amable durante todo el proceso.');
  await dialogo.getByRole('button', { name: 'Publicar reseña' }).click();
  await expect(page.getByText('Reseña registrada')).toBeVisible();
  expect(registrada).toEqual({ calificacion: 4, comentario: 'Atención amable durante todo el proceso.' });

  await page.getByRole('link', { name: 'Volver a Mis solicitudes' }).click();
  await expect(page.getByRole('heading', { name: 'Seguimiento de solicitud' })).toHaveCount(0);
  const consultasPrevias = consultas;
  await navegar(page, historial);
  await expect(page.getByText('Reseña registrada')).toBeVisible();
  await expect(page.getByRole('button', { name: 'Opiniones y reseñas' })).toHaveCount(0);
  expect(consultas).toBeGreaterThan(consultasPrevias);
});

test('el albergue ve su foto, el resumen y las opiniones recibidas', async ({ page }) => {
  const foto = '/foto-albergue-prueba.png';
  await page.route('**' + foto, route => route.fulfill({
    contentType: 'image/png',
    body: Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jWZkAAAAASUVORK5CYII=', 'base64'),
  }));
  await ingresar(page, 'ALBERGUE', (route, path) => {
    if (path === '/api/albergues/mi-perfil/') return route.fulfill({ json: {
      id: 9, nombre: 'Refugio Nube', descripcion: 'Cuidamos a animales rescatados.',
      foto_url: new URL(foto, 'http://localhost:5173').href,
    } });
    if (path === '/api/albergue/resenas') return route.fulfill({ json: [
      { id: 1, calificacion: 4, comentario: 'Atención amable.', estado_moderacion: 'VISIBLE', creado_en: '2026-11-24T10:00:00' },
      { id: 2, calificacion: 5, comentario: 'Excelente atención.', estado_moderacion: 'VISIBLE', creado_en: '2026-11-24T10:00:00' },
    ] });
    return route.fulfill({ json: { results: [], next: null } });
  });
  await page.getByRole('button', { name: 'Comunidad', exact: true }).click();
  await page.getByRole('link', { name: /Reseñas recibidas/ }).click();
  await expect(page.getByRole('img', { name: 'Foto de Refugio Nube' })).toBeVisible();
  await expect(page.getByText('Cuidamos a animales rescatados.')).toBeVisible();
  const resumen = page.getByRole('region', { name: 'Calificación del albergue' });
  await expect(resumen.getByText('4.5', { exact: true })).toBeVisible();
  await expect(resumen.getByRole('progressbar', { name: '1 opinión de 4 estrellas' })).toBeVisible();
  await expect(page.getByText('Atención amable.')).toBeVisible();
});
