import { baseStations, linkStateMetaMap } from '@/mock/systemData'
import {
  LinkState,
  type ExperimentQualityMetrics,
  type ExperimentReplayPoint,
  type ExperimentTimelinePoint,
  type ScenePosition,
  type StationState
} from '@/types/system'

const linkStateSeverityMap: Record<LinkState, number> = {
  [LinkState.S0]: 0,
  [LinkState.S4]: 1,
  [LinkState.S1]: 2,
  [LinkState.S2]: 3,
  [LinkState.S3]: 4
}

export interface LinkOverviewMetrics {
  onlineCount: number
  abnormalCount: number
  averageLatency: number
  averagePacketLossRate: number
  worstState: LinkState
}

export interface LinkSwitchLogEntry {
  entryId: string
  timestamp: string
  stationId: string
  stationName: string
  previousState?: LinkState
  currentState: LinkState
  transitionLabel: string
  transitionKind: '初始状态' | '状态切换' | '状态采样'
  latency: number
  packetLossRate: number
  online: boolean
  eventTitle: string
}

export interface LinkTrendDatum {
  timestamp: string
  stationId: string
  latency: number
  packetLossRate: number
  dataAge: number
  jitter: number
}

function fallbackPosition(stationId: string): ScenePosition {
  return baseStations.find((station) => station.stationId === stationId)?.position ?? { x: 50, y: 50 }
}

export function formatDateTime(timestamp: string): string {
  return new Date(timestamp).toLocaleString('zh-CN', { hour12: false })
}

export function formatTime(timestamp: string): string {
  return new Date(timestamp).toLocaleTimeString('zh-CN', { hour12: false })
}

export function stationNameOf(stationId: string): string {
  return baseStations.find((station) => station.stationId === stationId)?.stationName ?? stationId
}

export function normalizeStations(stations: StationState[]): StationState[] {
  return stations.map((station) => ({
    ...station,
    position: station.position ?? fallbackPosition(station.stationId)
  }))
}

export function computeLinkOverview(stations: StationState[]): LinkOverviewMetrics {
  if (stations.length === 0) {
    return {
      onlineCount: 0,
      abnormalCount: 0,
      averageLatency: 0,
      averagePacketLossRate: 0,
      worstState: LinkState.S0
    }
  }

  const averageLatency = Number(
    (stations.reduce((sum, station) => sum + station.latency, 0) / stations.length).toFixed(1)
  )
  const averagePacketLossRate = Number(
    (stations.reduce((sum, station) => sum + station.packetLossRate, 0) / stations.length).toFixed(2)
  )

  const worstState = [...stations]
    .sort((left, right) => linkStateSeverityMap[right.linkState] - linkStateSeverityMap[left.linkState])[0]
    ?.linkState ?? LinkState.S0

  return {
    onlineCount: stations.filter((station) => station.online).length,
    abnormalCount: stations.filter((station) => station.linkState !== LinkState.S0).length,
    averageLatency,
    averagePacketLossRate,
    worstState
  }
}

export function buildSwitchLogEntries(points: ExperimentTimelinePoint[]): LinkSwitchLogEntry[] {
  const sortedPoints = [...points].sort(
    (left, right) => new Date(left.timestamp).getTime() - new Date(right.timestamp).getTime()
  )
  const previousStateMap = new Map<string, LinkState>()

  const entries = sortedPoints.map((point) => {
    const previousState = previousStateMap.get(point.stationId)
    const transitionKind =
      previousState === undefined
        ? '初始状态'
        : previousState === point.linkState
          ? '状态采样'
          : '状态切换'

    const transitionLabel =
      transitionKind === '初始状态'
        ? `初始 ${point.linkState}`
        : transitionKind === '状态采样'
          ? '状态采样'
          : `${previousState} → ${point.linkState}`

    previousStateMap.set(point.stationId, point.linkState)

    return {
      entryId: `${point.timestamp}-${point.stationId}-${point.linkState}-${point.eventTitle}`,
      timestamp: point.timestamp,
      stationId: point.stationId,
      stationName: stationNameOf(point.stationId),
      previousState,
      currentState: point.linkState,
      transitionLabel,
      transitionKind,
      latency: point.latency,
      packetLossRate: point.packetLossRate,
      online: point.online,
      eventTitle: point.eventTitle
    }
  })

  return entries.sort((left, right) => new Date(right.timestamp).getTime() - new Date(left.timestamp).getTime())
}

export function buildTrendData(
  qualityMetrics: ExperimentQualityMetrics | null,
  replayPoints: ExperimentReplayPoint[],
  selectedStationId: string
): LinkTrendDatum[] {
  const qualityTrendPoints = (qualityMetrics?.trends ?? [])
    .filter((point) => point.stationId === selectedStationId)
    .map((point) => ({
      timestamp: point.timestamp,
      stationId: point.stationId,
      latency: point.latency,
      packetLossRate: point.packetLossRate,
      dataAge: point.dataAge
    }))

  const sourcePoints =
    qualityTrendPoints.length > 0
      ? qualityTrendPoints
      : replayPoints
          .filter((point) => point.stationId === selectedStationId)
          .map((point) => ({
            timestamp: point.timestamp,
            stationId: point.stationId,
            latency: point.latency,
            packetLossRate: point.packetLossRate,
            dataAge: point.dataAge
          }))

  return sourcePoints.map((point, index) => ({
    ...point,
    jitter:
      index === 0
        ? 0
        : Math.abs(point.latency - sourcePoints[index - 1].latency)
  }))
}

export function buildReplayStations(
  stations: StationState[],
  replayPoint: ExperimentReplayPoint | null
): StationState[] {
  if (!replayPoint) {
    return normalizeStations(stations)
  }

  return normalizeStations(stations).map((station) => {
    if (station.stationId !== replayPoint.stationId) {
      return station
    }

    return {
      ...station,
      activePower: replayPoint.activePower,
      availableCapacity: replayPoint.availableCapacity,
      latency: replayPoint.latency,
      packetLossRate: replayPoint.packetLossRate,
      dataAge: replayPoint.dataAge,
      linkState: replayPoint.linkState,
      online: replayPoint.online
    }
  })
}

export function linkStateLabel(state: LinkState): string {
  return `${state} ${linkStateMetaMap[state].label}`
}
