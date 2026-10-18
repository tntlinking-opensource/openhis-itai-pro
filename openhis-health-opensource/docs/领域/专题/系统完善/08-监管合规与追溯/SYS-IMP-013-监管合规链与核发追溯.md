# SYS-IMP-013 监管合规链与核发追溯边界

> **问题类型**：监管合规与追溯 · [本类索引](./README.md) · [总索引](../README.md)
>
> **来源**：2026-06 代码审查；**2026-07-13** 追溯 SPI 合并：`DispenseTraceGateExtension` 已移除，CHS 侧校验内聚于 `InventoryThirdPartyUploadOrchestrator`。
>
> **关联 AS**：`DM-MED-003`（BR-MRQ-018/027 等）、`AS-OP-PHA-001/002`、`AS-IP-PHA-001/002`、打印类 AS（多处标注非本 AS）
>
> **关联**：`SYS-IMP-011` GAP-INT-001/002（3505 Outbox）；`SYS-IMP-012` GAP-CLN-001/003；`SYS-IMP-008` GAP-ACL-006（可选模块模式）
>
> **状态**：待实施

---

## 1. 背景与结论摘要

ZXPlat **已具备**部分监管能力：

| 能力 | 落点 | 条件 |
| --- | --- | --- |
| 医保进销存追溯校验 | `InventoryThirdPartyUploadExtension` → `InventoryUploadTraceLineSupport` / Orchestrator | **`zxplat.modules.chs.enabled=true` 且上报启用** |
| 门诊分方/金额阈值 | `OutpatientPrescriptionSplitAssembler` → `splitPrescription` | 门诊西药/中成药签发（排除中药饮片） |
| 追溯码入库 | 药库/药房进货 `InventoryWarehouseInboundReceiptAppService` | 进货同意 |
| 3505 销售上报 | `ChsInventoryAppService` | 见 IMP-011 |

**结论**：

- 追溯 **CHS 上报前校验**在 `InventoryUploadTraceLineSupport`（`block-settle-without-trace`）；**不再**在 prepare/dispense 前单独调用 Extension 门控。
- 院内核发仍依赖 `TraceNumberQueryService`（在库/在院/重复码等）；**空 traceNo** 是否拦截取决于是否提交码及 CHS 上报配置。
- **处方权、法定疗程、补打审计、批号召回禁发、高值植入物 UDI** 等监管链 **未**在 IMP-001～012 系统审查；本文件补齐。

---

## 2. 问题清单总览

| ID | 标题 | 严重度 | 门/住 | 优先级 | 状态 |
| --- | --- | --- | --- | --- | --- |
| GAP-REG-001 | 追溯 Gate 依赖 CHS 模块，默认无码可核发 | **高危** | 门+住 | P0 | 待实施 |
| GAP-REG-002 | 监管品种「应扫未扫」与空 traceNo 路径 | **高危** | 门+住 | P0 | 待实施 |
| GAP-REG-003 | 本地追溯记录与 3505 上报不一致 | 中危 | 药库 | P1 | 见 SYS-IMP-011 |
| GAP-REG-004 | 处方权/精麻权限签发 Gate 未落地 | **高危** | 门+住 | P1 | 待实施 |
| GAP-REG-005 | 法定疗程/用量（BR-MRQ-018）签发验收不足 | 中危 | 门诊 | P2 | 待验收 |
| GAP-REG-006 | 处方/小票/挂号单补打审计不完整 | 中危 | 门+住 | P2 | 待实施 |
| GAP-REG-007 | 批号召回/禁发未阻断核发 | 中危 | 门+住 | P2 | 待实施 |
| GAP-REG-008 | 高值耗材/植入物 UDI 与 Charge 锚点 | 中危 | 住 | P2 | 待评估 |
| GAP-REG-009 | 急诊/绿色通道 override 无统一审计模型 | 低危～中危 | 门+住 | P3 | 待评估 |
| GAP-REG-010 | 汇总核发追溯与单行核发口径一致性 | 中危 | 住 | P1 | 待验收 |

---

## 3. 分项：问题与修改方案

### GAP-REG-001 追溯 Gate 可选导致无 CHS 无码核发

| 维度 | 说明 |
| --- | --- |
| **严重度** | **高危**（飞检「应扫未扫」） |
| **关联 IF/BR** | 追溯码 TRC-WIDE-R-001；3505 监管要求 |
| **现象** | （2026-07-13 前）`DispenseTraceGateExtension` 在 prepare/dispense 前校验；已合并进 `InventoryThirdPartyUploadExtension` 上报链。CHS 未启用时 **无** 3505 上报前校验；院内 `TraceNumberQueryService` 仍生效。 |
| **根因** | 院内 mandatory 监管开关仍缺（见方案 1）；CHS 校验仅在业务成功后的上报阶段。 |
| **业务影响** | 生产误关 CHS 或单模块部署时，全品种可无码出库。 |

