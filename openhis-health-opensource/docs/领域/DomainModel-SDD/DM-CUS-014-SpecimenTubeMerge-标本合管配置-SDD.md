# DM-CUS-014 SpecimenTubeMerge 标本合管配置 · DomainModel-SDD

> **对应 DM**：`docs/领域/DomainModel/DM-CUS-014-SpecimenTubeMerge-标本合管配置.md`（v1.2，本 SDD 假定该 DM 为当期锁定版本）  
> **目标工程**：`zxplat-core/zxplat-domain`  
> **限界上下文**：CUS 本地扩展 · 标本合管  
> **聚合**：SpecimenTubeMerge（单表；`groupNo` 合管组见 §4.2）

---

## 输入完整性检查结果

| 最小输入项 | 状态 | 说明 |
| ---------- | ---- | ---- |
| DM 路径 | 满足 | 已绑定 DM-CUS-014 |
| SDD 输出路径 | 满足 | 本文件 |
| 目标模块 | 满足 | `zxplat-domain` |
| 包规则 `com.zxplat.domain.custom.specimentubemerge` | 满足 | 全小写 |
| 固定技术口径 | 满足 | 见 §3、§9 |
| DM 写/读白名单 | 满足 | 附录 2 写 + 6 读；§5 全覆盖 |
| DM §4 BR | 满足 | §6 映射 BR-STM-001～003 及编排类 BR 说明 |
| DM 领域事件 | 满足 | 无事件类 |

**与 DM 显式差异（采纳依据：模式 B）**

| 点 | DM 差异位置 | SDD 采纳 |
| -- | ----------- | -------- |
| 派生绑定读 | §3 写应用 Support | `SpecimenTubeMergeBindResolveSupport#listDerivedBindsByDeviceIdAndGroupNo` 在应用层，不在本 QueryService |
| 采血费解析 | §2 写「采血费」唯一 activity | 应用层解析 `bloodCollectionFeeActivityId` 写入 `AlignSpecimenTubeMergeCommand` |
| 存量组号 | §1 V48 | 空 `groupNo` 读侧回落 `DEFAULT_GROUP_NO=1` |

---

## 1. 设计目标与边界

### 1.1 设计目标

- 固化 **设备 × 合管组 × 绑定目标** 配置模型；写侧 **`alignBindingsForDevice` 全量对齐全部组**（含整组删除）。
- 检验 activity（非采血费）**全局唯一**归属一组；采血费/耗材可跨设备、跨组复用。
- 读侧提供 **`SpecimenTubeMergeGroupQueryModel`（deviceId + groupNo）** 供开嘱/LIS 聚组。

### 1.2 边界

- **在内**：`cus_specimen_tube_merge` 对齐/删除、反查合管组。
- **在外**：采血费目录解析 UI、派生绑定列表、LIS medsync（BR-STM-011/012 应用编排）。
- **跨域读**：写侧设备存在性 `LabDeviceQueryService.getLabDevice`（B.6.4）。

---

## 2. 工程落点与包结构

### 2.1 Maven 模块

- 模块：`zxplat-domain`；Flyway V48 唯一索引 `uk_cus_specimen_tube_merge_device_group_target`。

### 2.2 包结构（`com.zxplat.domain.custom.specimentubemerge`）

| 相对包 | 内容 |
| ------ | ---- |
| `entity` | `SpecimenTubeMerge` |
| `command` | `AlignSpecimenTubeMergeCommand`、`AlignSpecimenTubeMergeLineCommand`、`DeleteDeviceBindingsCommand` |
| `repository` | `SpecimenTubeMergeRepository` |
| `service` | `SpecimenTubeMergeDomainService`、`SpecimenTubeMergeQueryService` |
| `queries.model` | `SpecimenTubeMergeLineQueryModel`、`SpecimenTubeMergeGroupQueryModel` |

**存储口径**：`target_table`/`unit_code` → `String`；`quantity` → `BigDecimal`；`group_no` → `Integer`（≥1）。

---

## 3. 类型 / 字段命名 / 注解规则

| 规则 | 说明 |
| ---- | ---- |
| 主键 / 外键 | `Long` deviceId/targetId |
| 合管组 | `groupNo` Integer，从 1 起 |
| 软删 | `markDeleted`；对齐时缺席行软删 |
| 事务 | 领域服务不声明 `@Transactional` |
| 事件 | 无 |

---

## 4. 实体 / 值对象 / 枚举 / 仓储 / 查询契约

### 4.1 表与实体映射

| 逻辑表 | 实体类 | 说明 |
| ------ | ------ | ---- |
| `cus_specimen_tube_merge` | `SpecimenTubeMerge` | 合管绑定行 |

### 4.2 聚合根 `SpecimenTubeMerge`（字段级契约）

