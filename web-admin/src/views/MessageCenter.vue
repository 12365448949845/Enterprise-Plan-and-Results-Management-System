<template>
  <section class="page-panel message-center-page">
    <div class="page-header">
      <div>
        <h1 class="page-title">消息中心</h1>
        <p class="page-subtitle">集中查看当前账号的审批待办、处理结果和系统通知。</p>
      </div>
      <div class="toolbar">
        <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
        <el-button type="primary" :icon="Check" :disabled="messageStore.unreadCount === 0" @click="markAllRead">
          全部已读
        </el-button>
      </div>
    </div>

    <div class="message-summary-strip" aria-label="消息统计">
      <button type="button" :class="{ active: filter === 'ALL' }" @click="setFilter('ALL')">
        <span>全部消息</span><strong>{{ total }}</strong>
      </button>
      <button type="button" :class="{ active: filter === 'UNREAD' }" @click="setFilter('UNREAD')">
        <span>未读消息</span><strong>{{ messageStore.unreadCount }}</strong>
      </button>
      <button type="button" :class="{ active: filter === 'TODO' }" @click="setFilter('TODO')">
        <span>待办提醒</span><strong>{{ messageStore.summary.unreadTodoCount }}</strong>
      </button>
      <button type="button" :class="{ active: filter === 'NOTICE' }" @click="setFilter('NOTICE')">
        <span>结果与系统通知</span><strong>{{ messageStore.summary.unreadNoticeCount }}</strong>
      </button>
    </div>

    <div v-loading="loading" class="message-list">
      <article
        v-for="message in messages"
        :key="message.id"
        class="message-row"
        :class="{ 'is-unread': message.status === 'UNREAD', 'is-overdue': message.overdue }"
      >
        <span class="message-row__indicator" aria-hidden="true"></span>
        <div class="message-row__content">
          <div class="message-row__title">
            <strong>{{ message.title }}</strong>
            <span class="message-kind">{{ typeLabel(message.messageType) }}</span>
            <span v-if="message.overdue" class="message-overdue">已逾期</span>
          </div>
          <p>{{ message.content || '请进入对应业务页面查看详情。' }}</p>
          <div class="message-row__meta">
            <span>{{ formatDateTime(message.createdAt) }}</span>
            <span v-if="message.dueAt">截止 {{ formatDateTime(message.dueAt) }}</span>
          </div>
        </div>
        <div class="message-row__actions">
          <el-button v-if="message.status === 'UNREAD'" link @click="markRead(message)">标为已读</el-button>
          <el-button v-if="message.route" link type="primary" @click="openMessage(message)">查看</el-button>
        </div>
      </article>

      <div v-if="!loading && !messages.length" class="message-center-empty">
        <el-icon><Bell /></el-icon>
        <strong>当前筛选下没有消息</strong>
        <span>新的审批、处理结果或系统提醒会显示在这里。</span>
      </div>
    </div>

    <el-pagination
      v-if="total > pageSize"
      class="message-pagination"
      background
      layout="prev, pager, next"
      :current-page="pageNo"
      :page-size="pageSize"
      :total="total"
      @current-change="changePage"
    />
  </section>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { Bell, Check, Refresh } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { listMessagesApi, markMessageReadApi, type MessageType, type UserMessage } from '@/api/messages'
import { useMessageStore } from '@/stores/messages'

type Filter = 'ALL' | 'UNREAD' | 'TODO' | 'NOTICE'

const router = useRouter()
const messageStore = useMessageStore()
const loading = ref(false)
const messages = ref<UserMessage[]>([])
const filter = ref<Filter>('ALL')
const total = ref(0)
const pageNo = ref(1)
const pageSize = 20

onMounted(async () => {
  await Promise.all([messageStore.refresh(true), load()])
})

async function load() {
  loading.value = true
  try {
    const params: { messageType?: MessageType | 'INFO'; unreadOnly?: boolean; pageNo: number; pageSize: number } = {
      pageNo: pageNo.value,
      pageSize,
    }
    if (filter.value === 'UNREAD') params.unreadOnly = true
    if (filter.value === 'TODO') params.messageType = 'TODO'
    if (filter.value === 'NOTICE') params.messageType = 'INFO'
    const data = await listMessagesApi(params)
    messages.value = data.records
    total.value = data.total
  } finally {
    loading.value = false
  }
}

async function setFilter(value: Filter) {
  filter.value = value
  pageNo.value = 1
  await load()
}

async function changePage(value: number) {
  pageNo.value = value
  await load()
}

async function markRead(message: UserMessage) {
  await markMessageReadApi(message.id)
  message.status = 'READ'
  await messageStore.refresh(true)
  ElMessage.success('消息已标为已读')
}

async function markAllRead() {
  const updated = await messageStore.markAllRead()
  await load()
  ElMessage.success(updated ? `已将 ${updated} 条消息标为已读` : '当前没有未读消息')
}

async function openMessage(message: UserMessage) {
  if (message.status === 'UNREAD') await markMessageReadApi(message.id)
  await messageStore.refresh(true)
  await router.push(message.route)
}

function typeLabel(type: MessageType) {
  return type === 'TODO' ? '待办' : type === 'SYSTEM' ? '系统' : '通知'
}

function formatDateTime(value?: string) {
  if (!value) return '刚刚'
  return value.replace('T', ' ').slice(0, 16)
}
</script>
