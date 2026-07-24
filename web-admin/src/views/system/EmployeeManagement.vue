<template>
  <section class="page-panel" v-loading="loading">
    <div class="page-header">
      <div>
        <span class="eyebrow">EMPLOYEE DIRECTORY</span>
        <h1 class="page-title">员工管理</h1>
        <p class="page-subtitle">维护员工归属、直属负责人、角色和账号状态；新增员工仍从“员工注册”进入。</p>
      </div>
      <div class="toolbar">
        <el-button @click="downloadTemplate">下载模板</el-button>
        <el-upload :auto-upload="false" :show-file-list="false" accept=".xlsx,.xls" :on-change="handleImport"><el-button>导入</el-button></el-upload>
        <el-button @click="exportList">导出</el-button>
        <el-button type="primary" @click="router.push('/system/employee-register')">注册员工</el-button>
      </div>
    </div>

    <div class="filter-bar">
      <el-input v-model="filters.keyword" clearable placeholder="姓名、账号、工号或手机号" @keyup.enter="loadUsers" />
      <el-select v-model="filters.deptId" clearable filterable placeholder="归属组织">
        <el-option v-for="dept in flatDepartments" :key="dept.id" :label="`${'　'.repeat(dept.depth)}${dept.name}`" :value="dept.id" />
      </el-select>
      <el-select v-model="filters.status" clearable placeholder="账号状态"><el-option label="启用" :value="1" /><el-option label="禁用" :value="0" /></el-select>
      <el-button type="primary" @click="loadUsers">查询</el-button><el-button @click="resetFilters">重置</el-button>
    </div>

    <div class="table-card">
      <el-table :data="page.records" empty-text="暂无员工数据">
        <el-table-column label="员工" min-width="180">
          <template #default="{ row }"><div class="identity-cell"><span class="user-avatar">{{ row.realName.slice(0, 1) }}</span><span><strong>{{ row.realName }}</strong><small>{{ row.employeeNo }}</small></span></div></template>
        </el-table-column>
        <el-table-column prop="username" label="登录账号" width="120" />
        <el-table-column prop="mobile" label="手机号" width="140" />
        <el-table-column prop="departmentName" label="归属组织" min-width="150" />
        <el-table-column prop="directLeaderName" label="直属负责人" width="130" />
        <el-table-column label="角色" min-width="180"><template #default="{ row }"><div class="tag-list"><el-tag v-for="name in row.roleNames" :key="name" size="small" effect="plain">{{ name }}</el-tag></div></template></el-table-column>
        <el-table-column label="状态" width="105"><template #default="{ row }"><el-tag :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '启用' : '禁用' }}</el-tag></template></el-table-column>
        <el-table-column label="安全" width="120"><template #default="{ row }"><span :class="row.forceChangePassword ? 'warning-text' : 'muted-text'">{{ row.forceChangePassword ? '待首次改密' : '正常' }}</span></template></el-table-column>
        <el-table-column label="操作" fixed="right" width="220">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link @click="resetPassword(row)">重置密码</el-button>
            <el-button link :type="row.status === 1 ? 'danger' : 'success'" @click="toggleStatus(row)">{{ row.status === 1 ? '禁用' : '启用' }}</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
    <div class="pagination-row"><span>共 {{ page.total }} 名员工</span><el-pagination v-model:current-page="filters.pageNo" :page-size="filters.pageSize" layout="prev, pager, next" :total="page.total" @current-change="loadUsers" /></div>

    <el-drawer v-model="drawerVisible" title="编辑员工" size="520px">
      <el-form ref="editFormRef" :model="editForm" :rules="editRules" label-position="top">
        <div class="form-grid two-columns">
          <el-form-item label="姓名" prop="realName"><el-input v-model="editForm.realName" /></el-form-item>
          <el-form-item label="手机号" prop="mobile"><el-input v-model="editForm.mobile" maxlength="11" /></el-form-item>
          <el-form-item label="归属组织" prop="deptId"><el-tree-select v-model="editForm.deptId" :data="departmentTreeOptions" check-strictly :render-after-expand="false" /></el-form-item>
          <el-form-item label="直属负责人"><el-select v-model="editForm.directLeaderId" clearable filterable><el-option v-for="leader in options.leaders" :key="leader.id" :label="leader.label" :value="leader.id" /></el-select></el-form-item>
          <el-form-item class="span-two" label="角色" prop="roleIds"><el-select v-model="editForm.roleIds" multiple collapse-tags><el-option v-for="role in options.roles" :key="role.id" :label="role.name" :value="role.id" :disabled="role.status !== 1" /></el-select></el-form-item>
          <el-form-item label="账号状态"><el-radio-group v-model="editForm.status"><el-radio-button :value="1">启用</el-radio-button><el-radio-button :value="0">禁用</el-radio-button></el-radio-group></el-form-item>
        </div>
      </el-form>
      <template #footer><el-button @click="drawerVisible = false">取消</el-button><el-button type="primary" :loading="saving" @click="saveEdit">保存</el-button></template>
    </el-drawer>

    <el-dialog v-model="importResultVisible" title="员工导入结果" width="520px">
      <el-descriptions :column="3" border><el-descriptions-item label="总数">{{ importResult.total }}</el-descriptions-item><el-descriptions-item label="成功">{{ importResult.success }}</el-descriptions-item><el-descriptions-item label="失败">{{ importResult.failed }}</el-descriptions-item></el-descriptions>
        <ul v-if="importResult.errors.length" class="import-errors"><li v-for="error in importResult.errors" :key="error">{{ error }}</li></ul>
        <p v-else class="empty-copy">全部数据导入成功。</p>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules, type UploadFile } from 'element-plus'
