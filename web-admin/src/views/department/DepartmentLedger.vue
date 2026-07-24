<template>
  <section v-loading="loading" class="page-panel">
    <div class="page-header">
      <div>
        <h1 class="page-title">部门台账</h1>
        <p class="page-subtitle">查询部门日、月、季度、年度绩效依据台账，并发起异步导出。</p>
      </div>
      <div class="toolbar">
        <el-button :icon="Search" @click="refresh()">查询</el-button>
        <el-button type="primary" :icon="Download" :loading="exportingId === 'all'" @click="exportLedger">异步导出</el-button>
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
        v-model="selectedOrgId"
        :loading="orgLoading"
        :disabled="!orgOptions.length"
        placeholder="暂无授权组织"
      >
        <el-option v-for="item in orgOptions" :key="item.value" :label="item.label" :value="item.value" />
      </el-select>
      <el-input v-model="query.employee" clearable placeholder="员工姓名" />
      <el-select v-model="query.format" placeholder="格式">
        <el-option label="PDF" value="pdf" />
        <el-option label="Word" value="word" />
        <el-option label="Zip" value="zip" />
      </el-select>
    </div>

    <div class="metric-grid compact-grid">
      <div class="metric primary"><span>月计划</span><strong>{{ monthPlanCount }}</strong></div>
      <div class="metric success"><span>日计划</span><strong>{{ dayPlanCount }}</strong></div>
      <div class="metric warning"><span>资料缺项</span><strong>{{ missingCount }}</strong></div>
      <div class="metric danger"><span>申诉处理中</span><strong>{{ appealCount }}</strong></div>
    </div>

    <el-table :data="tableRows" border class="mt16">
      <el-table-column prop="employee" label="员工" width="100" />
      <el-table-column prop="group" label="组织" min-width="140" />
      <el-table-column prop="period" label="周期" width="100" />
      <el-table-column prop="monthPlans" label="月计划" width="90" />
      <el-table-column prop="dayPlans" label="日计划" width="90" />
      <el-table-column prop="results" label="成果" width="90" />
      <el-table-column prop="confirmedRate" label="确认比例" width="110" />
      <el-table-column prop="score" label="参考分" width="100" />
      <el-table-column prop="appeal" label="申诉" width="120" />
      <el-table-column label="资料完整性" width="130">
        <template #default="{ row }">
          <el-tag :type="row.completeness === '完整' ? 'success' : 'warning'">{{ row.completeness }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="210" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" :icon="View" @click="openDetail(row)">明细</el-button>
          <el-button link type="primary" :icon="Download" :loading="exportingId === String(row.ownerId)" @click="exportOne(row)">导出</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-drawer v-model="detailVisible" title="部门台账明细" size="560px">
      <div v-if="activeRow" class="drawer-stack">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="员工">{{ activeRow.employee }}</el-descriptions-item>
          <el-descriptions-item label="周期">{{ activeRow.period }}</el-descriptions-item>
          <el-descriptions-item label="月计划 / 日计划 / 成果">{{ activeRow.monthPlans }} / {{ activeRow.dayPlans }} / {{ activeRow.results }}</el-descriptions-item>
          <el-descriptions-item label="确认比例">{{ activeRow.confirmedRate }}</el-descriptions-item>
          <el-descriptions-item label="资料完整性">{{ activeRow.completeness }}</el-descriptions-item>
          <el-descriptions-item label="水印口径">部门、导出人、导出时间、周期</el-descriptions-item>
        </el-descriptions>
      </div>
    </el-drawer>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Download, Search, View } from '@element-plus/icons-vue'
import { exportDepartmentLedgersApi, listDepartmentLedgersApi } from '@/api/department'
import { currentMonthDateRange, errorMessage, normalizeExportFormat } from '@/api/performance'
import { useAutoQuery } from '@/composables/useAutoQuery'
import { useDepartmentOrgScope } from '@/composables/useDepartmentOrgScope'
import { mapDepartmentLedger, periodTypeCode } from '@/views/performanceAdapters'

type DepartmentLedgerRow = ReturnType<typeof mapDepartmentLedger>

const query = reactive({
  periodType: 'month',
  periodRange: currentMonthDateRange(),
  employee: '',
  format: 'pdf',
})
const { orgOptions, selectedOrgId, orgLoading, loadOrgScope } = useDepartmentOrgScope()
const activeRow = ref<DepartmentLedgerRow | null>(null)
const detailVisible = ref(false)
const tableRows = ref<DepartmentLedgerRow[]>([])
const loading = ref(false)
const exportingId = ref<string | null>(null)
const monthPlanCount = computed(() => tableRows.value.reduce((sum, row) => sum + row.monthPlans, 0))
const dayPlanCount = computed(() => tableRows.value.reduce((sum, row) => sum + row.dayPlans, 0))
const missingCount = computed(() => tableRows.value.filter((row) => row.completeness !== '完整').length)
const appealCount = computed(() => tableRows.value.filter((row) => row.appeal !== '无').length)
const autoQuery = useAutoQuery(
  () => [selectedOrgId.value, query.periodType, query.periodRange?.[0], query.periodRange?.[1], query.employee],
  () => refresh(false),
)

async function refresh(showMessage = true) {
  loading.value = true
  try {
    if (selectedOrgId.value == null) {
      tableRows.value = []
      return
    }
    const data = await listDepartmentLedgersApi({
      orgId: selectedOrgId.value,
      periodType: periodTypeCode(query.periodType),
      periodStart: query.periodRange?.[0],
      periodEnd: query.periodRange?.[1],
      employeeName: query.employee || undefined,
    })
    tableRows.value = data.map(mapDepartmentLedger)
    if (showMessage) ElMessage.success('部门台账已刷新')
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    loading.value = false
  }
}

async function exportLedger() {
  const orgId = selectedOrgId.value
  if (orgId == null) {
    ElMessage.warning('当前账号没有可导出的授权组织')
    return
  }
  await createLedgerExport(String(orgId), '部门台账', false, 'all')
}

async function exportOne(row: DepartmentLedgerRow) {
  await createLedgerExport(String(row.ownerId), row.employee, true, String(row.ownerId))
}

function openDetail(row: DepartmentLedgerRow) {
  activeRow.value = row
  detailVisible.value = true
}

function formatText(format: string) {
  return ({ pdf: 'PDF', word: 'Word', zip: 'Zip' } as Record<string, string>)[format] || format
}

async function createLedgerExport(dimensionId: string, label: string, personal: boolean, loadingKey: string) {
  exportingId.value = loadingKey
  try {
    const task = await exportDepartmentLedgersApi({
      dimensionType: personal ? 'PERSON_LEDGER' : 'DEPARTMENT_LEDGER',
      dimensionId,
      periodType: periodTypeCode(query.periodType),
      periodStart: query.periodRange?.[0],
      periodEnd: query.periodRange?.[1],
      formats: [normalizeExportFormat(query.format)],
      includeEvidence: true,
      watermark: '部门、导出人、导出时间、周期',
    })
    ElMessage.success(`${label} ${formatText(query.format)} 导出任务已创建：${task.id}`)
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
