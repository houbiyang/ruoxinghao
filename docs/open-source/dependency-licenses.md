# 第三方依赖与许可核对

本页依据仓库中 `backend/pom.xml`、`simulator/pom.xml`、`frontend/package.json`、`frontend/package-lock.json` 和 `docker-compose.yml` 核对依赖。核对日期：2026-10-29。许可证名称来自相应版本的 Maven POM、npm 发布元数据或项目官方许可说明。根目录的 MIT License 只授权本项目自有代码与文档，不替代第三方许可。

完整版本清单见 [`dependency-audit.csv`](dependency-audit.csv)：从两个 Maven 依赖树解析出 129 个唯一构件，从前端锁文件解析出 135 个唯一包版本，共 264 项；每项都有上游许可元数据链接，未发现许可证字段缺失。CSV 保留上游原始许可名称，双重许可条目也保留全部候选许可。本清单覆盖依赖解析结果，不等于对每个二进制包内部版权文件和镜像层完成法律审查。

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

`frontend/package-lock.json` 固定了传递依赖版本，逐项 npm 许可字段见 CSV。发布编译后的前端文件时，仍须核对所含第三方代码的许可证文本和版权声明是否随分发包保留。

## 本地基础设施镜像

| Compose 镜像 | 上游许可情况 | 本项目处理状态 |
| --- | --- | --- |
| `postgres:16` | [PostgreSQL License](https://www.postgresql.org/about/licence/) | 只记录上游数据库许可；镜像内其他组件未逐项审计。 |
| `emqx/emqx:5.8.6` | [EMQX 官方说明](https://www.emqx.com/en/content/license-faq)称 5.9.0 之前的开源版本保持 Apache-2.0 | 只记录当前指定版本；升级时须重新核对。 |
| `redis:7.2` | [Redis 官方许可说明](https://redis.io/legal/licenses/)列明 7.2.x 适用 BSD-3-Clause | 已从 7.4 调整到 7.2；2026-10-29 拉取到 7.2.17，隔离 Compose 的 Redis、PostgreSQL 与 EMQX 均通过健康检查。 |

以上镜像通过 Compose 拉取，本仓库不包含镜像本体。若制作含镜像或第三方二进制的分发包，需另行核对其完整许可、版权声明和分发条件。

隔离回归使用独立容器名、端口和数据卷：后端连接 PostgreSQL、Redis 7.2 和 EMQX 后，`/actuator/health` 返回 `UP`，模拟器接入 MQTT，`/api/v1/stations` 返回 7 个在线场站。该验证说明当前软件链路可在 Redis 7.2 上运行；它不覆盖旧容器数据迁移、所有故障场景或真实设备接入。

## 更新规则与未完成项

- 新增或升级依赖前，记录精确版本、官方许可证来源、使用位置及分发方式。
- 对双重许可、非 OSI 许可或许可字段缺失的组件，先确认使用条件，再决定是否保留或替换。
- 发布包含第三方构件的二进制包前，核对容器镜像层、版权声明和必要的许可证文本；源码仓库当前不包含这些镜像或构建产物。
- 许可元数据核对不等于完整法律合规结论。CSV 中的双重许可、LGPL 等条目在改变分发方式时需要重新审查具体义务。
