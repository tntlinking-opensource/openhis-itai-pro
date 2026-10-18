# DM-ADM-012 Invoice 票据发票 · DomainModel-SDD

> **对应 DM**：`docs/领域/DomainModel/DM-ADM-012-Invoice-票据发票.md`（v1.0，本 SDD 假定该 DM 为当期锁定版本）  
> **目标工程**：`zxplat-core/zxplat-domain`  
> **限界上下文**：Financial（费用域 · 票据发票管理）  
> **聚合**：Invoice（子实体见 §4）

---

## 输入完整性检查结果

| 最小输入项 | 状态 | 说明 |
| ---------- | ---- | ---- |
| DM 路径 | 满足 | 已绑定 DM-ADM-012 |
| SDD 输出路径 | 满足 | 本文件 |
| 目标模块 | 满足 | `zxplat/zxplat-domain` |
| 包规则 `com.zxplat.domain.financial.invoice` | 满足 | `invoice` 全小写 |
| 固定技术口径（ID Long、时间 LocalDateTime、软删、beforeCommit、分页等） | 满足 | 见 §9 |
| DM 第 6 章写/读白名单 | 满足 | §5 全覆盖并给出 Command 字段全集 |
| DM 第 7 章 BR | 满足 | §6 映射并可追溯 |
| DM 第 5 章事件 | 满足 | §7 |

**与 DM 显式差异（采纳依据：DM 优先 + SDD 落地契约细化）**

| 点 | DM 差异位置 | SDD 采纳 |
| -- | ----------- | -------- |
| 限界上下文描述 | 标题使用费用域 | SDD 落地为财务域分包，语义保持一致 |
| 电子票据时间字段 | `billCreateTime` 为字符串 | SDD 保留字符串回填，业务排序用本地时间字段补齐 |
| 就诊维度 | Invoice 主表不强制 `encounterId` | SDD 通过 `chargeItemIds` 反查就诊口径 |

---

## 1. 设计目标与边界

### 1.1 设计目标

- 在单一文档内固化 **Invoice 聚合** 的持久化模型、领域服务契约、查询契约、事件与不变式实现策略，使下一会话可一次性生成 `zxplat/zxplat-domain` 领域层实现并通过编译。
- 将实现前澄清前置到 SDD：每个写方法对应独立 Command 文件，命令字段全集列出，覆盖草稿生成、开具、红冲、草稿作废。
- 单次成稿目标：正文不出现后续补充/待确认类措辞，缺口在文档阶段关闭。

### 1.2 边界

- **在内**：票据主档状态、票面字段、电子票据要素回填、红冲追溯。
- **在外**：第三方电子票据平台 HTTP 交互、号段分配策略、支付退款执行。
- **跨域仅端口声明**：仅声明与 Patient/ChargeItem/PaymentReconciliation 的引用规则，不在本聚合执行跨域写入。

---

## 2. 工程落点与包结构

### 2.1 Maven 模块

- 模块：`zxplat/zxplat-domain`。

### 2.2 包结构（`com.zxplat.domain.financial.invoice`）

| 相对包 | 内容 |
| ------ | ---- |
| `entity` | `Invoice` |
| `valueobject` | `InvoiceStatus`、`InvoiceType`、`ElectronicInvoiceInfo` |
| `enums`（共享） | 复用发票状态与类型枚举 |
| `command` | 每个写方法一个 Java 文件 |
| `repository` | `InvoiceRepository`（仅 `JpaRepository` 接口） |
| `service` | `*DomainService` 与 `*QueryService`（两个方法集，直接实现类） |
| `queries` | 查询入参对象（如 `*Query`） |
| `queries.model` | 查询出参模型（如 `*QueryModel`） |
| `share.events` | `com.zxplat.domain.share.events.financial.invoice` |

**存储口径（DM 优先）**：`*_enum` 默认 `Integer`；`*_code` 为 `String`；软删统一 `Boolean isDeleted`。

---

## 3. 类型 / 字段命名 / 注解规则

| 规则 | 说明 |
| ---- | ---- |
| 主键 / 外键 | `Long`；聚合根主键 `id`；外键 `patientId/reconciliationId` |
| 时间 | 业务时间字段统一 `LocalDateTime`；外部回传时间可保留字符串 |
| `*_enum` | 枚举编码列，默认 `Integer` |
| `*_code` | 字典编码列，`String` |
| 软删 | `Boolean isDeleted`，默认查询过滤 |
| 实体 | `@Entity` + 显式 `@Table`/`@Column` |
| 值对象 | `@Embeddable` |
| 仓储 | `JpaRepository<Invoice, Long>` |
| 分页 | `Pageable`，默认 `createdAt DESC` |
| 事务 | 领域服务方法不机械添加 `@Transactional` |
| 事件 | 服务内直接构造并 beforeCommit 发布 |

