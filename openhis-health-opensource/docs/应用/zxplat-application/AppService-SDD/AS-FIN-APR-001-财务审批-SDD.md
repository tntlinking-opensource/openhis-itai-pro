# 财务管理 · 财务审批 · 应用服务详细设计（SDD）

> **对应 AS**：`AS-FIN-APR-001-财务审批.md`（**v0.2.3**）  
> **编码**：AS-FIN-APR-001-SDD · **版本**：v0.2.4 · **日期**：2026-07-26  
> **说明**：本 SDD **不维护 Gap 表**；领域缺口 **GAP-APR-001～002 已关闭**（见 AS §3）。统一列表跨聚合分页由 `FinanceApprovalDocumentPageLoader` 按 DM §6.2.14 / 调价 §6.2 契约实现（应用层成码阶段）。  
> **实现阶段口径**：**目标态全量契约**（应用层已按本 SDD 生成；领域白名单与列表过滤已就绪）。

---

## 1. 实现边界

### 1.1 目标与边界

| 项 | 内容 |
| --- | --- |
| **模块** | 财务管理 · 财务审批（`finance/approval`；前端 `views/finance/approval`） |
| **菜单** | **一级「财务管理」** → **二级「财务审批」** |
| **边界** | IF-APR-01～04：初始化、统一列表、同意、驳回；详情读**不**在本模块新建 HTTP，前端复用各业务详情 IF |
| **编排权威** | `AS-FIN-APR-001` §2、§4 |
| **方法权威** | `DM-WKF-006` §6.1 agree/reject + §6.2 列表；`DM-CUS-011` §6.1 approve/reject + §6.2 列表；各 INV 业务 AS 同意后下游编排 |
| **非目标** | 建单/改单/提交/撤回/删除；导出；批量审；后端默认写死进货/待审 |
| **严格对齐** | IF **IF-APR-01～04** |
| **实现阶段口径** | **目标态全量契约** |

### 1.2 术语口径

| 项 | 约定 |
| --- | --- |
| **HTTP** | 一律 `POST /api/app/financeApproval/{methodName}` |
| **包路径** | `application.finance.approval` |
| **分页** | `pageNo` **1-based**；出参 `content` / `totalCount`；默认 `pageSize=50` |
| **时间** | 制单时间窗、审批时间窗：`yyyy-MM-dd HH:mm:ss`；供应单制单窗映射行 `occurrence_time` 聚合键（与进货列表同口径）；调价制单窗映射 `adjustment_time` |
| **documentKind** | `SUPPLY`：供应请求财务族；`PRICE_ADJUSTMENT`：调价单 |
| **typeEnum（列表）** | 空/`ALL`=全部；供应码=§1.3 白名单之一；`PRICE_ADJUSTMENT`=仅调价（应用层判别码，**非** `SupplyType`） |
| **待审映射** | 供应：`SupplyStatus.APPROVAL`；调价：`PriceAdjustmentStatus.PENDING`（code `pending`） |
| **默认筛选** | **禁止** AppService 在 Request 缺省时写入进货或待审；仅透传入参 |
| **事务** | 同意/驳回：与被分发业务路径同事务边界（单 `bus_no` 一次写） |
| **前端 HTTP** | `publicRequestClient` 解包标准响应 `data` |

---

## 2. 代码结构

### 2.1 包路径（类级清单）

```text
zxplat/health-application/src/main/java/com/openhis/health/application/finance/approval/
  service/
    FinanceApprovalAppService.java
  support/
    FinanceApprovalTypeSupport.java
    FinanceApprovalDocumentPageLoader.java
    FinanceApprovalAgreeOrchestration.java
    FinanceApprovalRejectOrchestration.java
    FinanceApprovalAmountAssembler.java
  mapper/
    FinanceApprovalMapper.java
    FinanceApprovalMapperImpl.java
  request/
    InitFinanceApprovalRequest.java
    PageFinanceApprovalDocumentRequest.java
    AgreeFinanceApprovalRequest.java
    RejectFinanceApprovalRequest.java
  response/
    InitFinanceApprovalResponse.java
    PageFinanceApprovalDocumentResponse.java
    AgreeFinanceApprovalResponse.java
    RejectFinanceApprovalResponse.java
  dto/
    FinanceApprovalDocumentRowDto.java

zxplat/zxplat-domain/src/main/java/com/openhis/health/domain/share/constants/
  FinanceApprovalConstants.java
```

