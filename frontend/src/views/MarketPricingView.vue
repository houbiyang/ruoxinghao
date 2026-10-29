<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import type { EChartsOption } from 'echarts'

import {
  fetchLatestForecastSnapshot,
  fetchLatestMarketSignal,
  fetchMarketDashboardOverview,
  refreshSimulatorMarketData
} from '@/api/systemApi'
import EChartPanel from '@/components/charts/EChartPanel.vue'
import type { MarketDashboardOverview, MarketDashboardRange, SimulatorMarketDataRefresh } from '@/types/system'

type PriceTrendPoint = {
  time: string
  realtime: number
  dayAhead: number
}

type PriceTableRow = {
  period: string
  realtime: number | string
  dayAhead: number | string
}

const dashboardOverview = ref<MarketDashboardOverview | null>(null)
const dashboardLoading = ref(false)
const dashboardError = ref('')
const latestSimulatorMarketData = ref<SimulatorMarketDataRefresh | null>(null)
const simulatorMarketDataLoading = ref(false)
const simulatorMarketDataError = ref('')
const periodTabs = ['24小时', '48小时', '7天']

const activePeriodTab = ref('24小时')

const periodRangeMap: Record<string, MarketDashboardRange> = {
  '24小时': '24H',
  '48小时': '48H',
  '7天': '7D'
}

const currentRealtimePrice = computed(() => latestSimulatorMarketData.value?.marketSignal.realTimePrice ?? '--')
const latestTelemetryReceivedAt = computed(() => latestSimulatorMarketData.value?.telemetryReceivedAt ?? '')

const priceTrendData = computed<PriceTrendPoint[]>(() => {
  return dashboardOverview.value?.priceTrend.points.map((item) => ({
    time: item.time,
    realtime: toNumber(item.realTimePrice),
    dayAhead: toNumber(item.dayAheadPrice)
  })) ?? []
})

const tableRows = computed<PriceTableRow[]>(() => {
  return dashboardOverview.value?.timeOfUsePrices.map((item) => ({
    period: item.period,
    realtime: toNumber(item.realTimePrice),
    dayAhead: toNumber(item.dayAheadPrice)
  })) ?? []
})

const trendChartOption = computed<EChartsOption>(() => ({
  animationDuration: 700,
  tooltip: {
    trigger: 'axis',
    backgroundColor: 'rgba(6, 20, 54, 0.96)',
    borderColor: 'rgba(91, 174, 255, 0.35)',
    textStyle: { color: '#e6f2ff' }
  },
  legend: {
    top: 0,
    left: 16,
    textStyle: { color: '#b6c9ef', fontSize: 12 },
    itemWidth: 16,
    itemHeight: 6,
    data: ['实时电价', '日前电价']
  },
  grid: { top: 44, left: 48, right: 18, bottom: 28 },
  xAxis: {
    type: 'category',
    boundaryGap: false,
    data: priceTrendData.value.map((item) => item.time),
    axisLabel: { color: '#8ca9d6', fontSize: 11 },
    axisLine: { lineStyle: { color: 'rgba(76, 110, 170, 0.45)' } },
    axisTick: { show: false }
  },
  yAxis: {
    type: 'value',
    name: '元/kWh',
    min: 0,
    max: 1.5,
    interval: 0.3,
    nameTextStyle: { color: '#8ca9d6', padding: [0, 0, 0, -8] },
    axisLabel: { color: '#8ca9d6', fontSize: 11 },
    axisLine: { show: false },
    splitLine: { lineStyle: { color: 'rgba(57, 86, 135, 0.32)' } }
  },
  series: [
    buildLineSeries('实时电价', '#6be97f', priceTrendData.value.map((item) => item.realtime)),
    buildLineSeries('日前电价', '#46a0ff', priceTrendData.value.map((item) => item.dayAhead))
  ]
}))

function buildLineSeries(name: string, color: string, data: number[]) {
  return {
    name,
    type: 'line',
    smooth: true,
    symbol: 'circle',
    symbolSize: 6,
    lineStyle: { color, width: 2 },
    itemStyle: { color },
    data
  }
}

async function loadMarketDashboardOverview(): Promise<void> {
  dashboardLoading.value = true
  dashboardError.value = ''
  try {
    dashboardOverview.value = await fetchMarketDashboardOverview(periodRangeMap[activePeriodTab.value] ?? '24H', 5)
  } catch (error) {
    dashboardError.value = error instanceof Error ? error.message : '市场与电价数据加载失败'
  } finally {
    dashboardLoading.value = false
  }
}

