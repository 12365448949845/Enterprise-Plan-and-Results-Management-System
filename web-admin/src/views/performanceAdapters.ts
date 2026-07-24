import type { ExportTask, LedgerItem } from '@/api/performance'
import type {
  DailyReviewItem,
  LeaderDateStatus,
  PlanAdjustmentItem,
  ResultSuggestionItem,
} from '@/api/leader'
import type {
  AcceptanceStandard,
  DeliverableTemplate,
  DepartmentSummary,
  MonthPlanApprovalItem,
  ResultConfirmItem,
  TodoItem,
} from '@/api/department'

export const reviewStatusCodes: Record<string, string> = {
  '待点评': 'PENDING_COMMENT',
  '已点评': 'COMMENTED',
  '风险': 'RISK_MARKED',
  '风险已复核': 'RISK_RESOLVED',
  '需补充': 'SUPPLEMENT_REQUIRED',
}

export const suggestionStatusCodes: Record<string, string> = {
  '待建议': 'PENDING_SUGGEST',
  '建议确认': 'SUGGEST_CONFIRM',
  '建议驳回': 'SUGGEST_REJECT',
  '建议补充证据': 'SUGGEST_REJECT',
}

export const adjustmentStatusCodes: Record<string, string> = {
  '待处理': 'PENDING',
  '已暂停': 'PAUSED',
  '已撤销': 'CANCELED',
}

export const approvalStatusCodes: Record<string, string> = {
  '待审批': 'PENDING_APPROVAL',
  '已通过': 'APPROVED',
  '已驳回': 'REJECTED',
}

export const confirmStatusCodes: Record<string, string> = {
  '待确认': 'PENDING_CONFIRM',
  '不可确认': 'BLOCKED',
  '已确认': 'CONFIRMED',
  '已驳回': 'REJECTED',
}

export const todoStatusCodes: Record<string, string> = {
  '待处理': 'UNREAD',
  '处理中': 'READ',
  '已处理': 'DONE',
}

export const todoSceneCodes: Record<string, string> = {
  '日计划补审': 'DAY_PLAN_REVIEW',
  '成果最终确认': 'RESULT_CONFIRM',
  '申诉待处理': 'APPEAL_PROCESS',
  '资料包导出完成': 'EXPORT_DONE',
}

export const exportStatusCodes: Record<string, string> = {
  '成功': 'SUCCESS',
  '待确认': 'NEEDS_REVIEW',
  '生成中': 'PROCESSING',
  '失败': 'FAILED',
  '待处理': 'PENDING',
  '已过期': 'EXPIRED',
}

const reviewStatusLabels: Record<string, string> = {
  PENDING_COMMENT: '待点评',
  COMMENTED: '已点评',
  RISK_MARKED: '风险',
  RISK_RESOLVED: '风险已复核',
  SUPPLEMENT_REQUIRED: '需补充',
}

const riskLevelLabels: Record<string, string> = {
  LOW: '低',
  MEDIUM: '中',
  HIGH: '高',
}

const aiCheckLabels: Record<string, string> = {
  NORMAL: '正常',
  NOT_RUN: 'AI未执行',
  UNKNOWN: '依据不足',
  MEDIUM: '中风险',
  HIGH: '高风险',
  EXPECTED_HOURS_HIGH: '超参考工时',
  REQUIRED_FIELD_MISSING: '必要字段缺失',
  DELIVERABLE_MISSING: '交付物为空',
}

const suggestionStatusLabels: Record<string, string> = {
  PENDING_SUGGEST: '待建议',
  SUGGEST_CONFIRM: '建议确认',
  SUGGEST_REJECT: '建议驳回',
}

const autoLevelLabels: Record<string, string> = {
  DONE: '完成',
  BASIC_DONE: '基本完成',
  PARTIAL_DONE: '部分完成',
}

const adjustmentStatusLabels: Record<string, string> = {
  PENDING: '待处理',
  PAUSED: '已暂停',
  CANCELED: '已撤销',
}

const approvalStatusLabels: Record<string, string> = {
  PENDING_APPROVAL: '待审批',
  RETURNED: '待补正',
  APPROVED: '已通过',
  REJECTED: '已驳回',
}

