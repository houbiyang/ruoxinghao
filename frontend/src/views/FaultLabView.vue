<script setup lang="ts">
import type { Component } from 'vue'
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import type { EChartsOption } from 'echarts'
import {
  ArrowDown,
  ArrowUp,
  CircleCheckFilled,
  Cloudy,
  Connection,
  Cpu,
  Delete,
  EditPen,
  Lightning,
  MostlyCloudy,
  RefreshRight,
  Sunny,
  Timer,
  VideoPlay,
  WarningFilled,
  WindPower
} from '@element-plus/icons-vue'

import { resourceTypeLabelMap } from '@/mock/systemData'
import {
  fetchCurrentExperimentRun,
  fetchExperimentReplay,
  fetchExperimentScenarios,
  fetchExperimentTimeline,
  fetchLatestExperimentRuns,
  fetchMarketDispatchExecution,
  fetchRealtimePowerTelemetry,
  fetchStations,
  startExperiment,
  stopExperiment
} from '@/api/systemApi'
import EChartPanel from '@/components/charts/EChartPanel.vue'
import type { ExperimentReplayPoint, ExperimentRun, ExperimentScenario, ExperimentTimelinePoint, MarketDispatchExecution, StationState } from '@/types/system'
import { LinkState, ResourceType } from '@/types/system'

type FaultCategory = 'environment' | 'communication' | 'device'
type QueueStatus = '待执行' | '启动中' | '注入中' | '故障已观测' | '智能体调度中' | '恢复中' | '已完成' | '已停止' | '失败'
type ExperimentNamingAction = 'queue' | 'inject' | 'edit'

interface FaultParameter {
  key: string
  label: string
  unit: string
  minimum: number
  maximum: number
  step: number
  defaultValue: number
}

interface FaultDefinition {
  code: string
  name: string
  summary: string
  category: FaultCategory
  expectedLinkState?: LinkState
  icon: Component
  tone: 'cyan' | 'amber' | 'violet' | 'red'
  targetTypes: ResourceType[]
  defaultTargets: string[]
  impactLabel: string
  impactRate: number
  parameters: FaultParameter[]
}

interface FaultQueueItem {
  queueId: string
  faultCode: string
  faultName: string
  targetStationIds: string[]
  targetNames: string[]
  durationMinutes: number
  faultParameters: Record<string, number>
  severityLevel: number
  automaticRecovery: boolean
  remark: string
  experimentRunId?: string
  status: QueueStatus
  failureReason?: string
  experimentName?: string
}

interface LinkStateStage {
  code: LinkState
  label: string
  trigger: string
  operation: string
}

interface StationExperimentStatus {
  observedFaultCode: string | null
  evidenceStatus: string | null
  recoveryStatus: string | null
  environmentMetrics: Record<string, number> | null
}

type ExperimentRunSummary = Record<string, unknown>

interface PowerChartProfile {
  stationId: string
  label: string
  color: string
  resourceType: ResourceType
  values: Array<number | null>
}

interface ExperimentPhaseInterval {
  code: string
  label: string
  startAt: string | null
  endAt: string | null
  tone: 'red' | 'amber' | 'blue' | 'green' | 'slate'
}

interface PowerChartMarker {
  name: string
  xAxis: string
  lineStyle: { color: string }
  label: { color: string }
}

const powerChartResourceColors: Record<ResourceType, string> = {
  [ResourceType.Photovoltaic]: '#36e18a',
  [ResourceType.Thermal]: '#ff9f43',
  [ResourceType.Storage]: '#d03cff',
  [ResourceType.Load]: '#d9e3ef',
  [ResourceType.Wind]: '#2d8cff',
  [ResourceType.Charging]: '#ff3d55',
  [ResourceType.Grid]: '#39dce8'
}

const powerChartResourceOrder: ResourceType[] = [
  ResourceType.Photovoltaic,
  ResourceType.Thermal,
  ResourceType.Storage,
  ResourceType.Load,
  ResourceType.Wind,
  ResourceType.Charging,
  ResourceType.Grid
]
const powerChartSampleIntervalSeconds = 10
const powerChartHistoryWindowMinutes = 6
const powerChartFutureWindowSeconds = 120
const powerChartCarryForwardSeconds = 20

const categoryLabels: Record<FaultCategory, string> = {
  environment: '环境故障',
  communication: '通信故障',
  device: '设备故障'
}

const linkStateStages: LinkStateStage[] = [
  { code: LinkState.S0, label: '正常协同', trigger: '时延 < 80 ms，丢包 < 1%，数据时效 < 3 s', operation: '正常参与调度' },
  { code: LinkState.S1, label: '通信退化', trigger: '任一指标达到退化阈值', operation: '能力按 0.85 折算' },
  { code: LinkState.S2, label: '严重退化', trigger: '时延 ≥ 500 ms、丢包 ≥ 20% 或数据时效 ≥ 15 s', operation: '能力按 0.60 折算，控制更保守' },
  { code: LinkState.S3, label: '失联自治', trigger: '设备离线或心跳超过 15 s', operation: '能力归零，停止远程控制，进入本地自治' },
  { code: LinkState.S4, label: '恢复同步', trigger: 'S3 后收到首条有效遥测', operation: '保持观测 6 s，同步状态后重新评估' }
]

const executionStages = [
  { code: 'INJECTED', label: '注入命令' },
  { code: 'OBSERVED', label: '故障观测' },
  { code: 'PLANNED', label: '计划生成' },
  { code: 'TARGET_REACHED', label: '执行收敛' },
  { code: 'RECOVERED', label: '恢复验收' }
] as const

const fallbackFaultCatalog: FaultDefinition[] = [
  {
    code: 'ENV_WIND_CALM',
    name: '无风 / 低风速',
    summary: '模拟风速跌破切入阈值，验证风电出力骤降后的功率平衡能力。',
    category: 'environment',
    icon: WindPower,
    tone: 'cyan',
    targetTypes: [ResourceType.Wind],
    defaultTargets: ['wind-ridge-02'],
    impactLabel: '风电出力预计下降',
    impactRate: 82,
    parameters: [
      { key: 'windSpeed', label: '目标风速', unit: 'm/s', minimum: 0, maximum: 5, step: 0.1, defaultValue: 0.8 },
      { key: 'turbulence', label: '湍流强度', unit: '%', minimum: 0, maximum: 40, step: 1, defaultValue: 6 }
    ]
  },
  {
    code: 'ENV_OVERCAST',
    name: '阴天 / 低辐照',
    summary: '模拟云层遮挡导致辐照度持续下降，观察光伏出力与预测偏差。',
    category: 'environment',
    icon: MostlyCloudy,
    tone: 'violet',
    targetTypes: [ResourceType.Photovoltaic],
    defaultTargets: ['pv-gobi-01'],
    impactLabel: '光伏出力预计下降',
    impactRate: 64,
    parameters: [
      { key: 'irradiance', label: '太阳辐照度', unit: 'W/m²', minimum: 0, maximum: 500, step: 10, defaultValue: 160 },
      { key: 'cloudCover', label: '云量覆盖', unit: '%', minimum: 0, maximum: 100, step: 1, defaultValue: 85 }
    ]
  },
  {
    code: 'ENV_HEAT',
    name: '持续高温',
    summary: '模拟组件与储能舱温度升高，验证限功率保护和调度降额。',
    category: 'environment',
    icon: Sunny,
    tone: 'amber',
    targetTypes: [ResourceType.Photovoltaic, ResourceType.Storage],
    defaultTargets: ['pv-gobi-01', 'storage-hub-03'],
    impactLabel: '可用容量预计下降',
    impactRate: 28,
    parameters: [
      { key: 'temperature', label: '环境温度', unit: '℃', minimum: 30, maximum: 60, step: 1, defaultValue: 46 },
      { key: 'duration', label: '升温时间', unit: 'min', minimum: 5, maximum: 120, step: 5, defaultValue: 30 }
    ]
  },
  {
    code: 'ENV_DUST',
    name: '沙尘遮蔽',
    summary: '模拟光伏组件积尘与沙尘天气，观察发电效率缓慢衰减。',
    category: 'environment',
    icon: Cloudy,
    tone: 'amber',
    targetTypes: [ResourceType.Photovoltaic],
    defaultTargets: ['pv-gobi-01'],
    impactLabel: '光伏转换效率下降',
    impactRate: 36,
    parameters: [
      { key: 'dustDensity', label: '积尘密度', unit: 'g/m²', minimum: 0, maximum: 30, step: 1, defaultValue: 18 },
      { key: 'visibility', label: '能见度', unit: 'km', minimum: 0.5, maximum: 10, step: 0.5, defaultValue: 2 }
    ]
  },
  {
    code: 'COM_PACKET_LOSS',
    name: '突发丢包',
    summary: '模拟场站上行遥测连续丢失，验证弱信号下的数据可信治理。',
    category: 'communication',
    expectedLinkState: LinkState.S2,
    icon: Connection,
    tone: 'red',
    targetTypes: Object.values(ResourceType),
    defaultTargets: ['pv-gobi-01'],
    impactLabel: '遥测可信度预计下降',
    impactRate: 72,
    parameters: [
      { key: 'packetLoss', label: '丢包率', unit: '%', minimum: 0, maximum: 100, step: 1, defaultValue: 35 },
      { key: 'burstLength', label: '连续丢包', unit: '次', minimum: 1, maximum: 20, step: 1, defaultValue: 6 }
    ]
  },
  {
    code: 'COM_LATENCY',
    name: '时延与抖动',
    summary: '模拟链路高时延和随机抖动，验证控制指令过期保护。',
    category: 'communication',
    expectedLinkState: LinkState.S1,
    icon: Timer,
    tone: 'violet',
    targetTypes: Object.values(ResourceType),
    defaultTargets: ['wind-ridge-02'],
    impactLabel: '控制响应时间预计增加',
    impactRate: 58,
    parameters: [
      { key: 'latency', label: '基础时延', unit: 'ms', minimum: 50, maximum: 2000, step: 50, defaultValue: 800 },
      { key: 'jitter', label: '链路抖动', unit: 'ms', minimum: 0, maximum: 500, step: 10, defaultValue: 120 }
    ]
  },
  {
    code: 'COM_OFFLINE',
    name: '通信中断',
    summary: '模拟场站完全失联，验证本地自治、容量剔除与安全恢复。',
    category: 'communication',
    expectedLinkState: LinkState.S3,
    icon: Lightning,
    tone: 'red',
    targetTypes: Object.values(ResourceType),
    defaultTargets: ['storage-hub-03'],
    impactLabel: '聚合可控容量预计下降',
    impactRate: 46,
    parameters: [
      { key: 'heartbeatAge', label: '心跳失联', unit: 's', minimum: 5, maximum: 120, step: 5, defaultValue: 30 },
      { key: 'reconnectDelay', label: '重连延迟', unit: 's', minimum: 0, maximum: 300, step: 10, defaultValue: 60 }
    ]
  },
  {
    code: 'DEV_INVERTER',
    name: '逆变器降额',
    summary: '模拟逆变器过温或保护降额，验证场站侧能力重新评估。',
    category: 'device',
    icon: Cpu,
    tone: 'amber',
    targetTypes: [ResourceType.Photovoltaic],
    defaultTargets: ['pv-gobi-01'],
    impactLabel: '光伏可用容量预计下降',
    impactRate: 42,
    parameters: [
      { key: 'derating', label: '降额比例', unit: '%', minimum: 5, maximum: 100, step: 5, defaultValue: 45 },
      { key: 'deviceCount', label: '影响设备', unit: '台', minimum: 1, maximum: 20, step: 1, defaultValue: 4 }
    ]
  },
  {
    code: 'DEV_STORAGE',
    name: '储能簇离线',
    summary: '模拟部分电池簇离线，验证备用容量不足时的安全约束。',
    category: 'device',
    icon: WarningFilled,
    tone: 'red',
    targetTypes: [ResourceType.Storage],
    defaultTargets: ['storage-hub-03'],
    impactLabel: '储能可调容量预计下降',
    impactRate: 50,
    parameters: [
      { key: 'offlineClusters', label: '离线电池簇', unit: '组', minimum: 1, maximum: 8, step: 1, defaultValue: 2 },
      { key: 'remainingSoc', label: '剩余 SOC', unit: '%', minimum: 5, maximum: 100, step: 1, defaultValue: 42 }
    ]
  }
]

const faultCatalog = ref<FaultDefinition[]>([])
const scenarioLoadError = ref('')
const stationLoadError = ref('')
const runtimeStatusError = ref('')
const stations = ref<StationState[]>([])
const stationExperimentStatuses = ref<Record<string, StationExperimentStatus>>({})
const activeCategory = ref<FaultCategory>('environment')
const selectedFaultCode = ref('ENV_WIND_CALM')
const selectedStationIds = ref<string[]>(['wind-ridge-02'])
const parameterValues = ref<Record<string, number>>({ windSpeed: 0.8, turbulence: 6 })
const severityLevel = ref(4)
const fixedFaultInjectionDurationMinutes = 1
const durationMinutes = ref(fixedFaultInjectionDurationMinutes)
const automaticRecovery = ref(true)
const experimentRemark = ref('')
const isRunning = ref(false)
const runProgress = ref(0)
const activeRunId = ref('')
const activeRunStatus = ref<ExperimentRun['status'] | null>(null)
const activeRunSummary = ref<ExperimentRunSummary>({})
const activeExpectedLinkState = ref<LinkState | null>(null)
const chartRunId = ref('')
const chartRunSummary = ref<ExperimentRunSummary>({})
const chartReplayPoints = ref<ExperimentReplayPoint[]>([])
const chartTimelinePoints = ref<ExperimentTimelinePoint[]>([])
const chartExecution = ref<MarketDispatchExecution | null>(null)
const chartLoading = ref(false)
const chartError = ref('')
const isStopping = ref(false)
const activeSummaryPanel = ref<'faults' | 'queue' | 'completed' | null>(null)
const unsupportedStationName = ref('')
const namingQueueItemId = ref<string | null>(null)
const experimentNameDraft = ref('')
const experimentNamingAction = ref<ExperimentNamingAction>('edit')
const queueStorageKey = 'weak-signal-fault-queue'
const queueItems = ref<FaultQueueItem[]>(restoreQueueItems())
const router = useRouter()
let statusTimer: number | undefined
let chartTimer: number | undefined

