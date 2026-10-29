import {
  type AgentDispatchOverview,
  ControlMode,
  DemoScenario,
  LinkState,
  ResourceType,
  type AlarmEvent,
  type DispatchCommand,
  type DispatchContextSnapshot,
  type DispatchPlanPoint,
  type DispatchStatus,
  type AiProviderStatus,
  type FaultDiagnosisRun,
  type ForecastSnapshot,
  type ExperimentComparison,
  type ExperimentQualityMetrics,
  type ExperimentReplayPoint,
  type ExperimentRun,
  type ExperimentScenario,
  type ExperimentSummary,
  type ExperimentTimelinePoint,
  type MarketAgentRun,
  type MarketBid,
  type MarketClearing,
  type MarketDispatchExecution,
  type MarketDispatchPlan,
  type MarketDispatchTracking,
  type MarketSignalSnapshot,
  type ResourceCapabilitySnapshot,
  type ResourceDetail,
  type ResourceMetric,
  type ScenarioProfile,
  type SettlementResult,
  type StationDeviceDetail,
  type StationDevicePowerTrend,
  type StationDeviceQualityTrendPoint,
  type StationDeviceSnapshot,
  type StationDeviceTrendPoint,
  type StationState,
  type TimelinePoint
} from '@/types/system'

export const resourceTypeLabelMap: Record<ResourceType, string> = {
  [ResourceType.Photovoltaic]: '光伏场站',
  [ResourceType.Wind]: '风电场站',
  [ResourceType.Thermal]: '火力发电',
  [ResourceType.Grid]: '市电接口',
  [ResourceType.Storage]: '储能场站',
  [ResourceType.Load]: '可控负荷',
  [ResourceType.Charging]: '充电设施'
}

export const linkStateMetaMap: Record<
  LinkState,
  { label: string; color: string; lineStyle: 'solid' | 'dashed'; animationClass: string }
> = {
  [LinkState.S0]: { label: '正常协同', color: '#35d6a6', lineStyle: 'solid', animationClass: 'is-stable' },
  [LinkState.S1]: { label: '通信退化', color: '#f5d76e', lineStyle: 'solid', animationClass: 'is-degraded' },
  [LinkState.S2]: { label: '严重退化', color: '#f0953b', lineStyle: 'solid', animationClass: 'is-severe' },
  [LinkState.S3]: { label: '失联自治', color: '#ef6457', lineStyle: 'dashed', animationClass: 'is-lost' },
  [LinkState.S4]: { label: '恢复同步', color: '#5aa7ff', lineStyle: 'solid', animationClass: 'is-recovering' }
}

export const controlModeLabelMap: Record<ControlMode, string> = {
  [ControlMode.CentralOptimization]: '中心优化',
  [ControlMode.EdgeCoordination]: '边缘协同',
  [ControlMode.LocalAutonomy]: '本地自治'
}

export const demoScenarioProfiles: ScenarioProfile[] = [
  {
    scenario: DemoScenario.Stable,
    label: '正常',
    description: '主站与边缘控制链路稳定，计划与实测保持高一致性。',
    controlMode: ControlMode.CentralOptimization,
    primaryState: LinkState.S0
  },
  {
    scenario: DemoScenario.Degraded,
    label: '通信退化',
    description: '链路延迟升高并伴随丢包，控制节奏切换到边缘协同。',
    controlMode: ControlMode.EdgeCoordination,
    primaryState: LinkState.S1
  },
  {
    scenario: DemoScenario.Lost,
    label: '失联自治',
    description: '主链路中断，场站进入本地自治并保持保守出力。',
    controlMode: ControlMode.LocalAutonomy,
    primaryState: LinkState.S3
  },
  {
    scenario: DemoScenario.Recovering,
    label: '恢复同步',
    description: '链路逐步恢复，边缘执行状态回传并重新并入云侧优化。',
    controlMode: ControlMode.EdgeCoordination,
    primaryState: LinkState.S4
  }
]

export const experimentScenarios: ExperimentScenario[] = [
  {
    scenarioCode: 'T0_STABLE_BASELINE',
    scenarioName: '稳定基线',
    description: '无故障注入，记录正常协同状态下的基线数据。',
    targetStationIds: ['pv-gobi-01', 'wind-ridge-02', 'storage-hub-03'],
    expectedLinkState: LinkState.S0,
    durationSeconds: 90,
    faultParameters: { mode: 'baseline' },
    enabled: true
  },
  {
    scenarioCode: 'T1_LATENCY_JITTER',
    scenarioName: '通信时延抖动',
    description: '对目标场站注入稳定可重复的时延抬升和抖动。',
    targetStationIds: ['pv-gobi-01'],
    expectedLinkState: LinkState.S1,
    durationSeconds: 120,
    faultParameters: { baseLatencyMs: '120', jitterMs: '160' },
    enabled: true
  },
  {
    scenarioCode: 'T2_RANDOM_PACKET_LOSS',
    scenarioName: '随机丢包',
    description: '按固定节奏丢弃目标场站部分遥测消息。',
    targetStationIds: ['wind-ridge-02'],
    expectedLinkState: LinkState.S1,
    durationSeconds: 120,
    faultParameters: { dropEveryTicks: '4' },
    enabled: true
  },
  {
    scenarioCode: 'T3_BURST_PACKET_LOSS',
    scenarioName: '突发丢包',
    description: '周期性连续丢弃目标场站遥测，形成短时突发丢包。',
    targetStationIds: ['pv-gobi-01', 'wind-ridge-02'],
    expectedLinkState: LinkState.S2,
    durationSeconds: 120,
    faultParameters: { burstWindowTicks: '3', periodTicks: '10' },
    enabled: true
  },
  {
    scenarioCode: 'T4_INTERMITTENT_DISCONNECT',
    scenarioName: '间歇中断',
    description: '目标场站在线状态按固定周期切换，模拟弱覆盖边缘中断。',
    targetStationIds: ['storage-hub-03'],
    expectedLinkState: LinkState.S2,
    durationSeconds: 120,
    faultParameters: { offlineWindowTicks: '2', periodTicks: '8' },
    enabled: true
  },
  {
    scenarioCode: 'T5_FULL_LOSS_AUTONOMY',
    scenarioName: '完全失联自治',
    description: '目标场站停止心跳和遥测，触发后端心跳超时与本地自治判断。',
    targetStationIds: ['pv-gobi-01'],
    expectedLinkState: LinkState.S3,
    durationSeconds: 120,
    faultParameters: { heartbeat: 'stopped', telemetry: 'stopped' },
    enabled: true
  },
  {
    scenarioCode: 'T6_BANDWIDTH_LIMIT',
    scenarioName: '带宽受限',
    description: '目标场站保持心跳，但遥测上报频率降低。',
    targetStationIds: ['charge-corridor-05'],
    expectedLinkState: LinkState.S1,
    durationSeconds: 120,
    faultParameters: { telemetryEveryTicks: '3' },
    enabled: true
  },
  {
    scenarioCode: 'T7_OUT_OF_ORDER_DUPLICATE',
    scenarioName: '乱序与重复消息',
    description: '目标场站周期性发送重复序号或旧时间戳遥测。',
    targetStationIds: ['wind-ridge-02'],
    expectedLinkState: LinkState.S1,
    durationSeconds: 120,
    faultParameters: { duplicateEveryTicks: '5' },
    enabled: true
  },
  {
    scenarioCode: 'T8_STALE_TELEMETRY',
    scenarioName: '遥测陈旧冻结',
    description: '目标场站保持功率冻结并持续发送陈旧采样时间。',
    targetStationIds: ['load-oasis-04'],
    expectedLinkState: LinkState.S2,
    durationSeconds: 120,
    faultParameters: { staleSeconds: '18' },
    enabled: true
  },
  {
    scenarioCode: 'T9_RECOVERY_SYNC',
    scenarioName: '恢复同步',
    description: '目标场站从失联状态逐步恢复，先进入恢复同步再回到正常协同。',
    targetStationIds: ['pv-gobi-01'],
    expectedLinkState: LinkState.S4,
    durationSeconds: 120,
    faultParameters: { rampRatio: '0.35' },
    enabled: true
  },
  {
    scenarioCode: 'T10_CONTROL_ACK_LOSS',
    scenarioName: '控制确认丢失',
    description: '预留控制回执丢失实验，当前模拟器仅记录命令并保持遥测退化。',
    targetStationIds: ['storage-hub-03'],
    expectedLinkState: LinkState.S1,
    durationSeconds: 90,
    faultParameters: { ackBehavior: 'reserved' },
    enabled: true
  }
]

export const mockExperimentRuns: ExperimentRun[] = [
  {
    runId: 'mock-run-001',
    scenarioCode: 'T1_LATENCY_JITTER',
    scenarioName: '通信时延抖动',
    status: 'STOPPED',
    startedAt: '2026-06-30T15:20:00+08:00',
    endedAt: '2026-06-30T15:22:00+08:00',
    resultSummary: '目标场站 pv-gobi-01，时延抖动已触发 S1。'
  }
]

export const mockExperimentSummary: ExperimentSummary = {
  runId: 'mock-run-001',
  scenarioCode: 'T1_LATENCY_JITTER',
  scenarioName: '通信时延抖动',
  status: 'STOPPED',
  startedAt: '2026-06-30T15:20:00+08:00',
  stoppedAt: '2026-06-30T15:22:00+08:00',
  durationSeconds: 120,
  targetStationIds: ['pv-gobi-01'],
  sampleCount: 8,
  eventCount: 2,
  dominantState: LinkState.S1
}

export const mockExperimentComparison: ExperimentComparison = {
  run: mockExperimentRuns[0],
  targetStationIds: ['pv-gobi-01'],
  before: {
    sampleCount: 6,
    averagePower: 124.8,
    maxPower: 129.3,
    minPower: 121.6,
    averageLatency: 36.2,
    maxLatency: 42,
    averagePacketLossRate: 0.42,
    maxPacketLossRate: 0.7,
    averageDataAge: 1.1,
    maxDataAge: 2,
    onlineRate: 100,
    stateCounts: { S0: 6, S1: 0, S2: 0, S3: 0, S4: 0 }
  },
  during: {
    sampleCount: 8,
    averagePower: 119.4,
    maxPower: 126.2,
    minPower: 111.8,
    averageLatency: 152.6,
    maxLatency: 228,
    averagePacketLossRate: 2.48,
    maxPacketLossRate: 4.1,
    averageDataAge: 4.2,
    maxDataAge: 7,
    onlineRate: 100,
    stateCounts: { S0: 1, S1: 5, S2: 2, S3: 0, S4: 0 }
  }
}

export const mockExperimentTimeline: ExperimentTimelinePoint[] = [
  { timestamp: '2026-06-30T15:20:05+08:00', stationId: 'pv-gobi-01', linkState: LinkState.S0, latency: 42, packetLossRate: 0.6, online: true, eventTitle: '实验启动，记录稳定基线' },
  { timestamp: '2026-06-30T15:20:28+08:00', stationId: 'pv-gobi-01', linkState: LinkState.S1, latency: 118, packetLossRate: 1.8, online: true, eventTitle: '时延抖动进入 S1' },
  { timestamp: '2026-06-30T15:21:14+08:00', stationId: 'pv-gobi-01', linkState: LinkState.S2, latency: 228, packetLossRate: 4.1, online: true, eventTitle: '短时严重退化' },
  { timestamp: '2026-06-30T15:21:48+08:00', stationId: 'pv-gobi-01', linkState: LinkState.S1, latency: 136, packetLossRate: 2.2, online: true, eventTitle: '边缘协同稳定接管' },
  { timestamp: '2026-06-30T15:22:00+08:00', stationId: 'pv-gobi-01', linkState: LinkState.S0, latency: 44, packetLossRate: 0.5, online: true, eventTitle: '实验停止，恢复 stable' }
]

export const mockExperimentReplay: ExperimentReplayPoint[] = [
  { timestamp: '2026-06-30T15:20:05+08:00', stationId: 'pv-gobi-01', activePower: 126, availableCapacity: 168, latency: 42, packetLossRate: 0.6, dataAge: 1, linkState: LinkState.S0, online: true },
  { timestamp: '2026-06-30T15:20:20+08:00', stationId: 'pv-gobi-01', activePower: 124, availableCapacity: 168, latency: 96, packetLossRate: 1.4, dataAge: 3, linkState: LinkState.S1, online: true },
  { timestamp: '2026-06-30T15:20:35+08:00', stationId: 'pv-gobi-01', activePower: 121, availableCapacity: 168, latency: 142, packetLossRate: 2.6, dataAge: 4, linkState: LinkState.S1, online: true },
  { timestamp: '2026-06-30T15:20:50+08:00', stationId: 'pv-gobi-01', activePower: 116, availableCapacity: 168, latency: 188, packetLossRate: 3.5, dataAge: 6, linkState: LinkState.S2, online: true },
  { timestamp: '2026-06-30T15:21:05+08:00', stationId: 'pv-gobi-01', activePower: 112, availableCapacity: 168, latency: 228, packetLossRate: 4.1, dataAge: 7, linkState: LinkState.S2, online: true },
  { timestamp: '2026-06-30T15:21:20+08:00', stationId: 'pv-gobi-01', activePower: 118, availableCapacity: 168, latency: 152, packetLossRate: 2.9, dataAge: 4, linkState: LinkState.S1, online: true },
  { timestamp: '2026-06-30T15:21:40+08:00', stationId: 'pv-gobi-01', activePower: 122, availableCapacity: 168, latency: 124, packetLossRate: 2.1, dataAge: 3, linkState: LinkState.S1, online: true },
  { timestamp: '2026-06-30T15:22:00+08:00', stationId: 'pv-gobi-01', activePower: 126, availableCapacity: 168, latency: 44, packetLossRate: 0.5, dataAge: 1, linkState: LinkState.S0, online: true }
]

