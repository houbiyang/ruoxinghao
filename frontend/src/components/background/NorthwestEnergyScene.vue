<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'

import { ResourceType, type LinkState, type StationState } from '@/types/system'

const props = defineProps<{
  stations: StationState[]
  selectedStationId?: string
  linkStateMetaMap: Record<
    string,
    { label: string; color: string; lineStyle: 'solid' | 'dashed'; animationClass: string }
  >
  resourceTypeLabelMap: Record<string, string>
  activeState: LinkState
}>()

const emit = defineEmits<{
  selectStation: [stationId: string]
}>()

const panelRef = ref<HTMLElement>()
const tooltipStation = ref<StationState>()
const isReducedMotion = ref(false)
let mediaQueryList: MediaQueryList | undefined
let frameId = 0

const centerPoint = { x: 500, y: 305 }

const resourceIconMap: Record<ResourceType, string> = {
  [ResourceType.Photovoltaic]: '光伏',
  [ResourceType.Wind]: '风电',
  [ResourceType.Thermal]: '火电',
  [ResourceType.Grid]: '市电',
  [ResourceType.Storage]: '储能',
  [ResourceType.Load]: '负荷',
  [ResourceType.Charging]: '充电桩'
}

const stationPanels = computed(() => {
  return props.stations.map((station) => {
    const stateMeta = props.linkStateMetaMap[station.linkState]
    const powerLabel = getPowerLabel(station)
    return {
      ...station,
      stateMeta,
      panelX: station.position.x * 10,
      panelY: station.position.y * 5.9,
      displayName: resourceIconMap[station.resourceType],
      powerLabel
    }
  })
})

const renewableRatio = computed(() => {
  const renewablePower = props.stations
    .filter((station) => [ResourceType.Photovoltaic, ResourceType.Wind].includes(station.resourceType))
    .reduce((sum, station) => sum + Math.max(station.activePower, 0), 0)
  const supplyPower = props.stations.reduce((sum, station) => sum + getSupplyPower(station), 0)

  if (supplyPower === 0) {
    return 0
  }

  return Math.round((renewablePower / supplyPower) * 100)
})

function getNodePath(station: StationState): string {
  const targetX = station.position.x * 10
  const targetY = station.position.y * 5.9
  const controlX = (centerPoint.x + targetX) / 2
  const controlY = targetY > centerPoint.y ? centerPoint.y + 96 : centerPoint.y - 96
  return `M ${centerPoint.x} ${centerPoint.y} Q ${controlX} ${controlY} ${targetX} ${targetY}`
}

function getInboundNodePath(station: StationState): string {
  const targetX = station.position.x * 10
  const targetY = station.position.y * 5.9
  const controlX = (centerPoint.x + targetX) / 2
  const controlY = targetY > centerPoint.y ? centerPoint.y + 96 : centerPoint.y - 96
  return `M ${targetX} ${targetY} Q ${controlX} ${controlY} ${centerPoint.x} ${centerPoint.y}`
}

function getEnergyFlowPath(station: StationState): string {
  return isConsumerFlow(station) ? getNodePath(station) : getInboundNodePath(station)
}

function isConsumerFlow(station: StationState): boolean {
  if ([ResourceType.Load, ResourceType.Charging].includes(station.resourceType)) {
    return true
  }
  if (station.resourceType === ResourceType.Storage) {
    return station.activePower < 0
  }
  if (station.resourceType === ResourceType.Grid) {
    return station.activePower < 0
  }
  return false
}

function getSupplyPower(station: StationState): number {
  if ([ResourceType.Photovoltaic, ResourceType.Wind, ResourceType.Thermal].includes(station.resourceType)) {
    return Math.max(station.activePower, 0)
  }
  if (station.resourceType === ResourceType.Grid || station.resourceType === ResourceType.Storage) {
    return Math.max(station.activePower, 0)
  }
  return 0
}

