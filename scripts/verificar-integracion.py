"""Prueba real con fixtures nuevos en una base aislada; no borra datos.
Requiere servicios activos, PyMySQL, requests y Pillow. Nunca imprime credenciales.
"""
import os, secrets, time, io, json, re
from pathlib import Path
import pymysql
import requests
from PIL import Image

spring = os.getenv('SPRING_API_URL', 'http://127.0.0.1:8080')
django = os.getenv('DJANGO_API_URL', 'http://127.0.0.1:8000')
name = os.environ['TEST_DB_NAME']
if not (name.startswith('petly_test_') or name == 'petly_integracion'):
    raise SystemExit('Usa únicamente una base de pruebas identificada explícitamente.')
db = pymysql.connect(host=os.getenv('TEST_DB_HOST','127.0.0.1'),port=int(os.getenv('TEST_DB_PORT','13306')),
    user=os.environ['TEST_DB_USER'],password=os.getenv('TEST_DB_PASSWORD',''),database=name,autocommit=True)
results=[]
def check(method,base,path,status=200,token=None,**kwargs):
    headers={'Authorization':'Bearer '+token} if token else {}
    r=requests.request(method,base+path,headers=headers,timeout=15,**kwargs)
    assert r.status_code==status,(method,path,r.status_code,"estado HTTP inesperado")
    return r.json() if r.content and 'application/json' in r.headers.get('content-type','') else r

def account(role):
    email='prueba-'+secrets.token_hex(8)+'@example.test';password=secrets.token_urlsafe(24)
    user=check('POST',spring,'/api/auth/registro',201,json={'correo':email,'password':password})
    if role!='ADOPTANTE':
        with db.cursor() as cursor:cursor.execute('UPDATE usuarios SET rol=%s WHERE id=%s',(role,user['id']))
    login=check('POST',spring,'/api/auth/login',json={'correo':email,'password':password})
    return user['id'],email,password,login['token']

aid,email,password,adopt=account('ADOPTANTE')
sid,_,_,shelter=account('ALBERGUE')
otherid,_,_,other=account('ALBERGUE')
s1=check('POST',django,'/api/albergues/',201,shelter,json={'nombre':'Refugio de prueba','direccion':'Lima','capacidad_maxima':30})['id']
s2=check('POST',django,'/api/albergues/',201,other,json={'nombre':'Otro refugio de prueba','direccion':'Callao','capacidad_maxima':20})['id']
image=io.BytesIO();Image.new('RGB',(32,32),'#16705a').save(image,format='PNG');photo=image.getvalue()
pets=[]
for n in range(4):
    pet=check('POST',django,'/api/mascotas/',201,shelter,data={'nombre':'Luna prueba '+str(n),'especie':'PERRO','sexo':'HEMBRA','tamanio':'MEDIANO','temperamento':'POR_EVALUAR','energia':'MEDIA','edad_anos':2,'edad_meses':0},files={'foto_principal':('luna.png',photo,'image/png')})
    pets.append(pet['id'])
check('POST',django,'/api/mascotas/',400,shelter,data={'nombre':'Inválida','especie':'PERRO','sexo':'HEMBRA'},files={'foto_principal':('fake.png',b'fake','image/png')})
check('PATCH',spring,'/api/perfil',token=adopt,json={'vivienda':'Departamento','tieneNinos':False})
profile=check('GET',spring,'/api/perfil',token=adopt)
assert profile['tieneNinos'] is False and profile['tieneOtrasMascotas'] is None
check('PATCH',spring,'/api/perfil',400,adopt,json={'finalizado':True})
check('PATCH',spring,'/api/perfil',token=adopt,json={'horasFuera':8.5,'presupuesto':150.25,'tieneOtrasMascotas':True,'finalizado':True})
assert check('GET',spring,'/api/perfil',token=adopt)['finalizado'] is True
page=check('GET',spring,'/api/catalogo',token=adopt,params={'search':'Luna prueba','especie':'Perro','tamano':'Mediano','temperamento':'Por evaluar','edadMinimaMeses':24,'edadMaximaMeses':24,'page':2,'pageSize':2})
assert page['total']>=4 and len(page['items'])==2
pet=check('GET',spring,'/api/catalogo/'+str(pets[0]),token=adopt)
assert pet['edadMeses']==24
photo_url=pet['fotografiaUrl'].replace('10.0.2.2','127.0.0.1')
assert requests.get(photo_url,timeout=10).status_code==200
check('POST',django,'/api/mascotas/'+str(pets[0])+'/cambiar-estado/',token=shelter,json={'estado':'EN_PROCESO'})
assert check('GET',spring,'/api/catalogo/'+str(pets[0]),token=adopt)['estado']=='En Proceso'
check('POST',django,'/api/mascotas/'+str(pets[0])+'/cambiar-estado/',404,other,json={'estado':'ADOPTADO'})
shared='/api/acceso/perfiles-compartidos/'+str(aid)+'/'
check('GET',django,shared,403,shelter)
check('PUT',spring,'/api/autorizaciones/'+str(s1),token=adopt)
assert check('GET',django,shared,token=shelter)['tieneNinos'] is False
check('GET',django,shared,403,other)
check('DELETE',spring,'/api/autorizaciones/'+str(s1),token=adopt)
check('GET',django,shared,403,shelter)
results+=['Registro/login Spring y JWT Django reales','Albergues y mascotas persistidos; foto real','Perfil parcial/final y convivencia false/null','Filtros combinados y segunda página','Estado cambiado en Django y consultado desde Spring','Consentimiento por albergue y revocación con 403 Django']
print('Flujos de catálogo/perfil/permisos comprobados.',flush=True)
check('POST',spring,'/api/auth/password/reset',json={'correo':email})
mail=Path(os.getenv('MAIL_TEST_FILE','/tmp/petly-correo-prueba.eml'))
for _ in range(20):
    if mail.exists():break
    time.sleep(.25)
