<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import type { EChartsOption } from 'echarts'
import { useRoute, useRouter } from 'vue-router'

import EChartPanel from '@/components/charts/EChartPanel.vue'
import StationDeviceDetailPanel from '@/components/stations/StationDeviceDetailPanel.vue'
import StationDeviceTopology from '@/components/stations/StationDeviceTopology.vue'
import {
  analyzeFault,
  executeDispatch,
  fetchLatestDispatchCommands,
  fetchStationDetail,
  fetchStationDeviceDetail,
  fetchStationDevicePowerTrend,
  fetchStationDeviceQualityTrend,
  fetchStationDevices,
  previewDispatch
} from '@/api/systemApi'
import {
  buildMockStationDeviceDetail,
  buildMockStationDevices,
  controlModeLabelMap,
  linkStateMetaMap,
  resourceDetailMap,
  resourceTypeLabelMap
} from '@/mock/systemData'
import { stationPowerMetricLabel, useSystemStore } from '@/stores/system'
import { ControlMode, type DispatchCommand, type DispatchPreview, type DispatchRequest, type FaultDiagnosisRun, type ResourceDetail, type StationDeviceDetail, type StationDevicePowerTrend, type StationDeviceQualityTrendPoint, type StationDeviceSnapshot } from '@/types/system'

const AUTO_REFRESH_INTERVAL_MS = 5000
const TREND_LIMIT = 120
const dataSource = 'api' as const

const route = useRoute()
const router = useRouter()
const systemStore = useSystemStore()

const stationId = computed(() => String(route.params.stationId ?? ''))
const station = computed(() => systemStore.getStationById(stationId.value))
const stationDetail = ref<ResourceDetail | null>(null)
const devices = ref<StationDeviceSnapshot[]>([])
const selectedDeviceId = ref('')
const selectedDeviceDetail = ref<StationDeviceDetail | null>(null)
const powerTrend = ref<StationDevicePowerTrend | null>(null)
const qualityTrend = ref<StationDeviceQualityTrendPoint[]>([])
const latestCommand = ref<DispatchCommand | null>(null)
const latestDiagnosis = ref<FaultDiagnosisRun | null>(null)
const dispatchPreviewResult = ref<DispatchPreview | null>(null)
const pageError = ref('')
const deviceError = ref('')
const trendError = ref('')
const controlError = ref('')
const isPageLoading = ref(false)
const isDeviceLoading = ref(false)
const isTrendLoading = ref(false)
const isSubmittingDispatch = ref(false)
const isSubmittingDiagnosis = ref(false)
const dispatchTargetPower = ref<number | null>(null)
const maximumRampRate = ref(8)
const commandTimeoutSeconds = ref(30)
const previewReady = ref(false)
const lastUpdatedText = ref('--')
const powerTrendSection = ref<HTMLElement>()
const qualityTrendSection = ref<HTMLElement>()
let refreshTimer: number | undefined
let pendingDetailRequestId = 0
let pendingTrendRequestId = 0
let loadedTrendDeviceId = ''
const mockTimers: number[] = []

const currentControlMode = computed(() => controlModeLabelMap[systemStore.currentControlMode])
const dataSourceLabel = computed(() => (dataSource === 'api' ? 'API' : 'MOCK'))
const stationLabel = computed(() => (station.value ? resourceTypeLabelMap[station.value.resourceType] : '资源场站'))
const pageTitle = computed(() => `${stationLabel.value}专题界面`)
const pageStateLabel = computed(() => (station.value ? linkStateMetaMap[station.value.linkState].label : '--'))
const stationPowerLabel = computed(() => (station.value ? stationPowerMetricLabel(station.value) : '实时功率'))
const stationDisplayPower = computed(() => Math.abs(station.value?.activePower ?? 0))
const selectedDevice = computed(() => devices.value.find((device) => device.deviceId === selectedDeviceId.value) ?? null)
const selectedTrendEmpty = computed(() => !powerTrend.value || powerTrend.value.points.length === 0)
const qualityTrendEmpty = computed(() => qualityTrend.value.length === 0)
const unsafeControlReason = computed(() => {
  if (!station.value || !selectedDeviceDetail.value) {
    return '请先加载设备数据'
  }
  if (!selectedDeviceDetail.value.controllable) {
    return '当前设备不支持功率下发'
  }
  if (!station.value.online || station.value.linkState === 'S3') {
    return '当前链路状态不允许中心功率下发'
  }
  if (station.value.dataAge > 10) {
    return '当前数据时效不足，已禁止不安全操作'
  }
  return ''
})

