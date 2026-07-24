<template>
  <section class="page-panel month-plan-detail">

    <el-alert v-if="errorMessage" class="dashboard-alert" type="warning" :closable="false" show-icon :title="errorMessage" />

    <el-skeleton v-if="loading" :rows="8" animated />
    <template v-else-if="detail">
      <AiReviewPanel
        :review="monthAiReview"
        title="AI月计划检查"
        empty-text="该月计划尚未生成AI检查记录。编辑或提交月计划时可执行检查，生成后的报告会持续保留在这里。"
      />
      <section class="plan-cockpit">
        <div class="cockpit-main">
          <div class="eyebrow">MONTH PLAN / {{ detail.planMonth }}</div>
          <div class="cockpit-title"><div><h1>{{ detail.planMonth }} 月计划</h1><p>{{ detail.summary || '尚未填写计划摘要' }}</p></div><el-tag size="large" :type="getPlanStatus(detail.status).type">{{ getPlanStatus(detail.status).label }}</el-tag></div>
          <div class="identity-line"><span>{{ detail.employeeName }}</span><i></i><span>{{ detail.departmentName }}</span><i></i><span>更新于 {{ detail.updatedAt }}</span></div>
        </div>
        <div class="cockpit-metrics">
          <div><span>计划任务</span><strong>{{ detail.items.length }}</strong><small>项</small></div>
          <div><span>当前权重</span><strong>{{ effectiveWeightTotal }}</strong><small>%</small></div>
          <div><span>整体完成</span><strong>{{ detail.resultSummary.overallCompletionRate }}</strong><small>%</small></div>
        </div>
        <aside class="cockpit-actions">
          <span>当前下一步</span><strong>{{ nextActionText }}</strong>
          <el-button v-if="canEdit" type="primary" size="large" @click="goEdit">继续编辑</el-button>
          <el-button v-else-if="detail.status === 'submitted'" type="warning" size="large" @click="withdrawMonthPlan">撤回计划</el-button>
          <el-button v-else-if="canAddExtra" type="primary" size="large" @click="openNewExtraTask">新增额外任务</el-button>
          <el-button v-else size="large" @click="router.push('/employee/month-plans')">返回月计划</el-button>
          <el-button v-if="canAdjust" link type="warning" @click="adjustmentDialog = true">申请暂停或撤销</el-button>
        </aside>
      </section>
      <section v-if="detail.approvalComment" class="approval-banner" :class="{ 'is-rejected': detail.status === 'rejected' }">
        <div><span>{{ detail.status === 'rejected' ? '审批未通过' : '审批意见' }}</span><strong>{{ detail.approvalComment }}</strong></div>
        <el-button v-if="canEdit" type="danger" plain @click="goEdit">根据意见修改</el-button>
      </section>

      <section class="task-workbench-section mt16">
        <div class="section-header">
          <div>
            <span class="section-kicker">计划任务</span>
            <h2>按任务查看目标、交付与验收</h2>
          </div>
          <div class="weight-legend">常规 {{ regularWeightTotal }}% · 已通过额外 {{ approvedExtraWeightTotal }}%</div>
        </div>
        <div v-if="detail.items.length" class="task-card-list">
          <MonthPlanTaskCard v-for="(item, index) in detail.items" :key="item.id" :item="item" :index="index" @edit="openExtraTaskEditor" @withdraw="withdrawExtraTask" @resubmit="resubmitExtraTask" />
        </div>
        <el-empty v-else description="当前计划还没有任务"><el-button v-if="canEdit" type="primary" @click="goEdit">开始编制</el-button></el-empty>
      </section>

      <el-row class="mt16" :gutter="16">
        <el-col :xs="24" :lg="14">
          <section class="dashboard-section">
            <div class="section-header">
              <div>
                <h2>交付物</h2>
                <p>成果文件与关联任务</p>
              </div>
            </div>
            <el-table :data="detail.deliverables" border empty-text="暂无交付物">
              <el-table-column prop="name" label="文件名称" min-width="180">
                <template #default="{ row }">
                  <el-link :href="row.fileUrl" type="primary" target="_blank">{{ row.name }}</el-link>
                </template>
              </el-table-column>
              <el-table-column prop="fileType" label="类型" width="90" />
              <el-table-column prop="relatedTaskName" label="关联任务" min-width="150" />
              <el-table-column prop="submittedAt" label="提交时间" width="170" />
            </el-table>
          </section>
        </el-col>
        <el-col :xs="24" :lg="10">
          <section class="dashboard-section">
            <div class="section-header">
              <div>
                <h2>成果汇总</h2>
                <p>提交版本与确认情况</p>
              </div>
            </div>
            <el-descriptions :column="2" border>
              <el-descriptions-item label="已提交">{{ detail.resultSummary.submittedCount }}</el-descriptions-item>
              <el-descriptions-item label="已确认">{{ detail.resultSummary.confirmedCount }}</el-descriptions-item>
              <el-descriptions-item label="已驳回">{{ detail.resultSummary.rejectedCount }}</el-descriptions-item>
              <el-descriptions-item label="最新版本">{{ detail.resultSummary.latestVersion }}</el-descriptions-item>
              <el-descriptions-item label="总体完成率" :span="2">
                <el-progress :percentage="detail.resultSummary.overallCompletionRate" :stroke-width="10" />
              </el-descriptions-item>
            </el-descriptions>
          </section>
        </el-col>
      </el-row>

      <section class="dashboard-section mt16">
        <div class="section-header">
          <div>
            <h2>确认记录</h2>
            <p>保存、提交、审批与确认的时间轴</p>
          </div>
        </div>
        <el-empty v-if="!detail.confirmRecords.length" description="暂无确认记录" />
        <el-timeline v-else>
          <el-timeline-item v-for="record in detail.confirmRecords" :key="record.id" :timestamp="record.createdAt" placement="top">
            <strong>{{ record.action }}</strong>
            <p class="timeline-meta">{{ record.operatorName }} · {{ record.comment || '无备注' }}</p>
          </el-timeline-item>
        </el-timeline>
      </section>
    </template>

    <el-dialog v-model="extraTaskDialog" :title="editingExtraItemId ? '编辑额外月计划任务' : '新增额外月计划任务'" width="760px" destroy-on-close>
      <el-alert title="额外任务不占用原常规任务的 100%，提交后仅由直属领导审批当前任务。" type="info" :closable="false" show-icon />
      <AiReviewPanel
        class="mt16"
        :review="extraTaskAiReview"
        :stale="extraTaskAiReviewStale"
        title="AI额外任务检查"
        empty-text="填写完成后可检查任务是否重复、内容是否完整，以及任务范围、期限、交付物和权重是否合理。"
      >
        <template #actions>
          <el-button type="primary" plain :loading="extraTaskAiChecking" :disabled="extraTaskSubmitting" @click="checkExtraTaskNow">
            {{ extraTaskAiChecking ? 'AI正在分析…' : extraTaskAiReview ? '重新AI检查' : '立即AI检查' }}
          </el-button>
        </template>
      </AiReviewPanel>
      <el-form class="mt16" label-position="top" :disabled="extraTaskAiChecking || extraTaskSubmitting">
        <el-row :gutter="16">
          <el-col :xs="24" :md="16"><el-form-item label="任务名称"><el-input v-model="extraTaskForm.taskName" maxlength="120" /></el-form-item></el-col>
          <el-col :xs="24" :md="8"><el-form-item label="绩效权重"><el-input-number v-model="extraTaskForm.performanceWeight" :min="0.01" :step="1" :precision="2" controls-position="right" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="任务内容"><el-input v-model="extraTaskForm.taskContent" type="textarea" :rows="3" maxlength="5000" /></el-form-item></el-col>
          <el-col :xs="24" :md="16"><el-form-item label="交付物"><el-input v-model="extraTaskForm.deliverable" maxlength="500" /></el-form-item></el-col>
          <el-col :xs="24" :md="8"><el-form-item label="截止日期"><el-date-picker v-model="extraTaskForm.deadline" type="date" value-format="YYYY-MM-DD" :disabled-date="disableExtraDeadline" /></el-form-item></el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="extraTaskDialog = false">取消</el-button>
        <el-button v-if="editingExtraItemId" :loading="extraTaskSubmitting" @click="saveExtraTaskDraft">保存草稿</el-button>
        <el-button type="primary" :loading="extraTaskSubmitting" :disabled="extraTaskAiChecking" @click="submitExtraTask">AI检查并提交审批</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="adjustmentDialog" title="申请暂停或撤销月计划" width="560px">
      <el-form label-position="top">
        <el-form-item label="处理类型">
          <el-radio-group v-model="adjustmentForm.adjustmentType">
            <el-radio-button value="PAUSE">暂停</el-radio-button>
            <el-radio-button value="CANCEL">撤销</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="申请原因">
          <el-input v-model="adjustmentForm.reason" type="textarea" :rows="4" maxlength="1000" show-word-limit />
        </el-form-item>
        <el-form-item label="影响说明">
          <el-input v-model="adjustmentForm.impactText" type="textarea" :rows="3" maxlength="1000" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="adjustmentDialog = false">取消</el-button>
        <el-button type="primary" :loading="adjustmentSubmitting" @click="submitAdjustment">提交申请</el-button>
      </template>
    </el-dialog>
    <AiReviewConfirmDialog v-model="aiDialogVisible" :review="extraTaskAiReview" :confirming="confirmingExtraTask" @confirm="confirmExtraTaskSubmit" />
  </section>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import MonthPlanTaskCard from './components/MonthPlanTaskCard.vue'
