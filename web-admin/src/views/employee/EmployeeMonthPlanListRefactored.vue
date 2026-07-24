<template>
  <section class="page-panel employee-month-page">
    <div class="page-header">
      <div>
        <span class="eyebrow">EMPLOYEE WORKSPACE / MONTHLY PLANS</span>
        <h1 class="page-title">月计划</h1>
        <p class="page-subtitle">按月份查看本人计划、成果状态与可继续处理的工作。</p>
      </div>
      <div class="toolbar">
        <el-button :loading="loading" @click="loadAll">刷新</el-button>
        <el-button type="primary" @click="goEdit()">新建月计划</el-button>
      </div>
    </div>

    <el-alert v-if="errorMessage" class="dashboard-alert" type="warning" :closable="false" show-icon :title="errorMessage" />

    <section class="employee-month-browser mt16">
      <div class="employee-month-browser__head">
        <div>
          <span class="eyebrow">MONTHLY INDEX</span>
          <h2>月份导航</h2>
          <p>本月最大显示，其他月份保持紧凑，点击即可切换。</p>
        </div>
        <el-date-picker v-model="currentMonth" type="month" value-format="YYYY-MM" placeholder="选择月份" />
      </div>
      <div v-if="monthCards.length" class="employee-month-browser__list">
        <button
          v-for="month in monthCards"
          :key="month.month"
          type="button"
          class="employee-month-card"
          :class="{ 'is-active': month.month === currentMonth, 'is-current': month.isCurrent }"
          @click="selectMonth(month.month)"
          @dblclick="goDetail(month.id)"
        >
          <span class="employee-month-card__label">{{ month.month === currentMonth ? '当前月份' : month.label }}</span>
          <strong>{{ month.month.slice(5) }}<small> / {{ month.month.slice(0, 4) }}</small></strong>
          <span class="employee-month-card__meta">{{ month.title || '暂无月计划记录' }}</span>
          <el-tag size="small" :type="month.statusType">{{ month.statusLabel }}</el-tag>
        </button>
      </div>
      <el-empty v-else description="暂无月计划记录" />
    </section>

    <el-row :gutter="16" class="mt16">
      <el-col :xs="24" :sm="12" :lg="6"><div class="metric primary"><span>本月计划</span><strong>{{ summary.monthPlanCount }}</strong></div></el-col>
      <el-col :xs="24" :sm="12" :lg="6"><div class="metric success"><span>已提交成果</span><strong>{{ summary.submittedResultCount }}</strong></div></el-col>
      <el-col :xs="24" :sm="12" :lg="6"><div class="metric"><span>平均完成率</span><strong>{{ summary.averageCompletionRate }}%</strong></div></el-col>
      <el-col :xs="24" :sm="12" :lg="6"><div class="metric warning"><span>可编辑计划</span><strong>{{ editableCount }}</strong></div></el-col>
    </el-row>

    <section class="dashboard-section mt16">
      <div class="section-header">
        <div>
          <h2>{{ currentMonth }} 月计划</h2>
          <p>草稿或驳回状态可继续编辑，其他状态只读查看。</p>
        </div>
        <el-select v-model="statusFilter" clearable placeholder="计划状态">
          <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
      </div>
      <el-table v-loading="loading" :data="filteredRows" border empty-text="暂无月计划">
        <el-table-column prop="planMonth" label="月份" width="110" />
        <el-table-column prop="title" label="计划标题" min-width="180" />
        <el-table-column label="计划状态" width="120">
          <template #default="{ row }"><el-tag :type="getPlanStatus(row.planStatus).type">{{ getPlanStatus(row.planStatus).label }}</el-tag></template>
        </el-table-column>
        <el-table-column label="成果状态" width="130">
          <template #default="{ row }"><el-tag :type="getResultStatus(row.resultStatus).type">{{ getResultStatus(row.resultStatus).label }}</el-tag></template>
        </el-table-column>
        <el-table-column label="完成率" width="150">
          <template #default="{ row }"><el-progress :percentage="row.completionRate" :stroke-width="8" /></template>
        </el-table-column>
        <el-table-column prop="updatedAt" label="更新时间" width="170" />
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="goDetail(row.id)">查看</el-button>
            <el-button link type="primary" :disabled="!canEdit(row.planStatus)" @click="goEdit(row.id)">编辑</el-button>
            <el-button v-if="row.planStatus === 'submitted'" link type="warning" @click="withdraw(row)">撤回</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import { listMonthPlansApi, type MonthPlan } from '@/api/planning'
