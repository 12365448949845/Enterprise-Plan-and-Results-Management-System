<template>
  <section class="page-panel week-plan-workbench">
    <div class="page-header">
      <div>
        <span class="eyebrow">WEEKLY PLAN / {{ isEdit ? 'EDIT' : 'NEW' }}</span>
        <h1 class="page-title">{{ isEdit ? '编辑周计划' : '新建周计划' }}</h1>
        <p class="page-subtitle">从已审批月计划中选择任务，明确本周工作、交付物和完成日期。</p>
      </div>
      <div class="toolbar">
        <el-tag v-if="detailStatus" :type="weekPlanStatusMeta[detailStatus].type">{{ weekPlanStatusMeta[detailStatus].label }}</el-tag>
        <el-button @click="backToList">返回列表</el-button>
      </div>
    </div>

    <el-alert v-if="errorMessage" class="dashboard-alert" type="warning" :closable="false" show-icon :title="errorMessage" />
    <el-alert v-if="!optionsLoading && !parentOptions.length" class="dashboard-alert" type="warning" :closable="false" show-icon title="暂无可拆解的已审批月计划条目，请先完成月计划审批。" />
    <AiReviewPanel
      class="mt16"
      :review="aiReview"
      :stale="aiReviewStale"
      title="AI周计划检查"
      empty-text="AI会逐项对照父级月计划，检查拆解关系、重复内容和一周内的可执行性。"
    >
      <template #actions>
        <el-button type="primary" plain :loading="aiChecking" :disabled="loading || optionsLoading || saving || submitting" @click="checkWeekPlanNow">
          {{ aiChecking ? 'AI正在分析…' : aiReview ? '重新AI检查' : '立即AI检查' }}
        </el-button>
      </template>
    </AiReviewPanel>

    <div v-loading="loading || optionsLoading" class="plan-editor-layout mt16">
      <main class="plan-editor-main">
        <PlanFeedbackBanner v-if="approvalComment" :status="detailStatus" :comment="approvalComment" next-step="根据意见修改计划并重新提交" />

        <section class="week-period-card">
          <div class="week-period-card__copy">
            <span>计划周期</span>
            <strong>{{ form.weekStart ? `${form.weekStart} — ${weekEnd}` : '请选择自然周' }}</strong>
            <p>{{ isEdit ? '已有计划的周期保持不变。' : '周期固定为周一至周日，只能选择当前周及以后。' }}</p>
          </div>
          <el-date-picker v-if="!isEdit" id="week-plan-start" v-model="form.weekStart" type="date" value-format="YYYY-MM-DD" :disabled-date="disableNonMonday" placeholder="选择周一" :disabled="saving" />
          <div v-else class="week-period-card__badge"><span>WEEK</span><b>{{ weekNumber }}</b></div>
        </section>

        <PlanAiAssistant v-if="form.weekStart" mode="week" :date="form.weekStart" :form="{ items: form.items.map((item) => ({ monthPlanItemId: item.monthPlanItemId || 0, content: item.content, deliverable: item.deliverable || '', plannedFinishDate: item.plannedFinishDate || '' })) }" @apply="applyAiDraft" />

        <section class="week-task-stage">
          <div class="section-header">
            <div><span class="eyebrow">TASK BREAKDOWN</span><h2>本周任务</h2><p>每条任务只能关联一个月计划来源，按任务逐条填写更容易核对。</p></div>
            <el-button type="primary" plain :disabled="saving || submitting || aiChecking" @click="addItem">新增任务</el-button>
          </div>

          <div class="week-task-list">
            <WeekPlanTaskEditorCard
              v-for="(item, index) in form.items"
              :key="itemKeys[index]"
              v-model="form.items[index]"
              :item="item"
              :index="index"
              :options="availableOptions"
              :used-source-ids="usedSourceIds"
              :week-start="form.weekStart"
              :week-end="weekEnd"
              :disabled="saving || submitting || aiChecking"
              :field-errors="fieldErrors(index)"
              @duplicate="duplicateItem(index)"
              @remove="removeItem(index)"
            />
          </div>
          <button type="button" class="week-task-add" :disabled="saving || submitting || aiChecking" @click="addItem"><span>＋</span><strong>新增一条周任务</strong><small>继续从月计划拆解本周工作</small></button>
        </section>
      </main>

      <PlanValidationAside
        :issues="issues"
        :dirty="dirty"
        :saving="saving"
        :submitting="submitting"
        :editable="true"
        :save-error="saveError"
        :summary-items="summaryItems"
        @locate="locateIssue"
        @save="saveDraft"
        @submit="submitPlan"
      />
    </div>
    <AiReviewConfirmDialog
      v-model="aiDialogVisible"
      :review="aiReview"
      :confirming="confirmingSubmit"
      @confirm="confirmSubmit"
    />
  </section>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import { createEmployeeWeekPlanApi, getEmployeeWeekPlanApi, listWeekPlanParentOptionsApi, submitEmployeeWeekPlanApi, updateEmployeeWeekPlanApi, weekPlanStatusMeta, type WeekPlanParentOption, type WeekPlanSavePayload, type WeekPlanStatus } from '@/api/weekPlan'
