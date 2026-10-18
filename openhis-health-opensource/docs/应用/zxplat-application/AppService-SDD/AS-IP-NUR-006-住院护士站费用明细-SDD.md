# 住院护士站 · 费用明细 · 应用服务详细设计（SDD）

> 编码：AS-IP-NUR-006-SDD  
> 版本：v1.16 | 日期：2026-10-17 | 作者：（待填）

## 1. 实现边界

### 1.1 目标与边界

| 项 | 内容 |
| --- | --- |
| 模块 | 住院护士站工作台 · **费用明细** Tab（明细 / 汇总 + 合计区） |
| 实现阶段口径 | **目标态全量契约**（IF-IP-CDT-01～02）；**领域读查询**（`ChargeItemQueryService#listInpatientNurseStationCostDetailChargeRows`、`ProcedureQueryService#mapNetCompletedOccurrenceTimeByEncounterId`）**已落地**；**应用层**目标落包 `inpatient/nursestation/costdetail`，AppService **`NurseStationCostDetailAppService`**，HTTP 前缀 **`nurseStationCostDetail`** |
| 编排权威 | `AS-IP-NUR-006-住院护士站费用明细.md`（**v1.18**）§1.3、§1.6、§4 |
| 方法权威 | `DM-ADM-011` §6.2.15；`DM-CLN-004` §6.2.6、§6.2.8～6.2.9；`DM-MED-001 / DM-WKF-001 / DM-ADM-008 §6.2 目录 QueryService` §2、§3 |
| 严格对齐 | IF 编号 **IF-IP-CDT-01、IF-IP-CDT-02**，不得增删改号 |
| 读侧方案 | **`adm_charge_item` 业务表** + **`ProductDefinitionBatchLoader#loadAsMap`**；**禁止** `InpatientChargeDetail QueryService（已删除）` / QueryService Repository |

**Out of scope（重复 AS §1.4）**

| 类别 | 内容 |
| --- | --- |
| 左侧患者树/列表/详情 | **AS-IP-NUR-001** |
| 划价 / 执行 / 结算写路径 | **AS-IP-NUR-005** / **004** / **FIN** |
| 多患者合并、导出、医生站/收费窗嵌入 | AS §1.4 非目标 |
| 费用清单/明细单打印 | 前端复用 IF-02（**BR-IP-CDT-021**）；非独立 HTTP |
| `InpatientChargeDetail` QueryService | 刻意不用 |

**上游依赖**

| 文档 | 关系 |
| --- | --- |
| `AS-IP-NUR-001` | 可见范围 **BR-IP-NUR-003**；单选 `encounterId` |
| `AS-IP-NUR-005` | 划价入账后刷新本 Tab |
| `DM-MED-001 / DM-WKF-001 / DM-ADM-008 §6.2 目录 QueryService` | `loadAsMap`、包装/拆零比、`category_code` |

### 1.2 术语口径

| 项 | 约定 |
| --- | --- |
| 状态 / 枚举 code | 持久化与比较一律 **String `code`**（`ChargeItemStatus`、`GenerateSource` 等） |
| 业务时间（客户可见） | 进窗 / 排序 / 列同源：执行/净剩划价诊疗 = 净剩 `Procedure.recordedTime`；划价诊疗冲销留痕 = 原 COMPLETED.`recordedTime`（`listByIds` 回查，**BR-IP-CDT-016** 例外）；划价耗材 = 发放展示；滚费 = `ChargeItem.occurrenceTime` |
| 展示时间（页面列） | 与业务时间同源（DTO `businessDateTime`/`displayDateTime` 同值）；**occurrence 不对用户** |
| 展示 vs 统计 | 行级 `unitPrice`/`totalPrice` **原样**；汇总行 `lineTotalAmount` 与合计区为 **`total_price` 净额** |
| `presentationMode` | `DETAIL` / `SUMMARY`（**InpatientNurseStationConstants**，目标态待生成） |
| 费用大类块 | `MEDICATION` → `SERVICE` → `DEVICE`（Assembler 映射 `service_table`：`med_medication_request`/`med_medication_dispense`、`wkf_service_request`、`wkf_device_request`/`wkf_device_dispense`） |
| HTTP | 一律 **`POST`**；Bean **`NurseStationCostDetailAppService`** → 前缀 **`nurseStationCostDetail`** |
| 事务 | 本模块**只读**，无 `@Transactional` 写事务 |
| 金额换算 | 汇总数量链 **6 位 HALF_UP**；复用 **`ProductDefinitionCatalogUnitBandsAssembler`** 包装锚（**BR-IP-CDT-011**） |
| 汇总展开 | `SUMMARY` 时每条汇总行携带 `activityLines`（组内执行/划价明细，**BR-IP-CDT-018**）；前端展开子表展示 |

## 2. 代码结构

