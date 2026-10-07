package dev.geode.data

import android.content.Context
import android.net.Uri
import java.io.File
import java.security.MessageDigest

data class MilkTexture(
    val name: String,
    val path: String,
)

data class TextureImportResult(
    val name: String,
    val storedName: String?,
    val skipReason: String?,
) {
    val imported: Boolean get() = storedName != null
}

data class TextureImportOutcome(
    val results: List<TextureImportResult>,
    val textures: List<MilkTexture>,
    val issue: String? = null,
)

data class TextureRemoveOutcome(
    val removed: Boolean,
    val removedGeneratedPresetPaths: List<String>,
    val textures: List<MilkTexture>,
)

class TextureStore(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val dir = File(context.filesDir, "milk/textures").apply { mkdirs() }

    fun list(): List<MilkTexture> =
        dir
            .listFiles { f -> f.isFile && f.extension.lowercase() in IMAGE_EXTS }
            ?.sortedBy { it.name.lowercase() }
            ?.map { MilkTexture(it.name, it.absolutePath) }
            .orEmpty()

    fun import(uris: List<Uri>): List<MilkTexture> = importDetailed(uris).textures

    fun importDetailed(uris: List<Uri>): TextureImportOutcome =
        synchronized(MilkAssetAdmission.importLock) {
            val budget = MilkAssetAdmission.Budget()
            val results = uris.take(MilkAssetAdmission.MAX_BATCH_FILES).map { importOne(it, budget) }.toMutableList()
            val remaining = uris.size - results.size
            if (remaining > 0) {
                results += TextureImportResult("$remaining additional files", null, "import file-count limit reached")
            }
            TextureImportOutcome(results, list())
        }

    private fun importOne(
        uri: Uri,
        budget: MilkAssetAdmission.Budget,
    ): TextureImportResult {
        val name = displayName(uri) ?: "texture_${System.currentTimeMillis()}.png"
        MilkAssetAdmission.fileNameIssue(name)?.let { return TextureImportResult(name.take(240), null, it) }
        val storedName = safeTextureFileName(name.substringAfterLast('/').substringAfterLast('\\'))
        val reason = MilkAssetAdmission.store(
            target = File(dir, storedName),
            extension = name.substringAfterLast('.', "").lowercase(),
            budget = budget,
            replaceExisting = true,
        ) { appContext.contentResolver.openInputStream(uri) }
        return TextureImportResult(name, if (reason == null) storedName else null, reason)
    }

    fun remove(name: String): List<MilkTexture> = removeDetailed(name).textures

    fun removeDetailed(name: String): TextureRemoveOutcome {
        val removed = runCatching { File(dir, name).delete() }.getOrDefault(false)
        val generated = generatedPresetFile(name)
        val remaining = list()
        val stem = name.substringBeforeLast('.')
        val legacy = File(generatedDir(), "show_$stem.milk")
        val sweepLegacy = legacy != generated && remaining.none { it.name.substringBeforeLast('.') == stem }
        val gone =
            listOfNotNull(
                generated.takeIf { runCatching { it.isFile && it.delete() }.getOrDefault(false) },
                legacy.takeIf { sweepLegacy && runCatching { it.isFile && it.delete() }.getOrDefault(false) },
            )
        return TextureRemoveOutcome(
            removed = removed,
            removedGeneratedPresetPaths = gone.map { it.absolutePath },
            textures = remaining,
        )
    }

    private fun displayName(uri: Uri): String? =
        runCatching {
            appContext.contentResolver
                .query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/')

    private fun generatedDir(): File = File(appContext.filesDir, "milk/generated")

    private fun generatedPresetFile(name: String): File = File(generatedDir(), "show_${name.replace('.', '_')}.milk")

    fun generateDisplayPreset(textureName: String): String {
        val base = textureName.substringBeforeLast('.')
        generatedDir().mkdirs()
        val file = generatedPresetFile(textureName)
        AtomicWrite.text(
            file,
            """
            MILKDROP_PRESET_VERSION=201
            PSVERSION=2
            PSVERSION_WARP=2
            PSVERSION_COMP=2
            [preset00]
            fRating=3.0
            fGammaAdj=1.6
            fDecay=0.97
            fVideoEchoZoom=1.0
            fVideoEchoAlpha=0.0
            nWaveMode=7
            bAdditiveWaves=0
            fWaveAlpha=0.001
            zoom=1.0
            rot=0.0
            cx=0.5
            cy=0.5
            warp=0.0
            sx=1.0
            sy=1.0
            ob_size=0.0
            ib_size=0.0
            per_frame_1=wave_a = 0;
            comp_1=`sampler sampler_$base;
            comp_2=`shader_body
            comp_3=`{
            comp_4=`   float2 uv2 = (uv - 0.5) / (1.0 + 0.10 * bass) + 0.5;
            comp_5=`   float3 img = tex2D(sampler_$base, uv2).xyz;
            comp_6=`   float pulse = 0.75 + 0.45 * treb + 0.30 * bass;
            comp_7=`   ret = img * pulse;
            comp_8=`}
            """.trimIndent(),
        )
        return file.absolutePath
    }

    internal companion object {
        val IMAGE_EXTS = MilkAssetAdmission.textureExtensions

        internal fun safeTextureFileName(name: String): String {
            val rawBase = name.substringBeforeLast('.')
            val ext = name.substringAfterLast('.', "").lowercase()
            val base = rawBase.replace(Regex("[^A-Za-z0-9]"), "_")
            val safeBase = if (base.firstOrNull()?.isDigit() == true) "t$base" else base.ifEmpty { "tex" }
            if (safeBase == rawBase) return "$safeBase.$ext"
            val hash =
                MessageDigest
                    .getInstance("SHA-256")
                    .digest(rawBase.toByteArray(Charsets.UTF_8))
                    .take(4)
                    .joinToString("") { b -> "%02x".format(b.toInt() and 0xff) }
            return "${safeBase}_$hash.$ext"
        }
    }
}
