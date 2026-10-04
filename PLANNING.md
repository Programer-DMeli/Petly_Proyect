# PLANNING — Petly (v2)

Documento de contexto y planificación para el equipo y OpenCode.
Última actualización: 4 de octubre de 2026.
Versión 2: decisiones del Sprint 1, responsables, revisión cruzada y criterios de aceptación.

## 1. Propósito del proyecto

Petly es una plataforma web y Android que facilita la adopción responsable de mascotas. Conecta adoptantes con albergues, orienta la búsqueda mediante compatibilidad con el estilo de vida y organiza publicaciones, solicitudes y seguimiento posterior.

La IA ayudará a preparar descripciones revisables de mascotas y, en una etapa posterior, a orientar al usuario mediante un asistente. El albergue conserva la decisión sobre las adopciones.

El recorrido principal del producto es:

1. El albergue registra y publica una mascota.
2. El adoptante completa su perfil de estilo de vida.
3. Consulta el catálogo y recibe recomendaciones explicadas.
4. Envía una solicitud.
5. El albergue evalúa la solicitud y registra la adopción.
6. Se registra un seguimiento posterior.

## 2. Contexto académico y restricciones

- Equipo de tres estudiantes de cuarto ciclo de Diseño y Desarrollo de Software.
- Duración total: dos meses, divididos en cuatro sprints.
- Sprint 1: entrega esta semana, según lo indicado por el usuario; no se ha fijado una fecha exacta en este documento.
- Metodología utilizada por el curso: Scrum.
- Cursos integrados: Construcción y Pruebas de Software, Desarrollo de Aplicaciones Web, Desarrollo de Aplicaciones Empresariales y Programación en Móviles.
- Priorizar una solución sencilla, integrada, comprobable y comprensible para el equipo.
- Las 75 historias representan la visión general. No se ha comprometido implementar las 75 en dos meses.
- Responsables acordados: Persona A lidera Django y panel del albergue; Persona B Spring Boot y web del adoptante; Persona C Kotlin e integración. La capacidad semanal real todavía debe acordarse. No inventar nombres ni nuevas estimaciones aprobadas.

## 3. Estado conocido

Información aportada por el usuario, pendiente de comprobar en el repositorio de desarrollo:

- Ya existe un proyecto Spring Boot con Java 21 y Maven.
- Ya existe un proyecto Android con Kotlin y Jetpack Compose.
- Se dispone de XAMPP y SQLyog.
- La base de datos prevista se llama `database_petly`.

Antes de modificar archivos, localizar e inspeccionar esos proyectos. Conservar sus paquetes, identificadores y configuración cuando sea posible. La estructura propuesta sirve como guía; no autoriza recrearlos ni sobrescribirlos.

## 4. Arquitectura acordada

| Componente | Tecnología | Responsabilidad |
| --- | --- | --- |
| Web | React, TypeScript y Vite | Una aplicación con áreas de adoptante, albergue y administrador. |
| Backend administrativo | Django y Django REST Framework | Gestión de albergues y mascotas; posteriormente evaluación de solicitudes, publicaciones con IA y seguimiento. |
| Backend de usuarios | Spring Boot, Java 21 y Maven | Cuentas, acceso, perfiles y catálogo; posteriormente compatibilidad y envío de solicitudes. |
| Móvil | Kotlin y Jetpack Compose | Experiencia Android del adoptante conectada a Spring Boot. |
| Datos | MariaDB, inicialmente desde XAMPP | Base compartida `database_petly`; SQLyog como herramienta de administración. |

XAMPP suele incluir MariaDB aunque su panel indique MySQL. Comprobar el motor y su versión antes de fijar las dependencias. No actualizar instalaciones existentes automáticamente ni asumir compatibilidad sin verificarla.

React y Kotlin acceden a APIs, nunca directamente a la base de datos. React utiliza Spring Boot para funciones del adoptante y Django para funciones administrativas.

## 5. Actores y permisos

- **Adoptante:** gestiona su propio perfil, consulta mascotas y, posteriormente, sus solicitudes y seguimientos.
- **Personal del albergue:** gestiona la información y mascotas de su organización. Consulta datos de postulantes únicamente cuando exista una relación autorizada.
- **Administrador de Petly:** gestiona accesos y albergues dentro del alcance implementado.

