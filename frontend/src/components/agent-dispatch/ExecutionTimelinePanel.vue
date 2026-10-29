<script setup lang="ts">
import { formatDateTime, timelineStatusLabelMap } from '@/components/agent-dispatch/agentDispatchMeta'
import type { ExecutionTimeline, ExecutionTimelineStep } from '@/types/system'

defineProps<{
  timeline: ExecutionTimeline
}>()

function stepClass(step: ExecutionTimelineStep): string {
  return `is-${step.status.toLowerCase()}`
}

function stepIcon(step: ExecutionTimelineStep): string {
  if (step.stepCode === 'FAULT_OCCURRED') {
    return '故'
  }
  if (step.stepCode === 'AGENT_DIAGNOSIS') {
    return '诊'
  }
  if (step.stepCode === 'SAFETY_VALIDATION') {
    return '安'
  }
  if (step.stepCode === 'COMMAND_DISPATCHED') {
    return '发'
  }
  if (step.stepCode === 'RESOURCE_EXECUTION') {
    return '执'
  }
  if (step.stepCode === 'RECOVERY_SYNC') {
    return '链'
  }
  return '果'
}
</script>

<template>
  <section class="timeline-panel">
    <header class="timeline-panel__header">
      <div>
        <p>执行时间线</p>
        <h3>本次实验闭环进度</h3>
      </div>
      <div class="timeline-panel__run">
        <span>Run ID</span>
        <strong>{{ timeline.runId ?? '--' }}</strong>
      </div>
    </header>

    <div class="timeline-panel__track">
      <article v-for="step in timeline.steps" :key="step.stepCode" :class="stepClass(step)">
        <time>{{ formatDateTime(step.timestamp) }}</time>
        <div class="timeline-panel__card">
          <div class="timeline-panel__icon">{{ stepIcon(step) }}</div>
          <div class="timeline-panel__copy">
            <strong>{{ step.stepLabel }}</strong>
            <span>{{ timelineStatusLabelMap[step.status] }}</span>
            <p>{{ step.description }}</p>
          </div>
        </div>
      </article>
    </div>
  </section>
</template>

<style scoped>
.timeline-panel {
  display: grid;
  gap: 8px;
}

.timeline-panel__header,
.timeline-panel__track article {
  border: 1px solid rgba(72, 224, 255, 0.24);
  border-radius: 8px;
  background: linear-gradient(180deg, rgba(6, 28, 104, 0.94), rgba(3, 16, 62, 0.98));
}

.timeline-panel__header {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  padding: 9px 12px;
}

.timeline-panel__header p,
.timeline-panel__header h3 {
  margin: 0;
}

.timeline-panel__header p,
.timeline-panel__run span {
  color: #92dbff;
  font-size: 12px;
}

.timeline-panel__header h3 {
  margin-top: 4px;
  font-size: 18px;
}

.timeline-panel__run {
  display: grid;
  align-content: center;
  justify-items: end;
}

.timeline-panel__run strong {
  margin-top: 6px;
  max-width: 200px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.timeline-panel__track {
  display: grid;
  grid-template-columns: repeat(7, minmax(0, 1fr));
  gap: 8px;
}

.timeline-panel__track article {
  display: grid;
  gap: 5px;
  padding: 8px 8px 9px;
  min-height: 98px;
  position: relative;
}

.timeline-panel__track article:not(:last-child)::after {
  content: '➜';
  position: absolute;
  top: 50%;
  right: -16px;
  transform: translateY(-50%);
  color: #51d7ff;
  font-size: 20px;
}

.timeline-panel__track time,
.timeline-panel__track span,
.timeline-panel__track p {
  color: #9ad9ff;
}

.timeline-panel__card {
  display: grid;
  grid-template-columns: 46px 1fr;
  align-items: center;
  gap: 10px;
}

.timeline-panel__icon {
  display: grid;
  place-items: center;
  width: 46px;
  height: 46px;
  border-radius: 10px;
  border: 1px solid rgba(72, 224, 255, 0.24);
  background: linear-gradient(180deg, rgba(8, 44, 152, 0.7), rgba(4, 18, 66, 0.96));
  color: #8ee9ff;
  font-size: 20px;
}

.timeline-panel__copy {
  display: grid;
  gap: 4px;
}

.timeline-panel__track strong {
  font-size: 15px;
}

.timeline-panel__track article.is-succeeded {
  border-color: rgba(98, 255, 182, 0.5);
}

.timeline-panel__track article.is-running {
  border-color: rgba(255, 176, 85, 0.82);
}

.timeline-panel__track article.is-failed {
  border-color: rgba(239, 100, 87, 0.82);
}

.timeline-panel__track article.is-pending,
.timeline-panel__track article.is-skipped {
  border-color: rgba(72, 224, 255, 0.18);
}

@media (max-width: 1380px) {
  .timeline-panel__track {
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }
}

@media (max-width: 960px) {
  .timeline-panel__header {
    flex-direction: column;
    align-items: stretch;
  }

  .timeline-panel__run {
    justify-items: start;
  }

  .timeline-panel__track {
    grid-template-columns: 1fr 1fr;
  }
}

@media (max-width: 640px) {
  .timeline-panel__track {
    grid-template-columns: 1fr;
  }
}
</style>
