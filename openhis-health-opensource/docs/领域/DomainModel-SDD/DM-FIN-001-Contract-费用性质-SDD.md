# DM-FIN-001 Contract 费用性质 · DomainModel-SDD

> **对应 DM**：`docs/领域/DomainModel/DM-FIN-001-Contract-费用性质.md`（**v1.0.2**，本 SDD 假定该 DM 为当期锁定版本）  
> **目标工程**：`zxplat-core/zxplat-domain`  
> **限界上下文**：FIN  
> **聚合**：Contract（子实体见 §4）

---

## 输入完整性检查结果

| 最小输入项 | 状态 | 说明 |
| ---------- | ---- | ---- |
| DM 路径 | 满足 | 已绑定 DM-FIN-001 |
| SDD 输出路径 | 满足 | 本文件 |
| 目标模块 | 满足 | `zxplat/zxplat-domain` |
| 包规则 `com.zxplat.domain.fin.contract` | 满足 | `yyy` 全小写 |
| 固定技术口径（ID Long、时间 LocalDateTime、软删、beforeCommit、分页等） | 满足 | 见 §9 |
| DM 第 6 章写/读白名单 | 满足 | §5 全覆盖 |
| DM 第 7 章 BR | 满足 | §6 映射并可追溯 |
| DM 第 5 章事件 | 满足 | §7 |

**与 DM 显式差异（采纳依据：DM 优先 + 领域落地契约细化）**

| 点 | DM 差异位置 | SDD 采纳 |
| -- | ----------- | -------- |
| 事件发布状态 | §5.1 注明"当前实现均未发布独立领域事件" | SDD §7 按设计预留完整事件契约，实现锚点保留，后续迭代可直接启用 |
| 终端配置字段 | §3.1 已列出但未明确分组 | SDD §5.1.1/§5.1.2 使用 `ContractTerminalConfigCommand` 子命令封装，保持主命令简洁 |
| 缓存策略 | DM 未提及 | SDD §2.2/§6 明确写后缓存失效策略（`contract`/`contractList`） |

---

## 1. 设计目标与边界

### 1.1 设计目标

- 固化 `Contract` 费用性质主数据聚合，实现费用承担方式定义、启停、逻辑删除与查询契约。
- 将医保终端配置字段与费用性质基本字段统一纳入字段级契约，避免实现层口径漂移。
- 以一方法一 Command 明确 5 个写方法，实现缓存失效、软删与状态一致性。

### 1.2 边界

- **在内**：费用性质主数据维护、启停控制、逻辑删除、主数据查询。
- **在外**：Account 切换编排、收费重算、医保平台协议编排。
- **跨域仅端口声明**：通过 `ContractId` 供 Encounter/Account/ChargeItem 引用。

---

## 2. 工程落点与包结构

### 2.1 Maven 模块

- 模块：`zxplat/zxplat-domain`。

### 2.2 包结构（`com.zxplat.domain.fin.contract`）

| 相对包 | 内容 |
| ------ | ---- |
| `entity` | `Contract` |
| `valueobject` | 无独立值对象 |
| `enums`（共享） | 复用 `ContractCategory`、`AccountStatus` |
| `command` | `CreateContractCommand`、`EditContractCommand`、`DeleteContractCommand`、`ActivateContractCommand`、`DeactivateContractCommand` |
| `repository` | `ContractRepository extends JpaRepository<Contract, Long>` |
| `service` | `*DomainService` 与 `*QueryService`（两个方法集，直接实现类） |
| `queries` | 查询入参对象（如 `*Query`） |
| `queries.model` | 查询出参模型（如 `*QueryModel`） |
| `share.events` | `com.zxplat.domain.share.events.financial.contract`（当前为预留） |

**存储口径（DM 优先）**：`category_enum/status_enum` 为 `Integer`；`*_code` 为 `String`；`active_flag` 为 `Boolean`。

---

## 3. 类型 / 字段命名 / 注解规则

