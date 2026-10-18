# DM-LIS-006 QcMaterial 质控品 · DomainModel-SDD

> **对应 DM**：`docs/领域/缺口/文档无代码/DM-LIS-006-QcMaterial-质控品.md`（v1.0，本 SDD 假定该 DM 为当期锁定版本）
> **目标工程：`zxplat``
> **限界上下文**：检验域 · 质控管理
> **聚合**：QcMaterial（子实体：QcRun、QcMeasurement 见 §4）

---

## 输入完整性检查结果

| 最小输入项 | 状态 | 说明 |
| ---------- | ---- | ---- |
| DM 路径 | 满足 | 已绑定 DM-LIS-006 |
| SDD 输出路径 | 满足 | 本文件 |
| 目标模块 | 满足 | `zxplat/zxplat-domain` |
| 包规则 `com.zxplat.domain.lis.qcmaterial` | 满足 | `qcmaterial` 全小写 |
| 固定技术口径（ID Long、时间 LocalDateTime、软删、beforeCommit、分页等） | 满足 | 见 §9 |
| DM §4 业务规则 | 满足 | §6 映射（BR-QC-01~02），§5 写方法由 §1 职责推导 |
| DM 持久化形态 | 满足 | §4 字段表对齐（主表 + run + measurement） |
| DM 领域事件 | 满足 | 无（DM 未定义事件） |

---

## 1. 设计目标与边界

### 1.1 设计目标

- 在单一文档内固化 **QcMaterial 聚合** 的持久化模型、领域服务契约、查询契约、不变式实现策略，使下一会话可 **一次性** 生成 `zxplat/zxplat-domain` 领域层实现并通过编译。
- 将「实现前澄清」前置到 SDD：**每个写方法对应独立 Command 文件**，命令字段全集列出；子表同步（QcMeasurement）采用 **全量对齐 + 软删旧行**。
- **单次成稿目标**：正文不出现「后续补充」「待确认」类措辞；缺口须在文档阶段关闭。

### 1.2 边界

- **在内**：`lis_qc_material`、`lis_qc_run`、`lis_qc_measurement` 聚合内状态、字段、不变式。
- **在外**：质控数据录入/图表分析编排（应用层编排，不在本 SDD 范围）。
- **跨域仅端口声明**：对 Device 仅声明引用约束。

---

## 2. 工程落点与包结构

### 2.1 Maven 模块

- 模块：`zxplat/zxplat-domain`（与当次输入限定一致）。

### 2.2 包结构（`com.zxplat.domain.lis.qcmaterial`）

| 相对包 | 内容 |
| ------ | ---- |
| `entity` | `QcMaterial`、`QcRun`、`QcMeasurement` |
| `valueobject` | 无 |
| `enums`（共享） | 复用 `com.zxplat.domain.share.enums`；生成前须先判断是否已存在 |
| `command` | 每个写方法一个 Java 文件（见 §5） |
| `repository` | `QcMaterialRepository`、`QcRunRepository`、`QcMeasurementRepository`（仅 `JpaRepository` 接口） |
| `service` | `QcMaterialDomainService`（直接实现类） |
| `queries` | `QcMaterialQueryService`（直接实现类） |
| `queries.model` | `QcMaterialQueryModel` |
| `share.events` | 无（本聚合无领域事件） |

**存储口径（DM 优先）**：`statusEnum` / `levelEnum` 采用 `String`（存枚举 code）；禁止混用。

---

## 3. 类型 / 字段命名 / 注解规则

| 规则 | 说明 |
| ---- | ---- |
| 主键 / 外键 | `Long`；聚合根主键 `id`；外键 `materialId`、`deviceId`、`runId`、`operatorId` |
| 时间 | `runDate` → `LocalDate`（语义为日期） |
| `*_enum` | `statusEnum` / `levelEnum` → `String`（存枚举 code） |
| 软删 | `Boolean isDeleted` |
| 实体 | `@Entity`，显式 `@Table`/`@Column` |
| 仓储 | `interface XxxRepository extends JpaRepository<Entity, Long>`；禁止手写 `@Query` |
| 分页 | `Pageable`；未指定排序时默认 `createdAt DESC` |
| 事务 | 领域服务 **不** 声明 `@Transactional`；参与 AppService `AppServiceAspect` 已开启的 UoW |
| 事件 | 本聚合无领域事件 |

---

## 4. 实体 / 值对象 / 枚举 / 仓储 / 查询契约

### 4.1 表与实体映射

| 逻辑表 | 实体类 | 说明 |
| ------ | ------ | ---- |
| `lis_qc_material` | `QcMaterial` | 聚合根 · 质控品 |
| `lis_qc_run` | `QcRun` | 子实体 · 质控运行 |
| `lis_qc_measurement` | `QcMeasurement` | 子实体 · 质控测定值 |

### 4.2 聚合根 `QcMaterial`（字段级契约）

| 字段名（Java） | 类型 | 必填 | 说明 / 约束 |
| -------------- | ---- | ---- | ----------- |
| `id` | `Long` | 系统 | 聚合根标识（雪花 ID） |
| `lotNo` | `String` | 是 | 批号（BR-QC-01：lotNo + levelEnum 唯一） |
| `levelEnum` | `String` | 是 | normal / abnormal |
| `targetValue` | `BigDecimal` | 是 | 靶值 |
| `standardDeviation` | `BigDecimal` | 是 | SD |
| `isDeleted` | `Boolean` | 系统 | 软删标记 |

**不变式方法**：`saveMaterial` / `createRun`

### 4.3 子实体 `QcRun`

| 字段名 | 类型 | 必填 | 说明 |
| ------ | ---- | ---- | ---- |
| `id` | `Long` | 系统 | 子实体标识 |
| `materialId` | `Long` | 是 | 外键 → `lis_qc_material.id` |
| `deviceId` | `Long` | 是 | 仪器 |
| `runDate` | `LocalDate` | 是 | 运行日期 |
| `operatorId` | `Long` | 是 | 操作者 |
| `statusEnum` | `String` | 是 | normal / out_of_control（BR-QC-02） |
| `isDeleted` | `Boolean` | 系统 | 软删标记 |

**归属校验**：任何带 `id` 的更新必须 `loadByIdAndMaterialId(id, materialId)`，不一致则拒绝。

### 4.4 子实体 `QcMeasurement`

| 字段名 | 类型 | 必填 | 说明 |
| ------ | ---- | ---- | ---- |
| `id` | `Long` | 系统 | 子实体标识 |
| `runId` | `Long` | 是 | 外键 → `lis_qc_run.id` |
| `projectCode` | `String` | 是 | 项目码 |
| `measuredValue` | `BigDecimal` | 是 | 测定值 |
| `deviation` | `BigDecimal` | 是 | 偏差（测量值-靶值） |
| `isDeleted` | `Boolean` | 系统 | 软删标记 |

**归属校验**：任何带 `id` 的更新必须 `loadByIdAndRunId(id, runId)`，不一致则拒绝。

### 4.5 仓储接口

```java
public interface QcMaterialRepository extends JpaRepository<QcMaterial, Long> {
    boolean existsByLotNoAndLevelEnumAndIsDeletedFalse(String lotNo, String levelEnum);
}
public interface QcRunRepository extends JpaRepository<QcRun, Long> {
    List<QcRun> findByMaterialIdAndIsDeletedFalse(Long materialId);
}
public interface QcMeasurementRepository extends JpaRepository<QcMeasurement, Long> {
    List<QcMeasurement> findByRunIdAndIsDeletedFalse(Long runId);
}
```

### 4.6 查询模型（`queries.model`）

#### 4.6.1 `QcMaterialQueryModel`（对应 `searchMaterials`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `id` | `Long` | `lis_qc_material.id` | |
| `lotNo` | `String` | `lis_qc_material.lot_no` | |
| `levelEnum` | `String` | `lis_qc_material.level_enum` | |
| `targetValue` | `BigDecimal` | `lis_qc_material.target_value` | |
| `standardDeviation` | `BigDecimal` | `lis_qc_material.standard_deviation` | |

#### 4.6.2 `QcMaterialDetailQueryModel`（对应 `getDetail`）

聚合根字段 + `runs` 列表（含 `measurements` 子列表）。

---

## 5. 写侧与读侧白名单（命令字段全集）

### 5.1 写侧 — `QcMaterialDomainService`

#### 5.1.1 `saveMaterial(SaveQcMaterialCommand cmd)` → `QcMaterial`

**命令文件**：`SaveQcMaterialCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `id` | `Long` | 编辑时传 |
| `lotNo` | `String` | 批号 |
| `levelEnum` | `String` | 水平 |
| `targetValue` | `BigDecimal` | 靶值 |
| `standardDeviation` | `BigDecimal` | SD |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `saveMaterial` |
| `statusTransitionMethod` | 无 |
| `repositoryChecks` | BR-QC-01：lotNo + levelEnum 唯一 |
| `eventPublishAnchor` | 无 |
| `unitTestCaseId` | TC-QC-001 |

