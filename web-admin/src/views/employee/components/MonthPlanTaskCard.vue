<template>
  <article class="plan-task-card" :class="[`is-${item.taskType.toLowerCase()}`, { 'has-warning': item.status === 'rejected' }]">
    <header class="task-card-header">
      <div class="task-index">{{ String(index + 1).padStart(2, '0') }}</div>
      <div class="task-heading">
        <div class="task-title-row">
          <h3>{{ item.taskName || '未命名任务' }}</h3>
          <el-tag size="small" effect="plain" :type="item.taskType === 'EXTRA' ? 'warning' : 'info'">
            {{ item.taskType === 'EXTRA' ? '额外任务' : '常规任务' }}
          </el-tag>
        </div>
        <div class="task-meta">
          <span><b>{{ item.performanceWeight }}%</b> 绩效权重</span>
          <span>{{ item.deadline || '未填截止日期' }}</span>
          <el-tag size="small" :type="statusType">{{ statusLabel }}</el-tag>
        </div>
      </div>
      <div v-if="item.taskType === 'EXTRA'" class="task-actions">
        <el-button v-if="item.status === 'submitted'" link type="warning" @click="$emit('withdraw', item)">撤回</el-button>
        <el-button v-if="item.status === 'draft' || item.status === 'rejected'" link type="primary" @click="$emit('edit', item)">编辑</el-button>
        <el-button v-if="item.status === 'draft' || item.status === 'rejected'" link type="success" @click="$emit('resubmit', item)">重新提交</el-button>
      </div>
    </header>

    <div class="task-content-grid">
      <section>
        <span class="content-label">做什么</span>
        <strong>任务内容</strong>
        <p>{{ item.taskContent || '未填写任务内容' }}</p>
      </section>
      <section>
        <span class="content-label">产出什么</span>
        <strong>交付物</strong>
        <p>{{ item.deliverable || '未填写交付物' }}</p>
      </section>
    </div>

    <footer class="task-card-footer">
      <p v-if="item.progress"><span>当前进展</span>{{ item.progress }}</p>
      <p v-if="item.approvalComment" class="task-feedback"><span>审批意见</span>{{ item.approvalComment }}</p>
    </footer>
  </article>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { EmployeeMonthPlanItem } from '@/api/employee'

const props = defineProps<{ item: EmployeeMonthPlanItem; index: number }>()

defineEmits<{
  edit: [item: EmployeeMonthPlanItem]
  withdraw: [item: EmployeeMonthPlanItem]
  resubmit: [item: EmployeeMonthPlanItem]
}>()

const statusMap = {
  draft: { label: '草稿', type: 'info' }, submitted: { label: '待审批', type: 'warning' },
  approved: { label: '已通过', type: 'success' }, rejected: { label: '已驳回', type: 'danger' },
  paused: { label: '已暂停', type: 'warning' }, canceled: { label: '已撤销', type: 'danger' },
  confirmed: { label: '已确认', type: 'success' }, archived: { label: '已归档', type: 'info' },
} as const

const statusLabel = computed(() => statusMap[props.item.status]?.label || props.item.status)
const statusType = computed(() => statusMap[props.item.status]?.type || 'info')
</script>

<style scoped>
.plan-task-card { position:relative; overflow:hidden; border:1px solid var(--line); border-radius:14px; background:#fff; box-shadow:var(--shadow-soft); }
.plan-task-card::before { position:absolute; inset:0 auto 0 0; width:4px; background:#6e9cac; content:""; }
.plan-task-card.is-extra::before { background:#c88738; }
.plan-task-card.has-warning::before { background:#c65346; }
.task-card-header { display:grid; grid-template-columns:44px minmax(0,1fr) auto; gap:14px; align-items:start; padding:20px 22px 16px; border-bottom:1px solid #edf0eb; }
.task-index { color:#79908a; font:700 13px/1 "IBM Plex Mono",monospace; letter-spacing:.08em; }
.task-heading { min-width:0; }
.task-title-row,.task-meta,.task-actions { display:flex; flex-wrap:wrap; align-items:center; gap:9px; }
.task-title-row h3 { margin:0; color:var(--ink); font-size:17px; line-height:1.45; }
.task-meta { margin-top:8px; color:var(--muted); font-size:12px; }
.task-meta b { color:var(--blue); font-family:"IBM Plex Mono",monospace; }
.task-content-grid { display:grid; grid-template-columns:1.25fr .85fr; gap:0; padding:20px 22px; }
.task-content-grid section { min-width:0; padding:0 22px; border-left:1px solid #edf0eb; }
.task-content-grid section:first-child { padding-left:0; border-left:0; }
.content-label { display:block; margin-bottom:13px; color:var(--blue); font-size:10px; font-weight:800; letter-spacing:.12em; }
.task-content-grid strong { display:block; margin-top:10px; color:#52645f; font-size:11px; }
.task-content-grid p { margin:5px 0 0; color:#273c36; font-size:13px; line-height:1.75; white-space:pre-wrap; overflow-wrap:anywhere; }
.task-card-footer { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:18px; align-items:start; padding:14px 22px 16px; background:#f8faf7; }
.task-card-footer p span { display:block; margin-bottom:6px; color:#82918c; font-size:10px; font-weight:700; }
.task-card-footer p { margin:0; color:#53665f; font-size:12px; line-height:1.6; }
.task-feedback { color:#a9473d !important; }
@media (max-width:1350px) { .task-content-grid { grid-template-columns:1fr 1fr; } .task-content-grid section:last-child { grid-column:1/-1; margin-top:18px; padding:18px 0 0; border-top:1px solid #edf0eb; border-left:0; } .task-card-footer { grid-template-columns:1fr 1fr; } }
</style>