const visibleCategories: FaultCategory[] = ['environment', 'communication']
const visibleFaultCatalog = computed(() => faultCatalog.value.filter((fault) => visibleCategories.includes(fault.category)))
const visibleFaults = computed(() => visibleFaultCatalog.value.filter((fault) => fault.category === activeCategory.value))
const selectedFault = computed(() => faultCatalog.value.find((fault) => fault.code === selectedFaultCode.value) ?? fallbackFaultCatalog[0])
const selectedStations = computed(() => stations.value.filter((station) => selectedStationIds.value.includes(station.stationId)))
const categoryOptions = computed(() => visibleCategories.map((category) => ({
  value: category,
  label: categoryLabels[category],
  count: faultCatalog.value.filter((fault) => fault.category === category).length
})))
const availableFaultCount = computed(() => visibleFaultCatalog.value.length)
const pendingQueueItems = computed(() => queueItems.value.filter((item) => item.status === '待执行'))
const completedQueueItems = computed(() => queueItems.value.filter((item) => item.status === '已完成'))
const activeQueueItem = computed(() => queueItems.value.find((item) => item.experimentRunId === activeRunId.value))
const expectedLinkState = computed(() => (isRunning.value ? activeExpectedLinkState.value : null)
  ?? selectedFault.value.expectedLinkState
  ?? LinkState.S0)
const actualTargetLinkStates = computed(() => new Set(selectedStations.value.map((station) => station.linkState).filter((state): state is LinkState => state !== undefined)))
const currentExecutionStageCode = computed(() => executionStages.find((stage) => !isExecutionStageCompleted(stage.code))?.code ?? null)
const executionDockStatusText = computed(() => {
  const faultName = activeQueueItem.value?.faultName ?? selectedFault.value.name
  if (activeRunSummary.value.recoveryReportStatus === 'RECOVERED') {
    return activeRunSummary.value.orchestrationStatus === 'FAILED' || activeRunStatus.value === 'FAILED'
      ? `${faultName}已恢复（智能体调度失败）`
      : `${faultName}已恢复`
  }
  if (!isRunning.value) return '等待故障注入任务'
  if (activeRunStatus.value === 'STARTING') return `${faultName}准备注入`
  if (activeRunStatus.value === 'INJECTING') return `${faultName}持续注入中`
  if (activeRunStatus.value === 'FAULT_OBSERVED') return `${faultName}故障已观测`
  if (activeRunStatus.value === 'ORCHESTRATING') return `${faultName}智能体调度中`
  if (activeRunStatus.value === 'RECOVERING') return `${faultName}恢复确认中`
  return '等待故障注入任务'
})
const experimentNameIsDuplicate = computed(() => {
  const normalizedName = experimentNameDraft.value.trim().toLocaleLowerCase()
  return normalizedName !== '' && queueItems.value.some((item) => item.queueId !== namingQueueItemId.value && item.experimentName?.trim().toLocaleLowerCase() === normalizedName)
})
const chartTimestamps = computed(() => {
  return [...new Set(chartReplayPoints.value.map((point) => normalizePowerChartTimestamp(point.timestamp)))].sort()
})
const chartTimeLabels = computed(() => chartTimestamps.value.map(formatPowerChartTime))
const chartDisplayTimestamps = computed(() => {
  const latestTimestamp = chartTimestamps.value.at(-1)
  if (latestTimestamp === undefined) {
    return []
  }
  const latestTime = new Date(latestTimestamp).getTime()
  const futurePointCount = Math.floor(powerChartFutureWindowSeconds / powerChartSampleIntervalSeconds)
  const futureTimestamps = Array.from({ length: futurePointCount }, (_, index) => (
    new Date(latestTime + (index + 1) * powerChartSampleIntervalSeconds * 1000).toISOString()
  ))
  return [...chartTimestamps.value, ...futureTimestamps]
})
const chartDisplayTimeLabels = computed(() => chartDisplayTimestamps.value.map(formatPowerChartTime))
const powerChartProfiles = computed<PowerChartProfile[]>(() => {
  const pointByStationAndTimestamp = new Map<string, ExperimentReplayPoint>()
  chartReplayPoints.value.forEach((point) => {
    pointByStationAndTimestamp.set(`${point.stationId}|${normalizePowerChartTimestamp(point.timestamp)}`, point)
  })
  const stationIds = [...new Set(chartReplayPoints.value.map((point) => point.stationId))]
  return stationIds.map((stationId) => {
    const station = stations.value.find((item) => item.stationId === stationId)
    const resourceType = station?.resourceType ?? inferResourceType(stationId)
    const latestChartTimestamp = new Date(chartTimestamps.value.at(-1) ?? '').getTime()
    let latestPower: number | null = null
    let latestPowerTimestamp: number | null = null
    return {
      stationId,
      label: station === undefined ? stationId : `${resourceTypeLabelMap[resourceType]}功率`,
      color: powerChartResourceColors[resourceType],
      resourceType,
      values: chartDisplayTimestamps.value.map((timestamp) => {
        const point = pointByStationAndTimestamp.get(`${stationId}|${timestamp}`)
        if (point !== undefined && Number.isFinite(point.activePower)) {
          latestPower = [ResourceType.Load, ResourceType.Charging].includes(resourceType)
            ? -Math.abs(point.activePower)
            : point.activePower
          latestPowerTimestamp = new Date(timestamp).getTime()
        }
        const currentTimestamp = new Date(timestamp).getTime()
        if (currentTimestamp > latestChartTimestamp
          || latestPowerTimestamp === null
          || currentTimestamp - latestPowerTimestamp > powerChartCarryForwardSeconds * 1000) {
          return null
        }
        return latestPower
      })
    }
  }).sort((left, right) => powerChartResourceOrder.indexOf(left.resourceType) - powerChartResourceOrder.indexOf(right.resourceType))
})
const netSupplyPower = computed(() => chartDisplayTimestamps.value.map((_, index) => {
  const values = powerChartProfiles.value
    .map((profile) => {
      const value = profile.values[index]
      if (value === null) {
        return null
      }
      return [ResourceType.Load, ResourceType.Charging].includes(profile.resourceType) ? -Math.abs(value) : value
    })
    .filter((value): value is number => value !== null)
  return values.length === 0 ? null : values.reduce((total, value) => total + value, 0)
}))
const chartMarkers = computed(() => buildPowerChartMarkers())
const chartTimelineEventCount = computed(() => chartTimelinePoints.value.filter((point) => point.eventTitle !== '').length)
const chartExecutionOutcomeText = computed(() => {
  const commands = chartExecution.value?.commands ?? []
  if (commands.length === 0) return '尚无调度指令'
  const executedCount = commands.filter((command) => command.status === 'EXECUTED').length
  const timeoutCount = commands.filter((command) => command.status === 'TIMEOUT').length
  const rejectedCount = commands.filter((command) => command.status === 'REJECTED' || command.status === 'FAILED').length
  const pendingCount = commands.length - executedCount - timeoutCount - rejectedCount
  return `${executedCount} 条执行成功 · ${timeoutCount} 条超时 · ${rejectedCount} 条拒绝${pendingCount > 0 ? ` · ${pendingCount} 条处理中` : ''}`
})
const experimentPhaseIntervals = computed<ExperimentPhaseInterval[]>(() => [
  {
    code: 'FAULT_ACTIVE',
    label: '故障前（基线运行）',
    startAt: chartTimestamps.value[0] ?? null,
    endAt: visibleSummaryTimestamp('injectionStartedAt'),
    tone: 'red'
  },
  {
    code: 'FAULT_OBSERVED',
    label: '故障持续注入',
    startAt: visibleSummaryTimestamp('injectionStartedAt'),
    endAt: visibleSummaryTimestamp('orchestrationScheduledAt') ?? visibleSummaryTimestamp('orchestrationTriggeredAt'),
    tone: 'amber'
  },
  {
    code: 'ORCHESTRATING',
    label: '智能体调度',
    startAt: visibleSummaryTimestamp('orchestrationScheduledAt') ?? visibleSummaryTimestamp('orchestrationTriggeredAt'),
    endAt: visibleSummaryTimestamp('orchestrationStageEndsAt'),
    tone: 'blue'
  },
  {
    code: 'TRACKING',
    label: '控制执行',
    startAt: visiblePowerChartTimestamp(firstCommandTimestamp()),
    endAt: visibleSummaryTimestamp('convergenceUpdatedAt'),
    tone: 'green'
  },
  {
    code: 'RECOVERING',
    label: '故障恢复',
    startAt: visibleSummaryTimestamp('recoveryStartedAt'),
    endAt: visibleSummaryTimestamp('recoveredAt'),
    tone: 'slate'
  }
])
const powerChartOption = computed<EChartsOption>(() => ({
  animationDuration: 300,
  color: powerChartProfiles.value.map((profile) => profile.color),
  tooltip: {
    trigger: 'axis',
    triggerOn: 'mousemove|click',
    confine: true,
    order: 'valueDesc',
    backgroundColor: 'rgba(3, 13, 35, 0.96)',
    borderColor: 'rgba(72, 224, 255, 0.42)',
    textStyle: { color: '#e7f7ff' },
    axisPointer: {
      type: 'cross',
      snap: true,
      lineStyle: { color: 'rgba(72, 224, 255, 0.7)', type: 'dashed' },
      crossStyle: { color: 'rgba(72, 224, 255, 0.7)' },
      label: { color: '#e7f7ff', backgroundColor: '#0a3263' }
    },
    valueFormatter: (value) => `${Number(value).toFixed(1)} kW`
  },
  toolbox: {
    top: 4,
    right: 18,
    iconStyle: { borderColor: '#8bc8ff' },
    emphasis: { iconStyle: { borderColor: '#48e0ff' } },
    feature: {
      dataZoom: { title: { zoom: '框选放大', back: '返回上一级' }, yAxisIndex: 'none' },
      restore: { title: '重置缩放' }
    }
  },
  legend: {
    top: 10,
    left: 'center',
    itemWidth: 18,
    itemHeight: 7,
    textStyle: { color: '#b9d4ed', fontSize: 10 },
    data: [...powerChartProfiles.value.map((profile) => profile.label), '供需平衡偏差']
  },
  grid: { top: 68, left: 58, right: 26, bottom: 68 },
  xAxis: {
    type: 'category',
    data: chartDisplayTimeLabels.value,
    boundaryGap: false,
    axisLine: { lineStyle: { color: 'rgba(139, 200, 255, 0.32)' } },
    axisTick: { show: false },
    axisLabel: { color: '#8bc8ff', hideOverlap: true, fontSize: 9 }
  },
  yAxis: {
    type: 'value',
    name: '功率 (kW)',
    scale: true,
    nameTextStyle: { color: '#b9d4ed', align: 'left' },
    axisLabel: { color: '#8bc8ff' },
    splitLine: { lineStyle: { color: 'rgba(139, 200, 255, 0.1)' } }
  },
  dataZoom: [
    { type: 'inside', zoomOnMouseWheel: true, moveOnMouseMove: true, moveOnMouseWheel: false },
    {
      type: 'slider',
      bottom: 14,
      height: 18,
      borderColor: 'rgba(72, 224, 255, 0.2)',
      backgroundColor: 'rgba(3, 13, 35, 0.8)',
      fillerColor: 'rgba(60, 146, 255, 0.24)',
      handleStyle: { color: '#48e0ff', borderColor: '#48e0ff' },
      moveHandleStyle: { color: '#3c92ff' },
      textStyle: { color: '#8bc8ff', fontSize: 9 }
    }
  ],
  series: [
    ...powerChartProfiles.value.map((profile) => ({
      name: profile.label,
      type: 'line' as const,
      smooth: 0.45,
      smoothMonotone: 'x' as const,
      showSymbol: false,
      connectNulls: true,
      lineStyle: {
        color: profile.color,
        width: profile.resourceType === ResourceType.Load ? 1.5 : 2,
        type: profile.resourceType === ResourceType.Load ? 'dashed' as const : 'solid' as const
      },
      data: profile.values
    })),
    {
      name: '供需平衡偏差',
      type: 'line',
      smooth: 0.42,
      smoothMonotone: 'x',
      showSymbol: false,
      connectNulls: true,
      lineStyle: { color: '#5cebd1', width: 2.5, type: 'dashed' },
      data: netSupplyPower.value,
      markLine: {
        symbol: 'none',
        silent: true,
        lineStyle: { type: 'dashed', width: 1.2 },
        label: { show: true, formatter: '{b}', fontSize: 9, lineHeight: 13 },
        data: chartMarkers.value
      }
    }
  ]
}))

function normalizePowerChartTimestamp(timestamp: string): string {
  const value = new Date(timestamp)
  if (Number.isNaN(value.getTime())) {
    return timestamp
  }
  const sampleIntervalMilliseconds = powerChartSampleIntervalSeconds * 1000
  const bucketTimestamp = Math.floor(value.getTime() / sampleIntervalMilliseconds) * sampleIntervalMilliseconds
  return new Date(bucketTimestamp).toISOString()
}

function formatPowerChartTime(timestamp: string): string {
  const value = new Date(timestamp)
  if (Number.isNaN(value.getTime())) {
    return timestamp
  }
  return value.toLocaleTimeString('zh-CN', { hour12: false, hour: '2-digit', minute: '2-digit', second: '2-digit' })
}

