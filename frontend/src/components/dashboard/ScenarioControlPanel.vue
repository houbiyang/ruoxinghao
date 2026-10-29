<script setup lang="ts">
import { computed } from 'vue'

import type { DemoScenario, ScenarioProfile } from '@/types/system'

const props = defineProps<{
  activeScenario: DemoScenario
  profiles: ScenarioProfile[]
}>()

const emit = defineEmits<{
  change: [scenario: DemoScenario]
}>()

const scenarioButtons = computed(() => props.profiles)
</script>

<template>
  <div class="scenario-panel">
    <div class="scenario-panel__actions">
      <button
        v-for="profile in scenarioButtons"
        :key="profile.scenario"
        type="button"
        class="scenario-panel__button"
        :class="{ 'is-active': profile.scenario === activeScenario }"
        @click="emit('change', profile.scenario)"
      >
        {{ profile.label }}
      </button>
    </div>
    <p class="scenario-panel__description">
      {{ scenarioButtons.find((profile) => profile.scenario === activeScenario)?.description }}
    </p>
  </div>
</template>

<style scoped>
.scenario-panel {
  display: grid;
  gap: 12px;
}

.scenario-panel__actions {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
}

.scenario-panel__button {
  padding: 12px;
  border: 1px solid rgba(222, 198, 150, 0.16);
  border-radius: 16px 8px 16px 8px;
  color: var(--color-text-300);
  background: rgba(20, 27, 38, 0.8);
  cursor: pointer;
}

.scenario-panel__button.is-active {
  color: var(--color-text-100);
  border-color: rgba(90, 167, 255, 0.42);
  background: linear-gradient(135deg, rgba(90, 167, 255, 0.16), rgba(53, 214, 166, 0.12));
}

.scenario-panel__description {
  margin: 0;
  color: var(--color-text-300);
  line-height: 1.7;
  font-size: 13px;
}

@media (max-width: 768px) {
  .scenario-panel__actions {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
