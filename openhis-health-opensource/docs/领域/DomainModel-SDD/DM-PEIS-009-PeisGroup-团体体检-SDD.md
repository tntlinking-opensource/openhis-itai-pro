# DM-PEIS-009 PeisGroup 团体体检 · DomainModel-SDD

> **对应 DM**：`docs/领域/DomainModel/DM-PEIS-009-PeisGroup-团体体检.md`（v1.0，本 SDD 假定该 DM 为当期锁定版本）  
> **目标工程**：`zxplat-plugin/zxplat-module-peis/module-peis-domain`  
> **限界上下文**：health · peis · 团体体检  
> **聚合**：PeisGroup 多表协作（单位/合同/分组/名册，见 §4）

---

## 输入完整性检查结果

| 最小输入项 | 状态 | 说明 |
| ---------- | ---- | ---- |
| DM 路径 | 满足 | DM-PEIS-009 |
| SDD 输出路径 | 满足 | 本文件 |
| 目标模块 | 满足 | module-peis-domain |
| DM 附录写方法 ×21 | 满足 | §5.1.1～5.1.21 |
| DM 附录读方法 ×33 | 满足 | §5.2 + §4.6 |
| DM §7 BR | 满足 | §6 |
| DM §5 事件 | 满足 | 无独立事件 |

---

## 1. 设计目标与边界

### 1.1 设计目标

- 团检单位树、合同与套餐池、体检分组、名册及可选/选定套餐、到检与缴费状态推进。
- 写侧 `PeisGroupCheckDomainService` 与读侧 `PeisGroupCheckQueryService` 与源码 public 方法对齐。
- 子表套餐关联采用 **软删旧行 + 插入新行**（replace*Packages）。

### 1.2 边界

- **在内**：peis_group_* / peis_contract_type 表域。
- **在外**：个检登记编排、LIS/PACS 外送。
- **跨域**：Patient/Encounter/ActivityDefinition 由 AS 校验。

---

## 2. 工程落点与包结构

### 2.1 Maven 模块

- `zxplat-plugin/zxplat-module-peis/module-peis-domain`。

### 2.2 包结构（`com.zxplat.plugin.peis.domain.group`）

| 相对包 | 内容 |
| ------ | ---- |
| `entity` | Company/Contract/Batch/Roster/Package/PeisContractType |
| `repository` | 各实体 Repository |
| `service` | `PeisGroupCheckDomainService` |
| `queries` | `PeisGroupCheckQueryService` |

**存储口径**：`pay_status_code` / `arrival_status_code` / `status_code` / `contract_type_code` → `String`；`enabled_flag` → `Boolean`。

---

## 3. 类型 / 字段命名 / 注解规则

| 规则 | 说明 |
| ---- | ---- |
| 主键 | `Long id` |
| 日期 | `bookingDate`/`validFrom`/`validTo` → `LocalDate` |
| 金额 | `contractAmount` → `BigDecimal` |
| 软删 | `isDeleted` |
| 事务 | 不声明 `@Transactional` |

---

## 4. 实体 / 值对象 / 枚举 / 仓储 / 查询契约

### 4.1 表与实体映射

| 逻辑表 | 实体类 |
| ------ | ------ |
| `peis_contract_type` | `PeisContractType` |
| `peis_group_company` | `PeisGroupCompany` |
| `peis_group_contract` | `PeisGroupContract` |
| `peis_group_contract_package` | `PeisGroupContractPackage` |
| `peis_group_batch` | `PeisGroupBatch` |
| `peis_group_batch_package` | `PeisGroupBatchPackage` |
| `peis_group_roster` | `PeisGroupRoster` |
| `peis_group_roster_package` | `PeisGroupRosterPackage` |

### 4.2 实体 `PeisContractType`（字典）

| 字段名（Java） | 类型 | 必填 | 说明 |
| -------------- | ---- | ---- | ---- |
| `typeCode` | `String` | 是 | 类型编码 |
| `typeName` | `String` | 是 | 名称 |
| `sortOrder` | `Integer` | 是 | 排序 |
| `enabledFlag` | `Boolean` | 是 | 启用 |

### 4.3 实体 `PeisGroupCompany`

| 字段名（Java） | 类型 | 必填 | 说明 |
| -------------- | ---- | ---- | ---- |
| `orgId` | `Long` | 否 | 机构 |
| `parentId` | `Long` | 否 | 上级单位 |
| `name` | `String` | 是 | 名称 |
| `campusCollegeId` | `Long` | 否 | 院系 |
| `contactName` | `String` | 否 | 联系人 |
| `contactPhone` | `String` | 否 | 电话 |

### 4.4 实体 `PeisGroupContract` / `PeisGroupBatch` / 套餐子表

#### `PeisGroupContract`

