# 基础资料 · 组套模板 · 应用服务详细设计（SDD）

> **对应 AS**：`AS-BAS-MST-017-组套模板.md`（v1.7）  
> **编码**：AS-BAS-MST-017-SDD · **版本**：v1.9 · **日期**：2026-10-13

---

## 1. 实现边界

### 1.1 目标与边界

| 项 | 内容 |
| --- | --- |
| **模块** | 基础资料 · 组套模板（`basic/ordertemplate`） |
| **边界** | IF-BAS-OTL-01～08、10、11：维护端 CRUD/生命周期；临床侧**快捷数据源**（列表 + **预览结构 only** + 另存模板）；套用期价/库位走 prepicked 目录 IF |
| **编排权威** | `AS-BAS-MST-017` §2、§4 |
| **方法权威** | `DM-CUS-012` §6.1 `OrderTemplateDomainService`；§6.2 `OrderTemplateQueryService` |
| **非目标 IF** | **IF-BAS-OTL-09/12**（后端代调用 saveDraftBatch / confirm*Pricing）**不实现**；前端预览后走下游 AS |
| **实现阶段口径** | **IF-BAS-OTL-01～08、10、11** 已在 `zxplat/health-application/basic/ordertemplate` 落地 |

### 1.2 术语口径

| 项 | 约定 |
| --- | --- |
| **HTTP** | 一律 `POST /api/app/orderTemplate/{methodName}`（`AppServiceControllerRegistrar`） |
| **维护端来源** | 写头/行/生命周期 IF 固定 `template_source_enum = MAINTAINED`（**BR-BAS-OTL-001**） |
| **医嘱模板另存** | IF-10：`FROM_ADVICE` + **PERSONAL / DEPARTMENT**（缺省 **DEPARTMENT**；**BR-BAS-OTL-002**） |
| **临床快捷** | IF-08 **仅**返回头行结构（**不** enrich、**不**实时查价）；套用期由前端 prepicked 调 IF-DOR-11 / IF-IP-PRC-01；写路径回传 `dispenseLocationId` / `performLocation`（**不**写下游） |
| **QueryService 口径** | 只读经 `OrderTemplateQueryService`；禁止 App 直查 `OrderTemplateRepository` |
| **事务口径** | AppService **不**声明 `@Transactional`；同一 IF 内多 Cmd 由框架 `@AppService` 切面或领域层保证原子性（与 `DepartmentAppService` 同口径） |
| **多归属 objectId** | `OrderTemplateScopeResolver`：PERSONAL→`CurrentPractitionerResolver#requirePractitionerId`；DEPARTMENT→`CurrentManagedOrganizationIdsResolver#requireSessionOrganizationUnitId`；HOSPITAL / AGREEMENT→租户级全院根组织 id（`OrganizationQueryService`） |
| **分页口径** | 请求继承 `AbstractHealthPageRequest`（`pageNo` 1-based / `pageSize` / `keyword`）；响应继承 `PagedResult`（`content` / `totalCount` / `pageNo` / `pageSize`）；经 `PageWindowGate` + `PagedResultBuilder`（**B.10.11**） |

#### 1.2.1 文档语义码与 `ErrorCodeConstants` 对照

| 文档语义码 | 实现 `ErrorCodeConstants` / 业务异常 |
| --- | --- |
| EX-BAS-OTL-101 | 读失败统一 `ORDER_TEMPLATE_INVALID_ARGUMENT` 或包装为 `ValidationException` |
| EX-BAS-OTL-102 | `ORDER_TEMPLATE_NOT_FOUND` |
| EX-BAS-OTL-201 | `ORDER_TEMPLATE_INVALID_ARGUMENT` / `ORDER_TEMPLATE_CODE_CONFLICT` |
| EX-BAS-OTL-202 | `ORDER_TEMPLATE_INVALID_STATUS` |
| EX-BAS-OTL-203 | `ORDER_TEMPLATE_INVALID_ARGUMENT`（行引用非法） |
| EX-BAS-OTL-204 | `ORDER_TEMPLATE_INVALID_ARGUMENT`（PRICING 药行 / FROM_ADVICE+PRICING） |
| EX-BAS-OTL-205 | `ORDER_TEMPLATE_INVALID_STATUS` |
| EX-BAS-OTL-301 | 就诊门控：`ENCOUNTER_NOT_FOUND` / `ENCOUNTER_INVALID_ARGUMENT` |
| EX-BAS-OTL-302 | `ORDER_TEMPLATE_INVALID_STATUS` / `ORDER_TEMPLATE_INVALID_ARGUMENT`（预览 kind/scene） |
| EX-BAS-OTL-304 | `ORDER_TEMPLATE_INVALID_ARGUMENT`（另存无选中行） |

---

## 2. 代码结构

### 2.1 包路径（类级清单）

```text
zxplat/health-application/src/main/java/com/openhis/health/application/basic/ordertemplate/
  service/
    OrderTemplateAppService.java
  mapper/
    OrderTemplateMapper.java
    OrderTemplateMapperImpl.java
  request/
    ...
  response/
    ...
  dto/
    ...

zxplat/health-application/src/main/java/com/openhis/health/application/
  common/validator/ordertemplate/
    OrderTemplateScopeResolver.java
  application.shared.advicetemplate/
    OrderTemplateLineCatalogEnrichAssembler.java
    dto/OrderTemplateLineEnrichRow.java
```

> enrich 边界：`OrderTemplateAppService` 私有方法 `toOrderTemplateLineEnrichRow` / `applyOrderTemplateLineEnrichRow` 完成 `OrderTemplateLineRowDto` ↔ `OrderTemplateLineEnrichRow`；**禁止** querysupport import application DTO。

> 下拉选项使用公共库 `com.zxplat.framework.core.application.dto.OptionDto`（**不**在本模块 `dto/` 新建）。  
> 生命周期动作等共享字面量落在 `com.zxplat.domain.share.constants.OrderTemplateConstants`（**禁止**在 `health-application` 下新建 `constants/` 包，**B.10.2.1**）。

### 2.2 依赖注入表

