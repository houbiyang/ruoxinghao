<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import type { EChartsOption } from 'echarts'

import {
  executeDispatch,
  fetchDispatchCommand,
  fetchDispatchPlan,
  fetchDispatchStatus,
  fetchLatestDispatchCommands,
  fetchStations,
  previewDispatch
} from '@/api/systemApi'
import EChartPanel from '@/components/charts/EChartPanel.vue'
import {
  baseStations,
  controlModeLabelMap,
  linkStateMetaMap,
  mockDispatchCommands,
  mockDispatchPlan,
  mockDispatchStatus,
  resourceTypeLabelMap
} from '@/mock/systemData'
import {
  ControlMode,
  LinkState,
  type DispatchAllocation,
  type DispatchCommand,
  type DispatchCommandStatus,
  type DispatchPlanPoint,
  type DispatchPreview,
  type DispatchRequest,
  type DispatchStatus,
  type StationState
} from '@/types/system'

const dataSource = 'api' as const
const AUTO_REFRESH_INTERVAL_MS = 5000

const plan = ref<DispatchPlanPoint[]>([])
const stations = ref<StationState[]>([])
const commands = ref<DispatchCommand[]>([])
const dispatchStatus = ref<DispatchStatus | null>(null)
const preview = ref<DispatchPreview | null>(null)
const selectedCommand = ref<DispatchCommand | null>(null)
const isLoading = ref(false)
const isSubmitting = ref(false)
const errorMessage = ref('')
const statusFilter = ref<'ALL' | DispatchCommandStatus>('ALL')
const stationFilter = ref('ALL')
const lastUpdatedAt = ref('')
const mockTimers = new Set<number>()
let refreshTimer: number | undefined

const form = ref<DispatchRequest>({
  totalTargetPower: 420,
  controlMode: ControlMode.EdgeCoordination,
  stationIds: ['pv-gobi-01', 'wind-ridge-02', 'storage-hub-03'],
  minimumPower: 0,
  maximumPower: 250,
  maximumRampRate: 18,
  commandTimeoutSeconds: 30,
  allowLocalAutonomy: true
})

const adjustableStations = computed(() =>
  stations.value.filter((station) => station.online && station.linkState !== LinkState.S3)
)

const filteredCommands = computed(() =>
  commands.value.filter((command) => {
    const statusMatches = statusFilter.value === 'ALL' || command.status === statusFilter.value
    const stationMatches = stationFilter.value === 'ALL' || command.stationId === stationFilter.value
    return statusMatches && stationMatches
  })
)

const planChartOption = computed<EChartsOption>(() => ({
  animationDuration: 600,
  tooltip: {
    trigger: 'axis',
    backgroundColor: 'rgba(5, 21, 79, 0.96)',
    borderColor: '#42e8ff',
    textStyle: { color: '#e7f7ff' },
    formatter: (parameters) => {
      const seriesItems = Array.isArray(parameters) ? parameters : [parameters]
      const dataIndex = Number(seriesItems[0]?.dataIndex ?? 0)
      const point = plan.value[dataIndex]
      if (!point) return ''
      return [
        formatDateTime(point.timestamp),
        `计划功率：${point.planPower.toFixed(1)} kW`,
        `可用容量：${point.availableCapacity.toFixed(1)} kW`,
        `备用容量：${point.reserveCapacity.toFixed(1)} kW`
      ].join('<br/>')
    }
  },
  legend: {
    top: 0,
    right: 8,
    textStyle: { color: '#8bc8ff' },
    data: ['计划功率', '可用容量', '备用容量']
  },
  grid: { left: 54, right: 26, top: 42, bottom: 42 },
  xAxis: {
    type: 'category',
    data: plan.value.map((point) => formatHour(point.timestamp)),
    axisLabel: { color: '#8bc8ff', interval: 2 },
    axisLine: { lineStyle: { color: 'rgba(66,232,255,.3)' } }
  },
  yAxis: {
    type: 'value',
    name: 'kW',
    nameTextStyle: { color: '#8bc8ff' },
    axisLabel: { color: '#8bc8ff' },
    splitLine: { lineStyle: { color: 'rgba(66,232,255,.12)' } }
  },
  series: [
    {
      name: '计划功率',
      type: 'line',
      smooth: true,
      symbol: 'none',
      lineStyle: { color: '#42e8ff', width: 3 },
      areaStyle: { color: 'rgba(66,232,255,.12)' },
      data: plan.value.map((point) => point.planPower)
    },
    {
      name: '可用容量',
      type: 'line',
      symbol: 'none',
      lineStyle: { color: '#5affd6', width: 2, type: 'dashed' },
      data: plan.value.map((point) => point.availableCapacity)
    },
    {
      name: '备用容量',
      type: 'bar',
      barMaxWidth: 12,
      itemStyle: { color: 'rgba(255,212,79,.52)' },
      data: plan.value.map((point) => point.reserveCapacity)
    }
  ]
}))

