<template>
  <section class="plan-ai">
    <header>
      <h2>{{ mode === 'week' ? 'AI 周计划助手' : 'AI 今日助手' }}</h2>
      <el-tag :type="context?.enabled ? 'success' : 'info'">{{ context?.enabled ? '可用' : '未启用' }}</el-tag>
    </header>
    <el-alert v-if="error" type="warning" :closable="false" :title="error" />
    <p v-if="context?.missingContext?.length">{{ context.missingContext.join('；') }}</p>
    <el-input v-model="intent" type="textarea" :rows="3" placeholder="描述工作重点和交付要求" />
    <el-button type="primary" :loading="busy" :disabled="!intent.trim() || !context?.enabled" @click="generate">生成建议</el-button>

    <el-drawer v-model="visible" title="AI 建议预览" size="560px">
      <div v-if="mode === 'week'" class="week-suggestions">
        <label v-for="(item, index) in weekDraft?.items" :key="index" class="task-option">
          <el-checkbox v-model="selected[index]" :aria-label="`选择第 ${index + 1} 条建议`" />
          <article>
            <div class="task-option__index">建议任务 {{ index + 1 }}</div>
            <dl>
              <dt>工作内容</dt><dd :class="{ missing: !item.content }">{{ item.content || 'AI 未返回工作内容' }}</dd>
              <dt>交付物</dt><dd :class="{ missing: !item.deliverable }">{{ item.deliverable || 'AI 未返回交付物，请重新生成' }}</dd>
              <dt>完成日期</dt><dd :class="{ missing: !item.plannedFinishDate }">{{ item.plannedFinishDate || 'AI 未安排完成日期' }}</dd>
              <dt>月计划来源</dt><dd>#{{ item.monthPlanItemId }}</dd>
            </dl>
          </article>
        </label>
      </div>
      <div v-else-if="dayDraft" class="day-fields">
        <el-checkbox v-model="fields.source">月计划关联：{{ dayDraft.relatedMonthPlanItemId || '无' }}</el-checkbox>
        <el-checkbox v-model="fields.content">工作内容：{{ dayDraft.content }}</el-checkbox>
        <el-checkbox v-model="fields.remark">备注：{{ dayDraft.remark || '无' }}</el-checkbox>
      </div>
      <el-input v-model="instruction" class="adjust" placeholder="继续调整，例如：精简并明确交付物" @keyup.enter="adjust" />
      <template #footer>
        <el-button @click="ignore">忽略</el-button>
        <el-button :loading="busy" :disabled="!instruction.trim()" @click="adjust">让 AI 调整</el-button>
        <el-button type="primary" :disabled="mode === 'week' && !selected.some(Boolean)" @click="apply">采纳所选内容</el-button>
      </template>
    </el-drawer>
  </section>
</template>

<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { adjustDayPlanAiApi, adjustWeekPlanAiApi, aiRequestId, generateDayPlanAiApi, generateWeekPlanAiApi, getDayPlanAiContextApi, getWeekPlanAiContextApi, recordAiSuggestionActionApi, type AiContextResponse, type DayPlanAiDraft, type DayPlanAiForm, type WeekPlanAiDraft, type WeekPlanAiItem } from '@/api/employeeAi'

const props = defineProps<{ mode: 'week' | 'day'; date: string; form: { items?: WeekPlanAiItem[]; relatedMonthPlanItemId?: number | null; content?: string; remark?: string } }>()
const emit = defineEmits<{ apply: [WeekPlanAiItem[] | DayPlanAiForm] }>()
const context = ref<AiContextResponse>()
const intent = ref('')
const instruction = ref('')
const busy = ref(false)
const error = ref('')
const visible = ref(false)
const weekDraft = ref<WeekPlanAiDraft>()
const dayDraft = ref<DayPlanAiDraft>()
const selected = ref<boolean[]>([])
const fields = ref({ source: true, content: true, remark: true })

