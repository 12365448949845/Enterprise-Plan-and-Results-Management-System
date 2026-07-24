const BASE_URL = 'http://localhost:48090/api'

export function request<T>(options: UniApp.RequestOptions) {
  return new Promise<T>((resolve, reject) => {
    const token = uni.getStorageSync('planning_access_token')
    uni.request({
      ...options,
      url: `${BASE_URL}${options.url}`,
      header: {
        ...(options.header || {}),
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      success: (response) => {
        const body = response.data as any
        if (body && body.code === 0) {
          resolve(body.data as T)
          return
        }
        if (response.statusCode === 401) {
          uni.removeStorageSync('planning_access_token')
          uni.removeStorageSync('planning_refresh_token')
          uni.redirectTo({ url: '/pages/login/index' })
          return
        }
        reject(new Error(body?.message || '请求失败'))
      },
      fail: reject,
    })
  })
}
