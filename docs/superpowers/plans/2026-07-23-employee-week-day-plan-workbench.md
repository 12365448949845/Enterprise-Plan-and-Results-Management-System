# Employee Week and Day Plan Workbench Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将员工端周计划和日计划的列表、编辑与只读页面重构为一致的任务卡片工作台，同时保持所有后端接口、状态流转和权限规则不变。

**Architecture:** 页面组件继续负责编排路由和 API；共享展示组件承载状态摘要、审批反馈和提交前检查；周计划使用任务卡编辑，日计划在现有 `EmployeeDayPlanEditor` 抽取基础上增加日期导航、未保存保护和两栏检查。所有修改均限制在员工端 Vue 前端。

**Tech Stack:** Vue 3.5、TypeScript 5.7、Element Plus 2.9、Vue Router 4.5、Vite 6。

## Global Constraints

- 不修改后端接口、请求参数、响应模型、数据库、审批链、状态流转或权限规则。
- 周计划和日计划保持独立菜单与路由。
- 保留手动“保存草稿”和“提交审批”，不引入自动保存。
- 不覆盖或回退工作区中已有的 `EmployeeDashboard.vue`、`EmployeeDayPlanEdit.vue` 和 `EmployeeDayPlanEditor.vue` 改动。
- 不新增运行时依赖。
- 仅面向桌面 Web；1280px、1440px、1920px 不产生页面级横向滚动。
- 按用户要求不执行 Git 提交；计划中的验收以构建和工作区 diff 为准。

---

## File Structure

- Create: `web-admin/src/views/employee/components/PlanFeedbackBanner.vue` — 周、日计划统一审批和驳回反馈。
- Create: `web-admin/src/views/employee/components/PlanValidationAside.vue` — 未保存状态、字段问题、保存与提交操作。
- Create: `web-admin/src/views/employee/components/WeekPlanTaskEditorCard.vue` — 周任务编辑卡、来源选择、复制、删除和字段定位。
- Modify: `web-admin/src/views/employee/EmployeeWeekPlanEdit.vue` — 周计划两栏工作台、快照、实时校验和任务操作。
- Modify: `web-admin/src/views/employee/EmployeeWeekPlanList.vue` — 当前周/待处理摘要、紧凑筛选和计划摘要卡。
- Modify: `web-admin/src/views/employee/EmployeeWeekPlanDetail.vue` — 驾驶舱、反馈条和只读任务卡。
- Modify: `web-admin/src/views/employee/components/EmployeeDayPlanEditor.vue` — 在已有可复用编辑器上增加日期导航、快照保护、两栏编辑和检查栏。
- Modify: `web-admin/src/views/employee/EmployeeDayPlanList.vue` — 本周日期导航、当前日期状态和简化列表字段。

### Task 1: Build Shared Feedback and Validation Components

**Files:**
- Create: `web-admin/src/views/employee/components/PlanFeedbackBanner.vue`
- Create: `web-admin/src/views/employee/components/PlanValidationAside.vue`

**Interfaces:**
- `PlanFeedbackBanner` consumes `status`, `comment`, `title?`, `nextStep?`.
- `PlanValidationAside` consumes `issues`, `dirty`, `saving`, `submitting`, `editable`, `saveError?`, `summaryItems?`; emits `locate`, `save`, `submit`.
- `PlanValidationIssue` is exported with `{ key: string; label: string; targetId?: string; blocking: boolean }`.

- [ ] **Step 1: Create the feedback banner**

Implement a semantic `<section>` that renders nothing when `comment` is empty, maps rejected statuses (`REJECTED`/`rejected`) to danger styling, and displays the supplied next step below the comment.

- [ ] **Step 2: Create the validation aside**

Render summary rows, dirty/save state, blocking and advisory issue groups, clickable issue buttons, and footer actions. Disable submit when `!editable || blockingIssues.length > 0 || saving || submitting`; keep save available only when editable.

- [ ] **Step 3: Verify component type-checking**

Run: `npm run build`

Expected: `vue-tsc --noEmit` and Vite build both succeed; no missing props or emit type errors.

### Task 2: Refactor the Week Plan Editor

**Files:**
- Create: `web-admin/src/views/employee/components/WeekPlanTaskEditorCard.vue`
- Modify: `web-admin/src/views/employee/EmployeeWeekPlanEdit.vue`

