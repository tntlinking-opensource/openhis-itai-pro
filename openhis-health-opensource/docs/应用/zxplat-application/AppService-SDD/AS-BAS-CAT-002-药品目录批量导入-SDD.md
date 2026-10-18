# AS-BAS-CAT-002 药品目录批量导入 · 应用服务详细设计（SDD）

> **编码**：AS-BAS-CAT-002-SDD · **版本**：v1.5 · **日期**：2026-10-07  
> **对应 AS**：`AS-BAS-CAT-002-药品目录批量导入.md`（**v1.4**）  
> **关联 DM**：`DM-MED-001` §6.1.6 / §6.2.4 / §6.2.5；`DM-ADM-010`（维护态定价）  
> **实现阶段口径**：**已成码**（xlsx 导入 + 按筛选导出单 Sheet；库内同键跳过可重导）

---

## 1. 实现边界

### 1.1 目标与边界

| 项 | 内容 |
| --- | --- |
| 模块 | 基础资料 · 药品目录（包 `application/basic/medicationcatalog`） |
| 边界 | 编排 **IF-BAS-CAT-IMP-01～03**、**IF-BAS-CAT-EXP-01**；xlsx 模板生成 / 解析校验 / 整批新建+维护态价 / 错误标注回传 / **按筛选导出** |
| 编排权威 | AS-BAS-CAT-002 v1.4 §0～§7 |
| 写路径复用 | **必须**与 `MedicationCatalogAppService#createMedicationCatalog` 共用「领域建档 + `replacePriceLines`」；禁止导入另写一套定价 |
| 在外 | 目录编辑门禁（AS-BAS-CAT-001）、启停废弃、医保对码、批号价、耗材/诊疗导入 |

### 1.2 与 AS 的实现裁决（强制）

| 裁决 ID | 内容 |
| --- | --- |
| **CAT-IMP-SDD-R-001** | HTTP 前缀 **`/medicationCatalog/`**；三 IF 均为 **POST + JSON**（`fileBytes`/`token` base64 或字符串）；**禁止**依赖 `MultipartFile`/`HttpServletResponse`（AppService 动态网关不注入） |
| **CAT-IMP-SDD-R-002** | 模板版本常量 **`MED_CATALOG_IMPORT` / `v1.7`（兼容 v1.6/v1.5/v1.4）**（兼容导入 v1.5/v1.4）；落 `MedicationCatalogImportConstants`（**`zxplat-domain/.../constants`**，禁止 application 自建 constants 包） |
| **CAT-IMP-SDD-R-003** | 单文件有效行上限 **`IMPORT_MAX_ROWS = 3000`**；导出上限 **`EXPORT_MAX_ROWS = 3000`**；数据验证下拉覆盖 Excel 行 **5～3004** |
| **CAT-IMP-SDD-R-004** | 导入事务：异步工作流写段 **`@Transactional(rollbackFor = Exception.class)`**；校验失败**不得**进入事务写段 |
| **CAT-IMP-SDD-R-005** | 成功行状态 **`PublicationStatus.ACTIVE`**；由现网 `createMedicationDefinition` 默认态保证，导入映射**禁止**设 DRAFT |
| **CAT-IMP-SDD-R-006** | 废弃领域 stub 路径：现有 CSV `parseImportRows` + 窄字段 `ImportMedicationCatalogCommand` **不再作为业务入口**；应用层按行组装 `CreateMedicationCatalogRequest` → 抽私有 `createOneForImport`（与 `createMedicationCatalog` 同体） |
| **CAT-IMP-SDD-R-007** | 批次事件：写段全部成功后发布 `MedicationCatalogImportedEvent(batchNo, successCount, 0, operatorId)`；校验失败**不**发该事件 |
| **CAT-IMP-SDD-R-008** | 院内旧编码与药品说明合并写入 `definition`：仅旧编码→`院内旧编码={value}`；仅说明→原文；两者都有→说明+换行+旧编码行 |
| **CAT-IMP-SDD-R-009** | 包装/库存/剂量单位与四倍数均由模板显式填写；**禁止**写路径静默默认包装=采购、库存=拆零 |
| **CAT-IMP-SDD-R-010** | 错误文件：内存或本地临时文件 + **token 缓存 TTL=30min**；单文件 ≤ **15MB**；过期或缺失 → 业务码 `MED_CAT_IMP_410` |
| **CAT-IMP-SDD-R-011** | 同键跳过、不更新。有院内旧编码：`L:`+旧编码。无旧编码或同时复核：`N:`+名称+厂家**主键**+归一化规格。文件内同键只保留首行（厂家段用文案）。库内：同 `orgId`、未删除、`statusEnum ≠ DISCARDED`。Validator 标 `skipExisting`，**不进 errors**。厂家文案经 `SupplierQueryService.findManufacturerIdsByExactNames` 对齐主键后再查 `findExistingDuplicateKeyTokens(orgId, tokens)` |
| **CAT-IMP-SDD-R-012** | 码表解析：中文 label 优先；其次精确匹配 code；同 label 多码取选项列表**稳定顺序第一条**并 `log.warn`（UX-01） |
| **CAT-IMP-SDD-R-013** | 技术栈：Apache POI `XSSFWorkbook`（与 `TabularExcelExporter` 同族）；**不**引入 EasyExcel 为本期依赖 |
| **CAT-IMP-SDD-R-014** | 金额：`purchasePrice`/`retailPrice`/`maximumRetailPrice` 经现网金额刻度契约入库（与单条创建一致）；Excel 数字/文本均可，非法格式记行错 |