| 规则 | 说明 |
| ---- | ---- |
| 主键 / 外键 | `Long`；主键 `id` |
| 时间 | 业务时间无新增，审计字段沿用基类 `LocalDateTime` |
| `*_enum` | `categoryEnum`、`statusEnum` 为 `Integer` |
| `*_code` | `busNo`、`admVs`、`fixmedinsCode` 等为 `String` |
| `_flag` | `activeFlag`、`ybFlag`、`isDeleted` 为 `Boolean` |
| 软删 | `Boolean isDeleted`（基类） |
| 实体 | `@Entity` + `@Table(name="fin_contract")` |
| 值对象 | 无 |
| 仓储 | 仅 `JpaRepository` 接口 |
| 分页 | `Pageable`，默认 `createdAt DESC` |
| 事务 | 不机械添加 `@Transactional` |
| 事件 | 当前实现不发布，事件契约按 §7 预留 |

---

## 4. 实体 / 值对象 / 枚举 / 仓储 / 查询契约

### 4.1 表与实体映射

| 逻辑表 | 实体类 | 说明 |
| ------ | ------ | ---- |
| `fin_contract` | `Contract` | 费用性质主数据单表聚合 |

### 4.2 聚合根 `Contract`（字段级契约）

对应表 `fin_contract`，主键 `id`。

| 字段名（Java） | 类型 | 必填 | 说明 / 约束 |
| -------------- | ---- | ---- | ----------- |
| `id` | `Long` | 系统 | 费用性质标识，雪花 ID |
| `busNo` | `String` | 是 | 业务编码，全局唯一，非空（BR-CTR-001），如 ZF=自费、YBBX=医保保险 |
| `name` | `String` | 是 | 费用性质名称，如"自费""省医保"，变更时触发检索码重算 |
| `categoryEnum` | `Integer` | 是 | 费用性质分类，复用 `ContractCategory`：SELF_CHARGE=1/PUBLIC_CHARGE=2/PROVINCIAL_INSURANCE=3/MUNICIPAL_INSURANCE=4/WORKER_INSURANCE=5 |
| `statusEnum` | `Integer` | 是 | 启停状态，复用 `AccountStatus`：ACTIVE=1/INACTIVE=2，新建默认 ACTIVE（BR-CTR-003） |
| `activeFlag` | `Boolean` | 是 | 是否启用，`statusEnum == ACTIVE` 时为 true，其余为 false |
| `orgId` | `Long` | 否 | 机构标识，多租户隔离 |
| `ybFlag` | `Boolean` | 否 | 是否医保标志，true=医保类，false=非医保类 |
| `displayOrder` | `Integer` | 否 | 显示顺序，排序权重，非负整数（BR-CTR-005），数值越小越靠前 |
| `admVs` | `String` | 否 | 医保区划，医保统筹区代码（如省级行政区划编码），医保类费用性质填写 |
| `pyStr` | `String` | 否 | 拼音检索码，由 `name` 自动生成，用于拼音首字母检索 |
| `wbStr` | `String` | 否 | 五笔检索码，由 `name` 自动生成，用于五笔编码检索 |
| `clientId` | `String` | 否 | 医保客户端标识，医保终端接口 clientId |
| `clientSecret` | `String` | 否 | 医保客户端密钥，医保终端接口 clientSecret |
| `username` | `String` | 否 | 医保账号，医保终端接口登录账号 |
| `password` | `String` | 否 | 医保密码，医保终端接口登录密码 |
| `cliPrvKey` | `String` | 否 | 医保私钥，医保终端客户端私钥 |
| `cliPubKey` | `String` | 否 | 医保公钥，医保终端客户端公钥 |
| `serverPubKey` | `String` | 否 | 服务端公钥，医保终端服务端公钥 |
| `scope` | `String` | 否 | 医保授权范围 |
| `grantType` | `String` | 否 | 医保授权类型 |
| `fixmedinsCode` | `String` | 否 | 定点医疗机构编码 |
| `fixmedinsName` | `String` | 否 | 定点医疗机构名称 |
| `isDeleted` | `Boolean` | 系统 | 逻辑删除标记，默认 false（BR-CTR-002） |

**不变式方法（示例命名，实现须一致）**：
- `assertBusNoNotBlank()`：业务编码非空（BR-CTR-001）
- `assertDisplayOrderNonNegative()`：显示顺序为非负整数（BR-CTR-005）
- `activate()`：启用状态迁移，statusEnum=ACTIVE/activeFlag=true，触发缓存失效（BR-CTR-004）
- `deactivate()`：停用状态迁移，statusEnum=INACTIVE/activeFlag=false，触发缓存失效（BR-CTR-004）
- `markDeleted()`：逻辑删除，isDeleted=true，保留审计痕迹（BR-CTR-002），触发缓存失效（BR-CTR-004）

