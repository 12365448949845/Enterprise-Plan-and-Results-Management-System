<template>
  <section class="page-panel">
    <div class="page-header">
      <div>
        <h1 class="page-title">额外任务审批</h1>
        <p class="page-subtitle">审批月初常规计划之外新增的工作任务；处理结果只影响当前任务。</p>
      </div>
      <div class="toolbar">
        <el-select v-model="status" style="width: 150px" @change="loadRows">
          <el-option label="待审批" value="PENDING" />
          <el-option label="已通过" value="APPROVED" />
          <el-option label="已驳回" value="REJECTED" />
          <el-option label="全部" value="" />
        </el-select>
        <el-button :loading="loading" @click="loadRows">刷新</el-button>
      </div>
    </div>

    <el-alert v-if="errorMessage" type="warning" :closable="false" show-icon :title="errorMessage" />
    <AiReviewPanel
      class="mt16"
      :review="null"
      :display-report="false"
      title="AI审批辅助"
      empty-text="员工提交额外任务前会自动检查与原月计划是否重复，以及新增工作量、期限、交付物和权重是否合理；点击“详情与AI”查看报告。"
    />

    <section class="dashboard-section mt16">
      <div class="section-header">
        <div>
          <h2>审批清单</h2>
          <p>仅展示当前账号作为直属领导的员工任务。</p>
        </div>
        <el-tag type="warning">待审批 {{ pendingCount }}</el-tag>
      </div>
      <el-table v-loading="loading" :data="rows" border empty-text="暂无额外任务审批记录">
        <el-table-column prop="employeeName" label="员工" width="110" />
        <el-table-column prop="orgName" label="组织" min-width="130" />
        <el-table-column prop="planMonth" label="月份" width="100" />
        <el-table-column prop="taskName" label="额外任务" min-width="170" />
        <el-table-column prop="taskContent" label="任务内容" min-width="200" show-overflow-tooltip />
        <el-table-column prop="deliverable" label="交付物" min-width="150" show-overflow-tooltip />
        <el-table-column label="权重" width="85">
          <template #default="{ row }"><strong>{{ row.performanceWeight }}%</strong></template>
        </el-table-column>
        <el-table-column prop="deadline" label="截止日期" width="120" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }"><el-tag :type="statusMeta(row.status).type">{{ statusMeta(row.status).label }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="approvalComment" label="审批意见" min-width="160" show-overflow-tooltip />
        <el-table-column label="操作" width="205" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="viewAi(row)">详情与AI</el-button>
            <template v-if="row.status === 'PENDING'">
              <el-button link type="primary" @click="approve(row)">通过</el-button>
              <el-button link type="danger" @click="reject(row)">驳回</el-button>
            </template>
            <span v-else>-</span>
          </template>
        </el-table-column>
      </el-table>
    </section>
    <el-drawer v-model="aiDrawerVisible" title="额外任务AI检查" size="680px">
      <el-skeleton v-if="aiLoading" :rows="7" animated />
      <div v-else-if="activeRow" class="drawer-stack">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="员工">{{ activeRow.employeeName }}</el-descriptions-item>
          <el-descriptions-item label="额外任务">{{ activeRow.taskName }}</el-descriptions-item>
          <el-descriptions-item label="任务内容">{{ activeRow.taskContent }}</el-descriptions-item>
          <el-descriptions-item label="交付物">{{ activeRow.deliverable }}</el-descriptions-item>
        </el-descriptions>
        <AiReviewPanel :review="aiReview" empty-text="该任务尚未生成AI检查记录。" />
      </div>
    </el-drawer>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  approveExtraMonthPlanItemApi,
  listExtraMonthPlanApprovalsApi,
  rejectExtraMonthPlanItemApi,
  type ExtraMonthPlanApprovalItem,
} from '@/api/leader'
import { getLatestAiReviewApi, type AiReview } from '@/api/aiReview'
import AiReviewPanel from '@/components/AiReviewPanel.vue'

const loading = ref(false)
const errorMessage = ref('')
const status = ref('PENDING')
const rows = ref<ExtraMonthPlanApprovalItem[]>([])
const activeRow = ref<ExtraMonthPlanApprovalItem | null>(null)
const aiReview = ref<AiReview | null>(null)
const aiDrawerVisible = ref(false)
const aiLoading = ref(false)
const pendingCount = computed(() => rows.value.filter((row) => row.status === 'PENDING').length)

const statusMap = {
  PENDING: { label: '待审批', type: 'warning' as const },
  APPROVED: { label: '已通过', type: 'success' as const },
  REJECTED: { label: '已驳回', type: 'danger' as const },
}

function statusMeta(value: ExtraMonthPlanApprovalItem['status']) {
  return statusMap[value]
}

async function loadRows() {
  loading.value = true
  errorMessage.value = ''
  try {
    rows.value = await listExtraMonthPlanApprovalsApi(status.value ? { status: status.value } : undefined)
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '额外任务审批列表加载失败'
  } finally {
    loading.value = false
  }
}

async function viewAi(row: ExtraMonthPlanApprovalItem) {
  activeRow.value = row
  aiReview.value = null
  aiDrawerVisible.value = true
  aiLoading.value = true
  try {
    aiReview.value = await getLatestAiReviewApi('EXTRA_TASK', Number(row.id))
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : 'AI检查结果加载失败')
  } finally {
    aiLoading.value = false
  }
}

async function approve(row: ExtraMonthPlanApprovalItem) {
  try {
    const { value } = await ElMessageBox.prompt('可填写审批意见', `通过：${row.taskName}`, {
      inputValue: '同意纳入额外月计划',
      inputValidator: (value) => value.length <= 500 || '审批意见不能超过500个字符',
    })
    await approveExtraMonthPlanItemApi(row.id, { comment: value })
    ElMessage.success('额外任务已审批通过')
    await loadRows()
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
    ElMessage.error(error instanceof Error ? error.message : '审批操作失败')
  }
}

async function reject(row: ExtraMonthPlanApprovalItem) {
  try {
    const { value } = await ElMessageBox.prompt('请输入驳回原因', `驳回：${row.taskName}`, {
      inputValidator: (input) => input.trim().length > 0 && input.length <= 500 || '请输入不超过500个字符的驳回原因',
    })
    await rejectExtraMonthPlanItemApi(row.id, { comment: value.trim() })
    ElMessage.success('额外任务已驳回')
    await loadRows()
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
    ElMessage.error(error instanceof Error ? error.message : '驳回操作失败')
  }
}

onMounted(loadRows)
</script>
