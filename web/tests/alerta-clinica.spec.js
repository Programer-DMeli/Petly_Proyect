import { test, expect } from '@playwright/test';

async function abrirPanel(page, usuario, ruta, manejar) {
  await page.route('**/api/**', async route => {
    const path = new URL(route.request().url()).pathname;
    if (path === '/api/auth/refresh') return route.fulfill({ status: 401 });
    if (path === '/api/auth/login') return route.fulfill({ json: { token: `token-${usuario.id}`, expiresIn: 3600, user: usuario } });
    if (path === '/api/auth/me') return route.fulfill({ json: usuario });
    return manejar(route, path);
  });
  await page.goto('/acceso');
  await page.getByLabel('Correo electrónico', { exact: true }).fill(usuario.correo);
  await page.getByLabel('Contraseña', { exact: true }).fill('clave-de-prueba');
  await page.getByRole('button', { name: 'Entrar', exact: true }).click();
  await expect(page).toHaveURL(usuario.rol === 'ALBERGUE' ? /\/albergue$/ : /\/adoptante\/catalogo$/);
  await page.evaluate(path => {
    window.history.pushState({}, '', path);
    window.dispatchEvent(new PopStateEvent('popstate'));
  }, ruta);
}

test('el albergue guarda, reabre, corrige y retira la alerta clínica', async ({ page }) => {
  const usuario = { id: '8', correo: 'albergue@example.test', rol: 'ALBERGUE' };
  const mascota = {
    id: 1, nombre: 'Nube', especie: 'GATO', sexo: 'HEMBRA', tamanio: 'MEDIANO',
    temperamento: 'SOCIABLE', energia: 'MEDIA', edad_anos: 2, edad_meses: 0,
    descripcion: '', estado: 'DISPONIBLE', vacunas_al_dia: true,
    necesidades_especiales: 'Dieta habitual.', alerta_clinica: '',
  };
  const guardadas = [];
  await abrirPanel(page, usuario, '/albergue/mascotas', async (route, path) => {
    if (path === '/api/albergues/mi-perfil/') return route.fulfill({ json: {
      id: 1, nombre: 'Refugio', direccion: 'Lima', telefono: '999000111', email: usuario.correo,
    } });
    if (path === '/api/mascotas/1/') {
      const request = route.request();
      if (request.method() === 'PATCH') {
        expect(request.headers().authorization).toBe('Bearer token-8');
        const form = await new Response(request.postDataBuffer(), { headers: request.headers() }).formData();
        mascota.alerta_clinica = form.get('alerta_clinica');
        guardadas.push(mascota.alerta_clinica);
      }
      return route.fulfill({ json: mascota });
    }
    return route.fulfill({ json: { results: path === '/api/mascotas/' ? [mascota] : [], next: null } });
  });

  const alerta = page.getByRole('textbox', { name: 'Alerta clínica', exact: true });
  const editar = page.getByRole('button', { name: 'Editar mascota', exact: true });
  async function guardarYReabrir() {
    await page.getByRole('button', { name: 'Guardar cambios de mascota', exact: true }).click();
    await expect(page.getByText('Mascota guardada.', { exact: true })).toBeVisible();
    await editar.click();
  }

  await editar.click();
  await expect(alerta).toHaveValue('');
  await alerta.fill('Alergia a un alimento.');
  await guardarYReabrir();
  await expect(alerta).toHaveValue('Alergia a un alimento.');
  await alerta.fill('Alergia confirmada. Seguir el cuidado registrado.');
  await guardarYReabrir();
  await expect(alerta).toHaveValue('Alergia confirmada. Seguir el cuidado registrado.');
  await page.getByRole('button', { name: 'Quitar alerta clínica' }).click();
  await guardarYReabrir();
  await expect(alerta).toHaveValue('');
  await expect(page.getByRole('textbox', { name: 'Necesidades especiales', exact: true })).toHaveValue('Dieta habitual.');
  await expect(page.getByLabel('Vacunas al día')).toBeChecked();
  expect(guardadas).toEqual(['Alergia a un alimento.', 'Alergia confirmada. Seguir el cuidado registrado.', '']);
});

const textoPrivado = 'Alergia a un alimento. Seguir el cuidado indicado por el albergue.';
const fechaSolicitud = '2026-11-10T10:00:00Z';

for (const titular of [true, false]) {
  test(titular ? 'el titular ve la alerta de su adopción entregada' : 'otra cuenta no ve la alerta cuando Django deniega el acceso', async ({ page }) => {
    const usuario = { id: titular ? '17' : '18', correo: 'adoptante@example.test', rol: 'ADOPTANTE' };
    const consultadas = [];
    await abrirPanel(page, usuario, '/adoptante/solicitudes', async (route, path) => {
      if (path === '/api/solicitudes') return route.fulfill({ json: [
        { id: 42, mascota_nombre: 'Nube', estado: 'ENTREGADA', fecha_solicitud: fechaSolicitud },
        { id: 43, mascota_nombre: 'Luna', estado: 'APROBADA', fecha_solicitud: fechaSolicitud },
        { id: 44, mascota_nombre: 'Sol', estado: 'ENTREGADA', fecha_solicitud: fechaSolicitud },
      ] });
      if (path === '/api/contratos/') return route.fulfill({ json: [] });
      if (path.endsWith('/alerta-clinica/')) {
        consultadas.push(path.split('/')[4]);
        const token = route.request().headers().authorization;
        expect(token).toBe(`Bearer token-${usuario.id}`);
        if (path.includes('/42/')) return token === 'Bearer token-17'
          ? route.fulfill({ json: { alerta_clinica: textoPrivado } })
          : route.fulfill({ status: 404, json: { detail: 'No encontrado.' } });
        return route.fulfill({ json: { alerta_clinica: '' } });
      }
      return route.fulfill({ json: { items: [], total: 0 } });
    });

    const tarjeta = nombre => page.getByRole('article').filter({ has: page.getByRole('heading', { name: nombre, exact: true }) });
    const alerta = tarjeta('Nube').getByRole('region', { name: 'Alerta clínica' });
    if (titular) {
      await expect(alerta).toContainText(textoPrivado);
    } else {
      await expect(tarjeta('Nube').getByText('No se pudo consultar la alerta clínica.')).toBeVisible();
      await expect(page.getByText(textoPrivado)).toHaveCount(0);
    }
    await expect(tarjeta('Nube').getByRole('button', { name: 'Descargar ficha veterinaria' })).toBeVisible();
    await expect(tarjeta('Luna').getByRole('region', { name: 'Alerta clínica' })).toHaveCount(0);
    await expect(tarjeta('Sol').getByText('Sin alerta clínica registrada.')).toBeVisible();
    expect(consultadas.sort()).toEqual(['42', '44']);
  });
}
