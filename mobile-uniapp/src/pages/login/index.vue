<template>
  <view class="page">
    <view class="panel">
      <view class="brand-mark">计</view>
      <view class="kicker">成果计划系统</view>
      <view class="title">登录工作台</view>
      <view class="subtitle">进入计划、成果与审批工作流程。</view>

      <view class="form">
        <view class="field">
          <text class="label">账号</text>
          <input v-model="username" class="input" placeholder="请输入账号" />
        </view>
        <view class="field">
          <text class="label">密码</text>
          <input v-model="password" class="input" placeholder="请输入密码" password />
        </view>
      </view>
      <view v-if="error" class="error">{{ error }}</view>
      <button class="primary" :loading="loading" @click="submit">登录</button>
      <view class="tip">开发默认账号 admin / Admin@123456</view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { loginApi } from '../../api/auth'

const username = ref('admin')
const password = ref('Admin@123456')
const loading = ref(false)
const error = ref('')

async function submit() {
  error.value = ''
  loading.value = true
  try {
    const data = await loginApi(username.value, password.value)
    uni.setStorageSync('planning_access_token', data.accessToken)
    uni.setStorageSync('planning_refresh_token', data.refreshToken)
    uni.reLaunch({ url: '/pages/index/index' })
  } catch (err) {
    error.value = err instanceof Error ? err.message : '登录失败'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.page {
  min-height: 100vh;
  padding: calc(env(safe-area-inset-top) + 48rpx) 28rpx calc(env(safe-area-inset-bottom) + 40rpx);
  background: #f4f6fa;
}

.panel {
  box-sizing: border-box;
  width: 100%;
  max-width: 680rpx;
  margin: 8vh auto 0;
  padding: 44rpx 36rpx;
  border: 1px solid #dce1ea;
  border-radius: 24rpx;
  background: #fefeff;
}

.brand-mark {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 72rpx;
  height: 72rpx;
  margin-bottom: 28rpx;
  border-radius: 18rpx;
  background: #3f5fd6;
  color: #f9fbff;
  font-size: 34rpx;
  font-weight: 800;
}

.kicker {
  color: #3f5fd6;
  font-size: 24rpx;
  font-weight: 700;
}

.title {
  margin-top: 10rpx;
  color: #20283a;
  font-size: 44rpx;
  font-weight: 750;
  line-height: 1.25;
}

.subtitle {
  margin-top: 12rpx;
  color: #667187;
  font-size: 26rpx;
  line-height: 1.6;
}

.form {
  display: grid;
  gap: 24rpx;
  margin-top: 44rpx;
}

.field {
  display: grid;
  gap: 12rpx;
}

.label {
  color: #39445a;
  font-size: 26rpx;
  font-weight: 650;
}

.input {
  box-sizing: border-box;
  width: 100%;
  height: 88rpx;
  padding: 0 24rpx;
  border: 1px solid #dce1ea;
  border-radius: 16rpx;
  background: #fefeff;
  color: #20283a;
  font-size: 28rpx;
}

.primary {
  height: 88rpx;
  margin-top: 28rpx;
  border-radius: 16rpx;
  background: #3f5fd6;
  color: #f9fbff;
  font-size: 28rpx;
  font-weight: 700;
}

.primary::after {
  border: 0;
}

.primary:active {
  background: #324cac;
}

.error {
  margin-top: 24rpx;
  padding: 18rpx 20rpx;
  border-radius: 12rpx;
  background: #fff0f1;
  color: #b92d38;
  font-size: 24rpx;
  line-height: 1.5;
}

.tip {
  margin-top: 24rpx;
  color: #667187;
  font-size: 24rpx;
  line-height: 1.5;
  text-align: center;
}
</style>
