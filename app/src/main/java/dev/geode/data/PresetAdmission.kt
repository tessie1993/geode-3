package dev.geode.data

import android.util.JsonReader
import android.util.JsonToken
import dev.geode.render.fluid.FluidQuality
import dev.geode.render.scene.CymaticsMath
import dev.geode.render.scene.MarchBudget
import dev.geode.render.scene.SceneCapabilities
import dev.geode.render.scene.SceneIds
import dev.geode.render.scene.SceneParams
import dev.geode.render.scene.VisualStyleCatalog
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.StringReader
import java.nio.ByteBuffer

/**
 * Admission for external preset documents. Never clamps a supplied value or discards a key.
 * Missing v0/v1 optional fields retain the codec's documented legacy defaults.
 * These are requested-value domains, not the renderer's tighter output safety clamps.
 */
object PresetAdmission {
    const val SCHEMA_VERSION = 1
    const val MAX_BYTES = 4 * 1024 * 1024
    const val MAX_NAME_LENGTH = 120
    private const val MAX_SOURCE_BYTES = 1024 * 1024

    private val defaults by lazy {
        JSONObject(PresetStore.toJson(Preset("Preset", SceneIds.DEFAULT, 0.6f, 0.12f)))
    }

    internal val sceneIds: Set<String> by lazy {
        SceneCapabilities.SHADER_SCENES.keys + VisualStyleCatalog.cymaticsIds + VisualStyleCatalog.fluidIds +
            VisualStyleCatalog.silkIds + VisualStyleCatalog.lifeIds + VisualStyleCatalog.acidIds +
            VisualStyleCatalog.mycoIds + setOf(SceneIds.MILKDROP, SceneIds.CURLFLOW, SceneIds.WATER)
    }

