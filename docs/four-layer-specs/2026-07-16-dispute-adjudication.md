# 裁决端四层规格

日期：2026-07-16
范围：Web Admin 裁决端首版
依据：`原型/app.js` 中 `C01/C02/C03`、`原型/page-list.md`、现有申诉/成果/审计实现

## 1. 目标层

### 1.1 目标

为被授权的裁决评审员提供争议案件的统一复核入口，将员工申诉、原计划及审批、计划调整、成果版本及证据、领导建议、部门最终确认、绩效依据和审计记录聚合为可追溯资料包，并形成只能由人工确认的裁决结论。

### 1.2 范围

- C01 争议案件：按授权范围查看案件、筛选状态和进入资料包。
- C02 裁决资料包：查看完整证据链、评审意见并提交个人意见或最终结论。
- C03 评审小组：查看和维护当前案件的 2-5 名成员、来源和回避状态。
- 首版仅支持 Web Admin；不增加小程序裁决页面。
- 部门负责人已有申诉处理流程保持不变；只有升级到裁决或被授权的案件进入本模块。

### 1.3 角色与边界

- 评审成员：复用现有账号，只有加入具体案件评审小组后，才能查看该案件、查看资料包和提交个人评审意见；不能修改原计划、成果或台账。
- 上级/授权管理员：复用现有超级管理员或部门负责人权限，负责维护评审小组、处理回避并提交最终裁决结论。
- `SUPER_ADMIN`：拥有全量查看和小组管理能力，但仍需按审计要求提交人工结论。
- 员工、直属领导、部门负责人不能访问裁决端接口，除非后续明确增加授权流程。

### 1.4 成功标准

- 授权评审员进入裁决工作台后，5 秒内能找到待处理案件和截止时间。
- 任何裁决结论都能回溯到案件、评审成员、评审意见、资料快照和审计日志。
- 资料缺失、评审人数不在 2-5 人、当前用户回避或结论意见为空时，服务端拒绝结论提交。
- 裁决端只展示真实业务资料，不展示未实际生成的分析内容。

## 2. 展示/交互层

### 2.1 路由和页面

| 编号 | 路由 | 主对象 | 主要动作 |
| --- | --- | --- | --- |
| C00 | `/dispute/dashboard` | 裁决工作台 | 查看待处理、进行中、待补充、已裁决统计和最近案件 |
| C01 | `/dispute/cases` | 争议案件 | 周期/状态/员工筛选，查看资料包 |
| C02 | `/dispute/cases/:id` | 裁决资料包 | 查看资料、提交意见、提交结论 |
| C03 | `/dispute/cases/:id/review-panel` | 评审小组 | 查看成员、添加/移除成员、标记回避 |

页面必须沿用现有 Web Admin 壳层、日期控件、状态标签、反馈和审计说明；业务页面不提供角色切换器。

### 2.2 C00/C01

输入：周期、员工、案件状态、资料完整性。

输出：案件编号、员工、部门、争议对象、申诉原因摘要、状态、资料完整性、评审进度、截止时间。

动作：

- 查询和重置筛选。
- 进入 C02。
- 仅对授权案件显示数据；未授权案件返回 403。

空状态必须说明“当前授权范围内没有案件”；接口异常可重试。

### 2.3 C02

区域：

1. 案件摘要：案件编号、申诉人、部门、周期、争议对象、当前状态、截止时间。
2. 资料目录：原计划/审批、调整记录、成果版本/证据、领导建议、部门确认、台账影响、申诉、审计。
3. 资料详情：按对象展示只读内容和版本链；下载资料包走带水印接口。
4. 评审意见：成员、来源、意见倾向、意见文本、提交时间。
5. 人工裁决：结果为“支持申诉 / 驳回申诉 / 退回补充材料”，必须填写理由。

约束：

- 资料包未完成完整性校验时，允许查看和补充意见，但禁止最终结论。
- 已裁决案件默认只读；超级管理员不能无审计覆盖原结论。
- 个人意见可保存一次后更新，但每次更新保留版本/审计。

### 2.4 C03

输入：案件编号、候选用户、成员来源。

输出：2-5 名成员、当前用户身份、同部门标记、回避状态、回避原因、更新时间。

动作：

- 添加或移除评审成员。
- 标记本人回避并填写原因。
- 重新生成评审资格校验。

人数不足、成员重复、案件当事人/直接处理人加入或回避未处理时，禁止最终裁决。

## 3. 数据/API 层

### 3.1 新增实体

#### `biz_dispute_case`

- `id`、`case_no`、`appeal_id`、`owner_user_id`、`dept_id`、`period_start`、`period_end`
- `dispute_subject`、`status`、`package_status`、`package_checksum`
- `deadline_at`、`decided_by`、`decided_at`、`decision`、`decision_comment`
- 通用创建/更新/删除字段

案件由申诉升级时创建；同一申诉只能对应一个未删除案件。

