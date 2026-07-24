<template>
  <section class="page-panel employee-appeals">
    <div class="page-header">
      <div>
        <h1 class="page-title">申诉记录</h1>
        <p class="page-subtitle">发起本人绩效申诉，查看争议进度和裁决资料包入口。</p>
      </div>
      <div class="toolbar">
        <el-button :loading="loading" @click="loadAppeals">刷新</el-button>
        <el-button type="primary" @click="openCreate">发起申诉</el-button>
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

    <el-row :gutter="16">
      <el-col :xs="24" :lg="16">
        <section class="dashboard-section">
      <div class="section-header">
        <div>
          <h2>争议进度</h2>
          <p>仅展示本人申诉记录，资料包用于裁决和复核。</p>
        </div>
      </div>
      <el-table v-loading="loading" :data="rows" border empty-text="暂无申诉记录">
        <el-table-column prop="appealNo" label="申诉编号" width="150" />
        <el-table-column prop="title" label="申诉标题" min-width="180" />
        <el-table-column prop="reason" label="申诉原因" min-width="260" show-overflow-tooltip />
        <el-table-column prop="handleComment" label="处理意见" min-width="240" show-overflow-tooltip>
          <template #default="{ row }">{{ row.handleComment || '待处理' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)">{{ getStatusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="handledAt" label="处理时间" width="170">
          <template #default="{ row }">{{ row.handledAt || '--' }}</template>
        </el-table-column>
        <el-table-column prop="createdAt" label="发起时间" width="170" />
        <el-table-column label="资料包" width="130" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :loading="downloadingId === row.id" @click="downloadPackage(row)">下载资料包</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>
      </el-col>
      <el-col :xs="24" :lg="8">
        <section class="dashboard-section">
          <div class="section-header">
            <div>
              <h2>申诉规则</h2>
              <p>员工申诉必须保留证据链和处理边界。</p>
            </div>
          </div>
          <ul class="rule-list">
            <li>成果确认后 3 个自然日内可提交申诉。</li>
            <li>先由部门负责人处理，利益冲突时升级裁决评审员。</li>
            <li>资料包包含申诉、关联计划、成果、确认意见和证据附件。</li>
          </ul>
        </section>
        <section class="dashboard-section mt16">
          <div class="section-header">
            <div>
              <h2>资料包完整性</h2>
              <p>下载时根据数据库最新记录重新生成。</p>
            </div>
          </div>
          <div class="check-list">
            <span>计划记录完整</span>
            <span>成果版本完整</span>
            <span>最终确认意见</span>
            <span>证据附件（如有）</span>
          </div>
        </section>
      </el-col>
    </el-row>

    <el-dialog v-model="dialogVisible" title="发起申诉" width="640px">
      <el-form label-position="top">
        <el-form-item label="争议对象">
          <el-select v-model="form.relatedResultId" class="full-control" placeholder="请选择成果记录" clearable>
            <el-option v-for="option in appealOptions" :key="option.resultId" :label="option.label" :value="option.resultId" />
          </el-select>
        </el-form-item>
        <el-form-item label="接收人">
          <el-input model-value="部门负责人（利益冲突时由处理人升级）" disabled />
        </el-form-item>
        <el-form-item label="申诉标题">
          <el-input v-model="form.title" maxlength="120" placeholder="请输入申诉标题" />
        </el-form-item>
        <el-form-item label="申诉原因">
          <el-input
            v-model="form.reason"
            type="textarea"
            :rows="5"
            maxlength="1000"
            show-word-limit
            placeholder="请说明争议事项、依据记录和需要复核的原因"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitAppeal">提交申诉</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { onMounted, reactive, ref } from 'vue'
import {
  createEmployeeAppealApi,
  downloadEmployeeAppealPackageApi,
  getEmployeeAppealOptionsApi,
  getEmployeeAppealsApi,
  type EmployeeAppealOption,
  type EmployeeAppealItem,
  type EmployeeAppealStatus,
} from '@/api/employee'
import { saveBlob } from '@/utils/download'

const loading = ref(false)
const errorMessage = ref('')
const rows = ref<EmployeeAppealItem[]>([])
const dialogVisible = ref(false)
const submitting = ref(false)
const downloadingId = ref<number | null>(null)
const appealOptions = ref<EmployeeAppealOption[]>([])
const form = reactive({
  relatedResultId: null as number | null,
  title: '',
  reason: '',
})

const statusMap: Record<EmployeeAppealStatus, { label: string; type: 'info' | 'warning' | 'primary' | 'success' }> = {
  draft: { label: '草稿', type: 'info' },
  submitted: { label: '已提交', type: 'warning' },
  processing: { label: '处理中', type: 'primary' },
  resolved: { label: '已裁决', type: 'success' },
  closed: { label: '已关闭', type: 'info' },
}

function getStatusLabel(status: EmployeeAppealStatus) {
  return statusMap[status]?.label ?? status
}

function getStatusType(status: EmployeeAppealStatus) {
  return statusMap[status]?.type ?? 'info'
}

async function loadAppeals() {
  loading.value = true
  errorMessage.value = ''
  try {
    const [data, options] = await Promise.all([
      getEmployeeAppealsApi(),
      getEmployeeAppealOptionsApi(),
    ])
    rows.value = data.items ?? []
    appealOptions.value = options
  } catch (error) {
    rows.value = []
    errorMessage.value = error instanceof Error ? error.message : '申诉记录加载失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

function openCreate() {
  if (!appealOptions.value.length) {
    ElMessage.info('当前没有处于申诉期且尚未发起申诉的成果')
    return
  }
  form.relatedResultId = appealOptions.value[0]?.resultId ?? null
  form.title = ''
  form.reason = ''
  dialogVisible.value = true
}

async function submitAppeal() {
  if (!form.relatedResultId) {
    ElMessage.warning('请选择争议对象')
    return
  }
  const title = form.title.trim()
  const reason = form.reason.trim()
  if (!title || !reason) {
    ElMessage.warning('请填写申诉标题和申诉原因')
    return
  }
  submitting.value = true
  try {
    const created = await createEmployeeAppealApi({ relatedResultId: form.relatedResultId, title, reason })
    dialogVisible.value = false
    ElMessage.success(`申诉 ${created.appealNo} 已提交`)
    await loadAppeals()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '申诉提交失败，请稍后重试')
  } finally {
    submitting.value = false
  }
}

async function downloadPackage(row: EmployeeAppealItem) {
  downloadingId.value = row.id
  try {
    const blob = await downloadEmployeeAppealPackageApi(row.id)
    saveBlob(blob, `${row.appealNo}.zip`)
    ElMessage.success(`${row.appealNo} 裁决资料包已下载`)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '申诉资料包下载失败')
  } finally {
    downloadingId.value = null
  }
}

onMounted(loadAppeals)
</script>
