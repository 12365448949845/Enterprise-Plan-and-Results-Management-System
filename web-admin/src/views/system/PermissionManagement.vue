<template>
  <section class="page-panel" v-loading="loading">
    <div class="page-header">
      <div><span class="eyebrow">ACCESS CONTROL</span><h1 class="page-title">权限管理</h1><p class="page-subtitle">配置角色可以看到的菜单和可以执行的动作。所有业务接口仍由后端再次校验。</p></div>
      <el-button type="primary" :disabled="!selectedRole || selectedRole.code === 'SUPER_ADMIN'" :loading="saving" @click="save">保存权限</el-button>
    </div>

    <div class="permission-layout">
      <aside class="section-card permission-role-list">
        <div class="section-header"><div><h2>选择角色</h2><p>切换后加载当前授权。</p></div></div>
        <button v-for="role in roles" :key="role.id" type="button" :class="['permission-role-item', { active: role.id === selectedRoleId }]" @click="selectRole(role)">
          <span><strong>{{ role.name }}</strong><small>{{ role.code }}</small></span><b>{{ role.permissionCount }}</b>
        </button>
      </aside>

      <section class="section-card permission-tree-panel">
        <div class="section-header">
          <div><h2>{{ selectedRole?.name || '角色权限' }}</h2><p>{{ selectedRole?.description || '请选择左侧角色。' }}</p></div>
          <div v-if="selectedRole" class="permission-summary"><span>数据范围</span><strong>{{ scopeLabel(selectedRole.dataScope) }}</strong></div>
        </div>
        <el-alert v-if="selectedRole?.code === 'SUPER_ADMIN'" title="超级管理员固定拥有全部权限，无需单独配置。" type="info" :closable="false" show-icon />
        <el-tree v-if="selectedRole" ref="treeRef" class="permission-tree" :data="permissions" node-key="id" show-checkbox default-expand-all :check-strictly="false" :props="{ label: 'name', children: 'children', disabled: disabledPermission }">
          <template #default="{ data }"><span class="permission-node"><span><strong>{{ data.name }}</strong><small>{{ data.code }}</small></span><el-tag size="small" effect="plain" :type="data.type === 'MENU' ? 'primary' : 'info'">{{ data.type === 'MENU' ? '菜单' : '动作' }}</el-tag></span></template>
        </el-tree>
        <div v-else class="empty-state"><strong>选择一个角色开始配置</strong><span>菜单权限和动作权限会在这里以树形展示。</span></div>
      </section>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue'
import { ElMessage, ElTree } from 'element-plus'
import { useRoute } from 'vue-router'
import { getPermissions, getRolePermissions, getRoles, saveRolePermissions, type PermissionNode, type RoleItem } from '@/api/system'

const route = useRoute(), loading = ref(false), saving = ref(false), roles = ref<RoleItem[]>([]), permissions = ref<PermissionNode[]>([]), selectedRoleId = ref<number>(), treeRef = ref<InstanceType<typeof ElTree>>()
const selectedRole = computed(() => roles.value.find((role) => role.id === selectedRoleId.value))
const scopeMap: Record<string, string> = { SELF: '本人', DIRECT_SUBORDINATE: '直属下属', GROUP: '本组', DEPARTMENT_AND_CHILDREN: '本部门及下级', ASSIGNED_ORG: '指定组织', ASSIGNED_CASE: '指定案件', SYSTEM_CONFIG: '系统配置数据', ALL: '全部数据' }
function scopeLabel(value: string) { return scopeMap[value] || value }
function disabledPermission() { return selectedRole.value?.code === 'SUPER_ADMIN' }
async function load() {
  loading.value = true
  try {
    const [roleData, permissionData] = await Promise.all([getRoles(), getPermissions()])
    roles.value = roleData; permissions.value = permissionData
    const queryId = Number(route.query.roleId)
    await selectRole(roles.value.find((role) => role.id === queryId) || roles.value.find((role) => role.code === 'SYS_ADMIN') || roles.value[0])
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '权限数据加载失败') }
  finally { loading.value = false }
}
async function selectRole(role?: RoleItem) {
  if (!role) return
  selectedRoleId.value = role.id
  const checked = role.code === 'SUPER_ADMIN' ? allPermissionIds(permissions.value) : await getRolePermissions(role.id)
  await nextTick(); treeRef.value?.setCheckedKeys(checked, false)
}
function allPermissionIds(nodes: PermissionNode[]): number[] { return nodes.flatMap((node) => [node.id, ...allPermissionIds(node.children || [])]) }
async function save() {
  if (!selectedRole.value || selectedRole.value.code === 'SUPER_ADMIN') return
  saving.value = true
  try {
    const checked = treeRef.value?.getCheckedKeys(false) as number[] || []
    const half = treeRef.value?.getHalfCheckedKeys() as number[] || []
    await saveRolePermissions(selectedRole.value.id, [...new Set([...checked, ...half])])
    ElMessage.success('角色权限已保存')
    roles.value = await getRoles()
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '权限保存失败') }
  finally { saving.value = false }
}
onMounted(load)
</script>
