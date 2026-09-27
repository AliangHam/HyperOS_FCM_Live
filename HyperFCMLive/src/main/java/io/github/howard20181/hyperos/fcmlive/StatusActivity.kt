package io.github.howard20181.hyperos.fcmlive

import androidx.appcompat.app.AppCompatActivity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Toast
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import io.github.howard20181.hyperos.fcmlive.theme.HyperFCMLiveTheme
import io.github.howard20181.hyperos.fcmlive.theme.ThemeEngine
import io.github.howard20181.hyperos.fcmlive.theme.ThemeSupport
import io.github.howard20181.hyperos.fcmlive.ui.StatusHookGroup
import io.github.howard20181.hyperos.fcmlive.ui.StatusHookRow
import io.github.howard20181.hyperos.fcmlive.ui.StatusKeyValue
import io.github.howard20181.hyperos.fcmlive.ui.StatusScreen
import io.github.howard20181.hyperos.fcmlive.ui.StatusUi
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Whether the module can actually do anything on this ROM.
 *
 * The question this screen answers is not "is the module enabled" — it is
 * "does this ROM still have the methods my hooks are written against". That is
 * the usual reason a module looks fine and does nothing: the ROM moved on, the
 * target method is gone, and nothing anywhere says so.
 *
 * The body is Compose ([StatusScreen]); the top bar stays a View so window
 * insets and the back button keep working like every other screen.
 */
class StatusActivity : AppCompatActivity() {

    private var composeView: ComposeView? = null
    private val probeExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(ThemeSupport.attach(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeSupport.onCreate(this)
        setContentView(R.layout.activity_status)
        applySystemBarInsets()

        val back = findViewById<View>(R.id.btn_back)
        back?.setOnClickListener { finish() }

        composeView = findViewById(R.id.status_content)
        composeView?.setBackgroundColor(ThemeEngine.palette(this).pageBg)
        render(StatusUi(networkRows(), getString(R.string.status_probing), emptyList(), diagnostics()))
        probeHooks()
    }

    override fun onDestroy() {
        probeExecutor.shutdownNow()
        super.onDestroy()
    }

    private fun applySystemBarInsets() {
        // Fully immersive content: only the top bar is pushed below the status
        // bar. Navigation inset is scroll content inside StatusScreen.
        UiUtils.applyBarInsets(this, findViewById(R.id.top_bar), null, 0)
    }

    private fun render(ui: StatusUi) {
        val host = composeView ?: return
        try {
            host.setContent {
                HyperFCMLiveTheme {
                    StatusScreen(ui = ui, onCopyCommand = ::copyToClipboard)
                }
            }
        } catch (t: Throwable) {
            // Compose runtime missing / R8 strip: avoid taking the screen down.
        }
    }

    private fun networkRows(): List<StatusKeyValue> = listOf(
        StatusKeyValue(getString(R.string.status_gms), describeGms()),
        StatusKeyValue(getString(R.string.status_network_active), describeNetwork()),
        StatusKeyValue(getString(R.string.status_strict_mode), describeStrictMode()),
    )

    private fun diagnostics(): List<String> = listOf(DIAG_LOG, DIAG_GCM)

    private fun describeStrictMode(): String {
        return getString(
            if (Prefs.readLocalStrictMode(this)) R.string.status_yes else R.string.status_no
        )
    }

    private fun describeGms(): String {
        return try {
            val pi = packageManager.getPackageInfo(GMS_PACKAGE, 0)
            pi.versionName ?: getString(R.string.status_unknown)
        } catch (t: Throwable) {
            getString(R.string.status_gms_absent)
        }
    }

    private fun describeNetwork(): String {
        return try {
            val cm = getSystemService(android.net.ConnectivityManager::class.java)
                ?: return getString(R.string.status_unknown)
            val network = cm.activeNetwork ?: return getString(R.string.status_network_none)
            val caps = cm.getNetworkCapabilities(network)
                ?: return getString(R.string.status_unknown)
            when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ->
                    getString(R.string.status_network_wifi)
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ->
                    getString(R.string.status_network_cellular)
                else -> getString(R.string.status_network_other)
            }
        } catch (t: Throwable) {
            getString(R.string.status_unknown)
        }
    }

