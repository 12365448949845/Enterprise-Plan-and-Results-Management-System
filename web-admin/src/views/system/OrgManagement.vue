<template>
  <section class="page-panel" v-loading="loading">
    <div class="page-header">
      <div><span class="eyebrow">ORGANIZATION</span><h1 class="page-title">部门/项目组</h1><p class="page-subtitle">维护组织树、节点负责人和启用状态，为计划归属、审批链和统计口径提供基础。</p></div>
      <el-button type="primary" @click="openCreate">新增节点</el-button>
    </div>

    <div class="split-layout org-management-layout">
      <aside class="section-card org-card">
        <div class="section-header"><div><h2>组织树</h2><p>部门、小组和项目组</p></div></div>
        <el-tree :data="departments" node-key="id" default-expand-all highlight-current :props="{ label: 'name', children: 'children' }" @node-click="selectNode">
          <template #default="{ data }"><span class="org-tree-node"><span>{{ data.name }}</span><small>{{ typeLabel(data.orgType) }}</small></span></template>
        </el-tree>
      </aside>

      <section class="section-card org-detail-panel">
        <div class="section-header">
          <div><h2>{{ selected ? selected.name : '全部组织节点' }}</h2><p>{{ selected ? `${typeLabel(selected.orgType)} · ${selected.code}` : '选择左侧节点查看详情，或直接编辑列表。' }}</p></div>
          <el-button v-if="selected" @click="openEdit(selected)">编辑当前节点</el-button>
        </div>
        <el-table :data="visibleRows" empty-text="暂无组织节点">
          <el-table-column prop="name" label="节点" min-width="170"><template #default="{ row }"><strong>{{ '　'.repeat(row.depth) }}{{ row.name }}</strong></template></el-table-column>
          <el-table-column label="类型" width="120"><template #default="{ row }">{{ typeLabel(row.orgType) }}</template></el-table-column>
          <el-table-column prop="leaderName" label="负责人" width="140"><template #default="{ row }">{{ row.leaderName || '未配置' }}</template></el-table-column>
          <el-table-column prop="employeeCount" label="员工数" width="90" />
          <el-table-column label="状态" width="100"><template #default="{ row }"><el-tag :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '启用' : '禁用' }}</el-tag></template></el-table-column>
          <el-table-column label="操作" width="90"><template #default="{ row }"><el-button link type="primary" @click="openEdit(row)">编辑</el-button></template></el-table-column>
        </el-table>
      </section>
    </div>

    <el-drawer v-model="drawerVisible" :title="form.id ? '编辑组织节点' : '新增组织节点'" size="500px">
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <div class="form-grid two-columns">
          <el-form-item label="节点名称" prop="name"><el-input v-model="form.name" maxlength="80" /></el-form-item>
          <el-form-item label="组织编码" prop="code"><el-input v-model="form.code" maxlength="50" :disabled="Boolean(form.id)" /></el-form-item>
          <el-form-item label="节点类型" prop="orgType"><el-select v-model="form.orgType"><el-option label="部门" value="DEPARTMENT" /><el-option label="小组" value="GROUP" /><el-option label="项目组" value="PROJECT_GROUP" /></el-select></el-form-item>
          <el-form-item label="父节点" prop="parentId"><el-tree-select v-model="form.parentId" :data="parentOptions" check-strictly :render-after-expand="false" /></el-form-item>
          <el-form-item label="负责人"><el-select v-model="form.leaderUserId" clearable filterable><el-option v-for="leader in options.leaders" :key="leader.id" :label="leader.label" :value="leader.id" /></el-select></el-form-item>
          <el-form-item label="排序"><el-input-number v-model="form.sortNo" :min="0" :max="9999" controls-position="right" /></el-form-item>
          <el-form-item label="状态"><el-radio-group v-model="form.status"><el-radio-button :value="1">启用</el-radio-button><el-radio-button :value="0">禁用</el-radio-button></el-radio-group></el-form-item>
        </div>
      </el-form>
      <template #footer><el-button @click="drawerVisible = false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存</el-button></template>
    </el-drawer>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { createDepartment, flattenDepartments, getDepartments, getSystemOptions, updateDepartment, type DeptNode, type SystemOptions } from '@/api/system'

const loading = ref(false), saving = ref(false), drawerVisible = ref(false)
const departments = ref<DeptNode[]>([]), selected = ref<(DeptNode & { depth?: number })>()
const options = reactive<SystemOptions>({ departments: [], leaders: [], roles: [] })
const formRef = ref<FormInstance>()
const form = reactive<{ id?: number; name: string; code: string; orgType: string; parentId: number; leaderUserId?: number; sortNo: number; status: number }>({ name: '', code: '', orgType: 'DEPARTMENT', parentId: 0, sortNo: 0, status: 1 })
const rules: FormRules = { name: [{ required: true, message: '请输入节点名称' }], code: [{ required: true, pattern: /^[A-Z][A-Z0-9_]{1,49}$/, message: '请输入大写编码' }], orgType: [{ required: true }], parentId: [{ required: true }] }
const rows = computed(() => flattenDepartments(departments.value))
const visibleRows = computed(() => selected.value ? rows.value.filter((row) => row.id === selected.value?.id || isDescendant(row, selected.value!.id)) : rows.value)
const parentOptions = computed(() => [{ value: 0, label: '根节点', children: [] as any[] }, ...mapTree(departments.value.filter((node) => node.id !== form.id))])
function mapTree(nodes: DeptNode[]): any[] { return nodes.filter((node) => node.id !== form.id).map((node) => ({ value: node.id, label: node.name, disabled: node.status !== 1, children: mapTree(node.children || []) })) }
function isDescendant(row: DeptNode & { depth: number }, parentId: number) { let current = row.parentId; const map = new Map(rows.value.map((item) => [item.id, item.parentId])); while (current) { if (current === parentId) return true; current = map.get(current) || 0 } return false }
function typeLabel(type: string) { return ({ DEPARTMENT: '部门', GROUP: '小组', PROJECT_GROUP: '项目组' } as Record<string, string>)[type] || type }
function selectNode(node: DeptNode) { selected.value = node }
async function load() { loading.value = true; try { const [tree, base] = await Promise.all([getDepartments(), getSystemOptions()]); departments.value = tree; Object.assign(options, base) } catch (error) { ElMessage.error(error instanceof Error ? error.message : '组织数据加载失败') } finally { loading.value = false } }
function openCreate() { Object.assign(form, { id: undefined, name: '', code: '', orgType: 'DEPARTMENT', parentId: selected.value?.id || 0, leaderUserId: undefined, sortNo: rows.value.length + 1, status: 1 }); drawerVisible.value = true }
function openEdit(row: DeptNode & { depth?: number }) { Object.assign(form, { id: row.id, name: row.name, code: row.code, orgType: row.orgType, parentId: row.parentId, leaderUserId: row.leaderUserId, sortNo: row.sortNo, status: row.status }); drawerVisible.value = true }
async function save() { if (!await formRef.value?.validate()) return; saving.value = true; try { form.id ? await updateDepartment(form.id, form) : await createDepartment(form); ElMessage.success('组织节点已保存'); drawerVisible.value = false; await load() } catch (error) { ElMessage.error(error instanceof Error ? error.message : '保存失败') } finally { saving.value = false } }
onMounted(load)
</script>