> 包名：`com.zxplat.domain.application.finance.approval`  
> `OptionDto` 使用 zxplat-framework 公共库，**不**列入本模块 dto/。  
> 同意后进货/退货/损益/盘点/调价的发放、库存、落价等，经 `FinanceApprovalAgreeOrchestration` **复用**各业务已落地编排步骤（或抽取同源 support），**禁止**在本包复制一套无关语义的新动账规则。

### 2.2 依赖注入表

| 依赖 | 用途 |
| --- | --- |
| `FinanceApprovalRoleGate`（`FinanceApprovalAuthorizer`） | IF-APR-03/04 Gate；IF-APR-01 可投影 `canFinanceApprove`（可选，前端亦有登录配置） |
| `SupplyRequestQueryService` | 列表/解析供应单头；`countDistinctBusNo` / `listDistinctBusNoPage` / `listByBusNosWithFilter`（或工程等价方法名） |
| `PriceAdjustmentQueryService` | 调价列表：`countDistinctBusNo` / `listDistinctBusNoPage` / `listLinesByBusNosWithFilter`（与 PRC-001 列表同构） |
| `SupplyRequestDomainService` | 供应同意/驳回 Cmd |
| `PriceAdjustmentDomainService` | 调价 approve/reject Cmd |
| `InventoryItemDomainService` | 调价解锁；供应同意后库存（经编排） |
| `ChargeItemDefinition` 写侧（与 PRC-001 同源 orchestration） | 调价同意落价 |
| 各 INV 同意编排已用 Bean（发放/盘点发放/追溯等） | 按类型复用，白名单见 §2.4 |
| `LocationQueryService` / `SupplierQueryService` / `PractitionerQueryService` | IF-APR-01 选项；列表 enrich |
| `CurrentManagedLocationIdsResolver` | 库房选项收窄 |
| `FinanceApprovalDocumentPageLoader` | IF-APR-02 跨源分页 |
| `FinanceApprovalAgreeOrchestration` / `FinanceApprovalRejectOrchestration` | IF-APR-03/04 分发 |
| `FinanceApprovalAmountAssembler` | 列表金额列 |
| `FinanceApprovalMapper` | DTO 装配 |
| `CurrentOperatorResolver` | 审批人会话解析 |

### 2.3 枚举、字典与值集（DM 与代码可追溯）

