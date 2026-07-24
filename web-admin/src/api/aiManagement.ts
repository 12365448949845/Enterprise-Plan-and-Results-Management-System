import { http } from './http'

export interface AiPlanContext {
  id?: number
  orgId: number
  orgName: string
  planMonth: string
  departmentGoal: string
  leaderRequirement: string
  versionNo: number
  updatedAt: string
}

export interface AiModelConfig {
  id: number
  configName: string
  providerCode: 'MOCK' | 'OPENAI_COMPATIBLE' | 'ALIYUN'
  baseUrl: string
  apiKeyConfigured: boolean
  modelName: string
  timeoutSeconds: number
  globalEnabled: boolean
  draftEnabled: boolean
  optimizeEnabled: boolean
  checkEnabled: boolean
  allowedUserIds: string
  allowedOrgIds: string
  draftDailyLimit: number
  optimizeDailyLimit: number
  checkDailyLimit: number
  versionNo: number
  status: 'ENABLED' | 'DISABLED'
}

export type AiModelConfigPayload = Omit<AiModelConfig, 'id' | 'apiKeyConfigured' | 'status'> & { apiKey?: string }

export interface AiPrompt {
  id: number
  sceneCode: string
  versionNo: string
  systemPrompt: string
  userTemplate: string
  outputSchemaVersion: string
  status: string
  createdAt: string
}

export interface AiMetrics {
  totalCalls: number
  successCalls: number
  successRate: number
  inputTokens: number
  outputTokens: number
  averageLatencyMs: number
  adoptedSuggestions: number
  adoptionRate: number
  byScene: Array<Record<string, string | number>>
}

export function getLeaderAiContextApi(planMonth: string, orgId: number) {
  return http.get<unknown, AiPlanContext>('/leader/ai/month-plan-context', { params: { planMonth, orgId } })
}

export function saveLeaderAiContextApi(payload: Pick<AiPlanContext, 'orgId' | 'planMonth' | 'departmentGoal' | 'leaderRequirement' | 'versionNo'>) {
  return http.put<typeof payload, AiPlanContext>('/leader/ai/month-plan-context', payload)
}

export function listAiConfigsApi() {
  return http.get<unknown, AiModelConfig[]>('/system/ai/configs')
}

export function createAiConfigApi(payload: AiModelConfigPayload) {
  return http.post<typeof payload, AiModelConfig>('/system/ai/configs', payload)
}

export function updateAiConfigApi(id: number, payload: AiModelConfigPayload) {
  return http.put<typeof payload, AiModelConfig>(`/system/ai/configs/${id}`, payload)
}

export function testAiConnectionApi(id: number) {
  return http.post<undefined, { success: boolean; providerCode: string; modelName: string; latencyMs: number; message: string }>(`/system/ai/configs/${id}/test`)
}

export function enableAiConfigApi(id: number) {
  return http.post<undefined, AiModelConfig>(`/system/ai/configs/${id}/enable`)
}

export function listAiPromptsApi() {
  return http.get<unknown, AiPrompt[]>('/system/ai/prompts')
}

export function publishAiPromptApi(payload: { sceneCode: string; systemPrompt: string; userTemplate: string; outputSchemaVersion: string }) {
  return http.post<typeof payload, AiPrompt>('/system/ai/prompts/publish', payload)
}

export function getAiMetricsApi(days = 30) {
  return http.get<unknown, AiMetrics>('/system/ai/metrics', { params: { days } })
}
