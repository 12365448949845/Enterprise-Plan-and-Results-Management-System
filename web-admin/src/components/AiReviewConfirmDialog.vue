<template>
  <el-dialog
    :model-value="modelValue"
    title="提交前AI检查"
    width="min(860px, calc(100vw - 32px))"
    destroy-on-close
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <AiReviewPanel :review="review" />
    <template #footer>
      <el-button :disabled="confirming" @click="$emit('update:modelValue', false)">返回修改</el-button>
      <el-button :type="confirmButtonType" :loading="confirming" :disabled="!review || review.stale" @click="$emit('confirm')">
        {{ confirmButtonText }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { AiReview } from '@/api/aiReview'
import AiReviewPanel from './AiReviewPanel.vue'

const props = defineProps<{
  modelValue: boolean
  review: AiReview | null
  confirming?: boolean
}>()

const unknownCount = computed(() => props.review?.result.analysisDimensions
  ?.filter((item) => item.status === 'UNKNOWN').length || 0)
const needsAcknowledgement = computed(() => props.review?.status !== 'SUCCESS'
  || props.review?.overallRisk === 'HIGH' || props.review?.overallRisk === 'MEDIUM'
  || unknownCount.value > 0)
const confirmButtonType = computed(() => needsAcknowledgement.value ? 'warning' : 'primary')
const confirmButtonText = computed(() => {
  if (!props.review) return '等待检查结果'
  if (props.review.stale) return '检查结果已过期'
  if (props.review.status !== 'SUCCESS') return 'AI未完成，仍然提交'
  if (props.review.overallRisk === 'HIGH' || props.review.overallRisk === 'MEDIUM') return '确认风险并继续提交'
  if (unknownCount.value > 0) return '确认依据不足并继续提交'
  return '确认并继续提交'
})

defineEmits<{
  'update:modelValue': [value: boolean]
  confirm: []
}>()
</script>