### 4.3 子实体

无。本聚合为单表。

### 4.5 仓储接口

- `Optional<Contract> findByIdAndIsDeletedFalse(Long id)`
- `Optional<Contract> findByBusNoAndIsDeletedFalse(String busNo)`
- `Optional<Contract> findByBusNoAndIdNotAndIsDeletedFalse(String busNo, Long id)`
- `List<Contract> findAllByTenantIdAndIsDeletedFalseOrderByDisplayOrderAsc(...)`

### 4.6 查询模型（`queries.model`）

#### 4.6.1 `ContractDetailQueryModel`（对应 `findContractById`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `id` | `Long` | `fin_contract.id` | 主键 |
| `busNo` | `String` | `fin_contract.bus_no` | 编码 |
| `name` | `String` | `fin_contract.name` | 名称 |
| `categoryEnum` | `Integer` | `fin_contract.category_enum` | 分类 |
| `statusEnum` | `Integer` | `fin_contract.status_enum` | 状态 |
| `activeFlag` | `Boolean` | `fin_contract.active_flag` | 是否启用 |
| `displayOrder` | `Integer` | `fin_contract.display_order` | 排序 |
| `ybFlag` | `Boolean` | `fin_contract.yb_flag` | 医保标志 |

#### 4.6.2 `ContractListItemQueryModel`（对应 `listContracts`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `id` | `Long` | `fin_contract.id` | 主键 |
| `busNo` | `String` | `fin_contract.bus_no` | 编码 |
| `name` | `String` | `fin_contract.name` | 名称 |
| `categoryEnum` | `Integer` | `fin_contract.category_enum` | 分类 |
| `activeFlag` | `Boolean` | `fin_contract.active_flag` | 是否可选 |
| `displayOrder` | `Integer` | `fin_contract.display_order` | 排序 |

---

## 5. 写侧与读侧白名单（命令字段全集）

### 5.1 写侧 — `ContractDomainService`

#### 5.1.1 `createContract(CreateContractCommand cmd)` → `Long`

**命令文件**：`CreateContractCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `busNo` | `String` | 业务编码 |
| `name` | `String` | 名称 |
| `categoryEnum` | `Integer` | 分类 |
| `displayOrder` | `Integer` | 排序 |
| `ybFlag` | `Boolean` | 医保标识 |
| `admVs` | `String` | 医保区划 |
| `terminalConfig` | `ContractTerminalConfigCommand` | 医保终端配置子命令 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `assertBusNoNotBlank`、`assertDisplayOrderNonNegative` |
| `statusTransitionMethod` | `initActive` |
| `repositoryChecks` | `findByBusNoAndIsDeletedFalse` |
| `eventPublishAnchor` | `ContractCreatedEvent`（预留，当前 N/A） |
| `unitTestCaseId` | `TC-FIN-CTR-001` |

**方法契约摘要**：前置条件为编码非空与编码唯一；持久化新增主记录并生成检索码；失败语义为编码冲突拒绝；事件触发当前为 N/A（保留扩展位）。

---

#### 5.1.2 `editContract(EditContractCommand cmd)` → `void`

**命令文件**：`EditContractCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `id` | `Long` | 主键 |
| `busNo` | `String` | 业务编码 |
| `name` | `String` | 名称 |
| `categoryEnum` | `Integer` | 分类 |
| `displayOrder` | `Integer` | 排序 |
| `terminalConfig` | `ContractTerminalConfigCommand` | 医保终端配置 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `assertBusNoNotBlank`、`refreshSearchCodeFromName` |
| `statusTransitionMethod` | `N/A` |
| `repositoryChecks` | `findByIdAndIsDeletedFalse`、`findByBusNoAndIdNotAndIsDeletedFalse` |
| `eventPublishAnchor` | `ContractEditedEvent`（预留，当前 N/A） |
| `unitTestCaseId` | `TC-FIN-CTR-002` |

