# DM-WKF-001 ActivityDefinition 诊疗项目定义

> **限界上下文**：基础数据 · 诊疗项目目录\
> **聚合类型**：实体聚合\
> **版本**：v1.0.21（列表页展示销售/计价单位；`pageServiceCatalog` 行含 `packageUnitCode`）

## 1. 概述

### 1.1 业务定义

诊疗项目定义（ActivityDefinition）是对医疗机构可开展的**诊疗服务活动的标准化描述**。它定义了一项诊疗活动的编码、名称、分类、价格、适用范围、执行规范等元数据，是 HIS 中**收费项目目录、临床路径、医嘱模板、服务申请**等业务的基础数据支撑。

在临床业务中，诊疗项目定义回答的核心问题是："**医院能做什么？**"——它是服务目录、价表、临床路径的基础。

### 1.2 核心职责


| 职责        | 说明                       |
| --------- | ------------------------ |
| 诊疗活动标准化描述 | 为每一项可执行的诊疗活动赋予唯一编码与标准化属性 |
| 价格管理      | 维护项目在各收费体系下的标准价格         |
| 临床适用性约束   | 定义项目的适用科室、适用人群、禁忌条件      |
| 执行规范关联    | 关联操作指南、临床路径步骤、医嘱模板       |
| 医保目录映射    | 维护项目与医保目录（甲/乙/丙类）的对应关系   |


### 1.3 适用范围


| 类型  | 说明            | 示例          |
| --- | ------------- | ----------- |
| 检查类 | 影像、超声、内镜等检查项目 | 腹部CT平扫、胃镜检查 |
| 检验类 | 实验室检验项目       | 血常规、肝功能     |
| 治疗类 | 各类治疗操作        | 静脉输液、换药、理疗  |
| 手术类 | 手术及操作项目       | 阑尾切除术       |
| 护理类 | 护理服务项目        | 一级护理、特级护理   |
| 诊察类 | 门诊诊察、会诊       | 主任医师诊察费     |
| 其他类 | 床位费、耗材费等      | 普通床位费       |


### 1.4 与药品目录的边界

> **与 MedicationRequest 的边界**：诊疗项目定义**不包含药品**。药品由独立的药品目录（Formulary/medicationCatalog）管理，药品申请通过 MedicationRequest 发起。当一项诊疗活动**必须伴随药品使用**时（如化疗方案），通过 ActivityDefinition 的"配套医嘱模板"关联药品。

## 2. 聚合根与持久化形态

### 2.1 持久化形态

**持久化形态**：主表 + 子项表（1:N）；主表可同时冗余 `children_json` 作快照或迁移过渡，**子项事实行以子表为准**时须满足 BR-ACTDEF-005/006/007/008。

**表结构**：

- 主表：`wkf_activity_definition`（主键：`id`）
- 子表：`wkf_activity_definition_children`（外键：`activity_id` → 主表 `id`，1:N，N 可为 0）

**高频查询模式**：

- 按项目标识：主表主键查询
- 按业务编码：主表 `bus_no` 索引扫描
- 按名称/拼音/五笔：主表 `name` / `py_str` / `wb_str` 辅助检索

### 2.2 聚合根标识


| 属性   | 类型                     | 说明       |
| ---- | ---------------------- | -------- |
| 项目标识 | `ActivityDefinitionId` | 系统内部唯一标识 |


### 2.3 状态机

```
  [草稿] ──发布──> [已发布] ──停用──> [已停用] ──恢复──> [已发布]
```

#### 状态定义

| value | code | info | 语义 |
| ----- | ---- | ---- | ---- |
| 1 | DRAFT | 草稿 | 新建尚未发布，可编辑，允许编辑、发布、标记废弃 |
| 2 | ACTIVE | 已发布 | 已发布到生产环境，临床可引用，允许停用 |
| 3 | RETIRED | 已停用 | 暂时停用，不再可引用，允许恢复、标记废弃 |
| 4 | UNKNOWN | 未知 | 状态未知或数据迁移中待确认，仅允许人工修复 |


> **状态枚举说明**：`draft` / `active` / `retired` / `unknown` 与 FHIR ActivityDefinition 的 `status` 值域保持一致。文档中的“已废弃/逻辑删除”不再映射到 `status`，改由本地扩展字段（如 `lifecycleFlag=DEPRECATED`）表达。

## 3. 实体

```
ActivityDefinition（聚合根）
════════════════════════════════════════════════════════════

  ┌──────────────────────────────────────────────────────┐
  │               ActivityDefinition                     │
  │  - category_code / bus_no / name                    │
  │  - py_str / wb_str / permitted_unit_code            │
  │  - type_enum / status_enum                          │
  │  - yb_flag / yb_no / yb_match_flag / chrgitm_lv    │
  │  - mr_settlement_type_code                         │
  │  - org_id / location_id / description_text         │
  │  - children_json / children_flag                   │
  │                                                      │
  │  ┌──────────────────────────────────────────────┐    │
  │  │ ActivityDefinitionChild                      │    │
  │  │ - activity_id / children_id / children_num  │    │
  │  │ - children_name                              │    │
  │  └──────────────────────────────────────────────┘    │
  └──────────────────────────────────────────────────────┘
```

### 3.1 诊疗项目定义（ActivityDefinition）

ActivityDefinition 聚合根的属性定义，对应 `wkf_activity_definition` 表。