function inferResourceType(stationId: string): ResourceType {
  if (stationId.startsWith('pv-')) return ResourceType.Photovoltaic
  if (stationId.startsWith('wind-')) return ResourceType.Wind
  if (stationId.startsWith('thermal-')) return ResourceType.Thermal
  if (stationId.startsWith('storage-')) return ResourceType.Storage
  if (stationId.startsWith('load-')) return ResourceType.Load
  if (stationId.startsWith('charge-')) return ResourceType.Charging
  return ResourceType.Grid
}

function summaryTimestamp(key: string): string | null {
  const value = chartRunSummary.value[key]
  return typeof value === 'string' && value !== '' ? value : null
}

function visibleSummaryTimestamp(key: string): string | null {
  return visiblePowerChartTimestamp(summaryTimestamp(key))
}

function visiblePowerChartTimestamp(timestamp: string | null): string | null {
  const firstTimestamp = chartTimestamps.value[0]
  const lastTimestamp = chartTimestamps.value.at(-1)
  if (timestamp === null || firstTimestamp === undefined || lastTimestamp === undefined) {
    return null
  }
  const value = new Date(timestamp).getTime()
  return value >= new Date(firstTimestamp).getTime() && value <= new Date(lastTimestamp).getTime()
    ? timestamp
    : null
}

function firstCommandTimestamp(): string | null {
  const timestamps = chartExecution.value?.commands
    .map((command) => command.sendTime)
    .filter((timestamp) => timestamp !== '')
    .sort() ?? []
  return timestamps[0] ?? null
}

function nearestPowerChartLabel(timestamp: string | null): string | null {
  if (timestamp === null || chartTimestamps.value.length === 0) {
    return null
  }
  const target = new Date(timestamp).getTime()
  if (!Number.isFinite(target)) {
    return null
  }
  let nearestIndex = 0
  let nearestDistance = Number.POSITIVE_INFINITY
  chartTimestamps.value.forEach((candidate, index) => {
    const distance = Math.abs(new Date(candidate).getTime() - target)
    if (distance < nearestDistance) {
      nearestDistance = distance
      nearestIndex = index
    }
  })
  if (nearestDistance > 5000) {
    return null
  }
  return chartTimeLabels.value[nearestIndex] ?? null
}

function buildPowerChartMarkers(): PowerChartMarker[] {
  const definitions = [
    { label: '故障注入开始', timestamp: summaryTimestamp('injectionStartedAt'), color: '#ff4b58' },
    { label: '智能体调度开始', timestamp: summaryTimestamp('orchestrationScheduledAt'), color: '#5aa7ff' },
    { label: '智能体调度结束', timestamp: summaryTimestamp('orchestrationStageEndsAt'), color: '#63efae' },
    { label: '故障恢复完成', timestamp: summaryTimestamp('recoveredAt'), color: '#b9d4ed' }
  ]
  const occupiedLabels = new Set<string>()
  return definitions.flatMap((definition) => {
    const xAxis = nearestPowerChartLabel(definition.timestamp)
    if (xAxis === null || occupiedLabels.has(`${definition.label}|${xAxis}`)) {
      return []
    }
    occupiedLabels.add(`${definition.label}|${xAxis}`)
    return [{
      name: `${definition.label}\n${formatPowerChartTime(definition.timestamp ?? '')}`,
      xAxis,
      lineStyle: { color: definition.color },
      label: { color: definition.color }
    }]
  })
}

function formatExperimentPhaseRange(phase: ExperimentPhaseInterval): string {
  if (phase.startAt === null && phase.endAt === null) {
    return '等待真实时间点'
  }
  return `${phase.startAt === null ? '--' : formatPowerChartTime(phase.startAt)} - ${phase.endAt === null ? '进行中' : formatPowerChartTime(phase.endAt)}`
}

function mergeRealtimePowerPoints(
  existingPoints: ExperimentReplayPoint[],
  incomingPoints: ExperimentReplayPoint[],
  cutoffTimestamp: number
): ExperimentReplayPoint[] {
  const pointsByStationAndTime = new Map<string, ExperimentReplayPoint>()
  for (const point of [...existingPoints, ...incomingPoints]) {
    const timestamp = new Date(point.timestamp).getTime()
    if (!Number.isFinite(timestamp) || timestamp < cutoffTimestamp) {
      continue
    }
    pointsByStationAndTime.set(`${point.stationId}|${normalizePowerChartTimestamp(point.timestamp)}`, point)
  }
  return [...pointsByStationAndTime.values()]
    .sort((first, second) => new Date(first.timestamp).getTime() - new Date(second.timestamp).getTime())
    .slice(-10000)
}

function stationSnapshotsAsPowerPoints(): ExperimentReplayPoint[] {
  return stations.value.flatMap((station) => station.updatedAt === null ? [] : [{
    timestamp: station.updatedAt,
    stationId: station.stationId,
    activePower: station.activePower,
    availableCapacity: station.availableCapacity,
    latency: station.latency,
    packetLossRate: station.packetLossRate,
    dataAge: station.dataAge,
    linkState: station.linkState,
    online: station.online
  }])
}

async function fetchContinuousPowerPoints(runId: string, from: Date, to: Date): Promise<ExperimentReplayPoint[]> {
  try {
    const realtimePoints = await fetchRealtimePowerTelemetry(from.toISOString(), to.toISOString(), 10000, 10)
    return mergeRealtimePowerPoints([], realtimePoints, from.getTime())
  } catch {
    const fallbackPoints = runId === ''
      ? stationSnapshotsAsPowerPoints()
      : [...await fetchExperimentReplay(runId, undefined, 1000, true, true), ...stationSnapshotsAsPowerPoints()]
    return mergeRealtimePowerPoints(chartReplayPoints.value, fallbackPoints, from.getTime())
  }
}

function restoreQueueItems(): FaultQueueItem[] {
  const storedQueueItems = window.localStorage.getItem(queueStorageKey)
  if (storedQueueItems === null) {
    return []
  }
  try {
    const parsedQueueItems = JSON.parse(storedQueueItems) as unknown
    return Array.isArray(parsedQueueItems) ? parsedQueueItems.map((item) => ({
      ...item as FaultQueueItem,
      durationMinutes: (item as Partial<FaultQueueItem>).status === '待执行'
        ? fixedFaultInjectionDurationMinutes
        : (item as Partial<FaultQueueItem>).durationMinutes ?? fixedFaultInjectionDurationMinutes,
      faultParameters: (item as Partial<FaultQueueItem>).faultParameters ?? {},
      severityLevel: (item as Partial<FaultQueueItem>).severityLevel ?? 3,
      automaticRecovery: (item as Partial<FaultQueueItem>).automaticRecovery ?? true,
      remark: (item as Partial<FaultQueueItem>).remark ?? ''
    })) : []
  } catch {
    return []
  }
}

function persistQueueItems(): void {
  window.localStorage.setItem(queueStorageKey, JSON.stringify(queueItems.value))
}

function openExperimentNamingDialog(queueItemId: string, action: ExperimentNamingAction = 'edit'): void {
  const queueItem = queueItems.value.find((item) => item.queueId === queueItemId)
  if (queueItem === undefined) {
    return
  }
  namingQueueItemId.value = queueItemId
  experimentNameDraft.value = queueItem.experimentName ?? ''
  experimentNamingAction.value = action
}

function saveExperimentName(): void {
  const experimentName = experimentNameDraft.value.trim()
  const queueItem = queueItems.value.find((item) => item.queueId === namingQueueItemId.value)
  if (queueItem === undefined || experimentName === '' || experimentNameIsDuplicate.value) {
    return
  }
  const namingAction = experimentNamingAction.value
  queueItem.experimentName = experimentName
  namingQueueItemId.value = null
  experimentNameDraft.value = ''
  experimentNamingAction.value = 'edit'
  persistQueueItems()
  if (namingAction === 'inject') {
    void startInjection(queueItem.queueId)
  }
}

function closeExperimentNamingDialog(): void {
  const queueItem = queueItems.value.find((item) => item.queueId === namingQueueItemId.value)
  if (queueItem?.experimentName === undefined) {
    return
  }
  namingQueueItemId.value = null
  experimentNameDraft.value = ''
  experimentNamingAction.value = 'edit'
}

function deleteCompletedExperiment(): void {
  const queueItem = queueItems.value.find((item) => item.queueId === namingQueueItemId.value)
  if (queueItem?.status !== '已完成') {
    return
  }
  queueItems.value = queueItems.value.filter((item) => item.queueId !== queueItem.queueId)
  namingQueueItemId.value = null
  experimentNameDraft.value = ''
  experimentNamingAction.value = 'edit'
  persistQueueItems()
}

function openExperimentResults(experimentRunId: string | undefined): void {
  if (experimentRunId === undefined || experimentRunId === '') {
    return
  }
  void router.push({ name: 'results', query: { runId: experimentRunId } })
}

function getStationIcon(resourceType: ResourceType): Component {
  if (resourceType === ResourceType.Wind) {
    return WindPower
  }
  if (resourceType === ResourceType.Photovoltaic) {
    return Sunny
  }
  if (resourceType === ResourceType.Storage) {
    return Cpu
  }
  return Lightning
}

function selectFault(fault: FaultDefinition): void {
  selectedFaultCode.value = fault.code
  selectedStationIds.value = [...fault.defaultTargets]
  parameterValues.value = Object.fromEntries(fault.parameters.map((parameter) => [parameter.key, parameter.defaultValue]))
  severityLevel.value = fault.impactRate >= 60 ? 4 : 3
  unsupportedStationName.value = ''
}

function isStationCompatible(resourceType: ResourceType): boolean {
  return selectedFault.value.targetTypes.includes(resourceType)
}

function toggleStationSelection(stationId: string, resourceType: ResourceType): void {
  if (!isStationCompatible(resourceType)) {
    const station = stations.value.find((item) => item.stationId === stationId)
    unsupportedStationName.value = station?.stationName ?? ''
    return
  }
  unsupportedStationName.value = ''
  selectedStationIds.value = selectedStationIds.value.includes(stationId)
    ? selectedStationIds.value.filter((selectedStationId) => selectedStationId !== stationId)
    : [...selectedStationIds.value, stationId]
}

function switchCategory(category: FaultCategory): void {
  activeCategory.value = category
  const firstFault = faultCatalog.value.find((fault) => fault.category === category)
  if (firstFault) {
    selectFault(firstFault)
  }
}

function createQueueItem(): FaultQueueItem {
  return {
    queueId: `queue-${Date.now()}`,
    faultCode: selectedFault.value.code,
    faultName: selectedFault.value.name,
    targetStationIds: [...selectedStationIds.value],
    targetNames: selectedStations.value.map((station) => station.stationName),
    durationMinutes: fixedFaultInjectionDurationMinutes,
    faultParameters: { ...parameterValues.value },
    severityLevel: severityLevel.value,
    automaticRecovery: automaticRecovery.value,
    remark: experimentRemark.value,
    status: '待执行'
  }
}

function addToQueue(): void {
  if (selectedStations.value.length === 0) {
    return
  }
  const queueItem = createQueueItem()
  queueItems.value.push(queueItem)
  openExperimentNamingDialog(queueItem.queueId, 'queue')
}

function removeQueueItem(queueId: string): void {
  queueItems.value = queueItems.value.filter((item) => item.queueId !== queueId)
  persistQueueItems()
}

function toggleSummaryPanel(panel: 'faults' | 'queue' | 'completed'): void {
  activeSummaryPanel.value = activeSummaryPanel.value === panel ? null : panel
}

function moveQueueItem(queueId: string, direction: -1 | 1): void {
  const pendingQueueIds = pendingQueueItems.value.map((item) => item.queueId)
  const currentPendingIndex = pendingQueueIds.indexOf(queueId)
  const targetQueueId = pendingQueueIds[currentPendingIndex + direction]
  if (currentPendingIndex < 0 || targetQueueId === undefined) {
    return
  }
  const currentIndex = queueItems.value.findIndex((item) => item.queueId === queueId)
  const targetIndex = queueItems.value.findIndex((item) => item.queueId === targetQueueId)
  ;[queueItems.value[currentIndex], queueItems.value[targetIndex]] = [queueItems.value[targetIndex], queueItems.value[currentIndex]]
  persistQueueItems()
}

function executeNextQueueItem(): void {
  const nextQueueItem = pendingQueueItems.value[0]
  if (nextQueueItem === undefined || isRunning.value) {
    return
  }
  const fault = faultCatalog.value.find((item) => item.code === nextQueueItem.faultCode)
  if (fault === undefined) {
    return
  }
  if (!nextQueueItem.experimentName?.trim()) {
    openExperimentNamingDialog(nextQueueItem.queueId, 'inject')
    return
  }
  startInjection(nextQueueItem.queueId)
}

async function startInjection(queueId?: string): Promise<void> {
  if (selectedStationIds.value.length === 0 || isRunning.value || scenarioLoadError.value !== '') {
    return
  }
  let queuedItem = queueId === undefined
    ? queueItems.value.find((item) => item.status === '待执行' && item.faultCode === selectedFault.value.code)
    : queueItems.value.find((item) => item.queueId === queueId && item.status === '待执行')
  if (queuedItem === undefined) {
    queuedItem = createQueueItem()
    queueItems.value.push(queuedItem)
  }
  if (!queuedItem.experimentName?.trim()) {
    openExperimentNamingDialog(queuedItem.queueId, 'inject')
    return
  }
  queuedItem.durationMinutes = fixedFaultInjectionDurationMinutes
  queuedItem.status = '启动中'
  queuedItem.failureReason = undefined
  persistQueueItems()
  try {
    const response = await startExperiment(queuedItem.faultCode, {
      targetStationIds: [...queuedItem.targetStationIds],
      durationSeconds: queuedItem.durationMinutes * 60,
      faultParameters: { ...queuedItem.faultParameters },
      severityLevel: queuedItem.severityLevel,
      automaticRecovery: queuedItem.automaticRecovery,
      remark: queuedItem.remark
    })
    queuedItem.experimentRunId = response.experimentRunId
    queuedItem.status = '注入中'
    activeRunId.value = response.experimentRunId
    isRunning.value = true
    runProgress.value = 0
    persistQueueItems()
    startStatusPolling()
  } catch (error) {
    queuedItem.status = '失败'
    queuedItem.failureReason = error instanceof Error ? error.message : '启动实验请求失败'
    persistQueueItems()
  }
}

