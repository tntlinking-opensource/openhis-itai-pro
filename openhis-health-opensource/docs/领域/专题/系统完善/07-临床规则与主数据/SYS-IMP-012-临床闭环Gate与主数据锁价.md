# SYS-IMP-012 临床闭环 Gate 与主数据锁价

> **问题类型**：临床规则与主数据 · [本类索引](./README.md) · [总索引](../README.md)
>
> **来源**：2026-06 代码审查（`OutpatientNurseStationSkinTestAppService`、`NurseStationSkinTestAppService`、`DoctorStationSkinTestSyncOrchestrationsupport、药房核发路径、`ChargeItemDomainService` 定价写入、`PatientDomainService#mergePatient`）。
>
> **关联 AS**：`AS-OP-NUR-00x`（皮试 Tab UI Gap）、`AS-IP-NUR-0xx`（住院皮试）、`AS-OP-PHA-001`（门诊药房）、抗菌/审方 AS（非本 AS 范围标注）；主数据价表 AS
>
> **关联**：`SYS-IMP-001` GAP-EXE-008（停嘱后仍执行）；`SYS-IMP-010` GAP-BAT-006；`SYS-IMP-002` GAP-FIN-006；`SYS-IMP-013` GAP-REG-004/009（处方权、override 审计）
>
> **状态**：待实施

---

## 1. 背景与结论摘要

IMP-001～007 主要覆盖 **库存、金钱、并发、Stale**；下列 **临床规则与主数据** 缺口同样可导致 **医疗安全与账价不一致**，但技术形态多为 **「写路径缺 Gate」** 而非 DB 双写：

| 领域 | 后端 | 核发/执行 Gate | UI |
| --- | --- | --- | --- |
| **皮试** | 门诊/住院 `*SkinTestAppService` 已实现 | 药房核发 **未**查皮试结果 | 门诊皮试 Tab **未接入**（AS 明确） |
| **审方** | 部分 AS 标注药房侧 | 核发路径待验收 | — |
| **重复医嘱** | AS 非目标 | — | 提示缺失 |
| **锁价** | ChargeItem 写入单价快照 | 价表变更不回写 | 报表/医保可能多套价 |
| **患者合并** | `mergePatient` 发事件 | 历史 Encounter 迁移 **未**在财务 Gate 体现 | — |

---

## 2. 问题清单总览

| ID | 标题 | 严重度 | 门/住 | 优先级 | 状态 |
| --- | --- | --- | --- | --- | --- |
| GAP-CLN-001 | 皮试阳性/未完成仍核发（药房无 Gate） | **高危** | 门+住 | P1 | 待实施 |
| GAP-CLN-002 | 门诊皮试 UI 未接入导致流程 bypass | 中危 | 门诊 | P1 | 待实施 |
| GAP-CLN-003 | 抗菌药物/审方通过未作为核发前置 | 中危 | 门+住 | P2 | 待验收 |
| GAP-CLN-004 | 重复检验检查开立无近 30 天提示 | 低危（业务） | 门+住 | P3 | 已知限制 |
| GAP-CLN-005 | 住院预结算缺诊断 Gate（与门诊不对称） | 中危 | 住 | P2 | 待验收 |
| GAP-CLN-PRICE-001 | 价表变更后旧 Charge 与报表/医保价漂移 | 中危 | 门+住 | P2 | 待实施 |
| GAP-CLN-PRICE-002 | 滚费/划价取 live 价非签发快照 | 中危 | 住 | P2 | 见 SYS-IMP-010 |
| GAP-CLN-006 | 患者合并后双 patientId 历史费用分散 | 中危 | 门+住 | P2 | 待实施 |
| GAP-CLN-007 | 过敏史未参与核发硬 Gate | 中危 | 门+住 | P2 | 待评估 |

---

## 3. 分项：问题与修改方案

### GAP-CLN-001 药房核发无皮试 Gate

| 维度 | 说明 |
| --- | --- |
| **严重度** | **高危**（医疗安全） |
| **关联 IF/BR** | IF-IP-STK-03；BR-IP-STK-006/009；BR-ORD-025 皮试联动 |
| **现象** | `OutpatientPharmacyDispenseAppService` / `InpatientPharmacyDispenseAppService` 的 `validateDispensable` **未**检索 `MedicationRequest.skinTestFlag` 与皮试结果（阳性/阴性/未测）。 |
| **根因** | 皮试模块与药房模块未编排；AS 门诊 UI Gap 加剧「未登记仍发药」。 |
| **业务影响** | 皮试药未测或阳性仍出库。 |

