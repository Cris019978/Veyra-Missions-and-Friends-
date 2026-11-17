package com.example.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.Architecture
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.Cable
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Coffee
import androidx.compose.material.icons.rounded.CompassCalibration
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Hiking
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Landscape
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.LocalFlorist
import androidx.compose.material.icons.rounded.LocalLibrary
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Museum
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Park
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.VolunteerActivism
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.data.model.Mission

object MissionIcons {

  fun iconForMission(mission: Mission): ImageVector {
    return iconForKey(mission.iconKey, mission.category, mission.missionType)
  }

  fun iconForKey(
    iconKey: String,
    category: String = "explore",
    missionType: String = "photo",
  ): ImageVector = when (iconKey) {
    "park" -> Icons.Rounded.Park
    "landscape" -> Icons.Rounded.Landscape
    "eco" -> Icons.Rounded.Eco
    "florist" -> Icons.Rounded.LocalFlorist
    "water_drop" -> Icons.Rounded.WaterDrop
    "wb_sunny" -> Icons.Rounded.WbSunny
    "pets" -> Icons.Rounded.Pets
    "volunteer" -> Icons.Rounded.VolunteerActivism
    "coffee" -> Icons.Rounded.Coffee
    "music_note" -> Icons.Rounded.MusicNote
    "local_library" -> Icons.Rounded.LocalLibrary
    "camera" -> Icons.Rounded.CameraAlt
    "brush" -> Icons.Rounded.Brush
    "palette" -> Icons.Rounded.AutoAwesome
    "architecture" -> Icons.Rounded.Architecture
    "museum" -> Icons.Rounded.Museum
    "directions_run" -> Icons.AutoMirrored.Rounded.DirectionsRun
    "hiking" -> Icons.Rounded.Hiking
    "bolt" -> Icons.Rounded.Bolt
    "cable" -> Icons.Rounded.Cable
    "hub" -> Icons.Rounded.Hub
    "memory" -> Icons.Rounded.Memory
    "extension" -> Icons.Rounded.Extension
    "grid_puzzle" -> Icons.Rounded.GridView
    "lightbulb" -> Icons.Rounded.Lightbulb
    "rocket" -> Icons.Rounded.RocketLaunch
    "compass" -> Icons.Rounded.CompassCalibration
    "map" -> Icons.Rounded.Map
    "explore" -> Icons.Rounded.Explore
    else -> when (missionType) {
      "wires" -> Icons.Rounded.Cable
      "puzzle" -> Icons.Rounded.Extension
      else -> when (category) {
        "eco" -> Icons.Rounded.LocalFlorist
        "social" -> Icons.Rounded.VolunteerActivism
        "challenge" -> Icons.Rounded.CameraAlt
        "fitness" -> Icons.AutoMirrored.Rounded.DirectionsRun
        "creative" -> Icons.Rounded.Brush
        else -> Icons.Rounded.Explore
      }
    }
  }

  fun accentColorForMission(
    mission: Mission,
    primary: Color,
    tertiary: Color,
  ): Color = when (mission.missionType) {
    "wires" -> Color(0xFF00897B)
    "puzzle" -> Color(0xFF5E35B1)
    else -> when (mission.category) {
      "eco" -> Color(0xFF1B8E6E)
      "social" -> tertiary
      "challenge" -> Color(0xFFD84315)
      "fitness" -> Color(0xFFE67E22)
      "creative" -> Color(0xFF8E44AD)
      else -> primary
    }
  }

  fun typeLabel(missionType: String): String = when (missionType) {
    "wires" -> "Cables"
    "puzzle" -> "Puzzle"
    else -> "Foto"
  }

  fun typeActionIcon(missionType: String): ImageVector = when (missionType) {
    "wires" -> Icons.Rounded.Cable
    "puzzle" -> Icons.Rounded.Extension
    else -> Icons.Rounded.CameraAlt
  }
}
