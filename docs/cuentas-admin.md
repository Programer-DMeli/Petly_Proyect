# Creación de cuentas de albergue y administración

Procedimiento local para el equipo; no existe un endpoint interno de creación ni variables INITIAL_ADMIN implementadas.

1. Registrar la cuenta mediante POST Spring `/api/auth/registro` con correo y contraseña privados elegidos por el responsable. La API siempre crea ADOPTANTE y un username interno.
2. El equipo verifica identidad y finalidad de esa cuenta; anota su ID Spring.
3. Desde SQLyog/MariaDB, verificar primero `SELECT id,correo,rol FROM usuarios WHERE id=<ID_VERIFICADO>;`. Cambiar **esa cuenta** con `UPDATE usuarios SET rol='ALBERGUE',token_version=token_version+1 WHERE id=<ID_VERIFICADO>;` (o ADMIN). No convertir cuentas sin verificar propietario y no insertar hashes de ejemplo.
4. Iniciar sesión otra vez en Spring. El token anterior deja de ser válido. Con rol ALBERGUE crear el perfil en `/api/albergues/` de Django; el espejo se crea sin copiar la contraseña.
5. Si ya existe un albergue con usuario Django, revisar correos y ejecutar `python manage.py vincular_cuenta_spring <ID_DJANGO> <ID_SPRING>`. No usar la cuenta técnica superuser. El comando conserva relaciones del usuario anterior y deshabilita su contraseña local.
6. Verificar que otro albergue no puede editar su información ni mascotas. Registrar evidencia sin tokens ni contraseñas.

Django admin utiliza una cuenta técnica superuser interna distinta, sin spring_id. Las cuentas staff de negocio tampoco pueden usar ese acceso. No se concede acceso al admin técnico por convertir una cuenta Petly a ADMIN. El rol de administración general y panel React requieren revisión de Mayra; no son sustituidos por este procedimiento.
