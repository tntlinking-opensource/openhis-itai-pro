# PacsAppService · FHIR 对齐领域模型

> **版本**：v1.5 · **日期**：2026-06-27  
> **落地方案**：**`PacsWorkItem`** 聚合根 + 子实体 **`PacsWorkItemOrder`**（多项合并一次检查）；影像事实 **`ImagingStudy`**；报告 **`DiagnosticReport`**。  
> **正式 DM**：[DM-PACS-001 PacsWorkItem](./DM-PACS-001-PacsWorkItem-检查工单.md) · **Domain SDD**：DM-PACS-001-SDD（以 DM/SDD 为准；本文保留总览与跨实体关系）  
> **跨模块约定**：[01-概要设计 §7](./01-概要设计.md#7-lispacspeis-跨模块架构约定)  
> **关联**：[00-功能清单.md](./00-功能清单.md) §1.3、[README.md](./README.md) §2

---

## 一、总览：FHIR 影像链路

```text
health                                    pacs（zxplat-domain/pacs 子包）
──────                                    ───────────────
ServiceRequest × N（多条医嘱行）  ──┐
                                    ├──► PacsWorkItem（1 次检查会话，聚合根）
ServiceRequest × 1（单项）      ──┘         │
                                              │ 1──N PacsWorkItemOrder
                                              ▼
                                         ImagingStudy（Study/Series UID）
                                              │
                    ┌─────────────────────────┼─────────────────────────┐
                    ▼                         ▼                         ▼
              DiagnosticReport            Media / DocumentReference    Device
        （pacs_diagnostic_report）  （关键图、PDF）
                              │
                              └── 发布 → cln_diagnostic_report_index（[DM-CLN-006](./DM-CLN-006-DiagnosticReportIndex-报告读侧索引.md)）
```

**原则**：代码侧以 **`PacsWorkItem`** 为 RIS 执行聚合根；互操作/区域平台导出时再 Assembly 为 FHIR JSON。

**与 LIS 对称**：LIS 用 `LisWorkItem`（标本/条码）；PACS 用 `PacsWorkItem`（检查号/登记）；二者均通过 `orderLinks` 挂接 health `ServiceRequest`，**不复制**患者主数据。

---

## 二、多项合并：GROUPCODE 与 orderLinks

### 2.1 业务场景

| 场景 | 说明 | 遗留对标 |
|------|------|----------|
| **合并检查** | 一次摄片覆盖多项医嘱（胸部 CT 平扫+增强计费拆分） | `sendApply` 中 `groupCode` 合并，`YGZZ_HZXX` 一行 |
| **单项检查** | 1 `ServiceRequest` → 1 `PacsWorkItem` | 常见 |
| **追加项** | 已登记后追加医嘱，同会话补做 | 租户策略，P2 |
| **拆单** | 一项分两次检查（罕见） | 1 SR → N `PacsWorkItem`，P2 |

并管时 **医嘱粒度仍是「一行一项」**（health `ServiceRequest`），**执行粒度是「一次检查会话」**（pacs **`PacsWorkItem`**）。

### 2.2 聚合设计

```text
PacsWorkItem（聚合根，表 pacs_work_item）
  id, accessionNumber（≈ APPLYNO）
  patientId, encounterId
  organizationId（≈ REGIRID）
  statusEnum（≈ JCZT：registered → scheduled → in-progress → completed → …）
  modalityCode（≈ SBLX）, bodySite（≈ JCBW）
  deviceId（≈ SBID）, performerPractitionerId（≈ JCYS）
  scheduledTime,   performedTime（≈ JCSJ）
  │
  └── orderLinks: PacsWorkItemOrder[]（表 pacs_work_item_order）

PacsWorkItemOrder（子实体）
  workItemId
  serviceRequestId          -- UNIQUE (workItemId, serviceRequestId)
  activityId
  linkStatusEnum
  groupCode                 -- 冗余，来源 GROUPCODE
  sortNumber

ImagingStudy（实体，表 pacs_imaging_study）
  workItemId                -- 1:0..1 或 1:N（复查场景）
  studyUid（≈ STUUID）
  seriesUid（≈ XDID）
  numberOfSeries, numberOfInstances
  endpoint                  -- DICOMweb / WADO 根 URL

DiagnosticReport
  serviceRequestId          -- 临床按医嘱看报告（分项报告时）
  workItemId                -- RIS 主键
  status（registered/partial/preliminary/final/amended）
  conclusion, findings
  media[] → Media ids
  presentedForm → DocumentReference（PDF）

Media
  workItemId
  contentType, url, title
  type（screenshot / key-image / video）

DocumentReference
  workItemId
  contentType application/pdf
  url / storageKey
```

### 2.3 FHIR 映射（互操作层）

| pacs-domain | FHIR R4 |
|-------------|---------|
| `PacsWorkItem` | 无单一资源；导出时组装 `ImagingStudy` + `ServiceRequest` 引用 |
| `PacsWorkItemOrder` | `ImagingStudy.basedOn` → `ServiceRequest` |
| `ImagingStudy` | `ImagingStudy` |
| `DiagnosticReport | `DiagnosticReport（`imagingStudy` 引用） |
| `Media` | `Media` 或 `DiagnosticReport.media` |
| `DocumentReference` | `DocumentReference` |
| 设备 | `Device`（模态 `Device.type`） |

**基数**：

| 关系 | 基数 | 说明 |
|------|------|------|
| `ServiceRequest` → `PacsWorkItem` | N : M | 经 `PacsWorkItemOrder`；合并检查时 N>1 : 1 |
| `PacsWorkItem` → `ImagingStudy` | 1 : 0..1 | 无影像仅有报告的场景（超声纸质） |
| `PacsWorkItem` → `DiagnosticReport | 1 : 0..1 | 一份主报告；分项见 SR |
| `ServiceRequest` → `DiagnosticReport | 1 : 0..1 | 按医嘱出报告索引 |

---

## 三、状态机（≈ JCZT）

```text
registered(0) ──登记确认──► scheduled(1) 待摄
                              │
                         开始检查
                              ▼
                         in-progress ──完成──► completed(2/4) 已检
                              │
                         作废/撤单
                              ▼
                         cancelled 已作废
```

报告子状态（`DiagnosticReport.status`）与检查态 **正交**：

```text
（无报告）→ draft → preliminary（已书写未审）→ final（已审核）→ amended（修改后）
```

审核字段映射：`SFSH`、`SHZT`、`SHR`、`SHSJ` → `DiagnosticReport 审核元数据 + `AuditEvent`。

---

## 四、配置表（pacs-domain，非 framework Setting）

| 表 | 用途 |
|----|------|
| **`pacs_config`** | 模块级参数（自动登记、PDF 路径等） |
| **`pacs_modality_capability`** | 设备可执行 `activity_id` / 部位/协议（**接单 INT 可执行性真源**，[01 §7.5](./01-概要设计.md#75-项目对照--一体化无-mapd3)） |
| **`pacs_viewer_endpoint`** | 阅片 URL（≈ `SYS_DS_URL`） |
| **`pacs_report_template`** | 报告模板 |
| **`pacs_device`** | 设备台账（≈ `SBID`） |

> **`pacs_item_map`**：**zxplat-domain 不建**（[01 §7.5](./01-概要设计.md#75-项目对照--一体化无-mapd3)）。外购/现场 PACS（如朝阳）在 **pacs-adapter** 内维护「`activity_id` ↔ 现场 EXESCODE」私有对照。

分组策略：**`PacsGroupingPolicy`** 读 `groupCode` + `pacs_modality_capability`；医生站签发时 **`PacsOrderIngressOrchestrator`**（support 类）执行，见 [AS-PACS-INT-001](../AppService/AS-PACS-INT-001-HIS接单入口.md)。

---

## 五、与 health 的集成点

| 集成 | 方向 | 说明 |
|------|------|------|
| 医生站签发 | health → pacs | `DoctorStationAdviceAppService` 直调 `PacsWorkItemDomainService`（同 LIS） |
| `DiagnosticReportIndex` | pacs → health | 报告 `final` 后 upsert **`cln_diagnostic_report_index`** |
| `ChargeItem` | health | 收费不回写 PACS；撤单由 health 触发 INT-02 |
| `Patient` / `Encounter` | health 只读 | **仅 ID 引用**；**禁止**在 `pacs_work_item` 冗余姓名/证件号（[01 §7.3](./01-概要设计.md#73-列表查询d8)） |

---

## 六、adapter 边界（不进 domain）

| 能力 | 遗留 | 落点 |
|------|------|------|
| DICOM C-STORE / Worklist | 工作站 | **imaging-bridge**（[zxplat-module-pacs-imaging](../../../模块/zxplat-module-pacs-imaging/)） |
| `WorkStationServer` JSON API | 同上 | pacs-adapter 调 application 命令 |
| 遗留 `/webservice/*` | HIS | pacs-adapter |
| Web 阅片组件 URL 拼装 | `DsUrl` + token | **imaging-adapter** + AS-PACS-IM-003 |

---

## 七、ZXPlat 表命名建议（Flyway）

```sql
-- 核心
pacs_work_item
pacs_work_item_order
pacs_imaging_study
pacs_diagnostic_report
pacs_media
pacs_document_reference

-- 配置与运营
pacs_config
pacs_modality_capability
pacs_viewer_endpoint
pacs_report_template
pacs_device
pacs_integration_log
pacs_external_exam
```

---

## 八、修订记录

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.0 | 2026-06-18 | 首版：PacsWorkItem、ImagingStudy、状态机、FHIR 映射 |
| v1.1 | 2026-06-18 | 移除会诊态字段（不做会诊） |
| v1.2 | 2026-06-18 | 移除医共体 adapter 引用 |
| v1.3 | 2026-06-18 | DICOM/阅片归 imaging 扩展 |
| v1.6 | 2026-06-27 | DEC 回归主文档；交叉引用改 01 §7 |
| v1.5 | 2026-06-27 | 方案 A：废止 health 侧 `pacs_item_map`；INT-07 改 modality_capability 校验 |
| v1.4 | 2026-06-27 | 无 PHI 冗余、DR index、Maven zxplat、朝阳 adapter Profile |
