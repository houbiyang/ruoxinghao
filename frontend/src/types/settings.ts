export interface SystemConfiguration {
  sourceName: string
  writable: boolean
  pendingRestart: boolean
  lastModifiedAt: string
  mqtt: MqttConfiguration
  ai: AiConfiguration
  simulation: SimulationConfiguration
}

export interface MqttConfiguration {
  host: string
  port: number
  username: string
  passwordConfigured: boolean
  clientId: string
  reconnectDelayMillis: number
}

export interface AiConfiguration {
  globallyEnabled: boolean
  maximumContextCharacters: number
  providerCode: string
  providerEnabled: boolean
  baseUrl: string
  apiKeyConfigured: boolean
  apiKeyMasked: string
  model: string
  timeoutSeconds: number
}

export interface SimulationConfiguration {
  intervalMillis: number
  randomSeed: number
  initialScenario: 'stable' | 'degraded' | 'lost' | 'recovering'
  stationIds: string[]
}

export interface SystemConfigurationUpdateRequest {
  mqtt: {
    host: string
    port: number
    username: string
    password: string
    clientId: string
    reconnectDelayMillis: number
  }
  ai: {
    globallyEnabled: boolean
    maximumContextCharacters: number
    providerEnabled: boolean
    baseUrl: string
    apiKey: string
    model: string
    timeoutSeconds: number
  }
  simulation: SimulationConfiguration
}

export interface ConfigurationTestResult {
  success: boolean
  target: string
  message: string
  statusCode: number | null
  checkedAt: string
}
