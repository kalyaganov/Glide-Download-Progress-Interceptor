package ru.futurobot.glidedownloadinterceptor

/**
 * A functional interface for receiving download progress updates.
 *
 * Implementations are notified on every chunk of data read from the network.
 * Use with [GlideProgressManager.addListener] and
 * [GlideProgressManager.removeListener].
 *
 * Example:
 * ```kotlin
 * val listener = ProgressListener { bytesRead, contentLength, done ->
 *     val percent = if (contentLength > 0) (bytesRead * 100 / contentLength) else 0
 *     println("Download: $percent% ($bytesRead / $contentLength) done=$done")
 * }
 * ```
 */
fun interface ProgressListener {

    /**
     * Called when download progress changes.
     *
     * @param bytesRead      Total number of bytes read so far.
     * @param contentLength  Total content length in bytes, or -1 if unknown.
     * @param done           `true` if the download has completed.
     */
    fun onProgress(bytesRead: Long, contentLength: Long, done: Boolean)
}
