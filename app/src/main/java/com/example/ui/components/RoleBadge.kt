package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ChangeHistory
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.MilitaryTech
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.WorkspacePremium
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
import com.example.roulette.RouletteCatalogDefaults

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
      LocalAuthManager.ROLE_PALMGYM -> LocalAuthManager.TITLE_PALMGYM
      LocalAuthManager.ROLE_BETA_TESTER -> LocalAuthManager.TITLE_BETA_TESTER
      LocalAuthManager.ROLE_CURIOUS_EXPLORER -> LocalAuthManager.TITLE_CURIOUS_EXPLORER
      LocalAuthManager.ROLE_NIGHT_RANGER -> LocalAuthManager.TITLE_NIGHT_RANGER
      LocalAuthManager.ROLE_VANGUARD -> LocalAuthManager.TITLE_VANGUARD
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
    LocalAuthManager.ROLE_PALMGYM -> {
      containerColor = Color(0xFF0E2A1F)
      contentColor = Color(0xFF5EEAD4)
      borderColor = Color(0xFF34D399).copy(alpha = 0.55f)
      Icons.Rounded.FitnessCenter
    }
    LocalAuthManager.ROLE_CURIOUS_EXPLORER -> {
      containerColor = Color(0xFF2A2108)
      contentColor = Color(0xFFFDE047)
      borderColor = Color(0xFFFACC15).copy(alpha = 0.55f)
      Icons.Rounded.ChangeHistory
    }
    LocalAuthManager.ROLE_NIGHT_RANGER -> {
      containerColor = Color(0xFF1D1836)
      contentColor = Color(0xFFC4B5FD)
      borderColor = Color(0xFFA78BFA).copy(alpha = 0.55f)
      Icons.Rounded.NightsStay
    }
    LocalAuthManager.ROLE_VANGUARD -> {
      containerColor = Color(0xFF0C2638)
      contentColor = Color(0xFF7DD3FC)
      borderColor = Color(0xFF38BDF8).copy(alpha = 0.55f)
      Icons.Rounded.MilitaryTech
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

@Composable
fun ContributionScoreBadge(
  score: Long,
  modifier: Modifier = Modifier,
) {
  val safeScore = score.coerceAtLeast(0L)
  val containerColor: Color
  val contentColor: Color
  val borderColor: Color
  when {
    safeScore >= 10L -> {
      containerColor = Color(0xFF28200D)
      contentColor = Color(0xFFFFD166)
      borderColor = Color(0xFFFFB703).copy(alpha = 0.50f)
    }
    safeScore >= 5L -> {
      containerColor = Color(0xFF0A2923)
      contentColor = Color(0xFF4CEAC8)
      borderColor = Color(0xFF00BFA5).copy(alpha = 0.50f)
    }
    safeScore >= 1L -> {
      containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
      contentColor = MaterialTheme.colorScheme.onPrimaryContainer
      borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.40f)
    }
    else -> {
      containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.75f)
      contentColor = MaterialTheme.colorScheme.onSurfaceVariant
      borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
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
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Icon(
        imageVector = Icons.Rounded.AutoAwesome,
        contentDescription = "Score de contribución: $safeScore misiones completadas o verificadas",
        tint = contentColor,
        modifier = Modifier.size(12.dp),
      )
      Text(
        text = "Contribución: $safeScore",
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = contentColor,
      )
    }
  }
}

@Composable
fun RouletteSpecialTitleBadge(
  title: String,
  modifier: Modifier = Modifier,
) {
  val cleanTitle = title.trim()
  if (cleanTitle.isBlank()) return
  val variant = RouletteCatalogDefaults.findTitleVariant(cleanTitle)
  val accentColor = variant?.accentColor ?: MaterialTheme.colorScheme.tertiary
  val containerColor = variant?.rarity?.badgeBgColor ?: MaterialTheme.colorScheme.tertiaryContainer
  val borderColor = accentColor.copy(alpha = 0.65f)

  Surface(
    color = containerColor,
    contentColor = accentColor,
    shape = RoundedCornerShape(50),
    border = BorderStroke(1.dp, borderColor),
    modifier = modifier,
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 11.dp, vertical = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
      Icon(
        imageVector = Icons.Rounded.WorkspacePremium,
        contentDescription = "Título de ruleta equipado: $cleanTitle",
        tint = accentColor,
        modifier = Modifier.size(14.dp),
      )
      Text(
        text = cleanTitle,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = accentColor,
      )
    }
  }
}
