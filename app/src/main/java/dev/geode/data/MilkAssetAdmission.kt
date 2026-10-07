package dev.geode.data

import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Shared limits for picker and folder imports. Values bound IO and GPU allocation, not visual quality. */
internal object MilkAssetAdmission {
    const val MAX_PRESET_BYTES = 2L * 1024 * 1024
    const val MAX_TEXTURE_BYTES = 16L * 1024 * 1024
    const val MAX_DIMENSION = 8192
    const val MAX_DECODED_BYTES = 64L * 1024 * 1024
    const val MAX_BATCH_BYTES = 256L * 1024 * 1024
    const val MAX_BATCH_DECODED_BYTES = 512L * 1024 * 1024
    const val MAX_BATCH_FILES = 512
    const val MAX_WALK_NODES = 4096
    const val MAX_WALK_DEPTH = 4
    val textureExtensions = setOf("png", "jpg", "jpeg", "bmp", "tga", "dds", "dib")

    fun fileNameIssue(name: String): String? =
        if (name.isBlank() || name.length > 240 || name.any { it.isISOControl() }) "invalid or overlong asset name" else null

    // Folder imports and direct imports share one writer so a same-name admission cannot race.
    val importLock = Any()

    class Rejected(
        override val message: String,
    ) : IOException(message)

    class Budget(
        private val maxFiles: Int = MAX_BATCH_FILES,
        private val maxBytes: Long = MAX_BATCH_BYTES,
        private val maxDecodedBytes: Long = MAX_BATCH_DECODED_BYTES,
    ) {
        private var files = 0
        private var encodedBytes = 0L
        private var decodedBytes = 0L

        fun beginEntry() {
            if (files >= maxFiles) throw Rejected("import file-count limit reached ($maxFiles)")
            if (encodedBytes >= maxBytes) throw Rejected("import byte budget exceeded")
            files++
        }

        fun consumeEncoded(bytes: Int) {
            if (bytes.toLong() > maxBytes - encodedBytes) {
                encodedBytes = maxBytes
                throw Rejected("import byte budget exceeded")
            }
            encodedBytes += bytes
        }

        fun readSize(
            bufferSize: Int,
            fileRemaining: Long,
        ): Int = minOf(bufferSize.toLong(), fileRemaining + 1, maxBytes - encodedBytes + 1).toInt()

        fun admitDecoded(bytes: Long) {
            if (bytes > maxDecodedBytes - decodedBytes) throw Rejected("import decoded-image budget exceeded")
            decodedBytes += bytes
        }
    }

    /** Returns only after validation and atomic publication. Rejection never opens the destination for writing. */
    @Suppress("TooGenericExceptionCaught")
    fun store(
        target: File,
        extension: String,
        budget: Budget,
        replaceExisting: Boolean,
        open: () -> InputStream?,
    ): String? =
        synchronized(importLock) {
            var staged: File? = null
            try {
                budget.beginEntry()
                if (extension != "milk" && extension !in textureExtensions) throw Rejected("unsupported file type")
                if (!replaceExisting && target.exists()) throw Rejected("already present")
                val parent = target.parentFile ?: throw Rejected("invalid destination")
                if (!parent.isDirectory && !parent.mkdirs()) throw Rejected("destination could not be created")
                val temporary = File.createTempFile(".asset-import-", ".tmp", parent)
                staged = temporary
                val cap = if (extension == "milk") MAX_PRESET_BYTES else MAX_TEXTURE_BYTES
                val input = open() ?: throw Rejected("could not be read")
                input.use { source ->
                    FileOutputStream(temporary).use { output ->
                        copyBounded(source, output, cap, budget)
                        output.fd.sync()
                    }
                }
                if (extension == "milk") {
                    validatePreset(readPresetText(temporary))
                } else {
                    budget.admitDecoded(validateTexture(temporary, extension))
                }
                // App-private source and destination share a filesystem. If atomic replacement
                // is unsupported, fail closed instead of deleting an existing valid texture.
                Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                null
            } catch (e: Rejected) {
                e.message
            } catch (_: IOException) {
                "could not be read or saved"
            } catch (_: SecurityException) {
                "access denied"
            } catch (error: RuntimeException) {
                if (error is java.util.concurrent.CancellationException) throw error
                "provider or image could not be read"
            } finally {
                staged?.delete()
            }
        }

    private fun copyBounded(
        source: InputStream,
        output: FileOutputStream,
        cap: Long,
        budget: Budget,
    ) {
        var size = 0L
        val buffer = ByteArray(32 * 1024)
        while (true) {
            val count = source.read(buffer, 0, budget.readSize(buffer.size, cap - size))
            if (count < 0) break
            // Some document providers return zero instead of making progress.
            val read = if (count == 0) {
                val one = source.read()
                if (one < 0) break
                buffer[0] = one.toByte()
                1
            } else {
                count
            }
            budget.consumeEncoded(read)
            size += read
            if (size > cap) throw Rejected("larger than ${cap / (1024 * 1024)} MB")
            output.write(buffer, 0, read)
        }
        if (size == 0L) throw Rejected("file is empty")
    }

    /** Also used by the relinker: legacy on-disk files are not implicitly trusted. */
    fun readPresetText(file: File): String {
        if (file.length() > MAX_PRESET_BYTES) throw Rejected("preset is larger than 2 MB")
        return file.inputStream().use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer, 0, minOf(buffer.size.toLong(), MAX_PRESET_BYTES + 1 - output.size()).toInt())
                if (count < 0) break
                if (count == 0) {
                    val one = input.read()
                    if (one < 0) break
                    output.write(one)
                } else {
                    output.write(buffer, 0, count)
                }
                if (output.size() > MAX_PRESET_BYTES) throw Rejected("preset is larger than 2 MB")
            }
            output.toString(Charsets.UTF_8.name())
        }
    }

    private fun validatePreset(text: String) {
        if (text.any { it < ' ' && it != '\r' && it != '\n' && it != '\t' }) throw Rejected("not a text MilkDrop preset")
        var section = false
        var assignment = false
        text.removePrefix("\uFEFF").lineSequence().forEach { raw ->
            if (raw.length > 64 * 1024) throw Rejected("preset line is too long")
            val line = raw.trim()
            when {
                line.isEmpty() || line.startsWith("//") || line.startsWith(';') -> Unit
                line.equals("[preset00]", ignoreCase = true) -> section = true
                '=' in line && line.substringBefore('=').isNotBlank() -> if (section) assignment = true
                else -> throw Rejected("unrecognized MilkDrop preset syntax")
            }
        }
        if (!section || !assignment) throw Rejected("missing MilkDrop preset section or settings")
    }

    private fun validateTexture(
        file: File,
        extension: String,
    ): Long {
        val header = file.inputStream().use { input ->
            val bytes = ByteArray(148)
            var size = 0
            while (size < bytes.size) {
                val count = input.read(bytes, size, bytes.size - size)
                if (count < 0) break
                if (count == 0) break
                size += count
            }
            bytes.copyOf(size)
        }
        return when (extension) {
            "dds" -> TextureHeaderAdmission.dds(header, file.length())
            "tga" -> TextureHeaderAdmission.tga(header, file.length()).also { TextureHeaderAdmission.validateTgaPixels(file, header) }
            "dib" -> TextureHeaderAdmission.bitmap(header, file.length(), dib = true)
            "bmp" -> TextureHeaderAdmission.bitmap(header, file.length(), dib = false)
            else -> {
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.absolutePath, options)
                TextureHeaderAdmission.decodedBytes(options.outWidth.toLong(), options.outHeight.toLong())
            }
        }
    }
}
