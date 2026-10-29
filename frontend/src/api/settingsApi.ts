import { requestJson } from '@/api/httpClient'
import type {
  ConfigurationTestResult,
  SystemConfiguration,
  SystemConfigurationUpdateRequest
} from '@/types/settings'

export async function fetchSystemConfiguration(): Promise<SystemConfiguration> {
  return requestJson<SystemConfiguration>('/api/v1/settings')
}

export async function updateSystemConfiguration(
  request: SystemConfigurationUpdateRequest
): Promise<SystemConfiguration> {
  return requestJson<SystemConfiguration>('/api/v1/settings', {
    method: 'PUT',
    body: JSON.stringify(request)
  })
}

export async function testMqttConnection(request: SystemConfigurationUpdateRequest['mqtt']): Promise<ConfigurationTestResult> {
  return requestJson<ConfigurationTestResult>('/api/v1/settings/mqtt/test', {
    method: 'POST',
    body: JSON.stringify({
      host: request.host,
      port: request.port,
      username: request.username,
      password: request.password
    })
  })
}

export async function testAiConnection(request: SystemConfigurationUpdateRequest['ai']): Promise<ConfigurationTestResult> {
  return requestJson<ConfigurationTestResult>('/api/v1/settings/ai/test', {
    method: 'POST',
    body: JSON.stringify({
      baseUrl: request.baseUrl,
      apiKey: request.apiKey,
      model: request.model,
      timeoutSeconds: request.timeoutSeconds
    })
  })
}
