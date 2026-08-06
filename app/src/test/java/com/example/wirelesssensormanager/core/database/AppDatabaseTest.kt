package com.example.wirelesssensormanager.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppDatabaseTest {
    private lateinit var db: AppDatabase
    @Before fun setup() { db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java).allowMainThreadQueries().build() }
    @After fun close() = db.close()
    @Test fun `operation and binding round trip`() = runTest {
        db.dao().insertOperation(OperationEntity(timestamp = 1, deviceAddress = "AA", action = "绑定", detail = "槽位 1", success = true))
        db.dao().upsertBindings(listOf(BindingEntity("AA", 0, 1, 2, "11:22:33:44:55:66", true, 2)))
        assertEquals("绑定", db.dao().operations().first().single().action)
        assertEquals(2, db.dao().bindings("AA").first().single().sensorId)
    }
}
