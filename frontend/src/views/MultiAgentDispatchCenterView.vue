<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import type { EChartsOption } from 'echarts'

import {
  analyzeMarketDispatch,
  fetchAgentDispatchOverview,
  fetchExperimentScenarios,
  fetchLatestMarketSignal,
  fetchPowerFlowTrend,
  fetchStationDevicePowerTrend,
  startAgentDispatchExperiment,
  stopAgentDispatchExperiment
} from '@/api/systemApi'
import EChartPanel from '@/components/charts/EChartPanel.vue'
import AgentDispatchPowerCurve from '@/components/agent-dispatch/AgentDispatchPowerCurve.vue'
import { controlModeLabelMap, mockAgentDispatchKeyScenarios, mockAgentDispatchOverview } from '@/mock/systemData'
import {
  ControlMode,
  LinkState,
  type AgentDecisionSummaryRow,
  type AgentDispatchDemoMode,
  type AgentDispatchOverview,
  type DispatchMetricValue,
  type DispatchStrategyItem,
  type DispatchTopologyBalanceSummary,
  type DispatchTopologyNode,
  type ExperimentScenario,
  type MarketSignalSnapshot
} from '@/types/system'

type KpiCard = {
  title: string
  value: string
  unit: string
  rate: string
  rateLabel: string
  trend: 'up' | 'down' | 'flat'
  tone: 'blue' | 'green' | 'purple'
}

type MainCurveSample = {
  time: number
  generationPower: number | null
  consumptionPower: number | null
  netPower: number | null
}

type PowerBalanceSample = {
  time: number
  supply: number | null
  consumption: number | null
  gap: number | null
}

type CollaborationStep = {
  stepIndex: number
  stageName: string
  agentType: string
  agentName: string
  output: string
  status: string
  statusClass: string
  adopted: boolean
}

type DispatchOperationRow = {
  agentName: string
  action: string
  operation: string
  decision: string
}

type DecisionProgressItem = {
  label: string
  completed: boolean
}

const AUTO_REFRESH_INTERVAL_MS = 5000
const MOCK_TIMELINE_ADVANCE_MS = 4000
const MAIN_CURVE_WINDOW_MS = 5 * 60 * 1000
const MAIN_CURVE_FUTURE_RATIO = 0.35
const dataSource = 'api' as const

function clonePlainData<T>(value: T): T {
  return JSON.parse(JSON.stringify(value)) as T
}

const overview = ref<AgentDispatchOverview>(clonePlainData(mockAgentDispatchOverview))
const marketSignal = ref<MarketSignalSnapshot | null>(null)
const powerBalanceSummary = ref<DispatchTopologyBalanceSummary>(overview.value.resourceTopology.balanceSummary)
const storageSocTrend = ref<Array<{ timestamp: string; value: number }>>([])
const scenarios = ref<ExperimentScenario[]>(clonePlainData(mockAgentDispatchKeyScenarios))
const isLoading = ref(true)
const isExperimentBusy = ref(false)
const errorMessage = ref('')
const demoMode = ref<AgentDispatchDemoMode>('realtime')
const activeDispatchMode = ref('自动调度')
const selectedGoal = ref('收益最大化')
const mainCurveSamples = ref<MainCurveSample[]>([])
const powerBalanceSamples = ref<PowerBalanceSample[]>([])
const mainCurveUpdatedAt = ref(Date.now())

let refreshTimer: number | undefined
let mockTimelineTimer: number | undefined
let mockElapsedTimer: number | undefined
let mainCurveTimer: number | undefined
let requestPending = false
let powerBalanceRequestPending = false
let initialRunCreationAttempted = false

const dispatchModes = ['自动调度', '经济优先', '安全优先', '环保优先', '自定义策略']
const globalGoals = ['收益最大化', '削峰填谷 + 收益最优', '安全约束优先', '新能源消纳最大化']

const statusText = computed(() => ({
  ai: overview.value.systemStatus.aiEnabled
    ? overview.value.systemStatus.aiAvailable
      ? '正常'
      : '不可用'
    : '关闭',
  mode: controlModeLabelMap[overview.value.systemStatus.controlMode]
}))

const lastUpdatedAt = computed(() => formatDateTime(overview.value.systemStatus.lastUpdatedAt))

const topologyNodes = computed(() => overview.value.resourceTopology.resourceNodes)
const resourceTracking = computed(() => overview.value.resourceTracking)
const trackingResources = computed(() => resourceTracking.value?.resources ?? [])

const generationPower = computed(() => {
  return sumNodes(['photovoltaic', 'wind', 'thermal'])
})

const loadPower = computed(() => {
  return sumNodes(['load', 'charging'])
})

const storagePower = computed(() => {
  return Math.abs(topologyNodes.value.find((node) => node.nodeType === 'storage')?.activePower ?? 0)
})

const todayElapsedHours = computed(() => {
  const updatedAt = new Date(overview.value.systemStatus.lastUpdatedAt)
  if (Number.isNaN(updatedAt.getTime())) {
    return null
  }
  const dayStart = new Date(updatedAt)
  dayStart.setHours(0, 0, 0, 0)
  return (updatedAt.getTime() - dayStart.getTime()) / 3_600_000
})

const realTimePrice = computed(() => numberOrNull(marketSignal.value?.realTimePrice))

const dayAheadPrice = computed(() => numberOrNull(marketSignal.value?.dayAheadPrice))

const estimatedLoadEnergy = computed(() => calculateTodayEnergy(loadPower.value, todayElapsedHours.value))

const estimatedGenerationEnergy = computed(() => calculateTodayEnergy(generationPower.value, todayElapsedHours.value))

const averageSoc = computed(() => {
  const storageNode = topologyNodes.value.find((node) => node.nodeType === 'storage')
  const matched = storageNode?.statusText.match(/SOC\s*:\s*(\d+(\.\d+)?)/i)
  return matched ? Number(matched[1]) : null
})

const kpiCards = computed<KpiCard[]>(() => {
  const priceRate = calculateChangeRate(realTimePrice.value, dayAheadPrice.value)
  const loadComparison = comparisonRate('peak-load')
  const generationComparison = comparisonRate('curtailment')
  const socComparison = comparisonRate('soc-swing')

  return [
    {
      title: '实时电价',
      value: formatNumber(realTimePrice.value, 2),
      unit: '元/kWh',
      rate: formatRate(priceRate),
      rateLabel: '相对日前电价',
      trend: resolveTrend(priceRate),
      tone: 'blue'
    },
    {
      title: '用电量(今日预估)',
      value: formatNumber(estimatedLoadEnergy.value, 1),
      unit: 'MWh',
      rate: formatRate(loadComparison.rate),
      rateLabel: loadComparison.label,
      trend: loadComparison.trend,
      tone: 'blue'
    },
    {
      title: '发电量(今日预估)',
      value: formatNumber(estimatedGenerationEnergy.value, 1),
      unit: 'MWh',
      rate: formatRate(generationComparison.rate),
      rateLabel: generationComparison.label,
      trend: generationComparison.trend,
      tone: 'green'
    },
    {
      title: '用电功率(实时)',
      value: formatNumber(loadPower.value / 1_000, 2),
      unit: 'MW',
      rate: formatRate(loadComparison.rate),
      rateLabel: loadComparison.label,
      trend: loadComparison.trend,
      tone: 'purple'
    },
    {
      title: '发电功率(实时)',
      value: formatNumber(generationPower.value / 1_000, 2),
      unit: 'MW',
      rate: formatRate(generationComparison.rate),
      rateLabel: generationComparison.label,
      trend: generationComparison.trend,
      tone: 'purple'
    },
    {
      title: '储能SOC(平均)',
      value: formatNumber(averageSoc.value, 1),
      unit: '%',
      rate: formatRate(socComparison.rate),
      rateLabel: socComparison.label,
      trend: socComparison.trend,
      tone: 'purple'
    }
  ]
})

async function loadMainCurveSamples(): Promise<void> {
  const now = Date.now()
  const from = new Date(now - MAIN_CURVE_WINDOW_MS)
  const to = new Date(now)
  const points = await fetchPowerFlowTrend(from.toISOString(), to.toISOString(), 240)
  const realtimeSamples = points
    .map((point) => ({
      time: new Date(point.timestamp).getTime(),
      generationPower: round(point.generationPower, 1),
      consumptionPower: round(point.consumptionPower, 1),
      netPower: round(point.generationPower - point.consumptionPower, 1)
    }))
    .filter((point) => Number.isFinite(point.time))
    .sort((left, right) => left.time - right.time)
  const samplesWithGaps: MainCurveSample[] = []
  for (const sample of realtimeSamples) {
    const previousSample = samplesWithGaps.at(-1)
    if (previousSample && sample.time - previousSample.time > 90_000) {
      samplesWithGaps.push({
        time: previousSample.time + 30_000,
        generationPower: null,
        consumptionPower: null,
        netPower: null
      })
    }
    samplesWithGaps.push(sample)
  }
  mainCurveSamples.value = samplesWithGaps
  mainCurveUpdatedAt.value = now
}