const statusChartOption = computed<EChartsOption>(() => {
  const statusCounts = dispatchStatus.value?.statusCounts
  return {
    tooltip: {
      trigger: 'item',
      backgroundColor: 'rgba(5, 21, 79, 0.96)',
      borderColor: '#42e8ff',
      textStyle: { color: '#e7f7ff' }
    },
    legend: {
      bottom: 0,
      textStyle: { color: '#8bc8ff' }
    },
    series: [
      {
        type: 'pie',
        radius: ['48%', '70%'],
        center: ['50%', '43%'],
        label: { color: '#e7f7ff', formatter: '{b} {c}' },
        data: [
          { name: '待接收', value: (statusCounts?.CREATED ?? 0) + (statusCounts?.SENT ?? 0), itemStyle: { color: '#42e8ff' } },
          { name: '已接受', value: statusCounts?.ACCEPTED ?? 0, itemStyle: { color: '#ffd44f' } },
          { name: '已执行', value: statusCounts?.EXECUTED ?? 0, itemStyle: { color: '#5affd6' } },
          { name: '已拒绝', value: statusCounts?.REJECTED ?? 0, itemStyle: { color: '#ff9d3f' } },
          { name: '超时/失败', value: (statusCounts?.TIMEOUT ?? 0) + (statusCounts?.FAILED ?? 0), itemStyle: { color: '#ef6457' } }
        ]
      }
    ]
  }
})

const canExecute = computed(() => preview.value?.valid === true && !isSubmitting.value)

function formatHour(timestamp: string): string {
  return new Intl.DateTimeFormat('zh-CN', { hour: '2-digit', minute: '2-digit', hour12: false }).format(new Date(timestamp))
}

function formatDateTime(timestamp?: string | null): string {
  if (!timestamp) return '--'
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false
  }).format(new Date(timestamp))
}

function stationName(stationId: string): string {
  return stations.value.find((station) => station.stationId === stationId)?.stationName ?? stationId
}

function statusLabel(status: DispatchCommandStatus): string {
  const labels: Record<DispatchCommandStatus, string> = {
    CREATED: '已创建',
    SENT: '已发送',
    ACCEPTED: '已接受',
    EXECUTED: '已执行',
    REJECTED: '已拒绝',
    TIMEOUT: '已超时',
    FAILED: '发送失败'
  }
  return labels[status]
}

function commandStatusClass(status: DispatchCommandStatus): string {
  return `is-${status.toLowerCase()}`
}

async function loadAllData(showLoading = true): Promise<void> {
  if (showLoading) isLoading.value = true
  errorMessage.value = ''
  try {
    const [planPayload, stationPayload, commandPayload, statusPayload] = await Promise.all([
      fetchDispatchPlan(),
      fetchStations(),
      fetchLatestDispatchCommands(20),
      fetchDispatchStatus()
    ])
    plan.value = planPayload
    stations.value = stationPayload
    commands.value = commandPayload
    dispatchStatus.value = statusPayload
    lastUpdatedAt.value = new Date().toISOString()
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '调度数据加载失败'
  } finally {
    isLoading.value = false
  }
}

function cloneCommand(command: DispatchCommand): DispatchCommand {
  return { ...command, acknowledgements: command.acknowledgements.map((acknowledgement) => ({ ...acknowledgement })) }
}

