package com.beatohm.ui

/**
 * PathData para el pack Neón Glow 2.0.
 * Formas bold rellenas; el builder del factory les aplica el efecto glow
 * con los colores del tema activo.
 *
 * Viewport 24x24. Estilo: sólidos chunky, bordes redondeados,
 * formas neón de barrio visualmente distintas a Lucide (stroke) y Phosphor (duotone).
 */
object NeonPaths {

    const val PACK_ID = "neon"

    /** iconKey → pathData (forma bold rellena) */
    val PATHS: Map<String, String> = mapOf(
        "play" to "M8,5 L21,12 L8,19 Z",
        "pause" to "M6,4 h4 v16 h-4 Z M14,4 h4 v16 h-4 Z",
        "next" to "M4,4 L14,12 L4,20 Z M14,4 h2 v16 h-2 Z",
        "prev" to "M20,4 L10,12 L20,20 Z M10,4 H8 v16 h2 Z",
        "shuffle" to "M4,16 L8.5,11.5 L4,7 V4 h3 l8,8 -8,8 v-3 Z M20,4 h-3 l-3.5,3.5 L16,11 h4 v-7 Z M16,16 h4 v-3 l-3.5,2.5 L20,17 Z M4,7 h3 l4.5,3.5 L8,14 H4 Z M20,17 h-4 l-4.5-3.5 L16,10 h4 Z",
        "repeat" to "M17,2 h4 v4 h-4 Z M3,8 h12 v8 H9 l-4,4 v-4 H3 Z M21,16 h-4 v4 l-4-4 h8 Z M12,8 l4-4 v4 h4 l-4,4 Z",
        "repeat_one" to "M17,2 h4 v4 h-4 Z M3,8 h12 v8 H9 l-4,4 v-4 H3 Z M21,16 h-4 v4 l-4-4 h8 Z M12,8 l4-4 v4 h4 l-4,4 Z M10.5,11 h3 v3 h-1.5 v-1 h-0.5 v-2 Z",
        "heart" to "M12,21 C12,21 3,15 3,8.5 A4.5,4.5,0,0,1,12,7 A4.5,4.5,0,0,1,21,8.5 C21,15 12,21 12,21 Z",
        "heart_border" to "M12,19.5 L4,12.5 A4.5,4.5,0,0,1,4,5.5 L12,5.5 L20,5.5 A4.5,4.5,0,0,1,20,12.5 Z M12,17.5 L5.5,11 A3,3,0,0,1,5.5,6 L12,6 L18.5,6 A3,3,0,0,1,18.5,11 Z",
        "volume" to "M3,9 h3.5 l5-4.5 v15 l-5-4.5 H3 Z M15,9 a3.5,3.5,0,0,1,0,6 M17.5,6.5 a7,7,0,0,1,0,11",
        "equalizer" to "M4,6 h2 v12 h-2 Z M11,3 h2 v18 h-2 Z M18,9 h2 v6 h-2 Z M8.5,6 h-1 v12 h1 Z M15.5,9 h-1 v6 h1 Z",
        "queue" to "M3,5 h12 v2 H3 Z M3,10 h12 v2 H3 Z M3,15 h8 v2 H3 Z M15,13 h2 v6 h-2 Z M14,16 h4 v0 h-4 Z",
        "lyrics" to "M3,4 h18 v2 H3 Z M3,8 h14 v2 H3 Z M3,12 h18 v2 H3 Z M3,16 h10 v2 H3 Z",
        "player" to "M12,2 A10,10,0,1,1,2,12 A10,10,0,0,1,12,2 Z M10,8 v8 l6-4 Z",
        "library" to "M4,4 h5 v16 h-5 Z M13,4 h5 v16 h-5 Z M19,6 l3-1 v16 l-3,1 Z M6.5,6 h-1 v12 h1 Z M15.5,6 h-1 v12 h1 Z",
        "downloads" to "M12,3 v11 M7,11 l5,5 5-5 M4,19 h16 v2 H4 Z",
        "music_note" to "M12,3 v10 A4,4,0,1,1,8,9 V3 Z M12,3 h4 v5 h-4 Z M8,17 A3,3,0,1,1,11,14 A3,3,0,0,1,8,17 Z",
        "mic" to "M12,2 A4,4,0,0,1,16,6 v5 A4,4,0,0,1,12,16 A4,4,0,0,1,8,11 V6 A4,4,0,0,1,12,2 Z M5,10 v2 a7,7,0,0,0,14,0 v-2 M12,18 v4 M9,22 h6",
        "genres" to "M9,3 v13 A3,3,0,1,1,6,13 V7 h12 v5 A3,3,0,1,1,15,16 V3 Z M15,16 A3,3,0,1,1,18,13 A3,3,0,0,1,15,16 Z M6,16 A3,3,0,1,1,9,13 A3,3,0,0,1,6,16 Z",
        "album" to "M12,2 A10,10,0,1,1,2,12 A10,10,0,0,1,12,2 Z M12,6 A6,6,0,1,0,18,12 A6,6,0,0,0,12,6 Z M12,9 A3,3,0,1,1,9,12 A3,3,0,0,1,12,9 Z",
        "playlist" to "M3,4 h14 v2 H3 Z M3,8 h14 v2 H3 Z M3,12 h10 v2 H3 Z M17,11 v7 l5-3.5 Z",
        "trending" to "M3,19 L8,12 l4,4 9-13 M17,3 h4 v4",
        "folder" to "M3,5 h7 l2,2 h9 v12 H3 Z",
        "search" to "M11,3 A8,8,0,1,1,3,11 A8,8,0,0,1,11,3 Z M19,19 l-4,-4",
        "settings" to "M12,15 a3,3,0,1,0,0,-6 a3,3,0,0,0,0,6 Z M19.4,10.2 l-1.5-0.9 a0.7,0.7,0,0,1-0.3-0.5 l-0.5-2.3 a7.5,7.5,0,0,0-1.8-1.1 l-0.4,1.6 a0.7,0.7,0,0,1-0.5,0.4 l-2.3,0.5 a7.5,7.5,0,0,0-1.1,1.8 L9.5,9 a0.7,0.7,0,0,1-0.4,0.5 l-2.3,0.5 a7.5,7.5,0,0,0-1.1,1.8 l1.6,0.4 a0.7,0.7,0,0,1,0.5,0.3 l0.9,1.5 a7.5,7.5,0,0,0,1.8,1.1 l0.5-2.3 a0.7,0.7,0,0,1,0.4-0.5 l2.3-0.5 a7.5,7.5,0,0,0,1.1-1.8 l-1.6-0.4 a0.7,0.7,0,0,1-0.5-0.3 Z",
        "playlist_add" to "M3,4 h14 v2 H3 Z M3,8 h14 v2 H3 Z M3,12 h10 v2 H3 Z M17,13 v-3 h2 v3 h3 v2 h-3 v3 h-2 v-3 h-3 v-2 Z",
        "back" to "M19,12 H5 M5,12 l6,-6 M5,12 l6,6",
    )
}