function createPowerBalanceSample(time: number): PowerBalanceSample {
  const balanceSummary = powerBalanceSummary.value
  const supply = toMegawatt(balanceSummary.totalSupplyPower)
  const consumption = toMegawatt(balanceSummary.totalConsumePower)
  const netBalancePower = toMegawatt(balanceSummary.netBalancePower)
  return {
    time,
    supply,
    consumption,
    gap: netBalancePower === null ? null : round(-Math.abs(netBalancePower), 4)
  }
}

function initializePowerBalanceSamples(): void {
  powerBalanceSamples.value = [createPowerBalanceSample(Date.now())]
}

function appendPowerBalanceSample(): void {
  const now = Date.now()
  const cutoffTime = now - MAIN_CURVE_WINDOW_MS
  powerBalanceSamples.value = [
    ...powerBalanceSamples.value.filter((sample) => sample.time >= cutoffTime),
    createPowerBalanceSample(now)
  ]
}

const mainCurveAxisMaximum = computed(() => mainCurveUpdatedAt.value
  + MAIN_CURVE_WINDOW_MS * MAIN_CURVE_FUTURE_RATIO)

const mainCurveOption = computed<EChartsOption>(() => ({
  animationDurationUpdate: 350,
  color: ['#45dff5', '#8b5cf6', '#62dc70'],
  tooltip: {
    trigger: 'axis',
    backgroundColor: 'rgba(4, 22, 52, 0.96)',
    borderColor: 'rgba(71, 159, 255, 0.45)',
    textStyle: { color: '#dceaff' },
    valueFormatter: (value) => `${Number(value).toFixed(1)}`
  },
  legend: {
    top: 4,
    left: 'center',
    itemWidth: 16,
    itemHeight: 7,
    textStyle: { color: '#b9c9e7', fontSize: 12 },
    data: ['总供给功率', '总用电功率', '净供电功率']
  },
  grid: { top: 54, left: 45, right: 46, bottom: 44 },
  xAxis: {
    type: 'time',
    min: mainCurveUpdatedAt.value - MAIN_CURVE_WINDOW_MS,
    max: mainCurveAxisMaximum.value,
    axisLabel: {
      color: '#a8bbd8',
      formatter: (value: number) => new Intl.DateTimeFormat('zh-CN', {
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit',
        hour12: false
      }).format(new Date(value))
    },
    axisTick: { show: false },
    axisLine: { lineStyle: { color: 'rgba(83, 132, 190, 0.45)' } }
  },
  yAxis: {
    type: 'value',
    scale: true,
    name: '功率（kW）',
    nameTextStyle: { color: '#b4c7df', align: 'left' },
    axisLabel: { color: '#a8bbd8' },
    splitLine: { lineStyle: { color: 'rgba(62, 104, 158, 0.28)' } }
  },
  graphic: [{
    type: 'text',
    right: 52,
    top: 165,
    silent: true,
    style: {
      text: '等待实时数据产生\n当前周期尚未结束',
      fill: 'rgba(168, 187, 216, 0.62)',
      font: '12px sans-serif',
      lineHeight: 20,
      textAlign: 'center'
    }
  }],
  series: [
    buildRollingLine('总供给功率', 'generationPower', '#45dff5'),
    buildRollingLine('总用电功率', 'consumptionPower', '#8b5cf6'),
    buildRollingLine('净供电功率', 'netPower', '#62dc70')
  ]
}))

const resourcePieOption = computed<EChartsOption>(() => {
  const pieData = [
    { name: '光伏发电', value: nodeValue('photovoltaic', 0) },
    { name: '风电发电', value: nodeValue('wind', 0) },
    { name: '火力发电', value: nodeValue('thermal', 0) },
    { name: '储能放电', value: storagePower.value },
    { name: '市电购电', value: nodeValue('grid', 0) },
    { name: '可控负荷', value: nodeValue('load', 0) }
  ]
  return {
    color: ['#2d8cff', '#50d66b', '#6870ff', '#9ca3af', '#e85858'],
    tooltip: { trigger: 'item' },
    legend: {
      orient: 'vertical',
      right: 8,
      top: 'middle',
      textStyle: { color: '#c2d4ef', fontSize: 12 },
      formatter: (name: string) => {
        const total = pieData.reduce((sum, item) => sum + item.value, 0)
        const current = pieData.find((item) => item.name === name)?.value ?? 0
        return `${name}  ${formatNumber((current / total) * 100, 1)}%`
      }
    },
    series: [
      {
        type: 'pie',
        radius: ['44%', '74%'],
        center: ['28%', '52%'],
        label: { show: false },
        data: pieData
      }
    ]
  }
})

const storageSocLabels = computed(() => storageSocTrend.value.map((point) => formatTrendTime(point.timestamp)))

const storageSocValues = computed(() => storageSocTrend.value.map((point) => point.value))

const socOption = computed<EChartsOption>(() => ({
  color: ['#39e6cc'],
  tooltip: { trigger: 'axis' },
  grid: { top: 28, left: 40, right: 18, bottom: 30 },
  xAxis: {
    type: 'category',
    data: storageSocLabels.value,
    axisLabel: { color: '#a8bbd8' },
    axisTick: { show: false },
    axisLine: { lineStyle: { color: 'rgba(83, 132, 190, 0.45)' } }
  },
  yAxis: {
    type: 'value',
    min: 0,
    max: 100,
    axisLabel: { color: '#a8bbd8' },
    splitLine: { lineStyle: { color: 'rgba(62, 104, 158, 0.28)' } }
  },
  series: [
    {
      type: 'line',
      smooth: true,
      symbol: 'circle',
      symbolSize: 5,
      areaStyle: { color: 'rgba(42, 226, 208, 0.16)' },
      lineStyle: { width: 3 },
      data: storageSocValues.value
    }
  ]
}))

const powerBalanceOption = computed<EChartsOption>(() => ({
  color: ['#39dced', '#368dff', '#ff7d3b'],
  tooltip: {
    trigger: 'axis',
    valueFormatter: (value: string | number) => {
      const numericValue = Number(value)
      return Number.isFinite(numericValue) ? `${formatNumber(numericValue, 4)} MW` : '--'
    }
  },
  legend: {
    top: 2,
    right: 8,
    textStyle: { color: '#c2d4ef', fontSize: 11 },
    data: ['实时供给功率', '实时用电功率', '功率缺口']
  },
  grid: { top: 32, left: 40, right: 14, bottom: 30 },
  xAxis: {
    type: 'category',
    data: powerBalanceSamples.value.map((point) => formatBalanceTime(point.time)),
    axisLabel: {
      color: '#a8bbd8',
      formatter: (value: string, index: number) => index % 30 === 0 ? value : ''
    },
    axisTick: { show: false },
    axisLine: { lineStyle: { color: 'rgba(83, 132, 190, 0.45)' } }
  },
  yAxis: {
    type: 'value',
    axisLabel: { color: '#a8bbd8' },
    splitLine: { lineStyle: { color: 'rgba(62, 104, 158, 0.28)' } }
  },
  series: [
    { name: '实时供给功率', type: 'bar', barMaxWidth: 8, data: powerBalanceSamples.value.map((point) => point.supply) },
    { name: '实时用电功率', type: 'bar', barMaxWidth: 8, data: powerBalanceSamples.value.map((point) => point.consumption) },
    {
      name: '功率缺口',
      type: 'bar',
      barMaxWidth: 8,
      data: powerBalanceSamples.value.map((point) => point.gap)
    }
  ]
}))

const collaborationStageMap: Array<Pick<CollaborationStep, 'stageName' | 'agentType'>> = [
  { stageName: '市场输入', agentType: 'MARKET_INTERFACE' },
  { stageName: '预测生成', agentType: 'FORECAST' },
  { stageName: '环境感知', agentType: 'ENVIRONMENT_ASSESSMENT' },
  { stageName: '资源能力', agentType: 'RESOURCE_CAPABILITY' },
  { stageName: '市场调度', agentType: 'MARKET_DISPATCH' },
  { stageName: '通信质量', agentType: 'COMMUNICATION_QUALITY' },
  { stageName: '控制决策', agentType: 'CONTROL_DECISION' },
  { stageName: '安全裁决', agentType: 'SECURITY_AUDIT' }
]

const collaborationSteps = computed<CollaborationStep[]>(() => {
  return collaborationStageMap.map((stage, index) => {
    const row = overview.value.agentDecisionSummary.rows.find((item) => item.agentType === stage.agentType)
    const decisionLevel = overview.value.agentDecisionSummary.safetyDecisionLevel
    const adopted = Boolean(row?.adopted)
    const compatibleAvailable = Boolean(overview.value.agentDecisionSummary.decisionRunId) && Boolean(overview.value.agentDecisionSummary.safetyGatePassed)
    const available = Boolean(row?.available) || compatibleAvailable
    const status = resolveCollaborationStatus(available, adopted, decisionLevel, row?.finalDecision)
    const statusClass = resolveCollaborationStatusClass(available, adopted, decisionLevel, row?.finalDecision)

    return {
      stepIndex: index + 1,
      stageName: stage.stageName,
      agentType: stage.agentType,
      agentName: row?.agentName ?? defaultAgentName(stage.agentType),
      output: resolveStepOutput(row, stage.agentType),
      status,
      statusClass,
      adopted
    }
  })
})

