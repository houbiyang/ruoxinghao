<script setup lang="ts">
import { computed } from 'vue'

import { formatDuration } from '@/components/agent-dispatch/agentDispatchMeta'
import { linkStateMetaMap } from '@/mock/systemData'
import type { AgentDispatchExperimentStatus, ExperimentScenario } from '@/types/system'

const props = defineProps<{
  scenarios: ExperimentScenario[]
  experimentStatus: AgentDispatchExperimentStatus
  busy: boolean
}>()

const emit = defineEmits<{
  start: [scenarioCode: string]
  stop: []
}>()

const keyScenarios = computed(() =>
  props.scenarios.filter((scenario) =>
    ['T1_LATENCY_JITTER', 'T2_RANDOM_PACKET_LOSS', 'T5_FULL_LOSS_AUTONOMY', 'T9_RECOVERY_SYNC'].includes(scenario.scenarioCode)
  )
)

function handleStart(scenario: ExperimentScenario): void {
  if (props.experimentStatus.running) {
    return
  }
  const confirmed = window.confirm(`确认启动 ${scenario.scenarioName} 吗？`)
  if (confirmed) {
    emit('start', scenario.scenarioCode)
  }
}
</script>

<template>
  <section class="fault-panel">
    <header class="fault-panel__header">
      <div>
        <p>故障注入实验台</p>
        <h3>故障注入实验台</h3>
      </div>
      <div class="fault-panel__status">
        <span>当前实验状态</span>
        <strong>{{ experimentStatus.currentScenarioName ?? '未运行' }}</strong>
      </div>
    </header>

    <div class="fault-panel__summary">
      <span>当前实验状态：<strong>{{ experimentStatus.currentScenarioName ?? '未运行' }}</strong></span>
      <span>已运行：{{ formatDuration(experimentStatus.elapsedSeconds) }}</span>
      <span>影响场站：{{ experimentStatus.affectedStationIds.join('、') || '--' }}</span>
    </div>

    <div class="fault-panel__content">
      <div class="fault-panel__scenarios">
        <article v-for="scenario in keyScenarios" :key="scenario.scenarioCode" :class="{ 'is-running': experimentStatus.currentScenarioCode === scenario.scenarioCode && experimentStatus.running }">
          <div class="fault-panel__scenario-title">
            <strong>{{ scenario.scenarioCode.replace('_', ' ') }}</strong>
            <span>{{ scenario.scenarioName }}</span>
          </div>
          <p>{{ scenario.description }}</p>
          <small>
            目标状态：{{ linkStateMetaMap[scenario.expectedLinkState].label }} / 默认时长：{{ scenario.durationSeconds }} 秒
          </small>
          <button type="button" :disabled="busy || experimentStatus.running" @click="handleStart(scenario)">
            {{ busy ? '处理中' : '启动' }}
          </button>
        </article>
      </div>

      <aside class="fault-panel__runtime">
        <strong>运行区</strong>
        <span>状态：{{ experimentStatus.running ? '运行中' : '空闲' }}</span>
        <span>已运行：{{ formatDuration(experimentStatus.elapsedSeconds) }}</span>
        <span>影响场站：{{ experimentStatus.affectedStationIds.join('、') || '--' }}</span>
        <span>目标链路：{{ experimentStatus.expectedLinkState ? linkStateMetaMap[experimentStatus.expectedLinkState].label : '--' }}</span>
        <button type="button" :disabled="busy || !experimentStatus.running" @click="emit('stop')">
          {{ busy ? '处理中' : '停止实验' }}
        </button>
      </aside>
    </div>
  </section>
</template>

<style scoped>
.fault-panel {
  display: grid;
  gap: 8px;
}

.fault-panel__header,
.fault-panel__summary,
.fault-panel__scenarios article,
.fault-panel__runtime {
  border: 1px solid rgba(72, 224, 255, 0.24);
  border-radius: 8px;
  background: linear-gradient(180deg, rgba(6, 28, 104, 0.94), rgba(3, 16, 62, 0.98));
}

.fault-panel__header {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  padding: 9px 12px;
}

.fault-panel__header p,
.fault-panel__header h3 {
  margin: 0;
}

.fault-panel__header p,
.fault-panel__status span {
  color: #92dbff;
  font-size: 12px;
}

.fault-panel__header h3 {
  margin-top: 4px;
  font-size: 18px;
}

.fault-panel__status {
  display: grid;
  align-content: center;
  justify-items: end;
}

.fault-panel__status strong {
  margin-top: 4px;
  color: #ffae57;
  font-size: 15px;
}

.fault-panel__content {
  display: grid;
  grid-template-columns: 1.6fr 0.9fr;
  gap: 8px;
}

.fault-panel__summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 7px 10px;
  color: #9cdcff;
  font-size: 12px;
}

.fault-panel__summary strong {
  color: #ffb349;
}

.fault-panel__scenarios {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px;
}

.fault-panel__scenarios article,
.fault-panel__runtime {
  display: grid;
  gap: 7px;
  padding: 10px;
}

.fault-panel__scenarios article.is-running {
  border-color: rgba(239, 100, 87, 0.9);
  box-shadow: inset 0 0 20px rgba(239, 100, 87, 0.14);
}

.fault-panel__scenario-title {
  display: grid;
  gap: 4px;
}

.fault-panel__scenario-title strong {
  color: #76ff79;
  font-size: 18px;
}

.fault-panel__scenario-title span,
.fault-panel__scenarios p,
.fault-panel__scenarios small,
.fault-panel__runtime span {
  color: #9bdcff;
}

.fault-panel__scenarios p {
  margin: 0;
  min-height: 56px;
  line-height: 1.35;
  font-size: 12px;
}

.fault-panel__scenarios button,
.fault-panel__runtime button {
  padding: 8px 12px;
  border-radius: 5px;
  border: 1px solid rgba(72, 224, 255, 0.26);
  background: rgba(7, 37, 130, 0.86);
  color: #f3fbff;
}

.fault-panel__runtime strong {
  color: #effaff;
  font-size: 16px;
}

@media (max-width: 1380px) {
  .fault-panel__content {
    grid-template-columns: 1fr;
  }

  .fault-panel__scenarios {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 760px) {
  .fault-panel__header {
    flex-direction: column;
    align-items: stretch;
  }

  .fault-panel__status {
    justify-items: start;
  }

  .fault-panel__scenarios {
    grid-template-columns: 1fr;
  }
}
</style>
