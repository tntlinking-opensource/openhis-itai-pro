# DM-CUS-006 ServiceLocationTemplate 取药科室 · DomainModel-SDD

> **对应 DM**：`docs/领域/DomainModel/DM-CUS-006-ServiceLocationTemplate-取药科室.md`（本 SDD 假定该 DM 为当期锁定版本）  
> **目标工程**：`zxplat-core/zxplat-domain`  
> **限界上下文**：CUS 本地扩展  
> **聚合**：ServiceLocationTemplate（子实体见 §4）

---

## 输入完整性检查结果

| 最小输入项 | 状态 | 说明 |
| ---------- | ---- | ---- |
| DM 路径 | 满足 | 已绑定 DM-CUS-006 |
| SDD 输出路径 | 满足 | 本文件 |
| 目标模块 | 满足 | `zxplat-domain`（DomainModel-SDD） |
| 包规则 `com.zxplat.domain.custom.servicelocationtemplate` | 满足 | `yyy` 全小写 |
| 固定技术口径（ID Long、时间 LocalDateTime、软删、beforeCommit、分页等） | 满足 | 见 §3、§9 |
| DM 第 6 章写/读白名单 | 满足 | §5 覆盖写方法 3 个、读方法 6 个（含 `listApplicable…` / IF-DOR-11） |
| DM 第 7 章 BR | 满足 | §6 映射 12 条并给出实现锚点 |
| DM 第 5 章事件 | 满足 | §7 覆盖 1 个事件 |

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

### 2.2 包结构（`com.zxplat.domain.custom.servicelocationtemplate`）

| 相对包 | 内容 |
| ------ | ---- |
| `entity` | 聚合根实体及子实体（若有） |
| `valueobject` | 值对象（若有） |
| `command` | 一方法一命令文件 |
| `service` | `*DomainService` 与 `*QueryService`（两个方法集，直接实现类） |
| `queries` | 查询入参对象（如 `*Query`） |
| `queries.model` | 查询出参模型（如 `*QueryModel`） |
| `repository` | `JpaRepository` 接口 |
| `share.events` | `com.zxplat.domain.share.events.custom.servicelocationtemplate` |

---

## 3. 类型 / 字段命名 / 注解规则

| 规则 | 说明 |
| ---- | ---- |
| 主键 / 外键 | `Long`；命名与 DM 字段一致 |
| 时间 | 业务字段按 DM；审计字段 `LocalDateTime` |
| `*_enum` | `Integer`（或 DM 明示类型） |
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
| `cus_service_location_template` | `ServiceLocationTemplate` | 与 `zxplat` 实体 `@Table(name = "cus_service_location_template")` 一致 |

### 4.2 聚合根 `ServiceLocationTemplate`（字段级契约）

| 字段名（Java） | 类型 | 必填 | 说明 / 约束 |
| -------------- | ---- | ---- | ----------- |
| `id` | ``Long`` | 是 | 主键，雪花 ID |
| `organizationId` | ``Long`` | 是 | 开立科室/组织标识（`organization_id`） |
| `itemTable` | ``String`` | 是 | 物品来源表名（`item_table`）；**仅** `med_medication_definition` / `adm_device_definition`（见 DM §4.1） |
| `distributionCategoryCode` | ``String`` | 是 | 模板 `distribution_category_code`；**须与产品目录 `categoryCode` 一致**（药品→`med_category_code`，耗材→`device_category_code`，按 `itemTable` 区分码表；**非**给药途径等其它维度） |
| `defLocationId` | ``Long`` | 是 | 默认发药库位（`def_location_id`） |
| `itemDefinitionId` | ``Long`` | 否 | 物品定义主键（`item_definition_id`）；空为通配 |
| `typeCode` | ``String`` | 否 | 耗材种类（`type_code` / `device_type_code`）；空为通配；药品通常空 |
| `methodCode` | ``String`` | 否 | 给药途径/用法（`method_code` 列）；空为通配；**中草药类别**须选自 `tcm_method_code`，**西药/中成药**须选自 `method_code` |
| `startTime` | ``LocalTime`` | 否 | 当日内生效开始（`start_time`）；空为全天起点 |
| `endTime` | ``LocalTime`` | 否 | 当日内生效结束（`end_time`）；空为全天终点 |
| `priority` | ``Integer`` | 是 | **数值越小越优先**（`priority`；与 `DM-CUS-001` 执行科室「数值越大越优先」相反，勿混用） |
| `displayOrder` | ``Integer`` | 否 | 列表展示序（`display_order`） |
| `remark` | ``String`` | 否 | 备注（`remark`） |

