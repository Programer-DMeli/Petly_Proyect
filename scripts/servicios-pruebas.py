"""Arranque local reproducible sobre una base aislada: Spring → migraciones Django → Django.
La instancia MariaDB debe estar iniciada. No inicia el emulador ni modifica la base del equipo.
"""
import os, secrets, subprocess, signal, time, threading, socketserver, json
from pathlib import Path
import pymysql, requests
root=Path(__file__).resolve().parents[1]
name=os.getenv('TEST_DB_NAME','petly_integracion')
if not (name.startswith('petly_test_') or name=='petly_integracion'):
    raise SystemExit('Solo se permite una base de pruebas identificada.')
secret=secrets.token_urlsafe(48)
user=os.getenv('TEST_DB_USER','root');password=os.getenv('TEST_DB_PASSWORD','')
port=os.getenv('TEST_DB_PORT','13306');host=os.getenv('TEST_DB_HOST','127.0.0.1')
connection=pymysql.connect(host=host,port=int(port),user=user,password=password,autocommit=True)
with connection.cursor() as c:
    # Identifier quoted after strict validation; no arbitrary database names.
    if not name.replace('_','').isalnum():raise SystemExit('Nombre de base inválido.')
    c.execute('CREATE DATABASE IF NOT EXISTS `'+name+'` CHARACTER SET utf8mb4')
connection.close()
env=dict(os.environ,SPRING_JWT_SECRET=secret,DJANGO_SECRET_KEY=secret,
    DB_NAME=name,DB_USER=user,DB_USERNAME=user,DB_PASSWORD=password,DB_HOST=host,DB_PORT=port,
    DB_URL=f'jdbc:mariadb://{host}:{port}/{name}',DJANGO_DEBUG='true',MAIL_HOST='127.0.0.1',MAIL_PORT='1025',
    SPRING_API_URL='http://127.0.0.1:8080',RESET_LINK_BASE='petly://recuperacion',MEDIA_BASE_URL='http://10.0.2.2:8000/media/')
class SMTP(socketserver.StreamRequestHandler):
    def handle(self):
        self.wfile.write(b'220 Petly test SMTP\r\n');data=False;lines=[]
        while True:
            line=self.rfile.readline()
            if not line:break
            if data:
                if line==b'.\r\n':
                    target=Path('/tmp/petly-correo-prueba.eml')
                    target.write_bytes(b''.join(lines));target.chmod(0o600)
                    data=False;self.wfile.write(b'250 Accepted\r\n')
                else:lines.append(line)
            else:
                cmd=line.split(b' ')[0].strip().upper()
                if cmd in (b'EHLO',b'HELO'):self.wfile.write(b'250-localhost\r\n250 8BITMIME\r\n')
                elif cmd==b'DATA':data=True;lines=[];self.wfile.write(b'354 End with dot\r\n')
                elif cmd==b'QUIT':self.wfile.write(b'221 Bye\r\n');break
                else:self.wfile.write(b'250 OK\r\n')
server=socketserver.ThreadingTCPServer(('127.0.0.1',1025),SMTP)
threading.Thread(target=server.serve_forever,daemon=True).start()
children=[]
def launch(cwd,cmd,log):
    child=subprocess.Popen(cmd,cwd=cwd,env=env,stdout=open(log,'w'),stderr=subprocess.STDOUT,start_new_session=True)
    children.append(child);return child

def stop(*args):
    for child in children:
        if child.poll() is None:os.killpg(child.pid,signal.SIGTERM)
    server.shutdown()
    if args:
        raise SystemExit(0)
signal.signal(signal.SIGTERM,stop);signal.signal(signal.SIGINT,stop)
try:
    spring=launch(root/'backend_usuario/petly_proyect',['bash','mvnw','-q','spring-boot:run'],'/tmp/petly-spring-runtime.log')
    for _ in range(120):
        if spring.poll() is not None:raise RuntimeError('Spring terminó; revisar /tmp/petly-spring-runtime.log')
        try:
            if requests.get('http://127.0.0.1:8080/api/health/',timeout=1).status_code==200:break
        except requests.RequestException:pass
        time.sleep(1)
    else:raise RuntimeError('Spring no arrancó dentro del plazo.')
    with open('/tmp/petly-migraciones-reales.log','w') as log:
        subprocess.run([os.sys.executable,'manage.py','migrate','--noinput'],cwd=root/'backend-admin',env=env,stdout=log,stderr=subprocess.STDOUT,check=True)
    launch(root/'backend-admin',[os.sys.executable,'manage.py','runserver','127.0.0.1:8000','--noreload'],'/tmp/petly-django-runtime.log')
    state=Path('/tmp/petly-runtime-state.json');state.write_text(json.dumps({'pid':os.getpid(),'secret':secret}));state.chmod(0o600)
    print('Spring y Django activos sobre '+name+'. SMTP local: 1025. Logs en /tmp.',flush=True)
    while True:
        time.sleep(1)
        if any(p.poll() is not None for p in children):raise RuntimeError('Un servicio terminó; consultar logs.')
finally:
    stop()
