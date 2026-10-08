package dev.geode.ui.world

import android.app.ActivityManager
import android.content.Context
import android.view.View
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import dev.geode.R
import dev.geode.analysis.AudioFeatures
import dev.geode.ui.GeodeDestination
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect

/**
 * One persistent world surface behind native content. It observes the shared feature stream;
 * it owns no playback, analysis, capture, visualizer scene, or export resources.
 */
@Composable
fun NativeWorldBackdrop(
    destination: GeodeDestination,
    modifier: Modifier = Modifier,
    features: AudioFeatures? = null,
    reducedMotion: Boolean = false,
    active: Boolean = true,
    orbitExpanded: Boolean = false,
    lenses: List<WorldLensAnchor> = emptyList(),
    quality: WorldQuality = WorldQuality.BALANCED,
    backProgress: Float = 0f,
    backDestination: GeodeDestination? = null,
    featureSource: Flow<AudioFeatures>? = null,
    onRenderError: (String?) -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val reportError by rememberUpdatedState(onRenderError)
    var renderError by remember(context) { mutableStateOf<String?>(null) }
    val supportsGles3 =
        remember(context) {
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            (manager?.deviceConfigurationInfo?.reqGlEsVersion ?: 0) >= 0x30000
        }
    val view =
        remember(context, supportsGles3) {
            if (supportsGles3) {
                LakeWorldView(context) { error ->
                    renderError = error
                    reportError(error)
                }
            } else {
                null
            }
        }
    DisposableEffect(lifecycleOwner, view) {
        val observer =
            LifecycleEventObserver { _, _ ->
                view?.setHostResumed(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        view?.setHostResumed(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        if (!supportsGles3) reportError("UI world requires OpenGL ES 3.0; using the still environment.")
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            view?.release()
        }
    }
    val canCollectAudio = active && renderError == null
    LaunchedEffect(view, lifecycleOwner, featureSource, canCollectAudio) {
        if (view != null && featureSource != null && canCollectAudio) {
            lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                featureSource.collect { view.updateAudioFeatures(it) }
            }
        }
    }
    BoxWithConstraints(modifier) {
        val scenery = if (maxWidth > maxHeight) R.drawable.ui2_lake_dawn_landscape else R.drawable.ui2_lake_dawn
        Image(
            painter = painterResource(scenery),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (view != null && renderError == null) {
            AndroidView(
                factory = { view },
                modifier = Modifier.fillMaxSize(),
                update = {
                    it.setFeatureSourceEnabled(featureSource != null)
                    it.updateScene(destination, features, reducedMotion, orbitExpanded, lenses, backProgress, backDestination)
                    it.setQuality(quality)
                    if (active) {
                        it.visibility = View.VISIBLE
                        it.setHostActive(true)
                    } else {
                        // A paused SurfaceView can retain its last full-screen buffer. Hide the
                        // native surface as well before handing the space to another renderer.
                        it.setHostActive(false)
                        it.visibility = View.INVISIBLE
                    }
                },
            )
        }
    }
}
