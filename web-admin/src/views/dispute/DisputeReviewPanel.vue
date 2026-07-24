<template>
  <section class="page-panel">
    <div class="page-header">
      <div>
        <div class="eyebrow">DISPUTE WORKSPACE / C03</div>
        <h1 class="page-title">评审小组</h1>
        <p class="page-subtitle">评审小组必须保持 2 到 5 名有效成员，回避未处理前不能提交最终裁决。</p>
      </div>
      <div class="toolbar">
        <el-button @click="router.push(`/dispute/cases/${id}`)">返回资料包</el-button>
        <el-button @click="load">刷新</el-button>
      </div>
    </div>

    <el-alert title="成员管理由服务端校验权限、组织范围、重复成员和利益冲突。" type="info" :closable="false" />
    <section class="section-card mt16" v-loading="loading">
      <div class="section-header">
        <div>
          <h2>{{ detail?.summary.caseNo || `案件 #${id}` }}</h2>
          <p>{{ detail?.summary.employeeName }} · {{ detail?.summary.disputeSubject }}</p>
        </div>
        <el-tag :type="panelValid ? 'success' : 'danger'">{{ reviewers.length }}/5 人</el-tag>
      </div>

      <div class="table-toolbar">
        <span>优先展示案件所属组织成员，也可搜索其他已启用员工。</span>
        <el-select v-model="selectedUserId" filterable clearable style="width: 280px" placeholder="选择评审成员">
          <el-option
            v-for="candidate in candidates"
            :key="candidate.userId"
            :label="`${candidate.userName}（${candidate.employeeNo}）`"
            :value="candidate.userId"
          />
        </el-select>
        <el-input v-model="candidateKeyword" clearable style="width: 180px" placeholder="搜索姓名/工号" @keyup.enter="loadCandidates" />
        <el-button @click="loadCandidates">搜索</el-button>
        <el-button type="primary" :loading="saving" @click="addReviewer">添加成员</el-button>
      </div>

      <el-table :data="reviewers" empty-text="当前案件还没有评审成员">
        <el-table-column prop="userName" label="成员" width="160" />
        <el-table-column prop="sourceType" label="来源" width="130">
          <template #default="{ row }">{{ row.sourceType === 'MANUAL' ? '快捷添加' : row.sourceType }}</template>
        </el-table-column>
        <el-table-column label="回避状态" width="150">
          <template #default="{ row }">
            <el-tag :type="row.recusalStatus === 'ACTIVE' ? 'success' : 'warning'">
              {{ row.recusalStatus === 'ACTIVE' ? '可参与' : '待处理回避' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="recusalReason" label="回避原因" min-width="240" show-overflow-tooltip />
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button link type="danger" :disabled="row.recusalStatus !== 'ACTIVE'" @click="removeReviewer(row.id)">移除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-alert v-if="!panelValid" class="mt16" title="评审成员数量必须在 2 到 5 人之间，当前不能提交最终裁决。" type="warning" :closable="false" />
    </section>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  addDisputeReviewerApi,
  getDisputeDetailApi,
  getDisputeReviewerCandidatesApi,
  getDisputeReviewersApi,
  removeDisputeReviewerApi,
  type DisputeDetail,
  type DisputeReviewer,
  type DisputeReviewerCandidate,
} from '@/api/dispute'

const route = useRoute()
const router = useRouter()
const id = Number(route.params.id)
const loading = ref(false)
const saving = ref(false)
const selectedUserId = ref<number>()
const candidateKeyword = ref('')
const detail = ref<DisputeDetail>()
const reviewers = ref<DisputeReviewer[]>([])
const candidates = ref<DisputeReviewerCandidate[]>([])
const panelValid = computed(() => reviewers.value.length >= 2 && reviewers.value.length <= 5)

async function loadCandidates() {
  try {
    candidates.value = await getDisputeReviewerCandidatesApi(id, candidateKeyword.value)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '候选成员加载失败')
  }
}

async function load() {
  loading.value = true
  try {
    const [caseDetail, members] = await Promise.all([getDisputeDetailApi(id), getDisputeReviewersApi(id)])
    detail.value = caseDetail
    reviewers.value = members
    await loadCandidates()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '评审小组加载失败')
  } finally {
    loading.value = false
  }
}

async function addReviewer() {
  if (!selectedUserId.value) return ElMessage.warning('请选择评审成员')
  saving.value = true
  try {
    await addDisputeReviewerApi(id, selectedUserId.value)
    selectedUserId.value = undefined
    ElMessage.success('评审成员已添加')
    await load()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '成员添加失败')
  } finally {
    saving.value = false
  }
}

async function removeReviewer(reviewerId: number) {
  try {
    await ElMessageBox.confirm('移除后该成员将不再参与本案件评审，是否继续？', '确认移除')
    await removeDisputeReviewerApi(id, reviewerId)
    ElMessage.success('评审成员已移除')
    await load()
  } catch (error) {
    if (error !== 'cancel') ElMessage.error(error instanceof Error ? error.message : '成员移除失败')
  }
}

onMounted(load)
</script>
