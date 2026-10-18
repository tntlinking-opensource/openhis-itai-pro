# PeisAppService · Extension 与外部依赖

> **版本**：v1.2 · **日期**：2026-06-27  
> **用途**：PEIS 与 booking / 收费 / LIS / PACS / campus 边界契约索引（**文档**，实现位于各模块 adapter 或 contracts）。

---

## 1. booking H 线

### 1.1 `PeisBookingAppointment旧命名（已废止）

| 项 | 说明 |
|----|------|
| **消费 AS** | [AS-PEIS-BKG-001](../AppService/AS-PEIS-BKG-001-预约衔接.md) |
| **实现方** | booking 模块（`HealthcareService.category = PHYSICAL_EXAM`） |
| **非 Gap** | 外部集成；PEIS 仅声明 Extension/contracts |

| 方法 | 语义 | IF |
|------|------|-----|
| `getAppointmentSnapshot(Long appointmentId): PeisAppointmentSnapshot` | 读预约快照（套餐/患者/到检态） | BKG-01 |
| `linkEncounter(Long appointmentId, Long encounterId)` | 登记后回写 encounterId | BKG-01 |
| `findAppointmentByBusNo(String busNo): Optional<Long>` | 可选：流水反查 | BKG-02 |

**`PeisAppointmentSnapshot` 最小字段**：`appointmentId`, `patientId`, `packageActivityId`, `healthcareServiceCategory`, `statusEnum`, `slotStartTime`

---

### 1.2 `PeisGroup`BookingClient`（规划）

| 项 | 说明 |
|----|------|
| **消费 AS** | [AS-PEIS-GRP-001](../AppService/AS-PEIS-GRP-001-团检管理.md) |
| **实现方** | booking 团检扩展 |

| 方法 | 语义 | IF |
|------|------|-----|
| `saveCompany(SaveCompanyCommand)` | 单位 | GRP-01 |
| `saveContract(SaveContractCommand)` | 合同 | GRP-02 |
| `saveGroupBatch(SaveGroupBatchCommand)` | 批次 | GRP-03 |
| `createGroupOrder(CreateGroupOrderCommand)` | 团体订单 | GRP-04 |
| `importRoster(ImportRosterCommand)` | 名单导入 | GRP-04 |
| `isOnRoster(groupBatchId, patientId): boolean` | 到检校验 | GRP-05 |
| `getCompany` / `getGroupBatch` | 读 | GRP-* |

PEIS 侧持久化：`peis_encounter_ext.company_id / contract_id / group_batch_id`（[DM-PEIS-007](./DM-PEIS-007-PeisEncounterExt-就诊扩展.md)）。

---

## 2. 收费（health ADM）

### 2.1 `ChargeItem`QueryReader`（规划）

| 项 | 说明 |
|----|------|
| **消费 AS** | [AS-PEIS-REG-001](../AppService/AS-PEIS-REG-001-个检登记.md) REG-08 |
| **实现方** | health `ChargeItemQueryService` 适配（或 thin Client impl） |
| **非 Gap** | 读 Reader |

| 方法 | 语义 |
|------|------|
| `isEncounterPaid(Long encounterId): boolean` | 是否已收费（REG 门禁展示） |

> 完整收费明细能力见 [DM-ADM-011](./DM-ADM-011-ChargeItem-收费明细.md)；PEIS **不**重复实现 ChargeItem 写侧。

---

## 3. LIS / PACS（院内执行）

| 能力 | 落点 | PEIS 关系 |
|------|------|-----------|
| LisWorkItem 创建 | LIS INT + [AS-PEIS-INT-001](../AppService/AS-PEIS-INT-001-LIS-PACS外发.md) | INT-01 **本院** |
| PacsWorkItem 创建 | 同上 | 同 `activity_id` |
| lis_observation 结果 | LIS 模块 | **AUD-05** 投影 → CLN；INT-03 / DES-08 补偿 |
| pacs 报告摘要 | PACS 模块 / **pacs-adapter** | **REP-07** 投影 → CLN；DES-08 补偿 |
| **金域外送** | [zxplat-module-lis-kingmed](../../../模块/zxplat-module-lis-kingmed/) | LIS EXT，**不是** INT-01 替代 |

### 3.1 LIS 投影（主路径）

| 步骤 | 说明 |
|------|------|
| 1 | PEIS INT-01 创建 LisWorkItem |
| 2 | LIS 审核发布（AUD-05） |
| 3 | 同事务 `LisResultProjectionService#projectFromLis` → upsert `cln_observation` |
| 4 | `peis_service_request_ext.lis_dispatch_enum` → `resultReceived` |
| 5 | 漏投影时 PEIS INT-03 / DES-08 补偿 |

### 3.2 PACS adapter 院点 · CLN 投影

> **院点**：东北师大 **2026-09-01**；朝阳等同理。真源编排：[Pacs 01 §4.2](../PacsAppService/01-概要设计.md#42-东北师大-nenu-profile)、AS-PACS-REP-002、「SDD」。

| 步骤 | 说明 |
|------|------|
| 1 | PEIS INT-01 创建 **PacsWorkItem**（zxplat-domain；同 `activity_id`，无 map） |
| 2 | **pacs-adapter** 与现场 PACS 同步（`/webservice/*`、WorkStationServer） |
| 3 | 现场报告 **审核通过 / final** → adapter 回调 health **final 命令**（等价 REP-07，非 PEIS 重复 INT-01） |
| 4 | 同事务：`pacs_diagnostic_report final + upsert `cln_diagnostic_report_index` |
| 5 | 对每个关联 SR（有 `encounterId`）：`LisResultProjectionService#projectFromPacsSummary` → upsert `cln_observation`（BR-CLN-OBS-05） |
| 6 | `peis_service_request_ext.pacs_dispatch_enum` → `resultReceived` |
| 7 | 可选 INT-06 报告路径回传 HIS |
| 8 | 漏投影 → PEIS **DES-08** / INT-03 补偿（与 LIS 对称） |

**边界**：

- adapter **禁止**直写 `cln_observation` 或 health 侧项目对照表
- 投影实现 **唯一** 经 [DM-CLN-007 §6.3](./DM-CLN-007-Observation-临床观察实例.md)（Gap **GAP-PACS-REP-AUD-01**）
- 自研 RIS 院点走 `PacsReportAuditAppService` REP-07；adapter 院点走同一领域命令，编排入口在 **pacs-adapter** 薄层

---

## 4. campus / miniapp（只读）

| Extension / 能力 | 用途 |
|-------------|------|
| Campus 档案 Reader | REG/GRP 读学生教职工 |
| miniapp BFF | 预约/报告查询（非 PEIS AS） |

---

## 5. peis-adapter（INT-05）

| 项 | 说明 |
|----|------|
| **定位** | 第三方 **入站**只读；sdpeis 兼容 |
| **AS/SDD** | [AS-PEIS-INT-005](../AppService/AS-PEIS-INT-005-第三方入站.md) · [SDD](../AppService-SDD/AS-PEIS-INT-005-第三方入站-SDD.md) |
| **禁止** | 项目对照 map；重复 INT-01 本院外发 |

> **无 `peis-starter`**：PEIS 随 zxplat 主应用装配（[01 §4](./01-概要设计.md#4-maven)）。

---

## 6. 修订记录

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.2 | 2026-06-27 | §3.1/3.2 LIS AUD-05 与 PACS adapter→CLN 投影时序 |
| v1.1 | 2026-06-27 | INT-005 AS/SDD；PACS REP-07 投影链；无 peis-starter |
| v1.0 | 2026-06-27 | 首版；contracts 名与现有 AS-SDD 对齐 |
