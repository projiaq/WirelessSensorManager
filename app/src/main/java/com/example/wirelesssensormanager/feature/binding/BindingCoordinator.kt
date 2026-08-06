package com.example.wirelesssensormanager.feature.binding

import com.example.wirelesssensormanager.core.common.*
import com.example.wirelesssensormanager.core.repository.DeviceRepository
import javax.inject.Inject

sealed interface BindingResult {
    data class Success(val slot: BindingSlot) : BindingResult
    data class Rejected(val reason: String) : BindingResult
}

class BindingCoordinator @Inject constructor(private val repository: DeviceRepository) {
    suspend fun synchronize(desired: List<SensorIdentity?>): List<BindingSlot> {
        require(desired.size == 8) { "绑定编辑表必须包含 8 个槽位" }
        val duplicate = desired.filterNotNull().groupBy { it.displayMac }.values.firstOrNull { it.size > 1 }
        require(duplicate == null) { "绑定编辑表中存在重复传感器 ${duplicate?.firstOrNull()?.displayMac}" }
        val before = repository.readBindingTable()
        desired.forEachIndexed { index, target ->
            val current = before[index]
            val same = target != null && current.sensorType == target.type && current.sensorId == target.sensorId && current.mac.contentEquals(target.mac)
            if (!same && current.occupied) check(repository.clearBinding(index).status == 0) { "清除槽位 ${index + 1} 失败" }
            if (!same && target != null) check(repository.setBinding(index, target).status == 0) { "写入槽位 ${index + 1} 失败" }
        }
        val actual = repository.readBindingTable()
        desired.forEachIndexed { index, target ->
            val value = actual[index]
            check(if (target == null) !value.occupied else value.sensorType == target.type && value.sensorId == target.sensorId && value.mac.contentEquals(target.mac)) { "槽位 ${index + 1} 回读不一致" }
        }
        return actual
    }
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