No confundir al administrador general con el personal del albergue. Ocultar una pantalla no sustituye la validación de permisos en el backend.

El rol veterinario aparece en US-59, pero su implementación está pendiente de definición y fuera de la estructura inicial.

## 6. Autenticación y propiedad de datos

Estrategia prevista:

- Spring Boot gestiona las cuentas y credenciales de negocio y emite los tokens.
- Django valida esos tokens y aplica permisos sobre sus recursos.
- Documentar el formato del token, roles, identificador de usuario, emisor, destinatarios y expiración antes de implementar la integración.
- No duplicar contraseñas ni crear cuentas de negocio independientes en ambos backends.
- No implementar accesos ficticios ni declarar completa una autenticación que todavía no existe.

Cada tabla debe tener un único responsable de su estructura:

| Información | Responsable de las migraciones |
| --- | --- |
| Cuentas y perfiles de adoptantes | Spring Boot |
| Albergues y mascotas | Django |
| Solicitudes, adopciones y seguimientos | Definir antes de sus respectivos sprints. |

- Django conserva sus migraciones en cada aplicación.
- Spring Boot utiliza Flyway con un historial propio. Hibernate no debe crear ni actualizar tablas automáticamente.
- Los modelos de consulta de tablas ajenas no deben generar migraciones ni alterar su estructura.
- Definir nombres y tipos de claves compartidas, relaciones y orden de ejecución de migraciones.
- Evitar borrados en cascada o modificaciones sobre tablas del otro backend sin una regla explícita.
- Si se usan componentes internos de autenticación de Django, documentar su propósito y separarlos de las cuentas de Petly.
- `database/` contiene documentación y datos de demostración; no duplica las migraciones.

## 7. Estructura del repositorio

Un solo repositorio, sin repositorios Git anidados:

```text
petly/
├── README.md
├── PLANNING.md
├── .gitignore
├── .editorconfig
├── docs/
│   ├── arquitectura.md
│   ├── instalacion.md
│   ├── acuerdos-equipo.md
│   ├── api.md
│   └── sprint-01.md
├── web/
│   ├── README.md
│   ├── package.json
│   ├── package-lock.json
│   ├── index.html
│   ├── vite.config.ts
│   ├── tsconfig.json
│   ├── .env.example
│   ├── public/
│   └── src/
│       ├── main.tsx
│       ├── App.tsx
│       ├── assets/
│       ├── components/
│       ├── layouts/
│       │   ├── AdoptanteLayout.tsx
│       │   ├── AlbergueLayout.tsx
│       │   └── AdminLayout.tsx
│       ├── routes/
│       │   ├── index.tsx
│       │   └── ProtectedRoute.tsx
│       ├── services/
│       │   ├── usuarioApi.ts
│       │   └── administracionApi.ts
│       ├── features/
│       │   ├── auth/
│       │   ├── adoptante/
│       │   │   ├── perfil/
│       │   │   └── catalogo/
│       │   ├── albergue/
│       │   │   ├── perfil/
│       │   │   ├── mascotas/
│       │   │   └── postulantes/
│       │   └── administracion/albergues/
│       ├── types/
│       └── styles/
├── backend-admin/
│   ├── README.md
│   ├── manage.py
│   ├── requirements.txt
│   ├── .env.example
│   ├── config/
│   │   ├── __init__.py
│   │   ├── settings.py
│   │   ├── urls.py
│   │   ├── asgi.py
│   │   └── wsgi.py
│   └── apps/
│       ├── __init__.py
│       ├── albergues/
│       ├── mascotas/
│       └── acceso/
├── backend-usuario/
│   ├── README.md
│   ├── pom.xml
│   ├── mvnw
│   ├── mvnw.cmd
│   ├── .mvn/
│   ├── .env.example
│   └── src/
│       ├── main/
│       │   ├── java/com/petly/
│       │   │   ├── PetlyApplication.java
│       │   │   ├── config/
│       │   │   ├── security/
│       │   │   ├── common/exception/
│       │   │   ├── auth/
│       │   │   ├── usuarios/
│       │   │   ├── perfiles/
│       │   │   └── catalogo/
│       │   └── resources/
│       │       ├── application.yml
│       │       └── db/migration/
│       └── test/java/com/petly/
├── mobile/
│   ├── README.md
│   ├── settings.gradle.kts
│   ├── build.gradle.kts
│   ├── gradle.properties
│   ├── gradlew
│   ├── gradlew.bat
│   ├── gradle/
│   └── app/
│       ├── build.gradle.kts
│       └── src/
│           ├── main/
│           │   ├── AndroidManifest.xml
│           │   ├── java/com/petly/mobile/
│           │   │   ├── MainActivity.kt
│           │   │   ├── core/
│           │   │   │   ├── network/
│           │   │   │   ├── session/
│           │   │   │   └── navigation/
│           │   │   ├── data/
│           │   │   │   ├── remote/
│           │   │   │   ├── model/
│           │   │   │   └── repository/
│           │   │   └── ui/
│           │   │       ├── auth/
│           │   │       ├── perfil/
│           │   │       ├── catalogo/
│           │   │       └── theme/
│           │   └── res/
│           ├── test/
│           └── androidTest/
└── database/
    ├── README.md
    ├── modelo-datos.md
    └── seeds/
```

