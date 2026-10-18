# 住院反结 · 应用服务详细设计（SDD）

> 编码：AS-IP-FIN-004-SDD  
> 版本：v1.11 | 日期：2026-10-09 | 作者：（待填）  
> 来源契约：`AS-IP-FIN-004-住院反结.md` v1.7；读侧扩展对齐 `AS-IP-FIN-003-住院结算-SDD.md` IF-IP-STL-07

## 1. 实现边界

### 1.1 目标与边界

| 项 | 内容 |
| --- | --- |
| 模块 | 住院反结（与 **AS-IP-FIN-003** 共用结算页 `/inpatient/charge/settle` **已结算**侧） |
| 包路径 | `com.zxplat.domain.application.inpatient.financial.settle`（**与 FIN-003 同包**） |
| 边界 | 编排 PaymentReconciliation / ChargeItem / Encounter / Account / Invoice / ServiceRequest；读侧扩展 **IF-IP-STL-07** 结算历史 NET 展示与「可反结」门禁；**本期无医保** |
| 编排权威 | `AS-IP-FIN-004-住院反结.md` §2、§4 |
| 方法权威 | `DM-FIN-002` §6.1.7、`DM-ADM-011` §6.1.12a、`DM-ADM-002` `revertInpatientFinancialSettlement`（成码）、`DM-WKF-004` §6.1.7、`DM-ADM-012`、`DM-ADM-009` |
| 严格对齐 | 写侧 **IF-IP-RVS-01**、**IF-IP-RCL-01**；读侧增量 **IF-IP-STL-07**（历史条 NET / `reverseEnabled` / `recallEnabled`） |
| 实现阶段口径 | **v1.5 目标态**（2026-07-11）：ChargeItem **复制再生**（源 `SETTLED` 终态 + `refund_id` 链）；应用层 **`reverseInpatientSettle`** |

**Out of scope（重复 AS §1.4）**

| 类别 | 内容 |
| --- | --- |
| 医保结算撤销 | 2303/2304/2305；`runInsuranceRevokeBeforeCancel` **已成码但未接入** `executeReverseInpatientSettle`（扩展位；**disabled 时 NoOp**） |
| 进销存冲销 | afterCommit：`uploadAfterClearBedSummarySalesReverse` → 3506（仅已成功清床汇总 3505；见 **09** / **AS-IP-NUR-002**） |
| 中途结算反结 | **BR-IP-RVS-005/010** 分支；**中期结算能力上线后再启用**（见 §1.2） |
| 打印/电票 | 反结后自动重打 |
| 独立反结预览 IF | — |

**上游 / 调用方**

| 文档 | 关系 |
| --- | --- |
| `AS-IP-FIN-003-住院结算-SDD.md` | 同包、同 Controller；IF-IP-STL-07 读侧本 SDD **增量扩展** |

### 1.2 术语口径

| 项 | 约定 |
| --- | --- |
| 业务用语 | **反结**（禁止 UI/接口文案「取消结算」） |
| 正向结算单 | `kind_enum=INPATIENT_CLINIC`、`relation_id IS NULL`、结单时推进为 **`SUCCESS`** 的支付主单 |
| 逆向反结单 | `cancelInpatientSettleAsRefund` 新建、`relation_id=原单.id`、收口后 **`REFUNDED`** 的支付主单；展示金额为**负值** |
| 一正一负抵净 | 同一原单与其逆向单 `display_amount` 代数和为 **0**；历史列表 **`netSettleTotal`** 为各行 **`signedAmount`** 之和 |
| 可反结 | 仅**正向**且 **`SUCCESS`** 且 **尚无逆向关联** 的单可点「反结」；**同一 `paymentId` 禁止反结两次**（**BR-IP-RVS-002**） |
| 本期结算形态 | **无中途结算**；可反结正向单 **一律按出院全结对称项**执行 **BR-IP-RVS-006**（全路径）。中途结算上线后启用 **BR-IP-RVS-005/010** |
| 字段口径 | Request/Response/DTO 枚举与状态字段一律 **String code** |
| 事务口径 | 与 FIN-003-SDD §5.2 一致：**App 不声明 `@Transactional`**；跨聚合顺序按 §3.3 序号 |

## 2. 代码结构

```text
zxplat/health-application/src/main/java/com/openhis/health/application/inpatient/financial/settle/
  service/
    InpatientSettleAppService.java          # reverseInpatientSettle（IF-IP-RVS-01）；recallInpatientDischarge（IF-IP-RCL-01）；executeReverseInpatientSettle 内核
  request/
    ReverseInpatientSettleRequest.java
    RecallInpatientDischargeRequest.java
  response/
    ReverseInpatientSettleResponse.java
    RecallInpatientDischargeResponse.java
  mapper/
    InpatientSettleMapper.java
    InpatientSettleMapperImpl.java

zxplat/zxplat-domain/.../chargeitem/service/
  ChargeItemRegenerationChainSupport.java   # BR-REF-020 / BR-IP-RVS-011 再生链递归解析（多轮结算/反结）
  ChargeItemQueryService.java               # filterOutSupersededSettledLines / mapPreservedNew…
  ChargeItemDomainService.java              # revertInpatientSettledChargeItems；写侧 Gate 委托链解析

zxplat/health-module-support/.../querysupport/financial/
  InpatientSettleReadLoader.java            # IF-IP-STL-07：历史条扩展
  InpatientSettleReadAssembler.java
  InpatientReverseSettlePreSupport.java    # 反结 Pre 读侧（收银台共用）
  dto/SettledPaymentBriefDto.java           # reverseEnabled / recallEnabled
```

