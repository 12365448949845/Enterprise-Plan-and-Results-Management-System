<template>
  <view class="page">
    <view class="page-header">
      <view class="title">日计划</view>
      <view class="subtitle">明确今天要完成的事项，保存后可继续补充并提交审批。</view>
    </view>

    <view class="panel">
      <view class="field">
        <text class="label">计划标题</text>
        <input v-model="form.title" class="input" placeholder="简要说明今天的重点" />
      </view>
      <view class="field">
        <text class="label">计划日期</text>
        <input v-model="form.planDate" class="input" placeholder="YYYY-MM-DD" />
      </view>
      <view class="field">
        <text class="label">计划内容</text>
        <textarea v-model="form.content" class="textarea" placeholder="说明要完成的工作和预期结果" />
      </view>
      <button class="primary" :loading="saving" @click="save">保存草稿</button>
    </view>

    <view class="section-heading">
      <view class="section-title">我的日计划</view>
      <view class="section-count">{{ plans.length }} 条</view>
    </view>
    <view v-if="plans.length === 0" class="empty">
      <text class="empty-title">暂无日计划</text>
      <text class="empty-description">保存第一份草稿后会显示在这里。</text>
    </view>
    <view v-for="item in plans" :key="item.id" class="card">
      <view class="card-head">
        <text class="card-title">{{ item.title }}</text>
        <text :class="['status', statusClass(item.status)]">{{ statusText(item.status) }}</text>
      </view>
      <view class="date">计划日期 {{ item.planDate }}</view>
      <view class="content">{{ item.content }}</view>
      <button v-if="canSubmit(item.status)" class="ghost" @click="submit(item.id)">提交审批</button>
    </view>
  </view>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { createDayPlanApi, myDayPlansApi, submitDayPlanApi, type DayPlan } from '../../api/planning'

const saving = ref(false)
const plans = ref<DayPlan[]>([])
const form = reactive({
  title: '',
  planDate: today(),
  content: '',
})

function today() {
  return new Date().toISOString().slice(0, 10)
}

function statusText(status: string) {
  return ({ DRAFT: '草稿', PENDING: '待审批', APPROVED: '已通过', REJECTED: '已驳回' } as Record<string, string>)[status] || status
}

function canSubmit(status: string) {
  return status === 'DRAFT' || status === 'REJECTED'
}

function statusClass(status: string) {
  return `status-${status.toLowerCase()}`
}

async function loadList() {
  plans.value = await myDayPlansApi()
}

async function save() {
  saving.value = true
  try {
    await createDayPlanApi({ title: form.title, planDate: form.planDate, content: form.content })
    uni.showToast({ title: '已保存', icon: 'success' })
    Object.assign(form, { title: '', planDate: today(), content: '' })
    await loadList()
  } catch (err) {
    uni.showToast({ title: err instanceof Error ? err.message : '保存失败', icon: 'none' })
  } finally {
    saving.value = false
  }
}

async function submit(id: number) {
  try {
    await submitDayPlanApi(id)
    uni.showToast({ title: '已提交', icon: 'success' })
    await loadList()
  } catch (err) {
    uni.showToast({ title: err instanceof Error ? err.message : '提交失败', icon: 'none' })
  }
}

onShow(loadList)
</script>

<style scoped>
.page {
  min-height: 100vh;
  padding: calc(env(safe-area-inset-top) + 36rpx) 28rpx calc(env(safe-area-inset-bottom) + 40rpx);
  background: #f4f6fa;
}

.page-header {
  margin-bottom: 28rpx;
}

.title {
  color: #20283a;
  font-size: 42rpx;
  font-weight: 750;
  line-height: 1.25;
}

.subtitle {
  margin-top: 10rpx;
  color: #667187;
  font-size: 25rpx;
  line-height: 1.6;
}

.section-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 40rpx 0 20rpx;
}

.section-title {
  color: #20283a;
  font-size: 30rpx;
  font-weight: 700;
}

.section-count {
  color: #667187;
  font-size: 24rpx;
}

.panel,
.card,
.empty {
  border: 1px solid #dce1ea;
  border-radius: 20rpx;
  background: #fefeff;
}

.panel {
  display: grid;
  gap: 24rpx;
  padding: 30rpx;
}

.field {
  display: grid;
  gap: 12rpx;
}

.label {
  color: #39445a;
  font-size: 25rpx;
  font-weight: 650;
}

.input,
.textarea {
  box-sizing: border-box;
  width: 100%;
  padding: 0 22rpx;
  border: 1px solid #dce1ea;
  border-radius: 16rpx;
  background: #fefeff;
  color: #20283a;
  font-size: 27rpx;
}

.input {
  height: 84rpx;
}

.textarea {
  height: 190rpx;
  padding-top: 20rpx;
  line-height: 1.6;
}

.primary,
.ghost {
  height: 82rpx;
  border-radius: 16rpx;
  font-size: 26rpx;
  font-weight: 700;
}

.primary::after,
.ghost::after {
  border: 0;
}

.primary {
  margin-top: 4rpx;
  background: #3f5fd6;
  color: #f9fbff;
}

.card {
  margin-bottom: 20rpx;
  padding: 26rpx;
}

.card-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20rpx;
}

.card-title {
  flex: 1;
  color: #20283a;
  font-size: 29rpx;
  font-weight: 720;
  line-height: 1.45;
}

.date,
.content {
  color: #667187;
  font-size: 24rpx;
}

.status {
  flex: 0 0 auto;
  padding: 6rpx 12rpx;
  border-radius: 10rpx;
  background: #eef1f6;
  color: #566176;
  font-size: 22rpx;
  font-weight: 650;
}

.status-pending {
  background: #fff5dc;
  color: #8a5708;
}

.status-approved {
  background: #e7f7ef;
  color: #176b4b;
}

.status-rejected {
  background: #fff0f1;
  color: #b92d38;
}

.date {
  margin-top: 12rpx;
}

.content {
  margin: 18rpx 0;
  line-height: 1.6;
}

.ghost {
  border: 1px solid #cfd7f6;
  background: #eef1ff;
  color: #3f5fd6;
}

.empty {
  display: grid;
  gap: 8rpx;
  padding: 42rpx 28rpx;
  text-align: center;
}

.empty-title {
  color: #39445a;
  font-size: 27rpx;
  font-weight: 700;
}

.empty-description {
  color: #667187;
  font-size: 24rpx;
  line-height: 1.5;
}
</style>
