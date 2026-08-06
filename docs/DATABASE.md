# 数据库说明

Room 文件：`wireless-sensors.db`，schema version 1。

| 表 | 用途 |
|---|---|
| `devices` | 接收器/传感器身份、最后发现时间、版本镜像 |
| `bindings` | 按接收器地址和槽位保存最近一次设备回读 |
| `config_snapshots` | 参数写入并回读后的快照 |
| `operations` | 连接、绑定、解绑、参数修改记录 |
| `device_versions` | 历次版本读取 |
| `communication_errors` | 状态和错误原因 |
| `diagnostic_logs` | 收发方向、原始十六进制、耗时、结果 |

DataStore 保存简单开关。密钥材料不得进入这些表；`SecureStore` 使用 Android Keystore AES/GCM 密钥。当前固件没有认证密钥。
