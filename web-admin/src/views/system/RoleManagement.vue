<template>
  <section class="page-panel" v-loading="loading">
    <div class="page-header">
      <div><span class="eyebrow">ROLE GOVERNANCE</span><h1 class="page-title">角色管理</h1><p class="page-subtitle">角色用于组合菜单、动作和数据范围。内置角色保留稳定编码，不允许删除。</p></div>
      <div class="toolbar"><el-button @click="router.push('/system/permissions')">配置权限</el-button><el-button type="primary" @click="openCreate">新增角色</el-button></div>
    </div>

    <div class="table-card">
      <el-table :data="roles" empty-text="暂无角色">
        <el-table-column label="角色" min-width="180"><template #default="{ row }"><div class="role-name-cell"><strong>{{ row.name }}</strong><small>{{ row.code }}</small></div></template></el-table-column>
        <el-table-column prop="description" label="说明" min-width="230"><template #default="{ row }">{{ row.description || '—' }}</template></el-table-column>
        <el-table-column label="数据范围" width="180"><template #default="{ row }">{{ scopeLabel(row.dataScope) }}</template></el-table-column>
        <el-table-column prop="userCount" label="关联用户" width="100" />
        <el-table-column prop="permissionCount" label="权限数" width="90" />
        <el-table-column label="属性" width="90"><template #default="{ row }"><el-tag v-if="row.builtIn" type="info" effect="plain">内置</el-tag><span v-else>自定义</span></template></el-table-column>
        <el-table-column label="状态" width="90"><template #default="{ row }"><el-tag :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '启用' : '禁用' }}</el-tag></template></el-table-column>
        <el-table-column label="操作" width="150"><template #default="{ row }"><el-button link type="primary" @click="openEdit(row)">编辑</el-button><el-button link @click="goPermission(row)">权限</el-button></template></el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑角色' : '新增角色'" width="560px">
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <div class="form-grid two-columns">
          <el-form-item label="角色名称" prop="name"><el-input v-model="form.name" maxlength="80" /></el-form-item>
          <el-form-item label="角色编码" prop="code"><el-input v-model="form.code" maxlength="50" :disabled="Boolean(form.builtIn)" /></el-form-item>
          <el-form-item class="span-two" label="角色说明"><el-input v-model="form.description" type="textarea" :rows="3" maxlength="255" show-word-limit /></el-form-item>
          <el-form-item label="数据范围" prop="dataScope"><el-select v-model="form.dataScope"><el-option v-for="scope in scopes" :key="scope.value" :label="scope.label" :value="scope.value" /></el-select></el-form-item>
          <el-form-item label="状态"><el-radio-group v-model="form.status"><el-radio-button :value="1">启用</el-radio-button><el-radio-button :value="0" :disabled="form.code === 'SUPER_ADMIN'">禁用</el-radio-button></el-radio-group></el-form-item>
        </div>
      </el-form>
      <template #footer><el-button @click="dialogVisible = false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存</el-button></template>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { useRouter } from 'vue-router'
import { createRole, getRoles, updateRole, type RoleItem } from '@/api/system'

const router = useRouter(), loading = ref(false), saving = ref(false), dialogVisible = ref(false), roles = ref<RoleItem[]>([]), formRef = ref<FormInstance>()
const form = reactive<{ id?: number; name: string; code: string; description: string; dataScope: string; status: number; builtIn?: boolean }>({ name: '', code: '', description: '', dataScope: 'SELF', status: 1 })
const rules: FormRules = { name: [{ required: true, message: '请输入角色名称' }], code: [{ required: true, pattern: /^[A-Z][A-Z0-9_]{1,49}$/, message: '请输入大写角色编码' }], dataScope: [{ required: true, message: '请选择数据范围' }] }
const scopes = [
  { value: 'SELF', label: '本人' }, { value: 'DIRECT_SUBORDINATE', label: '直属下属' }, { value: 'GROUP', label: '本组' },
  { value: 'DEPARTMENT_AND_CHILDREN', label: '本部门及下级' }, { value: 'ASSIGNED_ORG', label: '指定组织' },
  { value: 'ASSIGNED_CASE', label: '指定案件' }, { value: 'SYSTEM_CONFIG', label: '系统配置数据' }, { value: 'ALL', label: '全部数据' },
]
function scopeLabel(value: string) { return scopes.find((item) => item.value === value)?.label || value }
async function load() { loading.value = true; try { roles.value = await getRoles() } catch (error) { ElMessage.error(error instanceof Error ? error.message : '角色加载失败') } finally { loading.value = false } }
function openCreate() { Object.assign(form, { id: undefined, name: '', code: '', description: '', dataScope: 'SELF', status: 1, builtIn: false }); dialogVisible.value = true }
function openEdit(row: RoleItem) { Object.assign(form, row); dialogVisible.value = true }
function goPermission(row: RoleItem) { router.push({ path: '/system/permissions', query: { roleId: row.id } }) }
async function save() { if (!await formRef.value?.validate()) return; saving.value = true; try { form.id ? await updateRole(form.id, form) : await createRole(form); ElMessage.success('角色已保存'); dialogVisible.value = false; await load() } catch (error) { ElMessage.error(error instanceof Error ? error.message : '保存失败') } finally { saving.value = false } }
onMounted(load)
</script>