export const mockExperimentQualityMetrics: ExperimentQualityMetrics = {
  before: mockExperimentComparison.before,
  during: mockExperimentComparison.during,
  trends: mockExperimentReplay.map((point) => ({
    timestamp: point.timestamp,
    stationId: point.stationId,
    latency: point.latency,
    packetLossRate: point.packetLossRate,
    dataAge: point.dataAge
  })),
  stateDistribution: [
    { linkState: LinkState.S0, count: 1 },
    { linkState: LinkState.S1, count: 5 },
    { linkState: LinkState.S2, count: 2 },
    { linkState: LinkState.S3, count: 0 },
    { linkState: LinkState.S4, count: 0 }
  ],
  stationRanking: [
    { stationId: 'pv-gobi-01', averageLatency: 152.6, averagePacketLossRate: 2.48, averageDataAge: 4.2, onlineRate: 100, qualityScore: 61.4 }
  ]
}

export const baseStations: StationState[] = [
  {
    stationId: 'pv-gobi-01',
    stationName: '光伏',
    resourceType: ResourceType.Photovoltaic,
    activePower: 126,
    availableCapacity: 168,
    linkState: LinkState.S0,
    latency: 36,
    packetLossRate: 0.4,
    dataAge: 1,
    online: true,
    position: { x: 31, y: 28 }
  },
  {
    stationId: 'wind-ridge-02',
    stationName: '风电',
    resourceType: ResourceType.Wind,
    activePower: 178,
    availableCapacity: 220,
    linkState: LinkState.S0,
    latency: 42,
    packetLossRate: 0.7,
    dataAge: 1,
    online: true,
    position: { x: 72, y: 30 }
  },
  {
    stationId: 'thermal-plant-06',
    stationName: '火电',
    resourceType: ResourceType.Thermal,
    activePower: 96,
    availableCapacity: 150,
    linkState: LinkState.S0,
    latency: 39,
    packetLossRate: 0.5,
    dataAge: 2,
    online: true,
    position: { x: 52, y: 18 }
  },
  {
    stationId: 'grid-link-00',
    stationName: '市电',
    resourceType: ResourceType.Grid,
    activePower: 10,
    availableCapacity: 120,
    linkState: LinkState.S0,
    latency: 24,
    packetLossRate: 0.2,
    dataAge: 1,
    online: true,
    position: { x: 12, y: 54 }
  },
  {
    stationId: 'storage-hub-03',
    stationName: '储能',
    resourceType: ResourceType.Storage,
    activePower: 58,
    availableCapacity: 96,
    linkState: LinkState.S0,
    latency: 28,
    packetLossRate: 0.2,
    dataAge: 1,
    online: true,
    position: { x: 39, y: 83 }
  },
  {
    stationId: 'load-oasis-04',
    stationName: '负荷',
    resourceType: ResourceType.Load,
    activePower: 122,
    availableCapacity: 148,
    linkState: LinkState.S0,
    latency: 33,
    packetLossRate: 0.5,
    dataAge: 2,
    online: true,
    position: { x: 90, y: 55 }
  },
  {
    stationId: 'charge-corridor-05',
    stationName: '充电桩',
    resourceType: ResourceType.Charging,
    activePower: 24,
    availableCapacity: 44,
    linkState: LinkState.S0,
    latency: 31,
    packetLossRate: 0.4,
    dataAge: 1,
    online: true,
    position: { x: 70, y: 84 }
  }
]

export const baseTrend: TimelinePoint[] = [
  { label: '08:00', planPower: 312, actualPower: 305 },
  { label: '10:00', planPower: 336, actualPower: 330 },
  { label: '12:00', planPower: 362, actualPower: 354 },
  { label: '14:00', planPower: 378, actualPower: 375 },
  { label: '16:00', planPower: 368, actualPower: 362 },
  { label: '18:00', planPower: 340, actualPower: 338 },
  { label: '20:00', planPower: 298, actualPower: 294 }
]

export const baseEvents: AlarmEvent[] = [
  {
    eventId: 'event-001',
    title: '云边协同调度窗口已刷新',
    description: '最新滚动出力计划已下发至全部场站边缘控制器。',
    level: '提示',
    timestamp: '09:12:18'
  },
  {
    eventId: 'event-002',
    title: '风电功率预测偏差受控',
    description: '山口风电二场实测偏差低于 2.5%，保持正常协同。',
    level: '提示',
    timestamp: '10:04:26',
    relatedStationId: 'wind-ridge-02'
  },
  {
    eventId: 'event-003',
    title: '储能备用容量满足调频需求',
    description: '边塞储能站保留可调容量 38MW，可接管短时波动。',
    level: '提示',
    timestamp: '10:26:42',
    relatedStationId: 'storage-hub-03'
  }
]

export const resourceDetailMap: Record<string, ResourceDetail> = {
  'pv-gobi-01': {
    stationId: 'pv-gobi-01',
    overview: '光伏专题页聚焦逆变器状态、组串功率分布、限发约束和云边协同跟踪情况。',
    metrics: [
      { label: '当前出力', value: 126, unit: 'kW' },
      { label: '预测出力', value: 132, unit: 'kW' },
      { label: '环境修正因子', value: 0.82, unit: '' },
      { label: '修正后可用能力', value: 138, unit: 'kW' },
      { label: '辐照度', value: 786, unit: 'W/m2' },
      { label: '云量', value: 18, unit: '%' },
      { label: '组件温度', value: 42.8, unit: '°C' },
      { label: '积灰损失率', value: 4, unit: '%' }
    ],
    deviceStatuses: [
      { deviceId: 'pv-inv-01', deviceName: '逆变器 01', status: '运行', currentValue: '31.6 kW', description: '跟踪 MPPT 正常，温升可控。' },
      { deviceId: 'pv-inv-02', deviceName: '逆变器 02', status: '运行', currentValue: '32.1 kW', description: '并网侧电压波动小于 1.2%。' },
      { deviceId: 'pv-combiner-01', deviceName: '汇流箱 A', status: '待机', currentValue: '24 串', description: '待日照增强后提升出力。' }
    ],
    controlActions: [
      { actionId: 'pv-action-01', actionName: '功率柔性限发', status: '可执行', description: '按主站计划限制光伏输出爬坡速率。' },
      { actionId: 'pv-action-02', actionName: '无功电压支撑', status: '可执行', description: '按站内电压偏差参与无功调节。' },
      { actionId: 'pv-action-03', actionName: '边缘跟踪校准', status: '执行中', description: '边缘控制器正在对云侧预测偏差进行修正。' }
    ],
    powerTrend: [
      { label: '09:00', power: 84, target: 90 },
      { label: '10:00', power: 102, target: 108 },
      { label: '11:00', power: 116, target: 120 },
      { label: '12:00', power: 126, target: 132 },
      { label: '13:00', power: 122, target: 128 },
      { label: '14:00', power: 118, target: 124 }
    ],
    qualityTrend: [
      { label: '09:00', latency: 34, packetLossRate: 0.4 },
      { label: '10:00', latency: 36, packetLossRate: 0.5 },
      { label: '11:00', latency: 35, packetLossRate: 0.4 },
      { label: '12:00', latency: 36, packetLossRate: 0.4 },
      { label: '13:00', latency: 38, packetLossRate: 0.6 },
      { label: '14:00', latency: 37, packetLossRate: 0.5 }
    ],
    eventFocus: [
      { eventId: 'pv-event-01', title: '云侧光伏计划已下发', description: '12:00-14:00 时段目标出力曲线已同步至边缘控制器。', level: '提示', timestamp: '11:52:10' },
      { eventId: 'pv-event-02', title: '组串遮挡风险可控', description: '东南侧阵列局部遮挡未触发限发动作。', level: '提示', timestamp: '12:14:28' }
    ]
  },
  'wind-ridge-02': {
    stationId: 'wind-ridge-02',
    overview: '风电专题页展示风机簇状态、爬坡控制、偏航跟踪和风功率预测偏差。',
    metrics: [
      { label: '当前出力', value: 178, unit: 'kW' },
      { label: '预测风速', value: 8.4, unit: 'm/s' },
      { label: '环境修正因子', value: 0.76, unit: '' },
      { label: '修正后可用能力', value: 167.2, unit: 'kW' },
      { label: '湍流强度', value: 0.11, unit: '' },
      { label: '空气密度', value: 1.18, unit: 'kg/m3' },
      { label: '阵风风速', value: 12.4, unit: 'm/s' },
      { label: '偏航偏差', value: 3.2, unit: '°' }
    ],
    deviceStatuses: [
      { deviceId: 'wind-turbine-01', deviceName: '风机 A1', status: '运行', currentValue: '62.3 kW', description: '偏航角校准正常。' },
      { deviceId: 'wind-turbine-02', deviceName: '风机 A2', status: '运行', currentValue: '58.1 kW', description: '桨距角处于经济区间。' },
      { deviceId: 'wind-edge-01', deviceName: '风电边缘控制器', status: '运行', currentValue: '在线', description: '云边预测偏差闭环正常。' }
    ],
    controlActions: [
      { actionId: 'wind-action-01', actionName: '爬坡速率约束', status: '可执行', description: '限制风机群快速爬坡，抑制功率波动。' },
      { actionId: 'wind-action-02', actionName: '有功备用下调', status: '可执行', description: '为系统频率支撑预留旋转备用。' },
      { actionId: 'wind-action-03', actionName: '弱信号自治跟踪', status: '执行中', description: '链路退化时由边缘保持局部最优。' }
    ],
    powerTrend: [
      { label: '09:00', power: 142, target: 146 },
      { label: '10:00', power: 156, target: 160 },
      { label: '11:00', power: 168, target: 172 },
      { label: '12:00', power: 178, target: 182 },
      { label: '13:00', power: 172, target: 176 },
      { label: '14:00', power: 166, target: 170 }
    ],
    qualityTrend: [
      { label: '09:00', latency: 38, packetLossRate: 0.6 },
      { label: '10:00', latency: 40, packetLossRate: 0.7 },
      { label: '11:00', latency: 42, packetLossRate: 0.7 },
      { label: '12:00', latency: 43, packetLossRate: 0.8 },
      { label: '13:00', latency: 42, packetLossRate: 0.8 },
      { label: '14:00', latency: 41, packetLossRate: 0.7 }
    ],
    eventFocus: [
      { eventId: 'wind-event-01', title: '风机群功率偏差受控', description: '滚动预测与实测偏差低于 2.5%。', level: '提示', timestamp: '12:08:31' },
      { eventId: 'wind-event-02', title: '边缘爬坡约束已生效', description: '出力爬坡上限切换到 18kW/min。', level: '提示', timestamp: '12:19:42' }
    ]
  },
  'thermal-plant-06': {
    stationId: 'thermal-plant-06',
    overview: '火力发电专题页展示常规机组出力、爬坡约束、备用容量和调峰支撑能力。',
    metrics: [
      { label: '当前出力', value: 96, unit: 'kW' },
      { label: '可用容量', value: 150, unit: 'kW' },
      { label: '爬坡裕度', value: 18, unit: 'kW/min' },
      { label: '调峰状态', value: '可调', unit: '' }
    ],
    deviceStatuses: [
      { deviceId: 'thermal-generator-01', deviceName: '发电机组 01', status: '运行', currentValue: '96 kW', description: '有功出力稳定，参与常规供电。' },
      { deviceId: 'thermal-turbine-01', deviceName: '汽轮机', status: '运行', currentValue: '稳态', description: '转速与负荷跟踪正常。' },
      { deviceId: 'thermal-boiler-01', deviceName: '锅炉系统', status: '运行', currentValue: '保温', description: '维持调峰备用热态。' }
    ],
    controlActions: [
      { actionId: 'thermal-action-01', actionName: '有功出力调节', status: '可执行', description: '按调度计划调整常规机组出力。' },
      { actionId: 'thermal-action-02', actionName: '爬坡速率约束', status: '可执行', description: '限制机组爬坡，避免扰动系统频率。' },
      { actionId: 'thermal-action-03', actionName: '备用容量保持', status: '执行中', description: '保留旋转备用支撑新能源波动。' }
    ],
    powerTrend: [
      { label: '09:00', power: 88, target: 90 },
      { label: '10:00', power: 92, target: 94 },
      { label: '11:00', power: 95, target: 96 },
      { label: '12:00', power: 96, target: 98 },
      { label: '13:00', power: 94, target: 96 },
      { label: '14:00', power: 90, target: 92 }
    ],
    qualityTrend: [
      { label: '09:00', latency: 36, packetLossRate: 0.4 },
      { label: '10:00', latency: 38, packetLossRate: 0.5 },
      { label: '11:00', latency: 39, packetLossRate: 0.5 },
      { label: '12:00', latency: 39, packetLossRate: 0.5 },
      { label: '13:00', latency: 40, packetLossRate: 0.6 },
      { label: '14:00', latency: 39, packetLossRate: 0.5 }
    ],
    eventFocus: [
      { eventId: 'thermal-event-01', title: '火电调峰备用已就绪', description: '常规机组保留 18kW/min 爬坡裕度，支撑新能源波动。', level: '提示', timestamp: '12:12:18' }
    ]
  },
  'grid-link-00': {
    stationId: 'grid-link-00',
    overview: '市电专题页用于展示并网点潮流、变压器负载率和外部电网支撑状态。',
    metrics: [
      { label: '当前购电', value: 10, unit: 'kW' },
      { label: '负载率', value: 5, unit: '%' },
      { label: '备用容量', value: 120, unit: 'kW' },
      { label: '并网状态', value: '稳定', unit: '' }
    ],
    deviceStatuses: [
      { deviceId: 'grid-breaker-01', deviceName: '并网开关', status: '运行', currentValue: '合位', description: '开关状态正常，可执行隔离。' },
      { deviceId: 'grid-transformer-01', deviceName: '10kV 变压器', status: '运行', currentValue: '5%', description: '温度和负载率处于安全区间。' },
      { deviceId: 'grid-meter-01', deviceName: '双向表计', status: '运行', currentValue: '在线', description: '购售电潮流计量稳定。' }
    ],
    controlActions: [
      { actionId: 'grid-action-01', actionName: '并网功率约束', status: '可执行', description: '控制并网点最大购电功率。' },
      { actionId: 'grid-action-02', actionName: '峰谷套利切换', status: '可执行', description: '按时段电价切换购电策略。' },
      { actionId: 'grid-action-03', actionName: '孤网预切换准备', status: '已锁定', description: '仅在故障实验台中允许切入。' }
    ],
    powerTrend: [
      { label: '09:00', power: 18, target: 20 },
      { label: '10:00', power: 15, target: 18 },
      { label: '11:00', power: 12, target: 15 },
      { label: '12:00', power: 10, target: 12 },
      { label: '13:00', power: 14, target: 16 },
      { label: '14:00', power: 18, target: 20 }
    ],
    qualityTrend: [
      { label: '09:00', latency: 22, packetLossRate: 0.2 },
      { label: '10:00', latency: 24, packetLossRate: 0.2 },
      { label: '11:00', latency: 24, packetLossRate: 0.3 },
      { label: '12:00', latency: 25, packetLossRate: 0.2 },
      { label: '13:00', latency: 24, packetLossRate: 0.2 },
      { label: '14:00', latency: 23, packetLossRate: 0.2 }
    ],
    eventFocus: [
      { eventId: 'grid-event-01', title: '并网点潮流维持低位', description: '新能源覆盖率提升后购电功率降至 10kW。', level: '提示', timestamp: '12:03:08' }
    ]
  },
  'storage-hub-03': {
    stationId: 'storage-hub-03',
    overview: '储能专题页展示 SOC、充放电切换、备用容量和调频支撑能力。',
    metrics: [
      { label: '当前放电', value: 58, unit: 'kW' },
      { label: 'SOC', value: 63, unit: '%' },
      { label: '可用容量', value: 96, unit: 'kW' },
      { label: '调频状态', value: '待命', unit: '' }
    ],
    deviceStatuses: [
      { deviceId: 'storage-pcs-01', deviceName: 'PCS 01', status: '运行', currentValue: '58 kW', description: '处于并网放电状态。' },
      { deviceId: 'storage-bms-01', deviceName: '电池管理系统', status: '运行', currentValue: '63%', description: '单簇电压一致性良好。' },
      { deviceId: 'storage-edge-01', deviceName: '储能边缘策略器', status: '待机', currentValue: '待命', description: '等待频率事件触发。' }
    ],
    controlActions: [
      { actionId: 'storage-action-01', actionName: '充放电切换', status: '可执行', description: '按电价与负荷趋势切换充放电。' },
      { actionId: 'storage-action-02', actionName: '调频支撑预留', status: '可执行', description: '预留功率参与一次调频。' },
      { actionId: 'storage-action-03', actionName: '自治保底策略', status: '执行中', description: '弱信号场景下保持 SOC 安全边界。' }
    ],
    powerTrend: [
      { label: '09:00', power: 22, target: 24 },
      { label: '10:00', power: 34, target: 36 },
      { label: '11:00', power: 42, target: 44 },
      { label: '12:00', power: 58, target: 60 },
      { label: '13:00', power: 48, target: 52 },
      { label: '14:00', power: 32, target: 34 }
    ],
    qualityTrend: [
      { label: '09:00', latency: 26, packetLossRate: 0.2 },
      { label: '10:00', latency: 28, packetLossRate: 0.2 },
      { label: '11:00', latency: 29, packetLossRate: 0.3 },
      { label: '12:00', latency: 28, packetLossRate: 0.2 },
      { label: '13:00', latency: 29, packetLossRate: 0.3 },
      { label: '14:00', latency: 30, packetLossRate: 0.3 }
    ],
    eventFocus: [
      { eventId: 'storage-event-01', title: '备用容量满足调频窗口', description: '当前保留 38MW 调节裕度，可接管波动。', level: '提示', timestamp: '12:11:06' }
    ]
  },
  'load-oasis-04': {
    stationId: 'load-oasis-04',
    overview: '负荷专题页聚焦可控负荷削峰、需求响应执行率和边缘自治策略回执。',
    metrics: [
      { label: '当前用电', value: 122, unit: 'kW' },
      { label: '可调容量', value: 148, unit: 'kW' },
      { label: '响应执行率', value: 96, unit: '%' },
      { label: '控制状态', value: '已纳管', unit: '' }
    ],
    deviceStatuses: [
      { deviceId: 'load-feeder-01', deviceName: '冷站负荷簇', status: '运行', currentValue: '48.2 kW', description: '可执行 10% 负荷削减。' },
      { deviceId: 'load-feeder-02', deviceName: '生产支路', status: '运行', currentValue: '36.5 kW', description: '支持阶段性错峰。' },
      { deviceId: 'load-edge-01', deviceName: '需求响应终端', status: '运行', currentValue: '在线', description: '边缘自治策略已启用。' }
    ],
    controlActions: [
      { actionId: 'load-action-01', actionName: '柔性削峰', status: '可执行', description: '按计划执行分级负荷削减。' },
      { actionId: 'load-action-02', actionName: '需求响应回执', status: '执行中', description: '当前正在回传本轮执行效果。' },
      { actionId: 'load-action-03', actionName: '本地自治保底', status: '可执行', description: '失联时保持最小生产负荷。' }
    ],
    powerTrend: [
      { label: '09:00', power: 132, target: 128 },
      { label: '10:00', power: 138, target: 132 },
      { label: '11:00', power: 145, target: 138 },
      { label: '12:00', power: 122, target: 124 },
      { label: '13:00', power: 118, target: 120 },
      { label: '14:00', power: 126, target: 128 }
    ],
    qualityTrend: [
      { label: '09:00', latency: 31, packetLossRate: 0.4 },
      { label: '10:00', latency: 33, packetLossRate: 0.5 },
      { label: '11:00', latency: 34, packetLossRate: 0.5 },
      { label: '12:00', latency: 33, packetLossRate: 0.5 },
      { label: '13:00', latency: 35, packetLossRate: 0.6 },
      { label: '14:00', latency: 34, packetLossRate: 0.5 }
    ],
    eventFocus: [
      { eventId: 'load-event-01', title: '可控负荷削峰动作完成', description: '本轮执行削减 18kW，未影响关键生产支路。', level: '提示', timestamp: '12:16:03' }
    ]
  },
  'charge-corridor-05': {
    stationId: 'charge-corridor-05',
    overview: '充电专题页用于展示充电枪占用率、功率分配和有序充电策略执行效果。',
    metrics: [
      { label: '当前充电功率', value: 24, unit: 'kW' },
      { label: '枪口占用率', value: 62, unit: '%' },
      { label: '可调容量', value: 44, unit: 'kW' },
      { label: '充电策略', value: '有序充电', unit: '' }
    ],
    deviceStatuses: [
      { deviceId: 'charge-pile-01', deviceName: '直流桩 01', status: '运行', currentValue: '18.4 kW', description: '功率分配正常。' },
      { deviceId: 'charge-pile-02', deviceName: '交流桩 02', status: '待机', currentValue: '0 kW', description: '待新的车辆接入。' },
      { deviceId: 'charge-edge-01', deviceName: '有序充电控制器', status: '运行', currentValue: '在线', description: '根据场站负荷调整充电优先级。' }
    ],
    controlActions: [
      { actionId: 'charge-action-01', actionName: '有序充电调度', status: '可执行', description: '根据站内功率余量动态调节充电顺序。' },
      { actionId: 'charge-action-02', actionName: '削峰限流', status: '可执行', description: '负荷高峰阶段自动降低充电功率。' },
      { actionId: 'charge-action-03', actionName: '紧急保供切断', status: '已锁定', description: '仅在极端故障演示时使用。' }
    ],
    powerTrend: [
      { label: '09:00', power: 14, target: 16 },
      { label: '10:00', power: 18, target: 20 },
      { label: '11:00', power: 22, target: 24 },
      { label: '12:00', power: 24, target: 26 },
      { label: '13:00', power: 26, target: 28 },
      { label: '14:00', power: 20, target: 22 }
    ],
    qualityTrend: [
      { label: '09:00', latency: 29, packetLossRate: 0.4 },
      { label: '10:00', latency: 30, packetLossRate: 0.4 },
      { label: '11:00', latency: 31, packetLossRate: 0.4 },
      { label: '12:00', latency: 31, packetLossRate: 0.5 },
      { label: '13:00', latency: 32, packetLossRate: 0.5 },
      { label: '14:00', latency: 31, packetLossRate: 0.4 }
    ],
    eventFocus: [
      { eventId: 'charge-event-01', title: '有序充电策略生效', description: '高峰时段自动下调 2 把枪的充电功率。', level: '提示', timestamp: '12:21:16' }
    ]
  }
}

