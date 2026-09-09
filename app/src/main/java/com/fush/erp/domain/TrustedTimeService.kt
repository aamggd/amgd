package com.fush.erp.domain

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import kotlin.concurrent.thread
import kotlin.math.abs

/**
 * Monotonic trusted-time policy for ERP/security decisions.
 *
 * Runtime time always advances from [SystemClock.elapsedRealtime]. Device wall time is only an
 * anchor candidate. When internet is available, an HTTPS Date header is used to re-anchor against
 * an external UTC source. Offline operation then continues from elapsedRealtime without following
 * later manual Date/Time or Time-Zone changes.
 *
 * First launch cannot prove an arbitrary device wall clock while fully offline. Therefore:
 * - Android automatic time enabled => device wall clock is accepted provisionally and HTTPS sync
 *   opportunistically strengthens it.
 * - automatic time disabled => sensitive operations remain blocked until HTTPS time succeeds or
 *   automatic time is restored and the app is reopened.
 */
object TrustedTimeService {
    const val WALL_DRIFT_TOLERANCE_MS = 120_000L

    private const val PREFS_NAME = "fush_trusted_time"
    private const val KEY_BOOT_COUNT = "boot_count"
    private const val KEY_ANCHOR_WALL = "anchor_wall"
    private const val KEY_ANCHOR_ELAPSED = "anchor_elapsed"
    private const val KEY_BOOT_EPOCH_WALL = "boot_epoch_wall"
    private const val KEY_LAST_TRUSTED_WALL = "last_trusted_wall"
    private const val KEY_LAST_HTTPS_SYNC_WALL = "last_https_sync_wall"
    private const val PERSIST_INTERVAL_MS = 60_000L

    private val lock = Any()

    @Volatile private var appContext: Context? = null
    @Volatile private var anchorWallMs: Long = rawWallMillis()
    @Volatile private var anchorElapsedMs: Long = rawElapsedRealtime()
    @Volatile private var currentBootCount: Int = -1
    @Volatile private var initialized = false
    @Volatile private var integrityFailure: String? = null
    @Volatile private var lastPersistElapsedMs: Long = anchorElapsedMs
    @Volatile private var lastHttpsSyncWallMs: Long = 0L
    @Volatile private var httpsRefreshInFlight = false

    data class Status(
        val trustedNow: Long,
        val elapsedRealtime: Long,
        val trusted: Boolean,
        val failureReason: String?,
        val lastHttpsSyncWall: Long = 0L
    )

    fun initialize(context: Context) {
        synchronized(lock) {
            val application = context.applicationContext
            appContext = application
            val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val wallNow = rawWallMillis()
            val elapsedNow = rawElapsedRealtime()
            val bootNow = readBootCount(application)
            val savedBoot = prefs.getInt(KEY_BOOT_COUNT, -2)
            val savedAnchorWall = prefs.getLong(KEY_ANCHOR_WALL, 0L)
            val savedAnchorElapsed = prefs.getLong(KEY_ANCHOR_ELAPSED, -1L)
            val savedBootEpochWall = prefs.getLong(KEY_BOOT_EPOCH_WALL, 0L)
            val savedLastTrusted = prefs.getLong(KEY_LAST_TRUSTED_WALL, 0L)
            lastHttpsSyncWallMs = prefs.getLong(KEY_LAST_HTTPS_SYNC_WALL, 0L)
            val bootEpochWallNow = wallNow - elapsedNow

            integrityFailure = null
            currentBootCount = bootNow

            val bootIdentityMatches = if (savedBoot >= 0 && bootNow >= 0) {
                savedBoot == bootNow
            } else {
                savedBootEpochWall > 0L &&
                    !TrustedTimeMath.isWallDriftSuspicious(bootEpochWallNow, savedBootEpochWall, WALL_DRIFT_TOLERANCE_MS)
            }
            val sameBootAnchorUsable = bootIdentityMatches &&
                savedAnchorWall > 0L && savedAnchorElapsed >= 0L && elapsedNow >= savedAnchorElapsed

            if (sameBootAnchorUsable) {
                val expected = TrustedTimeMath.monotonicWall(savedAnchorWall, savedAnchorElapsed, elapsedNow)
                anchorWallMs = expected
                anchorElapsedMs = elapsedNow
                if (TrustedTimeMath.isWallDriftSuspicious(wallNow, expected, WALL_DRIFT_TOLERANCE_MS)) {
                    integrityFailure = "تم اكتشاف تغيير في تاريخ/ساعة الجهاز. أعد الوقت الصحيح ثم أعد فتح التطبيق."
                }
            } else {
                val autoTimeEnabled = isAutomaticTimeEnabled(application)
                val firstLaunch = savedLastTrusted <= 0L
                val rolledBack = savedLastTrusted > 0L && wallNow + WALL_DRIFT_TOLERANCE_MS < savedLastTrusted
                val untrustedAfterReboot = savedLastTrusted > 0L && !autoTimeEnabled
                anchorWallMs = if (rolledBack || untrustedAfterReboot) savedLastTrusted else wallNow
                anchorElapsedMs = elapsedNow
                integrityFailure = when {
                    rolledBack -> "تم اكتشاف إرجاع تاريخ/ساعة الجهاز للخلف. صحح الوقت قبل متابعة العمليات الحساسة."
                    untrustedAfterReboot -> "تعذر توثيق الوقت بعد إعادة تشغيل الجهاز لأن الضبط التلقائي للوقت غير مفعّل. فعّل التاريخ والوقت التلقائيين أو اتصل بالإنترنت للتحقق عبر HTTPS."
                    firstLaunch && !autoTimeEnabled -> "أول تشغيل يحتاج وقتًا موثوقًا. فعّل التاريخ والوقت التلقائيين أو اتصل بالإنترنت للتحقق عبر HTTPS."
                    else -> null
                }
            }

            initialized = true
            lastPersistElapsedMs = elapsedNow
            persistAnchorLocked(forceLastTrusted = integrityFailure == null)
        }
        refreshFromHttpsAsync()
    }