Los paquetes `com.petly` y `com.petly.mobile` son propuestos. Si los proyectos existentes usan otros, conservarlos y documentar el ajuste; no romper el escaneo de Spring ni el namespace o applicationId de Android.

Organización interna:

- Django: `__init__.py`, `apps.py`, `models.py`, `serializers.py`, `views.py`, `urls.py`, `migrations/__init__.py` y `tests/__init__.py`; agregar servicios y permisos cuando sean necesarios. Registrar las aplicaciones en settings.
- Spring Boot: módulos con `controller`, `service`, `repository`, `entity` y `dto`, según necesidad.
- React: funcionalidades con `pages`, `components`, `services` y `types`, según necesidad.
- Usar `.gitkeep` para carpetas vacías necesarias. No crear clases artificiales para rellenarlas.
- Los nombres Python son literalmente `__init__.py`, nunca `init.py` ni `**init**.py`.
- Conservar o agregar los archivos técnicos reales requeridos por cada generador y los wrappers oficiales completos.

## 8. Épicas y prioridades registradas por el docente

| Código | Épica | Prioridad |
| --- | --- | --- |
| E06 | Motor de Matchmaking Inteligente | Alta |
| E08 | Gestión de Solicitudes y Proceso de Adopción | Alta |
| E05 | Gestión del Catálogo y Perfiles de Mascotas | Alta |
| E02 | Gestión de Perfiles de Adoptantes y Estilo de Vida | Alta |
| E01 | Gestión de Autenticación y Verificación de Identidad (KYC) | Alta |
| E04 | Publicación Asistida y Generación de Contenido por IA | Alta |
| E07 | Asistente Virtual y Chatbot Guiado | Media |
| E09 | Módulo de Seguimiento Continuo Post-Adopción | Media |
| E10 | Sistema de Notificaciones y Alertas en Tiempo Real | Media |
| E03 | Gestión y Registro de Albergues | Media |
| E11 | Comunicación y Mensajería Directa | Media |
| E12 | Gestión de Historial Médico y Ficha Veterinaria | Baja |
| E14 | Gestión de Feedback, Reseñas y Casos de Éxito | Baja |
| E13 | Reportes, Métricas y Analítica de Adopción | Baja |
| E15 | Administración, Roles y Seguridad del Sistema | Baja |

Las prioridades son las registradas, no una secuencia técnica de implementación. Los permisos básicos se implementan desde el inicio aunque E15 tenga prioridad baja.

US-03 tiene esta redacción actualizada por el usuario, que reemplaza la del PDF:

> Como usuario quiero visualizar el puntaje recalculado de coincidencia cuando actualice mis preferencias para mantener el catálogo personalizado.

## 9. Sprint 1: alcance de desarrollo

Objetivo: disponer de una base integrada donde el albergue registre mascotas y actualice su disponibilidad, y el adoptante acceda, complete su perfil y consulte el catálogo.

