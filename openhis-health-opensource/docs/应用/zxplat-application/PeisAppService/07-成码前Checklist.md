# PeisAppService · 成码前 Checklist

> **版本**：v2.4 · **日期**：2026-07-19  
> **用途**：PEIS 文档 → 代码会话 **唯一门禁索引**；成码前逐项勾选。  
> **规范**：[`whale-peis-codegen.mdc`](../../../../.cursor/rules/whale-peis-codegen.mdc) · [`PEIS-LIS-PACS文档口径`](../../../../.cursor/skills/PEIS-LIS-PACS文档口径/SKILL.md)

---

## 1. 文档真源（须齐）

| 类别 | 路径 | 状态 |
|------|------|:--:|
| 功能清单 | [00-功能清单.md](./00-功能清单.md) | ✅ v2.2 |
| 概要设计 | [01-概要设计.md](./01-概要设计.md) | ✅ §7 跨模块 |
| AS 索引 | [03-AppService详细设计.md](./03-AppService详细设计.md) | ✅ |
| FHIR/领域 | [04-FHIR领域模型.md](./04-FHIR领域模型.md) | ✅ |
| Flyway | [06](./06-Flyway-表结构.md) · [peis/V1 SQL](../../../zxplat/zxplat/zxplat-domain/src/main/resources/db/migration/peis/) | ModularFlyway；history=`flyway_schema_history_peis` |
| Extension | [08-Extension与外部依赖.md](./08-Extension与外部依赖.md) | ✅ v1.2 · §3.2 adapter 投影 |
| 跨模块 SDD | [LIS AUD-001-SDD](../AppService-SDD/AS-LIS-AUD-001-结果审核-SDD.md) · **[PACS REP-002-SDD](../AppService-SDD/AS-PACS-REP-002-报告审核-SDD.md)** | P0 投影主触发 |
| 领域 SDD | [DomainModel-SDD/DM-PEIS-*](../DomainModel-SDD/) | 见 §3 |
| CLN SDD | [005](../DomainModel-SDD/DM-CLN-005-ObservationDefinition-临床观察定义-SDD.md) · [007](../DomainModel-SDD/DM-CLN-007-Observation-临床观察实例-SDD.md) · [006](../DomainModel-SDD/DM-CLN-006-DiagnosticReportIndex-报告读侧索引-SDD.md) · [008](../DomainModel-SDD/DM-CLN-008-DiagnosticReport-临床总检报告-SDD.md) | ✅ |
| CUS SDD | **[013 AOL](../DomainModel-SDD/DM-CUS-013-ActivityObservationLink-项目小项绑定-SDD.md)** | ✅ |
| 编排 SDD | [AS-PEIS-REG-ORCH](../AppService-SDD/AS-PEIS-REG-ORCH-登记编排-SDD.md) | application/peis |

---

## 2. 领域 DM + SDD（CLN · P0）

| DM | SDD | 说明 |
|----|-----|------|
| [005 OD](./DM-CLN-005-ObservationDefinition-临床观察定义.md) | [SDD](../DomainModel-SDD/DM-CLN-005-ObservationDefinition-临床观察定义-SDD.md) | CFG-03 |
| [007 实例](./DM-CLN-007-Observation-临床观察实例.md) | [SDD](../DomainModel-SDD/DM-CLN-007-Observation-临床观察实例-SDD.md) | DES/INT 投影 |
| [006 DR Index](./DM-CLN-006-DiagnosticReportIndex-报告读侧索引.md) | [SDD](../DomainModel-SDD/DM-CLN-006-DiagnosticReportIndex-报告读侧索引-SDD.md) | MAS/REP |
| [008 总检 DR](./DM-CLN-008-DiagnosticReport-临床总检报告.md) | [SDD](../DomainModel-SDD/DM-CLN-008-DiagnosticReport-临床总检报告-SDD.md) | MAS-03/04 |

## 2b. 领域 DM + SDD（CUS · P0）

| DM | SDD | 说明 |
|----|-----|------|
| [013 AOL](./DM-CUS-013-ActivityObservationLink-项目小项绑定.md) | [SDD](../DomainModel-SDD/DM-CUS-013-ActivityObservationLink-项目小项绑定-SDD.md) | CFG-04 / 投影 · `domain.custom` |

