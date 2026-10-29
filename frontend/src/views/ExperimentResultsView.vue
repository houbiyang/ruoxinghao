<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import type { Component } from 'vue'
import type { EChartsOption } from 'echarts'
import {
  Aim,
  Back,
  CircleCheckFilled,
  Clock,
  Connection,
  DataAnalysis,
  Monitor,
  Opportunity,
  RefreshRight,
  Timer,
  TrendCharts,
  VideoPause,
  VideoPlay
} from '@element-plus/icons-vue'

import {
  deleteExperiment,
  fetchExperimentQualityMetrics,
  fetchExperimentReplay,
  fetchExperimentRun,
  fetchExperimentSummary,
  fetchExperimentTimeline,
  fetchLatestExperimentRunItems,
  fetchMarketAgentRun
} from '@/api/systemApi'
import EChartPanel from '@/components/charts/EChartPanel.vue'
import type {
  ExperimentQualityMetrics,
  ExperimentReplayPoint,
  ExperimentRun,
  ExperimentRunListItem,
  ExperimentSummary,
  ExperimentTimelinePoint,
  MarketAgentDecision,
  MarketAgentRun,
  MarketAgentType
} from '@/types/system'

interface MetricCard {
  label: string
  value: string
  unit: string
  tone: 'cyan' | 'orange' | 'green' | 'blue'
  icon: Component
}

interface AgentDecision {
  id: string
  time: string
  phase: string
  agentName: string
  operation: string
  retryCount: number
  evidenceCount: number
  status: string
  badge: string
  completed: boolean
  tone: 'cyan' | 'blue' | 'orange' | 'green' | 'red'
}

interface ResourceProfile {
  code: string
  label: string
  color: string
  availableCapacity: Array<number | null>
  actualPower: Array<number | null>
}

interface StoredExperimentName {
  experimentRunId?: string
  experimentName?: string
}

const chartResourceDefinitions = [
  { code: 'pv-gobi-01', label: '光伏', color: '#66d39a' },
  { code: 'thermal-plant-06', label: '火电', color: '#f5a340' },
  { code: 'storage-hub-03', label: '储能', color: '#9d8cf2' },
  { code: 'load-oasis-04', label: '负荷', color: '#eef5ff' },
  { code: 'wind-ridge-02', label: '风电', color: '#778cff' },
  { code: 'charge-corridor-05', label: '充电桩', color: '#ef6f75' },
  { code: 'grid-link-00', label: '市电', color: '#35d9e8' }
] as const
const fallbackResourceColor = '#3f82ff'
const agentNameMap: Record<MarketAgentType, string> = {
  MARKET_INTERFACE: '市场接口智能体',
  FORECAST: '预测智能体',
  ENVIRONMENT_ASSESSMENT: '环境评估智能体',
  RESOURCE_CAPABILITY: '资源能力智能体',
  MARKET_DISPATCH: '市场调度智能体',
  COMMUNICATION_QUALITY: '通信质量智能体',
  ASSET_DIAGNOSIS: '资产诊断智能体',
  CONTROL_DECISION: '控制决策智能体',
  RECOVERY_ORCHESTRATION: '恢复编排智能体',
  SECURITY_AUDIT: '安全审计智能体',
  SETTLEMENT_EVALUATION: '结算评估智能体'
}
const agentPhaseMap: Record<MarketAgentType, string> = {
  MARKET_INTERFACE: '市场输入',
  FORECAST: '预测生成',
  ENVIRONMENT_ASSESSMENT: '环境感知',
  RESOURCE_CAPABILITY: '资源能力',
  MARKET_DISPATCH: '调度生成',
  COMMUNICATION_QUALITY: '通信评估',
  ASSET_DIAGNOSIS: '资产诊断',
  CONTROL_DECISION: '控制决策',
  RECOVERY_ORCHESTRATION: '恢复编排',
  SECURITY_AUDIT: '安全审计',
  SETTLEMENT_EVALUATION: '结算评估'
}
const agentActionMap: Record<string, string> = {
  VERIFY_MARKET_SIGNAL_VERSION: '校验当前市场信号版本并锁定本轮市场输入',
  USE_P50_FORECAST: '生成 P50 预测基线',
  USE_CONSERVATIVE_FORECAST: '生成保守预测基线',
  APPLY_ENVIRONMENT_CORRECTION: '应用环境修正',
  DOWNGRADE_RENEWABLE_CAPABILITY: '下调新能源可用能力',
  FREEZE_ENVIRONMENT_INPUT: '冻结环境输入',
  FREEZE_RESOURCE_CAPABILITY_BOUNDARY: '冻结修正后的资源能力边界',
  GENERATE_MARKET_OPTIMAL_PLAN: '生成市场最优候选调度计划',
  APPLY_S0_S4_CAPACITY_FACTOR: '评估 S0-S4 链路并应用弱信号容量折减',
  ISSUE_SAFE_DISPATCH_DECISION: '生成可安全下发的控制决策',
  RECREATE_PLAN_AFTER_S4: '在 S4 恢复同步后重建计划',
  ALLOW_SAFE_ACTIONS: '允许安全控制动作继续执行',
  BLOCK_UNSAFE_ACTIONS: '阻断不满足安全约束的控制动作',
  TRACE_PLAN_COMMAND_TELEMETRY: '核对计划、指令和遥测执行链路'
}

const experimentRuns = ref<ExperimentRunListItem[]>([])
const selectedExperimentId = ref('')
const route = useRoute()
const selectedResourceCode = ref('')
const summary = ref<ExperimentSummary | null>(null)
const qualityMetrics = ref<ExperimentQualityMetrics | null>(null)
const timelinePoints = ref<ExperimentTimelinePoint[]>([])
const replayPoints = ref<ExperimentReplayPoint[]>([])
const agentRun = ref<MarketAgentRun | null>(null)
const loading = ref(false)
const deleting = ref(false)
const batchDeleting = ref(false)
const batchDeleteOpen = ref(false)
const selectedBatchRunIds = ref<string[]>([])
const experimentSelectorOpen = ref(false)
const experimentNames = ref<Record<string, string>>({})
const loadError = ref('')
const replayIndex = ref(0)
const isPlaying = ref(false)
const powerChartWindowSize = ref(60)
let replayTimer: number | undefined

const selectedExperiment = computed(() => experimentRuns.value.find((item) => item.runId === selectedExperimentId.value) ?? null)
const selectedExperimentName = computed(() => selectedExperiment.value === null ? '选择历史实验' : getExperimentName(selectedExperiment.value))
const batchDeletableExperiments = computed(() => experimentRuns.value.filter(isExperimentDeletable))
const timelineTimestamps = computed(() => [...new Set(replayPoints.value.map((point) => normalizeTimestamp(point.timestamp)))].sort())
const timelineLabels = computed(() => timelineTimestamps.value.map(formatTime))
const resourceProfiles = computed<ResourceProfile[]>(() => {
  const pointByStationAndTimestamp = new Map<string, ExperimentReplayPoint>()
  const reportedStationIds = new Set(replayPoints.value.map((point) => point.stationId))
  const stationIds = [
    ...chartResourceDefinitions.map((resource) => resource.code),
    ...[...reportedStationIds].filter((stationId) => !chartResourceDefinitions.some((resource) => resource.code === stationId)).sort()
  ]
  replayPoints.value.forEach((point) => pointByStationAndTimestamp.set(`${point.stationId}|${normalizeTimestamp(point.timestamp)}`, point))
  return stationIds.map((stationId) => {
    const definition = chartResourceDefinitions.find((resource) => resource.code === stationId)
    return {
      code: stationId,
      label: definition?.label ?? stationId,
      color: definition?.color ?? fallbackResourceColor,
      availableCapacity: timelineTimestamps.value.map((timestamp) => toFiniteNumber(pointByStationAndTimestamp.get(`${stationId}|${timestamp}`)?.availableCapacity)),
      actualPower: timelineTimestamps.value.map((timestamp) => toFiniteNumber(pointByStationAndTimestamp.get(`${stationId}|${timestamp}`)?.activePower))
    }
  })
})
const selectedResource = computed(() => resourceProfiles.value.find((item) => item.code === selectedResourceCode.value) ?? resourceProfiles.value[0] ?? null)
const powerProfiles = computed(() => resourceProfiles.value.filter((profile) => !isLoadStation(profile.code)))
const loadProfiles = computed(() => resourceProfiles.value.filter((profile) => isLoadStation(profile.code)))
const totalSupplyPower = computed(() => timelineTimestamps.value.map((_, index) => sumAt(powerProfiles.value, index)))
const loadPower = computed(() => timelineTimestamps.value.map((_, index) => sumAt(loadProfiles.value, index)))
const balanceError = computed(() => totalSupplyPower.value.map((supply, index) => {
  const load = loadPower.value[index]
  return supply === null || load === null ? null : supply - load
}))
const powerChartEndIndex = computed(() => Math.min(replayIndex.value, Math.max(0, timelineLabels.value.length - 1)))
const powerChartStartIndex = computed(() => powerChartWindowSize.value === 0
  ? 0
  : Math.max(0, powerChartEndIndex.value - powerChartWindowSize.value + 1))
