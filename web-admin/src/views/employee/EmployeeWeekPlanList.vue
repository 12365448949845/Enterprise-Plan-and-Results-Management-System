<template>
  <section class="page-panel week-plan-list">
    <div class="page-header">
      <div><span class="eyebrow">EMPLOYEE WORKSPACE / WEEKLY PLANS</span><h1 class="page-title">周计划</h1><p class="page-subtitle">先看本周状态，再处理需要修改或等待审批的计划。</p></div>
      <div class="toolbar"><el-button :loading="loading" @click="loadRows">刷新</el-button><el-button type="primary" @click="router.push('/employee/week-plans/new/edit')">新建周计划</el-button></div>
    </div>

    <section class="week-now-card mt16">
      <div class="week-now-card__date"><span>本周</span><strong>{{ currentWeekStart.slice(5) }}</strong><small>至 {{ currentWeekEnd.slice(5) }}</small></div>
      <div class="week-now-card__copy">
        <span>当前自然周</span>
        <h2>{{ currentPlan ? currentPlan.title : '本周尚未创建计划' }}</h2>
        <p>{{ currentPlan ? `${currentPlan.itemCount} 条任务 · ${statusMeta(currentPlan.status).label}` : '从已审批月计划中拆解本周工作，明确交付节奏。' }}</p>
      </div>
      <el-button type="primary" @click="openPrimary(currentPlan)">{{ currentPlan ? primaryLabel(currentPlan) : '开始编制' }}</el-button>
    </section>

    <div class="week-status-strip">
      <div><span>待我处理</span><strong>{{ actionableCount }}</strong><small>草稿或已驳回</small></div>
      <div><span>等待审批</span><strong>{{ pendingCount }}</strong><small>可在审批前撤回</small></div>
      <div><span>筛选结果</span><strong>{{ filteredRows.length }}</strong><small>共 {{ rows.length }} 份周计划</small></div>
    </div>

    <div class="filter-bar week-filter-bar">
      <el-date-picker v-model="query.weekStart" type="date" value-format="YYYY-MM-DD" clearable placeholder="按周一筛选" />
      <el-select v-model="query.status" clearable placeholder="全部状态" style="width: 150px"><el-option v-for="(meta, value) in weekPlanStatusMeta" :key="value" :label="meta.label" :value="value" /></el-select>
      <el-checkbox v-model="onlyActionable">只看待处理</el-checkbox>
      <el-button @click="loadRows">查询</el-button><el-button link @click="resetFilters">重置</el-button>
    </div>

    <el-alert v-if="errorMessage" class="mt16" type="warning" :closable="false" show-icon :title="errorMessage" />

    <section v-loading="loading" class="week-plan-stack">
      <article v-for="row in filteredRows" :key="row.id" class="week-plan-card" :class="`is-${row.status.toLowerCase()}`">
        <div class="week-plan-card__rail"><strong>{{ row.weekStart.slice(5) }}</strong><span>—</span><small>{{ row.weekEnd.slice(5) }}</small></div>
        <div class="week-plan-card__body">
          <div class="week-plan-card__title"><div><el-tag :type="statusMeta(row.status).type">{{ statusMeta(row.status).label }}</el-tag><h3>{{ row.title }}</h3></div><span>{{ row.itemCount }} 条任务</span></div>
          <p v-if="row.approvalComment" class="week-plan-card__feedback">{{ row.approvalComment }}</p>
          <div class="week-plan-card__meta"><span>{{ row.submitAt ? `提交于 ${formatTime(row.submitAt)}` : '尚未提交' }}</span><span>{{ row.departmentName || '未配置部门' }}</span></div>
        </div>
        <div class="week-plan-card__actions">
          <el-button type="primary" @click="openPrimary(row)">{{ primaryLabel(row) }}</el-button>
          <el-dropdown trigger="click" @command="(command: string) => handleCommand(command, row)"><el-button>更多</el-button><template #dropdown><el-dropdown-menu><el-dropdown-item command="view">查看详情</el-dropdown-item><el-dropdown-item v-if="row.status === 'PENDING'" command="withdraw">撤回计划</el-dropdown-item><el-dropdown-item v-if="row.status === 'DRAFT' || row.status === 'REJECTED'" command="delete" divided>删除计划</el-dropdown-item></el-dropdown-menu></template></el-dropdown>
        </div>
      </article>
      <el-empty v-if="!loading && !filteredRows.length" :description="rows.length ? '当前筛选条件下没有计划' : '还没有周计划'"><el-button v-if="rows.length" @click="resetFilters">重置筛选</el-button><el-button v-else type="primary" @click="router.push('/employee/week-plans/new/edit')">新建周计划</el-button></el-empty>
    </section>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import { deleteEmployeeWeekPlanApi, listEmployeeWeekPlansApi, withdrawEmployeeWeekPlanApi, weekPlanStatusMeta, type WeekPlanStatus, type WeekPlanSummary } from '@/api/weekPlan'

