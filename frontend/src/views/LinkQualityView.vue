<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import type { EChartsOption } from 'echarts'

import {
  fetchExperimentQualityMetrics,
  fetchExperimentReplay,
  fetchExperimentTimeline,
  fetchLatestExperimentRuns,
  fetchStations
} from '@/api/systemApi'
import EChartPanel from '@/components/charts/EChartPanel.vue'
import LinkQualityMap from '@/components/links/LinkQualityMap.vue'
import LinkRecoveryReplay from '@/components/links/LinkRecoveryReplay.vue'
import LinkSwitchLog from '@/components/links/LinkSwitchLog.vue'
import {
  buildSwitchLogEntries,
  buildTrendData,
  computeLinkOverview,
  formatDateTime,
  formatTime,
  linkStateLabel,
  normalizeStations,
  stationNameOf,
  type LinkTrendDatum
} from '@/components/links/linkQualityUtils'
import { linkStateMetaMap } from '@/mock/systemData'
import {
  type ExperimentQualityMetrics,
  type ExperimentReplayPoint,
  type ExperimentRun,
  type ExperimentTimelinePoint,
  type StationState
} from '@/types/system'

const AUTO_REFRESH_INTERVAL_MS = 5000
const REPLAY_INTERVAL_MS = 900

const stations = ref<StationState[]>([])
const runs = ref<ExperimentRun[]>([])
const selectedRunId = ref('')
const selectedStationId = ref('')
const qualityMetrics = ref<ExperimentQualityMetrics | null>(null)
const timelinePoints = ref<ExperimentTimelinePoint[]>([])
const replayPoints = ref<ExperimentReplayPoint[]>([])
const isLoadingStations = ref(false)
const isLoadingAnalysis = ref(false)
const stationErrorMessage = ref('')
const analysisErrorMessage = ref('')
const lastUpdatedAt = ref('')
const replayIndex = ref(0)
const isReplayPlaying = ref(false)
const replayEnded = ref(false)

let stationRefreshTimer: number | undefined
let replayTimer: number | undefined

const selectedRun = computed(() => runs.value.find((run) => run.runId === selectedRunId.value) ?? null)
const selectedStation = computed(() => stations.value.find((station) => station.stationId === selectedStationId.value) ?? null)
const currentReplayPoint = computed(() => replayPoints.value[replayIndex.value] ?? null)
const mapStations = computed(() => stations.value)
const overviewMetrics = computed(() => computeLinkOverview(stations.value))
const trendData = computed(() => buildTrendData(qualityMetrics.value, replayPoints.value, selectedStationId.value))
const switchLogEntries = computed(() => {
  const stationScopedPoints = timelinePoints.value.filter((point) => point.stationId === selectedStationId.value)
  return buildSwitchLogEntries(stationScopedPoints)
})
const selectedQualityRank = computed(() => {
  return qualityMetrics.value?.stationRanking.find((item) => item.stationId === selectedStationId.value) ?? null
})
const currentDataSourceLabel = computed(() => 'API')
const currentWorstStateLabel = computed(() => linkStateLabel(overviewMetrics.value.worstState))
const replayDisabledReason = computed(() => {
  if (analysisErrorMessage.value) {
    return analysisErrorMessage.value
  }
  if (!selectedRun.value) {
    return '暂无实验记录，请先在实时注入页面执行一次实验。'
  }
  if (!selectedStation.value) {
    return '当前没有可用场站，无法加载回放。'
  }
  if (replayPoints.value.length === 0) {
    return '所选实验与场站暂无回放数据。'
  }
  return ''
})
const stationScopeLabel = computed(() => `当前场站：${selectedStation.value?.stationName ?? '未选择场站'}`)

const latencyTrendOption = computed<EChartsOption>(() => {
  return createTrendOption(
    '时延与抖动趋势',
    trendData.value,
    [
      { key: 'latency', label: '通信时延', color: '#48e0ff', unit: 'ms' },
      { key: 'jitter', label: '时延抖动', color: '#f6d87b', unit: 'ms' }
    ]
  )
})

const packetLossTrendOption = computed<EChartsOption>(() => {
  return createTrendOption(
    '丢包率趋势',
    trendData.value,
    [{ key: 'packetLossRate', label: '丢包率', color: '#ff9d3f', unit: '%' }]
  )
})