function getPowerLabel(station: StationState): string {
  const power = Math.abs(station.activePower)
  if ([ResourceType.Photovoltaic, ResourceType.Wind, ResourceType.Thermal].includes(station.resourceType)) {
    return `发电功率：${power}kW`
  }
  if (station.resourceType === ResourceType.Grid) {
    return station.activePower >= 0 ? `市电购电：${power}kW` : `市电上网：${power}kW`
  }
  if (station.resourceType === ResourceType.Storage) {
    return station.activePower >= 0 ? `储能放电：${power}kW` : `储能充电：${power}kW`
  }
  return `用电功率：${power}kW`
}

function getEnergyDuration(linkState: LinkState): string {
  if (linkState === 'S1') {
    return '2.2s'
  }

  if (linkState === 'S2') {
    return '3s'
  }

  if (linkState === 'S4') {
    return '1.5s'
  }

  return '1.8s'
}

function getEnergyParticleRadius(linkState: LinkState): number {
  if (linkState === 'S2') {
    return 4.5
  }

  if (linkState === 'S4') {
    return 5.5
  }

  return 5
}

function getEnergyParticleOpacity(linkState: LinkState): number {
  if (linkState === 'S1') {
    return 0.72
  }

  if (linkState === 'S2') {
    return 0.58
  }

  if (linkState === 'S4') {
    return 0.9
  }

  return 0.82
}

function getLinkLabelPosition(station: StationState): { x: number; y: number } {
  const targetX = station.position.x * 10
  const targetY = station.position.y * 5.9
  return {
    x: (centerPoint.x + targetX) / 2,
    y: (centerPoint.y + targetY) / 2 + (targetY > centerPoint.y ? 12 : -10)
  }
}

function getNodeIconPath(resourceType: ResourceType): string {
  if (resourceType === ResourceType.Photovoltaic) {
    return 'M -26 -4 L -6 -20 L 14 -4 L -6 12 Z M 4 -26 L 24 -10 L 4 6 L -16 -10 Z'
  }

  if (resourceType === ResourceType.Wind) {
    return 'M 0 -26 L 0 18 M 0 -20 L -18 -4 M 0 -20 L 18 -10 M 0 -20 L 2 0'
  }

  if (resourceType === ResourceType.Thermal) {
    return 'M -24 16 H 24 M -18 16 V -10 H -6 V 16 M 2 16 V -22 H 18 V 16 M -22 -10 H -6 M 2 -22 H 18'
  }

  if (resourceType === ResourceType.Grid) {
    return 'M -20 18 L -4 -22 L 2 -2 L 16 -18 L 4 18 L -2 0 Z'
  }

  if (resourceType === ResourceType.Storage) {
    return 'M -24 -12 H 18 V 14 H -24 Z M 18 -4 H 26 V 6 H 18 Z'
  }

  if (resourceType === ResourceType.Load) {
    return 'M -18 16 V -8 H -6 V -20 H 18 V 16 M -12 4 H 12'
  }

  return 'M -20 8 H 16 V -8 H -8 L -18 6 Z M 8 -10 H 20 V 4 H 8'
}

function handlePointerMove(event: MouseEvent): void {
  if (!panelRef.value || isReducedMotion.value) {
    return
  }

  if (frameId) {
    cancelAnimationFrame(frameId)
  }

  const bounds = panelRef.value.getBoundingClientRect()
  const offsetX = ((event.clientX - bounds.left) / bounds.width - 0.5) * 10
  const offsetY = ((event.clientY - bounds.top) / bounds.height - 0.5) * 8

  frameId = requestAnimationFrame(() => {
    panelRef.value?.style.setProperty('--microgrid-offset-x', `${offsetX.toFixed(2)}px`)
    panelRef.value?.style.setProperty('--microgrid-offset-y', `${offsetY.toFixed(2)}px`)
  })
}

