package io.github.howard20181.hyperos.fcmlive

import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import io.github.howard20181.hyperos.fcmlive.mcu.Hct
import io.github.howard20181.hyperos.fcmlive.theme.ThemeEngine
import io.github.howard20181.hyperos.fcmlive.theme.ThemeSupport
import java.io.InputStream
import java.nio.charset.StandardCharsets
import kotlin.math.max

/**
 * Open-source license list. Deps show version on the right; this project and
 * reference projects use name + license with a vertically centered link icon.
 */
class LicensesActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(ThemeSupport.attach(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeSupport.onCreate(this)
        setContentView(R.layout.activity_licenses)
        applySystemBarInsets()

        val back = findViewById<View>(R.id.btn_back)
        back?.setOnClickListener { finish() }

        val list = findViewById<LinearLayout>(R.id.licenses_list) ?: return
        // Was applied on every inset dispatch; once is enough — the bottom padding
        // itself still tracks the navigation mode.
        list.clipToPadding = false
        val inflater = LayoutInflater.from(this)

        addSectionHeader(list, inflater, getString(R.string.licenses_section_licenses))
        val licenseHint = getString(R.string.license_view_full_text)
        val licenseNames = arrayOf(
            getString(R.string.license_apache_2),
            getString(R.string.license_gpl_3),
            getString(R.string.license_mit),
        )
        val licenseRaw = intArrayOf(
            R.raw.license_apache2,
            R.raw.license_gpl3,
            R.raw.license_mit,
        )
        for (i in licenseNames.indices) {
            val row = inflater.inflate(R.layout.item_license_dep, list, false)
            bindRow(row, licenseNames[i], licenseHint)
            val licenseLink = row.findViewById<View>(R.id.dep_link)
            licenseLink?.visibility = View.GONE
            val title = licenseNames[i]
            val rawRes = licenseRaw[i]
            row.setOnClickListener { showLicenseDialog(title, rawRes) }
            addRow(list, row, i == 0, i == licenseNames.size - 1)
        }

        addSectionHeader(list, inflater, getString(R.string.licenses_section_deps))
        for (i in DEPS.indices) {
            val dep = DEPS[i]
            val row = inflater.inflate(R.layout.item_license_dep, list, false)
            bindRow(row, dep[0], dep[1], dep[2])
            val url = dep[3]
            val licenseRaw = licenseRawFor(dep[0], dep[2])
            row.setOnClickListener { showProjectLicenseDialog(dep[0], licenseRaw, url) }
            addRow(list, row, i == 0, i == DEPS.size - 1)
        }

        addSectionHeader(list, inflater, getString(R.string.licenses_section_refs))
        for (i in REFERENCES.indices) {
            val ref = REFERENCES[i]
            val row = inflater.inflate(R.layout.item_license_ref, list, false)
            bindRow(row, ref[0], ref[1])
            val url = ref[2]
            val licenseRaw = licenseRawFor(ref[0], ref[1])
            row.setOnClickListener { showProjectLicenseDialog(ref[0], licenseRaw, url) }
            addRow(list, row, i == 0, i == REFERENCES.size - 1)
        }
    }

    /** Two-line row (name + license) for this project / references. */
    private fun bindRow(row: View, name: String, license: String) {
        val nameView = row.findViewById<TextView>(R.id.dep_name)
        val licenseView = row.findViewById<TextView>(R.id.dep_license)
        nameView?.text = name
        licenseView?.text = license
    }

    /** Dep row: name + version on the right, license below. */
    private fun bindRow(row: View, name: String, version: String?, license: String) {
        bindRow(row, name, license)
        val versionView = row.findViewById<TextView>(R.id.dep_version)
        versionView?.text = version ?: ""
    }

    /** Section header styled exactly like the About page groups. */
    private fun addSectionHeader(list: LinearLayout, inflater: LayoutInflater, title: String) {
        val header = TextView(this)
        header.text = title
        // Same colour role as About's section titles (@color/md_primary).
        header.setTextColor(getColor(R.color.md_primary))
        header.setTextAppearance(R.style.TextAppearance_HyperFCMLive_BodyMedium)
        header.typeface = Typeface.create("sans-medium", Typeface.NORMAL)
        header.setPadding(dp(8), dp(28), dp(8), dp(12))
        list.addView(header)
    }

    /**
     * Adds one card to a section with the M3 connected-group look used on the
     * About page: 2dp gaps between cards, 16dp outer corners, 4dp inner
     * corners, flat (no elevation), and a ripple masked to the same shape.
     */
    private fun addRow(list: LinearLayout, row: View, first: Boolean, last: Boolean) {
        row.background = groupRowBackground(first, last)
        val lp = row.layoutParams as LinearLayout.LayoutParams
        lp.topMargin = if (first) 0 else dp(2)
        list.addView(row)
    }

    /** Position-aware rounded ripple matching the About page card groups. */
    private fun groupRowBackground(first: Boolean, last: Boolean): android.graphics.drawable.Drawable {
        val palette = ThemeEngine.palette(this)
        val top = if (first) dp(16).toFloat() else dp(4).toFloat()
        val bottom = if (last) dp(16).toFloat() else dp(4).toFloat()
        val radii = floatArrayOf(top, top, top, top, bottom, bottom, bottom, bottom)
        val content = GradientDrawable()
        content.shape = GradientDrawable.RECTANGLE
        content.cornerRadii = radii
        content.setColor(palette.card)
        val mask = GradientDrawable()
        mask.shape = GradientDrawable.RECTANGLE
        mask.cornerRadii = radii
        mask.setColor(Color.WHITE)
        return RippleDrawable(ColorStateList.valueOf(palette.ripple), content, mask)
    }

    private fun openUrl(url: String) {
        UiUtils.openUrl(this, url)
    }

    /**
     * Full license text in a Material dialog.
     *
     * Uses the standard message slot (not a hand-built setView hierarchy):
     * that is what crashed on click after the M3 dialog swap — custom
     * ScrollView + selectable TextView inside MaterialAlertDialogBuilder is
     * fragile — and it is also what gives official M3 corners and button
     * colours without painting them by hand.
     */
    private fun showLicenseDialog(title: String, rawRes: Int) {
        val text = readRawText(rawRes)
        try {
            ThemeSupport.withoutPalettePainting {
                val dialog = MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_HyperFCMLive_Dialog)
                    .setTitle(title)
                    .setMessage(text)
                    .setPositiveButton(R.string.dialog_close, null)
                    .show()
                styleLicenseDialog(dialog, sourceUrl = null)
            }
        } catch (t: Throwable) {
            // Never let a license viewer take the screen down.
            Toast.makeText(this, t.message ?: "dialog failed", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * License body for one dependency / reference project, with 「查看源代码」
     * opening its repository and 「关闭」 dismissing the dialog.
     */
    private fun showProjectLicenseDialog(name: String, rawRes: Int, url: String) {
        val text = readRawText(rawRes)
        try {
            ThemeSupport.withoutPalettePainting {
                val dialog = MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_HyperFCMLive_Dialog)
                    .setTitle(name)
                    .setMessage(text)
                    .setNegativeButton(R.string.view_source_code, null)
                    .setPositiveButton(R.string.dialog_close, null)
                    .show()
                styleLicenseDialog(dialog, sourceUrl = url)
            }
        } catch (t: Throwable) {
            Toast.makeText(this, t.message ?: "dialog failed", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Monospace license body plus the two action pills from the reference
     * shots: outlined 「查看源代码」 and filled 「关闭」 whose fill is the dark
     * tone of the current dynamic primary.
     */
    private fun styleLicenseDialog(dialog: AlertDialog, sourceUrl: String?) {
        dialog.findViewById<TextView>(android.R.id.message)?.typeface = Typeface.MONOSPACE

        val close = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        val fill = darkPrimaryFill()
        close.background = pill(fill)
        close.setTextColor(darkPrimaryOnFill())
        close.setPadding(dp(20), dp(10), dp(20), dp(10))

        val source = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
        if (sourceUrl == null) {
            source.visibility = View.GONE
            return
        }
        val palette = ThemeEngine.palette(this)
        source.background = pill(Color.TRANSPARENT, palette.outline)
        source.setTextColor(palette.onSurface)
        source.setPadding(dp(20), dp(10), dp(20), dp(10))
        source.setOnClickListener {
            dialog.dismiss()
            openUrl(sourceUrl)
        }
    }

    /** Dark tonal step (≈ tone 32) of the current system-extracted primary. */
    private fun darkPrimaryFill(): Int {
        val hct = Hct.fromInt(ThemeEngine.palette(this).primary)
        return Hct.from(hct.hue, max(hct.chroma, 24.0), 32.0).toInt()
    }

    private fun darkPrimaryOnFill(): Int {
        val hct = Hct.fromInt(darkPrimaryFill())
        return if (hct.tone < 50.0) Color.WHITE else 0xFF1C1B1F.toInt()
    }

    private fun pill(fill: Int, stroke: Int = Color.TRANSPARENT): GradientDrawable {
        val d = GradientDrawable()
        d.shape = GradientDrawable.RECTANGLE
        d.cornerRadius = dp(28).toFloat()
        d.setColor(fill)
        if (stroke != Color.TRANSPARENT) {
            d.setStroke(dp(1), stroke)
        }
        return d
    }

    private fun readRawText(rawRes: Int): String {
        return try {
            resources.openRawResource(rawRes).use { input: InputStream ->
                input.readBytes().toString(StandardCharsets.UTF_8)
            }
        } catch (t: Throwable) {
            ""
        }
    }

    /**
     * Maps a list row to the LICENSE body as published on that project's
     * GitHub repository. Shared SPDX texts (Apache/GPL/pure MIT) live in one
     * raw file each; the MIT reference projects ship their own copyright
     * line, so those keep a dedicated copy of their GitHub LICENSE.
     */
    private fun licenseRawFor(name: String, licenseLabel: String): Int = when {
        name == "Kr328/HyperOSFCMFix" -> R.raw.license_mit_hyperosfcmfix
        name == "ReedGAOOO/FCMGuard-HyperOS" -> R.raw.license_mit_fcmguard
        licenseLabel.contains("Apache") -> R.raw.license_apache2
        licenseLabel.contains("GPL") -> R.raw.license_gpl3
        licenseLabel.contains("MIT") -> R.raw.license_mit
        else -> R.raw.license_apache2
    }

    /** Same top-bar inset as MainActivity; list clears the gesture nav bar. */
    private fun applySystemBarInsets() {
        UiUtils.applyBarInsets(
            this, findViewById(R.id.top_bar),
            findViewById(R.id.licenses_list), 16
        )
    }

    private fun dp(value: Int): Int = UiUtils.dp(this, value)

    companion object {
        private const val REPO_URL = "https://github.com/iamqwert/HyperOS_FCM_Live"
        private const val ANDROIDX_URL = "https://github.com/androidx/androidx"
        private const val AOSP_URL = "https://android.googlesource.com/platform/frameworks/base"
        private const val JSPECIFY_URL = "https://github.com/jspecify/jspecify"
        private const val MCU_URL =
            "https://github.com/material-foundation/material-color-utilities"

        /** name, version ("" if none), license label, project URL. */
        private val DEPS = arrayOf(
            arrayOf("AndroidX Annotation", "1.10.0", "Apache License 2.0", ANDROIDX_URL),
            arrayOf("AndroidX AppCompat", "1.7.0", "Apache License 2.0", ANDROIDX_URL),
            arrayOf("AndroidX Arch Core", "2.0.0", "Apache License 2.0", ANDROIDX_URL),
            arrayOf("AndroidX Collection", "1.0.0", "Apache License 2.0", ANDROIDX_URL),
            arrayOf("AndroidX Compose Foundation", "1.10.0", "Apache License 2.0", ANDROIDX_URL),
            arrayOf("AndroidX Compose Material3", "1.4.0", "Apache License 2.0", ANDROIDX_URL),
            arrayOf("AndroidX Compose UI", "1.10.0", "Apache License 2.0", ANDROIDX_URL),
            arrayOf("AndroidX Core", "1.1.0", "Apache License 2.0", ANDROIDX_URL),
            arrayOf("AndroidX Interpolator", "1.0.0", "Apache License 2.0", ANDROIDX_URL),
            // Build-only stubs vendored under hiddenapi/stubs; kept for attribution.
            arrayOf("AOSP Framework Annotations", "", "Apache License 2.0", AOSP_URL),
            arrayOf(
                "JetBrains Annotations",
                "13.0",
                "Apache License 2.0",
                "https://github.com/JetBrains/java-annotations"
            ),
            arrayOf("JSpecify", "1.0.0", "Apache License 2.0", JSPECIFY_URL),
            arrayOf("Kotlin Stdlib", "2.2.10", "Apache License 2.0", "https://github.com/JetBrains/kotlin"),
            arrayOf("libxposed API", "102.0.0", "Apache License 2.0", "https://github.com/libxposed/api"),
            arrayOf("libxposed Interface", "102.0.0", "Apache License 2.0", "https://github.com/libxposed"),
            arrayOf("libxposed Service", "102.0.0", "Apache License 2.0", "https://github.com/libxposed/service"),
            arrayOf("Lifecycle Common", "2.0.0", "Apache License 2.0", ANDROIDX_URL),
            arrayOf("Lifecycle Runtime", "2.0.0", "Apache License 2.0", ANDROIDX_URL),
            arrayOf(
                "Material Components",
                "1.12.0",
                "Apache License 2.0",
                "https://github.com/material-components/material-components-android"
            ),
            // Vendored source under mcu/ (no Gradle artifact) — listed for attribution.
            arrayOf("Material Color Utilities", "", "Apache License 2.0", MCU_URL),
            arrayOf("SwipeRefreshLayout", "1.2.0", "Apache License 2.0", ANDROIDX_URL),
            arrayOf("VersionedParcelable", "1.1.0", "Apache License 2.0", ANDROIDX_URL),
        )

        /** name, license label, project URL. */
        private val REFERENCES = arrayOf(
            arrayOf("250king/HyperOS_FCM_Live", "GPL-3.0", "https://github.com/250king/HyperOS_FCM_Live"),
            arrayOf("billtv/HyperOS_FCM_Live", "GPL-3.0", "https://github.com/billtv/HyperOS_FCM_Live"),
            arrayOf("dingwen07/hyperos-fcm-fix", "GPL-3.0", "https://github.com/dingwen07/hyperos-fcm-fix"),
            arrayOf("HappyMax0/FCMPushViewer", "Apache License 2.0", "https://github.com/HappyMax0/FCMPushViewer"),
            arrayOf("Howard20181/HyperOS_FCM_Live", "GPL-3.0", "https://github.com/Howard20181/HyperOS_FCM_Live"),
            arrayOf("Kr328/HyperOSFCMFix", "MIT License", "https://github.com/Kr328/HyperOSFCMFix"),
            arrayOf("ReedGAOOO/FCMGuard-HyperOS", "MIT License", "https://github.com/ReedGAOOO/FCMGuard-HyperOS"),
            arrayOf("zuohl/HyperOS_FCM_Live", "GPL-3.0", "https://github.com/zuohl/HyperOS_FCM_Live"),
        )
    }
}