| 属性      | 字段名                   | 类型        | 必填  | 说明                     |
| ------- | --------------------- | --------- | --- | ---------------------- |
| 项目标识    | `id`                  | `Long`    | 是   | 主键                     |
| 目录类别编码  | `category_code`       | `String`  | 是   | 诊疗项目目录分类               |
| 业务编码    | `bus_no`              | `String`  | 是   | 全局唯一业务编码               |
| 项目名称    | `name`                | `String`  | 是   | 诊疗项目显示名                |
| 拼音检索码   | `py_str`              | `String`  | 否   | 名称拼音首字母                |
| 五笔检索码   | `wb_str`              | `String`  | 否   | 名称五笔检索码                |
| 使用单位编码  | `permitted_unit_code` | `String`  | 否   | 执行计量单位                 |
| 诊疗类型    | `type_enum`           | `String`  | 是   | 业务类型**标签**码，术语 `activity_type_code`，取值见 §4.2；维护页/创建更新**必填**（**BR-ACTDEF-011**）；**非**医嘱通道（通道认 `category_code`） |
| 发布状态    | `status_enum`         | `Integer` | 是   | 对齐 `PublicationStatus`，取值见 §4.3 |
| 说明      | `description_text`    | `String`  | 否   | 项目说明文本                 |
| 身体部位编码  | `body_site_code`      | `String`  | 否   | 适用身体部位                 |
| 标本编码    | `specimen_code`       | `String`  | 否   | 检验标本类型                 |
| 所属机构标识  | `org_id`              | `Long`    | 否   | 科室/机构标识                |
| 位置标识    | `location_id`         | `Long`    | 否   | 执行地点标识                 |
| 计费规则标识  | `rule_id`             | `Integer` | 否   | 计费规则                   |
| 划价标记    | `pricing_flag`        | `Boolean` | 否   | 是否参与划价                 |
| 医保标记    | `yb_flag`             | `Boolean` | 否   | 是否医保项目                 |
| 医保编码    | `yb_no`               | `String`  | 否   | 医保目录编码                 |
| 医保对码标记  | `yb_match_flag`       | `Boolean` | 否   | 对码完成标记                 |
| 医保收费等级  | `chrgitm_lv`          | `Integer` | 否   | 医保收费项目等级               |
| 病案结算类型  | `mr_settlement_type_code` | `String` | 否 | 病案首页费用结算分类；术语体系 `mr_settlement_type_code` |
| 子项 JSON | `children_json`       | `String`  | 否   | 子项定义 JSON（可与子表并存，以产品约定谁为展示源） |
| 子项标记    | `children_flag`       | `Boolean` | 否   | 是否包含子项（与子表行一致，见 BR-ACTDEF-006） |


> 通用审计字段（含租户等）继承自 `HisBaseEntity`，不在此重复列举。

### 3.2 诊疗项目子项（ActivityDefinitionChild）

子实体，对应表 `wkf_activity_definition_children`；与主表为 1:N（N 可为 0），外键 `activity_id` 指向 `wkf_activity_definition.id`。

| 属性           | 字段名           | 类型        | 必填 | 说明                    |
| ------------ | ------------- | --------- | -- | --------------------- |
| 子项行标识       | `id`          | `Long`    | 是  | 主键                    |
| 父项标识        | `activity_id` | `Long`    | 是  | 所属诊疗项目，同主表 `id`      |
| 子项诊疗项目标识   | `children_id` | `Long`    | 是  | 被引用的子诊疗项目主键           |
| 子项数量        | `children_num` | `Integer` | 否  | 相对父项的数量系数（上传/展开数量 = 父数量 × `children_num`；空按 1） |
| 子项名称（快照）   | `children_name` | `String`  | 否  | 回显/快照，可与被引用项目名冗余同步 |

## 4. 值对象

### 4.1 术语编码与存储口径

无独立值对象；分类、单位、标本、部位等使用术语编码字符串 + 码表解析。

### 4.2 `type_enum`（诊疗业务类型）

列名仍为 `type_enum`，**语义为术语字典** `activity_type_code`（Flyway `V10__seed_platform`）。落库存 **String code**，与历史枚举码一致，**无痛切换、无需刷存量数据**。

| 种子码 | 中文 | 说明 |
| --- | --- | --- |
| `1` | 检验 | 实验室检验项目 |
| `2` | 检查 | 影像、超声、内镜等检查项目 |
| `3` | 护理 | 护理类目录项 |
| `4` | 手术 | 手术及操作目录项 |
| `5` | 其他 | 其他诊疗目录项 |
| `6` | 外检 | 外送检验/检查，非本院执行 |
| `7` | 诊查 | 诊查费类 |

> **维护口径**：`getDiseaseTreatmentInit` / 导入 / 诊疗目录表单 / PEIS 目录的 `typeEnumOptions` **必须**由术语 `activity_type_code` 生成（**BR-ACTDEF-010**）。现场可增码（如 CT、DR）；申请单「分类」chips 按本字段查询（`pageForCatalog.typeEnum`），**不**改目录分类 `category_code`。`type_enum` **必填**（**BR-ACTDEF-011**）：前端 `rules: required`，后端空/`null` 拒绝；**禁止**前后端写死枚举码兜底。
>
> **与通道分工**：医嘱通道、申请单类型、LIS/PACS 外发认 **`category_code` / `ServiceRequest.category_enum`**；`type_enum` **仅**目录标签与 chips，**无**业务分流身份。已删除 Java `ActivityType` 枚举，禁止再引入等价常量类生成下拉。
>
> **嘱托医嘱**：不以本字段识别；固定项「嘱托」挂 **`category_code=52`**（术语 `activity_category_code` / **DM-WKF-004 §4.3**）；见 **AS-IP-DOR-003 · BR-IP-ORD-028**。

### 4.3 `status_enum`（发布状态）

| 常量名（实现可有别名） | 整型码（默认约定） | 英文编码 | 中文 | 说明 |
| --- | --- | --- | --- | --- |
| `PUBLICATION_STATUS_DRAFT` | 1 | `draft` | 草稿 | 语义与允许操作见 §2.3 |
| `PUBLICATION_STATUS_ACTIVE` | 2 | `active` | 已发布 | 同上 |
| `PUBLICATION_STATUS_RETIRED` | 3 | `retired` | 已停用 | 同上 |
| `PUBLICATION_STATUS_UNKNOWN` | 4 | `unknown` | 未知 | 同上 |

> **码表约束**：英文编码必须与 §2.3 状态定义表一致；整型码若与现存库不一致，以实现迁移脚本为准，并回填本 DM 表。

## 5. 领域事件

### 5.1 事件定义

| 编号 | 事件名称 | 英文名称 | 触发时机 | 携带数据 / 最小载荷 | 消费方 |
| --- | --- | --- | --- | --- | --- |
| 5.1.1 | 诊疗项目已创建 | `ActivityDefinitionCreatedEvent` | 新建诊疗项目成功 | `ActivityDefinitionId`、`bus_no`、`name`（最小载荷） | 审计订阅方（可选）；目录混页由 **`ActivityDefinitionQueryService#pageForCatalog`** 直读主表 |
| 5.1.2 | 诊疗项目已更新 | `ActivityDefinitionUpdatedEvent` | 维护诊疗项目、对码回写等非价格快照类字段变更成功 | `ActivityDefinitionId`、修改字段摘要（含对码相关变更时须显式包含） | 产品目录QueryService；引用门控侧缓存失效（可选） |
| 5.1.3 | 诊疗项目已停用 | `ActivityDefinitionRetiredEvent` | 批量停用成功 | `ActivityDefinitionId` 列表、目标状态 `retired` | ServiceRequest 开立引用门控；领域事件后查询刷新 |
| 5.1.4 | 诊疗项目已启用 | `ActivityDefinitionActivatedEvent` | 批量启用成功 | `ActivityDefinitionId` 列表、目标状态 `active` | 同上（恢复可引用） |
| 5.1.5 | 诊疗项目展示价已变更 | `ActivityDefinitionPriceUpdatedEvent` | 目录展示价或计价展示相关字段变更并落库成功（口径见 §9.3、§9.6） | `ActivityDefinitionId`、新/旧 `standardPrice`（或版本号）、变更原因码（以落库与交互契约为准） | ChargeItem 上下文（收费标准缓存）；ChargeItemDefinition 编排（评估是否联动 SALE 定价行） |