```text
zxplat/health-application/src/main/java/com/openhis/health/application/inpatient/nursestation/
  costdetail/
    service/NurseStationCostDetailAppService.java
    request/
      LoadNurseStationCostDetailPageInitRequest.java
      QueryNurseStationInpatientCostDetailRequest.java
    response/
      LoadNurseStationCostDetailPageInitResponse.java
      QueryNurseStationInpatientCostDetailResponse.java
    dto/
      NurseStationCostDetailLineDto.java
      NurseStationCostSummaryLineDto.java
      NurseStationCostSummaryActivityLineDto.java
      NurseStationCostDetailTotalsDto.java
      NurseStationCostCategoryTotalDto.java
      NurseStationCostDetailPresentationOptionDto.java
    assembler/
      NurseStationCostDetailAssembler.java
      NurseStationCostDetailQuantityAssembler.java
    mapper/
      NurseStationCostDetailMapper.java
      NurseStationCostDetailMapperImpl.java

zxplat/zxplat-domain/src/main/java/com/openhis/health/domain/administration/chargeitem/
  service/ChargeItemQueryService.java
  query/ChargeItemQueryCommand.java
  query/InpatientNurseStationCostDetailChargeRow.java

zxplat/health-application/src/main/java/com/openhis/health/application/
  encounter/EncounterContextReader.java
  nursestation/
    InpatientNurseStationCostDetailLoader.java
    NurseStationCostDetailReadAssembler.java
    dto/...
  product/ProductDefinitionBatchLoader.java

zxplat/zxplat-domain/src/main/java/com/openhis/health/domain/clinical/procedure/
  service/ProcedureQueryService.java
```

> 包名：`com.zxplat.domain.application.inpatient.nursestation.costdetail`  
> AppService Bean 名 → HTTP 前缀：`nurseStationCostDetail`

**前端打印（BR-IP-CDT-021，非本包 Java）**：`zxplat-ui/.../print-center/build-print-source.ts` 的 `buildFeeListPrintSource` / `buildFeeDetailPrintSource`，及 `load-print-pending.ts` 费用待打——与页同参调 IF-02；单头 `totalAmountText`←`grandTotal`，`prepaidAmountText`←`prepayNetBalance`，`balanceAmountText`←二者之差。

### 2.1 依赖注入表

| 依赖 | 用途 |
| --- | --- |
| `CurrentManagedWardLocationIdsResolver` | `listManagedWardLocationIdsOrEmpty`（**BR-IP-NUR-003**；**B.6.5**） |
| `EncounterQueryService` | 薄就诊查询（病区可见性门控辅助） |
| `EncounterContextReader` | IF-02：`listInpatientEncounterContexts` 可见 encounter 收窄 |
| `InpatientNurseStationCostDetailLoader` | IF-02：`listInpatientNurseStationCostDetailChargeRows`（净剩 Procedure 时间；划价诊疗冲销留痕按原 COMPLETED.`recordedTime` 进窗；固定 `excludeSupersededSettledLines=true`，**BR-IP-CDT-016/017/018**） |
| `ChargeItemQueryService` | 组套父行 `loadChargeItemsByIds` |
| `ProductDefinitionBatchLoader` | **`loadAsMap`**、复合键（`application.shared.product`） |
| `NurseStationCostDetailMapper` | Request ↔ Command/DTO |
| `NurseStationCostDetailAssembler` | 明细/汇总行、合计、组套父项解析 |
| `NurseStationCostDetailQuantityAssembler` | 汇总包装/拆零数量（**BR-IP-CDT-011**） |
| `PractitionerQueryService` | 汇总展开明细操作人批量 `findAllByIds`（**BR-IP-CDT-018**） |

> **不注入** `InpatientChargeDetail QueryService（已删除）`。  
> **`EnumUtil`**：状态/来源展示文案（B.10.8.1）。

### 2.2 领域方法白名单

| 类型 | 方法 | 用于 IF |
| --- | --- | --- |
| `CurrentManagedWardLocationIdsResolver` | `listManagedWardLocationIdsOrEmpty` | **IF-02** Gate |
| `EncounterContextReader` | `listInpatientEncounterContexts` | **IF-02** Gate |
| `InpatientNurseStationCostDetailLoader` | `listInpatientNurseStationCostDetailChargeRows` | **IF-02** Pre |
| `ChargeItemQueryService` | `loadChargeItemsByIds` | **IF-02** Pre（组套 `parent_id` 父行） |
| `ProductDefinitionBatchLoader` | `loadAsMap` | **IF-02** Pre |
| `ProductDefinitionBatchLoader` | `buildProductCompositeKey` | **IF-02** Asm |
| `ProcedureQueryService` | `mapNetCompletedOccurrenceTimeByEncounterId` | **IF-02**（域内已由 §6.2.15 调用；App **不**重复调用） |
| `ProcedureQueryService` | `listByIds` | —（域 §6.2.15 已覆盖；白名单保留供扩展/单测） |

### 2.3 枚举、字典与值集（DM 与代码可追溯）

