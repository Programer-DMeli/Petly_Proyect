import { test, expect } from '@playwright/test';

const sizes = [{ name: 'escritorio', width: 1440, height: 1000, columns: 4 }, { name: 'tableta', width: 834, height: 1112, columns: 2 }, { name: 'movil', width: 390, height: 844, columns: 1 }];
const roleHome = { ADOPTANTE: '/adoptante/catalogo', ALBERGUE: '/albergue', ADMIN: '/administracion/albergues' };
const initialShelter = { id: 1, nombre: 'Refugio Esperanza', direccion: 'Lima, Perú', descripcion: 'Una nueva oportunidad para cada compañero.', telefono: '999111222', email: 'refugio@example.test', sitio_web: '', horario_atencion: '09:00–17:00', capacidad_maxima: 30, responsable_nombre: 'Responsable de prueba', responsable_email: 'responsable@example.test', responsable_telefono: '999111222', propietario_spring_id: 8, propietario_correo: 'albergue@example.test', activo: true, infraestructura: [] };
const profile = { vivienda: 'Casa', horasFuera: 4, presupuesto: 250, tieneNinos: false, tieneOtrasMascotas: false, finalizado: true };
const pets = Array.from({ length: 16 }, (_, index) => ({ id: index + 1, nombre: ['Luna', 'Toby', 'Nube', 'Miel', 'Simba', 'Bruno', 'Domi', 'Copito'][index % 8] + (index > 7 ? ' II' : ''), especie: index % 2 ? 'Gato' : 'Perro', tamano: 'Mediano', edadMeses: 24, temperamento: 'Sociable', energia: 'Media', descripcion: 'Compañero de prueba para revisar la interfaz.', albergue: 'Refugio Esperanza', albergueId: 1, ubicacion: 'Lima', estado: 'Disponible', fotografiaUrl: '/images/mascotas-portada.webp', compatibilidad: { porcentaje: 85, puntaje: 85, cobertura: 100, datosInsuficientes: false, mensaje: 'Compatibilidad orientativa.', versionReglas: 'v1', criterios: [] } }));

