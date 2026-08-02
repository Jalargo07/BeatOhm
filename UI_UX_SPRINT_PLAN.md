# UI_UX_SPRINT_PLAN.md — Plan CAÓTICO de Rediseño

> **"La app es funcional pero se siente como caca de pájaro pero de un pájaro que comió pepas de oro."**
> Vamos a cambiar eso. Sin reescribir todo. Sin romper nada. Con sangre, sudor y `dp`.

---

## AUDITORÍA RÁPIDA — Estado Actual vs Visión

| Componente | Estado Actual | Gap | Esfuerzo |
|---|---|---|---|
| **Colores** | Dark theme + purple/red básico | Paleta no coincide con la visión (#0B0910 vs #0F0F14, #9D35FF vs #8A2BE2) | Bajo |
| **Player** | **OVERHAUL COMPLETADO** — Dynamic gradient (Palette API), glow dinámico, cover 300dp con swipe, panel letras immersivo (glassmorphism), cola moderna con drag-reorder | ✅ COMPLETADO | — |
| **Mini Player** | **NO EXISTE** | Feature completo a crear | ALTO |
| **Biblioteca** | Lista vertical de categorías (cards altas) | Sin search bar, sin favoritos destacados, sin grid, cards desperdician espacio | MEDIO |
| **Downloads** | Search + URL + lista básica | Sin cover art en items, sin estados claros, botón download genérico | MEDIO |
| **Navegación** | Bottom nav 3 tabs | Sin indicador activo premium, sin mini player | MEDIO |
| **Microinteracciones** | Player: play/pause bounce, favorite heart, cover breathe, song change fade | Solo en player (falta downloads, library) | MEDIO |
| **Glow** | **IMPLEMENTADO EN PLAYER** — GlowDrawable + DynamicGradientDrawable con Palette API | Pendiente glow ambiental fuera del player | BAJO |
| **Equalizer visual** | Solo dialog, no visual en player | Sin barra animada en player | BAJO |
| **Empty states** | Texto simple | Sin ilustración ni equalizer decorativo | BAJO |

---

## MAPA DE DEPENDENCIAS

```
Design System (colores/tipografía)
    │
    ├──→ Player Rediseño ──→ Mini Player
    │         │                    │
    │         └──→ Glow ──────────┘
    │
    ├──→ Library Rediseño
    │
    ├──→ Downloads Rediseño
    │
    └──→ Microinteracciones
              │
              └──→ Polish Final
```

**REGLA DE ORO:** El Design System se hace PRIMERO. Todo lo demás depende de él.

---

## SPRINT 1 — "LOS CIMIENTOS" (1 día)

### Objetivo: Design System + Quick Wins visuales que cambien toda la app de golpe

### Quick Wins (alto impacto, bajo esfuerzo):

#### 1.1 Actualizar paleta de colores `colors.xml`
**Impacto: 🔥🔥🔥🔥🔥 | Esfuerzo: 15 min**

Cambiar la paleta para alinearla con la visión:

```xml
<!-- NUEVA paleta -->
background:         #0B0910  (era #0F0F14)
surface:            #12101A  (era #16161C)
cards:              #191722  (nuevo, para cards)
primary:            #9D35FF  (era #8A2BE2 — más neon)
secondary:          #FF304F  (era #FF6B6B — más rojo puro)
primary_container:  #6D22C9  (era #4B1D8F)
text_secondary:     #A7A3B0  (era #9E9EAE)
```

**Archivos a modificar:**
- `app/src/main/res/values/colors.xml`
- `app/src/main/res/color/color_nav_states.xml` (verificar que sigue funcionando)

**LANDMINES:**
- NO cambiar `on_surface`, `on_primary` — se rompe legibilidad
- Probar que el ripple de la bottom nav sigue visible
- El `queue_current_bg` debe mantener la transparencia

#### 1.2 Nuevo background para cards `bg_card.xml`
**Impacto: 🔥🔥🔥🔥 | Esfuerzo: 10 min**

Crear un nuevo drawable `bg_card.xml` con el color #191722 y corner radius 16dp para usar en todas las cards.

**Archivo a crear:**
- `app/src/main/res/drawable/bg_card.xml`

#### 1.3 Nuevo background con glow para cover del player
**Impacto: 🔥🔥🔥🔥 | Esfuerzo: 15 min**

Crear `bg_glow_cover.xml` — un layer-list con:
- Capa 0: Sombra glow morada sutil (#9D35FF con 20% alpha)
- Capa 1: Borde redondeado

**Archivo a crear:**
- `app/src/main/res/drawable/bg_glow_cover.xml`

#### 1.4 Nuevo botón de descarga rojo
**Impacto: 🔥🔥🔥🔥 | Esfuerzo: 10 min**

Crear `bg_download_button.xml` — shape con:
- Solid color: #FF304F
- Corner radius: 12dp
- Estado pressed: lighter red

**Archivo a crear:**
- `app/src/main/res/drawable/bg_download_button.xml`
- `app/src/main/res/color/bg_download_button_states.xml`

### Landmines del Sprint 1:
- NO tocar layouts todavía — solo colors y drawables
- NO cambiar `themes.xml` excepto si es estrictamente necesario
- Verificar que `color_nav_states.xml` sigue funcionando con el nuevo primary

---

## PLAYER OVERHAUL — IMPLEMENTADO ✅

> Overhaul completo del player UI/UX. Feature premium que cambia la experiencia de la app.

### 1. Fondo Dinámico con Palette API

**Clase:** `DynamicGradientDrawable.kt` → asignado como `background` del root del player.

- Extrae **dominant**, **vibrant** y **muted** de la cover con `Palette.from(bitmap).generate()`
- Mezcla colores extraídos con el fondo oscuro base (`#0B0910`) usando blending por fracción:
  - Top: base + dominant × 0.42
  - Mid: base + vibrant × 0.22
  - Bottom: base + muted × 0.12
- **Animación** de 1500ms entre canciones usando `ArgbEvaluator`
- Escala bitmap a 128px máximo antes de extraer palette (performance)
- Reset a colores default cuando no hay canción

### 2. Glow Dinámico con Blur

**Clase:** `GlowDrawable.kt` → asignado como `background` de `iv_glow`.

- **RadialGradient** centrado detrás del cover con el color dominante del álbum
- Tres stops de alpha: 0x4D → 0x1F → 0x00 (30% → 12% → transparente)
- Transición animada de color con `ArgbEvaluator`
- **Blur dinámico** con `RenderEffect.createBlurEffect(40f, 40f)` (API 31+)
- Fade in/out (alpha 0→1) al cargar canción, ocultar si no hay song

### 3. Tipografía Jerárquica

- **Título:** 26sp, bold, `on_surface`, ellipsize end, maxLines 1
- **Artista:** 15sp, `on_surface_variant`, alpha 0.7, ellipsize end, maxLines 1
- Layout: horizontal con título a la izquierda y botón favorito a la derecha
- **Animación al cambiar canción:** slide Y 20dp + fade in/out (200ms, DecelerateInterpolator)

### 4. Panel de Letras Inmersivo (Glassmorphism)

**Layout:** `lyrics_panel` (FrameLayout, constraints match_parent, visibility=GONE)

- **Background:** `bg_lyrics_scrim.xml` — gradiente vertical de `#55110F1C` → `#F20B0910` (scrim oscuro semitransparente)
- **Cover de fondo:** `iv_lyrics_background` — misma imagen del cover con blur dinámico (`RenderEffect 50f`, API 31+), escala 1.06→1.0 con fade 250ms
- **SyncedLyricsView:** Custom `View` que renderiza letras sincronizadas en canvas
  - Línea actual: 22sp bold blanca con `setShadowLayer(24f)` glow
  - Otras líneas: 16sp light, alpha 102 (40%)
  - Auto-scroll con `ValueAnimator` (300ms, DecelerateInterpolator)
  - Fallback a texto plano si no hay LRC sync
- **Header:** "Letras" (17sp bold) + botón cerrar (X)
- **Toggle:** `btn_lyrics` en bottom actions → `toggleLyrics()` en PlayerFragment
- **Transiciones:** Panel fade+scale in (250ms), out (200ms). Cover/controles se ocultan al abrir letras

### 5. Cola de Reproducción Moderna

**Clase:** `QueueBottomSheetDialogFragment.kt` (BottomSheetDialogFragment)

- **Layout:** `bottom_sheet_queue.xml` — handle visual + header con contador + divider + RecyclerView
- **Item:** `item_queue_song_modern.xml` — MaterialCardView con:
  - Drag handle (ic_drag) para reordenar
  - Artwork (ShapeableImageView 40dp, rounded corners medium)
  - Título (15sp, bold si es canción actual) + artista (13sp, secondary)
  - Icono playing animado (equalizer icon, scale 1→0.55 loop, color secondary)
  - Botón remover (X)
- **Drag-to-reorder:** `ItemTouchHelper` con `UP|DOWN` flags, sincroniza con `PlayerViewModel.moveQueueItem()`
- **Click:** `playerViewModel.playAtDisplay(index)` → `service.playFile(path)`
- **Auto-scroll** a canción actual al abrir
- **Background:** `bg_queue_sheet.xml`

### 6. Swipe en Cover para Cambiar Canciones

Implementado en `PlayerFragment.kt` con `OnTouchListener` en `cover_container`:

- **Gesture detection:** `ViewConfiguration.scaledTouchSlop` para detectar swipe horizontal
- **Preview:** `iv_cover_preview` se muestra con opacidad proporcional al delta del swipe
- **Commit:** Si supera `swipeThreshold` (100dp) → animación slide out + cambio de canción
- **Spring back:** `SpringAnimation` (damping=0.7, stiffness=300) para rebote al soltar
- **Deshabilitado** cuando panel de letras está abierto

### 7. Animaciones del Player

- **Cover breathe:** `ValueAnimator` 0.98→1.0, 1600ms, loop infinito reverse (solo cuando isPlaying)
- **Play/pause press:** Scale 0.9→1.0 con BounceInterpolator (200ms)
- **Favorite heart:** Scale 1→1.3→1 con DecelerateInterpolator + OvershootInterpolator
- **Song change:** Fade out 100ms → load → fade in 200ms + title slide up

### Archivos Creados

| Archivo | Tipo | Descripción |
|---|---|---|
| `ui/DynamicGradientDrawable.kt` | Kotlin | Drawable de gradiente vertical dinámico con Palette |
| `ui/GlowDrawable.kt` | Kotlin | Drawable radial glow con blur |
| `ui/QueueBottomSheetDialogFragment.kt` | Kotlin | Bottom sheet de cola con drag-reorder |
| `res/layout/bottom_sheet_queue.xml` | Layout | Layout del bottom sheet de cola |
| `res/layout/item_queue_song_modern.xml` | Layout | Item de canción en la cola |
| `res/drawable/bg_lyrics_scrim.xml` | Drawable | Gradiente scrim para panel de letras |
| `res/drawable/bg_queue_sheet.xml` | Drawable | Background del bottom sheet de cola |
| `res/drawable/bg_play_pause.xml` | Drawable | Background del botón play/pause |
| `res/drawable/bg_cover_rounded.xml` | Drawable | Background con esquinas redondeadas para cover |
| `res/drawable/bg_sheet_handle.xml` | Drawable | Handle visual del bottom sheet |

### Archivos Modificados

| Archivo | Cambios |
|---|---|
| `ui/PlayerFragment.kt` | Overhaul completo: Palette, glow, swipe, letras, animaciones |
| `res/layout/fragment_player.xml` | Cover 300dp responsive, glow layer, lyrics panel inmersivo, controles jerárquicos |

### Dependencias Utilizadas

- `androidx.palette:palette` — Extracción de colores de imágenes
- `androidx.dynamicanimation:dynamicanimation` — SpringAnimation para gestos
- `com.google.android.material:material` — BottomSheetDialogFragment, MaterialCardView
- `io.coil-kt:coil` — Carga de imágenes (ya existente)
- `android.graphics.RenderEffect` — Blur dinámico (API 31+, gracefully degraded)

---

## SPRINT 2 — "EL PLAYER BRILLA" (3 días) ✅ COMPLETADO

### Objetivo: Rediseñar el player para que se sienta premium — sin botón de descarga

> **Estado: ✅ COMPLETADO** — Ver sección "PLAYER OVERHAUL — IMPLEMENTADO" arriba para detalles técnicos.

### Día 1: Estructura + Cover protagonista + Swipe ✅

#### 2.1 Rediseñar `fragment_player.xml` ✅
- Cover responsive: `constraintWidth_percent=0.78` + `constraintWidth_max=300dp`
- **SIN botón de descarga** — la música ya está en el dispositivo
- Seekbar debajo de la cover, controles jerárquicos
- `iv_glow` con `DynamicGradientDrawable` + `GlowDrawable` + blur RenderEffect

#### 2.2 Panel de letras inmersivo ✅
- `SyncedLyricsView`: custom View con renderizado canvas, highlight 22sp bold blanco con glow shadow
- Cover de fondo con blur 50f (API 31+), transición scale + fade
- Header "Letras" + botón cerrar

#### 2.3 Swipe en cover ✅
- `OnTouchListener` con `ViewConfiguration.scaledTouchSlop`
- Preview proporcional, `SpringAnimation` (damping=0.7, stiffness=300)
- Deshabilitado cuando panel de letras está abierto

### Día 2: Animaciones + Polish ✅

#### 2.4 Cover breathe al reproducir ✅
- `ValueAnimator` 0.98→1.0, 1600ms, loop reverse

#### 2.5 Microinteracción play/pause ✅
- Scale 0.9→1.0 con BounceInterpolator, 200ms

#### 2.6 Glow dinámico (Palette API) ✅
- `DynamicGradientDrawable` + `GlowDrawable` con colores del álbum
- Transición animada 1500ms

### Día 3: Polish ✅
- Threshold 100dp, preview proporcional, spring back

### Landmines del Sprint 2 (resueltos):
- ✅ Lógica existente preservada
- ✅ Panel de letras funciona con blur y glassmorphism
- ✅ Player funciona sin canción (empty state)
- ✅ Swipe deshabilitado con letras abierto
- ✅ SpringAnimation performante

---

## SPRINT 3 — "EL MINI PLAYER" (2 días)

### Objetivo: Crear el mini player persistente — LA feature que más cambia la UX

### Día 1: Estructura del mini player

#### 3.1 Crear layout `mini_player.xml`
**Impacto: 🔥🔥🔥🔥🔥 | Esfuerzo: MEDIO**

Crear un layout independiente que se infliya dentro de `activity_main.xml`:
```
┌─────────────────────────────────────┐
│ [cover 40dp]  Título        ▶  ⋮   │
│               Artista              │
└─────────────────────────────────────┘
```

**Archivos a crear:**
- `app/src/main/res/layout/mini_player.xml`

**Elementos:**
- `ShapeableImageView` (cover 40dp, rounded)
- `TextView` título (1 bold, maxLines 1)
- `TextView` artista (secondary, maxLines 1)
- `ImageButton` play/pause
- `ImageButton` menú (opcional)
- Background: `@color/surface` con top border morado sutil
- Click listener → navegar al playerFragment

#### 3.2 Integrar mini player en `activity_main.xml`
**Impacto: 🔥🔥🔥🔥🔥 | Esfuerzo: MEDIO**

Cambiar `activity_main.xml`:
- El `nav_host_fragment` bottom constraint ahora apunta al mini player
- El mini player se coloca entre el nav host y el bottom nav
- El mini player tiene `visibility=gone` por defecto, se muestra cuando hay canción

**Archivos a modificar:**
- `app/src/main/res/layout/activity_main.xml`
- `app/src/main/java/com/musicdownloader/MainActivity.kt` (inflar mini player, observar PlayerViewModel)

### Día 2: Lógica del mini player

#### 3.3 Lógica de visibilidad del mini player
**Impacto: 🔥🔥🔥🔥🔥 | Esfuerzo: MEDIO**

En `MainActivity.kt`:
- Observar `PlayerViewModel.currentSong`
- Si `currentSong != null` → mostrar mini player con fade in
- Si `currentSong == null` → ocultar con fade out
- Click en mini player → navegar a playerFragment
- Actualizar cover/título/artista cuando cambia la canción
- Botón play/pause sincronizado con PlayerViewModel

**Archivos a modificar:**
- `app/src/main/java/com/musicdownloader/MainActivity.kt`

#### 3.4 Animaciones del mini player
**Impacto: 🔥🔥🔥 | Esfuerzo: BAJO**

- Fade in/out al mostrar/ocultar
- Slide up desde abajo
- Cover actualiza con crossfade

**Archivos a modificar:**
- `MainActivity.kt`

### Landmines del Sprint 3:
- El mini player NO debe tapar contenido del bottom nav
- El mini player DEBE respetar safe areas
- La navegación NO debe romperse — el nav host sigue funcionando igual
- El `fitsSystemWindows` del activity_main puede necesitar ajuste
- El toolbar existente puede necesitar reposicionarse
- PROBAR en pantalla pequeña (el espacio entre nav host y bottom nav es limitado)

---

## SPRINT 4 — "LA BIBLIOTECA COMPACTA" (2 días)

### Objetivo: Transformar la biblioteca de lista vertical a grid compacto con favoritos

### Día 1: Rediseño visual

#### 4.1 Nuevo `fragment_library.xml`
**Impacto: 🔥🔥🔥🔥 | Esfuerzo: MEDIO**

Cambiar la estructura:
```
Biblioteca

🔍 Buscar en tu música...  ← TextInputLayout sutil

Favoritos
─────────────────
[cover] Bien Loco        ♡  ▶
        Nova & Jony
[cover] Otra canción     ♡  ▶
        Artista
Ver todos →

Tu biblioteca
┌──────────┐ ┌──────────┐
│ 🎵       │ │ 👤       │
│ Canciones│ │ Artistas │
│ 1,284    │ │ 86       │
└──────────┘ └──────────┘
┌──────────┐ ┌──────────┐
│ 💿       │ │ 🎧       │
│ Álbumes  │ │ Géneros  │
│ 142      │ │ 18       │
└──────────┘ └──────────┘
```

**Archivos a modificar:**
- `app/src/main/res/layout/fragment_library.xml`

#### 4.2 Nuevo item de categoría compacto `item_library_grid.xml`
**Impacto: 🔥🔥🔥🔥 | Esfuerzo: BAJO**

Card cuadrada con:
- Icono centrado (48dp)
- Nombre de categoría
- Contador de items
- Background: `@color/cards` (#191722)
- Corner radius: 16dp
- Stroke sutil

**Archivos a crear:**
- `app/src/main/res/layout/item_library_grid.xml`

#### 4.3 Nuevo item de favorito `item_favorite_horizontal.xml`
**Impacto: 🔥🔥🔥 | Esfuerzo: BAJO**

List item horizontal con:
- Cover (48dp, rounded)
- Título + artista
- Botón play rápido
- Botón favorito

**Archivos a crear:**
- `app/src/main/res/layout/item_favorite_horizontal.xml`

### Día 2: Lógica

#### 4.4 Sección de favoritos en la biblioteca
**Impacto: 🔥🔥🔥🔥 | Esfuerzo: MEDIO**

En `LibraryFragment.kt`:
- Consultar canciones favoritas (ya existe `getFavoriteSongs` en el DAO)
- Mostrar las primeras 3-5 en la sección horizontal
- "Ver todos" navega a `favoritesFragment`

**Archivos a modificar:**
- `app/src/main/java/com/musicdownloader/ui/LibraryFragment.kt`
- `app/src/main/java/com/musicdownloader/ui/LibraryMenuAdapter.kt`

#### 4.5 Grid de categorías 2x2
**Impacto: 🔥🔥🔥 | Esfuerzo: MEDIO**

Usar `GridLayoutManager(2)` para las categorías en lugar de lista vertical.

**Archivos a modificar:**
- `LibraryFragment.kt`
- `LibraryMenuAdapter.kt` (nuevo viewType para grid)

### Landmines del Sprint 4:
- NO eliminar la funcionalidad de "enriquecimiento manual" — solo reposicionar el botón
- El `progress_enrich` y `tv_enrich_progress` deben mantenerse (pueden moverse al toolbar o como banner)
- Las categorías actuales (Canciones, Álbumes, Artistas, Géneros, Años, Playlists, Favoritos, Carpetas) NO deben perderse
- El `btn_enrich_manual` debe seguir accesible

---

## SPRINT 5 — "DOWNLOADS CON IDENTIDAD" (1-2 días)

### Objetivo: Que la pantalla de descargas se sienta como parte de la app, no como una herramienta genérica

### Día 1: Visual

#### 5.1 Rediseñar `fragment_downloads.xml`
**Impacto: 🔥🔥🔥 | Esfuerzo: MEDIO**

Cambios:
- Separar claramente: Búsqueda por nombre (arriba) + Descarga por URL (abajo)
- Agregar encabezados visuales: "Buscar música" / "Descargar por URL"
- Botón search con estilo `bg_download_button` (rojo)
- Botón download con estilo `bg_download_button` (rojo)

**Archivos a modificar:**
- `app/src/main/res/layout/fragment_downloads.xml`

#### 5.2 Rediseñar `item_download.xml` con cover
**Impacto: 🔥🔥🔥🔥 | Esfuerzo: MEDIO**

Cambiar de card vertical a list item horizontal:
```
┌──────────────────────────────────┐
│ [cover] Bien Loco            ✓   │
│         Nova & Jony              │
│         MP3 • 8.4 MB             │
└──────────────────────────────────┘
```

- Agregar `ShapeableImageView` para cover (48dp)
- Mostrar tamaño y formato
- Indicador de estado (✓ completado, progreso, error)
- El indicatorColor del progress bar debe ser `@color/secondary` (#FF304F)

**Archivos a modificar:**
- `app/src/main/res/layout/item_download.xml`
- `app/src/main/java/com/musicdownloader/ui/DownloadAdapter.kt`

### Día 2: Estados + Empty state

#### 5.3 Empty state con equalizer
**Impacto: 🔥🔥🔥 | Esfuerzo: BAJO**

Cuando no hay descargas ni búsqueda:
- Mostrar ilustración del equalizer (▂ ▅ ▇ ▅ ▂) en grande
- Texto: "Busca tu música o pega un enlace de YouTube"
- Estilo: centrado, secondary text

**Archivos a modificar:**
- `fragment_downloads.xml` (nuevo LinearLayout con empty state)

#### 5.4 Estados del botón de descarga
**Impacto: 🔥🔥🔥 | Esfuerzo: BAJO**

En `SearchResultAdapter`:
- Idle: "↓ Descargar" (rojo)
- Descargando: "Descargando..." (con progress)
- Completado: "✓" (verde sutil)

**Archivos a modificar:**
- `app/src/main/res/layout/item_search_result.xml`
- `app/src/main/java/com/musicdownloader/ui/SearchResultAdapter.kt`

### Landmines del Sprint 5:
- NO cambiar la lógica de descarga (YouTubeExtractor, AudioDownloader, etc.)
- NO cambiar la URL validation
- El `btn_settings` debe seguir funcionando
- El `pb_search` (progress bar de búsqueda) debe mantenerse

---

## SPRINT 6 — "MICROINTERACCIONES + ANIMACIONES PREMIUM" (2 días)

### Objetivo: Agregar vida a la app con animaciones sutiles, glow y transiciones premium

### Día 1: Microinteracciones básicas

#### 6.1 Animación de heart (favorito)
**Impacto: 🔥🔥🔥 | Esfuerzo: BAJO**

Al tocar el botón de favorito:
- Scale 1.0 → 1.3 → 1.0 con interpolación spring
- Cambio de icono: `ic_favorite_border` → `ic_favorite`
- Color: `@color/secondary` (#FF304F)

**Archivos a modificar:**
- `PlayerFragment.kt` (en `updateFavoriteIcon`)
- `FilteredSongAdapter.kt` (si tiene favorito)

#### 6.2 Transición de descarga: ↓ → progreso → ✓
**Impacto: 🔥🔥🔥🔥 | Esfuerzo: MEDIO**

En el botón de descarga:
- Idle → Descargando: fade out "↓" + fade in progress
- Descargando → Completado: fade out progress + scale bounce en "✓"
- Duración: 300ms cada transición

**Archivos a modificar:**
- `DownloadAdapter.kt`
- `SearchResultAdapter.kt`

#### 6.3 Fade/slide al cambiar de canción
**Impacto: 🔥🔥🔥 | Esfuerzo: BAJO**

En `PlayerFragment.kt`:
- Cuando cambia `currentSong`: fade out (100ms) → update cover/title → fade in (200ms)
- Cover: crossfade entre imágenes
- Título: slide de 20dp + fade

**Archivos a modificar:**
- `PlayerFragment.kt` (en observer de `currentSong`)

#### 6.4 Glow en cover del player
**Impacto: 🔥🔥🔥🔥 | Esfuerzo: BAJO**

Usar `bg_glow_cover.xml` — el glow se activa con `ViewCompat.setTransitionName` o simplemente como background estático.

Si el glow dinámico (extraer color de la cover) es muy costoso:
- Usar glow morado estático (#9D35FF con 15% alpha)
- Radio: 40dp más grande que la cover
- Blur: usar `RenderEffect.createBlurEffect` si minSdk >= 31, sino skip

**Archivos a modificar:**
- `fragment_player.xml` (agregar ImageView detrás de cover_container)
- `PlayerFragment.kt` (animación del glow)

### Día 2: Animaciones Premium

#### 6.5 Shared Element Transitions (MaterialContainerTransform)
**Impacto: 🔥🔥🔥🔥🔥 | Esfuerzo: ALTO**

Transiciones compartidas entre pantallas para una experiencia fluida:
- **Mini Player → Player:** Al tocar el mini player, la cover se "expande" hacia el player completo
- **Canción en lista → Player:** Al tocar una canción en la biblioteca, la cover hace transición al player
- **Implementación:** Usar `MaterialContainerTransform` de Material Design
- **Configuración:** 
  - `drawingViewId = R.id.nav_host_fragment`
  - `duration = 400ms`
  - `interpolator = FastOutSlowInInterpolator`
  - `fadeMode = MaterialContainerTransform.FADE_MODE_THROUGH`
- **Transition Name:** Asignar `transitionName` único por canción en cada vista

**Archivos a modificar:**
- `PlayerFragment.kt` (configurar shared element enter/return transition)
- `LibraryFragment.kt` / `SongListFragment.kt` (iniciar transición al tocar canción)
- `MainActivity.kt` (iniciar transición desde mini player)
- `fragment_player.xml` (asignar transitionName al cover_container)
- `item_song.xml` / `item_favorite_horizontal.xml` (asignar transitionName al cover)

#### 6.6 Morphing Transitions (Scale Fade + Desplazamiento eje Z)
**Impacto: 🔥🔥🔥🔥 | Esfuerzo: MEDIO**

Animaciones de morphing para elementos que cambian de forma:
- **Cover → Full screen:** Scale de 0.3 → 1.0 con fade simultáneo
- **Desplazamiento eje Z:** Elevar la vista durante la transición (elevation 0 → 8dp → 0)
- **Implementación:** Usar `ChangeBounds` + `ChangeTransform` + `ChangeImageTransform` como `TransitionSet`
- **Configuración:**
  - `setOrdering(TransitionSet.ORDERING_TOGETHER)`
  - `duration = 350ms`
  - `interpolator = DecelerateInterpolator(1.5f)`
  - `ChangeBounds` para tamaño/posición
  - `ChangeTransform` para scale y elevation (eje Z)

**Archivos a modificar:**
- `PlayerFragment.kt` (configurar morphing transition)
- `MusicPlaybackService.kt` (pasar transition name al intent)

#### 6.7 SpringAnimation para física de movimiento
**Impacto: 🔥🔥🔥🔥 | Esfuerzo: MEDIO**

Usar `DynamicAnimation` (SpringAnimation) para movimientos con física realista:
- **Swipe cover:** Al soltar, la cover rebota con `SpringAnimation` hacia su posición final
  - `SpringForce(dampingRatio = 0.7f, stiffness = 300f)`
  - Suaviza el final del gesto de swipe
- **Cover breathe:** En lugar de ValueAnimator, usar spring para el efecto de respiración
  - `SpringForce(dampingRatio = 0.5f, stiffness = 50f)`
  - Más natural que interpolación lineal
- **Botón play:** Spring en el scale del botón al tocar
  - `SpringForce(dampingRatio = 0.6f, stiffness = 500f)`
  - Sensación de "click" más satisfactoria
- **Pull to refresh (si aplica):** Spring en el overscroll

**Archivos a modificar:**
- `PlayerFragment.kt` (SpringAnimation para cover, play button, swipe)
- `build.gradle` (verificar que `dynamic-animation` está incluido en material)

#### 6.8 Transiciones de estado del player
**Impacto: 🔥🔥🔥 | Esfuerzo: BAJO**

Animaciones al cambiar estados:
- **Play → Pause:** Scale down 1.0 → 0.9 → 1.0 (spring) + icono morph
- **Shuffle on/off:** Rotation 0 → 360 + scale bounce
- **Repeat modes:** Scale 0.8 → 1.0 (spring) + icono change
- **Favorito:** Scale 1.0 → 1.3 → 1.0 (spring) + color change

**Archivos a modificar:**
- `PlayerFragment.kt` (en `setupControls`)

### Landmines del Sprint 6:
- Las animaciones DEBEN ser rápidas (< 400ms) — nada de animaciones lentas
- RESPETAR `prefers-reduced-motion` (verificar con `AnimationUtils.loadAnimation`)
- NO abusar del glow — solo en el player
- El `SyncedLyricsView` NO debe afectarse con las animaciones
- **MaterialContainerTransform** requiere `implementation 'com.google.android.material:material:1.11.0'` (ya incluido)
- **DynamicAnimation** requiere `implementation 'androidx.dynamicanimation:dynamicanimation:1.0.0-alpha03'` (verificar si está en dependencies)
- Probar en dispositivos con minSdk 24 — algunas APIs de animation requieren compat checks
- Las transiciones shared element NO deben romper la navegación del nav_graph
- Verificar que `transitionName` no colisione entre elementos

---

## SPRINT 7 — "NAVIGATION PREMIUM" (1 día)

### Objetivo: Que la navegación inferior se sienta como una app de verdad

#### 7.1 Indicador activo tipo cápsula
**Impacto: 🔥🔥🔥 | Esfuerzo: MEDIO**

El `BottomNavigationView` actual usa `ActiveIndicator` de Material3. Mejorar:
- Indicator color: `@color/primary` con 20% alpha
- Indicator shape: cápsula (pill)
- Label activo: `@color/primary`
- Label inactivo: `@color/text_secondary`
- Icon activo: `@color/primary`
- Icon inactivo: `@color/text_secondary`

**Archivos a modificar:**
- `app/src/main/res/layout/activity_main.xml` (atributos del BottomNavigationView)
- `app/src/main/res/color/color_nav_states.xml` (verificar)

#### 7.2 Top border sutil en bottom nav
**Impacto: 🔥🔥🔥 | Esfuerzo: BAJO**

Agregar un divider de 1dp con color `@color/outline` (#3A3A46) en la parte superior del bottom nav.

**Archivos a modificar:**
- `activity_main.xml` (agregar View de 1dp arriba del bottom_nav)

#### 7.3 Toolbar minimalista
**Impacto: 🔥🔥🔥 | Esfuerzo: BAJO**

El toolbar actual muestra "Music Downloader" siempre. Mejorar:
- Ocultar toolbar cuando el player está activo (o hacerlo transparente)
- Mostrar título de la pantalla actual
- Background: `@color/background` sin elevation

**Archivos a modificar:**
- `activity_main.xml`
- `MainActivity.kt` (actualizar título según fragment)

### Landmines del Sprint 7:
- El `bottom_nav` NO debe cubrir el mini player
- El `fitsSystemWindows` puede necesitar ajuste
- La navegación del nav_graph NO debe romperse

---

## SPRINT 8 — "POLISH FINAL" (1-2 días)

### Objetivo: Limpiar, pulir, y asegurar que todo funcione together

#### 8.1 Empty states consistentes
**Impacto: 🔥🔥 | Esfuerzo: BAJO**

En todas las pantallas, agregar empty states con:
- Icono del equalizer (▂ ▅ ▇ ▅ ▂) en secondary color
- Texto descriptivo
- Pantallas: Player (sin canción), Library (sin songs), Downloads (sin descargas), Favorites (sin favs)

**Archivos a modificar:**
- `fragment_player.xml`
- `fragment_library.xml`
- `fragment_downloads.xml`
- `FavoritesFragment.kt` (layout)

#### 8.2 Responsive check
**Impacto: 🔥🔥🔥 | Esfuerzo: MEDIO**

Probar en:
- Pantalla pequeña (320dp width)
- Pantalla grande (600dp+ width)
- Evitar overflow con el mini player + bottom nav + contenido
- El cover del player debe ser responsive (300dp max, % del width en pantallas pequeñas)

**Archivos a modificar:**
- `fragment_player.xml` (usar % en vez de dp fijo para cover)
- `mini_player.xml` (verificar)

#### 8.3 Loading states
**Impacto: 🔥🔥 | Esfuerzo: BAJO**

Agregar shimmer/progress donde haga falta:
- Library: al escanear canciones
- Downloads: al buscar
- Player: al cargar cover

**Archivos a modificar:**
- Varios fragments (agregar progress indicators)

#### 8.4 Error states
**Impacto: 🔥🔥 | Esfuerzo: BAJO**

Mostrar errores de forma amigable:
- Sin internet: "Conectate a internet para buscar"
- Error de descarga: "Error al descargar. Intenta de nuevo."
- Sin canciones: "Agrega carpetas en Biblioteca > Carpetas"

**Archivos a modificar:**
- Varios fragments

### Landmines del Sprint 8:
- NO agregar dependencias nuevas innecesarias
- Verificar que TODO funciona después de los cambios
- Hacer build release y probar en dispositivo real
- NO romper la notificación existente

---

## RIESGOS TÉCNICOS

| Riesgo | Probabilidad | Impacto | Mitigación |
|---|---|---|---|
| Mini player rompe navegación | ALTA | ALTO | Testear cada cambio de layout en dispositivo |
| Animaciones causan lag | MEDIA | MEDIO | Usar solo animaciones de layout/property, no custom drawing |
| Glow effect consume batería | MEDIA | BAJO | Usar solo en player, no en lists |
| Cover glow dinámico es muy lento | ALTA | BAJO | Empezar con glow estático, dinámico como optional |
| Cards rompen en pantallas pequeñas | MEDIA | MEDIO | Usar wrap_content y maxdp, no fixed sizes |
| Colores nuevos rompen legibilidad | BAJA | ALTO | Probar contraste con herramienta de accesibilidad |
| Bottom nav se solapa con mini player | ALTA | ALTO | ConstraintLayout bien configurado, testear en 320dp |
| Swipe cover interfiriendo con scroll de letras | MEDIA | MEDIO | Desactivar swipe cuando panel de letras está abierto |
| Shared Element Transition rompe navegación | MEDIA | ALTO | Probar transiciones con nav_graph, usar drawingViewId correcto |
| SpringAnimation consume CPU | BAJA | BAJO | Usar solo en elementos visibles, cancelar en onPause |
| DynamicAnimation no disponible en minSdk 24 | BAJA | MEDIO | Verificar compatibilidad, usar ValueAnimator como fallback |

---

## QUICK WINS RESUMEN (cosas que se pueden hacer YA)

| # | Cambio | Impacto | Tiempo |
|---|---|---|---|
| 1 | Actualizar `colors.xml` | 🔥🔥🔥🔥🔥 | 15 min |
| 2 | Crear `bg_glow_cover.xml` | 🔥🔥🔥🔥 | 10 min |
| 3 | Crear `bg_download_button.xml` | 🔥🔥🔥🔥 | 10 min |
| 4 | Crear `bg_card.xml` | 🔥🔥🔥 | 10 min |
| 5 | Animación play/pause scale | 🔥🔥🔥 | 15 min |
| 6 | Animación heart favorito | 🔥🔥🔥 | 15 min |
| 7 | Top border en bottom nav | 🔥🔥 | 5 min |
| 8 | Empty state con equalizer | 🔥🔥🔥 | 20 min |
| 9 | Panel de letras mejorado | 🔥🔥🔥🔥 | 30 min |
| 10 | Swipe cover (básico) | 🔥🔥🔥🔥🔥 | 45 min |

**Total quick wins: ~2.5 horas para cambios visibles en toda la app**

---

## BIG BETS (features ambiciosos)

| # | Feature | Riesgo | Recompensa |
|---|---|---|---|
| 1 | Mini Player | ALTO | REVOLUCIONARIO — cambia toda la UX |
| 2 | Player rediseñado con glow + swipe | MEDIO | La pantalla más vista se siente premium |
| 3 | Biblioteca con grid + favoritos | MEDIO | La app se siente como un producto completo |
| 4 | Glow dinámico (color de cover) | ALTO | Wow factor, pero puede ser lento |
| 5 | Equalizer visual animado | MEDIO | Identidad visual fuerte |
| 6 | Animaciones Premium (Shared Element + Spring) | MEDIO | Experiencia fluida y profesional |

---

## ORDEN DE IMPLEMENTACIÓN ÓPTIMO

```
Sprint 1: Colors + Drawables (base de todo)
    ↓
Sprint 2: Player + Swipe + Letras (la pantalla más importante)
    ↓
Sprint 3: Mini Player (la feature más impactante)
    ↓
Sprint 4: Library (la segunda pantalla más usada)
    ↓
Sprint 5: Downloads (completar la experiencia)
    ↓
Sprint 6: Animaciones Premium (darle vida y fluidez)
    ↓
Sprint 7: Navigation (pulir la estructura)
    ↓
Sprint 8: Polish (asegurar calidad)
```

**Por qué este orden:**
1. **Colors primero** — Todo lo demás depende de la paleta
2. **Player después** — Es lo que más se ve, más impacto inmediato (incluye swipe y letras mejoradas)
3. **Mini Player tercero** — Sin él, la app se siente rota
4. **Library cuarto** — Es la segunda pantalla más usada
5. **Downloads quinto** — Completar el loop de la app
6. **Animaciones Premium sexto** — Shared Element Transitions, SpringAnimation, Morphing
7. **Navigation séptimo** — Pulir la estructura
8. **Polish último** — Asegurar calidad

---

## CRITERIOS DE ACEPTACIÓN GLOBALES

### Visual
- [x] La paleta es consistente en todas las pantallas
- [x] El morado domina la interfaz
- [ ] El rojo representa descarga
- [ ] El equalizer aparece como elemento de identidad
- [ ] Las cards no desperdician espacio
- [x] El Player se siente premium (sin botón de descarga)
- [ ] No hay interfaz saturada
- [x] El panel de letras es legible y estilizado

### UX
- [ ] Es evidente qué canción está reproduciéndose (mini player + player)
- [ ] El progreso de descarga es visible
- [ ] Se puede navegar sin perder el mini player
- [ ] Biblioteca es fácil de explorar (grid + search)
- [ ] Favoritos tiene protagonismo
- [ ] Downloads comunica claramente sus estados
- [x] El swipe en cover funciona intuitivamente
- [ ] Las transiciones entre pantallas son fluidas

### Técnico
- [x] No se rompe funcionalidad existente
- [ ] No aparecen errores de consola
- [ ] No existen problemas evidentes de overflow
- [x] Las animaciones son fluidas (< 400ms)
- [x] No se agregan dependencias innecesarias
- [x] Los componentes siguen una estructura mantenible
- [ ] Build release funciona sin errores
- [ ] Las shared element transitions no rompen la navegación

---

## ARCHIVOS A ACTUALIZAR EN PROJECT_INDEX.json

Después de cada sprint, actualizar el `PROJECT_INDEX.json` con:
- Nuevos archivos creados (drawables, layouts)
- Archivos modificados (fragments, adapters)
- Nuevas features documentadas

**Archivos creados en el Player Overhaul (Sprint 2):**
- `ui/DynamicGradientDrawable.kt` — Fondo gradiente vertical dinámico con Palette API
- `ui/GlowDrawable.kt` — Halo radial glow con blur dinámico
- `ui/QueueBottomSheetDialogFragment.kt` — Bottom sheet de cola con drag-reorder
- `res/layout/bottom_sheet_queue.xml` — Layout del bottom sheet de cola
- `res/layout/item_queue_song_modern.xml` — Item de canción en la cola
- `res/drawable/bg_lyrics_scrim.xml` — Gradiente scrim para panel de letras
- `res/drawable/bg_queue_sheet.xml` — Background del bottom sheet de cola
- `res/drawable/bg_play_pause.xml` — Background del botón play/pause
- `res/drawable/bg_cover_rounded.xml` — Background con esquinas redondeadas para cover
- `res/drawable/bg_sheet_handle.xml` — Handle visual del bottom sheet

**Archivos modificados en el Player Overhaul (Sprint 2):**
- `ui/PlayerFragment.kt` — Overhaul completo: Palette, glow, swipe, letras, animaciones
- `res/layout/fragment_player.xml` — Cover responsive, glow layer, lyrics panel, controles jerárquicos

**Archivos pendientes de este plan (Sprints 3-8):**
- `fragment_library.xml` (transiciones shared element)
- `item_song.xml` / `item_favorite_horizontal.xml` (transitionName para shared element)
- `MainActivity.kt` (transiciones desde mini player)
- `build.gradle` (verificar dynamicanimation dependency)

**Dependencias verificadas:**
- `com.google.android.material:material:1.11.0` — ✅ Incluido (MaterialContainerTransform, BottomSheetDialogFragment, MaterialCardView)
- `androidx.dynamicanimation:dynamicanimation` — ✅ Incluido (SpringAnimation)
- `androidx.palette:palette` — ✅ Incluido (extracción de colores)
- `io.coil-kt:coil` — ✅ Incluido (carga de imágenes)

---

## NOTA FINAL

Este plan es **CAÓTICO pero ACCIONABLE**. Cada sprint produce cambios visibles. No hay sprints de "preparación" o "investigación" — todo es implementación directa.

La clave es: **empezar por los quick wins (Sprint 1) para tener motivación, y construir features grandes (Mini Player, Player con swipe y animaciones premium) sobre una base sólida.**

**Cambios implementados (Player Overhaul):**
- ✅ Fondo dinámico con Palette API (colores del álbum, animación 1500ms)
- ✅ Cover con glow dinámico (RadialGradient + blur RenderEffect)
- ✅ Tipografía jerárquica (26sp bold título, 15sp artist alpha 0.7)
- ✅ Panel de letras inmersivo (glassmorphism, SyncedLyricsView canvas, blur cover fondo)
- ✅ Cola de reproducción moderna (BottomSheet con drag-reorder, playing animation)
- ✅ Swipe en cover (OnTouchListener + SpringAnimation para cambio de canciones)
- ✅ Animaciones (cover breathe, play/pause bounce, favorite heart, song change fade)
- ✅ Eliminado botón de descarga (la música ya está en el dispositivo)

**Pendiente:**
- Sprint 3: Mini Player
- Sprint 4: Biblioteca compacta
- Sprint 5: Downloads con identidad
- Sprint 6: Animaciones premium (Shared Element, Morphing, Spring)
- Sprint 7: Navigation premium
- Sprint 8: Polish final

Si en algún momento algo se rompe, el PLAN.md existente tiene el historial de lo que ya funciona — NO tocar la lógica de reproducción, descarga, ni metadata.

**Let's make this app not look like bird poop anymore. 🎵**
