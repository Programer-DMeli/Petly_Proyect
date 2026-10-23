"""Arranca Spring y Django con sus .env permanentes; no crea ni borra bases.

backend-admin/.venv/bin/python scripts/servicios-locales.py
"""
import json
import os
from pathlib import Path
import signal
import socket
import subprocess
import time

import requests
from dotenv import dotenv_values

root = Path(__file__).resolve().parents[1]
home = Path.home() / ".local/share/petly"
spring = dotenv_values(root / "backend_usuario/petly_proyect/.env")
django = dotenv_values(root / "backend-admin/.env")
for key in ("SPRING_JWT_SECRET", "DB_PASSWORD"):
    if not spring.get(key) or spring.get(key) != django.get(key):
        raise SystemExit("Revisar configuración compartida: " + key)
if django.get("DB_NAME") != "database_petly" or "/database_petly" not in spring.get("DB_URL", ""):
    raise SystemExit("Los dos backends deben utilizar la base permanente database_petly.")
for port in (8080, 8000):
    with socket.socket() as sock:
        if sock.connect_ex(("127.0.0.1", port)) == 0:
            raise SystemExit(f"El puerto {port} está ocupado. No se detuvo el otro proceso.")
env = dict(os.environ, **{k: v for k, v in {**spring, **django}.items() if v is not None})
logs = home / "logs"
logs.mkdir(parents=True, exist_ok=True, mode=0o700)
children = []


def launch(cwd, command, name):
    with (logs / name).open("w") as log:
        child = subprocess.Popen(command, cwd=cwd, env=env, stdout=log,
                                 stderr=subprocess.STDOUT, start_new_session=True)
    children.append(child)
    return child


def stop(*args):
    for child in children:
        if child.poll() is None:
            os.killpg(child.pid, signal.SIGTERM)
    if args:
        raise SystemExit(0)


signal.signal(signal.SIGTERM, stop)
signal.signal(signal.SIGINT, stop)
try:
    # clean elimina recursos compilados antiguos, incluidas migraciones retiradas por main.
    backend = launch(root / "backend_usuario/petly_proyect",
                     ["bash", "mvnw", "-q", "clean", "spring-boot:run"], "spring.log")
    for _ in range(240):
        if backend.poll() is not None:
            raise SystemExit("Spring terminó. Revisar ~/.local/share/petly/logs/spring.log.")
        try:
            if requests.get("http://127.0.0.1:8080/api/health/", timeout=1).status_code == 200:
                break
        except requests.RequestException:
            pass
        time.sleep(1)
    else:
        raise SystemExit("Spring no respondió en el plazo esperado.")
    with (logs / "migraciones-django.log").open("w") as log:
        subprocess.run([os.sys.executable, "manage.py", "migrate", "--noinput"],
                       cwd=root / "backend-admin", env=env, stdout=log,
                       stderr=subprocess.STDOUT, check=True)
    launch(root / "backend-admin", [os.sys.executable, "manage.py", "runserver",
                                   "127.0.0.1:8000", "--noreload"], "django.log")
    state = home / "servicios.json"
    state.write_text(json.dumps({"pid": os.getpid(), "database": "database_petly"}))
    state.chmod(0o600)
    print("Spring (8080) y Django (8000) conectados a database_petly permanente.", flush=True)
    while True:
        time.sleep(1)
        if any(child.poll() is not None for child in children):
            raise SystemExit("Un backend terminó. Revisar los logs permanentes.")
finally:
    stop()
