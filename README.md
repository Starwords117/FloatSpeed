# 悬浮测速 · FloatSpeed

把实时 GPS 速度做成一枚悬浮在**任意 App 之上**的小窗。开车导航、骑行看码表时，速度常驻可见，无需切回测速应用。

> Android 8.0+（API 26）· Kotlin · Jetpack Compose · 前台服务（location）· 不联网

## 下载

已签名的 APK 见 [Releases](https://github.com/Starwords117/FloatSpeed/releases/latest)。下载后允许「安装未知来源应用」即可侧载安装，或使用：

```bash
adb install -r FloatSpeed-v0.1.0.apk
```

## 功能

- **悬浮速度窗**：半透明深色胶囊，仅显示速度数字与单位，默认约 120 × 64 dp
- **任意 App 之上**：基于 `TYPE_APPLICATION_OVERLAY`，覆盖地图、导航、音乐等应用
- **跟手拖拽**：松手落位并记忆屏幕坐标，重启后恢复；双击悬浮窗打开控制台
- **实时刷新**：≥ 1 Hz，控制台退出后台、被划掉后仍持续测速
- **速度口径**：GPS 原始速度优先、位移差分兜底；双 α 滤波 + 零锁 + 归零门限，整数显示
- **信号状态**：正常白字、搜星/丢失 `--` 灰字、精度差黄字、定位权限失效 `--` 红字
- **单位切换**：`km/h` / `mph`，控制台与悬浮窗同步
- **透明度三档**：50% / 70% / 90%
- **屏幕常亮**：驾驶场景默认开启，可关闭
- **可停**：关悬浮即摘窗、撤通知、停服务、释放定位；不保活、不常驻后台

## 界面

控制台是本应用唯一 Activity，负责权限引导、悬浮开关与设置：

```
┌────────────────────────────────┐
│ 悬浮测速                       │
│ 🟢 信号良好 · 精度 6m  🛰 18   │
│                                │
│              62                │
│             km/h               │
│                                │
│ 悬浮测速            [开关]     │
│ 单位            [ km/h | mph ] │
│ 透明度        [低] [中] [高]   │
│ 屏幕常亮            [开关]     │
└────────────────────────────────┘
```

悬浮窗只有速度读数，不含信号点、按钮或图标；所有状态通过数字颜色与 `--` 表达。

## 构建

环境要求：

- JDK 17
- Android SDK Platform 37（`compileSdk = 37` / `targetSdk = 37`）
- Android Studio（或命令行 Gradle）

```bash
# 指向本机 Android SDK
echo "sdk.dir=/path/to/Android/Sdk" > local.properties

# 构建 Debug APK
./gradlew assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk

# 运行单元测试（domain 算法 + 会话引用计数）
./gradlew test
```

安装到已开启 USB 调试的设备：

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## 使用

1. 首次启动授予定位权限（建议精确位置）
2. 打开「悬浮测速」开关，按引导授予「显示悬浮窗」权限
3. 返回控制台，悬浮窗出现；可拖到任意位置
4. 双击悬浮窗回到控制台；也可从常驻通知点「关闭」停止悬浮

HyperOS / MIUI 建议在系统设置中把本应用的省电策略设为「无限制」并允许自启动，以提高后台定位稳定性。部分 MIUI 版本锁屏后会隐藏悬浮窗，属系统限制，应用不承诺锁屏显示。

## 隐私

- **不声明 `INTERNET` 权限**，没有任何网络请求与数据上传
- 位置信息仅在内存中参与速度计算，不落盘、不记录经纬度
- 持久化的只有设置项与悬浮窗的**屏幕坐标**，不包含地理位置
- 权限清单：`ACCESS_FINE_LOCATION`、`ACCESS_COARSE_LOCATION`、`SYSTEM_ALERT_WINDOW`、`POST_NOTIFICATIONS`、`FOREGROUND_SERVICE`、`FOREGROUND_SERVICE_LOCATION`、`ACCESS_BACKGROUND_LOCATION`

## 技术结构

```
app/src/main/java/com/starwords/floatingspeed/
├── MainActivity.kt          单 Activity，权限流程与生命周期持锁
├── App.kt                   进程级单例（settings / session）
├── console/                 控制台 Compose UI、ViewModel、权限页、主题
├── overlay/                 前台服务、悬浮窗、窗口生命周期、权限跳转
├── session/                 SessionManager：引擎粘合、引用计数、tick、StateFlow
├── location/                GPS / GNSS / 定位开关检查
├── data/                    DataStore 设置仓库
├── domain/                  滤波、信号状态机、单位、地理计算（纯 Kotlin）
└── util/                    无坐标标量日志
```

核心设计：

- **单一数据源**：`SessionManager` 统一生产 `SessionSnapshot`，控制台与悬浮窗都只订阅
- **引用计数持锁**：控制台可见与悬浮开启共同持有定位引擎，归零即停，保证关悬浮后 5 秒内停止定位
- **算法零改动移植**：滤波、信号状态机、单位换算等 domain 层来自前作《实时GPS速度》，46 项 JVM 单测覆盖
- **可见时启动前台服务**：规避 Android 15 对后台启动 FGS 的限制
- **独立窗口生命周期**：`OverlayLifecycleOwner` 为 `WindowManager` 中的 `ComposeView` 补齐 Lifecycle / ViewModelStore / SavedStateRegistry 三个 owner

更多细节见 [需求文档](需求文档.md) 与 [详细设计](详细设计.md)。

## 许可证

本项目以 **GNU General Public License v3.0** 开源，见 [LICENSE](LICENSE)。

Copyright (C) 2026 Starwords117

你可以自由使用、修改和分发本软件，但分发衍生作品时须同样以 GPL-3.0 授权并提供源代码。
