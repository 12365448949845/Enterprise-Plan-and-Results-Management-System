import { AI_OPERATION_TIMEOUT_MS, http } from './http'

export type EmployeePlanStatus = 'draft' | 'submitted' | 'approved' | 'rejected' | 'confirmed' | 'paused' | 'canceled' | 'archived'
export type EmployeeResultStatus = 'not_submitted' | 'draft' | 'submitted' | 'confirmed' | 'rejected'
export type EmployeeAppealStatus = 'draft' | 'submitted' | 'processing' | 'resolved' | 'closed'
export type EmployeeEvidencePeriodType = 'day' | 'week' | 'month' | 'quarter' | 'year'
export type EmployeeEvidenceSourceType = 'day_plan' | 'month_plan' | 'result' | 'appeal'

export interface EmployeeMonthPlan {
  id: number
  planMonth: string
  title: string
  planStatus: EmployeePlanStatus
  resultStatus: EmployeeResultStatus
  completionRate: number
  updatedAt: string
}

export interface EmployeeDayPlanCalendarItem {
  date: string
  status: EmployeePlanStatus
}

export interface EmployeeWorkdayCalendarItem {
  date: string
  ruleType: 'WORKDAY' | 'WEEKEND' | 'HOLIDAY' | 'LEAVE' | 'BUSINESS_TRIP' | 'SPECIAL_SHIFT'
  forceReport: boolean
  description?: string
  ruleId?: number
  versionNo?: number
  explicit: boolean
  missingRequired: boolean
}

export interface EmployeeDashboardResp {
  currentMonth: string
  orgName: string
  monthPlans: EmployeeMonthPlan[]
  dayPlanCalendar: EmployeeDayPlanCalendarItem[]
  workdayCalendar: EmployeeWorkdayCalendarItem[]
  summary: {
    monthPlanCount: number
    submittedResultCount: number
    averageCompletionRate: number
    openAppealCount: number
    missingRequiredDayPlanCount: number
  }
}

export interface EmployeeMonthPlanItem {
  id: number
  taskType: 'REGULAR' | 'EXTRA'
  performanceWeight: number
  taskName: string
  taskContent: string
  progress: string
  deliverable: string
  deadline?: string
  status: EmployeePlanStatus
  sortNo: number
  submittedAt?: string
  approvedAt?: string
  approvalComment?: string
}

export interface EmployeeDeliverable {
  id: number
  name: string
  fileType: string
  relatedTaskName: string
  submittedAt: string
  fileUrl: string
}

export interface EmployeeResultSummary {
  submittedCount: number
  confirmedCount: number
  rejectedCount: number
  latestVersion: string
  overallCompletionRate: number
}

export interface ConfirmRecord {
  id: number
  bizType: string
  bizId: number
  operatorName: string
  action: string
  comment: string
  createdAt: string
}

export interface EmployeeMonthPlanDetailResp {
  id: number
  planMonth: string
  employeeName: string
  departmentName: string
  status: EmployeePlanStatus
  resultStatus: EmployeeResultStatus
  updatedAt: string
  summary?: string
  approvalComment?: string
  approvedAt?: string
  items: EmployeeMonthPlanItem[]
  deliverables: EmployeeDeliverable[]
  resultSummary: EmployeeResultSummary
  confirmRecords: ConfirmRecord[]
}

export interface SaveMonthPlanDraftReq {
  planMonth?: string
  summary: string
  items: Array<{
    id?: number
    taskName: string
    taskContent: string
    deliverable: string
    deadline: string
    performanceWeight: number
  }>
}

export interface EmployeeMonthPlanSubmitResp {
  id: number
  status: EmployeePlanStatus
  submittedAt?: string
}

export interface EmployeeMonthPlanItemOption {
  id: number
  taskName: string
}

export interface EmployeeDayPlanDetailResp {
  id: number
  planDate: string
  orgName: string
  relatedMonthPlanItemId: number | null
  content: string
  remark: string
  status: EmployeePlanStatus
  reviewStatus?: string
  riskLevel?: string
  leaderComment?: string
  reviewedAt?: string
  departmentComment?: string
  departmentReviewedAt?: string
  approvalDueAt?: string
  aiCheckResult?: string
  calendarRule: EmployeeWorkdayCalendarItem
  monthPlanItemOptions: EmployeeMonthPlanItemOption[]
}

export interface SaveDayPlanDraftReq {
  id?: number
  planDate: string
  relatedMonthPlanItemId: number | null
  content: string
  remark: string
}

export interface SubmitDayPlanReq extends SaveDayPlanDraftReq {}

export interface EmployeeDayPlanSubmitResp {
  id: number
  status: EmployeePlanStatus
  submittedAt: string
}