| 注入类型 | 类名（FQCN） | 职责 | 用于 IF |
| --- | --- | --- | --- |
| 应用服务 | `OrderTemplateAppService` | 编排 IF-01～08、10、11 | 全部 |
| 领域写 | `OrderTemplateDomainService` | 头/行/生命周期 | 04～06、10 |
| 领域读 | `OrderTemplateQueryService` | 分页/详情/列表 | 02～03、07～08、11 |
| 就诊 Facade | `EncounterContextReader` | 门诊/住院就诊快照与 scene 推导 | 07～10 |
| 组织读 | `OrganizationQueryService` | 全院根组织 id、科室展示名 | 02～11 |
| 目录 enrich | `ProductDefinitionBatchLoader` | 批量目录展示 | 03 |
| 定价读 | `ChargeItemDefinitionQueryService` | **维护端** IF-03 详情 enrich（**IF-08 不调用**） | 03 |
| 术语 | `TerminologyOptionLoader` | 单位/用法展示名 | 03、08 |
| 频次读 | `FrequencyQueryService` | 频次名称 enrich | 03、08 |
| 医嘱读（另存） | `InpatientOrderLookupLoader` + 域 QueryService | 加载选中行事实 | 10 |
| Mapper | `OrderTemplateMapper` | Command/DTO 装配 | 全部写/Asm |
| Validator | `OrderTemplateScopeResolver`（`module.common.validator.ordertemplate`） | binding→objectId | 04、07、10、11 |
| Querysupport | `OrderTemplateLineCatalogEnrichAssembler`（`application.shared.advicetemplate`） | 行 enrich | 03 |
| 操作人 | `CurrentOperatorResolver` | `operatorId` | 04～06、10 |
| 医务人员 | `CurrentPractitionerResolver` | 个人归属 `practitionerId` | 04、07～11 |
| 会话科室 | `CurrentManagedOrganizationIdsResolver` | 科室归属 `organizationId` | 04、07～11 |
| 分页门控 | `PageWindowGate` | `pageNo`/`pageSize` 入站校验 | 02 |
| 分页装配 | `PagedResultBuilder` | `PagedResult` 响应构建 | 02 |

### 2.3 枚举、字典与值集（DM 与代码可追溯）

| 类别 | 代码实现锚点 | DM 追溯锚点 | 说明 |
| --- | --- | --- | --- |
| 绑定类型 | `com.zxplat.domain.share.enums.OrderTemplateBinding` | `DM-CUS-012` §4.1 | PERSONAL/DEPARTMENT/HOSPITAL/AGREEMENT；IF-01/02/04/07/11 |
| 适用场景 | `com.zxplat.domain.share.enums.OrderTemplateScene` | `DM-CUS-012` §4.2 `scene_enum` | OUTPATIENT/INPATIENT/COMMON |
| 组套种类 | `com.zxplat.domain.share.enums.OrderTemplateKind` | `DM-CUS-012` §4.4 | GENERAL/TCM/PRICING |
| 模板来源 | `com.zxplat.domain.share.enums.OrderTemplateSource` | `DM-CUS-012` §4.2 `template_source_enum` | MAINTAINED/FROM_ADVICE |
| 模板状态 | `com.zxplat.domain.share.enums.OrderTemplateStatus` | `DM-CUS-012` §2.3 | DRAFT/ACTIVE/RETIRED |
| 行类型 | `com.zxplat.domain.share.enums.OrderTemplateLineKind` | `DM-CUS-012` §4.3 `line_kind_enum` | MEDICATION/SERVICE/DEVICE |
| 生命周期动作 | `com.zxplat.domain.share.constants.OrderTemplateConstants.LifecycleAction` | `AS-BAS-MST-017` IF-06 | PUBLISH/RETIRE/DELETE；已落地 |
| 预览上下文 | `PreviewOrderTemplateApplyRequest.applyContextCode` | IF-08 | DOCTOR / NURSE_PRICING |
| 医生站工作站 | `PreviewOrderTemplateApplyRequest.workstationKindCode` | IF-08（DOCTOR） | OUTPATIENT/INPATIENT |
| 维护端来源字段 | `SaveMaintainedOrderTemplateHeaderRequest`（无 source 字段，App 写死 MAINTAINED） | `BR-OTL-031` | 创建 Command 时注入 |
| 业务错误码 | `ErrorCodeConstants.ORDER_TEMPLATE_*` | `DM-CUS-012` §6 失败语义 | 与 §1.2.1 对照 |

### 2.4 领域方法白名单

| 类型 | 方法 | 用于 IF |
| --- | --- | --- |
| `OrderTemplateQueryService` | `pageOrderTemplates` | 02 |
| `OrderTemplateQueryService` | `getOrderTemplateWithLines` | 03、08 |
| `OrderTemplateQueryService` | `listOrderTemplatesByOwnerAndScene` | 07、11 |
| `OrderTemplateDomainService` | `createOrderTemplate` | 04、10 |
| `OrderTemplateDomainService` | `updateOrderTemplateHeader` | 04 |
| `OrderTemplateDomainService` | `addOrderTemplateLine` | 05、10 |
| `OrderTemplateDomainService` | `updateOrderTemplateLine` | 05 |
| `OrderTemplateDomainService` | `removeOrderTemplateLine` | 05 |
| `OrderTemplateDomainService` | `publishOrderTemplate` | 06、10 |
| `OrderTemplateDomainService` | `retireOrderTemplate` | 06 |
| `OrderTemplateDomainService` | `deleteOrderTemplate` | 06 |
| `ChargeItemDefinitionQueryService` | `findPriceLinesByProjects` | 08 |
| `EncounterContextReader` | `mustGetOutpatientContextByEncounterId` / `mustGetInpatientContextByEncounterId` | 07～10 |
| `OrderTemplateMapper` | 见各 IF Mapper 签名 | Asm |
| `OrderTemplateLineCatalogEnrichAssembler` | `enrichLines` | 03 |
| `OrderTemplateScopeResolver` | `resolveObjectId` | 04、07、10、11 |

---

## 3. 按接口 ID 命名展开

> **阅读顺序**：各 IF 的「领域编排序列」须按序号自上而下执行；Pre/Gate 失败禁止进入 Cmd。