| 类别 | 代码实现锚点 | DM 追溯锚点 | 说明 |
| --- | --- | --- | --- |
| 费用状态 | `com.zxplat.domain.share.enums.ChargeItemStatus` | `DM-ADM-011` §5 状态机 | IF-02 白名单四态 + 展示 |
| 生成来源 | `com.zxplat.domain.share.enums.GenerateSource` | `DM-ADM-011` §3 `generate_source_enum` | 明细来源列 |
| 执行状态（净剩） | `com.zxplat.domain.share.enums.EventStatus` | `DM-CLN-004` §5；**BR-IP-CDT-015** | `COMPLETED` / `CANCEL` 对冲 |
| 挂号锚点排除 | `com.zxplat.domain.share.constants.TableNameConstants.ADM_HEALTHCARE_SERVICE` | `AS-IP-NUR-006` **BR-IP-CDT-004** | 查询 `excludedServiceTable` |
| 产品复合键分隔 | `com.zxplat.domain.share.constants.GeneralSharedConstants.PRODUCT_DEFINITION_COMPOSITE_KEY_SEPARATOR` | QueryService `loadAsMap` 契约 | 表名+id |
| 展示模式 | `QueryNurseStationInpatientCostDetailRequest.presentationMode` | `AS-IP-NUR-006` §2 | `DETAIL`/`SUMMARY`；常量 **`InpatientNurseStationConstants.COST_DETAIL_PRESENTATION_MODE_*`**（目标态待生成） |
| 费用大类块码 | `NurseStationCostSummaryLineDto.expenseBlockCode` | `AS-IP-NUR-006` **BR-IP-CDT-008** | `MEDICATION`/`SERVICE`/`DEVICE` |
| 分类字典 | `ProductDefinitionDisplayDto.categoryCode` | QueryService §2 `category_code`；**BR-IP-CDT-009** | 缺失 → **EX-IP-CDT-203** |
| 业务消息码 | `InpatientNurseStationConstants.EX_IP_CDT_*` | `AS-IP-NUR-006` §5 | **101～203**（目标态待生成） |

### 2.4 领域层能力核对（非 Gap）

> **Gap 仅登记于 AS `AS-IP-NUR-006` §3**（**GAP-IP-CDT-001/002 已关闭**）；本 SDD **禁止**维护 Gap 表。

**已落地**：`ChargeItemQueryService#listInpatientNurseStationCostDetailChargeRows`；`ProcedureQueryService#mapNetCompletedOccurrenceTimeByEncounterId`；`ProcedureQueryService#listByIds`。

**本 SDD 成码交付**：`NurseStationCostDetailAppService` 两接口及 Request/Response/DTO/Mapper/Assembler；`InpatientNurseStationConstants` 增补 `EX_IP_CDT_*` 与 `COST_DETAIL_*`。

## 3. 按接口 ID 命名展开

先读完每个 IF 的「领域编排序列（严格顺序）」，按序号执行；Gate/Pre 失败不得进入 Asm。

### 3.1 应用服务接口（方法白名单）

| IF | 应用服务方法（英文） | 中文方法名 | HTTP |
| --- | --- | --- | --- |
| IF-IP-CDT-01 | `loadNurseStationCostDetailPageInit` | 费用明细页初始化 | `POST /api/app/nurseStationCostDetail/loadNurseStationCostDetailPageInit` |
| IF-IP-CDT-02 | `queryNurseStationInpatientCostDetail` | 查询住院费用明细 | `POST /api/app/nurseStationCostDetail/queryNurseStationInpatientCostDetail` |

### 3.1.1 编排约定（应用层如何实现 AS §4）

| 阶段 | 含义 | 失败时 |
| --- | --- | --- |
| **Gate** | 参数、`presentationMode`、日期区间、单就诊可见性 | 抛 **`EX-IP-CDT-*`**，不进入 Pre |
| **Pre** | 域查询 ChargeItem 候选行、父 ChargeItem、产品QueryService | 抛业务异常，**无 Cmd** |
| **Asm** | 明细/汇总/合计 DTO 装配 | — |

记法：`ChargeItemQueryService#listInpatientNurseStationCostDetailChargeRows` 表示注入 Bean 调用。

### 3.2 IF-IP-CDT-01 `loadNurseStationCostDetailPageInit`（费用明细页初始化）

**HTTP**：`POST /api/app/nurseStationCostDetail/loadNurseStationCostDetailPageInit`

**Request**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| （无业务字段） | — | — | 可继承空 Request 基类；服务端以 **`LocalDate.now()`** 计算默认日期 |

> 无 `encounterId` 亦可调用；仅下发默认配置。患者未选时由前端决定是否调用 IF-02。

**Response**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `dateFrom` | `LocalDate` | 默认**当日** |
| `dateTo` | `LocalDate` | 默认**当日**（与 `dateFrom` 相同） |
| `presentationOptions` | `List<NurseStationCostDetailPresentationOptionDto>` | `DETAIL`、`SUMMARY` |
| `expenseBlockOrder` | `List<String>` | 固定 `["MEDICATION","SERVICE","DEVICE"]` |

**DTO：`NurseStationCostDetailPresentationOptionDto`**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `code` | `String` | `DETAIL` / `SUMMARY` |
| `display` | `String` | 明细 / 汇总 |

**关键校验**

| 规则 | 说明 |
| --- | --- |
| **BR-IP-CDT-002** | 默认日期均为服务端当日 |

**失败码**

| 码 | 场景 |
| --- | --- |
| （无） | 纯配置下发，无业务失败 |

**Mapper 接口方法签名**

- `buildPageInitResponse(LocalDate today): LoadNurseStationCostDetailPageInitResponse`

**领域编排序列（严格顺序）**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Asm | `NurseStationCostDetailMapper#buildPageInitResponse` | `LocalDate.now()` | 写死 `dateFrom=dateTo=today`、presentation 选项、块顺序 |
| — | — | — | — | **仅读路径**；无 Gate/Pre/Cmd |

### 3.3 IF-IP-CDT-02 `queryNurseStationInpatientCostDetail`（查询住院费用明细）

**HTTP**：`POST /api/app/nurseStationCostDetail/queryNurseStationInpatientCostDetail`

