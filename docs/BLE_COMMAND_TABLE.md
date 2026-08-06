# BLE 命令表

所有槽位在线路上为 0..7，UI 显示 1..8；Receiver ID 为小端。命令/响应没有 CRC 和事务号。

| opcode | 方向 | 请求 | 成功确认 | 固件符号 |
|---:|---|---|---|---|
| 1 | 手机→接收器 | `01 01 idLo idHi` | 15 B Response，status=0 | `PHONE_CFG_SET_ID`，接收器 `app.c:88,1559-1564` |
| 2 | 手机→接收器 | `01 02 slot type sensorId mac[6]` | 15 B，回读 opcode 5 | `PHONE_CFG_SET_SLOT`，`:89,1564-1578` |
| 3 | 手机→接收器 | `01 03 slot` | 15 B，回读 opcode 5 | `PHONE_CFG_CLEAR_SLOT`，`:90,1579-1586` |
| 4 | 手机→接收器 | `01 04` | 15 B | `PHONE_CFG_CLEAR_ALL`，`:91,1587-1596` |
| 5 | 手机→接收器 | `01 05 slot` | 15 B 绑定项 | `PHONE_CFG_GET_SLOT`，`:92,1597-1600` |
| 6 | 手机→接收器 | `01 06 slot rate` | 15 B；rate 0/1/2 | `PHONE_CFG_SET_RATE`，`:93,1601-1606` |
| 7 | 手机→接收器 | `01 07 slot` | 成功 39 B | `PHONE_CFG_GET_SENSOR_INFO`，`:94,1607-1621` |

基础响应：`version,opcode,status,slot,receiverIdLE,type,sensorId,mac[6],online`。GET_SENSOR_INFO 成功时追加 24 字节 Receiver Info。

传感器参数不是 opcode 帧：直接 Read/Write 对应 Characteristic。Offset 为 LE int32；速率为 0/1/2；Power 为 0/1。写入必须等待 ATT Write Response，再 Read 同一特征并逐字节比较。
