package dev.geode.ui

import android.content.Context
import dev.geode.R
import dev.geode.data.PresetFailure
import dev.geode.data.PresetWrite

internal fun Context.presetWriteMessage(result: PresetWrite): String = when (result) {
    is PresetWrite.Saved -> getString(R.string.preset_saved_durably, result.preset.name)
    is PresetWrite.Failed -> {
        val message = when (result.reason) {
            PresetFailure.IO -> R.string.preset_write_io
            PresetFailure.TOO_LARGE -> R.string.preset_write_too_large
            PresetFailure.MALFORMED -> R.string.preset_write_malformed
            PresetFailure.UNSUPPORTED_VERSION -> R.string.preset_write_version
            PresetFailure.UNSUPPORTED_FIELD -> R.string.preset_write_field
            PresetFailure.UNSUPPORTED_SCENE -> R.string.preset_write_scene
            PresetFailure.INVALID_VALUE -> R.string.preset_write_value
            PresetFailure.CONFLICT -> R.string.preset_write_conflict
        }
        getString(message)
    }
}
