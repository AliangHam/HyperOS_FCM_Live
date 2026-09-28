package io.github.howard20181.hyperos.fcmlive

import androidx.appcompat.app.AppCompatActivity
import android.content.Context
import android.os.Bundle
import android.view.View
import io.github.howard20181.hyperos.fcmlive.theme.ThemeSupport

/**
 * Privacy & permissions page, opened from the About card between
 * 「检查更新」 and 「查看源代码」. Body is Compose (PrivacyScreen); the top
 * bar stays a View so insets and the back button match Help/About.
 */
class PrivacyActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(ThemeSupport.attach(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeSupport.onCreate(this)
        setContentView(R.layout.activity_privacy)
        applySystemBarInsets()

        val back = findViewById<View>(R.id.btn_back)
        back?.setOnClickListener { finish() }

        val composeView =
            findViewById<androidx.compose.ui.platform.ComposeView>(R.id.privacy_content)
        composeView?.let {
            it.setBackgroundColor(
                io.github.howard20181.hyperos.fcmlive.theme.ThemeEngine.palette(this).pageBg
            )
            try {
                it.setContent {
                    io.github.howard20181.hyperos.fcmlive.theme.HyperFCMLiveTheme {
                        io.github.howard20181.hyperos.fcmlive.ui.PrivacyScreen()
                    }
                }
            } catch (t: Throwable) {
                // Compose runtime missing / R8 strip: keep the page alive.
                it.visibility = View.GONE
            }
        }
    }

    /**
     * Same safe-area handling as Help: status bar pads the top bar only;
     * PrivacyScreen adds navigation-bar padding as scroll content.
     */
    private fun applySystemBarInsets() {
        UiUtils.applyBarInsets(
            this, findViewById(R.id.top_bar),
            null, 0
        )
    }
}
