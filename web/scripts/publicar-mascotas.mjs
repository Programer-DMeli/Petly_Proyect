/** Publica las imágenes suministradas usando el formulario real del panel ALBERGUE.
 * Credenciales privadas de demostración en PETLY_DEMO_FILE. No imprime tokens/passwords.
 */
import { chromium, expect } from '@playwright/test';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const account = JSON.parse(
  fs.readFileSync(process.env['PETLY_DEMO_FILE'] ?? '/tmp/petly-demo-cuentas.json', 'utf8')
).ALBERGUE;
const animals = [
  [
    'luna_01.jpg',
    'Luna',
    'PERRO',
    'Luna busca un hogar donde recibir cuidado y compañía. Consulta al albergue para conocerla mejor.',
  ],
  [
    'mascota_2.jpg',
    'Toby',
    'PERRO',
    'Toby espera una familia que le ofrezca tiempo, atención y un lugar seguro.',
  ],
  [
    'mascota_3.png',
    'Bruno',
    'PERRO',
    'Bruno busca una nueva oportunidad. El albergue te orientará sobre sus necesidades.',
  ],
  [
    'mascota_4.png',
    'Miel',
    'PERRO',
    'Miel espera un hogar que la acompañe con paciencia y cuidado.',
  ],
  [
    'mascota_5.png',
    'Copito',
    'PERRO',
    'Copito busca una familia que lo acompañe en su nueva etapa.',
  ],
  [
    'gatito_1.png',
    'Simba',
    'GATO',
    'Simba espera un hogar seguro y una familia que le dé atención y cariño.',
  ],
  [
    'gato_domi.jpeg',
    'Domi',
    'GATO',
    'Domi busca un hogar donde recibir cuidado, atención y compañía.',
  ],
  [
    'gatito_2.jpg',
    'Nube',
    'GATO',
    'Nube busca un hogar donde recibir cuidado y compañía todos los días.',
  ],
];
// Comprobar todas las fuentes antes de modificar publicaciones.
for (const [filename] of animals) {
  if (!fs.existsSync(path.join(root, 'img_mascotas', filename)))
    throw new Error('No existe la imagen: ' + filename);
}
const browser = await chromium.launch();
const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } });
page.setDefaultTimeout(15000);
const published = [];
try {
  await page.goto('http://localhost:5173/acceso');
  await page.getByLabel('Correo', { exact: true }).fill(account.correo);
  await page.getByLabel('Contraseña', { exact: true }).fill(account.password);
  const login = page.waitForResponse(r => r.url().includes('/api/auth/login'));
  await page.getByRole('button', { name: 'Entrar', exact: true }).click();
  let response = await login;
  if (response.status() === 429) {
    await new Promise(r => setTimeout(r, 61000));
    const again = page.waitForResponse(r => r.url().includes('/api/auth/login'));
    await page.getByRole('button', { name: 'Entrar', exact: true }).click();
    response = await again;
  }
  if (response.status() !== 200)
    throw new Error('No se pudo acceder al albergue: HTTP ' + response.status());
  const { token } = await response.json();
  await page.getByRole('heading', { name: 'Mascotas de mi albergue', exact: true }).waitFor();
  for (const [filename, name, species, description] of animals) {
    const desc =
      description +
      ' Publicación de demostración de Petly; edad, sexo y comportamiento pendientes de evaluación.';
    let lookup = 'http://localhost:8000/api/mascotas/?search=' + encodeURIComponent(name);
    let found = null;
    while (lookup && !found) {
      const existing = await page.request.get(lookup, {headers:{Authorization:'Bearer '+token}});
      if (existing.status() !== 200) throw new Error('No se pudo consultar mascotas.');
      const list = await existing.json();
      for (const candidate of list.results.filter(p => p.nombre === name)) {
        const response = await page.request.get(`http://localhost:8000/api/mascotas/${candidate.id}/`, {headers:{Authorization:'Bearer '+token}});
        if (response.status() !== 200) throw new Error('No se pudo consultar el detalle.');
        const detail = await response.json();
        const photoName = detail.foto_principal
          ? path.basename(new URL(detail.foto_principal).pathname) : '';
        const sourceName = path.parse(filename).name;
        if (detail.descripcion === desc || photoName === filename ||
            photoName.startsWith(sourceName + '_')) { found = detail; break; }
      }
      lookup = list.next;
    }
    if (found) {
      // Comparar contenido: un cambio de nombre o formato no debe crear otra mascota.
      const current = await page.request.get(found.foto_principal);
      const source = fs.readFileSync(path.join(root, 'img_mascotas', filename));
      if (current.ok() && (await current.body()).equals(source)) {
        published.push({ id: found.id, nombre: name, archivo: filename });
        console.log('Fotografía vigente conservada: ' + name);
        continue;
      }
      const previous = page.getByRole('button', { name: 'Página anterior', exact: true });
      while (await previous.isEnabled()) {
        const loaded = page.waitForResponse(r => r.url().includes('/api/mascotas/?page='));
        await previous.click();
        await loaded;
      }
      const row = page.locator('article.list-row').filter({
        has: page.getByRole('heading', { name, exact: true }),
      }).filter({has: page.locator(`img[src="${found.foto_principal}"]`)});
      // El listado puede estar paginado; localizar por la URL de la fotografía y el nombre.
      while ((await row.count()) === 0) {
        const next = page.getByRole('button', { name: 'Página siguiente', exact: true });
        if (!(await next.isEnabled())) throw new Error('No se encontró la publicación: ' + name);
        const loaded = page.waitForResponse(r => r.url().includes('/api/mascotas/?page='));
        await next.click();
        await loaded;
      }
      await row.getByRole('button', { name: 'Editar mascota', exact: true }).click();
      await page.getByRole('heading', { name: 'Editar mascota', exact: true }).waitFor();
      await page.locator('input[type=file]').setInputFiles(path.join(root, 'img_mascotas', filename));
      const update = page.waitForResponse(r =>
        r.url() === `http://localhost:8000/api/mascotas/${found.id}/` && r.request().method() === 'PATCH');
      await page.getByRole('button', { name: 'Guardar cambios de mascota', exact: true }).click();
      const saved = await update;
      if (saved.status() !== 200) throw new Error('No se actualizó ' + name + ': HTTP ' + saved.status());
      const pet = await saved.json();
      const image = await page.request.get(pet.foto_principal);
      if (!image.ok() || !(await image.body()).equals(source))
        throw new Error('La imagen publicada no coincide: ' + name);
      published.push({ id: pet.id, nombre: name, archivo: filename });
      await page.getByRole('heading', { name: 'Registrar mascota', exact: true }).waitFor();
      console.log('Fotografía actualizada desde panel ALBERGUE: ' + name);
      continue;
    }
    await page.getByLabel('Nombre de la mascota', { exact: true }).fill(name);
    await page.getByLabel('Especie', { exact: true }).selectOption(species);
    await page.getByLabel('Sexo', { exact: true }).selectOption('POR_EVAL');
    await page.getByLabel('Tamaño', { exact: true }).selectOption('');
    await page.getByLabel('Temperamento', { exact: true }).selectOption('POR_EVALUAR');
    await page.getByLabel('Energía', { exact: true }).selectOption('POR_EVALUAR');
    await page.getByLabel('Edad: años', { exact: true }).fill('');
    await page.getByLabel('Edad: meses adicionales', { exact: true }).fill('');
    await page.getByLabel('Descripción de la mascota', { exact: true }).fill(desc);
    await page.locator('input[type=file]').setInputFiles(path.join(root, 'img_mascotas', filename));
    const upload = page.waitForResponse(
      r => r.url() === 'http://localhost:8000/api/mascotas/' && r.request().method() === 'POST'
    );
    await page.getByRole('button', { name: 'Registrar mascota', exact: true }).click();
    const result = await upload;
    if (result.status() !== 201)
      throw new Error('No se publicó ' + name + ': HTTP ' + result.status());
    const pet = await result.json();
    published.push({ id: pet.id, nombre: name, archivo: filename });
    await page.getByText('Mascota guardada.', { exact: true }).waitFor();
    const row = page
      .locator('article.list-row')
      .filter({ has: page.getByRole('heading', { name, exact: true }) });
    await row.getByRole('img', { name, exact: true }).waitFor();
    await expect
      .poll(() => row.getByRole('img', { name, exact: true }).evaluate(i => i.naturalWidth))
      .toBeGreaterThan(0);
    console.log('Publicado desde panel ALBERGUE: ' + name);
  }
  fs.writeFileSync('/tmp/petly-publicaciones-img.json', JSON.stringify(published, null, 2), {
    mode: 0o600,
  });
  console.log('Publicaciones disponibles: ' + published.length);
} finally {
  await browser.close();
}