function calculateMockStatus(): DispatchStatus {
  const statusCounts: DispatchStatus['statusCounts'] = {
    CREATED: 0,
    SENT: 0,
    ACCEPTED: 0,
    EXECUTED: 0,
    REJECTED: 0,
    TIMEOUT: 0,
    FAILED: 0
  }
  commands.value.forEach((command) => {
    statusCounts[command.status] += 1
  })
  const terminalCount = statusCounts.EXECUTED + statusCounts.REJECTED + statusCounts.TIMEOUT + statusCounts.FAILED
  return {
    ...mockDispatchStatus,
    controlMode: form.value.controlMode,
    adjustableStationCount: adjustableStations.value.length,
    adjustableCapacity: adjustableStations.value.reduce((sum, station) => sum + station.availableCapacity, 0),
    currentPlanTarget: plan.value[0]?.planPower ?? 0,
    successRate: terminalCount === 0 ? 0 : Math.round((statusCounts.EXECUTED * 1000) / terminalCount) / 10,
    rejectionRate: terminalCount === 0 ? 0 : Math.round((statusCounts.REJECTED * 1000) / terminalCount) / 10,
    pendingCommandCount: statusCounts.CREATED + statusCounts.SENT + statusCounts.ACCEPTED,
    timeoutCount: statusCounts.TIMEOUT,
    statusCounts,
    latestBatchId: commands.value[0]?.batchId ?? null,
    updatedAt: new Date().toISOString()
  }
}

function createMockPreview(): DispatchPreview {
  const selectedStations = stations.value.filter((station) => form.value.stationIds.includes(station.stationId))
  const candidates = selectedStations.map((station) => {
    const factor = station.linkState === LinkState.S1 ? 0.85 : station.linkState === LinkState.S2 ? 0.6 : 1
    const eligible = station.online && station.linkState !== LinkState.S3
    return {
      station,
      eligible,
      capacity: eligible ? Math.min(station.availableCapacity, form.value.maximumPower) * factor : 0
    }
  })
  const totalCapacity = candidates.reduce((sum, candidate) => sum + candidate.capacity, 0)
  if (form.value.minimumPower > form.value.maximumPower) {
    throw new Error('单场站最小功率不能大于最大功率')
  }
  if (form.value.totalTargetPower > totalCapacity) {
    throw new Error(`总目标功率超过当前约束容量 ${totalCapacity.toFixed(1)} kW`)
  }
  let remainingTarget = form.value.totalTargetPower
  let remainingCapacity = totalCapacity
  const allocations: DispatchAllocation[] = candidates.map((candidate, index) => {
    const targetPower = candidate.eligible
      ? index === candidates.length - 1
        ? remainingTarget
        : Math.round((remainingTarget * candidate.capacity * 10) / remainingCapacity) / 10
      : 0
    if (candidate.eligible) {
      remainingTarget = Math.round((remainingTarget - targetPower) * 10) / 10
      remainingCapacity -= candidate.capacity
    }
    return {
      stationId: candidate.station.stationId,
      currentPower: candidate.station.activePower,
      targetPower,
      availableCapacity: candidate.station.availableCapacity,
      constrainedCapacity: candidate.capacity,
      linkState: candidate.station.linkState,
      eligible: candidate.eligible,
      validationMessage: candidate.eligible ? `通过 ${candidate.station.linkState} 链路约束` : '离线或 S3 场站仅保持本地自治'
    }
  })
  return {
    totalTargetPower: form.value.totalTargetPower,
    totalConstrainedCapacity: totalCapacity,
    controlMode: form.value.controlMode,
    valid: true,
    message: 'Mock 规则校验通过',
    allocations
  }
}

async function handlePreview(): Promise<void> {
  isSubmitting.value = true
  errorMessage.value = ''
  preview.value = null
  try {
    preview.value = await previewDispatch(form.value)
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '调度预览失败'
  } finally {
    isSubmitting.value = false
  }
}

async function handleExecute(): Promise<void> {
  if (!canExecute.value) return
  isSubmitting.value = true
  errorMessage.value = ''
  try {
    await executeDispatch(form.value)
    preview.value = null
    await loadAllData(false)
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '调度指令下发失败'
  } finally {
    isSubmitting.value = false
  }
}

