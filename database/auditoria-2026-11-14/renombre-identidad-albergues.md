# Renombrado de identidad y albergues

Ensayo del 14 de noviembre de 2026 sobre una restauración aislada de MariaDB. Las migraciones están preparadas para un despliegue coordinado de Django y Spring.

| Nombre anterior | Nombre propuesto | Relación |
|---|---|---|
| `acceso_user.spring_id` | `acceso_user.usuario_spring_id` | Vínculo lógico con `usuarios.id` de Spring. Sigue siendo único y admite NULL. |
| `albergues_albergue` | `albergues` | Tabla de Django. |
| `albergues_albergue.usuario_id` | `albergues.propietario_django_id` | FK única a `acceso_user.id`, la PK local de Django. |

Django conserva el nombre técnico `acceso_user` y las PK de las dos cuentas. Su relación ORM `Albergue.usuario` sigue disponible. Los nombres JSON de las API y el argumento posicional del comando de vinculación se conservan. Se actualizaron los consumidores del ORM y las consultas JDBC de catálogo, KYC, alertas, autorizaciones y perfiles compartidos, además de sus esquemas de prueba. El diccionario vigente describe la base local antes de este despliegue.

La copia se actualizó hasta el retiro aprobado del chat. Después se aplicaron, revirtieron y reaplicaron `acceso.0022` y `albergues.0009`. En las tres comparaciones coincidieron las 63 tablas tras normalizar los nombres, las parejas de IDs de 25 cuentas Django, los propietarios de 8 albergues, las 73 FK y los conteos y checksums de todas las tablas de datos. Se verificó la nulabilidad y unicidad de las dos columnas. Pasaron 82 pruebas Django sobre MariaDB y las pruebas Spring ejecutadas con su esquema de prueba.

Para desplegar, coordinar el cambio de esquema con la parada de ambos backends y los workers y verificar un respaldo reciente en una base aislada. Revisar que el plan incluya `acceso.0022` y `albergues.0009`. La primera migración exige `PETLY_AUTORIZAR_RENOMBRE_IDENTIDAD=1` para `database_petly`. Iniciar ambos backends con los consumidores actualizados después de migrar y verificar sus rutas HTTP. Para revertir, detener los escritores y llevar primero `albergues` a `0008` y luego `acceso` a `0021`, antes de arrancar el código anterior.