import { checkPlanAiReviewApi, ensurePlanAiReviewApi, getLatestAiReviewApi, previewExtraTaskAiReviewApi, type AiReview } from '@/api/aiReview'
import AiReviewPanel from '@/components/AiReviewPanel.vue'
import AiReviewConfirmDialog from '@/components/AiReviewConfirmDialog.vue'
import { notifyAiReviewResult } from '@/utils/aiReviewFeedback'
import {
  getEmployeeMonthPlanDetailApi,
  createEmployeePlanAdjustmentApi,
  saveEmployeeExtraMonthPlanItemDraftApi,
  submitEmployeeExtraMonthPlanItemApi,
  submitEmployeeExtraMonthPlanItemDraftApi,
  withdrawEmployeeExtraMonthPlanItemApi,
  withdrawEmployeeMonthPlanApi,
  type EmployeeMonthPlanDetailResp,
  type EmployeeMonthPlanItem,
  type EmployeePlanStatus,
  type EmployeeResultStatus,
} from '@/api/employee'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const errorMessage = ref('')
const detail = ref<EmployeeMonthPlanDetailResp | null>(null)
const adjustmentDialog = ref(false)
const extraTaskDialog = ref(false)
const editingExtraItemId = ref<number | null>(null)
const extraTaskSubmitting = ref(false)
const extraTaskAiChecking = ref(false)
const confirmingExtraTask = ref(false)
const aiDialogVisible = ref(false)
const monthAiReview = ref<AiReview | null>(null)
const extraTaskAiReview = ref<AiReview | null>(null)
const extraTaskAiReviewStale = ref(false)
const pendingExtraMode = ref<'new' | 'edited' | 'existing' | null>(null)
const pendingExtraId = ref<number | null>(null)
const adjustmentSubmitting = ref(false)
const adjustmentForm = reactive({
  adjustmentType: 'PAUSE' as 'PAUSE' | 'CANCEL',
  reason: '',
  impactText: '',
})
const extraTaskForm = reactive({
  taskName: '',
  taskContent: '',
  deliverable: '',
  deadline: '',
  performanceWeight: 0,
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

const resultStatusMap = {
  not_submitted: { label: '未提交', type: 'info' },
  draft: { label: '草稿', type: 'info' },
  submitted: { label: '已提交确认', type: 'warning' },
  confirmed: { label: '已确认', type: 'success' },
  rejected: { label: '已退回', type: 'danger' },
} as const

const canEdit = computed(() => detail.value?.status === 'draft' || detail.value?.status === 'rejected')
const canAdjust = computed(() => detail.value?.status === 'submitted' || detail.value?.status === 'approved')
const canAddExtra = computed(() => detail.value?.status === 'approved'
  && detail.value.planMonth >= formatDate(new Date()).slice(0, 7))
const regularWeightTotal = computed(() => weightTotal('REGULAR'))
const approvedExtraWeightTotal = computed(() => Number((detail.value?.items || [])
  .filter((item) => item.taskType === 'EXTRA' && item.status === 'approved')
  .reduce((sum, item) => sum + Number(item.performanceWeight || 0), 0)
  .toFixed(2)))
const effectiveWeightTotal = computed(() => Number((regularWeightTotal.value + approvedExtraWeightTotal.value).toFixed(2)))
const nextActionText = computed(() => {
  if (!detail.value) return ''
  if (canEdit.value) return detail.value.status === 'rejected' ? '根据审批意见修改计划' : '完成任务编制并提交审批'
  if (detail.value.status === 'submitted') return '等待审批，可在需要时撤回'
  if (canAddExtra.value) return '计划已生效，可补充额外任务'
  return '查看计划执行与成果记录'
})

function weightTotal(taskType: 'REGULAR' | 'EXTRA') {
  return Number((detail.value?.items || [])
    .filter((item) => item.taskType === taskType)
    .reduce((sum, item) => sum + Number(item.performanceWeight || 0), 0)
    .toFixed(2))
}

function getPlanStatus(status: EmployeePlanStatus) {
  return planStatusMap[status]
}

function getResultStatus(status: EmployeeResultStatus) {
  return resultStatusMap[status]
}

function getPlanId() {
  const rawId = route.params.id ?? route.query.id
  const id = Number(Array.isArray(rawId) ? rawId[0] : rawId)
  return Number.isFinite(id) && id > 0 ? id : null
}

function goEdit() {
  if (detail.value) {
    router.push(`/employee/month-plans/${detail.value.id}/edit`)
  }
}

function formatDate(date: Date) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}

function disableExtraDeadline(date: Date) {
  const value = formatDate(date)
  return value < formatDate(new Date()) || !detail.value || value.slice(0, 7) !== detail.value.planMonth
}

function resetExtraTaskForm() {
  editingExtraItemId.value = null
  extraTaskAiReview.value = null
  extraTaskAiReviewStale.value = false
  pendingExtraMode.value = null
  pendingExtraId.value = null
  Object.assign(extraTaskForm, {
    taskName: '', taskContent: '', deliverable: '', deadline: '', performanceWeight: 0,
  })
}

function openNewExtraTask() {
  resetExtraTaskForm()
  extraTaskDialog.value = true
}

function openExtraTaskEditor(item: EmployeeMonthPlanItem) {
  extraTaskAiReview.value = null
  extraTaskAiReviewStale.value = false
  pendingExtraMode.value = null
  pendingExtraId.value = null
  editingExtraItemId.value = item.id
  Object.assign(extraTaskForm, {
    taskName: item.taskName,
    taskContent: item.taskContent,
    deliverable: item.deliverable,
    deadline: item.deadline || '',
    performanceWeight: Number(item.performanceWeight || 0),
  })
  extraTaskDialog.value = true
}

function validateExtraTask() {
  const requiredValues = [extraTaskForm.taskName, extraTaskForm.taskContent,
    extraTaskForm.deliverable, extraTaskForm.deadline]
  if (requiredValues.some((value) => !value.trim()) || extraTaskForm.performanceWeight <= 0) {
    ElMessage.warning('请补齐任务名称、任务内容、交付物、截止日期和绩效权重')
    return false
  }
  if (!detail.value || extraTaskForm.deadline < formatDate(new Date())
    || extraTaskForm.deadline.slice(0, 7) !== detail.value.planMonth) {
    ElMessage.warning('截止日期须在计划月份内且不能早于今天')
    return false
  }
  return true
}

async function submitExtraTask() {
  if (!detail.value || !validateExtraTask()) return
  extraTaskAiChecking.value = true
  try {
    if (editingExtraItemId.value) {
      await saveEmployeeExtraMonthPlanItemDraftApi(detail.value.id, editingExtraItemId.value, { ...extraTaskForm })
      extraTaskAiReview.value = await ensurePlanAiReviewApi('EXTRA_TASK', editingExtraItemId.value)
      pendingExtraMode.value = 'edited'
      pendingExtraId.value = editingExtraItemId.value
    } else {
      if (!extraTaskAiReview.value || extraTaskAiReviewStale.value) {
        extraTaskAiReview.value = await previewExtraTaskAiReviewApi(detail.value.id, { ...extraTaskForm })
      }
      pendingExtraMode.value = 'new'
      pendingExtraId.value = null
    }
    extraTaskAiReviewStale.value = false
    aiDialogVisible.value = true
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '额外任务AI检查失败')
  } finally {
    extraTaskAiChecking.value = false
  }
}

