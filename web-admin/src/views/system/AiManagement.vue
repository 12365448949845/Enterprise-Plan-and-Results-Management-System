<template>
  <section class="page-panel" v-loading="loading">
    <div class="page-header">
      <div>
        <span class="eyebrow">AI CONTROL CENTER</span>
        <h1 class="page-title">AI 配置</h1>
        <p class="page-subtitle">管理模型连接、启用配置、灰度范围、限流、Prompt 版本和调用指标，密钥不会回显。</p>
      </div>
    </div>

    <el-tabs v-model="activeTab">
      <el-tab-pane label="模型与开关" name="config">
        <div class="config-toolbar">
          <el-alert type="warning" :closable="false" show-icon title="任意时刻仅有一条模型配置启用；切换前请先测试连接。" />
          <el-button type="primary" :icon="Plus" @click="openCreate">新增配置</el-button>
        </div>

        <el-table :data="configs" border row-key="id" class="config-table">
          <el-table-column prop="configName" label="配置名称" min-width="170" />
          <el-table-column prop="providerCode" label="供应商" width="170" />
          <el-table-column prop="modelName" label="模型名称" min-width="170" />
          <el-table-column label="AI 总开关" width="110" align="center">
            <template #default="{ row }"><el-tag :type="row.globalEnabled ? 'success' : 'info'">{{ row.globalEnabled ? '开启' : '关闭' }}</el-tag></template>
          </el-table-column>
          <el-table-column label="API Key" width="110" align="center">
            <template #default="{ row }"><el-tag :type="row.apiKeyConfigured || row.providerCode === 'MOCK' ? 'success' : 'danger'">{{ row.providerCode === 'MOCK' ? '无需配置' : row.apiKeyConfigured ? '已配置' : '未配置' }}</el-tag></template>
          </el-table-column>
          <el-table-column label="启用状态" width="110" align="center">
            <template #default="{ row }"><el-tag :type="row.status === 'ENABLED' ? 'success' : 'info'">{{ row.status === 'ENABLED' ? '当前启用' : '未启用' }}</el-tag></template>
          </el-table-column>
          <el-table-column label="版本" width="80" align="center"><template #default="{ row }">v{{ row.versionNo }}</template></el-table-column>
          <el-table-column label="操作" width="270" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" :icon="Edit" @click="openEdit(row)">编辑</el-button>
              <el-button link type="primary" :icon="Connection" :loading="testingId === row.id" @click="testConnection(row)">测试</el-button>
              <el-button v-if="row.status !== 'ENABLED'" link type="success" :icon="SwitchButton" :loading="enablingId === row.id" @click="enableConfig(row)">启用</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="Prompt 版本" name="prompts">
        <el-table :data="prompts" border>
          <el-table-column prop="sceneCode" label="场景" min-width="220" />
          <el-table-column prop="versionNo" label="版本" width="90" />
          <el-table-column prop="outputSchemaVersion" label="结构版本" width="110" />
          <el-table-column prop="status" label="状态" width="100"><template #default="{ row }"><el-tag :type="row.status === 'ENABLED' ? 'success' : 'info'">{{ row.status }}</el-tag></template></el-table-column>
          <el-table-column prop="createdAt" label="发布时间" width="170" />
          <el-table-column label="操作" width="100"><template #default="{ row }"><el-button link type="primary" @click="editPrompt(row)">新版本</el-button></template></el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="调用指标" name="metrics">
        <div class="metric-grid ai-metrics">
          <div class="metric"><span>调用总数</span><strong>{{ metrics.totalCalls }}</strong></div>
          <div class="metric"><span>成功率</span><strong>{{ metrics.successRate }}%</strong></div>
          <div class="metric"><span>总 Token</span><strong>{{ metrics.inputTokens + metrics.outputTokens }}</strong></div>
          <div class="metric"><span>平均耗时</span><strong>{{ metrics.averageLatencyMs }}ms</strong></div>
          <div class="metric"><span>建议采纳率</span><strong>{{ metrics.adoptionRate }}%</strong></div>
        </div>
        <el-table :data="metrics.byScene" border><el-table-column prop="sceneCode" label="场景" min-width="220" /><el-table-column prop="calls" label="调用" /><el-table-column prop="successes" label="成功" /><el-table-column prop="tokens" label="Token" /><el-table-column prop="averageLatencyMs" label="平均耗时(ms)" /></el-table>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="configDialog" :title="editingId === null ? '新增模型配置' : '编辑模型配置'" width="min(860px, 94vw)" destroy-on-close>
      <el-form label-position="top" class="ai-config-form">
        <div class="form-grid three-columns">
          <el-form-item label="配置名称" required><el-input v-model="configForm.configName" maxlength="120" /></el-form-item>
          <el-form-item label="供应商" required><el-select v-model="configForm.providerCode"><el-option label="开发 Mock" value="MOCK" /><el-option label="OpenAI 兼容" value="OPENAI_COMPATIBLE" /><el-option label="阿里云兼容" value="ALIYUN" /></el-select></el-form-item>
          <el-form-item label="模型名称" required><el-input v-model="configForm.modelName" maxlength="120" /></el-form-item>
          <el-form-item class="span-two" label="调用地址"><el-input v-model="configForm.baseUrl" :disabled="configForm.providerCode === 'MOCK'" placeholder="例如 https://dashscope.aliyuncs.com/compatible-mode/v1" /></el-form-item>
          <el-form-item label="超时秒数"><el-input-number v-model="configForm.timeoutSeconds" :min="5" :max="120" /></el-form-item>
          <el-form-item class="span-two" :label="apiKeyConfigured ? 'API Key（已配置，留空则保留）' : 'API Key'"><el-input v-model="apiKey" type="password" show-password autocomplete="new-password" :disabled="configForm.providerCode === 'MOCK'" /></el-form-item>
          <el-form-item label="AI 总开关"><el-switch v-model="configForm.globalEnabled" /></el-form-item>
        </div>
        <div class="switch-row"><el-checkbox v-model="configForm.draftEnabled">整表生成</el-checkbox><el-checkbox v-model="configForm.optimizeEnabled">单条优化</el-checkbox><el-checkbox v-model="configForm.checkEnabled">提交前检查</el-checkbox></div>
        <div class="form-grid three-columns">
          <el-form-item label="灰度用户 ID"><el-input v-model="configForm.allowedUserIds" placeholder="逗号分隔；留空不限制" /></el-form-item>
          <el-form-item label="灰度组织 ID"><el-input v-model="configForm.allowedOrgIds" placeholder="逗号分隔；留空不限制" /></el-form-item>
          <el-form-item label="整表生成/人/日"><el-input-number v-model="configForm.draftDailyLimit" :min="1" :max="1000" /></el-form-item>
          <el-form-item label="单条优化/人/日"><el-input-number v-model="configForm.optimizeDailyLimit" :min="1" :max="5000" /></el-form-item>
          <el-form-item label="提交检查/人/日"><el-input-number v-model="configForm.checkDailyLimit" :min="1" :max="5000" /></el-form-item>
          <el-form-item label="配置版本"><el-input :model-value="editingId === null ? '新配置' : `v${configForm.versionNo}`" disabled /></el-form-item>
        </div>
      </el-form>
      <template #footer><el-button @click="configDialog = false">取消</el-button><el-button type="primary" :loading="saving" @click="saveConfig">保存</el-button></template>
    </el-dialog>

    <el-dialog v-model="promptDialog" title="发布 Prompt 新版本" width="min(760px, 94vw)">
      <el-alert type="info" :closable="false" show-icon title="发布会归档当前启用版本，历史调用仍保留原版本号。" />
      <el-form label-position="top" class="dialog-form-gap">
        <el-form-item label="场景"><el-input v-model="promptForm.sceneCode" disabled /></el-form-item>
        <el-form-item label="系统提示词"><el-input v-model="promptForm.systemPrompt" type="textarea" :rows="6" maxlength="20000" show-word-limit /></el-form-item>
        <el-form-item label="用户模板"><el-input v-model="promptForm.userTemplate" type="textarea" :rows="6" maxlength="20000" show-word-limit /></el-form-item>
        <el-form-item label="输出结构版本"><el-input v-model="promptForm.outputSchemaVersion" maxlength="30" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="promptDialog = false">取消</el-button><el-button type="primary" :loading="publishing" @click="publishPrompt">发布</el-button></template>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Connection, Edit, Plus, SwitchButton } from '@element-plus/icons-vue'
