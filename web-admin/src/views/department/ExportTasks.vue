<template>
  <section v-loading="loading" class="page-panel">
    <div class="page-header">
      <div>
        <h1 class="page-title">导出任务</h1>
        <p class="page-subtitle">查看 PDF、Word、Zip 资料包导出任务，完成文件完整性校验。</p>
      </div>
      <div class="toolbar">
        <el-button :icon="Refresh" :disabled="actionBusy || submitting" @click="refresh()">刷新</el-button>
        <el-button type="primary" :icon="Plus" :disabled="actionBusy || submitting" @click="createTask">新建导出任务</el-button>
      </div>
    </div>

    <div class="status-strip">
      <span class="status-pill"><strong>{{ tableRows.length }}</strong> 个任务</span>
      <span class="status-pill"><strong>{{ selectedRows.length }}</strong> 个已选</span>
      <span class="status-pill"><strong>{{ successCount }}</strong> 个成功</span>
      <span class="status-pill"><strong>{{ failedCount }}</strong> 个失败</span>
    </div>

    <div class="filter-bar">
      <el-select v-model="query.format" clearable placeholder="格式">
        <el-option label="PDF" value="PDF" />
        <el-option label="Word" value="Word" />
        <el-option label="Zip" value="Zip" />
        <el-option label="PDF + Word + Zip" value="PDF + Word + Zip" />
      </el-select>
      <el-select v-model="query.status" clearable placeholder="任务状态">
        <el-option label="成功" value="成功" />
        <el-option label="待确认" value="待确认" />
        <el-option label="待处理" value="待处理" />
        <el-option label="生成中" value="生成中" />
        <el-option label="失败" value="失败" />
        <el-option label="已过期" value="已过期" />
      </el-select>
      <el-date-picker v-model="query.date" type="date" value-format="YYYY-MM-DD" placeholder="导出日期" />
      <el-button :icon="Search" @click="refresh()">查询</el-button>
      <el-button @click="reset">重置</el-button>
    </div>

    <div v-if="selectedRows.length" class="batch-bar">
      <div>已选择 <strong>{{ selectedRows.length }}</strong> 个导出任务。</div>
      <div class="batch-actions">
        <el-button size="small" type="primary" :loading="batchAction === 'check'" :disabled="actionBusy && batchAction !== 'check'" @click="batchCheck">批量校验</el-button>
        <el-button size="small" type="warning" :loading="batchAction === 'retry'" :disabled="actionBusy && batchAction !== 'retry'" @click="batchRetry">批量重试失败任务</el-button>
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
      <el-table-column type="selection" width="48" />
      <el-table-column prop="id" label="任务编号" width="150" />
      <el-table-column prop="dimension" label="维度" min-width="190" show-overflow-tooltip />
      <el-table-column prop="format" label="格式" width="120" />
      <el-table-column prop="watermark" label="水印" min-width="220" show-overflow-tooltip />
      <el-table-column label="完整性" width="130">
        <template #default="{ row }">
          <el-tag :type="row.integrity === '完整' ? 'success' : 'warning'">{{ row.integrity }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="checksum" label="校验值" min-width="160" show-overflow-tooltip />
      <el-table-column label="状态" width="110">
        <template #default="{ row }">
          <el-tag :type="statusTag(row.status)">{{ row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="size" label="大小" width="100" />
      <el-table-column label="操作" width="260" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" :icon="View" @click="openDetail(row)">查看</el-button>
          <el-button link type="primary" :loading="processingId === row.id && processingAction === 'check'" :disabled="row.status !== '成功' || actionBusy" @click="verifyTask(row)">校验</el-button>
          <el-button link type="success" :icon="Download" :loading="processingId === row.id && processingAction === 'download'" :disabled="row.status !== '成功' || actionBusy" @click="downloadFile(row)">下载文件</el-button>
          <el-button link type="warning" :icon="RefreshRight" :loading="processingId === row.id && processingAction === 'retry'" :disabled="!['失败', '待确认', '已过期'].includes(row.status) || actionBusy" @click="retry(row.id)">重试</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-drawer v-model="detailVisible" title="文件完整性校验" size="560px">
      <div v-if="activeRow" class="drawer-stack">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="任务编号">{{ activeRow.id }}</el-descriptions-item>
          <el-descriptions-item label="导出维度">{{ activeRow.dimension }}</el-descriptions-item>
          <el-descriptions-item label="统计周期">{{ activeRow.periodStart }} 至 {{ activeRow.periodEnd }}</el-descriptions-item>
          <el-descriptions-item label="格式">{{ activeRow.format }}</el-descriptions-item>
          <el-descriptions-item label="证据附件">{{ activeRow.includeEvidence ? '包含' : '不包含' }}</el-descriptions-item>
          <el-descriptions-item label="水印">{{ activeRow.watermark }}</el-descriptions-item>
          <el-descriptions-item label="任务状态">{{ activeRow.status }}</el-descriptions-item>
          <el-descriptions-item label="完整性">{{ activeRow.integrity }}</el-descriptions-item>
          <el-descriptions-item label="校验值">{{ activeRow.checksum }}</el-descriptions-item>
          <el-descriptions-item label="申请人">{{ activeRow.requestedByName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="申请时间">{{ activeRow.requestedAt || '-' }}</el-descriptions-item>
          <el-descriptions-item label="完成时间">{{ activeRow.finishedAt || '-' }}</el-descriptions-item>
          <el-descriptions-item label="过期时间">{{ activeRow.expireAt || '-' }}</el-descriptions-item>
          <el-descriptions-item v-if="activeRow.errorMessage" label="失败原因">{{ activeRow.errorMessage }}</el-descriptions-item>
        </el-descriptions>
        <div class="section-card compact">
          <div class="section-title">资料包目录</div>
          <ul class="plain-list">
            <li v-for="item in packageContents" :key="item">{{ item }}</li>
          </ul>
        </div>
      </div>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
        <el-button v-if="activeRow" type="primary" :loading="processingId === activeRow.id && processingAction === 'check'" :disabled="activeRow.status !== '成功' || actionBusy" @click="verifyTask(activeRow)">校验完整性</el-button>
        <el-button v-if="activeRow" type="success" :loading="processingId === activeRow.id && processingAction === 'download'" :disabled="activeRow.status !== '成功' || actionBusy" @click="downloadFile(activeRow)">下载文件</el-button>
      </template>
    </el-drawer>

    <el-dialog v-model="taskVisible" title="新建导出任务" width="560px">
      <el-form label-position="top">
        <el-form-item label="导出维度">
          <el-select v-model="taskForm.dimension">
            <el-option label="部门台账" value="部门台账" />
            <el-option label="月计划审批清单" value="月计划审批清单" />
            <el-option label="成果确认证据清单" value="成果确认证据清单" />
            <el-option label="申诉资料包" value="申诉资料包" />
            <el-option label="季度汇总" value="季度汇总" />
          </el-select>
        </el-form-item>
        <el-form-item label="统计口径">
          <el-segmented v-model="taskForm.periodType" :options="periodTypeOptions" block />
        </el-form-item>
        <el-form-item label="统计日期范围" required>
          <el-date-picker
            v-model="taskForm.periodRange"
            type="daterange"
            value-format="YYYY-MM-DD"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="导出格式">
          <el-checkbox-group v-model="taskForm.formats">
            <el-checkbox-button label="PDF" />
            <el-checkbox-button label="Word" />
            <el-checkbox-button label="Zip" />
          </el-checkbox-group>
        </el-form-item>
        <el-form-item label="包含证据附件">
          <el-switch v-model="taskForm.includeEvidence" />
        </el-form-item>
        <el-form-item label="水印">
          <el-input v-model="taskForm.watermark" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button :disabled="submitting" @click="taskVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitTask">创建任务</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Download, Plus, Refresh, RefreshRight, Search, View } from '@element-plus/icons-vue'
import {
  checkExportTaskApi,
  createExportTaskApi,
  downloadExportTaskApi,
  getExportDownloadInfoApi,
  getExportTaskApi,
  listExportTasksApi,
  retryExportTaskApi,
} from '@/api/department'
import { errorMessage, normalizeExportFormat } from '@/api/performance'
import { useAutoQuery } from '@/composables/useAutoQuery'
import { saveBlob } from '@/utils/download'
import { exportStatusCodes, mapExportTask } from '@/views/performanceAdapters'

type ExportTaskRow = ReturnType<typeof mapExportTask>

const route = useRoute()
const tableRef = ref()
const query = reactive({
  format: '',
  status: '',
  date: '',
})
const tableRows = ref<ExportTaskRow[]>([])
const selectedRows = ref<ExportTaskRow[]>([])
const activeRow = ref<ExportTaskRow | null>(null)
const detailVisible = ref(false)
const taskVisible = ref(false)
const loading = ref(false)
const submitting = ref(false)
const processingId = ref('')
const processingAction = ref('')
const batchAction = ref('')
const taskForm = reactive({
  dimension: '部门台账',
  periodType: 'MONTH',
  periodRange: currentMonthRange(),
  formats: ['PDF', 'Word', 'Zip'],
  includeEvidence: true,
  watermark: '部门、导出人、导出时间、周期',
})
const successCount = computed(() => tableRows.value.filter((row) => row.status === '成功').length)
const failedCount = computed(() => tableRows.value.filter((row) => row.status === '失败').length)
const actionBusy = computed(() => Boolean(processingId.value || batchAction.value))
const periodTypeOptions = [
  { label: '日', value: 'DAY' },
  { label: '月', value: 'MONTH' },
  { label: '季度', value: 'QUARTER' },
  { label: '年度', value: 'YEAR' },
]
const packageContents = computed(() => {
  if (!activeRow.value) return []
  const contents: string[] = []
  if (activeRow.value.dimensionType === 'MONTH_PLAN_APPROVAL_LIST') contents.push('月计划与审批结果清单')
  else if (activeRow.value.dimensionType === 'RESULT_CONFIRM_LIST') contents.push('成果、确认意见与证据索引')
  else if (activeRow.value.dimensionType === 'APPEAL_PACKAGE') contents.push('申诉记录与关联成果信息')
  else if (activeRow.value.dimensionType === 'QUARTER_SUMMARY') contents.push('季度计划与成果汇总')
  else contents.push('部门计划、成果与台账汇总')
  if (activeRow.value.includeEvidence) contents.push('成果证据附件')
  contents.push(`任务清单与 SHA-256 校验值（${activeRow.value.format}）`)
  return contents
})
let refreshTimer: number | undefined
let refreshRequestId = 0
const autoQuery = useAutoQuery(
  () => [query.format, query.status, query.date],
  () => refresh(false),
)

function localDate(date: Date) {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

function currentMonthRange() {
  const now = new Date()
  return [localDate(new Date(now.getFullYear(), now.getMonth(), 1)), localDate(new Date(now.getFullYear(), now.getMonth() + 1, 0))]
}

function statusTag(status: string) {
  if (status === '成功') return 'success'
  if (status === '失败') return 'danger'
  if (status === '待确认') return 'warning'
  if (status === '已过期') return 'info'
  return 'info'
}

async function refresh(showMessage = true, background = false) {
  const requestId = background ? refreshRequestId : ++refreshRequestId
  const querySnapshot = JSON.stringify(query)
  if (!background) loading.value = true
  try {
    const format = query.format && !query.format.includes('+') ? normalizeExportFormat(query.format) : undefined
    const data = await listExportTasksApi({
      format,
      status: exportStatusCodes[query.status] || undefined,
    })
    const existingRows = new Map(tableRows.value.map((row) => [row.id, row]))
    let rows = data.map((item) => {
      const next = mapExportTask(item)
      const existing = existingRows.get(next.id)
      if (!existing) return next
      Object.assign(existing, next)
      return existing
    })
    if (requestId !== refreshRequestId || querySnapshot !== JSON.stringify(query)) return
    if (query.format.includes('+')) rows = rows.filter((row) => row.format === query.format)
    tableRows.value = query.date ? rows.filter((row) => row.requestedAt.startsWith(query.date)) : rows
    if (showMessage) ElMessage.success('导出任务已刷新')
  } catch (error) {
    if (requestId !== refreshRequestId) return
    ElMessage.error(errorMessage(error))
  } finally {
    if (!background && requestId === refreshRequestId) loading.value = false
  }
}

async function reset() {
  autoQuery.pause()
  query.format = ''
  query.status = ''
  query.date = ''
  await refresh(false)
  autoQuery.resume()
  ElMessage.success('筛选条件已重置')
}

function createTask() {
  taskVisible.value = true
}

async function retry(id: string) {
  if (actionBusy.value) return
  processingId.value = id
  processingAction.value = 'retry'
  try {
    const result = await retryExportTaskApi(id)
    const row = tableRows.value.find((item) => item.id === id)
    if (row) Object.assign(row, { status: '待处理', integrity: '待校验', checksum: '待校验', errorMessage: '' })
    ElMessage.success(result.message)
  } catch (error) {
    ElMessage.error(errorMessage(error))
  }
}

async function openDetail(row: ExportTaskRow) {
  await openDetailById(row.id, row)
}

async function openDetailById(id: string, targetRow?: ExportTaskRow) {
  try {
    activeRow.value = mapExportTask(await getExportTaskApi(id))
    if (targetRow) Object.assign(targetRow, activeRow.value)
    detailVisible.value = true
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    processingId.value = ''
    processingAction.value = ''
  }
}

async function verifyTask(row: ExportTaskRow) {
  if (row.status !== '成功' || actionBusy.value) return
  processingId.value = row.id
  processingAction.value = 'check'
  try {
    const result = await checkExportTaskApi(row.id)
    const refreshed = mapExportTask(await getExportTaskApi(row.id))
    Object.assign(row, refreshed)
    if (activeRow.value?.id === row.id) activeRow.value = row
    if (['NEEDS_REVIEW', 'INCOMPLETE'].includes(result.status)) ElMessage.warning(result.message)
    else ElMessage.success(result.message)
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    processingId.value = ''
    processingAction.value = ''
  }
}

function onSelectionChange(rows: ExportTaskRow[]) {
  selectedRows.value = rows
}

function clearSelection() {
  tableRef.value?.clearSelection?.()
  selectedRows.value = []
}

async function batchCheck() {
  const checkableRows = selectedRows.value.filter((row) => ['成功', '待确认'].includes(row.status))
  if (!checkableRows.length) {
    ElMessage.warning('当前选择中没有可校验的已生成任务')
    return
  }
  if (actionBusy.value) return
  try {
    const rows = [...checkableRows]
    await ElMessageBox.confirm(`确认校验选中的 ${rows.length} 个导出任务？`, '批量完整性校验', { type: 'warning' })
    batchAction.value = 'check'
    const settled = await Promise.allSettled(rows.map((row) => checkExportTaskApi(row.id)))
    await refresh(false, true)
    clearSelection()
    const fulfilled = settled.filter((item): item is PromiseFulfilledResult<Awaited<ReturnType<typeof checkExportTaskApi>>> => item.status === 'fulfilled')
    const mismatchCount = fulfilled.filter((item) => ['NEEDS_REVIEW', 'INCOMPLETE'].includes(item.value.status)).length
    const failed = settled.length - fulfilled.length
    if (failed || mismatchCount) ElMessage.warning(`校验完成 ${fulfilled.length} 个，${mismatchCount} 个需重新导出，${failed} 个执行失败`)
    else ElMessage.success(`已对 ${fulfilled.length} 个任务完成完整性复核`)
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
    ElMessage.error(errorMessage(error))
  } finally {
    batchAction.value = ''
  }
}

async function batchRetry() {
  const rows = selectedRows.value.filter((row) => ['失败', '待确认', '已过期'].includes(row.status))
  if (!rows.length) {
    ElMessage.warning('当前选择中没有可重试任务')
    return
  }
  if (actionBusy.value) return
  try {
    await ElMessageBox.confirm(`确认重试选中的 ${rows.length} 个导出任务？`, '批量重试', { type: 'warning' })
    batchAction.value = 'retry'
    const settled = await Promise.allSettled(rows.map((row) => retryExportTaskApi(row.id)))
    const succeededIds = new Set(rows.filter((_, index) => settled[index]?.status === 'fulfilled').map((row) => row.id))
    rows.filter((row) => succeededIds.has(row.id))
      .forEach((row) => Object.assign(row, { status: '待处理', integrity: '待校验', checksum: '待校验', errorMessage: '' }))
    clearSelection()
    const failed = settled.length - succeededIds.size
    if (failed) ElMessage.warning(`${succeededIds.size} 个任务已重新进入队列，${failed} 个重试失败`)
    else ElMessage.success(`${succeededIds.size} 个任务已重新进入队列`)
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
    ElMessage.error(errorMessage(error))
  } finally {
    batchAction.value = ''
  }
}

function rowClassName({ row }: { row: ExportTaskRow }) {
  if (row.status === '失败') return 'danger-row clickable-row'
  if (row.status === '待确认') return 'warning-row clickable-row'
  return 'clickable-row'
}

async function submitTask() {
  if (!taskForm.formats.length) {
    ElMessage.warning('请至少选择一种导出格式')
    return
  }
  if (!taskForm.periodRange || taskForm.periodRange.length !== 2) {
    ElMessage.warning('请选择完整的统计日期范围')
    return
  }
  submitting.value = true
  try {
    const dimensionTypes: Record<string, string> = {
      '个人资料包': 'PERSON_LEDGER',
      '部门台账': 'DEPARTMENT_LEDGER',
      '月计划审批清单': 'MONTH_PLAN_APPROVAL_LIST',
      '成果确认证据清单': 'RESULT_CONFIRM_LIST',
      '申诉资料包': 'APPEAL_PACKAGE',
      '季度汇总': 'QUARTER_SUMMARY',
    }
    const task = await createExportTaskApi({
      dimensionType: dimensionTypes[taskForm.dimension] || 'DEPARTMENT_LEDGER',
      periodType: taskForm.periodType,
      periodStart: taskForm.periodRange[0],
      periodEnd: taskForm.periodRange[1],
      formats: taskForm.formats.map(normalizeExportFormat),
      includeEvidence: taskForm.includeEvidence,
      watermark: taskForm.watermark,
    })
    tableRows.value.unshift(mapExportTask(task))
    ElMessage.success(`${taskForm.dimension} ${taskForm.formats.join('、')} 导出任务已创建：${task.id}`)
    taskVisible.value = false
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    submitting.value = false
  }
}

async function downloadFile(row: ExportTaskRow) {
  if (row.status !== '成功' || actionBusy.value) return
  processingId.value = row.id
  processingAction.value = 'download'
  try {
    const info = await getExportDownloadInfoApi(row.id)
    const blob = await downloadExportTaskApi(row.id)
    saveBlob(blob, info.fileName)
    ElMessage.success(`${info.fileName} 已下载，校验值 ${info.checksum || '无'}`)
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    processingId.value = ''
    processingAction.value = ''
  }
}

onMounted(async () => {
  await refresh(false)
  autoQuery.resume()
  if (typeof route.query.taskId === 'string') {
    await openDetailById(route.query.taskId)
  }
  refreshTimer = window.setInterval(() => {
    if (!loading.value && !submitting.value && !actionBusy.value
      && tableRows.value.some((row) => ['待处理', '生成中'].includes(row.status))) {
      void refresh(false, true)
    }
  }, 3000)
})

onBeforeUnmount(() => window.clearInterval(refreshTimer))
</script>
