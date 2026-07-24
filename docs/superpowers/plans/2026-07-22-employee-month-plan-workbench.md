# Employee Month Plan Workbench Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the employee month-plan detail and edit tables with a desktop task-card workbench that improves scanning, editing, validation, and approval submission.

**Architecture:** Keep existing APIs and page-level orchestration, introduce focused presentational components for read-only and editable task cards, and move validation/save/submit feedback into a sticky edit sidebar. Page-scoped CSS provides the new desktop layout without changing unrelated screens.

**Tech Stack:** Vue 3 Composition API, TypeScript, Element Plus, Vue Router, Vite.

## Global Constraints

- Target desktop widths from 1280px through 1920px; mobile-specific layout is out of scope.
- Keep existing backend APIs, database schema, approval flow, permissions, AI request protection, and auto-save request-version protection.
- Do not add dependencies or create a Git commit.
- Core task content must not require horizontal page scrolling or hover-only tooltips.

---

### Task 1: Shared task-card components

**Files:**
- Create: `web-admin/src/views/employee/components/MonthPlanTaskCard.vue`
- Create: `web-admin/src/views/employee/components/MonthPlanTaskEditorCard.vue`

**Interfaces:**
- `MonthPlanTaskCard` consumes `EmployeeMonthPlanItem`, index, editability flags and emits extra-task actions.
- `MonthPlanTaskEditorCard` consumes an editable task object, validation issues and disabled/loading flags; emits optimize, duplicate and remove actions.

- [x] Build a read-only task card with header metadata, three semantic content columns, completion metadata and extra-task actions.
- [x] Build an editable task card with grouped fields, expandable remarks, deterministic DOM ids for issue location, and task-level actions.
- [x] Confirm all required data is passed via props and neither component calls an API.

### Task 2: Detail-page workbench

**Files:**
- Modify: `web-admin/src/views/employee/EmployeeMonthPlanDetail.vue`

**Interfaces:**
- Consumes existing `EmployeeMonthPlanDetailResp` and existing action methods.
- Uses `MonthPlanTaskCard` and maps its events to `openExtraTaskEditor`, `withdrawExtraTask`, and `resubmitExtraTask`.

- [x] Replace the generic page header and descriptions table with a plan cockpit containing month, summary, employee, department, metrics, state and one primary next action.
- [x] Add a prominent approval/rejection feedback banner only when feedback exists.
- [x] Replace the wide item table with `MonthPlanTaskCard` instances and an actionable empty state.
- [x] Merge result summary and deliverables into a compact task-oriented results section; preserve the process timeline.
- [x] Add scoped desktop styling and verify no page-level horizontal scrolling at 1280px.

### Task 3: Edit-page validation model and task editor

**Files:**
- Modify: `web-admin/src/views/employee/EmployeeMonthPlanEdit.vue`

**Interfaces:**
- Define `ValidationIssue { id: string; itemIndex?: number; field?: string; message: string }`.
- Computed `validationIssues` is the single frontend source for sidebar issues and submit blocking.
- `locateValidationIssue(issue)` scrolls to `[data-field-id]` and focuses its input.

- [x] Replace the editable table with `MonthPlanTaskEditorCard` instances.
- [x] Add task duplication that removes persisted ids and inserts the copy after its source.
- [x] Derive validation issues for empty tasks, required fields, dates, hours, weights and total regular weight.
- [x] Route AI issue location through the same deterministic field-location mechanism when possible.
- [x] Preserve current auto-save and stale-response protection while editing task objects through child components.

### Task 4: Sticky editing sidebar and AI drawer

**Files:**
- Modify: `web-admin/src/views/employee/EmployeeMonthPlanEdit.vue`

**Interfaces:**
- Existing `MonthPlanAiAssistant` and `MonthPlanAiCheckPanel` move unchanged into an `el-drawer` controlled by `aiDrawerVisible`.
- Sidebar uses existing `saveDraft`, `goDetail`, and `submitApproval` methods.

- [x] Create the two-column desktop workbench with a sticky sidebar.
- [x] Show task count, total hours, weight progress, auto-save status and clickable validation issues.
- [x] Move save/submit actions exclusively into the sidebar; rename manual secondary action to “保存并返回”.
- [x] Disable submit when blocking validation exists and display the reason next to the action.
- [x] Replace the always-visible AI workspace with an on-demand drawer while preserving all AI behaviors.
- [x] Keep rejection feedback visible above the editor when an approval comment exists.

### Task 5: Submission behavior and verification

**Files:**
- Modify: `web-admin/src/views/employee/EmployeeMonthPlanEdit.vue`
- Verify: `web-admin/src/views/employee/EmployeeMonthPlanDetail.vue`
- Verify: `web-admin/src/views/employee/components/MonthPlanTaskCard.vue`
- Verify: `web-admin/src/views/employee/components/MonthPlanTaskEditorCard.vue`

**Interfaces:**
- `submitApproval` first checks `validationIssues`; on error it locates the first issue and does not call the API.
- Successful submission continues routing to the detail page.

- [x] Update submit confirmation to summarize task count, total hours and 100% weight readiness.
- [x] Preserve user input after API or AI failures and keep errors near the relevant action.
- [x] Run `npm run build` from `web-admin`; expected result is successful `vue-tsc --noEmit` and Vite production build.
- [x] Inspect Git diff for accidental API/business-rule changes and confirm only scoped UI/docs files changed.
