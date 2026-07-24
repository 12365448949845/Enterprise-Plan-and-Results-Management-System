<template>
  <section class="dashboard-section">
    <div class="section-header">
      <div><h2>AI 提交前检查</h2><p>只提示风险，不替代系统校验。</p></div>
      <el-button :loading="checking" :disabled="disabled || !form.items.length" @click="check">开始检查</el-button>
    </div>
    <el-empty v-if="checked && !issues.length" description="未发现 AI 风险提示" :image-size="64" />
    <div v-else-if="issues.length" class="issue-list">
      <button v-for="issue in issues" :key="`${issue.code}-${issue.fieldPath}`" type="button" class="issue" @click="emit('locate', issue.fieldPath)">
        <el-tag :type="tagType(issue.level)" size="small">{{ levelText(issue.level) }}</el-tag>
        <strong>{{ issue.message }}</strong>
        <span>{{ issue.suggestion }}</span>
        <small>{{ issue.fieldPath }}</small>
      </button>
    </div>
    <ul v-else class="rule-list"><li>检查目标清晰度、交付物、验收标准、工时和权重。</li><li>检查结果不会禁用提交按钮。</li></ul>
    <el-alert v-if="errorMessage" type="error" :closable="false" show-icon :title="errorMessage" />
  </section>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { aiRequestId, checkMonthPlanApi, recordAiSuggestionActionApi, type AiCheckIssue, type AiPlanForm } from '@/api/employeeAi'
const props = defineProps<{ planMonth: string; form: AiPlanForm; disabled: boolean; jobDescription?: string }>()
const emit = defineEmits<{ locate: [path: string] }>()
const checking = ref(false)
const checked = ref(false)
const issues = ref<AiCheckIssue[]>([])
const errorMessage = ref('')
let checkRequest = 0
async function check() {
  const request = ++checkRequest
  const requestedMonth = props.planMonth
  checking.value = true; errorMessage.value = ''
  try {
    const response = await checkMonthPlanApi({ requestId: aiRequestId(), planMonth: props.planMonth, currentForm: props.form, jobDescription: props.jobDescription })
    if (request !== checkRequest || requestedMonth !== props.planMonth) return
    issues.value = response.issues; checked.value = true
    await recordAiSuggestionActionApi(response.suggestionId, { actionCode: 'PREVIEW' })
  } catch (error) { errorMessage.value = error instanceof Error ? error.message : 'AI 检查失败' }
  finally { if (request === checkRequest) checking.value = false }
}
function tagType(level: AiCheckIssue['level']) { return level === 'HIGH' ? 'danger' : level === 'WARNING' ? 'warning' : 'info' }
function levelText(level: AiCheckIssue['level']) { return level === 'HIGH' ? '高风险' : level === 'WARNING' ? '警告' : '提示' }
</script>

<style scoped>
.issue-list { display:grid; gap:9px; }
.issue { display:grid; grid-template-columns:auto 1fr; gap:6px 9px; padding:11px; border:1px solid #e5e7eb; border-radius:10px; background:#fff; text-align:left; cursor:pointer; }
.issue span,.issue small { grid-column:2; color:#64748b; }
.issue small { font-size:11px; }
</style>