async function load() {
  if (!props.date) return
  try { context.value = props.mode === 'week' ? await getWeekPlanAiContextApi(props.date) : await getDayPlanAiContextApi(props.date) }
  catch (e) { error.value = e instanceof Error ? e.message : 'AI 上下文加载失败' }
}
async function generate() {
  busy.value = true; error.value = ''
  try {
    if (props.mode === 'week') {
      weekDraft.value = await generateWeekPlanAiApi({ requestId: aiRequestId(), weekStart: props.date, intentText: intent.value, currentForm: { items: props.form.items ?? [] } })
      selected.value = weekDraft.value.items.map(() => true)
    } else dayDraft.value = await generateDayPlanAiApi({ requestId: aiRequestId(), planDate: props.date, intentText: intent.value, currentForm: { relatedMonthPlanItemId: props.form.relatedMonthPlanItemId ?? null, content: props.form.content ?? '', remark: props.form.remark ?? '' } })
    visible.value = true
  } catch (e) { error.value = e instanceof Error ? e.message : 'AI 生成失败' }
  finally { busy.value = false }
}
async function adjust() {
  busy.value = true; error.value = ''
  try {
    if (props.mode === 'week' && weekDraft.value) {
      weekDraft.value = await adjustWeekPlanAiApi({ requestId: aiRequestId(), weekStart: props.date, draft: { items: weekDraft.value.items }, instruction: instruction.value })
      selected.value = weekDraft.value.items.map(() => true)
    } else if (dayDraft.value) dayDraft.value = await adjustDayPlanAiApi({ requestId: aiRequestId(), planDate: props.date, draft: dayDraft.value, instruction: instruction.value })
    instruction.value = ''
  } catch (e) { error.value = e instanceof Error ? e.message : 'AI 调整失败' }
  finally { busy.value = false }
}
async function apply() {
  const draft = props.mode === 'week' ? weekDraft.value : dayDraft.value
  if (!draft) return
  if (props.mode === 'week') emit('apply', (draft as WeekPlanAiDraft).items.filter((_, index) => selected.value[index]))
  else {
    const day = draft as DayPlanAiDraft
    emit('apply', { relatedMonthPlanItemId: fields.value.source ? day.relatedMonthPlanItemId : props.form.relatedMonthPlanItemId ?? null, content: fields.value.content ? day.content : props.form.content ?? '', remark: fields.value.remark ? day.remark : props.form.remark ?? '' })
  }
  visible.value = false
  await recordAiSuggestionActionApi(draft.suggestionId, { actionCode: 'APPLY_FIELDS' })
}
async function ignore() {
  const draft = props.mode === 'week' ? weekDraft.value : dayDraft.value
  visible.value = false
  if (draft) await recordAiSuggestionActionApi(draft.suggestionId, { actionCode: 'IGNORE' })
}
watch(() => props.date, load)
onMounted(load)
</script>

<style scoped>
.plan-ai { display: grid; gap: 12px; padding: 18px; border: 1px solid var(--line); border-left: 4px solid #2d776c; border-radius: 8px; background: #fff; }
.plan-ai header { display: flex; align-items: center; justify-content: space-between; }
.plan-ai h2 { margin: 0; font-size: 17px; }
.plan-ai > p { margin: 0; color: var(--muted); font-size: 12px; }
.week-suggestions { display: grid; gap: 10px; }
.task-option { display: grid; grid-template-columns: 24px minmax(0, 1fr); gap: 10px; align-items: start; padding: 14px; border: 1px solid var(--line); border-radius: 8px; background: #fff; cursor: pointer; }
.task-option article { min-width: 0; }
.task-option__index { margin-bottom: 10px; color: #2d776c; font-size: 12px; font-weight: 700; }
.task-option dl { display: grid; grid-template-columns: 72px minmax(0, 1fr); gap: 8px 10px; margin: 0; }
.task-option dt { color: var(--muted); font-size: 12px; }
.task-option dd { margin: 0; overflow-wrap: anywhere; color: var(--ink); line-height: 1.5; }
.task-option dd.missing { color: var(--el-color-warning-dark-2); }
.day-fields { display: grid; gap: 14px; }
.day-fields .el-checkbox { height: auto; white-space: normal; }
.adjust { margin-top: 18px; }
</style>
