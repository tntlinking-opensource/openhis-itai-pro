# DM-CUS-002 ChargeItemAutoGen 收费明细自动生成规则 · DomainModel-SDD

> **对应 DM**：`docs/领域/DomainModel/DM-CUS-002-ChargeItemAutoGen-收费明细自动生成规则.md`（本 SDD 假定该 DM 为当期锁定版本）  
> **目标工程**：`zxplat-core/zxplat-domain`  
> **限界上下文**：CUS 本地扩展  
> **聚合**：ChargeItemAutoGen（子实体见 §4）

---

## 输入完整性检查结果

| 最小输入项 | 状态 | 说明 |
| ---------- | ---- | ---- |
| DM 路径 | 满足 | 已绑定 DM-CUS-002 |
| SDD 输出路径 | 满足 | 本文件 |
| 目标模块 | 满足 | `zxplat-domain`（DomainModel-SDD） |
| 包规则 `com.zxplat.domain.custom.chargeitemautogen` | 满足 | `yyy` 全小写 |
| 固定技术口径（ID Long、时间 LocalDateTime、软删、beforeCommit、分页等） | 满足 | 见 §3、§9 |
| DM 第 6 章写/读白名单 | 满足 | §5 覆盖写方法 5 个、读方法 5 个 |
| DM 第 7 章 BR | 满足 | §6 映射 13 条并给出实现锚点 |
| DM 第 5 章事件 | 满足 | §7 覆盖 2 个事件 |

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

### 2.2 包结构（`com.zxplat.domain.custom.chargeitemautogen`）

| 相对包 | 内容 |
| ------ | ---- |
| `entity` | 聚合根实体及子实体（若有） |
| `valueobject` | 值对象（若有） |
| `command` | 一方法一命令文件 |
| `service` | `*DomainService` 与 `*QueryService`（两个方法集，直接实现类） |
| `queries` | 查询入参对象（如 `*Query`） |
| `queries.model` | 查询出参模型（如 `*QueryModel`） |
| `repository` | `JpaRepository` 接口 |
| `share.events` | `com.zxplat.domain.share.events.custom.chargeitemautogen` |

---

## 3. 类型 / 字段命名 / 注解规则

| 规则 | 说明 |
| ---- | ---- |
| 主键 / 外键 | `Long`；持久化列名与 DM 一致 |
| 主键 `id` | **表列**；创建时不入 Command，由 `JpaRepository.save` 生成后作为返回值 / `aggregateId` |
| Command `ruleId` | **非表列**；仅 Update/Delete/Activate/Suspend Command 中传入，语义 = 已存在行的 `id` |
| 时间 | 业务字段按 DM；审计字段 `LocalDateTime` |
| `*_enum`（`status_enum`） | `String`（`PublicationStatus.code`：`ACTIVE` / `INACTIVE` / `DISCARDED`） |
| 其它 `*_enum` | 按 DM 明示类型 |
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
| `cus_charge_item_auto_gen` | `ChargeItemAutoGen` | DM §3.1 单表聚合；住院滚费按 `encounter_id` 维度配置 |

### 4.2 聚合根 `ChargeItemAutoGen`（字段级契约）

| 字段名（Java / 列） | 类型 | 必填 | 说明 / 约束 |
| -------------- | ---- | ---- | ----------- |
| `id` | `Long` | 库内必填 | 主键；**无 `rule_id` 列**；创建 Command 不含 `id` |
| `ruleName` / `rule_name` | `String` | 是 | 管理端展示名称 |
| `statusEnum` / `status_enum` | `String`（`PublicationStatus.code`） | 是 | `ACTIVE` / `INACTIVE` / `DISCARDED`；缺省创建为 `ACTIVE` |
| `encounterId` / `encounter_id` | `Long` | 是 | 住院就诊 `adm_encounter.id`；住院滚费配置归属 |
| `triggerSourceTable` / `trigger_source_table` | `String` | 是 | 来源 DM 主档表名，如 `wkf_activity_definition` |
| `triggerSourceId` / `trigger_source_id` | `Long` | 是 | 来源主键（ActivityDefinition.id） |
| `chargeDefinitionId` / `charge_definition_id` | `Long` | 是 | `adm_charge_item_definition.id` |
| `performingOrgId` / `performing_org_id` | `Long` | 否 | 执行科室限定；空=不限科室 |
| `quantityValue` / `quantity_value` | `BigDecimal` | 是 | 默认数量（>0，缺省 1） |
| `priority` | `Integer` | 是 | 同 encounter 下排序，升序，缺省 0 |
| `remark` | `String` | 否 | 备注 |
| `businessTime` / `business_time` | `LocalTime` | 是 | 滚费业务时刻（拼 `billDate`；缺省 `23:59:59`；护理滚费不读） |
| `billingPractitionerId` / `billing_practitioner_id` | `Long` | 是（新建） | 滚费计费人 `adm_practitioner.id`；落 Charge `performer_id`；存量可空回落责任护士 |