| Historia | Alcance registrado | Suma de tareas del PDF |
| --- | --- | --- |
| US-21 | Acceso, tokens, bloqueo por intentos fallidos y pruebas. | 3 h |
| US-16 | Cuestionario de vivienda y estilo de vida, persistencia y recuperación de respuestas. | 5 h |
| US-11 | Registro de mascotas, CRUD, estructura de datos y validaciones. | 3,5 h |
| US-46 | Registro de información e infraestructura del albergue. | 3 h |
| US-12 | Búsqueda, filtros, paginación y pruebas. | 2,5 h |
| US-17 | Datos de convivencia con niños y otras mascotas. | 2 h |
| US-24 | Recuperación de contraseña por correo/SMS y tokens temporales. | 2 h |
| US-18 | Consulta de datos de postulantes por el albergue. El backlog añade antecedentes e historial. | 3 h |
| US-13 | Estados Disponible, En Proceso y Adoptado; actualización del catálogo. | 2 h |

El PDF contiene 33 tareas que suman **26 horas**, aunque declara **30 horas**. No corregir silenciosamente: aclarar si las cuatro horas restantes son una reserva. Las estimaciones pueden ser insuficientes si se parte desde cero y no incluyen explícitamente toda la preparación e integración.

### 9.1. Decisiones aprobadas para el Sprint 1

El usuario eligió la opción A de las alternativas de aclaración, con la excepción de pantallas, donde combinó A y B. Estas decisiones sustituyen los pendientes anteriores. La tabla de horas conserva el alcance y estimaciones del PDF como referencia histórica; no constituye una nueva estimación del alcance actualizado.

- **US-21:** registro del adoptante con correo y contraseña. Las cuentas de albergue y administrador son creadas por el equipo mediante un procedimiento documentado, sin contraseñas en Git. Tras dos intentos consecutivos fallidos, bloqueo temporal de la cuenta durante **5 minutos**, con desbloqueo automático. Aplicar también limitación de peticiones. No incluir registro mediante redes sociales en esta entrega.
- **US-11:** **una fotografía por mascota**, en JPEG o PNG, de máximo **5 MB**. El archivo se guarda en almacenamiento local de Django y su referencia en la DB. Validar el archivo en el backend; no guardar las fotografías privadas en Git.
- **US-12:** filtros por especie, tamaño, edad y temperamento. El temperamento utiliza categorías predefinidas registradas por el albergue, incluyendo **“Por evaluar”** cuando se desconozca. No inferirlo mediante IA. Documentar las categorías y su correspondencia entre registro y filtros.
- **US-24:** recuperación **solo por correo**, mediante enlace con token de un solo uso que vence en **15 minutos**. SMS queda pendiente para una versión posterior. No declarar entregada la alternativa SMS.
- **US-18:** consulta del perfil únicamente cuando el adoptante **autorice expresamente compartirlo con un albergue específico**. El adoptante puede revocar la autorización. No incluir antecedentes externos ni historial de adopciones en el Sprint 1. Spring Boot conserva la autorización vinculada al perfil; Django comprueba su vigencia antes de permitir la consulta. La consulta no equivale a una solicitud de adopción.

### 9.2. Pantallas: indicación exacta del usuario

> React: acceso, recuperación, catálogo, cuestionario, panel del albergue. Kotlin: experiencia completa del adoptante y el portal React del adoptante pasa al siguiente sprint

Esta indicación combina el inventario de pantallas React con el aplazamiento del portal del adoptante. Se conserva literalmente para no perder ninguna de las dos decisiones.

Interpretación de planificación utilizada en este documento:

- **Sprint 1, React:** acceso y recuperación para el personal del albergue y administrador; panel del albergue con perfil institucional, registro de mascotas, estados y consulta autorizada de perfiles. Administración general mínima.
- **Sprint 1, Kotlin:** experiencia completa del adoptante **dentro de las historias de este sprint**: registro, acceso, recuperación por correo, catálogo con filtros, detalle de mascota, cuestionario y convivencia, y autorización/revocación para compartir el perfil con un albergue.
- **Sprint 2, React:** portal funcional del adoptante, con acceso, recuperación, catálogo y cuestionario. Sus carpetas y rutas previstas se conservan desde la preparación inicial, sin presentar pantallas pendientes como terminadas.
- “Experiencia completa” no incorpora matchmaking, solicitudes, chatbot ni otras historias fuera del Sprint 1.
- Si el usuario esperaba entregar también catálogo y cuestionario funcionales en React durante el Sprint 1, confirmar esa diferencia antes de ampliar la programación. No borrar ni descartar esas pantallas del producto.

