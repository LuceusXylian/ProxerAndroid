package me.proxer.app.anime.resolver

import io.reactivex.Single
import me.proxer.app.exception.StreamResolutionException
import me.proxer.app.util.Utils
import me.proxer.app.util.extension.buildSingle

/**
 * @author Ruben Gees
 */
class AmazonPrimeVideoStreamResolver : StreamResolver() {

    override val name = "Amazon Prime Video"

    override fun resolve(id: String): Single<StreamResolutionResult> = api.anime.link(id)
        .buildSingle()
        .map { StreamResolutionResult.Link(Utils.parseAndFixUrl(it) ?: throw StreamResolutionException()) }
}
