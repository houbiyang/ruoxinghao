<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import type { EChartsOption, LineSeriesOption } from 'echarts'

import {
  activateMarketDispatchPlan,
  analyzeMarketDispatch,
  approveMarketDispatchPlan,
  createMarketDispatchPlan,
  executeNextMarketDispatchPlan,
  fetchRealtimePowerTelemetry,
  supersedeMarketDispatchPlan,
  validateMarketDispatchPlan
} from '@/api/systemApi'
import EChartPanel from '@/components/charts/EChartPanel.vue'
import type { ExperimentReplayPoint } from '@/types/system'

type PowerSample = {
  time: number
  photovoltaicPower: number | null
  thermalPower: number | null
  windPower: number | null
  gridPower: number | null
  storagePower: number | null
  chargingPower: number | null
  loadPower: number | null
  unmanagedGridPower: number | null
}

type PowerField = keyof Omit<PowerSample, 'time' | 'unmanagedGridPower'>

type DispatchActionItem = {
  title: string
  detail: string
  tone: 'green' | 'blue' | 'cyan' | 'purple'
}

const REFRESH_INTERVAL_MS = 5000
const TELEMETRY_BUCKET_MS = 10_000
const DISPLAY_WINDOW_MS = 5 * 60 * 1000
const FUTURE_WINDOW_MS = 2 * 60 * 1000
const powerSamples = ref<PowerSample[]>([])
const axisNow = ref(Date.now())
const isTelemetryLoading = ref(false)
const isDispatching = ref(false)
const isDispatchBusy = ref(false)
const activeDispatchPlanId = ref('')
const dispatchStartedAt = ref<number | null>(null)
const dispatchEndedAt = ref<number | null>(null)
const dispatchFeedback = ref('正常基线运行中，等待启动智能体调度')

const stationFieldById: Record<string, PowerField> = {
  'pv-gobi-01': 'photovoltaicPower',
  'thermal-plant-06': 'thermalPower',
  'wind-ridge-02': 'windPower',
  'grid-link-00': 'gridPower',
  'storage-hub-03': 'storagePower',
  'charge-corridor-05': 'chargingPower',
  'load-oasis-04': 'loadPower'
}

let sampleTimer: number | undefined

function roundPower(value: number): number {
  return Math.round(value * 10) / 10
}

function calculateAxisMinimum(value: { min: number, max: number }): number {
  const padding = Math.max(8, (value.max - value.min) * 0.08)
  return Math.floor((value.min - padding) / 10) * 10
}

function calculateAxisMaximum(value: { min: number, max: number }): number {
  const padding = Math.max(8, (value.max - value.min) * 0.08)
  return Math.ceil((value.max + padding) / 10) * 10
}

function createEmptyPowerSample(time: number): PowerSample {
  return {
    time,
    photovoltaicPower: null,
    thermalPower: null,
    windPower: null,
    gridPower: null,
    storagePower: null,
    chargingPower: null,
    loadPower: null,
    unmanagedGridPower: null
  }
}

function aggregateTelemetry(points: ExperimentReplayPoint[]): PowerSample[] {
  const samplesByBucket = new Map<number, PowerSample>()
  for (const point of points) {
    const timestamp = new Date(point.timestamp).getTime()
    const field = stationFieldById[point.stationId]
    if (!Number.isFinite(timestamp) || !field) {
      continue
    }
    const bucketTime = Math.floor(timestamp / TELEMETRY_BUCKET_MS) * TELEMETRY_BUCKET_MS
    const sample = samplesByBucket.get(bucketTime) ?? createEmptyPowerSample(bucketTime)
    sample[field] = roundPower(point.activePower)
    samplesByBucket.set(bucketTime, sample)
  }

  const aggregatedSamples = [...samplesByBucket.values()]
    .sort((left, right) => left.time - right.time)
    .map((sample) => {
      const requiredValues = [
        sample.loadPower,
        sample.chargingPower,
        sample.photovoltaicPower,
        sample.thermalPower,
        sample.windPower
      ]
      if (requiredValues.every((value) => value !== null)) {
        sample.unmanagedGridPower = roundPower(
          (sample.loadPower as number) + (sample.chargingPower as number)
          - (sample.photovoltaicPower as number) - (sample.thermalPower as number) - (sample.windPower as number)
        )
      }
      return sample
    })
  const samplesWithGaps: PowerSample[] = []
  for (const sample of aggregatedSamples) {
    const previousSample = samplesWithGaps.at(-1)
    if (previousSample && sample.time - previousSample.time > TELEMETRY_BUCKET_MS * 2) {
      samplesWithGaps.push(createEmptyPowerSample(previousSample.time + TELEMETRY_BUCKET_MS))
    }
    samplesWithGaps.push(sample)
  }
  return samplesWithGaps
}