const finalDecisionStatus = computed(() => {
  const decisionLevel = overview.value.agentDecisionSummary.safetyDecisionLevel
  if (decisionLevel === 'PASSED') {
    return 'PASSED 自动执行'
  }
  if (decisionLevel === 'DEGRADED') {
    return 'DEGRADED 降级执行'
  }
  if (decisionLevel === 'PASSED_WITH_FALLBACK') {
    return 'PASSED_WITH_FALLBACK 规则回退通过'
  }
  if (decisionLevel === 'REJECTED') {
    return 'REJECTED 拒绝执行'
  }
  return 'UNKNOWN 观察中'
})

const finalDecisionClass = computed(() => {
  const decisionLevel = overview.value.agentDecisionSummary.safetyDecisionLevel
  if (decisionLevel === 'DEGRADED') {
    return 'is-degraded'
  }
  if (decisionLevel === 'PASSED_WITH_FALLBACK') {
    return 'is-degraded'
  }
  if (decisionLevel === 'REJECTED') {
    return 'is-rejected'
  }
  if (decisionLevel === 'PASSED') {
    return 'is-passed'
  }
  return 'is-unknown'
})

const decisionExecutionMode = computed(() => {
  const decisionLevel = overview.value.agentDecisionSummary.safetyDecisionLevel
  if (decisionLevel === 'PASSED') {
    return '自动执行'
  }
  if (decisionLevel === 'PASSED_WITH_FALLBACK') {
    return '规则保障执行'
  }
  if (decisionLevel === 'DEGRADED') {
    return '受约束执行'
  }
  if (decisionLevel === 'REJECTED') {
    return '已阻断'
  }
  return '待确认'
})

const decisionHeadline = computed(() => {
  const decisionLevel = overview.value.agentDecisionSummary.safetyDecisionLevel
  if (decisionLevel === 'PASSED') {
    return '调度方案已通过安全校验，可自动下发执行'
  }
  if (decisionLevel === 'PASSED_WITH_FALLBACK') {
    return '关键安全约束已通过，已由规则保障执行'
  }
  if (decisionLevel === 'DEGRADED') {
    return '调度方案可受约束执行，系统将持续观察'
  }
  if (decisionLevel === 'REJECTED') {
    return '本轮不满足自动执行条件，系统已阻断下发'
  }
  return '正在等待最新调度裁决结果'
})

const decisionProgress = computed<DecisionProgressItem[]>(() => {
  const rows = overview.value.agentDecisionSummary.rows
  const stageCompleted = (agentType: string) => {
    const stageStatus = rows.find((row) => row.agentType === agentType)?.stageStatus
    return stageStatus === 'SUCCESS' || stageStatus === 'RULE_FALLBACK'
  }
  return [
    {
      label: '输入校验',
      completed: stageCompleted('MARKET_INTERFACE') && stageCompleted('FORECAST')
    },
    {
      label: '策略生成',
      completed: stageCompleted('MARKET_DISPATCH') && stageCompleted('CONTROL_DECISION')
    },
    {
      label: '安全通过',
      completed: Boolean(overview.value.agentDecisionSummary.safetyGatePassed)
    }
  ]
})

const logs = computed(() => {
  return overview.value.executionTimeline.steps
    .filter((step) => step.timestamp)
    .slice(-4)
    .reverse()
    .map((step) => ({
      time: formatTime(step.timestamp),
      text: `【${step.stepLabel}】${step.description}`
    }))
})

const dispatchOperationRows = computed<DispatchOperationRow[]>(() => {
  return [
    ...executionRowsFromGroup('负荷侧', overview.value.dispatchStrategy.loadSideActions.items),
    ...executionRowsFromGroup('电源侧', overview.value.dispatchStrategy.generationSideActions.items),
    ...executionRowsFromGroup('储能侧', overview.value.dispatchStrategy.storageActions.items),
    ...executionRowsFromGroup('电网侧', overview.value.dispatchStrategy.gridActions.items)
  ]
})

function resolveCollaborationStatus(available: boolean, adopted: boolean, decisionLevel: string, finalDecision?: string): string {
  if (!available) {
    return '待命'
  }
  if (finalDecision === '规则兼容输出') {
    return '规则兼容'
  }
  if (finalDecision === '规则回退') {
    return '规则回退'
  }
  if (finalDecision === '阶段阻断') {
    return '阶段阻断'
  }
  if (finalDecision === '阶段失败') {
    return '阶段失败'
  }
  if (finalDecision === '已完成，未采纳') {
    return '已完成，未采纳'
  }
  if (decisionLevel === 'REJECTED') {
    return '已完成，未采纳'
  }
  if (decisionLevel === 'DEGRADED') {
    return adopted ? '降级执行' : '约束覆盖'
  }
  if (decisionLevel === 'PASSED_WITH_FALLBACK') {
    return adopted ? '已执行' : '已归一'
  }
  return adopted ? '已执行' : '已归一'
}

function resolveCollaborationStatusClass(available: boolean, adopted: boolean, decisionLevel: string, finalDecision?: string): string {
  if (!available) {
    return 'is-idle'
  }
  if (finalDecision === '规则回退') {
    return 'is-degraded'
  }
  if (finalDecision === '阶段阻断' || finalDecision === '阶段失败') {
    return 'is-rejected'
  }
  if (finalDecision === '已完成，未采纳') {
    return 'is-observed'
  }
  if (decisionLevel === 'REJECTED') {
    return 'is-observed'
  }
  if (decisionLevel === 'DEGRADED') {
    return adopted ? 'is-degraded' : 'is-observed'
  }
  if (decisionLevel === 'PASSED_WITH_FALLBACK') {
    return adopted ? 'is-adopted' : 'is-observed'
  }
  return adopted ? 'is-adopted' : 'is-observed'
}

function normalizeSuggestedAction(action: string | undefined, agentType: string): string {
  if (!action || action === '暂无建议动作') {
    return defaultAgentAction(agentType)
  }
  return action
}

function resolveStepOutput(row: AgentDecisionSummaryRow | undefined, agentType: string): string {
  const executionSummary = row
    ? `阶段 ${formatStageStatus(row.stageStatus)} · 重试 ${row.retryCount ?? 0} 次${row.errorCategory && row.errorCategory !== 'NONE' ? ` · ${formatErrorCategory(row.errorCategory)}` : ''}`
    : ''
  if (row?.fallbackReason) {
    const continuation = row.stageStatus === 'RULE_FALLBACK' ? '；规则输出已进入后续链路' : ''
    return `${executionSummary}；${row.fallbackReason}${continuation}`
  }
  const operationDescription = agentOperationDescription(agentType, normalizeSuggestedAction(row?.suggestedAction, agentType))
  return executionSummary ? `${executionSummary}；${operationDescription}` : operationDescription
}

function formatErrorCategory(errorCategory: string): string {
  const errorCategoryMap: Record<string, string> = {
    PROVIDER_ENDPOINT_NOT_FOUND: '模型服务端点或模型配置不存在',
    AUTH_ERROR: '模型服务鉴权失败',
    RATE_LIMIT: '模型服务限流',
    PROVIDER_5XX: '模型服务异常',
    TIMEOUT: '模型服务响应超时',
    INVALID_SCHEMA: '模型输出格式不合法',
    INVALID_ACTION: '模型输出动作不在白名单内',
    STALE_INPUT: '输入数据已过期',
    CONSTRAINT_VIOLATION: '输入约束不满足'
  }
  return errorCategoryMap[errorCategory] ?? errorCategory
}

function formatStageStatus(stageStatus: string | undefined): string {
  const stageStatusMap: Record<string, string> = {
    SUCCESS: '成功',
    RETRYING: '重试中',
    RULE_FALLBACK: '规则回退',
    BLOCKED: '已阻断',
    FAILED: '失败',
    LEGACY: '历史记录'
  }
  return stageStatusMap[stageStatus ?? ''] ?? '未知'
}

function formatTrackingResourceType(resourceType: string): string {
  const resourceTypeMap: Record<string, string> = {
    PHOTOVOLTAIC: '光伏',
    photovoltaic: '光伏',
    WIND: '风电',
    wind: '风电',
    THERMAL: '火电',
    thermal: '火电',
    STORAGE: '储能',
    storage: '储能',
    GRID: '市电',
    grid: '市电',
    LOAD: '负荷',
    load: '负荷',
    CHARGING: '充电设施',
    charging: '充电设施'
  }
  return resourceTypeMap[resourceType] ?? resourceType
}

function formatTrackingCommandStatus(commandStatus: string): string {
  const commandStatusMap: Record<string, string> = {
    CREATED: '已创建',
    SENT: '已下发',
    ACCEPTED: '已接收',
    EXECUTING: '执行中',
    EXECUTED: '已执行',
    REJECTED: '已拒绝',
    TIMEOUT: '执行超时',
    FAILED: '执行失败'
  }
  return commandStatusMap[commandStatus] ?? commandStatus
}

