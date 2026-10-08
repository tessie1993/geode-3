package dev.geode.ui

import androidx.compose.runtime.saveable.mapSaver
import dev.geode.export.ClipEdit
import dev.geode.export.ClipLook
import dev.geode.export.ExportQuality
import dev.geode.export.ExportRatio

/** Unsaved clip work survives removal of the Studio content during navigation and overlays. */
internal val ClipEditSaver =
    mapSaver(
        save = { ClipEditSavedState.save(it) },
        restore = ClipEditSavedState::restore,
    )

/** Bundle-safe fields, with enum names so reordering an enum cannot alter an existing edit. */
internal object ClipEditSavedState {
    fun save(edit: ClipEdit): Map<String, Any?> =
        mapOf(
            "startMs" to edit.startMs,
            "endMs" to edit.endMs,
            "look" to edit.look.name,
            "brightness" to edit.brightness,
            "contrast" to edit.contrast,
            "saturation" to edit.saturation,
            "hueDegrees" to edit.hueDegrees,
            "monochrome" to edit.monochrome,
            "invert" to edit.invert,
            "speed" to edit.speed,
            "rotationDegrees" to edit.rotationDegrees,
            "ratio" to edit.ratio?.name,
            "quality" to edit.quality.name,
            "mute" to edit.mute,
            "caption" to edit.caption,
            "lutUri" to edit.lutUri,
            "gammaRed" to edit.gammaRed,
            "gammaGreen" to edit.gammaGreen,
            "gammaBlue" to edit.gammaBlue,
        )

    fun restore(saved: Map<String, Any?>): ClipEdit =
        ClipEdit(
            startMs = (saved["startMs"] as? Long)?.takeIf { it >= 0L } ?: 0L,
            endMs = (saved["endMs"] as? Long)?.takeIf { it >= 0L } ?: 0L,
            look = ClipLook.entries.firstOrNull { it.name == saved["look"] } ?: ClipLook.NONE,
            brightness = saved.finiteFloat("brightness", 0f),
            contrast = saved.finiteFloat("contrast", 0f),
            saturation = saved.finiteFloat("saturation", 0f),
            hueDegrees = saved.finiteFloat("hueDegrees", 0f),
            monochrome = saved["monochrome"] as? Boolean ?: false,
            invert = saved["invert"] as? Boolean ?: false,
            speed = saved.finiteFloat("speed", 1f).takeIf { it > 0f } ?: 1f,
            rotationDegrees = saved.finiteFloat("rotationDegrees", 0f),
            ratio = ExportRatio.entries.firstOrNull { it.name == saved["ratio"] },
            quality = ExportQuality.entries.firstOrNull { it.name == saved["quality"] } ?: ExportQuality.FHD1080,
            mute = saved["mute"] as? Boolean ?: false,
            caption = saved["caption"] as? String ?: "",
            lutUri = saved["lutUri"] as? String,
            gammaRed = saved.finiteFloat("gammaRed", 1f).takeIf { it > 0f } ?: 1f,
            gammaGreen = saved.finiteFloat("gammaGreen", 1f).takeIf { it > 0f } ?: 1f,
            gammaBlue = saved.finiteFloat("gammaBlue", 1f).takeIf { it > 0f } ?: 1f,
        )

    private fun Map<String, Any?>.finiteFloat(
        key: String,
        fallback: Float,
    ): Float = (this[key] as? Number)?.toFloat()?.takeIf { it.isFinite() } ?: fallback
}