async function loadPowerSamples(): Promise<void> {
  if (isTelemetryLoading.value) {
    return
  }
  isTelemetryLoading.value = true
  const now = Date.now()
  try {
    const points = await fetchRealtimePowerTelemetry(
      new Date(now - DISPLAY_WINDOW_MS).toISOString(),
      new Date(now).toISOString(),
      5000,
      TELEMETRY_BUCKET_MS / 1000
    )
    powerSamples.value = aggregateTelemetry(points)
    axisNow.value = now
  } finally {
    isTelemetryLoading.value = false
  }
}

function buildPowerLine(
  name: string,
  field: keyof Omit<PowerSample, 'time' | 'unmanagedGridPower'>,
  color: string,
  yAxisIndex: number
): LineSeriesOption {
  return {
    name,
    type: 'line',
    yAxisIndex,
    smooth: 0.22,
    symbol: 'circle',
    symbolSize: 4,
    showSymbol: true,
    sampling: 'lttb',
    lineStyle: { width: 2, color },
    itemStyle: { color },
    data: powerSamples.value.map((sample) => [sample.time, sample[field]])
  }
}

function formatMarkerTime(timestamp: number): string {
  return new Intl.DateTimeFormat('zh-CN', {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false
  }).format(new Date(timestamp))
}

const dispatchBoundaryLines = computed(() => {
  const boundaries: Array<Record<string, unknown>> = []
  if (dispatchStartedAt.value !== null) {
    boundaries.push({
      name: '智能体调度开始',
      xAxis: dispatchStartedAt.value,
      lineStyle: { color: '#42d785', width: 2, type: 'dashed' },
      label: {
        show: true,
        formatter: `调度开始\n${formatMarkerTime(dispatchStartedAt.value)}`,
        color: '#73f5a1',
        backgroundColor: 'rgba(7, 54, 41, 0.92)',
        padding: [4, 7],
        borderRadius: 4
      }
    })
  }
  if (dispatchEndedAt.value !== null) {
    boundaries.push({
      name: '智能体调度结束',
      xAxis: dispatchEndedAt.value,
      lineStyle: { color: '#ff9d66', width: 2, type: 'dashed' },
      label: {
        show: true,
        formatter: `调度结束\n${formatMarkerTime(dispatchEndedAt.value)}`,
        color: '#ffc09a',
        backgroundColor: 'rgba(74, 36, 24, 0.92)',
        padding: [4, 7],
        borderRadius: 4
      }
    })
  }
  return boundaries
})

const latestSample = computed(() => powerSamples.value[powerSamples.value.length - 1])

const telemetryStatus = computed(() => latestSample.value ? '实时遥测中' : '等待遥测')

const currentPeakReduction = computed(() => {
  if (!latestSample.value) {
    return 0
  }
  if (latestSample.value.unmanagedGridPower === null || latestSample.value.gridPower === null) {
    return 0
  }
  return Math.max(0, latestSample.value.unmanagedGridPower - latestSample.value.gridPower)
})

const photovoltaicConsumptionRate = computed(() => {
  const current = latestSample.value
  if (!current || current.photovoltaicPower === null || current.photovoltaicPower <= 0
      || current.loadPower === null || current.storagePower === null) {
    return 0
  }
  const surplusPower = Math.max(0, current.photovoltaicPower - current.loadPower)
  const absorbedSurplus = current.storagePower < 0 ? Math.min(surplusPower, Math.abs(current.storagePower)) : 0
  const curtailedPower = Math.max(0, surplusPower - absorbedSurplus)
  return Math.max(0, Math.min(100, ((current.photovoltaicPower - curtailedPower) / current.photovoltaicPower) * 100))
})

const storageStatus = computed(() => {
  const storagePower = latestSample.value?.storagePower ?? 0
  if (storagePower > 0.5) {
    return `放电 ${formatPower(storagePower)} kW`
  }
  if (storagePower < -0.5) {
    return `充电 ${formatPower(Math.abs(storagePower))} kW`
  }
  return '待机'
})