    private fun probeHooks() {
        val appContext = applicationContext
        probeExecutor.execute {
            val items = HookStatus.probe(appContext)
            mainHandler.post {
                render(
                    StatusUi(
                        network = networkRows(),
                        hooksSummary = hooksSummary(items),
                        hookGroups = hookGroups(items),
                        diagnostics = diagnostics(),
                    )
                )
            }
        }
    }

    private fun hooksSummary(items: List<HookStatus.Item>): String {
        var present = 0
        var unexpected = 0
        var unknown = 0
        for (item in items) {
            if (item.state == HookStatus.State.PRESENT) present++
            if (item.isUnexpectedAbsence()) unexpected++
            if (item.state == HookStatus.State.UNKNOWN) unknown++
        }
        return when {
            items.isEmpty() || unknown == items.size ->
                getString(R.string.status_hooks_unreachable)
            unexpected == 0 ->
                getString(R.string.status_hooks_ok, present, items.size)
            else ->
                getString(R.string.status_hooks_missing, unexpected, present, items.size)
        }
    }

    private fun hookGroups(items: List<HookStatus.Item>): List<StatusHookGroup> {
        if (items.isEmpty()) return emptyList()
        val groups = ArrayList<StatusHookGroup>()
        var title = items[0].side.labelRes
        var rows = ArrayList<StatusHookRow>()
        for (item in items) {
            if (item.side.labelRes != title) {
                groups.add(StatusHookGroup(getString(title), rows))
                title = item.side.labelRes
                rows = ArrayList()
            }
            rows.add(
                StatusHookRow(
                    name = item.target,
                    note = noteFor(item),
                    stateText = stateText(item),
                    stateColor = stateColor(item),
                )
            )
        }
        groups.add(StatusHookGroup(getString(title), rows))
        return groups
    }

    private fun stateText(item: HookStatus.Item): String {
        return when (item.state) {
            HookStatus.State.PRESENT -> getString(R.string.status_state_present)
            HookStatus.State.ABSENT -> getString(
                if (item.isUnexpectedAbsence()) {
                    R.string.status_state_absent_unexpected
                } else {
                    R.string.status_state_absent_expected
                }
            )
            else -> getString(R.string.status_state_unknown)
        }
    }

    private fun stateColor(item: HookStatus.Item): Color {
        val palette = ThemeEngine.palette(this)
        val primary = Color(palette.primary)
        if (item.state == HookStatus.State.PRESENT) return primary
        if (item.state == HookStatus.State.ABSENT) {
            // Expected absence ("不适用") is a ROM difference, not a fault:
            // same quiet tone as "无法检测", never the accent.
            return if (item.isUnexpectedAbsence()) {
                primary
            } else {
                Color(palette.hint)
            }
        }
        return Color(palette.hint)
    }

    private fun noteFor(item: HookStatus.Item): String? {
        return when (item.expect) {
            HookStatus.Expect.HYPEROS_3 -> getString(R.string.status_note_hyperos3)
            HookStatus.Expect.HYPEROS_4 -> getString(R.string.status_note_hyperos4)
            HookStatus.Expect.NONE -> getString(R.string.status_note_never)
            HookStatus.Expect.OPTIONAL -> getString(R.string.status_note_optional)
            else -> null
        }
    }

    private fun copyToClipboard(text: String) {
        try {
            val cm = getSystemService(ClipboardManager::class.java)
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText("command", text))
                Toast.makeText(this, R.string.status_copied, Toast.LENGTH_SHORT).show()
                return
            }
        } catch (ignored: Throwable) {
            // Fall through to the toast fallback below.
        }
        Toast.makeText(this, text, Toast.LENGTH_LONG).show()
    }

    companion object {
        private const val DIAG_LOG =
            "adb logcat -d -b all | grep -i LSPosedLogDaemon"
        private const val DIAG_GCM =
            "adb shell dumpsys activity service com.google.android.gms/.gcm.GcmService"
        private const val GMS_PACKAGE = "com.google.android.gms"
    }
}
