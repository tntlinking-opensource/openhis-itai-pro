# 团检批量收费 · 应用服务设计（AS）

> **状态（2026-08-22）**：**写侧已冻结**。主路径为筹备免收费建单（`ENROLLED`）+ 合同单位后结（`markContractPaid`）+ FIN-001 团检加项收费。`confirmBatchSettlement` / `preSettleBatch` 将抛 `PEIS_GROUP_CHARGE_FROZEN`。

> **文档职责**：团检「先收费、后来人检查」批量收银契约；结算成功后为每人创建 `Encounter(PE)`（进度待检）。  
> **编码**：AS-PE-FIN-002 · **版本**：v1.0 · **日期**：2026-07-19  
> **菜单**：团检管理 · 收费 Tab（或 `/peis/group-charge`）· **遗留对照**：`GroupController/addGroupCharge`  
> **归属**：health 主程序收费 AS；**禁止** `AS-PEIS-FIN-*`；与个检 [AS-PE-FIN-001](./AS-PE-FIN-001-体检收费.md) **分流**（不混队列）

---

## 1. 场景概述

### 1.1 目标与边界

| 维度 | 说明 |
|------|------|
| **在内** | 按批次/学院/班级拉取未结名册；教职工导入名册后的批量队列；批量预结算；批量正式结算；**结算成功编排建 PE Encounter + 套餐 SR**（进度=待检） |
| **在外** | 单位/合同/批次/名册维护 → [AS-PEIS-GRP-001](./AS-PEIS-GRP-001-团检管理.md)；学院/班级/教职工真源 → Campus；个检单笔收银 → AS-PE-FIN-001 |
| **跨域** | booking 团检名册、Patient、Encounter(PE)、PeisEncounterExt、ChargeItem、PaymentReconciliation、Account/Contract |
| **编排权威** | 支付预结算/正式结算语义**复用** AS-OP-FIN-002；建单复用 `PeisRegistrationOrchestrator` 团检分支 |

### 1.2 角色与关键动作

| 角色 | 关键动作 |
|------|----------|
| **团检收费员** | 选批次 →（可选）按学院/班级筛选 → 勾选名册 → 预结算 → 确认批量收费 |
| **团检管理员** | 先完成 GRP 名册导入（含教职工导入挂册） |

### 1.3 术语与口径

| 术语 | 口径 |
|------|------|
| **先收费后建单** | 名册行在收费前**无** Encounter；**正式结算成功**后为每人创建 Encounter(PE)+ext.`group_flag=true`+SR；进度展示「待检」 |
| **待检** | `PeisEncounterProgressQuery` 展示态；**禁止**扩展 Encounter 专用 status |
| **批量** | 一次确认可结算多名册行；每人独立 Encounter 与收费事实 |
| **学院/班级** | Campus `college_id` + `class_name`；筛名册只读 Campus/名册冗余字段 |
| **已收费名册** | roster.`booking_status_code=PAID`（或等价）且已绑定 `encounter_id` |

### 1.4 本期范围与非目标

| 类别 | 内容 |
|------|------|
| **本期** | IF-PE-GCHG-01～06 |
| **非目标** | 个检 `/peis/charge` 混入；医保细则；退费深链；日结专页 |

### 1.5 业务规则（BR-*）

| 规则 ID | 描述 | 绑定 IF |
|---------|------|---------|
| **BR-PE-GCHG-001** | 仅处理团检批次名册；须绑定有效 patientId | 02～05 |
| **BR-PE-GCHG-002** | 正式结算成功须为每人创建 Encounter(PE)+SR；失败整批回滚或按行报错（实现选行级失败汇总） | 05 |
| **BR-PE-GCHG-003** | **BR-PE-ANNUAL-01**：同一学生同一**自然年**仅允许一条有效年度体检 Encounter（未取消） | 05 |
| **BR-PE-GCHG-004** | **BR-GRP-PAY-01**：未收费（无 SETTLED 收费事实）不得进入分科待检 | 跨 DES |
| **BR-PE-GCHG-005** | 支付编排复用门诊预结算/结算门禁；禁止复制支付不变式 | 04/05 |
| **BR-PE-GCHG-006** | 操作人会话解析；禁止前端伪造机构收窄 | 全写 IF |

---

## 2. 页面与接口总览

