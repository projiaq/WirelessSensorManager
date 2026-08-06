# 架构说明

单一 `app` module，按职责分包。数据流固定为 Compose 页面 → `MainViewModel` → `DeviceRepository` / `BindingCoordinator` → `BleTransport` / Room。

- `core/ble`：Android 原生 GATT 与测试 Fake；扫描超时、服务验证、MTU、CCCD、串行操作、资源释放。
- `core/protocol`：真实 UUID、接收器命令、严格响应解析、传感器字段和独立 RS485 审计解析器。
- `core/database`：Room、DataStore、Android Keystore 初始化能力。
- `feature/binding`：设备回读优先的绑定/解绑协调。
- `core/design` 与 `MainActivity`：Material 3 中文工业现场 UI、Navigation Compose。

Android 层默认仅有一个活动连接。连接对象由 Hilt 单例 Transport 持有，不依赖页面生命周期。