export interface EmployeeResultSubmitOptionsResp {
  monthPlanOptions: Array<{
    id: number
    title: string
    planMonth: string
  }>
  monthPlanItemOptions: Array<{
    id: number
    monthPlanId: number
    taskName: string
  }>
  acceptedFileTypes: string[]
  maxFileSizeMb: number
  resultVersions: Array<{
    id: number
    monthPlanId: number
    monthPlanItemId?: number
    versionNo: string
    status: EmployeeResultStatus
    submittedAt: string
    leaderSuggestion: string
    confirmComment: string
  }>
}

export interface SubmitEmployeeResultReq {
  monthPlanId: number
  monthPlanItemId?: number
  completionRate: number
  description?: string
  file: File | Blob
}

export interface EmployeeResultVersionResp {
  id: number
  versionNo: string
  status: EmployeeResultStatus
  submittedAt: string
}

export interface EmployeePerformanceEvidenceItem {
  id: number
  evidenceDate: string
  periodType: EmployeeEvidencePeriodType
  sourceType: EmployeeEvidenceSourceType
  title: string
  description: string
  score: number
  createdAt: string
}

export interface EmployeePerformanceEvidenceResp {
  periodType: EmployeeEvidencePeriodType
  periodStart: string
  periodEnd: string
  items: EmployeePerformanceEvidenceItem[]
}

export interface EmployeeResultEvidence {
  id: number
  fileName: string
  fileType: string
  fileSize: number
  status: string
  reviewPassed: boolean
  checksum: string
  createdAt: string
  downloadUrl: string
}

export interface EmployeeResultDetailResp {
  id: number
  resultNo: string
  title: string
  resultDate: string
  description: string
  completionRate: number
  versionNo: string
  status: EmployeeResultStatus
  planType: string
  planId?: number
  planTitle: string
  monthPlanItemId?: number
  planItemName: string
  submittedAt: string
  suggestionStatus: string
  leaderSuggestion: string
  suggestedAt?: string
  confirmComment: string
  confirmedAt?: string
  evidenceStatus: string
  issueCodes: string[]
  issueText: string
  evidences: EmployeeResultEvidence[]
}

export interface EmployeeAppealItem {
  id: number
  appealNo: string
  title: string
  reason: string
  status: EmployeeAppealStatus
  handleComment: string
  handledAt?: string
  createdAt: string
}

export interface EmployeeAppealListResp {
  items: EmployeeAppealItem[]
}

export interface EmployeeAppealOption {
  resultId: number
  label: string
  status: EmployeeResultStatus
  deadline: string
}

export interface CreateEmployeeAppealReq {
  relatedResultId?: number
  title: string
  reason: string
}

export interface CreateEmployeePlanAdjustmentReq {
  planType: 'MONTH'
  planId: number
  adjustmentType: 'PAUSE' | 'CANCEL'
  reason: string
  impactText?: string
}

export interface EmployeeExportResp {
  taskId: string
  fileName: string
  checksum: string
  downloadUrl: string
}

export async function getEmployeeDashboardApi(month: string) {
  return http.get<unknown, EmployeeDashboardResp>('/employee/dashboard', { params: { month } })
}

export async function getEmployeeMonthPlanDetailApi(id: number) {
  return http.get<unknown, EmployeeMonthPlanDetailResp>(`/employee/month-plans/${id}`)
}

export async function createEmployeeMonthPlanDraftApi(payload: SaveMonthPlanDraftReq) {
  return http.post<SaveMonthPlanDraftReq, EmployeeMonthPlanDetailResp>('/employee/month-plans/draft', payload)
}

export async function saveEmployeeMonthPlanDraftApi(id: number, payload: SaveMonthPlanDraftReq) {
  return http.post<SaveMonthPlanDraftReq, EmployeeMonthPlanDetailResp>(`/employee/month-plans/${id}/draft`, payload)
}

export async function submitEmployeeMonthPlanApi(id: number) {
  return http.post<undefined, EmployeeMonthPlanSubmitResp>(`/employee/month-plans/${id}/submit`, undefined, {
    timeout: AI_OPERATION_TIMEOUT_MS,
  })
}

export async function withdrawEmployeeMonthPlanApi(id: number) {
  return http.post<undefined, EmployeeMonthPlanSubmitResp>(`/employee/month-plans/${id}/withdraw`)
}

export async function submitEmployeeExtraMonthPlanItemApi(id: number, payload: SaveMonthPlanDraftReq['items'][number], aiReviewId?: number) {
  return http.post<SaveMonthPlanDraftReq['items'][number], EmployeeMonthPlanItem>(`/employee/month-plans/${id}/extra-items`, payload, {
    params: aiReviewId ? { aiReviewId } : undefined,
    timeout: AI_OPERATION_TIMEOUT_MS,
  })
}

