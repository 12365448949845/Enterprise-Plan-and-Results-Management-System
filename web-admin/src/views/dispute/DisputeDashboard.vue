<template>
  <section class="page-panel">
    <div class="page-header">
      <div>
        <div class="eyebrow">DISPUTE WORKSPACE / C00</div>
        <h1 class="page-title">裁决工作台</h1>
        <p class="page-subtitle">只处理授权争议案件。资料包、评审意见和最终结论均保留完整审计链。</p>
      </div>
      <div class="toolbar">
        <el-button type="primary" @click="router.push('/dispute/cases')">进入争议案件</el-button>
      </div>
    </div>

    <div v-loading="loading" class="metric-grid">
      <div v-for="metric in data?.metrics || []" :key="metric.code" class="metric" :class="metric.tone">
        <span>{{ metric.label }}</span>
        <strong>{{ metric.value }}</strong>
        <em>授权范围内</em>
      </div>
    </div>

    <section class="section-card mt16">
      <div class="section-header">
        <div><h2>最近案件</h2><p>优先处理待补充和临近截止的案件。</p></div>
        <el-button link type="primary" @click="router.push('/dispute/cases')">查看全部</el-button>
      </div>
      <el-table :data="data?.recentCases || []" empty-text="当前授权范围内没有案件" @row-click="openCase">
        <el-table-column prop="caseNo" label="案件编号" width="190" />
        <el-table-column prop="employeeName" label="申诉人" width="110" />
        <el-table-column prop="disputeSubject" label="争议点" min-width="240" show-overflow-tooltip />
        <el-table-column prop="status" label="状态" width="130">
          <template #default="{ row }"><el-tag :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag></template>
        </el-table-column>
        <el-table-column label="评审进度" width="130">
          <template #default="{ row }">{{ row.opinionCount }}/{{ row.reviewerCount }} 份意见</template>
        </el-table-column>
        <el-table-column label="操作" width="100">
          <template #default="{ row }"><el-button link type="primary" @click.stop="openCase(row)">查看</el-button></template>
        </el-table-column>
      </el-table>
    </section>
  </section>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getDisputeDashboardApi, type DisputeCase, type DisputeDashboard } from '@/api/dispute'

const router = useRouter()
const loading = ref(false)
const data = ref<DisputeDashboard>()
onMounted(async () => {
  loading.value = true
  try { data.value = await getDisputeDashboardApi() } catch (error) { ElMessage.error(error instanceof Error ? error.message : '裁决工作台加载失败') } finally { loading.value = false }
})
function openCase(row: DisputeCase) { router.push(`/dispute/cases/${row.id}`) }
function statusLabel(status: string) { return ({ SUBMITTED: '待处理', REVIEWING: '评审中', NEEDS_SUPPLEMENT: '待补充', DECIDED: '已裁决', ARCHIVED: '已归档' } as Record<string, string>)[status] || status }
function statusType(status: string) { return ({ SUBMITTED: 'warning', REVIEWING: '', NEEDS_SUPPLEMENT: 'danger', DECIDED: 'success', ARCHIVED: 'info' } as Record<string, string>)[status] || 'info' }
</script>
