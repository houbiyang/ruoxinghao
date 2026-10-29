# 弱信号场景下通信质量自适应分布式协同控制测试报告

## 1. 摘要

本报告对应 `distributed-control-v1` 确定性控制实现。控制功率由 Java 优化器和分布式控制器计算，大模型仅生成分析与审计证据，不参与功率计算、S0-S4 判定、安全约束或 MQTT 指令发布。

2026-07-30 完成三种策略、五种场景、五个随机种子的 75 轮真实 MQTT 对照实验。最终矩阵包含 75 个唯一实验运行 ID，全部达到 `COMPLETED + TARGET_REACHED`，310 条控制命令全部取得 `EXECUTED`，约束违规为 0。四类弱通信场景中，自适应策略综合 RMSE 为 0.4685 kW，固定权重策略为 0.7055 kW，降低 33.60%，超过预先设定的 10% 声明阈值。

## 2. 系统与控制链路

真实链路为：

```text
实验脚本 -> REST 启动场景 -> MQTT 下发故障 -> Java 模拟器注入故障
-> 后端识别 S0-S4 -> 生成能力快照 -> 选择控制策略 -> 持久化逐资源目标
-> 多智能体安全门 -> MQTT 控制命令 -> 模拟器 EXECUTED 回执
-> 遥测连续收敛 -> 自动恢复 -> 实验终态 -> CSV/SVG 导出
```

三种策略共用相同场景、资源能力、随机种子和验收标准：

- `CENTRALIZED`：OR-Tools MILP 集中式基线。
- `FIXED_WEIGHT`：固定通信权重的分布式一致性控制。
- `ADAPTIVE`：根据真实时延、丢包率和消息时效调整权重的分布式一致性控制。

OR-Tools 或分布式控制器产生的逐资源目标写入 `targetPowersJson`，安全门、`executeNext` 和 MQTT 命令只读取该持久化结果，不重新分配或静默裁剪。

## 3. 分布式模型

目标函数为：

$$J(\boldsymbol P)=\sum_{i=1}^{N} C_i(P_i),\quad C_i(P_i)=a_iP_i^2+b_iP_i+c_i$$

功率平衡与资源约束为：

$$\sum_{i=1}^{N}P_i+P_{pv}+P_{wind}=P_{load}$$

$$P_i^{min}\le P_i\le P_i^{max}$$

$$-R_i^{down}\le P_i(k+1)-P_i(k)\le R_i^{up}$$

储能同时满足 SOC 上下限、容量、充放电方向和爬坡约束。储能正功率表示放电，负功率表示充电。

节点更新为：

$$\lambda_i(k+1)=\sum_{j\in\mathcal N_i\cup\{i\}}w_{ij}(k)\hat\lambda_j(k)+\alpha_s z_i(k)$$

$$P_i(k+1)=\Pi_{\Omega_i}\left(\frac{\lambda_i(k+1)-b_i}{2a_i}\right)$$

$$z_i(k+1)=\sum_{j\in\mathcal N_i\cup\{i\}}w_{ij}(k)z_j(k)+\Delta d_i(k)-\Delta P_i(k)$$

自适应通信质量为：

$$q_{ij}=clip(\omega_lq_{ij}^{latency}+\omega_pq_{ij}^{loss}+\omega_aq_{ij}^{age},0,1)$$

邻居权重由基础邻接权重与质量分数相乘后归一化。每轮先交换上一轮消息，再同步计算下一轮状态，避免节点遍历顺序影响结果。

## 4. S0-S4 控制语义

- S0：完整通信图与正常协同参数。
- S1/S2：使用实时通信质量降低不可靠邻居权重，在线场站继续协同。
- S3：目标场站拒绝远程控制，在模拟器站端按最后安全目标、容量、爬坡和 SOC 执行本地自治。
- S4：补报自治证据并限速恢复，连续 3 个有效稳定样本后重新入图。
- S0：恢复完成后重新允许远程目标，实验进入 `COMPLETED`。

## 5. 验收标准

