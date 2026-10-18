# 财务管理 · 调价明细 · 应用服务详细设计（SDD）

> **对应 AS**：`AS-FIN-PRC-002-调价明细.md`（**v1.2**）  
> **编码**：AS-FIN-PRC-002-SDD · **版本**：v1.2 · **日期**：2026-07-31  
> **说明**：本 SDD **不维护 Gap 表**；领域读能力见 AS §3（**GAP-PRC-DTL-001～002 已关闭**）；**应用层 IF-PRC-DTL-01～03 已落地**。  
> **运维**：朝阳期初后清库保留 `cus_price_adjustment*`（中药入库自动调价审计）。

---

## 1. 实现边界

### 1.1 目标与边界

| 项 | 内容 |
| --- | --- |
| **模块** | 财务管理 · 调价明细（`finance/priceadjustmentdetail`；前端 `views/finance/price-adjustment-detail`） |
| **菜单** | **一级「财务管理」** → **二级「调价明细」**（与「价格调整」并列） |
| **边界** | IF-PRC-DTL-01～03：只读查询；按**已生效项目主行**分页；详情含子表批号明细 |
| **编排权威** | `AS-FIN-PRC-002` §2、§4 |
| **方法权威** | `DM-CUS-011` §6.2 `PriceAdjustmentQueryService`；目录 enrich 对齐 **AS-FIN-PRC-001** 读侧惯例 |
| **非目标** | 制单/提交/审批（**AS-FIN-PRC-001**）；导出；草稿/待审批查询；列表展示调前/调后价 |
| **严格对齐** | IF **IF-PRC-DTL-01～03** |
| **实现阶段口径** | **目标态全量契约**：领域读侧、querysupport 关键字解析、应用层 `priceadjustmentdetail/**` **均已落地** |

### 1.2 术语口径

| 项 | 约定 |
| --- | --- |
| **HTTP** | 一律 `POST /api/app/priceAdjustmentDetail/{methodName}`（`AppServiceControllerRegistrar`） |
| **包路径** | `application.finance.priceadjustmentdetail`（与 PRC-001 制单包分离；共享 `ProductDefinitionBatchLoader` 等 querysupport） |
| **分页** | `pageNo` **1-based**；`PagedResult` 出参 `content` / `totalCount`；默认 `pageSize=20` |
| **时间** | Request `approveTimeFrom`/`approveTimeTo` 为 `yyyy-MM-dd HH:mm:ss`（`GeneralSharedConstants.DATE_TIME_PATTERN`）；映射 `PriceAdjustmentApprovedLineListQuery.startDate`/`endDate` → 域 **`approve_time` 闭区间** |
| **盈亏** | `totalImpact` = 读模型已固化 Σ(`purchase_impact`)+Σ(`selling_impact`)；**禁止** App 用调后−调前重算 |
| **列表金额** | 列表 DTO **不含**调前/调后价字段（**BR-PRC-DTL-008**）；详情 DTO **含**主行目标价与子表调前价 |
| **事务** | 本模块**只读**；AppService **禁止**声明写事务 |
| **租户** | 租户隔离由框架/JPA 多租户完成；**无**额外 org/个人 Gate |
| **前端 HTTP** | 须 `publicRequestClient` 解包标准响应 `data` |

---

## 2. 代码结构

### 2.1 包路径（类级清单）

```text
zxplat/health-application/src/main/java/com/openhis/health/application/finance/priceadjustmentdetail/
  service/
    PriceAdjustmentDetailAppService.java
  mapper/
    PriceAdjustmentDetailMapper.java
    PriceAdjustmentDetailMapperImpl.java
  request/
    InitPriceAdjustmentDetailRequest.java
    PagePriceAdjustmentApprovedLineRequest.java
    GetPriceAdjustmentApprovedLineRequest.java
  response/
    InitPriceAdjustmentDetailResponse.java
    PagePriceAdjustmentApprovedLineResponse.java
    GetPriceAdjustmentApprovedLineResponse.java
  dto/
    PriceAdjustmentApprovedLineListRowDto.java

zxplat/health-application/src/main/java/com/openhis/health/application/financial/priceadjustment/support/
  PriceAdjustmentDetailItemKeywordSupport.java

zxplat/zxplat-domain/src/main/java/com/openhis/health/domain/custom/priceadjustment/queries/
  PriceAdjustmentApprovedLineListQuery.java

zxplat/zxplat-domain/src/main/java/com/openhis/health/domain/share/constants/
  PriceAdjustmentDetailConstants.java
```

