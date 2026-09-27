package io.github.howard20181.hyperos.fcmlive

import android.animation.ValueAnimator
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Outline
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.text.TextUtils
import android.text.TextPaint
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.AnimationSet
import android.view.animation.AnimationUtils
import android.view.animation.Interpolator
import android.view.animation.ScaleAnimation
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import io.github.howard20181.hyperos.fcmlive.theme.AppPalette
import io.github.howard20181.hyperos.fcmlive.theme.ThemeEngine
import io.github.howard20181.hyperos.fcmlive.theme.ThemeSupport
import kotlin.math.ceil
import kotlin.math.max

/**
 * In-window dropdown used by About pickers (theme mode, palette style, …).
 *
 * A plain view inside the Activity window rather than a PopupWindow: window
 * animations resolve the transform origin against the window, and the window
 * surface clips ease-out-back overshoot. As an in-window overlay the panel
 * scales from a real corner and has room to overshoot.
 */
object AboutDropdown {

    private var menuOverlay: ViewGroup? = null
    private var menuPanel: View? = null
    private var menuAbove = false
    private var menuRtl = false
    private var menuBackCallback: OnBackInvokedCallback? = null

    private const val MENU_CONTAINER_RADIUS_DP = 16
    private const val MENU_ITEM_RADIUS_DP = 12
    private const val MENU_OUTER_PAD_DP = 6
    private const val MENU_ITEM_GAP_DP = 10
    private const val MENU_ITEM_PAD_H_DP = 12
    private const val MENU_ITEM_HEIGHT_DP = 40
    private const val MENU_ITEM_TEXT_SP = 16
    private const val MENU_EXTRA_WIDTH_DP = 28
    private const val MENU_ANCHOR_GAP_DP = 4
    private const val MENU_EDGE_INSET_DP = 12
    private const val MENU_SCREEN_PAD_DP = 12
    private const val MENU_ELEVATION_DP = 3
    private const val POPUP_DISMISS_GRACE_MS = 64L
    private const val MENU_ENTER_FROM = 0.8f
    private const val MENU_ENTER_MS = 250L
    private const val MENU_EXIT_MS = 150L
    private const val MENU_TENSION_FULL = 1.70158f

    fun isOpen(): Boolean = menuOverlay != null

    fun show(
        activity: android.app.Activity,
        anchor: View,
        items: Array<String>,
        checked: Int,
        insetTop: Int,
        insetBottom: Int,
        onPick: (Int) -> Unit,
        onRebuild: () -> Unit,
    ) {
        dismiss(activity)
        if (items.isEmpty()) {
            return
        }
        val palette = ThemeEngine.palette(activity)
        val current = checked.coerceIn(0, items.size - 1)

        val measure = TextPaint()
        measure.textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            MENU_ITEM_TEXT_SP.toFloat(), activity.resources.displayMetrics
        )
        var widest = 0f
        for (item in items) {
            widest = max(widest, measure.measureText(item))
        }
        val panelW = ceil(widest.toDouble()).toInt() +
            activity.dp(MENU_ITEM_PAD_H_DP) * 2 + activity.dp(MENU_OUTER_PAD_DP) * 2 +
            activity.dp(MENU_EXTRA_WIDTH_DP)

        val rtl = activity.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
        val anchorLoc = IntArray(2)
        anchor.getLocationOnScreen(anchorLoc)
        val screenH = activity.resources.displayMetrics.heightPixels
        val outer = activity.dp(MENU_OUTER_PAD_DP)
        val rowH = activity.dp(MENU_ITEM_HEIGHT_DP)
        val itemGap = activity.dp(MENU_ITEM_GAP_DP)
        val backCurve = easeOutBack(MENU_TENSION_FULL)
        val naturalH = naturalHeight(rowH, itemGap, outer, items.size)
        val topLimit = insetTop + activity.dp(MENU_SCREEN_PAD_DP)
        val bottomLimit = screenH - insetBottom - activity.dp(MENU_SCREEN_PAD_DP)
        val panelH = minOf(naturalH, max(rowH, bottomLimit - topLimit))
        val below = anchorLoc[1] + anchor.height + activity.dp(MENU_ANCHOR_GAP_DP)
        val above = anchorLoc[1] - activity.dp(MENU_ANCHOR_GAP_DP) - panelH
        val panelTop = when {
            below + panelH <= bottomLimit -> below
            above >= topLimit -> above
            else -> below.coerceIn(topLimit, max(topLimit, bottomLimit - panelH))
        }
        val opensAbove = panelTop + panelH / 2 < anchorLoc[1] + anchor.height / 2

