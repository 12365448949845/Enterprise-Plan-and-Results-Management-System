import { http } from './http'

export type PlanStatus = 'DRAFT' | 'PENDING' | 'APPROVED' | 'REJECTED' | 'PAUSED' | 'CANCELED'
export type ResultStatus = 'DRAFT' | 'PENDING' | 'CONFIRMED' | 'REJECTED'

export interface MonthPlan {
  id: number
  title: string
  planMonth: string
  content: string
  completionRate?: number
  versionNo?: string
  ownerUserId: number
  deptId?: number
  status: PlanStatus
  submitAt?: string
  approverId?: number
  approveAt?: string
  approvalComment?: string
  reviewStatus?: string
  riskLevel?: string
  reviewedAt?: string
}

export interface DayPlan {
  id: number
  title: string
  planDate: string
  content: string
  monthPlanId?: number
  ownerUserId: number
  deptId?: number
  status: PlanStatus
  submitAt?: string
  approverId?: number
  approveAt?: string
  approvalComment?: string
  departmentReviewComment?: string
  reviewStatus?: string
  riskLevel?: string
  reviewedAt?: string
}

export interface ResultItem {
  id: number
  title: string
  resultDate: string
  content: string
  planType: 'DAY' | 'MONTH' | 'TEMP'
  planId?: number
  temporary: boolean
  temporaryReason?: string
  ownerUserId: number
  deptId?: number
  status: ResultStatus
  submitAt?: string
  confirmerId?: number
  confirmAt?: string
  confirmComment?: string
  leaderSuggestion?: string
  completionRate?: number
  versionNo?: string
  evidenceStatus?: string
}

export interface PlanningStats {
  pendingDayPlans: number
  overduePendingDayPlans: number
  currentMonthResults: number
  closureRate: string
}

export function getPlanningStatsApi() {
  return http.get<unknown, PlanningStats>('/planning/stats')
}

export function listMonthPlansApi(params?: Record<string, unknown>) {
  return http.get<unknown, MonthPlan[]>('/planning/month-plans', { params })
}

export function listDayPlansApi(params?: Record<string, unknown>) {
  return http.get<unknown, DayPlan[]>('/planning/day-plans', { params })
}

export function listResultsApi(params?: Record<string, unknown>) {
  return http.get<unknown, ResultItem[]>('/planning/results', { params })
}
