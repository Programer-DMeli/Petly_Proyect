# Cuentas ALBERGUE y ADMIN - Procedimiento (US-21)

## Principio
Nunca guardar contrasenas en Git. Solo hashes BCrypt en base de datos.

## Opcion A: Script SQL manual (recomendado para staging/prod)

Ejecutar directamente en MariaDB (XAMPP puerto 3306, base database_petly):

```sql
-- ALBERGUE
INSERT INTO usuarios (correo, password_hash, rol, intentos_fallidos, bloqueado_hasta, created_at, updated_at)
VALUES (
  'albergue@petly.test',
  '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
  'ALBERGUE',
  0, NULL, NOW(), NOW()
);

-- ADMIN
INSERT INTO usuarios (correo, password_hash, rol, intentos_fallidos, bloqueado_hasta, created_at, updated_at)
VALUES (
  'admin@petly.test',
  '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
  'ADMIN',
  0, NULL, NOW(), NOW()
);
```

Generar nuevo hash BCrypt (Java):
```java
BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
String hash = encoder.encode("tuPasswordSeguro");
```

## Opcion B: Endpoint interno protegido (solo desarrollo)

POST /api/interno/usuarios (requiere rol ADMIN + header X-Internal-Key)

```json
{ "correo": "nuevo@petly.test", "password": "claveSegura123", "rol": "ALBERGUE" }
```

Respuestas: 201, 400 validacion, 401 no autorizado, 403 sin rol ADMIN, 409 duplicado.

## Variables de entorno para arranque inicial

En .env (no en Git):
```
INITIAL_ADMIN_EMAIL=admin@petly.local
INITIAL_ADMIN_PASSWORD=AdminSeguro2024
INITIAL_ALBERGUE_EMAIL=albergue@petly.local
INITIAL_ALBERGUE_PASSWORD=AlbergueSeguro2024
```

Script de arranque (init-admin.sh o CommandLineRunner condicional a perfil init-admin) lee esas vars, hashea y persiste si no existen.

## Verificacion rapida

```bash
# Login ALBERGUE
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"correo":"albergue@petly.test","password":"Albergue2024!"}'
# -> token con rol ALBERGUE

# Login ADMIN
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"correo":"admin@petly.test","password":"Admin2024!"}'
# -> token con rol ADMIN
```