import { requestJson } from '@/api/httpClient'
import type {
  AlarmEvent,
  AgentDispatchOverview,
  DemoScenario,
  DispatchCommand,
  DispatchExecuteResult,
  DispatchContextSnapshot,
  DispatchPlanPoint,
  DispatchPreview,
  DispatchRequest,
  DispatchStatus,
  AiProviderStatus,
  ForecastSnapshot,
  ExperimentComparison,
  ExperimentDeleteResult,
  ExperimentQualityMetrics,
  ExperimentReplayPoint,
  ExperimentRun,
  ExperimentRunListItem,
  ExperimentScenario,
  ExperimentStartRequest,
  ExperimentStartResult,
  ExperimentStopResult,
  ExperimentSummary,
  ExperimentTimelinePoint,
  FaultAnalysisRequest,
  FaultDiagnosisRun,
  FaultActionPlan,
  MarketAgentAnalyzeRequest,
  MarketAgentRun,
  MarketBid,
  MarketBidCreateRequest,
  MarketClearing,
  MarketClearingTestRequest,
  MarketDashboardOverview,
  MarketDashboardRange,
  MarketDispatchAuditChain,
  MarketDispatchExecution,
  MarketDispatchPlan,
  MarketDispatchPlanRequest,
  MarketDispatchTracking,
  MarketSignalSnapshot,
  ResourceCapabilitySnapshot,
  ResourceDetail,
  SettlementCalculationRequest,
  SettlementResult,
  SimulatorMarketDataRefresh,
  StationDeviceDetail,
  StationDevicePowerTrend,
  StationDeviceQualityTrendPoint,
  StationDeviceSnapshot,
  StationState,
  PowerFlowTrendPoint,
  TimelinePoint
} from '@/types/system'

export interface DashboardSummaryPayload {
  totalOutputPower: number
  adjustableCapacity: number
  onlineStationCount: number
  alarmCount: number
  controlMode: 'central-optimization' | 'edge-coordination' | 'local-autonomy'
  activeScenario: DemoScenario
}

export interface ScenarioStatusPayload {
  scenario: DemoScenario
  controlMode: 'central-optimization' | 'edge-coordination' | 'local-autonomy'
}

export async function fetchStations(): Promise<StationState[]> {
  return requestJson<StationState[]>('/api/v1/stations')
}

export async function fetchStationDetail(stationId: string): Promise<ResourceDetail> {
  return requestJson<ResourceDetail>(`/api/v1/stations/${stationId}`)
}

export async function fetchStationDevices(stationId: string): Promise<StationDeviceSnapshot[]> {
  return requestJson<StationDeviceSnapshot[]>(`/api/v1/stations/${stationId}/devices`)
}

export async function fetchStationDeviceDetail(stationId: string, deviceId: string): Promise<StationDeviceDetail> {
  return requestJson<StationDeviceDetail>(`/api/v1/stations/${stationId}/devices/${deviceId}`)
}

export async function fetchStationDevicePowerTrend(
  stationId: string,
  deviceId: string,
  from: string,
  to: string,
  limit = 120
): Promise<StationDevicePowerTrend> {
  const search = new URLSearchParams({
    from,
    to,
    limit: String(limit)
  })
  return requestJson<StationDevicePowerTrend>(`/api/v1/stations/${stationId}/devices/${deviceId}/power-trend?${search.toString()}`)
}

export async function fetchStationDeviceQualityTrend(
  stationId: string,
  deviceId: string,
  from: string,
  to: string,
  limit = 120
): Promise<StationDeviceQualityTrendPoint[]> {
  const search = new URLSearchParams({
    from,
    to,
    limit: String(limit)
  })
  return requestJson<StationDeviceQualityTrendPoint[]>(`/api/v1/stations/${stationId}/devices/${deviceId}/quality-trend?${search.toString()}`)
}

export async function fetchDashboardSummary(): Promise<DashboardSummaryPayload> {
  return requestJson<DashboardSummaryPayload>('/api/v1/dashboard/summary')
}

