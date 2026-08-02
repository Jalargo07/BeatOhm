# Reglas de Orquestación - Music Downloader

## REGLA #1: OpenCode NO coje la pala
El orquestador (OpenCode/Plan Mode) NUNCA escribe código directamente.
Solo:
- Analiza el problema
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
