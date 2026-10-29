<script setup lang="ts">
import { computed } from 'vue'

import { linkStateMetaMap } from '@/mock/systemData'
import type { ExperimentReplayPoint } from '@/types/system'

const props = defineProps<{
  points: ExperimentReplayPoint[]
  currentIndex: number
  isPlaying: boolean
  isLoading: boolean
  replayEnded: boolean
  disabledReason: string
}>()

const emit = defineEmits<{
  play: []
  pause: []
  reset: []
  seek: [index: number]
}>()

const currentPoint = computed(() => props.points[props.currentIndex] ?? null)
const progress = computed(() => {
  if (props.points.length === 0) {
    return 0
  }
  return Math.round((props.currentIndex / Math.max(props.points.length - 1, 1)) * 100)
})

function formatDateTime(timestamp: string): string {
  return new Date(timestamp).toLocaleString('zh-CN', { hour12: false })
}

function updateProgress(event: Event): void {
  const target = event.target as HTMLInputElement
  emit('seek', Number(target.value))
}
</script>

<template>
  <section class="link-recovery-replay">
    <header class="link-recovery-replay__header">
      <div>
        <span>弱信号恢复回放</span>
        <h3>回放链路恢复过程与指标变化</h3>
      </div>
      <p>{{ replayEnded ? '回放已到终点，可重播或拖动到任意进度。' : '播放时同步联动地图节点与趋势图。' }}</p>
    </header>

    <p v-if="disabledReason" class="link-recovery-replay__state">{{ disabledReason }}</p>

    <template v-else-if="currentPoint">
      <section class="link-recovery-replay__metrics">
        <article>
          <span>当前时间</span>
          <strong>{{ formatDateTime(currentPoint.timestamp) }}</strong>
        </article>
        <article>
          <span>链路状态</span>
          <strong :style="{ color: linkStateMetaMap[currentPoint.linkState].color }">
            {{ currentPoint.linkState }} {{ linkStateMetaMap[currentPoint.linkState].label }}
          </strong>
        </article>
        <article>
          <span>通信指标</span>
          <strong>{{ currentPoint.latency }} ms / {{ currentPoint.packetLossRate }}%</strong>
        </article>
        <article>
          <span>数据时效</span>
          <strong>{{ currentPoint.dataAge }} s / {{ currentPoint.online ? '在线' : '离线' }}</strong>
        </article>
      </section>

      <div class="link-recovery-replay__bar">
        <span :style="{ width: `${progress}%` }"></span>
      </div>

      <input
        class="link-recovery-replay__slider"
        type="range"
        min="0"
        :max="Math.max(points.length - 1, 0)"
        :value="currentIndex"
        :disabled="isLoading || points.length === 0"
        @input="updateProgress"
      />

      <div class="link-recovery-replay__actions">
        <button type="button" :disabled="isLoading || points.length === 0 || isPlaying" @click="emit('play')">播放</button>
        <button type="button" :disabled="isLoading || !isPlaying" @click="emit('pause')">暂停</button>
        <button type="button" :disabled="isLoading || points.length === 0" @click="emit('reset')">重新开始</button>
      </div>
    </template>
  </section>
</template>

<style scoped>
.link-recovery-replay {
  display: grid;
  gap: 12px;
  padding: 16px;
  border: 1px solid rgba(72, 224, 255, 0.24);
  background: linear-gradient(180deg, rgba(8, 34, 128, 0.82), rgba(4, 24, 95, 0.88));
}

.link-recovery-replay__header {
  display: flex;
  justify-content: space-between;
  gap: 14px;
  align-items: flex-start;
}

.link-recovery-replay__header h3,
.link-recovery-replay__header p {
  margin: 0;
}

.link-recovery-replay__header span {
  color: #62ffb6;
  font-size: 13px;
}

.link-recovery-replay__header p {
  max-width: 280px;
  color: #9ddfff;
  line-height: 1.6;
}

.link-recovery-replay__state {
  margin: 0;
  padding: 10px 12px;
  color: #9ddfff;
  border: 1px dashed rgba(72, 224, 255, 0.24);
  background: rgba(5, 20, 85, 0.32);
}

.link-recovery-replay__metrics {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.link-recovery-replay__metrics article {
  padding: 10px 12px;
  border: 1px solid rgba(72, 224, 255, 0.16);
  background: rgba(4, 29, 111, 0.44);
}

.link-recovery-replay__metrics span {
  display: block;
  color: #9ddfff;
  font-size: 12px;
}

.link-recovery-replay__metrics strong {
  display: block;
  margin-top: 6px;
}

.link-recovery-replay__bar {
  height: 6px;
  background: rgba(72, 224, 255, 0.14);
}

.link-recovery-replay__bar span {
  display: block;
  height: 100%;
  background: linear-gradient(90deg, #48e0ff, #62ffb6);
}

.link-recovery-replay__slider {
  width: 100%;
  accent-color: #48e0ff;
}

.link-recovery-replay__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.link-recovery-replay__actions button {
  border: 1px solid rgba(72, 224, 255, 0.34);
  color: #ffffff;
  background: linear-gradient(180deg, rgba(18, 146, 220, 0.9), rgba(6, 83, 157, 0.9));
  padding: 8px 12px;
  cursor: pointer;
}

.link-recovery-replay__actions button:disabled {
  opacity: 0.48;
  cursor: not-allowed;
}

@media (max-width: 860px) {
  .link-recovery-replay__header {
    display: grid;
  }
}

@media (max-width: 640px) {
  .link-recovery-replay__metrics {
    grid-template-columns: 1fr;
  }
}
</style>