function executeMockDispatch(): void {
  if (!preview.value) return
  const batchId = `mock-batch-${Date.now()}`
  const sendTime = new Date()
  const createdCommands = preview.value.allocations
    .filter((allocation) => allocation.eligible)
    .map<DispatchCommand>((allocation, index) => ({
      commandId: `mock-command-${Date.now()}-${index}`,
      batchId,
      stationId: allocation.stationId,
      targetPower: allocation.targetPower,
      controlMode: form.value.controlMode,
      status: 'SENT',
      sendTime: sendTime.toISOString(),
      expireAt: new Date(sendTime.getTime() + form.value.commandTimeoutSeconds * 1000).toISOString(),
      acknowledgements: []
    }))
  commands.value = [...createdCommands, ...commands.value].slice(0, 20)
  dispatchStatus.value = calculateMockStatus()
  scheduleMockTransition(createdCommands, 'ACCEPTED', 900)
  scheduleMockTransition(createdCommands, 'EXECUTED', 1900)
}

function scheduleMockTransition(
  targetCommands: DispatchCommand[],
  targetStatus: 'ACCEPTED' | 'EXECUTED',
  delay: number
): void {
  const timerId = window.setTimeout(() => {
    const ackStage = targetStatus === 'ACCEPTED' ? 'RECEIVED' : 'EXECUTED'
    const ackTime = new Date().toISOString()
    targetCommands.forEach((targetCommand) => {
      const command = commands.value.find((item) => item.commandId === targetCommand.commandId)
      if (!command) return
      command.status = targetStatus
      command.latestAckStage = ackStage
      command.latestAckMessage = targetStatus === 'ACCEPTED' ? '边缘控制器已接收' : '已按爬坡约束执行'
      command.latestAckTime = ackTime
      command.acknowledgements.push({
        ackStage,
        success: true,
        message: command.latestAckMessage,
        ackTime
      })
    })
    commands.value = [...commands.value]
    dispatchStatus.value = calculateMockStatus()
    mockTimers.delete(timerId)
  }, delay)
  mockTimers.add(timerId)
}

async function showCommandDetails(command: DispatchCommand): Promise<void> {
  errorMessage.value = ''
  try {
    selectedCommand.value = await fetchDispatchCommand(command.commandId)
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '指令详情加载失败'
  }
}

function resetPreview(): void {
  preview.value = null
}

onMounted(() => {
  void loadAllData()
  refreshTimer = window.setInterval(() => {
    void loadAllData(false)
  }, AUTO_REFRESH_INTERVAL_MS)
})

onBeforeUnmount(() => {
  if (refreshTimer !== undefined) window.clearInterval(refreshTimer)
  mockTimers.forEach((timerId) => window.clearTimeout(timerId))
  mockTimers.clear()
})
</script>

