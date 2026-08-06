# 绑定协议

## 事实边界

接收器最多 8 槽，依据接收器 `app.c:26` 的 `SENSOR_COUNT=8`。绑定项为 `sensor_type + sensor_id + bd_addr`，依据 `app.c:143-147`；表保存在接收器 NVM，依据 `app.c:188-193,478-538`。传感器固件没有接收器 ID 或绑定表，因此同一传感器在协议上可以被多个接收器配置，无法由传感器阻止。

## App 流程

1. 连接传感器，读取 Sensor Info 的真实类型；BLE 身份地址作为真实 MAC。
2. 用户选择非零 `sensor_id`。固件未提供出厂测点编号，App 不伪造自动读取结果。
3. 断开传感器并连接接收器；读取 8 个 GET_SLOT 响应。
4. 检查同 MAC 重复、目标槽占用和满槽。
5. 写 SET_SLOT，等待 opcode/slot 匹配且 status=0 的真实 Notify。
6. 再次 GET_SLOT；type、sensor_id、MAC 全部一致才保存本地记录并显示成功。

解绑先显示二次确认，发送 CLEAR_SLOT 后 GET_SLOT，只有目标槽为空才成功。本地 Room 仅保存镜像和审计记录，永远不覆盖设备回读。

接收器 SET_SLOT 会关闭旧连接、替换槽并保存 NVM，不要求接收器重启：接收器 `app.c:1572-1577,1627-1630`。接收器随后扫描并连接目标传感器。
