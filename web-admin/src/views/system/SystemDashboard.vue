<template>
  <section class="page-panel system-dashboard" v-loading="loading">
    <div class="page-header">
      <div>
        <span class="eyebrow">SYSTEM ADMINISTRATION</span>
        <h1 class="page-title">系统管理工作台</h1>
        <p class="page-subtitle">检查组织、员工、角色和权限配置是否完整，并快速进入对应维护页面。</p>
      </div>
      <el-button @click="load">刷新数据</el-button>
    </div>

    <div class="metric-grid system-metric-grid">
      <article class="metric primary"><span>组织节点</span><strong>{{ data.departmentCount }}</strong><em>部门、小组与项目组</em></article>
      <article class="metric success"><span>启用员工</span><strong>{{ data.employeeCount }}</strong><em>可登录账号</em></article>
      <article class="metric"><span>启用角色</span><strong>{{ data.roleCount }}</strong><em>当前权限集合</em></article>
      <article class="metric warning"><span>近90天审计</span><strong>{{ data.auditCount }}</strong><em>关键操作记录</em></article>
    </div>

    <div class="system-dashboard-grid">
      <section class="section-card">
        <div class="section-header">
          <div><h2>配置风险</h2><p>风险项可直接进入对应管理页面处理。</p></div>
        </div>
        <div class="risk-list">
          <button v-for="risk in data.risks" :key="risk.code" type="button" class="risk-row" @click="router.push(risk.route)">
            <span :class="['risk-indicator', `is-${risk.level}`]"></span>
            <span><strong>{{ risk.title }}</strong><small>{{ risk.count ? '存在待处理项' : '当前配置正常' }}</small></span>
            <b>{{ risk.count }}</b><em>查看 ›</em>
          </button>
        </div>
      </section>

      <section class="section-card">
        <div class="section-header">
          <div><h2>常用管理</h2><p>保持原型的直接入口，不在工作台执行批量变更。</p></div>
        </div>
        <div class="admin-entry-list">
          <button v-for="item in entries" :key="item.path" type="button" @click="router.push(item.path)">
            <span>{{ item.label }}</span><small>{{ item.desc }}</small><b>›</b>
          </button>
        </div>
      </section>
    </div>

    <section class="section-card mt16">
      <div class="section-header">
        <div><h2>最近审计记录</h2><p>系统管理、计划、成果和审批动作统一留痕。</p></div>
        <el-button text type="primary" @click="router.push('/system/audits')">查看全部</el-button>
      </div>
      <el-table :data="data.recentAudits" empty-text="暂无审计记录">
        <el-table-column prop="createdAt" label="时间" width="180" />
        <el-table-column prop="username" label="操作人" width="140" />
        <el-table-column prop="action" label="动作" min-width="210" />
        <el-table-column prop="targetType" label="对象" width="150" />
        <el-table-column label="结果" width="100">
          <template #default="{ row }"><el-tag :type="row.result === 'SUCCESS' ? 'success' : 'danger'">{{ row.result === 'SUCCESS' ? '成功' : '失败' }}</el-tag></template>
        </el-table-column>
      </el-table>
    </section>
  </section>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { getSystemDashboard, type DashboardData } from '@/api/system'

const router = useRouter()
const loading = ref(false)
const data = reactive<DashboardData>({ departmentCount: 0, employeeCount: 0, roleCount: 0, auditCount: 0, risks: [], recentAudits: [] })
const entries = [
  { label: '员工注册', desc: '直接创建员工账号', path: '/system/employee-register' },
  { label: '员工管理', desc: '维护组织、负责人和状态', path: '/system/employees' },
  { label: '部门/项目组', desc: '维护组织树和负责人', path: '/system/orgs' },
  { label: '角色与权限', desc: '配置角色动作边界', path: '/system/roles' },
  { label: '工作日规则', desc: '维护日期填报口径', path: '/system/workday-rules' },
  { label: '审计日志', desc: '查询关键操作留痕', path: '/system/audits' },
]

async function load() {
  loading.value = true
  try { Object.assign(data, await getSystemDashboard()) }
  catch (error) { ElMessage.error(error instanceof Error ? error.message : '系统管理数据加载失败') }
  finally { loading.value = false }
}
onMounted(load)
</script>
