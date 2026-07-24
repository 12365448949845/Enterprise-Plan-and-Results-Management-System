<template>
  <section class="page-panel day-plan-list">
    <div class="page-header">
      <div><span class="eyebrow">EMPLOYEE WORKSPACE / DAILY PLANS</span><h1 class="page-title">日计划</h1><p class="page-subtitle">按周查看每日填报状态，优先处理今天和需要修改的计划。</p></div>
      <div class="toolbar"><el-button :loading="loading" @click="loadList">刷新</el-button><el-button type="primary" @click="openDate(today)">{{ todayActionLabel }}</el-button></div>
    </div>

    <section class="day-week-browser mt16">
      <div class="day-week-browser__head"><div><span>本周日期带</span><strong>{{ activeWeekStart }} — {{ activeWeekEnd }}</strong></div><div class="toolbar"><el-button @click="moveWeek(-7)">上一周</el-button><el-button @click="goCurrentWeek">本周</el-button><el-button @click="moveWeek(7)">下一周</el-button></div></div>
      <div class="day-week-track">
        <button v-for="day in weekDays" :key="day.date" type="button" :class="{ 'is-today': day.date === today, 'has-plan': !!day.plan }" @click="openDate(day.date)">
          <span>{{ day.weekday }}</span><strong>{{ day.date.slice(8) }}</strong><small>{{ day.plan ? planStatusMeta(day.plan.status).label : day.date < today ? '无记录' : '待编制' }}</small>
          <i :class="day.plan ? `is-${day.plan.status.toLowerCase()}` : ''"></i>
        </button>
      </div>
    </section>

    <section class="today-plan-card">
      <div class="today-plan-card__date"><span>TODAY</span><strong>{{ today.slice(5).replace('-', '/') }}</strong><small>{{ weekdayLabel(today) }}</small></div>
      <div><el-tag :type="todayPlan ? planStatusMeta(todayPlan.status).type : 'info'">{{ todayPlan ? planStatusMeta(todayPlan.status).label : '尚未编制' }}</el-tag><h2>{{ todayPlan?.title || '今天的工作计划还没有开始' }}</h2><p>{{ todayPlan?.content || '先写清今天最重要的工作和预期结果，再保存草稿。' }}</p></div>
      <el-button type="primary" @click="openDate(today)">{{ todayActionLabel }}</el-button>
    </section>

    <el-collapse class="day-filter-collapse">
      <el-collapse-item title="筛选历史记录" name="filters">
        <div class="filter-bar"><el-date-picker v-model="query.startDate" type="date" value-format="YYYY-MM-DD" placeholder="开始日期" /><el-date-picker v-model="query.endDate" type="date" value-format="YYYY-MM-DD" placeholder="结束日期" /><el-select v-model="query.status" clearable placeholder="计划状态"><el-option v-for="(meta, value) in planStatuses" :key="value" :label="meta.label" :value="value" /></el-select><el-button @click="loadList">查询</el-button><el-button link @click="resetQuery">重置</el-button></div>
      </el-collapse-item>
    </el-collapse>

    <el-alert v-if="errorMessage" class="dashboard-alert" type="warning" :closable="false" show-icon :title="errorMessage" />

    <section v-loading="loading" class="day-record-stack">
      <article v-for="row in pagedRows" :key="row.id" class="day-record-card" :class="`is-${row.status.toLowerCase()}`">
        <div class="day-record-card__date"><strong>{{ row.planDate.slice(8) }}</strong><span>{{ row.planDate.slice(5, 7) }}月</span><small>{{ weekdayLabel(row.planDate) }}</small></div>
        <div class="day-record-card__body"><div><el-tag :type="planStatusMeta(row.status).type">{{ planStatusMeta(row.status).label }}</el-tag><h3>{{ row.title }}</h3></div><p>{{ row.content }}</p><footer><span>风险：{{ riskLabel(row.riskLevel) }}</span><span v-if="row.approvalComment">领导反馈：{{ row.approvalComment }}</span></footer><details v-if="row.departmentReviewComment"><summary>查看部门补审意见</summary><p>{{ row.departmentReviewComment }}</p></details></div>
        <div class="day-record-card__actions"><el-button type="primary" plain @click="openDate(row.planDate)">{{ row.status === 'DRAFT' || row.status === 'REJECTED' ? '继续编辑' : '查看计划' }}</el-button><el-button v-if="row.status === 'PENDING'" link type="warning" @click="withdraw(row)">撤回</el-button></div>
      </article>
      <el-empty v-if="!loading && !pagedRows.length" description="当前条件下没有日计划记录"><el-button type="primary" @click="openDate(today)">{{ todayActionLabel }}</el-button><el-button v-if="rows.length" @click="resetQuery">重置筛选</el-button></el-empty>
    </section>

    <div v-if="rows.length" class="pagination-row"><span>共 {{ rows.length }} 条记录</span><el-pagination v-model:current-page="currentPage" :page-size="pageSize" layout="prev, pager, next" :total="rows.length" /></div>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import { listDayPlansApi, type DayPlan, type PlanStatus } from '@/api/planning'
