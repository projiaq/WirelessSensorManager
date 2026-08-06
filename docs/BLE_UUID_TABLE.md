# BLE UUID 表

| 设备 | Service / Characteristic | UUID | 属性与长度 | 固件位置 |
|---|---|---|---|---|
| 接收器 | Receiver Service | `6F8A0000-7D4B-4A8E-9B21-3C5D7E9F1000` | Primary，广播 | 接收器 `gatt_configuration.btconf:78` |
| 接收器 | Command | `6F8A0001-7D4B-4A8E-9B21-3C5D7E9F1000` | Write / Write No Response，<=20 | 同文件 `:79-85` |
| 接收器 | Response | `6F8A0002-7D4B-4A8E-9B21-3C5D7E9F1000` | Read / Notify，<=39，CCCD | `:86-92` |
| 接收器 | Sensor Stream | `6F8A0003-7D4B-4A8E-9B21-3C5D7E9F1000` | Notify，18，CCCD | `:93-98` |
| 接收器 | Status | `6F8A0004-7D4B-4A8E-9B21-3C5D7E9F1000` | Read / Notify，8，CCCD | `:99-105` |
| 传感器 | Data Service | `C8E21E04-2D3A-4C65-B9D6-8E1A4B0F2C7D` | Primary，广播 | 传感器 `gatt_configuration.btconf:78` |
| 传感器 | Data | `C8E21E05-2D3A-4C65-B9D6-8E1A4B0F2C7D` | Notify，17，CCCD | `:81-86` |
| 传感器 | Config Service | `D6E1F204-3A1B-4C72-B9A5-5E1F6C308D2A` | Primary | `:90` |
| 传感器 | Offset | `D6E1F205-3A1B-4C72-B9A5-5E1F6C308D2A` | Read / Write，4 | `:93-99` |
| 传感器 | Data Rate | `D6E1F206-3A1B-4C72-B9A5-5E1F6C308D2A` | Read / Write，1 | `:102-108` |
| 传感器 | MAC | `D6E1F207-3A1B-4C72-B9A5-5E1F6C308D2A` | Write，7 | `:111-116` |
| 传感器 | Sensor Info | `D6E1F208-3A1B-4C72-B9A5-5E1F6C308D2A` | Read，18 | `:119-124` |
| 传感器 | Power Mode | `D6E1F209-3A1B-4C72-B9A5-5E1F6C308D2A` | Read / Write，1 | `:127-133` |
| 两者 | Device Information | `0000180A-0000-1000-8000-00805F9B34FB` | 标准 Read 特征 | 两套 `gatt_configuration.btconf` |

CCCD 为标准 UUID `00002902-0000-1000-8000-00805F9B34FB`，Notify 写值 `01 00`。无 Indicate。