async function checkExtraTaskNow() {
  if (!detail.value || !validateExtraTask()) return
  extraTaskAiChecking.value = true
  try {
    if (editingExtraItemId.value) {
      await saveEmployeeExtraMonthPlanItemDraftApi(detail.value.id, editingExtraItemId.value, { ...extraTaskForm })
      extraTaskAiReview.value = await checkPlanAiReviewApi('EXTRA_TASK', editingExtraItemId.value)
    } else {
      extraTaskAiReview.value = await previewExtraTaskAiReviewApi(detail.value.id, { ...extraTaskForm })
    }
    extraTaskAiReviewStale.value = false
    notifyAiReviewResult(extraTaskAiReview.value, 'AI语义检查已完成，逐维度报告已显示在当前弹窗')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '额外任务AI检查失败')
  } finally {
    extraTaskAiChecking.value = false
  }
}

async function confirmExtraTaskSubmit() {
  if (!detail.value || confirmingExtraTask.value) return
  confirmingExtraTask.value = true
  try {
    if (pendingExtraMode.value === 'new') {
      await submitEmployeeExtraMonthPlanItemApi(detail.value.id, { ...extraTaskForm }, extraTaskAiReview.value?.id)
    } else if (pendingExtraId.value) {
      await submitEmployeeExtraMonthPlanItemDraftApi(detail.value.id, pendingExtraId.value)
    }
    aiDialogVisible.value = false
    extraTaskDialog.value = false
    resetExtraTaskForm()
    await loadDetail()
    ElMessage.success('额外任务已提交直属领导审批')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '额外任务提交失败')
  } finally {
    confirmingExtraTask.value = false
  }
}

