"""Recorrido HTTP real con registros nuevos de prueba. No modifica cuentas ni pesos existentes.
Requiere los servicios locales. Usa --gemini para verificar también el proveedor real.
"""
import argparse
import json
import os
import secrets
import sys
from datetime import datetime, timezone
from pathlib import Path
import requests
ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'backend-admin'))
os.environ.setdefault('DJANGO_SETTINGS_MODULE', 'config.settings')
import django
django.setup()
from django.db import connection
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--gemini', action='store_true')
args = parser.parse_args()
S = 'http://127.0.0.1:8080'
D = 'http://127.0.0.1:8000'
checks = []
accounts = {}
def call(method, base, path, token=None, expected=200, **kwargs):
    r = requests.request(method, base + path, headers={'Authorization': 'Bearer ' + token} if token else {}, timeout=65, **kwargs)
    assert r.status_code == expected, (method, path, r.status_code, 'HTTP inesperado')
    return r.json() if r.content else None

def account(role):
    email = 's2-match-' + secrets.token_hex(6) + '@example.test'
    password = secrets.token_urlsafe(24)
    user = call('POST', S, '/api/auth/registro', expected=201, json={'correo': email, 'password': password})
    if role != 'ADOPTANTE':
        with connection.cursor() as cursor:
            cursor.execute('UPDATE usuarios SET rol=%s WHERE id=%s', [role, user['id']])
    token = call('POST', S, '/api/auth/login', json={'correo': email, 'password': password})['token']
    accounts[role] = {'correo': email, 'password': password, 'token': token}
    return token

a = account('ADOPTANTE'); owner = account('ALBERGUE'); admin = account('ADMIN')
shelter = call('POST', D, '/api/albergues/', owner, expected=201, json={'nombre': 'Sprint 2 Match pruebas ' + secrets.token_hex(3), 'direccion': 'Lima', 'telefono': '999000111', 'email': 'pruebas@example.test', 'capacidad_maxima': 10})['id']
prefix = 'Sprint2 Match ' + secrets.token_hex(4)
pets = []
for i, temperament in enumerate(['SOCIABLE', 'SOCIABLE', 'TIMIDO', 'POR_EVALUAR']):
    with (ROOT / 'img_mascotas/mailo.jpg').open('rb') as photo:
        p = call('POST', D, '/api/mascotas/', owner, expected=201,
                 data={'nombre': f'{prefix} {i}', 'especie': 'PERRO', 'sexo': 'MACHO', 'tamanio': 'GRANDE', 'energia': 'ALTA', 'temperamento': temperament, 'descripcion': 'Se acerca a otros perros y juega con ellos durante los paseos.'},
                 files={'foto_principal': ('prueba-match.jpg', photo, 'image/jpeg')})
    pets.append(p['id'])
profile = {'vivienda': 'Casa', 'horasFuera': 3, 'presupuesto': 300, 'tieneNinos': False, 'tieneOtrasMascotas': False, 'finalizado': True}
call('PATCH', S, '/api/perfil', a, json=profile)
def catalog(): return call('GET', S, '/api/catalogo', a, params={'search': prefix, 'pageSize': 100})
rows = catalog()['items']
assert [r['id'] for r in rows[:2]] == pets[:2]
assert rows[0]['compatibilidad']['porcentaje'] == 100
assert rows[-1]['compatibilidad']['porcentaje'] is None
checks.append('US-01/02: perfil compatible 100, empates por id y desconocidos al final; mascotas nuevas persistidas')
first = call('GET', S, '/api/catalogo', a, params={'search': prefix, 'page': 1, 'pageSize': 2})
second = call('GET', S, '/api/catalogo', a, params={'search': prefix, 'page': 2, 'pageSize': 2})
assert [r['id'] for r in first['items'] + second['items']] == [r['id'] for r in rows]
detail = call('GET', S, f'/api/v1/matches/mascota/{pets[0]}', a)
assert detail['compatibilidad'] == rows[0]['compatibilidad']
for row in rows[:-1]:
    c = row['compatibilidad']
    from decimal import Decimal, ROUND_HALF_UP
    total = sum(Decimal(str(k['aporte'])) for k in c['criterios'])
    assert int(total.quantize(Decimal('1'), rounding=ROUND_HALF_UP)) == c['porcentaje']