### 3.1 应用服务接口（方法白名单）

| IF | 应用服务方法（英文） | 中文方法名 | HTTP |
| --- | --- | --- | --- |
| IF-BAS-OTL-01 | `initOrderTemplateMaintenance` | 维护页初始化 | `POST /api/app/orderTemplate/initOrderTemplateMaintenance` |
| IF-BAS-OTL-02 | `pageMaintainedOrderTemplates` | 维护端分页查询 | `POST /api/app/orderTemplate/pageMaintainedOrderTemplates` |
| IF-BAS-OTL-03 | `getOrderTemplateDetail` | 维护端详情含行 | `POST /api/app/orderTemplate/getOrderTemplateDetail` |
| IF-BAS-OTL-04 | `saveMaintainedOrderTemplateHeader` | 维护端保存头 | `POST /api/app/orderTemplate/saveMaintainedOrderTemplateHeader` |
| IF-BAS-OTL-05 | `saveMaintainedOrderTemplateLines` | 维护端保存行 | `POST /api/app/orderTemplate/saveMaintainedOrderTemplateLines` |
| IF-BAS-OTL-06 | `changeOrderTemplateLifecycle` | 维护端发布停用删除 | `POST /api/app/orderTemplate/changeOrderTemplateLifecycle` |
| IF-BAS-OTL-07 | `listDoctorStationOrderTemplates` | 医生站可套用列表 | `POST /api/app/orderTemplate/listDoctorStationOrderTemplates` |
| IF-BAS-OTL-08 | `previewOrderTemplateApply` | 预览明细（仅头行结构） | `POST /api/app/orderTemplate/previewOrderTemplateApply` |
| IF-BAS-OTL-10 | `saveOrderTemplateFromAdvice` | 医生站另存医嘱模板 | `POST /api/app/orderTemplate/saveOrderTemplateFromAdvice` |
| IF-BAS-OTL-11 | `listNurseStationPricingOrderTemplates` | 护士站划价组套列表 | `POST /api/app/orderTemplate/listNurseStationPricingOrderTemplates` |

> **非目标**：IF-BAS-OTL-09 `applyOrderTemplateToAdviceDraft`、IF-BAS-OTL-12 `applyOrderTemplateToNursePricing` **无 HTTP 实现**；前端预览后调 **AS-OP-DOR-003** / **AS-IP-DOR-003** / **AS-IP-NUR-005** 既有写 IF。

### 3.1.1 编排约定（应用层如何实现 AS §4）

| 阶段 | 含义 | 失败时 |
| --- | --- | --- |
| **Gate** | 参数、权限、Tab 枚举、工作站/划价模式 | 不再调用后续步骤 |
| **Pre** | 只读：加载模板/就诊快照、ACTIVE/kind/source/scene 门禁 | 抛业务异常，**禁止进入 Cmd** |
| **Cmd** | 领域写 | 事务回滚见 §5 |
| **Asm** | Mapper/Support 纯装配 | — |

**记法**：`OrderTemplateDomainService#createOrderTemplate(CreateOrderTemplateCommand)` 表示注入 Bean 调用。

---

### 3.2 IF-BAS-OTL-01 `initOrderTemplateMaintenance`（维护页初始化）

#### Request（`InitOrderTemplateMaintenanceRequest`）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| — | — | — | 空请求体 |

#### Response（`InitOrderTemplateMaintenanceResponse`）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| bindingOptions | List\<OptionDto\> | 绑定类型 |
| sceneOptions | List\<OptionDto\> | 适用场景 |
| templateKindOptions | List\<OptionDto\> | 组套种类 |
| lineKindOptions | List\<OptionDto\> | 行类型（PRICING Tab 前端隐藏 MEDICATION） |
| statusOptions | List\<OptionDto\> | 模板状态 |
| tcmMethodCodeOptions | List\<OptionDto\> | 中药用法下拉（术语 `tcm_method_code`；TCM Tab 行编辑） |
| medicationRateOptions | List\<OptionDto\> | 用药频次下拉（与 IF-DOR-18 同源） |

#### 失败码口径

| 失败码 | 场景 |
| --- | --- |
| EX-BAS-OTL-101 | 枚举装配异常 |

#### 领域编排序列

| 序号 | 阶段 | 调用 | 说明 |
| --- | --- | --- | --- |
| 1 | Asm | `OrderTemplateMapper#toInitOrderTemplateMaintenanceResponse` | 由 `OrderTemplateBinding/Scene/Kind/LineKind/Status` 枚举生成 `OptionDto`；仅读 |
| 2 | Asm | `CodeSystemQueryService#getOptionListByCodeSystemName(TCM_METHOD_CODE)` | 中药用法下拉 |
| 3 | Asm | `FrequencyOptionLoader#loadActiveRateOptions` | 用药频次下拉 |

---

### 3.3 IF-BAS-OTL-02 `pageMaintainedOrderTemplates`（维护端分页查询）

#### Request（`PageMaintainedOrderTemplateRequest`，继承 `AbstractHealthPageRequest`）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| templateKindEnum | String | 是 | GENERAL/TCM/PRICING（当前 Tab） |
| bindingEnum | String | 否 | 筛选绑定 |
| sceneEnum | String | 否 | 筛选场景 code |
| statusEnum | String | 否 | 筛选状态 |
| templateSourceEnum | String | 否 | 模板来源（MAINTAINED / FROM_ADVICE）；**缺省不过滤** |
| keyword | String | 否 | 名称/编码关键字（**B.10.11** 标准分页字段） |
| searchKey | String | 否 | 名称/编码关键字（与 `keyword` 二选一，兼容旧契约） |
| pageNo | Integer | 是 | 从 1 起 |
| pageSize | Integer | 是 | 默认 50 |

#### Response（`PageMaintainedOrderTemplateResponse`，继承 `PagedResult<OrderTemplateHeaderRowDto>`）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| content | List\<OrderTemplateHeaderRowDto\> | 当前页头列表 |
| totalCount | Long | 总条数 |
| pageNo | Integer | 当前页码（1-based） |
| pageSize | Integer | 每页条数 |

