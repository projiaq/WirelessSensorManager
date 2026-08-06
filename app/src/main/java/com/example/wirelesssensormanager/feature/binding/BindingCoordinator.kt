package com.example.wirelesssensormanager.feature.binding

import com.example.wirelesssensormanager.core.common.*
import com.example.wirelesssensormanager.core.repository.DeviceRepository
import javax.inject.Inject

sealed interface BindingResult {
    data class Success(val slot: BindingSlot) : BindingResult
    data class Rejected(val reason: String) : BindingResult
}

class BindingCoordinator @Inject constructor(private val repository: DeviceRepository) {
    suspend fun bind(targetSlot: Int, identity: SensorIdentity): BindingResult {
        if (targetSlot !in 0..7) return BindingResult.Rejected("槽位必须为 1 至 8")
        val before = repository.readBindingTable()
        before.firstOrNull { it.occupied && it.mac.contentEquals(identity.mac) }?.let {
            return BindingResult.Rejected("该传感器已绑定在槽位 ${it.index + 1}")
        }
        if (before.all { it.occupied }) return BindingResult.Rejected("8 个槽位均已占用")
        if (before[targetSlot].occupied) return BindingResult.Rejected("指定槽位已占用")
        val response = repository.setBinding(targetSlot, identity)
        if (response.status != 0) return BindingResult.Rejected(responseMessage(response.status))
        val actual = repository.readBindingTable()[targetSlot]
        return if (actual.sensorType == identity.type && actual.sensorId == identity.sensorId && actual.mac.contentEquals(identity.mac)) BindingResult.Success(actual)
        else BindingResult.Rejected("设备回复成功，但绑定表回读不一致")
    }

    suspend fun unbind(slot: Int): BindingResult {
        if (slot !in 0..7) return BindingResult.Rejected("槽位必须为 1 至 8")
        val response = repository.clearBinding(slot)
        if (response.status != 0) return BindingResult.Rejected(responseMessage(response.status))
        val actual = repository.readBindingTable()[slot]
        return if (!actual.occupied) BindingResult.Success(actual) else BindingResult.Rejected("设备回复成功，但解绑后槽位仍被占用")
    }
    private fun responseMessage(status: Int) = com.example.wirelesssensormanager.core.protocol.ReceiverProtocol.statusMessage(status)
}