**Request**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `encounterId` | `Long` | Y | 当前选中就诊（**BR-IP-CDT-014**） |
| `patientId` | `Long` | Y | 与 **AS-IP-NUR-001** 卡片一致，用于可见性交叉校验 |
| `dateFrom` | `LocalDate` | Y | 业务日期下界（闭区间） |
| `dateTo` | `LocalDate` | Y | 业务日期上界；须 `dateFrom ≤ dateTo` |
| `presentationMode` | `String` | Y | `DETAIL` 或 `SUMMARY` |
| `statusEnum` | `String` | Y | `PENDING_SETTLEMENT` 或 `SETTLED` |
| `keyword` | `String` | N | 项目关键字：展示产品名称 / 院内编码模糊（**BR-IP-CDT-019**）；空白不过滤 |
| `excludeOffsetPairs` | `Boolean` | N | 可选：成对剔除冲销留痕。页面 Tab **不传**；护士站费用清单/明细单打印**禁止传**（**BR-IP-CDT-021**） |
| `printExpandPackageChildren` | `Boolean` | N | 可选：**仅检验**出子账单、隐藏父账单。页面 Tab **不传**；护士站费用清单/明细单打印**禁止传**（**BR-IP-CDT-021**）；其它单据（如国基收费小票）可按院点文档传 |

**Response**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `presentationMode` | `String` | 回显请求模式 |
| `detailLines` | `List<NurseStationCostDetailLineDto>` | `presentationMode=DETAIL` 时填充；否则空列表 |
| `summaryLines` | `List<NurseStationCostSummaryLineDto>` | `presentationMode=SUMMARY` 时填充；否则空列表 |
| `totals` | `NurseStationCostDetailTotalsDto` | **两种模式均返回**；净额口径 **BR-IP-CDT-010** |

**DTO：`NurseStationCostDetailLineDto`（明细行）**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `chargeItemId` | `Long` | `adm_charge_item.id` |
| `productId` | `Long` | 展示用产品 id（组套子项取**父项** `product_id`，**BR-IP-CDT-006**） |
| `productTableName` | `String` | `product_table`（父项） |
| `itemName` | `String` | QueryService `productName` |
| `itemSpec` | `String` | QueryService规格展示 |
| `quantityValue` | `BigDecimal` | 账单数量（原样） |
| `quantityUnit` | `String` | 账单单位 code |
| `quantityUnitDisplay` | `String` | 单位展示名 |
| `unitPrice` | `BigDecimal` | 账单单价（**原样**，**BR-IP-CDT-012**） |
| `totalPrice` | `BigDecimal` | 账单行合计（**原样**；退费负金额保留） |
| `businessDateTime` | `LocalDateTime` | 客户可见业务时间（recorded / 发放 / 滚费 occurrence；进窗/倒序） |
| `displayDateTime` | `LocalDateTime` | 与 businessDateTime 同源 |
| `statusEnum` | `String` | `ChargeItemStatus#getCode()` |
| `statusDisplay` | `String` | `EnumUtil` 展示 |
| `generateSourceEnum` | `String` | 来源 code |
| `generateSourceDisplay` | `String` | 来源展示；床位滚费覆盖「自动计费」；护士划价冲销行覆盖「护士冲销」（**BR-IP-CDT-018**） |
| `refundHighlight` | `Boolean` | `REFUNDING`/`REFUNDED` 为 true（**BR-IP-CDT-013**） |
| `serviceTable` | `String` | 费用大类映射源 |
| `expenseBlockCode` | `String` | `MEDICATION`/`SERVICE`/`DEVICE` |

**DTO：`NurseStationCostSummaryLineDto`（汇总行）**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `productId` | `Long` | 分组键（父项 product） |
| `productTableName` | `String` | 产品表名 |
| `itemName` | `String` | 产品名称 |
| `itemSpec` | `String` | 规格 |
| `categoryCode` | `String` | 分类 code |
| `categoryDisplay` | `String` | 分类展示名 |
| `unitPrice` | `BigDecimal` | 组内任一条 `unit_price`（**BR-IP-CDT-012**） |
| `lineTotalAmount` | `BigDecimal` | 组内 `total_price` **净额**合计 |
| `packageQuantity` | `Integer` | 满包装整数数量（**BR-IP-CDT-011**） |
| `splitUnitQuantity` | `BigDecimal` | 不足一包时小单位数量；满包可为 `null` 或 0 |
| `packageUnitCode` | `String` | 包装单位 code |
| `packageUnitDisplay` | `String` | 包装单位展示 |
| `splitUnitCode` | `String` | 拆零/小单位 code |
| `splitUnitDisplay` | `String` | 小单位展示 |
| `expenseBlockCode` | `String` | 块顺序：药→诊→材 |
| `expenseBlockDisplay` | `String` | 块展示名 |
| `activityLines` | `List<NurseStationCostSummaryActivityLineDto>` | 组内执行/划价明细（**BR-IP-CDT-018**）；按业务时间倒序 |