function resetPointerEffect(): void {
  panelRef.value?.style.setProperty('--microgrid-offset-x', '0px')
  panelRef.value?.style.setProperty('--microgrid-offset-y', '0px')
}

function updateMotionPreference(event?: MediaQueryListEvent): void {
  isReducedMotion.value = event ? event.matches : Boolean(mediaQueryList?.matches)
}

function selectStation(stationId: string): void {
  emit('selectStation', stationId)
}

function showTooltip(station: StationState): void {
  tooltipStation.value = station
}

function hideTooltip(): void {
  tooltipStation.value = undefined
}

onMounted(() => {
  mediaQueryList = window.matchMedia('(prefers-reduced-motion: reduce)')
  updateMotionPreference()
  mediaQueryList.addEventListener('change', updateMotionPreference)
})

onBeforeUnmount(() => {
  if (frameId) {
    cancelAnimationFrame(frameId)
  }
  mediaQueryList?.removeEventListener('change', updateMotionPreference)
})
</script>

<template>
  <div
    ref="panelRef"
    class="microgrid-scene"
    :class="props.linkStateMetaMap[activeState].animationClass"
    @mousemove="handlePointerMove"
    @mouseleave="resetPointerEffect"
  >
    <svg class="microgrid-scene__svg" viewBox="0 0 1000 620" aria-hidden="true">
      <defs>
        <radialGradient id="sceneCoreGlow" cx="50%" cy="50%" r="50%">
          <stop offset="0%" stop-color="#63f7ff" stop-opacity="0.9" />
          <stop offset="65%" stop-color="#2a90ff" stop-opacity="0.24" />
          <stop offset="100%" stop-color="#2a90ff" stop-opacity="0" />
        </radialGradient>
        <linearGradient id="scenePlatformFill" x1="0%" x2="100%" y1="0%" y2="100%">
          <stop offset="0%" stop-color="#79d8ff" stop-opacity="0.95" />
          <stop offset="100%" stop-color="#2950cb" stop-opacity="0.98" />
        </linearGradient>
        <linearGradient id="sceneLineGlow" x1="0%" x2="100%" y1="0%" y2="0%">
          <stop offset="0%" stop-color="#56efff" />
          <stop offset="100%" stop-color="#8effc6" />
        </linearGradient>
      </defs>

      <g class="microgrid-scene__parallax microgrid-scene__parallax--far">
        <ellipse cx="500" cy="510" rx="330" ry="72" fill="rgba(59, 125, 255, 0.1)" />
        <ellipse cx="500" cy="294" rx="250" ry="162" fill="rgba(51, 112, 255, 0.08)" />
        <ellipse cx="500" cy="294" rx="276" ry="182" fill="none" stroke="rgba(86, 239, 255, 0.22)" stroke-width="3" stroke-dasharray="4 12" />
        <ellipse cx="500" cy="304" rx="196" ry="110" fill="rgba(63, 112, 255, 0.08)" />
        <ellipse cx="500" cy="304" rx="326" ry="112" fill="none" stroke="rgba(92, 152, 255, 0.12)" stroke-width="26" />
      </g>

      <g class="microgrid-scene__parallax microgrid-scene__parallax--mid">
        <circle cx="500" cy="268" r="122" fill="url(#sceneCoreGlow)" />
        <circle cx="500" cy="268" r="96" fill="none" stroke="rgba(98, 255, 204, 0.75)" stroke-width="8" />
        <circle cx="500" cy="268" r="124" fill="none" stroke="rgba(86, 239, 255, 0.28)" stroke-width="16" />
        <circle cx="500" cy="268" r="138" fill="none" stroke="rgba(100, 255, 188, 0.16)" stroke-width="3" />
        <circle cx="500" cy="352" r="88" fill="rgba(59, 211, 255, 0.24)" />
        <ellipse cx="500" cy="352" rx="118" ry="40" fill="rgba(89, 241, 255, 0.18)" />

        <path d="M420 168 A126 126 0 0 1 458 146" stroke="#5aff98" stroke-width="10" stroke-linecap="round" />
        <path d="M582 180 A126 126 0 0 1 604 226" stroke="#62ffb6" stroke-width="10" stroke-linecap="round" />
        <path d="M590 322 A126 126 0 0 1 548 364" stroke="#5dff88" stroke-width="10" stroke-linecap="round" />
        <ellipse cx="500" cy="352" rx="176" ry="62" fill="none" stroke="rgba(57, 217, 255, 0.18)" stroke-width="2" />
      </g>

      <g class="microgrid-scene__parallax microgrid-scene__parallax--near">
        <g v-for="station in stationPanels" :key="`${station.stationId}-path-group`">
          <path
            :d="getNodePath(station)"
            fill="none"
            :stroke="station.stateMeta.color"
            stroke-width="9"
            stroke-linecap="round"
            opacity="0.12"
          />
          <path
            :d="getNodePath(station)"
            fill="none"
            stroke="rgba(76, 236, 255, 0.18)"
            stroke-width="5"
            stroke-linecap="round"
            opacity="0.9"
          />
          <path
            :d="getNodePath(station)"
            fill="none"
            :stroke="station.stateMeta.color"
            stroke-width="3"
            stroke-linecap="round"
            :stroke-dasharray="station.stateMeta.lineStyle === 'dashed' ? '8 10' : '1 0'"
            opacity="0.92"
          >
            <animate
              v-if="!isReducedMotion && station.linkState !== 'S3'"
              attributeName="stroke-dashoffset"
              from="0"
              to="-28"
              :dur="getEnergyDuration(station.linkState)"
              repeatCount="indefinite"
            />
          </path>
          <path
            v-if="!isReducedMotion && station.linkState !== 'S3'"
            :d="getEnergyFlowPath(station)"
            fill="none"
            stroke="url(#sceneLineGlow)"
            stroke-width="6"
            stroke-linecap="round"
            stroke-dasharray="22 200"
            opacity="0.68"
          >
            <animate
              attributeName="stroke-dashoffset"
              from="0"
              to="-240"
              :dur="getEnergyDuration(station.linkState)"
              repeatCount="indefinite"
            />
          </path>
          <g v-if="!isReducedMotion && station.linkState !== 'S3'" class="scene-energy-particles">
            <circle
              :r="getEnergyParticleRadius(station.linkState)"
              :fill="station.linkState === 'S4' ? '#7be8ff' : '#62ff96'"
              :opacity="getEnergyParticleOpacity(station.linkState)"
            >
              <animateMotion
                :path="getEnergyFlowPath(station)"
                :dur="getEnergyDuration(station.linkState)"
                rotate="auto"
                repeatCount="indefinite"
              />
            </circle>
            <circle
              :r="Math.max(getEnergyParticleRadius(station.linkState) - 1.3, 2.8)"
              :fill="station.linkState === 'S4' ? '#d9fbff' : '#baffd3'"
              :opacity="Math.min(getEnergyParticleOpacity(station.linkState) + 0.1, 1)"
            >
              <animateMotion
                :path="getEnergyFlowPath(station)"
                :dur="getEnergyDuration(station.linkState)"
                begin="-0.9s"
                rotate="auto"
                repeatCount="indefinite"
              />
            </circle>
          </g>
        </g>

        <g
          v-for="station in stationPanels"
          :key="`${station.stationId}-label`"
          class="scene-link-label"
        >
          <text
            :x="getLinkLabelPosition(station).x"
            :y="getLinkLabelPosition(station).y"
            text-anchor="middle"
            :fill="station.stateMeta.color"
            font-size="12"
          >
            {{ station.powerLabel }}
          </text>
        </g>

        <g
          v-for="station in stationPanels"
          :key="station.stationId"
          class="scene-node"
          :class="{ 'is-selected': station.stationId === selectedStationId }"
          :transform="`translate(${station.panelX}, ${station.panelY})`"
          tabindex="0"
          role="button"
          :aria-label="`${station.stationName}，${resourceTypeLabelMap[station.resourceType]}`"
          @mouseenter="showTooltip(station)"
          @mouseleave="hideTooltip"
          @focus="showTooltip(station)"
          @blur="hideTooltip"
          @click="selectStation(station.stationId)"
          @keydown.enter.prevent="selectStation(station.stationId)"
          @keydown.space.prevent="selectStation(station.stationId)"
        >
          <polygon
            points="-86,38 0,4 86,38 0,72"
            fill="url(#scenePlatformFill)"
            opacity="0.92"
          />
          <polygon
            points="-86,38 0,72 0,86 -86,52"
            fill="#2042a9"
            opacity="0.96"
          />
          <polygon
            points="86,38 0,72 0,86 86,52"
            fill="#1a3790"
            opacity="0.96"
          />
          <polygon
            points="-56,20 0,0 56,20 0,40"
            fill="rgba(163, 243, 255, 0.28)"
            stroke="rgba(113, 241, 255, 0.5)"
          />
          <ellipse cx="0" cy="26" rx="70" ry="20" :fill="station.stateMeta.color" opacity="0.12" />
          <rect x="-52" y="-54" width="104" height="84" rx="10" fill="rgba(12, 41, 122, 0.64)" stroke="rgba(87, 222, 255, 0.46)" />
          <rect x="-40" y="-42" width="80" height="58" rx="8" fill="rgba(87, 222, 255, 0.1)" />
          <path
            :d="getNodeIconPath(station.resourceType)"
            fill="none"
            stroke="#ffffff"
            stroke-width="4"
            stroke-linecap="round"
            stroke-linejoin="round"
          />
          <text x="0" y="62" text-anchor="middle" fill="#ffffff" font-size="16" font-weight="700">{{ station.displayName }}</text>
        </g>
      </g>

      <g class="microgrid-scene__center-text">
        <text x="500" y="258" text-anchor="middle" fill="#ffe56d" font-size="56" font-weight="700">{{ renewableRatio }}%</text>
        <text x="500" y="308" text-anchor="middle" fill="#ffffff" font-size="24">新能源供电占比</text>
      </g>
    </svg>

    <div class="microgrid-scene__callouts">
      <article
        v-for="station in stationPanels"
        :key="`${station.stationId}-card`"
        class="scene-callout"
        :class="`scene-callout--${station.resourceType}`"
      >
        <div class="scene-callout__title-row">
          <span class="scene-callout__marker" :style="{ background: station.stateMeta.color }" />
          <strong>{{ station.stationName }}</strong>
        </div>
        <p>{{ station.powerLabel.replace('：', '： ') }}</p>
        <p>链路状态：{{ station.stateMeta.label }}</p>
      </article>
    </div>

    <div v-if="tooltipStation" class="microgrid-scene__tooltip">
      <strong>{{ tooltipStation.stationName }}</strong>
      <p>{{ resourceTypeLabelMap[tooltipStation.resourceType] }}</p>
      <p>{{ getPowerLabel(tooltipStation).replace('：', '： ') }}</p>
      <p>时延：{{ tooltipStation.latency }} ms</p>
    </div>
  </div>
