<template>
  <article class="week-task-editor" :class="{ 'has-error': hasError }">
    <header>
      <button type="button" class="week-task-editor__toggle" @click="collapsed = !collapsed">
        <span class="week-task-editor__index">{{ String(index + 1).padStart(2, '0') }}</span>
        <span><strong>{{ title }}</strong><small>{{ sourceLabel }}</small></span>
      </button>
      <div class="week-task-editor__actions">
        <el-button link :disabled="disabled" @click="$emit('duplicate')">复制</el-button>
        <el-button link type="danger" :disabled="disabled" @click="$emit('remove')">删除</el-button>
        <button type="button" class="week-task-editor__chevron" :aria-label="collapsed ? '展开任务' : '折叠任务'" @click="collapsed = !collapsed">{{ collapsed ? '＋' : '－' }}</button>
      </div>
    </header>

    <div v-show="!collapsed" class="week-task-editor__body">
      <section :id="`week-item-${index}-source`" class="week-task-field" :class="{ 'is-error': fieldErrors.source }">
        <div class="week-task-field__label"><span>来源</span><strong>关联月计划条目</strong></div>
        <el-select v-model="model.monthPlanItemId" filterable placeholder="搜索并选择已审批月计划任务" :disabled="disabled" class="full-control">
          <el-option v-for="option in visibleOptions" :key="option.monthPlanItemId" :value="option.monthPlanItemId" :label="`${option.planMonth} · ${option.taskName}`">
            <div class="week-source-option"><span>{{ option.planMonth }} · {{ option.taskName }}</span><small>{{ option.taskType === 'EXTRA' ? '额外任务' : '常规任务' }} · 权重 {{ option.performanceWeight }}% · 已拆 {{ option.existingWeekPlanCount }} 周</small></div>
          </el-option>
        </el-select>
        <small v-if="fieldErrors.source" class="week-task-field__error">{{ fieldErrors.source }}</small>
      </section>

      <section :id="`week-item-${index}-content`" class="week-task-field" :class="{ 'is-error': fieldErrors.content }">
        <div class="week-task-field__label"><span>01 / 本周做什么</span><strong>工作内容</strong></div>
        <el-input v-model="model.content" type="textarea" :rows="4" maxlength="5000" show-word-limit placeholder="写清本周要推进的具体工作和预期结果" :disabled="disabled" />
        <small v-if="fieldErrors.content" class="week-task-field__error">{{ fieldErrors.content }}</small>
      </section>

      <div class="week-task-editor__pair">
        <section :id="`week-item-${index}-deliverable`" class="week-task-field">
          <div class="week-task-field__label"><span>02 / 交付什么</span><strong>计划交付物</strong></div>
          <el-input v-model="model.deliverable" maxlength="500" placeholder="例如：方案文档、上线功能、数据报表" :disabled="disabled" />
        </section>
        <section :id="`week-item-${index}-finish-date`" class="week-task-field" :class="{ 'is-error': fieldErrors.finishDate }">
          <div class="week-task-field__label"><span>03 / 何时完成</span><strong>计划完成日期</strong></div>
          <el-date-picker v-model="model.plannedFinishDate" type="date" value-format="YYYY-MM-DD" :disabled-date="disableOutsideWeek" :disabled="disabled || !weekStart" class="full-control" />
          <small v-if="fieldErrors.finishDate" class="week-task-field__error">{{ fieldErrors.finishDate }}</small>
        </section>
      </div>
    </div>
  </article>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import type { WeekPlanParentOption, WeekPlanSavePayload } from '@/api/weekPlan'

const props = defineProps<{ item: WeekPlanSavePayload['items'][number]; index: number; options: WeekPlanParentOption[]; usedSourceIds: number[]; weekStart: string; weekEnd: string; disabled?: boolean; fieldErrors: { source?: string; content?: string; finishDate?: string } }>()
defineEmits<{ duplicate: []; remove: [] }>()
const model = defineModel<WeekPlanSavePayload['items'][number]>({ required: true })
const collapsed = ref(false)
const title = computed(() => model.value.content.trim().split('\n')[0]?.slice(0, 38) || '待填写的周任务')
const selectedSource = computed(() => props.options.find((option) => option.monthPlanItemId === model.value.monthPlanItemId))
const sourceLabel = computed(() => selectedSource.value ? `${selectedSource.value.planMonth} · ${selectedSource.value.taskName}` : '尚未选择月计划来源')
const visibleOptions = computed(() => props.options.filter((option) => option.monthPlanItemId === model.value.monthPlanItemId || !props.usedSourceIds.includes(option.monthPlanItemId)))
const hasError = computed(() => Object.values(props.fieldErrors).some(Boolean))
function dateValue(date: Date) { return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}` }
function disableOutsideWeek(date: Date) { const value = dateValue(date); return !props.weekStart || value < props.weekStart || value > props.weekEnd || value < dateValue(new Date()) }
</script>

<style scoped>
.week-task-editor { overflow: hidden; border: 1px solid var(--line, #dce3df); border-radius: 12px; background: #fff; transition: border-color .18s ease, box-shadow .18s ease; }.week-task-editor:hover { border-color: #b9cec4; box-shadow: 0 9px 24px rgb(35 57 49 / 7%); }.week-task-editor.has-error { border-color: #e4b9ab; }
.week-task-editor header { display: flex; align-items: center; justify-content: space-between; gap: 14px; padding: 14px 16px; border-bottom: 1px solid var(--line, #e4e9e6); background: linear-gradient(90deg, #f6f9f6, #fffdf8); }
.week-task-editor__toggle { display: flex; flex: 1; gap: 12px; align-items: center; min-width: 0; padding: 0; border: 0; color: inherit; background: transparent; text-align: left; cursor: pointer; }.week-task-editor__toggle > span:last-child { display: grid; gap: 3px; min-width: 0; }.week-task-editor__toggle strong, .week-task-editor__toggle small { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }.week-task-editor__toggle small { color: var(--muted, #687a74); }
.week-task-editor__index { display: grid; width: 38px; height: 38px; place-items: center; border-radius: 9px 9px 9px 3px; color: #fff; background: var(--blue, #2d776c); font-family: "IBM Plex Mono", "Cascadia Mono", monospace; font-size: 12px; }
.week-task-editor__actions { display: flex; align-items: center; }.week-task-editor__chevron { width: 28px; height: 28px; border: 1px solid var(--line, #dce3df); border-radius: 7px; color: var(--muted, #687a74); background: #fff; cursor: pointer; }
.week-task-editor__body { display: grid; gap: 18px; padding: 18px; }.week-task-editor__pair { display: grid; grid-template-columns: minmax(0, 1.25fr) minmax(210px, .75fr); gap: 14px; }
.week-task-field { display: grid; gap: 8px; padding: 13px; border: 1px solid #e6ebe8; border-radius: 9px; background: #fcfdfb; }.week-task-field.is-error { border-color: #d88770; background: #fff9f6; }.week-task-field__label { display: flex; align-items: baseline; justify-content: space-between; gap: 12px; }.week-task-field__label span { color: var(--blue, #2d776c); font-size: 10px; font-weight: 750; letter-spacing: .06em; }.week-task-field__label strong { font-size: 12px; }.week-task-field__error { color: #b5533d; font-size: 11px; }
.week-source-option { display: grid; gap: 2px; }.week-source-option small { color: var(--muted, #687a74); font-size: 11px; }
@media (max-width: 760px) { .week-task-editor__pair { grid-template-columns: 1fr; }.week-task-editor header { align-items: flex-start; }.week-task-editor__actions .el-button { display: none; } }
</style>
