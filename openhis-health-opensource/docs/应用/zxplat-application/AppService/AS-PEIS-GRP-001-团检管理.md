# 体检域 · 团检筹备 · 应用服务设计（AS）

> **编码**：AS-PEIS-GRP-001 · **版本**：v1.5 · **日期**：2026-08-28  
> **菜单**：团检筹备 · **类名**：`PeisGroupCheckAdminAppService` · **路由**：`/peis/group-check`  
> **对账菜单**：团检合同对账 · **路由**：`/peis/report/contract-reconciliation`  
> **产品真源（已成码）**：[10-团检单位树分组与名册选定方案.md](../PeisAppService/10-团检单位树分组与名册选定方案.md)

---

## 1. 场景概述

### 1.1 目标与边界

| 维度 | 说明 |
|------|------|
| **在内** | 单位树/合同/体检分组（多可选套餐）；名册导入与选定；**选定即建单**（`selectRosterPackage`）；到检前换套餐；登记台/小程序同口径；**合同对账（GRP-08）**；名册加/减项 |
| **在外** | 学院/学生主数据 → **Campus**；**FIN-002 批量收费已冻结**；**日常到检主路径** → [AS-PEIS-REG-001](./AS-PEIS-REG-001-个检登记.md) 登记台 |
| **涉及域** | `peis_group_*`（含 `peis_group_batch_package` / `peis_group_roster_package` / `selected_package_activity_id`）；Encounter(PE) + `peis_encounter_ext`；名册 `UNPAID` / `ENROLLED` / `PAID` |

### 1.2 角色

| 角色 | 动作 |
|------|------|
| **团检管理员** | 筹备页维护单位树/合同/分组、导入名册、批量选定建单 |
| **体检前台** | **登记台**按人检索名册 → 团检建单 / 到检 / 指引单（主路径） |
| **学校保健老师** | 查看团检进度（GRP-06，P2） |

### 1.3 术语与建模（v1.3 定版）

| 术语 | 口径 |
|------|------|
| **主路径（现场）** | 筹备导入名册 → **登记台/小程序** 反查可选套餐 → **选定套餐建单**（`ENROLLED`）→ 到检 → 分科 |
| **单位后结** | 合同 `contract_amount` + `markContractPaid` → `pay_status_code=PAID`（不经 Payment） |
| **加项收费** | 筹备/登记台 `adjustRosterServiceRequests` → FIN-001「团检加项收费」Tab |
| **收费路径（已冻结）** | ~~FIN-002 批量收费 → `PAID`~~ → 勿作主路径 |
| **团检一人一流水** | 每人 1× `Encounter(PE)` + ext（`group_flag=true`） |
| **建单态** | `ENROLLED` = 免收费已建单；`PAID` = 真实收费已建单；二者均可到检与分科（BR-GRP-PAY-01 v1.3） |
| **待检** | 有 Encounter + SR 即可进分科；团检免收费不要求 ChargeItem |

### 1.4 范围

| P0/P1 | GRP-01～05；登记台团检路径；P0-1 免收费建单 |
| P2 | GRP-06 进度、GRP-07 问卷 |
| 非目标 | Campus 批量导入（仍 Gap）；在本 AS 写 ChargeItem |

### 1.5 领域清单

| 聚合 | 职责 |
|------|------|
| `peis_group_*` | 单位/合同/批次/名册（当前直写表，GAP-PEIS-GRP-02 Port 待迁） |
| PeisEncounterExt | 团检扩展；`group_flag` / `group_batch_id` |
| PeisEncounterProgressQuery | 批次进度（P2） |

### 1.6 业务规则（BR）