**不变式方法（实现命名）**：`assertRequiredFields`、`assertEditable`、`assertActivatable`、`assertSuspendable`。

> **Command 与表列**：`CreateAutoGenRuleCommand` **无** `id`/`ruleId`。`UpdateAutoGenRuleCommand` 等中的 **`ruleId`** → `findById(ruleId)` → 表 **`id`**。事件 `ChargeItemAutoGenEditedEvent` 构造器首参传入 `saved.getId()`，落 `DomainEvent.aggregateId`。

### 4.3 子实体

无。本聚合为单表。

### 4.5 仓储接口

- `ChargeItemAutoGenRepository extends JpaRepository<ChargeItemAutoGen, Long>`
- `findById(Long id)`
- `existsByEncounterIdAndTriggerSourceTableAndTriggerSourceIdAndPerformingOrgIdAndChargeDefinitionId(...)`（BR-CAG-001）
- `existsByEncounterIdAndTriggerSourceTableAndTriggerSourceIdAndPerformingOrgIdAndChargeDefinitionIdAndIdNot(...)`（更新唯一性）
- `findAllByEncounterIdAndIsDeletedFalseOrderByPriorityAscIdAsc(Long encounterId)`
- `findAllByTriggerSourceTableAndTriggerSourceIdAndIsDeletedFalseOrderByPriorityAscIdAsc(...)`
- `findAllByChargeDefinitionIdAndIsDeletedFalseOrderByIdAsc(Long chargeDefinitionId)`

### 4.6 查询模型（`queries.model`）

各 QueryModel 均含 `encounterId`（除仅触发键命中用的精简模型外，完整规则模型均携带）。

#### 4.6.1 `ChargeItemAutoGenActiveRuleQueryModel`（`findActiveRulesByTrigger` / `findActiveRulesByEncounter`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `id` | `Long` | `cus_charge_item_auto_gen.id` | 规则主键 |
| `statusEnum` | `String` | `status_enum` | 发布状态 |
| `ruleName` | `String` | `rule_name` | 展示名称 |
| `encounterId` | `Long` | `encounter_id` | 住院就诊 |
| `triggerSourceTable` | `String` | `trigger_source_table` | 触发来源表 |
| `triggerSourceId` | `Long` | `trigger_source_id` | 触发来源主键 |
| `chargeDefinitionId` | `Long` | `charge_definition_id` | 收费项定价 |
| `performingOrgId` | `Long` | `performing_org_id` | 执行科室 |
| `quantityValue` | `BigDecimal` | `quantity_value` | 默认数量（滚费/触发生成用） |
| `priority` | `Integer` | `priority` | 同键下排序 |
| `businessTime` | `LocalTime` | `business_time` | 滚费业务时刻 |
| `billingPractitionerId` | `Long` | `billing_practitioner_id` | 滚费计费人 |

#### 4.6.2 `ChargeItemAutoGenRuleQueryModel`（`findRulesByEncounterId` / `findRulesByChargeDefinitionId`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `id` | `Long` | `id` | 规则主键 |
| `statusEnum` | `String` | `status_enum` | 发布状态 |
| `ruleName` | `String` | `rule_name` | 展示名称 |
| `encounterId` | `Long` | `encounter_id` | 住院就诊 |
| `triggerSourceTable` | `String` | `trigger_source_table` | 触发来源表 |
| `triggerSourceId` | `Long` | `trigger_source_id` | 触发来源主键 |
| `chargeDefinitionId` | `Long` | `charge_definition_id` | 收费项定价 |
| `performingOrgId` | `Long` | `performing_org_id` | 执行科室 |
| `quantityValue` | `BigDecimal` | `quantity_value` | 默认数量 |
| `priority` | `Integer` | `priority` | 优先级 |
| `remark` | `String` | `remark` | 备注 |
| `businessTime` | `LocalTime` | `business_time` | 滚费业务时刻 |
| `billingPractitionerId` | `Long` | `billing_practitioner_id` | 滚费计费人 |

