# Reglas de Orquestación - Music Downloader

## REGLA #0: PROJECT_INDEX.json es la fuente de verdad
Antes de analizar, planificar, delegar o revisar CUALQUIER tarea, el orquestador DEBE
consultar `PROJECT_INDEX.json` (skill `project-index`). Ahí está la arquitectura:
qué archivos tocan cada feature, dependencias, roles y el historial de cambios.
No se analiza a ciegas leyendo archivo por archivo: primero el índice, después el código.

## REGLA #1: OpenCode NO coje la pala
El orquestador (OpenCode/Plan Mode) NUNCA escribe código directamente.
Solo:
- Analiza el problema (consultando PROJECT_INDEX.json primero)
- Planifica la solución
- Delega la implementación a subagentes (code)
- Revisa los resultados
- Construye y testea

## REGLA #2: Build NO coje la pala
El orquestador NO ejecuta builds directamente. Delega al code agent.

## Flujo correcto:
1. Analizar (orquestador)
2. Planificar (orquestador)
3. Delegar implementación → `task(subagent_type="code")`
4. Review código → `task(subagent_type="review")`
5. Build → delegar al code agent
6. Test en dispositivo → orquestador revisa logs

## Subagentes disponibles:
- `code`: Implementa features, escribe código
- `review`: Revisa código, busca errores
- `planning`: Genera PLAN.md
- `document`: Genera documentación

## Build rules:
- NUNCA usar pipes de PowerShell con Gradle (`.\gradlew.bat ... 2>&1 | Select-String`)
- SIEMPRE usar `.\gradlew.bat <task> --no-daemon --console=plain`

## Skills de ingeniería (Agent Skills - addyosmani)
Los agentes DEBEN invocar el skill adecuado mediante la herramienta `skill` antes de actuar.
- `using-agent-skills`: meta - decide qué skill aplica a cada petición
- `project-index`: consultar PROJECT_INDEX.json (fuente de verdad de arquitectura) antes de analizar
- `spec-driven-development`: definir QUÉ construir antes de tocar código
- `planning-and-task-breakdown`: descomponer en tareas pequeñas y verificables
- `incremental-implementation`: implementar por slices verticales, verificando cada una
- `test-driven-development`: red-green-refactor para lógica nueva
- `debugging-and-error-recovery`: reproducción → localización → fix → guarda
- `code-review-and-quality`: review multi-eje antes de dar por bueno un cambio
- `code-simplification`: simplificar código sin cambiar comportamiento
- `security-and-hardening`: OWASP, secretos, input validation (app descarga de red)
- `source-driven-development`: decisiones de frameworks basadas en docs oficiales
- `git-workflow-and-versioning`: commits atómicos, trunk-based

Reglas:
- Si un skill aplica a la tarea, el agente que la ejecuta DEBE usarlo (vía `skill` tool).
- El orquestador NO sustituye un skill: delega la tarea al subagente y le indica qué skill cargar.
- Los subagentes de implementación (`code`) usan los skills de build/verify; los de `review` usan `code-review-and-quality`.

## REGLA #3: Convención de Releases y Tags

### Formato de Tags

| Tipo | Formato | Ejemplo | Descripción |
|------|---------|---------|-------------|
| **Stable** | `v{major}.{minor}.{patch}-stable` | `v2.7-stable` | Versión estable, probada, lista para producción |
| **Nightly** | `nightly-{YYMMDD}` | `nightly-260809` | Build nocturno, funcional pero en desarrollo |
| **Beta/RC** | `v{major}.{minor}.{patch}-beta.{n}` | `v3.0-beta.1` | Release candidate, prueba de features nuevas |

### Formato de Releases en GitHub

| Tipo | Título | Pre-release | APK | Changelog |
|------|--------|-------------|-----|-----------|
| **Stable** | `v{version}-stable` | No | Sí | Completo en body |
| **Nightly** | `nightly-{YYMMDD}` | **Sí** | Sí | Resumen en body |
| **Beta** | `v{version}-beta.{n}` | **Sí** | Sí | Resumen en body |

### Reglas de Naming

1. **NUNCA** usar nombres largos con descripciones (`v2.3 Stable - Real Audio Waveform Visualizer`)
2. **NUNCA** mezclar formatos (`v2.2-night` vs `nightly-260802`)
3. **SIEMPRE** usar el tag name como título del release
4. **SIEMPRE** adjuntar el APK como asset descargable
5. **SIEMPRE** incluir changelog en el body del release

### Flujo de Release

#### Stable Release
```bash
# 1. Actualizar versionName/versionCode en build.gradle.kts
# 2. Actualizar README.md con changelog
# 3. Commitear y pushear
git add -A && git commit -m "build: bump version to v2.8-stable"
git push origin main

# 4. Crear tag
git tag -a v2.8-stable -m "Release v2.8-stable"
git push origin v2.8-stable

# 5. Crear release con APK
gh release create v2.8-stable --title "v2.8-stable" --notes "Changelog..." app/build/outputs/apk/release/app-release.apk
```

#### Nightly Release
```bash
# 1. Actualizar versionName/versionCode en build.gradle.kts
# 2. Actualizar README.md con changelog
# 3. Commitear y pushear
git add -A && git commit -m "build: bump version to 2.8-nightly.260810"
git push origin main

# 4. Crear tag
git tag -a nightly-260810 -m "Nightly build 2026-08-10"
git push origin nightly-260810

# 5. Crear release como pre-release con APK
gh release create nightly-260810 --title "nightly-260810" --notes "Resumen..." --prerelease app/build/outputs/apk/debug/app-debug.apk
```

### Historial de Versiones (Referencia)

| Versión | Tipo | Estado | Fecha |
|---------|------|--------|-------|
| v2.7-stable | Stable | Latest | 2026-08-09 |
| nightly-260809 | Nightly | Pre-release | 2026-08-09 |
| v2.6-stable | Stable | Archived | 2026-08-08 |
| nightly-260808 | Nightly | Pre-release | 2026-08-08 |
| nightly-260802 | Nightly | Pre-release | 2026-08-02 |
| v2.5 | Stable | Archived | 2026-08-02 |
| v2.4 | Stable | Archived | 2026-08-02 |
| v2.3-stable | Stable | Archived | 2026-08-02 |
| v2.2-night | Nightly | Archived | 2026-08-02 |
| v2.1 | Stable | Archived | 2026-08-01 |
| v2.0 | Stable | Archived | 2026-08-01 |