const router = useRouter()
const loading = ref(false)
const errorMessage = ref('')
const rows = ref<WeekPlanSummary[]>([])
const query = reactive<{ status: WeekPlanStatus | ''; weekStart: string }>({ status: '', weekStart: '' })
const onlyActionable = ref(false)
const currentWeekStart = currentMonday()
const currentWeekEnd = addDays(currentWeekStart, 6)
const currentPlan = computed(() => rows.value.find((row) => row.weekStart === currentWeekStart))
const pendingCount = computed(() => rows.value.filter((row) => row.status === 'PENDING').length)
const actionableCount = computed(() => rows.value.filter((row) => row.status === 'DRAFT' || row.status === 'REJECTED').length)
const filteredRows = computed(() => rows.value.filter((row) => !onlyActionable.value || row.status === 'DRAFT' || row.status === 'REJECTED'))
function statusMeta(status: WeekPlanStatus) { return weekPlanStatusMeta[status] }
function dateValue(date: Date) { return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}` }
function currentMonday() { const date = new Date(); const day = date.getDay() || 7; date.setDate(date.getDate() - day + 1); return dateValue(date) }
function addDays(value: string, days: number) { const date = new Date(`${value}T00:00:00`); date.setDate(date.getDate() + days); return dateValue(date) }
function formatTime(value: string) { return value.replace('T', ' ').slice(0, 16) }
function primaryLabel(row?: WeekPlanSummary) { if (!row) return '开始编制'; return row.status === 'DRAFT' || row.status === 'REJECTED' ? '继续编辑' : row.status === 'PENDING' ? '查看审批' : '查看计划' }
function openPrimary(row?: WeekPlanSummary) { if (!row) return router.push('/employee/week-plans/new/edit'); return router.push(row.status === 'DRAFT' || row.status === 'REJECTED' ? `/employee/week-plans/${row.id}/edit` : `/employee/week-plans/${row.id}`) }
async function loadRows() { loading.value = true; errorMessage.value = ''; try { rows.value = await listEmployeeWeekPlansApi({ status: query.status, weekStart: query.weekStart || undefined }) } catch (error) { errorMessage.value = error instanceof Error ? error.message : '周计划加载失败' } finally { loading.value = false } }
function resetFilters() { query.status = ''; query.weekStart = ''; onlyActionable.value = false; void loadRows() }
async function withdraw(row: WeekPlanSummary) { try { await ElMessageBox.confirm('撤回后周计划恢复为草稿，可修改后重新提交。确认撤回？', '撤回周计划', { type: 'warning' }); await withdrawEmployeeWeekPlanApi(row.id, row.versionNo); ElMessage.success('周计划已撤回'); await loadRows() } catch (error) { if (error !== 'cancel' && error !== 'close') ElMessage.error(error instanceof Error ? error.message : '撤回失败') } }
async function remove(row: WeekPlanSummary) { try { await ElMessageBox.confirm('删除后不可恢复，确认删除这份周计划？', '删除周计划', { type: 'warning' }); await deleteEmployeeWeekPlanApi(row.id, row.versionNo); ElMessage.success('周计划已删除'); await loadRows() } catch (error) { if (error !== 'cancel' && error !== 'close') ElMessage.error(error instanceof Error ? error.message : '删除失败') } }
function handleCommand(command: string, row: WeekPlanSummary) { if (command === 'view') void router.push(`/employee/week-plans/${row.id}`); if (command === 'withdraw') void withdraw(row); if (command === 'delete') void remove(row) }
onMounted(loadRows)
</script>

<style scoped>
.week-now-card { display: grid; grid-template-columns: 108px minmax(0, 1fr) auto; gap: 20px; align-items: center; padding: 20px; border: 1px solid #bfd4c9; border-radius: 13px; background: linear-gradient(110deg, #eef7f2, #fffdf8 70%); }.week-now-card__date { display: grid; place-items: center; padding: 12px; border-right: 1px solid #cddbd4; }.week-now-card__date span, .week-now-card__copy > span { color: var(--blue); font-size: 10px; font-weight: 800; letter-spacing: .1em; }.week-now-card__date strong { margin-top: 5px; font: 800 25px/1.1 "IBM Plex Mono", "Cascadia Mono", monospace; }.week-now-card__date small { color: var(--muted); }.week-now-card__copy h2 { margin: 4px 0; font-size: 19px; }.week-now-card__copy p { margin: 0; color: var(--muted); font-size: 12px; }
.week-status-strip { display: grid; grid-template-columns: repeat(3, 1fr); margin-top: 14px; border: 1px solid var(--line); border-radius: 10px; background: #fff; }.week-status-strip div { display: grid; gap: 2px; padding: 14px 18px; border-right: 1px solid var(--line); }.week-status-strip div:last-child { border: 0; }.week-status-strip span, .week-status-strip small { color: var(--muted); font-size: 11px; }.week-status-strip strong { color: var(--ink); font-size: 22px; }
.week-filter-bar { margin-top: 14px; }.week-plan-stack { display: grid; gap: 12px; min-height: 180px; margin-top: 14px; }.week-plan-card { display: grid; grid-template-columns: 92px minmax(0, 1fr) auto; gap: 18px; align-items: center; padding: 17px; border: 1px solid var(--line); border-left: 4px solid #98aea5; border-radius: 11px; background: #fff; }.week-plan-card.is-pending { border-left-color: #c28a2e; }.week-plan-card.is-approved { border-left-color: var(--green); }.week-plan-card.is-rejected { border-left-color: #b9573f; }.week-plan-card__rail { display: grid; place-items: center; color: var(--muted); }.week-plan-card__rail strong { color: var(--ink); font: 800 16px "IBM Plex Mono", "Cascadia Mono", monospace; }.week-plan-card__body { min-width: 0; }.week-plan-card__title { display: flex; align-items: center; justify-content: space-between; gap: 12px; }.week-plan-card__title > div { display: flex; align-items: center; gap: 10px; min-width: 0; }.week-plan-card h3 { overflow: hidden; margin: 0; font-size: 15px; text-overflow: ellipsis; white-space: nowrap; }.week-plan-card__title > span, .week-plan-card__meta { color: var(--muted); font-size: 11px; }.week-plan-card__feedback { overflow: hidden; margin: 10px 0 7px; color: #9b4a37; font-size: 12px; text-overflow: ellipsis; white-space: nowrap; }.week-plan-card__meta { display: flex; gap: 18px; }.week-plan-card__actions { display: flex; gap: 8px; }
@media (max-width: 900px) { .week-now-card, .week-plan-card { grid-template-columns: 1fr; }.week-now-card__date { justify-items: start; border-right: 0; border-bottom: 1px solid #cddbd4; }.week-status-strip { grid-template-columns: 1fr; }.week-status-strip div { border-right: 0; border-bottom: 1px solid var(--line); }.week-plan-card__rail { display: flex; justify-content: flex-start; gap: 6px; }.week-plan-card__actions { justify-content: flex-start; } }
</style>