### 9.3. Responsables y revisión cruzada

Indicaciones de liderazgo acordadas:

- **Persona A:** lidera Django y panel del albergue.
- **Persona B:** lidera Spring Boot y web del adoptante.
- **Persona C:** lidera Kotlin e integración.

| Historia | Responsable principal | Revisor | Colaboración necesaria |
| --- | --- | --- | --- |
| US-21 — Registro y acceso | Persona B | Persona C | A integra el acceso administrativo y la validación de tokens en Django; C integra Android. |
| US-16 — Cuestionario | Persona C | Persona A | B implementa persistencia y API en Spring Boot; C implementa el formulario Android. |
| US-11 — Registro de mascotas | Persona A | Persona B | A implementa Django y panel React; B revisa el contrato de datos para el catálogo. |
| US-46 — Registro de albergues | Persona A | Persona C | B apoya el vínculo con cuentas; C verifica integración y datos de demostración. |
| US-12 — Búsqueda y filtros | Persona C | Persona B | B implementa consultas y API; A proporciona los datos de mascotas; C integra el catálogo Android. |
| US-17 — Convivencia | Persona C | Persona B | B implementa modelo y API de perfiles; C integra los campos en el cuestionario Android. |
| US-24 — Recuperación por correo | Persona B | Persona A | C integra el flujo Android; A integra las pantallas React administrativas. |
| US-18 — Consulta autorizada | Persona B | Persona A | B implementa autorización/revocación y consulta autorizada; A implementa panel y permisos Django; C integra el consentimiento Android. |
| US-13 — Estado de la mascota | Persona A | Persona C | B expone el estado en el catálogo; C verifica el cambio al consultar desde Android. |

Cada persona tiene tres historias como responsable, pero esto no implica igualdad de esfuerzo. El responsable coordina la historia completa, sus dependencias y su evidencia; no desarrolla necesariamente todos sus componentes. El revisor comprueba criterios y permisos y revisa los cambios antes de integrarlos. Ninguna historia se aprueba únicamente por su propio responsable.

### 9.4. Criterios mínimos de aceptación

**US-21 — Registro y acceso**

- El adoptante puede crear una cuenta con correo válido y contraseña; se rechazan registros con correo duplicado y entradas inválidas.
- Con credenciales válidas obtiene acceso según su rol; con credenciales incorrectas se rechaza el acceso.
- El segundo intento consecutivo fallido activa un bloqueo de 5 minutos; las credenciales correctas tampoco permiten entrar durante el bloqueo. Tras el plazo puede volver a intentarlo.
- Se restablece el contador tras un acceso válido y se comprueba la limitación de peticiones.
- Las credenciales se almacenan mediante hash de contraseña, nunca como texto plano. Ambos backends aplican permisos sin duplicar cuentas de negocio.

**US-16 — Cuestionario**

- El adoptante registra vivienda, horas fuera de casa y presupuesto, con validación de los campos acordados.
- Puede guardar y recuperar respuestas parciales o finalizadas y modificarlas desde su cuenta.
- Otro adoptante no puede consultar ni modificar sus respuestas privadas.

**US-11 — Registro de mascotas**

- El albergue registra los datos requeridos de la mascota, incluyendo nombre, edad, energía y comportamiento, y una fotografía válida.
- Se rechazan archivos que no sean JPEG/PNG o superen 5 MB. La mascota queda vinculada al albergue que la registró.
- Los datos y la referencia de la imagen persisten y la mascota puede consultarse en el catálogo según su estado.
- Otro albergue no puede modificarla ni eliminarla. Las operaciones CRUD incluidas en el backlog respetan esos permisos.

**US-46 — Registro de albergues**

- Una cuenta de albergue creada por el equipo registra y recupera su información institucional, ubicación y capacidad.
- Se validan los campos requeridos y se conserva la relación con su cuenta.
- Otro albergue no puede modificar esa información.