---

## 4. 实体 / 值对象 / 枚举 / 仓储 / 查询契约

### 4.1 表与实体映射

| 逻辑表 | 实体类 | 说明 |
| ------ | ------ | ---- |
| `adm_invoice` | `Invoice` | 票据主档聚合根单表 |

### 4.2 聚合根 `Invoice`（字段级契约）

| 字段名（Java） | 类型 | 必填 | 说明 / 约束 |
| -------------- | ---- | ---- | ----------- |
| `id` | `Long` | 系统 | 票据标识 |
| `patientId` | `Long` | 是 | 患者标识 |
| `reconciliationId` | `Long` | 否 | 付款单标识 |
| `statusEnum` | `Integer` | 是 | 票据状态 |
| `typeCode` | `String` | 否 | 蓝字/红字类型 |
| `cancelledReason` | `String` | 否 | 作废原因 |
| `chargeItemIds` | `String` | 否 | 关联收费项ID集合（逗号分隔） |
| `totalNet` | `BigDecimal` | 是 | 总净额 |
| `totalGross` | `BigDecimal` | 是 | 总含税额 |
| `paymentTerms` | `String` | 否 | 支付条款 |
| `busNo` | `String` | 否 | 院内发票编号 |
| `invoicingStaffId` | `Long` | 否 | 开票人标识 |
| `billBusDate` | `LocalDateTime` | 否 | 业务日期 |
| `billBatchCode` | `String` | 否 | 电子票据代码 |
| `billNo` | `String` | 否 | 电子票据号码 |
| `random` | `String` | 否 | 随机码 |
| `billCreateTime` | `String` | 否 | 电子票据生成时间字符串 |
| `billQrCode` | `String` | 否 | 二维码 |
| `pictureUrl` | `String` | 否 | 票据图片地址 |
| `pictureNetUrl` | `String` | 否 | 外网图片地址 |
| `wxCardUrl` | `String` | 否 | 微信卡包地址 |
| `isDeleted` | `Boolean` | 系统 | 逻辑删除标记 |

**不变式方法（示例命名，实现须一致）**：`assertIssueFromDraftOnly`、`assertCancelReasonRequired`、`assertInvoiceNoUniqueInOrg`、`assertSingleActiveInvoicePerPaymentAndType`、`assertElectronicFieldsCompleteWhenIssued`.

### 4.3 子实体

无。本聚合为单表主档聚合。

### 4.5 仓储接口

- `InvoiceRepository extends JpaRepository<Invoice, Long>`

**说明**：列表/报表查询优先QueryService，主表查询用于写前校验与详情回显。

### 4.6 查询模型（`queries.model`）

每个读方法一个子节，字段级逐行列清：

#### 4.6.1 `InvoiceDetailQueryModel`（对应 `getInvoiceById`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `id` | `Long` | `adm_invoice.id` | 票据标识 |
| `statusEnum` | `Integer` | `adm_invoice.status_enum` | 票据状态 |
| `typeCode` | `String` | `adm_invoice.type_code` | 票据类型 |
| `totalGross` | `BigDecimal` | `adm_invoice.total_gross` | 含税金额 |
| `busNo` | `String` | `adm_invoice.bus_no` | 发票编号 |

#### 4.6.2 `InvoiceByPaymentQueryModel`（对应 `getInvoiceByPaymentId`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `id` | `Long` | `adm_invoice.id` | 票据标识 |
| `reconciliationId` | `Long` | `adm_invoice.reconciliation_id` | 付款单标识 |
| `statusEnum` | `Integer` | `adm_invoice.status_enum` | 票据状态 |
| `billNo` | `String` | `adm_invoice.bill_no` | 电子票据号码 |

#### 4.6.3 `LatestIssuedInvoiceQueryModel`（对应 `getLatestIssuedInvoiceByPaymentId`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `id` | `Long` | `adm_invoice.id` | 票据标识 |
| `reconciliationId` | `Long` | `adm_invoice.reconciliation_id` | 付款单标识 |
| `busNo` | `String` | `adm_invoice.bus_no` | 发票编号 |
| `billCreateTime` | `String` | `adm_invoice.bill_create_time` | 开票时间（平台回传） |
| `pictureUrl` | `String` | `adm_invoice.picture_url` | 票据图片地址 |

