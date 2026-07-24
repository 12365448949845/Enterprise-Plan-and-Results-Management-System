<template>
  <section v-loading="loading" class="page-panel">
    <div class="page-header">
      <div>
        <h1 class="page-title">成果确认建议</h1>
        <p class="page-subtitle">查看成果证据和员工完成比例，向部门负责人提交确认或驳回建议。</p>
      </div>
      <div class="toolbar">
        <el-button :icon="Refresh" :disabled="actionBusy" @click="refresh()">刷新</el-button>
        <el-button type="primary" :icon="Download" :loading="exporting" :disabled="loading || actionBusy" @click="exportList">导出建议清单</el-button>
      </div>
    </div>

    <AiReviewPanel
      class="mt16"
      :review="null"
      :display-report="false"
      title="AI成果核验辅助"
      empty-text="员工提交成果前会自动检查证据完整性、验收项覆盖和完成比例合理性；点击“证据与AI”查看引用原文和判断依据。"
    />

    <div class="status-strip mt16">
      <span class="status-pill"><strong>{{ tableRows.length }}</strong> 条成果</span>
      <span class="status-pill"><strong>{{ selectedRows.length }}</strong> 条已选</span>
      <span class="status-pill"><strong>{{ pendingCount }}</strong> 条待建议</span>
      <span class="status-pill"><strong>{{ issueCount }}</strong> 条证据问题</span>
    </div>

    <div class="filter-bar">
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
      <el-date-picker v-model="query.dateRange" type="daterange" start-placeholder="开始日期" end-placeholder="结束日期" value-format="YYYY-MM-DD" />
      <el-select v-model="query.status" clearable placeholder="成果状态">
        <el-option label="待建议" value="待建议" />
        <el-option label="建议确认" value="建议确认" />
        <el-option label="建议驳回" value="建议驳回" />
      </el-select>
      <el-select v-model="query.evidence" clearable placeholder="证据状态">
        <el-option label="完整" value="完整" />
        <el-option label="缺失" value="缺失" />
      </el-select>
      <el-button :icon="Search" @click="refresh()">查询</el-button>
      <el-button @click="reset">重置</el-button>
    </div>

    <div v-if="selectedRows.length" class="batch-bar">
      <div>已选择 <strong>{{ selectedRows.length }}</strong> 条成果，可批量提交建议。</div>
      <div class="batch-actions">
        <el-button size="small" type="success" :loading="batchAction === '建议确认'" :disabled="actionBusy && batchAction !== '建议确认'" @click="batchSuggest('建议确认')">建议确认</el-button>
        <el-button size="small" type="danger" :loading="batchAction === '建议驳回'" :disabled="actionBusy && batchAction !== '建议驳回'" @click="batchSuggest('建议驳回')">建议驳回</el-button>
        <el-button size="small" :disabled="actionBusy" @click="clearSelection">清空选择</el-button>
      </div>
    </div>

    <el-table
      ref="tableRef"
      :data="tableRows"
      border
      highlight-current-row
      @selection-change="onSelectionChange"
      @row-dblclick="openEvidence"
    >
      <el-table-column type="selection" width="48" :selectable="canSelect" />
      <el-table-column prop="employee" label="员工" width="100" />
      <el-table-column prop="result" label="成果" min-width="200" show-overflow-tooltip />
      <el-table-column prop="plan" label="关联计划" width="130" />
      <el-table-column prop="evidence" label="成果证据" min-width="190" show-overflow-tooltip />
      <el-table-column label="完成比例" width="140">
        <template #default="{ row }">
          <el-progress :percentage="row.completion" :stroke-width="8" />
        </template>
      </el-table-column>
      <el-table-column prop="autoGrade" label="系统等级" width="120" />
      <el-table-column label="检查结果" width="150">
        <template #default="{ row }">
          <el-tag :type="row.issue === '无' ? 'success' : 'warning'">{{ row.issue }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="suggestion" label="当前建议" width="120" />
      <el-table-column prop="resultStatus" label="成果状态" width="110" />
      <el-table-column label="操作" width="260" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" :icon="View" @click="openEvidence(row)">证据与AI</el-button>
          <el-button link type="success" :icon="Check" :disabled="row.terminal || actionBusy" @click="openSuggest(row, '建议确认')">建议确认</el-button>
          <el-button link type="danger" :icon="Close" :disabled="row.terminal || actionBusy" @click="openSuggest(row, '建议驳回')">建议驳回</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-drawer v-model="evidenceVisible" title="成果证据" size="520px" @closed="closeEvidence">
      <div class="drawer-stack">
        <el-skeleton v-if="evidenceLoading" :rows="7" animated />
        <template v-else-if="activeRow">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="员工">{{ activeRow.employee }}</el-descriptions-item>
          <el-descriptions-item label="成果">{{ activeRow.result }}</el-descriptions-item>
          <el-descriptions-item label="关联计划">{{ activeRow.plan }}</el-descriptions-item>
          <el-descriptions-item label="证据">{{ activeRow.evidence }}</el-descriptions-item>
          <el-descriptions-item label="完成比例">{{ activeRow.completion }}%</el-descriptions-item>
          <el-descriptions-item label="检查结果">{{ activeRow.issue }}</el-descriptions-item>
        </el-descriptions>
        <AiReviewPanel :review="aiReview" compact empty-text="该成果尚未生成AI检查记录。" />
        <div>
          <div class="section-header mt16">
            <div>
              <h2>证据文件</h2>
              <p>下载前会再次校验数据范围、文件路径和 SHA-256。</p>
            </div>
          </div>
          <el-table :data="evidenceFiles" border>
            <el-table-column prop="name" label="文件" />
            <el-table-column prop="type" label="类型" width="100" />
            <el-table-column prop="status" label="状态" width="100" />
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
        </div>
        </template>
      </div>
    </el-drawer>

    <el-dialog v-model="suggestVisible" :title="suggestForm.type" width="560px">
      <el-form label-position="top">
        <el-form-item label="建议结论">
          <el-select v-model="suggestForm.type">
            <el-option label="建议确认" value="建议确认" />
            <el-option label="建议驳回" value="建议驳回" />
          </el-select>
        </el-form-item>
        <el-form-item label="建议说明">
          <el-input v-model="suggestForm.comment" type="textarea" :rows="4" maxlength="300" show-word-limit :disabled="submitting" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button :disabled="submitting" @click="suggestVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitSuggest">提交建议</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Check, Close, Download, Refresh, Search, View } from '@element-plus/icons-vue'
