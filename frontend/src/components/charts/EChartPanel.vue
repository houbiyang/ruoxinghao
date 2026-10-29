<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts'
import type { EChartsOption } from 'echarts'

const props = defineProps<{
  option: EChartsOption
  height?: string
  preserveDataZoom?: boolean
}>()

const chartContainer = ref<HTMLElement>()
let chartInstance: echarts.ECharts | undefined
let resizeObserver: ResizeObserver | undefined

interface DataZoomRange {
  start: number
  end: number
}

function readDataZoomRanges(): DataZoomRange[] {
  if (!chartInstance || !props.preserveDataZoom) {
    return []
  }
  const currentOption = chartInstance.getOption() as { dataZoom?: Array<{ start?: number; end?: number }> }
  return (currentOption.dataZoom ?? []).flatMap((dataZoom) => (
    typeof dataZoom.start === 'number' && typeof dataZoom.end === 'number'
      ? [{ start: dataZoom.start, end: dataZoom.end }]
      : []
  ))
}

function renderChart(): void {
  if (!chartContainer.value) {
    return
  }

  const dataZoomRanges = readDataZoomRanges()
  if (!chartInstance) {
    chartInstance = echarts.init(chartContainer.value, undefined, { renderer: 'canvas' })
  }

  chartInstance.setOption(props.option, true)
  dataZoomRanges.forEach((range, dataZoomIndex) => {
    chartInstance?.dispatchAction({
      type: 'dataZoom',
      dataZoomIndex,
      start: range.start,
      end: range.end
    }, { silent: true })
  })
  chartInstance.resize()
}

onMounted(() => {
  renderChart()

  if (chartContainer.value) {
    resizeObserver = new ResizeObserver(() => {
      chartInstance?.resize()
    })
    resizeObserver.observe(chartContainer.value)
  }

  window.addEventListener('resize', renderChart)
})

watch(
  () => props.option,
  () => {
    renderChart()
  },
  { deep: true }
)

onBeforeUnmount(() => {
  window.removeEventListener('resize', renderChart)
  resizeObserver?.disconnect()
  chartInstance?.dispose()
  chartInstance = undefined
})
</script>

<template>
  <div
    ref="chartContainer"
    :style="{ height: height ?? '280px', width: '100%' }"
    aria-hidden="true"
  />
</template>