---

## 3. 领域 DM + SDD（7 份 PEIS）

| DM | SDD | P | 成码包 | 备注 |
|----|-----|---|--------|------|
| [001 SR Ext](./DM-PEIS-001-PeisServiceRequestExt-项目执行扩展.md) | [SDD](../DomainModel-SDD/DM-PEIS-001-PeisServiceRequestExt-项目执行扩展-SDD.md) | P0 | `domain.peis.servicerequestext` | INT/DES 依赖 |
| ~~002 AOL~~ | — | — | — | **已迁** [DM-CUS-013](./DM-CUS-013-ActivityObservationLink-项目小项绑定.md) |
| [003 CommonResult](./DM-PEIS-003-PeisCommonResult-常见结果.md) | [SDD](../DomainModel-SDD/DM-PEIS-003-PeisCommonResult-常见结果-SDD.md) | P0 | `domain.peis.commonresult` | |
| [004 Config](./DM-PEIS-004-PeisConfig-模块参数.md) | [SDD](../DomainModel-SDD/DM-PEIS-004-PeisConfig-模块参数-SDD.md) | P0 | `domain.peis.peisconfig` | 对齐 LIS-008 |
| [005 Grant](./DM-PEIS-005-PeisReportGrantExt-报告发放扩展.md) | [SDD](../DomainModel-SDD/DM-PEIS-005-PeisReportGrantExt-报告发放扩展-SDD.md) | P1 | `domain.peis.reportgrantext` | V76 |
| [006 AutoExpress](./DM-PEIS-006-PeisAutoExpress-自动诊断表达式.md) | [SDD](../DomainModel-SDD/DM-PEIS-006-PeisAutoExpress-自动诊断表达式-SDD.md) | P1 | `domain.peis.autoexpress` | V76 |
| [007 Encounter Ext](./DM-PEIS-007-PeisEncounterExt-就诊扩展.md) | [SDD](../DomainModel-SDD/DM-PEIS-007-PeisEncounterExt-就诊扩展-SDD.md) | P0 | `domain.peis.encounterext` | 登记编排在 AS |
| [008 Progress Query](./DM-PEIS-008-PeisEncounterProgressQuery-体检进度查询.md) | [SDD](../DomainModel-SDD/DM-PEIS-008-PeisEncounterProgressQuery-体检进度查询-SDD.md) | P0 | `domain.peis.encounterprogress` | 只读+信号 |

**SDD §1.4 PASS** 后方可进入领域成码会话（P0 领域 SDD 均已标 PASS）。

---

## 4. AppService + SDD（P0 优先）

| AS | SDD | P0 IF | Gap（仅 AS §3） |
|----|-----|-------|-----------------|
| REG-001 | ✅ | REG-01～04 | 无 |
| CFG-001 | ✅ | CFG-03～05（CFG-01/02 已撤销）；**P1** 06/07 并本 AS | GAP-CFG-01/02 |
| CFG-002/003 | ✅ | CRT/表达式/参数 | 见各 AS |
| INT-001 | ✅ | INT-01 | GAP-INT-01 = DES-08 |
| INT-005 | ✅ | INT-05 | adapter 只读；见 [AS](../AppService/AS-PEIS-INT-005-第三方入站.md) |
| DES-001 | ✅ | DES-01～04/08；**P1** 05～07/09 | GAP-DES-01 |
| MAS-001 | ✅ | MAS-01～04；**P1** 05～07 | GAP-MAS-01 |
| REP-001/002 | ✅ | REP-01～03；P1 05/06 | REP2 GAP P1 |
| BKG/GRP/REC/CRT/INT-002 | ✅ | 见 00 | 见各 AS |

---

## 5. 合法 Gap 汇总（领域层，成码前）

