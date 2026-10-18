# DM-ADM-009 Account 就诊账户 · DomainModel-SDD

> **对应 DM**：`docs/领域/DomainModel/DM-ADM-009-Account-就诊账户.md`（**v1.1**，本 SDD 假定该 DM 为当期锁定版本）  
> **目标工程**：`zxplat-core/zxplat-domain`  
> **限界上下文**：Financial（财务域 · 就诊账户管理）  
> **聚合**：Account（子实体见 §4）

---

## 输入完整性检查结果

| 最小输入项 | 状态 | 说明 |
| ---------- | ---- | ---- |
| DM 路径 | 满足 | 已绑定 DM-ADM-009 |
| SDD 输出路径 | 满足 | 本文件 |
| 目标模块 | 满足 | `zxplat/zxplat-domain` |
| 包规则 `com.zxplat.domain.financial.account` | 满足 | `account` 全小写 |
| 固定技术口径（ID Long、时间 LocalDateTime、软删、beforeCommit、分页等） | 满足 | 见 §9 |
| DM 第 6 章写/读白名单 | 满足 | §5 全覆盖并给出 Command 字段全集 |
| DM 第 7 章 BR | 满足 | §6 映射并可追溯 |
| DM 第 5 章事件 | 满足 | §7 |

**与 DM 显式差异（采纳依据：DM 优先 + SDD 落地契约细化）**

| 点 | DM 差异位置 | SDD 采纳 |
| -- | ----------- | -------- |
| 限界上下文前缀 | 文档编号 `ADM` 但上下文写财务域 | SDD 以财务域包路径落地 |
| 事件命名 | §5 使用 `AccountDomainEvents.*` 嵌套命名 | SDD 事件表保持原命名并落到共享事件包 |
| 状态机制 | §2.3 说明无独立状态机 | SDD 保留“状态字段驱动”并在方法契约中约束状态变化 |

---

## 1. 设计目标与边界

### 1.1 设计目标

- 在单一文档内固化 **Account 聚合** 的持久化模型、领域服务契约、查询契约、事件与不变式实现策略，使下一会话可一次性生成 `zxplat/zxplat-domain` 领域层实现并通过编译。
- 将实现前澄清前置到 SDD：每个写方法对应独立 Command 文件，命令字段全集列出，覆盖“开立主户、批量并行户、upsert”三类路径。
- 单次成稿目标：正文无后续补充/待确认措辞，缺口在文档阶段关闭。

### 1.2 边界

- **在内**：账户主档开立、余额更新观测、账户定位查询、并行多户事实维护。
- **在外**：支付流水入账、发票、对账主单、收费明细计算与核销。
- **跨域仅端口声明**：仅声明与 Patient/Encounter/ChargeItem/PaymentReconciliation 的引用关系，不在本聚合执行他域写入。

---

## 2. 工程落点与包结构

### 2.1 Maven 模块

- 模块：`zxplat/zxplat-domain`。

### 2.2 包结构（`com.zxplat.domain.financial.account`）

| 相对包 | 内容 |
| ------ | ---- |
| `entity` | `Account` |
| `valueobject` | `AccountStatus`、`AccountBillingStatus`；`type_code` 值域见 **`com.zxplat.domain.share.enums.AccountTypeCode`** |
| `enums`（共享） | 复用账户状态/类型枚举 |
| `command` | 每个写方法一个 Java 文件 |
| `repository` | `AccountRepository`（仅 `JpaRepository` 接口） |
| `service` | `*DomainService` 与 `*QueryService`（两个方法集，直接实现类） |
| `queries` | 查询入参对象（如 `*Query`） |
| `queries.model` | 查询出参模型（如 `*QueryModel`） |
| `share.events` | `com.zxplat.domain.share.events.financial.account` |

**存储口径（DM 优先）**：`*_enum` 默认 `Integer`；`*_code` 为 `String`；`_flag` 为 `Boolean`；软删统一 `Boolean isDeleted`。

---

## 3. 类型 / 字段命名 / 注解规则

| 规则 | 说明 |
| ---- | ---- |
| 主键 / 外键 | `Long`；聚合根主键 `id`；外键 `patientId/encounterId` |
| 时间 | 审计字段使用 `LocalDateTime`；业务字段无独立时间列 |
| `*_enum` | 枚举编码列，默认 `Integer` |
| `*_code` | 字典/术语编码列，`String` |
| `_flag` | `Boolean` |
| 软删 | `Boolean isDeleted`，默认查询过滤 |
| 实体 | `@Entity` + 显式 `@Table`/`@Column` |
| 值对象 | `@Embeddable` |
| 仓储 | `JpaRepository<Account, Long>` |
| 分页 | `Pageable`，默认 `createdAt DESC` |
| 事务 | 领域服务方法不机械添加 `@Transactional` |
| 事件 | 服务内直接构造并 beforeCommit 发布 |

