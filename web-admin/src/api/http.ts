import axios from 'axios'

export const AI_OPERATION_TIMEOUT_MS = 75_000

export const http = axios.create({
  baseURL: '/api',
  timeout: 15000,
})

http.interceptors.request.use((config) => {
  const token = localStorage.getItem('planning_access_token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

http.interceptors.response.use((response) => {
  const body = response.data
  if (body && typeof body.code !== 'undefined') {
    if (body.code !== 0) {
      return Promise.reject(new Error(body.message || '请求失败'))
    }
    return body.data
  }
  return body
}, (error) => {
  if (error?.response?.status === 401) {
    localStorage.removeItem('planning_access_token')
    localStorage.removeItem('planning_refresh_token')
    window.location.href = '/login'
  }
  const message = error?.response?.data?.message || error?.message || '请求失败'
  return Promise.reject(new Error(message))
})
