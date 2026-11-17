import { expect, test } from '@playwright/test';

const pet = {
  id: 12,
  nombre: 'Luna',
  albergue: 'Refugio Esperanza',
  albergueId: 7,
  especie: 'Perro',
  tamano: 'Mediano',
  edadMeses: 18,
  estado: 'Disponible',
  temperamento: 'Sociable',
  energia: 'Media',
  descripcion: 'Disfruta los paseos.',
};
const detalle = '/adoptante/catalogo/12';
const fecha = '2026-11-15T15:00:00Z';

async function entrar(page, role, path) {
  const correo = `${role.toLowerCase()}@example.test`;
  await page.goto('/acceso');
  await page.getByLabel('Correo', { exact: true }).fill(correo);
  await page.getByLabel('Contraseña', { exact: true }).fill('clave-de-prueba');
  await page.getByRole('button', { name: 'Entrar', exact: true }).click();
  await expect(page).not.toHaveURL(/\/acceso$/);
  await page.evaluate(destino => {
    window.history.pushState({}, '', destino);
    window.dispatchEvent(new PopStateEvent('popstate'));
  }, path);
}

test('envía las respuestas desde el detalle y refleja la decisión del albergue sin recargar', async ({
  browser,
  baseURL,
}) => {
  const adoptante = await browser.newPage({ baseURL, viewport: { width: 390, height: 844 } });
  const albergue = await browser.newPage({ baseURL });
  let solicitud;
  let consultasDetalle = 0;
  const eventos = [];

  async function mock(page, rol) {
    await page.route('**/api/**', async route => {
      const request = route.request();
      const { pathname, searchParams } = new URL(request.url());
      const send = (json, status = 200) => route.fulfill({ json, status });
      const addEvent = (estado, title, descripcion) =>
        eventos.push({
          id: eventos.length + 1,
          tipo: estado,
          tipo_display: title,
          estado_nuevo: estado,
          descripcion,
          ocurrido_en: fecha,
        });
      switch (pathname) {
        case '/api/auth/refresh':
          return send({ error: 'Sin sesión' }, 401);
        case '/api/auth/login':
          return send({ token: rol, expiresIn: 3600, user: { id: '1', correo: `${rol.toLowerCase()}@example.test`, rol } });
        case '/api/auth/me':
          return send({ id: '1', correo: `${rol.toLowerCase()}@example.test`, rol });
        case '/api/catalogo':
          return send({ items: [pet], total: 1 });
        case '/api/catalogo/12':
          return send(pet);
        case '/api/perfil':
          return send({ finalizado: true });
        case '/api/autorizaciones':
          return send([{ albergueId: pet.albergueId, estado: 'ACTIVA' }]);
        case '/api/solicitudes': {
          const { mascotaId, respuestasCuestionario } = request.postDataJSON();
          expect(mascotaId).toBe(pet.id);
          expect(respuestasCuestionario).toMatchObject({
            nombreCompleto: 'Camila de prueba',
            dni: '12345678',
            permisoPropietarioDetalle: 'Autorización escrita',
            tieneAlergias: false,
            planCuidado: 'Paseos diarios y controles veterinarios.',
          });
          expect(respuestasCuestionario).not.toHaveProperty('alergiasDetalle');
          solicitud = {
            id: 42,
            mascota: pet.id,
            mascota_nombre: pet.nombre,
            postulante_nombre: 'Camila',
            fecha_solicitud: fecha,
            estado: 'PENDIENTE',
            respuestas_cuestionario: respuestasCuestionario,
          };
          addEvent('SOLICITUD_CREADA', 'Solicitud creada', 'Solicitud enviada.');
          return send(solicitud, 201);
        }
        case '/api/acceso/solicitudes/':
          expect(searchParams.get('vista')).toBe('resumen');
          return send({ results: [{ ...solicitud, respuestas_cuestionario: undefined }] });
        case '/api/acceso/solicitudes/42/':
          consultasDetalle += 1;
          return send(solicitud);
        case '/api/acceso/solicitudes/42/cambiar-estado/': {
          const decision = request.postDataJSON();
          solicitud = { ...solicitud, ...decision };
          addEvent(
            decision.estado,
            decision.estado === 'APROBADA' ? 'Solicitud aprobada' : 'Solicitud observada',
            decision.observaciones_revision
          );
          return send(solicitud);
        }
        case '/api/solicitudes/42/seguimiento':
          return send({ solicitudId: 42, estadoActual: solicitud.estado, eventos });
        default:
          return send([], 404);
      }
    });
  }

  try {
    await mock(adoptante, 'ADOPTANTE');
    await mock(albergue, 'ALBERGUE');
    await entrar(adoptante, 'ADOPTANTE', detalle);
    await expect(
      adoptante.getByRole('heading', { name: 'Solicitar adopción', exact: true })
    ).toHaveCount(0);
    await adoptante.getByRole('link', { name: 'Llenar formulario de adopción' }).click();
    await expect(adoptante).toHaveURL(/\/catalogo\/12\/solicitud$/);
    const formulario = adoptante.locator('.adoption-form');
    await formulario.getByLabel('Nombre completo').fill('Camila de prueba');
    const dni = formulario.getByLabel('DNI declarado (8 dígitos)');
    await dni.fill('1234');
    expect(await dni.evaluate(input => input.validity.patternMismatch)).toBe(true);
    await dni.fill('12345678');
    await formulario.getByLabel('Dirección del hogar').fill('Dirección de prueba');
    await formulario.getByLabel('Teléfono de contacto').fill('999888777');
    await formulario.getByLabel('Propiedad de vivienda').selectOption('Alquilada');
    await formulario.getByLabel(/permiso del propietario/).fill('Autorización escrita');
    await formulario.getByLabel(/espacios tendrá/).fill('Sala y patio');
    await formulario.getByLabel(/alergia a los animales/).selectOption('true');
    await formulario.getByLabel('Detalle de alergias').fill('Respuesta que se ocultará');
    await formulario.getByLabel(/alergia a los animales/).selectOption('false');
    await formulario.getByLabel(/requieren cuidados especiales/).selectOption('false');
    await formulario.getByLabel(/Por qué quieres adoptar/).fill('Quiero darle un hogar a Luna.');
    await formulario
      .getByLabel(/organizarás sus cuidados/)
      .fill('Paseos diarios y controles veterinarios.');
    await formulario.getByLabel(/Has cuidado mascotas/).selectOption('false');
    await formulario.getByLabel(/Aceptas la esterilización/).selectOption('true');
    await formulario.getByLabel(/Aceptas el seguimiento/).selectOption('true');
    await formulario.getByLabel(/cubrir los gastos/).selectOption('true');
    await formulario.getByRole('button', { name: 'Enviar solicitud' }).click();
    await expect(adoptante).toHaveURL(/\/solicitudes\/42$/);
    await expect(adoptante.locator('.tracking-badge')).toHaveText('Pendiente');

    await entrar(albergue, 'ALBERGUE', '/albergue/solicitudes');
    const abrir = albergue.getByRole('button', { name: 'Ver solicitud de adopción' });
    await expect(abrir).toBeVisible();
    expect(consultasDetalle).toBe(0);
    await abrir.click();
    const dialogo = albergue.getByRole('dialog', { name: 'Solicitud de adopción' });
    await expect(dialogo.getByText('Autorización escrita')).toBeVisible();
    await dialogo.getByRole('button', { name: 'Rechazar', exact: true }).click();
    await expect(dialogo.getByRole('alert')).toContainText('Escribe el motivo');
    await dialogo.getByLabel('Motivo de la decisión').fill('Coordinar una visita.');
    await dialogo.getByRole('button', { name: 'Observar', exact: true }).click();
    await expect(dialogo.getByText('OBSERVADA', { exact: true })).toBeVisible();
    await expect(adoptante.locator('.tracking-badge')).toHaveText('Observada', { timeout: 12_000 });
    await expect(adoptante.getByText('Coordinar una visita.')).toBeVisible();
    await dialogo.getByLabel('Motivo de la decisión').fill('La visita fue satisfactoria.');
    await dialogo.getByRole('button', { name: 'Aprobar', exact: true }).click();
    await expect(adoptante.locator('.tracking-badge')).toHaveText('Aprobada', { timeout: 12_000 });
    await expect(adoptante.getByText('La visita fue satisfactoria.')).toBeVisible();
    await albergue.keyboard.press('Escape');
    await expect(dialogo).toHaveCount(0);
    await expect(abrir).toBeFocused();
  } finally {
    await Promise.all([adoptante.close(), albergue.close()]);
  }
});