| 字段名（Java） | 类型 | 必填 | 说明 / 约束 |
| -------------- | ---- | ---- | ----------- |
| `id` | `Long` | 系统 | 主键 |
| `deviceId` | `Long` | 是 | `adm_device.id` |
| `groupNo` | `Integer` | 是 | 设备内合管组，从 1 起 |
| `targetTable` | `String` | 是 | `wkf_activity_definition` / `adm_device_definition` |
| `targetId` | `Long` | 是 | 目标主键 |
| `quantity` | `BigDecimal` | 条件 | 耗材必填 >0；activity 须 null |
| `unitCode` | `String` | 条件 | 耗材必填；activity 须 null |
| `displayOrder` | `Integer` | 否 | 组内排序 |
| `isDeleted` | `Boolean` | 系统 | 软删 |

**不变式方法**：`assertRequiredFields`、`markDeleted`。

**合管组语义**：同 `deviceId + groupNo` 内检验项目合管；不同组不合管。展示名「组N」不落库。

### 4.3 子实体

无。本聚合为单表。

### 4.5 仓储接口

- `SpecimenTubeMergeRepository`
- `findByDeviceIdAndIsDeletedFalse`
- `findByDeviceIdAndGroupNoAndIsDeletedFalse`
- `findByTargetTableAndTargetIdInAndIsDeletedFalse`
- `existsByTargetTableAndTargetIdAndIsDeletedFalseAndDeviceIdNot`

### 4.6 查询模型（`queries.model`）

#### 4.6.1 `SpecimenTubeMergeLineQueryModel`（对应 `listByDeviceId` / `listByDeviceIdAndGroupNo`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `id` | `Long` | id | 行主键 |
| `deviceId` | `Long` | device_id | 设备 |
| `groupNo` | `Integer` | group_no | 合管组 |
| `targetTable` | `String` | target_table | 目标表 |
| `targetId` | `Long` | target_id | 目标 id |
| `quantity` | `BigDecimal` | quantity | 耗材数量 |
| `unitCode` | `String` | unit_code | 单位 |
| `displayOrder` | `Integer` | display_order | 排序 |

#### 4.6.2 `SpecimenTubeMergeGroupQueryModel`（对应 `findGroupRefByActivityId` / `mapGroupRefByActivityIds`）

| 字段名（Java） | 类型 | 来源 | 说明 |
| -------------- | ---- | ---- | ---- |
| `deviceId` | `Long` | 绑定行 | 合管设备 |
| `groupNo` | `Integer` | 绑定行 | 组序号 |

辅助方法：`of(deviceId, groupNo)`、`toGroupKey()`（`deviceId:groupNo`）。

#### 4.6.3 `Optional<Long>` / `Map<Long,Long>`（对应 `findDeviceIdByActivityId` / `mapDeviceIdByActivityIds`）

兼容包装：值为 `SpecimenTubeMergeGroupQueryModel.deviceId`；非唯一归属时 empty/不入 Map。

---

## 5. 写侧与读侧白名单（命令字段全集）

### 5.1 写侧 — `SpecimenTubeMergeDomainService`

#### 5.1.1 `alignBindingsForDevice(AlignSpecimenTubeMergeCommand command)` → `List<SpecimenTubeMerge>`

**命令文件**：`AlignSpecimenTubeMergeCommand.java`；行 `AlignSpecimenTubeMergeLineCommand.java`

**字段全集（命令头）**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `deviceId` | `Long` | 设备 |
| `operatorId` | `Long` | 软删审计 |
| `bloodCollectionFeeActivityId` | `Long` | 采血费 activity；该 id 不做全局唯一校验 |
| `lines` | `List<AlignSpecimenTubeMergeLineCommand>` | 全设备全组期望集；空列表=清空 |

**字段全集（行 `AlignSpecimenTubeMergeLineCommand`）**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `groupNo` | `Integer` | 合管组，≥1 |
| `targetTable` | `String` | 目标表 |
| `targetId` | `Long` | 目标 id |
| `quantity` | `BigDecimal` | 耗材数量 |
| `unitCode` | `String` | 耗材单位 |
| `displayOrder` | `Integer` | 排序 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | 行 `assertRequiredFields`；activity 强制 quantity/unit 清空 |
| `statusTransitionMethod` | N/A |
| `repositoryChecks` | `LabDeviceQueryService.getLabDevice`；`assertActivityNotBoundToOtherDevice` |
| `eventPublishAnchor` | N/A |
| `unitTestCaseId` | `TC-DM-CUS-014-W-001` |

**方法契约摘要**：前置设备存在；同组目标不重复；检验 activity（非采血费）同设备跨组不可重复且不可绑其它设备；键 `groupNo:targetTable:targetId` upsert；未覆盖旧行 `markDeleted`；返回仍有效行列表。**注释锚点**：设备校验 → 入参行校验 → 加载 existing → upsert → 软删缺席 → saveAll。

