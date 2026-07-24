<template>
  <section class="page-panel month-plan-editor-page">
    <div class="page-header">
      <div>
        <h1 class="page-title">月计划编辑</h1>
        <p class="page-subtitle">{{ form.planMonth || '-' }} 整表编辑、草稿保存与提交审批</p>
      </div>
      <div class="toolbar"><el-button @click="goDetail">返回详情</el-button><el-button type="primary" plain @click="aiDrawerVisible = true">打开 AI 助手</el-button><el-button type="success" plain :loading="aiChecking" :disabled="loading || saving || submitting" @click="checkMonthPlanNow">{{ aiChecking ? 'AI正在分析…' : aiReview ? '重新AI检查' : '立即AI检查' }}</el-button></div>
    </div>

    <el-alert v-if="errorMessage" class="dashboard-alert" type="warning" :closable="false" show-icon :title="errorMessage" />

    <AiReviewPanel class="mt16" :review="aiReview" :stale="aiReviewStale" title="AI月计划检查" empty-text="AI会检查任务是否具体、交付物是否可核验，以及任务、期限和权重之间是否合理。" />

    <el-skeleton v-if="loading" :rows="8" animated />
    <template v-else>
      <section v-if="form.approvalComment" class="rejection-guidance">
        <div><span>审批反馈</span><strong>{{ form.approvalComment }}</strong></div><p>请对照反馈修改对应任务，检查栏会持续提示未完成项。</p>
      </section>

      <div class="editor-workbench mt16">
        <main class="editor-main">
          <section class="plan-profile-card">
            <div class="profile-heading"><div><span>PLAN PROFILE</span><h2>{{ form.planMonth || '选择计划月份' }}</h2><p>{{ form.employeeName }} · {{ form.departmentName }}</p></div><el-tag :type="getPlanStatus(form.status).type">{{ getPlanStatus(form.status).label }}</el-tag></div>
            <el-form label-position="top">
              <el-form-item v-if="!form.id" label="计划月份"><el-date-picker v-model="form.planMonth" type="month" value-format="YYYY-MM" :disabled="editingDisabled" :disabled-date="disablePastMonth" /></el-form-item>
              <el-form-item label="计划摘要" :error="summaryIssue"><el-input v-model="form.summary" type="textarea" :rows="3" maxlength="500" show-word-limit :disabled="editingDisabled" placeholder="概括本月最重要的目标和交付重点" /></el-form-item>
            </el-form>
          </section>

          <section class="task-editor-section">
            <div class="section-header"><div><span class="section-kicker">任务编制</span><h2>逐条定义本月要完成的工作</h2><p>按“任务定义—交付验收—排期考核”完成每一项。</p></div><el-button type="primary" :disabled="editingDisabled" @click="addRow">新增任务</el-button></div>
            <div v-if="form.items.length" class="task-editor-list">
              <MonthPlanTaskEditorCard v-for="(item, index) in form.items" :key="item.id || `new-${index}`" :item="item" :index="index" :issues="issuesForItem(index)" :disabled="editingDisabled" :optimizing="optimizingIndex === index" :disabled-date="disableItemDeadline" @optimize="optimizeRow(index)" @duplicate="duplicateRow(index)" @remove="removeRow(index)" />
            </div>
            <el-empty v-else description="还没有计划任务"><el-button type="primary" :disabled="editingDisabled" @click="addRow">添加第一个任务</el-button></el-empty>
            <button class="add-task-strip" type="button" :disabled="editingDisabled" @click="addRow">＋ 新增一项计划任务</button>
          </section>
        </main>

        <aside class="validation-aside">
          <div class="aside-heading"><span>提交准备度</span><strong>{{ readiness }}%</strong></div>
          <el-progress :percentage="readiness" :show-text="false" :stroke-width="8" :status="validationIssues.length ? 'warning' : 'success'" />
          <div class="aside-metrics"><div><strong>{{ form.items.length }}</strong><span>任务</span></div><div><strong>{{ regularWeightTotal }}%</strong><span>权重</span></div></div>
          <div class="save-state"><span :class="`is-${autoSaveStatus}`"></span><div><strong>{{ autoSaveState.text }}</strong><small v-if="lastSavedAt">最后保存 {{ lastSavedAt }}</small></div><el-button v-if="autoSaveStatus === 'error'" link type="danger" @click="saveDraft(false)">重试</el-button></div>
          <div class="issue-panel"><div class="issue-title"><strong>提交前检查</strong><el-tag size="small" :type="validationIssues.length ? 'danger' : 'success'">{{ validationIssues.length ? `${validationIssues.length} 项待处理` : '可以提交' }}</el-tag></div><div v-if="validationIssues.length" class="issue-list"><button v-for="issue in validationIssues" :key="issue.id" type="button" @click="locateValidationIssue(issue)"><span>!</span>{{ issue.message }}</button></div><p v-else class="all-clear">任务字段完整，常规任务权重合计为 100%。</p></div>
          <div class="aside-actions"><el-button :disabled="editingDisabled || saving" @click="saveAndReturn">保存并返回</el-button><el-tooltip :disabled="!validationIssues.length" content="请先处理提交前检查中的问题" placement="top"><span><el-button type="primary" :loading="submitting" :disabled="editingDisabled || saving || validationIssues.length > 0" @click="submitApproval">提交审批</el-button></span></el-tooltip></div>
          <p class="approval-note">提交后页面转为只读，由直属领导审批。</p>
        </aside>
      </div>

    </template>

    <el-drawer v-model="aiDrawerVisible" title="AI 月计划助手" size="min(920px, 72vw)" destroy-on-close>
      <div class="ai-drawer-stack">
        <MonthPlanAiAssistant :plan-month="form.planMonth" :summary="form.summary" :items="buildAiForm().items" :disabled="editingDisabled" :job-description="aiJobDescription" @update:job-description="aiJobDescription = $event" @apply="applyAiPlan" />
      </div>
    </el-drawer>

    <el-dialog v-model="optimizeDialogVisible" title="应用 AI 单条优化建议" width="min(880px, 94vw)">
      <el-alert type="info" :closable="false" show-icon title="勾选需要应用的字段；未选择字段保持原值。" />
      <el-alert v-if="optimizePreviewFields.length && !hasOptimizeChanges" class="mt16" type="success" :closable="false" show-icon title="AI 建议与当前内容一致，无需应用修改。" />
      <el-checkbox-group v-if="optimizePreviewFields.length" v-model="selectedOptimizeFields" class="optimize-fields">
        <div v-for="field in optimizePreviewFields" :key="field.key" class="optimize-field" :class="{ 'is-changed': field.changed }">
          <div class="optimize-field-heading">
            <el-checkbox :value="field.key">{{ field.label }}</el-checkbox>
            <el-tag size="small" :type="field.changed ? 'warning' : 'info'">{{ field.changed ? '有变化' : '未变化' }}</el-tag>
          </div>
          <div><span>原内容</span><p>{{ displayOptimizeValue(field.originalValue, field.key) }}</p></div>
          <div><span>AI 优化后</span><p>{{ displayOptimizeValue(field.suggestedValue, field.key) }}</p></div>
        </div>
      </el-checkbox-group>
      <el-alert v-for="warning in optimizeSuggestion?.warnings || []" :key="warning" class="mt16" type="warning" :closable="false" :title="warning" />
      <template #footer>
        <el-button @click="ignoreOptimization">忽略</el-button>
        <el-button type="primary" :disabled="!selectedOptimizeFields.length" @click="applyOptimization">应用所选字段</el-button>
      </template>
    </el-dialog>
    <AiReviewConfirmDialog v-model="aiDialogVisible" :review="aiReview" :confirming="confirmingSubmit" @confirm="confirmSubmitApproval" />
  </section>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  createEmployeeMonthPlanDraftApi,
  deleteEmployeeMonthPlanItemApi,
  getEmployeeMonthPlanDetailApi,
  saveEmployeeMonthPlanDraftApi,
  submitEmployeeMonthPlanApi,
  type EmployeeMonthPlanDetailResp,
  type EmployeePlanStatus,
  type SaveMonthPlanDraftReq,
} from '@/api/employee'
import { currentMonth } from '@/api/performance'
import { checkPlanAiReviewApi, ensurePlanAiReviewApi, getLatestAiReviewApi, type AiReview } from '@/api/aiReview'
import AiReviewPanel from '@/components/AiReviewPanel.vue'
import AiReviewConfirmDialog from '@/components/AiReviewConfirmDialog.vue'
import { notifyAiReviewResult } from '@/utils/aiReviewFeedback'
import {
  aiRequestId,
  optimizeMonthPlanItemApi,
  recordAiSuggestionActionApi,
  type AiOptimizeResponse,
  type AiPlanForm,
  type AiPlanItem,
} from '@/api/employeeAi'
import MonthPlanAiAssistant from './components/MonthPlanAiAssistant.vue'
import MonthPlanTaskEditorCard from './components/MonthPlanTaskEditorCard.vue'