export async function saveEmployeeExtraMonthPlanItemDraftApi(planId: number, itemId: number, payload: SaveMonthPlanDraftReq['items'][number]) {
  return http.post<SaveMonthPlanDraftReq['items'][number], EmployeeMonthPlanItem>(`/employee/month-plans/${planId}/extra-items/${itemId}/draft`, payload)
}

export async function submitEmployeeExtraMonthPlanItemDraftApi(planId: number, itemId: number) {
  return http.post<undefined, EmployeeMonthPlanItem>(`/employee/month-plans/${planId}/extra-items/${itemId}/submit`, undefined, {
    timeout: AI_OPERATION_TIMEOUT_MS,
  })
}

export async function withdrawEmployeeExtraMonthPlanItemApi(planId: number, itemId: number) {
  return http.post<undefined, EmployeeMonthPlanItem>(`/employee/month-plans/${planId}/extra-items/${itemId}/withdraw`)
}

export async function deleteEmployeeMonthPlanItemApi(planId: number, itemId: number) {
  return http.delete<unknown, void>(`/employee/month-plans/${planId}/items/${itemId}`)
}

export async function getEmployeeDayPlanDetailApi(date: string) {
  return http.get<unknown, EmployeeDayPlanDetailResp>('/employee/day-plans/detail', { params: { date } })
}

export async function saveEmployeeDayPlanDraftApi(payload: SaveDayPlanDraftReq) {
  return http.post<SaveDayPlanDraftReq, EmployeeDayPlanDetailResp>('/employee/day-plans/draft', payload)
}

export async function submitEmployeeDayPlanApi(payload: SubmitDayPlanReq) {
  return http.post<SubmitDayPlanReq, EmployeeDayPlanSubmitResp>('/employee/day-plans/submit', payload, {
    timeout: AI_OPERATION_TIMEOUT_MS,
  })
}

export async function withdrawEmployeeDayPlanApi(id: number) {
  return http.post<undefined, EmployeeDayPlanSubmitResp>(`/employee/day-plans/${id}/withdraw`)
}

export async function getEmployeeResultSubmitOptionsApi() {
  return http.get<unknown, EmployeeResultSubmitOptionsResp>('/employee/results/submit/options')
}

export async function getEmployeeResultDetailApi(id: number) {
  return http.get<unknown, EmployeeResultDetailResp>(`/employee/results/${id}`)
}

export async function downloadEmployeeResultEvidenceApi(resultId: number, evidenceId: number) {
  return http.get<unknown, Blob>(`/employee/results/${resultId}/evidence/${evidenceId}`, { responseType: 'blob' })
}

export async function submitEmployeeResultApi(payload: FormData | SubmitEmployeeResultReq) {
  return http.post<FormData | SubmitEmployeeResultReq, EmployeeResultVersionResp>('/employee/results/submit', payload, payload instanceof FormData ? {
    headers: { 'Content-Type': 'multipart/form-data' },
  } : undefined)
}

export async function getEmployeePerformanceEvidenceApi(periodType: EmployeeEvidencePeriodType) {
  return http.get<unknown, EmployeePerformanceEvidenceResp>('/employee/performance-evidence', { params: { periodType } })
}

export async function getEmployeeAppealsApi() {
  return http.get<unknown, EmployeeAppealListResp>('/employee/appeals')
}

export async function getEmployeeAppealOptionsApi() {
  return http.get<unknown, EmployeeAppealOption[]>('/employee/appeals/options')
}

export async function downloadEmployeeAppealPackageApi(id: number) {
  return http.get<unknown, Blob>(`/employee/appeals/${id}/package`, { responseType: 'blob' })
}

export async function createEmployeeAppealApi(payload: CreateEmployeeAppealReq) {
  return http.post<CreateEmployeeAppealReq, EmployeeAppealItem>('/employee/appeals', payload)
}

export async function createEmployeePlanAdjustmentApi(payload: CreateEmployeePlanAdjustmentReq) {
  return http.post<CreateEmployeePlanAdjustmentReq, { id: number; adjustmentNo: string; status: string }>('/employee/plan-adjustments', payload)
}

export async function createEmployeeEvidenceExportApi(payload: { periodType: EmployeeEvidencePeriodType; formats: string[]; includeEvidence: boolean }) {
  return http.post<typeof payload, EmployeeExportResp>('/employee/performance-evidence/export', payload)
}

export async function downloadEmployeeExportApi(taskId: string) {
  return http.get<unknown, Blob>(`/employee/export-tasks/${taskId}/download`, { responseType: 'blob' })
}