**DTO：`NurseStationCostSummaryActivityLineDto`（汇总展开明细行）**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `chargeItemId` | `Long` | 组内账单锚点 |
| `activityDateTime` | `LocalDateTime` | 客户可见业务时间（与 display 同源；组内倒序） |
| `displayDateTime` | `LocalDateTime` | 与 activityDateTime 同源 |
| `operatorName` | `String` | 操作人展示名（Procedure 主执行人 / DeviceDispense 发放·配药人） |
| `quantityValue` | `BigDecimal` | 账单数量（原样） |
| `quantityUnit` | `String` | 账单单位 code |
| `quantityUnitDisplay` | `String` | 单位展示名 |
| `totalPrice` | `BigDecimal` | 账单行合计（**原样**；退费/冲销负金额保留；与明细视图同口径） |
| `activityTypeEnum` | `String` | `PRICING` / `EXECUTE` / `AUTO_ROLL`（**InpatientNurseStationConstants**） |
| `activityTypeDisplay` | `String` | 划价 / 执行 / 自动计费 / **冲销** / 取消执行 / 取消自动计费 |
| `cancelHighlight` | `Boolean` | 取消类记录醒目标识（**BR-IP-CDT-018**） |

**DTO：`NurseStationCostDetailTotalsDto`**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `medicationTotal` | `BigDecimal` | 药品类 **净额** |
| `serviceTotal` | `BigDecimal` | 诊疗类 **净额** |
| `deviceTotal` | `BigDecimal` | 耗材类 **净额** |
| `categoryTotals` | `List<NurseStationCostCategoryTotalDto>` | 按 `category_code` 净额小计 |
| `grandTotal` | `BigDecimal` | 总合计（净额）；打印单头「费用总额」同源（**BR-IP-CDT-021**） |
| `prepayNetBalance` | `BigDecimal` | 登记个人现金户库内预交金合计；**不计入** `grandTotal`；打印单头「预缴金额」同源；预缴余额=`prepayNetBalance − grandTotal`（**BR-IP-CDT-021**） |

**DTO：`NurseStationCostCategoryTotalDto`**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `categoryCode` | `String` | 分类 code |
| `categoryDisplay` | `String` | 分类展示 |
| `amount` | `BigDecimal` | 该分类净额 |

**关键校验**

| 规则 | 说明 |
| --- | --- |
| **BR-IP-CDT-001** | `encounterId`+`patientId` 可见 |
| **BR-IP-CDT-002** | 日期合法 |
| **BR-IP-CDT-003/015/016** | 域查询内净 Procedure / 划价时间；Procedure 对冲孤儿行闭区间外跳过 |
| **BR-IP-CDT-004～005** | 状态 + 挂号排除 + 日期（域 §6.2.15） |
| **BR-IP-CDT-006** | 组套父项 product |
| **BR-IP-CDT-007/009** | `product_id`、`category_code` 非空 |
| **BR-IP-CDT-008** | 明细倒序；汇总块顺序 |
| **BR-IP-CDT-010～013** | 净额合计 vs 行展示 |
| **BR-IP-CDT-018** | 汇总 `activityLines`：组内倒序；划价 / 自动计费 / 执行类型分流；操作人/取消标红；护士划价负向行展示「冲销」；明细来源列冲销行「护士冲销」 |
| **BR-IP-CDT-019** | 可选 `keyword`：`productName` / `productBusNo` 忽略大小写包含，OR；组套按父项产品；过滤后装配行与合计 |
| **BR-IP-CDT-020** | 排除嘱托：展示产品 `category_code=52` 或名「嘱托」；明细/汇总/合计均不含 |
| **BR-IP-CDT-021** | 费用清单/明细单打印所见即所打：与页同参复用本 IF；禁止打印传 `excludeOffsetPairs`/`printExpandPackageChildren`；单头余额=`prepayNetBalance − grandTotal` |

**失败码**

| 码 | 场景 | 用户提示（与 AS 一致） |
| --- | --- | --- |
| **EX-IP-CDT-101** | 域查询/装配未捕获异常 | 费用明细加载失败，请稍后重试 |
| **EX-IP-CDT-102** | 就诊不可见 / patient 不匹配 | 当前患者不可用，请刷新左侧列表 |
| **EX-IP-CDT-201** | `presentationMode` 非法 | 查询参数错误，请刷新页面 |
| **EX-IP-CDT-202** | 日期未传或起止倒置 | 请选择有效的查询日期 |
| **EX-IP-CDT-203** | 缺 product/category/业务时间/未知 `service_table`；域 `IllegalArgumentException` | 费用数据不完整，请联系管理员核对收费项目配置 |

**Mapper 接口方法签名**

- `toChargeItemQueryCommand(QueryNurseStationInpatientCostDetailRequest request): ChargeItemQueryCommand`
- `toInpatientEncounterPageQuery(Long encounterId, List<Long> wardIds): InpatientEncounterPageQuery`（或等价，与 **005** 同构）
- `toProductBatchLookups(List<InpatientNurseStationCostDetailChargeRow> rows, Map<Long, ChargeItem> parentById): List<ProductDefinitionBatchLookup>`
- `toQueryResponse(String presentationMode, List<NurseStationCostDetailLineDto> detailLines, List<NurseStationCostSummaryLineDto> summaryLines, NurseStationCostDetailTotalsDto totals): QueryNurseStationInpatientCostDetailResponse`

**Assembler 方法签名**

