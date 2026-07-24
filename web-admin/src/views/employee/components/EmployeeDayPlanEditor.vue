<template>
  <section class="employee-day-plan-editor" :class="{ 'is-compact': compact }">
    <div v-if="!compact" class="page-header">
      <div><span class="eyebrow">DAILY PLAN / {{ dateLabel }}</span><h1 class="page-title">日计划编制</h1><p class="page-subtitle">围绕当天工作填写计划，保存后再完成 AI 检查和提交。</p></div>
      <div class="toolbar"><el-tag :type="statusMeta.type">{{ statusMeta.label }}</el-tag><el-button :loading="loading" :disabled="busy" @click="loadDetail">刷新</el-button><el-button v-if="form.status === 'submitted'" type="warning" :loading="withdrawing" @click="withdrawPlan">撤回</el-button></div>
    </div>
    <div v-else class="day-plan-dialog-toolbar"><div><strong>{{ form.planDate }}</strong><span>{{ form.orgName || '未配置归属组织' }}</span></div><div class="toolbar"><el-tag :type="statusMeta.type">{{ statusMeta.label }}</el-tag><el-button :loading="loading" :disabled="busy" @click="loadDetail">刷新</el-button><el-button v-if="form.status === 'submitted'" type="warning" :loading="withdrawing" @click="withdrawPlan">撤回</el-button></div></div>

    <nav class="day-date-track" aria-label="日计划日期导航">
      <button type="button" @click="requestDate(addDays(selectedDate, -1))"><span>前一天</span><strong>{{ shortDate(addDays(selectedDate, -1)) }}</strong></button>
      <button type="button" class="is-current" @click="requestDate(today)"><span>{{ selectedDate === today ? '今天' : '回到今天' }}</span><strong>{{ shortDate(selectedDate) }}</strong><small>{{ weekdayLabel(selectedDate) }}</small></button>
      <button type="button" @click="requestDate(addDays(selectedDate, 1))"><span>后一天</span><strong>{{ shortDate(addDays(selectedDate, 1)) }}</strong></button>
      <el-date-picker :model-value="selectedDate" type="date" value-format="YYYY-MM-DD" :clearable="false" :disabled="busy" :disabled-date="disablePastDate" @update:model-value="requestDate" />
    </nav>

    <el-alert v-if="errorMessage" class="dashboard-alert" type="warning" :closable="false" show-icon :title="errorMessage" />
    <el-alert v-if="calendarRuleNotice" class="dashboard-alert" :type="form.calendarRule?.forceReport ? 'warning' : 'info'" :closable="false" show-icon :title="calendarRuleNotice" />
    <el-alert v-if="!editable && !loading" class="dashboard-alert" type="info" :closable="false" show-icon title="当前日计划已提交或已流转，页面仅支持查看。" />

    <PlanAiAssistant v-if="!loading && editable" mode="day" :date="form.planDate" :form="form" @apply="applyAiDraft" />
    <el-skeleton v-if="loading" :rows="8" animated />
    <div v-else class="day-editor-layout">
      <main class="day-editor-main">
        <PlanFeedbackBanner v-if="form.leaderComment" :status="form.status" :comment="form.leaderComment" title="直属领导反馈" :next-step="form.status === 'rejected' ? '修改计划后重新提交' : undefined" />
        <section v-if="form.departmentComment" class="day-department-feedback"><span>部门补审意见</span><p>{{ form.departmentComment }}</p><small>{{ formatTime(form.departmentReviewedAt) }}</small></section>

        <section id="day-plan-source" class="day-source-panel">
          <div class="section-header"><div><span class="eyebrow">PLAN SOURCE</span><h2>日期与计划来源</h2><p>明确当天工作的归属；提交后该区域转为只读。</p></div><el-tag :type="statusMeta.type">{{ statusMeta.label }}</el-tag></div>
          <div class="day-source-panel__grid">
            <div><span>计划日期</span><strong>{{ form.planDate }}</strong><small>{{ weekdayLabel(form.planDate) }}</small></div>
            <div><span>归属组织</span><strong>{{ form.orgName || '未配置归属组织' }}</strong><small>{{ form.calendarRule?.forceReport ? '当日需要填报' : '按实际工作填报' }}</small></div>
            <label><span>关联月计划项</span><el-select v-if="editable" v-model="form.relatedMonthPlanItemId" clearable filterable placeholder="选择关联月计划项" :disabled="editingDisabled" class="full-control"><el-option v-for="item in form.monthPlanItemOptions" :key="item.id" :label="item.taskName" :value="item.id" /></el-select><strong v-else>{{ relatedItemLabel }}</strong><small>用于建立月计划与日执行的关联</small></label>
          </div>
        </section>

        <section id="day-plan-content" class="day-content-card" :class="{ 'has-error': !form.content.trim() }">
          <div class="day-content-card__head"><div><span>01 / TODAY'S FOCUS</span><h2>今天要完成什么</h2><p>写清具体工作、预期推进结果和可判断的完成状态。</p></div><b>{{ form.content.length }}/2000</b></div>
          <el-input v-if="editable" v-model="form.content" type="textarea" :rows="9" maxlength="2000" placeholder="例如：完成周计划编辑工作台的交互重构，并通过前端生产构建" :disabled="editingDisabled" />
          <p v-else class="day-readonly-copy">{{ form.content || '未填写工作内容' }}</p>
        </section>

        <section id="day-plan-remark" class="day-content-card is-secondary">
          <div class="day-content-card__head"><div><span>02 / CONTEXT</span><h2>风险、依赖与备注</h2><p>补充需要协同、可能阻塞或需要领导关注的信息。</p></div><b>{{ form.remark.length }}/500</b></div>
          <el-input v-if="editable" v-model="form.remark" type="textarea" :rows="4" maxlength="500" placeholder="没有风险时可以留空" :disabled="editingDisabled" />
          <p v-else class="day-readonly-copy">{{ form.remark || '无补充备注' }}</p>
        </section>

        <AiReviewPanel :review="aiReview" :stale="aiReviewStale" title="AI日计划检查" empty-text="保存草稿后可执行 AI 检查；提交时也会自动检查。"><template #actions><el-button type="primary" plain :loading="aiChecking" :disabled="busy" @click="checkPlanNow">{{ aiReview ? '重新AI检查' : '立即AI检查' }}</el-button></template></AiReviewPanel>
      </main>

      <PlanValidationAside :issues="issues" :dirty="dirty" :saving="saving" :submitting="submitting" :editable="editable" :save-error="saveError" :summary-items="summaryItems" submit-label="AI检查并提交" @locate="locateIssue" @save="saveDraft" @submit="submitPlan" />
    </div>
    <AiReviewConfirmDialog v-model="aiDialogVisible" :review="aiReview" :confirming="confirmingSubmit" @confirm="confirmSubmitPlan" />
  </section>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import { checkPlanAiReviewApi, ensurePlanAiReviewApi, getLatestAiReviewApi, type AiReview } from '@/api/aiReview'
