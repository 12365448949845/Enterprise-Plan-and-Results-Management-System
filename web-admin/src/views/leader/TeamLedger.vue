<template>
  <section v-loading="loading" class="page-panel">
    <div class="page-header">
      <div>
        <h1 class="page-title">下属台账</h1>
        <p class="page-subtitle">查询授权下属绩效依据、证据链和参考分，不自动裁定绩效。</p>
      </div>
      <div class="toolbar">
        <el-button :icon="Search" :disabled="exportingId !== null" @click="refresh()">查询</el-button>
        <el-button type="primary" :icon="Download" :loading="exportingId === 'all'" :disabled="loading || exportingId !== null" @click="exportLedger">导出</el-button>
      </div>
    </div>

    <div class="filter-bar">
      <el-select v-model="query.periodType" placeholder="周期类型">
        <el-option label="日" value="day" />
        <el-option label="月" value="month" />
        <el-option label="季度" value="quarter" />
        <el-option label="年度" value="year" />
      </el-select>
      <el-date-picker
        v-model="query.periodRange"
        type="daterange"
        value-format="YYYY-MM-DD"
        range-separator="至"
        start-placeholder="开始日期"
        end-placeholder="结束日期"
      />
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
      <el-input v-model="query.employee" clearable placeholder="员工姓名" />
      <el-select v-model="query.format" placeholder="导出格式">
        <el-option label="PDF" value="pdf" />
        <el-option label="Word" value="word" />
        <el-option label="Zip" value="zip" />
      </el-select>
    </div>

    <div class="metric-grid compact-grid">
      <div class="metric success">
        <span>平均闭环率</span>
        <strong>{{ averageRate }}%</strong>
      </div>
      <div class="metric primary">
        <span>下属人数</span>
        <strong>{{ tableRows.length }}</strong>
      </div>
      <div class="metric warning">
        <span>证据缺项</span>
        <strong>{{ missingEvidenceCount }}</strong>
      </div>
      <div class="metric danger">
        <span>逾期记录</span>
        <strong>{{ overdueCount }}</strong>
      </div>
    </div>

    <el-table :data="tableRows" border class="mt16">
      <el-table-column prop="employee" label="员工" width="100" />
      <el-table-column prop="group" label="组织" min-width="140" />
      <el-table-column prop="period" label="周期" width="110" />
      <el-table-column prop="planCount" label="计划数" width="100" />
      <el-table-column prop="resultCount" label="成果数" width="100" />
      <el-table-column prop="confirmedRate" label="确认比例" width="110" />
      <el-table-column prop="overdue" label="逾期" width="90" />
      <el-table-column prop="score" label="参考分" width="100" />
      <el-table-column label="证据完整性" width="130">
        <template #default="{ row }">
          <el-tag :type="row.evidence === '完整' ? 'success' : 'warning'">{{ row.evidence }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="210" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" :icon="View" @click="openDetail(row)">证据链</el-button>
          <el-button link type="primary" :icon="Download" :loading="exportingId === String(row.ownerId)" :disabled="exportingId !== null" @click="exportOne(row)">导出</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-drawer v-model="detailVisible" title="绩效依据证据链" size="560px">
      <div v-if="activeRow" class="drawer-stack">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="员工">{{ activeRow.employee }}</el-descriptions-item>
          <el-descriptions-item label="周期">{{ activeRow.period }}</el-descriptions-item>
          <el-descriptions-item label="计划 / 成果">{{ activeRow.planCount }} / {{ activeRow.resultCount }}</el-descriptions-item>
          <el-descriptions-item label="参考分">{{ activeRow.score }}</el-descriptions-item>
          <el-descriptions-item label="证据完整性">{{ activeRow.evidence }}</el-descriptions-item>
        </el-descriptions>
        <div class="section-card compact">
          <div class="section-title">资料包目录</div>
          <ul class="plain-list">
            <li>月计划与审批记录</li>
            <li>日计划点评记录</li>
            <li>成果证据和确认建议</li>
            <li>最终确认记录与证据附件</li>
          </ul>
        </div>
      </div>
    </el-drawer>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Download, Search, View } from '@element-plus/icons-vue'
