<template>
  <section class="page-panel" v-loading="loading">
    <div class="page-header">
      <div><span class="eyebrow">AUDIT TRAIL</span><h1 class="page-title">审计日志</h1><p class="page-subtitle">审计记录只读，支持按时间、操作人、对象和动作查询；导出行为也会留痕。</p></div>
      <el-button @click="exportList">导出查询结果</el-button>
    </div>

    <div class="filter-bar audit-filter-bar">
      <el-date-picker v-model="filters.range" type="datetimerange" range-separator="至" start-placeholder="开始时间" end-placeholder="结束时间" value-format="YYYY-MM-DDTHH:mm:ss" />
      <el-input v-model="filters.username" clearable placeholder="操作人" />
      <el-input v-model="filters.action" clearable placeholder="动作编码" />
      <el-select v-model="filters.result" clearable placeholder="结果"><el-option label="成功" value="SUCCESS" /><el-option label="失败" value="FAILED" /></el-select>
      <el-button type="primary" @click="search">查询</el-button><el-button @click="reset">重置</el-button>
    </div>

    <div class="table-card">
      <el-table :data="page.records" empty-text="暂无审计记录" @row-click="openDetail">
        <el-table-column prop="createdAt" label="时间" width="180" />
        <el-table-column prop="username" label="操作人" width="130"><template #default="{ row }">{{ row.username || '系统' }}</template></el-table-column>
        <el-table-column prop="action" label="动作" min-width="230" show-overflow-tooltip />
        <el-table-column label="对象" min-width="170"><template #default="{ row }">{{ row.targetType || '—' }}<span v-if="row.targetId" class="muted-text"> #{{ row.targetId }}</span></template></el-table-column>
        <el-table-column prop="clientIp" label="IP" width="130"><template #default="{ row }">{{ row.clientIp || '—' }}</template></el-table-column>
        <el-table-column label="结果" width="90"><template #default="{ row }"><el-tag :type="row.result === 'SUCCESS' ? 'success' : 'danger'">{{ row.result === 'SUCCESS' ? '成功' : '失败' }}</el-tag></template></el-table-column>
        <el-table-column label="详情" width="90"><template #default="{ row }"><el-button link type="primary" @click.stop="openDetail(row)">查看</el-button></template></el-table-column>
      </el-table>
    </div>
    <div class="pagination-row"><span>共 {{ page.total }} 条记录</span><el-pagination v-model:current-page="filters.pageNo" :page-size="filters.pageSize" layout="prev, pager, next" :total="page.total" @current-change="load" /></div>

    <el-drawer v-model="detailVisible" title="审计记录详情" size="560px">
      <el-descriptions v-if="active" :column="1" border>
        <el-descriptions-item label="时间">{{ active.createdAt }}</el-descriptions-item>
        <el-descriptions-item label="操作人">{{ active.username || '系统' }}</el-descriptions-item>
        <el-descriptions-item label="动作">{{ active.action }}</el-descriptions-item>
        <el-descriptions-item label="对象">{{ active.targetType || '—' }} {{ active.targetId ? `#${active.targetId}` : '' }}</el-descriptions-item>
        <el-descriptions-item label="结果">{{ active.result }}</el-descriptions-item>
        <el-descriptions-item label="客户端IP">{{ active.clientIp || '未记录' }}</el-descriptions-item>
      </el-descriptions>
      <section v-if="active" class="audit-json-panel"><strong>变更摘要</strong><pre>{{ prettyDetail(active.detail) }}</pre></section>
    </el-drawer>
  </section>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { downloadBlob, exportAudits, getAudits, type AuditItem, type PageResult } from '@/api/system'
import { useAutoQuery } from '@/composables/useAutoQuery'

const loading = ref(false), detailVisible = ref(false), active = ref<AuditItem>()
const page = reactive<PageResult<AuditItem>>({ records: [], total: 0, pageNo: 1, pageSize: 20 })
const filters = reactive<{ range: string[]; username: string; action: string; result?: string; pageNo: number; pageSize: number }>({ range: [], username: '', action: '', pageNo: 1, pageSize: 20 })
const autoQuery = useAutoQuery(
  () => [filters.range?.[0], filters.range?.[1], filters.username, filters.action, filters.result],
  () => {
    filters.pageNo = 1
    return load()
  },
)
function params() { return { start: filters.range?.[0], end: filters.range?.[1], username: filters.username || undefined, action: filters.action || undefined, result: filters.result, pageNo: filters.pageNo, pageSize: filters.pageSize } }
async function load() { loading.value = true; try { Object.assign(page, await getAudits(params())) } catch (error) { ElMessage.error(error instanceof Error ? error.message : '审计日志加载失败') } finally { loading.value = false } }
function search() { filters.pageNo = 1; load() }
async function reset() { autoQuery.pause(); filters.range = []; filters.username = ''; filters.action = ''; filters.result = undefined; filters.pageNo = 1; await load(); autoQuery.resume() }
function openDetail(row: AuditItem) { active.value = row; detailVisible.value = true }
function prettyDetail(value?: string) { if (!value) return '无变更明细'; try { return JSON.stringify(JSON.parse(value), null, 2) } catch { return value } }
async function exportList() { try { downloadBlob(await exportAudits(params()), '审计日志.csv') } catch (error) { ElMessage.error(error instanceof Error ? error.message : '审计导出失败') } }
onMounted(async () => { await load(); autoQuery.resume() })
</script>
