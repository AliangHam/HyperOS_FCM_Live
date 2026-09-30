package io.github.howard20181.hyperos.fcmlive.theme

import android.content.res.ColorStateList
import com.google.android.material.materialswitch.MaterialSwitch

/**
 * Tint a [MaterialSwitch] from the runtime [AppPalette].
 *
 * The XML theme can only tint a switch with fixed colours, which stops working
 * the moment the palette is built at runtime: a custom seed or the AMOLED
 * variant changes primary / onPrimary / surfaceVariant, and a switch left on
 * the theme's own tint is the one control that then still wears the old
 * palette. Every screen with a switch therefore calls this after inflating.
 *
 * Only the checked state is themed with the palette; the unchecked state keeps
 * the neutral outline / surface-variant pair, which is what M3 specifies and
 * what keeps an off switch from reading as another accent-coloured control.
 */
fun MaterialSwitch.applyPalette(palette: AppPalette) {
    thumbTintList = ColorStateList(
        arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
        intArrayOf(palette.onPrimary, palette.outline)
    )
    trackTintList = ColorStateList(
        arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
        intArrayOf(palette.primary, palette.surfaceVariant)
    )
}