const dispatchActionItems = computed<DispatchActionItem[]>(() => {
  const chargingPower = latestSample.value?.chargingPower ?? 0
  const peakReduction = currentPeakReduction.value

  return [
    {
      title: '新能源优先消纳',
      detail: `光伏消纳率 ${formatPower(photovoltaicConsumptionRate.value)}%`,
      tone: 'green'
    },
    {
      title: '储能动态调节',
      detail: storageStatus.value,
      tone: 'cyan'
    },
    {
      title: '市电削峰平滑',
      detail: peakReduction > 0.5 ? `当前削峰 ${formatPower(peakReduction)} kW` : '维持平滑购电',
      tone: 'blue'
    },
    {
      title: '充电桩错峰用电',
      detail: `当前充电功率 ${formatPower(chargingPower)} kW`,
      tone: 'purple'
    }
  ]
})

function formatPower(value: number): string {
  return value.toFixed(1)
}

async function startBaselineDispatch(): Promise<void> {
  isDispatchBusy.value = true
  dispatchFeedback.value = '智能体正在计算正常基线调度方案...'
  try {
    const agentRun = await analyzeMarketDispatch({
      dispatchContextSnapshotId: null,
      dispatchPlanId: null,
      conservativePlan: false
    })
    if (!agentRun.safetyDecision.passed) {
      dispatchFeedback.value = '安全校验未通过，本轮调度未执行'
      return
    }
    const executablePlan = await createMarketDispatchPlan({
      dispatchContextSnapshotId: null,
      conservativePlan: agentRun.conservativePlan
    })
    await validateMarketDispatchPlan(executablePlan.planId)
    await approveMarketDispatchPlan(executablePlan.planId)
    await activateMarketDispatchPlan(executablePlan.planId)
    await executeNextMarketDispatchPlan(executablePlan.planId)
    activeDispatchPlanId.value = executablePlan.planId
    dispatchStartedAt.value = Date.now()
    dispatchEndedAt.value = null
    isDispatching.value = true
    dispatchFeedback.value = '智能体调度执行中，曲线持续接收模拟器实时遥测'
    await loadPowerSamples()
  } catch (error) {
    dispatchFeedback.value = error instanceof Error ? error.message : '智能体调度启动失败'
  } finally {
    isDispatchBusy.value = false
  }
}

async function stopBaselineDispatch(): Promise<void> {
  if (!activeDispatchPlanId.value) {
    return
  }
  isDispatchBusy.value = true
  dispatchFeedback.value = '正在结束本轮智能体调度...'
  try {
    await supersedeMarketDispatchPlan(activeDispatchPlanId.value)
    dispatchEndedAt.value = Date.now()
    isDispatching.value = false
    activeDispatchPlanId.value = ''
    dispatchFeedback.value = '本轮智能体调度已结束，继续接收模拟器真实遥测'
    await loadPowerSamples()
  } catch (error) {
    dispatchFeedback.value = error instanceof Error ? error.message : '智能体调度结束失败'
  } finally {
    isDispatchBusy.value = false
  }
}

async function toggleBaselineDispatch(): Promise<void> {
  if (isDispatching.value) {
    await stopBaselineDispatch()
    return
  }
  await startBaselineDispatch()
}

