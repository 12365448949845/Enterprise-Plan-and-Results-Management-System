<template>
  <section v-loading="loading" class="page-panel">
    <div class="page-header">
      <div>
        <h1 class="page-title">{{ isLeader ? '月计划审批' : '月计划查看' }}</h1>
        <p class="page-subtitle">{{ isLeader ? '审批直属下属提交的月计划，处理结果同步给员工和部门负责人。' : '只读查看本部门待审批、已通过和已驳回的月计划。' }}</p>
      </div>
      <div class="toolbar">
        <el-button :icon="HomeFilled" @click="router.push('/')">首页</el-button>
        <el-button v-if="!isLeader" :icon="Files" @click="openDesignReview">设计审查</el-button>
        <el-button v-if="!isLeader" type="primary" :icon="Download" :loading="exporting" :disabled="loading" @click="exportList">清单导出</el-button>
      </div>
    </div>

    <AiReviewPanel
      class="mt16"
      :review="null"
      :display-report="false"
      title="AI计划审阅辅助"
      empty-text="员工提交月计划前会自动生成AI检查报告；点击列表中的“详情与AI”，可查看任务完整性、内部一致性和风险判断依据。"
    />

    <div class="status-strip mt16">
      <span class="status-pill"><strong>{{ total }}</strong> 条计划</span>
      <span v-if="isLeader" class="status-pill"><strong>{{ selectedRows.length }}</strong> 条已选</span>
      <span class="status-pill"><strong>{{ pendingCount }}</strong> 条本页待审批</span>
      <span v-if="isLeader" class="status-pill"><strong>{{ blockedCount }}</strong> 条本页不可通过</span>
      <span v-else class="status-pill">部门负责人仅查看，不参与审批</span>
    </div>

    <div class="filter-bar">
      <el-date-picker v-model="query.year" type="year" value-format="YYYY" placeholder="开发年份" />
      <el-select v-model="query.month" clearable placeholder="月份">
        <el-option v-for="item in monthOptions" :key="item.value" :label="item.label" :value="item.value" />
      </el-select>
      <el-select
        v-model="selectedOrgId"
        clearable
        :loading="orgLoading"
        :disabled="!orgOptions.length"
        placeholder="全部授权组织"
      >
        <el-option v-for="item in orgOptions" :key="item.value" :label="item.label" :value="item.value" />
      </el-select>
      <el-select v-model="query.status" clearable placeholder="审批状态">
        <el-option label="待审批" value="待审批" />
        <el-option label="已通过" value="已通过" />
        <el-option label="已驳回" value="已驳回" />
      </el-select>
      <el-input v-model="query.keyword" clearable placeholder="员工/计划内容" />
      <el-button :icon="Search" @click="refresh()">查询</el-button>
      <el-button @click="reset">重置</el-button>
    </div>

    <div v-if="isLeader && selectedRows.length" class="batch-bar">
      <div>已选择 <strong>{{ selectedRows.length }}</strong> 条月计划。</div>
      <div class="batch-actions">
        <el-button size="small" type="success" :loading="batchAction === 'approve'" :disabled="actionBusy && batchAction !== 'approve'" @click="batchApprove">批量通过</el-button>
        <el-button size="small" type="danger" :loading="batchAction === 'reject'" :disabled="actionBusy && batchAction !== 'reject'" @click="batchReject">批量驳回</el-button>
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
      @row-dblclick="openDetail"
    >
      <el-table-column v-if="isLeader" type="selection" width="48" :selectable="canSelect" />
      <el-table-column prop="id" label="计划编号" width="150" />
      <el-table-column prop="employee" label="员工" width="100" />
      <el-table-column prop="department" label="部门" min-width="160" />
      <el-table-column prop="month" label="月份" width="100" />
      <el-table-column prop="content" label="计划内容" min-width="220" show-overflow-tooltip />
      <el-table-column prop="deliverable" label="交付物" width="140">
        <template #default="{ row }">
          <span :class="{ 'text-danger': !row.deliverable }">{{ row.deliverable || '未填写' }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="leaderComment" label="直属领导审批意见" min-width="170" />
      <el-table-column label="系统检查" width="130">
        <template #default="{ row }">
          <el-tag :type="row.aiCheck === '正常' ? 'success' : 'danger'">{{ row.aiCheck }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="110">
        <template #default="{ row }">
          <el-tag :type="statusTag(row.status)">{{ row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" :width="isLeader ? 320 : 125" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" :icon="View" @click="openDetail(row)">详情与AI</el-button>
          <el-tooltip v-if="isLeader"
            :disabled="canApprove(row)"
            content="必要字段缺失或已审批，不能直接通过"
            placement="top"
          >
            <span>
              <el-button link type="success" :icon="Check" :loading="processingId === row.id && processingAction === 'approve'" :disabled="!canApprove(row) || actionBusy" @click="approve(row)">通过</el-button>
            </span>
          </el-tooltip>
          <el-button v-if="isLeader" link type="danger" :icon="Close" :loading="processingId === row.id && processingAction === 'reject'" :disabled="!canProcess(row) || actionBusy" @click="reject(row)">驳回</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination-row">
      <el-pagination
        v-model:current-page="currentPage"
        :page-size="pageSize"
        layout="prev, pager, next, total"
        :total="total"
        @current-change="handlePageChange"
      />
    </div>

    <el-drawer v-model="detailVisible" title="月计划详情" size="820px" @closed="closeDetail">
      <div class="drawer-stack">
        <el-skeleton v-if="detailLoading" :rows="7" animated />
        <template v-else-if="activeRow">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="计划编号">{{ activeRow.planNo }}</el-descriptions-item>
          <el-descriptions-item label="员工">{{ activeRow.employee }}</el-descriptions-item>
          <el-descriptions-item label="部门">{{ activeRow.department }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="statusTag(activeRow.status)">{{ activeRow.status }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="系统检查">
            <el-tag :type="activeRow.aiCheck === '正常' ? 'success' : 'danger'">{{ activeRow.aiCheck }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="计划内容">{{ activeRow.content }}</el-descriptions-item>
          <el-descriptions-item label="直属领导审批意见">{{ activeRow.leaderComment || '待审批' }}</el-descriptions-item>
          <el-descriptions-item label="审批人">{{ activeRow.approverName }}</el-descriptions-item>
          <el-descriptions-item label="审批时间">{{ activeRow.approvedAt || '尚未处理' }}</el-descriptions-item>
        </el-descriptions>
        <div class="section-header mt16">
          <div>
            <h2>计划事项</h2>
            <p>逐项核对任务内容、交付物、截止日期和绩效权重。</p>
          </div>
        </div>
        <el-table :data="activeRow.items" border empty-text="暂无计划事项">
          <el-table-column prop="sortNo" label="#" width="55" />
          <el-table-column prop="taskName" label="任务" min-width="150" show-overflow-tooltip />
          <el-table-column prop="taskContent" label="任务内容" min-width="190" show-overflow-tooltip />
          <el-table-column prop="deliverable" label="交付物" min-width="140" show-overflow-tooltip />
          <el-table-column label="绩效权重" width="105">
            <template #default="{ row }">{{ row.performanceWeight }}%</template>
          </el-table-column>
          <el-table-column prop="deadline" label="截止日期" width="120" />
        </el-table>
        <AiReviewPanel class="mt16" :review="aiReview" compact empty-text="该月计划尚未生成AI检查记录。" />
        <div v-if="isLeader" class="batch-bar">
          <div>当前计划 <strong>{{ activeRow.status }}</strong></div>
          <div class="batch-actions">
            <el-tooltip
              :disabled="canApprove(activeRow)"
              content="必要字段缺失或已审批，不能直接通过"
              placement="top"
            >
              <span>
                <el-button
                  type="success"
                  :icon="Check"
                  :loading="processingId === activeRow.id && processingAction === 'approve'"
                  :disabled="!canApprove(activeRow) || actionBusy"
                  @click="approve(activeRow)"
                >
                  通过
                </el-button>
              </span>
            </el-tooltip>
            <el-button
              type="danger"
              :icon="Close"
              :loading="processingId === activeRow.id && processingAction === 'reject'"
              :disabled="!canProcess(activeRow) || actionBusy"
              @click="reject(activeRow)"
            >
              驳回
            </el-button>
          </div>
        </div>
        </template>
      </div>
    </el-drawer>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Check, Close, Download, Files, HomeFilled, Search, View } from '@element-plus/icons-vue'
import {
  createExportTaskApi,
  getMonthPlanApprovalApi,
  pageMonthPlanApprovalsApi,
} from '@/api/department'
import {
  approveLeaderMonthPlanApi,
  batchApproveLeaderMonthPlansApi,
  batchRejectLeaderMonthPlansApi,
  getLeaderMonthPlanApprovalApi,
  pageLeaderMonthPlanApprovalsApi,
  rejectLeaderMonthPlanApi,
} from '@/api/leader'
import { currentMonth, currentYear, errorMessage, periodRange } from '@/api/performance'
import { useAutoQuery } from '@/composables/useAutoQuery'
import { useDepartmentOrgScope } from '@/composables/useDepartmentOrgScope'
import { useLeaderOrgScope } from '@/composables/useLeaderOrgScope'
import { approvalStatusCodes, mapMonthApproval } from '@/views/performanceAdapters'
import { getLatestAiReviewApi, type AiReview } from '@/api/aiReview'
import AiReviewPanel from '@/components/AiReviewPanel.vue'

type MonthApprovalRow = ReturnType<typeof mapMonthApproval>

const router = useRouter()
const route = useRoute()
const isLeader = computed(() => route.path.startsWith('/leader'))
const tableRef = ref()
const departmentScope = useDepartmentOrgScope(false)
const leaderScope = useLeaderOrgScope()
const orgOptions = computed(() => isLeader.value ? leaderScope.orgOptions.value : departmentScope.orgOptions.value)
const orgLoading = computed(() => isLeader.value ? leaderScope.orgLoading.value : departmentScope.orgLoading.value)
const selectedOrgId = computed<number | undefined>({
  get: () => isLeader.value ? leaderScope.scopeOrgId.value : departmentScope.selectedOrgId.value,
  set: (value) => {
    if (isLeader.value) leaderScope.scopeOrgId.value = value
    else departmentScope.selectedOrgId.value = value
  },
})
const loadOrgScope = () => isLeader.value ? leaderScope.loadOrgScope() : departmentScope.loadOrgScope()
const resetOrgScope = () => isLeader.value ? leaderScope.resetOrgScope() : departmentScope.resetOrgScope()
const query = reactive({
  year: currentYear(),
  month: currentMonth().slice(5),
  status: '',
  keyword: '',
})
const tableRows = ref<MonthApprovalRow[]>([])
const selectedRows = ref<MonthApprovalRow[]>([])
const activeRow = ref<MonthApprovalRow | null>(null)
const aiReview = ref<AiReview | null>(null)
const detailVisible = ref(false)
const detailLoading = ref(false)
const loading = ref(false)
const exporting = ref(false)
const processingId = ref('')
const processingAction = ref('')
const batchAction = ref('')
let refreshRequestId = 0
let detailRequestId = 0
const currentPage = ref(1)
const pageSize = 10
const total = ref(0)
const pendingCount = computed(() => tableRows.value.filter((row) => row.status === '待审批').length)
const blockedCount = computed(() => tableRows.value.filter((row) => row.status === '待审批' && row.aiCheck !== '正常').length)
const actionBusy = computed(() => Boolean(processingId.value || batchAction.value))
const selectedPeriodMonth = computed(() => query.year && query.month ? `${query.year}-${query.month}` : '')
const autoQuery = useAutoQuery(
  () => [selectedOrgId.value, query.year, query.month, query.status, query.keyword],
  () => refresh(false),
)
const monthOptions = Array.from({ length: 12 }, (_, index) => {
  const value = String(index + 1).padStart(2, '0')
  return { label: `${index + 1} 月`, value }
})

function statusTag(status: string) {
  if (status === '已通过') return 'success'
  if (status === '已驳回' || status === '待补正') return 'danger'
  return 'warning'
}

async function refresh(showMessage = true, resetPage = true) {
  if (resetPage) currentPage.value = 1
  const requestId = ++refreshRequestId
  loading.value = true
  try {
    const queryParams = {
      planYear: query.year ? Number(query.year) : undefined,
      planMonth: query.month ? Number(query.month) : undefined,
      orgId: selectedOrgId.value,
      status: approvalStatusCodes[query.status] || undefined,
      keyword: query.keyword || undefined,
      pageNo: currentPage.value,
      pageSize,
    }
    const data = isLeader.value
      ? await pageLeaderMonthPlanApprovalsApi(queryParams)
      : await pageMonthPlanApprovalsApi(queryParams)
    if (requestId !== refreshRequestId) return
    tableRows.value = data.items.map(mapMonthApproval)
    total.value = data.total
    clearSelection()
    if (showMessage) ElMessage.success(isLeader.value ? '月计划审批列表已刷新' : '月计划查看列表已刷新')
  } catch (error) {
    if (requestId !== refreshRequestId) return
    ElMessage.error(errorMessage(error))
  } finally {
    if (requestId === refreshRequestId) loading.value = false
  }
}

async function handlePageChange() {
  await refresh(false, false)
}

async function reset() {
  autoQuery.pause()
  Object.assign(query, {
    year: currentYear(),
    month: currentMonth().slice(5),
    status: '',
    keyword: '',
  })
  resetOrgScope()
  await refresh(false)
  autoQuery.resume()
  ElMessage.success('筛选条件已重置')
}

async function exportList() {
  if (!selectedPeriodMonth.value || exporting.value) {
    if (!selectedPeriodMonth.value) ElMessage.warning('请选择导出年份和月份')
    return
  }
  exporting.value = true
  try {
    const task = await createExportTaskApi({
      dimensionType: 'MONTH_PLAN_APPROVAL_LIST',
      dimensionId: selectedOrgId.value == null ? undefined : String(selectedOrgId.value),
      periodType: 'MONTH',
      ...periodRange(selectedPeriodMonth.value),
      formats: ['PDF'],
      includeEvidence: false,
    })
    ElMessage.success(`月计划查看清单导出任务已创建：${task.id}`)
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    exporting.value = false
  }
}

function openDesignReview() {
  router.push('/department/standard')
}

async function openDetail(row: MonthApprovalRow) {
  await openDetailById(row.id)
}

async function openDetailById(id: string) {
  const requestId = ++detailRequestId
  detailVisible.value = true
  detailLoading.value = true
  activeRow.value = null
  aiReview.value = null
  try {
    const response = isLeader.value
      ? await getLeaderMonthPlanApprovalApi(id)
      : await getMonthPlanApprovalApi(id)
    const detail = mapMonthApproval(response)
    if (requestId !== detailRequestId) return
    activeRow.value = detail
    aiReview.value = await getLatestAiReviewApi('MONTH_PLAN', Number(id))
  } catch (error) {
    if (requestId !== detailRequestId) return
    detailVisible.value = false
    ElMessage.error(errorMessage(error))
  } finally {
    if (requestId === detailRequestId) detailLoading.value = false
  }
}

function closeDetail() {
  detailRequestId += 1
  activeRow.value = null
  aiReview.value = null
  detailLoading.value = false
}

function canApprove(row: MonthApprovalRow) {
  return row.aiCheck === '正常' && canProcess(row)
}

function canProcess(row: MonthApprovalRow) {
  return row.status === '待审批'
}

function canSelect(row: MonthApprovalRow) {
  return canProcess(row) && !actionBusy.value
}

function rowClassName({ row }: { row: MonthApprovalRow }) {
  if (row.aiCheck !== '正常') return 'danger-row clickable-row'
  if (row.status === '待审批') return 'warning-row clickable-row'
  return 'clickable-row'
}

function onSelectionChange(rows: MonthApprovalRow[]) {
  selectedRows.value = rows
}

function clearSelection() {
  tableRef.value?.clearSelection?.()
  selectedRows.value = []
}

function syncRowStatus(id: string, status: string) {
  const tableRow = tableRows.value.find((row) => row.id === id)
  if (tableRow) tableRow.status = status
  if (activeRow.value?.id === id) activeRow.value.status = status
}

async function batchApprove() {
  if (actionBusy.value || !selectedRows.value.length) return
  const blocked = selectedRows.value.filter((row) => !canApprove(row)).length
  if (blocked) {
    ElMessage.warning(`${blocked} 条计划存在字段缺失或已审批，需先处理异常`)
    return
  }
  try {
    await ElMessageBox.confirm(`确认批量通过 ${selectedRows.value.length} 条月计划？`, '批量审批', {
      confirmButtonText: '批量通过',
      cancelButtonText: '取消',
      type: 'success',
    })
    batchAction.value = 'approve'
    const rows = [...selectedRows.value]
    const results = await batchApproveLeaderMonthPlansApi({ ids: rows.map((row) => row.id), comment: '批量审批通过。' })
    rows.forEach((row) => syncRowStatus(row.id, '已通过'))
    clearSelection()
    await refresh(false, false)
    ElMessage.success(`${results.length} 条月计划已批量审批通过`)
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
    ElMessage.error(errorMessage(error))
  } finally {
    batchAction.value = ''
  }
}

async function batchReject() {
  if (actionBusy.value || !selectedRows.value.length) return
  try {
    const { value } = await ElMessageBox.prompt('请输入批量驳回原因', '批量驳回月计划', {
      confirmButtonText: '批量驳回',
      cancelButtonText: '取消',
      inputType: 'textarea',
      inputValidator: (text) => {
        if (!text?.trim()) return '请输入驳回原因'
        return text.trim().length <= 500 || '驳回原因不能超过 500 个字符'
      },
    })
    batchAction.value = 'reject'
    const rows = [...selectedRows.value]
    const results = await batchRejectLeaderMonthPlansApi({
      ids: rows.map((row) => row.id),
      comment: value,
      notifyEmployee: true,
    })
    rows.forEach((row) => syncRowStatus(row.id, '已驳回'))
    clearSelection()
    await refresh(false, false)
    ElMessage.success(`${results.length} 条月计划已批量驳回`)
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
    ElMessage.error(errorMessage(error))
  } finally {
    batchAction.value = ''
  }
}

async function approve(row: MonthApprovalRow) {
  if (!canApprove(row) || actionBusy.value) return
  try {
    await ElMessageBox.confirm(`确认通过 ${row.employee} 的月计划？`, '月计划审批', {
      confirmButtonText: '通过',
      cancelButtonText: '取消',
      type: 'success',
    })
    processingId.value = row.id
    processingAction.value = 'approve'
    const result = await approveLeaderMonthPlanApi(row.id, { decision: 'APPROVED', comment: '审批通过。' })
    syncRowStatus(row.id, '已通过')
    await refresh(false, false)
    ElMessage.success(result.message)
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
    ElMessage.error(errorMessage(error))
  } finally {
    processingId.value = ''
    processingAction.value = ''
  }
}

async function reject(row: MonthApprovalRow) {
  if (!canProcess(row) || actionBusy.value) return
  try {
    const { value } = await ElMessageBox.prompt(`请输入驳回 ${row.employee} 月计划的原因`, '驳回月计划', {
      confirmButtonText: '驳回',
      cancelButtonText: '取消',
      inputType: 'textarea',
      inputValidator: (text) => {
        if (!text?.trim()) return '请输入驳回原因'
        return text.trim().length <= 500 || '驳回原因不能超过 500 个字符'
      },
    })
    processingId.value = row.id
    processingAction.value = 'reject'
    const result = await rejectLeaderMonthPlanApi(row.id, { decision: 'REJECTED', comment: value, notifyEmployee: true })
    syncRowStatus(row.id, '已驳回')
    await refresh(false, false)
    ElMessage.success(result.message)
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
    ElMessage.error(errorMessage(error))
  } finally {
    processingId.value = ''
    processingAction.value = ''
  }
}

onMounted(async () => {
  try {
    await loadOrgScope()
    await refresh(false)
    autoQuery.resume()
    if (typeof route.query.planId === 'string') {
      await openDetailById(route.query.planId)
    }
  } catch (error) {
    ElMessage.error(errorMessage(error))
  }
})
</script>
