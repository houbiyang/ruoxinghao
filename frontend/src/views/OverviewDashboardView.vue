<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import type { EChartsOption } from 'echarts'
import { useRouter } from 'vue-router'

import NorthwestEnergyScene from '@/components/background/NorthwestEnergyScene.vue'
import EChartPanel from '@/components/charts/EChartPanel.vue'
import { demoScenarioProfiles } from '@/mock/systemData'
import { useSystemStore } from '@/stores/system'
import { LinkState, DemoScenario, ResourceType, type StationState } from '@/types/system'

const systemStore = useSystemStore()
const router = useRouter()
const currentTime = ref('')
let timeTimer = 0
let refreshTimer = 0

function updateTimeText(): void {
  currentTime.value = new Date().toLocaleString('zh-CN', {
    hour12: false,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit'
  })
}

const topMetrics = computed(() => [
  { label: '累计节约电量', value: '581.1', unit: 'MW·h' },
  { label: '累计运行天数', value: '673', unit: '天' },
  { label: '累计系统收益', value: '44.4', unit: '万元' },
  { label: '投资回收期', value: '5.49', unit: '年' },
  { label: '倒计时', value: '1329', unit: '天' }
])

const basicInfoCards = computed(() => [
  { label: '电压等级', value: '0.4', unit: 'kV' },
  { label: '变压器容量', value: '500', unit: 'kVA' },
  { label: '光伏容量', value: '300', unit: 'kW' },
  { label: '风电容量', value: '5', unit: 'kW' },
  { label: '储能容量', value: '60kW/107', unit: 'kWh' },
  { label: '充电桩规模', value: '603', unit: 'kW' }
])

const energyAnalysisCards = computed(() => [
  { label: '年用电量', value: '487.1', unit: 'MW·h' },
  { label: '月用电量', value: '9.4', unit: 'MW·h' },
  { label: '日用电量', value: '2', unit: 'MW·h' },
  { label: '当月需量', value: '293.1', unit: 'kW' }
])

const socialBenefitCards = computed(() => [
  { label: '新能源年发电量', value: '128.6', unit: 'MW·h' },
  { label: '新能源年有效小时数', value: '856', unit: 'h' },
  { label: '新能源年自用电量', value: '128.6', unit: 'MW·h' },
  { label: '新能源年上网电量', value: '0', unit: 'kW·h' },
  { label: '减少碳排放', value: '74.7', unit: 't' },
  { label: '节约标准煤', value: '38.9', unit: 't' },
  { label: '等效植树数', value: '102', unit: '棵' },
  { label: '等效绿证数', value: '128', unit: '张' }
])

const centerSummaryCards = computed(() => [
  { title: '峰谷套利', rows: [['当月节约电费', '107 元'], ['累计节约电费', '7966.1 元']] },
  { title: '需量管理', rows: [['当月降低需量', '85.66 kW'], ['当月节约电费', '4385.8 元'], ['累计节约电费', '60745.2 元']] },
  { title: '新能源消纳', rows: [['当月消纳电量', '3.3 MW·h'], ['累计节约电费', '40.8 万元']] },
  { title: '综合用电成本对比', rows: [['投入前', '0.75 元/kW·h'], ['投入后', '0.54 元/kW·h']] }
])

const selectedStation = computed<StationState | undefined>(() => systemStore.selectedStation)
const activeLinkState = computed(() => selectedStation.value?.linkState ?? LinkState.S0)

const consumptionRanking = computed(() => [
  { name: '光伏发电表', power: 135.9, energy: 1408.2, rank: 1 },
  { name: '高压柜', power: -10.4, energy: 750, rank: 2 },
  { name: '馈线柜6-地源热泵', power: 19.4, energy: 585.4, rank: 3 },
  { name: '2#光伏变流器', power: 49.1, energy: 476.6, rank: 4 }
])

