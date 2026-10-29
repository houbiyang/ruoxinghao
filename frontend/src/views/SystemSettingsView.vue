<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

import {
  fetchSystemConfiguration,
  testAiConnection,
  testMqttConnection,
  updateSystemConfiguration
} from '@/api/settingsApi'
import type {
  ConfigurationTestResult,
  SystemConfiguration,
  SystemConfigurationUpdateRequest
} from '@/types/settings'

type SettingsSection = 'overview' | 'mqtt' | 'ai' | 'simulation'

const sectionItems: Array<{ id: SettingsSection; title: string; description: string; icon: string }> = [
  { id: 'overview', title: '配置概览', description: '当前保存状态与生效方式', icon: '▦' },
  { id: 'mqtt', title: 'MQTT 通信', description: 'Broker、账号与重连策略', icon: '⌁' },
  { id: 'ai', title: 'AI 智能分析', description: 'DeepSeek API 与模型参数', icon: '✦' },
  { id: 'simulation', title: '仿真参数', description: '场站、场景与发布周期', icon: '◉' }
]

const activeSection = ref<SettingsSection>('overview')
const configuration = ref<SystemConfiguration | null>(null)
const loading = ref(true)
const saving = ref(false)
const mqttTesting = ref(false)
const aiTesting = ref(false)
const loadError = ref('')
const mqttTestResult = ref<ConfigurationTestResult | null>(null)
const aiTestResult = ref<ConfigurationTestResult | null>(null)
const showMqttPassword = ref(false)
const showAiApiKey = ref(false)
const stationIdsInput = ref('')

const form = reactive<SystemConfigurationUpdateRequest>(createEmptyForm())

const currentSection = computed(() => sectionItems.find((item) => item.id === activeSection.value) ?? sectionItems[0])
const restartRequired = computed(() => configuration.value?.pendingRestart ?? false)
const aiKeyHint = computed(() => {
  if (!configuration.value?.ai.apiKeyConfigured) return '尚未配置 API Key'
  return `${configuration.value.ai.apiKeyMasked}，留空表示保留现有密钥`
})
const mqttPasswordHint = computed(() => {
  return configuration.value?.mqtt.passwordConfigured
    ? '已配置 MQTT 密码，留空表示保留现有密码'
    : '尚未配置 MQTT 密码'
})

onMounted(loadConfiguration)

async function loadConfiguration() {
  loading.value = true
  loadError.value = ''
  try {
    applyConfiguration(await fetchSystemConfiguration())
  } catch (error) {
    loadError.value = messageOf(error, '系统配置暂时无法加载，请确认后端已启动最新版本。')
  } finally {
    loading.value = false
  }
}

function applyConfiguration(value: SystemConfiguration) {
  configuration.value = value
  form.mqtt.host = value.mqtt.host
  form.mqtt.port = value.mqtt.port
  form.mqtt.username = value.mqtt.username
  form.mqtt.password = ''
  form.mqtt.clientId = value.mqtt.clientId
  form.mqtt.reconnectDelayMillis = value.mqtt.reconnectDelayMillis
  form.ai.globallyEnabled = value.ai.globallyEnabled
  form.ai.maximumContextCharacters = value.ai.maximumContextCharacters
  form.ai.providerEnabled = value.ai.providerEnabled
  form.ai.baseUrl = value.ai.baseUrl
  form.ai.apiKey = ''
  form.ai.model = value.ai.model
  form.ai.timeoutSeconds = value.ai.timeoutSeconds
  form.simulation.intervalMillis = value.simulation.intervalMillis
  form.simulation.randomSeed = value.simulation.randomSeed
  form.simulation.initialScenario = value.simulation.initialScenario
  form.simulation.stationIds = [...value.simulation.stationIds]
  stationIdsInput.value = value.simulation.stationIds.join(', ')
  mqttTestResult.value = null
  aiTestResult.value = null
}

