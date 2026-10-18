# DM-ADM-016 ProductDefinition 产品定义扩展 · DomainModel-SDD

> **对应 DM**：`docs/领域/DomainModel/DM-ADM-016-ProductDefinition-产品定义扩展.md`（minimal canonical，本 SDD 假定该 DM 为当期锁定版本）  
> **目标工程**：`zxplat-core/zxplat-domain`  
> **限界上下文**：Administration · 产品定义读侧门面  
> **聚合**：ProductDefinition（无本包 JPA 聚合根，读侧路由）

---

## 输入完整性检查结果

| 最小输入项 | 状态 | 说明 |
| ---------- | ---- | ---- |
| DM 路径 | 满足 | 已绑定 DM-ADM-016 |
| SDD 输出路径 | 满足 | 本文件 |
| 目标模块 | 满足 | `zxplat-domain` |
| 包规则 `com.zxplat.domain.administration.product` | 满足 | `product` 全小写 |
| 固定技术口径 | 满足 | 见 §3、§9 |
| DM §6.1 写侧 | 满足 | 附录无 DomainService；写方法 **0** |
| DM §6.2 读侧 | 满足 | §5.2 覆盖 2 个读方法 |
| DM §7 BR | 满足 | §6 映射 BR-PROD-001～002 |
| DM §5 事件 | 满足 | 无事件；§7 声明 |

**与 DM 显式差异（采纳依据：模式 B）**

| 点 | DM 差异位置 | SDD 采纳 |
| -- | ----------- | -------- |
| 持久化 | §2 无本包表 | §4 无实体；路由四表见 `ProductDefinitionQueryService` |
| 展示 DTO | DM 未列字段 | `ProductDefinitionDisplayDto` 字段以 `share/product/dto` 源码为准（§4.6.1） |
| 术语回填 | DM §1 写应用 shared | 领域只出 DTO；`*DictText` 由应用 `ProductDefinitionDisplayTerminologyAssembler` 回填 |

---

## 1. 设计目标与边界

### 1.1 设计目标

- 固化 **产品定义批量只读路由**：按 `productTableName + productDefinitionId` 复合键批量加载展示 DTO 或医保目录投影。
- **不写**独立产品主表；禁止在本包新增写侧 DomainService（BR-PROD-002）。
- **单次成稿目标**：契约与 `ProductDefinitionQueryService` 一致（模式 B）。

### 1.2 边界

- **在内**：四表路由（药品/诊疗/耗材/医疗服务）、复合键 Map、单表失败不阻断其它表（display 路径）。
- **在外**：各上游定义包 CRUD、术语展示组装、订单计价。
- **跨域**：注入 `MedicationDefinitionQueryService`、`ActivityDefinitionQueryService`、`DeviceDefinitionQueryService`、`HealthcareServiceQueryService`（只读）。

---

## 2. 工程落点与包结构

### 2.1 Maven 模块

- 模块：`zxplat-domain`（`ProductDefinitionQueryService`）；共享 DTO/QueryModel 位于 `zxplat-core/zxplat-share`（`com.zxplat.domain.share.*`）。

### 2.2 包结构（`com.zxplat.domain.administration.product`）

| 相对包 | 内容 |
| ------ | ---- |
| `entity` | 无 |
| `valueobject` | 无 |
| `command` | 无 |
| `repository` | 无 |
| `service` | `ProductDefinitionQueryService` |
| `support` | `ProductDefinitionDisplaySupport`（实体→DTO 映射） |
| `share.query` | `ProductDefinitionBatchLookup`、`ProductDefinitionCatalogQueryModel`（模块 share） |
| `share.product.dto` | `ProductDefinitionDisplayDto` |

**存储口径**：路由表名常量 `TableNameConstants`（`med_medication_definition`、`wkf_activity_definition`、`adm_device_definition`、`adm_healthcare_service`）。

---

## 3. 类型 / 字段命名 / 注解规则

| 规则 | 说明 |
| ---- | ---- |
| 主键 | 复合键 `ProductDefinitionKeyUtil.buildCompositeKey(tableName, id)` |
| `*_enum` / `*_code` | 投影 DTO 内为 `String` |
| 事务 | 只读服务，无写库 |
| 事件 | 无 |

---

## 4. 实体 / 值对象 / 枚举 / 仓储 / 查询契约

### 4.1 表与实体映射

| 逻辑表 | 实体类 | 说明 |
| ------ | ------ | ---- |
| （路由） | 无本包实体 | 持久化在上游定义包 |

### 4.2 聚合根

本包无 JPA 聚合根；读模型见 §4.6。

### 4.3 子实体

无。

### 4.5 仓储接口

无本包 Repository。

### 4.6 查询模型（`queries.model` / share DTO）

