<script setup lang="ts">
import { computed, ref } from 'vue'

import { baseStations, linkStateMetaMap } from '@/mock/systemData'
import { LinkState, type ExperimentTimelinePoint } from '@/types/system'

const props = defineProps<{
  points: ExperimentTimelinePoint[]
}>()

const selectedStationId = ref('all')
const selectedLinkState = ref<'all' | LinkState>('all')

const filteredPoints = computed(() => {
  return props.points.filter((point) => {
    const stationMatched = selectedStationId.value === 'all' || point.stationId === selectedStationId.value
    const stateMatched = selectedLinkState.value === 'all' || point.linkState === selectedLinkState.value
    return stationMatched && stateMatched
  })
})

function formatTime(timestamp: string): string {
  return new Date(timestamp).toLocaleTimeString('zh-CN', { hour12: false })
}

function stationName(stationId: string): string {
  return baseStations.find((station) => station.stationId === stationId)?.stationName ?? stationId
}
</script>

<template>
  <section class="link-timeline">
    <header>
      <div>
        <span>状态迁移时间线</span>
        <h3>S0-S4 链路变化</h3>
      </div>
      <div class="link-timeline__filters">
        <select v-model="selectedStationId">
          <option value="all">全部场站</option>
          <option v-for="station in baseStations" :key="station.stationId" :value="station.stationId">
            {{ station.stationName }}
          </option>
        </select>
        <select v-model="selectedLinkState">
          <option value="all">全部状态</option>
          <option v-for="state in Object.values(LinkState)" :key="state" :value="state">
            {{ state }} {{ linkStateMetaMap[state].label }}
          </option>
        </select>
      </div>
    </header>

    <div v-if="filteredPoints.length > 0" class="link-timeline__track">
      <article
        v-for="point in filteredPoints"
        :key="`${point.timestamp}-${point.stationId}-${point.linkState}-${point.eventTitle}`"
        class="link-timeline__point"
        :style="{ '--state-color': linkStateMetaMap[point.linkState].color }"
        :title="`${formatTime(point.timestamp)} / ${stationName(point.stationId)} / ${linkStateMetaMap[point.linkState].label} / ${point.latency}ms / 丢包 ${point.packetLossRate}% / ${point.eventTitle || '无事件说明'}`"
      >
        <span>{{ formatTime(point.timestamp) }}</span>
        <strong>{{ stationName(point.stationId) }}</strong>
        <b>{{ point.linkState }} {{ linkStateMetaMap[point.linkState].label }}</b>
        <small>{{ point.latency }}ms / {{ point.packetLossRate }}% / {{ point.online ? '在线' : '离线' }}</small>
        <p v-if="point.eventTitle">{{ point.eventTitle }}</p>
      </article>
    </div>
    <p v-else class="link-timeline__empty">当前筛选条件下没有链路状态点。</p>
  </section>
</template>

<style scoped>
.link-timeline {
  display: grid;
  gap: 12px;
  padding: 16px;
  border: 1px solid rgba(72, 224, 255, 0.24);
  background: linear-gradient(180deg, rgba(8, 34, 128, 0.82), rgba(4, 24, 95, 0.88));
}

.link-timeline header {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
}

.link-timeline h3,
.link-timeline p {
  margin: 0;
}

.link-timeline header span {
  color: #62ffb6;
  font-size: 13px;
}

.link-timeline__filters {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.link-timeline select {
  border: 1px solid rgba(72, 224, 255, 0.25);
  background: rgba(0, 22, 76, 0.72);
  color: #ffffff;
  padding: 7px 10px;
}

.link-timeline__track {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 10px;
}

.link-timeline__point {
  display: grid;
  gap: 5px;
  padding: 10px;
  border: 1px solid color-mix(in srgb, var(--state-color) 58%, transparent);
  border-left: 4px solid var(--state-color);
  background: rgba(4, 29, 111, 0.45);
}

.link-timeline__point span,
.link-timeline__point small {
  color: #9ddfff;
  font-size: 12px;
}

.link-timeline__point b {
  color: var(--state-color);
}

.link-timeline__point p {
  color: #d8f6ff;
  line-height: 1.5;
}

.link-timeline__empty {
  color: #9ddfff;
}

@media (max-width: 768px) {
  .link-timeline header {
    display: grid;
  }
}
</style>