function formatTrackingStatus(trackingStatus: string | null | undefined): string {
  const trackingStatusMap: Record<string, string> = {
    NO_PLAN: '暂无计划',
    NO_ACTIVE_PLAN: '暂无活动计划',
    PLAN_EXPIRED: '计划已过期',
    WAITING_ACK: '等待执行回执',
    WAITING_TELEMETRY: '等待遥测数据',
    TRACKING: '收敛跟踪中',
    TARGET_REACHED: '目标已达成',
    REOPTIMIZATION_REQUIRED: '需要重新优化',
    CONVERGENCE_TIMEOUT: '收敛超时',
    FAILED: '收敛失败'
  }
  if (!trackingStatus) {
    return '暂无计划'
  }
  return trackingStatusMap[trackingStatus] ?? trackingStatus
}

function formatTrackingLinkState(linkState: LinkState | null): string {
  const linkStateMap: Record<LinkState, string> = {
    [LinkState.S0]: '正常协同',
    [LinkState.S1]: '通信退化',
    [LinkState.S2]: '严重退化',
    [LinkState.S3]: '失联自治',
    [LinkState.S4]: '恢复同步'
  }
  return linkState ? linkStateMap[linkState] : '--'
}

function executionRowsFromGroup(groupName: string, items: DispatchStrategyItem[]): DispatchOperationRow[] {
  return items
    .filter((item) => item.targetDeltaPower !== null && item.status !== 'STANDBY')
    .map((item) => ({
      agentName: '最终执行',
      action: item.strategyCode,
      operation: `${groupName}${item.strategyName}：${formatNumber(Math.abs(item.targetDeltaPower ?? 0), 1)} ${item.unit}。${item.reason}`,
      decision: resolveExecutionDecision(item)
    }))
}

function resolveExecutionDecision(item: DispatchStrategyItem): string {
  if (item.status === 'COMPLETED') {
    return '已执行'
  }
  if (item.status === 'EXECUTING') {
    return '正在下发'
  }
  if (item.status === 'REJECTED') {
    return '安全门拒绝'
  }
  if (item.adoptedBySafetyGate) {
    return overview.value.agentDecisionSummary.safetyDecisionLevel === 'DEGRADED' ? '降级执行' : '准备下发'
  }
  return '观察记录'
}

function normalizeOperationDecision(decision: string | undefined): string {
  const decisionLevel = overview.value.agentDecisionSummary.safetyDecisionLevel
  if (!decision || decision === '暂无裁决' || decision === '兼容采纳') {
    return '规则兼容输出'
  }
  if (decision === '采纳') {
    return decisionLevel === 'DEGRADED' ? '降级执行' : '已执行'
  }
  if (decision === '待执行') {
    return decisionLevel === 'DEGRADED' ? '约束覆盖' : '已归一'
  }
  if (decision === '拒绝') {
    return '安全门拒绝'
  }
  return decision
}

function defaultAgentName(agentType: string): string {
  return collaborationStageMap.find((stage) => stage.agentType === agentType)?.stageName ?? agentType
}

function defaultAgentAction(agentType: string): string {
  return {
    MARKET_INTERFACE: 'VERIFY_MARKET_SIGNAL_VERSION',
    FORECAST: 'USE_P50_FORECAST',
    ENVIRONMENT_ASSESSMENT: 'APPLY_ENVIRONMENT_CORRECTION',
    RESOURCE_CAPABILITY: 'FREEZE_RESOURCE_CAPABILITY_BOUNDARY',
    MARKET_DISPATCH: 'GENERATE_MARKET_OPTIMAL_PLAN',
    COMMUNICATION_QUALITY: 'APPLY_S0_S4_CAPACITY_FACTOR',
    CONTROL_DECISION: 'ISSUE_SAFE_DISPATCH_DECISION',
    SECURITY_AUDIT: 'ALLOW_SAFE_ACTIONS'
  }[agentType] ?? 'RULE_COMPATIBLE_OUTPUT'
}

function agentOperationDescription(agentType: string, action: string): string {
  if (agentType === 'MARKET_INTERFACE') {
    return '校验日前/实时市场信号版本，锁定本轮电价和合同输入。'
  }
  if (agentType === 'FORECAST') {
    return '生成 P50 预测基线，输出负荷、新能源和价格预测输入。'
  }
  if (agentType === 'ENVIRONMENT_ASSESSMENT') {
    return '读取天气与传感器数据，修正光伏/风电可用能力并解释设备数据。'
  }
  if (agentType === 'RESOURCE_CAPABILITY') {
    return '冻结环境修正后的资源能力边界，防止按装机容量乐观调度。'
  }
  if (agentType === 'MARKET_DISPATCH') {
    return '根据价格、预测和能力边界生成市场最优调度计划。'
  }
  if (agentType === 'COMMUNICATION_QUALITY') {
    return '按 S0-S4 链路质量折减可控容量，限制弱信号远程控制风险。'
  }
  if (agentType === 'CONTROL_DECISION') {
    return '把通过校验的计划转换为可下发的安全控制决策。'
  }
  if (agentType === 'SECURITY_AUDIT') {
    return action.includes('BLOCK')
      ? '安全门拦截越界动作，阻止进入执行链路。'
      : '安全门校验通过，允许安全动作进入执行链路。'
  }
  return action
}

function createMockOverview(): AgentDispatchOverview {
  return clonePlainData(mockAgentDispatchOverview)
}

function updateMockOverview(updater: (draft: AgentDispatchOverview) => void): void {
  const draft = clonePlainData(overview.value)
  updater(draft)
  draft.systemStatus.lastUpdatedAt = new Date().toISOString()
  overview.value = draft
}

async function loadScenarios(): Promise<void> {
  if (dataSource !== 'api') {
    scenarios.value = clonePlainData(mockAgentDispatchKeyScenarios)
    return
  }
  scenarios.value = (await fetchExperimentScenarios()).filter((scenario) =>
    ['T1_LATENCY_JITTER', 'T2_RANDOM_PACKET_LOSS', 'T5_FULL_LOSS_AUTONOMY', 'T9_RECOVERY_SYNC'].includes(scenario.scenarioCode)
  )
}

async function loadStorageSocTrend(): Promise<void> {
  if (dataSource !== 'api') {
    storageSocTrend.value = []
    return
  }

  const storageStationId = topologyNodes.value.find((node) => node.nodeType === 'storage')?.stationId
  if (!storageStationId) {
    storageSocTrend.value = []
    return
  }
  const updatedAt = new Date(overview.value.systemStatus.lastUpdatedAt)
  const trendEnd = Number.isNaN(updatedAt.getTime()) ? new Date() : updatedAt
  const trendStart = new Date(trendEnd.getTime() - 24 * 60 * 60 * 1_000)
  const storageTrendResult = await Promise.allSettled([
    fetchStationDevicePowerTrend(storageStationId, 'storage-bms-01', trendStart.toISOString(), trendEnd.toISOString(), 120)
  ])
  const storageTrend = storageTrendResult[0]
  storageSocTrend.value = storageTrend.status === 'fulfilled'
    ? storageTrend.value.points
      .filter((point) => point.primaryValue !== null)
      .map((point) => ({ timestamp: point.timestamp, value: point.primaryValue as number }))
    : []
}

async function refreshPowerBalanceSample(): Promise<void> {
  if (powerBalanceRequestPending) {
    return
  }
  if (dataSource !== 'api') {
    powerBalanceSummary.value = overview.value.resourceTopology.balanceSummary
    appendPowerBalanceSample()
    return
  }

  powerBalanceRequestPending = true
  try {
    const latestOverview = await fetchAgentDispatchOverview()
    powerBalanceSummary.value = latestOverview.resourceTopology.balanceSummary
    appendPowerBalanceSample()
  } catch {
    return
  } finally {
    powerBalanceRequestPending = false
  }
}

async function loadOverview(force = false): Promise<void> {
  if (requestPending && !force) {
    return
  }
  requestPending = true
  isLoading.value = true
  errorMessage.value = ''
  try {
    if (dataSource === 'api') {
      const [initialOverview, latestMarketSignal] = await Promise.all([
        fetchAgentDispatchOverview(),
        fetchLatestMarketSignal().catch(() => null)
      ])
      const latestOverview = initialOverview.agentDecisionSummary.decisionRunId || initialRunCreationAttempted
        ? initialOverview
        : await createInitialAgentRun()
      overview.value = latestOverview
      powerBalanceSummary.value = latestOverview.resourceTopology.balanceSummary
      marketSignal.value = latestMarketSignal
      await loadStorageSocTrend()
    } else if (!force && overview.value.experimentStatus.running) {
      updateMockOverview((draft) => {
        if (draft.experimentStatus.startedAt) {
          draft.experimentStatus.elapsedSeconds = Math.max(
            0,
            Math.floor((Date.now() - new Date(draft.experimentStatus.startedAt).getTime()) / 1000)
          )
        }
      })
    } else {
      overview.value = createMockOverview()
      powerBalanceSummary.value = overview.value.resourceTopology.balanceSummary
    }
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '多智能体调度中心加载失败'
  } finally {
    requestPending = false
    isLoading.value = false
  }
}

