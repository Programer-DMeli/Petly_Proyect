import { expect, test } from '@playwright/test';

const perfil = '/api/albergues/mi-perfil/';
const conversionApi = `${perfil}metricas/conversion/`;
const permanenciaApi = `${perfil}metricas/permanencia/`;

async function abrirPanel(page, responderConversion) {
  const usuario = { id: '8', correo: 'albergue@example.test', rol: 'ALBERGUE' };
  await page.route('**/api/**', async route => {
    const url = new URL(route.request().url());
    const path = url.pathname;
    if (path === '/api/auth/refresh') return route.fulfill({ status: 401 });
    if (path === '/api/auth/login') return route.fulfill({ json: { token: 'conversion-prueba', expiresIn: 3600, user: usuario } });
    if (path === '/api/auth/me') return route.fulfill({ json: usuario });
    if (path === perfil) return route.fulfill({ json: {
      id: 3, nombre: 'Refugio', direccion: 'Lima', descripcion: '', telefono: '', email: '', capacidad_maxima: 10,
      responsable_nombre: 'Ana', responsable_email: usuario.correo, responsable_telefono: '',
    } });
    if (path === permanenciaApi) return route.fulfill({ json: { dias_promedio: 12, mascotas_entregadas: 2 } });
    if (path === conversionApi) {
      expect(route.request().headers().authorization).toBe('Bearer conversion-prueba');
      expect(url.search).toBe('');
      return responderConversion(route);
    }
    return route.fulfill({ json: { results: [], next: null } });
  });

  await page.goto('/acceso');
  await page.getByLabel('Correo electrónico', { exact: true }).fill(usuario.correo);
  await page.getByLabel('Contraseña', { exact: true }).fill('clave-de-prueba');
  await page.getByRole('button', { name: 'Entrar', exact: true }).click();
  await expect(page).toHaveURL(/\/albergue$/);
  return page.getByRole('region', { name: 'Conversión de solicitudes' });
}

function cifras(recibidas, aprobadas, tasa) {
  return { solicitudes_recibidas: recibidas, solicitudes_aprobadas: aprobadas, tasa_conversion: tasa };
}

test('presenta carga, recibidas, aprobadas y conversión junto a permanencia', async ({ page }) => {
  let responder;
  const pendiente = new Promise(resolve => { responder = resolve; });
  const conversion = await abrirPanel(page, async route => {
    await pendiente;
    return route.fulfill({ json: cifras(5, 2, 40) });
  });
  await expect(conversion.getByRole('status')).toHaveText('Cargando tasa de conversión…');
  responder();

  await expect(conversion.locator('dt')).toHaveText(['Solicitudes recibidas', 'Solicitudes aprobadas', 'Tasa de conversión']);
  await expect(conversion.locator('dd')).toHaveText(['5', '2', /40\s*%/]);
  const permanencia = page.getByRole('region', { name: 'Permanencia promedio' });
  await expect(permanencia.getByText('12 días')).toBeVisible();
  await expect(permanencia.getByText('Promedio de 2 mascotas entregadas.')).toBeVisible();
});

test('reintenta después de un error y distingue un albergue sin solicitudes', async ({ page }) => {
  let intentos = 0;
  const conversion = await abrirPanel(page, route => {
    if (++intentos === 1) return route.fulfill({ status: 503, json: { detail: 'No se pudo consultar la conversión.' } });
    return route.fulfill({ json: cifras(0, 0, null) });
  });
  await expect(conversion.getByRole('alert')).toHaveText('No se pudo consultar la conversión.');
  await conversion.getByRole('button', { name: 'Reintentar conversión' }).click();
  await expect(conversion.locator('dd')).toHaveText(['0', '0', 'Sin datos']);
  await expect(page.getByRole('region', { name: 'Permanencia promedio' }).getByText('12 días')).toBeVisible();
});

test('muestra cero por ciento si hay solicitudes y ninguna aprobada', async ({ page }) => {
  const conversion = await abrirPanel(page, route => route.fulfill({ json: cifras(3, 0, 0) }));
  await expect(conversion.locator('dd')).toHaveText(['3', '0', /0\s*%/]);
});
