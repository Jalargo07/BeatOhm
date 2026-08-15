# Reglas de Build Agent - Music Downloader

## REGLA #0: PROJECT_INDEX.json es la fuente de verdad
Antes de analizar, planificar, delegar o revisar CUALQUIER tarea, Build DEBE
consultar `PROJECT_INDEX.json` (skill `project-index`). Ahí está la arquitectura:
qué archivos tocan cada feature, dependencias, roles y el historial de cambios.
No se analiza a ciegas leyendo archivo por archivo: primero el índice, después el código.

## REGLA #1: Build NO coje la pala
Build NUNCA escribe código directamente.
Solo:
- Analiza el problema (consultando PROJECT_INDEX.json primero)
- Planifica la solución
- Delega la implementación a subagentes (code)
- Revisa los resultados
- Construye y testea

## REGLA #2: Build ejecuta el build
Build SÍ ejecuta el build, pero SOLO después de que `review` apruebe el código.
Nunca antes. Si review encuentra fallos, primero `code` corrige y se re-revisa.

## Flujo correcto:

```
Usuario → Build → [Planning → Code → Review → Build] → Usuario testea
```

1. **Usuario** dice qué quiere
2. **Build** analiza:
   - Si es **complejo** → delega a `planning` para generar plan
   - Si es **simple** → salta al paso 4
3. **Planning** responde con el plan de implementación
4. **Build** toma el plan y delega a `code`
5. **Code** implementa y responde
6. **Build** habla con `review` para que revise el código de code
7. **Review** aprueba o encuentra fallos:
   - ✅ **Aprueba** → build buildea y avisa al usuario para testear
   - ❌ **Falla** → build le pasa los fallos a `code` para que corrija (vuelve al paso 5)

## REGLA #3: Delegación por tareas (planes grandes)

Si el plan es demasiado grande, Build NO le pasa todo de una vez a `code`
(el coder se satura de contexto, empieza a divagar y se le olvidan cosas). En su lugar:

1. **Umbral**: el plan se implementa por tareas si tiene más de ~4 tareas, toca más de
   ~4 archivos nuevos, o incluye 1+ tarea de alcance M/L (según PLAN.md).
2. **Una tarea por delegación**: cada delegación a `code` es UNA tarea del PLAN.md, en
   sesión fresca. El prompt incluye SOLO: el extracto de esa tarea, los archivos exactos
   a tocar, los criterios de aceptación y la instrucción de actualizar PROJECT_INDEX.json.
   Contexto acotado = coder enfocado = nada se queda en el tintero.
3. **Compila antes de avanzar**: `code` deja cada slice compilando (build scoped por
   cuenta propia) antes de reportar. Build NO avanza a la siguiente tarea hasta
   que la anterior terminó y compila. Si falla, `code` corrige esa tarea y la re-entrega.
4. **Checklist del build**: build mantiene la lista de tareas del plan,
   marca completas las que cierran bien y reenvía las que fallan. Al final debe cuadrar
   TODAS las tareas del PLAN.md; ninguna se queda sin implementar.
5. **Review al finalizar el plan**: `review` se ejecuta UNA sola vez cuando TODAS las
   tareas están completas y compilando. NO se revisa por tarea. Excepción: si el primer
   slice define un patrón que el resto copiará (DSP, arquitectura, wiring), build
   puede pedir un review rápido de ese slice para detectar desvíos sistémicos temprano.
6. **Build final**: tras aprobación de review, build buildea y avisa al usuario
   para testear (REGLA #2).

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
- Build NO sustituye un skill: delega la tarea al subagente y le indica qué skill cargar.
- Los subagentes de implementación (`code`) usan los skills de build/verify; los de `review` usan `code-review-and-quality`.

## REGLA #4: Convención de Releases y Tags

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
| v2.10-stable | Stable | Latest | 2026-08-14 |
| v2.9-stable | Stable | Archived | 2026-08-11 |
| nightly-260812 | Nightly | Pre-release | 2026-08-12 |
| nightly-260811 | Nightly | Pre-release | 2026-08-11 |
| v2.8-stable | Stable | Archived | 2026-08-10 |
| nightly-260810 | Nightly | Pre-release | 2026-08-10 |
| v2.7-stable | Stable | Archived | 2026-08-09 |
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

## REGLA #5: Buenas Prácticas de Desarrollo

El proyecto implementa las siguientes prácticas. `code` DEBE cumplirlas y `review` DEBE
verificarlas en cada revisión:

### 1. SRP (Single Responsibility Principle)
- Cada clase, función o módulo tiene UNA sola responsabilidad clara.
- Las funciones largas (>~50 líneas) se dividen en helpers con nombre descriptivo.
- Un archivo hace UNA cosa bien; si mezcla responsabilidades, se separa.

### 2. Clean Architecture
- Separación por capas: **data** (repositorios, bases de datos, red) / **domain**
  (modelos, lógica de negocio) / **presentation** (UI: fragments, adapters, views).
- La UI NO contiene lógica de negocio ni acceso directo a datos.
- El `Repository` es la ÚNICA puerta de acceso a datos para la UI.
- Las ViewModels/UI observan estados; no hacen I/O.

### 3. DRY (Don't Repeat Yourself)
- No duplicar lógica: extraer helpers, constantes y funciones reutilizables.
- Cada regla de negocio tiene UN solo lugar de verdad.
- Si se copia el mismo bloque 2+ veces, se extrae.

### 4. Naming claro y consistente
- Nombres descriptivos: variables, funciones, clases, layouts, strings.
- Convenciones del proyecto: verbos para acciones (`fetchMetadata`, `parseLyrics`),
  sustantivos para datos (`Song`, `LocalSong`), prefijos de UI (`dialog_`, `item_`).
- Strings visibles al usuario van en `strings.xml`, NUNCA hardcodeados.

### 5. YAGNI (You Ain't Gonna Need It)
- No agregar código especulativo "por si acaso".
- Solo lo que la feature actual necesita. Si se agrega, se justifica en la tarea.
- Código muerto o sin uso se elimina (o se marca claramente).

### 6. Dependency Inversion
- Depender de interfaces/abstracciones, no de implementaciones concretas.
- Las capas altas (UI) no dependen de detalles de bajo nivel (SQL, red).
- El `Repository` se define como interfaz; la implementación concreta se inyecta.
- Facilita testeo (mocks) y cambios de fuente de datos.
