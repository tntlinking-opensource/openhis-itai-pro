# DM-CUS-015 PrescriptionReview 处方点评 · DomainModel-SDD

> **对应 DM**：`docs/领域/DomainModel/DM-CUS-015-PrescriptionReview-处方点评.md`（本 SDD 假定该 DM 为当期锁定版本）  
> **目标工程**：`zxplat-core/zxplat-domain`  
> **限界上下文**：CUS 本地扩展 · 处方点评  
> **聚合**：PrescriptionReview（子实体见 §4）

---

## 输入完整性检查结果

| 最小输入项 | 状态 | 说明 |
| ---------- | ---- | ---- |
| DM 路径 | 满足 | 已绑定 DM-CUS-015 |
| SDD 输出路径 | 满足 | 本文件 |
| 目标模块 | 满足 | `zxplat-domain`（实体/服务）+ `zxplat-domain`（枚举/事件/常量）+ Flyway `zxplat-domain` |
| 包规则 `com.zxplat.domain.custom.prescriptionreview` | 满足 | `yyy` 全小写 |
| 固定技术口径（ID Long、时间 LocalDateTime、软删、beforeCommit、分页等） | 满足 | 见 §3、§9 |
| DM 第 6 章写/读白名单 | 满足 | §5 覆盖写方法 3 个、读方法 4 个 |
| DM 第 7 章 BR | 满足 | §6 映射 BR-PRV-001～005 |
| DM 第 5 章事件 | 满足 | §7 覆盖 2 个事件 |

---

## 1. 设计目标与边界

### 1.1 设计目标

- 在单一文档内固化 **PrescriptionReview 聚合** 的持久化模型、领域服务契约、查询契约、事件与不变式实现策略，使下一会话可 **一次性** 生成领域层实现并通过编译。
- 将「实现前澄清」前置到 SDD：**每个写方法对应独立 Command 文件**，命令字段全集列出。
- **单次成稿目标**：正文不出现「后续补充」「待确认」类措辞；缺口须在文档阶段关闭。

### 1.2 边界

- **在内**：点评实体、有效一对一约束、原因必填、软删撤销、本聚合读查询与事件。
- **在外**：是否已发药 Gate、发药页开关、门诊处方查看拼装、导出 Excel、人员姓名字典回填。
- **跨域仅端口声明**：以 `prescription_no` / `bus_no` / `reviewer_id` 引用外域，不落库外域实体、不注入外域 Repository。

---

## 2. 工程落点与包结构

### 2.1 Maven 模块

- 模块：`zxplat-domain`（实体/命令/仓储/领域服务/查询）；`zxplat-domain`（枚举/事件/错误码/聚合类型常量）；`zxplat-domain`（Flyway `V15__cus_prescription_review.sql`）。

### 2.2 包结构（`com.zxplat.domain.custom.prescriptionreview`）

| 相对包 | 内容 |
| ------ | ---- |
| `entity` | `PrescriptionReview` |
| `valueobject` | 无 |
| `enums`（共享） | `com.zxplat.domain.share.enums.PrescriptionReviewStatus` |
| `command` | 一方法一命令：`CreatePrescriptionReviewCommand`、`CreatePrescriptionReviewsCommand`、`RevokePrescriptionReviewCommand`；批量子项 `CreatePrescriptionReviewItemCommand` |
| `repository` | `PrescriptionReviewRepository` |
| `service` | `PrescriptionReviewDomainService` 与 `PrescriptionReviewQueryService` |
| `queries` | `PrescriptionReviewPageQuery` |
| `queries.model` | `PrescriptionReviewQueryModel` |
| `share.events` | `com.zxplat.domain.share.events.custom.prescriptionreview` |

**存储口径（DM 优先）**：`status_enum` → `String`（存枚举 code）；`reasonable_flag` → `Boolean`；字典码列本期无。

---

## 3. 类型 / 字段命名 / 注解规则

| 规则 | 说明 |
| ---- | ---- |
| 主键 / 外键 | `Long`；`id`、`reviewerId` |
| 时间 | `reviewedAt` → `LocalDateTime`；审计字段 `LocalDateTime` |
| `*_enum` | `String`（存枚举 code） |
| `*_code` | `String`（本期无） |
| `_flag` | `Boolean` |
| 软删 | 基类 `isDeleted`；读默认过滤未删除 |
| 实体 | `@Entity` + `@Table(name="cus_prescription_review")` + `@Column` |
| 仓储 | 仅接口，无默认实现类；禁 `@Query` |
| 分页 | `Pageable`；未指定排序默认 `reviewedAt DESC` |
| 事务 | 领域服务不声明 `@Transactional` |
| 事件 | `new XxxEvent` + `domainEventPublisher.publish`（框架 beforeCommit） |