---

## 4. 实体 / 值对象 / 枚举 / 仓储 / 查询契约

### 4.1 表与实体映射

| 逻辑表 | 实体类 | 说明 |
| ------ | ------ | ---- |
| `adm_account` | `Account` | 就诊账户聚合根单表 |

### 4.2 聚合根 `Account`（字段级契约）

| 字段名（Java） | 类型 | 必填 | 说明 / 约束 |
| -------------- | ---- | ---- | ----------- |
| `id` | `Long` | 系统 | 账户标识 |
| `statusEnum` | `Integer` | 是 | 账户主状态 |
| `billingStatusEnum` | `Integer` | 是 | 结账状态 |
| `typeCode` | `String` | 是 | 账户类型编码 |
| `busNo` | `String` | 是 | 账户业务编码 |
| `name` | `String` | 否 | 账户名称 |
| `patientId` | `Long` | 是 | 患者标识 |
| `encounterId` | `Long` | 是 | 就诊标识 |
| `balanceAmount` | `BigDecimal` | 是 | 账户余额 |
| `ybAreaNo` | `String` | 否 | 医保区划 |
| `contractNo` | `String` | 否 | 合同号 |
| `limitAccount` | `BigDecimal` | 否 | 欠费限额 |
| `encounterFlag` | `Boolean` | 否 | 登记主户标志 |
| `insutype` | `String` | 否 | 险种 |
| `isDeleted` | `Boolean` | 系统 | 逻辑删除标记 |

**不变式方法（示例命名，实现须一致）**：`assertEncounterMainAccountUnique`、`assertSaveOrUpdatePredicate`、`assertBalanceLowerBound`、`assertContractScenarioConsistent`、`assertWriteAfterRead`.

### 4.3 子实体

无。本聚合为单表。

### 4.5 仓储接口

- `AccountRepository extends JpaRepository<Account, Long>`

**说明**：软删由框架统一过滤时，不强制接口名追加 `AndIsDeletedFalse`。

### 4.6 查询模型（`queries.model`）

每个读方法一个子节，字段级逐行列清：

#### 4.6.1 `SelfPayAccountIdQueryModel`（对应 `getSelfPayAccount`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `accountId` | `Long` | `adm_account.id` | 默认**个人现金账户**主键（`adm_account.type_code` 为 **`AccountTypeCode.PERSONAL_CASH_ACCOUNT.getCode()`**；与 DM §6.2.1、`getSelfPayAccount` 同口径） |
| `encounterId` | `Long` | `adm_account.encounter_id` | 就诊标识 |

#### 4.6.2 `MedicalInsuranceAccountIdQueryModel`（对应 `getMedicalInsuranceAccount`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `accountId` | `Long` | `adm_account.id` | 医保账户主键 |
| `encounterId` | `Long` | `adm_account.encounter_id` | 就诊标识 |

#### 4.6.3 `EncounterMainAccountQueryModel`（对应 `getAccountByEncounterFlag`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `id` | `Long` | `adm_account.id` | 账户标识 |
| `encounterFlag` | `Boolean` | `adm_account.encounter_flag` | 登记主户标志 |
| `balanceAmount` | `BigDecimal` | `adm_account.balance_amount` | 当前余额 |

#### 4.6.4 `YbAccountQueryModel`（对应 `getYbAccount`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `id` | `Long` | `adm_account.id` | 账户标识 |
| `typeCode` | `String` | `adm_account.type_code` | **`AccountTypeCode.getCode()`** 持久化值 |
| `insutype` | `String` | `adm_account.insutype` | 险种 |

#### 4.6.5 `SelfAccountQueryModel`（对应 `getSelfAccount`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `id` | `Long` | `adm_account.id` | 账户标识 |
| `contractNo` | `String` | `adm_account.contract_no` | 合同号 |
| `balanceAmount` | `BigDecimal` | `adm_account.balance_amount` | 当前余额 |