export async function fetchPowerTrend(from: string, to: string, limit = 24): Promise<TimelinePoint[]> {
  const search = new URLSearchParams({
    from,
    to,
    limit: String(limit)
  })

  return requestJson<TimelinePoint[]>(`/api/v1/dashboard/power-trend?${search.toString()}`)
}

export async function fetchRealtimePowerTelemetry(
  from: string,
  to: string,
  limit = 10000,
  bucketSeconds = 0
): Promise<ExperimentReplayPoint[]> {
  const search = new URLSearchParams({
    from,
    to,
    limit: String(limit)
  })
  if (bucketSeconds > 0) {
    search.set('bucketSeconds', String(bucketSeconds))
  }
  return requestJson<ExperimentReplayPoint[]>(`/api/v1/dashboard/realtime-power-telemetry?${search.toString()}`)
}

export async function fetchPowerFlowTrend(from: string, to: string, limit = 240): Promise<PowerFlowTrendPoint[]> {
  const search = new URLSearchParams({
    from,
    to,
    limit: String(limit)
  })

  return requestJson<PowerFlowTrendPoint[]>(`/api/v1/dashboard/power-flow-trend?${search.toString()}`)
}

export async function fetchLatestEvents(limit = 6): Promise<AlarmEvent[]> {
  return requestJson<AlarmEvent[]>(`/api/v1/events/latest?limit=${limit}`)
}

export async function fetchCurrentScenario(): Promise<ScenarioStatusPayload> {
  return requestJson<ScenarioStatusPayload>('/api/v1/scenarios/current')
}

export async function activateScenario(scenario: DemoScenario): Promise<void> {
  await requestJson(`/api/v1/scenarios/${scenario}/activate`, {
    method: 'POST'
  })
}

export async function fetchExperimentScenarios(): Promise<ExperimentScenario[]> {
  return requestJson<ExperimentScenario[]>('/api/v1/experiments/scenarios')
}

export async function fetchLatestExperimentRuns(limit = 10): Promise<ExperimentRun[]> {
  return requestJson<ExperimentRun[]>(`/api/v1/experiments/runs/latest?limit=${limit}`)
}

export async function fetchLatestExperimentRunItems(limit = 10): Promise<ExperimentRunListItem[]> {
  return requestJson<ExperimentRunListItem[]>(`/api/v1/experiments/runs/latest/items?limit=${limit}`)
}

export async function fetchExperimentRun(runId: string): Promise<ExperimentRun> {
  return requestJson<ExperimentRun>(`/api/v1/experiments/runs/${runId}`)
}

export async function fetchCurrentExperimentRun(): Promise<ExperimentRun | null> {
  return requestJson<ExperimentRun | null>('/api/v1/experiments/runs/current')
}

export async function startExperiment(
  scenarioCode: string,
  request: ExperimentStartRequest
): Promise<ExperimentStartResult> {
  return requestJson<ExperimentStartResult>(`/api/v1/experiments/scenarios/${scenarioCode}/start`, {
    method: 'POST',
    body: JSON.stringify(request)
  })
}

export async function stopExperiment(runId: string): Promise<ExperimentStopResult> {
  return requestJson<ExperimentStopResult>(`/api/v1/experiments/runs/${runId}/stop`, {
    method: 'POST'
  })
}

export async function deleteExperiment(runId: string): Promise<ExperimentDeleteResult> {
  return requestJson<ExperimentDeleteResult>(`/api/v1/experiments/runs/${runId}`, {
    method: 'DELETE'
  })
}

export async function fetchExperimentSummary(runId: string): Promise<ExperimentSummary> {
  return requestJson<ExperimentSummary>(`/api/v1/experiments/runs/${runId}/summary`)
}

export async function fetchExperimentComparison(runId: string): Promise<ExperimentComparison> {
  return requestJson<ExperimentComparison>(`/api/v1/experiments/runs/${runId}/comparison`)
}

