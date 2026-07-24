<template>
  <section class="page-panel employee-performance-evidence">
    <div class="page-header">
      <div>
        <h1 class="page-title">本人绩效依据</h1>
        <p class="page-subtitle">按日、周、月、季、年核对本人计划、成果和申诉形成的绩效依据。</p>
      </div>
      <div class="toolbar">
        <el-button :icon="Download" :disabled="loading || exporting || !rows.length" @click="exportDialog = true">导出</el-button>
        <el-button :loading="loading" :disabled="exporting" @click="loadEvidence">刷新</el-button>
      </div>
    </div>

    <el-alert
      v-if="loadError"
      class="dashboard-alert"
      type="warning"
      :closable="false"
      show-icon
      :title="loadError"
    />

    <section class="dashboard-section evidence-filter">
      <div class="section-header">
        <div>
          <h2>依据周期</h2>
          <p>切换周期后，系统会从数据库重新汇总当前周期内的本人记录。</p>
        </div>
        <el-radio-group v-model="periodType" class="period-switch" :disabled="loading || exporting" @change="loadEvidence">
          <el-radio-button
            v-for="option in periodOptions"
            :key="option.value"
            class="period-switch-option"
            :label="option.value"
          >
            {{ option.label }}
          </el-radio-button>
        </el-radio-group>
      </div>
      <div class="context-strip">
        <div>
          <span>统计范围</span>
          <strong>{{ periodRangeText }}</strong>
        </div>
        <div>
          <span>数据权限</span>
          <strong>仅本人可见，按来源对象追溯</strong>
        </div>
        <div>
          <span>导出格式</span>
          <strong>PDF / Word / Zip</strong>
        </div>
      </div>
    </section>

    <el-row class="mt16" :gutter="16">
      <el-col :xs="24" :sm="8">
        <div class="metric">
          <span>当前周期</span>
          <strong>{{ currentPeriodLabel }}</strong>
        </div>
      </el-col>
      <el-col :xs="24" :sm="8">
        <div class="metric">
          <span>依据条数</span>
          <strong>{{ rows.length }}</strong>
        </div>
      </el-col>
      <el-col :xs="24" :sm="8">
        <div class="metric">
          <span>平均参考值</span>
          <strong>{{ averageScore }}%</strong>
        </div>
      </el-col>
    </el-row>

    <el-row class="mt16" :gutter="16">
      <el-col :xs="24" :lg="17">
        <section class="dashboard-section evidence-table-section">
          <div class="section-header">
            <div>
              <h2>依据列表</h2>
              <p>参考值来自业务记录中的完成比例，不替代部门最终绩效裁量。</p>
            </div>
          </div>

          <el-table
            v-loading="loading"
            :data="rows"
            border
            row-key="rowKey"
            empty-text="当前周期暂无绩效依据"
          >
            <el-table-column prop="evidenceDate" label="依据日期" width="118" />
            <el-table-column label="来源" width="108">
              <template #default="{ row }">
                <el-tag :type="getSourceType(row.sourceType)">
                  {{ getSourceLabel(row.sourceType) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="title" label="依据标题" min-width="170" show-overflow-tooltip />
            <el-table-column prop="description" label="依据说明" min-width="300" show-overflow-tooltip />
            <el-table-column prop="score" label="参考值" width="90" align="right">
              <template #default="{ row }">{{ row.score }}%</template>
            </el-table-column>
            <el-table-column prop="createdAt" label="生成时间" width="168" />
          </el-table>
        </section>
      </el-col>

      <el-col :xs="24" :lg="7">
        <section class="dashboard-section">
          <div class="section-header">
            <div>
              <h2>来源构成</h2>
              <p>快速核对当前周期是否覆盖关键业务记录。</p>
            </div>
          </div>
          <div class="source-summary">
            <div v-for="source in sourceSummary" :key="source.type">
              <span>{{ source.label }}</span>
              <strong>{{ source.count }}</strong>
            </div>
          </div>
        </section>

        <section class="dashboard-section mt16">
          <div class="section-header">
            <div>
              <h2>结果有异议</h2>
              <p>成果确认或驳回后 3 个自然日内可提交申诉。</p>
            </div>
          </div>
          <el-button type="danger" plain @click="goAppeals">查看申诉记录</el-button>
        </section>
      </el-col>
    </el-row>

    <el-dialog v-model="exportDialog" title="导出个人绩效依据" width="520px">
      <el-alert
        class="export-range-alert"
        type="info"
        :closable="false"
        :title="`导出范围：${periodRangeText}`"
      />
      <el-form label-position="top">
        <el-form-item label="文件格式">
          <el-checkbox-group v-model="exportFormats">
            <el-checkbox-button value="PDF">PDF</el-checkbox-button>
            <el-checkbox-button value="WORD">Word</el-checkbox-button>
            <el-checkbox-button value="ZIP">Zip</el-checkbox-button>
          </el-checkbox-group>
        </el-form-item>
        <el-form-item label="资料范围">
          <el-checkbox v-model="includeEvidence">包含成果证据文件</el-checkbox>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button :disabled="exporting" @click="exportDialog = false">取消</el-button>
        <el-button type="primary" :loading="exporting" @click="handleExport">生成并下载</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { Download } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  createEmployeeEvidenceExportApi,
  downloadEmployeeExportApi,
  getEmployeePerformanceEvidenceApi,
  type EmployeeEvidencePeriodType,
  type EmployeeEvidenceSourceType,
  type EmployeePerformanceEvidenceItem,
} from '@/api/employee'
import { saveBlob } from '@/utils/download'

type EvidenceRow = EmployeePerformanceEvidenceItem & { rowKey: string }

const router = useRouter()

const periodOptions: Array<{ label: string; value: EmployeeEvidencePeriodType }> = [
  { label: '日', value: 'day' },
  { label: '周', value: 'week' },
  { label: '月', value: 'month' },
  { label: '季', value: 'quarter' },
  { label: '年', value: 'year' },
]

const sourceMap: Record<EmployeeEvidenceSourceType, { label: string; type: 'primary' | 'success' | 'warning' | 'info' }> = {
  day_plan: { label: '日计划', type: 'primary' },
  month_plan: { label: '月计划', type: 'success' },
  result: { label: '成果', type: 'warning' },
  appeal: { label: '申诉', type: 'info' },
}

const periodType = ref<EmployeeEvidencePeriodType>('month')
const periodStart = ref('')
const periodEnd = ref('')
const rows = ref<EvidenceRow[]>([])
const loading = ref(false)
const loadError = ref('')
const exportDialog = ref(false)
const exporting = ref(false)
const exportFormats = ref(['PDF'])
const includeEvidence = ref(true)
let evidenceRequestId = 0

const currentPeriodLabel = computed(() => getPeriodLabel(periodType.value))
const periodRangeText = computed(() => {
  if (!periodStart.value || !periodEnd.value) return '正在加载'
  return periodStart.value === periodEnd.value
    ? periodStart.value
    : `${periodStart.value} 至 ${periodEnd.value}`
})
const averageScore = computed(() => {
  if (!rows.value.length) return 0
  const average = rows.value.reduce((sum, item) => sum + item.score, 0) / rows.value.length
  return Number(average.toFixed(1))
})
const sourceSummary = computed(() => Object.entries(sourceMap).map(([type, source]) => ({
  type,
  label: source.label,
  count: rows.value.filter((row) => row.sourceType === type).length,
})))

function getPeriodLabel(value: EmployeeEvidencePeriodType) {
  return periodOptions.find((option) => option.value === value)?.label ?? value
}

function getSourceLabel(value: EmployeeEvidenceSourceType) {
  return sourceMap[value]?.label ?? value
}

function getSourceType(value: EmployeeEvidenceSourceType) {
  return sourceMap[value]?.type ?? 'info'
}

async function loadEvidence() {
  const requestId = ++evidenceRequestId
  const requestedPeriodType = periodType.value
  loading.value = true
  loadError.value = ''
  try {
    const data = await getEmployeePerformanceEvidenceApi(requestedPeriodType)
    if (requestId !== evidenceRequestId || requestedPeriodType !== periodType.value) return
    periodType.value = data.periodType
    periodStart.value = data.periodStart
    periodEnd.value = data.periodEnd
    rows.value = (data.items ?? []).map((item) => ({
      ...item,
      rowKey: `${item.sourceType}-${item.id}`,
    }))
  } catch (error) {
    if (requestId !== evidenceRequestId) return
    rows.value = []
    periodStart.value = ''
    periodEnd.value = ''
    loadError.value = error instanceof Error ? error.message : '本人绩效依据加载失败，请稍后重试'
  } finally {
    if (requestId === evidenceRequestId) loading.value = false
  }
}

async function handleExport() {
  if (!exportFormats.value.length) {
    ElMessage.warning('请至少选择一种导出格式')
    return
  }
  exporting.value = true
  try {
    const task = await createEmployeeEvidenceExportApi({
      periodType: periodType.value,
      formats: exportFormats.value,
      includeEvidence: includeEvidence.value,
    })
    const blob = await downloadEmployeeExportApi(task.taskId)
    saveBlob(blob, task.fileName || `${periodType.value}-performance-evidence.zip`)
    exportDialog.value = false
    ElMessage.success('个人绩效依据已导出并通过完整性校验')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '导出失败，请稍后重试')
  } finally {
    exporting.value = false
  }
}

function goAppeals() {
  router.push('/employee/appeals')
}

onMounted(loadEvidence)
</script>

<style scoped>
.employee-performance-evidence .metric {
  margin-bottom: 0;
}

.evidence-filter {
  padding-bottom: 12px;
}

.evidence-table-section {
  overflow: hidden;
}

.source-summary {
  display: grid;
  gap: 10px;
}

.source-summary > div {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 38px;
  padding: 0 12px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
}

.source-summary span {
  color: var(--el-text-color-regular);
}

.source-summary strong {
  font-size: 18px;
}

.export-range-alert {
  margin-bottom: 16px;
}

@media (max-width: 900px) {
  .period-switch {
    width: 100%;
  }

  .period-switch-option {
    flex: 1;
  }
}
</style>