import { useRouter } from 'vue-router'
import { changeUserStatus, downloadBlob, downloadUserImportTemplate, exportUsers, flattenDepartments, getSystemOptions, getUsers, importUsers, resetUserPassword, updateUser, type PageResult, type SystemOptions, type UserItem } from '@/api/system'
import { useAutoQuery } from '@/composables/useAutoQuery'

const router = useRouter()
const loading = ref(false), saving = ref(false), drawerVisible = ref(false), importResultVisible = ref(false)
const editFormRef = ref<FormInstance>()
const options = reactive<SystemOptions>({ departments: [], leaders: [], roles: [] })
const page = reactive<PageResult<UserItem>>({ records: [], total: 0, pageNo: 1, pageSize: 20 })
const filters = reactive<{ keyword: string; deptId?: number; status?: number; pageNo: number; pageSize: number }>({ keyword: '', pageNo: 1, pageSize: 20 })
const editForm = reactive<{ id?: number; realName: string; mobile: string; deptId?: number; directLeaderId?: number; roleIds: number[]; status: number }>({ realName: '', mobile: '', roleIds: [], status: 1 })
const importResult = reactive({ total: 0, success: 0, failed: 0, errors: [] as string[] })
const editRules: FormRules = { realName: [{ required: true, message: '请输入姓名' }], mobile: [{ required: true, pattern: /^1\d{10}$/, message: '手机号格式不正确' }], deptId: [{ required: true, message: '请选择归属组织' }], roleIds: [{ required: true, type: 'array', min: 1, message: '至少选择一个角色' }] }
const flatDepartments = computed(() => flattenDepartments(options.departments))
const departmentTreeOptions = computed(() => mapDepartments(options.departments))
const autoQuery = useAutoQuery(
  () => [filters.keyword, filters.deptId, filters.status],
  () => {
    filters.pageNo = 1
    return loadUsers()
  },
)
function mapDepartments(nodes: SystemOptions['departments']): any[] { return nodes.map((node) => ({ value: node.id, label: node.name, disabled: node.status !== 1, children: mapDepartments(node.children || []) })) }

async function loadOptions() { Object.assign(options, await getSystemOptions()) }
async function loadUsers() {
  loading.value = true
  try { Object.assign(page, await getUsers({ ...filters })) }
  catch (error) { ElMessage.error(error instanceof Error ? error.message : '员工列表加载失败') }
  finally { loading.value = false }
}
async function resetFilters() { autoQuery.pause(); filters.keyword = ''; filters.deptId = undefined; filters.status = undefined; filters.pageNo = 1; await loadUsers(); autoQuery.resume() }
function openEdit(row: UserItem) { Object.assign(editForm, { id: row.id, realName: row.realName, mobile: row.mobile, deptId: row.deptId, directLeaderId: row.directLeaderId, roleIds: [...row.roleIds], status: row.status }); drawerVisible.value = true }
async function saveEdit() {
  if (!await editFormRef.value?.validate() || !editForm.id) return
  saving.value = true
  try { await updateUser(editForm.id, editForm); ElMessage.success('员工信息已保存'); drawerVisible.value = false; await Promise.all([loadUsers(), loadOptions()]) }
  catch (error) { ElMessage.error(error instanceof Error ? error.message : '保存失败') }
  finally { saving.value = false }
}
async function toggleStatus(row: UserItem) {
  try { await ElMessageBox.confirm(`确认${row.status === 1 ? '禁用' : '启用'}员工“${row.realName}”吗？`, '账号状态确认', { type: 'warning' }); await changeUserStatus(row.id, row.status === 1 ? 0 : 1); ElMessage.success('账号状态已更新'); await loadUsers() } catch (error) { if (error !== 'cancel' && error !== 'close') ElMessage.error(error instanceof Error ? error.message : '操作失败') }
}
async function resetPassword(row: UserItem) {
  try { await ElMessageBox.confirm(`将“${row.realName}”的密码重置为 123456，并要求首次登录改密。`, '重置密码', { type: 'warning' }); await resetUserPassword(row.id); ElMessage.success('密码已重置'); await loadUsers() } catch (error) { if (error !== 'cancel' && error !== 'close') ElMessage.error(error instanceof Error ? error.message : '重置失败') }
}
async function handleImport(uploadFile: UploadFile) {
  if (!uploadFile.raw) return
  try { Object.assign(importResult, await importUsers(uploadFile.raw)); importResultVisible.value = true; await loadUsers() }
  catch (error) { ElMessage.error(error instanceof Error ? error.message : '导入失败') }
}
async function downloadTemplate() { try { downloadBlob(await downloadUserImportTemplate(), '员工导入模板.xlsx') } catch (error) { ElMessage.error(error instanceof Error ? error.message : '模板下载失败') } }
async function exportList() { try { downloadBlob(await exportUsers(), '员工清单.csv') } catch (error) { ElMessage.error(error instanceof Error ? error.message : '导出失败') } }
onMounted(async () => { try { await Promise.all([loadOptions(), loadUsers()]); autoQuery.resume() } catch (error) { ElMessage.error(error instanceof Error ? error.message : '页面加载失败') } })
</script>
