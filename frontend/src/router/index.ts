import { createRouter, createWebHistory } from 'vue-router'

import OverviewDashboardView from '@/views/OverviewDashboardView.vue'
import ExperimentResultsView from '@/views/ExperimentResultsView.vue'
import FaultLabView from '@/views/FaultLabView.vue'
import MultiAgentDispatchCenterView from '@/views/MultiAgentDispatchCenterView.vue'
import MarketPricingView from '@/views/MarketPricingView.vue'
import StationDetailView from '@/views/StationDetailView.vue'
import StationOverviewView from '@/views/StationOverviewView.vue'
import SystemSettingsView from '@/views/SystemSettingsView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      name: 'overview',
      component: OverviewDashboardView,
      meta: {
        title: '总览驾驶舱'
      }
    },
    {
      path: '/stations',
      name: 'stations',
      component: StationOverviewView,
      meta: {
        title: '场站实时监控',
        overview:
          '聚焦单场站实时遥测、设备态势、边缘控制回执与本地自治策略窗口，为后续接入 MQTT 遥测与设备故障诊断提供承载面。',
        modules: ['场站拓扑概览', '设备状态矩阵', '实时功率与SOC曲线', '本地控制日志']
      }
    },
    {
      path: '/stations/:stationId',
      name: 'station-detail',
      component: StationDetailView,
      meta: {
        title: '资源专题监控'
      }
    },
    {
      path: '/links',
      redirect: '/'
    },
    {
      path: '/dispatch',
      redirect: '/'
    },
    {
      path: '/agent-dispatch',
      name: 'agent-dispatch',
      component: MultiAgentDispatchCenterView,
      meta: {
        title: '多智能体调度中心'
      }
    },
    {
      path: '/market-pricing',
      name: 'market-pricing',
      component: MarketPricingView,
      meta: {
        title: '市场与电价'
      }
    },
    {
      path: '/fault-lab',
      name: 'fault-lab',
      component: FaultLabView,
      meta: {
        title: '实时注入'
      }
    },
    {
      path: '/results',
      name: 'results',
      component: ExperimentResultsView,
      meta: {
        title: '实验结果对比'
      }
    },
    {
      path: '/events',
      redirect: '/'
    },
    {
      path: '/settings',
      name: 'settings',
      component: SystemSettingsView,
      meta: {
        title: '系统配置'
      }
    }
  ]
})

export default router