const confirmStatusLabels: Record<string, string> = {
  PENDING_CONFIRM: '待确认',
  BLOCKED: '不可确认',
  CONFIRMED: '已确认',
  REJECTED: '已驳回',
}

const todoStatusLabels: Record<string, string> = {
  UNREAD: '待处理',
  READ: '处理中',
  DONE: '已处理',
}

const missingFieldLabels: Record<string, string> = {
  deliverable: '交付物为空',
  deadline: '截止日期为空',
}

export function periodTypeCode(value: string) {
  return value.toUpperCase()
}

export function formatDateTime(value?: string) {
  if (!value) return '--'
  return value.replace('T', ' ').slice(0, 16)
}

export function mapLeaderDateStatus(item: LeaderDateStatus) {
  const labels: Record<string, string> = {
    NORMAL: '正常',
    OVERDUE: '有逾期',
    FOLLOW_UP: '需跟进',
  }
  return {
    date: item.date,
    group: item.orgName,
    pendingReview: item.pendingReviewCount,
    pendingSuggest: item.pendingSuggestCount,
    overdue: item.overdueCount,
    status: labels[item.status] || item.status,
  }
}

export function mapDailyReview(item: DailyReviewItem) {
  const missing = item.missingFields.length
    ? item.missingFields.map((field) => missingFieldLabels[field] || field).join('、')
    : '无'
  return {
    id: item.id,
    employee: item.employeeName,
    group: item.orgName,
    date: item.planDate,
    submittedAt: item.submittedAt || '',
    content: item.workContent,
    deliverable: item.deliverable,
    deadline: item.overdueApproval ? '逾期未审' : `截止 ${formatDateTime(item.approvalDueAt).slice(5)}`,
    missing,
    aiRisk: aiCheckLabels[item.aiCheckResult] || item.aiCheckResult,
    reviewStatus: reviewStatusLabels[item.reviewStatus] || item.reviewStatus,
    riskLevel: riskLevelLabels[item.riskLevel] || item.riskLevel,
    leaderComment: item.leaderComment || '',
    reviewedAt: item.reviewedAt || '',
  }
}

export function mapResultSuggestion(item: ResultSuggestionItem) {
  const resultStatusLabels: Record<string, string> = {
    PENDING: '待处理',
    CONFIRMED: '已确认',
    REJECTED: '已驳回',
  }
  return {
    id: item.id,
    employee: item.employeeName,
    group: item.orgName,
    result: item.resultTitle,
    plan: item.planNo,
    evidence: item.evidences.map((file) => file.fileName).join('、') || '无',
    completion: Number(item.completionRatio),
    autoGrade: autoLevelLabels[item.autoLevel] || item.autoLevel,
    issue: item.issueText || '无',
    suggestion: suggestionStatusLabels[item.suggestionStatus] || item.suggestionStatus,
    resultStatus: resultStatusLabels[item.resultStatus] || item.resultStatus,
    terminal: item.resultStatus === 'CONFIRMED' || item.resultStatus === 'REJECTED',
    evidences: item.evidences.map((file) => ({
      id: file.fileId,
      name: file.fileName,
      type: evidenceTypeText(file.evidenceType),
      status: evidenceStatusText(file.status),
    })),
  }
}

export function mapPlanAdjustment(item: PlanAdjustmentItem) {
  const audit = item.operatorName
    ? `${formatDateTime(item.operatedAt)} ${item.operatorName}处理：${item.operationComment || '未填写处理说明'}`
    : '员工发起，直属领导待处理'
  return {
    id: item.id,
    originalPlan: `${item.originalPlanNo} ${item.originalWorkContent}`,
    newPlan: item.newPlanNo,
    employee: item.employeeName,
    reason: item.reason,
    impact: item.impactText,
    operationComment: item.operationComment || '',
    status: adjustmentStatusLabels[item.status] || item.status,
    audit,
    keepEvidenceChain: item.keepEvidenceChain,
  }
}

