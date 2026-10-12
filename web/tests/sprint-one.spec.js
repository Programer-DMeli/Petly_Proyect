import { test, expect } from '@playwright/test';
import fs from 'node:fs';
import crypto from 'node:crypto';
const accounts = JSON.parse(
  fs.readFileSync(process.env['PETLY_DEMO_FILE'] ?? '/tmp/petly-demo-cuentas.json', 'utf8')
);
const photo = Buffer.from(
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=',
  'base64'
);
async function login(page, role) {
  await page.goto('/acceso');
  await page.getByLabel('Correo', { exact: true }).fill(accounts[role].correo);
  await page.getByLabel('Contraseña', { exact: true }).fill(accounts[role].password);
  for (let attempt = 0; attempt < 3; attempt++) {
    const response = page.waitForResponse(r => r.url().includes('/api/auth/login'));
    await page.getByRole('button', {name:'Entrar',exact:true}).click();
    const result = await response;
    if (result.status() !== 429) { expect(result.status()).toBe(200); break; }
    if (attempt === 2) throw new Error('La cuota de acceso sigue ocupada por otra ejecución.');
    await page.waitForTimeout(61000);
  }
  await expect(page.getByRole('button', { name: 'Cerrar sesión' })).toBeVisible();
}
test('Sprint 1 integrado: tres roles, CRUD, perfil, consentimiento, catálogo y recuperación', async ({
  browser,
}) => {
  const errors = [];
  const contexts = await Promise.all([
    browser.newContext({ baseURL: 'http://localhost:5173' }),
    browser.newContext({ baseURL: 'http://localhost:5173' }),
    browser.newContext({ baseURL: 'http://localhost:5173' }),
  ]);
  const [adopt, shelter, admin] = await Promise.all(contexts.map(c => c.newPage()));
  for (const page of [adopt, shelter, admin]) page.on('pageerror', e => errors.push(e.message));
  const suffix = crypto.randomBytes(4).toString('hex');
  const refuge = 'Refugio Web ' + suffix;
  const pet = 'Luna Web ' + suffix;
  const area = 'Patio principal ' + suffix;
  const petRow = shelter.locator('article.list-row').filter({has: shelter.getByRole('heading', {name:pet,exact:true})});
  const areaRow = shelter.locator('article.list-row').filter({has: shelter.getByRole('heading', {name:area,exact:true})});
  // Albergue autentica en Spring y guarda datos institucionales/mascotas en Django.
  await login(shelter, 'ALBERGUE');
  await shelter.getByLabel('Nombre del albergue', { exact: true }).fill(refuge);
  await shelter.getByLabel('Dirección', { exact: true }).fill('Lima, Perú');
  await shelter.getByLabel('Capacidad máxima', { exact: true }).fill('30');
  await shelter.getByLabel('Nombre del responsable', { exact: true }).fill('Responsable de prueba');
  const createShelter = shelter.getByRole('button', { name: 'Registrar albergue', exact: true });
  if (await createShelter.count()) await createShelter.click();
  else await shelter.getByRole('button', { name: 'Actualizar albergue', exact: true }).click();
  await expect(shelter.getByText('Información del albergue guardada.')).toBeVisible();
  await shelter.getByLabel('Nombre del área').fill(area);
  await shelter.getByLabel('Capacidad del área').fill('10');
  await shelter.getByRole('button', { name: 'Agregar área' }).click();
  await expect(
    shelter.getByRole('heading', { name: area, exact: true })
  ).toBeVisible();
  await areaRow.getByRole('button', { name: 'Editar área', exact: true }).click();
  await shelter.getByLabel('Capacidad del área').fill('12');
  await shelter.getByRole('button', { name: 'Actualizar área', exact: true }).click();
  await expect(areaRow.getByText('PATIO · 12 · Activa')).toBeVisible();
  await shelter.getByLabel('Nombre de la mascota').fill(pet);
  await shelter.getByLabel('Edad: años', { exact: true }).fill('2');
  await shelter.getByLabel('Edad: meses adicionales').fill('3');
  await shelter
    .locator('input[type=file]')
    .setInputFiles({ name: 'luna.png', mimeType: 'image/png', buffer: photo });
  await shelter.getByRole('button', { name: 'Registrar mascota', exact: true }).click();
  await expect(shelter.getByRole('heading', { name: pet, exact: true })).toBeVisible();
  await petRow.getByRole('button', { name: 'Editar mascota', exact: true }).click();
  await shelter
    .getByLabel('Descripción de la mascota')
    .fill('Mascota de prueba con comportamiento por evaluar.');
  await shelter.getByRole('button', { name: 'Guardar cambios de mascota', exact: true }).click();
  await expect(shelter.getByText('Mascota guardada.')).toBeVisible();
  // Adoptante recibe datos reales desde Spring, guarda perfil y concede permiso explícito.
  await login(adopt, 'ADOPTANTE');
  await adopt.getByRole('link', { name: 'Cuestionario', exact: true }).click();
  await adopt.getByLabel('Vivienda', { exact: true }).fill('Departamento');
  await adopt.getByLabel('¿Viven niños en tu hogar?').selectOption('false');
  await adopt.getByRole('button', { name: 'Guardar borrador', exact: true }).click();
  await expect(adopt.getByText('Respuestas guardadas.')).toBeVisible();
  await adopt.getByLabel('Horas fuera de casa por día').fill('8.5');
  await adopt.getByLabel('Presupuesto mensual (S/)').fill('150.25');
  await adopt.getByLabel('¿Tienes otras mascotas?').selectOption('true');
  await adopt.getByRole('button', { name: 'Finalizar cuestionario' }).click();
  await expect(adopt.getByText('Cuestionario finalizado.')).toBeVisible();
  await adopt.getByRole('link', { name: 'Catálogo', exact: true }).click();
  await adopt.getByLabel('Buscar mascota o albergue').fill(pet);
  await adopt.getByLabel('Edad mínima (meses)').fill('27');
  await adopt.getByLabel('Edad máxima (meses)').fill('27');
  await adopt.getByRole('button', { name: 'Buscar', exact: true }).click();
  await expect(adopt.getByRole('heading', { name: pet, exact: true })).toBeVisible();
  await expect(adopt.getByRole('img', { name: pet, exact: true })).toBeVisible();
  await expect
    .poll(() =>
      adopt.getByRole('img', { name: pet, exact: true }).evaluate(img => img.naturalWidth)
    )
    .toBeGreaterThan(0);
  await adopt.locator('article.pet').filter({has:adopt.getByRole('heading',{name:pet,exact:true})}).getByRole('link',{name:'Ver detalle',exact:true}).click();
  await expect(adopt.getByText('Mascota de prueba con comportamiento por evaluar.')).toBeVisible();
  await adopt.getByRole('link', { name: 'Autorizaciones', exact: true }).click();
  const consentRow = adopt
    .locator('article')
    .filter({ has: adopt.getByRole('heading', { name: refuge, exact: true }) });
  await consentRow.getByRole('button', { name: 'Autorizar', exact: true }).click();
  await adopt.getByRole('button', { name: 'Sí, autorizo compartir' }).click();
  await expect(consentRow.getByRole('button', { name: 'Revocar' })).toBeVisible();
  await shelter.getByRole('button', { name: 'Actualizar autorizaciones' }).click();
  await shelter.getByRole('button', { name: 'Consultar perfil' }).click();
  await expect(shelter.getByText('Departamento', { exact: true })).toBeVisible();
  await consentRow.getByRole('button', { name: 'Revocar' }).click();
  await expect(consentRow.getByRole('button', { name: 'Autorizar', exact: true })).toBeVisible();
  await shelter.getByRole('button', { name: 'Actualizar autorizaciones' }).click();
  await expect(shelter.getByText('No hay perfiles autorizados.')).toBeVisible();
  // ADMIN consulta todas las instituciones aunque no tenga is_staff Django.
  await login(admin, 'ADMIN');
  await expect(admin.getByRole('heading', { name: refuge, exact: true })).toBeVisible();
  await expect(admin.getByText('Responsable de prueba', { exact: true })).toBeVisible();
  await admin.getByRole('link', { name: 'Albergues', exact: true }).click();
  // Corrección manual de estados y reflejo en catálogo web.
  shelter.on('dialog', dialog => dialog.accept());
  await petRow.getByRole('button', { name: 'Cambiar a ADOPTADO', exact: true }).click();
  await expect(shelter.getByText('Disponibilidad actualizada.')).toBeVisible();
  await petRow.getByRole('button', { name: 'Cambiar a DISPONIBLE', exact: true }).click();
  await expect(petRow.getByText('DISPONIBLE', { exact: true })).toBeVisible();
  await adopt.getByRole('link', { name: 'Catálogo', exact: true }).click();
  await adopt.getByLabel('Buscar mascota o albergue').fill(pet);
  await adopt.getByRole('button', { name: 'Buscar', exact: true }).click();
  await expect(adopt.locator('article.pet').filter({has:adopt.getByRole('heading',{name:pet,exact:true})}).getByText('Disponible',{exact:true})).toBeVisible();
  // Recuperación real: solicitar desde web, consumir correo SMTP y cambiar contraseña por enlace.
  await adopt.getByRole('button', { name: 'Cerrar sesión' }).click();
  await adopt.getByRole('link', { name: 'Recuperar contraseña', exact: true }).click();
  await adopt.getByLabel('Correo de tu cuenta').fill(accounts.ADOPTANTE.correo);
  const resetResponse = adopt.waitForResponse(r => r.url().includes('/api/auth/password/reset?'));
  await adopt.getByRole('button', { name: 'Enviar enlace', exact: true }).click();
  if ((await resetResponse).status() === 429) {
    await adopt.waitForTimeout(61000);
    await adopt.getByRole('button', { name: 'Enviar enlace', exact: true }).click();
  }
  await expect(
    adopt.getByText('Si el correo existe, recibirás instrucciones.', { exact: false })
  ).toBeVisible();
  const mail = fs
    .readFileSync('/tmp/petly-correo-prueba.eml', 'utf8')
    .replace(/=\r?\n/g, '')
    .replace(/=3D/g, '=');
  let url = mail.match(/http:\/\/localhost:5173\/recuperacion\?token=[A-Za-z0-9_-]+/)?.[0];
  if (!url) {
    // JavaMail puede codificar el cuerpo como base64.
    const content = Buffer.from(
      mail
        .split(/\r?\n\r?\n/)
        .slice(1)
        .join('\n')
        .replace(/\s/g, ''),
      'base64'
    ).toString();
    url = content.match(/http:\/\/localhost:5173\/recuperacion\?token=[A-Za-z0-9_-]+/)?.[0];
  }
  expect(Boolean(url)).toBe(true);
  await adopt.goto(url);
  await expect(adopt.getByLabel('Código de recuperación')).not.toHaveValue('');
  await expect(adopt).toHaveURL('/recuperacion');
  const newPassword = crypto.randomBytes(16).toString('hex');
  await adopt.getByLabel('Nueva contraseña', { exact: true }).fill(newPassword);
  await adopt.getByLabel('Confirmar nueva contraseña', { exact: true }).fill(newPassword);
  await adopt.getByRole('button', { name: 'Cambiar contraseña', exact: true }).click();
  await expect(
    adopt.getByText('Contraseña actualizada. Inicia sesión con tu nueva contraseña.')
  ).toBeVisible();
  accounts.ADOPTANTE.password = newPassword;
  fs.writeFileSync(process.env['PETLY_DEMO_FILE'] ?? '/tmp/petly-demo-cuentas.json',JSON.stringify(accounts),{mode:0o600});
  await login(adopt, 'ADOPTANTE');
  await adopt.getByRole('link', { name: 'Cuestionario', exact: true }).click();
  await expect(adopt.getByLabel('Vivienda', { exact: true })).toHaveValue('Departamento');
  await expect(adopt.getByLabel('¿Viven niños en tu hogar?')).toHaveValue('false');
  // Eliminar mascota y área desde su propietario, y comprobar catálogo vacío para esta mascota.
  await petRow.getByRole('button', { name: 'Eliminar mascota', exact: true }).click();
  await expect(shelter.getByText('Mascota eliminada.')).toBeVisible();
  await areaRow.getByRole('button', { name: 'Eliminar área', exact: true }).click();
  await expect(shelter.getByRole('heading', { name: area, exact: true })).toHaveCount(
    0
  );
  await adopt.getByRole('link', { name: 'Catálogo', exact: true }).click();
  await adopt.getByLabel('Buscar mascota o albergue').fill(pet);
  await adopt.getByRole('button', { name: 'Buscar', exact: true }).click();
  await expect(adopt.getByText('No hay coincidencias. Prueba otros filtros.')).toBeVisible();
  expect(errors).toEqual([]);
  // Se conserva la cuenta actualizada en el archivo privado para repetir la demostración.
  fs.writeFileSync(
    process.env['PETLY_DEMO_FILE'] ?? '/tmp/petly-demo-cuentas.json',
    JSON.stringify(accounts),
    { mode: 0o600 }
  );
  for (const context of contexts) await context.close();
});

