package io.github.howard20181.hyperos.fcmlive.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** One label/value pair on the network card. */
data class StatusKeyValue(val key: String, val value: String)

/** One hook target row. */
data class StatusHookRow(
    val name: String,
    val note: String?,
    val stateText: String,
    val stateColor: Color,
)

/** One host (PowerKeeper / system_server) card. */
data class StatusHookGroup(val title: String, val rows: List<StatusHookRow>)

/** Everything the status screen shows, collected by the Activity. */
data class StatusUi(
    val network: List<StatusKeyValue>,
    val hooksSummary: String,
    val hookGroups: List<StatusHookGroup>,
    val diagnostics: List<String>,
)

/**
 * Module status body. Same immersive scroll as Help: content runs under the
 * gesture bar; navigation inset is scroll content, not a fixed band.
 */
@Composable
fun StatusScreen(
    ui: StatusUi,
    onCopyCommand: (String) -> Unit,
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
            SectionTitle(stringResource(io.github.howard20181.hyperos.fcmlive.R.string.status_network_title), spaced = false)
            Card {
                ui.network.forEach { StatusKeyValueRow(it) }
            }

            SectionTitle(stringResource(io.github.howard20181.hyperos.fcmlive.R.string.status_hooks_title), spaced = true)
            Text(
                text = ui.hooksSummary,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                fontFamily = FontFamily.SansSerif,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, top = 4.dp, end = 8.dp, bottom = 12.dp)
            )
            ui.hookGroups.forEachIndexed { index, group ->
                HostCard(title = group.title, rows = group.rows, topGap = index > 0)
            }

            SectionTitle(stringResource(io.github.howard20181.hyperos.fcmlive.R.string.status_diag_title), spaced = true)
            Card {
                ui.diagnostics.forEachIndexed { index, cmd ->
                    if (index > 0) {
                        Spacer(Modifier.height(8.dp))
                    }
                    CommandRow(command = cmd, onClick = { onCopyCommand(cmd) })
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String, spaced: Boolean) {
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
private fun Card(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .background(
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                shape = RoundedCornerShape(16.dp)
            )
            .padding(16.dp),
        content = content
    )
}

@Composable
private fun StatusKeyValueRow(kv: StatusKeyValue) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = kv.key,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp,
            fontFamily = FontFamily.SansSerif,
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp)
        )
        Text(
            text = kv.value,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.SansSerif,
            modifier = Modifier.weight(1f),
            textAlign = androidx.compose.ui.text.style.TextAlign.End
        )
    }
}

@Composable
private fun HostCard(title: String, rows: List<StatusHookRow>, topGap: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = if (topGap) 14.dp else 0.dp)
            .background(
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                shape = RoundedCornerShape(16.dp)
            )
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.SansSerif,
            modifier = Modifier.padding(start = 16.dp, top = 10.dp, end = 16.dp, bottom = 6.dp)
        )
        rows.forEachIndexed { index, row ->
            if (index > 0) {
                Spacer(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                )
            }
            HookRow(row)
        }
    }
}

@Composable
private fun HookRow(row: StatusHookRow) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Name column width must not depend on how long the state word is:
        // "可用" must not reveal more of the target than "不适用" does.
        // State is a fixed slot; name ellipsizes at the same place every row.
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp)
        ) {
            Text(
                text = row.name,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 18.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (row.note != null) {
                Text(
                    text = row.note,
                    color = MaterialTheme.colorScheme.outline,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.SansSerif
                )
            }
        }
        Text(
            text = row.stateText,
            color = row.stateColor,
            fontSize = 12.sp,
            fontFamily = FontFamily.SansSerif,
            lineHeight = 16.sp,
            maxLines = 2,
            textAlign = TextAlign.End,
            modifier = Modifier.width(72.dp)
        )
    }
}

@Composable
private fun CommandRow(command: String, onClick: () -> Unit) {
    Text(
        text = command,
        color = MaterialTheme.colorScheme.onSurface,
        fontSize = 12.sp,
        fontFamily = FontFamily.Monospace,
        lineHeight = 18.sp,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    )
}
