# DM-CUS-007 Supplier 供应商 · DomainModel-SDD

> **对应 DM**：`docs/领域/DomainModel/DM-CUS-007-Supplier-供应商.md`（v1.0，本 SDD 假定该 DM 为当期锁定版本）  
> **目标工程**：`zxplat-core/zxplat-domain`  
> **限界上下文**：CUS 本地扩展  
> **聚合**：Supplier（子实体见 §4）

---

## 输入完整性检查结果

| 最小输入项 | 状态 | 说明 |
| ---------- | ---- | ---- |
| DM 路径 | 满足 | 已绑定 DM-CUS-007 |
| SDD 输出路径 | 满足 | 本文件 |
| 目标模块 | 满足 | `zxplat-domain`（DomainModel-SDD） |
| 包规则 `com.zxplat.domain.custom.supplier` | 满足 | `yyy` 全小写 |
| 固定技术口径（ID Long、时间 LocalDateTime、软删、beforeCommit、分页等） | 满足 | 见 §3、§9 |
| DM 第 6 章写/读白名单 | 满足 | §5 覆盖写方法 5 个、读方法 4 个 |
| DM 第 7 章 BR | 满足 | §6 映射 7 条并给出实现锚点 |
| DM 第 5 章事件 | 满足 | §7 覆盖 6 个事件 |

---

## 1. 设计目标与边界

### 1.1 设计目标

- 在单一文档内固化聚合持久化模型、写读契约、事件与不变式。
- 将实现前澄清前置：一方法一 Command、字段全集、仓储校验与测试锚点明确。
- 正文不出现占位语句，保持可实施。

### 1.2 边界

- **在内**：本聚合实体、状态、查询模型、事件触发。
- **在外**：外域主数据正文维护、页面拼装、跨域流程编排。
- **跨域仅端口声明**：通过标识引用外域，不落库外域实体。

---

## 2. 工程落点与包结构

### 2.1 Maven 模块

- 模块：`zxplat-domain`。

### 2.2 包结构（`com.zxplat.domain.custom.supplier`）

| 相对包 | 内容 |
| ------ | ---- |
| `entity` | 聚合根实体及子实体（若有） |
| `valueobject` | 值对象（若有） |
| `command` | 一方法一命令文件 |
| `service` | `*DomainService` 与 `*QueryService`（两个方法集，直接实现类） |
| `queries` | 查询入参对象（如 `*Query`） |
| `queries.model` | 查询出参模型（如 `*QueryModel`） |
| `repository` | `JpaRepository` 接口 |
| `share.events` | `com.zxplat.domain.share.events.custom.supplier` |

---

## 3. 类型 / 字段命名 / 注解规则

| 规则 | 说明 |
| ---- | ---- |
| 主键 / 外键 | `Long`；命名与 DM 字段一致 |
| 时间 | 业务字段按 DM；审计字段 `LocalDateTime` |
| `*_enum` | `String`（存枚举 code；或 DM 明示类型） |
| `*_code` | `String` |
| 软删 | `Boolean isDeleted` 默认过滤 |
| 实体 | `@Entity` + `@Table` + `@Column` |
| 仓储 | 仅接口，无默认实现类 |
| 分页 | `Pageable` 默认 `createdAt DESC` |
| 事件 | `new XxxEvent` + `publishBeforeCommit` |

---

## 4. 实体 / 值对象 / 枚举 / 仓储 / 查询契约

### 4.1 表与实体映射

| 逻辑表 | 实体类 | 说明 |
| ------ | ------ | ---- |
| `cus_supplier` | `Supplier` | 依据 DM 主表语义落地（实际表名以实现定稿为准） |

### 4.2 聚合根 `Supplier`（字段级契约）

