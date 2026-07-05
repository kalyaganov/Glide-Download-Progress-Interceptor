package ru.futurobot.glidedownloadinterceptor

import okhttp3.OkHttpClient
import java.util.Collections
import java.util.LinkedHashSet
import java.util.concurrent.TimeUnit

/**
 * Central manager for Glide download progress tracking.
 *
 * Maintains a thread-safe set of [ProgressListener] instances and provides a
 * factory method for creating an [OkHttpClient] configured with progress
 * interception.
 *
 * ## Basic Usage
 *
 * ```kotlin
 * val manager = GlideProgressManager.getInstance()
 * val listener = ProgressListener { bytesRead, contentLength, done ->
 *     // update UI
 * }
 *
 * manager.addListener(listener)
 * // ... perform Glide download ...
 * manager.removeListener(listener)
 * ```
 *
 * ## Integration with Glide
 *
 * Register the progress-tracking OkHttp client in your [com.bumptech.glide.module.AppGlideModule]:
 *
 * ```kotlin
 * @GlideModule
 * class MyAppGlideModule : AppGlideModule() {
 *     override fun registerComponents(context: Context, glide: Glide, registry: Registry) {
 *         val client = GlideProgressManager.createProgressClient()
 *         registry.replace(
 *             GlideUrl::class.java,
 *             InputStream::class.java,
 *             OkHttpUrlLoader.Factory(client)
 *         )
 *     }
 * }
 * ```
 *
 * ## Thread Safety
 *
 * All listener operations are synchronized and safe for use from multiple threads.
 * Progress notifications are dispatched on the thread that reads the network stream
 * (typically an OkHttp background thread).
 */
class GlideProgressManager private constructor() {

    // Thread-safe set of listeners, preserving insertion order
    private val listeners: MutableSet<ProgressListener> =
        Collections.synchronizedSet(LinkedHashSet())

    /**
     * Adds a progress listener. Duplicate additions are ignored.
     *
     * @param listener The listener to add. Must not be null.
     */
    fun addListener(listener: ProgressListener) {
        listeners.add(listener)
    }

    /**
     * Removes a progress listener.
     *
     * @param listener The listener to remove.
     * @return `true` if the listener was present and removed.
     */
    fun removeListener(listener: ProgressListener): Boolean {
        return listeners.remove(listener)
    }

    /**
     * Removes all registered listeners.
     */
    fun clearListeners() {
        listeners.clear()
    }

    /**
     * Returns `true` if any listeners are currently registered.
     */
    fun hasListeners(): Boolean = listeners.isNotEmpty()

    /**
     * Returns the number of registered listeners.
     */
    fun listenerCount(): Int = listeners.size

    /**
     * Called internally by [ProgressInterceptor] to dispatch progress updates
     * to all registered listeners.
     *
     * @param bytesRead      Total bytes read so far.
     * @param contentLength  Total content length, or -1 if unknown.
     * @param done           Whether the download is complete.
     */
    internal fun notifyProgress(bytesRead: Long, contentLength: Long, done: Boolean) {
        // Iterate over a snapshot to avoid ConcurrentModificationException
        // if a listener removes itself during notification.
        val snapshot: List<ProgressListener>
        synchronized(listeners) {
            snapshot = listeners.toList()
        }
        for (listener in snapshot) {
            try {
                listener.onProgress(bytesRead, contentLength, done)
            } catch (_: Exception) {
                // Silently ignore exceptions from individual listeners
                // to avoid breaking other listeners or the network stream.
            }
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: GlideProgressManager? = null

        /**
         * Returns the singleton instance of [GlideProgressManager].
         *
         * This uses double-checked locking for thread-safe lazy initialization.
         */
        @JvmStatic
        fun getInstance(): GlideProgressManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: GlideProgressManager().also { INSTANCE = it }
            }
        }

        /**
         * Creates a new [OkHttpClient] configured with progress interception.
         *
         * The returned client wraps all network responses with [ProgressResponseBody]
         * and dispatches progress to listeners registered on the singleton
         * [GlideProgressManager].
         *
         * Use this to integrate with Glide's OkHttp URL loader:
         * ```kotlin
         * val client = GlideProgressManager.createProgressClient()
         * registry.replace(GlideUrl::class, InputStream::class, OkHttpUrlLoader.Factory(client))
         * ```
         *
         * @param baseClient Optional base OkHttpClient to extend. A default client
         *                   is used if not provided.
         * @return A new OkHttpClient with the progress interceptor added.
         */
        @JvmStatic
        @JvmOverloads
        fun createProgressClient(baseClient: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(DEFAULT_CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(DEFAULT_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
        ): OkHttpClient {
            val manager = getInstance()
            return baseClient.newBuilder()
                .addNetworkInterceptor(ProgressInterceptor(manager))
                .build()
        }

        private const val DEFAULT_CONNECT_TIMEOUT_SECONDS = 15L
        private const val DEFAULT_READ_TIMEOUT_SECONDS = 30L
    }
}
