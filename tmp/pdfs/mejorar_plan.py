from pathlib import Path
import ast
from xml.sax.saxutils import escape
from reportlab.platypus import SimpleDocTemplate, Paragraph, Table, TableStyle, Spacer, PageBreak, KeepTogether
from reportlab.lib import colors
from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
from reportlab.lib.pagesizes import A4
root=Path('/home/meli_dev/Documentos/Local_Petly_Proyect/Petly_Proyect')
data={}
for n in ast.parse((root/'tmp/pdfs/crear_plan.py').read_text()).body:
 if isinstance(n,ast.Assign) and isinstance(n.targets[0],ast.Name) and n.targets[0].id in ['closed','carried','selected','deferred']:
  data[n.targets[0].id]=ast.literal_eval(n.value)
old=root/'docs/planificacion-sprint-02.md'
archive=root/'docs/comparacion-backlog-sprint-02.md'
if not archive.exists(): archive.write_text(old.read_text())
responsibles={'US-21':'Angie','US-16':'Meliton','US-17':'Meliton','US-12':'Meliton','US-24':'Angie','US-18':'Angie','US-46':'Mayra','US-11':'Mayra','US-13':'Mayra','US-06':'Angie','US-07':'Mayra','US-08':'Angie','US-10':'Angie (apoyo: Mayra)'}
rows=[(f'{a} - {b} (cierre pendiente)',responsibles[a],c) for a,b,c,d in data['closed']]
rows +=[(f'{a} - {b}',responsibles[a],str(c)) for a,b,c,*_ in data['selected']]
rows += [('Integración final, correcciones y evidencia de entrega','Meliton (apoyo: equipo)','5'),('TOTAL DEL SPRINT 2','','30')]
intro='Duración: 2 semanas. Dedicación: 3 horas al día durante 10 días laborables. Capacidad total: 30 horas-persona. Responsables propuestos según el liderazgo del proyecto; las horas no se multiplican por integrante.'
objective='Objetivo: cerrar la aceptación pendiente del Sprint 1 y completar en React y APIs el flujo de solicitudes de adopción: envío, evaluación, consulta y cancelación.'
notes='Las nueve US arrastradas ya están implementadas: sus 4 horas cubren revisión y evidencia. Las cuatro US nuevas suman 21 horas e incluyen pruebas específicas. Se reservan 5 horas para integración y entrega. US-15 (foto de mascota) se revisa junto con US-11, sin horas adicionales.'
closure=[
('US-21 - Registro e inicio de sesión',['El registro rechaza correo inválido o duplicado y el acceso dirige al área del rol correcto.','Dos contraseñas incorrectas consecutivas bloquean la cuenta un minuto; tras el plazo se permite acceder. React y Android reflejan el bloqueo.']),
('US-16 - Cuestionario de vivienda y estilo de vida',['Se guardan, recuperan y editan respuestas parciales y finalizadas desde React y Android.','Los campos requeridos se validan y otro adoptante no puede consultar o cambiar el perfil.']),
('US-17 - Convivencia',['Niños y otras mascotas admiten Sí, No y sin responder; los valores se conservan al volver a entrar.','Los cambios solo pueden realizarlos el titular y se reflejan en web y Android.']),
('US-12 - Catálogo de mascotas',['Búsqueda, filtros combinados y paginación conservan los criterios en web y Android.','El detalle muestra fotografía y disponibilidad; una búsqueda sin resultados presenta un mensaje claro.']),
('US-24 - Recuperación por correo',['Mailpit recibe el código opaco de 22 caracteres y el enlace compatible con web/Android.','El código vence a los 15 minutos, funciona una vez y el cambio de contraseña revoca la sesión anterior.']),
('US-18 - Consulta autorizada del perfil',['Solo el albergue elegido puede consultar el perfil con autorización vigente.','Revocar bloquea la consulta incluso desde la API; compartir el perfil no crea una solicitud.']),
('US-46 - Información e infraestructura del albergue',['ADMIN registra, consulta, edita y desactiva instituciones vinculadas a cuentas ALBERGUE existentes.','El propietario edita sus datos; se rechaza eliminar una institución con mascotas y otro albergue no puede modificarla.']),
('US-11 - Mascotas y fotografía',['El albergue crea, consulta, edita y elimina sus mascotas, respetando las validaciones.','La foto es JPEG/PNG de hasta 5 MB y se visualiza en el catálogo; otro albergue no puede modificar la mascota. Incluye aceptación de US-15.']),
('US-13 - Disponibilidad de la mascota',['Solo el propietario cambia entre Disponible, En Proceso y Adoptado; se rechazan estados inválidos.','El cambio persiste y aparece al consultar el catálogo. Con solicitudes nuevas, no se permite liberar una mascota con aprobación activa.'])]
newcriteria=[
('US-06 - Enviar una solicitud de adopción',[
 'El adoptante autenticado tiene perfil finalizado y autorización expresa vigente para el albergue de una mascota disponible.',
 'La solicitud queda persistida con identificador, fecha y estado PENDIENTE; la interfaz confirma el envío.',
 'Se rechazan duplicados activos, mascotas no disponibles, albergues inactivos y operaciones a nombre de otro usuario. Enviar no reserva la mascota.']),
('US-07 - Evaluar solicitudes del propio albergue',[
 'Solo el albergue propietario lista y consulta sus solicitudes y registra una decisión.',
 'Observar o rechazar exige un motivo. Aprobar exige permiso vigente para consultar el perfil y mascota disponible.',
 'La aprobación cambia la mascota a En Proceso e impide dos aprobaciones activas simultáneas. No registra todavía una adopción concluida.',
 'La consulta del perfil sigue respetando la revocación de US-18; no se almacena una copia del perfil para eludirla.']),
('US-08 - Consultar el estado de mis solicitudes',[
 'Solo el titular consulta sus solicitudes con mascota, albergue, estado, fecha y motivo de la decisión.',
 'Los datos se actualizan al abrir la pantalla o pulsar Actualizar; se contemplan lista vacía y errores.',
 'El alcance es consulta bajo demanda. No incluye notificaciones push ni actualización automática en tiempo real.']),
('US-10 - Cancelar una solicitud antes de aprobarla',[
 'Solo el titular puede cancelar una solicitud PENDIENTE u OBSERVADA, tras confirmar la acción.',
 'Se conserva el registro con estado CANCELADA y fecha; no se permiten decisiones posteriores.',
 'Se rechaza cancelar una solicitud APROBADA o RECHAZADA. Ante aprobación y cancelación simultáneas, solo una transición se completa.'])]
