# 基础资料 · 打印任务运行时 · 应用服务设计（AS）

> **文档职责**：说明业务页 **静默打印闭环**（`preparePrint` → OpenHIS.Win 渲染 → `confirmPrint`）的**接口契约（IF）**、业务语义、涉及聚合、规则（BR）、编排意图；作为上游契约供详细设计与实现对齐。
>
> **本文必须写**：IF 编号；用户动作与读写属性；关键校验与状态变化；Gap；异常口径；非功能约束；业务向测试要点。
>
> **本文禁止写**：具体类名/应用方法名；Request/Response 字段明细；包路径；Mapper；伪代码；写库顺序。
>
> **调用边界**：Vue 业务页（如门诊收费小票 `usePrintJob` / `use-charge-receipt-print`）；**非**基础数据维护页（模板/绑定见 **AS-BAS-MST-019**）；日志只读见 **AS-BAS-MST-018**。
>
> **编码**：AS-BAS-PRT-001 · **版本**：v1.0 · **日期**：2026-06-22

---

## 1. 场景概述

### 1.1 目标与边界

| 维度 | 说明 |
| --- | --- |
| **在内** | **preparePrint**（IF-BAS-PRT-31）：解析 `scenarioCode` 或 `busNo`，返回 traceId + 模板快照；**confirmPrint**（IF-BAS-PRT-32）：落 **PrintLog** 审计行 |
| **在外** | Host 本地渲染与物理打印（OpenHIS.Win `/api/print/print`，**无** Bearer）；模板设计/发布（**AS-BAS-MST-019**）；日志分页查询（**AS-BAS-MST-018**） |
| **涉及域** | **PrintTemplate**（prepare 只读 published）；**PrintLog**（confirm 写入） |
| **编排权威** | 以本文 **§2、§4** 为准；前端 `usePrintJob` 为参考编排，**不**替代本文 IF 契约 |

### 1.2 角色与关键动作

| 角色 | 关键动作 |
| --- | --- |
| **收费员 / 医生 / 护士等业务用户** | 业务页点「打印」→ 前端组装 printData → prepare → 本地 Host 出纸 → confirm |
| **系统（prepare）** | 生成 **traceId**；按 **scenarioCode** 或 **busNo** 读 **published** 模板；**不写** PrintLog |
| **系统（confirm）** | 校验 traceId；追加 **PrintLog** 一行（SUCCESS / FAILED） |

### 1.3 术语与口径约定

| 术语 | 口径 |
| --- | --- |
| **traceId** | 服务端在 prepare 生成；confirm **必传**；库 `trace_id` |
| **scenarioCode** | **运行时主入口**；与 **busNo** 二选一；对应 `prt_template.scenario_code` |
| **busNo** | 模板业务键；API **`busNo`**；设计/管理/debug 直传 prepare 时使用 |
| **bindingCode** | **兼容别名**：prepare 请求中与 `scenarioCode` 同义（**不**经 PrintBinding 表解析） |
| **printData** | Vue 组装的业务 JSON；confirm 可传用于 **content_hash**；**不**存 PrintJson |
| **clientType** | 默认 `openhis-win`；confirm 写入 **PrintLog** |
| **status** | confirm：**SUCCESS** / **FAILED**（大写） |

### 1.4 本期范围与非目标

| 类别 | 内容 |
| --- | --- |
| **本期契约** | IF-BAS-PRT-31、IF-BAS-PRT-32 |
| **HTTP** | `POST /api/app/printJob/preparePrint`、`POST /api/app/printJob/confirmPrint` |
| **非目标** | 服务端渲染 PNG/PDF；printToken；Host 鉴权；日志查询；模板编辑 |
| **非目标（院点）** | prepare 解析 **不含** 院点 template/binding override（DM deferred） |

### 1.5 领域清单（聚合 + 职责 + DM 路径）

