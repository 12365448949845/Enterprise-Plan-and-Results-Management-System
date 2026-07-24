import assert from 'node:assert/strict'
import test from 'node:test'
import {
  availableWorkspaces,
  defaultWorkspacePath,
  visibleWorkspaceItems,
  workspaceForPath,
} from '../src/navigation/workspaces.ts'

test('selects the highest-priority authorized workspace', () => {
  assert.equal(defaultWorkspacePath(['dashboard:view']), '/employee/dashboard')
  assert.equal(defaultWorkspacePath(['dashboard:view', 'leader:workbench:view']), '/leader/workbench')
  assert.equal(
    defaultWorkspacePath(['leader:workbench:view', 'department:dashboard:view']),
    '/department/dashboard',
  )
  assert.equal(
    defaultWorkspacePath(['department:dashboard:view', 'system:dashboard:view']),
    '/system/dashboard',
  )
})

test('keeps the declared order when every workspace is available', () => {
  const permissions = [
    'dashboard:view',
    'leader:workbench:view',
    'department:dashboard:view',
    'system:dashboard:view',
    'dispute:dashboard:view',
  ]

  assert.deepEqual(
    availableWorkspaces(permissions).map((workspace) => workspace.id),
    ['system', 'department', 'leader', 'employee', 'dispute'],
  )
})

test('returns no workspace when no workspace home permission exists', () => {
  assert.equal(defaultWorkspacePath(['planning:month:view']), undefined)
  assert.deepEqual(availableWorkspaces([]), [])
})

test('filters granular menu entries without hiding workspace-level compatibility entries', () => {
  const employee = availableWorkspaces(['dashboard:view'])[0]
  assert.ok(employee)

  assert.deepEqual(
    visibleWorkspaceItems(employee, ['dashboard:view']).map((item) => item.path),
    [
      '/employee/dashboard',
      '/employee/week-plans',
      '/employee/performance-evidence',
      '/employee/appeals',
    ],
  )

  assert.ok(
    visibleWorkspaceItems(employee, ['dashboard:view', 'planning:month:view'])
      .some((item) => item.path === '/employee/month-plans'),
  )
})

test('matches canonical, alias, and nested workspace paths', () => {
  assert.equal(workspaceForPath('/planning/month')?.id, 'employee')
  assert.equal(workspaceForPath('/employee/month-plans/3')?.id, 'employee')
  assert.equal(workspaceForPath('/system/roles')?.id, 'system')
  assert.equal(workspaceForPath('/messages'), undefined)
})
