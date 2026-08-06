# 固件 BLE 协议审查

审查日期：2026-08-06。结论来自源码和 GATT 配置，不依据 BIN/HEX 字符串猜测。固件目录在本工程外，保持只读。

## 1. 固件与构建系统

| 角色 | 路径 | 构建依据 |
|---|---|---|
| 接收器/网关 | `../bt_multicentral_multiperipheral_dual_topology_revice_bg22/` | Silicon Labs `.slcp`，Gecko SDK 4.5.0，主逻辑 `app.c` |
| 压力/倾角传感器 | `../bt_soc_press_bg22/` | Silicon Labs `.slcp`，通过 `sensor_select.h:11` 的 `SENSOR_TYPE_TILT` 选择倾角构建；注释该符号为压力构建 |

## 2. 广播与设备识别

- 接收器完整名称为 `AIOT_%02X%02X_#%03u`，由身份地址末两字节和 Receiver ID 组成。依据：接收器 `app.c:387-419`，符号 `update_receiver_device_name()`。
- 接收器 GATT 配置把自定义 Service 标为可广播，但运行时 `update_receiver_device_name()` 用固定 19 字节 Flags + Complete Name 覆盖主广播（接收器 `app.c:409-419`），该数据不含 Service UUID，也未见 Scan Response。实际扫描不能使用 Service UUID 硬过滤；只能先用 `AIOT_` 名称识别候选，再在连接后严格验证 Service/Characteristic。
- 传感器名称为 `PRES-XXXX` 或 `TILT-XXXX`。依据：传感器 `app.c:144-183`，符号 `set_ble_device_name()`。
- 传感器广播 Sensor Data Service。依据：传感器 `config/btconf/gatt_configuration.btconf:78`。
- 两端均未实现厂商自定义广播数据。类型识别优先使用 Service UUID；名称只作兼容提示。连接传感器后以 Sensor Info 偏移 15 的类型再确认。

## 3. GATT 行为

完整表见 `BLE_UUID_TABLE.md`。接收器命令支持 Write 和 Write Without Response；固件同时处理 attribute value 与 user write request，依据接收器 `app.c:2356-2377`。App 默认使用 Write Request，以获得 ATT 层确认。

Notify 特征均有标准 CCCD `0x2902`。接收器在 CCCD 开启后推送状态/缓存数据，依据接收器 `app.c:2320-2351`；传感器监听 CCCD 状态，依据传感器 `app.c:296-303`。未发现 Indicate。

接收器在连接手机时仍作为 Central 连接传感器；手机不需要同时连接两者完成接收器写表。App 首版保持一个活动手机 GATT 会话，先读取传感器身份、断开，再连接接收器。

## 4. 接收器手机协议

- 版本：`PHONE_CFG_VERSION=1`；命令 1..7。依据：接收器 `app.c:87-94`。
- 命令是单个 Characteristic value，没有帧头、长度、流水号或 CRC。依据：`handle_phone_config()`，接收器 `app.c:1549-1628`。
- 响应固定 15 字节；成功的 GET_SENSOR_INFO 为 39 字节。依据：`send_phone_config_response()`，接收器 `app.c:1518-1546`。
- 多字节整数为小端。Receiver ID 解析见接收器 `app.c:1559-1560`。
- 状态码：0 成功、1 请求非法、2 NVM 保存失败、3 设备/操作不可用。依据：接收器 `app.c:1551-1627` 各响应分支。
- 协议没有事务号。App 以 opcode + slot 关联在途请求和 Notify 响应，并串行发送命令。

Receiver Status 为 8 字节：`version,id_le16,boundBitmap,onlineBitmap,validBitmap,slotCount,fastBitmap`。依据：接收器 `app.c:1482-1515`。

## 5. 传感器协议与编码

Sensor Info 为 18 字节：

| 偏移 | 类型 | 含义 | 依据 |
|---:|---|---|---|
| 0 | LE uint32 | 累计运行秒 | 传感器 `app.c:811-823` |
| 4 | LE uint32 | 本次启动秒 | 同上 |
| 8 | LE uint16 | 电压 mV | `app.c:827-828` |
| 10 | uint8 | I2C 传感器在线 | `app.c:829` |
| 11 | uint8 | 速率 0/1 | `app.c:830` |
| 12 | LE uint16 | 饱和错误计数 | `app.c:831-832` |
| 14 | uint8 | Info 版本，当前 2 | `app.c:53-54,833` |
| 15 | uint8 | 1 压力，2 倾角 | `app.c:834-838` |
| 16 | uint8 | PA5 电平 | `app.c:839-841` |
| 17 | uint8 | 功耗模式 0/1 | `app.c:842` |

可写真实参数仅有：Offset（LE int32）、Data Rate（0 慢、1 快、2 进入 EM4）、Power Mode（0 普通、1 低功耗）和 MAC。依据：传感器 `app.c:524-610`。MAC 写入导致立即复位，首版 UI 不开放该高风险入口。

Sensor Data 长 17 字节。压力布局见传感器 `app_pressure.c:173-187`；倾角使用 X/Y 角度 LE int16（0.1 度）及 X/Y/Z 原始值，构建符号 `tilt_build_payload()`。共同偏移 14..15 为序号，16 为 PA5/readOk/电压位域，定义见传感器 `app.c:68-71`。

## 6. MTU、分包与时序

- Sensor Data 17 字节可放入默认 ATT payload 20 字节。
- Receiver Response 最大 39 字节，默认 MTU 23 不足。固件没有应用层分片头。App 请求 MTU 247；若 MTU 不足，39 字节 Notify 是否由目标手机/栈完整交付必须真机确认。
- Android GATT callback 给出特征值边界，不存在串口式粘包。App 不臆造 BLE 分包协议。
- 接收器自身传感器连接和 GATT procedure 超时均为 10 秒，依据接收器 `app.c:68-76`。App 命令超时取 10 秒，连接超时 15 秒，扫描 12 秒，有限重试为 0（写操作避免重复副作用）。

## 7. 安全与持久化

自定义特征均标注 `authenticated=false, bonded=false, encrypted=false`，未发现密码、密钥、随机数或应用层加密。绑定表与 Receiver ID 存在接收器 NVM3，结构见接收器 `app.c:188-193`，键见 `app.c:35-37`。传感器不保存“属于哪个接收器”的关系。

## 8. 两端一致性

接收器内置的四个传感器 UUID 数组与传感器 GATT 配置一致：接收器 `app.c:195-217` 对照传感器 `gatt_configuration.btconf:78-131`。数据长度 17、Info 长度 18、速率 0/1/2 也一致。未发现 CRC、认证或版本定义冲突。

压力工程单位仍不一致：传感器计算公式在 `app_pressure.c:145-159` 产生乘 10 整数，但旧手机参考实现显示为 bar。App 保存/显示原始整数及“固件原始单位”，不擅自换算。
