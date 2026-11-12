package app.wayfinder.ui

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

/**
 * iiSU's fonts (Style › Font), read from the installed iiSU's own APK and cached in this app's cache — never copied into
 * this fork: their licences aren't stated. Cal Sans (iiSU's default) is the one this app ships (OFL).
 */
object IisuFonts {
    /** iiSU's launcher_font_choice → the family name inside its font files (Arctainium is spelt so in the font). */
    private val families = mapOf("console_sans" to "Console Sans", "arctanium" to "Arctainium")
    private val weights = listOf("Extrabold" to FontWeight.ExtraBold, "Semibold" to FontWeight.SemiBold,
        "Medium" to FontWeight.Medium, "Bold" to FontWeight.Bold)

    /** The family for iiSU's [choice], or null (Cal Sans, an unknown choice, iiSU not installed). Blocking: off the main thread. */
    fun load(ctx: android.content.Context, choice: String?): FontFamily? {
        val family = families[choice] ?: return null
        val apk = runCatching { ctx.packageManager.getApplicationInfo("com.iisulauncher", 0).sourceDir }.getOrNull() ?: return null
        val dir = java.io.File(ctx.cacheDir, "iisu_fonts").apply { mkdirs() }
        val fonts = mutableListOf<Font>()
        runCatching {
            java.util.zip.ZipFile(apk).use { z ->
                for (e in z.entries()) {
                    if (!(e.name.endsWith(".otf") || e.name.endsWith(".ttf")) || e.size > 2_000_000) continue
                    val bytes = z.getInputStream(e).use { it.readBytes() }
                    val name = familyName(bytes)?.takeIf { it.startsWith(family) } ?: continue
                    val f = java.io.File(dir, name.replace(' ', '_') + "." + e.name.substringAfterLast('.'))
                    if (f.length() != bytes.size.toLong()) f.writeBytes(bytes)
                    fonts += Font(f, weights.firstOrNull { name.endsWith(it.first) }?.second ?: FontWeight.Normal)
                }
            }
        }
        return fonts.takeIf { it.isNotEmpty() }?.let { FontFamily(it) }
    }

    /** A TrueType / OpenType font's family name (its name table, id 1), or null. */
    fun familyName(b: ByteArray): String? = runCatching {
        fun u16(o: Int) = (b[o].toInt() and 0xFF shl 8) or (b[o + 1].toInt() and 0xFF)
        fun u32(o: Int) = (u16(o).toLong() shl 16) or u16(o + 2).toLong()
        val tables = u16(4)
        val name = (0 until tables).map { 12 + 16 * it }.first { String(b, it, 4, Charsets.ISO_8859_1) == "name" }
        val off = u32(name + 8).toInt()
        val count = u16(off + 2); val strings = off + u16(off + 4)
        (0 until count).map { off + 6 + 12 * it }.firstNotNullOfOrNull { r ->
            if (u16(r + 6) != 1) return@firstNotNullOfOrNull null
            val platform = u16(r); val len = u16(r + 8); val at = strings + u16(r + 10)
            if (platform == 0 || platform == 3) String(b, at, len, Charsets.UTF_16BE) else String(b, at, len, Charsets.ISO_8859_1)
        }
    }.getOrNull()
}
