<template>
  <section v-loading="loading" class="page-panel">
    <div class="page-header">
      <div>
        <h1 class="page-title">参考分规则</h1>
        <p class="page-subtitle">配置部门参考分试算规则，参考分用于解释依据，不替代最终绩效裁量。</p>
      </div>
      <div class="toolbar">
        <el-tooltip :disabled="!trialBlocked" :content="trialBlockReason" placement="bottom">
          <span>
            <el-button :icon="DataAnalysis" :loading="trialLoading" :disabled="trialBlocked" @click="trial">试算</el-button>
          </span>
        </el-tooltip>
        <el-button type="primary" :icon="Check" :loading="saving" :disabled="!weightValid || !orgOptions.length" @click="save">保存并启用</el-button>
      </div>
    </div>

    <div class="filter-bar">
      <el-date-picker v-model="query.month" type="month" value-format="YYYY-MM" :clearable="false" placeholder="生效月份" />
      <el-select
        v-model="selectedOrgId"
        :loading="orgLoading"
        :disabled="!orgOptions.length"
        placeholder="暂无授权组织"
        @change="refresh"
      >
        <el-option v-for="item in orgOptions" :key="item.value" :label="item.label" :value="item.value" />
      </el-select>
      <el-tag :type="weightValid ? 'success' : 'danger'">启用权重 {{ totalWeight }} / 100</el-tag>
      <el-tag :type="currentRuleStatus === 'ENABLED' ? 'success' : 'info'">{{ currentRuleStatusText }}</el-tag>
      <el-tag v-if="requiresMigration" type="warning">旧版规则待保存迁移</el-tag>
    </div>

    <el-table :data="rows" border>
      <el-table-column prop="factor" label="因素" width="130" />
      <el-table-column prop="rule" label="规则" min-width="220" />
      <el-table-column label="权重" width="260">
        <template #default="{ row }">
          <el-slider v-model="row.weight" :min="0" :max="100" show-input :disabled="saving" />
        </template>
      </el-table-column>
      <el-table-column prop="note" label="说明" min-width="160" />
      <el-table-column label="启用" width="100">
        <template #default="{ row }">
          <el-switch v-model="row.enabled" :disabled="saving" />
        </template>
      </el-table-column>
    </el-table>

    <div class="dashboard-grid mt16">
      <div class="section-card">
        <div class="section-title">试算输入</div>
        <el-form label-position="top">
          <el-form-item label="员工">
            <el-input v-model="trialForm.employee" clearable placeholder="可选，仅用于标识本次试算" />
          </el-form-item>
          <el-form-item label="完成比例">
            <el-slider v-model="trialForm.completion" :min="0" :max="100" show-input />
          </el-form-item>
          <el-form-item label="逾期次数">
            <el-input-number v-model="trialForm.overdue" :min="0" controls-position="right" />
          </el-form-item>
          <el-form-item label="驳回次数">
            <el-input-number v-model="trialForm.rejected" :min="0" controls-position="right" />
          </el-form-item>
          <el-form-item label="证据完整">
            <el-switch v-model="trialForm.evidenceComplete" />
          </el-form-item>
          <el-form-item label="评审通过">
            <el-switch v-model="trialForm.reviewPassed" />
          </el-form-item>
        </el-form>
      </div>
      <div class="section-card score-preview">
        <span>试算参考分</span>
        <strong>{{ trialScore ?? '--' }}</strong>
        <p>{{ trialExplanation || '填写左侧条件后试算，系统会返回命中因素和解释性分值。' }}</p>
        <div v-if="trialFactors.length" class="toolbar">
          <el-tag v-for="factor in trialFactors" :key="factor" size="small">{{ factorName(factor) }}</el-tag>
        </div>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Check, DataAnalysis } from '@element-plus/icons-vue'
import {
  createScoreRuleApi,
  enableScoreRuleApi,
  listScoreRulesApi,
  simulateScoreRuleApi,
  updateScoreRuleApi,
} from '@/api/department'
import { currentMonth, errorMessage, periodRange } from '@/api/performance'
import { useDepartmentOrgScope } from '@/composables/useDepartmentOrgScope'

interface FactorRow {
  code: string
  factor: string
  rule: string
  weight: number
  enabled: boolean
  note: string
  penaltyPerTime?: number
}

const { orgOptions, selectedOrgId, orgLoading, loadOrgScope } = useDepartmentOrgScope()
const query = reactive({
  month: currentMonth(),
})
const rows = ref<FactorRow[]>([])
const trialForm = reactive({
  employee: '',
  completion: 82,
  overdue: 1,
  rejected: 0,
  evidenceComplete: true,
  reviewPassed: true,
})
const trialScore = ref<number | null>(null)
const trialExplanation = ref('')
const trialFactors = ref<string[]>([])
const currentRuleId = ref<number | null>(null)
const currentRuleName = ref('部门月度参考分规则')
const currentRuleStatus = ref('DRAFT')
const currentRuleJson = ref<Record<string, unknown>>({})
const loading = ref(false)
const saving = ref(false)
const trialLoading = ref(false)
const ruleDirty = ref(false)
const requiresMigration = ref(false)
const totalWeight = computed(() => rows.value
  .filter((row) => row.enabled)
  .reduce((sum, row) => sum + Number(row.weight || 0), 0))
const weightValid = computed(() => totalWeight.value > 0 && totalWeight.value <= 100)
const currentRuleStatusText = computed(() => ({
  ENABLED: '已启用',
  DISABLED: '已停用',
  DRAFT: '草稿',
} as Record<string, string>)[currentRuleStatus.value] || currentRuleStatus.value)
const trialBlocked = computed(() => !currentRuleId.value || ruleDirty.value || requiresMigration.value)
const trialBlockReason = computed(() => {
  if (!currentRuleId.value) return '请先保存并启用规则'
  return requiresMigration.value ? '请先保存，将旧版规则迁移为因素权重规则' : '请先保存当前修改再试算'
})