- `buildDetailLines(List<InpatientNurseStationCostDetailChargeRow> rows, Map<String, ProductDefinitionDisplayDto> productByKey, Map<Long, ChargeItem> parentChargeById): List<NurseStationCostDetailLineDto>`
- `buildSummaryLines(List<InpatientNurseStationCostDetailChargeRow> rows, Map<String, ProductDefinitionDisplayDto> productByKey, Map<Long, ChargeItem> parentChargeById, Map<Long, String> practitionerNameById): List<NurseStationCostSummaryLineDto>`
- `filterChargeRowsByItemKeyword(List<InpatientNurseStationCostDetailChargeRow> rows, Map<String, ProductDefinitionDisplayDto> productByKey, Map<Long, ChargeItem> parentChargeById, String keyword): List<InpatientNurseStationCostDetailChargeRow>`（**BR-IP-CDT-019**）
- `filterOutEntrustChargeRows(List<InpatientNurseStationCostDetailChargeRow> rows, Map<String, ProductDefinitionDisplayDto> productByKey, Map<Long, ChargeItem> parentChargeById): List<InpatientNurseStationCostDetailChargeRow>`（**BR-IP-CDT-020**）
- `filterOutPackageChildChargeRows(...)`：页面默认隐藏全部 `parent_id` 子账单
- `filterChargeRowsForActivityDefinitionChildrenPrint(...)`：`printExpandPackageChildren=true` 时**仅检验**出子账单、隐藏对应父账单（须产品 Map；检查/治疗仍父项）
- `collectOperatorIdsForChargeRows(List<InpatientNurseStationCostDetailChargeRow> rows): Set<Long>`
- `buildTotals(List<InpatientNurseStationCostDetailChargeRow> rows, Map<String, ProductDefinitionDisplayDto> productByKey, Map<Long, ChargeItem> parentChargeById): NurseStationCostDetailTotalsDto`
- `resolveDisplayProductId(ChargeItem charge, Map<Long, ChargeItem> parentChargeById): Long`
- `resolveExpenseBlockCode(String serviceTable): String`
- `NurseStationCostDetailQuantityAssembler#aggregatePackageAndSplitQuantity(List<InpatientNurseStationCostDetailChargeRow> groupRows, ProductDefinitionDisplayDto product): PackageSplitQuantityResult`（内部类或 record）

**领域编排序列（严格顺序）**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | App 校验 | `encounterId`、`patientId`、`dateFrom`/`dateTo`、`presentationMode`、`statusEnum` | 缺参/日期倒置 → **EX-IP-CDT-202**；非法 mode/status → **EX-IP-CDT-201** |
| 2 | Gate | `CurrentManagedWardLocationIdsResolver#listManagedWardLocationIdsOrEmpty` | — | 管理病区 |
| 3 | Gate | `EncounterQueryService#listInpatientEncounterContexts` | ② wardIds + `encounterId` | 须命中且 `patientId` 一致；否则 **EX-IP-CDT-102**（同 **005** `requireVisibleSnapshot` 口径） |
| 4 | Pre | `NurseStationCostDetailMapper#toChargeItemQueryCommand` | Request | 填 `encounterId`、`businessDateFrom/To` |
| 5 | Pre | `ChargeItemQueryService#listInpatientNurseStationCostDetailChargeRows` | ④ Command | **BR-IP-CDT-004/005/015/016**；已倒序；闭区间内 `IllegalArgumentException` → **EX-IP-CDT-203** |
| 5a | Pre | 子账单过滤 | ⑤ 行 + `printExpandPackageChildren` | 默认 `filterOutPackageChildChargeRows`；打印展开时本步跳过（先留子行以便加载产品） |
| 6 | Pre | 收集 `parent_id` | ⑤ 行 | 非空 `parent_id` 去重 |
| 7 | Pre | `ChargeItemQueryService#loadChargeItemsByIds` | ⑥ ids | 组套父 ChargeItem（**BR-IP-CDT-006**） |
| 8 | Pre | `NurseStationCostDetailMapper#toProductBatchLookups` | ⑤⑦ + 父项 product | 含父项 `product_table`+`product_id` |
| 9 | Pre | `ProductDefinitionBatchLoader#loadAsMap` | ⑧ lookups | 批量 IN |
| 10 | Pre | Assembler 校验 | ⑨ Map | 每条展示 product 须存在且 `categoryCode` 非空；未知 `service_table` → **EX-IP-CDT-203** |
| 10y | Pre | 打印展开过滤 | ⑤⑨⑦ + `printExpandPackageChildren` | 为 true 时 `filterChargeRowsForActivityDefinitionChildrenPrint`：**仅检验**（父产品类目 22）出子账单 |
| 10z | Pre | `NurseStationCostDetailAssembler#filterOutEntrustChargeRows` | ⑤⑨⑦ | **BR-IP-CDT-020**：排除嘱托活动账单 |
| 10a | Pre | `NurseStationCostDetailAssembler#filterChargeRowsByItemKeyword` | 10z + `keyword` | **BR-IP-CDT-019**；空白不过滤；后续 Asm 均用过滤结果 |
| 11 | Asm | `NurseStationCostDetailAssembler#buildTotals` | 10a | **净额**合计（**BR-IP-CDT-010**）；**两种 presentationMode 均执行** |
| 12 | Asm | 若 `presentationMode=DETAIL`：`#buildDetailLines` | 10a | 行单价/行合计原样；`refundHighlight` |
| 13 | Asm | 若 `presentationMode=SUMMARY`：Pre `PractitionerQueryService#findAllByIds` + `#buildSummaryLines` | 10a + 操作人 Map + `NurseStationCostDetailQuantityAssembler` | 按父 `product_id` 分组；块顺序药→诊→材；**BR-IP-CDT-011/018** |
| 14 | Asm | `NurseStationCostDetailMapper#toQueryResponse` | 11～13 | 空列表允许；合计为 0 |
| — | — | — | — | **仅读路径**；**无 Cmd** |