export function mapTeamLedger(item: LedgerItem) {
  return {
    id: item.id,
    ownerId: item.ownerId,
    employee: item.employeeName,
    group: item.orgName,
    period: periodText(item),
    planCount: item.planCount,
    resultCount: item.resultCount,
    confirmedRate: `${Number(item.avgCompletionRatio)}%`,
    overdue: item.overdueCount,
    score: Number(item.referenceScore),
    evidence: ledgerCompleteness(item),
  }
}

export function mapDepartmentLedger(item: LedgerItem) {
  return {
    id: item.id,
    ownerId: item.ownerId,
    employee: item.employeeName,
    group: item.orgName,
    period: periodText(item),
    monthPlans: item.periodType === 'MONTH' ? Math.max(1, Math.round(item.planCount / 5)) : 0,
    dayPlans: item.planCount,
    results: item.resultCount,
    confirmedRate: `${Number(item.avgCompletionRatio)}%`,
    score: Number(item.referenceScore),
    appeal: appealText(item.appealStatus),
    completeness: ledgerCompleteness(item),
  }
}

export function mapExportTask(item: ExportTask) {
  const statusLabels: Record<string, string> = {
    SUCCESS: '成功',
    NEEDS_REVIEW: '待确认',
    PROCESSING: '生成中',
    FAILED: '失败',
    PENDING: '待处理',
    EXPIRED: '已过期',
  }
  const integrityLabels: Record<string, string> = {
    COMPLETE: '完整',
    VERIFIED: '已校验',
    INCOMPLETE: item.missingItems.join('、') || '不完整',
    PENDING_CHECK: '待校验',
    FAILED: '失败',
    MISMATCH: '校验不一致',
    EXPIRED: '已过期',
  }
  return {
    id: item.id,
    dimensionType: item.dimensionType,
    dimension: item.dimensionName,
    periodType: item.periodType,
    periodStart: item.periodStart || '',
    periodEnd: item.periodEnd || '',
    format: item.formats.map((format) => format === 'WORD' ? 'Word' : format === 'ZIP' ? 'Zip' : 'PDF').join(' + '),
    includeEvidence: item.includeEvidence,
    watermark: item.watermark,
    integrity: integrityLabels[item.integrityStatus] || item.integrityStatus,
    checksum: item.checksum || (item.status === 'PROCESSING' ? '生成中' : '待校验'),
    status: statusLabels[item.status] || item.status,
    size: item.sizeText,
    missingItems: item.missingItems,
    requestedByName: item.requestedByName,
    requestedAt: formatDateTime(item.requestedAt),
    finishedAt: item.finishedAt ? formatDateTime(item.finishedAt) : '',
    expireAt: item.expireAt ? formatDateTime(item.expireAt) : '',
    errorMessage: item.errorMessage || '',
  }
}

export function mapDepartmentSummary(item: DepartmentSummary) {
  return {
    group: item.orgName,
    monthPlans: item.monthPlanCount,
    approvedPlans: item.approvedPlanCount,
    pendingPlans: item.pendingPlanCount,
    confirmedResults: item.confirmedResultCount,
    closureRate: `${Number(item.closureRate)}%`,
    risk: item.riskSummary,
  }
}

export function mapMonthApproval(item: MonthPlanApprovalItem) {
  return {
    id: item.id,
    planNo: item.planNo,
    employee: item.employeeName,
    department: item.orgName,
    month: `${item.planYear}-${String(item.planMonth).padStart(2, '0')}`,
    content: item.workContent,
    deliverable: item.deliverable,
    leaderComment: item.leaderComment,
    approverName: item.approverName || '待分配',
    approvedAt: item.approvedAt || '',
    aiCheck: aiCheckLabels[item.aiCheckResult] || item.aiCheckResult,
    status: approvalStatusLabels[item.status] || item.status,
    items: item.items.map((planItem, index) => ({
      id: planItem.id,
      sortNo: index + 1,
      taskName: planItem.taskName,
      taskContent: planItem.taskContent,
      deliverable: planItem.deliverable,
      performanceWeight: Number(planItem.performanceWeight || 0),
      deadline: planItem.deadline,
      status: approvalStatusLabels[planItem.status] || planItem.status,
    })),
  }
}