async function saveConfiguration() {
  if (!configuration.value?.writable) {
    ElMessage.error('当前 .env 文件不可写，无法保存配置。')
    return
  }

  try {
    await ElMessageBox.confirm(
      '将保存 MQTT、AI 和模拟器配置到项目根目录 .env。后端和模拟器需重启后才会使用新值，密钥不会回传到页面。',
      '确认保存系统配置',
      {
        confirmButtonText: '保存到 .env',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
  } catch {
    return
  }

  saving.value = true
  try {
    applyConfiguration(await updateSystemConfiguration(buildRequest()))
    ElMessage.success('配置已保存到 .env，重启后端和模拟器后生效。')
  } catch (error) {
    ElMessage.error(messageOf(error, '保存系统配置失败。'))
  } finally {
    saving.value = false
  }
}

async function verifyMqttConnection() {
  mqttTesting.value = true
  mqttTestResult.value = null
  try {
    mqttTestResult.value = await testMqttConnection(buildRequest().mqtt)
    ElMessage[mqttTestResult.value.success ? 'success' : 'error'](mqttTestResult.value.message)
  } catch (error) {
    ElMessage.error(messageOf(error, 'MQTT 测试失败。'))
  } finally {
    mqttTesting.value = false
  }
}

async function verifyAiConnection() {
  aiTesting.value = true
  aiTestResult.value = null
  try {
    aiTestResult.value = await testAiConnection(buildRequest().ai)
    ElMessage[aiTestResult.value.success ? 'success' : 'error'](aiTestResult.value.message)
  } catch (error) {
    ElMessage.error(messageOf(error, 'AI Provider 测试失败。'))
  } finally {
    aiTesting.value = false
  }
}

function buildRequest(): SystemConfigurationUpdateRequest {
  return {
    mqtt: { ...form.mqtt },
    ai: { ...form.ai },
    simulation: {
      ...form.simulation,
      stationIds: stationIdsInput.value
        .split(',')
        .map((stationId) => stationId.trim())
        .filter((stationId) => stationId !== '')
    }
  }
}

function selectSection(section: SettingsSection) {
  activeSection.value = section
}

function formatDateTime(value: string | null): string {
  if (!value) return '暂无记录'
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false
  }).format(new Date(value))
}

function messageOf(error: unknown, fallback: string): string {
  return error instanceof Error && error.message.trim() !== '' ? error.message : fallback
}

function createEmptyForm(): SystemConfigurationUpdateRequest {
  return {
    mqtt: {
      host: '127.0.0.1',
      port: 1883,
      username: 'vpp_client',
      password: '',
      clientId: 'vpp-backend-core',
      reconnectDelayMillis: 3000
    },
    ai: {
      globallyEnabled: false,
      maximumContextCharacters: 6000,
      providerEnabled: false,
      baseUrl: 'https://api.deepseek.com',
      apiKey: '',
      model: 'deepseek-v4-flash',
      timeoutSeconds: 30
    },
    simulation: {
      intervalMillis: 1000,
      randomSeed: 20260629,
      initialScenario: 'stable',
      stationIds: []
    }
  }
}
</script>

