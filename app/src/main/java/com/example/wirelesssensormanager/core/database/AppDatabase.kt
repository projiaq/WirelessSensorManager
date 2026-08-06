package com.example.wirelesssensormanager.core.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "devices")
data class DeviceEntity(
    @PrimaryKey val address: String, val name: String?, val type: String,
    val deviceId: Int?, val lastSeen: Long, val firmwareVersion: String? = null,
    val hardwareVersion: String? = null, val protocolVersion: Int? = null
)

@Entity(tableName = "bindings", primaryKeys = ["receiverAddress", "slot"])
data class BindingEntity(
    val receiverAddress: String, val slot: Int, val sensorType: Int,
    val sensorId: Int, val sensorMac: String, val online: Boolean,
    val verifiedAt: Long
)

@Entity(tableName = "config_snapshots")
data class ConfigSnapshotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0, val deviceAddress: String,
    val key: String, val value: String, val capturedAt: Long
)

@Entity(tableName = "operations")
data class OperationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0, val timestamp: Long,
    val deviceAddress: String?, val action: String, val detail: String,
    val success: Boolean
)

@Entity(tableName = "device_versions")
data class DeviceVersionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0, val deviceAddress: String,
    val hardware: String?, val firmware: String?, val protocol: String?, val readAt: Long
)

@Entity(tableName = "communication_errors")
data class CommunicationErrorEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0, val timestamp: Long,
    val deviceAddress: String?, val state: String, val reason: String
)

@Entity(tableName = "diagnostic_logs")
data class DiagnosticLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0, val timestamp: Long,
    val deviceType: String, val deviceAddress: String?, val deviceId: String?,
    val connectionState: String, val commandName: String, val direction: String,
    val hexData: String, val durationMs: Long?, val result: String, val error: String?
)

@Dao
interface AppDao {
    @Upsert suspend fun upsertDevice(value: DeviceEntity)
    @Upsert suspend fun upsertBindings(values: List<BindingEntity>)
    @Query("DELETE FROM bindings WHERE receiverAddress = :address") suspend fun clearBindings(address: String)
    @Insert suspend fun insertSnapshot(value: ConfigSnapshotEntity)
    @Insert suspend fun insertOperation(value: OperationEntity)
    @Insert suspend fun insertVersion(value: DeviceVersionEntity)
    @Insert suspend fun insertError(value: CommunicationErrorEntity)
    @Insert suspend fun insertLog(value: DiagnosticLogEntity)
    @Query("SELECT * FROM operations ORDER BY timestamp DESC LIMIT 500") fun operations(): Flow<List<OperationEntity>>
    @Query("SELECT * FROM diagnostic_logs ORDER BY timestamp DESC LIMIT 2000") fun logs(): Flow<List<DiagnosticLogEntity>>
    @Query("SELECT * FROM bindings WHERE receiverAddress = :address ORDER BY slot") fun bindings(address: String): Flow<List<BindingEntity>>
}

@Database(
    entities = [DeviceEntity::class, BindingEntity::class, ConfigSnapshotEntity::class,
        OperationEntity::class, DeviceVersionEntity::class, CommunicationErrorEntity::class,
        DiagnosticLogEntity::class],
    version = 1, exportSchema = true
)
abstract class AppDatabase : RoomDatabase() { abstract fun dao(): AppDao }
