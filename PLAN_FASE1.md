# PLAN FASE 1 — Reproductor básico + Navegación + Notificación persistente

## Resumen

Refactorizar la `MainActivity` actual (única pantalla con toolbar, input URL, y lista de descargas) a una arquitectura con **Navigation Component + BottomNavigation** de 3 tabs. Agregar un **`MusicPlaybackService`** con Media3 ExoPlayer para reproducción de audio local, con MediaSession para notificación persistente y controles remotos. Mantener el `DownloadService` existente **intacto**.

---

## Requisitos funcionales

1. BottomNavigation con 3 tabs: **Player**, **Biblioteca**, **Downloads**
2. Tab **Downloads**: contiene la funcionalidad actual (input URL + lista de descargas)
3. Tab **Player**: reproductor funcional con play/pause, next/prev, seek bar, volumen, carátula, título/artista
4. Tab **Biblioteca**: esqueleto que muestre la música descargada escaneada del dispositivo (lista simple, sin funcionalidad de reproducción aún)
5. `MusicPlaybackService` con Media3 ExoPlayer que reproduce archivos locales
6. MediaSession para notificación persistente con controles (play/pause, next, prev, seek)
7. Coil para cargar carátulas desde el archivo de audio o URL

---

## Arquitectura propuesta

```
MainActivity (NavHost)
├── BottomNavigation
│   ├── Tab "Player"   → PlayerFragment
│   ├── Tab "Biblioteca" → LibraryFragment
│   └── Tab "Downloads" → DownloadsFragment
│
├── MusicPlaybackService (Media3 ExoPlayer + MediaSession)
│   └── Notif persistente con MediaSessionConnector
│
└── PlayerViewModel (compartido entre fragments si es necesario)
```

- `MainActivity` actúa como **NavHost** y contiene el `BottomNavigationView`.
- Cada tab es un **Fragment** independiente.
- `MusicPlaybackService` se comunica con los Fragments vía **binding to service** o un **ViewModel singleton**.
- `DownloadService` permanece igual, solo se mueve el código de UI de descargas a `DownloadsFragment`.

---

## Dependencias nuevas a agregar

```kotlin
// Media3 ExoPlayer
implementation("androidx.media3:media3-exoplayer:1.3.1")
implementation("androidx.media3:media3-session:1.3.1")
implementation("androidx.media3:media3-ui:1.3.1")

// Navigation Component
implementation("androidx.navigation:navigation-fragment-ktx:2.7.7")
implementation("androidx.navigation:navigation-ui-ktx:2.7.7")

// Coil para carátulas
implementation("io.coil-kt:coil:2.6.0")
```

---

## Lista de tareas (orden de implementación)

### 1. Agregar dependencias en `app/build.gradle.kts`
- Media3, Navigation Component, Coil
- Asegurar que `viewBinding = true` ya está habilitado (✓)

### 2. Crear recursos de navegación
- `res/navigation/nav_graph.xml` con 3 destinos: PlayerFragment, LibraryFragment, DownloadsFragment

### 3. Crear layouts para los Fragments
- `res/layout/fragment_player.xml` — reproductor: carátula (ImageView), título, artista, seekbar (Media3 PlayerView), buttons play/pause/next/prev, control de volumen
- `res/layout/fragment_library.xml` — esqueleto: RecyclerView + TextView vacío
- `res/layout/fragment_downloads.xml` — contiene el contenido actual de `activity_main.xml` (input URL + RecyclerView de descargas + TextView vacío)

### 4. Refactorizar `activity_main.xml`
- Convertir en un layout con `androidx.fragment.app.FragmentContainerView` (NavHost) + `com.google.android.material.bottomnavigation.BottomNavigationView`
- Quitar el contenido actual (URL input, lista) y dejarlo solo como contenedor

### 5. Crear `DownloadsFragment`
- Mover el código de UI de descargas de `MainActivity` a `DownloadsFragment`
- Inflar `fragment_downloads.xml`, configurar RecyclerView con `DownloadAdapter`, ViewModel, input URL, botón descargar
- `MainActivity` ya no maneja descargas directamente

### 6. Crear `PlayerFragment`
- Layout con: `PlayerView` de Media3 (o seekbar + controls custom), carátula (ImageView con Coil), título, artista, botones play/pause, next, prev, control de volumen
- Se vincula al `MusicPlaybackService` para controlar reproducción

### 7. Crear `LibraryFragment`
- Esqueleto: RecyclerView que escanee archivos de música en el directorio de descargas
- Mostrar lista simple con título/artista (sin reproducción aún en Fase 1)

### 8. Refactorizar `MainActivity`
- Eliminar código de descargas (se mueve a DownloadsFragment)
- Configurar `NavHostFragment` con `nav_graph.xml`
- Configurar `BottomNavigationView` con `NavigationUI.setupWithNavController`
- Mantener el `MainViewModel` compartido si es necesario (o crear ViewModels por fragment)

