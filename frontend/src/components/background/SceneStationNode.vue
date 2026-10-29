<script setup lang="ts">
import type { StationState } from '@/types/system'

defineProps<{
  station: StationState
  selected: boolean
  stateColor: string
  resourceLabel: string
}>()

const emit = defineEmits<{
  enter: [station: StationState]
  leave: []
  select: [stationId: string]
}>()

function onKeyboardSelect(stationId: string): void {
  emit('select', stationId)
}
</script>

<template>
  <g
    class="scene-station-node"
    :class="{ 'is-selected': selected }"
    :transform="`translate(${station.position.x * 10}, ${station.position.y * 5.6})`"
    tabindex="0"
    role="button"
    :aria-label="`${station.stationName}，${resourceLabel}`"
    focusable="true"
    @mouseenter="emit('enter', station)"
    @mouseleave="emit('leave')"
    @focus="emit('enter', station)"
    @blur="emit('leave')"
    @click="emit('select', station.stationId)"
    @keydown.enter.prevent="onKeyboardSelect(station.stationId)"
    @keydown.space.prevent="onKeyboardSelect(station.stationId)"
  >
    <circle
      class="scene-station-node__halo"
      r="26"
      :fill="stateColor"
      opacity="0.08"
    />
    <circle
      class="scene-station-node__core"
      r="12"
      :fill="stateColor"
    />
    <rect
      x="-44"
      y="18"
      width="88"
      height="26"
      rx="8"
      fill="rgba(12, 18, 29, 0.76)"
      stroke="rgba(222, 198, 150, 0.18)"
    />
    <text
      x="0"
      y="35"
      text-anchor="middle"
      fill="#f7f4eb"
      font-size="12"
    >
      {{ station.stationName }}
    </text>
  </g>
</template>

<style scoped>
.scene-station-node {
  cursor: pointer;
  outline: none;
}

.scene-station-node__halo {
  animation: pulse 3s ease-in-out infinite;
  transform-origin: center;
}

.scene-station-node.is-selected .scene-station-node__core {
  stroke: #f7f4eb;
  stroke-width: 2;
}

.scene-station-node:focus-visible .scene-station-node__core {
  stroke: #f5d76e;
  stroke-width: 3;
}

@keyframes pulse {
  0%,
  100% {
    transform: scale(0.94);
    opacity: 0.12;
  }

  50% {
    transform: scale(1.08);
    opacity: 0.24;
  }
}
</style>
