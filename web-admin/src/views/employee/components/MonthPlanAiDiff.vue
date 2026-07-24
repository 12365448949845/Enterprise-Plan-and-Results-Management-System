<template>
  <el-dialog
    :model-value="visible"
    title="预览 AI 月计划建议"
    width="min(1080px, calc(100vw - 48px))"
    top="5vh"
    append-to-body
    class="month-plan-ai-diff-dialog"
    @update:model-value="emit('close')"
  >
    <el-alert type="info" :closable="false" show-icon title="AI 建议不会直接覆盖表单；请选择需要应用的内容。" />
    <div class="summary-diff">
      <el-checkbox v-model="applySummary">应用 AI 计划摘要</el-checkbox>
      <div class="diff-grid">
        <div><span>当前摘要</span><p>{{ currentSummary || '暂无' }}</p></div>
        <div><span>AI 建议</span><p>{{ suggestion?.summary || '-' }}</p></div>
      </div>
    </div>
    <el-table :data="suggestion?.items || []" border max-height="42vh">
      <el-table-column width="54">
        <template #default="{ $index }"><el-checkbox :model-value="selectedIndexes.includes($index)" @change="toggle($index)" /></template>
      </el-table-column>
      <el-table-column type="index" label="#" width="52" />
      <el-table-column prop="taskName" label="任务" min-width="150" />
      <el-table-column prop="taskContent" label="任务内容" min-width="220" />
      <el-table-column prop="deliverable" label="交付物" min-width="160" />
      <el-table-column prop="deadline" label="截止日" width="112" />
      <el-table-column prop="performanceWeight" label="权重" width="80"><template #default="{ row }">{{ row.performanceWeight }}%</template></el-table-column>
    </el-table>
    <el-alert v-for="warning in suggestion?.warnings || []" :key="warning" class="warning" type="warning" :closable="false" show-icon :title="warning" />
    <template #footer>
      <el-button @click="emit('ignore')">忽略建议</el-button>
      <el-button :disabled="!applySummary && !selectedIndexes.length" @click="applySelected">追加所选内容</el-button>
      <el-button type="primary" @click="applyAll">应用整表</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import type { AiGenerateResponse } from '@/api/employeeAi'

const props = defineProps<{ visible: boolean; suggestion?: AiGenerateResponse; currentSummary: string }>()
const emit = defineEmits<{
  close: []
  ignore: []
  apply: [value: { mode: 'replace' | 'append'; applySummary: boolean; indexes: number[] }]
}>()
const selectedIndexes = ref<number[]>([])
const applySummary = ref(true)

watch(() => props.suggestion, (value) => {
  selectedIndexes.value = value?.items.map((_, index) => index) ?? []
  applySummary.value = true
}, { immediate: true })

function toggle(index: number) {
  selectedIndexes.value = selectedIndexes.value.includes(index)
    ? selectedIndexes.value.filter((item) => item !== index)
    : [...selectedIndexes.value, index]
}
function applySelected() { emit('apply', { mode: 'append', applySummary: applySummary.value, indexes: selectedIndexes.value }) }
function applyAll() { emit('apply', { mode: 'replace', applySummary: true, indexes: props.suggestion?.items.map((_, index) => index) ?? [] }) }
</script>

<style scoped>
:deep(.month-plan-ai-diff-dialog.el-dialog),
:deep(.month-plan-ai-diff-dialog .el-dialog) {
  display: flex;
  flex-direction: column;
  max-height: 90vh;
  margin-bottom: 0;
}

:deep(.month-plan-ai-diff-dialog.el-dialog .el-dialog__body),
:deep(.month-plan-ai-diff-dialog .el-dialog__body) {
  flex: 1 1 auto;
  min-height: 0;
  overflow: auto;
}

:deep(.month-plan-ai-diff-dialog.el-dialog .el-dialog__footer),
:deep(.month-plan-ai-diff-dialog .el-dialog__footer) {
  flex: 0 0 auto;
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}

:deep(.month-plan-ai-diff-dialog.el-dialog .el-dialog__footer .el-button),
:deep(.month-plan-ai-diff-dialog .el-dialog__footer .el-button) {
  margin-left: 0;
}

.summary-diff { margin: 16px 0; }
.diff-grid { display:grid; grid-template-columns:1fr 1fr; gap:12px; margin-top:10px; }
.diff-grid > div { border:1px solid #e1e7ef; border-radius:10px; padding:12px; background:#f8fafc; }
.diff-grid span { color:#64748b; font-size:12px; }
.diff-grid p { margin:6px 0 0; white-space:pre-wrap; }
.warning { margin-top:10px; }

@media (max-width: 720px) {
  :deep(.month-plan-ai-diff-dialog.el-dialog),
  :deep(.month-plan-ai-diff-dialog .el-dialog) {
    width: calc(100vw - 16px) !important;
    max-height: calc(100vh - 16px);
    margin-top: 8px !important;
  }

  .diff-grid { grid-template-columns:1fr; }
}
</style>
