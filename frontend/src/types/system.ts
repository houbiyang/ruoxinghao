export enum LinkState {
  S0 = 'S0',
  S1 = 'S1',
  S2 = 'S2',
  S3 = 'S3',
  S4 = 'S4'
}

export enum ResourceType {
  Photovoltaic = 'photovoltaic',
  Wind = 'wind',
  Thermal = 'thermal',
  Grid = 'grid',
  Storage = 'storage',
  Load = 'load',
  Charging = 'charging'
}

export enum ControlMode {
  CentralOptimization = 'central-optimization',
  EdgeCoordination = 'edge-coordination',
  LocalAutonomy = 'local-autonomy'
}

export enum DemoScenario {
  Stable = 'stable',
  Degraded = 'degraded',
  Lost = 'lost',
  Recovering = 'recovering'
}

export interface ScenePosition {
  x: number
  y: number
}

export interface StationState {
  stationId: string
  stationName: string
  resourceType: ResourceType
  activePower: number
  availableCapacity: number
  linkState: LinkState
  latency: number
  packetLossRate: number
  dataAge: number
  online: boolean
  position: ScenePosition
  heartbeatAge: number | null
  activeFaultCode: string | null
  updatedAt: string | null
}

export interface AlarmEvent {
  eventId: string
  title: string
  description: string
  level: '提示' | '预警' | '严重'
  timestamp: string
  relatedStationId?: string
}

export interface TimelinePoint {
  label: string
  planPower: number
  actualPower: number
}

export interface PowerFlowTrendPoint {
  timestamp: string
  generationPower: number
  consumptionPower: number
  sampledStationCount: number
}

export interface DashboardSummary {
  totalOutputPower: number
  adjustableCapacity: number
  onlineStationCount: number
  alarmCount: number
}

export interface ScenarioProfile {
  scenario: DemoScenario
  label: string
  description: string
  controlMode: ControlMode
  primaryState: LinkState
}

export interface ResourceMetric {
  label: string
  value: number | string
  unit: string
}

export interface ResourceDeviceStatus {
  deviceId: string
  deviceName: string
  status: '运行' | '待机' | '告警' | '离线'
  currentValue: string
  description: string
}

export interface ResourceControlAction {
  actionId: string
  actionName: string
  status: '可执行' | '执行中' | '已锁定'
  description: string
}

export interface ResourceTelemetryPoint {
  label: string
  power: number
  target: number | null
}

export interface ResourceQualityPoint {
  label: string
  latency: number
  packetLossRate: number
}

export interface StationDeviceSnapshot {
  deviceId: string
  stationId: string
  deviceName: string
  deviceType: string
  status: '运行' | '待机' | '告警' | '离线'
  activePower: number | null
  communicationStatus: string
  linkState: LinkState
  latency: number
  packetLossRate: number
  dataAge: number
  updatedAt: string
  controllable: boolean
  metrics: ResourceMetric[]
}

export interface StationDeviceDetail {
  deviceId: string
  stationId: string
  deviceName: string
  deviceType: string
  status: '运行' | '待机' | '告警' | '离线'
  activePower: number | null
  communicationStatus: string
  linkState: LinkState
  latency: number
  packetLossRate: number
  dataAge: number
  updatedAt: string
  controllable: boolean
  minimumPower: number | null
  maximumPower: number | null
  metrics: ResourceMetric[]
}

export interface StationDeviceTrendPoint {
  timestamp: string
  primaryValue: number | null
  secondaryValue: number | null
}

export interface StationDevicePowerTrend {
  deviceId: string
  mode: 'power' | 'environment'
  primaryLabel: string
  primaryUnit: string
  secondaryLabel: string
  secondaryUnit: string
  points: StationDeviceTrendPoint[]
}

export interface StationDeviceQualityTrendPoint {
  timestamp: string
  latency: number
  packetLossRate: number
  dataAge: number
  linkState: LinkState
}

export interface ResourceDetail {
  stationId: string
  overview: string
  metrics: ResourceMetric[]
  deviceStatuses: ResourceDeviceStatus[]
  controlActions: ResourceControlAction[]
  powerTrend: ResourceTelemetryPoint[]
  qualityTrend: ResourceQualityPoint[]
  eventFocus: AlarmEvent[]
}