| Gap ID | 能力 | 关闭 |
|--------|------|------|
| GAP-PEIS-INT-01 / DES-01 | `LisResultProjectionService#projectFromLis` | DM-CLN-007 §6.3；**主触发 LIS AUD-05** |
| GAP-PACS-REP-AUD-01 | `projectFromPacsSummary` | DM-CLN-007 §6.3；**主触发 PACS REP-07**；[REP-002 SDD](../AppService-SDD/AS-PACS-REP-002-报告审核-SDD.md) |
| GAP-PEIS-MAS-01 | `PeisEncounterProgressQuery` | ✅ 已关：MAS-01 `searchPendingMasterReview`（2026-07-21） |
| GAP-PEIS-CFG-01/02 | AOL + OD DomainService | ✅ 领域 + **CFG-001 AppService** 已落地（2026-07-19） |
| GAP-PEIS-CFG2-02 / CFG3-01 | CommonResult + PeisConfig | ✅ 领域 + **CFG-002/003 AppService** 已落地（2026-07-19） |
| GAP-PEIS-CRT-* | DetectedIssue PE 语义 | P1 |
| GAP-PEIS-REC-* | Task 复查 | P1 |
| GAP-PEIS-REP2-01 | PeisReportGrantDomainService | P1 + V76 |
| GAP-PEIS-GRP-01 | searchGroupProgress | P2 |

**非 Gap**：`PeisRegistrationDomainService` 编排、`Peis*AppService`、Reader/Client 实现、Flyway 执行验证。

---

## 6. 跨模块依赖（成码顺序建议）

```text
1. Flyway：**peis V1**（`peis_*`）+ health **V3/V7**（CLN/AOL）已落库；开 `zxplat.modules.peis.enabled` ✅
2. EncounterClass.PE（ADM 枚举）✅
3. DM-PEIS-001/007/003/004 领域层
4. DM-CLN-005 OD + DM-CUS-013 AOL ✅；余 DM-CLN-007 Projection（LIS AUD-05 + **PACS REP-07**）
5. DM-CLN-008 DiagnosticReport（clinical 包）+ DM-CLN-006 index upsert（MAS-04）
6. DM-PEIS-008 ProgressQuery
7. LisWorkItem / PacsWorkItem（LIS/PACS 模块，INT-01 联调）
8. health-application/peis AppService + **peis-adapter**（INT-05）
9. booking Client 桩 / 联调（BKG/GRP）
10. zxplat-module-lis-kingmed EXT（金域，师大 9/1）
```

---

## 7. 代码现状（2026-07-19）

| 项 | 状态 |
|----|------|
| `zxplat/**/peis/**` Java | ✅ 领域 `peisconfig`/`commonresult`/`servicerequestext`/`encounterext` + `application/peis/config/*`（CFG）+ `registration`/`orchestration`（REG-001） |
| `EncounterClass.PE` | ✅ `zxplat-domain` 枚举已补（2026-07-19） |
| OD 领域（DM-CLN-005） | ✅ `domain.clinical.observationdefinition` |
| AOL 领域（DM-CUS-013） | ✅ `domain.custom.activityobservationlink`（QS 不跨聚合 enrich OD） |
| PeisConfig / CommonResult | ✅ `domain.peis.peisconfig` / `domain.peis.commonresult` |
| CFG AppService | ✅ `PeisCatalogConfig` / `PeisDiagnosisConfig` / `PeisModuleSettings` |
| 前端体检配置三页 | ✅ `web-ele/views/peis/config/{catalog,diagnosis,settings}` |
| REG-001 个检登记后端 | ✅ `PeisPersonalRegistrationAppService` + `PeisRegistrationOrchestrator`；菜单 V4 |
| peis ModularFlyway | ✅ `db/migration/peis/V1`～`V2` 最终态；history=`flyway_schema_history_peis` |
| **AS-PE-FIN-001 个检收费** | ✅ `physicalexam/financial/charge` + 前端 `/peis/charge`（2026-07-21）；懒生成 PLANNED 关闭 GAP-PE-CHG-02 |
| **AS-PEIS-DES-001 分科诊台** | ✅ P0 子集 DES-01～04（2026-07-21）：`domain.clinical.observation` 手工写路径、`PeisEncounterProgressQuery#searchPendingDeptDesk`、`application/peis/deptdesk` + 前端 `/peis/dept-desk`；DES-08 投影 Gate（GAP-PEIS-DES-01 未关） |
| **AS-PEIS-MAS-001 总检诊台** | ✅ P0 MAS-01～04（2026-07-21）：`domain.clinical.diagnosticreport` + `diagnosticreportindex`（catalog 表）、`finishEncounter`、`searchPendingMasterReview`、`application/peis/masterreview` + 前端 `/peis/master-review`；菜单 V9 |
| **DM-CLN-007 Observation** | ✅ 领域手工 CRUD + QS（`LisResultProjectionService` 仍 Gap） |
| **DM-CLN-008 DiagnosticReport** | ✅ `domain.clinical.diagnosticreport`（createOrGet / saveMasterDraft / finalizeMaster） |
| **DM-CLN-006 DR Index** | ✅ `upsertFromPeis` → `cln_diagnostic_report_catalog`（P0；LIS/PACS upsert 未建） |
| **DM-PEIS-008 ProgressQuery** | ✅ DES-01 + MAS-01 子集 + `signalProgressChanged` stub |
| **AS-PE-FIN-002 团检先收费** | ✅ 契约 ✅；成码见 physicalexam/financial/groupcharge |
| **AS-PEIS-GRP-001 改定** | ✅ 契约 v1.2（到检不建单）；后端+前端已落地 |
| **BR-PE-ANNUAL-01 / REP-05 / BAS-05** | ⚠ REP-05/ARCH 后端子集已落地；完整报告打印仍缺 |
| `peis-adapter` | ⬜ 未建（[INT-005 AS+SDD](../AppService/AS-PEIS-INT-005-第三方入站.md) 已齐） |
| `peis-starter` | **不需要**（PEIS 主模块装配，见 01 §4） |

