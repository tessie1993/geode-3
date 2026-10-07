package dev.geode.data

import java.io.File
import java.io.RandomAccessFile

/** Header-only admission for formats Android's BitmapFactory does not consistently recognize. */
internal object TextureHeaderAdmission {
    fun decodedBytes(
        width: Long,
        height: Long,
        layers: Long = 1,
    ): Long {
        if (width !in 1L..MilkAssetAdmission.MAX_DIMENSION.toLong() || height !in 1L..MilkAssetAdmission.MAX_DIMENSION.toLong()) {
            reject("image dimensions exceed ${MilkAssetAdmission.MAX_DIMENSION} pixels or are invalid")
        }
        val oneLayer = width * height * 4L
        if (layers < 1 || layers > MilkAssetAdmission.MAX_DECODED_BYTES / oneLayer) {
            reject("decoded image is larger than 64 MB")
        }
        return oneLayer * layers
    }

    fun tga(
        bytes: ByteArray,
        fileSize: Long,
    ): Long {
        if (bytes.size < 18) reject("truncated TGA header")
        val imageType = u8(bytes, 2)
        val colorMap = u8(bytes, 1)
        val depth = u8(bytes, 16)
        val width = u16(bytes, 12)
        val height = u16(bytes, 14)
        val decoded = decodedBytes(width.toLong(), height.toLong())
        val mapped = imageType == 1 || imageType == 9
        val validDepth = when (imageType) {
            1, 9 -> depth == 8 || depth == 16
            2, 10 -> depth in setOf(15, 16, 24, 32)
            3, 11 -> depth == 8 || depth == 16
            else -> false
        }
        if (!validDepth || colorMap !in 0..1 || (mapped && colorMap != 1)) reject("unrecognized TGA image type")
        val paletteBytes = if (colorMap == 1) {
            val paletteDepth = u8(bytes, 7)
            if (paletteDepth !in setOf(15, 16, 24, 32) || u16(bytes, 5) == 0) reject("invalid TGA color map")
            u16(bytes, 5).toLong() * ((paletteDepth + 7) / 8)
        } else {
            0L
        }
        val dataOffset = 18L + u8(bytes, 0) + paletteBytes
        val minimumPixels = if (imageType in 1..3) width.toLong() * height * ((depth + 7) / 8) else 1L
        if (fileSize < dataOffset + minimumPixels) reject("truncated TGA image")
        return decoded
    }

    fun validateTgaPixels(
        file: File,
        header: ByteArray,
    ) {
        val type = u8(header, 2)
        if (type < 9) return
        val paletteBytes = if (u8(header, 1) == 1) u16(header, 5).toLong() * ((u8(header, 7) + 7) / 8) else 0L
        val pixelBytes = (u8(header, 16) + 7) / 8
        var remaining = u16(header, 12).toLong() * u16(header, 14)
        RandomAccessFile(file, "r").use { input ->
            input.seek(18L + u8(header, 0) + paletteBytes)
            while (remaining > 0) {
                val packet = input.readUnsignedByte()
                val count = (packet and 0x7f) + 1L
                if (count > remaining) reject("TGA packet exceeds image dimensions")
                val bytes = (if (packet and 0x80 != 0) 1L else count) * pixelBytes
                if (file.length() - input.filePointer < bytes) reject("truncated TGA packet")
                input.seek(input.filePointer + bytes)
                remaining -= count
            }
        }
    }

    fun dds(
        bytes: ByteArray,
        fileSize: Long,
    ): Long {
        if (bytes.size < 128 || bytes.copyOfRange(0, 4).toString(Charsets.US_ASCII) != "DDS " ||
            u32(bytes, 4) != 124L || u32(bytes, 76) != 32L
        ) reject("invalid DDS header")
        val width = u32(bytes, 16)
        val height = u32(bytes, 12)
        val depth = u32(bytes, 24).coerceAtLeast(1)
        val mips = u32(bytes, 28).coerceAtLeast(1)
        val format = bytes.copyOfRange(84, 88).toString(Charsets.US_ASCII)
        var layers = depth
        val caps = u32(bytes, 112)
        if (caps and 0x200L != 0L) {
            val faces = java.lang.Long.bitCount(caps and 0xFC00L)
            if (faces == 0) reject("DDS cube has no faces")
            layers *= faces
        }
        val headerSize = if (format == "DX10") 148 else 128
        if (format == "DX10") {
            if (bytes.size < 148 || u32(bytes, 140) == 0L) reject("invalid DDS extended header")
            val arrays = u32(bytes, 140)
            if (arrays > MilkAssetAdmission.MAX_DECODED_BYTES) reject("DDS array is too large")
            layers = depth * arrays
            if (u32(bytes, 136) and 4L != 0L) layers *= 6
        }
        decodedBytes(width, height, layers)
        val maximumMips = 64 - java.lang.Long.numberOfLeadingZeros(maxOf(width, height, depth))
        if (mips > maximumMips) reject("invalid DDS mip count")
        val pixelFormat = ddsPixelFormat(bytes, format)
        var decoded = 0L
        var w = width
        var h = height
        var d = depth
        val surfaces = layers / depth
        var expectedPayload = 0L
        repeat(mips.toInt()) {
            val mipLayers = surfaces * d
            decoded += decodedBytes(w, h, mipLayers)
            expectedPayload += if (pixelFormat.blockBytes > 0) {
                ((w + 3) / 4) * ((h + 3) / 4) * pixelFormat.blockBytes * mipLayers
            } else {
                w * h * pixelFormat.bytesPerPixel * mipLayers
            }
            w = (w / 2).coerceAtLeast(1)
            h = (h / 2).coerceAtLeast(1)
            d = (d / 2).coerceAtLeast(1)
        }
        if (decoded > MilkAssetAdmission.MAX_DECODED_BYTES) reject("decoded DDS is larger than 64 MB")
        if (fileSize < headerSize + expectedPayload) reject("truncated DDS image")
        return decoded
    }