checks.append('US-04: lista y detalle idénticos, aportes sumados y redondeados; paginación conserva orden')
call('PATCH', S, '/api/perfil', a, expected=400, json={'horasFuera': 25})
assert catalog()['items'] == rows
call('PATCH', S, '/api/perfil', a, json={**profile, 'vivienda': 'Departamento', 'horasFuera': 10, 'tieneNinos': True, 'tieneOtrasMascotas': True})
new = catalog()['items']
assert next(p for p in new if p['id'] == pets[2])['compatibilidad']['porcentaje'] < 60
checks.append('US-03: formulario inválido no cambia datos; guardar vivienda/rutina/convivencia recalcula con perfil poco compatible')
call('PATCH', S, '/api/perfil', a, json=profile)
call('POST', D, f'/api/mascotas/{pets[1]}/cambiar-estado/', owner, json={'estado': 'EN_PROCESO'})
assert pets[1] not in [p['id'] for p in catalog()['items']]
checks.append('US-02/03: mascota recién reservada se excluye del recálculo')
for token in [a, owner]:
    call('PUT', S, '/api/admin/compatibilidad/reglas', token, expected=403, json={'pesos': {'vivienda': 40, 'rutina': 30, 'convivencia': 30}})
previous = call('GET', S, '/api/admin/compatibilidad/reglas', admin)
call('PUT', S, '/api/admin/compatibilidad/reglas', admin, expected=400, json={'pesos': {'vivienda': 40.5, 'rutina': 30, 'convivencia': 29.5}})
assert call('GET', S, '/api/admin/compatibilidad/reglas', admin) == previous
checks.append('US-05: acceso negado a adoptante/albergue y pesos inválidos no sustituyen configuración vigente')
ai = []
if args.gemini:
    before = catalog()
    advice = call('POST', S, f'/api/v1/matches/mascota/{pets[0]}/explicacion', a, json={})
    assert catalog() == before
    ai.append({'flujo': 'consejos Spring', 'estado': advice['estado'], 'respuesta': advice})
    original = call('GET', D, f'/api/mascotas/{pets[0]}/', owner)
    draft = call('POST', D, f'/api/mascotas/{pets[0]}/generar-descripcion/', owner, json={})
    assert not draft['guardado']
    assert call('GET', D, f'/api/mascotas/{pets[0]}/', owner)['descripcion'] == original['descripcion']
    ai.append({'flujo': 'descripción Django', 'estado': 'DISPONIBLE', 'respuesta': draft})
    labels = call('POST', D, f'/api/mascotas/{pets[0]}/sugerir-temperamento/', owner, json={})
    assert not labels['guardado']
    assert call('GET', D, f'/api/mascotas/{pets[0]}/', owner)['temperamento'] == original['temperamento']
    call('PATCH', D, f'/api/mascotas/{pets[0]}/', owner, json={'temperamento': labels['etiqueta'], 'descripcion': draft['borrador']})
    stored = call('GET', D, f'/api/mascotas/{pets[0]}/', owner)
    assert stored['temperamento'] == labels['etiqueta'] and stored['descripcion'] == draft['borrador']
    filtered = call('GET', S, '/api/catalogo', a, params={'search': prefix, 'temperamento': labels['etiqueta']})
    assert pets[0] in [p['id'] for p in filtered['items']]
    ai.append({'flujo': 'temperamento Django', 'estado': 'DISPONIBLE', 'respuesta': labels})
    checks.append('US-29/30: IA real devuelve borrador/etiqueta sin guardar; confirmación explícita persiste y actualiza filtros Spring')
private = Path('/tmp/petly-matching-fixtures.json')
with os.fdopen(os.open(private, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600), 'w') as f:
    json.dump({'cuentas': accounts, 'mascotas': pets, 'busqueda': prefix}, f)
report = {'fecha': datetime.now(timezone.utc).isoformat(), 'tipo': 'APIs reales Spring-Django-MariaDB', 'mascotasPrueba': pets, 'checks': checks, 'gemini': ai}
(ROOT / 'docs/evidencias/compatibilidad-sprint2-real.json').write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n')
print(json.dumps(report, ensure_ascii=False, indent=2))
