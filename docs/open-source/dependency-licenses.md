# 第三方依赖与许可核对

本页依据仓库中 `backend/pom.xml`、`simulator/pom.xml`、`frontend/package.json`、`frontend/package-lock.json` 和 `docker-compose.yml` 核对依赖。核对日期：2026-10-29。许可证名称来自相应版本的 Maven POM、npm 发布元数据或项目官方许可说明。根目录的 MIT License 只授权本项目自有代码与文档，不替代第三方许可。

完整版本清单见 [`dependency-audit.csv`](dependency-audit.csv)：从两个 Maven 依赖树解析出 129 个唯一构件，从前端锁文件解析出 135 个唯一包版本，共 264 项；每项都有上游许可元数据链接，未发现许可证字段缺失。CSV 保留上游原始许可名称，双重许可条目也保留全部候选许可。本清单覆盖依赖解析结果，不等于对每个二进制包内部版权文件和镜像层完成法律审查。

运行 `scripts/check-dependency-audit.ps1` 可重新解析当前 Maven 依赖树和 npm 锁文件，检查 CSV 是否缺项、重复、过期或缺少许可来源。该脚本已在 Windows PowerShell 本地核对通过；[GitHub Actions 工作流](../../.github/workflows/dependency-audit.yml)在依赖清单或相关配置发生变更的 PR 与 `main` 提交上执行同一检查。脚本只验证清单与当前解析结果一致，不会据此推断许可证义务已全部履行。

## Java 直接依赖