> 包名：`com.zxplat.domain.application.finance.priceadjustmentdetail`  
> 详情子表复用 PRC-001 已存在 DTO：`com.zxplat.domain.application.finance.priceadjustment.dto.PriceAdjustmentDetailDto`（**不**在 `priceadjustmentdetail/dto` 重复定义）。  
> `OptionDto` 使用 zxplat-framework 公共库。

### 2.2 依赖注入表

| 依赖 | 用途 |
| --- | --- |
| `PriceAdjustmentQueryService` | IF-DTL-02/03：`countApprovedLines`、`pageApprovedLines`、`getApprovedLineById` |
| `PriceAdjustmentDetailItemKeywordsupport | IF-DTL-02：项目名/编码 → `item_id` 集合（**BR-PRC-DTL-005**） |
| `ProductDefinitionBatchLoader` | IF-DTL-02/03：批量 enrich 项目名/院内编码 |
| `PractitionerQueryService` | IF-DTL-02/03：制单人/审批人展示名 |
| `PriceAdjustmentDetailMapper` | Request ↔ Query / Response 装配 |
| `PriceAdjustmentMapper`（PRC-001，只读复用） | IF-DTL-03：`toLineDto`（含子表映射）；**禁止**循环依赖写 IF |
| `CurrentOperatorResolver` | IF-DTL-01～03 Gate（登录上下文；不做数据权限收窄） |

### 2.3 枚举、字典与值集（DM 与代码可追溯）

| 类别 | 代码实现锚点 | DM 追溯锚点 | 说明 |
| --- | --- | --- | --- |
| 纳入状态 | `com.zxplat.domain.share.enums.PriceAdjustmentStatus.APPROVED` | `DM-CUS-011` §4.1 | 硬过滤 `approved`；IF-DTL-02/03 |
| 项目物理表 | `PagePriceAdjustmentApprovedLineRequest.itemTable` | `DM-CUS-011` §4.2 `item_table` | 三档合法值（药/耗/诊疗）；空=并集 |
| 展示口径 | `PriceAdjustmentApprovedLineListRowDto.adjustmentStyle` | `DM-CUS-011` §4.2 `adjustmentStyle` | `logistics`/`clinical`；由 `PriceAdjustmentItemTablesupport 投影 |
| 调价原因 | `PriceAdjustmentApprovedLineListRowDto.adjustmentReason` | `DM-CUS-011` §3.1 `adjustment_reason`；§4.4 | 存自由文案；`adjustmentReasonText` 同文案（历史 code 过渡映射） |
| 批号占位 | `com.zxplat.domain.share.constants.CustomConstants.PriceAdjustment.CLINICAL_DETAIL_LOT_PLACEHOLDER` | `DM-CUS-011` §3.2 `lot_number` | 诊疗子表 `-` |
| 默认分页 | `PriceAdjustmentDetailConstants.DEFAULT_PAGE_SIZE` | **AS-FIN-PRC-002** **BR-PRC-DTL-009** | **20** |
| 业务消息 | `PriceAdjustmentDetailConstants.EX_PRC_DTL_*` | **AS-FIN-PRC-002** §5 | **101～104** 映射用户提示 |
| 领域错误码 | `com.zxplat.domain.share.constants.ErrorCodeConstants.PRICE_ADJUSTMENT_*` | `DM-CUS-011` §9.4 | `NOT_FOUND`/`INVALID_ARGUMENT` 等 |

### 2.4 领域方法与 Mapper 白名单