const mockDispatchBaseTime = new Date('2026-07-01T08:00:00+08:00')
const mockDispatchAvailableCapacity = baseStations.reduce(
  (totalCapacity, station) => totalCapacity + station.availableCapacity,
  0
)

export const mockDispatchPlan: DispatchPlanPoint[] = Array.from({ length: 24 }, (_, hourOffset) => {
  const timestamp = new Date(mockDispatchBaseTime.getTime() + hourOffset * 60 * 60 * 1000)
  const localHour = (8 + hourOffset) % 24
  const loadFactor =
    localHour < 6 ? 0.55 : localHour < 10 ? 0.72 : localHour < 16 ? 0.85 : localHour < 21 ? 0.92 : 0.68
  const planPower = Math.round(mockDispatchAvailableCapacity * loadFactor * 10) / 10
  return {
    timestamp: timestamp.toISOString(),
    planPower,
    availableCapacity: mockDispatchAvailableCapacity,
    reserveCapacity: Math.round((mockDispatchAvailableCapacity - planPower) * 10) / 10,
    loadFactor
  }
})

export const mockDispatchCommands: DispatchCommand[] = [
  {
    commandId: 'mock-command-003',
    batchId: 'mock-batch-002',
    stationId: 'storage-hub-03',
    targetPower: 68,
    controlMode: ControlMode.EdgeCoordination,
    status: 'EXECUTED',
    sendTime: '2026-07-01T08:18:30+08:00',
    expireAt: '2026-07-01T08:19:00+08:00',
    latestAckStage: 'EXECUTED',
    latestAckMessage: '场站按爬坡约束执行',
    latestAckTime: '2026-07-01T08:18:32+08:00',
    acknowledgements: [
      { ackStage: 'RECEIVED', success: true, message: '边缘控制器已接收', ackTime: '2026-07-01T08:18:31+08:00' },
      { ackStage: 'EXECUTED', success: true, message: '场站按爬坡约束执行', ackTime: '2026-07-01T08:18:32+08:00' }
    ]
  },
  {
    commandId: 'mock-command-002',
    batchId: 'mock-batch-001',
    stationId: 'wind-ridge-02',
    targetPower: 156,
    controlMode: ControlMode.EdgeCoordination,
    status: 'ACCEPTED',
    sendTime: '2026-07-01T08:16:10+08:00',
    expireAt: '2026-07-01T08:16:40+08:00',
    latestAckStage: 'RECEIVED',
    latestAckMessage: '场站边缘控制器已接收指令',
    latestAckTime: '2026-07-01T08:16:11+08:00',
    acknowledgements: [
      { ackStage: 'RECEIVED', success: true, message: '场站边缘控制器已接收指令', ackTime: '2026-07-01T08:16:11+08:00' }
    ]
  },
  {
    commandId: 'mock-command-001',
    batchId: 'mock-batch-001',
    stationId: 'pv-gobi-01',
    targetPower: 118,
    controlMode: ControlMode.EdgeCoordination,
    status: 'SENT',
    sendTime: '2026-07-01T08:16:10+08:00',
    expireAt: '2026-07-01T08:16:40+08:00',
    acknowledgements: []
  }
]

export const mockDispatchStatus: DispatchStatus = {
  controlMode: ControlMode.EdgeCoordination,
  adjustableStationCount: 6,
  adjustableCapacity: mockDispatchAvailableCapacity,
  currentPlanTarget: mockDispatchPlan[0]?.planPower ?? 0,
  successRate: 100,
  rejectionRate: 0,
  pendingCommandCount: 2,
  timeoutCount: 0,
  statusCounts: {
    CREATED: 0,
    SENT: 1,
    ACCEPTED: 1,
    EXECUTED: 1,
    REJECTED: 0,
    TIMEOUT: 0,
    FAILED: 0
  },
  latestBatchId: 'mock-batch-002',
  updatedAt: '2026-07-01T08:18:32+08:00'
}

export const mockAiProviderStatuses: AiProviderStatus[] = [
  { providerCode: 'deepseek', enabled: false, available: false, lastErrorAt: null }
]