        val rebuildPending = booleanArrayOf(false)
        val rebuildIfPending = Runnable {
            if (rebuildPending[0]) {
                rebuildPending[0] = false
                onRebuild()
            }
        }

        val rows = LinearLayout(activity)
        rows.orientation = LinearLayout.VERTICAL
        for (i in items.indices) {
            val position = i
            rows.addView(
                buildMenuRow(
                    activity, items[i], i == current, i == 0, i == items.size - 1,
                    rowH, itemGap, palette,
                    Runnable {
                        onPick(position)
                        rebuildPending[0] = true
                        dismiss(activity)
                        anchor.postDelayed(
                            rebuildIfPending,
                            MENU_EXIT_MS + POPUP_DISMISS_GRACE_MS
                        )
                    })
            )
        }

        val scroll = ScrollView(activity)
        scroll.isVerticalScrollBarEnabled = false
        scroll.overScrollMode = View.OVER_SCROLL_NEVER
        scroll.addView(
            rows,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        val panel = FrameLayout(activity)
        panel.clipChildren = false
        panel.background = ThemeSupport.cardBackground(
            activity, palette.pageBg,
            MENU_CONTAINER_RADIUS_DP.toFloat()
        )
        panel.elevation = activity.dp(MENU_ELEVATION_DP).toFloat()
        panel.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(
                    0, 0, view.width, view.height,
                    activity.dp(MENU_CONTAINER_RADIUS_DP).toFloat()
                )
            }
        }
        panel.addView(
            scroll,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, panelH)
        )

        val decor = activity.window?.decorView
        val decorLoc = IntArray(2)
        decor?.getLocationOnScreen(decorLoc)
        val inset = activity.dp(MENU_EDGE_INSET_DP)
        val panelStart = if (rtl) {
            anchorLoc[0] + inset
        } else {
            anchorLoc[0] + anchor.width - panelW - inset
        }

        val panelLp = FrameLayout.LayoutParams(panelW, panelH)
        panelLp.leftMargin = panelStart - decorLoc[0]
        panelLp.topMargin = panelTop - decorLoc[1]

        val overlay = FrameLayout(activity)
        overlay.clipChildren = false
        overlay.isClickable = true
        overlay.setOnClickListener { dismiss(activity) }
        overlay.addView(panel, panelLp)

        val content = activity.findViewById<ViewGroup>(android.R.id.content)
        content.addView(
            overlay,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        menuOverlay = overlay
        menuPanel = panel
        menuAbove = opensAbove
        menuRtl = rtl
        val backCallback = OnBackInvokedCallback { dismiss(activity) }
        menuBackCallback = backCallback
        try {
            activity.onBackInvokedDispatcher.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT, backCallback
            )
        } catch (ignored: Throwable) {
        }
        playMenuEnter(panel, opensAbove, rtl, backCurve)
    }

    fun dismiss(activity: android.app.Activity) {
        val overlay = menuOverlay
        val panel = menuPanel
        val opensAbove = menuAbove
        val rtl = menuRtl
        menuOverlay = null
        menuPanel = null
        if (overlay == null) {
            return
        }
        menuBackCallback?.let { cb ->
            try {
                activity.onBackInvokedDispatcher.unregisterOnBackInvokedCallback(cb)
            } catch (ignored: Throwable) {
            }
        }
        menuBackCallback = null
        if (panel == null || !ValueAnimator.areAnimatorsEnabled()) {
            detachMenuOverlay(overlay)
            return
        }
        val out = AnimationSet(false)
        val shrink = ScaleAnimation(
            1f, MENU_ENTER_FROM, 1f, MENU_ENTER_FROM,
            Animation.RELATIVE_TO_SELF, if (rtl) 0f else 1f,
            Animation.RELATIVE_TO_SELF, if (opensAbove) 1f else 0f
        )
        shrink.duration = MENU_EXIT_MS
        shrink.interpolator = AnimationUtils.loadInterpolator(
            activity, R.interpolator.m3_emphasized_accelerate
        )
        val fade = AlphaAnimation(1f, 0f)
        fade.duration = MENU_EXIT_MS
        fade.interpolator = shrink.interpolator
        out.addAnimation(shrink)
        out.addAnimation(fade)
        out.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation) {}
            override fun onAnimationRepeat(animation: Animation) {}
            override fun onAnimationEnd(animation: Animation) {
                detachMenuOverlay(overlay)
            }
        })
        panel.startAnimation(out)
        panel.postDelayed({ detachMenuOverlay(overlay) }, MENU_EXIT_MS + POPUP_DISMISS_GRACE_MS)
    }

    private fun detachMenuOverlay(overlay: View?) {
        if (overlay == null || overlay.parent == null) {
            return
        }
        (overlay.parent as ViewGroup).removeView(overlay)
    }

    private fun playMenuEnter(
        panel: View, opensAbove: Boolean, rtl: Boolean, curve: Interpolator
    ) {
        if (!ValueAnimator.areAnimatorsEnabled()) {
            return
        }
        val set = AnimationSet(false)
        val grow = ScaleAnimation(
            MENU_ENTER_FROM, 1f, MENU_ENTER_FROM, 1f,
            Animation.RELATIVE_TO_SELF, if (rtl) 0f else 1f,
            Animation.RELATIVE_TO_SELF, if (opensAbove) 1f else 0f
        )
        grow.duration = MENU_ENTER_MS
        grow.interpolator = curve
        val fade = AlphaAnimation(0f, 1f)
        fade.duration = MENU_ENTER_MS
        fade.interpolator = curve
        set.addAnimation(grow)
        set.addAnimation(fade)
        panel.startAnimation(set)
    }

    private fun buildMenuRow(
        activity: android.app.Activity,
        text: String, selected: Boolean, first: Boolean, last: Boolean,
        rowH: Int, itemGap: Int, palette: AppPalette, onPick: Runnable
    ): View {
        val outer = activity.dp(MENU_OUTER_PAD_DP)
        val half = itemGap / 2
        val wrapper = FrameLayout(activity)
        wrapper.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        wrapper.setPadding(outer, if (first) outer else half, outer, if (last) outer else half)

        val row = LinearLayout(activity)
        row.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, rowH
        )
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.minimumHeight = rowH
        row.setPadding(activity.dp(MENU_ITEM_PAD_H_DP), 0, activity.dp(MENU_ITEM_PAD_H_DP), 0)

        val label = TextView(activity)
        label.setTextAppearance(R.style.TextAppearance_HyperFCMLive_BodyLarge)
        label.includeFontPadding = false
        label.setSingleLine(true)
        label.ellipsize = TextUtils.TruncateAt.END
        label.text = text
        label.setTextColor(palette.onSurface)
        row.addView(
            label,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        row.isClickable = true
        row.isFocusable = false
        row.background = menuItemBackground(activity, palette, selected)
        row.setOnClickListener { onPick.run() }
        wrapper.addView(row)
        return wrapper
    }

    private fun menuItemBackground(
        activity: android.app.Activity, palette: AppPalette, selected: Boolean
    ): Drawable {
        val radius = activity.dp(MENU_ITEM_RADIUS_DP).toFloat()
        val content = GradientDrawable()
        content.shape = GradientDrawable.RECTANGLE
        content.cornerRadius = radius
        content.setColor(if (selected) palette.primaryContainer else palette.pageBg)
        val mask = GradientDrawable()
        mask.shape = GradientDrawable.RECTANGLE
        mask.cornerRadius = radius
        mask.setColor(Color.WHITE)
        return RippleDrawable(ColorStateList.valueOf(palette.ripple), content, mask)
    }

    private fun naturalHeight(rowH: Int, itemGap: Int, outer: Int, count: Int): Int {
        if (count <= 0) {
            return outer * 2
        }
        return outer * 2 + count * rowH + max(0, count - 1) * itemGap
    }

    private fun easeOutBack(tension: Float): Interpolator {
        return Interpolator { t ->
            val u = t - 1f
            u * u * ((tension + 1f) * u + tension) + 1f
        }
    }

    private fun android.app.Activity.dp(value: Int): Int = UiUtils.dp(this, value)
}