async function stopInjection(): Promise<void> {
  const runningQueueItem = queueItems.value.find((item) => item.experimentRunId === activeRunId.value)
  if (runningQueueItem === undefined || runningQueueItem.experimentRunId === undefined || isStopping.value) {
    return
  }
  isStopping.value = true
  runtimeStatusError.value = ''
  runningQueueItem.status = '恢复中'
  persistQueueItems()
  try {
    await stopExperiment(runningQueueItem.experimentRunId)
    runningQueueItem.status = '恢复中'
    persistQueueItems()
    await syncExperimentStatus()
  } catch (error) {
    runningQueueItem.failureReason = error instanceof Error ? error.message : '停止实验请求失败'
    runtimeStatusError.value = runningQueueItem.failureReason
    persistQueueItems()
  } finally {
    isStopping.value = false
  }
}

function resetConfiguration(): void {
  selectFault(selectedFault.value)
  durationMinutes.value = fixedFaultInjectionDurationMinutes
  automaticRecovery.value = true
  experimentRemark.value = ''
}

function toFaultCategory(category: ExperimentScenario['category']): FaultCategory {
  return category === 'COMMUNICATION' ? 'communication' : category === 'DEVICE' ? 'device' : 'environment'
}

function scenarioIcon(category: FaultCategory): Component {
  return category === 'communication' ? Connection : category === 'device' ? Cpu : Cloudy
}

function scenarioTone(category: FaultCategory): FaultDefinition['tone'] {
  return category === 'communication' ? 'red' : category === 'device' ? 'amber' : 'cyan'
}

function toFaultDefinition(scenario: ExperimentScenario): FaultDefinition {
  const category = toFaultCategory(scenario.category)
  return {
    code: scenario.scenarioCode,
    name: scenario.scenarioName,
    summary: scenario.description,
    category,
    expectedLinkState: scenario.expectedLinkState,
    icon: scenarioIcon(category),
    tone: scenarioTone(category),
    targetTypes: scenario.supportedResourceTypes,
    defaultTargets: scenario.targetStationIds,
    impactLabel: scenario.expectedEvidenceType,
    impactRate: scenario.expectedLinkState === LinkState.S3 ? 80 : 50,
    parameters: Object.entries(scenario.parameterDefinitions).map(([key, definition]) => ({
      key,
      label: definition.name,
      unit: definition.unit,
      minimum: definition.minimum,
      maximum: definition.maximum,
      step: definition.step,
      defaultValue: definition.defaultValue
    }))
  }
}

async function refreshExperimentChart(runId: string, summary: ExperimentRunSummary): Promise<void> {
  chartLoading.value = true
  chartError.value = ''
  const queryTo = new Date()
  const queryFrom = new Date(queryTo.getTime() - powerChartHistoryWindowMinutes * 60 * 1000)
  const planId = typeof summary.dispatchPlanId === 'string' ? summary.dispatchPlanId : ''
  const executionRequest = planId === ''
    ? Promise.resolve<MarketDispatchExecution | null>(null)
    : fetchMarketDispatchExecution(planId).catch(() => null)
  const timelineRequest = runId === ''
    ? Promise.resolve<ExperimentTimelinePoint[]>([])
    : fetchExperimentTimeline(runId).catch(() => [])
  try {
    const [replayPoints, timelinePoints, execution] = await Promise.all([
      fetchContinuousPowerPoints(runId, queryFrom, queryTo),
      timelineRequest,
      executionRequest
    ])
    chartRunId.value = runId
    chartRunSummary.value = summary
    chartReplayPoints.value = replayPoints
    chartTimelinePoints.value = timelinePoints
    chartExecution.value = execution
  } catch (error) {
    chartError.value = error instanceof Error ? error.message : '实验功率曲线加载失败'
  } finally {
    chartLoading.value = false
  }
}

function startChartPolling(): void {
  if (chartTimer !== undefined) {
    window.clearInterval(chartTimer)
  }
  chartTimer = window.setInterval(() => {
    void refreshExperimentChart(chartRunId.value, chartRunSummary.value)
  }, powerChartSampleIntervalSeconds * 1000)
}

async function syncExperimentStatus(): Promise<void> {
  try {
    const run = await fetchCurrentExperimentRun()
    if (run === null) {
      isRunning.value = false
      const latestRuns = await fetchLatestExperimentRuns(10)
      const finishedRun = activeRunId.value === '' ? undefined
        : latestRuns.find((latestRun) => latestRun.runId === activeRunId.value)
      if (finishedRun !== undefined) {
        applyRunStatus(finishedRun)
      } else {
        syncQueueItemsFromRuns(latestRuns)
        chartRunId.value = ''
        chartRunSummary.value = {}
        chartTimelinePoints.value = []
        chartExecution.value = null
        void refreshExperimentChart('', {})
      }
      return
    }
    applyRunStatus(run)
  } catch (error) {
    runtimeStatusError.value = error instanceof Error ? error.message : '实验状态刷新失败'
  }
}

function applyRunStatus(run: ExperimentRun): void {
  const summary = parseRunSummary(run.resultSummary)
  const runIsActive = ['STARTING', 'INJECTING', 'FAULT_OBSERVED', 'ORCHESTRATING', 'RECOVERING'].includes(run.status)
  const chartRunChanged = chartRunId.value !== run.runId
  activeRunStatus.value = run.status
  activeRunSummary.value = summary
  chartRunSummary.value = summary
  if (chartRunChanged) {
    void refreshExperimentChart(run.runId, summary)
  }
  if (runIsActive) {
    syncConfigurationFromRun(run, summary)
  }
  let item = queueItems.value.find((queueItem) => queueItem.experimentRunId === run.runId)
  if (item === undefined) {
    item = {
      queueId: `run-${run.runId}`,
      faultCode: run.scenarioCode,
      faultName: run.scenarioName,
      targetStationIds: [...selectedStationIds.value],
      targetNames: selectedStations.value.map((station) => station.stationName),
      durationMinutes: durationMinutes.value,
      faultParameters: { ...parameterValues.value },
      severityLevel: severityLevel.value,
      automaticRecovery: automaticRecovery.value,
      remark: experimentRemark.value,
      experimentRunId: run.runId,
      status: '注入中'
    }
    queueItems.value.push(item)
  } else {
    item.faultCode = run.scenarioCode
    item.faultName = run.scenarioName
    item.targetStationIds = [...selectedStationIds.value]
    item.targetNames = selectedStations.value.map((station) => station.stationName)
    item.durationMinutes = durationMinutes.value
    item.faultParameters = { ...parameterValues.value }
    item.automaticRecovery = automaticRecovery.value
    item.remark = experimentRemark.value
  }
  activeRunId.value = run.runId
  isRunning.value = runIsActive
  item.status = toQueueStatus(run)
  if (item.status === '失败') {
    item.failureReason = run.resultSummary
  }
  runProgress.value = isRunning.value ? calculateRunProgress(run, summary) : 0
  updateStationExperimentStatuses(run.resultSummary)
  persistQueueItems()
}

function syncQueueItemsFromRuns(runs: ExperimentRun[]): void {
  let changed = false
  for (const run of runs) {
    const item = queueItems.value.find((queueItem) => queueItem.experimentRunId === run.runId)
    if (item === undefined) {
      continue
    }
    item.faultCode = run.scenarioCode
    item.faultName = run.scenarioName
    item.status = toQueueStatus(run)
    if (item.status === '失败') {
      item.failureReason = run.resultSummary
    }
    changed = true
  }
  if (changed) {
    persistQueueItems()
  }
}

function toQueueStatus(run: ExperimentRun): QueueStatus {
  if (run.status === 'STARTING' || run.status === 'INJECTING') {
    return '注入中'
  }
  if (run.status === 'FAULT_OBSERVED') {
    return '故障已观测'
  }
  if (run.status === 'ORCHESTRATING') {
    return '智能体调度中'
  }
  if (run.status === 'RECOVERING') {
    return '恢复中'
  }
  if (run.status === 'COMPLETED') {
    return '已完成'
  }
  if (run.status === 'STOPPED') {
    return '已停止'
  }
  if (run.status === 'FAILED' || run.status === 'INJECTION_NOT_OBSERVED') {
    return '失败'
  }
  return '注入中'
}

function parseRunSummary(resultSummary: string): ExperimentRunSummary {
  try {
    return JSON.parse(resultSummary.split('；', 1)[0]) as ExperimentRunSummary
  } catch {
    return {}
  }
}

function syncConfigurationFromRun(run: ExperimentRun, summary: ExperimentRunSummary): void {
  const fault = visibleFaultCatalog.value.find((item) => item.code === run.scenarioCode)
  activeExpectedLinkState.value = Object.values(LinkState).includes(summary.expectedLinkState as LinkState)
    ? summary.expectedLinkState as LinkState
    : null
  if (fault === undefined) {
    return
  }
  if (selectedFaultCode.value !== fault.code) {
    activeCategory.value = fault.category
    selectFault(fault)
  }
  const targetStationIds = Array.isArray(summary.targetStationIds)
    ? summary.targetStationIds.filter((stationId): stationId is string => typeof stationId === 'string')
    : []
  if (targetStationIds.length > 0) {
    selectedStationIds.value = targetStationIds
  }
  if (summary.faultParameters !== null && typeof summary.faultParameters === 'object') {
    parameterValues.value = Object.fromEntries(Object.entries(summary.faultParameters)
      .filter((entry): entry is [string, number] => typeof entry[1] === 'number' && Number.isFinite(entry[1])))
  }
  if (typeof summary.durationSeconds === 'number' && Number.isFinite(summary.durationSeconds) && summary.durationSeconds > 0) {
    durationMinutes.value = summary.durationSeconds / 60
  }
  if (typeof summary.automaticRecovery === 'boolean') {
    automaticRecovery.value = summary.automaticRecovery
  }
  if (typeof summary.remark === 'string') {
    experimentRemark.value = summary.remark
  }
}

function calculateRunProgress(run: ExperimentRun, summary: ExperimentRunSummary): number {
  const completedStages = executionStages.filter((stage) => isExecutionStageCompleted(stage.code, run.status, summary)).length
  return Math.round(completedStages / executionStages.length * 100)
}

function isExecutionStageCompleted(
  stageCode: typeof executionStages[number]['code'],
  runStatus: ExperimentRun['status'] | null = activeRunStatus.value,
  summary: ExperimentRunSummary = activeRunSummary.value
): boolean {
  if (runStatus === null) {
    return false
  }
  const faultObserved = summary.evidenceStatus === 'FAULT_OBSERVED'
    || ['FAULT_OBSERVED', 'ORCHESTRATING', 'RECOVERING', 'COMPLETED'].includes(runStatus)
  const planGenerated = typeof summary.dispatchPlanId === 'string' && summary.dispatchPlanId !== ''
  const targetReached = summary.convergenceStatus === 'TARGET_REACHED'
  if (stageCode === 'INJECTED') return runStatus !== 'STARTING'
  if (stageCode === 'OBSERVED') return faultObserved
  if (stageCode === 'PLANNED') return planGenerated
  if (stageCode === 'TARGET_REACHED') return targetReached
  return runStatus === 'COMPLETED' && summary.recoveryReportStatus === 'RECOVERED'
}

function updateStationExperimentStatuses(resultSummary: string): void {
  try {
    const summary = JSON.parse(resultSummary.split('；', 1)[0]) as Record<string, unknown>
    const observed = summary.observedFaultValues as Record<string, unknown> | undefined
    const recovered = summary.recoveredLinkStates as Record<string, unknown> | undefined
    const nextStatuses: Record<string, StationExperimentStatus> = {}
    for (const stationId of selectedStationIds.value) {
      const observedValue = observed?.[stationId] as { activeFaultCode?: string; environmentMetrics?: Record<string, number> } | undefined
      nextStatuses[stationId] = {
        observedFaultCode: typeof observedValue?.activeFaultCode === 'string' ? observedValue.activeFaultCode : null,
        evidenceStatus: observed?.[stationId] === undefined ? null : String(summary.evidenceStatus ?? 'FAULT_OBSERVED'),
        recoveryStatus: recovered?.[stationId] === undefined ? null : String(summary.recoveryReportStatus ?? 'S0'),
        environmentMetrics: observedValue !== undefined
          ? (observedValue.environmentMetrics ?? null)
          : null
      }
    }
    stationExperimentStatuses.value = nextStatuses
  } catch {
    runtimeStatusError.value = '实验摘要解析失败，已保留最近逐站实时状态'
  }
}

async function refreshStations(): Promise<void> {
  try {
    stations.value = await fetchStations()
    stationLoadError.value = ''
  } catch (error) {
    stationLoadError.value = error instanceof Error ? error.message : '场站实时状态加载失败'
  }
}

function isParameterValueValid(parameter: FaultParameter): boolean {
  const value = parameterValues.value[parameter.key]
  return Number.isFinite(value) && value >= parameter.minimum && value <= parameter.maximum
}

function environmentMetricLabel(name: string): string {
  const labels: Record<string, string> = {
    windSpeed: '风速（米/秒）', turbulence: '湍流比', irradiance: '辐照度（瓦/平方米）', cloudCover: '云量比',
    temperature: '温度（摄氏度）', ambientTemperature: '环境温度（摄氏度）', moduleTemperature: '组件温度（摄氏度）',
    dustDensity: '积尘密度（克/平方米）', visibility: '能见度（千米）', conversionEfficiency: '转换效率',
    voltage: '电压（伏）', frequency: '频率（赫兹）'
  }
  return labels[name] ?? '其他环境指标'
}