---

## 2. 代码结构

### 2.1 包结构（成码目标）

```text
com/openhis/health/application/basic/medicationcatalog/
  service/MedicationCatalogAppService.java          # IMP-01/02/03、EXP-01 入口；事务；复用创建+定价
  support/
    MedicationCatalogImportTemplateBuilder.java     # 导入模板生成；导出单 Sheet（buildExport）
    MedicationCatalogExportRowAssembler.java        # 详情+维护态价 → 56 列中文
    MedicationCatalogImportWorkbookParser.java      # 解析 + 列指纹 Gate
    MedicationCatalogImportRowValidator.java        # 行/批校验；库内同键标 skipExisting
    MedicationCatalogImportWriteSupport.java        # 事务写段；skipExisting 跳过
    MedicationCatalogImportErrorFileService.java    # 标注文件生成 + token 存取
    MedicationCatalogImportTerminologyIndex.java    # 术语 label/code 双向索引（一次加载）
  request/   （IMP-02 / EXP-01 为 JSON body）
  dto/
    ImportMedicationCatalogResponse.java           # 成功：batchNo/totalCount/successCount=新建/failCount=0
    ExportMedicationCatalogResponse.java           # fileName/contentType/fileBytes/exportedRowCount/truncated
    ImportMedicationCatalogErrorItemDto.java        # excelRowNum/columnName/code/message
    ImportMedicationCatalogFailureDto.java          # message/errors/errorFileToken（失败体）
  mapper/MedicationCatalogMapper(.java|Impl)        # 既有；导入行 → Create 命令若需可扩

com/openhis/health/domain/share/constants/
  MedicationCatalogImportConstants.java             # 版本、列名、导入/导出上限、Sheet 名、错误码前缀

com/openhis/health/domain/medication/medicationdefinition/
  service/MedicationDefinitionQueryService.java     # +findExistingDuplicateKeyTokens；+listMedicationCatalogForExport
  # DomainService#importMedicationCatalog：可保留空壳或改为委托说明「应用层编排」，禁止再走窄 CSV 行
```

HTTP 接入：与现网药品目录其它接口同一 Controller/网关扫描方式（`@AppService` 或既有 Web 映射）；路径见 §3。

### 2.2 依赖注入表（导入相关）

