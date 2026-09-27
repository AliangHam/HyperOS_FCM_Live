package io.github.howard20181.hyperos.fcmlive.theme

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.google.android.material.card.MaterialCardView
import io.github.howard20181.hyperos.fcmlive.R

/**
 * Applies the runtime [AppPalette] to views as they are inflated.
 *
 * Android resolves `@color/` and `@drawable/` references inside
 * XML natively, so a color resource cannot be swapped at runtime. Instead we
 * read the raw resource ids from the [AttributeSet] — before resolution —
 * and repaint the freshly created view. Inflation itself is untouched: any tag
 * we cannot build simply falls through to the platform's own path.
 *
 * When an AppCompat factory is already installed, [ThemeSupport] chains this
 * one behind it so widget compatibility is preserved and only the repaint runs.
 */
internal class ThemeFactory(
    @Suppress("UNUSED_PARAMETER") context: Context,
    private val inflater: LayoutInflater,
    private val palette: AppPalette,
    private val delegate: LayoutInflater.Factory2? = null
) : LayoutInflater.Factory2 {

    private val colorRoles: MutableMap<Int, Int> = HashMap()
    private val drawableRoles: MutableMap<Int, Int> = HashMap()

    init {
        buildRoleMaps()
    }

    private fun buildRoleMaps() {
        val p = palette
        colorRoles[R.color.md_primary] = p.primary
        colorRoles[R.color.md_primary_container] = p.primaryContainer
        colorRoles[R.color.md_surface] = p.surface
        colorRoles[R.color.md_page_bg] = p.pageBg
        colorRoles[R.color.md_on_surface] = p.onSurface
        colorRoles[R.color.md_on_surface_variant] = p.onSurfaceVariant
        colorRoles[R.color.md_hint_light] = p.hint
        colorRoles[R.color.md_outline] = p.outline
        colorRoles[R.color.md_card] = p.card
        colorRoles[R.color.md_icon_tint] = p.iconTint
        colorRoles[R.color.md_tooltip_bg] = p.tooltipBg
        colorRoles[R.color.md_tooltip_text] = p.tooltipText
        colorRoles[R.color.md_popup_menu_bg] = p.popupBg
        colorRoles[R.color.md_popup_item_ripple] = p.ripple

        putDrawable(R.drawable.bg_card, p.card)
        putDrawable(R.drawable.bg_card_group, p.card)
        putDrawable(R.drawable.bg_card_ripple, p.card)
        putDrawable(R.drawable.bg_card_single_ripple, p.card)
        putDrawable(R.drawable.bg_card_group_top_ripple, p.card)
        putDrawable(R.drawable.bg_card_group_middle_ripple, p.card)
        putDrawable(R.drawable.bg_card_group_bottom_ripple, p.card)
        putDrawable(R.drawable.bg_card_selected, p.primaryContainer)
        putDrawable(R.drawable.bg_fab, p.primaryContainer)
        putDrawable(R.drawable.bg_popup_menu, p.popupBg)
        putDrawable(R.drawable.bg_tooltip, p.tooltipBg)
        putDrawable(R.drawable.md3_check_on, p.primary)
    }

    private fun putDrawable(resId: Int, color: Int) {
        drawableRoles[resId] = color
    }

    override fun onCreateView(name: String, context: Context, attrs: AttributeSet): View? {
        return onCreateView(null, name, context, attrs)
    }

    override fun onCreateView(
        parent: View?,
        name: String,
        context: Context,
        attrs: AttributeSet
    ): View? {
        // MaterialAlertDialog / AppCompat dialog chrome cannot survive our
        // stand-in views (Binary XML inflation). Callers pause this factory
        // around dialog show(); during that window the platform inflates alone.
        if (paused) {
            return null
        }
        // Prefer the existing (AppCompat) factory so platform widgets keep
        // their compatibility wrappers; we only repaint afterwards.
        val delegated = try {
            delegate?.onCreateView(parent, name, context, attrs)
        } catch (ignored: Throwable) {
            null
        }
        if (delegated != null) {
            try {
                bind(delegated, attrs)
            } catch (ignored: Throwable) {
                // Theming is best effort; a broken binding must never break inflation.
            }
            return delegated
        }
        // No delegate (or it declined the tag).
        // - Fully-qualified androidx.* is dialog/AppCompat chrome: never invent
        //   a stand-in (license-dialog Binary XML crash).
        // - Button: dialog actions need AppCompat/Material only.
        // - Material (card / FAB) can be built and painted.
        // - Short names are only the simple widgets our screens use; anything
        //   else falls through to the platform inflater.
        val view = when {
            paused -> return null
            name.startsWith("androidx.") -> return null
            name.contains('.') && !name.startsWith(MATERIAL_PREFIX) -> return null
            name.startsWith(MATERIAL_PREFIX) -> createFullClassView(name, context, attrs)
            name == "Button" -> return null
            name in SAFE_SHORT_NAMES -> createView(name, context, attrs)
            else -> return null
        } ?: return null
        try {
            bind(view, attrs)
        } catch (ignored: Throwable) {
            // Theming is best effort; a broken binding must never break inflation.
        }
        return view
    }

    private fun createFullClassView(name: String, context: Context, attrs: AttributeSet): View? {
        return try {
            val clazz = Class.forName(name, false, context.classLoader)
                .asSubclass(View::class.java)
            val ctor = clazz.getConstructor(Context::class.java, AttributeSet::class.java)
            ctor.newInstance(context, attrs)
        } catch (ignored: Throwable) {
            null
        }
    }

    private fun createView(name: String, context: Context, attrs: AttributeSet): View? {
        for (prefix in PREFIXES) {
            try {
                val created = inflater.createView(name, prefix, attrs)
                if (created != null) {
                    return created
                }
            } catch (ignored: Throwable) {
                // Try the next prefix.
            }
        }
        return null
    }

    private fun bind(view: View, attrs: AttributeSet) {
        val background = attrs.getAttributeResourceValue(NS, "background", 0)
        if (background != 0) {
            val solid = colorRoles[background]
            if (solid != null) {
                view.setBackgroundColor(solid)
            } else {
                val tint = drawableRoles[background]
                if (tint != null) {
                    recolor(view.background, tint)
                }
            }
        }

        val backgroundTint = colorRoles[
            attrs.getAttributeResourceValue(NS, "backgroundTint", 0)
        ] ?: colorRoles[
            attrs.getAttributeResourceValue(NS_APP, "backgroundTint", 0)
        ]
        if (backgroundTint != null) {
            view.backgroundTintList = ColorStateList.valueOf(backgroundTint)
        }

        if (view is MaterialCardView) {
            val cardBg = colorRoles[
                attrs.getAttributeResourceValue(NS_APP, "cardBackgroundColor", 0)
            ]
            if (cardBg != null) {
                view.setCardBackgroundColor(cardBg)
            } else {
                view.setCardBackgroundColor(palette.card)
            }
            view.rippleColor = ColorStateList.valueOf(palette.ripple)
        }

        if (view is TextView) {
            val textColor = colorRoles[attrs.getAttributeResourceValue(NS, "textColor", 0)]
            if (textColor != null) {
                view.setTextColor(textColor)
            }
            val hintColor = colorRoles[attrs.getAttributeResourceValue(NS, "textColorHint", 0)]
            if (hintColor != null) {
                view.setHintTextColor(hintColor)
            }
        }

        if (view is ImageView) {
            val tint = colorRoles[attrs.getAttributeResourceValue(NS, "tint", 0)]
                ?: colorRoles[attrs.getAttributeResourceValue(NS_APP, "tint", 0)]
            if (tint != null) {
                view.imageTintList = ColorStateList.valueOf(tint)
            }
            val src = drawableRoles[attrs.getAttributeResourceValue(NS, "src", 0)]
            if (src != null) {
                recolor(view.drawable, src)
            }
        }
    }

    /** Repaint every solid shape inside a drawable without touching its geometry. */
    private fun recolor(drawable: Drawable?, color: Int) {
        if (drawable == null) {
            return
        }
        drawable.mutate()
        if (drawable is GradientDrawable) {
            drawable.setColor(color)
            return
        }
        if (drawable is ColorDrawable) {
            drawable.color = color
            return
        }
        if (drawable is LayerDrawable) {
            for (i in 0 until drawable.numberOfLayers) {
                recolor(drawable.getDrawable(i), color)
            }
        }
    }

    companion object {
        private const val NS = "http://schemas.android.com/apk/res/android"
        private const val NS_APP = "http://schemas.android.com/apk/res-auto"
        private const val MATERIAL_PREFIX = "com.google.android.material."
        private val PREFIXES = arrayOf(
            "android.widget.", "android.view.", "android.webkit.", "android.app.",
        )

        /** Only these short tags may be built when AppCompat declines them. */
        private val SAFE_SHORT_NAMES = setOf(
            "View", "FrameLayout", "LinearLayout", "RelativeLayout",
            "TextView", "ImageView", "ImageButton", "ImageView",
            "ScrollView", "HorizontalScrollView", "Space",
            "ListView", "SearchView", "ProgressBar",
        )

        /**
         * When true, every tag falls through to the platform inflater.
         * Used around MaterialAlertDialog inflation.
         */
        @Volatile
        @JvmStatic
        var paused: Boolean = false

        /** Solid rounded rectangle, used for card backgrounds built in code. */
        @JvmStatic
        fun roundRect(context: Context, color: Int, radiusDp: Float): Drawable {
            val shape = GradientDrawable()
            shape.setColor(color)
            shape.cornerRadius = radiusDp * context.resources.displayMetrics.density
            return shape
        }

        /**
         * Paint any [MaterialCardView] under [root] that the inflater already
         * built (AppCompat path). Covers list rows even when we did not create
         * the card ourselves.
         */
        @JvmStatic
        fun paintCards(root: View?, palette: AppPalette) {
            if (root == null) {
                return
            }
            if (root is MaterialCardView) {
                try {
                    root.setCardBackgroundColor(palette.card)
                    root.rippleColor = ColorStateList.valueOf(palette.ripple)
                } catch (ignored: Throwable) {
                    // Best effort.
                }
            }
            if (root is ViewGroup) {
                for (i in 0 until root.childCount) {
                    paintCards(root.getChildAt(i), palette)
                }
            }
        }
    }
}
