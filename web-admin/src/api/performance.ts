export type MetricTone = 'primary' | 'success' | 'warning' | 'danger' | 'info' | string
export type ExportFormat = 'PDF' | 'WORD' | 'ZIP'

export interface MetricItem {
  code: string
  label: string
  value: number
  tone: MetricTone
}

export interface OrgNode {
  id: number
  label: string
  orgType: string
  children: OrgNode[]
}

export interface EvidenceFile {
  fileId: number
  fileName: string
  evidenceType: string
  status: string
  reviewPassed: boolean
}

export interface ActionResult {
  objectId: string
  status: string
  message: string
  auditActionCode: string
  auditDeferred: boolean
}

export interface LedgerItem {
  id: string
  ownerId: number
  employeeNo: string
  employeeName: string
  orgId: number
  orgName: string
  periodType: string
  periodStart: string
  periodEnd: string
  planCount: number
  resultCount: number
  avgCompletionRatio: number
  referenceScore: number
  overdueCount: number
  missingEvidenceCount: number
  evidenceChainStatus: string
  appealStatus: string
}

export interface PerformanceActionPayload {
  action?: string
  decision?: string
  comment?: string
  riskLevel?: string
  notifyEmployee?: boolean
  keepEvidenceChain?: boolean
  authPassword?: string
}

export interface BatchActionPayload extends PerformanceActionPayload {
  ids: string[]
}

export interface ExportTaskCreatePayload {
  dimensionType?: string
  dimensionId?: string
  periodType?: string
  periodStart?: string
  periodEnd?: string
  formats: ExportFormat[]
  includeEvidence?: boolean
  watermark?: string
}

export interface ExportTask {
  id: string
  dimensionType: string
  dimensionName: string
  periodType: string
  periodStart?: string
  periodEnd?: string
  formats: ExportFormat[]
  includeEvidence: boolean
  watermark: string
  integrityStatus: string
  missingItems: string[]
  checksum?: string
  status: string
  sizeText: string
  requestedBy: number
  requestedByName: string
  requestedAt: string
  finishedAt?: string
  expireAt?: string
  errorMessage?: string
}

export interface ExportDownload {
  taskId: string
  status: string
  fileName: string
  downloadUrl: string
  expireAt?: string
  checksum?: string
}

export function errorMessage(error: unknown) {
  return error instanceof Error ? error.message : '操作失败，请稍后重试'
}

export function normalizeExportFormat(format: string): ExportFormat {
  const value = format.toUpperCase()
  if (value === 'WORD' || value === 'DOC' || value === 'DOCX') return 'WORD'
  if (value === 'ZIP') return 'ZIP'
  return 'PDF'
}

export function periodRange(period: string) {
  const [yearText, monthText] = period.split('-')
  const year = Number(yearText)
  const month = Number(monthText)
  const lastDay = new Date(year, month, 0).getDate()
  return {
    periodStart: `${yearText}-${monthText}-01`,
    periodEnd: `${yearText}-${monthText}-${String(lastDay).padStart(2, '0')}`,
  }
}

export function formatLocalDate(date: Date) {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

export function currentDate() {
  return formatLocalDate(new Date())
}

export function currentMonth() {
  const now = new Date()
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`
}

export function currentYear() {
  return String(new Date().getFullYear())
}

export function currentMonthDateRange() {
  const now = new Date()
  return [
    formatLocalDate(new Date(now.getFullYear(), now.getMonth(), 1)),
    formatLocalDate(new Date(now.getFullYear(), now.getMonth() + 1, 0)),
  ]
}

export function currentMonthToDateRange() {
  const now = new Date()
  return [formatLocalDate(new Date(now.getFullYear(), now.getMonth(), 1)), formatLocalDate(now)]
}

export function currentWeekDateRange() {
  const now = new Date()
  const day = now.getDay() || 7
  const monday = new Date(now.getFullYear(), now.getMonth(), now.getDate() - day + 1)
  const sunday = new Date(monday.getFullYear(), monday.getMonth(), monday.getDate() + 6)
  return [formatLocalDate(monday), formatLocalDate(sunday)]
}
