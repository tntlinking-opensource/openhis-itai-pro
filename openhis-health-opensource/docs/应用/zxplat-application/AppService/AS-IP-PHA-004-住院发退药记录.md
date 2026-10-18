# 住院发退药记录 · 应用服务设计（AS）

> **迁移声明（2026-07-21）**：本页将演进为报表中心 **「住院发药报表」**，正式契约改由 **`AS-RPT-004`** 承接（汇总 + 明细）；**不另建**平行药品报表页；与 **AS-IP-PHA-005 住院患者药品使用（用药明细）** 严格隔离。族边界见 `health/设计决策/DD-报表中心-发药耗材挂号费用汇总明细族.md`。在 **AS-RPT-004** 成稿前，本文仍为现网发退药记录读侧权威。
>
> **文档职责**：说明住院药房「发退药记录」查询页的**接口契约（IF）**、业务语义、涉及聚合与 QueryService、规则（BR）、编排意图；作为 **health-application** `inpatient/pharmacy/dispenserecord` 包实现的编排权威。
>
> **页面边界**：筛选区（发药时间、药房、项目类型、发药人、药品关键字、患者关键字、记录类型）→ **分页查询**平铺明细 → 前端按**就诊**或**处方号**分组展示 → **导出 Excel**。**纯读**。
>
> **编码**：AS-IP-PHA-004 · **版本**：v1.0 · **日期**：2026-06-21（迁移声明 2026-07-21）

---

## 1. 场景概述

### 1.1 目标与边界

| 维度 | 说明 |
| --- | --- |
| **在内** | 页面初始化；**分页查询**住院场景已发生的药品发药/退药事实行；**导出**；实现落点 **health-application / inpatient / pharmacy / dispenserecord** |
| **在外** | 住院发药（**AS-IP-PHA-001**）；住院汇总发药（**AS-IP-PHA-002**）；住院退药（**AS-IP-PHA-003**）；耗材；门诊发退药记录（**AS-OP-PHA-004**）；小计行；护士站汇总领药写路径 |
| **涉及域** | **MedicationDispense**；**MedicationRequest**；**MedicationDefinition**；**ChargeItem**；**Encounter** / **Patient**；**InpatientEncounterContext**（住院号、床号、病区）；**Location**；**Practitioner**；**Organization** |
| **编排权威** | 本文 **§2、§4** |
| **权限** | 报表式只读；药房下拉为租户下**全部启用药房**；租户过滤由框架承担 |

### 1.2 角色与关键动作

| 角色 | 关键动作 |
| --- | --- |
| **住院药房人员 / 药房管理** | 筛选 → 查询 → 前端切换分组方式 → 导出 |
| **系统** | 下发筛选项；平铺分页返回明细；装配账单价格与住院上下文 |

### 1.3 术语与口径约定

| 术语 | 口径 |
| --- | --- |
| **发药记录行** | `dispense_enum ∈ {INPATIENT, TEMPORARY}` 且 `status_enum = COMPLETED` |
| **退药记录行** | `dispense_enum = REFUND` 且 `status_enum = REFUNDED` |
| **一发一退** | 正向与退药各占一行；**展示**已退药收口的正向行（**不** supersede 排除）；**按处方（默认）**视图下同一临床方发药组与退药组**相邻** |
| **无部分退** | 全量退；数量取发放单实发/实退字段 |
| **发药时间** | 筛选与排序均为 `dispense_time`；空值不纳入时间窗 |
| **记录类型筛** | 全部 / 仅发药 / 仅退药 |
| **项目类型** | `medication_category_code` ↔ `category_code`；多选 |
| **价格** | 正向 dispense 锚点 + 住院待结算补充；退药按原药嘱 service 补读；不看 charge 状态；退药为负 |
| **处方号** | 存库发药 `CP…`、退药 `TCP…`（**DM-MED-003** **BR-MRQ-013**）；**读侧禁止**回落原药嘱；前端退药列展示原 `CP…` |
| **药品/患者检索** | 独立模糊，可同时 |
| **展示模式** | 后端平铺；前端 `PharmacyDispenseRecordPage`（`scope=inpatient`）+ **`dispense-record-group-display.ts`**：**按处方（默认）/ 按就诊/ 平铺**；完整处方号合并 + 临床方维度一发一退相邻；**无小计** |
| **长临嘱** | **不按**医嘱长期/临时过滤 |
| **汇总单号** | 住院汇总发药产生的 `summary_no` 作为展示字段，**不**按汇总单聚合小计 |

### 1.4 本期范围与非目标