**修改方案（推荐）**

1. 新增 **`SkinTestDispenseGatesupport**：
   - `skinTestFlag=true` → 须存在有效皮试记录且非阳性；
   - 阳性 → `PHA-STK-403` 拒绝核发（需 pharmacist override role 可选）。
2. 接入：`prepareMedicationDispense`、`dispenseMedication` 前。
3. 与护士站皮试 `NurseStationSkinTestAppService` 读模型对齐。

**涉及代码**

- `OutpatientPharmacyDispenseAppService.java`
- `InpatientPharmacyDispenseAppService.java`
- 新建 `SkinTestDispenseGateSupport.java`

**验收用例**

- T1：skinTest 药无皮试记录 → 核发失败。
- T2：皮试阳性 → 核发失败。
- T3：皮试阴性 → 核发成功。

**工作量**：2～3 人天

---

### GAP-CLN-002 门诊皮试 UI 未接入

| 维度 | 说明 |
| --- | --- |
| **严重度** | 中危 |
| **现象** | 后端 `OutpatientNurseStationSkinTestAppService` 存在；前端 Tab 未接 — 护士无法登记，CLN-001 Gate 将全部拒绝或需 bypass。 |
| **修改方案** | 产品排期 UI；上线前临时：医生站展示皮试嘱状态；药房 Gate 与 UI 同步发布。 |

**状态**：待实施（前后端联调）

**工作量**：3～5 人天（含 UI）

---

### GAP-CLN-003 审方/抗菌 Gate

| 维度 | 说明 |
| --- | --- |
| **严重度** | 中危 |
| **关联 AS** | 门诊药房审方标注「非本 AS」 |
| **现象** | 核发前是否校验 `MedicationRequest` 审方态 / 抗菌等级 — **需按现场启用模块验收**。 |
| **修改方案** | 若启用审方：核发 Gate `assertReviewApproved(requestId)`；未启用则文档化关闭。 |

**状态**：待验收

**工作量**：1～3 人天（视模块）

---

### GAP-CLN-004 重复检验检查

| 维度 | 说明 |
| --- | --- |
| **严重度** | 低危（业务） |
| **修改方案** | AS 非目标；记录为 **已知限制**；可选 CDSS 插件。 |

**状态**：已知限制

---

### GAP-CLN-005 住院预结算诊断 Gate

| 维度 | 说明 |
| --- | --- |
| **严重度** | 中危 |
| **现象** | 门诊 `preSettleOutpatientCharges` 强制 `getEncounterDiagnosis` 非空；住院 `InpatientSettleAppService` 预结算路径 **需对称验收**。 |
| **修改方案** | 住院预结算/全结前 `assertEncounterHasDiagnosis`；失败码对齐门诊。 |

**涉及代码**

- `InpatientSettleAppService.java`

**验收用例**

- 无诊断住院预结算 → 拒绝。

**工作量**：0.5～1 人天

---

### GAP-CLN-PRICE-001 锁价策略未闭合

| 维度 | 说明 |
| --- | --- |
| **严重度** | 中危 |
| **关联** | **BND-PRICE-***（本文件边界节） |
| **现象** | 医嘱签发/执行/滚费创建 `ChargeItem` 时写入 `unitPrice/totalPrice`；后续 `ChargeItemDefinition` 调价 **不**回写已存在行；医保上传可能读第三套价。 |
| **根因** | 产品未定义「何时锁价、何时跟价」。 |
| **业务影响** | 结算价与价表不一致；报表 SUM 与定义表对不上。 |

**修改方案**

1. **产品规则**（须 PM 签字）：
   - **签发锁价**：PLANNED 行价格在签发时冻结；
   - **结算锁价**：preSettle 时刷新一次（可选）；
   - **退费时价**：按原 Charge 快照，不跟新价表。
2. **实现**：ChargeItem 增加 `priceDefinitionVersion` / `pricedAt`；结算 Gate 校验 definition 仍有效（失效则拒绝或按快照）。
3. 医保/报表 **只读 Charge 快照**，不读 live definition。

**涉及代码**

- `ChargeItemDomainService` 各 create 路径
- `ChargeItemDefinitionQueryService`

**工作量**：3～4 人天（含规则确认）

---

### GAP-CLN-PRICE-002 滚费 live 价

| 维度 | 说明 |
| --- | --- |
| **修改方案** | `NurseStationAutoRollWriteAssembler#resolveAutoRollChargeLine` 写入快照字段；见 **SYS-IMP-010 GAP-BAT-006**。 |