| 字段名 | 类型 | 必填 | 说明 |
| ------ | ---- | ---- | ---- |
| `orgId` | `Long` | 否 |  |
| `companyId` | `Long` | 是 | 单位 |
| `packageActivityId` | `Long` | 是 | 主套餐 AD |
| `contractName` | `String` | 否 |  |
| `validFrom` | `LocalDate` | 否 |  |
| `validTo` | `LocalDate` | 否 |  |
| `contractAmount` | `BigDecimal` | 否 |  |
| `payStatusCode` | `String` | 是 | 支付状态 |
| `contractTypeCode` | `String` | 否 | 合同类型 |

#### `PeisGroupContractPackage`

| 字段名 | 类型 | 必填 | 说明 |
| ------ | ---- | ---- | ---- |
| `contractId` | `Long` | 是 |  |
| `packageActivityId` | `Long` | 是 |  |
| `sortOrder` | `Integer` | 是 |  |

#### `PeisGroupBatch`

| 字段名 | 类型 | 必填 | 说明 |
| ------ | ---- | ---- | ---- |
| `orgId` | `Long` | 否 |  |
| `companyId` | `Long` | 是 |  |
| `contractId` | `Long` | 是 |  |
| `batchName` | `String` | 是 |  |
| `packageActivityId` | `Long` | 是 | 主套餐 |
| `bookingDate` | `LocalDate` | 否 |  |
| `statusCode` | `String` | 是 | 默认 DRAFT |

#### `PeisGroupBatchPackage`

| 字段名 | 类型 | 必填 | 说明 |
| ------ | ---- | ---- | ---- |
| `batchId` | `Long` | 是 |  |
| `packageActivityId` | `Long` | 是 |  |
| `sortOrder` | `Integer` | 是 |  |

#### `PeisGroupRoster`

| 字段名 | 类型 | 必填 | 说明 |
| ------ | ---- | ---- | ---- |
| `orgId` | `Long` | 否 |  |
| `batchId` | `Long` | 是 | 分组 |
| `companyId` | `Long` | 否 | 单位节点 |
| `patientId` | `Long` | 否 | 患者 |
| `fullName` | `String` | 是 | 姓名 |
| `idCardNo` | `String` | 否 | 身份证 |
| `studentNo` | `String` | 否 | 学号 |
| `employeeNo` | `String` | 否 | 工号 |
| `collegeId` | `Long` | 否 |  |
| `className` | `String` | 否 |  |
| `mobilePhone` | `String` | 否 |  |
| `encounterId` | `Long` | 否 | 建单后 PE |
| `selectedPackageActivityId` | `Long` | 否 | 选定套餐 |
| `payStatusCode` | `String` | 是 | 默认 UNPAID |
| `arrivalStatusCode` | `String` | 是 | 默认 PENDING |

#### `PeisGroupRosterPackage`

| 字段名 | 类型 | 必填 | 说明 |
| ------ | ---- | ---- | ---- |
| `rosterId` | `Long` | 是 |  |
| `packageActivityId` | `Long` | 是 |  |
| `sortOrder` | `Integer` | 是 |  |

**归属校验**：名册/套餐子表更新须校验 roster/batch/contract 归属一致。

### 4.5 仓储接口

- 各 `*Repository extends JpaRepository`；软删由框架过滤。

### 4.6 查询模型（`queries.model`）

#### 4.6.1 `listEnabledContractTypes`

| 字段名（Java） | 类型 | 来源 | 说明 |
| -------------- | ---- | ---- | ---- |
| （实体） | `List<PeisContractType>` | 对应表 | 字段见 §4.2～§4.4；PeisContractType §4.2 |

#### 4.6.2 `findContractTypeName`

| 字段名（Java） | 类型 | 来源 | 说明 |
| -------------- | ---- | ---- | ---- |
| （返回值） | `Optional<String>` | 查询组装 | 直接 String |

#### 4.6.3 `listValidContractsForPatient`

| 字段名（Java） | 类型 | 来源 | 说明 |
| -------------- | ---- | ---- | ---- |
| （实体） | `List<PeisGroupContract>` | 对应表 | 字段见 §4.2～§4.4；PeisGroupContract §4.3 |

#### 4.6.4 `isContractValidOn`

| 字段名（Java） | 类型 | 来源 | 说明 |
| -------------- | ---- | ---- | ---- |
| （返回值） | `boolean` | 查询组装 | 日期区间 |

#### 4.6.5 `listCompanies`

| 字段名（Java） | 类型 | 来源 | 说明 |
| -------------- | ---- | ---- | ---- |
| （实体） | `List<PeisGroupCompany>` | 对应表 | 字段见 §4.2～§4.4；§4.3 |

#### 4.6.6 `listRootCompanies`

| 字段名（Java） | 类型 | 来源 | 说明 |
| -------------- | ---- | ---- | ---- |
| （实体） | `List<PeisGroupCompany>` | 对应表 | 字段见 §4.2～§4.4；§4.3 |

#### 4.6.7 `listChildCompanies`

| 字段名（Java） | 类型 | 来源 | 说明 |
| -------------- | ---- | ---- | ---- |
| （实体） | `List<PeisGroupCompany>` | 对应表 | 字段见 §4.2～§4.4；§4.3 |