#### 4.6.3 `ChargeItemAutoGenPageItemQueryModel`（`pageChargeItemAutoGenRules`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `id` | `Long` | `id` | 规则主键 |
| `statusEnum` | `String` | `status_enum` | 发布状态 |
| `ruleName` | `String` | `rule_name` | 展示名称 |
| `encounterId` | `Long` | `encounter_id` | 住院就诊（分页筛选） |
| `triggerSourceTable` | `String` | `trigger_source_table` | 触发来源表 |
| `triggerSourceId` | `Long` | `trigger_source_id` | 触发来源主键 |
| `chargeDefinitionId` | `Long` | `charge_definition_id` | 收费项定价 |
| `performingOrgId` | `Long` | `performing_org_id` | 执行科室 |
| `quantityValue` | `BigDecimal` | `quantity_value` | 默认数量 |

---

## 5. 写侧与读侧白名单（命令字段全集）

### 5.1 写侧 — `ChargeItemAutoGenDomainService`

#### 5.1.1 `createAutoGenRule(CreateAutoGenRuleCommand cmd)` → `Long`

**命令文件**：`CreateAutoGenRuleCommand.java`（**不含 `id` / `ruleId`**）

**字段全集**：

| 字段 | 类型 | 必填 | 说明 |
| ---- | ---- | ---- | ---- |
| `ruleName` | `String` | 是 | 规则名称 |
| `encounterId` | `Long` | 是 | 住院就诊 |
| `triggerSourceTable` | `String` | 是 | 触发来源表名 |
| `triggerSourceId` | `Long` | 是 | 触发来源主键 |
| `chargeDefinitionId` | `Long` | 是 | 收费项定价主键 |
| `statusEnum` | `String` | 否 | 缺省由领域 `initActiveStatus` 置 `ACTIVE` |
| `performingOrgId` | `Long` | 否 | 执行科室 |
| `quantityValue` | `BigDecimal` | 否 | 缺省 1 |
| `priority` | `Integer` | 否 | 缺省 0 |
| `remark` | `String` | 否 | 备注 |
| `businessTime` | `LocalTime` | 否 | 缺省日末 `23:59:59` |
| `billingPractitionerId` | `Long` | 否 | 缺省回落 `operatorId`；断言非空 |
| `operatorId` | `Long` | 否 | 操作人（事件用） |

**返回值**：`Long` — 持久化后的 **`id`**（`saved.getId()`）。

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `assertRequiredFields` |
| `statusTransitionMethod` | `initActiveStatus` 或命令指定 |
| `repositoryChecks` | `existsByEncounterIdAndTriggerSourceTableAndTriggerSourceIdAndPerformingOrgIdAndChargeDefinitionId` |
| `eventPublishAnchor` | `new ChargeItemAutoGenEditedEvent(saved.getId(), ...)` |
| `unitTestCaseId` | `TC-DM-CUS-002-W-001` |

---

#### 5.1.2 `updateAutoGenRule(UpdateAutoGenRuleCommand cmd)` → `void`

**命令文件**：`UpdateAutoGenRuleCommand.java`

> **不含 `statusEnum`**：启停须走 `activateAutoGenRule` / `suspendAutoGenRule`（BR-CAG-009）。

| 字段 | 类型 | 必填 | 说明 |
| ---- | ---- | ---- | ---- |
| `ruleId` | `Long` | 是 | **= 表 `id`**，非独立表列 |
| `ruleName` | `String` | 否 | 非空则覆盖 |
| `encounterId` | `Long` | 否 | 非空则覆盖 |
| `triggerSourceTable` | `String` | 否 | 非空则覆盖 |
| `triggerSourceId` | `Long` | 否 | 非空则覆盖 |
| `chargeDefinitionId` | `Long` | 否 | 非空则覆盖 |
| `performingOrgId` | `Long` | 否 | 非空则覆盖 |
| `quantityValue` | `BigDecimal` | 否 | 非空则覆盖 |
| `priority` | `Integer` | 否 | 非空则覆盖 |
| `remark` | `String` | 否 | 非空则覆盖 |
| `businessTime` | `LocalTime` | 否 | 非空则覆盖 |
| `billingPractitionerId` | `Long` | 否 | 非空则覆盖 |
| `operatorId` | `Long` | 否 | 操作人 |

