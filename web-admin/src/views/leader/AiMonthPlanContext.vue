<template>
  <section class="page-panel" v-loading="loading">
    <div class="page-header">
      <div><span class="eyebrow">AI PLANNING CONTEXT</span><h1 class="page-title">本月计划要求</h1><p class="page-subtitle">为授权组织维护部门目标和领导要求，员工生成月计划时作为受控上下文。</p></div>
      <el-button type="primary" :loading="saving" :disabled="!scopeOrgId" @click="save">保存要求</el-button>
    </div>
    <div class="filter-bar">
      <el-date-picker v-model="planMonth" type="month" value-format="YYYY-MM" :disabled-date="disablePastMonth" />
      <el-select v-model="scopeOrgId" :loading="orgLoading" placeholder="选择授权组织" filterable>
        <el-option v-for="item in orgOptions" :key="item.value" :label="item.label" :value="item.value" />
      </el-select>
      <el-button @click="load">查询</el-button>
    </div>
    <el-alert type="info" :closable="false" show-icon title="只维护真实、已确认的业务要求；AI 仍不会替员工提交或替领导审批。" />
    <div class="context-layout">
      <section class="dashboard-section">
        <div class="section-header"><div><h2>部门目标</h2><p>说明本月组织需要达成的结果、指标和优先级。</p></div><el-tag effect="plain">{{ form.orgName || '未选择组织' }}</el-tag></div>
        <el-input v-model="form.departmentGoal" type="textarea" :rows="10" maxlength="10000" show-word-limit placeholder="例如：完成重点客户版本交付；核心需求按期闭环率达到 95%。" />
      </section>
      <section class="dashboard-section">
        <div class="section-header"><div><h2>直属领导要求</h2><p>补充拆解原则、交付物标准、必须覆盖事项和协作边界。</p></div><el-tag type="warning" effect="plain">v{{ form.versionNo }}</el-tag></div>
        <el-input v-model="form.leaderRequirement" type="textarea" :rows="10" maxlength="10000" show-word-limit placeholder="例如：每项计划必须有可下载交付物；上线类任务需包含回滚方案。" />
      </section>
    </div>
    <p class="save-meta">{{ form.updatedAt ? `最近更新：${form.updatedAt}` : '当前月份尚未维护要求' }}</p>
  </section>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { getLeaderAiContextApi, saveLeaderAiContextApi } from '@/api/aiManagement'
import { currentMonth } from '@/api/performance'
import { useLeaderOrgScope } from '@/composables/useLeaderOrgScope'

const { orgOptions, scopeOrgId, orgLoading, loadOrgScope } = useLeaderOrgScope()
const planMonth = ref(currentMonth())
const loading = ref(false), saving = ref(false)
const form = reactive({ orgName: '', departmentGoal: '', leaderRequirement: '', versionNo: 0, updatedAt: '' })
let requestVersion = 0
function formatMonth(date: Date) { return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}` }
function disablePastMonth(date: Date) { return formatMonth(date) < currentMonth() }
async function load() {
  if (!scopeOrgId.value || !planMonth.value) return
  const request = ++requestVersion; loading.value = true
  try {
    const result = await getLeaderAiContextApi(planMonth.value, scopeOrgId.value)
    if (request === requestVersion) Object.assign(form, result)
  } catch (error) { if (request === requestVersion) ElMessage.error(error instanceof Error ? error.message : '计划要求加载失败') }
  finally { if (request === requestVersion) loading.value = false }
}
async function save() {
  if (!scopeOrgId.value) return
  saving.value = true
  try {
    const result = await saveLeaderAiContextApi({ orgId: scopeOrgId.value, planMonth: planMonth.value, departmentGoal: form.departmentGoal, leaderRequirement: form.leaderRequirement, versionNo: form.versionNo })
    Object.assign(form, result); ElMessage.success('本月计划要求已保存')
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '保存失败') }
  finally { saving.value = false }
}
watch([scopeOrgId, planMonth], load)
onMounted(async () => { await loadOrgScope(); await load() })
</script>

<style scoped>
.context-layout { display:grid; grid-template-columns:1fr 1fr; gap:16px; margin-top:16px; }
.save-meta { color:#64748b; text-align:right; font-size:12px; }
@media (max-width: 900px) { .context-layout { grid-template-columns:1fr; } }
</style>