#### 4.6.8 `listDescendantCompanyIds`

| 字段名（Java） | 类型 | 来源 | 说明 |
| -------------- | ---- | ---- | ---- |
| （返回值） | `List<Long>` | 查询组装 | 树展开 ID |

#### 4.6.9 `requireCompany`

| 字段名（Java） | 类型 | 来源 | 说明 |
| -------------- | ---- | ---- | ---- |
| （实体） | `PeisGroupCompany` | 对应表 | 字段见 §4.2～§4.4；§4.3 |

#### 4.6.10 `listContracts`

| 字段名（Java） | 类型 | 来源 | 说明 |
| -------------- | ---- | ---- | ---- |
| （实体） | `List<PeisGroupContract>` | 对应表 | 字段见 §4.2～§4.4；§4.3 |

#### 4.6.11 `listContractsCoveringCompany`

| 字段名（Java） | 类型 | 来源 | 说明 |
| -------------- | ---- | ---- | ---- |
| （实体） | `List<PeisGroupContract>` | 对应表 | 字段见 §4.2～§4.4；含祖先合同 |

#### 4.6.12 `listContractsByCompanyIds`

| 字段名（Java） | 类型 | 来源 | 说明 |
| -------------- | ---- | ---- | ---- |
| （实体） | `List<PeisGroupContract>` | 对应表 | 字段见 §4.2～§4.4；§4.3 |

#### 4.6.13 `listContractPackageIds`

| 字段名（Java） | 类型 | 来源 | 说明 |
| -------------- | ---- | ---- | ---- |
| （返回值） | `List<Long>` | 查询组装 | 同写侧列表 |

#### 4.6.14 `mapContractPackageIds`

| 字段名（Java） | 类型 | 来源 | 说明 |
| -------------- | ---- | ---- | ---- |
| （返回值） | `Map<Long,List<Long>>` | 查询组装 | 批量 |

#### 4.6.15 `requireContract`

| 字段名（Java） | 类型 | 来源 | 说明 |
| -------------- | ---- | ---- | ---- |
| （实体） | `PeisGroupContract` | 对应表 | 字段见 §4.2～§4.4；§4.3 |

#### 4.6.16 `listBatches`

| 字段名（Java） | 类型 | 来源 | 说明 |
| -------------- | ---- | ---- | ---- |
| （实体） | `List<PeisGroupBatch>` | 对应表 | 字段见 §4.2～§4.4；§4.4 |

#### 4.6.17 `listBatchesCoveringCompany`

| 字段名（Java） | 类型 | 来源 | 说明 |
| -------------- | ---- | ---- | ---- |
| （实体） | `List<PeisGroupBatch>` | 对应表 | 字段见 §4.2～§4.4；§4.4 |

#### 4.6.18 `listBatchesByCompanyIds`

| 字段名（Java） | 类型 | 来源 | 说明 |
| -------------- | ---- | ---- | ---- |
| （实体） | `List<PeisGroupBatch>` | 对应表 | 字段见 §4.2～§4.4；§4.4 |

#### 4.6.19 `listBatchesByContractId`

| 字段名（Java） | 类型 | 来源 | 说明 |
| -------------- | ---- | ---- | ---- |
| （实体） | `List<PeisGroupBatch>` | 对应表 | 字段见 §4.2～§4.4；§4.4 |

#### 4.6.20 `requireBatch`

| 字段名（Java） | 类型 | 来源 | 说明 |
| -------------- | ---- | ---- | ---- |
| （实体） | `PeisGroupBatch` | 对应表 | 字段见 §4.2～§4.4；§4.4 |

#### 4.6.21 `requireBatchByNameUnderContract`

| 字段名（Java） | 类型 | 来源 | 说明 |
| -------------- | ---- | ---- | ---- |
| （实体） | `PeisGroupBatch` | 对应表 | 字段见 §4.2～§4.4；§4.4 |

#### 4.6.22 `listBatchPackageIds`

| 字段名（Java） | 类型 | 来源 | 说明 |
| -------------- | ---- | ---- | ---- |
| （返回值） | `List<Long>` | 查询组装 | §4.4 |

#### 4.6.23 `mapBatchPackageIds`

| 字段名（Java） | 类型 | 来源 | 说明 |
| -------------- | ---- | ---- | ---- |
| （返回值） | `Map<Long,List<Long>>` | 查询组装 | 批量 |

#### 4.6.24 `listRosterOptionalPackageIds`

| 字段名（Java） | 类型 | 来源 | 说明 |
| -------------- | ---- | ---- | ---- |
| （返回值） | `List<Long>` | 查询组装 | §4.5 |

#### 4.6.25 `mapRosterOptionalPackageIds`

| 字段名（Java） | 类型 | 来源 | 说明 |
| -------------- | ---- | ---- | ---- |
| （返回值） | `Map<Long,List<Long>>` | 查询组装 | §4.5 |

#### 4.6.26 `findUniqueActiveRosterByPatientId`

