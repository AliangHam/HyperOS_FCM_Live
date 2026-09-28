package io.github.howard20181.hyperos.fcmlive.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import io.github.howard20181.hyperos.fcmlive.mcu.Scheme

/**
 * Jetpack Compose theme bridge.
 *
 * Colors are **not** taken from Compose's own dynamic-color helpers. They are
 * mapped from [ThemeEngine]/[AppPalette] so the in-app palette style
 * (Tonal spot, Monochrome, …) and color spec stay identical to the View layer
 * driven by [ThemeFactory].
 */
@Composable
fun HyperFCMLiveTheme(content: @Composable () -> Unit) {
    // isSystemInDarkTheme is only a fallback when no palette is applied yet;
    // ThemeEngine.palette already resolves the user's theme mode.
    val palette = androidx.compose.ui.platform.LocalContext.current.let { context ->
        remember(context) { ThemeEngine.palette(context) }
    }
    val colorScheme = remember(palette) { palette.toComposeColorScheme() }
    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}

/** Map the runtime [AppPalette] (full [Scheme]) onto Compose Material 3. */
fun AppPalette.toComposeColorScheme(): ColorScheme {
    val s = scheme
    val surface = Color(pageBg)
    val onSurface = Color(this.onSurface)
    // `background` carries pageBg, whose dark tone is derived from
    // surfaceContainerLowest — mapping that role verbatim would paint the
    // cards the same colour as the page. Cards take the `card` alias instead,
    // which always stays one step above the page in both modes.
    val cardColor = Color(card)
    return if (dark) {
        darkColorScheme(
            primary = Color(primary),
            onPrimary = Color(onPrimary),
            primaryContainer = Color(primaryContainer),
            onPrimaryContainer = Color(s.onPrimaryContainer),
            inversePrimary = Color(s.inverseSurface),
            secondary = Color(s.secondary),
            onSecondary = Color(s.onSecondary),
            secondaryContainer = Color(s.secondaryContainer),
            onSecondaryContainer = Color(s.onSecondaryContainer),
            tertiary = Color(s.tertiary),
            onTertiary = Color(s.onTertiary),
            tertiaryContainer = Color(s.tertiaryContainer),
            onTertiaryContainer = Color(s.onTertiaryContainer),
            background = surface,
            onBackground = onSurface,
            surface = Color(this.surface),
            onSurface = onSurface,
            surfaceVariant = Color(this.surfaceVariant),
            onSurfaceVariant = Color(this.onSurfaceVariant),
            surfaceTint = Color(primary),
            inverseSurface = Color(this.inverseSurface),
            inverseOnSurface = Color(this.inverseOnSurface),
            outline = Color(this.outline),
            outlineVariant = Color(this.outlineVariant),
            scrim = Color.Black,
            error = Color(s.error),
            onError = Color(s.onError),
            errorContainer = Color(s.errorContainer),
            onErrorContainer = Color(s.onErrorContainer),
            surfaceBright = Color(s.surfaceBright),
            surfaceDim = Color(s.surfaceDim),
            surfaceContainer = Color(s.surfaceContainer),
            surfaceContainerHigh = Color(this.surfaceContainerHigh),
            surfaceContainerHighest = Color(s.surfaceContainerHighest),
            surfaceContainerLow = Color(this.surfaceContainerLow),
            surfaceContainerLowest = cardColor,
        )
    } else {
        lightColorScheme(
            primary = Color(primary),
            onPrimary = Color(onPrimary),
            primaryContainer = Color(primaryContainer),
            onPrimaryContainer = Color(s.onPrimaryContainer),
            inversePrimary = Color(s.inverseSurface),
            secondary = Color(s.secondary),
            onSecondary = Color(s.onSecondary),
            secondaryContainer = Color(s.secondaryContainer),
            onSecondaryContainer = Color(s.onSecondaryContainer),
            tertiary = Color(s.tertiary),
            onTertiary = Color(s.onTertiary),
            tertiaryContainer = Color(s.tertiaryContainer),
            onTertiaryContainer = Color(s.onTertiaryContainer),
            background = surface,
            onBackground = onSurface,
            surface = Color(this.surface),
            onSurface = onSurface,
            surfaceVariant = Color(this.surfaceVariant),
            onSurfaceVariant = Color(this.onSurfaceVariant),
            surfaceTint = Color(primary),
            inverseSurface = Color(this.inverseSurface),
            inverseOnSurface = Color(this.inverseOnSurface),
            outline = Color(this.outline),
            outlineVariant = Color(this.outlineVariant),
            scrim = Color.Black,
            error = Color(s.error),
            onError = Color(s.onError),
            errorContainer = Color(s.errorContainer),
            onErrorContainer = Color(s.onErrorContainer),
            surfaceBright = Color(s.surfaceBright),
            surfaceDim = Color(s.surfaceDim),
            surfaceContainer = Color(s.surfaceContainer),
            surfaceContainerHigh = Color(this.surfaceContainerHigh),
            surfaceContainerHighest = Color(s.surfaceContainerHighest),
            surfaceContainerLow = Color(this.surfaceContainerLow),
            surfaceContainerLowest = cardColor,
        )
    }
}