**领域读（QueryService）**

| 类型 | 方法 | 用于 IF |
| --- | --- | --- |
| `PriceAdjustmentQueryService` | `countApprovedLines` | IF-PRC-DTL-02 |
| `PriceAdjustmentQueryService` | `pageApprovedLines` | IF-PRC-DTL-02 |
| `PriceAdjustmentQueryService` | `getApprovedLineById` | IF-PRC-DTL-03 |

**Support（querysupport）**

| 类型 | 方法 | 用于 IF |
| --- | --- | --- |
| `PriceAdjustmentDetailItemKeywordsupport | `resolveItemIds` | IF-PRC-DTL-02 |
| `ProductDefinitionBatchLoader` | `loadAsMap` | IF-PRC-DTL-02、03 |
| `PractitionerQueryService` | `findAllByIds` | IF-PRC-DTL-02、03 |

**Mapper**

| 类型 | 方法 | 用于 IF |
| --- | --- | --- |
| `PriceAdjustmentDetailMapper` | `toInitPriceAdjustmentDetailResponse` | IF-PRC-DTL-01 |
| `PriceAdjustmentDetailMapper` | `toPriceAdjustmentApprovedLineListQuery` | IF-PRC-DTL-02 |
| `PriceAdjustmentDetailMapper` | `toPagePriceAdjustmentApprovedLineResponse` | IF-PRC-DTL-02 |
| `PriceAdjustmentDetailMapper` | `toPriceAdjustmentApprovedLineListRowDto` | IF-PRC-DTL-02 |
| `PriceAdjustmentDetailMapper` | `toGetPriceAdjustmentApprovedLineResponse` | IF-PRC-DTL-03 |
| `PriceAdjustmentMapper` | `toLineDto(PriceAdjustmentLineQueryModel, Map<String, ProductDefinitionDisplayDto>)` | IF-PRC-DTL-03（复用 PRC-001 签名） |

---

## 3. 按接口 ID 命名展开

**导语**：先读完各 IF 的「领域编排序列」，按序号自上而下执行；本模块**无 Cmd**（只读）。

### 3.1 应用服务接口（方法白名单）

| IF | 应用服务方法（英文） | 中文方法名 | HTTP |
| --- | --- | --- | --- |
| IF-PRC-DTL-01 | `initPriceAdjustmentDetail` | 调价明细页初始化 | `POST /api/app/priceAdjustmentDetail/initPriceAdjustmentDetail` |
| IF-PRC-DTL-02 | `pagePriceAdjustmentApprovedLine` | 分页查询调价明细 | `POST /api/app/priceAdjustmentDetail/pagePriceAdjustmentApprovedLine` |
| IF-PRC-DTL-03 | `getPriceAdjustmentApprovedLine` | 查看调价项目详情 | `POST /api/app/priceAdjustmentDetail/getPriceAdjustmentApprovedLine` |

### 3.1.1 编排约定

| 阶段 | 含义 | 失败时 |
| --- | --- | --- |
| **Gate** | 登录、日期区间、分页、主键非空 | 停止，抛 EX-PRC-DTL-* / `BusinessException` |
| **Pre** | 只读：关键字预解析、域分页/详情 QueryService | 抛业务异常，**无 Cmd** |
| **Asm** | Mapper 装配 Response；批量 enrich | enrich 失败 → EX-PRC-DTL-103 |

---

### 3.2 IF-PRC-DTL-01 `initPriceAdjustmentDetail`（调价明细页初始化）

| 项 | 内容 |
| --- | --- |
| **HTTP** | `POST /api/app/priceAdjustmentDetail/initPriceAdjustmentDetail` |

**Request `InitPriceAdjustmentDetailRequest`**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| — | — | — | 空体 |

**Response `InitPriceAdjustmentDetailResponse`**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `defaultApproveTimeFrom` | `String` | 兼容字段，固定不下发（前端自带当月） |
| `defaultApproveTimeTo` | `String` | 兼容字段，固定不下发（前端自带当月） |
| `itemTableTabOptions` | `List<OptionDto>` | 三档 `item_table` Tab（value=物理表名，label=中文；不含医疗服务） |
| `adjustmentReasonOptions` | `List<OptionDto>` | 兼容字段，固定空列表（原因手写） |
| `defaultPageSize` | `Integer` | 默认 **20** |

**关键校验**

| 规则 | 说明 |
| --- | --- |
| — | 无业务 Gate；枚举投影失败不阻断（前端可 code 回退） |

**失败码口径**

| 场景 | 错误码 / 消息 | 可重试 |
| --- | --- | --- |
| 枚举加载失败 | 降级空列表，不抛错 | — |

**Mapper 方法签名**

| 方法 | 签名 |
| --- | --- |
| `toInitPriceAdjustmentDetailResponse` | `toInitPriceAdjustmentDetailResponse(): InitPriceAdjustmentDetailResponse` |

**领域编排序列（严格顺序）**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | `CurrentOperatorResolver#requireOperatorId` | — | 仅确认登录上下文 |
| 2 | Asm | `PriceAdjustmentDetailMapper#toInitPriceAdjustmentDetailResponse` | — | 投影 `item_table` Tab；**默认生效时间不下发**（前端自带当月）；原因不下发枚举；**仅读路径** |