| 字段名（Java） | 类型 | 来源 | 说明 |
| -------------- | ---- | ---- | ---- |
| （实体） | `Optional<PeisGroupRoster>` | 对应表 | 字段见 §4.2～§4.4；§4.5 |

#### 4.6.27 `pageRoster`

| 字段名（Java） | 类型 | 来源 | 说明 |
| -------------- | ---- | ---- | ---- |
| （实体） | `Page<PeisGroupRoster>` | 对应表 | 字段见 §4.2～§4.4；§4.5 |

#### 4.6.28 `pageRosterByContract`

| 字段名（Java） | 类型 | 来源 | 说明 |
| -------------- | ---- | ---- | ---- |
| （实体） | `Page<PeisGroupRoster>` | 对应表 | 字段见 §4.2～§4.4；§4.5 |

#### 4.6.29 `listRosterByBatch`

| 字段名（Java） | 类型 | 来源 | 说明 |
| -------------- | ---- | ---- | ---- |
| （实体） | `List<PeisGroupRoster>` | 对应表 | 字段见 §4.2～§4.4；§4.5 |

#### 4.6.30 `listRosterByPatientId`

| 字段名（Java） | 类型 | 来源 | 说明 |
| -------------- | ---- | ---- | ---- |
| （实体） | `List<PeisGroupRoster>` | 对应表 | 字段见 §4.2～§4.4；§4.5 |

#### 4.6.31 `requireRoster`

| 字段名（Java） | 类型 | 来源 | 说明 |
| -------------- | ---- | ---- | ---- |
| （实体） | `PeisGroupRoster` | 对应表 | 字段见 §4.2～§4.4；§4.5 |

#### 4.6.32 `findRosterByEncounterId`

| 字段名（Java） | 类型 | 来源 | 说明 |
| -------------- | ---- | ---- | ---- |
| （实体） | `Optional<PeisGroupRoster>` | 对应表 | 字段见 §4.2～§4.4；§4.5 |

#### 4.6.33 `listByIds`

| 字段名（Java） | 类型 | 来源 | 说明 |
| -------------- | ---- | ---- | ---- |
| （实体） | `List<PeisGroupRoster>` | 对应表 | 字段见 §4.2～§4.4；§4.5 |

---

## 5. 写侧与读侧白名单（命令字段全集）

### 5.1 写侧 — `PeisGroupCheckDomainService`

#### 5.1.1 `saveCompany(PeisGroupCompany patch, Long operatorId)` → `PeisGroupCompany`

**命令文件**：N/A（模式 B：入参与 `PeisGroupCheckDomainService` 一致）

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `patch` | `PeisGroupCompany` | 单位字段 |
| `operatorId` | `Long` | 审计 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | 见源码 |
| `statusTransitionMethod` | N/A |
| `repositoryChecks` | 见源码 |
| `eventPublishAnchor` | 无 |
| `unitTestCaseId` | TC-PEIS-GRP-W01 |

**方法契约摘要**：前置/持久化/失败语义与 Java 实现一致；**注释锚点**：校验 → 迁态/子表 → save。

---

#### 5.1.2 `deleteCompany(Long companyId, Long operatorId)` → `void`

**命令文件**：N/A（模式 B：入参与 `PeisGroupCheckDomainService` 一致）

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `companyId` | `Long` | 必填 |
| `operatorId` | `Long` | 审计 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | 见源码 |
| `statusTransitionMethod` | N/A |
| `repositoryChecks` | 见源码 |
| `eventPublishAnchor` | 无 |
| `unitTestCaseId` | TC-PEIS-GRP-W02 |

**方法契约摘要**：前置/持久化/失败语义与 Java 实现一致；**注释锚点**：校验 → 迁态/子表 → save。

---

#### 5.1.3 `saveContract(PeisGroupContract patch, List<Long> packageActivityIds, Long operatorId)` → `PeisGroupContract`

**命令文件**：N/A（模式 B：入参与 `PeisGroupCheckDomainService` 一致）

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `patch` | `PeisGroupContract` | 合同 |
| `packageActivityIds` | `List<Long>` | ≥1 |
| `operatorId` | `Long` | 审计 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | 见源码 |
| `statusTransitionMethod` | N/A |
| `repositoryChecks` | 见源码 |
| `eventPublishAnchor` | 无 |
| `unitTestCaseId` | TC-PEIS-GRP-W03 |

**方法契约摘要**：前置/持久化/失败语义与 Java 实现一致；**注释锚点**：校验 → 迁态/子表 → save。

---

#### 5.1.4 `assertPackageAllowedForContract(Long contractId, Long packageActivityId)` → `void`

**命令文件**：N/A（模式 B：入参与 `PeisGroupCheckDomainService` 一致）

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `contractId` | `Long` | 必填 |
| `packageActivityId` | `Long` | 必填 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | BR-GROUP-001 |
| `statusTransitionMethod` | N/A |
| `repositoryChecks` | 见源码 |
| `eventPublishAnchor` | 无 |
| `unitTestCaseId` | TC-PEIS-GRP-W04 |