**不变式方法（示例命名，实现须一致）**：`validateRequiredFields`、`validateStatusTransition`、`assertOwnership`。

### 4.3 子实体

无。本聚合为单表。

### 4.5 仓储接口

- `ServiceLocationTemplateRepository extends JpaRepository<ServiceLocationTemplate, Long>`
- `findByIdAndIsDeletedFalse(Long id)`
- 其余查询签名按 §5.2 与 §6 不变式派生。

### 4.6 查询模型（`queries.model`）

#### 4.6.1 `ServiceLocationTemplateQueryModel`

**适用读方法**：`listDistributionRulesByOrganization`、`listAllDistributionRules`、`listEffectiveTemplateRowsForDispensing`（`ServiceLocationTemplateQueryService#toQueryModel` 映射，与聚合字段一一对应）。

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `id` | ``Long`` | `cus_service_location_template.id` | 规则主键 |
| `organizationId` | ``Long`` | `cus_service_location_template.organization_id` | 开立科室/组织 |
| `itemTable` | ``String`` | `cus_service_location_template.item_table` | 物品来源表名（药品/耗材表），见 §4.1 |
| `distributionCategoryCode` | ``String`` | `cus_service_location_template.distribution_category_code` | 发放类别编码 |
| `defLocationId` | ``Long`` | `cus_service_location_template.def_location_id` | 默认发药库位 |
| `itemDefinitionId` | ``Long`` | `cus_service_location_template.item_definition_id` | 物品定义主键；空为通配 |
| `methodCode` | ``String`` | `cus_service_location_template.method_code` | 给药途径等；空为通配 |
| `startTime` | ``LocalTime`` | `cus_service_location_template.start_time` | 当日内生效起点；空为全天起点语义 |
| `endTime` | ``LocalTime`` | `cus_service_location_template.end_time` | 当日内生效终点；空为全天终点语义 |
| `priority` | ``Integer`` | `cus_service_location_template.priority` | 匹配优先级，数值越小越优先 |
| `displayOrder` | ``Integer`` | `cus_service_location_template.display_order` | 配置页展示序；可空 |
| `remark` | ``String`` | `cus_service_location_template.remark` | 备注；可空 |

#### 4.6.2 非 `QueryModel` 包装的读返回

| 读方法 | 返回类型 | 说明 |
| ------ | -------- | ---- |
| `listCandidateDistributionLocationIds` | `List<Long>` | 候选库房库位 id 列表（由命中模板行的 `def_location_id` 去重并保持排序） |
| `matchDefaultDistributionLocation` | `Long` | 单个默认库位 id；无命中返回 `null`（不包装为 DTO） |

---

## 5. 写侧与读侧白名单（命令字段全集）

### 5.1 写侧 — `ServiceLocationTemplateDomainService`

#### 5.1.1 `saveDistributionConfig(SaveDistributionConfigCommand cmd)` → `void`

**命令文件**：`SaveDistributionConfigCommand.java`（继承 `BatchCommand<DistributionRuleItemCommand>`）

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `organizationId` | `Long` | 开立科室标识，必填 |
| `operatorId` | `Long` | 操作人标识 |
| `items` | `List<DistributionRuleItemCommand>` | 本次提交的完整规则列表；空列表表示清空该科规则 |

