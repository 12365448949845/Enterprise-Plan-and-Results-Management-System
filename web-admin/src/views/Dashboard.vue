<template>
  <section class="page-panel">
    <div class="page-header">
      <div>
        <span class="eyebrow">SYSTEM CONTROL / 01</span>
        <h1 class="page-title">管理工作台</h1>
        <p class="page-subtitle">查看当前系统的计划执行、成果闭环和需要管理员关注的运行信号。</p>
      </div>
      <div class="toolbar">
        <span class="data-freshness">数据更新于 {{ lastUpdated }}</span>
        <el-button :loading="loading" @click="loadStats">刷新数据</el-button>
      </div>
    </div>
    <el-row :gutter="16">
      <el-col :span="6">
        <div class="metric primary">
          <span>待审日计划数</span>
          <strong>{{ stats.pendingDayPlans }}</strong>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="metric warning">
          <span>逾期未审数</span>
          <strong>{{ stats.overduePendingDayPlans }}</strong>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="metric success">
          <span>本月成果</span>
          <strong>{{ stats.currentMonthResults }}</strong>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="metric primary">
          <span>计划执行闭环率</span>
          <strong>{{ stats.closureRate }}</strong>
        </div>
      </el-col>
    </el-row>
    <el-alert
      class="mt16"
      title="当前版本已接入登录、JWT/Redis 会话、月计划、日计划、成果提交与审批确认接口。"
      type="success"
      :closable="false"
    />
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { getPlanningStatsApi, type PlanningStats } from '@/api/planning'

const loading = ref(false)
const stats = reactive<PlanningStats>({
  pendingDayPlans: 0,
  overduePendingDayPlans: 0,
  currentMonthResults: 0,
  closureRate: '--',
})
const lastUpdatedAt = ref(new Date())
const lastUpdated = computed(() => new Intl.DateTimeFormat('zh-CN', {
  hour: '2-digit',
  minute: '2-digit',
}).format(lastUpdatedAt.value))

async function loadStats() {
  loading.value = true
  try {
    Object.assign(stats, await getPlanningStatsApi())
    lastUpdatedAt.value = new Date()
  } finally {
    loading.value = false
  }
}

onMounted(loadStats)
</script>