| 类别 | 内容 |
| --- | --- |
| **本期** | IF-IPR-01～03；前端列表页（`pharmacy/dispense-record/inpatient`）含按处方/按就诊/平铺展示切换 |
| **非目标** | 写路径；耗材；小计；管辖库房收窄；部分退；未发完/待退/作废状态；门诊记录 |

### 1.5 领域清单（聚合 + 职责 + DM 路径）

| 聚合 | 在本场景中的职责 | DM 路径 |
| --- | --- | --- |
| **MedicationDispense** | 发/退事实、时间、数量、库房、发药人、批号、追溯码、`summary_no` | `DM-MED-002-MedicationDispense-药品发放.md` |
| **MedicationRequest** | 处方号、开单科室、用法用量 | `DM-MED-003-MedicationRequest-药品申请.md` |
| **MedicationDefinition** | 药品展示、`category_code` | `DM-MED-001-MedicationDefinition-药品定义.md` |
| **ChargeItem** | 单价、金额 | `DM-ADM-011-ChargeItem-收费明细.md` |
| **Encounter** / 在院上下文 | 住院号、床号、病区 | `DM-ADM-002-Encounter-就诊.md` |
| **Patient** | 患者姓名 | `DM-ADM-001-Patient-患者.md` |
| **Location** | 药房 | `DM-ADM-004-Location-位置.md` |
| **Practitioner** | 发药人 | `DM-ADM-005-Practitioner-医务人员.md` |
| **Organization** | 开单科室 | `DM-ADM-003-Organization-组织.md` |

### 1.6 业务规则（BR-*）

| 规则 ID | 描述 | 绑定 IF |
| --- | --- | --- |
| **BR-IPR-001** | 仅已发生事实：发药 `COMPLETED` 正向 + 退药 `REFUNDED` | IF-IPR-02、03 |
| **BR-IPR-002** | 场景：正向 `dispense_enum ∈ {INPATIENT, TEMPORARY}`；退药 `REFUND+REFUNDED` 且 `encounter_id` 关联 **IMP** 就诊（无患者关键字时领域 **EXISTS** 按就诊类别收窄；有患者关键字时 `resolvedEncounterIdsForScene` IN）；仅药品 | IF-IPR-02、03 |
| **BR-IPR-003** | `dispense_time` 时间窗与倒序；空 `dispense_time` 不入窗 | IF-IPR-02、03 |
| **BR-IPR-004** | 记录类型筛同门诊 **BR-OPR-004** | IF-IPR-02、03 |
| **BR-IPR-005** | **不** supersede 排除正向 `COMPLETED` | IF-IPR-02、03 |
| **BR-IPR-006** | 项目类型 `category_code` 多选（`medication_category_code`） | IF-IPR-02、03 |
| **BR-IPR-007** | 药房：租户下全部启用药房可选；未选不按库房过滤 | IF-IPR-01、02、03 |
| **BR-IPR-008** | 发药人仅 `practitioner_id` | IF-IPR-02、03 |
| **BR-IPR-009** | 药品/患者关键字独立 AND | IF-IPR-02、03 |
| **BR-IPR-010** | 价格：正向 dispense 锚点 + 住院待结算补充；退药按原药嘱 service 补读；退药负；无 charge 空 | IF-IPR-02、03 |
| **BR-IPR-011** | 平铺无小计 | IF-IPR-02、03 |
| **BR-IPR-012** | 列表须含住院号、床号（在院上下文可读时） | IF-IPR-02、03 |
| **BR-IPR-013** | 导出与查询筛选一致 | IF-IPR-03 |
| **BR-IPR-014** | **按处方**视图一发一退相邻与处方号展示口径同 **AS-OP-PHA-004** **BR-OPR-013** | IF-IPR-02（前端） |
| **BR-IPR-015** | 处方号/开单科室/用法读侧口径同 **AS-OP-PHA-004** **BR-OPR-014** | IF-IPR-02、03 |

---

## 2. 页面与接口总览（契约概览）

| IF 编号 | 用户动作 | 接口语义（业务） | 读/写 | 关键规则（BR） | 关键状态变化 | 涉及领域 | 查询服务 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| IF-IPR-01 | 进入住院发退药记录页 | 下发筛选项 | 读 | BR-IPR-007 | — | Location / Practitioner / 术语 | `LocationQueryService`；`PractitionerQueryService` |
| IF-IPR-02 | 查询/翻页/刷新 | 分页查询住院发退药平铺明细 | 读 | BR-IPR-001～014 | — | MedicationDispense 及关联 | `MedicationDispenseQueryService#pageMedicationDispenseRecords`；`ChargeItemQueryService`；`PharmacyDispenseRecordChargePricingsupport；`InpatientMedicationDispenseChargePricingLoader` |
| IF-IPR-03 | 导出 | 按筛选导出 Excel | 读 | BR-IPR-013 | — | 同 IF-IPR-02 | `MedicationDispenseQueryService#listMedicationDispenseRecords` |