const dispatchInputError = computed(() => {
  if (unsafeControlReason.value) {
    return unsafeControlReason.value
  }
  if (!Number.isFinite(dispatchTargetPower.value)) {
    return '请输入有效的目标功率'
  }
  const minimumPower = selectedDeviceDetail.value?.minimumPower
  const maximumPower = selectedDeviceDetail.value?.maximumPower
  if (minimumPower === null || maximumPower === null || minimumPower === undefined || maximumPower === undefined) {
    return '当前设备未提供可下发功率范围'
  }
  if (dispatchTargetPower.value! < minimumPower || dispatchTargetPower.value! > maximumPower) {
    return `目标功率需在 ${minimumPower.toFixed(1)} - ${maximumPower.toFixed(1)} kW 范围内`
  }
  if (!Number.isFinite(maximumRampRate.value) || maximumRampRate.value <= 0) {
    return '最大爬坡速率必须大于零'
  }
  if (!Number.isInteger(commandTimeoutSeconds.value) || commandTimeoutSeconds.value < 5 || commandTimeoutSeconds.value > 300) {
    return '指令超时需为 5 - 300 秒的整数'
  }
  return ''
})

const latestCommandStatusText = computed(() => {
  if (!latestCommand.value) {
    return '暂无'
  }
  return `${latestCommand.value.status} · ${latestCommand.value.targetPower.toFixed(1)} kW`
})

const latestAcknowledgementText = computed(() => {
  if (!latestCommand.value?.latestAckStage) {
    return '等待回执'
  }
  return latestCommand.value.latestAckStage
})

const controlModeForDispatch = computed(() => {
  if (!station.value) {
    return ControlMode.EdgeCoordination
  }
  if (station.value.linkState === 'S3') {
    return ControlMode.LocalAutonomy
  }
  if (station.value.linkState === 'S0' || station.value.linkState === 'S4') {
    return ControlMode.CentralOptimization
  }
  return ControlMode.EdgeCoordination
})

function buildDispatchRequest(): DispatchRequest {
  return {
    totalTargetPower: dispatchTargetPower.value!,
    controlMode: controlModeForDispatch.value,
    stationIds: [stationId.value],
    minimumPower: selectedDeviceDetail.value!.minimumPower!,
    maximumPower: selectedDeviceDetail.value!.maximumPower!,
    maximumRampRate: maximumRampRate.value,
    commandTimeoutSeconds: commandTimeoutSeconds.value,
    allowLocalAutonomy: true
  }
}

function formatTrendTimestamp(timestamp: string): string {
  return new Intl.DateTimeFormat('zh-CN', {
    hour12: false,
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit'
  }).format(new Date(timestamp))
}