### 5.2 事件触发规则

| 当前状态 | 操作 | 目标状态 | 发布事件 |
| --- | --- | --- | --- |
| — | 新建项目 | 草稿或已发布（未显式传入状态时默认草稿） | `ActivityDefinitionCreatedEvent` |
| 草稿 | 批量启用（发布） | 已发布 | `ActivityDefinitionActivatedEvent` |
| 草稿 / 已发布 | 维护项目（非展示价类字段） | 原状态不变 | `ActivityDefinitionUpdatedEvent` |
| 已发布 | 批量停用 | 已停用 | `ActivityDefinitionRetiredEvent` |
| 已停用 | 批量启用（恢复） | 已发布 | `ActivityDefinitionActivatedEvent` |
| 已发布 | 批量启用（重复） | 已发布（幂等） | 可不重复发事件（以实现为准） |
| 已发布 | 变更目录展示价或计价展示相关字段（见 §9.6） | 已发布（状态不变） | `ActivityDefinitionPriceUpdatedEvent` |
| — | 批量导入单行落库成功 | 新增或更新（以导入策略为准） | 逐条 `ActivityDefinitionCreatedEvent` 或 `ActivityDefinitionUpdatedEvent` |
| 任意 | 医保对码结果回写 | 业务字段变更（以对码承载字段为准） | `ActivityDefinitionUpdatedEvent` |

## 6. 领域服务

### 6.1 写侧领域服务（ActivityDefinitionDomainService）

#### 6.1.1 新建诊疗项目（`createActivityDefinition`）

- **业务意义**：在目录中创建一条诊疗项目主数据，生成稳定可引用的项目标识与业务编码。
- **边界说明**：仅维护 ActivityDefinition 聚合内字段与子项口径；与 `ChargeItemDefinition` 的定价行绑定、医保网关在途对码等由**应用服务编排**，不在本方法内写收费域表。
- **入参**：`categoryCode`、`name`、`typeEnum`、`permittedUnitCode`（及实现约定的其他创建字段）。
- **出参**：项目标识（`ActivityDefinitionId` / 主键）。
- **前置条件**：名称非空；`busNo` 在系统范围内唯一。
- **状态变化**：按初始化策略进入草稿或已发布；当创建入参未显式提供 `statusEnum` 时，默认置为草稿（`PublicationStatus.DRAFT` 的 `code`，落库如 `DRAFT`）。
- **交互约束**：维护端新增/编辑表单不直接暴露 `statusEnum`；状态迁移通过“启用/停用”独立动作完成。
- **应用编排（诊疗目录维护）**：`ServiceCatalogAppService#createServiceCatalog` 在领域创建并写入定价行后，**同请求内**调用 `startServiceCatalog`（委托 `activateActivityDefinitions`），将新建项发布为 `ACTIVE`，供门诊/住院医生站医嘱开立目录检索（见 **BR-ACTDEF-QRY-004**）。
- **聚合内影响**：落主表；同步生成检索码（拼音/五笔）；可按策略写入 `children_json` 或子项表。
- **事件输出**：`ActivityDefinitionCreatedEvent`（发布前领域层 **`flush()`** 主表，供领域事件后查询刷新同窗回源）；维护端创建路径在发布成功后另触发 `ActivityDefinitionActivatedEvent`。
- **失败语义**：编码冲突、必填缺失、分类或类型非法时拒绝创建。

#### 6.1.2 维护诊疗项目（`updateActivityDefinition`）

- **业务意义**：修订已存在项目的目录属性与子项，保持检索与展示一致。
- **边界说明**：仅更新本聚合内数据（含 `wkf_activity_definition_children` 子项行维护）；不级联修改 ServiceRequest 等下游执行态；关联 `ChargeItemDefinition` 定价行同步由应用服务编排。
- **入参**：项目标识、`id` 及可编辑字段集合。
- **出参**：—。
- **前置条件**：目标记录存在且允许编辑（见读侧 `validateActivityEdit` 门控）。
- **状态变化**：`N/A`（除非入参显式包含状态迁移，以落库与交互契约为准）。
- **聚合内影响**：名称变更时重算 `py_str`/`wb_str`；同步维护子项（JSON 或子表）。
- **事件输出**：`ActivityDefinitionUpdatedEvent`；若本次变更触及 §9.6 所述目录展示价或计价展示相关字段，还须按产品约定另行发布 `ActivityDefinitionPriceUpdatedEvent`（可与更新事件同事务，以实现侧拆分策略为准）。
- **失败语义**：记录不存在、并发冲突或子项不一致时拒绝提交。

#### 6.1.3 停用（`retireActivityDefinitions`）

- **业务意义**：将一批项目从可引用态切换为停用，阻断新开立引用。
- **边界说明**：仅变更发布状态相关字段，不删除主数据行、不改业务编码。
- **入参**：`idList`。
- **出参**：—。
- **前置条件**：入参列表非空；目标当前为允许停用状态。
- **状态变化**：`active -> retired`（或等价枚举迁移）。
- **聚合内影响**：批量更新状态字段。
- **事件输出**：`ActivityDefinitionRetiredEvent`。
- **失败语义**：包含不可停用项时整批失败或部分失败（以实现约定为准，须在接口契约中一致）。

#### 6.1.4 启用（`activateActivityDefinitions`）

- **业务意义**：维护端「启用」统一入口——**草稿发布**（临床可引用）与**停用恢复**。
- **边界说明**：不自动修复医保对码等外部状态；不修改业务编码与其他主数据（**BR-ACTDEF-004**）。
- **入参**：`idList`。
- **出参**：—。
- **前置条件**：`idList` 非空；每条记录存在且未软删。
- **状态变化**（聚合根方法）：
  - `DRAFT` → 调用 `publish()` → `ACTIVE`；
  - `RETIRED` → 调用 `activate()` → `ACTIVE`；
  - `ACTIVE` → 幂等跳过（不报错）；
  - 其他状态 → 拒绝。
- **聚合内影响**：批量更新 `status_enum`。
- **事件输出**：`ActivityDefinitionActivatedEvent`（载荷含 `activityDefinitionIdList`、`targetStatus=ACTIVE`）。
- **失败语义**：无效主键、非法状态、空列表时拒绝执行。