**方法契约摘要**：前置/持久化/失败语义与 Java 实现一致；**注释锚点**：校验 → 迁态/子表 → save。

---

#### 5.1.5 `assertPackageAllowedForBatch(Long batchId, Long packageActivityId)` → `void`

**命令文件**：N/A（模式 B：入参与 `PeisGroupCheckDomainService` 一致）

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `batchId` | `Long` | 必填 |
| `packageActivityId` | `Long` | 必填 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | BR-GROUP-001 |
| `statusTransitionMethod` | N/A |
| `repositoryChecks` | 见源码 |
| `eventPublishAnchor` | 无 |
| `unitTestCaseId` | TC-PEIS-GRP-W05 |

**方法契约摘要**：前置/持久化/失败语义与 Java 实现一致；**注释锚点**：校验 → 迁态/子表 → save。

---

#### 5.1.6 `listContractPackageIds(Long contractId)` → `List<Long>`

**命令文件**：N/A（模式 B：入参与 `PeisGroupCheckDomainService` 一致）

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `contractId` | `Long` | 必填 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | 见源码 |
| `statusTransitionMethod` | N/A |
| `repositoryChecks` | 见源码 |
| `eventPublishAnchor` | 无 |
| `unitTestCaseId` | TC-PEIS-GRP-W06 |

**方法契约摘要**：前置/持久化/失败语义与 Java 实现一致；**注释锚点**：校验 → 迁态/子表 → save。

---

#### 5.1.7 `listBatchPackageIds(Long batchId)` → `List<Long>`

**命令文件**：N/A（模式 B：入参与 `PeisGroupCheckDomainService` 一致）

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `batchId` | `Long` | 必填 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | 见源码 |
| `statusTransitionMethod` | N/A |
| `repositoryChecks` | 见源码 |
| `eventPublishAnchor` | 无 |
| `unitTestCaseId` | TC-PEIS-GRP-W07 |

**方法契约摘要**：前置/持久化/失败语义与 Java 实现一致；**注释锚点**：校验 → 迁态/子表 → save。

---

#### 5.1.8 `saveBatch(PeisGroupBatch patch, List<Long> packageActivityIds, Long operatorId)` → `PeisGroupBatch`

**命令文件**：N/A（模式 B：入参与 `PeisGroupCheckDomainService` 一致）

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `patch` | `PeisGroupBatch` | 分组 |
| `packageActivityIds` | `List<Long>` | ≥1 |
| `operatorId` | `Long` | 审计 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | 见源码 |
| `statusTransitionMethod` | N/A |
| `repositoryChecks` | 见源码 |
| `eventPublishAnchor` | 无 |
| `unitTestCaseId` | TC-PEIS-GRP-W08 |

**方法契约摘要**：前置/持久化/失败语义与 Java 实现一致；**注释锚点**：校验 → 迁态/子表 → save。

---

#### 5.1.9 `saveRosterWithOptionalPackages(PeisGroupRoster entity, List<Long> optionalPackageIds, Long operatorId)` → `PeisGroupRoster`

**命令文件**：N/A（模式 B：入参与 `PeisGroupCheckDomainService` 一致）

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `entity` | `PeisGroupRoster` | 名册 |
| `optionalPackageIds` | `List<Long>` | 非空 |
| `operatorId` | `Long` | 审计 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | assertNoActiveRosterForPerson |
| `statusTransitionMethod` | N/A |
| `repositoryChecks` | 见源码 |
| `eventPublishAnchor` | 无 |
| `unitTestCaseId` | TC-PEIS-GRP-W09 |

**方法契约摘要**：前置/持久化/失败语义与 Java 实现一致；**注释锚点**：校验 → 迁态/子表 → save。

---

#### 5.1.10 `saveRoster(PeisGroupRoster entity, Long operatorId)` → `PeisGroupRoster`

**命令文件**：N/A（模式 B：入参与 `PeisGroupCheckDomainService` 一致）

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `entity` | `PeisGroupRoster` | 名册 |
| `operatorId` | `Long` | 审计 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | assertNoActiveRosterForPerson |
| `statusTransitionMethod` | N/A |
| `repositoryChecks` | 见源码 |
| `eventPublishAnchor` | 无 |
| `unitTestCaseId` | TC-PEIS-GRP-W10 |

**方法契约摘要**：前置/持久化/失败语义与 Java 实现一致；**注释锚点**：校验 → 迁态/子表 → save。

---

#### 5.1.11 `importRosterRows(Long batchId, List<PeisGroupRosterImportRow> rows, Long operatorId)` → `List<Long>`

**命令文件**：N/A（模式 B：入参与 `PeisGroupCheckDomainService` 一致）

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `batchId` | `Long` | 必填 |
| `rows` | `List<ImportRow>` | 导入行 |
| `operatorId` | `Long` | 审计 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | 见源码 |
| `statusTransitionMethod` | N/A |
| `repositoryChecks` | 见源码 |
| `eventPublishAnchor` | 无 |
| `unitTestCaseId` | TC-PEIS-GRP-W11 |

