# 住院护士站 · 护理记录 · 应用服务详细设计（SDD）

> 编码：AS-IP-NUR-014-SDD  
> 版本：v1.1.2 | 日期：2026-06-20

## 1. 实现边界

### 1.1 目标与边界

| 项 | 内容 |
| --- | --- |
| 模块 | 住院护士站 · **护理记录** Tab |
| 实现阶段口径 | **目标态全量契约**（IF-IP-NRC-01～06）；包 `inpatient/nursestation/nursingrecord`；AppService **`NurseStationNursingRecordAppService`**；HTTP 前缀 **`nurseStationNursingRecord`** |
| 编排权威 | `AS-IP-NUR-014-住院护士站护理记录.md` **v1.1.0** |
| 方法权威 | `DM-DIA-002` v2.1.0 §6.1、§6.2、§9.1.3 |
| 严格对齐 | IF **IF-IP-NRC-01～06** |

**Out of scope（AS §1.4）**：同步体温单；模板 CRUD；`reservedCol1/2`；Tab 内患者分页。

**上游**：`AS-IP-NUR-001`。

### 1.2 术语口径

| 项 | 约定 |
| --- | --- |
| HTTP | 一律 **POST** |
| 事务 | App **禁止** `@Transactional`；IF-IP-NRC-04 由领域层多次写或 App 编排单领域写事务（SDD 采用 **App 循环调用领域写，外层 `@AppService` 事务由框架统一**——与 **011** 一致，禁止 App 显式 `@Transactional`） |
| `type_enum` | `NURSING_RECORD` |
| skeleton | Request 带 `rowType=skeleton` 时 IF-04 **过滤** |

## 2. 代码结构

```text
zxplat/health-application/src/main/java/com/openhis/health/application/inpatient/nursestation/
  nursingrecord/
    service/NurseStationNursingRecordAppService.java
    request/
    response/
    dto/NursingRecordRowDto.java
    assembler/NursingRecordRowAssembler.java
    mapper/NursingRecordMapper.java
    mapper/NursingRecordMapperImpl.java

zxplat/zxplat-domain/src/main/java/com/openhis/health/domain/share/utils/
  BloodPressureParser.java

zxplat/health-application/.../application/inpatient/nursestation/{feature}/support/
  NurseStationEncounterVisibilitySupport.java
```

**领域共享常量**：`zxplat-domain` → `NursingRecordItemCodeConstants`（UI 字段 ↔ `vital_signs_code`）；`InpatientNurseStationConstants` → `EX_IP_NRC_*`、视图/打印/行类型码。

### 2.1 依赖注入表

