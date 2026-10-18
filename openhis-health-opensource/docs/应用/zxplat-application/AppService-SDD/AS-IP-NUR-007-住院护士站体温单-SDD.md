# 住院护士站 · 体温单 · 应用服务详细设计（SDD）

> 编码：AS-IP-NUR-007-SDD  
> 版本：v1.2.0 | 日期：2026-06-20

## 1. 实现边界

### 1.1 目标与边界

| 项 | 内容 |
| --- | --- |
| 模块 | 住院护士站 · **体温单** Tab |
| 实现阶段口径 | **目标态全量契约**（IF-IP-TMP-01～04）；包 `inpatient/nursestation/temperaturesheet`；AppService **`NurseStationTemperatureSheetAppService`**；HTTP 前缀 **`nurseStationTemperatureSheet`** |
| 编排权威 | `AS-IP-NUR-007-住院护士站体温单.md` **v1.1.0** §1.3、§4 |
| 方法权威 | `DM-DIA-002` §6.1、§6.2 |
| 严格对齐 | IF **IF-IP-TMP-01～04** |
| 读侧 | **禁止** App 注入 Repository |

**Out of scope（AS §1.4）**：按时点删除；Tab 内患者分页；报表模板设计。

**上游**：`AS-IP-NUR-001`（患者点选、`NurseStationEncounterVisibilitysupport）。

### 1.2 术语口径

| 项 | 约定 |
| --- | --- |
| HTTP | 一律 **POST** |
| 事务 | App **禁止** `@Transactional`；写经 `VitalSignDomainService` |
| `type_enum` | `VitalSignTypeEnum.TEMPERATURE_SHEET.code` |
| 明细读写 | Request/Response 统一 `detailItems`（`TemperatureSheetDetailItemDto`）；皮试阳性/其他经 remark 投影 |
| 曲线 | `listTemperaturePointsByEncounterId`（主表 join 明细 `item_code=TEMP`） |

## 2. 代码结构

```text
zxplat/health-application/src/main/java/com/openhis/health/application/inpatient/nursestation/
  temperaturesheet/
    service/NurseStationTemperatureSheetAppService.java
    request/
      LoadNurseStationTemperatureSheetInitRequest.java
      GetNurseStationTemperatureSheetDataRequest.java
      SaveNurseStationTemperatureSheetAtRecordedTimeRequest.java
      GetNurseStationTemperatureSheetPrintDataRequest.java
    response/
      LoadNurseStationTemperatureSheetInitResponse.java
      GetNurseStationTemperatureSheetDataResponse.java
      SaveNurseStationTemperatureSheetAtRecordedTimeResponse.java
    dto/
      TemperatureSheetGridRowDto.java
      TemperatureSheetGridSlotCellDto.java
      TemperatureSheetGridAssemblyResult.java
      TemperatureSheetTemperaturePointDto.java
      TemperatureSheetDerivedMetaDto.java
      TemperatureSheetFillSlotDto.java
      TemperatureSheetDetailItemDto.java
      TemperatureSheetPaginationDto.java
      TemperatureSheetTimeSlotOptionDto.java
      TemperatureSheetWindowOptionDto.java
    assembler/TemperatureSheetGridAssembler.java
    mapper/TemperatureSheetMapper.java
    mapper/TemperatureSheetMapperImpl.java

zxplat/health-application/src/main/java/com/openhis/health/application/inpatient/nursestation/{feature}/support/
  TemperatureSheetRecordedAtSupport.java
  TemperatureSheetDetailItemSupport.java

zxplat/health-application/src/main/java/com/openhis/health/application/inpatient/nursestation/{feature}/support/
  NurseStationEncounterVisibilitySupport.java