const stages = computed(() => {
  const boundaries = resolvePowerStageBoundaries()
  return [
    { label: '正常数据', startIndex: 0, description: '模拟器在注入前上报的真实基线遥测' },
    { label: '故障注入', startIndex: boundaries.injectionIndex, description: '故障生效后的实际资源功率变化' },
    { label: '智能体调控', startIndex: boundaries.orchestrationIndex, description: '智能体生成并执行调度计划后的数据变化' },
    { label: '恢复正常', startIndex: boundaries.recoveryIndex, description: '恢复完成后模拟器持续上报的真实数据' }
  ]
})
const decisions = computed<AgentDecision[]>(() => agentRun.value?.decisions.map(toAgentDecision) ?? [])
const currentStage = computed(() => [...stages.value].reverse().find((stage) => replayIndex.value >= stage.startIndex) ?? stages.value[0])
const completedDecisionCount = computed(() => decisions.value.filter((decision) => decision.completed).length)
const decisionResultTitle = computed(() => {
  if (!agentRun.value) return summary.value?.orchestrationStatus === 'FAILED' ? '智能体编排失败' : '等待智能体运行'
  if (agentRun.value.safetyDecision.passed) return '安全门校验通过'
  return '安全门拒绝执行'
})
const decisionResultDetail = computed(() => {
  if (!agentRun.value) return summary.value?.failureReason ?? '当前实验尚未生成智能体决策'
  return `${completedDecisionCount.value} / ${decisions.value.length} 个智能体阶段完成 · ${agentRun.value.safetyDecision.decisionLevel}`
})
const firstFaultTimestamp = computed(() => timelinePoints.value.find((point) => !point.online || point.linkState === 'S3')?.timestamp ?? null)
const firstRecoveryTimestamp = computed(() => {
  if (!firstFaultTimestamp.value) return null
  const faultTimestampValue = new Date(firstFaultTimestamp.value).getTime()
  return timelinePoints.value.find((point) => new Date(point.timestamp).getTime() > faultTimestampValue && point.online && point.linkState === 'S4')?.timestamp ?? null
})

const metrics = computed<MetricCard[]>(() => {
  const during = qualityMetrics.value?.during
  const maximumBalanceError = Math.max(0, ...balanceError.value.filter((value): value is number => value !== null).map(Math.abs))
  return [
    { label: '遥测样本', value: String(summary.value?.sampleCount ?? 0), unit: '条', tone: 'cyan', icon: DataAnalysis },
    { label: '事件数量', value: String(summary.value?.eventCount ?? 0), unit: '个', tone: 'orange', icon: Opportunity },
    { label: '平均时延', value: formatNumber(during?.averageLatency), unit: 'ms', tone: 'blue', icon: Clock },
    { label: '平均丢包率', value: formatNumber(during?.averagePacketLossRate), unit: '%', tone: 'blue', icon: Connection },
    { label: '最大数据时效', value: formatNumber(during?.maxDataAge), unit: 's', tone: 'blue', icon: Timer },
    { label: '在线率', value: formatNumber(during?.onlineRate), unit: '%', tone: 'green', icon: CircleCheckFilled },
    { label: '最大供需误差', value: formatNumber(maximumBalanceError), unit: 'kW', tone: 'blue', icon: Aim },
    { label: '主要链路状态', value: summary.value?.dominantState ?? '--', unit: '', tone: 'green', icon: TrendCharts }
  ]
})

const mainPowerOption = computed<EChartsOption>(() => ({
  animationDurationUpdate: 300,
  color: [...chartResourceDefinitions.map((resource) => resource.color), '#3f82ff'],
  tooltip: {
    trigger: 'axis',
    axisPointer: { type: 'cross', lineStyle: { color: 'rgba(169, 190, 213, 0.62)', width: 1 } },
    backgroundColor: 'rgba(4, 14, 31, 0.96)',
    borderColor: 'rgba(80, 139, 200, 0.55)',
    textStyle: { color: '#edf5ff' },
    formatter: formatPowerTooltip
  },
  legend: {
    top: 2,
    right: 8,
    itemWidth: 18,
    itemHeight: 7,
    textStyle: { color: '#a9bed5', fontSize: 11 },
    data: [...resourceProfiles.value.map((profile) => `${profile.label}功率`), '总供电功率']
  },
  grid: { top: 58, left: 52, right: 24, bottom: 82 },
  xAxis: {
    type: 'category',
    name: '时间',
    nameTextStyle: { color: '#8ea7c2', padding: [14, 0, 0, 0] },
    data: timelineLabels.value,
    boundaryGap: false,
    axisLine: { lineStyle: { color: 'rgba(116, 156, 199, 0.42)' } },
    axisTick: { show: false },
    axisLabel: { color: '#8ea7c2', hideOverlap: true, fontSize: 10 }
  },
  yAxis: {
    type: 'value',
    name: '功率 (kW)',
    scale: true,
    nameTextStyle: { color: '#8ea7c2', align: 'left' },
    axisLabel: { color: '#8ea7c2' },
    splitLine: { lineStyle: { color: 'rgba(104, 145, 190, 0.12)' } }
  },
  dataZoom: [
    { type: 'inside', startValue: powerChartStartIndex.value, endValue: powerChartEndIndex.value, zoomOnMouseWheel: true, moveOnMouseMove: true },
    {
      type: 'slider',
      startValue: powerChartStartIndex.value,
      endValue: powerChartEndIndex.value,
      height: 18,
      bottom: 8,
      borderColor: 'rgba(83, 137, 194, 0.34)',
      backgroundColor: 'rgba(6, 19, 38, 0.86)',
      fillerColor: 'rgba(76, 134, 247, 0.18)',
      dataBackground: { lineStyle: { color: '#4c86f7', opacity: 0.7 }, areaStyle: { color: '#4c86f7', opacity: 0.12 } },
      selectedDataBackground: { lineStyle: { color: '#35d9e8' }, areaStyle: { color: '#35d9e8', opacity: 0.14 } },
      handleStyle: { color: '#0a1d38', borderColor: '#35d9e8' },
      textStyle: { color: '#8ea7c2', fontSize: 9 }
    }
  ],
  series: [
    ...resourceProfiles.value.map((profile) => ({
      name: `${profile.label}功率`,
      type: 'line',
      smooth: 0.25,
      showSymbol: true,
      showAllSymbol: false,
      symbol: 'circle',
      symbolSize: 4,
      connectNulls: true,
      lineStyle: { color: profile.color, width: 2 },
      itemStyle: { color: profile.color, borderColor: '#08172c', borderWidth: 1 },
      emphasis: { scale: true, itemStyle: { borderColor: '#eef5ff', borderWidth: 2 } },
      data: mapPowerSeriesForReplay(profile.actualPower)
    })),
    {
      name: '总供电功率',
      type: 'line',
      smooth: 0.25,
      showSymbol: true,
      showAllSymbol: false,
      symbol: 'circle',
      symbolSize: 5,
      connectNulls: true,
      lineStyle: { color: '#3f82ff', width: 3 },
      itemStyle: { color: '#3f82ff', borderColor: '#08172c', borderWidth: 1 },
      emphasis: { scale: true, itemStyle: { borderColor: '#eef5ff', borderWidth: 2 } },
      data: mapPowerSeriesForReplay(totalSupplyPower.value),
      markArea: summary.value && timelineLabels.value.length > 0 ? {
        silent: true,
        data: buildPowerStageAreas()
      } : undefined,
      markLine: summary.value && timelineLabels.value.length > 0 ? {
        symbol: 'none',
        silent: true,
        lineStyle: { type: 'dashed', width: 1.5 },
        label: { show: true, formatter: '{b}', fontSize: 10 },
        data: buildPowerStageLines()
      } : undefined
    }
  ]
}))