| 依赖 | 用途 |
| --- | --- |
| `VitalSignQueryService` | 列表/明细/保存回读/删除前校验 |
| `VitalSignDomainService` | 写/删 |
| `EncounterQueryService` | 活动床 locationId |
| `PractitionerQueryService` | 签名展示 |
| `TerminologyOptionLoader` | IF-01（委托 `CodeSystemQueryService`，B.6.1） |
| `BusinessSequenceService` | 新单 `busNo` |
| `NurseStationEncounterVisibilitysupport | Gate（`application.inpatient.nursestation.{feature}.support`） |
| `CurrentOperatorResolver` | 写路径 operatorId |
| `CurrentPractitionerResolver` | 写路径 recorderId（BR-IP-NRC-007） |
| `NursingRecordMapper` / `NursingRecordRowAssembler` | 映射与 pivot |
| `BloodPressureParser`（`domain.share.utils`）/ 护理记录行过滤（AppService 内联） | 血压拆分、批量行过滤 |

### 2.2 领域方法白名单

| 类型 | 方法 | 用于 IF |
| --- | --- | --- |
| `VitalSignQueryService` | `listHeadersByEncounterId` | 02、06 |
| `VitalSignQueryService` | `listDetailsByEncounterId` | 02（批量 pivot，优先于 N×getWithDetailsById） |
| `VitalSignQueryService` | `getWithDetailsById` | 03 Asm 回读、05 Pre |
| `VitalSignDomainService` | `saveVitalSign` | 03、04 |
| `VitalSignDomainService` | `deleteVitalSignById` | 05 |

### 2.3 枚举、字典与值集

| UI 字段 | 落点 | `term_code` / `item_code` |
| --- | --- | --- |
| `temperature` | 明细 | `vital_signs_code` / `TEMP` |
| `conditionMeasure` | 主表 `remark` | — |
| `nurseSignature` | 主表 `recorderId` | — |
| `pulse` | 明细 | `vital_signs_code` / `PULSE` |
| `respiratory` | 明细 | `vital_signs_code` / `RESP` |
| `heartRate` | 明细 | `vital_signs_code` / `HR` |
| `oxygenSaturation` | 明细 | `vital_signs_code` / `SPO2` |
| `consciousness` | 明细 | `vital_signs_code` / `CONSCIOUSNESS` |
| `pupilLeft` / `pupilRight` | 明细 | `vital_signs_code` / `PUPIL_L` / `PUPIL_R` |
| `oxygenMode` / `oxygenRate` / `oxygenFlow` | 明细 | `vital_signs_code` / `OXYGEN_MODE` / `OXYGEN_RATE` / `OXYGEN_FLOW` |
| `intakeName` / `intakeAmount` / `intakeRoute` | 明细 | `vital_signs_code` / `INTAKE_NAME` / 值存 `INTAKE` 或独立扩展 / `INTAKE_ROUTE` |
| `outputName` / `outputAmount` / `outputColorCharacter` | 明细 | `vital_signs_code` / `OUTPUT_NAME` / `OUTPUT` / `OUTPUT_CHARACTER` |
| `skinStatus` | 明细 | `vital_signs_code` / `SKIN_STATUS` |
| `tubeCare` | 明细 | `vital_signs_code` / `TUBE_CARE` |
| `bloodPressure` | 明细 | 复合血压字符串经 **`BloodPressureParser`**（`domain.share.utils`）拆为 `SBP`+`DBP`；无法拆分写 `BP` |

> 常量类：`NursingRecordItemCodeConstants`（`zxplat-domain`），与 **DM-DIA-002 §9.1.3** 对齐。

## 3. 按接口 ID 命名展开

### 3.1 应用服务接口

| IF | 方法 | HTTP |
| --- | --- | --- |
| IF-IP-NRC-01 | `loadNurseStationNursingRecordInit` | `POST /api/app/nurseStationNursingRecord/loadNurseStationNursingRecordInit` |
| IF-IP-NRC-02 | `listNurseStationNursingRecords` | `POST /api/app/nurseStationNursingRecord/listNurseStationNursingRecords` |
| IF-IP-NRC-03 | `saveNurseStationNursingRecord` | `POST /api/app/nurseStationNursingRecord/saveNurseStationNursingRecord` |
| IF-IP-NRC-04 | `saveNurseStationNursingRecordsBatch` | `POST /api/app/nurseStationNursingRecord/saveNurseStationNursingRecordsBatch` |
| IF-IP-NRC-05 | `deleteNurseStationNursingRecord` | `POST /api/app/nurseStationNursingRecord/deleteNurseStationNursingRecord` |
| IF-IP-NRC-06 | `getNurseStationNursingRecordPrintData` | `POST /api/app/nurseStationNursingRecord/getNurseStationNursingRecordPrintData` |

### 3.1.1 IF-IP-NRC-01 `loadNurseStationNursingRecordInit`

**Response**：`viewModeOptions`、`vitalSignItemOptions`（`vital_signs_code` 护理子集）、`defaultWeekRangeDescription`

### 3.2 IF-IP-NRC-02 `listNurseStationNursingRecords`

**Request**

| 字段 | 类型 | 必填 |
| --- | --- | --- |
| `encounterId` | Long | 是 |
| `patientId` | Long | 是（Gate，BR-IP-NRC-001） |
| `recordedAtFrom` | LocalDate | 否 |
| `recordedAtTo` | LocalDate | 否 |
| `sortAsc` | Boolean | 否 |

**Response**：`rows: List<NursingRecordRowDto>`、`paperHeader: NursingRecordPaperHeaderDto`

**编排序列**

| 序号 | 阶段 | 调用 |
| --- | --- | --- |
| 1 | Gate | 可见性 |
| 2 | Qry | `listHeadersByEncounterId`（type=NURSING_RECORD, range） |
| 3 | Qry | `listDetailsByEncounterId`（同 filter） |
| 4 | Asm | `NursingRecordRowAssembler#pivot`（血压合并为 `bloodPressure`） |

### 3.3 IF-IP-NRC-03 `saveNurseStationNursingRecord`

**Request**：对齐 UI `NursingRecordRow` + `vitalSignId?`、`encounterId`、`patientId`

