# 贡献说明

感谢关注本项目。项目当前由侯博洋独立维护，贡献会根据项目目标、代码质量和验证结果逐项审核。

## 提交 Issue

提交前请先搜索已有 Issue，避免重复报告。问题报告请尽量包含：

- 操作系统、JDK、Maven、Node.js、npm 和 Docker Desktop 版本；
- 可复现问题的步骤、实际结果和预期结果；
- 相关日志、运行 ID 和最小复现数据；
- 脱敏后的配置和截图。

请勿在 Issue、提交记录或代码中公开密码、令牌、私钥、真实设备凭据或未授权的现场数据。

## 提交功能建议

功能建议请说明要解决的实际问题、目标使用场景、预期行为以及对现有接口和数据的影响。涉及控制安全、资源约束或运行证据的改动，应同时说明验证方法和适用边界。

## 提交代码

1. 从 `main` 分支创建独立分支，分支名使用 `feature/` 或 `fix/` 前缀。
2. 保持改动范围清晰，不提交 `.env`、真实密钥、构建产物、临时文件或未经授权的数据。
3. 更新受影响的接口契约、运行说明或测试；不要用页面展示结果代替执行证据。
4. 在 Windows PowerShell 环境完成以下检查：

   ```powershell
   Set-Location backend
   mvn.cmd -s ..\maven-settings.xml test
   Set-Location ..\simulator
   mvn.cmd -s ..\maven-settings.xml test
   Set-Location ..\frontend
   npm ci
   npm run type-check
   npm run build
   ```

5. 提交拉取请求时，说明改动目的、影响范围、验证命令和未覆盖的边界。单人维护者会在确认需求、代码和验证结果后决定是否合并。

## 许可证

本项目源码按根目录 `LICENSE` 中的 MIT License 发布。项目使用的第三方组件、模型、数据和工具仍受其各自许可证、服务条款和授权范围约束，使用前请分别核对。