async function loadLatestSimulatorMarketData(): Promise<void> {
  try {
    const [marketSignal, forecast] = await Promise.all([
      fetchLatestMarketSignal(),
      fetchLatestForecastSnapshot()
    ])
    if (marketSignal.source === 'SIMULATOR_LIVE_TELEMETRY' && forecast.source === marketSignal.source) {
      latestSimulatorMarketData.value = {
        marketSignal,
        forecast,
        telemetryReceivedAt: marketSignal.receivedAt,
        formulaVersion: marketSignal.version
      }
    }
  } catch {
    latestSimulatorMarketData.value = null
  }
}

async function refreshCurrentRealtimePrice(): Promise<void> {
  simulatorMarketDataLoading.value = true
  simulatorMarketDataError.value = ''
  try {
    latestSimulatorMarketData.value = await refreshSimulatorMarketData()
    await loadMarketDashboardOverview()
  } catch (error) {
    simulatorMarketDataError.value = error instanceof Error ? error.message : '实时电价刷新失败'
  } finally {
    simulatorMarketDataLoading.value = false
  }
}

function toNumber(value: number | string | null | undefined): number {
  if (typeof value === 'number') {
    return Number.isFinite(value) ? value : 0
  }
  if (typeof value === 'string') {
    const parsedValue = Number(value)
    return Number.isFinite(parsedValue) ? parsedValue : 0
  }
  return 0
}

function formatNumber(value: number | string): string {
  return typeof value === 'number' ? value.toFixed(2) : value
}

function formatDateTime(date: Date): string {
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false
  }).format(date).replace(/\//g, '-')
}

function trendClass(value: number | string): string {
  if (typeof value !== 'number') {
    return ''
  }
  if (value >= 1) {
    return 'is-hot'
  }
  if (value >= 0.8) {
    return 'is-warm'
  }
  return 'is-cool'
}

onMounted(() => {
  void loadMarketDashboardOverview()
  void loadLatestSimulatorMarketData()
})

watch(activePeriodTab, () => {
  void loadMarketDashboardOverview()
})

</script>