> HTTP：`AppServiceControllerRegistrar` → `POST /api/app/inpatientSettle/{methodName}`  
> 编排常量：`InpatientSettleConstants` 增补 **`EX_IP_RVS_*`**（目标态）；**禁止** `health-application` 下建 `constants` 子包

### 2.1 依赖注入表（IF-IP-RVS-01 增量）

| 依赖 | 用途 |
| --- | --- |
| `CurrentOperatorResolver` | Gate（IF-IP-RVS-01） |
| `CurrentPractitionerResolver` | 撤销出院嘱校对人（**BR-IP-RVS-007**） |
| `PatientEncounterConsistencyGate` | 三主键与就诊一致 |
| `EncounterQueryService` | `mustGetEncounterOfClass`、就诊态门禁 |
| `PaymentReconciliationQueryService` | 原单加载、明细、`existsByRelationId` 门禁、历史列表 |
| `PaymentReconciliationDomainService` | 逆向单、`completeInpatientSettleCancelRefund` |
| `ChargeItemDomainService` | `revertInpatientSettledChargeItems` |
| `ChargeItemQueryService` | 原单绑定 SETTLED 校验 + 已再生门禁（`existsActiveRegenerationForSourceChargeItemIds`） | Pre |
| `EncounterDomainService` | `revertInpatientFinancialSettlement` |
| `AccountDomainService` | 多退预交金回补 |
| `InvoiceDomainService` / `InvoiceQueryService` | 作废/冲红 |
| `ServiceRequestQueryService` / `ServiceRequestDomainService` | 出院嘱 `softDeleteDischargeServiceRequestForReverseSettle` |
| `InpatientSettleMapper` | Request/Command/Response 装配 |
| `InpatientReverseSettlePreSupport` | 反结 Pre 读侧 |
| `InpatientSettleReadLoader` / `InpatientSettleReadAssembler` | IF-IP-STL-07 历史 NET（读侧扩展） |

### 2.2 领域方法白名单

| 类型 | 方法 | 用于 IF |
| --- | --- | --- |
| `PaymentReconciliationQueryService` | `mustGetPayment` | IF-IP-RVS-01 Pre |
| `PaymentReconciliationQueryService` | `listPaymentRecDetailsByPaymentId` | IF-IP-RVS-01 Pre |
| `PaymentReconciliationQueryService` | `findPaymentsByEncounter` | IF-IP-STL-07 扩展 |
| `PaymentReconciliationQueryService` | `hasRefundByRelationId` / `existsByRelationId`（经 Repository） | IF-IP-RVS-01 Pre；`reverseEnabled` |
| `PaymentReconciliationDomainService` | `cancelInpatientSettleAsRefund` | IF-IP-RVS-01 Cmd ⑥ |
| `PaymentReconciliationDomainService` | `completeInpatientSettleCancelRefund` | IF-IP-RVS-01 Cmd ⑪ |
| `PaymentReconciliationDomainService` | `updatePaymentStatusWithSettleIds` | IF-IP-RVS-01 Cmd ⑧（多退预交金流水标 REFUNDED） |
| `ChargeItemQueryService` | `loadChargeItemsByIds` | IF-IP-RVS-01 Pre；IF-IP-STL-07 |
| `ChargeItemDomainService` | `revertInpatientSettledChargeItems` | IF-IP-RVS-01 Cmd ⑦ |
| `EncounterDomainService` | `revertInpatientFinancialSettlement` | IF-IP-RVS-01 Cmd ⑧（本期全路径） |
| `EncounterDomainService` | `recallInpatientDischarge` | IF-IP-RCL-01 Cmd ⑥ |
| `EncounterQueryService` | `findLastCompletedWardLocationIdByEncounterId` | IF-IP-RCL-01 Pre ③ |
| `AccountDomainService` | `adjustAdvanceDepositBalance` | IF-IP-RVS-01 Cmd ⑧ |
| `InvoiceQueryService` | `getInvoiceByPaymentId` | IF-IP-RVS-01 Pre/Cmd ⑨ |
| `InvoiceDomainService` | `voidDraftInvoice` / `writeoffInvoice` | IF-IP-RVS-01 Cmd ⑨ |
| `ServiceRequestQueryService` | `listServiceRequestsByEncounter` | IF-IP-RVS-01 Pre ⑤ |
| `ServiceRequestDomainService` | `softDeleteDischargeServiceRequestForReverseSettle` | IF-IP-RVS-01 Cmd ⑰ |
| `EncounterQueryService` | `mustGetEncounterOfClass` | IF-IP-RVS-01 / IF-IP-STL-07 |

### 2.3 枚举、字典与值集（DM 与代码可追溯）

