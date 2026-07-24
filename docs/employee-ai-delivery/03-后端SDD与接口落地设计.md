# 后端 SDD 与接口落地设计

## 1. 文档目标
本文件定义员工端后端首版的表结构边界、接口职责、服务规则、权限校验和状态流转，确保后端按前端已冻结的字段契约落地实现。

## 2. 开发边界
- 后端项目：`代码/planning-platform/backend`
- 目标：优先支撑员工端接口，不先做无关管理后台能力
- 原则：字段名、状态值、接口路径尽量与前端 SDD 保持一致
- 权限边界：所有员工端接口都必须从登录态获取当前员工身份，不信任前端传入的员工 ID

## 3. 核心业务对象
### 3.1 月计划主表 `employee_month_plan`
| 字段 | 类型建议 | 说明 |
| --- | --- | --- |
| id | bigint | 主键 |
| plan_month | varchar(7) | 计划月份，格式 `YYYY-MM` |
| employee_id | bigint | 员工 ID |
| employee_name | varchar(64) | 冗余员工姓名 |
| department_id | bigint | 部门 ID |
| department_name | varchar(64) | 冗余部门名称 |
| status | varchar(32) | 计划状态 |
| result_status | varchar(32) | 成果状态 |
| completion_rate | int | 总完成率 |
| summary | varchar(1000) | 落实情况摘要 |
| submitted_at | datetime | 提交时间 |
| approved_at | datetime | 审批通过时间 |
| created_by | bigint | 创建人 |
| created_at | datetime | 创建时间 |
| updated_by | bigint | 更新人 |
| updated_at | datetime | 更新时间 |
| deleted | tinyint | 逻辑删除 |

### 3.2 月计划明细表 `employee_month_plan_item`
| 字段 | 类型建议 | 说明 |
| --- | --- | --- |
| id | bigint | 主键 |
| month_plan_id | bigint | 所属月计划 |
| task_name | varchar(200) | 工作事项 |
| task_content | varchar(2000) | 工作内容 |
| target | varchar(2000) | 目标要求 |
| deliverable | varchar(500) | 交付物 |
| progress | varchar(2000) | 落实情况 |
| completion_rate | int | 完成比例 |
| status | varchar(32) | 明细状态 |
| sort_no | int | 排序 |
| remark | varchar(1000) | 备注 |
| created_by | bigint | 创建人 |
| created_at | datetime | 创建时间 |
| updated_by | bigint | 更新人 |
| updated_at | datetime | 更新时间 |
| deleted | tinyint | 逻辑删除 |

### 3.3 日计划表 `employee_day_plan`
| 字段 | 类型建议 | 说明 |
| --- | --- | --- |
| id | bigint | 主键 |
| plan_date | date | 日期 |
| employee_id | bigint | 员工 ID |
| related_month_plan_item_id | bigint | 关联月计划明细 |
| content | varchar(4000) | 工作内容 |
| remark | varchar(1000) | 备注 |
| status | varchar(32) | 状态 |
| submitted_at | datetime | 提交时间 |
| created_by | bigint | 创建人 |
| created_at | datetime | 创建时间 |
| updated_by | bigint | 更新人 |
| updated_at | datetime | 更新时间 |
| deleted | tinyint | 逻辑删除 |

### 3.4 成果表 `employee_result_submission`
| 字段 | 类型建议 | 说明 |
| --- | --- | --- |
| id | bigint | 主键 |
| month_plan_id | bigint | 月计划 ID |
| month_plan_item_id | bigint | 月计划明细 ID |
| employee_id | bigint | 员工 ID |
| version_no | varchar(32) | 成果版本号 |
| completion_rate | int | 完成比例 |
| description | varchar(2000) | 成果说明 |
| file_name | varchar(255) | 文件名 |
| file_type | varchar(32) | 文件类型 |
| file_url | varchar(500) | 文件地址 |
| status | varchar(32) | 成果状态 |
| submitted_at | datetime | 提交时间 |
| confirmed_at | datetime | 确认时间 |
| created_by | bigint | 创建人 |
| created_at | datetime | 创建时间 |
| updated_by | bigint | 更新人 |
| updated_at | datetime | 更新时间 |
| deleted | tinyint | 逻辑删除 |

### 3.5 绩效依据表 `employee_performance_evidence`
| 字段 | 类型建议 | 说明 |
| --- | --- | --- |
| id | bigint | 主键 |
| employee_id | bigint | 员工 ID |
| evidence_date | date | 依据日期 |
| period_type | varchar(16) | day/week/month/quarter/year |
| source_type | varchar(32) | 来源类型 |
| title | varchar(255) | 标题 |
| description | varchar(2000) | 说明 |
| score | decimal(10,2) | 分值或权重 |
| created_at | datetime | 生成时间 |
| deleted | tinyint | 逻辑删除 |

### 3.6 申诉记录表 `employee_appeal_record`
| 字段 | 类型建议 | 说明 |
| --- | --- | --- |
| id | bigint | 主键 |
| appeal_no | varchar(64) | 申诉编号 |
| employee_id | bigint | 员工 ID |
| title | varchar(255) | 申诉标题 |
| reason | varchar(2000) | 申诉原因 |
| status | varchar(32) | 申诉状态 |
| created_at | datetime | 发起时间 |
| updated_at | datetime | 更新时间 |
| deleted | tinyint | 逻辑删除 |