function formatLinkState(linkState: LinkState | undefined): string {
  if (linkState === undefined) {
    return '暂无数据'
  }
  return linkStateStages.find((stage) => stage.code === linkState)?.label ?? '未知状态'
}

function formatFaultName(faultCode: string | null | undefined): string {
  if (faultCode === null || faultCode === undefined || faultCode === '') {
    return '未知故障'
  }
  return faultCatalog.value.find((fault) => fault.code === faultCode)?.name
    ?? activeQueueItem.value?.faultName
    ?? '未知故障'
}

function formatEvidenceState(status: string | null | undefined): string {
  const labels: Record<string, string> = {
    FAULT_OBSERVED: '故障已确认',
    WAITING_FOR_FAULT: '等待故障证据',
    RECOVERED: '已恢复',
    RECOVERING: '恢复中',
    FAILED: '失败',
    PENDING: '等待处理'
  }
  return status === null || status === undefined ? '暂无实验故障证据' : labels[status] ?? '状态已记录'
}

function formatStationUpdatedAt(timestamp: string | null | undefined): string {
  if (timestamp === null || timestamp === undefined || timestamp === '') {
    return '暂无数据'
  }
  const date = new Date(timestamp)
  return Number.isNaN(date.getTime()) ? '暂无数据' : date.toLocaleString('zh-CN', { hour12: false })
}

function formatActiveFault(station: StationState): string {
  const experimentStatus = stationExperimentStatuses.value[station.stationId]
  if (station.activeFaultCode !== null && station.activeFaultCode !== undefined) {
    return formatFaultName(station.activeFaultCode)
  }
  if (experimentStatus?.recoveryStatus !== null && experimentStatus?.recoveryStatus !== undefined) {
    return `已恢复（原故障：${formatFaultName(experimentStatus.observedFaultCode ?? activeQueueItem.value?.faultCode)}）`
  }
  return '暂无活动故障'
}

function formatEvidenceStatus(stationId: string): string {
  const experimentStatus = stationExperimentStatuses.value[stationId]
  if (experimentStatus?.recoveryStatus !== null && experimentStatus?.recoveryStatus !== undefined) {
    return `${formatEvidenceState(experimentStatus.evidenceStatus)} · 恢复：${formatEvidenceState(experimentStatus.recoveryStatus)}`
  }
  return formatEvidenceState(experimentStatus?.evidenceStatus)
}

function startStatusPolling(): void {
  if (statusTimer !== undefined) {
    window.clearInterval(statusTimer)
  }
  statusTimer = window.setInterval(() => {
    void syncExperimentStatus()
    void refreshStations()
  }, 2000)
}

onMounted(async () => {
  await refreshStations()
  try {
    const scenarios = await fetchExperimentScenarios()
    const definitions = scenarios.filter((scenario) => scenario.category !== 'LEGACY').map(toFaultDefinition)
    if (definitions.length > 0) {
      faultCatalog.value = definitions
      const initialFault = definitions.find((fault) => fault.code === selectedFaultCode.value) ?? definitions[0]
      selectFault(initialFault)
    }
  } catch (error) {
    scenarioLoadError.value = error instanceof Error ? error.message : '实验场景目录加载失败'
    faultCatalog.value = []
  }
  await syncExperimentStatus()
  startStatusPolling()
  startChartPolling()
})

onBeforeUnmount(() => {
  if (statusTimer !== undefined) {
    window.clearInterval(statusTimer)
  }
  if (chartTimer !== undefined) {
    window.clearInterval(chartTimer)
  }
})
</script>

<template>
  <section class="fault-workbench page-shell">
    <header class="workbench-header">
      <div class="workbench-header__main">
        <div class="workbench-header__mark"><Lightning /></div>
        <div>
          <span class="workbench-header__eyebrow">FAULT INJECTION WORKBENCH</span>
          <h1>实时注入</h1>
          <p>构建环境与通信故障，验证新能源场站在异常条件下的韧性与恢复能力。</p>
        </div>
      </div>
      <div class="workbench-header__status">
        <span class="status-dot" :class="{ 'is-running': isRunning }"></span>
        <div>
          <small>实验环境</small>
          <strong>{{ isRunning ? '故障注入中' : '就绪 · 安全隔离' }}</strong>
        </div>
        <span class="workbench-header__mode">仿真模式</span>
      </div>
    </header>

    <div class="workbench-summary">
      <button
        type="button"
        :class="{ 'is-active': activeSummaryPanel === 'faults' }"
        :aria-expanded="activeSummaryPanel === 'faults'"
        @click="toggleSummaryPanel('faults')"
      >
        <span>故障场景</span><strong>{{ availableFaultCount }}</strong><small>环境 / 通信 / 设备</small>
      </button>
      <button
        type="button"
        :class="{ 'is-active': activeSummaryPanel === 'completed' }"
        :aria-expanded="activeSummaryPanel === 'completed'"
        @click="toggleSummaryPanel('completed')"
      >
        <span>已注入的实验</span><strong>{{ completedQueueItems.length }}</strong><small>已完成</small>
      </button>
    </div>

    <section v-if="activeSummaryPanel" class="summary-drawer panel-surface">
      <template v-if="activeSummaryPanel === 'faults'">
        <div class="panel-heading">
          <div><span>FAULT CATALOG</span><h2>可选故障</h2></div>
          <small>{{ availableFaultCount }} 个场景</small>
        </div>
        <div class="summary-fault-list">
          <button
            v-for="fault in visibleFaultCatalog"
            :key="fault.code"
            type="button"
            class="summary-fault-item"
            :class="{ 'is-selected': selectedFaultCode === fault.code }"
            @click="selectFault(fault); activeSummaryPanel = null"
          >
            <component :is="fault.icon" />
            <span><strong>{{ fault.name }}</strong><small>{{ categoryLabels[fault.category] }}</small></span>
          </button>
        </div>
      </template>
      <template v-else-if="activeSummaryPanel === 'queue'">
        <div class="panel-heading">
          <div><span>SEQUENCE</span><h2>待执行任务</h2></div>
          <div class="queue-panel-actions">
            <small>{{ pendingQueueItems.length }} 项</small>
            <button type="button" :disabled="isRunning || pendingQueueItems.length === 0" @click="executeNextQueueItem"><VideoPlay />执行下一任务</button>
          </div>
        </div>
        <div v-if="pendingQueueItems.length" class="queue-list">
          <article v-for="(item, index) in pendingQueueItems" :key="item.queueId" class="queue-item">
            <span class="queue-item__order">{{ String(index + 1).padStart(2, '0') }}</span>
            <div>
              <strong>{{ item.experimentName }}</strong>
              <span>{{ item.faultName }} · {{ item.targetNames.join('、') }} · {{ item.durationMinutes }} min</span>
            </div>
            <div class="queue-item__actions">
              <button type="button" title="上移任务" :disabled="index === 0" @click.stop="moveQueueItem(item.queueId, -1)"><ArrowUp /></button>
              <button type="button" title="下移任务" :disabled="index === pendingQueueItems.length - 1" @click.stop="moveQueueItem(item.queueId, 1)"><ArrowDown /></button>
              <button type="button" title="删除任务" @click.stop="removeQueueItem(item.queueId)"><Delete /></button>
            </div>
          </article>
        </div>
        <p v-else class="queue-empty">暂无待执行故障，可从左侧选择后加入队列。</p>
      </template>
      <template v-else>
        <div class="panel-heading">
          <div><span>EXPERIMENT HISTORY</span><h2>已注入的实验</h2></div>
          <small>{{ completedQueueItems.length }} 项</small>
        </div>
        <div v-if="completedQueueItems.length" class="completed-experiment-list">
          <article
            v-for="item in completedQueueItems"
            :key="item.queueId"
            class="completed-experiment-item"
            :class="{ 'is-clickable': item.experimentRunId !== undefined }"
            :tabindex="item.experimentRunId === undefined ? -1 : 0"
            @click="openExperimentResults(item.experimentRunId)"
            @keydown.enter="openExperimentResults(item.experimentRunId)"
            @keydown.space.prevent="openExperimentResults(item.experimentRunId)"
          >
            <span class="completed-experiment-item__icon"><CircleCheckFilled /></span>
            <div>
              <strong>{{ item.experimentName ?? '待命名实验' }}</strong>
              <span>{{ item.faultName }} · {{ item.targetNames.join('、') }} · {{ item.durationMinutes }} min</span>
            </div>
            <em>已完成</em>
            <button type="button" title="编辑实验名称" @click.stop="openExperimentNamingDialog(item.queueId)"><EditPen /></button>
          </article>
        </div>
        <p v-else class="queue-empty">暂无已注入的实验记录。</p>
      </template>
    </section>

    <main class="workbench-grid">
      <aside class="fault-library panel-surface">
        <div class="panel-heading">
          <div>
            <span>STEP 01</span>
            <h2>选择故障</h2>
          </div>
          <small>{{ visibleFaults.length }} 个场景</small>
        </div>

        <div class="category-tabs" role="tablist" aria-label="故障分类">
          <button
            v-for="category in categoryOptions"
            :key="category.value"
            type="button"
            :class="{ 'is-active': activeCategory === category.value }"
            @click="switchCategory(category.value)"
          >
            {{ category.label }}<span>{{ category.count }}</span>
          </button>
        </div>

        <div class="fault-cards">
          <button
            v-for="fault in visibleFaults"
            :key="fault.code"
            type="button"
            class="fault-card"
            :class="[`is-${fault.tone}`, { 'is-selected': selectedFaultCode === fault.code }]"
            @click="selectFault(fault)"
          >
            <span class="fault-card__icon"><component :is="fault.icon" /></span>
            <span class="fault-card__content">
              <strong>{{ fault.name }}</strong>
              <small>{{ fault.summary }}</small>
            </span>
            <CircleCheckFilled v-if="selectedFaultCode === fault.code" class="fault-card__check" />
          </button>
        </div>

        <div class="library-note">
          <WarningFilled />
          <span>所有故障仅作用于仿真环境，不会下发至真实设备。</span>
        </div>
      </aside>

      <section class="fault-config panel-surface">
        <div class="panel-heading">
          <div>
            <span>STEP 02</span>
            <h2>配置注入参数</h2>
          </div>
          <button type="button" class="text-button" @click="resetConfiguration"><RefreshRight />重置</button>
        </div>

        <div class="selected-fault-banner" :class="`is-${selectedFault.tone}`">
          <span class="selected-fault-banner__icon"><component :is="selectedFault.icon" /></span>
          <div>
            <small>{{ selectedFault.code }}</small>
            <strong>{{ selectedFault.name }}</strong>
            <p>{{ selectedFault.summary }}</p>
          </div>
        </div>

        <fieldset class="config-section">
          <legend><span>01</span> 影响对象</legend>
          <div class="station-options">
            <div class="station-topology">
              <div class="station-topology__ring station-topology__ring--outer"></div>
              <div class="station-topology__ring station-topology__ring--inner"></div>
              <div class="station-topology__core">
                <span>故障影响</span>
                <strong>{{ selectedStationIds.length }}</strong>
                <small>已选设备</small>
              </div>
              <button
              v-for="station in stations"
              :key="station.stationId"
              type="button"
              class="station-option"
              :class="{
                'is-selected': selectedStationIds.includes(station.stationId),
                'is-incompatible': !isStationCompatible(station.resourceType),
                'is-offline': !station.online
              }"
              :style="{ '--station-x': `${station.position.x}%`, '--station-y': `${station.position.y}%` }"
              :aria-pressed="selectedStationIds.includes(station.stationId)"
              @click="toggleStationSelection(station.stationId, station.resourceType)"
            >
              <span class="station-option__visual">
                <span class="station-option__icon"><component :is="getStationIcon(station.resourceType)" /></span>
                <i></i>
              </span>
              <span class="station-option__content">
                <span class="station-option__meta"><small>{{ resourceTypeLabelMap[station.resourceType] }}</small><em>{{ station.online ? '在线' : '离线' }}</em></span>
                <strong>{{ station.stationName }}</strong>
                <small>可用容量 <b>{{ station.availableCapacity }} kW</b></small>
              </span>
              <CircleCheckFilled v-if="selectedStationIds.includes(station.stationId)" class="station-option__check" />
              </button>
            </div>
            <div v-if="unsupportedStationName" class="station-selection-notice" role="alert">
              <WarningFilled />
              <span>当前“{{ selectedFault.name }}”不会影响“{{ unsupportedStationName }}”，请选择高亮的兼容设备。</span>
            </div>
          </div>
          <p v-if="stationLoadError" class="station-selection-notice" role="alert"><WarningFilled />{{ stationLoadError }}</p>
          <p v-if="scenarioLoadError" class="station-selection-notice" role="alert"><WarningFilled />{{ scenarioLoadError }}；已禁止真实注入。</p>
        </fieldset>

        <fieldset class="config-section">
          <legend><span>02</span> 故障强度</legend>
          <div class="severity-control">
            <div class="severity-control__label">
              <span>轻微</span><span>中等</span><span>严重</span><span>极端</span>
            </div>
            <input v-model.number="severityLevel" type="range" min="1" max="5" step="1" />
            <div class="severity-control__value">
              <strong>L{{ severityLevel }}</strong>
              <span>{{ severityLevel >= 5 ? '极端影响' : severityLevel >= 4 ? '严重影响' : severityLevel >= 3 ? '中等影响' : '轻微影响' }}</span>
            </div>
          </div>
        </fieldset>

        <fieldset class="config-section">
          <legend><span>03</span> 环境与故障参数</legend>
          <div class="parameter-grid">
            <label v-for="parameter in selectedFault.parameters" :key="parameter.key" :class="{ 'is-invalid': !isParameterValueValid(parameter) }">
              <span>{{ parameter.label }}</span>
              <div class="number-input">
                <input
                  v-model.number="parameterValues[parameter.key]"
                  type="number"
                  :min="parameter.minimum"
                  :max="parameter.maximum"
                  :step="parameter.step"
                />
                <em>{{ parameter.unit }}</em>
              </div>
              <small v-if="!isParameterValueValid(parameter)">请输入 {{ parameter.minimum }}–{{ parameter.maximum }} {{ parameter.unit }}</small>
            </label>
            <label>
              <span>持续时间</span>
              <div class="number-input">
                <input v-model.number="durationMinutes" type="number" min="1" max="1" readonly />
                <em>min</em>
              </div>
            </label>
            <label>
              <span>自动恢复</span>
              <button
                type="button"
                class="switch-control"
                :class="{ 'is-enabled': automaticRecovery }"
                role="switch"
                :aria-checked="automaticRecovery"
                @click="automaticRecovery = !automaticRecovery"
              >
                <i></i><strong>{{ automaticRecovery ? '启用' : '关闭' }}</strong>
              </button>
            </label>
          </div>
          <label class="remark-field">
            <span>实验备注</span>
            <input v-model.trim="experimentRemark" type="text" placeholder="可选：填写本轮故障注入目的" />
          </label>
        </fieldset>
      </section>

      <aside class="fault-review">
        <section class="panel-surface action-panel">
          <div class="review-actions">
            <button type="button" class="secondary-action" :disabled="selectedStations.length === 0" @click="addToQueue">加入队列</button>
            <button type="button" class="primary-action" :disabled="selectedStations.length === 0 || isRunning || scenarioLoadError !== ''" @click="startInjection">
              <VideoPlay />{{ isRunning ? '正在注入' : '立即注入' }}
            </button>
          </div>
          <div class="fault-workflow">
            <div class="fault-workflow__heading">
              <span>ACTUAL LINK STATE</span>
              <strong>实际：{{ actualTargetLinkStates.size > 0 ? [...actualTargetLinkStates].join(' / ') : '等待真实遥测' }} · 预期：{{ expectedLinkState }}</strong>
            </div>
            <div class="fault-workflow__chain">
              <template v-for="(stage, index) in linkStateStages" :key="stage.code">
                <article
                  class="fault-workflow__stage"
                  :class="{
                    'is-active': actualTargetLinkStates.has(stage.code),
                    'is-expected': expectedLinkState === stage.code
                  }"
                >
                  <span class="fault-workflow__index">{{ stage.code }}</span>
                  <div>
                    <div class="fault-workflow__stage-title">
                      <strong>{{ stage.label }}</strong>
                      <span v-if="actualTargetLinkStates.has(stage.code)" class="is-actual">实际</span>
                      <span v-if="expectedLinkState === stage.code" class="is-expected">预期</span>
                    </div>
                    <small>{{ stage.trigger }}</small>
                    <em>{{ stage.operation }}</em>
                  </div>
                </article>
                <i v-if="index < linkStateStages.length - 1" class="fault-workflow__link"></i>
              </template>
            </div>
          </div>
        </section>
        <section class="panel-surface station-runtime-panel">
          <div class="panel-heading"><div><span>REALTIME</span><h2>目标场站实际状态</h2></div></div>
          <p v-if="runtimeStatusError" class="station-selection-notice" role="alert"><WarningFilled />{{ runtimeStatusError }}</p>
          <p v-if="selectedStations.length === 0" class="queue-empty">暂无目标场站。</p>
          <article v-for="station in selectedStations" :key="station.stationId" class="runtime-station-card">
            <strong>{{ station.stationName }}</strong>
            <span>实际链路：{{ formatLinkState(station.linkState) }} · 时延：{{ station.latency ?? '暂无数据' }} 毫秒 · 丢包：{{ station.packetLossRate ?? '暂无数据' }}%</span>
            <span>心跳年龄：{{ station.heartbeatAge ?? '暂无数据' }} 秒 · 当前活动：{{ formatActiveFault(station) }}</span>
            <span>实验证据：{{ formatEvidenceStatus(station.stationId) }}</span>
            <span v-for="(value, key) in stationExperimentStatuses[station.stationId]?.environmentMetrics ?? {}" :key="key">{{ environmentMetricLabel(key) }}：{{ Number.isFinite(value) ? value : '暂无数据' }}</span>
            <span>更新时间：{{ formatStationUpdatedAt(station.updatedAt) }}</span>
          </article>
        </section>
      </aside>
    </main>

    <section class="power-curve-panel panel-surface" aria-labelledby="power-curve-title">
      <header class="power-curve-panel__header">
        <div>
          <span>模拟器实时遥测 · {{ chartRunId || '正常运行' }}</span>
          <h2 id="power-curve-title">功率曲线与调度效果</h2>
          <small>{{ chartReplayPoints.length }} 条真实遥测 · {{ chartTimelineEventCount }} 个链路事件 · {{ chartExecutionOutcomeText }} · 供需平衡偏差接近 0 kW 表示供需平衡 · 右侧留白为尚未产生的数据</small>
        </div>
      </header>
      <div class="power-curve-panel__chart">
        <EChartPanel
          v-if="chartTimeLabels.length > 0"
          :option="powerChartOption"
          height="360px"
          preserve-data-zoom
        />
        <div v-else class="power-curve-panel__empty">
          <RefreshRight v-if="chartLoading" />
          <WarningFilled v-else-if="chartError" />
          <Lightning v-else />
          <strong>{{ chartLoading ? '正在加载真实遥测曲线' : chartError || '暂无实验回放数据' }}</strong>
          <span>{{ chartError ? '请确认后端实时遥测接口可用' : '等待模拟器上报真实遥测数据' }}</span>
        </div>
      </div>
      <div class="experiment-phase-strip" aria-label="实验阶段区间">
        <strong>阶段区间</strong>
        <article v-for="phase in experimentPhaseIntervals" :key="phase.code" :class="`is-${phase.tone}`">
          <span>{{ phase.label }}</span>
          <time>{{ formatExperimentPhaseRange(phase) }}</time>
        </article>
      </div>
    </section>

    <section class="execution-dock" :class="{ 'is-active': isRunning }">
      <div class="execution-dock__identity">
        <span class="execution-dock__pulse"><Lightning /></span>
        <div>
          <small>{{ activeRunStatus !== null ? (activeQueueItem?.experimentName ?? '未命名实验') : '执行监控' }}</small>
          <strong>{{ executionDockStatusText }}</strong>
        </div>
      </div>
      <div class="execution-dock__progress">
        <div><span>{{ isRunning ? `${activeQueueItem?.targetNames.join('、') ?? selectedStations.map((station) => station.stationName).join('、')} · ${activeQueueItem?.durationMinutes ?? durationMinutes} 分钟` : '配置完成后点击“立即注入”启动实验' }}</span><strong>{{ runProgress }}%</strong></div>
        <div class="progress-track"><i :style="{ width: `${runProgress}%` }"></i></div>
      </div>
      <div class="execution-dock__steps">
        <span
          v-for="stage in executionStages"
          :key="stage.code"
          :class="{
            'is-done': isExecutionStageCompleted(stage.code),
            'is-current': stage.code === currentExecutionStageCode && isRunning
          }"
        >
          <CircleCheckFilled v-if="isExecutionStageCompleted(stage.code)" />
          <RefreshRight v-else-if="stage.code === 'RECOVERED'" />
          <Lightning v-else />{{ stage.label }}
        </span>
      </div>
      <button
        type="button"
        :class="{ 'is-completed': activeRunStatus === 'COMPLETED' }"
        :disabled="!isRunning || isStopping"
        @click="stopInjection"
      >
        {{ isStopping ? '正在停止' : activeRunStatus === 'COMPLETED' ? '实验已完成' : activeRunStatus === 'STOPPED' ? '实验已停止' : !isRunning ? '暂无运行实验' : '停止并恢复' }}
      </button>
    </section>

    <div v-if="namingQueueItemId" class="experiment-name-dialog" role="dialog" aria-modal="true" aria-labelledby="experiment-name-dialog-title">
      <form class="experiment-name-dialog__content" @submit.prevent="saveExperimentName">
        <span>EXPERIMENT NAME</span>
        <h2 id="experiment-name-dialog-title">{{ experimentNamingAction === 'inject' ? '命名并注入实验' : experimentNamingAction === 'queue' ? '命名并加入队列' : '编辑实验名称' }}</h2>
        <label>
          <span>实验名称</span>
          <input v-model.trim="experimentNameDraft" type="text" maxlength="30" autofocus placeholder="请输入唯一的实验名称" />
        </label>
        <p v-if="experimentNameIsDuplicate" class="is-error">该实验名称已存在，请使用其他名称。</p>
        <div class="experiment-name-dialog__actions">
          <button
            v-if="experimentNamingAction === 'edit' && queueItems.find((item) => item.queueId === namingQueueItemId)?.experimentName"
            type="button"
            @click="closeExperimentNamingDialog"
          >
            取消
          </button>
          <button
            v-if="experimentNamingAction === 'edit' && queueItems.find((item) => item.queueId === namingQueueItemId)?.experimentName"
            type="button"
            class="is-danger"
            @click="deleteCompletedExperiment"
          >
            删除实验
          </button>
          <button type="submit" :disabled="experimentNameDraft.trim() === '' || experimentNameIsDuplicate">
            {{ experimentNamingAction === 'inject' ? '保存并立即注入' : experimentNamingAction === 'queue' ? '保存并加入队列' : '保存名称' }}
          </button>
        </div>
      </form>
    </div>
  </section>