**方法契约摘要**：前置：lotNo + levelEnum 不重复；持久化：新增或更新；失败：重复 → 拒绝；**注释锚点**：校验唯一 → 保存 → 返回。

---

#### 5.1.2 `createRun(CreateQcRunCommand cmd)` → `QcRun`

**命令文件**：`CreateQcRunCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `materialId` | `Long` | 质控品 ID |
| `deviceId` | `Long` | 仪器 ID |
| `runDate` | `LocalDate` | 运行日期 |
| `operatorId` | `Long` | 操作者 |
| `measurements` | `List<QcMeasurementItem>` | 测定值列表 |

**子命令 `QcMeasurementItem.java`**（独立文件）：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `projectCode` | `String` | 项目码 |
| `measuredValue` | `BigDecimal` | 测定值 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `createRun + addMeasurements` |
| `statusTransitionMethod` | 初始创建 → 评估 → `normal` / `out_of_control` |
| `repositoryChecks` | materialId / deviceId 存在性 |
| `eventPublishAnchor` | 无 |
| `unitTestCaseId` | TC-QC-002 |

**方法契约摘要**：前置：materialId / deviceId 存在；持久化：创建 QcRun + 批量创建 QcMeasurement + 计算 deviation + BR-QC-02 Westgard 判定；**注释锚点**：创建 Run → 创建 Measurements → 计算偏差 → Westgard 评估 → 标记失控。

---

### 5.2 读侧 — `QcMaterialQueryService`（`domain.queries`）

所有查询 **默认** `isDeleted=false`；分页在 **数据库侧** 过滤与统计 total。

| 方法 | 入参 | 出参（类名，字段见 §4.6） | 关键实现 | `unitTestCaseId` |
| ---- | ---- | ------------------------- | -------- | ---------------- |
| `searchMaterials` | `QcMaterialQuery` | `Page<QcMaterialQueryModel>` | 分页 + 按 lotNo / levelEnum 过滤 | TC-QC-R01 |
| `getDetail` | `Long id` | `QcMaterialDetailQueryModel` | 按 id 查质控品 + runs + measurements | TC-QC-R02 |

**读方法注释锚点（统一）**：构建查询条件（含软删过滤）→ 单次 SQL/仓储调用并完成分页统计 → 组装 QueryModel（无写库）。

---

## 6. 不变式映射（DM §4）

| BR 编号 | 实现策略摘要 |
| ------- | ------------ |
| **BR-QC-01** | saveMaterial 前置：`existsByLotNoAndLevelEnumAndIsDeletedFalse` 校验唯一性；编辑时排除自身 id |
| **BR-QC-02** | createRun 内：逐 measurement 执行 Westgard 规则判定（1-2s / 1-3s / 2-2s / R-4s / 4-1s / 10x）；任一规则失控 → QcRun.statusEnum = out_of_control |

---

## 7. 事件契约与触发规则（DM §5）

无。DM 未定义 QcMaterial 领域事件。

---

## 8. 删除与可见性契约

- **软删**：`isDeleted = true`；查询默认过滤 `isDeleted = false`。
- **子表同步**：QcRun 删除时级联软删 QcMeasurement（runs → measurements 全量管理）。

---

## 9. 框架优先适配规则

- 遵循项目级 Java 成码门禁。
- 领域服务不声明 `@Transactional`；事务由 AppService `AppServiceAspect` 管理。
- 分页默认 `Pageable`，排序默认 `createdAt DESC`。
- 枚举统一复用 `com.zxplat.domain.share.enums`。

---

## 10. 生成与验收清单

- [ ] `QcMaterial` / `QcRun` / `QcMeasurement` 实体 + 字段与 §4 一致
- [ ] `SaveQcMaterialCommand` / `CreateQcRunCommand` / `QcMeasurementItem` 独立文件
- [ ] 三个 Repository 仅 `JpaRepository` 接口，无 `@Query`
- [ ] `QcMaterialDomainService` 两个写方法：saveMaterial / createRun
- [ ] `QcMaterialQueryService` 两个读方法：searchMaterials / getDetail
- [ ] Westgard 多规则判定逻辑在 DomainService 实现
- [ ] 子表同步：QcMeasurement 全量对齐 + 软删旧行

---

## 11. 单次会话代码生成门禁（DoD）

### 11.1 前置输入

- 锁定 DM：`DM-LIS-006-QcMaterial-质控品.md`
- 本 SDD 文件
- 目标模块：`zxplat/zxplat-domain`

### 11.2 DoD

- [ ] 字段表 §4.2/§4.3/§4.4 与 DM §3 一致（字段名、类型、必填）
- [ ] 写方法 §5.1 覆盖全部业务操作（saveMaterial / createRun）
- [ ] BR §6 逐条有实现策略（BR-QC-01 唯一性、BR-QC-02 Westgard）
- [ ] 读方法 §5.2 覆盖全部查询场景
- [ ] 包路径 `com.zxplat.domain.lis.qcmaterial` 正确
- [ ] 编译通过（`mvn -pl zxplat/zxplat-domain compile`）

**本 SDD 正文结束。**