**子项 `DistributionRuleItemCommand` 字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `itemTable` | `String` | `med_medication_definition` / `adm_device_definition` |
| `distributionCategoryCode` | `String` | 发放类别编码 |
| `defLocationId` | `Long` | 默认发药库位 |
| `itemDefinitionId` | `Long` | 具体项；可空 |
| `methodCode` | `String` | 用法；可空 |
| `startTime` | `LocalTime` | 生效开始；可空=全天 |
| `endTime` | `LocalTime` | 生效结束；可空=全天 |
| `priority` | `Integer` | 优先级；**同一匹配竞争组**内非空值须 1..N 连续（见 BR-SLT-005）；不同条件组各自从 1 起 |
| `displayOrder` | `Integer` | 展示序；可空 |
| `remark` | `String` | 备注；可空 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `assertRequiredFields` |
| `statusTransitionMethod` | `N/A` |
| `repositoryChecks` | 软删旧行 + `saveAll` 新行；`assertNoDuplicateKey`（匹配条件+时段+默认发药位置）；`assertPriorityContinuous` |
| `eventPublishAnchor` | `new ServiceLocationTemplateEditedEvent(...)` + `publishBeforeCommit` |
| `unitTestCaseId` | `TC-DM-CUS-006-W-001` |
| `transaction` | 由调用方 `ServiceLocationTemplateAppService` + 框架 `AppServiceAspect` 提供读写事务；领域方法**不**单独 `@Transactional` |

**方法契约摘要**：前置条件为 `organizationId`、各行必填与 BR-SLT-004/005/009；持久化为同一科室内先软删全部旧行再批量插入；BR-SLT-008 与码表归属由应用层 Gate。**注释锚点**：校验 → 软删旧行 → 批量插入 → 事件发布。

---

#### 5.1.2 `deleteDistributionRulesByOrganization(DeleteDistributionRulesByOrganizationCommand cmd)` → `void`

**命令文件**：`DeleteDistributionRulesByOrganizationCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `organizationId` | `Long` | 开立科室标识 |
| `operatorId` | `Long` | 操作人 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `markDeleted`（逐行） |
| `statusTransitionMethod` | `N/A` |
| `repositoryChecks` | `findAllByOrganizationId` + 批量软删 |
| `eventPublishAnchor` | 通常不单独发布（由 5.1.1 统一） |
| `unitTestCaseId` | `TC-DM-CUS-006-W-002` |

**方法契约摘要**：仅作为 `saveDistributionConfig` 内联步骤；**不**对 WH-DPL001 单独暴露 HTTP IF。

---

#### 5.1.3 `batchInsertDistributionRules(BatchInsertDistributionRulesCommand cmd)` → `void`

**命令文件**：`BatchInsertDistributionRulesCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `organizationId` | `Long` | 开立科室标识 |
| `operatorId` | `Long` | 操作人 |
| `items` | `List<DistributionRuleItemCommand>` | 与 5.1.1 子项结构相同 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `assertRequiredFields` |
| `statusTransitionMethod` | `N/A` |
| `repositoryChecks` | `saveAll` |
| `eventPublishAnchor` | 通常不单独发布 |
| `unitTestCaseId` | `TC-DM-CUS-006-W-003` |

**方法契约摘要**：仅作为 `saveDistributionConfig` 内联步骤；**不**对 WH-DPL001 单独暴露 HTTP IF。

### 5.2 读侧 — `ServiceLocationTemplateQueryService`（`domain.queries`）

所有查询默认 `isDeleted=false`；分页在数据库侧过滤与统计 total。