export interface ExperimentScenario {
  scenarioCode: string
  scenarioName: string
  description: string
  targetStationIds: string[]
  category: 'ENVIRONMENT' | 'COMMUNICATION' | 'DEVICE' | 'LEGACY'
  supportedResourceTypes: ResourceType[]
  expectedLinkState: LinkState
  durationSeconds: number
  parameterDefinitions: Record<string, ExperimentParameterDefinition>
  expectedEvidenceType: string
  automaticRecoverySupported: boolean
  enabled: boolean
}

export interface ExperimentParameterDefinition {
  name: string
  unit: string
  defaultValue: number
  minimum: number
  maximum: number
  step: number
}

export interface ExperimentRun {
  runId: string
  scenarioCode: string
  scenarioName: string
  status: 'STARTING' | 'INJECTING' | 'FAULT_OBSERVED' | 'ORCHESTRATING' | 'RECOVERING' | 'COMPLETED' | 'STOPPED' | 'FAILED' | 'INJECTION_NOT_OBSERVED'
  startedAt: string
  endedAt?: string | null
  resultSummary: string
}

export interface ExperimentRunListItem {
  runId: string
  scenarioCode: string
  scenarioName: string
  status: ExperimentRun['status']
  startedAt: string
  endedAt?: string | null
}

export interface ExperimentStartRequest {
  targetStationIds?: string[]
  durationSeconds?: number
  faultParameters?: Record<string, number>
  severityLevel?: number
  automaticRecovery?: boolean
  remark?: string
}

export interface ExperimentStartResult {
  experimentRunId: string
  scenarioCode: string
  status: string
  startedAt: string
}

export interface ExperimentStopResult {
  runId: string
  status: string
  stoppedAt: string
}

export interface ExperimentDeleteResult {
  runId: string
  deletedAt: string
}

export interface ExperimentMetricSnapshot {
  sampleCount: number
  averagePower: number
  maxPower: number
  minPower: number
  averageLatency: number
  maxLatency: number
  averagePacketLossRate: number
  maxPacketLossRate: number
  averageDataAge: number
  maxDataAge: number
  onlineRate: number
  stateCounts: Record<LinkState, number>
}

export interface ExperimentSummary {
  runId: string
  scenarioCode: string
  scenarioName: string
  status: string
  startedAt: string
  stoppedAt?: string | null
  durationSeconds: number
  targetStationIds: string[]
  sampleCount: number
  eventCount: number
  dominantState: LinkState
  orchestrationStatus?: 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED' | string | null
  orchestrationTriggeredAt?: string | null
  orchestrationCompletedAt?: string | null
  dispatchContextSnapshotId?: string | null
  dispatchPlanId?: string | null
  agentRunId?: string | null
  planStatus?: string | null
  executionBatchId?: string | null
  safetyGatePassed?: boolean | null
  failureReason?: string | null
  controlStrategy?: string | null
  randomSeed?: number | null
  controllerType?: string | null
  convergenceStatus?: string | null
  convergenceRound?: number | null
  plannedBalanceErrorKw?: number | null
  actualBalanceErrorKw?: number | null
  messageSentCount?: number | null
  messageLostCount?: number | null
  constraintViolationCount?: number | null
  commandTotalCount: number
  commandExecutedCount: number
  commandFailedCount: number
  commandPendingCount: number
  localAutonomyEvidence: Record<string, Record<string, unknown>>
}

export interface ExperimentComparison {
  run: ExperimentRun
  targetStationIds: string[]
  before: ExperimentMetricSnapshot
  during: ExperimentMetricSnapshot
  derivedMetrics: ExperimentDerivedMetric[]
}

export interface ExperimentDerivedMetric {
  metricCode: string
  metricName: string
  beforeValue: number | null
  duringValue: number | null
  unit: string
  improvementValue: number | null
  improvementRate: number | null
  higherIsBetter: boolean
  available: boolean
}

export interface ExperimentTimelinePoint {
  timestamp: string
  stationId: string
  linkState: LinkState
  latency: number
  packetLossRate: number
  online: boolean
  eventTitle: string
}

export interface ExperimentReplayPoint {
  timestamp: string
  stationId: string
  activePower: number
  availableCapacity: number
  latency: number
  packetLossRate: number
  dataAge: number
  linkState: LinkState
  online: boolean
}

export interface ExperimentQualityTrendPoint {
  timestamp: string
  stationId: string
  latency: number
  packetLossRate: number
  dataAge: number
}

export interface ExperimentStateDistribution {
  linkState: LinkState
  count: number
}

export interface ExperimentStationQualityRank {
  stationId: string
  averageLatency: number
  averagePacketLossRate: number
  averageDataAge: number
  onlineRate: number
  qualityScore: number
}