| 类别 | 代码实现锚点 | DM 追溯锚点 | 说明 |
| --- | --- | --- | --- |
| 支付业务类型 | `com.zxplat.domain.share.enums.PaymentKind` | `DM-FIN-002` §3.1 | **`INPATIENT_CLINIC`** |
| 支付流水状态 | `com.zxplat.domain.share.enums.PaymentStatus` | `DM-FIN-002` §5.2 | 正向 **`SUCCESS`**；逆向过程 **`REFUND_PROCESSING`**；收口 **`REFUNDED`** |
| 收费项状态 | `com.zxplat.domain.share.enums.ChargeItemStatus` | `DM-ADM-011` §2 | 反结：源 `SETTLED` 不变；插入 `PENDING_SETTLEMENT` 再生行 + `refund_id` |
| 就诊状态 | `com.zxplat.domain.share.enums.EncounterStatus` | `DM-ADM-002` §2.3.1 | 反结 **`COMPLETED→IN_PROGRESS`**（本期）；**`DISCHARGED`** 禁止本页反结 |
| 诊疗状态 | `com.zxplat.domain.share.enums.RequestStatus` | `DM-WKF-004` | 出院嘱 **`VERIFIED→DRAFT`** |
| 出院类诊疗类目 | `ServiceRequestCategoryConstants.SERVICE_CATEGORY_DISCHARGE` | `DM-WKF-004` §4.3 | 值 **`"34"`** |
| 历史条类型 | `SettledPaymentBriefDto.recordType` | 本 SDD §3.2 | **`FORWARD`** / **`REVERSE`**（目标态待生成） |
| 编排消息码 | `InpatientSettleConstants.EX_IP_RVS_001`～`005`；`EX_IP_RCL_001`～`004` | `AS-IP-FIN-004` §5 | 已成码 |
| 票据作废原因 | `InpatientSettleConstants.INVOICE_CANCEL_REASON_INPATIENT_SETTLE` | `AS-IP-FIN-004` | 值 **`住院反结`** |
| 撤销出院嘱原因 | `InpatientSettleConstants.DISCHARGE_SERVICE_REJECT_REASON_INPATIENT_REVERSE` | `AS-IP-FIN-004` | 值 **`住院反结`** |

### 2.4 白名单双向一致性复核

| 复核项 | 结论 | 说明 |
| --- | --- | --- |
| 正向 | PASS | §2.2 各方法均出现在 §3.3 / §3.2 编排序列 |
| 反向 | PASS | §3.3 序列调用均可于 §2.2 定位 |

### 2.5 成码前已闭合契约（领域层）

| 主题 | 约定 |
| --- | --- |
| ChargeItem 再生 | `revertInpatientSettledChargeItems`：对支付单快照内源 `SETTLED` 行**复制插入** `PENDING_SETTLEMENT` 新行（`refund_id` 链回）；**源行保持 SETTLED**；发布 **`InpatientChargeItemsRevertedEvent`**（新行 ID） |
| 再生链读侧/Gate | **`ChargeItemRegenerationChainSupport`**：链末端 `PLANNED`/`PENDING_SETTLEMENT` 叶沿 `refund_id` **向上递归**；链上全部祖先 `SETTLED` 在 Gate/读侧视为终态历史（**多轮**结算/反结） |
| 逆向单门禁 | `cancelInpatientSettleAsRefund`：`existsByRelationId(原单.id)` → 拒绝重复反结 |
| 就诊财务回退 | `revertInpatientFinancialSettlement`：仅 **`COMPLETED→IN_PROGRESS`**（`recallFromCompletedToInProgress`）；**`DISCHARGED` 无操作**（召回路径见 §3.4） |
| 出院召回 | `recallInpatientDischarge`：**`DISCHARGED→IN_PROGRESS`** + INSERT 活动 **WARD**（**不** INSERT 活动床）；`requiresBedAssignment=false` |

### 2.6 文档与代码追溯

| IF | App 方法 | 主要领域写 | 读侧扩展 |
| --- | --- | --- | --- |
| IF-IP-RVS-01 | `reverseInpatientSettle` | 逆向单 → ChargeItem 复制再生 → 出院全结对称项 → 出院嘱 → 收口 REFUNDED | — |
| IF-IP-RCL-01 | `recallInpatientDischarge` | `executeReverseInpatientSettle(..., allowDischarged=true)` + `recallInpatientDischarge` | — |
| IF-IP-STL-07（增量） | `loadSettledInpatientPaymentView` | — | 历史条含正/负、NET、`reverseEnabled`、`recallEnabled` |

**HTTP 前缀**：`POST /api/app/inpatientSettle/{methodName}`

## 3. 按接口 ID 命名展开

### 3.1 应用服务接口（方法白名单）

| IF | 应用服务方法 | 中文 | HTTP |
| --- | --- | --- | --- |
| IF-IP-RVS-01 | `reverseInpatientSettle` | 住院反结 | `POST /api/app/inpatientSettle/reverseInpatientSettle` |
| IF-IP-RCL-01 | `recallInpatientDischarge` | 出院召回 | `POST /api/app/inpatientSettle/recallInpatientDischarge` |
| IF-IP-STL-07（增量） | `loadSettledInpatientPaymentView` | 已结算单查阅（历史 NET） | `POST /api/app/inpatientSettle/loadSettledInpatientPaymentView` |