export const mockFaultDiagnosisRun: FaultDiagnosisRun = {
  runId: 'mock-fault-run-c6',
  eventId: 'mock-event-c6',
  stationId: 'pv-gobi-01',
  status: 'READY',
  approvalStatus: 'NOT_REQUIRED',
  ruleDecision: {
    matched: true,
    faultCode: 'C6',
    faultType: 'FULL_DISCONNECTION',
    severity: 'HIGH',
    suggestedLinkState: LinkState.S3,
    requiresAiAnalysis: true,
    evidence: [
      { metric: 'heartbeatAge', value: '18', unit: 's', description: '连续心跳超过演示默认阈值' }
    ],
    immediateProtectionActions: [
      { actionType: 'FREEZE_EXPIRED_COMMANDS', stationId: 'pv-gobi-01', parameters: {}, reason: '冻结远程过期指令' },
      { actionType: 'ENTER_LOCAL_AUTONOMY', stationId: 'pv-gobi-01', parameters: {}, reason: '进入本地安全计划' }
    ]
  },
  agentResults: [
    ['COMMUNICATION_QUALITY', '确认完全失联并建议 S3'],
    ['STATE_DIAGNOSIS', '当前云侧数据不再可信'],
    ['CONTROL_DECISION', '切换本地自治并剔除容量'],
    ['RECOVERY_ORCHESTRATION', '恢复前等待身份与快照校验'],
    ['SECURITY_AUDIT', '未发现明确攻击证据']
  ].map(([agentType, riskExplanation]) => ({
    agentType: agentType as FaultDiagnosisRun['agentResults'][number]['agentType'],
    provider: 'rule',
    faultCode: 'C6',
    faultType: 'FULL_DISCONNECTION',
    affectedStationIds: ['pv-gobi-01'],
    severity: 'HIGH',
    confidence: 0.95,
    evidence: [{ metric: 'heartbeatAge', value: '18', unit: 's', description: '连续心跳超时' }],
    recommendedMode: ControlMode.LocalAutonomy,
    proposedActions: [],
    requiresHumanApproval: false,
    riskExplanation
  })),
  safetyDecision: {
    approvedActions: [
      { actionType: 'FREEZE_EXPIRED_COMMANDS', stationId: 'pv-gobi-01', parameters: {}, reason: '冻结远程指令' },
      { actionType: 'ENTER_LOCAL_AUTONOMY', stationId: 'pv-gobi-01', parameters: {}, reason: '进入本地自治' }
    ],
    requiresHumanApproval: false,
    conflictDetected: false,
    fallbackUsed: true,
    explanation: '高风险失联处置已由安全门自动裁决，运行人员只观察证据、状态和回执。'
  },
  actions: [
    {
      actionId: 'mock-action-freeze',
      stationId: 'pv-gobi-01',
      actionType: 'FREEZE_EXPIRED_COMMANDS',
      parameters: {},
      riskLevel: 'HIGH',
      status: 'READY',
      requiresApproval: false
    },
    {
      actionId: 'mock-action-autonomy',
      stationId: 'pv-gobi-01',
      actionType: 'ENTER_LOCAL_AUTONOMY',
      parameters: {},
      riskLevel: 'HIGH',
      status: 'READY',
      requiresApproval: false
    }
  ],
  conflictDetected: false,
  fallbackUsed: true,
  createdAt: '2026-07-01T10:00:00+08:00',
  updatedAt: '2026-07-01T10:00:01+08:00'
}

type DeviceCatalogItem = {
  deviceId: string
  deviceName: string
  deviceType: string
  controllable: boolean
}

const stationDeviceCatalogMap: Record<string, DeviceCatalogItem[]> = {
  'pv-gobi-01': [
    { deviceId: 'pv-array-a', deviceName: '组件方阵 A', deviceType: 'pv-array', controllable: false },
    { deviceId: 'pv-array-b', deviceName: '组件方阵 B', deviceType: 'pv-array', controllable: false },
    { deviceId: 'inverter-group', deviceName: '逆变器组', deviceType: 'inverter-group', controllable: true },
    { deviceId: 'combiner-box-01', deviceName: '汇流箱', deviceType: 'combiner-box', controllable: false },
    { deviceId: 'weather-station-01', deviceName: '气象站', deviceType: 'weather-station', controllable: false }
  ],
  'wind-ridge-02': [
    { deviceId: 'wind-turbine-a', deviceName: '风机 A', deviceType: 'wind-turbine', controllable: true },
    { deviceId: 'wind-turbine-b', deviceName: '风机 B', deviceType: 'wind-turbine', controllable: true },
    { deviceId: 'yaw-controller-01', deviceName: '偏航控制器', deviceType: 'yaw-controller', controllable: false },
    { deviceId: 'anemometer-01', deviceName: '测风仪', deviceType: 'anemometer', controllable: false },
    { deviceId: 'wind-edge-01', deviceName: '风电边缘控制器', deviceType: 'edge-controller', controllable: false }
  ],
  'thermal-plant-06': [
    { deviceId: 'thermal-generator-01', deviceName: '发电机组 01', deviceType: 'thermal-generator', controllable: true },
    { deviceId: 'thermal-turbine-01', deviceName: '汽轮机', deviceType: 'steam-turbine', controllable: false },
    { deviceId: 'thermal-boiler-01', deviceName: '锅炉系统', deviceType: 'boiler-system', controllable: false },
    { deviceId: 'thermal-meter-01', deviceName: '厂用电表计', deviceType: 'thermal-meter', controllable: false },
    { deviceId: 'thermal-edge-01', deviceName: '火电边缘控制器', deviceType: 'edge-controller', controllable: false }
  ],
  'grid-link-00': [
    { deviceId: 'grid-breaker-01', deviceName: '并网开关', deviceType: 'grid-breaker', controllable: true },
    { deviceId: 'grid-transformer-01', deviceName: '10kV 变压器', deviceType: 'grid-transformer', controllable: false },
    { deviceId: 'grid-meter-01', deviceName: '双向表计', deviceType: 'grid-meter', controllable: false },
    { deviceId: 'grid-protection-01', deviceName: '保护测控单元', deviceType: 'protection-relay', controllable: false },
    { deviceId: 'grid-edge-01', deviceName: '并网边缘网关', deviceType: 'edge-controller', controllable: false }
  ],
  'storage-hub-03': [
    { deviceId: 'storage-pcs-01', deviceName: 'PCS 01', deviceType: 'storage-pcs', controllable: true },
    { deviceId: 'storage-bms-01', deviceName: '电池管理系统', deviceType: 'battery-management', controllable: false },
    { deviceId: 'battery-cluster-01', deviceName: '电池簇 01', deviceType: 'battery-cluster', controllable: false },
    { deviceId: 'thermal-unit-01', deviceName: '温控单元', deviceType: 'thermal-management', controllable: false },
    { deviceId: 'storage-edge-01', deviceName: '储能边缘策略器', deviceType: 'edge-controller', controllable: false }
  ],
  'load-oasis-04': [
    { deviceId: 'load-feeder-01', deviceName: '冷站负荷簇', deviceType: 'load-feeder', controllable: true },
    { deviceId: 'load-feeder-02', deviceName: '生产支路', deviceType: 'load-feeder', controllable: true },
    { deviceId: 'hvac-controller-01', deviceName: '空调群控器', deviceType: 'hvac-controller', controllable: false },
    { deviceId: 'load-meter-01', deviceName: '负荷表计', deviceType: 'load-meter', controllable: false },
    { deviceId: 'load-edge-01', deviceName: '需求响应终端', deviceType: 'edge-controller', controllable: false }
  ],
  'charge-corridor-05': [
    { deviceId: 'charge-pile-01', deviceName: '直流桩 01', deviceType: 'charging-pile', controllable: true },
    { deviceId: 'charge-pile-02', deviceName: '交流桩 02', deviceType: 'charging-pile', controllable: true },
    { deviceId: 'charge-distributor-01', deviceName: '功率分配单元', deviceType: 'charge-distributor', controllable: false },
    { deviceId: 'queue-sensor-01', deviceName: '排队检测器', deviceType: 'queue-sensor', controllable: false },
    { deviceId: 'charge-edge-01', deviceName: '有序充电控制器', deviceType: 'edge-controller', controllable: false }
  ]
}

function findMockStation(stationId: string): StationState {
  return baseStations.find((station) => station.stationId === stationId) ?? baseStations[0]
}

function isoTime(label: string): string {
  return `2026-07-03T${label}:00+08:00`
}

function capacityRatio(station: StationState): number {
  if (station.availableCapacity <= 0) {
    return 0
  }
  return Math.max(0, Math.min(1, station.activePower / station.availableCapacity))
}

function deviceLatencyOffset(deviceId: string): number {
  if (deviceId === 'weather-station-01') {
    return 7
  }
  if (['anemometer-01', 'queue-sensor-01'].includes(deviceId)) {
    return 6
  }
  if (['wind-edge-01', 'grid-edge-01', 'storage-edge-01', 'load-edge-01', 'charge-edge-01'].includes(deviceId)) {
    return 5
  }
  if (deviceId === 'grid-meter-01') {
    return 3
  }
  return 0
}

function devicePacketLossOffset(deviceId: string): number {
  if (deviceId === 'weather-station-01') {
    return 0.2
  }
  if (['anemometer-01', 'queue-sensor-01'].includes(deviceId)) {
    return 0.2
  }
  if (['wind-edge-01', 'grid-edge-01', 'storage-edge-01', 'load-edge-01', 'charge-edge-01'].includes(deviceId)) {
    return 0.1
  }
  return 0
}

function rounded(value: number): number {
  return Math.round(value * 10) / 10
}

function deviceStatus(station: StationState, deviceId: string, activePower: number | null): '运行' | '待机' | '告警' | '离线' {
  if (!station.online || station.linkState === LinkState.S3) {
    return '离线'
  }
  if (station.linkState === LinkState.S2) {
    return '告警'
  }
  if (deviceId === 'charge-pile-02' || (activePower !== null && Math.abs(activePower) < 0.1)) {
    return '待机'
  }
  return '运行'
}

function communicationStatus(linkState: LinkState, online: boolean): string {
  if (!online) {
    return '失联'
  }
  return linkStateMetaMap[linkState].label
}

function deriveDevicePower(station: StationState, deviceId: string): number | null {
  const dcBridgePower = rounded(station.activePower / 0.978)
  switch (deviceId) {
    case 'pv-array-a':
      return rounded(dcBridgePower * 0.56)
    case 'pv-array-b':
      return rounded(dcBridgePower * 0.44)
    case 'inverter-group':
      return rounded(station.activePower)
    case 'combiner-box-01':
      return dcBridgePower
    case 'weather-station-01':
    case 'yaw-controller-01':
    case 'anemometer-01':
    case 'wind-edge-01':
    case 'storage-bms-01':
    case 'battery-cluster-01':
    case 'thermal-unit-01':
    case 'storage-edge-01':
    case 'hvac-controller-01':
    case 'load-meter-01':
    case 'load-edge-01':
    case 'charge-distributor-01':
    case 'queue-sensor-01':
    case 'charge-edge-01':
      return null
    case 'wind-turbine-a':
      return rounded(station.activePower * 0.52)
    case 'wind-turbine-b':
      return rounded(station.activePower * 0.48)
    case 'grid-breaker-01':
    case 'grid-transformer-01':
    case 'grid-meter-01':
    case 'storage-pcs-01':
      return rounded(station.activePower)
    case 'load-feeder-01':
      return rounded(station.activePower * 0.56)
    case 'load-feeder-02':
      return rounded(station.activePower * 0.44)
    case 'charge-pile-01':
      return rounded(station.activePower * 0.76)
    case 'charge-pile-02':
      return rounded(station.activePower * 0.24)
    default:
      return rounded(station.activePower)
  }
}

