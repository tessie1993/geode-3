package dev.geode.data

import android.graphics.BitmapFactory
import java.io.File
import java.io.InputStream

/** Resource admission shared by document, texture-picker and folder imports. */
internal object MilkAssetAdmission {
    const val MAX_PRESET_BYTES = 2L * 1024 * 1024
    const val MAX_TEXTURE_BYTES = 16L * 1024 * 1024
    const val MAX_PACK_BYTES = 128L * 1024 * 1024
    const val MAX_ENTRIES = 1024
    const val MAX_DIMENSION = 8192L
    const val MAX_DECODED_BYTES = 128L * 1024 * 1024
    val textureExtensions = setOf("png", "jpg", "jpeg", "bmp", "tga", "dds", "dib")

    class Budget {
        var bytes = 0L
            private set

        fun consume(count: Int) {
            require(count >= 0 && count.toLong() <= MAX_PACK_BYTES - bytes) { "folder exceeds 128 MB" }
            bytes += count
        }
    }

    fun stage(
        input: InputStream,
        file: File,
        extension: String,
        budget: Budget = Budget(),
    ) {
        require(extension == "milk" || extension in textureExtensions) { "unsupported asset type" }
        val limit = if (extension == "milk") MAX_PRESET_BYTES else MAX_TEXTURE_BYTES
        var size = 0L
        file.outputStream().use { output ->
            val buffer = ByteArray(16 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                require(count > 0) { "provider stopped returning data" }
                require(count.toLong() <= limit - size) { "asset exceeds byte limit" }
                budget.consume(count)
                output.write(buffer, 0, count)
                size += count
            }
        }
        require(size > 0) { "asset is empty" }
        if (extension == "milk") validatePreset(file) else validateTexture(file, extension)
    }

    private fun validatePreset(file: File) {
        // Historic MilkDrop packs may use an 8-bit Windows encoding. Admission does not
        // rewrite their bytes or require a modern version header.
        val text = file.readText(Charsets.ISO_8859_1)
        require('\u0000' !in text && text.lineSequence().any { it.trim() == "[preset00]" }) {
            "not a MilkDrop preset (missing preset00 section or binary content)"
        }
    }

    private fun validateTexture(
        file: File,
        extension: String,
    ) {
        val header = file.inputStream().use { it.readBytesAtMost(148) }
        when (extension) {
            "dds" -> {
                require(header.size >= 128 && header.copyOfRange(0, 4).contentEquals("DDS ".toByteArray())) {
                    "invalid DDS header"
                }
                require(u32(header, 4) == 124L && u32(header, 76) == 32L) { "invalid DDS header size" }
                val dx10 = header.copyOfRange(84, 88).contentEquals("DX10".toByteArray())
                require(!dx10 || header.size >= 148) { "missing DDS extended header" }
                val arraySize = if (dx10) u32(header, 140) else 1L
                require(arraySize in 1..MAX_DIMENSION) { "invalid DDS array size" }
                val depth = u32(header, 24).coerceAtLeast(1)
                val cube = u32(header, 112) and 0x200L != 0L || (dx10 && u32(header, 136) and 4L != 0L)
                val faces = if (cube) 6L else 1L
                require(depth <= MAX_DIMENSION && u32(header, 28) <= 14L) { "invalid DDS depth or mip count" }
                dimensions(u32(header, 16), u32(header, 12), depth * faces * arraySize * 2)
                require(file.length() > if (dx10) 148 else 128) { "DDS pixel data is missing" }
            }
            "tga" -> {
                require(header.size >= 18) { "invalid TGA header" }
                require(u8(header, 1) <= 1 && u8(header, 2) in setOf(1, 2, 3, 9, 10, 11)) { "invalid TGA type" }
                require(u8(header, 16) in setOf(8, 15, 16, 24, 32)) { "invalid TGA pixel depth" }
                dimensions(u16(header, 12), u16(header, 14))
                validateTgaData(file, header)
            }
            "dib" -> {
                if (header.size >= 2 && header[0] == 'B'.code.toByte() && header[1] == 'M'.code.toByte()) {
                    validateBitmap(file)
                } else {
                    validateDib(file, header)
                }
            }
            else -> validateBitmap(file)
        }
    }

