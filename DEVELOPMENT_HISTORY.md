# 开发历程与证据边界

本页区分原始本地开发记录、实验数据形成时间与公开仓库维护记录。日期均为北京时间。它记录可以核对的里程碑，不把本地提交追溯为更早的公开开源运营，也不把软件模拟解释为真实场站部署。

## 已有记录

| 时间 | 可核查事实 | 证据与范围 |
| --- | --- | --- |
| 2026-07-10 | 原始本地 Git 仓库出现最早提交，包含后端、模拟器、前端、脚本和基础文档。 | 本地提交 `6dee048f0e`；只证明当时已有代码快照，不证明所有功能已完成。 |
| 2026-07-18 至 2026-07-26 | 后端接口、实验闭环、前端页面和模拟器持续修改。 | 本地提交 `259efa4df6`、`0bc67fc1bc`、`b8a0445a0a`、`ad3609ece8` 的变更路径；功能状态以对应版本和测试为准。 |
| 2026-07-27 | 形成正式实验环境记录所指向的源码提交。 | 本地提交 `5bb4e0439c155ab66de86e8b17a86747cb386b41`；该哈希见 [`experiment-environment.json`](docs/reports/data/real-control-formal/experiment-environment.json)。 |
| 2026-07-30 | 汇总三策略、五场景、五种子的 75 轮软件模拟实验结果，保留运行 ID、曲线、失败记录和统计。 | [正式实验目录](docs/reports/data/real-control-formal/README.md)与[测试报告](docs/reports/distributed-cooperative-control-test-report.md)。环境记录中的 `reused_run_count` 为 75，表示本次汇总复用了已完成运行，并非当天重新执行 75 轮。 |
| 2026-07-30 至 2026-08-28 | 继续修改前端页面、后端功能及项目文档。 | 本地提交 `04429f9d93`、`1cf0fa7e7b`、`b0f2c8013a`；这些记录不能代替后续版本的回归测试。 |
| 2026-10-29 | 第一次向 GitHub 公开经整理的项目源码和实验材料，随后添加 MIT 许可、依赖清单、贡献流程和自动核对。 | [公开仓库首个提交](https://github.com/houbiyang/ruoxinghao/commit/98189e9)、[许可与依赖处理 PR](https://github.com/houbiyang/ruoxinghao/pull/2)、[持续核对 PR](https://github.com/houbiyang/ruoxinghao/pull/4)。公开维护记录从这一天开始。 |
| 2026-10-29 | 从正式实验所指的原始本地提交导出运行所需的历史源码子集，逐文件核对 Git blob 哈希并在独立目录完成后端、模拟器测试及前端构建。 | [源码快照与逐文件清单](docs/reports/source-snapshots/README.md)；这是历史源码可获取和构建验证，不是 75 轮实验重跑。 |

## 原始历史与公开仓库的关系

原始本地 Git 历史包含 Maven 缓存、前端依赖目录等生成文件，因此公开仓库使用了整理后的源码快照，没有直接导入原始提交链。`5bb4e0439c155ab66de86e8b17a86747cb386b41` 能在原始本地仓库解析，但仍不能在公开仓库直接检出。现已发布该提交的[运行所需源码子集](docs/reports/source-snapshots/README.md)及逐文件清单，使正式实验记录中的源码版本可以获取；完整 Git 历史、原始运行环境和 75 轮重跑尚未公开验证，继续由[首个稳定版本任务](https://github.com/houbiyang/ruoxinghao/issues/7)跟踪。

原始本地历史可用 Windows PowerShell 中的 `git log --reverse --date=iso-strict --format="%H %aI %an %s"` 和 `git cat-file -t 5bb4e0439c155ab66de86e8b17a86747cb386b41` 核对。以上记录未证明真实设备接入、外部用户采用或跨版本社区维护。