const powerCurveOption = computed<EChartsOption>(() => ({
  animationDurationUpdate: 350,
  color: ['#f7b84b', '#ff7866', '#58d77a', '#3d9cff', '#42d785', '#49d7f2', '#a46bff'],
  tooltip: {
    trigger: 'axis',
    backgroundColor: 'rgba(4, 22, 52, 0.96)',
    borderColor: 'rgba(71, 159, 255, 0.45)',
    textStyle: { color: '#dceaff' },
    valueFormatter: (value) => `${Number(value).toFixed(1)} kW`
  },
  legend: {
    top: 4,
    left: 12,
    right: 12,
    itemWidth: 16,
    itemHeight: 7,
    itemGap: 12,
    selectedMode: true,
    textStyle: { color: '#b9c9e7', fontSize: 12 },
    data: ['光伏发电', '火电发电', '风电发电', '市电购电', '储能充放电', '充电桩用电', '负载用电']
  },
  grid: { top: 76, left: 72, right: 72, bottom: 42 },
  xAxis: {
    type: 'time',
    min: axisNow.value - DISPLAY_WINDOW_MS,
    max: axisNow.value + FUTURE_WINDOW_MS,
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
    axisLine: { lineStyle: { color: 'rgba(83, 132, 190, 0.45)' } },
    splitLine: { show: false }
  },
  yAxis: [
    {
      type: 'value',
      scale: true,
      name: '负荷 / 光伏（kW）',
      min: calculateAxisMinimum,
      max: calculateAxisMaximum,
      nameTextStyle: { color: '#b4c7df', align: 'left' },
      axisLabel: { color: '#a8bbd8' },
      splitLine: { lineStyle: { color: 'rgba(62, 104, 158, 0.28)' } }
    },
    {
      type: 'value',
      scale: true,
      position: 'right',
      name: '调节资源（kW）',
      min: calculateAxisMinimum,
      max: calculateAxisMaximum,
      nameTextStyle: { color: '#b4c7df', align: 'right' },
      axisLabel: { color: '#a8bbd8' },
      splitLine: { show: false }
    }
  ],
  graphic: [{
    type: 'text',
    right: 68,
    top: 175,
    silent: true,
    style: {
      text: '等待实时数据产生\n数据尚未补齐',
      fill: 'rgba(168, 187, 216, 0.62)',
      font: '12px sans-serif',
      lineHeight: 20,
      textAlign: 'center'
    }
  }],
  series: [
    {
      ...buildPowerLine('光伏发电', 'photovoltaicPower', '#f7b84b', 0),
      markLine: {
        silent: true,
        symbol: 'none',
        data: dispatchBoundaryLines.value
      }
    },
    buildPowerLine('火电发电', 'thermalPower', '#ff7866', 1),
    buildPowerLine('风电发电', 'windPower', '#58d77a', 1),
    buildPowerLine('市电购电', 'gridPower', '#3d9cff', 1),
    buildPowerLine('储能充放电', 'storagePower', '#42d785', 1),
    buildPowerLine('充电桩用电', 'chargingPower', '#49d7f2', 1),
    buildPowerLine('负载用电', 'loadPower', '#a46bff', 0)
  ]
}))

onMounted(() => {
  void loadPowerSamples()
  sampleTimer = window.setInterval(() => {
    void loadPowerSamples()
  }, REFRESH_INTERVAL_MS)
})

onBeforeUnmount(() => {
  if (sampleTimer !== undefined) {
    window.clearInterval(sampleTimer)
  }
})
</script>

<template>
  <article class="dispatch-card agent-power-card">
    <header class="agent-power-header">
      <div>
        <h2>智能体调度功率曲线</h2>
        <p>模拟器真实遥测 · 10 秒聚合 · {{ dispatchFeedback }}</p>
      </div>
      <div class="agent-power-controls">
        <span class="agent-power-status" :class="{ 'is-active': telemetryStatus === '实时遥测中' }">
          <i />{{ telemetryStatus }}
        </span>
        <button
          type="button"
          class="agent-dispatch-button"
          :class="{ 'is-running': isDispatching }"
          :disabled="isDispatchBusy"
          @click="toggleBaselineDispatch"
        >
          {{ isDispatchBusy ? '处理中...' : isDispatching ? '结束调度' : '开始调度' }}
        </button>
      </div>
    </header>

    <div class="agent-power-chart">
      <EChartPanel :option="powerCurveOption" height="350px" />
      <aside v-if="latestSample" class="agent-power-actions" aria-label="智能体调度动作">
        <h3>本轮调度动作</h3>
        <ul>
          <li v-for="item in dispatchActionItems" :key="item.title" :class="`is-${item.tone}`">
            <i />
            <div>
              <strong>{{ item.title }}</strong>
              <span>{{ item.detail }}</span>
            </div>
          </li>
        </ul>
      </aside>
    </div>

    <dl class="agent-power-summary">
      <div>
        <dt>当前市电削峰值</dt>
        <dd>{{ formatPower(currentPeakReduction) }} <small>kW</small></dd>
      </div>
      <div>
        <dt>光伏消纳率</dt>
        <dd>{{ formatPower(photovoltaicConsumptionRate) }}<small>%</small></dd>
      </div>
      <div>
        <dt>储能当前状态</dt>
        <dd class="is-storage">{{ storageStatus }}</dd>
      </div>
    </dl>
  </article>
</template>

<style scoped>
.agent-power-card {
  overflow: hidden;
}

.agent-power-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 62px;
  padding: 0 22px;
  border-bottom: 1px solid rgba(71, 128, 194, 0.16);
}

.agent-power-header h2 {
  margin: 0;
  color: #ffffff;
  font-size: 20px;
}