**修改方案（推荐组合）**

1. **双开关**：`openhis.trace.dispense-gate.required=true` 独立于 CHS；院内监管品种表驱动 Gate。
2. **启动自检**：required=true 但无 Gate 实现 → **启动失败**（对齐 IMP-008 ACL-006）。
3. 保留 CHS Client 作 **增强校验**（医保码段规则），不与院内 mandatory 混为一开关。

**涉及代码**

- `InventoryThirdPartyUploadExtension` / `InventoryThirdPartyUploadExtensionImpl`
- `InventoryUploadTraceLineSupport`、`InventoryThirdPartyUploadOrchestrator`
- `OutpatientPharmacyDispenseAppService`、`InpatientPharmacyDispenseAppService`（院内 `TraceNumberQueryService`）

**验收用例**

- T1：CHS 关、trace.required=true → 无码核发失败。
- T2：CHS 开、required=false → 行为可配置。

**工作量**：2～3 人天

---

### GAP-REG-002 应扫品种空 traceNo 仍可提交

| 维度 | 说明 |
| --- | --- |
| **严重度** | **高危** |
| **现象** | 配药循环：`lineTraceCodes` 为空时跳过 `assertCodesInNetStockForDispenseLine`；Gate Extension 对空列表行为依赖 CHS Client。 |
| **根因** | 「有码才校验」；缺 **按品种/批号强制扫满数量** 规则。 |
| **修改方案** | 1. `MedicationTracePolicy`：按 `medicationId + distributionCategory` 标记 `traceMandatory`；2. 核发前 `assertTraceCountMatchesDispenseQty`；3. 汇总核发 `InpatientPharmacySummaryDispenseAppService` 对称。 |

**涉及代码**

- 上述核发 AppService + `TraceNumberRecordQueryService`
- `InpatientPharmacySummaryDispenseAppService`

**验收用例**

- 监管品种 qty=2 只扫 1 码 → 失败。
- 非监管品种无码 → 允许（可配置）。

**工作量**：3～4 人天

---

### GAP-REG-003 本地追溯 vs 3505

| 维度 | 说明 |
| --- | --- |
| **修改方案** | 见 **SYS-IMP-011 GAP-INT-001/002**；核发成功 → outbox 3505；对账台比对 `trace_number_record` 与 `yb_inv_upload`。 |

---

### GAP-REG-004 处方权签发 Gate 未落地

| 维度 | 说明 |
| --- | --- |
| **严重度** | **高危** |
| **关联 IF/BR** | DM-MRQ 处方权；`MedicationManageInitDto` 仅有字典项 |
| **现象** | 医生站 `signOff` / 签发路径 **未见**统一 `PractitionerPrescriptionPrivilegeGate`（抗菌、精麻、毒性药品等级别）。 |
| **根因** | 主数据字典与签发写路径未编排。 |
| **业务影响** | 无处方权医师仍可签发受限品种。 |

**修改方案**

1. 新增 `PrescriptionPrivilegeGatesupport：输入 `practitionerId + medicationId/category + rxTypeCode`；
2. 挂接：`DoctorStationAdviceAppService` 批量签发、住院对称路径、中药签发；
3. 失败码：`DOR-RX-403`；override 需双签角色 + 审计（BND-REG-001）。

**涉及代码**

- `DoctorStationAdviceAppService.java`
- `InpatientDoctorStationAdviceAppService.java`（对称）
- 新建 Gate Support

**验收用例**

- 无精麻权医生签发麻药 → 403。
- 有权限 → 成功。

**工作量**：4～5 人天（含主数据映射）

---

### GAP-REG-005 法定疗程 BR-MRQ-018

| 维度 | 说明 |
| --- | --- |
| **严重度** | 中危 |
| **现象** | DM 定义普通 ≤7 日、急诊 ≤3 日；签发 FIFO/数量校验 **需逐路径验收**是否全覆盖。 |
| **修改方案** | 签发 Gate：`assertDurationWithinLegalLimit(therapy, encounterClass, daysSupply)`；急诊 encounter 标记走 3 日上限。 |

**状态**：待验收 + 缺则补 Gate

**工作量**：2～3 人天

---

### GAP-REG-006 补打/重打审计

| 维度 | 说明 |
| --- | --- |
| **严重度** | 中危 |
| **现象** | `printRegisterTicket` 累加 `printCount`；`PaymentReconciliation.printCount` 存在；**未见**强制写入 `whale_audit_logs` 或专用「补打原因 + 操作人」表。 |
| **修改方案** | 1. 补打 API 必填 `reprintReason`；2. 写 `PrintAuditRecord`（entityType, entityId, operator, reason, copyNo）；3. 超 N 次补打需主管授权。 |

**涉及代码**

- `OutpatientRegistrationAppService#printRegisterTicket`
- 打印数据集服务（处方、收费小票）

