# 基础资料 · 打印日志查看 · 应用服务设计（AS）

> **文档职责**：说明「打印日志」页（明细 / 日汇总 Tab）的**接口契约（IF）**、业务语义、涉及聚合、规则（BR）、编排意图；作为上游契约供详细设计与实现对齐。
>
> **本文必须写**：IF 编号；用户动作与读写属性；关键校验与状态变化；Gap；异常口径；非功能约束；业务向测试要点。
>
> **本文禁止写**：具体类名/应用方法名；Request/Response 字段明细；包路径；Mapper；伪代码；写库顺序。
>
> **页面边界**：**基础数据 → 打印日志**（`Print-log/index.vue`）；**只读**审计查询，**不**在本页触发打印或写日志（写路径见运行时 `preparePrint` / `confirmPrint`）。
>
> **编码**：AS-BAS-MST-018 · **版本**：v1.1 · **日期**：2026-06-22

---

## 1. 场景概述

### 1.1 目标与边界

| 维度 | 说明 |
| --- | --- |
| **在内** | 按租户查询 **PrintLog** 明细分页（IF-BAS-PRT-01）；按日期范围 + 可选 **busNo** 聚合日汇总（IF-BAS-PRT-02）；明细行「详情」弹窗（**纯前端**，复用列表行数据，无独立 IF） |
| **在外** | 打印闭环写入（`preparePrint` → Host 渲染 → `confirmPrint` 落 **PrintLog**）；模板设计（**AS-BAS-MST-019**）；报表导出/合规归档（规划）；按操作人姓名检索（当前 UI 未提供） |
| **涉及域** | **PrintLog**（本 AS 唯一主域）；**PrintTemplate** 仅逻辑引用 **busNo**（展示模板编码，无 FK 联查） |
| **编排权威** | 以本文 **§2、§4** 为准；本页**只读**，不编排写侧 |

### 1.2 角色与关键动作

| 角色 | 关键动作 |
| --- | --- |
| **信息科 / 审计员 / 实施** | 进入打印日志页 → 切换「明细 / 日汇总」Tab → 按 **busNo**、状态、日期筛选 → 明细分页浏览 → 点「详情」查看单条快照 |
| **系统** | 明细按租户 + 软删过滤；日汇总在应用层对明细结果按「日期 + busNo」计数成功/失败 |

### 1.3 术语与口径约定

| 术语 | 口径 |
| --- | --- |
| **PrintLog** | 领域名；物理表 `prt_print_event_log`；一次 confirm 闭环一行 append-only |
| **busNo** | 模板业务键（库 `bus_no`）；API 与 UI 统一 **`busNo`** |
| **traceId** | 打印闭环追踪号（库 `trace_id`）；**preparePrint 返回**，confirm 带回；**废弃** `client_job_id` |
| **status** | 落库 **SUCCESS** / **FAILED**（大写）；UI 筛选与展示与之对齐；`cancelled` **本期不做** |
| **日汇总** | 非独立聚合表；由 IF-BAS-PRT-02 在应用层对 IF-BAS-PRT-01 结果内存分组 |

### 1.4 本期范围与非目标

| 类别 | 内容 |
| --- | --- |
| **本期（UI + 后端）** | IF-BAS-PRT-01 明细分页；IF-BAS-PRT-02 日汇总；详情弹窗（客户端） |
| **非目标** | 日志导出 Excel/PDF；按 **bindingCode** / 操作人姓名 / 院点筛选（UI 未设计）；日志删除/修正；重放 printData；独立「按 id 查详情」IF |
| **非目标（权限）** | 菜单权限 `Print:audit` 已 seed；AppService 方法级 `@RequirePermission` 为应用层 SDD 范围，**不**入 Gap |
| **下游写入** | 业务页打印成功后由 **confirmPrint** 追加 **PrintLog**；本页不参与 |

### 1.5 领域清单（聚合 + 职责 + DM 路径）

| 聚合（域） | 在本场景中的职责（摘要） | DM 路径 |
| --- | --- | --- |
| **PrintLog** | append-only 审计快照；本页只读分页与汇总 | `DM-PRT-002-PrintLog-打印审计日志.md` |
| **PrintTemplate** | 逻辑引用 **busNo**（列表展示编码含义） | `DM-PRT-001-PrintTemplate-画布打印模板.md` |