import { createAiConfigApi, enableAiConfigApi, getAiMetricsApi, listAiConfigsApi, listAiPromptsApi, publishAiPromptApi, testAiConnectionApi, updateAiConfigApi, type AiMetrics, type AiModelConfig, type AiModelConfigPayload, type AiPrompt } from '@/api/aiManagement'

const defaultConfig = (): AiModelConfigPayload => ({ configName: '', providerCode: 'MOCK', baseUrl: '', modelName: 'planning-mock-v1', timeoutSeconds: 30, globalEnabled: true, draftEnabled: true, optimizeEnabled: true, checkEnabled: true, allowedUserIds: '', allowedOrgIds: '', draftDailyLimit: 10, optimizeDailyLimit: 30, checkDailyLimit: 20, versionNo: 1 })
const activeTab = ref('config'), loading = ref(false), saving = ref(false), publishing = ref(false)
const testingId = ref<number | null>(null), enablingId = ref<number | null>(null), editingId = ref<number | null>(null)
const configDialog = ref(false), promptDialog = ref(false), apiKeyConfigured = ref(false), apiKey = ref('')
const configs = ref<AiModelConfig[]>([]), prompts = ref<AiPrompt[]>([])
const configForm = reactive<AiModelConfigPayload>(defaultConfig())
const metrics = reactive<AiMetrics>({ totalCalls: 0, successCalls: 0, successRate: 0, inputTokens: 0, outputTokens: 0, averageLatencyMs: 0, adoptedSuggestions: 0, adoptionRate: 0, byScene: [] })
const promptForm = reactive({ sceneCode: '', systemPrompt: '', userTemplate: '', outputSchemaVersion: 'v1' })