const balanceOption = computed<EChartsOption>(() => ({
  color: ['#448cff'],
  tooltip: { trigger: 'axis', valueFormatter: (value) => `${Number(value)} kW` },
  grid: { top: 18, left: 45, right: 18, bottom: 42 },
  xAxis: { type: 'category', data: timelineLabels.value, axisLabel: { color: '#7f9ab7', interval: 'auto', hideOverlap: true, showMinLabel: true, showMaxLabel: true, fontSize: 9 }, axisLine: { lineStyle: { color: 'rgba(100,145,190,.35)' } }, axisTick: { show: false } },
  yAxis: { type: 'value', min: -100, max: 40, axisLabel: { color: '#7f9ab7' }, splitLine: { lineStyle: { color: 'rgba(100,145,190,.12)' } } },
  series: [{ name: '供需误差', type: 'line', smooth: true, showSymbol: false, areaStyle: { opacity: 0.08 }, data: balanceError.value, markLine: { symbol: 'none', silent: true, label: { show: false }, lineStyle: { color: '#5edaa1', opacity: 0.5 }, data: [{ yAxis: 0 }] } }]
}))

const resourceExecutionOption = computed<EChartsOption>(() => ({
  color: ['#748ba6', selectedResource.value?.color ?? '#4f8cff'],
  tooltip: { trigger: 'axis', valueFormatter: (value) => `${Number(value)} kW` },
  legend: { top: 0, right: 4, textStyle: { color: '#8fa9c4', fontSize: 10 }, data: ['可用容量', '实际功率'] },
  grid: { top: 38, left: 45, right: 18, bottom: 42 },
  xAxis: { type: 'category', data: timelineLabels.value, axisLabel: { color: '#7f9ab7', interval: 'auto', hideOverlap: true, showMinLabel: true, showMaxLabel: true, fontSize: 9 }, axisLine: { lineStyle: { color: 'rgba(100,145,190,.35)' } }, axisTick: { show: false } },
  yAxis: { type: 'value', axisLabel: { color: '#7f9ab7' }, splitLine: { lineStyle: { color: 'rgba(100,145,190,.12)' } } },
  series: selectedResource.value ? [
    { name: '可用容量', type: 'line', showSymbol: false, connectNulls: true, lineStyle: { type: 'dashed', width: 1.5 }, data: selectedResource.value.availableCapacity },
    { name: '实际功率', type: 'line', smooth: true, showSymbol: false, connectNulls: true, lineStyle: { width: 2.5 }, data: selectedResource.value.actualPower }
  ] : []
}))

function startReplay(): void {
  if (isPlaying.value) return
  if (timelineLabels.value.length === 0) return
  if (replayIndex.value >= timelineLabels.value.length - 1) replayIndex.value = 0
  isPlaying.value = true
  replayTimer = window.setInterval(() => {
    if (replayIndex.value >= timelineLabels.value.length - 1) {
      stopReplay()
      return
    }
    replayIndex.value += 1
  }, 700)
}

function stopReplay(): void {
  if (replayTimer !== undefined) {
    window.clearInterval(replayTimer)
    replayTimer = undefined
  }
  isPlaying.value = false
}

function resetReplay(): void {
  stopReplay()
  replayIndex.value = 0
}

async function loadExperimentData(runId: string): Promise<void> {
  loading.value = true
  loadError.value = ''
  resetReplay()
  try {
    const [nextSummary, nextTimeline, nextReplay, nextQualityMetrics] = await Promise.all([
      fetchExperimentSummary(runId),
      fetchExperimentTimeline(runId),
      fetchExperimentReplay(runId, undefined, 1000, true, true),
      fetchExperimentQualityMetrics(runId)
    ])
    summary.value = nextSummary
    timelinePoints.value = nextTimeline
    replayPoints.value = nextReplay
    qualityMetrics.value = nextQualityMetrics
    agentRun.value = nextSummary.agentRunId ? await fetchMarketAgentRun(nextSummary.agentRunId) : null
    selectedResourceCode.value = resourceProfiles.value[0]?.code ?? ''
    replayIndex.value = Math.max(0, timelineLabels.value.length - 1)
  } catch (error) {
    summary.value = null
    timelinePoints.value = []
    replayPoints.value = []
    agentRun.value = null
    qualityMetrics.value = null
    loadError.value = error instanceof Error ? error.message : '实验数据加载失败'
  } finally {
    loading.value = false
  }
}

async function loadExperimentRuns(): Promise<void> {
  loading.value = true
  loadError.value = ''
  try {
    experimentRuns.value = await fetchLatestExperimentRunItems(50)
    const requestedRunId = resolveRequestedRunId()
    if (requestedRunId !== '' && !experimentRuns.value.some((item) => item.runId === requestedRunId)) {
      const requestedRun = await fetchRequestedExperimentRun(requestedRunId)
      experimentRuns.value = [requestedRun, ...experimentRuns.value]
    }
    selectedExperimentId.value = experimentRuns.value.some((item) => item.runId === requestedRunId)
      ? requestedRunId
      : experimentRuns.value[0]?.runId ?? ''
  } catch (error) {
    loadError.value = error instanceof Error ? error.message : '实验记录加载失败'
  } finally {
    loading.value = false
  }
}

function resolveRequestedRunId(): string {
  const runId = route.query.runId
  return typeof runId === 'string' ? runId : ''
}

async function fetchRequestedExperimentRun(runId: string): Promise<ExperimentRunListItem> {
  try {
    return await fetchExperimentRun(runId)
  } catch {
    const requestedSummary = await fetchExperimentSummary(runId)
    return {
      runId: requestedSummary.runId,
      scenarioCode: requestedSummary.scenarioCode,
      scenarioName: requestedSummary.scenarioName,
      status: requestedSummary.status as ExperimentRun['status'],
      startedAt: requestedSummary.startedAt,
      endedAt: requestedSummary.stoppedAt
    }
  }
}

async function deleteSelectedExperiment(): Promise<void> {
  const experiment = selectedExperiment.value
  if (!experiment || deleting.value || !isExperimentDeletable(experiment)) return
  if (!window.confirm(`确定删除实验记录“${experiment.scenarioName}”吗？此操作不会删除原始遥测数据。`)) return
  deleting.value = true
  loadError.value = ''
  try {
    await deleteExperiment(experiment.runId)
    removeExperimentRuns([experiment.runId])
  } catch (error) {
    loadError.value = error instanceof Error ? error.message : '实验记录删除失败'
  } finally {
    deleting.value = false
  }
}

function isExperimentDeletable(experiment: ExperimentRun): boolean {
  return !['RUNNING', 'STARTING', 'INJECTING', 'FAULT_OBSERVED', 'ORCHESTRATING', 'RECOVERING'].includes(experiment.status)
}

function toggleBatchDeletePanel(): void {
  batchDeleteOpen.value = !batchDeleteOpen.value
  if (!batchDeleteOpen.value) {
    selectedBatchRunIds.value = []
  }
}

async function deleteBatchExperiments(): Promise<void> {
  const selectedRuns = batchDeletableExperiments.value.filter((experiment) => selectedBatchRunIds.value.includes(experiment.runId))
  if (selectedRuns.length === 0 || batchDeleting.value) return
  if (!window.confirm(`确定删除已选中的 ${selectedRuns.length} 条实验记录吗？此操作不会删除原始遥测数据。`)) return
  batchDeleting.value = true
  loadError.value = ''
  const deletedRunIds: string[] = []
  try {
    for (const experiment of selectedRuns) {
      await deleteExperiment(experiment.runId)
      deletedRunIds.push(experiment.runId)
    }
    removeExperimentRuns(deletedRunIds)
    batchDeleteOpen.value = false
    selectedBatchRunIds.value = []
  } catch (error) {
    removeExperimentRuns(deletedRunIds)
    loadError.value = error instanceof Error ? error.message : '批量删除实验记录失败'
  } finally {
    batchDeleting.value = false
  }
}

function removeExperimentRuns(runIds: string[]): void {
  const removedRunIds = new Set(runIds)
  experimentRuns.value = experimentRuns.value.filter((experiment) => !removedRunIds.has(experiment.runId))
  selectedBatchRunIds.value = selectedBatchRunIds.value.filter((runId) => !removedRunIds.has(runId))
  if (!selectedExperimentId.value || removedRunIds.has(selectedExperimentId.value)) {
    selectedExperimentId.value = experimentRuns.value[0]?.runId ?? ''
  }
  if (selectedExperimentId.value !== '') return
  summary.value = null
  qualityMetrics.value = null
  timelinePoints.value = []
  replayPoints.value = []
  agentRun.value = null
}