<template>
  <section class="settings-screen page-shell" aria-label="系统配置">
    <header class="settings-hero">
      <div class="settings-hero__title">
        <span class="settings-hero__icon">⚙</span>
        <div>
          <p>运行与接入管理</p>
          <h1>系统配置中心</h1>
          <span>统一管理 MQTT 通信、AI 智能分析和场站仿真参数</span>
        </div>
      </div>
      <div class="settings-hero__actions">
        <button class="outline-button" type="button" :disabled="loading" @click="loadConfiguration">刷新配置</button>
        <button class="primary-button" type="button" :disabled="loading || saving" @click="saveConfiguration">
          {{ saving ? '正在保存…' : '保存到 .env' }}
        </button>
      </div>
    </header>

    <div v-if="loadError" class="settings-error" role="alert">
      <div>
        <strong>无法读取系统配置</strong>
        <span>{{ loadError }}</span>
      </div>
      <button class="outline-button" type="button" @click="loadConfiguration">重新加载</button>
    </div>

    <template v-else>
      <section class="settings-status-grid" aria-label="配置状态">
        <article class="settings-status-card">
          <span class="settings-status-card__label">配置来源</span>
          <strong>{{ configuration?.sourceName ?? '正在读取' }}</strong>
          <small>{{ configuration?.writable ? '文件可写，可通过本页保存' : '文件不可写' }}</small>
        </article>
        <article class="settings-status-card">
          <span class="settings-status-card__label">MQTT 通信</span>
          <strong>{{ configuration?.mqtt.host ?? '—' }}:{{ configuration?.mqtt.port ?? '—' }}</strong>
          <small>{{ configuration?.mqtt.passwordConfigured ? '账号密码已配置' : '尚未配置访问密码' }}</small>
        </article>
        <article class="settings-status-card">
          <span class="settings-status-card__label">AI 智能分析</span>
          <strong :class="configuration?.ai.globallyEnabled && configuration?.ai.providerEnabled ? 'is-teal' : 'is-dim'">
            {{ configuration?.ai.globallyEnabled && configuration?.ai.providerEnabled ? '已启用' : '规则模式' }}
          </strong>
          <small>{{ configuration?.ai.model ?? '未配置模型' }}</small>
        </article>
        <article class="settings-status-card" :class="{ 'is-warning': restartRequired }">
          <span class="settings-status-card__label">当前生效状态</span>
          <strong :class="restartRequired ? 'is-yellow' : 'is-teal'">{{ restartRequired ? '等待重启' : '已与 .env 一致' }}</strong>
          <small>{{ restartRequired ? '请重启后端与模拟器' : '当前进程正在使用保存配置' }}</small>
        </article>
      </section>

      <section class="settings-workspace">
        <aside class="settings-section-nav" aria-label="配置模块">
          <button
            v-for="item in sectionItems"
            :key="item.id"
            class="settings-section-nav__item"
            :class="{ 'is-active': activeSection === item.id }"
            type="button"
            @click="selectSection(item.id)"
          >
            <span class="settings-section-nav__icon">{{ item.icon }}</span>
            <span>
              <strong>{{ item.title }}</strong>
              <small>{{ item.description }}</small>
            </span>
          </button>
          <div class="settings-section-nav__notice">
            <span>安全说明</span>
            <p>页面不会读取或回显明文密码、API Key。输入框留空时，保存将保留已配置的密钥。</p>
          </div>
        </aside>

        <main class="settings-panel">
          <header class="settings-panel__header">
            <div>
              <p>{{ currentSection.description }}</p>
              <h2>{{ currentSection.title }}</h2>
            </div>
            <span v-if="restartRequired" class="restart-badge">保存后需重启</span>
          </header>

          <div v-if="loading" class="settings-panel__loading">正在读取配置…</div>

          <template v-else-if="activeSection === 'overview'">
            <section class="overview-panel">
              <article class="overview-item">
                <span class="overview-item__index">01</span>
                <div>
                  <h3>保存与生效方式</h3>
                  <p>本页保存到项目根目录 <code>.env</code>。后端和 Java 模拟器均在启动时读取环境变量，因此保存不重载当前进程。</p>
                </div>
              </article>
              <article class="overview-item">
                <span class="overview-item__index">02</span>
                <div>
                  <h3>密钥保护</h3>
                  <p>DeepSeek API Key 与 MQTT 密码只接受新输入值；接口仅返回是否已配置和末尾提示，不返回明文。</p>
                </div>
              </article>
              <article class="overview-item">
                <span class="overview-item__index">03</span>
                <div>
                  <h3>配置测试</h3>
                  <p>MQTT 测试会校验 Broker 连通性和账号密码；AI 测试会以当前输入或已保存 API Key 请求模型列表。</p>
                </div>
              </article>
            </section>

            <section class="overview-runtime">
              <div>
                <span>最近保存时间</span>
                <strong>{{ formatDateTime(configuration?.lastModifiedAt ?? null) }}</strong>
              </div>
              <div>
                <span>已启用场站</span>
                <strong>{{ configuration?.simulation.stationIds.length || '全部默认场站' }}</strong>
              </div>
              <div>
                <span>遥测发布周期</span>
                <strong>{{ configuration?.simulation.intervalMillis ?? '—' }} ms</strong>
              </div>
            </section>
          </template>

          <template v-else-if="activeSection === 'mqtt'">
            <section class="form-section">
              <div class="form-section__header">
                <div>
                  <h3>Broker 接入</h3>
                  <p>后端与模拟器共用 EMQX 账号；保存后需要重启两个进程。</p>
                </div>
                <span class="form-section__tag">需要重启</span>
              </div>
              <div class="form-grid">
                <label class="field-control">
                  <span>Broker 地址</span>
                  <input v-model.trim="form.mqtt.host" autocomplete="off" placeholder="例如 127.0.0.1" />
                </label>
                <label class="field-control">
                  <span>端口</span>
                  <input v-model.number="form.mqtt.port" type="number" min="1" max="65535" />
                </label>
                <label class="field-control">
                  <span>用户名</span>
                  <input v-model.trim="form.mqtt.username" autocomplete="username" placeholder="MQTT 用户名" />
                </label>
                <label class="field-control">
                  <span>密码</span>
                  <span class="secret-input">
                    <input
                      v-model="form.mqtt.password"
                      :type="showMqttPassword ? 'text' : 'password'"
                      autocomplete="new-password"
                      :placeholder="mqttPasswordHint"
                    />
                    <button type="button" @click="showMqttPassword = !showMqttPassword">{{ showMqttPassword ? '隐藏' : '显示' }}</button>
                  </span>
                  <small>{{ mqttPasswordHint }}</small>
                </label>
                <label class="field-control">
                  <span>后端客户端标识</span>
                  <input v-model.trim="form.mqtt.clientId" autocomplete="off" />
                </label>
                <label class="field-control">
                  <span>自动重连间隔</span>
                  <span class="unit-input">
                    <input v-model.number="form.mqtt.reconnectDelayMillis" type="number" min="1000" max="300000" />
                    <em>ms</em>
                  </span>
                </label>
              </div>
              <div class="test-row">
                <button class="secondary-button" type="button" :disabled="mqttTesting" @click="verifyMqttConnection">
                  {{ mqttTesting ? '正在测试…' : '测试 MQTT 连接' }}
                </button>
                <p v-if="mqttTestResult" :class="mqttTestResult.success ? 'test-success' : 'test-failure'">
                  {{ mqttTestResult.message }} · {{ mqttTestResult.target }}
                </p>
                <p v-else>测试使用当前填写内容；密码留空时会使用已保存密码。</p>
              </div>
            </section>
          </template>

          <template v-else-if="activeSection === 'ai'">
            <section class="form-section">
              <div class="form-section__header">
                <div>
                  <h3>DeepSeek 智能分析</h3>
                  <p>请求由 Spring Boot 后端发出，浏览器不会直连 Provider。</p>
                </div>
                <span class="form-section__tag is-teal">服务端代理</span>
              </div>
              <div class="switch-grid">
                <button
                  class="switch-control"
                  :class="{ 'is-active': form.ai.globallyEnabled }"
                  type="button"
                  role="switch"
                  :aria-checked="form.ai.globallyEnabled"
                  @click="form.ai.globallyEnabled = !form.ai.globallyEnabled"
                >
                  <span class="switch-control__track"><i /></span>
                  <span><strong>启用 AI 智能分析</strong><small>关闭时始终使用本地规则策略</small></span>
                </button>
                <button
                  class="switch-control"
                  :class="{ 'is-active': form.ai.providerEnabled }"
                  type="button"
                  role="switch"
                  :aria-checked="form.ai.providerEnabled"
                  @click="form.ai.providerEnabled = !form.ai.providerEnabled"
                >
                  <span class="switch-control__track"><i /></span>
                  <span><strong>启用 DeepSeek Provider</strong><small>可在不关闭 AI 总开关时临时禁用外部调用</small></span>
                </button>
              </div>
              <div class="form-grid">
                <label class="field-control field-control--wide">
                  <span>API 基础地址</span>
                  <input v-model.trim="form.ai.baseUrl" type="url" autocomplete="url" placeholder="https://api.deepseek.com" />
                </label>
                <label class="field-control field-control--wide">
                  <span>API Key</span>
                  <span class="secret-input">
                    <input
                      v-model="form.ai.apiKey"
                      :type="showAiApiKey ? 'text' : 'password'"
                      autocomplete="new-password"
                      :placeholder="aiKeyHint"
                    />
                    <button type="button" @click="showAiApiKey = !showAiApiKey">{{ showAiApiKey ? '隐藏' : '显示' }}</button>
                  </span>
                  <small>{{ aiKeyHint }}</small>
                </label>
                <label class="field-control">
                  <span>模型名称</span>
                  <input v-model.trim="form.ai.model" autocomplete="off" />
                </label>
                <label class="field-control">
                  <span>请求超时</span>
                  <span class="unit-input">
                    <input v-model.number="form.ai.timeoutSeconds" type="number" min="1" max="300" />
                    <em>秒</em>
                  </span>
                </label>
                <label class="field-control field-control--wide">
                  <span>最大上下文长度</span>
                  <span class="unit-input">
                    <input v-model.number="form.ai.maximumContextCharacters" type="number" min="1000" max="100000" />
                    <em>字符</em>
                  </span>
                </label>
              </div>
              <div class="test-row">
                <button class="secondary-button" type="button" :disabled="aiTesting" @click="verifyAiConnection">
                  {{ aiTesting ? '正在测试…' : '测试 API Key' }}
                </button>
                <p v-if="aiTestResult" :class="aiTestResult.success ? 'test-success' : 'test-failure'">
                  {{ aiTestResult.message }}<template v-if="aiTestResult.statusCode"> · HTTP {{ aiTestResult.statusCode }}</template>
                </p>
                <p v-else>测试请求不会调用推理接口，也不会产生模型推理费用。</p>
              </div>
            </section>
          </template>

          <template v-else>
            <section class="form-section">
              <div class="form-section__header">
                <div>
                  <h3>场站模拟器</h3>
                  <p>这些参数由独立 Java 模拟器启动时读取，保存后需重新启动模拟器。</p>
                </div>
                <span class="form-section__tag">需要重启</span>
              </div>
              <div class="form-grid">
                <label class="field-control">
                  <span>遥测发布周期</span>
                  <span class="unit-input">
                    <input v-model.number="form.simulation.intervalMillis" type="number" min="100" max="60000" />
                    <em>ms</em>
                  </span>
                </label>
                <label class="field-control">
                  <span>随机种子</span>
                  <input v-model.number="form.simulation.randomSeed" type="number" />
                </label>
                <label class="field-control">
                  <span>初始通信场景</span>
                  <select v-model="form.simulation.initialScenario">
                    <option value="stable">稳定通信</option>
                    <option value="degraded">链路退化</option>
                    <option value="lost">通信中断</option>
                    <option value="recovering">恢复阶段</option>
                  </select>
                </label>
                <label class="field-control field-control--wide">
                  <span>启用场站</span>
                  <input v-model="stationIdsInput" autocomplete="off" placeholder="留空时使用全部默认场站；多个场站使用英文逗号分隔" />
                  <small>例如：pv-gobi-01, wind-ridge-02, storage-hub-03</small>
                </label>
              </div>
              <div class="simulation-note">
                <span>当前场景说明</span>
                <p>稳定通信用于正常演示；链路退化和通信中断用于故障实验；恢复阶段用于验证恢复链路。</p>
              </div>
            </section>
          </template>

          <footer class="settings-panel__footer">
            <p><span class="footer-indicator" :class="{ 'is-warning': restartRequired }" />{{ restartRequired ? '检测到 .env 已更新，当前运行进程仍在使用旧环境变量。' : '当前运行进程与 .env 保存配置一致。' }}</p>
            <button class="primary-button" type="button" :disabled="loading || saving" @click="saveConfiguration">
              {{ saving ? '正在保存…' : '保存全部配置' }}
            </button>
          </footer>
        </main>
      </section>
    </template>
  </section>