**Interfaces:**
- `WeekPlanTaskEditorCard` consumes `item`, `index`, `options`, `weekStart`, `weekEnd`, `disabled`, `fieldErrors`; emits `update:item`, `duplicate`, `remove`.
- The page provides `locateIssue(targetId: string)`, `duplicateItem(index: number)`, `removeItem(index: number)`, `saveDraft()`, and `submitPlan()`.

- [ ] **Step 1: Implement the task editor card**

Create a card with source selector, content, deliverable and finish date sections. Use stable DOM ids `week-item-{index}-source`, `week-item-{index}-content`, `week-item-{index}-deliverable`, and `week-item-{index}-finish-date`. Exclude already-used source ids except the card's current value. Duplicate must emit to the page; remove must emit to the page.

- [ ] **Step 2: Replace the long week form with a workbench layout**

Use a main column for period/feedback/task cards and a sticky aside for validation/actions. Keep the existing API functions and payload shape. Existing plans show the natural week as read-only; new plans retain the Monday date picker.

- [ ] **Step 3: Add normalized snapshots and dirty protection**

Define `normalizeForm()` from `weekStart` and trimmed item fields, update `savedSnapshot` after load/save, compute `dirty`, register `onBeforeRouteLeave`, and confirm before leaving when dirty. Do not create drafts automatically.

- [ ] **Step 4: Add real-time validation and field location**

Produce blocking issues for missing week, missing source, duplicate source, empty content, invalid finish date and zero valid tasks. Clicking an issue expands the card, calls `scrollIntoView({ behavior: 'smooth', block: 'center' })`, then focuses the first input/textarea/button in the target.

- [ ] **Step 5: Add duplicate and guarded delete behavior**

Duplicate content/deliverable/finish date but set `monthPlanItemId: null`. Remove a blank card immediately; require `ElMessageBox.confirm` for a populated card. Always retain at least one empty card.

- [ ] **Step 6: Separate save and submit**

`saveDraft()` calls create/update only and updates the snapshot. `submitPlan()` first blocks on validation, saves the current payload, calls the existing submit API, then returns to `/employee/week-plans`. Preserve version numbers returned by the server.

- [ ] **Step 7: Build the frontend**

Run: `npm run build`

Expected: build succeeds and the editor has no TypeScript errors.

### Task 3: Refactor Week Plan List and Detail

**Files:**
- Modify: `web-admin/src/views/employee/EmployeeWeekPlanList.vue`
- Modify: `web-admin/src/views/employee/EmployeeWeekPlanDetail.vue`

**Interfaces:**
- Both pages continue to consume `WeekPlanSummary`, `WeekPlanDetail`, and `weekPlanStatusMeta` from `@/api/weekPlan`.
- Detail uses `PlanFeedbackBanner`.

- [ ] **Step 1: Add week list derived state**

Compute the current Monday/range, current plan, pending count, actionable count, filtered rows and result count. “只看待处理” includes `DRAFT` and `REJECTED` without changing API calls.

- [ ] **Step 2: Replace the wide table with plan summary cards**

Each card displays week range, title, item count, status, approval excerpt and submit time. Primary action is edit for draft/rejected and view otherwise. Pending withdrawal and deletions remain guarded secondary actions.

- [ ] **Step 3: Add actionable empty states**

Show “本周尚未创建计划” with a create action when appropriate, or “当前筛选条件下没有计划” with a reset action after filtering.

- [ ] **Step 4: Refactor week detail to read-only cards**

Replace the descriptions/table layout with a header summary, feedback banner, and vertical task cards containing source, content, deliverable, finish date and parent weight. Preserve the existing edit permission calculation.

- [ ] **Step 5: Build the frontend**

Run: `npm run build`

Expected: build succeeds; no API or router changes are required.

### Task 4: Upgrade the Existing Day Plan Editor Without Overwriting Current Work

**Files:**
- Modify: `web-admin/src/views/employee/components/EmployeeDayPlanEditor.vue`
- Verify only: `web-admin/src/views/employee/EmployeeDayPlanEdit.vue`
- Verify only: `web-admin/src/views/employee/EmployeeDashboard.vue`

**Interfaces:**
- Preserve props `{ date: string; compact?: boolean }`.
- Preserve emits `changed` and `date-change` so the dashboard dialog integration remains functional.
- Reuse `PlanFeedbackBanner` and `PlanValidationAside`.

- [ ] **Step 1: Add date navigation helpers**

Implement previous day, today and next day actions using local date arithmetic. Route changes continue through `date-change`; compact mode keeps the same component contract.

- [ ] **Step 2: Add normalized snapshots**

