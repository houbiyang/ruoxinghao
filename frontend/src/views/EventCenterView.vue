<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'

import { fetchLatestEvents, fetchLatestFaultDiagnosisRuns } from '@/api/systemApi'
import type { AlarmEvent, FaultDiagnosisRun } from '@/types/system'

const events = ref<AlarmEvent[]>([])
const diagnosisRuns = ref<FaultDiagnosisRun[]>([])
const selectedSeverity = ref('ALL')
const isLoading = ref(false)
const errorMessage = ref('')
let refreshTimer: number | undefined

const filteredEvents = computed(() =>
  events.value.filter((event) => selectedSeverity.value === 'ALL' || event.level === selectedSeverity.value)
)

async function loadData(): Promise<void> {
  isLoading.value = true
  errorMessage.value = ''
  try {
    const [eventPayload, diagnosisPayload] = await Promise.all([
      fetchLatestEvents(20),
      fetchLatestFaultDiagnosisRuns(20)
    ])
    events.value = eventPayload
    diagnosisRuns.value = diagnosisPayload
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '事件中心数据加载失败'
  } finally {
    isLoading.value = false
  }
}

onMounted(() => {
  void loadData()
  refreshTimer = window.setInterval(() => void loadData(), 5000)
})

onBeforeUnmount(() => {
  if (refreshTimer !== undefined) window.clearInterval(refreshTimer)
})
</script>

<template>
  <section class="event-center page-shell">
    <header>
      <div>
        <span>告警与事件中心</span>
        <h2>故障事件与多智能体审计轨迹</h2>
        <p>统一展示场站故障、规则判定、自动裁决状态和处置结果，不展示模型原始思维过程。</p>
      </div>
      <div class="event-center__tools">
        <strong>实时数据</strong>
        <select v-model="selectedSeverity">
          <option value="ALL">全部等级</option>
          <option value="提示">提示</option>
          <option value="预警">预警</option>
          <option value="严重">严重</option>
        </select>
        <button type="button" :disabled="isLoading" @click="loadData">{{ isLoading ? '刷新中' : '刷新' }}</button>
      </div>
    </header>

    <p v-if="errorMessage" class="event-center__error">{{ errorMessage }}</p>

    <div class="event-center__grid">
      <section>
        <h3>场站事件</h3>
        <article v-for="event in filteredEvents" :key="event.eventId">
          <div>
            <strong>{{ event.title }}</strong>
            <span :class="`is-${event.level}`">{{ event.level }}</span>
          </div>
          <p>{{ event.description }}</p>
          <small>{{ event.relatedStationId ?? '系统级' }} / {{ event.timestamp }}</small>
        </article>
        <div v-if="filteredEvents.length === 0" class="event-center__empty">当前筛选条件下没有事件</div>
      </section>

      <section>
        <h3>诊断与处置审计</h3>
        <article v-for="run in diagnosisRuns" :key="run.runId">
          <div>
            <strong>{{ run.ruleDecision.faultCode }} · {{ run.ruleDecision.faultType }}</strong>
            <span>{{ run.status }}</span>
          </div>
          <p>{{ run.safetyDecision.explanation }}</p>
          <small>
            {{ run.stationId }} / {{ run.approvalStatus }} /
            {{ run.fallbackUsed ? '规则回退' : '结构化协商' }}
          </small>
          <div class="event-center__actions">
            <i v-for="action in run.actions" :key="action.actionId">{{ action.actionType }}：{{ action.status }}</i>
          </div>
        </article>
        <div v-if="diagnosisRuns.length === 0" class="event-center__empty">尚无多智能体诊断记录</div>
      </section>
    </div>
  </section>
</template>

<style scoped>
.event-center {
  display: grid;
  gap: 16px;
}

.event-center > header,
.event-center__grid > section {
  border: 1px solid rgba(72, 224, 255, 0.25);
  border-radius: 8px;
  background: linear-gradient(180deg, rgba(8, 35, 129, 0.84), rgba(4, 23, 90, 0.92));
}

.event-center > header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  padding: 18px;
}

.event-center header span {
  color: #62ffb6;
  font-size: 12px;
}

.event-center h2,
.event-center h3,
.event-center p {
  margin: 0;
}

.event-center h2 {
  margin-top: 5px;
  font-size: 25px;
}

.event-center header p {
  margin-top: 7px;
  color: #9ed7f7;
}

.event-center__tools {
  display: flex;
  align-items: center;
  gap: 8px;
}

.event-center__tools strong {
  padding: 6px 9px;
  color: #06164f;
  background: #48e0ff;
}

.event-center select,
.event-center button {
  padding: 8px 10px;
  border: 1px solid rgba(72, 224, 255, 0.35);
  color: #fff;
  background: rgba(3, 35, 112, 0.9);
}

.event-center__error {
  padding: 10px;
  color: #ffb4aa;
  border: 1px solid rgba(239, 100, 87, 0.45);
}

.event-center__grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

.event-center__grid > section {
  padding: 15px;
}

.event-center h3 {
  padding-bottom: 10px;
  color: #48e0ff;
  border-bottom: 1px solid rgba(72, 224, 255, 0.15);
}

.event-center article {
  margin-top: 9px;
  padding: 11px;
  border-left: 3px solid rgba(72, 224, 255, 0.55);
  background: rgba(3, 28, 101, 0.55);
}

.event-center article > div:first-child {
  display: flex;
  justify-content: space-between;
  gap: 10px;
}

.event-center article p {
  margin-top: 7px;
  color: #b4e5ff;
  line-height: 1.55;
}

.event-center article small {
  display: block;
  margin-top: 7px;
  color: #72afd9;
}

.event-center__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
}

.event-center__actions i {
  padding: 4px 6px;
  color: #62ffb6;
  background: rgba(98, 255, 182, 0.08);
  font-size: 10px;
  font-style: normal;
}

.event-center__empty {
  padding: 36px;
  text-align: center;
  color: #72afd9;
}

@media (max-width: 768px) {
  .event-center > header,
  .event-center__grid {
    display: grid;
    grid-template-columns: 1fr;
  }

  .event-center__tools {
    flex-wrap: wrap;
  }
}
</style>