<template>
  <section class="dispatch page-shell">
    <header class="dispatch__header">
      <div>
        <span class="dispatch__eyebrow">规则型调度与控制闭环</span>
        <h2>云边协同功率调度台</h2>
        <p>实时遥测驱动计划与约束分配，经 MQTT 下发至模拟场站并追踪执行回执。</p>
      </div>
      <div class="dispatch__header-actions">
        <span class="dispatch__source">实时数据</span>
        <span>自动刷新 5s</span>
        <button type="button" :disabled="isLoading" @click="loadAllData()">
          {{ isLoading ? '刷新中' : '手动刷新' }}
        </button>
      </div>
    </header>

    <div class="dispatch__notice">当前为规则型演示调度，不连接真实电力设备，不参与真实电力交易。</div>
    <p v-if="errorMessage" class="dispatch__error">{{ errorMessage }}</p>

    <section class="dispatch__kpis">
      <article>
        <span>当前控制模式</span>
        <strong>{{ dispatchStatus ? controlModeLabelMap[dispatchStatus.controlMode] : '--' }}</strong>
      </article>
      <article>
        <span>在线可调场站</span>
        <strong>{{ dispatchStatus?.adjustableStationCount ?? 0 }}<small> 座</small></strong>
      </article>
      <article>
        <span>可调总容量</span>
        <strong>{{ dispatchStatus?.adjustableCapacity.toFixed(1) ?? '0.0' }}<small> kW</small></strong>
      </article>
      <article>
        <span>当前计划目标</span>
        <strong>{{ dispatchStatus?.currentPlanTarget.toFixed(1) ?? '0.0' }}<small> kW</small></strong>
      </article>
      <article>
        <span>执行成功率</span>
        <strong>{{ dispatchStatus?.successRate.toFixed(1) ?? '0.0' }}<small> %</small></strong>
      </article>
      <article>
        <span>待执行指令</span>
        <strong>{{ dispatchStatus?.pendingCommandCount ?? 0 }}<small> 条</small></strong>
      </article>
      <article>
        <span>最近更新时间</span>
        <strong class="is-time">{{ formatDateTime(lastUpdatedAt) }}</strong>
      </article>
    </section>

    <section class="dispatch__main-grid">
      <article class="dispatch-panel dispatch-plan">
        <div class="dispatch-panel__heading">
          <div>
            <span>日内计划窗口</span>
            <h3>未来 24 小时确定性功率计划</h3>
          </div>
          <small>按在线容量与固定时段负荷系数生成</small>
        </div>
        <EChartPanel v-if="plan.length" :option="planChartOption" height="310px" />
        <div v-else class="dispatch__empty">暂无日内计划数据</div>
      </article>

      <article class="dispatch-panel dispatch-strategy">
        <div class="dispatch-panel__heading">
          <div>
            <span>边缘协同策略</span>
            <h3>滚动目标与约束校验</h3>
          </div>
        </div>

        <div class="dispatch-form">
          <label>
            <span>总目标功率（kW）</span>
            <input v-model.number="form.totalTargetPower" type="number" min="0.1" step="1" @input="resetPreview">
          </label>
          <label>
            <span>控制模式</span>
            <select v-model="form.controlMode" @change="resetPreview">
              <option :value="ControlMode.CentralOptimization">中心优化</option>
              <option :value="ControlMode.EdgeCoordination">边缘协同</option>
              <option :value="ControlMode.LocalAutonomy">本地自治</option>
            </select>
          </label>
          <label>
            <span>单站最大功率（kW）</span>
            <input v-model.number="form.maximumPower" type="number" min="0.1" step="1" @input="resetPreview">
          </label>
          <label>
            <span>最大爬坡（kW/周期）</span>
            <input v-model.number="form.maximumRampRate" type="number" min="0.1" step="1" @input="resetPreview">
          </label>
          <label>
            <span>指令超时（秒）</span>
            <input v-model.number="form.commandTimeoutSeconds" type="number" min="5" max="300" @input="resetPreview">
          </label>
        </div>

        <div class="dispatch-stations">
          <span>参与场站</span>
          <label
            v-for="station in stations"
            :key="station.stationId"
            :class="{ 'is-disabled': !station.online || station.linkState === LinkState.S3 }"
          >
            <input
              v-model="form.stationIds"
              type="checkbox"
              :value="station.stationId"
              :disabled="!station.online || station.linkState === LinkState.S3"
              @change="resetPreview"
            >
            <span>{{ station.stationName }}</span>
            <small :style="{ color: linkStateMetaMap[station.linkState].color }">{{ station.linkState }}</small>
          </label>
        </div>

        <div class="dispatch-strategy__actions">
          <button type="button" :disabled="isSubmitting" @click="handlePreview">生成预览</button>
          <button class="is-primary" type="button" :disabled="!canExecute" @click="handleExecute">确认下发</button>
        </div>

        <div v-if="preview" class="dispatch-preview">
          <div>
            <strong>{{ preview.message }}</strong>
            <span>约束容量 {{ preview.totalConstrainedCapacity.toFixed(1) }} kW</span>
          </div>
          <ul>
            <li v-for="allocation in preview.allocations" :key="allocation.stationId">
              <span>{{ stationName(allocation.stationId) }}</span>
              <strong>{{ allocation.targetPower.toFixed(1) }} kW</strong>
              <small :class="{ 'is-invalid': !allocation.eligible }">{{ allocation.validationMessage }}</small>
            </li>
          </ul>
        </div>
      </article>
    </section>

    <section class="dispatch__lower-grid">
      <article class="dispatch-panel dispatch-commands">
        <div class="dispatch-panel__heading">
          <div>
            <span>控制指令回执</span>
            <h3>MQTT 指令状态与确认时间线</h3>
          </div>
          <div class="dispatch-filters">
            <select v-model="stationFilter">
              <option value="ALL">全部场站</option>
              <option v-for="station in stations" :key="station.stationId" :value="station.stationId">
                {{ station.stationName }}
              </option>
            </select>
            <select v-model="statusFilter">
              <option value="ALL">全部状态</option>
              <option value="SENT">已发送</option>
              <option value="ACCEPTED">已接受</option>
              <option value="EXECUTED">已执行</option>
              <option value="REJECTED">已拒绝</option>
              <option value="TIMEOUT">已超时</option>
              <option value="FAILED">发送失败</option>
            </select>
          </div>
        </div>

        <div v-if="filteredCommands.length" class="dispatch-table-wrap">
          <table>
            <thead>
              <tr>
                <th>指令编号</th>
                <th>场站</th>
                <th>目标功率</th>
                <th>下发时间</th>
                <th>当前状态</th>
                <th>最新回执</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="command in filteredCommands" :key="command.commandId">
                <td :title="command.commandId">{{ command.commandId.slice(0, 12) }}</td>
                <td>{{ stationName(command.stationId) }}</td>
                <td>{{ command.targetPower.toFixed(1) }} kW</td>
                <td>{{ formatDateTime(command.sendTime) }}</td>
                <td><span class="dispatch-status" :class="commandStatusClass(command.status)">{{ statusLabel(command.status) }}</span></td>
                <td>{{ command.latestAckStage ?? '等待回执' }}</td>
                <td><button type="button" @click="showCommandDetails(command)">查看</button></td>
              </tr>
            </tbody>
          </table>
        </div>
        <div v-else class="dispatch__empty">当前筛选条件下没有控制指令</div>
      </article>

      <article class="dispatch-panel dispatch-overview">
        <div class="dispatch-panel__heading">
          <div>
            <span>执行状态总览</span>
            <h3>状态分布与最近批次</h3>
          </div>
        </div>
        <EChartPanel :option="statusChartOption" height="250px" />
        <div class="dispatch-progress">
          <span>执行成功率</span>
          <strong>{{ dispatchStatus?.successRate.toFixed(1) ?? '0.0' }}%</strong>
          <div><i :style="{ width: `${dispatchStatus?.successRate ?? 0}%` }" /></div>
        </div>
        <div class="dispatch-overview__stats">
          <span>拒绝率 <strong>{{ dispatchStatus?.rejectionRate.toFixed(1) ?? '0.0' }}%</strong></span>
          <span>超时 <strong>{{ dispatchStatus?.timeoutCount ?? 0 }}</strong></span>
          <span>最近批次 <strong>{{ dispatchStatus?.latestBatchId?.slice(0, 10) ?? '--' }}</strong></span>
        </div>
      </article>
    </section>

    <section v-if="selectedCommand" class="dispatch-panel dispatch-detail">
      <div class="dispatch-panel__heading">
        <div>
          <span>指令详情</span>
          <h3>{{ selectedCommand.commandId }}</h3>
        </div>
        <button type="button" @click="selectedCommand = null">关闭</button>
      </div>
      <div class="dispatch-detail__summary">
        <span>场站 <strong>{{ stationName(selectedCommand.stationId) }}</strong></span>
        <span>目标 <strong>{{ selectedCommand.targetPower.toFixed(1) }} kW</strong></span>
        <span>状态 <strong>{{ statusLabel(selectedCommand.status) }}</strong></span>
        <span>过期时间 <strong>{{ formatDateTime(selectedCommand.expireAt) }}</strong></span>
      </div>
      <ol v-if="selectedCommand.acknowledgements.length" class="dispatch-detail__timeline">
        <li v-for="acknowledgement in selectedCommand.acknowledgements" :key="`${acknowledgement.ackStage}-${acknowledgement.ackTime}`">
          <time>{{ formatDateTime(acknowledgement.ackTime) }}</time>
          <strong>{{ acknowledgement.ackStage }}</strong>
          <span>{{ acknowledgement.message }}</span>
        </li>
      </ol>
      <div v-else class="dispatch__empty">指令已发送，尚未收到模拟器回执</div>
    </section>
  </section>
