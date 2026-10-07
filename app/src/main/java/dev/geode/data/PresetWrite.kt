package dev.geode.data

/** A result is emitted only after the local preset has been committed. */
sealed interface PresetWrite {
    data class Saved(val preset: Preset) : PresetWrite

    data class Failed(val reason: PresetFailure, val field: String? = null) : PresetWrite
}

enum class PresetFailure {
    IO,
    TOO_LARGE,
    MALFORMED,
    UNSUPPORTED_VERSION,
    UNSUPPORTED_FIELD,
    UNSUPPORTED_SCENE,
    INVALID_VALUE,
    CONFLICT,
}

internal class PresetAdmissionException(
    val reason: PresetFailure,
    val field: String? = null,
) : Exception(reason.name)