| 依赖 | 用途 |
| --- | --- |
| `MedicationDefinitionDomainService` | `createMedicationDefinition` |
| `MedicationDefinitionQueryService` | 库内同键批量查询（skipExisting）；模板码表无需其写 |
| `MedicationCatalogImportWriteSupport` | 事务写段；跳过已存在；发布批次事件 |
| `ChargeItemDefinitionDomainService` | `removePricingByInstance` / `saveAllChargeItemDefinitions`（经 `replacePriceLines`） |
| `TerminologyOptionLoader` | 模板「③可选值」与导入映射索引（分类/单位/剂型等） |
| `FrequencyOptionLoader`（或现网等价 Bean） | 默认频次下拉与映射（同 `getMedicationInit`） |
| `ICurrentUser` / 会话组织 | `orgId` 默认（同 `applyDefaultOrganizationIdForCreate`）；**AppService 禁直注**若规范禁止——沿用现网目录服务既有用户解析方式 |
| `MedicationCatalogImportTemplateBuilder` | IMP-01 |
| `MedicationCatalogImportWorkbookParser` | IMP-02 解析 |
| `MedicationCatalogImportRowValidator` | IMP-02 校验 |
| `MedicationCatalogImportErrorFileService` | 失败标注 + IMP-03 |
| `DomainEventPublisher` | `MedicationCatalogImportedEvent`（若事件由 DomainService 发，则创建路径已覆盖 Created；批次事件在 AppService 成功末尾发一次） |

### 2.3 常量真源

`MedicationCatalogImportConstants`（建议字段）：

| 常量 | 值 |
| --- | --- |
| `TEMPLATE_CODE` | `MED_CATALOG_IMPORT` |
| `TEMPLATE_VERSION` | `v1.2` |
| `SHEET_GUIDE` | `①从这里开始` |
| `SHEET_DATA` | `②药品目录` |
| `SHEET_OPTIONS` | `③可选值一览` |
| `SHEET_SYS` | `_sys` |
| `HEADER_ROW_INDEX` | `1`（0-based：第 2 行字段表头） |
| `DATA_START_ROW_INDEX` | `4`（0-based：第 5 行） |
| `IMPORT_MAX_ROWS` | `3000` |
| `VALIDATION_END_ROW` | `3004`（1-based Excel 行号） |
| `EXAMPLE_NAME_PREFIX` | `[示例]` |
| `ERROR_COLUMN_TITLE` | `错误说明` |
| `LEGACY_CODE_DEFINITION_PREFIX` | `院内旧编码=` |
| `ERROR_FILE_TTL_MINUTES` | `30` |
| `ERROR_FILE_MAX_BYTES` | `15 * 1024 * 1024` |
| `COLUMN_FINGERPRINT` | 见 §4.3（生成时写入 `_sys!B3`） |

列中文名（去 `*` 后）有序数组亦放此常量类，供指纹与解析共用。

---

## 3. 应用层接口契约

### 3.1 IF-BAS-CAT-IMP-01 下载模板

| 项 | 约定 |
| --- | --- |
| AppService | `byte[] downloadImportTemplate()` 或 `Resource`/`ResponseEntity` 由 Web 层包装 |
| HTTP | `POST /medicationCatalog/downloadImportTemplate`（与现网多 POST 风格对齐；允许等价 GET） |
| 响应 | `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet` |
| 文件名 | `药品目录导入模板_v1.2.xlsx` |
| 行为 | `TemplateBuilder#build()`：灌码表、双行表头、示例、下拉、批注、冻结、保护、激活指导页 |

### 3.2 IF-BAS-CAT-IMP-02 上传导入（已删除）

> **已删除**同步 `importMedicationCatalog`；产品主线仅保留异步 **IMP-04**（`startImportMedicationCatalog` + 进度轮询）。校验/写库逻辑在异步工作流中复用同一套 Parser/Validator/WriteSupport。

<details>
<summary>历史约定（归档，勿再实现）</summary>

| 项 | 约定 |
| --- | --- |
| AppService | `ImportMedicationCatalogResponse importMedicationCatalog(...)` |
| HTTP | `POST /medicationCatalog/importMedicationCatalog` |

</details>
| `message` | `String` | 人话 |

### 3.3 IF-BAS-CAT-IMP-03 下载错误标注文件