export interface ExperimentQualityMetrics {
  before: ExperimentMetricSnapshot
  during: ExperimentMetricSnapshot
  trends: ExperimentQualityTrendPoint[]
  stateDistribution: ExperimentStateDistribution[]
  stationRanking: ExperimentStationQualityRank[]
}

export type DispatchCommandStatus =
  | 'CREATED'
  | 'SENT'
  | 'ACCEPTED'
  | 'EXECUTED'
  | 'REJECTED'
  | 'TIMEOUT'
  | 'FAILED'

export interface DispatchPlanPoint {
  timestamp: string
  planPower: number
  availableCapacity: number
  reserveCapacity: number
  loadFactor: number
}

export interface DispatchRequest {
  totalTargetPower: number
  controlMode: ControlMode
  stationIds: string[]
  minimumPower: number
  maximumPower: number
  maximumRampRate: number
  commandTimeoutSeconds: number
  allowLocalAutonomy: boolean
}

export interface DispatchAllocation {
  stationId: string
  currentPower: number
  targetPower: number
  availableCapacity: number
  constrainedCapacity: number
  linkState: LinkState
  eligible: boolean
  validationMessage: string
}

export interface DispatchPreview {
  totalTargetPower: number
  totalConstrainedCapacity: number
  controlMode: ControlMode
  valid: boolean
  message: string
  allocations: DispatchAllocation[]
}

export interface CommandAck {
  ackStage: 'RECEIVED' | 'EXECUTED' | 'REJECTED' | string
  success: boolean
  message: string
  ackTime: string
}

export interface DispatchCommand {
  commandId: string
  batchId: string
  stationId: string
  targetPower: number
  controlMode: ControlMode
  status: DispatchCommandStatus
  sendTime: string
  expireAt: string
  latestAckStage?: string | null
  latestAckMessage?: string | null
  latestAckTime?: string | null
  acknowledgements: CommandAck[]
}

export interface DispatchExecuteResult {
  batchId: string
  createdAt: string
  commands: DispatchCommand[]
}

export interface DispatchStatus {
  controlMode: ControlMode
  adjustableStationCount: number
  adjustableCapacity: number
  currentPlanTarget: number
  successRate: number
  rejectionRate: number
  pendingCommandCount: number
  timeoutCount: number
  statusCounts: Record<DispatchCommandStatus, number>
  latestBatchId?: string | null
  updatedAt: string
}

export interface MarketSignalSnapshot {
  snapshotId: string
  marketType: string
  intervalStart: string
  intervalEnd: string
  contractPower: number
  contractPrice: string
  dayAheadPrice: string
  realTimePrice: string
  gridImportLimit: number
  gridExportLimit: number
  reserveUpRequirement: number
  reserveDownRequirement: number
  generatedAt: string
  validFrom: string
  validUntil: string
  source: string
  qualityCode: string
  version: string
  receivedAt: string
}

export interface ForecastPoint {
  intervalStart: string
  intervalEnd: string
  loadP10: number
  loadP50: number
  loadP90: number
  photovoltaicP10: number
  photovoltaicP50: number
  photovoltaicP90: number
  windP10: number
  windP50: number
  windP90: number
  priceP10: string
  priceP50: string
  priceP90: string
}

export interface ForecastSnapshot {
  snapshotId: string
  intervalMinutes: number
  modelVersion: string
  points: ForecastPoint[]
  generatedAt: string
  validFrom: string
  validUntil: string
  source: string
  qualityCode: string
  version: string
  receivedAt: string
}

export interface SimulatorMarketDataRefresh {
  marketSignal: MarketSignalSnapshot
  forecast: ForecastSnapshot
  telemetryReceivedAt: string
  formulaVersion: string
}

export interface StorageConstraintSnapshot {
  soc: number
  minimumSoc: number
  maximumSoc: number
  energyCapacityKWh: number
  maximumChargePowerKW: number
  maximumDischargePowerKW: number
  chargeEfficiency: number
  dischargeEfficiency: number
  cycleCost: string
  stateOfHealth: number
  reservedUpPower: number
  reservedDownPower: number
}