| IF 编号 | 用户动作 | 接口语义 | 读/写 | 关键规则 | 关键状态变化 |
|---------|----------|----------|:----:|----------|--------------|
| **IF-PE-GCHG-01** | 进入收费 Tab | 页初始化（渠道、状态下拉） | 读 | — | — |
| **IF-PE-GCHG-02** | 筛选未结名册 | 按 batchId + 可选学院/班级分页未收费名册 | 读 | BR-PE-GCHG-001 | — |
| **IF-PE-GCHG-03** | 已收费名册 | 按批次分页已收费（含 encounterId） | 读 | — | — |
| **IF-PE-GCHG-04** | 批量预结算 | 对勾选名册生成/汇总应付草稿 | 写 | BR-PE-GCHG-005 | 草稿支付 |
| **IF-PE-GCHG-05** | 确认批量收费 | 正式结算 → **建 Encounter** → 名册 PAID | 写 | BR-PE-GCHG-002/003/005 | ChargeItem SETTLED；Encounter 待检 |
| **IF-PE-GCHG-06** | 单行收费态 | 名册行是否已结清 | 读 | — | — |

---

## 3. 缺口跟踪（Gap）

| Gap ID | 说明 | 关闭条件 |
|--------|------|----------|
| **GAP-PE-GCHG-01** | 名册行 ChargeItem 生成时机（建单时批量 PLANNED vs 结算前懒生成） | 与套餐定价联调后收口 |
| **GAP-PE-GCHG-02** | `PeisGroupBookingPort` 写 PAID / 回填 encounterId | booking bridge 成码 |

---

## 4. 接口编排说明书

### 4.1 IF-PE-GCHG-01 初始化

返回支付渠道等元数据（对齐门诊/个检）。

### 4.2 IF-PE-GCHG-02 未结名册

读批次名册中 `booking_status_code≠PAID` 且 `patientId` 非空；可按 Campus 学院/班级过滤。

### 4.3 IF-PE-GCHG-03 已收费名册

读 `PAID` 名册 + encounter 摘要（流水号、套餐）。

### 4.4 IF-PE-GCHG-04 批量预结算

对勾选行解析套餐应付 → 预结算快照 → 草稿支付流水（可一单多就诊或按人多草稿；SDD 选定）。

### 4.5 IF-PE-GCHG-05 确认批量收费

1. Gate：名册有效、未重复 PAID、年度唯一（学生）。  
2. 正式结算支付。  
3. 每人：`PeisRegistrationOrchestrator#registerGroupAfterPaid`（Encounter+ext+SR）。  
4. 名册标记 PAID、回填 encounterId。  
5. 发布 `PeisEncounterRegisteredEvent`（DES 待检可消费）。

### 4.6 IF-PE-GCHG-06 收费态

返回 paid / unpaid / unknown。

---

## 5. 业务异常

| 码 | 场景 | 提示口径 |
|----|------|----------|
| EX-PE-GCHG-101 | 初始化失败 | 团检收费页初始化失败 |
| EX-PE-GCHG-102 | 名册查询失败 | 查询团检收费名册失败 |
| EX-PE-GCHG-201 | 预结算失败 | 批量预结算失败 |
| EX-PE-GCHG-202 | 正式结算失败 | 批量收费失败（请勿重复提交） |
| EX-PE-GCHG-203 | 年度重复 | 该学生本年度已有有效体检 |
| EX-PE-GCHG-204 | 名册无患者 | 请先绑定患者档案 |

---

## 6. 事件

| 事件 | 消费方 |
|------|--------|
| 支付结算成功（通用） | 队列刷新 |
| `PeisEncounterRegisteredEvent` | DES 待检（P1 订阅） |

---

## 7. NFR

- 批量建议单次 ≤ 200 人；超限分页确认。  
- 写路径同事务：支付成功与建单；失败可补偿标记另开（P2）。

---

## 8. 测试要点

- 学院+班级筛选 → 勾选 → 预结算 → 确认 → 每人 Encounter 存在且待检。  
- 未收费名册 DES 不可见（BR-GRP-PAY-01）。  
- 同一自然年重复年度体检拒绝（BR-PE-ANNUAL-01）。  
- 与 AS-PE-FIN-001 队列互不混入。

---

## 9. 关联文档

- SDD：[AS-PE-FIN-002-团检批量收费-SDD](../AppService-SDD/AS-PE-FIN-002-团检批量收费-SDD.md)
- GRP：[AS-PEIS-GRP-001](./AS-PEIS-GRP-001-团检管理.md)
- 个检收费：[AS-PE-FIN-001](./AS-PE-FIN-001-体检收费.md)
- 门诊支付：[AS-OP-FIN-002](./AS-OP-FIN-002-门诊收费.md)

---

## 修订记录

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.0 | 2026-07-19 | 首版：团检先收费后建 Encounter(待检)；批量；学院/班级筛选 |
