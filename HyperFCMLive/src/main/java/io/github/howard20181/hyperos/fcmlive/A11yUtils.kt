package io.github.howard20181.hyperos.fcmlive

import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.ImageView
import android.widget.TextView

/**
 * TalkBack / accessibility helpers for surfaces that are not official
 * Material menus (custom PopupWindow tooltips and overflow).
 *
 * Goal: one spoken node per control, explicit checked state, and an
 * announcement when a tooltip or menu appears.
 */
object A11yUtils {

    /**
     * Android 16 throws IllegalStateException ("Accessibility off") when an
     * event is sent while the service is disabled. Always gate on the manager.
     */
    @JvmStatic
    private fun a11yEnabled(view: View?): Boolean {
        val context = view?.context ?: return false
        val manager = context.getSystemService(AccessibilityManager::class.java) ?: return false
        return manager.isEnabled
    }

    /** Announce a transient message (tooltip opened, menu opened, etc.). */
    @JvmStatic
    @Suppress("DEPRECATION")
    fun announce(view: View?, text: CharSequence?) {
        if (view == null || text.isNullOrEmpty() || !a11yEnabled(view)) {
            return
        }
        try {
            view.announceForAccessibility(text)
            val event = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_ANNOUNCEMENT)
            event.text.add(text)
            event.className = view.javaClass.name
            event.packageName = view.context?.packageName
            view.parent?.requestSendAccessibilityEvent(view, event) ?: view.sendAccessibilityEvent(
                AccessibilityEvent.TYPE_ANNOUNCEMENT
            )
        } catch (ignored: Throwable) {
            // Accessibility must never take the UI down (Android 16 strictness).
        }
    }

    /**
     * Tooltip bubble: readable as one node, and announced when shown.
     * The anchor keeps the real control description.
     */
    @JvmStatic
    fun markTooltip(bubble: View?, text: CharSequence?) {
        if (bubble == null || text == null) {
            return
        }
        bubble.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        bubble.contentDescription = text
        bubble.isFocusable = true
        bubble.accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        announce(bubble, text)
    }

    /**
     * One menu row = one TalkBack stop: label + checked state.
     * Child label/check images are hidden from the a11y tree so they are not
     * read twice.
     */
    @JvmStatic
    fun applyMenuRow(
        row: View?,
        label: CharSequence?,
        checked: Boolean,
        roleDescription: CharSequence = "菜单项"
    ) {
        if (row == null) {
            return
        }
        val state = if (checked) "已选中" else "未选中"
        val name = if (label.isNullOrEmpty()) "" else "$label，"
        row.contentDescription = "$name$state"
        row.isFocusable = true
        row.isClickable = true
        row.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        row.accessibilityDelegate = object : View.AccessibilityDelegate() {
            @Suppress("DEPRECATION")
            override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.className = "android.widget.CheckBox"
                info.isCheckable = true
                info.isChecked = checked
                info.isEnabled = host.isEnabled
                info.stateDescription = if (checked) "已选中" else "未选中"
            }
        }
        if (row is android.view.ViewGroup) {
            hideChildrenFromA11y(row)
        }
    }

    private fun hideChildrenFromA11y(group: android.view.ViewGroup) {
        for (i in 0 until group.childCount) {
            val child = group.getChildAt(i)
            child.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }
    }

    /** Popup / dialog pane title so TalkBack announces what opened. */
    @JvmStatic
    fun setPaneTitle(view: View?, title: CharSequence?) {
        if (view == null || title == null) {
            return
        }
        view.accessibilityPaneTitle = title
        view.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    /** Fire a window-state event (menu opened) for TalkBack focus movement. */
    @JvmStatic
    fun announceWindowOpened(view: View?, title: CharSequence?) {
        if (view == null || !a11yEnabled(view)) {
            return
        }
        try {
            setPaneTitle(view, title)
            announce(view, title)
            view.sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)
        } catch (ignored: Throwable) {
        }
    }

    /** Keep check ImageViews decorative when the parent row carries state. */
    @JvmStatic
    fun markDecorative(image: ImageView?) {
        image?.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        image?.contentDescription = null
    }

    /** Force a TextView into the a11y tree (labels used as only text). */
    @JvmStatic
    fun markLabel(tv: TextView?) {
        tv?.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
    }
}
