package com.aka.alarm.model

import com.aka.alarm.Tuning
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class AlarmScheduleTest {

    // The 5-minute minimum lead — matches the original hardcoded behaviour.
    private val minLead = Tuning.baselineWindow.inWholeMinutes.toInt()

    private val windowMillis = Tuning.DEFAULT_WAKE_WINDOW_MINUTES * 60_000L

    private fun epoch(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        Calendar.getInstance().apply {
            set(year, month, day, hour, minute, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    @Test
    fun baselineStart_minimumLeadIsOneBaselineWindowBeforeWindowStart() {
        val start = epoch(2026, Calendar.AUGUST, 13, 8, 0)
        assertEquals(
            start - Tuning.baselineWindow.inWholeMilliseconds,
            AlarmSchedule.baselineStartMillis(start, minLead),
        )
    }

    @Test
    fun baselineStart_configurableLeadSubtractsFromWindowStart() {
        val start = epoch(2026, Calendar.AUGUST, 13, 8, 0)
        assertEquals(
            epoch(2026, Calendar.AUGUST, 13, 7, 0),
            AlarmSchedule.baselineStartMillis(start, 60),
        )
        assertEquals(
            epoch(2026, Calendar.AUGUST, 13, 0, 0),
            AlarmSchedule.baselineStartMillis(start, 480),
        )
    }

    @Test
    fun baselineStart_immediateLeadIsAlwaysInThePast() {
        val start = epoch(2026, Calendar.AUGUST, 13, 8, 0)
        assertEquals(Long.MIN_VALUE, AlarmSchedule.baselineStartMillis(start, -1))
    }

    @Test
    fun baselineStart_leadBelowMinimumClampsToBaselineWindow() {
        val start = epoch(2026, Calendar.AUGUST, 13, 8, 0)
        assertEquals(
            start - Tuning.baselineWindow.inWholeMilliseconds,
            AlarmSchedule.baselineStartMillis(start, 1),
        )
    }

    @Test
    fun initialPhase_armedWhenWellBeforeBaseline() {
        val start = epoch(2026, Calendar.AUGUST, 13, 8, 0)
        val end = start + windowMillis
        val now = epoch(2026, Calendar.AUGUST, 12, 22, 0)
        assertTrue(AlarmSchedule.initialPhase(now, start, end, minLead) is AlarmPhase.Armed)
    }

    @Test
    fun initialPhase_immediateLeadNeverArms() {
        val start = epoch(2026, Calendar.AUGUST, 13, 8, 0)
        val end = start + windowMillis
        val now = epoch(2026, Calendar.AUGUST, 12, 22, 0)
        assertTrue(AlarmSchedule.initialPhase(now, start, end, -1) is AlarmPhase.Monitoring)
    }

    @Test
    fun initialPhase_oneHourLeadMonitorsWithinTheHour() {
        val start = epoch(2026, Calendar.AUGUST, 13, 8, 0)
        val end = start + windowMillis
        val justInsideLead = epoch(2026, Calendar.AUGUST, 13, 7, 30)
        val justOutsideLead = epoch(2026, Calendar.AUGUST, 13, 6, 30)
        assertTrue(
            AlarmSchedule.initialPhase(justInsideLead, start, end, 60) is AlarmPhase.Monitoring
        )
        assertTrue(
            AlarmSchedule.initialPhase(justOutsideLead, start, end, 60) is AlarmPhase.Armed
        )
    }

    @Test
    fun initialPhase_monitoringDuringBaselineInterval() {
        val start = epoch(2026, Calendar.AUGUST, 13, 8, 0)
        val end = start + windowMillis
        val now = epoch(2026, Calendar.AUGUST, 13, 7, 58)
        assertTrue(AlarmSchedule.initialPhase(now, start, end, minLead) is AlarmPhase.Monitoring)
    }

    @Test
    fun initialPhase_monitoringAtBaselineStartBoundary() {
        val start = epoch(2026, Calendar.AUGUST, 13, 8, 0)
        val end = start + windowMillis
        val now = AlarmSchedule.baselineStartMillis(start, minLead)
        assertTrue(AlarmSchedule.initialPhase(now, start, end, minLead) is AlarmPhase.Monitoring)
    }

    @Test
    fun initialPhase_inWindowAfterStart() {
        val start = epoch(2026, Calendar.AUGUST, 13, 8, 0)
        val end = start + windowMillis
        val now = epoch(2026, Calendar.AUGUST, 13, 8, 5)
        assertTrue(AlarmSchedule.initialPhase(now, start, end, minLead) is AlarmPhase.InWindow)
    }

    @Test
    fun initialPhase_inWindowAtWindowStartBoundary() {
        val start = epoch(2026, Calendar.AUGUST, 13, 8, 0)
        val end = start + windowMillis
        assertTrue(AlarmSchedule.initialPhase(start, start, end, minLead) is AlarmPhase.InWindow)
    }

    @Test
    fun computeWakeWindow_rollsToNextDayWhenWindowPassed() {
        val now = epoch(2026, Calendar.AUGUST, 13, 9, 0)
        val (start, end) = AlarmSchedule.computeWakeWindow(now, 8, 0, 30)
        assertEquals(epoch(2026, Calendar.AUGUST, 14, 8, 0), start)
        assertEquals(
            epoch(2026, Calendar.AUGUST, 14, 8, 0) + windowMillis,
            end,
        )
    }

    @Test
    fun computeWakeWindow_fifteenMinuteWindowEndsFifteenMinutesLater() {
        val now = epoch(2026, Calendar.AUGUST, 13, 6, 0)
        val (start, end) = AlarmSchedule.computeWakeWindow(now, 8, 0, 15)
        assertEquals(epoch(2026, Calendar.AUGUST, 13, 8, 0), start)
        assertEquals(epoch(2026, Calendar.AUGUST, 13, 8, 15), end)
    }

    @Test
    fun computeWakeWindow_fifteenMinuteWindowRollsOnlyOnceItHasPassed() {
        // 08:20 is inside a 30-minute window starting 08:00 but past a 15-minute one.
        val now = epoch(2026, Calendar.AUGUST, 13, 8, 20)
        assertEquals(
            epoch(2026, Calendar.AUGUST, 13, 8, 0),
            AlarmSchedule.computeWakeWindow(now, 8, 0, 30).first,
        )
        assertEquals(
            epoch(2026, Calendar.AUGUST, 14, 8, 0),
            AlarmSchedule.computeWakeWindow(now, 8, 0, 15).first,
        )
    }
}