export interface ResourceCapabilitySnapshot {
  capabilityId: string
  snapshotGroupId: string
  stationId: string
  resourceType: ResourceType
  currentPower: number
  availableCapacity: number
  minimumPower: number
  maximumPower: number
  maximumRampUp: number
  maximumRampDown: number
  availableEnergyKwh: number
  storageConstraint: StorageConstraintSnapshot | null
  environmentAssessment: EnvironmentAssessment | null
  linkState: LinkState
  dataAge: number
  capabilityConfidence: number
  dataTrusted: boolean
  unavailableReason: string
  generatedAt: string
  validFrom: string
  validUntil: string
  source: string
  qualityCode: string
  version: string
  receivedAt: string
}

export interface DispatchContextSnapshot {
  snapshotId: string
  generatedAt: string
  validFrom: string
  validUntil: string
  source: string
  qualityCode: string
  version: string
  receivedAt: string
  intervalMinutes: number
  marketSignal: MarketSignalSnapshot
  forecastSnapshot: ForecastSnapshot
  resourceCapabilities: ResourceCapabilitySnapshot[]
}

export interface MarketDispatchPlanItem {
  itemId: string
  inputSnapshotId: string
  intervalStart: string
  intervalEnd: string
  netPowerTarget: number
  gridImportPower: number
  gridExportPower: number
  storageChargePower: number
  storageDischargePower: number
  flexibleLoadAdjustment: number
  upwardReserve: number
  downwardReserve: number
  renewableCurtailment: number
  expectedCost: string
  expectedRevenue: string
  riskCost: string
  reasonCode: string
  itemOrder: number
}

export type MarketDispatchPlanStatus =
  | 'DRAFT'
  | 'VALIDATED'
  | 'PENDING_APPROVAL'
  | 'APPROVED'
  | 'ACTIVE'
  | 'SUPERSEDED'
  | 'EXPIRED'
  | 'REJECTED'
  | 'COMPLETED'

export interface MarketDispatchPlan {
  planId: string
  inputSnapshotId: string
  intervalMinutes: number
  conservativePlan: boolean
  status: MarketDispatchPlanStatus
  generatedAt: string
  validFrom: string
  validUntil: string
  source: string
  qualityCode: string
  version: string
  receivedAt: string
  items: MarketDispatchPlanItem[]
}

export interface MarketDispatchPlanRequest {
  dispatchContextSnapshotId: string | null
  conservativePlan: boolean
}

export interface MarketDispatchExecution {
  planId: string
  version: string
  status: MarketDispatchPlanStatus
  activeBatchId: string | null
  commandCount: number
  terminalCommandCount: number
  latestCommandTime: string | null
  commands: DispatchCommand[]
}

export interface MarketDispatchTrackingItem {
  dispatchPlanItemId: string
  intervalStart: string
  intervalEnd: string
  plannedNetPower: number
  actualNetPower: number
  trackingError: number
  latestCommandStatus: string
}

export interface MarketDispatchTracking {
  planId: string
  version: string
  items: MarketDispatchTrackingItem[]
}

export interface MarketDispatchAuditChain {
  planId: string
  version: string
  status: MarketDispatchPlanStatus
  inputSnapshotId: string
  commandIds: string[]
  events: string[]
}

export interface MarketBidSegment {
  segmentOrder: number
  quantity: number
  price: string
}

export interface MarketBid {
  bidId: string
  marketType: string
  aggregateObject: string
  intervalStart: string
  intervalEnd: string
  availableCapacity: number
  totalQuantity: number
  inputSnapshotId: string
  bidVersion: string
  status: string
  validUntil: string
  testData: boolean
  createdAt: string
  segments: MarketBidSegment[]
}

export interface MarketBidCreateRequest {
  marketType: string
  aggregateObject: string
  intervalStart: string
  intervalEnd: string
  availableCapacity: number
  inputSnapshotId: string
  bidVersion: string
  validUntil: string
  testData: boolean
  segments: Array<{
    quantity: number
    price: string
  }>
}

export interface MarketClearing {
  clearingId: string
  bidId: string
  marketType: string
  intervalStart: string
  intervalEnd: string
  clearedPower: number
  clearedPrice: string
  ancillaryServicePower: number
  externalTradeId: string
  testData: boolean
  createdAt: string
}

export interface MarketClearingTestRequest {
  bidId: string
  ancillaryServicePower?: number
  externalTradeId?: string
}

export interface MarketMeteringRecord {
  meteringId: string
  stationId: string
  intervalStart: string
  intervalEnd: string
  averagePower: number
  energyKwh: string
  sampleCount: number
}