**方法契约摘要**：前置记录存在且编码有效；持久化更新可编辑字段；失败语义为记录不存在或编码冲突拒绝；事件当前不发布。

---

#### 5.1.3 `deleteContract(DeleteContractCommand cmd)` → `void`

**命令文件**：`DeleteContractCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `id` | `Long` | 主键 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `assertDeletable` |
| `statusTransitionMethod` | `markDeleted` |
| `repositoryChecks` | `findByIdAndIsDeletedFalse` |
| `eventPublishAnchor` | `ContractDeletedEvent`（预留，当前 N/A） |
| `unitTestCaseId` | `TC-FIN-CTR-003` |

**方法契约摘要**：前置条件为主键有效且记录存在；持久化影响为逻辑删除；失败语义为记录不存在拒绝；事件当前不发布。

---

#### 5.1.4 `activateContract(ActivateContractCommand cmd)` → `void`

**命令文件**：`ActivateContractCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `id` | `Long` | 主键 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `assertStatusTransitionAllowed` |
| `statusTransitionMethod` | `activate` |
| `repositoryChecks` | `findByIdAndIsDeletedFalse` |
| `eventPublishAnchor` | `ContractActivatedEvent`（预留，当前 N/A） |
| `unitTestCaseId` | `TC-FIN-CTR-004` |

**方法契约摘要**：前置记录存在；持久化将 `statusEnum=ACTIVE` 且 `activeFlag=true`；失败语义为非法迁移拒绝；事件当前不发布。

---

#### 5.1.5 `deactivateContract(DeactivateContractCommand cmd)` → `void`