import { getEmployeeDayPlanDetailApi, saveEmployeeDayPlanDraftApi, submitEmployeeDayPlanApi, withdrawEmployeeDayPlanApi, type EmployeeDayPlanDetailResp, type EmployeeMonthPlanItemOption, type EmployeePlanStatus, type EmployeeWorkdayCalendarItem, type SaveDayPlanDraftReq } from '@/api/employee'
import AiReviewPanel from '@/components/AiReviewPanel.vue'
import AiReviewConfirmDialog from '@/components/AiReviewConfirmDialog.vue'
import { notifyAiReviewResult } from '@/utils/aiReviewFeedback'
import PlanFeedbackBanner from './PlanFeedbackBanner.vue'
import PlanValidationAside, { type PlanSummaryItem, type PlanValidationIssue } from './PlanValidationAside.vue'
import PlanAiAssistant from './PlanAiAssistant.vue'
import type { DayPlanAiForm, WeekPlanAiItem } from '@/api/employeeAi'

const props = withDefaults(defineProps<{ date: string; compact?: boolean }>(), { compact: false })
const emit = defineEmits<{ changed: []; 'date-change': [date: string] }>()
const loading = ref(false); const saving = ref(false); const submitting = ref(false); const confirmingSubmit = ref(false); const aiDialogVisible = ref(false); const aiReview = ref<AiReview | null>(null); const aiReviewStale = ref(false); const aiChecking = ref(false); const withdrawing = ref(false); const errorMessage = ref(''); const saveError = ref(''); const selectedDate = ref(props.date); const savedSnapshot = ref(''); let loadRequestId = 0; let syncingProp = false
const form = reactive({ id: 0, planDate: props.date, orgName: '', relatedMonthPlanItemId: null as number | null, content: '', remark: '', status: 'draft' as EmployeePlanStatus, monthPlanItemOptions: [] as EmployeeMonthPlanItemOption[], reviewStatus: 'PENDING_COMMENT', riskLevel: 'LOW', leaderComment: '', reviewedAt: '', departmentComment: '', departmentReviewedAt: '', calendarRule: null as EmployeeWorkdayCalendarItem | null })
const planStatusMap = { draft: { label: '草稿', type: 'info' }, submitted: { label: '已提交', type: 'warning' }, approved: { label: '已通过', type: 'success' }, rejected: { label: '已驳回', type: 'danger' }, confirmed: { label: '已确认', type: 'success' }, paused: { label: '已暂停', type: 'warning' }, canceled: { label: '已撤销', type: 'danger' }, archived: { label: '已归档', type: 'info' } } as const
const editable = computed(() => form.status === 'draft' || form.status === 'rejected')
const busy = computed(() => loading.value || saving.value || submitting.value || withdrawing.value || aiChecking.value)
const editingDisabled = computed(() => !editable.value || busy.value)
const statusMeta = computed(() => planStatusMap[form.status] ?? planStatusMap.draft)
const today = formatDate(new Date())
const dateLabel = computed(() => `${shortDate(selectedDate.value)} ${weekdayLabel(selectedDate.value)}`)
const relatedItemLabel = computed(() => form.monthPlanItemOptions.find((item) => item.id === form.relatedMonthPlanItemId)?.taskName || '未关联月计划项')
const calendarRuleNotice = computed(() => { const rule = form.calendarRule; if (!rule || (!rule.explicit && rule.ruleType === 'WORKDAY')) return ''; const labels: Record<string, string> = { WORKDAY: '工作日', WEEKEND: '周末', HOLIDAY: '节假日', LEAVE: '请假', BUSINESS_TRIP: '出差', SPECIAL_SHIFT: '特殊排班' }; return `${labels[rule.ruleType] || rule.ruleType}：${rule.forceReport ? '当日需要填报日计划' : '当日不强制填报日计划'}${rule.description ? `；${rule.description}` : ''}` })
const dirty = computed(() => !!savedSnapshot.value && normalizeForm() !== savedSnapshot.value)
const issues = computed<PlanValidationIssue[]>(() => { const result: PlanValidationIssue[] = []; if (!form.planDate) result.push({ key: 'date', label: '请选择计划日期', targetId: 'day-plan-source', blocking: true }); if (!form.content.trim()) result.push({ key: 'content', label: '请填写当天工作内容', targetId: 'day-plan-content', blocking: true }); if (!form.relatedMonthPlanItemId) result.push({ key: 'source', label: '建议关联一个月计划项', targetId: 'day-plan-source', blocking: false }); return result })
const summaryItems = computed<PlanSummaryItem[]>(() => [{ label: '计划日期', value: shortDate(form.planDate) }, { label: '工作内容', value: form.content.trim() ? '已填写' : '未填写' }, { label: '计划来源', value: form.relatedMonthPlanItemId ? '已关联' : '未关联' }])

