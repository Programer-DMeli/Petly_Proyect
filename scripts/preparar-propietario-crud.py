"""Prepara una cuenta ALBERGUE libre para la prueba CRUD. Solo base aislada."""
import os,secrets,json
from pathlib import Path
import requests,pymysql
name=os.getenv('TEST_DB_NAME','petly_integracion')
if not (name=='petly_integracion' or name.startswith('petly_test_')):raise SystemExit('Solo base de pruebas.')
path=Path(os.getenv('PETLY_CRUD_OWNER_FILE','/tmp/petly-crud-owner.json'))
if path.exists():raise SystemExit('Ya existe el archivo privado. Reutilízalo o indica otra ruta.')
correo='albergue-crud-'+secrets.token_hex(5)+'@example.test';password=secrets.token_urlsafe(18)
r=requests.post('http://localhost:8080/api/auth/registro',json={'correo':correo,'password':password},timeout=15)
if r.status_code!=201:raise SystemExit('Registro rechazado: HTTP '+str(r.status_code))
uid=r.json()['id']
db=pymysql.connect(host=os.getenv('TEST_DB_HOST','127.0.0.1'),port=int(os.getenv('TEST_DB_PORT','13306')),user=os.getenv('TEST_DB_USER','root'),password=os.getenv('TEST_DB_PASSWORD',''),database=name,autocommit=True)
with db.cursor() as c:c.execute("UPDATE usuarios SET rol='ALBERGUE',token_version=token_version+1 WHERE id=%s",(uid,))
db.close()
with os.fdopen(os.open(path,os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600),'w') as f:json.dump({'id':uid,'correo':correo,'password':password},f)
print('Propietario de prueba preparado; credenciales solo en archivo privado.')