const powerChartOption = computed<EChartsOption>(() => {
  const trend = powerTrend.value
  const xAxis = trend?.points.map((point) => formatTrendTimestamp(point.timestamp)) ?? []
  const primaryValues = trend?.points.map((point) => point.primaryValue) ?? []
  const secondaryValues = trend?.points.map((point) => point.secondaryValue) ?? []
  return {
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(6, 31, 113, 0.94)',
      borderColor: 'rgba(72, 224, 255, 0.28)',
      textStyle: { color: '#e7f7ff' },
      formatter: (params: { axisValue: string; seriesName: string; value: number | null }[]) => {
        const lines = [`时间：${params[0]?.axisValue ?? '--'}`]
        params.forEach((item) => {
          lines.push(`${item.seriesName}：${item.value ?? '--'}`)
        })
        return lines.join('<br/>')
      }
    },
    grid: { top: 26, right: 18, bottom: 34, left: 46 },
    legend: {
      top: 0,
      right: 6,
      textStyle: { color: '#9bd6ff' }
    },
    xAxis: {
      type: 'category',
      data: xAxis,
      axisLabel: { color: '#78bfff' },
      axisLine: { lineStyle: { color: 'rgba(72, 224, 255, 0.18)' } }
    },
    yAxis: {
      type: 'value',
      name: trend?.primaryUnit ?? '',
      nameTextStyle: { color: '#78bfff' },
      axisLabel: { color: '#78bfff' },
      splitLine: { lineStyle: { color: 'rgba(72, 224, 255, 0.08)' } }
    },
    series: [
      {
        name: trend?.primaryLabel ?? '主序列',
        type: 'line',
        smooth: true,
        symbol: 'none',
        lineStyle: { width: 3, color: '#42e8ff' },
        data: primaryValues
      },
      {
        name: trend?.secondaryLabel ?? '次序列',
        type: 'line',
        smooth: true,
        symbol: 'none',
        lineStyle: { width: 2, color: '#ffd44f' },
        data: secondaryValues
      }
    ]
  }
})

const qualityChartOption = computed<EChartsOption>(() => ({
  tooltip: {
    trigger: 'axis',
    backgroundColor: 'rgba(6, 31, 113, 0.94)',
    borderColor: 'rgba(72, 224, 255, 0.28)',
    textStyle: { color: '#e7f7ff' }
  },
  grid: { top: 26, right: 50, bottom: 34, left: 46 },
  legend: {
    top: 0,
    right: 6,
    textStyle: { color: '#9bd6ff' }
  },
  xAxis: {
    type: 'category',
    data: qualityTrend.value.map((point) => formatTrendTimestamp(point.timestamp)),
    axisLabel: { color: '#78bfff' },
    axisLine: { lineStyle: { color: 'rgba(72, 224, 255, 0.18)' } }
  },
  yAxis: [
    {
      type: 'value',
      name: 'ms',
      nameTextStyle: { color: '#78bfff' },
      axisLabel: { color: '#78bfff' },
      splitLine: { lineStyle: { color: 'rgba(72, 224, 255, 0.08)' } }
    },
    {
      type: 'value',
      name: '%',
      nameTextStyle: { color: '#78bfff' },
      axisLabel: { color: '#78bfff' },
      splitLine: { show: false }
    }
  ],
  series: [
    {
      name: '通信时延',
      type: 'line',
      smooth: true,
      symbol: 'none',
      lineStyle: { width: 3, color: '#42e8ff' },
      data: qualityTrend.value.map((point) => point.latency)
    },
    {
      name: '丢包率',
      type: 'line',
      yAxisIndex: 1,
      smooth: true,
      symbol: 'none',
      lineStyle: { width: 2, color: '#ffb23f' },
      data: qualityTrend.value.map((point) => point.packetLossRate)
    }
  ]
}))

function defaultDeviceIdForStation(currentStationId: string, deviceList: StationDeviceSnapshot[]): string {
  if (currentStationId === 'pv-gobi-01' && deviceList.some((device) => device.deviceId === 'inverter-group')) {
    return 'inverter-group'
  }
  return deviceList[0]?.deviceId ?? ''
}

function setLastUpdated(): void {
  lastUpdatedText.value = new Date().toLocaleTimeString('zh-CN', { hour12: false })
}

function clearMockTimers(): void {
  while (mockTimers.length) {
    const timer = mockTimers.pop()
    if (timer !== undefined) {
      window.clearTimeout(timer)
    }
  }
}

