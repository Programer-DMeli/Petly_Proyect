"""Recorrido real local Sprint 2. Crea datos identificados de prueba, sin reemplazar existentes.
Ejecutar con Python Django, backends locales y --crear-datos-de-prueba.
Credenciales de fixtures: /tmp/petly-sprint2-fixtures.json (privado y temporal).
"""
import argparse
import io
import json
import os
from pathlib import Path
import secrets
import sys
from datetime import datetime, timedelta, timezone
import requests
from PIL import Image

parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--crear-datos-de-prueba',action='store_true',required=True)
parser.add_argument('--fixtures',type=Path,default=Path('/tmp/petly-sprint2-fixtures.json'))
args=parser.parse_args()
fixture_path=args.fixtures.expanduser().resolve()
ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'backend-admin'))
os.environ.setdefault('DJANGO_SETTINGS_MODULE','config.settings')
import django
django.setup()
from django.db import connection
assert connection.settings_dict['NAME']=='database_petly'
SPRING='http://127.0.0.1:8080'
DJANGO='http://127.0.0.1:8000'
checks=[]
fixtures=json.loads(fixture_path.read_text()) if fixture_path.exists() else {}

def call(method,base,path,token=None,status=200,**kwargs):
    headers={'Authorization':'Bearer '+token} if token else {}
    response=requests.request(method,base+path,headers=headers,timeout=20,**kwargs)
    assert response.status_code==status, (method,path,response.status_code,response.text[:300])
    return response.json() if response.content and 'json' in response.headers.get('Content-Type','') else response

def cuenta(role, key=None):
    existing=fixtures.get(key or role)
    if existing:
        existing["token"]=call("POST",SPRING,"/api/auth/login",json={"correo":existing["correo"],"password":existing["password"]})["token"]
        return existing["token"]
    email='integracion-s2-'+secrets.token_hex(6)+'@example.test'
    password=secrets.token_urlsafe(24)
    user=call('POST',SPRING,'/api/auth/registro',status=201,json={'correo':email,'password':password})
    if role!='ADOPTANTE':
        with connection.cursor() as cursor:
            cursor.execute('UPDATE usuarios SET rol=%s WHERE id=%s',[role,user['id']])
    token=call('POST',SPRING,'/api/auth/login',json={'correo':email,'password':password})['token']
    fixtures[key or role]={'correo':email,'password':password,'token':token,'id':user['id']}
    with os.fdopen(os.open(fixture_path,os.O_WRONLY|os.O_CREAT|os.O_TRUNC,0o600),'w') as output:
        json.dump(fixtures,output)
    return token

owner=cuenta('ALBERGUE')
other=cuenta('ALBERGUE','OTRO_ALBERGUE')
adopt=cuenta('ADOPTANTE')
second=cuenta('ADOPTANTE','OTRO_ADOPTANTE')
def albergue(token,nombre):
    response=call('GET',DJANGO,'/api/albergues/',token)
    rows=response.get('results',[]) if isinstance(response,dict) else response
    return rows[0]['id'] if rows else call('POST',DJANGO,'/api/albergues/',token,201,json={'nombre':nombre,'direccion':'Lima','telefono':'999000111','email':'pruebas@example.test','capacidad_maxima':10})['id']
shelter=albergue(owner,'Integración Sprint 2 '+secrets.token_hex(3))
albergue(other,'Otro albergue de pruebas')
photo=io.BytesIO();Image.new('RGB',(150,150),'#18745a').save(photo,'PNG');photo=photo.getvalue()
pets=[]
for n in range(3):
    pet=call('POST',DJANGO,'/api/mascotas/',owner,201,data={'nombre':'Integración S2 '+str(n),'especie':'PERRO','sexo':'HEMBRA','tamanio':'MEDIANO','temperamento':'POR_EVALUAR','energia':'MEDIA'},files={'foto_principal':('mascota.png',photo,'image/png')})
    pets.append(pet['id'])
profile={'vivienda':'Casa','horasFuera':4,'presupuesto':300,'tieneNinos':False,'tieneOtrasMascotas':False,'finalizado':True}
for token in [adopt,second]:
    call('PATCH',SPRING,'/api/perfil',token,json=profile)
    call('POST',SPRING,'/api/autorizaciones',token,201,json={'albergueId':shelter})