#### `biz_dispute_reviewer`

- `id`、`case_id`、`user_id`、`source_type`
- `recusal_status`、`recusal_reason`、`joined_at`、`updated_at`
- 唯一键：`case_id + user_id`

#### `biz_dispute_opinion`

- `id`、`case_id`、`reviewer_id`、`opinion`、`comment`
- `version_no`、`submitted_at`、创建/更新字段

#### `biz_dispute_audit`

- `id`、`case_id`、`action`、`operator_id`、`before_json`、`after_json`、`created_at`

关键动作同时写入现有 `sys_audit_log`；专用表用于资料包内的业务时间线。

### 3.2 状态

`SUBMITTED -> REVIEWING -> NEEDS_SUPPLEMENT -> REVIEWING -> DECIDED -> ARCHIVED`

- 创建案件为 `SUBMITTED`。
- 首次打开/加入评审小组后可转 `REVIEWING`。
- 退回补充材料为 `NEEDS_SUPPLEMENT`，补充后由授权人员重新进入 `REVIEWING`。
- 通过申诉、驳回申诉或退回补充材料并完成处理后为 `DECIDED`。
- 按保留策略归档为 `ARCHIVED`。

### 3.3 API

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| GET | `/dispute/dashboard` | `dispute:dashboard:view` | 统计和最近案件 |
| GET | `/dispute/cases` | `dispute:case:view` | 授权案件列表 |
| GET | `/dispute/cases/{id}` | `dispute:case:view` | 案件和资料包 |
| GET | `/dispute/cases/{id}/package` | `dispute:package:view` | 带水印资料包下载 |
| GET | `/dispute/cases/{id}/reviewers` | `dispute:reviewer:view` | 评审小组 |
| POST | `/dispute/cases/{id}/reviewers` | `dispute:reviewer:manage` | 添加成员 |
| DELETE | `/dispute/cases/{id}/reviewers/{userId}` | `dispute:reviewer:manage` | 移除成员 |
| POST | `/dispute/cases/{id}/recusal` | `dispute:reviewer:recuse` | 本人回避 |
| POST | `/dispute/cases/{id}/opinions` | `dispute:opinion:submit` | 提交/更新个人意见 |
| POST | `/dispute/cases/{id}/decision` | `dispute:decision:submit` | 提交最终结论 |

所有接口由服务端校验登录身份、角色、案件授权、案件状态和数据范围；前端隐藏不是权限控制。

### 3.4 决策影响

最终结论保存：

- 结论类型、理由、影响对象和影响说明。
- 是否需要通知申诉人。
- 原申诉状态的前值和新值。
- 评审小组校验快照、资料完整性校验结果和审计编号。

首版不允许通过任意 JSON 直接改分；若“支持申诉”需要修正结果，由受控的申诉处理服务执行并生成对应业务审计。

## 4. 架构层

- 前端：Vue 3 + TypeScript + Vue Router + Pinia + Element Plus，新增 `views/dispute`、`api/dispute.ts`。
- 后端：Spring Boot + MyBatis-Plus，新增 `dispute` domain/mapper/dto/vo/service/controller。
- 数据库：在 `00-init.sql` 增加裁决表、角色、权限和初始角色授权；不改变现有计划/成果表语义。
- 权限：不新增裁决专属角色；复用现有超级管理员、部门负责人及其他现有账号角色，案件访问通过评审小组成员关系控制。
- 审计：复用 `AuditLogService`，并在状态变化、成员变化、回避、意见和结论动作中写入审计。
- 资料包：复用现有 `EmployeeAppealPackageService` 的证据聚合能力；裁决端通过授权服务访问，不复用员工自有权限路径。
- 测试：后端服务权限/状态/人数/回避/资料完整性测试；前端 `npm run build`，并检查路由、空态、错误态、窄屏布局。

## 5. 不做范围

- 不自动裁决、不自动确认、不自动变更参考分。
- 首版不引入裁决 AI 分析能力。
- 不在裁决端维护组织、角色和权限配置。
- 不在首版增加小程序裁决端。
- 不引入新的 AI 供应商或异步任务系统。
## 6. 角色与案件级授权修正

原型没有定义“裁决主管”或独立的 `DISPUTE_LEAD` / `DISPUTE_REVIEWER` 角色，首版不新增裁决专属角色。

- 最终裁决人：复用现有的超级管理员或部门负责人权限。
- 评审小组管理人：复用现有的超级管理员或部门负责人权限，负责添加成员、移除成员、处理回避和确认小组资格。
- 评审成员：从现有员工、直属领导或其他现有账号中选择，加入具体案件后获得案件级访问权。
- 评审成员只能查看被授权案件、查看资料包、提交个人评审意见和申请回避，不能查看全量案件，也不能提交最终裁决。
- 加入评审小组不等于获得全量裁决端权限；服务端必须同时校验现有账号身份和案件级评审小组关系。