async function loadLatestCommand(): Promise<void> {
  if (dataSource !== 'api' || !stationId.value) {
    return
  }
  const commands = await fetchLatestDispatchCommands(20)
  latestCommand.value = commands.find((command) => command.stationId === stationId.value) ?? null
}

async function loadStationPage(showLoading = false): Promise<void> {
  isPageLoading.value = showLoading
  pageError.value = ''
  try {
    if (dataSource === 'api') {
      await systemStore.refreshFromApi(stationId.value)
      stationDetail.value = await fetchStationDetail(stationId.value)
      devices.value = await fetchStationDevices(stationId.value)
      await loadLatestCommand()
    } else {
      stationDetail.value = resourceDetailMap[stationId.value] ?? null
      devices.value = buildMockStationDevices(stationId.value)
      latestCommand.value = null
    }

    if (!selectedDeviceId.value || !devices.value.some((device) => device.deviceId === selectedDeviceId.value)) {
      selectedDeviceId.value = defaultDeviceIdForStation(stationId.value, devices.value)
    }
    dispatchTargetPower.value = selectedDevice.value?.activePower ?? station.value?.activePower ?? null
    setLastUpdated()
    await loadSelectedDeviceArtifacts()
  } catch (error) {
    pageError.value = error instanceof Error ? error.message : '专题页加载失败'
  } finally {
    isPageLoading.value = false
  }
}

async function loadSelectedDeviceArtifacts(): Promise<void> {
  if (!selectedDeviceId.value) {
    selectedDeviceDetail.value = null
    powerTrend.value = null
    qualityTrend.value = []
    return
  }

  const detailRequestId = ++pendingDetailRequestId
  const trendRequestId = ++pendingTrendRequestId
  const isSwitchingDevice = loadedTrendDeviceId !== selectedDeviceId.value
  isDeviceLoading.value = true
  isTrendLoading.value = isSwitchingDevice
  if (isSwitchingDevice) {
    powerTrend.value = null
    qualityTrend.value = []
  }
  deviceError.value = ''
  trendError.value = ''
  const now = new Date()
  const from = new Date(now.getTime() - 2 * 60 * 60 * 1000).toISOString()
  const to = now.toISOString()

  try {
    const [detailPayload, powerTrendPayload, qualityTrendPayload] = await Promise.all([
      fetchStationDeviceDetail(stationId.value, selectedDeviceId.value),
      fetchStationDevicePowerTrend(stationId.value, selectedDeviceId.value, from, to, TREND_LIMIT),
      fetchStationDeviceQualityTrend(stationId.value, selectedDeviceId.value, from, to, TREND_LIMIT)
    ])
    if (detailRequestId === pendingDetailRequestId) {
      selectedDeviceDetail.value = detailPayload
    }
    if (trendRequestId === pendingTrendRequestId) {
      powerTrend.value = powerTrendPayload
      qualityTrend.value = qualityTrendPayload
      loadedTrendDeviceId = selectedDeviceId.value
    }
  } catch (error) {
    const message = error instanceof Error ? error.message : '设备数据加载失败'
    if (detailRequestId === pendingDetailRequestId) {
      deviceError.value = message
    }
    if (trendRequestId === pendingTrendRequestId) {
      trendError.value = message
    }
  } finally {
    if (detailRequestId === pendingDetailRequestId) {
      isDeviceLoading.value = false
    }
    if (trendRequestId === pendingTrendRequestId) {
      isTrendLoading.value = false
    }
  }
}

function startRefreshTimer(): void {
  if (dataSource !== 'api') {
    return
  }
  if (refreshTimer !== undefined) {
    window.clearInterval(refreshTimer)
  }
  refreshTimer = window.setInterval(() => {
    void loadStationPage()
  }, AUTO_REFRESH_INTERVAL_MS)
}

function stopRefreshTimer(): void {
  if (refreshTimer !== undefined) {
    window.clearInterval(refreshTimer)
    refreshTimer = undefined
  }
}