export function mapResultConfirm(item: ResultConfirmItem) {
  return {
    id: item.id,
    employee: item.employeeName,
    plan: `${item.planNo} / ${item.resultTitle}`,
    evidence: item.evidences.map((file) => file.fileName).join('、') || '无',
    completion: Number(item.completionRatio),
    autoGrade: autoLevelLabels[item.autoLevel] || item.autoLevel,
    leaderSuggestion: item.leaderSuggestion,
    status: confirmStatusLabels[item.confirmStatus] || item.confirmStatus,
    issue: item.issueText || '无',
    evidences: item.evidences.map((file) => ({
      id: file.fileId,
      name: file.fileName,
      type: evidenceTypeText(file.evidenceType),
      status: evidenceStatusText(file.status),
      reviewPassed: file.reviewPassed,
    })),
  }
}

export function mapTodo(item: TodoItem) {
  const impact = item.impact || ''
  return {
    id: item.id,
    scene: item.title,
    trigger: item.triggerText,
    receiver: item.receiverName,
    deadline: formatDateTime(item.dueAt),
    requirement: item.requirement,
    impact,
    escalated: impact.includes('已标记升级处理') || impact.includes('已升级处理'),
    status: todoStatusLabels[item.status] || item.status,
    remindCount: item.remindCount || 0,
    objectType: item.objectType,
    objectId: item.objectId,
    route: item.routeHint,
  }
}

export function mapDeliverableTemplate(item: DeliverableTemplate) {
  return {
    id: item.id,
    orgId: item.orgId,
    name: item.templateName,
    department: item.orgName,
    evidenceTypeCode: item.evidenceType,
    evidenceType: evidenceTypeText(item.evidenceType),
    required: item.required ? '必填' : '选填',
    appliesCodes: appliesToCodes(item.appliesTo),
    applies: appliesToText(item.appliesTo),
    description: item.description,
    version: item.versionNo,
    status: item.status === 'ENABLED' ? '启用' : '停用',
    references: item.referenceCount,
  }
}

export function mapAcceptanceStandard(item: AcceptanceStandard) {
  return {
    id: item.id,
    templateId: item.templateId,
    deliverable: item.templateName,
    standard: item.standardText,
    evidence: item.evidenceRequirement,
    reviewRequired: item.requireReviewPassed ? '是' : '否',
    version: item.versionNo,
    status: item.status === 'ENABLED' ? '启用' : '停用',
  }
}

export function ledgerCompleteness(item: LedgerItem) {
  if (item.evidenceChainStatus === 'COMPLETE') return '完整'
  return item.missingEvidenceCount ? `缺 ${item.missingEvidenceCount} 项证据` : '不完整'
}

export function appealText(status: string) {
  return status === 'PROCESSING' ? '处理中 1' : '无'
}

function periodText(item: LedgerItem) {
  if (item.periodType === 'MONTH') return item.periodStart.slice(0, 7)
  return `${item.periodStart} ~ ${item.periodEnd}`
}

function evidenceTypeText(type: string) {
  const labels: Record<string, string> = {
    DOCUMENT: '文档',
    SPREADSHEET: '表格',
    FILE: '附件',
    IMAGE: '图片',
  }
  return labels[type] || type
}

function evidenceStatusText(status: string) {
  const labels: Record<string, string> = {
    COMPLETE: '完整',
    INCOMPLETE: '不完整',
    PENDING: '待补',
  }
  return labels[status] || status
}

function appliesToText(value: string) {
  const labels: Record<string, string> = {
    MONTH_PLAN: '月计划',
    DAY_PLAN: '日计划',
    RESULT: '成果',
  }
  return appliesToCodes(value)
    .map((item) => labels[item] || item)
    .join(' / ')
}

function appliesToCodes(value: string) {
  const codes = value.split(',').map((item) => item.trim()).filter(Boolean)
  if (codes.every((item) => ['MONTH_PLAN', 'DAY_PLAN', 'RESULT'].includes(item))) return codes
  const legacyCodes: string[] = []
  if (value.includes('月计划')) legacyCodes.push('MONTH_PLAN')
  if (value.includes('日计划')) legacyCodes.push('DAY_PLAN')
  if (value.includes('成果')) legacyCodes.push('RESULT')
  return legacyCodes.length ? legacyCodes : codes
}