function formatDate(date: Date) { const year = date.getFullYear(); const month = `${date.getMonth() + 1}`.padStart(2, '0'); const day = `${date.getDate()}`.padStart(2, '0'); return `${year}-${month}-${day}` }
function addDays(value: string, days: number) { const date = new Date(`${value}T00:00:00`); date.setDate(date.getDate() + days); return formatDate(date) }
function shortDate(value: string) { return value ? value.slice(5).replace('-', '/') : '--' }
function weekdayLabel(value: string) { if (!value) return ''; return ['星期日', '星期一', '星期二', '星期三', '星期四', '星期五', '星期六'][new Date(`${value}T00:00:00`).getDay()] }
function formatTime(value?: string) { return value ? value.replace('T', ' ').slice(0, 16) : '' }
function disablePastDate(date: Date) { return formatDate(date) < today }
function normalizeForm() { return JSON.stringify({ planDate: form.planDate, relatedMonthPlanItemId: form.relatedMonthPlanItemId, content: form.content.trim(), remark: form.remark.trim() }) }
function applyAiDraft(value: WeekPlanAiItem[] | DayPlanAiForm) { if (Array.isArray(value)) return; form.relatedMonthPlanItemId = value.relatedMonthPlanItemId; form.content = value.content; form.remark = value.remark }
function applyDetail(detail: EmployeeDayPlanDetailResp) { Object.assign(form, { id: detail.id, planDate: detail.planDate, orgName: detail.orgName ?? '', relatedMonthPlanItemId: detail.relatedMonthPlanItemId, content: detail.content ?? '', remark: detail.remark ?? '', status: detail.status, monthPlanItemOptions: detail.monthPlanItemOptions ?? [], reviewStatus: detail.reviewStatus ?? 'PENDING_COMMENT', riskLevel: detail.riskLevel ?? 'LOW', leaderComment: detail.leaderComment ?? '', reviewedAt: detail.reviewedAt ?? '', departmentComment: detail.departmentComment ?? '', departmentReviewedAt: detail.departmentReviewedAt ?? '', calendarRule: detail.calendarRule ?? null }); savedSnapshot.value = normalizeForm() }
function buildPayload(): SaveDayPlanDraftReq { return { id: form.id || undefined, planDate: form.planDate, relatedMonthPlanItemId: form.relatedMonthPlanItemId, content: form.content.trim(), remark: form.remark.trim() } }
async function confirmDiscard() { if (!dirty.value) return true; try { await ElMessageBox.confirm('当前日期有未保存修改，切换后这些内容会丢失。确认继续？', '切换计划日期', { type: 'warning', confirmButtonText: '放弃修改并切换', cancelButtonText: '继续编辑' }); return true } catch { return false } }
async function requestDate(value: string | null) { if (!value || value === selectedDate.value || busy.value) return; if (!(await confirmDiscard())) return; selectedDate.value = value; emit('date-change', value); await loadDetail() }
async function locateIssue(issue: PlanValidationIssue) { if (!issue.targetId) return; await nextTick(); const target = document.getElementById(issue.targetId); target?.scrollIntoView({ behavior: 'smooth', block: 'center' }); setTimeout(() => (target?.querySelector('input, textarea, button, [tabindex]') as HTMLElement | null)?.focus(), 280) }
async function loadDetail() { const requestId = ++loadRequestId; const requestedDate = selectedDate.value; loading.value = true; errorMessage.value = ''; try { const detail = await getEmployeeDayPlanDetailApi(requestedDate); if (requestId !== loadRequestId || requestedDate !== selectedDate.value) return; applyDetail(detail); aiReview.value = form.id ? await getLatestAiReviewApi('DAY_PLAN', form.id) : null; aiReviewStale.value = false } catch (error) { if (requestId === loadRequestId) errorMessage.value = error instanceof Error ? error.message : '日计划加载失败，请稍后重试' } finally { if (requestId === loadRequestId) loading.value = false } }
async function saveDraft() { if (!editable.value || saving.value || !form.content.trim()) { if (!form.content.trim()) await locateIssue(issues.value[0]); return } saving.value = true; saveError.value = ''; errorMessage.value = ''; try { applyDetail(await saveEmployeeDayPlanDraftApi(buildPayload())); ElMessage.success('日计划草稿已保存'); emit('changed') } catch (error) { saveError.value = error instanceof Error ? error.message : '保存草稿失败，请稍后重试' } finally { saving.value = false } }
async function submitPlan() { const blocking = issues.value.find((issue) => issue.blocking); if (!editable.value || blocking) { if (blocking) await locateIssue(blocking); return } submitting.value = true; saveError.value = ''; errorMessage.value = ''; try { applyDetail(await saveEmployeeDayPlanDraftApi(buildPayload())); if (!form.id) throw new Error('日计划草稿保存失败'); aiReview.value = await ensurePlanAiReviewApi('DAY_PLAN', form.id); aiReviewStale.value = false; aiDialogVisible.value = true } catch (error) { errorMessage.value = error instanceof Error ? error.message : 'AI检查失败，请稍后重试' } finally { submitting.value = false } }
async function checkPlanNow() { if (busy.value || (editable.value && !form.content.trim())) { if (!form.content.trim()) await locateIssue(issues.value[0]); return } aiChecking.value = true; errorMessage.value = ''; try { if (editable.value) applyDetail(await saveEmployeeDayPlanDraftApi(buildPayload())); if (!form.id) throw new Error('请先保存日计划后再检查'); aiReview.value = await checkPlanAiReviewApi('DAY_PLAN', form.id); aiReviewStale.value = false; notifyAiReviewResult(aiReview.value, 'AI语义检查已完成，逐维度报告已显示在当前页面'); emit('changed') } catch (error) { errorMessage.value = error instanceof Error ? error.message : 'AI检查失败，请稍后重试' } finally { aiChecking.value = false } }
async function confirmSubmitPlan() { if (confirmingSubmit.value) return; confirmingSubmit.value = true; errorMessage.value = ''; try { const result = await submitEmployeeDayPlanApi(buildPayload()); form.id = result.id; form.status = result.status; savedSnapshot.value = normalizeForm(); aiDialogVisible.value = false; ElMessage.success('日计划已提交'); emit('changed') } catch (error) { errorMessage.value = error instanceof Error ? error.message : '提交日计划失败，请稍后重试' } finally { confirmingSubmit.value = false } }
async function withdrawPlan() { if (!form.id || form.status !== 'submitted' || withdrawing.value) return; try { await ElMessageBox.confirm('撤回后日计划恢复为草稿，可修改后重新提交。确认撤回？', '撤回日计划', { type: 'warning' }) } catch { return } withdrawing.value = true; errorMessage.value = ''; try { await withdrawEmployeeDayPlanApi(form.id); ElMessage.success('日计划已撤回为草稿'); await loadDetail(); emit('changed') } catch (error) { errorMessage.value = error instanceof Error ? error.message : '撤回日计划失败，请稍后重试'; await loadDetail() } finally { withdrawing.value = false } }