const dataAgeTrendOption = computed<EChartsOption>(() => {
  return createTrendOption(
    '数据时效趋势',
    trendData.value,
    [{ key: 'dataAge', label: '数据时效', color: '#62ffb6', unit: 's' }]
  )
})

const stateDistributionOption = computed<EChartsOption>(() => {
  const distribution = qualityMetrics.value?.stateDistribution ?? []
  return {
    color: distribution.map((item) => linkStateMetaMap[item.linkState].color),
    tooltip: {
      trigger: 'item',
      formatter: '{b}: {c} 次'
    },
    series: [
      {
        type: 'pie',
        radius: ['50%', '74%'],
        label: { color: '#d8f6ff' },
        data: distribution.map((item) => ({
          name: `${item.linkState} ${linkStateMetaMap[item.linkState].label}`,
          value: item.count
        }))
      }
    ]
  }
})

const rankingOption = computed<EChartsOption>(() => {
  const ranking = qualityMetrics.value?.stationRanking ?? []
  return {
    color: ['#48e0ff'],
    grid: { top: 24, left: 82, right: 20, bottom: 28 },
    tooltip: {
      trigger: 'axis'
    },
    xAxis: {
      type: 'value',
      axisLabel: { color: '#9ddfff' },
      splitLine: { lineStyle: { color: 'rgba(72,224,255,0.12)' } }
    },
    yAxis: {
      type: 'category',
      data: ranking.map((item) => stationNameOf(item.stationId)),
      axisLabel: { color: '#d8f6ff' }
    },
    series: [
      {
        type: 'bar',
        barWidth: 14,
        data: ranking.map((item) => ({
          value: item.qualityScore,
          itemStyle: {
            color: item.stationId === selectedStationId.value ? '#ffd44f' : '#48e0ff'
          }
        }))
      }
    ]
  }
})

function createTrendOption(
  title: string,
  points: LinkTrendDatum[],
  seriesConfig: Array<{
    key: keyof Pick<LinkTrendDatum, 'latency' | 'jitter' | 'packetLossRate' | 'dataAge'>
    label: string
    color: string
    unit: string
  }>
): EChartsOption {
  return {
    color: seriesConfig.map((item) => item.color),
    legend: {
      top: 0,
      textStyle: {
        color: '#9ddfff'
      }
    },
    tooltip: {
      trigger: 'axis',
      formatter: (params: unknown) => formatTrendTooltip(params, title, points, seriesConfig)
    },
    grid: {
      top: 44,
      left: 48,
      right: 18,
      bottom: 32
    },
    xAxis: {
      type: 'category',
      data: points.map((point) => formatTime(point.timestamp)),
      axisLabel: { color: '#9ddfff' }
    },
    yAxis: {
      type: 'value',
      axisLabel: { color: '#9ddfff' },
      splitLine: {
        lineStyle: { color: 'rgba(72,224,255,0.12)' }
      }
    },
    series: seriesConfig.map((item) => ({
      type: 'line',
      smooth: true,
      showSymbol: false,
      name: item.label,
      data: points.map((point) => Number(point[item.key]))
    }))
  }
}

function formatTrendTooltip(
  params: unknown,
  title: string,
  points: LinkTrendDatum[],
  seriesConfig: Array<{ key: keyof Pick<LinkTrendDatum, 'latency' | 'jitter' | 'packetLossRate' | 'dataAge'>; label: string; unit: string }>
): string {
  if (!Array.isArray(params) || params.length === 0) {
    return title
  }

  const firstParam = params[0]
  if (typeof firstParam !== 'object' || firstParam === null || !('dataIndex' in firstParam)) {
    return title
  }

  const pointIndex = typeof firstParam.dataIndex === 'number' ? firstParam.dataIndex : 0
  const point = points[pointIndex]
  if (!point) {
    return title
  }

  const metricLines = seriesConfig
    .map((item) => `${item.label}: ${point[item.key]} ${item.unit}`)
    .join('<br/>')

  return [
    formatDateTime(point.timestamp),
    stationNameOf(point.stationId),
    metricLines,
    `丢包率: ${point.packetLossRate}%`,
    `数据时效: ${point.dataAge}s`
  ].join('<br/>')
}