<template>
  <section class="market-screen">
    <div class="market-screen__body">
      <main class="market-content">
        <div class="market-toolbar">
          <div class="market-toolbar__title">市场概览</div>
        </div>

        <section class="market-current-price" aria-label="当前实时电价">
          <header class="market-section-header">
            <h3>当前实时电价</h3>
            <div class="market-current-price__actions">
              <span>模拟器遥测派生</span>
              <button type="button" :disabled="simulatorMarketDataLoading" @click="refreshCurrentRealtimePrice">
                {{ simulatorMarketDataLoading ? '刷新中' : '刷新实时电价' }}
              </button>
            </div>
          </header>
          <div class="market-current-price__content">
            <strong>{{ currentRealtimePrice }}</strong>
            <span>元/kWh</span>
            <p v-if="latestTelemetryReceivedAt">
              数据源：模拟器实时遥测，接收于 {{ formatDateTime(new Date(latestTelemetryReceivedAt)) }}；
              计算版本 {{ latestSimulatorMarketData?.formulaVersion }}。
            </p>
            <p v-else>{{ simulatorMarketDataError || '暂无可用的模拟器实时电价数据。' }}</p>
          </div>
        </section>

        <div class="market-main-grid">
          <section class="market-card market-chart-card">
            <header class="market-section-header">
              <h3>电价曲线对比</h3>
              <div class="market-section-header__tabs">
                <button
                  v-for="tab in periodTabs"
                  :key="tab"
                  type="button"
                  :class="{ 'is-active': activePeriodTab === tab }"
                  @click="activePeriodTab = tab"
                >
                  {{ tab }}
                </button>
              </div>
            </header>
            <p class="market-data-note">仅展示模拟器实时遥测派生的采样点。</p>
            <EChartPanel :option="trendChartOption" height="300px" />
          </section>
        </div>

        <div class="market-bottom-grid">
          <section class="market-card market-table-card">
            <header class="market-section-header">
              <h3>分时电价表 <small>(元/kWh)</small></h3>
            </header>
            <p class="market-data-note">仅展示模拟器实时遥测派生的采样点。</p>
            <div class="market-table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>时段</th>
                    <th>实时电价</th>
                    <th>日前电价</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="row in tableRows" :key="row.period">
                    <td>{{ row.period }}</td>
                    <td :class="trendClass(row.realtime)">{{ formatNumber(row.realtime) }}</td>
                    <td>{{ formatNumber(row.dayAhead) }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </section>
        </div>
      </main>
    </div>
  </section>
</template>

<style scoped>
.market-screen {
  min-height: 100vh;
  color: #e7f1ff;
  background:
    radial-gradient(circle at 50% 0%, rgba(23, 89, 235, 0.26), transparent 20%),
    linear-gradient(180deg, #020b1f 0%, #071736 18%, #08152f 100%);
}

.market-screen__topbar {
  display: grid;
  grid-template-columns: 276px minmax(0, 1fr) 318px;
  align-items: center;
  gap: 14px;
  height: 54px;
  padding: 0 14px;
  border-bottom: 1px solid rgba(58, 108, 185, 0.44);
  background: rgba(2, 11, 33, 0.92);
  box-shadow: inset 0 -1px 0 rgba(35, 118, 242, 0.18);
}

.market-brand {
  display: flex;
  align-items: center;
  gap: 12px;
}

.market-brand__logo {
  position: relative;
  width: 28px;
  height: 28px;
}

.market-brand__logo span {
  position: absolute;
  inset: 0;
  border: 2px solid rgba(245, 250, 255, 0.95);
  transform: rotate(45deg) scale(0.78);
}

.market-brand__logo span:nth-child(2) {
  transform: rotate(45deg) scale(0.54);
  opacity: 0.78;
}

.market-brand__logo span:nth-child(3) {
  transform: rotate(45deg) scale(0.3);
  opacity: 0.5;
}

.market-brand__title {
  font-size: 14px;
  font-weight: 700;
  letter-spacing: 0.04em;
  white-space: nowrap;
}

.market-primary-nav {
  display: flex;
  justify-content: center;
  gap: 6px;
}

.market-primary-nav__item {
  position: relative;
  min-width: 102px;
  height: 54px;
  padding: 0 16px;
  border: 0;
  color: #b6c9ef;
  background: transparent;
  cursor: pointer;
  font-size: 12px;
}

.market-primary-nav__item.is-active {
  color: #f7fbff;
  background:
    linear-gradient(135deg, rgba(21, 100, 255, 0.18), rgba(40, 133, 255, 0.3)),
    linear-gradient(180deg, rgba(18, 56, 145, 0.96), rgba(11, 36, 104, 0.96));
  clip-path: polygon(8% 0, 100% 0, 92% 100%, 0 100%);
  box-shadow: inset 0 -2px 0 rgba(72, 164, 255, 0.8), 0 0 18px rgba(39, 112, 255, 0.18);
}

.market-topbar__status {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  min-width: 0;
  font-size: 11px;
}

.market-topbar__status > div {
  height: 32px;
  padding: 0 10px;
  border: 1px solid rgba(48, 83, 130, 0.72);
  border-radius: 6px;
  background: rgba(7, 20, 50, 0.92);
  display: inline-flex;
  align-items: center;
  white-space: nowrap;
}

.market-topbar__alarm {
  color: #ff705b;
}

.market-topbar__time {
  line-height: 1.1;
}

.market-screen__body {
  width: min(100%, 1600px);
  padding: 0 0 12px;
  margin: 0 auto;
}

.market-sidebar {
  display: grid;
  gap: 12px;
}

.market-card {
  border: 1px solid rgba(72, 224, 255, 0.24);
  border-radius: 8px;
  background: linear-gradient(180deg, rgba(8, 34, 128, 0.8), rgba(4, 24, 95, 0.84));
  box-shadow: inset 0 0 20px rgba(54, 200, 255, 0.06);
}

.market-sidebar__menu {
  padding: 8px 0;
}

.market-sidebar__item {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 12px;
  height: 43px;
  padding: 0 18px;
  border: 0;
  color: #c8d6f1;
  background: transparent;
  text-align: left;
  cursor: pointer;
}

.market-sidebar__item.is-active {
  color: #ffffff;
  background: linear-gradient(90deg, rgba(22, 92, 222, 0.86), rgba(18, 70, 184, 0.86));
  box-shadow: inset 0 0 16px rgba(110, 190, 255, 0.18);
}

.market-sidebar__icon {
  width: 16px;
  height: 16px;
  border: 1px solid rgba(177, 200, 240, 0.85);
  border-radius: 4px;
  opacity: 0.9;
}

.market-sidebar__status-card {
  padding: 16px 14px 18px;
}

.market-sidebar__status-card h3 {
  margin: 0 0 14px;
  font-size: 14px;
}

.market-sidebar__status-card ul {
  display: grid;
  gap: 12px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.market-sidebar__status-card li {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  color: #b2c5e7;
  font-size: 13px;
}

.market-sidebar__status-card li i {
  width: 10px;
  height: 10px;
  margin-top: 4px;
  border-radius: 50%;
  background: #3a557e;
}

.market-sidebar__status-card li i.is-online {
  background: #2cd66f;
  box-shadow: 0 0 10px rgba(44, 214, 111, 0.45);
}

.market-sidebar__status-card p {
  margin: 40px 0 0;
  color: #6e84ab;
  font-size: 12px;
}

.market-content {
  display: grid;
  gap: 8px;
}

.market-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 0 8px;
  min-height: 40px;
}

.market-toolbar__title {
  font-size: 16px;
  font-weight: 700;
}

.market-toolbar__controls {
  display: flex;
  align-items: center;
  gap: 12px;
  color: #a7bbdd;
  font-size: 12px;
}

.market-toolbar__controls label {
  display: flex;
  align-items: center;
  gap: 8px;
}

.market-toolbar select {
  min-width: 112px;
  height: 30px;
  padding: 0 10px;
  border: 1px solid rgba(48, 83, 130, 0.72);
  border-radius: 6px;
  color: #f0f6ff;
  background: rgba(8, 25, 58, 0.92);
}

.market-toolbar__tabs,
.market-section-header__tabs {
  display: flex;
  gap: 6px;
}

.market-toolbar__tabs button,
.market-section-header__tabs button {
  min-width: 74px;
  height: 30px;
  padding: 0 12px;
  border: 1px solid rgba(48, 83, 130, 0.72);
  border-radius: 6px;
  color: #b8caea;
  background: rgba(8, 25, 58, 0.82);
  cursor: pointer;
}

.market-toolbar__tabs button.is-active,
.market-section-header__tabs button.is-active {
  color: #ffffff;
  border-color: rgba(70, 136, 255, 0.9);
  background: linear-gradient(180deg, #1b63df, #1350b8);
}

.market-card {
  min-width: 0;
  padding: 12px 14px;
}

.market-current-price {
  padding: 14px;
  border: 1px solid rgba(57, 119, 223, 0.58);
  border-radius: 8px;
  background: rgba(8, 31, 83, 0.72);
}

.market-current-price .market-section-header span {
  color: #91b7ec;
  font-size: 12px;
}

.market-current-price__actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.market-current-price__actions button {
  height: 30px;
  padding: 0 12px;
  border: 1px solid rgba(74, 164, 255, 0.78);
  border-radius: 6px;
  color: #dff3ff;
  background: rgba(22, 91, 193, 0.72);
  cursor: pointer;
}

.market-current-price__actions button:disabled {
  cursor: wait;
  opacity: 0.58;
}

.market-current-price__content {
  display: flex;
  align-items: baseline;
  flex-wrap: wrap;
  gap: 8px;
}

.market-current-price__content strong {
  color: #69e57d;
  font-size: 32px;
  line-height: 1.1;
}

.market-current-price__content span {
  color: #c7d8f1;
  font-size: 15px;
}

.market-current-price__content p,
.market-data-note {
  width: 100%;
  margin: 8px 0 0;
  color: #9bb8df;
  font-size: 12px;
  line-height: 1.5;
}

.market-section-header h3 {
  margin: 0;
}

.market-main-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
}

.market-chart-card {
  min-height: 334px;
}

.market-section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
}

.market-bottom-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
}