Snapshot `planDate`, `relatedMonthPlanItemId`, trimmed `content`, and trimmed `remark` after detail load and successful save. Compute `dirty` from the current normalized form.

- [ ] **Step 3: Guard date changes and route departure**

Before changing `selectedDate`, confirm when dirty. If canceled, restore the prior selected date without loading. Expose a component method `confirmDiscardChanges(): Promise<boolean>` only if needed by the wrapper; otherwise use `onBeforeRouteLeave` inside the component.

- [ ] **Step 4: Replace top action duplication with two-column workbench actions**

Keep refresh and withdraw in the header. Move save and AI-check/submit into the validation aside. In compact mode, retain a concise header and ensure the same actions remain reachable.

- [ ] **Step 5: Reorganize source, feedback and content**

Add a source panel containing date, organization and related month item. Render leader/department comments through the feedback banner/read-only feedback blocks. Keep AI review behavior and dialogs unchanged.

- [ ] **Step 6: Add day validation and problem location**

Block submission for missing date or content. Treat missing related month item as an advisory unless the current backend rejects it. Locate content/source fields via stable DOM ids.

- [ ] **Step 7: Preserve dashboard dialog compatibility**

Confirm that `compact` rendering, `changed`, and `date-change` still work in `EmployeeDashboard.vue`. Do not rewrite the dashboard's existing uncommitted dialog feature.

- [ ] **Step 8: Build the frontend**

Run: `npm run build`

Expected: build succeeds and both route page and dashboard dialog compile.

### Task 5: Refactor Day Plan List

**Files:**
- Modify: `web-admin/src/views/employee/EmployeeDayPlanList.vue`

**Interfaces:**
- Continue using `listDayPlansApi` and `withdrawEmployeeDayPlanApi`.
- Continue navigating to `/employee/daily-plan?date=YYYY-MM-DD`.

- [ ] **Step 1: Add weekly navigation derived state**

Compute the Monday of the active week and seven date cells. Match loaded `DayPlan` rows by `planDate` and display each date's status. Provide previous week, current week and next week controls.

- [ ] **Step 2: Add current-day summary and dynamic main action**

For today, show whether a plan exists, its status and content excerpt. Use “编制今天计划”, “继续编辑”, or “查看今天计划” based on the matched row.

- [ ] **Step 3: Simplify filters and record presentation**

Move date range/status filters into a compact collapsible section. Keep pagination. Reduce visible columns/cards to date, content summary, status, risk and leader feedback; reveal department feedback in expandable content.

- [ ] **Step 4: Add contextual empty states**

No loaded plans for a selected week shows a week-specific empty message and today/create action. Filtered no-results shows reset controls without implying that no plans exist globally.

- [ ] **Step 5: Build the frontend**

Run: `npm run build`

Expected: build succeeds and list actions navigate with an explicit date query.

### Task 6: Final Regression and Scope Audit

**Files:**
- Verify: all files listed above.

- [ ] **Step 1: Run the production build**

Run: `npm run build`

Expected: `vue-tsc --noEmit` passes and Vite emits `dist/` successfully.

- [ ] **Step 2: Inspect the focused diff**

Run: `git diff -- web-admin/src/views/employee/EmployeeWeekPlanList.vue web-admin/src/views/employee/EmployeeWeekPlanEdit.vue web-admin/src/views/employee/EmployeeWeekPlanDetail.vue web-admin/src/views/employee/EmployeeDayPlanList.vue web-admin/src/views/employee/EmployeeDayPlanEdit.vue web-admin/src/views/employee/components/EmployeeDayPlanEditor.vue web-admin/src/views/employee/components/PlanFeedbackBanner.vue web-admin/src/views/employee/components/PlanValidationAside.vue web-admin/src/views/employee/components/WeekPlanTaskEditorCard.vue`

Expected: no backend/API/router contract changes, no unrelated month-plan edits, and existing dashboard/day-editor integration remains present.

- [ ] **Step 3: Check forbidden scope changes**

Run: `git diff --name-only -- backend web-admin/src/api web-admin/src/router`

Expected: no output for changes made by this implementation.

- [ ] **Step 4: Manual desktop acceptance**

At 1280px, 1440px and 1920px verify: no page-level horizontal scroll; sticky validation aside remains visible; long content wraps; draft/rejected edit; pending withdrawal; approved read-only; date switching prompts on dirty day plans; issue buttons focus fields; save failures preserve input.

- [ ] **Step 5: Report without committing**

Summarize changed files, build result, manual limitations and preserved pre-existing worktree changes. Do not run `git add` or `git commit`.
