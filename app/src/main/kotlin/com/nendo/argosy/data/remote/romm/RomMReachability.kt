package com.nendo.argosy.data.remote.romm

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.SystemClock
import com.nendo.argosy.data.remote.ssl.UserCertStore
import com.nendo.argosy.data.remote.ssl.UserCertTrustManager.withUserCertTrust
import com.nendo.argosy.util.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.Call
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.X509TrustManager

private const val TAG = "RomMReachability"
private const val REACHABLE_FRESH_MS = 30_000L
private const val UNREACHABLE_FRESH_MS = 10_000L
private const val PROBE_TIMEOUT_SECONDS = 5L
private const val HEARTBEAT_PATH = "api/heartbeat"

class RomMUnreachableException(message: String) : IOException(message)

internal fun heartbeatGotAnyResponse(callFactory: Call.Factory, root: String): Boolean = try {
    callFactory.newCall(Request.Builder().url(root + HEARTBEAT_PATH).build()).execute().use { true }
} catch (_: Exception) {
    false
}

@Singleton
class RomMReachability @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userCertStore: UserCertStore
) {
    private val lock = Any()
    private val lastReachableAt = mutableMapOf<String, Long>()
    private val lastUnreachableAt = mutableMapOf<String, Long>()

    private var cachedProbeClient: OkHttpClient? = null
    private var probeClientTrust: X509TrustManager? = null

    private fun probeClient(): OkHttpClient {
        val trust = userCertStore.trustManager()
        cachedProbeClient?.takeIf { probeClientTrust === trust }?.let { return it }
        return OkHttpClient.Builder()
            .connectTimeout(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .withUserCertTrust(userCertStore)
            .build()
            .also {
                cachedProbeClient = it
                probeClientTrust = trust
            }
    }

    val interceptor: Interceptor = Interceptor { chain ->
        val request = chain.request()
        val root = serverRoot(request.url)
        if (root == null || request.url.encodedPath.endsWith(HEARTBEAT_PATH)) {
            return@Interceptor chain.proceed(request)
        }
        if (!isReachable(root)) {
            throw RomMUnreachableException("RomM at $root is not reachable")
        }
        proceedRecording(chain, request, root)
    }

    fun hasActiveNetwork(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun recordReachable(baseUrl: String) {
        val root = baseUrl.trimEnd('/') + "/"
        synchronized(lock) {
            lastReachableAt[root] = SystemClock.elapsedRealtime()
            lastUnreachableAt.remove(root)
        }
    }

    fun isReachable(baseUrl: String): Boolean {
        if (!hasActiveNetwork()) return false
        val root = baseUrl.trimEnd('/') + "/"
        synchronized(lock) {
            val now = SystemClock.elapsedRealtime()
            lastReachableAt[root]?.let { if (now - it < REACHABLE_FRESH_MS) return true }
            lastUnreachableAt[root]?.let { if (now - it < UNREACHABLE_FRESH_MS) return false }
            val reachable = heartbeatGotAnyResponse(probeClient(), root)
            if (reachable) {
                lastReachableAt[root] = SystemClock.elapsedRealtime()
                lastUnreachableAt.remove(root)
            } else {
                lastUnreachableAt[root] = SystemClock.elapsedRealtime()
                Logger.info(TAG, "heartbeat probe failed at $root, failing calls fast")
            }
            return reachable
        }
    }

    private fun proceedRecording(chain: Interceptor.Chain, request: Request, root: String): Response =
        try {
            chain.proceed(request).also { recordReachable(root) }
        } catch (e: IOException) {
            if (chain.call().isCanceled()) throw e
            synchronized(lock) {
                lastReachableAt.remove(root)
                lastUnreachableAt[root] = SystemClock.elapsedRealtime()
            }
            throw e
        }

    private fun serverRoot(url: HttpUrl): String? {
        val full = url.toString()
        val apiIndex = full.indexOf("/api/")
        if (apiIndex < 0) return null
        return full.substring(0, apiIndex + 1)
    }
}