</template>

<style scoped>
/* 配置中心视觉重构：降低边框密度，突出状态、输入内容与主要操作。 */
.settings-screen {
  --settings-surface: rgba(7, 25, 67, 0.9);
  --settings-surface-soft: rgba(10, 37, 91, 0.62);
  --settings-surface-input: rgba(3, 17, 48, 0.76);
  --settings-border: rgba(125, 174, 235, 0.2);
  --settings-border-strong: rgba(74, 211, 255, 0.42);
  --settings-muted: #8eaed4;
  --settings-title: #f4f8ff;
  display: flex;
  flex-direction: column;
  gap: 16px;
  width: min(100%, 1840px);
  padding-bottom: 32px;
}

.settings-screen :where(h1, h2, h3, p) {
  margin: 0;
}

.settings-hero,
.settings-status-card,
.settings-workspace,
.settings-error {
  border: 1px solid var(--settings-border);
  border-radius: 14px;
  background: var(--settings-surface);
  box-shadow: 0 18px 48px rgba(0, 7, 29, 0.24);
}

.settings-hero {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 28px;
  min-height: 126px;
  padding: 24px 28px;
  overflow: hidden;
  background:
    radial-gradient(circle at 12% 0%, rgba(49, 165, 255, 0.24), transparent 34%),
    linear-gradient(110deg, rgba(8, 38, 96, 0.98), rgba(5, 20, 55, 0.98));
}