> **迁移说明**：成码现存 `cancelInpatientSettle` / `CancelInpatientSettleRequest` → 重命名为上表；HTTP 路径同步变更；前端 `httpCancelInpatientSettle` → `httpReverseInpatientSettle`。

### 3.1.1 编排约定

| 阶段 | 含义 | 失败时 |
| --- | --- | --- |
| **Gate** | 登录、三主键非空 | 不进入 Pre |
| **Pre** | 原单/就诊/ChargeItem/出院嘱/重复反结门禁 | 抛 **EX-IP-RVS-***，**禁止 Cmd** |
| **Cmd** | 领域写 | 见 §5 |
| **Asm** | Mapper 装配 Response | — |

---

### 3.2 IF-IP-STL-07 读侧增量（结算历史 · NET · 可反结）

**目的**：**已结算**与**待结算** Tab 右侧 **结算历史**列表只读展示 **一正一负**记录与 **净额**；**反结写操作**仅 **已结算 Tab** 可对**正向 SUCCESS 且未反结过**的行点「反结」。

**就诊态门禁（IF-IP-STL-07 Pre，v1.3）**

| 场景 | 规则 |
| --- | --- |
| 允许只读查历史 | 住院 `IMP` 且 `status_enum ∉ { CANCELLED, PLANNED }`（含反结后 **`IN_PROGRESS`**、召回后 **`REGISTERED`**、已清床 **`DISCHARGED`**、待清床 **`COMPLETED`** 等）；**IF-02 已结算队列**另按流水存在性 + 住院号去重（**FIN-003 BR-IP-STL-017**） |
| 拒绝 | `CANCELLED` / `PLANNED` → **EX-IP-STL-102** |
| 无流水 | 过滤后无 `INPATIENT_CLINIC` + SUCCESS/REFUNDED → **EX-IP-STL-110**（待结算首次就诊前端静默为空） |
| 写侧反结 | 仍须 **已结算 Tab** + **IF-IP-RVS-01**；待结算 Tab **仅展示**历史，不提供反结入口 |

**Response 增量字段（`LoadSettledInpatientPaymentViewResponse`）**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `netSettleTotal` | BigDecimal | 本就诊住院结算流水 **`signedAmount` 代数和**（正+负=净；全反结后应为 **0**） |

**DTO 增量字段（`SettledPaymentBriefDto`）**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `paymentNo` | String | N | 支付业务单号（`fin_payment_reconciliation.payment_no`；历史表「单据号」列） |
| `recordType` | String | Y | **`FORWARD`**：正向结单（`relation_id` 为空）；**`REVERSE`**：逆向反结单（`relation_id` 指向原单） |
| `signedAmount` | BigDecimal | Y | 展示用带符号金额：正向 **`+display_amount`**；逆向 **`-display_amount`**（绝对值取库内 `display_amount`） |
| `statusEnum` | String | Y | `PaymentStatus#getCode()`（`SUCCESS` / `REFUNDED` 等） |
| `relationId` | Long | N | 逆向单指向的原单 ID；正向为空 |
| `reverseEnabled` | Boolean | Y | **是否展示可点「反结」**（见下表） |
| `reverseDisabledReason` | String | N | `reverseEnabled=false` 时前端 Tooltip；如「已反结」「逆向记录不可反结」「已离院请先召回」 |
| `recallEnabled` | Boolean | Y | **是否展示可点「召回」**；**仅**就诊 **`DISCHARGED`** 且正向 SUCCESS 未反结时为 true |

**`reverseEnabled` 判定（必须全部满足才为 `true`）**

| # | 条件 |
| --- | --- |
| 1 | `recordType = FORWARD` |
| 2 | `statusEnum = SUCCESS`（`PaymentStatus.SUCCESS`） |
| 3 | `kind_enum = INPATIENT_CLINIC` |
| 4 | **`PaymentReconciliationRepository#existsByRelationId(paymentId) = false`**（尚无逆向单） |
| 5 | 就诊 **`status_enum ≠ DISCHARGED`**（**BR-IP-RVS-008**；`COMPLETED` 未清床 **允许**） |

**`recallEnabled` 判定（须同时满足）**

| # | 条件 |
| --- | --- |
| 1 | `recordType = FORWARD` |
| 2 | `statusEnum = SUCCESS` |
| 3 | `kind_enum = INPATIENT_CLINIC` |
| 4 | **`existsByRelationId(paymentId) = false`** |
| 5 | 就诊 **`status_enum = DISCHARGED`** |

> **`DISCHARGED`** 时 **`reverseEnabled=false`**、`recallEnabled=true`；非 **`DISCHARGED`** 且满足上表 1～4 时 **`reverseEnabled=true`**。

**历史列表加载规则（替换 FIN-003-SDD §3.8 步骤 5 过滤口径）**