| 方法 | 入参 | 出参（类名，字段见 §4.6） | 关键实现 | `unitTestCaseId` |
| ---- | ---- | ------------------------- | -------- | ---------------- |
| `listDistributionRulesByOrganization` | `organizationId` | `List<ServiceLocationTemplateQueryModel>` | 按科室加载未删模板行，内存排序后映射 | `TC-DM-CUS-006-R-001` |
| `listAllDistributionRules` | 无 | `List<ServiceLocationTemplateQueryModel>` | 全量未删模板行，机构+优先级排序 | `TC-DM-CUS-006-R-002` |
| `listCandidateDistributionLocationIds` | `organizationId,itemTable,distributionCategoryCode,atTime` | `List<Long>` | 科室内该 **categoryCode** 下全部生效模板行 → **`priority`→`id` 排序** + `def_location_id` 去重（**不做** calcScore；**不用** `display_order`）；护士降级等 | `TC-DM-CUS-006-R-004` |
| `listApplicableDistributionLocationIds` | `organizationId,itemTable,distributionCategoryCode,itemDefinitionId,methodCode,typeCode,atTime` | `List<Long>` | 医生站 IF-DOR-11 / 签发 FIFO：生效行 + **calcScore≥0**（单品→耗材种类→药品用法→仅发放类别）→ 按**匹配粒度→`priority`→`id`** 排序去重（**不用** `display_order`）；应用层再叠 **BR-SLT-005** 正库存下探写默认点；写路径前端已回传库位时由 **`ClinicalCatalogPriceResolver#resolveDraftChargePriceLine`** 跳过二次 match | `TC-DM-CUS-006-R-006` |
| `listEffectiveTemplateRowsForDispensing` | 同 `listCandidate…` 入参 | `List<ServiceLocationTemplateQueryModel>` | 与 `listCandidate…` 同源过滤，返回完整模板行供目录降级 | `TC-DM-CUS-006-R-005` |
| `matchDefaultDistributionLocation` | `organizationId,itemTable,distributionCategoryCode,itemDefinitionId,methodCode,typeCode,atTime` | `Long` | 领域单点择优（**不查库存**）：主路径 calcScore≥0 后按粒度→`priority`→`id`；**耗材**主路径全负时软回落未绑单品行（种类可放宽，用法须兼容，见 `collectDeviceUnscopedFallback`）；无命中返回 null；缺科室抛业务异常；有库存下探见应用层 `ClinicalCatalogDispenseLocationResolver#pickFirstLocationWithPositiveStock` | `TC-DM-CUS-006-R-003` |

**读方法注释锚点（统一）**：构建查询条件（含软删过滤）→ 仓储/规格单次或有限次调用 → 组装 `ServiceLocationTemplateQueryModel`、库位 id 列表或标量 `Long`（无写库）。

---

## 6. 不变式映射（DM §7）

| BR 编号 | 实现策略摘要 |
| ------- | ------------ |
| `BR-SLT-001` | 保存/列表前须选择有效开立科室（`organizationId` 存在）且为**门诊或住院科室**；落地：`ServiceLocationTemplateAppService#assertOrderingDepartment`；测试：`TC-DM-CUS-006-BR-001` |
| `BR-SLT-002` | 物品表名必填且仅允许 `med_medication_definition` / `adm_device_definition`；落地：应用层 Gate + 实体 `assertRequiredFields`；测试：`TC-DM-CUS-006-BR-002` |
| `BR-SLT-003` | 发放类别、默认库位、优先级必填；落地：实体 + 应用层；测试：`TC-DM-CUS-006-BR-003` |
| `BR-SLT-004` | 同一科室内「物品表+发放类别+具体项+**种类**+用法+生效时段+默认发药位置」配置键不重复；同条件多药房允许（靠 `priority` 排候选）；落地：`assertNoDuplicateKey`；测试：`TC-DM-CUS-006-BR-004` |
| `BR-SLT-005` | **同一匹配竞争组**内非空 `priority` 须 1..N 连续（同条件多药房：1 先试，无正库存再试 2）；不同品类/种类各自从 1 起；匹配先比粒度（同档单品优先）再比组内 `priority`；落地：`assertPriorityContinuous` + 应用层 `pickFirstLocationWithPositiveStock`；测试：`TC-DM-CUS-006-BR-005` |
| `BR-SLT-006` | 整科覆盖单事务先软删后插；事务边界：`ServiceLocationTemplateAppService` + `AppServiceAspect`（领域方法不单独 `@Transactional`）；测试：`TC-DM-CUS-006-BR-006` |
| `BR-SLT-014` | 非空 `typeCode` 须在 `device_type_code` 且仅耗材行；落地：`ServiceLocationTemplateAppService#validateSaveDistributionConfigRequest`；测试：`TC-DM-CUS-006-BR-014` |
| `BR-SLT-007` | 匹配仅未删且时段有效行；落地：QueryService 过滤；测试：`TC-DM-CUS-006-BR-007` |
| `BR-SLT-008` | 默认库位须为有效药房/科室药房/药库；落地：**应用层** `listAllWarehouseAndPharmacyLocations` Gate；测试：`TC-DM-CUS-006-BR-008` |
| `BR-SLT-009` | 生效时段非空时 start 早于 end；落地：实体 `assertRequiredFields` + 应用层 Gate；测试：`TC-DM-CUS-006-BR-009` |
| `BR-SLT-010` | 双空时段=全天；落地：`isEffectiveAt` / QueryService；测试：`TC-DM-CUS-006-BR-010` |
| `BR-SLT-011` | 前端暂存不落库；落地：前端 + 仅 `saveDistributionConfig` 写库；测试：`TC-DM-CUS-006-BR-011` |
| `BR-SLT-012` | 删行后整科保存，无独立删除 IF；落地：`ServiceLocationTemplateAppService` 仅暴露 save；测试：`TC-DM-CUS-006-BR-012` |
| `BR-SLT-013` | 非空 `methodCode` 须在对应码表内；中草药（`med_category_code`= `4`）→ `tcm_method_code`，其余药品→ `method_code`；落地：`ServiceLocationTemplateAppService#validateSaveDistributionConfigRequest`；测试：`TC-DM-CUS-006-BR-013` |