```

### 2.1 依赖注入表

| 依赖 | 用途 |
| --- | --- |
| `VitalSignQueryService` | 曲线/主表/明细/存在性 |
| `VitalSignDomainService` | 写 |
| `EncounterQueryService` | 活动床（`listActiveEncounterLocationEntitiesByEncounterIds`） |
| `ProcedureQueryService` | 术后天数 |
| `BusinessSequenceService` | 新增 `busNo` |
| `CurrentOperatorResolver` / `CurrentPractitionerResolver` | IF-03 写路径操作人/记录人 |
| `NurseStationEncounterVisibilitysupport | Gate |

> IF-01 为静态配置，**不**注入 `CodeSystemQueryService`。

### 2.2 领域方法白名单

| 类型 | 方法 | 用于 IF |
| --- | --- | --- |
| `VitalSignQueryService` | `listTemperaturePointsByEncounterId` | 02、04 |
| `VitalSignQueryService` | `listHeadersByEncounterId` | 02、03 |
| `VitalSignQueryService` | `listDetailsByEncounterId` | 02 |
| `VitalSignQueryService` | `existsAtRecordedTime` | 03（定位 upsert） |
| `VitalSignDomainService` | `saveVitalSign` | 03 |

### 2.3 枚举、字典与值集一览

| 字段/概念 | DM 锚点 | 代码锚点 |
| --- | --- | --- |
| `type_enum` | DM §4.1 | `VitalSignTypeEnum.TEMPERATURE_SHEET` |
| 明细 code | DM §9.1 | `TemperatureSheetItemCodeConstants`（含 `TEMP`）；特殊事件 `TemperatureSheetSpecialEventCodeConstants` |
| 三测槽 | AS §1.3 | `TemperatureSheetRecordedAtSupport.TIME_SLOTS` |

**`detailItems` 结构（IF-02/03 统一）**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `termCode` | String | 术语体系 code（如 `vital_signs_code`、`special_event_code`） |
| `itemCode` | String | 字典项 code |
| `itemValue` | String | 字典项值（标记类特殊事件无附加值时 API 为 null） |
| `remark` | String | 明细备注（可空） |

**UI→明细映射（经 `detailItems` 提交）**

| `item_code`（`term_code` 缺省 `vital_signs_code`） | 说明 |
| --- | --- |
| `TEMP` / `PULSE` / `RESP` / `HR` / `SBP` / `DBP` | 主录入 |
| `WEIGHT` / `HEIGHT` / `INTAKE` / `URINE_VOL` / `STOOL` / `WAIST` / `URINE_FREQ` | 补充记录（锚定 22:00 槽） |
| `SKIN_TEST_POSITIVE` / `OTHER` | 写主表 `remark` 投影项（`TemperatureSheetDetailItemsupport） |
| `special_event_code` 下各 code | 特殊事件（标记类用占位值 `FLAG_VALUE` 落库） |

## 3. 按接口 ID 命名展开

### 3.1 应用服务接口（方法白名单）

| IF | 应用服务方法 | HTTP |
| --- | --- | --- |
| IF-IP-TMP-01 | `loadNurseStationTemperatureSheetInit` | `POST /api/app/nurseStationTemperatureSheet/loadNurseStationTemperatureSheetInit` |
| IF-IP-TMP-02 | `getNurseStationTemperatureSheetData` | `POST /api/app/nurseStationTemperatureSheet/getNurseStationTemperatureSheetData` |
| IF-IP-TMP-03 | `saveNurseStationTemperatureSheetAtRecordedTime` | `POST /api/app/nurseStationTemperatureSheet/saveNurseStationTemperatureSheetAtRecordedTime` |
| IF-IP-TMP-04 | `getNurseStationTemperatureSheetPrintData` | `POST /api/app/nurseStationTemperatureSheet/getNurseStationTemperatureSheetPrintData` |

### 3.1.1 IF-IP-TMP-01 `loadNurseStationTemperatureSheetInit`

**Request**：无业务字段。

**Response**

| 字段 | 说明 |
| --- | --- |
| `timeSlotOptions` | 固定 6 槽 `02/06/10/14/18/22` |
| `windowOptions` | `3d`/`7d`/`14d` |
| `defaultWindowRange` | 默认 `7d` |

**编排序列**：纯静态，`TemperatureSheetMapper#buildInitResponse`（无领域读）。

