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

export interface DepartmentSummary {
  orgId: number
  orgName: string
  monthPlanCount: number
  approvedPlanCount: number
  pendingPlanCount: number
  confirmedResultCount: number
  closureRate: number
  missingFieldCount: number
  overdueCount: number
  riskSummary: string
}

export interface TodoItem {
  id: string
  sceneCode: string
  title: string
  triggerText: string
  receiverId: number
  receiverName: string
  objectType: string
  objectId: string
  dueAt: string
  requirement: string
  impact: string
  status: string
  remindCount: number
  routeHint: string
}

export interface DepartmentAppeal {
  id: number
  appealNo: string
  title: string
  reason: string
  status: string
  ownerUserId: number
  employeeName: string
  orgName: string
  relatedResultId?: number
  resultTitle: string
  resultStatus: string
  completionRate: number
  handlerId?: number
  handleComment: string
  createdAt: string
  handledAt?: string
}

export interface DepartmentDayPlanReview {
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
  approvalDueAt?: string
  overdueApproval: boolean
  missingFields: string[]
  aiCheckResult: string
  reviewStatus: string
  riskLevel: string
  leaderComment: string
  leaderName: string
  reviewedAt?: string
  status: string
  departmentComment: string
  departmentReviewerName: string
  departmentReviewedAt?: string
}

export interface DepartmentDashboard {
  metrics: MetricItem[]
  summaries: DepartmentSummary[]
  urgentTodos: TodoItem[]
}

export interface MonthPlanApprovalItem {
  id: string
  planNo: string
  ownerId: number
  employeeNo: string
  employeeName: string
  orgId: number
  orgName: string
  planYear: number
  planMonth: number
  workContent: string
  deliverable: string
  deadline: string
  status: string
  leaderComment: string
  approverId?: number
  approverName?: string
  approvedAt?: string
  aiCheckResult: string
  missingFields: string[]
  submittedAt: string
  version: number
  items: Array<{
    id: number
    taskName: string
    taskContent: string
    deliverable: string
    performanceWeight: number
    deadline?: string
    status: string
  }>
}

export interface MonthPlanApprovalPage {
  items: MonthPlanApprovalItem[]
  total: number
  pageNo: number
  pageSize: number
}

export interface ResultConfirmItem {
  id: string
  resultNo: string
  ownerId: number
  employeeNo: string
  employeeName: string
  orgId: number
  orgName: string
  planType: string
  planId: number
  planNo: string
  resultTitle: string
  completionRatio: number
  autoLevel: string
  evidenceStatus: string
  leaderSuggestion: string
  issueCodes: string[]
  issueText: string
  confirmStatus: string
  evidences: EvidenceFile[]
}

export interface DeliverableTemplate {
  id: number
  orgId: number
  orgName: string
  templateName: string
  evidenceType: string
  required: boolean
  appliesTo: string
  description: string
  versionNo: string
  status: string
  referenceCount: number
}

export interface AcceptanceStandard {
  id: number
  templateId: number
  templateName: string
  standardText: string
  requireReviewPassed: boolean
  evidenceRequirement: string
  versionNo: string
  status: string
}

export interface ScoreRule {
  id: number
  orgId: number
  orgName: string
  ruleName: string
  status: string
  effectiveStart?: string
  effectiveEnd?: string
  ruleJson: Record<string, unknown>
}

export interface ScoreSimulation {
  employeeName: string
  score: number
  hitFactors: string[]
  explanation: string
}

export interface DeliverableTemplatePayload {
  orgId: number
  templateName: string
  evidenceType: string
  required: boolean
  appliesTo?: string
  description?: string
}

export interface AcceptanceStandardPayload {
  templateId: number
  standardText: string
  requireReviewPassed?: boolean
  evidenceRequirement?: string
}

export interface ScoreRulePayload {
  orgId: number
  ruleName: string
  effectiveStart?: string
  effectiveEnd?: string
  ruleJson?: Record<string, unknown>
}