export interface SettlementResult {
  settlementId: string
  planId: string
  clearingId: string
  totalEnergyRevenue: string
  gridImportCost: string
  gridExportRevenue: string
  deviationCost: string
  storageCycleCost: string
  demandResponseCompensation: string
  renewableCurtailmentCost: string
  ancillaryServiceRevenue: string
  netRevenue: string
  resourceRevenueAllocation: Record<string, string>
  testData: boolean
  createdAt: string
  meteringRecords: MarketMeteringRecord[]
}

export interface SettlementCalculationRequest {
  planId: string
  clearingId: string
}

export type MarketDashboardRange = '24H' | '48H' | '7D'

export interface MarketDashboardSummary {
  currentRealTimePrice: number | string
  peakForecastPrice: number | string
  peakForecastWindow: string
  contractEnergyMwh: number | string
  executedEnergyMwh: number | string
  contractProgressPercent: number | string
  averageForecastPrice: number | string
  todayTradeVolumeMwh: number | string
  marketName: string
  settlementCycle: string
  currentTimeWindow: string
  currentPhase: string
  clearingStatus: string
}

export interface MarketDashboardSummarySparklines {
  realTimePriceTrend: Array<number | string>
  peakForecastTrend: Array<number | string>
  averageForecastTrend: Array<number | string>
  tradeVolumeTrend: Array<number | string>
}

export interface MarketDashboardPriceTrendPoint {
  time: string
  realTimePrice: number | string
  dayAheadPrice: number | string
  forecastTodayPrice: number | string
  forecastTomorrowPrice: number | string
}

export interface MarketDashboardPriceTrend {
  range: MarketDashboardRange
  points: MarketDashboardPriceTrendPoint[]
}

export interface MarketDashboardDistributionItem {
  bucket: string
  percentage: number | string
}

export interface MarketDashboardTimeOfUsePriceItem {
  period: string
  realTimePrice: number | string
  dayAheadPrice: number | string
  forecastTodayPrice: number | string
  forecastTomorrowPrice: number | string
}

export interface MarketDashboardPredictionPoint {
  time: string
  price: number | string
}

export interface MarketDashboardPredictionAnalysis {
  todayTrend: MarketDashboardPredictionPoint[]
  tomorrowTrend: MarketDashboardPredictionPoint[]
  conclusion: string
}

export interface MarketDashboardRecommendationItem {
  type: string
  title: string
  description: string
  valueLabel: string
  value: number | string
  tone: 'green' | 'orange' | 'blue'
}

export interface MarketDashboardNewsItem {
  publishedAt: string
  title: string
}

export interface MarketDashboardDataSourceStatusItem {
  label: string
  online: boolean
}

export interface MarketDashboardOverview {
  summary: MarketDashboardSummary
  summarySparklines: MarketDashboardSummarySparklines
  priceTrend: MarketDashboardPriceTrend
  distribution: MarketDashboardDistributionItem[]
  timeOfUsePrices: MarketDashboardTimeOfUsePriceItem[]
  predictionAnalysis: MarketDashboardPredictionAnalysis
  recommendations: MarketDashboardRecommendationItem[]
  news: MarketDashboardNewsItem[]
  dataSourceStatus: MarketDashboardDataSourceStatusItem[]
}

export type MarketAgentType =
  | 'MARKET_INTERFACE'
  | 'FORECAST'
  | 'ENVIRONMENT_ASSESSMENT'
  | 'MARKET_DISPATCH'
  | 'RESOURCE_CAPABILITY'
  | 'COMMUNICATION_QUALITY'
  | 'ASSET_DIAGNOSIS'
  | 'CONTROL_DECISION'
  | 'RECOVERY_ORCHESTRATION'
  | 'SECURITY_AUDIT'
  | 'SETTLEMENT_EVALUATION'

export interface MarketAgentDecision {
  decisionId: string
  agentType: MarketAgentType
  snapshotId: string
  generatedAt: string
  validUntil: string
  confidence: number
  evidenceRefs: string[]
  ruleVersion: string
  modelProvider: string
  modelVersion: string
  candidateActions: string[]
  riskLevel: string
  requiresHumanApproval: boolean
  explanation: string
  fallbackUsed: boolean
  stageStatus?: 'SUCCESS' | 'RETRYING' | 'RULE_FALLBACK' | 'BLOCKED' | 'FAILED' | string
  retryCount?: number
  errorCategory?: string
  providerStatusCode?: number | null
  providerErrorCode?: string | null
  providerErrorMessage?: string | null
  inputVersion?: string
  outputVersion?: string
  nextStageAllowed?: boolean
}

