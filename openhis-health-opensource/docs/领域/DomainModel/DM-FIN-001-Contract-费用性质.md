# DM-FIN-001 Contract 费用性质

> **限界上下文**：费用域 · 费用性质管理  
> **聚合类型**：实体聚合（定义类主数据）  
> **版本**：v1.0.2

---

## 1. 概述

### 1.1 业务定义

费用性质（Contract）是患者就诊费用承担方式的主数据定义，回答的核心业务问题是："**这次就诊由谁来买单、按什么规则报销？**"——它是挂号登记、账户创建、收费计费和医保结算的前置基础。

### 1.2 核心职责


| 职责       | 说明                                           |
| -------- | -------------------------------------------- |
| 费用承担方式定义 | 维护自费、公费、省医保、市医保、职工医保等费用性质主数据                 |
| 编码与检索    | 管理费用性质的业务编码（`busNo`）、名称，支持挂号窗口快速选取           |
| 分类与排序    | 通过分类枚举和显示顺序支持前端分组展示和排序                       |
| 医保区划对齐   | 通过医保区划字段（`admVs`）关联医保统筹区，支撑医保结算类型确定          |
| 医保终端配置   | 维护医保接口对接所需的密钥、账号、定点机构等配置信息（院内 2025-06-18 新增） |
| 启停与生命周期  | 支持费用性质的启用、停用和逻辑删除，保留历史数据                     |


### 1.3 适用范围


| 类型     | 说明                                 |
| ------ | ---------------------------------- |
| 门诊挂号   | 挂号时选择费用性质（自费/医保），决定后续账户类型与计费规则     |
| 住院登记   | 入院登记时选择费用性质，关联医保类型                 |
| 账户创建   | Account 创建时引用费用性质标识，确定账户的费用承担方式    |
| 收费计费   | ChargeItem 计费时按费用性质确定医保目录分类和报销比例   |
| 医保结算   | 结算时按费用性质确定医保申报类型，通过绑定的医保终端配置对接医保平台 |
| 费用性质切换 | 患者中途补办医保或切换费用性质时，驱动账户切换与收费明细重算     |


### 1.4 与 FHIR Contract 的关系

FHIR R5 `Contract` 资源是通用的法律协议/合约模型，语义范围极广（保险合同、知情同意等）。本系统仅取其"费用承担协议"子语义——将 Contract 窄化为"费用性质"主数据，用 `type` 区分费用承担方式。标准交换时需通过 `Contract.type = financial-nature` 标记本地用途。

---

## 2. 聚合根与持久化形态

### 2.1 持久化形态

**持久化形态**：单表。

聚合根对应表 `fin_contract`，主键 `id`；一行表示一条费用性质主数据（含医保终端配置等扩展列）。

**高频查询模式（建议索引口径）**：按 `bus_no`、`name`/`py_str`/`wb_str`、`category_enum`、`status_enum`、`active_flag`、`org_id`、租户；窗口下拉按 `display_order` 与分类分组。

### 2.2 聚合根标识


| 属性     | 类型           | 说明                 |
| ------ | ------------ | ------------------ |
| 费用性质标识 | `ContractId` | 全局唯一标识，系统生成（雪花 ID） |


### 2.3 状态机

```
  [启用] ──停用──> [已停用] ──启用──> [启用]
    │                  │
    └──逻辑删除──>     └──逻辑删除──>  （逻辑删除，保留审计）
```

#### 状态定义


| value | code | info | 语义 |
| ----- | ---- | ---- | ---- |
| 1 | ACTIVE | 启用 | 生效中，可被挂号、账户创建等业务引用，允许编辑、停用、逻辑删除 |
| 2 | INACTIVE | 已停用 | 暂时停用，已关联的历史数据保留，新业务不可选取，允许启用、逻辑删除 |


> **状态枚举说明**：当前实现复用 `AccountStatus` 枚举控制启停（院内持久化字段 `statusEnum`，应用侧 `activeFlag`）。新建时默认启用（`ACTIVE`）。逻辑删除由 `BaseAuditedEntity` 的逻辑删除字段（`isDeleted` 等）承载，与状态字段正交，不作为状态枚举值。

---

## 3. 实体

本聚合为单表聚合，不设独立子实体。费用性质的分类通过值对象 `ContractCategory` 表达。