### 3.2 IF-IP-TMP-02 `getNurseStationTemperatureSheetData`

**Request**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `encounterId` | Long | 是 | |
| `patientId` | Long | 是 | 与就诊一致 |
| `windowRange` | String | 是 | `3d`/`7d`/`14d` |
| `pageIndex` | Integer | 否 | 默认 0 |
| `focusRecordDate` | LocalDate | 否 | 录入抽屉回显日 |

**Response**

| 字段 | 说明 |
| --- | --- |
| `temperaturePoints` | `{vitalSignId, recordedAt, temperature}` |
| `gridRows` | 日×6 槽宽表；`slotCells[].detailItems` 为统一明细结构 |
| `pagination` | `{pageIndex, totalPages, dateFrom, dateTo}` |
| `derivedMeta` | `{hospitalizationDaysByDate, weekNoByDate, postoperativeDaysByDate}` |
| `fillSlots` | `focusRecordDate` 有值时：6 槽 `detailItems` 回显 |

**领域编排序列**

| 序号 | 阶段 | 调用 | 说明 |
| --- | --- | --- | --- |
| 1 | Gate | `NurseStationEncounterVisibilitySupport#assertVisible` | **BR-IP-TMP-001** |
| 2 | Pre | 由 `windowRange+pageIndex` 算 `dateFrom/dateTo` | |
| 3 | Qry | `VitalSignQueryService#listTemperaturePointsByEncounterId` | type=TEMPERATURE_SHEET |
| 4 | Qry | `VitalSignQueryService#listHeadersByEncounterId` | 同时段 |
| 5 | Qry | `VitalSignQueryService#listDetailsByEncounterId` | 同时段 |
| 6 | Qry | `EncounterQueryService` + Procedure 读侧 | 派生 meta |
| 7 | Asm | `TemperatureSheetGridAssembler#assemble` | 返回 `TemperatureSheetGridAssemblyResult`（gridRows + derivedMeta） |
| 8 | Asm | `TemperatureSheetGridAssembler#assembleFillSlots` | 有 `focusRecordDate` 时；remark 投影项拆回 `detailItems` |

### 3.3 IF-IP-TMP-03 `saveNurseStationTemperatureSheetAtRecordedTime`

**Request**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `encounterId` | Long | 是 | |
| `patientId` | Long | 是 | |
| `recordDate` | LocalDate | 是 | |
| `timeSlot` | String | 是 | `02`…`22` |
| `vitalSignId` | Long | 否 | 更新 |
| `detailItems` | List\<TemperatureSheetDetailItemDto\> | 否 | 体征/特殊事件/补充项统一结构；至少一项非空（**BR-IP-TMP-004**） |

**Response**：`vitalSignId`、`busNo`、`nextTimeSlot`（前端聚焦下一槽）

**领域编排序列**

| 序号 | 阶段 | 调用 | 说明 |
| --- | --- | --- | --- |
| 1 | Gate | 可见性 + 当前用户 Practitioner | **BR-IP-TMP-001、010** |
| 2 | Pre | `TemperatureSheetRecordedAtSupport#toRecordedAt` | **BR-IP-TMP-003** |
| 3 | Pre | `TemperatureSheetDetailItemSupport#hasAnyDetailContent` | **BR-IP-TMP-004** |
| 4 | Pre | `vitalSignId` 空则 `existsAtRecordedTime` + `listHeadersByEncounterId` 取首条 id | **BR-IP-TMP-005** |
| 5 | Pre | 活动床、新单 `busNo` | **BR-IP-TMP-006** |
| 6 | Pre | `TemperatureSheetMapper#toSaveCommand` | `detailItems` → Command + remark 投影 |
| 7 | Cmd | `VitalSignDomainService#saveVitalSign` | **写库点** |
| 8 | Asm | Response | |