</template>

<style scoped>
.microgrid-scene {
  --microgrid-offset-x: 0px;
  --microgrid-offset-y: 0px;
  position: relative;
  min-height: 560px;
  overflow: hidden;
  background:
    radial-gradient(circle at center, rgba(43, 143, 255, 0.16), transparent 36%),
    linear-gradient(180deg, rgba(7, 28, 112, 0.54), rgba(4, 19, 84, 0.76));
}

.microgrid-scene__svg {
  display: block;
  width: 100%;
  height: 560px;
}

.microgrid-scene__parallax--far {
  transform: translate(calc(var(--microgrid-offset-x) * 0.18), calc(var(--microgrid-offset-y) * 0.18));
}

.microgrid-scene__parallax--mid {
  transform: translate(calc(var(--microgrid-offset-x) * 0.38), calc(var(--microgrid-offset-y) * 0.26));
}

.microgrid-scene__parallax--near {
  transform: translate(calc(var(--microgrid-offset-x) * 0.56), calc(var(--microgrid-offset-y) * 0.42));
}

.scene-node {
  cursor: pointer;
  outline: none;
}

.scene-node.is-selected rect,
.scene-node.is-selected polygon:first-child {
  stroke: #ffe56d;
}

.scene-node.is-selected polygon:first-child {
  filter: drop-shadow(0 0 12px rgba(255, 229, 109, 0.38));
}

