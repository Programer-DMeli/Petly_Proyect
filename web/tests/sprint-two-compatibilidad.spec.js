import { test, expect } from '@playwright/test';
const profile = { vivienda:'Casa',horasFuera:3,presupuesto:300,tieneNinos:false,tieneOtrasMascotas:false,finalizado:true };
const criteria = ['Vivienda','Rutina','Convivencia'].map((nombre,i) => ({ id:nombre.toLowerCase(),nombre,peso:i?35:30,puntaje:100,aporte:i?35:30,explicacion:`Datos de ${nombre} registrados.` }));
const pet = { id:1,nombre:'Mascota Sprint 2',especie:'Perro',tamano:'Grande',edadMeses:24,temperamento:'Sociable',energia:'Alta',descripcion:'Pelaje marrón.',albergue:'Refugio de prueba',albergueId:1,ubicacion:'Lima',estado:'Disponible',fotografiaUrl:null,compatibilidad:{porcentaje:100,puntaje:100,cobertura:100,datosInsuficientes:false,mensaje:'Orientativo; el albergue decide.',versionReglas:'petly-compat-v1',criterios:criteria} };
async function login(page,role,handle) {
 await page.route('**/api/**',async route=>{
  const path=new URL(route.request().url()).pathname;
  const send=json=>route.fulfill({json});
  if(path==='/api/auth/login')return send({token:'token-simulado',expiresIn:3600});
  if(path==='/api/auth/me')return send({id:'7',correo:'sprint@example.test',rol:role});
  if(await handle(route,path))return;
  if(path==='/api/perfil/favoritos'||path==='/api/autorizaciones'||path==='/api/albergues/cuentas-disponibles/')return send([]);
  if(path==='/api/catalogo/opciones')return send({especies:['Perro'],tamanos:['Grande'],temperamentos:['Sociable']});
  if(path==='/api/catalogo')return send({items:[pet],total:1,page:1,pageSize:12});
  if(path==='/api/albergues/mi-perfil/')return send({id:1,nombre:'Refugio',direccion:'Lima',telefono:'999111222',email:'test@example.test',infraestructura:[],activo:true});
  return send({results:[],count:0,next:null});
 });
 await page.goto('/acceso');
 await page.getByLabel('Correo',{exact:true}).fill('sprint@example.test');
 await page.getByLabel('Contraseña',{exact:true}).fill('password-prueba');
 await page.getByRole('button',{name:'Entrar',exact:true}).click();
 await expect(page).not.toHaveURL(/acceso/);
}
async function nav(page,path) { await page.evaluate(path=>{history.pushState({},'',path);window.dispatchEvent(new PopStateEvent('popstate'));},path); }

test('Simulada: desglose, reintento IA sin cambiar puntaje y recálculo tras guardar perfil',async({page})=>{
 let current={...profile},calls=0;
 await login(page,'ADOPTANTE',async(route,path)=>{
  const score=current.vivienda==='Casa'?100:88;
  const animal={...pet,compatibilidad:{...pet.compatibilidad,porcentaje:score,puntaje:score}};
  let result;
  if(path==='/api/catalogo')result={items:[animal],total:1,page:1,pageSize:12};
  else if(path==='/api/catalogo/1')result=animal;
  else if(path==='/api/perfil') {if(route.request().method()==='PATCH')current=route.request().postDataJSON();result=current;}
  else if(path.endsWith('/explicacion')) {calls++;result=calls===1?{estado:'NO_DISPONIBLE',explicacion:'Puedes reintentar.',recomendaciones:[]}:{estado:'DISPONIBLE',explicacion:'Consejo complementario.',recomendaciones:['Coordina paseos.']};}
  else return false;
  await route.fulfill({json:result});return true;
 });
 await page.getByRole('link',{name:'Ver detalle de Mascota Sprint 2',exact:true}).click();
 const detail=page.getByRole('region',{name:'¿Cómo se calcula tu compatibilidad?'});
 await expect(detail.getByText('100%',{exact:true})).toBeVisible();
 for(const name of ['Vivienda','Rutina','Convivencia'])await expect(detail.getByText(name,{exact:true})).toBeVisible();
 await expect(detail.getByText(/petly-compat-v1/)).toBeVisible();
 await page.getByRole('button',{name:'Consultar o reintentar consejos IA'}).click();
 await expect(page.getByText('IA no disponible',{exact:true})).toBeVisible();
 await page.getByRole('button',{name:'Consultar o reintentar consejos IA'}).click();
 await expect(page.getByText('Coordina paseos.')).toBeVisible();
 await expect(detail.getByText('100%',{exact:true})).toBeVisible();
 await nav(page,'/adoptante/cuestionario');
 await page.getByLabel('Vivienda',{exact:true}).fill('Departamento');
 await page.getByRole('button',{name:'Finalizar cuestionario',exact:true}).click();
 await expect(page.getByText('Cuestionario finalizado.',{exact:true})).toBeVisible();
 await nav(page,'/adoptante/catalogo');
 await expect(page.getByText('88% compatible',{exact:true})).toBeVisible();
});

