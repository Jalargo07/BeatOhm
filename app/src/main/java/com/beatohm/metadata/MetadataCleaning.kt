package com.beatohm.metadata

import android.util.Log
import com.google.gson.JsonObject

/**
 * Helpers top-level de limpieza de metadata compartidos entre [MetadataFetcher],
 * los `MetadataProvider` y [com.beatohm.data.MusicRepository] (DRY).
 *
 * DECISIÓN DE ARQUITECTURA: estas funciones vivían como métodos propios de
 * MetadataFetcher. Se extrajeron a top-level de `com.beatohm.metadata` para que
 * los providers puedan reutilizarlas al armar queries (cleanChannelName/cleanTitle)
 * y al limpiar campos de candidatos (cleanTitle/cleanArtist/extractYear) sin
 * duplicar lógica. MetadataFetcher delega a estas mismas funciones.
 *
 * `normalizeForMatch()` fue movido desde MetadataFetcher (T12) para que
 * MusicRepository pueda usarlo en la detección de metadata sospechosa.
 * `isGoodMatch()` quedan PRIVADOS en MetadataFetcher (scoring/validación T4).
 *
 * [jsonString] es un helper de parseo JSON compartido por los providers (lee campos
 * string sin romperse ante valores JSON null).
 */

/**
 * Limpia el título que viene de fuentes externas eliminando sufijos de YouTube.
 * Ejemplos:
 *   "Bohemian Rhapsody (Official Video)" → "Bohemian Rhapsody"
 *   "Shape of You [Lyrics]" → "Shape of You"
 *   "Blinding Lights (Remix 2024)" → "Blinding Lights"
 *   "Havana ft. Young Thug" → "Havana" (ft. se maneja aparte en el artista)
 */
fun cleanTitle(title: String): String {
    var clean = title
    // Remover paréntesis y corchetes con contenido: (Official Video), [Lyrics], (Audio), (Live), etc.
    clean = clean.replace(Regex("\\s*[\\(\\[].*?[\\)\\]]"), "")
    // Remover "ft.", "feat.", "featuring" y lo que siga (a menos que esté al inicio)
    clean = clean.replace(Regex("\\s+(?:ft\\.?|feat\\.?|featuring)\\s+.*$", RegexOption.IGNORE_CASE), "")
    // Remover " - Official Video", " - Lyrics", etc. (guion largo o corto)
    clean = clean.replace(Regex("\\s*[-–—]\\s*(?:Official|Lyrics|Audio|Live|HD|4K|Explicit|Clean|Remix|Cover|Version|Version).*", RegexOption.IGNORE_CASE), "")
    // Remover "MV", "M/V" al final
    clean = clean.replace(Regex("\\s+(?:MV|M/V)$"), "")
    // Remover year al final si es "(2024)" o "[2024]"
    clean = clean.replace(Regex("\\s*[\\(\\[]\\d{4}[\\)\\]]$"), "")
    // Remover texto después de "|" (pipe de canales: "Song | The Cypher Effect")
    clean = clean.replace(Regex("\\s*\\|.*$"), "")
    // Remover nombres de canales/series conocidos
    clean = clean.replace(Regex("\\s*(?:The\\s+)?(?:Cypher\\s+Effect|Mic\\s+Check\\s+Session|Freestyle|Batalla|Red\\s+Bull|Audiomack|SoundCloud).*", RegexOption.IGNORE_CASE), "")
    return clean.trim()
}

/**
 * Limpia el artista: remueve " - Topic", "VEVO", etc.
 */
fun cleanArtist(artist: String): String {
    var clean = artist
    // Remover " - Topic" (canal de YouTube genérico)
    clean = clean.replace(Regex("\\s*-?\\s*Topic$"), "")
    // Remover "VEVO"
    clean = clean.replace(Regex("\\s*VEVO$", RegexOption.IGNORE_CASE), "")
    return clean.trim()
}

