<script setup lang="ts">
import { computed } from 'vue'

import { linkStateMetaMap, resourceTypeLabelMap } from '@/mock/systemData'
import type { ExperimentReplayPoint, StationState } from '@/types/system'

const props = defineProps<{
  stations: StationState[]
  selectedStationId: string
  replayPoint: ExperimentReplayPoint | null
}>()

const emit = defineEmits<{
  select: [stationId: string]
}>()

const centralPosition = {
  x: 50,
  y: 52
}

const links = computed(() => {
  return props.stations.map((station) => ({
    station,
    centerX: centralPosition.x,
    centerY: centralPosition.y,
    targetX: station.position.x,
    targetY: station.position.y,
    meta: linkStateMetaMap[station.linkState]
  }))
})

function selectStation(stationId: string): void {
  emit('select', stationId)
}
</script>

<template>
  <section class="link-quality-map">
    <header class="link-quality-map__header">
      <div>
        <span>链路质量地图</span>
        <h3>西北新能源场站通信拓扑</h3>
      </div>
      <p>以固定场站坐标映射虚拟电厂中心到边缘节点的通信质量与状态分布。</p>
    </header>

    <div class="link-quality-map__canvas" aria-label="链路质量地图">
      <svg class="link-quality-map__lines" viewBox="0 0 100 100" preserveAspectRatio="none" aria-hidden="true">
        <defs>
          <radialGradient id="link-core-glow" cx="50%" cy="50%" r="50%">
            <stop offset="0%" stop-color="rgba(72, 224, 255, 0.95)" />
            <stop offset="100%" stop-color="rgba(72, 224, 255, 0)" />
          </radialGradient>
        </defs>
        <circle cx="50" cy="52" r="9.5" fill="url(#link-core-glow)" />
        <g v-for="item in links" :key="item.station.stationId">
          <line
            :x1="item.centerX"
            :y1="item.centerY"
            :x2="item.targetX"
            :y2="item.targetY"
            :stroke="item.meta.color"
            :stroke-dasharray="item.station.linkState === 'S3' ? '3.2 2.2' : '0'"
            stroke-width="0.58"
            class="link-quality-map__line"
            :class="item.meta.animationClass"
          />
          <circle
            :cx="((item.centerX + item.targetX) / 2).toFixed(2)"
            :cy="((item.centerY + item.targetY) / 2).toFixed(2)"
            r="0.95"
            :fill="item.meta.color"
            class="link-quality-map__pulse"
          />
        </g>
      </svg>

      <article class="link-quality-map__hub">
        <span>虚拟电厂云侧平台</span>
        <strong>VPP CORE</strong>
        <small>{{ replayPoint ? '回放联动中' : '实时链路总览' }}</small>
      </article>

      <button
        v-for="station in stations"
        :key="station.stationId"
        type="button"
        class="link-quality-map__node"
        :class="{ 'is-selected': station.stationId === selectedStationId, 'is-offline': !station.online }"
        :style="{
          left: `${station.position.x}%`,
          top: `${station.position.y}%`,
          '--state-color': linkStateMetaMap[station.linkState].color
        }"
        :title="`${station.stationName} / ${resourceTypeLabelMap[station.resourceType]} / ${linkStateMetaMap[station.linkState].label} / 时延 ${station.latency}ms / 丢包 ${station.packetLossRate}% / 数据时效 ${station.dataAge}s / ${station.online ? '在线' : '离线'}`"
        :aria-pressed="station.stationId === selectedStationId"
        @click="selectStation(station.stationId)"
      >
        <span class="link-quality-map__node-tag">{{ station.stationName }}</span>
        <strong>{{ station.latency }}ms</strong>
        <small>{{ station.packetLossRate }}% / {{ station.online ? '在线' : '离线' }}</small>
        <b>{{ station.linkState }}</b>
      </button>
    </div>
  </section>
</template>

<style scoped>
.link-quality-map {
  display: grid;
  gap: 14px;
  padding: 18px;
  border: 1px solid rgba(72, 224, 255, 0.24);
  background:
    radial-gradient(circle at center, rgba(50, 169, 255, 0.18), transparent 34%),
    linear-gradient(180deg, rgba(8, 34, 128, 0.82), rgba(4, 24, 95, 0.9));
}

.link-quality-map__header {
  display: flex;
  justify-content: space-between;
  gap: 18px;
  align-items: flex-start;
}

.link-quality-map__header h3,
.link-quality-map__header p {
  margin: 0;
}

.link-quality-map__header span {
  color: #62ffb6;
  font-size: 13px;
}

.link-quality-map__header h3 {
  margin-top: 6px;
  font-size: 24px;
}

.link-quality-map__header p {
  max-width: 320px;
  color: #9ddfff;
  line-height: 1.65;
}

.link-quality-map__canvas {
  position: relative;
  min-height: 460px;
  overflow: hidden;
  border: 1px solid rgba(72, 224, 255, 0.16);
  background:
    radial-gradient(circle at 50% 52%, rgba(77, 226, 255, 0.22), transparent 16%),
    radial-gradient(circle at 46% 46%, rgba(105, 223, 255, 0.12), transparent 28%),
    linear-gradient(180deg, rgba(4, 22, 82, 0.94), rgba(2, 14, 58, 0.96));
}

