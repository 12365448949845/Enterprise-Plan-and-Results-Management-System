<template>
  <section v-loading="loading" class="page-panel">
    <div class="page-header">
      <div>
        <h1 class="page-title">暂停撤销</h1>
        <p class="page-subtitle">处理原计划暂停、撤销和新计划关联，保留处理记录与证据链。</p>
      </div>
      <div class="toolbar">
        <el-button :icon="Refresh" :disabled="submitting" @click="refresh()">刷新</el-button>
        <el-button type="primary" :icon="Download" :loading="exporting" :disabled="loading || submitting" @click="exportList">导出调整记录</el-button>
      </div>
    </div>

    <div class="status-strip">
      <span class="status-pill"><strong>{{ tableRows.length }}</strong> 条调整记录</span>
      <span class="status-pill"><strong>{{ pendingCount }}</strong> 条待处理</span>
      <span class="status-pill"><strong>{{ pausedCount }}</strong> 条已暂停</span>
      <span class="status-pill"><strong>{{ canceledCount }}</strong> 条已撤销</span>
    </div>

    <el-alert
      v-if="lastResult"
      class="mb16"
      type="success"
      show-icon
      closable
      :title="lastResult.title"
      :description="lastResult.description"
      @close="lastResult = null"
    />

    <div class="filter-bar">
      <el-date-picker v-model="query.month" type="month" value-format="YYYY-MM" placeholder="月份" />
      <el-select
        v-model="scopeOrgId"
        :loading="orgLoading"
        :disabled="!orgOptions.length"
        placeholder="暂无授权组织"
      >
        <el-option
          v-for="item in orgOptions"
          :key="item.value"
          :label="item.label"
          :value="item.value"
        />
      </el-select>
      <el-select v-model="query.status" clearable placeholder="处理状态">
        <el-option label="待处理" value="待处理" />
        <el-option label="已暂停" value="已暂停" />
        <el-option label="已撤销" value="已撤销" />
      </el-select>
      <el-button :icon="Search" @click="refresh()">查询</el-button>
    </div>

    <el-table :data="tableRows" border :row-class-name="rowClassName">
      <el-table-column prop="id" label="调整单" width="140" />
      <el-table-column prop="employee" label="员工" width="100" />
      <el-table-column label="状态" width="110">
        <template #default="{ row }">
          <el-tag :type="statusTag(row.status)">{{ row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="originalPlan" label="原计划" min-width="220" show-overflow-tooltip />
      <el-table-column prop="newPlan" label="新计划" min-width="220" show-overflow-tooltip />
      <el-table-column prop="reason" label="原因" min-width="190" show-overflow-tooltip />
      <el-table-column prop="impact" label="影响" min-width="160" />
      <el-table-column label="操作" width="260" fixed="right">
        <template #default="{ row }">
          <el-button link type="warning" :icon="VideoPause" :loading="submitting && activeRow?.id === row.id && actionForm.type === '暂停原计划'" :disabled="row.status !== '待处理' || submitting" @click="openAction(row, '暂停原计划')">暂停</el-button>
          <el-button link type="danger" :icon="CircleClose" :loading="submitting && activeRow?.id === row.id && actionForm.type === '撤销原计划'" :disabled="row.status !== '待处理' || submitting" @click="openAction(row, '撤销原计划')">撤销</el-button>
          <el-button link type="primary" :icon="View" @click="openDetail(row)">关联</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-drawer v-model="detailVisible" title="计划调整关联" size="520px">
      <div v-if="activeRow" class="drawer-stack">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="调整单">{{ activeRow.id }}</el-descriptions-item>
          <el-descriptions-item label="原计划">{{ activeRow.originalPlan }}</el-descriptions-item>
          <el-descriptions-item label="新计划">{{ activeRow.newPlan }}</el-descriptions-item>
          <el-descriptions-item label="调整原因">{{ activeRow.reason }}</el-descriptions-item>
          <el-descriptions-item label="当前状态">{{ activeRow.status }}</el-descriptions-item>
          <el-descriptions-item label="影响说明">{{ activeRow.impact }}</el-descriptions-item>
          <el-descriptions-item label="处理说明">{{ activeRow.operationComment || '待处理' }}</el-descriptions-item>
          <el-descriptions-item label="操作记录">{{ activeRow.audit }}</el-descriptions-item>
        </el-descriptions>
      </div>
    </el-drawer>

    <el-dialog v-model="actionVisible" :title="actionForm.type" width="560px">
      <el-form label-position="top">
        <el-form-item label="处理说明">
          <el-input v-model="actionForm.comment" type="textarea" :rows="4" maxlength="300" show-word-limit :disabled="submitting" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button :disabled="submitting" @click="actionVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitAction">确认处理</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { CircleClose, Download, Refresh, Search, VideoPause, View } from '@element-plus/icons-vue'
import {
  createLeaderExportTaskApi,
  downloadLeaderExportTaskApi,
  getPlanAdjustmentApi,
  getLeaderExportDownloadInfoApi,
  listPlanAdjustmentsApi,
  processPlanAdjustmentApi,
} from '@/api/leader'
import { currentMonth, errorMessage, periodRange } from '@/api/performance'
import { useAutoQuery } from '@/composables/useAutoQuery'
import { useLeaderOrgScope } from '@/composables/useLeaderOrgScope'
import { saveBlob } from '@/utils/download'
import { adjustmentStatusCodes, mapPlanAdjustment } from '@/views/performanceAdapters'

type PlanAdjustRow = ReturnType<typeof mapPlanAdjustment>

const query = reactive({
  month: currentMonth(),
  status: '',
})
const { orgOptions, scopeOrgId, orgLoading, loadOrgScope } = useLeaderOrgScope()
const activeRow = ref<PlanAdjustRow | null>(null)
const tableRows = ref<PlanAdjustRow[]>([])
const detailVisible = ref(false)
const actionVisible = ref(false)
const loading = ref(false)
const submitting = ref(false)
const exporting = ref(false)
let refreshRequestId = 0
const lastProcessedId = ref('')
const lastResult = ref<{ title: string; description: string } | null>(null)
const pendingCount = computed(() => tableRows.value.filter((row) => row.status === '待处理').length)
const pausedCount = computed(() => tableRows.value.filter((row) => row.status === '已暂停').length)
const canceledCount = computed(() => tableRows.value.filter((row) => row.status === '已撤销').length)
const autoQuery = useAutoQuery(
  () => [scopeOrgId.value, query.month, query.status],
  () => refresh(false),
)
const actionForm = reactive({
  type: '暂停原计划',
  comment: '',
})

async function refresh(showMessage = true) {
  const requestId = ++refreshRequestId
  loading.value = true
  try {
    if (scopeOrgId.value == null) {
      if (requestId === refreshRequestId) tableRows.value = []
      return
    }
    const data = await listPlanAdjustmentsApi({
      scopeOrgId: scopeOrgId.value,
      status: adjustmentStatusCodes[query.status] || undefined,
      periodMonth: query.month || undefined,
    })
    if (requestId !== refreshRequestId) return
    tableRows.value = data.map(mapPlanAdjustment)
    if (showMessage) ElMessage.success('调整记录已刷新')
  } catch (error) {
    if (requestId !== refreshRequestId) return
    ElMessage.error(errorMessage(error))
  } finally {
    if (requestId === refreshRequestId) loading.value = false
  }
}

async function exportList() {
  const orgId = scopeOrgId.value
  if (orgId == null) {
    ElMessage.warning('当前账号没有可导出的授权组织')
    return
  }
  exporting.value = true
  try {
    const range = periodRange(query.month)
    const task = await createLeaderExportTaskApi({
      dimensionType: 'PLAN_ADJUSTMENT_LIST',
      dimensionId: String(orgId),
      periodType: 'MONTH',
      ...range,
      formats: ['PDF'],
      includeEvidence: false,
    })
    const info = await getLeaderExportDownloadInfoApi(task.id)
    saveBlob(await downloadLeaderExportTaskApi(task.id), info.fileName)
    ElMessage.success(`调整记录已导出，校验值 ${info.checksum || '无'}`)
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    exporting.value = false
  }
}

async function openDetail(row: PlanAdjustRow) {
  try {
    activeRow.value = mapPlanAdjustment(await getPlanAdjustmentApi(row.id))
    detailVisible.value = true
  } catch (error) {
    ElMessage.error(errorMessage(error))
  }
}

function openAction(row: PlanAdjustRow, type: string) {
  if (row.status !== '待处理' || submitting.value) return
  activeRow.value = row
  Object.assign(actionForm, {
    type,
    comment: `已核对调整原因和影响，同意${type === '撤销原计划' ? '撤销' : '暂停'}原计划，并保留完整证据链。`,
  })
  actionVisible.value = true
}

async function submitAction() {
  if (!activeRow.value || submitting.value) return
  const comment = actionForm.comment.trim()
  if (!comment) {
    ElMessage.warning('请填写处理说明')
    return
  }
  submitting.value = true
  try {
    const action = actionForm.type === '撤销原计划' ? 'CANCEL' : 'PAUSE'
    const targetId = activeRow.value.id
    const result = await processPlanAdjustmentApi(targetId, {
      action,
      comment,
      keepEvidenceChain: true,
      notifyEmployee: true,
    })
    const row = tableRows.value.find((item) => item.id === targetId)
    const nextStatus = actionResultStatus(result.status, action)
    if (row) {
      row.status = nextStatus
      row.operationComment = comment
      row.audit = `当前登录用户刚刚${nextStatus === '已撤销' ? '撤销' : '暂停'}：${comment}`
    }
    activeRow.value = row || activeRow.value
    lastProcessedId.value = targetId
    lastResult.value = {
      title: `${targetId} 已${nextStatus === '已撤销' ? '撤销' : '暂停'}`,
      description: `${result.message} 当前状态已更新为“${nextStatus}”，处理说明和证据链已保留。`,
    }
    actionVisible.value = false
    ElMessage.success(result.message)

    const detail = mapPlanAdjustment(await getPlanAdjustmentApi(targetId))
    if (row && detail.status !== '待处理') Object.assign(row, detail)
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    submitting.value = false
  }
}

function actionResultStatus(resultStatus: string, action: string) {
  if (resultStatus === 'CANCELED' || resultStatus === 'CANCEL' || action === 'CANCEL') return '已撤销'
  return '已暂停'
}

function statusTag(status: string) {
  if (status === '已暂停') return 'info'
  if (status === '已撤销') return 'danger'
  return 'warning'
}

function rowClassName({ row }: { row: PlanAdjustRow }) {
  return row.id === lastProcessedId.value ? 'success-row' : ''
}

onMounted(async () => {
  try {
    await loadOrgScope()
    await refresh(false)
    autoQuery.resume()
  } catch (error) {
    ElMessage.error(errorMessage(error))
  }
})
</script>