import { getEmployeeDashboardApi, withdrawEmployeeMonthPlanApi, type EmployeeDashboardResp, type EmployeeMonthPlan, type EmployeePlanStatus, type EmployeeResultStatus } from '@/api/employee'
import { currentMonth as getCurrentMonth } from '@/api/performance'

const router = useRouter()
const loading = ref(false)
const errorMessage = ref('')
const currentMonth = ref(getCurrentMonth())
const statusFilter = ref<EmployeePlanStatus | ''>('')
const allPlans = ref<MonthPlan[]>([])
const rows = ref<EmployeeMonthPlan[]>([])
const summary = reactive<EmployeeDashboardResp['summary']>({ monthPlanCount: 0, submittedResultCount: 0, averageCompletionRate: 0, openAppealCount: 0, missingRequiredDayPlanCount: 0 })

const planStatusMap = {
  draft: { label: '草稿', type: 'info' }, submitted: { label: '已提交', type: 'warning' }, approved: { label: '已通过', type: 'success' },
  rejected: { label: '已驳回', type: 'danger' }, paused: { label: '已暂停', type: 'warning' }, canceled: { label: '已撤销', type: 'danger' },
  confirmed: { label: '已确认', type: 'success' }, archived: { label: '已归档', type: 'info' },
} as const
const resultStatusMap = {
  not_submitted: { label: '未提交', type: 'info' }, draft: { label: '草稿', type: 'info' }, submitted: { label: '待确认', type: 'warning' },
  confirmed: { label: '已确认', type: 'success' }, rejected: { label: '已驳回', type: 'danger' },
} as const
const statusOptions = Object.entries(planStatusMap).map(([value, item]) => ({ value: value as EmployeePlanStatus, label: item.label }))
const filteredRows = computed(() => rows.value.filter((row) => !statusFilter.value || row.planStatus === statusFilter.value))
const editableCount = computed(() => rows.value.filter((row) => canEdit(row.planStatus)).length)
const monthCards = computed(() => {
  const current = getCurrentMonth()
  return allPlans.value
    .reduce<Array<{ id: number; month: string; title: string; isCurrent: boolean; label: string; statusLabel: string; statusType: 'info' | 'warning' | 'success' | 'danger' }>>((items, plan) => {
      if (items.some((item) => item.month === plan.planMonth)) return items
      const status = planStatusMap[toEmployeeStatus(plan.status)]
      items.push({ id: plan.id, month: plan.planMonth, title: plan.title, isCurrent: plan.planMonth === current, label: plan.planMonth === current ? '当前月份' : '历史月份', statusLabel: status.label, statusType: status.type })
      return items
    }, [])
    .sort((a, b) => b.month.localeCompare(a.month))
})

function toEmployeeStatus(status: string): EmployeePlanStatus {
  return ({ DRAFT: 'draft', PENDING: 'submitted', APPROVED: 'approved', REJECTED: 'rejected', PAUSED: 'paused', CANCELED: 'canceled' } as Record<string, EmployeePlanStatus>)[status] || 'draft'
}
function getPlanStatus(status: EmployeePlanStatus) { return planStatusMap[status] }
function getResultStatus(status: EmployeeResultStatus) { return resultStatusMap[status] }
function canEdit(status: EmployeePlanStatus) { return status === 'draft' || status === 'rejected' }
function selectMonth(month: string) { currentMonth.value = month }
function goDetail(id: number) { router.push(`/employee/month-plans/${id}`) }
function goEdit(id?: number) { router.push(id ? `/employee/month-plans/${id}/edit` : '/employee/month-plans/new/edit') }

async function withdraw(row: EmployeeMonthPlan) {
  try {
    await ElMessageBox.confirm('撤回后月计划恢复为草稿，可修改后重新提交。确认撤回？', '撤回月计划', { type: 'warning' })
    await withdrawEmployeeMonthPlanApi(row.id)
    ElMessage.success('月计划已撤回为草稿')
    await loadAll()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error instanceof Error ? error.message : '撤回失败')
      await loadAll()
    }
  }
}

async function loadAll() {
  loading.value = true
  errorMessage.value = ''
  try {
    const [all, current] = await Promise.all([listMonthPlansApi({ mine: true }), getEmployeeDashboardApi(currentMonth.value)])
    allPlans.value = all ?? []
    rows.value = current.monthPlans ?? []
    Object.assign(summary, current.summary)
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '月计划加载失败，请稍后重试'
    rows.value = []
  } finally {
    loading.value = false
  }
}
watch(currentMonth, loadAll)
onMounted(loadAll)
</script>