### 1.6 业务规则（BR-*）

| 规则 ID | 描述 | 绑定 IF |
| --- | --- | --- |
| **BR-PRT-LOG-001** | `trace_id` 非空（写侧 confirmPrint） | — |
| **BR-PRT-LOG-002** | `bus_no` 非空 | 01、02 |
| **BR-PRT-LOG-003** | `status` 合法枚举 | 01 |
| **BR-PRT-LOG-004** | append-only；本页无 update/delete | 01、02 |
| **BR-PRT-LOG-QRY-001** | 查询须限定**当前租户**；默认排除软删行 | 01、02 |
| **BR-PRT-LOG-QRY-002** | 日期范围为 **[dateFrom, dateTo+1)** 左闭右开 | 01、02 |
| **BR-PRT-LOG-QRY-003** | 日汇总：**SUCCESS** 计成功，其余计失败 | 02 |

---

## 2. 页面与接口总览（契约概览）

| IF 编号 | 用户动作 | 接口语义（业务） | 读/写 | 关键规则（BR） | 关键状态变化（自然语言） | 涉及领域 | 查询服务 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| IF-BAS-PRT-01 | 明细 Tab：筛选（busNo/状态/日期）、查询、重置、翻页；切换至明细 Tab | 分页查询当前租户 **PrintLog** 明细，按创建时间倒序 | 读 | BR-PRT-LOG-QRY-001～002、BR-PRT-LOG-003 | 无 | PrintLog | —（仓储 Specification 只读） |
| IF-BAS-PRT-02 | 日汇总 Tab：筛选（busNo/日期）、查询、重置 | 按日 + **busNo** 统计成功/失败笔数 | 读 | BR-PRT-LOG-QRY-001～003 | 无 | PrintLog | —（复用 01 读模型内存聚合） |

---

## 3. 缺口跟踪（Gap）

