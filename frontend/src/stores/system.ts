import { computed, ref } from 'vue'
import { defineStore } from 'pinia'

import {
  activateScenario,
  fetchCurrentScenario,
  fetchDashboardSummary,
  fetchLatestEvents,
  fetchPowerFlowTrend,
  fetchPowerTrend,
  fetchStationDetail,
  fetchStations,
  type DashboardSummaryPayload
} from '@/api/systemApi'
import {
  baseEvents,
  baseStations,
  resourceDetailMap,
  baseTrend,
  controlModeLabelMap,
  demoScenarioProfiles,
  linkStateMetaMap,
  resourceTypeLabelMap
} from '@/mock/systemData'
import {
  ControlMode,
  DemoScenario,
  LinkState,
  ResourceType,
  type AlarmEvent,
  type DashboardSummary,
  type ResourceDetail,
  type PowerFlowTrendPoint,
  type StationState,
  type TimelinePoint
} from '@/types/system'

const dataSource = 'api' as const

function cloneStation(station: StationState): StationState {
  return {
    ...station,
    position: { ...station.position }
  }
}

export function stationPowerMetricLabel(station: StationState): string {
  if ([ResourceType.Photovoltaic, ResourceType.Wind, ResourceType.Thermal].includes(station.resourceType)) {
    return '发电功率'
  }
  if (station.resourceType === ResourceType.Grid) {
    return station.activePower >= 0 ? '市电购电' : '市电上网'
  }
  if (station.resourceType === ResourceType.Storage) {
    return station.activePower >= 0 ? '储能放电' : '储能充电'
  }
  if (station.resourceType === ResourceType.Charging) {
    return '充电功率'
  }
  return '用电功率'
}

function createScenarioEvents(scenario: DemoScenario): AlarmEvent[] {
  const now = new Date()
  const currentTime = now.toLocaleTimeString('zh-CN', { hour12: false })

  if (scenario === DemoScenario.Degraded) {
    return [
      {
        eventId: 'scenario-degraded-01',
        title: '弱信号链路发生退化',
        description: '部分场站上行延迟升高，系统切换至边缘协同控制节奏。',
        level: '预警',
        timestamp: currentTime,
        relatedStationId: 'wind-ridge-02'
      }
    ]
  }

  if (scenario === DemoScenario.Lost) {
    return [
      {
        eventId: 'scenario-lost-01',
        title: '主通信链路失联',
        description: '云侧调度暂停下发，场站按自治策略保持本地保守出力。',
        level: '严重',
        timestamp: currentTime,
        relatedStationId: 'pv-gobi-01'
      }
    ]
  }

  if (scenario === DemoScenario.Recovering) {
    return [
      {
        eventId: 'scenario-recover-01',
        title: '链路恢复同步中',
        description: '边缘控制回传逐步恢复平滑，系统开始重新校准计划曲线。',
        level: '提示',
        timestamp: currentTime,
        relatedStationId: 'storage-hub-03'
      }
    ]
  }

  return [
    {
      eventId: 'scenario-stable-01',
      title: '全域协同运行稳定',
      description: '边疆新能源基地计划功率与实测功率保持高一致性。',
      level: '提示',
      timestamp: currentTime
    }
  ]
}

function applyScenarioToStations(scenario: DemoScenario): StationState[] {
  const stationCopies = baseStations.map(cloneStation)

  if (scenario === DemoScenario.Stable) {
    return stationCopies
  }

  if (scenario === DemoScenario.Degraded) {
    return stationCopies.map((station, index) => ({
      ...station,
      linkState: index % 2 === 0 ? LinkState.S1 : LinkState.S2,
      latency: station.latency + 68,
      packetLossRate: Number((station.packetLossRate + 2.3).toFixed(1)),
      dataAge: station.dataAge + 4,
      activePower: Math.max(station.activePower - 8, 12)
    }))
  }

  if (scenario === DemoScenario.Lost) {
    return stationCopies.map((station, index) => ({
      ...station,
      linkState: index < 3 ? LinkState.S3 : LinkState.S2,
      latency: index < 3 ? 999 : station.latency + 120,
      packetLossRate: index < 3 ? 100 : Number((station.packetLossRate + 8.2).toFixed(1)),
      dataAge: index < 3 ? 22 : station.dataAge + 10,
      online: index >= 3,
      activePower: Math.max(Math.round(station.activePower * 0.62), 10)
    }))
  }

  return stationCopies.map((station, index) => ({
    ...station,
    linkState: index % 2 === 0 ? LinkState.S4 : LinkState.S1,
    latency: station.latency + 24,
    packetLossRate: Number((station.packetLossRate + 1.1).toFixed(1)),
    dataAge: station.dataAge + 2,
    activePower: Math.round(station.activePower * 0.9)
  }))
}

