<template>
  <section class="page-panel">
    <div class="page-header">
      <div>
        <h1 class="page-title">月计划</h1>
        <p class="page-subtitle">{{ currentMonth }} 本人月计划列表，进入详情查看交付物、成果汇总和确认记录</p>
      </div>
      <div class="toolbar">
        <el-date-picker v-model="currentMonth" type="month" value-format="YYYY-MM" placeholder="选择月份" />
        <el-select v-model="statusFilter" clearable placeholder="计划状态">
          <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-button :loading="loading" @click="loadList">刷新</el-button>
        <el-button type="primary" @click="goEdit()">新建月计划</el-button>
      </div>
    </div>

    <el-alert v-if="errorMessage" class="dashboard-alert" type="warning" :closable="false" show-icon :title="errorMessage" />

    <el-row :gutter="16">
      <el-col :xs="24" :sm="12" :lg="6">
        <div class="metric">
          <span>本月计划</span>
          <strong>{{ summary.monthPlanCount }}</strong>
        </div>
      </el-col>
      <el-col :xs="24" :sm="12" :lg="6">
        <div class="metric">
          <span>已提交成果</span>
          <strong>{{ summary.submittedResultCount }}</strong>
        </div>
      </el-col>
      <el-col :xs="24" :sm="12" :lg="6">
        <div class="metric">
          <span>平均完成率</span>
          <strong>{{ summary.averageCompletionRate }}%</strong>
        </div>
      </el-col>
      <el-col :xs="24" :sm="12" :lg="6">
        <div class="metric">
          <span>可编辑计划</span>
          <strong>{{ editableCount }}</strong>
        </div>
      </el-col>
    </el-row>

    <section class="dashboard-section mt16">
      <div class="section-header">
        <div>
          <h2>月计划列表</h2>
          <p>草稿或驳回状态可进入编辑，其余状态只读查看</p>
        </div>
      </div>
      <el-table v-loading="loading" :data="filteredRows" border empty-text="暂无月计划">
        <el-table-column prop="planMonth" label="月份" width="110" />
        <el-table-column prop="title" label="计划标题" min-width="180" />
        <el-table-column label="计划状态" width="120">
          <template #default="{ row }">
            <el-tag :type="getPlanStatus(row.planStatus).type">{{ getPlanStatus(row.planStatus).label }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="成果状态" width="130">
          <template #default="{ row }">
            <el-tag :type="getResultStatus(row.resultStatus).type">{{ getResultStatus(row.resultStatus).label }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="完成率" width="150">
          <template #default="{ row }">
            <el-progress :percentage="row.completionRate" :stroke-width="8" />
          </template>
        </el-table-column>
        <el-table-column prop="updatedAt" label="更新时间" width="170" />
        <el-table-column label="操作" width="170" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="goDetail(row.id)">查看</el-button>
            <el-button link type="primary" :disabled="!canEdit(row.planStatus)" @click="goEdit(row.id)">编辑</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import {
  getEmployeeDashboardApi,
  type EmployeeDashboardResp,
  type EmployeeMonthPlan,
  type EmployeePlanStatus,
  type EmployeeResultStatus,
} from '@/api/employee.ts'
import { currentMonth as getCurrentMonth } from '@/api/performance'

const router = useRouter()
const loading = ref(false)
const errorMessage = ref('')
const currentMonth = ref(getCurrentMonth())
const statusFilter = ref<EmployeePlanStatus | ''>('')
const rows = ref<EmployeeMonthPlan[]>([])
const summary = reactive<EmployeeDashboardResp['summary']>({
  monthPlanCount: 0,
  submittedResultCount: 0,
  averageCompletionRate: 0,
  openAppealCount: 0,
  missingRequiredDayPlanCount: 0,
})

const planStatusMap = {
  draft: { label: '草稿', type: 'info' },
  submitted: { label: '已提交', type: 'warning' },
  approved: { label: '已通过', type: 'success' },
  rejected: { label: '已驳回', type: 'danger' },
  paused: { label: '已暂停', type: 'warning' },
  canceled: { label: '已撤销', type: 'danger' },
  confirmed: { label: '已确认', type: 'success' },
  archived: { label: '已归档', type: 'info' },
} as const

const resultStatusMap = {
  not_submitted: { label: '未提交', type: 'info' },
  draft: { label: '草稿', type: 'info' },
  submitted: { label: '已提交确认', type: 'warning' },
  confirmed: { label: '已确认', type: 'success' },
  rejected: { label: '已退回', type: 'danger' },
} as const

const statusOptions = Object.entries(planStatusMap).map(([value, item]) => ({
  value: value as EmployeePlanStatus,
  label: item.label,
}))

const filteredRows = computed(() => rows.value.filter((row) => !statusFilter.value || row.planStatus === statusFilter.value))
const editableCount = computed(() => rows.value.filter((row) => canEdit(row.planStatus)).length)

function getPlanStatus(status: EmployeePlanStatus) {
  return planStatusMap[status]
}

function getResultStatus(status: EmployeeResultStatus) {
  return resultStatusMap[status]
}

function canEdit(status: EmployeePlanStatus) {
  return status === 'draft' || status === 'rejected'
}

async function loadList() {
  loading.value = true
  errorMessage.value = ''
  try {
    const data = await getEmployeeDashboardApi(currentMonth.value)
    rows.value = data.monthPlans ?? []
    Object.assign(summary, data.summary ?? {
      monthPlanCount: rows.value.length,
      submittedResultCount: rows.value.filter((item) => item.resultStatus === 'submitted' || item.resultStatus === 'confirmed').length,
      averageCompletionRate: 0,
    })
    if (data.currentMonth) {
      currentMonth.value = data.currentMonth
    }
  } catch (error) {
    rows.value = []
    Object.assign(summary, {
      monthPlanCount: 0,
      submittedResultCount: 0,
      averageCompletionRate: 0,
    })
    errorMessage.value = error instanceof Error ? error.message : '月计划列表加载失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

function goDetail(id: number) {
  router.push(`/employee/month-plans/${id}`)
}

function goEdit(id?: number) {
  router.push(id ? `/employee/month-plans/${id}/edit` : '/employee/month-plans/new/edit')
}

watch(currentMonth, loadList)
onMounted(loadList)
</script>
