package io.github.howard20181.hyperos.fcmlive.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.howard20181.hyperos.fcmlive.R

/** Top-bar actions shown / hidden as the screen changes mode. */
data class MainChrome(
    val title: String,
    val searching: Boolean = false,
    val multiSelect: Boolean = false,
    val showBack: Boolean = false,
    val showSearch: Boolean = true,
    val showMore: Boolean = true,
    val showBatch: Boolean = false,
    val showSelectAll: Boolean = false,
    val selectAllIcon: Int = R.drawable.ic_select_all,
)

/**
 * Main screen chrome in Compose: top bar + FAB. The swipe-refresh + ListView
 * stay as Views (multi-select, adapter, a11y) via [listContent].
 */
@Composable
fun MainScreen(
    chrome: MainChrome,
    onBack: () -> Unit,
    onSearch: () -> Unit,
    onMore: () -> Unit,
    onBatchAdd: () -> Unit,
    onBatchRemove: () -> Unit,
    onSelectAll: () -> Unit,
    onFab: () -> Unit,
    listContent: @Composable (androidx.compose.foundation.layout.BoxScope.() -> Unit),
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(Modifier.fillMaxSize()) {
            MainTopBar(
                chrome = chrome,
                onBack = onBack,
                onSearch = onSearch,
                onMore = onMore,
                onBatchAdd = onBatchAdd,
                onBatchRemove = onBatchRemove,
                onSelectAll = onSelectAll,
            )
            Box(modifier = Modifier.weight(1f), content = listContent)
        }
        MainFab(onClick = onFab, modifier = Modifier.align(Alignment.BottomEnd))
    }
}

@Composable
private fun MainTopBar(
    chrome: MainChrome,
    onBack: () -> Unit,
    onSearch: () -> Unit,
    onMore: () -> Unit,
    onBatchAdd: () -> Unit,
    onBatchRemove: () -> Unit,
    onSelectAll: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 10.dp, top = 12.dp, end = 8.dp, bottom = 12.dp)
            .height(76.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (chrome.showBack) {
            IconBtn(R.drawable.ic_arrow_back, onBack)
        }
        if (chrome.searching) {
            // Search field is a View (SearchView) hosted by the Activity.
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 6.dp)
            )
        } else {
            Text(
                text = chrome.title,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 22.sp,
                fontWeight = FontWeight.Normal,
                fontFamily = FontFamily.SansSerif,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
            )
        }
        if (chrome.showSearch) {
            IconBtn(R.drawable.ic_search, onSearch)
        }
        if (chrome.showBatch) {
            IconBtn(R.drawable.ic_batch_add, onBatchAdd)
            IconBtn(R.drawable.ic_batch_remove, onBatchRemove)
        }
        if (chrome.showSelectAll) {
            IconBtn(chrome.selectAllIcon, onSelectAll)
        }
        if (chrome.showMore) {
            IconBtn(R.drawable.ic_more_vert, onMore)
        }
    }
}

@Composable
private fun IconBtn(@DrawableRes icon: Int, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
private fun MainFab(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .padding(16.dp)
            .size(56.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_fcm_diagnostics),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(24.dp)
        )
    }
}

/**
 * Host for the existing swipe-refresh list. [factory] receives the Compose
 * [androidx.compose.ui.platform.AndroidView] context and must return the root
 * of the inflated list chrome.
 */
@Composable
fun MainListHost(factory: (android.content.Context) -> android.view.View) {
    AndroidView(
        factory = { ctx -> factory(ctx) },
        modifier = Modifier.fillMaxSize()
    )
}