#### DTO：`OrderTemplateHeaderRowDto`

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| orderTemplateId | Long | 头 id |
| name | String | 名称 |
| templateCode | String | 编码 |
| bindingEnum | String | 绑定 code |
| bindingEnumText | String | 绑定展示名 |
| objectId | Long | 归属对象 id（PERSONAL=医务人员；DEPARTMENT/HOSPITAL/AGREEMENT=组织） |
| objectName | String | 归属对象展示名（Asm enrich） |
| sceneEnum | String | 场景 code |
| sceneEnumText | String | 场景展示名 |
| templateKindEnum | String | 种类 code |
| templateKindEnumText | String | 种类展示名 |
| templateSourceEnum | String | 来源 code（固定 MAINTAINED） |
| templateSourceEnumText | String | 来源展示名 |
| statusEnum | String | 状态 code |
| statusEnumText | String | 状态展示名 |
| sortWeight | Integer | 排序权重 |
| defaultChineseHerbsDoseQuantity | BigDecimal | TCM 默认付数 |
| defaultSufferingFlag | Boolean | TCM 默认代煎 |
| description | String | 描述 |
| remark | String | 备注 |

#### 失败码口径

| 失败码 | 场景 |
| --- | --- |
| EX-BAS-OTL-101 | 查询失败 |

#### Mapper 方法签名

```java
PageMaintainedOrderTemplateResponse toPageMaintainedOrderTemplateResponse(Page<OrderTemplateHeaderQueryModel>, String keyword);
List<OrderTemplateHeaderRowDto> toHeaderRowDtos(List<OrderTemplateHeaderQueryModel>);
```

#### 领域编排序列

| 序号 | 阶段 | 调用 | 说明 |
| --- | --- | --- | --- |
| 1 | Gate | `PageWindowGate#requirePositivePageWindow` | pageNo/pageSize 非法 → EX-BAS-OTL-201 |
| 2 | Gate | App 校验 `templateKindEnum` 合法 | 非法 → EX-BAS-OTL-201 |
| 3 | Pre | App `resolvePageKeyword`（`keyword` 优先，其次 `searchKey`） | |
| 4 | Pre | `OrderTemplateQueryService#pageOrderTemplates(binding, scene, status, kind, source?, pageable)` | `templateSourceEnum` 缺省 **不过滤**（含 FROM_ADVICE）（**BR-BAS-OTL-007**） |
| 5 | Asm | `OrderTemplateMapper#toPageMaintainedOrderTemplateResponse` | 经 `PagedResultBuilder` 装配；若 keyword 非空，对**当前页** `content` 按 name/templateCode 子串过滤（**B.2.1** `SearchCodeUtil`） |

---

### 3.4 IF-BAS-OTL-03 `getOrderTemplateDetail`（维护端详情含行）

#### Request（`GetOrderTemplateDetailRequest`）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| orderTemplateId | Long | 是 | 组套头 id |

#### Response（`GetOrderTemplateDetailResponse`）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| header | OrderTemplateHeaderRowDto | 头（字段同 §3.3 DTO） |
| lines | List\<OrderTemplateLineRowDto\> | 行列表 |

#### DTO：`OrderTemplateLineRowDto`

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| lineId | Long | 行 id |
| lineKindEnum | String | 行类型 code |
| lineKindEnumText | String | 行类型展示名 |
| medicationDefinitionId | Long | 药品定义 id |
| activityDefinitionId | Long | 诊疗定义 id |
| deviceDefinitionId | Long | 耗材定义 id |
| productTableName | String | 目录物理表名 |
| productName | String | 目录展示名 |
| productSpec | String | 规格 |
| groupKey | String | 成组键 |
| sortOrder | Integer | 套内排序 |
| defaultQuantity | BigDecimal | 默认数量 |
| defaultUnitCode | String | 默认单位 code |
| defaultUnitName | String | 默认单位名 |
| defaultDays | Integer | 默认天数 |
| frequencyId | Long | 频次 id |
| frequencyText | String | 频次展示 |
| routeCode | String | 用法 code |
| routeName | String | 用法展示 |
| dosageText | String | 剂量文本 |
| defaultPerformOrgId | Long | 执行科室/库房 id |
| defaultPerformOrgName | String | 执行科室/库房名 |
| lineRemark | String | 行备注 |
| chineseHerbsDoseQuantity | BigDecimal | TCM 行付数 |
| sufferingFlag | Boolean | TCM 行代煎 |

#### 失败码口径

| 失败码 | 场景 |
| --- | --- |
| EX-BAS-OTL-102 | 不存在 |
| EX-BAS-OTL-101 | 读失败 |

#### Mapper / Support 签名

```java
GetOrderTemplateDetailResponse toGetOrderTemplateDetailResponse(OrderTemplateWithLinesQueryModel model);
void enrichLines(List<OrderTemplateLineRowDto> lines, Long performOrgContextId); // OrderTemplateLineCatalogEnrichAssembler
```

#### 领域编排序列

| 序号 | 阶段 | 调用 | 说明 |
| --- | --- | --- | --- |
| 1 | Pre | `OrderTemplateQueryService#getOrderTemplateWithLines(orderTemplateId)` | 不存在 → EX-BAS-OTL-102 |
| 2 | Asm | `OrderTemplateMapper#toGetOrderTemplateDetailResponse` | |
| 3 | Asm | `OrderTemplateAppService#enrichOrderTemplateDetailLines` → `OrderTemplateLineCatalogEnrichAssembler#enrichLines`（AppService 映射 `OrderTemplateLineEnrichRow`；`performOrgContextId` 传 null） | 批量目录/术语 enrich |

---

### 3.5 IF-BAS-OTL-04 `saveMaintainedOrderTemplateHeader`（维护端保存头）

#### Request（`SaveMaintainedOrderTemplateHeaderRequest`）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| orderTemplateId | Long | 否 | 空=新建 |
| bindingEnum | String | 是 | PERSONAL/DEPARTMENT/HOSPITAL/AGREEMENT |
| objectId | Long | 否 | 缺省由 ScopeResolver 补全（**BR-BAS-OTL-008**） |
| sceneEnum | String | 是 | OUTPATIENT/INPATIENT/COMMON |
| templateKindEnum | String | 是 | GENERAL/TCM/PRICING |
| templateCode | String | 否 | 编码 |
| name | String | 是 | 名称 |
| description | String | 否 | 描述 |
| sortWeight | Integer | 否 | 默认 0 |
| remark | String | 否 | 备注 |
| defaultChineseHerbsDoseQuantity | BigDecimal | 条件 | TCM 必填 |
| defaultSufferingFlag | Boolean | 否 | TCM 代煎 |