#### 4.6.6 `EncounterAccountListQueryModel`（对应 `getAccountListByEncounter`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `id` | `Long` | `adm_account.id` | 账户标识 |
| `typeCode` | `String` | `adm_account.type_code` | **`AccountTypeCode.getCode()`** 持久化值 |
| `statusEnum` | `Integer` | `adm_account.status_enum` | 主状态 |
| `billingStatusEnum` | `Integer` | `adm_account.billing_status_enum` | 结账状态 |
| `balanceAmount` | `BigDecimal` | `adm_account.balance_amount` | 余额 |

#### 4.6.7 `StudentSelfAccountIdQueryModel`（对应 `getStudentSelfAccount`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `accountId` | `Long` | `adm_account.id` | 学生场景**个人现金侧**账户主键（与 `getSelfPayAccount` 同枚举探测口径） |
| `contractNo` | `String` | `adm_account.contract_no` | 学生合同号 |

#### 4.6.8 `StudentYbAccountIdQueryModel`（对应 `getStudentYbAccount`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `accountId` | `Long` | `adm_account.id` | 学生场景医保账户主键 |
| `insutype` | `String` | `adm_account.insutype` | 险种 |

#### 4.6.9 `SelfPayFlagQueryModel`（对应 `isSelfPay`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `accountId` | `Long` | `adm_account.id` | 账户标识 |
| `isSelfPay` | `Boolean` | 规则计算 | 是否**个人现金账户**口径（与 `AccountTypeCode.PERSONAL_CASH_ACCOUNT.getCode()` 比较） |

---

## 5. 写侧与读侧白名单（命令字段全集）

### 5.1 写侧 — `AccountDomainService`

#### 5.1.1 `openEncounterAccounts(OpenEncounterAccountsCommand cmd)` → `List<Long>`

**命令文件**：`OpenEncounterAccountsCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `patientId` | `Long` | 患者标识 |
| `encounterId` | `Long` | 就诊标识 |
| `items` | `List<AccountOpenItemCommand>` | 待开立账户行 |
| `operatorId` | `Long` | 操作人 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `Account.assertOpenEncounterAccountsAllowed` |
| `statusTransitionMethod` | `Account.initAsActiveOpen` |
| `repositoryChecks` | `AccountRepository.findByEncounterIdAndEncounterFlagTrue` |
| `eventPublishAnchor` | `new AccountDomainEvents.AccountOpenedEvent(...); DomainEventPublisher.publish(event)` |
| `unitTestCaseId` | `TC-ACC-W-001` |

**方法契约摘要**：前置条件为登记主户唯一约束、批次最小字段完整；持久化影响为批量插入账户行；失败语义为主户冲突或字段缺失时整批回滚；事件触发为每条新开立账户发布 `AccountOpenedEvent`。**注释锚点**：批量校验 -> 批量写入 -> 结果收集 -> 事件。

---

#### 5.1.2 `saveAccountByRegister(SaveAccountByRegisterCommand cmd)` → `Long`

**命令文件**：`SaveAccountByRegisterCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `patientId` | `Long` | 患者标识 |
| `encounterId` | `Long` | 就诊标识 |
| `typeCode` | `String` | 账户类型 |
| `contractNo` | `String` | 合同号 |
| `busNo` | `String` | 账户业务编码 |
| `balanceAmount` | `BigDecimal` | 初始余额 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `Account.assertRegisterMainAccountRule` |
| `statusTransitionMethod` | `Account.initAsEncounterMain` |
| `repositoryChecks` | `AccountRepository.findByEncounterIdAndEncounterFlagTrue` |
| `eventPublishAnchor` | `new AccountDomainEvents.AccountOpenedEvent(...); DomainEventPublisher.publish(event)` |
| `unitTestCaseId` | `TC-ACC-W-002` |

**方法契约摘要**：前置条件为登记主户必须 `encounterFlag=true` 且同就诊仅一条；持久化影响为已存在则幂等返回，不存在则插入主户；失败语义为主户重复冲突时报错；事件触发为插入时发布 `AccountOpenedEvent`。**注释锚点**：存在性校验 -> 幂等判断 -> 插入 -> 事件。

---

#### 5.1.3 `saveOrUpdateAccount(SaveOrUpdateAccountCommand cmd)` → `Long`

**命令文件**：`SaveOrUpdateAccountCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `patientId` | `Long` | 患者标识 |
| `encounterId` | `Long` | 就诊标识 |
| `statusEnum` | `Integer` | 主状态（用于判重口径） |
| `typeCode` | `String` | 账户类型 |
| `balanceAmount` | `BigDecimal` | 余额 |
| `billingStatusEnum` | `Integer` | 结账状态 |
| `contractNo` | `String` | 合同号 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `Account.assertSaveOrUpdatePredicate` |
| `statusTransitionMethod` | `N/A` |
| `repositoryChecks` | `AccountRepository.findActiveByPatientIdAndEncounterId` |
| `eventPublishAnchor` | `new AccountOpenedEvent / AccountBalanceEditedEvent + publish` |
| `unitTestCaseId` | `TC-ACC-W-003` |

**方法契约摘要**：前置条件为按 `patientId+encounterId+ACTIVE` 判重；持久化影响为命中则整行更新、未命中则插入；失败语义为并行多户场景误覆盖风险需调用方先分流；事件触发为插入发 `AccountOpenedEvent`，余额变化更新发 `AccountBalanceEditedEvent`。**注释锚点**：先查 -> 更新或插入 -> 余额差异判定 -> 事件。

---

#### 5.1.4 `adjustAdvanceDepositBalance(AdjustAdvanceDepositBalanceCommand cmd)` → `void`

**命令文件**：`AdjustAdvanceDepositBalanceCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `patientId` | `Long` | 患者标识 |
| `encounterId` | `Long` | 就诊标识 |
| `balanceDelta` | `BigDecimal` | 余额增量：收取为正、退费为负 |
| `operatorId` | `Long` | 操作人（可空） |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `N/A`（由服务内校验） |
| `statusTransitionMethod` | `N/A` |
| `repositoryChecks` | 有效个人现金户（口径同 `AccountQueryService#getRegistrationAccount`） |
| `eventPublishAnchor` | 内部调用 `saveOrUpdateAccount` → `AccountBalanceEditedEvent` |
| `unitTestCaseId` | `TC-ACC-W-004` |

**方法契约摘要**：前置条件为住院预交金支付已 `settleInpatientPayment`（**DM-FIN-002** §9.1、**BR-IP-PREP-005**）；`balanceDelta` 为 0 则 no-op；持久化影响为就诊个人现金户余额增减；失败语义为无户 → **EX-IP-PREP-002**，退费后余额 &lt; 0 → **EX-IP-PREP-005**；调用方为 **AS-IP-FIN-001** 登记首笔、**AS-IP-FIN-002** 续收/退费。**注释锚点**：查户 -> 余额门禁 -> `saveOrUpdateAccount`。

#### 5.1.5 `openInpatientRegAccount(Long patientId, Long encounterId, String contractNo, Long operatorId)` → `void`

**命令文件**：无（原子 API；由 **AS-IP-FIN-001** IF-IP-REG-03 编排）。

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `N/A` |
| `statusTransitionMethod` | `N/A` |
| `repositoryChecks` | 就诊 `IMP` 有效；同就诊未重复开户 |
| `eventPublishAnchor` | `AccountOpenedEvent` |
| `unitTestCaseId` | `TC-ACC-W-005` |

**方法契约摘要**：住院登记提交时开立 **`PERSONAL_CASH_ACCOUNT`**；余额初始 0；幂等跳过已存在行。

#### 5.1.6 `backfillContractNoIfMissing(Long accountId, String contractNo)` → `void`

**命令文件**：无。

| 绑定项 | 内容 |
| ------ | ---- |
| `repositoryChecks` | 账户存在且 `contract_no` 为空 |
| `eventPublishAnchor` | `N/A` |
| `unitTestCaseId` | `TC-ACC-W-006` |

#### 5.1.7 `deactivateMedicalInsuranceAccount(Long encounterId)` → `void`

**命令文件**：无。按就诊停用 `MEDICAL_INSURANCE`：`status_enum=INACTIVE`，`billing_status_enum=closed-voided`。不存在或已停用幂等返回。不改余额、不发事件。

#### 5.1.8 `reactivateMedicalInsuranceAccount(Long encounterId, String contractNo, String insutype)` → `void`

**命令文件**：无。将已停用医保户改回 `ACTIVE` / `open`，并写入本次 `contract_no` 与险种。仍启用则不改合同。无行不插入。不发事件。

### 5.2 读侧 — `AccountQueryService`（`domain.queries`）

所有查询默认 `isDeleted=false`；分页在数据库侧过滤与统计 total。

**出参**填 QueryModel 类名（字段定义见 §4.6 对应子节，此处不重复）。