| 类别 | 代码实现锚点 | DM 追溯锚点 | 说明 |
| --- | --- | --- | --- |
| 供应类型·进货 | `com.zxplat.domain.share.enums.SupplyType.CABINET_INBOUND_RECEIPT` | `DM-WKF-006` §4.1 type | IF-APR-01/02/03/04 |
| 供应类型·退供应商 | `SupplyType.CABINET_RETURN_TO_SUPPLIER` | `DM-WKF-006` §4.1 | 财务族 |
| 供应类型·损益 | `SupplyType.CABINET_GAIN_LOSS` / `PHARMACY_GAIN_LOSS` / `DEPT_GAIN_LOSS` | `DM-WKF-006` §4.1 | 财务族 |
| 供应类型·盘点 | `SupplyType.CABINET_STOCKTAKE` / `PHARMACY_STOCKTAKE` / `DEPT_STOCKTAKE` | `DM-WKF-006` §4.1 | 财务族；不含已废止的 `DEPT_DEVICE_STOCKTAKE` |
| 财务门控白名单 | `SupplyType.requiresFinanceApproval` | `DM-WKF-006` 财务审批表述（待回写） | 须与 AS §1.3 一致 |
| 供应待审 | `com.zxplat.domain.share.enums.SupplyStatus.APPROVAL` | `DM-WKF-006` §5 状态机 | 列表/写门控 |
| 供应已同意 | `SupplyStatus.AGREE` | `DM-WKF-006` §5 | 同意后 |
| 供应已驳回 | `SupplyStatus.REJECT` | `DM-WKF-006` §5 | 驳回后 |
| 调价待审 | `com.zxplat.domain.share.enums.PriceAdjustmentStatus.PENDING` | `DM-CUS-011` §4.1 / §5 | code `pending` |
| 调价已生效 | `PriceAdjustmentStatus.APPROVED` | `DM-CUS-011` §4.1 | `approved` |
| 调价已驳回 | `PriceAdjustmentStatus.REJECTED` | `DM-CUS-011` §4.1 | `rejected` |
| 品类·药品表 | `PageFinanceApprovalDocumentRequest.itemTable`=`med_medication_definition` | `DM-WKF-006` §3 行 `item_table`；`DM-CUS-011` §4.2 | 品类筛 |
| 品类·耗材表 | `itemTable`=`adm_device_definition` | 同上 | 品类筛 |
| 品类·诊疗活动 | `itemTable`=`wkf_activity_definition` | `DM-CUS-011` §4.2；对齐调价 clinical | 品类筛/列表展示 |
| 品类·医疗服务 | `itemTable`=`adm_healthcare_service` | 同上 | 品类筛/列表展示 |
| 列表判别·调价 | `FinanceApprovalConstants.TYPE_PRICE_ADJUSTMENT`=`PRICE_ADJUSTMENT` | **AS-FIN-APR-001** §1.3 | 非 SupplyType |
| 列表判别·全部 | `FinanceApprovalConstants.TYPE_ALL`=`ALL` | **AS-FIN-APR-001** §1.3 | 前端可选 |
| 文档种类 | `AgreeFinanceApprovalRequest.documentKind` | **AS-FIN-APR-001** §1.3 | `SUPPLY`/`PRICE_ADJUSTMENT` |
| 平台财务角色 | `WhalePlatformRoleCodeConstants.ROLE_ACCOUNTANT` | 平台角色；AS §1.2 | Gate |
| 领域错误·无财务权限 | `ErrorCodeConstants.FINANCE_APPROVAL_ROLE_REQUIRED` | AS §5 | 写失败 |
| 默认页大小 | `FinanceApprovalConstants.DEFAULT_PAGE_SIZE` | **AS-FIN-APR-001** **BR-APR-006** 旁路 | **50**；目标态待生成 |

### 2.4 领域方法与 Mapper 白名单

**领域读（QueryService）**

| 类型 | 方法 | 用于 IF |
| --- | --- | --- |
| `SupplyRequestQueryService` | `countDistinctBusNo` | IF-APR-02 |
| `SupplyRequestQueryService` | `listDistinctBusNoPage` | IF-APR-02 |
| `SupplyRequestQueryService` | `listByBusNosWithFilter`（或工程等价 `listLinesByBusNosWithFilter`） | IF-APR-02 |
| `PriceAdjustmentQueryService` | `countDistinctBusNo` | IF-APR-02 |
| `PriceAdjustmentQueryService` | `listDistinctBusNoPage` | IF-APR-02 |
| `PriceAdjustmentQueryService` | `listLinesByBusNosWithFilter` | IF-APR-02 |
| `LocationQueryService` | `listAllActiveWarehouses` / `listAllActivePharmacies` / `listAllActiveDepartmentPharmacies`（以工程既有为准） | IF-APR-01 |
| `SupplierQueryService` | 启用供应商列表（工程既有方法） | IF-APR-01 |
| `PractitionerQueryService` | `findAllByIds` / 选项列表方法 | IF-APR-01、02 |

**领域写 / 编排复用**

