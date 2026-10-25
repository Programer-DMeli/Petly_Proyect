import { test, expect } from '@playwright/test';

async function loginAsAdopter(page) {
  await page.route('http://localhost:8080/api/auth/login', route =>
    route.fulfill({ json: { token: 'token-de-prueba', expiresIn: 3600 } })
  );
  await page.route('http://localhost:8080/api/auth/me', route =>
    route.fulfill({ json: { id: '17', correo: 'adoptante@example.com', rol: 'ADOPTANTE' } })
  );

  await page.goto('/acceso');
  await page.getByLabel('Correo', { exact: true }).fill('adoptante@example.com');
  await page.getByLabel('Contraseña', { exact: true }).fill('clave-de-prueba');
  await page.getByRole('button', { name: 'Entrar', exact: true }).click();
  await expect(page).toHaveURL(/\/adoptante\/catalogo$/);
  await page.evaluate(() => {
    window.history.pushState({}, '', '/adoptante/solicitudes/42');
    window.dispatchEvent(new PopStateEvent('popstate'));
  });
}

function event(id, type, label, date, previous = null, next = null, description = '') {
  return {
    id,
    tipo: type,
    tipo_display: label,
    estado_anterior: previous,
    estado_nuevo: next,
    descripcion: description,
    ocurrido_en: date,
  };
}

test('el seguimiento ordena los eventos y actualiza el estado y el evento más reciente', async ({ page }) => {
  let requests = 0;
  await page.route('http://localhost:8080/api/solicitudes/42/seguimiento', route => {
    requests += 1;
    const payload = requests === 1
      ? {
          solicitudId: 42,
          estadoActual: 'OBSERVADA',
          eventos: [
            event(2, 'OBSERVADA', 'Solicitud observada', '2026-10-24T10:00:00Z', 'PENDIENTE', 'OBSERVADA', 'Coordinar visita.'),
            event(1, 'SOLICITUD_CREADA', 'Solicitud creada', '2026-10-24T09:00:00Z', null, 'PENDIENTE'),
          ],
        }
      : {
          solicitudId: 42,
          estadoActual: 'APROBADA',
          eventos: [
            event(3, 'APROBADA', 'Solicitud aprobada', '2026-10-24T11:00:00Z', 'OBSERVADA', 'APROBADA'),
            event(2, 'OBSERVADA', 'Solicitud observada', '2026-10-24T10:00:00Z', 'PENDIENTE', 'OBSERVADA', 'Coordinar visita.'),
            event(1, 'SOLICITUD_CREADA', 'Solicitud creada', '2026-10-24T09:00:00Z', null, 'PENDIENTE'),
          ],
        };
    return route.fulfill({ json: payload });
  });

  await loginAsAdopter(page);

  const timeline = page.locator('ol.tracking-timeline');
  await expect(timeline.locator('li strong')).toHaveText([
    'Solicitud creada',
    'Solicitud observada',
  ]);
  await page.getByRole('button', { name: 'Actualizar historial', exact: true }).click();
  await expect(page.getByText('Aprobada', { exact: true })).toBeVisible();
  await expect(timeline.locator('li strong')).toHaveText([
    'Solicitud creada',
    'Solicitud observada',
    'Solicitud aprobada',
  ]);
  await expect(timeline.locator('li').last()).toHaveClass(/tracking-timeline__event--latest/);
  expect(requests).toBe(2);
});

test('el seguimiento informa cuando la solicitud todavía no tiene eventos', async ({ page }) => {
  await page.route('http://localhost:8080/api/solicitudes/42/seguimiento', route =>
    route.fulfill({ json: { solicitudId: 42, estadoActual: 'PENDIENTE', eventos: [] } })
  );

  await loginAsAdopter(page);

  await expect(page.getByText(/Aún no hay eventos registrados/)).toBeVisible();
  await expect(page.getByRole('button', { name: 'Actualizar historial', exact: true })).toBeVisible();
});

test('el adoptante puede reintentar cuando falla la consulta del historial', async ({ page }) => {
  let requests = 0;
  await page.route('http://localhost:8080/api/solicitudes/42/seguimiento', route => {
    requests += 1;
    if (requests === 1) return route.fulfill({ status: 503, json: { detail: 'Servicio temporalmente no disponible.' } });
    return route.fulfill({ json: { solicitudId: 42, estadoActual: 'PENDIENTE', eventos: [] } });
  });

  await loginAsAdopter(page);

  await expect(page.getByRole('alert')).toContainText('Servicio temporalmente no disponible.');
  await page.getByRole('button', { name: 'Reintentar', exact: true }).click();
  await expect(page.getByText(/Aún no hay eventos registrados/)).toBeVisible();
  expect(requests).toBe(2);
});
