<script setup lang="ts">
import { computed } from 'vue'

import { linkStateMetaMap, resourceTypeLabelMap } from '@/mock/systemData'
import { ResourceType, type StationDeviceSnapshot, type StationState } from '@/types/system'

const props = defineProps<{
  station: StationState | null
  devices: StationDeviceSnapshot[]
  selectedDeviceId: string
}>()

const emit = defineEmits<{
  (event: 'select-device', deviceId: string): void
}>()

const parentLabel = computed(() => {
  if (!props.station) {
    return '场站'
  }
  return resourceTypeLabelMap[props.station.resourceType]
})

const parentIcon = computed(() => {
  if (!props.station) {
    return '◎'
  }
  const iconMap: Record<ResourceType, string> = {
    [ResourceType.Photovoltaic]: '☀',
    [ResourceType.Wind]: '◌',
    [ResourceType.Thermal]: '♨',
    [ResourceType.Grid]: '⌁',
    [ResourceType.Storage]: '▣',
    [ResourceType.Load]: '◫',
    [ResourceType.Charging]: '▥'
  }
  return iconMap[props.station.resourceType]
})

const layoutDevices = computed(() => {
  const layoutMap: Record<string, { order: string[]; left: number[] }> = {
    'pv-gobi-01': {
      order: ['pv-array-a', 'pv-array-b', 'inverter-group', 'combiner-box-01', 'weather-station-01'],
      left: [8, 28, 45, 66, 84]
    },
    'wind-ridge-02': {
      order: ['wind-turbine-a', 'wind-turbine-b', 'yaw-controller-01', 'anemometer-01', 'wind-edge-01'],
      left: [8, 28, 45, 66, 84]
    },
    'thermal-plant-06': {
      order: ['thermal-generator-01', 'thermal-turbine-01', 'thermal-boiler-01', 'thermal-meter-01', 'thermal-edge-01'],
      left: [8, 28, 45, 66, 84]
    },
    'grid-link-00': {
      order: ['grid-breaker-01', 'grid-transformer-01', 'grid-meter-01', 'grid-protection-01', 'grid-edge-01'],
      left: [8, 28, 45, 66, 84]
    },
    'storage-hub-03': {
      order: ['storage-pcs-01', 'storage-bms-01', 'battery-cluster-01', 'thermal-unit-01', 'storage-edge-01'],
      left: [8, 28, 45, 66, 84]
    },
    'load-oasis-04': {
      order: ['load-feeder-01', 'load-feeder-02', 'hvac-controller-01', 'load-meter-01', 'load-edge-01'],
      left: [8, 28, 45, 66, 84]
    },
    'charge-corridor-05': {
      order: ['charge-pile-01', 'charge-pile-02', 'charge-distributor-01', 'queue-sensor-01', 'charge-edge-01'],
      left: [8, 28, 45, 66, 84]
    }
  }

  const currentLayout = props.station ? layoutMap[props.station.stationId] : undefined
  if (currentLayout) {
    return currentLayout.order
      .map((deviceId, index) => {
        const device = props.devices.find((item) => item.deviceId === deviceId)
        if (!device) {
          return null
        }
        return {
          ...device,
          left: currentLayout.left[index]
        }
      })
      .filter((device): device is NonNullable<typeof device> => device !== null)
  }

  return props.devices.map((device, index) => ({
    ...device,
    left: props.devices.length === 1 ? 50 : 12 + (76 / Math.max(props.devices.length - 1, 1)) * index
  }))
})

function powerText(device: StationDeviceSnapshot): string {
  return device.activePower === null ? '--' : `${device.activePower.toFixed(1)} kW`
}

function deviceIcon(device: StationDeviceSnapshot): string {
  if (device.deviceType === 'pv-array') {
    return '▥'
  }
  if (device.deviceType.includes('inverter')) {
    return '▣'
  }
  if (device.deviceType.includes('weather')) {
    return '◌'
  }
  if (device.deviceType.includes('wind')) {
    return '◐'
  }
  if (device.deviceType.includes('yaw')) {
    return '◎'
  }
  if (device.deviceType.includes('anemometer')) {
    return '◌'
  }
  if (device.deviceType.includes('grid')) {
    return '⌁'
  }
  if (device.deviceType.includes('protection')) {
    return '⟐'
  }
  if (device.deviceType.includes('storage') || device.deviceType.includes('battery')) {
    return '▦'
  }
  if (device.deviceType.includes('thermal')) {
    return '◍'
  }
  if (device.deviceType.includes('load')) {
    return '◫'
  }
  if (device.deviceType.includes('hvac')) {
    return '◔'
  }
  if (device.deviceType.includes('charging')) {
    return '⌂'
  }
  if (device.deviceType.includes('charge-distributor')) {
    return '⌘'
  }
  if (device.deviceType.includes('queue')) {
    return '◉'
  }
  if (device.deviceType.includes('edge')) {
    return '◈'
  }
  return '◈'
}