import { checkPlanAiReviewApi, ensurePlanAiReviewApi, getLatestAiReviewApi, type AiReview } from '@/api/aiReview'
import AiReviewPanel from '@/components/AiReviewPanel.vue'
import AiReviewConfirmDialog from '@/components/AiReviewConfirmDialog.vue'
import { notifyAiReviewResult } from '@/utils/aiReviewFeedback'
import PlanFeedbackBanner from './components/PlanFeedbackBanner.vue'
import PlanValidationAside, { type PlanSummaryItem, type PlanValidationIssue } from './components/PlanValidationAside.vue'
import WeekPlanTaskEditorCard from './components/WeekPlanTaskEditorCard.vue'
import PlanAiAssistant from './components/PlanAiAssistant.vue'
import type { DayPlanAiForm, WeekPlanAiItem } from '@/api/employeeAi'

const route = useRoute()
const router = useRouter()
const id = computed(() => Number(route.params.id || 0))
const isEdit = computed(() => id.value > 0)
const loading = ref(false)
const optionsLoading = ref(false)
const saving = ref(false)
const submitting = ref(false)
const errorMessage = ref('')
const saveError = ref('')
const confirmingSubmit = ref(false)
const aiDialogVisible = ref(false)
const aiReview = ref<AiReview | null>(null)
const aiReviewStale = ref(false)
const aiChecking = ref(false)
const pendingSubmit = ref<{ id: number; versionNo: number } | null>(null)
const parentOptions = ref<WeekPlanParentOption[]>([])
const detailStatus = ref<WeekPlanStatus>('DRAFT')
const approvalComment = ref('')
const savedSnapshot = ref('')
let keySeed = 1
const itemKeys = ref<number[]>([keySeed++])
const form = reactive<WeekPlanSavePayload>({ weekStart: '', versionNo: undefined, items: [emptyItem()] })

