<script setup lang="ts">
import { computed } from 'vue'

import { stationPowerMetricLabel } from '@/stores/system'
import type { StationState } from '@/types/system'

const props = defineProps<{
  station?: StationState
  stateLabelMap: Record<string, { label: string; color: string }>
  resourceLabelMap: Record<string, string>
}>()

const stationMetrics = computed(() => {
  if (!props.station) {
    return []
  }

  return [
    { label: stationPowerMetricLabel(props.station), value: `${Math.abs(props.station.activePower)} kW` },
    { label: '可调容量', value: `${props.station.availableCapacity} kW` },
    { label: '通信时延', value: `${props.station.latency} ms` },
    { label: '丢包率', value: `${props.station.packetLossRate}%` },
    { label: '数据时效', value: `${props.station.dataAge} s` },
    { label: '在线状态', value: props.station.online ? '在线' : '离线' }
  ]
})
</script>

<template>
  <div class="station-detail">
    <template v-if="station">
      <div class="station-detail__header">
        <div>
          <p class="station-detail__eyebrow">{{ resourceLabelMap[station.resourceType] }}</p>
          <h3>{{ station.stationName }}</h3>
        </div>
        <span
          class="station-detail__state"
          :style="{ '--state-color': stateLabelMap[station.linkState].color }"
        >
          {{ stateLabelMap[station.linkState].label }}
        </span>
      </div>

      <div class="station-detail__grid">
        <div
          v-for="metric in stationMetrics"
          :key="metric.label"
          class="station-detail__metric"
        >
          <span>{{ metric.label }}</span>
          <strong>{{ metric.value }}</strong>
        </div>
      </div>
    </template>
    <p v-else class="station-detail__empty">请选择场站节点查看详情。</p>
  </div>
</template>

<style scoped>
.station-detail {
  display: grid;
  gap: 16px;
}

.station-detail__header {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
}

.station-detail__eyebrow {
  margin: 0 0 6px;
  color: var(--color-gold-400);
  font-size: 12px;
  letter-spacing: 0.16em;
}

.station-detail__header h3 {
  margin: 0;
  font-size: 24px;
}

.station-detail__state {
  padding: 8px 12px;
  border-radius: 999px;
  color: var(--state-color);
  background: color-mix(in srgb, var(--state-color) 14%, transparent);
  white-space: nowrap;
}

.station-detail__grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.station-detail__metric {
  padding: 14px;
  border: 1px solid rgba(222, 198, 150, 0.16);
  border-radius: 18px 8px 18px 8px;
  background: rgba(31, 42, 57, 0.58);
}

.station-detail__metric span,
.station-detail__metric strong {
  display: block;
}

.station-detail__metric span {
  color: var(--color-text-500);
  font-size: 12px;
}

.station-detail__metric strong {
  margin-top: 8px;
}

.station-detail__empty {
  margin: 0;
  color: var(--color-text-300);
}

@media (max-width: 768px) {
  .station-detail__grid {
    grid-template-columns: 1fr;
  }
}
</style>