function formatTargetStationNames(stationIds: string[]): string {
  return stationIds.map((stationId) => chartResourceDefinitions.find((resource) => resource.code === stationId)?.label ?? stationId).join('、')
}

function formatTime(timestamp: string): string {
  return new Date(timestamp).toLocaleTimeString('zh-CN', { hour12: false })
}

function formatDateTime(timestamp: string | null | undefined): string {
  return timestamp ? new Date(timestamp).toLocaleString('zh-CN', { hour12: false }) : '--'
}

function formatExperimentStatus(status: ExperimentRun['status'] | undefined): string {
  const statusLabels: Record<string, string> = {
    STARTING: '启动中',
    INJECTING: '注入中',
    FAULT_OBSERVED: '故障已观测',
    ORCHESTRATING: '智能体调度中',
    RECOVERING: '恢复中',
    COMPLETED: '已完成',
    STOPPED: '已停止',
    FAILED: '失败',
    INJECTION_NOT_OBSERVED: '未观测到故障'
  }
  return status === undefined ? '--' : statusLabels[status] ?? '未知状态'
}

function formatControlStrategy(strategy: string | null | undefined): string {
  const strategyLabels: Record<string, string> = {
    CENTRALIZED: '集中式控制',
    FIXED_WEIGHT: '固定权重控制',
    ADAPTIVE: '自适应控制'
  }
  return strategy === null || strategy === undefined ? '--' : strategyLabels[strategy] ?? '其他控制策略'
}

function formatControllerType(controllerType: string | null | undefined): string {
  const controllerLabels: Record<string, string> = {
    FIXED_WEIGHT_CONSENSUS: '固定权重共识控制器',
    ADAPTIVE_CONSENSUS: '自适应共识控制器',
    OR_TOOLS_MILP: 'OR-Tools 混合整数规划控制器',
    OR_TOOLS_SCIP: 'OR-Tools SCIP 控制器'
  }
  return controllerType === null || controllerType === undefined ? '--' : controllerLabels[controllerType] ?? '其他控制器'
}

function formatNumber(value: number | null | undefined, fractionDigits = 2): string {
  if (value === null || value === undefined || !Number.isFinite(value)) return '--'
  return Number(value.toFixed(fractionDigits)).toString()
}

function formatPowerValue(value: unknown): string {
  const numericValue = Number(value)
  return Number.isFinite(numericValue) ? `${numericValue.toFixed(0)} kW` : '--'
}

function formatPowerTooltip(parameters: unknown): string {
  const items = Array.isArray(parameters) ? parameters as Array<{ axisValue?: unknown; marker?: string; seriesName?: string; value?: unknown }> : []
  const timestamp = String(items[0]?.axisValue ?? '--')
  const values = items
    .filter((item) => item.value !== null && item.value !== undefined)
    .map((item) => `${item.marker ?? ''}${item.seriesName ?? '--'}：${formatPowerValue(item.value)}`)
  return [`真实时间：${timestamp}`, ...values].join('<br/>')
}

function mapPowerSeriesForReplay(values: Array<number | null>): Array<number | null> {
  return values.map((value, index) => index <= powerChartEndIndex.value ? value : null)
}

function normalizeTimestamp(timestamp: string): string {
  const timestampValue = new Date(timestamp).getTime()
  return new Date(Math.floor(timestampValue / 1000) * 1000).toISOString()
}

function toFiniteNumber(value: number | undefined): number | null {
  return typeof value === 'number' && Number.isFinite(value) ? value : null
}

function isLoadStation(stationId: string): boolean {
  return stationId.includes('load') || stationId.includes('charge')
}

function sumAt(profiles: ResourceProfile[], index: number): number | null {
  const values = profiles
    .map((profile) => profile.actualPower[index])
    .filter((value): value is number => value !== null && Number.isFinite(value))
    .map((value) => Math.max(0, value))
  return values.length === 0 ? null : values.reduce((total, value) => total + value, 0)
}

function toAgentDecision(decision: MarketAgentDecision): AgentDecision {
  const status = resolveAgentStatus(decision)
  return {
    id: decision.decisionId,
    time: formatDecisionTime(decision.generatedAt),
    phase: agentPhaseMap[decision.agentType] ?? decision.agentType,
    agentName: agentNameMap[decision.agentType] ?? decision.agentType,
    operation: resolveAgentOperation(decision),
    retryCount: decision.retryCount ?? 0,
    evidenceCount: decision.evidenceRefs.length,
    status: status.label,
    badge: status.badge,
    completed: status.completed,
    tone: status.tone
  }
}

function resolveAgentStatus(decision: MarketAgentDecision): { label: string; badge: string; completed: boolean; tone: AgentDecision['tone'] } {
  if (decision.stageStatus === 'SUCCESS') return { label: '成功', badge: '已执行', completed: true, tone: 'green' }
  if (decision.stageStatus === 'RULE_FALLBACK') return { label: '规则回退', badge: '规则回退', completed: true, tone: 'orange' }
  if (decision.stageStatus === 'BLOCKED') return { label: '阻断', badge: '已阻断', completed: false, tone: 'red' }
  if (decision.stageStatus === 'FAILED') return { label: '失败', badge: '失败', completed: false, tone: 'red' }
  if (decision.stageStatus === 'RETRYING') return { label: '重试中', badge: '重试中', completed: false, tone: 'blue' }
  const statusLabel = decision.stageStatus ?? '待执行'
  return { label: statusLabel, badge: statusLabel, completed: false, tone: 'cyan' }
}

function resolveAgentOperation(decision: MarketAgentDecision): string {
  const actionDescription = decision.candidateActions
    .map((action) => agentActionMap[action] ?? action)
    .join('、')
  const explanation = conciseExplanation(decision.explanation)
  const providerFailure = formatProviderFailure(decision)
  const operation = !actionDescription
    ? explanation
    : !explanation
      ? `${actionDescription}。`
      : `${actionDescription}；${explanation}`
  return providerFailure === '' ? operation : `${operation}；调用详情：${providerFailure}`
}

function formatProviderFailure(decision: MarketAgentDecision): string {
  const details: string[] = []
  if (decision.providerStatusCode !== null && decision.providerStatusCode !== undefined) {
    details.push(`HTTP ${decision.providerStatusCode}`)
  }
  if (decision.providerErrorCode?.trim()) {
    details.push(`错误码 ${decision.providerErrorCode.trim()}`)
  }
  if (decision.providerErrorMessage?.trim()) {
    details.push(decision.providerErrorMessage.trim())
  }
  return details.join(' · ')
}

function conciseExplanation(explanation: string): string {
  const primaryExplanation = explanation.split('；上游输入：', 1)[0]?.trim() ?? ''
  return primaryExplanation.replace(/。{2,}$/u, '。')
}

function formatDecisionTime(timestamp: string): string {
  return new Date(timestamp).toLocaleTimeString('zh-CN', {
    hour12: false,
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    fractionalSecondDigits: 3
  })
}

function resolvePowerStageBoundaries(): { injectionIndex: number; orchestrationIndex: number; recoveryIndex: number } {
  const finalIndex = Math.max(0, timelineTimestamps.value.length - 1)
  const injectionIndex = findTimelineIndex(summary.value?.startedAt, 0)
  const orchestrationIndex = Math.max(
    injectionIndex,
    findTimelineIndex(summary.value?.orchestrationTriggeredAt ?? firstFaultTimestamp.value, injectionIndex)
  )
  const recoveryTimestamp = summary.value?.orchestrationCompletedAt ?? firstRecoveryTimestamp.value ?? summary.value?.stoppedAt
  const recoveryIndex = Math.min(finalIndex, Math.max(orchestrationIndex, findTimelineIndex(recoveryTimestamp, orchestrationIndex)))
  return { injectionIndex, orchestrationIndex, recoveryIndex }
}

function findTimelineIndex(timestamp: string | null | undefined, fallbackIndex: number): number {
  if (!timestamp || timelineTimestamps.value.length === 0) return fallbackIndex
  const targetTimestamp = new Date(timestamp).getTime()
  if (!Number.isFinite(targetTimestamp)) return fallbackIndex
  const index = timelineTimestamps.value.findIndex((item) => new Date(item).getTime() >= targetTimestamp)
  return index === -1 ? timelineTimestamps.value.length - 1 : index
}