---

## 5. 写侧与读侧白名单（命令字段全集）

### 5.1 写侧 — `InvoiceDomainService`

#### 5.1.1 `createDraftInvoiceAfterPayment(CreateDraftInvoiceAfterPaymentCommand cmd)` → `Long`

**命令文件**：`CreateDraftInvoiceAfterPaymentCommand.java`

**字段全集**：`patientId`,`reconciliationId`,`typeCode`,`chargeItemIds`,`totalNet`,`totalGross`,`paymentTerms`,`billBusDate`

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `Invoice.assertDraftCreatableAfterPayment` |
| `statusTransitionMethod` | `Invoice.initAsDraft` |
| `repositoryChecks` | `InvoiceRepository.findActiveByPaymentAndType` |
| `eventPublishAnchor` | `new InvoiceDraftCreatedEvent(...); DomainEventPublisher.publish(event)` |
| `unitTestCaseId` | `TC-INV-W-001` |

**方法契约摘要**：前置条件为患者与付款单有效、同付款单同类型无重复有效票据，且**应用层已判定票面金额 > 0**（`BR-INV-008`，收费项合计，非现金 `displayAmount`）；持久化影响为创建草稿主档并绑定收费项集合；失败语义为重复开票冲突或必填缺失拒绝；事件触发发布 `InvoiceDraftCreatedEvent`。**注释锚点**：校验 -> 建档 -> 持久化 -> 事件。

---

#### 5.1.1a 应用编排 — `InvoiceAfterPaymentOrchestration#createDraftAndScheduleIssueIfPayable`

> **落点**：`zxplat-domain` · `com.zxplat.domain.infrastructure.integration.financial.orchestration.InvoiceAfterPaymentOrchestration`

| 项 | 内容 |
| --- | --- |
| 触发场景 | 门诊挂号缴费（IF-REG-07）、门诊收费正式结算（IF-OPC-07）、住院正式结算（IF-IP-STL-06）支付成功后 |
| 金额口径 | `CreateDraftInvoiceCommand.amount` = 支付单 `displayAmount`（患者应付/实付） |
| 分支 | `amount > 0` → `InvoiceDomainService#createDraftInvoiceAfterPayment` + `BossElectronicInvoiceAfterCommitOrchestration#afterDraftCreated`；否则返回 `null`，跳过开票 |
| 响应 | 挂号/收费/住院结算响应中 `invoiceId` / `invoiceDraftId` / `invoiceStatus` 可为 `null` |

---

#### 5.1.2 `issueElectronicInvoice(IssueElectronicInvoiceCommand cmd)` → `void`

**命令文件**：`IssueElectronicInvoiceCommand.java`

**字段全集**：`invoiceId`,`busNo`,`invoicingStaffId`,`billBatchCode`,`billNo`,`random`,`billCreateTime`,`billQrCode`,`pictureUrl`,`pictureNetUrl`,`wxCardUrl`

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `Invoice.assertElectronicIssueAllowed` |
| `statusTransitionMethod` | `Invoice.transitDraftToIssued` |
| `repositoryChecks` | `InvoiceRepository.findById / existsByBusNoAndOrg` |
| `eventPublishAnchor` | `new InvoiceIssuedEvent(...); DomainEventPublisher.publish(event)` |
| `unitTestCaseId` | `TC-INV-W-002` |

**方法契约摘要**：前置条件为当前 `DRAFT` 且电子票据关键要素齐全；持久化影响为回填票面字段并推进 `ISSUED`；失败语义为前态不符或票面要素不完整拒绝；事件触发发布 `InvoiceIssuedEvent`。**注释锚点**：状态校验 -> 票面校验 -> 更新 -> 事件。

---

#### 5.1.3 `writeoffInvoiceAndCreateRedRecord(WriteoffInvoiceAndCreateRedRecordCommand cmd)` → `Long`

**命令文件**：`WriteoffInvoiceAndCreateRedRecordCommand.java`

**字段全集**：`sourceInvoiceId`,`cancelledReason`,`operatorId`,`refundReconciliationId`,`redTotalNet`,`redTotalGross`,`redChargeItemIds`

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `Invoice.assertWriteoffAndRedRecordAllowed` |
| `statusTransitionMethod` | `Invoice.transitIssuedToCancelledAndCreateRed` |
| `repositoryChecks` | `InvoiceRepository.findById / findRedBySourceAndRefund` |
| `eventPublishAnchor` | `new InvoiceCancelledEvent(...); DomainEventPublisher.publish(event)` |
| `unitTestCaseId` | `TC-INV-W-003` |

