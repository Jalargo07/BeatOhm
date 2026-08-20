# Auditoría integral de BeatOhm

Fecha: 2026-08-20  
Alcance: árbol actual del repositorio, `PROJECT_INDEX.json`, configuración Android,
fuentes Kotlin, Room, recursos XML y documentación.

## Estado de la auditoría

- Se consultó primero `PROJECT_INDEX.json`, como exige `AGENTS.md`.
- Se revisaron las rutas de descarga, enriquecimiento, regeneración, tags, anuncios,
  Room, reproducción, importación, UI, permisos y dependencias.
- No se ejecutaron builds ni pruebas de comportamiento durante esta auditoría.
- El worktree ya tenía muchos cambios sin commit; por eso los hallazgos describen el
  estado actual, no atribuyen los cambios a una sola sesión.
- No se exponen secretos ni tokens en este documento.

## Hallazgos críticos y altos

| ID | Severidad | Área | Evidencia | Impacto | Recomendación | Esfuerzo |
|---|---|---|---|---|---|---|
| A-001 | Critical | Room/migraciones | `AppDatabase.kt:172` usa `fallbackToDestructiveMigration()` | Una migración no soportada puede borrar toda la biblioteca, playlists, ranking y temas | Eliminar fallback destructivo para release; agregar migraciones verificadas y test de migración/exportación de backup | L |
| A-002 | Critical | Rewarded/monetización | `InMobiManager.showRewardedAd()` llama `onRewardEarned()` en `onAdFetchFailed` y `onAdLoadFailed` | El usuario obtiene el desbloqueo sin ver un anuncio; además se falsean métricas y se incentiva abuso | Otorgar recompensa únicamente en `onRewardsUnlocked`; en fallo mostrar estado de no disponible y mantener la pausa | S |
| A-003 | High | Tags/contadores | `AudioTagWriter.writeTags()` devuelve `Unit` y absorbe errores; `finalizeMetadataUpdate()` incrementa `TagWriteCounter` después de llamar aunque la escritura interna haya fallado | El contador puede consumir un crédito aunque el archivo no se haya escrito | Cambiar escritores a `Result<Boolean>`/resultado tipado; incrementar solo con éxito confirmado | M |
| A-004 | High | Tags/metadata | `writeWithJaudiotagger()` reemplaza el tag con `createDefaultTag()` | En M4A/FLAC/OGG una actualización de metadata puede borrar artwork, lyrics u otros campos existentes | Leer el tag existente y modificar solo campos propios; pruebas por formato | M |
| A-005 | High | Tags/metadata | `writeMp3Tags()` reconstruye el ID3 con campos conocidos y no conserva APIC u otros frames | Una escritura de metadata posterior puede eliminar la carátula agregada previamente | Parser/serializador ID3 centralizado que conserve frames no gestionados y reemplace solo frames controlados | L |
| A-006 | High | Límite de tags | `AudioDownloader.writeMetadata()`, `enrichSong()` sin metadata aplicada y `PlaylistImportManager.writeTags()` escriben directamente sin pasar por el límite/incremento | El límite de 100 no representa todas las escrituras; algunos flujos son gratis y otros bloquean | Crear un único `TagWriteCoordinator`/repositorio para toda escritura de archivo y usarlo en descarga, importación, regen y selección manual | M |
| A-007 | High | Datos/seguridad | Las claves de Last.fm, Genius y Spotify se convierten en `BuildConfig` y viajan dentro del APK | Cualquier usuario puede extraerlas; pueden agotarse, abusarse o revocarse | Mover llamadas con secretos a backend/proxy; eliminar Spotify muerto y sus claves si sigue deshabilitado; rotar las claves existentes | L |
| A-008 | High | Permisos/red | Manifest usa `android.permission.AD_ID` en vez de `com.google.android.gms.permission.AD_ID`, `usesCleartextTraffic=true` y `MANAGE_EXTERNAL_STORAGE` | El identificador publicitario puede no declararse correctamente; cleartext amplía superficie; almacenamiento amplio aumenta riesgo y problemas de aprobación | Corregir permiso, desactivar cleartext salvo dominio justificado y migrar a SAF/MediaStore; justificar o retirar MANAGE_EXTERNAL_STORAGE | M |
| A-009 | High | Callback/lifecycle | `MusicRepository` guarda callback global estático (`onLimitReachedGlobal`) con referencia a Activity; MainActivity lo registra, pero no lo limpia | Posible fuga de Activity, diálogo contra una Activity destruida y callbacks duplicados | Usar estado persistente del servicio/Repository y eventos lifecycle-aware; limpiar callbacks en `onDestroy` | M |
| A-010 | High | Metadata regen | `MetadataRegenService` marca `done`/estado de forma distinta según excepción y reintenta la misma canción tras desbloqueo; el estado global de pausa no persiste si el proceso muere | Puede haber progreso incorrecto, canciones repetidas o regen que queda pendiente sin recuperación | Modelar estados por canción (`RUNNING`, `PAUSED_LIMIT`, `SUCCESS`, `FAILED`), persistir checkpoint y probar 2-4 workers concurrentes | L |
| A-011 | High | InMobi lifecycle/fill | `DownloadsFragment.loadBanner()` abandona si `isInitialized` aún es falso; no hay evento de SDK listo ni retry; no se conserva una política de no-fill | El banner puede no volver a intentarse después de una carrera de inicialización o no-fill | Exponer `StateFlow` de SDK y ciclo de banner; retry con backoff y métricas sin spam | M |
| A-012 | High | Consistencia DB/archivo | `MetadataCandidateRepository.applyCandidate()` actualiza Room, pero no pasa por `finalizeMetadataUpdate()`/escritura física | Elegir un candidato puede cambiar solo la DB y dejar nombre, tags y archivo desincronizados | Aplicar candidato mediante un caso de uso único que migre DB, renombre, escriba tags y marque APPLIED de forma transaccional/compensable | L |

