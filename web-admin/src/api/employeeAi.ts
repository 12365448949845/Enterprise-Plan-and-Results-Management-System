import { http } from './http'

export type AiWorkType = 'TASK' | 'METRIC' | 'DOCUMENT' | 'PROJECT' | 'COMMUNICATION' | 'ROUTINE' | 'UNKNOWN'

export interface AiPlanItem {
  workType?: AiWorkType
  taskName: string
  taskContent: string
  deliverable: string
  deadline: string
  performanceWeight: number
}

export interface AiPlanForm {
  summary: string
  items: AiPlanItem[]
}

export interface AiContextResponse {
  enabled: boolean
  providerCode: string
  modelName: string
  availableContext: string[]
  missingContext: string[]
  historyPlanCount?: number
  parentOptions?: PlanAiParentOption[]
  relatedWeekItems?: WeekPlanAiItem[]
  remainingCalls: Record<string, number>
  notice: string
}

export interface PlanAiParentOption { id: number; planMonth: string; taskName: string; taskContent: string; deliverable: string }
export interface WeekPlanAiItem { monthPlanItemId: number; content: string; deliverable: string; plannedFinishDate: string }
export interface WeekPlanAiDraft { suggestionId: string; items: WeekPlanAiItem[]; warnings: string[]; missingContext: string[]; notice: string }
export interface DayPlanAiForm { relatedMonthPlanItemId: number | null; content: string; remark: string }
export interface DayPlanAiDraft extends DayPlanAiForm { suggestionId: string; warnings: string[]; missingContext: string[]; notice: string }

export const getWeekPlanAiContextApi = (weekStart: string) => http.get<unknown, AiContextResponse>('/employee/ai/week-plans/context', { params: { weekStart } })
export const generateWeekPlanAiApi = (payload: { requestId: string; weekStart: string; intentText: string; currentForm: { items: WeekPlanAiItem[] } }) => http.post<typeof payload, WeekPlanAiDraft>('/employee/ai/week-plans/generate', payload, { timeout: 35000 })
export const adjustWeekPlanAiApi = (payload: { requestId: string; weekStart: string; draft: { items: WeekPlanAiItem[] }; instruction: string; targetItemIndex?: number }) => http.post<typeof payload, WeekPlanAiDraft>('/employee/ai/week-plans/adjust', payload, { timeout: 35000 })
export const getDayPlanAiContextApi = (planDate: string) => http.get<unknown, AiContextResponse>('/employee/ai/day-plans/context', { params: { planDate } })
export const generateDayPlanAiApi = (payload: { requestId: string; planDate: string; intentText: string; currentForm: DayPlanAiForm }) => http.post<typeof payload, DayPlanAiDraft>('/employee/ai/day-plans/generate', payload, { timeout: 35000 })
export const adjustDayPlanAiApi = (payload: { requestId: string; planDate: string; draft: DayPlanAiForm; instruction: string }) => http.post<typeof payload, DayPlanAiDraft>('/employee/ai/day-plans/adjust', payload, { timeout: 35000 })

export interface AiGenerateResponse {
  suggestionId: string
  summary: string
  items: AiPlanItem[]
  warnings: string[]
  missingContext: string[]
  notice: string
}

export interface AiOptimizeResponse {
  suggestionId: string
  item: AiPlanItem
  warnings: string[]
  notice: string
}

export interface AiCheckIssue {
  code: string
  level: 'INFO' | 'WARNING' | 'HIGH'
  fieldPath: string
  message: string
  suggestion: string
}

export interface AiCheckResponse {
  suggestionId: string
  issues: AiCheckIssue[]
  notice: string
}

export function getMonthPlanAiContextApi(planMonth: string) {
  return http.get<unknown, AiContextResponse>('/employee/ai/month-plans/context', { params: { planMonth } })
}

export function generateMonthPlanApi(payload: {
  requestId: string
  planMonth: string
  intentText: string
  currentForm: AiPlanForm
  jobDescription?: string
}) {
  return http.post<typeof payload, AiGenerateResponse>('/employee/ai/month-plans/generate', payload, { timeout: 35000 })
}

export function optimizeMonthPlanItemApi(payload: {
  requestId: string
  planMonth: string
  summary: string
  item: AiPlanItem
  instruction?: string
  jobDescription?: string
}) {
  return http.post<typeof payload, AiOptimizeResponse>('/employee/ai/month-plans/items/optimize', payload, { timeout: 35000 })
}

export function checkMonthPlanApi(payload: {
  requestId: string
  planMonth: string
  currentForm: AiPlanForm
  jobDescription?: string
}) {
  return http.post<typeof payload, AiCheckResponse>('/employee/ai/month-plans/check', payload, { timeout: 35000 })
}

export function recordAiSuggestionActionApi(suggestionId: string, payload: {
  actionCode: 'PREVIEW' | 'APPLY_ALL' | 'APPLY_ITEM' | 'APPLY_FIELDS' | 'ADOPT_WITH_EDIT' | 'IGNORE'
  appliedFields?: string[]
  beforeHash?: string
  afterHash?: string
}) {
  return http.post<typeof payload, void>(`/employee/ai/suggestions/${suggestionId}/actions`, payload)
}

export function aiRequestId() {
  return globalThis.crypto?.randomUUID?.() ?? `ai-${Date.now()}-${Math.random().toString(16).slice(2)}`
}
