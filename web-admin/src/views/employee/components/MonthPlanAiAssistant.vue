<template>
  <section class="dashboard-section ai-assistant">
    <div class="section-header">
      <div><span class="eyebrow">AI PLAN COPILOT</span><h2>月计划助手</h2><p>生成整表前先预览，不会自动提交。</p></div>
      <el-tag size="small" effect="plain">仅供参考</el-tag>
    </div>
    <el-skeleton v-if="contextLoading" :rows="4" animated />
    <template v-else>
      <el-alert v-if="contextInfo && !contextInfo.enabled" type="warning" :closable="false" show-icon :title="contextInfo.notice" />
      <div v-if="contextInfo" class="context-tags">
        <el-tag v-for="item in contextInfo.availableContext" :key="item" size="small" type="success" effect="plain">{{ item }}</el-tag>
        <el-tag v-for="item in contextInfo.missingContext" :key="item" size="small" type="warning" effect="plain">缺少：{{ item }}</el-tag>
      </div>
      <div class="ai-assistant__body">
        <el-form class="ai-assistant__form" label-position="top">
          <el-form-item label="本月工作意图">
            <el-input v-model="intentText" type="textarea" :rows="5" maxlength="5000" show-word-limit placeholder="例如：完成客户需求方案和产品版本上线，同时沉淀交付复盘模板" :disabled="disabled" />
          </el-form-item>
          <el-form-item label="岗位说明（员工资料未配置时补充）">
            <el-input v-model="jobDescription" maxlength="500" placeholder="例如：产品规划岗" :disabled="disabled" @update:model-value="emit('update:jobDescription', String($event || ''))" />
          </el-form-item>
        </el-form>
        <div class="ai-assistant__actions">
          <el-button class="generate-button" type="primary" :loading="generating" :disabled="disabled || !contextInfo?.enabled || !intentText.trim()" @click="generate">
            生成月计划草稿
          </el-button>
          <div v-if="contextInfo" class="quota">
            <span>今日剩余</span>
            <strong>生成 {{ contextInfo.remainingCalls.generate ?? 0 }} 次</strong>
            <strong>优化 {{ contextInfo.remainingCalls.optimize ?? 0 }} 次</strong>
            <strong>检查 {{ contextInfo.remainingCalls.check ?? 0 }} 次</strong>
          </div>
        </div>
      </div>
      <el-alert v-if="errorMessage" class="ai-error" type="error" :closable="false" show-icon :title="errorMessage" />
    </template>
  </section>

  <MonthPlanAiDiff :visible="diffVisible" :suggestion="suggestion" :current-summary="summary" @close="diffVisible = false" @ignore="ignore" @apply="applySuggestion" />
</template>

<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import {
  aiRequestId,
  generateMonthPlanApi,
  getMonthPlanAiContextApi,
  recordAiSuggestionActionApi,
  type AiContextResponse,
  type AiGenerateResponse,
  type AiPlanForm,
  type AiPlanItem,
} from '@/api/employeeAi'
import MonthPlanAiDiff from './MonthPlanAiDiff.vue'

const props = defineProps<{ planMonth: string; summary: string; items: AiPlanItem[]; disabled: boolean; jobDescription?: string }>()
const emit = defineEmits<{
  apply: [value: { mode: 'replace' | 'append'; summary?: string; items: AiPlanItem[]; suggestionId: string }]
  'update:jobDescription': [value: string]
}>()
const contextLoading = ref(false)
const contextInfo = ref<AiContextResponse>()
const intentText = ref('')
const jobDescription = ref(props.jobDescription || '')
const generating = ref(false)
const errorMessage = ref('')
const suggestion = ref<AiGenerateResponse>()
const diffVisible = ref(false)
let contextRequest = 0
let generationRequest = 0

async function loadContext() {
  if (!props.planMonth) return
  const request = ++contextRequest
  contextLoading.value = true
  try {
    const result = await getMonthPlanAiContextApi(props.planMonth)
    if (request === contextRequest) contextInfo.value = result
  } catch (error) {
    if (request === contextRequest) errorMessage.value = error instanceof Error ? error.message : 'AI 上下文加载失败'
  } finally {
    if (request === contextRequest) contextLoading.value = false
  }
}

async function generate() {
  const request = ++generationRequest
  const requestedMonth = props.planMonth
  generating.value = true
  errorMessage.value = ''
  try {
    const result = await generateMonthPlanApi({
      requestId: aiRequestId(), planMonth: props.planMonth, intentText: intentText.value,
      currentForm: { summary: props.summary, items: props.items } satisfies AiPlanForm,
      jobDescription: jobDescription.value || undefined,
    })
    if (request !== generationRequest || props.planMonth !== requestedMonth) {
      ElMessage.info('计划月份已变化，已忽略旧的 AI 生成结果')
      return
    }
    suggestion.value = result
    diffVisible.value = true
    await recordAiSuggestionActionApi(suggestion.value.suggestionId, { actionCode: 'PREVIEW' })
    await loadContext()
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : 'AI 月计划生成失败'
  } finally {
    if (request === generationRequest) generating.value = false
  }
}

async function applySuggestion(value: { mode: 'replace' | 'append'; applySummary: boolean; indexes: number[] }) {
  if (!suggestion.value) return
  const selected = value.indexes.map((index) => suggestion.value!.items[index]).filter(Boolean)
  const actionCode = value.mode === 'replace' ? 'APPLY_ALL' : 'APPLY_ITEM'
  await recordAiSuggestionActionApi(suggestion.value.suggestionId, {
    actionCode,
    appliedFields: [value.applySummary ? 'summary' : '', ...selected.map((_, index) => `items[${index}]`)].filter(Boolean),
  })
  emit('apply', { mode: value.mode, summary: value.applySummary ? suggestion.value.summary : undefined, items: selected, suggestionId: suggestion.value.suggestionId })
  diffVisible.value = false
  ElMessage.success(value.mode === 'replace' ? 'AI 月计划已应用到表单' : '所选 AI 任务已追加到表单')
}

async function ignore() {
  if (suggestion.value) await recordAiSuggestionActionApi(suggestion.value.suggestionId, { actionCode: 'IGNORE' })
  diffVisible.value = false
}

watch(() => props.planMonth, () => { generationRequest += 1; generating.value = false; diffVisible.value = false; void loadContext() })
watch(() => props.jobDescription, (value) => { if (value !== jobDescription.value) jobDescription.value = value || '' })
onMounted(loadContext)
</script>

<style scoped>
.ai-assistant { border-color:#cddbf7; background:linear-gradient(180deg,#f8fbff 0%,#fff 100%); }
.eyebrow { color:#2563eb; font-size:11px; letter-spacing:.08em; }
.context-tags { display:flex; flex-wrap:wrap; gap:6px; margin:12px 0; }
.ai-assistant__body { display:grid; grid-template-columns:minmax(0, 1fr) 180px; gap:16px; align-items:end; }
.ai-assistant__form { min-width:0; }
.ai-assistant__form :deep(.el-form-item:last-child) { margin-bottom:0; }
.ai-assistant__actions { display:grid; gap:10px; align-content:end; }
.generate-button { width:100%; }
.quota { display:grid; gap:6px; padding:10px; border:1px solid #dbe7f5; border-radius:8px; color:#64748b; background:#f8fbff; font-size:12px; line-height:1.45; }
.quota span { color:#64748b; }
.quota strong { color:#334155; font-weight:650; }
.ai-error { margin-top:12px; }

@media (max-width: 900px) {
  .ai-assistant__body { grid-template-columns:1fr; align-items:stretch; }
}
</style>