const weekEnd = computed(() => form.weekStart ? addDays(form.weekStart, 6) : '')
const weekNumber = computed(() => form.weekStart ? getWeekNumber(form.weekStart) : '--')
const availableOptions = computed(() => parentOptions.value.filter((option) => !form.weekStart || overlapsMonth(form.weekStart, weekEnd.value, option.planMonth)))
const usedSourceIds = computed(() => form.items.map((item) => item.monthPlanItemId).filter((value): value is number => !!value))
const dirty = computed(() => !!savedSnapshot.value && normalizeForm() !== savedSnapshot.value || !isEdit.value && normalizeForm() !== JSON.stringify(normalizePayload({ weekStart: '', items: [emptyItem()] })))
const issues = computed<PlanValidationIssue[]>(() => {
  const result: PlanValidationIssue[] = []
  if (!form.weekStart) result.push({ key: 'week-start', label: '请选择自然周', targetId: 'week-plan-start', blocking: true })
  form.items.forEach((item, index) => {
    if (!item.monthPlanItemId) result.push({ key: `source-${index}`, label: `任务 ${index + 1} 尚未关联月计划`, targetId: `week-item-${index}-source`, blocking: true })
    if (!item.content.trim()) result.push({ key: `content-${index}`, label: `任务 ${index + 1} 尚未填写工作内容`, targetId: `week-item-${index}-content`, blocking: true })
    if (item.plannedFinishDate && (!form.weekStart || item.plannedFinishDate < form.weekStart || item.plannedFinishDate > weekEnd.value || item.plannedFinishDate < dateValue(new Date()))) result.push({ key: `finish-${index}`, label: `任务 ${index + 1} 的完成日期不在可用范围`, targetId: `week-item-${index}-finish-date`, blocking: true })
    if (!item.deliverable?.trim() && item.content.trim()) result.push({ key: `deliverable-${index}`, label: `建议补充任务 ${index + 1} 的交付物`, targetId: `week-item-${index}-deliverable`, blocking: false })
  })
  const sourceIds = usedSourceIds.value
  sourceIds.forEach((sourceId, index) => { if (sourceIds.indexOf(sourceId) !== index) result.push({ key: `duplicate-${index}`, label: '同一月计划条目不能重复关联', targetId: `week-item-${index}-source`, blocking: true }) })
  return result
})
const summaryItems = computed<PlanSummaryItem[]>(() => [
  { label: '任务数量', value: `${form.items.length} 条` },
  { label: '已关联来源', value: `${usedSourceIds.value.length}/${form.items.length}` },
  { label: '计划周期', value: form.weekStart ? `${form.weekStart.slice(5)} 至 ${weekEnd.value.slice(5)}` : '未选择' },
])