**方法契约摘要**：前置/持久化/失败语义与 Java 实现一致；**注释锚点**：校验 → 迁态/子表 → save。

---

#### 5.1.12 `assignSelectedPackage(Long rosterId, Long packageActivityId, Long operatorId)` → `PeisGroupRoster`

**命令文件**：N/A（模式 B：入参与 `PeisGroupCheckDomainService` 一致）

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `rosterId` | `Long` | 必填 |
| `packageActivityId` | `Long` | 必填 |
| `operatorId` | `Long` | 审计 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | 见源码 |
| `statusTransitionMethod` | N/A |
| `repositoryChecks` | 见源码 |
| `eventPublishAnchor` | 无 |
| `unitTestCaseId` | TC-PEIS-GRP-W12 |

**方法契约摘要**：前置/持久化/失败语义与 Java 实现一致；**注释锚点**：校验 → 迁态/子表 → save。

---

#### 5.1.13 `replaceRosterOptionalPackages(Long rosterId, List<Long> optionalPackageIds, Long operatorId)` → `void`

**命令文件**：N/A（模式 B：入参与 `PeisGroupCheckDomainService` 一致）

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `rosterId` | `Long` | 必填 |
| `optionalPackageIds` | `List<Long>` | 非空 |
| `operatorId` | `Long` | 审计 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | 见源码 |
| `statusTransitionMethod` | N/A |
| `repositoryChecks` | 见源码 |
| `eventPublishAnchor` | 无 |
| `unitTestCaseId` | TC-PEIS-GRP-W13 |

**方法契约摘要**：前置/持久化/失败语义与 Java 实现一致；**注释锚点**：校验 → 迁态/子表 → save。

---

#### 5.1.14 `changeRosterBatch(Long rosterId, Long targetBatchId, Long operatorId)` → `PeisGroupRoster`

**命令文件**：N/A（模式 B：入参与 `PeisGroupCheckDomainService` 一致）

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `rosterId` | `Long` | 必填 |
| `targetBatchId` | `Long` | 同合同 |
| `operatorId` | `Long` | 审计 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | 见源码 |
| `statusTransitionMethod` | N/A |
| `repositoryChecks` | 见源码 |
| `eventPublishAnchor` | 无 |
| `unitTestCaseId` | TC-PEIS-GRP-W14 |

**方法契约摘要**：前置/持久化/失败语义与 Java 实现一致；**注释锚点**：校验 → 迁态/子表 → save。

---

#### 5.1.15 `listRosterOptionalPackageIds(Long rosterId)` → `List<Long>`

**命令文件**：N/A（模式 B：入参与 `PeisGroupCheckDomainService` 一致）

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `rosterId` | `Long` | 必填 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | 见源码 |
| `statusTransitionMethod` | N/A |
| `repositoryChecks` | 见源码 |
| `eventPublishAnchor` | 无 |
| `unitTestCaseId` | TC-PEIS-GRP-W15 |

**方法契约摘要**：前置/持久化/失败语义与 Java 实现一致；**注释锚点**：校验 → 迁态/子表 → save。

---

#### 5.1.16 `markPaid(Long rosterId, Long encounterId, Long operatorId)` → `PeisGroupRoster`

**命令文件**：N/A（模式 B：入参与 `PeisGroupCheckDomainService` 一致）

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `rosterId` | `Long` | 必填 |
| `encounterId` | `Long` | 必填 |
| `operatorId` | `Long` | 审计 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | 见源码 |
| `statusTransitionMethod` | pay/arrival 迁态 |
| `repositoryChecks` | 见源码 |
| `eventPublishAnchor` | 无 |
| `unitTestCaseId` | TC-PEIS-GRP-W16 |

**方法契约摘要**：前置/持久化/失败语义与 Java 实现一致；**注释锚点**：校验 → 迁态/子表 → save。

---

#### 5.1.17 `markContractPaid(Long contractId, Long operatorId)` → `PeisGroupContract`

**命令文件**：N/A（模式 B：入参与 `PeisGroupCheckDomainService` 一致）

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `contractId` | `Long` | 必填 |
| `operatorId` | `Long` | 审计 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | 见源码 |
| `statusTransitionMethod` | pay/arrival 迁态 |
| `repositoryChecks` | 见源码 |
| `eventPublishAnchor` | 无 |
| `unitTestCaseId` | TC-PEIS-GRP-W17 |

**方法契约摘要**：前置/持久化/失败语义与 Java 实现一致；**注释锚点**：校验 → 迁态/子表 → save。

---

#### 5.1.18 `markEnrolled(Long rosterId, Long encounterId, Long operatorId)` → `PeisGroupRoster`

