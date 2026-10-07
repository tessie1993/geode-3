package dev.geode

import android.graphics.Path
import androidx.graphics.path.PathIterator
import androidx.graphics.path.PathSegment
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the rebuilt JNI implementation; API 34+ uses the platform iterator. */
@SdkSuppress(maxSdkVersion = 33)
@RunWith(AndroidJUnit4::class)
class GraphicsPathCompatibilityTest {
    @Test
    fun nativeIteratorPreservesCommandsAndCoordinates() {
        val path =
            Path().apply {
                moveTo(1f, 2f)
                lineTo(3f, 4f)
                quadTo(5f, 6f, 7f, 8f)
                cubicTo(9f, 10f, 11f, 12f, 13f, 14f)
                close()
            }
        val iterator = PathIterator(path)
        assertEquals(PathSegment.Type.Move, iterator.peek())
        val segments = iterator.asSequence().take(16).toList()
        assertFalse(iterator.hasNext())
        assertEquals(
            listOf(
                PathSegment.Type.Move,
                PathSegment.Type.Line,
                PathSegment.Type.Quadratic,
                PathSegment.Type.Cubic,
                PathSegment.Type.Close,
            ),
            segments.map { it.type },
        )
        assertEquals(3f, segments[1].points.last().x, 0f)
        assertEquals(4f, segments[1].points.last().y, 0f)
        assertEquals(7f, segments[2].points.last().x, 0f)
        assertEquals(14f, segments[3].points.last().y, 0f)
        assertEquals(PathSegment.Type.Done, iterator.peek())
    }

    @Test
    fun nativeConicConversionProducesFiniteQuadratics() {
        val path = Path().apply { addCircle(20f, 20f, 10f, Path.Direction.CW) }
        val conics =
            PathIterator(path, PathIterator.ConicEvaluation.AsConic)
                .asSequence()
                .take(64)
                .toList()
        assertEquals(4, conics.count { it.type == PathSegment.Type.Conic })
        val iterator = PathIterator(path, PathIterator.ConicEvaluation.AsQuadratics)
        val expectedSize = iterator.calculateSize()
        assertEquals(expectedSize, iterator.calculateSize())
        assertEquals(PathSegment.Type.Move, iterator.peek())
        val segments = iterator.asSequence().take(64).toList()
        val untouched =
            PathIterator(path, PathIterator.ConicEvaluation.AsQuadratics)
                .asSequence()
                .take(64)
                .toList()
        assertFalse(iterator.hasNext())
        assertEquals(expectedSize, segments.size)
        assertEquals(untouched.map { it.type }, segments.map { it.type })
        assertEquals(
            untouched.flatMap { it.points.toList() },
            segments.flatMap { it.points.toList() },
        )
        assertTrue(segments.count { it.type == PathSegment.Type.Quadratic } >= 4)
        assertFalse(segments.any { it.type == PathSegment.Type.Conic })
        assertTrue(segments.flatMap { it.points.toList() }.all { it.x.isFinite() && it.y.isFinite() })
    }
}
