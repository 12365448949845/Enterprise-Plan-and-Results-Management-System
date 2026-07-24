<template>
  <main class="login-page">
    <div class="login-aside">
      <div class="login-brand"><span class="brand-mark">成</span><strong>成果计划</strong></div>
      <div class="login-statement">
        <span class="eyebrow">PLANNING PLATFORM</span>
        <h1>让每一项计划，<br /><em>都有结果可追踪。</em></h1>
        <p>从目标拆解到成果确认，建立清晰、可验证、可复盘的工作闭环。</p>
      </div>
      <div class="login-aside-footer"><span>计划</span><i></i><span>成果</span><i></i><span>审批</span><i></i><span>台账</span></div>
    </div>
    <section class="login-panel">
      <div class="login-panel__head">
        <span class="eyebrow">SIGN IN</span>
        <h2>欢迎回来</h2>
        <p>使用企业账号进入你的工作空间</p>
      </div>
      <el-form label-position="top" @submit.prevent>
        <el-form-item label="账号">
          <el-input v-model="form.username" size="large" autocomplete="username" placeholder="请输入账号" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="form.password" size="large" type="password" autocomplete="current-password" show-password placeholder="请输入密码" @keyup.enter="submit" />
        </el-form-item>
        <el-alert v-if="error" :title="error" type="error" :closable="false" />
        <el-button class="login-button" type="primary" size="large" :loading="loading" @click="submit">进入工作空间</el-button>
      </el-form>
      <div class="login-security"><span class="status-dot"></span>企业账号安全认证 · 操作全程留痕</div>
      <p class="login-tip">开发环境账号：`admin` / `Admin@123456`</p>
    </section>
  </main>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { defaultWorkspacePath, hasPermission } from '@/navigation/workspaces'
import { useAuthStore } from '@/stores/auth'
const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const loading = ref(false)
const error = ref('')
const form = reactive({ username: 'admin', password: 'Admin@123456' })
async function submit() {
  error.value = ''; loading.value = true
  try {
    await authStore.login(form.username, form.password)
    ElMessage.closeAll()
    ElMessage.success('登录成功')
    const permissions = authStore.user?.permissions || []
    const defaultPath = defaultWorkspacePath(permissions) || '/no-access'
    const redirect = authorizedRedirect(route.query.redirect, permissions)
    await router.replace(authStore.user?.forceChangePassword ? '/change-password' : (redirect || defaultPath))
  }
  catch (err) { error.value = err instanceof Error ? err.message : '登录失败，请检查账号或密码' }
  finally { loading.value = false }
}

function authorizedRedirect(value: unknown, permissions: readonly string[]) {
  if (typeof value !== 'string' || !value.startsWith('/')) return undefined
  const target = router.resolve(value)
  if (!target.matched.length || ['/', '/login', '/change-password', '/no-access'].includes(target.path)) return undefined
  const requiredPermission = typeof target.meta.permission === 'string' ? target.meta.permission : undefined
  return hasPermission(permissions, requiredPermission) ? target.fullPath : undefined
}
</script>
