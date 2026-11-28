import { test, expect } from '@playwright/test';

const initialTime = new Date('2026-11-17T05:00:00Z');
const origin = 'http://localhost:5173';

async function backend(context) {
  const calls = { refresh: [], actions: [], logout: 0 };
  let available = true;
  let actionStatus = 200;
  let serial = 0;
  let refreshCookie = 'fixture-session-0';
  await context.addCookies([{ name: 'petly_refresh', value: refreshCookie, domain: 'localhost',
    path: '/api/auth', httpOnly: true, sameSite: 'Lax' }]);
  await context.route('**/api/**', async route => {
    const req = route.request();
    const path = new URL(req.url()).pathname;
    const headers = {
      'Access-Control-Allow-Origin': origin,
      'Access-Control-Allow-Credentials': 'true',
      'Access-Control-Allow-Methods': 'POST, GET, OPTIONS',
      'Access-Control-Allow-Headers': 'authorization, content-type, x-petly-client',
    };
    if (req.method() === 'OPTIONS') return route.fulfill({ status: 204, headers });
    if (path === '/api/auth/refresh') {
      calls.refresh.push(req.postDataJSON());
      if (!available) return route.abort('internetdisconnected');
      if (!String(await req.headerValue('cookie')).includes(refreshCookie)) {
        return route.fulfill({ status: 401, headers, json: { error: 'Sesión inválida' } });
      }
      refreshCookie = `fixture-session-${++serial}`;
      return route.fulfill({ status: 200,
        headers: { ...headers, 'Set-Cookie': `petly_refresh=${refreshCookie}; Path=/api/auth; HttpOnly; SameSite=Lax` },
        json: { token: `access-${serial}`, expiresIn: 3600, user: { id: 4, rol: 'ADMIN', correo: 'admin@petly.test' } } });
    }
    if (path === '/api/auth/logout') {
      calls.logout += 1;
      return route.fulfill({ status: 204, headers: {
        ...headers, 'Set-Cookie': 'petly_refresh=; Path=/api/auth; HttpOnly; Max-Age=0',
      } });
    }
    if (path === '/api/test-renovacion') {
      calls.actions.push({ method: req.method(), authorization: await req.headerValue('authorization') });
      return route.fulfill({ status: actionStatus, headers, json: {} });
    }
    if (path === '/api/albergues/') {
      return route.fulfill({ headers, json: { results: [], count: 0, next: null, previous: null } });
    }
    return route.fulfill({ headers, json: [] });
  });
  return {
    calls,
    disconnect: () => { available = false; },
    reconnect: () => { available = true; },
    rejectAction: () => { actionStatus = 401; },
  };
}

async function ready(page) {
  await expect(page.getByRole('button', { name: 'Abrir menú de usuario' })).toBeVisible();
}

async function perform(page, method = 'GET') {
  return page.evaluate(async method => {
    const { spring } = await import('/src/services/http.ts');
    try {
      await spring('/api/test-renovacion', { method, ...(method === 'POST' ? { body: '{}' } : {}) });
      return true;
    } catch {
      return false;
    }
  }, method);
}

test('restaura al recargar y comparte una renovación entre peticiones y pestañas', async ({ page, context }) => {
  const { calls } = await backend(context);
  await page.clock.setFixedTime(initialTime);
  await page.goto('/acceso');
  await ready(page);
  expect(calls.refresh).toHaveLength(1);
  await page.reload();
  await ready(page);
  expect(calls.refresh).toHaveLength(2);

  const second = await context.newPage();
  await second.clock.setFixedTime(initialTime);
  await second.goto('/');
  await expect(second.getByRole('heading', { name: 'Tu próximo mejor amigo te espera', level: 1 })).toBeVisible();
  await second.getByRole('link', { name: 'Ir a iniciar sesión' }).last().click();
  await ready(second);
  await expect(second).toHaveURL('/administracion/albergues');
  expect(calls.refresh).toHaveLength(2);
  const nearExpiry = new Date(initialTime.getTime() + 3_550_000);
  await Promise.all([page.clock.setFixedTime(nearExpiry), second.clock.setFixedTime(nearExpiry)]);
  expect(await Promise.all([perform(page), perform(page), perform(second)])).toEqual([true, true, true]);
  expect(calls.refresh).toHaveLength(3);
  expect(calls.actions.map(action => action.authorization)).toEqual(Array(3).fill('Bearer access-3'));
  await expect(page).toHaveURL('/administracion/albergues');
  expect(await page.evaluate(() => Object.values(localStorage).some(value => value.includes('access-')))).toBe(false);
  expect((await context.cookies()).find(cookie => cookie.name === 'petly_refresh')?.httpOnly).toBe(true);
});

test('permite reintentar tras una caída y jamás vuelve a enviar un POST rechazado', async ({ page, context }) => {
  const mock = await backend(context);
  await page.clock.setFixedTime(initialTime);
  await page.goto('/acceso');
  await ready(page);
  const expired = initialTime.getTime() + 3_700_000;
  await page.clock.setFixedTime(expired);
  mock.disconnect();
  expect(await perform(page, 'POST')).toBe(false);
  await ready(page);
  expect(mock.calls.actions).toHaveLength(0);
  mock.reconnect();
  await page.clock.setFixedTime(expired + 2_001);
  expect(await perform(page, 'POST')).toBe(true);
  expect(mock.calls.refresh[1].requestId).toBe(mock.calls.refresh[2].requestId);

  mock.rejectAction();
  expect(await perform(page, 'POST')).toBe(false);
  expect(mock.calls.actions.filter(action => action.method === 'POST')).toHaveLength(2);
});

test('cierra la sesión en todas las pestañas y elimina la cookie de renovación', async ({ page, context }) => {
  const { calls } = await backend(context);
  await page.goto('/acceso');
  await ready(page);
  const second = await context.newPage();
  await second.goto('/acceso');
  await ready(second);
  await page.getByRole('button', { name: 'Abrir menú de usuario' }).click();
  await page.getByRole('button', { name: /Cerrar sesión/ }).click();
  await expect(second.getByRole('button', { name: 'Abrir menú de usuario' })).toHaveCount(0);
  await page.reload();
  await expect(page.getByRole('button', { name: 'Entrar' })).toBeVisible();
  expect(calls.logout).toBe(1);
  expect((await context.cookies()).some(cookie => cookie.name === 'petly_refresh')).toBe(false);
});