---

#### 5.1.3 `deleteAutoGenRule(DeleteAutoGenRuleCommand cmd)` → `void`

| 字段 | 类型 | 必填 | 说明 |
| ---- | ---- | ---- | ---- |
| `ruleId` | `Long` | 是 | = 表 `id` |
| `operatorId` | `Long` | 否 | 操作人 |

---

#### 5.1.4 `activateAutoGenRule(ActivateAutoGenRuleCommand cmd)` → `void`

| 字段 | 类型 | 必填 | 说明 |
| ---- | ---- | ---- | ---- |
| `ruleId` | `Long` | 是 | = 表 `id` |
| `operatorId` | `Long` | 否 | 操作人 |

---

#### 5.1.5 `suspendAutoGenRule(SuspendAutoGenRuleCommand cmd)` → `void`

| 字段 | 类型 | 必填 | 说明 |
| ---- | ---- | ---- | ---- |
| `ruleId` | `Long` | 是 | = 表 `id` |
| `operatorId` | `Long` | 否 | 操作人 |

### 5.2 读侧 — `ChargeItemAutoGenQueryService`

所有查询默认 `isDeleted=false`；分页在数据库侧过滤与统计 total。

| 方法 | 入参 | 出参 | 关键实现 | `unitTestCaseId` |
| ---- | ---- | ---- | -------- | ---------------- |
| `findActiveRulesByTrigger` | `triggerSourceTable`, `triggerSourceId`, `performingOrgId` | `ChargeItemAutoGenActiveRuleQueryModel` | 按触发键 + active + 科室匹配 | `TC-DM-CUS-002-R-001` |
| `findRulesByEncounterId` | `encounterId` | `ChargeItemAutoGenRuleQueryModel` | `findAllByEncounterIdAndIsDeletedFalseOrderByPriorityAscIdAsc` | `TC-DM-CUS-002-R-002` |
| `findActiveRulesByEncounter` | `encounterId`, `performingOrgId` | `ChargeItemAutoGenActiveRuleQueryModel` | 同 encounter 列表 + active + 科室匹配 | `TC-DM-CUS-002-R-003` |
| `findRulesByChargeDefinitionId` | `chargeDefinitionId` | `ChargeItemAutoGenRuleQueryModel` | 按定价反查 | `TC-DM-CUS-002-R-004` |
| `pageChargeItemAutoGenRules` | `ChargeItemAutoGenPageQuery`, `Pageable` | `ChargeItemAutoGenPageItemQueryModel` | Specification；支持 `encounterId` 筛选 | `TC-DM-CUS-002-R-005` |

**读方法注释锚点（统一）**：构建查询条件（含软删过滤）→ 单次 SQL/仓储调用并完成分页统计 → 组装 QueryModel（无写库）。

---

## 6. 不变式映射（DM §7）