function selectDevice(deviceId: string): void {
  emit('select-device', deviceId)
}

function linkPath(deviceLeft: number): string {
  return `M 500 175 C 500 ${230 + Math.abs(deviceLeft - 50) * 1.4}, ${deviceLeft * 10} 245, ${deviceLeft * 10} 342`
}

function linkPowerLabelPosition(deviceLeft: number): { x: number; y: number } {
  const targetX = deviceLeft * 10
  const controlY = 230 + Math.abs(deviceLeft - 50) * 1.4
  const midX = 500 * 0.16 + 500 * 0.34 + targetX * 0.5
  const direction = deviceLeft >= 50 ? 1 : -1
  const horizontalOffset = Math.abs(deviceLeft - 50) < 8 ? 0 : direction * 12
  return {
    x: midX + horizontalOffset,
    y: 175 * 0.05 + controlY * 0.2 + 245 * 0.35 + 342 * 0.4 - 8
  }
}

function pathId(deviceId: string): string {
  return `device-link-${deviceId}`
}

function particlePath(deviceLeft: number): string {
  return `M ${deviceLeft * 10} 404 C ${deviceLeft * 10} 312, ${deviceLeft * 10} 250, 500 175`
}

function particleDuration(linkState: StationDeviceSnapshot['linkState']): string {
  switch (linkState) {
    case 'S1':
      return '5.8s'
    case 'S2':
      return '7.2s'
    case 'S3':
      return '0s'
    case 'S4':
      return '4.8s'
    default:
      return '4.2s'
  }
}

function showParticle(linkState: StationDeviceSnapshot['linkState']): boolean {
  return linkState !== 'S3'
}

function particleBegin(deviceLeft: number): string {
  return `${Math.max(0, (deviceLeft - 8) / 38)}s`
}

function particleTrailBegin(deviceLeft: number): string {
  return `${Math.max(0.45, (deviceLeft - 8) / 38 + 0.9)}s`
}
</script>