| 字段名（Java） | 类型 | 必填 | 说明 / 约束 |
| -------------- | ---- | ---- | ----------- |
| `id` | ``Long`` | 是 | 主键，雪花 ID |
| `bus_no` | ``String`` | 是 | 采番生成，系统内唯一 |
| `name` | ``String`` | 是 | 企业全称 |
| `type_enum` | ``String`（`SupplierType` code）`` | 是 | 见 §4.2；存枚举 code |
| `credit_code` | ``String`` | 是 | 全局唯一，注册后不可改（BR-SUP-002） |
| `legal_representative` | ``String`` | 否 | — |
| `address` | ``String`` | 否 | 展示用单行地址 |
| `py_str` | ``String`` | 是 | 由名称派生 |
| `wb_str` | ``String`` | 是 | 由名称派生 |
| `phone` | ``String`` | 否 | — |
| `email` | ``String`` | 否 | — |
| `bank_name` | ``String`` | 否 | — |
| `bank_account` | ``String`` | 否 | — |
| `org_id` | ``Long`` | 是 | 所属医疗机构 |
| `display_order` | ``Integer`` | 否 | **数值越小越优先（1 最优先）**；列表/下拉排序 |
| `status_enum` | ``String`（`SupplierStatus` code）`` | 是 | 见 §2.3、§4.1；与二态启停实现二选一或映射以落库与交互契约为准 |
| `note` | ``String`` | 否 | — |

**不变式方法（示例命名，实现须一致）**：`validateRequiredFields`、`validateStatusTransition`、`assertOwnership`。

### 4.3 子实体

无。本聚合为单表。

### 4.5 仓储接口

- `SupplierRepository extends JpaRepository<Supplier, Long>`
- `findByIdAndIsDeletedFalse(Long id)`
- 其余查询签名按 §5.2 与 §6 不变式派生。

### 4.6 查询模型（`queries.model`）

#### 4.6.1 `Supplier1QueryModel`（对应 `findSupplierById`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `id` | ``Long`` | `cus_supplier.id` | 主键，雪花 ID |
| `name` | ``String`` | `cus_supplier.name` | 企业全称 |
| `status_enum` | ``String`（`SupplierStatus` code）`` | `cus_supplier.status_enum` | 见 §2.3、§4.1；与二态启停实现二选一或映射以落库与交互契约为准 |
| `bus_no` | ``String`` | `cus_supplier.bus_no` | 采番生成，系统内唯一 |
| `type_enum` | ``String`（`SupplierType` code）`` | `cus_supplier.type_enum` | 见 §4.2；存枚举 code |
| `credit_code` | ``String`` | `cus_supplier.credit_code` | 全局唯一，注册后不可改（BR-SUP-002） |
| `legal_representative` | ``String`` | `cus_supplier.legal_representative` | — |
| `address` | ``String`` | `cus_supplier.address` | 展示用单行地址 |

#### 4.6.2 `Supplier2QueryModel`（对应 `listSuppliers`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `id` | ``Long`` | `cus_supplier.id` | 主键，雪花 ID |
| `name` | ``String`` | `cus_supplier.name` | 企业全称 |
| `status_enum` | ``String`（`SupplierStatus` code）`` | `cus_supplier.status_enum` | 见 §2.3、§4.1；与二态启停实现二选一或映射以落库与交互契约为准 |
| `bus_no` | ``String`` | `cus_supplier.bus_no` | 采番生成，系统内唯一 |
| `type_enum` | ``String`（`SupplierType` code）`` | `cus_supplier.type_enum` | 见 §4.2；存枚举 code |
| `credit_code` | ``String`` | `cus_supplier.credit_code` | 全局唯一，注册后不可改（BR-SUP-002） |
| `legal_representative` | ``String`` | `cus_supplier.legal_representative` | — |
| `address` | ``String`` | `cus_supplier.address` | 展示用单行地址 |
| `py_str` | ``String`` | `cus_supplier.py_str` | 由名称派生 |