- 策略：`CENTRALIZED`、`FIXED_WEIGHT`、`ADAPTIVE`。
- 场景：正常、时延、丢包、离线恢复、组合故障。
- 随机种子：101、202、303、404、505。
- 计划平衡误差不超过 1 kW。
- 实际供需误差连续 3 个有效遥测样本不超过 1 kW，才记录 `TARGET_REACHED`。
- 分布式算法内部平衡误差小于 1 kW、一致性误差小于 0.01，连续 10 轮后判定算法收敛。
- 所有控制命令必须进入 `EXECUTED` 或明确失败终态。
- 约束违规次数必须为 0，失败实验必须保留。
- 自适应策略弱通信综合 RMSE 相比固定权重至少降低 10%，才允许声明性能提升。

## 6. 实验环境

| 项目 | 配置 |
| --- | --- |
| 后端 | Java 21、Spring Boot、PostgreSQL、Redis |
| 消息链路 | EMQX、MQTT QoS 1 |
| 模拟器 | Java 21，多资源实时遥测与故障注入 |
| 前端 | Vue 3、TypeScript、ECharts |
| 集中式求解器 | OR-Tools SCIP 10.0.0 |
| 分布式控制版本 | distributed-control-v1 |
| 正式运行数 | 3 策略 x 5 场景 x 5 种子 = 75 |
| 遥测收敛容差 | 1 kW，连续 3 个样本 |
| 最大滚动轮次 | 3 |

## 7. 正式结果

以下为每个策略和场景的五种子均值。RMSE 包含执行后的暂态过程，因此可以高于最终 1 kW 收敛容差；最终状态均已满足连续 3 个样本不超过 1 kW。

| 策略 | 场景 | RMSE (kW) | 平均绝对平衡误差 (kW) | 平均收敛时间 (s) | 失败数 |
| --- | --- | ---: | ---: | ---: | ---: |
| ADAPTIVE | NORMAL | 0.5624 | 0.0705 | 2.8 | 0 |
| ADAPTIVE | LATENCY | 0.6921 | 0.4605 | 2.6 | 0 |
| ADAPTIVE | PACKET_LOSS | 0.0000 | 0.0000 | 2.0 | 0 |
| ADAPTIVE | OFFLINE_RECOVERY | 0.0000 | 0.0000 | 2.0 | 0 |
| ADAPTIVE | COMBINED | 0.5953 | 0.4161 | 3.8 | 0 |
| FIXED_WEIGHT | NORMAL | 0.5869 | 0.0861 | 2.8 | 0 |
| FIXED_WEIGHT | LATENCY | 0.9346 | 0.4908 | 5.6 | 0 |
| FIXED_WEIGHT | PACKET_LOSS | 0.7440 | 0.1389 | 2.8 | 0 |
| FIXED_WEIGHT | OFFLINE_RECOVERY | 0.0000 | 0.0000 | 12.6 | 0 |
| FIXED_WEIGHT | COMBINED | 0.5667 | 0.4089 | 2.8 | 0 |
| CENTRALIZED | NORMAL | 0.7383 | 0.1665 | 4.2 | 0 |
| CENTRALIZED | LATENCY | 1.0687 | 0.2055 | 3.6 | 0 |
| CENTRALIZED | PACKET_LOSS | 0.1784 | 0.0306 | 3.6 | 0 |
| CENTRALIZED | OFFLINE_RECOVERY | 0.0000 | 0.0000 | 2.0 | 0 |
| CENTRALIZED | COMBINED | 0.7943 | 0.1884 | 4.6 | 0 |

总体审计结果：

- 75/75 个正式样本为 `COMPLETED + TARGET_REACHED`。
- 310/310 条控制命令为 `EXECUTED`，命令成功率 100%。
- 75/75 个样本约束违规数为 0。
- 正式曲线包含 2706 个完整系统供需采样点。
- 四类弱通信场景固定权重综合 RMSE 为 0.7055 kW，自适应为 0.4685 kW，降低 33.60%。

该结果支持在本次固定模型、场景参数和随机种子范围内声明自适应策略的 RMSE 改善，不代表任意拓扑、任意故障强度下均有相同比例提升。

## 8. 失败与修复分析