```
Contract（聚合根）
════════════════════════════════════════════════════════════

  ┌──────────────────────────────────────────────────────┐
  │  busNo / contractName / categoryEnum / statusEnum    │
  │  orgId / ybFlag / sort / admVs                       │
  │  医保终端配置（clientId / clientSecret / username     │
  │    / password / cliPrvKey / cliPubKey / scope ...）   │
  │  fixmedinsCode / fixmedinsName                       │
  └──────────────────────────────────────────────────────┘
```

### 3.1 费用性质（Contract）

Contract 聚合根的属性定义，对应 `fin_contract` 表。


| 属性       | 字段名              | 类型                            | 必填  | 说明                                |
| -------- | ---------------- | ----------------------------- | --- | --------------------------------- |
| 主键       | `id`             | `Long`                        | 是   | 雪花 ID                             |
| 业务编码     | `bus_no`         | `String`                      | 是   | 费用性质编码（如 `ZF`=自费），领域服务校验非空        |
| 名称       | `name`           | `String`                      | 是   | 费用性质名称（如"自费""省医保"）                |
| 费用性质分类   | `category_enum`  | `Integer`（`ContractCategory`） | 是   | 见 §4.1                            |
| 状态       | `status_enum`    | `Integer`（`AccountStatus`）    | 是   | 生命周期状态（ACTIVE/INACTIVE），见 §2.3 |
| 是否启用     | `active_flag`    | `Boolean`                     | 是   | `status_enum == ACTIVE` 时为 `true`，其余为 `false` |
| 机构标识     | `org_id`         | `Long`                        | 否   | 所属机构/组织标识                         |
| 是否医保     | `yb_flag`        | `Boolean`                     | 否   | 医保标志（`true`=医保，`false`=非医保）       |
| 显示顺序     | `display_order`  | `Integer`                     | 否   | 前端列表排序权重，数值越小越靠前                  |
| 医保区划     | `adm_vs`         | `String`                      | 否   | 医保统筹区代码（如省级行政区划编码），医保类费用性质填写      |
| 拼音检索码    | `py_str`         | `String`                      | 否   | 由名称自动生成，用于拼音首字母检索                 |
| 五笔检索码    | `wb_str`         | `String`                      | 否   | 由名称自动生成，用于五笔编码检索                  |
| 医保客户端标识  | `client_id`      | `String`                      | 否   | 医保终端接口的 clientId                  |
| 医保客户端密钥  | `client_secret`  | `String`                      | 否   | 医保终端接口的 clientSecret              |
| 医保账号     | `username`       | `String`                      | 否   | 医保终端接口的登录账号                       |
| 医保密码     | `password`       | `String`                      | 否   | 医保终端接口的登录密码                       |
| 医保私钥     | `cli_prv_key`    | `String`                      | 否   | 医保终端的客户端私钥                        |
| 医保公钥     | `cli_pub_key`    | `String`                      | 否   | 医保终端的客户端公钥                        |
| 服务端公钥    | `server_pub_key` | `String`                      | 否   | 医保终端的服务端公钥                        |
| 医保授权范围   | `scope`          | `String`                      | 否   | 医保终端授权范围                          |
| 医保授权类型   | `grant_type`     | `String`                      | 否   | 医保终端授权类型                          |
| 定点医疗机构编码 | `fixmedins_code` | `String`                      | 否   | 医保定点机构编码                          |
| 定点医疗机构名称 | `fixmedins_name` | `String`                      | 否   | 医保定点机构名称                          |


> 通用审计字段（`createdAt/updatedAt/createdBy/updatedBy`）、租户字段（`tenantId`）、逻辑删除字段（`isDeleted/deletedAt/deletedBy`）继承自 `BaseAuditedEntity`，遵循统一约定。

> **设计预留**：
>
> - **医保结算类型**（`InsuranceSettlementType`）——标识医保申报结算通道（门诊统筹/住院统筹/门慢特等），当前由外部配置或医保接口参数承载