function goBack(): void {
  void router.push('/stations')
}

function selectDevice(deviceId: string): void {
  if (selectedDeviceId.value === deviceId) {
    return
  }
  selectedDeviceId.value = deviceId
}

async function handlePreviewDispatch(): Promise<void> {
  if (!station.value || !selectedDeviceDetail.value || dispatchTargetPower.value === null) {
    return
  }
  if (dispatchInputError.value) {
    ElMessage.warning(dispatchInputError.value)
    return
  }

  controlError.value = ''
  try {
    dispatchPreviewResult.value = await previewDispatch(buildDispatchRequest())
    previewReady.value = dispatchPreviewResult.value.valid
    if (dispatchPreviewResult.value.valid) {
      ElMessage.success(dispatchPreviewResult.value.message)
    } else {
      ElMessage.warning(dispatchPreviewResult.value.message)
    }
  } catch (error) {
    controlError.value = error instanceof Error ? error.message : '预览失败'
    previewReady.value = false
  }
}

async function handleExecuteDispatch(): Promise<void> {
  if (!previewReady.value || dispatchTargetPower.value === null || !station.value) {
    ElMessage.warning('请先生成有效预览')
    return
  }
  if (dispatchInputError.value) {
    ElMessage.warning(dispatchInputError.value)
    return
  }
  isSubmittingDispatch.value = true
  controlError.value = ''
  try {
    await ElMessageBox.confirm('将通过后端调度服务向模拟器控制通道发布场站功率指令。发布成功不代表已执行，需以回执状态为准。', '确认功率下发', {
      type: 'warning',
      confirmButtonText: '确认下发',
      cancelButtonText: '取消'
    })
    const executionResult = await executeDispatch(buildDispatchRequest())
    const stationCommand = executionResult.commands.find((command) => command.stationId === stationId.value)
    if (!stationCommand) {
      throw new Error('后端未创建当前场站的控制指令')
    }
    latestCommand.value = stationCommand
    previewReady.value = false
    await loadStationPage()
    ElMessage.success(`指令已${stationCommand.status === 'SENT' ? '发布，等待模拟器回执' : `创建，当前状态：${stationCommand.status}`}`)
  } catch (error) {
    if (error instanceof Error && error.message !== 'cancel') {
      controlError.value = error.message
    }
  } finally {
    isSubmittingDispatch.value = false
  }
}

async function handleDiagnosis(conservative: boolean): Promise<void> {
  if (!station.value) {
    return
  }
  isSubmittingDiagnosis.value = true
  controlError.value = ''
  try {
    if (conservative) {
      await ElMessageBox.confirm('当前仅支持场站级保守策略申请，将发起一次场站级诊断并输出处置方案。', '本地保守模式', {
        type: 'warning',
        confirmButtonText: '确认申请',
        cancelButtonText: '取消'
      })
    }
    if (dataSource === 'api') {
      latestDiagnosis.value = await analyzeFault({
        stationId: stationId.value,
        eventId: undefined,
        jitter: station.value.latency,
        consecutivePacketLoss: Math.round(station.value.packetLossRate),
        heartbeatAge: station.value.heartbeatAge ?? station.value.dataAge,
        acknowledgementAge: station.value.dataAge,
        duplicateMessage: false,
        staleSequence: false,
        timeSkewSeconds: 0,
        frozenTelemetry: false,
        suspectedAttack: false
      })
      ElMessage.success(
        conservative
          ? '已基于当前遥测发起保守策略评估，请查看诊断结果与处置建议'
          : '已发起场站级诊断，请查看最新诊断结果'
      )
    } else {
      latestDiagnosis.value = {
        runId: conservative ? 'mock-conservative-run' : 'mock-diagnosis-run',
        eventId: null,
        stationId: stationId.value,
        status: 'READY',
        approvalStatus: 'NOT_REQUIRED',
        ruleDecision: {
          matched: true,
          faultCode: conservative ? 'C6' : 'C1',
          faultType: conservative ? 'FULL_DISCONNECTION' : 'LATENCY_JITTER',
          severity: conservative ? 'HIGH' : 'MEDIUM',
          suggestedLinkState: conservative ? station.value.linkState : station.value.linkState,
          evidence: [],
          requiresAiAnalysis: true,
          immediateProtectionActions: []
        },
        agentResults: [],
        safetyDecision: {
          approvedActions: [],
          requiresHumanApproval: false,
          conflictDetected: false,
          fallbackUsed: true,
          explanation: conservative ? 'Mock 模式下演示站级保守策略自动裁决。' : 'Mock 模式下仅演示诊断发起。'
        },
        actions: [],
        conflictDetected: false,
        fallbackUsed: true,
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString()
      }
      ElMessage.success(conservative ? 'Mock 模式已生成站级保守策略申请' : 'Mock 模式已生成诊断任务')
    }
  } catch (error) {
    if (error instanceof Error && error.message !== 'cancel') {
      controlError.value = error.message
    }
  } finally {
    isSubmittingDiagnosis.value = false
  }
}