### 3.7 确认记录表 `confirm_record`
| 字段 | 类型建议 | 说明 |
| --- | --- | --- |
| id | bigint | 主键 |
| biz_type | varchar(32) | 业务类型 |
| biz_id | bigint | 业务 ID |
| operator_id | bigint | 操作人 ID |
| operator_name | varchar(64) | 操作人姓名 |
| action | varchar(64) | 操作动作 |
| comment | varchar(1000) | 意见 |
| created_at | datetime | 操作时间 |

## 4. 状态流转规则
### 4.1 月计划状态
- `draft` -> `submitted`：员工提交审批
- `submitted` -> `approved`：领导审批通过
- `submitted` -> `rejected`：领导驳回
- `approved` -> `confirmed`：后续确认完成
- `confirmed` -> `archived`：归档

约束：
1. `draft`、`rejected` 可编辑
2. `submitted`、`approved`、`confirmed`、`archived` 不可由员工编辑
3. 员工不能直接写入 `approved`、`confirmed`、`archived`

### 4.2 成果状态
- `draft` -> `submitted`
- `submitted` -> `confirmed`
- `submitted` -> `rejected`
- `rejected` -> `submitted`

### 4.3 日计划状态
- `draft` -> `submitted`
- `submitted` 后只读

## 5. 接口清单
### 5.1 登录
- `POST /api/auth/login`
- 说明：返回 token、用户信息、角色信息

### 5.2 工作台
- `GET /api/employee/dashboard`
- 入参：`month`
- 返回：当前月份、月计划列表、日计划日历、汇总数据

### 5.3 月计划
- `GET /api/employee/month-plans/{id}`
- `POST /api/employee/month-plans/{id}/draft`
- `POST /api/employee/month-plans/{id}/submit`

### 5.4 日计划
- `GET /api/employee/day-plans/detail`
- `POST /api/employee/day-plans/draft`
- `POST /api/employee/day-plans/submit`

### 5.5 成果提交
- `GET /api/employee/results/submit/options`
- `POST /api/employee/results/submit`

### 5.6 绩效依据
- `GET /api/employee/performance-evidence`

### 5.7 申诉记录
- `GET /api/employee/appeals`

## 6. 服务层职责
### 6.1 EmployeeDashboardService
负责：
- 聚合员工当月月计划列表
- 聚合当月日计划日历状态
- 计算汇总卡片

### 6.2 EmployeeMonthPlanService
负责：
- 查询月计划详情
- 保存月计划草稿
- 提交月计划审批
- 校验状态可编辑性
- 写入确认记录

### 6.3 EmployeeDayPlanService
负责：
- 按日期查询本人日计划
- 保存草稿
- 提交日计划
- 维护工作台日历状态

### 6.4 EmployeeResultService
负责：
- 查询成果提报下拉选项
- 校验上传文件类型
- 生成版本号
- 保存成果记录
- 更新月计划成果汇总状态
- 写入确认记录

### 6.5 EmployeePerformanceEvidenceService
负责：
- 按时间维度查询本人绩效依据

### 6.6 EmployeeAppealService
负责：
- 查询本人申诉记录

## 7. 权限与校验规则
1. 所有员工端接口从登录上下文获取当前员工 ID。
2. 查询、编辑、提交都必须校验资源归属到当前员工。
3. 禁止通过请求体传入 employeeId 后越权访问他人数据。
4. 月计划草稿保存时校验必填字段最小集。
5. 月计划提交时必须校验工作事项、工作内容、目标要求等完整性。
6. 日计划提交时必须校验日期和工作内容。
7. 成果提交必须校验文件类型和大小。
8. 成果完成比例必须在 0 到 100 之间。
9. 员工端接口不得接受审批通过、驳回等领导专属动作。

## 8. 成果版本规则
首版规则：
1. 以 `monthPlanId + monthPlanItemId` 为维度生成版本序号。
2. 如果无 `monthPlanItemId`，按月计划主维度生成版本号。
3. 第一次提交为 `V1`，后续递增为 `V2`、`V3`。
4. 被退回后再次提交生成新版本，不覆盖旧记录。

## 9. 审计与确认记录
以下动作必须写入 `confirm_record`：
- 保存月计划草稿
- 提交月计划审批
- 领导审批通过
- 领导驳回
- 提交成果
- 成果确认
- 成果退回

建议字段：
- `bizType`
- `bizId`
- `operatorId`
- `operatorName`
- `action`
- `comment`
- `createdAt`

## 10. 开发顺序
1. 先补数据库表和初始化脚本。
2. 再定义 DTO、VO、枚举和接口返回结构。
3. 然后实现查询接口。
4. 再实现保存草稿、提交、上传等写接口。
5. 最后补确认记录、审批反馈联动和权限边界校验。

## 11. 后端验收清单
1. 所有接口路径与前端 SDD 完全一致。
2. 返回字段名与前端 mock 字段完全一致。
3. 员工无法查询或修改他人数据。
4. 非法状态跳转会被后端拒绝。
5. 上传接口只能接受 PDF、Word、Zip。
6. 提交、驳回、通过、确认能写入确认记录。
7. 工作台、详情页、编辑页读取到的状态一致。
8. 被驳回后员工可重新编辑并提交。
