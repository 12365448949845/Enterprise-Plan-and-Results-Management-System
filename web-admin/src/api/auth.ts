import { http } from './http'

export interface LoginResp {
  accessToken: string
  refreshToken: string
  expiresIn: number
  userId: number
  username: string
  realName: string
  forceChangePassword: boolean
  roles: string[]
  permissions: string[]
}

export interface AuthUser {
  userId: number
  username: string
  realName: string
  mobile: string
  deptId?: number
  groupId?: number
  forceChangePassword: boolean
  roles: string[]
  permissions: string[]
}

export function loginApi(username: string, password: string) {
  return http.post<unknown, LoginResp>('/auth/login', { username, password })
}

export function getMeApi() {
  return http.get<unknown, AuthUser>('/auth/me')
}

export function logoutApi() {
  return http.post('/auth/logout')
}

export function changePasswordApi(oldPassword: string, newPassword: string) {
  return http.post('/auth/change-password', { oldPassword, newPassword })
}