function scrollToTrend(): void {
  void nextTick(() => {
    powerTrendSection.value?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  })
}

async function retryDevicePanel(): Promise<void> {
  await loadSelectedDeviceArtifacts()
}

watch(
  () => stationId.value,
  async () => {
    selectedDeviceId.value = ''
    loadedTrendDeviceId = ''
    dispatchPreviewResult.value = null
    previewReady.value = false
    await loadStationPage()
  }
)

watch(
  () => selectedDeviceId.value,
  async () => {
    dispatchPreviewResult.value = null
    previewReady.value = false
    dispatchTargetPower.value = selectedDevice.value?.activePower ?? station.value?.activePower ?? null
    await loadSelectedDeviceArtifacts()
  }
)

watch([dispatchTargetPower, maximumRampRate, commandTimeoutSeconds], () => {
  previewReady.value = false
  dispatchPreviewResult.value = null
})

onMounted(async () => {
  await systemStore.initialize()
  await loadStationPage(true)
  startRefreshTimer()
})

onBeforeUnmount(() => {
  stopRefreshTimer()
  clearMockTimers()
})
</script>

<template>
  <section v-if="station" class="station-topic page-shell">
    <header class="station-topic__header">
      <div class="station-topic__intro">
        <button type="button" class="station-topic__back" @click="goBack">返回资源总览</button>
        <p>{{ stationLabel }}</p>
        <h2>{{ pageTitle }}</h2>
        <span>{{ stationDetail?.overview ?? `${stationLabel}专题页正在加载中。` }}</span>
      </div>
      <div class="station-topic__status">
        <small>{{ dataSourceLabel }}</small>
        <strong :style="{ color: linkStateMetaMap[station.linkState].color }">{{ pageStateLabel }}</strong>
      </div>
    </header>

    <div class="station-topic__toolbar">
      <span>{{ stationLabel }} / 子设备拓扑</span>
      <span>控制模式：{{ currentControlMode }}</span>
      <span>最近更新时间：{{ lastUpdatedText }}</span>
      <button type="button" @click="loadStationPage">手动刷新</button>
    </div>

    <div v-if="pageError" class="station-topic__state is-error">{{ pageError }}</div>
    <div v-else-if="isPageLoading && !stationDetail" class="station-topic__state">专题页加载中...</div>
    <template v-else>
      <section class="station-topic__metrics">
        <article class="metric-card">
          <span>{{ stationPowerLabel }}</span>
          <strong>{{ stationDisplayPower.toFixed(1) }}<small>kW</small></strong>
        </article>
        <article class="metric-card">
          <span>可用容量</span>
          <strong>{{ station.availableCapacity.toFixed(1) }}<small>kW</small></strong>
        </article>
        <article class="metric-card">
          <span>通信时延</span>
          <strong>{{ station.latency }}<small>ms</small></strong>
        </article>
        <article class="metric-card">
          <span>丢包率</span>
          <strong>{{ station.packetLossRate }}<small>%</small></strong>
        </article>
        <article class="metric-card">
          <span>数据时效</span>
          <strong>{{ station.dataAge }}<small>s</small></strong>
        </article>
        <article class="metric-card">
          <span>链路状态</span>
          <strong>{{ station.linkState }}</strong>
        </article>
      </section>

      <section class="station-topic__middle">
        <StationDeviceTopology
          class="station-topic__topology"
          :station="station"
          :devices="devices"
          :selected-device-id="selectedDeviceId"
          @select-device="selectDevice"
        />

        <StationDeviceDetailPanel
          class="station-topic__detail"
          :detail="selectedDeviceDetail"
          :loading="isDeviceLoading"
          :error-message="deviceError"
          @retry="retryDevicePanel"
          @view-trend="scrollToTrend"
          @diagnose="handleDiagnosis(false)"
        />
      </section>

      <section class="station-topic__bottom">
        <article ref="powerTrendSection" class="station-topic__panel">
          <header>
            <h3>{{ powerTrend?.mode === 'environment' ? '环境监测趋势' : '功率执行趋势' }}</h3>
            <small>{{ selectedDevice?.deviceName ?? '未选择设备' }}</small>
          </header>
          <div v-if="isTrendLoading" class="station-topic__chart-state">趋势加载中...</div>
          <div v-else-if="trendError" class="station-topic__chart-state is-error">{{ trendError }}</div>
          <div v-else-if="selectedTrendEmpty" class="station-topic__chart-state">暂无设备级趋势数据</div>
          <EChartPanel v-else :option="powerChartOption" height="260px" />
        </article>

        <article ref="qualityTrendSection" class="station-topic__panel">
          <header>
            <h3>链路质量趋势</h3>
            <small>{{ selectedDevice?.deviceName ?? '未选择设备' }}</small>
          </header>
          <div v-if="isTrendLoading" class="station-topic__chart-state">趋势加载中...</div>
          <div v-else-if="trendError" class="station-topic__chart-state is-error">{{ trendError }}</div>
          <div v-else-if="qualityTrendEmpty" class="station-topic__chart-state">暂无设备级链路趋势数据</div>
          <EChartPanel v-else :option="qualityChartOption" height="260px" />
        </article>
      </section>
    </template>
  </section>

  <section v-else class="station-topic__empty page-shell">
    <div>
      <h2>未找到资源专题数据</h2>
      <button type="button" class="station-topic__back" @click="goBack">返回资源总览</button>
    </div>
  </section>
