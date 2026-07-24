<template>
  <router-view v-if="$route.meta.public || $route.meta.passwordChange" />
  <div v-else class="app-shell">
    <aside class="sidebar" :class="{ 'sidebar--open': sidebarOpen }">
      <div class="brand">
        <div class="brand-mark">成</div>
        <div class="brand-text">
          <strong>成果计划</strong>
          <span>工作闭环平台</span>
        </div>
        <button class="sidebar-close" type="button" aria-label="关闭导航" @click="sidebarOpen = false">×</button>
      </div>

      <div class="sidebar-caption">当前工作空间</div>
      <el-dropdown
        v-if="userWorkspaces.length > 1"
        class="workspace-dropdown"
        trigger="click"
        @command="switchWorkspace"
      >
        <button class="workspace-switcher workspace-switcher--button" type="button" aria-label="切换工作空间">
          <span class="workspace-dot"></span>
          <span class="workspace-switcher__copy">
            <strong>{{ activeWorkspace?.title || '未配置工作台' }}</strong>
            <small>{{ authStore.user?.realName || '当前用户' }}</small>
          </span>
          <el-icon class="workspace-chevron"><ArrowDown /></el-icon>
        </button>
        <template #dropdown>
          <el-dropdown-menu class="workspace-menu">
            <el-dropdown-item
              v-for="workspace in userWorkspaces"
              :key="workspace.id"
              :command="workspace.homePath"
              :disabled="workspace.id === activeWorkspace?.id"
            >
              <span class="workspace-menu__dot"></span>
              <span>{{ workspace.title }}</span>
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
      <div v-else class="workspace-switcher">
        <span class="workspace-dot"></span>
        <div class="workspace-switcher__copy">
          <strong>{{ activeWorkspace?.title || '未配置工作台' }}</strong>
          <small>{{ authStore.user?.realName || '当前用户' }}</small>
        </div>
      </div>

      <nav class="sidebar-nav" aria-label="主导航">
        <div v-if="activeWorkspace" class="nav-group">
          <div class="nav-title">{{ activeWorkspace.title }}</div>
          <router-link
            v-for="item in activeItems"
            :key="item.path"
            class="nav-link"
            :to="item.path"
            @click="sidebarOpen = false"
          >
            <span class="nav-icon">{{ item.icon }}</span><span>{{ item.label }}</span>
            <span v-if="menuBadge(item.path)" class="nav-badge">{{ badgeText(menuBadge(item.path)) }}</span>
          </router-link>
        </div>
      </nav>

      <div class="sidebar-footer">
        <div class="sidebar-footer__line"><span class="status-dot"></span>系统运行正常</div>
        <span>v0.1 · 2026</span>
      </div>
    </aside>

    <div v-if="sidebarOpen" class="sidebar-overlay" @click="sidebarOpen = false"></div>
    <main class="main">
      <header class="topbar">
        <div class="topbar-left">
          <button class="mobile-menu" type="button" aria-label="打开导航" @click="sidebarOpen = true">☰</button>
          <div class="breadcrumbs">
            <span>{{ pageScope }}</span><b>/</b><strong>{{ pageTitle }}</strong>
          </div>
        </div>
        <div class="topbar-actions">
          <div ref="notificationWrap" class="notification-wrap">
            <button
              class="topbar-icon"
              type="button"
              :aria-expanded="notificationOpen"
              aria-controls="notification-panel"
              :aria-label="messageStore.unreadCount ? `打开消息中心，${messageStore.unreadCount} 条未读消息` : '打开消息中心'"
              @click="toggleNotification"
            >
              <el-icon><Bell /></el-icon>
              <span v-if="messageStore.unreadCount" class="topbar-message-count">
                {{ badgeText(messageStore.unreadCount) }}
              </span>
            </button>
            <div v-if="notificationOpen" id="notification-panel" class="notification-panel" role="dialog" aria-label="消息中心">
              <div class="notification-panel__head">
                <div>
                  <strong>消息中心</strong>
                  <small>{{ messageStore.unreadCount ? `${messageStore.unreadCount} 条未读` : '没有新消息' }}</small>
                </div>
                <button v-if="messageStore.unreadCount" type="button" class="notification-read-all" @click="markAllRead">
                  全部已读
                </button>
              </div>
              <div class="notification-panel__list">
                <button
                  v-for="message in messageStore.unreadMessages"
                  :key="message.id"
                  class="notification-item"
                  type="button"
                  @click="openNotification(message)"
                >
                  <span class="notification-item__dot" :class="`is-${message.messageType.toLowerCase()}`"></span>
                  <span>
                    <span class="notification-item__title">
                      <strong>{{ message.title }}</strong>
                      <em v-if="message.overdue">逾期</em>
                    </span>
                    <small>{{ message.content || '进入对应页面查看详情' }}</small>
                    <time>{{ formatMessageTime(message.createdAt) }}</time>
                  </span>
                  <b>›</b>
                </button>
                <div v-if="messageStore.loading" class="notification-empty">正在加载消息…</div>
                <div v-else-if="!messageStore.unreadMessages.length" class="notification-empty">
                  <el-icon><Check /></el-icon>
                  <strong>消息已全部读完</strong>
                  <span>新的审批和处理结果会在这里提醒你。</span>
                </div>
              </div>
              <button type="button" class="notification-panel__footer" @click="openMessageCenter">
                查看全部消息<span>›</span>
              </button>
            </div>
          </div>
          <span class="topbar-divider"></span>
          <div class="user-chip">
            <span class="user-avatar">{{ (authStore.user?.realName || '用').slice(0, 1) }}</span>
            <span class="topbar-user">{{ authStore.user?.realName || '当前用户' }}</span>
          </div>
          <el-button class="logout-button" size="small" @click="logout">退出</el-button>
        </div>
      </header>
      <router-view />
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ArrowDown, Bell, Check } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import type { UserMessage } from '@/api/messages'
import {
  availableWorkspaces,
  visibleWorkspaceItems,
  workspaceForPath,
} from '@/navigation/workspaces'
import { useAuthStore } from '@/stores/auth'
import { useMessageStore } from '@/stores/messages'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const messageStore = useMessageStore()
const sidebarOpen = ref(false)
const notificationOpen = ref(false)
const notificationWrap = ref<HTMLElement>()

