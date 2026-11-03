**Evaluación de respuestas del Asistente Petly — 3 de noviembre de 2026**

Evaluación completada con Gemini real, modelo `gemini-3.5-flash-lite`, usando el servicio Java de Petly y preguntas sintéticas. Se revisaron respuestas sobre catálogo, formulario, adopción y cuidados básicos, además de privacidad, límites y preguntas de seguimiento. Ninguna consulta usa cuentas reales ni modifica la base.

Los criterios se definieron antes de ejecutar: exactitud respecto a la app, instrucciones accionables, pertinencia de la respuesta, uso del contexto y ausencia de afirmaciones o indicaciones peligrosas. «Cumple» significa que satisface los criterios del caso; «Parcial» conserva una omisión o imprecisión; «No cumple» contiene una indicación incorrecta. La revisión fue de contenido, sin encuestas de usuarios ni autoevaluación de Gemini.

| Área | Inicial: cumplimiento estricto | Final consolidado |
|---|---:|---:|
| Catálogo: navegación y límites | 0/5 | 6/6 |
| Formulario y contexto | 3/5 | 6/6 |
| Orientación de adopción | 0/5 | 5/5 |
| Cuidados básicos | 1/5 | 6/6 |
| Privacidad y límites de dominio | 2/2 | 2/2 |

La ronda inicial tuvo 6 casos que cumplieron, 11 parciales y 5 que no cumplieron. El resultado final consolidado cumple los criterios de los 25 casos distintos, incluyendo tres seguimientos añadidos. Se realizaron seis repeticiones de estabilidad y tres regresiones dirigidas: 34 respuestas reales en la ronda completa y sus repeticiones. En la ronda completa se encontraron tres imprecisiones menores adicionales; se corrigieron y repitieron los tres casos afectados antes de consolidar el resultado. Cada respuesta identifica su archivo de procedencia y versión de instrucciones.

**Correcciones verificadas**

- Catálogo: retiradas recomendaciones de filtros inexistentes de raza, ciudad, distancia y edad; se distingue la barra inferior de los controles dentro de Catálogo.
- Formularios: rutas Perfil > Ir al Cuestionario, rango real de horas fuera, borrador frente a finalización y continuidad sin repetir datos.
- Adopción: solicitud desde la ficha y Quiero Adoptar; ninguna aprobación automática por porcentaje ni reservas ficticias; normas legales sujetas a jurisdicción.
- Cuidados: respuestas adaptadas a especie y edad, sin dosis ni recetas, atención prioritaria en urgencias y actividad individualizada según energía, edad y salud. Se eliminaron cierres ajenos a la consulta urgente.
- Privacidad y límites: no solicitar claves/DNI completo, no simular acceso administrativo ni acciones realizadas.

**Evidencia y disponibilidad**

- [Inicial: 22 casos](inicial.json).
- [Ronda intermedia: 3 casos limitados por cuota](intermedia.json). No se contaron como aprobados. Los 429 se produjeron al ejecutar un lote rápido; después se espaciaron las llamadas.
- [Ronda completa: 25 respuestas](ronda-completa.json).
- [Seis repeticiones de estabilidad](estabilidad.json).
- [Tres regresiones finales](ajustes-finales.json).
- [Resultado consolidado con revisión caso por caso](final.json).

Las 34 respuestas de la ronda completa y repeticiones se recibieron sin casos pendientes. Mediana de tiempo de respuesta: 1.41 s; percentil 95 de esta muestra: 5.80 s. Estos tiempos no representan una prueba de carga ni un compromiso de disponibilidad del proveedor.

Compilación y pruebas del backend: 8 pruebas pasaron (`ChatGeminiServiceTest` y `ChatControllerTest`). Esta evaluación no cambió Android; las pruebas de interfaz y las 30 pruebas Android del desarrollo anterior no se presentan como repetidas en esta ronda.

**Límite del catálogo**

El chatbot puede orientar con los controles reales y reconocer lo que no sabe. No está conectado a una consulta en vivo del catálogo; por tanto, no puede confirmar por sí mismo que Domi esté disponible, su edad ni cuántas mascotas hay hoy. Aprobar esos casos significa que responde con honestidad y una ruta de verificación, no que haya consultado o confirmado esos datos.

El historial enviado al modelo incluye contexto reciente; no se certifica memoria ilimitada. Tampoco se evaluaron aquí el guardado real de todos los campos del cuestionario, trámites completos de adopción ni atención clínica. La calidad futura debe seguir supervisándose con este banco de casos y con revisiones de usuarios reales.

**Referencias de verificación**

La navegación y los campos se contrastaron con `CatalogoScreen.kt`, `CuestionarioScreen.kt`, `PerfilDraft.kt`, `SolicitudAdopcionScreen.kt` y `DetalleMascotasScreen.kt` del repositorio. Los criterios de seguridad clínica se contrastaron con:

- [FDA: analgésicos para mascotas](https://www.fda.gov/animal-veterinary/animal-health-literacy/get-facts-about-pain-relievers-pets), sobre evitar medicación humana sin supervisión y toxicidad del acetaminofén en gatos.
- [Cornell: ingestión de chocolate en perros](https://www.vet.cornell.edu/departments-centers-and-institutes/riney-canine-health-center/canine-health-topics/chocolate-toxicity-what-should-i-do-if-my-dog-eats-chocolate), sobre contacto veterinario inmediato.
- [AVMA: primeros auxilios para mascotas](https://ebusiness.avma.org/files/productdownloads/mcm-client-brochures-pet-first-aid-2023.pdf), sobre no provocar vómito ni medicar sin indicación profesional.

Para repetir la evaluación (consume la API configurada), después de ejecutar las pruebas del backend:

```bash
backend-admin/.venv/bin/python scripts/evaluar-chatbot.py --output /tmp/chatbot-evaluacion.json
```

Para repetir solo los casos críticos:

```bash
backend-admin/.venv/bin/python scripts/evaluar-chatbot.py --ids CAT04,ADO01,ADO04,CUI02,CUI03,FOR06 --output /tmp/chatbot-estabilidad.json
```

El ejecutor usa el adaptador Java real, limita el inicio a una consulta cada seis segundos, mantiene claves fuera de argumentos e informes y marca como pendientes de revisión los nuevos resultados. Devuelve error si queda alguna consulta sin respuesta: recibir texto no equivale a aprobar su calidad.

**Comprobación posterior de la integración con React y albergues**

Antes de publicar se verificaron 10 pruebas de Spring Boot y 3 pruebas de navegador React,
además de compilar React y Android. Las pruebas unitarias Android conservan 30 casos aprobados
con resultados válidos en la caché de Gradle. Las pruebas nuevas de React simulan el backend;
las de Spring simulan Gemini: no son otra evaluación de disponibilidad o calidad del proveedor.
Se verificaron permisos por rol, contexto según plataforma, continuidad al navegar, reintento
sin duplicar consultas y limpieza del historial al cerrar sesión. La evaluación real de contenido
anterior corresponde al adoptante en Android; no certifica respuestas reales para React o albergues.

Android y React llaman a `POST /api/asistente/chat` en Spring Boot con JWT de adoptante o albergue.
Solo Spring Boot llama a Gemini. El campo opcional `platform` adapta las indicaciones de pantalla;
no concede permisos: el rol procede del JWT validado. Los clientes Android existentes pueden omitirlo.
`GEMINI_CHAT_MODEL` es opcional y usa `GEMINI_MODEL` si falta; la clave permanece en el entorno privado.
No se añadieron migraciones ni se cambiaron los contratos existentes de perfil o adopción.