interface EditableMonthPlanItem extends AiPlanItem {
  id?: number
}

type OptimizableField = 'taskName' | 'taskContent' | 'deliverable' | 'deadline' | 'performanceWeight'

interface OptimizePreviewField {
  key: OptimizableField
  label: string
  originalValue: unknown
  suggestedValue: unknown
  changed: boolean
}

interface ValidationIssue {
  id: string
  itemIndex?: number
  field?: string
  message: string
}

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const saving = ref(false)
const submitting = ref(false)
const confirmingSubmit = ref(false)
const aiDialogVisible = ref(false)
const aiReview = ref<AiReview | null>(null)
const aiReviewStale = ref(false)
const aiChecking = ref(false)
const errorMessage = ref('')
const lastSavedAt = ref('')
const autoSaveStatus = ref<'idle' | 'saving' | 'saved' | 'readonly' | 'error'>('idle')
const dirty = ref(false)
let suppressChangeTracking = false
let changeVersion = 0
let autoSaveTimer: ReturnType<typeof setTimeout> | undefined
const optimizingIndex = ref(-1)
const optimizeDialogVisible = ref(false)
const optimizeSuggestion = ref<AiOptimizeResponse>()
const optimizePreviewFields = ref<OptimizePreviewField[]>([])
const selectedOptimizeFields = ref<OptimizableField[]>([])
const optimizationTargetIndex = ref(-1)
const aiJobDescription = ref('')
const aiDrawerVisible = ref(false)
let optimizationRequestVersion = 0
const optimizableFields: Array<{ key: OptimizableField; label: string }> = [
  { key: 'taskName', label: '任务名称' }, { key: 'taskContent', label: '任务内容' },
  { key: 'deliverable', label: '交付物' }, { key: 'deadline', label: '截止日期' },
  { key: 'performanceWeight', label: '绩效权重' },
]
const hasOptimizeChanges = computed(() => optimizePreviewFields.value.some((field) => field.changed))