> **CHS 扩展模块（`zxplat-module-chs-jilin`）读侧约定**：
>
> - **配置键**：`fin_contract.bus_no` **仅**作为 `chs-contract.properties` 的关联键（`contract.{bus_no}.*`）；`fixmedins_code`、`adm_vs`、OAuth 密钥等终端参数**不读库表**，由 CHS 模块 properties 提供（见 `Contract` 实体 Javadoc）。
> - **医保判定**：`InsuranceContractCategorySupport.isInsuranceContract(ybFlag, categoryEnum)`（`zxplat-domain`）综合 `yb_flag` 与 `ContractCategory` 判定是否按医保合同处理。
> - **bridge 读路径**：`ContractQueryService.findOptionalContractEntityByBusNo` / `findOptionalContractEntityById` 返回实体供 `InsuranceContractResolver` 组装；禁止 bridge 注入 `ContractRepository`。
> - **适用就诊类型**（`ApplicableEncounterType`）——限定门诊/住院/通用，当前未在实体中建模
> - **备注**（`description`）——补充说明字段

---

## 4. 值对象

### 4.1 费用性质分类（ContractCategory）

对齐公共枚举 `ContractCategory`。


| 编码值 | 枚举常量                   | 说明   |
| --- | ---------------------- | ---- |
| 1   | `SELF_CHARGE`          | 自费   |
| 2   | `PUBLIC_CHARGE`        | 公费   |
| 3   | `PROVINCIAL_INSURANCE` | 省医保  |
| 4   | `MUNICIPAL_INSURANCE`  | 市医保  |
| 5   | `WORKER_INSURANCE`     | 职工医保 |


### 4.2 费用性质状态

复用 `AccountStatus` 枚举，Contract 实际使用以下两个值：


| 编码值 | 枚举常量       | 说明      |
| --- | ---------- | ------- |
| 1   | `ACTIVE`   | 启用 / 有效 |
| 2   | `INACTIVE` | 停用 / 无效 |


> 完整 `AccountStatus` 枚举还包含 `ERROR(3)` / `ON_HOLD(4)` / `UNKNOWN(5)`，Contract 当前仅使用 `ACTIVE` 和 `INACTIVE`。

---

## 5. 领域事件

> **当前实现说明**：当前实现均未对 Contract 发布独立领域事件。以下为设计预留，待后续迭代实现。

### 5.1 事件定义


| 事件名称    | 英文名称                       | 触发时机         | 携带数据                       |
| ------- | -------------------------- | ------------ | -------------------------- |
| 费用性质已创建 | `ContractCreatedEvent`     | 新建费用性质       | 费用性质标识, 业务编码, 名称, 分类, 租户标识 |
| 费用性质已编辑 | `ContractEditedEvent`      | 编辑名称/分类/检索码等 | 费用性质标识, 变更字段列表, 编辑时间       |
| 费用性质已启用 | `ContractActivatedEvent`   | 已停用→启用       | 费用性质标识, 业务编码               |
| 费用性质已停用 | `ContractDeactivatedEvent` | 启用→已停用       | 费用性质标识, 业务编码, 停用时间         |
| 费用性质已删除 | `ContractDeletedEvent`     | 逻辑删除         | 费用性质标识, 业务编码, 删除时间         |


### 5.2 事件触发规则


| 当前状态   | 操作   | 目标状态 | 发布事件                       |
| ------ | ---- | ---- | -------------------------- |
| —      | 创建   | 启用   | `ContractCreatedEvent`     |
| 启用     | 编辑   | 启用   | `ContractEditedEvent`      |
| 启用     | 停用   | 已停用  | `ContractDeactivatedEvent` |
| 已停用    | 启用   | 启用   | `ContractActivatedEvent`   |
| 启用/已停用 | 逻辑删除 | —    | `ContractDeletedEvent`     |


---

## 6. 领域服务

### 6.1 写侧领域服务（ContractDomainService）

#### 6.1.1 新建费用性质（`createContract`）

- **业务意义**：创建一条可被全院引用的费用性质主数据，并生成检索辅助码与默认启用状态。
- **边界说明**：仅维护 Contract 聚合内落库与缓存失效，不处理挂号、账户、医保结算等跨域编排。
- **入参**：业务编码、名称、分类、显示顺序、医保区划（可选）及医保终端等可选配置字段（以应用服务传入的命令对象为准）。
- **出参**：费用性质（聚合根实体或等价 DTO）。
- **前置条件**：业务编码非空（`BR-CTR-001`）；租户上下文合法。
- **状态变化**：新建记录，默认 `AccountStatus.ACTIVE`（`BR-CTR-003`）。
- **聚合内影响**：持久化主表；自动生成拼音码/五笔码；设置租户标识；写操作后按约定失效 `contract` / `contractList` 缓存（`BR-CTR-004`）。
- **事件输出**：当前实现不发布领域事件（见 §5 设计预留）。
- **失败语义**：业务编码非法、必填缺失或持久化失败时拒绝创建。