export interface EnvironmentAssessment {
  stationId: string
  resourceType: string
  environmentFactor: number
  riskLevel: string
  correctionReasons: string[]
  affectedDeviceMetrics: string[]
  deviceDataInterpretation: string
  safetyConstraints: string[]
  dataAvailable: boolean
  missingFields: string[]
}

export interface MarketSafetyDecision {
  decisionLevel: 'PASSED' | 'PASSED_WITH_FALLBACK' | 'DEGRADED' | 'REJECTED' | string
  passed: boolean
  fallbackUsed: boolean
  approvedActions: string[]
  explanation: string
}

export interface MarketAgentRun {
  runId: string
  snapshotId: string
  dispatchPlanId: string
  conservativePlan: boolean
  decisions: MarketAgentDecision[]
  safetyDecision: MarketSafetyDecision
  createdAt: string
  workflowVersion?: string
  agentSetVersion?: string
  stageRecordsComplete?: boolean
  legacyRun?: boolean
}

export interface MarketAgentAnalyzeRequest {
  dispatchContextSnapshotId: string | null
  dispatchPlanId: string | null
  conservativePlan: boolean
}

export type FaultSeverity = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'
export type FaultAgentType =
  | 'COMMUNICATION_QUALITY'
  | 'STATE_DIAGNOSIS'
  | 'CONTROL_DECISION'
  | 'RECOVERY_ORCHESTRATION'
  | 'SECURITY_AUDIT'
export type FaultActionType =
  | 'MARK_DATA_UNTRUSTED'
  | 'FREEZE_EXPIRED_COMMANDS'
  | 'REDUCE_TELEMETRY_RATE'
  | 'LIMIT_CONTROL_RAMP'
  | 'EXCLUDE_OFFLINE_CAPACITY'
  | 'ENTER_LOCAL_AUTONOMY'
  | 'ENABLE_CONSERVATIVE_ESTIMATE'
  | 'START_RATE_LIMITED_RECOVERY'
  | 'ISOLATE_STATION'
  | 'SWITCH_CONTROL_AUTHORITY'
  | 'REDISTRIBUTE_AGGREGATE_POWER'
  | 'RESTORE_CENTRAL_CONTROL'

export interface FaultEvidence {
  metric: string
  value: string
  unit: string
  description: string
}

export interface ProposedFaultAction {
  actionType: FaultActionType
  stationId: string
  parameters: Record<string, string>
  reason: string
}

export interface FaultRuleDecision {
  matched: boolean
  faultCode: string
  faultType: string
  severity: FaultSeverity
  suggestedLinkState: LinkState
  evidence: FaultEvidence[]
  requiresAiAnalysis: boolean
  immediateProtectionActions: ProposedFaultAction[]
}

export interface FaultAgentResult {
  agentType: FaultAgentType
  provider: string
  faultCode: string
  faultType: string
  affectedStationIds: string[]
  severity: FaultSeverity
  confidence: number
  evidence: FaultEvidence[]
  recommendedMode: ControlMode
  proposedActions: ProposedFaultAction[]
  requiresHumanApproval: boolean
  riskExplanation: string
}

export interface FaultSafetyDecision {
  approvedActions: ProposedFaultAction[]
  requiresHumanApproval: boolean
  conflictDetected: boolean
  fallbackUsed: boolean
  explanation: string
}

export interface FaultActionPlan {
  actionId: string
  stationId: string
  actionType: FaultActionType
  parameters: Record<string, string>
  riskLevel: FaultSeverity
  status: string
  requiresApproval: boolean
  mqttCommandId?: string | null
  issuedAt?: string | null
  expireAt?: string | null
}

export interface FaultDiagnosisRun {
  runId: string
  eventId?: string | null
  stationId: string
  status: 'ANALYZING' | 'PENDING_APPROVAL' | 'READY' | 'EXECUTING' | 'EXECUTED' | 'REJECTED' | 'FAILED'
  approvalStatus: 'NOT_REQUIRED' | 'PENDING' | 'APPROVED' | 'REJECTED'
  ruleDecision: FaultRuleDecision
  agentResults: FaultAgentResult[]
  safetyDecision: FaultSafetyDecision
  actions: FaultActionPlan[]
  conflictDetected: boolean
  fallbackUsed: boolean
  createdAt: string
  updatedAt: string
}

export interface FaultAnalysisRequest {
  eventId?: string
  stationId: string
  jitter: number
  consecutivePacketLoss: number
  heartbeatAge: number
  acknowledgementAge: number
  duplicateMessage: boolean
  staleSequence: boolean
  timeSkewSeconds: number
  frozenTelemetry: boolean
  suspectedAttack: boolean
}

