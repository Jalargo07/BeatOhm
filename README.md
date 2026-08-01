<div align="center">

# 🎵 Music Downloader

### Tu música. Tu biblioteca. Todo en una sola app.

**Descargá, organizá y escuchá tu música favorita directo desde YouTube — con metadata completa, letras y un reproductor que no es secundario.**

[![Version](https://img.shields.io/badge/version-1.0-blue.svg)]()
[![minSdk](https://img.shields.io/badge/minSdk-24-green.svg)]()
[![targetSdk](https://img.shields.io/badge/targetSdk-34-orange.svg)]()
[![Kotlin](https://img.shields.io/badge/Kotlin-100%25-purple.svg)]()
[![License](https://img.shields.io/badge/license-MIT-yellow.svg)](LICENSE)
[![API](https://img.shields.io/badge/API-Android%207.0%2B-brightgreen.svg)]()

---

<br/>

</div>

## Capturas

> _Capturas de pantalla próximas. Por ahora, imaginá una interfaz oscura con gradientes violeta a coral._

| Reproductor | Biblioteca | Descargas |
|:-----------:|:----------:|:---------:|
| [![Player](screenshots/player.png)](screenshots/player.png) | [![Library](screenshots/library.png)](screenshots/library.png) | [![Downloads](screenshots/downloads.png)](screenshots/downloads.png) |
| _Control total con seek, volumen, lyrics y favoritos_ | _Canciones, álbumes, artistas, géneros, playlists_ | _Pegá un link de YouTube y listo_ |

| Cola de reproducción | Equalizer | Carpetas |
|:--------------------:|:---------:|:--------:|
| [![Queue](screenshots/queue.png)](screenshots/queue.png) | [![Equalizer](screenshots/equalizer.png)](screenshots/equalizer.png) | [![Folders](screenshots/folders.png)](screenshots/folders.png) |
| _Gestioná tu cola de reproducción_ | _Ajustá las frecuencias a tu gusto_ | _Escaneá carpetas personalizadas_ |

---

## Características principales

### ⬇️ Descarga

- **YouTube / YouTube Music** — pegá cualquier link (playlist o canción individual) y descargá.
- **Metadata automática** — InnerTube extrae título, artista y miniatura. Luego se enriquece con iTunes y MusicBrainz (álbum, género, año, número de track, carátula en alta resolución).
- **Letras embebidas** — busca letras automáticamente via lyrics.ovh y las escribe en los tags ID3 del archivo.
- **Tags ID3 completos** — cada archivo MP3 se graba con título, artista, álbum, género, año, número de track, carátula y letras. Listo para cualquier otro reproductor.
- **Proxy confiable** — fallback a loader.to cuando la descarga directa no está disponible.
- **Progreso visual** — barra de progreso en tiempo real durante la descarga.

### 🎧 Reproductor

- **Media3 ExoPlayer** — motor de reproducción nativo, estable y eficiente.
- **Shuffle & Repeat** — aleatorio y repetición (una canción / toda la lista).
- **Cola de reproducción** — bottom sheet con lista de reproducción actual, navegación y eliminación de canciones.
- **Equalizer nativo** — ecualizador de hardware con bandas configurables y persistencia en SharedPreferences.
- **Volumen** — seekbar integrado que respeta el sistema.
- **Lyrics** — toggle para ver la letra de la canción actual directamente en el reproductor.
- **Favoritos** — marcá cualquier canción como favorita con un toque.
- **Reproducción automática** — avanza a la siguiente canción al terminar.
- **Notificación** — controles en la barra de notificaciones con MediaSession.

### 📚 Biblioteca

Organización inteligente de tu música, con múltiples vistas:

- **Canciones** — lista completa con ordenamiento por título, artista, álbum o duración.
- **Álbumes** — agrupados por álbum con carátula.
- **Artistas** — explorá por artista.
- **Géneros** — filtrá por género.
- **Años** — explorá por año de lanzamiento.
- **Favoritos** — todas tus canciones marcadas.
- **Más escuchadas** — ranking por contador de reproducciones.
- **Playlists** — creá, editá y eliminá playlists personalizadas. Agregá/quité canciones desde cualquier punto.

### 📁 Carpetas personalizadas

- **Escaneo de carpetas** — agregá directorios externos con SAF (Storage Access Framework) y la app escanea tu música existente.
- **Enriquecimiento en background** — si tus archivos no tienen metadata completa, la app la busca y mergea inteligentemente con iTunes/MusicBrainz sin duplicar datos.
- **Gestión de carpetas** — agregá, eliminá y revisá el estado de cada carpeta vinculada.

---

## Cómo funciona

El flujo de descarga tiene varias capas para máxima compatibilidad:

```
URL de YouTube/YouTube Music
        │
        ▼
┌──────────────────────┐
│  YouTubeExtractor    │  Extrae metadata básica (título, artista, thumbnail,
│  (InnerTube iOS)     │  duración) usando la API interna de YouTube
└──────────┬───────────┘
           │
           ▼
┌──────────────────────┐
│  MetadataFetcher     │  Enriquece con iTunes Search API y MusicBrainz:
│  iTunes + MB         │  álbum, género, año, track#, carátula HD
└──────────┬───────────┘
           │
           ▼
┌──────────────────────┐
│  LyricsFetcher       │  Busca la letra en lyrics.ovh por artista + título
│  lyrics.ovh          │
└──────────┬───────────┘
           │
           ▼
┌──────────────────────┐
│  ProxyDownloader     │  Obtiene URL de descarga via loader.to (proxy confiable)
│  (loader.to)         │
└──────────┬───────────┘
           │
           ▼
┌──────────────────────┐
│  AudioDownloader     │  Descarga el stream, convierte a MP3 (si necesario),
│  OkHttp + JAudio     │  escribe tags ID3 completos con JAudioTagger
└──────────┬───────────┘
           │
           ▼
    📁 Archivo MP3 listo
    con metadata, carátula y letras
```

La **biblioteca** se gestiona con Room Database (SQLite) y se escanea automáticamente al iniciar la app o al agregar carpetas nuevas.

---

## Stack tecnológico

| Componente | Tecnología | Versión | Para qué |
|------------|-----------|---------|----------|
| **Lenguaje** | Kotlin | — | 100% Kotlin, cero Java |
| **UI** | XML + ViewBinding | — | Layouts declarativos con acceso tipado |
| **Navegación** | Navigation Component | 2.7.7 | Navegación entre fragments con Safe Args |
| **Reproductor** | Media3 ExoPlayer | 1.3.1 | Motor de audio, MediaSession, UI |
| **Base de datos** | Room | 2.6.1 | Persistencia de canciones, playlists, favoritos |
| **Networking** | OkHttp | 4.12.0 | Descargas, APIs (iTunes, lyrics, InnerTube) |
| **JSON** | Gson | 2.10.1 | Parseo de respuestas API |
| **Tags ID3** | JAudioTagger | 3.0.1 | Escritura de metadata en archivos MP3 |
| **Imágenes** | Coil | 2.6.0 | Carga de carátulas (memoria + cache) |
| **Coroutines** | Kotlinx Coroutines | 1.7.3 | Operaciones async (descargas, DB, APIs) |
| **Design** | Material Components | 1.11.0 | Material 3, bottom sheets, diálogos |
| **Desugaring** | Desugar JDK Libs | 2.0.4 | APIs de Java 8+ en Android < 33 |

---

## Instalación

### Desde código fuente

```bash
# Clonar el repositorio
git clone https://github.com/TU_USUARIO/Music-downloader-for-Android.git
cd Music-downloader-for-Android

# Compilar (debug)
./gradlew assembleDebug

# Instalar en un dispositivo/emulador conectado
adb install app/build/outputs/apk/debug/app-debug.apk
```

### Requisitos

- Android Studio Hedgehog (2023.1) o superior
- JDK 17
- Dispositivo o emulador con Android 7.0 (API 24) o superior
- Permisos de almacenamiento (se piden al iniciar la app)

---

## Estructura del proyecto

```
app/src/main/java/com/musicdownloader/
│
├── MainActivity.kt                # Activity principal, navegación y service binding
├── MusicPlaybackService.kt        # Servicio Media3 con notificación
├── PlaylistDetailActivity.kt      # Detalle de playlist
│
├── data/                          # Capa de persistencia
│   ├── AppDatabase.kt             # Room DB (songs, playlists, playlist_songs)
│   ├── LocalSong.kt               # Entidad de canción
│   ├── SongDao.kt                 # DAO de canciones (40+ queries)
│   ├── PlaylistDao.kt             # DAO de playlists
│   ├── MusicRepository.kt         # Repositorio: scan, folders, enrich
│   ├── AudioTagWriter.kt          # Escritor de tags ID3
│   ├── Playlist.kt                # Entidad playlist
│   └── PlaylistSong.kt            # Relación playlist-canción
│
├── ui/                            # Capa de presentación
│   ├── PlayerFragment.kt          # Reproductor con controles completos
│   ├── PlayerViewModel.kt         # Estado de reproducción (singleton)
│   ├── QueueBottomSheetDialogFragment.kt  # Cola de reproducción
│   ├── LibraryFragment.kt         # Menú de biblioteca
│   ├── LibraryViewModel.kt        # ViewModel de biblioteca + enrich
│   ├── SongListFragment.kt        # Lista de canciones (con sort)
│   ├── CategoryListFragment.kt    # Lista por categoría
│   ├── FavoritesFragment.kt       # Lista de favoritos
│   ├── MostPlayedFragment.kt      # Más escuchadas
│   ├── PlaylistsFragment.kt       # Gestión de playlists
│   ├── FoldersFragment.kt         # Gestión de carpetas (SAF)
│   ├── DownloadsFragment.kt       # Pantalla de descargas
│   ├── EqualizerDialog.kt         # Dialog de ecualizador nativo
│   ├── MainViewModel.kt           # ViewModel de descargas
│   └── ArtworkLoader.kt           # Carga de carátulas desde archivos
│
├── metadata/                      # Enriquecimiento de metadata
│   ├── MetadataFetcher.kt         # iTunes + MusicBrainz
│   └── LyricsFetcher.kt           # lyrics.ovh
│
├── downloader/                    # Capa de descarga
│   ├── AudioDownloader.kt         # Descarga + conversión + tags
│   └── ProxyDownloader.kt         # Proxy loader.to
│
├── extractor/                     # Extracción de YouTube
│   └── YouTubeExtractor.kt        # InnerTube iOS client
│
└── model/                         # Modelos de dominio
    ├── Song.kt                    # Modelo de canción
    └── DownloadState.kt           # Estado de descarga
```

---

## Roadmap

Features planeadas o en discusión:

- [ ] **Visualizador de cola drag-and-drop** — reordená canciones con gestos en la cola de reproducción.
- [ ] **Ecualizador con presets** — perfiles predefinidos (Rock, Pop, Jazz, Classical, Bass Boost, etc.).
- [ ] **Letras sincronizadas** — lyrics con timestamps para karaoke en tiempo real.
- [ ] **Tema claro / oscuro** — toggle de tema con Material 3 dynamic colors.
- [ ] **Android Auto** — integración con Android Auto para escuchar en el auto.
- [ ] **Widget de reproductor** — widget en el home screen con controles básicos.
- [ ] **Modo nocturno automático** — basado en la hora del dispositivo.
- [ ] **Exportar playlists** — exportar/importar playlists en formato M3U.
- [ ] **Mejoras enriquecimiento** — fallback a más fuentes de metadata (Deezer, Spotify API).
- [ ] **Soporte de formatos** — FLAC, OGG, AAC (además de MP3).

---

## Licencia

```
MIT License

Copyright (c) 2026 Music Downloader

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

---

<div align="center">

**Hecho con Kotlin y mucho café ☕**

</div>