    /** Wall-clock value that advances only from a monotonic clock after anchoring. */
    fun now(): Long = synchronized(lock) {
        val elapsed = rawElapsedRealtime()
        val trusted = TrustedTimeMath.monotonicWall(anchorWallMs, anchorElapsedMs, elapsed)
        detectRuntimeTamperLocked(trusted)
        maybePersistLocked(trusted, elapsed)
        trusted
    }

    /** Monotonic duration clock for sessions, reauthentication windows and other elapsed-time rules. */
    fun elapsedRealtime(): Long = rawElapsedRealtime()

    fun status(): Status {
        val trustedNow = now()
        return Status(
            trustedNow = trustedNow,
            elapsedRealtime = elapsedRealtime(),
            trusted = integrityFailure == null,
            failureReason = integrityFailure,
            lastHttpsSyncWall = lastHttpsSyncWallMs
        )
    }

    fun isTrusted(): Boolean {
        now()
        return integrityFailure == null
    }

    fun failureReason(): String? {
        now()
        return integrityFailure
    }

    fun requireTrustedNow(): Long {
        val trustedNow = now()
        val failure = integrityFailure
        if (failure != null) throw ClockTamperDetectedException(failure)
        return trustedNow
    }

    /**
     * Opportunistically strengthens/recover the trusted anchor using an external HTTPS Date header.
     * Never blocks the UI thread and never makes ordinary offline time depend on continuous internet.
     */
    fun refreshFromHttpsAsync() {
        val context = appContext ?: return
        synchronized(lock) {
            if (httpsRefreshInFlight) return
            httpsRefreshInFlight = true
        }
        thread(name = "fush-trusted-utc", isDaemon = true) {
            try {
                val sample = TrustedUtcClient.fetchUtcMillis() ?: return@thread
                synchronized(lock) {
                    if (lastHttpsSyncWallMs > 0L && sample.utcMillis + WALL_DRIFT_TOLERANCE_MS < lastHttpsSyncWallMs) {
                        return@synchronized
                    }
                    val elapsedNow = rawElapsedRealtime()
                    anchorWallMs = sample.utcMillis
                    anchorElapsedMs = elapsedNow
                    lastHttpsSyncWallMs = sample.utcMillis
                    integrityFailure = null
                    lastPersistElapsedMs = elapsedNow
                    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    prefs.edit()
                        .putLong(KEY_LAST_HTTPS_SYNC_WALL, sample.utcMillis)
                        .putLong(KEY_LAST_TRUSTED_WALL, sample.utcMillis)
                        .apply()
                    persistAnchorLocked(forceLastTrusted = true)
                }
            } finally {
                synchronized(lock) { httpsRefreshInFlight = false }
            }
        }
    }

    private fun detectRuntimeTamperLocked(expectedTrustedWall: Long) {
        if (!initialized || integrityFailure != null) return
        val wallNow = rawWallMillis()
        if (TrustedTimeMath.isWallDriftSuspicious(wallNow, expectedTrustedWall, WALL_DRIFT_TOLERANCE_MS)) {
            integrityFailure = "تم اكتشاف تغيير في تاريخ/ساعة الجهاز أثناء تشغيل التطبيق. صحح الوقت أو اتصل بالإنترنت للتحقق عبر HTTPS."
            refreshFromHttpsAsync()
        }
    }

    private fun maybePersistLocked(trustedNow: Long, elapsedNow: Long) {
        if (!initialized || integrityFailure != null || elapsedNow - lastPersistElapsedMs < PERSIST_INTERVAL_MS) return
        lastPersistElapsedMs = elapsedNow
        val context = appContext ?: return
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putLong(KEY_LAST_TRUSTED_WALL, trustedNow)
            .apply()
    }

    private fun persistAnchorLocked(forceLastTrusted: Boolean) {
        val context = appContext ?: return
        val trustedNow = TrustedTimeMath.monotonicWall(anchorWallMs, anchorElapsedMs, rawElapsedRealtime())
        val edit = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putInt(KEY_BOOT_COUNT, currentBootCount)
            .putLong(KEY_ANCHOR_WALL, anchorWallMs)
            .putLong(KEY_ANCHOR_ELAPSED, anchorElapsedMs)
            .putLong(KEY_BOOT_EPOCH_WALL, rawWallMillis() - rawElapsedRealtime())
            .putLong(KEY_LAST_HTTPS_SYNC_WALL, lastHttpsSyncWallMs)
        if (forceLastTrusted) edit.putLong(KEY_LAST_TRUSTED_WALL, trustedNow)
        edit.apply()
    }