| 编号 | 规则 | IF |
|------|------|-----|
| **BR-GRP-01** | Encounter 可由 **登记台免收费建单**（`ENROLLED`）或 **FIN-002**（`PAID`）创建；筹备页可批量补录 | GRP `confirmBatchEnrollment` / FIN-002 |
| **BR-GRP-02** | 名册导入须有效档案；**教职工**导入应同步 Campus（`cmp_patient_staff`），见 [02-教职工导入同步Campus方案](../peis-import/02-教职工导入同步Campus方案.md) | GRP-04 |
| **BR-GRP-03** | 团检真实收费不在 GRP IF → **AS-PE-FIN-002** | — |
| **BR-GRP-04** | `group_flag=true` 时 `company_id` 必填 | 建单 |
| **BR-GRP-05** | 批次 Slot 须 PHYSICAL_EXAM（若关联号源） | GRP-03 |
| **BR-GRP-06** | 仅名册内且 **已建单**（`ENROLLED` 或 `PAID`）人员可 GRP-05 到检 | GRP-05 |
| **BR-GRP-07** | 防重复在检 Encounter(PE) | 建单侧 |
| **BR-GRP-PAY-01** | 团检须 **已建单**（`ENROLLED`/`PAID`）或收银结清后方可分科；免收费 `ENROLLED` 豁免真实收银 | DES / FIN-002 |
| **BR-GRP-08** | GRP-05 **不**新建 Encounter；仅到检确认/导引 | GRP-05 |
| **BR-GRP-PKG-01** | 允许空白套餐先建 Encounter，后续补项（P1-5） | REG-ORCH |

---

## 2. 页面与接口总览

| IF | Tab/动作 | 接口语义 | 读写 | 关键规则 |
|----|----------|----------|:--:|----------|
| **IF-PEIS-GRP-01** | 单位维护 | 保存团检单位（树） | 写 | BR-GRP-TREE-* |
| **IF-PEIS-GRP-02** | 合同维护 | 保存团检合同（多套餐池） | 写 | — |
| **IF-PEIS-GRP-03** | 分组维护 | 保存体检分组（多可选套餐） | 写 | BR-GRP-GRP-01 |
| **IF-PEIS-GRP-04** | 导入名册 | 按合同导入；Excel 可指定分组/套餐 | 写 | BR-GRP-ROS-02 |
| **IF-PEIS-GRP-04b** | 登记台反查 | `lookupRosterForDesk` | 读 | 含可选/选定态 |
| **IF-PEIS-GRP-04c** | 选定建单 | `selectRosterPackage` / `confirmBatchEnrollment(items)` | 写 | 须 `rosterId+packageActivityId` |
| **IF-PEIS-GRP-04e** | 到检前换套餐 | `changeRosterSelectedPackage` | 写 | BR-GRP-SEL-03/04 |
| **IF-PEIS-GRP-05** | 到检确认 | 校验已建单 + encounterId | 写 | BR-GRP-06/08 |
| **IF-PEIS-GRP-02b** | 合同后结 | `markContractPaid` | 写 | 合同 `PAID` |
| **IF-PEIS-GRP-04d** | 名册加/减项 | `adjustRosterServiceRequests` | 写 | REG-04 复用 |
| **IF-PEIS-GRP-08** | 合同对账 | `listContractReconciliation` | 读 | 合同汇总；明细分组展开 + 选定套餐分布 |
| **IF-PEIS-GRP-06** | 团检进度 | 批次进度列表 | 读 | P2 |

---

## 3. 缺口跟踪（Gap）

| Gap ID | 说明 | 关闭条件 | 优先级 |
|--------|------|----------|--------|
| GAP-PEIS-GRP-01 | `searchGroupProgress` | DM-PEIS-008 + 代码 | P2 |
| GAP-PEIS-GRP-02 | 教职工导入同步 Campus + `PeisGroupBookingPort` | P0：`PeisCampusStaffArchiveExtension` + `syncCampus`（[方案](../peis-import/02-教职工导入同步Campus方案.md)）；P2：Port 迁表 | P0 |

---

## 4. 接口编排说明书

### 4.5 IF-PEIS-GRP-05 到检确认（v1.3）