| 组件 | 当前版本或版本来源 | 发布元数据中的许可证 | 用途 |
| --- | --- | --- | --- |
| [OR-Tools Java](https://repo.maven.apache.org/maven2/com/google/ortools/ortools-java/9.15.6755/ortools-java-9.15.6755.pom) | 9.15.6755 | Apache-2.0 | 后端运行依赖 |
| [Spring Boot Starters](https://repo.maven.apache.org/maven2/org/springframework/boot/spring-boot-starter-parent/3.5.5/spring-boot-starter-parent-3.5.5.pom) | 父 POM 3.5.5 管理；使用 web、validation、data-jpa、data-redis、actuator、test | Apache-2.0 | 后端运行与测试依赖 |
| [PostgreSQL JDBC](https://repo.maven.apache.org/maven2/org/postgresql/postgresql/42.7.7/postgresql-42.7.7.pom) | Spring Boot 3.5.5 依赖管理为 42.7.7 | BSD-2-Clause | 后端运行依赖 |
| [Eclipse Paho MQTT v5 Client](https://repo.maven.apache.org/maven2/org/eclipse/paho/java-parent/1.2.5/java-parent-1.2.5.pom) | 1.2.5 | EPL-2.0，见上游父 POM | 后端与模拟器运行依赖 |
| [JUnit Jupiter](https://repo.maven.apache.org/maven2/org/junit/jupiter/junit-jupiter-api/5.10.2/junit-jupiter-api-5.10.2.pom) | 5.10.2 | EPL-2.0 | 模拟器测试依赖 |

Spring Boot 的 PostgreSQL JDBC 版本可在 [3.5.5 依赖管理 POM](https://repo.maven.apache.org/maven2/org/springframework/boot/spring-boot-dependencies/3.5.5/spring-boot-dependencies-3.5.5.pom) 中核对。上表只汇总直接依赖；传递依赖的逐项元数据见 CSV。

## 前端直接依赖

下列版本来自 `frontend/package.json`，许可字段来自对应版本的 npm 发布元数据。

| 组件 | 版本 | npm 许可字段 | 用途 |
| --- | --- | --- | --- |
| [ECharts](https://registry.npmjs.org/echarts/5.6.0) | 5.6.0 | Apache-2.0 | 运行依赖 |
| [Element Plus](https://registry.npmjs.org/element-plus/2.11.0) | 2.11.0 | MIT | 运行依赖 |
| [Pinia](https://registry.npmjs.org/pinia/3.0.3) | 3.0.3 | MIT | 运行依赖 |
| [Vue](https://registry.npmjs.org/vue/3.5.17) | 3.5.17 | MIT | 运行依赖 |
| [Vue Router](https://registry.npmjs.org/vue-router/4.5.1) | 4.5.1 | MIT | 运行依赖 |
| [@types/node](https://registry.npmjs.org/@types/node/24.0.10) | 24.0.10 | MIT | 开发依赖 |
| [@vitejs/plugin-vue](https://registry.npmjs.org/@vitejs/plugin-vue/5.2.4) | 5.2.4 | MIT | 开发依赖 |
| [TypeScript](https://registry.npmjs.org/typescript/5.8.3) | 5.8.3 | Apache-2.0 | 开发依赖 |
| [Vite](https://registry.npmjs.org/vite/5.4.19) | 5.4.19 | MIT | 开发依赖 |
| [vue-tsc](https://registry.npmjs.org/vue-tsc/2.2.12) | 2.2.12 | MIT | 开发依赖 |

`frontend/package-lock.json` 固定了传递依赖版本，逐项 npm 许可字段见 CSV。比赛运行包已按生产锁文件收集许可文本，具体覆盖和剩余缺口见[二进制运行包核对](submission-package-audit.md)；这不等于逐字节确认前端 bundle 的所有第三方代码归属。

## 多许可证条目与分发边界

CSV 保留上游 POM 中的全部许可名称。以下为本项目在当前源码使用场景中拟采用的许可路径；若将对应 JAR 随安装包分发，仍须依据实际文件核对许可证正文、版权声明及必要的 NOTICE，不得仅凭 POM 字段作合规结论。

| 构件 | 上游提供的路径 | 拟选路径 |
| --- | --- | --- |
| `logback-classic`、`logback-core` 1.5.18 | EPL-1.0 或 LGPL | EPL-1.0 |
| `jakarta.annotation-api` 2.1.1、`jakarta.transaction-api` 2.0.1 | EPL-2.0 或 GPL-2.0 with Classpath Exception | EPL-2.0 |
| `jakarta.persistence-api` 3.1.0 | EPL-2.0 或 EDL-1.0 | EDL-1.0 |
| `jna`、`jna-platform` 5.14.0 | LGPL-2.1-or-later 或 Apache-2.0 | Apache-2.0 |
| `HdrHistogram` 2.2.2 | CC0 或 BSD-2-Clause | BSD-2-Clause |

`hibernate-core` 6.6.26.Final 的上游 POM 声明 LGPL-2.1-or-later，并非上表的双许可证选择项。本 GitHub 仓库只发布项目源码；独立比赛运行包包含第三方 JAR，现随包提供 LGPL 正文、Hibernate 对应版本的未修改源码 JAR 及获取路径。其余版权与分发义务仍以[二进制运行包核对](submission-package-audit.md)所列实际证据和剩余事项为准。

## 本地基础设施镜像

| Compose 镜像 | 上游许可情况 | 本项目处理状态 |
| --- | --- | --- |
| `postgres:16` | [PostgreSQL License](https://www.postgresql.org/about/licence/) | 只记录上游数据库许可；镜像内其他组件未逐项审计。 |
| `emqx/emqx:5.8.6` | [EMQX 官方说明](https://www.emqx.com/en/content/license-faq)称 5.9.0 之前的开源版本保持 Apache-2.0 | 只记录当前指定版本；升级时须重新核对。 |
| `redis:7.2` | [Redis 官方许可说明](https://redis.io/legal/licenses/)列明 7.2.x 适用 BSD-3-Clause | 已从 7.4 调整到 7.2；2026-10-29 拉取到 7.2.17，隔离 Compose 的 Redis、PostgreSQL 与 EMQX 均通过健康检查。 |

以上镜像通过 Compose 拉取，本仓库和比赛 ZIP 均不包含镜像本体。比赛 ZIP 包含第三方 JAR 和编译前端文件，其随包文本及未完成核对项见[二进制运行包核对](submission-package-audit.md)；若以后把镜像本体打包分发，应另行检查镜像层许可。

隔离回归使用独立容器名、端口和数据卷：后端连接 PostgreSQL、Redis 7.2 和 EMQX 后，`/actuator/health` 返回 `UP`，模拟器接入 MQTT，`/api/v1/stations` 返回 7 个在线场站。该验证说明当前软件链路可在 Redis 7.2 上运行；它不覆盖旧容器数据迁移、所有故障场景或真实设备接入。

## 更新规则与未完成项

- 新增或升级依赖前，记录精确版本、官方许可证来源、使用位置及分发方式。
- 对双重许可、非 OSI 许可或许可字段缺失的组件，先确认使用条件，再决定是否保留或替换。
- 更新独立比赛运行包前，按[运行包构件清单](runtime-binary-inventory.csv)与[二进制运行包核对](submission-package-audit.md)重新核对实际 JAR、前端生产依赖、版权声明和必要的 NOTICE；源码仓库当前不包含镜像或构建产物。
- 许可元数据核对不等于完整法律合规结论。CSV 中的双重许可、LGPL 等条目在改变分发方式时需要重新审查具体义务。
