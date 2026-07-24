<template>
  <section class="page-panel employee-result-submit">
    <header class="result-page-header">
      <div>
        <span class="result-eyebrow">RESULT DELIVERY</span>
        <h1 class="page-title">成果提交</h1>
        <p class="page-subtitle">把计划转化为可核验的成果版本，提交前完成证据与完整性检查。</p>
      </div>
      <div class="result-header-actions">
        <div v-if="selectedMonthPlan" class="header-plan-summary">
          <span>{{ selectedMonthPlan.planMonth }}</span>
          <strong>{{ selectedMonthPlan.title }}</strong>
        </div>
        <el-tooltip content="刷新提交选项" placement="bottom">
          <el-button circle :icon="Refresh" :loading="loading" :disabled="submitting" aria-label="刷新提交选项" @click="loadOptions" />
        </el-tooltip>
        <el-button @click="router.push('/employee/results')">返回成果记录</el-button>
      </div>
    </header>

    <div class="result-lifecycle" aria-label="成果生命周期">
      <div class="lifecycle-step is-active"><span>01</span><strong>编辑成果</strong><small>填写交付信息</small></div>
      <div class="lifecycle-step" :class="{ 'is-active': aiReview }"><span>02</span><strong>AI 检查</strong><small>核验证据质量</small></div>
      <div class="lifecycle-step" :class="{ 'is-active': latestScopedVersion?.status === 'submitted' }"><span>03</span><strong>待确认</strong><small>进入确认流程</small></div>
      <div class="lifecycle-step" :class="{ 'is-active': latestScopedVersion?.status === 'confirmed' }"><span>04</span><strong>已确认</strong><small>形成成果依据</small></div>
    </div>

    <el-alert
      v-if="errorMessage"
      class="dashboard-alert"
      type="warning"
      :closable="false"
      show-icon
      :title="errorMessage"
    />
    <el-alert
      v-else-if="submissionBlocked"
      class="dashboard-alert"
      type="info"
      :closable="false"
      show-icon
      title="当前计划事项已有待确认或已确认成果，不能重复提交"
    />
    <el-skeleton v-if="loading" class="result-loading" :rows="10" animated />
    <div v-else class="result-submit-layout">
      <main class="result-editor">
        <section class="result-surface plan-context-surface">
          <div class="result-section-head">
            <div>
              <span class="section-index">01 / 关联计划</span>
              <h2>关联计划</h2>
              <p>选择成果所对应的月计划和具体事项。</p>
            </div>
            <el-tag v-if="selectedMonthPlan" type="success" effect="plain">已关联</el-tag>
          </div>
          <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
          <el-row :gutter="16">
            <el-col :xs="24" :md="12">
              <el-form-item label="月计划" prop="monthPlanId">
                <el-select
                  v-model="form.monthPlanId"
                  filterable
                  placeholder="请选择月计划"
                  class="full-control"
                  :disabled="formDisabled"
                  @change="handleMonthPlanChange"
                >
                  <el-option
                    v-for="plan in options.monthPlanOptions"
                    :key="plan.id"
                    :label="`${plan.planMonth} ${plan.title}`"
                    :value="plan.id"
                  />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :xs="24" :md="12">
              <el-form-item label="事项" prop="monthPlanItemId">
                <el-select
                  v-model="form.monthPlanItemId"
                  clearable
                  filterable
                  placeholder="请选择事项"
                  class="full-control"
                  :disabled="formDisabled || !form.monthPlanId"
                >
                  <el-option
                    v-for="item in filteredItems"
                    :key="item.id"
                    :label="item.taskName"
                    :value="item.id"
                  />
                </el-select>
              </el-form-item>
            </el-col>
          </el-row>
          </el-form>
          <div class="selected-plan-context">
            <div class="context-mark"><el-icon><Document /></el-icon></div>
            <div>
              <span>当前提交范围</span>
              <strong>{{ selectedMonthPlanLabel }}</strong>
              <p>{{ selectedPlanItem?.taskName || '整份月计划，未限定具体事项' }}</p>
            </div>
            <el-tag :type="submissionBlocked ? 'warning' : 'info'" effect="plain">
              {{ submissionBlocked ? '已有有效成果' : '可提交新版本' }}
            </el-tag>
          </div>
        </section>

        <section class="result-surface result-content-surface">
          <div class="result-section-head">
            <div>
              <span class="section-index">02 / 成果内容</span>
              <h2>说明与完成情况</h2>
              <p>说明交付内容、完成边界和可核验的关键证据。</p>
            </div>
          </div>
          <el-form :model="form" :rules="rules" label-position="top">
          <el-form-item label="成果说明" prop="description">
            <el-input
              v-model="form.description"
              type="textarea"
              :rows="7"
              maxlength="1000"
              show-word-limit
              placeholder="请填写成果内容、交付范围、关键证据或需确认事项"
              :disabled="formDisabled"
            />
          </el-form-item>
          <el-form-item label="完成比例" prop="completionRate">
            <div class="completion-control">
              <div class="completion-input">
                <el-input-number v-model="form.completionRate" :min="0" :max="100" :step="5" controls-position="right" :disabled="formDisabled" />
                <span>%</span>
              </div>
              <el-progress :percentage="form.completionRate ?? 0" :stroke-width="10" :show-text="false" />
              <small>按实际交付范围填写，系统校验仅作为提示。</small>
            </div>
          </el-form-item>
          </el-form>
        </section>

        <section class="result-surface result-evidence-surface">
          <div class="result-section-head">
            <div>
              <span class="section-index">03 / 成果证据</span>
              <h2>上传附件</h2>
              <p>提交一个可归档的成果文件，作为确认与后续追踪依据。</p>
            </div>
          </div>
          <el-form :model="form" :rules="rules" label-position="top">
          <el-form-item prop="file">
            <el-upload
              drag
              class="result-upload"
              :auto-upload="false"
              :limit="1"
              :file-list="fileList"
              :before-upload="beforeUpload"
              :on-change="handleFileChange"
              :on-remove="handleFileRemove"
              :on-exceed="handleFileExceed"
              :disabled="formDisabled"
              :accept="uploadAccept"
            >
              <el-icon class="upload-icon"><UploadFilled /></el-icon>
              <div class="el-upload__text">拖拽文件到此处，或点击选择</div>
              <template #tip>
                <div class="el-upload__tip">
                  支持 {{ options.acceptedFileTypes.map((item) => item.toUpperCase()).join('、') }}，单个文件不超过 {{ options.maxFileSizeMb }} MB。
                </div>
              </template>
            </el-upload>
          </el-form-item>
          </el-form>
          <div v-if="selectedFileMeta" class="selected-file">
            <div class="file-type">{{ selectedFileMeta.extension }}</div>
            <div>
              <strong>{{ selectedFileMeta.name }}</strong>
              <span>{{ selectedFileMeta.size }} · 本地校验通过</span>
            </div>
            <el-icon class="file-passed"><Check /></el-icon>
          </div>
        </section>
      </main>

      <aside class="result-submit-aside">
        <section class="submit-status-panel">
          <div class="aside-head">
            <div><span>提交准备度</span><strong>{{ formCompletion }}%</strong></div>
            <el-progress type="circle" :percentage="formCompletion" :width="62" :stroke-width="7" />
          </div>
          <div class="submission-checks">
            <div v-for="item in submissionChecks" :key="item.key" :class="{ 'is-passed': item.passed }">
              <el-icon><Check v-if="item.passed" /><Clock v-else /></el-icon>
              <span>{{ item.label }}</span>
            </div>
          </div>
          <el-alert v-if="submissionBlocked" type="warning" :closable="false" show-icon title="当前计划事项已有待确认或已确认成果，不能重复提交" />
        </section>

        <AiReviewPanel
          class="result-ai-panel"
          :review="aiReview"
          :stale="aiReviewStale"
          title="AI 成果证据检查"
          empty-text="完成必填项后检查证据完整性、验收项覆盖和完成比例合理性。"
        >
          <template #actions>
            <el-button type="primary" plain :loading="aiChecking" :disabled="formDisabled || submissionBlocked" @click="checkResultNow">
              {{ aiReview ? '重新检查' : '检查成果证据' }}
            </el-button>
          </template>
        </AiReviewPanel>

        <section class="submit-status-panel version-panel">
          <div class="aside-title">
            <div><span>版本记录</span><small>{{ filteredVersions.length }} 个版本</small></div>
          </div>
          <el-collapse v-if="filteredVersions.length" class="version-collapse">
            <el-collapse-item name="versions" title="查看历史版本">
              <div class="version-list">
                <div v-for="version in filteredVersions" :key="version.id" class="version-item">
                  <div>
                    <strong>{{ version.versionNo }}</strong>
                    <span>{{ version.submittedAt?.replace('T', ' ').slice(0, 16) || '-' }}</span>
                  </div>
                  <el-tag :type="versionStatusMeta(version.status).type" size="small">{{ versionStatusMeta(version.status).label }}</el-tag>
                  <p>{{ version.leaderSuggestion || version.confirmComment || '等待处理' }}</p>
                </div>
              </div>
            </el-collapse-item>
          </el-collapse>
          <p v-else class="empty-version">当前计划暂无成果版本</p>
        </section>

        <div class="submit-command-bar">
          <span>提交后进入直属领导建议与部门确认流程</span>
          <el-button type="primary" size="large" :loading="submitting" :disabled="formDisabled || submissionBlocked || !options.monthPlanOptions.length" @click="submitResult">
            AI 检查并提交
          </el-button>
        </div>
      </aside>
    </div>
    <AiReviewConfirmDialog
      v-model="aiDialogVisible"
      :review="aiReview"
      :confirming="confirmingSubmit"
      @confirm="confirmSubmitResult"
    />
  </section>