---

### 3.3 IF-PRC-DTL-02 `pagePriceAdjustmentApprovedLine`（分页查询调价明细）

| 项 | 内容 |
| --- | --- |
| **HTTP** | `POST /api/app/priceAdjustmentDetail/pagePriceAdjustmentApprovedLine` |

**Request `PagePriceAdjustmentApprovedLineRequest`**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `approveTimeFrom` | `LocalDateTime` | 是 | 生效时间下限（**BR-PRC-DTL-001**） |
| `approveTimeTo` | `LocalDateTime` | 是 | 生效时间上限；须 `≥ approveTimeFrom` |
| `itemTable` | `String` | 否 | 三档 `item_table` 之一；空=三类并集（**BR-PRC-DTL-004**） |
| `itemNameKeyword` | `String` | 否 | 项目名称模糊；与 `itemCodeKeyword` 双填 **AND**（**BR-PRC-DTL-005**） |
| `itemCodeKeyword` | `String` | 否 | 院内项目编码模糊 |
| `busNo` | `String` | 否 | 调价单号模糊（**BR-PRC-DTL-006**） |
| `pageNo` | `Integer` | 否 | 默认 1 |
| `pageSize` | `Integer` | 否 | 默认 20；上限 1000 |

**Response `PagePriceAdjustmentApprovedLineResponse`**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `content` | `List<PriceAdjustmentApprovedLineListRowDto>` | 当前页主行 |
| `pageNo` | `Integer` | 当前页（1-based） |
| `pageSize` | `Integer` | 页大小 |
| `totalCount` | `Long` | 主行总条数 |

**DTO `PriceAdjustmentApprovedLineListRowDto`**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | `Long` | 主行主键 |
| `busNo` | `String` | 调价单号 |
| `approveTime` | `Date` | 生效时间 |
| `itemTable` | `String` | 项目物理表 code |
| `itemTableText` | `String` | 项目类型展示名 |
| `itemId` | `Long` | 项目定义主键 |
| `itemName` | `String` | 项目名称（enrich） |
| `itemCode` | `String` | 院内编码（enrich） |
| `adjustmentStyle` | `String` | `logistics` / `clinical` |
| `adjustmentStyleText` | `String` | 展示口径文案 |
| `adjustmentReason` | `String` | 调价原因（自由文案） |
| `adjustmentReasonText` | `String` | 同文案（列表列） |
| `totalImpact` | `BigDecimal` | 盈亏合计（**禁止**列表返回调前/调后价） |
| `operatorId` | `Long` | 制单人 |
| `operatorName` | `String` | 制单人展示名 |
| `approverId` | `Long` | 审批人 |
| `approverName` | `String` | 审批人展示名 |

