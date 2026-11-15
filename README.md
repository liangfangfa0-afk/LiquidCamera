# LiquidCamera

Android 原生相机应用，采用 Liquid Glass（液态玻璃）视觉方向。

- **版本：** 1.0.0
- **Application ID：** `com.ljj.LiquidCamera`
- **技术栈：** Kotlin、Jetpack Compose、CameraX
- **最低系统：** Android 8.0（API 26）

## 1.0.0 起步功能

- CameraX 实时后置摄像头预览
- 拍照并保存到系统照片媒体库
- 闪光灯开关（硬件支持时）
- 变焦控制
- 曝光补偿
- 点击预览画面对焦/测光
- Compose 液态玻璃风格控件

录像、前后摄切换、相册浏览、RAW 和更完整的手动控制将在后续版本迭代。

## 本地编译

需要 Android Studio、JDK 17 和 Android SDK Platform 35。

1. 克隆仓库，在 Android Studio 中打开项目根目录。
2. 等待 Gradle Sync；按提示安装 Android SDK Platform 35。
3. 连接 Android 设备或启动模拟器。
4. 执行 `./gradlew assembleDebug`，或使用 Android Studio 的 Build > Make Project。

Windows 使用 `gradlew.bat assembleDebug`。

Debug APK：`app/build/outputs/apk/debug/app-debug.apk`

## 权限策略

首版仅申请 CAMERA 权限。照片通过 MediaStore 保存，不申请旧式外部存储权限。当前没有录像/录音功能，因此不申请麦克风权限。后续仅在实际加入对应功能时申请所需权限。

## 许可

仓库沿用初始化时已有的 GNU GPL v3 许可证。第三方依赖遵循各自上游许可证。