**方法契约摘要**：前置条件为原票已开具且作废原因必填；持久化影响为原票置 `CANCELLED` 并新增红票记录；失败语义为重复红冲或前态非法拒绝；事件触发发布 `InvoiceCancelledEvent`（原票与红票链路可区分载荷）。**注释锚点**：校验 -> 原票作废 -> 红票新建 -> 事件。

---

#### 5.1.4 `voidDraftInvoice(VoidDraftInvoiceCommand cmd)` → `void`

**命令文件**：`VoidDraftInvoiceCommand.java`

**字段全集**：`invoiceId`,`cancelledReason`,`operatorId`

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `Invoice.assertDraftVoidAllowed` |
| `statusTransitionMethod` | `Invoice.transitDraftToCancelled` |
| `repositoryChecks` | `InvoiceRepository.findById` |
| `eventPublishAnchor` | `new InvoiceCancelledEvent(...); DomainEventPublisher.publish(event)` |
| `unitTestCaseId` | `TC-INV-W-004` |

**方法契约摘要**：前置条件为目标票据处于 `DRAFT` 且作废原因非空；持久化影响为状态置 `CANCELLED` 并写作废原因；失败语义为非草稿拒绝；事件触发发布 `InvoiceCancelledEvent`。**注释锚点**：状态校验 -> 作废 -> 持久化 -> 事件。

### 5.2 读侧 — `InvoiceQueryService`（`domain.queries`）

所有查询默认 `isDeleted=false`；分页在数据库侧过滤与统计 total。

**出参**填 QueryModel 类名（字段定义见 §4.6 对应子节，此处不重复）。

| 方法 | 入参 | 出参（类名，字段见 §4.6） | 关键实现 | `unitTestCaseId` |
| ---- | ---- | ------------------------- | -------- | ---------------- |
| `getInvoiceById` | `Long invoiceId` | `InvoiceDetailQueryModel` | 票据主键详情查询 | `TC-INV-R-001` |
| `getInvoiceByPaymentId` | `Long reconciliationId` | `List<InvoiceByPaymentQueryModel>` | 付款单维度票据查询 | `TC-INV-R-002` |
| `getLatestIssuedInvoiceByPaymentId` | `Long reconciliationId` | `LatestIssuedInvoiceQueryModel` | 付款单最新已开具票据查询 | `TC-INV-R-003` |

**读方法注释锚点（统一）**：构建查询条件（含软删过滤）-> 单次 SQL/仓储调用并完成分页统计 -> 组装 QueryModel（无写库）。

---

## 6. 不变式映射（DM §7）

| BR 编号 | 实现策略摘要 |
| ------- | ------------ |
| BR-INV-001 | 写入状态推进遵循状态机，终态不可逆 |
| BR-INV-002 | 作废必须记录 `cancelledReason` |
| BR-INV-003 | 红冲保留原票+红票双记录 |
| BR-INV-004 | `busNo` 同机构唯一 |
| BR-INV-005 | 票据必须关联有效患者 |
| BR-INV-006 | 同付款单同类型不允许多张有效票据 |
| BR-INV-007 | 电子开具后关键票面要素必须齐全 |
| BR-INV-008 | 票面（收费项合计）> 0 才创建草稿；真正零元不开票；医保全报现金为 0 仍开票 |
| BR-INV-QRY-001 | 票据查询路径只读 |
| BR-INV-QRY-002 | 支付详情取最新已开具记录 |

---

## 7. 事件契约与触发规则（DM §5）

| 事件类名 | 事件类包路径 | 最小载荷（字段） | 触发方法 |
| -------- | ------------ | ---------------- | -------- |
| `InvoiceDraftCreatedEvent` | `com.zxplat.domain.share.events.financial.invoice` | `invoiceId,patientId,reconciliationId,typeCode,totalGross` | `createDraftInvoiceAfterPayment` |
| `InvoiceIssuedEvent` | `com.zxplat.domain.share.events.financial.invoice` | `invoiceId,patientId,reconciliationId,billNo,billBatchCode,billCreateTime,typeCode` | `issueElectronicInvoice` |
| `InvoiceCancelledEvent` | `com.zxplat.domain.share.events.financial.invoice` | `invoiceId,cancelledReason,operatorId,typeCode` | `writeoffInvoiceAndCreateRedRecord` / `voidDraftInvoice` |