import {
  batchSubmitResultSuggestionsApi,
  createLeaderExportTaskApi,
  downloadLeaderExportTaskApi,
  downloadLeaderResultEvidenceApi,
  getLeaderExportDownloadInfoApi,
  getResultSuggestionApi,
  listResultSuggestionsApi,
  submitResultSuggestionApi,
} from '@/api/leader'
import { currentMonthToDateRange, errorMessage } from '@/api/performance'
import { useAutoQuery } from '@/composables/useAutoQuery'
import { useLeaderOrgScope } from '@/composables/useLeaderOrgScope'
import { saveBlob } from '@/utils/download'
import { getLatestAiReviewApi, type AiReview } from '@/api/aiReview'
import AiReviewPanel from '@/components/AiReviewPanel.vue'
import {
  mapResultSuggestion,
  suggestionStatusCodes,
} from '@/views/performanceAdapters'

type ResultSuggestionRow = ReturnType<typeof mapResultSuggestion>

const tableRef = ref()
const { orgOptions, scopeOrgId, orgLoading, loadOrgScope, resetOrgScope } = useLeaderOrgScope()
const query = reactive({
  dateRange: currentMonthToDateRange(),
  status: '',
  evidence: '',
})
const tableRows = ref<ResultSuggestionRow[]>([])
const selectedRows = ref<ResultSuggestionRow[]>([])
const activeRow = ref<ResultSuggestionRow | null>(null)
const evidenceVisible = ref(false)
const suggestVisible = ref(false)
const loading = ref(false)
const evidenceLoading = ref(false)
const submitting = ref(false)
const aiReview = ref<AiReview | null>(null)
const batchAction = ref('')
const exporting = ref(false)
let refreshRequestId = 0
let evidenceRequestId = 0
const suggestForm = reactive({
  type: '建议确认',
  comment: '',
})
const evidenceFiles = ref<{ id: number; name: string; type: string; status: string }[]>([])
const downloadingEvidenceId = ref<number | null>(null)
const pendingCount = computed(() => tableRows.value.filter((row) => row.suggestion === '待建议').length)
const issueCount = computed(() => tableRows.value.filter((row) => row.issue !== '无').length)
const actionBusy = computed(() => submitting.value || Boolean(batchAction.value))
const autoQuery = useAutoQuery(
  () => [scopeOrgId.value, query.dateRange?.[0], query.dateRange?.[1], query.status, query.evidence],
  () => refresh(false),
)

async function refresh(showMessage = true) {
  const requestId = ++refreshRequestId
  loading.value = true
  try {
    if (scopeOrgId.value == null) {
      if (requestId === refreshRequestId) {
        tableRows.value = []
        clearSelection()
      }
      return
    }
    const data = await listResultSuggestionsApi({
      scopeOrgId: scopeOrgId.value,
      startDate: query.dateRange?.[0],
      endDate: query.dateRange?.[1],
      suggestionStatus: suggestionStatusCodes[query.status] || undefined,
      evidenceStatus: query.evidence === '完整' ? 'COMPLETE' : query.evidence === '缺失' ? 'INCOMPLETE' : undefined,
    })
    if (requestId !== refreshRequestId) return
    tableRows.value = data.map(mapResultSuggestion)
    clearSelection()
    if (showMessage) ElMessage.success('成果建议列表已刷新')
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
    const task = await createLeaderExportTaskApi({
      dimensionType: 'RESULT_SUGGESTION_LIST',
      dimensionId: String(orgId),
      periodType: 'DAY',
      periodStart: query.dateRange?.[0],
      periodEnd: query.dateRange?.[1],
      formats: ['PDF'],
      includeEvidence: true,
    })
    const info = await getLeaderExportDownloadInfoApi(task.id)
    saveBlob(await downloadLeaderExportTaskApi(task.id), info.fileName)
    ElMessage.success(`成果建议清单已导出，校验值 ${info.checksum || '无'}`)
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    exporting.value = false
  }
}

