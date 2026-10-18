# 进销存运维 · 应用服务详细设计（SDD）

> 编码：**AS-CHS-INV-001-SDD**  
> 版本：**v0.12** | 日期：**2026-09-15**  
> 编排权威：AS-CHS-INV-001（**v0.12**）

## 1. 实现边界

| 项 | 内容 |
| --- | --- |
| 模块 | 医保进销存运维台（单 AppService，多 Tab） |
| AppService | `InsuranceInventoryAppService` |
| HTTP 前缀 | `/api/app/insuranceInventory` |
| 边界 | **默认 Tab=3501–3506 流水补传**（唯一补传入口）；3501/3502/批初始化；业务 Extension 上报在外 |
| 实现阶段 | **已编码**；`retryUploadWithRequest`；进页默认筛 FAILED |

### 1.1 实现裁决

| 裁决 ID | 内容 |
| --- | --- |
| **INV-SDD-R-001** | AppService **禁止**注入 `InsuranceGatewayClient`；出站经 bridge `invoker/` |
| **INV-SDD-R-002** | `ChsInventoryProperties.uploadEnabled=false` 时写 IF 短路；`Extension#isEnabled` 同口径 |
| **INV-SDD-R-003** | 3505/3506 平台 `retRslt` 校验在 Upload workflow 内 |
| **INV-SDD-R-004** | 3512/3513 比对走 `YbInvUploadTraceLookup`，非主表 |
| **INV-SDD-R-005** | 本地流水筛选 AND；补传仅 FAILED/PENDING；空 `request_json` 须手工填；3502 `drugtracinfo` 超本系统上限（现 100）自动拆批；**「191 + 主档无码」不进补传列表/自动重试；「191 + 主档有码」须可见可处理**（BR-011） |
| **INV-SDD-R-006** | **3501–3506 补传唯一入口**为本页流水补传 Tab（BR-CHS-INV-010）；业务单据页不补传 |
| **INV-SDD-R-007** | 3505/3506 结算性质跟本批发药/退药收费项所挂支付单（BR-CHS-INV-012）；`InventoryUploadContextLoader#findPaymentContract` 按收费项交集取支付，禁止就诊级首条医保支付单冒充 |
| **INV-SDD-R-008** | `yb_inv_upload_trace` 同码写入：先软删再挂（BR-CHS-INV-013）；Orchestrator 平台 SUCCESS 后 `persistTraceRowsSafely`，子表异常不否定主表 |
| **INV-SDD-R-010** | 3505/3506 跟结算平台；销售配对 3502 跟码的入库平台（市入市出/省入省出；BR-CHS-INV-015）；InventorySalePaired3502ContractSupport |
| **INV-SDD-R-009** | 入库类 3502 协议成功且有码 → 3512 按批次核验落码；未落全改 FAILED（BR-CHS-INV-014）；Orchestrator / UploadWorkflow / RetryWorkflow 同口径 |

## 2. 代码结构

```text
module-chs-jilin-application/
  com/openhis/chs/application/inventory/service/
    InsuranceInventoryAppService.java

module-chs-jilin-bridge/
  com/openhis/chs/bridge/inventory/
    invoker/     Inv350xInvoker、Inv35xxQueryInvoker、Inv35xxRetryInvoker
    workflow/    InventoryUploadWorkflow、InventoryRetryWorkflow、
                 InventoryThirdPartyUploadAsyncRunner、…
    context/     *UploadContextLoader
  com/openhis/health/integration/insurance/inventory/
    InventoryThirdPartyUploadExtensionImpl.java

module-chs-jilin-domain/
  com/openhis/chs/domain/inventory/
    entity/ YbInvUpload
    service/ YbInvUploadDomainService
    repository/ YbInvUploadLookup（Jdbc AND 筛选）
    access/ InventoryRetryAccess 等端口
```

## 3. HTTP 接口 ↔ IF 映射