| 项 | 规则 |
| --- | --- |
| 纳入流水 | 同就诊、`INPATIENT_CLINIC`、`status_enum ∈ { SUCCESS, REFUNDED }` |
| 排序 | `bill_date` 降序，同秒 `id` 降序（**正向与其逆向相邻展示**） |
| 禁止仅 SUCCESS | 反结后原单变 **`REFUNDED`**，须与逆向 **`REFUNDED`** 一并展示，否则用户看不到「一正一负」 |
| 费用快照 | `activePaymentId` 仍指向用户选中行；绑定 `charge_item_ids` 只读快照（含已反结正向单） |
| 空列表 | 无任何 `INPATIENT_CLINIC` 结算/反结流水 → **EX-IP-STL-110** |

**领域编排序列（严格顺序，相对 FIN-003 §3.8 变更点）**

| 序号 | 阶段 | 调用 | 说明 |
| --- | --- | --- | --- |
| 1～4 | Gate/Pre | 同 FIN-003 §3.8 | 就诊 `IMP`；**非** `CANCELLED`/`PLANNED`（**v1.3**：含反结后 `IN_PROGRESS`） |
| 5 | Pre | `PaymentReconciliationQueryService#findPaymentsByEncounter` | 过滤 **`INPATIENT_CLINIC` + SUCCESS/REFUNDED** |
| 6 | Pre | App 组装 `recordType` / `signedAmount` / `reverseEnabled` / `recallEnabled` | 规则见上表 |
| 7 | Pre | App 计算 `netSettleTotal` | Σ `signedAmount` |
| 8～12 | Pre/Asm | 同 FIN-003 §3.8 | 快照装配 |

**Mapper 签名（增量）**

- `toSettledPaymentBriefDtoList(..., EncounterDetailQueryModel encounter): List<SettledPaymentBriefDto>` — 注入 `reverseEnabled` 判定所需就诊态
- `toLoadSettledInpatientPaymentViewResponse(..., BigDecimal netSettleTotal): LoadSettledInpatientPaymentViewResponse`

---

### 3.3 IF-IP-RVS-01 `reverseInpatientSettle`（住院反结）

**HTTP**：`POST /api/app/inpatientSettle/reverseInpatientSettle`

**Request（`ReverseInpatientSettleRequest`）**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `paymentId` | Long | Y | **正向**成功结算单 ID（用户于历史条点击「反结」的那一行） |
| `encounterId` | Long | Y | 住院就诊 ID |
| `patientId` | Long | Y | 患者 ID |

**Response（`ReverseInpatientSettleResponse`）**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `refundPaymentId` | Long | **逆向反结单** ID（负向记录；收口后 `REFUNDED`） |
| `succeeded` | Boolean | 固定 **`true`**（失败抛异常） |

> **本期** Response **不含**医保字段；若保留 CHS 扩展位，字段须可空且 AS 非目标下不赋值。

**关键校验**

| 场景 | 语义码 |
| --- | --- |
| 原单非 SUCCESS / 非 INPATIENT_CLINIC / 三主键不一致 | EX-IP-RVS-001 |
| 已存在逆向单（`existsByRelationId`） | EX-IP-RVS-001 |
| 本批 ChargeItem 非 SETTLED | EX-IP-RVS-002 |
| 就诊 DISCHARGED（本页入口） | EX-IP-RVS-003 |
| 本期出院全结须取消出院嘱但未找到可取消 category=34 | EX-IP-RVS-004 |
| 持久化失败 | EX-IP-RVS-005 |

**Mapper 接口方法签名**

- `toCancelInpatientSettleAsRefundCommand(PaymentReconciliationQueryModel original, List<PaymentRecDetailQueryModel> details, Long operatorId): CancelInpatientSettleAsRefundCommand`（领域命令名保持 DM）
- `toCompleteInpatientSettleCancelRefundCommand(Long originalPaymentId, Long reversePaymentId, String ybSetlId): CompleteInpatientSettleCancelRefundCommand`
- `toSoftDeleteDischargeServiceRequestCommand(Long serviceRequestId, Long operatorId): SoftDeleteDischargeServiceRequestForReverseSettleCommand`
- `toReverseInpatientSettleResponse(Long refundPaymentId): ReverseInpatientSettleResponse`