async function loadStationsSnapshot(): Promise<void> {
  isLoadingStations.value = true
  stationErrorMessage.value = ''

  try {
    const stationPayload = await fetchStations()
    stations.value = normalizeStations(stationPayload)

    if (!selectedStationId.value || !stations.value.some((station) => station.stationId === selectedStationId.value)) {
      selectedStationId.value = stations.value[0]?.stationId ?? ''
    }
    lastUpdatedAt.value = new Date().toLocaleString('zh-CN', { hour12: false })
  } catch (error) {
    stationErrorMessage.value = error instanceof Error ? error.message : '场站链路状态加载失败'
  } finally {
    isLoadingStations.value = false
  }
}

async function loadRuns(): Promise<void> {
  try {
    runs.value = await fetchLatestExperimentRuns(20)
    if (!selectedRunId.value || !runs.value.some((run) => run.runId === selectedRunId.value)) {
      selectedRunId.value = runs.value[0]?.runId ?? ''
    }
  } catch (error) {
    analysisErrorMessage.value = error instanceof Error ? error.message : '实验记录加载失败'
  }
}

async function loadAnalysis(): Promise<void> {
  stopReplay()
  replayIndex.value = 0
  replayEnded.value = false
  analysisErrorMessage.value = ''

  if (!selectedRunId.value || !selectedStationId.value) {
    qualityMetrics.value = null
    timelinePoints.value = []
    replayPoints.value = []
    return
  }

  isLoadingAnalysis.value = true

  try {
    const [timelinePayload, replayPayload, qualityPayload] = await Promise.all([
      fetchExperimentTimeline(selectedRunId.value),
      fetchExperimentReplay(selectedRunId.value, selectedStationId.value, 300),
      fetchExperimentQualityMetrics(selectedRunId.value)
    ])
    timelinePoints.value = timelinePayload
    replayPoints.value = replayPayload
    qualityMetrics.value = qualityPayload
  } catch (error) {
    qualityMetrics.value = null
    timelinePoints.value = []
    replayPoints.value = []
    analysisErrorMessage.value = error instanceof Error ? error.message : '实验分析数据加载失败'
  } finally {
    isLoadingAnalysis.value = false
  }
}

async function refreshPage(): Promise<void> {
  await loadStationsSnapshot()
  await loadRuns()
  await loadAnalysis()
}

function startStationAutoRefresh(): void {
  stopStationAutoRefresh()
  stationRefreshTimer = window.setInterval(() => {
    void loadStationsSnapshot()
  }, AUTO_REFRESH_INTERVAL_MS)
}

function stopStationAutoRefresh(): void {
  if (stationRefreshTimer !== undefined) {
    window.clearInterval(stationRefreshTimer)
    stationRefreshTimer = undefined
  }
}

function stopReplay(): void {
  if (replayTimer !== undefined) {
    window.clearInterval(replayTimer)
    replayTimer = undefined
  }
  isReplayPlaying.value = false
}

function startReplay(): void {
  if (replayPoints.value.length === 0 || isReplayPlaying.value) {
    return
  }

  if (replayIndex.value >= replayPoints.value.length - 1) {
    replayIndex.value = 0
    replayEnded.value = false
  }

  isReplayPlaying.value = true
  replayTimer = window.setInterval(() => {
    if (replayIndex.value >= replayPoints.value.length - 1) {
      replayEnded.value = true
      stopReplay()
      return
    }
    replayIndex.value += 1
  }, REPLAY_INTERVAL_MS)
}

function resetReplay(): void {
  stopReplay()
  replayIndex.value = 0
  replayEnded.value = false
}

function seekReplay(index: number): void {
  stopReplay()
  replayIndex.value = Math.min(Math.max(index, 0), Math.max(replayPoints.value.length - 1, 0))
  replayEnded.value = replayIndex.value >= Math.max(replayPoints.value.length - 1, 0) && replayPoints.value.length > 0
}

async function handleRunChange(): Promise<void> {
  await loadAnalysis()
}

async function handleStationChange(): Promise<void> {
  await loadAnalysis()
}