function applyScenarioToTrend(scenario: DemoScenario): TimelinePoint[] {
  return baseTrend.map((point, index) => {
    if (scenario === DemoScenario.Stable) {
      return { ...point }
    }

    if (scenario === DemoScenario.Degraded) {
      return { ...point, actualPower: point.actualPower - (index % 2 === 0 ? 14 : 9) }
    }

    if (scenario === DemoScenario.Lost) {
      return { ...point, actualPower: Math.round(point.actualPower * 0.72) }
    }

    return { ...point, actualPower: point.actualPower - (index < 3 ? 16 : 6) }
  })
}

function createPlaceholderDetail(station: StationState | undefined): ResourceDetail | undefined {
  if (!station) {
    return undefined
  }

  return {
    stationId: station.stationId,
    overview: '正在从后端加载专题详情。',
    metrics: [
      { label: stationPowerMetricLabel(station), value: Math.abs(station.activePower), unit: 'kW' },
      { label: '可用容量', value: station.availableCapacity, unit: 'kW' },
      { label: '通信时延', value: station.latency, unit: 'ms' },
      { label: '丢包率', value: station.packetLossRate, unit: '%' }
    ],
    deviceStatuses: [],
    controlActions: [],
    powerTrend: [],
    qualityTrend: [],
    eventFocus: []
  }
}