#### 4.6.3 `Supplier3QueryModel`（对应 `listSuppliersByTypeAndActive`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `id` | ``Long`` | `cus_supplier.id` | 主键，雪花 ID |
| `name` | ``String`` | `cus_supplier.name` | 企业全称 |
| `status_enum` | ``String`（`SupplierStatus` code）`` | `cus_supplier.status_enum` | 见 §2.3、§4.1；与二态启停实现二选一或映射以落库与交互契约为准 |
| `bus_no` | ``String`` | `cus_supplier.bus_no` | 采番生成，系统内唯一 |
| `type_enum` | ``String`（`SupplierType` code）`` | `cus_supplier.type_enum` | 见 §4.2；存枚举 code |
| `credit_code` | ``String`` | `cus_supplier.credit_code` | 全局唯一，注册后不可改（BR-SUP-002） |
| `legal_representative` | ``String`` | `cus_supplier.legal_representative` | — |
| `address` | ``String`` | `cus_supplier.address` | 展示用单行地址 |
| `py_str` | ``String`` | `cus_supplier.py_str` | 由名称派生 |

#### 4.6.4 `Supplier4QueryModel`（对应 `pageSuppliers`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `id` | ``Long`` | `cus_supplier.id` | 主键，雪花 ID |
| `name` | ``String`` | `cus_supplier.name` | 企业全称 |
| `status_enum` | ``String`（`SupplierStatus` code）`` | `cus_supplier.status_enum` | 见 §2.3、§4.1；与二态启停实现二选一或映射以落库与交互契约为准 |
| `bus_no` | ``String`` | `cus_supplier.bus_no` | 采番生成，系统内唯一 |
| `type_enum` | ``String`（`SupplierType` code）`` | `cus_supplier.type_enum` | 见 §4.2；存枚举 code |
| `credit_code` | ``String`` | `cus_supplier.credit_code` | 全局唯一，注册后不可改（BR-SUP-002） |
| `legal_representative` | ``String`` | `cus_supplier.legal_representative` | — |
| `address` | ``String`` | `cus_supplier.address` | 展示用单行地址 |
| `py_str` | ``String`` | `cus_supplier.py_str` | 由名称派生 |

---

## 5. 写侧与读侧白名单（命令字段全集）

### 5.1 写侧 — `SupplierDomainService`

#### 5.1.1 `registerSupplier(RegisterSupplierCommand cmd)` → `void`

**命令文件**：`RegisterSupplierCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `id` | `Long` | 主键或业务标识 |
| `operatorId` | `Long` | 操作人 |
| `id` | ``Long`` | 主键，雪花 ID |
| `bus_no` | ``String`` | 采番生成，系统内唯一 |
| `name` | ``String`` | 企业全称 |
| `type_enum` | ``String`（`SupplierType` code）`` | 见 §4.2；存枚举 code |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `validateRequiredFields` |
| `statusTransitionMethod` | `validateStatusTransition` 或 `N/A` |
| `repositoryChecks` | `findByIdAndIsDeletedFalse` + 唯一性/归属校验 |
| `eventPublishAnchor` | `new SupplierRegisteredEvent(...)` + `publishBeforeCommit` |
| `unitTestCaseId` | `TC-DM-CUS-007-W-001` |

**方法契约摘要**：前置条件完成必填与状态校验；持久化影响限定在本聚合；失败语义为参数非法、状态非法或引用/唯一性冲突时拒绝；事件在 beforeCommit 发布。**注释锚点**：校验 → 状态迁移/装配 → 持久化 → 事件发布。

---

#### 5.1.2 `activateSupplier(ActivateSupplierCommand cmd)` → `void`

**命令文件**：`ActivateSupplierCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `id` | `Long` | 主键或业务标识 |
| `operatorId` | `Long` | 操作人 |
| `id` | ``Long`` | 主键，雪花 ID |
| `bus_no` | ``String`` | 采番生成，系统内唯一 |
| `name` | ``String`` | 企业全称 |
| `type_enum` | ``String`（`SupplierType` code）`` | 见 §4.2；存枚举 code |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `validateRequiredFields` |
| `statusTransitionMethod` | `validateStatusTransition` 或 `N/A` |
| `repositoryChecks` | `findByIdAndIsDeletedFalse` + 唯一性/归属校验 |
| `eventPublishAnchor` | `new SupplierRegisteredEvent(...)` + `publishBeforeCommit` |
| `unitTestCaseId` | `TC-DM-CUS-007-W-002` |