> **列表刻意省略**：`newPurchaseAmount`、`newSellingAmount`、子表 `details`（**BR-PRC-DTL-008**）。

**关键校验**

| 规则 ID | 说明 |
| --- | --- |
| BR-PRC-DTL-001 | 时间必填且合法 |
| BR-PRC-DTL-004 | `itemTable` 非空时须为三档合法值（药/耗/诊疗） |
| BR-PRC-DTL-005 | 关键字双填 AND；解析在 Support 完成 |
| BR-PRC-DTL-011 | 关键字命中空集 → 空页，非失败 |

**失败码口径**

| 场景 | 错误码 / EX | 用户提示 | 可重试 |
| --- | --- | --- | --- |
| 时间非法 | EX-PRC-DTL-102 | 请选择有效的生效日期 | 是 |
| 目录 enrich 失败 | EX-PRC-DTL-103 | 调价项目数据不完整… | 否 |
| 查询异常 | EX-PRC-DTL-104 | 调价明细查询失败… | 是 |
| `itemTable` 非法 | `PRICE_ADJUSTMENT_INVALID_ARGUMENT` | 参数错误 | 否 |

**Mapper 方法签名**

| 方法 | 签名 |
| --- | --- |
| `toPriceAdjustmentApprovedLineListQuery` | `toPriceAdjustmentApprovedLineListQuery(PagePriceAdjustmentApprovedLineRequest, List<Long> itemIds): PriceAdjustmentApprovedLineListQuery` |
| `toPriceAdjustmentApprovedLineListRowDto` | `toPriceAdjustmentApprovedLineListRowDto(PriceAdjustmentLineQueryModel, ProductDefinitionDisplayDto product, Map<Long, Practitioner> practitioners): PriceAdjustmentApprovedLineListRowDto` |
| `toPagePriceAdjustmentApprovedLineResponse` | `toPagePriceAdjustmentApprovedLineResponse(List<PriceAdjustmentApprovedLineListRowDto>, int pageNo, int pageSize, long totalCount): PagePriceAdjustmentApprovedLineResponse` |

**领域编排序列（严格顺序）**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | `CurrentOperatorResolver#requireOperatorId` | — | 登录 Gate |
| 2 | Gate | 校验 `approveTimeFrom`/`approveTimeTo`（**BR-PRC-DTL-001**） | Request | 非法 → EX-PRC-DTL-102 |
| 3 | Gate | 若 `itemTable` 非空：`PriceAdjustmentItemTableSupport#isSupportedItemTable` | Request.itemTable | 非法 → `INVALID_ARGUMENT` |
| 4 | Pre | 若 `itemNameKeyword` 或 `itemCodeKeyword` 非空：`PriceAdjustmentDetailItemKeywordSupport#resolveItemIds(itemTable, name, code)` | Request | 返回空集 → **跳至步骤 8** 返回空页（**BR-PRC-DTL-011**）；否则得 `itemIds` |
| 5 | Pre | `PriceAdjustmentDetailMapper#toPriceAdjustmentApprovedLineListQuery` | Request + `itemIds` | 映射 `approveTime*` → `startDate`/`endDate`；`busNo` → Query.busNo |
| 6 | Pre | `PriceAdjustmentQueryService#countApprovedLines(PriceAdjustmentApprovedLineListQuery)` | Query | 硬过滤 `approved` + `approve_time` 下推 |
| 7 | Pre | `PriceAdjustmentQueryService#pageApprovedLines(PriceAdjustmentApprovedLineListQuery)` | Query | 返回 `LineQueryModel`（`details` 空，`totalImpact` 已算） |
| 8 | Pre | `ProductDefinitionBatchLoader#loadAsMap(lookups)` | 当前页各行 `itemTable+itemId` | 批量 enrich |
| 9 | Pre | `PractitionerQueryService#findAllByIds(ids)`（AppService 内批量映射） | 制单/审批人 id 集 | 批量 enrich |
| 10 | Asm | `PriceAdjustmentDetailMapper#toPriceAdjustmentApprovedLineListRowDto` × N | 行 + product + practitioners | **禁止**填充调前/调后价 |
| 11 | Asm | `PriceAdjustmentDetailMapper#toPagePriceAdjustmentApprovedLineResponse` | rows + 分页元数据 | **仅读路径** |

