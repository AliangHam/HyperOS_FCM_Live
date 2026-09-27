package io.github.howard20181.hyperos.fcmlive

import androidx.appcompat.app.AppCompatActivity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.github.howard20181.hyperos.fcmlive.mcu.Scheme
import io.github.howard20181.hyperos.fcmlive.theme.HyperFCMLiveTheme
import io.github.howard20181.hyperos.fcmlive.theme.ThemeEngine
import io.github.howard20181.hyperos.fcmlive.theme.ThemePrefs
import io.github.howard20181.hyperos.fcmlive.theme.ThemeSupport
import io.github.howard20181.hyperos.fcmlive.ui.AboutScreen
import io.github.howard20181.hyperos.fcmlive.ui.AboutUi
import java.io.OutputStream
import java.nio.charset.StandardCharsets

/**
 * About: source, licenses, allowlist backup, update check with red badge.
 *
 * The body is Compose ([AboutScreen]); the top bar stays a View. All
 * pickers / dialogs / file APIs remain here — Compose only renders and
 * reports taps.
 */
class AboutActivity : AppCompatActivity() {

    private var ui by mutableStateOf(
        AboutUi(
            hideIconChecked = false,
            hideIconState = "",
            languageValue = "",
            themeModeValue = "",
            dynamicColorChecked = true,
            dynamicColorState = "",
            paletteStyleValue = "",
            colorSpecValue = "",
            versionValue = "",
            updateBadge = false,
        )
    )