const routeTitles: Record<string, { title: string; scope: string }> = {
  '/no-access': { title: '暂无工作台权限', scope: '账号权限' },
  '/messages': { title: '消息中心', scope: '个人消息' },
  '/dashboard': { title: '管理工作台', scope: '系统管理端' },
  '/system/dashboard': { title: '管理工作台', scope: '系统管理端' },
  '/employee/dashboard': { title: '工作台', scope: '员工工作台' },
  '/employee/month-plans': { title: '月计划', scope: '员工工作台' },
  '/employee/week-plans': { title: '周计划', scope: '员工工作台' },
  '/employee/day-plans': { title: '日计划', scope: '员工工作台' },
  '/employee/results': { title: '成果记录', scope: '员工工作台' },
  '/employee/results/submit': { title: '成果提交', scope: '员工工作台' },
  '/employee/performance-evidence': { title: '绩效依据', scope: '员工工作台' },
  '/employee/appeals': { title: '申诉记录', scope: '员工工作台' },
  '/leader/workbench': { title: '工作台', scope: '直属领导端' },
  '/leader/daily-review': { title: '日计划点评', scope: '直属领导端' },
  '/leader/month-plan-approval': { title: '月计划审批', scope: '直属领导端' },
  '/leader/week-plan-approval': { title: '周计划审批', scope: '直属领导端' },
  '/leader/result-suggest': { title: '成果确认建议', scope: '直属领导端' },
  '/leader/plan-adjust': { title: '计划调整', scope: '直属领导端' },
  '/leader/team-ledger': { title: '下属台账', scope: '直属领导端' },
  '/leader/ai-month-context': { title: '本月计划要求', scope: '直属领导端' },
  '/department/dashboard': { title: '部门总览', scope: '部门负责人端' },
  '/department/plan-approval': { title: '月计划查看', scope: '部门负责人端' },
  '/department/result-confirm': { title: '成果最终确认', scope: '部门负责人端' },
  '/department/todo': { title: '通知待办', scope: '部门负责人端' },
  '/department/template': { title: '交付物模板', scope: '部门负责人端' },
  '/department/standard': { title: '验收标准', scope: '部门负责人端' },
  '/department/score-rule': { title: '参考分规则', scope: '部门负责人端' },
  '/department/department-ledger': { title: '部门台账', scope: '部门负责人端' },
  '/department/week-plan-ledger': { title: '周计划台账', scope: '部门负责人端' },
  '/department/export-tasks': { title: '导出任务', scope: '部门负责人端' },
  '/system/employee-register': { title: '员工注册', scope: '系统管理端' },
  '/system/employees': { title: '员工管理', scope: '系统管理端' },
  '/system/users': { title: '员工管理', scope: '系统管理端' },
  '/system/orgs': { title: '部门/项目组', scope: '系统管理端' },
  '/system/roles': { title: '角色管理', scope: '系统管理端' },
  '/system/permissions': { title: '权限管理', scope: '系统管理端' },
  '/system/workday-rules': { title: '工作日规则', scope: '系统管理端' },
  '/system/audits': { title: '审计日志', scope: '系统管理端' },
  '/system/ai': { title: 'AI 配置', scope: '系统管理端' },
}