function deriveDeviceMetrics(station: StationState, device: DeviceCatalogItem): ResourceMetric[] {
  const ratio = capacityRatio(station)
  const activePower = deriveDevicePower(station, device.deviceId)
  const irradiance = rounded(320 + ratio * 620)
  const moduleTemperature = rounded(20 + ratio * 19 + station.packetLossRate * 0.1)
  const ambientTemperature = rounded(14 + ratio * 15)
  const windSpeed = rounded(2.6 + ratio * 5.4)
  const cloudCover = rounded(12 + station.packetLossRate * 1.5)
  const soilingLossRate = rounded(3 + station.packetLossRate * 0.2)
  const shadingLossRate = rounded(2 + station.packetLossRate * 0.1)
  const windDirection = rounded(185 + station.latency * 0.2)
  const turbulenceIntensity = rounded(0.1 + station.packetLossRate * 0.01)
  const airDensity = rounded(1.2 - ratio * 0.03)
  const gustSpeed = rounded(windSpeed + 3.8)
  const yawDeviation = rounded(station.packetLossRate * 0.7 + 1.2)
  const dcBridgePower = rounded(station.activePower / 0.978)
  const dcVoltage = rounded(845 + ratio * 42)
  const acVoltage = rounded(378 + Math.min(station.latency, 150) * 0.04)
  const efficiency = rounded(station.activePower <= 0 ? 0 : (station.activePower / Math.max(dcBridgePower, 1)) * 100)

  switch (device.deviceId) {
    case 'pv-array-a':
    case 'pv-array-b':
      return [
        { label: '辐照度', value: irradiance, unit: 'W/m2' },
        { label: '组件温度', value: moduleTemperature, unit: '°C' },
        { label: '云量', value: cloudCover, unit: '%' },
        { label: '直流功率', value: activePower ?? 0, unit: 'kW' }
      ]
    case 'inverter-group':
      return [
        { label: '交流功率', value: activePower ?? 0, unit: 'kW' },
        { label: '转换效率', value: efficiency, unit: '%' },
        { label: '直流电压', value: dcVoltage, unit: 'V' },
        { label: '交流电压', value: acVoltage, unit: 'V' }
      ]
    case 'combiner-box-01':
      return [
        { label: '输入支路数', value: 24, unit: '路' },
        { label: '母线电压', value: dcVoltage, unit: 'V' },
        { label: '输出电流', value: rounded((dcBridgePower * 1000) / Math.max(dcVoltage, 1)), unit: 'A' }
      ]
    case 'weather-station-01':
      return [
        { label: '辐照度', value: irradiance, unit: 'W/m2' },
        { label: '环境温度', value: ambientTemperature, unit: '°C' },
        { label: '云量', value: cloudCover, unit: '%' },
        { label: '积灰损失率', value: soilingLossRate, unit: '%' },
        { label: '遮挡损失率', value: shadingLossRate, unit: '%' },
        { label: '风速', value: windSpeed, unit: 'm/s' }
      ]
    case 'wind-turbine-a':
    case 'wind-turbine-b':
      return [
        { label: '风速', value: rounded(windSpeed + 1.6), unit: 'm/s' },
        { label: '叶轮转速', value: rounded(12 + windSpeed * 0.8), unit: 'rpm' },
        { label: '湍流强度', value: turbulenceIntensity, unit: '' },
        { label: '阵风风速', value: gustSpeed, unit: 'm/s' },
        { label: '机舱温度', value: rounded(24 + station.packetLossRate * 0.4), unit: '°C' }
      ]
    case 'yaw-controller-01':
      return [
        { label: '偏航角', value: rounded(12 + windSpeed * 2.1), unit: '°' },
        { label: '角度偏差', value: yawDeviation, unit: '°' },
        { label: '执行状态', value: station.online ? '闭环跟踪' : '失联', unit: '' }
      ]
    case 'anemometer-01':
      return [
        { label: '风速', value: rounded(windSpeed + 1.8), unit: 'm/s' },
        { label: '风向', value: windDirection, unit: '°' },
        { label: '空气密度', value: airDensity, unit: 'kg/m3' },
        { label: '环境温度', value: ambientTemperature, unit: '°C' }
      ]
    case 'wind-edge-01':
      return [
        { label: '协同状态', value: communicationStatus(station.linkState, station.online), unit: '' },
        { label: '控制步长', value: 8, unit: 'kW' },
        { label: '自治窗口', value: 90, unit: 's' }
      ]
    case 'thermal-generator-01':
      return [
        { label: '有功出力', value: activePower ?? 0, unit: 'kW' },
        { label: '频率', value: rounded(49.96 + station.packetLossRate * 0.01), unit: 'Hz' },
        { label: '功率因数', value: rounded(0.95 + station.packetLossRate * 0.002), unit: '' }
      ]
    case 'thermal-turbine-01':
      return [
        { label: '转速', value: 3000, unit: 'rpm' },
        { label: '缸温', value: rounded(515 + station.packetLossRate * 0.8), unit: '°C' },
        { label: '调门开度', value: rounded(62 + ratio * 18), unit: '%' }
      ]
    case 'thermal-boiler-01':
      return [
        { label: '蒸汽压力', value: rounded(13.2 + ratio * 1.6), unit: 'MPa' },
        { label: '炉膛温度', value: rounded(850 + ratio * 60), unit: '°C' },
        { label: '负荷率', value: rounded(ratio * 100), unit: '%' }
      ]
    case 'thermal-meter-01':
      return [
        { label: '厂用电率', value: 6.2, unit: '%' },
        { label: '上网功率', value: activePower ?? 0, unit: 'kW' },
        { label: '计量状态', value: station.online ? '在线' : '离线', unit: '' }
      ]
    case 'thermal-edge-01':
      return [
        { label: '边缘链路', value: communicationStatus(station.linkState, station.online), unit: '' },
        { label: '爬坡约束', value: 18, unit: 'kW/min' },
        { label: '备用策略', value: '旋转备用', unit: '' }
      ]
    case 'grid-breaker-01':
      return [
        { label: '开关状态', value: station.online ? '合位' : '分位', unit: '' },
        { label: '交换功率', value: activePower ?? 0, unit: 'kW' },
        { label: '保护状态', value: station.linkState === LinkState.S3 ? '待孤网' : '稳定', unit: '' }
      ]
    case 'grid-transformer-01':
      return [
        { label: '负载率', value: rounded((station.activePower / Math.max(station.availableCapacity, 1)) * 100), unit: '%' },
        { label: '母线电压', value: acVoltage, unit: 'V' },
        { label: '油温', value: rounded(38 + station.packetLossRate * 0.5), unit: '°C' }
      ]
    case 'grid-meter-01':
      return [
        { label: '双向功率', value: activePower ?? 0, unit: 'kW' },
        { label: '频率', value: rounded(49.95 + station.packetLossRate * 0.01), unit: 'Hz' },
        { label: '计量状态', value: station.online ? '在线' : '离线', unit: '' }
      ]
    case 'grid-protection-01':
      return [
        { label: '保护状态', value: station.linkState === LinkState.S3 ? '孤网保护' : '投入', unit: '' },
        { label: '动作门槛', value: 110, unit: 'kV' },
        { label: '最近自检', value: '通过', unit: '' }
      ]
    case 'grid-edge-01':
      return [
        { label: '边缘链路', value: communicationStatus(station.linkState, station.online), unit: '' },
        { label: '策略缓存', value: 12, unit: '条' },
        { label: '自治窗口', value: 120, unit: 's' }
      ]
    case 'storage-pcs-01':
      return [
        { label: '交流功率', value: activePower ?? 0, unit: 'kW' },
        { label: '直流母线', value: dcVoltage, unit: 'V' },
        { label: '转换效率', value: rounded(95.8 + Math.max(0, 1 - station.packetLossRate * 0.02)), unit: '%' }
      ]
    case 'storage-bms-01':
      return [
        { label: 'SOC', value: rounded(58 + station.activePower / 12), unit: '%' },
        { label: '簇温差', value: rounded(2.1 + station.packetLossRate * 0.1), unit: '°C' },
        { label: '单簇电压', value: rounded(715 + station.activePower * 0.2), unit: 'V' }
      ]
    case 'battery-cluster-01':
      return [
        { label: '簇电流', value: rounded(Math.max(station.activePower, 1) * 2.8), unit: 'A' },
        { label: '簇电压', value: rounded(702 + station.activePower * 0.18), unit: 'V' },
        { label: '健康度', value: rounded(96.4 - station.packetLossRate * 0.2), unit: '%' }
      ]
    case 'thermal-unit-01':
      return [
        { label: '冷却液温度', value: rounded(23 + station.packetLossRate * 0.5), unit: '°C' },
        { label: '风机转速', value: rounded(820 + station.activePower * 4.2), unit: 'rpm' },
        { label: '温控状态', value: station.online ? '运行' : '失联', unit: '' }
      ]
    case 'storage-edge-01':
      return [
        { label: '自治策略', value: station.linkState === LinkState.S3 ? '已接管' : '待命', unit: '' },
        { label: '爬坡约束', value: 12, unit: 'kW/min' },
        { label: 'SOC 安全边界', value: '10% - 90%', unit: '' }
      ]
    case 'load-feeder-01':
    case 'load-feeder-02':
      return [
        { label: '当前用电', value: activePower ?? 0, unit: 'kW' },
        { label: '可调比例', value: device.deviceId === 'load-feeder-01' ? 12 : 8, unit: '%' },
        { label: '响应状态', value: station.linkState === LinkState.S3 ? '本地保底' : '可协同', unit: '' }
      ]
    case 'load-edge-01':
      return [
        { label: '协调窗口', value: 60, unit: 's' },
        { label: '响应终端', value: station.online ? '在线' : '离线', unit: '' },
        { label: '本地策略', value: station.linkState === LinkState.S3 ? '已启用' : '待命', unit: '' }
      ]
    case 'hvac-controller-01':
      return [
        { label: '冷机台数', value: 4, unit: '台' },
        { label: '设定温度', value: rounded(22 + station.packetLossRate * 0.2), unit: '°C' },
        { label: '控制状态', value: station.online ? '联动' : '失联', unit: '' }
      ]
    case 'load-meter-01':
      return [
        { label: '表计功率', value: rounded(station.activePower), unit: 'kW' },
        { label: '功率因数', value: rounded(0.94 + station.packetLossRate * 0.002), unit: '' },
        { label: '计量状态', value: station.online ? '在线' : '离线', unit: '' }
      ]
    case 'charge-pile-01':
    case 'charge-pile-02':
      return [
        { label: '充电功率', value: activePower ?? 0, unit: 'kW' },
        { label: '枪口占用', value: device.deviceId === 'charge-pile-01' ? 1 : 0, unit: '把' },
        { label: '排队车辆', value: device.deviceId === 'charge-pile-01' ? 2 : 0, unit: '辆' }
      ]
    case 'charge-distributor-01':
      return [
        { label: '分配通道', value: 6, unit: '路' },
        { label: '当前充电功率', value: rounded(station.activePower), unit: 'kW' },
        { label: '调度状态', value: station.online ? '启用' : '失联', unit: '' }
      ]
    case 'queue-sensor-01':
      return [
        { label: '排队车辆', value: 3, unit: '辆' },
        { label: '平均等待', value: 8, unit: 'min' },
        { label: '检测状态', value: station.online ? '在线' : '离线', unit: '' }
      ]
    case 'charge-edge-01':
      return [
        { label: '有序策略', value: station.linkState === LinkState.S3 ? '本地保守' : '云边协同', unit: '' },
        { label: '限流步长', value: 6, unit: 'kW' },
        { label: '优先队列', value: 3, unit: '级' }
      ]
    default:
      return [{ label: '当前功率', value: activePower ?? 0, unit: 'kW' }]
  }
}

export function buildMockStationDevices(stationId: string): StationDeviceSnapshot[] {
  const station = findMockStation(stationId)
  return (stationDeviceCatalogMap[stationId] ?? []).map((device) => {
    const activePower = deriveDevicePower(station, device.deviceId)
    const linkState = device.deviceId === 'weather-station-01' && station.linkState === LinkState.S1 ? LinkState.S2 : station.linkState
    return {
      deviceId: device.deviceId,
      stationId,
      deviceName: device.deviceName,
      deviceType: device.deviceType,
      status: deviceStatus(station, device.deviceId, activePower),
      activePower,
      communicationStatus: communicationStatus(linkState, station.online),
      linkState,
      latency: station.latency + deviceLatencyOffset(device.deviceId),
      packetLossRate: rounded(station.packetLossRate + devicePacketLossOffset(device.deviceId)),
      dataAge: station.dataAge + (device.deviceId.includes('edge') || device.deviceId === 'weather-station-01' ? 1 : 0),
      updatedAt: '2026-07-03T15:18:00+08:00',
      controllable: device.controllable,
      metrics: deriveDeviceMetrics(station, device)
    }
  })
}

export function buildMockStationDeviceDetail(stationId: string, deviceId: string): StationDeviceDetail | undefined {
  const device = buildMockStationDevices(stationId).find((item) => item.deviceId === deviceId)
  if (!device) {
    return undefined
  }
  return {
    ...device,
    minimumPower: device.controllable && device.activePower !== null ? 0 : null,
    maximumPower:
      device.controllable && device.activePower !== null ? rounded(Math.max(device.activePower, findMockStation(stationId).availableCapacity * 0.55)) : null
  }
}

function trendPoint(timestamp: string, primaryValue: number | null, secondaryValue: number | null): StationDeviceTrendPoint {
  return { timestamp, primaryValue, secondaryValue }
}

export function buildMockStationDevicePowerTrend(stationId: string, deviceId: string): StationDevicePowerTrend {
  const station = findMockStation(stationId)
  const labels = ['14:50', '14:55', '15:00', '15:05', '15:10', '15:15']
  const points = labels.map((label, index) => {
    const factor = 0.92 + index * 0.03
    switch (deviceId) {
      case 'weather-station-01':
        return trendPoint(isoTime(label), rounded(648 * factor), rounded(20 + index * 1.2))
      case 'anemometer-01':
        return trendPoint(isoTime(label), rounded((2.6 + factor * 6.8) * 10) / 10, rounded(16 + index * 0.8))
      case 'pv-array-a':
        return trendPoint(isoTime(label), rounded((deriveDevicePower(station, deviceId) ?? 0) * factor), rounded((deriveDevicePower(station, deviceId) ?? 0) * 1.04))
      case 'pv-array-b':
        return trendPoint(isoTime(label), rounded((deriveDevicePower(station, deviceId) ?? 0) * factor), rounded((deriveDevicePower(station, deviceId) ?? 0) * 1.05))
      case 'yaw-controller-01':
        return trendPoint(isoTime(label), rounded(10 + factor * 7), rounded(9 + factor * 6))
      case 'grid-protection-01':
        return trendPoint(isoTime(label), rounded(100 + index * 1.5), rounded(110 + index))
      case 'battery-cluster-01':
        return trendPoint(isoTime(label), rounded(690 + index * 6.5), rounded(700 + index * 5.8))
      case 'thermal-unit-01':
        return trendPoint(isoTime(label), rounded(22 + index * 0.7), rounded(24 + index * 0.5))
      case 'hvac-controller-01':
        return trendPoint(isoTime(label), rounded(21 + index * 0.3), rounded(20.5 + index * 0.2))
      case 'load-meter-01':
        return trendPoint(isoTime(label), rounded(station.activePower * factor), rounded(station.activePower * factor * 0.98))
      case 'charge-distributor-01':
        return trendPoint(isoTime(label), rounded(station.activePower * factor), rounded(station.activePower * factor * 0.92))
      case 'queue-sensor-01':
        return trendPoint(isoTime(label), rounded(3 + index * 0.2), rounded(8 + index * 0.3))
      default:
        return trendPoint(isoTime(label), rounded((deriveDevicePower(station, deviceId) ?? station.activePower * 0.12) * factor), rounded((deriveDevicePower(station, deviceId) ?? station.activePower * 0.12) * 1.03))
    }
  })
  if (deviceId === 'weather-station-01') {
    return {
      deviceId,
      mode: 'environment',
      primaryLabel: '辐照度',
      primaryUnit: 'W/m2',
      secondaryLabel: '环境温度',
      secondaryUnit: '°C',
      points
    }
  }
  if (deviceId === 'anemometer-01') {
    return {
      deviceId,
      mode: 'environment',
      primaryLabel: '风速',
      primaryUnit: 'm/s',
      secondaryLabel: '环境温度',
      secondaryUnit: '°C',
      points
    }
  }
  return {
    deviceId,
    mode: 'power',
    primaryLabel: '实测功率',
    primaryUnit: 'kW',
    secondaryLabel: '目标功率',
    secondaryUnit: 'kW',
    points
  }
}

export function buildMockStationDeviceQualityTrend(stationId: string, deviceId: string): StationDeviceQualityTrendPoint[] {
  const station = findMockStation(stationId)
  const labels = ['14:50', '14:55', '15:00', '15:05', '15:10', '15:15']
  return labels.map((label, index) => ({
    timestamp: isoTime(label),
    latency: station.latency + deviceLatencyOffset(deviceId) + (index % 2 === 0 ? 2 : 4),
    packetLossRate: rounded(station.packetLossRate + devicePacketLossOffset(deviceId) + (index % 3) * 0.1),
    dataAge: station.dataAge + (deviceId.includes('edge') ? 1 : 0),
    linkState: deviceId === 'weather-station-01' && station.linkState === LinkState.S1 ? LinkState.S2 : station.linkState
  }))
}