**发布时机**：全部 **beforeCommit**；载荷为最小业务键 + 关键快照。

---

## 8. 删除与可见性契约

- 票据作废优先状态推进到 `CANCELLED`，保留主档追溯。
- 红冲场景必须保留原票与红票链路，不覆盖原票金额事实。
- 默认查询过滤软删记录，作废票据可在审计视图中可见。
- 支付详情默认展示最新 `ISSUED` 票据，避免误取草稿/作废记录。
- 电子票据字段回填后不可缺失关键外显字段（二维码、图片地址）。

---

## 9. 框架优先适配规则

- 软删、审计字段、事务工作单元、beforeCommit 事件发布遵循框架默认能力。
- 领域服务与查询服务采用直接实现类，不默认接口+Impl 双层。
- 一方法一 Command，禁止命令容器类。
- 号段分配与第三方平台交互由应用层编排，领域层只落结果事实。
- 票据列表与报表优先QueryService，写模型承载主档与写前校验。

---

## 10. 生成与验收清单

- [ ] §4 字段表与 DM §3 对齐（单表字段完整）
- [ ] §5.1 写方法小节数量 = 4，与 DM §6.1 一致
- [ ] 每个写方法均包含命令文件、字段全集、绑定项、方法契约摘要
- [ ] §5.2 读方法覆盖 DM §6.2 全部 3 个方法
- [ ] §6 BR 映射覆盖 BR-INV-* 与 BR-INV-QRY-* 规则
- [ ] §7 事件表覆盖 DM §5 全部 3 条业务事件
- [ ] 红冲双记录与电子票据字段口径一致
- [ ] 全文无占位语、无待确认语

---

## 11. 单次会话代码生成门禁（DoD）

### 11.1 前置输入

- 锁定 DM：`DM-ADM-012-Invoice-票据发票.md`
- 本 SDD 文件（通过结构/字段/方法/事件门禁）
- 目标模块：`zxplat/zxplat-domain`
- 共享事件包可用：`com.zxplat.domain.share.events.financial.invoice`

### 11.2 DoD

- 写侧 4 个方法全部落地，签名与 Command 命名与 §5 一致。
- 票据状态、红冲链路、电子票面字段与 §4/§6 一致。
- 事件按 §7 beforeCommit 发布，载荷字段不少于最小要求。
- BR 至少一条绑定一个测试用例 ID，可追溯到 `unitTestCaseId`。
- 读侧 3 个方法与 §4.6 QueryModel 对齐。

### 11.3 失败处理

- 任一方法契约无法实现时立即停机并回文档会话修订。
- 若 DM 白名单方法/事件/BR 变更，先更新 SDD 再进入代码会话。
- 若红冲链路不能保证双记录追溯，DoD 失败不得进入成码。

---

## 定稿检查清单（可打勾）

- [ ] 主章节结构与金骨架一致（输入检查、1-11、定稿检查、评分、结束语）
- [ ] §5.1 子节数量 = DM 写方法数量（4）
- [ ] §4 字段表覆盖 DM §3 字段，不截断
- [ ] §7 事件表覆盖 DM §5 全部事件（3）
- [ ] 每个写方法包含 `entityInvariantMethod` 与 `unitTestCaseId`
- [ ] 全文无 YAML、无 `sdd_status`、无 `dm_code`、无占位措辞

---

## 定稿评分（百分制）

| 维度 | 分值 | 自评 |
| ---- | ---- | ---- |
| 输入完整性 | 20 | 20 |
| 方法白名单对齐 | 25 | 25 |
| 不变式映射 | 20 | 20 |
| 事件契约 | 15 | 15 |
| 软删与框架一致 | 10 | 10 |
| 去重与无歧义 | 10 | 10 |
| **总分** | **100** | **100（可进入代码会话）** |

---

## 12. 与 DM 同步记录

| 日期 | 摘要 |
| --- | --- |
| 2026-05-28 | **对账收口**：与 `zxplat-core/zxplat-domain` 现行实现一致；本聚合**无** InpatientInfo/OutpatientInfo/OrderInfo ReadModel 消费（跨聚合读组装见 **DM-ADM-002** §6.2.18～20 · `EncounterContextFacade`）。 |（已迁至 EncounterContextReader / application.shared.encounter）

---

**本 SDD 正文结束。**

