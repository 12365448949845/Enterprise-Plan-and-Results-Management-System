import { http } from '@/api/http'

export type MessageType = 'TODO' | 'NOTICE' | 'SYSTEM'

export interface UserMessage {
  id: string
  messageType: MessageType
  sceneCode: string
  title: string
  content: string
  status: 'UNREAD' | 'READ' | 'DONE'
  objectType: string
  objectId: string
  route: string
  menuPath: string
  dueAt?: string
  createdAt?: string
  readAt?: string
  overdue: boolean
}

export interface MessageSummary {
  unreadCount: number
  unreadTodoCount: number
  unreadNoticeCount: number
  menuBadges: Record<string, number>
  unreadMessages: UserMessage[]
}

export interface MessagePage {
  records: UserMessage[]
  total: number
  pageNo: number
  pageSize: number
  unreadCount: number
}

export function getMessageSummaryApi() {
  return http.get<unknown, MessageSummary>('/messages/summary')
}

export function listMessagesApi(params: {
  messageType?: MessageType | 'INFO'
  unreadOnly?: boolean
  pageNo?: number
  pageSize?: number
}) {
  return http.get<unknown, MessagePage>('/messages', { params })
}

export function markMessageReadApi(id: string) {
  return http.post<unknown, void>(`/messages/${id}/read`)
}

export function markAllMessagesReadApi() {
  return http.post<unknown, { updated: number }>('/messages/read-all')
}
