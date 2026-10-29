# 比赛运行包的第三方二进制核对

核对日期：2026-10-29。范围是桌面提交用 ZIP 中的 `backend/backend.jar`、`simulator/lib/org.eclipse.paho.mqttv5.client-1.2.5.jar` 和 `frontend/dist`。ZIP 只携带 Compose 配置，不携带 PostgreSQL、Redis 或 EMQX 镜像。仓库的 264 项[依赖元数据清单](dependency-audit.csv)覆盖测试、开发和运行依赖；本页只记录实际运行包内或生产前端锁文件中的组件。

## 已完成的随包证据

| 对象 | 核对结果 | 随包材料 |
| --- | --- | --- |
| 后端 Spring Boot JAR | 内嵌 90 个第三方 JAR；89 个对应依赖清单，另有打包插件加入的 `spring-boot-jarmode-tools`。54 个 JAR 自带许可或 NOTICE 文件，已原样提取；其余 36 个已补许可正文、POM 元数据与来源链接。 | `THIRD_PARTY_LICENSES.csv`、`THIRD_PARTY_NOTICES.md`、`third-party-licenses/maven/` 与 `third-party-licenses/common/`。 |
| 模拟器 JAR | 外置 Paho JAR 与后端内嵌的同版本 JAR 的 SHA-256 相同，清单只计一次。 | 同上。 |
| 前端生产依赖 | 从锁文件识别 65 个生产依赖位置；62 个安装包自带许可文本，已复制。Element Plus 2.11.0 与 Vue Devtools API 6.6.4 的发布包未附许可文件，补入各自对应版本的上游文件。 | `third-party-licenses/npm/`、`third-party-licenses/common/`。 |
| 需提供源码的许可路径 | 对运行包中选用 EPL 或 LGPL 路径且本地可取得的 7 个构件，提供未修改的对应源码 JAR；包括 Hibernate ORM 6.6.26.Final。 | `third-party-sources/`。 |

许可正文来源与文件 SHA-256 见 `third-party-licenses/common/SOURCES.csv`。逐项清单保存在[运行包构件清单](runtime-binary-inventory.csv)；其中同时标明原始许可声明、当前选用路径、随包正文位置和需复核状态。`@popperjs/core` 在 npm 锁文件中是 `@sxzz/popperjs-es@2.11.8` 的别名，清单保留依赖入口并指向实际发布包的许可元数据。

生成命令（Windows PowerShell；先在仓库执行前后端构建及 `npm ci`，并准备尚未加入许可材料的运行包目录）：

```powershell
python .\scripts\build-runtime-notices.py 'C:\path\to\unpacked-runtime'
```

脚本逐项比对实际 JAR 与锁文件；版本或数量变化时会停止，要求重新审查，而不会沿用本次结论。

## 仍需核实

1. 36 个未在 JAR 内嵌许可或 NOTICE 的构件虽有许可正文和 POM 来源，项目级版权声明与潜在附加 NOTICE 仍需逐项复核。`redis-authx-core@0.1.1-beta2` 的 POM 声明 MIT，但其上游 LICENSE 链接未能取得可核验的同版本版权文本。
2. `lodash-unified@1.0.3` 的 npm 发布包声明 MIT，却没有附具体版权文本；随包提供 MIT 正文并明确标记待复核。
3. OR-Tools 9.15 的外层构件声明 Apache-2.0；Windows 原生库可能包含 SCIP、HiGHS 等第三方组件。还需对实际原生库内容和附加声明做专项核对，不能仅凭外层 POM 宣称全部合规。[OR-Tools 9.15 发布说明](https://github.com/google/or-tools/releases/tag/v9.15)、[SCIP 许可说明](https://scipopt.org/)可作为核对入口。
4. 前端清单按生产锁文件与安装包生成，尚未对压缩后的每个 JS/CSS 字节反向归属到具体包。将来更换锁文件或构建配置，应重新生成并复核。

这些材料解决了“运行包没有独立第三方许可和 NOTICE 入口”的问题，但不构成完整法律合规结论。对尚未解决的版权、原生库和打包归属问题，继续由 [Issue #12](https://github.com/houbiyang/ruoxinghao/issues/12) 跟踪；不应在参赛材料中声称已全部审计完成。
