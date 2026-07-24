import { http } from './http'

export interface PageResult<T> {
  records: T[]
  total: number
  pageNo: number
  pageSize: number
}

export interface AuditItem {
  id: number
  userId?: number
  username?: string
  action: string
  targetType?: string
  targetId?: number
  result: string
  clientIp?: string
  detail?: string
  createdAt: string
}

export interface DashboardData {
  departmentCount: number
  employeeCount: number
  roleCount: number
  auditCount: number
  risks: Array<{ code: string; title: string; count: number; level: string; route: string }>
  recentAudits: AuditItem[]
}

export interface DeptNode {
  id: number
  parentId: number
  name: string
  code: string
  orgType: 'DEPARTMENT' | 'GROUP' | 'PROJECT_GROUP'
  leaderUserId?: number
  leaderName?: string
  sortNo: number
  status: number
  employeeCount: number
  children: DeptNode[]
}

export interface RoleItem {
  id: number
  name: string
  code: string
  description?: string
  dataScope: string
  builtIn: boolean
  status: number
  userCount: number
  permissionCount: number
}

export interface OptionItem { id: number; label: string; secondary?: string; status: number }
export interface SystemOptions { departments: DeptNode[]; leaders: OptionItem[]; roles: RoleItem[] }

export interface RegistrationResult {
  userId: number
  username: string
  employeeNo: string
  initialPassword: string
  realName: string
  departmentName: string
  directLeaderName: string
}

export interface UserItem {
  id: number
  username: string
  employeeNo: string
  realName: string
  mobile: string
  deptId?: number
  departmentName: string
  groupId?: number
  directLeaderId?: number
  directLeaderName: string
  roleIds: number[]
  roleNames: string[]
  status: number
  forceChangePassword: boolean
  lastLoginAt?: string
  createdAt: string
}

export interface PermissionNode {
  id: number
  parentId: number
  name: string
  code: string
  type: string
  path?: string
  status: number
  children: PermissionNode[]
}

export interface WorkdayRule {
  id: number
  ruleDate: string
  ruleType: string
  forceReport: boolean
  description?: string
  status: number
  versionNo: number
  updatedAt?: string
}

export const getSystemDashboard = () => http.get<unknown, DashboardData>('/system/dashboard')
export const getSystemOptions = () => http.get<unknown, SystemOptions>('/system/options')
export const registerEmployee = (data: { realName: string; mobile: string; deptId: number; directLeaderId: number }) =>
  http.post<unknown, RegistrationResult>('/system/users/register', data)
export const getUsers = (params: Record<string, unknown>) => http.get<unknown, PageResult<UserItem>>('/system/users', { params })
export const updateUser = (id: number, data: Record<string, unknown>) => http.put<unknown, UserItem>(`/system/users/${id}`, data)
export const changeUserStatus = (id: number, status: number) => http.post(`/system/users/${id}/status`, { status })
export const resetUserPassword = (id: number) => http.post(`/system/users/${id}/reset-password`)
export const importUsers = (file: File) => {
  const form = new FormData()
  form.append('file', file)
  return http.post<unknown, { total: number; success: number; failed: number; errors: string[] }>('/system/users/import', form)
}
export const downloadUserImportTemplate = () => http.get<unknown, Blob>('/system/users/import-template', { responseType: 'blob' })
export const exportUsers = () => http.get<unknown, Blob>('/system/users/export', { responseType: 'blob' })

export const getDepartments = () => http.get<unknown, DeptNode[]>('/system/departments')
export const createDepartment = (data: Record<string, unknown>) => http.post<unknown, DeptNode>('/system/departments', data)
export const updateDepartment = (id: number, data: Record<string, unknown>) => http.put<unknown, DeptNode>(`/system/departments/${id}`, data)

export const getRoles = () => http.get<unknown, RoleItem[]>('/system/roles')
export const createRole = (data: Record<string, unknown>) => http.post<unknown, RoleItem>('/system/roles', data)
export const updateRole = (id: number, data: Record<string, unknown>) => http.put<unknown, RoleItem>(`/system/roles/${id}`, data)

export const getPermissions = () => http.get<unknown, PermissionNode[]>('/system/permissions')
export const getRolePermissions = (id: number) => http.get<unknown, number[]>(`/system/roles/${id}/permissions`)
export const saveRolePermissions = (id: number, permissionIds: number[]) =>
  http.put(`/system/roles/${id}/permissions`, { permissionIds })

export const getWorkdayRules = (params: Record<string, unknown>) => http.get<unknown, WorkdayRule[]>('/system/workday-rules', { params })
export const createWorkdayRule = (data: Record<string, unknown>) => http.post<unknown, WorkdayRule>('/system/workday-rules', data)
export const updateWorkdayRule = (id: number, data: Record<string, unknown>) => http.put<unknown, WorkdayRule>(`/system/workday-rules/${id}`, data)
export const changeWorkdayStatus = (id: number, status: number) => http.post(`/system/workday-rules/${id}/status`, { status })

export const getAudits = (params: Record<string, unknown>) => http.get<unknown, PageResult<AuditItem>>('/system/audits', { params })
export const exportAudits = (params: Record<string, unknown>) => http.get<unknown, Blob>('/system/audits/export', { params, responseType: 'blob' })

export function flattenDepartments(nodes: DeptNode[], depth = 0): Array<DeptNode & { depth: number }> {
  return nodes.flatMap((node) => [{ ...node, depth }, ...flattenDepartments(node.children || [], depth + 1)])
}

export function downloadBlob(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  link.click()
  URL.revokeObjectURL(url)
}