#### 4.6.1 `ProductDefinitionDisplayDto`（对应 `batchLoadByKeys`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `productTableName` | `String` | 路由键 | 来源表名 |
| `productDefinitionId` | `Long` | 各定义表.id | 定义主键 |
| `productBusNo` | `String` | 各表 bus_no | 业务编码 |
| `productName` | `String` | 各表 name | 名称 |
| `merchandiseName` | `String` | 药品 | 商品名 |
| `categoryCode` | `String` | 各表 | 类别码 |
| `categoryCodeDictText` | `String` | 应用回填 | 类别展示 |
| `typeCode` | `String` | 耗材 | 种类码 |
| `typeCodeDictText` | `String` | 应用回填 | 种类展示 |
| `purchaseUnitCode` | `String` | 药品/耗材 | 采购单位 |
| `purchaseUnitCodeDictText` | `String` | 应用回填 | |
| `purchaseUnitRatio` | `BigDecimal` | 药品/耗材 | 换算比 |
| `splitUnitCode` | `String` | 药品/耗材 | 拆零单位 |
| `splitUnitCodeDictText` | `String` | 应用回填 | |
| `packageUnitCode` | `String` | 药品/耗材 | 包装单位 |
| `packageUnitCodeDictText` | `String` | 应用回填 | |
| `packageUnitRatio` | `BigDecimal` | 药品/耗材 | |
| `traceUnitCode` | `String` | 药品/耗材 | 追溯单位 |
| `traceUnitCodeDictText` | `String` | 应用回填 | |
| `traceUnitRatio` | `BigDecimal` | 药品/耗材 | |
| `stockUnitCode` | `String` | 药品/耗材 | 库存单位 |
| `stockUnitCodeDictText` | `String` | 应用回填 | |
| `stockUnitRatio` | `BigDecimal` | 药品/耗材 | |
| `itemMinQuantity` | `BigDecimal` | 药品/耗材 | 最小警戒 |
| `itemMaxQuantity` | `BigDecimal` | 药品/耗材 | 最大警戒 |
| `doseUnitCode` | `String` | 药品 | 剂量单位 |
| `doseUnitCodeDictText` | `String` | 应用回填 | |
| `doseUnitRatio` | `BigDecimal` | 药品 | |
| `dose` | `BigDecimal` | 药品 | 默认剂量 |
| `minUnit` | `BigDecimal` | 药品 | 最小剂量 |
| `maxUnit` | `BigDecimal` | 药品 | 最大剂量 |
| `defEncounterUnitCode` | `String` | 各表 | 门诊计价单位 |
| `defEncounterUnitCodeDictText` | `String` | 应用回填 | |
| `defInhospitalUnitCode` | `String` | 各表 | 住院计价单位 |
| `defInhospitalUnitCodeDictText` | `String` | 应用回填 | |
| `totalVolume` | `String` | 药品/耗材 | 规格文本 |
| `price` | `BigDecimal` | 各表 | 目录价 |
| `manufacturer` | `String` | 应用回填 | 厂家名 |
| `manufacturerId` | `Long` | 各表 | 厂家 id |
| `locationId` | `Long` | 各表 | 默认库房 |
| `locationName` | `String` | 应用回填 | |
| `supplierId` | `Long` | 各表 | 供应商 |
| `statusEnum` | `String` | 各表 | 状态 |
| `activeFlag` | `Boolean` | 投影 | 是否有效 |
| `rxFlag` | `Boolean` | 药品 | 处方药 |
| `ybNo` | `String` | 各表 | 医保码 |
| `approvalNumber` | `String` | 药品 | 批准文号 |
| `pyStr` | `String` | 各表 | 拼音 |
| `wbStr` | `String` | 各表 | 五笔 |
| `chrgitmLv` | `Integer` | 各表 | 医保等级码 |
| `chrgitmLvText` | `String` | 应用回填 | |
| `chargeItemDefinitionId` | `Long` | 各表 | 收费项定义 |
| `rateCode` | `String` | 诊疗/药品 | 频次 |
| `rateCodeDictText` | `String` | 应用回填 | |
| `methodCode` | `String` | 药品 | 给药途径 |
| `methodCodeDictText` | `String` | 应用回填 | |
| `doseFormCode` | `String` | 药品 | 剂型 |
| `doseFormCodeDictText` | `String` | 应用回填 | |
| `dosageInstruction` | `String` | 药品 | 中药煎法 |
| `dosageInstructionDictText` | `String` | 应用回填 | |
| `restrictedFlag` | `Boolean` | 各表 | 限制用药 |
| `restrictedScope` | `String` | 各表 | 限制范围 |
| `restrictedEnum` | `String` | 各表 | 限制类型 |
| `restrictedEnumDictText` | `String` | 应用回填 | |
| `pharmacologyCategoryCode` | `String` | 药品 | 药品性质 |
| `pharmacologyCategoryCodeDictText` | `String` | 应用回填 | |
| `partAttributeEnum` | `String` | 药品 | 门诊拆分属性 |
| `partAttributeEnumDictText` | `String` | 应用回填 | |
| `thoPartAttributeEnum` | `String` | 药品 | 住院拆分属性 |
| `thoPartAttributeEnumDictText` | `String` | 应用回填 | |
| `skinTestFlag` | `Boolean` | 药品 | 皮试 |
| `injectFlag` | `Boolean` | 药品 | 注射 |
| `activityTypeCode` | `String` | 诊疗 | 活动类型 |