</template>

<script setup lang="ts">
import { Check, Clock, Document, Refresh, UploadFilled } from '@element-plus/icons-vue'
import { ElMessage, type FormRules, type UploadFile, type UploadFiles, type UploadProps, type UploadUserFile } from 'element-plus'
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  getEmployeeResultSubmitOptionsApi,
  submitEmployeeResultApi,
  type EmployeeResultSubmitOptionsResp,
} from '@/api/employee'
import { previewResultAiReviewApi, type AiReview } from '@/api/aiReview'
import AiReviewPanel from '@/components/AiReviewPanel.vue'
import AiReviewConfirmDialog from '@/components/AiReviewConfirmDialog.vue'
import { notifyAiReviewResult } from '@/utils/aiReviewFeedback'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const submitting = ref(false)
const confirmingSubmit = ref(false)
const aiDialogVisible = ref(false)
const aiReview = ref<AiReview | null>(null)
const aiReviewStale = ref(false)
const aiChecking = ref(false)
const errorMessage = ref('')
const fileList = ref<UploadUserFile[]>([])

const options = reactive<EmployeeResultSubmitOptionsResp>({
  monthPlanOptions: [],
  monthPlanItemOptions: [],
  acceptedFileTypes: ['pdf', 'doc', 'docx', 'zip'],
  maxFileSizeMb: 20,
  resultVersions: [],
})

