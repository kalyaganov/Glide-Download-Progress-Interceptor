package ru.kalyaganov.glidedownloadinterceptor.ext

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import ru.kalyaganov.glidedownloadinterceptor.GlideProgressManager
import ru.kalyaganov.glidedownloadinterceptor.ProgressListener

/**
 * Registers a [ProgressListener] that is automatically removed when the
 * [Lifecycle] is destroyed.
 *
 * This is the recommended way to use [GlideProgressManager] in an Activity
 * or Fragment to avoid leaking listeners.
 *
 * Example:
 * ```kotlin
 * class MyActivity : AppCompatActivity() {
 *     override fun onCreate(savedInstanceState: Bundle?) {
 *         super.onCreate(savedInstanceState)
 *         GlideProgressManager.getInstance().observeProgress(lifecycle) { bytesRead, contentLength, done ->
 *             // Safe to update UI — listener is auto-removed on destroy.
 *         }
 *     }
 * }
 * ```
 *
 * @param lifecycle The lifecycle to observe.
 * @param listener  The progress listener to register.
 */
fun GlideProgressManager.observeProgress(
    lifecycle: Lifecycle,
    listener: ProgressListener
) {
    addListener(listener)
    lifecycle.addObserver(object : DefaultLifecycleObserver {
        override fun onDestroy(owner: LifecycleOwner) {
            removeListener(listener)
        }
    })
}
