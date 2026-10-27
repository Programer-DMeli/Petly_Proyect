# Sprint 2: US-01, US-02, US-03, US-04, US-06, US-07, US-08, US-09, US-10, US-28 y US-29

## Compatibilidad (US-01, US-02 y US-03)

Spring calcula en cada consulta, sin cachear puntajes. El perfil finalizado y los datos actuales de mascotas son la entrada; cambiar y guardar el perfil hace que la siguiente consulta recalcule. La lista completa se puntúa y ordena antes de aplicar paginación: porcentaje descendente, identificador ascendente; resultados sin información suficiente van al final. Django expone los atributos de mascotas en `/api/mascotas/compatibilidad/` y filtra mascotas disponibles de albergues activos.

La configuración inicial `petly-compat-v1` usa los pesos 30/35/35. ADMIN puede cambiarlos en Spring mediante `GET/PUT /api/admin/compatibilidad/reglas`; los valores deben ser enteros entre 0 y 100 y sumar exactamente 100. Cada actualización persiste los pesos, registra el ID Spring del administrador y aumenta la versión (`petly-compat-v2`, etc.). El catálogo devuelve la versión usada en cada cálculo. Django y Spring leen la misma configuración.

| Criterio | Peso | Regla |
| --- | ---: | --- |
| Vivienda | 30% inicial | Vivienda conocida y tamaño evaluado: 60/100 para perro grande en departamento; 100/100 para los demás pares conocidos. El patio no altera esta regla para evitar diferencias entre contratos de perfil. |
| Rutina | 35% inicial | Categorías de horas fuera: hasta 4, hasta 8, más de 8. Energía alta/media/baja requiere respectivamente la primera/segunda/tercera categoría. Misma categoría: 100; adyacente: 70; extremos: 35. |
| Convivencia | 35% inicial | Temperamento registrado: sociable 100, tranquilo 90, juguetón 80, activo 75, independiente 70, tímido 60. Niños con mascota tímida: 45; otras mascotas con temperamento activo: 65. |

Los tres criterios se calculan solo con perfil finalizado y datos requeridos conocidos. Si falta alguno, porcentaje `null`, cobertura en porcentaje del peso calculable e indicación “Información insuficiente”; no se asigna una coincidencia perfecta por defecto. Se calcula con decimales y se redondea al entero más cercano. Es orientación y no decide una adopción. Revisar estos pesos con el equipo antes de presentarlos como recomendación validada.

React y Android usan el orden y puntaje de Spring. No se almacena resultado previo, así que una consulta posterior a guardar el perfil usa los valores vigentes. Django conserva el endpoint de compatibilidad para la API administrativa y utiliza la misma fila de pesos y las mismas reglas de vivienda.

## Desglose explicativo (US-04)

El detalle `GET /api/catalogo/{id}` aplica las mismas reglas y versión que la lista cuando el titular autenticado es ADOPTANTE. React y Android muestran vivienda, rutina y convivencia con peso, aporte ponderado y explicación. El detalle marca los aportes no calculables, presenta la cobertura y explica que el total se redondea al entero más cercano; no decide adopciones.

## Solicitudes y decisiones (US-06, US-07 y US-08)

- `POST /api/solicitudes` en Spring comprueba rol, perfil finalizado, disponibilidad, albergue activo y consentimiento; remite el JWT original a Django para validar nuevamente y persistir.
- Django asigna el adoptante desde la identidad autenticada, registra titular, mascota, albergue, fecha y estado `PENDIENTE`. No cambia la disponibilidad al enviar. La restricción de base de datos y el bloqueo de mascota rechazan solicitudes activas duplicadas.
- `GET /api/solicitudes` y `GET /api/solicitudes/{id}/seguimiento` son del titular. La API Django limita las decisiones al albergue propietario. Observar/rechazar exigen motivo; aprobar comprueba de nuevo el consentimiento con Spring, bloquea la solicitud y mascota en transacción y cambia la mascota a `EN_PROCESO`.
- Cada envío y transición agrega un `SolicitudEvento`; la consulta del titular devuelve únicamente su historial persistido, en orden cronológico. La mascota no se puede liberar manualmente mientras tenga una solicitud aprobada o entregada.

## Cancelación por el adoptante (US-10)

`POST /api/solicitudes/{id}/cancelar` confirma la operación en React y Spring la delega a Django. Solo el titular puede cancelar solicitudes `PENDIENTE` u `OBSERVADA`; Django bloquea la fila y aplica la transición en la misma transacción que registra actor y fecha en `SolicitudEvento`. Una solicitud ajena devuelve no encontrada y los estados terminales no admiten una segunda cancelación. La UI oculta la acción después del cambio. La cancelación compite con aprobar sobre la misma fila bloqueada; solo una transición puede completarse.

## Afiche social (US-28)

Desde el panel del albergue propietario, React prepara una vista previa descargable como PNG de 1080 × 1350 px con foto, nombre, datos básicos y contacto público. La foto se encaja sin deformarse y el texto se ajusta a la plantilla; hace falta una foto y un teléfono o correo público. Si faltan, la descarga queda desactivada y se indica qué completar. El PNG es un borrador local: Petly no lo publica en redes ni genera una nueva imagen.

## Contrato firmado (US-09)

La aplicación genera un borrador inmutable con adoptante, mascota, albergue y términos aprobados. El flujo local predeterminado envía al correo del adoptante un código aleatorio de un uso, válido por 15 minutos. Después de revisar el contrato, el adoptante comparte el código con el albergue y el albergue propietario lo registra desde su sesión autenticada. Se limita a cinco intentos, se persiste solo el HMAC del código y se audita quién lo registró. La constancia de aprobación queda descargable por las partes autorizadas. El correo y la constancia explican que el código registra consentimiento por correo en Petly y no es una firma digital certificada ni verifica identidad.

La entrega física es una acción explícita del albergue en `/api/contratos/solicitudes/{id}/entrega/`. Solo después de registrar la aprobación del contrato cambia la solicitud a `ENTREGADA` y la mascota a `ADOPTADO`. El proveedor REST alternativo continúa disponible con `CONTRACT_SIGNATURE_PROVIDER=generic-rest`; requiere URL, credenciales, webhook público y acceso público a la API.

En ambos modos es obligatorio configurar en el entorno los términos, versión y responsable aprobados por el equipo; la aplicación rechaza el envío si faltan esos datos. En desarrollo los correos se guardan en `backend-admin/emails`; para entregar códigos a bandejas reales hay que configurar SMTP.

## Borrador desde foto (US-29)

`POST /api/mascotas/{id}/generar-descripcion/` permite al albergue propietario enviar la foto almacenada (JPEG/PNG, máximo 5 MiB) a un endpoint de visión configurado. El servicio solicita español, rasgos visibles y prohíbe inferir nombre, salud, edad exacta, raza confirmada, personalidad o conducta. Valida que la respuesta sea texto de hasta 500 caracteres y rechaza expresiones de inferencia habituales.

La API responde `{ "borrador": "...", "guardado": false }`. React lo pone en el editor; el albergue puede editarlo, descartarlo o guardarlo explícitamente. Una llamada fallida no modifica la descripción existente ni publica la foto. Configurar `PET_DESCRIPTION_API_URL` y `PET_DESCRIPTION_API_TOKEN` para activar el proveedor. La respuesta sigue requiriendo revisión humana.