async function handleMapSelect(stationId: string): Promise<void> {
  if (selectedStationId.value === stationId) {
    return
  }
  selectedStationId.value = stationId
  await handleStationChange()
}

function chartEmptyText(points: LinkTrendDatum[]): string {
  if (!selectedRun.value) {
    return '暂无实验记录'
  }
  if (!selectedStation.value) {
    return '暂无场站'
  }
  if (points.length === 0) {
    return `所选实验在 ${selectedStation.value.stationName} 上暂无可用趋势数据`
  }
  return ''
}

onMounted(() => {
  void refreshPage()
  startStationAutoRefresh()
})

onBeforeUnmount(() => {
  stopStationAutoRefresh()
  stopReplay()
})
</script>

<template>
  <section class="link-quality page-shell">
    <header class="link-quality__header">
      <div>
        <span>链路质量监测</span>
        <h2>场站链路状态、通信指标与恢复回放联动监测</h2>
        <p>实时读取场站链路状态，联动最近实验的时延、丢包率、数据时效、切换日志与恢复回放。</p>
      </div>
      <div class="link-quality__controls">
        <label>
          <span>实验记录</span>
          <select v-model="selectedRunId" :disabled="isLoadingAnalysis || runs.length === 0" @change="handleRunChange">
            <option v-for="run in runs" :key="run.runId" :value="run.runId">
              {{ run.scenarioName }} / {{ run.status }} / {{ formatDateTime(run.startedAt) }}
            </option>
          </select>
        </label>
        <label>
          <span>场站筛选</span>
          <select
            v-model="selectedStationId"
            :disabled="isLoadingStations || stations.length === 0"
            @change="handleStationChange"
          >
            <option v-for="station in stations" :key="station.stationId" :value="station.stationId">
              {{ station.stationName }}
            </option>
          </select>
        </label>
        <button type="button" :disabled="isLoadingStations || isLoadingAnalysis" @click="refreshPage">手动刷新</button>
      </div>
    </header>

    <section class="link-quality__kpis">
      <article>
        <span>在线场站</span>
        <strong>{{ overviewMetrics.onlineCount }}</strong>
        <small>共 {{ stations.length }} 个场站</small>
      </article>
      <article>
        <span>异常链路</span>
        <strong>{{ overviewMetrics.abnormalCount }}</strong>
        <small>状态不为 S0 的场站数</small>
      </article>
      <article>
        <span>平均时延</span>
        <strong>{{ overviewMetrics.averageLatency }} ms</strong>
        <small>当前场站实时均值</small>
      </article>
      <article>
        <span>平均丢包率</span>
        <strong>{{ overviewMetrics.averagePacketLossRate }}%</strong>
        <small>当前场站实时均值</small>
      </article>
      <article>
        <span>最差链路状态</span>
        <strong :style="{ color: linkStateMetaMap[overviewMetrics.worstState].color }">{{ currentWorstStateLabel }}</strong>
        <small>按 S3-S0 严重程度评估</small>
      </article>
      <article>
        <span>最近更新时间</span>
        <strong>{{ lastUpdatedAt || '--' }}</strong>
        <small>{{ currentDataSourceLabel }} / 每 5 秒自动刷新</small>
      </article>
    </section>

    <p v-if="stationErrorMessage" class="link-quality__error">{{ stationErrorMessage }}</p>
    <p v-if="analysisErrorMessage" class="link-quality__error">{{ analysisErrorMessage }}</p>

    <section class="link-quality__topology">
      <LinkQualityMap
        :stations="mapStations"
        :selected-station-id="selectedStationId"
        :replay-point="currentReplayPoint"
        @select="handleMapSelect"
      />

      <div class="link-quality__side">
        <section class="link-quality__panel">
          <div class="link-quality__panel-head">
            <span>当前场站详情</span>
            <h3>{{ selectedStation?.stationName ?? '未选择场站' }}</h3>
          </div>
          <div v-if="selectedStation" class="link-quality__station-grid">
            <article>
              <span>链路状态</span>
              <strong :style="{ color: linkStateMetaMap[selectedStation.linkState].color }">
                {{ selectedStation.linkState }} {{ linkStateMetaMap[selectedStation.linkState].label }}
              </strong>
            </article>
            <article>
              <span>通信质量</span>
              <strong>{{ selectedStation.latency }} ms / {{ selectedStation.packetLossRate }}%</strong>
            </article>
            <article>
              <span>数据时效</span>
              <strong>{{ selectedStation.dataAge }} s / {{ selectedStation.online ? '在线' : '离线' }}</strong>
            </article>
            <article>
              <span>运行功率</span>
              <strong>{{ selectedStation.activePower }} kW / {{ selectedStation.availableCapacity }} kW</strong>
            </article>
          </div>
          <p v-else class="link-quality__empty">当前没有可用场站。</p>
        </section>

        <section class="link-quality__panel">
          <div class="link-quality__panel-head">
            <span>通信状态分布</span>
            <h3>{{ selectedRun?.scenarioName ?? '暂无实验运行' }}</h3>
          </div>
          <div v-if="qualityMetrics?.stateDistribution.length" class="link-quality__chart-box">
            <EChartPanel :option="stateDistributionOption" height="240px" />
          </div>
          <p v-else class="link-quality__empty">所选实验暂无状态分布数据。</p>
        </section>

        <section class="link-quality__panel">
          <div class="link-quality__panel-head">
            <span>场站质量排名</span>
            <h3>质量评分与在线率</h3>
          </div>
          <div v-if="qualityMetrics?.stationRanking.length" class="link-quality__chart-box">
            <EChartPanel :option="rankingOption" height="240px" />
          </div>
          <p v-else class="link-quality__empty">所选实验暂无质量排名数据。</p>
          <div v-if="selectedQualityRank" class="link-quality__score-card">
            <span>当前选中评分</span>
            <strong>{{ selectedQualityRank.qualityScore }}</strong>
            <small>
              在线率 {{ selectedQualityRank.onlineRate }}% / 平均时延 {{ selectedQualityRank.averageLatency }} ms
            </small>
          </div>
        </section>
      </div>
    </section>

    <section class="link-quality__trends">
      <article class="link-quality__panel">
        <div class="link-quality__panel-head">
          <span>时延与抖动趋势</span>
          <h3>{{ selectedStation?.stationName ?? '未选择场站' }}</h3>
        </div>
        <div class="link-quality__chart-box">
          <EChartPanel :option="latencyTrendOption" height="240px" />
          <p v-if="chartEmptyText(trendData)" class="link-quality__chart-empty">{{ chartEmptyText(trendData) }}</p>
        </div>
      </article>

      <article class="link-quality__panel">
        <div class="link-quality__panel-head">
          <span>丢包率趋势</span>
          <h3>{{ selectedRun?.scenarioCode ?? '未选择实验' }}</h3>
        </div>
        <div class="link-quality__chart-box">
          <EChartPanel :option="packetLossTrendOption" height="240px" />
          <p v-if="chartEmptyText(trendData)" class="link-quality__chart-empty">{{ chartEmptyText(trendData) }}</p>
        </div>
      </article>

      <article class="link-quality__panel">
        <div class="link-quality__panel-head">
          <span>数据时效趋势</span>
          <h3>{{ selectedRun?.scenarioName ?? '未选择实验' }}</h3>
        </div>
        <div class="link-quality__chart-box">
          <EChartPanel :option="dataAgeTrendOption" height="240px" />
          <p v-if="chartEmptyText(trendData)" class="link-quality__chart-empty">{{ chartEmptyText(trendData) }}</p>
        </div>
      </article>
    </section>

    <section class="link-quality__bottom">
      <LinkSwitchLog :entries="switchLogEntries" :station-scope-label="stationScopeLabel" />

      <LinkRecoveryReplay
        :points="replayPoints"
        :current-index="replayIndex"
        :is-playing="isReplayPlaying"
        :is-loading="isLoadingAnalysis"
        :replay-ended="replayEnded"
        :disabled-reason="replayDisabledReason"
        @play="startReplay"
        @pause="stopReplay"
        @reset="resetReplay"
        @seek="seekReplay"
      />
    </section>
  </section>