---

### 3.4 IF-PRC-DTL-03 `getPriceAdjustmentApprovedLine`（查看调价项目详情）

| 项 | 内容 |
| --- | --- |
| **HTTP** | `POST /api/app/priceAdjustmentDetail/getPriceAdjustmentApprovedLine` |

**Request `GetPriceAdjustmentApprovedLineRequest`**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `id` | `Long` | 是 | 调价项目主行主键 |

**Response `GetPriceAdjustmentApprovedLineResponse`**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `line` | `PriceAdjustmentLineDto` | 主行 + 子表（复用 PRC-001 DTO） |

**DTO `PriceAdjustmentLineDto`（复用，本 IF 须完整返回）**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | `Long` | 主行主键 |
| `busNo` | `String` | 调价单号 |
| `itemTable` | `String` | 项目物理表 |
| `itemTableText` | `String` | 项目类型展示名 |
| `itemId` | `Long` | 项目定义主键 |
| `itemName` | `String` | 项目名称 |
| `newPurchaseAmount` | `BigDecimal` | 调后进价（物流；诊疗 null） |
| `newSellingAmount` | `BigDecimal` | 调后售价 |
| `adjustmentReason` | `String` | 原因文案 |
| `adjustmentReasonText` | `String` | 原因展示（同文案） |
| `adjustmentTime` | `Date` | 制单时间 |
| `statusEnum` | `String` | 固定 `approved` |
| `statusEnumText` | `String` | 已生效 |
| `operatorId` | `Long` | 制单人 |
| `orgId` | `Long` | 机构 |
| `adjustmentStyle` | `String` | 展示口径 |
| `adjustmentStyleText` | `String` | 口径文案 |
| `details` | `List<PriceAdjustmentDetailDto>` | 子表明细 |

**DTO `PriceAdjustmentDetailDto`（复用）**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | `Long` | 子表主键 |
| `lotNumber` | `String` | 批号（诊疗 `-`） |
| `quantity` | `BigDecimal` | 影响数量 |
| `oldPurchaseAmount` | `BigDecimal` | 调前进价 |
| `oldSellingAmount` | `BigDecimal` | 调前售价 |
| `purchaseImpact` | `BigDecimal` | 进价盈亏 |
| `sellingImpact` | `BigDecimal` | 售价盈亏 |

> Response 须额外在 Asm 层写入行级 `totalImpact`（可增 `GetPriceAdjustmentApprovedLineResponse.totalImpact` 字段，或约定前端对 `details` 分项求和 + 主行 DTO 扩展字段 `totalImpact` — **实现时二选一，推荐 Response 增 `totalImpact` 与列表口径一致**）。

**Response 补充字段（推荐）**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `totalImpact` | `BigDecimal` | 行级盈亏合计（与列表同口径） |
| `approveTime` | `Date` | 生效时间（列表主时间列） |
| `approverId` | `Long` | 审批人 |
| `approverName` | `String` | 审批人展示名 |

**关键校验**

| 规则 ID | 说明 |
| --- | --- |
| BR-PRC-DTL-010 | 仅 `approved` 主行 |
| BR-PRC-DTL-012 | enrich 失败整单失败 |

**失败码口径**

| 场景 | 错误码 / EX | 用户提示 | 可重试 |
| --- | --- | --- | --- |
| id 为空 | `PRICE_ADJUSTMENT_INVALID_ARGUMENT` | 参数错误 | 否 |
| 不存在或非 approved | EX-PRC-DTL-101 / `PRICE_ADJUSTMENT_NOT_FOUND` | 调价记录不存在或尚未生效 | 否 |
| enrich 失败 | EX-PRC-DTL-103 | 调价项目数据不完整… | 否 |

