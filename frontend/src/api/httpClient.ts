import type { ApiResponse } from '@/types/api'

const apiBaseUrl = import.meta.env.VITE_API_BASE_URL ?? 'http://127.0.0.1:8080'

export async function requestJson<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${apiBaseUrl}${path}`, {
    headers: {
      'Content-Type': 'application/json',
      ...(init?.headers ?? {})
    },
    ...init
  })

  const payload = (await response.json()) as ApiResponse<T>

  if (!response.ok || payload.code !== 'SUCCESS') {
    throw new Error(payload.message || `请求失败: ${response.status}`)
  }

  return payload.data
}

