import { AI_OPERATION_TIMEOUT_MS, http } from './http'

export type WeekPlanStatus = 'DRAFT' | 'PENDING' | 'APPROVED' | 'REJECTED'

export interface WeekPlanParentOption {
  monthPlanItemId: number
  monthPlanId: number
  monthPlanTitle: string
  planMonth: string
  taskType: 'REGULAR' | 'EXTRA'
  performanceWeight: number
  taskName: string
  deadline?: string
  status: string
  existingWeekPlanCount: number
}

export interface WeekPlanItem {
  id: number
  monthPlanItemId: number
  content: string
  deliverable?: string
  plannedFinishDate?: string
  sortNo: number
  parent?: WeekPlanParentOption
}

export interface WeekPlanSummary {
  id: number
  title: string
  weekStart: string
  weekEnd: string
  status: WeekPlanStatus
  versionNo: number
  ownerUserId: number
  employeeName: string
  deptId?: number
  departmentName: string
  itemCount: number
  submitAt?: string
  approveAt?: string
  approvalComment?: string
}

export interface WeekPlanDetail {
  summary: WeekPlanSummary
  items: WeekPlanItem[]
  siblingPlans: WeekPlanSummary[]
  dayPlanCount: number
}

export interface WeekPlanSavePayload {
  weekStart: string
  versionNo?: number
  items: Array<{
    monthPlanItemId: number | null
    content: string
    deliverable?: string
    plannedFinishDate?: string
  }>
}

export interface WeekPlanAction {
  id: number
  status: WeekPlanStatus
  versionNo: number
  message: string
}

export interface WeekPlanListQuery {
  status?: WeekPlanStatus | ''
  weekStart?: string
  deptId?: number
}

export function listEmployeeWeekPlansApi(params?: WeekPlanListQuery) {
  return http.get<unknown, WeekPlanSummary[]>('/employee/week-plans', { params })
}

export function getEmployeeWeekPlanApi(id: number) {
  return http.get<unknown, WeekPlanDetail>(`/employee/week-plans/${id}`)
}

export function listWeekPlanParentOptionsApi() {
  return http.get<unknown, WeekPlanParentOption[]>('/employee/week-plans/parent-options')
}

export function createEmployeeWeekPlanApi(payload: WeekPlanSavePayload) {
  return http.post<WeekPlanSavePayload, WeekPlanDetail>('/employee/week-plans', payload)
}

export function updateEmployeeWeekPlanApi(id: number, payload: WeekPlanSavePayload) {
  return http.put<WeekPlanSavePayload, WeekPlanDetail>(`/employee/week-plans/${id}`, payload)
}

export function submitEmployeeWeekPlanApi(id: number, versionNo: number) {
  return http.post<undefined, WeekPlanAction>(`/employee/week-plans/${id}/submit`, undefined, {
    params: { versionNo },
    timeout: AI_OPERATION_TIMEOUT_MS,
  })
}

export function withdrawEmployeeWeekPlanApi(id: number, versionNo: number) {
  return http.post<undefined, WeekPlanAction>(`/employee/week-plans/${id}/withdraw`, undefined, { params: { versionNo } })
}

export function deleteEmployeeWeekPlanApi(id: number, versionNo: number) {
  return http.delete<unknown, WeekPlanAction>(`/employee/week-plans/${id}`, { params: { versionNo } })
}

export function listLeaderWeekPlansApi(params?: WeekPlanListQuery) {
  return http.get<unknown, WeekPlanSummary[]>('/leader/week-plan-approvals', { params })
}

export function getLeaderWeekPlanApi(id: number) {
  return http.get<unknown, WeekPlanDetail>(`/leader/week-plan-approvals/${id}`)
}

export function approveLeaderWeekPlanApi(id: number, versionNo: number, comment: string) {
  return http.post<unknown, WeekPlanAction>(`/leader/week-plan-approvals/${id}/approve`, { versionNo, comment })
}

export function rejectLeaderWeekPlanApi(id: number, versionNo: number, comment: string) {
  return http.post<unknown, WeekPlanAction>(`/leader/week-plan-approvals/${id}/reject`, { versionNo, comment })
}

export function listDepartmentWeekPlansApi(params?: WeekPlanListQuery) {
  return http.get<unknown, WeekPlanSummary[]>('/department/week-plans', { params })
}

export function getDepartmentWeekPlanApi(id: number) {
  return http.get<unknown, WeekPlanDetail>(`/department/week-plans/${id}`)
}

export const weekPlanStatusMeta: Record<WeekPlanStatus, { label: string; type: 'info' | 'warning' | 'success' | 'danger' }> = {
  DRAFT: { label: '草稿', type: 'info' },
  PENDING: { label: '待审批', type: 'warning' },
  APPROVED: { label: '已通过', type: 'success' },
  REJECTED: { label: '已驳回', type: 'danger' },
}
