package com.example.wirelesssensormanager

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.wirelesssensormanager.core.database.SettingsStore
import com.example.wirelesssensormanager.core.ble.FakeBleTransport
import com.example.wirelesssensormanager.core.ota.OtaService
import com.example.wirelesssensormanager.feature.binding.BindingCoordinator
import com.example.wirelesssensormanager.feature.binding.StubRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MainViewModelTest {
    private lateinit var dispatcher: TestDispatcher
    @Before fun setup() { dispatcher = StandardTestDispatcher(); Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun `scan result flows into view model state`() = runTest(dispatcher) {
        val repo = StubRepository()
        val vm = MainViewModel(repo, BindingCoordinator(repo), SettingsStore(ApplicationProvider.getApplicationContext<Context>()), OtaService(FakeBleTransport()))
        val collection = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect() }
        vm.scan()
        advanceUntilIdle()
        assertEquals("AIOT_0011_#001", vm.state.value.devices.single().name)
        collection.cancel()
    }
}