async function createInitialAgentRun(): Promise<AgentDispatchOverview> {
  initialRunCreationAttempted = true
  await analyzeMarketDispatch({
    dispatchContextSnapshotId: null,
    dispatchPlanId: null,
    conservativePlan: false
  })
  return fetchAgentDispatchOverview()
}

function clearMockTimers(): void {
  if (mockTimelineTimer !== undefined) {
    window.clearInterval(mockTimelineTimer)
    mockTimelineTimer = undefined
  }
  if (mockElapsedTimer !== undefined) {
    window.clearInterval(mockElapsedTimer)
    mockElapsedTimer = undefined
  }
}

function startMockTimers(): void {
  clearMockTimers()
  mockElapsedTimer = window.setInterval(() => {
    updateMockOverview((draft) => {
      if (!draft.experimentStatus.running || !draft.experimentStatus.startedAt) {
        return
      }
      draft.experimentStatus.elapsedSeconds = Math.max(
        0,
        Math.floor((Date.now() - new Date(draft.experimentStatus.startedAt).getTime()) / 1000)
      )
    })
  }, 1000)

  mockTimelineTimer = window.setInterval(() => {
    updateMockOverview((draft) => {
      const currentIndex = draft.executionTimeline.steps.findIndex((step) => step.status === 'RUNNING')
      if (currentIndex === -1) {
        return
      }
      draft.executionTimeline.steps[currentIndex].status = 'SUCCEEDED'
      if (currentIndex < draft.executionTimeline.steps.length - 1) {
        draft.executionTimeline.steps[currentIndex + 1].status = 'RUNNING'
        draft.executionTimeline.steps[currentIndex + 1].timestamp = new Date().toISOString()
        draft.executionTimeline.currentStepCode = draft.executionTimeline.steps[currentIndex + 1].stepCode
      }
    })
  }, MOCK_TIMELINE_ADVANCE_MS)
}

function scenarioName(scenarioCode: string): string {
  return scenarios.value.find((scenario) => scenario.scenarioCode === scenarioCode)?.scenarioName ?? scenarioCode
}

function expectedLinkState(scenarioCode: string) {
  return scenarios.value.find((scenario) => scenario.scenarioCode === scenarioCode)?.expectedLinkState ?? null
}

function applyMockScenario(scenarioCode: string): void {
  const startedAt = new Date().toISOString()
  updateMockOverview((draft) => {
    draft.systemStatus.activeScenario = scenarioCode
    draft.experimentStatus.currentRunId = `mock-run-${scenarioCode.toLowerCase()}`
    draft.experimentStatus.currentScenarioCode = scenarioCode
    draft.experimentStatus.currentScenarioName = scenarioName(scenarioCode)
    draft.experimentStatus.running = true
    draft.experimentStatus.startedAt = startedAt
    draft.experimentStatus.elapsedSeconds = 0
    draft.experimentStatus.durationSeconds = scenarios.value.find((scenario) => scenario.scenarioCode === scenarioCode)?.durationSeconds ?? 120
    draft.experimentStatus.affectedStationIds = scenarios.value.find((scenario) => scenario.scenarioCode === scenarioCode)?.targetStationIds ?? []
    draft.experimentStatus.expectedLinkState = expectedLinkState(scenarioCode)
    draft.executionTimeline.currentStepCode = 'FAULT_OCCURRED'
    draft.executionTimeline.steps = draft.executionTimeline.steps.map((step, index) => ({
      ...step,
      status: index === 0 ? 'RUNNING' : 'PENDING',
      timestamp: index === 0 ? startedAt : null
    }))
    draft.resourceTopology.resourceNodes = draft.resourceTopology.resourceNodes.map((node) => {
      if (scenarioCode === 'T5_FULL_LOSS_AUTONOMY' && node.nodeId === 'storage-hub-03') {
        return { ...node, linkState: LinkState.S3, controlMode: ControlMode.LocalAutonomy, statusText: '本地自治' }
      }
      if (scenarioCode === 'T9_RECOVERY_SYNC' && node.nodeId === 'storage-hub-03') {
        return { ...node, linkState: LinkState.S4, controlMode: ControlMode.EdgeCoordination, statusText: '恢复同步' }
      }
      if (scenarioCode === 'T1_LATENCY_JITTER' && node.nodeId === 'pv-gobi-01') {
        return { ...node, linkState: LinkState.S1, statusText: '时延抖动' }
      }
      if (scenarioCode === 'T2_RANDOM_PACKET_LOSS' && node.nodeId === 'wind-ridge-02') {
        return { ...node, linkState: LinkState.S1, statusText: '随机丢包' }
      }
      return node
    })
  })
  startMockTimers()
}

async function handleStartExperiment(scenarioCode: string): Promise<void> {
  isExperimentBusy.value = true
  errorMessage.value = ''
  try {
    if (dataSource === 'api') {
      await startAgentDispatchExperiment(scenarioCode)
      await loadOverview(true)
      return
    }
    applyMockScenario(scenarioCode)
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '实验启动失败'
  } finally {
    isExperimentBusy.value = false
  }
}

async function handleStopExperiment(): Promise<void> {
  isExperimentBusy.value = true
  errorMessage.value = ''
  try {
    if (dataSource === 'api') {
      await stopAgentDispatchExperiment()
      await loadOverview(true)
      return
    }
    clearMockTimers()
    updateMockOverview((draft) => {
      draft.experimentStatus.running = false
      draft.executionTimeline.steps = draft.executionTimeline.steps.map((step) => ({
        ...step,
        status: 'SUCCEEDED',
        timestamp: step.timestamp ?? new Date().toISOString()
      }))
      draft.executionTimeline.currentStepCode = 'RESULT_COMPARISON'
    })
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '实验停止失败'
  } finally {
    isExperimentBusy.value = false
  }
}

function buildRollingLine(
  name: string,
  field: keyof Omit<MainCurveSample, 'time'>,
  color: string
) {
  return {
    name,
    type: 'line',
    smooth: true,
    symbol: 'none',
    connectNulls: false,
    lineStyle: { width: 2, color },
    itemStyle: { color },
    data: mainCurveSamples.value.map((sample) => [sample.time, sample[field]])
  }
}

function metricNumber(value: DispatchMetricValue['value'] | DispatchMetricValue['improvementRate'], fallback: number): number {
  if (typeof value === 'number' && Number.isFinite(value)) {
    return value
  }
  if (typeof value === 'string' && value.trim()) {
    const parsed = Number(value)
    return Number.isFinite(parsed) ? parsed : fallback
  }
  return fallback
}

function numberOrNull(value: number | string | null | undefined): number | null {
  if (value === null || value === undefined || value === '') {
    return null
  }
  const numericValue = typeof value === 'number' ? value : Number(value)
  return Number.isFinite(numericValue) ? numericValue : null
}

function calculateTodayEnergy(powerKw: number, elapsedHours: number | null): number | null {
  if (elapsedHours === null) {
    return null
  }
  return powerKw * elapsedHours / 1_000
}

function calculateChangeRate(currentValue: number | null, referenceValue: number | null): number | null {
  if (currentValue === null || referenceValue === null || referenceValue === 0) {
    return null
  }
  return (currentValue - referenceValue) / Math.abs(referenceValue) * 100
}

function comparisonRate(metricCode: string): { rate: number | null; label: string; trend: KpiCard['trend'] } {
  const metric = overview.value.comparisonSummary.metrics.find((item) => item.metricCode === metricCode)
  if (!metric?.available || metric.improvementRate === null) {
    return { rate: null, label: '暂无调度对比', trend: 'flat' }
  }
  return {
    rate: Math.abs(metric.improvementRate),
    label: metric.metricName,
    trend: metric.trendDirection === 'UP' ? 'up' : metric.trendDirection === 'DOWN' ? 'down' : 'flat'
  }
}

function resolveTrend(rate: number | null): KpiCard['trend'] {
  if (rate === null || rate === 0) {
    return 'flat'
  }
  return rate > 0 ? 'up' : 'down'
}

function formatRate(rate: number | null): string {
  return rate === null ? '--' : `${formatNumber(Math.abs(rate), 1)}%`
}

function nodeValue(nodeType: DispatchTopologyNode['nodeType'], fallback: number): number {
  return topologyNodes.value.find((node) => node.nodeType === nodeType)?.activePower ?? fallback
}

function sumNodes(nodeTypes: DispatchTopologyNode['nodeType'][]): number {
  return topologyNodes.value
    .filter((node) => nodeTypes.includes(node.nodeType))
    .reduce((sum, node) => sum + Math.abs(node.activePower ?? 0), 0)
}

function formatNumber(value: number | string | null | undefined, digits = 1): string {
  if (value === null || value === undefined || value === '') {
    return '--'
  }
  const numericValue = typeof value === 'number' ? value : Number(value)
  if (!Number.isFinite(numericValue)) {
    return String(value)
  }
  return numericValue.toLocaleString('zh-CN', {
    minimumFractionDigits: digits,
    maximumFractionDigits: digits
  })
}

function formatDateTime(value: string | null): string {
  if (!value) {
    return '--'
  }
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false
  }).format(new Date(value)).replaceAll('/', '-')
}