#### 6.1.2 编辑费用性质（`editContract`）

- **业务意义**：维护费用性质展示名、分类、排序与检索相关字段，保证主数据持续可用。
- **边界说明**：仅更新 Contract 聚合内可编辑字段，不迁移历史账户引用。
- **入参**：费用性质标识、业务编码、名称、分类、显示顺序及允许变更的配置字段。
- **出参**：费用性质（更新后实体或等价 DTO）。
- **前置条件**：目标记录须存在；业务编码非空（`BR-CTR-001`）。
- **状态变化**：`N/A`（不改变启停枚举语义时状态可不变）。
- **聚合内影响**：更新主表；名称变更时重算拼音码/五笔码；写操作后失效相关缓存（`BR-CTR-004`）。
- **事件输出**：当前实现不发布领域事件（见 §5 设计预留）。
- **失败语义**：记录不存在、业务编码非法或并发更新冲突时拒绝保存。

#### 6.1.3 逻辑删除费用性质（`deleteContract`）

- **业务意义**：将费用性质从默认可选集合中移除，同时保留审计与历史引用完整性。
- **边界说明**：仅执行逻辑删除标记，不物理删除；不处理下游账户批量迁移。
- **入参**：费用性质标识。
- **出参**：—。
- **前置条件**：费用性质标识非空且记录须存在。
- **状态变化**：`N/A`（启停状态可不变，删除标记独立，见 §2.3 说明）。
- **聚合内影响**：逻辑删除（`BR-CTR-002`）；写操作后失效相关缓存（`BR-CTR-004`）。
- **事件输出**：当前实现不发布领域事件（见 §5 设计预留）。
- **失败语义**：标识无效或记录不存在时拒绝删除。

#### 6.1.4 启用费用性质（`activateContract`）

- **业务意义**：恢复已停用费用性质为可被新业务选取的启用态。
- **边界说明**：只变更启停字段，不回补停用期间外部业务数据。
- **入参**：费用性质标识。
- **出参**：—。
- **前置条件**：记录须存在。
- **状态变化**：`status_enum` 设为 `AccountStatus.ACTIVE`；`active_flag` 置 `true`。
- **聚合内影响**：更新状态字段；写操作后失效相关缓存（`BR-CTR-004`）。
- **事件输出**：当前实现不发布领域事件（见 §5 设计预留）。
- **失败语义**：记录不存在或状态不允许时拒绝启用。

#### 6.1.5 停用费用性质（`deactivateContract`）

- **业务意义**：将费用性质从可新选取集合中移出，新业务不可再选，历史引用保留。
- **边界说明**：只变更启停字段，不关闭已开立的账户或就诊。
- **入参**：费用性质标识。
- **出参**：—。
- **前置条件**：记录须存在。
- **状态变化**：设为 `AccountStatus.INACTIVE`。
- **聚合内影响**：更新状态字段；写操作后失效相关缓存（`BR-CTR-004`）。
- **事件输出**：当前实现不发布领域事件（见 §5 设计预留）。
- **失败语义**：记录不存在或状态不允许时拒绝停用。

> **缓存说明**：所有写操作统一通过 `@CacheEvict(value = {"contractList", "contract"}, allEntries = true)` 使读缓存失效。

### 6.2 读侧查询服务（ContractQueryService）

> 查询类动作无写库，以「查询范围（读库，无写入）+ 缓存策略」表达QueryService；用于管理端列表、挂号下拉选择与详情回显。

#### 6.2.1 按主键查询费用性质（`findContractById`）

- **业务意义**：按主键加载单条费用性质，支撑详情与跨域引用回显。
- **边界说明**：只读查询，不写库，不做跨域编排。
- **查询范围（读库，无写入）**：`fin_contract` 主表单条记录。
- **过滤口径**：默认排除已逻辑删除记录（与 `BaseAuditedEntity` 对 `isDeleted` 的约定一致）；按主键精确匹配。
- **组装规则**：直接映射为领域实体或查询 DTO；可走 Spring Cache `@Cacheable(value="contract", key="#id")`。
- **返回约束**：不存在时按实现约定返回空或抛业务异常；不触发写库与缓存写穿透以外的副作用。

