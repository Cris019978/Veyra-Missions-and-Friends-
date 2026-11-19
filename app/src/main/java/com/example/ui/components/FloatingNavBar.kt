package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Radar
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R

data class FloatingNavDestination(
  val index: Int,
  val label: String,
  val selectedIcon: ImageVector,
  val unselectedIcon: ImageVector,
  val testTag: String,
)

/**
 * Floating, generously rounded Material You pill bottom navigation bar.
 */
@Composable
fun FloatingBottomNavBar(
  selectedTab: Int,
  disableAnimations: Boolean,
  onSelectTab: (Int) -> Unit,
  modifier: Modifier = Modifier,
) {
  val destinations = listOf(
    FloatingNavDestination(
      index = 0,
      label = stringResource(id = R.string.nav_radar),
      selectedIcon = Icons.Rounded.Radar,
      unselectedIcon = Icons.Outlined.Radar,
      testTag = "nav_tab_radar",
    ),
    FloatingNavDestination(
      index = 1,
      label = stringResource(id = R.string.nav_profile),
      selectedIcon = Icons.Rounded.AccountCircle,
      unselectedIcon = Icons.Outlined.AccountCircle,
      testTag = "nav_tab_profile",
    ),
    FloatingNavDestination(
      index = 2,
      label = stringResource(id = R.string.nav_social_feed),
      selectedIcon = Icons.Rounded.PhotoLibrary,
      unselectedIcon = Icons.Outlined.PhotoLibrary,
      testTag = "nav_tab_social_feed",
    ),
    FloatingNavDestination(
      index = 3,
      label = stringResource(id = R.string.nav_social),
      selectedIcon = Icons.Rounded.Groups,
      unselectedIcon = Icons.Outlined.Groups,
      testTag = "nav_tab_social",
    ),
    FloatingNavDestination(
      index = 4,
      label = stringResource(id = R.string.nav_streak),
      selectedIcon = Icons.Rounded.LocalFireDepartment,
      unselectedIcon = Icons.Outlined.LocalFireDepartment,
      testTag = "nav_tab_streak",
    ),
  )

  Box(
    modifier = modifier
      .fillMaxWidth()
      .windowInsetsPadding(WindowInsets.navigationBars)
      .padding(horizontal = 12.dp, vertical = 12.dp),
    contentAlignment = Alignment.Center,
  ) {
    Surface(
      shape = RoundedCornerShape(34.dp),
      color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f),
      tonalElevation = 8.dp,
      shadowElevation = 12.dp,
      modifier = Modifier
        .widthIn(max = 560.dp)
        .fillMaxWidth()
        .border(
          width = 1.dp,
          color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
          shape = RoundedCornerShape(34.dp),
        )
        .testTag("floating_bottom_nav_bar"),
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        destinations.forEach { item ->
          val selected = selectedTab == item.index
          val activeBg = MaterialTheme.colorScheme.primaryContainer
          val pillColor = if (disableAnimations) {
            if (selected) activeBg else Color.Transparent
          } else {
            animateColorAsState(
              targetValue = if (selected) activeBg else Color.Transparent,
              animationSpec = spring(stiffness = 480f),
              label = "navPill_${item.index}",
            ).value
          }
          val iconTint = if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer
          } else {
            MaterialTheme.colorScheme.onSurfaceVariant
          }

          Column(
            modifier = Modifier
              .weight(1f)
              .minimumInteractiveComponentSize()
              .clip(RoundedCornerShape(26.dp))
              .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true),
                onClick = { onSelectTab(item.index) },
              )
              .padding(vertical = 4.dp)
              .testTag(item.testTag),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
          ) {
            Box(
              modifier = Modifier
                .clip(CircleShape)
                .background(pillColor)
                .padding(horizontal = 10.dp, vertical = 6.dp),
              contentAlignment = Alignment.Center,
            ) {
              Icon(
                imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                contentDescription = item.label,
                tint = iconTint,
                modifier = Modifier.size(22.dp),
              )
            }
            Text(
              text = item.label,
              style = MaterialTheme.typography.labelSmall,
              fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
              color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }
      }
    }
  }
}