async function saveExtraTaskDraft() {
  if (!detail.value || !editingExtraItemId.value || !validateExtraTask()) return
  extraTaskSubmitting.value = true
  try {
    await saveEmployeeExtraMonthPlanItemDraftApi(detail.value.id, editingExtraItemId.value, { ...extraTaskForm })
    extraTaskDialog.value = false
    resetExtraTaskForm()
    await loadDetail()
    ElMessage.success('额外任务草稿已保存')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '额外任务草稿保存失败')
  } finally {
    extraTaskSubmitting.value = false
  }
}

async function withdrawMonthPlan() {
  if (!detail.value) return
  try {
    await ElMessageBox.confirm('撤回后月计划恢复为草稿，可修改后重新提交。确认撤回？', '撤回月计划', { type: 'warning' })
    await withdrawEmployeeMonthPlanApi(detail.value.id)
    ElMessage.success('月计划已撤回为草稿')
    await loadDetail()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error instanceof Error ? error.message : '月计划撤回失败')
      await loadDetail()
    }
  }
}

async function withdrawExtraTask(item: EmployeeMonthPlanItem) {
  if (!detail.value) return
  try {
    await ElMessageBox.confirm('撤回后额外任务恢复为草稿，可修改后重新提交。确认撤回？', '撤回额外任务', { type: 'warning' })
    await withdrawEmployeeExtraMonthPlanItemApi(detail.value.id, item.id)
    ElMessage.success('额外任务已撤回为草稿')
    await loadDetail()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error instanceof Error ? error.message : '额外任务撤回失败')
      await loadDetail()
    }
  }
}