| 方法 | 入参 | 出参（类名，字段见 §4.6） | 关键实现 | `unitTestCaseId` |
| ---- | ---- | ------------------------- | -------- | ---------------- |
| `getAccountById` | `Long accountId` | `AccountDetailQueryModel` | 账户主键定位 | `TC-ACC-R-001` |
| `getMainAccountByEncounter` | `Long encounterId` | `EncounterMainAccountQueryModel` | 就诊维度主户 | `TC-ACC-R-002` |
| `getAccountByEncounterFlag` | `Long encounterId` | `EncounterFlagAccountQueryModel` | 定位登记主户实体 | `TC-ACC-R-003` |
| `getSelfAccount` / `getSelfPayAccount` | `Long encounterId` | `SelfAccountQueryModel` | 默认**个人现金账户**实体/主键（`PERSONAL_CASH_ACCOUNT`） | `TC-ACC-R-004` |
| `getYbAccount` / `getMedicalInsuranceAccount` | `Long encounterId` | `YbAccountQueryModel` | 医保侧实体/主键 | `TC-ACC-R-005` |
| `getAccountListByEncounter` | `Long encounterId` | `List<EncounterAccountListQueryModel>` | 就诊维度账户全量行 | `TC-ACC-R-006` |
| `getStudentSelfAccount` | `Long encounterId` | `StudentSelfAccountIdQueryModel` | 学生场景个人现金侧主键 | `TC-ACC-R-007` |
| `getStudentYbAccount` | `Long encounterId` | `StudentYbAccountIdQueryModel` | 学生场景医保主键 | `TC-ACC-R-008` |
| `isSelfPay` | `Long accountId` | `SelfPayFlagQueryModel` | 个人现金账户口径判定 | `TC-ACC-R-009` |
| `getRegistrationAccount` | `Long patientId, Long encounterId` | `AccountDetailQueryModel` | 登记主户（`encounterFlag=YES`） | `TC-ACC-R-010` |
| `mapRegistrationAccountsByEncounterIds` | `Collection<Long> encounterIds` | `Map<Long, Account>` | 批量登记户（队列余额回填） | `TC-ACC-R-011` |
| `mapAccountsByIds` | `Collection<Long> accountIds` | `Map<Long, Account>` | 批量按主键加载 | `TC-ACC-R-012` |

**读方法注释锚点（统一）**：构建查询条件（含软删过滤）-> 单次 SQL/仓储调用并完成分页统计 -> 组装 QueryModel（无写库）。

---

## 6. 不变式映射（DM §7）

| BR 编号 | 实现策略摘要 |
| ------- | ------------ |
| BR-ACC-001 | 出账前余额不得低于 `-limitAccount`（编排断言） |
| BR-ACC-002 | 冻结能力未落字段，扩展需同轮补齐 |
| BR-ACC-003 | `saveOrUpdateAccount` 使用固定判重口径 |
| BR-ACC-004 | 开立路径不分门急住，主户与并行户统一编排 |
| BR-ACC-QRY-001 | 大报表不在写模型上做重 JOIN，走QueryService边界 |
| BR-ACC-QRY-002 | 学生口径与普通口径方法禁止混用 |
| BR-ACC-QRY-003 | 写入前需要账户事实时先读再写 |
| PR-ACC-001 | 同一就诊登记主户最多一条且幂等 |
| PR-ACC-002 | `encounterId+typeCode+contractNo` 开立前先查后写 |
| PR-ACC-003 | 并行多户更新优先定向更新，避免 upsert 误覆盖 |

---

## 7. 事件契约与触发规则（DM §5）

| 事件类名 | 事件类包路径 | 最小载荷（字段） | 触发方法 |
| -------- | ------------ | ---------------- | -------- |
| `AccountDomainEvents.AccountOpenedEvent` | `com.zxplat.domain.share.events.financial.account` | `accountId,patientId,patientName,encounterId,typeCode,balanceAmount` | `openEncounterAccounts` / `saveAccountByRegister` / `saveOrUpdateAccount(插入)` |
| `AccountDomainEvents.AccountBalanceEditedEvent` | `com.zxplat.domain.share.events.financial.account` | `accountId,beforeBalance,afterBalance,encounterId` | `saveOrUpdateAccount(更新且余额变化)` |

**发布时机**：全部 **beforeCommit**；载荷为最小业务键 + 关键快照。

---

## 8. 删除与可见性契约

- Account 聚合默认逻辑删除语义，禁止物理删除历史账户行。
- 非 ACTIVE 账户默认不参与新业务引用，但历史记账可追溯。
- 就诊主户（`encounterFlag=true`）需保持单一语义，重复开立走幂等或拒绝。
- 并行多户场景下，读方法必须按场景口径选择，避免主键混取。
- 大报表与流水分析走QueryService边界，不在写模型重联表。

