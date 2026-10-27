from pathlib import Path
from xml.sax.saxutils import escape
from reportlab.platypus import SimpleDocTemplate,Paragraph,Table,TableStyle,Spacer,PageBreak,KeepTogether
from reportlab.lib import colors
from reportlab.lib.styles import getSampleStyleSheet,ParagraphStyle
from reportlab.lib.pagesizes import A4,landscape
R=Path('/home/meli_dev/Documentos/Local_Petly_Proyect/Petly_Proyect')
context={}
src=(R/'tmp/pdfs/sprint2_definitivo.py').read_text()
exec(src[:src.index('assert len(D)==25')],context)
previous={v[0]:v for v in context['D']}
T=[
('US-01','Motor de Recomendación con % de Compatibilidad entre Mascota y Adoptante.'),
('US-06','Formulario y Envío de Solicitud de Adopción por parte del rol de Adoptante.'),
('US-07','Aprobación, Observación o Rechazo de Solicitud desde el Panel de Albergue'),
('US-02','Clasificación de posibles Candidatos Mascotas por Compatibilidad entre Mascota y Adoptante a partir del perfil del Adoptante.'),
('US-08','Seguimiento de Estado de Solicitud con línea de Tiempo'),
('US-03','Recálculo de Compatibilidad al Actualizar el Perfil de Adoptante.'),
('US-09','Firma e Integración de Contrato Digital de Adopción'),
('US-29','Descripción generada de la mascota partir de una foto'),
('US-04','Desglose Explicativo del % de Compatibilidad de Mascota en el Panel de Adoptante'),
('US-28','Generación Automática de Afiches para Redes Sociales como primer borrador en rol de Albergue'),
('US-10','Cancelación o Desistimiento de Solicitud de Adopcion por parte del Adoptante'),
('US-05','Ajustar el peso de los criterios de compatibilidad (ejemplo vivienda, rutina y convivencia) para medir la compatibilidad entre mascota y adoptante.'),
('US-30','Etiquetado Automático de Temperamento con IA según una perfil y foto de la mascota'),
('US-49','Registro de Necesidades y Donaciones para el Albergue'),
('US-50','Publicación de Eventos de Adopción Presencial'),
('US-19','Carga de Fotos de la Vivienda del Adoptante en el formulario de adopción, desde la app Móvil'),
('US-14','Guardar Mascotas Favoritas desde el perfil del Adoptante con el Icono de Favoritos.'),
('US-18','Verificación de Datos e Historial de Usuarios por Albergue'),
('US-15','Carga de Foto de Presentación de la Mascota'),
('US-22','Verificación KYC de Documentos (DNI del Adoptante verificando con datos de Reniec según el DNI)'),
('US-23','Gestión Administradora de Verificación KYC desde el Panel de Admin en React'),
('US-20','Actualización de Disponibilidad y Horarios del Adoptante')]
C={
'US-01':('Angie define reglas, pesos y API Spring. Mayra expone atributos de mascotas en Django. Meliton integra el porcentaje en Android y prueba perfiles.'),
'US-06':('Angie implementa formulario React y envío desde Spring. Mayra crea persistencia y validaciones Django. Meliton prueba el envío integrado y los duplicados.'),
'US-07':('Mayra implementa panel React del albergue y decisiones Django. Angie conecta perfil, permisos y consentimiento Spring. Meliton prueba transiciones y aprobaciones simultáneas.'),
'US-02':('Angie ordena mascotas por puntaje y crea el listado React. Mayra garantiza disponibilidad y datos de mascotas. Meliton integra el orden en Android y verifica desempates.'),
'US-08':('Angie implementa consulta Spring y línea de tiempo React. Mayra registra eventos y fechas en Django. Meliton verifica orden de eventos, privacidad y estados vacíos.'),
'US-03':('Meliton conecta guardado de perfil y refresco en Android. Angie invalida resultados en Spring y actualiza React. Mayra comprueba que se usen los datos vigentes de mascotas.'),
'US-09':('Mayra genera el contrato y conecta el proveedor de firma. Angie verifica identidad de firmantes y permisos. Meliton prueba descarga, fallos y registro de entrega.'),
'US-29':('Mayra conecta foto, proveedor IA y editor React del albergue. Angie implementa validación de respuesta y gestión de errores. Meliton prueba generación, edición y conservación del texto.'),
'US-04':('Meliton presenta aportes y explicaciones en Android. Angie expone el desglose Spring y lo muestra en React. Mayra valida categorías y coherencia con la ficha de mascota.'),
'US-28':('Mayra crea plantilla, vista previa y descarga en React. Angie facilita datos públicos y validaciones. Meliton comprueba legibilidad, resolución y manejo de fotos faltantes.'),
'US-10':('Angie implementa confirmación React y petición Spring. Mayra persiste la cancelación y protege la transición en Django. Meliton prueba propiedad y concurrencia con aprobación.'),
'US-05':('Angie implementa configuración ADMIN y versiones en Spring. Mayra revisa permisos y validez de los criterios. Meliton verifica recálculo y consistencia de puntajes en Android.'),
'US-30':('Mayra conecta perfil, foto y sugerencias IA en el panel. Angie valida etiquetas permitidas y respuesta del proveedor. Meliton prueba confirmación humana y visualización en catálogo.'),
'US-49':('Mayra implementa necesidades y registro manual de donaciones en Django/React. Angie conecta consulta pública para adoptantes. Meliton prueba cantidades, permisos y privacidad del donante.'),
'US-50':('Mayra implementa CRUD de eventos y cancelación Django/React. Angie integra listado público del adoptante. Meliton revisa fechas, ubicación y rechazo a cambios de otro albergue.'),
'US-19':('Meliton implementa selección y carga desde Android. Angie vincula identidad, autorización y solicitud desde Spring. Mayra almacena fotos privadas y aplica permisos Django.'),
'US-14':('Meliton implementa icono y lista en Android. Angie persiste favoritos en Spring e integra React. Mayra comprueba que se conserve la disponibilidad real de cada mascota.'),
'US-18':('Angie verifica consentimiento y expone el perfil Spring. Mayra muestra perfil e historial interno autorizado en React/Django. Meliton prueba revocación y acceso desde otro albergue.'),
'US-15':('Mayra verifica carga, reemplazo y validación Django/React. Angie comprueba la URL y datos de foto en catálogo Spring/React. Meliton verifica descarga y visualización Android.'),
'US-22':('Angie integra servicio autorizado RENIEC y estados KYC. Mayra protege documentos y consulta administrativa. Meliton implementa captura/carga Android y consulta del resultado.'),
'US-23':('Mayra implementa panel ADMIN React y revisión de documentos. Angie integra estados KYC, permisos y auditoría. Meliton verifica resultado visible al adoptante y acceso denegado.'),
'US-20':('Meliton implementa horarios y validación Android. Angie persiste franjas en Spring y crea formulario React. Mayra integra consulta del albergue con autorización vigente.')}
D=[]
for i,title in T:
 row=list(previous[i]);row[1]=title;row[4]=C[i]
 if i=='US-02':
  row[2:4]=['Angie','Meliton'];row[5]=6
  row[7]=['El catálogo React/Android clasifica mascotas disponibles de albergues activos usando el perfil del adoptante autenticado y el puntaje de US-01.', 'El orden es de mayor a menor compatibilidad; empates se resuelven por identificador de mascota ascendente. Las mascotas con información insuficiente se identifican y aparecen al final.', 'Los filtros y la paginación mantienen el orden. Cambiar el perfil y recalcular actualiza la clasificación; no se expone el perfil privado a otros adoptantes.']
 if i=='US-08':
  row[5]=5
  row[7]=['El titular consulta una línea de tiempo de cada solicitud con estado, fecha y motivo de los eventos registrados, ordenados del más antiguo al más reciente.', 'Cada envío, observación, aprobación, rechazo o cancelación agrega un evento sin sobrescribir los anteriores. La línea destaca el estado actual y solo muestra eventos realmente ocurridos.', 'Al abrir o pulsar Actualizar se recuperan los eventos persistidos; se manejan lista vacía y error. Otro adoptante no puede acceder. No exige actualización automática ni notificaciones push.']
 if i=='US-29':
  row[5]=12
  row[7]=['El albergue propietario selecciona una foto válida de su mascota y solicita a la IA un borrador de descripción; el proveedor procesa la imagen y devuelve texto editable.', 'El borrador describe rasgos visibles y no inventa nombre, salud, edad exacta, raza confirmada ni conducta. Cuando no puede identificar un rasgo, lo omite o indica que no está determinado.', 'El albergue puede editar, descartar o confirmar el borrador antes de guardarlo o publicarlo. Un fallo conserva la descripción anterior y permite reintentar; la foto no se publica por generar texto.']
 if i=='US-30':
  row[7]=['El albergue propietario solicita etiquetas usando el perfil registrado de la mascota y su foto, sin sustituirlos por el perfil del adoptante; se valida que ambos recursos estén disponibles.', 'La IA devuelve sugerencias de categorías permitidas y su explicación. No se considera la imagen una prueba concluyente de conducta: los rasgos deben apoyarse en observaciones registradas; si faltan, se conserva Por evaluar.', 'Las etiquetas se muestran como sugerencias hasta confirmación o corrección del albergue. Un error del proveedor no reemplaza las etiquetas vigentes; las confirmadas se reflejan en catálogo y filtros.']
 D.append(tuple(row))