| 维度 | 说明 |
|------|------|
| **触发** | 登记台或筹备页选定**已建单**团员 |
| **主路径** | 校验 `ENROLLED`/`PAID` + `encounterId` → 标记到检 |
| **禁止** | 新建 Encounter / SR |
| **失败** | `NOT_ENROLLED`；无 Encounter；不在名册 |

### 4.6 IF-PEIS-GRP-04c 选定建单（P0-1 · v1.5）

| 步骤 | 说明 |
|------|------|
| 1 | 入参 `rosterId` + `packageActivityId`（批量：`items[]` 成对） |
| 2 | 校验可选套餐 ⊆ 分组；未选定禁止建单 |
| 3 | `GroupRosterPackageSelectionSupport.selectAndEnroll` → `ENROLLED` |
| 4 | 失败行汇总于 `failures`；**废弃**导入后 `autoEnroll` |

### 4.7 IF-PEIS-GRP-08 合同对账

| 维度 | 说明 |
|------|------|
| **触发** | 团检管理员选择单位，查看合同汇总 |
| **汇总** | 合同金额、收款状态、名册建单/到检、加项待收/已收（锚定 SR 的 ChargeItem） |
| **明细** | 按**体检分组**展开；每组统计**选定套餐分布**（名册/建单/到检）及未选定人数 |
| **后结** | 可在筹备页或本页调用 `markContractPaid` |
| **挂费/导检** | 套餐费锚定**选定套餐 AD**；指引单套餐名取快照或选定 AD |

---

## 5. 业务异常

| 码 | 场景 | 提示 |
|----|------|------|
| `COMPANY_REQUIRED` | 团检缺单位 | 请选择团检单位 |
| `ROSTER_NOT_FOUND` | 不在名册 | 该人员不在本批次名单 |
| `NOT_ENROLLED` | 未建单到检/分科 | 请先完成团检建单 |
| `ROSTER_ALREADY_ENROLLED` | 重复建单 | 该人员已建单 |
| `PACKAGE_NOT_SELECTED` | 未选定套餐建单 | 请先选定体检套餐 |
| `ROSTER_PACKAGES_EXCEED_TARGET_BATCH` | 换组后可选超出目标组 | 请先调整可选套餐或换组 |

---

## 6. 事件

| 事件 | 触发 | 消费方 |
|------|------|--------|
| `PeisEncounterRegisteredEvent` | 建单（免收费或 FIN-002） | DES/导检 |

---

## 7. NFR

| 项 | 承诺 |
|----|------|
| **批量** | `confirmBatchEnrollment` 支持逐行部分成功 |
| **幂等** | GRP-05 到检确认可重复调用 |

---

## 8. 测试要点

- 筹备导入 → 登记台搜人 → 免收费建单 → 到检 → 分科（无 ChargeItem）  
- FIN-002 收费建单 → `PAID` → 到检 → 分科  
- 空白套餐建单 → Encounter 无 SR → 后续补项  
- 编辑单位/合同/批次 save 不抛 detached entity  

---

## 9. 关联文档

- SDD：[AS-PEIS-GRP-001-SDD](../AppService-SDD/AS-PEIS-GRP-001-团检管理-SDD.md)
- 收费：[AS-PE-FIN-002](./AS-PE-FIN-002-团检批量收费.md)
- 登记：[AS-PEIS-REG-001](./AS-PEIS-REG-001-个检登记.md)
- **筹备改造定稿（已成码）**：[10-团检单位树分组与名册选定方案.md](../PeisAppService/10-团检单位树分组与名册选定方案.md)

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.5 | 2026-08-28 | 对齐方案 10：单位树、分组多套餐、名册选定/换套餐、对账按选定套餐展开 |
| v1.3 | 2026-08-22 | **免收费 ENROLLED** 定版；登记台主路径；筹备分工；BR-GRP-PAY-01/DES 门禁；空白套餐兜底 |
| v1.2 | 2026-07-19 | 先收费后建单（已被 v1.3 现场口径扩展） |
| v1.1 | 2026-06-27 | 补全 §1～§8 |
| v1.0 | 2026-06-27 | 首版 P1 骨架 |
