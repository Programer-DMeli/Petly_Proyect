import { test, expect } from '@playwright/test';
import fs from 'node:fs';
const source=process.env.PETLY_SPRINT2_FIXTURES;
const fixtures=source && fs.existsSync(source)?JSON.parse(fs.readFileSync(source,'utf8')):null;
test.skip(!fixtures,'Ejecutar primero scripts/verificar-sprint02-integracion.py y definir PETLY_SPRINT2_FIXTURES.');
async function login(page,role) {
 await page.goto('/acceso');
 await page.getByLabel('Correo',{exact:true}).fill(fixtures[role].correo);
 await page.getByLabel('Contraseña',{exact:true}).fill(fixtures[role].password);
 const response=page.waitForResponse(r=>r.url().includes('/api/auth/login'));
 await page.getByRole('button',{name:'Entrar',exact:true}).click();
 const result=await response;
 await page.waitForURL(url=>!url.pathname.includes('/acceso'));
 return (await result.json()).token;
}
async function nav(page,path) {
 const target=page.locator(`a[href="${path}"]`).filter({visible:true}).first();
 if(!await target.isVisible()) {
  const mobile=page.getByRole('button',{name:'Abrir menú de navegación',exact:true});
  if(await mobile.isVisible() && await mobile.getAttribute('aria-expanded')==='false')await mobile.click();
  if(!await target.isVisible()) {
   const group=path.startsWith('/albergue') ? (path.endsWith('/eventos')?'Comunidad':'Adopciones') : (path.endsWith('/disponibilidad')?'Mi perfil':'Mi adopción');
   await page.getByRole('button',{name:group,exact:true}).click();
  }
 }
 await target.click();await expect(page).toHaveURL(path);
}

test('Sprint 2 real: favoritos, disponibilidad, fotos privadas, eventos y afiche',async({browser})=>{
 const errors=[];
 const ca=await browser.newContext({baseURL:'http://localhost:5173'}),co=await browser.newContext({baseURL:'http://localhost:5173',acceptDownloads:true});
 try {
  const a=await ca.newPage(),o=await co.newPage();
  for(const p of [a,o])p.on('pageerror',e=>errors.push(e.message));
  const token=await login(a,'ADOPTANTE');
  const initialized=await ca.request.post('http://localhost:8080/api/perfil/disponibilidad',{headers:{Authorization:`Bearer ${token}`},data:[{diaSemana:'Lunes',horaInicio:'09:00',horaFin:'11:00',descripcion:'Entrevistas'}]});
  expect(initialized.status()).toBe(200);
  await nav(a,'/adoptante/favoritos');
  await expect(a.getByRole('heading',{name:'Integración S2 0',exact:true})).toBeVisible();
  await nav(a,'/adoptante/disponibilidad');
  await expect(a.getByText(/Lunes: 09:00–11:00/)).toBeVisible();
  await a.getByLabel('Día',{exact:true}).selectOption('Miércoles');
  await a.getByLabel('Hora de inicio').fill('14:00');await a.getByLabel('Hora de fin').fill('15:00');
  await a.getByRole('button',{name:'Guardar franja',exact:true}).click();
  await expect(a.getByText('Disponibilidad guardada.',{exact:true})).toBeVisible();
  await expect(a.getByText(/Miércoles: 14:00–15:00/)).toBeVisible();
  await nav(a,'/adoptante/solicitudes');
  // Entrar al seguimiento usando navegación del cliente conserva la sesión en memoria.
  await a.evaluate(id=>{history.pushState({},'',`/adoptante/solicitudes/${id}`);window.dispatchEvent(new PopStateEvent('popstate'));},fixtures.datos.solicitud);
  await a.getByRole('button',{name:'Consultar fotos privadas del hogar'}).click();
  await expect(a.getByRole('button',{name:'Descargar foto 1',exact:true})).toBeVisible();
  await login(o,'ALBERGUE');
  await nav(o,'/albergue/eventos');
  const title='Evento visual de integración '+Date.now();
  const date=new Date(Date.now()+5*86400000).toISOString().slice(0,10);
  await o.getByLabel('Título del evento').fill(title);await o.getByLabel('Descripción del evento').fill('Jornada de prueba para revisión de integración.');
  await o.getByLabel('Inicio del evento').fill(date+'T10:00');await o.getByLabel('Fin del evento').fill(date+'T13:00');
  await o.getByLabel('Ubicación del evento').fill('Lima');await o.getByLabel('Contacto público del evento').fill('pruebas@example.test');
  await o.getByRole('button',{name:'Publicar evento',exact:true}).click();
  const card=o.locator('article').filter({has:o.getByRole('heading',{name:title,exact:true})});await expect(card).toBeVisible();
  await card.getByRole('button',{name:'Editar evento',exact:true}).click();
  await o.getByLabel('Descripción del evento').fill('Descripción editada en React.');await o.getByRole('button',{name:'Guardar evento',exact:true}).click();
  await expect(card.getByText('Descripción editada en React.',{exact:true})).toBeVisible();
  await o.screenshot({path:'../docs/evidencias/sprint2-eventos-integrados.png',fullPage:true});
  await nav(a,'/adoptante/eventos');await expect(a.getByRole('heading',{name:title,exact:true})).toBeVisible();
  o.on('dialog',d=>d.accept());await card.getByRole('button',{name:'Cancelar evento',exact:true}).click();await expect(card.getByText('CANCELADO',{exact:true})).toBeVisible();
  await a.getByRole('button',{name:'Actualizar eventos',exact:true}).click();await expect(a.locator('article').filter({has:a.getByRole('heading',{name:title,exact:true})}).getByText('CANCELADO',{exact:true})).toBeVisible();
  await nav(o,'/albergue/mascotas');
  const pet=o.locator('article').filter({has:o.getByRole('heading',{name:'Integración S2 0',exact:true})}).first();
  await pet.getByRole('button',{name:'Preparar afiche',exact:true}).click();
  const download=o.getByRole('button',{name:'Descargar PNG',exact:true});await expect(download).toBeEnabled();
  const downloaded=o.waitForEvent('download');await download.click();const file=await downloaded;await file.saveAs('../docs/evidencias/sprint2-afiche-integrado.png');
  const bytes=fs.readFileSync('../docs/evidencias/sprint2-afiche-integrado.png');expect(bytes.readUInt32BE(16)).toBe(1080);expect(bytes.readUInt32BE(20)).toBe(1350);
  expect(errors).toEqual([]);
 } finally {await ca.close();await co.close();}
});