<template>
  <section class="device-topology">
    <header class="device-topology__header">
      <h3>{{ parentLabel }} / 子设备拓扑</h3>
    </header>

    <div v-if="station && layoutDevices.length" class="device-topology__canvas">
      <svg class="device-topology__links" viewBox="0 0 1000 520" preserveAspectRatio="none" aria-hidden="true">
        <defs>
          <linearGradient id="device-link-gradient" x1="0%" x2="100%">
            <stop offset="0%" stop-color="rgba(80, 225, 255, 0.2)" />
            <stop offset="45%" stop-color="rgba(58, 204, 255, 0.95)" />
            <stop offset="100%" stop-color="rgba(92, 255, 221, 0.55)" />
          </linearGradient>
          <linearGradient id="device-link-energy-gradient" x1="0%" y1="100%" x2="0%" y2="0%">
            <stop offset="0%" stop-color="rgba(73, 242, 168, 0)" />
            <stop offset="28%" stop-color="rgba(73, 242, 168, 0.3)" />
            <stop offset="55%" stop-color="rgba(125, 255, 195, 0.95)" />
            <stop offset="100%" stop-color="rgba(73, 242, 168, 0)" />
          </linearGradient>
        </defs>
        <template v-for="device in layoutDevices" :key="device.deviceId">
          <path
            :id="pathId(device.deviceId)"
            :d="linkPath(device.left)"
            class="device-topology__path"
            :class="linkStateMetaMap[device.linkState].animationClass"
            stroke="url(#device-link-gradient)"
          />
          <path
            :d="linkPath(device.left)"
            class="device-topology__path device-topology__path--energy"
            :class="linkStateMetaMap[device.linkState].animationClass"
            stroke="url(#device-link-energy-gradient)"
          />
          <circle
            v-if="showParticle(device.linkState)"
            class="device-topology__particle"
            :class="linkStateMetaMap[device.linkState].animationClass"
            r="6"
          >
            <animateMotion
              :dur="particleDuration(device.linkState)"
              :begin="particleBegin(device.left)"
              repeatCount="indefinite"
              calcMode="linear"
              keyPoints="0;1"
              keyTimes="0;1"
              rotate="auto"
              :path="particlePath(device.left)"
            />
          </circle>
          <circle
            v-if="showParticle(device.linkState)"
            class="device-topology__particle device-topology__particle--trail"
            :class="linkStateMetaMap[device.linkState].animationClass"
            r="4.2"
          >
            <animateMotion
              :dur="particleDuration(device.linkState)"
              :begin="particleTrailBegin(device.left)"
              repeatCount="indefinite"
              calcMode="linear"
              keyPoints="0;1"
              keyTimes="0;1"
              rotate="auto"
              :path="particlePath(device.left)"
            />
          </circle>
          <g
            class="device-topology__power-label"
            :transform="`translate(${linkPowerLabelPosition(device.left).x}, ${linkPowerLabelPosition(device.left).y})`"
          >
            <rect x="-38" y="-13" width="76" height="20" rx="10" />
            <text text-anchor="middle" dominant-baseline="middle" y="-2">
              {{ powerText(device) }}
            </text>
          </g>
        </template>
      </svg>

      <article class="device-topology__parent">
        <div class="device-topology__icon">{{ parentIcon }}</div>
        <strong>{{ parentLabel }}</strong>
        <span>{{ station.activePower.toFixed(1) }} kW | {{ station.linkState }}</span>
      </article>

      <button
        v-for="device in layoutDevices"
        :key="device.deviceId"
        type="button"
        class="device-node"
        :class="{ 'is-selected': selectedDeviceId === device.deviceId, 'is-offline': device.status === '离线' }"
        :style="{ left: `${device.left}%` }"
        @click="selectDevice(device.deviceId)"
        @keydown.enter.prevent="selectDevice(device.deviceId)"
        @keydown.space.prevent="selectDevice(device.deviceId)"
      >
        <span class="device-node__icon">{{ deviceIcon(device) }}</span>
        <strong>{{ device.deviceName }}</strong>
        <span class="device-node__status">
          <i :style="{ backgroundColor: linkStateMetaMap[device.linkState].color }"></i>
          {{ device.status }}
        </span>
        <small>{{ powerText(device) }} | {{ device.linkState }}</small>
      </button>
    </div>

    <div v-else class="device-topology__empty">暂无子设备数据</div>
  </section>
</template>

<style scoped>
.device-topology {
  border: 1px solid rgba(72, 224, 255, 0.24);
  background: linear-gradient(180deg, rgba(8, 34, 128, 0.8), rgba(4, 24, 95, 0.84));
}

.device-topology__header {
  padding: 14px 16px;
  border-bottom: 1px solid rgba(72, 224, 255, 0.18);
}

.device-topology__header h3 {
  margin: 0;
  font-size: 17px;
}

.device-topology__canvas {
  position: relative;
  min-height: 430px;
  padding: 28px 12px 18px;
  overflow: hidden;
}

.device-topology__links {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
}

.device-topology__path {
  fill: none;
  stroke-width: 4;
  opacity: 0.88;
  filter: drop-shadow(0 0 10px rgba(58, 204, 255, 0.48));
}

.device-topology__path--energy {
  stroke-width: 6;
  opacity: 0.95;
  stroke-linecap: round;
  stroke-dasharray: 34 196;
  animation: energy-flow 3.2s linear infinite;
  filter: drop-shadow(0 0 12px rgba(73, 242, 168, 0.72));
}

.device-topology__path.is-stable,
.device-topology__path.is-recovering,
.device-topology__path.is-degraded,
.device-topology__path.is-severe {
  stroke-dasharray: 14 8;
  animation: flow 5s linear infinite;
}

.device-topology__path.is-lost {
  stroke-dasharray: 8 12;
  opacity: 0.45;
}

.device-topology__path--energy.is-lost {
  display: none;
}

.device-topology__path--energy.is-degraded {
  stroke-dasharray: 24 210;
  animation-duration: 4.6s;
}

.device-topology__path--energy.is-severe {
  stroke-dasharray: 18 220;
  animation-duration: 6s;
}

.device-topology__path--energy.is-recovering {
  stroke-dasharray: 28 188;
  animation-duration: 3.6s;
}

