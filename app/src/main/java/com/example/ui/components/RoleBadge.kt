package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.LocalAuthManager

@Composable
fun RoleTitleBadge(
  role: String,
  roleTitle: String,
  modifier: Modifier = Modifier,
) {
  val normalizedRole = role.ifBlank { LocalAuthManager.ROLE_EXPLORER }
  val label = roleTitle.ifBlank {
    when (normalizedRole) {
      LocalAuthManager.ROLE_CREATOR -> LocalAuthManager.TITLE_CREATOR
      LocalAuthManager.ROLE_BETA_TESTER -> LocalAuthManager.TITLE_BETA_TESTER
      else -> LocalAuthManager.TITLE_EXPLORER
    }
  }

  val containerColor: Color
  val contentColor: Color
  val borderColor: Color
  val icon = when (normalizedRole) {
    LocalAuthManager.ROLE_CREATOR -> {
      containerColor = Color(0xFF2B220B)
      contentColor = Color(0xFFFFD166)
      borderColor = Color(0xFFFFB703).copy(alpha = 0.55f)
      Icons.Rounded.AutoAwesome
    }
    LocalAuthManager.ROLE_BETA_TESTER -> {
      containerColor = Color(0xFF092A26)
      contentColor = Color(0xFF00E5C3)
      borderColor = Color(0xFF00E5C3).copy(alpha = 0.50f)
      Icons.Rounded.Science
    }
    else -> {
      containerColor = MaterialTheme.colorScheme.secondaryContainer
      contentColor = MaterialTheme.colorScheme.onSecondaryContainer
      borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
      Icons.Rounded.Explore
    }
  }

  Surface(
    color = containerColor,
    contentColor = contentColor,
    shape = RoundedCornerShape(50),
    border = BorderStroke(1.dp, borderColor),
    modifier = modifier,
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Icon(
        imageVector = icon,
        contentDescription = label,
        tint = contentColor,
        modifier = Modifier.size(13.dp),
      )
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = contentColor,
      )
    }
  }
}
