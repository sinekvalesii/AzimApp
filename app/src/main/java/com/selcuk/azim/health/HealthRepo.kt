package com.selcuk.azim.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.selcuk.azim.data.HealthDay
import java.time.LocalDate
import java.time.Period

enum class HcState { UNAVAILABLE, NEEDS_UPDATE, NO_PERMISSION, READY }

/** Health Connect erişimi: yalnızca adım ve nabız OKUR, hiçbir veri yazmaz. */
class HealthRepo(private val ctx: Context) {
    private val client get() = HealthConnectClient.getOrCreate(ctx)

    private suspend fun granted(): Set<String> = client.permissionController.getGrantedPermissions()

    suspend fun state(): HcState = runCatching {
        when (HealthConnectClient.getSdkStatus(ctx)) {
            HealthConnectClient.SDK_UNAVAILABLE -> HcState.UNAVAILABLE
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HcState.NEEDS_UPDATE
            else -> if (STEPS in granted()) HcState.READY else HcState.NO_PERMISSION
        }
    }.getOrDefault(HcState.UNAVAILABLE)

    /** Son [n] günün günlük adım toplamı ve ortalama nabzı. */
    suspend fun lastDays(n: Long = 7): List<HealthDay> = runCatching {
        val g = granted()
        val metrics = buildSet {
            if (STEPS in g) add(StepsRecord.COUNT_TOTAL)
            if (HEART in g) add(HeartRateRecord.BPM_AVG)
        }
        if (metrics.isEmpty()) return emptyList()
        val end = LocalDate.now().plusDays(1).atStartOfDay()
        client.aggregateGroupByPeriod(
            AggregateGroupByPeriodRequest(metrics, TimeRangeFilter.between(end.minusDays(n), end), Period.ofDays(1))
        ).map {
            HealthDay(
                it.startTime.toLocalDate().toString(),
                it.result[StepsRecord.COUNT_TOTAL] ?: 0L,
                if (HEART in g) it.result[HeartRateRecord.BPM_AVG] else null
            )
        }
    }.getOrDefault(emptyList())

    companion object {
        val STEPS = HealthPermission.getReadPermission(StepsRecord::class)
        val HEART = HealthPermission.getReadPermission(HeartRateRecord::class)
        val PERMS = setOf(STEPS, HEART)
    }
}
