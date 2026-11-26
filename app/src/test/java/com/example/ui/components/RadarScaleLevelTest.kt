package com.example.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RadarScaleLevelTest {

  @Test
  fun nextScale_cyclesThrough50_75_100_andBackTo50() {
    assertEquals(RadarScaleLevel.SCALE_75, RadarScaleLevel.SCALE_50.nextScale())
    assertEquals(RadarScaleLevel.SCALE_100, RadarScaleLevel.SCALE_75.nextScale())
    assertEquals(RadarScaleLevel.SCALE_50, RadarScaleLevel.SCALE_100.nextScale())
  }

  @Test
  fun progressiveReveal_increasesOsmVisibilityAndZoomsOutAsRadarExpands() {
    assertTrue(RadarScaleLevel.SCALE_50.osmRevealAlpha < RadarScaleLevel.SCALE_75.osmRevealAlpha)
    assertTrue(RadarScaleLevel.SCALE_75.osmRevealAlpha < RadarScaleLevel.SCALE_100.osmRevealAlpha)

    assertTrue(RadarScaleLevel.SCALE_50.veilOuterAlpha > RadarScaleLevel.SCALE_75.veilOuterAlpha)
    assertTrue(RadarScaleLevel.SCALE_75.veilOuterAlpha > RadarScaleLevel.SCALE_100.veilOuterAlpha)

    assertTrue(RadarScaleLevel.SCALE_50.veilInnerAlpha > RadarScaleLevel.SCALE_75.veilInnerAlpha)
    assertTrue(RadarScaleLevel.SCALE_75.veilInnerAlpha > RadarScaleLevel.SCALE_100.veilInnerAlpha)

    assertTrue(RadarScaleLevel.SCALE_50.osmZoomOffset > RadarScaleLevel.SCALE_75.osmZoomOffset)
    assertTrue(RadarScaleLevel.SCALE_75.osmZoomOffset > RadarScaleLevel.SCALE_100.osmZoomOffset)
  }
}
