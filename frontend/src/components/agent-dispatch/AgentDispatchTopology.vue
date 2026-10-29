<script setup lang="ts">
import { computed } from 'vue'

import { controlModeLabelMap, linkStateMetaMap } from '@/mock/systemData'
import type {
  AgentDispatchResourceTopology,
  DispatchTopologyControlLink,
  DispatchTopologyEnergyLink,
  DispatchTopologyNode
} from '@/types/system'

const props = defineProps<{
  topology: AgentDispatchResourceTopology
  selectedNodeId: string | null
  highlightedResourceIds: string[]
}>()

const emit = defineEmits<{
  'select-node': [nodeId: string | null]
}>()

type NodePosition = {
  x: number
  y: number
}

const nodePositionMap: Record<string, NodePosition> = {
  'agent-dispatch-engine': { x: 500, y: 224 },
  'pv-gobi-01': { x: 210, y: 84 },
  'wind-ridge-02': { x: 790, y: 84 },
  'grid-link-00': { x: 138, y: 204 },
  'load-oasis-04': { x: 862, y: 204 },
  'storage-hub-03': { x: 210, y: 340 },
  'charge-corridor-05': { x: 790, y: 340 }
}

const nodeMap = computed(() => {
  const entries: Array<[string, DispatchTopologyNode | { nodeId: string }]> = [
    [props.topology.centerNode.nodeId, { nodeId: props.topology.centerNode.nodeId }],
    ...props.topology.resourceNodes.map((node) => [node.nodeId, node] satisfies [string, DispatchTopologyNode])
  ]
  return new Map(entries)
})

const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)')

function getNodePosition(nodeId: string): NodePosition {
  return nodePositionMap[nodeId] ?? { x: 500, y: 310 }
}

function pathForLink(sourceNodeId: string, targetNodeId: string): string {
  const source = getNodePosition(sourceNodeId)
  const target = getNodePosition(targetNodeId)
  const controlOffset = source.y === target.y ? 0 : (target.x - source.x) * 0.08
  return `M ${source.x} ${source.y} C ${source.x + controlOffset} ${source.y}, ${target.x - controlOffset} ${target.y}, ${target.x} ${target.y}`
}

function midPoint(sourceNodeId: string, targetNodeId: string): NodePosition {
  const source = getNodePosition(sourceNodeId)
  const target = getNodePosition(targetNodeId)
  return {
    x: (source.x + target.x) / 2,
    y: (source.y + target.y) / 2
  }
}

function nodeClass(node: DispatchTopologyNode): Record<string, boolean> {
  const highlighted = props.highlightedResourceIds.includes(node.nodeId) || props.highlightedResourceIds.includes(node.stationId ?? '')
  return {
    'is-selected': props.selectedNodeId === node.nodeId,
    'is-highlighted': highlighted,
    'is-offline': !node.online,
    'is-local': node.linkState === 'S3'
  }
}

function linkClass(link: DispatchTopologyEnergyLink | DispatchTopologyControlLink): Record<string, boolean> {
  const source = nodeMap.value.get(link.sourceNodeId)
  const target = nodeMap.value.get(link.targetNodeId)
  const targetNode = props.topology.resourceNodes.find((node) => node.nodeId === link.targetNodeId)
  return {
    'is-active': link.active && !reducedMotion.matches,
    'is-isolated': targetNode?.linkState === 'S3' || targetNode?.controlMode === 'local-autonomy',
    'is-selected':
      props.selectedNodeId !== null &&
      (link.sourceNodeId === props.selectedNodeId || link.targetNodeId === props.selectedNodeId || source?.nodeId === props.selectedNodeId || target?.nodeId === props.selectedNodeId)
  }
}

function selectNode(node: DispatchTopologyNode): void {
  emit('select-node', props.selectedNodeId === node.nodeId ? null : node.nodeId)
}

function nodeTooltip(node: DispatchTopologyNode): string {
  const linkState = node.linkState ? linkStateMetaMap[node.linkState].label : '无状态'
  return `${node.displayName} | ${node.activePower ?? '--'} ${node.unit} | ${linkState} | ${controlModeLabelMap[node.controlMode]}`
}