## Hallazgos medios

| ID | Área | Evidencia | Recomendación |
|---|---|---|---|
| A-013 | Errores silenciosos | Hay numerosos `catch (_: Exception) {}` en lyrics, playback, escaneo, artwork, providers y UI | Usar errores tipados, logs sanitizados y estado visible para operaciones importantes; reservar silencios para cleanup | 
| A-014 | Bloqueo de UI | `ThemeManager.init()` usa `runBlocking` para leer Room; `MusicPlaybackService.recordScoreForPath()` también | Sustituir por inicialización async/cache y evitar I/O síncrono en callbacks del servicio | 
| A-015 | Concurrencia | `PlaylistImportManager` crea `CoroutineScope(Dispatchers.IO)` independiente en `generateWaveform()` y otros puntos | Hacer todos los jobs hijos del scope del servicio/importación para cancelación estructurada | 
| A-016 | Concurrencia | `TagWriteCounter` usa read-modify-write en SharedPreferences sin exclusión; varios workers pueden perder incrementos | Usar mutex/actor o contador persistente atómico dentro del coordinador | 
| A-017 | Red | Muchas llamadas usan `response.body?.string()` y `Response` manual; no existe política común de timeout/retry/cache/cancelación | Centralizar cliente, cerrar respuestas con `use`, límites por endpoint y backoff | 
| A-018 | Recurso | `AudioTagWriter` duplica parsing ID3 y escritura de frames en metadata/artwork/lyrics | Extraer modelo de frames y escritura atómica única; reduce corrupción y mantenimiento | 
| A-019 | Rendimiento | `findDuplicatePairs()` compara pares O(n²); `getAllSongsNow()` carga toda la biblioteca | Limitar candidatos, indexar/normalizar, paginar o mover clustering a una estrategia incremental | 
| A-020 | Rendimiento | Se crean `MusicRepository` y `MetadataCandidateRepository` repetidamente en playback/importación | Inyectar/reutilizar repositorios y DAOs desde Application/ViewModel/servicio | 
| A-021 | Redundancia | InMobi mantiene `showInterstitial()` aunque el flujo de límite usa rewarded; placement interstitial no tiene consumidor confirmado | Decidir si se conserva como formato futuro; si no, eliminar API/ID y evitar código muerto | 
| A-022 | Compatibilidad | El proyecto fija AGP 8.2.0 con Kotlin 2.1.20/KSP 2.1.20 y compileSdk 34 | Validar matriz oficial de compatibilidad y fijar versiones coherentes antes de subir target/SDK | 
| A-023 | Observabilidad | Se usan `Log.e` para trazas normales y se registran queries, URLs, títulos y respuestas parciales | Crear logger por niveles; redactar URLs/tokens/body y eliminar logs de producción | 
| A-024 | Documentación | README afirma versión estable 2.10, stack 1.3.1, estructura `com/musicdownloader` y componentes ya cambiados | Actualizar README, versión, estructura y limitaciones reales; separar nightly/stable | 
| A-025 | UI/strings | Persisten hardcodes visibles en XML/Kotlin: bandas del equalizer, idiomas, botones de tema, filtros, toast de contador y limpieza de pendientes | Migrar a `strings.xml` en los 4 idiomas y validar claves equivalentes | 
| A-026 | Accesibilidad | Auditoría estática detecta ImageView/ImageButton sin `contentDescription` visible en varios layouts | Añadir descripciones, estados seleccionados y pruebas TalkBack/contraste | 
| A-027 | Estados UI | Banner, importación, regen y metadata candidates exponen estados parciales, pero no todos tienen retry/error persistente | Unificar `Loading/Success/Empty/Error/Paused` y restauración tras rotación/proceso | 
| A-028 | Descarga | `AudioDownloader` escribe el archivo final antes de enriquecer y captura cualquier error de metadata sin informar al caller | Separar estado `downloaded` de `tagged`, usar temp completo y reportar degradación | 
| A-029 | Limpieza | `SpotifyProvider.search()` retorna antes de todo su código real; la implementación de token/API quedó inalcanzable | Eliminar código muerto o reactivar mediante decisión explícita; no mantener dos políticas | S |
| A-030 | Robustez | `MusicRepository.fetchLyricsForSong()` ignora excepciones y `AudioTagReader`/writer soportan formatos de forma desigual | Matriz de capacidades por formato y tests de lectura/escritura MP3/Opus/FLAC/M4A/OGG | 