| 类型 | 方法 | 用于 IF |
| --- | --- | --- |
| `FinanceApprovalAuthorizer` | `assertFinanceApprovalRole` | IF-APR-03、04 |
| `FinanceApprovalAuthorizer` | `assertFinanceApprovalForSupplyType` | IF-APR-03、04（供应） |
| `SupplyRequestDomainService` | `agreeRequest(AgreeRequestCommand)` | IF-APR-03 |
| `SupplyRequestDomainService` | `rejectRequest(RejectRequestCommand)` | IF-APR-04 |
| `PriceAdjustmentDomainService` | `approvePriceAdjustment(ApprovePriceAdjustmentCommand)` | IF-APR-03 |
| `PriceAdjustmentDomainService` | `rejectPriceAdjustment(RejectPriceAdjustmentCommand)` | IF-APR-04 |
| `InventoryItemDomainService` | `unlockByBusNo`（及各 INV 同意路径既有增减方法） | IF-APR-03、04 |
| 进货/退货/损益/盘点同意下游 | 与对应业务 AppService 同意路径**同序同 Command**（发放/定价/追溯等） | IF-APR-03 |
| 调价同意落价 | 与 `AS-FIN-PRC-001` IF-PRC-13 同源 orchestration | IF-APR-03 |

**Support**

| 类型 | 方法 | 用于 IF |
| --- | --- | --- |
| `FinanceApprovalTypeSupport` | `isFinanceSupplyType` / `resolveDocumentKind` / `listTypeOptions` | IF-APR-01～04 |
| `FinanceApprovalDocumentPageLoader` | `page` | IF-APR-02 |
| `FinanceApprovalAmountAssembler` | `fillAmounts`（供应明细 / 调价进价·售价盈亏分列重载） | IF-APR-02 |
| `FinanceApprovalAgreeOrchestration` | `agree` | IF-APR-03 |
| `FinanceApprovalRejectOrchestration` | `reject` | IF-APR-04 |

**Mapper**

| 类型 | 方法 | 用于 IF |
| --- | --- | --- |
| `FinanceApprovalMapper` | `toSupplyDocumentListQuery` / `toPriceAdjustmentDocumentListQuery` / `toRowDto`（供应头 / 调价头重载） / `toInitResponse` | IF-APR-01、02 |

---

## 3. 按接口 ID 命名展开

> **执行约定**：实现时先读完各 IF「领域编排序列」，按序号自上而下执行；**Pre/Gate 失败不得进入 Cmd**。

### 3.1 应用服务接口（方法白名单）

| IF | 应用服务方法（英文） | 中文方法名 | HTTP |
| --- | --- | --- | --- |
| IF-APR-01 | `init` | 初始化财务审批页 | `POST /api/app/financeApproval/init` |
| IF-APR-02 | `pageDocuments` | 分页查询财务审批单据 | `POST /api/app/financeApproval/pageDocuments` |
| IF-APR-03 | `agree` | 财务审批同意 | `POST /api/app/financeApproval/agree` |
| IF-APR-04 | `reject` | 财务审批驳回 | `POST /api/app/financeApproval/reject` |

#### 3.1.1 编排约定（应用层如何实现 AS §4）

| 阶段 | 含义 | 失败时 |
| --- | --- | --- |
| **Gate** | 权限 / 参数门禁 | 不再调用后续步骤 |
| **Pre** | 写前只读：加载快照、状态与 BR 门禁 | 抛业务异常，**禁止进入 Cmd** |
| **Cmd** | 领域写 + 下游编排 | 事务回滚见 §5 |
| **Asm** | Mapper 纯 DTO 装配 | — |

记法：`SupplyRequestQueryService#countDistinctBusNo` 表示对注入 Bean 调用该方法。

---

### 3.2 IF-APR-01 `init`（初始化财务审批页）

**Request**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| （无业务字段） | — | — | 允许空体；忽略客户端传入的默认 type/status |