.device-topology__particle {
  fill: #49f2a8;
  filter: drop-shadow(0 0 10px rgba(73, 242, 168, 0.9));
}

.device-topology__particle--trail {
  fill: rgba(173, 255, 214, 0.88);
  filter: drop-shadow(0 0 8px rgba(125, 255, 195, 0.85));
}

.device-topology__particle.is-degraded {
  fill: #c8ef6c;
}

.device-topology__particle.is-severe {
  fill: #ffb347;
}

.device-topology__particle.is-recovering {
  fill: #63d3ff;
}

.device-topology__power-label {
  pointer-events: none;
}

.device-topology__power-label rect {
  fill: rgba(5, 31, 110, 0.78);
  stroke: rgba(92, 255, 221, 0.38);
  stroke-width: 1;
}

.device-topology__power-label text {
  fill: #62ffb6;
  font-size: 11px;
  font-weight: 600;
  text-shadow: 0 0 10px rgba(98, 255, 182, 0.32);
}

.device-topology__parent {
  position: relative;
  z-index: 1;
  width: 180px;
  margin: 0 auto;
  padding: 24px 16px 22px;
  border: 2px solid rgba(66, 232, 255, 0.82);
  border-radius: 999px;
  text-align: center;
  background: radial-gradient(circle at top, rgba(41, 162, 255, 0.3), rgba(6, 24, 98, 0.92) 70%);
  box-shadow: 0 0 28px rgba(66, 232, 255, 0.26), inset 0 0 18px rgba(66, 232, 255, 0.18);
}

.device-topology__icon {
  font-size: 34px;
  color: #f7eb89;
}

.device-topology__parent strong,
.device-topology__parent span {
  display: block;
}

.device-topology__parent strong {
  margin-top: 8px;
  font-size: 18px;
}

.device-topology__parent span {
  margin-top: 8px;
  color: #8ce9ff;
}

.device-node {
  position: absolute;
  bottom: 8px;
  z-index: 1;
  width: 124px;
  transform: translateX(-50%);
  padding: 16px 12px 12px;
  border: 1px solid rgba(72, 224, 255, 0.42);
  border-radius: 26px;
  background: radial-gradient(circle at top, rgba(16, 86, 209, 0.38), rgba(4, 27, 103, 0.94) 74%);
  box-shadow: inset 0 0 18px rgba(66, 232, 255, 0.12), 0 0 18px rgba(19, 106, 255, 0.12);
  color: #e8f7ff;
  text-align: center;
  cursor: pointer;
}

.device-node.is-selected {
  border-color: rgba(255, 213, 76, 0.92);
  box-shadow: 0 0 0 2px rgba(255, 213, 76, 0.22), inset 0 0 18px rgba(66, 232, 255, 0.18);
}

.device-node.is-offline {
  opacity: 0.72;
}

.device-node__icon {
  display: block;
  font-size: 28px;
  color: #e8f7ff;
}

.device-node strong,
.device-node small,
.device-node__status {
  display: block;
}

.device-node strong {
  margin-top: 8px;
  font-size: 14px;
}

.device-node__status {
  margin-top: 8px;
  color: #7ad8ff;
}

.device-node__status i {
  display: inline-block;
  width: 10px;
  height: 10px;
  margin-right: 6px;
  border-radius: 50%;
  vertical-align: middle;
}

.device-node small {
  margin-top: 8px;
  color: #d8f3ff;
  line-height: 1.5;
}

.device-topology__empty {
  padding: 48px 16px;
  color: #8fcfff;
}

@keyframes flow {
  from {
    stroke-dashoffset: 120;
  }

  to {
    stroke-dashoffset: 0;
  }
}

@keyframes energy-flow {
  from {
    stroke-dashoffset: 0;
  }

  to {
    stroke-dashoffset: 230;
  }
}

@media (max-width: 1024px) {
  .device-topology__canvas {
    min-height: auto;
    display: grid;
    gap: 14px;
  }

  .device-topology__links {
    display: none;
  }

  .device-topology__parent {
    width: 100%;
    max-width: none;
    border-radius: 24px;
  }

  .device-node {
    position: static;
    width: 100%;
    transform: none;
  }
}

@media (prefers-reduced-motion: reduce) {
  .device-topology__path,
  .device-topology__particle {
    animation: none;
  }

  .device-topology__path--energy,
  .device-topology__particle {
    display: none;
  }
}
</style>
