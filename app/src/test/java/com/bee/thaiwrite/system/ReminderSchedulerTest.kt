package com.bee.thaiwrite.system

import java.time.Duration
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class ReminderSchedulerTest {
    @Test
    fun `computeNextReminderDelay uses same day when target is still ahead`() {
        val delay = computeNextReminderDelay(
            now = LocalDateTime.of(2026, 5, 4, 9, 15, 0),
            hour = 19,
            minute = 0,
        )

        assertEquals(Duration.ofHours(9).plusMinutes(45), delay)
    }

    @Test
    fun `computeNextReminderDelay rolls to next day when target already passed`() {
        val delay = computeNextReminderDelay(
            now = LocalDateTime.of(2026, 5, 4, 19, 15, 0),
            hour = 19,
            minute = 0,
        )

        assertEquals(Duration.ofHours(23).plusMinutes(45), delay)
    }

    @Test
    fun `computeNextReminderDelay rolls to next day when target equals now`() {
        val delay = computeNextReminderDelay(
            now = LocalDateTime.of(2026, 5, 4, 19, 0, 0),
            hour = 19,
            minute = 0,
        )

        assertEquals(Duration.ofDays(1), delay)
    }
}
