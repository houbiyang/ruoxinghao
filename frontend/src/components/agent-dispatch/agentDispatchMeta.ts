import {
  type AgentDecisionSummaryRow,
  type DispatchComparisonMetric,
  type DispatchStrategyGroupType,
  type DispatchStrategyStatus,
  type ExecutionTimelineStatus
} from '@/types/system'

export const strategyGroupTitleMap: Record<DispatchStrategyGroupType, string> = {
  load: '负荷侧操作',
  generation: '电源侧操作',
  storage: '储能操作',
  grid: '电网交互'
}

export const strategyStatusLabelMap: Record<DispatchStrategyStatus, string> = {
  CANDIDATE: '候选',
  ADOPTED: '已采用',
  EXECUTING: '执行中',
  COMPLETED: '已完成',
  REJECTED: '已拒绝',
  STANDBY: '待执行'
}

export const timelineStatusLabelMap: Record<ExecutionTimelineStatus, string> = {
  PENDING: '等待',
  RUNNING: '进行中',
  SUCCEEDED: '完成',
  FAILED: '失败',
  SKIPPED: '跳过'
}

export function formatSignedNumber(value: number | null, digits = 1): string {
  if (value === null || Number.isNaN(value)) {
    return '--'
  }
  const prefix = value > 0 ? '+' : ''
  return `${prefix}${value.toFixed(digits)}`
}

export function formatValue(value: number | string | null, digits = 1): string {
  if (value === null || value === '') {
    return '--'
  }
  if (typeof value === 'number') {
    return value.toFixed(digits)
  }
  return value
}

export function formatPercent(value: number | null, digits = 1): string {
  if (value === null || Number.isNaN(value)) {
    return '--'
  }
  return `${value.toFixed(digits)}%`
}

export function formatConfidencePercent(value: number | null, digits = 0): string {
  if (value === null || Number.isNaN(value)) {
    return '--'
  }
  const normalizedValue = value <= 1 ? value * 100 : value
  return `${normalizedValue.toFixed(digits)}%`
}

export function formatDateTime(value: string | null): string {
  if (!value) {
    return '--'
  }
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false
  }).format(new Date(value))
}

export function formatDuration(seconds: number | null): string {
  if (seconds === null || seconds < 0) {
    return '--'
  }
  const minutes = Math.floor(seconds / 60)
  const remainingSeconds = seconds % 60
  return `${String(minutes).padStart(2, '0')}:${String(remainingSeconds).padStart(2, '0')}`
}

export function comparisonTrendClass(metric: DispatchComparisonMetric): string {
  if (!metric.available || metric.improved === null) {
    return 'is-neutral'
  }
  return metric.improved ? 'is-improved' : 'is-worsened'
}

export function strategyItemClass(status: DispatchStrategyStatus, adoptedBySafetyGate: boolean): string[] {
  const classes = [`is-${status.toLowerCase()}`]
  if (adoptedBySafetyGate) {
    classes.push('is-adopted')
  }
  return classes
}

export function decisionRowModeLabel(row: AgentDecisionSummaryRow): string {
  if (!row.available) {
    return '不可用'
  }
  if (row.fallbackReason) {
    return '规则回退'
  }
  return '结构化决策'
}
