"""Datos NUEVOS de demostración; no restaura cuentas ni perfiles perdidos.

Usa exclusivamente la nueva database_petly configurada en ~/.local/share/petly.
Credenciales privadas persistentes: ~/.local/share/petly/cuentas-demo.json.
"""
import json
import os
from pathlib import Path
import secrets

import pymysql
import requests

home = Path.home() / ".local/share/petly"
config = json.loads((home / "conexion.json").read_text())
if config["database"] != "database_petly" or config["port"] != 13306:
    raise SystemExit("La conexión no corresponde a la instancia permanente de Petly.")
path = home / "cuentas-demo.json"
accounts = json.loads(path.read_text()) if path.exists() else {}
connection = pymysql.connect(host=config["host"], port=config["port"], user=config["user"],
                             password=config["password"], database=config["database"], autocommit=True)
try:
    for role in ("ADOPTANTE", "ALBERGUE", "ADMIN"):
        if role in accounts:
            continue
        username = "petly_demo_" + role.lower() + "_" + secrets.token_hex(3)
        email = username + "@example.test"
        password = secrets.token_urlsafe(20)
        result = requests.post("http://127.0.0.1:8080/api/auth/registro", timeout=15,
                               json={"username": username, "correo": email, "password": password})
        if result.status_code != 201:
            raise SystemExit("Registro de demostración rechazado: HTTP " + str(result.status_code))
        uid = result.json()["id"]
        if role != "ADOPTANTE":
            with connection.cursor() as cursor:
                cursor.execute("UPDATE usuarios SET rol=%s, token_version=token_version+1 WHERE id=%s",
                               (role, uid))
        accounts[role] = {"id": uid, "correo": email, "password": password}
        with os.fdopen(os.open(path, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600), "w") as output:
            json.dump(accounts, output, indent=2)
finally:
    connection.close()
account = accounts["ADMIN"]
login = requests.post("http://127.0.0.1:8080/api/auth/login", timeout=15,
                      json={"correo": account["correo"], "password": account["password"]})
if login.status_code != 200:
    raise SystemExit("Acceso de demostración rechazado: HTTP " + str(login.status_code))
headers = {"Authorization": "Bearer " + login.json()["token"]}
existing = requests.get("http://127.0.0.1:8000/api/albergues/", headers=headers, timeout=15)
if existing.status_code != 200:
    raise SystemExit("Consulta Django rechazada: HTTP " + str(existing.status_code))
items = existing.json()["results"]
if not any(item["propietario_spring_id"] == accounts["ALBERGUE"]["id"] for item in items):
    created = requests.post("http://127.0.0.1:8000/api/albergues/", headers=headers, timeout=15, json={
        "nombre": "Refugio Verde Petly", "descripcion": "Albergue de demostración para pruebas locales.",
        "direccion": "Dirección de demostración", "capacidad_maxima": 30,
        "propietario_spring_id": accounts["ALBERGUE"]["id"], "activo": True,
    })
    if created.status_code != 201:
        raise SystemExit("Creación del albergue rechazada: HTTP " + str(created.status_code))
print("Tres cuentas y albergue de demostración disponibles; credenciales en archivo privado permanente.")
