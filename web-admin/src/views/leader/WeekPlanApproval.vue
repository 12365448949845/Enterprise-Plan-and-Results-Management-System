<template>
  <section class="page-panel">
    <div class="page-header"><div><h1 class="page-title">周计划审批</h1><p class="page-subtitle">审批直属下属周计划，并对照父级月计划和同级周计划检查拆解质量。</p></div><el-button :loading="loading" @click="loadRows">刷新</el-button></div>
    <AiReviewPanel class="mt16" :review="null" :display-report="false" title="AI审批辅助" empty-text="员工提交周计划前会自动生成AI检查报告；点击“详情与AI”可核对计划拆解、交付物和日期风险。" />
    <div class="filter-bar mt16"><el-select v-model="status" clearable placeholder="全部状态"><el-option label="待审批" value="PENDING" /><el-option label="已通过" value="APPROVED" /><el-option label="已驳回" value="REJECTED" /></el-select><el-date-picker v-model="weekStart" type="date" value-format="YYYY-MM-DD" clearable placeholder="按周一筛选" /><el-button @click="loadRows">查询</el-button></div>
    <el-table v-loading="loading" class="mt16" :data="rows" border empty-text="暂无周计划审批记录">
      <el-table-column prop="employeeName" label="员工" width="110" /><el-table-column prop="departmentName" label="组织" min-width="140" /><el-table-column prop="weekStart" label="周开始" width="120" /><el-table-column prop="weekEnd" label="周结束" width="120" /><el-table-column prop="itemCount" label="条目" width="70" />
      <el-table-column label="状态" width="100"><template #default="{ row }"><el-tag :type="statusMeta(row.status).type">{{ statusMeta(row.status).label }}</el-tag></template></el-table-column>
      <el-table-column prop="approvalComment" label="审批意见" min-width="160" show-overflow-tooltip /><el-table-column label="操作" width="205" fixed="right"><template #default="{ row }"><el-button link type="primary" @click="open(row)">详情与AI</el-button><el-button v-if="row.status === 'PENDING'" link type="success" @click="approve(row)">通过</el-button><el-button v-if="row.status === 'PENDING'" link type="danger" @click="reject(row)">驳回</el-button></template></el-table-column>
    </el-table>
    <el-drawer v-model="drawer" title="周计划审批详情" size="760px">
      <template v-if="detail"><el-descriptions :column="2" border><el-descriptions-item label="员工">{{ detail.summary.employeeName }}</el-descriptions-item><el-descriptions-item label="自然周">{{ detail.summary.weekStart }} 至 {{ detail.summary.weekEnd }}</el-descriptions-item></el-descriptions>
        <h3 class="mt16">计划条目与父级月计划</h3><el-table :data="detail.items" border><el-table-column label="父级月计划" min-width="210"><template #default="{ row }">{{ row.parent?.planMonth }} · {{ row.parent?.taskName }}<br><small>{{ row.parent?.taskType === 'EXTRA' ? '额外任务' : '常规任务' }} · {{ row.parent?.performanceWeight }}%</small></template></el-table-column><el-table-column prop="content" label="本周工作" min-width="220" /><el-table-column prop="deliverable" label="交付物" min-width="140" /><el-table-column prop="plannedFinishDate" label="完成日期" width="120" /></el-table>
        <h3 class="mt16">同级周计划</h3><el-table :data="detail.siblingPlans" border empty-text="暂无其他同级周计划"><el-table-column prop="weekStart" label="周开始" width="120" /><el-table-column prop="weekEnd" label="周结束" width="120" /><el-table-column prop="itemCount" label="条目" width="70" /><el-table-column label="状态"><template #default="{ row }">{{ statusMeta(row.status).label }}</template></el-table-column></el-table>
        <AiReviewPanel class="mt16" :review="aiReview" compact empty-text="该周计划尚未生成AI检查记录。" />
        <div v-if="detail.summary.status === 'PENDING'" class="drawer-actions"><el-button type="danger" @click="reject(detail.summary)">驳回</el-button><el-button type="primary" @click="approve(detail.summary)">审批通过</el-button></div>
      </template>
    </el-drawer>
  </section>
</template>
<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRoute } from 'vue-router'
import { approveLeaderWeekPlanApi, getLeaderWeekPlanApi, listLeaderWeekPlansApi, rejectLeaderWeekPlanApi, weekPlanStatusMeta, type WeekPlanDetail, type WeekPlanStatus, type WeekPlanSummary } from '@/api/weekPlan'
import { getLatestAiReviewApi, type AiReview } from '@/api/aiReview'
import AiReviewPanel from '@/components/AiReviewPanel.vue'
const route = useRoute(); const loading = ref(false); const rows = ref<WeekPlanSummary[]>([]); const status = ref('PENDING'); const weekStart = ref(''); const drawer = ref(false); const detail = ref<WeekPlanDetail | null>(null)
const aiReview = ref<AiReview | null>(null)
function statusMeta(value: WeekPlanStatus) { return weekPlanStatusMeta[value] }
async function loadRows() { loading.value = true; try { rows.value = await listLeaderWeekPlansApi({ status: status.value as 'PENDING' | 'APPROVED' | 'REJECTED' | '', weekStart: weekStart.value || undefined }) } catch (e) { ElMessage.error(e instanceof Error ? e.message : '审批列表加载失败') } finally { loading.value = false } }
async function open(row: WeekPlanSummary) { try { const [planDetail, review] = await Promise.all([getLeaderWeekPlanApi(row.id), getLatestAiReviewApi('WEEK_PLAN', row.id)]); detail.value = planDetail; aiReview.value = review; drawer.value = true } catch (e) { ElMessage.error(e instanceof Error ? e.message : '详情加载失败') } }
async function approve(row: WeekPlanSummary) { try { const { value } = await ElMessageBox.prompt('可填写审批意见', '通过周计划', { inputValue: '同意，按计划执行', inputValidator: (v) => v.length <= 500 || '审批意见不能超过500个字符' }); await approveLeaderWeekPlanApi(row.id, row.versionNo, value); ElMessage.success('周计划已审批通过'); drawer.value = false; await loadRows() } catch (e) { if (e !== 'cancel' && e !== 'close') ElMessage.error(e instanceof Error ? e.message : '审批失败') } }
async function reject(row: WeekPlanSummary) { try { const { value } = await ElMessageBox.prompt('请输入驳回原因', '驳回周计划', { inputValidator: (v) => !!v.trim() && v.length <= 500 || '请输入不超过500个字符的驳回原因' }); await rejectLeaderWeekPlanApi(row.id, row.versionNo, value.trim()); ElMessage.success('周计划已驳回'); drawer.value = false; await loadRows() } catch (e) { if (e !== 'cancel' && e !== 'close') ElMessage.error(e instanceof Error ? e.message : '驳回失败') } }
onMounted(async () => { await loadRows(); const id = Number(route.query.id || 0); const row = rows.value.find((item) => item.id === id); if (row) await open(row) })
</script>
<style scoped>.drawer-actions { display:flex; justify-content:flex-end; gap:12px; margin-top:20px; }</style>