function buildPowerStageAreas(): Array<Array<Record<string, unknown>>> {
  if (!summary.value || timelineTimestamps.value.length === 0) return []
  const boundaries = resolvePowerStageBoundaries()
  const firstLabel = timelineLabels.value[0]
  const injectionLabel = timelineLabels.value[boundaries.injectionIndex]
  const orchestrationLabel = timelineLabels.value[boundaries.orchestrationIndex]
  const recoveryLabel = timelineLabels.value[boundaries.recoveryIndex]
  const lastLabel = timelineLabels.value[timelineLabels.value.length - 1]
  return [
    [{ name: '正常数据', xAxis: firstLabel, itemStyle: { color: 'rgba(78, 140, 247, 0.06)' } }, { xAxis: injectionLabel }],
    [{ name: '故障注入', xAxis: injectionLabel, itemStyle: { color: 'rgba(239, 111, 117, 0.08)' } }, { xAxis: orchestrationLabel }],
    [{ name: '智能体调控', xAxis: orchestrationLabel, itemStyle: { color: 'rgba(245, 163, 64, 0.08)' } }, { xAxis: recoveryLabel }],
    [{ name: '恢复正常', xAxis: recoveryLabel, itemStyle: { color: 'rgba(98, 217, 143, 0.06)' } }, { xAxis: lastLabel }]
  ]
}

function buildPowerStageLines(): Array<Record<string, unknown>> {
  if (!summary.value || timelineTimestamps.value.length === 0) return []
  const boundaries = resolvePowerStageBoundaries()
  return [
    { name: '故障注入', xAxis: timelineLabels.value[boundaries.injectionIndex], lineStyle: { color: '#ef6f75' }, label: { color: '#ff9b9f' } },
    { name: '智能体调控', xAxis: timelineLabels.value[boundaries.orchestrationIndex], lineStyle: { color: '#f5a340' }, label: { color: '#ffd16f' } },
    { name: '恢复正常', xAxis: timelineLabels.value[boundaries.recoveryIndex], lineStyle: { color: '#62d98f' }, label: { color: '#8aefaa' } }
  ]
}

watch(selectedExperimentId, (runId) => {
  if (runId) void loadExperimentData(runId)
})
watch(() => route.query.runId, (runId) => {
  if (typeof runId === 'string' && experimentRuns.value.some((item) => item.runId === runId)) {
    selectedExperimentId.value = runId
  }
})
onMounted(() => {
  restoreExperimentNames()
  void loadExperimentRuns()
})
onBeforeUnmount(stopReplay)

function restoreExperimentNames(): void {
  const storedQueueItems = window.localStorage.getItem('weak-signal-fault-queue')
  if (storedQueueItems === null) return
  try {
    const queueItems = JSON.parse(storedQueueItems) as StoredExperimentName[]
    experimentNames.value = Object.fromEntries(queueItems
      .filter((item) => item.experimentRunId !== undefined && item.experimentName?.trim())
      .map((item) => [item.experimentRunId as string, item.experimentName!.trim()]))
  } catch {
    experimentNames.value = {}
  }
}

function getExperimentName(experiment: ExperimentRun): string {
  return experimentNames.value[experiment.runId] ?? experiment.scenarioName
}

function selectExperiment(experimentId: string): void {
  selectedExperimentId.value = experimentId
  experimentSelectorOpen.value = false
}
</script>

