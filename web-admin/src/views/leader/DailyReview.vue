<template>
  <section v-loading="loading" class="page-panel">
    <div class="page-header">
      <div>
        <h1 class="page-title">日计划初审点评</h1>
        <p class="page-subtitle">查看授权下属日计划，提交点评并标记风险，不做最终审批。</p>
      </div>
      <div class="toolbar">
        <el-button :icon="Refresh" :disabled="actionBusy" @click="refresh()">刷新</el-button>
        <el-button type="primary" :icon="Download" :loading="exporting" :disabled="loading || actionBusy" @click="exportList">导出清单</el-button>
      </div>
    </div>

    <AiReviewPanel
      class="mt16"
      :review="null"
      :display-report="false"
      title="AI点评辅助"
      empty-text="员工提交日计划前会自动生成AI检查报告；点击列表中的“详情与AI”，可查看字段缺失、内容不合规和风险判断依据。"
    />

    <div class="status-strip mt16">
      <span class="status-pill"><strong>{{ pendingCount }}</strong> 条待处理</span>
      <span class="status-pill"><strong>{{ selectedRows.length }}</strong> 条已选</span>
      <span class="status-pill"><strong>{{ riskCount }}</strong> 条风险项</span>
      <span class="status-pill"><strong>{{ missingCount }}</strong> 条字段缺失</span>
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
      <el-select v-model="query.status" clearable placeholder="点评状态">
        <el-option label="待点评" value="待点评" />
        <el-option label="已点评" value="已点评" />
        <el-option label="风险" value="风险" />
        <el-option label="风险已复核" value="风险已复核" />
        <el-option label="需补充" value="需补充" />
      </el-select>
      <el-select v-model="query.missing" clearable placeholder="必要字段">
        <el-option label="完整" value="无" />
        <el-option label="有缺失" value="missing" />
      </el-select>
      <el-button :icon="Search" @click="refresh()">查询</el-button>
      <el-button @click="reset">重置</el-button>
    </div>

    <div v-if="selectedRows.length" class="batch-bar">
      <div>已选择 <strong>{{ selectedRows.length }}</strong> 条日计划，可批量处理。</div>
      <div class="batch-actions">
        <el-button size="small" type="primary" :loading="batchAction === 'comment'" :disabled="actionBusy && batchAction !== 'comment'" @click="batchComment">批量点评</el-button>
        <el-button size="small" type="warning" :loading="batchAction === 'risk'" :disabled="actionBusy && batchAction !== 'risk'" @click="batchRisk">批量标记风险</el-button>
        <el-button size="small" :disabled="actionBusy" @click="clearSelection">清空选择</el-button>
      </div>
    </div>

    <el-table
      ref="tableRef"
      :data="tableRows"
      border
      highlight-current-row
      @selection-change="onSelectionChange"
      @row-dblclick="openDetail"
    >
      <el-table-column type="selection" width="48" :selectable="canSelect" />
      <el-table-column prop="employee" label="员工" width="100" />
      <el-table-column prop="date" label="日期" width="120" />
      <el-table-column prop="content" label="计划内容" min-width="220" show-overflow-tooltip />
      <el-table-column prop="deliverable" label="交付物" width="140">
        <template #default="{ row }">
          <span :class="{ 'text-danger': !row.deliverable }">{{ row.deliverable || '未填写' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="审批时限" width="120">
        <template #default="{ row }">
          <el-tag :type="row.deadline.includes('逾期') ? 'danger' : 'success'">{{ row.deadline }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="必要字段" width="130">
        <template #default="{ row }">
          <el-tag :type="row.missing === '无' ? 'success' : 'danger'">{{ row.missing }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="aiRisk" label="系统检查" width="140" />
      <el-table-column label="操作" width="250" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" :icon="View" @click="openDetail(row)">详情与AI</el-button>
          <el-button link type="primary" :icon="EditPen" :disabled="!canReview(row) || actionBusy" @click="openComment(row)">点评</el-button>
          <el-button link type="warning" :icon="Warning" :disabled="!canReview(row) || actionBusy" @click="markRisk(row)">标记风险</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-drawer v-model="detailVisible" title="日计划详情" size="520px">
      <div v-if="activeRow" class="drawer-stack">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="员工">{{ activeRow.employee }}</el-descriptions-item>
          <el-descriptions-item label="日期">{{ activeRow.date }}</el-descriptions-item>
          <el-descriptions-item label="计划内容">{{ activeRow.content }}</el-descriptions-item>
          <el-descriptions-item label="交付物">{{ activeRow.deliverable || '未填写' }}</el-descriptions-item>
          <el-descriptions-item label="必要字段">{{ activeRow.missing }}</el-descriptions-item>
        </el-descriptions>
        <AiReviewPanel :review="aiReview" compact empty-text="该日计划尚未生成AI检查记录。" />
        <div class="section-card compact">
          <div class="section-title">过程留痕</div>
          <el-timeline>
            <el-timeline-item :timestamp="formatDateTime(activeRow.submittedAt)">员工提交日计划</el-timeline-item>
            <el-timeline-item :timestamp="formatDateTime(activeRow.submittedAt)">
              系统字段完整性检查：{{ activeRow.missing === '无' ? '通过' : `缺少${activeRow.missing}` }}
            </el-timeline-item>
            <el-timeline-item :timestamp="activeRow.reviewedAt ? formatDateTime(activeRow.reviewedAt) : '待处理'">
              {{ activeRow.reviewedAt ? '直属领导已完成点评' : '等待直属领导点评' }}
            </el-timeline-item>
          </el-timeline>
        </div>
      </div>
    </el-drawer>

    <el-dialog v-model="commentVisible" title="提交点评" width="560px">
      <el-form label-position="top">
        <el-form-item label="点评结论">
          <el-select v-model="commentForm.result">
            <el-option label="建议继续执行" value="建议继续执行" />
            <el-option label="存在风险" value="存在风险" />
          </el-select>
        </el-form-item>
        <el-form-item label="风险等级">
          <el-radio-group v-model="commentForm.risk">
            <el-radio-button label="低" />
            <el-radio-button label="中" />
            <el-radio-button label="高" />
          </el-radio-group>
        </el-form-item>
        <el-form-item label="点评内容">
          <el-input v-model="commentForm.comment" type="textarea" :rows="4" maxlength="300" show-word-limit :disabled="submitting" />
        </el-form-item>
        <el-form-item label="通知员工">
          <el-switch v-model="commentForm.notify" :disabled="submitting" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button :disabled="submitting" @click="commentVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitComment">提交点评</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Download, EditPen, Refresh, Search, View, Warning } from '@element-plus/icons-vue'
import {
  batchCommentDailyReviewsApi,
  batchMarkDailyReviewRisksApi,
  commentDailyReviewApi,
  createLeaderExportTaskApi,
  downloadLeaderExportTaskApi,
  getDailyReviewApi,
  getLeaderExportDownloadInfoApi,
  listDailyReviewsApi,
  markDailyReviewRiskApi,
} from '@/api/leader'
import { currentWeekDateRange, errorMessage } from '@/api/performance'
import { useAutoQuery } from '@/composables/useAutoQuery'
import { useLeaderOrgScope } from '@/composables/useLeaderOrgScope'
import { saveBlob } from '@/utils/download'
import { formatDateTime, mapDailyReview, reviewStatusCodes } from '@/views/performanceAdapters'
import { getLatestAiReviewApi, type AiReview } from '@/api/aiReview'
import AiReviewPanel from '@/components/AiReviewPanel.vue'

type DailyReviewRow = ReturnType<typeof mapDailyReview>

const route = useRoute()
const tableRef = ref()
const { orgOptions, scopeOrgId, orgLoading, loadOrgScope, resetOrgScope } = useLeaderOrgScope()
const routeDate = typeof route.query.date === 'string' ? route.query.date : ''
const routeDayPlanId = typeof route.query.dayPlanId === 'string' ? route.query.dayPlanId : ''
const query = reactive({
  dateRange: routeDate ? [routeDate, routeDate] : currentWeekDateRange(),
  status: '',
  missing: '',
})
const tableRows = ref<DailyReviewRow[]>([])
const selectedRows = ref<DailyReviewRow[]>([])
const detailVisible = ref(false)
const aiReview = ref<AiReview | null>(null)
const commentVisible = ref(false)
const activeRow = ref<DailyReviewRow | null>(null)
const loading = ref(false)
const submitting = ref(false)
const batchAction = ref('')
const exporting = ref(false)
let refreshRequestId = 0
const commentForm = reactive({
  result: '建议继续执行',
  risk: '低',
  comment: '',
  notify: true,
})
const riskCount = computed(() => tableRows.value.filter((row) => row.riskLevel === '高').length)
const missingCount = computed(() => tableRows.value.filter((row) => row.missing !== '无').length)
const pendingCount = computed(() => tableRows.value.filter(canReview).length)
const actionBusy = computed(() => submitting.value || Boolean(batchAction.value))
const autoQuery = useAutoQuery(
  () => [scopeOrgId.value, query.dateRange?.[0], query.dateRange?.[1], query.status, query.missing],
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
    const data = await listDailyReviewsApi({
      scopeOrgId: scopeOrgId.value,
      startDate: query.dateRange?.[0] || undefined,
      endDate: query.dateRange?.[1] || undefined,
      reviewStatus: reviewStatusCodes[query.status] || undefined,
      missingOnly: query.missing === 'missing' ? true : query.missing === '无' ? false : undefined,
    })
    if (requestId !== refreshRequestId) return
    tableRows.value = data.map(mapDailyReview)
    clearSelection()
    if (showMessage) ElMessage.success('查询条件已应用')
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
      dimensionType: 'DAILY_REVIEW_LIST',
      dimensionId: String(orgId),
      periodType: 'DAY',
      periodStart: query.dateRange?.[0],
      periodEnd: query.dateRange?.[1],
      formats: ['PDF'],
      includeEvidence: false,
    })
    const info = await getLeaderExportDownloadInfoApi(task.id)
    saveBlob(await downloadLeaderExportTaskApi(task.id), info.fileName)
    ElMessage.success(`日计划点评清单已导出，校验值 ${info.checksum || '无'}`)
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    exporting.value = false
  }
}