| HTTP 方法 | IF 编号 | 读/写 | 编排落点 |
| --- | --- | --- | --- |
| `listLocalUploadRecords` | IF-CHS-INV-R-01 | 读 | `YbInvUploadDomainService.listUploadRecords`（AND） |
| `queryPlatformInventory3508` | IF-CHS-INV-R-08 | 读 | `Inv35xxQueryInvoker.query3508`（本期 UI 隐藏） |
| `queryPlatformInvChange3509` | IF-CHS-INV-R-09 | 读 | `Inv35xxQueryInvoker.query3509` |
| `queryPlatformPurchase3510` | IF-CHS-INV-R-10 | 读 | `Inv35xxQueryInvoker.query3510` |
| `queryPlatformSale3511` | IF-CHS-INV-R-11 | 读 | `Inv35xxQueryInvoker.query3511` |
| `queryPlatformInboundTrace3512` | IF-CHS-INV-R-12 | 读 | `Inv35xxQueryInvoker.query3512` |
| `queryPlatformSaleTrace3513` | IF-CHS-INV-R-13 | 读 | `Inv35xxQueryInvoker.query3513` |
| `compareLocalVsPlatform` | IF-CHS-INV-R-CMP | 读 | `InventoryCompareWorkflow.compare`（本期 UI 隐藏） |
| `uploadStocktake3501` | IF-CHS-INV-W-01 | 写 | `InventoryUploadWorkflow.uploadStocktake3501` |
| `uploadInvChange3502` | IF-CHS-INV-W-02 | 写 | `InventoryUploadWorkflow.uploadInvChange3502` |
| `uploadPurchase3503` | IF-CHS-INV-W-03 | 写 | `InventoryUploadWorkflow.uploadPurchase3503` |
| `uploadPurchaseReturn3504` | IF-CHS-INV-W-04 | 写 | `InventoryUploadWorkflow.uploadPurchaseReturn3504` |
| `uploadSale3505` | IF-CHS-INV-W-05 | 写 | `InventoryUploadWorkflow.uploadSale3505` |
| `uploadSaleReturn3506` | IF-CHS-INV-W-06 | 写 | `InventoryUploadWorkflow.uploadSaleReturn3506` |
| `initializeInventory` | IF-CHS-INV-W-INIT | 写 | `InventoryInitializeUploadContextLoader` + `InventoryInitializeWorkflow` |
| `deleteInvData3507` | IF-CHS-INV-W-07 | 写 | `Inv3507Invoker.invoke`（本期 UI 隐藏） |
| `retryPendingUploads` | IF-CHS-INV-W-RETRY | 写 | `InventoryRetryWorkflow.retry` |
| `retryUploadWithRequest` | IF-CHS-INV-W-RETRY-ONE | 写 | `InventoryRetryWorkflow.retryOne` |

## 4. 依赖注入表

| 依赖 | 用途 |
| --- | --- |
| `InventoryUploadAccess` | 3501–3506 上报 |
| `InventoryInitializeAccess` / `InventoryInitializeFillAccess` | 批初始化 |
| `Inv3507Invoker` | 3507 删除 |
| `Inv35xxQueryInvoker` | 3508–3513 查询 |
| `InventoryCompareAccess` | 差异比对 |
| `InventoryRetryAccess` | 失败批重试 + 单条补传（`retryOne`） |
| `YbInvUploadDomainService` | 本地流水读写 |

## 5. 事务与异常

| 项 | 约定 |
| --- | --- |
| 事务 | `AppServiceAspect`：`list*`/`query*`/`compare*` 只读；其余 REQUIRED |
| 平台失败 | Invoker 内 `ChsPlatformResponseSupport.requireSuccess` |
| 前端 | `ensureInsuranceSignIn` 在写操作前调用 |
| 业务 Extension | `runUploadIfEnabled` → afterCommit；出站 `@Async`（`InventoryThirdPartyUploadAsyncRunner`） |

## 6. 关联

- LLD：[09-医保进销存-详细设计.md](../09-医保进销存-详细设计.md)（§4 字段；§4.5e `purc_invo_no`←`invoice_no`，空→`0`）
- AS：**BR-CHS-INV-005** / **BR-CHS-INV-007** / **BR-CHS-INV-008** / **BR-CHS-INV-009**
- 代码 README：`module-chs-jilin-application/.../application/inventory/README.md`
- Extension 自动上报：`InventoryInboundUploadContextLoader` / `InventoryPurchaseReturnUploadContextLoader`（3503/3504）

### 6.1 变更历史

| 版本 | 日期 | 变更摘要 |
|------|------|----------|
| v0.12 | 2026-09-15 | INV-SDD-R-010 / BR-015：销售配对 3502 跟入库平台；3505 跟结算（含工伤） |
| v0.11 | 2026-09-15 | INV-SDD-R-009 / BR-014：入库 3512 落码核验；出站上限 100；禁去码冒充 |
| v0.10 | 2026-09-15 | INV-SDD-R-008 / BR-013：追溯码子表同码软删再挂；子表失败不否定主表 |
| v0.9 | 2026-09-15 | INV-SDD-R-007 / BR-012：3505 结算性质跟本批发药收费项支付单 |
| v0.8 | 2026-09-15 | BR-011：191 仅主档无码隐藏；主档有码补传页可见 |
| v0.7 | 2026-09-15 | INV-SDD-R-005 / BR-011：入库追溯底账缺失 FAILED 隐藏于补传列表与自动重试 |
| v0.6 | 2026-09-15 | INV-SDD-R-005：3502 超 500 码补传自动拆批（对齐 09 §4.4 / AS v0.6） |
| v0.5 | 2026-07-27 | 流水补传 Tab 定位为 3501–3506 唯一补传入口（INV-SDD-R-006 / BR-010） |
| v0.4 | 2026-07-27 | 结构改为 application/bridge/domain；补 `retryUploadWithRequest`；AND 筛选与补传门禁裁决 |
| v0.3 | 2026-07-27 | 对齐 AS v0.3：`retryUploadWithRequest`；业务 Extension afterCommit+@Async；本期 UI 隐藏扩展 Tab |
| v0.2 | 2026-07-19 | 交叉引用 09 §4.5e / BR-CHS-INV-007 / 进退货 Loader |
| v0.1 | 2026-07-11 | 初稿 |