#### Response（`SaveMaintainedOrderTemplateHeaderResponse`）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| orderTemplateId | Long | 头 id |

#### 失败码口径

| 失败码 | 场景 |
| --- | --- |
| EX-BAS-OTL-201 | 校验/编码冲突 |
| EX-BAS-OTL-202 | RETIRED 不可改 |

#### Mapper 方法签名

```java
CreateOrderTemplateCommand toCreateOrderTemplateCommand(SaveMaintainedOrderTemplateHeaderRequest, Long operatorId, Long objectId);
UpdateOrderTemplateHeaderCommand toUpdateOrderTemplateHeaderCommand(SaveMaintainedOrderTemplateHeaderRequest, Long operatorId);
```

#### 领域编排序列

| 序号 | 阶段 | 调用 | 说明 |
| --- | --- | --- | --- |
| 1 | Gate | 校验 name/binding/scene/kind；PRICING 场景∈{INPATIENT,COMMON}；TCM 付数 | 失败 → EX-BAS-OTL-201 |
| 2 | Gate | `OrderTemplateScopeResolver#resolveObjectId(bindingEnum, request.objectId)` | |
| 3 | Gate | `CurrentOperatorResolver#requireOperatorId` | |
| 4 | Cmd | 若 `orderTemplateId==null`：`OrderTemplateMapper#toCreateOrderTemplateCommand`（**注入 templateSourceEnum=MAINTAINED**）→ `OrderTemplateDomainService#createOrderTemplate` | **写库点 1** |
| 5 | Cmd | 若 `orderTemplateId!=null`：`OrderTemplateMapper#toUpdateOrderTemplateHeaderCommand` → `OrderTemplateDomainService#updateOrderTemplateHeader` | **写库点 1**；RETIRED → EX-BAS-OTL-202 |
| 6 | Asm | 返回 id | |

---

### 3.6 IF-BAS-OTL-05 `saveMaintainedOrderTemplateLines`（维护端保存行）

#### Request（`SaveMaintainedOrderTemplateLinesRequest`）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| orderTemplateId | Long | 是 | 头 id |
| lines | List\<OrderTemplateLineEditDto\> | 是 | 全量对齐集合 |

#### DTO：`OrderTemplateLineEditDto`

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| lineId | Long | 否 | 空=新增 |
| deleted | Boolean | 否 | true=本次移除 |
| lineKindEnum | String | 是 | MEDICATION/SERVICE/DEVICE |
| medicationDefinitionId | Long | 条件 | 药行 |
| activityDefinitionId | Long | 条件 | 诊疗行 |
| deviceDefinitionId | Long | 条件 | 耗材行 |
| groupKey | String | 否 | 成组键 |
| sortOrder | Integer | 是 | ≥1 |
| defaultQuantity | BigDecimal | 否 | |
| defaultUnitCode | String | 否 | |
| defaultDays | Integer | 否 | |
| frequencyId | Long | 否 | |
| routeCode | String | 否 | |
| dosageText | String | 否 | |
| defaultPerformOrgId | Long | 否 | |
| lineRemark | String | 否 | |
| chineseHerbsDoseQuantity | BigDecimal | 否 | |
| sufferingFlag | Boolean | 否 | |

#### Response（`SaveMaintainedOrderTemplateLinesResponse`）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| savedLineCount | Integer | 有效行数 |

#### 失败码口径

| 失败码 | 场景 |
| --- | --- |
| EX-BAS-OTL-203 | 引用非法 |
| EX-BAS-OTL-204 | PRICING 含 MEDICATION |
| EX-BAS-OTL-202 | 头 RETIRED/已删 |

#### Mapper 方法签名

```java
AddOrderTemplateLineCommand toAddLineCommand(OrderTemplateLineEditDto, Long orderTemplateId, Long operatorId);
UpdateOrderTemplateLineCommand toUpdateLineCommand(OrderTemplateLineEditDto, Long operatorId);
RemoveOrderTemplateLineCommand toRemoveLineCommand(Long lineId, Long operatorId);
OrderTemplateMapper.LineDiffResult diffLines(List<OrderTemplateLineEditDto> submitted, List<OrderTemplateLineQueryModel> existing);
```

> `LineDiffResult` 为 `OrderTemplateMapper` 内部类，含 `removeLineIds` / `updateLines` / `addLines` 三组集合。

#### 领域编排序列

| 序号 | 阶段 | 调用 | 说明 |
| --- | --- | --- | --- |
| 1 | Pre | `OrderTemplateQueryService#getOrderTemplateWithLines(orderTemplateId)` | 断言头可维护行（**BR-OTL-018**） |
| 2 | Gate | 对全部非 `deleted` 提交行校验 PRICING 禁止 MEDICATION | EX-BAS-OTL-204 |
| 3 | Gate | `CurrentOperatorResolver#requireOperatorId` | |
| 4 | Pre | `OrderTemplateMapper#diffLines` | 计算新增/更新/删除集合 |
| 5 | Cmd | 对每个删除行：`removeOrderTemplateLine(RemoveOrderTemplateLineCommand)` | **写库点**（行软删） |
| 6 | Cmd | 对每个更新行：`updateOrderTemplateLine(UpdateOrderTemplateLineCommand)` | **写库点** |
| 7 | Cmd | 对每个新增行：`addOrderTemplateLine(AddOrderTemplateLineCommand)` | **写库点** |
| 8 | Asm | 回读有效行数 `savedLineCount` | 同一 IF **同一事务** |

---

### 3.7 IF-BAS-OTL-06 `changeOrderTemplateLifecycle`（维护端发布停用删除）

#### Request（`ChangeOrderTemplateLifecycleRequest`）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| orderTemplateId | Long | 是 | 头 id |
| lifecycleActionCode | String | 是 | PUBLISH / RETIRE / DELETE |