> **互斥说明**：步骤 12 与 13 **互斥**——若 `presentationMode=DETAIL` 仅执行 12，跳过 13；若 `SUMMARY` 仅执行 13，跳过 12。步骤 11 **始终执行**。

## 4. QueryService 与事件刷新

| 项 | 约定 |
| --- | --- |
| 本 Tab | **不消费** `InpatientChargeDetail` 投影；直接读 **`adm_charge_item`** |
| 产品主数据 | **ProductDefinition** QueryService物化表；`loadAsMap` 批量加载 |
| 事件 | 划价/执行/结算事件更新 ChargeItem 后，用户**手动刷新**本 IF；**不要求**投影实时 |

```text
ChargeItem 写侧事件 → adm_charge_item（事实表）
ProductDefinition 投影 → med_medication_definition / adm_device_definition / wkf_activity_definition（主表）（QueryService）
本 Tab 查询时刻：listInpatientNurseStationCostDetailChargeRows + loadAsMap
```

## 5. 写库顺序（核心写接口视角）

本模块 **无写接口**。

| IF | App 方法 | Cmd | 说明 |
| --- | --- | --- | --- |
| IF-IP-CDT-01 | `loadNurseStationCostDetailPageInit` | — | 只读 |
| IF-IP-CDT-02 | `queryNurseStationInpatientCostDetail` | — | 只读 |

| 事务边界 | 说明 |
| --- | --- |
| 无事务 | 只读查询；域方法不开启写事务 |

| 失败回滚 | 不适用 |

## 6. 非功能性需求实现（收敛为承诺表）

### 6.1 幂等性

| 项 | 承诺 | 代码位置（必须） | 关联步骤 |
| --- | --- | --- | --- |
| 查询幂等 | 相同 Request 重复调用结果一致（以 DB 当前事实为准） | `NurseStationCostDetailAppService#queryNurseStationInpatientCostDetail` | §3.3 序号 4～14 |

### 6.2 并发控制

| 项 | 承诺 | 代码位置（必须） | 关联步骤 |
| --- | --- | --- | --- |
| 只读无锁 | 不引入乐观锁写 | 全类 | — |

### 6.3 重试与容错

| 项 | 承诺 | 代码位置（必须） | 关联步骤 |
| --- | --- | --- | --- |
| 域异常映射 | `IllegalArgumentException`（业务时间/product）→ **EX-IP-CDT-203** | `queryNurseStationInpatientCostDetail` catch | §3.3 序号 5、10 |
| 其它异常 | → **EX-IP-CDT-101** | 同上 | 全局 |

### 6.4 数据脱敏

| 项 | 承诺 | 代码位置（必须） | 关联步骤 |
| --- | --- | --- | --- |
| 无额外 PII | 仅返回费用与产品展示字段 | DTO 定义 | Asm |

### 6.5 审计日志

| 项 | 承诺 | 代码位置（必须） | 关联步骤 |
| --- | --- | --- | --- |
| 操作人 | 本模块只读；无写路径操作人 Gate | — | Gate |

### 6.6 性能

| 项 | 承诺 | 代码位置（必须） | 关联步骤 |
| --- | --- | --- | --- |
| 批量产品 | `loadAsMap` 按表名分组 IN | §3.3 序号 9 | **禁止** N+1 `getProductById` |
| 父行批量 | `loadChargeItemsByIds` 一次 | §3.3 序号 7 | — |
| 单就诊全量 | 日期区间内一次域查询 | §3.3 序号 5 | 与 AS §7 一致 |

## 7. 测试要点

### 7.1 功能测试

| 用例 | 预期 | 自动化归属 | 优先级 |
| --- | --- | --- | --- |
| 初始化默认当日 | `dateFrom=dateTo=today` | 集成 | P0 |
| 明细：待结算+已结算+退费态 | 均出现；退单 `refundHighlight=true` | 集成 | P0 |
| 明细：不含草稿/待收费/挂号费 | 过滤 | 集成 | P0 |
| 净 Procedure：医嘱执行已对冲 COMPLETED 不展示 | 对应 order-execute charge 不在结果 | 集成 | P0 |
| 划价诊疗冲销留痕：Procedure 已对冲仍成对可见 | nurse-pricing 正负 Charge 均按原 COMPLETED.`recorded_time` 进窗；来源列「护士划价」+「护士冲销」 | 集成 | P0 |
| 划价/执行行按客户可见 `recorded_time`（耗材发放）日过滤 | 改时后同日两条均可见 | 集成 | P0 |
| 汇总：同 product 净额 | `lineTotalAmount` 含退单冲减 | 集成 | P0 |
| 行单价不因净额为 0 改变 | 退光后仍展示原 `unitPrice` | 集成 | P1 |
| 合计两 Tab 一致 | DETAIL/SUMMARY 请求 `totals` 相同 | 集成 | P0 |
| 不可见 encounter | **EX-IP-CDT-102** | 集成 | P0 |

### 7.2 非功能测试

| 用例 | 预期 | 自动化归属 | 优先级 |
| --- | --- | --- | --- |
| 单就诊 500+ 行 | 响应可接受；无 N+1 产品查询 | 性能 | P1 |

### 7.3 QueryService一致性