</template>

<style scoped>
.fault-workbench {
  --panel: linear-gradient(180deg, rgba(8, 34, 128, 0.8), rgba(4, 24, 95, 0.84));
  --panel-soft: #061c6a;
  --panel-raised: #082880;
  --line: rgba(72, 224, 255, 0.24);
  --line-strong: rgba(72, 224, 255, 0.58);
  --cyan: #42e8ff;
  --teal: #62ffb6;
  --amber: #ffd44f;
  --red: #ef7474;
  --violet: #a99ae8;
  --text: #e7f7ff;
  --muted: #8bc8ff;
  display: grid;
  gap: 16px;
  min-height: 100vh;
  padding: 0;
  color: var(--text);
  background: linear-gradient(180deg, #06164f 0%, #041141 24%, #020a25 100%);
}

.workbench-header,
.workbench-summary,
.panel-surface,
.execution-dock {
  border: 1px solid var(--line);
  background: var(--panel);
  border-radius: 10px;
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.18);
}

.workbench-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  min-height: 76px;
  padding: 12px 16px;
}

.workbench-header__main,
.workbench-header__status,
.execution-dock__identity {
  display: flex;
  align-items: center;
}

.workbench-header__main { gap: 14px; }
.workbench-header__mark,
.selected-fault-banner__icon {
  display: grid;
  place-items: center;
  flex: 0 0 auto;
  width: 42px;
  height: 42px;
  border: 1px solid rgba(72, 224, 255, 0.44);
  color: var(--cyan);
  border-radius: 9px;
  background: #0c274c;
}

.workbench-header__mark svg { width: 24px; }
.workbench-header__eyebrow,
.panel-heading span,
.selected-fault-banner small,
.execution-dock small {
  color: var(--cyan);
  font-size: 9px;
  font-weight: 700;
  letter-spacing: 0.16em;
}