<template>
  <section class="analysis-screen page-shell">
    <header class="analysis-header">
      <div class="analysis-header__title">
        <span class="analysis-header__logo"><DataAnalysis /></span>
        <div><h1>实验结果分析</h1><p>故障影响 · 智能体决策 · 调度执行 · 恢复验证</p></div>
      </div>
      <div class="analysis-header__actions">
        <span class="data-badge">后端实验数据</span>
        <button class="experiment-selector-trigger" type="button" @click="experimentSelectorOpen = true">
          <span>当前实验</span>
          <strong>{{ selectedExperimentName }}</strong>
        </button>
        <button class="batch-delete-toggle" type="button" :disabled="batchDeletableExperiments.length === 0 || batchDeleting" @click="toggleBatchDeletePanel">
          {{ batchDeleteOpen ? '收起批量删除' : '批量删除' }}
        </button>
        <button class="delete-experiment-button" type="button" :disabled="!selectedExperiment || !isExperimentDeletable(selectedExperiment) || deleting" @click="deleteSelectedExperiment">
          {{ deleting ? '删除中…' : '删除记录' }}
        </button>
        <RouterLink to="/fault-lab"><Back />返回实时注入</RouterLink>
      </div>
    </header>

    <section v-if="batchDeleteOpen" class="batch-delete-panel analysis-panel" aria-label="批量删除实验记录">
      <header>
        <div><span>实验记录</span><h2>批量删除</h2></div>
        <button class="delete-experiment-button" type="button" :disabled="selectedBatchRunIds.length === 0 || batchDeleting" @click="deleteBatchExperiments">
          {{ batchDeleting ? '删除中…' : `删除已选 ${selectedBatchRunIds.length} 条` }}
        </button>
      </header>
      <div class="batch-delete-list">
        <label v-for="experiment in batchDeletableExperiments" :key="experiment.runId">
          <input v-model="selectedBatchRunIds" type="checkbox" :value="experiment.runId" />
          <span><strong>{{ experiment.scenarioName }}</strong><small>{{ formatDateTime(experiment.startedAt) }} · {{ experiment.status }}</small></span>
        </label>
      </div>
    </section>

    <p v-if="loading" class="data-state analysis-panel">正在加载后端实验数据…</p>
    <p v-if="loadError" class="data-state analysis-panel is-error">操作未完成：{{ loadError }}</p>
    <p v-if="!loading && !selectedExperiment" class="data-state analysis-panel">暂无已保存的真实实验记录。</p>

    <section v-if="selectedExperiment" class="experiment-meta analysis-panel">
      <div><span>实验名称</span><button class="experiment-meta__selector" type="button" @click="experimentSelectorOpen = true">{{ selectedExperimentName }}</button><small>实验场景：{{ selectedExperiment.scenarioName }}</small></div>
      <div><span>开始时间</span><strong>{{ formatDateTime(selectedExperiment.startedAt) }}</strong><small>主链路状态：{{ summary?.dominantState ?? '--' }}</small></div>
      <div><span>持续时间</span><strong>{{ summary?.durationSeconds ?? 0 }} 秒</strong><small>遥测样本：{{ summary?.sampleCount ?? 0 }}</small></div>
      <div><span>目标场站</span><strong>{{ formatTargetStationNames(summary?.targetStationIds ?? []) || '--' }}</strong><small>{{ selectedExperiment.runId }}</small></div>
      <div class="experiment-meta__status"><CircleCheckFilled /><span><strong>实验状态</strong><small>{{ selectedExperiment.status }}</small></span><button class="delete-experiment-button is-compact" type="button" :disabled="!isExperimentDeletable(selectedExperiment) || deleting" @click="deleteSelectedExperiment">删除</button></div>
    </section>

    <section class="metric-grid" aria-label="调度结果指标">
      <article v-for="metric in metrics" :key="metric.label" class="metric-card analysis-panel" :class="`is-${metric.tone}`">
        <span class="metric-card__icon"><component :is="metric.icon" /></span>
        <div><span>{{ metric.label }}</span><strong>{{ metric.value }} <small>{{ metric.unit }}</small></strong></div>
      </article>
    </section>

    <div v-if="experimentSelectorOpen" class="experiment-selector-dialog" role="dialog" aria-modal="true" aria-labelledby="experiment-selector-title" @click.self="experimentSelectorOpen = false">
      <section class="experiment-selector-dialog__content">
        <header>
          <div><span>HISTORY EXPERIMENTS</span><h2 id="experiment-selector-title">选择历史实验</h2><p>按实验名称选择需要查看的结果记录</p></div>
          <button type="button" aria-label="关闭实验选择" @click="experimentSelectorOpen = false">×</button>
        </header>
        <div class="experiment-selector-list">
          <button
            v-for="experiment in experimentRuns"
            :key="experiment.runId"
            type="button"
            :class="{ 'is-active': experiment.runId === selectedExperimentId }"
            @click="selectExperiment(experiment.runId)"
          >
            <span class="experiment-selector-list__mark"><Clock /></span>
            <span class="experiment-selector-list__content">
              <strong>{{ getExperimentName(experiment) }}</strong>
              <small>{{ experiment.scenarioName }} · {{ formatDateTime(experiment.startedAt) }}</small>
            </span>
            <em>{{ experiment.status }}</em>
          </button>
          <p v-if="experimentRuns.length === 0">暂无历史实验记录</p>
        </div>
      </section>
    </div>

    <section class="stage-strip analysis-panel">
      <article v-for="(stage, index) in stages" :key="stage.label" :class="{ 'is-active': currentStage?.label === stage.label, 'is-completed': replayIndex >= stage.startIndex }">
        <span>{{ String(index + 1).padStart(2, '0') }}</span><div><strong>{{ stage.label }}</strong><small>{{ stage.description }}</small></div>
      </article>
    </section>

    <section class="analysis-main-grid">
      <article class="main-chart analysis-panel">
        <header class="panel-header">
          <div><span>核心结果</span><h2>多资源功率调度曲线</h2></div>
          <label class="power-chart-window">
            <span>时间窗口</span>
            <select v-model.number="powerChartWindowSize">
              <option :value="30">最近 30 点</option>
              <option :value="60">最近 60 点</option>
              <option :value="120">最近 120 点</option>
              <option :value="0">全部数据</option>
            </select>
          </label>
        </header>
        <EChartPanel :option="mainPowerOption" height="410px" />
        <div class="replay-controller">
          <button type="button" :aria-label="isPlaying ? '暂停回放' : '开始回放'" @click="isPlaying ? stopReplay() : startReplay()"><VideoPause v-if="isPlaying" /><VideoPlay v-else /></button>
          <button type="button" aria-label="重置回放" @click="resetReplay"><RefreshRight /></button>
          <div class="replay-controller__time"><strong>{{ timelineLabels[replayIndex] ?? '--' }}</strong><span>{{ currentStage?.label ?? '--' }}</span></div>
          <input v-model.number="replayIndex" type="range" min="0" :max="Math.max(0, timelineLabels.length - 1)" step="1" :disabled="timelineLabels.length === 0" />
          <span class="replay-controller__progress">{{ timelineLabels.length > 1 ? Math.round((replayIndex / (timelineLabels.length - 1)) * 100) : 0 }}%</span>
        </div>
      </article>

      <aside class="decision-panel analysis-panel">
        <header class="panel-header"><div><span>决策证据</span><h2>智能体协作流程</h2></div><Monitor /></header>
        <div class="decision-list">
          <article v-for="(decision, index) in decisions" :key="decision.id" :class="`is-${decision.tone}`">
            <span class="decision-list__number">{{ index + 1 }}</span>
            <div class="decision-list__content">
              <div class="decision-list__heading">
                <div><strong>{{ decision.phase }}</strong><span>{{ decision.agentName }}</span></div>
                <em>{{ decision.badge }}</em>
              </div>
              <p :title="decision.operation">阶段 {{ decision.status }} · 重试 {{ decision.retryCount }} 次；{{ decision.operation }}</p>
              <footer><span>证据 {{ decision.evidenceCount }} 条</span><time>{{ decision.time }}</time></footer>
            </div>
          </article>
          <p v-if="decisions.length === 0" class="decision-list__empty">当前实验尚未生成多智能体运行记录。</p>
        </div>
        <footer class="decision-result" :class="{ 'is-completed': agentRun?.safetyDecision.passed, 'is-rejected': agentRun && !agentRun.safetyDecision.passed }">
          <CircleCheckFilled /><div><strong>{{ decisionResultTitle }}</strong><span>{{ decisionResultDetail }}</span></div>
        </footer>
      </aside>
    </section>

    <section class="analysis-lower-grid">
      <article class="small-chart analysis-panel">
        <header class="panel-header"><div><span>供需平衡</span><h2>功率缺口与恢复过程</h2></div><Aim /></header>
        <EChartPanel :option="balanceOption" height="230px" />
      </article>
      <article class="small-chart analysis-panel">
        <header class="panel-header">
          <div><span>执行跟踪</span><h2>{{ selectedResource?.label ?? '资源' }}可用容量与实际功率</h2></div>
          <div class="resource-tabs"><button v-for="resource in resourceProfiles" :key="resource.code" type="button" :class="{ 'is-active': selectedResourceCode === resource.code }" @click="selectedResourceCode = resource.code">{{ resource.label }}</button></div>
        </header>
        <EChartPanel :option="resourceExecutionOption" height="230px" />
      </article>
    </section>

    <section class="conclusion-panel analysis-panel">
      <div class="conclusion-panel__mark"><CircleCheckFilled /><strong>数据说明</strong></div>
      <div class="conclusion-panel__content"><span>实验结果</span><p>本页面仅展示后端实验运行记录、遥测回放、链路状态时间线与统计接口返回的数据。缺少的计划功率、控制指令或对照实验数据不会以演示数组补全。</p></div>
      <dl>
        <div><dt>记录状态</dt><dd>{{ formatExperimentStatus(selectedExperiment?.status) }}</dd></div>
        <div><dt>控制策略</dt><dd>{{ formatControlStrategy(summary?.controlStrategy) }}</dd></div>
        <div><dt>真实控制器</dt><dd>{{ formatControllerType(summary?.controllerType) }}</dd></div>
        <div><dt>计划 / 实际误差</dt><dd>{{ formatNumber(summary?.plannedBalanceErrorKw ?? null, 1) }} / {{ formatNumber(summary?.actualBalanceErrorKw ?? null, 1) }} kW</dd></div>
        <div><dt>命令回执</dt><dd>{{ summary?.commandExecutedCount ?? 0 }}/{{ summary?.commandTotalCount ?? 0 }} 已执行</dd></div>
        <div><dt>自治 / 重入图证据</dt><dd>{{ Object.keys(summary?.localAutonomyEvidence ?? {}).length }} 个场站</dd></div>
      </dl>
    </section>
  </section>
</template>