#### 6.1.5 批量导入诊疗目录（`importActivityCatalog`）

- **业务意义**：通过结构化导入包批量新增或更新诊疗目录，支撑建库与批量变更。
- **边界说明**：仅校验并落库本聚合主表与子表（及允许的 `children_json` 同步策略）；与收费定价行批量创建/更新、事务补偿策略由应用服务编排；不替代医保网关对码。
- **入参**：导入文件或解析后的行集合、导入口径（新增/更新/跳过冲突等，以落库与交互契约为准）。
- **出参**：导入结果摘要（成功条数、失败行号与原因列表等，以落库与交互契约为准）。
- **前置条件**：文件格式合法；单行满足 BR-ACTDEF-001/002；库内/文件内重复策略已选定。
- **状态变化**：按行可能为新建初始状态或保持原状态；不以本动作批量改启停语义（启停用 6.1.3/6.1.4）。
- **聚合内影响**：批量写入或更新 `wkf_activity_definition`；必要时维护 `wkf_activity_definition_children` 与 `children_flag`（BR-ACTDEF-006）。
- **事件输出**：对每条成功落库行发布 `ActivityDefinitionCreatedEvent` 或 `ActivityDefinitionUpdatedEvent`（以落库与交互契约为准）。
- **失败语义**：校验失败行按策略整批回滚或跳过并汇总；与收费域联动失败时按应用层事务/补偿约定处理。

#### 6.1.6 同步医保对码回写（`syncActivityYbMapping`）

- **业务意义**：接收医保对码结果或触发本侧对码状态更新，回写目录承载字段（如对码标记、医保编码、等级等）。
- **边界说明**：仅更新本聚合可读写的医保承载字段；对码规则、网关报文交互属医保上下文，不在此方法内实现。
- **入参**：项目标识（或批量列表）、对码结果字段集合（以落库与交互契约为准）。
- **出参**：— 或 更新条数摘要。
- **前置条件**：目标目录行存在；回写字段在合同允许集合内。
- **状态变化**：`N/A`（不改变 `PublicationStatus`，除非产品显式约定）。
- **聚合内影响**：更新 `wkf_activity_definition` 上 `yb_no`/`yb_match_flag`/`chrgitm_lv` 等约定字段。
- **事件输出**：`ActivityDefinitionUpdatedEvent`（修改字段摘要须包含对码相关变更，便于投影/缓存）。
- **失败语义**：目标不存在、字段非法、网关拒绝时拒绝或标记失败状态（以落库与交互契约为准）。
- **跨模块消费**：`zxplat-module-chs-jilin` · `InsuranceCatalogMappingAppService` 在 3301 成功后调用。

### 6.2 读侧查询服务（ActivityDefinitionQueryService）

> 查询类动作无写库，以"查询范围（读库，无写入）+ 组装规则"表达QueryService；用于维护页初始化、列表、详情、检索与开单门控。

#### 6.2.1 页面初始化字典查询（`getDiseaseTreatmentInit`）

- **业务意义**：为诊疗项目维护页加载字典与基础选项，支撑表单渲染。
- **边界说明**：只读查询，不写库，不做跨域编排。
- **查询范围（读库，无写入）**：字典/码表及维护页所需初始化数据集（以落库与交互契约为准），含病案结算类型术语 `mr_settlement_type_code` → `mrSettlementTypeCodeOptions`。
- **过滤口径**：仅返回当前租户/机构可见的字典视图。
- **组装规则**：按前端约定结构分组返回；`typeEnumOptions` 来自术语 `activity_type_code`（**BR-ACTDEF-010**），含病案结算类型术语 `mr_settlement_type_code` → `mrSettlementTypeCodeOptions`。
- **返回约束**：不修改任何业务主表；缺失字典项时返回空集合或明确错误码（以落库与交互契约为准）。

#### 6.2.2 分页查询目录（`pageServiceCatalog`）

- **业务意义**：按筛选条件分页返回诊疗项目目录列表。
- **边界说明**：只读查询，不写库，不做跨域编排。
- **查询范围（读库，无写入）**：`wkf_activity_definition` 主表；**可优先**走统一产品目录领域事件后查询刷新（如 `product_table_name = wkf_activity_definition`），与 BR-ACTDEF-QRY-001 一致。
- **过滤口径**：支持分类 `categoryCode`、业务类型 `typeEnum`（术语 `activity_type_code`）、关键字、可选 `statusEnum`（`ACTIVE`/`INACTIVE`）等组合条件；默认排除逻辑删除（若存在）。
- **组装规则**：列表行字段与排序规则与 UI 合同一致；**须含**销售/计价单位 `packageUnitCode`（主表 `package_unit_code`），应用层补字典文案 `packageUnitCode_dictText` 供维护页「单位」列展示（**禁止**用 `defEncounterUnitCode` 充当列表单位）；QueryService 字段与主表不一致时以实现映射为准；不在读侧回写。
- **返回约束**：分页元数据完整；大数据量时遵守查询上限策略。

#### 6.2.3 查询目录详情（`getServiceCatalog`）

- **业务意义**：按标识加载单项详情与扩展信息，用于编辑回显与详情弹窗。
- **边界说明**：只读查询，不写库，不做跨域编排；与收费定价事实的组装为**QueryService拼接**，不改变 §9.6 计费事实源归属。
- **查询范围（读库，无写入）**：`wkf_activity_definition`；子项按 `activity_id` 加载 `wkf_activity_definition_children`（及/或 `children_json`，以产品约定为准）；定价展示可按 BR-ACTDEF-QRY-002 合并 `ChargeItemDefinition` 读数据。
- **过滤口径**：按主键精确匹配；排除已删除主表记录；子项行默认排除逻辑删除（BR-ACTDEF-QRY-003）。
- **组装规则**：主表 + 子项列表 +（可选）收费展示字段合并为单一详情 DTO；价格语义遵循 §9.6。
- **返回约束**：不存在时返回空或抛业务异常（以实现约定为准，须前后端一致）。

#### 6.2.4 关键字检索（`findBySearchKey`）

- **业务意义**：按名称、业务编码、拼音、五笔等关键字提供联想与快速定位。
- **边界说明**：只读查询，不写库，不做跨域编排。
- **查询范围（读库，无写入）**：主表检索字段索引扫描。
- **过滤口径**：关键字长度与通配策略以落库与交互契约为准；可限制仅返回已发布项。
- **组装规则**：命中列表按相关度或编码排序截断返回。
- **返回约束**：条数上限与脱敏规则与调用场景一致。

