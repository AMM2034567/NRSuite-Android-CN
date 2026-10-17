![NRSuite Banner](screenshots/banner.png)

# NRSuite-Android-CN（简体中文汉化版）

> 📢 **说明**：本项目是 [NRSuite (Android)](https://github.com/7wp81x/nrsuite-android) 的**简体中文本地化（汉化）版本**。所有界面文本、操作提示、系统状态及日志均已完成高质量中文本地化翻译，严格保留与原项目相同的 [MIT 开源许可证](./LICENSE)。

NRSuite 是一款配合定制 ESP32 固件使用的 Android 伴侣端应用，旨在将 Wi-Fi 与低功耗蓝牙（BLE）安全测试与无线攻防工具链带入高效、便携的手机工作流中——无需携带笔记本电脑。

只需通过 USB-OTG（串口）将 Android 手机连接到烧录了 NRSuite 固件的 ESP32 开发板，即可将其作为便携式 Wi-Fi / BLE 攻防与无线安全检测平台。

> ⚠️ **仅供授权测试使用**：NRSuite 仅用于经过授权的安全研究与渗透测试，严禁用于未授权设备或恶意用途。详情参见 [LEGAL.md](./LEGAL.md)。

---

## 原版项目及生态

| 项目 | 说明 |
|---|---|
| [原版 Android 客户端 (7wp81x/nrsuite-android)](https://github.com/7wp81x/nrsuite-android) | 原版英文客户端仓库 |
| [ESP32 固件 (nrsuite-firmware)](https://github.com/7wp81x/nrsuite-firmware) | ESP32 固件源码（PlatformIO / ESP-IDF） |
| [通信协议规范 (nrsuite-protocol)](https://github.com/7wp81x/nrsuite-protocol) | 串口通讯帧协议规范与文档 |

---

## 功能特性

<details open>
<summary><strong>无线安全测试（Wi-Fi / 802.11）</strong></summary>

- [x] **Wi-Fi 扫描** - AP 与信道探测，显示 SSID、BSSID、RSSI、加密方式与 WPS 标识
- [x] **信标伪造 (Beacon Spam)** - 广播自定义或隐藏 SSID
- [x] **解除认证 (Deauthentication)** - 注入 Deauth 管理帧
- [x] **钓鱼热点 (Evil Twin)** - 伪造流氓 AP 并挂载强制认证门户
- [x] **强制认证门户 (Captive Portal)** - 自定义 HTML 钓鱼捕获页面
- [x] **数据包嗅探 (Packet Sniffer)** - 混杂模式监听捕获，支持导出 PCAP 文件
- [x] **隐藏 AP 探测 (Hidden AP Revealer)** - 被动监听隐藏 AP，结合探测请求候选列表与关联帧还原 SSID
- [ ] **PMKID 捕获**

</details>

<details open>
<summary><strong>无线防御与环境检测</strong></summary>

- [x] **Deauth 洪水检测** - 被动监听 Deauth / Disassoc 帧，支持 BSSID、客户端、RSSI 及原因过滤，实时告警并记录日志
- [x] **伪造 AP 检测器 (Rogue AP)** - 比对周围可见 AP 与 OUI 规则，标记同名 SSID 仿冒、安全降级等可疑热点
- [x] **客户端探针检测 (Client Presence)** - 被动探测 Probe / Assoc / Reassoc / Auth 管理帧，支持主动触发重连探测
- [x] **追踪器检测 (Tracker Detector)** - 检测周围类似 Apple Find My / AirTag 的 BLE 寻物防丢器广播
- [ ] **Karma / MANA 攻击检测**
- [ ] **未授权 RFID 读卡器检测**
- [ ] **干扰检测 (Jam Detector)**（仅检测）

</details>

<details open>
<summary><strong>蓝牙（BLE）</strong></summary>

- [x] **BLE 扫描器** - 发现周围低功耗蓝牙设备、RSSI、广播厂商数据及服务 UUID
- [x] **BLE GATT 属性画像** - 枚举分析 BLE 设备的只读 GATT 服务与特征值
- [x] **BLE HID** - 模拟蓝牙键盘鼠标并执行 DuckyScript 脚本
- [ ] **FastPair 模型识别**

</details>

<details open>
<summary><strong>HID 与 BadUSB</strong></summary>

- [x] **DuckyScript 编辑器** - 创建、导入和导出 Hak5 DuckyScript 脚本
- [x] **BadUSB** - 通过 USB 模拟 HID 键盘输入载荷
- [x] **多设备 USB 会话** - 支持同时连接多个 USB 串口设备独立管理与交互
- [x] **BadUSB 目标选择器** - 随时切换注入目标设备而不中断其他通讯连接

</details>

<details open>
<summary><strong>凭据与握手包</strong></summary>

- [x] **WPA 握手包捕获与离线爆破** - 4-way 握手包捕获、校验与本地字典离线验证
- [x] **凭据管理器** - 本地安全存储与管理捕获到的无线凭据

</details>

<details open>
<summary><strong>存储、固件与辅助工具</strong></summary>

- [x] **板载存储管理 (Mass Storage)** - 浏览、删除 ESP32 Flash 中的文件，支持 USB MSC 模式
- [x] **手机端固件烧录 (ESP32 Flasher)** - 直接在手机上通过 USB-OTG 烧录 NRSuite 固件，无需电脑
- [x] **通用串口监视器 (Serial Monitor)** - 支持连接任意 ESP32 或 USB-UART 设备进行原生串口监视与调试
- [x] **离线 MAC 厂商查询** - 内置离线 OUI 数据库查询 MAC 所属硬件厂商
- [x] **固件设备持久标识** - 支持读取并显示设备的 NVS 标识（`NRxxxxxxx`），方便重连识别

</details>

---

## 界面预览

| 首页仪表盘 | 功能模块目录 | 运行日志 |
|---|---|---|
| ![首页](screenshots/home.jpg) | ![功能目录](screenshots/module-catalog.jpg) | ![日志](screenshots/logs-screen.jpg) |

| 设备连接管理 | Wi-Fi 扫描 | 数据包嗅探器 |
|---|---|---|
| ![设备管理](screenshots/device-manager.jpg) | ![WiFi扫描](screenshots/wifi-scan.jpg) | ![嗅探器](screenshots/packet-sniffer.jpg) |

| Deauth 攻击检测 | 伪造 AP 检测器 | BLE 扫描器 |
|---|---|---|
| ![Deauth检测](screenshots/deauth-detector.jpg) | ![Rogue AP](screenshots/rogue-ap.jpg) | ![BLE扫描](screenshots/ble-scanner.jpg) |

| BadUSB 注入 | 凭据管理器 | 固件烧录器 |
|---|---|---|
| ![BadUSB](screenshots/badusb.jpg) | ![凭据管理](screenshots/credential-manager.jpg) | ![固件烧录](screenshots/firmware-flasher.jpg) |

---

## 安装与快速上手

### 1. 下载 APK
进入本仓库的 [Releases 页面](../../releases) 下载最新版本的 `nrsuite-cn-vX.X.X.apk` 并安装到手机。

### 2. 硬件准备与连接
1. 准备一块兼容的 ESP32 开发板（推荐 ESP32-S2 或 ESP32-S3 等支持原生 USB 的型号）。
2. 将 NRSuite 固件烧录至 ESP32（可以使用本 App 内置的**固件烧录器**直接刷入，或在电脑上使用 PlatformIO 烧录）。
3. 使用 USB-OTG 转接线/线缆将 ESP32 连接到手机。
4. 打开 NRSuite-Android-CN，授予 USB 权限后即可开始使用。

### 3. 环境与系统要求
- **Android 系统版本**：Android 8.0 (API 26) 及以上
- **硬件支持**：手机必须支持 USB-OTG（USB Host 模式）

---

## 从源码编译

```sh
git clone https://github.com/AMM2034567/NRSuite-Android-CN.git
cd NRSuite-Android-CN
./gradlew assembleDebug
```

编译输出目录：`app/build/outputs/apk/debug/app-debug.apk`

---

## 开源许可证

本项目遵循与原项目完全相同的 [MIT License](./LICENSE)。

本项目仅供学习、测试及已获得授权的安全评估使用。开发者不对任何因不当使用造成的损失承担责任。详见 [LEGAL.md](./LEGAL.md)。