import {
  downloadLeaderExportTaskApi,
  exportTeamLedgersApi,
  getLeaderExportDownloadInfoApi,
  listTeamLedgersApi,
} from '@/api/leader'
import { currentMonthDateRange, errorMessage, normalizeExportFormat } from '@/api/performance'
import { useAutoQuery } from '@/composables/useAutoQuery'
import { useLeaderOrgScope } from '@/composables/useLeaderOrgScope'
import { saveBlob } from '@/utils/download'
import { mapTeamLedger, periodTypeCode } from '@/views/performanceAdapters'

type TeamLedgerRow = ReturnType<typeof mapTeamLedger>

const query = reactive({
  periodType: 'month',
  periodRange: currentMonthDateRange(),
  employee: '',
  format: 'pdf',
})
const { orgOptions, scopeOrgId, orgLoading, loadOrgScope } = useLeaderOrgScope()
const activeRow = ref<TeamLedgerRow | null>(null)
const detailVisible = ref(false)
const tableRows = ref<TeamLedgerRow[]>([])
const loading = ref(false)
const exportingId = ref<string | null>(null)
let refreshRequestId = 0
const averageRate = computed(() => {
  if (!tableRows.value.length) return 0
  return Math.round(tableRows.value.reduce((sum, row) => sum + Number(row.confirmedRate.replace('%', '')), 0) / tableRows.value.length)
})
const missingEvidenceCount = computed(() => tableRows.value.filter((row) => row.evidence !== '完整').length)
const overdueCount = computed(() => tableRows.value.reduce((sum, row) => sum + row.overdue, 0))
const autoQuery = useAutoQuery(
  () => [scopeOrgId.value, query.periodType, query.periodRange?.[0], query.periodRange?.[1], query.employee],
  () => refresh(false),
)

async function refresh(showMessage = true) {
  const requestId = ++refreshRequestId
  loading.value = true
  try {
    if (scopeOrgId.value == null) {
      if (requestId === refreshRequestId) tableRows.value = []
      return
    }
    const data = await listTeamLedgersApi({
      scopeOrgId: scopeOrgId.value,
      periodType: periodTypeCode(query.periodType),
      periodStart: query.periodRange?.[0],
      periodEnd: query.periodRange?.[1],
      employeeName: query.employee || undefined,
    })
    if (requestId !== refreshRequestId) return
    tableRows.value = data.map(mapTeamLedger)
    if (showMessage) ElMessage.success('下属台账已刷新')
  } catch (error) {
    if (requestId !== refreshRequestId) return
    ElMessage.error(errorMessage(error))
  } finally {
    if (requestId === refreshRequestId) loading.value = false
  }
}

async function exportLedger() {
  const orgId = scopeOrgId.value
  if (orgId == null) {
    ElMessage.warning('当前账号没有可导出的授权组织')
    return
  }
  await createExportTask(String(orgId), '下属台账', false, 'all')
}

async function exportOne(row: TeamLedgerRow) {
  await createExportTask(String(row.ownerId), row.employee, true, String(row.ownerId))
}

function openDetail(row: TeamLedgerRow) {
  activeRow.value = row
  detailVisible.value = true
}

function formatText(format: string) {
  return ({ pdf: 'PDF', word: 'Word', zip: 'Zip' } as Record<string, string>)[format] || format
}

async function createExportTask(dimensionId: string, label: string, personal: boolean, loadingKey: string) {
  if (exportingId.value !== null) return
  exportingId.value = loadingKey
  try {
    const task = await exportTeamLedgersApi({
      dimensionType: personal ? 'PERSON_LEDGER' : 'SUBORDINATE_LEDGER',
      dimensionId,
      periodType: periodTypeCode(query.periodType),
      periodStart: query.periodRange?.[0],
      periodEnd: query.periodRange?.[1],
      formats: [normalizeExportFormat(query.format)],
      includeEvidence: true,
      watermark: '直属领导、员工、组织、导出时间、周期',
    })
    const info = await getLeaderExportDownloadInfoApi(task.id)
    const blob = await downloadLeaderExportTaskApi(task.id)
    saveBlob(blob, info.fileName)
    ElMessage.success(`${label} ${formatText(query.format)} 已导出，校验值 ${info.checksum || '无'}`)
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    exportingId.value = null
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