import { currentMonthToDateRange } from '@/api/performance'
import { withdrawEmployeeDayPlanApi } from '@/api/employee'

const router = useRouter(); const loading = ref(false); const errorMessage = ref(''); const rows = ref<DayPlan[]>([]); const weekRows = ref<DayPlan[]>([]); const initialDateRange = currentMonthToDateRange(); const query = reactive({ startDate: initialDateRange[0], endDate: initialDateRange[1], status: '' }); const currentPage = ref(1); const pageSize = 10; const today = dateValue(new Date()); const activeWeekStart = ref(mondayOf(today))
const activeWeekEnd = computed(() => addDays(activeWeekStart.value, 6))
const planStatuses: Record<PlanStatus, { label: string; type: 'info' | 'warning' | 'success' | 'danger' }> = { DRAFT: { label: '草稿', type: 'info' }, PENDING: { label: '待审批', type: 'warning' }, APPROVED: { label: '已通过', type: 'success' }, REJECTED: { label: '已驳回', type: 'danger' }, PAUSED: { label: '已暂停', type: 'warning' }, CANCELED: { label: '已撤销', type: 'danger' } }
const pagedRows = computed(() => rows.value.slice((currentPage.value - 1) * pageSize, currentPage.value * pageSize))
const todayPlan = computed(() => rows.value.find((row) => row.planDate === today))
const todayActionLabel = computed(() => !todayPlan.value ? '编制今天计划' : todayPlan.value.status === 'DRAFT' || todayPlan.value.status === 'REJECTED' ? '继续编辑今天' : '查看今天计划')
const weekDays = computed(() => Array.from({ length: 7 }, (_, index) => { const date = addDays(activeWeekStart.value, index); return { date, weekday: weekdayLabel(date).replace('星期', '周'), plan: weekRows.value.find((row) => row.planDate === date) } }))
function dateValue(date: Date) { return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}` }
function addDays(value: string, days: number) { const date = new Date(`${value}T00:00:00`); date.setDate(date.getDate() + days); return dateValue(date) }
function mondayOf(value: string) { const date = new Date(`${value}T00:00:00`); const day = date.getDay() || 7; date.setDate(date.getDate() - day + 1); return dateValue(date) }
function weekdayLabel(value: string) { return ['星期日', '星期一', '星期二', '星期三', '星期四', '星期五', '星期六'][new Date(`${value}T00:00:00`).getDay()] }
function planStatusMeta(status: PlanStatus) { return planStatuses[status] ?? { label: status, type: 'info' as const } }
function riskLabel(level?: string) { return ({ LOW: '低', MEDIUM: '中', HIGH: '高' } as Record<string, string>)[level || ''] || '未标记' }
function openDate(date: string) { void router.push({ path: '/employee/daily-plan', query: { date } }) }
function moveWeek(days: number) { activeWeekStart.value = addDays(activeWeekStart.value, days); void loadWeek() }
function goCurrentWeek() { activeWeekStart.value = mondayOf(today); void loadWeek() }
async function loadWeek() { try { weekRows.value = await listDayPlansApi({ startDate: activeWeekStart.value, endDate: activeWeekEnd.value }) } catch (error) { errorMessage.value = error instanceof Error ? error.message : '本周日计划状态加载失败' } }
async function loadList() { loading.value = true; errorMessage.value = ''; try { rows.value = await listDayPlansApi({ startDate: query.startDate, endDate: query.endDate, status: query.status || undefined }); currentPage.value = 1 } catch (error) { errorMessage.value = error instanceof Error ? error.message : '日计划记录加载失败' } finally { loading.value = false } }
function resetQuery() { const range = currentMonthToDateRange(); query.startDate = range[0]; query.endDate = range[1]; query.status = ''; void loadList() }
async function withdraw(row: DayPlan) { try { await ElMessageBox.confirm('撤回后日计划恢复为草稿，可修改后重新提交。确认撤回？', '撤回日计划', { type: 'warning' }); await withdrawEmployeeDayPlanApi(row.id); ElMessage.success('日计划已撤回'); await loadList() } catch (error) { if (error !== 'cancel' && error !== 'close') ElMessage.error(error instanceof Error ? error.message : '撤回失败') } }
onMounted(() => { void Promise.all([loadList(), loadWeek()]) })
</script>

<style scoped>
.day-week-browser { padding: 18px; border: 1px solid var(--line); border-radius: 12px; background: #fffdf8; }.day-week-browser__head { display: flex; align-items: center; justify-content: space-between; gap: 16px; margin-bottom: 13px; }.day-week-browser__head > div:first-child { display: grid; gap: 4px; }.day-week-browser__head span { color: var(--blue); font-size: 10px; font-weight: 800; letter-spacing: .08em; }.day-week-browser__head strong { font-size: 16px; }.day-week-track { display: grid; grid-template-columns: repeat(7, 1fr); gap: 7px; }.day-week-track button { position: relative; display: grid; gap: 4px; min-height: 86px; place-items: center; padding: 10px; border: 1px solid var(--line); border-radius: 9px; color: var(--muted); background: #fff; cursor: pointer; }.day-week-track button:hover { border-color: #9fbfb0; transform: translateY(-1px); }.day-week-track button.is-today { border-color: var(--blue); background: #eef7f2; box-shadow: 0 6px 18px rgb(45 119 108 / 10%); }.day-week-track span, .day-week-track small { font-size: 10px; }.day-week-track strong { color: var(--ink); font: 800 23px/1 "IBM Plex Mono", monospace; }.day-week-track i { width: 6px; height: 6px; border-radius: 50%; background: #cdd5d1; }.day-week-track i.is-pending { background: #c28a2e; }.day-week-track i.is-approved { background: var(--green); }.day-week-track i.is-rejected { background: #b9573f; }
.today-plan-card { display: grid; grid-template-columns: 105px minmax(0, 1fr) auto; gap: 20px; align-items: center; margin-top: 14px; padding: 18px; border: 1px solid #bfd4c9; border-radius: 12px; background: linear-gradient(110deg, #eef7f2, #fffdf8 70%); }.today-plan-card__date { display: grid; place-items: center; padding-right: 18px; border-right: 1px solid #cddbd4; }.today-plan-card__date span { color: var(--blue); font-size: 9px; font-weight: 800; letter-spacing: .12em; }.today-plan-card__date strong { font: 800 25px "IBM Plex Mono", monospace; }.today-plan-card__date small { color: var(--muted); }.today-plan-card h2 { margin: 6px 0 4px; font-size: 17px; }.today-plan-card p { overflow: hidden; margin: 0; color: var(--muted); font-size: 12px; text-overflow: ellipsis; white-space: nowrap; }
.day-filter-collapse { margin-top: 14px; border: 1px solid var(--line); border-radius: 10px; padding: 0 14px; }.day-filter-collapse :deep(.el-collapse-item__header) { border: 0; }.day-filter-collapse :deep(.el-collapse-item__wrap) { border: 0; }.day-record-stack { display: grid; gap: 11px; min-height: 180px; margin-top: 14px; }.day-record-card { display: grid; grid-template-columns: 78px minmax(0, 1fr) auto; gap: 17px; padding: 16px; border: 1px solid var(--line); border-left: 4px solid #98aea5; border-radius: 11px; background: #fff; }.day-record-card.is-pending { border-left-color: #c28a2e; }.day-record-card.is-approved { border-left-color: var(--green); }.day-record-card.is-rejected { border-left-color: #b9573f; }.day-record-card__date { display: grid; place-items: center; align-content: center; border-right: 1px solid var(--line); }.day-record-card__date strong { font: 800 27px/1 "IBM Plex Mono", monospace; }.day-record-card__date span, .day-record-card__date small { color: var(--muted); font-size: 10px; }.day-record-card__body { min-width: 0; }.day-record-card__body > div { display: flex; align-items: center; gap: 9px; }.day-record-card h3 { overflow: hidden; margin: 0; font-size: 14px; text-overflow: ellipsis; white-space: nowrap; }.day-record-card__body > p { display: -webkit-box; overflow: hidden; margin: 9px 0; color: var(--ink); line-height: 1.55; -webkit-box-orient: vertical; -webkit-line-clamp: 2; }.day-record-card footer { display: flex; gap: 18px; color: var(--muted); font-size: 11px; }.day-record-card details { margin-top: 9px; color: var(--muted); font-size: 11px; }.day-record-card details p { color: var(--ink); line-height: 1.6; }.day-record-card__actions { display: flex; align-items: center; }
@media (max-width: 900px) { .day-week-track { grid-template-columns: repeat(4, 1fr); }.today-plan-card, .day-record-card { grid-template-columns: 1fr; }.today-plan-card__date, .day-record-card__date { justify-items: start; border: 0; }.day-record-card__actions { justify-content: flex-start; } }
@media (max-width: 600px) { .day-week-track { grid-template-columns: repeat(2, 1fr); }.day-week-browser__head { align-items: flex-start; flex-direction: column; } }
</style>