---

| 正确 | 禁止 |
|------|------|
| INT-01 → **本院** LisWorkItem/PacsWorkItem | 「不发 LIS 申请」 |
| **外发 LIS** = **金域** LisKingMed EXT | 与 INT-01 混写 |
| 分科结论 → `cln_observation` + OD.dept_conclusion | `peis_dept_conclusion` |
| 无 `peis_*_project_map` | 对照表 |
| 团检：**先收费后建 Encounter**（FIN-002） | GRP-05 再建 Encounter |

---

## 9. 修订记录

| 版本 | 日期 | 说明 |
|------|------|------|
| v2.5 | 2026-07-19 | 团检先收费 FIN-002；GRP-05 到检确认；年度唯一/毕业合打/class_name |
| v2.4 | 2026-07-19 | 体检配置 CFG-001/002/003 前后端成码；peis V2 菜单；§5/§7 更新 |
| v2.3 | 2026-07-19 | EncounterClass.PE + OD/AOL 领域成码；§5/§7 状态更新 |
| v2.2 | 2026-07-19 | peis ModularFlyway（独立目录/history）；§6/§7 对齐 |
| v2.1 | 2026-06-27 | 文档债清理；08-Extension PACS adapter 投影；PACS REP-002 SDD |
| v2.0 | 2026-06-27 | Flyway 并入 zxplat-domain；`cln_diagnostic_report V79 |
| v1.9 | 2026-06-27 | PACS REP-07 主路径；INT-005 AS/SDD；无 peis-starter |
| v1.7 | 2026-06-27 | DM-PEIS-002 → **DM-CUS-013**；包 `domain.custom` |
| v1.6 | 2026-06-27 | LIS AUD-05 → cln_observation 主路径；INT-03 补偿 |
| v1.5 | 2026-06-27 | DM-PEIS-002 §1.1 AOL 跨模块；Lis D10；CLN-007 投影读 AOL |
| v1.4 | 2026-06-27 | DM-CLN-008 去 Clinical 前缀；002/003/006 SDD §1.4 微扩 |
| v1.3 | 2026-06-27 | 旧 CLN-005 链接批量改 005/007/008；成码规则补 008 |
| v1.1 | 2026-06-27 | CLN 005/007 拆分；CLN SDD 三份；REG-ORCH SDD |
| v1.0 | 2026-06-27 | 首版 |
