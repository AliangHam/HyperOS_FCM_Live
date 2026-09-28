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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.howard20181.hyperos.fcmlive.R

/**
 * Privacy & permissions page. Opened from the About card that sits between
 * 「检查更新」 and 「查看源代码」. Body is the same content that used to live
 * as a static section on the About screen.
 */
@Composable
fun PrivacyScreen() {
    // Same edge-to-edge treatment as HelpScreen: the scroll viewport fills the
    // window; only the scrolled body carries the navigation-bar inset.
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
            SectionTitle(R.string.privacy_section_perms)
            PrivacyCard {
                BodyTitle(R.string.privacy_perm_query_all)
                BodyText(R.string.privacy_perm_query_all_desc, topPadding = 4.dp)
                BodyTitle(R.string.privacy_perm_get_installed, topPadding = 16.dp)
                BodyText(R.string.privacy_perm_get_installed_desc, topPadding = 4.dp)
                BodyTitle(R.string.privacy_perm_internet, topPadding = 16.dp)
                BodyText(R.string.privacy_perm_internet_desc, topPadding = 4.dp)
            }

            SectionTitle(R.string.privacy_section_commitment)
            PrivacyCard {
                BodyTitle(R.string.privacy_commitment)
                BodyText(R.string.privacy_commitment_desc, topPadding = 4.dp)
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
private fun PrivacyCard(content: @Composable ColumnScope.() -> Unit) {
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
private fun BodyTitle(res: Int, topPadding: Dp = 0.dp) {
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
