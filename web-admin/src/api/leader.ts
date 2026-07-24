import { http } from './http'
import type {
  ActionResult,
  BatchActionPayload,
  EvidenceFile,
  ExportDownload,
  ExportTask,
  ExportTaskCreatePayload,
  LedgerItem,
  MetricItem,
  OrgNode,
  PerformanceActionPayload,
} from './performance'
import type { MonthPlanApprovalItem, MonthPlanApprovalPage } from './department'

export interface LeaderDateStatus {
  date: string
  orgId: number
  orgName: string
  pendingReviewCount: number
  pendingSuggestCount: number
  overdueCount: number
  status: string
}

export interface DailyReviewItem {
  id: string
  ownerId: number
  employeeNo: string
  employeeName: string
  orgId: number
  orgName: string
  planDate: string
  submittedAt?: string
  workContent: string
  deliverable: string
  approvalDueAt: string
  overdueApproval: boolean
  missingFields: string[]
  aiCheckResult: string
  reviewStatus: string
  riskLevel: string
  leaderComment?: string
  reviewedAt?: string
}

export interface LeaderWorkbench {
  orgTree: OrgNode[]
  metrics: MetricItem[]
  dateStatuses: LeaderDateStatus[]
  subordinateSummaries: DailyReviewItem[]
}

export interface ResultSuggestionItem {
  id: string
  ownerId: number
  employeeNo: string
  employeeName: string
  orgId: number
  orgName: string
  resultNo: string
  resultTitle: string
  planType: string
  planId: number
  planNo: string
  completionRatio: number
  autoLevel: string
  evidenceStatus: string
  issueCodes: string[]
  issueText: string
  suggestionStatus: string
  leaderSuggestion?: string
  resultStatus: string
  evidences: EvidenceFile[]
}

export interface PlanAdjustmentItem {
  id: string
  originalPlanType: string
  originalPlanId: number
  originalPlanNo: string
  originalWorkContent: string
  newPlanType: string
  newPlanId: number
  newPlanNo: string
  ownerId: number
  employeeName: string
  adjustmentType: string
  reason: string
  impactText: string
  operationComment?: string
  status: string
  keepEvidenceChain: boolean
  operatorName?: string
  operatedAt?: string
}

export interface ExtraMonthPlanApprovalItem {
  id: string
  monthPlanId: number
  planMonth: string
  ownerId: number
  employeeNo: string
  employeeName: string
  orgId: number
  orgName: string
  taskName: string
  taskContent: string
  deliverable: string
  deadline: string
  performanceWeight: number
  status: 'PENDING' | 'APPROVED' | 'REJECTED'
  submittedAt: string
  approverId?: number
  approvedAt?: string
  approvalComment?: string
}

export function getLeaderOrgTreeApi() {
  return http.get<unknown, OrgNode[]>('/leader/org-tree')
}

export function getLeaderWorkbenchApi(params?: Record<string, unknown>) {
  return http.get<unknown, LeaderWorkbench>('/leader/workbench', { params })
}

export function listDailyReviewsApi(params?: Record<string, unknown>) {
  return http.get<unknown, DailyReviewItem[]>('/leader/daily-reviews', { params })
}

export function getDailyReviewApi(id: string) {
  return http.get<unknown, DailyReviewItem>(`/leader/daily-reviews/${id}`)
}

export function commentDailyReviewApi(id: string, data: PerformanceActionPayload) {
  return http.post<unknown, ActionResult>(`/leader/daily-reviews/${id}/comment`, data)
}

export function markDailyReviewRiskApi(id: string, data: PerformanceActionPayload) {
  return http.post<unknown, ActionResult>(`/leader/daily-reviews/${id}/risk`, data)
}

export function batchCommentDailyReviewsApi(data: BatchActionPayload) {
  return http.post<unknown, ActionResult[]>('/leader/daily-reviews/batch-comment', data)
}

export function batchMarkDailyReviewRisksApi(data: BatchActionPayload) {
  return http.post<unknown, ActionResult[]>('/leader/daily-reviews/batch-risk', data)
}

export function listResultSuggestionsApi(params?: Record<string, unknown>) {
  return http.get<unknown, ResultSuggestionItem[]>('/leader/result-suggestions', { params })
}