async function reset() {
  autoQuery.pause()
  resetOrgScope()
  query.dateRange = currentWeekDateRange()
  query.status = ''
  query.missing = ''
  await refresh(false)
  autoQuery.resume()
  ElMessage.success('筛选条件已重置')
}

async function openDetail(row: DailyReviewRow) {
  await openDetailById(row.id)
}

async function openDetailById(id: string) {
  try {
    activeRow.value = mapDailyReview(await getDailyReviewApi(id))
    aiReview.value = await getLatestAiReviewApi('DAY_PLAN', Number(id))
    detailVisible.value = true
  } catch (error) {
    ElMessage.error(errorMessage(error))
  }
}

function openComment(row: DailyReviewRow) {
  activeRow.value = row
  const hasMissingFields = row.missing !== '无'
  Object.assign(commentForm, {
    result: hasMissingFields ? '存在风险' : '建议继续执行',
    risk: hasMissingFields ? '中' : row.riskLevel,
    comment: hasMissingFields ? `必要字段缺失：${row.missing}，请补充后跟进。` : '',
    notify: true,
  })
  commentVisible.value = true
}

function markRisk(row: DailyReviewRow) {
  activeRow.value = row
  Object.assign(commentForm, {
    result: '存在风险',
    risk: '高',
    comment: `${row.id} 已标记风险，请补充必要字段和证据。`,
    notify: true,
  })
  commentVisible.value = true
}