export interface AiProviderStatus {
  providerCode: string
  enabled: boolean
  available: boolean
  lastErrorAt?: string | null
}

export type AgentDispatchControlMode = ControlMode

export type AgentDispatchDataSourceStatus = 'REALTIME' | 'MOCK' | 'DEGRADED' | 'UNAVAILABLE'

export type DispatchStrategyGroupType = 'load' | 'generation' | 'storage' | 'grid'

export type DispatchStrategyStatus = 'CANDIDATE' | 'ADOPTED' | 'EXECUTING' | 'COMPLETED' | 'REJECTED' | 'STANDBY'

export type DispatchTopologyNodeType = 'center' | 'photovoltaic' | 'wind' | 'thermal' | 'storage' | 'load' | 'charging' | 'grid'

export type DispatchTopologyDirection = 'SUPPLY' | 'CONSUME' | 'BIDIRECTIONAL' | 'UNKNOWN'

export type DispatchTopologyLinkStatus = 'NORMAL' | 'DEGRADED' | 'ISOLATED' | 'RECOVERING' | 'UNAVAILABLE'

export type DispatchCommandFlowStatus = 'IDLE' | 'PENDING' | 'SENT' | 'EXECUTING' | 'BLOCKED' | 'COMPLETED'

export type ExecutionTimelineStatus = 'PENDING' | 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'SKIPPED'

export type AgentDispatchDemoMode = 'realtime' | 'presentation'

export interface AgentDispatchSystemStatus {
  aiEnabled: boolean
  aiAvailable: boolean
  aiProvider: string | null
  controlMode: AgentDispatchControlMode
  activeScenario: string | null
  dataSourceStatus: AgentDispatchDataSourceStatus
  lastUpdatedAt: string
}

export interface DispatchStrategyItem {
  strategyCode: string
  strategyName: string
  groupType: DispatchStrategyGroupType
  status: DispatchStrategyStatus
  targetDeltaPower: number | null
  unit: string
  reason: string
  affectedResourceIds: string[]
  adoptedBySafetyGate: boolean
}

export interface DispatchStrategyGroup {
  groupType: DispatchStrategyGroupType
  groupTitle: string
  groupSubtitle: string
  items: DispatchStrategyItem[]
}

export interface AgentDispatchStrategy {
  currentStrategyTitle: string
  strategyMode: AgentDispatchControlMode
  loadSideActions: DispatchStrategyGroup
  generationSideActions: DispatchStrategyGroup
  storageActions: DispatchStrategyGroup
  gridActions: DispatchStrategyGroup
}

export interface DispatchTopologyCenterNode {
  nodeId: string
  displayName: string
  controlMode: AgentDispatchControlMode
  systemStatus: string
  statusText: string
}

export interface DispatchTopologyNode {
  nodeId: string
  stationId: string | null
  nodeType: DispatchTopologyNodeType
  displayName: string
  activePower: number | null
  unit: string
  linkState: LinkState | null
  online: boolean
  controlMode: AgentDispatchControlMode
  selected: boolean
  statusText: string
}

export interface DispatchTopologyEnergyLink {
  linkId: string
  sourceNodeId: string
  targetNodeId: string
  power: number | null
  unit: string
  direction: DispatchTopologyDirection
  status: DispatchTopologyLinkStatus
  active: boolean
}

export interface DispatchTopologyControlLink {
  linkId: string
  sourceNodeId: string
  targetNodeId: string
  commandStatus: DispatchCommandFlowStatus
  linkState: LinkState | null
  active: boolean
}

export interface DispatchTopologyBalanceSummary {
  totalSupplyPower: number | null
  totalConsumePower: number | null
  netBalancePower: number | null
  unit: string
}

export interface DispatchTopologyLegendItem {
  label: string
  type: 'energy' | 'control'
  color: string
}

export interface AgentDispatchResourceTopology {
  centerNode: DispatchTopologyCenterNode
  resourceNodes: DispatchTopologyNode[]
  energyLinks: DispatchTopologyEnergyLink[]
  controlLinks: DispatchTopologyControlLink[]
  balanceSummary: DispatchTopologyBalanceSummary
  legend: DispatchTopologyLegendItem[]
}

export interface DispatchMetricValue {
  label: string
  value: number | string | null
  unit: string
  improvementValue: number | string | null
  improvementRate: number | null
  trendDirection: 'UP' | 'DOWN' | 'FLAT' | 'UNKNOWN'
  higherIsBetter: boolean | null
  available: boolean
}