| 项 | 约定 |
| --- | --- |
| AppService | `MedicationCatalogImportFileResponse downloadImportErrorFile(DownloadMedicationCatalogImportErrorRequest request)` |
| HTTP | `POST /medicationCatalog/downloadImportErrorFile`，JSON `{ token }` |
| 文件名 | `药品目录导入_错误标注_yyyyMMddHHmmss.xlsx` |
| 失败 | token 无效/过期 → `MED_CAT_IMP_410` |

### 3.4 IF-BAS-CAT-IMP-04 异步启动导入 + 进度轮询

| 项 | 约定 |
| --- | --- |
| 启动 AppService | `StartMedicationCatalogImportResponse startImportMedicationCatalog(ImportMedicationCatalogRequest request)` |
| 启动 HTTP | `POST /medicationCatalog/startImportMedicationCatalog`，JSON `{ fileBytes, fileName? }`（base64） |
| 启动响应 | `{ jobId }` |
| 进度 AppService | `MedicationCatalogImportProgressResponse getImportMedicationCatalogProgress(GetMedicationCatalogImportProgressRequest request)` |
| 进度 HTTP | `POST /medicationCatalog/getImportMedicationCatalogProgress`，JSON `{ jobId }` |
| 限流 | 进度 IF 加入网关限流排除（高频轮询） |

**MedicationCatalogImportProgressResponse**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `jobId` | `String` | 任务号 |
| `status` | `String` | `PENDING` / `RUNNING` / `SUCCEEDED` / `FAILED` |
| `stage` | `String` | `PARSING` / `VALIDATING` / `WRITING` / `DONE` |
| `percent` | `Integer` | 0～100 |
| `total` / `processed` | `Integer` | 行进度 |
| `message` | `String` | 阶段文案 |
| `batchNo` / `successCount` | — | 成功态 |
| `errorFileToken` / `errorCount` / `errorMessages` | — | 失败态 |

**说明**：同步 **IMP-02**（`importMedicationCatalog`）已删除；列表页仅用 **IMP-04 + 进度轮询**。

### 3.5 IF-BAS-CAT-EXP-01 按筛选导出

| 项 | 约定 |
| --- | --- |
| AppService | `ExportMedicationCatalogResponse exportMedicationCatalog(ExportMedicationCatalogRequest request)` |
| HTTP | `POST /medicationCatalog/exportMedicationCatalog`，JSON `{ keyword?, categoryCode? }` |
| 响应 | `{ fileName, contentType, fileBytes, exportedRowCount, truncated }` |
| 文件名 | `药品目录导出_yyyyMMddHHmmss.xlsx` |
| 领域读 | `MedicationDefinitionQueryService#listMedicationCatalogForExport`（同列表谓词；`maxRows=EXPORT_MAX_ROWS+1` 判截断） |
| 定价 | 批量 `ChargeItemDefinitionQueryService#findPriceLinesByProjects`（维护态当前价，§2.2.3） |
| 组装 | `MedicationCatalogExportRowAssembler` → `MedicationCatalogImportTemplateBuilder#buildExport`（单 Sheet「药品目录」，表头+数据，无示例） |

---

## 4. 模板生成（MedicationCatalogImportTemplateBuilder）

### 4.1 Sheet 职责

| Sheet | 生成内容 |
| --- | --- |
| `①从这里开始` | 标题；三步（大字）；拆零对照表；FAQ；`B2=模板版本 v1.2`（可读）；保护只读 |
| `②药品目录` | 第 1 行分组合并（基础/临床/库存单位/业务）；第 2 行字段表头；第 3～4 行示例；第 5～3004 数据验证；冻结前 2 行+第 1 列；列宽；批注 |
| `③可选值一览` | 各列一块：仅 **中文名称**（分类/剂量·采购·拆零·包装·库存单位/剂型/用法/频次/财务类型/是否/药品性质/医保等级/病案结算/医保类别/用法说明/DDD值/DDD单位/拆分属性）；保护只读 |
| `_sys` | 隐藏；`TEMPLATE_CODE` / `TEMPLATE_VERSION=v1.2` / `COLUMN_FINGERPRINT` |

