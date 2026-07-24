<template>
  <section class="page-panel permission-page">
    <div class="page-header">
      <div>
        <span class="eyebrow">ACCESS GOVERNANCE / 01</span>
        <h1 class="page-title">用户权限</h1>
        <p class="page-subtitle">权限由组织、角色、菜单动作和数据范围共同决定；当前页面先展示治理边界，接口接入后再开放维护动作。</p>
      </div>
      <el-tag type="warning">功能接入中</el-tag>
    </div>

    <div class="permission-notice">
      <span class="permission-notice__mark">!</span>
      <div>
        <strong>前端隐藏不是权限控制</strong>
        <p>所有查看、修改、点评、审批、确认和导出动作都必须由服务端按角色与组织范围再次校验。</p>
      </div>
    </div>

    <div class="permission-grid">
      <article v-for="item in permissionDomains" :key="item.code" class="permission-domain">
        <span class="permission-domain__code">{{ item.code }}</span>
        <strong>{{ item.title }}</strong>
        <p>{{ item.description }}</p>
        <span class="permission-domain__status">待接入维护</span>
      </article>
    </div>

    <section class="section-card permission-boundary">
      <div class="section-header">
        <div>
          <h2>当前权限边界</h2>
          <p>系统会按真实账号加载工作空间，不在业务页面提供角色切换器。</p>
        </div>
      </div>
      <div class="permission-boundary__list">
        <div><span>可见范围</span><strong>由组织归属与授权范围决定</strong></div>
        <div><span>可执行动作</span><strong>由角色动作权限决定</strong></div>
        <div><span>关键变更</span><strong>写入审计日志并保留操作人</strong></div>
      </div>
    </section>
  </section>
</template>

<script setup lang="ts">
const permissionDomains = [
  { code: '01', title: '组织与部门', description: '维护部门、工作组和负责人归属，作为数据范围的基础。' },
  { code: '02', title: '人员账号', description: '管理员工状态、组织归属和登录安全信息。' },
  { code: '03', title: '角色与动作', description: '配置查看、编辑、点评、审批、确认和导出等动作。' },
  { code: '04', title: '菜单与入口', description: '按真实角色裁剪工作空间导航和页面入口。' },
  { code: '05', title: '审计记录', description: '追踪关键权限变更及计划、成果、审批操作。' },
]
</script>