#### 6.2.5 查询启用列表（`diseaseTreatmentList`）

- **业务意义**：为开单场景返回当前启用且可用的项目集合。
- **边界说明**：只读查询，不写库，不做跨域编排。
- **查询范围（读库，无写入）**：主表有效发布状态子集。
- **过滤口径**：启用 + 未删除 + （可选）机构/科室适用范围。
- **组装规则**：轻量列表字段，避免携带大 JSON。
- **返回约束**：保证与开立入口性能约定一致（缓存/分页以落库与交互契约为准）。

#### 6.2.6 编辑前可编辑性校验（`validateActivityEdit`）

- **业务意义**：领域层兜底：记录存在、未软删、非 INACTIVE。
- **应用层完整口径**：`ServiceCatalogAppService#validateServiceCatalogEdit` → `CatalogEditabilityResolver#resolveActivityEditability`；存在**任一**有效 `ServiceRequest`（本项 `activity_id`）时返回 `LOCK_CLINICAL`；**另**：本项作为套餐子项时，任一未删父项存在有效 `ServiceRequest` 亦返回 `LOCK_CLINICAL`（父开立≡子开立，**AS-BAS-CAT-001 BR-CAT-014** / **BR-ACTDEF-QRY-005**）。写侧锁定：诊疗名称、目录分类、计价单位、财务/医保类别、售价；**业务类型 `typeEnum` 不锁**（**BR-CAT-015**）；医保等级与病案结算类型仍可改；拼音/说明等仍可改；**无库存侧码2**。创建/更新：业务类型**必填**（**BR-ACTDEF-011**）；财务类型 `itemTypeCode` 维护页必填，后端有售价/进价时 `assertItemTypeCodeWhenPricingPresent`（**AS-BAS-CAT-001 BR-CAT-016**）。
- **写侧**：`CatalogEditWriteGate#assertServiceCatalogUpdateAllowed`；锁定态跳过 `replacePriceLines` 避免误动维护态定价行。
- **联调**：**AS-BAS-CAT-001** §1.4 / §3；前端 `servicecatalog/edit-dialog.vue` + `catalog-editability.ts`（**AS-BAS-CAT-001-SDD** §5）。

#### 6.2.6a 按子项反查父套餐 id（`listParentActivityIdsByChildrenId`）

- **业务意义**：目录编辑门禁判定「父开立是否锁子项」时，按 `children_id` 反查未删父 `activity_id`（**BR-ACTDEF-QRY-005** / **BR-CAT-014**）。
- **边界说明**：只读；不写库；不做跨域编排；开立引用判定仍由应用层经 `ServiceRequestQueryService#existsByActivityIdIn` 完成。
- **查询范围（读库，无写入）**：`wkf_activity_definition_children` 按 `children_id`。
- **过滤口径**：排除 `is_deleted=true`；父 `activity_id` 空跳过；同子项多父去重保序。
- **组装规则**：返回 `List<Long>` 父主键；无引用时空列表。
- **返回约束**：`childrenId` 为空时返回空列表。

#### 6.2.7 下载导入模板（`downloadActivityCatalogTemplate`）

- **业务意义**：提供批量导入用模板文件或模板结构定义，降低批量维护错误率。
- **边界说明**：只读查询，不写库，不做跨域编排；可不访问业务主表，仅返回模板元数据/空表结构。
- **查询范围（读库，无写入）**：模板资源存储或配置中心（以落库与交互契约为准）。
- **过滤口径**：按租户/产品版本选择模板版本。
- **组装规则**：返回文件流或模板字段说明结构。
- **返回约束**：模板格式与 `importActivityCatalog` 入参契约一致。

#### 6.2.8 导出诊疗目录（`exportActivityCatalog`）

- **业务意义**：按筛选条件导出目录数据，用于对账、迁移或离线编辑。
- **边界说明**：只读查询，不写库，不做跨域编排。
- **查询范围（读库，无写入）**：与列表同源（主表或产品领域事件后查询刷新，见 BR-ACTDEF-QRY-001）。
- **过滤口径**：与 `pageServiceCatalog` 等价筛选条件子集或扩展导出字段集（以落库与交互契约为准）。
- **组装规则**：导出列顺序与编码与导入模板或约定报表一致。
- **返回约束**：大数据量时分片/异步导出策略以落库与交互契约为准。

#### 6.2.9 查询医保对码状态（`getActivityYbMappingStatus`）

- **业务意义**：为维护页展示指定目录项的医保对码状态摘要（已对接/待对码/失败原因等）。
- **边界说明**：只读查询，不写库，不做跨域编排；不发起网关实时对码（对码写回用 6.1.6）。
- **查询范围（读库，无写入）**：`wkf_activity_definition` 上对码承载字段；若存在独立对码状态表则关联只读查询（以落库与交互契约为准）。
- **过滤口径**：按项目标识；排除无权限租户数据。
- **组装规则**：聚合为状态枚举或结构化状态 DTO（以落库与交互契约为准）。
- **返回约束**：不存在时返回空状态或明确错误码。

#### 6.2.10 按父项加载套餐子项（`listChildrenByActivityId`）

- **业务意义**：为护士划价选品（**AS-IP-NUR-005** IF-IP-PRC-01）展示 `packageChildren`，以及 **`ActivityDefinitionPackageChildResolver`** / 医保 **`ChsActivityPackageFeeExpandSupport`** 用时解析子项（**BR-IP-PRC-011**）；**优先**子表事实行，**不以** `children_json` 为唯一真源。
- **边界说明**：只读查询，不写库，不做跨域编排。
- **查询范围（读库，无写入）**：`wkf_activity_definition_children` 按 `activity_id`。
- **过滤口径**：`activity_id =` 入参；默认排除 `is_deleted=true`（与 **BR-ACTDEF-QRY-003** 一致）。
- **组装规则**：映射为 `ActivityDefinitionChildQueryModel`（`childrenId`、`childrenNum`、`childrenName` 等）；无子项返回空列表。
- **返回约束**：`activityId` 为空时返回空列表。
- **展开消费口径**（不在本方法内执行）：有子项时对外**只传子项**；**只认**账单子行冻价（`parent_id`）；目录有子项但无子项账单时**拒绝**（**禁止**回落 SALE）；数量取子项账单已冻数量；**禁止**分摊父项；各子项金额之和须等于父 `totalPrice`。与医嘱组套（OrderTemplate）无关。

#### 6.2.11 批量加载套餐子项（`listChildrenByActivityIds`）