.link-quality-map__canvas::before,
.link-quality-map__canvas::after {
  position: absolute;
  inset: 0;
  content: '';
  pointer-events: none;
}

.link-quality-map__canvas::before {
  background:
    linear-gradient(90deg, rgba(72, 224, 255, 0.08) 1px, transparent 1px),
    linear-gradient(180deg, rgba(72, 224, 255, 0.08) 1px, transparent 1px);
  background-size: 72px 72px;
  opacity: 0.24;
}

.link-quality-map__canvas::after {
  background:
    radial-gradient(circle at 50% 52%, rgba(72, 224, 255, 0.15), transparent 18%),
    radial-gradient(circle at 50% 52%, rgba(72, 224, 255, 0.08), transparent 32%);
}

.link-quality-map__lines {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
}

.link-quality-map__line {
  stroke-linecap: round;
  opacity: 0.88;
}

.link-quality-map__pulse {
  animation: linkPulse 2s linear infinite;
}

.link-quality-map__hub {
  position: absolute;
  left: 50%;
  top: 52%;
  z-index: 2;
  display: grid;
  gap: 6px;
  min-width: 178px;
  padding: 16px 18px;
  text-align: center;
  transform: translate(-50%, -50%);
  border: 1px solid rgba(72, 224, 255, 0.34);
  border-radius: 999px;
  background:
    radial-gradient(circle at top, rgba(82, 242, 255, 0.35), transparent 48%),
    rgba(5, 28, 110, 0.9);
  box-shadow: 0 0 32px rgba(72, 224, 255, 0.18);
}

.link-quality-map__hub span,
.link-quality-map__hub small {
  color: #9ddfff;
}

.link-quality-map__hub strong {
  color: #f7f3ae;
  font-size: 24px;
  letter-spacing: 0.08em;
}

.link-quality-map__node {
  position: absolute;
  z-index: 3;
  display: grid;
  gap: 5px;
  min-width: 132px;
  padding: 12px 12px 11px;
  border: 1px solid color-mix(in srgb, var(--state-color) 58%, transparent);
  border-radius: 10px;
  color: #ffffff;
  text-align: left;
  background:
    linear-gradient(180deg, rgba(12, 56, 160, 0.88), rgba(6, 26, 103, 0.9));
  box-shadow: 0 0 24px color-mix(in srgb, var(--state-color) 18%, transparent);
  transform: translate(-50%, -50%);
  cursor: pointer;
}

.link-quality-map__node::before {
  position: absolute;
  inset: -8px;
  content: '';
  border-radius: 14px;
  border: 1px solid color-mix(in srgb, var(--state-color) 26%, transparent);
  opacity: 0.56;
}

.link-quality-map__node.is-selected {
  box-shadow:
    0 0 0 1px rgba(255, 255, 255, 0.18),
    0 0 28px color-mix(in srgb, var(--state-color) 35%, transparent);
}

.link-quality-map__node.is-offline {
  background:
    linear-gradient(180deg, rgba(91, 28, 41, 0.88), rgba(49, 14, 26, 0.92));
}

.link-quality-map__node-tag,
.link-quality-map__node small {
  color: #9ddfff;
  font-size: 12px;
}

.link-quality-map__node strong {
  color: #f7f3ae;
  font-size: 18px;
}

.link-quality-map__node b {
  color: var(--state-color);
  font-size: 12px;
}

.is-stable {
  animation: flowStable 2.4s linear infinite;
}

.is-degraded,
.is-severe,
.is-recovering {
  stroke-dasharray: 2.6 2.6;
  animation: flowSignal 2s linear infinite;
}

.is-lost {
  stroke-dasharray: 3.2 2.2;
  animation: flowLost 3s linear infinite;
}

@keyframes flowStable {
  from {
    stroke-dashoffset: 14;
  }

  to {
    stroke-dashoffset: 0;
  }
}

@keyframes flowSignal {
  from {
    stroke-dashoffset: 10;
  }

  to {
    stroke-dashoffset: 0;
  }
}

@keyframes flowLost {
  from {
    stroke-dashoffset: 0;
  }

  to {
    stroke-dashoffset: -8;
  }
}

@keyframes linkPulse {
  0%,
  100% {
    opacity: 0.24;
    transform: scale(1);
  }

  50% {
    opacity: 1;
    transform: scale(1.5);
  }
}

@media (max-width: 960px) {
  .link-quality-map__header {
    display: grid;
  }

  .link-quality-map__header p {
    max-width: none;
  }

  .link-quality-map__canvas {
    min-height: 520px;
  }

  .link-quality-map__node {
    min-width: 116px;
  }
}

@media (max-width: 640px) {
  .link-quality-map__canvas {
    min-height: 600px;
  }

  .link-quality-map__hub {
    min-width: 150px;
    padding: 14px;
  }

  .link-quality-map__hub strong {
    font-size: 20px;
  }

  .link-quality-map__node {
    min-width: 102px;
    padding: 10px;
  }

  .link-quality-map__node strong {
    font-size: 16px;
  }
}
</style>