async function resubmitExtraTask(item: EmployeeMonthPlanItem) {
  if (!detail.value) return
  try {
    extraTaskAiReview.value = await checkPlanAiReviewApi('EXTRA_TASK', item.id)
    pendingExtraMode.value = 'existing'
    pendingExtraId.value = item.id
    aiDialogVisible.value = true
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error instanceof Error ? error.message : '额外任务重新提交失败')
      await loadDetail()
    }
  }
}

async function submitAdjustment() {
  if (!detail.value || !adjustmentForm.reason.trim()) {
    ElMessage.warning('请填写申请原因')
    return
  }
  adjustmentSubmitting.value = true
  try {
    await createEmployeePlanAdjustmentApi({
      planType: 'MONTH',
      planId: detail.value.id,
      adjustmentType: adjustmentForm.adjustmentType,
      reason: adjustmentForm.reason.trim(),
      impactText: adjustmentForm.impactText.trim(),
    })
    adjustmentDialog.value = false
    adjustmentForm.reason = ''
    adjustmentForm.impactText = ''
    ElMessage.success('调整申请已提交直属领导处理')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '调整申请提交失败')
  } finally {
    adjustmentSubmitting.value = false
  }
}

async function loadDetail() {
  const planId = getPlanId()
  if (!planId) {
    detail.value = null
    errorMessage.value = '缺少有效的月计划编号，请从员工工作台进入月计划详情'
    return
  }
  loading.value = true
  errorMessage.value = ''
  monthAiReview.value = null
  try {
    detail.value = await getEmployeeMonthPlanDetailApi(planId)
    try {
      monthAiReview.value = await getLatestAiReviewApi('MONTH_PLAN', planId)
    } catch {
      monthAiReview.value = null
    }
  } catch (error) {
    detail.value = null
    errorMessage.value = error instanceof Error ? error.message : '月计划详情加载失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

onMounted(loadDetail)

watch(extraTaskForm, () => {
  if (extraTaskAiReview.value && !extraTaskAiChecking.value) extraTaskAiReviewStale.value = true
}, { deep: true })
</script>

<style scoped>
.month-plan-detail { background:#f5f7f3; }
.plan-cockpit { display:grid; grid-template-columns:minmax(0,1.5fr) minmax(420px,1fr) 220px; gap:0; overflow:hidden; border:1px solid #dce4df; border-radius:16px; background:#fff; box-shadow:var(--shadow-soft); }
.cockpit-main { padding:26px; }
.cockpit-title { display:flex; align-items:flex-start; justify-content:space-between; gap:18px; margin-top:10px; }
.cockpit-title h1 { margin:0; color:var(--ink); font-size:30px; letter-spacing:-.04em; }
.cockpit-title p { max-width:720px; margin:9px 0 0; color:#52645f; font-size:14px; line-height:1.7; }
.identity-line { display:flex; flex-wrap:wrap; align-items:center; gap:10px; margin-top:22px; color:var(--muted); font-size:12px; }
.identity-line i { width:3px; height:3px; border-radius:50%; background:#9aaba5; }
.cockpit-metrics { display:grid; grid-template-columns:repeat(3,1fr); border-left:1px solid #e5eae6; background:#f8faf7; }
.cockpit-metrics div { padding:22px; border-right:1px solid #e5eae6; border-bottom:1px solid #e5eae6; }
.cockpit-metrics span { display:block; color:var(--muted); font-size:11px; }
.cockpit-metrics strong { margin-top:8px; color:var(--ink); font:700 28px/1 "IBM Plex Mono",monospace; }
.cockpit-metrics small { margin-left:3px; color:#73847e; }
.cockpit-actions { display:flex; flex-direction:column; align-items:stretch; justify-content:center; gap:11px; padding:22px; background:#183f4a; color:#fff; }
.cockpit-actions > span { color:#aac1c5; font-size:10px; letter-spacing:.1em; }
.cockpit-actions > strong { margin-bottom:6px; font-size:14px; line-height:1.55; }
.approval-banner { display:flex; align-items:center; justify-content:space-between; gap:18px; margin-top:16px; padding:16px 18px; border:1px solid #ead4b6; border-left:4px solid #c88738; border-radius:11px; background:#fff8ef; }
.approval-banner.is-rejected { border-color:#ecc5c5; border-left-color:#c65346; background:#fff6f6; }
.approval-banner div { display:grid; gap:5px; }
.approval-banner span { color:#9a5e2c; font-size:10px; font-weight:800; letter-spacing:.1em; }
.approval-banner strong { color:#563b32; font-size:13px; line-height:1.65; }
.task-workbench-section { padding:22px; border:1px solid var(--line); border-radius:14px; background:#eef2ed; }
.section-kicker { display:block; margin-bottom:5px; color:var(--blue); font-size:10px; font-weight:800; letter-spacing:.12em; }
.weight-legend { color:#667a73; font:700 12px/1.5 "IBM Plex Mono",monospace; }
.task-card-list { display:grid; gap:14px; }
@media (max-width:1450px) { .plan-cockpit { grid-template-columns:minmax(0,1fr) 360px; } .cockpit-actions { grid-column:1/-1; display:grid; grid-template-columns:1fr auto auto; align-items:center; } .cockpit-actions > span { display:none; } }
.timeline-meta {
  margin: 6px 0 0;
  color: var(--color-muted);
  font-size: 13px;
}
</style>
