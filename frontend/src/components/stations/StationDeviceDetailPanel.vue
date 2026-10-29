<script setup lang="ts">
import { computed } from 'vue'

import { linkStateMetaMap } from '@/mock/systemData'
import type { StationDeviceDetail } from '@/types/system'

const props = defineProps<{
  detail: StationDeviceDetail | null
  loading: boolean
  errorMessage: string
}>()

const emit = defineEmits<{
  (event: 'retry'): void
  (event: 'view-trend'): void
  (event: 'diagnose'): void
}>()

const activePowerText = computed(() => {
  if (!props.detail || props.detail.activePower === null) {
    return '--'
  }
  return `${Math.abs(props.detail.activePower).toFixed(1)} kW`
})

const activePowerLabel = computed(() => {
  if (!props.detail) {
    return '实时功率'
  }
  if (props.detail.stationId.startsWith('pv-') || props.detail.stationId.startsWith('wind-') || props.detail.stationId.startsWith('thermal-')) {
    return '发电功率'
  }
  if (props.detail.stationId.startsWith('grid-')) {
    return (props.detail.activePower ?? 0) >= 0 ? '市电购电' : '市电上网'
  }
  if (props.detail.stationId.startsWith('storage-')) {
    return (props.detail.activePower ?? 0) >= 0 ? '储能放电' : '储能充电'
  }
  if (props.detail.stationId.startsWith('charge-')) {
    return '充电功率'
  }
  return '用电功率'
})

const updatedAtText = computed(() => {
  if (!props.detail?.updatedAt) {
    return '--'
  }
  return new Intl.DateTimeFormat('zh-CN', {
    hour12: false,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit'
  }).format(new Date(props.detail.updatedAt))
})

const telemetryFreshnessText = computed(() => {
  if (!props.detail) {
    return '--'
  }
  return props.detail.dataAge <= 10 ? '模拟器遥测实时' : '模拟器遥测延迟'
})
</script>

<template>
  <section class="device-detail-panel">
    <header class="device-detail-panel__header">
      <div>
        <h3>{{ detail?.deviceName ?? '设备详情' }}</h3>
        <small>{{ telemetryFreshnessText }}</small>
      </div>
    </header>

    <div v-if="loading" class="device-detail-panel__state">设备详情加载中...</div>
    <div v-else-if="errorMessage" class="device-detail-panel__state is-error">
      <p>{{ errorMessage }}</p>
      <button type="button" @click="emit('retry')">重试</button>
    </div>
    <div v-else-if="detail" class="device-detail-panel__content">
      <section class="device-detail-panel__summary">
        <article>
          <span>{{ activePowerLabel }}</span>
          <strong>{{ activePowerText }}</strong>
        </article>
        <article>
          <span>设备状态</span>
          <strong>{{ detail.status }}</strong>
        </article>
        <article>
          <span>通信状态</span>
          <strong>{{ detail.communicationStatus }}</strong>
        </article>
        <article>
          <span>链路状态</span>
          <strong :style="{ color: linkStateMetaMap[detail.linkState].color }">
            {{ linkStateMetaMap[detail.linkState].label }}
          </strong>
        </article>
      </section>

      <dl class="device-detail-panel__fields">
        <div>
          <dt>设备类型</dt>
          <dd>{{ detail.deviceType }}</dd>
        </div>
        <div>
          <dt>通信时延</dt>
          <dd>{{ detail.latency }} ms</dd>
        </div>
        <div>
          <dt>丢包率</dt>
          <dd>{{ detail.packetLossRate }} %</dd>
        </div>
        <div>
          <dt>数据时效</dt>
          <dd>{{ detail.dataAge }} s</dd>
        </div>
        <div>
          <dt>最近更新时间</dt>
          <dd>{{ updatedAtText }}</dd>
        </div>
        <div>
          <dt>功率下发范围</dt>
          <dd>
            {{
              detail.minimumPower !== null && detail.maximumPower !== null
                ? `${detail.minimumPower.toFixed(1)} - ${detail.maximumPower.toFixed(1)} kW`
                : '当前设备不支持功率下发'
            }}
          </dd>
        </div>
      </dl>

      <section class="device-detail-panel__metrics">
        <article v-for="metric in detail.metrics" :key="metric.label">
          <span>{{ metric.label }}</span>
          <strong>{{ metric.value }}<small v-if="metric.unit">{{ metric.unit }}</small></strong>
        </article>
      </section>

      <div class="device-detail-panel__actions">
        <button type="button" class="is-primary" @click="emit('view-trend')">查看趋势</button>
        <button type="button" @click="emit('diagnose')">设备诊断</button>
      </div>
    </div>
    <div v-else class="device-detail-panel__state">请选择设备</div>
  </section>
</template>

<style scoped>
.device-detail-panel {
  border: 1px solid rgba(72, 224, 255, 0.24);
  background: linear-gradient(180deg, rgba(8, 34, 128, 0.8), rgba(4, 24, 95, 0.84));
}

.device-detail-panel__header {
  padding: 14px 16px;
  border-bottom: 1px solid rgba(72, 224, 255, 0.18);
}

.device-detail-panel__header h3 {
  margin: 0;
  font-size: 18px;
}

.device-detail-panel__header small {
  display: block;
  margin-top: 4px;
  color: #74d9ff;
}

.device-detail-panel__content,
.device-detail-panel__state {
  padding: 16px;
}

.device-detail-panel__state {
  color: #92d7ff;
}

.device-detail-panel__state.is-error {
  color: #ffb98f;
}

.device-detail-panel__state button {
  margin-top: 12px;
}

.device-detail-panel__summary {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.device-detail-panel__summary article,
.device-detail-panel__fields div,
.device-detail-panel__metrics article {
  padding: 12px;
  border: 1px solid rgba(72, 224, 255, 0.18);
  background: rgba(6, 28, 110, 0.48);
}

.device-detail-panel__summary span,
.device-detail-panel__summary strong,
.device-detail-panel__fields dt,
.device-detail-panel__fields dd,
.device-detail-panel__metrics span,
.device-detail-panel__metrics strong {
  display: block;
}

.device-detail-panel__summary span,
.device-detail-panel__metrics span,
.device-detail-panel__fields dt {
  color: #8dcfff;
}

.device-detail-panel__summary strong,
.device-detail-panel__fields dd,
.device-detail-panel__metrics strong {
  margin-top: 8px;
  color: #e8f7ff;
}

.device-detail-panel__fields {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin: 12px 0;
}

.device-detail-panel__fields dt,
.device-detail-panel__fields dd {
  margin: 0;
}

.device-detail-panel__metrics {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.device-detail-panel__metrics small {
  margin-left: 4px;
}

.device-detail-panel__actions {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin-top: 16px;
}

.device-detail-panel__actions button {
  padding: 12px 14px;
  border: 1px solid rgba(72, 224, 255, 0.24);
  background: rgba(7, 33, 126, 0.64);
  color: #e8f7ff;
  cursor: pointer;
}

.device-detail-panel__actions button.is-primary {
  color: #57ecff;
  border-color: rgba(87, 236, 255, 0.52);
}

@media (max-width: 768px) {
  .device-detail-panel__summary,
  .device-detail-panel__fields,
  .device-detail-panel__metrics,
  .device-detail-panel__actions {
    grid-template-columns: 1fr;
  }
}
</style>
