package me.proxer.app

import android.annotation.SuppressLint
import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkRequest
import android.os.Environment
import android.os.Looper
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.getSystemService
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.GooglePlayServicesUtil
import com.google.android.gms.security.ProviderInstaller
import com.jakewharton.threetenabp.AndroidThreeTen
import io.reactivex.Completable
import io.reactivex.android.plugins.RxAndroidPlugins
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.exceptions.UndeliverableException
import io.reactivex.plugins.RxJavaPlugins
import io.reactivex.schedulers.Schedulers
import me.proxer.app.auth.LoginHandler
import me.proxer.app.base.NetworkConnectedEvent
import me.proxer.app.util.GlideDrawerImageLoader
import me.proxer.app.util.NotificationUtils
import me.proxer.app.util.compat.isConnected
import me.proxer.app.util.data.PreferenceHelper
import me.proxer.app.util.extension.isPackageInstalled
import me.proxer.app.util.extension.safeInject
import me.proxer.app.util.extension.subscribeAndLogErrors
import me.proxer.app.util.logging.TimberFileTree
import me.proxer.app.util.logging.WorkManagerTimberLogger
//TODO: FUCK Dependency Injection ; remove if not too much work
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import java.util.concurrent.TimeUnit

/**
 * @author Ruben Gees
 */
class MainApplication : Application() {

    companion object {
        const val USER_AGENT = "ProxerAndroid/${BuildConfig.VERSION_NAME}"
        const val GENERIC_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"
    }

    private val loginHandler by safeInject<LoginHandler>()
    private val preferenceHelper by safeInject<PreferenceHelper>()

    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidContext(this@MainApplication)

            modules(koinModules)
        }

        enableStrictModeForDebug()
        initGlobalErrorHandler()
        initSecurity()

        FlavorInitializer.initialize(this)
        NotificationUtils.createNotificationChannels(this)

        initLibs()
        initCache()
        initNightMode()

        initConnectionManager()

        loginHandler.listen(this)
    }

    private fun initConnectionManager() {
        val connectivityManager = requireNotNull(getSystemService<ConnectivityManager>())

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                if (connectivityManager.isConnected) {
                    Completable.fromAction { bus.post(NetworkConnectedEvent()) }
                        .delay(100, TimeUnit.MILLISECONDS)
                        .subscribeAndLogErrors()
                }
            }
        }

        connectivityManager.registerNetworkCallback(NetworkRequest.Builder().build(), callback)
    }

    private fun initGlobalErrorHandler() {
        val oldHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            error.printStackTrace()

            oldHandler?.uncaughtException(thread, error)
        }
    }

    private fun initSecurity() {
        ProviderInstaller.installIfNeededAsync(
            this,
            object : ProviderInstaller.ProviderInstallListener {
                override fun onProviderInstallFailed(errorCode: Int, recoveryIntent: Intent?) {
                    GoogleApiAvailability.getInstance().apply {
                        System.err.println("Error installing security patches with error code $errorCode")

                        if (
                            isUserResolvableError(errorCode) &&
                            packageManager.isPackageInstalled(GooglePlayServicesUtil.GOOGLE_PLAY_STORE_PACKAGE)
                        ) {
                            showErrorNotification(this@MainApplication, errorCode)
                        }
                    }
                }

                override fun onProviderInstalled() = Unit
            }
        )
    }

    private fun enableStrictModeForDebug() {
        if (BuildConfig.DEBUG) {
            val threadPolicy = StrictModeCompat.ThreadPolicy.Builder()
                .detectAll()
                .permitCustomSlowCalls()
                .permitDiskWrites()
                .permitDiskReads()
                .penaltyDialog()
                .penaltyLog()
                .build()

            val vmPolicy = StrictModeCompat.VmPolicy.Builder()
                .detectActivityLeaks()
                .detectCleartextNetwork()
                .detectFileUriExposure()
                .detectLeakedClosableObjects()
                .detectLeakedRegistrationObjects()
                .detectLeakedSqlLiteObjects()
                .detectContentUriWithoutPermission()
                .detectNonSdkApiUsage()
                .detectImplicitDirectBoot()
                .detectCredentialProtectedWhileLocked()
                .penaltyLog()
                .build()

            StrictModeCompat.setPolicies(threadPolicy, vmPolicy)
        }
    }

    // TODO: Remove once api becomes public.
    @SuppressLint("RestrictedApi")
    private fun initLibs() {
        WorkManager.initialize(this, Configuration.Builder().build())
        AndroidThreeTen.init(this)

        if (BuildConfig.LOG) {
            Timber.plant(TimberFileTree())

            if (BuildConfig.DEBUG) {
                Timber.plant(Timber.DebugTree())
            }
        }

        Logger.setLogger(WorkManagerTimberLogger())

        RxJavaPlugins.setErrorHandler { error ->
            when (error) {
                is UndeliverableException -> Timber.e(error, "Can't deliver error")
                is InterruptedException -> Timber.w(error)
                else ->
                    Thread.currentThread().uncaughtExceptionHandler
                        ?.uncaughtException(Thread.currentThread(), error)
            }
        }

        RxAndroidPlugins.setInitMainThreadSchedulerHandler { AndroidSchedulers.from(Looper.getMainLooper(), true) }
        RxAndroidPlugins.setMainThreadSchedulerHandler { AndroidSchedulers.from(Looper.getMainLooper(), true) }

        SubsamplingScaleImageView.setPreferredBitmapConfig(Bitmap.Config.RGB_565)
        DrawerImageLoader.init(GlideDrawerImageLoader())

        Completable
            .fromAction { EmojiManager.install(IosEmojiProvider()) }
            .subscribeOn(Schedulers.computation())
            .subscribe()
    }

    private fun initCache() {
        val hasExternalStorage = Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED

        if (!preferenceHelper.isCacheExternallySet) {
            preferenceHelper.shouldCacheExternally = hasExternalStorage
        } else if (preferenceHelper.shouldCacheExternally && !hasExternalStorage) {
            preferenceHelper.shouldCacheExternally = false
        }
    }

    @SuppressLint("CheckResult")
    private fun initNightMode() {
        AppCompatDelegate.setDefaultNightMode(preferenceHelper.themeContainer.variant.value)

        preferenceHelper.themeObservable.subscribe {
            AppCompatDelegate.setDefaultNightMode(it.variant.value)
        }
    }
}