#### 6.2.2 查询费用性质列表（`listContracts`）

- **业务意义**：返回当前租户下全量费用性质列表，支撑管理端维护与挂号下拉。
- **边界说明**：只读查询，不写库，不做跨域编排。
- **查询范围（读库，无写入）**：`fin_contract` 租户范围内列表。
- **过滤口径**：按租户隔离；默认排除已逻辑删除（与 `BaseAuditedEntity` 对 `isDeleted` 的约定一致）；可按 `display_order` 等字段排序（见 §3.1）。
- **组装规则**：列表行映射为统一列表 DTO；可走 `@Cacheable(value="contractList")`，缓存键须包含租户标识。
- **返回约束**：只读集合；若实现为全量列表则与性能治理策略（分页/投影）以当期代码为准。

#### 6.2.3 按机构全量列表（`listAllContractsByOrgId`）

- **业务意义**：挂号/结算下拉等按 `orgId` 过滤的全量费用性质列表。
- **出参**：`List<ContractListItemQueryModel>`。

#### 6.2.4 全租户费用性质列表（`listAllContracts`）

- **业务意义**：管理端无机构过滤的全量列表（缓存 `contractList`）。
- **出参**：`List<ContractListItemQueryModel>`。

#### 6.2.5 批量解析费用性质名称（`mapContractNameByBusNoIn`）

- **业务意义**：按 `bus_no` 批量反查 `name` 展示。
- **出参**：`Map<String, String>`；未命中键不写入。

#### 6.2.6 按业务号加载实体（`findOptionalContractEntityByBusNo`）

- **业务意义**：bridge/编排读路径按 `bus_no` 加载 `Contract` 实体（CHS `InsuranceContractResolver`）。
- **边界说明**：只读；排除已软删；`bus_no` 空白时返回 `Optional.empty()`。
- **出参**：`Optional<Contract>`。

#### 6.2.7 按主键加载实体（`findOptionalContractEntityById`）

- **业务意义**：bridge/编排读路径按主键加载 `Contract` 实体。
- **边界说明**：只读；排除已软删；`id` 为空时返回 `Optional.empty()`。
- **出参**：`Optional<Contract>`。

### 6.3 领域边界说明

- 费用性质主数据 CRUD 与启停由 `ContractDomainService` 承载；应用层由 `ContractAppService` 编排。
- 挂号初始化、账户与医嘱等仅**引用**本聚合，不在此展开跨域编排。
- 查询类动作无写库操作，由 `ContractQueryService` 独立承载，遵循 CQRS 分离原则。

---

## 7. 不变式


| 编号         | 不变式                      | 说明                                                                                           |
| ---------- | ------------------------ | -------------------------------------------------------------------------------------------- |
| BR-CTR-001 | 业务编码（`busNo`）不得为空字符串     | 创建/编辑时须校验，非法入参拒绝                                                                             |
| BR-CTR-002 | 删除为逻辑删除，保留审计痕迹           | 费用性质被历史账户引用，物理删除将破坏引用完整性                                                                     |
| BR-CTR-003 | 新建默认启用                   | 创建时默认 `AccountStatus.ACTIVE`，与账户启用枚举语义一致                                                     |
| BR-CTR-004 | 列表与单条读可走缓存，写操作须使相关缓存失效   | 写后缓存失效（`contract` / `contractList`），保证读写一致                                                   |
| BR-CTR-005 | 显示顺序为非负整数                | 防止排序字段配置异常                                                                                   |
| BR-CTR-006 | `busNo` 业务唯一性须在领域或数据库层保证 | 仓储提供 `findByBusNo`、`findByBusNoAndIdIsNot`，当前领域服务未在创建/更新时强制校验 `busNo` 唯一，若业务要求编码唯一需补强数据库唯一约束 |


---

## 8. 上下文映射

### 8.1 上游依赖


| 上游上下文   | 聚合           | 关联方式          | 说明                               |
| ------- | ------------ | ------------- | -------------------------------- |
| 行政管理上下文 | Organization | `机构标识(orgId)` | 费用性质归属的机构，多租户隔离                  |
| 系统参数上下文 | CodeSystem   | 枚举引用          | 费用性质分类下拉选项来源（`ContractCategory`） |