export const mockMarketSignal: MarketSignalSnapshot = {
  snapshotId: 'market-signal-mock-v1',
  marketType: 'TEST_DAY_AHEAD',
  intervalStart: '2026-07-04T16:00:00+08:00',
  intervalEnd: '2026-07-04T17:00:00+08:00',
  contractPower: 320,
  contractPrice: '0.452000',
  dayAheadPrice: '0.486000',
  realTimePrice: '0.518000',
  gridImportLimit: 80,
  gridExportLimit: 26,
  reserveUpRequirement: 18,
  reserveDownRequirement: 12,
  generatedAt: '2026-07-04T15:40:00+08:00',
  validFrom: '2026-07-04T16:00:00+08:00',
  validUntil: '2026-07-04T17:00:00+08:00',
  source: 'TEST_MARKET_DATA',
  qualityCode: 'GOOD',
  version: 'market-v1',
  receivedAt: '2026-07-04T15:40:05+08:00'
}

export const mockForecastSnapshot: ForecastSnapshot = {
  snapshotId: 'forecast-mock-v1',
  intervalMinutes: 60,
  modelVersion: 'forecast-model-v1',
  generatedAt: '2026-07-04T15:42:00+08:00',
  validFrom: '2026-07-04T16:00:00+08:00',
  validUntil: '2026-07-05T16:00:00+08:00',
  source: 'TEST_FORECAST_DATA',
  qualityCode: 'GOOD',
  version: 'forecast-v1',
  receivedAt: '2026-07-04T15:42:04+08:00',
  points: Array.from({ length: 6 }).map((_, index) => {
    const hour = 16 + index
    const hourText = String(hour).padStart(2, '0')
    return {
      intervalStart: `2026-07-04T${hourText}:00:00+08:00`,
      intervalEnd: `2026-07-04T${String(hour + 1).padStart(2, '0')}:00:00+08:00`,
      loadP10: 250 + index * 8,
      loadP50: 268 + index * 10,
      loadP90: 288 + index * 12,
      photovoltaicP10: 82 - index * 6,
      photovoltaicP50: 98 - index * 7,
      photovoltaicP90: 110 - index * 8,
      windP10: 62 + index * 3,
      windP50: 74 + index * 4,
      windP90: 88 + index * 5,
      priceP10: (0.438 + index * 0.01).toFixed(6),
      priceP50: (0.486 + index * 0.012).toFixed(6),
      priceP90: (0.536 + index * 0.014).toFixed(6)
    }
  })
}

export const mockCapabilitySnapshots: ResourceCapabilitySnapshot[] = baseStations.map((station, index) => ({
  capabilityId: `capability-${station.stationId}`,
  snapshotGroupId: 'capability-group-mock',
  stationId: station.stationId,
  resourceType: station.resourceType,
  currentPower: station.activePower,
  availableCapacity: correctedMockAvailableCapacity(station),
  minimumPower: station.resourceType === ResourceType.Storage ? -correctedMockAvailableCapacity(station) : 0,
  maximumPower: correctedMockAvailableCapacity(station),
  maximumRampUp: station.resourceType === ResourceType.Storage ? 28 : 20,
  maximumRampDown: station.resourceType === ResourceType.Storage ? 28 : 20,
  availableEnergyKwh: station.resourceType === ResourceType.Storage ? 240 : correctedMockAvailableCapacity(station) * 1.5,
  storageConstraint: station.resourceType === ResourceType.Storage
    ? {
        soc: 58,
        minimumSoc: 10,
        maximumSoc: 90,
        energyCapacityKWh: 240,
        maximumChargePowerKW: 96,
        maximumDischargePowerKW: 96,
        chargeEfficiency: 0.95,
        dischargeEfficiency: 0.95,
        cycleCost: '0.080000',
        stateOfHealth: 0.96,
        reservedUpPower: 12,
        reservedDownPower: 10
      }
    : null,
  environmentAssessment: buildMockEnvironmentAssessment(station),
  linkState: station.linkState,
  dataAge: station.dataAge,
  capabilityConfidence: rounded(0.92 + index * 0.01),
  dataTrusted: station.online && station.linkState !== LinkState.S3,
  unavailableReason: station.online ? '' : '场站离线',
  generatedAt: '2026-07-04T15:45:00+08:00',
  validFrom: '2026-07-04T16:00:00+08:00',
  validUntil: '2026-07-04T17:00:00+08:00',
  source: 'REALTIME_TELEMETRY',
  qualityCode: 'GOOD',
  version: 'capability-v1',
  receivedAt: '2026-07-04T15:45:03+08:00'
}))

function correctedMockAvailableCapacity(station: StationState): number {
  const environmentAssessment = buildMockEnvironmentAssessment(station)
  return rounded(station.availableCapacity * (environmentAssessment?.environmentFactor ?? 1))
}

function buildMockEnvironmentAssessment(station: StationState): ResourceCapabilitySnapshot['environmentAssessment'] {
  if (station.resourceType === ResourceType.Photovoltaic) {
    return {
      stationId: station.stationId,
      resourceType: 'PHOTOVOLTAIC',
      environmentFactor: 0.82,
      riskLevel: 'NORMAL',
      correctionReasons: ['辐照度 786.0 W/m2', '云量 18.0%', '组件温度 42.8°C', '积灰损失 4.0%', '遮挡损失 2.0%'],
      affectedDeviceMetrics: ['activePower', 'dcPower', 'moduleTemperature', 'inverterTemperature'],
      deviceDataInterpretation: '当前光伏出力受云量、组件温度和积灰影响，按环境修正后能力参与调度。',
      safetyConstraints: []
    }
  }
  if (station.resourceType === ResourceType.Wind) {
    return {
      stationId: station.stationId,
      resourceType: 'WIND',
      environmentFactor: 0.76,
      riskLevel: 'NORMAL',
      correctionReasons: ['风速 8.6 m/s', '湍流强度 0.1', '空气密度 1.2 kg/m3', '结冰风险 0.0%', '阵风 12.4 m/s', '偏航偏差 3.2°'],
      affectedDeviceMetrics: ['activePower', 'rotorSpeed', 'yawDeviation', 'rampRate'],
      deviceDataInterpretation: '当前风电设备数据与风速、湍流和空气密度基本一致，按环境修正后能力参与调度。',
      safetyConstraints: []
    }
  }
  return null
}

export const mockDispatchContextSnapshot: DispatchContextSnapshot = {
  snapshotId: 'dispatch-context-mock-v1',
  generatedAt: '2026-07-04T15:46:00+08:00',
  validFrom: '2026-07-04T16:00:00+08:00',
  validUntil: '2026-07-04T17:00:00+08:00',
  source: 'TEST_CONTEXT',
  qualityCode: 'GOOD',
  version: 'dispatch-context-v1',
  receivedAt: '2026-07-04T15:46:02+08:00',
  intervalMinutes: 60,
  marketSignal: mockMarketSignal,
  forecastSnapshot: mockForecastSnapshot,
  resourceCapabilities: mockCapabilitySnapshots
}

export const mockMarketDispatchPlan: MarketDispatchPlan = {
  planId: 'market-plan-mock-001',
  inputSnapshotId: mockDispatchContextSnapshot.snapshotId,
  intervalMinutes: 60,
  conservativePlan: false,
  status: 'APPROVED',
  generatedAt: '2026-07-04T15:48:00+08:00',
  validFrom: '2026-07-04T16:00:00+08:00',
  validUntil: '2026-07-04T22:00:00+08:00',
  source: 'RULE_BASED_OPTIMIZER',
  qualityCode: 'GOOD',
  version: 'dispatch-plan-v3',
  receivedAt: '2026-07-04T15:48:03+08:00',
  items: mockForecastSnapshot.points.map((point, index) => ({
    itemId: `market-plan-item-${index + 1}`,
    inputSnapshotId: mockDispatchContextSnapshot.snapshotId,
    intervalStart: point.intervalStart,
    intervalEnd: point.intervalEnd,
    netPowerTarget: rounded(point.photovoltaicP50 + point.windP50 + 42 - point.loadP50 * 0.36),
    gridImportPower: rounded(Math.max(0, point.loadP50 * 0.12 - point.photovoltaicP50 * 0.08)),
    gridExportPower: rounded(Math.max(0, point.photovoltaicP50 * 0.04)),
    storageChargePower: rounded(index < 2 ? 18 + index * 4 : 0),
    storageDischargePower: rounded(index >= 3 ? 22 + (index - 3) * 5 : 0),
    flexibleLoadAdjustment: rounded(index >= 4 ? 12 + (index - 4) * 2 : 4),
    upwardReserve: 18,
    downwardReserve: 12,
    renewableCurtailment: rounded(index === 0 ? 6 : index === 1 ? 3 : 0),
    expectedCost: (38 + index * 4.2).toFixed(6),
    expectedRevenue: (52 + index * 6.4).toFixed(6),
    riskCost: (4.2 + index * 0.6).toFixed(6),
    reasonCode: index < 2 ? 'SURPLUS_CHARGE_STORAGE' : index >= 4 ? 'DEFICIT_SUPPORT' : 'BALANCED_OPERATION',
    itemOrder: index
  }))
}

export const mockMarketDispatchExecution: MarketDispatchExecution = {
  planId: mockMarketDispatchPlan.planId,
  version: mockMarketDispatchPlan.version,
  status: 'ACTIVE',
  activeBatchId: mockDispatchCommands[0]?.batchId ?? 'mock-batch-1',
  commandCount: mockDispatchCommands.length,
  terminalCommandCount: mockDispatchCommands.filter((command) => ['EXECUTED', 'REJECTED', 'TIMEOUT', 'FAILED'].includes(command.status)).length,
  latestCommandTime: mockDispatchCommands[0]?.sendTime ?? null,
  commands: mockDispatchCommands
}

export const mockMarketDispatchTracking: MarketDispatchTracking = {
  planId: mockMarketDispatchPlan.planId,
  version: mockMarketDispatchPlan.version,
  items: mockMarketDispatchPlan.items.map((item, index) => ({
    dispatchPlanItemId: item.itemId,
    intervalStart: item.intervalStart,
    intervalEnd: item.intervalEnd,
    plannedNetPower: item.netPowerTarget,
    actualNetPower: rounded(item.netPowerTarget + (index % 2 === 0 ? -6.5 : 4.2)),
    trackingError: rounded(index % 2 === 0 ? -6.5 : 4.2),
    latestCommandStatus: index < 4 ? 'EXECUTED' : 'ACCEPTED'
  }))
}

export const mockMarketBid: MarketBid = {
  bidId: 'market-bid-mock-001',
  marketType: mockMarketSignal.marketType,
  aggregateObject: 'virtual-power-plant',
  intervalStart: mockMarketSignal.intervalStart,
  intervalEnd: mockMarketSignal.intervalEnd,
  availableCapacity: 280,
  totalQuantity: 240,
  inputSnapshotId: mockDispatchContextSnapshot.snapshotId,
  bidVersion: 'bid-v1',
  status: 'CLEARED',
  validUntil: '2026-07-04T17:30:00+08:00',
  testData: true,
  createdAt: '2026-07-04T15:50:00+08:00',
  segments: [
    { segmentOrder: 1, quantity: 90, price: '0.472000' },
    { segmentOrder: 2, quantity: 80, price: '0.486000' },
    { segmentOrder: 3, quantity: 70, price: '0.508000' }
  ]
}

export const mockMarketClearing: MarketClearing = {
  clearingId: 'market-clearing-mock-001',
  bidId: mockMarketBid.bidId,
  marketType: mockMarketBid.marketType,
  intervalStart: mockMarketBid.intervalStart,
  intervalEnd: mockMarketBid.intervalEnd,
  clearedPower: 240,
  clearedPrice: '0.487333',
  ancillaryServicePower: 16,
  externalTradeId: 'trade-mock-001',
  testData: true,
  createdAt: '2026-07-04T15:53:00+08:00'
}

export const mockSettlementResult: SettlementResult = {
  settlementId: 'settlement-mock-001',
  planId: mockMarketDispatchPlan.planId,
  clearingId: mockMarketClearing.clearingId,
  totalEnergyRevenue: '116.960000',
  gridImportCost: '5.964000',
  gridExportRevenue: '1.909333',
  deviationCost: '1.462000',
  storageCycleCost: '0.920000',
  demandResponseCompensation: '0.720000',
  renewableCurtailmentCost: '0.270000',
  ancillaryServiceRevenue: '1.559466',
  netRevenue: '111.092799',
  resourceRevenueAllocation: {
    'pv-gobi-01': '38.124000',
    'wind-ridge-02': '34.862000',
    'storage-hub-03': '15.408000',
    'grid-link-00': '8.216000',
    'load-oasis-04': '9.482799',
    'charge-corridor-05': '5.000000'
  },
  testData: true,
  createdAt: '2026-07-04T15:56:00+08:00',
  meteringRecords: [
    { meteringId: 'metering-001', stationId: 'pv-gobi-01', intervalStart: mockMarketClearing.intervalStart, intervalEnd: mockMarketClearing.intervalEnd, averagePower: 126, energyKwh: '126.000000', sampleCount: 6 },
    { meteringId: 'metering-002', stationId: 'wind-ridge-02', intervalStart: mockMarketClearing.intervalStart, intervalEnd: mockMarketClearing.intervalEnd, averagePower: 178, energyKwh: '178.000000', sampleCount: 6 },
    { meteringId: 'metering-003', stationId: 'storage-hub-03', intervalStart: mockMarketClearing.intervalStart, intervalEnd: mockMarketClearing.intervalEnd, averagePower: 58, energyKwh: '58.000000', sampleCount: 6 }
  ]
}