正式过程中保留了四条修复前失败记录：自适应正常 seed 202、自适应时延 seed 505、自适应丢包 seed 202 和 404。四条记录均为 `CONVERGENCE_TIMEOUT`，命令实际已全部执行，回放中也出现过连续平衡证据。

根因是稳定等待结束后的第一份瞬时偏差会立即触发滚动重调度，导致三轮可能在后续稳定样本到达前耗尽。修复后，最小重调度间隔从稳定等待结束时开始计算，保留完整遥测观测窗口。新增单元测试验证瞬时偏差不会立即消耗轮次，四个失败组合随后均以第 1 MPC 轮次达到 `TARGET_REACHED`。失败记录未删除，见 `pre-fix-failures.csv`。

## 9. 回归结果

- 后端：194 个测试通过，0 失败。
- 模拟器：39 个测试通过，0 失败。
- 前端：`npm run type-check` 通过。
- 前端：`npm run build` 通过。
- OR-Tools：原生库加载成功，求解器为 SCIP 10.0.0。

## 10. 数据追溯

正式数据位于 `docs/reports/data/real-control-formal/`：

- `real-control-runs.csv`：75 轮运行明细和运行 ID。
- `real-control-balance-curves.csv`：实际供需误差时序。
- `real-control-summary.csv`：15 个策略场景统计分组。
- `real-control-failures.csv`：最终矩阵失败清单，当前为空。
- `pre-fix-failures.csv`：修复前四条失败证据。
- `adaptive-performance-assessment.json`：10% 声明阈值判断。
- `experiment-environment.json`：环境、求解器、种子和代码版本。
- `real-control-balance-curves.svg`：曲线导出。
- `sha256-manifest.csv`：交付文件校验值。

正式矩阵允许复用相同策略、场景、种子且已经通过全部硬验收的运行。复用时重新查询运行、计划、回放、命令和约束证据，不复制旧统计结果；失败和中断运行不参与有效矩阵。

## 11. 可复现实验命令

```powershell
.\scripts\run-real-control-comparison.ps1 `
  -Seeds @(101,202,303,404,505) `
  -Strategies @('CENTRALIZED','FIXED_WEIGHT','ADAPTIVE') `
  -ScenarioCodes @('T0_STABLE_BASELINE','COM_LATENCY','COM_PACKET_LOSS','COM_OFFLINE','COM_COMBINED') `
  -DurationSeconds 10 `
  -ReuseCompletedRunsSince '2026-07-30T13:00:00+08:00'
```

## 12. 局限性

- 物理设备由 Java 模拟器代替，尚未完成真实逆变器、储能 EMS 或硬件在环测试。
- 正式矩阵固定为五个种子和当前故障强度，统计区间不能外推到全部通信环境。
- 三种策略共享同一套系统，实验必须串行执行；并行需要隔离的 MQTT、数据库和模拟器实例。
- RMSE 按真实遥测供需误差计算，分布式内部一致性指标另作为控制器审计证据，两者不可混用。
- 大模型可能超时并进入规则分析回退，但不会改变确定性控制目标和安全门结果。

## 13. 结论

项目已形成故障注入、S0-S4 识别、三策略调度、S3 自治、S4 重新入图、计划持久化、安全审计、MQTT 执行、遥测收敛和实验导出的真实闭环。当前 75 轮正式矩阵全部通过硬验收，自适应策略在四类弱通信场景的综合 RMSE 相比固定权重降低 33.60%。

## 14. 参考文献

1. R. Olfati-Saber, J. A. Fax, R. M. Murray, "Consensus and Cooperation in Networked Multi-Agent Systems," Proceedings of the IEEE, vol. 95, no. 1, pp. 215-233, 2007.
2. S. Boyd, N. Parikh, E. Chu, B. Peleato, J. Eckstein, "Distributed Optimization and Statistical Learning via the Alternating Direction Method of Multipliers," Foundations and Trends in Machine Learning, vol. 3, no. 1, pp. 1-122, 2011.
3. A. J. Wood, B. F. Wollenberg, G. B. Sheble, Power Generation, Operation, and Control, 3rd ed., Wiley, 2013.

