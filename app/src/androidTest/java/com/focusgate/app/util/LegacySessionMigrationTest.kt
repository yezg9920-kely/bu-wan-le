package com.focusgate.app.util

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.focusgate.app.Constants
import com.focusgate.app.data.AppSession
import com.focusgate.app.data.IntentType
import com.focusgate.app.data.MoodType
import com.focusgate.app.data.local.BuwanleDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LegacySessionMigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val prefs by lazy {
        context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
    }

    @Before
    fun setUp() {
        BuwanleDatabase.get(context).clearAllTables()
        prefs.edit().clear().commit()
    }

    @After
    fun tearDown() {
        BuwanleDatabase.get(context).clearAllTables()
        prefs.edit().clear().commit()
    }

    @Test
    fun legacyJsonMigratesExactlyOnceWithoutLoss() {
        val legacy = AppSession(
            packageName = "com.xingin.xhs",
            startTime = 1_700_000_000_000,
            endTime = 1_700_000_060_000,
            plannedDurationMinutes = 10,
            intentType = IntentType.RELAX,
            mood = MoodType.TIRED
        )
        prefs.edit().putString("sessions", SessionJsonCodec.encode(listOf(legacy))).commit()

        assertEquals(listOf(legacy), SessionStorage(context).getAllSessions())
        assertEquals(listOf(legacy), SessionStorage(context).getAllSessions())
        assertEquals(true, prefs.getBoolean("sessions_room_migrated_v1", false))
    }
}
