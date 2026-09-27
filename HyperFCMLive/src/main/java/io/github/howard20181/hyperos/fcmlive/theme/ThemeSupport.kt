package io.github.howard20181.hyperos.fcmlive.theme

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.WindowInsetsController
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.DynamicColorsOptions

/**
 * Hooks the runtime palette into an Activity:
 * - [attach] forces light/dark when the user overrides the system
 *   mode, by rewriting the night flag of the base configuration;
 * - [onCreate] installs the inflation-time painter and repaints the
 *   window chrome.
 */
object ThemeSupport {

    /** Call from `Activity.attachBaseContext`. */
    @JvmStatic
    fun attach(base: Context): Context {
        val mode = ThemePrefs.themeMode(base)
        val locale = ThemePrefs.locale(base)
        if (mode == ThemePrefs.MODE_SYSTEM && locale == null) {
            return base
        }
        val config = Configuration(base.resources.configuration)
        if (mode != ThemePrefs.MODE_SYSTEM) {
            config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                if (mode == ThemePrefs.MODE_DARK) {
                    Configuration.UI_MODE_NIGHT_YES
                } else {
                    Configuration.UI_MODE_NIGHT_NO
                }
        }
        if (locale != null) {
            // Same rewrite, second axis: the chosen locale decides which values-*
            // folder resolves, so the in-app language switch costs no extra
            // machinery — a recreate re-runs attach() and re-inflates everything.
            config.setLocale(locale)
        }
        return base.createConfigurationContext(config)
    }

    /**
     * Official Material You dynamic color.
     * - Dynamic on: wallpaper accent (DynamicColors / Monet).
     * - Dynamic off with a custom seed: content-based source from that seed.
     * ThemeEngine / AppPalette still drive hand-tuned layouts and ThemeFactory.
     */
    private fun applyDynamicColors(activity: Activity) {
        try {
            if (ThemePrefs.dynamicColor(activity)) {
                val systemSeed = systemAccentSeed(activity)
                if (systemSeed != 0 && !isNearGrey(systemSeed)) {
                    DynamicColors.applyToActivityIfAvailable(activity)
                } else {
                    // HyperOS can expose a near-grey accent after overnight
                    // palette refresh. Values-v31 md_* roles alias system_accent*,
                    // so the main / licenses screens collapse to monochrome.
                    // Re-seed from ThemeEngine (Tonal spot etc. still apply
                    // chroma) so those roles stay visibly colored.
                    val seed = colorfulSeed(activity, systemSeed)
                    val options = DynamicColorsOptions.Builder()
                        .setContentBasedSource(seed)
                        .build()
                    DynamicColors.applyToActivityIfAvailable(activity, options)
                }
            } else {
                val seed = ThemePrefs.seedColor(activity)
                if (seed != 0) {
                    val options = DynamicColorsOptions.Builder()
                        .setContentBasedSource(seed)
                        .build()
                    DynamicColors.applyToActivityIfAvailable(activity, options)
                }
            }
        } catch (ignored: Throwable) {
            // ROM without DynamicColors support: static theme colors remain.
        }
    }

    /** Prefer the in-app seed; fall back to the brand rose when the system hue is empty. */
    private fun colorfulSeed(activity: Activity, systemSeed: Int): Int {
        val themed = ThemeEngine.palette(activity).primary
        if (!isNearGrey(themed)) {
            return themed
        }
        if (systemSeed != 0 && !isNearGrey(systemSeed)) {
            return systemSeed
        }
        return 0xFF8B4A5A.toInt()
    }

    private fun systemAccentSeed(context: Context): Int {
        return try {
            @Suppress("DEPRECATION")
            context.resources.getColor(android.R.color.system_accent1_500)
        } catch (ignored: Throwable) {
            0
        }
    }

    /** True when the color's chroma is too low to carry a Material accent hue. */
    private fun isNearGrey(color: Int): Boolean {
        if (color == 0) {
            return true
        }
        val r = ((color shr 16) and 0xFF) / 255f
        val g = ((color shr 8) and 0xFF) / 255f
        val b = (color and 0xFF) / 255f
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        return (max - min) < 0.04f
    }

    /** Call before `setContentView`. */
    @JvmStatic
    fun onCreate(activity: Activity) {
        applyDynamicColors(activity)
        val palette = ThemeEngine.palette(activity)
        installFactory(activity, palette)
        applyWindow(activity, palette)
        installCardPainter(activity, palette)
    }

