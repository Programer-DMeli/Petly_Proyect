# Petly web — Sprint 1

React, TypeScript y Vite. Una aplicación con áreas ADMIN, ALBERGUE y ADOPTANTE. Spring autentica a los tres roles y gestiona perfil, catálogo y consentimiento; Django gestiona instituciones y mascotas y comprueba autorización de perfiles. Nunca accede directamente a DB.

## Arranque

`npm ci` y `npm run dev -- --strictPort`. URL http://localhost:5173. Revisar .env.example y docs/instalacion.md. Recargar cierra la sesión porque se conserva en memoria.

## Verificación

`npm run lint`, `npm run build`, `npm run format:check`. Prueba integrada: `npx playwright install chromium` y `npm run test:e2e`, con servicios de pruebas y cuentas privadas preparadas según docs/instalacion.md. No utiliza mocks de APIs ni guarda trace con credenciales.
