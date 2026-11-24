import { expect, test } from '@playwright/test';

test('Mensajes recupera el hilo sin volver a cargar la lista en cada sondeo', async ({ page }) => {
  const base = '/api/mensajeria/conversaciones/';
  const reply = { token: 'sesion-sintetica', expiresIn: 3600, user: { id: '7', correo: 'prueba@example.test', rol: 'ADOPTANTE' } };
  const conversation = {
    id: 42, solicitud_id: null, mascota_id: null, mascota: 'Consulta general', nombre_contacto: 'Refugio de prueba',
    no_leidos: 0, promedio_respuesta_horas: null, ultimo_mensaje: null,
    bloqueada: false, bloqueada_por_mi: false, puede_enviar: true,
  };
  let lists = 0;
  let threads = 0;
  let polls = 0;
  let readWrites = 0;
  await page.route('**/api/**', async route => {
    const url = new URL(route.request().url());
    const send = (json, status = 200) => route.fulfill({ status, json });
    switch (url.pathname) {
      case '/api/auth/refresh': return send(reply);
      case '/api/auth/me': return send(reply.user);
      case '/api/acceso/notificaciones/': return send({ results: [], no_leidas: 0 });
      case '/api/acceso/notificaciones/eventos/': return route.fulfill({ status: 204 });
      case base + 'no-leidos/': return send({ no_leidos: 0 });
      case base:
        expect(url.searchParams.get('resumen')).toBe('1');
        lists++;
        return send([conversation]);
      case base + '42/':
        threads++;
        if (threads === 1) return send({ detail: 'No se pudo comprobar la sesión. Intenta nuevamente.' }, 503);
        return send({
          conversacion: { ...conversation, promedio_respuesta_horas: 1.2 },
          mensajes: [{ id: 1, propio: false, texto: 'Mensaje de prueba', creado_en: '2026-11-24T10:00:00Z', adjuntos: [] }],
        });
      case base + '42/mensajes/':
        polls++;
        expect(url.searchParams.get('despues_de_id')).toBe('1');
        return send({ mensajes: [], leidos_hasta_id: null });
      case base + '42/leer/':
        readWrites++;
        return route.fulfill({ status: 204 });
      default: return send([]);
    }
  });

  await page.clock.install();
  await page.goto('/adoptante/mensajes');
  await expect(page.getByText('Volviendo a intentar cargar la conversación…')).toBeVisible();
  const initialLists = lists;
  await page.clock.runFor(5100);
  await expect(page.getByText('Mensaje de prueba', { exact: true })).toBeVisible();
  await expect(page.getByText('No se pudo comprobar la sesión. Intenta nuevamente.')).toHaveCount(0);
  await page.clock.runFor(5100);
  await expect.poll(() => polls).toBe(1);
  expect(threads).toBe(2);
  expect(lists).toBe(initialLists);
  expect(readWrites).toBe(0);
  await page.clock.runFor(5100);
  await expect.poll(() => lists).toBe(initialLists + 1);
});
