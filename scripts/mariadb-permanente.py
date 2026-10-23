"""MariaDB local persistente, separada de XAMPP y de la instalación del sistema.

python3 scripts/mariadb-permanente.py init|start|status|backup
Datos y respaldos privados: ~/.local/share/petly/ (nunca /tmp ni Git).
"""
import argparse
import datetime
import json
import os
from pathlib import Path
import secrets
import shutil
import socket
import subprocess
import tarfile
import time

ROOT = Path(__file__).resolve().parents[1]
HOME = Path.home() / ".local/share/petly"
DATA = HOME / "mariadb"
CONFIG = HOME / "mariadb.cnf"
ADMIN = HOME / "admin.cnf"
CLIENT = HOME / "client.cnf"
STATE = HOME / "conexion.json"
PORT = 13306
DATABASE = "database_petly"


def private_file(path, content):
    with os.fdopen(os.open(path, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600), "w") as f:
        f.write(content)
    path.chmod(0o600)


def query(sql, client=ADMIN):
    result = subprocess.run(
        ["mariadb", f"--defaults-extra-file={client}", "--batch", "--skip-column-names"],
        input=sql, text=True, capture_output=True,
    )
    if result.returncode:
        raise RuntimeError("MariaDB rechazó la operación; revisar conexión y permisos.")
    return result.stdout.strip()


def start():
    if not CONFIG.exists():
        raise SystemExit("Ejecutar primero init.")
    try:
        query("SELECT 1;")
        return
    except RuntimeError:
        pass
    with socket.socket() as sock:
        if sock.connect_ex(("127.0.0.1", PORT)) == 0:
            raise SystemExit("El puerto 13306 ya está ocupado; no se modificó la otra instancia.")
    with (HOME / "arranque.log").open("a") as log:
        process = subprocess.Popen(
            [shutil.which("mariadbd"), f"--defaults-file={CONFIG}"],
            stdout=log, stderr=subprocess.STDOUT, start_new_session=True,
        )
    for _ in range(60):
        if process.poll() is not None:
            raise SystemExit("MariaDB terminó. Consultar ~/.local/share/petly/mariadb.log.")
        try:
            query("SELECT 1;")
            return
        except RuntimeError:
            time.sleep(1)
    raise SystemExit("MariaDB no respondió en 60 segundos.")


def init():
    HOME.mkdir(parents=True, exist_ok=True, mode=0o700)
    HOME.chmod(0o700)
    if STATE.exists():
        start()
        print("Configuración permanente existente conservada.")
        return
    if DATA.exists() and any(DATA.iterdir()):
        raise SystemExit("El directorio contiene datos sin configuración completa. No se sobrescribió.")
    DATA.mkdir(mode=0o700, exist_ok=True)
    subprocess.run([
        "mariadb-install-db", f"--datadir={DATA}",
        "--auth-root-authentication-method=normal", "--skip-test-db",
    ], check=True, stdout=subprocess.DEVNULL)
    private_file(CONFIG, f"""[mysqld]
datadir={DATA}
socket={HOME}/mariadb.sock
pid-file={HOME}/mariadb.pid
log-error={HOME}/mariadb.log
bind-address=127.0.0.1
port={PORT}
character-set-server=utf8mb4
collation-server=utf8mb4_unicode_ci
""")
    private_file(ADMIN, f"[client]\nuser=root\nprotocol=socket\nsocket={HOME}/mariadb.sock\n")
    start()
    password = secrets.token_urlsafe(32)
    admin_password = secrets.token_urlsafe(32)
    # Proteger todas las cuentas root creadas por el instalador y retirar las anónimas.
    accounts = query("SELECT User, Host FROM mysql.user WHERE User='root' OR User='';")
    changes = []
    for account in accounts.splitlines():
        user, host = account.split("\t", 1)
        host = host.replace("\\", "\\\\").replace("'", "''")
        if user == 'root':
            changes.append(f"ALTER USER 'root'@'{host}' IDENTIFIED BY '{admin_password}';")
        else:
            changes.append(f"DROP USER ''@'{host}';")
    query(f"""CREATE DATABASE `{DATABASE}` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'petly_app'@'127.0.0.1' IDENTIFIED BY '{password}';
GRANT ALL PRIVILEGES ON `{DATABASE}`.* TO 'petly_app'@'127.0.0.1';
""" + '\n'.join(changes))
    private_file(ADMIN, f"[client]\nuser=root\npassword={admin_password}\nprotocol=socket\nsocket={HOME}/mariadb.sock\n")
    private_file(CLIENT, f"[client]\nuser=petly_app\npassword={password}\nhost=127.0.0.1\nport={PORT}\nprotocol=tcp\n")
    private_file(STATE, json.dumps({"host": "127.0.0.1", "port": PORT, "database": DATABASE,
                                  "user": "petly_app", "password": password}, indent=2))
    print("Base permanente creada: database_petly, puerto 13306. Credenciales privadas guardadas.")


def backup():
    if not STATE.exists():
        raise SystemExit("No existe la configuración permanente.")
    folder = HOME / "respaldos" / datetime.datetime.now(datetime.timezone.utc).strftime("%Y%m%dT%H%M%S%fZ")
    folder.mkdir(parents=True, mode=0o700)
    dump = folder / "database_petly.sql"
    temporary = folder / "database_petly.sql.partial"
    with temporary.open("wb") as output:
        temporary.chmod(0o600)
        result = subprocess.run([
            "mariadb-dump", f"--defaults-extra-file={CLIENT}", "--single-transaction",
            "--routines", "--events", "--triggers", DATABASE,
        ], stdout=output, stderr=subprocess.PIPE)
    if result.returncode or not temporary.stat().st_size:
        raise SystemExit("Respaldo incompleto: no se publicó el archivo .sql.")
    temporary.rename(dump)
    photos = folder / "fotografias.tar.gz"
    with tarfile.open(photos, "w:gz") as archive:
        for relative in ("backend-admin/media", "img_mascotas"):
            path = ROOT / relative
            if path.exists():
                archive.add(path, arcname=relative)
    photos.chmod(0o600)
    print("Respaldo SQL y fotografías: " + str(folder))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=["init", "start", "status", "backup"])
    action = parser.parse_args().action
    if action == "init":
        init()
    elif action == "start":
        start()
        print("MariaDB permanente activo en 127.0.0.1:13306.")
    elif action == "backup":
        backup()
    else:
        print(query("SELECT VERSION(), @@datadir;"))
        print("Tablas de Petly: " + query(f"SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='{DATABASE}';", CLIENT))