</template>

<style scoped>
.station-topic,
.station-topic__empty {
  display: grid;
  gap: 16px;
}

.station-topic__header,
.station-topic__toolbar,
.metric-card,
.station-topic__panel,
.station-topic__empty div {
  border: 1px solid rgba(72, 224, 255, 0.24);
  border-radius: 8px;
  background: linear-gradient(180deg, rgba(8, 34, 128, 0.8), rgba(4, 24, 95, 0.84));
}

.station-topic__header {
  display: flex;
  justify-content: space-between;
  gap: 20px;
  padding: 18px;
}

.station-topic__back,
.station-topic__toolbar button,
.station-topic__action-row button {
  padding: 10px 14px;
  border: 1px solid rgba(72, 224, 255, 0.24);
  border-radius: 5px;
  background: rgba(4, 29, 111, 0.42);
  color: #dff7ff;
  cursor: pointer;
}

.station-topic__intro p,
.station-topic__intro h2,
.station-topic__intro span {
  margin: 0;
}

.station-topic__intro p {
  margin-top: 12px;
  color: #67cfff;
  font-size: 13px;
}

.station-topic__intro h2 {
  margin-top: 8px;
  font-size: 40px;
}

.station-topic__intro span {
  display: block;
  margin-top: 8px;
  color: #a8ddff;
  line-height: 1.7;
}

