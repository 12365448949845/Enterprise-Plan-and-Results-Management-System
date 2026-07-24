<template>
  <article class="task-editor-card" :class="{ 'has-errors': issues.length }">
    <header class="editor-card-header">
      <div class="task-number">任务 {{ String(index + 1).padStart(2, '0') }}</div>
      <div class="editor-title">
        <strong>{{ item.taskName || '未命名任务' }}</strong>
        <span>{{ completenessText }} · {{ item.performanceWeight || 0 }}% · {{ item.deadline || '未定日期' }}</span>
      </div>
      <div class="editor-actions">
        <el-button link type="primary" :disabled="disabled || optimizing" :loading="optimizing" @click="$emit('optimize')">AI 优化</el-button>
        <el-button link :disabled="disabled" @click="$emit('duplicate')">复制</el-button>
        <el-button link type="danger" :disabled="disabled" @click="$emit('remove')">删除</el-button>
      </div>
    </header>

    <div class="editor-section">
      <div class="section-kicker">任务定义</div>
      <div class="field-grid">
        <el-form-item label="任务名称" class="span-2" :error="fieldError('taskName')" :data-field-id="fieldId('taskName')">
          <el-input v-model="item.taskName" maxlength="120" :disabled="disabled" placeholder="用一句话说明要完成的任务" />
        </el-form-item>
        <el-form-item label="任务内容" class="span-2" :error="fieldError('taskContent')" :data-field-id="fieldId('taskContent')">
          <el-input v-model="item.taskContent" type="textarea" :rows="4" maxlength="5000" :disabled="disabled" placeholder="说明具体工作范围和关键动作" />
        </el-form-item>
      </div>
    </div>

    <div class="editor-section">
      <div class="section-kicker">交付验收</div>
      <div class="field-grid">
        <el-form-item label="交付物" class="span-2" :error="fieldError('deliverable')" :data-field-id="fieldId('deliverable')">
          <el-input v-model="item.deliverable" type="textarea" :rows="3" maxlength="500" :disabled="disabled" placeholder="例如：方案文档、上线功能、数据报表" />
        </el-form-item>
      </div>
    </div>

    <div class="editor-section compact-section">
      <div class="section-kicker">排期与考核</div>
      <div class="schedule-grid">
        <el-form-item label="截止日期" :error="fieldError('deadline')" :data-field-id="fieldId('deadline')">
          <el-date-picker v-model="item.deadline" type="date" value-format="YYYY-MM-DD" :disabled="disabled" :disabled-date="disabledDate" placeholder="选择日期" />
        </el-form-item>
        <el-form-item label="绩效权重" :error="fieldError('performanceWeight')" :data-field-id="fieldId('performanceWeight')">
          <el-input-number v-model="item.performanceWeight" :disabled="disabled" :min="0.01" :max="100" :step="1" :precision="2" controls-position="right" />
        </el-form-item>
      </div>
    </div>
  </article>
</template>

<script setup lang="ts" generic="T extends { taskName: string; taskContent: string; deliverable: string; deadline: string; performanceWeight: number }">
import { computed } from 'vue'

interface TaskValidationIssue { field?: string; message: string }

const props = defineProps<{ item: T; index: number; disabled: boolean; optimizing: boolean; issues: TaskValidationIssue[]; disabledDate: (date: Date) => boolean }>()
defineEmits<{ optimize: []; duplicate: []; remove: [] }>()

const required = ['taskName', 'taskContent', 'deliverable', 'deadline'] as const
const completeCount = computed(() => required.filter((key) => String(props.item[key] || '').trim()).length + (props.item.performanceWeight > 0 ? 1 : 0))
const completenessText = computed(() => completeCount.value === 5 ? '信息完整' : `还差 ${5 - completeCount.value} 项`)
const fieldId = (field: string) => `task-${props.index}-${field}`
const fieldError = (field: string) => props.issues.find((issue) => issue.field === field)?.message || ''
</script>

<style scoped>
.task-editor-card { overflow:hidden; border:1px solid var(--line); border-radius:14px; background:#fff; box-shadow:var(--shadow-soft); }
.task-editor-card.has-errors { border-left:4px solid #c65346; }
.editor-card-header { display:grid; grid-template-columns:auto minmax(0,1fr) auto; gap:15px; align-items:center; padding:16px 20px; border-bottom:1px solid #e9ede8; background:#f8faf7; }
.task-number { color:var(--blue); font:800 11px/1 "IBM Plex Mono",monospace; letter-spacing:.08em; }
.editor-title { display:grid; min-width:0; gap:4px; }
.editor-title strong { overflow:hidden; color:var(--ink); font-size:15px; text-overflow:ellipsis; white-space:nowrap; }
.editor-title span { color:var(--muted); font-size:11px; }
.editor-actions { display:flex; align-items:center; }
.editor-section { padding:20px; border-bottom:1px solid #edf0eb; }
.editor-section:last-child { border-bottom:0; }
.section-kicker { margin-bottom:14px; color:#587068; font-size:11px; font-weight:800; letter-spacing:.08em; }
.field-grid { display:grid; grid-template-columns:1fr 1fr; gap:0 16px; }
.span-2 { grid-column:1/-1; }
.schedule-grid { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:16px; }
.schedule-grid :deep(.el-input-number),.schedule-grid :deep(.el-date-editor) { width:100%; }
.more-settings { margin-top:2px; border:0; }
.more-settings :deep(.el-collapse-item__header) { height:36px; color:var(--muted); font-size:12px; border:0; }
.more-settings :deep(.el-collapse-item__wrap) { border:0; }
.more-settings :deep(.el-collapse-item__content) { padding:8px 0 0; }
:deep(.el-form-item) { margin-bottom:16px; }
:deep(.el-form-item__label) { color:#52645f; font-size:12px; font-weight:700; }
</style>