- **业务意义**：选品目录当前页批量预热子项，避免 N+1。
- **边界说明**：只读；内部可委托 `ActivityDefinitionChildRepository#findByActivityIdIn`。
- **查询范围（读库，无写入）**：多父项 `activity_id IN (...)` 之子表行。
- **过滤口径**：同 **6.2.10**；空集合返回空 `Map`。
- **组装规则**：`Map<Long, List<ActivityDefinitionChildQueryModel>>`，key 为父 `activity_id`。
- **返回约束**：未命中父 id 的 key 可不出现或映射为空列表（以实现 `getOrDefault` 为准）。

#### 6.2.12 医嘱开立目录分页（`pageForCatalog`）

- **业务意义**：门诊/住院医生站 IF-DOR-11 / IF-IP-DOR-11 等产品定义混页中的**诊疗分支**（`wkf_activity_definition`），由应用层 `ProductDefinitionCatalogPageLoader` 编排调用（**非** ProductDefinition RM）。
- **边界说明**：只读；条件下推至数据库分页，禁止先全量拉取再内存过滤导致 `total` 失真。
- **查询范围（读库，无写入）**：`wkf_activity_definition` 主表。
- **过滤口径**：`is_deleted = false`；**`status_enum = PublicationStatus.ACTIVE` 的 `code`（落库 `ACTIVE`）**；可选 `category_code`、可选 `type_enum`（申请单分类 chips，与目录分类独立）、关键字（名称/商品名/拼音/五笔/业务编码，不区分大小写）、`prepickedProductKeys` 与关键字 OR 组合（**BR-ACTDEF-QRY-004**）。
- **组装规则**：映射为 `ProductDefinitionDisplayDto`（应用层），术语展示列由 `ProductDefinitionSelectionTerminologysupport 回填。
- **返回约束**：草稿/停用项**不得**出现在本查询结果中（与 §8.3 临床引用规则一致）。

#### 6.2.13 按主键批量加载（`findAllByIds`）

- **业务意义**：医嘱列表预热、批量展示名解析等场景避免 N+1。
- **边界说明**：只读；返回主表实体列表（非 QueryModel）。
- **入参**：诊疗项目主键集合；空集合返回空列表。
- **出参**：`List<ActivityDefinition>`。

#### 6.2.14 按业务编码批量加载（`findByBusNos`）

- **业务意义**：外部目录同步、导入后按 `bus_no` 反查实例主键。
- **边界说明**：只读；`findByBusNoIn` 下推。
- **入参**：业务编码列表；空列表返回空列表。
- **出参**：`List<ActivityDefinition>`。

### 6.3 领域边界说明

- 诊疗项目定义只负责目录主数据的生命周期与编码检索，不承载患者级执行记录。
- `ServiceRequest`、`ChargeItem`、临床路径等业务均通过 `项目标识(ActivityDefinitionId)` 只读引用，不在本聚合内维护其状态。
- 关键字检索（`findBySearchKey`）为读侧能力，由 `ActivityDefinitionQueryService` 承载；应用层价格初始化、分组套餐展示属于编排能力，不记入领域服务。
- 新建/维护目录与 `ChargeItemDefinition` 定价行绑定、批量导入与收费的**跨聚合事务**由应用服务编排；领域方法只承诺本聚合落库与事件口径。
- 批量导入模板下载、目录导出、对码状态查询为读侧或静态资源能力，**不得**隐式写库。

## 7. 不变式


| 编号            | 不变式                                | 说明                       |
| ------------- | ---------------------------------- | ------------------------ |
| BR-ACTDEF-001 | 创建诊疗项目必须提供 `category_code` 与 `name` | 创建前置校验                   |
| BR-ACTDEF-002 | `bus_no` 作为稳定业务编码且必须唯一              | 重复编码禁止创建                 |
| BR-ACTDEF-003 | 名称变更必须同步更新 `py_str`/`wb_str`           | 保证检索一致性                  |
| BR-ACTDEF-004 | 批量启停仅改变发布状态                        | `retire` 仅 `ACTIVE→RETIRED`；`activateActivityDefinitions` 对 `DRAFT` 调 `publish`、`RETIRED` 调 `activate`；已 `ACTIVE` 幂等跳过；不改业务编码与其他主数据 |
| BR-ACTDEF-005 | 子项数据按父项聚合维护                        | 子表 `wkf_activity_definition_children` 以 `activity_id` 为边界；与 `children_json` 并存时须约定单一事实源或同步策略 |
| BR-ACTDEF-006 | 子项标记与子项内容一致                        | `children_flag` 与子表行集合一致，防止有标无子或子行已空仍标记含子项 |
| BR-ACTDEF-007 | 同一父项下子项引用不重复                       | 同一 `activity_id` 下 `children_id` 不得重复；遇重复时覆盖、累加数量或拒绝须为固定产品策略 |
| BR-ACTDEF-008 | 子项删除语义与列表默认口径                      | 子项行删除以落库与交互契约为准（逻辑删除优先）；列表/详情默认过滤已删除子项行 |
| BR-ACTDEF-009 | 创建/导入创建时初始状态可省略                    | `statusEnum` 为空时按默认策略落为 `draft`，避免因空状态导致创建失败 |
| BR-ACTDEF-010 | 诊疗业务类型选项口径统一                         | `type_enum` 与初始化下拉 `typeEnumOptions` 均由术语 `activity_type_code` 生成；**禁止** `ActivityType.values()` / 硬编码码表 / 等价常量类生成下拉；现场增码后刷缓存即可见 |
| BR-ACTDEF-011 | 诊疗业务类型必填                               | 创建/更新 IF 与维护页（含 PEIS 项目/套餐）**强制** `type_enum` 非空；前端 required；领域 `assertRequiredFieldsProvided` 拒空。财务类型见 **AS-BAS-CAT-001 BR-CAT-016** |
| BR-ACTDEF-QRY-001 | 列表读路径直读 `wkf_activity_definition` 主表               | 由 `ActivityDefinitionQueryService` 承载；混页由应用 `ProductDefinitionCatalogPageLoader` 按表路由 |
| BR-ACTDEF-QRY-002 | 详情读组装可合并收费定价读数据                   | 展示价与费用类别等与 `ChargeItemDefinition` 组装时，计费事实仍以 §9.6 为准 |
| BR-ACTDEF-QRY-003 | 子项列表按父项加载并默认排除逻辑删除               | 按 `activity_id` 加载 `wkf_activity_definition_children`，默认不含已逻辑删除行 |
| BR-ACTDEF-QRY-004 | 医嘱开立目录分页仅已发布项                      | `pageForCatalog` 谓词固定 `status_enum = ACTIVE`；草稿/停用不可被医生站目录检索 |
| BR-ACTDEF-QRY-005 | 子项反查父套餐供编辑门禁                     | `listParentActivityIdsByChildrenId`：按 `children_id` 反查未删父 `activity_id`；供 **AS-BAS-CAT-001 BR-CAT-014**（父开立≡子开立） |

## 8. 上下文映射

### 8.1 上游依赖


| 上游上下文 | 聚合             | 关联方式                         | 说明              |
| ----- | -------------- | ---------------------------- | --------------- |
| 组织架构  | Department     | `科室标识(DepartmentId)`         | 适用科室关联          |
| 组织架构  | Organization   | `机构标识(OrganizationId)`       | 机构维度目录可见性（可选）   |
| 位置服务  | Location       | `位置标识(LocationId)`           | 执行地点字典（可选）      |
| 术语体系  | 术语/码表（边界外）     | 编码引用                         | 分类、单位、标本、部位等码表解析 |

### 8.2 下游影响


| 下游上下文 | 聚合             | 触发方式 | 说明                                                |
| ----- | -------------- | ---- | ------------------------------------------------- |
| 产品目录混页 | `ActivityDefinitionQueryService` | 应用编排 | `pageForCatalog` / `findBySearchKey` 直读 `wkf_activity_definition`（见 **BR-ACTDEF-QRY-001/004**） |
| 服务申请  | ServiceRequest | 引用   | ServiceRequest 创建时引用 ActivityDefinitionId 确定具体服务  |
| 收费定价定义 | ChargeItemDefinition | 应用层绑定/引用 | 定价行与目录主键关联；事实价与展示价语义见 §9.6 |
| 收费管理  | ChargeItem     | 事件订阅 | 监听 `ActivityDefinitionPriceUpdatedEvent`，更新收费标准缓存 |
| 临床路径  | PlanDefinition | 引用   | 临床路径步骤引用 ActivityDefinitionId                     |
| 医嘱模板  | OrderTemplate  | 引用   | 医嘱模板中的检查/检验/治疗项目引用                                |
| 医保结算  | Claim          | 引用   | 医保申报时根据医保目录分类进行报销计算                               |


### 8.3 引用规则

> **统一规则**：
>
> - 所有引用诊疗项目定义的聚合，统一使用 `项目标识(ActivityDefinitionId)` 引用
> - 引用时须校验目标项目状态为已发布（`status_enum = PublicationStatus.ACTIVE` 的 `code`，落库 `ACTIVE`；见 §2.3、§4.3、**BR-ACTDEF-QRY-004**）
> - **价格引用口径**：诊间展示、检索排序可使用 `ActivityDefinition.standardPrice`（目录展示价，见 §9.6）；**开立与结算所用计费事实价**必须以 `ChargeItemDefinition` 定价行为准，不得以本聚合展示字段替代事实源

### 8.4 事件流示意图

```
ActivityDefinition          ServiceRequest          ChargeItem           Claim
       │                         │                      │                  │
       │── ActivityDefinitionCreatedEvent ──────>│ (可引用开立)           │                  │
       │── ActivityDefinitionActivatedEvent ────>│ (恢复可引用)           │                  │
       │                         │                      │                  │
       │── ActivityDefinitionPriceUpdatedEvent ───────────────────────>│ (刷新收费标准)     │
       │                                                 │                  │
       │── ActivityDefinitionRetiredEvent ──────>│ (不再可引用)           │                  │
       │                         │                      │                  │
       │ (医保目录分类) ──────────────────────────────────────────────────>│ (报销计算)
