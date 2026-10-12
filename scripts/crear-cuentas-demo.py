"""Crea tres cuentas privadas de prueba en una base aislada, sin imprimir credenciales.
Requiere servicios-pruebas.py. Guarda las credenciales solo en /tmp con modo 600.
"""
import os, json, secrets
from pathlib import Path
import pymysql, requests
name=os.getenv('TEST_DB_NAME','petly_integracion')
if not (name=='petly_integracion' or name.startswith('petly_test_')):
    raise SystemExit('Solo se permite una base de pruebas.')
db=pymysql.connect(host=os.getenv('TEST_DB_HOST','127.0.0.1'),port=int(os.getenv('TEST_DB_PORT','13306')),
    user=os.getenv('TEST_DB_USER','root'),password=os.getenv('TEST_DB_PASSWORD',''),database=name,autocommit=True)
accounts={}
for role in ['ADOPTANTE','ALBERGUE','ADMIN']:
    correo=role.lower()+'-'+secrets.token_hex(6)+'@example.test';password=secrets.token_urlsafe(18)
    r=requests.post('http://127.0.0.1:8080/api/auth/registro',json={'correo':correo,'password':password},timeout=15)
    if r.status_code!=201:raise SystemExit('No se pudo registrar fixture: HTTP '+str(r.status_code))
    uid=r.json()['id']
    if role!='ADOPTANTE':
        with db.cursor() as c:c.execute('UPDATE usuarios SET rol=%s,token_version=token_version+1 WHERE id=%s',(role,uid))
    accounts[role]={'id':uid,'correo':correo,'password':password}
db.close()
path=Path(os.getenv('PETLY_DEMO_FILE','/tmp/petly-demo-cuentas.json'))
# O_EXCL evita sobrescribir enlaces/archivos privados previos. El usuario elige otra ruta para otra ejecución.
with os.fdopen(os.open(path,os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600),'w') as f:json.dump(accounts,f)
print('Cuentas de los tres roles creadas en la base aislada. Archivo privado: '+str(path))
