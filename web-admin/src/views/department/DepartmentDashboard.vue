<template>
  <section v-loading="loading" class="page-panel">
    <div class="page-header">
      <div>
        <h1 class="page-title">部门总览</h1>
        <p class="page-subtitle">查看部门周期汇总、待审批、待确认、逾期和导出任务。</p>
      </div>
      <div class="toolbar">
        <el-button type="primary" :icon="DocumentChecked" @click="go('/department/plan-approval')">月计划查看</el-button>
        <el-button :icon="CircleCheck" @click="go('/department/result-confirm')">成果最终确认</el-button>
        <el-button :icon="Bell" @click="go('/department/todo')">通知待办</el-button>
      </div>
    </div>

    <div class="filter-bar">
      <el-date-picker v-model="query.month" type="month" value-format="YYYY-MM" placeholder="月份" />
      <el-select
        v-model="selectedOrgId"
        :loading="orgLoading"
        :disabled="!orgOptions.length"
        placeholder="暂无授权组织"
      >
        <el-option v-for="item in orgOptions" :key="item.value" :label="item.label" :value="item.value" />
      </el-select>
      <el-select v-model="query.periodType" placeholder="周期">
        <el-option label="月度" value="month" />
        <el-option label="季度" value="quarter" />
        <el-option label="年度" value="year" />
      </el-select>
      <el-button :icon="Search" @click="refresh()">查询</el-button>
    </div>

    <div class="metric-grid">
      <div v-for="item in departmentMetrics" :key="item.label" :class="['metric', item.tone]">
        <span>{{ item.label }}</span>
        <strong>{{ item.value }}</strong>
      </div>
    </div>

    <div class="dashboard-grid mt16">
      <div class="section-card">
        <div class="section-title">部门周期汇总</div>
        <el-table :data="departmentSummaryRows" border>
          <el-table-column prop="group" label="组织" min-width="140" />
          <el-table-column prop="monthPlans" label="月计划" width="100" />
          <el-table-column prop="approvedPlans" label="已批" width="90" />
          <el-table-column prop="pendingPlans" label="待审" width="90" />
          <el-table-column prop="confirmedResults" label="已确认成果" width="120" />
          <el-table-column prop="closureRate" label="闭环率" width="100" />
          <el-table-column label="风险" min-width="140">
            <template #default="{ row }">
              <el-tag :type="row.risk === '正常' ? 'success' : 'warning'">{{ row.risk }}</el-tag>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <div class="section-card">
        <div class="section-title">治理入口</div>
        <div class="quick-list">
          <button type="button" @click="go('/department/template')">交付物模板</button>
          <button type="button" @click="go('/department/standard')">验收标准</button>
          <button type="button" @click="go('/department/score-rule')">参考分规则</button>
          <button type="button" @click="go('/department/department-ledger')">部门台账</button>
          <button type="button" @click="go('/department/export-tasks')">导出任务</button>
        </div>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Bell, CircleCheck, DocumentChecked, Search } from '@element-plus/icons-vue'
import { getDepartmentDashboardApi } from '@/api/department'
import { currentMonth, errorMessage } from '@/api/performance'
import { useAutoQuery } from '@/composables/useAutoQuery'
import { useDepartmentOrgScope } from '@/composables/useDepartmentOrgScope'
import { mapDepartmentSummary, periodTypeCode } from '@/views/performanceAdapters'

const router = useRouter()
const { orgOptions, selectedOrgId, orgLoading, loadOrgScope } = useDepartmentOrgScope()
const query = reactive({
  month: currentMonth(),
  periodType: 'month',
})
const loading = ref(false)
const departmentMetrics = ref<{ code: string; label: string; value: number; tone: string }[]>([])
const departmentSummaryRows = ref<ReturnType<typeof mapDepartmentSummary>[]>([])
const autoQuery = useAutoQuery(
  () => [selectedOrgId.value, query.month, query.periodType],
  () => refresh(false),
)

function go(path: string) {
  router.push(path)
}

async function refresh(showMessage = true) {
  loading.value = true
  try {
    if (selectedOrgId.value == null) {
      departmentMetrics.value = []
      departmentSummaryRows.value = []
      return
    }
    const data = await getDepartmentDashboardApi({
      orgId: selectedOrgId.value,
      periodType: periodTypeCode(query.periodType),
      periodMonth: query.month,
    })
    departmentMetrics.value = data.metrics
    departmentSummaryRows.value = data.summaries.map(mapDepartmentSummary)
    if (showMessage) ElMessage.success('部门总览已刷新')
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    loading.value = false
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