.market-table-wrap {
  max-height: 340px;
  overflow: auto;
}

.market-table-card {
  min-width: 0;
}

.market-table-wrap table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}

.market-table-wrap th,
.market-table-wrap td {
  padding: 12px 10px;
  text-align: left;
  border-bottom: 1px solid rgba(41, 71, 118, 0.48);
}

.market-table-wrap thead th {
  position: sticky;
  top: 0;
  z-index: 1;
  color: #aecaef;
  background: rgba(9, 25, 59, 0.96);
}

.market-table-wrap td.is-cool {
  color: #69e57d;
}

.market-table-wrap td.is-warm {
  color: #ff8165;
}

.market-table-wrap td.is-hot {
  color: #ff6c52;
}

@media (max-width: 1440px) {
  .market-screen__topbar {
    grid-template-columns: 250px minmax(0, 1fr) 260px;
  }

}

@media (max-width: 1280px) {
  .market-screen__body {
    grid-template-columns: 1fr;
  }

  .market-sidebar {
    grid-template-columns: minmax(0, 1fr) 320px;
  }

  .market-main-grid {
    grid-template-columns: 1fr;
  }

}

@media (max-width: 960px) {
  .market-screen__topbar,
  .market-toolbar,
  .market-section-header {
    grid-template-columns: 1fr;
    display: grid;
    height: auto;
  }

  .market-primary-nav,
  .market-topbar__status,
  .market-toolbar__controls {
    justify-content: flex-start;
    flex-wrap: wrap;
  }

  .market-sidebar {
    grid-template-columns: 1fr;
  }

  .market-sidebar__item {
    height: 40px;
  }

  .market-current-price__actions {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