**命令文件**：`DeactivateContractCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `id` | `Long` | 主键 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `assertStatusTransitionAllowed` |
| `statusTransitionMethod` | `deactivate` |
| `repositoryChecks` | `findByIdAndIsDeletedFalse` |
| `eventPublishAnchor` | `ContractDeactivatedEvent`（预留，当前 N/A） |
| `unitTestCaseId` | `TC-FIN-CTR-005` |

**方法契约摘要**：前置记录存在；持久化更新停用态；失败语义为非法迁移拒绝；事件当前不发布。

### 5.2 读侧 — `ContractQueryService`（`domain.queries`）

所有查询默认 `isDeleted=false`；分页在数据库侧过滤与统计 total。

| 方法 | 入参 | 出参（类名，字段见 §4.6） | 关键实现 | `unitTestCaseId` |
| ---- | ---- | ------------------------- | -------- | ---------------- |
| `findContractById` | `Long id` | `ContractDetailQueryModel` | 主键查询 + 缓存 | `TC-FIN-CTR-Q-001` |
| `listContracts` | `Long orgId`, `Pageable` | `Page<ContractListItemQueryModel>` | 机构分页列表 | `TC-FIN-CTR-Q-002` |
| `listAllContractsByOrgId` | `Long orgId` | `List<ContractListItemQueryModel>` | 机构全量下拉 | `TC-FIN-CTR-Q-003` |
| `listAllContracts` | — | `List<ContractListItemQueryModel>` | 全租户列表 | `TC-FIN-CTR-Q-004` |
| `mapContractNameByBusNoIn` | `Collection<String> busNos` | `Map<String,String>` | busNo→name | `TC-FIN-CTR-Q-005` |
| `findOptionalContractEntityByBusNo` | `String busNo` | `Optional<Contract>` | bridge 读实体；软删过滤 | `TC-FIN-CTR-Q-006` |
| `findOptionalContractEntityById` | `Long id` | `Optional<Contract>` | bridge 读实体；软删过滤 | `TC-FIN-CTR-Q-007` |

**读方法注释锚点（统一）**：构建查询条件（含软删过滤）→ 单次 SQL/仓储调用并完成分页统计 → 组装 QueryModel（无写库）。

---

## 6. 不变式映射（DM §7）

| BR 编号 | 实现策略摘要 |
| ------- | ------------ |
| BR-CTR-001 | `busNo` 创建/编辑均不可为空 |
| BR-CTR-002 | 删除为逻辑删除，禁止物理删除 |
| BR-CTR-003 | 新建默认启用 `ACTIVE` |
| BR-CTR-004 | 写操作后统一失效 `contract/contractList` 缓存 |
| BR-CTR-005 | `displayOrder >= 0` |
| BR-CTR-006 | `busNo` 唯一性由领域校验 + DB 约束兜底 |

---

## 7. 事件契约与触发规则（DM §5）

| 事件类名 | 事件类包路径 | 最小载荷（字段） | 触发方法 |
| -------- | ------------ | ---------------- | -------- |
| `ContractCreatedEvent` | `com.zxplat.domain.share.events.financial.contract` | `id,busNo,name,categoryEnum` | `createContract` |
| `ContractEditedEvent` | `com.zxplat.domain.share.events.financial.contract` | `id,changedFields` | `editContract` |
| `ContractActivatedEvent` | `com.zxplat.domain.share.events.financial.contract` | `id,busNo` | `activateContract` |
| `ContractDeactivatedEvent` | `com.zxplat.domain.share.events.financial.contract` | `id,busNo` | `deactivateContract` |
| `ContractDeletedEvent` | `com.zxplat.domain.share.events.financial.contract` | `id,busNo` | `deleteContract` |

**发布时机**：全部 beforeCommit；当前实现暂不发布，代码会话按“事件预留、锚点保留”落地。

---

## 8. 删除与可见性契约

- 删除只做软删标记，历史引用保持可追溯。
- 默认查询不返回 `isDeleted=true` 记录。
- 停用仅影响新增选择，不回写历史 Account/Encounter。

---

## 9. 框架优先适配规则

- 复用共享枚举，不重复定义等价枚举。
- 写后缓存失效通过框架注解或统一缓存门面。
- Repository 仅接口，查询服务保持无写库副作用。

---

## 10. 生成与验收清单

- [ ] 字段映射覆盖 DM §3.1 全量字段
- [ ] 写侧 5 方法与命令文件一一对应
- [ ] `busNo` 唯一性 + 状态迁移 + 软删覆盖测试
- [ ] 查询模型字段齐全，缓存行为可验证
- [ ] 事件预留锚点留在服务方法中

---

## 11. 单次会话代码生成门禁（DoD）

### 11.1 前置输入

- 锁定 DM：`DM-FIN-001`
- 共享枚举 `ContractCategory/AccountStatus` 可用
- 缓存策略与租户过滤规则已明确

### 11.2 DoD

- 写侧 5 方法签名与 §5.1 一致。
- 所有写操作后触发缓存失效。
- `statusEnum` 与 `activeFlag` 一致性可测。
- 逻辑删除与默认查询过滤均通过测试。

### 11.3 失败处理

- 若字段或方法缺失，停止成码回补 SDD。
- 若唯一性策略不清，先补充 DB/仓储约束再继续。
- 若事件是否发布发生变更，先修订 §7 后再实现。

---

## 定稿检查清单（可打勾）

- [x] 主章节结构完整
- [x] 写侧 5 方法逐项契约化
- [x] 字段表覆盖 DM 全量字段
- [x] BR-CTR-001~006 映射完成
- [x] 事件与当前实现“预留不发布”口径一致

---

## 定稿评分（百分制）

| 维度 | 分值 | 自评 |
| ---- | ---- | ---- |
| 输入完整性 | 20 | 20 |
| 方法白名单对齐 | 25 | 25 |
| 不变式映射 | 20 | 19 |
| 事件契约 | 15 | 14 |
| 软删与框架一致 | 10 | 10 |
| 去重与无歧义 | 10 | 9 |
| **总分** | **100** | **97（可进入代码会话）** |

---

## 12. 与 DM 同步记录

| 日期 | 摘要 |
| --- | --- |
| 2026-07-11 | **CHS bridge**：§5.2 增补 `findOptionalContractEntityByBusNo/ById`；对齐 DM **v1.0.2** |
| 2026-05-28 | **DM-SDD M1**：§5.2 五读法对齐 Java；对齐 DM **v1.0.1** |
| 2026-05-28 | **对账收口**：与 `zxplat-core/zxplat-domain` 现行实现一致；本聚合**无** InpatientInfo/OutpatientInfo/OrderInfo ReadModel 消费（跨聚合读组装见 **DM-ADM-002** §6.2.18～20 · `EncounterContextFacade`）。 |（已迁至 EncounterContextReader / application.shared.encounter）

---

**本 SDD 正文结束。**
