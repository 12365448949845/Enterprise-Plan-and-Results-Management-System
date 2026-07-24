<template>
  <section class="page-panel" v-loading="loading">
    <div class="page-header">
      <div><span class="eyebrow">WORK CALENDAR</span><h1 class="page-title">工作日规则</h1><p class="page-subtitle">维护工作日、节假日、请假、出差和特殊排班，作为日期填报与统计口径。</p></div>
      <el-button type="primary" @click="openCreate">新增日期规则</el-button>
    </div>

    <div class="filter-bar">
      <el-date-picker v-model="filters.month" type="month" value-format="YYYY-MM" placeholder="选择月份" />
      <el-select v-model="filters.ruleType" clearable placeholder="规则类型"><el-option v-for="item in ruleTypes" :key="item.value" :label="item.label" :value="item.value" /></el-select>
      <el-select v-model="filters.status" clearable placeholder="状态"><el-option label="启用" :value="1" /><el-option label="停用" :value="0" /></el-select>
      <el-button type="primary" @click="load">查询</el-button><el-button @click="resetFilters">重置</el-button>
    </div>

    <div class="table-card">
      <el-table :data="items" empty-text="所选月份暂无规则">
        <el-table-column prop="ruleDate" label="日期" width="140" />
        <el-table-column label="类型" width="130"><template #default="{ row }"><el-tag :type="typeTag(row.ruleType)" effect="plain">{{ typeLabel(row.ruleType) }}</el-tag></template></el-table-column>
        <el-table-column label="是否强制填报" width="140"><template #default="{ row }"><strong :class="row.forceReport ? 'success-text' : 'muted-text'">{{ row.forceReport ? '是' : '否' }}</strong></template></el-table-column>
        <el-table-column prop="description" label="说明" min-width="260"><template #default="{ row }">{{ row.description || '—' }}</template></el-table-column>
        <el-table-column label="版本" width="90"><template #default="{ row }">v{{ row.versionNo }}</template></el-table-column>
        <el-table-column label="状态" width="90"><template #default="{ row }"><el-tag :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '启用' : '停用' }}</el-tag></template></el-table-column>
        <el-table-column label="操作" width="150"><template #default="{ row }"><el-button link type="primary" @click="openEdit(row)">编辑</el-button><el-button link :type="row.status === 1 ? 'danger' : 'success'" @click="toggleStatus(row)">{{ row.status === 1 ? '停用' : '启用' }}</el-button></template></el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="dialogVisible" :title="form.id ? '调整工作日规则' : '新增工作日规则'" width="540px">
      <el-alert v-if="form.id" title="保存调整会生成新版本，历史规则不会被覆盖。" type="info" :closable="false" show-icon />
      <el-form ref="formRef" :model="form" :rules="formRules" label-position="top" class="dialog-form-gap">
        <div class="form-grid two-columns">
          <el-form-item label="日期" prop="ruleDate"><el-date-picker v-model="form.ruleDate" value-format="YYYY-MM-DD" type="date" placeholder="选择日期" /></el-form-item>
          <el-form-item label="规则类型" prop="ruleType"><el-select v-model="form.ruleType"><el-option v-for="item in ruleTypes" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item>
          <el-form-item label="强制填报"><el-switch v-model="form.forceReport" inline-prompt active-text="是" inactive-text="否" /></el-form-item>
          <el-form-item label="启用状态"><el-switch v-model="form.enabled" inline-prompt active-text="启用" inactive-text="停用" /></el-form-item>
          <el-form-item class="span-two" label="说明"><el-input v-model="form.description" type="textarea" :rows="3" maxlength="500" show-word-limit /></el-form-item>
        </div>
      </el-form>
      <template #footer><el-button @click="dialogVisible = false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存</el-button></template>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { changeWorkdayStatus, createWorkdayRule, getWorkdayRules, updateWorkdayRule, type WorkdayRule } from '@/api/system'
import { useAutoQuery } from '@/composables/useAutoQuery'

const loading = ref(false), saving = ref(false), dialogVisible = ref(false), items = ref<WorkdayRule[]>([]), formRef = ref<FormInstance>()
const now = new Date(), currentMonth = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`
const filters = reactive<{ month: string; ruleType?: string; status?: number }>({ month: currentMonth })
const form = reactive<{ id?: number; ruleDate: string; ruleType: string; forceReport: boolean; description: string; enabled: boolean }>({ ruleDate: '', ruleType: 'WORKDAY', forceReport: true, description: '', enabled: true })
const formRules: FormRules = { ruleDate: [{ required: true, message: '请选择日期' }], ruleType: [{ required: true, message: '请选择规则类型' }] }
const ruleTypes = [
  { value: 'WORKDAY', label: '工作日' }, { value: 'WEEKEND', label: '周末' }, { value: 'HOLIDAY', label: '节假日' },
  { value: 'LEAVE', label: '请假' }, { value: 'BUSINESS_TRIP', label: '出差' }, { value: 'SPECIAL_SHIFT', label: '特殊排班' },
]
const autoQuery = useAutoQuery(
  () => [filters.month, filters.ruleType, filters.status],
  () => load(),
)
function typeLabel(value: string) { return ruleTypes.find((item) => item.value === value)?.label || value }
function typeTag(value: string) { if (value === 'WORKDAY' || value === 'SPECIAL_SHIFT') return 'success'; if (value === 'HOLIDAY' || value === 'WEEKEND') return 'warning'; return 'info' }
async function load() { loading.value = true; try { items.value = await getWorkdayRules(filters) } catch (error) { ElMessage.error(error instanceof Error ? error.message : '工作日规则加载失败') } finally { loading.value = false } }
async function resetFilters() { autoQuery.pause(); filters.month = currentMonth; filters.ruleType = undefined; filters.status = undefined; await load(); autoQuery.resume() }
function openCreate() { Object.assign(form, { id: undefined, ruleDate: '', ruleType: 'WORKDAY', forceReport: true, description: '', enabled: true }); dialogVisible.value = true }
function openEdit(row: WorkdayRule) { Object.assign(form, { id: row.id, ruleDate: row.ruleDate, ruleType: row.ruleType, forceReport: row.forceReport, description: row.description || '', enabled: row.status === 1 }); dialogVisible.value = true }
async function save() { if (!await formRef.value?.validate()) return; saving.value = true; const data = { ruleDate: form.ruleDate, ruleType: form.ruleType, forceReport: form.forceReport, description: form.description, status: form.enabled ? 1 : 0 }; try { form.id ? await updateWorkdayRule(form.id, data) : await createWorkdayRule(data); ElMessage.success('工作日规则已保存'); dialogVisible.value = false; await load() } catch (error) { ElMessage.error(error instanceof Error ? error.message : '保存失败') } finally { saving.value = false } }
async function toggleStatus(row: WorkdayRule) { try { await ElMessageBox.confirm(`确认${row.status === 1 ? '停用' : '启用'} ${row.ruleDate} 的规则吗？`, '规则状态确认', { type: 'warning' }); await changeWorkdayStatus(row.id, row.status === 1 ? 0 : 1); ElMessage.success('规则状态已更新'); await load() } catch (error) { if (error !== 'cancel' && error !== 'close') ElMessage.error(error instanceof Error ? error.message : '操作失败') } }
onMounted(async () => { await load(); autoQuery.resume() })
</script>