function nodeIcon(nodeType: DispatchTopologyNode['nodeType']): string {
  if (nodeType === 'photovoltaic') {
    return '光'
  }
  if (nodeType === 'wind') {
    return '风'
  }
  if (nodeType === 'storage') {
    return '储'
  }
  if (nodeType === 'grid') {
    return '市'
  }
  if (nodeType === 'charging') {
    return '充'
  }
  return '负'
}

function nodeThemeClass(node: DispatchTopologyNode): string {
  if (node.nodeType === 'photovoltaic' || node.nodeType === 'wind') {
    return 'is-supply'
  }
  if (node.nodeType === 'storage') {
    return 'is-storage'
  }
  if (node.nodeType === 'charging' || node.nodeType === 'load') {
    return 'is-consume'
  }
  return 'is-grid'
}

function energyLabel(link: DispatchTopologyEnergyLink): string {
  const sourceNode = props.topology.resourceNodes.find((node) => node.nodeId === link.sourceNodeId)
  const targetNode = props.topology.resourceNodes.find((node) => node.nodeId === link.targetNodeId)
  if (sourceNode?.nodeType === 'grid') {
    return '购电'
  }
  if (sourceNode?.nodeType === 'storage') {
    return '放电'
  }
  if (targetNode?.nodeType === 'storage') {
    return '充电'
  }
  if (link.direction === 'CONSUME') {
    return '供电'
  }
  return '发电'
}

function energyToneClass(link: DispatchTopologyEnergyLink): string {
  const sourceNode = props.topology.resourceNodes.find((node) => node.nodeId === link.sourceNodeId)
  const targetNode = props.topology.resourceNodes.find((node) => node.nodeId === link.targetNodeId)
  if (sourceNode?.nodeType === 'storage' || targetNode?.nodeType === 'storage') {
    return 'is-storage'
  }
  if (sourceNode?.nodeType === 'photovoltaic' || sourceNode?.nodeType === 'wind') {
    return 'is-supply'
  }
  if (targetNode?.nodeType === 'load' || targetNode?.nodeType === 'charging') {
    return 'is-consume'
  }
  return 'is-grid'
}
</script>

