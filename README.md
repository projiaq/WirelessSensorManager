# WirelessSensorManager

“无线传感器配置工具”是 Android 原生 BLE Central/GATT Client，用于扫描、连接和配置仓库中的 EFR32BG22 接收器与压力/倾角传感器，并管理接收器最多 8 个真实绑定槽位。App 运行时完全离线，不申请 Internet 权限。

> 当前包名 `com.example.wirelesssensormanager` 是临时包名，发布前必须确认正式企业包名。

## 打开与 SDK

用 Android Studio 直接打开本目录 `WirelessSensorManager/`，等待 Gradle Sync。要求 JDK 17 或更高、Android SDK Platform 35、Build Tools 35.x；最低运行 Android 8.0/API 26。

## 构建

Windows PowerShell：

```powershell
cd WirelessSensorManager
./gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

Linux/macOS：

```bash
cd WirelessSensorManager
chmod +x gradlew
./gradlew testDebugUnitTest lintDebug assembleDebug
```

Debug APK 输出：`app/build/outputs/apk/debug/app-debug.apk`。

GitHub Actions 位于 `.github/workflows/android.yml`。把本目录作为 GitHub 仓库根目录提交后，push、PR 或手动触发都会运行单测、Android Lint、Debug 构建，并上传 APK 与报告 artifact。

## 真机安装

连接已开启 USB 调试的 Android 手机：

```powershell
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Android 12 及以上需授予“附近设备”的扫描和连接权限；Android 8～11 的 BLE 扫描由系统要求定位权限。App 不把 BLE 扫描结果用于定位。

## 使用流程

1. 在环境检查页授权蓝牙权限。
2. 扫描并连接传感器，读取类型/信息；在绑定向导选择现场测点编号并暂存身份。
3. 返回扫描页连接接收器，读取 8 个设备槽位。
4. 在绑定向导选择空槽；App 写入后重新读取并逐字段验证。
5. 参数配置同样在写入后 Read 回来，一致才报告成功。

通信诊断页显示命令、方向、十六进制和错误，右上角下载图标通过系统分享面板导出 UTF-8 CSV。日志、设备、配置与操作历史仅保存在 Room 本地数据库。

## 文档

- `docs/FIRMWARE_PROTOCOL_AUDIT.md`：固件审查和代码追溯
- `docs/BLE_UUID_TABLE.md` / `BLE_COMMAND_TABLE.md`：UUID 与命令
- `docs/BINDING_PROTOCOL.md`：绑定事实和验证流程
- `docs/ARCHITECTURE.md` / `DATABASE.md`：架构与数据库
- `docs/OPEN_QUESTIONS.md` / `KNOWN_ISSUES.md`：未确认项和限制
- `docs/TEST_REPORT.md` / `DEVICE_TEST_CHECKLIST.md`：自动化范围与真机清单