#### 4.6.2 `ProductDefinitionCatalogQueryModel`（对应 `batchLoadAsMap`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `productTableName` | `String` | 路由 | 来源表 |
| `productDefinitionId` | `Long` | 定义表.id | 主键 |
| `productBusNo` | `String` | bus_no | 机构目录编码回落 |
| `productName` | `String` | name | 名称 |
| `ybNo` | `String` | yb_no | 国家医保目录编码 |
| `ybOrgNo` | `String` | 投影 | 机构目录编码；空则回落 busNo |

#### 4.6.3 入参 `ProductDefinitionBatchLookup`（读方法共用）

| 字段名（Java） | 类型 | 来源 | 说明 |
| -------------- | ---- | ---- | ---- |
| `productTableName` | `String` | 调用方 | 表名 |
| `productDefinitionId` | `Long` | 调用方 | 定义 id |

---

## 5. 写侧与读侧白名单（命令字段全集）

### 5.1 写侧 — （无 DomainService）

DM §6.1 与源码均无写方法（**0**）。持久化与写能力归属上游 Medication/Activity/Device/Healthcare 定义包；**本节无 `#### 5.1.n` Command 块**（纯读侧门面，BR-PROD-002）。

### 5.2 读侧 — `ProductDefinitionQueryService`

| 方法 | 入参 | 出参（类名，字段见 §4.6） | 关键实现 | `unitTestCaseId` |
| ---- | ---- | ------------------------- | -------- | ---------------- |
| `batchLoadByKeys` | `List<ProductDefinitionBatchLookup>` | `Map<String, ProductDefinitionDisplayDto>` | 按表分组 id；四表路由；单表异常 log.warn 不阻断 | `TC-DM-ADM-016-R-001` |
| `batchLoadAsMap` | `List<ProductDefinitionBatchLookup>` | `Map<String, ProductDefinitionCatalogQueryModel>` | 同上，目录投影；无 try/catch 包裹 | `TC-DM-ADM-016-R-002` |

**读方法注释锚点（统一）**：`groupIdsByTable` → 分表批量 QueryService → 复合键 put Map（无写库）。

---

## 6. 不变式映射（DM §7）

| BR 编号 | 实现策略摘要 |
| ------- | ------------ |
| BR-PROD-001 | `loadDisplayRows`/`loadCatalogRows` 仅识别四表常量；未知表名跳过 |
| BR-PROD-002 | 本包无 DomainService；写能力不得静默加在本包 |

---

## 7. 事件契约与触发规则（DM §5）

| 事件类名 | 事件类包路径 | 最小载荷（字段） | 触发方法 |
| -------- | ------------ | ---------------- | -------- |
| （无） | — | — | 只读门面 |

**发布时机**：不适用。

---

## 8. 删除与可见性契约

- 本包不持久化；上游定义软删由各自 QueryService 过滤策略决定。
- 批量加载跳过 `productDefinitionId` 或 `productTableName` 为空的 lookup 项。

---

## 9. 框架优先适配规则

- 复合键统一 `ProductDefinitionKeyUtil`。
- 映射逻辑收敛 `ProductDefinitionDisplaySupport`。
- 遵循项目级成码门禁。

---

## 10. 生成与验收清单

- [ ] `ProductDefinitionQueryService` 两读方法与 DM 附录一致
- [ ] 四表路由与 TableNameConstants 一致
- [ ] 无本包写侧 DomainService
- [ ] 编译通过

---

## 11. 单次会话代码生成门禁（DoD）

### 11.1 前置输入

- 锁定 DM-ADM-016 与本 SDD。

### 11.2 DoD

- 读侧 2 方法 + DTO 投影与源码一致。

### 11.3 失败处理

- 新增表路由须先改 DM 再改 SDD。

---

## 定稿检查清单（可打勾）

- [x] 章节骨架与金母版同构
- [x] 写方法 0 = DM 附录
- [x] 读方法 2 = DM 附录
- [x] §4.6 字段表完整（DisplayDto 全字段）
- [x] 无占位措辞

---

## 定稿评分（百分制）

| 维度 | 分值 | 自评 |
| ---- | ---- | ---- |
| 输入完整性 | 20 | 20 |
| 方法白名单对齐 | 25 | 25 |
| 不变式映射 | 20 | 20 |
| 事件契约 | 15 | 15 |
| 软删与框架一致 | 10 | 9 |
| 去重与无歧义 | 10 | 10 |
| **总分** | **100** | **99，可进入代码会话** |

---

**本 SDD 正文结束。**