---

## 9. 框架优先适配规则

- 软删、审计字段、工作单元事务、beforeCommit 事件发布遵循框架默认能力。
- 领域服务与查询服务采用直接实现类，不默认接口+Impl 双层。
- 一方法一 Command，禁止命令容器类。
- 仓储仅保留 `JpaRepository` 接口；跨域支付/收费编排在应用层实现。
- 分页和统计一致性由数据库侧保证。

---

## 10. 生成与验收清单

- [ ] §4 字段表与 DM §3 对齐（单表字段完整）
- [ ] §5.1 写方法小节数量 = 3，与 DM §6.1 一致
- [ ] 每个写方法均包含命令文件、字段全集、绑定项、方法契约摘要
- [ ] §5.2 读方法覆盖 DM §6.2 全部 9 个方法
- [ ] §6 BR 映射覆盖 BR-ACC-* 与 PR-ACC-* 规则
- [ ] §7 事件表覆盖 DM §5 全部 2 类事件
- [ ] 并行多户与主户唯一语义全篇一致
- [ ] 全文无占位语、无待确认语

---

## 11. 单次会话代码生成门禁（DoD）

### 11.1 前置输入

- 锁定 DM：`DM-ADM-009-Account-就诊账户.md`
- 本 SDD 文件（通过结构/字段/方法/事件门禁）
- 目标模块：`zxplat/zxplat-domain`
- 共享事件包可用：`com.zxplat.domain.share.events.financial.account`

### 11.2 DoD

- 写侧 3 个方法全部落地，签名与 Command 命名与 §5 一致。
- 账户字段、判重口径、主户语义与 §4/§6 一致。
- 事件按 §7 beforeCommit 发布，载荷字段不少于最小要求。
- BR 至少一条绑定一个测试用例 ID，可追溯到 `unitTestCaseId`。
- 读侧 9 个方法与 §4.6 QueryModel 对齐，口径不混用。

### 11.3 失败处理

- 任一方法契约无法实现时立即停机并回文档会话修订。
- 若 DM 白名单方法/事件/BR 变更，先更新 SDD 再进入代码会话。
- 若并行多户语义与 upsert 口径冲突未解，DoD 失败不得进入成码。

---

## 定稿检查清单（可打勾）

- [ ] 主章节结构与金骨架一致（输入检查、1-11、定稿检查、评分、结束语）
- [ ] §5.1 子节数量 = DM 写方法数量（3）
- [ ] §4 字段表覆盖 DM §3 字段，不截断
- [ ] §7 事件表覆盖 DM §5 全部事件类型（2）
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

## 10. 变更历史（相对 DM-ADM-009 v1.1）

| 版本 | 日期 | 变更摘要 |
| --- | --- | --- |
| v1.2 | 2026-05-28 | §5.1.5～5.1.6 增补 `openInpatientRegAccount`/`backfillContractNoIfMissing`；§5.2 增补 `getRegistrationAccount`/`mapRegistrationAccountsByEncounterIds`/`mapAccountsByIds`；对齐 **DM v1.2** |
| 2026-05-19 修订 | 2026-05-19 | §5.1.4 新增 `adjustAdvanceDepositBalance` / `AdjustAdvanceDepositBalanceCommand`（住院预交金 settle 后调余额） |
| 2026-05-04 修订 | 2026-05-04 | §4.6.1/§4.6.7/§4.6.9、§5.2 读方法摘要：「自费」统一为**个人现金账户**（`AccountTypeCode.PERSONAL_CASH_ACCOUNT`）；与上游 **DM-ADM-009 v1.1** 及 `AccountTypeCode.getCode()` 落库口径一致。 |

---

## 12. 与 DM 同步记录

| 日期 | 摘要 |
| --- | --- |
| 2026-05-28 | **对账收口**：§5.1.5～5.1.6 + §5.2 三读法；与 `AccountDomainService`/`AccountQueryService` 对齐 |
| 2026-05-28 | **对账收口**：与 `zxplat-core/zxplat-domain` 现行实现一致；本聚合**无** InpatientInfo/OutpatientInfo/OrderInfo ReadModel 消费（跨聚合读组装见 **DM-ADM-002** §6.2.18～20 · `EncounterContextFacade`）。 |（已迁至 EncounterContextReader / application.shared.encounter）

---

**本 SDD 正文结束。**