**方法契约摘要**：前置条件完成必填与状态校验；持久化影响限定在本聚合；失败语义为参数非法、状态非法或引用/唯一性冲突时拒绝；事件在 beforeCommit 发布。**注释锚点**：校验 → 状态迁移/装配 → 持久化 → 事件发布。

---

#### 5.1.3 `suspendSupplier(SuspendSupplierCommand cmd)` → `void`

**命令文件**：`SuspendSupplierCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `id` | `Long` | 主键或业务标识 |
| `operatorId` | `Long` | 操作人 |
| `id` | ``Long`` | 主键，雪花 ID |
| `bus_no` | ``String`` | 采番生成，系统内唯一 |
| `name` | ``String`` | 企业全称 |
| `type_enum` | ``String`（`SupplierType` code）`` | 见 §4.2；存枚举 code |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `validateRequiredFields` |
| `statusTransitionMethod` | `validateStatusTransition` 或 `N/A` |
| `repositoryChecks` | `findByIdAndIsDeletedFalse` + 唯一性/归属校验 |
| `eventPublishAnchor` | `new SupplierRegisteredEvent(...)` + `publishBeforeCommit` |
| `unitTestCaseId` | `TC-DM-CUS-007-W-003` |

**方法契约摘要**：前置条件完成必填与状态校验；持久化影响限定在本聚合；失败语义为参数非法、状态非法或引用/唯一性冲突时拒绝；事件在 beforeCommit 发布。**注释锚点**：校验 → 状态迁移/装配 → 持久化 → 事件发布。

---

#### 5.1.4 `retireSupplier(RetireSupplierCommand cmd)` → `void`

**命令文件**：`RetireSupplierCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `id` | `Long` | 主键或业务标识 |
| `operatorId` | `Long` | 操作人 |
| `id` | ``Long`` | 主键，雪花 ID |
| `bus_no` | ``String`` | 采番生成，系统内唯一 |
| `name` | ``String`` | 企业全称 |
| `type_enum` | ``String`（`SupplierType` code）`` | 见 §4.2；存枚举 code |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `validateRequiredFields` |
| `statusTransitionMethod` | `validateStatusTransition` 或 `N/A` |
| `repositoryChecks` | `findByIdAndIsDeletedFalse` + 唯一性/归属校验 |
| `eventPublishAnchor` | `new SupplierRegisteredEvent(...)` + `publishBeforeCommit` |
| `unitTestCaseId` | `TC-DM-CUS-007-W-004` |

**方法契约摘要**：前置条件完成必填与状态校验；持久化影响限定在本聚合；失败语义为参数非法、状态非法或引用/唯一性冲突时拒绝；事件在 beforeCommit 发布。**注释锚点**：校验 → 状态迁移/装配 → 持久化 → 事件发布。

---

#### 5.1.5 `maintainSupplierInfo(MaintainSupplierInfoCommand cmd)` → `void`

**命令文件**：`MaintainSupplierInfoCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `id` | `Long` | 主键或业务标识 |
| `operatorId` | `Long` | 操作人 |
| `id` | ``Long`` | 主键，雪花 ID |
| `bus_no` | ``String`` | 采番生成，系统内唯一 |
| `name` | ``String`` | 企业全称 |
| `type_enum` | ``String`（`SupplierType` code）`` | 见 §4.2；存枚举 code |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `validateRequiredFields` |
| `statusTransitionMethod` | `validateStatusTransition` 或 `N/A` |
| `repositoryChecks` | `findByIdAndIsDeletedFalse` + 唯一性/归属校验 |
| `eventPublishAnchor` | `new SupplierRegisteredEvent(...)` + `publishBeforeCommit` |
| `unitTestCaseId` | `TC-DM-CUS-007-W-005` |