const form = reactive({
  id: null as number | null,
  planMonth: currentMonth(),
  employeeName: '',
  departmentName: '',
  status: 'draft' as EmployeePlanStatus,
  summary: '',
  approvalComment: '',
  items: [] as EditableMonthPlanItem[],
})

const planStatusMap = {
  draft: { label: '草稿', type: 'info' },
  submitted: { label: '已提交', type: 'warning' },
  approved: { label: '已通过', type: 'success' },
  rejected: { label: '已驳回', type: 'danger' },
  paused: { label: '已暂停', type: 'warning' },
  canceled: { label: '已撤销', type: 'danger' },
  confirmed: { label: '已确认', type: 'success' },
  archived: { label: '已归档', type: 'info' },
} as const

const editable = computed(() => form.status === 'draft' || form.status === 'rejected')
const editingDisabled = computed(() => !editable.value || submitting.value || aiChecking.value)
const regularWeightTotal = computed(() => Number(form.items
  .reduce((sum, item) => sum + (Number(item.performanceWeight) || 0), 0)
  .toFixed(2)))
const validationIssues = computed<ValidationIssue[]>(() => {
  const issues: ValidationIssue[] = []
  if (!form.planMonth || form.planMonth < currentMonth()) issues.push({ id: 'plan-month', field: 'planMonth', message: '计划月份只能选择当前月或未来月份' })
  if (!form.summary.trim()) issues.push({ id: 'summary', field: 'summary', message: '请填写计划摘要' })
  if (!form.items.length) issues.push({ id: 'items-empty', message: '请至少新增一项计划任务' })
  const requiredFields: Array<[keyof EditableMonthPlanItem, string]> = [['taskName', '任务名称'], ['taskContent', '任务内容'], ['deliverable', '交付物'], ['deadline', '截止日期']]
  form.items.forEach((item, itemIndex) => {
    requiredFields.forEach(([field, label]) => { if (!String(item[field] || '').trim()) issues.push({ id: `${itemIndex}-${String(field)}`, itemIndex, field: String(field), message: `任务 ${itemIndex + 1}：请填写${label}` }) })
    if (!item.performanceWeight || item.performanceWeight <= 0 || item.performanceWeight > 100) issues.push({ id: `${itemIndex}-performanceWeight`, itemIndex, field: 'performanceWeight', message: `任务 ${itemIndex + 1}：请填写有效绩效权重` })
    if (item.deadline && (item.deadline < formatDate(new Date()) || item.deadline.slice(0, 7) !== form.planMonth)) issues.push({ id: `${itemIndex}-deadline-range`, itemIndex, field: 'deadline', message: `任务 ${itemIndex + 1}：截止日期须在计划月份内且不能早于今天` })
  })
  if (form.items.length && regularWeightTotal.value !== 100) issues.push({ id: 'weight-total', field: 'performanceWeight', message: `常规任务权重合计需为 100%，当前为 ${regularWeightTotal.value}%` })
  return issues
})
const summaryIssue = computed(() => validationIssues.value.find((issue) => issue.id === 'summary')?.message || '')
const readiness = computed(() => Math.max(0, Math.round(100 - Math.min(validationIssues.value.length * 9, 90))))
const autoSaveState = computed(() => {
  if (!editable.value || autoSaveStatus.value === 'readonly') return { text: '只读', type: 'info' as const }
  if (autoSaveStatus.value === 'saving') return { text: '保存中', type: 'warning' as const }
  if (autoSaveStatus.value === 'saved') return { text: `最近保存 ${lastSavedAt.value}`, type: 'success' as const }
  if (autoSaveStatus.value === 'error') return { text: '保存失败', type: 'danger' as const }
  return { text: '未自动保存', type: 'info' as const }
})