TOTAL=sum(x[5] for x in D)
assert len(D)==22
intro='Se mantienen literalmente las 22 descripciones y sus identificadores del PDF Sprint_2.pdf, en el mismo orden. Solo se unen los saltos de línea propios del documento; no se corrigen palabras, signos ni redacción. Responsables, colaboración, estimaciones y criterios son propuestas añadidas.'
objective='Objetivo del Sprint: Desplegar el algoritmo de compatibilidad (Matchmaking), los servicios de IA generativa/visión para publicaciones y el flujo central de solicitudes de adopción.'
capacity=f'Las estimaciones son horas-persona restantes por historia e incluyen integración y pruebas. Alcance completo: {TOTAL} h antes de reserva. La capacidad previamente indicada es 30 h (10 días laborables x 3 h); las 22 historias no caben en ese plazo. No se convierten los SP originales directamente en horas.'
selection='Selección recomendada para las dos semanas: US-06 (8 h), US-07 (7 h), US-08 (5 h) y US-10 (3 h) = 23 h; más 7 h para integración final, revisión y correcciones = 30 h. Esta selección no elimina ninguna historia del cuadro completo.'
common='El responsable coordina la historia completa y el revisor es otra persona. Cada US requiere persistencia real, integración en las interfaces indicadas, pruebas de permisos y errores, documentación sin secretos y evidencia revisada. La colaboración detallada se incluye en las horas de la US; no se suma tres veces.'
notes='Las nuevas solicitudes se estiman en React y APIs. Android se incluye en compatibilidad, favoritos, horarios y fotos de vivienda donde se indica. IA, firma digital y RENIEC requieren proveedor, acceso autorizado y entorno de prueba disponibles; sus esperas externas no se incluyen en las horas.'
md=['# Sprint 2 - Planificación con descripciones originales','','Actualización: 14 de octubre de 2026.','',objective,'',intro,'',capacity,'',selection,'',notes,'','## Cuadro de planificación','','| Historia | Responsable principal | Revisor | Colaboración necesaria entre los tres integrantes | Tiempo estimado (h) |','| --- | --- | --- | --- | --- |']
md +=[f'| {i} - {t} | {p} | {r} | {c} | {h} |' for i,t,p,r,c,h,s,ac in D]
md +=[f'| **TOTAL DEL ALCANCE COMPLETO** | | | **Sin reserva** | **{TOTAL}** |','','## Criterios de aceptación por historia','']
for i,t,p,r,c,h,s,ac in D:
 md +=[f'### {i} - {t}','']+['- '+v for v in ac]+['']
