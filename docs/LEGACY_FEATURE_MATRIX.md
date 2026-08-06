# Legacy Feature Matrix

对照来源：

- `bt_multicentral_multiperipheral_dual_topology_revice_bg22/mobile_app/lib`
- `bt_multicentral_multiperipheral_dual_topology_revice_bg22/phone_app`
- `simplicity-connect-android/mobile/.../OtaProgressDialog.kt` 与 GATT 队列实现

## 已确认的旧端能力

| 领域 | 旧端实际行为 | 当前 Android 状态 |
|---|---|---|
| 扫描 | 接收器/压力/倾角分类；名称、Service UUID、RSSI；搜索、排序；二维码填入 MAC；最近设备 | 已实现 |
| 站点 | 客户/维护模式；维护密码；井下高对比模式；维护模式限制危险操作 | 已实现；密码仅保存 SHA-256 摘要 |
| 接收器 | 状态、Receiver ID、8 槽；绑定类型/点位/MAC；单槽清除、清空全部；每槽 Sensor Info；槽位快慢速 | 已实现 |
| 实时数据 | Receiver Stream 17 字节负载；压力/倾角格式化；温度、电压、序号、原始值；按槽刷新 | 已实现 V1/V2 电压兼容、质量状态和 watchdog |
| 传感器 | 直连实时数据；零点 X/Y 持久化；偏移；慢速 1s/快速 100ms；普通/低功耗；EM4；MAC 写入后重启 | 已实现；MAC/EM4 按固件预期断链处理 |
| OTA | 本地 GBL、HTTP URL；进入 DFU；断开；扫描 OTA 广播；恢复原设备；版本回读；失败块禁止重发；日志复制 | 已实现；仍需真实设备验证广播地址匹配 |
| 连接恢复 | 连接/服务发现/订阅；指数重连；数据停滞 watchdog；页面返回自动恢复 | 已实现最多 5 次有限重连 |
| 数据持久化 | 最近接收器/传感器、站点模式、零点、绑定编辑缓存、槽位点位缓存、OTA 日志 | Room 基础设备/绑定/配置/日志；缺站点、零点、点位缓存、OTA 记录 |
| 诊断 | 收发十六进制、耗时、错误；OTA 日志复制；敏感字段脱敏 | BLE 诊断 CSV 已有；缺 OTA 专项日志和敏感数据规则 |

## 必须继续实现的高风险行为

1. OTA 不能把普通连接直接当作 DFU 完成。必须复刻旧端 `waitForOtaDevice` / `waitForNormalDevice`：按原地址、OTA 名称、OTA Service UUID 和厂商广播中的原地址匹配，超时必须失败。
2. `WRITE_NO_RESPONSE` 数据块失败不可重发；需要记录失败块偏移并终止升级。当前 GATT 层对无响应写只等待固定延迟，尚缺 Android 回调级失败检测。
3. 维护模式必须限制 EM4、功耗、MAC、Receiver ID、清空全部和 OTA；客户模式仍允许只读和实时数据。
4. Receiver Stream 和 Sensor Data 的协议版本、V1/V2 电压位定义必须依据 Sensor Info.version 选择，不能固定按 V2 解码。
5. 绑定编辑器提交前要把本地缓存与设备真实表 diff，只下发变化槽位，完成后逐槽回读；本地缓存不能覆盖设备状态。

## 仍需真实硬件确认

- Android `ScanRecord` 的 AD type `0x1B` 是否与现场 DFU 固件始终携带原地址。
- MAC 写入和 EM4 命令后的实际断链时序。
- OTA Control `0x00/0xEE/0x03` 与量产 bootloader 版本兼容性。
- 压力单位沿用旧端显示，最终工程单位仍需产品定义确认。

本文件是后续实现验收基准；“已加入基础功能”不等同于与旧端完全等价。
