package io.github.howard20181.hyperos.fcmlive

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import android.widget.TextView
import io.github.howard20181.hyperos.fcmlive.theme.ThemeEngine
import io.github.howard20181.hyperos.fcmlive.theme.ThemeSupport

/**
 * Long-press tooltip that never covers the anchor icon.
 *
 * HyperOS (and some AOSP builds) place the framework bubble right on top of
 * the control, so [View.tooltipText] is suppressed and a custom MD-style
 * popup with an explicit gap is shown instead. Screens own one instance and
 * call [dismiss] from `onDestroy`, which is what keeps a bubble from outliving
 * its window.
 */
class TooltipHost(private val activity: Activity) {

    private var active: PopupWindow? = null
    private val dismissRunnable = Runnable { dismiss() }

    /** Binds [textRes] as both the accessibility label and the long-press bubble. */
    fun attach(view: View?, textRes: Int) {
        if (view == null) {
            return
        }
        val tip: CharSequence = activity.getText(textRes)
        view.contentDescription = tip
        // Suppress framework / HyperOS bubbles that sit on the icon.
        view.tooltipText = null
        view.isLongClickable = true
        view.setOnLongClickListener { v ->
            show(v, tip)
            true
        }
    }

    fun dismiss() {
        val popup = active
        active = null
        if (popup != null) {
            try {
                popup.contentView?.removeCallbacks(dismissRunnable)
                popup.dismiss()
            } catch (ignored: Throwable) {
            }
        }
    }

    /** Show a short bubble below the anchor, flipping above when it would clip. */
    private fun show(anchor: View, text: CharSequence) {
        dismiss()
        if (activity.isFinishing || text.isEmpty()) {
            return
        }

        // HyperOS may re-surface contentDescription as a covering bubble on
        // long-press. Hide it while our offset tooltip is visible, restore for
        // accessibility after dismiss.
        val restoredCd: CharSequence = anchor.contentDescription ?: text
        anchor.contentDescription = null

        val dp = { value: Int -> UiUtils.dp(activity, value) }
        val tipView = TextView(activity)
        tipView.text = text
        A11yUtils.markTooltip(tipView, text)
        val palette = ThemeEngine.palette(activity)
        tipView.setTextColor(palette.tooltipText)
        tipView.setTextAppearance(R.style.TextAppearance_HyperFCMLive_BodySmall)
        tipView.gravity = Gravity.CENTER
        tipView.background = ThemeSupport.cardBackground(activity, palette.tooltipBg, 4f)
        val padH = dp(12)
        val padV = dp(6)
        tipView.setPadding(padH, padV, padH, padV)
        tipView.setSingleLine(true)
        tipView.includeFontPadding = false

        val screenW = activity.resources.displayMetrics.widthPixels
        val maxTextW = maxOf(dp(64), minOf(dp(240), screenW - dp(48)))
        tipView.maxWidth = maxTextW
        tipView.measure(
            View.MeasureSpec.makeMeasureSpec(maxTextW, View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(dp(64), View.MeasureSpec.AT_MOST)
        )
        val tipW = maxOf(tipView.measuredWidth, padH * 2 + dp(24))
        val tipH = maxOf(tipView.measuredHeight, dp(28))

        val popup = PopupWindow(
            tipView, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        popup.width = tipW
        popup.height = tipH
        popup.isOutsideTouchable = true
        popup.isFocusable = false
        popup.isTouchable = true
        try {
            popup.elevation = dp(6).toFloat()
        } catch (ignored: Throwable) {
        }
        // Transparent so the rounded shape is not clipped by a default frame.
        popup.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        popup.setOnDismissListener {
            tipView.removeCallbacks(dismissRunnable)
            if (active === popup) {
                active = null
            }
            try {
                anchor.contentDescription = restoredCd
            } catch (ignored: Throwable) {
            }
        }

        val loc = IntArray(2)
        anchor.getLocationInWindow(loc)
        val gap = dp(8)
        val edge = dp(8)
        // Freeform / split-screen: clamp against the window, not the display.
        val windowW = activity.window.decorView.width
        val windowH = activity.window.decorView.height
        val screenH = activity.resources.displayMetrics.heightPixels

        var x = loc[0] + (anchor.width - tipW) / 2
        if (x < edge) {
            x = edge
        }
        if (windowW > 0 && x + tipW > windowW - edge) {
            x = maxOf(edge, windowW - edge - tipW)
        }

        // Prefer a clear gap under the icon; flip above when tight (toolbar
        // icons near the status bar).
        val yBelow = loc[1] + anchor.height + gap
        val yAbove = loc[1] - gap - tipH
        val roomBelow = (if (windowH > 0) windowH else screenH) - edge - (loc[1] + anchor.height)
        val roomAbove = loc[1] - edge
        val y = when {
            roomBelow >= tipH + gap -> yBelow
            roomAbove >= tipH + gap -> yAbove
            roomBelow >= roomAbove -> minOf(yBelow, (if (windowH > 0) windowH else screenH) - edge - tipH)
            else -> maxOf(yAbove, edge)
        }.coerceAtLeast(edge)

        try {
            popup.showAtLocation(anchor, Gravity.NO_GRAVITY, x, y)
            active = popup
            tipView.postDelayed(dismissRunnable, 2200)
        } catch (ignored: Throwable) {
        }
    }
}