## Código posiblemente muerto o redundante

La siguiente tabla distingue referencias comprobadas de elementos que requieren una
segunda comprobación con Android Lint/resource shrinker:

| Elemento | Evidencia actual | Decisión propuesta |
|---|---|---|
| `WavePhaseAnimator.kt` | Solo aparece la declaración; no hay consumidor en `app/src` | Eliminar tras confirmar que no hay reflexión/XML |
| Código posterior al `return emptyList()` de `SpotifyProvider.search()` | Inalcanzable en la función | Eliminar junto con credenciales Spotify si continúa deshabilitado |
| `InMobiManager.showInterstitial()` e ID interstitial | Existe, pero el límite usa `showRewardedAd()` | Conservar solo si habrá uso explícito; si no, eliminar |
| `GlowDrawable`, `NeonPaths`, `GradientPaths`, `GlassPaths`, `PhosphorPaths` | Sí tienen consumidores; no son muertos | Conservar |
| `TokenBankAdapter`, `ColorPaletteAdapter`, `ThemeAdapter`, `SongSelectorAdapter` | Tienen consumidores en Settings/Library/Theme | Conservar |
| `AudioTagReader` | Consumido por PlayerFragment/PlayerLyricsHelper/ArtworkLoader | Conservar, pero cubrir con tests |
| Recursos XML/drawables | No se puede declarar dead solo por búsqueda textual; algunos se referencian por binding, styles o reflection | Ejecutar Android Lint + `shrinkResources` en una variante de auditoría y revisar reporte |
| `backups/` dentro del worktree | Contiene archivos de música y rutas con caracteres problemáticos; provoca errores de `rg` | Sacar backups del repo o moverlos fuera del worktree/añadir regla clara de ignore |

## Mejoras visuales y UX propuestas

1. Rediseñar estados de carga/error/retry con un componente común en Descargas,
   Biblioteca, Importación, Regen y candidatos.
2. Revisar layouts a 320dp, landscape, fuente grande y modo oscuro; eliminar tamaños
   fijos donde rompen contenido.
3. Completar `contentDescription`, foco, estados seleccionados y contraste WCAG.
4. Sustituir Toasts técnicos por banners/snackbars localizados con acción de retry.
5. Mostrar claramente en candidatos si la elección actualiza DB, archivo y nombre.
6. Mostrar progreso real de artwork/lyrics/tags separado de waveform/color, incluyendo
   pausa por límite y razón del bloqueo.
7. Añadir indicador de fill/estado de anuncios solo para diagnóstico del usuario, sin
   mostrar IDs ni datos internos.

## Criterios de validación globales

- Ninguna escritura de tags incrementa el contador si el archivo no cambió con éxito.
- ClearMatch y selección manual dejan DB, ruta, tags, artwork y lyrics coherentes.
- Rewarded solo desbloquea tras `onRewardsUnlocked`.
- Una migración Room nunca destruye datos de usuario.
- Cancelar servicio cancela red, parsing, escritura, artwork y waveform hijos.
- No se registran secretos, tokens ni cuerpos de respuesta en producción.
- Pruebas de matriz de formatos y migraciones pasan en dispositivo/emulador.
- Android Lint no deja errores nuevos; recursos muertos quedan justificados.