<template>
  <section class="dispatch-topology">
    <header class="dispatch-topology__header">
      <div>
        <p>中心调度拓扑</p>
        <h3>{{ topology.centerNode.displayName }}</h3>
      </div>
      <div class="dispatch-topology__status">
        <span>{{ controlModeLabelMap[topology.centerNode.controlMode] }}</span>
        <strong>{{ topology.centerNode.statusText }}</strong>
      </div>
    </header>

    <div class="dispatch-topology__canvas" tabindex="0" @click="emit('select-node', null)" @keydown.escape.prevent="emit('select-node', null)">
      <svg class="dispatch-topology__svg" viewBox="0 0 1000 500" preserveAspectRatio="xMidYMin meet" aria-label="多智能体调度拓扑">
        <defs>
          <filter id="dispatch-node-glow">
            <feGaussianBlur stdDeviation="6" result="blur" />
            <feMerge>
              <feMergeNode in="blur" />
              <feMergeNode in="SourceGraphic" />
            </feMerge>
          </filter>
        </defs>

        <g class="dispatch-topology__grid">
          <circle cx="500" cy="224" r="182" />
          <circle cx="500" cy="224" r="138" />
          <circle cx="500" cy="224" r="94" />
        </g>

        <g class="dispatch-topology__links">
          <g
            v-for="link in topology.energyLinks"
            :key="link.linkId"
            class="dispatch-topology__energy-group"
            :class="[linkClass(link), energyToneClass(link)]"
          >
            <path :d="pathForLink(link.sourceNodeId, link.targetNodeId)" class="dispatch-topology__energy-line" />
            <text :x="midPoint(link.sourceNodeId, link.targetNodeId).x" :y="midPoint(link.sourceNodeId, link.targetNodeId).y - 10" class="dispatch-topology__link-label">
              {{ energyLabel(link) }}
            </text>
            <text :x="midPoint(link.sourceNodeId, link.targetNodeId).x" :y="midPoint(link.sourceNodeId, link.targetNodeId).y + 18" class="dispatch-topology__link-value">
              {{ link.power === null ? '--' : `${link.power.toFixed(1)} ${link.unit}` }}
            </text>
          </g>

          <g v-for="link in topology.controlLinks" :key="link.linkId" class="dispatch-topology__control-group" :class="linkClass(link)">
            <path :d="pathForLink(link.sourceNodeId, link.targetNodeId)" class="dispatch-topology__control-line" />
          </g>
        </g>

        <g class="dispatch-topology__center" filter="url(#dispatch-node-glow)">
          <circle cx="500" cy="224" r="112" class="dispatch-topology__center-ring dispatch-topology__center-ring--halo" />
          <circle cx="500" cy="224" r="98" class="dispatch-topology__center-ring dispatch-topology__center-ring--outer" />
          <circle cx="500" cy="224" r="80" class="dispatch-topology__center-ring dispatch-topology__center-ring--inner" />
          <text x="500" y="206" text-anchor="middle" class="dispatch-topology__center-title">多智能体</text>
          <text x="500" y="240" text-anchor="middle" class="dispatch-topology__center-title">协同调度引擎</text>
          <text x="500" y="282" text-anchor="middle" class="dispatch-topology__center-icon">智控</text>
        </g>

        <g v-for="node in topology.resourceNodes" :key="node.nodeId" class="dispatch-topology__node-group">
          <foreignObject
            :x="getNodePosition(node.nodeId).x - 96"
            :y="getNodePosition(node.nodeId).y - 62"
            width="192"
            height="124"
          >
            <button
              type="button"
              class="dispatch-node"
              :class="[nodeClass(node), nodeThemeClass(node)]"
              :title="nodeTooltip(node)"
              @click.stop="selectNode(node)"
            >
              <div class="dispatch-node__icon">{{ nodeIcon(node.nodeType) }}</div>
              <div class="dispatch-node__content">
                <strong>{{ node.displayName }}</strong>
                <b>{{ node.activePower === null ? '--' : node.activePower.toFixed(1) }} {{ node.unit }}</b>
                <small><i />链路：{{ node.statusText }}</small>
              </div>
            </button>
          </foreignObject>
        </g>
      </svg>
    </div>

    <footer class="dispatch-topology__footer">
      <div class="dispatch-topology__balance">
        <span>当前能量平衡</span>
        <strong>总发电 {{ topology.balanceSummary.totalSupplyPower?.toFixed(1) ?? '--' }} {{ topology.balanceSummary.unit }}</strong>
        <strong>总用电 {{ topology.balanceSummary.totalConsumePower?.toFixed(1) ?? '--' }} {{ topology.balanceSummary.unit }}</strong>
        <strong>净功率 {{ topology.balanceSummary.netBalancePower?.toFixed(1) ?? '--' }} {{ topology.balanceSummary.unit }}</strong>
      </div>
      <div class="dispatch-topology__legend">
        <span v-for="item in topology.legend" :key="item.label">
          <i :style="{ background: item.color }" />
          {{ item.label }}
        </span>
      </div>
    </footer>
  </section>
</template>

<style scoped>
.dispatch-topology {
  display: grid;
  gap: 8px;
  height: 100%;
}

.dispatch-topology__header,
.dispatch-topology__canvas,
.dispatch-topology__footer {
  border: 1px solid rgba(72, 224, 255, 0.24);
  border-radius: 8px;
  background:
    linear-gradient(180deg, rgba(5, 24, 97, 0.96), rgba(3, 14, 59, 0.98)),
    radial-gradient(circle at center, rgba(30, 98, 255, 0.18), transparent 62%);
}

.dispatch-topology__header,
.dispatch-topology__footer {
  display: flex;
  justify-content: space-between;
  gap: 14px;
  padding: 10px 16px;
}

.dispatch-topology__header p,
.dispatch-topology__header h3 {
  margin: 0;
}

.dispatch-topology__header p {
  color: #94ddff;
  font-size: 12px;
}

