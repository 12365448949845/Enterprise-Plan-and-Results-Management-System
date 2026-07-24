import { defineStore } from 'pinia'
import {
  getMessageSummaryApi,
  markAllMessagesReadApi,
  markMessageReadApi,
  type MessageSummary,
  type UserMessage,
} from '@/api/messages'

const emptySummary = (): MessageSummary => ({
  unreadCount: 0,
  unreadTodoCount: 0,
  unreadNoticeCount: 0,
  menuBadges: {},
  unreadMessages: [],
})

let pollingTimer: number | undefined

export const useMessageStore = defineStore('messages', {
  state: () => ({
    summary: emptySummary(),
    loading: false,
    initialized: false,
  }),
  getters: {
    unreadCount: (state) => state.summary.unreadCount,
    unreadMessages: (state) => state.summary.unreadMessages,
  },
  actions: {
    async refresh(_silent = false) {
      if (this.loading) return
      this.loading = true
      try {
        this.summary = await getMessageSummaryApi()
        this.initialized = true
      } finally {
        this.loading = false
      }
    },
    badgeFor(path: string) {
      return this.summary.menuBadges[path] || 0
    },
    async markRead(message: UserMessage) {
      if (message.status === 'UNREAD') await markMessageReadApi(message.id)
      await this.refresh(true)
    },
    async markAllRead() {
      const result = await markAllMessagesReadApi()
      await this.refresh(true)
      return result.updated
    },
    startPolling() {
      if (pollingTimer) return
      pollingTimer = window.setInterval(() => {
        void this.refresh(true).catch(() => undefined)
      }, 30_000)
    },
    stopPolling() {
      if (!pollingTimer) return
      window.clearInterval(pollingTimer)
      pollingTimer = undefined
    },
    reset() {
      this.summary = emptySummary()
      this.initialized = false
      this.stopPolling()
    },
  },
})