    private var lastEggAtMs = 0L

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(ThemeSupport.attach(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeSupport.onCreate(this)
        setContentView(R.layout.activity_about)
        applySystemBarInsets()

        findViewById<View>(R.id.btn_back)?.setOnClickListener { finish() }
        findViewById<View>(R.id.btn_help)?.setOnClickListener {
            startActivity(Intent(this, HelpActivity::class.java))
        }

        val composeView = findViewById<ComposeView>(R.id.about_content)
        composeView?.setBackgroundColor(ThemeEngine.palette(this).pageBg)
        composeView?.setContent {
            HyperFCMLiveTheme {
                AboutScreen(
                    ui = ui,
                    onToggleHideIcon = { checked ->
                        applyLauncherIcon(hidden = checked)
                        refreshUi()
                    },
                    onLanguageClick = {
                        showPicker(
                            R.string.language,
                            R.array.language_entries,
                            effectiveLanguage(),
                        ) { index -> ThemePrefs.setLanguage(this, index); applyAppearanceChange() }
                    },
                    onThemeModeClick = {
                        showPicker(
                            R.string.theme_mode,
                            R.array.theme_mode_entries,
                            ThemePrefs.themeMode(this),
                        ) { index -> ThemePrefs.setThemeMode(this, index); applyAppearanceChange() }
                    },
                    onToggleDynamicColor = { checked ->
                        ThemePrefs.setDynamicColor(this, checked)
                        applyAppearanceChange()
                    },
                    onPaletteStyleClick = {
                        showPicker(
                            R.string.palette_style,
                            R.array.palette_style_entries,
                            ThemePrefs.paletteStyle(this).ordinal,
                        ) { index ->
                            ThemePrefs.setPaletteStyle(this, variantAt(index))
                            applyAppearanceChange()
                        }
                    },
                    onColorSpecClick = {
                        showPicker(
                            R.string.color_spec,
                            R.array.color_spec_entries,
                            ThemePrefs.specVersion(this),
                        ) { index -> ThemePrefs.setSpecVersion(this, index); applyAppearanceChange() }
                    },
                    onVersionClick = { onVersionRowTapped() },
                    onCheckUpdateClick = { checkForUpdates() },
                    onExportClick = { exportAllowlist() },
                    onImportClick = { importAllowlist() },
                    onSourceClick = { openUrl(REPO_URL) },
                    onLicensesClick = {
                        startActivity(Intent(this, LicensesActivity::class.java))
                    },
                )
            }
        }
        refreshUi()
    }

    private fun applySystemBarInsets() {
        UiUtils.applyBarInsets(this, findViewById(R.id.top_bar), null, 0)
    }

    private fun refreshUi() {
        val modes = resources.getStringArray(R.array.theme_mode_entries)
        val styles = resources.getStringArray(R.array.palette_style_entries)
        val specs = resources.getStringArray(R.array.color_spec_entries)
        val languages = resources.getStringArray(R.array.language_entries)
        val langIndex = effectiveLanguage()
        ui = ui.copy(
            hideIconChecked = LauncherIcon.isHidden(this),
            hideIconState = getString(
                if (LauncherIcon.isHidden(this)) {
                    R.string.about_sub_hide_icon_on
                } else {
                    R.string.about_sub_hide_icon_off
                }
            ),
            languageValue = languages.getOrElse(langIndex) { "" },
            themeModeValue = modes.getOrElse(ThemePrefs.themeMode(this)) { "" },
            dynamicColorChecked = ThemePrefs.dynamicColor(this),
            dynamicColorState = getString(
                if (ThemePrefs.dynamicColor(this)) {
                    R.string.about_sub_dynamic_color_on
                } else {
                    R.string.about_sub_dynamic_color_off
                }
            ),
            paletteStyleValue = styles.getOrElse(ThemePrefs.paletteStyle(this).ordinal) { "" },
            colorSpecValue = specs.getOrElse(ThemePrefs.specVersion(this)) { "" },
            versionValue = moduleVersion(),
            updateBadge = UpdateChecker.isUpdateAvailable(this),
        )
    }

    private fun showPicker(titleRes: Int, entriesRes: Int, current: Int, onPick: (Int) -> Unit) {
        val entries = resources.getStringArray(entriesRes)
        ThemeSupport.withoutPalettePainting {
            MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_HyperFCMLive_Dialog)
                .setTitle(titleRes)
                .setSingleChoiceItems(entries, current) { dialog, which ->
                    dialog.dismiss()
                    onPick(which)
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    private fun applyAppearanceChange() {
        ThemeEngine.invalidate()
        recreate()
    }

    private fun applyLauncherIcon(hidden: Boolean) {
        LauncherIcon.setHidden(this, hidden)
        Toast.makeText(
            this,
            if (hidden) R.string.hide_icon_toast else R.string.show_icon_toast,
            Toast.LENGTH_LONG
        ).show()
    }

    private fun effectiveLanguage(): Int {
        val pinned = ThemePrefs.language(this)
        if (pinned >= 0) {
            return pinned
        }
        val current = resources.configuration.locales[0]
        return if (current.language == "zh") 0 else 1
    }

    private fun onVersionRowTapped() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastEggAtMs < EGG_COOLDOWN_MS) {
            return
        }
        lastEggAtMs = now
    }

    private fun checkForUpdates() {
        toastShort(R.string.update_checking)
        UpdateChecker.checkAsync(this, object : UpdateChecker.Callback {
            override fun onResult(
                updateAvailable: Boolean,
                latestVersion: String,
                downloadUrl: String
            ) {
                runOnUiThread {
                    if (isFinishing || isDestroyed) {
                        return@runOnUiThread
                    }
                    refreshUi()
                    if (!updateAvailable) {
                        Toast.makeText(
                            this@AboutActivity,
                            R.string.update_none, Toast.LENGTH_SHORT
                        ).show()
                        return@runOnUiThread
                    }
                    ThemeSupport.withoutPalettePainting {
                        MaterialAlertDialogBuilder(this@AboutActivity)
                            .setMessage(getString(R.string.update_found, latestVersion))
                            .setPositiveButton(R.string.update_open) { _, _ ->
                                UpdateChecker.clearBadge(this@AboutActivity)
                                refreshUi()
                                openUrl(downloadUrl)
                            }
                            .setNegativeButton(android.R.string.cancel, null)
                            .show()
                    }
                }
            }

            override fun onError() {
                runOnUiThread {
                    if (isFinishing || isDestroyed) {
                        return@runOnUiThread
                    }
                    Toast.makeText(
                        this@AboutActivity,
                        R.string.update_error, Toast.LENGTH_SHORT
                    ).show()
                }
            }
        })
    }

    private fun openUrl(url: String) {
        UiUtils.openUrl(this, url)
    }

    private fun toastShort(resId: Int) {
        Toast.makeText(this, resId, Toast.LENGTH_SHORT).show()
    }

    // ---- allowlist backup ----------------------------------------------

    private fun exportAllowlist() {
        val allow = currentAllowlist()
        if (allow.isEmpty()) {
            toastShort(R.string.allowlist_export_failed)
            return
        }
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/plain"
            putExtra(Intent.EXTRA_TITLE, "fcmlive-allowlist.txt")
        }
        try {
            startActivityForResult(intent, REQ_EXPORT)
        } catch (t: Throwable) {
            toastShort(R.string.allowlist_export_failed)
        }
    }