.agent-power-header p {
  margin: 5px 0 0;
  color: #8198b8;
  font-size: 12px;
}

.agent-power-status {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 7px 12px;
  color: #a9bdd8;
  font-size: 13px;
  border: 1px solid rgba(80, 142, 212, 0.36);
  border-radius: 16px;
  background: rgba(7, 30, 67, 0.72);
}

.agent-power-status i {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #71839a;
}

.agent-power-status.is-active {
  color: #72f6a3;
  border-color: rgba(66, 215, 133, 0.38);
  background: rgba(17, 92, 60, 0.28);
}

.agent-power-status.is-active i {
  background: #42d785;
  box-shadow: 0 0 8px rgba(66, 215, 133, 0.9);
}

.agent-power-controls {
  display: flex;
  align-items: center;
  gap: 10px;
}

.agent-dispatch-button {
  min-width: 92px;
  padding: 8px 14px;
  color: #e9f7ff;
  border: 1px solid rgba(67, 185, 255, 0.72);
  border-radius: 5px;
  background: linear-gradient(180deg, #258fe6, #1364bb);
  cursor: pointer;
}

.agent-dispatch-button.is-running {
  color: #ffd9cc;
  border-color: rgba(255, 126, 99, 0.72);
  background: rgba(120, 40, 31, 0.78);
}

.agent-dispatch-button:disabled {
  cursor: not-allowed;
  opacity: 0.58;
}

.agent-power-chart {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 174px;
  gap: 12px;
  padding: 12px;
}

.agent-power-actions {
  padding: 14px 12px;
  border: 1px solid rgba(64, 143, 235, 0.28);
  border-radius: 6px;
  background: rgba(7, 30, 67, 0.72);
}

.agent-power-actions h3 {
  margin: 0 0 12px;
  color: #e8f4ff;
  font-size: 13px;
}

.agent-power-actions ul {
  display: grid;
  gap: 14px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.agent-power-actions li {
  display: grid;
  grid-template-columns: 8px minmax(0, 1fr);
  gap: 8px;
}

.agent-power-actions li > i {
  width: 7px;
  height: 7px;
  margin-top: 5px;
  border-radius: 50%;
  background: #60d987;
  box-shadow: 0 0 7px rgba(96, 217, 135, 0.7);
}

.agent-power-actions li.is-blue > i {
  background: #48a4ff;
  box-shadow: 0 0 7px rgba(72, 164, 255, 0.7);
}

.agent-power-actions li.is-cyan > i {
  background: #49d7f2;
  box-shadow: 0 0 7px rgba(73, 215, 242, 0.7);
}

.agent-power-actions li.is-purple > i {
  background: #a46bff;
  box-shadow: 0 0 7px rgba(164, 107, 255, 0.7);
}

.agent-power-actions strong,
.agent-power-actions span {
  display: block;
}

.agent-power-actions strong {
  color: #dceaff;
  font-size: 12px;
}

.agent-power-actions span {
  margin-top: 4px;
  color: #91a8c8;
  font-size: 11px;
  line-height: 1.45;
}

.agent-power-summary {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 1px;
  margin: 0;
  border-top: 1px solid rgba(71, 128, 194, 0.16);
  background: rgba(71, 128, 194, 0.16);
}

.agent-power-summary div {
  padding: 14px 22px;
  background: rgba(4, 24, 56, 0.96);
}

.agent-power-summary dt {
  color: #91a8c8;
  font-size: 12px;
}

.agent-power-summary dd {
  margin: 6px 0 0;
  color: #48a4ff;
  font-size: 20px;
  font-weight: 700;
}

.agent-power-summary dd.is-storage {
  color: #42d785;
}

.agent-power-summary small {
  margin-left: 3px;
  color: #b8cbe3;
  font-size: 12px;
  font-weight: 500;
}

@media (max-width: 720px) {
  .agent-power-header {
    align-items: flex-start;
    gap: 12px;
    padding: 14px 16px;
  }

  .agent-power-header h2 {
    font-size: 17px;
  }

  .agent-power-status {
    flex: 0 0 auto;
    padding: 6px 9px;
    font-size: 11px;
  }

  .agent-power-controls {
    align-items: flex-end;
    flex-direction: column;
  }

  .agent-power-chart {
    margin: 0 -4px;
    padding: 0;
  }

  .agent-power-summary {
    grid-template-columns: 1fr;
  }
}
</style>