**Response**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `typeOptions` | `List<OptionDto>` | 全部 + 财务族供应码 + `PRICE_ADJUSTMENT` |
| `statusOptions` | `List<OptionDto>` | 待审/已同意(已生效)/已驳回（展示文案统一，value 分域见 Asm 说明或前端映射表） |
| `locationOptions` | `List<OptionDto>` | 库房（药库/药房/科室药房并集，管辖收窄） |
| `supplierOptions` | `List<OptionDto>` | 供应商 |
| `itemTableOptions` | `List<OptionDto>` | 药品/耗材/诊疗活动/医疗服务四档 |
| `applicantOptions` | `List<OptionDto>` | 制单人：在职医务人员全量 |
| `approverOptions` | `List<OptionDto>` | 审批人：持有 `ROLE_ACCOUNTANT` 登录角色且绑定在职医务人员 |

**关键校验**：无。

**Mapper 接口方法签名**

- `toInitResponse(...): InitFinanceApprovalResponse`

**领域编排序列（严格顺序）**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | `CurrentOperatorResolver` 会话 | 登录上下文 | 未登录失败 |
| 2 | Pre | `FinanceApprovalTypeSupport#listTypeOptions` | — | 类型 Option |
| 3 | Pre | `LocationQueryService` 三类库位列表 + `CurrentManagedLocationIdsResolver#narrowActiveLocationsByManaged` | — | 库房 Option |
| 4 | Pre | `SupplierQueryService` 启用列表 | — | 供应商 Option |
| 5 | Asm | `FinanceApprovalMapper#toInitResponse` | 2～4 结果 | 仅读路径 |

---

### 3.3 IF-APR-02 `pageDocuments`（分页查询财务审批单据）

**Request**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `pageNo` | Integer | 是 | 1-based |
| `pageSize` | Integer | 是 | 默认 50 |
| `typeEnum` | String | 否 | 空/`ALL`/供应码/`PRICE_ADJUSTMENT`；**缺省不写死进货** |
| `statusEnum` | String | 否 | 空=不限；待审时供应侧传 `APPROVAL`、调价侧由 Loader 映射 `pending`（见序列分支）；**缺省不写死待审** |
| `busNo` | String | 否 | 模糊 |
| `locationId` | Long | 否 | 库房 |
| `supplierId` | Long | 否 | 供应商（调价可忽略） |
| `itemTable` | String | 否 | 药品/耗材/诊疗活动/医疗服务表名 |
| `applicantId` | Long | 否 | 制单人 |
| `approverId` | Long | 否 | 审批人 |
| `occurrenceTimeFrom` / `occurrenceTimeTo` | String | 否 | 制单时间窗 |
| `approvalTimeFrom` / `approvalTimeTo` | String | 否 | 审批时间窗 |

**Response**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `content` | `List<FinanceApprovalDocumentRowDto>` | 行 |
| `totalCount` | Long | 合并后总单数 |

**DTO `FinanceApprovalDocumentRowDto`**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `documentKind` | String | `SUPPLY` / `PRICE_ADJUSTMENT` |
| `busNo` | String | 单号 |
| `typeEnum` | String | 供应码或 `PRICE_ADJUSTMENT` |
| `typeEnumText` | String | 展示名 |
| `statusEnum` | String | 供应或调价状态码 |
| `statusEnumText` | String | 展示名 |
| `locationId` | Long | 库房 |
| `locationName` | String | 库房名 |
| `supplierId` | Long | 供应商（可空） |
| `supplierName` | String | 可空 |
| `itemTable` | String | 品类表名（可空） |
| `itemTableText` | String | 药品/耗材/诊疗活动/医疗服务 |
| `applicantId` | Long | 制单人 |
| `applicantName` | String | |
| `approverId` | Long | 审批人可空 |
| `approverName` | String | |
| `occurrenceTime` | String | 制单时间 |
| `applyTime` / `submitTime` | String | 申请/提交时间（分域） |
| `approvalTime` / `approveTime` | String | 审批时间 |
| `inboundAmount` | BigDecimal | 进货金额（方案 B / 进货类型） |
| `returnAmount` | BigDecimal | 退货金额 |
| `stocktakeActualAmount` | BigDecimal | 实盘金额 |
| `profitLossAmount` | BigDecimal | 盈亏金额（盘点/损益） |
| `priceAdjustmentPurchaseImpact` | BigDecimal | 调价进价盈亏（单号 `totalPurchaseImpact`；与售价分列） |
| `priceAdjustmentSellingImpact` | BigDecimal | 调价售价盈亏（单号 `totalSellingImpact`；与进价分列） |
| `rejectReason` | String | 驳回原因可空 |