.scene-node:focus-visible rect,
.scene-node:focus-visible polygon:first-child {
  stroke: #ffffff;
  stroke-width: 2;
}

.microgrid-scene__callouts {
  position: absolute;
  inset: 0;
  pointer-events: none;
}

.scene-callout {
  position: absolute;
  width: 190px;
  padding: 10px 12px;
  border: 1px solid rgba(89, 219, 255, 0.36);
  background: rgba(6, 36, 130, 0.54);
  box-shadow: inset 0 0 16px rgba(95, 233, 255, 0.08);
}

.scene-callout::before,
.scene-callout::after {
  content: '';
  position: absolute;
  width: 14px;
  height: 14px;
  border-top: 2px solid rgba(89, 219, 255, 0.72);
}

.scene-callout::before {
  left: 6px;
  top: 6px;
  border-left: 2px solid rgba(89, 219, 255, 0.72);
}

.scene-callout::after {
  right: 6px;
  top: 6px;
  border-right: 2px solid rgba(89, 219, 255, 0.72);
}

.scene-callout--photovoltaic {
  left: 18px;
  top: 104px;
}

.scene-callout--wind {
  right: 18px;
  top: 138px;
}

.scene-callout--thermal {
  left: 405px;
  top: 78px;
}

.scene-callout--grid {
  left: 8px;
  top: 388px;
}