**US-12 — Búsqueda y filtros**

- La búsqueda y los filtros por especie, tamaño, edad y temperamento devuelven resultados coherentes, individualmente y combinados.
- Se contempla “Por evaluar” sin atribuir un comportamiento conocido al animal.
- La paginación conserva los filtros; cuando no hay coincidencias se muestra un estado claro.
- Android consulta los resultados desde Spring Boot, sin acceso directo a la DB.

**US-17 — Convivencia**

- El adoptante registra si tiene niños u otras mascotas; los valores persisten y se recuperan en su cuestionario.
- Puede actualizarlos y otro adoptante no puede modificarlos.
- Los campos utilizan un contrato consistente entre Android y Spring Boot. No se exige todavía calcular compatibilidad.

**US-24 — Recuperación por correo**

- El usuario solicita recuperación y recibe un enlace por correo cuando la cuenta existe; la respuesta pública no revela si el correo está registrado.
- El enlace válido permite cambiar la contraseña. El token vence a los 15 minutos y no puede utilizarse dos veces.
- Se rechazan tokens inválidos, vencidos o ya utilizados. Tras el cambio, la contraseña anterior no permite acceder.
- El envío se verifica con un entorno de correo de pruebas documentado; una respuesta simulada no demuestra entrega de correo.

**US-18 — Consulta autorizada**

- El adoptante elige un albergue específico y autoriza compartir su perfil; puede consultar y revocar esa autorización.
- El albergue autorizado accede únicamente a los campos de perfil necesarios para evaluar vivienda, rutina y convivencia; nunca a contraseñas ni tokens.
- Sin autorización, desde otro albergue o tras revocarla, se rechaza la consulta, incluso si se accede directamente al endpoint.
- La vista no incorpora antecedentes externos ni historial de adopciones inexistente.

**US-13 — Estado de la mascota**

- El albergue propietario puede cambiar el estado entre Disponible, En Proceso y Adoptado, rechazando valores no permitidos.
- El estado persiste y se refleja al volver a consultar el catálogo Android. El reflejo no exige una actualización push.
- Otro albergue no puede cambiarlo. El cambio manual de este sprint no crea una solicitud ni un expediente de adopción.

### 9.5. Condiciones comunes para cerrar una historia

- Cumple sus criterios en las interfaces comprometidas para este sprint y cuenta con revisión de otro integrante.
- Backend e interfaz están integrados con datos persistidos; no son solamente pantallas o respuestas simuladas.
- Se verifican errores relevantes, acceso autorizado y acceso denegado según corresponda.
- Los cambios de API, configuración y migraciones se documentan sin secretos.
- Se conserva evidencia de las comprobaciones; si faltan componentes o verificación, se registra como pendiente y no se declara terminada.

Pendientes que permanecen: confirmar capacidad y reestimar el alcance actualizado; aclarar la diferencia entre 26 y 30 horas del PDF; fijar fechas exactas y categorías de temperamento. La distribución por persona no modifica las estimaciones originales automáticamente.

El Sprint 1 no incluye el motor de compatibilidad, IA, solicitudes, chatbot, mensajería ni seguimiento.

## 10. Objetivo inmediato de OpenCode: preparar la estructura

Esta etapa prepara el proyecto para que el equipo programe. No equivale a completar el Sprint 1.

1. Inspeccionar los archivos existentes y organizar los proyectos sin sobrescribir trabajo.
2. Crear React y Django si no existen; extender los proyectos Spring Boot y Android existentes.
3. Preparar configuraciones, documentación y carpetas de los módulos.
4. Mostrar una pantalla inicial sencilla de Petly en web y Android.
5. Añadir `GET /api/health/` en ambos backends, con respuesta básica sin información sensible.
6. Documentar el modelo preliminar de datos, sin generar todavía entidades ni migraciones de negocio.
7. Mantener las funcionalidades del Sprint 1 como pendientes durante esta preparación. Las decisiones de v2 definen cómo programarlas cuando se solicite esa etapa; no autorizan implementarlas todas al leer este archivo.
8. Preparar únicamente el proyecto local de acuerdo a la estructura del repositorio. No publicar en GitHub, hacer push ni crear repositorios remotos en esta etapa.