### 4.2 字段表头有序清单（指纹源）

去 `*` 与首尾空白后的规范名（顺序即列序，共 51 列）：

```
药品名称,商品名,药品分类,生产厂家,财务类型,批准文号,贯标国家编码,医保编码,医保等级,病案结算类型,医保对码,医保类别,
药品性质,规格,剂量单位,剂量/拆零倍数,剂型,皮试判别,注射药品,用量限定,默认用法,默认频次,单次剂量,单次最大剂量,用法说明,限制使用范围,药品说明,DDD值,DDD单位,
采购单位,拆零单位,采购/拆零倍数,包装单位,包装/拆零倍数,库存单位,库存/拆零倍数,购入价,零售价,最高零售价,门诊拆分属性,住院临时医嘱拆分属性,警戒线最低值,警戒线最高值,
院内旧编码,69码,基药标识,儿童用药标志,自制药标识,限制使用,抗生素,处方药标志,国家重点监控药品,集采药品
```

**列指纹算法**：将上列用 `|` 拼接后做 **SHA-256**，输出小写 hex；写入 `_sys!B3`；解析时重算比对。

### 4.3 术语灌入来源（与 getMedicationInit 对齐）

| 下拉列 | TerminologyCodeSystemNameConstants / 来源 |
| --- | --- |
| 药品分类 | `MEDICATION_CATEGORY_CODE` |
| 采购单位 | `PURCHASE_UNIT` |
| 拆零单位 | `SPLIT_UNIT` |
| 包装单位 | `SALES_UNIT` |
| 库存单位 | `STOCK_UNIT` |
| 剂量单位 | `DOSE_UNIT` |
| DDD 单位 | `DDD_UNIT` |
| 剂型 | `DOSE_FORM_CODE` |
| 药品性质 | `medicineProperties`（与表单同源） |
| 默认用法 | `METHOD_CODE` |
| 默认频次 | `frequencyOptionLoader.loadActiveRateOptions()`（与 `getMedicationInit` 同源，**非** TerminologyCodeSystem 常量） |
| 用法说明 | `dosageInstruction` |
| DDD 值 / DDD 单位 | `dddCode` / `ddd_unit` |
| 财务类型 | `FIN_TYPE_CODE` |
| 医保等级 | `chrgitmLv` |
| 病案结算类型 | `mrSettlement` |
| 医保类别 | `ybType` |
| 门诊/住院拆分属性 | `EnumUtil.toOptions(OrderCalcRuleEnum.class)`（非术语表） |
| 是/否 | `YES_NO_FLAG`（展示归一为「是」「否」；若术语文案不同，Builder 映射为是/否二字） |

分类若为树形 `OptionDto.children`：**展平**为叶子 label 列表供下拉；导入映射按叶子 code。

### 4.4 示例行（第 3～4 行）

| 行 | 要点 |
| --- | --- |
| 3 | `[示例]阿莫西林胶囊`；商品名/财务类型/药品性质齐全；五单位+四倍数；拆分属性；零售/进价示例数（元/包装单位） |
| 4 | `[示例]某某中成药`；整包装口径；采购=拆零时倍数=1 |

浅黄底；导入跳过。

### 4.5 POI 实现要点

- DataValidation：`XSSFDataValidationHelper`；`createFormulaListConstraint` 指向命名区域或 `③可选值一览!$A$2:$A$N`
- Comment：`Drawing` + `ClientAnchor`
- Sheet 保护：指导/选项/`_sys` `protectSheet`；数据 Sheet **不**整表保护（避免无法插行）
- `workbook.setActiveSheet` → 指导页索引
- 分组合并：按 AS §3.3 列区间合并第 1 行

---

## 5. 解析 / 校验 / 写入

### 5.1 解析 Gate（WorkbookParser）

顺序：