.settings-hero::after {
  position: absolute;
  top: -70px;
  right: 10%;
  width: 200px;
  height: 150px;
  border: 1px solid rgba(86, 220, 255, 0.08);
  border-radius: 50%;
  content: "";
  pointer-events: none;
}

.settings-hero__title,
.settings-hero__actions {
  position: relative;
  z-index: 1;
}

.settings-hero__title {
  display: flex;
  align-items: center;
  gap: 18px;
}

.settings-hero__actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}

.settings-hero__icon {
  display: grid;
  place-items: center;
  flex: 0 0 52px;
  width: 52px;
  height: 52px;
  border: 0;
  border-radius: 15px;
  color: #051d3a;
  background: linear-gradient(145deg, #4bdfff, #6ff1c2);
  box-shadow: 0 10px 28px rgba(62, 222, 255, 0.26);
  font-size: 24px;
}

.settings-hero p {
  color: #6f94bd;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.16em;
}

.settings-hero h1 {
  margin: 4px 0 0;
  color: var(--settings-title);
  font-size: 30px;
  line-height: 1.25;
  letter-spacing: 0.02em;
}

.settings-hero__title > div > span {
  display: block;
  margin-top: 4px;
  color: var(--settings-muted);
  font-size: 13px;
}

.primary-button,
.secondary-button,
.outline-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 40px;
  padding: 0 18px;
  border: 1px solid transparent;
  border-radius: 10px;
  cursor: pointer;
  font-size: 13px;
  font-weight: 700;
  transition: transform 160ms ease, border-color 160ms ease, box-shadow 160ms ease, opacity 160ms ease;
}

.primary-button:hover:not(:disabled),
.secondary-button:hover:not(:disabled),
.outline-button:hover:not(:disabled) {
  transform: translateY(-1px);
}