watch(() => props.date, async (date) => { if (date === selectedDate.value) return; syncingProp = true; selectedDate.value = date; await loadDetail(); syncingProp = false })
watch(selectedDate, () => { if (syncingProp) return })
watch(form, () => { if (!loading.value && !saving.value && !submitting.value && !aiChecking.value && !confirmingSubmit.value && aiReview.value) aiReviewStale.value = true }, { deep: true })
onBeforeRouteLeave(async () => await confirmDiscard())
onMounted(loadDetail)
</script>

<style scoped>
.day-plan-dialog-toolbar { display: flex; align-items: center; justify-content: space-between; gap: 16px; margin-bottom: 12px; }.day-plan-dialog-toolbar > div:first-child { display: flex; flex-direction: column; gap: 4px; }.day-plan-dialog-toolbar span { color: var(--el-text-color-secondary); font-size: 13px; }
.day-date-track { display: grid; grid-template-columns: 1fr 1.35fr 1fr auto; gap: 8px; margin: 16px 0; padding: 8px; border: 1px solid var(--line); border-radius: 12px; background: #f5f8f5; }.day-date-track > button { display: grid; gap: 3px; min-height: 58px; place-content: center; border: 1px solid transparent; border-radius: 9px; color: var(--muted); background: transparent; cursor: pointer; }.day-date-track > button:hover { border-color: #bfd4c9; background: #fff; }.day-date-track > button span { font-size: 10px; letter-spacing: .06em; }.day-date-track > button strong { color: var(--ink); font-family: "IBM Plex Mono", "Cascadia Mono", monospace; }.day-date-track > button small { font-size: 10px; }.day-date-track > button.is-current { border-color: var(--blue); color: var(--blue); background: #fff; box-shadow: 0 6px 18px rgb(45 119 108 / 10%); }.day-date-track .el-date-editor { align-self: center; width: 150px; }
.day-editor-layout { display: grid; grid-template-columns: minmax(0, 1fr) 310px; gap: 20px; align-items: start; }.day-editor-main { display: grid; gap: 15px; min-width: 0; }.day-source-panel, .day-content-card, .day-department-feedback { padding: 20px; border: 1px solid var(--line); border-radius: 12px; background: #fffdf8; }.day-source-panel__grid { display: grid; grid-template-columns: .8fr 1fr 1.4fr; gap: 1px; margin-top: 15px; overflow: hidden; border: 1px solid var(--line); border-radius: 9px; background: var(--line); }.day-source-panel__grid > div, .day-source-panel__grid > label { display: grid; align-content: start; gap: 6px; min-width: 0; padding: 14px; background: #fff; }.day-source-panel__grid span { color: var(--blue); font-size: 10px; font-weight: 800; letter-spacing: .06em; }.day-source-panel__grid strong { overflow: hidden; color: var(--ink); font-size: 13px; text-overflow: ellipsis; white-space: nowrap; }.day-source-panel__grid small { color: var(--muted); font-size: 10px; line-height: 1.4; }
.day-content-card { border-left: 4px solid var(--blue); }.day-content-card.is-secondary { border-left-color: #9bac9f; }.day-content-card.has-error { border-color: #e1b4a6; border-left-color: #b9573f; }.day-content-card__head { display: flex; align-items: flex-start; justify-content: space-between; gap: 14px; margin-bottom: 14px; }.day-content-card__head span { color: var(--blue); font-size: 10px; font-weight: 800; letter-spacing: .08em; }.day-content-card__head h2 { margin: 5px 0; font-size: 18px; }.day-content-card__head p { margin: 0; color: var(--muted); font-size: 12px; }.day-content-card__head b { color: var(--muted); font: 500 11px "IBM Plex Mono", monospace; }.day-readonly-copy { min-height: 90px; margin: 0; line-height: 1.75; white-space: pre-wrap; }.day-department-feedback { background: #f7f9f7; }.day-department-feedback span { color: var(--blue); font-size: 10px; font-weight: 800; letter-spacing: .08em; }.day-department-feedback p { margin: 8px 0; line-height: 1.65; }.day-department-feedback small { color: var(--muted); }
.is-compact .day-date-track { margin-top: 8px; }.is-compact .day-editor-layout { grid-template-columns: minmax(0, 1fr) 280px; }
@media (max-width: 1180px) { .day-editor-layout, .is-compact .day-editor-layout { grid-template-columns: 1fr; } }
@media (max-width: 760px) { .day-date-track { grid-template-columns: repeat(3, 1fr); }.day-date-track .el-date-editor { grid-column: 1 / -1; width: 100%; }.day-source-panel__grid { grid-template-columns: 1fr; }.day-plan-dialog-toolbar { align-items: flex-start; flex-direction: column; } }
</style>
