<template>
  <section v-loading="loading" class="page-panel">
    <div class="page-header">
      <div>
        <h1 class="page-title">成果最终确认</h1>
        <p class="page-subtitle">部门负责人完成成果最终确认或驳回，确认动作需强认证留痕。</p>
      </div>
      <div class="toolbar">
        <el-button :icon="Refresh" :disabled="actionBusy" @click="refresh()">刷新</el-button>
        <el-button type="primary" :icon="Download" :loading="exporting" :disabled="loading || actionBusy" @click="exportList">导出确认清单</el-button>
      </div>
    </div>

    <AiReviewPanel
      class="mt16"
      :review="null"
      :display-report="false"
      title="AI成果核验辅助"
      empty-text="成果提交时会自动生成证据完整性、验收项覆盖和完成比例合理性报告；点击“详情与AI”后再结合直属领导建议完成人工确认。"
    />

    <div class="status-strip mt16">
      <span class="status-pill"><strong>{{ tableRows.length }}</strong> 条成果</span>
      <span class="status-pill"><strong>{{ selectedRows.length }}</strong> 条已选</span>
      <span class="status-pill"><strong>{{ confirmableCount }}</strong> 条可确认</span>
      <span class="status-pill"><strong>{{ blockedCount }}</strong> 条需驳回或补证</span>
    </div>

    <div class="filter-bar">
      <el-date-picker v-model="query.month" type="month" value-format="YYYY-MM" placeholder="月份" />
      <el-select
        v-model="selectedOrgId"
        clearable
        :loading="orgLoading"
        :disabled="!orgOptions.length"
        placeholder="全部授权组织"
      >
        <el-option v-for="item in orgOptions" :key="item.value" :label="item.label" :value="item.value" />
      </el-select>
      <el-select v-model="query.status" clearable placeholder="确认状态">
        <el-option label="待确认" value="待确认" />
        <el-option label="不可确认" value="不可确认" />
        <el-option label="已确认" value="已确认" />
        <el-option label="已驳回" value="已驳回" />
      </el-select>
      <el-input v-model="query.keyword" clearable placeholder="员工/成果" />
      <el-button :icon="Search" @click="refresh()">查询</el-button>
      <el-button @click="reset">重置</el-button>
    </div>

    <div v-if="selectedRows.length" class="batch-bar">
      <div>已选择 <strong>{{ selectedRows.length }}</strong> 条成果。</div>
      <div class="batch-actions">
        <el-button size="small" :loading="exporting" :disabled="actionBusy" @click="batchExport">导出证据清单</el-button>
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
      <el-table-column prop="employee" label="员工" width="100" />
      <el-table-column prop="plan" label="计划/成果" min-width="220" show-overflow-tooltip />
      <el-table-column prop="evidence" label="证据" min-width="160" show-overflow-tooltip />
      <el-table-column label="员工比例" width="140">
        <template #default="{ row }">
          <el-progress :percentage="row.completion" :stroke-width="8" />
        </template>
      </el-table-column>
      <el-table-column prop="autoGrade" label="自动等级" width="120" />
      <el-table-column prop="leaderSuggestion" label="直属领导建议" min-width="190" show-overflow-tooltip />
      <el-table-column label="状态" width="120">
        <template #default="{ row }">
          <el-tag :type="statusTag(row.status)">{{ row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="300" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" :icon="View" @click="openDetail(row)">详情与AI</el-button>
          <el-tooltip :disabled="row.status === '待确认'" content="证据或建议未满足最终确认条件" placement="top">
            <span>
              <el-button link type="success" :icon="Lock" :disabled="row.status !== '待确认' || actionBusy" @click="openVerify(row)">强认证确认</el-button>
            </span>
          </el-tooltip>
          <el-button
            link
            type="danger"
            :icon="Close"
            :loading="processingId === row.id"
            :disabled="row.status === '已确认' || row.status === '已驳回' || actionBusy"
            @click="reject(row)"
          >驳回</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-drawer v-model="detailVisible" title="成果确认详情" size="680px" @closed="closeDetail">
      <div class="drawer-stack">
        <el-skeleton v-if="detailLoading" :rows="7" animated />
        <template v-else-if="activeRow">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="员工">{{ activeRow.employee }}</el-descriptions-item>
          <el-descriptions-item label="计划/成果">{{ activeRow.plan }}</el-descriptions-item>
          <el-descriptions-item label="证据">{{ activeRow.evidence }}</el-descriptions-item>
          <el-descriptions-item label="员工比例">{{ activeRow.completion }}%</el-descriptions-item>
          <el-descriptions-item label="直属领导建议">{{ activeRow.leaderSuggestion }}</el-descriptions-item>
          <el-descriptions-item label="问题">{{ activeRow.issue }}</el-descriptions-item>
        </el-descriptions>
        <AiReviewPanel :review="aiReview" compact empty-text="该成果尚未生成AI检查记录。" />
        <div class="section-header mt16">
          <div>
            <h2>成果证据</h2>
            <p>最终确认前可逐个下载并核对证据文件。</p>
          </div>
        </div>
        <el-table :data="activeRow.evidences" border empty-text="暂无成果证据">
          <el-table-column prop="name" label="文件" min-width="180" show-overflow-tooltip />
          <el-table-column prop="type" label="类型" width="100" />
          <el-table-column prop="status" label="状态" width="100" />
          <el-table-column label="评审" width="90">
            <template #default="{ row }">
              <el-tag :type="row.reviewPassed ? 'success' : 'info'">{{ row.reviewPassed ? '通过' : '待确认' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="90" fixed="right">
            <template #default="{ row }">
              <el-button
                link
                type="primary"
                :icon="Download"
                :loading="downloadingEvidenceId === row.id"
                :disabled="downloadingEvidenceId !== null"
                @click="downloadEvidence(row)"
              >下载</el-button>
            </template>
          </el-table-column>
        </el-table>
        </template>
      </div>
    </el-drawer>

    <el-dialog v-model="verifyVisible" title="强认证确认" width="520px" @closed="authPassword = ''">
      <el-form label-position="top">
        <el-form-item label="确认结果">
          <el-input :model-value="activeRow ? `${activeRow.employee} / ${activeRow.plan}` : ''" disabled />
        </el-form-item>
        <el-form-item label="当前登录密码">
          <el-input
            v-model="authPassword"
            type="password"
            show-password
            autocomplete="current-password"
            placeholder="输入当前账号密码完成身份复核"
            :disabled="submitting"
            @keyup.enter="submitConfirm"
          />
        </el-form-item>
        <el-form-item label="确认意见">
          <el-input v-model="confirmComment" type="textarea" :rows="3" maxlength="500" show-word-limit :disabled="submitting" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button :disabled="submitting" @click="verifyVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitConfirm">最终确认</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Close, Download, Lock, Refresh, Search, View } from '@element-plus/icons-vue'
import {
  confirmResultApi,
  createExportTaskApi,
  downloadDepartmentResultEvidenceApi,
  getResultConfirmApi,
  listResultConfirmsApi,
  rejectResultApi,
} from '@/api/department'
import { currentMonth, errorMessage, periodRange } from '@/api/performance'
import { useAutoQuery } from '@/composables/useAutoQuery'
import { useDepartmentOrgScope } from '@/composables/useDepartmentOrgScope'
import { confirmStatusCodes, mapResultConfirm } from '@/views/performanceAdapters'
import { saveBlob } from '@/utils/download'
import { getLatestAiReviewApi, type AiReview } from '@/api/aiReview'
import AiReviewPanel from '@/components/AiReviewPanel.vue'

type FinalConfirmRow = ReturnType<typeof mapResultConfirm>

const route = useRoute()
const tableRef = ref()
const { orgOptions, selectedOrgId, orgLoading, loadOrgScope, resetOrgScope } = useDepartmentOrgScope(false)
const query = reactive({
  month: currentMonth(),
  status: '',
  keyword: '',
})
const tableRows = ref<FinalConfirmRow[]>([])
const selectedRows = ref<FinalConfirmRow[]>([])
const activeRow = ref<FinalConfirmRow | null>(null)
const aiReview = ref<AiReview | null>(null)
const detailVisible = ref(false)
const verifyVisible = ref(false)
const authPassword = ref('')
const confirmComment = ref('确认成果进入绩效依据。')
const loading = ref(false)
const detailLoading = ref(false)
const submitting = ref(false)
const exporting = ref(false)
const downloadingEvidenceId = ref<number | null>(null)
const processingId = ref('')
let refreshRequestId = 0
let detailRequestId = 0
const confirmableCount = computed(() => tableRows.value.filter((row) => row.status === '待确认').length)
const blockedCount = computed(() => tableRows.value.filter((row) => row.status === '不可确认').length)
const actionBusy = computed(() => submitting.value || Boolean(processingId.value))
const autoQuery = useAutoQuery(
  () => [selectedOrgId.value, query.month, query.status, query.keyword],
  () => refresh(false),
)

async function refresh(showMessage = true) {
  const requestId = ++refreshRequestId
  loading.value = true
  try {
    const data = await listResultConfirmsApi({
      orgId: selectedOrgId.value,
      periodMonth: query.month || undefined,
      confirmStatus: confirmStatusCodes[query.status] || undefined,
      keyword: query.keyword || undefined,
    })
    if (requestId !== refreshRequestId) return
    tableRows.value = data.map(mapResultConfirm)
    clearSelection()
    if (showMessage) ElMessage.success('成果确认列表已刷新')
  } catch (error) {
    if (requestId !== refreshRequestId) return
    ElMessage.error(errorMessage(error))
  } finally {
    if (requestId === refreshRequestId) loading.value = false
  }
}

async function reset() {
  autoQuery.pause()
  Object.assign(query, {
    month: currentMonth(),
    status: '',
    keyword: '',
  })
  resetOrgScope()
  await refresh(false)
  autoQuery.resume()
  ElMessage.success('筛选条件已重置')
}

async function exportList() {
  await createEvidenceExport(false)
}

async function openDetail(row: FinalConfirmRow) {
  await openDetailById(row.id)
}

async function openDetailById(id: string) {
  const requestId = ++detailRequestId
  detailVisible.value = true
  detailLoading.value = true
  activeRow.value = null
  aiReview.value = null
  try {
    const detail = mapResultConfirm(await getResultConfirmApi(id))
    if (requestId !== detailRequestId) return
    activeRow.value = detail
    aiReview.value = await getLatestAiReviewApi('RESULT', Number(id))
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
  detailLoading.value = false
  activeRow.value = null
  aiReview.value = null
}

async function downloadEvidence(evidence: { id: number; name: string }) {
  if (!activeRow.value || downloadingEvidenceId.value !== null) return
  downloadingEvidenceId.value = evidence.id
  try {
    const blob = await downloadDepartmentResultEvidenceApi(activeRow.value.id, evidence.id)
    saveBlob(blob, evidence.name)
    ElMessage.success('成果证据已下载')
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    downloadingEvidenceId.value = null
  }
}

function openVerify(row: FinalConfirmRow) {
  if (row.status !== '待确认' || actionBusy.value) return
  activeRow.value = row
  authPassword.value = ''
  verifyVisible.value = true
}

function rowClassName({ row }: { row: FinalConfirmRow }) {
  if (row.status === '不可确认') return 'danger-row clickable-row'
  if (row.issue !== '无') return 'warning-row clickable-row'
  return 'clickable-row'
}

function statusTag(status: string) {
  if (status === '已确认') return 'success'
  if (status === '已驳回') return 'info'
  if (status === '不可确认') return 'danger'
  return 'warning'
}

function onSelectionChange(rows: FinalConfirmRow[]) {
  selectedRows.value = rows
}

function clearSelection() {
  tableRef.value?.clearSelection?.()
  selectedRows.value = []
}

async function batchExport() {
  await createEvidenceExport(true)
}

async function reject(row: FinalConfirmRow) {
  if (['已确认', '已驳回'].includes(row.status) || actionBusy.value) return
  try {
    const { value } = await ElMessageBox.prompt(`请输入驳回 ${row.employee} 成果的原因`, '驳回成果', {
      confirmButtonText: '驳回',
      cancelButtonText: '取消',
      inputType: 'textarea',
      inputValidator: (text) => {
        if (!text?.trim()) return '请输入驳回原因'
        return text.trim().length <= 500 || '驳回原因不能超过 500 个字符'
      },
    })
    processingId.value = row.id
    const result = await rejectResultApi(row.id, { decision: 'REJECTED', comment: value.trim(), notifyEmployee: true })
    row.status = '已驳回'
    ElMessage.success(result.message)
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
    ElMessage.error(errorMessage(error))
  } finally {
    processingId.value = ''
  }
}

async function submitConfirm() {
  if (!activeRow.value || submitting.value) return
  if (!authPassword.value) {
    ElMessage.warning('请输入当前登录密码')
    return
  }
  const comment = confirmComment.value.trim()
  if (!comment) {
    ElMessage.warning('请填写确认意见')
    return
  }
  submitting.value = true
  try {
    const result = await confirmResultApi(activeRow.value.id, {
      decision: 'CONFIRMED',
      comment,
      authPassword: authPassword.value,
      notifyEmployee: true,
    })
    verifyVisible.value = false
    authPassword.value = ''
    await refresh(false)
    ElMessage.success(result.message)
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    submitting.value = false
  }
}

async function createEvidenceExport(selectedOnly: boolean) {
  if (exporting.value) return
  if (selectedOnly && !selectedRows.value.length) {
    ElMessage.warning('请选择需要导出的成果')
    return
  }
  exporting.value = true
  try {
    const task = await createExportTaskApi({
      dimensionType: 'RESULT_CONFIRM_LIST',
      dimensionId: selectedOnly
        ? `RESULTS:${selectedRows.value.map((row) => row.id).join(',')}`
        : selectedOrgId.value == null ? undefined : String(selectedOrgId.value),
      periodType: 'MONTH',
      ...periodRange(query.month),
      formats: ['PDF'],
      includeEvidence: true,
    })
    ElMessage.success(`成果证据清单导出任务已创建：${task.id}`)
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    exporting.value = false
  }
}

onMounted(async () => {
  try {
    await loadOrgScope()
    await refresh(false)
    autoQuery.resume()
    if (typeof route.query.resultId === 'string') {
      await openDetailById(route.query.resultId)
    }
  } catch (error) {
    ElMessage.error(errorMessage(error))
  }
})
</script>
