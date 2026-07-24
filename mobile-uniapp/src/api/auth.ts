import { request } from './http'

export interface LoginResp {
  accessToken: string
  refreshToken: string
  userId: number
  username: string
  realName: string
  roles: string[]
  permissions: string[]
}

export interface AuthUser {
  userId: number
  username: string
  realName: string
  mobile: string
  roles: string[]
  permissions: string[]
}

export function loginApi(username: string, password: string) {
  return request<LoginResp>({
    url: '/auth/login',
    method: 'POST',
    data: { username, password },
  })
}

export function meApi() {
  return request<AuthUser>({
    url: '/auth/me',
    method: 'GET',
  })
}
