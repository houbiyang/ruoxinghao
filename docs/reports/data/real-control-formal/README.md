# 正式实验数据说明

本目录保存 2026-07-30 形成的比赛正式实验矩阵。有效矩阵包含三种策略、五种场景、五个固定随机种子，共 75 个唯一实验运行 ID。

- `real-control-runs.csv`：75 轮有效运行明细。
- `real-control-balance-curves.csv`：2706 个完整系统供需误差采样点。
- `real-control-summary.csv`：15 个策略场景统计分组。
- `real-control-failures.csv`：最终有效矩阵失败清单，当前为空。
- `pre-fix-failures.csv`：修复前四条 `CONVERGENCE_TIMEOUT`，用于失败审计。
- `adaptive-performance-assessment.json`：固定权重与自适应弱通信综合 RMSE 对比。
- `experiment-environment.json`：求解器、种子、场景和代码版本。
- `real-control-balance-curves.svg`：功率平衡误差曲线。
- `sha256-manifest.csv`：目录文件校验值。

最终矩阵的 75 个样本全部为 `COMPLETED + TARGET_REACHED`，310 条控制命令全部为 `EXECUTED`，约束违规为 0。自适应策略的弱通信综合 RMSE 为 0.4685 kW，固定权重为 0.7055 kW，降低 33.60%。

批处理允许复用相同策略、场景、随机种子且通过全部硬验收的真实运行。复用会重新读取数据库运行、回放、计划、命令和约束证据；失败、中断或证据不完整的运行不会进入有效矩阵。