.station-topic__status {
  min-width: 130px;
  text-align: right;
}

.station-topic__status small,
.station-topic__status strong {
  display: block;
}

.station-topic__status small {
  color: #8fcfff;
}

.station-topic__status strong {
  margin-top: 12px;
  font-size: 28px;
}

.station-topic__toolbar {
  display: flex;
  gap: 18px;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  padding: 12px 16px;
  color: #a8ddff;
}

.station-topic__state {
  padding: 18px;
  border: 1px solid rgba(72, 224, 255, 0.24);
  background: rgba(6, 31, 113, 0.78);
}

.station-topic__state.is-error,
.station-topic__error {
  color: #ffb98f;
}

.station-topic__metrics {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 12px;
}

.metric-card {
  padding: 16px 14px;
}

.metric-card span,
.metric-card strong {
  display: block;
}

.metric-card span {
  color: #9fd8ff;
}

.metric-card strong {
  margin-top: 10px;
  font-size: 30px;
  color: #53ecff;
}

.metric-card small {
  margin-left: 4px;
  font-size: 14px;
  color: #dff7ff;
}

.station-topic__middle {
  display: grid;
  grid-template-columns: minmax(0, 1.4fr) minmax(340px, 0.8fr);
  gap: 12px;
}

.station-topic__bottom {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.station-topic__panel header {
  padding: 14px 16px;
  border-bottom: 1px solid rgba(72, 224, 255, 0.18);
}

.station-topic__panel header h3,
.station-topic__panel header small {
  margin: 0;
}

.station-topic__panel header small {
  display: block;
  margin-top: 8px;
  color: #93d4ff;
}

.station-topic__control {
  padding-bottom: 14px;
}

.station-topic__control-grid {
  display: grid;
  gap: 12px;
  padding: 16px;
}

.station-topic__control-grid label,
.station-topic__constraint {
  display: grid;
  gap: 8px;
}

.station-topic__control-grid span {
  color: #8fcfff;
}

.station-topic__control-grid input {
  padding: 10px 12px;
  border: 1px solid rgba(72, 224, 255, 0.18);
  background: rgba(4, 29, 111, 0.42);
  color: #e7f7ff;
}

.station-topic__constraint strong,
.station-topic__warning,
.station-topic__preview,
.station-topic__error {
  padding: 0 16px;
}

.station-topic__warning {
  color: #ffd36c;
}

.station-topic__preview {
  color: #71f3c8;
}

.station-topic__action-row {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  padding: 16px;
}

.station-topic__action-row button:disabled,
.station-topic__toolbar button:disabled,
.station-topic__back:disabled {
  opacity: 0.54;
  cursor: not-allowed;
}

.station-topic__status-boxes {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  padding: 0 16px;
}

.station-topic__status-boxes article {
  padding: 12px;
  border: 1px solid rgba(72, 224, 255, 0.18);
  background: rgba(4, 29, 111, 0.42);
}

.station-topic__status-boxes span,
.station-topic__status-boxes strong {
  display: block;
}

.station-topic__status-boxes span {
  color: #8fcfff;
}

.station-topic__status-boxes strong {
  margin-top: 8px;
  color: #e7f7ff;
}

.station-topic__chart-state {
  padding: 18px 16px;
  color: #92d7ff;
}

.station-topic__chart-state.is-error {
  color: #ffb98f;
}

.station-topic__empty div {
  padding: 24px;
}

@media (max-width: 1280px) {
  .station-topic__metrics {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .station-topic__middle,
  .station-topic__bottom {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 1024px) {
  .station-topic__header {
    flex-direction: column;
  }
}

@media (max-width: 768px) {
  .station-topic__metrics,
  .station-topic__status-boxes,
  .station-topic__action-row {
    grid-template-columns: 1fr;
  }

  .station-topic__intro h2 {
    font-size: 32px;
  }
}
</style>
