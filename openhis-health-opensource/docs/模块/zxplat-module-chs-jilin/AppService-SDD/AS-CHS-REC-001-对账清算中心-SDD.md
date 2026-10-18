# 对账清算中心 · 应用服务详细设计（SDD）

> 编码：**AS-CHS-REC-001-SDD**  
> 版本：**v0.4** | 日期：**2026-09-29**  
> 编排权威：AS-CHS-REC-001

## 1. 实现边界

| 项 | 内容 |
| --- | --- |
| 模块 | 医保对账清算中心（单 AppService，Tab：日对账 / 月清算） |
| AppService | `InsuranceReconciliationAppService` |
| HTTP 前缀 | `/api/app/insuranceReconciliation` |
| 边界 | 3201–3209A、9101/9102、本地 list/汇总；**不含** 1320 工伤 Tab |
| DTO 形态 | `request/` + `response/`（P1 已落地） |

### 1.1 实现裁决

| 裁决 ID | 内容 |
| --- | --- |
| **REC-SDD-R-001** | `reconcileDetailLedger` **必须**经 `Recon3202Workflow`（9101→3202→9102） |
| **REC-SDD-R-002** | 本地 list 经 `InsuranceReconciliationAppMapper` 转 Response |
| **REC-SDD-R-003** | 工伤 1320 走 `work-injury` 模块 HTTP，**不**经本 AppService |
| **REC-SDD-R-004** | 本地汇总/明细预览金额与笔数口径固定为 **BR-CHS-REC-008**：正向累加（含已撤销回执）、同窗撤销抵减（一收一退净 0）；`setlCount` = 正向笔数 + 撤销笔数；3202 正向行**仅** `status=CONFIRMED`（`refd_setl_flag=0`），已 `REVOKED` **禁止**再打正向，只经 `fromReversalRows` 出反向（`revoke_setl_id` + `refd_setl_flag=1`） |

## 2. 代码结构

```text
com/openhis/chs/application/reconciliation/
  service/
    InsuranceReconciliationAppService.java
  request/
    ListLocalReconRecordsRequest.java
  response/
    ListLocalReconRecordsResponse.java
    ListLocalReconDailyResponse.java
    ListLocalClearAppliesResponse.java
    LocalReconDetailResponse.java
    LocalReconDailyResponse.java
    LocalClearApplyResponse.java
  mapper/
    InsuranceReconciliationAppMapper.java

com/openhis/chs/domain/reconciliation/
  service/
    YbReconciliationQueryService.java   # summarizeClearAmounts / loadDetailRows
    YbReconciliationDomainService.java
  support/
    ReconDetailRowSupport.java          # 正向 + 撤销反向行装配

com/openhis/chs/domain/settlement/
  service/
    YbSettlementRecordQueryService.java     # listCreatedBetween / mapByIds
    YbSettlementReversalQueryService.java   # listReversedBetween（对账读侧）

com/openhis/chs/bridge/reconciliation/
  invoker/
    Recon3201Invoker.java … Recon3209AInvoker.java
    Recon9101Invoker.java
  workflow/
    Recon3202Workflow.java
  support/
    ReconPlatformSupport.java
    ReconDetailFileSupport.java
```

**共享 Invoker**：`Cat9102Invoker` 位于 `catalog/download/invoker/`（对账 3202 与目录下载共用）。

## 3. HTTP 接口 ↔ IF 映射

| HTTP 方法 | IF 编号 | 读/写 | 编排落点 |
| --- | --- | --- | --- |
| `reconcileDailyLedger` | IF-CHS-REC-W-01 | 写 | `Recon3201Invoker.invoke`；金额缺省 → `findClearAmountSummary` |
| `reconcileDetailLedger` | IF-CHS-REC-W-02 | 写 | `Recon3202Workflow.invoke`（明细行含撤销反向） |
| `previewReconDetailRows` | IF-CHS-REC-R-01 | 读 | `YbReconciliationQueryService.loadDetailRows` |
| `summarizeClearAmounts` | IF-CHS-REC-R-02 | 读；出参 `List`（按 `insutype`+`clrType` 分组） | `YbReconciliationQueryService.summarizeClearAmounts` |
| `listLocalReconDetail` | IF-CHS-REC-R-L1 | 读 | `YbReconciliationDomainService` + Mapper |
| `listLocalReconDaily` | IF-CHS-REC-R-L2 | 读 | 同上 |
| `listLocalClearApplies` | IF-CHS-REC-R-L3 | 读 | 同上 |
| `queryClearOrganizations` | IF-CHS-REC-W-06 | 读 | `Recon3206AInvoker.invoke` |
| `applyJilinClear` | IF-CHS-REC-W-03 | 写 | `Recon3203AInvoker.invoke` |
| `queryClearApplyStatus` | IF-CHS-REC-W-05 | 读 | `Recon3205AInvoker.invoke` |
| `revokeJilinClear` | IF-CHS-REC-W-04 | 写 | `Recon3204AInvoker.invoke` |
| `queryCrossProvinceReconFailures` | IF-CHS-REC-R-09 | 读 | `Recon3209AInvoker.invoke` |

### 3.1 IF-CHS-REC-R-02 契约（`summarizeClearAmounts`）