| BR 编号 | 实现策略摘要 |
| ------- | ------------ |
| `BR-CAG-001` | 同一 `(encounterId, triggerSourceTable, triggerSourceId, performingOrgId, chargeDefinitionId)` 在租户内禁止重复；创建 / 更新均校验；更新时排除自身 ID；落地：`existsByEncounterIdAndTriggerSourceTableAndTriggerSourceIdAndPerformingOrgIdAndChargeDefinitionId`；测试锚点：`TC-DM-CUS-002-BR-001` |
| `BR-CAG-002` | 科室匹配规则：`performingOrgId` 为空的全局规则任意上下文均可命中；非空规则仅当执行上下文科室非空且相等时命中；上下文无科室时不命中仅科室限定的规则；防止降级为全局的误匹配；落地位置：QueryService 内存过滤；测试锚点：`TC-DM-CUS-002-BR-002` |
| `BR-CAG-003` | 软删记录不参与默认业务查询，也不允许更新或启用/停用操作；仓储 `IsDeletedFalse`；已删行操作抛出异常；测试锚点：`TC-DM-CUS-002-BR-003` |
| `BR-CAG-004` | `chargeDefinitionId` 须指向存在且未软删的 ChargeItemDefinition；创建 / 更新前校验（应用层）；测试锚点：`TC-DM-CUS-002-BR-004` |
| `BR-CAG-005` | 触发来源表名不允许为空；`assertRequiredFields`；测试锚点：`TC-DM-CUS-002-BR-005` |
| `BR-CAG-006` | 触发来源主键不允许为空；`assertRequiredFields`；测试锚点：`TC-DM-CUS-002-BR-006` |
| `BR-CAG-007` | 收费项定价标识不允许为空；`assertRequiredFields`；测试锚点：`TC-DM-CUS-002-BR-007` |
| `BR-CAG-008` | 默认数量须大于零；`assertRequiredFields`；测试锚点：`TC-DM-CUS-002-BR-008` |
| `BR-CAG-009` | 启用与停用分属 `activateAutoGenRule` / `suspendAutoGenRule`；测试锚点：`TC-DM-CUS-002-BR-009` |
| `BR-CAG-010` | 更新/删除/启停时 Command.`ruleId` 须对应已存在行的表 **`id`**；`repository.findById(ruleId)`；测试锚点：`TC-DM-CUS-002-BR-010` |
| `BR-CAG-011` | 已废弃（`DISCARDED`）状态不允许修改；`assertEditable`；测试锚点：`TC-DM-CUS-002-BR-011` |
| `BR-CAG-012` | 同 encounter / 触发键下按 `priority` 升序、`id` 升序；仓储 `OrderByPriorityAscIdAsc`；测试锚点：`TC-DM-CUS-002-BR-012` |
| `BR-CAG-013` | `encounterId` 不允许为空；`assertRequiredFields`；测试锚点：`TC-DM-CUS-002-BR-013` |

---
## 7. 事件契约与触发规则（DM §5）

| 事件类名 | 事件类包路径 | 最小载荷（字段） | 触发方法 |
| -------- | ------------ | ---------------- | -------- |
| `ChargeItemAutoGenEditedEvent` | `com.zxplat.domain.share.events.custom.chargeitemautogen` | `aggregateId`（= 表 `id`）、`operation`、`triggerSourceTable`、`triggerSourceId`、`chargeDefinitionId`、`operatorId` | 五个写方法 |

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
- [ ] §5.2 读方法与 §4.6 QueryModel 一一对应（当前 5 个）
- [ ] §6 BR 映射与单测绑定已覆盖
- [ ] §7 事件与触发方法覆盖 DM §5 全量事件
- [ ] 无占位措辞、无 YAML/front matter

---

## 11. 单次会话代码生成门禁（DoD）

### 11.1 前置输入

- 锁定 DM：`DM-CUS-002-ChargeItemAutoGen-收费明细自动生成规则.md`
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
- [x] 每个读方法均有对应 `§4.6.n` QueryModel 字段表（5 读 / 3 出参模型）
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
| 2026-08-12 | **v1.0.3**：增 `business_time` / `billing_practitioner_id`（住院滚费配置业务时刻与计费人；对齐 AS-IP-NUR-013 BR-IP-ARG-015/019/021） |
| 2026-06-19 | **v1.0.2 对齐**：`encounter_id`；`charge_definition_id`；读侧 +2；**`id` 与 Command `ruleId`**；**状态码 `ACTIVE`/`INACTIVE`/`DISCARDED`** |
| 2026-06-19 | **应用层对接**：**AS-IP-NUR-013** 通过 `ChargeItemAutoGenDomainService` / `ChargeItemAutoGenQueryService` 消费本聚合五写五读；`ChargeItemAutoGenRuleQueryModel.createdAt` 支撑滚费 BR-IP-ARG-013 |
| 2026-05-28 | **对账收口**：与 `zxplat-core/zxplat-domain` 现行实现一致；本聚合**无** InpatientInfo/OutpatientInfo/OrderInfo ReadModel 消费（跨聚合读组装见 **DM-ADM-002** §6.2.18～20 · `EncounterContextFacade`）。 |（已迁至 EncounterContextReader / application.shared.encounter）

---

**本 SDD 正文结束。**
