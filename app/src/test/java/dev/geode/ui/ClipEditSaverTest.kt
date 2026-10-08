package dev.geode.ui

import androidx.compose.runtime.saveable.SaverScope
import dev.geode.export.ClipEdit
import dev.geode.export.ClipLook
import dev.geode.export.ExportQuality
import dev.geode.export.ExportRatio
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipEditSaverTest {
    @Test
    fun unsavedGradeTrimAndExportChoicesSurviveActualSaverRoundTrip() {
        val edit =
            ClipEdit(
                startMs = 1_200L,
                endMs = 12_800L,
                look = ClipLook.NEON,
                brightness = 0.12f,
                contrast = -0.2f,
                saturation = 38f,
                hueDegrees = 22f,
                monochrome = true,
                invert = true,
                speed = 1.25f,
                rotationDegrees = 90f,
                ratio = ExportRatio.R9_16,
                quality = ExportQuality.UHD4K,
                mute = true,
                caption = "A lake\nwith light — 水",
                lutUri = "content://geode/luts/dawn.cube",
                gammaRed = 0.85f,
                gammaGreen = 1.05f,
                gammaBlue = 1.15f,
            )
        val scope =
            object : SaverScope {
                override fun canBeSaved(value: Any): Boolean = value is String || value is Long || value is Float || value is Boolean
            }
        val saved = with(ClipEditSaver) { scope.save(edit) }
        assertTrue(saved != null)
        assertEquals(edit, ClipEditSaver.restore(requireNotNull(saved)))
    }

    @Test
    fun nullableChoicesAndIdentityDefaultsSurviveRoundTrip() {
        val defaults = ClipEdit()
        assertEquals(defaults, ClipEditSavedState.restore(ClipEditSavedState.save(defaults)))
        val emptyLut = defaults.copy(lutUri = "")
        assertEquals(emptyLut, ClipEditSavedState.restore(ClipEditSavedState.save(emptyLut)))
    }

    @Test
    fun missingOrMalformedFieldsFallBackWithoutDiscardingValidWork() {
        val saved =
            mapOf(
                "startMs" to "not a timestamp",
                "endMs" to -8L,
                "look" to "FUTURE_LOOK",
                "brightness" to Float.NaN,
                "contrast" to 0.25f,
                "speed" to 0f,
                "ratio" to "FUTURE_RATIO",
                "quality" to "FUTURE_QUALITY",
                "mute" to "true",
                "caption" to "Keep this work",
                "gammaRed" to Float.POSITIVE_INFINITY,
                "gammaGreen" to -1f,
            )
        assertEquals(
            ClipEdit(contrast = 0.25f, caption = "Keep this work"),
            ClipEditSavedState.restore(saved),
        )
        assertEquals(ClipEdit(), ClipEditSavedState.restore(emptyMap()))
    }
}