### 8.2 下游影响


| 下游上下文   | 聚合                          | 触发方式  | 说明                                       |
| ------- | --------------------------- | ----- | ---------------------------------------- |
| 费用域     | Account（就诊账户）               | ID 引用 | 账户创建时引用 `费用性质标识(ContractId)`，确定账户的费用承担方式 |
| 费用域     | ChargeItem（收费明细）            | 间接引用  | 通过 Account 关联的费用性质确定计费时的医保目录分类           |
| 费用域     | ChargeItemDefinition（收费项定价） | 间接引用  | 费用性质变更可能触发定价行的医保分类调整                     |
| 就诊管理上下文 | Encounter（就诊）               | ID 引用 | 挂号时选择费用性质，就诊记录关联费用性质编码                   |


### 8.3 引用规则

> **统一规则**：下游聚合引用费用性质时，统一使用 `费用性质标识(ContractId)` 字段名。不使用 `contractNo`（与 FHIR Contract.identifier 混淆）或 `feeType`（非标准命名）。
>
> **与 Account 的引用关系**：
>
> - 一个 Account（就诊账户）关联且仅关联一个费用性质
> - 费用性质切换时，须创建新 Account 或在应用服务层协调 Account 的费用性质切换（参见 DM-ADM-009 Account）
> - 同一就诊允许因费用性质切换产生多个历史账户记录，但同一时刻仅一个活跃账户

### 8.4 事件流示意图

```
                    Contract                  Encounter              Account              ChargeItem
                       │                         │                     │                      │
                       │── (ID 引用) ────────────>│                     │                      │
                       │                         │                     │                      │
                       │                         │── 挂号选择费用性质 ──>│── 创建账户              │
                       │                         │                     │                      │
                       │                         │                     │── 关联费用性质标识 ──────>│
                       │                         │                     │                      │── 按费用性质计费
                       │                         │                     │                      │
```

---

## 9. 补充

### 9.1 费用性质与账户的协作规则

费用性质与就诊账户（Account）的协作是费用域的核心交互之一：

1. **挂号阶段**：患者挂号时选择费用性质，系统据此创建对应的就诊账户
2. **费用性质切换**：患者就诊中途补办医保或更换费用性质时：
  - 应用服务调用 Account 的账户切换方法
  - 未结算的 ChargeItem 需按新费用性质重新计算医保分类
  - 已结算的 ChargeItem 不受影响
3. **多账户场景**：同一就诊因费用性质切换可能产生多条账户记录（旧账户关闭、新账户创建）

### 9.2 文档变更历史

| 版本 | 日期 | 摘要 |
| ---- | ---- | ---- |
| v1.0.2 | 2026-07-11 | §3.1 CHS 扩展：`bus_no` 配置键约定；§6.2.6～7 `findOptionalContractEntityBy*`（**zxplat-module-chs-jilin** bridge） |
| v1.0.1 | 2026-05-28 | **M1**：§6.2.3～5 `listAllContractsByOrgId`/`listAllContracts`/`mapContractNameByBusNoIn` |

<!-- MODE-B-METHOD-INVENTORY 2026-09-23 -->

## 附录 · 模式 B 已实现方法清单（2026-09-23）

> 自 zxplat-domain 对应 *DomainService / *QueryService 抽取 public 方法名，用于关闭方法白名单 DOC-DRIFT。细规仍以正文 §6 与源码为准；本表不替代九章成稿。

### DomainService：`ContractDomainService`

| 方法 |
|------|
| `createContract` |
| `editContract` |
| `deleteContract` |
| `activateContract` |
| `deactivateContract` |

### QueryService：`ContractQueryService`

| 方法 |
|------|
| `findOptionalContractEntityByBusNo` |
| `findOptionalContractEntityById` |
| `findContractById` |
| `listContracts` |
| `listAllContractsByOrgId` |
| `listAllContracts` |
| `listContractsByIds` |
| `mapContractNameByBusNoIn` |
| `mapCategoryEnumByBusNoIn` |
| `findAllContractEntitiesByBusNoIn` |
| `mapContractIdByBusNoIn` |
| `listIdsByCategoryEnum` |