.scene-callout--storage {
  left: 380px;
  bottom: 28px;
}

.scene-callout--load {
  right: 18px;
  top: 392px;
}

.scene-callout--charging {
  right: 98px;
  bottom: 28px;
}

.scene-callout__title-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.scene-callout__marker {
  width: 8px;
  height: 8px;
  box-shadow: 0 0 10px currentColor;
}

.scene-callout strong,
.scene-callout p {
  margin: 0;
}

.scene-callout strong {
  color: var(--screen-cyan);
}

.scene-callout p {
  margin-top: 4px;
  color: #c4ebff;
  font-size: 12px;
}

.microgrid-scene__tooltip {
  position: absolute;
  left: 50%;
  top: 18px;
  transform: translateX(-50%);
  width: 220px;
  padding: 12px;
  border: 1px solid rgba(89, 219, 255, 0.36);
  background: rgba(8, 30, 106, 0.82);
  text-align: center;
}

.microgrid-scene__tooltip strong,
.microgrid-scene__tooltip p {
  margin: 0;
}

.microgrid-scene__tooltip p {
  margin-top: 4px;
  color: #c4ebff;
  font-size: 12px;
}

.scene-link-label text {
  text-shadow: 0 0 10px rgba(78, 234, 255, 0.4);
}

.scene-energy-particles circle {
  filter: drop-shadow(0 0 8px rgba(98, 255, 150, 0.58));
}

@media (max-width: 1366px) {
  .microgrid-scene {
    min-height: 520px;
  }

  .microgrid-scene__svg {
    height: 520px;
  }

  .scene-callout {
    width: 156px;
  }
}

@media (max-width: 900px) {
  .microgrid-scene {
    min-height: 700px;
  }

  .microgrid-scene__svg {
    height: 460px;
  }

  .microgrid-scene__callouts {
    display: none;
  }
}
</style>
