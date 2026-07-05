package ru.futurobot.glidedownloadinterceptor

import okhttp3.MediaType
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.IOException
import okio.Source
import okio.buffer

/**
 * An OkHttp [ResponseBody] wrapper that reports download progress to a [ProgressListener].
 *
 * Wraps the original body's [Source] with a [ForwardingSource] that counts bytes
 * as they are read and invokes the listener on every read operation.
 *
 * @property responseBody      The original response body being wrapped.
 * @property progressListener  Listener that receives progress updates.
 */
class ProgressResponseBody(
    private val responseBody: ResponseBody,
    private val progressListener: ProgressListener
) : ResponseBody() {

    override fun contentType(): MediaType? = responseBody.contentType()

    override fun contentLength(): Long = responseBody.contentLength()

    override fun source(): BufferedSource {
        return CountingSource(responseBody.source()).buffer()
    }

    /**
     * A [ForwardingSource] that counts bytes read and reports progress.
     */
    private inner class CountingSource(delegate: Source) : ForwardingSource(delegate) {

        private var totalBytesRead = 0L

        @Throws(IOException::class)
        override fun read(sink: Buffer, byteCount: Long): Long {
            val bytesRead = super.read(sink, byteCount)
            val done = bytesRead == -1L
            if (!done) {
                totalBytesRead += bytesRead
            }
            progressListener.onProgress(totalBytesRead, contentLength(), done)
            return bytesRead
        }
    }
}