const form = reactive({
  monthPlanId: null as number | null,
  monthPlanItemId: null as number | null,
  description: '',
  completionRate: null as number | null,
  file: null as File | null,
})

const rules: FormRules = {
  monthPlanId: [{ required: true, message: '请选择月计划', trigger: 'change' }],
  completionRate: [{ required: true, message: '请填写完成比例', trigger: 'change' }],
  file: [{ required: true, message: '请上传成果附件', trigger: 'change' }],
}

const filteredItems = computed(() => {
  if (!form.monthPlanId) return []
  return options.monthPlanItemOptions.filter((item) => item.monthPlanId === form.monthPlanId)
})
const selectedMonthPlanLabel = computed(() => {
  const plan = options.monthPlanOptions.find((item) => item.id === form.monthPlanId)
  return plan ? `${plan.planMonth} ${plan.title}` : '尚未选择'
})
const selectedMonthPlan = computed(() => options.monthPlanOptions.find((item) => item.id === form.monthPlanId))
const selectedPlanItem = computed(() => filteredItems.value.find((item) => item.id === form.monthPlanItemId))
const filteredVersions = computed(() => options.resultVersions.filter((item) => item.monthPlanId === form.monthPlanId))
const latestScopedVersion = computed(() => filteredVersions.value.find((item) =>
  (item.monthPlanItemId ?? null) === form.monthPlanItemId))
const submissionBlocked = computed(() =>
  latestScopedVersion.value?.status === 'submitted' || latestScopedVersion.value?.status === 'confirmed')
