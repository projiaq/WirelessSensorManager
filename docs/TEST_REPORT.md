# 测试报告

日期：2026-08-06。

## 自动化测试内容

- 接收器命令逐字节组帧、严格响应长度/版本/未知命令检查
- 小端有符号 Offset 与 Sensor Info V2 解析
- 固件公开 CRC16-Modbus 向量
- RS485 半包、连续多包、错误 CRC 后重同步
- opcode + slot 请求响应匹配
- Fake BLE 扫描与连接状态 READY/DISCONNECTED
- Room 内存数据库的操作记录和绑定关系回读
- 配置写入逐字节回读一致与不一致
- 绑定成功、重复、满槽、解绑和超时
- ViewModel 扫描结果到 StateFlow 的状态传播

配置写回读和绑定协调逻辑在生产代码中执行强制比较；仍需补充更多隔离 Repository/ViewModel 单测。

## 本次执行状态

按用户指示，本机未执行 Gradle 编译、单元测试、Lint 或 APK 构建。上述任务由 `.github/workflows/android.yml` 在 GitHub Actions 执行，因此当前不能声称：自动化测试通过、模拟 BLE 测试通过、Lint 通过或 APK 构建通过。

CI 成功后的 APK artifact 名为 `WirelessSensorManager-debug`，仓库内构建路径为 `app/build/outputs/apk/debug/app-debug.apk`。尚未进行任何真实蓝牙硬件测试；真机范围见 `DEVICE_TEST_CHECKLIST.md`。