async function mockApis(page, role) {
  let favorites = [], shelter = { ...initialShelter }, currentProfile = { ...profile };
  let solicitudes = [{ id: 1, mascota: 1, mascota_nombre: 'Luna', postulante_nombre: 'Adoptante de prueba', estado: 'PENDIENTE', fecha_solicitud: '2026-10-27T10:00:00Z', mensaje_postulante: 'Quiero conocer a Luna.', observaciones_revision: '', respuestas_cuestionario: {} }];
  let events = [{ id: 1, titulo: 'Encuentro de adopción', descripcion: 'Conoce a los compañeros del albergue.', inicio: '2026-11-15T15:00:00Z', fin: '2026-11-15T18:00:00Z', ubicacion: 'Lima', contacto_publico: 'refugio@example.test', cancelado: false, albergue_nombre: 'Refugio Esperanza' }];
  let horarios = [], areas = [], consents = [{ albergueId: 1, estado: 'ACTIVA' }];
  const unknown = [], writes = [];
  const paged = values => ({ results: values, count: values.length, next: null, previous: null });
  await page.route('**/api/**', async route => {
    const request = route.request(), url = new URL(request.url()), path = url.pathname, method = request.method();
    const body = () => request.postDataJSON() ?? {};
    const send = (json, status = 200) => route.fulfill({ status, json });
    if (!['GET', 'OPTIONS'].includes(method)) writes.push({ path, method });
    if (path === '/api/auth/login') return send({ token: 'token-ui-test', expiresIn: 3600 });
    if (path === '/api/auth/me') return send({ id: '7', correo: `${role.toLowerCase()}@example.test`, rol: role });
    if (path === '/api/perfil/favoritos') return send(favorites);
    if (/^\/api\/perfil\/favoritos\/\d+$/.test(path)) {
      const id = Number(path.split('/').pop());
      favorites = method === 'PUT' ? [...favorites.filter(p => p.id !== id), pets.find(p => p.id === id)] : favorites.filter(p => p.id !== id);
      return route.fulfill({ status: 204 });
    }
    if (path === '/api/catalogo/opciones') return send({ especies: ['Perro', 'Gato'], tamanos: ['Mediano'], temperamentos: ['Sociable'] });
    if (path === '/api/catalogo/albergues') return send([shelter]);
    if (path === '/api/catalogo') {
      const search = (url.searchParams.get('search') ?? '').toLowerCase();
      const rows = pets.filter(p => p.nombre.toLowerCase().includes(search) && (!url.searchParams.get('especie') || p.especie === url.searchParams.get('especie')));
      const n = Number(url.searchParams.get('page') ?? 1), size = Number(url.searchParams.get('pageSize') ?? 12);
      return send({ items: rows.slice((n - 1) * size, n * size), total: rows.length, page: n, pageSize: size });
    }
    if (/^\/api\/catalogo\/\d+$/.test(path)) return send(pets.find(p => p.id === Number(path.split('/').pop())));
    if (path === '/api/perfil/disponibilidad') { if (method === 'POST') horarios = body(); return send(horarios); }
    if (path === '/api/perfil') { if (method === 'PATCH') currentProfile = { ...currentProfile, ...body() }; return send(currentProfile); }
    if (path === '/api/autorizaciones') { if(method === 'POST') consents = [{albergueId:1,estado:'ACTIVA'}]; return send(method === 'POST' ? consents[0] : consents, method === 'POST' ? 201 : 200); }
    if (/^\/api\/autorizaciones\/\d+$/.test(path)) { consents=[]; return send({ detail: 'Autorización revocada.' }); }
    if (path === '/api/albergues/mi-perfil/') return send(shelter);
    if (path === '/api/albergues/cuentas-disponibles/') return send([{ id: 8, correo: 'albergue@example.test', asignada: true }, { id: 9, correo: 'otro@example.test', asignada: false }]);
    if (path === '/api/albergues/') return send(paged([shelter]));
    if (/^\/api\/albergues\/\d+\/$/.test(path)) { if (method === 'PATCH') shelter = { ...shelter, ...body() }; return send(shelter); }
    if (path.includes('/infraestructura/')) { if (method === 'POST') areas.push({ ...body(), id: areas.length + 1 }); return send(areas, method === 'POST' ? 201 : 200); }
    if (path === '/api/acceso/perfiles-compartidos/') return send([{ adoptanteId: 7, correo: 'adoptante@example.test' }]);
    if (/^\/api\/acceso\/perfiles-compartidos\/\d+\/$/.test(path)) return send({ ...profile, disponibilidad: horarios, solicitudesPrevias: [] });
    if (path === '/api/acceso/solicitudes/' || path === '/api/solicitudes') {
      if (method === 'POST') { const created = { ...solicitudes[0], id: 2, mascota: body().mascotaId, mensaje_postulante: body().mensajePostulante }; solicitudes.push(created); return send(created, 201); }
      return send(paged(solicitudes));
    }
    if (path.includes('/cambiar-estado/')) { solicitudes[0] = { ...solicitudes[0], ...body() }; return send(solicitudes[0]); }
    if (path.endsWith('/cancelar')) { solicitudes[0].estado = 'CANCELADA'; return send(solicitudes[0]); }
    if (path.endsWith('/seguimiento')) return send({ solicitudId: 1, estadoActual: solicitudes[0].estado, eventos: [] });
    if (path.includes('/fotos-hogar/')) return send([]);
    if (path.startsWith('/api/contratos/')) return send(paged([]));
    if (path.startsWith('/api/acceso/verificaciones-kyc/')) return send(paged([]));
    if (path === '/api/eventos/') { if (method === 'POST') { events.push({ ...body(), id: events.length + 1, albergue_nombre: shelter.nombre }); return send(events.at(-1), 201); } return send(paged(events)); }
    if (/^\/api\/eventos\/\d+\/$/.test(path)) { const id = Number(path.split('/').at(-2)); events = events.map(e => e.id === id ? { ...e, ...body() } : e); return send(events.find(e => e.id === id)); }
    if (path === '/api/mascotas/') return send(paged(pets.map(p => ({ id: p.id, nombre: p.nombre, codigo: `PET-${p.id}`, especie: 'PERRO', sexo: 'HEMBRA', tamanio: 'MEDIANO', temperamento: 'SOCIABLE', energia: 'MEDIA', edad_anos: 2, edad_meses: 0, estado: 'DISPONIBLE', foto_principal: p.fotografiaUrl, descripcion: p.descripcion }))));
    if (/^\/api\/mascotas\/\d+\/$/.test(path)) return send({ ...pets[0], especie: 'PERRO', tamanio: 'MEDIANO', foto_principal: '/images/mascotas-portada.webp', edad_anos: 2, edad_meses: 0 });
    unknown.push(`${method} ${path}`); return send({ error: `Ruta sin respuesta de prueba: ${path}` }, 500);
  });
  return { unknown, writes };
}
async function login(page, role) {
  await page.goto('/acceso'); await page.getByLabel('Correo', { exact: true }).fill(`${role.toLowerCase()}@example.test`); await page.getByLabel('Contraseña', { exact: true }).fill('password-ui-test'); await page.getByRole('button', { name: 'Entrar', exact: true }).click(); await expect(page).toHaveURL(roleHome[role]);
}
async function menu(page, group) {
  if (await page.getByRole('button', { name: 'Abrir menú de navegación', exact: true }).isVisible()) await page.getByRole('button', { name: 'Abrir menú de navegación', exact: true }).click();
  await page.getByRole('button', { name: group, exact: true }).click();
}
async function fits(page) {
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  const small = await page.locator('.workspace-header button:visible, .favorite-heart:visible, .carousel-arrows button:visible').evaluateAll(nodes => nodes.filter(n => n.getBoundingClientRect().height < 43).length);
  expect(small).toBe(0);
}
for (const size of sizes) for (const role of ['ADOPTANTE', 'ALBERGUE', 'ADMIN']) {
  test(`${role} · ${size.name}: navegación, funciones y teclado`, async ({ page }) => {
    await page.setViewportSize(size); const state = await mockApis(page, role); const errors = [];
    page.on('pageerror', e => errors.push(e.message)); await login(page, role); await fits(page);
    if (role !== 'ADMIN') {
      const image = page.locator('.adoption-carousel__visual img');
      const images = role === 'ADOPTANTE' ? [1, 2, 3] : [6, 5, 4];
      for (const number of images) {
        await expect(image).toHaveAttribute('src', `/images/carruseles/imagen_${number}.png`);
        await expect.poll(() => image.evaluate(img => img.complete && img.naturalWidth > 0)).toBe(true);
        await expect(image).toHaveAttribute('alt', /.+/);
        await page.getByRole('button', { name: 'Mensaje siguiente', exact: true }).click();
      }
      const title = await page.locator('.adoption-carousel h2').textContent();
      await page.getByRole('button', { name: 'Mensaje siguiente', exact: true }).click(); await expect(page.locator('.adoption-carousel h2')).not.toHaveText(title);
      await page.getByRole('button', { name: 'Mensaje anterior', exact: true }).click(); await expect(page.locator('.adoption-carousel h2')).toHaveText(title);
    }
    await page.screenshot({ path: `output/redisenio/${role.toLowerCase()}-${size.name}.png`, fullPage: true });
    if (role === 'ADOPTANTE') {
      await expect(page.locator('article.pet')).toHaveCount(12);
      expect(await page.locator('.pet-card-grid').evaluate(n => getComputedStyle(n).gridTemplateColumns.split(' ').length)).toBe(size.columns);
      await page.getByRole('button',{name:'Siguiente',exact:true}).click(); await expect(page.locator('article.pet')).toHaveCount(4);
      await page.getByRole('button',{name:'Anterior',exact:true}).click(); await expect(page.locator('article.pet')).toHaveCount(12);
      const filters=page.locator('details.catalog-filters'); if(!await filters.getAttribute('open') && await filters.getAttribute('open') !== '') await filters.locator('summary').click();
      await page.getByLabel('Buscar mascota o albergue').fill('Luna'); await page.getByRole('button',{name:'Buscar',exact:true}).click(); await expect(page.locator('article.pet')).toHaveCount(2);
      await page.getByRole('button',{name:'Limpiar filtros',exact:true}).click(); await expect(page.locator('article.pet')).toHaveCount(12);
      const heart = page.getByRole('button', { name: 'Guardar Luna en favoritos', exact: true }); await expect(heart).toBeEnabled(); await heart.click();
      await expect(page.getByRole('button', { name: 'Quitar Luna de favoritos', exact: true })).toHaveAttribute('aria-pressed', 'true');
      await menu(page, 'Mi adopción'); await page.getByRole('link', { name: 'Favoritos Los compañeros que te robaron el corazón.', exact: true }).click();
      await expect(page.getByRole('heading', { name: 'Luna', exact: true })).toBeVisible(); await page.getByRole('button', { name: 'Quitar Luna de favoritos', exact: true }).click(); await expect(page.getByText(/Aún no tienes favoritos/)).toBeVisible();
      await menu(page, 'Mi perfil'); await page.getByRole('link', { name: /^Mi cuestionario/ }).click();
      await page.getByLabel('Vivienda', { exact: true }).fill('Departamento'); await page.getByRole('button', { name: 'Guardar borrador', exact: true }).click(); await expect(page.getByText('Respuestas guardadas.', { exact: true })).toBeVisible();
      await menu(page, 'Mi perfil'); await page.getByRole('link', { name: /^Disponibilidad/ }).click(); await expect(page.getByRole('heading', { name: 'Disponibilidad semanal', exact: true })).toBeVisible();
      await page.getByRole('button', { name: 'Guardar franja', exact: true }).click(); await expect(page.getByText('Disponibilidad guardada.', { exact: true })).toBeVisible(); await fits(page);
      await menu(page,'Mi perfil');await page.getByRole('link',{name:/^Autorizaciones Decide/}).click();await page.getByRole('button',{name:'Revocar',exact:true}).click();
      const allow=page.getByRole('button',{name:'Autorizar',exact:true});await allow.click();const modal=page.getByRole('dialog',{name:'Confirmar autorización',exact:true});await expect(modal).toBeVisible();
      await modal.getByRole('button',{name:'Cancelar',exact:true}).focus();await page.keyboard.press('Tab');await expect(modal.getByRole('button',{name:'Sí, autorizo compartir',exact:true})).toBeFocused();await page.keyboard.press('Escape');await expect(modal).toHaveCount(0);await expect(allow).toBeFocused();
      await allow.click();await page.getByRole('button',{name:'Sí, autorizo compartir',exact:true}).click();await expect(page.getByRole('button',{name:'Revocar',exact:true})).toBeVisible();
      await menu(page, 'Mi adopción'); await page.getByRole('link', { name: /^Mis solicitudes/ }).click();
      page.on('dialog', d => d.accept()); await page.getByRole('button', { name: 'Cancelar solicitud', exact: true }).click(); await expect(page.getByText(/Estado: CANCELADA/)).toBeVisible();
    } else if (role === 'ALBERGUE') {
      await page.getByLabel('Nombre del albergue', { exact: true }).fill('Refugio Esperanza actualizado'); await page.getByRole('button', { name: 'Actualizar albergue', exact: true }).click(); await expect(page.getByText('Información del albergue guardada.', { exact: true })).toBeVisible();
      await menu(page, 'Mi albergue'); await page.getByRole('link', { name: /^Infraestructura Organiza/ }).click(); await expect(page.getByRole('heading', { name: 'Infraestructura', exact: true })).toBeVisible();
      await menu(page, 'Adopciones'); await page.getByRole('link', { name: /^Solicitudes Acompaña/ }).click(); await page.getByRole('button', { name: 'Observar', exact: true }).click(); await expect(page.getByRole('alert')).toContainText('Escribe el motivo');
      await page.getByLabel('Motivo de observación o rechazo').fill('Coordinar visita.'); await page.getByRole('button', { name: 'Observar', exact: true }).click(); await expect(page.getByText(/Estado: OBSERVADA/)).toBeVisible();
      await menu(page, 'Comunidad'); await page.getByRole('link', { name: /^Eventos Publica/ }).click(); await expect(page.getByRole('button', { name: 'Publicar evento', exact: true })).toBeVisible();
      await page.getByLabel('Título del evento').fill('Evento de interfaz');await page.getByLabel('Descripción del evento').fill('Prueba de publicación sin escribir datos reales.');await page.getByLabel('Inicio del evento').fill('2040-11-01T10:00');await page.getByLabel('Fin del evento').fill('2040-11-01T12:00');await page.getByLabel('Ubicación del evento').fill('Lima');await page.getByLabel('Contacto público del evento').fill('refugio@example.test');await page.getByRole('button',{name:'Publicar evento',exact:true}).click();
      const eventCard=page.locator('article').filter({has:page.getByRole('heading',{name:'Evento de interfaz',exact:true})});await expect(eventCard).toBeVisible();page.on('dialog',dialog=>dialog.accept());await eventCard.getByRole('button',{name:'Cancelar evento',exact:true}).click();await expect(eventCard.getByText('CANCELADO',{exact:true})).toBeVisible();await fits(page);
    } else {
      await page.getByRole('button', { name: '+ Registrar albergue', exact: true }).click(); await expect(page.getByRole('region', { name: 'Formulario de albergue' })).toBeVisible(); await page.getByRole('button', { name: 'Cancelar', exact: true }).click();
      await page.locator('.shelter-card').first().getByRole('button', { name: 'Ver', exact: true }).click(); await expect(page.getByRole('dialog', { name: 'Refugio Esperanza', exact: true })).toBeVisible(); await expect(page.getByRole('button',{name:'Cerrar detalle',exact:true})).toBeFocused();await page.keyboard.press('Tab');await expect(page.getByRole('button',{name:'Cerrar detalle',exact:true})).toBeFocused();await page.keyboard.press('Escape'); await expect(page.getByRole('dialog')).toHaveCount(0);
      await menu(page, 'Gestión'); await page.getByRole('link', { name: /^Revisión de identidad Revisa/ }).click(); await expect(page.getByRole('heading', { name: 'Revisión de identidad', exact: true })).toBeVisible(); await fits(page);
    }
    // Disclosure keyboard behavior: ArrowDown enters the links; Escape restores focus.
    const group = role === 'ADOPTANTE' ? 'Mi perfil' : role === 'ALBERGUE' ? 'Adopciones' : 'Gestión';
    const mobile = page.getByRole('button', { name: 'Abrir menú de navegación', exact: true }); if (await mobile.isVisible()) await mobile.click();
    const trigger = page.getByRole('button', { name: group, exact: true }); await trigger.focus(); await page.keyboard.press('ArrowDown'); await expect(trigger).toHaveAttribute('aria-expanded', 'true');
    await expect.poll(() => page.evaluate(() => document.activeElement?.tagName)).toBe('A'); await page.keyboard.press('Escape'); await expect(trigger).toHaveAttribute('aria-expanded', 'false');
    // On mobile Escape closes the whole disclosure; focus returns to the mobile trigger.
    if (size.width >= 960) await expect(trigger).toBeFocused();
    expect(errors).toEqual([]); expect(state.unknown).toEqual([]);
  });
}