export function getResultSuggestionApi(id: string) {
  return http.get<unknown, ResultSuggestionItem>(`/leader/result-suggestions/${id}`)
}

export function downloadLeaderResultEvidenceApi(resultId: string, evidenceId: number) {
  return http.get<unknown, Blob>(`/leader/result-suggestions/${resultId}/evidence/${evidenceId}`, { responseType: 'blob' })
}

export function submitResultSuggestionApi(id: string, data: PerformanceActionPayload) {
  return http.post<unknown, ActionResult>(`/leader/result-suggestions/${id}/suggest`, data)
}

export function batchSubmitResultSuggestionsApi(data: BatchActionPayload) {
  return http.post<unknown, ActionResult[]>('/leader/result-suggestions/batch-suggest', data)
}

export function listPlanAdjustmentsApi(params?: Record<string, unknown>) {
  return http.get<unknown, PlanAdjustmentItem[]>('/leader/plan-adjustments', { params })
}

export function getPlanAdjustmentApi(id: string) {
  return http.get<unknown, PlanAdjustmentItem>(`/leader/plan-adjustments/${id}`)
}

export function processPlanAdjustmentApi(id: string, data: PerformanceActionPayload) {
  return http.post<unknown, ActionResult>(`/leader/plan-adjustments/${id}/process`, data)
}

export function listLeaderMonthPlanApprovalsApi(params?: Record<string, unknown>) {
  return http.get<unknown, MonthPlanApprovalItem[]>('/leader/month-plan-approvals', { params })
}

export function pageLeaderMonthPlanApprovalsApi(params?: Record<string, unknown>) {
  return http.get<unknown, MonthPlanApprovalPage>('/leader/month-plan-approvals/page', { params })
}

export function getLeaderMonthPlanApprovalApi(id: string) {
  return http.get<unknown, MonthPlanApprovalItem>(`/leader/month-plan-approvals/${id}`)
}

export function approveLeaderMonthPlanApi(id: string, data: PerformanceActionPayload) {
  return http.post<unknown, ActionResult>(`/leader/month-plan-approvals/${id}/approve`, data)
}

export function rejectLeaderMonthPlanApi(id: string, data: PerformanceActionPayload) {
  return http.post<unknown, ActionResult>(`/leader/month-plan-approvals/${id}/reject`, data)
}

export function batchApproveLeaderMonthPlansApi(data: BatchActionPayload) {
  return http.post<unknown, ActionResult[]>('/leader/month-plan-approvals/batch-approve', data)
}

export function batchRejectLeaderMonthPlansApi(data: BatchActionPayload) {
  return http.post<unknown, ActionResult[]>('/leader/month-plan-approvals/batch-reject', data)
}

export function listExtraMonthPlanApprovalsApi(params?: Record<string, unknown>) {
  return http.get<unknown, ExtraMonthPlanApprovalItem[]>('/leader/extra-month-plan-approvals', { params })
}

export function approveExtraMonthPlanItemApi(id: string, data: PerformanceActionPayload) {
  return http.post<unknown, ActionResult>(`/leader/extra-month-plan-approvals/${id}/approve`, data)
}

export function rejectExtraMonthPlanItemApi(id: string, data: PerformanceActionPayload) {
  return http.post<unknown, ActionResult>(`/leader/extra-month-plan-approvals/${id}/reject`, data)
}

export function listTeamLedgersApi(params?: Record<string, unknown>) {
  return http.get<unknown, LedgerItem[]>('/leader/team-ledgers', { params })
}

export function exportTeamLedgersApi(data: ExportTaskCreatePayload) {
  return http.post<unknown, ExportTask>('/leader/team-ledgers/export', data)
}

export function createLeaderExportTaskApi(data: ExportTaskCreatePayload) {
  return http.post<unknown, ExportTask>('/leader/export-tasks', data)
}

export function getLeaderExportDownloadInfoApi(id: string) {
  return http.get<unknown, ExportDownload>(`/leader/export-tasks/${id}/download-info`)
}

export function downloadLeaderExportTaskApi(id: string) {
  return http.get<unknown, Blob>(`/leader/export-tasks/${id}/download`, { responseType: 'blob' })
}
