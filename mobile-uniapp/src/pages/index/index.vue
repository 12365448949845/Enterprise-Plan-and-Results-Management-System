<template>
  <view class="page">
    <view class="header">
      <view>
        <view class="kicker">员工工作台</view>
        <view class="hello">你好，{{ user?.realName || '同事' }}</view>
        <view class="sub">从今天的计划开始，及时沉淀可核验的工作成果。</view>
      </view>
      <button class="logout" @click="logout">退出</button>
    </view>

    <view class="section-title">快捷办理</view>
    <view class="grid">
      <button class="action-card primary-action" @click="go('/pages/day/index')">
        <text class="action-title">日计划</text>
        <text class="action-description">编制、保存并提交今天的工作安排</text>
        <text class="action-link">进入计划</text>
      </button>
      <button class="action-card secondary-action" @click="go('/pages/result/index')">
        <text class="action-title">成果提交</text>
        <text class="action-description">记录成果内容并关联对应计划</text>
        <text class="action-link">提交成果</text>
      </button>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { meApi, type AuthUser } from '../../api/auth'

const user = ref<AuthUser | null>(null)

onShow(async () => {
  const token = uni.getStorageSync('planning_access_token')
  if (!token) {
    uni.redirectTo({ url: '/pages/login/index' })
    return
  }
  user.value = await meApi()
})

function go(url: string) {
  uni.navigateTo({ url })
}

function logout() {
  uni.removeStorageSync('planning_access_token')
  uni.removeStorageSync('planning_refresh_token')
  uni.reLaunch({ url: '/pages/login/index' })
}
</script>

<style scoped>
.page {
  min-height: 100vh;
  padding: calc(env(safe-area-inset-top) + 36rpx) 28rpx calc(env(safe-area-inset-bottom) + 40rpx);
  background: #f4f6fa;
}

.header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24rpx;
  margin-bottom: 52rpx;
}

.kicker {
  margin-bottom: 10rpx;
  color: #3f5fd6;
  font-size: 24rpx;
  font-weight: 700;
}

.hello {
  color: #20283a;
  font-size: 44rpx;
  font-weight: 750;
  line-height: 1.25;
}

.sub {
  max-width: 500rpx;
  margin-top: 12rpx;
  color: #667187;
  font-size: 26rpx;
  line-height: 1.6;
}

.logout {
  flex: 0 0 auto;
  width: 120rpx;
  height: 68rpx;
  border: 1px solid #dce1ea;
  border-radius: 14rpx;
  background: #fefeff;
  color: #39445a;
  font-size: 24rpx;
}

.logout::after,
.action-card::after {
  border: 0;
}

.section-title {
  margin-bottom: 20rpx;
  color: #20283a;
  font-size: 28rpx;
  font-weight: 700;
}

.grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 20rpx;
}

.action-card {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  min-height: 220rpx;
  padding: 32rpx;
  border-radius: 20rpx;
  text-align: left;
}

.primary-action {
  background: #27365c;
  color: #f9fbff;
}

.secondary-action {
  border: 1px solid #dce1ea;
  background: #fefeff;
  color: #20283a;
}

.action-title {
  font-size: 34rpx;
  font-weight: 750;
}

.action-description {
  margin-top: 12rpx;
  color: #b8c1d4;
  font-size: 25rpx;
  line-height: 1.55;
}

.secondary-action .action-description {
  color: #667187;
}

.action-link {
  margin-top: auto;
  padding-top: 26rpx;
  color: #aebcff;
  font-size: 25rpx;
  font-weight: 700;
}

.secondary-action .action-link {
  color: #3f5fd6;
}

.action-card:active {
  opacity: 0.88;
}
</style>
