# 正式签名操作说明

正式密钥决定 Android 后续版本能否覆盖升级。一旦发布，不要更换密钥，也不要把密钥或密码提交到 Git。

## 创建和保管

在离线且可信的电脑上使用 Android Studio 的 **Generate Signed Bundle / APK** 创建 `buwanle-release.jks`。建议：

- 有效期至少 30 年；
- 使用独立随机的 keystore 密码和 key 密码；
- 将密钥、密码和恢复说明分别制作两份离线备份；
- 记录证书 SHA-256，并在每次发布前核对；
- 社区 GitHub 测试版、华为商业版应分别确定唯一签名策略，发布后不得混用。

## 本地/CI 注入

构建脚本只从环境变量读取密钥，不会读取仓库内密码文件：

```powershell
$env:BUWANLE_KEYSTORE_PATH = 'D:\secure\buwanle-release.jks'
$env:BUWANLE_KEYSTORE_PASSWORD = '<keystore password>'
$env:BUWANLE_KEY_ALIAS = 'buwanle'
$env:BUWANLE_KEY_PASSWORD = '<key password>'
.\gradlew.bat bundleCommunityRelease
```

未设置完整的四个变量时，Gradle 仍可生成未签名 release 产物，但该产物不能提交应用市场。

验证：

```powershell
& "$env:ANDROID_HOME\build-tools\36.0.0\apksigner.bat" verify --verbose --print-certs .\app\build\outputs\apk\community\release\app-community-release.apk
```

不要在命令历史、Issue、日志或截图里公开密码。