watch(rows, () => {
  ruleDirty.value = true
  clearTrialResult()
}, { deep: true })

watch(() => query.month, () => {
  ruleDirty.value = true
  clearTrialResult()
})

watch(trialForm, clearTrialResult, { deep: true })

async function refresh() {
  loading.value = true
  try {
    if (selectedOrgId.value == null) {
      rows.value = []
      currentRuleId.value = null
      currentRuleStatus.value = 'DRAFT'
      return
    }
    const data = await listScoreRulesApi({ orgId: selectedOrgId.value })
    const rule = data.find((item) => item.status === 'ENABLED') || data[0]
    if (!rule) {
      rows.value = defaultFactors()
      currentRuleId.value = null
      currentRuleStatus.value = 'DRAFT'
      currentRuleJson.value = {}
      requiresMigration.value = false
      return
    }
    currentRuleId.value = rule.id
    currentRuleName.value = rule.ruleName
    currentRuleStatus.value = rule.status
    currentRuleJson.value = rule.ruleJson
    query.month = rule.effectiveStart?.slice(0, 7) || currentMonth()
    const factors = Array.isArray(rule.ruleJson.factors) ? rule.ruleJson.factors as Array<Record<string, unknown>> : []
    requiresMigration.value = !factors.length
    rows.value = factors.length ? factors.map(mapFactor) : defaultFactors()
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    await nextTick()
    ruleDirty.value = false
    loading.value = false
  }
}

async function trial() {
  const ruleId = currentRuleId.value
  if (!ruleId || trialBlocked.value) {
    ElMessage.warning(trialBlockReason.value)
    return
  }
  trialLoading.value = true
  try {
    const result = await simulateScoreRuleApi(ruleId, {
      employeeName: trialForm.employee,
      completionRatio: trialForm.completion,
      overdueCount: trialForm.overdue,
      rejectCount: trialForm.rejected,
      evidenceComplete: trialForm.evidenceComplete,
      reviewPassed: trialForm.reviewPassed,
    })
    trialScore.value = Number(result.score)
    trialExplanation.value = result.explanation
    trialFactors.value = result.hitFactors
    ElMessage.success('试算已完成')
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    trialLoading.value = false
  }
}

async function save() {
  if (selectedOrgId.value == null) {
    ElMessage.warning('当前账号没有可配置的授权组织')
    return
  }
  if (!weightValid.value) {
    ElMessage.warning('启用因素权重合计必须大于 0 且不超过 100')
    return
  }
  saving.value = true
  try {
    const range = periodRange(query.month)
    const payload = {
      orgId: selectedOrgId.value,
      ruleName: currentRuleName.value,
      effectiveStart: range.periodStart,
      ruleJson: {
        ...currentRuleJson.value,
        factors: rows.value.map((row) => ({
          code: row.code,
          name: row.factor,
          type: 'weight',
          weight: row.weight,
          enabled: row.enabled,
          note: row.note,
          ...(row.penaltyPerTime == null ? {} : { penaltyPerTime: row.penaltyPerTime }),
        })),
      },
    }
    const saved = currentRuleId.value
      ? await updateScoreRuleApi(currentRuleId.value, payload)
      : await createScoreRuleApi(payload)
    if (saved.status !== 'ENABLED') {
      await enableScoreRuleApi(saved.id)
    }
    currentRuleId.value = saved.id
    currentRuleJson.value = saved.ruleJson
    await refresh()
    ElMessage.success('参考分规则已保存并启用')
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    saving.value = false
  }
}

function mapFactor(factor: Record<string, unknown>): FactorRow {
  const code = String(factor.code || '')
  const weight = Number(factor.weight ?? factor.maxScore ?? factor.score ?? factor.scorePerTime ?? 0)
  return {
    code,
    factor: String(factor.name || code || '未命名因素'),
    rule: factorRuleText(factor),
    weight,
    enabled: factor.enabled !== false,
    note: String(factor.note || '参考分解释因素'),
    penaltyPerTime: factor.penaltyPerTime == null ? undefined : Number(factor.penaltyPerTime),
  }
}

function factorRuleText(factor: Record<string, unknown>) {
  const type = String(factor.type || '')
  if (type === 'linear') return '按比例线性折算'
  if (type === 'boolean') return '满足条件时计入'
  if (type === 'deduct') return '按次数修正参考分'
  return '按配置权重参与试算'
}

function defaultFactors(): FactorRow[] {
  return [
    { code: 'completion_ratio', factor: '完成比例', rule: '按最终确认比例折算', weight: 70, enabled: true, note: '核心权重' },
    { code: 'overdue_count', factor: '逾期提交', rule: '每次轻量修正并提示', weight: 10, enabled: true, note: '不自动扣绩效', penaltyPerTime: 2 },
    { code: 'reject_count', factor: '驳回次数', rule: '用于参考分解释', weight: 10, enabled: true, note: '保留人工裁量', penaltyPerTime: 3 },
    { code: 'review_passed', factor: '评审通过', rule: '按评审记录计入', weight: 10, enabled: true, note: '依据完整性因素' },
  ]
}

function clearTrialResult() {
  trialScore.value = null
  trialExplanation.value = ''
  trialFactors.value = []
}

function factorName(code: string) {
  return rows.value.find((row) => row.code === code)?.factor || code
}

onMounted(async () => {
  try {
    await loadOrgScope()
    await refresh()
  } catch (error) {
    ElMessage.error(errorMessage(error))
  }
})
</script>