</template>

<style scoped>
.link-quality {
  display: grid;
  gap: 16px;
}

.link-quality__header,
.link-quality__kpis article,
.link-quality__panel {
  border: 1px solid rgba(72, 224, 255, 0.24);
  border-radius: 8px;
  background: linear-gradient(180deg, rgba(8, 34, 128, 0.82), rgba(4, 24, 95, 0.88));
}

.link-quality__header {
  display: flex;
  justify-content: space-between;
  gap: 18px;
  padding: 18px;
}

.link-quality__header h2,
.link-quality__header p,
.link-quality__panel h3,
.link-quality__panel p {
  margin: 0;
}

.link-quality__header span,
.link-quality__panel-head span,
.link-quality__kpis span {
  color: #62ffb6;
  font-size: 13px;
}

.link-quality__header h2 {
  margin-top: 6px;
  font-size: 28px;
}

.link-quality__header p {
  margin-top: 8px;
  color: #9ddfff;
  line-height: 1.65;
  max-width: 760px;
}

.link-quality__controls {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  min-width: 460px;
}

.link-quality__controls label {
  display: grid;
  gap: 6px;
}

.link-quality__controls select,
.link-quality__controls button {
  border: 1px solid rgba(72, 224, 255, 0.25);
  color: #ffffff;
  background: rgba(0, 22, 76, 0.78);
  padding: 9px 10px;
}

