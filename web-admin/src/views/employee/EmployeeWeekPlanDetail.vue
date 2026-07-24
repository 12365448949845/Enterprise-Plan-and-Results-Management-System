<template>
  <section v-loading="loading" class="page-panel week-plan-detail">
    <div class="page-header">
      <div><span class="eyebrow">WEEKLY PLAN / DETAIL</span><h1 class="page-title">周计划详情</h1><p class="page-subtitle">按任务查看月—周拆解关系、交付安排和审批反馈。</p></div>
      <div class="toolbar"><el-button @click="router.push('/employee/week-plans')">返回列表</el-button><el-button v-if="editable" type="primary" @click="router.push(`/employee/week-plans/${id}/edit`)">继续编辑</el-button></div>
    </div>
    <el-alert v-if="errorMessage" class="dashboard-alert" type="warning" :closable="false" show-icon :title="errorMessage" />
    <template v-if="detail">
      <section class="week-detail-hero mt16">
        <div><span>自然周</span><strong>{{ detail.summary.weekStart }} — {{ detail.summary.weekEnd }}</strong><p>{{ detail.summary.title }}</p></div>
        <dl><div><dt>任务</dt><dd>{{ detail.items.length }}</dd></div><div><dt>下级日计划</dt><dd>{{ detail.dayPlanCount }}</dd></div></dl>
        <el-tag :type="weekPlanStatusMeta[detail.summary.status].type" size="large">{{ weekPlanStatusMeta[detail.summary.status].label }}</el-tag>
      </section>
      <PlanFeedbackBanner class="mt16" :status="detail.summary.status" :comment="detail.summary.approvalComment" :next-step="editable ? '根据意见修改计划并重新提交' : undefined" />
      <section class="week-detail-stage mt16">
        <div class="section-header"><div><span class="eyebrow">TASKS</span><h2>计划任务</h2><p>核心信息完整展开，无需横向滚动或悬停查看。</p></div></div>
        <div class="week-detail-list">
          <article v-for="(item, index) in detail.items" :key="item.id" class="week-detail-task">
            <div class="week-detail-task__head"><span>{{ String(index + 1).padStart(2, '0') }}</span><div><strong>{{ item.content }}</strong><small>{{ item.parent?.planMonth }} · {{ item.parent?.taskName }}</small></div><el-tag effect="plain">{{ item.parent?.taskType === 'EXTRA' ? '额外任务' : '常规任务' }}</el-tag></div>
            <div class="week-detail-task__grid"><div><span>本周做什么</span><p>{{ item.content }}</p></div><div><span>交付什么</span><p>{{ item.deliverable || '未填写交付物' }}</p></div><div><span>何时完成</span><p>{{ item.plannedFinishDate || '未指定日期' }}</p></div></div>
            <footer><span>月计划权重 {{ item.parent?.performanceWeight ?? 0 }}%</span><span>排序 {{ item.sortNo }}</span></footer>
          </article>
        </div>
      </section>
    </template>
  </section>
</template>
<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getEmployeeWeekPlanApi, weekPlanStatusMeta, type WeekPlanDetail } from '@/api/weekPlan'
import PlanFeedbackBanner from './components/PlanFeedbackBanner.vue'
const route = useRoute(); const router = useRouter(); const id = Number(route.params.id); const loading = ref(false); const errorMessage = ref(''); const detail = ref<WeekPlanDetail | null>(null)
const editable = computed(() => detail.value?.summary.status === 'DRAFT' || detail.value?.summary.status === 'REJECTED')
onMounted(async () => { loading.value = true; try { detail.value = await getEmployeeWeekPlanApi(id) } catch (error) { errorMessage.value = error instanceof Error ? error.message : '详情加载失败' } finally { loading.value = false } })
</script>
<style scoped>
.week-detail-hero { display: grid; grid-template-columns: minmax(0, 1fr) auto auto; gap: 24px; align-items: center; padding: 22px; border: 1px solid #bfd4c9; border-radius: 13px; background: linear-gradient(110deg, #eef7f2, #fffdf8 70%); }.week-detail-hero > div { display: grid; gap: 5px; }.week-detail-hero > div span { color: var(--blue); font-size: 10px; font-weight: 800; letter-spacing: .1em; }.week-detail-hero > div strong { font-size: 22px; }.week-detail-hero p { margin: 0; color: var(--muted); }.week-detail-hero dl { display: flex; margin: 0; }.week-detail-hero dl div { min-width: 90px; padding: 0 18px; border-left: 1px solid #cddbd4; }.week-detail-hero dt { color: var(--muted); font-size: 11px; }.week-detail-hero dd { margin: 3px 0 0; font-size: 22px; font-weight: 800; }
.week-detail-stage { padding: 20px; border: 1px solid var(--line); border-radius: 12px; background: #fffdf8; }.week-detail-list { display: grid; gap: 12px; margin-top: 16px; }.week-detail-task { overflow: hidden; border: 1px solid var(--line); border-radius: 11px; background: #fff; }.week-detail-task__head { display: grid; grid-template-columns: 38px minmax(0, 1fr) auto; gap: 12px; align-items: center; padding: 14px 16px; background: #f5f8f5; }.week-detail-task__head > span { display: grid; width: 36px; height: 36px; place-items: center; border-radius: 9px 9px 9px 3px; color: #fff; background: var(--blue); font-family: "IBM Plex Mono", monospace; }.week-detail-task__head > div { display: grid; gap: 3px; min-width: 0; }.week-detail-task__head strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }.week-detail-task__head small { color: var(--muted); }.week-detail-task__grid { display: grid; grid-template-columns: 1.4fr 1fr .6fr; gap: 1px; background: var(--line); }.week-detail-task__grid > div { padding: 16px; background: #fff; }.week-detail-task__grid span { color: var(--blue); font-size: 10px; font-weight: 800; letter-spacing: .06em; }.week-detail-task__grid p { margin: 8px 0 0; line-height: 1.65; white-space: pre-wrap; }.week-detail-task footer { display: flex; gap: 18px; padding: 10px 16px; color: var(--muted); background: #fafbf9; font-size: 11px; }
@media (max-width: 900px) { .week-detail-hero, .week-detail-task__grid { grid-template-columns: 1fr; }.week-detail-hero dl div:first-child { border-left: 0; padding-left: 0; } }
</style>