**领域编排序列（严格顺序）**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | `CurrentOperatorResolver#requireOperatorId` | — | **EX-IP-RVS-001** |
| 3 | Pre | `PaymentReconciliationQueryService#mustGetPayment` | `paymentId` | 须 **`SUCCESS` + INPATIENT_CLINIC** |
| 4 | Pre | App 校验 | request 三主键 vs 单头 | 不一致 → **EX-IP-RVS-001** |
| 5 | Pre | `EncounterQueryService#mustGetEncounterOfClass` | `IMP` | — |
| 6 | Pre | App 就诊态 | `DISCHARGED` | 本页入口 → **EX-IP-RVS-003**（内嵌入口跳过，见 §3.4） |
| 7 | Pre | `PatientEncounterConsistencyGate` | patientId | — |
| 8 | Pre | `PaymentReconciliationRepository#existsByRelationId` | `paymentId` | 已反结 → **EX-IP-RVS-001** |
| 10 | Pre | App 解析原单绑定 `chargeItemIds` | 原单绑定串 | 反结再生**仅本支付单快照**内源 `SETTLED` 行（**BR-IP-RVS-011**） |
| 11 | Pre | `InpatientReverseSettlePreOrchestrator#resolveRemovableDischargeServiceRequestId` | category=34 | 可软删态出院嘱；无则 **EX-IP-RVS-004**（**BR-IP-RVS-007**） |
| 12 | Pre | `PaymentReconciliationQueryService#listPaymentRecDetailsByPaymentId` | 原单 ID | 复制渠道明细 |
| 13 | Cmd | `PaymentReconciliationDomainService#cancelInpatientSettleAsRefund` | `CancelInpatientSettleAsRefundCommand`（**`payment_enum=UN_PAY`**） | 新建逆向单 **`REFUND_PROCESSING`**；**写库点 ①** |
| 14 | Cmd | `ChargeItemDomainService#revertInpatientSettledChargeItems` | 步骤 10 全部 IDs | 源 `SETTLED` 不变；**插入** `PENDING_SETTLEMENT` 再生行 + `refund_id`；**写库点 ②** |
| 15 | Cmd | `EncounterDomainService#revertInpatientFinancialSettlement` | encounterId | **仅当** 就诊 **`COMPLETED`**（清空 `end_time`/`out_way_code`）；**写库点 ③** |
| 16 | Cmd | App `voidInvoiceLinkedToPaymentOnCancel` | 原单 `paymentId` | 草稿作废 / 已开冲红；**写库点 ④** |
| 17 | Cmd | `ServiceRequestDomainService#softDeleteDischargeServiceRequestForReverseSettle` | 步骤 11 解析 ID | 主档+明细逻辑删除；列表不再展示；医生须重新开立；**写库点 ⑤** |
| 18 | Cmd | `PaymentReconciliationDomainService#completeInpatientSettleCancelRefund` | 原单+逆向 ID | 逆向单 → **`REFUNDED`**；原单保持 **`SUCCESS`**；**写库点 ⑥** |
| 19 | Asm | `InpatientSettleMapper#toReverseInpatientSettleResponse` | `refundPaymentId` | — |
| 20 | afterCommit | `InventoryThirdPartyUploadExtension#uploadAfterClearBedSummarySalesReverse` | `encounterId` | 仅已成功清床汇总 3505 时异步 **3506**；未清床 **no-op** |

**本期简化（无中途结算）**

- 步骤 15 **仅** `COMPLETED` 时回退就诊财务态；**有冲抵明细 O>0 时须** `adjustAdvanceDepositBalance(+O)` 回冲个人现金户（DD §3.5）；本轮不强制追回已退预交金 UN_PAY。
- 读侧：存在已 `REFUNDED` 正向出院结算且无新 `SUCCESS` 正向单时，队列/摘要仍经 **`InpatientAccountBalanceDisplayAssembler`** 输出**单一展示余额**（库内 balance − 待结算；见 **AS-IP-FIN-002** §1.3）。
- **BR-IP-RVS-010** 留待中期结算上线。

**成码迁移注意（相对现存 `cancelInpatientSettle`）**

| 项 | 目标态 | 现存偏差 |
| --- | --- | --- |
| 出院全结判定 | 本期恒 true | 误用「就诊 COMPLETED」推断 |
| 撤销出院嘱 | 步骤 18 | **缺失** |
| 异常码 | **EX-IP-RVS-*** | 映射 **EX-IP-STL-106/109** |
| 方法/Request 命名 | `reverse*` | `cancel*` |

---

### 3.4 IF-IP-RCL-01 `recallInpatientDischarge`（出院召回）

**HTTP**：`POST /api/app/inpatientSettle/recallInpatientDischarge`

**Request（`RecallInpatientDischargeRequest`）**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- |
| `encounterId` | Long | Y | 住院就诊 ID |
| `patientId` | Long | Y | 患者 ID |
| `paymentId` | Long | N | 正向 SUCCESS 出院全结单；缺省取末次 |

**Response（`RecallInpatientDischargeResponse`）**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `encounterId` | Long | 就诊 ID |
| `succeeded` | Boolean | 固定 **`true`** |
| `targetStatusEnum` | String | 召回后 **`IN_PROGRESS`** |
| `requiresBedAssignment` | Boolean | **恒为 false**（**不**须 ATD-02 二次分床） |
| `refundPaymentId` | Long | 内嵌反结逆向单 ID（若有） |

**关键校验**

| 场景 | 语义码 |
| --- | --- |
| 三主键空 / 非 IMP / 非 DISCHARGED / COMPLETED 未清床 | EX-IP-RCL-001 |
| 内嵌反结失败 | EX-IP-RCL-002 |
| 无 VERIFIED 出院嘱可退 | EX-IP-RCL-003 |
| 末次 WARD 缺失 / 持久化失败 | EX-IP-RCL-004 |

**Mapper 接口方法签名**

- `toRecallInpatientDischargeCommand(Long encounterId, Long operatorId, Long wardLocationId): RecallInpatientDischargeCommand`
- `toRecallInpatientDischargeResponse(Long encounterId, String targetStatusEnum, boolean requiresBedAssignment, Long refundPaymentId): RecallInpatientDischargeResponse`

