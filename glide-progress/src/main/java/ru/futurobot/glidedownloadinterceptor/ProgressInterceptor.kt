package ru.futurobot.glidedownloadinterceptor

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * An OkHttp [Interceptor] that wraps every response body in a [ProgressResponseBody].
 *
 * This interceptor should be added as a **network interceptor** so that it can
 * observe the raw network stream. It delegates progress notifications to the
 * provided [GlideProgressManager].
 *
 * @property manager The manager that maintains the list of active listeners.
 */
internal class ProgressInterceptor(
    private val manager: GlideProgressManager
) : Interceptor {

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalResponse = chain.proceed(chain.request())
        val body = originalResponse.body ?: return originalResponse

        return originalResponse.newBuilder()
            .body(ProgressResponseBody(body) { bytesRead, contentLength, done ->
                manager.notifyProgress(bytesRead, contentLength, done)
            })
            .build()
    }
}
