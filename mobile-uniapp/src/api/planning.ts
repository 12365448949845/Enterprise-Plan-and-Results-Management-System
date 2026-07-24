import { request } from './http'

export interface DayPlan {
  id: number
  title: string
  planDate: string
  content: string
  status: string
}

export interface ResultItem {
  id: number
  title: string
  resultDate: string
  content: string
  planType: string
  planId?: number
  temporary: boolean
  temporaryReason?: string
  status: string
}

export function myDayPlansApi() {
  return request<DayPlan[]>({
    url: '/planning/day-plans/my',
    method: 'GET',
  })
}

export function createDayPlanApi(data: { title: string; planDate: string; content: string }) {
  return request<DayPlan>({
    url: '/planning/day-plans',
    method: 'POST',
    data,
  })
}

export function submitDayPlanApi(id: number) {
  return request<DayPlan>({
    url: `/planning/day-plans/${id}/submit`,
    method: 'POST',
  })
}

export function myResultsApi() {
  return request<ResultItem[]>({
    url: '/planning/results/my',
    method: 'GET',
  })
}

export function createResultApi(data: {
  title: string
  resultDate: string
  content: string
  planType?: 'DAY' | 'MONTH'
  planId?: number
  temporary: boolean
  temporaryReason?: string
}) {
  return request<ResultItem>({
    url: '/planning/results',
    method: 'POST',
    data,
  })
}

export function submitResultApi(id: number) {
  return request<ResultItem>({
    url: `/planning/results/${id}/submit`,
    method: 'POST',
  })
}