## 11. Entorno y configuración

- React: puerto 5173.
- Django: puerto 8000.
- Spring Boot: puerto 8080.
- Base de datos: `database_petly`; verificar host y puerto reales, sin asumir credenciales.
- `.env.example`: valores de ejemplo sin secretos reales y explicación de las variables requeridas.
- Spring Boot no carga `.env` automáticamente: documentar un procedimiento real para variables de entorno en Windows.
- Android Emulator: Spring Boot se alcanza mediante `http://10.0.2.2:8080`.
- Teléfono físico: usar la IP local del equipo con conectividad adecuada.
- Permitir HTTP en Android solamente en configuración de desarrollo.
- CORS: orígenes locales explícitos.
- Fijar dependencias compatibles y conservar los archivos de bloqueo aplicables.
- Excluir `.env`, secretos, entornos virtuales, dependencias, compilaciones, `local.properties`, medios privados y volcados de datos personales.
- No agregar Docker, microservicios adicionales ni infraestructura que el equipo no haya solicitado.

## 12. Plan orientativo de los sprints restantes

Propuesta pendiente de estimación y aprobación, no compromiso cerrado:

- **Sprint 2:** incorporar el portal React del adoptante según la decisión de v2. Como propuesta adicional, compatibilidad explicada y flujo inicial de solicitudes, sujetos a capacidad y reestimación una vez estable la base del Sprint 1.
- **Sprint 3:** completar la evaluación y registro de adopciones e incorporar descripciones asistidas por IA.
- **Sprint 4:** seguimiento básico, integración final, correcciones, pruebas y preparación de la demostración.

Reevaluar después de cada sprint según capacidad real. No llenar el último sprint con todas las historias pendientes.

## 13. Límites funcionales y reglas del producto

- El puntaje basado en reglas se presenta como orientación, no como modelo entrenado ni probabilidad validada de una adopción exitosa.
- La IA redacta desde datos suministrados; el albergue revisa antes de publicar.
- No inferir como hechos salud, temperamento, raza o edad exacta desde una fotografía.
- No prometer asesoría veterinaria gratuita sin un servicio real que la preste.
- La autenticación no equivale a KYC; definir su alcance antes de desarrollar verificación de identidad.
- Contratos con firma electrónica, KYC avanzado, 2FA, análisis de imágenes, notificaciones multicanal, chat avanzado y funciones sociales no forman parte de la preparación inicial.
- Cada albergue modifica solamente sus recursos; cada adoptante modifica solamente su perfil.
- Definir las transiciones de solicitudes y mascotas antes del módulo de adopciones para evitar resultados incompatibles.
- Si se reduce una historia, registrar el alcance entregado y pendiente. No declararla completa si no cumple lo acordado.

## 14. Verificación y entrega de la estructura

- React: verificar compilación.
- Django: ejecutar chequeos del proyecto y verificar el endpoint de salud cuando pueda arrancar.
- Spring Boot: verificar compilación y arranque con configuración válida.
- Android: verificar compilación si existe SDK y herramientas compatibles.
- Documentar requisitos, comandos para Windows, configuración y orden de inicio.
- Separar resultados comprobados de comprobaciones pendientes por falta de herramientas, conectividad o configuración.
- Entregar un resumen de archivos creados o modificados, ajustes a la estructura y pasos pendientes.
- No afirmar que hubo integración con MariaDB si no se probó realmente.

## 15. Referencias y uso de este documento

Cambios de v2: opciones A aprobadas para registro/acceso, fotografías, temperamento, recuperación, consulta autorizada y organización del equipo; indicación combinada A+B de pantallas conservada literalmente; nueve historias con responsable y revisor; criterios mínimos de aceptación. Se mantienen la arquitectura, estructura del repositorio y límites de la preparación inicial.

Este documento resume la conversación de planificación, la arquitectura proporcionada.
Las restricciones explícitas y actualizaciones del usuario prevalecen sobre las propuestas pendientes de este documento. Leer este archivo como contexto para la tarea solicitada; IMPORTANTE: no ejecutar automáticamente todas las funcionalidades aquí descritas.
