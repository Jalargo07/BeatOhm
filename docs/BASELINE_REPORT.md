# Baseline Report — BeatOhm

> Generated: 2026-08-16 (Phase 0 — Baseline & Work Hygiene)

## App Identity

| Field | Value |
|-------|-------|
| **App Name** | BeatOhm |
| **Namespace** | `com.beatohm` |
| **Application ID** | `com.musicdownloader` (Play Store identity) |
| **minSdk** | 24 (Android 7.0) |
| **targetSdk / compileSdk** | 34 |
| **Version** | 2.10-nightly.260814 (code 2260815) |
| **Language** | 100% Kotlin |
| **Build System** | Gradle (Kotlin DSL) + KSP |

## Key Dependencies

| Library | Version | Purpose |
|---------|---------|---------|
| Media3 ExoPlayer | 1.4.1 | Audio playback engine + MediaSession |
| Room | 2.6.1 | Local DB (songs, playlists, themes, regen, imports, candidates, playback events) |
| Navigation | 2.7.7 | Fragment navigation |
| Coil | 2.6.0 | Image loading (album art) |
| OkHttp | 4.12.0 | HTTP client (downloads, APIs) |
| Gson | 2.10.1 | JSON parsing |
| JAudioTagger | 3.0.1 | ID3/Vorbis tag writing |
| VorbisJava | 0.8 | Opus/Vorbis tag writing |
| Material | 1.11.0 | Material Design components |
| Coroutines | 1.10.1 | Async operations |
| Palette | 1.0.0 | Dynamic color extraction |
| DynamicAnimation | 1.0.0-alpha03 | Spring physics |
| Media | 1.7.0 | MediaSessionCompat (notification) |
| InMobi Ads | 11.4.1 | Monetization |
| Picasso | 2.8 | Image loading (InMobi) |
| Browser | 1.8.0 | Custom tabs |
| Play Services Ads ID | 18.0.1 | Advertising identifier |

## Room Database

**Current version: 10** with 9 entities:

| Entity | Table | Purpose |
|--------|-------|---------|
| LocalSong | songs | Song metadata |
| Playlist | playlists | User playlists |
| PlaylistSong | playlist_songs | Playlist membership |
| UserTheme | user_themes | Custom themes |
| RegenStatus | regen_status | Metadata regen tracking |
| ImportSession | import_sessions | Playlist import sessions |
| ImportTrackStatus | import_track_status | Individual track import status |
| PlaybackEvent | playback_events | Playback scoring |
| MetadataCandidateEntity | metadata_candidates | Ambiguous metadata candidates |

**Migrations:** 2→3 (playCount), 3→4 (waveformData), 4→5 (UserTheme), 5→6 (dominantColor), 6→7 (regen_status), 7→8 (import tables), 8→9 (playback_events), 9→10 (metadata_candidates)

## Architecture

**Package structure:** `com.beatohm.*`

- `com.beatohm` — Application, MainActivity, Services, Utils
- `com.beatohm.ads` — TagWriteCounter, InMobiManager
- `com.beatohm.audio` — WaveformExtractor, AudioVisualizerManager, LevelCaptureProcessor
- `com.beatohm.importer` — Playlist import (Spotify/Deezer/YouTube), URL detection, ImportSession/TrackStatus entities
- `com.beatohm.data` — Room DB, DAOs, Repositories (5 repos + 4 interfaces for DI)
- `com.beatohm.ui` — Fragments, Adapters, Custom Views, Helpers
- `com.beatohm.metadata` — MetadataFetcher, LyricsFetcher
- `com.beatohm.downloader` — AudioDownloader, ProxyDownloader
- `com.beatohm.extractor` — YouTubeExtractor
- `com.beatohm.model` — Song, SearchResult, DownloadState
- `com.beatohm.util` — FolderPatternParser

**Design patterns:**
- Clean Architecture (data/domain/presentation layers)
- Dependency Inversion (4 interfaces: IMusicRepository, IWaveformRepository, IRegenRepository, ILibraryRepository)
- SRP (extracted helpers: PlayerAnimationHelper, PlayerLyricsHelper, DeviceUtils)
- Singleton services (PlayerViewModel, InMobiManager)

## Foreground Services

| Service | ID | Purpose |
|---------|----|---------|
| MusicPlaybackService | — | Media3 playback + notification |
| MetadataRegenService | 1002 | Background metadata regeneration |
| ImportPlaylistService | 1003 | Background playlist import |

## Build Warnings / Notes

- `applicationId = "com.musicdownloader"` differs from `namespace = "com.beatohm"` — intentional (Play Store identity vs code package)
- `secrets.properties` loaded via Gradle Properties (GENIUS_ACCESS_TOKEN, LASTFM_API_KEY, SPOTIFY_CLIENT_ID, SPOTIFY_CLIENT_SECRET) — not committed to git
- `opencode.json`, `.opencode/`, `AGENTS.md`, `PROJECT_INDEX.json` are gitignored (agent tooling, not part of app)
- `backups/` directory exists on disk but is NOT tracked by git (untracked). Added to `.gitignore` in this phase.

## File Count Estimate

- ~60+ Kotlin source files
- ~40+ layout XMLs
- ~200+ localized strings (3 languages: es, en, pt)
- ~15+ drawable resources
- 6 icon packs (Material, Heroic, Neon, Glass, Gradient, Phosphor)