const resourceConsumptionOption = computed<EChartsOption>(() => ({
  tooltip: {
    trigger: 'item',
    backgroundColor: 'rgba(6, 31, 113, 0.94)',
    borderColor: 'rgba(72, 224, 255, 0.28)',
    textStyle: { color: '#e7f7ff' },
    formatter: '{b}<br/>电量：{c} MW·h'
  },
  series: [
    {
      type: 'pie',
      radius: ['54%', '74%'],
      center: ['50%', '42%'],
      avoidLabelOverlap: true,
      label: { color: '#9bd6ff', formatter: '{b}' },
      itemStyle: { borderColor: '#06206e', borderWidth: 2 },
      data: [
        { name: '电网用电', value: 6.1, itemStyle: { color: '#ffe24d' } },
        { name: '新能源消纳', value: 3.3, itemStyle: { color: '#62ffb6' } }
      ]
    }
  ]
}))

const cleanEnergyOption = computed<EChartsOption>(() => ({
  tooltip: {
    trigger: 'item',
    backgroundColor: 'rgba(6, 31, 113, 0.94)',
    borderColor: 'rgba(72, 224, 255, 0.28)',
    textStyle: { color: '#e7f7ff' },
    formatter: '{b}<br/>电量：{c} MW·h'
  },
  series: [
    {
      type: 'pie',
      radius: ['54%', '74%'],
      center: ['50%', '42%'],
      label: { color: '#9bd6ff', formatter: '{b}' },
      itemStyle: { borderColor: '#06206e', borderWidth: 2 },
      data: [
        { name: '电网供电', value: 379.3, itemStyle: { color: '#ffe24d' } },
        { name: '新能源供能', value: 111.9, itemStyle: { color: '#62ffb6' } }
      ]
    }
  ]
}))

const operationCurveOption = computed<EChartsOption>(() => ({
  tooltip: {
    trigger: 'axis',
    backgroundColor: 'rgba(6, 31, 113, 0.94)',
    borderColor: 'rgba(72, 224, 255, 0.28)',
    textStyle: { color: '#e7f7ff' }
  },
  grid: { top: 36, right: 16, bottom: 34, left: 42 },
  legend: {
    top: 4,
    right: 10,
    textStyle: { color: '#9bd6ff' },
    itemWidth: 10,
    itemHeight: 10
  },
  xAxis: {
    type: 'time',
    axisLabel: {
      color: '#78bfff',
      formatter: (value: number) => new Intl.DateTimeFormat('zh-CN', {
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit',
        hour12: false
      }).format(new Date(value))
    },
    axisLine: { lineStyle: { color: 'rgba(72, 224, 255, 0.18)' } }
  },
  yAxis: {
    type: 'value',
    name: 'kW',
    nameTextStyle: { color: '#78bfff' },
    axisLabel: { color: '#78bfff' },
    splitLine: { lineStyle: { color: 'rgba(72, 224, 255, 0.08)' } }
  },
  series: [
    {
      name: '发电功率',
      type: 'line',
      smooth: true,
      symbol: 'none',
      lineStyle: { width: 3, color: '#62ffb6' },
      data: systemStore.powerFlowTrend.map((point) => [point.timestamp, point.generationPower])
    },
    {
      name: '用电功率',
      type: 'line',
      smooth: true,
      symbol: 'circle',
      symbolSize: 5,
      lineStyle: { width: 3, color: '#42e8ff' },
      data: systemStore.powerFlowTrend.map((point) => [point.timestamp, point.consumptionPower])
    }
  ]
}))