test('Registro público, duplicado, rutas privadas y presentación responsive', async ({ page }) => {
  await page.goto('/adoptante/cuestionario');
  await expect(page).toHaveURL('/acceso');
  await page.goto('/registro');
  const correo = 'registro-web-' + crypto.randomBytes(6).toString('hex') + '@example.test';
  const password = crypto.randomBytes(16).toString('hex');
  await page.getByLabel('Correo', { exact: true }).fill(correo);
  await page.getByLabel('Contraseña', { exact: true }).fill(password);
  await page.getByLabel('Confirmar contraseña', { exact: true }).fill(password);
  await page.getByRole('button', { name: 'Crear cuenta', exact: true }).click();
  await expect(page.getByText('Cuenta creada. Ya puedes iniciar sesión.')).toBeVisible();
  await page.getByLabel('Contraseña', { exact: true }).fill(password);
  await page.getByLabel('Confirmar contraseña', { exact: true }).fill(password);
  await page.getByRole('button', { name: 'Crear cuenta', exact: true }).click();
  await expect(page.getByRole('alert')).toContainText('registrado');
  await page.goto('/acceso');
  await page.setViewportSize({ width: 390, height: 844 });
  await expect(page.getByRole('heading', { name: 'Bienvenido a Petly' })).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(
    true
  );
  await page.screenshot({ path: '../docs/evidencias/react-acceso-mobile.png', fullPage: true });
  await page.setViewportSize({ width: 1365, height: 900 });
  await page.goto('/');
  await expect(page.getByRole('heading', { name: 'Un hogar cambia una vida' })).toBeVisible();
  await page.screenshot({ path: '../docs/evidencias/react-inicio.png', fullPage: true });
});