**工作量**：2 人天

---

### GAP-REG-007 批号召回禁发

| 维度 | 说明 |
| --- | --- |
| **严重度** | 中危 |
| **现象** | 药库有批号/lot；**未见** `LotRecall` 主数据与核发 Gate「召回批号禁止出库」。 |
| **修改方案** | 1. 主数据维护召回批号列表；2. `prepare/dispense` 前 `assertLotNotRecalled(lotNumber, medicationId)`；3. 与库存 Query 联动。 |

**状态**：待实施（视院方是否启用召回模块）

**工作量**：3 人天

---

### GAP-REG-008 高值植入物 UDI

| 维度 | 说明 |
| --- | --- |
| **严重度** | 中危 |
| **现象** | IMP-001 GAP-EXE-004 覆盖绑定耗材扣库；**手术植入物 UDI 与 Charge/患者终身追溯** 未成文。 |
| **修改方案** | 手术耗材发放扩展 `DeviceDispense` 存 UDI；Charge 锚点 `udi`；报表对接 SPD（若上线）。 |

**状态**：待评估

---

### GAP-REG-009 急诊绿色通道 override

| 维度 | 说明 |
| --- | --- |
| **严重度** | 低危～中危 |
| **现象** | 急诊处方类型 `rxTypeCode` 有推导；**先药后付、先出后审** 等 override 分散，无统一 `ClinicalOverrideRecord`。 |
| **修改方案** | 产品定义 override 类型枚举 + 双签 + 审计；与 IMP-012 皮试 override（BND-CLN-001）统一模型。 |

**状态**：待评估

---

### GAP-REG-010 汇总核发追溯一致性

| 维度 | 说明 |
| --- | --- |
| **严重度** | 中危 |
| **现象** | `InpatientPharmacySummaryDispenseAppService` 含 `SummaryDispenseTraceLineSubmitDto`；须验收与单行核发 **同一套** REG-001/002 Gate。 |
| **修改方案** | 抽取 `DispenseTraceWriteGatesupport 供单行/汇总共用；回归 IMP-005 GAP-INV-003 并发场景。 |

**工作量**：1～2 人天

---

## 4. 边界问题

| 编号 | 主题 | 说明 | 建议 |
| --- | --- | --- | --- |
| BND-REG-001 | Override 双签 | 无码核发、无处方权紧急 | 角色 + 原因 + 审计 |
| BND-REG-002 | 中药/代煎 | 追溯口径可能与西药不同 | 分品种策略 |
| BND-REG-003 | 院内 vs 医保追溯 | CHS 码段规则 ⊃ 院内规则 | 分层 Gate |

---

## 5. 推荐实施顺序

| 顺序 | ID | 内容 | 人天 |
| --- | --- | --- | --- |
| 1 | GAP-REG-001 + 002 + 010 | 追溯 mandatory + 汇总一致 | 5～7 |
| 2 | GAP-REG-004 | 处方权 Gate | 4～5 |
| 3 | GAP-REG-006 | 补打审计 | 2 |
| 4 | GAP-REG-005/007/008/009 | 按上线模块 | 按需 |

---

## 6. 不建议的做法

| 做法 | 原因 |
| --- | --- |
| 仅依赖 3505 上报代替核发 Gate | 上报失败仍已出库 |
| 追溯与 CHS 单开关绑定 | 无医保也要监管 |
| 补打只加 printCount 无原因 | 审计不可用 |

---

## 7. 回归测试矩阵

| # | 场景 | 期望 |
| --- | --- | --- |
| T1 | trace.required=true，无码核发 | 失败 |
| T2 | 监管品种码数量≠发放量 | 失败 |
| T3 | 无精麻权签发 | DOR-RX-403 |
| T4 | 补打无 reason | 失败 |
| T5 | 召回批号核发 | 失败 |
| T6 | 汇总核发缺码 | 与单行一致失败 |

---

## 8. 修订记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v1.0 | 2026-06-21 | 初稿：监管合规链与核发追溯边界审查 |