---

### GAP-CLN-006 患者合并与历史费用

| 维度 | 说明 |
| --- | --- |
| **严重度** | 中危 |
| **现象** | `PatientDomainService#mergePatient` 标记 source MERGED 并发 `PatientMergedEvent`；**未见**统一投影将历史 Encounter/Charge 迁移至 target 的叙事（可能由 listener 处理 — 需验收）。 |
| **根因** | 合并与挂号防重（身份证 vs patientId）割裂；`resolvePatientIdForPreRegister` 身份证命中旧 id，合并后可能仍有 orphan encounter。 |
| **修改方案** | 1. 合并 Saga：迁移 encounter.patientId、account.patientId；2. 挂号 Gate：拒绝 MERGED 源 patientId；3. 与 **GAP-FIN-006** 联动测试。 |

**涉及代码**

- `PatientDomainService#mergePatient`
- `PatientMergedEvent` listeners（若有）
- `OutpatientRegistrationAppService`

**验收用例**

- 合并后 source patientId 挂号 → 拒绝或重定向 target。
- 合并后历史 Charge 仍可在 target 下结算。

**工作量**：3～5 人天

---

### GAP-CLN-007 过敏史核发 Gate

| 维度 | 说明 |
| --- | --- |
| **严重度** | 中危 |
| **现象** | 患者 allergy 与 medication 成分交叉 — 核发路径待验收是否有硬 Gate 或仅医生站提示。 |
| **修改方案** | 若 AS 要求硬 Gate：`AllergyDispenseGatesupport；否则文档化软提示。 |

**状态**：待评估

---

## 4. 边界问题（锁价与主数据）

| 编号 | 主题 | 说明 | 建议 |
| --- | --- | --- | --- |
| BND-PRICE-001 | 签发锁价 | 签发后改价表不影响 PLANNED 行 | 默认采用 |
| BND-PRICE-002 | 结算锁价 | preSettle 是否刷新单价 | 产品二选一 |
| BND-PRICE-003 | 退费价 | 按原 Charge 快照退 | 与 BR-REF 一致 |
| BND-PRICE-004 | 批号价 vs 定义价 | IMP-005 进货补批号价 | Charge 锚点字段明确来源 |
| BND-CLN-001 | 皮试 override | 紧急核发 | 双签 + 审计 |

---

## 5. 推荐实施顺序

| 顺序 | ID | 内容 | 人天 |
| --- | --- | --- | --- |
| 1 | GAP-CLN-001 + 002 | 皮试 Gate + UI | 5～8 |
| 2 | GAP-CLN-005 | 住院诊断 Gate | 1 |
| 3 | GAP-CLN-PRICE-001 | 锁价规则+字段 | 3～4 |
| 4 | GAP-CLN-006 | 患者合并 | 3～5 |
| 5 | GAP-CLN-003/007 | 审方/过敏 | 按需 |

---

## 6. 不建议的做法

| 做法 | 原因 |
| --- | --- |
| 仅 UI 提示皮试不发药 | 可绕过 |
| 价表变更 retroactive 改 SETTLED 行 | 审计灾难 |
| 合并患者不迁移 encounter | 费用散落 |

---

## 7. 回归测试矩阵

| # | 场景 | 期望 |
| --- | --- | --- |
| T1 | 皮试药无记录核发 | 失败 |
| T2 | 皮试阳性核发 | 失败 |
| T3 | 价表涨价后结旧 PLANNED 行 | 按锁价规则 |
| T4 | 合并患者后挂号旧 id | 拒绝/重定向 |
| T5 | 住院无诊断预结算 | 失败 |

---

## 8. 修订记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v1.0 | 2026-06-21 | 初稿：临床闭环 Gate 与主数据锁价审查 |
