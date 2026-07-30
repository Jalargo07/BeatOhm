# Workflow de Desarrollo - Music Downloader

Este proyecto usa un pipeline de 4 agentes para el desarrollo estructurado.

## Agentes disponibles

| Agente | Archivo | Rol |
|--------|---------|-----|
| `planning` | `.opencode/agents/planning.md` | Clarifica reqs y genera PLAN.md |
| `coder` | `.opencode/agents/coder.md` | Implementa según el plan |
| `reviewer` | `.opencode/agents/reviewer.md` | Revisa calidad y genera REVIEW.md |
| `documenter` | `.opencode/agents/documenter.md` | Crea manuales y README |

## Comandos

| Comando | Descripción |
|---------|-------------|
| `@develop <requerimiento>` | Pipeline completo: Planning → Coder → Reviewer → Documenter |
| `@plan <requerimiento>` | Solo fase de Planning |
| `@code` | Solo fase de Coder (requiere PLAN.md) |
| `@review` | Solo fase de Reviewer (requiere código) |
| `@document` | Solo fase de Documenter (requiere código revisado) |

## Pipeline

```
Usuario → @develop "funcionalidad"
              │
              ▼
         ┌──────────┐
         │ Planning  │  Pregunta, clarifica, genera PLAN.md
         └─────┬────┘
               │
         ┌─────▼────┐
         │  Coder   │  Implementa según PLAN.md
         └─────┬────┘
               │
         ┌─────▼────┐
         │ Reviewer │  Revisa código, genera REVIEW.md
         └─────┬────┘
               │
         ┌─────▼──────┐
         │ Documenter │  Crea manuales y README
         └─────┬──────┘
               │
               ▼
         Código + docs listos
```

## Cómo usar

1. **Nueva funcionalidad**: `@develop quiero que la app pueda [funcionalidad]`
2. **Solo planificar**: `@plan necesito [funcionalidad]`
3. **Solo codificar** (si ya hay PLAN.md): `@code`
4. **Solo revisar** (si ya hay código): `@review`
5. **Solo documentar** (si ya hay código): `@document`
