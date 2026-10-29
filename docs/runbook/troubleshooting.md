# 运行排障清单

## Docker daemon 不可用

- 现象：`docker version` 显示客户端信息后报 `dockerDesktopLinuxEngine` 不存在。
- 原因：Docker Desktop 未启动或 Linux Engine 未就绪。
- 检查命令：`docker version`。
- 处理步骤：执行 `Start-Process 'C:\Program Files\Docker\Docker\Docker Desktop.exe' -WindowStyle Hidden`，等待后重试。
- 是否影响比赛演示：影响真实闭环；可切换前端 mock 演示。

## Docker 镜像拉取慢

- 现象：`docker compose up -d` 长时间停留在 pulling。
- 原因：网络或镜像源较慢。
- 检查命令：`docker compose -p weak-signal ps`。
- 处理步骤：提前在比赛机器执行 `docker compose -p weak-signal --env-file .env pull`，确认镜像已缓存。
- 是否影响比赛演示：首次部署会影响；已缓存后不影响。

## PostgreSQL 容器不 healthy

- 现象：`weak-signal-postgres` 状态为 starting 或 unhealthy。
- 原因：数据库启动慢、密码配置不一致或端口冲突。
- 检查命令：`docker logs weak-signal-postgres`、`docker compose -p weak-signal ps`。
- 处理步骤：确认 `.env` 中 `POSTGRES_*` 一致，确认 5432 未被本机其他 PostgreSQL 占用。
- 是否影响比赛演示：影响后端启动和历史数据。

## Redis 密码错误

- 现象：后端 Redis 连接失败，或 `redis-cli` 返回认证错误。
- 原因：`.env` 与容器启动时使用的 `REDIS_PASSWORD` 不一致。
- 检查命令：`docker exec weak-signal-redis redis-cli -a <本机密码> --no-auth-warning ping`。
- 处理步骤：统一 `.env` 后执行 `.\scripts\stop-infrastructure.ps1` 再 `.\scripts\start-infrastructure.ps1`。
- 是否影响比赛演示：影响实时状态，但前端 mock 可兜底。

## EMQX MQTT 用户未初始化

- 现象：backend 或 simulator 日志显示 MQTT 认证失败。
- 原因：EMQX Dashboard API 未就绪或 MQTT 用户未创建。
- 检查命令：`docker logs weak-signal-emqx`、`.\scripts\start-infrastructure.ps1`。
- 处理步骤：重新执行 `.\scripts\start-infrastructure.ps1`，脚本会创建或确认 MQTT 用户。
- 是否影响比赛演示：影响 MQTT 实流。

## backend 8080 端口占用

- 现象：后端启动失败，提示 8080 already in use。
- 原因：旧后端进程未退出。
- 检查命令：`Get-NetTCPConnection -LocalPort 8080`。
- 处理步骤：执行 `.\scripts\stop-all.ps1`，或手动停止占用端口的 Java 进程。
- 是否影响比赛演示：影响 API 模式。

## simulator 无法连接 MQTT

- 现象：模拟器日志显示连接 `127.0.0.1:1883` 失败。
- 原因：EMQX 未启动、账号错误或端口被占用。
- 检查命令：`docker compose -p weak-signal ps`、`docker exec weak-signal-emqx emqx ctl clients list`。
- 处理步骤：确认 EMQX healthy，确认 `.env` 中 `EMQX_USERNAME` 和 `EMQX_PASSWORD` 与后端一致。
- 是否影响比赛演示：影响实时遥测。

## 前端 CORS 报错

- 现象：浏览器控制台出现 CORS blocked。
- 原因：后端 `BACKEND_ALLOWED_ORIGIN` 与前端 preview 地址不一致。
- 检查命令：查看 `.env` 中 `BACKEND_ALLOWED_ORIGIN`。
- 处理步骤：将其设置为当前前端地址，例如 `http://127.0.0.1:4173`，重启后端。
- 是否影响比赛演示：影响 API 模式。

## 前端仍显示 mock 数据

- 现象：后端数据变化但前端不变。
- 原因：构建时 `VITE_DATA_SOURCE` 仍为 `mock`。
- 检查命令：重新执行前端构建命令并确认环境变量。
- 处理步骤：设置 `$env:VITE_DATA_SOURCE='api'` 后执行 `npm run build` 与 `npm run preview`。
- 是否影响比赛演示：不影响 mock 兜底，但影响真实闭环展示。

## Maven 本地仓库路径错误

- 现象：Maven 依赖下载失败或路径乱码。
- 原因：未使用项目内 `maven-settings.xml`。
- 检查命令：`mvn.cmd -s .\maven-settings.xml -version`。
- 处理步骤：统一使用 `mvn.cmd -s ..\maven-settings.xml test` 或项目脚本。
- 是否影响比赛演示：影响构建，不影响已启动服务。

## Java 版本不是 21

- 现象：构建报 release 21 不支持。
- 原因：当前 `JAVA_HOME` 指向 Java 8 或其他版本。
- 检查命令：`java -version`、`mvn.cmd -version`。
- 处理步骤：安装 JDK 21，设置 `JAVA21_HOME` 或 `JDK21_HOME`，脚本会在进程内切换。
- 是否影响比赛演示：影响后端和模拟器启动。

## `power-trend` 接口异常

- 现象：总览趋势图加载失败。
- 原因：接口时间参数为空、后端未启动或数据库无遥测。
- 检查命令：`Invoke-RestMethod -Uri http://127.0.0.1:8080/api/v1/dashboard/power-trend`。
- 处理步骤：确认后端运行，启动模拟器产生遥测后刷新页面。
- 是否影响比赛演示：影响趋势图，但不影响故障实验核心链路。

## 浏览器页面空白

- 现象：打开前端后空白。
- 原因：前端构建失败、preview 未启动或浏览器缓存旧资源。
- 检查命令：`npm run build`、`Invoke-WebRequest -Uri http://127.0.0.1:4173/`。
- 处理步骤：重新构建并启动 preview，必要时换端口并刷新浏览器缓存。
- 是否影响比赛演示：影响前端展示。

## Windows 中文路径显示乱码但构建可运行

- 现象：Maven 或日志中 `弱信号` 显示为乱码。
- 原因：控制台编码显示问题。
- 检查命令：观察构建是否实际成功。
- 处理步骤：优先看退出码和 `BUILD SUCCESS`；如需改善显示，使用 PowerShell 7 和 UTF-8 终端。
- 是否影响比赛演示：通常不影响运行。