test('Paginación conserva filtros y detalle con datos reales', async ({ page, request }) => {
  const suffix = crypto.randomBytes(5).toString('hex');
  let response = await request.post('http://localhost:8080/api/auth/login', {
    data: { correo: accounts.ALBERGUE.correo, password: accounts.ALBERGUE.password },
  });
  if (response.status() === 429) {
    await page.waitForTimeout(61000);
    response = await request.post('http://localhost:8080/api/auth/login', {
      data: { correo: accounts.ALBERGUE.correo, password: accounts.ALBERGUE.password },
    });
  }
  expect(response.status()).toBe(200);
  const { token } = await response.json();
  const ids = [];
  try {
    for (let n = 0; n < 7; n++) {
      const created = await request.post('http://localhost:8000/api/mascotas/', {
        headers: { Authorization: `Bearer ${token}` },
        multipart: {
          nombre: `Gato ${suffix} ${n}`,
          especie: 'GATO',
          sexo: 'MACHO',
          tamanio: 'PEQUENO',
          temperamento: 'POR_EVALUAR',
          energia: 'POR_EVALUAR',
          edad_anos: '1',
          edad_meses: '0',
          foto_principal: { name: 'gato.png', mimeType: 'image/png', buffer: photo },
        },
      });
      expect(created.status()).toBe(201);
      ids.push((await created.json()).id);
    }
    await login(page, 'ADOPTANTE');
    await page.getByLabel('Buscar mascota o albergue').fill(suffix);
    await page.getByLabel('Especie', { exact: true }).selectOption('Gato');
    await page.getByLabel('Tamaño', { exact: true }).selectOption('Pequeño');
    await page.getByLabel('Temperamento', { exact: true }).selectOption('Por evaluar');
    await page.getByLabel('Edad mínima (meses)').fill('12');
    await page.getByLabel('Edad máxima (meses)').fill('12');
    await page.getByRole('button', { name: 'Buscar', exact: true }).click();
    await expect(page.getByText('7 mascotas encontradas')).toBeVisible();
    await expect(page.getByRole('link', { name: 'Ver detalle', exact: true })).toHaveCount(6);
    await page.getByRole('button', { name: 'Siguiente', exact: true }).click();
    await expect(page.getByText('Página 2 de 2')).toBeVisible();
    await expect(page.getByRole('link', { name: 'Ver detalle', exact: true })).toHaveCount(1);
    await expect(page.getByLabel('Buscar mascota o albergue')).toHaveValue(suffix);
    await expect(page.getByLabel('Especie', { exact: true })).toHaveValue('Gato');
    await page.getByRole('link', { name: 'Ver detalle', exact: true }).click();
    await expect(
      page.getByRole('heading', { name: `Gato ${suffix} 6`, exact: true })
    ).toBeVisible();
    await expect(page.getByText('Temperamento: Por evaluar · Energía: Por evaluar')).toBeVisible();
  } finally {
    for (const id of ids)
      await request.delete(`http://localhost:8000/api/mascotas/${id}/`, {
        headers: { Authorization: `Bearer ${token}` }, timeout: 3000,
      }).catch(() => { /* no ocultar el fallo principal con un error de limpieza */ });
  }
});
