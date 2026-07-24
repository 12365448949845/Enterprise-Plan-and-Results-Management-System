export type WorkspaceId = 'system' | 'department' | 'leader' | 'employee' | 'dispute'

export interface WorkspaceMenuItem {
  label: string
  path: string
  icon: string
  permission: string
}

export interface WorkspaceDefinition {
  id: WorkspaceId
  title: string
  homePath: string
  permission: string
  matches: readonly string[]
  items: readonly WorkspaceMenuItem[]
}

export const WORKSPACES: readonly WorkspaceDefinition[] = [
  {
    id: 'system',
    title: '系统管理端',
    homePath: '/system/dashboard',
    permission: 'system:dashboard:view',
    matches: ['/system', '/dashboard'],
    items: [
      { label: '管理工作台', path: '/system/dashboard', icon: '▦', permission: 'system:dashboard:view' },
      { label: '员工注册', path: '/system/employee-register', icon: '+', permission: 'system:employee:register' },
      { label: '员工管理', path: '/system/employees', icon: '♙', permission: 'system:employee:view' },
      { label: '部门/项目组', path: '/system/orgs', icon: '⌘', permission: 'system:org:view' },
      { label: '角色管理', path: '/system/roles', icon: '◇', permission: 'system:role:view' },
      { label: '权限管理', path: '/system/permissions', icon: '✓', permission: 'system:permission:view' },
      { label: '工作日规则', path: '/system/workday-rules', icon: '◷', permission: 'system:workday:view' },
      { label: '审计日志', path: '/system/audits', icon: '≡', permission: 'system:audit:view' },
      { label: 'AI 配置', path: '/system/ai', icon: '✦', permission: 'system:dashboard:view' },
    ],
  },
  {
    id: 'department',
    title: '部门负责人端',
    homePath: '/department/dashboard',
    permission: 'department:dashboard:view',
    matches: ['/department'],
    items: [
      { label: '部门总览', path: '/department/dashboard', icon: '▦', permission: 'department:dashboard:view' },
      { label: '月计划查看', path: '/department/plan-approval', icon: '□', permission: 'department:month-approval:view' },
      { label: '成果最终确认', path: '/department/result-confirm', icon: '◇', permission: 'department:result-confirm:view' },
      { label: '通知待办', path: '/department/todo', icon: '!', permission: 'department:todo:view' },
      { label: '交付物模板', path: '/department/template', icon: '□', permission: 'department:template:view' },
      { label: '验收标准', path: '/department/standard', icon: '◆', permission: 'department:standard:view' },
      { label: '参考分规则', path: '/department/score-rule', icon: '≈', permission: 'department:score-rule:view' },
      { label: '部门台账', path: '/department/department-ledger', icon: '≡', permission: 'department:department-ledger:view' },
      { label: '周计划台账', path: '/department/week-plan-ledger', icon: '▤', permission: 'department:dashboard:view' },
      { label: '导出任务', path: '/department/export-tasks', icon: '↓', permission: 'department:export-task:view' },
    ],
  },
  {
    id: 'leader',
    title: '直属领导端',
    homePath: '/leader/workbench',
    permission: 'leader:workbench:view',
    matches: ['/leader'],
    items: [
      { label: '工作台', path: '/leader/workbench', icon: '▦', permission: 'leader:workbench:view' },
      { label: '月计划审批', path: '/leader/month-plan-approval', icon: '✓', permission: 'leader:month-approval:view' },
      { label: '周计划审批', path: '/leader/week-plan-approval', icon: '▤', permission: 'leader:workbench:view' },
      { label: '日计划点评', path: '/leader/daily-review', icon: '✓', permission: 'leader:daily-review:view' },
      { label: '成果确认建议', path: '/leader/result-suggest', icon: '◇', permission: 'leader:result-suggest:view' },
      { label: '额外任务审批', path: '/leader/extra-task-approval', icon: '✓', permission: 'leader:workbench:view' },
      { label: '计划调整', path: '/leader/plan-adjust', icon: '↺', permission: 'leader:plan-adjust:view' },
      { label: '下属台账', path: '/leader/team-ledger', icon: '≡', permission: 'leader:team-ledger:view' },
      { label: '本月计划要求', path: '/leader/ai-month-context', icon: '✦', permission: 'leader:workbench:view' },
    ],
  },
  {
    id: 'employee',
    title: '员工工作台',
    homePath: '/employee/dashboard',
    permission: 'dashboard:view',
    matches: ['/employee', '/planning'],
    items: [
      { label: '工作台', path: '/employee/dashboard', icon: '▦', permission: 'dashboard:view' },
      { label: '月计划', path: '/employee/month-plans', icon: '□', permission: 'planning:month:view' },
      { label: '周计划', path: '/employee/week-plans', icon: '▤', permission: 'dashboard:view' },
      { label: '日计划', path: '/employee/day-plans', icon: '◷', permission: 'planning:day:view' },
      { label: '成果提交', path: '/employee/results/submit', icon: '↑', permission: 'planning:result:view' },
      { label: '成果记录', path: '/employee/results', icon: '≡', permission: 'planning:result:view' },
      { label: '绩效依据', path: '/employee/performance-evidence', icon: '◇', permission: 'dashboard:view' },
      { label: '申诉记录', path: '/employee/appeals', icon: '!', permission: 'dashboard:view' },
    ],
  },
  {
    id: 'dispute',
    title: '裁决工作台',
    homePath: '/dispute/dashboard',
    permission: 'dispute:dashboard:view',
    matches: ['/dispute'],
    items: [
      { label: '工作台', path: '/dispute/dashboard', icon: '▣', permission: 'dispute:dashboard:view' },
      { label: '争议案件', path: '/dispute/cases', icon: '!', permission: 'dispute:case:view' },
    ],
  },
]

export function hasPermission(permissions: readonly string[], required?: string) {
  return !required || permissions.includes(required)
}

export function availableWorkspaces(permissions: readonly string[]) {
  return WORKSPACES.filter((workspace) => hasPermission(permissions, workspace.permission))
}

export function defaultWorkspacePath(permissions: readonly string[]) {
  return availableWorkspaces(permissions)[0]?.homePath
}

export function workspaceForPath(path: string) {
  return WORKSPACES.find((workspace) => workspace.matches.some(
    (prefix) => path === prefix || path.startsWith(`${prefix}/`),
  ))
}

export function visibleWorkspaceItems(
  workspace: WorkspaceDefinition,
  permissions: readonly string[],
) {
  return workspace.items.filter((item) => hasPermission(permissions, item.permission))
}