export async function fetchExperimentTimeline(runId: string): Promise<ExperimentTimelinePoint[]> {
  return requestJson<ExperimentTimelinePoint[]>(`/api/v1/experiments/runs/${runId}/timeline`)
}

export async function fetchExperimentReplay(
  runId: string,
  stationId?: string,
  limit = 300,
  allStations = false,
  includeBaseline = false
): Promise<ExperimentReplayPoint[]> {
  const search = new URLSearchParams({ limit: String(limit) })
  if (stationId) {
    search.set('stationId', stationId)
  }
  if (allStations) {
    search.set('allStations', 'true')
  }
  if (includeBaseline) {
    search.set('includeBaseline', 'true')
  }
  return requestJson<ExperimentReplayPoint[]>(`/api/v1/experiments/runs/${runId}/replay?${search.toString()}`)
}

export async function fetchExperimentQualityMetrics(runId: string): Promise<ExperimentQualityMetrics> {
  return requestJson<ExperimentQualityMetrics>(`/api/v1/experiments/runs/${runId}/quality-metrics`)
}

export async function fetchAgentDispatchOverview(): Promise<AgentDispatchOverview> {
  return requestJson<AgentDispatchOverview>('/api/v1/agent-dispatch/overview')
}

export async function startAgentDispatchExperiment(
  scenarioCode: string,
  request?: ExperimentStartRequest
): Promise<ExperimentStartResult> {
  return requestJson<ExperimentStartResult>(`/api/v1/agent-dispatch/experiments/${scenarioCode}/start`, {
    method: 'POST',
    body: JSON.stringify(request ?? {})
  })
}

export async function stopAgentDispatchExperiment(): Promise<ExperimentStopResult> {
  return requestJson<ExperimentStopResult>('/api/v1/agent-dispatch/experiments/current/stop', {
    method: 'POST'
  })
}

export async function fetchDispatchPlan(): Promise<DispatchPlanPoint[]> {
  return requestJson<DispatchPlanPoint[]>('/api/v1/dispatch/plan')
}

export async function previewDispatch(request: DispatchRequest): Promise<DispatchPreview> {
  return requestJson<DispatchPreview>('/api/v1/dispatch/preview', {
    method: 'POST',
    body: JSON.stringify(request)
  })
}

export async function executeDispatch(request: DispatchRequest): Promise<DispatchExecuteResult> {
  return requestJson<DispatchExecuteResult>('/api/v1/dispatch/execute', {
    method: 'POST',
    body: JSON.stringify(request)
  })
}

export async function fetchLatestDispatchCommands(limit = 20): Promise<DispatchCommand[]> {
  return requestJson<DispatchCommand[]>(`/api/v1/dispatch/commands/latest?limit=${limit}`)
}

export async function fetchDispatchCommand(commandId: string): Promise<DispatchCommand> {
  return requestJson<DispatchCommand>(`/api/v1/dispatch/commands/${commandId}`)
}

export async function fetchDispatchStatus(): Promise<DispatchStatus> {
  return requestJson<DispatchStatus>('/api/v1/dispatch/status')
}

export async function analyzeFault(request: FaultAnalysisRequest): Promise<FaultDiagnosisRun> {
  return requestJson<FaultDiagnosisRun>('/api/v1/fault-intelligence/analyze', {
    method: 'POST',
    body: JSON.stringify(request)
  })
}

export async function fetchLatestFaultDiagnosisRuns(limit = 20): Promise<FaultDiagnosisRun[]> {
  return requestJson<FaultDiagnosisRun[]>(`/api/v1/fault-intelligence/runs/latest?limit=${limit}`)
}

export async function fetchFaultDiagnosisRun(runId: string): Promise<FaultDiagnosisRun> {
  return requestJson<FaultDiagnosisRun>(`/api/v1/fault-intelligence/runs/${runId}`)
}

