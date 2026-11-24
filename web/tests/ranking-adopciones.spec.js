import { expect, test } from '@playwright/test';

const analitica = '/api/acceso/analitica/adopciones/';
const rankingApi = `${analitica}ranking/`;
const gato = { especie: 'GATO', nombre_especie: 'Gato' };
const perro = { especie: 'PERRO', nombre_especie: 'Perro' };

function conteo(mascotas_ingresadas, mascotas_entregadas, tasa_adopcion, dias_promedio) {
  return { mascotas_ingresadas, mascotas_entregadas, tasa_adopcion, dias_promedio };
}

async function abrirAnalitica(page, resolverRanking, exportaciones = []) {
  const usuario = { id: '2', correo: 'admin@example.test', rol: 'ADMIN' };
  await page.route('**/api/**', async route => {
    const url = new URL(route.request().url());
    const path = url.pathname;
    if (path === '/api/auth/refresh') return route.fulfill({ status: 401 });
    if (path === '/api/auth/login') return route.fulfill({ json: { token: 'token-admin', expiresIn: 3600, user: usuario } });
    if (path === '/api/auth/me') return route.fulfill({ json: usuario });
    if (path === '/api/albergues/cuentas-disponibles/') return route.fulfill({ json: [] });
    if (path === rankingApi) {
      expect(route.request().headers().authorization).toBe('Bearer token-admin');
      return resolverRanking(route, url.searchParams);
    }
    if (path === analitica) return route.fulfill({ json: { total_adopciones: 3, por_mes: [], por_especie: [] } });
    if (path === `${analitica}exportar/`) {
      exportaciones.push(Object.fromEntries(url.searchParams));
      return route.fulfill({ contentType: 'application/octet-stream', body: 'Contenido simulado' });
    }
    return route.fulfill({ json: { count: 0, results: [], next: null } });
  });

  await page.goto('/acceso');
  await page.getByLabel('Correo', { exact: true }).fill(usuario.correo);
  await page.getByLabel('Contraseña', { exact: true }).fill('clave-de-prueba');
  await page.getByRole('button', { name: 'Entrar', exact: true }).click();
  await expect(page).toHaveURL(/\/administracion\/albergues$/);
  await page.getByRole('button', { name: 'Gestión', exact: true }).click();
  await page.getByRole('link', { name: /Analítica de adopciones/ }).click();
  await expect(page).toHaveURL(/\/administracion\/analitica\/adopciones$/);
  return page.getByRole('region', { name: 'Ranking de adopción' });
}

test('muestra la tasa, cambia el grupo y reutiliza el periodo al exportar', async ({ page }) => {
  const consultas = [];
  const exportaciones = [];
  const ranking = await abrirAnalitica(page, (route, filtros) => {
    const agrupacion = filtros.get('agrupacion');
    const desde = filtros.get('desde');
    const hasta = filtros.get('hasta');
    consultas.push({ agrupacion, desde, hasta });
    const grupos = agrupacion === 'especie' ? [
      { ...gato, raza: null, ...conteo(4, 1, 25, 4) },
      { ...perro, raza: null, ...conteo(4, 2, 50, 4.5) },
    ] : desde ? [
      { ...perro, raza: 'Labrador', ...conteo(1, 1, 100, 8) },
    ] : [
      { ...gato, raza: null, ...conteo(1, 0, 0, null) },
      { ...perro, raza: 'Labrador', ...conteo(3, 2, 66.67, 4.5) },
    ];
    return route.fulfill({ json: { grupos, hasta: hasta ?? '2026-11-23', totales: conteo(8, 3, 37.5, 4.3) } });
  }, exportaciones);

  await expect(ranking.locator('tbody tr').first()).toContainText('Gato');
  await expect(ranking.locator('tbody tr').first()).toContainText(/25\s*%/);
  await expect(ranking.locator('tbody tr').nth(1)).toContainText(/50\s*%/);
  await expect(ranking.getByRole('columnheader', { name: 'Tasa de adopción' })).toHaveAttribute('aria-sort', 'ascending');
  await ranking.getByLabel('Agrupar por').selectOption('raza');
  await expect(ranking.getByRole('rowheader', { name: 'Sin identificar' })).toBeVisible();
  await expect(ranking.locator('tbody tr').first()).toContainText(/0\s*%/);
  await expect(ranking.locator('tbody tr').first()).toContainText('Sin datos');

  await page.getByLabel('Desde', { exact: true }).fill('2026-01-01');
  await page.getByLabel('Hasta', { exact: true }).fill('2026-01-10');
  expect(consultas.at(-1)).toEqual({ agrupacion: 'raza', desde: null, hasta: null });
  await page.getByRole('button', { name: 'Aplicar periodo' }).click();
  await expect(ranking.locator('tbody tr')).toHaveCount(1);
  await expect(ranking.getByRole('rowheader', { name: 'Labrador' })).toBeVisible();
  expect(consultas.at(-1)).toEqual({ agrupacion: 'raza', desde: '2026-01-01', hasta: '2026-01-10' });

  for (const [nombre, formato] of [['Excel', 'xlsx'], ['PDF', 'pdf']]) {
    const evento = page.waitForEvent('download');
    await page.getByRole('button', { name: `Descargar ${nombre}`, exact: true }).click();
    expect((await evento).suggestedFilename()).toBe(`reporte-adopciones-2026-01-01-a-2026-01-10.${formato}`);
    expect(exportaciones.at(-1)).toEqual({ formato, desde: '2026-01-01', hasta: '2026-01-10' });
  }
});

test('muestra un error del ranking y permite reintentar hasta obtener un periodo vacío', async ({ page }) => {
  let intentos = 0;
  const ranking = await abrirAnalitica(page, route => {
    if (++intentos === 1) return route.fulfill({ status: 503, json: { detail: 'Ranking temporalmente indisponible.' } });
    return route.fulfill({ json: { grupos: [], hasta: '2026-11-23', totales: conteo(0, 0, null, null) } });
  });
  await expect(ranking.getByRole('alert')).toContainText('Ranking temporalmente indisponible.');
  await ranking.getByRole('button', { name: 'Reintentar ranking' }).click();
  await expect(ranking.getByText(/Sin datos.*No hubo ingresos/)).toBeVisible();
  await expect(page.getByRole('button', { name: 'Descargar Excel' })).toBeEnabled();
});