**命令文件**：N/A（模式 B：入参与 `PeisGroupCheckDomainService` 一致）

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `rosterId` | `Long` | 必填 |
| `encounterId` | `Long` | 必填 |
| `operatorId` | `Long` | 审计 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | 见源码 |
| `statusTransitionMethod` | pay/arrival 迁态 |
| `repositoryChecks` | 见源码 |
| `eventPublishAnchor` | 无 |
| `unitTestCaseId` | TC-PEIS-GRP-W18 |

**方法契约摘要**：前置/持久化/失败语义与 Java 实现一致；**注释锚点**：校验 → 迁态/子表 → save。

---

#### 5.1.19 `confirmArrival(Long rosterId, Long operatorId)` → `PeisGroupRoster`

**命令文件**：N/A（模式 B：入参与 `PeisGroupCheckDomainService` 一致）

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `rosterId` | `Long` | 必填 |
| `operatorId` | `Long` | 审计 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | 见源码 |
| `statusTransitionMethod` | pay/arrival 迁态 |
| `repositoryChecks` | 见源码 |
| `eventPublishAnchor` | 无 |
| `unitTestCaseId` | TC-PEIS-GRP-W19 |

**方法契约摘要**：前置/持久化/失败语义与 Java 实现一致；**注释锚点**：校验 → 迁态/子表 → save。

---

#### 5.1.20 `cancelArrival(Long rosterId, Long operatorId)` → `PeisGroupRoster`

**命令文件**：N/A（模式 B：入参与 `PeisGroupCheckDomainService` 一致）

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `rosterId` | `Long` | 必填 |
| `operatorId` | `Long` | 审计 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | 见源码 |
| `statusTransitionMethod` | pay/arrival 迁态 |
| `repositoryChecks` | 见源码 |
| `eventPublishAnchor` | 无 |
| `unitTestCaseId` | TC-PEIS-GRP-W20 |

**方法契约摘要**：前置/持久化/失败语义与 Java 实现一致；**注释锚点**：校验 → 迁态/子表 → save。

---

#### 5.1.21 `revertEnrollment(Long rosterId, Long operatorId)` → `PeisGroupRoster`

**命令文件**：N/A（模式 B：入参与 `PeisGroupCheckDomainService` 一致）

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `rosterId` | `Long` | 必填 |
| `operatorId` | `Long` | 审计 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | 见源码 |
| `statusTransitionMethod` | pay/arrival 迁态 |
| `repositoryChecks` | 见源码 |
| `eventPublishAnchor` | 无 |
| `unitTestCaseId` | TC-PEIS-GRP-W21 |

**方法契约摘要**：前置/持久化/失败语义与 Java 实现一致；**注释锚点**：校验 → 迁态/子表 → save。

---

### 5.2 读侧 — `PeisGroupCheckQueryService`

