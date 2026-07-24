import { ElMessage } from 'element-plus'
import type { AiReview } from '@/api/aiReview'

export function notifyAiReviewResult(review: AiReview, successMessage: string) {
  if (review.status === 'SUCCESS') {
    const riskCount = review.result.issues?.length || 0
    const unknownCount = review.result.analysisDimensions
      ?.filter((item) => item.status === 'UNKNOWN').length || 0
    if (riskCount > 0) {
      ElMessage.warning(`AI检查完成，发现 ${riskCount} 项需要处理的问题，请查看报告。`)
    } else if (unknownCount > 0) {
      ElMessage.warning(`AI检查完成，其中 ${unknownCount} 项依据不足，请人工核对。`)
    } else {
      ElMessage.success(successMessage)
    }
    return
  }
  if (review.status === 'MODEL_FAILED') {
    ElMessage.warning(review.errorMessage || '系统硬规则预检已完成，但AI语义分析失败；当前结果不能视为检查通过。')
    return
  }
  ElMessage.warning('当前只完成系统硬规则预检，AI语义分析未执行；字段填完整不代表内容合理。')
}