export interface ScoreSimulationPayload {
  employeeId?: number
  employeeName?: string
  completionRatio?: number
  overdueCount?: number
  rejectCount?: number
  evidenceComplete?: boolean
  reviewPassed?: boolean
}

export function getDepartmentOrgTreeApi() {
  return http.get<unknown, OrgNode[]>('/department/org-tree')
}

export function getDepartmentDashboardApi(params?: Record<string, unknown>) {
  return http.get<unknown, DepartmentDashboard>('/department/dashboard', { params })
}

export function listMonthPlanApprovalsApi(params?: Record<string, unknown>) {
  return http.get<unknown, MonthPlanApprovalItem[]>('/department/month-plan-approvals', { params })
}

export function pageMonthPlanApprovalsApi(params?: Record<string, unknown>) {
  return http.get<unknown, MonthPlanApprovalPage>('/department/month-plan-approvals/page', { params })
}

export function getMonthPlanApprovalApi(id: string) {
  return http.get<unknown, MonthPlanApprovalItem>(`/department/month-plan-approvals/${id}`)
}

export function listResultConfirmsApi(params?: Record<string, unknown>) {
  return http.get<unknown, ResultConfirmItem[]>('/department/result-confirms', { params })
}

export function getResultConfirmApi(id: string) {
  return http.get<unknown, ResultConfirmItem>(`/department/result-confirms/${id}`)
}

export function downloadDepartmentResultEvidenceApi(resultId: string, evidenceId: number) {
  return http.get<unknown, Blob>(`/department/result-confirms/${resultId}/evidence/${evidenceId}`, { responseType: 'blob' })
}

export function confirmResultApi(id: string, data: PerformanceActionPayload) {
  return http.post<unknown, ActionResult>(`/department/result-confirms/${id}/confirm`, data)
}

export function rejectResultApi(id: string, data: PerformanceActionPayload) {
  return http.post<unknown, ActionResult>(`/department/result-confirms/${id}/reject`, data)
}

export function listTodosApi(params?: Record<string, unknown>) {
  return http.get<unknown, TodoItem[]>('/department/todos', { params })
}

export function getDepartmentDayPlanReviewApi(id: string) {
  return http.get<unknown, DepartmentDayPlanReview>(`/department/day-plan-reviews/${id}`)
}

export function approveDepartmentDayPlanReviewApi(id: string, data: PerformanceActionPayload) {
  return http.post<unknown, ActionResult>(`/department/day-plan-reviews/${id}/approve`, data)
}

export function rejectDepartmentDayPlanReviewApi(id: string, data: PerformanceActionPayload) {
  return http.post<unknown, ActionResult>(`/department/day-plan-reviews/${id}/reject`, data)
}

export function remindTodoApi(id: string) {
  return http.post<unknown, ActionResult>(`/department/todos/${id}/remind`)
}

export function readTodoApi(id: string) {
  return http.post<unknown, ActionResult>(`/department/todos/${id}/read`)
}

export function escalateTodoApi(id: string) {
  return http.post<unknown, ActionResult>(`/department/todos/${id}/escalate`)
}

export function doneTodoApi(id: string) {
  return http.post<unknown, ActionResult>(`/department/todos/${id}/done`)
}

export function batchRemindTodosApi(data: BatchActionPayload) {
  return http.post<unknown, ActionResult[]>('/department/todos/batch-remind', data)
}

export function batchEscalateTodosApi(data: BatchActionPayload) {
  return http.post<unknown, ActionResult[]>('/department/todos/batch-escalate', data)
}

export function getDepartmentAppealApi(id: string) {
  return http.get<unknown, DepartmentAppeal>(`/department/appeals/${id}`)
}

export function acceptDepartmentAppealApi(id: string, data: PerformanceActionPayload) {
  return http.post<unknown, ActionResult>(`/department/appeals/${id}/accept`, data)
}