export async function fetchFaultActionPlan(runId: string): Promise<FaultActionPlan[]> {
  return requestJson<FaultActionPlan[]>(`/api/v1/fault-intelligence/runs/${runId}/plan`)
}

export async function approveFaultPlan(runId: string, reason: string): Promise<FaultDiagnosisRun> {
  return requestJson<FaultDiagnosisRun>(`/api/v1/fault-intelligence/runs/${runId}/approve`, {
    method: 'POST',
    body: JSON.stringify({ reason })
  })
}

export async function rejectFaultPlan(runId: string, reason: string): Promise<FaultDiagnosisRun> {
  return requestJson<FaultDiagnosisRun>(`/api/v1/fault-intelligence/runs/${runId}/reject`, {
    method: 'POST',
    body: JSON.stringify({ reason })
  })
}

export async function executeFaultPlan(runId: string): Promise<FaultDiagnosisRun> {
  return requestJson<FaultDiagnosisRun>(`/api/v1/fault-intelligence/runs/${runId}/execute`, {
    method: 'POST'
  })
}

export async function fetchAiProviderStatuses(): Promise<AiProviderStatus[]> {
  return requestJson<AiProviderStatus[]>('/api/v1/fault-intelligence/providers/status')
}

export async function fetchLatestMarketSignal(): Promise<MarketSignalSnapshot> {
  return requestJson<MarketSignalSnapshot>('/api/v1/market/signals/latest')
}

export async function refreshSimulatorMarketData(): Promise<SimulatorMarketDataRefresh> {
  return requestJson<SimulatorMarketDataRefresh>('/api/v1/market/simulator-data/refresh', {
    method: 'POST'
  })
}

export async function fetchLatestForecastSnapshot(): Promise<ForecastSnapshot> {
  return requestJson<ForecastSnapshot>('/api/v1/forecasts/snapshots/latest')
}

export async function fetchLatestCapabilities(): Promise<ResourceCapabilitySnapshot[]> {
  return requestJson<ResourceCapabilitySnapshot[]>('/api/v1/capabilities/latest')
}

export async function createDispatchContextSnapshot(): Promise<DispatchContextSnapshot> {
  return requestJson<DispatchContextSnapshot>('/api/v1/dispatch-context/snapshots', {
    method: 'POST'
  })
}

export async function previewMarketDispatchPlan(request: MarketDispatchPlanRequest): Promise<MarketDispatchPlan> {
  return requestJson<MarketDispatchPlan>('/api/v1/market-dispatch/plans/preview', {
    method: 'POST',
    body: JSON.stringify(request)
  })
}

export async function createMarketDispatchPlan(request: MarketDispatchPlanRequest): Promise<MarketDispatchPlan> {
  return requestJson<MarketDispatchPlan>('/api/v1/market-dispatch/plans', {
    method: 'POST',
    body: JSON.stringify(request)
  })
}

export async function fetchLatestMarketDispatchPlan(): Promise<MarketDispatchPlan> {
  return requestJson<MarketDispatchPlan>('/api/v1/market-dispatch/plans/latest')
}

export async function fetchMarketDispatchPlan(planId: string): Promise<MarketDispatchPlan> {
  return requestJson<MarketDispatchPlan>(`/api/v1/market-dispatch/plans/${planId}`)
}

export async function validateMarketDispatchPlan(planId: string): Promise<MarketDispatchPlan> {
  return requestJson<MarketDispatchPlan>(`/api/v1/market-dispatch/plans/${planId}/validate`, {
    method: 'POST'
  })
}

export async function approveMarketDispatchPlan(planId: string): Promise<MarketDispatchPlan> {
  return requestJson<MarketDispatchPlan>(`/api/v1/market-dispatch/plans/${planId}/approve`, {
    method: 'POST'
  })
}

export async function rejectMarketDispatchPlan(planId: string): Promise<MarketDispatchPlan> {
  return requestJson<MarketDispatchPlan>(`/api/v1/market-dispatch/plans/${planId}/reject`, {
    method: 'POST'
  })
}