<style scoped>
.analysis-screen {
  --background: linear-gradient(180deg, #06164f 0%, #041141 24%, #020a25 100%);
  --panel: linear-gradient(180deg, rgba(8, 34, 128, 0.8), rgba(4, 24, 95, 0.84));
  --line: rgba(72, 224, 255, 0.24);
  --line-soft: rgba(72, 224, 255, 0.16);
  --text: #e7f7ff;
  --muted: #8bc8ff;
  --cyan: #42e8ff;
  --blue: #1f80ff;
  --orange: #ff9d3f;
  --green: #62ffb6;
  display: grid;
  gap: 16px;
  min-height: 100vh;
  padding: 0;
  color: var(--text);
  background: var(--background);
  font-family: "Microsoft YaHei", "PingFang SC", sans-serif;
}

.analysis-panel,
.analysis-header { border: 1px solid var(--line); border-radius: 8px; background: var(--panel); }
.analysis-header { display: flex; align-items: center; justify-content: space-between; gap: 18px; min-height: 64px; padding: 10px 14px; }
.analysis-header__title,
.analysis-header__actions,
.experiment-meta__status,
.panel-header,
.replay-controller,
.conclusion-panel,
.conclusion-panel__mark { display: flex; align-items: center; }
.analysis-header__title { gap: 12px; min-width: 0; }
.analysis-header__logo { display: grid; place-items: center; width: 38px; height: 38px; flex: 0 0 auto; color: #75aaff; border-radius: 7px; background: #0c264b; }
.analysis-header__logo svg { width: 22px; }
.analysis-header h1 { margin: 0; font-size: 22px; letter-spacing: 0.02em; }
.analysis-header p { margin: 3px 0 0; color: var(--muted); font-size: 11px; }
.analysis-header__actions { gap: 9px; }
.experiment-selector-trigger { display: grid; min-width: 210px; height: 38px; padding: 5px 30px 5px 10px; color: var(--text); border: 1px solid var(--line); border-radius: 6px; background: #071426; cursor: pointer; text-align: left; }
.experiment-selector-trigger span { color: #7089a2; font-size: 8px; }
.experiment-selector-trigger strong { overflow: hidden; font-size: 11px; text-overflow: ellipsis; white-space: nowrap; }
.analysis-header__actions a { display: flex; align-items: center; gap: 5px; height: 34px; padding: 0 10px; color: #a9bdd4; border: 1px solid var(--line); border-radius: 6px; font-size: 11px; }
.analysis-header__actions a svg { width: 13px; }
.data-badge { padding: 5px 8px; color: #f0bd68; border: 1px solid rgba(245, 163, 64, 0.34); border-radius: 5px; background: rgba(245, 163, 64, 0.08); font-size: 9px; }
.delete-experiment-button { height: 34px; padding: 0 10px; color: #ff9b95; border: 1px solid rgba(240, 108, 96, 0.5); border-radius: 6px; background: rgba(240, 108, 96, 0.08); font-size: 11px; cursor: pointer; }
.delete-experiment-button:disabled { color: #607991; border-color: var(--line-soft); background: transparent; cursor: not-allowed; }
.delete-experiment-button.is-compact { height: 24px; padding: 0 7px; font-size: 9px; }
.batch-delete-toggle { height: 34px; padding: 0 10px; color: #f0bd68; border: 1px solid rgba(245, 163, 64, 0.42); border-radius: 6px; background: rgba(245, 163, 64, 0.08); font-size: 11px; cursor: pointer; }
.batch-delete-toggle:disabled { color: #607991; border-color: var(--line-soft); background: transparent; cursor: not-allowed; }
.batch-delete-panel { padding: 12px 14px; }
.batch-delete-panel > header { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.batch-delete-panel header span { color: #6f8fae; font-size: 8px; letter-spacing: 0.12em; }
.batch-delete-panel h2 { margin: 3px 0 0; font-size: 13px; }
.batch-delete-list { display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 8px; margin-top: 10px; }
.batch-delete-list label { display: flex; align-items: center; gap: 9px; min-width: 0; padding: 8px 10px; border: 1px solid var(--line-soft); border-radius: 5px; background: #061326; cursor: pointer; }
.batch-delete-list input { width: 14px; height: 14px; flex: 0 0 auto; accent-color: #ef7474; }
.batch-delete-list span { display: grid; min-width: 0; }
.batch-delete-list strong,
.batch-delete-list small { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.batch-delete-list strong { color: var(--text); font-size: 10px; }
.batch-delete-list small { margin-top: 3px; color: #7089a2; font-size: 8px; }

.experiment-meta { display: grid; grid-template-columns: 1.2fr 0.8fr 0.7fr 1.15fr auto; min-width: 0; }
.experiment-meta > div { display: grid; gap: 4px; align-content: center; min-width: 0; min-height: 64px; padding: 9px 14px; border-right: 1px solid var(--line-soft); }
.experiment-meta > div:last-child { border-right: 0; }
.experiment-meta span { color: var(--muted); font-size: 9px; }
.experiment-meta strong { overflow: hidden; font-size: 11px; text-overflow: ellipsis; white-space: nowrap; }
.experiment-meta__selector { overflow: hidden; min-width: 0; padding: 0; color: var(--text); border: 0; background: transparent; cursor: pointer; font-size: 11px; font-weight: 700; text-align: left; text-overflow: ellipsis; white-space: nowrap; }
.experiment-meta small { overflow: hidden; color: #6f89a4; font-size: 8px; text-overflow: ellipsis; white-space: nowrap; }
.experiment-meta__status { grid-template-columns: auto 1fr; gap: 8px !important; min-width: 120px !important; color: var(--green); }
.experiment-meta__status:has(.delete-experiment-button) { grid-template-columns: auto 1fr auto; }
.experiment-meta__status > svg { width: 24px; }
.experiment-meta__status span { display: grid; gap: 2px; }
.experiment-meta__status strong { color: var(--green); font-size: 13px; }

.experiment-selector-dialog { position: fixed; z-index: 30; inset: 0; display: grid; place-items: center; padding: 24px; background: rgba(2, 8, 22, 0.78); backdrop-filter: blur(4px); }
.experiment-selector-dialog__content { display: grid; grid-template-rows: auto minmax(0, 1fr); width: min(860px, 100%); max-height: min(720px, calc(100vh - 48px)); overflow: hidden; border: 1px solid rgba(76, 134, 247, 0.58); border-radius: 10px; background: linear-gradient(180deg, #09245f, #051737); box-shadow: 0 24px 70px rgba(0, 0, 0, 0.52); }
.experiment-selector-dialog__content > header { display: flex; align-items: flex-start; justify-content: space-between; gap: 20px; padding: 20px 22px; border-bottom: 1px solid var(--line-soft); }
.experiment-selector-dialog__content header span { color: var(--cyan); font-size: 8px; font-weight: 700; letter-spacing: 0.16em; }
.experiment-selector-dialog__content h2 { margin: 5px 0 0; font-size: 20px; }
.experiment-selector-dialog__content header p { margin: 5px 0 0; color: var(--muted); font-size: 10px; }
.experiment-selector-dialog__content header > button { width: 32px; height: 32px; color: #9eb5cd; border: 1px solid var(--line); border-radius: 6px; background: #071426; cursor: pointer; font-size: 20px; line-height: 1; }
.experiment-selector-list { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); align-content: start; gap: 10px; overflow-y: auto; padding: 16px; }
.experiment-selector-list > button { display: grid; grid-template-columns: 36px minmax(0, 1fr) auto; align-items: center; gap: 10px; min-width: 0; padding: 12px; color: var(--text); border: 1px solid var(--line-soft); border-radius: 7px; background: #061326; cursor: pointer; text-align: left; }
.experiment-selector-list > button:hover { border-color: rgba(76, 134, 247, 0.68); background: #0a2349; }
.experiment-selector-list > button.is-active { border-color: var(--cyan); background: rgba(76, 134, 247, 0.18); box-shadow: inset 3px 0 0 var(--cyan); }
.experiment-selector-list__mark { display: grid; place-items: center; width: 34px; height: 34px; color: var(--cyan); border-radius: 6px; background: rgba(76, 134, 247, 0.14); }
.experiment-selector-list__mark svg { width: 17px; }
.experiment-selector-list__content { display: grid; min-width: 0; gap: 5px; }
.experiment-selector-list__content strong,
.experiment-selector-list__content small { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.experiment-selector-list__content strong { font-size: 12px; }
.experiment-selector-list__content small { color: #7892ad; font-size: 8px; }
.experiment-selector-list em { padding: 4px 6px; color: var(--green); border: 1px solid rgba(98, 255, 182, 0.24); border-radius: 4px; background: rgba(98, 255, 182, 0.08); font-size: 8px; font-style: normal; white-space: nowrap; }
.experiment-selector-list > p { grid-column: 1 / -1; margin: 0; padding: 50px 20px; color: #7089a2; text-align: center; }

.metric-grid { display: grid; grid-template-columns: repeat(9, minmax(0, 1fr)); gap: 8px; }
.metric-card { display: flex; align-items: center; gap: 8px; min-width: 0; min-height: 70px; padding: 9px; }
.metric-card__icon { display: grid; place-items: center; width: 30px; height: 30px; flex: 0 0 auto; color: var(--blue); border-radius: 7px; background: rgba(76, 134, 247, 0.1); }
.metric-card__icon svg { width: 17px; }
.metric-card div > span { display: block; overflow: hidden; color: var(--muted); font-size: 9px; text-overflow: ellipsis; white-space: nowrap; }
.metric-card strong { display: block; margin-top: 5px; color: var(--blue); font-size: 18px; line-height: 1; white-space: nowrap; }
.metric-card strong small { color: #91a8c0; font-size: 9px; font-weight: 500; }
.metric-card.is-cyan .metric-card__icon,
.metric-card.is-cyan strong { color: var(--cyan); }
.metric-card.is-orange .metric-card__icon,
.metric-card.is-orange strong { color: var(--orange); }
.metric-card.is-green .metric-card__icon,
.metric-card.is-green strong { color: var(--green); }

.stage-strip { display: grid; grid-template-columns: repeat(7, minmax(0, 1fr)); overflow: hidden; }
.stage-strip article { display: grid; grid-template-columns: auto 1fr; align-items: center; gap: 7px; min-width: 0; min-height: 58px; padding: 8px 10px; color: #657f99; border-right: 1px solid var(--line-soft); }
.stage-strip article:last-child { border-right: 0; }
.stage-strip article > span { display: grid; place-items: center; width: 23px; height: 23px; border-radius: 50%; background: #0d2647; font-size: 8px; }
.stage-strip strong { display: block; font-size: 9px; }
.stage-strip small { display: block; margin-top: 2px; overflow: hidden; font-size: 7px; text-overflow: ellipsis; white-space: nowrap; }
.stage-strip article.is-completed { color: #aac0d5; }
.stage-strip article.is-completed > span { color: #07131f; background: var(--green); }
.stage-strip article.is-active { color: var(--text); background: rgba(76, 134, 247, 0.08); box-shadow: inset 0 -2px 0 var(--blue); }

.analysis-main-grid { display: grid; grid-template-columns: minmax(0, 2.15fr) minmax(280px, 0.95fr); gap: 12px; }
.main-chart,
.decision-panel,
.small-chart { min-width: 0; padding: 12px; }
.panel-header { justify-content: space-between; gap: 12px; min-height: 38px; margin-bottom: 4px; }
.panel-header span { color: #6f8fae; font-size: 8px; letter-spacing: 0.12em; }
.panel-header h2 { margin: 3px 0 0; font-size: 13px; }
.panel-header > svg { width: 18px; color: var(--blue); }
.power-chart-window { display: flex; align-items: center; gap: 7px; }
.power-chart-window span { letter-spacing: 0; }
.power-chart-window select { height: 28px; padding: 0 24px 0 8px; color: #b8cadc; border: 1px solid var(--line); border-radius: 5px; background: #071426; font-size: 9px; }
.counterfactual-switch { display: flex; align-items: center; gap: 6px; color: #8ea6c0; font-size: 9px; cursor: pointer; }
.counterfactual-switch input { accent-color: var(--blue); }
.replay-controller { gap: 8px; min-height: 42px; padding: 7px 9px; border-top: 1px solid var(--line-soft); background: #061326; }
.replay-controller button { display: grid; place-items: center; width: 28px; height: 28px; padding: 0; color: #a9c3de; border: 1px solid var(--line); border-radius: 5px; background: #0a1d38; cursor: pointer; }
.replay-controller button:first-child { color: #06131f; border-color: var(--cyan); background: var(--cyan); }
.replay-controller button svg { width: 13px; }
.replay-controller__time { display: grid; min-width: 82px; }
.replay-controller__time strong { font-size: 11px; }
.replay-controller__time span { color: var(--cyan); font-size: 8px; }
.replay-controller input { min-width: 80px; flex: 1; accent-color: var(--blue); }
.replay-controller__progress { color: #7f98b3; font-size: 9px; }

.decision-panel { display: flex; flex-direction: column; }
.decision-list { display: grid; align-content: start; min-height: 0; flex: 1; overflow: auto; }
.decision-list article { display: grid; grid-template-columns: 34px minmax(0, 1fr); align-items: start; gap: 10px; padding: 13px 2px; border-bottom: 1px solid rgba(85, 145, 204, 0.22); }
.decision-list__number { display: grid; place-items: center; width: 32px; height: 32px; color: #fff; border-radius: 50%; background: #2682ff; font-size: 13px; font-weight: 700; }
.decision-list__content { min-width: 0; }
.decision-list__heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 8px; }
.decision-list__heading > div { min-width: 0; }
.decision-list__heading strong { display: block; color: var(--text); font-size: 12px; }
.decision-list__heading span { display: block; margin-top: 3px; color: #47bfff; font-size: 9px; }
.decision-list p { display: -webkit-box; overflow: hidden; margin: 7px 0; color: #a9c3df; font-size: 9px; line-height: 1.55; -webkit-box-orient: vertical; -webkit-line-clamp: 3; }
.decision-list footer { display: flex; align-items: center; justify-content: space-between; gap: 8px; color: #6f8ca9; font-size: 8px; }
.decision-list time { font-variant-numeric: tabular-nums; }
.decision-list em { flex: 0 0 auto; padding: 4px 6px; color: #8ea6c0; border: 1px solid var(--line-soft); border-radius: 4px; background: rgba(108, 139, 172, 0.08); font-size: 8px; font-style: normal; font-weight: 700; white-space: nowrap; }
.decision-list article.is-green em { color: #46e999; border-color: rgba(70, 233, 153, 0.24); background: rgba(18, 133, 94, 0.18); }
.decision-list article.is-orange em { color: #ffc260; border-color: rgba(255, 194, 96, 0.26); background: rgba(150, 100, 20, 0.18); }
.decision-list article.is-red em { color: #ff8585; border-color: rgba(255, 108, 108, 0.28); background: rgba(142, 50, 65, 0.2); }
.decision-list article.is-blue em { color: #75aaff; border-color: rgba(117, 170, 255, 0.26); background: rgba(48, 94, 165, 0.18); }
.decision-list__empty { align-self: center; margin: 0; padding: 28px 12px; color: #7188a1; font-size: 9px; text-align: center; }
.decision-result { display: flex; align-items: center; gap: 8px; margin-top: 8px; padding: 9px; color: #7891aa; border: 1px solid var(--line-soft); border-radius: 6px; background: #061326; }
.decision-result.is-completed { color: var(--green); border-color: rgba(98, 217, 143, 0.3); }
.decision-result.is-rejected { color: #ef7474; border-color: rgba(239, 116, 116, 0.3); }
.decision-result > svg { width: 22px; }
.decision-result strong { display: block; font-size: 12px; }
.decision-result span { display: block; margin-top: 2px; color: #7089a2; font-size: 8px; }

.analysis-lower-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; }
.resource-tabs { display: flex; flex-wrap: wrap; justify-content: flex-end; gap: 3px; }
.resource-tabs button { padding: 3px 6px; color: #7892ad; border: 1px solid var(--line-soft); border-radius: 4px; background: transparent; cursor: pointer; font-size: 8px; }
.resource-tabs button.is-active { color: var(--text); border-color: var(--blue); background: rgba(76, 134, 247, 0.16); }

.conclusion-panel { gap: 14px; padding: 12px 14px; }
.conclusion-panel__mark { flex: 0 0 auto; gap: 7px; padding-right: 14px; color: var(--green); border-right: 1px solid var(--line-soft); }
.conclusion-panel__mark svg { width: 28px; }
.conclusion-panel__mark strong { font-size: 15px; }
.conclusion-panel__content { min-width: 0; flex: 1; }
.conclusion-panel__content span { color: #6f8fae; font-size: 8px; letter-spacing: 0.12em; }
.conclusion-panel__content p { margin: 5px 0 0; color: #b5c7d8; font-size: 10px; line-height: 1.5; }
.conclusion-panel dl { display: grid; grid-template-columns: repeat(3, auto); gap: 10px; margin: 0; }
.conclusion-panel dl > div { min-width: 92px; padding: 7px 9px; border: 1px solid var(--line-soft); border-radius: 5px; background: #061326; }
.conclusion-panel dt { color: #758fa8; font-size: 8px; }
.conclusion-panel dd { margin: 3px 0 0; color: var(--green); font-size: 11px; font-weight: 700; }

@media (max-width: 1280px) {
  .metric-grid { grid-template-columns: repeat(5, minmax(0, 1fr)); }
  .analysis-main-grid { grid-template-columns: minmax(0, 1.65fr) minmax(280px, 0.85fr); }
  .analysis-lower-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}

@media (max-width: 900px) {
  .analysis-header { align-items: flex-start; flex-direction: column; }
  .analysis-header__actions { width: 100%; flex-wrap: wrap; }
  .experiment-selector-trigger { min-width: 0; flex: 1; }
  .batch-delete-list { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .experiment-meta { grid-template-columns: repeat(2, 1fr); }
  .experiment-meta > div { border-bottom: 1px solid var(--line-soft); }
  .experiment-meta__status { grid-column: 1 / -1; }
  .metric-grid { grid-template-columns: repeat(3, minmax(0, 1fr)); }
  .analysis-main-grid,
  .analysis-lower-grid { grid-template-columns: 1fr; }
  .analysis-lower-grid > article:last-child { grid-column: auto; }
  .stage-strip { grid-template-columns: repeat(4, 1fr); }
  .stage-strip article { border-bottom: 1px solid var(--line-soft); }
  .conclusion-panel { align-items: flex-start; flex-wrap: wrap; }
  .conclusion-panel dl { width: 100%; grid-template-columns: repeat(3, 1fr); }
}

@media (max-width: 620px) {
  .analysis-screen { padding: 8px; }
  .analysis-header__actions a { width: 100%; justify-content: center; }
  .experiment-meta,
  .metric-grid,
  .stage-strip { grid-template-columns: 1fr; }
  .batch-delete-list { grid-template-columns: 1fr; }
  .experiment-selector-dialog { padding: 12px; }
  .experiment-selector-list { grid-template-columns: 1fr; }
  .decision-list article { grid-template-columns: 34px minmax(0, 1fr); }
  .conclusion-panel dl { grid-template-columns: 1fr; }
}
</style>
