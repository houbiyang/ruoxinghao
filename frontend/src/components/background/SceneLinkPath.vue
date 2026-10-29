<script setup lang="ts">
import { computed } from 'vue'

import type { LinkState, ScenePosition } from '@/types/system'

const props = defineProps<{
  hubPosition: ScenePosition
  stationPosition: ScenePosition
  stateMeta: {
    color: string
    lineStyle: 'solid' | 'dashed'
    animationClass: string
  }
  linkState: LinkState
}>()

const scenePath = computed(() => {
  const startX = props.stationPosition.x * 10
  const startY = props.stationPosition.y * 5.6
  const endX = props.hubPosition.x * 10
  const endY = props.hubPosition.y * 5.6
  const midX = (startX + endX) / 2
  const controlY = Math.min(startY, endY) - 54

  return `M ${startX} ${startY} Q ${midX} ${controlY} ${endX} ${endY}`
})

const particleDuration = computed(() => {
  if (props.linkState === 'S3') {
    return '0s'
  }

  if (props.linkState === 'S2') {
    return '5.8s'
  }

  if (props.linkState === 'S1') {
    return '4.8s'
  }

  if (props.linkState === 'S4') {
    return '3.4s'
  }

  return '3s'
})
</script>

<template>
  <g class="scene-link" :class="stateMeta.animationClass">
    <path
      class="scene-link__glow"
      :d="scenePath"
      :stroke="stateMeta.color"
      fill="none"
      stroke-width="6"
      stroke-linecap="round"
      opacity="0.18"
    />
    <path
      class="scene-link__line"
      :d="scenePath"
      :stroke="stateMeta.color"
      fill="none"
      stroke-width="2"
      stroke-linecap="round"
      :stroke-dasharray="stateMeta.lineStyle === 'dashed' ? '6 8' : '1 0'"
    >
      <animate
        v-if="linkState !== 'S3'"
        attributeName="stroke-dashoffset"
        from="0"
        to="-24"
        dur="1.5s"
        repeatCount="indefinite"
      />
    </path>

    <circle
      v-if="linkState !== 'S3'"
      r="4"
      :fill="stateMeta.color"
      class="scene-link__particle"
    >
      <animateMotion
        :dur="particleDuration"
        :path="scenePath"
        repeatCount="indefinite"
      />
      <animate
        v-if="linkState === 'S1' || linkState === 'S2'"
        attributeName="opacity"
        values="0.2;1;0.4;1"
        dur="0.8s"
        repeatCount="indefinite"
      />
    </circle>
  </g>
</template>

<style scoped>
.scene-link__particle {
  filter: drop-shadow(0 0 8px currentColor);
}
</style>
