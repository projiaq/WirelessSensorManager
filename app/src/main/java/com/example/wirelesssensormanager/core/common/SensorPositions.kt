package com.example.wirelesssensormanager.core.common

data class SensorPosition(val type: Int, val sensorId: Int, val label: String)

object SensorPositions {
    private val pressure = listOf("立柱前左", "立柱前右", "立柱后左", "立柱后右", "一级护帮", "二级护帮", "三级护帮", "前梁", "伸缩梁", "平衡上腔", "平衡下腔")
    private val tilt = listOf("底座", "顶梁", "前连杆", "一级护帮", "二级护帮", "三级护帮", "前梁", "尾梁", "掩护梁")
    val all: List<SensorPosition> = buildList {
        pressure.forEachIndexed { i, name -> add(SensorPosition(1, i + 1, "压力-$name")) }
        (12..21).forEach { add(SensorPosition(1, it, "压力-备用压力${it - 11}")) }
        tilt.forEachIndexed { i, name -> add(SensorPosition(2, i + 1, "倾角-$name")) }
        (10..19).forEach { add(SensorPosition(2, it, "倾角-备用倾角${it - 9}")) }
    }
    fun label(type: Int, id: Int): String = all.firstOrNull { it.type == type && it.sensorId == id }?.label ?: "类型$type-点位$id"
}