#### Response（`ChangeOrderTemplateLifecycleResponse`）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| success | Boolean | 是否成功 |

#### 失败码口径

| 失败码 | 场景 |
| --- | --- |
| EX-BAS-OTL-205 | 状态不允许 / 空壳发布 |
| EX-BAS-OTL-102 | 不存在 |

#### 领域编排序列

| 序号 | 阶段 | 调用 | 说明 |
| --- | --- | --- | --- |
| 1 | Gate | 校验 `lifecycleActionCode` ∈ {PUBLISH,RETIRE,DELETE} | |
| 2 | Gate | `CurrentOperatorResolver#requireOperatorId` | |
| 3 | Cmd | 若 action=PUBLISH：`publishOrderTemplate(PublishOrderTemplateCommand)` | **写库点**；空壳 → EX-BAS-OTL-205 |
| 4 | Cmd | 若 action=RETIRE：`retireOrderTemplate(RetireOrderTemplateCommand)` | **写库点** |
| 5 | Cmd | 若 action=DELETE：`deleteOrderTemplate(DeleteOrderTemplateCommand)` | **写库点**；幂等 |
| 6 | Asm | `success=true` | 互斥分支仅执行其一 |

---

### 3.8 IF-BAS-OTL-07 `listDoctorStationOrderTemplates`（医生站可套用列表）

#### Request（`ListDoctorStationOrderTemplateRequest`）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| encounterId | Long | 是 | 就诊 id |
| patientId | Long | 是 | 患者 id |
| templateSourceEnum | String | 是 | MAINTAINED 或 FROM_ADVICE（Tab） |
| templateKindEnum | String | 是 | GENERAL 或 TCM |
| searchKey | String | 否 | 名称过滤 |

#### Response（`ListDoctorStationOrderTemplateResponse`）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| groups | OrderTemplateBindingGroupDto | 四段列表 |

#### DTO：`OrderTemplateBindingGroupDto`

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| personalList | List\<OrderTemplateHeaderRowDto\> | 个人（字段同 §3.3） |
| departmentList | List\<OrderTemplateHeaderRowDto\> | 科室 |
| hospitalList | List\<OrderTemplateHeaderRowDto\> | 全院 |
| agreementList | List\<OrderTemplateHeaderRowDto\> | 协定处方 |

#### 失败码口径

| 失败码 | 场景 |
| --- | --- |
| EX-BAS-OTL-301 | 就诊无效 |
| EX-BAS-OTL-204 | FROM_ADVICE+PRICING（kind 非法） |

#### 领域编排序列

| 序号 | 阶段 | 调用 | 说明 |
| --- | --- | --- | --- |
| 1 | Gate | 校验 source/kind；**禁止** PRICING（**BR-OTL-030**） | |
| 2 | Pre | `EncounterContextReader` 加载就诊；推导 `OrderTemplateScene` | 失败 → EX-BAS-OTL-301 |
| 3 | Pre | 解析 practitionerId、organizationId、hospitalRootOrgId、agreementOrgId（与 hospital 同根组织） | |
| 4 | Pre | ×4：`listOrderTemplatesByOwnerAndScene(objectId, binding, scene, kind, source)` | 仅 **ACTIVE** |
| 5 | Asm | `OrderTemplateMapper#toListBindingGroupResponse` | 合并顺序 personal→department→hospital→agreement（**BR-OTL-QRY-001**）；可选 searchKey 过滤 |

---

### 3.9 IF-BAS-OTL-08 `previewOrderTemplateApply`（预览套用明细）

#### Request（`PreviewOrderTemplateApplyRequest`）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| orderTemplateId | Long | 是 | 模板 id |
| encounterId | Long | 否 | 医生站必填；护士站可空 |
| patientId | Long | 否 | 医生站必填 |
| applyContextCode | String | 是 | DOCTOR / NURSE_PRICING |
| workstationKindCode | String | 条件 | DOCTOR 时必填 |

#### Response（`PreviewOrderTemplateApplyResponse`）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| header | OrderTemplateHeaderRowDto | 头 |
| lines | List\<OrderTemplateLineRowDto\> | 行（定义 id、数量、用法等；**不含** enrich 与实时价；前端以 id 调 IF-DOR-11 `prepickedProductKeys` 补齐） |

#### 套用链路（与选品目录 / 写路径衔接）

1. **IF-BAS-OTL-08**：取头行结构（性能：不做目录 enrich、不查价）。
2. **医生站 IF-DOR-11** / **护士站 IF-IP-PRC-01**：前端以行内 `productDefinitionId` + `productTableName` 组装 `prepickedProductKeys`，拉目录（展示名、`unitPrice`、耗材 `defaultDispenseLocationId` 等）。
3. **医生站 IF-DOR-14（仅临嘱）** / **护士站 IF-IP-PRC-02/04（划价，非长期执行）**：写路径回传目录已解析库位（`dispenseLocationId` / `performLocation`）；定价经 **`clientResolvedDispenseLocationId`** / **`resolveDraftChargePriceLine`**，**不再**二次 match 模板。

> **不在此链路内**：住院 **`LONG_TERM`** 药品/耗材的**护士执行**（**IF-IP-EXE-02**）——每次 occurrence 在写路径按执行库存 Gate + FIFO 解析库位/批号并新建 Dispense+Charge，**不**沿用开嘱或划价时的目录透传库位。

#### 失败码口径

| 失败码 | 场景 |
| --- | --- |
| EX-BAS-OTL-302 | 非 ACTIVE / kind/scene 不符 |

#### 领域编排序列

| 序号 | 阶段 | 调用 | 说明 |
| --- | --- | --- | --- |
| 1 | Pre | `getOrderTemplateWithLines` | |
| 2 | Pre | `OrderTemplateMapper#assertPreviewable(header, applyContext, encounterScene)` | ACTIVE+kind+source（**BR-OTL-APY-001～002**） |
| 3 | Asm | `OrderTemplateMapper#toPreviewOrderTemplateApplyResponse` | **不**调用 `OrderTemplateLineCatalogEnrichAssembler` / 定价 enrich |

---