| 用例 | 预期 | 自动化归属 | 优先级 |
| --- | --- | --- |
| 划价后刷新 | 新 `PENDING_SETTLEMENT` 行出现 | 手工/集成 | P1 |

### 7.4 集成测试

| 用例 | 预期 | 自动化归属 | 优先级 |
| --- | --- | --- | --- |
| HTTP POST 路由 | `POST /api/app/nurseStationCostDetail/queryNurseStationInpatientCostDetail` | 契约 | P0 |

### 7.5 测试缺口跟踪

| 缺口 | 说明 | 计划 |
| --- | --- | --- |
| 包装数量进位边界 | 多单位换算小数用例 | 单元测试 `NurseStationCostDetailQuantityAssembler` |

## 8. 变更历史

| 版本 | 日期 | 摘要 |
| --- | --- | --- |
| v1.16 | 2026-10-17 | **代码先行回写**：**BR-IP-CDT-021**；Request 两可选打印旗标对护士站清单/明细单打印禁止传；前端 `buildFeeListPrintSource`/`buildFeeDetailPrintSource` 与页同参、单头余额=`预交金−grandTotal`；对齐 **AS v1.18** |
| v1.15 | 2026-10-15 | **代码先行回写**：`NurseStationCostSummaryActivityLineDto.totalPrice`；Assembler 原样透出 `ChargeItem.total_price`；前端汇总展开子表「总价」列；对齐 **AS v1.17** |
| v1.14 | 2026-10-11 | **代码先行回写**：**BR-IP-CDT-016** 划价诊疗冲销留痕例外；Loader 按 `procedure_id` 回查原 COMPLETED.`recordedTime`；对齐 **AS v1.16** |
| v1.13 | 2026-10-06 | `printExpandPackageChildren` 收窄为**仅检验**展开；编排序列 10y 在产品加载后过滤；对齐 **AS v1.15** |
| v1.12 | 2026-10-06 | Request 可选 `printExpandPackageChildren` / `excludeOffsetPairs`；Assembler 打印展开过滤；对齐 **AS v1.14** |
| v1.11 | 2026-08-06 | 客户可见单轴：进窗/排序/列=`recorded`（耗材发放；滚费 occurrence）；对齐 **AS v1.13** / Loader |
| v1.10 | 2026-08-06 | §1.2/`displayDateTime`：护士划价诊疗=`recorded_time`、耗材=发放时刻；对齐 **AS v1.12** / **BR-IP-PRC-030** / `InpatientNurseStationCostDetailLoader` |
| v1.9 | 2026-07-20 | **BR-IP-CDT-018**：冲销活动类型「冲销」、明细来源「护士冲销」；常量 `COST_DETAIL_GENERATE_SOURCE_NURSE_OFFSET_DISPLAY`；对齐 **AS v1.11** |
| v1.8 | 2026-07-18 | **BR-IP-CDT-020**：`filterOutEntrustChargeRows`；编排序列 10z；对齐 **AS v1.10** / **BR-IP-EXE-033** |
| v1.7 | 2026-07-17 | **BR-IP-CDT-018**：`activityTypeEnum` 增补 `AUTO_ROLL`；床位滚费来源/操作展示「自动计费」 |
| v1.6 | 2026-07-17 | 明细/汇总 `displayDateTime` 双轨：过滤/倒序仍用 occurrence，页面列优先 `recorded_time`；对齐 **AS v1.8** |
| v1.5 | 2026-07-17 | **BR-IP-CDT-019**：Request `keyword`；`filterChargeRowsByItemKeyword`；明细/汇总/合计基于过滤结果；对齐 **AS v1.7** |
| v1.4 | 2026-07-16 | **BR-IP-CDT-018**：`NurseStationCostSummaryActivityLineDto` + 汇总行 `activityLines`；`PractitionerQueryService` 批量操作人；前端汇总表展开子表 |
| v1.3 | 2026-06-20 | **support 落点对齐**：`InpatientNurseStationCostDetailLoader`、`NurseStationCostDetailReadAssembler` → `module.querysupport.nursestation`；对齐 **AS v1.3** |（已迁至 application.inpatient.nursestation.{feature}.support）（已迁至 application.shared / feature support（见 DISSOLUTION.md））
| v1.2 | 2026-05-28 | **六维对账**：`NurseStationCostDetailAppService` 二方法与 IF-IP-CDT-01～02 对齐；只读无事务 |
| v* | 2026-05-28 | **ReadModel 弃用对齐**：§2.1/§2.2 增补 `module.querysupport（`EncounterContextFacade`、`*Loader`）；就诊上下文改 `*EncounterContextQueryModel` + Facade；医嘱列表骨架改 `InpatientOrderListSkeleton` + `InpatientOrderListMergeAssembler`/`InpatientOrderLookupLoader`；§3/§4 序列与 QueryService 口径与 `zxplat` 一致 |（已迁至 application.shared / feature support（见 DISSOLUTION.md））（已迁至 EncounterContextReader / application.shared.encounter）
| v1.1 | 2026-05-27 | **BR-IP-CDT-016** Procedure 对冲孤儿行闭区间外跳过；`resolveExpenseBlockCode` 增补 `wkf_device_dispense`→耗材；对齐域 §6.2.15 |
| v1.0 | 2026-05-24 | 首版：IF-IP-CDT-01～02；`nurseStationCostDetail` 包；净 Procedure 业务时间；不用 InpatientChargeDetail；对齐 AS v1.1 与域 §6.2.15 |