const weeklyCostOption = computed<EChartsOption>(() => ({
  tooltip: {
    trigger: 'axis',
    axisPointer: { type: 'shadow' },
    backgroundColor: 'rgba(6, 31, 113, 0.94)',
    borderColor: 'rgba(72, 224, 255, 0.28)',
    textStyle: { color: '#e7f7ff' }
  },
  grid: { top: 36, right: 16, bottom: 26, left: 42 },
  legend: {
    top: 4,
    right: 10,
    textStyle: { color: '#9bd6ff' }
  },
  xAxis: {
    type: 'category',
    data: ['2025-05-29', '2025-05-31', '2025-06-02', '2025-06-04'],
    axisLabel: { color: '#78bfff' },
    axisLine: { lineStyle: { color: 'rgba(72, 224, 255, 0.18)' } }
  },
  yAxis: {
    type: 'value',
    name: '元',
    nameTextStyle: { color: '#78bfff' },
    axisLabel: { color: '#78bfff' },
    splitLine: { lineStyle: { color: 'rgba(72, 224, 255, 0.08)' } }
  },
  series: [
    {
      name: '优化前',
      type: 'bar',
      barGap: '20%',
      itemStyle: { color: '#ff9d3f' },
      data: [2620, 1880, 1320, 2380]
    },
    {
      name: '优化后',
      type: 'bar',
      itemStyle: { color: '#42e8ff' },
      data: [1500, 1450, 1020, 1360]
    }
  ]
}))

const demandCurveOption = computed<EChartsOption>(() => ({
  animationDuration: 600,
  tooltip: {
    trigger: 'axis',
    backgroundColor: 'rgba(6, 31, 113, 0.94)',
    borderColor: 'rgba(72, 224, 255, 0.28)',
    textStyle: { color: '#e7f7ff' }
  },
  grid: { top: 34, right: 16, bottom: 26, left: 42 },
  legend: {
    top: 2,
    right: 10,
    textStyle: { color: '#9bd6ff' }
  },
  xAxis: {
    type: 'category',
    data: ['00:00', '01:20', '02:40', '04:00', '05:20', '06:40', '08:00', '09:20', '10:40', '12:00', '13:20', '14:40'],
    axisLabel: { color: '#78bfff' },
    axisLine: { lineStyle: { color: 'rgba(72, 224, 255, 0.18)' } }
  },
  yAxis: {
    type: 'value',
    name: 'kW',
    nameTextStyle: { color: '#78bfff' },
    axisLabel: { color: '#78bfff' },
    splitLine: { lineStyle: { color: 'rgba(72, 224, 255, 0.08)' } }
  },
  series: [
    {
      name: '优化前',
      type: 'line',
      smooth: true,
      symbol: 'none',
      lineStyle: { width: 3, color: '#ffd44f' },
      data: [40, 54, 62, 68, 72, 110, 150, 182, 196, 228, 242, 160]
    },
    {
      name: '优化后',
      type: 'line',
      smooth: true,
      symbol: 'none',
      lineStyle: { width: 3, color: '#42e8ff' },
      data: [62, 58, 56, 54, 52, 46, 42, 38, 44, 66, 74, 58]
    }
  ]
}))

function handleScenarioChange(scenario: DemoScenario): void {
  systemStore.applyScenario(scenario)
}

function openStationDetail(stationId: string): void {
  systemStore.selectStation(stationId)
  void router.push(`/stations/${stationId}`)
}

function getStationByType(resourceType: ResourceType): StationState | undefined {
  return systemStore.stations.find((station) => station.resourceType === resourceType)
}

const sceneHighlightRows = computed(() => {
  return [
    getStationByType(ResourceType.Photovoltaic),
    getStationByType(ResourceType.Wind),
    getStationByType(ResourceType.Thermal),
    getStationByType(ResourceType.Grid),
    getStationByType(ResourceType.Storage),
    getStationByType(ResourceType.Load),
    getStationByType(ResourceType.Charging)
  ].filter((station): station is StationState => Boolean(station))
})

onMounted(() => {
  updateTimeText()
  timeTimer = window.setInterval(updateTimeText, 1000)
  void systemStore.refreshFromApi()
  refreshTimer = window.setInterval(() => void systemStore.refreshFromApi(), 5000)
})

onBeforeUnmount(() => {
  window.clearInterval(timeTimer)
  window.clearInterval(refreshTimer)
})
</script>

