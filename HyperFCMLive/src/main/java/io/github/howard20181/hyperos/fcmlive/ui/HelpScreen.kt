package io.github.howard20181.hyperos.fcmlive.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.text.HtmlCompat
import io.github.howard20181.hyperos.fcmlive.R
import io.github.howard20181.hyperos.fcmlive.theme.HyperFCMLiveTheme

@Composable
fun HelpScreen() {
    // Edge-to-edge: the scroll viewport fills the window (content passes under
    // the gesture indicator). Only the scrolled body carries padding — a
    // fixed inset band on the host view reads as a floating grey strip.
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
    ) {
        Column(
            modifier = Modifier.padding(
                start = 16.dp,
                end = 16.dp,
                bottom = 16.dp + navBottom
            )
        ) {
            SectionTitle(R.string.help_section_how)
            HelpCard {
                BodyText(R.string.help_how_body)
            }

            SectionTitle(R.string.help_section_allowlist)
            HelpCard {
                TitleText(R.string.help_allowlist_empty_title)
                BodyText(R.string.help_allowlist_empty_body, topPadding = 4.dp)
                TitleText(R.string.help_allowlist_checked_title, topPadding = 16.dp)
                BodyText(R.string.help_allowlist_checked_body, topPadding = 4.dp)
            }

            SectionTitle(R.string.help_section_strict)
            HelpCard {
                TitleText(R.string.help_strict_on_title)
                HtmlBodyText(R.string.help_strict_on_body, topPadding = 4.dp)
                TitleText(R.string.help_strict_unchanged_title, topPadding = 16.dp)
                BodyText(R.string.help_strict_unchanged_body, topPadding = 4.dp)
            }

            SectionTitle(R.string.help_section_power)
            HelpCard {
                TitleText(R.string.help_power_gms_title)
                BodyText(R.string.help_power_gms_body, topPadding = 4.dp)
                TitleText(R.string.help_power_actions_title, topPadding = 16.dp)
                BodyText(R.string.help_power_actions_body, topPadding = 4.dp)
                TitleText(R.string.help_power_scope_title, topPadding = 16.dp)
                BodyText(R.string.help_power_scope_body, topPadding = 4.dp)
            }

            SectionTitle(R.string.help_section_faq)
            HelpCard {
                TitleText(R.string.help_faq_q1)
                BodyText(R.string.help_faq_a1, topPadding = 4.dp)
                TitleText(R.string.help_faq_q2, topPadding = 16.dp)
                BodyText(R.string.help_faq_a2, topPadding = 4.dp)
                TitleText(R.string.help_faq_q3, topPadding = 16.dp)
                BodyText(R.string.help_faq_a3, topPadding = 4.dp)
                TitleText(R.string.help_faq_q4, topPadding = 16.dp)
                BodyText(R.string.help_faq_a4, topPadding = 4.dp)
            }
        }
    }
}

@Composable
private fun SectionTitle(res: Int) {
    Text(
        text = stringResource(res),
        color = MaterialTheme.colorScheme.primary,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        fontFamily = FontFamily.SansSerif,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, top = 28.dp, end = 8.dp, bottom = 12.dp)
    )
}

@Composable
private fun HelpCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                shape = RoundedCornerShape(16.dp)
            )
            .padding(16.dp),
        content = content
    )
}

@Composable
private fun TitleText(res: Int, topPadding: Dp = 0.dp) {
    Spacer(Modifier.height(topPadding))
    Text(
        text = stringResource(res),
        color = MaterialTheme.colorScheme.onSurface,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        fontFamily = FontFamily.SansSerif,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun BodyText(res: Int, topPadding: Dp = 0.dp) {
    Spacer(Modifier.height(topPadding))
    Text(
        text = stringResource(res),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontFamily = FontFamily.SansSerif,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun HtmlBodyText(res: Int, topPadding: Dp = 0.dp) {
    val html = stringResource(res)
    val text = remember(html) {
        val spanned = HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_COMPACT)
        AnnotatedString(spanned.toString())
    }
    Spacer(Modifier.height(topPadding))
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontFamily = FontFamily.SansSerif,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
fun HelpScreenThemed() {
    HyperFCMLiveTheme {
        HelpScreen()
    }
}