</template>

<style scoped>
.dispatch {
  display: grid;
  gap: 16px;
}

.dispatch__header,
.dispatch-panel,
.dispatch__kpis article {
  border: 1px solid rgba(66, 232, 255, 0.28);
  border-radius: 8px;
  background:
    linear-gradient(135deg, rgba(15, 61, 157, 0.2), transparent 42%),
    rgba(5, 25, 94, 0.82);
  box-shadow: inset 0 0 24px rgba(31, 177, 255, 0.07);
}

.dispatch__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  padding: 16px 20px;
}

.dispatch__eyebrow,
.dispatch-panel__heading span {
  color: var(--screen-cyan);
  font-size: 12px;
  letter-spacing: 0.16em;
}

.dispatch__header h2,
.dispatch-panel__heading h3 {
  margin: 5px 0 0;
}

.dispatch__header h2 {
  font-size: 25px;
}

.dispatch__header p {
  margin: 7px 0 0;
  color: var(--screen-text-dim);
  font-size: 13px;
}

.dispatch__header-actions,
.dispatch-filters,
.dispatch-strategy__actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.dispatch__header-actions {
  color: var(--screen-text-dim);
  font-size: 12px;
}

.dispatch__source {
  padding: 5px 10px;
  color: #07155c;
  background: var(--screen-cyan);
  font-weight: 800;
}

