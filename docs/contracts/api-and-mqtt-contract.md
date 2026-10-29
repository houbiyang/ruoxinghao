# API 与 MQTT 契约

- 契约版本：`v1`
- 冻结日期：`2026-07-01`
- 适用范围：`backend`、`simulator`、`frontend api` 模式

## 1. REST 统一响应结构

```json
{
  "code": "SUCCESS",
  "message": "操作成功",
  "data": {},
  "timestamp": "2026-06-30T16:00:00+08:00"
}
```

- `code`：业务结果码。
- `message`：中文结果说明。
- `data`：业务载荷，集合为空时返回 `[]`，当前运行实验不存在时返回 `null`。
- `timestamp`：ISO 8601 时间戳，必须包含时区。

## 2. REST API 清单

- `GET /api/v1/health`：健康检查，返回 `status`。
- `GET /api/v1/stations`：返回前端 `StationState[]`。
- `GET /api/v1/stations/{stationId}`：返回前端 `ResourceDetail`。
- `GET /api/v1/dashboard/summary`：返回总出力、可调容量、在线场站数、告警数、控制模式和当前场景。
- `GET /api/v1/dashboard/power-trend?from&to&limit`：返回计划功率与实测功率趋势，`from` 和 `to` 可缺省。
- `GET /api/v1/events/latest?limit=6`：返回最近告警与事件。
- `GET /api/v1/scenarios/current`：返回当前演示场景。
- `POST /api/v1/scenarios/{scenario}/activate`：激活 `stable`、`degraded`、`lost`、`recovering`。
- `GET /api/v1/experiments/scenarios`：返回 T0-T10 实验场景清单。
- `GET /api/v1/experiments/runs/latest?limit=10`：返回最近实验运行记录。
- `GET /api/v1/experiments/runs/current`：返回当前运行实验；没有运行时 `data` 为 `null`。
- `POST /api/v1/experiments/scenarios/{scenarioCode}/start`：启动实验。
- `POST /api/v1/experiments/runs/{runId}/stop`：停止实验并恢复 `stable`。
- `GET /api/v1/dispatch/plan`：返回未来 24 个整点的规则型日内计划。
- `POST /api/v1/dispatch/preview`：校验约束并预览场站功率分配，不下发。
- `POST /api/v1/dispatch/execute`：持久化并通过 MQTT 下发场站功率指令。
- `GET /api/v1/dispatch/commands/latest?limit=20`：查询最近调度指令及最新回执。
- `GET /api/v1/dispatch/commands/{commandId}`：查询单条指令和完整回执时间线。
- `GET /api/v1/dispatch/status`：查询执行状态分布、成功率、拒绝率和待执行数量。
- `POST /api/v1/fault-intelligence/analyze`：基于实时快照发起确定性规则和五类逻辑智能体诊断。
- `GET /api/v1/fault-intelligence/runs/latest?limit=20`：查询最近诊断审计。
- `GET /api/v1/fault-intelligence/runs/{runId}`：查询规则、智能体意见、安全裁决和动作状态。
- `GET /api/v1/fault-intelligence/runs/{runId}/plan`：查询最终动作计划。
- `POST /api/v1/fault-intelligence/runs/{runId}/approve`：兼容旧版复核入口，自动裁决模式正常不依赖该接口。
- `POST /api/v1/fault-intelligence/runs/{runId}/reject`：兼容旧版复核入口，自动裁决模式正常不依赖该接口。
- `POST /api/v1/fault-intelligence/runs/{runId}/execute`：执行安全门自动放行的方案。
- `GET /api/v1/fault-intelligence/providers/status`：返回脱敏 Provider 启用、可用和最近错误状态。

## 3. 实验接口 DTO

### ExperimentScenario

- `scenarioCode`
- `scenarioName`
- `description`
- `targetStationIds`
- `expectedLinkState`
- `durationSeconds`
- `faultParameters`
- `enabled`

### ExperimentRun

- `runId`
- `scenarioCode`
- `scenarioName`
- `status`
- `startedAt`
- `endedAt`
- `resultSummary`

### ExperimentStartRequest

- `targetStationIds`：可选；缺省使用场景默认目标场站。
- `durationSeconds`：可选；缺省使用场景默认持续时间。
- `remark`：可选备注。

### ExperimentStartResponse

- `experimentRunId`
- `scenarioCode`
- `status`
- `startedAt`

### ExperimentStopResponse

- `runId`
- `status`
- `stoppedAt`

## 4. 固定枚举

### LinkState

