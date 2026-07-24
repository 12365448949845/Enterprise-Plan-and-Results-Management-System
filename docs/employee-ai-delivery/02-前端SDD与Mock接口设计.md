# 前端 SDD 与 Mock 接口设计

## 1. 文档目标
本文件定义员工端前端首版的页面结构、路由、状态展示、接口契约和 mock 数据规则，用于先完成前端，再平滑切到后端真实接口。

## 2. 技术约束
- 项目位置：`代码/planning-platform/web-admin`
- 技术栈：Vue 3、TypeScript、Element Plus、Vue Router、Axios
- 现有接口封装：`src/api/http.ts`
- 员工端 API 文件：`src/api/employee.ts`
- 路由文件：`src/router/index.ts`

## 3. 前端目录建议
### 3.1 API
- `src/api/employee.ts`：员工端统一接口类型、mock 数据、API 调用函数

### 3.2 页面
- `src/views/employee/EmployeeDashboard.vue`
- `src/views/employee/EmployeeMonthPlanDetail.vue`
- `src/views/employee/EmployeeMonthPlanEdit.vue`
- `src/views/employee/EmployeeDayPlanEdit.vue`
- `src/views/employee/EmployeeResultSubmit.vue`
- `src/views/employee/EmployeePerformanceEvidence.vue`
- `src/views/employee/EmployeeAppeals.vue`

### 3.3 可选拆分
如果页面内状态映射重复较多，可在员工目录下新增一个局部常量文件，但不要过度抽象。

## 4. 路由清单
| 页面 | 路由 | 说明 |
| --- | --- | --- |
| 登录页 | `/login` | 现有页面保留，补员工角色跳转逻辑 |
| 员工工作台 | `/employee/dashboard` | 现有页面继续增强 |
| 月计划详情 | `/employee/month-plans/:id` | 新增 |
| 月计划编辑 | `/employee/month-plans/:id/edit` | 新增 |
| 月计划新建/编辑 | `/employee/month-plans/new/edit` | 复用编辑页 |
| 日计划编制 | `/employee/day-plans/edit` | 新增 |
| 成果提交 | `/employee/results/submit` | 新增 |
| 本人绩效依据 | `/employee/performance-evidence` | 新增 |
| 申诉记录 | `/employee/appeals` | 新增 |

## 5. 统一状态设计
### 5.1 计划状态
| 值 | 文案 | 说明 |
| --- | --- | --- |
| draft | 草稿 | 员工可编辑 |
| submitted | 已提交 | 等待审批 |
| approved | 已通过 | 领导审批通过 |
| rejected | 已驳回 | 员工可修改后再提交 |
| confirmed | 已确认 | 成果或计划被确认 |
| archived | 已归档 | 只读 |

### 5.2 成果状态
| 值 | 文案 | 说明 |
| --- | --- | --- |
| not_submitted | 未提交 | 尚未上传成果 |
| draft | 草稿 | 已保存但未提交 |
| submitted | 已提交确认 | 等待领导确认 |
| confirmed | 已确认 | 已确认通过 |
| rejected | 已退回 | 需补充后重新提交 |

### 5.3 申诉状态
| 值 | 文案 |
| --- | --- |
| draft | 草稿 |
| submitted | 已提交 |
| processing | 处理中 |
| resolved | 已裁决 |
| closed | 已关闭 |

## 6. 统一类型模型
建议在 `src/api/employee.ts` 中集中维护以下类型。

### 6.1 工作台
- `EmployeeDashboardResp`
- `EmployeeMonthPlan`
- `EmployeeDayPlanCalendarItem`

### 6.2 月计划详情与编辑
- `EmployeeMonthPlanDetailResp`
- `EmployeeMonthPlanItem`
- `EmployeeDeliverable`
- `EmployeeResultSummary`
- `ConfirmRecord`
- `SaveMonthPlanDraftReq`

### 6.3 日计划
- `EmployeeDayPlanDetailResp`
- `SaveDayPlanDraftReq`
- `SubmitDayPlanReq`