    private fun validateBitmap(file: File) {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        dimensions(options.outWidth.toLong(), options.outHeight.toLong())
    }

    /** Raw Windows DIB has no BMP file header and is not decoded by BitmapFactory. */
    private fun validateDib(
        file: File,
        header: ByteArray,
    ) {
        require(header.size >= 12) { "invalid DIB header" }
        val headerBytes = u32(header, 0)
        require(headerBytes in setOf(12L, 40L, 52L, 56L, 108L, 124L)) { "unsupported DIB header" }
        require(header.size >= headerBytes && file.length() > headerBytes) { "truncated DIB" }
        if (headerBytes == 12L) {
            require(u16(header, 8) == 1L && u16(header, 10) in setOf(1L, 4L, 8L, 24L)) { "invalid DIB pixel format" }
            dimensions(u16(header, 4), u16(header, 6))
        } else {
            val compression = u32(header, 16)
            val bits = u16(header, 14)
            require(u16(header, 12) == 1L && compression in 0L..6L) { "invalid DIB planes or compression" }
            require(bits in setOf(1L, 4L, 8L, 16L, 24L, 32L) || (bits == 0L && compression in 4L..5L)) {
                "invalid DIB pixel depth"
            }
            val rawHeight = u32(header, 8)
            val height = if (rawHeight >= 0x80000000L) 0x100000000L - rawHeight else rawHeight
            dimensions(u32(header, 4), height)
        }
    }

    private fun validateTgaData(
        file: File,
        header: ByteArray,
    ) {
        val type = u8(header, 2)
        val colorMap = u8(header, 1)
        require(type !in setOf(1, 9) || colorMap == 1) { "TGA color map is missing" }
        val paletteBytes = if (colorMap == 1) u16(header, 5) * ((u8(header, 7) + 7) / 8) else 0L
        val pixelBytes = (u8(header, 16) + 7) / 8
        val pixels = u16(header, 12) * u16(header, 14)
        java.io.RandomAccessFile(file, "r").use { input ->
            input.seek(18L + u8(header, 0) + paletteBytes)
            if (type < 9) {
                require(file.length() - input.filePointer >= pixels * pixelBytes) { "truncated TGA pixels" }
            } else {
                var remaining = pixels
                while (remaining > 0) {
                    val packet = input.readUnsignedByte()
                    val count = (packet and 0x7f) + 1L
                    require(count <= remaining) { "TGA packet exceeds image dimensions" }
                    val bytes = (if (packet and 0x80 != 0) 1L else count) * pixelBytes
                    require(file.length() - input.filePointer >= bytes) { "truncated TGA packet" }
                    input.seek(input.filePointer + bytes)
                    remaining -= count
                }
            }
        }
    }

    private fun dimensions(
        width: Long,
        height: Long,
        layers: Long = 1,
    ) {
        require(width in 1..MAX_DIMENSION && height in 1..MAX_DIMENSION) { "invalid or oversized image dimensions" }
        require(width * height * 4 <= MAX_DECODED_BYTES / layers) { "decoded texture exceeds 128 MB" }
    }

    private fun InputStream.readBytesAtMost(count: Int): ByteArray {
        val buffer = ByteArray(count)
        var offset = 0
        while (offset < count) {
            val read = read(buffer, offset, count - offset)
            if (read <= 0) break
            offset += read
        }
        return buffer.copyOf(offset)
    }

    private fun u8(
        bytes: ByteArray,
        offset: Int,
    ): Int = bytes[offset].toInt() and 0xff

    private fun u16(
        bytes: ByteArray,
        offset: Int,
    ): Long = (u8(bytes, offset) + (u8(bytes, offset + 1) shl 8)).toLong()

    private fun u32(
        bytes: ByteArray,
        offset: Int,
    ): Long =
        (0..3).fold(0L) { result, index -> result or (u8(bytes, offset + index).toLong() shl (index * 8)) }
}
