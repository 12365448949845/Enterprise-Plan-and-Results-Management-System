<template>
  <section v-loading="loading" class="page-panel">
    <div class="page-header">
      <div>
        <h1 class="page-title">交付物模板</h1>
        <p class="page-subtitle">维护部门交付物类型、证据类型、启用状态和引用关系。</p>
      </div>
      <div class="toolbar">
        <el-button :icon="Search" @click="refresh()">查询</el-button>
        <el-button type="primary" :icon="Plus" :disabled="!orgOptions.length" @click="openEdit()">新增模板</el-button>
      </div>
    </div>

    <div class="filter-bar">
      <el-select
        v-model="selectedOrgId"
        clearable
        :loading="orgLoading"
        :disabled="!orgOptions.length"
        placeholder="全部授权组织"
      >
        <el-option v-for="item in orgOptions" :key="item.value" :label="item.label" :value="item.value" />
      </el-select>
      <el-select v-model="query.status" clearable placeholder="状态">
        <el-option label="启用" value="启用" />
        <el-option label="停用" value="停用" />
      </el-select>
      <el-input v-model="query.keyword" clearable placeholder="模板名称" />
    </div>

    <el-table :data="tableRows" border empty-text="暂无交付物模板">
      <el-table-column prop="name" label="模板名称" min-width="160" />
      <el-table-column prop="department" label="部门" width="120" />
      <el-table-column prop="evidenceType" label="证据类型" width="110" />
      <el-table-column prop="required" label="必填规则" width="110" />
      <el-table-column prop="applies" label="适用场景" min-width="150" />
      <el-table-column prop="version" label="版本" width="90" />
      <el-table-column prop="references" label="引用数" width="90" />
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
      title="交付物模板"
      width="560px"
      :close-on-click-modal="!saving"
      :close-on-press-escape="!saving"
    >
      <el-form label-position="top">
        <el-form-item label="适用部门">
          <el-select v-model="formOrgId" :disabled="!orgOptions.length" placeholder="请选择授权组织">
            <el-option v-for="item in orgOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="模板名称">
          <el-input v-model="form.name" maxlength="120" show-word-limit />
        </el-form-item>
        <el-form-item label="证据类型">
          <el-select v-model="form.evidenceType">
            <el-option label="文档" value="DOCUMENT" />
            <el-option label="表格" value="SPREADSHEET" />
            <el-option label="图片" value="IMAGE" />
            <el-option label="附件" value="FILE" />
          </el-select>
        </el-form-item>
        <el-form-item label="必填规则">
          <el-radio-group v-model="form.required">
            <el-radio-button label="必填" />
            <el-radio-button label="选填" />
          </el-radio-group>
        </el-form-item>
        <el-form-item label="适用场景">
          <el-checkbox-group v-model="form.applies">
            <el-checkbox label="MONTH_PLAN">月计划</el-checkbox>
            <el-checkbox label="DAY_PLAN">日计划</el-checkbox>
            <el-checkbox label="RESULT">成果</el-checkbox>
          </el-checkbox-group>
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="form.description" type="textarea" :rows="3" maxlength="1000" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Edit, Plus, Search, SwitchButton } from '@element-plus/icons-vue'
import {
  createDeliverableTemplateApi,
  listDeliverableTemplatesApi,
  toggleDeliverableTemplateApi,
  updateDeliverableTemplateApi,
} from '@/api/department'
import { errorMessage } from '@/api/performance'
import { useAutoQuery } from '@/composables/useAutoQuery'
import { useDepartmentOrgScope } from '@/composables/useDepartmentOrgScope'
import { mapDeliverableTemplate } from '@/views/performanceAdapters'

type DeliverableTemplate = ReturnType<typeof mapDeliverableTemplate>

const { orgOptions, selectedOrgId, orgLoading, loadOrgScope } = useDepartmentOrgScope(false)
const query = reactive({
  status: '',
  keyword: '',
})
const dialogVisible = ref(false)
const tableRows = ref<DeliverableTemplate[]>([])
const editingId = ref<number | null>(null)
const loading = ref(false)
const saving = ref(false)
const operatingId = ref<number | null>(null)
const formOrgId = ref<number>()
const form = reactive({
  name: '',
  evidenceType: 'DOCUMENT',
  required: '必填',
  applies: ['MONTH_PLAN', 'RESULT'] as string[],
  description: '',
})
const autoQuery = useAutoQuery(
  () => [selectedOrgId.value, query.status, query.keyword],
  () => refresh(false),
)

async function refresh(showMessage = true) {
  loading.value = true
  try {
    const data = await listDeliverableTemplatesApi({
      orgId: selectedOrgId.value,
      status: query.status === '启用' ? 'ENABLED' : query.status === '停用' ? 'DISABLED' : undefined,
      keyword: query.keyword || undefined,
    })
    tableRows.value = data.map(mapDeliverableTemplate)
    if (showMessage) ElMessage.success('交付物模板已刷新')
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    loading.value = false
  }
}

function openEdit(row?: DeliverableTemplate) {
  editingId.value = row?.id || null
  formOrgId.value = row?.orgId ?? orgOptions.value[0]?.value
  Object.assign(form, {
    name: row?.name || '',
    evidenceType: row?.evidenceTypeCode || 'DOCUMENT',
    required: row?.required || '必填',
    applies: row ? [...row.appliesCodes] : ['MONTH_PLAN', 'RESULT'],
    description: row?.description || '',
  })
  dialogVisible.value = true
}

async function toggle(row: DeliverableTemplate) {
  if (operatingId.value !== null) return
  const enabled = row.status !== '启用'
  try {
    await ElMessageBox.confirm(
      `${enabled ? '启用' : '停用'}“${row.name}”${row.references ? `（当前被引用 ${row.references} 次）` : ''}？`,
      '确认操作',
      { type: 'warning', confirmButtonText: '确认', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  operatingId.value = row.id
  try {
    const result = await toggleDeliverableTemplateApi(row.id, enabled)
    row.status = enabled ? '启用' : '停用'
    ElMessage.success(result.message)
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    operatingId.value = null
  }
}

async function save() {
  if (formOrgId.value == null) {
    ElMessage.warning('请选择适用部门')
    return
  }
  if (!form.name.trim()) {
    ElMessage.warning('请输入模板名称')
    return
  }
  if (!form.applies.length) {
    ElMessage.warning('请至少选择一个适用场景')
    return
  }
  saving.value = true
  try {
    const payload = {
      orgId: formOrgId.value,
      templateName: form.name.trim(),
      evidenceType: form.evidenceType,
      required: form.required === '必填',
      appliesTo: form.applies.join(','),
      description: form.description.trim(),
    }
    const saved = editingId.value
      ? await updateDeliverableTemplateApi(editingId.value, payload)
      : await createDeliverableTemplateApi(payload)
    const row = mapDeliverableTemplate(saved)
    const index = tableRows.value.findIndex((item) => item.id === row.id)
    if (index >= 0) tableRows.value[index] = row
    else tableRows.value.unshift(row)
    dialogVisible.value = false
    ElMessage.success('交付物模板已保存')
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  try {
    await loadOrgScope()
    formOrgId.value = orgOptions.value[0]?.value
    await refresh(false)
    autoQuery.resume()
  } catch (error) {
    ElMessage.error(errorMessage(error))
  }
})
</script>