body={'mascotaId':pets[0],'mensajePostulante':'Solicitud de prueba de integración','respuestasCuestionario':{'nombreCompleto':'Adoptante de prueba','dni':'00000000','dniVerificado':False,'direccionHogar':'Lima','telefonoContacto':'999000111','motivoAdopcion':'Prueba integrada'}}
sol=call('POST',SPRING,'/api/solicitudes',adopt,201,json=body)
sid=sol['id']
call('POST',SPRING,'/api/solicitudes',adopt,400,json=body)
assert call('GET',SPRING,f'/api/catalogo/{pets[0]}',adopt)['estado']=='Disponible'
mis=call('GET',SPRING,'/api/solicitudes',adopt)
assert any(x['id']==sid for x in mis.get('results',mis) if isinstance(x,dict))
checks.append('Solicitud Spring–Django persistida, recuperada y duplicado rechazado; no reserva mascota')
url=f'/api/acceso/solicitudes/{sid}/fotos-hogar/'
f=call('POST',DJANGO,url,adopt,201,files={'archivo':('hogar.png',photo,'image/png')})
assert call('POST',DJANGO,url,adopt,files={'archivo':('hogar.png',photo,'image/png')})['id']==f['id']
assert len(call('GET',DJANGO,url,adopt))==1
private=call('GET',DJANGO,url,owner)[0]['url']
call('GET',DJANGO,private,owner)
call('GET',DJANGO,private,other,403)
call('GET',DJANGO,private,second,403)
call('GET',DJANGO,private,status=401)
call('DELETE',SPRING,f'/api/autorizaciones/{shelter}',adopt)
call('GET',DJANGO,private,owner,403)
call('POST',SPRING,'/api/autorizaciones',adopt,201,json={'albergueId':shelter})
checks.append('Foto privada persistida, reintento idempotente, titularidad, aislamiento y revocación con 403')
for _ in range(2):call('PUT',SPRING,f'/api/perfil/favoritos/{pets[0]}',adopt,status=204)
assert len(call('GET',SPRING,'/api/perfil/favoritos',adopt))==1
assert call('GET',SPRING,'/api/perfil/favoritos',second)==[]
call('GET',SPRING,'/api/perfil/favoritos',owner,403)
franja={'diaSemana':'Lunes','horaInicio':'09:00','horaFin':'11:00','descripcion':'Entrevistas'}
call('POST',SPRING,'/api/perfil/disponibilidad',adopt,json=[franja])
call('POST',SPRING,'/api/perfil/disponibilidad',adopt,400,json=[franja,franja])
assert len(call('GET',SPRING,'/api/perfil/disponibilidad',adopt))==1
shared=call('GET',SPRING,f'/api/albergue/perfiles/{fixtures["ADOPTANTE"]["id"]}',owner)
assert len(shared['disponibilidad'])==1
checks.append('Favoritos únicos por titular y horarios persistidos, rechazo de solapamiento sin pérdida, consulta autorizada del albergue')
call('POST',DJANGO,f'/api/acceso/solicitudes/{sid}/cambiar-estado/',owner,json={'estado':'OBSERVADA','observaciones_revision':'Coordinar visita'})
call('POST',DJANGO,f'/api/acceso/solicitudes/{sid}/cambiar-estado/',other,404,json={'estado':'APROBADA'})
call('POST',SPRING,f'/api/solicitudes/{sid}/cancelar',adopt)
call('POST',DJANGO,f'/api/acceso/solicitudes/{sid}/cambiar-estado/',owner,400,json={'estado':'APROBADA'})
timeline=call('GET',SPRING,f'/api/solicitudes/{sid}/seguimiento',adopt)
assert [e['tipo'] for e in timeline['eventos']]==['SOLICITUD_CREADA','OBSERVADA','CANCELADA']
call('GET',SPRING,f'/api/solicitudes/{sid}/seguimiento',second,404)
body['mascotaId']=pets[1]
approved=call('POST',SPRING,'/api/solicitudes',adopt,201,json=body)['id']
competitor=call('POST',SPRING,'/api/solicitudes',second,201,json=body)['id']
call('POST',DJANGO,f'/api/acceso/solicitudes/{approved}/cambiar-estado/',owner,json={'estado':'APROBADA'})
call('POST',DJANGO,f'/api/acceso/solicitudes/{competitor}/cambiar-estado/',owner,400,json={'estado':'APROBADA'})
call('POST',SPRING,f'/api/solicitudes/{approved}/cancelar',adopt,400)
assert call('GET',SPRING,f'/api/catalogo/{pets[1]}',adopt)['estado']=='En Proceso'
checks.append('Observación, cancelación terminal, cronología y privacidad; aprobación reserva mascota e impide una segunda aprobación')
now=datetime.now(timezone.utc)+timedelta(days=2)
event=call('POST',DJANGO,'/api/eventos/',owner,201,json={'titulo':'Jornada de integración Sprint 2','descripcion':'Evento de prueba para revisión del integrador','inicio':now.isoformat(),'fin':(now+timedelta(hours=3)).isoformat(),'ubicacion':'Lima','contacto_publico':'pruebas@example.test'})
eid=event['id']
call('PATCH',DJANGO,f'/api/eventos/{eid}/',other,404,json={'titulo':'No permitido'})
call('PATCH',DJANGO,f'/api/eventos/{eid}/',owner,json={'titulo':'Jornada de integración actualizada'})
call('PATCH',DJANGO,f'/api/eventos/{eid}/',owner,json={'cancelado':True})
assert call('GET',DJANGO,f'/api/eventos/{eid}/')['cancelado']
checks.append('Evento creado, editado, aislado por propietario y cancelado conservando registro público')
fixtures['datos']={'solicitud':sid,'aprobada':approved,'mascotas':pets,'albergue':shelter,'evento':eid}
with os.fdopen(os.open(fixture_path,os.O_WRONLY|os.O_CREAT|os.O_TRUNC,0o600),'w') as output:
    json.dump(fixtures,output)
report={'fecha':datetime.now(timezone.utc).isoformat(),'resultado':'PASS','comprobaciones':checks,'datos_prueba':fixtures['datos']}
(ROOT/'docs/evidencias/sprint2-integracion-2026-10-27.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
print(json.dumps(report,ensure_ascii=False,indent=2))