<template>
  <section class="dashboard-screen page-shell">
    <header class="dashboard-screen__hero">
      <div class="dashboard-screen__hero-edge" />
      <div class="dashboard-screen__hero-corners dashboard-screen__hero-corners--left" />
      <div class="dashboard-screen__hero-corners dashboard-screen__hero-corners--right" />
      <h1>微电网智慧能源平台</h1>
      <div class="dashboard-screen__hero-actions">
        <span class="dashboard-screen__time">{{ currentTime }}</span>
      </div>
    </header>

    <p v-if="systemStore.isLoading" class="dashboard-screen__loading">正在读取模拟器实时数据…</p>
    <p v-else-if="systemStore.apiError" class="dashboard-screen__loading is-error">{{ systemStore.apiError }}</p>

    <div v-else class="dashboard-screen__layout">
      <section class="dashboard-screen__column dashboard-screen__column--center">
        <section class="screen-panel screen-panel--scene">
          <NorthwestEnergyScene
            :stations="systemStore.stations"
            :selected-station-id="selectedStation?.stationId"
            :link-state-meta-map="systemStore.linkStateMetaMap"
            :resource-type-label-map="systemStore.resourceTypeLabelMap"
            :active-state="activeLinkState"
            @select-station="openStationDetail"
          />

          <div class="scene-status-row">
            <article
              v-for="station in sceneHighlightRows"
              :key="station.stationId"
              class="scene-status-card"
              :class="{ 'is-active': station.stationId === selectedStation?.stationId }"
              @click="openStationDetail(station.stationId)"
            >
              <div class="scene-status-card__title">
                <span
                  class="scene-status-card__dot"
                  :style="{ background: systemStore.linkStateMetaMap[station.linkState].color }"
                />
                <strong>{{ station.stationName }}</strong>
              </div>
              <p>功率：{{ station.activePower }} kW</p>
              <p>{{ systemStore.linkStateMetaMap[station.linkState].label }}</p>
            </article>
          </div>
        </section>

      </section>

      <aside class="dashboard-screen__column dashboard-screen__column--right">
        <section class="screen-panel"><div class="screen-panel__header"><span>发电与用电功率趋势（近 2 小时）</span></div><EChartPanel v-if="systemStore.powerFlowTrend.length" :option="operationCurveOption" height="clamp(280px, 28vw, 420px)" /><p v-else class="dashboard-screen__empty">暂无模拟器发电与用电趋势数据。</p></section>
      </aside>
    </div>
  </section>
</template>

<style scoped>
.dashboard-screen {
  display: grid;
  gap: 16px;
}

.dashboard-screen__loading {
  margin: 0;
  padding: 32px;
  color: #9bd6ff;
  border: 1px solid rgba(72, 224, 255, 0.24);
  border-radius: 8px;
  background: linear-gradient(180deg, rgba(8, 34, 128, 0.8), rgba(4, 24, 95, 0.84));
  text-align: center;
}

.dashboard-screen__loading.is-error {
  color: #ffb4aa;
  border-color: rgba(239, 100, 87, 0.45);
}

.dashboard-screen__empty {
  margin: 0;
  padding: 88px 16px;
  color: #9bd6ff;
  text-align: center;
}

.dashboard-screen__hero {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 76px;
  border: 1px solid rgba(72, 224, 255, 0.28);
  border-radius: 8px;
  background: linear-gradient(180deg, rgba(7, 39, 145, 0.82), rgba(5, 24, 97, 0.92));
}

.dashboard-screen__hero-edge {
  position: absolute;
  inset: 8px 22%;
  border-top: 2px solid rgba(72, 224, 255, 0.66);
  border-radius: 0 0 56px 56px;
  opacity: 0.68;
}

.dashboard-screen__hero-corners {
  position: absolute;
  top: 10px;
  width: 54px;
  height: 18px;
  border-top: 2px solid rgba(72, 224, 255, 0.66);
}

.dashboard-screen__hero-corners--left {
  left: 34%;
  border-left: 2px solid rgba(72, 224, 255, 0.66);
  transform: skewX(-28deg);
}

.dashboard-screen__hero-corners--right {
  right: 34%;
  border-right: 2px solid rgba(72, 224, 255, 0.66);
  transform: skewX(28deg);
}