---
## 7. 事件契约与触发规则（DM §5）

| 事件类名 | 事件类包路径 | 最小载荷（字段） | 触发方法 |
| -------- | ------------ | ---------------- | -------- |
| `ServiceLocationTemplateEditedEvent` | `com.zxplat.domain.share.events.custom.servicelocationtemplate` | `aggregateId`、`operationType`、`occurredAt` | `saveDistributionConfig` |

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
- [ ] §5.1 写方法数量、顺序与 DM §6.1 一致（当前 3 个）
- [ ] §5.2 读方法与 §4.6 契约一致（3 个返回 `List<ServiceLocationTemplateQueryModel>`，2 个为 IF-DOR-11 扩展；另有 2 个返回标量/列表基元，见 §4.6.2）
- [ ] §6 BR 映射与单测绑定已覆盖
- [ ] §7 事件与触发方法覆盖 DM §5 全量事件
- [ ] 无占位措辞、无 YAML/front matter

---

## 11. 单次会话代码生成门禁（DoD）

### 11.1 前置输入

- 锁定 DM：`DM-CUS-006-ServiceLocationTemplate-取药科室.md`
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
- [x] `### 5.1` 下 `#### 5.1.n` 数量为 3，与 DM §6.1 对齐
- [x] 每个写方法含命令文件、字段全集、绑定项、方法契约摘要
- [x] 列表型模板读返回统一落在 `§4.6.1`；标量/基元列表读返回落在 `§4.6.2`
- [x] 事件契约与 BR 映射已落地
- [x] 正文无 YAML/sdd_status/dm_code 元数据

---

## 定稿评分（百分制）

| 维度 | 分值 | 自评 |
| ---- | ---- | ---- |
| 输入完整性 | 20 | 20 |
| 方法白名单对齐 | 25 | 25 |
| 不变式映射 | 20 | 19 |
| 事件契约 | 15 | 15 |
| 软删与框架一致 | 10 | 10 |
| 去重与无歧义 | 10 | 10 |
| **总分** | **100** | **100（可进入代码会话）** |

---

## 12. 与 DM 同步记录