function formatTrendTime(value: string): string {
  const timestamp = new Date(value)
  if (Number.isNaN(timestamp.getTime())) {
    return value
  }
  return new Intl.DateTimeFormat('zh-CN', {
    hour: '2-digit',
    minute: '2-digit',
    hour12: false
  }).format(timestamp)
}

function formatBalanceTime(value: number): string {
  return new Intl.DateTimeFormat('zh-CN', {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false
  }).format(new Date(value))
}

function formatTime(value: string | null): string {
  if (!value) {
    return '--:--:--'
  }
  return new Intl.DateTimeFormat('zh-CN', {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false
  }).format(new Date(value))
}

function round(value: number, digits: number): number {
  const scale = 10 ** digits
  return Math.round(value * scale) / scale
}

function toMegawatt(powerKilowatt: number | null): number | null {
  return powerKilowatt === null ? null : round(powerKilowatt / 1_000, 4)
}

onMounted(async () => {
  await loadScenarios()
  await Promise.all([loadOverview(true), loadMainCurveSamples()])
  initializePowerBalanceSamples()
  mainCurveTimer = window.setInterval(() => {
    void loadMainCurveSamples()
    void refreshPowerBalanceSample()
  }, AUTO_REFRESH_INTERVAL_MS)
  refreshTimer = window.setInterval(() => {
    void loadOverview(false)
  }, AUTO_REFRESH_INTERVAL_MS)
})

onBeforeUnmount(() => {
  if (refreshTimer !== undefined) {
    window.clearInterval(refreshTimer)
  }
  if (mainCurveTimer !== undefined) {
    window.clearInterval(mainCurveTimer)
  }
  clearMockTimers()
})
</script>

<template>
  <section class="dispatch-screen">
    <main class="dispatch-main">
      <header class="dispatch-topbar">
        <div class="dispatch-topbar__tab">多智能体调度中心</div>
        <div class="dispatch-topbar__meta">
          <span>{{ lastUpdatedAt }}</span>
        </div>
      </header>

      <p v-if="errorMessage" class="dispatch-error">{{ errorMessage }}</p>

      <section class="dispatch-kpis">
        <article v-for="card in kpiCards" :key="card.title" class="dispatch-card dispatch-kpi" :class="`is-${card.tone}`">
          <span>{{ card.title }}</span>
          <strong>{{ card.value }} <small>{{ card.unit }}</small></strong>
          <p>{{ card.rateLabel }} <b :class="card.trend === 'up' ? 'is-up' : card.trend === 'down' ? 'is-down' : ''">{{ card.trend === 'up' ? '↑' : card.trend === 'down' ? '↓' : '—' }} {{ card.rate }}</b></p>
        </article>
      </section>

      <section class="dispatch-content">
        <div class="dispatch-left">
          <article class="dispatch-card dispatch-chart-card">
            <header class="dispatch-section-header"><div><h2>实时曲线趋势</h2><em>最近 5 分钟模拟器真实遥测 · 右侧留白为尚未产生的数据</em></div></header>
            <div class="dispatch-chart-wrap"><EChartPanel :option="mainCurveOption" height="360px" /></div>
          </article>

          <AgentDispatchPowerCurve />

          <article class="dispatch-card dispatch-tracking-card">
            <header class="dispatch-section-header">
              <div><h2>执行后遥测收敛跟踪</h2><em>轮次 {{ resourceTracking?.optimizationRound ?? '--' }} · {{ formatTrackingStatus(resourceTracking?.convergenceStatus) }}</em></div>
            </header>
            <div class="tracking-kpis">
              <span>计划平衡误差 <b>{{ formatNumber(resourceTracking?.plannedBalanceError ?? null, 1) }} kW</b></span>
              <span>实际平衡误差 <b>{{ formatNumber(resourceTracking?.actualBalanceError ?? null, 1) }} kW</b></span>
              <span>连续达标 <b>{{ resourceTracking?.consecutiveSatisfiedSamples ?? 0 }}/{{ resourceTracking?.requiredSatisfiedSamples ?? 3 }}</b></span>
              <span>状态 <b>{{ formatTrackingStatus(resourceTracking?.convergenceStatus) }}</b></span>
              <span>历史终态 <b>{{ formatTrackingStatus(resourceTracking?.historicalConvergenceStatus) }}</b></span>
            </div>
            <div class="tracking-table-wrap">
              <table><thead><tr><th>资源</th><th>链路</th><th>目标</th><th>实际</th><th>误差</th><th>命令</th><th>收敛</th><th>数据时间</th></tr></thead>
              <tbody><tr v-for="resource in trackingResources" :key="resource.stationId"><td>{{ resource.stationId }}<small>{{ formatTrackingResourceType(resource.resourceType) }}</small></td><td>{{ formatTrackingLinkState(resource.linkState) }}</td><td>{{ formatNumber(resource.targetPower, 1) }}</td><td>{{ formatNumber(resource.actualPower, 1) }}</td><td>{{ formatNumber(resource.absoluteError, 1) }}</td><td>{{ formatTrackingCommandStatus(resource.commandStatus) }}</td><td>{{ formatTrackingStatus(resource.trackingStatus) }}</td><td>{{ resource.actualReceivedAt ? formatDateTime(resource.actualReceivedAt) : '--' }}</td></tr>
              <tr v-if="trackingResources.length === 0"><td colspan="8">暂无真实逐资源执行数据。</td></tr></tbody></table>
            </div>
          </article>

          <div class="dispatch-lower-grid">
            <article class="dispatch-card dispatch-small-chart">
              <header class="dispatch-section-header"><h2>资源出力占比</h2></header>
              <EChartPanel :option="resourcePieOption" height="205px" />
            </article>

            <article class="dispatch-card dispatch-small-chart">
              <header class="dispatch-section-header">
                <h2>储能SOC变化</h2>
                <em>当前SOC：{{ formatNumber(averageSoc, 1) }}%</em>
              </header>
              <EChartPanel :option="socOption" height="205px" />
            </article>

            <article class="dispatch-card dispatch-small-chart">
              <header class="dispatch-section-header"><h2>功率平衡</h2><em>单位：MW</em></header>
              <EChartPanel :option="powerBalanceOption" height="205px" />
            </article>
          </div>

        </div>

        <aside class="dispatch-right">
          <article class="dispatch-card dispatch-ops">
            <h2>多智能体调度操作</h2>
            <h3>调度模式</h3>
            <div class="dispatch-mode-grid">
              <button
                v-for="item in dispatchModes"
                :key="item"
                type="button"
                :class="{ 'is-active': activeDispatchMode === item }"
                @click="activeDispatchMode = item"
              >
                {{ item }}
              </button>
            </div>
            <label>
              <span>全局目标</span>
              <select v-model="selectedGoal">
                <option v-for="item in globalGoals" :key="item" :value="item">{{ item }}</option>
              </select>
            </label>
            <div class="dispatch-actions">
              <button type="button" :disabled="isExperimentBusy" @click="handleStartExperiment(scenarios[0]?.scenarioCode ?? 'T2_RANDOM_PACKET_LOSS')">启动调度</button>
              <button type="button" class="is-warning" :disabled="isExperimentBusy" @click="handleStartExperiment(scenarios[1]?.scenarioCode ?? 'T1_LATENCY_JITTER')">暂停调度</button>
              <button type="button" class="is-danger" :disabled="isExperimentBusy" @click="handleStopExperiment">停止调度</button>
            </div>
            <div class="dispatch-operation-output">
              <h3>最终执行动作</h3>
              <ul v-if="dispatchOperationRows.length">
                <li v-for="item in dispatchOperationRows" :key="`${item.agentName}-${item.action}`">
                  <span>{{ item.agentName }}</span>
                  <strong>{{ item.operation }}</strong>
                  <small>{{ item.action }}</small>
                  <em>{{ item.decision }}</em>
                </li>
              </ul>
              <p v-else>本轮暂无真实控制动作，仅记录智能体诊断与约束结果。</p>
            </div>
          </article>

          <article class="dispatch-card dispatch-agent-card">
            <h2>智能体协作流程</h2>
            <ol class="dispatch-agent-flow">
              <li v-for="step in collaborationSteps" :key="step.agentType" :class="step.statusClass">
                <b>{{ step.stepIndex }}</b>
                <div>
                  <strong>{{ step.stageName }}</strong>
                  <span>{{ step.agentName }}</span>
                  <small>{{ step.output }}</small>
                </div>
                <em>{{ step.status }}</em>
              </li>
            </ol>
          </article>

          <article class="dispatch-card dispatch-decision-card">
            <header class="decision-card__header">
              <div>
                <h2>最终自动裁决</h2>
                <p>{{ decisionHeadline }}</p>
              </div>
              <em class="decision-card__status" :class="finalDecisionClass">{{ finalDecisionStatus }}</em>
            </header>

            <div class="decision-card__metrics">
              <section>
                <span>安全校验</span>
                <strong :class="{ 'is-safe': overview.agentDecisionSummary.safetyGatePassed }">
                  {{ overview.agentDecisionSummary.safetyGatePassed ? '已通过' : '未通过' }}
                </strong>
              </section>
              <section>
                <span>执行模式</span>
                <strong :class="finalDecisionClass">{{ decisionExecutionMode }}</strong>
              </section>
            </div>

            <ol class="decision-card__progress" aria-label="调度裁决进度">
              <li v-for="item in decisionProgress" :key="item.label" :class="{ 'is-completed': item.completed }">
                <span>{{ item.completed ? '✓' : '·' }}</span>
                <b>{{ item.label }}</b>
              </li>
            </ol>

            <footer class="decision-card__footer">
              <button type="button" @click="loadOverview(true)">刷新裁决数据</button>
              <span>更新时间 {{ lastUpdatedAt }}</span>
            </footer>
          </article>
        </aside>
      </section>

      <article class="dispatch-card dispatch-log-card">
        <header class="dispatch-section-header">
          <h2>调度日志</h2>
          <button type="button" @click="loadOverview(true)">查看更多</button>
        </header>
        <ul>
          <li v-for="item in logs" :key="`${item.time}-${item.text}`">
            <time>{{ item.time }}</time>
            <span>{{ item.text }}</span>
          </li>
        </ul>
      </article>
    </main>
  </section>