### 6.4 成果提交
- `EmployeeResultSubmitOptionsResp`
- `SubmitEmployeeResultReq`
- `EmployeeResultVersionResp`

### 6.5 绩效依据
- `EmployeePerformanceEvidenceItem`
- `EmployeePerformanceEvidenceResp`

### 6.6 申诉记录
- `EmployeeAppealItem`
- `EmployeeAppealListResp`

## 7. 页面设计与接口契约
### 7.1 P02 员工工作台
#### 输入
- 月份 `month`

#### 输出
- 当前月份
- 汇总卡片
- 月计划列表
- 日计划日历

#### 接口
- `GET /api/employee/dashboard?month=2026-07`

#### 响应示例
```json
{
  "currentMonth": "2026-07",
  "monthPlans": [
    {
      "id": 1,
      "planMonth": "2026-07",
      "title": "7月重点工作计划",
      "planStatus": "draft",
      "resultStatus": "not_submitted",
      "completionRate": 35,
      "updatedAt": "2026-07-13 09:30"
    }
  ],
  "dayPlanCalendar": [
    {
      "date": "2026-07-13",
      "status": "draft"
    }
  ],
  "summary": {
    "monthPlanCount": 2,
    "submittedResultCount": 4,
    "averageCompletionRate": 68
  }
}
```

### 7.2 P03 月计划详情
#### 接口
- `GET /api/employee/month-plans/{id}`

#### 响应结构
```json
{
  "id": 1,
  "planMonth": "2026-07",
  "employeeName": "张三",
  "departmentName": "经营计划部",
  "status": "draft",
  "resultStatus": "submitted",
  "updatedAt": "2026-07-13 09:30",
  "items": [
    {
      "id": 101,
      "taskName": "月度经营分析",
      "taskContent": "完成月经营分析报告",
      "target": "按时提交经营分析",
      "progress": "已完成数据收集和初稿",
      "deliverable": "经营分析报告初稿",
      "completionRate": 60,
      "status": "draft",
      "sortNo": 1
    }
  ],
  "deliverables": [
    {
      "id": 1,
      "name": "经营分析报告初稿.pdf",
      "fileType": "pdf",
      "relatedTaskName": "月度经营分析",
      "submittedAt": "2026-07-13 09:20",
      "fileUrl": "/mock/files/report.pdf"
    }
  ],
  "resultSummary": {
    "submittedCount": 1,
    "confirmedCount": 0,
    "rejectedCount": 0,
    "latestVersion": "V1",
    "overallCompletionRate": 60
  },
  "confirmRecords": [
    {
      "id": 1,
      "bizType": "month_plan",
      "bizId": 1,
      "operatorName": "张三",
      "action": "保存草稿",
      "comment": "补充了目标要求",
      "createdAt": "2026-07-13 09:30"
    }
  ]
}
```

### 7.3 P04 月计划编辑
#### 查询接口
- `GET /api/employee/month-plans/{id}`

#### 保存草稿
- `POST /api/employee/month-plans/{id}/draft`

#### 提交审批
- `POST /api/employee/month-plans/{id}/submit`

#### 保存草稿请求示例
```json
{
  "summary": "本月围绕经营分析和台账治理推进",
  "items": [
    {
      "id": 101,
      "taskName": "月度经营分析",
      "taskContent": "完成月经营分析报告",
      "target": "按时提交经营分析",
      "deliverable": "经营分析报告初稿",
      "completionRate": 60,
      "remark": "等待领导确认"
    }
  ]
}
```

#### 页面约束
- 仅 `draft`、`rejected` 可编辑
- 内容变更后 3 秒自动保存草稿
- 保存状态显示：未保存、保存中、已自动保存、保存失败
- `submitted`、`approved`、`archived` 只读

### 7.4 P05 日计划编制
#### 查询接口
- `GET /api/employee/day-plans/detail?date=2026-07-14`

#### 保存草稿
- `POST /api/employee/day-plans/draft`

