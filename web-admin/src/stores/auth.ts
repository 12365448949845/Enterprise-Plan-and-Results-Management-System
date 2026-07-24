import { defineStore } from 'pinia'
import { changePasswordApi, getMeApi, loginApi, logoutApi, type AuthUser } from '@/api/auth'

interface AuthState {
  token: string
  refreshToken: string
  user: AuthUser | null
}

export const useAuthStore = defineStore('auth', {
  state: (): AuthState => ({
    token: localStorage.getItem('planning_access_token') || '',
    refreshToken: localStorage.getItem('planning_refresh_token') || '',
    user: null,
  }),
  actions: {
    async login(username: string, password: string) {
      const data = await loginApi(username, password)
      this.token = data.accessToken
      this.refreshToken = data.refreshToken
      localStorage.setItem('planning_access_token', data.accessToken)
      localStorage.setItem('planning_refresh_token', data.refreshToken)
      await this.loadMe()
    },
    async loadMe() {
      if (!this.token) return
      this.user = await getMeApi()
    },
    async changePassword(oldPassword: string, newPassword: string) {
      await changePasswordApi(oldPassword, newPassword)
      await this.logout()
    },
    async logout() {
      try {
        await logoutApi()
      } finally {
        this.token = ''
        this.refreshToken = ''
        this.user = null
        localStorage.removeItem('planning_access_token')
        localStorage.removeItem('planning_refresh_token')
      }
    },
  },
})