**领域编排序列（严格顺序）**

| 序号 | 阶段 | 调用 | 说明 |
| --- | --- | --- | --- |
| 1 | Gate | `requireOperatorId` / `requirePractitionerId` | — |
| 2 | Pre | `mustGetEncounterOfClass` + 状态 | **须 `DISCHARGED`**；`COMPLETED` → **EX-IP-RCL-001** |
| 3 | Pre | `findLastCompletedWardLocationIdByEncounterId` | 末次 COMPLETED WARD |
| 4 | Pre | `resolveLastForwardSuccessInpatientClinicPaymentId` | 无 → **EX-IP-RCL-002** |
| 5 | Cmd | **`executeReverseInpatientSettle(..., allowDischarged=true)`** | 同 §3.3 对称；**跳过** 步骤 15 `revertInpatientFinancialSettlement` |
| 6 | Cmd | `EncounterDomainService#recallInpatientDischarge` | **`DISCHARGED→IN_PROGRESS`** + INSERT 活动 WARD |
| 7 | Asm | `toRecallInpatientDischargeResponse` | `requiresBedAssignment=false` |

**App 事务**：`recallInpatientDischarge` **须**包裹步骤 **5～6**（反结 + 就诊位置恢复同提交）。

> **废止**：原 **AS-IP-NUR-002** **IF-IP-NUR-ATD-07** 与 **`RecallReverseSettleSupport`** 已删除；召回写能力统一在本 AppService。

## 4. QueryService 与领域事件

### 4.1 读侧

| 能力 | 用于 |
| --- | --- |
| `PaymentReconciliationQueryService#findPaymentsByEncounter` | IF-IP-STL-07 历史正/负条 |
| `PaymentReconciliationQueryService#mustGetPayment` | 反结 Pre / 快照 |
| FIN-003 既有 ChargeItem / ProductDefinition 读链 | 快照明细 |

### 4.2 事件 → 刷新

| 事件 | 触发（§3.3 序号） | 消费语义 |
| --- | --- | --- |
| `InpatientSettleCancelledEvent` | 19 | 支付读侧；队列；日结 |
| `InpatientChargeItemsRevertedEvent` | 14 | 待结算/已结占用重算；展示余额恢复 |
| `ServiceRequestRejectedToDraftEvent` | 17 | 出院嘱列表；`discharge_type_enum` 投影 |
| 就诊财务回退 | 15 | `InpatientEncounterContextQueryModel` → **`IN_PROGRESS`** |

## 5. 写库顺序（IF-IP-RVS-01）

### 5.1 Cmd 落库顺序

| 序号 | 领域调用 | 聚合写入 |
| --- | --- | --- |
| 13 | `cancelInpatientSettleAsRefund` | 新建逆向 PaymentReconciliation + Details |
| 14 | `revertInpatientSettledChargeItems` | 支付单快照内源 `SETTLED` 行复制插入 `PENDING_SETTLEMENT` 再生行 |
| 15 | `revertInpatientFinancialSettlement` | Encounter `COMPLETED→IN_PROGRESS`（条件） |
| 16 | Invoice void/writeoff | Invoice |
| 17 | `softDeleteDischargeServiceRequestForReverseSettle` | ServiceRequest（出院嘱逻辑删除） |
| 18 | `completeInpatientSettleCancelRefund` | 逆向 Payment → `REFUNDED`；原单保持 `SUCCESS` |

### 5.2 事务边界

| 项 | 约定 |
| --- | --- |
| 产品期望 | 序号 13～19 **同一业务事务**；失败整单回滚 |
| 成码 | **App 不声明 `@Transactional`**（同 FIN-003-SDD §5.2 DOC-CONFLICT） |
| IF-IP-RCL-01 | 反结内核 + 召回位置恢复 **同一 App 事务** |

## 6. 非功能性需求实现

### 6.1 幂等

| 承诺 | 代码位置 | 步骤 |
| --- | --- | --- |
| 同一正向单禁止反结两次 | `existsByRelationId` + 原单须 SUCCESS | §3.3 序号 8、3 |
| 收口后原单仍 SUCCESS | `completeInpatientSettleCancelRefund` | 19 |

### 6.2 并发

| 承诺 | 说明 |
| --- | --- |
| 同 `paymentId` 并发反结 | 后者 `existsByRelationId` 或 SUCCESS 门禁失败 |

### 6.3 审计

| 承诺 | 说明 |
| --- | --- |
| `operatorId` / `entererId` | 写入逆向单与 Command |
| 可追溯 | 原 `paymentId`、逆向 `refundPaymentId`、本批 `chargeItemIds` |

## 7. 测试要点

### 7.1 功能