routeTitles['/dispute/dashboard'] = { title: '裁决工作台', scope: '裁决工作台' }
routeTitles['/dispute/cases'] = { title: '争议案件', scope: '裁决工作台' }

const userPermissions = computed(() => authStore.user?.permissions || [])
const userWorkspaces = computed(() => availableWorkspaces(userPermissions.value))
const activeWorkspace = computed(() => workspaceForPath(route.path) || userWorkspaces.value[0])
const activeItems = computed(() => activeWorkspace.value
  ? visibleWorkspaceItems(activeWorkspace.value, userPermissions.value)
  : [])
const pageTitle = computed(() => routeTitles[route.path]?.title || '成果计划')
const pageScope = computed(() => routeTitles[route.path]?.scope || '业务系统')

watch(() => authStore.token, async (token) => {
  if (!token) {
    messageStore.reset()
    return
  }
  await messageStore.refresh().catch(() => undefined)
  messageStore.startPolling()
}, { immediate: true })

onMounted(() => {
  document.addEventListener('click', closeNotificationOnOutsideClick)
})

onBeforeUnmount(() => {
  document.removeEventListener('click', closeNotificationOnOutsideClick)
  messageStore.stopPolling()
})

async function logout() {
  await authStore.logout()
  messageStore.reset()
  notificationOpen.value = false
  ElMessage.closeAll()
  ElMessage.success('退出成功')
  await router.replace('/login')
}

async function switchWorkspace(command: string | number | object) {
  if (typeof command !== 'string') return
  sidebarOpen.value = false
  await router.push(command)
}

async function toggleNotification() {
  notificationOpen.value = !notificationOpen.value
  if (notificationOpen.value) await messageStore.refresh(true)
}

async function openNotification(message: UserMessage) {
  await messageStore.markRead(message)
  notificationOpen.value = false
  if (message.route) await router.push(message.route)
}

async function markAllRead() {
  const updated = await messageStore.markAllRead()
  ElMessage.success(updated ? `已将 ${updated} 条消息标为已读` : '当前没有未读消息')
}

async function openMessageCenter() {
  notificationOpen.value = false
  await router.push('/messages')
}

function menuBadge(path: string) {
  return messageStore.badgeFor(path)
}

function badgeText(count: number) {
  return count > 99 ? '99+' : String(count)
}

function formatMessageTime(value?: string) {
  if (!value) return '刚刚'
  return value.replace('T', ' ').slice(0, 16)
}

function closeNotificationOnOutsideClick(event: MouseEvent) {
  if (!notificationOpen.value || notificationWrap.value?.contains(event.target as Node)) return
  notificationOpen.value = false
}
</script>