    /**
     * Inflate [block] without [ThemeFactory] stand-ins. MaterialAlertDialog
     * (and AppCompat dialog chrome) blow up with "Binary XML file line #…"
     * when our factory feeds it framework replacements.
     */
    @JvmStatic
    fun <T> withoutPalettePainting(block: () -> T): T {
        val previous = ThemeFactory.paused
        ThemeFactory.paused = true
        try {
            return block()
        } finally {
            ThemeFactory.paused = previous
        }
    }

    /**
     * setContentView runs after this method, so card painting is deferred to
     * the first layout pass. Covers MaterialCardViews the inflater built
     * without going through [ThemeFactory].
     */
    private fun installCardPainter(activity: Activity, palette: AppPalette) {
        try {
            val decor = activity.window?.decorView ?: return
            decor.viewTreeObserver.addOnPreDrawListener(
                object : android.view.ViewTreeObserver.OnPreDrawListener {
                    private var done = false
                    override fun onPreDraw(): Boolean {
                        if (!done) {
                            done = true
                            decor.viewTreeObserver.removeOnPreDrawListener(this)
                            ThemeFactory.paintCards(decor, palette)
                        }
                        return true
                    }
                }
            )
        } catch (ignored: Throwable) {
            // Theming is best effort.
        }
    }

    /**
     * Install the palette painter on the Activity's LayoutInflater.
     *
     * AppCompat has already called setFactory2 by the time onCreate runs, so the
     * public setter throws. That used to be swallowed, which left XML layouts on
     * the raw system accent (often monochrome after overnight HyperOS palette
     * refresh) and ignored the in-app palette style. Replace mFactory2 via
     * reflection, chaining the previous factory so AppCompat widgets stay intact.
     */
    private fun installFactory(activity: Activity, palette: AppPalette) {
        val inflater = activity.layoutInflater
        try {
            inflater.setFactory2(ThemeFactory(activity, inflater, palette))
            return
        } catch (ignored: Throwable) {
            // Factory already installed (AppCompat) — force-replace below.
        }
        try {
            val factory2Field = LayoutInflater::class.java.getDeclaredField("mFactory2")
            val factoryField = LayoutInflater::class.java.getDeclaredField("mFactory")
            factory2Field.isAccessible = true
            factoryField.isAccessible = true
            val previous = factory2Field.get(inflater) as? LayoutInflater.Factory2
            val factory = ThemeFactory(activity, inflater, palette, previous)
            factory2Field.set(inflater, factory)
            factoryField.set(inflater, factory)
        } catch (ignored: Throwable) {
            // Hidden-API block or exotic LayoutInflater: layout falls back to
            // static resources / system DynamicColors.
        }
    }

    private fun applyWindow(activity: Activity, palette: AppPalette) {
        val window = activity.window ?: return
        val surface = palette.pageBg
        // The window background is what shows through the system-bar areas, so
        // it has to be the page colour: Android 15 (API 35) forces edge-to-edge
        // and draws both bars transparent, which makes this drawable the only
        // thing covering the status bar and the gesture/home-indicator strip.
        window.setBackgroundDrawable(ColorDrawable(surface))
        // The per-bar colour setters are gone: they are deprecated and ignored once
        // the app targets API 35 (this project's minSdk), which the platform draws
        // edge-to-edge with transparent bars — setBackgroundDrawable above is what
        // actually shows through them. The contrast/divider calls stay: they are not
        // deprecated and disabling the scrim is what keeps the home-indicator strip
        // from showing as a detached grey band.
        @Suppress("DEPRECATION")
        window.isStatusBarContrastEnforced = false
        window.isNavigationBarContrastEnforced = false
        @Suppress("DEPRECATION")
        window.navigationBarDividerColor = Color.TRANSPARENT

        // The decor view has to be installed before the insets controller can
        // be reached. This runs from onCreate, before setContentView, so
        // PhoneWindow.mDecorView is still null — and getInsetsController()
        // dereferences it unconditionally, which crashed the app on launch.
        // getDecorView() creates the decor on demand, which is exactly what
        // this call is for; the null check is kept for exotic Window impls.
        val decor = window.decorView ?: return

        // Bar icon appearance follows the *runtime* palette, not the system
        // setting, because the user can force light/dark inside the app.
        val controller = window.insetsController
        if (controller != null) {
            val appearance = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            controller.setSystemBarsAppearance(if (palette.dark) 0 else appearance, appearance)
        }
    }

    /** Solid rounded rectangle using the current palette (for code-built rows). */
    @JvmStatic
    fun cardBackground(context: Context, color: Int, radiusDp: Float): Drawable {
        return ThemeFactory.roundRect(context, color, radiusDp)
    }
}
