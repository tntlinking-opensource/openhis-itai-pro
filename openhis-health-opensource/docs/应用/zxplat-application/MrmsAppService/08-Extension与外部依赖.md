# MrmsAppService · Extension 与外部依赖

> **版本**：v1.9 · **日期**：2026-09-04  
> **用途**：MRMS 跨 Module 边界契约索引（**按提供方 Module 命名**；实现位于 application support / 各 Module adapter）。

---

## 0. 术语

| 术语 | 含义 | MRMS 是否使用 |
|------|------|---------------|
| **Extension** | `health-integration-contracts` 中 `integration.{emr\|notification\|insurance…}.*Extension` | ✅ |
| **ExtensionImpl** | `module-emr-bridge/.../integration/emr/mrms` 或 `{mod}-adapter` 中的 `@Primary` 实现 | ✅ |
| **`*Adapter`（domain SPI）** | application 薄类，实现 domain 可选接口并委托 Extension | ✅ |
| **`integration/mrms`** | **已删除** — EMR → `integration.emr`；通知 → `integration.notification` | ❌ |
| **读 HIS** | `*QueryService` / `InsuranceEncounterLoader` | ✅ |

命名原则：**Extension 按提供方 Module 命名**（如 `EmrDocumentExtension`），**禁止**调用方前缀（如 ~~`MrmsEmr*Extension`~~）。

---

## 1. EmrDocumentExtension

| 项 | 说明 |
|----|------|
| **接口** | `com.zxplat.domain.integration.emr.EmrDocumentExtension` |
| **模型** | `integration.emr.dto.EmrDocumentSnapshot` |
| **实现** | `EmrDocumentExtensionImpl`（`zxplat-module-emr/module-emr-bridge/.../integration/emr/mrms`，`-Pwith-emr`） |
| **默认** | `NoOpEmrDocumentExtension` |
| **消费方** | `DefaultQcRuleExecutor`、`MrmsCaseRecordVolumeAppService`、`MrmsCaseRecordQcAppService.archive()`、`MrmsOutpatientEmrQueryAppService` |

| 方法 | 语义 |
|------|------|
| `listByEncounter` | 卷宗目录文书列表 |
| `listSubmittedDocumentTypeCodes` | 必写文书质控 |
| `exportPdf` | 卷宗 PDF 单份渲染 |
| `lockAllByEncounter` / `unlockAllByEncounter` | 归档锁定 / 解档恢复 |
| `queryOutpatientDocumentPage` | 门诊病历跨患者分页（`documentTypeCode` 默认 `OUTPATIENT_RECORD`；可选 patientIds/encounterIds/status） |

归档锁定：`archive()` 成功后 `IntegrationAfterCommitSupport.runAfterCommit` 调用 `lockAllByEncounter`。

---

## 2. StaffNotificationExtension

| 项 | 说明 |
|----|------|
| **接口** | `com.zxplat.domain.integration.notification.StaffNotificationExtension` |
| **命令** | `StaffNotificationCommand` |
| **默认** | `LoggingStaffNotificationExtension`（SLF4J 日志） |
| **消费方** | `CaseRecordOverdueScanJob` → `CaseRecordOverdueScanService` |

---

## 3. MedcasHomepageUploadExtension（4401）

| 项 | 说明 |
|----|------|
| **接口** | `com.zxplat.domain.integration.insurance.medcas.MedcasHomepageUploadExtension` |
| **载荷** | `MedcasHomepagePayload` |
| **实现** | `MedcasHomepageUploadExtensionImpl`（`chs-jilin-bridge`） |
| **消费方** | `MrmsCaseRecordUploadAppService` |

---

## 4. EmrAdmissionConditionExtension

| 项 | 说明 |
|----|------|
| **接口** | `com.zxplat.domain.integration.emr.EmrAdmissionConditionExtension` |
| **实现** | `EmrAdmissionConditionExtensionImpl`（`-Pwith-emr`） |
| **domain 适配** | `MrmsEmrAdmissionConditionResolverAdapter` → domain `AdmissionConditionResolver` |
| **消费方** | `CaseRecordAutoFillQueryService`（经 domain SPI） |

---

## 5. LIS / PACS 诊疗外发

| Extension | 包 | 消费方 |
|-----------|-----|--------|
| `LisAdviceApplyExtension` | `integration.lis` | 门诊/住院医生站开嘱（IF-DOR-15 / IF-IP-DOR-15） |
| `PacsAdviceApplyExtension` | `integration.pacs` | 同上 |

未装 LIS/PACS Module 时由 `NoOpLisAdviceApplyExtension` / `NoOpPacsAdviceApplyExtension` 占位。

---

## 5.1 InsuranceContractTerminalExtension（契约终端参数）

| 项 | 说明 |
|----|------|
| **接口** | `com.zxplat.domain.infrastructure.integration.insurance.contract.InsuranceContractTerminalExtension` |
| **出参** | `InsuranceContractTerminalQueryExtensionOut` |
| **实现** | `InsuranceContractTerminalExtensionImpl`（`chs-jilin-bridge` → `ChsContractPropertiesAccess`） |
| **默认** | `NoOpInsuranceContractTerminalExtension` |
| **消费方** | `InsuranceEncounterLoader`（4401/4101A 定点机构编码） |

| 方法 | 语义 |
|------|------|
| `findByContractBusNo` | 按 `fin_contract.bus_no` 读 `chs-contract.properties` 的 `fixmedins_code`/`fixmedins_name`/`adm_vs` |

**禁止**从 `fin_contract.fixmedins_code` 等历史列组装上报报文（表列可空，会导致 mdtrt_sn/fixmedins 校验失败）。

---

## 6. 读 HIS（非 Extension）

| 组件 | 说明 |
|------|------|
| `InsuranceEncounterLoader` | 4401/4101A 医保上下文（结算键读 HIS；定点编码经 §5.1 SPI；`bill_code` 博思批次码→发票号，住院号由 4101A Mapper 兜底） |
| `CaseRecordAutoFillQueryService` 等 | 直注入 health `*QueryService` |

---

## 修订记录

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.9 | 2026-09-04 | Loader 票据：`bill_code` 对齐老 ifnull 兜底说明 |
| v1.8 | 2026-09-04 | 增补 `InsuranceContractTerminalExtension`；定点编码改读 chs-contract |
| v1.7 | 2026-06-27 | `integration.diagnostic` 拆为 `integration.lis` + `integration.pacs` |
| v1.6 | 2026-06-27 | 删除 `integration/mrms`；EMR → `integration.emr`；通知 → `StaffNotificationExtension` |
| v1.5 | 2026-06-27 | 全仓 domain SPI Adapter 命名约定 |
| v1.4 | 2026-06-27 | 废止 Bridge 术语 |
| v1.3 | 2026-06-27 | 4401 / AdmissionCondition / LIS-PACS |
| v1.0 | 2026-06-27 | 首版 |
