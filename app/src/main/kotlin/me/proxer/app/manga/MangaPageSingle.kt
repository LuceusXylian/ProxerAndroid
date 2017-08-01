package me.proxer.app.manga

import android.content.Context
import io.reactivex.Single
import io.reactivex.SingleObserver
import io.reactivex.disposables.Disposable
import me.proxer.app.MainApplication
import me.proxer.app.util.extension.lock
import me.proxer.library.util.ProxerUrls
import okhttp3.Call
import okhttp3.Request
import okio.Okio
import java.io.File
import java.io.IOException
import java.util.concurrent.CancellationException
import kotlin.concurrent.read

/**
 * @author Ruben Gees
 */
class MangaPageSingle(context: Context, private val isLocal: Boolean, private val input: Input) : Single<File>() {

    private var context: Context? = context
    private var call: Call? = null

    override fun subscribeActual(observer: SingleObserver<in File>) {
        observer.onSubscribe(MangaPageDisposable())

        val directory = if (isLocal) context?.filesDir else context?.cacheDir ?: return
        val lock = if (isLocal) MangaLocks.localLock else MangaLocks.cacheLock

        lock.read {
            val lockKey = Triple(input.entryId, input.chapterId, input.name)

            synchronized(MangaLocks.pageLocks.getOrPut(lockKey, { Any() }), {
                val url = ProxerUrls.mangaPageImage(input.server, input.entryId, input.chapterId, input.name)
                val file = File("$directory/manga/${input.entryId}/${input.chapterId}/${url.hashCode()}.0")

                try {
                    if (file.exists()) {
                        observer.onSuccess(file)
                    } else {
                        file.parentFile.mkdirs()
                        file.createNewFile()

                        if (Thread.interrupted()) {
                            throw CancellationException()
                        }

                        MangaLocks.pageConcurrencyLock.lock {
                            call = MainApplication.client.newCall(Request.Builder()
                                    .url(url)
                                    .build())

                            call?.execute()?.let {
                                if (it.isSuccessful) {
                                    it.body()?.use { theBody ->
                                        Okio.buffer(Okio.sink(file)).use { fileBuffer ->
                                            fileBuffer.writeAll(theBody.source())
                                        }
                                    } ?: throw IOException()

                                    observer.onSuccess(file)
                                } else {
                                    throw IOException()
                                }
                            }
                        }
                    }
                } catch (error: Throwable) {
                    file.delete()

                    if (!Thread.interrupted()) observer.onError(error) else Unit
                }
            })
        }
    }

    private inner class MangaPageDisposable : Disposable {

        @Volatile private var isDisposed = false

        override fun isDisposed() = isDisposed

        override fun dispose() {
            if (!isDisposed) {
                isDisposed = true

                call?.cancel()
                context = null
                call = null
            }
        }
    }

    class Input(val server: String, val entryId: String, val chapterId: String, val name: String)
}