---

#### 5.1.2 `deleteDeviceBindings(DeleteDeviceBindingsCommand command)` → `void`

**命令文件**：`DeleteDeviceBindingsCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `deviceId` | `Long` | 设备 |
| `operatorId` | `Long` | 操作人 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `markDeleted` |
| `statusTransitionMethod` | N/A |
| `repositoryChecks` | 设备存在；无行幂等返回 |
| `eventPublishAnchor` | N/A |
| `unitTestCaseId` | `TC-DM-CUS-014-W-002` |

**方法契约摘要**：逻辑删除设备下全部组绑定。**注释锚点**：设备校验 → 加载 → markDeleted → saveAll。

### 5.2 读侧 — `SpecimenTubeMergeQueryService`

| 方法 | 入参 | 出参（§4.6） | 关键实现 | `unitTestCaseId` |
| ---- | ---- | ------------ | -------- | ---------------- |
| `listByDeviceId` | `Long deviceId` | `List<SpecimenTubeMergeLineQueryModel>` | 含全部组 | `TC-DM-CUS-014-R-001` |
| `listByDeviceIdAndGroupNo` | `Long deviceId`, `Integer groupNo` | 同上 | 单组 | `TC-DM-CUS-014-R-002` |
| `findDeviceIdByActivityId` | `Long activityId` | `Optional<Long>` | 委托 groupRef | `TC-DM-CUS-014-R-003` |
| `findGroupRefByActivityId` | `Long activityId` | `Optional<SpecimenTubeMergeGroupQueryModel>` | 唯一归属才返回 | `TC-DM-CUS-014-R-004` |
| `mapDeviceIdByActivityIds` | `Collection<Long>` | `Map<Long,Long>` | 多组/采血费污染时不写入 | `TC-DM-CUS-014-R-005` |
| `mapGroupRefByActivityIds` | `Collection<Long>` | `Map<Long,SpecimenTubeMergeGroupQueryModel>` | `resolveUniqueGroupRef` | `TC-DM-CUS-014-R-006` |

**读方法注释锚点（统一）**：软删过滤 → 仓储查询 → 唯一组解析 → 投影。

---

## 6. 不变式映射（DM §7 / §4）

| BR 编号 | 实现策略摘要 |
| ------- | ------------ |
| BR-STM-001 | `assertRequiredFields`：耗材 quantity>0 且 unitCode 非空 |
| BR-STM-002 | `requiresGlobalUniqueTarget` + `assertActivityNotBoundToOtherDevice`；采血费 id 排除 |
| BR-STM-003 | `seenTargetsInGroup` 同组键冲突拒绝 |
| BR-STM-010 | 应用层 `SpecimenTubeMergeBindResolveSupport` + 模板回落 |
| BR-STM-011 | 应用 LIS medsync 按「申请单 `prescription_no` + `deviceId+groupNo`」分桶聚组；跨申请单不合管 |
| BR-STM-012 | 应用报告查询：同申请单同合管组回落锚点 SR；跨申请单不串 PDF |

---

## 7. 事件契约与触发规则（DM §5）

| 事件类名 | 事件类包路径 | 最小载荷 | 触发方法 |
| -------- | ------------ | -------- | -------- |
| （无） | — | — | 写路径无领域事件 |

**发布时机**：不适用。

---

## 8. 删除与可见性契约

- 对齐缺席行与 `deleteDeviceBindings` 均逻辑删除。
- 读侧默认 `isDeleted=false`。
- 库唯一：`device_id + group_no + target_table + target_id`（未删）。

---

## 9. 框架优先适配规则

- 跨聚合仅 `LabDeviceQueryService` 读设备。
- 遵循项目级成码门禁。

---

## 10. 生成与验收清单

- [ ] V48 迁移与 `group_no`
- [ ] 两写方法 + 六读方法
- [ ] `SpecimenTubeMergeGroupQueryModel` 与聚组键
- [ ] 编译通过

---

## 11. 单次会话代码生成门禁（DoD）

### 11.1 前置输入

- DM-CUS-014 v1.2 + 本 SDD。

### 11.2 DoD

- `alignBindingsForDevice` 全组全量语义与 BR-STM-002 一致。

### 11.3 失败处理

- 采血费提示文案在应用层，领域层抛明确 BusinessException。

---

## 定稿检查清单（可打勾）

- [x] 写 2 / 读 6 = DM 附录
- [x] groupNo / GroupRef 已写清
- [x] §4.2 字段完整

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
| **总分** | **100** | **99，可进入代码会话** |

---

**本 SDD 正文结束。**
