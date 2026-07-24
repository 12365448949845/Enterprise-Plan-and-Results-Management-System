<template>
  <view class="page">
    <view class="page-header">
      <view class="title">成果提交</view>
      <view class="subtitle">记录成果内容和证明信息，提交后进入确认流程。</view>
    </view>

    <view class="panel">
      <view class="field">
        <text class="label">成果标题</text>
        <input v-model="form.title" class="input" placeholder="简要说明本次成果" />
      </view>
      <view class="field">
        <text class="label">成果日期</text>
        <input v-model="form.resultDate" class="input" placeholder="YYYY-MM-DD" />
      </view>
      <label class="switch-row">
        <switch :checked="form.temporary" @change="toggleTemporary" />
        <view><text class="switch-title">临时成果</text><text class="switch-description">不关联现有日计划时开启</text></view>
      </label>
      <view v-if="!form.temporary" class="field">
        <text class="label">关联日计划 ID</text>
        <input v-model.number="form.planId" class="input" type="number" placeholder="请输入日计划 ID" />
      </view>
      <view v-if="form.temporary" class="field">
        <text class="label">临时成果原因</text>
        <textarea v-model="form.temporaryReason" class="textarea small" placeholder="说明为什么未关联计划" />
      </view>
      <view class="field">
        <text class="label">成果说明</text>
        <textarea v-model="form.content" class="textarea" placeholder="说明成果内容、完成情况和证明信息" />
      </view>
      <button class="primary" :loading="saving" @click="save">保存草稿</button>
    </view>

    <view class="section-heading">
      <view class="section-title">我的成果</view>
      <view class="section-count">{{ results.length }} 条</view>
    </view>
    <view v-if="results.length === 0" class="empty">
      <text class="empty-title">暂无成果记录</text>
      <text class="empty-description">保存第一份草稿后会显示在这里。</text>
    </view>
    <view v-for="item in results" :key="item.id" class="card">
      <view class="card-head">
        <text class="card-title">{{ item.title }}</text>
        <text :class="['status', statusClass(item.status)]">{{ statusText(item.status) }}</text>
      </view>
      <view class="meta-row">
        <text>{{ item.resultDate }}</text>
        <text>{{ item.temporary ? '临时成果' : `日计划 #${item.planId}` }}</text>
      </view>
      <view class="content">{{ item.content }}</view>
      <button v-if="canSubmit(item.status)" class="ghost" @click="submit(item.id)">提交确认</button>
    </view>
  </view>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { createResultApi, myResultsApi, submitResultApi, type ResultItem } from '../../api/planning'

const saving = ref(false)
const results = ref<ResultItem[]>([])
const form = reactive({
  title: '',
  resultDate: today(),
  content: '',
  planId: undefined as number | undefined,
  temporary: false,
  temporaryReason: '',
})

function today() {
  return new Date().toISOString().slice(0, 10)
}

function statusText(status: string) {
  return ({ DRAFT: '草稿', PENDING: '待确认', CONFIRMED: '已确认', REJECTED: '已驳回' } as Record<string, string>)[status] || status
}

function canSubmit(status: string) {
  return status === 'DRAFT' || status === 'REJECTED'
}

function statusClass(status: string) {
  return `status-${status.toLowerCase()}`
}

function toggleTemporary(event: any) {
  form.temporary = Boolean(event.detail.value)
}

async function loadList() {
  results.value = await myResultsApi()
}

async function save() {
  saving.value = true
  try {
    await createResultApi({
      title: form.title,
      resultDate: form.resultDate,
      content: form.content,
      planType: form.temporary ? undefined : 'DAY',
      planId: form.temporary ? undefined : form.planId,
      temporary: form.temporary,
      temporaryReason: form.temporary ? form.temporaryReason : undefined,
    })
    uni.showToast({ title: '已保存', icon: 'success' })
    Object.assign(form, {
      title: '',
      resultDate: today(),
      content: '',
      planId: undefined,
      temporary: false,
      temporaryReason: '',
    })
    await loadList()
  } catch (err) {
    uni.showToast({ title: err instanceof Error ? err.message : '保存失败', icon: 'none' })
  } finally {
    saving.value = false
  }
}

async function submit(id: number) {
  try {
    await submitResultApi(id)
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

.textarea.small {
  height: 130rpx;
}

.switch-row {
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 18rpx 20rpx;
  border-radius: 16rpx;
  background: #f3f5f9;
}

.switch-row > view {
  display: grid;
  gap: 4rpx;
}

.switch-title {
  color: #39445a;
  font-size: 26rpx;
  font-weight: 650;
}

.switch-description {
  color: #667187;
  font-size: 22rpx;
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

.meta-row,
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

.status-confirmed {
  background: #e7f7ef;
  color: #176b4b;
}

.status-rejected {
  background: #fff0f1;
  color: #b92d38;
}

.meta-row {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  gap: 8rpx 20rpx;
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
