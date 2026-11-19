import { test, expect } from '@playwright/test';

test('edita y reabre el historial clínico de cada mascota', async ({ page }) => {
  const pet = {
    especie: 'PERRO', sexo: 'HEMBRA', tamanio: 'MEDIANO', temperamento: 'SOCIABLE',
    energia: 'MEDIA', edad_anos: 2, edad_meses: 0, descripcion: '', estado: 'DISPONIBLE',
    vacunas_al_dia: true, estado_salud_general: 'Control de rutina.',
  };
  const mascotas = new Map([
    [1, { ...pet, id: 1, nombre: 'Nube', registros_clinicos: [] }],
    [2, { ...pet, id: 2, nombre: 'Luna', registros_clinicos: [
      { id: 4, tipo: 'VACUNACION', fecha: '2025-02-01' },
    ] }],
  ]);
  const usuario = { id: '8', correo: 'albergue@example.test', rol: 'ALBERGUE' };
  const cargas = [];
  let siguienteId = 10;
  await page.route('**/api/**', async route => {
    const request = route.request();
    const path = new URL(request.url()).pathname;
    if (path === '/api/auth/refresh') return route.fulfill({ status: 401 });
    if (path === '/api/auth/login') return route.fulfill({ json: { token: 'prueba', expiresIn: 3600, user: usuario } });
    if (path === '/api/auth/me') return route.fulfill({ json: usuario });
    if (path === '/api/albergues/mi-perfil/') {
      return route.fulfill({ json: {
        id: 1, nombre: 'Refugio de prueba', direccion: 'Lima',
        telefono: '999000111', email: usuario.correo,
      } });
    }
    if (path === '/api/mascotas/') {
      return route.fulfill({ json: { results: Array.from(mascotas.values()), count: mascotas.size, next: null } });
    }
    const match = path.match(/^\/api\/mascotas\/(\d+)\/$/);
    if (!match) return route.fulfill({ json: { results: [], count: 0, next: null } });

    const mascota = mascotas.get(Number(match[1]));
    if (request.method() === 'PATCH') {
      const form = await new Response(request.postDataBuffer(), { headers: request.headers() }).formData();
      const registros = JSON.parse(form.get('registros_clinicos'));
      cargas.push(registros);
      const guardados = new Map(mascota.registros_clinicos.map(registro => [registro.id, registro]));
      for (const registro of registros) {
        const id = registro.id ?? siguienteId++;
        guardados.set(id, { ...registro, id });
      }
      mascota.registros_clinicos = Array.from(guardados.values());
    }
    return route.fulfill({ json: mascota });
  });

  await page.goto('/acceso');
  await page.getByLabel('Correo', { exact: true }).fill(usuario.correo);
  await page.getByLabel('Contraseña', { exact: true }).fill('prueba-local');
  await page.getByRole('button', { name: 'Entrar', exact: true }).click();
  await expect(page).toHaveURL(/\/albergue$/);
  await page.evaluate(() => {
    history.pushState({}, '', '/albergue/mascotas');
    dispatchEvent(new PopStateEvent('popstate'));
  });

  const nube = page.locator('article.list-row').filter({ hasText: 'Nube' });
  const luna = page.locator('article.list-row').filter({ hasText: 'Luna' });
  const editar = mascota => mascota.getByRole('button', { name: 'Editar mascota' }).click();
  const tipo = page.getByLabel('Tipo de procedimiento');
  const fecha = page.getByLabel('Fecha del procedimiento');
  const guardar = page.getByRole('button', { name: 'Guardar cambios de mascota' });
  await editar(nube);
  await expect(page.getByText('Aún no hay registros con fecha.')).toBeVisible();
  await expect(page.getByLabel('Vacunas al día')).toBeChecked();

  const procedimientos = ['VACUNACION', 'ESTERILIZACION', 'DESPARASITACION'];
  for (const [index, procedimiento] of procedimientos.entries()) {
    await page.getByRole('button', { name: 'Agregar registro clínico' }).click();
    await tipo.nth(index).selectOption(procedimiento);
    await fecha.nth(index).fill(`2025-01-0${index + 1}`);
  }
  await guardar.click();
  await expect(page.getByText('Mascota guardada.', { exact: true })).toBeVisible();
  expect(cargas[0]).toEqual(procedimientos.map((tipo, index) => ({ tipo, fecha: `2025-01-0${index + 1}` })));

  await editar(nube);
  await expect(fecha).toHaveCount(3);
  await expect(tipo.nth(1)).toHaveValue('ESTERILIZACION');
  await expect(fecha.nth(1)).toHaveValue('2025-01-02');
  await fecha.nth(1).fill('2025-01-05');
  await guardar.click();
  await expect(page.getByText('Mascota guardada.', { exact: true })).toBeVisible();
  expect(cargas[1]).toEqual([{ id: 11, tipo: 'ESTERILIZACION', fecha: '2025-01-05' }]);

  await editar(luna);
  await expect(fecha).toHaveCount(1);
  await expect(fecha.first()).toHaveValue('2025-02-01');
  await editar(nube);
  await expect(fecha.nth(1)).toHaveValue('2025-01-05');
});
