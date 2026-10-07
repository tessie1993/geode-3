package dev.geode.ui

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import dev.geode.R
import dev.geode.ui.theme.LocalThemePack
import kotlin.math.max
import kotlin.math.roundToInt

private const val SPATIAL_CACHE_BYTES = 24 * 1024 * 1024
private const val CAPSULE_SOURCE_EDGE = 1254f

private val SpatialBitmapResources =
    setOf(
        R.drawable.spatial_lake_atmosphere,
        R.drawable.spatial_glass_pebble,
        R.drawable.spatial_glass_capsule,
        R.drawable.spatial_glass_orb_shell,
        R.drawable.spatial_foreground_ferns,
    )
private val SpatialBitmapCache =
    object : LruCache<Int, ImageBitmap>(SPATIAL_CACHE_BYTES) {
        override fun sizeOf(
            key: Int,
            value: ImageBitmap,
        ): Int = value.asAndroidBitmap().allocationByteCount
    }

/** Shared immutable material plates; the cache retains application resources, never an activity. */
@Composable
internal fun rememberTidalBitmap(
    @DrawableRes resource: Int,
): ImageBitmap {
    val resources = LocalContext.current.applicationContext.resources
    return remember(resource) {
        require(resource in SpatialBitmapResources) { "Only spatial glass assets belong in this cache" }
        synchronized(SpatialBitmapCache) {
            SpatialBitmapCache.get(resource)
                ?: run {
                    val bitmap = decodeSpatialBitmap(resources, resource)
                    SpatialBitmapCache.put(resource, bitmap)
                    bitmap
                }
        }
    }
}

private fun spatialTextureLimit(resource: Int): Int =
    when (resource) {
        R.drawable.spatial_lake_atmosphere -> 1536
        R.drawable.spatial_foreground_ferns -> 1024
        R.drawable.spatial_glass_orb_shell -> 768
        else -> 512
    }

private fun decodeSpatialBitmap(
    resources: Resources,
    @DrawableRes resource: Int,
): ImageBitmap {
    val limit = spatialTextureLimit(resource)
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeResource(resources, resource, bounds)
    var sample = 1
    while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= limit) sample *= 2
    val options =
        BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inScaled = false
            inSampleSize = sample
        }
    val decoded =
        checkNotNull(BitmapFactory.decodeResource(resources, resource, options)) {
            "Unable to decode spatial glass asset $resource"
        }
    val material = if (resource == R.drawable.spatial_glass_capsule) cropCapsulePlate(decoded) else decoded
    if (material !== decoded) decoded.recycle()
    val scale = (limit.toFloat() / max(material.width, material.height)).coerceAtMost(1f)
    val sized =
        if (scale < 1f) {
            Bitmap.createScaledBitmap(
                material,
                (material.width * scale).roundToInt().coerceAtLeast(1),
                (material.height * scale).roundToInt().coerceAtLeast(1),
                true,
            )
        } else {
            material
        }
    if (sized !== material) material.recycle()
    return sized.asImageBitmap()
}

/** The source plate stays untouched; only its transparent export padding is excluded at decode. */
private fun cropCapsulePlate(bitmap: Bitmap): Bitmap {
    val x = (18f / CAPSULE_SOURCE_EDGE * bitmap.width).roundToInt()
    val y = (415f / CAPSULE_SOURCE_EDGE * bitmap.height).roundToInt()
    val width = (1218f / CAPSULE_SOURCE_EDGE * bitmap.width).roundToInt().coerceAtMost(bitmap.width - x)
    val height = (470f / CAPSULE_SOURCE_EDGE * bitmap.height).roundToInt().coerceAtMost(bitmap.height - y)
    return Bitmap.createBitmap(bitmap, x, y, width, height)
}

@Composable
internal fun rememberSpatialGlassTint(): ColorFilter {
    val palette = LocalThemePack.current.palette
    return remember(palette.primary) {
        ColorFilter.tint(palette.primary.copy(alpha = 0.42f), BlendMode.SrcAtop)
    }
}
