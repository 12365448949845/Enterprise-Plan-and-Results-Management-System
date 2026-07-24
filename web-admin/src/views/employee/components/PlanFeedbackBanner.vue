<template>
  <section v-if="comment" class="plan-feedback" :class="`is-${tone}`">
    <div class="plan-feedback__mark">{{ tone === 'danger' ? '!' : '✓' }}</div>
    <div>
      <div class="plan-feedback__head">
        <strong>{{ title || defaultTitle }}</strong>
        <el-tag :type="tone === 'danger' ? 'danger' : 'success'" effect="light">{{ statusLabel }}</el-tag>
      </div>
      <p>{{ comment }}</p>
      <span v-if="nextStep">下一步：{{ nextStep }}</span>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{ status: string; comment?: string; title?: string; nextStep?: string }>()
const rejected = computed(() => ['REJECTED', 'rejected'].includes(props.status))
const tone = computed(() => rejected.value ? 'danger' : 'success')
const defaultTitle = computed(() => rejected.value ? '计划需要修改' : '审批反馈')
const statusLabel = computed(() => rejected.value ? '已驳回' : '已反馈')
</script>

<style scoped>
.plan-feedback { display: grid; grid-template-columns: 34px minmax(0, 1fr); gap: 14px; padding: 17px 18px; border: 1px solid #cfe0d6; border-radius: 11px; background: #f3f9f5; }
.plan-feedback.is-danger { border-color: #ecc9be; background: #fff5f1; }
.plan-feedback__mark { display: grid; width: 30px; height: 30px; place-items: center; border-radius: 50%; color: #fff; background: var(--green, #2d776c); font-weight: 800; }
.is-danger .plan-feedback__mark { background: #b9573f; }
.plan-feedback__head { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.plan-feedback strong { color: var(--ink, #1f3430); font-size: 14px; }
.plan-feedback p { margin: 8px 0 5px; color: var(--ink, #1f3430); line-height: 1.7; white-space: pre-wrap; }
.plan-feedback span { color: var(--muted, #667873); font-size: 12px; }
</style>