**关键校验**：`typeEnum` 非空且非 `ALL`/`PRICE_ADJUSTMENT` 时须∈财务供应白名单；`itemTable` 非空则须∈药品/耗材/诊疗活动/医疗服务四档表名。

**失败码口径**

| 码 | 场景 |
| --- | --- |
| 参数错误 | 非法 type/itemTable |
| — | 空结果合法 |

**Mapper 接口方法签名**

- `toSupplyDocumentListQuery(PageFinanceApprovalDocumentRequest): SupplyRequestDocumentListQuery`
- `toPriceAdjustmentDocumentListQuery(PageFinanceApprovalDocumentRequest): PriceAdjustmentDocumentListQuery`
- `toRowDto(...): FinanceApprovalDocumentRowDto`

**领域编排序列（严格顺序）**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | 解析 Request；`FinanceApprovalTypeSupport` 校验 type/itemTable | — | 非法则失败 |
| 2 | Pre | `FinanceApprovalDocumentPageLoader#page` | Request | **若** `typeEnum` 为空或 `ALL`：供应财务族 ∪ 调价并集分页（依赖 **GAP-APR-002**）；**若** 供应单码：仅供应 QS；**若** `PRICE_ADJUSTMENT`：仅调价 QS。**禁止**在 Loader 内默认改写为进货/待审 |
| 3 | Pre | `FinanceApprovalAmountAssembler#fillAmounts` | 行 + 明细 | 按类型填金额字段 |
| 4 | Asm | `FinanceApprovalMapper#toRowDto` | — | 仅读路径 |

> **GAP-APR-002 已关闭**：供应/调价分域三件套 + 扩展过滤已落地；并集分页由本 Loader 实现（B.6.4），禁止在 Domain QS 跨聚合。

---

### 3.4 IF-APR-03 `agree`（财务审批同意）

**Request**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `busNo` | String | 是 | 单号 |
| `documentKind` | String | 是 | `SUPPLY` / `PRICE_ADJUSTMENT` |
| `typeEnum` | String | 条件 | `SUPPLY` 时必填，须∈财务供应白名单 |

**Response**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `busNo` | String | |
| `documentKind` | String | |
| `statusEnum` | String | 同意后状态码 |

**关键校验**：财务角色；白名单；待审态。

**失败码口径**

| 码 | 场景 |
| --- | --- |
| `FINANCE_APPROVAL_ROLE_REQUIRED` | 无财务角色 |
| 业务状态错误 | 非待审 / 类型非法 |
| 下游动账错误 | 与对应业务 AS 同意失败码对齐 |

**领域编排序列（严格顺序）**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | `FinanceApprovalAuthorizer#assertFinanceApprovalRole` | — | 失败终止 |
| 2 | Gate | 校验 `busNo`/`documentKind`；若 `SUPPLY`：`FinanceApprovalTypeSupport#isFinanceSupplyType` + `assertFinanceApprovalForSupplyType` | typeEnum | 非白名单失败 |
| 3 | Pre | 按 kind 加载单据头，断言待审 | busNo | 供应=`APPROVAL`；调价=`pending` |
| 4 | Cmd | `FinanceApprovalAgreeOrchestration#agree` | busNo, kind, type | **写库点**：内部分发。**若** `PRICE_ADJUSTMENT`：复用 **AS-FIN-PRC-001** IF-PRC-13 编排序列（落价→unlock→`approvePriceAdjustment`）；**若** `CABINET_INBOUND_RECEIPT`：复用 **AS-INV-WHS-002** IF-WHS-IN-08；**若** `CABINET_RETURN_TO_SUPPLIER`：复用 **AS-INV-WHS-005** IF-WHS-RT-08；**若** 损益三码：复用对应损益 AS 同意；**若** 盘点三码（药库/药房/科室）：复用对应盘点 AS 同意（含 `createStocktakingDelivery`）。互斥：只执行匹配分支 |
| 5 | Asm | 组装 `AgreeFinanceApprovalResponse` | — | |

