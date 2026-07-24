<template>
  <section class="no-access-page">
    <span class="no-access-page__code">403</span>
    <h1>当前账号未配置可访问工作台</h1>
    <p>请联系系统管理员，为当前账号所属角色配置至少一个工作台菜单权限。</p>
    <el-button type="primary" @click="logout">退出并重新登录</el-button>
  </section>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const authStore = useAuthStore()

async function logout() {
  await authStore.logout()
  await router.replace('/login')
}
</script>
