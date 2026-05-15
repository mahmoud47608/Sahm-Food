package com.example.sahmfood.sync

import com.example.sahmfood.data.MockBackend
import com.example.sahmfood.domain.PosRepository
import com.example.sahmfood.domain.SyncReport
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock


class SyncManager(
    private val repo: PosRepository,
    private val backend: MockBackend,
) {

    private val mutex = Mutex()


    suspend fun syncNow(): SyncReport = mutex.withLock {
        val report = repo.trySyncPending(backend::submitOrder)
        if (!report.isIdle) {
            Napier.i { "[Sync] $report" }
        }
        report
    }

    fun startBackgroundLoop(scope: CoroutineScope): Job = scope.launch {
        var delayMs = MIN_DELAY_MS
        while (isActive) {
            delay(delayMs)
            val report = runCatching { syncNow() }.getOrElse {
                Napier.e(it) { "[Sync] Loop crashed, will back off" }
                null
            }
            delayMs = nextDelay(current = delayMs, report = report)
        }
    }

    private fun nextDelay(current: Long, report: SyncReport?): Long = when {
        report == null -> (current * 2).coerceAtMost(MAX_DELAY_MS)
        report.attempted > 0 && report.succeeded == 0 ->
            (current * 2).coerceAtMost(MAX_DELAY_MS)
        else -> MIN_DELAY_MS
    }

    companion object {
        private const val MIN_DELAY_MS = 5_000L
        private const val MAX_DELAY_MS = 60_000L
    }
}
