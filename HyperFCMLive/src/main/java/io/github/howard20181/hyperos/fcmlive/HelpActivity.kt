package io.github.howard20181.hyperos.fcmlive

import androidx.appcompat.app.AppCompatActivity
import android.content.Context
import android.os.Bundle
import android.view.View
import io.github.howard20181.hyperos.fcmlive.theme.ThemeSupport

/**
 * The help page, opened from the top bar of the About screen.
 *
 * It is static text on purpose: everything it claims is behaviour that lives
 * in `Hooker` — the allowlist semantics (an empty list lets every app
 * through, a non-empty one only the listed apps), what a wake actually does to
 * the target app, what strict mode does to an app left unchecked
 * (`Hooker#shouldApply`), and the rules around enabling the module.
 * Keep this text in sync when those hooks change.
 */
class HelpActivity : AppCompatActivity() {

    private val tips = TooltipHost(this)

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(ThemeSupport.attach(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeSupport.onCreate(this)
        setContentView(R.layout.activity_help)
        applySystemBarInsets()

        val back = findViewById<View>(R.id.btn_back)
        back?.setOnClickListener { finish() }
        tips.attach(back, R.string.back)

        val composeView = findViewById<androidx.compose.ui.platform.ComposeView>(R.id.help_content)
        composeView?.let {
            it.setBackgroundColor(io.github.howard20181.hyperos.fcmlive.theme.ThemeEngine.palette(this).pageBg)
            try {
                it.setContent {
                    io.github.howard20181.hyperos.fcmlive.theme.HyperFCMLiveTheme {
                        io.github.howard20181.hyperos.fcmlive.ui.HelpScreen()
                    }
                }
            } catch (t: Throwable) {
                // Compose runtime missing / R8 strip: keep the page alive.
                it.visibility = View.GONE
            }
        }
    }

    /**
     * The same safe-area handling as the About page: the status bar inset pads
     * the top bar, and the bottom inset pads the scrollable content so the last
     * card clears the home indicator while the page colour still reaches the
     * bottom edge of the screen.
     */
    private fun applySystemBarInsets() {
        // Content is fully immersive: no bottom inset padding on the Compose
        // host (that band looks like a floating strip while scrolling). Only
        // the top bar is pushed below the status bar; HelpScreen adds
        // navigation-bar padding as scroll content so the last card can clear
        // the gesture line after the user reaches the end.
        UiUtils.applyBarInsets(
            this, findViewById(R.id.top_bar),
            null, 0
        )
    }

    override fun onDestroy() {
        tips.dismiss()
        super.onDestroy()
    }
}
