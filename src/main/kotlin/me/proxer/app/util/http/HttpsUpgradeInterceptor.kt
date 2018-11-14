package me.proxer.app.util.http

import okhttp3.Interceptor
import okhttp3.Response

/**
 * @author Ruben Gees
 */
class HttpsUpgradeInterceptor : Interceptor {

    private companion object {
        private val UPGRADABLE_HOSTS = listOf(
            "www.mp4upload.com", "www.dailymotion.com", "embed.yourupload.com", "www.yourupload.com"
        )
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val currentRequest = chain.request()

        val newRequest = if (currentRequest.isHttps.not()) {
            val currentHost = currentRequest.url().host()

            if (UPGRADABLE_HOSTS.any { it.equals(currentHost, ignoreCase = true) }) {
                currentRequest.newBuilder()
                    .url(
                        currentRequest.url().newBuilder()
                            .scheme("https")
                            .build()
                    )
                    .build()
            } else {
                currentRequest
            }
        } else {
            currentRequest
        }

        return chain.proceed(newRequest)
    }
}