- `S0`：正常协同
- `S1`：通信退化
- `S2`：严重退化
- `S3`：失联自治
- `S4`：恢复同步

### ResourceType

- `photovoltaic`
- `wind`
- `grid`
- `storage`
- `load`
- `charging`

### DemoScenario

- `stable`
- `degraded`
- `lost`
- `recovering`

## 5. 固定场站标识

- `pv-gobi-01`
- `wind-ridge-02`
- `grid-link-00`
- `storage-hub-03`
- `load-oasis-04`
- `charge-corridor-05`

## 6. T0-T10 实验场景

- `T0_STABLE_BASELINE`：稳定基线，无故障，预期 `S0`。
- `T1_LATENCY_JITTER`：通信时延抖动，预期 `S1`。
- `T2_RANDOM_PACKET_LOSS`：随机丢包，预期 `S1`。
- `T3_BURST_PACKET_LOSS`：突发丢包，预期 `S2`。
- `T4_INTERMITTENT_DISCONNECT`：间歇中断，预期 `S2`。
- `T5_FULL_LOSS_AUTONOMY`：完全失联，本地自治，预期 `S3`。
- `T6_BANDWIDTH_LIMIT`：带宽受限，遥测降频，预期 `S1`。
- `T7_OUT_OF_ORDER_DUPLICATE`：乱序与重复消息，预期 `S1`。
- `T8_STALE_TELEMETRY`：遥测陈旧或冻结，预期 `S2`。
- `T9_RECOVERY_SYNC`：恢复同步，预期 `S4`。
- `T10_CONTROL_ACK_LOSS`：控制确认丢失或延迟，预期 `S1`；当前控制 ACK 链路未完整实现，模拟器仅记录命令并保持遥测退化。

## 7. MQTT Topic 契约

### 上行

- `vpp/{stationId}/telemetry/report`
- `vpp/{stationId}/heartbeat/report`
- `vpp/{stationId}/ack/report`
- `vpp/{stationId}/fault/report`

### 下行

- `vpp/{stationId}/command/dispatch`
- `vpp/{stationId}/scenario/apply`
- `vpp/{stationId}/control/apply`
- `vpp/{stationId}/fault-action/apply`
- `vpp/all/scenario/apply`

### 当前实现说明

- 后端订阅 `vpp/+/telemetry/report`、`vpp/+/heartbeat/report`、`vpp/+/ack/report`、`vpp/+/fault/report`。
- 后端发布 `vpp/all/scenario/apply`、`vpp/{stationId}/control/apply` 和 `vpp/{stationId}/fault-action/apply`。
- 模拟器发布 `vpp/{stationId}/telemetry/report`、`vpp/{stationId}/heartbeat/report` 和 `vpp/{stationId}/ack/report`。
- 模拟器订阅 `vpp/all/scenario/apply`、`vpp/+/control/apply` 和 `vpp/+/fault-action/apply`。
- 本阶段实验命令复用 `vpp/all/scenario/apply`，未新增 Topic。

## 8. MQTT Payload

### 遥测消息

```json
{
  "messageId": "uuid",
  "stationId": "pv-gobi-01",
  "stationName": "光伏",
  "resourceType": "photovoltaic",
  "sequenceNumber": 1001,
  "deviceTimestamp": "2026-06-30T16:00:00+08:00",
  "sendTimestamp": "2026-06-30T16:00:00+08:00",
  "activePower": 126.0,
  "availableCapacity": 168.0,
  "latency": 36,
  "packetLossRate": 0.4,
  "online": true,
  "qualityCode": "GOOD",
  "linkState": "S0",
  "metrics": {
    "soc": 63.0,
    "temperature": 28.5,
    "voltage": 380.0,
    "frequency": 50.0
  }
}
```

### 心跳消息

```json
{
  "stationId": "pv-gobi-01",
  "online": true,
  "heartbeatAt": "2026-06-30T16:00:00+08:00",
  "clientId": "sim-pv-gobi-01"
}
```

### 普通场景切换命令

```json
{
  "commandId": "uuid",
  "scenario": "degraded",
  "requestedAt": "2026-06-30T16:00:00+08:00",
  "source": "backend-api",
  "commandType": "APPLY_SCENARIO"
}
```

### 启动实验命令

```json
{
  "commandId": "uuid",
  "scenario": "degraded",
  "requestedAt": "2026-06-30T16:00:00+08:00",
  "source": "backend-experiment",
  "commandType": "START_EXPERIMENT",
  "experimentScenarioCode": "T1_LATENCY_JITTER",
  "experimentRunId": "uuid",
  "targetStationIds": ["pv-gobi-01"],
  "durationSeconds": 120
}
```

