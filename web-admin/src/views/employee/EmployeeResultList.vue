<template>
  <section class="page-panel employee-result-list">
    <header class="result-page-header">
      <div>
        <span class="result-eyebrow">RESULT ARCHIVE</span>
        <h1 class="page-title">成果记录</h1>
        <p class="page-subtitle">集中查看成果版本、处理进度与确认依据。</p>
      </div>
      <el-button type="primary" :icon="Upload" @click="router.push('/employee/results/submit')">提交成果</el-button>
    </header>

    <div class="result-summary-rail" aria-label="成果状态概览">
      <button type="button" :class="{ 'is-active': !query.status }" @click="resetQuery">
        <span>本月记录</span><strong>{{ resultSummary.all }}</strong><small>当前日期范围</small>
      </button>
      <button type="button" class="is-pending" :class="{ 'is-active': query.status === 'PENDING' }" @click="applyStatusFilter('PENDING')">
        <span>待确认</span><strong>{{ resultSummary.pending }}</strong><small>等待后续处理</small>
      </button>
      <button type="button" class="is-confirmed" :class="{ 'is-active': query.status === 'CONFIRMED' }" @click="applyStatusFilter('CONFIRMED')">
        <span>已确认</span><strong>{{ resultSummary.confirmed }}</strong><small>已形成成果依据</small>
      </button>
      <button type="button" class="is-rejected" :class="{ 'is-active': query.status === 'REJECTED' }" @click="applyStatusFilter('REJECTED')">
        <span>已驳回</span><strong>{{ resultSummary.rejected }}</strong><small>需要补充或修订</small>
      </button>
    </div>

    <section class="result-filter-bar">
      <el-input v-model="query.keyword" :prefix-icon="Search" clearable placeholder="搜索成果标题或说明" @keyup.enter="loadList" />
      <el-date-picker v-model="dateRange" type="daterange" value-format="YYYY-MM-DD" range-separator="至" start-placeholder="开始日期" end-placeholder="结束日期" />
      <el-radio-group v-model="query.status" class="status-filter">
        <el-radio-button label="">全部</el-radio-button>
        <el-radio-button label="PENDING">待确认</el-radio-button>
        <el-radio-button label="CONFIRMED">已确认</el-radio-button>
        <el-radio-button label="REJECTED">已驳回</el-radio-button>
      </el-radio-group>
      <div class="filter-actions">
        <el-button :icon="Refresh" @click="resetQuery">重置</el-button>
        <el-button type="primary" :icon="Search" :loading="loading" @click="loadList">查询</el-button>
      </div>
    </section>

    <el-alert v-if="errorMessage" class="dashboard-alert" type="warning" :closable="false" show-icon :title="errorMessage" />

    <section class="result-table-shell">
      <div class="table-caption">
        <div><strong>成果清单</strong><span>共 {{ rows.length }} 条记录</span></div>
        <span>{{ query.startDate || '不限日期' }} 至 {{ query.endDate || '不限日期' }}</span>
      </div>
      <el-table v-loading="loading" :data="pagedRows" class="result-table" row-class-name="result-table-row" @row-dblclick="openDetailFromRow">
        <el-table-column label="成果信息" min-width="330">
          <template #default="{ row }">
            <div class="result-main-cell">
              <button type="button" @click="openDetail(row.id)">{{ row.title }}</button>
              <p>{{ row.content || '未填写成果说明' }}</p>
              <span>{{ row.versionNo || '未标记版本' }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="关联计划" min-width="170">
          <template #default="{ row }">
            <div class="result-plan-cell">
              <el-icon><Link /></el-icon>
              <div><strong>{{ row.temporary ? '临时成果' : planTypeLabel(row.planType) }}</strong><span>{{ row.temporary ? (row.temporaryReason || '未关联计划') : `计划 #${row.planId}` }}</span></div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="完成比例" width="150">
          <template #default="{ row }">
            <div class="completion-cell"><strong>{{ row.completionRate ?? 0 }}%</strong><el-progress :percentage="row.completionRate ?? 0" :stroke-width="7" :show-text="false" /></div>
          </template>
        </el-table-column>
        <el-table-column prop="resultDate" label="成果日期" width="120" />
        <el-table-column label="状态" width="105">
          <template #default="{ row }"><el-tag :type="statusMeta(row.status).type" effect="light">{{ statusMeta(row.status).label }}</el-tag></template>
        </el-table-column>
        <el-table-column label="操作" width="70" fixed="right" align="center">
          <template #default="{ row }">
            <el-tooltip content="查看成果详情" placement="top"><el-button circle text type="primary" :icon="View" aria-label="查看成果详情" @click="openDetail(row.id)" /></el-tooltip>
          </template>
        </el-table-column>
        <template #empty>
          <div class="result-empty-state">
            <el-icon><DocumentRemove /></el-icon>
            <strong>{{ hasActiveFilters ? '没有符合条件的成果记录' : '还没有成果记录' }}</strong>
            <span>{{ hasActiveFilters ? '调整关键词或状态后重新查询' : '提交第一份成果，开始沉淀交付记录' }}</span>
            <el-button v-if="hasActiveFilters" @click="resetQuery">清空筛选</el-button>
            <el-button v-else type="primary" @click="router.push('/employee/results/submit')">提交成果</el-button>
          </div>
        </template>
      </el-table>

      <div class="pagination-row">
        <el-pagination v-model:current-page="currentPage" :page-size="pageSize" layout="prev, pager, next, total" :total="rows.length" />
      </div>
    </section>

    <el-drawer v-model="detailVisible" class="result-detail-drawer" title="成果详情" size="680px" @closed="closeDetail">
      <div class="detail-drawer-body">
        <el-skeleton v-if="detailLoading" :rows="7" animated />
        <el-alert v-else-if="detailError" type="error" :closable="false" show-icon :title="detailError" />
        <template v-else-if="activeDetail">
          <section class="detail-summary">
            <div class="detail-summary-head">
              <div><span>{{ activeDetail.resultNo }} · {{ activeDetail.versionNo }}</span><h2>{{ activeDetail.title }}</h2></div>
              <el-tag :type="detailStatusMeta(activeDetail.status).type" size="large">{{ detailStatusMeta(activeDetail.status).label }}</el-tag>
            </div>
            <p>{{ activeDetail.description || '未填写成果说明' }}</p>
            <div class="detail-completion"><strong>{{ activeDetail.completionRate }}%</strong><div><span>成果完成比例</span><el-progress :percentage="activeDetail.completionRate" :stroke-width="8" :show-text="false" /></div></div>
            <div class="detail-meta-grid">
              <div><span>关联计划</span><strong>{{ activeDetail.planTitle || '未关联' }}</strong></div>
              <div><span>关联事项</span><strong>{{ activeDetail.planItemName || '整份月计划' }}</strong></div>
              <div><span>成果日期</span><strong>{{ activeDetail.resultDate }}</strong></div>
              <div><span>证据状态</span><strong>{{ evidenceStatusLabel(activeDetail.evidenceStatus) }}</strong></div>
            </div>
          </section>

          <section class="detail-section">
            <div class="detail-section-head"><span>PROCESS</span><h3>处理过程</h3></div>
            <el-timeline>
            <el-timeline-item :timestamp="dateTimeText(activeDetail.submittedAt)" type="primary">
              <strong>员工提交成果</strong>
            </el-timeline-item>
            <el-timeline-item
              v-if="activeDetail.suggestedAt || activeDetail.leaderSuggestion"
              :timestamp="dateTimeText(activeDetail.suggestedAt)"
              :type="activeDetail.suggestionStatus === 'SUGGEST_REJECT' ? 'danger' : 'warning'"
            >
              <strong>直属领导建议</strong><p>{{ activeDetail.leaderSuggestion || '已提交建议' }}</p>
            </el-timeline-item>
            <el-timeline-item
              v-if="activeDetail.confirmedAt || activeDetail.confirmComment"
              :timestamp="dateTimeText(activeDetail.confirmedAt)"
              :type="activeDetail.status === 'confirmed' ? 'success' : 'danger'"
            >
              <strong>部门负责人确认</strong><p>{{ activeDetail.confirmComment || '已完成最终处理' }}</p>
            </el-timeline-item>
            </el-timeline>

            <el-alert v-if="activeDetail.issueText || activeDetail.issueCodes.length" type="warning" :closable="false" show-icon :title="activeDetail.issueText || activeDetail.issueCodes.join('、')" />
          </section>

          <section class="detail-section evidence-section">
            <div class="detail-section-head"><span>EVIDENCE</span><h3>成果证据</h3></div>
          <el-table :data="activeDetail.evidences" border empty-text="暂无成果证据">
            <el-table-column prop="fileName" label="文件名" min-width="180" show-overflow-tooltip />
            <el-table-column prop="fileType" label="类型" width="90" />
            <el-table-column label="大小" width="100">
              <template #default="{ row }">{{ fileSizeText(row.fileSize) }}</template>
            </el-table-column>
            <el-table-column label="校验" min-width="150" show-overflow-tooltip>
              <template #default="{ row }">{{ row.checksum || '待生成' }}</template>
            </el-table-column>
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="row.reviewPassed ? 'success' : 'info'">{{ row.reviewPassed ? '已通过' : '已上传' }}</el-tag>
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
          </section>
        </template>
      </div>
    </el-drawer>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { DocumentRemove, Download, Link, Refresh, Search, Upload, View } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import { listResultsApi, type ResultItem } from '@/api/planning'
import {
  downloadEmployeeResultEvidenceApi,
  getEmployeeResultDetailApi,
  type EmployeeResultDetailResp,
  type EmployeeResultEvidence,
} from '@/api/employee'
import { currentMonthToDateRange } from '@/api/performance'
import { saveBlob } from '@/utils/download'

const router = useRouter()
const loading = ref(false)
const errorMessage = ref('')
const rows = ref<ResultItem[]>([])
const summaryRows = ref<ResultItem[]>([])
const initialDateRange = currentMonthToDateRange()
const query = reactive({
  keyword: '',
  startDate: initialDateRange[0],
  endDate: initialDateRange[1],
  status: '',
})
const currentPage = ref(1)
const detailVisible = ref(false)
const detailLoading = ref(false)
const detailError = ref('')
const activeDetail = ref<EmployeeResultDetailResp | null>(null)
const downloadingEvidenceId = ref<number | null>(null)
let detailRequestSequence = 0
const pageSize = 10
const dateRange = computed<[string, string] | null>({
  get: (): [string, string] | null => query.startDate && query.endDate
    ? [query.startDate, query.endDate]
    : null,
  set: (value: [string, string] | null) => {
    query.startDate = value?.[0] ?? ''
    query.endDate = value?.[1] ?? ''
  },
})
const pagedRows = computed(() => {
  const start = (currentPage.value - 1) * pageSize
  return rows.value.slice(start, start + pageSize)
})
const resultSummary = computed(() => ({
  all: summaryRows.value.length,
  pending: summaryRows.value.filter((row) => row.status === 'PENDING').length,
  confirmed: summaryRows.value.filter((row) => row.status === 'CONFIRMED').length,
  rejected: summaryRows.value.filter((row) => row.status === 'REJECTED').length,
}))
const hasActiveFilters = computed(() => Boolean(query.keyword || query.status))

const resultStatuses: Record<string, { label: string; type: 'info' | 'warning' | 'success' | 'danger' }> = {
  DRAFT: { label: '草稿', type: 'info' },
  PENDING: { label: '待确认', type: 'warning' },
  CONFIRMED: { label: '已确认', type: 'success' },
  REJECTED: { label: '已驳回', type: 'danger' },
}

function statusMeta(status: string) {
  return resultStatuses[status] ?? { label: status, type: 'info' as const }
}

function detailStatusMeta(status: string) {
  return ({
    draft: { label: '草稿', type: 'info' as const },
    submitted: { label: '待确认', type: 'warning' as const },
    confirmed: { label: '已确认', type: 'success' as const },
    rejected: { label: '已驳回', type: 'danger' as const },
    not_submitted: { label: '未提交', type: 'info' as const },
  } as Record<string, { label: string; type: 'info' | 'warning' | 'success' | 'danger' }>)[status]
    ?? { label: status, type: 'info' as const }
}

function planTypeLabel(type: string) {
  return ({ MONTH: '月计划', DAY: '日计划', TEMP: '临时成果' } as Record<string, string>)[type] || type
}

function evidenceStatusLabel(status: string) {
  return ({ COMPLETE: '完整', MISSING: '缺失', INCOMPLETE: '不完整' } as Record<string, string>)[status] || status
}

function dateTimeText(value?: string) {
  return value ? value.replace('T', ' ').slice(0, 19) : '时间未记录'
}

function fileSizeText(size: number) {
  if (!size) return '--'
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / 1024 / 1024).toFixed(1)} MB`
}

async function openDetail(id: number) {
  const requestSequence = ++detailRequestSequence
  detailVisible.value = true
  detailLoading.value = true
  detailError.value = ''
  activeDetail.value = null
  try {
    const detail = await getEmployeeResultDetailApi(id)
    if (requestSequence !== detailRequestSequence) return
    activeDetail.value = detail
  } catch (error) {
    if (requestSequence !== detailRequestSequence) return
    detailError.value = error instanceof Error ? error.message : '成果详情加载失败'
  } finally {
    if (requestSequence === detailRequestSequence) detailLoading.value = false
  }
}

function closeDetail() {
  detailRequestSequence += 1
  activeDetail.value = null
  detailError.value = ''
}

async function downloadEvidence(evidence: EmployeeResultEvidence) {
  const detail = activeDetail.value
  if (!detail || downloadingEvidenceId.value !== null) return
  downloadingEvidenceId.value = evidence.id
  try {
    const blob = await downloadEmployeeResultEvidenceApi(detail.id, evidence.id)
    saveBlob(blob, evidence.fileName)
    ElMessage.success('成果证据已下载')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '成果证据下载失败')
  } finally {
    downloadingEvidenceId.value = null
  }
}

async function loadList() {
  if (query.startDate && query.endDate && query.startDate > query.endDate) {
    ElMessage.warning('开始日期不能晚于结束日期')
    return
  }
  loading.value = true
  errorMessage.value = ''
  try {
    rows.value = await listResultsApi({ ...query, mine: true })
    if (!query.status) summaryRows.value = rows.value
    currentPage.value = 1
  } catch (error) {
    rows.value = []
    if (!query.status) summaryRows.value = []
    errorMessage.value = error instanceof Error ? error.message : '成果记录加载失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

async function resetQuery() {
  const dateRange = currentMonthToDateRange()
  Object.assign(query, { keyword: '', startDate: dateRange[0], endDate: dateRange[1], status: '' })
  await loadList()
}

function openDetailFromRow(row: ResultItem) {
  void openDetail(row.id)
}

async function applyStatusFilter(status: string) {
  query.status = status
  await loadList()
}

onMounted(loadList)
</script>

<style scoped>
.employee-result-list {
  --result-accent: #2d776c;
}

.result-page-header,
.table-caption,
.table-caption > div,
.result-plan-cell,
.detail-summary-head,
.detail-completion {
  display: flex;
  align-items: center;
}

.result-page-header {
  justify-content: space-between;
  gap: 24px;
  padding-bottom: 22px;
  border-bottom: 1px solid var(--line);
}

.result-eyebrow {
  display: block;
  margin-bottom: 8px;
  color: var(--result-accent);
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0;
}

.result-summary-rail {
  display: grid;
  grid-template-columns: repeat(4, minmax(150px, 1fr));
  margin: 18px 0;
  overflow: hidden;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: #f7f8f4;
}

.result-summary-rail button {
  position: relative;
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 4px 12px;
  min-height: 94px;
  padding: 17px 18px;
  border: 0;
  border-right: 1px solid var(--line);
  color: var(--ink);
  background: transparent;
  text-align: left;
  cursor: pointer;
}

.result-summary-rail button:last-child { border-right: 0; }
.result-summary-rail button::after { position: absolute; inset: auto 0 0; height: 3px; background: transparent; content: ''; }
.result-summary-rail button:hover, .result-summary-rail button.is-active { background: #eef5f2; }
.result-summary-rail button.is-active::after { background: var(--result-accent); }
.result-summary-rail button.is-pending.is-active::after { background: #bd7a35; }
.result-summary-rail button.is-confirmed.is-active::after { background: #2b8b68; }
.result-summary-rail button.is-rejected.is-active::after { background: #b9574e; }
.result-summary-rail span { align-self: end; color: var(--muted); font-size: 14px; font-weight: 650; }
.result-summary-rail strong { grid-row: 1 / 3; grid-column: 2; align-self: center; font-family: 'Cascadia Mono', monospace; font-size: 30px; font-variant-numeric: tabular-nums; }
.result-summary-rail small { color: #7d8d87; font-size: 12px; line-height: 1.45; }

.result-filter-bar {
  display: grid;
  grid-template-columns: minmax(220px, 1fr) 300px auto auto;
  gap: 12px;
  align-items: center;
  margin-bottom: 16px;
  padding: 14px;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: #f6f7f2;
}

.result-filter-bar :deep(.el-date-editor) { width: 100%; }
.status-filter { display: flex; flex-wrap: nowrap; }
.filter-actions { display: flex; gap: 8px; }
.filter-actions .el-button { margin: 0; }

.result-table-shell { overflow: hidden; border: 1px solid var(--line); border-radius: 8px; background: var(--surface); }
.table-caption { justify-content: space-between; gap: 16px; min-height: 54px; padding: 12px 15px; border-bottom: 1px solid var(--line); }
.table-caption > div { gap: 10px; }
.table-caption strong { font-size: 16px; }
.table-caption span { color: var(--muted); font-size: 13px; }
.result-table :deep(th.el-table__cell) { height: 50px; color: #50635e; background: #f6f8f4; font-size: 13px; font-weight: 750; }
.result-table :deep(td.el-table__cell) { padding-block: 14px; font-size: 14px; }
.result-table :deep(.el-table__row) { cursor: default; }
.result-table :deep(.el-table__row:hover > td.el-table__cell) { background: #f2f7f4; }

.result-main-cell { display: grid; min-width: 0; gap: 5px; padding: 4px 0; }
.result-main-cell button { width: fit-content; max-width: 100%; padding: 0; overflow: hidden; border: 0; color: var(--ink); background: transparent; font-size: 15px; font-weight: 750; text-align: left; text-overflow: ellipsis; white-space: nowrap; cursor: pointer; }
.result-main-cell button:hover { color: var(--result-accent); }
.result-main-cell p { display: -webkit-box; margin: 0; overflow: hidden; color: var(--muted); font-size: 13px; line-height: 1.6; -webkit-box-orient: vertical; -webkit-line-clamp: 2; }
.result-main-cell span { width: fit-content; padding: 3px 7px; border-radius: 4px; color: #58726b; background: #e9f0ec; font-family: 'Cascadia Mono', monospace; font-size: 12px; }

.result-plan-cell { gap: 9px; min-width: 0; }
.result-plan-cell > .el-icon { flex: 0 0 auto; color: var(--result-accent); }
.result-plan-cell > div { display: grid; min-width: 0; gap: 3px; }
.result-plan-cell strong, .result-plan-cell span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.result-plan-cell strong { font-size: 14px; }
.result-plan-cell span { color: var(--muted); font-size: 12px; }
.completion-cell { display: grid; grid-template-columns: 42px 1fr; gap: 8px; align-items: center; }
.completion-cell strong { font-size: 13px; font-variant-numeric: tabular-nums; }
.pagination-row { display: flex; justify-content: flex-end; padding: 14px; border-top: 1px solid var(--line); }

.result-empty-state { display: grid; justify-items: center; gap: 7px; padding: 48px 20px; color: var(--muted); }
.result-empty-state .el-icon { color: #8fa49d; font-size: 28px; }
.result-empty-state strong { color: var(--ink); font-size: 15px; }
.result-empty-state span { font-size: 13px; }
.result-empty-state .el-button { margin-top: 8px; }

.detail-drawer-body { padding-bottom: 24px; }
.detail-summary { padding: 20px; border: 1px solid var(--line); border-radius: 8px; background: #f7f9f5; }
.detail-summary-head { justify-content: space-between; gap: 16px; }
.detail-summary-head > div { min-width: 0; }
.detail-summary-head span { color: var(--muted); font-family: 'Cascadia Mono', monospace; font-size: 12px; }
.detail-summary-head h2 { margin: 6px 0 0; font-size: 21px; }
.detail-summary > p { margin: 16px 0; color: #536862; font-size: 14px; line-height: 1.75; }
.detail-completion { gap: 15px; padding: 13px 0; border-top: 1px solid var(--line); border-bottom: 1px solid var(--line); }
.detail-completion > strong { font-size: 29px; font-variant-numeric: tabular-nums; }
.detail-completion > div { display: grid; flex: 1; gap: 7px; }
.detail-completion span { color: var(--muted); font-size: 13px; }
.detail-meta-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 14px; padding-top: 15px; }
.detail-meta-grid > div { display: grid; min-width: 0; gap: 4px; }
.detail-meta-grid span { color: var(--muted); font-size: 12px; }
.detail-meta-grid strong { overflow: hidden; font-size: 14px; text-overflow: ellipsis; white-space: nowrap; }

.detail-section { margin-top: 18px; padding-top: 18px; border-top: 1px solid var(--line); }
.detail-section-head { margin-bottom: 16px; }
.detail-section-head span { color: var(--result-accent); font-size: 12px; font-weight: 800; }
.detail-section-head h3 { margin: 5px 0 0; font-size: 17px; }
.detail-section :deep(.el-timeline-item__content strong) { font-size: 14px; }
.detail-section :deep(.el-timeline-item__content p) { margin: 6px 0 0; color: var(--muted); font-size: 13px; line-height: 1.7; }
.evidence-section :deep(.el-table) { font-size: 13px; }

@media (max-width: 1360px) {
  .result-filter-bar { grid-template-columns: minmax(200px, 1fr) 280px auto; }
  .filter-actions { grid-column: 1 / -1; justify-content: flex-end; }
}
</style>