/**
 * Limpia sufijos de canales de YouTube del artista para mejorar el match con las fuentes.
 * Ej: "La Mosca Oficial" → "La Mosca", "BersuitTV" → "Bersuit", "elvecindariocalle13" → "calle13"
 */
fun cleanChannelName(artist: String): String {
    var clean = artist
    // Remover paréntesis/corchetes con contenido: "(Oficial)", "[Official]", etc.
    clean = clean.replace(Regex("\\s*[\\(\\[].*?[\\)\\]]"), "")
    // Remover nombres de playlists: "Letras Trap & Más", "Lyrics & Vibes", etc.
    clean = clean.replace(Regex("^\\s*(?:Letras?|Lyrics?|Canciones?|Songs?|Músicas?|Music)\\b.*", RegexOption.IGNORE_CASE), "")
    // Remover "En Español", "En vivo", "En Directo", "En Concierto" al final
    clean = clean.replace(Regex("\\s+En\\s+(?:Español|Espanol|Vivo|Directo|Concierto)$", RegexOption.IGNORE_CASE), "")
    // Remover sufijos comunes de canales
    clean = clean.replace(Regex("\\s+(?:Oficial|Official|VEVO|Music|Videos|Audio|HD|4K|Latino|Realidad|Records|Entertainment|Productions|Studios)$", RegexOption.IGNORE_CASE), "")
    // Remover "TV" al final (BersuitTV, NickyJamTV, etc.)
    clean = clean.replace(Regex("TV$"), "")
    // Remover prefijos de canales concatenados: "elvecindariocalle13", "lamoscatsetsé"
    // IMPORTANTE: orden descendente (las→los→la→el) para que "las" no matchee como "la"+"s"
    clean = clean.replace(Regex("^(?:las|los|la|el)(?=[a-záéíóúñ])", RegexOption.IGNORE_CASE), "")
    // Fix truncado: "s Pastillas del Abuelo" → "Las Pastillas del Abuelo" (artista con "L" cortada)
    clean = clean.replace(Regex("^s\\s+"), "Las ")
    // Normalizar preposiciones a minúsculas: "De La Ghetto" → "De la Ghetto"
    clean = clean.replace(Regex("\\b(?:de|del|la|el|los|las|y|e|en|por|para|sin|con)\\b")) { match ->
        match.value.lowercase()
    }
    // Primera letra siempre mayúscula
    clean = clean.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    Log.d(TAG, "cleanChannelName: '$artist' → '$clean'")
    return clean.trim()
}

/**
 * Extrae el año de una fecha tipo "YYYY-MM-DD" o "YYYY" (primeros 4 dígitos).
 */
fun extractYear(date: String): String {
    return date.take(4).filter { it.isDigit() }
}

/**
 * Lee un campo string de un [JsonObject] de forma segura: devuelve `null` si el
 * campo no existe, es JSON null o no es un string. A diferencia de
 * `obj.get(key)?.asString`, NO lanza si el valor es `JsonNull` (en Gson
 * `JsonNull.getAsString()` lanza `UnsupportedOperationException`), protegiendo el
 * parseo de candidatos de los providers.
 */
fun jsonString(obj: JsonObject, key: String): String? {
    val element = obj.get(key) ?: return null
    return if (element.isJsonPrimitive && element.asJsonPrimitive.isString) {
        element.asString
    } else {
        null
    }
}

/**
 * Normaliza para comparación: lowercase, NFD (separa diacríticos) y solo
 * letras/dígitos. "José" y "jose" normalizan igual.
 *
 * Movido desde MetadataFetcher (T12) para que MusicRepository pueda usarlo
 * en la detección de metadata sospechosa sin duplicar lógica (DRY).
 */
fun normalizeForMatch(text: String): String {
    return java.text.Normalizer.normalize(text.lowercase(), java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{InCombiningDiacriticalMarks}"), "")
        .filter { it.isLetterOrDigit() }
}

private const val TAG = "MetadataCleaning"