.dashboard-screen__hero h1 {
  margin: 0;
  color: #ffffff;
  font-size: 30px;
  letter-spacing: 0.08em;
  text-shadow: none;
}

.dashboard-screen__hero-actions {
  position: absolute;
  right: 18px;
  top: 50%;
  transform: translateY(-50%);
}

.dashboard-screen__time {
  color: #aebfc9;
  font-size: 13px;
}

.dashboard-screen__layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  align-items: start;
  gap: 16px;
}

.dashboard-screen__column {
  min-width: 0;
  display: grid;
  gap: 16px;
}

.screen-panel {
  position: relative;
  border: 1px solid rgba(72, 224, 255, 0.24);
  border-radius: 8px;
  background: linear-gradient(180deg, rgba(8, 34, 128, 0.8), rgba(4, 24, 95, 0.84));
  box-shadow: none;
}

.screen-panel::before,
.screen-panel::after {
  display: none;
}

.screen-panel::before {
  left: 8px;
  border-left: 2px solid rgba(72, 224, 255, 0.6);
}

.screen-panel::after {
  right: 8px;
  border-right: 2px solid rgba(72, 224, 255, 0.6);
}

.screen-panel__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-height: 42px;
  padding: 0 16px;
  color: #f1fbff;
  font-size: 15px;
  font-weight: 700;
  background: linear-gradient(90deg, rgba(30, 168, 255, 0.26), rgba(30, 168, 255, 0.04));
  border-bottom: 1px solid rgba(72, 224, 255, 0.18);
}

.screen-panel__header strong {
  color: #b3e8ff;
  font-size: 13px;
  font-weight: 500;
}

.screen-summary-strip {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  border: 1px solid rgba(72, 224, 255, 0.24);
  border-radius: 8px;
  background: rgba(5, 28, 105, 0.8);
  box-shadow: none;
}

.summary-metric {
  padding: 10px 12px;
  border-right: 1px solid rgba(72, 224, 255, 0.18);
  position: relative;
}

.summary-metric:last-child {
  border-right: 0;
}

.summary-metric::after {
  content: '';
  position: absolute;
  right: 0;
  top: 10px;
  width: 1px;
  height: calc(100% - 20px);
  background: linear-gradient(180deg, transparent, rgba(72, 224, 255, 0.64), transparent);
}

.summary-metric:last-child::after {
  display: none;
}

.summary-metric span,
.summary-metric strong {
  display: block;
}

.summary-metric span {
  color: #a8e0ff;
  font-size: 13px;
}

.summary-metric strong {
  margin-top: 8px;
  color: #ffab39;
  font-size: 22px;
  font-weight: 700;
}

.summary-metric small {
  margin-left: 3px;
  color: #eaf9ff;
  font-size: 14px;
}

.basic-card-grid,
.analysis-card-grid,
.benefit-grid {
  display: grid;
  gap: 10px;
  padding: 12px;
}

.basic-card-grid {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.analysis-card-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.benefit-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.basic-card,
.analysis-card,
.benefit-card,
.summary-card {
  min-width: 0;
}

.basic-card,
.analysis-card,
.benefit-card {
  padding: 12px;
  border: 1px solid rgba(72, 224, 255, 0.18);
  background: rgba(4, 29, 111, 0.54);
  text-align: center;
}

.basic-card strong,
.analysis-card strong,
.benefit-card strong {
  display: block;
  color: #42e8ff;
  font-size: 18px;
}

.basic-card small,
.analysis-card small,
.benefit-card small {
  margin-left: 2px;
  color: #dff7ff;
  font-size: 13px;
}

.basic-card span,
.analysis-card span,
.benefit-card span {
  display: block;
  margin-top: 6px;
  color: #9ad8ff;
  font-size: 12px;
}

.analysis-card {
  text-align: left;
}

.analysis-card strong {
  margin-top: 6px;
}

.donut-chart-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  padding: 0 12px 12px;
}

.mini-chart-card {
  padding: 10px;
  border: 1px solid rgba(72, 224, 255, 0.18);
  background: rgba(4, 29, 111, 0.54);
}