1. 文件非空；扩展名/魔数可打开为 xlsx  
2. 存在 `②药品目录`、`_sys`  
3. `_sys`：`TEMPLATE_CODE`/`TEMPLATE_VERSION` 匹配常量  
4. 第 2 行表头去 `*` 后与常量列序一致；或指纹相等（**两者都校验，任一失败拒绝**）  
5. 自第 5 行起读至连续空行过多或行号上限；构建 `ImportRow`（`excelRowNum` + 原始字符串 map）

**跳过**：整行关键单元格空；`药品名称` trim 后以 `[示例]` 开头。

**容错（UX-11）**：trim；全角数字→半角；布尔「是/否/Y/N/y/n/1/0/true/false」归一。

### 5.2 ImportRow 内部模型（support 包，非 API）

| 字段 | 说明 |
| --- | --- |
| `excelRowNum` | 1-based |
| 各列 `String` 原始值 | 校验前不转码 |
| 解析后 code/BigDecimal/Boolean | Validator 填充或并行 DTO |
| `skipExisting` | 文件内同键的后续行，或库内同键时 true；写段跳过，不报错、不更新 |

### 5.3 Validator（收集全量错误，不短路）

| 检查 | 错误码 | 列 |
| --- | --- | --- |
| 有效行数 0 | `MED_CAT_IMP_201` | — |
| 有效行数 > 3000 | `MED_CAT_IMP_202` | — |
| 必填空 | `MED_CAT_IMP_301` | 对应列 |
| 数字非法 / `<0` / 拆零比 `≤0` | `MED_CAT_IMP_302` | 对应列 |
| 码表无法解析 | `MED_CAT_IMP_303` | 对应列 |
| 采购单位=拆零单位且比≠1 | `MED_CAT_IMP_304` | 拆零比 |
| 限价 `<` 零售价 | `MED_CAT_IMP_305` | 最高限价 |
| 模板/指纹/版本 | `MED_CAT_IMP_101`～`104` | —（Gate，可无行号） |

> **不再作为错误**：文件内同键、库内同键。两者都 → `skipExisting`（文件内只保留首行）。  
> 历史码 `MED_CAT_IMP_306`/`307` 保留枚举占位，**校验器不得再抛出**。

唯一键：有院内旧编码时 `L:` + trim(旧编码)。名称键 `N:` + trim(名称) + `\u0001` + 厂家主键 + `\u0001` + 归一化规格（全角冒号/点转半角）。文件内名称键的厂家段用 Excel 文案，且加 `F:` 前缀，不送入库内查询。

库内检查：先批量解析厂家主键，再 **一次** `findExistingDuplicateKeyTokens(orgId, tokens)`。禁止逐行 exists。

### 5.4 映射 → CreateMedicationCatalogRequest

| Excel | Request | 备注 |
| --- | --- | --- |
| 药品名称 | `name` | |
| 药品分类 | `categoryCode` | 已解析 code |
| 规格 | `totalVolume` | |
| 生产厂家 | `manufacturerText` | `manufacturerId` 不设 |
| 采购/拆零/包装/库存/剂量单位 | 对应 `*UnitCode` | 模板显式填写，禁止默认覆盖 |
| 采购/拆零倍数 | `purchaseUnitRatio` | 模板显式；须 > 0 |
| 包装/拆零倍数 | `packageUnitRatio` | 模板显式；须 > 0 |
| 库存/拆零倍数 | `stockUnitRatio` | 模板显式；须 > 0 |
| 剂量/拆零倍数 | `doseUnitRatio` | 模板显式；须 > 0 |
| 门诊/住院拆分属性 | `partAttributeEnum` / `thoPartAttributeEnum` | `OrderCalcRuleEnum` 中文→码 |
| 零售/进价/限价 | `retailPrice`/`purchasePrice`/`maximumRetailPrice` | 元/一个包装单位 |
| 选填临床/标志/编码 | 同名字段 | 标志 Boolean |
| 药品说明 + 院内旧编码 | `definition` | R-008 合并规则 |
| — | `orgId` | `applyDefaultOrganizationIdForCreate` |
| — | `supplyId` | `null` |
| — | `busNo` | **不传**，领域采番 |
| — | `pyStr`/`wbStr` | 不传，创建路径生成 |

