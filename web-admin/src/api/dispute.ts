import { http } from './http'

export type DisputeStatus = 'SUBMITTED' | 'REVIEWING' | 'NEEDS_SUPPLEMENT' | 'DECIDED' | 'ARCHIVED'
export type DisputeOpinion = 'SUPPORT' | 'REJECT' | 'SUPPLEMENT'

export interface DisputeMetric { code: string; label: string; value: number; tone: string }
export interface DisputeCase {
  id: number
  caseNo: string
  appealId: number
  employeeName: string
  orgName: string
  periodStart: string
  periodEnd: string
  disputeSubject: string
  appealTitle: string
  status: DisputeStatus
  packageStatus: string
  deadlineAt?: string
  reviewerCount: number
  opinionCount: number
}
export interface DisputeReviewer {
  id: number
  userId: number
  userName: string
  sourceType: string
  recusalStatus: string
  recusalReason?: string
  currentUser: boolean
}
export interface DisputeReviewerCandidate {
  userId: number
  employeeNo: string
  userName: string
  deptId?: number
}
export interface DisputeOpinionRecord {
  id: number
  reviewerId: number
  reviewerName: string
  opinion: DisputeOpinion
  comment: string
  versionNo: number
  submittedAt: string
}
export interface DisputeDetail {
  summary: DisputeCase
  appealReason: string
  appealStatus: string
  relatedResultId?: number
  resultTitle: string
  resultStatus: string
  packageItems: string[]
  reviewers: DisputeReviewer[]
  opinions: DisputeOpinionRecord[]
  canDecide: boolean
  decision?: string
  decisionComment?: string
}
export interface DisputeDashboard { metrics: DisputeMetric[]; recentCases: DisputeCase[] }

export const getDisputeDashboardApi = () => http.get<unknown, DisputeDashboard>('/dispute/dashboard')
export const getDisputeCasesApi = (params?: Record<string, unknown>) =>
  http.get<unknown, DisputeCase[]>('/dispute/cases', { params })
export const getDisputeDetailApi = (id: number) =>
  http.get<unknown, DisputeDetail>(`/dispute/cases/${id}`)
export const getDisputeReviewersApi = (id: number) =>
  http.get<unknown, DisputeReviewer[]>(`/dispute/cases/${id}/reviewers`)
export const getDisputeReviewerCandidatesApi = (id: number, keyword?: string) =>
  http.get<unknown, DisputeReviewerCandidate[]>(`/dispute/cases/${id}/reviewer-candidates`, {
    params: keyword ? { keyword } : undefined,
  })
export const downloadDisputePackageApi = (id: number) =>
  http.get<unknown, Blob>(`/dispute/cases/${id}/package`, { responseType: 'blob' })
export const addDisputeReviewerApi = (id: number, userId: number) =>
  http.post<{ userId: number }, DisputeReviewer>(`/dispute/cases/${id}/reviewers`, { userId })
export const removeDisputeReviewerApi = (id: number, reviewerId: number) =>
  http.delete<unknown, void>(`/dispute/cases/${id}/reviewers/${reviewerId}`)
export const recuseDisputeApi = (id: number, reason: string) =>
  http.post<{ reason: string }, void>(`/dispute/cases/${id}/recusal`, { reason })
export const saveDisputeOpinionApi = (id: number, payload: { opinion: DisputeOpinion; comment: string }) =>
  http.post<typeof payload, DisputeOpinionRecord>(`/dispute/cases/${id}/opinions`, payload)
export const submitDisputeDecisionApi = (id: number, payload: { decision: DisputeOpinion; comment: string }) =>
  http.post<typeof payload, void>(`/dispute/cases/${id}/decision`, payload)