### 9. Crear `MusicPlaybackService`
- `class MusicPlaybackService : Service()` con `ExoPlayer` de Media3
- Inicializar `MediaSession` + `MediaSessionConnector` para manejar notificación y controles remotos
- Soportar: play/pause, next/prev, seek, cola de reproducción (Playlist)
- Notificación persistente estilo música con Media3 `MediaNotificationManager` (o manual con `MediaSessionConnector.setMediaButtonReceiver` y `ForegroundService`)
- Canal de notificación propio (`music_playback_channel`)

### 10. Registrar `MusicPlaybackService` en `AndroidManifest.xml`
- `<service android:name=".MusicPlaybackService" android:foregroundServiceType="mediaPlayback" android:exported="false" />`
- Agregar permiso `FOREGROUND_SERVICE_MEDIA_PLAYBACK` (Android 14+)

### 11. Crear `PlayerViewModel` (o repositorio de reproducción)
- Mantiene estado de reproducción actual (canción, posición, duración, playState)
- Expone LiveData/Flow para que los Fragments se suscriban
- Se comunica con `MusicPlaybackService` vía binding o intents

### 12. Integrar `MusicPlaybackService` con `PlayerFragment`
- PlayerFragment se bindea al servicio o usa un ViewModel que lo referencia
- Controles: play/pause, next, prev, seekbar, volumen
- La carátula se carga con Coil desde el archivo local o thumbnail URL

### 13. Escaneo de música en `LibraryFragment`
- Usar `ContentResolver` con `MediaStore.Audio.Media.EXTERNAL_CONTENT_URI`
- Filtrar por archivos en el directorio `MusicDownloader`
- Mostrar en RecyclerView

### 14. Verificar compilación y probar
- `./gradlew assembleDebug` sin errores
- Probar navegación entre tabs
- Probar reproducción de un archivo local
- Probar notificación persistente con controles
- Probar que `DownloadService` sigue funcionando igual

### 15. Personalizar estructura de carpetas de descarga
- El usuario puede definir un patrón de carpetas desde Settings/Configuración usando placeholders:
  - `{Artist}` - Nombre del artista
  - `{Album}` - Nombre del álbum
  - `{Title}` - Título de la canción
  - `{Track}` - Número de pista con leading zero (01, 02...)
  - `{Year}` - Año de publicación
  - `{Genre}` - Género musical
  - `{ArtistInitial}` - Primera letra del artista
- Patrón por defecto: `{Artist}/{Album}/{Track} - {Title}`
- El patrón se guarda en `SharedPreferences`
- `DownloadService` (método `getDownloadDirectory()` y lógica de guardado) usa el patrón para construir la ruta al guardar cada archivo
- Validar y sanitizar placeholders eliminando caracteres inválidos para nombres de archivo/carpeta (`\ / : * ? " < > |`)
- Si un placeholder no tiene metadata disponible, usar `Unknown` como fallback
- Mostrar vista previa del patrón resultante en la UI de Settings

---

## Archivos a crear

| Archivo | Propósito |
|---|---|
| `app/src/main/res/navigation/nav_graph.xml` | Grafo de navegación con 3 destinos |
| `app/src/main/res/layout/fragment_player.xml` | Layout del reproductor |
| `app/src/main/res/layout/fragment_library.xml` | Layout de biblioteca (esqueleto) |
| `app/src/main/res/layout/fragment_downloads.xml` | Layout de descargas (contenido actual) |
| `app/src/main/java/com/musicdownloader/MusicPlaybackService.kt` | Servicio de reproducción con Media3 |
| `app/src/main/java/com/musicdownloader/ui/PlayerFragment.kt` | Fragment del reproductor |
| `app/src/main/java/com/musicdownloader/ui/LibraryFragment.kt` | Fragment de biblioteca |
| `app/src/main/java/com/musicdownloader/ui/DownloadsFragment.kt` | Fragment de descargas (código movido) |
| `app/src/main/java/com/musicdownloader/ui/PlayerViewModel.kt` | ViewModel compartido de reproducción |
| `app/src/main/res/drawable/ic_player.xml` | Icono para tab Player |
| `app/src/main/res/drawable/ic_library.xml` | Icono para tab Biblioteca |
| `app/src/main/res/drawable/ic_downloads.xml` | Icono para tab Downloads |
| `app/src/main/java/com/musicdownloader/util/FolderPatternParser.kt` | Utilidad para parsear y resolver patrones de carpeta con placeholders |
| `app/src/main/java/com/musicdownloader/ui/SettingsFragment.kt` | Fragment de configuración para editar patrón de carpetas |
| `app/src/main/res/layout/fragment_settings.xml` | Layout del settings: EditText para patrón, vista previa, botón guardar |
| `app/src/main/res/navigation/nav_graph.xml` | Agregar destino `SettingsFragment` (o acceder desde DownloadsFragment) |

---

## Archivos a modificar