    private fun importAllowlist() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/plain"
        }
        try {
            startActivityForResult(intent, REQ_IMPORT)
        } catch (t: Throwable) {
            toastShort(R.string.allowlist_import_failed)
        }
    }

    private fun currentAllowlist(): Set<String> {
        val allow = Prefs.readAllowlist(Prefs.remote())
        return if (allow.isEmpty()) Prefs.readLocalAllowlist(this) else allow
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) {
            return
        }
        val uri = data?.data ?: return
        when (requestCode) {
            REQ_EXPORT -> writeAllowlistTo(uri)
            REQ_IMPORT -> readAllowlistFrom(uri)
        }
    }

    private fun writeAllowlistTo(uri: Uri) {
        val sorted = currentAllowlist().toTypedArray().sorted()
        if (sorted.isEmpty()) {
            toastShort(R.string.allowlist_export_failed)
            return
        }
        try {
            val out: OutputStream? = contentResolver.openOutputStream(uri)
            if (out == null) {
                throw IllegalStateException("null stream")
            }
            out.use { stream ->
                val sb = StringBuilder()
                for (pkg in sorted) {
                    sb.append(pkg).append('\n')
                }
                stream.write(sb.toString().toByteArray(StandardCharsets.UTF_8))
                Toast.makeText(
                    this, getString(R.string.allowlist_export_done, sorted.size),
                    Toast.LENGTH_SHORT
                ).show()
            }
        } catch (t: Throwable) {
            toastShort(R.string.allowlist_export_failed)
        }
    }

    private fun readAllowlistFrom(uri: Uri) {
        val allow = HashSet<String>()
        try {
            contentResolver.openInputStream(uri)?.use { input ->
                input.bufferedReader().useLines { lines ->
                    for (line in lines) {
                        val pkg = line.trim()
                        if (pkg.isNotEmpty()) {
                            allow.add(pkg)
                        }
                    }
                }
            } ?: throw IllegalStateException("null stream")
        } catch (t: Throwable) {
            toastShort(R.string.allowlist_import_failed)
            return
        }
        if (allow.isEmpty()) {
            toastShort(R.string.allowlist_import_empty)
            return
        }
        Prefs.writeAllowlist(this, Prefs.remote(), allow)
        Toast.makeText(
            this, getString(R.string.allowlist_import_done, allow.size),
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun moduleVersion(): String {
        return try {
            val pi = packageManager.getPackageInfo(packageName, 0)
            "${pi.versionName} (${pi.longVersionCode})"
        } catch (t: Throwable) {
            getString(R.string.status_unknown)
        }
    }

    private fun variantAt(index: Int): Scheme.Variant {
        val values = Scheme.Variant.values()
        return if (index >= 0 && index < values.size) values[index] else Scheme.Variant.TONAL_SPOT
    }

    companion object {
        private const val REPO_URL = "https://github.com/iamqwert/HyperOS_FCM_Live"
        private const val REQ_EXPORT = 41
        private const val REQ_IMPORT = 42
        private const val EGG_COOLDOWN_MS = 800L
    }
}