export async function activateMarketDispatchPlan(planId: string): Promise<MarketDispatchPlan> {
  return requestJson<MarketDispatchPlan>(`/api/v1/market-dispatch/plans/${planId}/activate`, {
    method: 'POST'
  })
}

export async function executeNextMarketDispatchPlan(planId: string): Promise<MarketDispatchExecution> {
  return requestJson<MarketDispatchExecution>(`/api/v1/market-dispatch/plans/${planId}/execute-next`, {
    method: 'POST'
  })
}

export async function supersedeMarketDispatchPlan(planId: string): Promise<MarketDispatchPlan> {
  return requestJson<MarketDispatchPlan>(`/api/v1/market-dispatch/plans/${planId}/supersede`, {
    method: 'POST'
  })
}

export async function fetchMarketDispatchExecution(planId: string): Promise<MarketDispatchExecution> {
  return requestJson<MarketDispatchExecution>(`/api/v1/market-dispatch/plans/${planId}/execution`)
}

export async function fetchMarketDispatchTracking(planId: string): Promise<MarketDispatchTracking> {
  return requestJson<MarketDispatchTracking>(`/api/v1/market-dispatch/plans/${planId}/tracking`)
}

export async function fetchMarketDispatchAuditChain(planId: string): Promise<MarketDispatchAuditChain> {
  return requestJson<MarketDispatchAuditChain>(`/api/v1/market-dispatch/plans/${planId}/audit-chain`)
}

export async function analyzeMarketDispatch(request: MarketAgentAnalyzeRequest): Promise<MarketAgentRun> {
  return requestJson<MarketAgentRun>('/api/v1/agents/market-dispatch/analyze', {
    method: 'POST',
    body: JSON.stringify(request)
  })
}

export async function fetchMarketAgentRun(runId: string): Promise<MarketAgentRun> {
  return requestJson<MarketAgentRun>(`/api/v1/agents/runs/${runId}`)
}

export async function fetchLatestMarketAgentRun(): Promise<MarketAgentRun | null> {
  return requestJson<MarketAgentRun | null>('/api/v1/agents/market-dispatch/latest')
}

export async function createMarketBid(request: MarketBidCreateRequest): Promise<MarketBid> {
  return requestJson<MarketBid>('/api/v1/market/bids', {
    method: 'POST',
    body: JSON.stringify(request)
  })
}

export async function fetchMarketBid(bidId: string): Promise<MarketBid> {
  return requestJson<MarketBid>(`/api/v1/market/bids/${bidId}`)
}

export async function createMarketClearing(request: MarketClearingTestRequest): Promise<MarketClearing> {
  return requestJson<MarketClearing>('/api/v1/market/clearings/test', {
    method: 'POST',
    body: JSON.stringify(request)
  })
}

export async function fetchMarketClearing(clearingId: string): Promise<MarketClearing> {
  return requestJson<MarketClearing>(`/api/v1/market/clearings/${clearingId}`)
}

export async function calculateSettlement(request: SettlementCalculationRequest): Promise<SettlementResult> {
  return requestJson<SettlementResult>('/api/v1/settlements/calculate', {
    method: 'POST',
    body: JSON.stringify(request)
  })
}

export async function fetchSettlement(settlementId: string): Promise<SettlementResult> {
  return requestJson<SettlementResult>(`/api/v1/settlements/${settlementId}`)
}

export async function fetchLatestSettlement(): Promise<SettlementResult> {
  return requestJson<SettlementResult>('/api/v1/settlements/latest')
}

export async function fetchMarketDashboardOverview(
  range: MarketDashboardRange = '24H',
  newsLimit = 5
): Promise<MarketDashboardOverview> {
  const search = new URLSearchParams({
    range,
    newsLimit: String(newsLimit)
  })
  return requestJson<MarketDashboardOverview>(`/api/v1/market-dashboard/overview?${search.toString()}`)
}