    private data class DdsPixelFormat(
        val blockBytes: Int = 0,
        val bytesPerPixel: Int = 0,
    )

    private fun ddsPixelFormat(
        bytes: ByteArray,
        fourCc: String,
    ): DdsPixelFormat =
        when (fourCc) {
            "DXT1", "ATI1", "BC4U", "BC4S" -> DdsPixelFormat(blockBytes = 8)
            "DXT2", "DXT3", "DXT4", "DXT5", "ATI2", "BC5U", "BC5S" -> DdsPixelFormat(blockBytes = 16)
            "DX10" -> {
                if (u32(bytes, 132) !in 2L..4L) reject("invalid DDS resource dimension")
                when (u32(bytes, 128).toInt()) {
                    in 70..72, in 79..81 -> DdsPixelFormat(blockBytes = 8)
                    in 73..78, in 82..84, in 94..99 -> DdsPixelFormat(blockBytes = 16)
                    in 1..4 -> DdsPixelFormat(bytesPerPixel = 16)
                    in 5..8 -> DdsPixelFormat(bytesPerPixel = 12)
                    in 9..18 -> DdsPixelFormat(bytesPerPixel = 8)
                    in 23..47, 67, 87, 88, 89, 90, 91, 92, 93 -> DdsPixelFormat(bytesPerPixel = 4)
                    in 48..59, 85, 86 -> DdsPixelFormat(bytesPerPixel = 2)
                    in 60..65 -> DdsPixelFormat(bytesPerPixel = 1)
                    else -> reject("unsupported DDS pixel format")
                }
            }
            else -> {
                // Legacy floating-point D3DFORMAT codes may occupy the FourCC field.
                val numeric = u32(bytes, 84)
                val pixelBytes = when (numeric) {
                    111L -> 2
                    112L, 114L -> 4
                    113L, 115L -> 8
                    116L -> 16
                    0L -> {
                        val bits = u32(bytes, 88)
                        if (bits !in setOf(8L, 16L, 24L, 32L)) reject("unsupported DDS pixel depth")
                        (bits / 8).toInt()
                    }
                    else -> reject("unsupported DDS pixel format")
                }
                DdsPixelFormat(bytesPerPixel = pixelBytes)
            }
        }

    fun bitmap(
        bytes: ByteArray,
        fileSize: Long,
        dib: Boolean,
    ): Long {
        val offset = if (dib) 0 else 14
        if (bytes.size < offset + 12 || (!dib && (u8(bytes, 0) != 66 || u8(bytes, 1) != 77))) {
            reject("invalid bitmap header")
        }
        val headerSize = u32(bytes, offset)
        val core = headerSize == 12L
        if (!core && headerSize !in setOf(40L, 52L, 56L, 64L, 108L, 124L)) reject("unsupported bitmap header")
        if (bytes.size < offset + minOf(headerSize, 40L)) reject("truncated bitmap header")
        val width = if (core) u16(bytes, offset + 4).toLong() else signed32(bytes, offset + 4)
        val height = if (core) u16(bytes, offset + 6).toLong() else kotlin.math.abs(signed32(bytes, offset + 8))
        val decoded = decodedBytes(width, height)
        val planes = u16(bytes, offset + if (core) 8 else 12)
        val bits = u16(bytes, offset + if (core) 10 else 14)
        if (planes != 1 || bits !in setOf(1, 4, 8, 16, 24, 32)) reject("invalid bitmap pixel format")
        val dataOffset = if (dib) headerSize else u32(bytes, 10)
        if (dataOffset < offset + headerSize || dataOffset >= fileSize) reject("truncated bitmap image")
        val compression = if (core) 0L else u32(bytes, offset + 16)
        if (compression == 0L || compression == 3L || compression == 6L) {
            val rowBytes = ((width * bits + 31) / 32) * 4
            if (fileSize - dataOffset < rowBytes * height) reject("truncated bitmap pixels")
        }
        return decoded
    }

    private fun u8(
        bytes: ByteArray,
        offset: Int,
    ): Int = bytes[offset].toInt() and 0xff

    private fun u16(
        bytes: ByteArray,
        offset: Int,
    ): Int = u8(bytes, offset) or (u8(bytes, offset + 1) shl 8)

    private fun u32(
        bytes: ByteArray,
        offset: Int,
    ): Long = (0..3).fold(0L) { value, index -> value or (u8(bytes, offset + index).toLong() shl (index * 8)) }

    private fun signed32(
        bytes: ByteArray,
        offset: Int,
    ): Long = u32(bytes, offset).toInt().toLong()

    private fun reject(message: String): Nothing = throw MilkAssetAdmission.Rejected(message)
}
