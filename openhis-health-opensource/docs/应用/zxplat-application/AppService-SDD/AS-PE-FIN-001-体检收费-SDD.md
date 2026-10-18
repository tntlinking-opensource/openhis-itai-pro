# 体检收费 · 应用服务详细设计（SDD）

> 编码：AS-PE-FIN-001-SDD  
> 版本：v1.0 | 日期：2026-07-19  
> 来源契约：`AS-PE-FIN-001-体检收费.md` v1.0  
> **成码状态**：本文为成码契约；实现类与前端路由在文档确认后另开任务落地。

---

## 1. 实现边界

### 1.1 目标与边界

| 项 | 内容 |
|----|------|
| 模块 | 体检收费应用服务（收费员工作站：PE 待收/已收队列、明细、自费预结算/正式结算） |
| 边界 | 编排 PE Encounter / Patient / PeisEncounterExt / ChargeItem / Account / Contract / PaymentReconciliation / Invoice；**不**混入 AMB 门诊队列 |
| 编排权威 | `AS-PE-FIN-001-体检收费.md` §2、§4 |
| 支付编排复用 | 预结算/正式结算命令与校验语义对齐 **AS-OP-FIN-002**（`CreatePaymentDraftFromPreSettleCommand` / 门诊结算命令族）；体检侧可薄封装，禁止复制一套支付不变式 |
| IF 编号 | `IF-PE-CHG-01`～`IF-PE-CHG-07` |
| 非本轮 | 医保读卡、转账户按钮、退费深链、团检单位结算 UI |

### 1.2 术语与口径

| 项 | 约定 |
|----|------|
| HTTP | `POST /api/app/physicalExamCharge/{methodName}`（类名路由） |
| 菜单 / 路由 | 菜单「体检收费」· 前端 `/peis/charge` · 挂健康体检菜单 |
| 队列 | 仅 `Encounter.class = PE`；入选前提对齐 **BR-PE-CHG-002** |
| 页签 | `WAITING`（待收费）/ `COMPLETE`（已收费），对齐遗留 waiting/complete |
| REG-09 | 跳转 query：`encounterId`、`patientId`（字符串雪花） |
| 操作人 | `CurrentOperatorResolver`；禁止前端传 `organizationId` 收窄（**B.6.5**） |

---

## 2. 代码结构（目标态）

```text
zxplat/health-application/src/main/java/com/openhis/health/application/
  physicalexam/financial/charge/          # 或 peis/financial/charge，与团队包约定一致；推荐 physicalexam 与业务名对齐
    service/
      PhysicalExamChargeAppService.java   # IF-PE-CHG-01～07
    request/
      LoadPhysicalExamChargePageInitRequest.java
      PagePhysicalExamChargeQueueRequest.java
      LoadPhysicalExamChargeItemsRequest.java
      PreSettlePhysicalExamChargesRequest.java
      ConfirmPhysicalExamChargeSettlementRequest.java
      RefreshPhysicalExamChargeStatusRequest.java
    response/
      LoadPhysicalExamChargePageInitResponse.java
      PagePhysicalExamChargeQueueResponse.java
      LoadPhysicalExamChargeItemsResponse.java
      PreSettlePhysicalExamChargesResponse.java
      ConfirmPhysicalExamChargeSettlementResponse.java
      RefreshPhysicalExamChargeStatusResponse.java
    dto/
      PhysicalExamChargeQueueRowDto.java
      PhysicalExamChargeItemRowDto.java
    mapper/
      PhysicalExamChargeMapper.java
    support/
      PhysicalExamChargeEncounterQueueLoader.java   # PE 队列收敛（GAP-PE-CHG-01）
```

> **共享常量**（若需要）：`PhysicalExamChargeQueueTab` 等落 `zxplat-domain`（**B.10.2**）。  
> **前端**：`openhis-pro-ui/apps/web-ele` → `views/peis/charge/index.vue`（待成码）。

### 2.1 依赖注入表

| 依赖 | 用途 |
|------|------|
| `CurrentOperatorResolver` | 写路径操作人 |
| `CodeSystemQueryService` | IF-PE-CHG-01 状态下拉 |
| `EncounterQueryService` | PE 就诊存在性 / 摘要；Gate `class=PE` |
| `PatientQueryService`（或 Encounter 回补路径） | 队列患者摘要 |
| `PeisEncounterExt` 读口（QueryService / Repository 按现网） | 流水号、套餐名/快照展示 |
| `ChargeItemQueryService` | 队列入选、明细列表、`isEncounterPaid`（IF-PE-CHG-07） |
| `ChargeItemDomainService` | `preSettleChargeItems` / `settleChargeItems` |
| `AccountQueryService` | 自费账户解析（P0） |
| `ContractQueryService` | 预结算合同锚点 |
| `PaymentReconciliationDomainService` | 草稿创建、正式结算 |
| `PaymentReconciliationQueryService` | 草稿校验、账单标签投影 |
| `InvoiceAfterPaymentOrchestration` | 实付 > 0 开票草稿（**BR-PE-CHG-008**） |
| `PhysicalExamChargeEncounterQueueLoader` | IF-PE-CHG-02/03 分页 |

### 2.2 领域方法白名单（目标）