export const useSystemStore = defineStore('system', () => {
  const activeScenario = ref<DemoScenario>(DemoScenario.Stable)
  const stations = ref<StationState[]>([])
  const timeline = ref<TimelinePoint[]>([])
  const powerFlowTrend = ref<PowerFlowTrendPoint[]>([])
  const latestEvents = ref<AlarmEvent[]>([])
  const selectedStationId = ref<string>('')
  const resourceDetailCache = ref<Record<string, ResourceDetail>>({})
  const apiSummary = ref<DashboardSummaryPayload | null>(null)
  const apiError = ref<string>('')
  const isLoading = ref(true)
  const initialized = ref(false)

  const currentProfile = computed(() => {
    return demoScenarioProfiles.find((profile) => profile.scenario === activeScenario.value) ?? demoScenarioProfiles[0]
  })

  const currentControlMode = computed<ControlMode>(() => {
    if (apiSummary.value?.controlMode) {
      return apiSummary.value.controlMode as ControlMode
    }
    return ControlMode.CentralOptimization
  })

  const selectedStation = computed(() => {
    return stations.value.find((station) => station.stationId === selectedStationId.value) ?? stations.value[0]
  })

  const summary = computed<DashboardSummary>(() => {
    if (apiSummary.value) {
      return {
        totalOutputPower: apiSummary.value.totalOutputPower,
        adjustableCapacity: apiSummary.value.adjustableCapacity,
        onlineStationCount: apiSummary.value.onlineStationCount,
        alarmCount: apiSummary.value.alarmCount
      }
    }

    const totalOutputPower = stations.value.reduce((sum, station) => sum + station.activePower, 0)
    const adjustableCapacity = stations.value.reduce((sum, station) => sum + station.availableCapacity - station.activePower, 0)
    const onlineStationCount = stations.value.filter((station) => station.online).length
    const alarmCount = latestEvents.value.filter((event) => event.level !== '提示').length

    return {
      totalOutputPower,
      adjustableCapacity,
      onlineStationCount,
      alarmCount
    }
  })

  const resourceComposition = computed(() => {
    return Object.values(ResourceType).map((resourceType) => {
      const stationGroup = stations.value.filter((station) => station.resourceType === resourceType)
      return {
        name: resourceTypeLabelMap[resourceType],
        value: stationGroup.reduce((sum, station) => sum + station.activePower, 0)
      }
    })
  })

  const linkStateDistribution = computed(() => {
    return Object.values(LinkState).map((linkState) => ({
      name: linkStateMetaMap[linkState].label,
      value: stations.value.filter((station) => station.linkState === linkState).length,
      color: linkStateMetaMap[linkState].color
    }))
  })

  const communicationRanking = computed(() => {
    return [...stations.value]
      .map((station) => ({
        stationName: station.stationName,
        score: Math.max(0, Math.round(100 - station.latency * 0.38 - station.packetLossRate * 6 - station.dataAge * 2.5)),
        stateLabel: linkStateMetaMap[station.linkState].label
      }))
      .sort((left, right) => right.score - left.score)
  })

  const stationCards = computed(() => {
    return stations.value.map((station) => ({
      ...station,
      resourceLabel: resourceTypeLabelMap[station.resourceType],
      powerMetricLabel: stationPowerMetricLabel(station),
      displayPower: Math.abs(station.activePower),
      stateLabel: linkStateMetaMap[station.linkState].label,
      qualityScore: Math.max(0, Math.round(100 - station.latency * 0.38 - station.packetLossRate * 6 - station.dataAge * 2.5))
    }))
  })

  async function refreshFromApi(forceStationId?: string): Promise<void> {
    const shouldShowInitialLoading = !initialized.value && stations.value.length === 0
    if (shouldShowInitialLoading) {
      isLoading.value = true
    }
    apiError.value = ''

    try {
      const [summaryPayload, stationPayload, eventPayload, scenarioPayload] = await Promise.all([
        fetchDashboardSummary(),
        fetchStations(),
        fetchLatestEvents(6),
        fetchCurrentScenario()
      ])

      const now = new Date()
      const trendFrom = new Date(now.getTime() - 24 * 60 * 60 * 1000).toISOString()
      const powerFlowTrendFrom = new Date(now.getTime() - 2 * 60 * 60 * 1000).toISOString()
      const [trendPayload, powerFlowTrendPayload] = await Promise.all([
        fetchPowerTrend(trendFrom, now.toISOString(), 24),
        fetchPowerFlowTrend(powerFlowTrendFrom, now.toISOString(), 240)
      ])

      apiSummary.value = summaryPayload
      stations.value = stationPayload
      latestEvents.value = eventPayload
      timeline.value = trendPayload
      powerFlowTrend.value = powerFlowTrendPayload
      activeScenario.value = scenarioPayload.scenario

      const detailStationId = forceStationId ?? selectedStationId.value ?? stationPayload[0]?.stationId
      if (detailStationId) {
        resourceDetailCache.value[detailStationId] = await fetchStationDetail(detailStationId)
        selectedStationId.value = detailStationId
      }
    } catch (error) {
      apiError.value = error instanceof Error ? error.message : '后端数据加载失败'
    } finally {
      if (shouldShowInitialLoading) {
        isLoading.value = false
      }
      initialized.value = true
    }
  }

  async function initialize(): Promise<void> {
    if (initialized.value) {
      return
    }

    await refreshFromApi(selectedStationId.value)
  }

  async function ensureResourceDetail(stationId: string): Promise<void> {
    if (resourceDetailCache.value[stationId]) {
      return
    }

    try {
      resourceDetailCache.value[stationId] = await fetchStationDetail(stationId)
    } catch (error) {
      apiError.value = error instanceof Error ? error.message : '场站详情加载失败'
    }
  }

  function selectStation(stationId: string): void {
    selectedStationId.value = stationId
    void ensureResourceDetail(stationId)
  }

  function getStationById(stationId: string): StationState | undefined {
    return stations.value.find((station) => station.stationId === stationId)
  }

  function getResourceDetail(stationId: string): ResourceDetail | undefined {
    void ensureResourceDetail(stationId)
    return resourceDetailCache.value[stationId] ?? createPlaceholderDetail(getStationById(stationId))
  }

  async function applyScenario(scenario: DemoScenario): Promise<void> {
    await activateScenario(scenario)
    await refreshFromApi(selectedStationId.value)
  }

  void initialize()

  return {
    activeScenario,
    stations,
    timeline,
    powerFlowTrend,
    latestEvents,
    selectedStation,
    summary,
    currentControlMode,
    currentProfile,
    resourceComposition,
    linkStateDistribution,
    communicationRanking,
    stationCards,
    controlModeLabelMap,
    linkStateMetaMap,
    resourceTypeLabelMap,
    apiError,
    isLoading,
    initialize,
    refreshFromApi,
    selectStation,
    getStationById,
    getResourceDetail,
    applyScenario
  }
})