.dispatch-topology__header h3 {
  margin-top: 4px;
  font-size: 22px;
}

.dispatch-topology__status {
  display: grid;
  align-content: center;
  justify-items: end;
}

.dispatch-topology__status span {
  color: #94ddff;
  font-size: 12px;
}

.dispatch-topology__status strong {
  margin-top: 4px;
  color: #62ffb6;
}

.dispatch-topology__canvas {
  min-height: 470px;
  overflow: hidden;
  background:
    radial-gradient(circle at center, rgba(52, 150, 255, 0.18), transparent 31%),
    radial-gradient(circle at center, rgba(52, 150, 255, 0.08), transparent 44%),
    linear-gradient(180deg, rgba(3, 14, 59, 0.98), rgba(2, 11, 46, 1));
}

.dispatch-topology__svg {
  width: 100%;
  height: 100%;
}

.dispatch-topology__grid circle {
  fill: none;
  stroke: rgba(58, 175, 255, 0.1);
  stroke-width: 1.5;
  stroke-dasharray: 6 8;
}

.dispatch-topology__energy-line,
.dispatch-topology__control-line {
  fill: none;
  stroke-linecap: round;
}

.dispatch-topology__energy-line {
  stroke: #4ac6ff;
  stroke-width: 4.5;
  filter: drop-shadow(0 0 8px rgba(72, 224, 255, 0.34));
}

.dispatch-topology__energy-group.is-supply .dispatch-topology__energy-line,
.dispatch-topology__energy-group.is-supply .dispatch-topology__link-label,
.dispatch-topology__energy-group.is-supply .dispatch-topology__link-value {
  stroke: #75ff77;
  fill: #75ff77;
}

.dispatch-topology__energy-group.is-storage .dispatch-topology__energy-line,
.dispatch-topology__energy-group.is-storage .dispatch-topology__link-label,
.dispatch-topology__energy-group.is-storage .dispatch-topology__link-value {
  stroke: #ffa443;
  fill: #ffa443;
}

.dispatch-topology__energy-group.is-consume .dispatch-topology__energy-line,
.dispatch-topology__energy-group.is-consume .dispatch-topology__link-label,
.dispatch-topology__energy-group.is-consume .dispatch-topology__link-value,
.dispatch-topology__energy-group.is-grid .dispatch-topology__energy-line,
.dispatch-topology__energy-group.is-grid .dispatch-topology__link-label,
.dispatch-topology__energy-group.is-grid .dispatch-topology__link-value {
  stroke: #45c6ff;
  fill: #45c6ff;
}

.dispatch-topology__control-line {
  stroke: #ffad42;
  stroke-width: 2.2;
  stroke-dasharray: 10 8;
  opacity: 0.78;
}

.dispatch-topology__energy-group.is-active .dispatch-topology__energy-line,
.dispatch-topology__control-group.is-active .dispatch-topology__control-line {
  animation: linkPulse 1.8s linear infinite;
}

.dispatch-topology__energy-group.is-selected .dispatch-topology__energy-line,
.dispatch-topology__control-group.is-selected .dispatch-topology__control-line {
  stroke-width: 6;
}

.dispatch-topology__control-group.is-isolated .dispatch-topology__control-line {
  opacity: 0.16;
  animation: none;
}

.dispatch-topology__link-label,
.dispatch-topology__link-value {
  font-size: 18px;
  font-weight: 600;
  text-anchor: middle;
}

.dispatch-topology__link-label {
  font-size: 14px;
}

.dispatch-topology__center-ring {
  fill: rgba(13, 104, 255, 0.18);
  stroke: rgba(92, 233, 255, 0.78);
}

.dispatch-topology__center-ring--halo {
  fill: rgba(12, 114, 255, 0.12);
  stroke: rgba(115, 234, 255, 0.3);
  stroke-width: 2;
}

.dispatch-topology__center-ring--outer {
  stroke-width: 8;
  filter: drop-shadow(0 0 24px rgba(73, 184, 255, 0.32));
}

.dispatch-topology__center-ring--inner {
  stroke-width: 4;
}