```

## 9. 补充

### 9.1 与 ChargeItem 的关系

**新开立** ChargeItem 的计费事实价以 `ChargeItemDefinition` 为准（§9.6）；`ActivityDefinition.standardPrice` 仅用于诊间展示与缓存刷新参考。目录展示价变更时：

1. 发布 `ActivityDefinitionPriceUpdatedEvent` 事件（见 §5.1.5）
2. ChargeItem 所在上下文更新本地收费标准缓存（展示/门控口径，以落库与交互契约为准）
3. **已生成的 ChargeItem 不受影响**（按开立时已固化的计费事实计费）
4. **新开立的 ServiceRequest 产生的 ChargeItem** 使用 `ChargeItemDefinition` 侧**当前生效**的定价事实（若 SALE 行已随联动更新，则自然体现为新价；不得以目录展示价字段替代事实源）

### 9.2 与药品目录的边界


| 维度   | ActivityDefinition（诊疗项目） | 药品目录（Formulary） |
| ---- | ------------------------ | --------------- |
| 管理对象 | 服务、操作、检查、检验等             | 药品（含制剂、规格）      |
| 计费方式 | 按次/项/日计价                 | 按用量×单价计价        |
| 医保分类 | 甲/乙/丙类                   | 甲/乙类目录          |
| 关联方式 | 通过"配套医嘱模板"关联药品           | 独立管理            |


### 9.3 多版本价格管理

同一诊疗项目在不同时期可能有不同价格。价格管理遵循以下规则：

1. 价格变更时**不删除旧价格**，而是设置失效日期
2. 同一时刻同一价格类型只有一条生效记录
3. 历史价格保留用于费用查询和审计
4. 集采价/协议价通过"价格类型"区分，不影响标准价

### 9.4 与 ChargeItemDefinition 的价格关系


| 维度   | ActivityDefinition.standardPrice             | ChargeItemDefinition     |
| ---- | -------------------------------------------- | ------------------------ |
| 定位   | **目录展示价**（对应门诊诊间一屏展示的售价）                    | **计费事实价**（对应实际结算时使用的定价行） |
| 数据关系 | 是 ChargeItemDefinition 中 SALE 类型定价行的**快照冗余** | 唯一事实源，计费时以此为准            |
| 用途   | 仅用于临床界面展示与检索排序                               | 用于实际收费结算、医保报销计算          |


**变更传播规则**：

1. `ActivityDefinitionPriceUpdatedEvent` 触发后，ChargeItemDefinition 侧需评估是否联动更新对应 SALE 定价行
2. 两者价格不要求实时强一致——ActivityDefinition.standardPrice 允许短暂滞后
3. **唯一事实源**：计费时以 ChargeItemDefinition 为准，ActivityDefinition.standardPrice 仅用于展示

### 9.6 字段可编辑矩阵（维护页）

| 业务码 | 触发条件 |
|---|---|
| `OK` | 无本项有效 `ServiceRequest`，且（若为套餐子项）无任一父项有效 `ServiceRequest` |
| `LOCK_CLINICAL` | 本项存在**任一**未删 `ServiceRequest`；**或**本项作为套餐子项时任一未删父项存在 `ServiceRequest`（**BR-CAT-014**；**无** `LOCK_INVENTORY`） |

| 字段 | OK | LOCK_CLINICAL |
|---|---|---|
| 诊疗名称 | 可编辑 | **锁定** |
| 目录分类 / 计价单位 / 财务类别 / 医保类别 | 可编辑 | **锁定** |
| 业务类型 typeEnum | 可编辑 | 可编辑 |
| 售价 | 可编辑 | **锁定** |
| 病案结算类型 mrSettlementTypeCode | 可编辑 | 可编辑 |
| 医保等级 chrgitmLv | 可编辑 | 可编辑 |
| 拼音/五笔 / 说明 / 子项 / 最高零售价等 | 可编辑 | 可编辑 |

应用层：`validateServiceCatalogEdit`；详情回填 `editabilityCode`+`editabilityMessage`。详见 **AS-BAS-CAT-001**。

### 9.5 文档变更历史

| 版本 | 日期 | 摘要 |
| ---- | ---- | ---- |
| v1.0.21 | 2026-10-08 | **代码先行回写**：§6.2.2 列表组装明确含 `packageUnitCode`（销售/计价单位）及 dictText；维护页「单位」列对齐；禁止用就诊单位冒充列表单位 |
| v1.0.20 | 2026-10-07 | **代码先行回写**：`type_enum` 维护页/PEIS **必填**（**BR-ACTDEF-011**）；删除 `ActivityType`；选项仅术语；通道仍认 `category_code`；禁止硬编码兜底 |
| v1.0.19 | 2026-10-06 | **代码先行回写**：`type_enum` 维护页/IF **选填**（**BR-ACTDEF-011**）；财务类型维护页必填见 **BR-CAT-016**；§6.2.6 锁定清单去掉业务类型 |
| v1.0.18 | 2026-10-06 | **代码先行回写**：§9.6 锁定态 **业务类型可改**（目录分类仍锁）；对齐 **AS-BAS-CAT-001 BR-CAT-015** |
| v1.0.17 | 2026-10-06 | **代码先行回写**：§4.2 `type_enum` 改术语 `activity_type_code`（种子码 1~7 原值）；**BR-ACTDEF-010**；`pageForCatalog`/`pageServiceCatalog` 可按 `typeEnum` 过滤 |
| v1.0.16 | 2026-10-06 | **代码先行回写**：§6.2.10 结算/医保展开禁 SALE 回落，只认子项账单 |
| v1.0.14 | 2026-09-10 | §9.6：病案结算类型锁定态改为可编辑（对齐 AS-BAS-CAT-001 BR-CAT-008） |
| v1.0.13 | 2026-07-28 | §9.6 / §6.2.6：诊疗价格文案改为售价（无进价/采购参考价） |
| v1.0.12 | 2026-07-24 | §3.2 `children_num` / §6.2.10：套餐展开口径（只传子项、SALE 计价、数量=父×系数）；对齐 **BR-IP-PRC-011** / 2204·2301 |
| v1.0.11 | 2026-07-17 | §9.6 / §6.2.6：名称锁定；医保等级可改；价格文案改为采购参考价/销售参考价 |
| v1.0.10 | 2026-07-16 | **纠正**：嘱托改挂 **`category_code=52`**（撤回误增的 `ActivityType.ENTRUST`）；对齐 **DM-WKF-004** / **AS-IP-DOR-003 BR-IP-ORD-028** |
| v1.0.9 | 2026-07-16 | ~~§4.2 增补 ENTRUST~~（已被 v1.0.10 撤回） |
| v1.0.8 | 2026-07-14 | §3.1 / §6.2.1 / §9.6 增补病案结算类型 `mr_settlement_type_code`（三大目录共用术语） |
| v1.0.7 | 2026-07-11 | §6.1.6 增补 CHS 跨模块消费（`zxplat-module-chs-jilin` · `InsuranceCatalogMappingAppService`） |
| v1.0.6 | 2026-07-04 | **§9.6** 维护页字段可编辑矩阵；§6.2.6 补充 AS-BAS-CAT-001 联调引用 |
| v1.0.5 | 2026-05-28 | **M1**：§6.2.13 `findAllByIds`、§6.2.14 `findByBusNos` 对齐 Java |
| v1.0.4 | 2026-05-28 | ReadModel 废止：删除 **ProductDefinition** RM / QueryModel 消费表述；§6.2.12 / §8.2 改 **`ActivityDefinitionQueryService`** + 应用 `ProductDefinitionCatalogPageLoader` |
| v1.0.3 | 2026-05-28 | §6.1.4 **`publish()`** + 启用分流（`DRAFT→ACTIVE` / `RETIRED→ACTIVE`）；§6.2.12 **`pageForCatalog` 仅 ACTIVE**；维护端 **`createServiceCatalog` 保存后自动发布**；**BR-ACTDEF-QRY-004** |
| v1.0.2 | 2026-05-24 | 创建/更新/导入发布事件前 **`flush()`** 主表 |
| v1.0.1 | 2026-05-24 | §6.2.10～6.2.11 **`listChildrenByActivityId` / `listChildrenByActivityIds`**（护士划价套餐，**AS-IP-NUR-005**） |

<!-- MODE-B-METHOD-INVENTORY 2026-09-23 -->

## 附录 · 模式 B 已实现方法清单（2026-09-23）

> 自 zxplat-domain 对应 *DomainService / *QueryService 抽取 public 方法名，用于关闭方法白名单 DOC-DRIFT。细规仍以正文 §6 与源码为准；本表不替代九章成稿。

### DomainService：`ActivityDefinitionDomainService`

| 方法 |
|------|
| `createActivityDefinition` |
| `updateActivityDefinition` |
| `retireActivityDefinitions` |
| `deleteActivityDefinitions` |
| `activateActivityDefinitions` |
| `importActivityCatalog` |
| `syncActivityYbMapping` |

### QueryService：`ActivityDefinitionQueryService`

| 方法 |
|------|
| `getDiseaseTreatmentInit` |
| `pageServiceCatalog` |
| `getServiceCatalog` |
| `findOptionalActivityDefinitionEntityById` |
| `requireIdByExactName` |
| `findBySearchKey` |
| `diseaseTreatmentList` |
| `validateActivityEdit` |
| `downloadActivityCatalogTemplate` |
| `exportActivityCatalog` |
| `listServiceCatalogForExport` |
| `getActivityYbMappingStatus` |
| `findAllByIds` |
| `pageForCatalog` |
| `findByBusNos` |
| `listChildrenByActivityId` |
| `listChildrenByActivityIds` |
| `listParentActivityIdsByChildrenId` |
| `findExistingDuplicateKeyTokens` |