async function reset() {
  autoQuery.pause()
  resetOrgScope()
  query.dateRange = currentMonthToDateRange()
  query.status = ''
  query.evidence = ''
  await refresh(false)
  autoQuery.resume()
  ElMessage.success('筛选条件已重置')
}

async function openEvidence(row: ResultSuggestionRow) {
  const requestId = ++evidenceRequestId
  evidenceVisible.value = true
  evidenceLoading.value = true
  activeRow.value = null
  aiReview.value = null
  evidenceFiles.value = []
  try {
    const detail = mapResultSuggestion(await getResultSuggestionApi(row.id))
    if (requestId !== evidenceRequestId) return
    activeRow.value = detail
    evidenceFiles.value = detail.evidences
    aiReview.value = await getLatestAiReviewApi('RESULT', Number(row.id))
  } catch (error) {
    if (requestId !== evidenceRequestId) return
    evidenceVisible.value = false
    ElMessage.error(errorMessage(error))
  } finally {
    if (requestId === evidenceRequestId) evidenceLoading.value = false
  }
}

function closeEvidence() {
  evidenceRequestId += 1
  evidenceLoading.value = false
  activeRow.value = null
  evidenceFiles.value = []
}

async function downloadEvidence(evidence: { id: number; name: string }) {
  if (!activeRow.value || downloadingEvidenceId.value !== null) return
  downloadingEvidenceId.value = evidence.id
  try {
    const blob = await downloadLeaderResultEvidenceApi(activeRow.value.id, evidence.id)
    saveBlob(blob, evidence.name)
    ElMessage.success('成果证据已下载')
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    downloadingEvidenceId.value = null
  }
}

function openSuggest(row: ResultSuggestionRow, type: string) {
  if (row.terminal) {
    ElMessage.warning('成果已完成最终处理，不能再次提交建议')
    return
  }
  activeRow.value = row
  Object.assign(suggestForm, {
    type,
    comment: type === '建议确认' ? '证据和完成比例基本满足确认条件。' : '证据不完整，建议驳回补充后重提。',
  })
  suggestVisible.value = true
}

function onSelectionChange(rows: ResultSuggestionRow[]) {
  selectedRows.value = rows
}

function canSelect(row: ResultSuggestionRow) {
  return !row.terminal && !actionBusy.value
}

function clearSelection() {
  tableRef.value?.clearSelection?.()
  selectedRows.value = []
}

async function batchSuggest(type: string) {
  if (actionBusy.value) return
  try {
    const rows = selectedRows.value.filter((row) => !row.terminal)
    const skippedCount = selectedRows.value.length - rows.length
    if (!rows.length) {
      ElMessage.warning('当前选择中没有可提交建议的成果')
      return
    }
    await ElMessageBox.confirm(`确认对选中的 ${rows.length} 条成果提交“${type}”？`, '批量提交建议', { type: 'warning' })
    batchAction.value = type
    const results = await batchSubmitResultSuggestionsApi({
      ids: rows.map((row) => row.id),
      decision: suggestionStatusCodes[type] || 'SUGGEST_CONFIRM',
      comment: type === '建议确认' ? '批量建议确认。' : '批量建议驳回并补充证据。',
      notifyEmployee: true,
    })
    rows.forEach((row) => { row.suggestion = type })
    clearSelection()
    ElMessage.success(`已对 ${results.length} 条成果批量提交${type}${skippedCount ? `，跳过 ${skippedCount} 条已终结成果` : ''}`)
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
    ElMessage.error(errorMessage(error))
  } finally {
    batchAction.value = ''
  }
}

async function submitSuggest() {
  if (!activeRow.value || submitting.value) return
  const comment = suggestForm.comment.trim()
  if (!comment) {
    ElMessage.warning('请填写建议说明')
    return
  }
  submitting.value = true
  try {
    const result = await submitResultSuggestionApi(activeRow.value.id, {
      decision: suggestionStatusCodes[suggestForm.type] || 'SUGGEST_CONFIRM',
      comment,
      notifyEmployee: true,
    })
    activeRow.value.suggestion = suggestForm.type
    suggestVisible.value = false
    ElMessage.success(result.message)
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    submitting.value = false
  }
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