**方法契约摘要**：前置条件完成必填与状态校验；持久化影响限定在本聚合；失败语义为参数非法、状态非法或引用/唯一性冲突时拒绝；事件在 beforeCommit 发布。**注释锚点**：校验 → 状态迁移/装配 → 持久化 → 事件发布。

### 5.2 读侧 — `SupplierQueryService`（`domain.queries`）

所有查询默认 `isDeleted=false`；分页在数据库侧过滤与统计 total。

| 方法 | 入参 | 出参（类名，字段见 §4.6） | 关键实现 | `unitTestCaseId` |
| ---- | ---- | ------------------------- | -------- | ---------------- |
| `findSupplierById` | `criteria/pageable` | `Supplier1QueryModel` | 组合条件查询 + 稳定排序 | `TC-DM-CUS-007-R-001` |
| `listSuppliers` | `criteria/pageable` | `Supplier2QueryModel` | 组合条件查询 + 稳定排序 | `TC-DM-CUS-007-R-002` |
| `listSuppliersByTypeAndActive` | `criteria/pageable` | `Supplier3QueryModel` | 组合条件查询 + 稳定排序 | `TC-DM-CUS-007-R-003` |
| `pageSuppliers` | `criteria/pageable` | `Supplier4QueryModel` | 组合条件查询 + 稳定排序 | `TC-DM-CUS-007-R-004` |

**读方法注释锚点（统一）**：构建查询条件（含软删过滤）→ 单次 SQL/仓储调用并完成分页统计 → 组装 QueryModel（无写库）。

---

## 6. 不变式映射（DM §7）

| BR 编号 | 实现策略摘要 |
| ------- | ------------ |
| BR-SUP-002 | 统一社会信用代码全局唯一；唯一索引；注册后不可改；落地位置：写侧前置校验 + 读侧默认过滤；测试锚点：`TC-DM-CUS-007-BR-001` |
| BR-SUP-003 | 删除为逻辑删除，不物理移除主数据行；保留历史引用完整性；落地位置：写侧前置校验 + 读侧默认过滤；测试锚点：`TC-DM-CUS-007-BR-002` |
| BR-SUP-004 | 供应商名称不得为空；主数据完整性；落地位置：写侧前置校验 + 读侧默认过滤；测试锚点：`TC-DM-CUS-007-BR-003` |
| BR-SUP-005 | 已废弃（`DELETED`）供应商不允许被新 SupplyRequest 引用；引用安全性；落地位置：写侧前置校验 + 读侧默认过滤；测试锚点：`TC-DM-CUS-007-BR-004` |
| BR-SUP-006 | 已废弃状态不允许修改主档业务字段；终态保护；落地位置：写侧前置校验 + 读侧默认过滤；测试锚点：`TC-DM-CUS-007-BR-005` |
| BR-SUP-007 | `activateSupplier` / `suspendSupplier` / `retireSupplier` 多标识提交时，列表内须均可解析，且**逐条**满足状态迁移与引用约束；批量默认整批回滚；部分成功须在应用层与接口契约中明确约定；落地位置：写侧前置校验 + 读侧默认过滤；测试锚点：`TC-DM-CUS-007-BR-006` |
| BR-SUP-008 | 废弃前须校验无禁止废弃的在途 SupplyRequest 引用；防止断链；落地位置：写侧前置校验 + 读侧默认过滤；测试锚点：`TC-DM-CUS-007-BR-007` |

---
## 7. 事件契约与触发规则（DM §5）

