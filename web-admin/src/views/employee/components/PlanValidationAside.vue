<template>
  <aside class="plan-checklist">
    <div class="plan-checklist__head">
      <div><span>提交前检查</span><strong>{{ blockingIssues.length ? `${blockingIssues.length} 项待处理` : '可以提交' }}</strong></div>
      <span class="plan-checklist__signal" :class="{ 'is-ready': !blockingIssues.length }"></span>
    </div>

    <dl v-if="summaryItems?.length" class="plan-checklist__summary">
      <div v-for="item in summaryItems" :key="item.label"><dt>{{ item.label }}</dt><dd>{{ item.value }}</dd></div>
    </dl>

    <div class="plan-save-state" :class="{ 'is-dirty': dirty, 'is-error': saveError }">
      <span></span>
      <div>
        <strong>{{ saveError ? '保存失败' : dirty ? '有未保存修改' : '当前内容已保存' }}</strong>
        <small>{{ saveError || (dirty ? '离开页面前请先保存草稿' : '提交前仍会再次核对') }}</small>
      </div>
    </div>

    <div class="plan-checklist__issues">
      <template v-if="issues.length">
        <button v-for="issue in issues" :key="issue.key" type="button" :class="{ 'is-advisory': !issue.blocking }" @click="$emit('locate', issue)">
          <span>{{ issue.blocking ? '!' : 'i' }}</span><b>{{ issue.label }}</b>
        </button>
      </template>
      <div v-else class="plan-checklist__ready"><span>✓</span><p><strong>内容完整</strong><small>保存后即可提交审批</small></p></div>
    </div>

    <div class="plan-checklist__actions">
      <el-button :disabled="!editable || saving || submitting" :loading="saving" @click="$emit('save')">保存草稿</el-button>
      <el-button type="primary" :disabled="!editable || !!blockingIssues.length || saving || submitting" :loading="submitting" @click="$emit('submit')">{{ submitLabel }}</el-button>
      <small v-if="blockingIssues.length">完成上方 {{ blockingIssues.length }} 项后可提交</small>
    </div>
  </aside>
</template>

<script setup lang="ts">
import { computed } from 'vue'

export interface PlanValidationIssue { key: string; label: string; targetId?: string; blocking: boolean }
export interface PlanSummaryItem { label: string; value: string | number }

const props = withDefaults(defineProps<{ issues: PlanValidationIssue[]; dirty: boolean; saving: boolean; submitting: boolean; editable: boolean; saveError?: string; summaryItems?: PlanSummaryItem[]; submitLabel?: string }>(), { saveError: '', summaryItems: () => [], submitLabel: '提交审批' })
defineEmits<{ locate: [issue: PlanValidationIssue]; save: []; submit: [] }>()
const blockingIssues = computed(() => props.issues.filter((issue) => issue.blocking))
</script>

<style scoped>
.plan-checklist { position: sticky; top: 86px; display: grid; gap: 16px; padding: 20px; border: 1px solid var(--line, #dce3df); border-radius: 12px; background: #fffdf8; box-shadow: 0 12px 34px rgb(35 57 49 / 8%); }
.plan-checklist__head { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.plan-checklist__head > div { display: grid; gap: 4px; }
.plan-checklist__head span { color: var(--muted, #687a74); font-size: 11px; letter-spacing: .08em; }
.plan-checklist__head strong { color: var(--ink, #203531); font-size: 16px; }
.plan-checklist__signal { width: 11px; height: 11px; border-radius: 50%; background: #c46043; box-shadow: 0 0 0 5px rgb(196 96 67 / 12%); }
.plan-checklist__signal.is-ready { background: var(--green, #2d776c); box-shadow: 0 0 0 5px rgb(45 119 108 / 12%); }
.plan-checklist__summary { display: grid; gap: 8px; margin: 0; padding: 13px 0; border-block: 1px solid var(--line, #e1e6e3); }
.plan-checklist__summary div { display: flex; justify-content: space-between; gap: 12px; }
.plan-checklist__summary dt { color: var(--muted, #687a74); font-size: 12px; }
.plan-checklist__summary dd { margin: 0; color: var(--ink, #203531); font-weight: 750; }
.plan-save-state { display: grid; grid-template-columns: 9px minmax(0, 1fr); gap: 10px; align-items: start; padding: 11px; border-radius: 8px; background: #f3f7f4; }
.plan-save-state > span { width: 8px; height: 8px; margin-top: 4px; border-radius: 50%; background: var(--green, #2d776c); }
.plan-save-state.is-dirty { background: #fff8e9; }.plan-save-state.is-dirty > span { background: #c28a2e; }
.plan-save-state.is-error { background: #fff1ed; }.plan-save-state.is-error > span { background: #b9573f; }
.plan-save-state div { display: grid; gap: 3px; }.plan-save-state strong { font-size: 12px; }.plan-save-state small { color: var(--muted, #687a74); line-height: 1.5; }
.plan-checklist__issues { display: grid; gap: 7px; }
.plan-checklist__issues button { display: grid; grid-template-columns: 22px minmax(0, 1fr); gap: 8px; align-items: center; width: 100%; padding: 9px; border: 1px solid #efd2c8; border-radius: 8px; color: #8d3e2f; background: #fff8f5; text-align: left; cursor: pointer; }
.plan-checklist__issues button:hover { border-color: #c76c53; }.plan-checklist__issues button.is-advisory { border-color: #d9e2dd; color: #536c64; background: #f6f9f7; }
.plan-checklist__issues button span { display: grid; width: 20px; height: 20px; place-items: center; border-radius: 50%; color: #fff; background: #b9573f; font-size: 11px; }.plan-checklist__issues button.is-advisory span { background: #718b82; }
.plan-checklist__issues button b { font-size: 12px; font-weight: 650; line-height: 1.45; }
.plan-checklist__ready { display: flex; gap: 10px; align-items: center; padding: 13px; border: 1px solid #cfe0d6; border-radius: 8px; background: #f3f9f5; }.plan-checklist__ready > span { color: var(--green, #2d776c); font-size: 18px; }.plan-checklist__ready p { display: grid; gap: 2px; margin: 0; }.plan-checklist__ready small { color: var(--muted, #687a74); }
.plan-checklist__actions { display: grid; gap: 9px; }.plan-checklist__actions .el-button { width: 100%; margin: 0; }.plan-checklist__actions small { color: #a24e39; text-align: center; line-height: 1.5; }
@media (max-width: 1180px) { .plan-checklist { position: static; } }
</style>