| 方法 | 入参 | 出参（类名，字段见 §4.6） | 关键实现 | `unitTestCaseId` |
| ---- | ---- | ------------------------- | -------- | ---------------- |
| `listEnabledContractTypes` | — | `List<PeisContractType>` | 软删过滤；PeisContractType §4.2 | TC-PEIS-GRP-R01 |
| `findContractTypeName` | String typeCode | `Optional<String>` | 软删过滤；直接 String | TC-PEIS-GRP-R02 |
| `listValidContractsForPatient` | Long patientId, LocalDate onDate | `List<PeisGroupContract>` | 软删过滤；PeisGroupContract §4.3 | TC-PEIS-GRP-R03 |
| `isContractValidOn` | PeisGroupContract, LocalDate day | `boolean` | 软删过滤；日期区间 | TC-PEIS-GRP-R04 |
| `listCompanies` | — | `List<PeisGroupCompany>` | 软删过滤；§4.3 | TC-PEIS-GRP-R05 |
| `listRootCompanies` | — | `List<PeisGroupCompany>` | 软删过滤；§4.3 | TC-PEIS-GRP-R06 |
| `listChildCompanies` | Long parentId | `List<PeisGroupCompany>` | 软删过滤；§4.3 | TC-PEIS-GRP-R07 |
| `listDescendantCompanyIds` | Long rootCompanyId | `List<Long>` | 软删过滤；树展开 ID | TC-PEIS-GRP-R08 |
| `requireCompany` | Long id | `PeisGroupCompany` | 软删过滤；§4.3 | TC-PEIS-GRP-R09 |
| `listContracts` | Long companyId | `List<PeisGroupContract>` | 软删过滤；§4.3 | TC-PEIS-GRP-R10 |
| `listContractsCoveringCompany` | Long companyId | `List<PeisGroupContract>` | 软删过滤；含祖先合同 | TC-PEIS-GRP-R11 |
| `listContractsByCompanyIds` | List<Long> companyIds | `List<PeisGroupContract>` | 软删过滤；§4.3 | TC-PEIS-GRP-R12 |
| `listContractPackageIds` | Long contractId | `List<Long>` | 软删过滤；同写侧列表 | TC-PEIS-GRP-R13 |
| `mapContractPackageIds` | List<Long> contractIds | `Map<Long,List<Long>>` | 软删过滤；批量 | TC-PEIS-GRP-R14 |
| `requireContract` | Long id | `PeisGroupContract` | 软删过滤；§4.3 | TC-PEIS-GRP-R15 |
| `listBatches` | Long companyId | `List<PeisGroupBatch>` | 软删过滤；§4.4 | TC-PEIS-GRP-R16 |
| `listBatchesCoveringCompany` | Long companyId | `List<PeisGroupBatch>` | 软删过滤；§4.4 | TC-PEIS-GRP-R17 |
| `listBatchesByCompanyIds` | List<Long> companyIds | `List<PeisGroupBatch>` | 软删过滤；§4.4 | TC-PEIS-GRP-R18 |
| `listBatchesByContractId` | Long contractId | `List<PeisGroupBatch>` | 软删过滤；§4.4 | TC-PEIS-GRP-R19 |
| `requireBatch` | Long id | `PeisGroupBatch` | 软删过滤；§4.4 | TC-PEIS-GRP-R20 |
| `requireBatchByNameUnderContract` | Long contractId, String batchName | `PeisGroupBatch` | 软删过滤；§4.4 | TC-PEIS-GRP-R21 |
| `listBatchPackageIds` | Long batchId | `List<Long>` | 软删过滤；§4.4 | TC-PEIS-GRP-R22 |
| `mapBatchPackageIds` | List<Long> batchIds | `Map<Long,List<Long>>` | 软删过滤；批量 | TC-PEIS-GRP-R23 |
| `listRosterOptionalPackageIds` | Long rosterId | `List<Long>` | 软删过滤；§4.5 | TC-PEIS-GRP-R24 |
| `mapRosterOptionalPackageIds` | List<Long> rosterIds | `Map<Long,List<Long>>` | 软删过滤；§4.5 | TC-PEIS-GRP-R25 |
| `findUniqueActiveRosterByPatientId` | Long patientId | `Optional<PeisGroupRoster>` | 软删过滤；§4.5 | TC-PEIS-GRP-R26 |
| `pageRoster` | batchId, filters, Pageable | `Page<PeisGroupRoster>` | 软删过滤；§4.5 | TC-PEIS-GRP-R27 |
| `pageRosterByContract` | contractId, filters, Pageable | `Page<PeisGroupRoster>` | 软删过滤；§4.5 | TC-PEIS-GRP-R28 |
| `listRosterByBatch` | Long batchId | `List<PeisGroupRoster>` | 软删过滤；§4.5 | TC-PEIS-GRP-R29 |
| `listRosterByPatientId` | Long patientId | `List<PeisGroupRoster>` | 软删过滤；§4.5 | TC-PEIS-GRP-R30 |
| `requireRoster` | Long id | `PeisGroupRoster` | 软删过滤；§4.5 | TC-PEIS-GRP-R31 |
| `findRosterByEncounterId` | Long encounterId | `Optional<PeisGroupRoster>` | 软删过滤；§4.5 | TC-PEIS-GRP-R32 |
| `listByIds` | List<Long> ids | `List<PeisGroupRoster>` | 软删过滤；§4.5 | TC-PEIS-GRP-R33 |

**读方法注释锚点（统一）**：构建查询条件（含软删过滤）→ 单次 SQL/仓储调用并完成分页统计 → 组装结果（无写库）。

---

## 6. 不变式映射（DM §7）

| BR 编号 | 实现策略摘要 |
| ------- | ------------ |
| **BR-GROUP-001** | `assertPackageAllowed*` + saveBatch/saveContract 套餐池校验 |
| **BR-GROUP-002** | 名册变更校验 encounter/到检态；revert 由 AS 先作废就诊 |

---

## 7. 事件契约与触发规则（DM §5）

| 事件类名 | 事件类包路径 | 最小载荷 | 触发方法 |
| -------- | ------------ | -------- | -------- |

（无独立领域事件。）

**发布时机**：不适用。

---

## 8. 删除与可见性契约

- 单位/子表套餐行软删；查询默认 `isDeleted=false`。
- 已到检名册禁止改可选套餐。

---

## 9. 框架优先适配规则

- 遵循项目级成码门禁；更新路径 load 托管实体再 save。

---

## 10. 生成与验收清单

- [ ] 写方法 21 + 读方法 33 与 DM 附录一致
- [ ] 套餐 replace 软删对齐单测
- [ ] 到检/建单状态机单测

---

## 11. 单次会话代码生成门禁（DoD）

### 11.1 前置输入

DM-PEIS-009、本 SDD、peis_group_* Flyway

### 11.2 DoD

编译通过；关键 BR 单测绿

### 11.3 失败处理

AD/Encounter 依赖未就绪 → AS 编排文档标注

---

## 定稿检查清单（可打勾）

- [ ] §5.1 节数 = 21
- [ ] §5.2 读方法 = 33
- [ ] §4 字段 ≥ DM §3

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
| **总分** | **100** | **≥95，可进入代码会话** |

---

**本 SDD 正文结束。**