const formDisabled = computed(() => loading.value || submitting.value || aiChecking.value)
const uploadAccept = computed(() => options.acceptedFileTypes.map((type) => `.${type.toLowerCase()}`).join(','))
const submissionChecks = computed(() => [
  { key: 'plan', label: '已关联月计划', passed: Boolean(form.monthPlanId) },
  { key: 'rate', label: '已填写完成比例', passed: form.completionRate !== null },
  { key: 'file', label: '已上传成果附件', passed: Boolean(form.file) },
])
const formCompletion = computed(() => Math.round(
  submissionChecks.value.filter((item) => item.passed).length / submissionChecks.value.length * 100,
))
const selectedFileMeta = computed(() => form.file ? {
  name: form.file.name,
  extension: getFileExt(form.file.name).toUpperCase(),
  size: fileSizeText(form.file.size),
} : null)

function versionStatusMeta(status: string) {
  return ({
    draft: { label: '草稿', type: 'info' as const },
    submitted: { label: '待确认', type: 'warning' as const },
    confirmed: { label: '已确认', type: 'success' as const },
    rejected: { label: '已退回', type: 'danger' as const },
    not_submitted: { label: '未提交', type: 'info' as const },
  }[status] ?? { label: status, type: 'info' as const })
}

function readInitialMonthPlanId() {
  const rawId = route.query.monthPlanId
  const id = Number(Array.isArray(rawId) ? rawId[0] : rawId)
  return Number.isFinite(id) && id > 0 ? id : null
}

function getFileExt(fileName: string) {
  return fileName.split('.').pop()?.toLowerCase() ?? ''
}