**异常映射**

| 领域/场景 | 应用码 |
| --- | --- |
| 未登录 | EX-IP-TMP-101 |
| `VITAL_SIGN_INVALID_ARGUMENT` | EX-IP-TMP-201 / 202 |
| 术语无效 | EX-IP-TMP-203 |
| 其他写失败 | EX-IP-TMP-204 |

常量：`InpatientNurseStationConstants.EX_IP_TMP_*`（与 AS §5 对齐）。

### 3.4 IF-IP-TMP-04 `getNurseStationTemperatureSheetPrintData`

**Request**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `encounterId` | Long | 是 | |
| `patientId` | Long | 是 | 与就诊一致 |
| `windowRange` | String | 是 | `3d`/`7d`/`14d`（校验 + 当前页打印时使用） |
| `pageIndex` | Integer | 否 | `printScope=CURRENT_PAGE` 时使用，默认 0 |
| `printScope` | String | 是 | `CURRENT_PAGE` / `ALL`（`InpatientNurseStationConstants.TEMPERATURE_SHEET_PRINT_SCOPE_*`） |

**Response**：与 IF-IP-TMP-02 相同（`GetNurseStationTemperatureSheetDataResponse`）。

**编排序列**：同 IF-02 Gate → 读路径；`printScope=ALL` 时 `dateFrom`=入科日、`dateTo`=今日、单页返回。

## 4. QueryService 与领域事件

| 事件 | 发布点 | 读侧刷新 |
| --- | --- | --- |
| `VitalSignSavedEvent` | `saveVitalSign` | 重调 IF-IP-TMP-02 |

## 5. 写库顺序（IF-IP-TMP-03）

1. `dia_vital_sign` upsert  
2. `dia_vital_sign_detail` 覆盖 + 软删未提交 code（**BR-VS-005**）

## 6. 非功能性需求实现

| 类别 | 承诺 |
| --- | --- |
| 性能 | IF-02：`listDetailsByEncounterId` 一次批量，禁止 N+1 |
| 幂等 | 同 `vitalSignId` 覆盖写 |
| 登录 | 写路径须 `CurrentPractitionerResolver`（等价 SDD 原 SecurityUtils 表述） |

## 7. 测试要点

| 用例 | 预期 |
| --- | --- |
| 6 槽逐次保存 | 网格对应槽有值 |
| 22:00+补充 | 补充项在 `detailItems` 中随 22:00 槽提交 |
| 非 22 槽带补充 | 前端不提交补充 code；后端按槽覆盖写 |
| 覆盖 | 同槽二次保存更新 |
| 曲线 | 明细 `TEMP` 有值才出现 |

## 8. 变更历史

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v1.2.0 | 2026-06-20 | 对齐成码：IF-02/03 统一 `detailItems`；删 `dailySupplement` 与扁平 Request 字段；补 `TemperatureSheetDetailItemsupport（落点 `module.common.assembler.nursestation`） |
| v1.1.3 | 2026-06-20 | **落点对齐**：`TemperatureSheetRecordedAtsupport → `module.common.assembler.nursestation` |
| v1.1.2 | 2026-06-20 | **support 落点对齐**：`NurseStationEncounterVisibilitysupport → `module.querysupport.nursestation`；对齐 **AS v1.1.1** |（已迁至 application.inpatient.nursestation.{feature}.support）（已迁至 application.shared / feature support（见 DISSOLUTION.md））
| v1.1.1 | 2026-06-19 | 对齐成码：补 IF-01/04 章节、`assemble`/`TemperatureSheetGridAssemblyResult`、依赖注入与常量锚点 |
| v1.1.0 | 2026-06-19 | 对齐 AS v1.1.0；删 IF 删除；补字段映射与 22:00 补充 |
| v1.0.0 | 2026-06-19 | 初版 |