button,
input,
select {
  border: 1px solid rgba(66, 232, 255, 0.3);
  color: var(--screen-text);
  background: rgba(5, 31, 112, 0.9);
}

button {
  padding: 7px 13px;
  cursor: pointer;
}

button:disabled {
  cursor: not-allowed;
  opacity: 0.45;
}

.dispatch__notice,
.dispatch__error {
  margin: 0;
  padding: 9px 14px;
  border-left: 3px solid var(--screen-yellow);
  color: #ffe79b;
  background: rgba(255, 185, 54, 0.08);
  font-size: 12px;
}

.dispatch__error {
  border-left-color: #ef6457;
  color: #ffb6af;
  background: rgba(239, 100, 87, 0.12);
}

.dispatch__kpis {
  display: grid;
  grid-template-columns: repeat(7, minmax(0, 1fr));
  gap: 8px;
}

.dispatch__kpis article {
  min-height: 82px;
  padding: 13px;
}

.dispatch__kpis span,
.dispatch__kpis small {
  color: var(--screen-text-dim);
  font-size: 11px;
}

.dispatch__kpis strong {
  display: block;
  margin-top: 9px;
  color: var(--screen-teal);
  font-size: 21px;
}

.dispatch__kpis strong.is-time {
  font-size: 14px;
}

.dispatch__main-grid,
.dispatch__lower-grid {
  display: grid;
  gap: 12px;
}

.dispatch__main-grid {
  grid-template-columns: minmax(0, 1.5fr) minmax(390px, 0.9fr);
}

.dispatch__lower-grid {
  grid-template-columns: minmax(0, 1.55fr) minmax(340px, 0.65fr);
}

.dispatch-panel {
  min-width: 0;
  padding: 15px;
}

.dispatch-panel__heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
  margin-bottom: 12px;
  padding-bottom: 10px;
  border-bottom: 1px solid rgba(66, 232, 255, 0.14);
}

.dispatch-panel__heading h3 {
  font-size: 16px;
}

.dispatch-panel__heading small {
  color: var(--screen-text-soft);
  font-size: 11px;
}

.dispatch-form {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 9px;
}

.dispatch-form label {
  display: grid;
  gap: 5px;
}

.dispatch-form span,
.dispatch-stations > span {
  color: var(--screen-text-dim);
  font-size: 11px;
}

.dispatch-form input,
.dispatch-form select,
.dispatch-filters select {
  min-width: 0;
  padding: 8px;
}

.dispatch-stations {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 7px;
  margin-top: 12px;
}

.dispatch-stations > span {
  grid-column: 1 / -1;
}

.dispatch-stations label {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 7px;
  border: 1px solid rgba(66, 232, 255, 0.15);
  background: rgba(7, 39, 125, 0.46);
  font-size: 11px;
}

.dispatch-stations label.is-disabled {
  opacity: 0.55;
}

.dispatch-stations small {
  margin-left: auto;
}

.dispatch-strategy__actions {
  justify-content: flex-end;
  margin-top: 12px;
}

