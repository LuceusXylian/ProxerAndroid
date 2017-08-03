package me.proxer.app.anime.resolver

import android.content.Intent
import android.net.Uri
import io.reactivex.Single
import me.proxer.app.MainApplication
import me.proxer.app.anime.AppRequiredException
import me.proxer.app.util.Utils
import me.proxer.app.util.extension.buildSingle
import me.proxer.app.util.extension.toBodySingle
import okhttp3.Request
import java.util.regex.Pattern.quote

/**
 * @author Ruben Gees
 */
class CrunchyrollStreamResolver : StreamResolver() {

    private companion object {
        private const val CRUNCHYROLL_PACKAGE = "com.crunchyroll.crunchyroid"
        private val regex = Regex("mediaMetadata = ${quote("{")}\"id\":(\\d*),")
    }

    override val name = "Crunchyroll"

    override fun supports(name: String) = name.startsWith(this.name, true)
    override fun resolve(id: String): Single<StreamResolutionResult> = Single
            .fromCallable {
                if (!Utils.isPackageInstalled(MainApplication.globalContext.packageManager, CRUNCHYROLL_PACKAGE)) {
                    throw AppRequiredException(name, CRUNCHYROLL_PACKAGE)
                }
            }
            .flatMap { MainApplication.api.anime().link(id).buildSingle() }
            .flatMap { url ->
                MainApplication.client.newCall(Request.Builder()
                        .get()
                        .url(Utils.parseAndFixUrl(url))
                        .addHeader("User-Agent", MainApplication.USER_AGENT)
                        .build())
                        .toBodySingle()
                        .map {
                            val regexResult = regex.find(it) ?: throw StreamResolutionException()
                            val mediaId = regexResult.groupValues[1]

                            if (mediaId.isBlank()) {
                                throw StreamResolutionException()
                            }

                            StreamResolutionResult(Intent(Intent.ACTION_VIEW,
                                    Uri.parse("crunchyroll://media/$mediaId")))
                        }
            }
}