### 3.10 IF-BAS-OTL-10 `saveOrderTemplateFromAdvice`（医生站另存医嘱模板）

#### Request（`SaveOrderTemplateFromAdviceRequest`）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| encounterId | Long | 是 | |
| patientId | Long | 是 | |
| templateName | String | 是 | 模板名 |
| bindingEnum | String | 否 | PERSONAL / DEPARTMENT；**缺省 DEPARTMENT**（**BR-BAS-OTL-002**） |
| sourceLines | List\<AdviceSourceLineKeyDto\> | 是 | 选中行键 |

#### DTO：`AdviceSourceLineKeyDto`

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| requestId | Long | 是 | 药/诊/材请求 id |
| productTableName | String | 是 | 资源表名 |
| listRowKey | String | 否 | 列表稳定键 |

#### Response（`SaveOrderTemplateFromAdviceResponse`）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| orderTemplateId | Long | 新建模板 id |

#### 失败码口径

| 失败码 | 场景 |
| --- | --- |
| EX-BAS-OTL-304 | sourceLines 空 |
| EX-BAS-OTL-204 | 推导 kind=PRICING |

#### 领域编排序列

| 序号 | 阶段 | 调用 | 说明 |
| --- | --- | --- | --- |
| 1 | Gate | sourceLines 非空 | EX-BAS-OTL-304 |
| 2 | Gate | `bindingEnum` 归一（缺省 DEPARTMENT）；仅允许 PERSONAL / DEPARTMENT | 非法 → EX-BAS-OTL-201 |
| 3 | Pre | 就诊门控 + 加载选中医嘱读模型 | |
| 4 | Pre | `OrderTemplateScopeResolver#resolveObjectId(bindingEnum, null)` | 个人→医务人员；科室→会话科室（**BR-BAS-OTL-008**） |
| 5 | Asm | `OrderTemplateMapper#deriveTemplateKindFromAdviceLines` | 含 TCM→TCM 否则 GENERAL；**禁止** PRICING |
| 6 | Cmd | `createOrderTemplate`（**FROM_ADVICE**、ACTIVE 目标） | **写库点 1** |
| 7 | Cmd | 循环 `addOrderTemplateLine`（药品 `dosageText` 优先 `dosageInstruction`、回落 `dose`+`doseUnitCode`；诊疗 `executionCount`→`defaultDays`） | **写库点 2..n** |
| 8 | Cmd | `publishOrderTemplate` | **写库点**；同事务（**BR-BAS-OTL-002**） |
| 9 | Asm | 返回 orderTemplateId | |

---

### 3.11 IF-BAS-OTL-11 `listNurseStationPricingOrderTemplates`（护士站划价组套列表）

#### Request（`ListNurseStationPricingOrderTemplateRequest`）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| searchKey | String | 否 | 名称过滤 |

#### Response

同 **§3.8** `ListDoctorStationOrderTemplateResponse` / `OrderTemplateBindingGroupDto`（固定 kind=**PRICING**、source=**MAINTAINED**）。

#### 领域编排序列

| 序号 | 阶段 | 调用 | 说明 |
| --- | --- | --- | --- |
| 1 | Gate | 固定 `templateKindEnum=PRICING`、`templateSourceEnum=MAINTAINED` | **BR-BAS-OTL-006** |
| 2 | Pre | scene 固定 **INPATIENT**（含 COMMON 并集由 QueryService 处理） | |
| 3 | Pre | ×4 `listOrderTemplatesByOwnerAndScene` | 同 IF-07 |
| 4 | Asm | Mapper 装配 + searchKey 过滤 | |

---

## 4. QueryService 与领域事件

### 4.1 QueryService 查询语义

| QueryService 方法 | 用于 IF | 语义 |
| --- | --- | --- |
| `pageOrderTemplates` | 02 | 维护端分页；kind 必填；**source 可选**（缺省不过滤）；**binding/scene/status 可选**（v1.4.1：任一为空走 Specification） |
| `getOrderTemplateWithLines` | 03、08 | 头+有效行升序 |
| `listOrderTemplatesByOwnerAndScene` | 07、11 | ACTIVE；scene 并 COMMON；kind/source 精确 |

### 4.2 写侧事件与读侧刷新

| 领域事件 | 触发 IF | 消费方 |
| --- | --- | --- |
| `OrderTemplateCreatedEvent` | 04、10 | 可选缓存；**不**写 QueryService 投影 |
| `OrderTemplateUpdatedEvent` | 04 | 同上 |
| `OrderTemplatePublishedEvent` | 06、10 | 套用列表即时可见 |
| `OrderTemplateRetiredEvent` | 06 | 套用列表移除 |
| `OrderTemplateDeletedEvent` | 06 | 维护端不可见 |
| `OrderTemplateLineAdded/Updated/RemovedEvent` | 05 | 详情缓存失效 |

> 临床 IF **不**写下游聚合、**不**发布 OrderTemplate 套用事件。

---

## 5. 写库顺序（核心写接口视角）

### 5.1 Cmd 落库顺序一览

| IF | App 方法 | Cmd 序号 | 调用 | 聚合写入顺序 |
| --- | --- | --- | --- | --- |
| IF-04 | saveMaintainedOrderTemplateHeader | 1 | create / updateHeader | OrderTemplate |
| IF-05 | saveMaintainedOrderTemplateLines | 1..n | remove / update / add Line | OrderTemplateLine |
| IF-06 | changeOrderTemplateLifecycle | 1 | publish / retire / delete | OrderTemplate (+Line 级联软删) |
| IF-10 | saveOrderTemplateFromAdvice | 1 | createOrderTemplate | OrderTemplate |
| IF-10 | | 2..n | addOrderTemplateLine | OrderTemplateLine |
| IF-10 | | n+1 | publishOrderTemplate | OrderTemplate |

### 5.2 事务边界与失败回滚

| 接口 | 事务边界 | 失败回滚 |
| --- | --- | --- |
| IF-04～06、10 | App 无 `@Transactional`；期望单 IF 内 Cmd 原子 | 任一步失败整 IF 失败 |
| IF-05 | 全量行对齐同一事务 | 任一行失败全部回滚 |

### 5.3 幂等落点