</template>

<style scoped>
.dispatch-screen {
  min-height: 100vh;
  color: #dce9ff;
  background:
    radial-gradient(circle at 48% 28%, rgba(0, 112, 255, 0.16), transparent 34%),
    linear-gradient(180deg, #061832 0%, #031024 48%, #020b19 100%);
  font-family: "Microsoft YaHei", "PingFang SC", sans-serif;
}

.dispatch-main {
  min-width: 0;
  width: min(100%, 1600px);
  padding: 0 0 12px;
  margin: 0 auto;
}

.dispatch-topbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  height: 62px;
  margin-bottom: 16px;
  border: 1px solid rgba(72, 224, 255, 0.24);
  border-radius: 8px;
  background: linear-gradient(180deg, rgba(8, 34, 128, 0.8), rgba(4, 24, 95, 0.84));
}

.dispatch-topbar__tab {
  min-width: 0;
  padding: 0 20px;
  color: #dbeeff;
  font-size: 16px;
  font-weight: 800;
  text-align: center;
  border: 0;
  border-radius: 0;
  background: transparent;
}

.dispatch-topbar__meta {
  display: flex;
  align-items: center;
  gap: 18px;
  color: #ffffff;
  font-size: 14px;
}

.dispatch-topbar button,
.dispatch-actions button,
.dispatch-mode-grid button,
.dispatch-section-header button {
  border: 1px solid rgba(58, 122, 205, 0.52);
  border-radius: 5px;
  color: #dce9ff;
  background: rgba(9, 32, 69, 0.9);
  cursor: pointer;
}

.dispatch-alert {
  padding: 9px 18px;
  color: #ff7668 !important;
  background: rgba(115, 23, 35, 0.62) !important;
}

.dispatch-user {
  padding: 9px 18px;
}

.dispatch-error {
  margin: 8px 0;
  padding: 10px 14px;
  color: #ffb7a8;
  border: 1px solid rgba(255, 100, 100, 0.35);
  background: rgba(110, 26, 26, 0.3);
}

.dispatch-kpis {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 16px;
  margin: 18px 0 16px;
}

.dispatch-card {
  border: 1px solid rgba(72, 224, 255, 0.24);
  border-radius: 8px;
  background: linear-gradient(180deg, rgba(8, 34, 128, 0.8), rgba(4, 24, 95, 0.84));
  box-shadow: inset 0 0 20px rgba(54, 200, 255, 0.06);
}

.dispatch-kpi {
  min-height: 126px;
  padding: 22px 24px;
}

.dispatch-kpi span {
  display: block;
  color: #ffffff;
  font-size: 16px;
  font-weight: 700;
}

.dispatch-kpi strong {
  display: block;
  margin-top: 13px;
  color: #3491ff;
  font-size: 36px;
  line-height: 1;
}

.dispatch-kpi.is-green strong {
  color: #50dd69;
}

.dispatch-kpi.is-purple strong {
  color: #8c5dff;
}

.dispatch-kpi small {
  color: #dce9ff;
  font-size: 15px;
  font-weight: 500;
}

.dispatch-kpi p {
  margin: 16px 0 0;
  color: #b2c2d9;
}

.dispatch-kpi b {
  margin-left: 8px;
}

.is-up {
  color: #ff6857;
}

.is-down {
  color: #38dd6f;
}

.dispatch-content {
  display: grid;
  grid-template-columns: minmax(0, 1fr) clamp(360px, 24vw, 440px);
  grid-template-rows: repeat(4, auto);
  gap: 16px;
}

.dispatch-left,
.dispatch-right {
  display: contents;
}

.dispatch-left > :nth-child(1) { grid-column: 1; grid-row: 1; }
.dispatch-left > :nth-child(2) { grid-column: 1; grid-row: 2; }
.dispatch-left > :nth-child(3) { grid-column: 1; grid-row: 3; }
.dispatch-left > :nth-child(4) { grid-column: 1; grid-row: 4; }
.dispatch-right > :nth-child(1) { grid-column: 2; grid-row: 1; }
.dispatch-right > :nth-child(2) { grid-column: 2; grid-row: 2; }
.dispatch-right > :nth-child(3) { grid-column: 2; grid-row: 3 / span 2; }

.dispatch-left > *,
.dispatch-right > * {
  min-width: 0;
}

.dispatch-section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 46px;
  padding: 0 22px;
}

.dispatch-section-header h2,
.dispatch-ops h2,
.dispatch-agent-card h2,
.dispatch-decision-card h2 {
  margin: 0;
  color: #ffffff;
  font-size: 20px;
}

.dispatch-section-header em {
  color: #62ff85;
  font-style: normal;
  font-size: 12px;
}