.workbench-header h1 { margin: 3px 0 2px; font-size: 20px; letter-spacing: 0.02em; }
.workbench-header p { margin: 0; color: var(--muted); font-size: 11px; }
.workbench-header__status { gap: 10px; min-width: 255px; padding-left: 18px; border-left: 1px solid var(--line); }
.workbench-header__status small { display: block; color: var(--muted); font-size: 10px; }
.workbench-header__status strong { display: block; margin-top: 2px; font-size: 12px; }
.status-dot { width: 9px; height: 9px; border-radius: 50%; background: var(--teal); box-shadow: 0 0 0 5px rgba(85, 230, 181, 0.1); }
.status-dot.is-running { background: var(--amber); box-shadow: 0 0 0 5px rgba(255, 189, 92, 0.12); }
.workbench-header__mode { margin-left: auto; padding: 6px 9px; color: var(--cyan); border: 1px solid rgba(72, 224, 255, 0.26); border-radius: 6px; background: #0a2448; font-size: 10px; }

.workbench-summary { display: grid; grid-template-columns: repeat(2, 1fr); overflow: hidden; }
.workbench-summary > button { position: relative; display: grid; grid-template-columns: 1fr auto; align-items: center; gap: 2px 8px; padding: 10px 14px; border: 0; border-right: 1px solid var(--line); color: inherit; background: transparent; cursor: pointer; text-align: left; }
.workbench-summary > button:last-child { border-right: 0; }
.workbench-summary > button:hover,
.workbench-summary > button.is-active { background: #0b2649; }
.workbench-summary span { color: var(--muted); font-size: 10px; }
.workbench-summary strong { grid-column: 2; grid-row: 1 / span 2; font-size: 18px; line-height: 1; }
.workbench-summary small { grid-column: 1; color: #6f8eaf; font-size: 8px; }

.summary-drawer { padding: 14px; }
.summary-drawer .panel-heading { margin-bottom: 10px; }
.summary-fault-list { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 8px; }
.summary-fault-item { display: flex; align-items: center; gap: 8px; min-width: 0; padding: 9px; border: 1px solid var(--line); border-radius: 7px; color: var(--text); background: #081a34; cursor: pointer; text-align: left; }
.summary-fault-item:hover,
.summary-fault-item.is-selected { border-color: var(--line-strong); background: #0b2649; }
.summary-fault-item > svg { flex: 0 0 auto; width: 16px; color: var(--cyan); }
.summary-fault-item span { min-width: 0; }
.summary-fault-item strong,
.summary-fault-item small { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.summary-fault-item strong { font-size: 10px; }
.summary-fault-item small { margin-top: 3px; color: var(--muted); font-size: 8px; }

.workbench-grid { display: grid; grid-template-columns: minmax(250px, 0.72fr) minmax(430px, 1.35fr) minmax(290px, 0.86fr); align-items: stretch; gap: 12px; }
.fault-library { display: flex; flex-direction: column; }
.panel-surface { min-width: 0; padding: 15px; }
.panel-heading { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 12px; }
.panel-heading h2 { margin: 3px 0 0; font-size: 15px; }
.panel-heading > small { color: var(--muted); font-size: 10px; }
.panel-heading > svg { width: 18px; color: var(--cyan); }
.text-button { display: flex; align-items: center; gap: 4px; padding: 0; border: 0; color: var(--muted); background: transparent; cursor: pointer; font-size: 11px; }
.text-button svg { width: 13px; }

.category-tabs { display: grid; grid-template-columns: repeat(3, 1fr); gap: 4px; margin-bottom: 12px; padding: 4px; border: 1px solid var(--line); border-radius: 8px; background: #06142b; }
.category-tabs button { position: relative; padding: 9px 2px; border: 0; color: var(--muted); background: transparent; cursor: pointer; font-size: 11px; }
.category-tabs button::after { display: none; }
.category-tabs button.is-active { color: #061523; border-radius: 6px; background: var(--cyan); font-weight: 700; }
.category-tabs button.is-active span { color: rgba(6, 21, 35, 0.58); }
.category-tabs button span { margin-left: 4px; color: #58769a; font-size: 9px; }
.fault-cards { display: grid; flex: 1; grid-auto-rows: minmax(0, 1fr); gap: 8px; }
.fault-card { position: relative; display: flex; align-items: flex-start; gap: 10px; width: 100%; padding: 11px; text-align: left; border: 1px solid var(--line); border-radius: 8px; color: var(--text); background: #0a1c38; cursor: pointer; transition: border-color 160ms ease, background 160ms ease, transform 160ms ease; }
.fault-card:hover { transform: translateX(2px); border-color: rgba(72, 224, 255, 0.38); background: #0a2454; }
.fault-card.is-selected { border-color: var(--line-strong); background: #0d294d; box-shadow: inset 3px 0 0 var(--cyan), 0 8px 22px rgba(0, 0, 0, 0.16); }
.fault-card__icon { display: grid; place-items: center; width: 34px; height: 34px; border: 1px solid rgba(72, 224, 255, 0.24); color: var(--cyan); background: #092759; }
.fault-card__icon svg { width: 18px; }
.fault-card.is-amber .fault-card__icon { color: var(--amber); border-color: rgba(255, 189, 92, 0.25); }
.fault-card.is-violet .fault-card__icon { color: var(--violet); border-color: rgba(167, 152, 255, 0.25); }
.fault-card.is-red .fault-card__icon { color: var(--red); border-color: rgba(255, 111, 125, 0.25); }
.fault-card__content { display: grid; gap: 5px; flex: 1; }
.fault-card__content strong { font-size: 12px; }
.fault-card__content small { color: var(--muted); line-height: 1.5; font-size: 10px; }
.fault-card__check { position: absolute; top: 8px; right: 8px; width: 13px; color: var(--cyan); }
.library-note { display: flex; align-items: flex-start; gap: 7px; margin-top: 12px; padding: 10px; border-radius: 7px; color: #879fb9; background: #06152b; font-size: 10px; line-height: 1.5; }
.library-note svg { flex: 0 0 auto; width: 13px; color: var(--amber); }

.selected-fault-banner { display: flex; align-items: center; gap: 12px; padding: 12px; border: 1px solid rgba(72, 224, 255, 0.28); border-radius: 8px; background: #0b2649; }
.selected-fault-banner__icon { width: 42px; height: 42px; }
.selected-fault-banner__icon svg { width: 22px; }
.selected-fault-banner.is-amber .selected-fault-banner__icon { color: var(--amber); border-color: rgba(255, 189, 92, 0.35); }
.selected-fault-banner.is-violet .selected-fault-banner__icon { color: var(--violet); border-color: rgba(167, 152, 255, 0.35); }
.selected-fault-banner.is-red .selected-fault-banner__icon { color: var(--red); border-color: rgba(255, 111, 125, 0.35); }
.selected-fault-banner strong { display: block; margin-top: 3px; font-size: 13px; }
.selected-fault-banner p { margin: 4px 0 0; color: var(--muted); font-size: 10px; line-height: 1.45; }
.config-section { min-width: 0; margin: 13px 0 0; padding: 11px 0 0; border: 0; border-top: 1px solid var(--line); }
.config-section legend { padding: 0 10px 0 0; color: #b9d4ed; font-size: 10px; font-weight: 700; }
.config-section legend span { display: inline-grid; place-items: center; width: 20px; height: 18px; margin-right: 5px; color: var(--cyan); background: #0b2b5e; }
.station-options { display: grid; gap: 8px; }
.station-topology { position: relative; height: 286px; overflow: hidden; border: 1px solid rgba(72, 224, 255, 0.18); border-radius: 8px; background: radial-gradient(circle at center, rgba(26, 103, 167, 0.38), transparent 35%), #071a37; }
.station-topology::before,
.station-topology::after { position: absolute; top: 50%; left: 50%; width: 78%; height: 1px; background: linear-gradient(90deg, transparent, rgba(72, 224, 255, 0.48), transparent); content: ''; transform: translate(-50%, -50%); }
.station-topology::after { transform: translate(-50%, -50%) rotate(48deg); }
.station-topology__ring { position: absolute; top: 50%; left: 50%; border: 1px dashed rgba(72, 224, 255, 0.28); border-radius: 50%; transform: translate(-50%, -50%); }
.station-topology__ring--outer { width: 70%; height: 76%; }
.station-topology__ring--inner { width: 38%; height: 42%; border-style: solid; border-color: rgba(92, 224, 178, 0.24); }
.station-topology__core { position: absolute; z-index: 2; top: 50%; left: 50%; display: grid; place-items: center; width: 86px; height: 86px; border: 1px solid rgba(92, 224, 178, 0.68); border-radius: 50%; color: var(--text); background: rgba(7, 49, 91, 0.94); box-shadow: 0 0 0 9px rgba(72, 224, 255, 0.06), 0 0 28px rgba(72, 224, 255, 0.18); transform: translate(-50%, -50%); }
.station-topology__core span { color: var(--cyan); font-size: 8px; font-weight: 700; }
.station-topology__core strong { color: var(--teal); font-size: 24px; line-height: 0.85; }
.station-topology__core small { color: var(--muted); font-size: 8px; }
.station-option { position: absolute; z-index: 3; top: var(--station-y); left: var(--station-x); display: grid; grid-template-columns: 35px minmax(0, 1fr); align-items: center; gap: 7px; width: clamp(104px, 16vw, 132px); min-height: 52px; padding: 6px; border: 1px solid rgba(72, 224, 255, 0.26); border-radius: 7px; color: var(--text); background: rgba(8, 32, 66, 0.94); cursor: pointer; text-align: left; transform: translate(-50%, -50%); transition: border-color 160ms ease, background 160ms ease, box-shadow 160ms ease, opacity 160ms ease; }
.station-option:hover { border-color: rgba(72, 224, 255, 0.7); background: #0c315e; }
.station-option.is-selected { border-color: var(--teal); box-shadow: 0 0 0 2px rgba(92, 224, 178, 0.15), 0 0 16px rgba(72, 224, 255, 0.15); }
.station-option.is-incompatible { opacity: 0.5; filter: saturate(0.55); }
.station-option.is-incompatible:hover { border-color: var(--amber); background: #2c2943; }
.station-option.is-offline { opacity: 0.4; }
.station-option__visual { position: relative; display: grid; place-items: center; width: 34px; height: 34px; border: 1px solid rgba(72, 224, 255, 0.34); border-radius: 6px; background: #0b2c5d; box-shadow: inset 0 -7px 0 rgba(4, 24, 60, 0.42); }
.station-option__visual::after { position: absolute; right: -2px; bottom: -2px; left: 5px; height: 4px; border-right: 1px solid rgba(72, 224, 255, 0.26); border-bottom: 1px solid rgba(72, 224, 255, 0.26); transform: skewX(-38deg); content: ''; }
.station-option__icon { display: grid; place-items: center; width: 22px; height: 22px; color: var(--cyan); }
.station-option__icon svg { width: 19px; height: 19px; }
.station-option__visual i { position: absolute; bottom: 3px; width: 20px; height: 1px; background: rgba(92, 224, 178, 0.72); box-shadow: 0 0 6px rgba(92, 224, 178, 0.55); }
.station-option__content { min-width: 0; }
.station-option__meta { display: flex; align-items: center; justify-content: space-between; gap: 3px; }
.station-option__meta small { color: var(--cyan); font-size: 7px; font-weight: 700; }
.station-option__meta em { color: var(--teal); font-size: 7px; font-style: normal; }
.station-option strong { display: block; overflow: hidden; margin-top: 2px; color: var(--text); font-size: 10px; text-overflow: ellipsis; white-space: nowrap; }
.station-option__content > small { display: block; margin-top: 2px; color: var(--muted); font-size: 7px; white-space: nowrap; }
.station-option__content b { color: #c6e4fb; font-weight: 600; }
.station-option__check { position: absolute; top: 3px; right: 3px; width: 11px; color: var(--teal); }
.station-selection-notice { display: flex; align-items: center; gap: 7px; padding: 8px 10px; border: 1px solid rgba(255, 189, 92, 0.34); border-radius: 6px; color: #f9c96c; background: rgba(246, 185, 87, 0.08); font-size: 9px; }
.station-selection-notice svg { width: 13px; flex: 0 0 auto; }
.station-runtime-panel { margin-top: 12px; padding: 12px; }
.runtime-station-card { display: grid; gap: 5px; margin-top: 8px; padding: 9px; border: 1px solid var(--line); border-radius: 6px; background: #081a34; font-size: 9px; }
.runtime-station-card strong { color: var(--text); font-size: 10px; }
.runtime-station-card strong small, .runtime-station-card span { color: var(--muted); font-weight: 400; }
.parameter-grid label.is-invalid input { border-color: var(--red); }
.parameter-grid label > small { display: block; margin-top: 3px; color: var(--red); font-size: 8px; }
.severity-control { display: grid; grid-template-columns: 1fr 94px; column-gap: 12px; align-items: center; padding: 9px 11px; border-radius: 7px; background: #081a34; }
.severity-control__label { grid-column: 1; display: flex; justify-content: space-between; color: var(--muted); font-size: 8px; }
.severity-control input { grid-column: 1; width: 100%; height: 4px; margin: 8px 0 2px; accent-color: var(--amber); }
.severity-control__value { grid-column: 2; grid-row: 1 / span 2; display: grid; place-items: center; height: 48px; border-left: 1px solid var(--line); }
.severity-control__value strong { color: var(--amber); font-size: 18px; }
.severity-control__value span { color: var(--muted); font-size: 8px; }
.parameter-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 8px; }
.parameter-grid label > span,
.remark-field > span { display: block; margin-bottom: 5px; color: var(--muted); font-size: 9px; }
.number-input { display: flex; align-items: center; height: 34px; border: 1px solid var(--line); border-radius: 6px; background: #06152b; overflow: hidden; }
.number-input input { min-width: 0; flex: 1; width: 100%; padding: 0 9px; border: 0; outline: 0; color: var(--text); background: transparent; font-size: 11px; }
.number-input em { padding: 0 9px; color: var(--muted); border-left: 1px solid var(--line); font-size: 9px; font-style: normal; }
.switch-control { display: flex; align-items: center; justify-content: space-between; width: 100%; height: 34px; padding: 0 9px; color: var(--muted); border: 1px solid var(--line); border-radius: 6px; background: #06152b; cursor: pointer; }
.switch-control i { position: relative; width: 28px; height: 14px; border-radius: 8px; background: #314b69; }
.switch-control i::after { position: absolute; top: 2px; left: 2px; width: 10px; height: 10px; border-radius: 50%; content: ''; background: #91a8be; transition: transform 160ms ease; }
.switch-control.is-enabled { color: var(--teal); }
.switch-control.is-enabled i { background: rgba(85, 230, 181, 0.32); }
.switch-control.is-enabled i::after { background: var(--teal); transform: translateX(14px); }
.switch-control strong { font-size: 9px; }
.remark-field { display: block; margin-top: 8px; }
.remark-field input { width: 100%; height: 34px; padding: 0 9px; border: 1px solid var(--line); border-radius: 6px; outline: 0; color: var(--text); background: #06152b; font-size: 10px; }
.remark-field input::placeholder { color: #4d6c8d; }

.fault-review { display: grid; grid-template-rows: auto minmax(0, 1fr); align-content: stretch; min-width: 0; }
.fault-review .station-runtime-panel { height: 100%; }
.action-panel { align-self: start; }
.progress-track { height: 4px; margin-top: 10px; background: #18345a; overflow: hidden; }
.review-actions { display: grid; grid-template-columns: 0.8fr 1.2fr; gap: 8px; margin-top: 10px; }
.review-actions button { height: 38px; border-radius: 7px; cursor: pointer; font-size: 11px; font-weight: 700; }
.review-actions button:disabled { cursor: not-allowed; opacity: 0.45; }
.secondary-action { border: 1px solid rgba(72, 224, 255, 0.35); color: var(--cyan); background: #092653; }
.primary-action { display: flex; align-items: center; justify-content: center; gap: 6px; border: 1px solid var(--cyan); color: #03132f; background: var(--cyan); }
.primary-action svg { width: 14px; }
.fault-workflow { margin-top: 16px; padding-top: 13px; border-top: 1px solid var(--line); }
.fault-workflow__heading { display: flex; align-items: center; justify-content: space-between; gap: 10px; margin-bottom: 11px; }
.fault-workflow__heading span { color: var(--cyan); font-size: 8px; font-weight: 700; letter-spacing: 0.15em; }
.fault-workflow__heading strong { color: var(--teal); font-size: 10px; }
.fault-workflow__chain { display: grid; }
.fault-workflow__stage { display: grid; grid-template-columns: 24px 1fr; align-items: center; gap: 8px; min-height: 54px; padding: 6px 8px; border: 1px solid var(--line); border-radius: 6px; background: #081a34; }
.fault-workflow__index { display: grid; place-items: center; width: 24px; height: 24px; color: #6583a6; border: 1px solid rgba(127, 174, 221, 0.22); background: #0a2452; font-size: 8px; }
.fault-workflow__stage strong { display: block; color: var(--muted); font-size: 10px; }
.fault-workflow__stage-title { display: flex; align-items: center; gap: 5px; }
.fault-workflow__stage-title span { padding: 1px 4px; border: 1px solid currentColor; border-radius: 3px; font-size: 7px; line-height: 1.2; }
.fault-workflow__stage-title span.is-actual { color: var(--teal); }
.fault-workflow__stage-title span.is-expected { color: var(--amber); }
.fault-workflow__stage small { display: block; margin-top: 2px; color: #58769a; font-size: 8px; line-height: 1.35; }
.fault-workflow__stage em { display: block; margin-top: 2px; color: #7595b5; font-size: 8px; font-style: normal; }
.fault-workflow__stage.is-completed { border-color: rgba(92, 224, 178, 0.25); }
.fault-workflow__stage.is-completed .fault-workflow__index { color: var(--teal); border-color: rgba(92, 224, 178, 0.34); }
.fault-workflow__stage.is-completed strong { color: #b9d9ce; }
.fault-workflow__stage.is-expected { border-color: rgba(255, 189, 92, 0.72); background: rgba(255, 189, 92, 0.08); box-shadow: inset 3px 0 0 rgba(255, 189, 92, 0.88); }
.fault-workflow__stage.is-expected .fault-workflow__index { color: #2a1b00; border-color: var(--amber); background: var(--amber); }
.fault-workflow__stage.is-expected strong { color: var(--amber); }
.fault-workflow__stage.is-active { border-color: var(--teal); background: rgba(92, 224, 178, 0.08); box-shadow: 0 0 14px rgba(92, 224, 178, 0.12); }
.fault-workflow__stage.is-active .fault-workflow__index { color: #052033; border-color: var(--teal); background: var(--teal); }
.fault-workflow__stage.is-active strong { color: var(--teal); }
.fault-workflow__stage.is-active small { color: #9fcdbd; }
.fault-workflow__stage.is-active em { color: #d0eee1; }
.fault-workflow__link { display: block; width: 1px; height: 7px; margin: 0 0 0 19px; background: var(--line); }
.fault-workflow__stage.is-completed + .fault-workflow__link { background: rgba(92, 224, 178, 0.44); }
.queue-list { display: grid; gap: 6px; }
.queue-panel-actions { display: flex; align-items: center; gap: 8px; }
.queue-panel-actions > small { color: var(--muted); font-size: 10px; }
.queue-panel-actions > button { display: flex; align-items: center; gap: 4px; height: 27px; padding: 0 8px; border: 1px solid rgba(72, 224, 255, 0.35); border-radius: 5px; color: var(--cyan); background: #092653; cursor: pointer; font-size: 9px; font-weight: 700; }
.queue-panel-actions > button:hover { border-color: var(--cyan); background: #0b315f; }
.queue-panel-actions > button:disabled { cursor: not-allowed; opacity: 0.45; }
.queue-panel-actions > button svg { width: 12px; }
.queue-item { display: grid; grid-template-columns: 26px 1fr auto; align-items: center; gap: 8px; padding: 8px 7px; border: 1px solid var(--line); border-radius: 7px; background: #081a34; }
.queue-item__order { display: grid; place-items: center; width: 26px; height: 26px; color: var(--cyan); background: #0a2a5c; font-size: 9px; }
.queue-item strong { display: block; font-size: 10px; }
.queue-item div span { display: block; margin-top: 3px; color: var(--muted); font-size: 9px; }
.queue-item__actions { display: flex; align-items: center; gap: 3px; }
.queue-item__actions button { display: grid; place-items: center; width: 24px; height: 24px; padding: 0; border: 0; color: #8da9c4; background: transparent; cursor: pointer; }
.queue-item__actions button:hover { color: var(--cyan); background: #0a2a5c; }
.queue-item__actions button:disabled { color: #3d5c7d; cursor: not-allowed; }
.queue-item__actions button:last-child:hover { color: var(--red); }
.queue-item__actions svg { width: 13px; }
.completed-experiment-list { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 7px; }
.completed-experiment-item { display: grid; grid-template-columns: 28px minmax(0, 1fr) auto 24px; align-items: center; gap: 8px; min-width: 0; padding: 8px; border: 1px solid rgba(92, 224, 178, 0.22); border-radius: 7px; background: rgba(92, 224, 178, 0.05); }
.completed-experiment-item.is-clickable { cursor: pointer; }
.completed-experiment-item.is-clickable:hover,
.completed-experiment-item.is-clickable:focus-visible { border-color: var(--cyan); outline: none; background: rgba(72, 224, 255, 0.08); }
.completed-experiment-item__icon { display: grid; place-items: center; width: 28px; height: 28px; color: var(--teal); background: rgba(92, 224, 178, 0.1); }
.completed-experiment-item__icon svg { width: 15px; }
.completed-experiment-item strong { display: block; overflow: hidden; font-size: 10px; text-overflow: ellipsis; white-space: nowrap; }
.completed-experiment-item div span { display: block; margin-top: 3px; color: var(--muted); font-size: 9px; }
.completed-experiment-item em { color: var(--teal); font-size: 8px; font-style: normal; }
.completed-experiment-item > button { display: grid; place-items: center; width: 24px; height: 24px; padding: 0; border: 0; color: var(--muted); background: transparent; cursor: pointer; }
.completed-experiment-item > button:hover { color: var(--cyan); background: #0a2a5c; }
.completed-experiment-item > button svg { width: 13px; }
.queue-empty { margin: 22px 0; color: var(--muted); text-align: center; font-size: 9px; }

.power-curve-panel { padding: 0; overflow: hidden; }
.power-curve-panel__header { display: flex; align-items: center; justify-content: space-between; gap: 18px; min-height: 58px; padding: 10px 14px; border-bottom: 1px solid var(--line); background: rgba(3, 18, 62, 0.72); }
.power-curve-panel__header > div:first-child { min-width: 0; }
.power-curve-panel__header span { color: var(--cyan); font-size: 8px; font-weight: 700; letter-spacing: 0.12em; }
.power-curve-panel__header h2 { display: inline; margin: 0 10px 0 0; font-size: 16px; }
.power-curve-panel__header small { color: var(--muted); font-size: 8px; }
.power-curve-panel__chart { position: relative; min-height: 360px; padding: 0 8px; background: linear-gradient(180deg, rgba(2, 17, 48, 0.84), rgba(2, 12, 37, 0.96)); }
.power-curve-panel__empty { display: grid; place-items: center; align-content: center; min-height: 360px; color: var(--muted); text-align: center; }
.power-curve-panel__empty svg { width: 28px; margin-bottom: 9px; color: var(--cyan); }
.power-curve-panel__empty strong { font-size: 12px; }
.power-curve-panel__empty span { margin-top: 5px; color: #58769a; font-size: 9px; }
.experiment-phase-strip { display: grid; grid-template-columns: 68px repeat(5, minmax(0, 1fr)); gap: 4px; align-items: stretch; padding: 8px 14px 12px; border-top: 1px solid var(--line); background: #031438; }
.experiment-phase-strip > strong { display: flex; align-items: center; color: #d6eaff; font-size: 10px; }
.experiment-phase-strip article { position: relative; display: grid; place-items: center; min-height: 49px; padding: 7px 9px; overflow: hidden; border: 1px solid var(--line); border-radius: 5px; background: #09204a; text-align: center; }
.experiment-phase-strip article::before { position: absolute; inset: 0 auto 0 0; width: 3px; content: ''; background: currentColor; }
.experiment-phase-strip article span { color: currentColor; font-size: 10px; font-weight: 700; letter-spacing: 0; }
.experiment-phase-strip article time { margin-top: 3px; color: #b1c6db; font-size: 8px; }
.experiment-phase-strip article.is-red { color: #ff6671; background: rgba(118, 25, 43, 0.4); }
.experiment-phase-strip article.is-amber { color: #ffb23f; background: rgba(111, 74, 17, 0.4); }
.experiment-phase-strip article.is-blue { color: #4d9eff; background: rgba(18, 67, 125, 0.42); }
.experiment-phase-strip article.is-green { color: #43df91; background: rgba(14, 91, 66, 0.42); }
.experiment-phase-strip article.is-slate { color: #b9d4ed; background: rgba(66, 83, 108, 0.42); }

.execution-dock { position: sticky; bottom: 10px; z-index: 5; display: grid; grid-template-columns: 230px minmax(260px, 1fr) auto 110px; align-items: center; gap: 18px; padding: 10px 14px; border-color: rgba(72, 224, 255, 0.28); }
.execution-dock.is-active { border-color: rgba(255, 189, 92, 0.48); }
.execution-dock__identity { gap: 9px; }
.execution-dock__pulse { display: grid; place-items: center; width: 32px; height: 32px; color: #5f7e9f; background: #0a2452; }
.execution-dock.is-active .execution-dock__pulse { color: var(--amber); }
.execution-dock__pulse svg { width: 16px; }
.execution-dock__identity strong { display: block; margin-top: 2px; font-size: 10px; }
.execution-dock__progress > div:first-child { display: flex; justify-content: space-between; gap: 10px; color: var(--muted); font-size: 8px; }
.execution-dock__progress strong { color: var(--cyan); }
.progress-track { margin-top: 6px; }
.progress-track i { display: block; height: 100%; background: var(--cyan); transition: width 300ms ease; }
.execution-dock__steps { display: flex; align-items: center; gap: 10px; }
.execution-dock__steps span { display: flex; align-items: center; gap: 3px; color: #536f8d; font-size: 8px; }
.execution-dock__steps svg { width: 11px; }
.execution-dock__steps span.is-done { color: var(--teal); }
.execution-dock__steps span.is-current { color: var(--amber); }
.execution-dock > button { height: 30px; border: 1px solid rgba(255, 111, 125, 0.35); color: var(--red); background: rgba(255, 111, 125, 0.07); cursor: pointer; font-size: 9px; }
.execution-dock > button:disabled { color: #58718d; border-color: var(--line); cursor: not-allowed; }
.execution-dock > button.is-completed,
.execution-dock > button.is-completed:disabled { border-color: rgba(92, 224, 178, 0.5); color: var(--teal); background: rgba(92, 224, 178, 0.12); }

.experiment-name-dialog { position: fixed; z-index: 20; inset: 0; display: grid; place-items: center; padding: 16px; background: rgba(2, 9, 24, 0.76); }
.experiment-name-dialog__content { width: min(100%, 360px); padding: 20px; border: 1px solid rgba(72, 224, 255, 0.42); border-radius: 8px; background: #091d3a; box-shadow: 0 18px 50px rgba(0, 0, 0, 0.42); }
.experiment-name-dialog__content > span { color: var(--cyan); font-size: 9px; font-weight: 700; letter-spacing: 0.16em; }
.experiment-name-dialog h2 { margin: 6px 0 16px; font-size: 17px; }
.experiment-name-dialog label > span { display: block; margin-bottom: 6px; color: var(--muted); font-size: 10px; }
.experiment-name-dialog input { width: 100%; height: 38px; padding: 0 10px; border: 1px solid var(--line); border-radius: 6px; outline: 0; color: var(--text); background: #06152b; font-size: 12px; }
.experiment-name-dialog input:focus { border-color: var(--cyan); }
.experiment-name-dialog .is-error { margin: 7px 0 0; color: var(--red); font-size: 10px; }
.experiment-name-dialog__actions { display: flex; justify-content: flex-end; gap: 8px; margin-top: 18px; }
.experiment-name-dialog__actions button { height: 34px; padding: 0 12px; border: 1px solid var(--line); border-radius: 6px; color: var(--muted); background: transparent; cursor: pointer; font-size: 10px; }
.experiment-name-dialog__actions button[type='submit'] { border-color: var(--cyan); color: #03132f; background: var(--cyan); font-weight: 700; }
.experiment-name-dialog__actions button.is-danger { border-color: rgba(255, 111, 125, 0.42); color: var(--red); }
.experiment-name-dialog__actions button:disabled { cursor: not-allowed; opacity: 0.45; }

@media (max-width: 1180px) {
  .workbench-grid { grid-template-columns: minmax(235px, 0.72fr) minmax(0, 1.45fr); }
  .fault-review { grid-column: 1 / -1; }
  .summary-fault-list { grid-template-columns: repeat(3, minmax(0, 1fr)); }
  .execution-dock { grid-template-columns: 210px 1fr 110px; }
  .execution-dock__steps { display: none; }
  .experiment-phase-strip { grid-template-columns: repeat(5, minmax(130px, 1fr)); overflow-x: auto; }
  .experiment-phase-strip > strong { display: none; }
}

@media (max-width: 720px) {
  .fault-workbench { padding: 10px; }
  .workbench-header { align-items: flex-start; flex-direction: column; }
  .workbench-header__status { width: 100%; padding: 10px 0 0; border-top: 1px solid var(--line); border-left: 0; }
  .power-curve-panel__header { align-items: flex-start; flex-direction: column; }
  .workbench-grid { grid-template-columns: 1fr; }
  .fault-review { grid-column: auto; }
  .summary-fault-list { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .completed-experiment-list { grid-template-columns: 1fr; }
  .station-topology { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); height: auto; gap: 7px; padding: 8px; background: #071a37; }
  .station-topology::before,
  .station-topology::after,
  .station-topology__ring,
  .station-topology__core { display: none; }
  .station-option { position: relative; top: auto; left: auto; width: 100%; transform: none; }
  .execution-dock { grid-template-columns: 1fr auto; }
  .execution-dock__progress { grid-column: 1 / -1; grid-row: 2; }
}
</style>