| 项 | 内容 |
| --- | --- |
| **入参** | `SummarizeClearAmountCommand`：`contractBusNo`、`contractId`、`begndate`、`enddate`；可选 `insutype`、可选 `clrType`（非空时仅返回匹配维度行） |
| **出参** | `List<ClearAmountSummaryResult>`：每行含 `insutype`、`clrType`、`medfeeSumamt`、`medSumfee`、`fundAppySum`、`cashPayamt`、`acctPay`、`setlCount` |
| **数据源** | ① `YbSettlementRecordQueryService.listCreatedBetween`（左闭右开，按创建时间）；② `YbSettlementReversalQueryService.listReversedBetween`（按 `reversed_at`）；③ `YbInpatientPreSettleQueryService.listSettledBetween`（住院已结算预结算，`clrType` 固定 `"21"`） |
| **分组** | 门诊/住院正式结算用快照 `insutype`+`clr_type`；住院预结算无 `clr_type` 时固定 `clrType="21"`；撤销行维度回查原结算 `mapByIds` |
| **金额 / 笔数** | **BR-CHS-REC-008** / **REC-SDD-R-004**：四项金额 = 正向累加 − 同窗撤销（绝对值抵减，一收一退净 0）；`setlCount` = 正向笔数 + 撤销笔数（一收一退计 2） |
| **辅助** | `mergeClearAmountSummaries`：将多行折叠为区间合计（3202 总额缺省补齐）；`findClearAmountSummary`：按险种+清算类别取一行（3201 金额缺省补齐） |
| **与 W-01 互斥** | R-02 **不**调用医保平台；真正对总账由 `reconcileDailyLedger`（W-01）出站 3201，金额缺省时用 `findClearAmountSummary` 按本维补齐 |

### 3.2 IF-CHS-REC-R-01 契约（`previewReconDetailRows` / `loadDetailRows`）

| 项 | 内容 |
| --- | --- |
| **入参** | 同 R-02 日期区间命令（`SummarizeClearAmountCommand`） |
| **出参** | `List<ReconDetailRowResult>`：`setlId`、`mdtrtId`、`psnNo`、金额列、`refdSetlFlag`、`source` |
| **正向行** | 同窗结算快照中 **仅 `CONFIRMED`**；`refd_setl_flag=0`（`ReconDetailRowSupport.fromSettlementRows`，内亦跳过非 CONFIRMED）；**不含 REVOKED**（曾为躲「未检索到对账信息」塞入正向，导致「退费状态不一致」，已废止） |
| **反向行** | 同窗撤销凭证；`refd_setl_flag=1`（`fromReversalRows`）；结算 ID 优先 `revoke_setl_id`，否则回落原结算；跨日退费时原结算可在窗外，经 `mapByIds` 补装；已撤销只走本路径 |
| **文件落点** | W-02 经 `ReconDetailFileSupport.buildTabTxtBytes` 写入 9101 上传 TXT（含 flag 列） |

## 4. 依赖注入表

| 依赖 | 用途 |
| --- | --- |
| `Recon3201Invoker` | 3201 日对账 |
| `Recon3202Workflow` | 3202 明细对账链 |
| `Recon3203AInvoker` … `Recon3209AInvoker` | 清算/跨省 |
| `YbReconciliationDomainService` | 本地流水写/查 |
| `YbReconciliationQueryService` | 分组汇总（`summarizeClearAmounts`）、`merge` / `find`、预览行（含撤销反向） |
| `YbSettlementRecordQueryService` | 正向结算时间窗 / 按 ID 批量回查 |
| `YbSettlementReversalQueryService` | 撤销时间窗（对账金额抵减与反向明细） |
| `YbInpatientPreSettleQueryService` | 住院已结算预结算时间窗 |
| `InsuranceReconciliationAppMapper` | Request ↔ Command / Result ↔ Response |

## 5. 3202 编排序列（Recon3202Workflow）

```text
1. loadDetailRows（CONFIRMED 正向 + 撤销反向；不含 REVOKED 正向）→ ReconDetailFileSupport 生成明细文件
2. Recon9101Invoker 上传 → file_qury_no
3. Recon3202Invoker 申请明细对账（总额缺省时 merge 区间合计，口径同 BR-CHS-REC-008）
4. Cat9102Invoker 下载结果 ZIP
5. 解析 + YbReconciliationDomainService 持久化
```

## 6. 事务与异常

| 项 | 约定 |
| --- | --- |
| 事务 | 3202 整链单事务（Workflow 入口方法经 AppServiceAspect） |
| 平台失败 | Invoker 内 `requireSuccess`；3202 任一步失败则回滚 |

## 7. 关联

- 前端：`openhis-pro-ui/.../chs/reconciliation/index.vue`
- API：`openhis-pro-ui/.../chs/api/reconciliation.ts`
- AS：[AS-CHS-REC-001-对账清算中心.md](../AppService/AS-CHS-REC-001-对账清算中心.md)
- 结算撤销表：`19-结算领域模型定稿.md` §5.3 `yb_settlement_reversal`

## 8. 修订记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v0.2 | 2026-07-26 | R-02 分组 List、find/merge 与 W-01 分工 |
| v0.3 | 2026-07-31 | **REC-SDD-R-004 / BR-CHS-REC-008**：一收一退金额抵净、笔数计 2；R-01 反向行；依赖 `YbSettlementReversalQueryService` |
| v0.4 | 2026-09-29 | **REC-SDD-R-004**：3202 `loadDetailRows` 正向仅 CONFIRMED；REVOKED 只经 `fromReversalRows`（修退费状态不一致） |
