<div align="center">

# 🎵 Music Downloader

### Tu música. Tu biblioteca. El alma de tu colección en una sola app.

**Descargá, organizá y escuchá tu música favorita directo desde YouTube — con metadata impecable, letras multi-fuente y un reproductor que no es secundario.**

[![Version](https://img.shields.io/badge/version-2.1-blue.svg)]()
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
- **Letras multi-fuente** — cascada de LRCLIB → Genius → lyrics.ovh con variantes de búsqueda (original, ASCII, sin tildes). Soporte de letras sincronizadas (LRC).
- **Tags ID3 completos** — cada archivo MP3 se graba con título, artista, álbum, género, año, número de track, carátula y letras. Listo para cualquier otro reproductor.
- **Proxy confiable** — fallback a loader.to cuando la descarga directa no está disponible.
- **Progreso visual** — barra de progreso en tiempo real durante la descarga.

### 🔧 Calidad de metadata

- **Corrección de tildes (Mojibake Fix)** — sistema completo de detección y reparación de doble encoding UTF-8. "Â¿QuÃ©" se convierte en "¿Qué" automáticamente.
- **Limpieza inteligente** — `cleanTitle()` elimina sufijos de YouTube (Official Video, Lyrics, VEVO), `cleanArtist()` limpia epítetos redundantes, `cleanChannelName()` maneja canales concatenados sin romper artistas reales.
- **Matching preciso** — normalización Unicode (NFD + diacríticos) para que tildes y mojibake no rompan la comparación de títulos/artistas.

### 🎧 Reproductor

- **Media3 ExoPlayer** — motor de reproducción nativo, estable y eficiente.
- **Shuffle & Repeat** — aleatorio y repetición (una canción / toda la lista).
- **Cola de reproducción** — bottom sheet con lista de reproducción actual, navegación y eliminación de canciones.
- **Mini-Player con artwork** — bitmap de carátula en la notificación y MediaMetadata.
- **Equalizer nativo** — ecualizador de hardware con bandas configurables y persistencia en SharedPreferences.
- **Volumen** — seekbar integrado que respeta el sistema.
- **Lyrics** — toggle para ver la letra de la canción actual (limpia timestamps LRC para lectura fluida).
- **Favoritos** — marcá cualquier canción como favorita con un toque.
- **Reproducción automática** — avanza a la siguiente canción al terminar.
- **Notificación avanzada** — controles previo/play/siguiente, artwork embebido, cola completa de reproducción.

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
- **Renombrado automático** — cuando la metadata se actualiza, el archivo se renombra al formato "Artista - Título" con limpieza de caracteres especiales.
- **Gestión de carpetas** — agregá, eliminá y revisá el estado de cada carpeta vinculada.

### 🛡️ Integridad de datos

- **Prevención de duplicados** — `cleanupDuplicates()` se ejecuta al inicio de cada escaneo. Elimina registros huérfanos y duplicados por título+artista.
- **DNS over HTTPS** — queries a APIs externas (letras, metadata) vía Cloudflare DoH para esquivar Private DNS (AdGuard) y mejorar conectividad.
- **Seguridad de tokens** — tokens de Genius almacenados en `secrets.properties` (gitignored), expuestos via `BuildConfig`.

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
│  + cleanTitle/Artist │  Limpieza de sufijos YouTube y mojibake
└──────────┬───────────┘
           │
           ▼
┌──────────────────────┐
│  LyricsFetcher       │  Cascada multi-fuente: LRCLIB → Genius → lyrics.ovh
│  LRCLIB+Genius+ovh   │  3 variantes de búsqueda por fuente
│  + DoH (Cloudflare)  │  DNS over HTTPS para máxima conectividad
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
│  OkHttp + JAudio     │  escribe tags ID3 completos (UTF-16) con JAudioTagger
└──────────┬───────────┘
           │
           ▼
    📁 Archivo MP3 listo
    con metadata, carátula y letras
```

La **biblioteca** se gestiona con Room Database (SQLite) y se escanea automáticamente al iniciar la app o al agregar carpetas nuevas. Cada escaneo ejecuta limpieza de duplicados y registros huérfanos.

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
| **DNS** | OkHttp DoH | 4.12.0 | DNS over HTTPS (Cloudflare) para lyrics y metadata |
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
git clone https://github.com/Jalargo07/Music-downloader-for-Android.git
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
├── MusicPlaybackService.kt        # Servicio Media3 con artwork, cola y notificación
├── PlaylistDetailActivity.kt      # Detalle de playlist
│
├── data/                          # Capa de persistencia
│   ├── AppDatabase.kt             # Room DB (songs, playlists, playlist_songs)
│   ├── LocalSong.kt               # Entidad de canción
│   ├── SongDao.kt                 # DAO de canciones (40+ queries)
│   ├── PlaylistDao.kt             # DAO de playlists
│   ├── MusicRepository.kt         # Repositorio: scan, folders, enrich, cleanup
│   ├── AudioTagWriter.kt          # Escritor de tags ID3 (UTF-16, mojibake-safe)
│   ├── Playlist.kt                # Entidad playlist
│   └── PlaylistSong.kt            # Relación playlist-canción
│
├── network/                       # Networking compartido
│   └── NetworkModule.kt           # OkHttp + DNS over HTTPS (Cloudflare DoH)
│
├── ui/                            # Capa de presentación
│   ├── PlayerFragment.kt          # Reproductor con controles completos + lyrics
│   ├── PlayerViewModel.kt         # Estado de reproducción (singleton)
│   ├── QueueBottomSheetDialogFragment.kt  # Cola de reproducción (drag-and-drop)
│   ├── LibraryFragment.kt         # Menú de biblioteca
│   ├── LibraryViewModel.kt        # ViewModel de biblioteca + enrich
│   ├── SongListFragment.kt        # Lista de canciones (con sort)
│   ├── CategoryListFragment.kt    # Lista por categoría
│   ├── FavoritesFragment.kt       # Lista de favoritos
│   ├── MostPlayedFragment.kt      # Más escuchadas
│   ├── PlaylistsFragment.kt       # Gestión de playlists
│   ├── FoldersFragment.kt         # Gestión de carpetas (SAF)
│   ├── DownloadsFragment.kt       # Pantalla de descargas + búsqueda
│   ├── SettingsFragment.kt        # Builder visual de patrón de carpetas
│   ├── EqualizerDialog.kt         # Dialog de ecualizador nativo
│   ├── MainViewModel.kt           # ViewModel de descargas + búsqueda
│   └── ArtworkLoader.kt           # Carga de carátulas desde archivos
│
├── metadata/                      # Enriquecimiento de metadata
│   ├── MetadataFetcher.kt         # iTunes + MusicBrainz (con cleanTitle/cleanArtist)
│   └── LyricsFetcher.kt           # LRCLIB → Genius → lyrics.ovh (multi-fuente)
│
├── downloader/                    # Capa de descarga
│   ├── AudioDownloader.kt         # Descarga + conversión + tags (UTF-16)
│   └── ProxyDownloader.kt         # Proxy loader.to
│
├── extractor/                     # Extracción de YouTube
│   └── YouTubeExtractor.kt        # InnerTube iOS client + búsqueda
│
├── model/                         # Modelos de dominio
│   ├── Song.kt                    # Modelo de canción (fileName mojibake-safe)
│   ├── SearchResult.kt            # Resultado de búsqueda YouTube
│   ├── PatternToken.kt            # Tokens del builder de patrones
│   └── DownloadState.kt           # Estado de descarga
│
└── util/                          # Utilidades
    └── FolderPatternParser.kt     # Parser de patrones de carpetas
```

---

## Roadmap

### ✅ Completado

- [x] **Letras sincronizadas** — lyrics con timestamps para karaoke (LRC) via LRCLIB + Genius.
- [x] **Corrección de tildes** — sistema anti-mojibake con reparación automática de encoding.
- [x] **DNS over HTTPS** — queries a APIs externas vía Cloudflare DoH.
- [x] **Renombrado automático** — archivos renombrados al actualizar metadata.
- [x] **Limpieza de duplicados** — eliminación automática de registros huérfanos y duplicados.
- [x] **Visualizador de cola drag-and-drop** — reordená canciones con gestos en la cola de reproducción.
- [x] **Ecualizador nativo** — ecualizador de hardware con bandas configurables y persistencia.
- [x] **Material 3 Dark Theme** — tema oscuro/claro con Dynamic Colors.

### 🔜 Próximamente

- [ ] **Android Auto** — integración con Android Auto para escuchar en el auto.
- [ ] **Widget de reproductor** — widget en el home screen con controles básicos.
- [ ] **Exportar playlists** — exportar/importar playlists en formato M3U.
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

**Hecho con Kotlin, mucho café ☕ y algo de alma**

</div>
