<template>
  <section class="page-panel">
    <div v-if="detail" class="page-header">
      <div><div class="eyebrow">DISPUTE WORKSPACE / C02</div><h1 class="page-title">{{ detail.summary.caseNo }}</h1><p class="page-subtitle">{{ detail.summary.employeeName }} · {{ detail.summary.disputeSubject }} · {{ detail.summary.periodStart?.slice(0, 7) }}</p></div>
      <div class="toolbar"><el-button @click="router.push('/dispute/cases')">返回案件</el-button><el-button @click="load">刷新</el-button></div>
    </div>
    <el-skeleton v-if="loading" :rows="8" animated />
    <el-empty v-else-if="!detail" description="案件不存在或无权访问" />
    <template v-else>
      <div class="status-strip">
        <span class="status-pill">案件状态 <strong>{{ statusLabel(detail.summary.status) }}</strong></span>
        <span class="status-pill">资料包 <strong>{{ detail.summary.packageStatus === 'READY' ? '完整' : '待校验' }}</strong></span>
        <span class="status-pill">评审意见 <strong>{{ detail.opinions.length }}/{{ detail.reviewers.length }}</strong></span>
      </div>
      <el-alert title="裁决结论仅由授权评审主管人工提交，不会自动生效。" type="warning" :closable="false" />
      <div class="dashboard-grid mt16">
        <div class="content-stack">
          <section class="section-card">
            <div class="section-header"><div><h2>资料包目录</h2><p>所有内容只读，按案件快照聚合。</p></div><el-button link type="primary" @click="downloadPackage">下载资料包</el-button></div>
            <div class="entry-grid">
              <div v-for="item in detail.packageItems" :key="item" class="entry-card"><strong>{{ item }}</strong><span>已纳入当前案件资料范围，可在原业务页面追溯。</span><el-tag type="success">已纳入</el-tag></div>
            </div>
          </section>
          <section class="section-card">
            <div class="section-title">申诉与成果摘要</div>
            <el-descriptions :column="2" border>
              <el-descriptions-item label="申诉状态">{{ detail.appealStatus || '未知' }}</el-descriptions-item>
              <el-descriptions-item label="成果状态">{{ detail.resultStatus || '无关联成果' }}</el-descriptions-item>
              <el-descriptions-item label="争议标题" :span="2">{{ detail.summary.appealTitle || detail.summary.disputeSubject }}</el-descriptions-item>
              <el-descriptions-item label="申诉理由" :span="2">{{ detail.appealReason || '未填写' }}</el-descriptions-item>
              <el-descriptions-item label="关联成果" :span="2">{{ detail.resultTitle || '无关联成果' }}</el-descriptions-item>
            </el-descriptions>
          </section>
          <section class="section-card">
            <div class="section-header"><div><h2>评审意见</h2><p>每位有效成员均需提交一份意见后才能进入最终裁决。</p></div></div>
            <el-table :data="detail.opinions" empty-text="暂无评审意见">
              <el-table-column prop="reviewerName" label="评审人" width="120" />
              <el-table-column prop="opinion" label="倾向" width="120"><template #default="{ row }"><el-tag>{{ opinionLabel(row.opinion) }}</el-tag></template></el-table-column>
              <el-table-column prop="comment" label="意见" min-width="260" show-overflow-tooltip />
              <el-table-column prop="submittedAt" label="提交时间" width="175" />
            </el-table>
          </section>
        </div>
        <div class="drawer-stack">
          <section class="section-card">
            <div class="section-header"><div><h2>评审小组</h2><p>{{ detail.reviewers.length }} / 5 人，至少需要 2 人。</p></div><el-button link type="primary" @click="router.push(`/dispute/cases/${id}/review-panel`)">管理</el-button></div>
            <div class="quick-list">
              <div v-for="reviewer in detail.reviewers" :key="reviewer.id" class="todo-item">
                <strong>{{ reviewer.userName }}</strong><span>{{ reviewer.currentUser ? '当前用户' : reviewer.sourceType === 'MANUAL' ? '人工加入' : reviewer.sourceType }}</span><el-tag :type="reviewer.recusalStatus === 'ACTIVE' ? 'success' : 'warning'">{{ reviewer.recusalStatus === 'ACTIVE' ? '可参与' : '待处理回避' }}</el-tag>
              </div>
            </div>
          </section>
          <section class="section-card">
            <div class="section-title">提交个人意见</div>
            <el-form label-position="top" @submit.prevent="saveOpinion">
              <el-form-item label="意见倾向"><el-select v-model="opinionForm.opinion" class="full-control"><el-option label="支持申诉" value="SUPPORT" /><el-option label="驳回申诉" value="REJECT" /><el-option label="退回补充材料" value="SUPPLEMENT" /></el-select></el-form-item>
              <el-form-item label="意见说明"><el-input v-model="opinionForm.comment" type="textarea" :rows="4" maxlength="2000" show-word-limit placeholder="说明证据链、判断依据和风险。" /></el-form-item>
              <el-button type="primary" :loading="saving" @click="saveOpinion">保存评审意见</el-button>
              <el-button v-if="currentReviewer" :disabled="saving" @click="recusalOpen = true">申请回避</el-button>
            </el-form>
          </section>
          <section v-if="detail.canDecide" class="section-card danger-entry">
            <div class="section-title">提交最终裁决</div>
            <el-form label-position="top">
              <el-form-item label="裁决结果"><el-select v-model="decisionForm.decision" class="full-control"><el-option label="支持申诉" value="SUPPORT" /><el-option label="驳回申诉" value="REJECT" /><el-option label="退回补充材料" value="SUPPLEMENT" /></el-select></el-form-item>
              <el-form-item label="裁决理由"><el-input v-model="decisionForm.comment" type="textarea" :rows="4" maxlength="2000" show-word-limit /></el-form-item>
              <el-button type="danger" :loading="deciding" @click="submitDecision">提交人工裁决</el-button>
            </el-form>
          </section>
        </div>
      </div>
    </template>

    <el-dialog v-model="recusalOpen" title="申请回避" width="480px">
      <el-input v-model="recusalReason" type="textarea" :rows="4" placeholder="请说明利益冲突或其他回避原因。" />
      <template #footer><el-button @click="recusalOpen = false">取消</el-button><el-button type="primary" @click="recuse">提交回避</el-button></template>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { downloadDisputePackageApi, getDisputeDetailApi, recuseDisputeApi, saveDisputeOpinionApi, submitDisputeDecisionApi, type DisputeDetail, type DisputeOpinion } from '@/api/dispute'