.primary-button:disabled,
.secondary-button:disabled,
.outline-button:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.primary-button {
  border: 0;
  color: #05263d;
  background: linear-gradient(135deg, #55ddff, #64efbd);
  box-shadow: 0 8px 22px rgba(65, 218, 221, 0.24);
}

.secondary-button {
  border-color: rgba(74, 185, 255, 0.45);
  color: #d9f5ff;
  background: rgba(22, 88, 164, 0.32);
}

.outline-button {
  border-color: rgba(126, 167, 218, 0.3);
  color: #aac6e5;
  background: rgba(5, 21, 55, 0.6);
}

.settings-status-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
  padding: 10px;
  border: 1px solid var(--settings-border);
  border-radius: 14px;
  background: rgba(4, 17, 47, 0.72);
}

.settings-status-card {
  position: relative;
  display: flex;
  flex-direction: column;
  justify-content: center;
  min-width: 0;
  min-height: 100px;
  padding: 16px 16px 14px 20px;
  border: 0;
  border-radius: 11px;
  background: linear-gradient(145deg, rgba(14, 49, 108, 0.65), rgba(7, 29, 72, 0.55));
  box-shadow: none;
}

.settings-status-card::before {
  position: absolute;
  top: 13px;
  bottom: 13px;
  left: 0;
  width: 2px;
  border-radius: 2px;
  background: rgba(78, 198, 255, 0.55);
  content: "";
}

.settings-status-card.is-warning::before {
  background: var(--screen-yellow);
}

.settings-status-card__label {
  color: #7295bd;
  font-size: 12px;
  letter-spacing: 0.05em;
}

.settings-status-card strong {
  display: block;
  margin-top: 5px;
  color: #eaf5ff;
  font-size: 18px;
  line-height: 1.2;
}

.settings-status-card small {
  display: block;
  margin-top: 4px;
  overflow: hidden;
  color: #86a6cd;
  font-size: 11px;
  line-height: 1.25;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.settings-workspace {
  display: grid;
  grid-template-columns: 244px minmax(0, 1fr);
  min-height: 540px;
  overflow: hidden;
  background: linear-gradient(130deg, rgba(5, 22, 59, 0.97), rgba(5, 19, 50, 0.95));
}

.settings-section-nav {
  display: flex;
  flex-direction: column;
  gap: 7px;
  padding: 18px 14px;
  border-right: 1px solid var(--settings-border);
  background: rgba(3, 14, 40, 0.5);
}

.settings-section-nav__item {
  display: flex;
  align-items: center;
  width: 100%;
  min-height: 68px;
  gap: 12px;
  padding: 11px 12px;
  border: 0;
  border-radius: 10px;
  color: #86a7cf;
  background: transparent;
  cursor: pointer;
  text-align: left;
  transition: color 160ms ease, background 160ms ease, transform 160ms ease;
}

.settings-section-nav__item:hover {
  border-color: transparent;
  background: rgba(25, 75, 137, 0.23);
  transform: translateX(2px);
}

.settings-section-nav__item.is-active {
  border-color: transparent;
  color: #f5fbff;
  background: linear-gradient(100deg, rgba(25, 111, 195, 0.5), rgba(18, 66, 126, 0.22));
  box-shadow: inset 2px 0 0 #4de0ff;
}

.settings-section-nav__icon {
  display: grid;
  place-items: center;
  flex: 0 0 36px;
  width: 36px;
  height: 36px;
  border: 1px solid rgba(83, 198, 255, 0.14);
  border-radius: 10px;
  background: rgba(20, 80, 157, 0.28);
  font-size: 16px;
}

.settings-section-nav__item > span:last-child {
  display: block;
  min-width: 0;
}

.settings-section-nav strong {
  display: block;
  font-size: 14px;
  line-height: 1.25;
}

.settings-section-nav small {
  display: block;
  margin-top: 3px;
  overflow: hidden;
  color: #688db7;
  font-size: 11px;
  line-height: 1.35;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.settings-section-nav__notice {
  margin-top: auto;
  padding: 16px 10px 4px;
  border-top: 1px solid rgba(124, 170, 224, 0.14);
  color: #7598bf;
  font-size: 11px;
  line-height: 1.65;
}

.settings-section-nav__notice span {
  color: #66e9bd;
  font-weight: 700;
}

.settings-panel {
  display: flex;
  flex-direction: column;
  min-width: 0;
  padding: 24px 28px 20px;
}

.settings-panel__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  min-height: 64px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--settings-border);
}

.settings-panel__header p {
  color: #6e94bd;
  font-size: 12px;
  letter-spacing: 0.04em;
}

.settings-panel__header h2 {
  margin: 4px 0 0;
  color: var(--settings-title);
  font-size: 24px;
  line-height: 1.2;
}

