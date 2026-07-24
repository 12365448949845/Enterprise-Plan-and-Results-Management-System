<template>
  <section v-loading="loading" class="page-panel">
    <div class="page-header">
      <div>
        <h1 class="page-title">通知待办</h1>
        <p class="page-subtitle">统一处理日计划风险补审、成果确认、申诉和导出类通知。</p>
      </div>
      <div class="toolbar">
        <el-button :icon="Refresh" :disabled="actionBusy" @click="refresh()">刷新</el-button>
        <el-button type="primary" :icon="Bell" :loading="batchAction === 'remind'" :disabled="actionBusy && batchAction !== 'remind'" @click="sendReminder">记录催办</el-button>
      </div>
    </div>

    <div class="status-strip">
      <span class="status-pill"><strong>{{ tableRows.length }}</strong> 条通知</span>
      <span class="status-pill"><strong>{{ selectedRows.length }}</strong> 条已选</span>
      <span class="status-pill"><strong>{{ pendingCount }}</strong> 条待处理</span>
      <span class="status-pill"><strong>{{ reminderCount }}</strong> 次催办记录</span>
    </div>

    <div class="filter-bar">
      <el-select v-model="query.scene" clearable placeholder="待办场景">
        <el-option label="日计划补审" value="日计划补审" />
        <el-option label="成果最终确认" value="成果最终确认" />
        <el-option label="申诉待处理" value="申诉待处理" />
        <el-option label="资料包导出完成" value="资料包导出完成" />
      </el-select>
      <el-select v-model="query.status" clearable placeholder="状态">
        <el-option label="待处理" value="待处理" />
        <el-option label="处理中" value="处理中" />
        <el-option label="已处理" value="已处理" />
      </el-select>
      <el-date-picker v-model="query.deadline" type="date" value-format="YYYY-MM-DD" placeholder="截止日期" />
      <el-button :icon="Search" @click="refresh()">查询</el-button>
      <el-button @click="reset">重置</el-button>
    </div>

    <div v-if="selectedRows.length" class="batch-bar">
      <div>已选择 <strong>{{ selectedRows.length }}</strong> 条待办。</div>
      <div class="batch-actions">
        <el-button size="small" type="primary" :loading="batchAction === 'remind'" :disabled="actionBusy && batchAction !== 'remind'" @click="batchReminder">批量记录催办</el-button>
        <el-button size="small" type="warning" :loading="batchAction === 'escalate'" :disabled="actionBusy && batchAction !== 'escalate'" @click="batchEscalate">批量标记升级</el-button>
        <el-button size="small" :disabled="actionBusy" @click="clearSelection">清空选择</el-button>
      </div>
    </div>

    <el-table
      ref="tableRef"
      :data="tableRows"
      border
      highlight-current-row
      :row-class-name="rowClassName"
      @selection-change="onSelectionChange"
    >
      <el-table-column type="selection" width="48" :selectable="canSelect" />
      <el-table-column prop="scene" label="场景" width="140" />
      <el-table-column prop="trigger" label="触发条件" min-width="180" />
      <el-table-column prop="receiver" label="接收人" width="120" />
      <el-table-column prop="deadline" label="截止时间" width="160" />
      <el-table-column prop="requirement" label="处理要求" min-width="170" />
      <el-table-column prop="impact" label="统计影响" width="140" />
      <el-table-column label="状态" width="110">
        <template #default="{ row }">
          <el-tag :type="row.status === '已处理' ? 'success' : row.status === '处理中' ? 'warning' : 'danger'">{{ row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="250" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" :icon="Position" :loading="processingId === row.id && processingAction === 'handle'" :disabled="actionBusy" @click="handleTodo(row)">处理</el-button>
          <el-button link type="warning" :icon="TopRight" :loading="processingId === row.id && processingAction === 'escalate'" :disabled="row.status === '已处理' || row.escalated || actionBusy" @click="escalate(row)">{{ row.escalated ? '已升级' : '标记升级' }}</el-button>
          <el-button
            v-if="row.objectType === 'EXPORT_TASK'"
            link
            type="success"
            :icon="Check"
            :loading="processingId === row.id && processingAction === 'done'"
            :disabled="row.status === '已处理' || actionBusy"
            @click="completeTodo(row)"
          >
            完成
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-drawer v-model="dayPlanReviewVisible" title="日计划补审" size="680px">
      <div v-loading="dayPlanReviewLoading">
        <template v-if="activeDayPlanReview">
          <el-alert
            v-if="activeDayPlanReview.overdueApproval"
            class="dashboard-alert"
            type="error"
            :closable="false"
            show-icon
            title="该日计划已超过补审时限，请优先处理。"
          />

          <el-descriptions :column="2" border>
            <el-descriptions-item label="员工">
              {{ activeDayPlanReview.employeeName }}（{{ activeDayPlanReview.employeeNo }}）
            </el-descriptions-item>
            <el-descriptions-item label="部门">{{ activeDayPlanReview.orgName }}</el-descriptions-item>
            <el-descriptions-item label="计划日期">{{ activeDayPlanReview.planDate }}</el-descriptions-item>
            <el-descriptions-item label="风险等级">
              <el-tag :type="riskTagType(activeDayPlanReview.riskLevel)">
                {{ riskLevelText(activeDayPlanReview.riskLevel) }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="工作内容" :span="2">{{ activeDayPlanReview.workContent || '未填写' }}</el-descriptions-item>
            <el-descriptions-item label="交付物">{{ activeDayPlanReview.deliverable || '未填写' }}</el-descriptions-item>
            <el-descriptions-item label="直属领导">{{ activeDayPlanReview.leaderName || '-' }}</el-descriptions-item>
            <el-descriptions-item label="初审时间">{{ formatDateTime(activeDayPlanReview.reviewedAt) }}</el-descriptions-item>
            <el-descriptions-item label="领导意见" :span="2">
              {{ activeDayPlanReview.leaderComment || '未填写' }}
            </el-descriptions-item>
            <el-descriptions-item label="当前状态">
              <el-tag :type="dayPlanStatusType(activeDayPlanReview.status)">
                {{ dayPlanStatusText(activeDayPlanReview.status, activeDayPlanReview.reviewStatus) }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="补审截止">{{ formatDateTime(activeDayPlanReview.approvalDueAt) }}</el-descriptions-item>
            <el-descriptions-item v-if="activeDayPlanReview.departmentComment" label="部门补审意见" :span="2">
              {{ activeDayPlanReview.departmentComment }}
            </el-descriptions-item>
          </el-descriptions>

          <el-alert
            v-if="activeDayPlanReview.missingFields.length"
            class="dashboard-alert mt16"
            type="warning"
            :closable="false"
            show-icon
            :title="`必要字段缺失：${activeDayPlanReview.missingFields.map(missingFieldText).join('、')}`"
          />

          <el-form class="mt16" label-position="top">
            <el-form-item label="部门补审意见">
              <el-input
                v-model="dayPlanReviewForm.comment"
                type="textarea"
                :rows="5"
                maxlength="500"
                show-word-limit
                :disabled="!canProcessDayPlanReview"
                placeholder="填写风险判断、处理结论和员工后续动作"
              />
            </el-form-item>
            <div class="appeal-actions">
              <el-button
                type="danger"
                :icon="CircleClose"
                :loading="dayPlanReviewSubmitting"
                :disabled="!canProcessDayPlanReview"
                @click="processDayPlanReview('REJECT')"
              >
                退回补充
              </el-button>
              <el-button
                type="success"
                :icon="CircleCheck"
                :loading="dayPlanReviewSubmitting"
                :disabled="!canProcessDayPlanReview || activeDayPlanReview.missingFields.length > 0"
                @click="processDayPlanReview('APPROVE')"
              >
                复核通过
              </el-button>
            </div>
          </el-form>
        </template>
      </div>
    </el-drawer>

    <el-drawer v-model="appealVisible" title="申诉处理" size="620px">
      <div v-loading="appealLoading">
        <template v-if="activeAppeal">
          <el-descriptions :column="2" border>
            <el-descriptions-item label="申诉编号">{{ activeAppeal.appealNo }}</el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag :type="appealStatusType(activeAppeal.status)">{{ appealStatusText(activeAppeal.status) }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="员工">{{ activeAppeal.employeeName }}</el-descriptions-item>
            <el-descriptions-item label="部门">{{ activeAppeal.orgName }}</el-descriptions-item>
            <el-descriptions-item label="申诉标题" :span="2">{{ activeAppeal.title }}</el-descriptions-item>
            <el-descriptions-item label="申诉原因" :span="2">{{ activeAppeal.reason }}</el-descriptions-item>
            <el-descriptions-item label="关联成果">{{ activeAppeal.resultTitle }}</el-descriptions-item>
            <el-descriptions-item label="完成比例">{{ activeAppeal.completionRate }}%</el-descriptions-item>
            <el-descriptions-item label="已有意见" :span="2">{{ activeAppeal.handleComment || '暂无' }}</el-descriptions-item>
          </el-descriptions>

          <div class="appeal-actions mt16">
            <el-button :icon="Download" :loading="appealDownloading" @click="downloadAppealPackage">下载资料包</el-button>
            <el-button
              v-if="activeAppeal.status === 'SUBMITTED'"
              type="primary"
              :loading="appealSubmitting"
              @click="acceptAppeal"
            >
              受理申诉
            </el-button>
          </div>

          <el-form class="mt16" label-position="top">
            <el-form-item label="处理结论">
              <el-radio-group v-model="appealForm.decision">
                <el-radio-button label="ACCEPT">接受申诉</el-radio-button>
                <el-radio-button label="MAINTAIN">维持原结果</el-radio-button>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="处理意见">
              <el-input
                v-model="appealForm.comment"
                type="textarea"
                :rows="5"
                maxlength="500"
                show-word-limit
                placeholder="填写事实依据、处理结论和后续安排"
              />
            </el-form-item>
            <el-button
              type="success"
              :loading="appealSubmitting"
              :disabled="activeAppeal.status === 'RESOLVED' || activeAppeal.status === 'CLOSED'"
              @click="resolveAppeal"
            >
              提交处理意见
            </el-button>
          </el-form>
        </template>
      </div>
    </el-drawer>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Bell, Check, CircleCheck, CircleClose, Download, Position, Refresh, Search, TopRight } from '@element-plus/icons-vue'
import {
  acceptDepartmentAppealApi,
  approveDepartmentDayPlanReviewApi,
  batchEscalateTodosApi,
  batchRemindTodosApi,
  doneTodoApi,
  downloadDepartmentAppealPackageApi,
  escalateTodoApi,
  getDepartmentDayPlanReviewApi,
  getDepartmentAppealApi,
  listTodosApi,
  readTodoApi,
  rejectDepartmentDayPlanReviewApi,
  resolveDepartmentAppealApi,
  type DepartmentAppeal,
  type DepartmentDayPlanReview,
} from '@/api/department'
import { errorMessage } from '@/api/performance'
import { useAutoQuery } from '@/composables/useAutoQuery'
import { saveBlob } from '@/utils/download'
import { mapTodo, todoSceneCodes, todoStatusCodes } from '@/views/performanceAdapters'

type TodoRow = ReturnType<typeof mapTodo>

const router = useRouter()
const tableRef = ref()
const query = reactive({
  scene: '',
  status: '',
  deadline: '',
})
const tableRows = ref<TodoRow[]>([])
const selectedRows = ref<TodoRow[]>([])
const loading = ref(false)
const processingId = ref('')
const processingAction = ref('')
const batchAction = ref('')
let refreshRequestId = 0
const dayPlanReviewVisible = ref(false)
const dayPlanReviewLoading = ref(false)
const dayPlanReviewSubmitting = ref(false)
const activeDayPlanReview = ref<DepartmentDayPlanReview | null>(null)
const appealVisible = ref(false)
const appealLoading = ref(false)
const appealSubmitting = ref(false)
const appealDownloading = ref(false)
const activeAppeal = ref<DepartmentAppeal | null>(null)
const activeTodo = ref<TodoRow | null>(null)
const appealForm = reactive({
  decision: 'ACCEPT',
  comment: '',
})
const dayPlanReviewForm = reactive({ comment: '' })
const pendingCount = computed(() => tableRows.value.filter((row) => row.status === '待处理').length)
const reminderCount = computed(() => tableRows.value.reduce((sum, row) => sum + row.remindCount, 0))
const canProcessDayPlanReview = computed(() => activeDayPlanReview.value?.status === 'PENDING'
  && activeDayPlanReview.value.reviewStatus === 'RISK_MARKED')
const actionBusy = computed(() => Boolean(processingId.value || batchAction.value)
  || dayPlanReviewSubmitting.value || appealSubmitting.value || appealDownloading.value)
const autoQuery = useAutoQuery(
  () => [query.scene, query.status, query.deadline],
  () => refresh(false),
)

async function refresh(showMessage = true) {
  const requestId = ++refreshRequestId
  loading.value = true
  try {
    const data = await listTodosApi({
      sceneCode: todoSceneCodes[query.scene] || undefined,
      status: todoStatusCodes[query.status] || undefined,
    })
    const rows = data.map(mapTodo)
    if (requestId !== refreshRequestId) return
    tableRows.value = query.deadline ? rows.filter((row) => row.deadline.startsWith(query.deadline)) : rows
    clearSelection()
    if (showMessage) ElMessage.success('通知待办已刷新')
  } catch (error) {
    if (requestId !== refreshRequestId) return
    ElMessage.error(errorMessage(error))
  } finally {
    if (requestId === refreshRequestId) loading.value = false
  }
}

async function reset() {
  autoQuery.pause()
  query.scene = ''
  query.status = ''
  query.deadline = ''
  await refresh(false)
  autoQuery.resume()
  ElMessage.success('筛选条件已重置')
}

async function sendReminder() {
  const rows = selectedRows.value.length ? [...selectedRows.value] : tableRows.value.filter((row) => row.status !== '已处理')
  await remindRows(rows)
}

async function handleTodo(row: TodoRow) {
  if (actionBusy.value) return
  processingId.value = row.id
  processingAction.value = 'handle'
  try {
    if (row.status === '待处理') {
      await readTodoApi(row.id)
      row.status = '处理中'
    }
    if (row.objectType === 'APPEAL') {
      await openAppeal(row)
      return
    }
    if (row.objectType === 'DAY_PLAN') {
      await openDayPlanReview(row)
      return
    }
    const queryKey = {
      MONTH_PLAN: 'planId',
      DAY_PLAN: 'dayPlanId',
      RESULT: 'resultId',
      EXPORT_TASK: 'taskId',
    }[row.objectType]
    await router.push({
      path: row.route,
      query: queryKey ? { [queryKey]: row.objectId } : undefined,
    })
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    processingId.value = ''
    processingAction.value = ''
  }
}

async function openDayPlanReview(row: TodoRow) {
  dayPlanReviewVisible.value = true
  dayPlanReviewLoading.value = true
  activeTodo.value = row
  try {
    activeDayPlanReview.value = await getDepartmentDayPlanReviewApi(row.objectId)
    dayPlanReviewForm.comment = activeDayPlanReview.value.departmentComment || ''
  } finally {
    dayPlanReviewLoading.value = false
  }
}

async function processDayPlanReview(decision: 'APPROVE' | 'REJECT') {
  if (!activeDayPlanReview.value || !canProcessDayPlanReview.value || dayPlanReviewSubmitting.value) return
  const comment = dayPlanReviewForm.comment.trim()
  if (decision === 'REJECT' && !comment) {
    ElMessage.warning('退回补充时必须填写原因')
    return
  }
  if (decision === 'APPROVE' && activeDayPlanReview.value.missingFields.length) {
    ElMessage.warning('必要字段缺失，不能复核通过')
    return
  }
  try {
    await ElMessageBox.confirm(
      decision === 'APPROVE' ? '确认该日计划风险已处理并复核通过？' : '确认将该日计划退回员工补充？',
      decision === 'APPROVE' ? '复核通过' : '退回补充',
      { type: decision === 'APPROVE' ? 'success' : 'warning', confirmButtonText: '确认', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  dayPlanReviewSubmitting.value = true
  try {
    const result = decision === 'APPROVE'
      ? await approveDepartmentDayPlanReviewApi(activeDayPlanReview.value.id, { comment })
      : await rejectDepartmentDayPlanReviewApi(activeDayPlanReview.value.id, { comment })
    activeDayPlanReview.value.status = decision === 'APPROVE' ? 'APPROVED' : 'REJECTED'
    activeDayPlanReview.value.reviewStatus = decision === 'APPROVE' ? 'RISK_RESOLVED' : 'SUPPLEMENT_REQUIRED'
    activeDayPlanReview.value.departmentComment = comment || '部门复核通过'
    activeDayPlanReview.value.departmentReviewedAt = new Date().toISOString()
    if (activeTodo.value) activeTodo.value.status = '已处理'
    ElMessage.success(result.message)
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    dayPlanReviewSubmitting.value = false
  }
}

async function escalate(row: TodoRow) {
  if (row.status === '已处理' || row.escalated || actionBusy.value) return
  try {
    await ElMessageBox.confirm(`确认将待办“${row.scene}”标记为需要升级处理？`, '标记升级', { type: 'warning' })
    processingId.value = row.id
    processingAction.value = 'escalate'
    const result = await escalateTodoApi(row.id)
    row.status = '处理中'
    row.escalated = true
    ElMessage.success(result.message)
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
    ElMessage.error(errorMessage(error))
  } finally {
    processingId.value = ''
    processingAction.value = ''
  }
}

async function completeTodo(row: TodoRow) {
  if (row.status === '已处理' || actionBusy.value) return
  try {
    await ElMessageBox.confirm(`确认将待办“${row.scene}”标记为已处理？`, '完成待办', { type: 'warning' })
    processingId.value = row.id
    processingAction.value = 'done'
    const result = await doneTodoApi(row.id)
    row.status = '已处理'
    ElMessage.success(result.message)
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
    ElMessage.error(errorMessage(error))
  } finally {
    processingId.value = ''
    processingAction.value = ''
  }
}

function onSelectionChange(rows: TodoRow[]) {
  selectedRows.value = rows
}

function clearSelection() {
  tableRef.value?.clearSelection?.()
  selectedRows.value = []
}

async function batchReminder() {
  await remindRows([...selectedRows.value])
}

async function batchEscalate() {
  const rows = selectedRows.value.filter((row) => !row.escalated)
  if (!rows.length) {
    ElMessage.warning('所选待办均已标记升级')
    return
  }
  if (actionBusy.value) return
  try {
    await ElMessageBox.confirm(`确认将选中的 ${rows.length} 条待办标记为需要升级处理？`, '批量标记升级', { type: 'warning' })
    batchAction.value = 'escalate'
    const results = await batchEscalateTodosApi({ ids: rows.map((row) => row.id), action: 'ESCALATE' })
    rows.forEach((row) => {
      row.status = '处理中'
      row.escalated = true
    })
    clearSelection()
    ElMessage.success(`已将 ${results.length} 条待办标记为升级处理`)
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
    ElMessage.error(errorMessage(error))
  } finally {
    batchAction.value = ''
  }
}

function rowClassName({ row }: { row: TodoRow }) {
  if (row.status === '待处理') return 'danger-row'
  if (row.status === '处理中') return 'warning-row'
  return ''
}

function canSelect(row: TodoRow) {
  return row.status !== '已处理' && !actionBusy.value
}

async function remindRows(rows: TodoRow[]) {
  if (!rows.length) {
    ElMessage.warning('当前没有可记录催办的待办')
    return
  }
  if (actionBusy.value) return
  try {
    await ElMessageBox.confirm(`确认记录 ${rows.length} 条待办的催办动作？`, '记录催办', { type: 'warning' })
    batchAction.value = 'remind'
    const results = await batchRemindTodosApi({ ids: rows.map((row) => row.id), action: 'REMIND' })
    rows.forEach((row) => {
      row.status = '处理中'
      row.remindCount += 1
    })
    clearSelection()
    ElMessage.success(`已保存 ${results.length} 条催办记录`)
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
    ElMessage.error(errorMessage(error))
  } finally {
    batchAction.value = ''
  }
}

async function openAppeal(row: TodoRow) {
  appealVisible.value = true
  appealLoading.value = true
  activeTodo.value = row
  try {
    activeAppeal.value = await getDepartmentAppealApi(row.objectId)
    appealForm.comment = activeAppeal.value.handleComment || ''
    appealForm.decision = 'ACCEPT'
  } finally {
    appealLoading.value = false
  }
}

async function acceptAppeal() {
  if (!activeAppeal.value || appealSubmitting.value) return
  appealSubmitting.value = true
  try {
    const result = await acceptDepartmentAppealApi(String(activeAppeal.value.id), { comment: appealForm.comment })
    activeAppeal.value.status = 'PROCESSING'
    if (activeTodo.value) activeTodo.value.status = '处理中'
    ElMessage.success(result.message)
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    appealSubmitting.value = false
  }
}

async function resolveAppeal() {
  if (!activeAppeal.value || appealSubmitting.value) return
  if (!appealForm.comment.trim()) {
    ElMessage.warning('请填写申诉处理意见')
    return
  }
  appealSubmitting.value = true
  try {
    const result = await resolveDepartmentAppealApi(String(activeAppeal.value.id), {
      decision: appealForm.decision,
      comment: appealForm.comment.trim(),
      notifyEmployee: true,
      keepEvidenceChain: true,
    })
    activeAppeal.value.status = 'RESOLVED'
    activeAppeal.value.handleComment = `${appealForm.decision === 'ACCEPT' ? '接受申诉' : '维持原结果'}：${appealForm.comment.trim()}`
    if (activeTodo.value) activeTodo.value.status = '已处理'
    ElMessage.success(result.message)
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    appealSubmitting.value = false
  }
}

async function downloadAppealPackage() {
  if (!activeAppeal.value) return
  appealDownloading.value = true
  try {
    const blob = await downloadDepartmentAppealPackageApi(String(activeAppeal.value.id))
    saveBlob(blob, `${activeAppeal.value.appealNo}.zip`)
    ElMessage.success('申诉资料包已下载')
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    appealDownloading.value = false
  }
}

function appealStatusText(status: string) {
  return { SUBMITTED: '待受理', PROCESSING: '处理中', RESOLVED: '已处理', CLOSED: '已关闭' }[status] || status
}

function appealStatusType(status: string) {
  if (status === 'RESOLVED') return 'success'
  if (status === 'PROCESSING') return 'primary'
  if (status === 'CLOSED') return 'info'
  return 'warning'
}

function missingFieldText(field: string) {
  return { workContent: '工作内容', deliverable: '交付物' }[field] || field
}

function riskLevelText(level: string) {
  return { LOW: '低风险', MEDIUM: '中风险', HIGH: '高风险' }[level] || level
}

function riskTagType(level: string) {
  if (level === 'HIGH') return 'danger'
  if (level === 'MEDIUM') return 'warning'
  return 'info'
}

function dayPlanStatusText(status: string, reviewStatus: string) {
  if (reviewStatus === 'RISK_RESOLVED') return '风险已复核'
  if (reviewStatus === 'SUPPLEMENT_REQUIRED') return '已退回补充'
  return { PENDING: '待补审', APPROVED: '已通过', REJECTED: '已驳回' }[status] || status
}

function dayPlanStatusType(status: string) {
  if (status === 'APPROVED') return 'success'
  if (status === 'REJECTED') return 'danger'
  return 'warning'
}

function formatDateTime(value?: string) {
  return value ? value.replace('T', ' ').slice(0, 16) : '-'
}

onMounted(async () => {
  await refresh(false)
  autoQuery.resume()
})
</script>