export const mockMarketAgentRun: MarketAgentRun = {
  runId: 'market-agent-run-mock-001',
  snapshotId: mockDispatchContextSnapshot.snapshotId,
  dispatchPlanId: mockMarketDispatchPlan.planId,
  conservativePlan: false,
  createdAt: '2026-07-04T15:54:00+08:00',
  decisions: [
    {
      decisionId: 'decision-market-interface',
      agentType: 'MARKET_INTERFACE',
      snapshotId: mockDispatchContextSnapshot.snapshotId,
      generatedAt: '2026-07-04T15:54:01+08:00',
      validUntil: '2026-07-04T16:10:00+08:00',
      confidence: 0.94,
      evidenceRefs: ['market-price-stable', 'contract-window-valid'],
      ruleVersion: 'market-rule-v1',
      modelProvider: 'deepseek',
      modelVersion: 'deepseek-v4-flash',
      candidateActions: ['VERIFY_MARKET_SIGNAL_VERSION'],
      riskLevel: 'LOW',
      requiresHumanApproval: false,
      explanation: '市场价格和合同窗口稳定，适合继续执行当前计划版本。',
      fallbackUsed: false
    },
    {
      decisionId: 'decision-forecast',
      agentType: 'FORECAST',
      snapshotId: mockDispatchContextSnapshot.snapshotId,
      generatedAt: '2026-07-04T15:54:02+08:00',
      validUntil: '2026-07-04T16:10:00+08:00',
      confidence: 0.92,
      evidenceRefs: ['forecast-p50-stable', 'renewable-middle-band'],
      ruleVersion: 'forecast-rule-v1',
      modelProvider: 'deepseek',
      modelVersion: 'deepseek-v4-flash',
      candidateActions: ['USE_P50_FORECAST'],
      riskLevel: 'LOW',
      requiresHumanApproval: false,
      explanation: '预测中位线稳定，可进入环境感知修正。',
      fallbackUsed: false
    },
    {
      decisionId: 'decision-environment-assessment',
      agentType: 'ENVIRONMENT_ASSESSMENT',
      snapshotId: mockDispatchContextSnapshot.snapshotId,
      generatedAt: '2026-07-04T15:54:03+08:00',
      validUntil: '2026-07-04T16:10:00+08:00',
      confidence: 0.91,
      evidenceRefs: ['pv-environment-factor-0.82', 'wind-environment-factor-0.76'],
      ruleVersion: 'environment-rule-v1',
      modelProvider: 'deepseek',
      modelVersion: 'deepseek-v4-flash',
      candidateActions: ['APPLY_ENVIRONMENT_CORRECTION'],
      riskLevel: 'MEDIUM',
      requiresHumanApproval: false,
      explanation: '光伏受云量、组件温度和积灰影响，风电受空气密度和湍流影响，已作为能力修正因子进入资源能力边界。',
      fallbackUsed: false
    },
    {
      decisionId: 'decision-resource-capability',
      agentType: 'RESOURCE_CAPABILITY',
      snapshotId: mockDispatchContextSnapshot.snapshotId,
      generatedAt: '2026-07-04T15:54:04+08:00',
      validUntil: '2026-07-04T16:10:00+08:00',
      confidence: 0.93,
      evidenceRefs: ['environment-corrected-capacity', 'storage-soc-safe'],
      ruleVersion: 'capability-rule-v1',
      modelProvider: 'deepseek',
      modelVersion: 'deepseek-v4-flash',
      candidateActions: ['FREEZE_RESOURCE_CAPABILITY_BOUNDARY'],
      riskLevel: 'LOW',
      requiresHumanApproval: false,
      explanation: '资源能力边界已采用环境修正后可用能力，避免按装机容量乐观调度。',
      fallbackUsed: false
    },
    {
      decisionId: 'decision-market-dispatch',
      agentType: 'MARKET_DISPATCH',
      snapshotId: mockDispatchContextSnapshot.snapshotId,
      generatedAt: '2026-07-04T15:54:05+08:00',
      validUntil: '2026-07-04T16:10:00+08:00',
      confidence: 0.9,
      evidenceRefs: ['storage-soc-safe', 'pv-wind-forecast-middle-band'],
      ruleVersion: 'dispatch-rule-v1',
      modelProvider: 'deepseek',
      modelVersion: 'deepseek-v4-flash',
      candidateActions: ['GENERATE_MARKET_OPTIMAL_PLAN'],
      riskLevel: 'LOW',
      requiresHumanApproval: false,
      explanation: '建议在供大于用时优先充储能，剩余功率参与测试外送。',
      fallbackUsed: false
    },
    {
      decisionId: 'decision-communication-quality',
      agentType: 'COMMUNICATION_QUALITY',
      snapshotId: mockDispatchContextSnapshot.snapshotId,
      generatedAt: '2026-07-04T15:54:06+08:00',
      validUntil: '2026-07-04T16:10:00+08:00',
      confidence: 0.89,
      evidenceRefs: ['link-s0-s1-observed', 'telemetry-fresh'],
      ruleVersion: 'communication-rule-v1',
      modelProvider: 'deepseek',
      modelVersion: 'deepseek-v4-flash',
      candidateActions: ['APPLY_S0_S4_CAPACITY_FACTOR'],
      riskLevel: 'MEDIUM',
      requiresHumanApproval: false,
      explanation: '通信质量满足闭环执行，弱信号时仅允许保守容量折减。',
      fallbackUsed: false
    },
    {
      decisionId: 'decision-control-decision',
      agentType: 'CONTROL_DECISION',
      snapshotId: mockDispatchContextSnapshot.snapshotId,
      generatedAt: '2026-07-04T15:54:07+08:00',
      validUntil: '2026-07-04T16:10:00+08:00',
      confidence: 0.95,
      evidenceRefs: ['plan-boundary-valid', 'command-scope-safe'],
      ruleVersion: 'control-rule-v1',
      modelProvider: 'deepseek',
      modelVersion: 'deepseek-v4-flash',
      candidateActions: ['ISSUE_SAFE_DISPATCH_DECISION'],
      riskLevel: 'LOW',
      requiresHumanApproval: false,
      explanation: '控制动作限定在资源能力和环境修正后的边界内。',
      fallbackUsed: false
    },
    {
      decisionId: 'decision-security-audit',
      agentType: 'SECURITY_AUDIT',
      snapshotId: mockDispatchContextSnapshot.snapshotId,
      generatedAt: '2026-07-04T15:54:08+08:00',
      validUntil: '2026-07-04T16:10:00+08:00',
      confidence: 0.97,
      evidenceRefs: ['command-version-consistent', 'no-replay-detected'],
      ruleVersion: 'security-rule-v1',
      modelProvider: 'deepseek',
      modelVersion: 'deepseek-v4-flash',
      candidateActions: ['ALLOW_SAFE_ACTIONS'],
      riskLevel: 'LOW',
      requiresHumanApproval: false,
      explanation: '未发现计划版本冲突、重放或伪造风险，可进入执行链路。',
      fallbackUsed: false
    }
  ],
  safetyDecision: {
    decisionLevel: 'PASSED',
    passed: true,
    fallbackUsed: false,
    approvedActions: [
      'VERIFY_MARKET_SIGNAL_VERSION',
      'USE_P50_FORECAST',
      'APPLY_ENVIRONMENT_CORRECTION',
      'FREEZE_RESOURCE_CAPABILITY_BOUNDARY',
      'GENERATE_MARKET_OPTIMAL_PLAN',
      'APPLY_S0_S4_CAPACITY_FACTOR',
      'ISSUE_SAFE_DISPATCH_DECISION',
      'ALLOW_SAFE_ACTIONS'
    ],
    explanation: '环境修正、功率边界、SOC、安全状态和 S0-S4 容量折减校验均已通过，进入可观察执行链路。'
  }
}

function createDispatchMetricValue(
  label: string,
  value: number | string | null,
  unit: string,
  improvementValue: number | string | null,
  improvementRate: number | null,
  trendDirection: 'UP' | 'DOWN' | 'FLAT' | 'UNKNOWN',
  higherIsBetter: boolean | null,
  available = true
) {
  return {
    label,
    value,
    unit,
    improvementValue,
    improvementRate,
    trendDirection,
    higherIsBetter,
    available
  }
}

function createComparisonMetric(
  metricCode: string,
  metricName: string,
  beforeValue: number | string | null,
  afterValue: number | string | null,
  unit: string,
  improvementValue: number | string | null,
  improvementRate: number | null,
  trendDirection: 'UP' | 'DOWN' | 'FLAT' | 'UNKNOWN',
  improved: boolean | null,
  available = true
) {
  return {
    metricCode,
    metricName,
    beforeValue,
    afterValue,
    unit,
    improvementValue,
    improvementRate,
    trendDirection,
    improved,
    available
  }
}

export const mockAgentDispatchKeyScenarios = experimentScenarios.filter((scenario) =>
  ['T1_LATENCY_JITTER', 'T2_RANDOM_PACKET_LOSS', 'T5_FULL_LOSS_AUTONOMY', 'T9_RECOVERY_SYNC'].includes(scenario.scenarioCode)
)

