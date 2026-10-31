"""Gemini real a través de Spring/Django; solo usa las mascotas de prueba del recorrido anterior."""
import json
from datetime import datetime, timezone
from pathlib import Path
import requests
root=Path(__file__).resolve().parents[1]
fixture=json.loads(Path('/tmp/petly-matching-fixtures.json').read_text())
pet=fixture['mascotas'][0]
S='http://127.0.0.1:8080';D='http://127.0.0.1:8000'
def call(method,base,path,role,**kwargs):
    token=fixture['cuentas'][role]['token']
    return requests.request(method,base+path,headers={'Authorization':'Bearer '+token},timeout=65,**kwargs)
original=call('GET',D,f'/api/mascotas/{pet}/','ALBERGUE').json()
assert original['nombre'].startswith('Sprint2 Match '), 'Solo se admiten datos de prueba'
score_before=call('GET',S,f'/api/catalogo/{pet}','ADOPTANTE').json()['compatibilidad']
checks=[]
for flow,base,path,role in [
    ('consejos',S,f'/api/v1/matches/mascota/{pet}/explicacion','ADOPTANTE'),
    ('descripcion',D,f'/api/mascotas/{pet}/generar-descripcion/','ALBERGUE'),
    ('temperamento',D,f'/api/mascotas/{pet}/sugerir-temperamento/','ALBERGUE')]:
    attempts=[]
    for attempt in range(2):
        response=call('POST',base,path,role,json={})
        data=response.json()
        attempts.append({'http':response.status_code,'respuesta':data})
        if response.status_code==200 and (flow!='consejos' or data.get('estado')=='DISPONIBLE'):break
    stored=call('GET',D,f'/api/mascotas/{pet}/','ALBERGUE').json()
    assert stored['descripcion']==original['descripcion'] and stored['temperamento']==original['temperamento']
    score_after=call('GET',S,f'/api/catalogo/{pet}','ADOPTANTE').json()['compatibilidad']
    assert score_after==score_before
    checks.append({'flujo':flow,'ok':response.status_code==200 and (flow!='consejos' or data.get('estado')=='DISPONIBLE'),'intentos':attempts,'sin_cambios_automaticos':True})
if all(c['ok'] for c in checks):
    description=checks[1]['intentos'][-1]['respuesta']['borrador']
    label=checks[2]['intentos'][-1]['respuesta']['etiqueta']
    assert not checks[1]['intentos'][-1]['respuesta']['guardado'] and not checks[2]['intentos'][-1]['respuesta']['guardado']
    saved=call('PATCH',D,f'/api/mascotas/{pet}/','ALBERGUE',json={'descripcion':description,'temperamento':label})
    assert saved.status_code==200
    persisted=call('GET',D,f'/api/mascotas/{pet}/','ALBERGUE').json()
    assert persisted['descripcion']==description and persisted['temperamento']==label
    catalog=call('GET',S,'/api/catalogo','ADOPTANTE',params={'search':fixture['busqueda'],'temperamento':label}).json()
    assert pet in [p['id'] for p in catalog['items']]
    checks.append({'flujo':'confirmacion humana simulada por PATCH explicito sobre registro de prueba','ok':True,'persistencia_y_filtro':True})
report={'tipo':'Gemini REAL mediante HTTP Spring y Django','fecha':datetime.now(timezone.utc).isoformat(),'mascota_prueba':pet,'checks':checks}
(root/'docs/evidencias/gemini-http-real-sprint2.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
print(json.dumps(report,ensure_ascii=False,indent=2))
raise SystemExit(0 if all(c['ok'] for c in checks) else 1)
