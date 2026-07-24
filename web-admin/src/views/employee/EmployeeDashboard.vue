<template>
  <section class="page-panel employee-dashboard employee-workbench">
    <div class="workbench-header">
      <div>
        <h1 class="page-title">员工工作台</h1>
        <p class="page-subtitle">按周期处理本人日计划、成果提交、当日状态和月计划进度。</p>
      </div>
    </div>

    <el-alert
      v-if="errorMessage"
      class="dashboard-alert"
      type="warning"
      :closable="false"
      show-icon
      :title="errorMessage"
    />

    <section class="employee-workbench-layout">
      <aside class="dashboard-section employee-workbench-sidebar">
        <section class="workbench-side-card workbench-month-card">
          <div class="workbench-side-card__head">
            <span class="workbench-side-card__eyebrow">当前月份</span>
            <el-button class="workbench-refresh-btn" :loading="loading" @click="loadDashboard">刷新</el-button>
          </div>
          <div class="workbench-month-card__main">
            <div class="workbench-month-card__month-block">
              <strong>{{ monthDisplay.month }}</strong>
              <div class="workbench-month-card__year-group">
                <p>{{ monthDisplay.year }}</p>
                <span>{{ currentGroup || '未配置归属组织' }}</span>
              </div>
            </div>
            <div class="workbench-month-card__switcher">
              <el-button circle :disabled="loading" @click="changeMonth(-1)">
                <el-icon><ArrowUp /></el-icon>
              </el-button>
              <el-button circle :disabled="loading" @click="changeMonth(1)">
                <el-icon><ArrowDown /></el-icon>
              </el-button>
            </div>
          </div>
          <div class="workbench-month-card__meta">
            <el-date-picker v-model="currentMonth" type="month" value-format="YYYY-MM" placeholder="选择月份" />
          </div>
        </section>

        <section class="workbench-side-card">
          <div class="workbench-side-card__head">
            <span class="workbench-side-card__eyebrow">今日待办</span>
            <strong>{{ todoCount }}</strong>
          </div>
          <div class="workbench-todo-stack">
            <button v-for="todo in todoItems" :key="todo.key" type="button" class="workbench-todo-card" @click="todo.action">
              <span>{{ todo.title }}</span>
              <strong>{{ todo.count }}</strong>
              <em>{{ todo.desc }}</em>
            </button>
          </div>
        </section>

        <section class="workbench-side-card">
          <div class="workbench-side-card__head">
            <span class="workbench-side-card__eyebrow">快捷入口</span>
          </div>
          <div class="workbench-entry-stack">
            <button class="workbench-entry-card" type="button" @click="goDayPlanEdit(formatDate(selectedDate))">
              <strong>编制日计划</strong>
              <span>按选中日期处理计划草稿、提交和查看。</span>
            </button>
            <button class="workbench-entry-card" type="button" :disabled="!resultSubmitPlan" @click="resultSubmitPlan && goResultSubmit(resultSubmitPlan.id)">
              <strong>提交成果</strong>
              <span>基于月计划条目补充成果版本和证明材料。</span>
            </button>
            <button class="workbench-entry-card" type="button" @click="router.push('/employee/performance-evidence')">
              <strong>绩效依据</strong>
              <span>查看本人日、周、月绩效台账和引用依据。</span>
            </button>
            <button class="workbench-entry-card danger-entry" type="button" @click="router.push('/employee/appeals')">
              <strong>申诉记录</strong>
              <span>查看本人申诉状态和处理结果。</span>
            </button>
          </div>
        </section>
      </aside>

      <div class="employee-workbench-main">
        <section class="dashboard-section workbench-calendar-stage">
          <div class="section-header">
            <div>
              <h2>日计划日历</h2>
              <p>{{ currentMonth }} 日期视图，选中后直接进入当日处理。</p>
            </div>
            <div class="workbench-calendar-stage__summary">
              <span>月计划 {{ summary.monthPlanCount }}</span>
              <span>已提交成果 {{ summary.submittedResultCount }}</span>
              <span>平均完成率 {{ summary.averageCompletionRate }}%</span>
            </div>
          </div>
          <el-calendar v-model="selectedDate">
            <template #date-cell="{ data }">
              <button
                class="calendar-cell"
                :class="{
                  'has-plan': Boolean(dayPlanMap[data.day]),
                  'is-non-required': workdayMap[data.day] && !workdayMap[data.day].forceReport,
                  'is-missing-required': workdayMap[data.day]?.missingRequired,
                }"
                type="button"
                @click="selectCalendarDate(data.day)"
                @dblclick.stop="openDayPlanDialog(data.day)"
              >
                <span>{{ data.day.split('-').pop() }}</span>
                <small v-if="dayPlanMap[data.day]" :class="['calendar-dot', `status-${dayPlanMap[data.day]}`]">
                  {{ getPlanStatus(dayPlanMap[data.day]).label }}
                </small>
                <small v-else-if="calendarCellLabel(data.day)" :class="['calendar-dot', calendarCellClass(data.day)]">
                  {{ calendarCellLabel(data.day) }}
                </small>
              </button>
            </template>
          </el-calendar>
        </section>

        <section class="dashboard-section workbench-day-detail">
          <div class="section-header">
            <div>
              <h2>当日详情</h2>
              <p>{{ selectedDateText }} / {{ currentGroup || '未配置归属组织' }}</p>
            </div>
            <el-tag :type="selectedDayStatus.type">{{ selectedDayStatus.label }}</el-tag>
          </div>
          <div class="workbench-day-detail__grid">
            <div class="workbench-day-detail__context">
              <span>关联月计划</span>
              <strong>{{ primaryMonthPlan?.title || '暂无关联月计划' }}</strong>
              <p>计划状态：{{ primaryPlanStatus.label }}，成果状态：{{ primaryResultStatus.label }}</p>
            </div>
            <div class="workbench-day-detail__actions">
              <el-button type="primary" @click="goDayPlanEdit(formatDate(selectedDate))">编制日计划</el-button>
              <el-button :disabled="!resultSubmitPlan" @click="resultSubmitPlan && goResultSubmit(resultSubmitPlan.id)">提交成果</el-button>
              <el-button :disabled="!primaryMonthPlan" @click="primaryMonthPlan && goMonthPlanDetail(primaryMonthPlan.id)">查看过程</el-button>
            </div>
          </div>
          <div class="workbench-day-detail__metrics">
            <div class="metric">
              <span>今日计划</span>
              <strong>{{ todayPlanCount }}</strong>
              <em>{{ selectedDateText }}</em>
            </div>
            <div class="metric">
              <span>待办事项</span>
              <strong>{{ todoCount }}</strong>
              <em>草稿、驳回、缺报和待补成果</em>
            </div>
            <div class="metric">
              <span>开放申诉</span>
              <strong>{{ summary.openAppealCount }}</strong>
              <em>本人待跟进申诉记录</em>
            </div>
          </div>
        </section>

        <section class="dashboard-section">
          <div class="section-header">
            <div>
              <h2>月计划与成果</h2>
              <p>{{ currentMonth }} 本人计划与成果状态，作为日计划拆解依据。</p>
            </div>
            <el-button type="primary" @click="goMonthPlanEdit()">新建月计划</el-button>
          </div>
          <el-table v-loading="loading" :data="monthPlans" border empty-text="暂无月计划" class="workbench-table">
            <el-table-column prop="planMonth" label="月份" width="110" />
            <el-table-column prop="title" label="计划标题" min-width="180" />
            <el-table-column label="计划状态" width="110">
              <template #default="{ row }">
                <el-tag :type="getPlanStatus(row.planStatus).type">
                  {{ getPlanStatus(row.planStatus).label }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="成果状态" width="120">
              <template #default="{ row }">
                <el-tag :type="getResultStatus(row.resultStatus).type">
                  {{ getResultStatus(row.resultStatus).label }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="完成率" width="120">
              <template #default="{ row }">
                <el-progress :percentage="row.completionRate" :stroke-width="8" />
              </template>
            </el-table-column>
            <el-table-column prop="updatedAt" label="更新时间" width="170" />
            <el-table-column label="操作" width="210" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="goMonthPlanDetail(row.id)">查看</el-button>
                <el-button link type="primary" :disabled="!canEdit(row.planStatus)" @click="goMonthPlanEdit(row.id)">编辑</el-button>
                <el-button link type="primary" :disabled="!canSubmitResult(row)" @click="goResultSubmit(row.id)">提交成果</el-button>
              </template>
            </el-table-column>
          </el-table>
        </section>
      </div>
    </section>

    <el-dialog
      v-model="dayPlanDialogVisible"
      :title="`${dayPlanDialogDate} 日计划`"
      width="min(1120px, 94vw)"
      destroy-on-close
      append-to-body
      @closed="handleDayPlanDialogClosed"
    >
      <EmployeeDayPlanEditor
        v-if="dayPlanDialogDate"
        :date="dayPlanDialogDate"
        compact
        @changed="handleDayPlanChanged"
        @date-change="handleDayPlanDateChange"
      />
      <template #footer>
        <el-button @click="openFullDayPlanPage">打开完整页面</el-button>
        <el-button type="primary" @click="dayPlanDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowDown, ArrowUp } from '@element-plus/icons-vue'
import EmployeeDayPlanEditor from './components/EmployeeDayPlanEditor.vue'
import {
  getEmployeeDashboardApi,
  type EmployeeDashboardResp,
  type EmployeeMonthPlan,
  type EmployeePlanStatus,
  type EmployeeResultStatus,
} from '@/api/employee'
import { currentMonth as getCurrentMonth } from '@/api/performance'

const router = useRouter()
const loading = ref(false)
const errorMessage = ref('')
const currentMonth = ref(getCurrentMonth())
const currentGroup = ref('')
const selectedDate = ref(new Date())
const dayPlanDialogVisible = ref(false)
const dayPlanDialogDate = ref('')
const monthPlans = ref<EmployeeMonthPlan[]>([])
const dayPlanCalendar = ref<EmployeeDashboardResp['dayPlanCalendar']>([])
const workdayCalendar = ref<EmployeeDashboardResp['workdayCalendar']>([])
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

const pendingPlanCount = computed(() => monthPlans.value.filter((item) => item.planStatus === 'draft' || item.planStatus === 'rejected').length)
const dayPlanMap = computed<Record<string, EmployeePlanStatus>>(() => Object.fromEntries(dayPlanCalendar.value.map((item) => [item.date, item.status])))
const workdayMap = computed<Record<string, EmployeeDashboardResp['workdayCalendar'][number]>>(() => Object.fromEntries(workdayCalendar.value.map((item) => [item.date, item])))
const selectedDateText = computed(() => formatDate(selectedDate.value))
const selectedDayPlanStatus = computed(() => dayPlanMap.value[selectedDateText.value])
const selectedWorkdayRule = computed(() => workdayMap.value[selectedDateText.value])
const selectedDayStatus = computed(() => {
  if (selectedDayPlanStatus.value) return getPlanStatus(selectedDayPlanStatus.value)
  if (selectedWorkdayRule.value?.missingRequired) return { label: '待填报', type: 'danger' as const }
  if (selectedWorkdayRule.value && !selectedWorkdayRule.value.forceReport) return { label: '无需填报', type: 'info' as const }
  return { label: '未编制', type: 'warning' as const }
})
const todayPlanCount = computed(() => selectedDayPlanStatus.value ? 1 : 0)
const pendingResultPlans = computed(() => monthPlans.value.filter((item) =>
  item.planStatus === 'approved'
  && (item.resultStatus === 'not_submitted' || item.resultStatus === 'rejected')))
const resultSubmitPlan = computed(() => pendingResultPlans.value[0])
const todoCount = computed(() => pendingPlanCount.value
  + pendingResultPlans.value.length
  + summary.openAppealCount
  + summary.missingRequiredDayPlanCount)
const primaryMonthPlan = computed(() => monthPlans.value[0])
const primaryPlanStatus = computed(() => primaryMonthPlan.value ? getPlanStatus(primaryMonthPlan.value.planStatus) : { label: '无计划', type: 'info' as const })
const primaryResultStatus = computed(() => primaryMonthPlan.value ? getResultStatus(primaryMonthPlan.value.resultStatus) : { label: '未提交', type: 'info' as const })
const todoItems = computed(() => [
  {
    key: 'day-plan',
    title: '日计划',
    count: summary.missingRequiredDayPlanCount,
    desc: summary.missingRequiredDayPlanCount ? '本月应填未填' : '本月无缺报',
    action: goFirstMissingDay,
  },
  {
    key: 'result',
    title: '成果',
    count: pendingResultPlans.value.length,
    desc: '待提交或待补证据',
    action: () => pendingResultPlans.value[0] && goResultSubmit(pendingResultPlans.value[0].id),
  },
  {
    key: 'appeal',
    title: '申诉',
    count: summary.openAppealCount,
    desc: '确认后 3 个自然日内处理',
    action: () => router.push('/employee/appeals'),
  },
])
const monthDisplay = computed(() => {
  const [year, month] = currentMonth.value.split('-')
  return {
    year,
    month: `${Number(month)}月`,
  }
})

function getPlanStatus(status: EmployeePlanStatus) {
  return planStatusMap[status]
}

function getResultStatus(status: EmployeeResultStatus) {
  return resultStatusMap[status]
}

async function loadDashboard() {
  loading.value = true
  errorMessage.value = ''
  try {
    const data = await getEmployeeDashboardApi(currentMonth.value)
    monthPlans.value = data.monthPlans ?? []
    dayPlanCalendar.value = data.dayPlanCalendar ?? []
    workdayCalendar.value = data.workdayCalendar ?? []
    currentGroup.value = data.orgName ?? ''
    Object.assign(summary, data.summary ?? {
      monthPlanCount: monthPlans.value.length,
      submittedResultCount: monthPlans.value.filter((item) => item.resultStatus === 'submitted' || item.resultStatus === 'confirmed').length,
      averageCompletionRate: 0,
      openAppealCount: 0,
      missingRequiredDayPlanCount: 0,
    })
    if (data.currentMonth) {
      currentMonth.value = data.currentMonth
    }
  } catch (error) {
    monthPlans.value = []
    dayPlanCalendar.value = []
    workdayCalendar.value = []
    Object.assign(summary, {
      monthPlanCount: 0,
      submittedResultCount: 0,
      averageCompletionRate: 0,
      openAppealCount: 0,
      missingRequiredDayPlanCount: 0,
    })
    errorMessage.value = error instanceof Error ? error.message : '员工工作台加载失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

function canEdit(status: EmployeeMonthPlan['planStatus']) {
  return status === 'draft' || status === 'rejected'
}

function canSubmitResult(plan: EmployeeMonthPlan) {
  return plan.planStatus === 'approved'
    && (plan.resultStatus === 'not_submitted' || plan.resultStatus === 'rejected')
}

function goMonthPlanDetail(id: number) {
  router.push(`/employee/month-plans/${id}`)
}

function goMonthPlanEdit(id?: number) {
  router.push(id ? `/employee/month-plans/${id}/edit` : '/employee/month-plans/new/edit')
}

function goDayPlanEdit(date?: string) {
  router.push({ path: '/employee/day-plans/edit', query: date ? { date } : undefined })
}

function goFirstMissingDay() {
  const missing = workdayCalendar.value.find((item) => item.missingRequired)
  goDayPlanEdit(missing?.date || selectedDateText.value)
}

function calendarCellLabel(day: string) {
  const rule = workdayMap.value[day]
  if (!rule) return ''
  if (rule.missingRequired) return '待填报'
  if (!rule.forceReport) return rule.ruleType === 'HOLIDAY' ? '节假日' : rule.ruleType === 'LEAVE' ? '请假' : '非强制'
  if (rule.explicit) return rule.ruleType === 'SPECIAL_SHIFT' ? '特殊排班' : rule.ruleType === 'BUSINESS_TRIP' ? '出差' : '需填报'
  return ''
}

function calendarCellClass(day: string) {
  const rule = workdayMap.value[day]
  return rule?.missingRequired ? 'status-missing' : rule?.forceReport ? 'status-required' : 'status-non-required'
}

function selectCalendarDate(day: string) {
  selectedDate.value = new Date(`${day}T00:00:00`)
}

function openDayPlanDialog(day: string) {
  selectCalendarDate(day)
  dayPlanDialogDate.value = day
  dayPlanDialogVisible.value = true
}

function handleDayPlanDateChange(day: string) {
  dayPlanDialogDate.value = day
  selectCalendarDate(day)
}

async function handleDayPlanChanged() {
  await loadDashboard()
}

async function handleDayPlanDialogClosed() {
  await loadDashboard()
}

function openFullDayPlanPage() {
  const date = dayPlanDialogDate.value
  dayPlanDialogVisible.value = false
  goDayPlanEdit(date)
}

function changeMonth(offset: number) {
  const [year, month] = currentMonth.value.split('-').map(Number)
  const next = new Date(year, month - 1 + offset, 1)
  currentMonth.value = `${next.getFullYear()}-${`${next.getMonth() + 1}`.padStart(2, '0')}`
}

function goResultSubmit(monthPlanId: number) {
  router.push({ path: '/employee/results/submit', query: { monthPlanId } })
}

function formatDate(date: Date) {
  const year = date.getFullYear()
  const month = `${date.getMonth() + 1}`.padStart(2, '0')
  const day = `${date.getDate()}`.padStart(2, '0')
  return `${year}-${month}-${day}`
}

watch(currentMonth, loadDashboard)
watch(currentMonth, (month) => {
  const requestedDay = selectedDate.value.getDate()
  const [year, monthNumber] = month.split('-').map(Number)
  const lastDay = new Date(year, monthNumber, 0).getDate()
  selectedDate.value = new Date(year, monthNumber - 1, Math.min(requestedDay, lastDay))
})
onMounted(loadDashboard)
</script>