然后调用与创建相同的：

```
validateCreateRequest(req)
applyDefaultOrganizationIdForCreate(req)
req.setSupplyId(null)
id = domainService.createMedicationDefinition(mapper.toCreateCommand(req))
replacePriceLines(id, resolveFinType(...), ybType, purchase, retail, limit)
```

建议抽私有方法 `createOneForImport(CreateMedicationCatalogRequest)`，供 `createMedicationCatalog` 与导入循环共用，避免漂移。

### 5.5 事务与错误文件时序

```
importMedicationCatalog(file):
  bytes = file.getBytes()  // Gate 大小
  parseResult = parser.parse(bytes)           // 可能抛 101～104
  errors = validator.validate(parseResult)    // 库内同键 → skipExisting，不进 errors
  if errors not empty:
    token = errorFileService.buildAndStore(bytes, errors)
    throw ImportFailedException(failureDto)   // 无 @Transactional 写
  batchNo = newBatchNo()
  created = writeSupport.importRows(batchNo, rows, createOneForImport)  // @Transactional
  return { batchNo, totalCount=rows.size(), successCount=created, failCount=0 }

importRows(...):  // MedicationCatalogImportWriteSupport
  syncMdSequenceFromExistingDefinitions()
  for row : rows:
    if row.skipExisting → continue（计入进度，不计入 created）
    createOneForImport(map(row))
  publish MedicationCatalogImportedEvent(batchNo, created, 0, operatorId)
```

**注意**：校验与写必须拆开。写段独立 `@Transactional`；跳过行不触发回滚。异步路径（`startImport` + 进度）消息可写「新建 X 条，跳过库内已存在 Y 条」。

### 5.6 错误标注文件格式

- 基于用户上传 workbook 复制  
- 在 `②药品目录` 最右插入列 `错误说明`  
- 同行多错：`message` 用中文分号 `；` 拼接  
- 错误行整行浅红底  
- 打开时激活数据 Sheet，选中第一个错误行  
- 不修改用户已填业务列内容  

---

## 6. 业务错误码

| code | 场景 |
| --- | --- |
| `MED_CAT_IMP_101` | 非 xlsx / 无法打开 |
| `MED_CAT_IMP_102` | 缺 Sheet |
| `MED_CAT_IMP_103` | 模板编码/版本不匹配 |
| `MED_CAT_IMP_104` | 列指纹或表头不一致 |
| `MED_CAT_IMP_201` | 无有效数据行 |
| `MED_CAT_IMP_202` | 超过 3000 行 |
| `MED_CAT_IMP_301` | 必填缺失 |
| `MED_CAT_IMP_302` | 数值非法 |
| `MED_CAT_IMP_303` | 码表无法解析 |
| `MED_CAT_IMP_304` | 拆零比与单位组合非法 |
| `MED_CAT_IMP_305` | 限价低于零售价 |
| `MED_CAT_IMP_306` | （保留，不用）原文件内重复；现网不抛 |
| `MED_CAT_IMP_307` | （保留，不用）原库内重复；现改为 skipExisting |
| `MED_CAT_IMP_401` | 写库阶段失败（回滚后返回；尽量带行号） |
| `MED_CAT_IMP_410` | 错误文件 token 无效或过期 |

Gate 类错误可只放 `message` 摘要，`errors` 一条 `excelRowNum=null`。

---

## 7. 领域层变更

| 项 | 动作 |
| --- | --- |
| `MedicationDefinitionQueryService` | `Set<String> findExistingDuplicateKeyTokens(Long orgId, Collection<String> tokens)`：同机构未废弃目录，按唯一键 token 批量命中 |
| `MedicationDefinitionDomainService#importMedicationCatalog` | **停止**被 AppService CSV 调用；可标记 `@Deprecated` 或改为文档说明「由应用层逐条 create」；批次事件改由 AppService 发布（若事件构造器在 domain-shared，AppService 可 publish） |
| `ImportMedicationCatalogCommand` | 本期可不扩字段；避免无用膨胀 |
| `downloadMedicationCatalog`（QueryService stub） | AppService IMP-01 **不再**返回 ids 串 字节；改走 TemplateBuilder。QueryService 旧方法可废弃 |