    /** Bounded even when a content provider lies about size or never reports one. */
    fun readText(input: InputStream): String {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer, 0, minOf(buffer.size, MAX_BYTES + 1 - output.size()))
            if (count < 0) break
            if (count == 0) {
                val one = input.read()
                if (one < 0) break
                output.write(one)
            } else {
                output.write(buffer, 0, count)
            }
            if (output.size() > MAX_BYTES) reject(PresetFailure.TOO_LARGE)
        }
        return try {
            Charsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(output.toByteArray())).toString()
        } catch (_: java.nio.charset.CharacterCodingException) {
            reject(PresetFailure.MALFORMED)
        }
    }

    internal fun decode(json: String): Preset {
        if (json.toByteArray(Charsets.UTF_8).size > MAX_BYTES) reject(PresetFailure.TOO_LARGE)
        val objectValue = try {
            JsonReader(StringReader(json)).use { reader ->
                reader.isLenient = false
                val value = JSONObject()
                reader.beginObject()
                while (reader.hasNext()) {
                    val key = reader.nextName()
                    if (value.has(key)) reject(PresetFailure.MALFORMED, key)
                    if (!isAllowedKey(key)) reject(PresetFailure.UNSUPPORTED_FIELD, key)
                    val entry: Any = when (reader.peek()) {
                        JsonToken.STRING -> reader.nextString()
                        JsonToken.BOOLEAN -> reader.nextBoolean()
                        JsonToken.NUMBER -> reader.nextDouble().also {
                            if (!it.isFinite()) reject(PresetFailure.INVALID_VALUE, key)
                        }
                        else -> reject(PresetFailure.INVALID_VALUE, key)
                    }
                    value.put(key, entry)
                }
                reader.endObject()
                if (reader.peek() != JsonToken.END_DOCUMENT) reject(PresetFailure.MALFORMED)
                value
            }
        } catch (e: PresetAdmissionException) {
            throw e
        } catch (_: Exception) {
            reject(PresetFailure.MALFORMED)
        }
        validate(objectValue)
        return try {
            PresetStore.fromJson(objectValue.toString())
        } catch (_: Exception) {
            reject(PresetFailure.MALFORMED)
        }
    }

    internal fun validate(o: JSONObject) {
        if (o.has("schemaVersion")) {
            val version = o.get("schemaVersion")
            if (version !is Number || version.toDouble() != SCHEMA_VERSION.toDouble()) {
                reject(PresetFailure.UNSUPPORTED_VERSION, "schemaVersion")
            }
        }
        for (key in o.keys()) {
            if (!isAllowedKey(key)) {
                reject(PresetFailure.UNSUPPORTED_FIELD, key)
            }
            val value = o.get(key)
            when (key) {
                "schemaVersion" -> Unit
                "name" -> if (value !is String || value.isBlank() || value.length > MAX_NAME_LENGTH ||
                    value.any { it.isISOControl() }
                ) reject(PresetFailure.INVALID_VALUE, key)
                "sceneId" -> if (value !is String || value !in sceneIds) reject(PresetFailure.UNSUPPORTED_SCENE, key)
                "customShader", "milkPreset" -> if (value !is String || value.toByteArray(Charsets.UTF_8).size > MAX_SOURCE_BYTES) {
                    reject(PresetFailure.INVALID_VALUE, key)
                }
                "customPaletteId", "customPalette2Id" -> if (value !is String || value.length > 256) {
                    reject(PresetFailure.INVALID_VALUE, key)
                }
                else -> when (defaults.get(key)) {
                    is Boolean -> if (value !is Boolean) reject(PresetFailure.INVALID_VALUE, key)
                    is Number -> {
                        val number = (value as? Number)?.toDouble() ?: reject(PresetFailure.INVALID_VALUE, key)
                        val range = ranges[key] ?: reject(PresetFailure.UNSUPPORTED_FIELD, key)
                        if (!number.isFinite() || !number.toFloat().isFinite() || number.toFloat().toDouble() !in range) {
                            reject(PresetFailure.INVALID_VALUE, key)
                        }
                        if (key in integers && number % 1.0 != 0.0) reject(PresetFailure.INVALID_VALUE, key)
                        if (key == "symmetry" && number.toInt() !in SceneParams.SYMMETRY_FOLDS) {
                            reject(PresetFailure.INVALID_VALUE, key)
                        }
                        if (key.endsWith("Override") && number < 0.0 && number != SceneParams.UNSET_OVERRIDE.toDouble()) {
                            reject(PresetFailure.INVALID_VALUE, key)
                        }
                    }
                    else -> reject(PresetFailure.UNSUPPORTED_FIELD, key)
                }
            }
        }
        for (key in listOf("name", "sceneId", "attack", "decay")) {
            if (!o.has(key)) reject(PresetFailure.MALFORMED, key)
        }
    }

    // Keep enum identities and performance counts integral before the float JNI wire.
    private val integers =
        setOf(
            "symmetry",
            "particleShape",
            "palette",
            "palette2",
            "paletteLut",
            "fluidQuality",
            "fluidIterations",
            "fluidBeatPattern",
            "fluidBeatSplats",
            "fluidStirrers",
            "fluidSpawnPath",
            "fluidSpawnPoints",
            "fluidCatchPoints",
            "cymaticsGeometry",
            "cymaticsModes",
        )

    /** Domains from CustomizeTabs/SceneParams; zero is retained where legacy presets used an off state. */
    internal val ranges: Map<String, ClosedFloatingPointRange<Double>> by lazy {
        buildMap {
            fun fields(
                min: Double,
                max: Double,
                vararg keys: String,
            ) =
                keys.forEach { put(it, min.toFloat().toDouble()..max.toFloat().toDouble()) }
            fields(
                0.0,
                1.0,
                "attack",
                "decay",
                "sway",
                "pulse",
                "shake",
                "density",
                "trailLength",
                "trailWarp",
                "warp",
                "ripple",
                "morph",
                "pixelate",
                "posterize",
                "paletteMix",
                "milkdropPaletteTint",
                "colorShift",
                "bloom",
                "flash",
                "chromaAb",
                "vignette",
                "scanlines",
                "grain",
                "glitch",
                "strobe",
                "fluidPressure",
                "fluidChromaticAging",
                "fluidRadiusPulse",
                "fluidSpawnProgress",
                "fluidParticleDrag",
                "fluidBloomThreshold",
                "fluidSunraysWeight",
                "fluidCurlAudio",
                "fluidBloomAudio",
                "fluidFadeAudio",
                "flowStrength",
                "waterDepth",
                "waterSpecular",
                "waterFlow",
                "waterLiquid",
                "cymaticsRing",
                "cymaticsFocus",
                "cymaticsFill",
                "cymaticsIridescence",
                "cymaticsFlow",
                "rippleOverlayStrength",
                "rippleOverlaySpecular",
                "formDrive",
                "motionAmount",
                "motionBreath",
                "motionOrbit",
                "motionDrift",
                "motionHue",
            )
            fields(
                -1.0,
                1.0,
                "driftX",
                "driftY",
                "twist",
                "temperature",
                "fisheye",
                "cymaticsSwirl",
                "paletteBaseOverride",
                "paletteRangeOverride",
                "palette2BaseOverride",
                "palette2RangeOverride",
            )
            fields(
                0.0,
                2.0,
                "beatResponse",
                "brightness",
                "intensity",
                "bassGain",
                "midGain",
                "trebGain",
                "fluidStirrerSpeed",
                "fluidPaletteCycleSpeed",
                "fluidParticleBrightness",
                "fluidBloomIntensity",
                "waterRippleStrength",
                "waterLiquidFade",
                "cymaticsLine",
                "cymaticsGlow",
            )
            fields(0.0, 1.5, "turbulence", "hueRange", "saturation", "cymaticsCaustic")
            fields(0.0, 4.0, "speed", "fluidVelocityDissipation", "fluidDensityDissipation", "waterLiquidFlow")
            fields(0.3, 3.0, "zoom")
            fields(-3.0, 3.0, "rotation")
            fields(0.0, 1.2, "endlessZoomSpeed")
            fields(0.0, 2.5, "audioDrive", "contrast")
            fields(MarchBudget.MIN_DETAIL.toDouble(), MarchBudget.MAX_DETAIL.toDouble(), "marchDetail")
            fields(-0.5, 0.5, "trailZoom")
            fields(0.0, 16.0, "symmetry")
            fields(0.0, SceneParams.PARTICLE_SHAPES.lastIndex.toDouble(), "particleShape")
            fields(0.3, 2.5, "particleSize")
            fields(1.0, 6.0, "tile")
            fields(0.0, SceneParams.PALETTES.lastIndex.toDouble(), "palette", "palette2")
            fields(SceneParams.NO_PALETTE_LUT.toDouble(), SceneParams.CYCLIC_PALETTES.lastIndex.toDouble(), "paletteLut")
            fields(0.3, 2.5, "gamma")
            fields(0.0, 0.6, "cycleSpeed")
            fields(0.0, 5.0, "paramFadeSec")
            fields(0.0, FluidQuality.LABELS.lastIndex.toDouble(), "fluidQuality")
            fields(8.0, 40.0, "fluidIterations")
            fields(0.0, 50.0, "fluidCurl", "flowCurl")
            fields(0.02, 0.4, "fluidSplatRadius")
            fields(0.0, 3.0, "fluidSplatForce", "fluidCatchPull", "flowForce")
            fields(0.0, SceneParams.FLUID_PATTERNS.lastIndex.toDouble(), "fluidBeatPattern")
            fields(0.0, 8.0, "fluidBeatSplats")
            fields(0.0, 4.0, "fluidStirrers", "fluidCatchPoints")
            fields(0.0, SceneParams.FLUID_PATHS.lastIndex.toDouble(), "fluidSpawnPath")
            fields(1.0, 8.0, "fluidSpawnPoints")
            fields(0.03, 0.3, "fluidCatchRadius")
            fields(1.0, 20.0, "fluidParticleLife")
            fields(0.2, 2.0, "waterWaveSpeed")
            fields(0.9, 0.999, "waterDamping")
            fields(0.0, SceneParams.CYMATICS_GEOMETRIES.lastIndex.toDouble(), "cymaticsGeometry")
            fields(CymaticsMath.MIN_FUNDAMENTAL_HZ.toDouble(), CymaticsMath.MAX_FUNDAMENTAL_HZ.toDouble(), "cymaticsFundamental")
            fields(1.0, CymaticsMath.MAX_RENDERED_MODES.toDouble(), "cymaticsModes")
            fields(0.5, 8.0, "cymaticsScale")
        }
    }

    private fun reject(
        reason: PresetFailure,
        field: String? = null,
    ): Nothing = throw PresetAdmissionException(reason, field)

    private fun isAllowedKey(key: String): Boolean = defaults.has(key) || key == "customShader" || key == "milkPreset"
}