| 接口 | 策略 | 关联步骤 |
| --- | --- | --- |
| IF-06 DELETE | 重复删除幂等 | §3.7 步骤 5 |

---

## 6. 非功能性需求实现（收敛为承诺表）

### 6.1 幂等性

| 场景 | 承诺 | 代码位置 | 关联步骤 |
| --- | --- | --- | --- |
| 逻辑删除重复提交 | 成功幂等 | `OrderTemplateDomainService#deleteOrderTemplate` | §3.7-5 |

### 6.2 并发控制

| 场景 | 承诺 | 实现方式 |
| --- | --- | --- |
| 同模板并发编辑行 | 后写覆盖 / 乐观锁以实现为准 | IF-05 全量对齐 |

### 6.3 重试与容错

预览读失败走 EX-BAS-OTL-101/302；无下游写委派。

### 6.4 数据脱敏

维护端列表不返回患者信息；医生/护士 IF 仅在有 `encounterId` 时校验患者。

### 6.5 审计日志

| 场景 | 承诺 | 代码位置 | 关联步骤 |
| --- | --- | --- | --- |
| 头/行/生命周期写 | `operatorId` 写入 Command | `CurrentOperatorResolver` | §3.5～3.7、§3.10 |

---

## 7. 测试要点

### 7.1 功能测试

| 用例 | 预期 | 层级 | 优先级 |
| --- | --- | --- | --- |
| 维护端三 Tab 分页 | kind+MAINTAINED 过滤 | 集成 | P0 |
| 发布空壳 | EX-BAS-OTL-205 | 单元 | P0 |
| PRICING 保存药行 | EX-BAS-OTL-204 | 单元 | P0 |
| 医生站 Tab source 隔离 | MAINTAINED vs FROM_ADVICE | 集成 | P0 |
| IF-08 预览结构 | 仅头行 ids/数量/用法；价/库位由 prepicked 目录 IF | 集成 | P0 |
| 另存 FROM_ADVICE 直接 ACTIVE | 列表可见 | 集成 | P0 |
| 护士仅 PRICING 列表 | 无 FROM_ADVICE | 集成 | P0 |

### 7.2 非功能测试

| 用例 | 预期 | 层级 | 优先级 |
| --- | --- | --- | --- |
| 10 接口 POST 注册 | `/api/app/orderTemplate/*` | 集成 | P0 |
| IF-08 预览无 enrich | 不调用 ProductDefinition/ChargeItem 批量读 | 性能 | P1 |

### 7.3 查询结果一致性

| 用例 | 预期 | 优先级 |
| --- | --- | --- |
| 发布后医生站列表含模板 | ACTIVE 可见 | P0 |
| 停用后预览拒绝 | EX-BAS-OTL-302 | P0 |

### 7.4 集成测试

| 用例 | 预期 | 优先级 |
| --- | --- | --- |
| IF-08 预览后前端 saveDraftBatch | 下游 OP/IP-DOR 草稿可见 | P0 |
| IF-08 预览后前端 confirm*Pricing | 下游 IF-IP-PRC-05 可见 | P0 |

### 7.5 测试缺口跟踪

| 项 | 说明 | 优先级 |
| --- | --- | --- |
| UI E2E | 维护页/组套抽屉待 UI 落地后补 | P2 |

---

## 8. 变更历史

| 版本 | 日期 | 变更摘要 |
| --- | --- | --- |
| v1.9 | 2026-10-13 | **BR-OTL-APY-005**：通用/申请单组套套用后下游签发禁止二次带出；交叉 **AS-OP-DOR-003 · BR-ORD-038** / IF-DOR-23 `skipAutoBind` |
| v1.8 | 2026-06-20 | **orchestration/querysupport 落点对齐**：`OrderTemplateScopeResolver` → `module.common.validator.ordertemplate`；`OrderTemplateLineCatalogEnrichAssembler` + `OrderTemplateLineEnrichRow` → `module.querysupport.ordertemplate`；AppService enrich 边界 |（已迁至 application.shared.advice）（已迁至 application.shared / feature support（见 DISSOLUTION.md））
| v1.7 | 2026-06-18 | **`OrderTemplateBinding.AGREEMENT`**；`OrderTemplateBindingGroupDto#agreementList`；IF-07/11 四段列表；`OrderTemplateScopeResolver` 扩展 HOSPITAL/AGREEMENT |
| v1.6 | 2026-06-17 | **IF-BAS-OTL-01**：Response 增补 `tcmMethodCodeOptions`/`medicationRateOptions`（TCM Tab 行编辑；频次与 IF-DOR-18 同源）；对齐 `zxplat` |
| v1.5 | 2026-06-12 | **IF-BAS-OTL-10**：另存支持 PERSONAL / DEPARTMENT（缺省科室）；**BR-BAS-OTL-002** 修订 |
| v1.4 | 2026-06-11 | **IF-BAS-OTL-08 性能**：预览仅返回头行结构，**不** enrich/实时查价；套用链路改为 IF-08 → IF-DOR-11 `prepickedProductKeys` → IF-DOR-14 回传 `dispenseLocationId`；删除 `OrderTemplateLinePriceEnrichsupport |
| v1.3 | 2026-06-11 | 废止 IF-09/12；~~IF-08 全上下文 `OrderTemplateLinePriceEnrichsupport 实时查价~~（**v1.4 已回退**） |
| v1.2 | 2026-06-11 | IF-07～11 落地；IF-10 固定 **DEPARTMENT** |
| v1.1 | 2026-06-03 | 对齐 IF-01～06 已落地代码：`PagedResult`/`keyword`/`PageWindowGate`/`PagedResultBuilder`；`OrderTemplateConstants` 常量落点；`LineDiffResult`；`toCreateOrderTemplateCommand` 三参；五类 Resolver 替代 App 直读会话；移除 `OrderTemplateAppConstants`；引用 DM **v1.4.1** `pageOrderTemplates` 可选筛选 |
| v1.0 | 2026-06-03 | 首版：对齐 AS-BAS-MST-017 IF-01～12；目标态 `basic/ordertemplate` 包结构与编排序列；套用委派医生站/护士站既有 AppService |