common=[
 'Las funcionalidades nuevas operan en React y APIs con persistencia real; la ampliación a Android no se incluye en estas 30 horas.',
 'Cada US cuenta con evidencia, pruebas relevantes de permisos y errores y revisión de otro integrante. Las US arrastradas no se aprueban solo por estar implementadas.',
 'Django administra solicitudes y migraciones; Spring conserva cuentas, perfil y consentimientos. La integración debe mantener una única autoridad de autenticación.',
 'PENDIENTE puede pasar a OBSERVADA, APROBADA, RECHAZADA o CANCELADA. OBSERVADA puede pasar a APROBADA, RECHAZADA o CANCELADA. Los demás estados son finales en este incremento.',
 'No se incluye edición/reenvío de solicitudes observadas, contrato digital, cierre de adopción, matchmaking ni IA. Los demás pendientes y sus horas permanecen en el documento de comparación del backlog.'
]
md=['# Planificación del Sprint 2 - Petly','','Actualización: 14 de octubre de 2026.','',intro,'',objective,'','## Cuadro de planificación','','| Historia de usuario | Responsable | Estimación de tiempo (horas) |','| --- | --- | --- |']
md+=['| '+' | '.join(r)+' |' for r in rows]
md+=['',notes,'','## Criterios de aceptación','','### Historias arrastradas: aceptación del trabajo existente','']
for title,bullets in closure:
 md+=['#### '+title,'']+['- '+b for b in bullets]+['']
md+=['### Historias nuevas del Sprint 2','']
for title,bullets in newcriteria:
 md+=['#### '+title,'']+['- '+b for b in bullets]+['']
md+=['### Integración y entrega','']+['- '+b for b in common]+['','Las 5 horas finales cubren 2 horas de integración y regresión, 2 horas de correcciones y 1 hora de evidencia y demostración. Si los defectos superan la reserva, reducir el alcance antes de añadir nuevas US.','','Comparación completa y estimaciones de historias aplazadas: [comparacion-backlog-sprint-02.md](comparacion-backlog-sprint-02.md).','']
old.write_text('\n'.join(md))
styles=getSampleStyleSheet()
styles.add(ParagraphStyle(name='Main',fontName='Helvetica-Bold',fontSize=21,leading=25,textColor=colors.HexColor('#183e4a'),spaceAfter=12))
styles.add(ParagraphStyle(name='BodyP',fontName='Helvetica',fontSize=10,leading=14,textColor=colors.HexColor('#2e424b'),spaceAfter=7))
styles.add(ParagraphStyle(name='Sub',fontName='Helvetica-Bold',fontSize=11,leading=15,textColor=colors.HexColor('#087d79'),spaceBefore=7,spaceAfter=4))
styles.add(ParagraphStyle(name='Cell',fontName='Helvetica',fontSize=9,leading=12,textColor=colors.HexColor('#2e424b')))
styles.add(ParagraphStyle(name='Head',fontName='Helvetica-Bold',fontSize=9,leading=12,textColor=colors.white))
styles.add(ParagraphStyle(name='BulletP',fontName='Helvetica',fontSize=9.7,leading=13.3,leftIndent=9,firstLineIndent=-9,spaceAfter=3,textColor=colors.HexColor('#2e424b')))
def p(t,style='BodyP'):return Paragraph(escape(t),styles[style])
def addcards(out,cards):
 for title,bullets in cards:
  out.append(KeepTogether([p(title,'Sub')]+[p('- '+b,'BulletP') for b in bullets]))