md +=['## Criterios comunes y dependencias','',common,'',notes,'','US-01 precede a US-02, US-03, US-04 y US-05. US-06 precede a US-07, US-08, US-10, fotos US-19 e historial de solicitudes US-18. US-09 parte de solicitud aprobada; US-23 comparte estados con US-22. La carga existente de US-15 se verifica sin reconstruirla.','']
(R/'docs/sprint-02-descripciones-originales.md').write_text('\n'.join(md))
styles=getSampleStyleSheet()
for name,font,size,lead,col in [('TitleX','Helvetica-Bold',20,24,'#173e49'),('SubX','Helvetica-Bold',11,15,'#087b78'),('BodyX','Helvetica',10,13.5,'#2e424b'),('CellX','Helvetica',9.1,12,'#2e424b'),('HeadX','Helvetica-Bold',9.3,12,'#ffffff')]:
 styles.add(ParagraphStyle(name=name,fontName=font,fontSize=size,leading=lead,textColor=colors.HexColor(col),spaceAfter=8 if name in ['BodyX','TitleX'] else 4))
styles.add(ParagraphStyle(name='BulletX',fontName='Helvetica',fontSize=10,leading=13.5,leftIndent=10,firstLineIndent=-10,spaceAfter=4,textColor=colors.HexColor('#2e424b')))
def P(t,st='BodyX'):return Paragraph(escape(t),styles[st])
W,H=landscape(A4)
story=[P('PETLY / SPRINT 2','SubX'),P('Planificación con descripciones originales','TitleX'),P(objective),P(intro),P(capacity),P(selection),P(notes),Spacer(1,5)]
heads=['Historia','Responsable principal','Revisor','Colaboración necesaria entre los tres integrantes','Tiempo estimado (h)']
rows=[[P(h,'HeadX') for h in heads]]
for i,t,p,r,c,h,s,ac in D:rows.append([P(i+' - '+t,'CellX'),P(p,'CellX'),P(r,'CellX'),P(c,'CellX'),P(str(h),'CellX')])
rows.append([P('TOTAL DEL ALCANCE COMPLETO','CellX'),P(''),P(''),P('Sin reserva','CellX'),P(str(TOTAL),'CellX')])
t=Table(rows,colWidths=[220,77,65,318,66],repeatRows=1,hAlign='LEFT')
t.setStyle(TableStyle([('BACKGROUND',(0,0),(-1,0),colors.HexColor('#173e49')),('ROWBACKGROUNDS',(0,1),(-1,-2),[colors.HexColor('#edf5f5'),colors.white]),('BACKGROUND',(0,-1),(-1,-1),colors.HexColor('#d6eeeb')),('VALIGN',(0,0),(-1,-1),'TOP'),('TOPPADDING',(0,0),(-1,-1),5),('BOTTOMPADDING',(0,0),(-1,-1),5),('LEFTPADDING',(0,0),(-1,-1),7),('RIGHTPADDING',(0,0),(-1,-1),7)]))
story +=[t,PageBreak(),P('Criterios de aceptación por historia','TitleX')]
for i,title,p,r,c,h,s,ac in D:
 story.append(KeepTogether([P(i+' - '+title,'SubX')]+[P('- '+v,'BulletX') for v in ac]))
