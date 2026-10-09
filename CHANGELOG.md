# Changelog

Todos los cambios notables en este proyecto se documentarán en este archivo.

El formato está basado en [Keep a Changelog](https://keepachangelog.com/es-ES/1.0.0/),
y este proyecto se adhiere a [Semantic Versioning](https://semver.org/lang/es/).

## [Unreleased]

### Added
- **Web**: ESLint, Prettier, TypeScript strict config, ErrorBoundary component
- **Web**: Vite config improvements (path aliases, build optimization, host config)
- **Web**: lint, format, typecheck npm scripts
- **Django**: Logging configuration with verbose formatter
- **Django**: drf-spectacular for OpenAPI 3 / Swagger UI documentation
- **Django**: API schema endpoint (/api/schema/) and Swagger UI (/api/docs/)
- **Spring Boot**: springdoc-openapi for OpenAPI 3 / Swagger UI
- **Spring Boot**: OpenApiConfig with JWT bearer auth scheme
- **Spring Boot**: GlobalExceptionHandler for consistent error responses
- **Spring Boot**: SecurityConfig updated to allow OpenAPI/Swagger endpoints
- **Mobile**: Network security configuration (debug: cleartext for localhost, release: HTTPS only)
- **Mobile**: New dependencies (OkHttp, Moshi, Retrofit, Navigation Compose, Lifecycle ViewModel Compose)
- **Mobile**: ProGuard rules for release builds
- **Mobile**: Updated AndroidManifest with networkSecurityConfig and INTERNET permission
- **Docs**: OpenAPI/Swagger UI endpoints table in README.md
- **Docs**: OpenAPI documentation section in docs/api.md
- **Config**: Shared JWT secret variables in .env.example files

### Changed
- **Web**: Improved tsconfig.json with path aliases and stricter checks
- **Web**: Formatted all source files with Prettier
- **Web**: Updated package.json with new devDependencies and scripts
- **Django**: Updated requirements.txt with drf-spectacular
- **Django**: Updated settings.py with INSTALLED_APPS, LOGGING, SPECTACULAR_SETTINGS
- **Spring Boot**: Updated pom.xml with springdoc-openapi dependency
- **Spring Boot**: Updated application.yml with springdoc configuration
- **Mobile**: Updated libs.versions.toml with new dependency versions
- **Mobile**: Updated app/build.gradle.kts with new dependencies and build features

### Fixed
- **Web**: TypeScript errors in ErrorBoundary and API services
- **Web**: ESLint unsafe assignment warnings in API services
- **Web**: Vite config TypeScript errors (path import, __dirname, manualChunks)

## [0.1.0] - 2026-10-06

### Added
- Initial project structure per PLANNING v2
- React web app with areas for adoptante, albergue, administrador
- Django backend-admin with albergues, mascotas, acceso apps
- Spring Boot backend-usuario with health endpoint
- Kotlin mobile_petly with Jetpack Compose
- MariaDB database model documentation
- Health endpoints on both backends (/api/health/)
- PLANNING.md v2 with sprint definitions and decisions