export interface DispatchSummary {
  plannedTargetPower: DispatchMetricValue
  actualPower: DispatchMetricValue
  trackingErrorRate: DispatchMetricValue
  revenueImprovement: DispatchMetricValue
  curtailmentReduction: DispatchMetricValue
  reserveSatisfactionRate: DispatchMetricValue
  executionSuccessRate: DispatchMetricValue
  activePlanId: string | null
  planVersion: string | null
}

export interface AgentDispatchExperimentStatus {
  currentRunId: string | null
  currentScenarioCode: string | null
  currentScenarioName: string | null
  running: boolean
  durationSeconds: number | null
  elapsedSeconds: number | null
  affectedStationIds: string[]
  startedAt: string | null
  expectedLinkState: LinkState | null
}

export interface DispatchComparisonMetric {
  metricCode: string
  metricName: string
  beforeValue: number | string | null
  afterValue: number | string | null
  unit: string
  improvementValue: number | string | null
  improvementRate: number | null
  trendDirection: 'UP' | 'DOWN' | 'FLAT' | 'UNKNOWN'
  improved: boolean | null
  available: boolean
}

export interface DispatchComparisonSummary {
  runId: string | null
  scenarioCode: string | null
  scenarioName: string | null
  metrics: DispatchComparisonMetric[]
}

export interface AgentDecisionSummaryRow {
  agentType: string
  agentName: string
  available: boolean
  confidence: number | null
  suggestedAction: string
  finalDecision: string
  adopted: boolean
  evidenceCount: number
  fallbackReason: string | null
  stageStatus: 'SUCCESS' | 'RETRYING' | 'RULE_FALLBACK' | 'BLOCKED' | 'FAILED' | 'LEGACY' | string
  retryCount: number
  errorCategory: string
  inputVersion: string
  outputVersion: string
}

export interface AgentDecisionSummary {
  rows: AgentDecisionSummaryRow[]
  overallDecision: string
  safetyDecisionLevel: 'PASSED' | 'DEGRADED' | 'REJECTED' | 'UNKNOWN' | string
  safetyGatePassed: boolean | null
  ruleFallbackActive: boolean
  decisionRunId: string | null
  modeLabel: string
  fallbackReason: string | null
}

export interface ExecutionTimelineStep {
  stepCode: string
  stepLabel: string
  status: ExecutionTimelineStatus
  timestamp: string | null
  description: string
  relatedEntityId: string | null
}

export interface ExecutionTimeline {
  runId: string | null
  currentStepCode: string | null
  steps: ExecutionTimelineStep[]
}

export interface AgentDispatchOverview {
  systemStatus: AgentDispatchSystemStatus
  dispatchStrategy: AgentDispatchStrategy
  resourceTopology: AgentDispatchResourceTopology
  dispatchSummary: DispatchSummary
  experimentStatus: AgentDispatchExperimentStatus
  comparisonSummary: DispatchComparisonSummary
  agentDecisionSummary: AgentDecisionSummary
  executionTimeline: ExecutionTimeline
  resourceTracking?: AgentDispatchResourceTracking
}

export interface AgentDispatchResourceTrackingItem {
  stationId: string
  resourceType: string
  linkState: LinkState | null
  targetPower: number | null
  actualPower: number | null
  absoluteError: number | null
  tolerance: number | null
  trackingStatus: string
  commandStatus: string
  targetGeneratedAt: string | null
  actualReceivedAt: string | null
  telemetryFresh: boolean
  consecutiveSatisfiedSamples: number
  requiredSatisfiedSamples: number
}

export interface AgentDispatchResourceTrackingPoint {
  stationId: string
  timestamp: string
  targetPower: number
  actualPower: number
}

export interface AgentDispatchResourceTracking {
  experimentRunId: string | null
  optimizationRound: number | null
  planId: string | null
  executionBatchId: string | null
  totalSupplyPower: number | null
  totalConsumePower: number | null
  plannedBalanceError: number | null
  actualBalanceError: number | null
  convergenceStatus: string
  consecutiveSatisfiedSamples: number
  requiredSatisfiedSamples: number
  resources: AgentDispatchResourceTrackingItem[]
  points: AgentDispatchResourceTrackingPoint[]
  optimizationBoundaries: string[]
  historicalConvergenceStatus: string | null
  planStatus: string | null
  planValidUntil: string | null
  currentlyWithinTolerance: boolean
}
