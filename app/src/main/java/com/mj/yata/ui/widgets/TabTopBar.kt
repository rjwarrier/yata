package com.mj.yata.ui.widgets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mj.yata.R
import com.mj.yata.util.initialsFor

/**
 * The top bar shared by the main tabs.
 *
 * Each tab used to build its own, from the same Row with the same insets, the same title styling
 * and the same trailing profile avatar. They were close enough to be the same thing and far
 * enough apart to drift: the last restyle had to be applied five times, and the menu button's
 * content description had already diverged between two of them.
 *
 * The title sits in the row with the icons rather than above them, which is what keeps this from
 * costing the ~50dp a stacked header would.
 */
@Composable
fun TabTopBar(
    title: String,
    onMenuClick: () -> Unit,
    userName: String,
    userPhotoUri: String?,
    onProfileClick: () -> Unit,
    modifier: Modifier = Modifier,
    menuContentDescription: String? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val menuLabel = menuContentDescription ?: stringResource(R.string.cd_open_drawer)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        YataTopBarIconButton(onClick = onMenuClick) {
            Icon(imageVector = Icons.Default.Menu, contentDescription = menuLabel)
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.ExtraBold,
                fontSynthesis = FontSynthesis.All,
                fontSize = 20.sp,
                color = MaterialTheme.colorScheme.onSurface
            ),
            modifier = Modifier.weight(1f).padding(start = 4.dp)
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            actions()
            val profileLabel = stringResource(R.string.cd_open_profile)
            IconButton(
                onClick = onProfileClick,
                modifier = Modifier.semantics { contentDescription = profileLabel }
            ) {
                PersonAvatar(
                    initials = initialsFor(userName),
                    accentKey = "accentC",
                    size = 32.dp,
                    photoUri = userPhotoUri
                )
            }
        }
    }
}

/**
 * The bar that replaces [TabTopBar] while items are selected. Identical in every tab that has one
 * except for the action on the right, which is the only part passed in.
 */
@Composable
fun TabSelectionTopBar(
    selectedCount: Int,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onCancel) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.cd_bulk_cancel_selection),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = pluralStringResource(R.plurals.selection_count, selectedCount, selectedCount),
                style = MaterialTheme.typography.titleMedium
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}
