"""Real Gemini smoke check. Never prints credentials or provider error bodies."""
import os
import sys
import json
from pathlib import Path
from types import SimpleNamespace
root = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(root / 'backend-admin'))
os.environ['DJANGO_SETTINGS_MODULE'] = 'config.settings'
import django
django.setup()
if '--models' in sys.argv:
    from django.conf import settings
    from urllib.request import Request, urlopen
    request = Request('https://generativelanguage.googleapis.com/v1beta/models', headers={'x-goog-api-key': settings.GEMINI_API_KEY})
    try:
        with urlopen(request, timeout=20) as response:
            models = json.load(response)
        print(json.dumps([m['name'] for m in models.get('models', []) if 'generateContent' in m.get('supportedGenerationMethods', [])]))
    except Exception as exc:
        print(json.dumps({'error': type(exc).__name__, 'http': getattr(exc, 'code', None)}))
    raise SystemExit()
from django.core.files import File
from apps.mascotas.descripcion_ia import generar_borrador
from apps.mascotas.gemini import temperamento
photos = list((root / 'img_mascotas').glob('*.jpg')) or list((root / 'backend-admin/media').rglob('*.jpg'))
if not photos:
    print('No hay foto local para prueba real.'); raise SystemExit(1)
results = []
for name, function in [('descripcion', generar_borrador), ('temperamento', temperamento)]:
    try:
        with photos[0].open('rb') as stream:
            pet = SimpleNamespace(foto_principal=File(stream, name=photos[0].name), descripcion='Se acerca a otros perros y juega con ellos durante los paseos.', temperamento='POR_EVALUAR', energia='MEDIA', especie='PERRO')
            result = function(pet)
        results.append({'flujo': name, 'resultado': 'OK', 'respuesta': result, 'datos': 'Foto local y observación de prueba; sin persistencia'})
    except Exception as exc:
        cause = exc.__cause__
        results.append({'flujo': name, 'resultado': 'ERROR', 'tipo': type(exc).__name__, 'causa': type(cause).__name__ if cause else None, 'http': getattr(cause, 'code', None), 'mensaje': str(exc)})
from django.conf import settings
from datetime import datetime, timezone
report = {'proveedor': 'Gemini real', 'modelo': settings.GEMINI_MODEL, 'fecha': datetime.now(timezone.utc).isoformat(), 'resultados': results}
print(json.dumps(report, ensure_ascii=False, indent=2))
(root / 'docs/evidencias/gemini-real-sprint2.json').write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n')
raise SystemExit(0 if all(r['resultado'] == 'OK' for r in results) else 1)
