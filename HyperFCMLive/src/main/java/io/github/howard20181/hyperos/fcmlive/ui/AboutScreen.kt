package io.github.howard20181.hyperos.fcmlive.ui

import android.view.LayoutInflater
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.material.materialswitch.MaterialSwitch
import io.github.howard20181.hyperos.fcmlive.R

@Composable
fun AboutSectionTitle(text: String, spaced: Boolean = true) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.primary,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        fontFamily = FontFamily.SansSerif,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, top = if (spaced) 32.dp else 20.dp, end = 8.dp, bottom = 6.dp)
    )
}

@Composable
fun AboutCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .background(
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                shape = RoundedCornerShape(16.dp)
            ),
        content = content
    )
}

@Composable
private fun RowIcon(@DrawableRes res: Int) {
    Icon(
        painter = painterResource(res),
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .padding(end = 14.dp)
            .size(22.dp)
    )
}

/**
 * M3 switch with the app's check / close thumb glyph (the Compose Switch
 * stock thumb has no icon slot matching `switch_thumb_icon`).
 */
@Composable
private fun AppSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    AndroidView(
        factory = { ctx ->
            MaterialSwitch(ctx).apply {
                // Selector carries check (on) / close (off), matching View UI.
                thumbDrawable = androidx.appcompat.content.res.AppCompatResources.getDrawable(
                    ctx, R.drawable.switch_thumb_icon
                )
                isChecked = checked
                setOnCheckedChangeListener { _, value -> onCheckedChange(value) }
            }
        },
        update = { switch ->
            switch.setOnCheckedChangeListener(null)
            switch.isChecked = checked
            switch.setOnCheckedChangeListener { _, value -> onCheckedChange(value) }
        }
    )
}

@Composable
fun AboutRow(
    @DrawableRes icon: Int,
    title: String,
    subtitle: String? = null,
    value: String? = null,
    badge: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val clickable = if (onClick != null) {
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    } else {
        Modifier.fillMaxWidth()
    }
    Row(
        modifier = clickable.padding(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RowIcon(icon)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = FontFamily.SansSerif
                )
                if (badge) {
                    Spacer(Modifier.size(6.dp))
                    Box(
                        Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.error)
                    )
                }
            }
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.SansSerif,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            if (value != null) {
                Text(
                    text = value,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.SansSerif,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
fun AboutSwitchRow(
    @DrawableRes icon: Int,
    title: String,
    subtitle: String? = null,
    stateLabel: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = 12.dp, end = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RowIcon(icon)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp)
        ) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.SansSerif
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.SansSerif,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            if (stateLabel != null) {
                Text(
                    text = stateLabel,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.SansSerif,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
        AppSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

data class AboutUi(
    val hideIconChecked: Boolean,
    val hideIconState: String,
    val languageValue: String,
    val themeModeValue: String,
    val dynamicColorChecked: Boolean,
    val dynamicColorState: String,
    val paletteStyleValue: String,
    val colorSpecValue: String,
    val versionValue: String,
    val updateBadge: Boolean,
)

@Composable
fun AboutScreen(
    ui: AboutUi,
    onToggleHideIcon: (Boolean) -> Unit,
    onLanguageClick: () -> Unit,
    onThemeModeClick: () -> Unit,
    onToggleDynamicColor: (Boolean) -> Unit,
    onPaletteStyleClick: () -> Unit,
    onColorSpecClick: () -> Unit,
    onVersionClick: () -> Unit,
    onCheckUpdateClick: () -> Unit,
    onExportClick: () -> Unit,
    onImportClick: () -> Unit,
    onSourceClick: () -> Unit,
    onLicensesClick: () -> Unit,
) {
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
    ) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp + navBottom)
        ) {
            AboutSectionTitle(stringResource(R.string.about_section_display), spaced = false)
            AboutCard {
                AboutSwitchRow(
                    icon = R.drawable.ic_hide_icon,
                    title = stringResource(R.string.hide_launcher_icon),
                    subtitle = stringResource(
                        if (ui.hideIconChecked) {
                            R.string.about_sub_hide_icon_on
                        } else {
                            R.string.about_sub_hide_icon_off
                        }
                    ),
                    stateLabel = ui.hideIconState,
                    checked = ui.hideIconChecked,
                    onCheckedChange = onToggleHideIcon,
                )
                AboutRow(
                    icon = R.drawable.ic_language,
                    title = stringResource(R.string.language),
                    subtitle = stringResource(R.string.about_sub_language),
                    value = ui.languageValue,
                    onClick = onLanguageClick,
                )
            }

            AboutSectionTitle(stringResource(R.string.about_section_theme))
            AboutCard {
                AboutRow(
                    icon = R.drawable.ic_theme_mode,
                    title = stringResource(R.string.theme_mode),
                    subtitle = stringResource(R.string.about_sub_theme_mode),
                    value = ui.themeModeValue,
                    onClick = onThemeModeClick,
                )
                AboutSwitchRow(
                    icon = R.drawable.ic_dynamic_color,
                    title = stringResource(R.string.dynamic_color),
                    subtitle = stringResource(
                        if (ui.dynamicColorChecked) {
                            R.string.about_sub_dynamic_color_on
                        } else {
                            R.string.about_sub_dynamic_color_off
                        }
                    ),
                    stateLabel = ui.dynamicColorState,
                    checked = ui.dynamicColorChecked,
                    onCheckedChange = onToggleDynamicColor,
                )
                AboutRow(
                    icon = R.drawable.ic_palette_style,
                    title = stringResource(R.string.palette_style),
                    subtitle = stringResource(R.string.about_sub_palette_style),
                    value = ui.paletteStyleValue,
                    onClick = onPaletteStyleClick,
                )
                AboutRow(
                    icon = R.drawable.ic_color_spec,
                    title = stringResource(R.string.color_spec),
                    subtitle = stringResource(R.string.about_sub_color_spec),
                    value = ui.colorSpecValue,
                    onClick = onColorSpecClick,
                )
            }

            AboutSectionTitle(stringResource(R.string.about_section_backup))
            AboutCard {
                AboutRow(
                    icon = R.drawable.ic_export,
                    title = stringResource(R.string.export_allowlist),
                    subtitle = stringResource(R.string.about_sub_export),
                    onClick = onExportClick,
                )
                AboutRow(
                    icon = R.drawable.ic_import,
                    title = stringResource(R.string.import_allowlist),
                    subtitle = stringResource(R.string.about_sub_import),
                    onClick = onImportClick,
                )
            }

            AboutSectionTitle(stringResource(R.string.about_section_project))
            AboutCard {
                AboutRow(
                    icon = R.drawable.ic_version,
                    title = stringResource(R.string.about_current_version),
                    value = ui.versionValue,
                    onClick = onVersionClick,
                )
                AboutRow(
                    icon = R.drawable.ic_check_update,
                    title = stringResource(R.string.check_for_updates),
                    subtitle = stringResource(R.string.about_sub_check_update),
                    badge = ui.updateBadge,
                    onClick = onCheckUpdateClick,
                )
                AboutRow(
                    icon = R.drawable.ic_view_source,
                    title = stringResource(R.string.view_source_code),
                    subtitle = stringResource(R.string.about_sub_source),
                    onClick = onSourceClick,
                )
                AboutRow(
                    icon = R.drawable.ic_open_licenses,
                    title = stringResource(R.string.open_source_licenses),
                    subtitle = stringResource(R.string.about_sub_licenses),
                    onClick = onLicensesClick,
                )
            }
        }
    }
}