---

## 4. 实体 / 值对象 / 枚举 / 仓储 / 查询契约

### 4.1 表与实体映射

| 逻辑表 | 实体类 | 说明 |
| ------ | ------ | ---- |
| `cus_prescription_review` | `PrescriptionReview` | 单表聚合根 |

### 4.2 聚合根 `PrescriptionReview`（字段级契约）

| 字段名（Java） | 类型 | 必填 | 说明 / 约束 |
| -------------- | ---- | ---- | ----------- |
| `id` | `Long` | 是 | 主键，雪花 ID |
| `busNo` | `String` | 是 | 列 `bus_no`；本聚合日序自生成（前缀 PRV） |
| `prescriptionNo` | `String` | 是 | 列 `prescription_no`；有效行唯一锚点 |
| `reviewerId` | `Long` | 是 | 列 `reviewer_id`；点评人 |
| `reviewedAt` | `LocalDateTime` | 是 | 列 `reviewed_at`；点评时间 |
| `reasonableFlag` | `Boolean` | 是 | 列 `reasonable_flag`；是否合理 |
| `statusEnum` | `String` | 是 | 列 `status_enum`；有效行固定 `REVIEWED` |
| `reason` | `String` | 否 | 列 `reason`；不合理必填 |
| `remark` | `String` | 否 | 列 `remark`；备注 |

**不变式方法**：`assertRequiredFields`、`assertReasonWhenUnreasonable`、`markReviewed`、`assertCanRevoke`。

### 4.3 子实体

无。本聚合为单表。

### 4.5 仓储接口

- `PrescriptionReviewRepository extends JpaRepository<PrescriptionReview, Long>`
- `existsByPrescriptionNoAndIsDeletedFalse(String prescriptionNo)`
- `findByPrescriptionNoAndIsDeletedFalse(String prescriptionNo)`
- `findByPrescriptionNoInAndIsDeletedFalse(Collection<String> prescriptionNos)`
- `findByIdAndIsDeletedFalse(Long id)`

### 4.6 查询模型（`queries.model`）

#### 4.6.1 `PrescriptionReviewQueryModel`（对应 `findActiveByPrescriptionNo` / `mapActiveByPrescriptionNos` / `getById` / `pageReviews` 行）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `id` | `Long` | `cus_prescription_review.id` | 点评主键 |
| `busNo` | `String` | `cus_prescription_review.bus_no` | 点评业务号（PRV 日序） |
| `prescriptionNo` | `String` | `cus_prescription_review.prescription_no` | 处方号 |
| `reviewerId` | `Long` | `cus_prescription_review.reviewer_id` | 点评人 |
| `reviewedAt` | `LocalDateTime` | `cus_prescription_review.reviewed_at` | 点评时间 |
| `reasonableFlag` | `Boolean` | `cus_prescription_review.reasonable_flag` | 是否合理 |
| `statusEnum` | `String` | `cus_prescription_review.status_enum` | 点评状态 code |
| `reason` | `String` | `cus_prescription_review.reason` | 原因 |
| `remark` | `String` | `cus_prescription_review.remark` | 备注 |

---

## 5. 写侧与读侧白名单（命令字段全集）

### 5.1 写侧 — `PrescriptionReviewDomainService`

#### 5.1.1 `createReview(CreatePrescriptionReviewCommand cmd)` → `Long`

**命令文件**：`CreatePrescriptionReviewCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `prescriptionNo` | `String` | 处方号 |
| `reviewerId` | `Long` | 点评人 |
| `reviewedAt` | `LocalDateTime` | 点评时间；空则领域用当前时间 |
| `reasonableFlag` | `Boolean` | 是否合理 |
| `reason` | `String` | 原因 |
| `remark` | `String` | 备注 |
| `operatorId` | `Long` | 操作人（事件载荷） |

> **业务号**：`bus_no` **不**由命令传入；`createReview` 内调用 `BusinessSequenceService#getSeqByDay(PRESCRIPTION_REVIEW, BUS_NO_SEQ_LENGTH)` 自生成（前缀 PRV）。

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `assertRequiredFields` + `assertReasonWhenUnreasonable` + `markReviewed` |
| `statusTransitionMethod` | `markReviewed`（写入 `REVIEWED`） |
| `repositoryChecks` | `existsByPrescriptionNoAndIsDeletedFalse` 须为 false |
| `eventPublishAnchor` | `new PrescriptionReviewCreatedEvent(...)` + `publish` |
| `unitTestCaseId` | `TC-DM-CUS-015-W-001` |