**编排序列**

| 序号 | 阶段 | 调用 |
| --- | --- | --- |
| 1 | Gate | 可见性 |
| 2 | Pre | `BloodPressureParser#parse` |
| 3 | Pre | `NursingRecordMapper#toSaveCommand` |
| 4 | Pre | 内容校验 **BR-IP-NRC-003** |
| 5 | Cmd | `VitalSignDomainService#saveVitalSign` |
| 6 | Asm | `NursingRecordRowDto` |

### 3.4 IF-IP-NRC-04 `saveNurseStationNursingRecordsBatch`

**Request**

| 字段 | 类型 | 必填 |
| --- | --- | --- |
| `encounterId` | Long | 是 |
| `patientId` | Long | 是 |
| `records` | `List<SaveNursingRecordItemRequest>` | 是（含 `rowType`） |

**编排序列**

| 序号 | 阶段 | 调用 |
| --- | --- | --- |
| 1 | Gate | 可见性（批入口一次） |
| 2 | Pre | `NurseStationNursingRecordAppService#rejectSkeletonAndEmptyNursingRecordRows` |
| 3 | Loop | IF-IP-NRC-03 步骤 2～5（循环内不再 Gate） |
| 4 | Fail | 任一步失败 → **EX-IP-NRC-205**；异常消息含 **1-based `failedIndex`**（「第 N 行」） |

**Response**：`savedCount`、`rows`

### 3.5 IF-IP-NRC-05 `deleteNurseStationNursingRecord`

**Request**：`vitalSignId`、`encounterId`、`patientId`（Gate）

| 序号 | 阶段 | 调用 |
| --- | --- | --- |
| 1 | Gate | 可见性 |
| 2 | Pre | header.type=NURSING_RECORD 且 encounter 一致 |
| 3 | Cmd | `deleteVitalSignById` |

**异常**：`NOT_FOUND`→301；写失败→302。

常量：`InpatientNurseStationConstants.EX_IP_NRC_*`。

### 3.6 IF-IP-NRC-06 `getNurseStationNursingRecordPrintData`

**Request**：同 §3.2（`encounterId`、`patientId` 必填；`recordedAtFrom/To`、`sortAsc` 可选；默认 `sortAsc=true` 纸质升序）

**Response**：`templateCode=nursing_record`、`rows`、`paperHeader`

**编排序列**：同 IF-IP-NRC-02（Gate → Qry headers → Qry details → Asm pivot）

## 4. QueryService 与领域事件

| 事件 | 触发 | 刷新 |
| --- | --- | --- |
| `VitalSignSavedEvent` | 03、04 | IF-IP-NRC-02 |
| `VitalSignDeletedEvent` | 05 | IF-IP-NRC-02 |

## 5. 写库顺序

主表 upsert → 明细覆盖 + 软删（**BR-VS-005**）。批量 IF-04 多行顺序执行，框架事务边界内 fail-fast。

## 6. 非功能性需求实现

| 类别 | 承诺 |
| --- | --- |
| 列表 | headers + `listDetailsByEncounterId` 两次查询 |
| 批量 | 原子；失败带行号 |
| Long ID | Request/Response 主键 **String** 或 Long 不转 Number（前端 Long 规范） |

## 7. 测试要点

| 用例 | 预期 |
| --- | --- |
| 仅 remark | 保存成功 |
| 血压 128/76 | SBP=128, DBP=76 |
| batch + skeleton | skeleton 被过滤 |
| 删 extra | 逻辑删成功 |

## 8. 变更历史

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v1.1.3 | 2026-06-20 | **落点对齐**：`BloodPressureParser` → `domain.share.utils`；`NursingRecordSaveFilter` 内联 `NurseStationNursingRecordAppService` |
| v1.1.2 | 2026-06-20 | **support 落点对齐**：`NurseStationEncounterVisibilitysupport → `module.querysupport.nursestation`；对齐 **AS v1.1.2** |（已迁至 application.inpatient.nursestation.{feature}.support）（已迁至 application.shared / feature support（见 DISSOLUTION.md））
| v1.1.1 | 2026-06-19 | 对齐成码：§2.1 依赖、§3 契约补全（patientId、IF-01/04/06、MapperImpl、领域常量） |
| v1.1.0 | 2026-06-19 | 对齐 AS v1.1.0；完整 UI 字段映射；listDetails 批量读 |
| v1.0.0 | 2026-06-19 | 初版 |