.dispatch-topology__center-title,
.dispatch-topology__center-icon {
  fill: #e9f8ff;
  font-size: 30px;
  font-weight: 700;
}

.dispatch-topology__center-icon {
  font-size: 24px;
  fill: #93f7ff;
  letter-spacing: 0.2em;
}

.dispatch-node {
  display: grid;
  grid-template-columns: 46px 1fr;
  gap: 10px;
  width: 100%;
  height: 100%;
  padding: 10px 12px;
  border: 1px solid rgba(72, 224, 255, 0.28);
  border-radius: 12px;
  background:
    linear-gradient(180deg, rgba(9, 44, 150, 0.78), rgba(5, 23, 83, 0.96)),
    radial-gradient(circle at top left, rgba(92, 233, 255, 0.18), transparent 55%);
  color: #effaff;
  text-align: left;
  box-shadow:
    inset 0 0 16px rgba(59, 174, 255, 0.14),
    0 0 18px rgba(22, 79, 169, 0.18);
  clip-path: polygon(10% 0, 100% 0, 100% 78%, 90% 100%, 0 100%, 0 22%);
}

.dispatch-node.is-selected,
.dispatch-node.is-highlighted {
  border-color: rgba(255, 211, 108, 0.88);
  box-shadow: inset 0 0 20px rgba(255, 211, 108, 0.16);
}

.dispatch-node.is-local {
  border-color: rgba(239, 100, 87, 0.78);
}

.dispatch-node.is-supply {
  box-shadow:
    inset 0 0 18px rgba(103, 255, 128, 0.16),
    0 0 18px rgba(32, 102, 190, 0.22);
}

.dispatch-node.is-storage {
  box-shadow:
    inset 0 0 18px rgba(255, 171, 74, 0.16),
    0 0 18px rgba(32, 102, 190, 0.22);
}

.dispatch-node__icon {
  display: grid;
  place-items: center;
  color: #f8e49a;
  font-size: 22px;
  border-radius: 10px;
  background: linear-gradient(180deg, rgba(84, 194, 255, 0.16), rgba(3, 18, 70, 0.06));
}

.dispatch-node__content {
  display: grid;
  gap: 4px;
}

.dispatch-node__content strong {
  font-size: 16px;
}

.dispatch-node__content b {
  color: #54ebff;
  font-size: 20px;
}

.dispatch-node__content small {
  color: #9cd8ff;
  font-size: 11px;
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.dispatch-node__content small i {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #63ff82;
  box-shadow: 0 0 10px rgba(99, 255, 130, 0.58);
}

.dispatch-topology__footer {
  align-items: flex-end;
  flex-direction: column;
}

.dispatch-topology__balance {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, auto));
  gap: 10px 24px;
  align-items: center;
  padding: 10px 20px;
  margin: 0 auto;
  border: 1px solid rgba(66, 207, 255, 0.18);
  border-radius: 12px;
  background: linear-gradient(180deg, rgba(7, 32, 110, 0.72), rgba(4, 18, 68, 0.88));
}

.dispatch-topology__balance span {
  grid-column: 1 / -1;
  text-align: center;
  color: #9bdcff;
  font-size: 13px;
}

.dispatch-topology__balance strong {
  color: #83ff6f;
  font-size: 16px;
  text-align: center;
}

.dispatch-topology__legend {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  width: 100%;
  gap: 16px;
  color: #9ad9ff;
}

.dispatch-topology__legend span {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.dispatch-topology__legend i {
  width: 22px;
  height: 4px;
}

@keyframes linkPulse {
  from {
    stroke-dashoffset: 0;
  }

  to {
    stroke-dashoffset: -36;
  }
}

@media (max-width: 1400px) {
  .dispatch-topology__canvas {
    min-height: 620px;
  }
}

@media (max-width: 960px) {
  .dispatch-topology__header,
  .dispatch-topology__footer {
    flex-direction: column;
    align-items: stretch;
  }

  .dispatch-topology__status {
    justify-items: start;
  }

  .dispatch-topology__legend {
    justify-content: flex-start;
  }
}
</style>