**B.6.4**：库内唯一只经本聚合 QueryService；定价经 ChargeItemDefinition DomainService（与现网创建相同，属应用编排跨聚合写，已有先例）。

---

## 8. 前端（openhis-pro-ui）

### 8.1 API（`catalog.ts`）

| 函数 | 路径 |
| --- | --- |
| `downloadMedicationCatalogImportTemplateApi` | `POST .../downloadImportTemplate` |
| `startImportMedicationCatalogApi` | `POST .../startImportMedicationCatalog`（JSON base64） |
| `getImportMedicationCatalogProgressApi` | `POST .../getImportMedicationCatalogProgress` |
| `downloadMedicationCatalogImportErrorFileApi` | `POST .../downloadImportErrorFile` |

### 8.2 列表页（`medicationcatalog/index.vue`）

1. 工具栏：`下载导入模板`、`导入`  
2. 导入：选 `.xlsx` → `startImportMedicationCatalog` → `ElDialog` + `ElProgress` 轮询进度（约 500ms）  
3. 成功：`ElMessage.success` 展示条数；刷新分页；关闭弹窗  
4. 失败：展示 `message` / `errorMessages`；有 `errorFileToken` 时提示下载错误标注表  
5. 轮询期间禁用导入相关按钮  
6. 权限：与「新建」同一权限码  

---

## 9. 测试要点

| 用例 | 断言 |
| --- | --- |
| 模板下载 | Sheet 名、版本、指纹、下拉、`[示例]` 两行、激活指导页 |
| 10 行合法导入 | 10 条 ACTIVE；各有 PURCHASE+SALE；busNo 非空且互异；包装/库存/四倍数与模板一致 |
| 缺拆零比 | 零新增；errors 含行号；token 可下载；标注列有文案 |
| 文件内两行同键 | 校验无 306；只新建 1 条，另一条 skipExisting |
| 库内已存在同键后整文件重导 | 校验无 307；successCount=0；原记录字段/价格不变 |
| 库内已有部分 + 文件含新品 | successCount=新品数；旧品跳过 |
| 改表头 | `104`；提示重新下载 |
| 仅中文「西药」 | categoryCode 正确 |
| `[示例]` 残留 | 不计入 successCount |
| token 过期 | `410` |
| 写库中抛错 | 整批回滚（可用 mock 第 N 条失败） |

---

## 10. 成码顺序（建议）

1. `MedicationCatalogImportConstants` + QueryService 唯一键  
2. `TemplateBuilder` + IMP-01  
3. `Parser` + `Validator` + `TerminologyIndex`  
4. 抽 `createOneForImport`；IMP-02 事务写段  
5. `ErrorFileService` + IMP-03  
6. 前端三按钮/错误态  
7. 删除或停用 CSV stub 路径  
8. 单测覆盖 §9  

---

## 11. 修订记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v1.5 | 2026-10-07 | 代码先行回写 R-011：旧编码键 / 名称+规格+厂家主键；文件内同键只保留首行 |
| v1.4 | 2026-07-31 | EXP-01：listMedicationCatalogForExport + ExportRowAssembler + TemplateBuilder#buildExport（**单 Sheet**）；常量 EXPORT_MAX_ROWS / EXPORT_SHEET_NAME |
| v1.0 | 2026-07-16 | 首版 SDD：对齐 AS v1.1；类职责、IF、错误码、指纹、旧编码落点、事务时序 |
| v1.1 | 2026-07-16 | 补充 IMP-04 异步启动 + 进度轮询；前端进度条 Dialog |
| v1.2 | 2026-07-16 | 模板升 v1.2 全字段（51 列）；显式包装/库存/四倍数；definition 合并；指纹与必填集同步 |
| v1.3 | 2026-07-25 | 对齐 AS v1.3 / 现网：库内同键 skipExisting、文件内同键不拦截；WriteSupport 写段；306/307 停用；重导验收用例 |