#### 提交
- `POST /api/employee/day-plans/submit`

#### 查询响应示例
```json
{
  "id": 11,
  "planDate": "2026-07-14",
  "relatedMonthPlanItemId": 101,
  "content": "整理经营数据，完善报告图表",
  "remark": "下午补充异常说明",
  "status": "draft",
  "monthPlanItemOptions": [
    {
      "id": 101,
      "taskName": "月度经营分析"
    }
  ]
}
```

### 7.5 P06 成果提交
#### 预加载接口
- `GET /api/employee/results/submit/options`

#### 提交接口
- `POST /api/employee/results/submit`
- `Content-Type: multipart/form-data`

#### 提交字段
| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| monthPlanId | number | 是 | 月计划 ID |
| monthPlanItemId | number | 否 | 月计划明细 ID |
| completionRate | number | 是 | 0-100 |
| description | string | 否 | 成果说明 |
| file | binary | 是 | PDF/Word/Zip |

#### 提交响应示例
```json
{
  "id": 21,
  "versionNo": "V2",
  "status": "submitted",
  "submittedAt": "2026-07-14 10:20"
}
```

### 7.6 P07 本人绩效依据
#### 接口
- `GET /api/employee/performance-evidence?periodType=month`

#### 响应示例
```json
{
  "periodType": "month",
  "items": [
    {
      "id": 1,
      "evidenceDate": "2026-07-01",
      "periodType": "month",
      "sourceType": "month_plan",
      "title": "7月月计划达成",
      "description": "完成率 68%，已生成绩效依据",
      "score": 12,
      "createdAt": "2026-07-14 09:00"
    }
  ]
}
```

### 7.7 P08 申诉记录
#### 接口
- `GET /api/employee/appeals`

#### 响应示例
```json
{
  "items": [
    {
      "id": 1,
      "appealNo": "AP20260714001",
      "title": "7月经营分析得分申诉",
      "reason": "部分成果确认时间晚于统计批次",
      "status": "processing",
      "createdAt": "2026-07-14 09:00"
    }
  ]
}
```

## 8. Mock 设计规则
### 8.1 原则
1. 所有 API 函数先走真实接口请求。
2. 请求失败时回退到本地 mock 数据。
3. mock 数据按接口粒度拆分，不要把所有对象硬塞进一个常量。
4. mock 数据时间、状态、字段必须能覆盖草稿、提交、驳回、通过等关键状态。

### 8.2 推荐实现方式
在 `src/api/employee.ts` 中维护：
- 类型定义
- mock 常量
- API 调用函数
- 请求失败回退逻辑

### 8.3 前端暴露接口清单
建议前端统一暴露以下函数：
- `getEmployeeDashboardApi(month)`
- `getEmployeeMonthPlanDetailApi(id)`
- `saveEmployeeMonthPlanDraftApi(id, payload)`
- `submitEmployeeMonthPlanApi(id)`
- `getEmployeeDayPlanDetailApi(date)`
- `saveEmployeeDayPlanDraftApi(payload)`
- `submitEmployeeDayPlanApi(payload)`
- `getEmployeeResultSubmitOptionsApi()`
- `submitEmployeeResultApi(formData)`
- `getEmployeePerformanceEvidenceApi(periodType)`
- `getEmployeeAppealsApi()`

## 9. 前端开发顺序
1. 先扩展 `employee.ts` 类型和 mock 数据。
2. 再补齐路由。
3. 然后按页面从工作台、详情、编辑、日计划、成果、绩效依据、申诉记录依次开发。
4. 每开发完一个页面，先用 mock 数据自验再进入下一个页面。

## 10. 页面验收清单
1. 页面路由可访问。
2. 页面有真实入口，不是孤立页面。
3. 页面在无真实后端时能通过 mock 数据展示完整信息。
4. 所有列表有空状态。
5. 所有提交动作有成功或失败反馈。
6. 状态标签文案和颜色一致。
7. 编辑/只读边界按状态生效。
8. 上传、日期、必填项等关键控件符合约束。
