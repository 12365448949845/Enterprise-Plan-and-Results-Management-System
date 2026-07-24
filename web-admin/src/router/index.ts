import { createRouter, createWebHistory } from 'vue-router'
import { ElMessage } from 'element-plus'
import { defaultWorkspacePath, hasPermission } from '@/navigation/workspaces'
import { useAuthStore } from '@/stores/auth'
import { pinia } from '@/stores'

const EmployeeDashboard = () => import('@/views/employee/EmployeeDashboard.vue')
const EmployeeMonthPlanDetail = () => import('@/views/employee/EmployeeMonthPlanDetail.vue')
const EmployeeMonthPlanEdit = () => import('@/views/employee/EmployeeMonthPlanEdit.vue')
const EmployeeDayPlanEdit = () => import('@/views/employee/EmployeeDayPlanEdit.vue')
const EmployeeResultSubmit = () => import('@/views/employee/EmployeeResultSubmit.vue')
const EmployeePerformanceEvidence = () => import('@/views/employee/EmployeePerformanceEvidence.vue')
const EmployeeAppeals = () => import('@/views/employee/EmployeeAppeals.vue')
const EmployeeMonthPlanList = () => import('@/views/employee/EmployeeMonthPlanListRefactored.vue')
const EmployeeDayPlanList = () => import('@/views/employee/EmployeeDayPlanList.vue')
const EmployeeWeekPlanList = () => import('@/views/employee/EmployeeWeekPlanList.vue')
const EmployeeWeekPlanEdit = () => import('@/views/employee/EmployeeWeekPlanEdit.vue')
const EmployeeWeekPlanDetail = () => import('@/views/employee/EmployeeWeekPlanDetail.vue')
const EmployeeResultList = () => import('@/views/employee/EmployeeResultList.vue')
const DisputeDashboard = () => import('@/views/dispute/DisputeDashboard.vue')
const DisputeCases = () => import('@/views/dispute/DisputeCases.vue')
const DisputeCaseDetail = () => import('@/views/dispute/DisputeCaseDetail.vue')
const DisputeReviewPanel = () => import('@/views/dispute/DisputeReviewPanel.vue')

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', component: () => import('@/views/Login.vue'), meta: { public: true } },
    { path: '/change-password', component: () => import('@/views/ChangePassword.vue'), meta: { passwordChange: true } },
    { path: '/', component: () => import('@/views/NoAccess.vue') },
    { path: '/no-access', component: () => import('@/views/NoAccess.vue') },
    { path: '/messages', component: () => import('@/views/MessageCenter.vue') },
    { path: '/leader/workbench', component: () => import('@/views/leader/LeaderWorkbench.vue'), meta: { permission: 'leader:workbench:view' } },
    { path: '/leader/month-plan-approval', component: () => import('@/views/department/MonthPlanApproval.vue'), meta: { permission: 'leader:month-approval:view' } },
    { path: '/leader/daily-review', component: () => import('@/views/leader/DailyReview.vue'), meta: { permission: 'leader:daily-review:view' } },
    { path: '/leader/week-plan-approval', component: () => import('@/views/leader/WeekPlanApproval.vue'), meta: { permission: 'leader:workbench:view' } },
    { path: '/leader/result-suggest', component: () => import('@/views/leader/ResultSuggest.vue'), meta: { permission: 'leader:result-suggest:view' } },
    { path: '/leader/plan-adjust', component: () => import('@/views/leader/PlanAdjust.vue'), meta: { permission: 'leader:plan-adjust:view' } },
    { path: '/leader/extra-task-approval', component: () => import('@/views/leader/ExtraTaskApproval.vue'), meta: { permission: 'leader:workbench:view' } },
    { path: '/leader/team-ledger', component: () => import('@/views/leader/TeamLedger.vue'), meta: { permission: 'leader:team-ledger:view' } },
    { path: '/leader/ai-month-context', component: () => import('@/views/leader/AiMonthPlanContext.vue'), meta: { permission: 'leader:workbench:view' } },
    { path: '/department/dashboard', component: () => import('@/views/department/DepartmentDashboard.vue'), meta: { permission: 'department:dashboard:view' } },
    { path: '/department/plan-approval', component: () => import('@/views/department/MonthPlanApproval.vue'), meta: { permission: 'department:month-approval:view' } },
    { path: '/department/result-confirm', component: () => import('@/views/department/ResultConfirm.vue'), meta: { permission: 'department:result-confirm:view' } },
    { path: '/department/todo', component: () => import('@/views/department/Todo.vue'), meta: { permission: 'department:todo:view' } },
    { path: '/department/template', component: () => import('@/views/department/DeliverableTemplate.vue'), meta: { permission: 'department:template:view' } },
    { path: '/department/standard', component: () => import('@/views/department/AcceptanceStandard.vue'), meta: { permission: 'department:standard:view' } },
    { path: '/department/score-rule', component: () => import('@/views/department/ScoreRule.vue'), meta: { permission: 'department:score-rule:view' } },
    { path: '/department/department-ledger', component: () => import('@/views/department/DepartmentLedger.vue'), meta: { permission: 'department:department-ledger:view' } },
    { path: '/department/week-plan-ledger', component: () => import('@/views/department/WeekPlanLedger.vue'), meta: { permission: 'department:dashboard:view' } },
    { path: '/department/export-tasks', component: () => import('@/views/department/ExportTasks.vue'), meta: { permission: 'department:export-task:view' } },
    { path: '/system/dashboard', alias: '/dashboard', component: () => import('@/views/system/SystemDashboard.vue'), meta: { permission: 'system:dashboard:view' } },
    { path: '/employee/dashboard', alias: '/employee/workbench', component: EmployeeDashboard, meta: { permission: 'dashboard:view' } },
    { path: '/employee/month-detail', alias: '/employee/month-plans/:id', component: EmployeeMonthPlanDetail, meta: { permission: 'planning:month:view' } },
    {
      path: '/employee/month-edit',
      alias: ['/employee/month-plans/new/edit', '/employee/month-plans/:id/edit'],
      component: EmployeeMonthPlanEdit,
      meta: { permission: 'planning:month:view' },
    },
    { path: '/employee/daily-plan', alias: '/employee/day-plans/edit', component: EmployeeDayPlanEdit, meta: { permission: 'planning:day:view' } },
    { path: '/employee/result-submit', alias: '/employee/results/submit', component: EmployeeResultSubmit, meta: { permission: 'planning:result:view' } },
    { path: '/employee/ledger', alias: '/employee/performance-evidence', component: EmployeePerformanceEvidence, meta: { permission: 'dashboard:view' } },
    { path: '/employee/appeal', alias: '/employee/appeals', component: EmployeeAppeals, meta: { permission: 'dashboard:view' } },
    { path: '/employee/month-plans', alias: '/planning/month', component: EmployeeMonthPlanList, meta: { permission: 'planning:month:view' } },
    { path: '/employee/week-plans', alias: '/planning/week', component: EmployeeWeekPlanList, meta: { permission: 'dashboard:view' } },
    { path: '/employee/week-plans/new/edit', component: EmployeeWeekPlanEdit, meta: { permission: 'dashboard:view' } },
    { path: '/employee/week-plans/:id/edit', component: EmployeeWeekPlanEdit, meta: { permission: 'dashboard:view' } },
    { path: '/employee/week-plans/:id', component: EmployeeWeekPlanDetail, meta: { permission: 'dashboard:view' } },
    { path: '/employee/day-plans', alias: '/planning/day', component: EmployeeDayPlanList, meta: { permission: 'planning:day:view' } },
    { path: '/employee/results', alias: '/planning/result', component: EmployeeResultList, meta: { permission: 'planning:result:view' } },
    { path: '/system/employee-register', component: () => import('@/views/system/EmployeeRegister.vue'), meta: { permission: 'system:employee:register' } },
    { path: '/system/employees', alias: '/system/users', component: () => import('@/views/system/EmployeeManagement.vue'), meta: { permission: 'system:employee:view' } },
    { path: '/system/orgs', component: () => import('@/views/system/OrgManagement.vue'), meta: { permission: 'system:org:view' } },
    { path: '/system/roles', component: () => import('@/views/system/RoleManagement.vue'), meta: { permission: 'system:role:view' } },
    { path: '/system/permissions', component: () => import('@/views/system/PermissionManagement.vue'), meta: { permission: 'system:permission:view' } },
    { path: '/system/workday-rules', component: () => import('@/views/system/WorkdayRules.vue'), meta: { permission: 'system:workday:view' } },
    { path: '/system/audits', component: () => import('@/views/system/AuditLog.vue'), meta: { permission: 'system:audit:view' } },
    { path: '/system/ai', component: () => import('@/views/system/AiManagement.vue'), meta: { permission: 'system:dashboard:view' } },
    { path: '/dispute/dashboard', component: DisputeDashboard, meta: { permission: 'dispute:dashboard:view' } },
    { path: '/dispute/cases', component: DisputeCases, meta: { permission: 'dispute:case:view' } },
    { path: '/dispute/cases/:id', component: DisputeCaseDetail, meta: { permission: 'dispute:case:view' } },
    { path: '/dispute/cases/:id/review-panel', component: DisputeReviewPanel, meta: { permission: 'dispute:case:view' } },
  ],
})

router.beforeEach(async (to) => {
  if (to.meta.public) return true
  const token = localStorage.getItem('planning_access_token')
  if (!token) return { path: '/login', query: { redirect: to.fullPath } }
  const authStore = useAuthStore(pinia)
  if (!authStore.user) {
    try { await authStore.loadMe() }
    catch { return { path: '/login', query: { redirect: to.fullPath } } }
  }
  if (authStore.user?.forceChangePassword && to.path !== '/change-password') return '/change-password'
  const permissions = authStore.user?.permissions || []
  const home = defaultWorkspacePath(permissions)
  if (!authStore.user?.forceChangePassword && to.path === '/change-password') return home || '/no-access'
  if (to.path === '/') return home || '/no-access'
  if (to.path === '/no-access') return home || true
  const requiredPermission = typeof to.meta.permission === 'string' ? to.meta.permission : undefined
  if (!hasPermission(permissions, requiredPermission)) {
    ElMessage.warning('当前账号无权访问该页面')
    return home || '/no-access'
  }
  return true
})

export default router
