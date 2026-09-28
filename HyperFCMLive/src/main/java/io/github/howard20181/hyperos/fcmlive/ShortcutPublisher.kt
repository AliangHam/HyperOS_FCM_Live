package io.github.howard20181.hyperos.fcmlive

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Icon
import android.os.Build
import io.github.howard20181.hyperos.fcmlive.mcu.Hct
import io.github.howard20181.hyperos.fcmlive.theme.ThemeEngine
import kotlin.math.roundToInt

/**
 * Push launcher shortcuts from code so icon/label updates reach the
 * launcher without relying only on static res/xml/shortcuts.xml caches.
 *
 * The icons are drawn at publish time instead of shipping fixed PNGs: the
 * tile background is derived from the current Material You accent, so it
 * follows the wallpaper on its own. Material 3 has no mechanism that lets
 * the launcher re-tint a shortcut icon (themed icons only ever apply to a
 * monochrome app icon), so the app has to supply a finished bitmap.
 */
object ShortcutPublisher {

    private const val ID_SETTINGS = "settings"
    private const val ID_HELP = "help"
    private const val ID_FCM = "fcm_diagnostics"

    /** Tile edge in dp. The launcher shows shortcuts well below this, so it
     *  stays crisp after downscaling. */
    private const val TILE_DP = 64
    /** Corner radius and glyph box, as a fraction of the tile (they match the
     *  geometry of the PNG tiles these bitmaps replaced). */
    private const val RADIUS_RATIO = 40f / 192f
    /**
     * HCT tone of the tile. Fixed rather than inherited: a white glyph needs a
     * dark enough plate to stay legible, and a wallpaper-derived accent can
     * land anywhere from near-black to near-white. Tone 40 keeps the white
     * glyph above 6:1 contrast for every hue.
     */
    private const val TILE_TONE = 40.0
    /** Chroma window: caps garish accents, and lifts greyscale wallpapers
     *  (chroma ~0) to a tile that still reads as a coloured plate. */
    private const val MIN_CHROMA = 16.0
    private const val MAX_CHROMA = 48.0

    @JvmStatic
    fun publish(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) return
        try {
            val sm = context.getSystemService(ShortcutManager::class.java) ?: return
            val settings = ShortcutInfo.Builder(context, ID_SETTINGS)
                .setShortLabel(context.getString(R.string.shortcut_settings))
                .setLongLabel(context.getString(R.string.shortcut_settings_long))
                .setIcon(
                    tileIcon(
                        context,
                        R.drawable.ic_shortcut_glyph_settings,
                        R.drawable.ic_shortcut_settings
                    )
                )
                .setIntent(
                    Intent(Intent.ACTION_VIEW).setClassName(
                        context.packageName,
                        "io.github.howard20181.hyperos.fcmlive.AboutActivity"
                    )
                )
                .build()
            val help = ShortcutInfo.Builder(context, ID_HELP)
                .setShortLabel(context.getString(R.string.help))
                .setLongLabel(context.getString(R.string.shortcut_help_long))
                .setIcon(
                    tileIcon(
                        context,
                        R.drawable.ic_shortcut_glyph_help,
                        R.drawable.ic_shortcut_help
                    )
                )
                .setIntent(
                    Intent(Intent.ACTION_VIEW).setClassName(
                        context.packageName,
                        "io.github.howard20181.hyperos.fcmlive.HelpActivity"
                    )
                )
                .build()
            val fcm = ShortcutInfo.Builder(context, ID_FCM)
                .setShortLabel(context.getString(R.string.fcm_diagnostics))
                .setLongLabel(context.getString(R.string.shortcut_fcm_diagnostics_long))
                .setIcon(
                    tileIcon(
                        context,
                        R.drawable.ic_shortcut_glyph_fcm,
                        R.drawable.ic_shortcut_fcm
                    )
                )
                .setIntent(
                    Intent(MainActivity.ACTION_FCM_DIAGNOSTICS).setClassName(
                        context.packageName,
                        "io.github.howard20181.hyperos.fcmlive.MainActivity"
                    )
                )
                .build()
            sm.dynamicShortcuts = listOf(settings, help, fcm)
        } catch (_: Throwable) {
        }
    }

    /**
     * Draws one shortcut tile: a rounded plate in the current accent with the
     * white glyph on top.
     *
     * The glyph is a 192x192 vector whose artwork already sits in a centred
     * 112x112 box, so it is drawn across the whole tile — no extra inset.
     *
     * Falls back to the shipped PNG if anything about the runtime draw fails.
     */
    private fun tileIcon(context: Context, glyphRes: Int, fallbackRes: Int): Icon {
        return try {
            val plate = tileColor(context)
            val size = (TILE_DP * context.resources.displayMetrics.density)
                .roundToInt().coerceAtLeast(48)
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            // Fill first with the plate colour at zero alpha: the corners stay
            // transparent, but keep the plate RGB, so a launcher that discards
            // the alpha channel shows the accent instead of black.
            bitmap.eraseColor(plate and 0x00FFFFFF)
            val canvas = Canvas(bitmap)
            val radius = size * RADIUS_RATIO
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = plate }
            canvas.drawRoundRect(RectF(0f, 0f, size.toFloat(), size.toFloat()), radius, radius, paint)
            context.getDrawable(glyphRes)?.let { glyph ->
                glyph.setBounds(0, 0, size, size)
                glyph.draw(canvas)
            }
            Icon.createWithBitmap(bitmap)
        } catch (_: Throwable) {
            Icon.createWithResource(context, fallbackRes)
        }
    }

    /**
     * Tile background: the accent's hue, with its chroma clamped and its tone
     * pinned so the white glyph always has contrast. The seed comes from
     * [ThemeEngine], which reads the system's Material You accent — i.e. the
     * wallpaper — whenever dynamic color is on (the default).
     */
    private fun tileColor(context: Context): Int {
        return try {
            val hct = Hct.fromInt(ThemeEngine.palette(context).primary)
            Hct.from(hct.hue, hct.chroma.coerceIn(MIN_CHROMA, MAX_CHROMA), TILE_TONE).toInt()
        } catch (_: Throwable) {
            0xFF8B4A5A.toInt()
        }
    }
}