---

### 3.5 IF-APR-04 `reject`（财务审批驳回）

**Request**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `busNo` | String | 是 | |
| `documentKind` | String | 是 | |
| `typeEnum` | String | 条件 | `SUPPLY` 时必填 |
| `rejectReason` | String | 条件 | 调价**必填**；供应按对应业务 AS（建议统一必填便于财务留痕） |

**Response**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `busNo` | String | |
| `documentKind` | String | |
| `statusEnum` | String | 驳回后状态 |

**关键校验**：同同意门控；调价 `rejectReason` 非空。

**失败码口径**

| 码 | 场景 |
| --- | --- |
| `FINANCE_APPROVAL_ROLE_REQUIRED` | 无财务角色 |
| `PRICE_ADJUSTMENT_*` / 供应驳回码 | 原因缺失、非待审等 |

**领域编排序列（严格顺序）**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | `assertFinanceApprovalRole` | — | |
| 2 | Gate | 白名单 / documentKind | — | |
| 3 | Pre | 加载并断言待审；调价断言原因非空 | — | |
| 4 | Cmd | `FinanceApprovalRejectOrchestration#reject` | — | **写库点**。**若** 调价：unlock（物流）→ `rejectPriceAdjustment(RejectPriceAdjustmentCommand)`；**若** 供应：`rejectRequest(RejectRequestCommand)`。互斥分支 |
| 5 | Asm | 组装 Response | — | |

---

## 4. QueryService 与领域事件

| 写侧事件 | 触发 | 读侧刷新 |
| --- | --- | --- |
| `SupplyRequestAgreedEvent` / 驳回事件 | IF-APR-03/04 供应分支 | 财务审批列表、原业务列表 |
| `PriceAdjustmentApprovedEvent` / `PriceAdjustmentRejectedEvent` | IF-APR-03/04 调价分支 | 财务审批列表、调价列表、`existsPendingByItem` 消费方 |

列表读不直查 Repository；统一经 `*QueryService` 或 **GAP-APR-002** 落地后的队列方法。

---

## 5. 写库顺序（核心写接口视角）

### 5.1 Cmd 落库顺序一览

| IF | App 方法 | Cmd 序号 | 调用 | 聚合写入顺序 |
| --- | --- | --- | --- | --- |
| IF-APR-03 | `agree` | §3.4 序号 4 | 分发同意编排 | 先领域同意态，再下游发放/库存/定价（与被复用 AS 一致；调价为先落价再 approve 或按 PRC-001 既定序） |
| IF-APR-04 | `reject` | §3.5 序号 4 | 分发驳回编排 | 调价：先 unlock 再 reject；供应：rejectRequest 写驳回态 |

### 5.2 事务边界 / 失败回滚

| 项 | 约定 |
| --- | --- |
| **默认** | 单次同意/驳回同一本地事务；下游任一步失败整单回滚 |
| **禁止** | 头表已同意但发放/库存未完成的对外可见中间态（与进货 AS 一致） |

---

## 6. 非功能性需求实现（收敛为承诺表）

### 6.1 幂等性

| 项 | 内容 |
| --- | --- |
| **代码位置（必须）** | `FinanceApprovalAgreeOrchestration` / 领域 `agreeRequest` 防重复 |
| **关联步骤** | §3.4 序号 3～4 |

### 6.2 并发控制

| 项 | 内容 |
| --- | --- |
| **代码位置（必须）** | 各复用同意路径既有行版本/防重复发放 |
| **关联步骤** | §3.4 序号 4 |

