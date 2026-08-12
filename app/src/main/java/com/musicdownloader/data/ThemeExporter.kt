package com.musicdownloader.data

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import com.musicdownloader.R

/**
 * Handles exporting and importing themes as JSON.
 */
object ThemeExporter {

    private const val MIMETYPE_THEME = "application/vnd.musicdownloader.theme+json"
    private const val SHARE_SUBJECT = "BeatOhm Theme"

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    /**
     * Serialize a UserTheme to a JSON string.
     */
    fun exportToJson(theme: UserTheme): String {
        val json = mapOf(
            "app" to "BeatOhm",
            "version" to 1,
            "theme" to mapOf(
                "name" to theme.name,
                "primaryColor" to theme.primaryColor,
                "secondaryColor" to theme.secondaryColor,
                "accentColor" to theme.accentColor,
                "backgroundColor" to theme.backgroundColor,
                "surfaceColor" to theme.surfaceColor,
                "textColor" to theme.textColor,
                "iconPackId" to theme.iconPackId,
                "playerLayoutId" to theme.playerLayoutId,
                "fontStyle" to theme.fontStyle
            )
        )
        return gson.toJson(json)
    }

    /**
     * Deserialize a JSON string into a UserTheme.
     * Returns null if the JSON is invalid.
     */
    fun importFromJson(json: String): UserTheme? {
        return try {
            val root = JsonParser.parseString(json).asJsonObject
            if (root.get("app")?.asString != "BeatOhm") return null

            val version = root.get("version")?.asInt ?: return null
            if (version > 1) return null // future version

            val theme = root.getAsJsonObject("theme") ?: return null

            UserTheme(
                id = 0, // will be assigned by Room
                name = theme.get("name")?.asString ?: "Imported Theme",
                primaryColor = theme.get("primaryColor")?.asInt ?: 0xFF6200EE.toInt(),
                secondaryColor = theme.get("secondaryColor")?.asInt ?: 0xFF03DAC5.toInt(),
                accentColor = theme.get("accentColor")?.asInt ?: 0xFFBB86FC.toInt(),
                backgroundColor = theme.get("backgroundColor")?.asInt ?: 0xFF121212.toInt(),
                surfaceColor = theme.get("surfaceColor")?.asInt ?: 0xFF1E1E1E.toInt(),
                textColor = theme.get("textColor")?.asInt ?: 0xFFFFFFFF.toInt(),
                iconPackId = theme.get("iconPackId")?.asString ?: "default",
                playerLayoutId = theme.get("playerLayoutId")?.asString ?: "classic",
                fontStyle = theme.get("fontStyle")?.asString ?: "default",
                isPreset = false,
                createdAt = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Validate a JSON string before importing.
     * Returns a pair of (isValid, themeName).
     */
    fun validateJson(json: String): Pair<Boolean, String> {
        val theme = importFromJson(json)
        return if (theme != null) Pair(true, theme.name) else Pair(false, "")
    }

    /**
     * Share a theme via Android share intent.
     */
    fun shareTheme(context: Context, theme: UserTheme) {
        val json = exportToJson(theme)
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "$SHARE_SUBJECT: ${theme.name}")
            putExtra(Intent.EXTRA_TEXT, json)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(sendIntent, SHARE_SUBJECT))
    }

    /**
     * Copy theme JSON to clipboard.
     */
    fun copyToClipboard(context: Context, theme: UserTheme): Boolean {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            ?: return false
        val json = exportToJson(theme)
        val clip = ClipData.newPlainText("theme", json)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, R.string.copied, Toast.LENGTH_SHORT).show()
        return true
    }

    /**
     * Read theme JSON from clipboard.
     * Returns null if clipboard is empty or doesn't contain valid theme JSON.
     */
    fun pasteFromClipboard(context: Context): UserTheme? {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            ?: return null
        val clip = clipboard.primaryClip ?: return null
        if (clip.itemCount == 0) return null
        val text = clip.getItemAt(0).text?.toString() ?: return null
        return importFromJson(text)
    }
}