.dispatch-mode-grid button.is-active,
.dispatch-actions button:first-child {
  color: #ffffff;
  background: linear-gradient(180deg, #2d98ff, #1465d8);
}

.dispatch-chart-wrap {
  position: relative;
  padding: 0 18px 12px;
}

.dispatch-lower-grid {
  display: grid;
  grid-template-columns: 0.88fr 1fr 1fr;
  gap: 16px;
}

.dispatch-tracking-card { margin-top: 14px; padding: 14px; }
.tracking-kpis { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 8px; margin: 8px 0; }
.tracking-kpis span { display: grid; gap: 4px; padding: 7px; color: #8ea6c0; border: 1px solid rgba(83, 137, 194, .25); border-radius: 4px; font-size: 10px; }
.tracking-kpis b { color: #55d9a5; font-size: 12px; }
.tracking-table-wrap { overflow-x: auto; margin-top: 8px; }
.tracking-table-wrap table { width: 100%; min-width: 760px; border-collapse: collapse; color: #c8d8ea; font-size: 10px; }
.tracking-table-wrap th, .tracking-table-wrap td { padding: 7px 8px; border-bottom: 1px solid rgba(83, 137, 194, .18); text-align: left; white-space: nowrap; }
.tracking-table-wrap th { color: #7f9abb; font-size: 9px; font-weight: 500; }
.tracking-table-wrap small { display: block; margin-top: 2px; color: #728ba5; font-size: 8px; }
.dispatch-small-chart {
  min-height: 260px;
}

.dispatch-log-card {
  margin-top: 16px;
  padding-bottom: 12px;
}

.dispatch-log-card ul,
.dispatch-agent-flow {
  margin: 0;
  padding: 0;
  list-style: none;
}

.dispatch-log-card li {
  display: grid;
  grid-template-columns: 78px 1fr;
  gap: 10px;
  padding: 8px 22px;
  color: #c9d8ee;
  border-top: 1px solid rgba(71, 128, 194, 0.16);
}

.dispatch-log-card time {
  color: #99acc8;
}

.dispatch-ops,
.dispatch-agent-card,
.dispatch-decision-card {
  padding: 18px 16px;
}

.dispatch-ops h3 {
  margin: 18px 0 10px;
  color: #c7d6eb;
  font-size: 13px;
}

.dispatch-mode-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.dispatch-mode-grid button {
  height: 36px;
}

.dispatch-ops label {
  display: grid;
  gap: 8px;
  margin-top: 14px;
}

.dispatch-ops label span {
  color: #c7d6eb;
  font-size: 13px;
}

.dispatch-ops select {
  height: 36px;
  padding: 0 10px;
  color: #dce9ff;
  border: 1px solid rgba(58, 122, 205, 0.52);
  border-radius: 5px;
  background: #092045;
}

.dispatch-actions {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
  margin-top: 14px;
}

.dispatch-actions button {
  height: 34px;
  font-weight: 700;
}

.dispatch-actions .is-warning {
  background: #c87b24;
}

.dispatch-actions .is-danger {
  background: #bc3c44;
}

.dispatch-operation-output {
  margin-top: 16px;
  padding-top: 14px;
  border-top: 1px solid rgba(80, 142, 212, 0.2);
}

.dispatch-operation-output h3 {
  margin-top: 0;
}

.dispatch-operation-output ul {
  display: grid;
  gap: 8px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.dispatch-operation-output li {
  display: grid;
  gap: 5px;
  padding: 9px 10px;
  border: 1px solid rgba(64, 143, 235, 0.28);
  border-radius: 6px;
  background: rgba(7, 30, 67, 0.72);
}

.dispatch-operation-output span {
  color: #69d9ff;
  font-size: 12px;
}

.dispatch-operation-output strong {
  color: #e7f1ff;
  font-size: 12px;
  word-break: break-word;
}

.dispatch-operation-output small {
  color: #8fb0d6;
  font-size: 11px;
  word-break: break-word;
}

.dispatch-operation-output em {
  justify-self: start;
  padding: 2px 7px;
  color: #62ff9d;
  font-size: 12px;
  font-style: normal;
  border-radius: 4px;
  background: rgba(18, 94, 62, 0.46);
}

.dispatch-operation-output p {
  margin: 0;
  color: #9eb3cf;
  font-size: 12px;
}

.dispatch-agent-flow {
  display: grid;
  gap: 10px;
  margin-top: 14px;
}

.dispatch-agent-flow li {
  display: grid;
  grid-template-columns: 24px minmax(0, 1fr) auto;
  align-items: start;
  gap: 10px;
  min-height: 0;
  padding: 12px 0;
  color: #cbd9ef;
  border-top: 1px solid rgba(80, 142, 212, 0.18);
}

.dispatch-agent-flow li b {
  display: grid;
  place-items: center;
  width: 22px;
  height: 22px;
  margin-top: 2px;
  border-radius: 50%;
  color: #ffffff;
  font-size: 12px;
  background: #227cff;
}

.dispatch-agent-flow li div {
  display: grid;
  min-width: 0;
  gap: 4px;
}

.dispatch-agent-flow strong {
  color: #ffffff;
  font-size: 14px;
}

.dispatch-agent-flow span {
  color: #7fc7ff;
  font-size: 12px;
}

.dispatch-agent-flow small {
  color: #a4b5cc;
  font-size: 12px;
  line-height: 1.45;
}

.dispatch-agent-flow em {
  justify-self: end;
  white-space: nowrap;
  padding: 3px 7px;
  font-style: normal;
  font-size: 12px;
  border-radius: 4px;
  color: #6dff9b;
  background: rgba(28, 150, 68, 0.18);
}

.dispatch-agent-flow li.is-idle b {
  background: #66768d;
}

.dispatch-agent-flow li.is-idle em {
  color: #93a6bf;
  background: rgba(116, 135, 160, 0.16);
}

.dispatch-agent-flow li.is-observed b {
  background: #5b83ff;
}

.dispatch-agent-flow li.is-observed em {
  color: #9ddfff;
  background: rgba(81, 139, 255, 0.18);
}

.dispatch-agent-flow li.is-degraded b {
  background: #c08420;
}

.dispatch-agent-flow li.is-degraded em {
  color: #ffd45d;
  background: rgba(192, 132, 32, 0.22);
}

.dispatch-agent-flow li.is-rejected b {
  background: #bc3c44;
}

.dispatch-agent-flow li.is-rejected em {
  color: #ff9e8b;
  background: rgba(188, 60, 68, 0.2);
}

.dispatch-decision-card {
  display: grid;
  gap: 20px;
  padding: 24px;
  color: #dce9ff;
  border-color: rgba(45, 137, 238, 0.72);
  background:
    radial-gradient(circle at 88% 4%, rgba(50, 190, 127, 0.14), transparent 30%),
    linear-gradient(135deg, rgba(9, 38, 84, 0.98), rgba(3, 22, 52, 0.98));
}

.decision-card__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 18px;
}

.decision-card__header h2 {
  font-size: 26px;
}

.decision-card__header p {
  margin: 8px 0 0;
  color: #a9c2e7;
  font-size: 15px;
}

.decision-card__status {
  flex: 0 0 auto;
  padding: 10px 14px;
  color: #7dff9a;
  font-style: normal;
  font-size: 16px;
  font-weight: 700;
  line-height: 1;
  border: 1px solid rgba(80, 232, 123, 0.6);
  border-radius: 8px;
  background: rgba(27, 131, 68, 0.2);
  box-shadow: 0 0 18px rgba(69, 222, 104, 0.14);
}

.decision-card__status.is-degraded {
  color: #ffd45d;
  border-color: rgba(255, 206, 73, 0.58);
  background: rgba(192, 132, 32, 0.22);
}

.decision-card__status.is-rejected {
  color: #ff9a8a;
  border-color: rgba(255, 125, 106, 0.52);
  background: rgba(188, 60, 68, 0.22);
}

.decision-card__status.is-unknown {
  color: #b7c8de;
  border-color: rgba(160, 184, 217, 0.36);
  background: rgba(116, 135, 160, 0.18);
}

.decision-card__metrics {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  overflow: hidden;
  border: 1px solid rgba(41, 116, 203, 0.44);
  border-radius: 10px;
  background: rgba(2, 17, 43, 0.42);
}

.decision-card__metrics section {
  display: grid;
  gap: 12px;
  align-content: center;
  min-height: 126px;
  padding: 22px 24px;
  border-left: 1px solid rgba(45, 103, 175, 0.38);
}

.decision-card__metrics section:first-child {
  border-left: 0;
}

.decision-card__metrics span {
  color: #9eb7da;
  font-size: 14px;
}

.decision-card__metrics strong {
  color: #eff6ff;
  font-size: 28px;
  line-height: 1.1;
  white-space: nowrap;
}

.decision-card__metrics small {
  margin-left: 4px;
  color: #9fb6d9;
  font-size: 15px;
  font-weight: 500;
}

.decision-card__metrics strong.is-safe,
.decision-card__metrics strong.is-passed {
  color: #73f58f;
}

.decision-card__metrics strong.is-degraded {
  color: #ffd45d;
}

.decision-card__metrics strong.is-rejected {
  color: #ff9a8a;
}

.decision-card__progress {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 14px;
  margin: 0;
  padding: 16px 20px;
  list-style: none;
  border: 1px solid rgba(56, 121, 205, 0.34);
  border-radius: 8px;
  background: rgba(5, 31, 66, 0.52);
}

.decision-card__progress li {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 9px;
  min-width: 0;
  color: #8fa8c9;
}

.decision-card__progress li + li::before {
  margin-right: auto;
  color: rgba(91, 145, 220, 0.7);
  content: '→';
}

.decision-card__progress span {
  display: grid;
  place-items: center;
  width: 22px;
  height: 22px;
  color: #9db5d4;
  font-size: 14px;
  border: 1px solid rgba(125, 159, 201, 0.52);
  border-radius: 50%;
}

.decision-card__progress b {
  font-size: 14px;
  font-weight: 600;
}

.decision-card__progress li.is-completed {
  color: #75f690;
}

.decision-card__progress li.is-completed span {
  color: #75f690;
  border-color: rgba(82, 231, 117, 0.78);
  background: rgba(27, 131, 68, 0.2);
}

.decision-card__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-top: 2px;
  border-top: 1px solid rgba(43, 106, 183, 0.4);
}

.decision-card__footer button {
  padding: 12px 0 0;
  color: #52a8ff;
  font-weight: 600;
  border: 0;
  background: transparent;
  cursor: pointer;
}

.decision-card__footer span {
  padding-top: 12px;
  color: #849bc0;
  font-size: 13px;
}

@media (max-width: 1400px) {
  .dispatch-screen {
    grid-template-columns: 210px minmax(0, 1fr);
  }

  .dispatch-kpis {
    grid-template-columns: repeat(3, 1fr);
  }

  .dispatch-content {
    grid-template-columns: 1fr;
    grid-template-rows: none;
  }

  .dispatch-left,
  .dispatch-right {
    display: grid;
    gap: 16px;
    align-content: start;
    min-width: 0;
  }

  .dispatch-right {
    grid-template-columns: repeat(3, 1fr);
  }

  .dispatch-left > *,
  .dispatch-right > * {
    grid-column: auto;
    grid-row: auto;
  }

  .dispatch-decision-card {
    grid-column: 1 / -1;
  }
}

@media (max-width: 980px) {
  .dispatch-topbar,
  .dispatch-topbar__meta {
    flex-wrap: wrap;
    height: auto;
    padding-bottom: 10px;
  }

  .dispatch-kpis,
  .dispatch-lower-grid,
  .dispatch-right {
    grid-template-columns: 1fr;
  }

  .dispatch-decision-card {
    grid-column: auto;
  }
}

@media (max-width: 680px) {
  .dispatch-decision-card {
    gap: 16px;
    padding: 18px;
  }

  .decision-card__header,
  .decision-card__footer {
    align-items: flex-start;
    flex-direction: column;
  }

  .decision-card__status {
    font-size: 14px;
  }

  .decision-card__metrics,
  .decision-card__progress {
    grid-template-columns: 1fr;
  }

  .decision-card__metrics section,
  .decision-card__metrics section:first-child {
    min-height: auto;
    border-top: 1px solid rgba(45, 103, 175, 0.38);
    border-left: 0;
  }

  .decision-card__metrics section:first-child {
    border-top: 0;
  }

  .decision-card__progress li {
    justify-content: flex-start;
  }

  .decision-card__progress li + li::before {
    display: none;
  }

}
</style>