**Mapper 方法签名**

| 方法 | 签名 |
| --- | --- |
| `toGetPriceAdjustmentApprovedLineResponse` | `toGetPriceAdjustmentApprovedLineResponse(PriceAdjustmentLineDto line, BigDecimal totalImpact, Date approveTime, Long approverId, String approverName): GetPriceAdjustmentApprovedLineResponse` |
| `PriceAdjustmentMapper#toLineDto` | `toLineDto(PriceAdjustmentLineQueryModel, Map<String, ProductDefinitionDisplayDto>): PriceAdjustmentLineDto` |

**领域编排序列（严格顺序）**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | `CurrentOperatorResolver#requireOperatorId` | — | 登录 Gate |
| 2 | Gate | 校验 `id` 非空 | Request.id | 空 → `INVALID_ARGUMENT` |
| 3 | Pre | `PriceAdjustmentQueryService#getApprovedLineById(Long id)` | Request.id | 非 approved/不存在 → EX-PRC-DTL-101 |
| 4 | Pre | `ProductDefinitionBatchLoader#loadAsMap` | 单行 lookup | enrich 项目名 |
| 5 | Pre | `PractitionerQueryService#findAllByIds` | operator/approver | enrich 人员名 |
| 6 | Asm | `PriceAdjustmentMapper#toLineDto` | LineQueryModel + productMap | 含子表调前价与分项盈亏 |
| 7 | Asm | `PriceAdjustmentDetailMapper#toGetPriceAdjustmentApprovedLineResponse` | line DTO + totalImpact + approveTime | **仅读路径** |

---

### 3.5 Support：`PriceAdjustmentDetailItemKeywordSupport#resolveItemIds`

| 项 | 约定 |
| --- | --- |
| **入参** | `itemTable`（可空）、`itemNameKeyword`、`itemCodeKeyword` |
| **出参** | `List<Long>` 命中 `item_id`；无关键字时返回 **`null`**（Query 不按项目过滤） |
| **范围** | `itemTable` 空：在四表并集内分别查后 **UNION** id；非空：仅该表 |
| **AND 语义** | 名称与编码均非空：取两路命中集 **交集** |
| **实现** | 调 `ProductDefinitionCatalogPageLoader` 或各 `*DefinitionQueryService` 关键字检索（**禁止**在 `PriceAdjustmentQueryService` 内跨聚合查目录表） |
| **空集** | 返回 `Collections.emptyList()`；App 短路空页 |

---

## 4. QueryService 与领域事件

