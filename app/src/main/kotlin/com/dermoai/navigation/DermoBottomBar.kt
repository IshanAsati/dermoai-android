package com.dermoai.navigation

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dermoai.R
import com.dermoai.core.ui.components.NeuSurface
import com.dermoai.core.ui.components.NeuSurfaceStyle
import com.dermoai.core.ui.theme.DermoColors

/** Tab definition for [DermoBottomBar]. */
data class BottomTab(
    val route: TabRoute,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
)

private val tabs = listOf(
    BottomTab(HomeTab, R.string.nav_home, Icons.Filled.Home, Icons.Outlined.Home),
    BottomTab(TimelineTab, R.string.nav_timeline, Icons.Filled.Timeline, Icons.Outlined.Timeline),
    BottomTab(ScanTab, R.string.nav_scan, Icons.Rounded.PhotoCamera, Icons.Rounded.PhotoCamera),
    BottomTab(SkinMindTab, R.string.nav_skinnmind, Icons.Filled.Psychology, Icons.Outlined.Psychology),
    BottomTab(MoreTab, R.string.nav_more, Icons.Filled.Menu, Icons.Outlined.Menu),
)

/**
 * Neumorphic 5-tab bottom bar: inset well surface with a carved top edge.
 * The Scan tab is center-emphasized with a raised pine chip, pressed when
 * selected.
 */
@Composable
fun DermoBottomBar(
    currentRoute: TabRoute?,
    onTabSelected: (TabRoute) -> Unit,
    modifier: Modifier = Modifier,
) {
    NeuSurface(
        modifier = modifier,
        style = NeuSurfaceStyle.Inset,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        NavigationBar(
            modifier = Modifier,
            containerColor = Color.Transparent,
            tonalElevation = 0.dp,
        ) {
            tabs.forEach { tab ->
                val selected = currentRoute == tab.route
                val isScan = tab.route is ScanTab

                NavigationBarItem(
                    selected = selected,
                    onClick = { onTabSelected(tab.route) },
                    icon = {
                        if (isScan) {
                            // Center-emphasized scan chip: raised well, pressed when selected.
                            NeuSurface(
                                modifier = Modifier.size(40.dp),
                                style = NeuSurfaceStyle.Raised,
                                shape = CircleShape,
                                pressedForce = selected,
                                color = DermoColors.TealAccent.copy(alpha = 0.1f),
                            ) {
                                Icon(
                                    imageVector = tab.selectedIcon,
                                    contentDescription = stringResource(tab.labelRes),
                                    modifier = Modifier.align(Alignment.Center).size(24.dp),
                                )
                            }
                        } else {
                            Icon(
                                imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                contentDescription = stringResource(tab.labelRes),
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    },
                    label = {
                        Text(
                            text = stringResource(tab.labelRes),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = if (isScan) DermoColors.TealText else MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        indicatorColor = if (isScan) Color.Transparent
                        else MaterialTheme.colorScheme.primaryContainer,
                    ),
                )
            }
        }
    }
}