### 6.3 重试与容错

| 项 | 内容 |
| --- | --- |
| **代码位置（必须）** | 写失败抛业务异常；不自动重试动账 |
| **关联步骤** | §3.4～3.5 |

### 6.4 数据脱敏

| 项 | 内容 |
| --- | --- |
| **代码位置（必须）** | 本页无额外敏感字段；金额明文 |
| **关联步骤** | — |

### 6.5 审计日志

| 项 | 内容 |
| --- | --- |
| **代码位置（必须）** | 领域写入 `approver_id` / `approval_time`（供应）或 `approver_id` / `approve_time`（调价） |
| **关联步骤** | §3.4 序号 4；§3.5 序号 4 |

---

## 7. 测试要点

### 7.1 功能测试

| 用例 | 层级 | 自动化归属 | 优先级 |
| --- | --- | --- | --- |
| init 选项含财务族+调价+全部 | 接口 | 应用层 | P0 |
| pageDocuments 缺省 type/status 不默认进货待审 | 接口 | 应用层 | P0 |
| 仅进货+待审筛选正确 | 接口 | 应用层 | P0 |
| 类型空/全部时供应金额/实盘/盈亏/进价盈亏/售价盈亏分列有值口径；调价禁止加总 | 接口 | 应用层 | P1 |
| 财务角色同意进货/退货/损益/盘点/调价 | 接口 | 应用层 | P0 |
| 无财务角色 agree/reject 拒绝 | 接口 | 应用层 | P0 |
| 非白名单类型拒绝 | 接口 | 应用层 | P0 |

### 7.2 非功能测试

| 用例 | 层级 | 自动化归属 | 优先级 |
| --- | --- | --- | --- |
| 同意失败整单回滚 | 接口 | 应用层 | P0 |
| 并集分页总数正确（Gap 关闭后） | 接口 | 应用层 | P0 |

### 7.3 查询结果一致性

| 用例 | 层级 | 自动化归属 | 优先级 |
| --- | --- | --- | --- |
| 同意后原业务详情状态与本列表一致 | 集成 | 手工/集成 | P0 |

### 7.4 集成测试

| 用例 | 层级 | 自动化归属 | 优先级 |
| --- | --- | --- | --- |
| 进货同意后库存增加 | 集成 | 集成 | P0 |
| 调价同意后定价 ACTIVE 切换 | 集成 | 集成 | P0 |

### 7.5 测试缺口跟踪

| 项 | 说明 |
| --- | --- |
| GAP-APR-001/002 | **已关闭**；应用层 Loader/AppService 成码不再阻塞 |

---

## 8. 变更历史

| 版本 | 日期 | 摘要 |
| --- | --- | --- |
| v0.2.4 | 2026-07-26 | 前端金额列表头随 `typeEnum` 动态切换（调价仅进价/售价盈亏）；对齐 AS v0.2.3 |
| v0.2.3 | 2026-07-26 | 调价列表金额改为 `priceAdjustmentPurchaseImpact` / `priceAdjustmentSellingImpact` 分列；对齐 AS v0.2.2 前端默认类型空 |
| v0.2.2 | 2026-07-21 | 财务审批族盘点去掉已废止的 `DEPT_DEVICE_STOCKTAKE`（与 AS §1.3 / `SupplyType#requiresFinanceApproval` 对齐） |
| v0.2.1 | 2026-07-21 | §2.4 Mapper/AmountAssembler 方法名与 §3.3 及成码对齐（`toSupplyDocumentListQuery`/`toRowDto`/`fillAmounts` 重载） |
| v0.2 | 2026-07-21 | 对齐 AS v0.2：GAP-APR-001/002 已关闭；列表过滤与白名单以领域实现为准 |
| v0.1 | 2026-07-21 | 初稿：目标态全量契约；IF-APR-01～04；分发复用各业务同意/驳回；§2.3 双锚；依赖 AS Gap-APR-001/002 |