| Gap ID | 缺口类型 | 文件 + 章节 | 缺什么 / 不一致点 | 关闭条件 | 影响 IF | 优先级 | 状态 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| G-PRT-LOG-01 | 代码-文档不一致 | `DM-PRT-002` v1.4 vs 现 Flyway V1 / 实体 | DM 已定稿 **trace_id** 全量列；现库/实体仍为 **client_job_id**、**event_type** 等旧结构 | 清库重跑 Flyway + 实体/confirm 与 DM-PRT-002 对齐 | 01（列表字段）、下游 confirmPrint | P1 | **DM Closed** / 实现 Open |
| G-PRT-LOG-02 | 代码-文档不一致 | `DM-PRT-002` §4.1 vs 决策纪要 | **已定稿**大写 **SUCCESS**/**FAILED**；现网读写已用大写，与 DM 一致 | DM v1.4 与 AS 口径一致 | 01、02 | P2 | **Closed** |
| G-PRT-LOG-03 | 实现 | 本 AS §4 IF-01 列表映射 vs `PrintLogAppService` | 读侧仍映射 **clientJobId**；未暴露 traceId、bindingCode、templateVersion、operatorName 等 | 列表 DTO 与 IF-01 字段与 DM-PRT-002 §3.1 对齐 | 01、详情弹窗 | P1 | Open |

> **说明**：IF-BAS-PRT-01/02 只读编排**已落地**（旧表结构）；G-PRT-LOG-01/03 关闭依赖 Flyway 清库重跑与读写侧改造。Gap **不**描述「缺 AppService」。

---

## 4. 接口编排说明书（业务步骤）

### IF-BAS-PRT-01 明细分页查询

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 用户位于「明细」Tab；已登录且已选租户 |
| **主路径步骤** | 解析当前租户 → 构造过滤：可选 **busNo** 精确匹配、可选 **status**（转大写）、可选 **dateFrom/dateTo** 日期闭区间 → 排除软删 → 按 **created_at** 降序分页 → 映射列表行（含 traceId、busNo、bindingCode、templateVersion、status、printerName、copies、operatorId、operatorName、errorCode/errorMessage、createdAt） |
| **关键分支** | 未传日期：仍允许仅 busNo/status 过滤；默认 UI 初始化近 7 日 |
| **失败分支** | 租户未设置 → EX-PRT-LOG-001 |
| **查询侧说明** | 纯读；**不** FK 联查 **PrintTemplate** |

### IF-BAS-PRT-02 日汇总查询

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 用户位于「日汇总」Tab |
| **主路径步骤** | 默认 dateFrom=今日-7、dateTo=今日（可覆盖）→ 复用 IF-BAS-PRT-01 同等过滤读取明细（内部分页上限取全量窗口，见 §7 性能）→ 按 **createdAt 日期 + busNo** 分组 → **SUCCESS** 累加 successCount，其余累加 failedCount → 返回汇总行列表 |
| **关键分支** | 无数据 → 空列表 |
| **失败分支** | 同 EX-PRT-LOG-001 |
| **查询侧说明** | 纯读；**非** SQL GROUP BY 真汇总表 |

### 明细「详情」弹窗（无独立 IF）

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 用户在明细 Tab 点击某行「详情」 |
| **主路径步骤** | 直接使用 IF-BAS-PRT-01 已返回行数据渲染 Descriptions；**不**发起二次查询 |
| **关键分支** | — |
| **失败分支** | — |

---

## 5. 业务异常与用户提示

| 异常 ID | 场景 | 用户提示（语义） | 可重试 |
| --- | --- | --- | --- |
| EX-PRT-LOG-001 | 当前会话无租户 | 请先选择租户 | 是 |
| EX-PRT-LOG-101 | 列表/汇总查询失败 | 打印日志加载失败，请稍后重试 | 是 |

---

## 6. 事件语义与消费方

| 事件 | 触发 IF | 消费方（语义） |
| --- | --- | --- |
| — | — | 本页**只读**，不发布领域事件 |

> **上游写入**：业务打印 **confirmPrint** 追加 **PrintLog** 后，本页 IF-BAS-PRT-01 刷新可见新行（无物化视图/推送，依赖用户手动查询）。

---

## 7. 非功能性需求

| 维度 | 要求 |
| --- | --- |
| **性能** | 明细分页默认 20 条/页；日汇总内部分页读取上限 10000 行（窗口过大时可能截断，产品需控制默认 7 日） |
| **权限** | 菜单与方法级 **`Print:audit`**（与 Flyway V3 一致） |
| **审计** | 本页只读，不修改 **PrintLog** |
| **一致性** | 强一致读主库；无缓存 |
| **多租户** | 强制 tenant 隔离 |

---

## 8. 测试要点（业务视角）

| 类别 | 要点 |
| --- | --- |
| **明细** | 默认近 7 日有数据；busNo/status 组合筛选；翻页；空结果 |
| **日汇总** | 同一 busNo 跨日多行计数正确；SUCCESS vs FAILED 分类与明细一致 |
| **详情** | 弹窗字段与列表行一致；无二次请求 |
| **租户** | 未选租户报错 EX-PRT-LOG-001 |
| **负向** | 跨租户不可见 |

---

## 9. 关联文档与变更历史

### 9.1 关联文档

| 文档 | 关系 |
| --- | --- |
| `DM-PRT-002-PrintLog-打印审计日志.md` | 聚合、不变式 |
| `DM-PRT-001-PrintTemplate-画布打印模板.md` | busNo 语义 |
| `AS-BAS-PRT-001-打印任务运行时.md` | 写侧 prepare/confirm |
| `AS-BAS-MST-019-打印设计.md` | 模板维护（同源 Print 能力） |
| `docs/Print/02-交互说明.md` | 交互补充（非契约真源） |

### 9.2 变更历史

| 版本 | 日期 | 变更摘要 |
| --- | --- | --- |
| v1.1 | 2026-06-22 | Gap 同步 DM v1.4；关联 AS-BAS-PRT-001；术语 traceId；权限 Print:audit |
| v1.0 | 2026-06-22 | 首版：明细 IF-01、日汇总 IF-02 |