function getPlanStatus(status: EmployeePlanStatus) {
  return planStatusMap[status]
}

function formatDate(date: Date) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}

function disablePastMonth(date: Date) {
  return formatDate(date).slice(0, 7) < currentMonth()
}

function disableItemDeadline(date: Date) {
  const value = formatDate(date)
  return value < formatDate(new Date()) || !form.planMonth || value.slice(0, 7) !== form.planMonth
}

function defaultDeadline() {
  if (!form.planMonth) return ''
  const today = formatDate(new Date())
  return form.planMonth === today.slice(0, 7) ? today : `${form.planMonth}-01`
}

function getPlanId() {
  const rawId = route.params.id ?? route.query.id
  const id = Number(Array.isArray(rawId) ? rawId[0] : rawId)
  return Number.isFinite(id) && id > 0 ? id : null
}

async function applyDetail(detail: EmployeeMonthPlanDetailResp) {
  suppressChangeTracking = true
  try {
    form.id = detail.id
    form.planMonth = detail.planMonth
    form.employeeName = detail.employeeName
    form.departmentName = detail.departmentName
    form.status = detail.status
    form.summary = detail.summary ?? ''
    form.approvalComment = detail.approvalComment ?? ''
    form.items = detail.items.map((item) => ({
      id: item.id,
      workType: 'UNKNOWN',
      taskName: item.taskName,
      taskContent: item.taskContent,
      deliverable: item.deliverable,
      deadline: item.deadline ?? '',
      performanceWeight: Number(item.performanceWeight ?? 0),
    }))
    autoSaveStatus.value = editable.value ? 'idle' : 'readonly'
    await nextTick()
    dirty.value = false
  } finally {
    suppressChangeTracking = false
  }
}

function buildPayload(): SaveMonthPlanDraftReq {
  return {
    planMonth: form.planMonth,
    summary: form.summary,
    items: form.items.map((item) => ({
      id: item.id,
      taskName: item.taskName,
      taskContent: item.taskContent,
      deliverable: item.deliverable,
      deadline: item.deadline,
      performanceWeight: item.performanceWeight,
    })),
  }
}

function buildAiForm(): AiPlanForm {
  return {
    summary: form.summary,
    items: form.items.map(({ id: _id, ...item }) => item),
  }
}

function validateDraft() {
  const firstIssue = validationIssues.value[0]
  if (!firstIssue) return true
  ElMessage.warning(firstIssue.message)
  void locateValidationIssue(firstIssue)
  return false
}

