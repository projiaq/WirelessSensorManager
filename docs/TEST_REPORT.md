# 测试报告

日期：2026-08-06

## 本次自动化结果

- `testDebugUnitTest`：通过，22 项测试，0 失败，0 跳过。
- `lintDebug`：通过。
- `assembleDebug`：通过。
- Debug APK：`app/build/outputs/apk/debug/app-debug.apk`。

覆盖内容包括接收器命令、严格响应解析、Sensor Info、V1/V2 实时采样、二维码 MAC 提取、CRC16-Modbus、半包/多包/坏帧、Fake BLE、Room、参数回读、绑定成功/重复/满槽/超时/解绑、8 槽差异同步以及 ViewModel 状态传播。

## 验证边界

本次结果属于本机自动化测试、模拟 BLE 测试、Lint 和 APK 构建，不代表真实硬件验证。完整 OTA、DFU 广播原地址、MAC 写入复位、EM4、掉线重连、100 次绑定/解绑仍须使用真实接收器和传感器执行 `DEVICE_TEST_CHECKLIST.md`。