.settings-panel__loading {
  display: grid;
  flex: 1;
  min-height: 320px;
  place-items: center;
  color: var(--settings-muted);
  font-size: 14px;
}

.restart-badge,
.form-section__tag {
  display: inline-flex;
  align-items: center;
  min-height: 26px;
  padding: 4px 10px;
  border: 1px solid rgba(255, 205, 83, 0.28);
  border-radius: 14px;
  color: #ffe08a;
  background: rgba(255, 187, 59, 0.09);
  font-size: 11px;
}

.form-section__tag.is-teal {
  border-color: rgba(100, 239, 189, 0.32);
  color: #67efbd !important;
  background: rgba(45, 190, 142, 0.1);
}

.form-section {
  position: relative;
  padding: 22px;
  margin: 18px 0;
  border: 1px solid var(--settings-border);
  border-radius: 12px;
  background:
    linear-gradient(135deg, rgba(16, 49, 105, 0.4), rgba(6, 24, 59, 0.3)),
    rgba(6, 23, 58, 0.46);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.015);
}

.form-section__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 20px;
}

.form-section__header h3 {
  margin: 0;
  color: #eff7ff;
  font-size: 17px;
  line-height: 1.3;
}

.form-section__header p {
  margin-top: 4px;
  color: var(--settings-muted);
  font-size: 12px;
  line-height: 1.45;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 18px;
}

.field-control {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-width: 0;
}

.field-control--wide {
  grid-column: 1 / -1;
}

.field-control > span:first-child {
  color: #b9d1ed;
  font-size: 13px;
  font-weight: 600;
}

.field-control input,
.field-control select {
  width: 100%;
  min-height: 44px;
  padding: 0 14px;
  border-style: solid;
  border-width: 1px;
  border-color: rgba(118, 168, 225, 0.24);
  border-radius: 10px;
  outline: none;
  color: #f0f7ff;
  background: var(--settings-surface-input);
  font-size: 14px;
  transition: border-color 160ms ease, box-shadow 160ms ease, background 160ms ease;
}

.field-control input:hover,
.field-control select:hover {
  border-color: rgba(89, 191, 246, 0.38);
  background: rgba(5, 23, 61, 0.86);
}

.field-control input:focus,
.field-control select:focus {
  border-color: #42d7fa;
  background: rgba(4, 21, 57, 0.96);
  box-shadow: 0 0 0 3px rgba(66, 215, 250, 0.09);
}

.field-control input::placeholder {
  color: #5f7898;
}

.field-control small {
  color: #668bb4;
  font-size: 11px;
  line-height: 1.4;
}

.secret-input,
.unit-input {
  position: relative;
  display: block;
}

.secret-input input,
.unit-input input {
  padding-right: 60px;
}

.secret-input button {
  position: absolute;
  top: 6px;
  right: 6px;
  min-height: 32px;
  padding: 0 10px;
  border: 1px solid rgba(70, 193, 244, 0.2);
  border-radius: 7px;
  color: #73defd;
  background: rgba(20, 91, 163, 0.36);
  font-size: 10px;
}

.unit-input em {
  position: absolute;
  top: 13px;
  right: 12px;
  color: #6e91b8;
  font-size: 10px;
}

.test-row {
  display: flex;
  align-items: center;
  min-height: 58px;
  gap: 14px;
  margin-top: 20px;
  padding: 9px 12px;
  border: 0;
  border-radius: 9px;
  background: rgba(2, 15, 42, 0.42);
}

.test-row p {
  margin: 0;
  color: #789cc4;
  font-size: 12px;
}

.switch-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 20px;
}

.switch-control {
  display: flex;
  align-items: center;
  gap: 12px;
  min-height: 72px;
  padding: 12px 14px;
  border-style: solid;
  border-width: 1px;
  border-color: rgba(116, 165, 222, 0.2);
  border-radius: 10px;
  color: #ddecff;
  background: rgba(3, 18, 49, 0.5);
  cursor: pointer;
  text-align: left;
}

.switch-control.is-active {
  border-color: rgba(93, 232, 184, 0.34);
  background: rgba(16, 102, 83, 0.18);
}

.switch-control strong {
  display: block;
  font-size: 13px;
}

.switch-control small {
  display: block;
  margin-top: 4px;
  color: #7899bd;
  font-size: 11px;
}

.switch-control__track {
  position: relative;
  display: block;
  flex: 0 0 38px;
  width: 38px;
  height: 22px;
  border: 1px solid rgba(120, 170, 223, 0.32);
  border-radius: 12px;
  background: rgba(3, 13, 39, 0.8);
  transition: background 160ms ease, border-color 160ms ease;
}