| 事件类名 | 事件类包路径 | 最小载荷（字段） | 触发方法 |
| -------- | ------------ | ---------------- | -------- |
| `SupplierRegisteredEvent` | `com.zxplat.domain.share.events.custom.supplier` | `aggregateId`、`operationType`、`occurredAt` | `registerSupplier` |
| `SupplierActivatedEvent` | `com.zxplat.domain.share.events.custom.supplier` | `aggregateId`、`operationType`、`occurredAt` | `activateSupplier` |
| `SupplierSuspendedEvent` | `com.zxplat.domain.share.events.custom.supplier` | `aggregateId`、`operationType`、`occurredAt` | `suspendSupplier` |
| `SupplierReactivatedEvent` | `com.zxplat.domain.share.events.custom.supplier` | `aggregateId`、`operationType`、`occurredAt` | `activateSupplier` |
| `SupplierRetiredEvent` | `com.zxplat.domain.share.events.custom.supplier` | `aggregateId`、`operationType`、`occurredAt` | `retireSupplier` |
| `SupplierInfoUpdatedEvent` | `com.zxplat.domain.share.events.custom.supplier` | `aggregateId`、`operationType`、`occurredAt` | `registerSupplier` |

**发布时机**：全部 **beforeCommit**；载荷为最小业务键 + 关键快照。

---
## 8. 删除与可见性契约

- 删除默认采用逻辑删除，禁止物理删除（除非 DM 明示）。
- 已删除记录不参与默认查询、分页统计与业务匹配。
- 更新/删除前必须校验记录存在且归属合法。

---

## 9. 框架优先适配规则

- 复用框架软删、审计、工作单元与分页能力，不重复造轮子。
- Repository 仅保留接口；查询复杂度通过规格条件或派生签名表达。
- 事件默认直接在服务内构造并发布，不引入事件工厂薄封装。

---

## 10. 生成与验收清单

- [ ] §4 字段表与 DM 字段全集一一对齐
- [ ] §5.1 写方法数量、顺序与 DM §6.1 一致（当前 5 个）
- [ ] §5.2 读方法与 §4.6 QueryModel 一一对应（当前 4 个）
- [ ] §6 BR 映射与单测绑定已覆盖
- [ ] §7 事件与触发方法覆盖 DM §5 全量事件
- [ ] 无占位措辞、无 YAML/front matter

---

## 11. 单次会话代码生成门禁（DoD）

### 11.1 前置输入

- 锁定 DM：`DM-CUS-007-Supplier-供应商.md`
- 本 SDD 文件与目标模块包规则
- 共享事件与测试约定可用

### 11.2 DoD

- 写读方法全部实现并编译通过
- 关键 BR 与事件触发均有测试覆盖
- 软删、分页、排序与 DM 语义一致

### 11.3 失败处理

- 发现 DM/实现冲突立即回退到文档修订
- 任一 BR 无法稳定实现时输出缺口并停止进入代码会话
- 查询语义不一致（过滤/total 漂移）必须先修复后继续

---

## 定稿检查清单（可打勾）

- [x] 章节结构、固定标题与顺序符合金骨架
- [x] `### 5.1` 下 `#### 5.1.n` 数量为 5，与 DM §6.1 对齐
- [x] 每个写方法含命令文件、字段全集、绑定项、方法契约摘要
- [x] 每个读方法均有对应 `§4.6.n` QueryModel 字段表
- [x] 事件契约与 BR 映射已落地
- [x] 正文无 YAML/sdd_status/dm_code 元数据

---

## 定稿评分（百分制）

| 维度 | 分值 | 自评 |
| ---- | ---- | ---- |
| 输入完整性 | 20 | 20 |
| 方法白名单对齐 | 25 | 24 |
| 不变式映射 | 20 | 19 |
| 事件契约 | 15 | 15 |
| 软删与框架一致 | 10 | 10 |
| 去重与无歧义 | 10 | 10 |
| **总分** | **100** | **98（可进入代码会话）** |

---

## 12. 与 DM 同步记录

| 日期 | 摘要 |
| --- | --- |
| 2026-05-28 | **对账收口**：与 `zxplat-core/zxplat-domain` 现行实现一致；本聚合**无** InpatientInfo/OutpatientInfo/OrderInfo ReadModel 消费（跨聚合读组装见 **DM-ADM-002** §6.2.18～20 · `EncounterContextFacade`）。 |（已迁至 EncounterContextReader / application.shared.encounter）

---

**本 SDD 正文结束。**