function onSelectionChange(rows: DailyReviewRow[]) {
  selectedRows.value = rows
}

function canReview(row: DailyReviewRow) {
  return row.reviewStatus === '待点评'
}

function canSelect(row: DailyReviewRow) {
  return canReview(row) && !actionBusy.value
}

function clearSelection() {
  tableRef.value?.clearSelection?.()
  selectedRows.value = []
}

async function batchComment() {
  const rows = [...selectedRows.value]
  if (!rows.length || actionBusy.value) return
  try {
    await ElMessageBox.confirm(`确认对选中的 ${rows.length} 条日计划提交“建议继续执行”点评？`, '批量点评', { type: 'warning' })
    batchAction.value = 'comment'
    const results = await batchCommentDailyReviewsApi({
      ids: rows.map((row) => row.id),
      comment: '批量点评：建议按计划继续执行。',
      notifyEmployee: true,
    })
    rows.forEach((row) => { row.reviewStatus = '已点评' })
    clearSelection()
    ElMessage.success(`已对 ${results.length} 条记录提交批量点评`)
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
    ElMessage.error(errorMessage(error))
  } finally {
    batchAction.value = ''
  }
}

async function batchRisk() {
  const rows = [...selectedRows.value]
  if (!rows.length || actionBusy.value) return
  try {
    await ElMessageBox.confirm(`确认将选中的 ${rows.length} 条日计划标记为高风险并转入部门补审？`, '批量标记风险', { type: 'warning' })
    batchAction.value = 'risk'
    const results = await batchMarkDailyReviewRisksApi({
      ids: rows.map((row) => row.id),
      riskLevel: 'HIGH',
      comment: '批量标记风险，请补充必要字段和证据。',
      notifyEmployee: true,
    })
    rows.forEach((row) => {
      row.reviewStatus = '风险'
      row.riskLevel = '高'
    })
    clearSelection()
    ElMessage.warning(`已将 ${results.length} 条记录批量标记为风险`)
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
    ElMessage.error(errorMessage(error))
  } finally {
    batchAction.value = ''
  }
}

async function submitComment() {
  if (!activeRow.value || submitting.value) return
  const comment = commentForm.comment.trim()
  if (!comment) {
    ElMessage.warning(commentForm.result === '存在风险' ? '请填写风险说明' : '请填写点评内容')
    return
  }
  submitting.value = true
  try {
    const riskCodes: Record<string, string> = { '低': 'LOW', '中': 'MEDIUM', '高': 'HIGH' }
    const payload = {
      comment: `${commentForm.result}：${comment}`,
      riskLevel: riskCodes[commentForm.risk] || 'LOW',
      notifyEmployee: commentForm.notify,
    }
    const result = commentForm.result === '存在风险'
      ? await markDailyReviewRiskApi(activeRow.value.id, payload)
      : await commentDailyReviewApi(activeRow.value.id, payload)
    activeRow.value.reviewStatus = commentForm.result === '存在风险' ? '风险' : '已点评'
    activeRow.value.riskLevel = commentForm.risk
    activeRow.value.leaderComment = payload.comment
    commentVisible.value = false
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
    if (routeDayPlanId) {
      await openDetailById(routeDayPlanId)
    }
  } catch (error) {
    ElMessage.error(errorMessage(error))
  }
})
</script>