.switch-control__track i {
  position: absolute;
  top: 3px;
  left: 3px;
  width: 14px;
  height: 14px;
  border-radius: 50%;
  background: #7293b9;
  transition: transform 160ms ease, background 160ms ease;
}

.switch-control.is-active .switch-control__track {
  border-color: rgba(100, 239, 189, 0.6);
  background: rgba(45, 190, 142, 0.24);
}

.switch-control.is-active .switch-control__track i {
  background: #64efbd;
  transform: translateX(16px);
}

.overview-panel {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  padding: 18px 0 14px;
}

.overview-item {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  min-height: 148px;
  padding: 18px;
  border: 1px solid var(--settings-border);
  border-radius: 12px;
  background: linear-gradient(145deg, rgba(12, 43, 96, 0.58), rgba(7, 28, 69, 0.45));
}

.overview-item__index {
  display: grid;
  place-items: center;
  flex: 0 0 34px;
  width: 34px;
  height: 34px;
  border-radius: 9px;
  color: #05203c;
  background: linear-gradient(145deg, #54dfff, #69eebd);
  font-size: 10px;
  font-weight: 800;
}

.overview-item h3 {
  margin: 2px 0 0;
  color: #eaf4ff;
  font-size: 15px;
}

.overview-item p {
  margin-top: 4px;
  color: #86a4c8;
  font-size: 12px;
  line-height: 1.65;
}

.overview-runtime {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.overview-runtime > div {
  padding: 16px;
  border: 1px solid var(--settings-border);
  border-radius: 9px;
  background: rgba(5, 26, 66, 0.4);
}

.overview-runtime span {
  color: #7194bc;
  font-size: 11px;
}

.overview-runtime strong {
  display: block;
  margin-top: 5px;
  color: #ecf7ff;
  font-size: 15px;
}

.simulation-note {
  margin-top: 15px;
  padding: 10px 12px;
  border-left-width: 2px;
  border-radius: 0 8px 8px 0;
  background: rgba(13, 76, 139, 0.14);
}

.simulation-note span,
.simulation-note p {
  font-size: 12px;
}

.settings-panel__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  min-height: 64px;
  padding-top: 16px;
  margin-top: auto;
  border-top: 1px solid var(--settings-border);
}

.settings-panel__footer p {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0;
  color: #789cc4;
  font-size: 12px;
}

.footer-indicator {
  display: block;
  flex: 0 0 7px;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #5fe7b5;
  box-shadow: 0 0 10px rgba(95, 231, 181, 0.65);
}

.footer-indicator.is-warning {
  background: var(--screen-yellow);
  box-shadow: 0 0 10px rgba(255, 212, 79, 0.6);
}

.settings-error {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  padding: 20px 22px;
}

.settings-error strong,
.settings-error span {
  display: block;
}

.settings-error span {
  margin-top: 5px;
  color: var(--settings-muted);
  font-size: 12px;
}

.is-teal,
.test-success {
  color: #67efbd !important;
}

.is-yellow,
.test-failure {
  color: #ffd76b !important;
}

.is-dim {
  color: #b7cae1 !important;
}

@media (max-width: 1080px) {
  .settings-workspace {
    grid-template-columns: 208px minmax(0, 1fr);
  }

  .settings-panel {
    padding: 16px;
  }
}

@media (max-width: 820px) {
  .settings-status-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .settings-workspace {
    grid-template-columns: 1fr;
  }

  .settings-section-nav {
    display: grid;
    grid-template-columns: repeat(4, minmax(0, 1fr));
    padding: 8px;
    border-right: 0;
    border-bottom: 1px solid var(--settings-border);
  }

  .settings-section-nav__item {
    justify-content: center;
  }

  .settings-section-nav__item > span:last-child small,
  .settings-section-nav__notice {
    display: none;
  }

  .overview-panel {
    grid-template-columns: 1fr;
  }

  .overview-item {
    min-height: 0;
  }
}

@media (max-width: 620px) {
  .settings-hero,
  .settings-panel__footer {
    align-items: flex-start;
    flex-direction: column;
  }

  .settings-hero {
    padding: 20px;
  }

  .settings-hero__actions {
    width: 100%;
  }

  .settings-hero__actions button {
    flex: 1;
  }

  .settings-hero h1 {
    font-size: 24px;
  }

  .settings-status-grid,
  .form-grid,
  .switch-grid,
  .overview-runtime {
    grid-template-columns: 1fr;
  }

  .field-control--wide {
    grid-column: auto;
  }

  .settings-section-nav {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .settings-panel {
    padding: 12px;
  }

  .form-section {
    padding: 13px;
  }

  .settings-panel__footer {
    align-items: stretch;
  }

  .settings-panel__footer .primary-button {
    width: 100%;
  }

  .settings-error {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