| 聚合（域） | 在本场景中的职责（摘要） | DM 路径 |
| --- | --- | --- |
| **PrintTemplate** | prepare 只读最新 published（按 scenarioCode 或 busNo） | `DM-PRT-001-PrintTemplate-画布打印模板.md` |
| **PrintLog** | confirm append-only | `DM-PRT-002-PrintLog-打印审计日志.md` |

### 1.6 业务规则（BR-*）

| 规则 ID | 描述 | 绑定 IF |
| --- | --- | --- |
| **BR-PRT-006** | prepare **仅**读 `published`（不含 archived） | 31 |
| **BR-PRT-LOG-001** | confirm 时 `trace_id` 非空 | 32 |
| **BR-PRT-LOG-002** | `bus_no` 非空 | 32 |
| **BR-PRT-LOG-003** | `status` 为 SUCCESS 或 FAILED | 32 |
| **BR-PRT-LOG-004** | append-only | 32 |
| **BR-PRT-LOG-005** | 可选 **content_hash** 由 printData 计算 | 32 |
| **BR-PRT-JOB-001** | prepare 入参 **scenarioCode** 与 **busNo** 二选一、互斥 | 31 |
| **BR-PRT-JOB-002** | prepare **不**写 PrintLog | 31 |
| **BR-PRT-JOB-003** | confirm 须已登录且已选租户 | 32 |

---

## 2. 页面与接口总览（契约概览）

| IF 编号 | 用户动作 | 接口语义（业务） | 读/写 | 关键规则（BR） | 关键状态变化（自然语言） | 涉及领域 |
| --- | --- | --- | --- | --- | --- | --- |
| IF-BAS-PRT-31 | 业务页：点打印（prepare 阶段） | 解析 scenarioCode/busNo → 生成 traceId → 返回 PrintJson + 元数据 | 读 | BR-PRT-006、JOB-001～002 | 无 | PrintTemplate |
| IF-BAS-PRT-32 | 业务页：Host 打印后/失败后（confirm 阶段） | 按 traceId 追加 **PrintLog** 审计行 | 写 | BR-PRT-LOG-001～005、JOB-003 | 新增 PrintLog | PrintLog |

---

## 3. 缺口跟踪（Gap）

| Gap ID | 缺口类型 | 缺什么 / 不一致点 | 关闭条件 | 影响 IF | 优先级 | 状态 |
| --- | --- | --- | --- | --- | --- | --- |
| G-PRT-JOB-FE-01 | 前端 | 打印日志页、绑定配置页 API 未建 | 补 `api/print/print-log.ts`、`print-binding.ts` 与对应 views | — | P2 | Open |

> **说明**：IF-BAS-PRT-31/32 后端与 `usePrintJob` / `utils/print/**` 前端运行时已对齐（traceId、scenarioCode、busNo）。

---

## 4. 接口编排说明书（业务步骤）

### IF-BAS-PRT-31 preparePrint

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 用户已在业务页触发打印；已登录；已选租户；具备 `Print:print` 或 `Print:preview` |
| **主路径步骤** | 校验入参：**scenarioCode** 与 **busNo** 恰好其一非空 → 若 scenarioCode：经 **findPublishedByScenarioCode**（租户→平台 fallback）→ 若 busNo：**findPublished(busNo)** → 生成 **traceId** → 返回 traceId、busNo、scenarioCode、templateVersion、PrintJson、可选 samplePrintData |
| **失败分支** | 入参都空或都传 → EX-PRT-JOB-201；无 published 模板 → EX-PRT-JOB-102；无租户 → EX-PRT-JOB-001 |
| **查询侧说明** | 纯读模板；**不写** PrintLog |

