<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'

import { useSystemStore } from '@/stores/system'

const router = useRouter()
const systemStore = useSystemStore()

const stationCards = computed(() => systemStore.stationCards)

function openStation(stationId: string): void {
  systemStore.selectStation(stationId)
  void router.push(`/stations/${stationId}`)
}
</script>

<template>
  <section class="station-overview page-shell">
    <header class="station-overview__header">
      <div>
        <h2>场站实时监控</h2>
        <p>按资源类型进入专题监控界面，展示该场站的出力、链路质量、设备状态与模拟控制功能。</p>
      </div>
      <span>{{ stationCards.length }} 个资源专题入口</span>
    </header>

    <section class="station-overview__grid">
      <article
        v-for="station in stationCards"
        :key="station.stationId"
        class="station-entry-card"
        @click="openStation(station.stationId)"
      >
        <div class="station-entry-card__header">
          <div>
            <p>{{ station.resourceLabel }}</p>
            <h3>{{ station.stationName }}</h3>
          </div>
          <span :style="{ color: systemStore.linkStateMetaMap[station.linkState].color }">
            {{ station.stateLabel }}
          </span>
        </div>

        <div class="station-entry-card__metrics">
          <p><span>{{ station.powerMetricLabel }}</span><strong>{{ station.displayPower }} kW</strong></p>
          <p><span>可调容量</span><strong>{{ station.availableCapacity }} kW</strong></p>
          <p><span>通信时延</span><strong>{{ station.latency }} ms</strong></p>
          <p><span>质量评分</span><strong>{{ station.qualityScore }}</strong></p>
        </div>

        <div class="station-entry-card__footer">
          <span>点击进入专题页</span>
          <strong>查看详情</strong>
        </div>
      </article>
    </section>
  </section>
</template>

<style scoped>
.station-overview {
  display: grid;
  gap: 16px;
}

.station-overview__header {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  padding: 20px;
  border: 1px solid rgba(72, 224, 255, 0.24);
  border-radius: 8px;
  background: linear-gradient(180deg, rgba(8, 34, 128, 0.8), rgba(4, 24, 95, 0.84));
}

.station-overview__header h2,
.station-overview__header p {
  margin: 0;
}

.station-overview__header p {
  margin-top: 8px;
  color: #a8ddff;
}

.station-overview__header span {
  color: #62ffb6;
  white-space: nowrap;
}

.station-overview__grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(260px, 100%), 1fr));
  gap: 16px;
}

.station-entry-card {
  padding: 18px;
  border: 1px solid rgba(72, 224, 255, 0.24);
  border-radius: 8px;
  background: linear-gradient(180deg, rgba(8, 34, 128, 0.8), rgba(4, 24, 95, 0.84));
  transition: border-color 160ms ease, transform 160ms ease;
  cursor: pointer;
}

.station-entry-card:hover {
  border-color: rgba(72, 224, 255, 0.62);
  transform: translateY(-2px);
}

.station-entry-card__header {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
}

.station-entry-card__header p,
.station-entry-card__header h3 {
  margin: 0;
}

.station-entry-card__header p {
  color: #67cfff;
  font-size: 12px;
}

.station-entry-card__header h3 {
  margin-top: 4px;
  font-size: 24px;
}

.station-entry-card__metrics {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin-top: 16px;
}

.station-entry-card__metrics p {
  margin: 0;
  padding: 10px;
  border: 1px solid rgba(72, 224, 255, 0.18);
  border-radius: 5px;
  background: rgba(4, 29, 111, 0.42);
}

.station-entry-card__metrics span,
.station-entry-card__metrics strong {
  display: block;
}

.station-entry-card__metrics span {
  color: #8fcfff;
  font-size: 12px;
}

.station-entry-card__metrics strong {
  margin-top: 6px;
}

.station-entry-card__footer {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-top: 14px;
  color: #8fcfff;
}

.station-entry-card__footer strong {
  color: #ffe56d;
}

@media (max-width: 768px) {
  .station-overview__header {
    flex-direction: column;
  }

  .station-entry-card__metrics {
    grid-template-columns: 1fr;
  }
}
</style>