    private fun readBootCount(context: Context): Int = runCatching {
        Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT)
    }.getOrDefault(-1)

    private fun isAutomaticTimeEnabled(context: Context): Boolean = runCatching {
        Settings.Global.getInt(context.contentResolver, Settings.Global.AUTO_TIME, 0) == 1
    }.getOrDefault(false)

    private fun rawElapsedRealtime(): Long = runCatching { SystemClock.elapsedRealtime() }
        .getOrElse { System.nanoTime() / 1_000_000L }

    private fun rawWallMillis(): Long = System.currentTimeMillis()
}

data class TrustedUtcSample(val utcMillis: Long, val source: String, val roundTripMs: Long)

/** Small HTTPS UTC client; no app/server account is required. */
object TrustedUtcClient {
    private const val CONSENSUS_SKEW_MS = 10_000L
    private val endpoints = listOf(
        "https://www.google.com/generate_204",
        "https://www.cloudflare.com/cdn-cgi/trace",
        "https://www.apple.com/library/test/success.html"
    )

    /** Require agreement from at least two independent HTTPS origins before re-anchoring UTC. */
    fun fetchUtcMillis(): TrustedUtcSample? {
        val samples = endpoints.mapNotNull { endpoint -> runCatching { fetch(endpoint) }.getOrNull() }
        return TrustedTimeMath.consensusUtc(samples, minSources = 2, maxSkewMs = CONSENSUS_SKEW_MS)
    }

    internal fun fetch(endpoint: String): TrustedUtcSample? {
        val started = SystemClock.elapsedRealtime()
        val connection = (URL(endpoint).openConnection() as HttpsURLConnection).apply {
            connectTimeout = 3_000
            readTimeout = 3_000
            requestMethod = "GET"
            instanceFollowRedirects = true
            useCaches = false
            setRequestProperty("Cache-Control", "no-cache")
            setRequestProperty("User-Agent", "FUSH-ERP-TrustedTime")
        }
        return try {
            connection.responseCode
            val serverDate = connection.date
            if (serverDate <= 0L) return null
            val ended = SystemClock.elapsedRealtime()
            val rtt = (ended - started).coerceAtLeast(0L)
            TrustedUtcSample(
                utcMillis = TrustedTimeMath.compensateHttpsDate(serverDate, rtt),
                source = endpoint,
                roundTripMs = rtt
            )
        } finally {
            runCatching { connection.inputStream?.close() }
            runCatching { connection.errorStream?.close() }
            connection.disconnect()
        }
    }
}

object TrustedTimeMath {
    fun monotonicWall(anchorWallMs: Long, anchorElapsedMs: Long, currentElapsedMs: Long): Long {
        require(currentElapsedMs >= anchorElapsedMs) { "monotonic clock moved backwards" }
        return anchorWallMs + (currentElapsedMs - anchorElapsedMs)
    }

    fun isWallDriftSuspicious(deviceWallMs: Long, trustedWallMs: Long, toleranceMs: Long): Boolean =
        abs(deviceWallMs - trustedWallMs) > toleranceMs

    fun compensateHttpsDate(serverDateMs: Long, roundTripMs: Long): Long {
        require(serverDateMs > 0L) { "HTTPS server date must be positive" }
        require(roundTripMs >= 0L) { "round trip must be non-negative" }
        return serverDateMs + (roundTripMs / 2L)
    }

    fun consensusUtc(
        samples: List<TrustedUtcSample>,
        minSources: Int = 2,
        maxSkewMs: Long = 10_000L
    ): TrustedUtcSample? {
        require(minSources >= 2) { "trusted UTC consensus needs at least two sources" }
        require(maxSkewMs >= 0L) { "max skew must be non-negative" }
        if (samples.size < minSources) return null

        val sorted = samples.sortedBy { it.utcMillis }
        var best: List<TrustedUtcSample> = emptyList()
        for (start in sorted.indices) {
            val cluster = sorted.drop(start).takeWhile { it.utcMillis - sorted[start].utcMillis <= maxSkewMs }
            if (cluster.size > best.size) best = cluster
        }
        if (best.size < minSources) return null

        val utcValues = best.map { it.utcMillis }.sorted()
        val consensus = if (utcValues.size % 2 == 1) {
            utcValues[utcValues.size / 2]
        } else {
            val hi = utcValues.size / 2
            utcValues[hi - 1] + (utcValues[hi] - utcValues[hi - 1]) / 2L
        }
        return TrustedUtcSample(
            utcMillis = consensus,
            source = best.joinToString("+") { it.source },
            roundTripMs = best.maxOf { it.roundTripMs }
        )
    }
}

class ClockTamperDetectedException(message: String) : SecurityException(message)
