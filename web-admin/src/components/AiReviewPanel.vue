<template>
  <section class="ai-review" :class="[`is-${review?.overallRisk?.toLowerCase() || 'empty'}`, { compact, 'is-semantic-incomplete': review && review.status !== 'SUCCESS', 'is-stale': isStale }]" aria-live="polite">
    <div class="ai-review__head">
      <div class="ai-review__intro">
        <div class="ai-review__title-row">
          <span class="ai-review__brand" aria-hidden="true">AI</span>
          <h3>{{ title }}</h3>
          <el-tag v-if="showRiskTag" :type="riskMeta.type">{{ riskMeta.label }}</el-tag>
          <el-tag v-if="!isStale && unknownDimensionCount" type="warning" effect="plain">{{ unknownDimensionCount }}项依据不足</el-tag>
          <el-tag v-if="review || capability" effect="plain" :type="statusMeta.type">{{ statusMeta.label }}</el-tag>
        </div>
        <p v-if="review">{{ isStale ? '业务内容或检查依据已经变化，原报告不再对应当前内容，请重新执行AI检查。' : review.result.summary }}</p>
        <p v-else>{{ emptyText }}</p>
        <small v-if="!review && capability" class="ai-review__capability">{{ capability.message }}</small>
      </div>
      <div class="ai-review__head-actions">
        <slot name="actions" />
        <small v-if="review">{{ checkedAtText }}</small>
      </div>
    </div>

    <template v-if="review && displayReport">
      <el-alert
        v-if="isStale"
        class="ai-review__failure"
        type="warning"
        :closable="false"
        show-icon
        title="当前报告已过期，不能作为本次提交依据。"
        description="重新检查后，系统会生成与当前业务内容和判断依据一致的新报告。"
      />
      <el-alert
        v-else-if="review.status === 'MODEL_FAILED'"
        class="ai-review__failure"
        type="error"
        :closable="false"
        show-icon
        title="AI模型本次调用失败，没有生成语义检查结果，请重新检查。"
        :description="review.errorMessage || '若重新检查后仍失败，请联系系统管理员查看AI调用日志。'"
      />
      <el-alert
        v-else-if="review.status === 'RULE_ONLY'"
        class="ai-review__failure"
        type="warning"
        :closable="false"
        show-icon
        title="AI语义检查本次未执行。"
        :description="review.errorMessage || '当前仅完成系统确定性规则检查。'"
      />

      <div v-if="!isStale && review.status !== 'SUCCESS' && ruleIssues.length" class="ai-review__rule-preview">
        <h4>系统预检仍发现 {{ ruleIssues.length }} 项问题</h4>
        <p>以下是日期、权重、必填项等确定性检查结果，不代表AI语义分析已经完成。</p>
        <ul>
          <li v-for="issue in ruleIssues" :key="`${issue.code}-${issue.field}`">
            <strong>{{ issue.title }}</strong><span>{{ issue.suggestion || issue.basis }}</span>
          </li>
        </ul>
      </div>

      <div v-if="!isStale && review.status === 'SUCCESS' && analysisDimensions.length" class="ai-review__dimensions">
        <div class="ai-review__section-title">
          <h4>逐维度分析依据</h4>
          <small>{{ completedDimensionCount }}/{{ analysisDimensions.length }} 项形成有效语义结论</small>
        </div>
        <article v-for="item in analysisDimensions" :key="item.ruleId" class="ai-review__dimension">
          <div class="ai-review__dimension-head">
            <el-tag size="small" :type="dimensionMeta(item.status).type">{{ dimensionMeta(item.status).label }}</el-tag>
            <strong>{{ item.title }}</strong>
          </div>
          <p>{{ item.conclusion }}</p>
          <dl>
            <div><dt>判断依据</dt><dd>{{ item.basis }}</dd></div>
            <div v-if="item.quote"><dt>引用原文</dt><dd>{{ item.quote }}</dd></div>
            <div v-if="item.references?.length"><dt>来源位置</dt><dd>{{ item.references.join('、') }}</dd></div>
          </dl>
        </article>
      </div>

      <div v-if="!isStale && review.status === 'SUCCESS' && hasCompletionRange" class="ai-review__completion">
        <span>员工申报</span>
        <strong>{{ review.result.declaredCompletionRate ?? '-' }}%</strong>
        <span>证据建议区间</span>
        <strong>{{ review.result.suggestedCompletionMin }}%～{{ review.result.suggestedCompletionMax }}%</strong>
        <el-tag :type="evidenceMeta.type">{{ evidenceMeta.label }}</el-tag>
        <small v-if="review.result.completionCalculationBasis">{{ review.result.completionCalculationBasis }} 仅供人工参考。</small>
      </div>

      <div v-if="!isStale && review.status === 'SUCCESS' && issues.length" class="ai-review__issues">
        <h4>需要处理的问题</h4>
        <article v-for="issue in issues" :key="`${issue.code}-${issue.field}`" class="ai-review__issue">
          <div class="ai-review__issue-head">
            <el-tag size="small" :type="severityMeta(issue.severity).type">{{ severityMeta(issue.severity).label }}</el-tag>
            <strong>{{ issue.title }}</strong>
          </div>
          <dl>
            <div><dt>引用原文</dt><dd>{{ issue.quote || '未提供' }}</dd></div>
            <div><dt>来源位置</dt><dd>{{ issue.references.join('、') || '未提供' }}</dd></div>
            <div><dt>判断理由</dt><dd>{{ issue.basis }}</dd></div>
            <div v-if="issue.suggestion"><dt>修改建议</dt><dd>{{ issue.suggestion }}</dd></div>
          </dl>
          <div class="ai-review__meta">
            <span>{{ issue.source === 'RULE' ? '系统规则' : '千问语义判断' }}</span>
          </div>
        </article>
      </div>
      <div v-else-if="!isStale && review.status === 'SUCCESS' && unknownDimensionCount" class="ai-review__unknown-summary">
        <strong>未发现已经确认的风险</strong>
        <p>仍有 {{ unknownDimensionCount }} 项因依据不足无法判断，不能视为全部检查通过，请结合原计划人工核对。</p>
      </div>
      <el-empty v-else-if="!isStale && review.status === 'SUCCESS'" :image-size="52" description="逐项语义分析已完成，未发现需要提示的问题" />

      <div v-if="!isStale && review.status === 'SUCCESS' && review.result.acceptanceCoverage.length" class="ai-review__coverage">
        <h4>验收项证据覆盖</h4>
        <el-table :data="review.result.acceptanceCoverage" border>
          <el-table-column prop="criterion" label="验收项" min-width="180" />
          <el-table-column label="覆盖状态" width="105">
            <template #default="{ row }">
              <el-tag size="small" :type="coverageMeta(row.status).type">{{ coverageMeta(row.status).label }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="basis" label="判断依据" min-width="220" />
          <el-table-column prop="evidenceQuote" label="证据原文" min-width="200">
            <template #default="{ row }">{{ row.evidenceQuote || '无可核验原文' }}</template>
          </el-table-column>
          <el-table-column label="证据位置" min-width="180">
            <template #default="{ row }">{{ row.evidenceReferences?.join('、') || '未找到' }}</template>
          </el-table-column>
        </el-table>
      </div>
      <p v-if="!isStale && review.status === 'SUCCESS'" class="ai-review__notice">AI检查结论仅供计划完善和人工审批参考，不替代负责人判断。</p>
    </template>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { getAiCapabilityApi, type AiAcceptanceCoverage, type AiAnalysisDimension, type AiCapability, type AiIssueSeverity, type AiReview } from '@/api/aiReview'

const props = withDefaults(defineProps<{
  review?: AiReview | null
  title?: string
  emptyText?: string
  compact?: boolean
  displayReport?: boolean
  showCapability?: boolean
  stale?: boolean
}>(), {
  review: null,
  title: 'AI检查结果',
  emptyText: '尚未执行AI检查。',
  compact: false,
  displayReport: true,
  showCapability: true,
  stale: false,
})

const capability = ref<AiCapability | null>(null)

onMounted(async () => {
  if (!props.showCapability) return
  try {
    capability.value = await getAiCapabilityApi()
  } catch {
    capability.value = null
  }
})

const issues = computed(() => props.review?.result.issues || [])
const isStale = computed(() => props.stale || Boolean(props.review?.stale))
const ruleIssues = computed(() => issues.value.filter((issue) => issue.source === 'RULE'))
const unknownDimensionCount = computed(() => analysisDimensions.value.filter((item) => item.status === 'UNKNOWN').length)
const riskMeta = computed(() => {
  const meta = ({
    LOW: { label: '低风险', type: 'success' as const },
    MEDIUM: { label: '中风险', type: 'warning' as const },
    HIGH: { label: '高风险', type: 'danger' as const },
  })[props.review?.overallRisk || 'LOW']
  return props.review?.status === 'SUCCESS' ? meta : { ...meta, label: `${meta.label}（系统规则）` }
})

const showRiskTag = computed(() => Boolean(props.review)
  && !isStale.value
  && (props.review?.status === 'SUCCESS' || issues.value.length > 0)
  && !(props.review?.status === 'SUCCESS' && !issues.value.length && unknownDimensionCount.value > 0))

const statusMeta = computed(() => {
  if (props.review && isStale.value) {
    return { label: '结果已过期', type: 'warning' as const }
  }
  if (props.review) {
    return ({
      SUCCESS: { label: `千问 · ${props.review.modelName || ''}`, type: 'success' as const },
      RULE_ONLY: { label: 'AI语义未执行', type: 'warning' as const },
      MODEL_FAILED: { label: 'AI分析失败', type: 'danger' as const },
    })[props.review.status]
  }
  return capability.value?.modelEnabled
    ? { label: `千问 · ${capability.value.modelName}`, type: 'success' as const }
    : { label: 'AI语义未启用', type: 'warning' as const }
})

const analysisDimensions = computed<AiAnalysisDimension[]>(() => {
  if (!props.review || props.review.status !== 'SUCCESS') return []
  const stored = props.review?.result.analysisDimensions || []
  if (stored.length) return stored
  return [{
    ruleId: 'AI_SEMANTIC',
    title: 'AI语义分析',
    status: 'UNKNOWN',
    conclusion: '当前记录未保存逐维度分析结果。',
    quote: '',
    basis: '这是旧版检查记录，请重新执行AI检查以生成逐维度判断依据。',
    confidence: 1,
    references: [],
  }]
})
const completedDimensionCount = computed(() => analysisDimensions.value
  .filter((item) => item.status === 'PASS' || item.status === 'RISK').length)

const checkedAtText = computed(() => props.review?.checkedAt?.replace('T', ' ').slice(0, 16) || '')
const hasCompletionRange = computed(() => props.review?.result.suggestedCompletionMin !== null
  && props.review?.result.suggestedCompletionMin !== undefined
  && props.review?.result.suggestedCompletionMax !== null
  && props.review?.result.suggestedCompletionMax !== undefined)

const evidenceMeta = computed(() => ({
  SUFFICIENT: { label: '证据充分', type: 'success' as const },
  PARTIAL: { label: '部分充分', type: 'warning' as const },
  INSUFFICIENT: { label: '证据不足', type: 'danger' as const },
  UNKNOWN: { label: '无法判断', type: 'info' as const },
}[props.review?.result.evidenceStatus || 'UNKNOWN']))

function severityMeta(value: AiIssueSeverity) {
  return ({
    LOW: { label: '低', type: 'info' as const },
    MEDIUM: { label: '中', type: 'warning' as const },
    HIGH: { label: '高', type: 'danger' as const },
    BLOCKING: { label: '阻断', type: 'danger' as const },
  })[value] || { label: value, type: 'info' as const }
}

function coverageMeta(value: AiAcceptanceCoverage['status']) {
  return ({
    PROVEN: { label: '已证明', type: 'success' as const },
    PARTIAL: { label: '部分证明', type: 'warning' as const },
    UNPROVEN: { label: '未证明', type: 'danger' as const },
    UNKNOWN: { label: '无法判断', type: 'info' as const },
  })[value]
}

function dimensionMeta(value: AiAnalysisDimension['status']) {
  return ({
    PASS: { label: '通过', type: 'success' as const },
    RISK: { label: '有风险', type: 'danger' as const },
    UNKNOWN: { label: '依据不足', type: 'warning' as const },
    NOT_RUN: { label: '未执行', type: 'info' as const },
  })[value]
}
</script>

<style scoped>
.ai-review {
  padding: 18px;
  border: 1px solid var(--line);
  border-radius: 12px;
  background: #fffdf8;
}

.ai-review.is-medium { border-color: #e2c9a8; background: #fffaf2; }
.ai-review.is-high { border-color: #e3b6ad; background: #fff7f5; }
.ai-review.is-semantic-incomplete { border-color: #e2c9a8; background: #fffaf2; }
.ai-review.is-stale { border-color: #d4c7a8; background: #fffaf0; }
.ai-review.compact { padding: 14px; }
.ai-review__head, .ai-review__title-row, .ai-review__issue-head, .ai-review__dimension-head, .ai-review__completion, .ai-review__meta, .ai-review__section-title { display: flex; align-items: center; }
.ai-review__head { justify-content: space-between; gap: 18px; }
.ai-review__intro { min-width: 0; }
.ai-review__title-row { flex-wrap: wrap; gap: 8px; }
.ai-review__brand { display: inline-grid; width: 30px; height: 30px; place-items: center; border-radius: 8px; color: #fff; background: var(--navy-800); font-family: "IBM Plex Mono", "Cascadia Mono", monospace; font-size: 11px; font-weight: 800; letter-spacing: 0; }
.ai-review__title-row h3 { margin: 0; color: var(--ink); font-size: 16px; }
.ai-review__head p { max-width: 72ch; margin: 7px 0 0; color: #5c6f69; font-size: 12px; line-height: 1.65; }
.ai-review__head small { flex: 0 0 auto; color: var(--muted); font-size: 11px; }
.ai-review__capability { display: block; max-width: 78ch; margin-top: 5px; line-height: 1.55; }
.ai-review__head-actions { display: flex; flex: 0 0 auto; align-items: center; gap: 10px; }
.ai-review__failure { margin-top: 14px; }
.ai-review__rule-preview { margin-top: 14px; padding: 13px 14px; border: 1px solid #ead8b9; border-radius: 9px; background: #fffaf1; }
.ai-review__rule-preview h4 { margin: 0; color: var(--ink); font-size: 13px; }
.ai-review__rule-preview > p { margin: 5px 0 0; color: #766854; font-size: 11px; line-height: 1.55; }
.ai-review__rule-preview ul { display: grid; gap: 7px; margin: 10px 0 0; padding: 0; list-style: none; }
.ai-review__rule-preview li { display: grid; gap: 3px; }
.ai-review__rule-preview li strong { color: #584a37; font-size: 12px; }
.ai-review__rule-preview li span { color: #766854; font-size: 11px; line-height: 1.5; }
.ai-review__completion { flex-wrap: wrap; gap: 9px 12px; margin-top: 14px; padding: 12px 14px; border: 1px solid #d7e1dc; border-radius: 9px; background: #f4f8f5; }
.ai-review__completion span { color: var(--muted); font-size: 11px; }
.ai-review__completion strong { color: var(--ink); font-size: 14px; }
.ai-review__completion small { flex-basis: 100%; color: var(--muted); font-size: 11px; line-height: 1.55; }
.ai-review__dimensions { margin-top: 16px; border-top: 1px solid #dce3de; }
.ai-review__section-title { justify-content: space-between; gap: 14px; padding: 14px 0 7px; }
.ai-review__section-title h4, .ai-review__issues > h4 { margin: 0; color: var(--ink); font-size: 14px; }
.ai-review__section-title small { color: var(--muted); font-size: 11px; }
.ai-review__dimension { padding: 12px 0; border-top: 1px solid #e4e9e5; }
.ai-review__dimension:first-of-type { border-top: 0; }
.ai-review__dimension-head { flex-wrap: wrap; gap: 8px; }
.ai-review__dimension-head strong { color: var(--ink); font-size: 13px; }
.ai-review__dimension > p { margin: 8px 0 0; color: #40534e; font-size: 12px; font-weight: 650; line-height: 1.55; }
.ai-review__dimension dl { display: grid; gap: 6px; margin: 9px 0 0; }
.ai-review__dimension dl div { display: grid; grid-template-columns: 72px minmax(0, 1fr); gap: 10px; }
.ai-review__dimension dt { color: var(--muted); font-size: 11px; }
.ai-review__dimension dd { margin: 0; color: #52645f; font-size: 11px; line-height: 1.55; overflow-wrap: anywhere; }
.ai-review__issues { display: grid; gap: 10px; margin-top: 14px; }
.ai-review__issues > h4 { margin-bottom: 2px; }
.ai-review__issue { padding: 13px 14px; border: 1px solid #dde4df; border-radius: 9px; background: rgb(255 255 255 / 70%); }
.ai-review__issue-head { flex-wrap: wrap; gap: 8px; }
.ai-review__issue-head strong { color: var(--ink); font-size: 13px; }
.ai-review__issue dl { display: grid; gap: 7px; margin: 11px 0 0; }
.ai-review__issue dl div { display: grid; grid-template-columns: 72px minmax(0, 1fr); gap: 10px; }
.ai-review__issue dt { color: var(--muted); font-size: 11px; }
.ai-review__issue dd { margin: 0; color: #40534e; font-size: 12px; line-height: 1.55; overflow-wrap: anywhere; }
.ai-review__meta { flex-wrap: wrap; gap: 12px; margin-top: 10px; color: #687b75; font-size: 11px; }
.ai-review__unknown-summary { margin-top: 14px; padding: 13px 14px; border: 1px solid #e5d4af; border-radius: 9px; background: #fffaf0; }
.ai-review__unknown-summary strong { color: #5b4c31; font-size: 13px; }
.ai-review__unknown-summary p { margin: 5px 0 0; color: #6d6048; font-size: 12px; line-height: 1.6; }
.ai-review__notice { margin: 16px 0 0; padding-top: 11px; border-top: 1px solid #dce3de; color: #687b75; font-size: 11px; line-height: 1.55; }
.ai-review__coverage { margin-top: 17px; }
.ai-review__coverage h4 { margin: 0 0 10px; color: var(--ink); font-size: 14px; }

@media (max-width: 680px) {
  .ai-review__head { align-items: flex-start; flex-direction: column; gap: 7px; }
  .ai-review__head-actions { width: 100%; justify-content: space-between; }
  .ai-review__issue dl div { grid-template-columns: 1fr; gap: 3px; }
  .ai-review__dimension dl div { grid-template-columns: 1fr; gap: 3px; }
}
</style>
