package dev.geode.export

import android.content.ContentResolver
import android.content.ContentValues
import android.net.Uri
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import dev.geode.R
import dev.geode.util.bestEffort
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.IOException

/** Only rows inserted by this transaction can be rolled back. */
internal interface PendingMediaStore<Id : Any> {
    fun insert(): Id?

    fun publish(id: Id): Int

    fun delete(id: Id)
}

@RequiresApi(29)
internal class PendingVideoStore(
    private val resolver: ContentResolver,
    private val displayName: String,
) : PendingMediaStore<Uri> {
    override fun insert(): Uri? =
        resolver.insert(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/Geode")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            },
        )

    override fun publish(id: Uri): Int =
        resolver.update(
            id,
            ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) },
            null,
            null,
        )

    override fun delete(id: Uri) {
        resolver.delete(id, null, null)
    }
}

internal class MediaStorePublicationException(
    val messageResource: Int,
) : IOException()

/** [write] includes closing the output and returns false when the user cancels. */
internal suspend fun <Id : Any> publishMediaStoreVideo(
    store: PendingMediaStore<Id>,
    write: suspend (Id) -> Boolean,
): Id? {
    currentCoroutineContext().ensureActive()
    val id = store.insert() ?: throw MediaStorePublicationException(R.string.export_output_create_failed)
    var published = false
    try {
        if (!write(id)) return null
        currentCoroutineContext().ensureActive()
        if (store.publish(id) != 1) throw MediaStorePublicationException(R.string.export_output_publish_failed)
        // The provider has committed a visible, complete file. Cancellation after this point
        // must keep it; the coordinator refreshes the library even if Saved delivery is cancelled.
        published = true
        return id
    } finally {
        if (!published) bestEffort("MediaStorePublication", "Delete unfinished export") { store.delete(id) }
    }
}