const route = useRoute(); const router = useRouter(); const id = Number(route.params.id)
const loading = ref(false); const saving = ref(false); const deciding = ref(false); const detail = ref<DisputeDetail>()
const recusalOpen = ref(false); const recusalReason = ref('')
const opinionForm = reactive<{ opinion: DisputeOpinion; comment: string }>({ opinion: 'SUPPORT', comment: '' })
const decisionForm = reactive<{ decision: DisputeOpinion; comment: string }>({ decision: 'SUPPORT', comment: '' })
const currentReviewer = computed(() => detail.value?.reviewers.find((item) => item.currentUser))
async function load() { loading.value = true; try { detail.value = await getDisputeDetailApi(id) } catch (error) { ElMessage.error(error instanceof Error ? error.message : '资料包加载失败') } finally { loading.value = false } }
async function saveOpinion() { if (!opinionForm.comment.trim()) return ElMessage.warning('请填写评审意见'); saving.value = true; try { await saveDisputeOpinionApi(id, opinionForm); ElMessage.success('评审意见已保存'); await load() } catch (error) { ElMessage.error(error instanceof Error ? error.message : '评审意见保存失败') } finally { saving.value = false } }
async function submitDecision() { if (!decisionForm.comment.trim()) return ElMessage.warning('请填写裁决理由'); await ElMessageBox.confirm('提交后案件将进入已裁决状态，是否继续？', '确认人工裁决'); deciding.value = true; try { await submitDisputeDecisionApi(id, decisionForm); ElMessage.success('人工裁决已提交'); await load() } catch (error) { ElMessage.error(error instanceof Error ? error.message : '裁决提交失败') } finally { deciding.value = false } }
async function recuse() { if (!recusalReason.value.trim()) return ElMessage.warning('请填写回避原因'); try { await recuseDisputeApi(id, recusalReason.value); recusalOpen.value = false; recusalReason.value = ''; ElMessage.success('回避申请已提交'); await load() } catch (error) { ElMessage.error(error instanceof Error ? error.message : '回避申请失败') } }
async function downloadPackage() {
  try {
    const blob = await downloadDisputePackageApi(id)
    const url = URL.createObjectURL(blob)
    const anchor = document.createElement('a')
    anchor.href = url
    anchor.download = `${detail.value?.summary.caseNo || `dispute-${id}`}.zip`
    anchor.click()
    URL.revokeObjectURL(url)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '资料包下载失败')
  }
}
function statusLabel(value: string) { return ({ SUBMITTED: '待处理', REVIEWING: '评审中', NEEDS_SUPPLEMENT: '待补充', DECIDED: '已裁决', ARCHIVED: '已归档' } as Record<string, string>)[value] || value }
function opinionLabel(value: string) { return ({ SUPPORT: '支持申诉', REJECT: '驳回申诉', SUPPLEMENT: '退回补充' } as Record<string, string>)[value] || value }
onMounted(load)
</script>