function fileSizeText(size: number) {
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / 1024 / 1024).toFixed(1)} MB`
}

function validateFile(file: File) {
  const allowed = new Set(options.acceptedFileTypes.map((type) => type.toLowerCase()))
  if (!allowed.has(getFileExt(file.name))) {
    ElMessage.warning('仅支持上传 PDF、Word 或 Zip 文件')
    return false
  }
  const maxBytes = options.maxFileSizeMb * 1024 * 1024
  if (file.size > maxBytes) {
    ElMessage.warning(`文件大小不能超过 ${options.maxFileSizeMb} MB`)
    return false
  }
  return true
}

const beforeUpload: UploadProps['beforeUpload'] = (rawFile) => validateFile(rawFile)

function handleFileChange(uploadFile: UploadFile, uploadFiles: UploadFiles) {
  const rawFile = uploadFile.raw
  if (!rawFile || !validateFile(rawFile)) {
    fileList.value = uploadFiles.filter((file) => file.uid !== uploadFile.uid)
    form.file = null
    return
  }
  fileList.value = [uploadFile]
  form.file = rawFile
}

function handleFileRemove() {
  fileList.value = []
  form.file = null
}

function handleFileExceed() {
  ElMessage.warning('一次只能上传 1 个成果附件')
}

function handleMonthPlanChange() {
  if (!filteredItems.value.some((item) => item.id === form.monthPlanItemId)) {
    form.monthPlanItemId = null
  }
}

function validateForm() {
  if (submissionBlocked.value) {
    ElMessage.warning('当前计划事项已有待确认或已确认成果')
    return false
  }
  if (!form.monthPlanId) {
    ElMessage.warning('请选择月计划')
    return false
  }
  if (form.completionRate === null || form.completionRate === undefined) {
    ElMessage.warning('请填写完成比例')
    return false
  }
  if (!form.file) {
    ElMessage.warning('请上传成果附件')
    return false
  }
  return validateFile(form.file)
}

async function loadOptions() {
  loading.value = true
  errorMessage.value = ''
  try {
    const data = await getEmployeeResultSubmitOptionsApi()
    Object.assign(options, data)
    const currentPlanAvailable = options.monthPlanOptions.some((plan) => plan.id === form.monthPlanId)
    if (!currentPlanAvailable) {
      const initialPlanId = readInitialMonthPlanId()
      form.monthPlanId = options.monthPlanOptions.some((plan) => plan.id === initialPlanId)
        ? initialPlanId
        : options.monthPlanOptions[0]?.id ?? null
    }
    handleMonthPlanChange()
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '成果提交选项加载失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

async function submitResult() {
  if (submitting.value || !validateForm()) return
  submitting.value = true
  errorMessage.value = ''
  try {
    if (!aiReview.value || aiReviewStale.value) {
      aiReview.value = await previewResultAiReviewApi(buildResultPayload())
      aiReviewStale.value = false
    }
    aiDialogVisible.value = true
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '成果AI检查失败，请稍后重试'
  } finally {
    submitting.value = false
  }
}

async function checkResultNow() {
  if (aiChecking.value || submitting.value || !validateForm()) return
  aiChecking.value = true
  errorMessage.value = ''
  try {
    aiReview.value = await previewResultAiReviewApi(buildResultPayload())
    aiReviewStale.value = false
    notifyAiReviewResult(aiReview.value, 'AI成果语义检查已完成，逐维度报告已显示在当前页面')
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '成果AI检查失败，请稍后重试'
  } finally {
    aiChecking.value = false
  }
}

function buildResultPayload(reviewId?: number) {
  const payload = new FormData()
  payload.append('monthPlanId', String(form.monthPlanId))
  if (form.monthPlanItemId) payload.append('monthPlanItemId', String(form.monthPlanItemId))
  payload.append('completionRate', String(form.completionRate))
  payload.append('description', form.description.trim())
  payload.append('file', form.file as File)
  if (reviewId) payload.append('aiReviewId', String(reviewId))
  return payload
}

async function confirmSubmitResult() {
  if (!aiReview.value || confirmingSubmit.value) return
  confirmingSubmit.value = true
  errorMessage.value = ''
  try {
    const result = await submitEmployeeResultApi(buildResultPayload(aiReview.value.id))
    aiDialogVisible.value = false
    ElMessage.success(`成果已提交，版本号 ${result.versionNo}`)
    router.push(`/employee/month-plans/${form.monthPlanId}`)
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '成果提交失败，请稍后重试'
  } finally {
    confirmingSubmit.value = false
  }
}

watch(() => [
  form.monthPlanId,
  form.monthPlanItemId,
  form.description,
  form.completionRate,
  form.file?.name,
  form.file?.size,
  form.file?.lastModified,
], () => {
  if (aiReview.value) aiReviewStale.value = true
})

onMounted(loadOptions)
</script>

<style scoped>
.employee-result-submit {
  --result-accent: #2d776c;
  --result-warm: #bd5c36;
}

.result-page-header,
.result-header-actions,
.selected-plan-context,
.aside-head,
.aside-title > div,
.selected-file {
  display: flex;
  align-items: center;
}

.result-page-header {
  justify-content: space-between;
  gap: 24px;
  padding-bottom: 22px;
  border-bottom: 1px solid var(--line);
}

.result-eyebrow,
.section-index {
  color: var(--result-accent);
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0;
}

.result-eyebrow { display: block; margin-bottom: 8px; }
.result-header-actions { gap: 10px; }
.header-plan-summary { display: grid; gap: 2px; max-width: 280px; margin-right: 6px; text-align: right; }
.header-plan-summary span { color: var(--muted); font-size: 13px; }
.header-plan-summary strong { overflow: hidden; font-size: 14px; text-overflow: ellipsis; white-space: nowrap; }

.result-lifecycle {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  margin: 18px 0;
  border: 1px solid var(--line);
  border-radius: 8px;
  overflow: hidden;
  background: #f7f8f4;
}

.lifecycle-step { position: relative; display: grid; grid-template-columns: 34px 1fr; gap: 4px 10px; min-width: 0; min-height: 68px; padding: 14px 16px; border-right: 1px solid var(--line); }
.lifecycle-step:last-child { border-right: 0; }
.lifecycle-step::before { position: absolute; inset: auto 0 0; height: 3px; background: transparent; content: ''; }
.lifecycle-step.is-active { background: #f0f6f3; }
.lifecycle-step.is-active::before { background: var(--result-accent); }
.lifecycle-step > span { grid-row: 1 / 3; color: #87958f; font-family: 'Cascadia Mono', monospace; font-size: 13px; }
.lifecycle-step strong { font-size: 14px; }
.lifecycle-step small { overflow: hidden; color: var(--muted); font-size: 12px; line-height: 1.45; text-overflow: ellipsis; white-space: nowrap; }

.result-loading { margin-top: 24px; }
.result-submit-layout { display: grid; grid-template-columns: minmax(0, 1fr) 340px; gap: 20px; align-items: start; }
.result-editor { display: grid; gap: 16px; min-width: 0; }
.result-surface { min-width: 0; padding: 22px; border: 1px solid var(--line); border-radius: 8px; background: var(--surface); }
.result-section-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; margin-bottom: 18px; }
.result-section-head h2 { margin: 5px 0 0; font-size: 18px; }
.result-section-head p { margin: 6px 0 0; color: var(--muted); font-size: 14px; line-height: 1.6; }

.selected-plan-context { gap: 13px; min-height: 72px; padding: 13px 15px; border-left: 3px solid var(--result-accent); background: #f3f7f4; }
.context-mark { display: grid; flex: 0 0 auto; width: 36px; height: 36px; place-items: center; border-radius: 7px; color: var(--result-accent); background: #dfece7; }
.selected-plan-context > div:nth-child(2) { display: grid; flex: 1; min-width: 0; gap: 3px; }
.selected-plan-context span, .selected-plan-context p { color: var(--muted); font-size: 13px; line-height: 1.5; }
.selected-plan-context strong { overflow: hidden; font-size: 15px; text-overflow: ellipsis; white-space: nowrap; }
.selected-plan-context p { margin: 0; }

.completion-control { display: grid; grid-template-columns: 130px minmax(180px, 1fr); gap: 10px 16px; align-items: center; width: 100%; }
.completion-input { display: flex; align-items: center; gap: 8px; }
.completion-input span { color: var(--muted); }
.completion-control small { grid-column: 1 / -1; color: var(--muted); font-size: 13px; line-height: 1.5; }
.result-upload { width: 100%; }
.upload-icon { color: var(--result-accent); font-size: 34px; }

.selected-file { gap: 12px; margin-top: 12px; padding: 12px; border: 1px solid #c9ded5; border-radius: 7px; background: #f1f8f5; }
.file-type { display: grid; width: 48px; height: 48px; place-items: center; border-radius: 6px; color: white; background: var(--result-accent); font-size: 12px; font-weight: 800; }
.selected-file > div:nth-child(2) { display: grid; flex: 1; min-width: 0; gap: 4px; }
.selected-file strong { overflow: hidden; font-size: 14px; text-overflow: ellipsis; white-space: nowrap; }
.selected-file span { color: var(--muted); font-size: 12px; }
.file-passed { color: var(--green); font-size: 20px; }

.result-submit-aside { position: sticky; top: 20px; display: grid; gap: 14px; min-width: 0; }
.submit-status-panel, .submit-command-bar { padding: 18px; border: 1px solid var(--line); border-radius: 8px; background: #f8f9f5; }
.aside-head { justify-content: space-between; gap: 14px; }
.aside-head > div { display: grid; gap: 5px; }
.aside-head span { color: var(--muted); font-size: 13px; }
.aside-head strong { font-size: 28px; font-variant-numeric: tabular-nums; }
.submission-checks { display: grid; gap: 9px; margin-top: 15px; padding-top: 14px; border-top: 1px solid var(--line); }
.submission-checks > div { display: flex; align-items: center; gap: 9px; min-height: 24px; color: var(--muted); font-size: 14px; }
.submission-checks > div.is-passed { color: var(--result-accent); }
.submission-checks .el-icon { font-size: 15px; }
.submit-status-panel .el-alert { margin-top: 14px; }
.result-ai-panel { margin: 0; border-radius: 8px; box-shadow: none; }
.aside-title > div { justify-content: space-between; width: 100%; }
.aside-title span { font-size: 15px; font-weight: 750; }
.aside-title small, .empty-version { color: var(--muted); font-size: 12px; }
.version-collapse { margin-top: 8px; }
.version-item { display: grid; grid-template-columns: minmax(0, 1fr) auto; gap: 5px 8px; padding: 10px 0; border-bottom: 1px solid var(--line); }
.version-item > div { display: grid; gap: 3px; }
.version-item strong { font-size: 14px; }
.version-item span, .version-item p { color: var(--muted); font-size: 12px; }
.version-item p { grid-column: 1 / -1; margin: 0; line-height: 1.6; }
.empty-version { margin: 12px 0 0; }
.submit-command-bar { display: grid; gap: 12px; color: white; border-color: var(--navy-900); background: var(--navy-900); }
.submit-command-bar span { color: #c8d9d3; font-size: 13px; line-height: 1.65; }
.submit-command-bar .el-button { width: 100%; margin: 0; }

.full-control {
  width: 100%;
}

@media (max-width: 1280px) {
  .result-submit-layout { grid-template-columns: minmax(0, 1fr) 300px; }
  .header-plan-summary { display: none; }
}
</style>