**方法契约摘要**：前置无有效点评且必填/原因规则通过；持久化 INSERT；失败时抛业务异常；事件 beforeCommit。**注释锚点**：校验一对一 → 装配实体 → 持久化 → 发布创建事件。

---

#### 5.1.2 `createReviews(CreatePrescriptionReviewsCommand cmd)` → `List<Long>`

**命令文件**：`CreatePrescriptionReviewsCommand.java`；子项 `CreatePrescriptionReviewItemCommand.java`

**字段全集（命令头）**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `items` | `List<CreatePrescriptionReviewItemCommand>` | 待点评处方列表 |
| `reasonableFlag` | `Boolean` | 共用是否合理 |
| `reason` | `String` | 共用原因 |
| `remark` | `String` | 共用备注 |
| `reviewerId` | `Long` | 共用点评人 |
| `reviewedAt` | `LocalDateTime` | 共用点评时间 |
| `operatorId` | `Long` | 操作人 |

**字段全集（子项 `CreatePrescriptionReviewItemCommand`）**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `prescriptionNo` | `String` | 处方号（`bus_no` 在单条 `createReview` 内自生成） |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | 复用单条不变式 |
| `statusTransitionMethod` | `markReviewed` |
| `repositoryChecks` | 每项 `existsByPrescriptionNoAndIsDeletedFalse` 须为 false |
| `eventPublishAnchor` | 每条 `PrescriptionReviewCreatedEvent` |
| `unitTestCaseId` | `TC-DM-CUS-015-W-002` |

**方法契约摘要**：前置 items 非空；逐条等价 `createReview`；任一条失败则整批失败。**注释锚点**：校验批次 → 循环校验/插入 → 逐条事件。

---

#### 5.1.3 `revokeReview(RevokePrescriptionReviewCommand cmd)` → `void`

**命令文件**：`RevokePrescriptionReviewCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `id` | `Long` | 点评主键（与处方号二选一，优先 id） |
| `prescriptionNo` | `String` | 处方号锚点 |
| `operatorId` | `Long` | 操作人 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `assertCanRevoke` |
| `statusTransitionMethod` | N/A（软删后展示回待点评） |
| `repositoryChecks` | `findByIdAndIsDeletedFalse` 或 `findByPrescriptionNoAndIsDeletedFalse` |
| `eventPublishAnchor` | `new PrescriptionReviewRevokedEvent(...)` + `publish` |
| `unitTestCaseId` | `TC-DM-CUS-015-W-003` |

**方法契约摘要**：前置有效行存在；软删；失败无目标行。**注释锚点**：定位有效行 → 软删 → 发布撤销事件。

### 5.2 读侧 — `PrescriptionReviewQueryService`

所有查询 **默认** `isDeleted=false`；分页在数据库侧过滤与统计 total。

| 方法 | 入参 | 出参（类名，字段见 §4.6） | 关键实现 | `unitTestCaseId` |
| ---- | ---- | ------------------------- | -------- | ---------------- |
| `findActiveByPrescriptionNo` | `String prescriptionNo` | `Optional<PrescriptionReviewQueryModel>` | 按处方号查有效行 | `TC-DM-CUS-015-R-001` |
| `mapActiveByPrescriptionNos` | `List<String> prescriptionNos` | `Map<String, PrescriptionReviewQueryModel>` | IN 查询后按处方号建 Map；空入参返回空 Map | `TC-DM-CUS-015-R-002` |
| `getById` | `Long id` | `PrescriptionReviewQueryModel` | 无有效行抛业务异常 | `TC-DM-CUS-015-R-003` |
| `pageReviews` | `PrescriptionReviewPageQuery` + `Pageable` | `Page<PrescriptionReviewQueryModel>` | 动态条件：statusEnum、reviewedAt 闭区间、reasonableFlag、reviewerId、prescriptionNo/busNo 关键字 | `TC-DM-CUS-015-R-004` |

**`PrescriptionReviewPageQuery` 字段**：`statusEnum`、`reviewedAtFrom`、`reviewedAtTo`、`reasonableFlag`、`reviewerId`、`prescriptionNoKeyword`、`busNoKeyword`。

**读方法注释锚点（统一）**：构建查询条件（含软删过滤）→ 单次仓储调用并完成分页统计 → 组装 QueryModel（无写库）。

---

## 6. 不变式映射（DM §7）

| BR 编号 | 实现策略摘要 |
| ------- | ------------ |
| BR-PRV-001 | `createReview`/`createReviews` 前 `existsByPrescriptionNoAndIsDeletedFalse`；冲突抛 `PRESCRIPTION_REVIEW_ALREADY_EXISTS` |
| BR-PRV-002 | 实体 `assertReasonWhenUnreasonable`；不合理且 reason 空白抛 `PRESCRIPTION_REVIEW_REASON_REQUIRED` |
| BR-PRV-003 | `revokeReview` 找不到有效行抛 `PRESCRIPTION_REVIEW_NOT_FOUND` |
| BR-PRV-004 | `markReviewed` 仅写 `REVIEWED`；禁止持久化 PENDING |
| BR-PRV-005 | `assertRequiredFields` 校验 busNo/prescriptionNo/reviewerId/reviewedAt/reasonableFlag |

---

## 7. 事件契约与触发规则（DM §5）

| 事件类名 | 事件类包路径 | 最小载荷（字段） | 触发方法 |
| -------- | ------------ | ---------------- | -------- |
| `PrescriptionReviewCreatedEvent` | `com.zxplat.domain.share.events.custom.prescriptionreview` | `id`、`prescriptionNo`、`busNo`、`reviewerId`、`reasonableFlag`、`statusEnum`、`operatorId` | `createReview` / `createReviews` |
| `PrescriptionReviewRevokedEvent` | `com.zxplat.domain.share.events.custom.prescriptionreview` | `id`、`prescriptionNo`、`busNo`、`operatorId` | `revokeReview` |

**发布时机**：全部 **beforeCommit**（`IDomainEventPublisher.publish`）；载荷为最小业务键 + 关键快照。

---

## 8. 删除与可见性契约

- 撤销采用逻辑删除（`isDeleted=true`），禁止物理 DELETE。
- 读侧默认仅见 `isDeleted=false`。
- 重评：旧行保持软删历史，新行 INSERT；有效唯一仍按 `prescription_no`。
- 唯一索引建议：部分唯一（`prescription_no` WHERE `is_deleted=false`）；若库不支持则应用层 + 存在性校验兜底。

---

## 9. 框架优先适配规则

- 审计/租户/软删字段继承 `BaseAuditedEntity`，不重复造轮子。
- 事件发布走 `IDomainEventPublisher`，不自建 EventFactory。
- 字符串空白判定用 `com.zxplat.framework.core.application.util.StringUtils`，禁止 Apache StringUtils。
- 错误码写入 `ErrorCodeConstants`；聚合类型常量 `AggregateTypeConstants.PRESCRIPTION_REVIEW`。
- 遵循项目级成码门禁（注释、禁三元、Repository 禁 `@Query`）。

---

## 10. 生成与验收清单

- [ ] Flyway 建表 `cus_prescription_review` 与索引
- [ ] 实体字段与 DM §3.1 / 本 SDD §4.2 一致
- [ ] 枚举 `PrescriptionReviewStatus`（PENDING/REVIEWED）
- [ ] 三个写方法 + 对应 Command 文件
- [ ] 四个读方法 + PageQuery + QueryModel
- [ ] 两个领域事件类
- [ ] ErrorCode / AggregateType 常量
- [ ] DomainService / QueryService JavaDoc 与分支注释齐全
- [ ] 编译 `zxplat-domain` / `zxplat-domain` 通过

---

## 11. 单次会话代码生成门禁（DoD）

### 11.1 前置输入

- 锁定 DM-CUS-015 v1.0.0 与本 SDD。
- 风格锚点：`custom/frequency`、`custom/supplier`。

### 11.2 DoD

- 字段 / 方法 / 事件 / BR / 软删过滤与本文 100% 一致。
- 无三元运算符、无 Repository `@Query`、无跨聚合 Repository 注入。
- Maven 编译相关模块成功。

### 11.3 失败处理

- 发现 DOC-CONFLICT：以 DM 为准改代码，并回写文档修订说明。
- 编译失败：先修本聚合新增文件，禁止用注释屏蔽调用。

---

## 定稿检查清单（可打勾）

- [x] 章节骨架与金母版同构
- [x] 写方法 3 = DM §6.1
- [x] 读方法 4 = DM §6.2
- [x] 事件 2 = DM §5
- [x] BR 5 条均有实现策略
- [x] §4.2 / §4.6 字段表完整
- [x] 无「待确认/后续补充」措辞

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
| **总分** | **100** | **100，可进入代码会话** |

---

**本 SDD 正文结束。**