export function resolveDepartmentAppealApi(id: string, data: PerformanceActionPayload) {
  return http.post<unknown, ActionResult>(`/department/appeals/${id}/resolve`, data)
}

export function downloadDepartmentAppealPackageApi(id: string) {
  return http.get<unknown, Blob>(`/department/appeals/${id}/package`, { responseType: 'blob' })
}

export function listDeliverableTemplatesApi(params?: Record<string, unknown>) {
  return http.get<unknown, DeliverableTemplate[]>('/department/templates', { params })
}

export function createDeliverableTemplateApi(data: DeliverableTemplatePayload) {
  return http.post<unknown, DeliverableTemplate>('/department/templates', data)
}

export function updateDeliverableTemplateApi(id: number, data: DeliverableTemplatePayload) {
  return http.put<unknown, DeliverableTemplate>(`/department/templates/${id}`, data)
}

export function toggleDeliverableTemplateApi(id: number, enabled: boolean) {
  return http.post<unknown, ActionResult>(`/department/templates/${id}/${enabled ? 'enable' : 'disable'}`)
}

export function listAcceptanceStandardsApi(params?: Record<string, unknown>) {
  return http.get<unknown, AcceptanceStandard[]>('/department/acceptance-standards', { params })
}

export function createAcceptanceStandardApi(data: AcceptanceStandardPayload) {
  return http.post<unknown, AcceptanceStandard>('/department/acceptance-standards', data)
}

export function updateAcceptanceStandardApi(id: number, data: AcceptanceStandardPayload) {
  return http.put<unknown, AcceptanceStandard>(`/department/acceptance-standards/${id}`, data)
}

export function toggleAcceptanceStandardApi(id: number, enabled: boolean) {
  return http.post<unknown, ActionResult>(`/department/acceptance-standards/${id}/${enabled ? 'enable' : 'disable'}`)
}

export function listScoreRulesApi(params?: Record<string, unknown>) {
  return http.get<unknown, ScoreRule[]>('/department/score-rules', { params })
}

export function createScoreRuleApi(data: ScoreRulePayload) {
  return http.post<unknown, ScoreRule>('/department/score-rules', data)
}

export function updateScoreRuleApi(id: number, data: ScoreRulePayload) {
  return http.put<unknown, ScoreRule>(`/department/score-rules/${id}`, data)
}

export function enableScoreRuleApi(id: number) {
  return http.post<unknown, ActionResult>(`/department/score-rules/${id}/enable`)
}

export function simulateScoreRuleApi(id: number, data: ScoreSimulationPayload) {
  return http.post<unknown, ScoreSimulation>(`/department/score-rules/${id}/simulate`, data)
}

export function listDepartmentLedgersApi(params?: Record<string, unknown>) {
  return http.get<unknown, LedgerItem[]>('/department/department-ledgers', { params })
}

export function exportDepartmentLedgersApi(data: ExportTaskCreatePayload) {
  return http.post<unknown, ExportTask>('/department/department-ledgers/export', data)
}

export function listExportTasksApi(params?: Record<string, unknown>) {
  return http.get<unknown, ExportTask[]>('/department/export-tasks', { params })
}

export function getExportTaskApi(id: string) {
  return http.get<unknown, ExportTask>(`/department/export-tasks/${id}`)
}

export function createExportTaskApi(data: ExportTaskCreatePayload) {
  return http.post<unknown, ExportTask>('/department/export-tasks', data)
}

export function checkExportTaskApi(id: string) {
  return http.post<unknown, ActionResult>(`/department/export-tasks/${id}/check`)
}

export function retryExportTaskApi(id: string) {
  return http.post<unknown, ActionResult>(`/department/export-tasks/${id}/retry`)
}

export function getExportDownloadInfoApi(id: string) {
  return http.get<unknown, ExportDownload>(`/department/export-tasks/${id}/download-info`)
}

export function downloadExportTaskApi(id: string) {
  return http.get<unknown, Blob>(`/department/export-tasks/${id}/download`, { responseType: 'blob' })
}