async function loadDetail() {
  const planId = getPlanId()
  if (!planId) {
    autoSaveStatus.value = 'idle'
    return
  }
  loading.value = true
  errorMessage.value = ''
  try {
    await applyDetail(await getEmployeeMonthPlanDetailApi(planId))
    aiReview.value = form.id ? await getLatestAiReviewApi('MONTH_PLAN', form.id) : null
    aiReviewStale.value = false
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '月计划加载失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

async function saveDraft(showMessage = true) {
  if (!editable.value || saving.value) return false
  saving.value = true
  autoSaveStatus.value = 'saving'
  errorMessage.value = ''
  const versionAtStart = changeVersion
  const payload = buildPayload()
  try {
    const detail = form.id
      ? await saveEmployeeMonthPlanDraftApi(form.id, payload)
      : await createEmployeeMonthPlanDraftApi(payload)
    await applyDetail(detail)
    const changedDuringSave = changeVersion !== versionAtStart
    if (!getPlanId()) {
      await router.replace(`/employee/month-plans/${detail.id}/edit`)
    }
    lastSavedAt.value = new Date().toLocaleTimeString('zh-CN', { hour12: false })
    dirty.value = changedDuringSave
    autoSaveStatus.value = changedDuringSave ? 'idle' : 'saved'
    if (showMessage) {
      if (changedDuringSave) ElMessage.info('已保存提交前的内容，最新修改将继续自动保存')
      else ElMessage.success('草稿已保存')
    }
    return true
  } catch (error) {
    autoSaveStatus.value = 'error'
    errorMessage.value = error instanceof Error ? error.message : '保存草稿失败，请稍后重试'
    return false
  } finally {
    saving.value = false
  }
}

async function submitApproval() {
  if (!editable.value || !validateDraft()) return
  submitting.value = true
  errorMessage.value = ''
  try {
    const saved = await saveDraft(false)
    if (!saved || !form.id) return
    aiReview.value = await ensurePlanAiReviewApi('MONTH_PLAN', form.id)
    aiReviewStale.value = false
    aiDialogVisible.value = true
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : 'AI检查失败，请稍后重试'
    ElMessage.error(errorMessage.value)
  } finally {
    submitting.value = false
  }
}

async function checkMonthPlanNow() {
  if (aiChecking.value || saving.value || submitting.value) return
  if (editable.value && !validateDraft()) return
  aiChecking.value = true
  errorMessage.value = ''
  try {
    if (editable.value) {
      const saved = await saveDraft(false)
      if (!saved) return
    }
    if (!form.id) throw new Error('请先保存月计划后再检查')
    aiReview.value = await checkPlanAiReviewApi('MONTH_PLAN', form.id)
    aiReviewStale.value = false
    notifyAiReviewResult(aiReview.value, 'AI语义检查已完成，逐维度报告已显示在当前页面')
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : 'AI检查失败，请稍后重试'
    ElMessage.error(errorMessage.value)
  } finally {
    aiChecking.value = false
  }
}

async function confirmSubmitApproval() {
  if (!form.id || confirmingSubmit.value) return
  confirmingSubmit.value = true
  try {
    const result = await submitEmployeeMonthPlanApi(form.id)
    form.status = result.status
    autoSaveStatus.value = 'readonly'
    aiDialogVisible.value = false
    ElMessage.success('已提交审批')
    await router.push(`/employee/month-plans/${form.id}`)
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '提交审批失败，请稍后重试'
    ElMessage.error(errorMessage.value)
  } finally {
    confirmingSubmit.value = false
  }
}

function markDirty() {
  if (!editable.value || loading.value || suppressChangeTracking) return
  changeVersion += 1
  dirty.value = true
  if (aiReview.value) aiReviewStale.value = true
}

function scheduleAutoSave() {
  if (autoSaveTimer) clearTimeout(autoSaveTimer)
  if (!editable.value || loading.value || suppressChangeTracking || !form.planMonth || !form.items.length) return
  autoSaveTimer = setTimeout(() => {
    if (dirty.value && !saving.value && editable.value) void saveDraft(false)
  }, 3000)
}

function addRow() {
  form.items.push({
    workType: 'UNKNOWN',
    taskName: '',
    taskContent: '',
    deliverable: '',
    deadline: defaultDeadline(),
    performanceWeight: 0,
  })
  markDirty()
}

function duplicateRow(index: number) {
  const source = form.items[index]
  if (!source) return
  form.items.splice(index + 1, 0, { ...source, id: undefined, taskName: source.taskName ? `${source.taskName}（副本）` : '' })
  markDirty()
  ElMessage.success(`已复制任务 ${index + 1}`)
}

function issuesForItem(index: number) {
  return validationIssues.value.filter((issue) => issue.itemIndex === index)
}

async function locateValidationIssue(issue: ValidationIssue) {
  if (issue.itemIndex === undefined) {
    document.querySelector('.plan-profile-card')?.scrollIntoView({ behavior: 'smooth', block: 'center' })
    return
  }
  await nextTick()
  const field = document.querySelector(`[data-field-id="task-${issue.itemIndex}-${issue.field}"]`) as HTMLElement | null
  field?.scrollIntoView({ behavior: 'smooth', block: 'center' })
  setTimeout(() => (field?.querySelector('input, textarea') as HTMLElement | null)?.focus(), 350)
}

async function saveAndReturn() {
  const saved = await saveDraft(false)
  if (saved) goDetail()
}

function applyAiPlan(value: { mode: 'replace' | 'append'; summary?: string; items: AiPlanItem[] }) {
  if (value.summary !== undefined) form.summary = value.summary
  const mapped = value.items.map((item) => ({
    ...item,
    workType: item.workType ?? 'UNKNOWN',
  } satisfies EditableMonthPlanItem))
  form.items = value.mode === 'replace' ? mapped : [...form.items, ...mapped]
  markDirty()
  scheduleAutoSave()
}

async function optimizeRow(index: number) {
  const row = form.items[index]
  if (!row || editingDisabled.value || optimizingIndex.value >= 0) return
  const sourceSnapshot = { ...row }
  const request = ++optimizationRequestVersion
  const requestedMonth = form.planMonth
  optimizingIndex.value = index
  errorMessage.value = ''
  try {
    const { id: _id, ...item } = sourceSnapshot
    const response = await optimizeMonthPlanItemApi({ requestId: aiRequestId(), planMonth: form.planMonth, summary: form.summary, item, jobDescription: aiJobDescription.value || undefined })
    if (request !== optimizationRequestVersion || requestedMonth !== form.planMonth || form.items[index] !== row || !sameOptimizableValues(row, sourceSnapshot)) {
      ElMessage.info('计划内容已变化，已忽略旧的 AI 优化结果')
      return
    }
    if (!response?.item || typeof response.item !== 'object') {
      throw new Error('AI 优化结果不完整，请重新优化')
    }
    const previewFields = buildOptimizePreview(sourceSnapshot, response.item)
    optimizeSuggestion.value = response
    optimizePreviewFields.value = previewFields
    optimizationTargetIndex.value = index
    selectedOptimizeFields.value = previewFields.filter((field) => field.changed).map((field) => field.key)
    optimizeDialogVisible.value = true
    await recordAiSuggestionActionApi(response.suggestionId, { actionCode: 'PREVIEW' })
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : 'AI 单条优化失败'
    ElMessage.error(errorMessage.value)
  } finally {
    if (request === optimizationRequestVersion) optimizingIndex.value = -1
  }
}

async function applyOptimization() {
  if (!optimizeSuggestion.value || !optimizePreviewFields.value.length) return
  const task = form.items[optimizationTargetIndex.value]
  if (!task) { ElMessage.error('原计划明细已变化，请重新执行 AI 优化'); return }
  for (const field of selectedOptimizeFields.value) {
    const value = optimizePreviewFields.value.find((preview) => preview.key === field)?.suggestedValue
    if (field === 'performanceWeight') task[field] = Number(value)
    else task[field] = String(value ?? '') as never
  }
  await recordAiSuggestionActionApi(optimizeSuggestion.value.suggestionId, { actionCode: 'APPLY_FIELDS', appliedFields: selectedOptimizeFields.value })
  optimizeDialogVisible.value = false
  optimizationTargetIndex.value = -1
  optimizePreviewFields.value = []
  markDirty(); scheduleAutoSave(); ElMessage.success('AI 优化字段已应用')
}

async function ignoreOptimization() {
  if (optimizeSuggestion.value) await recordAiSuggestionActionApi(optimizeSuggestion.value.suggestionId, { actionCode: 'IGNORE' })
  optimizeDialogVisible.value = false
  optimizationTargetIndex.value = -1
  optimizePreviewFields.value = []
}

function normalizeOptimizeValue(value: unknown, field: OptimizableField) {
  if (field === 'performanceWeight') {
    const number = Number(value)
    return Number.isFinite(number) ? number : 0
  }
  return String(value ?? '').trim()
}

function sameOptimizableValues(left: EditableMonthPlanItem, right: EditableMonthPlanItem) {
  return optimizableFields.every(({ key }) => normalizeOptimizeValue(left[key], key) === normalizeOptimizeValue(right[key], key))
}

function buildOptimizePreview(source: EditableMonthPlanItem, suggestion: AiPlanItem): OptimizePreviewField[] {
  return optimizableFields.map(({ key, label }) => ({
    key,
    label,
    originalValue: source[key],
    suggestedValue: suggestion[key],
    changed: normalizeOptimizeValue(source[key], key) !== normalizeOptimizeValue(suggestion[key], key),
  }))
}

function displayOptimizeValue(value: unknown, field: OptimizableField) {
  if (field === 'performanceWeight') return `${value ?? 0}%`
  return String(value ?? '暂无')
}

async function removeRow(index: number) {
  const row = form.items[index]
  if (!row) return
  try {
    await ElMessageBox.confirm('删除后将直接从数据库移除，确认继续？', '删除计划明细', { type: 'warning' })
  } catch {
    return
  }
  if (!row.id) {
    form.items.splice(index, 1)
    markDirty()
    autoSaveStatus.value = 'idle'
    ElMessage.success('已删除当前未保存明细')
    return
  }
  if (!form.id) return
  try {
    await deleteEmployeeMonthPlanItemApi(form.id, row.id)
    form.items.splice(index, 1)
    markDirty()
    autoSaveStatus.value = 'saved'
    lastSavedAt.value = new Date().toLocaleTimeString('zh-CN', { hour12: false })
    ElMessage.success('计划明细已删除')
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '删除计划明细失败，请稍后重试'
    ElMessage.error(errorMessage.value)
  }
}

function goDetail() {
  router.push(form.id ? `/employee/month-plans/${form.id}` : '/employee/dashboard')
}

onMounted(() => {
  void loadDetail()
})

watch(form, () => {
  if (suppressChangeTracking || loading.value) return
  markDirty()
  scheduleAutoSave()
}, { deep: true })

onBeforeUnmount(() => {
  if (autoSaveTimer) clearTimeout(autoSaveTimer)
})
</script>

<style scoped>
.month-plan-editor-page { background:#f4f6f2; }
.rejection-guidance { display:flex; align-items:center; justify-content:space-between; gap:24px; padding:16px 18px; border:1px solid #ecc5c5; border-left:4px solid #c65346; border-radius:11px; background:#fff6f6; }
.rejection-guidance div { display:grid; gap:5px; }
.rejection-guidance span { color:#b1473c; font-size:10px; font-weight:800; letter-spacing:.1em; }
.rejection-guidance strong { color:#563b32; font-size:13px; line-height:1.65; }
.rejection-guidance p { max-width:360px; margin:0; color:#80645e; font-size:12px; }
.editor-workbench { display:grid; grid-template-columns:minmax(0,1fr) 310px; gap:18px; align-items:start; }
.editor-main,.task-editor-list,.ai-drawer-stack { display:grid; gap:16px; min-width:0; }
.plan-profile-card,.task-editor-section,.validation-aside { border:1px solid var(--line); border-radius:14px; background:#fff; box-shadow:var(--shadow-soft); }
.plan-profile-card { padding:22px; }
.profile-heading { display:flex; align-items:flex-start; justify-content:space-between; gap:18px; margin-bottom:18px; }
.profile-heading span,.section-kicker { display:block; color:var(--blue); font-size:10px; font-weight:800; letter-spacing:.12em; }
.profile-heading h2 { margin:5px 0 0; color:var(--ink); font-size:25px; }
.profile-heading p { margin:5px 0 0; color:var(--muted); font-size:12px; }
.plan-profile-card :deep(.el-form-item:last-child) { margin-bottom:0; }
.task-editor-section { padding:22px; background:#eef2ed; }
.task-editor-section .section-header h2 { margin-top:4px; font-size:18px; }
.add-task-strip { width:100%; margin-top:16px; padding:15px; border:1px dashed #92aaa2; border-radius:11px; color:var(--blue); background:rgb(255 255 255 / 55%); font-size:13px; font-weight:700; cursor:pointer; transition:.18s ease; }
.add-task-strip:hover { border-color:var(--blue); background:#fff; }
.add-task-strip:disabled { opacity:.5; cursor:not-allowed; }
.validation-aside { position:sticky; top:18px; padding:20px; }
.aside-heading { display:flex; align-items:flex-end; justify-content:space-between; margin-bottom:10px; }
.aside-heading span { color:#52645f; font-size:12px; font-weight:700; }
.aside-heading strong { color:var(--blue); font:700 28px/1 "IBM Plex Mono",monospace; }
.aside-metrics { display:grid; grid-template-columns:repeat(2,1fr); margin:18px 0; border:1px solid #e5eae6; border-radius:10px; background:#f8faf7; }
.aside-metrics div { display:grid; gap:4px; padding:13px 8px; border-left:1px solid #e5eae6; text-align:center; }
.aside-metrics div:first-child { border-left:0; }
.aside-metrics strong { color:var(--ink); font:700 15px/1 "IBM Plex Mono",monospace; }
.aside-metrics span { color:var(--muted); font-size:10px; }
.save-state { display:flex; align-items:center; gap:10px; padding:12px 0; border-top:1px solid #edf0eb; border-bottom:1px solid #edf0eb; }
.save-state > span { width:9px; height:9px; border-radius:50%; background:#a5b1ad; }
.save-state > span.is-saving { background:#c88738; animation:pulse 1.1s infinite; }
.save-state > span.is-saved { background:#3b8f77; }
.save-state > span.is-error { background:#c65346; }
.save-state div { display:grid; flex:1; gap:3px; }
.save-state strong { color:#40544e; font-size:12px; }
.save-state small { color:var(--muted); font-size:10px; }
.issue-panel { margin-top:18px; }
.issue-title { display:flex; align-items:center; justify-content:space-between; gap:10px; }
.issue-title strong { color:var(--ink); font-size:13px; }
.issue-list { display:grid; gap:7px; max-height:280px; margin-top:12px; overflow:auto; }
.issue-list button { display:flex; gap:8px; width:100%; padding:9px 10px; border:1px solid #f0d2cc; border-radius:8px; color:#77483f; background:#fff8f6; text-align:left; font-size:11px; line-height:1.45; cursor:pointer; }
.issue-list button:hover { border-color:#c65346; }
.issue-list button span { flex:0 0 17px; height:17px; border-radius:50%; color:#fff; background:#c65346; text-align:center; font-weight:800; }
.all-clear { margin:12px 0 0; color:#477566; font-size:11px; line-height:1.6; }
.aside-actions { display:grid; grid-template-columns:1fr 1fr; gap:9px; margin-top:20px; }
.aside-actions > span,.aside-actions .el-button { width:100%; }
.approval-note { margin:10px 0 0; color:var(--muted); font-size:10px; line-height:1.5; text-align:center; }
@keyframes pulse { 50% { opacity:.35; } }
@media (prefers-reduced-motion:reduce) { .save-state > span.is-saving { animation:none; } }
@media (max-width:1360px) { .editor-workbench { grid-template-columns:minmax(0,1fr) 280px; } }
.month-plan-ai-workspace {
  display: grid;
  grid-template-columns: minmax(0, 2fr) minmax(320px, .9fr);
  gap: 16px;
  align-items: stretch;
}

.month-plan-ai-workspace > :deep(.dashboard-section) {
  height: 100%;
}

.month-plan-rules .rule-list {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
  gap: 8px 18px;
  margin-bottom: 0;
}

.optimize-fields { display:grid; gap:10px; margin-top:16px; }
.optimize-field { display:grid; grid-template-columns:120px 1fr 1fr; gap:10px; align-items:start; border:1px solid #e5e7eb; border-radius:10px; padding:10px; }
.optimize-field.is-changed { border-color:#f5c26b; background:#fffaf0; }
.optimize-field-heading { display:flex; flex-direction:column; align-items:flex-start; gap:6px; }
.optimize-field > div { background:#f8fafc; border-radius:8px; padding:8px; min-height:54px; }
.optimize-field > .optimize-field-heading { background:transparent; padding:0; }
.optimize-field span { color:#64748b; font-size:11px; }
.optimize-field p { margin:4px 0 0; color:#334155; font-size:13px; line-height:1.6; white-space:pre-wrap; word-break:break-word; }

@media (max-width: 1180px) {
  .month-plan-ai-workspace {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 720px) {
  .month-plan-rules .rule-list,
  .optimize-field {
    grid-template-columns: 1fr;
  }
}
</style>
