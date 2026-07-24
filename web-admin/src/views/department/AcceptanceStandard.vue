<template>
  <section v-loading="loading" class="page-panel">
    <div class="page-header">
      <div>
        <h1 class="page-title">验收标准</h1>
        <p class="page-subtitle">维护交付物对应的验收标准、证据要求和版本状态。</p>
      </div>
      <div class="toolbar">
        <el-button :icon="Search" @click="refresh()">查询</el-button>
        <el-button type="primary" :icon="Plus" :disabled="!hasEnabledTemplate" @click="openEdit()">新增标准</el-button>
      </div>
    </div>

    <div class="filter-bar">
      <el-select v-model="query.deliverable" clearable placeholder="交付物类型">
        <el-option v-for="item in templateOptions" :key="item.id" :label="item.label" :value="item.id" />
      </el-select>
      <el-select v-model="query.status" clearable placeholder="状态">
        <el-option label="启用" value="启用" />
        <el-option label="停用" value="停用" />
      </el-select>
      <el-button :icon="Search" @click="refresh()">查询</el-button>
    </div>

    <el-table :data="tableRows" border empty-text="暂无验收标准">
      <el-table-column prop="deliverable" label="交付物类型" min-width="150" />
      <el-table-column prop="standard" label="验收标准" min-width="220" show-overflow-tooltip />
      <el-table-column prop="evidence" label="证据要求" min-width="180" show-overflow-tooltip />
      <el-table-column prop="reviewRequired" label="需评审" width="100" />
      <el-table-column prop="version" label="版本" width="90" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.status === '启用' ? 'success' : 'info'">{{ row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" :icon="Edit" @click="openEdit(row)">编辑</el-button>
          <el-button
            link
            type="warning"
            :icon="SwitchButton"
            :loading="operatingId === row.id"
            :disabled="operatingId !== null"
            @click="toggle(row)"
          >{{ row.status === '启用' ? '停用' : '启用' }}</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog
      v-model="dialogVisible"
      title="验收标准"
      width="620px"
      :close-on-click-modal="!saving"
      :close-on-press-escape="!saving"
    >
      <el-form label-position="top">
        <el-form-item label="交付物类型">
          <el-select v-model="form.templateId" filterable :disabled="editingId !== null">
            <el-option
              v-for="item in templateOptions"
              :key="item.id"
              :label="item.label"
              :value="item.id"
              :disabled="item.disabled"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="验收标准">
          <el-input v-model="form.standard" type="textarea" :rows="3" maxlength="2000" show-word-limit />
        </el-form-item>
        <el-form-item label="证据要求">
          <el-input v-model="form.evidence" maxlength="1000" show-word-limit />
        </el-form-item>
        <el-form-item label="需要评审通过">
          <el-switch v-model="form.reviewRequired" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存新版本</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Edit, Plus, Search, SwitchButton } from '@element-plus/icons-vue'
import {
  createAcceptanceStandardApi,
  listAcceptanceStandardsApi,
  listDeliverableTemplatesApi,
  toggleAcceptanceStandardApi,
  updateAcceptanceStandardApi,
} from '@/api/department'
import { errorMessage } from '@/api/performance'
import { useAutoQuery } from '@/composables/useAutoQuery'
import { mapAcceptanceStandard } from '@/views/performanceAdapters'

type AcceptanceStandard = ReturnType<typeof mapAcceptanceStandard>

const query = reactive({
  deliverable: null as number | null,
  status: '',
})
const dialogVisible = ref(false)
const tableRows = ref<AcceptanceStandard[]>([])
const templateOptions = ref<{ id: number; name: string; label: string; disabled: boolean }[]>([])
const editingId = ref<number | null>(null)
const loading = ref(false)
const saving = ref(false)
const operatingId = ref<number | null>(null)
const hasEnabledTemplate = computed(() => templateOptions.value.some((item) => !item.disabled))
const form = reactive({
  templateId: null as number | null,
  standard: '',
  evidence: '',
  reviewRequired: true,
})
const autoQuery = useAutoQuery(
  () => [query.deliverable, query.status],
  () => refresh(false),
)

async function refresh(showMessage = true) {
  loading.value = true
  try {
    const [standards, templates] = await Promise.all([
      listAcceptanceStandardsApi({
        templateId: query.deliverable || undefined,
        status: query.status === '启用' ? 'ENABLED' : query.status === '停用' ? 'DISABLED' : undefined,
      }),
      listDeliverableTemplatesApi(),
    ])
    tableRows.value = standards.map(mapAcceptanceStandard)
    templateOptions.value = templates.map((item) => ({
      id: item.id,
      name: item.templateName,
      label: `${item.templateName} · ${item.orgName}`,
      disabled: item.status !== 'ENABLED',
    }))
    if (showMessage) ElMessage.success('验收标准已刷新')
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    loading.value = false
  }
}

function openEdit(row?: AcceptanceStandard) {
  const firstEnabledTemplate = templateOptions.value.find((item) => !item.disabled)
  if (!row && !firstEnabledTemplate) {
    ElMessage.warning('请先新增并启用交付物模板')
    return
  }
  editingId.value = row?.id || null
  Object.assign(form, {
    templateId: row?.templateId || firstEnabledTemplate?.id || null,
    standard: row?.standard || '',
    evidence: row?.evidence || '',
    reviewRequired: row?.reviewRequired !== '否',
  })
  dialogVisible.value = true
}

async function toggle(row: AcceptanceStandard) {
  if (operatingId.value !== null) return
  const enabled = row.status !== '启用'
  try {
    await ElMessageBox.confirm(
      `${enabled ? '启用' : '停用'}“${row.deliverable}”的这版验收标准？`,
      '确认操作',
      { type: 'warning', confirmButtonText: '确认', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  operatingId.value = row.id
  try {
    const result = await toggleAcceptanceStandardApi(row.id, enabled)
    row.status = enabled ? '启用' : '停用'
    ElMessage.success(result.message)
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    operatingId.value = null
  }
}

async function save() {
  if (!form.templateId || !form.standard.trim()) {
    ElMessage.warning('请选择交付物并填写验收标准')
    return
  }
  saving.value = true
  try {
    const payload = {
      templateId: form.templateId,
      standardText: form.standard.trim(),
      evidenceRequirement: form.evidence.trim(),
      requireReviewPassed: form.reviewRequired,
    }
    const saved = editingId.value
      ? await updateAcceptanceStandardApi(editingId.value, payload)
      : await createAcceptanceStandardApi(payload)
    const row = mapAcceptanceStandard(saved)
    const index = tableRows.value.findIndex((item) => item.id === row.id)
    if (index >= 0) tableRows.value[index] = row
    else tableRows.value.unshift(row)
    dialogVisible.value = false
    ElMessage.success('验收标准新版本已保存')
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  await refresh(false)
  autoQuery.resume()
})
</script>