async function load() {
  loading.value = true
  try {
    const [configData, promptData, metricData] = await Promise.all([listAiConfigsApi(), listAiPromptsApi(), getAiMetricsApi()])
    configs.value = configData; prompts.value = promptData; Object.assign(metrics, metricData)
  } catch (error) { showError(error, 'AI 配置加载失败') }
  finally { loading.value = false }
}
function openCreate() { editingId.value = null; apiKeyConfigured.value = false; apiKey.value = ''; Object.assign(configForm, defaultConfig()); configDialog.value = true }
function openEdit(row: AiModelConfig) {
  editingId.value = row.id; apiKeyConfigured.value = row.apiKeyConfigured; apiKey.value = ''
  Object.assign(configForm, { configName: row.configName, providerCode: row.providerCode, baseUrl: row.baseUrl || '', modelName: row.modelName, timeoutSeconds: row.timeoutSeconds, globalEnabled: row.globalEnabled, draftEnabled: row.draftEnabled, optimizeEnabled: row.optimizeEnabled, checkEnabled: row.checkEnabled, allowedUserIds: row.allowedUserIds || '', allowedOrgIds: row.allowedOrgIds || '', draftDailyLimit: row.draftDailyLimit, optimizeDailyLimit: row.optimizeDailyLimit, checkDailyLimit: row.checkDailyLimit, versionNo: row.versionNo })
  configDialog.value = true
}
async function saveConfig() {
  if (!configForm.configName.trim() || !configForm.modelName.trim()) return ElMessage.warning('请填写配置名称和模型名称')
  if (configForm.providerCode !== 'MOCK' && !configForm.baseUrl.trim()) return ElMessage.warning('请填写调用地址')
  saving.value = true
  try {
    const payload = { ...configForm, apiKey: apiKey.value || undefined }
    if (editingId.value === null) await createAiConfigApi(payload); else await updateAiConfigApi(editingId.value, payload)
    ElMessage.success(editingId.value === null ? '配置已新增' : '配置已保存'); configDialog.value = false; configs.value = await listAiConfigsApi()
  } catch (error) { showError(error, '保存失败') }
  finally { saving.value = false }
}
async function testConnection(row: AiModelConfig) {
  testingId.value = row.id
  try { const result = await testAiConnectionApi(row.id); ElMessage.success(`${result.message}，耗时 ${result.latencyMs}ms`) }
  catch (error) { showError(error, '连接失败') }
  finally { testingId.value = null }
}
async function enableConfig(row: AiModelConfig) {
  try { await ElMessageBox.confirm(`启用“${row.configName}”后，当前模型配置将自动停用。`, '确认切换模型', { type: 'warning', confirmButtonText: '确认启用' }) }
  catch { return }
  enablingId.value = row.id
  try { await enableAiConfigApi(row.id); ElMessage.success('当前启用配置已切换'); configs.value = await listAiConfigsApi() }
  catch (error) { showError(error, '启用失败') }
  finally { enablingId.value = null }
}
function editPrompt(row: AiPrompt) { Object.assign(promptForm, { sceneCode: row.sceneCode, systemPrompt: row.systemPrompt, userTemplate: row.userTemplate, outputSchemaVersion: row.outputSchemaVersion }); promptDialog.value = true }
async function publishPrompt() {
  publishing.value = true
  try { await publishAiPromptApi(promptForm); ElMessage.success('Prompt 新版本已发布'); promptDialog.value = false; prompts.value = await listAiPromptsApi() }
  catch (error) { showError(error, '发布失败') }
  finally { publishing.value = false }
}
function showError(error: unknown, fallback: string) { ElMessage.error(error instanceof Error ? error.message : fallback) }
onMounted(load)
</script>

<style scoped>
.config-toolbar { display:flex; align-items:center; gap:16px; margin:8px 0 18px; }
.config-toolbar .el-alert { flex:1; }
.config-table { width:100%; }
.ai-config-form { margin-top:4px; }
.switch-row { display:flex; gap:24px; padding:12px 0 22px; }
.ai-metrics { margin:8px 0 18px; grid-template-columns:repeat(5,minmax(130px,1fr)); }
@media (max-width: 900px) { .config-toolbar { align-items:stretch; flex-direction:column; } .ai-metrics { grid-template-columns:repeat(2,1fr); } }
</style>