| 用例 | 预期 | 优先级 |
| --- | --- | --- |
| 反结后待结算 Tab 仍可见一正一负历史 | `IN_PROGRESS` 就诊 IF-IP-STL-07 只读成功；前端待结算 Tab 加载历史 | P0 |
| 待结算 Tab 无反结按钮 | 写侧仍仅已结算 Tab | P0 |
| 历史条：正向 SUCCESS 显示 `reverseEnabled=true` | 可点反结 | P0 |
| 反结成功：新增 **REVERSE** 负向行；原正向变 REFUNDED | 一正一负 | P0 |
| `netSettleTotal=0` | 全反结后净额为 0 | P0 |
| 同一 `paymentId` 再次反结 | **EX-IP-RVS-001** | P0 |
| 逆向行 `reverseEnabled=false` | 不可点 | P0 |
| 反结后插入 `PENDING_SETTLEMENT` 再生行；源 `SETTLED` 仍在历史快照可查 | 可再次结算 | P0 |
| 多轮结算/反结（≥2 轮）后取消执行/取消划价 | 历史链上 `SETTLED` 不挡 Gate；仅链末端 `PENDING_SETTLEMENT` 可操作 | P0 |
| 出院全结反结（未清床） | `COMPLETED→IN_PROGRESS`；出院嘱回草稿；**不**入待结算队列直至再办出院 | P0 |
| `DISCHARGED` 本页反结 | **EX-IP-RVS-003**（已离院请先召回） | P0 |
| `DISCHARGED` 召回（IF-IP-RCL-01） | `IN_PROGRESS`+WARD；`requiresBedAssignment=false` | P0 |
| 召回后二次全结无活动床 | `completeInpatientFinancialSettlement` 直接 `DISCHARGED` | P1 |
| 中途结算多笔（**后续**） | 启用 BR-IP-RVS-010 后再测 | 延后 |

### 7.2 非功能

| 用例 | 指标 |
| --- | --- |
| 单笔反结 | ≤ 2000ms（AS §7） |

## 8. 变更历史

| 版本 | 日期 | 变更摘要 |
| --- | --- | --- |
| v1.11 | 2026-10-09 | **BR-IP-RVS-007**：出院嘱改为 `softDeleteDischargeServiceRequestForReverseSettle`；Pre `resolveRemovableDischargeServiceRequestId`；Mapper `toSoftDeleteDischargeServiceRequestCommand`；对齐 **AS v1.8** |
| v1.10 | 2026-10-09 | **BR-IP-RVS-007**：出院嘱改为 `cancelServiceRequest`；Pre `resolveCancelableDischargeServiceRequestId`；Mapper `toCancelDischargeServiceRequestCommand`；对齐 **AS v1.7** |
| v1.9 | 2026-07-19 | **IF-IP-RVS-01**：afterCommit 清床汇总冲销 **3506**；对齐 **AS v1.6** / **09** |
| v1.8 | 2026-07-16 | **出院召回 redesign**：**IF-IP-RCL-01** `recallInpatientDischarge`；§3.4 替换 ATD-07/`RecallReverseSettleSupport`；§3.2 **`recallEnabled`**；**EX-IP-RCL-001～004**；召回 **`DISCHARGED→IN_PROGRESS`**；对齐 **AS v1.5** |
| v1.6 | 2026-07-11 | **成码回更**：`revertInpatientFinancialSettlement` 改为 **`COMPLETED→IN_PROGRESS`**；**BR-IP-RVS-006/009**；IF-07/IF-02 与 **FIN-003** 待结算队列仅 `PENDING` 对齐 |
| v1.5 | 2026-07-11 | **BR-IP-RVS-011**：反结改为 ChargeItem **复制再生**（源 `SETTLED` 终态 + `refund_id`）；仅处理支付单 `charge_item_ids` 快照；读侧 **BR-REF-020** 扩展至 `PENDING_SETTLEMENT` 再生行 |
| v1.4 | 2026-07-11 | 对齐 **FIN-003 v1.14**：已结算队列按 **INPATIENT_CLINIC 流水** + **住院号去重**；IF-07 只读门禁补充 **`DISCHARGED`/`REGISTERED`** |
| v1.3 | 2026-06-19 | **成码回更**：反结回退**本就诊全部 SETTLED** 行；移除 `revertDischargeAdvanceDepositOnCancel`；出院嘱 Pre 支持 DRAFT 幂等；`resolveRejectableDischargeServiceRequestId`；**IF-IP-STL-07** 放宽就诊态门禁（反结后 `PENDING` 等仍可只读查历史；待结算 Tab 展示历史 NET；反结写操作仍限已结算 Tab） |
| v1.2 | 2026-06-18 | ATD-07 内嵌改为 `RecallReverseSettlesupport；Pre 共用 `InpatientReverseSettlePresupport；§3.4/§2 与代码一致；医保撤销扩展位标注未接入 |
| v1.1 | 2026-06-18 | 应用层/读侧/ATD-07/前端已落地；增补 `DISCHARGE_SERVICE_REJECT_REASON_INPATIENT_REVERSE` |
| v1.0 | 2026-06-18 | 初版：**IF-IP-RVS-01** `reverseInpatientSettle`；**IF-IP-STL-07** 历史 NET / `reverseEnabled`；同包 `inpatient.financial.settle`；**本期无中途结算**；ATD-07 内嵌 §3.4；异常 **EX-IP-RVS-***；自 `cancelInpatientSettle` 迁移 |

---

**文档负责人**：（待填） · **评审**：（待填）
