<template>
  <section v-loading="loading" class="page-panel leader-workbench-panel">
    <div class="page-header">
      <div>
        <h1 class="page-title">领导工作台</h1>
        <p class="page-subtitle">按授权组织范围查看待点评、待建议、逾期和日期状态。</p>
      </div>
      <div class="toolbar">
        <el-button type="primary" :icon="EditPen" @click="go('/leader/daily-review')">日计划点评</el-button>
        <el-button :icon="DocumentChecked" @click="go('/leader/result-suggest')">成果确认建议</el-button>
        <el-button :icon="Tickets" @click="go('/leader/team-ledger')">下属台账</el-button>
        <el-button @click="go('/leader/ai-month-context')">本月计划要求</el-button>
      </div>
    </div>

    <div class="filter-bar">
      <el-date-picker v-model="query.month" type="month" value-format="YYYY-MM" placeholder="月份" @change="clearDateFilter" />
      <el-date-picker v-model="query.date" type="date" value-format="YYYY-MM-DD" clearable placeholder="可选：精确到日" @change="syncMonthFromDate" />
      <el-select v-model="scopeOrgId" :loading="orgLoading" :disabled="!orgOptions.length" placeholder="暂无授权组织">
        <el-option v-for="item in orgOptions" :key="item.value" :label="item.label" :value="item.value" />
      </el-select>
      <el-button :icon="Search" :loading="loading" @click="refresh()">查询</el-button>
    </div>

    <div class="split-layout">
      <aside class="section-card org-card">
        <div class="section-title">组织范围</div>
        <el-tree :data="orgTree" default-expand-all :props="{ label: 'label', children: 'children' }" />
      </aside>

      <div class="content-stack">
        <div class="metric-grid">
          <div v-for="item in leaderMetrics" :key="item.label" :class="['metric', item.tone]">
            <span>{{ item.label }}</span>
            <strong>{{ item.value }}</strong>
          </div>
        </div>

        <div class="section-card leader-date-card">
          <div class="section-title">日期状态</div>
          <el-table class="leader-date-table" :data="leaderDateRows" border>
            <el-table-column prop="date" label="日期" width="136" />
            <el-table-column prop="group" label="组织" min-width="168" />
            <el-table-column prop="pendingReview" label="待点评" width="96" align="center" />
            <el-table-column prop="pendingSuggest" label="待建议" width="96" align="center" />
            <el-table-column prop="overdue" label="逾期" width="88" align="center" />
            <el-table-column label="状态" width="142">
              <template #default="{ row }">
                <el-tag :type="row.overdue ? 'danger' : 'success'">{{ row.status }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="116" align="center">
              <template #default="{ row }">
                <el-button link type="primary" @click="openDate(row.date)">查看当日</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>

        <div class="section-card">
          <div class="section-title">下属处理摘要</div>
          <el-table :data="dailyReviewRows" border>
            <el-table-column prop="employee" label="员工" width="100" />
            <el-table-column prop="date" label="日期" width="120" />
            <el-table-column prop="content" label="计划内容" min-width="220" show-overflow-tooltip />
            <el-table-column prop="missing" label="缺失状态" width="130" />
            <el-table-column label="风险" width="120">
              <template #default="{ row }">
                <el-tag :type="riskTag(row.riskLevel)">{{ row.riskLevel }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="reviewStatus" label="点评状态" width="120" />
          </el-table>
        </div>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { DocumentChecked, EditPen, Search, Tickets } from '@element-plus/icons-vue'
import { getLeaderWorkbenchApi } from '@/api/leader'
import { currentMonth, errorMessage } from '@/api/performance'
import { useAutoQuery } from '@/composables/useAutoQuery'
import { useLeaderOrgScope } from '@/composables/useLeaderOrgScope'
import { mapDailyReview, mapLeaderDateStatus } from '@/views/performanceAdapters'

const router = useRouter()
const { orgTree, orgOptions, scopeOrgId, orgLoading, loadOrgScope } = useLeaderOrgScope()
const query = reactive({
  month: currentMonth(),
  date: '',
})
const loading = ref(false)
const leaderMetrics = ref<{ code: string; label: string; value: number; tone: string }[]>([])
const leaderDateRows = ref<ReturnType<typeof mapLeaderDateStatus>[]>([])
const dailyReviewRows = ref<ReturnType<typeof mapDailyReview>[]>([])
let refreshRequestId = 0
const autoQuery = useAutoQuery(
  () => [scopeOrgId.value, query.month, query.date],
  () => refresh(false),
)

function go(path: string) {
  router.push(path)
}

async function refresh(showMessage = true) {
  const requestId = ++refreshRequestId
  loading.value = true
  try {
    const data = await getLeaderWorkbenchApi({
      scopeOrgId: scopeOrgId.value,
      date: query.date || undefined,
      periodMonth: query.month || undefined,
    })
    if (requestId !== refreshRequestId) return
    leaderMetrics.value = data.metrics
    leaderDateRows.value = data.dateStatuses.map(mapLeaderDateStatus)
    dailyReviewRows.value = data.subordinateSummaries.map(mapDailyReview)
    if (showMessage) ElMessage.success('已按组织范围刷新')
  } catch (error) {
    if (requestId !== refreshRequestId) return
    ElMessage.error(errorMessage(error))
  } finally {
    if (requestId === refreshRequestId) loading.value = false
  }
}

function openDate(date: string) {
  router.push({ path: '/leader/daily-review', query: { date } })
}

function clearDateFilter() {
  query.date = ''
}

function syncMonthFromDate(value: string) {
  if (value) query.month = value.slice(0, 7)
}

function riskTag(level: string) {
  if (level === '高') return 'danger'
  if (level === '中') return 'warning'
  return 'success'
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
