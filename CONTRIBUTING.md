# 参与贡献

感谢你愿意帮助改进“不玩了”。

## 提交问题

一般 Bug 和功能建议请使用 GitHub Issues。兼容性问题建议附上：

- 手机品牌和型号；
- Android 版本及厂商系统版本；
- 目标应用名称和版本；
- 无障碍、通知、自启动和省电设置状态；
- 最小复现步骤和预期/实际结果。

上传截图或日志前，请删除账号、设备标识、通知内容、聊天信息和本地路径等个人数据。安全漏洞不要公开提交，改按 [SECURITY.md](SECURITY.md) 报告。

## 提交代码

1. Fork 仓库并从 `main` 创建主题分支。
2. 保持改动聚焦，必要时补充或更新 JVM 单元测试。
3. 提交前执行：

```bash
./gradlew testCommunityDebugUnitTest testLegacyBridgeDebugUnitTest lintCommunityDebug assembleCommunityDebug assembleLegacyBridgeDebug
```

4. 在 Pull Request 中说明问题、实现方式、测试结果和兼容性影响。

首次提交 Pull Request 前需阅读并签署 [CLA.md](CLA.md)。贡献代码仍会在本仓库按 GPL-3.0 发布；CLA 同时允许项目维护者在商业版中使用贡献，避免社区版与商业版无法同步修复。请勿提交签名密钥、服务令牌、真实用户数据或第三方闭源代码。