---

## 3. 缺口跟踪（Gap）

**无待跟踪缺口**（`DM-MED-002` §6.2.18 已与领域代码对齐）。

---

## 4. 接口编排说明书（业务步骤）

### IF-IPR-01 页面初始化

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 用户进入住院发退药记录页 |
| **主路径步骤** | 同 **IF-OPR-01**（药房、项目类型、发药人、记录类型） |
| **关键分支** | — |
| **失败分支** | EX-IPR-101 |
| **查询侧说明** | 只读 |

### IF-IPR-02 分页查询发退药记录

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 用户设定筛选并查询 |
| **主路径步骤** | ① 校验时间窗。② 按 **BR-IPR-001～009** 分页查询住院场景发放行。③ 批量装配药品、医嘱、患者、**住院号/床号/病区**（`InpatientEncounterContextQueryModel` 或等价读模型）。④ 批量读 `ChargeItem`（**BR-IPR-010**）。⑤ 返回平铺行（含 `summary_no`、处方号、用法用量等；**无小计**） |
| **关键分支** | 记录类型、项目类型、关键字组合同门诊 |
| **失败分支** | EX-IPR-102、EX-IPR-103 |
| **查询侧说明** | 跨聚合批量装配 |

### IF-IPR-03 导出发退药记录

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 用户点击导出 |
| **主路径步骤** | 同 **IF-OPR-03**，筛选口径与 IF-IPR-02 一致 |
| **关键分支** | 零行处理同门诊 |
| **失败分支** | EX-IPR-104 |
| **查询侧说明** | 全量列表 + 导出行数上限 |

---

## 5. 业务异常与用户提示

| 语义代码 | 场景 | 用户提示方向 | 可重试 |
| --- | --- | --- | --- |
| EX-IPR-101 | 初始化失败 | 页面初始化失败，请稍后重试 | 是 |
| EX-IPR-102 | 时间窗非法 | 请选择有效的发药时间范围 | 否 |
| EX-IPR-103 | 查询失败 | 查询失败，请稍后重试 | 是 |
| EX-IPR-104 | 导出失败 | 导出失败，请稍后重试 | 是 |

---

## 6. 事件语义与消费方

**本期无写操作，无领域事件。**

---

## 7. 非功能性需求

| 类别 | 要求 |
| --- | --- |
| **性能** | 分页 ≤ 2000ms；导出 ≤ 30s（行数上限 SDD 定） |
| **一致性** | 纯读；价格与 charge 状态无关 |
| **租户** | 租户隔离；不按管辖库房收窄 |

---

## 8. 测试要点（业务视角）

| 类别 | 要点 |
| --- | --- |
| **正向** | 逐行发药、汇总发药 `COMPLETED` 均可见；退药 `REFUNDED` 可见；一发一退两行 |
| **住院字段** | 住院号、床号正确；`summary_no` 展示 |
| **筛选** | 同门诊口径 + 住院场景不含门诊 `OUTPATIENT` 行 |
| **价格** | 在院/已结算 charge 均按锚点展示；退药为负 |
| **导出** | 与列表一致 |

---

## 9. 关联文档与变更历史

| 类型 | 路径 |
| --- | --- |
| 发放 DM | `docs/领域/DomainModel/DM-MED-002-MedicationDispense-药品发放.md |
| 关联 AS | `AS-IP-PHA-001-住院发药.md`、`AS-IP-PHA-002-住院汇总发药.md`、`AS-IP-PHA-003-住院退药.md` |
| 门诊对照 | `AS-OP-PHA-004-门诊发退药记录.md` |

| 版本 | 日期 | 摘要 |
| --- | --- | --- |
| v1.3 | 2026-07-11 | **BR-IPR-002**：退药行须 IMP 就诊同窗；无患者关键字时由 `DM-MED-002` §6.2.18 EXISTS 收窄（修复门诊退药混入住院列表） |
| v1.2 | 2026-06-26 | **BR-IPR-015** 取消读侧原药嘱回落（同 **BR-OPR-014**）；退药价仍按原药嘱 service 补读 |
| v1.1 | 2026-06-26 | 退药定价补读、**BR-IPR-014** 前端一发一退相邻与 `T` 前缀处方展示；对齐 **DM-MED-003** **BR-MRQ-013** |
| v1.0 | 2026-06-21 | 初版：住院发退药记录纯读查询 + 导出 |
