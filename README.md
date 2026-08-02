<div align="center">

# 🎵 Music Downloader

### Tu música. Tu biblioteca. El alma de tu colección en una sola app.

**Descargá, organizá y escuchá tu música favorita directo desde YouTube — con metadata impecable, letras multi-fuente y un reproductor premium con visualizador de onda de audio real.**

[![Version](https://img.shields.io/badge/version-2.5-blue.svg)]()
[![minSdk](https://img.shields.io/badge/minSdk-24-green.svg)]()
[![targetSdk](https://img.shields.io/badge/targetSdk-35-orange.svg)]()
[![Kotlin](https://img.shields.io/badge/Kotlin-100%25-purple.svg)]()
[![License](https://img.shields.io/badge/license-PolyForm%20NC%201.0.0-orange.svg)](LICENSE)
[![API](https://img.shields.io/badge/API-Android%207.0%2B-brightgreen.svg)]()

---

<br/>

</div>

## Características principales

### ⬇️ Descarga

- **YouTube / YouTube Music** — pegá cualquier link (playlist o canción individual) y descargá.
- **Metadata automática** — InnerTube extrae título, artista y miniatura. Luego se enriquece con iTunes y MusicBrainz (álbum, género, año, número de track, carátula en alta resolución).
- **Letras multi-fuente** — cascada de LRCLIB → Genius → lyrics.ovh con variantes de búsqueda. Soporte de letras sincronizadas (LRC).
- **Tags ID3 completos** — cada archivo se graba con título, artista, álbum, género, año, número de track, carátula y letras.
- **Proxy confiable** — fallback a loader.to cuando la descarga directa no está disponible.

### 🎧 Reproductor Premium

- **Visualizador de onda de audio real** — extrae amplitud real del archivo de audio via MediaExtractor + MediaCodec. Cada barra representa ~3 segundos de audio. Datos cacheados en Room para carga instantánea. Barras 1/3 más grandes para mejor legibilidad.
- **Controles sobre la onda** — los controles de reproducción se superponen a la onda como fondo; barra de tiempo propia debajo con tiempo actual y total.
- **Scroll continuo tipo Poweramp** — cursor fijo al 30%, la onda se desplaza suavemente. Fling con inercia (OverScroller, fricción 0.008) para deslizamiento fluido.
- **Gesto invertido** — deslizar a la izquierda adelanta, a la derecha retrocede (empujar línea de tiempo).
- **Dynamic Gradient** — colores extraídos del Palette de la carátula (6 swatches), gradientes animados.
- **Album Art Glow** — efecto de brillo dual-layer en la carátula.
- **Letras glassmorphism** — panel translúcido con drag-handle para cerrar. Letras sincronizadas con resaltado de línea actual.
- **Letras estilo Spotify** — toda la letra visible desde el inicio, scroll manual libre, y auto-scroll que sigue la línea cantada. Auto-scroll se pausa al tocar/deslizar y se reanuda al tocar la línea resaltada o al acercarla al centro.
- **Cola glassmorphism** — bottom sheet con DiffUtil, animaciones escalonadas, stroke en canción actual.
- **Mini Player** — barra de progreso gradient integrada en el mini player.
- **Media3 ExoPlayer** — motor de reproducción nativo con controles en notificación.
- **Shuffle & Repeat** — aleatorio y repetición (una canción / toda la lista).
- **Favoritos con bookmark** — marcá cualquier canción como favorita con un toque.

### 📚 Biblioteca

- **Canciones** — lista completa con ordenamiento por título, artista, álbum o duración.
- **Álbumes / Artistas / Géneros / Años** — explorá por diferentes criterios.
- **Favoritos / Más escuchadas** — ranking por contador de reproducciones.
- **Playlists** — creá, editá y eliminá playlists personalizadas.
- **Fast Scan** — las canciones aparecen al instante al abrir la app (scan rápido de archivos sin bloquear la UI), y la metadata (artista, álbum, carátula, duración) se enriquece en background de a una por vez.

### 📁 Carpetas personalizadas

- **Escaneo de carpetas** — agregá directorios externos con SAF.
- **Enriquecimiento en background** — metadata automática sin duplicar datos.
- **Renombrado automático** — archivos renombrados al formato "Artista - Título".

---

## Stack tecnológico

| Componente | Tecnología | Versión | Para qué |
|------------|-----------|---------|----------|
| **Lenguaje** | Kotlin | — | 100% Kotlin |
| **UI** | XML + ViewBinding | — | Layouts declarativos |
| **Reproductor** | Media3 ExoPlayer | 1.3.1 | Motor de audio, MediaSession |
| **Base de datos** | Room | 2.6.1 | Persistencia (songs, playlists, waveformData) |
| **Networking** | OkHttp | 4.12.0 | Descargas, APIs |
| **JSON** | Gson | 2.10.1 | Parseo de respuestas API |
| **Tags** | JAudioTagger + vorbis-java | 3.0.1 + 0.8 | Escritura de tags ID3 y Vorbis Comments |
| **Imágenes** | Coil | 2.6.0 | Carga de carátulas |
| **Waveform** | MediaCodec | — | Decodificación PCM para RMS amplitude |
| **Design** | Material Components | 1.11.0 | Material 3, bottom sheets |
| **Palette** | Palette | 1.0.0 | Colores dinámicos desde carátula |

---

## Instalación

### Desde código fuente

```bash
git clone https://github.com/Jalargo07/Music-downloader-for-Android.git
cd Music-downloader-for-Android
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

### Requisitos

- Android Studio Hedgehog (2023.1) o superior
- JDK 17
- Dispositivo o emulador con Android 7.0 (API 24) o superior

---

## Estructura del proyecto

```
app/src/main/java/com/musicdownloader/
├── MainActivity.kt
├── MusicPlaybackService.kt
├── PlaylistDetailActivity.kt
│
├── audio/
│   └── WaveformExtractor.kt       # MediaExtractor + MediaCodec → RMS → FloatArray
│
├── data/
│   ├── AppDatabase.kt             # Room DB v4 (songs, playlists, waveformData)
│   ├── LocalSong.kt               # Entidad con waveformData
│   ├── SongDao.kt                 # DAO (40+ queries incl. updateWaveform, clearAllWaveforms)
│   ├── MusicRepository.kt         # Scan, folders, enrich, waveform extraction
│   └── AudioTagWriter.kt          # Tags ID3 + Vorbis Comments
│
├── ui/
│   ├── PlayerFragment.kt          # Reproductor premium con gradient, lyrics, waveform
│   ├── WaveformSeekBar.kt         # Waveform real con scroll, fling, Path batch
│   ├── DynamicGradientDrawable.kt # Gradient animado desde Palette
│   ├── GlowDrawable.kt            # Efecto de brillo dual-layer
│   ├── SyncedLyricsView.kt        # Letras con auto-scroll, scroll manual y tap-to-seek
│   ├── QueueBottomSheetDialogFragment.kt  # Cola glassmorphism
│   └── ArtworkLoader.kt           # Carga de carátulas desde archivos
│
├── metadata/
│   ├── MetadataFetcher.kt         # iTunes + MusicBrainz
│   └── LyricsFetcher.kt           # LRCLIB → Genius → lyrics.ovh
│
├── downloader/
│   ├── AudioDownloader.kt         # Descarga + conversión + tags
│   └── ProxyDownloader.kt         # Proxy loader.to
│
├── extractor/
│   └── YouTubeExtractor.kt        # InnerTube iOS client
│
└── model/
    ├── Song.kt
    ├── SearchResult.kt
    └── DownloadState.kt
```

---

## Changelog

### v2.5 Stable
- **Pantalla de enriquecimiento rediseñada** — pantalla completa (no modal) con 3 toggles independientes: Nombre correcto (iTunes/Deezer/MusicBrainz), Tags y Letra. Cada toggle controla su API por separado.
- **Buscador + orden alfabético** — la lista de canciones a enriquecer se busca y ordena fácilmente.
- **Deezer como fallback de metadata** — cuando iTunes bloquea (HTTP 403) o no matchea, Deezer encuentra artista/álbum reales.
- **Búsqueda sin artista como última instancia** — recupera canciones con el artista mal cargado (ej. "Boy Boy" → "Big Boy").
- **Limpieza de títulos con artista repetido** — "Soda Stereo - Soda Stereo - Entre Caníbales" → "Entre Caníbales".
- **Artistas-canal de YouTube** — "elvecindariocalle13" → "Calle 13", "velapuercavideos" → "La Vela Puerca", etc.
- **Consolidación automática de artistas** — unifica variantes ("Alexis y Fido"/"Alexis & Fido", "Arcangel"/"Arcángel") en un solo artista.
- **Fix géneros genéricos** — "Music", "Unknown", etc. ya no se escriben; iTunes les pone el real.
- **Fix letras** — el tag writer ya no borra las letras de los archivos al enriquecer.
- **Fix duplicados** — Mutex que serializa el enriquecimiento manual y el de fondo.
- **Fix notificación duplicada** — no se re-ofrece el enriquecimiento mientras corre.
- **Fix animaciones de drill-down** — sin música incorrecta al volver de artista/álbum.
- **Política de versionado** — ver `VERSIONING.md` (stable + nightly).

### v2.4 Stable
- **Fast Scan + Enriquecimiento async** — biblioteca aparece al instante; metadata (artista, álbum, carátula, duración, onda) se completa en background de a una canción por vez
- **Fix play/pause** — `onIsPlayingChanged()` es la única fuente de verdad del estado; elimina el reset espurio en `setSong()`
- **Reproductor rediseñado** — controles superpuestos a la onda como fondo, barra de tiempo propia, botones ±10s eliminados
- **Favoritos con bookmark** — ícono bookmark en vez de corazón
- **Onda 1/3 más grande** — mejor legibilidad
- **Letras estilo Spotify** — toda la letra visible desde el inicio, scroll manual libre, auto-scroll que se pausa con el dedo y se reanuda al tocar la línea resaltada o acercarla al centro
- **Fix overlap de letras** — posiciones cacheadas con altura máxima, sin superposición al resaltar
- **Fix fling de letras** — eje correcto del OverScroller (el scroll ya no vuela a la primera línea)
- **Drag-handle para cerrar letras** — arrastrá el panel hacia abajo para cerrarlo

### v2.3 Stable
- **Real Audio Waveform** — MediaExtractor + MediaCodec decodifica audio a PCM, calcula RMS por segmento
- **Densidad dinámica** — 1 barra por 3 segundos de audio (calculado automáticamente)
- **Room DB cache** — waveform persistido, migración automática desde formato anterior
- **Scroll continuo** — cursor fijo al 30%, canvas translation para scroll suave
- **Fling con inercia** — OverScroller con fricción 0.008 para deslizamiento fluido
- **Gesto invertido** — swipe izquierda = avanzar, derecha = retroceder
- **Path batch** — renderizado GPU-optimizado con doble capa (unplayed + played gradient)
- **Premium player** — dynamic gradient, album art glow, glassmorphism lyrics/queue
- **Material Symbols** — 36 iconos reemplazados
- **Mini Player** — barra de progreso gradient
- **DB Migration 3→4** — columna waveformData

### v2.2-night
- Mini Player, Library compact, Scroll preservation, Back navigation fix

### v2.1 Stable
- Mojibake fix, DoH DNS, duplicate cleanup, file rename, logo, README

### v2.0
- Buscador de canciones, carpetas personalizadas, cola mejorada, notificación Media3

---

## Licencia

```
PolyForm Noncommercial License 1.0.0
Copyright (c) 2026 Jorge Largo (Jalargo07)

Permiso de ejecutar, modificar y distribuir SOLO para fines no comerciales.
Prohibido el uso comercial: vender, alquilar, licenciar u ofrecer servicios pagos.
Texto completo: https://polyformproject.org/licenses/noncommercial/1.0.0/
```

---

<div align="center">

**Hecho con Kotlin y mucho café ☕**

</div>