| Archivo | Cambio |
|---|---|
| `app/build.gradle.kts` | Agregar dependencias de Media3, Navigation, Coil |
| `app/src/main/AndroidManifest.xml` | Registrar `MusicPlaybackService`, agregar `FOREGROUND_SERVICE_MEDIA_PLAYBACK` |
| `app/src/main/java/com/musicdownloader/MainActivity.kt` | Refactorizar: solo NavHost + BottomNavigation; quitar lógica de descargas |
| `app/src/main/res/layout/activity_main.xml` | Reemplazar contenido con NavHostFragment + BottomNavigation |
| `app/src/main/res/values/strings.xml` | Agregar strings para tabs, notificación de reproducción, y settings/patrón de carpeta |
| `app/src/main/java/com/musicdownloader/DownloadService.kt` | Modificar `getDownloadDirectory()` para usar patrón desde SharedPreferences; sanitizar placeholders |
| `app/src/main/java/com/musicdownloader/download/DownloadManager.kt` | (si existe) Pasar metadata (artista, álbum, etc.) al momento de construir ruta de guardado |
| `app/src/main/java/com/musicdownloader/MainActivity.kt` | Agregar opción de Settings en toolbar o navegación |
| `app/src/main/res/navigation/nav_graph.xml` | Agregar destino SettingsFragment |

---

## Consideraciones técnicas

### Media3 con minSdk 24
- Media3 (androidx.media3) soporta minSdk 24 sin problemas. No se requieren workarounds.
- Usar `ExoPlayer.Builder(context).build()` que funciona desde API 24.
- `MediaSession` de Media3 no requiere `MediaSessionCompat` de la support library.

### Notificación persistente con Media3
- Media3 provee `MediaNotificationManager` o se puede manejar manualmente con `startForeground()`.
- Se recomienda usar `MediaSessionConnector` + `MediaNotification.Provider` (disponible en media3-session).
- Alternativa manual: `startForeground()` con `NotificationCompat.Builder` + `addAction()` para controles.

### Canal de notificación
- Crear canal `music_playback_channel` con `IMPORTANCE_LOW` (o `IMPORTANCE_DEFAULT` si se quiere sonido).

### Service lifecycle
- `MusicPlaybackService` debe correr en foreground mientras reproduce.
- Detener servicio cuando no haya nada reproduciendo (o mantenerlo si hay playlist).
- Usar `startForeground()` al inicio de reproducción, `stopForeground(STOP_FOREGROUND_REMOVE)` al pausar sin playlist activa.

### Permisos Android 14+
- `FOREGROUND_SERVICE_MEDIA_PLAYBACK` requerido para API 34+.

### PlayerView vs UI custom
- Media3 `PlayerView` (de `media3-ui`) provee seekbar, play/pause, next/prev, y visualización de metadata. Se puede usar directamente o customizar.
- Para Fase 1 se recomienda usar `PlayerView` con controles incluidos para simplificar.

### Coil con archivos locales
- `imageView.load(File(path))` o `imageView.load(Uri.fromFile(file))` para carátulas locales.
- `imageView.load(song.thumbnailUrl)` para carátulas desde URL.

---

## Cómo probar la Fase 1

### Pruebas de navegación
1. Abrir la app → deben verse 3 tabs en la parte inferior
2. Tocar cada tab → debe mostrar el fragment correspondiente
3. El tab activo debe resaltarse visualmente

### Pruebas de descargas (regresión)
1. Tab Downloads → pegar URL de YouTube → tocar Descargar
2. Ver que la descarga progresa, se completa, y aparece en la lista
3. Verificar que `DownloadService` funciona igual que antes

### Pruebas de reproducción
1. (Precondición) Tener al menos un archivo descargado
2. Tab Player → mostrar controles de reproducción
3. Tocar play → debe empezar a reproducir el archivo local
4. Seekbar debe mostrar progreso y permitir seek
5. Play/Pause debe alternar correctamente
6. Next/Prev deben funcionar si hay playlist
7. Volumen debe poder ajustarse

### Pruebas de notificación
1. Iniciar reproducción → debe aparecer notificación persistente
2. Notificación debe mostrar: carátula, título, artista
3. Controles en notificación: play/pause, next, prev (según versión de Android)
4. Cerrar notificación → debe pausar (o seguir en background según configuración)
5. Notificación debe actualizarse al cambiar de canción

### Pruebas de biblioteca
1. Tab Biblioteca → debe mostrar lista de canciones descargadas
2. Si no hay canciones, debe mostrar mensaje vacío

### Pruebas de integración
1. Descargar una canción → ir a Biblioteca → debe aparecer
2. Tocar una canción en Biblioteca → (Fase 2, solo esqueleto en Fase 1)
3. Ir a Player → usar controles sin que las descargas se interrumpan

### Build
```bash
./gradlew assembleDebug
```
No debe tener errores de compilación ni warnings de deprecación de Media3.
