<template>
  <section class="page-panel">
    <div class="page-header">
      <div><div class="eyebrow">DISPUTE WORKSPACE / C01</div><h1 class="page-title">争议案件</h1><p class="page-subtitle">案件只按服务端授权范围返回；查看资料包不会改变案件状态。</p></div>
      <el-button @click="load">刷新</el-button>
    </div>
    <div class="filter-bar">
      <el-date-picker v-model="period" type="month" value-format="YYYY-MM" placeholder="选择周期" />
      <el-select v-model="status" clearable placeholder="案件状态">
        <el-option label="待处理" value="SUBMITTED" /><el-option label="评审中" value="REVIEWING" />
        <el-option label="待补充" value="NEEDS_SUPPLEMENT" /><el-option label="已裁决" value="DECIDED" />
      </el-select>
      <el-input v-model="keyword" clearable placeholder="搜索争议点" @keyup.enter="load" />
      <el-button type="primary" @click="load">查询</el-button>
      <el-button @click="reset">重置</el-button>
    </div>
    <div v-loading="loading" class="table-card">
      <el-table :data="items" empty-text="当前授权范围内没有争议案件" @row-click="open">
        <el-table-column prop="caseNo" label="案件编号" width="200" />
        <el-table-column prop="employeeName" label="申诉人" width="110" />
        <el-table-column label="周期" width="120"><template #default="{ row }">{{ row.periodStart?.slice(0, 7) }}</template></el-table-column>
        <el-table-column prop="disputeSubject" label="争议点" min-width="230" show-overflow-tooltip />
        <el-table-column label="资料完整性" width="120"><template #default="{ row }"><el-tag type="success">{{ row.packageStatus === 'READY' ? '完整' : '待校验' }}</el-tag></template></el-table-column>
        <el-table-column prop="status" label="状态" width="120"><template #default="{ row }"><el-tag :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag></template></el-table-column>
        <el-table-column label="评审进度" width="130"><template #default="{ row }">{{ row.opinionCount }}/{{ row.reviewerCount }} 份意见</template></el-table-column>
        <el-table-column label="操作" width="100"><template #default="{ row }"><el-button link type="primary" @click.stop="open(row)">资料包</el-button></template></el-table-column>
      </el-table>
    </div>
  </section>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getDisputeCasesApi, type DisputeCase } from '@/api/dispute'
import { useAutoQuery } from '@/composables/useAutoQuery'
const router = useRouter()
const loading = ref(false); const items = ref<DisputeCase[]>([]); const period = ref(''); const status = ref(''); const keyword = ref('')
const autoQuery = useAutoQuery(
  () => [period.value, status.value, keyword.value],
  () => load(),
)
async function load() { loading.value = true; try { items.value = await getDisputeCasesApi({ period: period.value || undefined, status: status.value || undefined, keyword: keyword.value || undefined }) } catch (error) { ElMessage.error(error instanceof Error ? error.message : '案件加载失败') } finally { loading.value = false } }
async function reset() { autoQuery.pause(); period.value = ''; status.value = ''; keyword.value = ''; await load(); autoQuery.resume() }
function open(row: DisputeCase) { router.push(`/dispute/cases/${row.id}`) }
function statusLabel(value: string) { return ({ SUBMITTED: '待处理', REVIEWING: '评审中', NEEDS_SUPPLEMENT: '待补充', DECIDED: '已裁决', ARCHIVED: '已归档' } as Record<string, string>)[value] || value }
function statusType(value: string) { return ({ SUBMITTED: 'warning', REVIEWING: '', NEEDS_SUPPLEMENT: 'danger', DECIDED: 'success', ARCHIVED: 'info' } as Record<string, string>)[value] || 'info' }
onMounted(async () => { await load(); autoQuery.resume() })
</script>