test('Simulada: ADMIN valida suma de pesos y recupera versión guardada',async({page})=>{
 let rules={pesos:{vivienda:30,rutina:35,convivencia:35},versionReglas:'petly-compat-v1'},writes=0;
 await login(page,'ADMIN',async(route,path)=>{
  if(path!=='/api/admin/compatibilidad/reglas')return false;
  if(route.request().method()==='PUT'){writes++;rules={...route.request().postDataJSON(),versionReglas:'petly-compat-v2'};}
  await route.fulfill({json:rules});return true;
 });
 await page.getByRole('link',{name:'Configurar pesos de compatibilidad'}).click();
 await page.getByLabel('vivienda',{exact:true}).fill('40');
 await expect(page.getByRole('button',{name:'Guardar pesos',exact:true})).toBeDisabled();
 await page.getByLabel('rutina',{exact:true}).fill('30');
 await page.getByLabel('convivencia',{exact:true}).fill('30');
 await page.getByRole('button',{name:'Guardar pesos',exact:true}).click();
 await expect(page.getByText('petly-compat-v2',{exact:true})).toBeVisible();
 await nav(page,'/administracion/albergues');
 await page.getByRole('link',{name:'Configurar pesos de compatibilidad'}).click();
 await expect(page.getByLabel('vivienda',{exact:true})).toHaveValue('40');expect(writes).toBe(1);
});

test('Simulada: borrador editable y etiquetas se descartan o confirman sin guardar automáticamente',async({page})=>{
 const animal={id:1,nombre:'Mascota Sprint 2',especie:'PERRO',sexo:'HEMBRA',tamanio:'GRANDE',temperamento:'POR_EVALUAR',energia:'ALTA',edad_anos:2,edad_meses:0,descripcion:'Descripción original. Juega con otros perros.',estado:'DISPONIBLE',foto_principal:'/test.png'};
 let writes=0,generates=0;
 await login(page,'ALBERGUE',async(route,path)=>{
  let result;
  if(path==='/api/mascotas/')result={results:[animal],count:1,next:null};
  else if(path==='/api/mascotas/1/'){
   if(route.request().method()==='PATCH'){
    writes++;const body=route.request().postData();expect(body).toContain('Descripción revisada.');expect(body).toContain('TRANQUILO');
    animal.descripcion='Descripción revisada.';animal.temperamento='TRANQUILO';
   }result=animal;
  }else if(path.endsWith('/generar-descripcion/')){
   generates++;
   if(generates===1){await route.fulfill({status:502,json:{detail:'Gemini no disponible. Reintenta.'}});return true;}
   result={borrador:'Pelaje marrón.',guardado:false};
  }else if(path.endsWith('/sugerir-temperamento/'))result={etiqueta:'SOCIABLE',explicacion:'Juega con otros perros.',evidencia:'Juega con otros perros',guardado:false};
  else return false;
  await route.fulfill({json:result});return true;
 });
 await nav(page,'/albergue/mascotas');
 await page.getByRole('button',{name:'Editar mascota',exact:true}).click();
 const original=page.getByRole('textbox',{name:'Descripción de la mascota',exact:true});
 const generate=page.getByRole('button',{name:'Generar borrador desde la foto'});
 await generate.click();await expect(page.getByText('Gemini no disponible. Reintenta.',{exact:true})).toBeVisible();
 await expect(original).toHaveValue(animal.descripcion);
 await generate.click();await expect(page.getByLabel('Borrador editable de Gemini')).toHaveValue('Pelaje marrón.');
 await expect(original).toHaveValue(animal.descripcion);
 await page.getByRole('button',{name:'Descartar borrador',exact:true}).click();
 await expect(original).toHaveValue(animal.descripcion);
 await generate.click();await page.getByLabel('Borrador editable de Gemini').fill('Descripción revisada.');
 await page.getByRole('button',{name:'Aplicar descripción al editor'}).click();
 const suggest=page.getByRole('button',{name:'Sugerir temperamento con Gemini'});
 await suggest.click();await expect(page.getByLabel('Temperamento',{exact:true})).toHaveValue('POR_EVALUAR');
 await page.getByRole('button',{name:'Descartar sugerencia'}).click();
 await suggest.click();await page.getByRole('button',{name:'Aplicar al editor',exact:true}).click();
 await expect(page.getByLabel('Temperamento',{exact:true})).toHaveValue('SOCIABLE');expect(writes).toBe(0);
 await page.getByLabel('Temperamento',{exact:true}).selectOption('TRANQUILO');
 await page.getByRole('button',{name:'Guardar cambios de mascota'}).click();
 await expect(page.getByText('Mascota guardada.',{exact:true})).toBeVisible();expect(writes).toBe(1);
 await page.getByRole('button',{name:'Editar mascota',exact:true}).click();
 await expect(original).toHaveValue('Descripción revisada.');
 await expect(page.getByLabel('Temperamento',{exact:true})).toHaveValue('TRANQUILO');
});