.mini-chart-card p {
  margin: 2px 0 0;
  color: #b3e8ff;
  font-size: 12px;
}

.screen-panel--scene {
  padding-bottom: 10px;
  overflow: hidden;
}

.scene-status-row {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(130px, 1fr));
  gap: 8px;
  padding: 0 10px 0;
}

.scene-status-card {
  padding: 8px 10px;
  border: 1px solid rgba(72, 224, 255, 0.14);
  background: rgba(4, 29, 111, 0.42);
  cursor: pointer;
}

.scene-status-card.is-active {
  border-color: rgba(255, 212, 79, 0.6);
}

.scene-status-card__title {
  display: flex;
  align-items: center;
  gap: 6px;
}

.scene-status-card__title strong {
  font-size: 13px;
}

.scene-status-card__dot {
  width: 8px;
  height: 8px;
}

.scene-status-card p {
  margin: 6px 0 0;
  color: #a8ddff;
  font-size: 11px;
}

.center-action-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 2px 0;
}

.scenario-button-group {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.scenario-button {
  min-width: 88px;
  padding: 10px 14px;
  border: 1px solid rgba(72, 224, 255, 0.22);
  background: rgba(6, 34, 130, 0.56);
  color: #b6ecff;
  cursor: pointer;
}

.scenario-button.is-active {
  color: #ffffff;
  border-color: rgba(255, 212, 79, 0.64);
  box-shadow: inset 0 0 12px rgba(255, 212, 79, 0.16);
}

.scenario-button:hover {
  box-shadow: inset 0 0 16px rgba(72, 224, 255, 0.14);
}

.control-mode-badge {
  padding: 10px 14px;
  border: 1px solid rgba(72, 224, 255, 0.22);
  background: rgba(6, 34, 130, 0.56);
  color: #62ffb6;
  white-space: nowrap;
}

.center-summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
}

.summary-card__rows {
  padding: 12px 16px 14px;
}

.summary-card__rows p {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin: 0 0 8px;
  color: #b4e8ff;
}

.summary-card__rows p:last-child {
  margin-bottom: 0;
}

.summary-card__rows strong {
  color: #ffffff;
}

.ranking-panel {
  padding-bottom: 8px;
}

.ranking-table {
  padding: 10px 12px;
}

.ranking-table__head,
.ranking-table__row {
  display: grid;
  grid-template-columns: 52px 1.2fr 0.9fr 1fr;
  gap: 10px;
  align-items: center;
}

.ranking-table__head {
  padding: 8px 10px;
  color: #59d4ff;
  font-weight: 700;
  border-bottom: 1px solid rgba(72, 224, 255, 0.16);
}

.ranking-table__row {
  margin-top: 8px;
  padding: 8px 10px;
  color: #dff7ff;
  background: rgba(7, 33, 121, 0.42);
  border-left: 2px solid rgba(72, 224, 255, 0.24);
}

.ranking-table__rank {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 22px;
  color: #07155c;
  font-weight: 700;
  background: linear-gradient(180deg, #ffcb47, #ff8b36);
}

@media (max-width: 1680px) {
  .screen-summary-strip {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .center-summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 1366px) {
  .basic-card-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .scene-status-row {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 1180px) {
  .dashboard-screen__layout {
    grid-template-columns: 1fr;
  }

  .screen-summary-strip {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .center-action-row {
    flex-direction: column;
    align-items: stretch;
  }
}

@media (max-width: 768px) {
  .dashboard-screen__hero h1 {
    font-size: 22px;
  }

  .dashboard-screen__hero-actions {
    position: static;
    transform: none;
    margin-left: 10px;
  }

  .basic-card-grid,
  .analysis-card-grid,
  .benefit-grid,
  .donut-chart-grid,
  .center-summary-grid,
  .scene-status-row {
    grid-template-columns: 1fr;
  }

  .screen-summary-strip {
    grid-template-columns: 1fr;
  }
}
</style>