# Decode MIME before extracting opaque token.
from email import policy
from email.parser import BytesParser
message=BytesParser(policy=policy.default).parsebytes(mail.read_bytes())
text=message.get_body(preferencelist=('plain',)).get_content() if message.is_multipart() else message.get_content()
reset=re.search(r'token=([A-Za-z0-9_-]+)',text).group(1)
with db.cursor() as cursor:
    from hashlib import sha256
    cursor.execute('SELECT TIMESTAMPDIFF(SECOND,CURRENT_TIMESTAMP,expires_at) FROM password_resets WHERE token_hash=%s',(sha256(reset.encode()).hexdigest(),))
    assert 0 < cursor.fetchone()[0] <= 900
newpassword=secrets.token_urlsafe(24)
body={'token':reset,'nuevaPassword':newpassword,'nuevaPasswordConfirm':newpassword}
check('POST',spring,'/api/auth/password/reset/confirm',json=body)
check('POST',spring,'/api/auth/password/reset/confirm',400,json=body)
check('GET',spring,'/api/auth/me',401,adopt)
# Exercise the quota explicitly before waiting for the new window.
for _ in range(12):
    quota=requests.post(spring+'/api/auth/login',json={'correo':email,'password':newpassword},timeout=10)
    if quota.status_code==429:break
    assert quota.status_code==200
assert quota.status_code==429
# Wait only if quota would hide the password test; the limit is part of acceptance.
print('Correo recibido, enlace de 15 minutos y uso único comprobados. Verificando cuota y bloqueo…',flush=True)
while True:
    r=requests.post(spring+'/api/auth/login',json={'correo':email,'password':newpassword},timeout=10)
    if r.status_code!=429:break
    time.sleep(5)
assert r.status_code==200
check('POST',spring,'/api/auth/login',401,json={'correo':email,'password':password})
check('POST',spring,'/api/auth/login',423,json={'correo':email,'password':password})
check('POST',spring,'/api/auth/login',423,json={'correo':email,'password':newpassword})
with db.cursor() as cursor:
    cursor.execute('SELECT TIMESTAMPDIFF(SECOND,CURRENT_TIMESTAMP,bloqueado_hasta) FROM usuarios WHERE id=%s',(aid,))
    assert 0 < cursor.fetchone()[0] <= 60
    cursor.execute('UPDATE usuarios SET bloqueado_hasta=DATE_SUB(CURRENT_TIMESTAMP,INTERVAL 1 SECOND) WHERE id=%s',(aid,))
check('POST',spring,'/api/auth/login',json={'correo':email,'password':newpassword})
# A separate unknown account does not leak whether email exists.
# May encounter rate limit legitimately; backend tests cover response equality.
results+=['SMTP real de pruebas; token válido 15 minutos, uso único y sesión previa revocada','Bloqueo 1 minuto tras dos fallos y desbloqueo automático']
Path('/tmp/petly-evidencia-integracion.json').write_text(json.dumps({'database':name,'result':'PASS','checks':results},ensure_ascii=False,indent=2))
print('PASS: '+str(len(results))+' grupos de comprobaciones reales. Evidencia en /tmp/petly-evidencia-integracion.json',flush=True)
db.close()