function emptyItem(): WeekPlanSavePayload['items'][number] { return { monthPlanItemId: null, content: '', deliverable: '', plannedFinishDate: '' } }
function normalizePayload(payload: Pick<WeekPlanSavePayload, 'weekStart' | 'items'>) { return { weekStart: payload.weekStart, items: payload.items.map((item) => ({ monthPlanItemId: item.monthPlanItemId, content: item.content.trim(), deliverable: item.deliverable?.trim() || '', plannedFinishDate: item.plannedFinishDate || '' })) } }
function normalizeForm() { return JSON.stringify(normalizePayload(form)) }
function addDays(value: string, days: number) { const date = new Date(`${value}T00:00:00`); date.setDate(date.getDate() + days); return dateValue(date) }
function overlapsMonth(start: string, end: string, month: string) { const first = `${month}-01`; const next = new Date(`${first}T00:00:00`); next.setMonth(next.getMonth() + 1); const last = dateValue(new Date(next.getTime() - 86400000)); return end >= first && start <= last }
function dateValue(date: Date) { return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}` }
function currentMonday() { const date = new Date(); const day = date.getDay() || 7; date.setDate(date.getDate() - day + 1); return dateValue(date) }
function disableNonMonday(date: Date) { return date.getDay() !== 1 || dateValue(date) < currentMonday() }
function getWeekNumber(value: string) { const date = new Date(`${value}T00:00:00`); const first = new Date(date.getFullYear(), 0, 1); return String(Math.ceil((((date.getTime() - first.getTime()) / 86400000) + first.getDay() + 1) / 7)).padStart(2, '0') }
function fieldErrors(index: number) { return { source: issues.value.find((issue) => issue.targetId === `week-item-${index}-source` && issue.blocking)?.label, content: issues.value.find((issue) => issue.targetId === `week-item-${index}-content`)?.label, finishDate: issues.value.find((issue) => issue.targetId === `week-item-${index}-finish-date`)?.label } }
function addItem() { form.items.push(emptyItem()); itemKeys.value.push(keySeed++) }
function applyAiDraft(value: WeekPlanAiItem[] | DayPlanAiForm) { if (!Array.isArray(value)) return; form.items = value.map((item) => ({ monthPlanItemId: item.monthPlanItemId, content: item.content, deliverable: item.deliverable || '', plannedFinishDate: item.plannedFinishDate || '' })); itemKeys.value = form.items.map(() => keySeed++) }
function duplicateItem(index: number) { const item = form.items[index]; form.items.splice(index + 1, 0, { monthPlanItemId: null, content: item.content, deliverable: item.deliverable || '', plannedFinishDate: item.plannedFinishDate || '' }); itemKeys.value.splice(index + 1, 0, keySeed++) }
async function removeItem(index: number) { const item = form.items[index]; const populated = !!(item.monthPlanItemId || item.content.trim() || item.deliverable?.trim() || item.plannedFinishDate); if (populated) { try { await ElMessageBox.confirm('删除后该任务的当前填写内容将丢失，确认删除？', '删除周任务', { type: 'warning' }) } catch { return } } form.items.splice(index, 1); itemKeys.value.splice(index, 1); if (!form.items.length) addItem() }
async function locateIssue(issue: PlanValidationIssue) { if (!issue.targetId) return; await nextTick(); const target = document.getElementById(issue.targetId); target?.scrollIntoView({ behavior: 'smooth', block: 'center' }); setTimeout(() => (target?.querySelector('input, textarea, button, [tabindex]') as HTMLElement | null)?.focus(), 280) }
function buildPayload(): WeekPlanSavePayload { return { weekStart: form.weekStart, versionNo: form.versionNo, items: form.items.map((item) => ({ monthPlanItemId: item.monthPlanItemId, content: item.content.trim(), deliverable: item.deliverable?.trim() || undefined, plannedFinishDate: item.plannedFinishDate || undefined })) } }
async function persistDraft() { const saved = isEdit.value ? await updateEmployeeWeekPlanApi(id.value, buildPayload()) : await createEmployeeWeekPlanApi(buildPayload()); form.versionNo = saved.summary.versionNo; detailStatus.value = saved.summary.status; savedSnapshot.value = normalizeForm(); if (!isEdit.value) await router.replace(`/employee/week-plans/${saved.summary.id}/edit`); return saved }
async function saveDraft() { if (saving.value) return; saving.value = true; saveError.value = ''; errorMessage.value = ''; try { await persistDraft(); ElMessage.success('周计划草稿已保存') } catch (error) { saveError.value = error instanceof Error ? error.message : '保存草稿失败，请稍后重试' } finally { saving.value = false } }
async function submitPlan() { const blocking = issues.value.find((issue) => issue.blocking); if (blocking) { await locateIssue(blocking); return } submitting.value = true; saveError.value = ''; errorMessage.value = ''; try { const saved = await persistDraft(); aiReview.value = await ensurePlanAiReviewApi('WEEK_PLAN', saved.summary.id); aiReviewStale.value = false; pendingSubmit.value = { id: saved.summary.id, versionNo: saved.summary.versionNo }; aiDialogVisible.value = true } catch (error) { errorMessage.value = error instanceof Error ? error.message : 'AI检查失败，请稍后重试' } finally { submitting.value = false } }
async function checkWeekPlanNow() { const blocking = issues.value.find((issue) => issue.blocking); if (blocking) { await locateIssue(blocking); return } if (aiChecking.value || saving.value || submitting.value) return; aiChecking.value = true; saveError.value = ''; errorMessage.value = ''; try { const saved = await persistDraft(); aiReview.value = await checkPlanAiReviewApi('WEEK_PLAN', saved.summary.id); aiReviewStale.value = false; notifyAiReviewResult(aiReview.value, 'AI语义检查已完成，逐维度报告已显示在当前页面') } catch (error) { errorMessage.value = error instanceof Error ? error.message : 'AI检查失败，请稍后重试' } finally { aiChecking.value = false } }
async function confirmSubmit() { if (!pendingSubmit.value || confirmingSubmit.value) return; confirmingSubmit.value = true; errorMessage.value = ''; try { await submitEmployeeWeekPlanApi(pendingSubmit.value.id, pendingSubmit.value.versionNo); aiDialogVisible.value = false; savedSnapshot.value = normalizeForm(); ElMessage.success('周计划已提交直属领导审批'); await router.push('/employee/week-plans') } catch (error) { errorMessage.value = error instanceof Error ? error.message : '周计划提交失败，请稍后重试' } finally { confirmingSubmit.value = false } }
async function confirmLeave() { if (!dirty.value) return true; try { await ElMessageBox.confirm('当前有未保存修改，离开后这些内容会丢失。确认离开？', '离开编辑页', { type: 'warning', confirmButtonText: '放弃修改并离开', cancelButtonText: '继续编辑' }); return true } catch { return false } }
async function backToList() { if (await confirmLeave()) await router.push('/employee/week-plans') }

async function load() {
  optionsLoading.value = true
  try { parentOptions.value = await listWeekPlanParentOptionsApi() } catch (error) { errorMessage.value = error instanceof Error ? error.message : '月计划条目加载失败' } finally { optionsLoading.value = false }
  if (!isEdit.value) return
  loading.value = true
  try {
    const detail = await getEmployeeWeekPlanApi(id.value)
    form.weekStart = detail.summary.weekStart
    form.versionNo = detail.summary.versionNo
    form.items = detail.items.map((item) => ({ monthPlanItemId: item.monthPlanItemId, content: item.content, deliverable: item.deliverable || '', plannedFinishDate: item.plannedFinishDate || '' }))
    itemKeys.value = form.items.map(() => keySeed++)
    detailStatus.value = detail.summary.status
    approvalComment.value = detail.summary.approvalComment || ''
    savedSnapshot.value = normalizeForm()
    aiReview.value = await getLatestAiReviewApi('WEEK_PLAN', id.value)
    aiReviewStale.value = false
  } catch (error) { errorMessage.value = error instanceof Error ? error.message : '周计划详情加载失败' } finally { loading.value = false }
}

onBeforeRouteLeave(async () => await confirmLeave())
onMounted(load)

watch(form, () => {
  if (!loading.value && !saving.value && !submitting.value && !aiChecking.value && !confirmingSubmit.value && aiReview.value) aiReviewStale.value = true
}, { deep: true })
</script>

<style scoped>
.plan-editor-layout { display: grid; grid-template-columns: minmax(0, 1fr) 310px; gap: 20px; align-items: start; }.plan-editor-main { display: grid; gap: 16px; min-width: 0; }
.week-period-card { display: flex; align-items: center; justify-content: space-between; gap: 20px; padding: 20px; border: 1px solid var(--line); border-left: 4px solid var(--amber); border-radius: 11px; background: linear-gradient(115deg, #fffdf8, #f4f8f5); }.week-period-card__copy { display: grid; gap: 5px; }.week-period-card__copy > span { color: var(--blue); font-size: 10px; font-weight: 800; letter-spacing: .1em; }.week-period-card__copy strong { color: var(--ink); font-size: 20px; letter-spacing: -.02em; }.week-period-card__copy p { margin: 0; color: var(--muted); font-size: 12px; }.week-period-card__badge { display: grid; min-width: 72px; place-items: center; padding: 10px; border: 1px solid #c9dbd2; border-radius: 10px; background: #eef6f1; }.week-period-card__badge span { color: var(--muted); font-size: 9px; letter-spacing: .12em; }.week-period-card__badge b { color: var(--blue); font: 800 28px/1.2 "IBM Plex Mono", "Cascadia Mono", monospace; }
.week-task-stage { padding: 20px; border: 1px solid var(--line); border-radius: 12px; background: #fffdf8; }.week-task-stage .section-header { margin-bottom: 16px; }.week-task-stage h2 { margin: 5px 0; }.week-task-list { display: grid; gap: 13px; }.week-task-add { display: grid; width: 100%; margin-top: 13px; padding: 16px; border: 1px dashed #abc7ba; border-radius: 10px; color: var(--ink); background: #f6faf7; cursor: pointer; }.week-task-add:hover { border-color: var(--blue); background: var(--soft-blue); }.week-task-add span { color: var(--blue); font-size: 22px; }.week-task-add strong { font-size: 13px; }.week-task-add small { margin-top: 3px; color: var(--muted); }
@media (max-width: 1180px) { .plan-editor-layout { grid-template-columns: 1fr; } }
@media (max-width: 680px) { .week-period-card { align-items: flex-start; flex-direction: column; }.week-period-card .el-date-editor { width: 100%; } }
</style>