### IF-BAS-PRT-32 confirmPrint

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 前端已完成 prepare 且已执行或跳过 Host 打印 |
| **主路径步骤** | 校验租户 → 校验 **traceId** 非空 → 校验 **status**（SUCCESS/FAILED）→ 组装 PrintLog：traceId、busNo（与 prepare 一致）、可选 bindingCode、templateVersion、printer、copies、printData→content_hash、bizRef、clientType（默认 openhis-win）、operatorId/operatorName、错误字段 → **追加一行** |
| **关键分支** | FAILED：仍落库；copies 可为 0 |
| **失败分支** | traceId 空 → EX-PRT-JOB-202；非法 status → EX-PRT-JOB-203；无租户 → EX-PRT-JOB-001 |
| **查询侧说明** | 纯写；**不**更新已有行 |

### 前端组合编排（参考，非独立 IF）

| 维度 | 说明 |
| --- | --- |
| **主路径** | preparePrint → assertPrintCapability → silentPrint(Host) → confirmPrint(SUCCESS) |
| **失败路径** | 任一步失败 → confirmPrint(FAILED) + errorCode/errorMessage（审计失败不阻断用户提示） |

---

## 5. 业务异常与用户提示

| 异常 ID | 场景 | 用户提示（语义） | 可重试 |
| --- | --- | --- | --- |
| EX-PRT-JOB-001 | 无租户 | 请先选择租户 | 是 |
| EX-PRT-JOB-101 | prepare 失败（通用） | 打印准备失败，请稍后重试 | 是 |
| EX-PRT-JOB-102 | 无 published 模板 | 未找到可用打印模板 | 否 |
| EX-PRT-JOB-103 | bindingCode 无法解析 | 打印场景未配置 | 否 |
| EX-PRT-JOB-201 | busNo 与 bindingCode 非法组合 | 打印参数错误 | 是 |
| EX-PRT-JOB-202 | confirm 缺少 traceId | 打印确认失败（缺少追踪号） | 否 |
| EX-PRT-JOB-203 | status 非法 | 打印状态无效 | 否 |
| EX-PRT-JOB-104 | confirm 落库失败 | 打印审计记录失败 | 是 |

---

## 6. 事件语义与消费方

| 事件 | 触发 IF | 消费方（语义） |
| --- | --- | --- |
| — | — | 首期不发域事件；**AS-BAS-MST-018** 列表刷新依赖用户手动查询 |

---

## 7. 非功能性需求

| 维度 | 要求 |
| --- | --- |
| **性能** | prepare 单次读库；confirm 单行 insert |
| **权限** | prepare：`Print:print` 或 `Print:preview`；confirm：同 prepare（与 Flyway V3 一致） |
| **安全** | Host 打印不经 Java 鉴权；权限在 prepare 门控 |
| **幂等** | 同一 traceId 重复 confirm：**允许**多行（业务层应避免重复提交） |
| **多租户** | prepare/confirm 强制当前租户 |

---

## 8. 测试要点（业务视角）

| 类别 | 要点 |
| --- | --- |
| **prepare** | 直传 scenarioCode（如 `op-charge-print`）；直传 busNo；租户 override 模板优先于平台 |
| **confirm 成功** | SUCCESS 行含 traceId、templateVersion、scenarioCode；日志页可见 |
| **confirm 失败** | Host 失败 → FAILED + errorCode；仍有审计行 |
| **负向** | 双入参/无入参拒绝；无模板拒绝；无租户拒绝 |
| **收费小票** | `op-charge-print` 端到端 |

---

## 9. 关联文档与变更历史

### 9.1 关联文档

| 文档 | 关系 |
| --- | --- |
| `DM-PRT-001` / `002` / `003` | 三聚合 DM |
| `AS-BAS-MST-019-打印设计.md` | 模板与 binding 维护 |
| `AS-BAS-MST-018-打印日志查看.md` | 审计只读 |
| `docs/print-client/` | Host 渲染 |

### 9.2 变更历史

| 版本 | 日期 | 变更摘要 |
| --- | --- | --- |
| v1.0 | 2026-06-22 | 首版：IF-31 prepare、IF-32 confirm；对齐 SDD 决策与 DM v1.4/v1.5 |
