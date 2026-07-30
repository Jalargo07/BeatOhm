# Music Downloader & Player — Plan de Desarrollo

## Reglas de Trabajo

1. **Yo soy opencode**. Mi rol es analizar tus requerimientos, clarificarlos con preguntas, y delegar la ejecución a los subagentes correspondientes (planner → coder → reviewer → documenter).
2. **Yo no escribo código, no planeo, no reviso, ni documento**. Solo orquesto el flujo entre subagentes.
3. **Los subagentes** se invocan con los comandos:
   - `@plan "descripción"` → Planning Agent (genera PLAN.md detallado)
   - `@code` → Coder Agent (implementa según el plan)
   - `@review` → Reviewer Agent (revisa el código implementado)
   - `@document` → Documenter Agent (genera manuales)
   - `@develop "descripción"` → Pipeline completo (plan → code → review → document)
4. **Si algo no está claro**, te preguntaré antes de delegar.
5. **Puedo proponer features** que no hayas considerado si veo oportunidades o riesgos.

---

## Proyecto Actual: Downloader para Android

App nativa Android 7+ que descarga audio de YouTube/YouTube Music, extrae metadata, etiqueta archivos ID3 y los guarda localmente.

### Stack actual
- Kotlin, minSdk 24, targetSdk 34
- InnerTube API (iOS client) para extracción de audio
- OkHttp + Gson para networking
- JAudioTagger para ID3
- Coroutines, ViewBinding, Material Design

---

## Próximo Feature Solicitado: Reproductor de Música Completo

Convertir la app en un reproductor estilo Spotify/Poweramp con:

### Features solicitados
| # | Feature | Prioridad |
|---|---------|-----------|
| 1 | Reproductor MP3 (play/pause, next/prev, seek) | Alta |
| 2 | Biblioteca: Carpetas, Álbumes, Artistas, Géneros, Años, Compositores | Alta |
| 3 | Playlists creadas por usuario | Alta |
| 4 | Modo aleatorio (shuffle) con orden antes/después | Media |
| 5 | Equalizer gráfico como Poweramp | Media |
| 6 | Carátula de álbum en reproducción | Alta |
| 7 | Letras en metadata (descargar + embeber) | Baja |
| 8 | Favoritos | Media |
| 9 | Atajos: forward/rewind, next/prev, volumen | Alta |
| 10 | Notificación persistente con controles | Alta |

### Timeline estimado
| Fase | Duración | Features |
|------|----------|---------|
| 1 | ~1 semana | Reproductor Media3 + notif + navegación |
| 2 | ~1 semana | Biblioteca (Room) + escaneo + carátulas |
| 3 | ~1-2 semanas | Playlists + favoritos + atajos |
| 4 | ~1-2 semanas | Shuffle + ordenamiento + equalizer |
| 5 | ~1-2 semanas | Letras + pulido final |
| **Total** | **~5-8 semanas** | |

### Riesgos identificados
- Equalizer depende del DSP del dispositivo
- APIs de letras pueden rate-limit
- InnerTube de YouTube es no-oficial y puede cambiar

### Preguntas abiertas para el usuario
- ¿Prefieres un reproductor simple primero o esperas a tener todo junto?
- ¿El equalizer es prioritario o puedes empezar sin él?
- ¿Las letras son importantes o es un "nice to have"?
- ¿Prefieres que los archivos se organicen en carpetas por artista/álbum automáticamente?
- ¿Te gustaría poder importar canciones que ya tengas en el dispositivo (no solo las descargadas)?