story=[p('PETLY / SPRINT 2','Sub'),p('Planificación del Sprint 2','Main'),p(intro),p(objective),p('Cuadro de planificación','Sub')]
heads=['Historia de usuario','Responsable','Estimación\n(horas)']
t=Table([[p(h,'Head') for h in heads]]+[[p(c,'Cell') for c in r] for r in rows],colWidths=[323,107,69],repeatRows=1)
t.setStyle(TableStyle([('BACKGROUND',(0,0),(-1,0),colors.HexColor('#183e4a')),('ROWBACKGROUNDS',(0,1),(-1,-2),[colors.HexColor('#eef5f5'),colors.white]),('BACKGROUND',(0,-1),(-1,-1),colors.HexColor('#d5edeb')),('VALIGN',(0,0),(-1,-1),'TOP'),('TOPPADDING',(0,0),(-1,-1),6),('BOTTOMPADDING',(0,0),(-1,-1),6),('LEFTPADDING',(0,0),(-1,-1),7),('RIGHTPADDING',(0,0),(-1,-1),7),('LINEBELOW',(0,0),(-1,0),1,colors.HexColor('#109f98'))]))
story += [t,Spacer(1,10),p(notes)]
story += [PageBreak(),p('Criterios de aceptación','Main'),p('Historias arrastradas del Sprint 1','Sub'),p('Se revisa el trabajo existente durante el Sprint 2. El responsable coordina el cierre y otro integrante confirma los criterios.')]
addcards(story,closure)
story += [PageBreak(),p('Criterios de aceptación','Main'),p('Historias nuevas del Sprint 2','Sub')]
addcards(story,newcriteria)
story += [PageBreak(),p('Criterios de aceptación','Main'),p('Integración y entrega','Sub')]+[p('- '+b,'BulletP') for b in common]
story +=[Spacer(1,9),p('Reserva de integración y entrega: 5 horas','Sub'),p('2 h para integración y regresión; 2 h para correcciones; 1 h para evidencia y demostración. Si la reserva resulta insuficiente, retirar primero US-10 y registrar la cancelación como pendiente.'),p('Secuencia de las dos semanas','Sub'),p('Semana 1: revisión y cierre del trabajo existente, US-06 completa e inicio de US-07. Semana 2: finalizar US-07, completar US-08 y US-10, integrar y demostrar. La suma de cierre, desarrollo y reserva permanece en 30 horas.'),p('Alcance del documento','Sub'),p('Este cuadro contiene únicamente el trabajo seleccionado para el Sprint 2. La comparación completa con el PDF original y las estimaciones de las historias aplazadas se conservan en docs/comparacion-backlog-sprint-02.md. No se eliminan pendientes del proyecto.')]
def footer(c,d):
 w,h=A4;c.setFillColor(colors.HexColor('#109f98'));c.rect(0,h-8,w,8,fill=1,stroke=0)
 c.setStrokeColor(colors.HexColor('#c8d9dc'));c.line(48,42,w-48,42);c.setFont('Helvetica',8);c.setFillColor(colors.HexColor('#5c727a'));c.drawString(48,28,'Petly | Sprint 2 | 2 semanas | 30 horas-persona');c.drawRightString(w-48,28,str(d.page))
SimpleDocTemplate(str(root/'output/pdf/planificacion-sprint-02-petly.pdf'),pagesize=A4,leftMargin=48,rightMargin=48,topMargin=35,bottomMargin=57,title='Planificación del Sprint 2 - Petly',author='Petly').build(story,onFirstPage=footer,onLaterPages=footer)
assert sum(float(r[2].replace(',','.')) for r in rows[:-1])==30
print('PDF y Markdown actualizados. Total: 30 h. Comparación anterior conservada.')