.link-quality__controls button {
  align-self: end;
  cursor: pointer;
  background: linear-gradient(180deg, rgba(18, 146, 220, 0.9), rgba(6, 83, 157, 0.9));
}

.link-quality__controls button:disabled {
  opacity: 0.48;
  cursor: not-allowed;
}

.link-quality__kpis {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 12px;
}

.link-quality__kpis article {
  display: grid;
  gap: 8px;
  padding: 14px;
}

.link-quality__kpis strong {
  color: #f6d87b;
  font-size: 22px;
}

.link-quality__kpis small {
  color: #9ddfff;
}

.link-quality__error,
.link-quality__empty {
  margin: 0;
  padding: 10px 12px;
  border: 1px solid rgba(239, 100, 87, 0.5);
  color: #ffb2a9;
  background: rgba(105, 22, 35, 0.48);
}

.link-quality__topology {
  display: grid;
  grid-template-columns: minmax(0, 1.45fr) minmax(320px, 0.8fr);
  gap: 12px;
}

.link-quality__side {
  display: grid;
  gap: 12px;
}

.link-quality__panel {
  display: grid;
  gap: 12px;
  padding: 16px;
}

.link-quality__panel-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
}

.link-quality__station-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.link-quality__station-grid article {
  padding: 10px 12px;
  border: 1px solid rgba(72, 224, 255, 0.14);
  background: rgba(4, 29, 111, 0.42);
}

.link-quality__station-grid span {
  display: block;
  color: #9ddfff;
  font-size: 12px;
}

.link-quality__station-grid strong {
  display: block;
  margin-top: 6px;
}

.link-quality__chart-box {
  position: relative;
}

.link-quality__chart-empty {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 18px;
  color: #9ddfff;
  text-align: center;
  background: rgba(4, 14, 48, 0.46);
}

.link-quality__score-card {
  padding: 12px;
  border: 1px solid rgba(72, 224, 255, 0.18);
  background: rgba(4, 29, 111, 0.42);
}

.link-quality__score-card span {
  display: block;
  color: #9ddfff;
  font-size: 12px;
}

.link-quality__score-card strong {
  display: block;
  margin-top: 6px;
  color: #ffd44f;
  font-size: 26px;
}

.link-quality__score-card small {
  display: block;
  margin-top: 6px;
  color: #9ddfff;
}

.link-quality__trends {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.link-quality__bottom {
  display: grid;
  grid-template-columns: minmax(0, 1.1fr) minmax(0, 0.9fr);
  gap: 12px;
}

@media (max-width: 1440px) {
  .link-quality__kpis {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .link-quality__trends {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 1180px) {
  .link-quality__header,
  .link-quality__topology,
  .link-quality__bottom {
    grid-template-columns: 1fr;
  }

  .link-quality__header {
    display: grid;
  }

  .link-quality__controls {
    min-width: 0;
  }
}

@media (max-width: 760px) {
  .link-quality__controls,
  .link-quality__kpis,
  .link-quality__station-grid,
  .link-quality__bottom {
    grid-template-columns: 1fr;
  }

  .link-quality__panel-head {
    display: grid;
  }
}
</style>