button.is-primary {
  color: #061850;
  border-color: var(--screen-teal);
  background: linear-gradient(90deg, var(--screen-cyan), var(--screen-teal));
  font-weight: 800;
}

.dispatch-preview {
  margin-top: 12px;
  padding: 10px;
  border: 1px solid rgba(90, 255, 214, 0.28);
  background: rgba(20, 151, 135, 0.08);
}

.dispatch-preview > div {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  color: var(--screen-teal);
  font-size: 12px;
}

.dispatch-preview ul {
  display: grid;
  gap: 5px;
  margin: 9px 0 0;
  padding: 0;
  list-style: none;
}

.dispatch-preview li {
  display: grid;
  grid-template-columns: 1fr auto 1.4fr;
  gap: 8px;
  font-size: 11px;
}

.dispatch-preview li small {
  text-align: right;
  color: var(--screen-text-dim);
}

.dispatch-preview li small.is-invalid {
  color: #ff9d3f;
}

.dispatch-table-wrap {
  overflow-x: auto;
}

table {
  width: 100%;
  border-collapse: collapse;
  font-size: 11px;
}

th,
td {
  padding: 9px 7px;
  text-align: left;
  border-bottom: 1px solid rgba(66, 232, 255, 0.12);
  white-space: nowrap;
}

th {
  color: var(--screen-cyan);
  font-weight: 600;
}

.dispatch-status {
  display: inline-block;
  min-width: 56px;
  padding: 4px 7px;
  text-align: center;
  color: var(--screen-cyan);
  border: 1px solid currentColor;
}

.dispatch-status.is-executed {
  color: var(--screen-teal);
}

.dispatch-status.is-accepted {
  color: var(--screen-yellow);
}

.dispatch-status.is-rejected,
.dispatch-status.is-timeout,
.dispatch-status.is-failed {
  color: #ef6457;
}

.dispatch-progress {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 7px;
  color: var(--screen-text-dim);
  font-size: 12px;
}

.dispatch-progress strong {
  color: var(--screen-teal);
}

.dispatch-progress div {
  grid-column: 1 / -1;
  height: 7px;
  overflow: hidden;
  background: rgba(66, 232, 255, 0.1);
}

.dispatch-progress i {
  display: block;
  height: 100%;
  background: linear-gradient(90deg, var(--screen-cyan), var(--screen-teal));
}

.dispatch-overview__stats,
.dispatch-detail__summary {
  display: grid;
  gap: 8px;
  margin-top: 12px;
}

.dispatch-overview__stats {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.dispatch-overview__stats span,
.dispatch-detail__summary span {
  padding: 8px;
  color: var(--screen-text-dim);
  background: rgba(66, 232, 255, 0.06);
  font-size: 11px;
}

.dispatch-overview__stats strong,
.dispatch-detail__summary strong {
  display: block;
  margin-top: 4px;
  color: var(--screen-text);
}

.dispatch-detail__summary {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.dispatch-detail__timeline {
  display: grid;
  gap: 7px;
  margin: 14px 0 0;
  padding: 0;
  list-style: none;
}

.dispatch-detail__timeline li {
  display: grid;
  grid-template-columns: 150px 90px 1fr;
  gap: 10px;
  padding: 9px;
  border-left: 2px solid var(--screen-teal);
  background: rgba(66, 232, 255, 0.05);
  font-size: 12px;
}

.dispatch-detail__timeline time {
  color: var(--screen-text-dim);
}

.dispatch__empty {
  display: grid;
  min-height: 120px;
  place-items: center;
  color: var(--screen-text-soft);
  font-size: 12px;
}

@media (max-width: 1400px) {
  .dispatch__kpis {
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }

  .dispatch__main-grid,
  .dispatch__lower-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 760px) {
  .dispatch__header,
  .dispatch-panel__heading {
    align-items: stretch;
    flex-direction: column;
  }

  .dispatch__header-actions,
  .dispatch-filters {
    flex-wrap: wrap;
  }

  .dispatch__kpis,
  .dispatch-form,
  .dispatch-stations,
  .dispatch-detail__summary {
    grid-template-columns: 1fr;
  }

  .dispatch-preview li,
  .dispatch-detail__timeline li {
    grid-template-columns: 1fr;
  }
}
</style>