| 类型 | 方法语义 | 用于 IF |
|------|----------|---------|
| ChargeItemQuery | 按 PE + 收银口径分页 encounterId；按就诊列 PLANNED/SETTLED 明细；isEncounterPaid | 02/03/04/07 |
| ChargeItemDomain | preSettle / settle | 05/06 |
| PaymentReconciliationDomain | createDraftFromPreSettle / settlePayment | 05/06 |
| EncounterQuery | mustGet / PE 过滤 | Gate |
| PeisEncounterExt 读 | byEncounterIds | 02/03/04 |

---

## 3. HTTP 与方法映射

| IF | HTTP methodName | 读/写 |
|----|-----------------|:----:|
| IF-PE-CHG-01 | `loadPageInit` | 读 |
| IF-PE-CHG-02 | `pageWaitingQueue` | 读 |
| IF-PE-CHG-03 | `pageCompleteQueue` | 读 |
| IF-PE-CHG-04 | `loadChargeItems` | 读 |
| IF-PE-CHG-05 | `preSettleCharges` | 写 |
| IF-PE-CHG-06 | `confirmChargeSettlement` | 写 |
| IF-PE-CHG-07 | `refreshChargeStatus` | 读 |

统一前缀：`POST /api/app/physicalExamCharge/<methodName>`。

### 3.1 请求/响应要点（契约级，非字段表全量）

| IF | 请求要点 | 响应要点 |
|----|----------|----------|
| 01 | 可空 | 状态下拉 Option 列表 |
| 02/03 | `pageNo`/`pageSize`、关键字、业务日起止 | `rows`：就诊/患者/流水号/套餐/金额摘要 + `total` |
| 04 | `encounterId`（必填）；可选 `patientId` 校验一致 | 明细行 + 套餐摘要 + 合计 |
| 05 | `encounterId`、`chargeItemIds[]` | 草稿支付 ID、应付金额拆分展示 |
| 06 | `encounterId`、`paymentId`、明细 ID 集合、实付渠道拆分 | 成功标志；可选发票草稿提示 |
| 07 | `encounterId` | `paid` / `unpaid` / `unknown` |

**REG-09 跳转**：前端路由 `/peis/charge?encounterId={id}&patientId={id}` → 进入后调 IF-PE-CHG-04。

---

## 4. 编排要点

### 4.1 队列（02/03）

1. `PhysicalExamChargeEncounterQueueLoader`：收费事实收敛 + **强制** `Encounter.class=PE`。  
2. 批量回补 Patient、PeisEncounterExt。  
3. 按 BillingStatement / 支付投影打待收/已收标签（**BR-PE-CHG-003**），页签过滤。  
4. **不按**会话机构收窄（与门诊收银 B.6.5 一致，除非院点另有产品决议）。

### 4.2 明细（04）

1. Gate：encounter 存在且 `class=PE`，否则 `EX-PE-CHG-103`。  
2. 列 `PLANNED`/`SETTLED` 明细（**BR-PE-CHG-004**）。  
3. 套餐展示读 `PeisEncounterExt`。

### 4.3 预结算 / 正式结算（05/06）

1. 复用门诊 financial orchestration 命令形态与金额对齐规则。  
2. P0 **仅自费账户**路径；医保相关入口不暴露或 NoOp 占位。  
3. 正式结算成功后：ChargeItem→SETTLED；实付 > 0 → 发票草稿。  
4. **不**强制在本 AS 内推进分科 SR 状态（执行门禁依赖 REG-08 收费态）。

### 4.4 收费态（07）

委托既有 `ChargeItemQueryPort#isEncounterPaid`（与 REG-08 同源）。

---

## 5. 前端约定（目标态）

| 项 | 约定 |
|----|------|
| 路由 | `/peis/charge` |
| 布局 | 顶栏筛选（日期、关键字）+ 双 Tab（待收费 / 已收费）+ 左队列右明细 + 底部结算操作 |
| 遗留对照 | peis `/personal/charge` waiting / complete |
| 菜单 Flyway | 健康体检下新增「体检收费」，权限码与菜单表另开任务 |

---

## 6. 异常映射

| AS 码 | 实现口径 |
|-------|----------|
| EX-PE-CHG-101 | 初始化异常包装 |
| EX-PE-CHG-102 | 队列查询失败 |
| EX-PE-CHG-103 | 非 PE / 就诊不存在 |
| EX-PE-CHG-201 | 预结算领域异常上抛文案 |
| EX-PE-CHG-202 | 正式结算冲突/防重 |

---

## 7. 测试（成码后）

- API：PE 入队、AMB 不入队；02/03 页签；05→06 成功态；06 重复提交失败。  
- 跳转：REG-09 query 打开后 04 成功。  
- 与 REG-08：06 成功后 `isEncounterPaid=true`。

---

## 8. 关联文档

- AS：[AS-PE-FIN-001-体检收费](../AppService/AS-PE-FIN-001-体检收费.md)
- 门诊：[AS-OP-FIN-002-SDD](./AS-OP-FIN-002-门诊收费-SDD.md)
- 登记：[AS-PEIS-REG-001-SDD](./AS-PEIS-REG-001-个检登记-SDD.md)

---

## 修订记录

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.0 | 2026-07-19 | 首版成码契约；实现待另开任务 |
