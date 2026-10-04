# Petly web (React)

Aplicación web de Petly con áreas de adoptante, albergue y administrador.
Stack: React 19 + TypeScript + Vite. Puerto **5173**.

## Comandos (Windows / PowerShell)

```powershell
npm install        # instala dependencias (genera package-lock.json)
npm run typecheck  # comprobación de tipos
npm run build      # compilación de verificación
npm run dev        # desarrollo en http://localhost:5173
```

## Configuración

```powershell
Copy-Item .env.example .env   # .env está excluido de Git
```

| Variable | Defecto | Descripción |
| --- | --- | --- |
| `VITE_API_USUARIO_URL` | `http://localhost:8080` | Spring Boot (cuentas, perfiles, catálogo) |
| `VITE_API_ADMINISTRACION_URL` | `http://localhost:8000` | Django (albergues, mascotas, administración) |

## Estructura

- `src/layouts/` — áreas `AdoptanteLayout`, `AlbergueLayout` y `AdminLayout`.
- `src/routes/` — `index.tsx` (rutas) y `ProtectedRoute.tsx` (guard; la autenticación real llega con US-21).
- `src/services/` — `usuarioApi.ts` (Spring Boot) y `administracionApi.ts` (Django). La web **nunca** accede directamente a la base de datos.
- `src/features/` — `auth`, `adoptante` (perfil, catálogo), `albergue` (perfil, mascotas, postulantes) y `administracion/albergues`.
- `src/components/`, `src/types/`, `src/styles/`, `src/assets/`.

## Estado

Solo la pantalla inicial (`HomePage`) está funcional y comprueba los endpoints de salud de
ambos backends. Las demás pantallas están preparadas y marcadas como pendientes:
**no** representan funcionalidad entregada. Ver `../PLANNING.md` v2 §9.2 y `../docs/sprint-01.md`.