| 日期 | 摘要 |
| --- | --- |
| 2026-10-11 | **对齐 DM v1.3.9**：`matchDefault…` 耗材软回落未绑单品；`listByOrganization` 轻量品名 enrich（`ProductDefinitionQueryService#batchLoadByKeys`）；**BR-SLT-001** 门诊/住院门禁；写操作人 `CurrentPractitionerResolver` |
| 2026-10-11 | **对齐 DM v1.3.8**：药品主路径=用法、耗材主路径=种类；**BR-SLT-005** 正库存下探落应用层；`matchDefault…` 不查库存 |
| 2026-08-25 | **对齐 DM v1.3.7**：**BR-SLT-005** 改为匹配竞争组内 1..N（同条件多药房）；非整科全局序号 |
| 2026-08-25 | **对齐 DM v1.3.6**：模板 `type_code`；calcScore 具体项→种类→用法→仅大类；**BR-SLT-014**；消费方传 `typeCode` |
| 2026-08-25 | **对齐 DM v1.3.5 / 现行实现**：`listCandidate…` 不做 calcScore；`listApplicable…` 带粒度排序；**BR-SLT-005** 整科全局 1..N；**BR-SLT-006** 事务改为 AppService 切面；§5.1.1 `transaction` 绑定同步 |
| 2026-07-28 | **v1.3.4**：**BR-SLT-004** 配置键纳入默认发药位置；同匹配条件多药房靠 `priority` 排候选 |
| 2026-06-17 | **v1.3.3**：WH-DPL001 用法分码表（`method_code`/`tcm_method_code`）；**BR-SLT-013**；init/enrich 对齐 `TcmMethodCodeDisplaysupport |
| 2026-06-11 | **v1.3.2**：§1.1 目录读侧 `defaultDispenseLocationId` + 写路径库位透传不再二次 match；§5.2 `listApplicable…` 消费边界 |
| 2026-06-05 | **M2 回更**：对齐 DM v1.3.0；§5.1 Command 字段全集修正；BR-SLT-001/004/008 落地分层；配置 App `ServiceLocationTemplateAppService`（`init`/`listByOrganization`/`saveDistributionConfig`）见 DM §6.4（事务由 `AppServiceAspect` 提供，见 v1.3.5） |
| 2026-05-28 | **对账收口**：与 `zxplat-core/zxplat-domain` 现行实现一致；本聚合**无** InpatientInfo/OutpatientInfo/OrderInfo ReadModel 消费（跨聚合读组装见 **DM-ADM-002** §6.2.18～20 · `EncounterContextFacade`）。 |（已迁至 EncounterContextReader / application.shared.encounter）

---

## 13. 配置端应用层落点（与 DM §6.4 对齐）

| Maven 模块 | 包 | 类 |
| ---------- | -- | -- |
| `zxplat-application` | `com.zxplat.application.basic.servicelocationtemplate` | `ServiceLocationTemplateAppService` |
| 同上 | `...request` / `...dto` / `...mapper` | `ListServiceLocationTemplateByOrganizationRequest`、`SaveServiceLocationTemplateConfigRequest`、`ServiceLocationTemplateDto`、`ServiceLocationTemplateInitDto`、`ServiceLocationTemplateMapper` |

**HTTP**：`POST /api/app/serviceLocationTemplate/init` | `listByOrganization` | `saveDistributionConfig`

**写操作人**：`CurrentPractitionerResolver#requireOperatorId`（B.6.1）。

**展示 enrich**：`LocationQueryService#mapNamesByIds`、`ProductDefinitionQueryService#batchLoadByKeys`（**仅品名**，不走选品术语 assembler）、`TerminologyOptionLoader#buildLabelMap`（分类按 `itemTable` 分码表；用法按中草药类别选用 `TcmMethodCodeDisplayAssembler`）。

**配置页 UX（非领域契约）**：前端 `servicelocation/index.vue` 对草稿表客户端分页（`is-all-data`）；单品/禁用列用只读文案替代整棵 `ElSelect`；时段用轻量输入，避免每行双 `ElTimePicker`。

**`ServiceLocationTemplateInitDto` 下拉**：

| 字段 | 术语码表 | 适用行 |
| --- | --- | --- |
| `medicationCategoryOptions` | `med_category_code` | 药品发放类别 |
| `deviceCategoryOptions` | `device_category_code` | 耗材发放类别 |
| `methodCodeOptions` | `method_code` | 西药/中成药用法 |
| `tcmMethodCodeOptions` | `tcm_method_code` | 中草药用法 |

---

**本 SDD 正文结束。**