### 停止实验命令

```json
{
  "commandId": "uuid",
  "scenario": "stable",
  "requestedAt": "2026-06-30T16:02:00+08:00",
  "source": "backend-experiment",
  "commandType": "STOP_EXPERIMENT",
  "experimentScenarioCode": "T1_LATENCY_JITTER",
  "experimentRunId": "uuid",
  "targetStationIds": [],
  "durationSeconds": null
}
```

### 功率控制命令

```json
{
  "commandId": "uuid",
  "batchId": "uuid",
  "stationId": "pv-gobi-01",
  "commandType": "SET_ACTIVE_POWER",
  "targetPower": 120.0,
  "issuedAt": "2026-07-01T20:00:00+08:00",
  "expireAt": "2026-07-01T20:00:30+08:00",
  "controlMode": "edge-coordination",
  "maximumRampRate": 18.0
}
```

### 控制指令回执

```json
{
  "commandId": "uuid",
  "stationId": "pv-gobi-01",
  "ackStage": "EXECUTED",
  "success": true,
  "message": "控制指令已接受，场站按爬坡约束执行",
  "ackTime": "2026-07-01T20:00:02+08:00"
}
```

调度指令状态固定为 `CREATED`、`SENT`、`ACCEPTED`、`EXECUTED`、`REJECTED`、`TIMEOUT`、`FAILED`。模拟器回执阶段固定支持 `RECEIVED`、`EXECUTED`、`REJECTED`。

### 故障处置动作

```json
{
  "actionId": "uuid",
  "diagnosisRunId": "uuid",
  "stationId": "pv-gobi-01",
  "actionType": "LIMIT_CONTROL_RAMP",
  "parameters": {},
  "issuedAt": "2026-07-01T21:00:00+08:00",
  "expireAt": "2026-07-01T21:00:30+08:00"
}
```

故障动作只能由后端安全门从固定枚举构造。AI 响应不得携带 Topic、URL、SQL 或任意代码。高风险动作 `ISOLATE_STATION`、`SWITCH_CONTROL_AUTHORITY`、`REDISTRIBUTE_AGGREGATE_POWER`、`RESTORE_CENTRAL_CONTROL` 必须经过确定性安全门自动裁决，并在前端作为观察和复盘证据展示。

## 9. Redis Key 契约

- 已验证存在：`vpp:station:{stationId}:latest`
- 已验证存在：`vpp:station:{stationId}:heartbeat`
- 规划中/未落地：`vpp:dashboard:summary`
- 当前代码已写入但本轮未重新验证：`vpp:scenario:active`

## 10. 数据库表用途

- `station`：保存场站主数据、名称、资源类型和启用状态。
- `resource_device`：保存场站下挂设备主数据和设备状态。
- `telemetry_snapshot`：每个场站只保留最新有效遥测快照。
- `telemetry_history`：保存全量遥测历史，用于趋势和故障追溯。
- `control_command`：保存平台下发指令及发送状态。
- `command_ack`：保存设备控制确认回执。
- `link_status_log`：保存链路状态切换记录。
- `fault_event`：保存故障、失联和恢复事件。
- `experiment_scenario`：保存演示场景和 T0-T10 实验场景基础定义。
- `experiment_run`：保存实验运行记录、状态、起止时间和结果摘要。
- `fault_diagnosis_run`：保存规则判定、最终裁决、兼容复核状态、冲突和回退审计。
- `agent_diagnosis_result`：保存五类智能体经过结构校验的结论，不保存模型思维过程。
- `fault_action_plan`：保存安全门自动放行的动作、MQTT 关联和执行状态。

## 11. 兼容性规则

- `mock` 模式必须保留，不能依赖后端。
- `api` 模式 DTO 字段不得随意改名。
- MQTT Topic 变更必须同步修改 `backend`、`simulator` 和本文档。
- 新增 payload 字段必须向后兼容；模拟器对缺失实验字段应按普通场景切换处理。
- 固定枚举值和固定 `stationId` 视为跨端契约，不允许擅自改名。

## 12. 市场调度与 DeepSeek 约束

- 多智能体调度中心默认使用 `deepseek-v4-flash`，并保留 `DEEPSEEK_MODEL` 环境变量覆盖能力。
- 当前项目不再暴露 Kimi Provider 契约；AI 关闭、超时、空响应或非法 JSON 时必须回退确定性规则模式。
- DeepSeek 只输出结构化候选决策，不能直接访问 MQTT、SQL、Redis 或设备控制接口。
