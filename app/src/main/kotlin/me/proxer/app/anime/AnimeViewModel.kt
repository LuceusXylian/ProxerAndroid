package me.proxer.app.anime

import androidx.lifecycle.ViewModel
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.kotlin.plusAssign
import io.reactivex.rxjava3.schedulers.Schedulers
import me.proxer.app.anime.resolver.StreamResolutionResult
import me.proxer.app.anime.resolver.StreamResolverFactory
import me.proxer.app.base.BaseViewModel
import me.proxer.app.exception.AgeConfirmationRequiredException
import me.proxer.app.exception.AppRequiredException
import me.proxer.app.exception.NotLoggedInException
import me.proxer.app.exception.StreamResolutionException
import me.proxer.app.util.ErrorUtils
import me.proxer.app.util.ErrorUtils.ErrorAction
import me.proxer.app.util.ErrorUtils.ErrorAction.ButtonAction
import me.proxer.app.util.data.ResettingMutableLiveData
import me.proxer.app.util.extension.buildPartialErrorSingle
import me.proxer.app.util.extension.buildSingle
import me.proxer.app.util.extension.isAgeRestricted
import me.proxer.app.util.extension.subscribeAndLogErrors
import me.proxer.app.util.extension.toAnimeStream
import me.proxer.app.util.extension.toMediaLanguage
import me.proxer.library.api.Endpoint
import me.proxer.library.entity.info.EntryCore
import me.proxer.library.enums.AnimeLanguage
import me.proxer.library.enums.Category
import kotlin.properties.Delegates

/**
 * @author Ruben Gees
 */
class AnimeViewModel(
    private val entryId: String,
    private val language: AnimeLanguage,
    episode: Int, animeStreamInfo: AnimeStreamInfo
) : ViewModel() {

    private companion object {
        private val streamComparator = compareBy<AnimeStream, Int?>(nullsLast()) { it.resolutionResult?.let { 0 } }
    }

//    override val dataSingle: Single<AnimeStreamInfo>
//        get() = Single.fromCallable { validate() }
//            .flatMap { entrySingle() }
//            .doOnSuccess {
//                if (it.isAgeRestricted) {
//                    if (!storageHelper.isLoggedIn) {
//                        throw NotLoggedInException()
//                    } else if (!preferenceHelper.isAgeRestrictedMediaAllowed) {
//                        throw AgeConfirmationRequiredException()
//                    }
//                }
//            }
//            .flatMap {
//                Single.just(it).zipWith(streamSingle(it)) { entry, streams ->
//                    AnimeStreamInfo(entry.name, entry.episodeAmount, streams)
//                }
//            }

    val resolutionResult = ResettingMutableLiveData<StreamResolutionResult>()
    val resolutionError = ResettingMutableLiveData<ErrorAction>()

    val userStateData = ResettingMutableLiveData<Unit?>()
    val userStateError = ResettingMutableLiveData<ErrorAction?>()

    var episode by Delegates.observable(episode) { _, old, new ->
        if (old != new) reload()
    }

    private var cachedEntryCore: EntryCore? = null

    private var resolverDisposable: Disposable? = null
    private var userStateDisposable: Disposable? = null

    init {
        disposables += storageHelper.isLoggedInObservable
            .subscribe {
                if (it && error.value?.buttonAction == ButtonAction.LOGIN) {
                    reload()
                }
            }

        disposables += preferenceHelper.isAgeRestrictedMediaAllowedObservable
            .subscribe {
                if (error.value?.buttonAction == ButtonAction.AGE_CONFIRMATION) {
                    reload()
                }
            }
    }

    override fun onCleared() {
        resolverDisposable?.dispose()
        userStateDisposable?.dispose()

        resolverDisposable = null
        userStateDisposable = null

        super.onCleared()
    }

    override fun load() {
        resolverDisposable?.dispose()

        super.load()
    }

    fun resolve(stream: AnimeStream) {
        resolverDisposable?.dispose()

        val resolutionSingle = stream.resolutionResult?.let { Single.just(it) }
            ?: StreamResolverFactory.resolverFor(stream.hosterName)?.resolve(stream.id)
            ?: throw StreamResolutionException()

        resolverDisposable = resolutionSingle
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .doOnSubscribe { isLoading.value = true }
            .doAfterTerminate { isLoading.value = false }
            .subscribeAndLogErrors(
                {
                    resolutionError.value = null
                    resolutionResult.value = it
                },
                {
                    resolutionResult.value = null
                    resolutionError.value = when (it) {
                        is AppRequiredException -> AppRequiredErrorAction(it.name, it.appPackage)
                        else -> ErrorUtils.handle(it)
                    }
                }
            )
    }

    fun markAsFinished() = updateUserState(api.info.markAsFinished(entryId))

    fun bookmark(episode: Int) = updateUserState(
        api.ucp.setBookmark(entryId, episode, language.toMediaLanguage(), Category.ANIME)
    )

    private fun entrySingle(): Single<EntryCore> {
        val safeCachedEntryCore = cachedEntryCore

        return when (safeCachedEntryCore != null) {
            true -> Single.just(safeCachedEntryCore)
            false -> api.info.entryCore(entryId).buildSingle()
        }.doOnSuccess { cachedEntryCore = it }
    }

    private fun streamSingle(entry: EntryCore) = api.anime.streams(entryId, episode, language)
        .includeProxerStreams(true)
        .buildPartialErrorSingle(entry)
        .map { it.filterNot { stream -> StreamResolverFactory.resolverFor(stream.hosterName)?.ignore == true } }
        .map { it.groupBy { stream -> stream.hoster }.map { (_, streams) -> streams.shuffled().first() } }
        .toObservable()
        .flatMapIterable { it }
        .flatMapSingle { stream ->
            val resolver = StreamResolverFactory.resolverFor(stream.hosterName)

            if (resolver != null && resolver.resolveEarly) {
                resolver.resolve(stream.id).map { resolutionResult ->
                    stream.toAnimeStream(true, resolutionResult)
                }
            } else {
                Single.just(stream.toAnimeStream(resolver != null))
            }
        }
        .sorted(streamComparator)
        .toList()

    @Suppress("ForbiddenVoid")
    private fun updateUserState(endpoint: Endpoint<Unit?>) {
        userStateDisposable?.dispose()
        userStateDisposable = Single.fromCallable { validators.validateLogin() }
            .flatMap { endpoint.buildSingle() }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeAndLogErrors(
                {
                    userStateError.value = null
                    userStateData.value = Unit
                },
                {
                    userStateData.value = null
                    userStateError.value = ErrorUtils.handle(it)
                }
            )
    }
}