| 读场景 | QueryService | 组装规则 |
| --- | --- | --- |
| 明细列表计数 | `countApprovedLines` | Criteria + `PriceAdjustmentApprovedLineListPredicatesupport；`approved` 硬过滤 |
| 明细列表分页 | `pageApprovedLines` | `approve_time DESC, id DESC`；`totalImpact` 在 QS 内批量汇总子表 |
| 明细详情 | `getApprovedLineById` | 主行 + 全子表；非 approved 抛 `NOT_FOUND` |

| 事件 | 对本页影响 |
| --- | --- |
| `PriceAdjustmentApprovedEvent` | 用户刷新列表后可见新行；**无**推送 |
| 其它调价事件 | 不影响（非 `approved` 不纳入） |

---

## 5. 写库顺序（核心写接口视角）

本模块 **IF-PRC-DTL-01～03 均为只读**，**无 Cmd / 无写库点**。

| IF | App 方法 | Cmd | 事务 |
| --- | --- | --- | --- |
| IF-PRC-DTL-01 | `initPriceAdjustmentDetail` | — | 无 |
| IF-PRC-DTL-02 | `pagePriceAdjustmentApprovedLine` | — | 无 |
| IF-PRC-DTL-03 | `getPriceAdjustmentApprovedLine` | — | 无 |

**失败回滚**：不适用（只读无事务写）。

---

## 6. 非功能性需求实现

### 6.1 幂等性

| 项 | 承诺 | 代码位置 | 关联步骤 |
| --- | --- | --- | --- |
| 查询幂等 | 相同 Request 重复调用结果一致（以查询时刻快照为准） | `PriceAdjustmentDetailAppService` 各 public 方法 | §3.2 序号 6～7；§3.4 序号 3 |

### 6.2 并发控制

| 项 | 承诺 | 代码位置 | 关联步骤 |
| --- | --- | --- | --- |
| 无写竞争 | 只读无锁 | — | — |

### 6.3 重试与容错

| 项 | 承诺 | 代码位置 | 关联步骤 |
| --- | --- | --- | --- |
| 关键字空集 | 返回空页，不抛错 | IF-DTL-02 步骤 4 分支 | §3.3 序号 4→8 |
| enrich 失败 | 整单失败 EX-103 | AppService catch 统一包装 | §3.3 序号 8；§3.4 序号 4 |

### 6.4 数据脱敏

| 项 | 承诺 |
| --- | --- |
| 无 PII 扩展 | 仅展示业务项目/人员姓名；遵循租户隔离 |

### 6.5 审计日志

| 项 | 承诺 |
| --- | --- |
| 只读查询 | **不**写业务审计表；框架访问日志可选 |

---

## 7. 测试要点

### 7.1 功能测试

| 用例 | 预期 | 层级 | 优先级 |
| --- | --- | --- | --- |
| 进入页默认当月时间窗 | 前端自带当月；重置回当月 | 前端 | P0 |
| 仅 approved 命中 | pending/draft 不出现 | 集成 | P0 |
| approve_time 过滤 | 跨日区间正确 | 集成 | P0 |
| itemTable Tab | 三类各自收窄 | 集成 | P0 |
| 关键字 AND | 名称+编码双填交集 | 单元+集成 | P0 |
| 关键字无命中 | 空 content，totalCount=0 | 集成 | P0 |
| 列表无调前/调后 | DTO 无 `new*` 价字段 | 契约 | P0 |
| totalImpact | 与 DB 子表 impact 之和一致 | 集成 | P0 |
| 详情子表 | 物流多批号；诊疗 `-`/qty=1 | 集成 | P0 |
| 非 approved 详情 | EX-101 | 集成 | P0 |

### 7.2 非功能测试

| 用例 | 预期 | 优先级 |
| --- | --- | --- |
| 分页 20 条 | 默认 pageSize | P1 |
| 翻页稳定 | approve_time 降序 | P1 |

### 7.3 查询结果一致性

| 用例 | 预期 | 优先级 |
| --- | --- | --- |
| 审批通过后刷新 | PRC-001 approve 后本页可查 | P0 |
| totalImpact 不重算 | 与固化 impact 一致 | P0 |

### 7.4 集成测试

| 用例 | 预期 | 优先级 |
| --- | --- | --- |
| HTTP POST 路径 | `/api/app/priceAdjustmentDetail/*` | P0 |
| publicRequestClient | 前端解包 `data` | P1 |

### 7.5 测试缺口跟踪

| 缺口 | 说明 | 计划 |
| --- | --- | --- |
| E2E UI | 前端页未落地 | 联调阶段补 |

---

## 8. 变更历史

| 版本 | 日期 | 摘要 |
| --- | --- | --- |
| v1.2 | 2026-07-31 | 对齐 AS v1.2：朝阳期初后清库保留调价主从表 |
| v1.1 | 2026-06-27 | 应用层成码落地：`priceadjustmentdetail/**`、`PriceAdjustmentDetailItemKeywordsupport、`PriceAdjustmentDetailConstants`；对齐 PRC-001 `PriceAdjustmentMapper#toLineDto` 与 `PractitionerQueryService#findAllByIds` |
| v1.0 | 2026-06-27 | 首版：IF-PRC-DTL-01～03；`PriceAdjustmentDetailAppService` 目标态；对齐已关闭 GAP-PRC-DTL-001～002 与 `PriceAdjustmentQueryService` 三读方法 |
