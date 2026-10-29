# 弱信号覆盖下新能源场站虚拟电厂分布式协调控制及监控平台

本仓库提供项目源码、测试、Windows 启动脚本，以及软件模拟实验的复核数据。系统以 Java 场站模拟器生成遥测与故障，经 MQTT 进入 Spring Boot 后端，由 Vue 前端展示监控、调度和实验回放。当前验证对象是软件模拟闭环，不代表真实逆变器、储能 EMS 或现场场站验收。

## 目录

- `backend/src`：后端业务、控制算法、REST/MQTT 接口及测试。
- `simulator/src`：场站模拟器及测试。
- `frontend/src`：Vue 前端源码。
- `scripts`：Windows PowerShell 启停、验收与对照实验脚本。
- `docs/contracts`、`docs/runbook`：接口契约和排障说明。
- `docs/reports`：分布式控制测试报告及 2026 年 7 月 30 日的软件模拟实验数据。

## 本地运行

需要 Windows PowerShell、JDK 21、Maven、Node.js、npm 和 Docker Desktop。请先在项目根目录把 `.env.example` 复制为 `.env`，将其中的占位密码改为本地值。`.env` 已被 Git 忽略，不要提交真实密钥。

在项目根目录执行：

```powershell
Copy-Item .env.example .env
.\一键启动.ps1
```

启动后访问 `http://127.0.0.1:4173/`；后端健康检查为 `http://127.0.0.1:8080/api/v1/health`。结束时执行：

```powershell
.\scripts\stop-all.ps1
```

测试与构建：

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

## 实验数据边界

`docs/reports/data/real-control-formal` 保存三种策略、五种场景、五个随机种子的 75 轮软件模拟运行数据和统计结果。报告中的改善比例仅适用于记录的拓扑、故障参数、资源模型及种子，不应外推到真实电网或任意通信条件。

## 开源状态

本次发布尚未附加开源许可证。代码公开可供查看；团队确定版权归属和许可证后，才能明确授予使用、修改和再分发权利。