export const mockAgentDispatchOverview: AgentDispatchOverview = {
  systemStatus: {
    aiEnabled: true,
    aiAvailable: true,
    aiProvider: 'deepseek',
    controlMode: ControlMode.EdgeCoordination,
    activeScenario: null,
    dataSourceStatus: 'MOCK',
    lastUpdatedAt: '2026-07-04T15:56:00+08:00'
  },
  dispatchStrategy: {
    currentStrategyTitle: '当前执行策略',
    strategyMode: '自动调度',
    loadSideActions: {
      groupType: 'load',
      groupTitle: '负荷侧操作',
      groupSubtitle: '当前策略',
      items: [
        {
          strategyCode: 'LOAD_PEAK_SHAVING',
          strategyName: '削峰',
          groupType: 'load',
          status: 'EXECUTING',
          targetDeltaPower: -12.6,
          unit: 'kW',
          reason: '晚高峰购电价格上行，优先削减柔性负荷。',
          affectedResourceIds: ['load-oasis-04'],
          adoptedBySafetyGate: true
        },
        {
          strategyCode: 'LOAD_VALLEY_FILLING',
          strategyName: '填谷',
          groupType: 'load',
          status: 'STANDBY',
          targetDeltaPower: 8.4,
          unit: 'kW',
          reason: '低谷补能窗口待命，当前未触发执行。',
          affectedResourceIds: ['charge-corridor-05'],
          adoptedBySafetyGate: false
        },
        {
          strategyCode: 'LOAD_DEMAND_RESPONSE',
          strategyName: '需求响应',
          groupType: 'load',
          status: 'EXECUTING',
          targetDeltaPower: -15,
          unit: 'kW',
          reason: '参与需求响应，释放向上调节能力。',
          affectedResourceIds: ['load-oasis-04', 'charge-corridor-05'],
          adoptedBySafetyGate: true
        }
      ]
    },
    generationSideActions: {
      groupType: 'generation',
      groupTitle: '电源侧操作',
      groupSubtitle: '功率修正',
      items: [
        {
          strategyCode: 'GEN_OUTPUT_REGULATION',
          strategyName: '出力调节',
          groupType: 'generation',
          status: 'EXECUTING',
          targetDeltaPower: 18.2,
          unit: 'kW',
          reason: '风电跟踪误差扩大，提升风电侧跟踪补偿。',
          affectedResourceIds: ['wind-ridge-02'],
          adoptedBySafetyGate: true
        },
        {
          strategyCode: 'GEN_RENEWABLE_CURTAILMENT',
          strategyName: '新能源消纳控制',
          groupType: 'generation',
          status: 'EXECUTING',
          targetDeltaPower: 22.5,
          unit: 'kW',
          reason: '优先吸纳光伏和风电富余电量，减少弃光弃风。',
          affectedResourceIds: ['pv-gobi-01', 'wind-ridge-02', 'storage-hub-03'],
          adoptedBySafetyGate: true
        },
        {
          strategyCode: 'GEN_RESERVE_DISPATCH',
          strategyName: '备用调度',
          groupType: 'generation',
          status: 'STANDBY',
          targetDeltaPower: 10,
          unit: 'kW',
          reason: '向上备用保留，等待故障场景进一步演化。',
          affectedResourceIds: ['grid-link-00', 'storage-hub-03'],
          adoptedBySafetyGate: false
        }
      ]
    },
    storageActions: {
      groupType: 'storage',
      groupTitle: '储能操作',
      groupSubtitle: 'SOC 与出力',
      items: [
        {
          strategyCode: 'STORAGE_CHARGE',
          strategyName: '充电',
          groupType: 'storage',
          status: 'STANDBY',
          targetDeltaPower: 20,
          unit: 'kW',
          reason: '低谷补能策略已批准，当前等待窗口。',
          affectedResourceIds: ['storage-hub-03'],
          adoptedBySafetyGate: false
        },
        {
          strategyCode: 'STORAGE_DISCHARGE',
          strategyName: '放电',
          groupType: 'storage',
          status: 'EXECUTING',
          targetDeltaPower: -38.9,
          unit: 'kW',
          reason: '故障实验期间储能承担负荷削减与边缘补偿。',
          affectedResourceIds: ['storage-hub-03', 'load-oasis-04'],
          adoptedBySafetyGate: true
        },
        {
          strategyCode: 'STORAGE_SOC_MANAGEMENT',
          strategyName: 'SOC管理',
          groupType: 'storage',
          status: 'EXECUTING',
          targetDeltaPower: null,
          unit: '%',
          reason: '维持 50%-80% 安全区间，限制深度放电。',
          affectedResourceIds: ['storage-hub-03'],
          adoptedBySafetyGate: true
        }
      ]
    },
    gridActions: {
      groupType: 'grid',
      groupTitle: '电网交互',
      groupSubtitle: '并网交换',
      items: [
        {
          strategyCode: 'GRID_IMPORT',
          strategyName: '市电购电',
          groupType: 'grid',
          status: 'STANDBY',
          targetDeltaPower: 30,
          unit: 'kW',
          reason: '保留兜底购电能力，当前未触发。',
          affectedResourceIds: ['grid-link-00'],
          adoptedBySafetyGate: false
        },
        {
          strategyCode: 'GRID_EXPORT',
          strategyName: '市电外送',
          groupType: 'grid',
          status: 'EXECUTING',
          targetDeltaPower: -20,
          unit: 'kW',
          reason: '新能源富余功率在安全门通过后参与外送。',
          affectedResourceIds: ['grid-link-00', 'pv-gobi-01', 'wind-ridge-02'],
          adoptedBySafetyGate: true
        }
      ]
    }
  },
  resourceTopology: {
    centerNode: {
      nodeId: 'agent-dispatch-engine',
      displayName: '多智能体协同调度引擎',
      controlMode: ControlMode.EdgeCoordination,
      systemStatus: 'RUNNING',
      statusText: '策略执行中'
    },
    resourceNodes: [
      {
        nodeId: 'pv-gobi-01',
        stationId: 'pv-gobi-01',
        nodeType: 'photovoltaic',
        displayName: '光伏场站',
        activePower: 126,
        unit: 'kW',
        linkState: LinkState.S0,
        online: true,
        controlMode: ControlMode.CentralOptimization,
        selected: false,
        statusText: '链路正常'
      },
      {
        nodeId: 'wind-ridge-02',
        stationId: 'wind-ridge-02',
        nodeType: 'wind',
        displayName: '风电场站',
        activePower: 178,
        unit: 'kW',
        linkState: LinkState.S0,
        online: true,
        controlMode: ControlMode.CentralOptimization,
        selected: false,
        statusText: '链路正常'
      },
      {
        nodeId: 'thermal-plant-06',
        stationId: 'thermal-plant-06',
        nodeType: 'thermal',
        displayName: '火力发电',
        activePower: 96,
        unit: 'kW',
        linkState: LinkState.S0,
        online: true,
        controlMode: ControlMode.EdgeCoordination,
        selected: false,
        statusText: '调峰备用'
      },
      {
        nodeId: 'grid-link-00',
        stationId: 'grid-link-00',
        nodeType: 'grid',
        displayName: '市电',
        activePower: 10,
        unit: 'kW',
        linkState: LinkState.S0,
        online: true,
        controlMode: ControlMode.EdgeCoordination,
        selected: false,
        statusText: '链路正常'
      },
      {
        nodeId: 'storage-hub-03',
        stationId: 'storage-hub-03',
        nodeType: 'storage',
        displayName: '储能',
        activePower: 58,
        unit: 'kW',
        linkState: LinkState.S0,
        online: true,
        controlMode: ControlMode.EdgeCoordination,
        selected: false,
        statusText: 'SOC 62%'
      },
      {
        nodeId: 'load-oasis-04',
        stationId: 'load-oasis-04',
        nodeType: 'load',
        displayName: '负荷',
        activePower: 122,
        unit: 'kW',
        linkState: LinkState.S0,
        online: true,
        controlMode: ControlMode.EdgeCoordination,
        selected: false,
        statusText: '可调负荷执行中'
      },
      {
        nodeId: 'charge-corridor-05',
        stationId: 'charge-corridor-05',
        nodeType: 'charging',
        displayName: '充电设施',
        activePower: 24,
        unit: 'kW',
        linkState: LinkState.S0,
        online: true,
        controlMode: ControlMode.EdgeCoordination,
        selected: false,
        statusText: '链路正常'
      }
    ],
    energyLinks: [
      { linkId: 'energy-pv', sourceNodeId: 'pv-gobi-01', targetNodeId: 'agent-dispatch-engine', power: 126, unit: 'kW', direction: 'SUPPLY', status: 'NORMAL', active: true },
      { linkId: 'energy-wind', sourceNodeId: 'wind-ridge-02', targetNodeId: 'agent-dispatch-engine', power: 178, unit: 'kW', direction: 'SUPPLY', status: 'NORMAL', active: true },
      { linkId: 'energy-thermal', sourceNodeId: 'thermal-plant-06', targetNodeId: 'agent-dispatch-engine', power: 96, unit: 'kW', direction: 'SUPPLY', status: 'NORMAL', active: true },
      { linkId: 'energy-grid', sourceNodeId: 'grid-link-00', targetNodeId: 'agent-dispatch-engine', power: 10, unit: 'kW', direction: 'SUPPLY', status: 'NORMAL', active: true },
      { linkId: 'energy-storage', sourceNodeId: 'storage-hub-03', targetNodeId: 'agent-dispatch-engine', power: 38.9, unit: 'kW', direction: 'SUPPLY', status: 'NORMAL', active: true },
      { linkId: 'energy-load', sourceNodeId: 'agent-dispatch-engine', targetNodeId: 'load-oasis-04', power: 122, unit: 'kW', direction: 'CONSUME', status: 'NORMAL', active: true },
      { linkId: 'energy-charging', sourceNodeId: 'agent-dispatch-engine', targetNodeId: 'charge-corridor-05', power: 24, unit: 'kW', direction: 'CONSUME', status: 'NORMAL', active: true }
    ],
    controlLinks: [
      { linkId: 'control-pv', sourceNodeId: 'agent-dispatch-engine', targetNodeId: 'pv-gobi-01', commandStatus: 'SENT', linkState: LinkState.S0, active: true },
      { linkId: 'control-wind', sourceNodeId: 'agent-dispatch-engine', targetNodeId: 'wind-ridge-02', commandStatus: 'EXECUTING', linkState: LinkState.S0, active: true },
      { linkId: 'control-thermal', sourceNodeId: 'agent-dispatch-engine', targetNodeId: 'thermal-plant-06', commandStatus: 'EXECUTING', linkState: LinkState.S0, active: true },
      { linkId: 'control-grid', sourceNodeId: 'agent-dispatch-engine', targetNodeId: 'grid-link-00', commandStatus: 'PENDING', linkState: LinkState.S0, active: true },
      { linkId: 'control-storage', sourceNodeId: 'agent-dispatch-engine', targetNodeId: 'storage-hub-03', commandStatus: 'EXECUTING', linkState: LinkState.S0, active: true },
      { linkId: 'control-load', sourceNodeId: 'agent-dispatch-engine', targetNodeId: 'load-oasis-04', commandStatus: 'EXECUTING', linkState: LinkState.S0, active: true },
      { linkId: 'control-charging', sourceNodeId: 'agent-dispatch-engine', targetNodeId: 'charge-corridor-05', commandStatus: 'SENT', linkState: LinkState.S0, active: true }
    ],
    balanceSummary: {
      totalSupplyPower: 400,
      totalConsumePower: 146,
      netBalancePower: 254,
      unit: 'kW'
    },
    legend: [
      { label: '能源流向（供能）', type: 'energy', color: '#67f06b' },
      { label: '能源流向（用能）', type: 'energy', color: '#39b3ff' },
      { label: '控制指令流', type: 'control', color: '#ffb349' }
    ]
  },
  dispatchSummary: {
    plannedTargetPower: createDispatchMetricValue('计划目标', 304, 'kW', null, null, 'UNKNOWN', null),
    actualPower: createDispatchMetricValue('实际功率', 298.6, 'kW', null, null, 'UNKNOWN', null),
    trackingErrorRate: createDispatchMetricValue('跟踪误差', -1.8, '%', 1.8, 38.6, 'DOWN', false),
    revenueImprovement: createDispatchMetricValue('收益改善', 8.64, '万元', 8.64, 12.2, 'UP', true),
    curtailmentReduction: createDispatchMetricValue('弃电降低', -26.7, 'kWh', 26.7, 71.1, 'DOWN', false),
    reserveSatisfactionRate: createDispatchMetricValue('备用满足率', 98.7, '%', 11.4, 13.1, 'UP', true),
    executionSuccessRate: createDispatchMetricValue('执行成功率', 96.4, '%', 18.1, 23.1, 'UP', true),
    activePlanId: mockMarketDispatchPlan.planId,
    planVersion: mockMarketDispatchPlan.version
  },
  experimentStatus: {
    currentRunId: null,
    currentScenarioCode: null,
    currentScenarioName: null,
    running: false,
    durationSeconds: null,
    elapsedSeconds: null,
    affectedStationIds: [],
    startedAt: null,
    expectedLinkState: null
  },
  comparisonSummary: {
    runId: 'mock-run-001',
    scenarioCode: 'T2_RANDOM_PACKET_LOSS',
    scenarioName: '随机丢包',
    metrics: [
      createComparisonMetric('peak-load', '峰值负荷', 182.6, 146.2, 'kW', 36.4, 19.9, 'DOWN', true),
      createComparisonMetric('grid-purchase-cost', '购电成本', 2685, 1984, '元', 701, 26.1, 'DOWN', true),
      createComparisonMetric('curtailment', '弃光弃风', 38.7, 11.2, 'kWh', 27.5, 71.1, 'DOWN', true),
      createComparisonMetric('soc-swing', 'SOC波动', 28.4, 12.7, '%', 15.7, 55.3, 'DOWN', true),
      createComparisonMetric('recovery-time', '链路恢复时间', 86, 32, 's', 54, 62.8, 'DOWN', true),
      createComparisonMetric('execution-success-rate', '执行成功率', 78.3, 96.4, '%', 18.1, 23.1, 'UP', true)
    ]
  },
  agentDecisionSummary: {
    rows: [
      { agentType: 'MARKET_INTERFACE', agentName: '市场接口智能体', available: true, confidence: 0.92, suggestedAction: '外送削峰 +20 kW', finalDecision: '采纳', adopted: true, evidenceCount: 2, fallbackReason: null },
      { agentType: 'FORECAST', agentName: '预测智能体', available: true, confidence: 0.96, suggestedAction: '风电出力上调 +18 kW', finalDecision: '采纳', adopted: true, evidenceCount: 2, fallbackReason: null },
      { agentType: 'ENVIRONMENT_ASSESSMENT', agentName: '环境感知智能体', available: true, confidence: 0.91, suggestedAction: '光伏环境因子 0.82，风电环境因子 0.76，修正设备数据解释', finalDecision: '采纳', adopted: true, evidenceCount: 4, fallbackReason: null },
      { agentType: 'RESOURCE_CAPABILITY', agentName: '资源能力智能体', available: true, confidence: 0.93, suggestedAction: '按环境修正后能力冻结资源边界，避免乐观调度', finalDecision: '采纳', adopted: true, evidenceCount: 3, fallbackReason: null },
      { agentType: 'MARKET_DISPATCH', agentName: '市场调度智能体', available: true, confidence: 0.94, suggestedAction: '实时电价高于日前电价，储能放电 +38.9 kW，市电购电下调 -26.4 kW', finalDecision: '采纳', adopted: true, evidenceCount: 4, fallbackReason: null },
      { agentType: 'COMMUNICATION_QUALITY', agentName: '通信质量智能体', available: true, confidence: 0.89, suggestedAction: '链路退化，降级边通边切换', finalDecision: '采纳', adopted: true, evidenceCount: 3, fallbackReason: null },
      { agentType: 'CONTROL_DECISION', agentName: '控制决策智能体', available: true, confidence: 0.95, suggestedAction: '执行：储能放电 +38.9 kW，负荷削减 -12.6 kW', finalDecision: '采纳', adopted: true, evidenceCount: 4, fallbackReason: null },
      { agentType: 'SECURITY_AUDIT', agentName: '安全审计智能体', available: true, confidence: 0.97, suggestedAction: '安全约束满足，允许下发', finalDecision: '采纳', adopted: true, evidenceCount: 3, fallbackReason: null }
    ],
    overallDecision: '综合裁决：执行当前调度方案',
    safetyDecisionLevel: 'PASSED',
    safetyGatePassed: true,
    ruleFallbackActive: false,
    decisionRunId: mockMarketAgentRun.runId,
    modeLabel: 'DeepSeek 模式',
    fallbackReason: null
  },
  executionTimeline: {
    runId: 'mock-run-001',
    currentStepCode: 'RESOURCE_EXECUTION',
    steps: [
      { stepCode: 'FAULT_OCCURRED', stepLabel: '故障发生', status: 'SUCCEEDED', timestamp: '2026-07-04T15:34:45+08:00', description: 'T2 随机丢包 10% 已注入。', relatedEntityId: 'mock-run-001' },
      { stepCode: 'AGENT_DIAGNOSIS', stepLabel: '智能体诊断', status: 'SUCCEEDED', timestamp: '2026-07-04T15:34:52+08:00', description: '通信质量异常已识别，进入结构化协商。', relatedEntityId: mockMarketAgentRun.runId },
      { stepCode: 'SAFETY_VALIDATION', stepLabel: '安全门校验', status: 'SUCCEEDED', timestamp: '2026-07-04T15:35:01+08:00', description: '功率、SOC、链路状态与权限校验通过。', relatedEntityId: mockMarketDispatchPlan.planId },
      { stepCode: 'COMMAND_DISPATCHED', stepLabel: '调度下发', status: 'SUCCEEDED', timestamp: '2026-07-04T15:35:06+08:00', description: '已向储能和负荷侧下发控制指令。', relatedEntityId: mockMarketDispatchExecution.activeBatchId },
      { stepCode: 'RESOURCE_EXECUTION', stepLabel: '资源执行', status: 'RUNNING', timestamp: '2026-07-04T15:35:10+08:00', description: '储能放电 38.9 kW，负荷削减 12.6 kW。', relatedEntityId: mockMarketDispatchPlan.items[0]?.itemId ?? null },
      { stepCode: 'RECOVERY_SYNC', stepLabel: '恢复同步', status: 'PENDING', timestamp: null, description: '等待链路恢复并回传同步状态。', relatedEntityId: null },
      { stepCode: 'RESULT_COMPARISON', stepLabel: '结果对比', status: 'PENDING', timestamp: null, description: '等待实验停止后生成对比结果。', relatedEntityId: null }
    ]
  }
}