story +=[PageBreak(),P('Condiciones de entrega','TitleX'),P('Criterios comunes','SubX'),P(common),P('Dependencias','SubX'),P('Compatibilidad: US-01 precede a US-02, US-03, US-04 y US-05. Solicitudes: US-06 precede a US-07, US-08, US-10, fotos US-19 e historial interno US-18. El contrato US-09 parte de una solicitud aprobada. La revisión administrativa US-23 utiliza los estados definidos con US-22.'),P('Interfaces y servicios externos','SubX'),P(notes),P('Comprobación de la capacidad','SubX'),P(selection),P('Distribución de la reserva: 3 h de integración/regresión, 2 h de correcciones y 2 h de revisión, evidencia y demostración. Si no basta, replanificar las historias seleccionadas antes de incorporar nuevas.'),P('Límites de aceptación','SubX'),P('US-02 clasifica mascotas para el adoptante, no personas candidatas para el albergue. US-08 muestra una línea de tiempo; no exige tiempo real. US-29 genera una descripción desde la foto. US-30 procesa perfil y foto de la mascota como sugerencia revisable; no toma una imagen como evidencia concluyente de temperamento. US-18 limita el historial a solicitudes internas del mismo albergue. Estos criterios precisan las descripciones originales sin cambiarlas.')]
def footer(c,d):
 c.setFillColor(colors.HexColor('#109c94'));c.rect(0,H-8,W,8,fill=1,stroke=0);c.setStrokeColor(colors.HexColor('#c8dadd'));c.line(48,37,W-48,37);c.setFont('Helvetica',8);c.setFillColor(colors.HexColor('#58727a'));c.drawString(48,23,'Petly | Sprint 2 | 22 historias con descripciones originales');c.drawRightString(W-48,23,str(d.page))
output=R/'output/pdf/sprint-02-descripciones-originales.pdf'
SimpleDocTemplate(str(output),pagesize=landscape(A4),leftMargin=48,rightMargin=48,topMargin=30,bottomMargin=49,title='Sprint 2 - Descripciones originales y criterios de aceptación',author='Petly').build(story,onFirstPage=footer,onLaterPages=footer)
# Comparación literal de títulos con la primera columna del PDF fuente, ignorando saltos de línea.
import pdfplumber,re
source=[]
with pdfplumber.open('/home/meli_dev/Descargas/Sprint_2.pdf') as pdf:
 for page in pdf.pages:
  table=page.extract_table()
  if table:
   for row in table:
    if row and row[0]:source.append(row[0])
normalize=lambda s: re.sub(r'\s+',' ',s).strip()
joined=normalize(' '.join(source))
missing=[t for i,t in T if normalize(t) not in joined]
# Los títulos cortados por página se unen sin incluir la cabecera repetida.
joined2=normalize(' '.join(s for s in source if normalize(s)!='Título'))
missing=[t for i,t in T if normalize(t) not in joined2]
assert not missing,missing
print({'historias':len(D),'horas':TOTAL,'titulos_comparados_literalmente':len(T),'pdf':str(output)})
