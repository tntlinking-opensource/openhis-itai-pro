# LisAppService · FHIR 对齐领域模型

> **版本**：v1.5 · **日期**：2026-06-27  
> **落地方案（已定）**：**`LisWorkItem`** 聚合根 + 子实体 **`LisWorkItemOrder`**（并管明细）；对外 FHIR 映射为 `Specimen` + `basedOn` 引用。  
> **跨模块约定**：[01-概要设计 §10](./01-概要设计.md#10-lispacspeis-跨模块架构约定)（Observation 分层、DR index、Maven、无 map）  
> **关联**：[00-功能清单.md](./00-功能清单.md) §1.3、[README.md](./README.md) §2

---

## 一、总览：FHIR 检验链路

```text
health                                    lis（zxplat/lis 子包）
──────                                    ──────────────
ServiceRequest × N（多条医嘱行）  ──┐
                                    ├──► LisWorkItem（1 管血 / 1 条码，聚合根）
ServiceRequest × 1（单项）      ──┘         │
                                              │ 1──N LisWorkItemOrder（并管明细）
                                              ▼
                                         Task（可选）
                                              │
                    ┌─────────────────────────┼─────────────────────────┐
                    ▼                         ▼                         ▼
              Observation              DiagnosticReport            Device
        （lis_observation；workItemId + serviceRequestId）  （lis_diagnostic_report）
                              │
                              └── 发布 → cln_diagnostic_report_index（health 读侧）
```

**与 CLN 边界**（[01 §10.2](./01-概要设计.md#102-observation-分层d1)）：检验事实 **仅** 写 **`lis_observation`**；PEIS/分科读 **`cln_observation`** 时通过 `source_observation_id` 引用，**禁止**双写结果值到 CLN 而不留引用。

**原则**：代码侧以 **`LisWorkItem`** 为检验执行聚合根；语义对齐 FHIR `Specimen`，互操作/区域平台导出时再 Assembly 为 FHIR JSON。

**并管核心**：**N 个 `ServiceRequest` → 1 个 `LisWorkItem`**（遗留 `l_laborder` 一行 + `groupCode` 用 `+` 拼接，见 §二）。

---

## 二、并管：多项目共用一样本

### 2.1 业务场景

| 场景 | 说明 | 遗留对标 |
|------|------|----------|
| **并管** | 一管血/一个条码检验多项（血常规+生化+肝功） | `sendApply` 中 `groupCode` 以 `+` 连接多码，**一个 `applyNo` = 一条 `l_laborder`** |
| **独管** | 一项一单管（尿常规、便常规） | 1 `ServiceRequest` → 1 `LisWorkItem` |
| **补项** | 已采样后追加医嘱，复用未送出标本（租户策略） | 同条码追加 link |
| **拆管** | 一项需多管（血培养×2） | 1 `ServiceRequest` → N `LisWorkItem`（少见，P2） |

并管时 **医嘱粒度仍是「一行一项」**（health `ServiceRequest`），**物理采样粒度是「一管一样本」**（lis **`LisWorkItem`**）。二者用子实体 **`LisWorkItemOrder`** 解耦。

**硬性约束**：并管场景下 **禁止** 为每个 `ServiceRequest` 各建一条 `LisWorkItem`（避免重复建管、重复条码）；**禁止** 把多项拼进一个 `ServiceRequest`。

### 2.2 聚合设计（方案 B，已定）

```text
LisWorkItem（聚合根，表 lis_work_item）
  id, barcode, sampleNo, specimenDefinitionId
  patientId, encounterId
  statusEnum（标本物理态：available → collected → received → …）
  collectionTime, receivedTime, …
  │
  └── orderLinks: LisWorkItemOrder[]（子实体，表 lis_work_item_order）

LisWorkItemOrder（子实体）
  workItemId
  serviceRequestId          -- UNIQUE (workItemId, serviceRequestId)
  activityId                -- 冗余，来源 SR
  linkStatusEnum            -- 该项在本管上的执行态（见 §2.4）
  sortNumber

Observation
  workItemId                -- 哪管血（FHIR 导出 → specimen.reference）
  serviceRequestId          -- 计费/报告归属（FHIR basedOn）
  code / value / …

DiagnosticReport
  serviceRequestId          -- 临床按医嘱看报告
  workItemId                -- 可选，合并 PDF
  result[] → Observation ids
```

**FHIR 映射**（仅互操作层，代码不混用两套主键名）：

| zxplat-domain/lis | FHIR R4 |
|------------|---------|
| `LisWorkItem` | `Specimen` |
| `LisWorkItemOrder` | `Specimen.request` 或 `ServiceRequest.specimen` 反向引用 |
| `Observation.workItemId` | `Observation.specimen` |

**基数**：

| 关系 | 基数 | 说明 |
|------|------|------|
| `ServiceRequest` → `LisWorkItem` | N : M | 经 `LisWorkItemOrder`；并管时 N>1 : 1 |
| `LisWorkItem` → `Observation` | 1 : N | 一管血产生多条结果 |
| `ServiceRequest` → `Observation` | 1 : N | 一项可有多细项结果 |
| `ServiceRequest` → `DiagnosticReport | 1 : 0..1 | 一项一份报告（可合并展示） |

### 2.3 合管策略（LIS 配置表 + 仪器检验能力）

分组在 **health-application/lis** 由 **`SpecimenGroupingPolicy`** 执行。**不使用 whale-framework Setting**；全部落在 **`zxplat/lis` 自有配置表**，与仪器能力、试管方案绑定。

**设计取向**：合管不应仅按「同就诊 + 同标本类型」粗并——应以 **试管物理约束** + **仪器可检验项目（能力）** + **合管方案（MergeProfile）** 为真源，避免「能并到一管但仪器/添加剂不兼容」。

```text
INT-01 接单（医生站 `DoctorStationAdviceAppService.sign()` 直接调用 `LisWorkItemDomainService`）
  输入：List<serviceRequestId>
  1. 读 SR → ActivityDefinition
  2. 解析每项：tubeSchemeId、mergeProfileId、候选 deviceId（来自能力表）
  3. load LisModuleConfig + MergeProfile + DeviceCapability（lis 配置表）
  4. SpecimenGroupingPolicy.group(...) → 若干 LisWorkItem
  5. 每管 create LisWorkItem + orderLinks；生成 barcode
```

#### 2.3.1 配置表（不用 framework Setting）

| 表 / 实体 | 职责 |
|-----------|------|
| **`lis_config`** | LIS 模块级 KV（`tenant_id`/`org_id`/`config_key`/`config_value`），如 `grouping.mode`、补项开关 |
| **`lis_tube_scheme`** | **试管/采血方案**：容器、添加剂、标本类型、最大采血量、单管最大项目数 |
| **`lis_merge_profile`** | **合管方案**：名称、绑定 `tube_scheme_id`、说明（如「生化+肝功+肾功」） |
| **`lis_merge_profile_item`** | 方案内可并管的 `activity_id` 列表 |
| **`lis_device_capability`** | **仪器检验能力**：`device_id` + 可检 `activity_id` + 接受 `tube_scheme_id` + 可选 `merge_profile_id` |
| **`lis_device_channel`** | 通道号 ↔ 项目（ingress 用，§4.3） |

**作用域**：`tenant_id` 必填；`org_id` 可选（院区/检验科差异）。**禁止**读写 framework `Setting` 表。

#### 2.3.2 合管判定逻辑（推荐默认：`PROFILE_AND_DEVICE`）

**硬约束**（代码内置，不可配置关闭）：

- 同一 `patientId` + 同一 `encounterId`
- 同一 **`tube_scheme_id`**（试管/添加剂/采集要求一致）

**软约束**（由配置表驱动）：

| 步骤 | 依据 | 说明 |
|------|------|------|
| 1 | `lis_merge_profile` | 待并多项须落在 **同一合管方案**，或方案配置允许子集合并 |
| 2 | `lis_device_capability` | 合并后的项目集，须能被 **至少一台在用仪器** 覆盖（每项有 capability 行） |
| 3 | `lis_tube_scheme.max_item_count` | 单管项目数上限 |
| 4 | `lis_config` | 全局模式、补项、时间窗等辅助开关 |

```text
SR 血常规 ──► activity 101 ──► mergeProfile「血液常规组」──┐
SR 肝功   ──► activity 102 ──► mergeProfile「生化组」      ├──► 若两 profile 不同
SR 血糖   ──► activity 103 ──► mergeProfile「生化组」      │    查「生化组」是否含 101
                                                          └──► 或查是否存在父方案「门诊血清套餐」
再校验：三台项目均存在 deviceCapability(生化仪, 黄帽血清方案)
→ 通过则 1 个 LisWorkItem；否则拆成多管
```

**为何跟仪器能力配置更好**：

| 仅按标本类型并管 | 按仪器能力 + 合管方案 |
|------------------|----------------------|
| 血清项全并一管，不管生化仪/免疫仪是否同管 | 按检验科 **实际仪器线** 与 **试管方案** 配置 |
| 忽略试管容量、添加剂 | `tube_scheme` 约束物理管 |
| 与 `yqtd` 通道配置脱节 | capability 与 channel 同一设备主数据 |

#### 2.3.3 `lis_config` 键（模块自有，非 framework）

| config_key | 默认 | 说明 |
|------------|------|------|
| `grouping.mode` | `PROFILE_AND_DEVICE` | 见 §2.3.4 |
| `grouping.allowAppendToOpenTube` | `false` | 补项挂未采集工单 |
| `grouping.collectionWindowMinutes` | `0` | 待采样时间窗（辅助） |
| `grouping.fallbackWhenNoProfile` | `ONE_SR_ONE_TUBE` | 项目未维护 mergeProfile 时的降级 |

#### 2.3.4 分组模式 `grouping.mode`

| 模式 | 行为 |
|------|------|
| `PROFILE_AND_DEVICE` | **默认**；合管方案 + 仪器能力 + 试管方案（§2.3.2） |
| `PROFILE_ONLY` | 只校验 mergeProfile + tubeScheme，不校验 device（调试/外送实验室） |
| `ONE_SR_ONE_TUBE` | 每项独管，不并管 |
| `MANUAL_AT_SMP` | 接单先独管；采样台护士手工合并（P1 UI） |

#### 2.3.5 配置维护与接单关系

| IF | 谁维护 | 内容 |
|----|--------|------|
| **CFG-04** 仪器 | 检验科 | `Device` 主档 |
| **CFG-05** 通道 | 检验科 | `lis_device_channel` |
| **CFG-08** 合管 | 检验科 | `tube_scheme`、`merge_profile`、`device_capability` |
| **CFG-09**（新） | 检验科 | `lis_config` 模块参数 |
| **INT-01** | 医生站 | 签发检验医嘱时直接注入 `LisWorkItemDomainService`；合管分组 `LisOrderIngressOrchestrator`；**BR-INT-06** 校验 `device_capability`（[01 §10.4](./01-概要设计.md#104-项目对照--一体化无-mapd3)） |

维护顺序建议：**试管方案 → 仪器能力 → 合管方案 → 通道**；合管方案中的项目必须是某台在用仪器 `capability` 的子集。

#### 2.3.6 代码结构

```text
zxplat-domain/lis/
  SpecimenGroupingPolicy（接口）
  LisGroupingContext（值对象：config + profiles + capabilities）

health-application/lis/
  ProfileDeviceGroupingPolicy（默认实现）
  LisGroupingContextLoader（读 lis_* 表 + 缓存）

health-application/lis / LisAppService CFG：
  IF-LIS-CFG-08  合管方案 + 试管方案 + 仪器能力
  IF-LIS-CFG-09  lis_config 维护
```

- **禁止** framework `Setting`；**禁止** `if (tenantId)` 院点硬编码。
- 未配置 `merge_profile` 的 Activity：按 `grouping.fallbackWhenNoProfile` 降级，并记 WARN 日志促维护。

#### 2.3.7 持久化补充（配置表）

```sql
lis_config (
  id, tenant_id, org_id, config_key, config_value, remark, ...
)

lis_tube_scheme (
  id, tenant_id, bus_no, name, specimen_definition_id,
  container_type, additive, max_volume_ml, max_item_count, status_enum, ...
)

lis_merge_profile (
  id, tenant_id, bus_no, name, tube_scheme_id, status_enum, ...
)

lis_merge_profile_item (
  id, merge_profile_id, activity_id, sort_number,
  UNIQUE (merge_profile_id, activity_id)
)

lis_device_capability (
  id, device_id, activity_id, tube_scheme_id,
  merge_profile_id,  -- 可选，加速匹配
  status_enum,
  UNIQUE (device_id, activity_id, tube_scheme_id)
)
```

### 2.4 状态：标本态 vs 医嘱项态

并管后 **整管状态** 与 **单项状态** 可能不一致，需双层状态：

| 层 | 字段 | 语义 | 示例 |
|----|------|------|------|
| 工单 | `LisWorkItem.statusEnum` | 物理流转 | 已采集、已核收、已上机 |
| 明细 | `LisWorkItemOrder.linkStatusEnum` | 该项在本管上的业务态 | 检测中、待审、已发布、已作废 |

```text
LisWorkItem.statusEnum = received（整管已核收）

LisWorkItemOrder:
  SR#101 血常规  linkStatusEnum = published
  SR#102 肝功    linkStatusEnum = testing
  SR#103 血糖    linkStatusEnum = cancelled
```

**禁止**用单一 `LisWorkItem.statusEnum` 表达「肝功未出、血常规已出」——用 `linkStatusEnum` 或各 `Observation.statusEnum`。

**领域方法**（聚合内）：`addOrder(serviceRequestId)`、`cancelOrder(serviceRequestId)`、`collect()`、`receive()` 等；并管追加项时调 `addOrder`，**不**新建 `LisWorkItem`。

### 2.5 结果与报告在并管下的归属

| 资源 | 并管规则 |
|------|----------|
| **Observation** | `workItemId` + `serviceRequestId` 双锚；仪器 `barcode` → `workItemId`，通道 → `activityId` → 反查 `serviceRequestId` |
| **DiagnosticReport** | 默认按 `serviceRequestId`；同 `LisWorkItem` 多项可各出一份或合并 |
| **危急值** | `DetectedIssue` → `Observation`；通知按 `serviceRequestId` |
| **撤单** | `LisWorkItem.cancelOrder(srId)`；仅当全部 order 作废才 `LisWorkItem.cancel()` |

### 2.6 条码与打印

| 对象 | 规则 |
|------|------|
| 条码 | **1 LisWorkItem = 1 barcode**；标签打印读 `orderLinks` + `ActivityDefinition` |
| 申请单打印 | PrintTemplate（**AS-BAS-PRT**）；条码 = `LisWorkItem.barcode` |
| 仪器 ingress | `barcode` → `workItemId` → 匹配管上 `LisWorkItemOrder` |

### 2.7 端到端示例

医生签发三项（同一就诊、均为血清）：

```text
SR-1  血常规    activityId=101
SR-2  肝功八项  activityId=102
SR-3  血糖      activityId=103
        │
        ▼ ProfileDeviceGroupingPolicy（合管方案「血清生化」+ 生化仪 capability）
LisWorkItem W-9  tubeScheme=黄帽血清  barcode=202606180001
  ├─ Order(W-9, SR-1)  linkStatusEnum=pending
  ├─ Order(W-9, SR-2)  linkStatusEnum=pending
  └─ Order(W-9, SR-3)  linkStatusEnum=pending
        │
        ▼ 采样签收：workItem.collect()
LisWorkItem W-9  statusEnum=collected
        │
        ▼ 仪器回传 barcode=202606180001
Observation O-1  workItemId=W-9, serviceRequestId=SR-3, code=GLU, statusEnum=preliminary
  Order(W-9, SR-3)  linkStatusEnum=result_pending
        │
        ▼ 审核发布 SR-3
DiagnosticReport DR-3  serviceRequestId=SR-3, result=[O-1], statusEnum=final
  Order(W-9, SR-3)  linkStatusEnum=published
（SR-1、SR-2 仍可独立审核发布，互不影响）
```

### 2.8 持久化草案

```sql
-- 检验工单（聚合根，≈ l_laborder）
lis_work_item (
  id, barcode, sample_no, specimen_definition_id,
  patient_id, encounter_id, status_enum,
  collection_time, received_time, ...
)

-- 并管明细（子表，聚合内 orderLinks）
lis_work_item_order (
  id, work_item_id, service_request_id, activity_id,
  link_status_enum, sort_number,
  UNIQUE (work_item_id, service_request_id)
)

-- 结果
lis_observation (
  id, work_item_id, service_request_id,
  code_system, code, value_*, status_enum, ...
)
```

**Repository 边界**：`LisWorkItemRepository` 以聚合为单位 load/save；`orderLinks` 随聚合持久化，application **不**单独写子表。

---

## 三、检验结果 → `Observation`

替代原 `LisResult` / `LisResultItem`。

### 3.1 映射

| FHIR Observation | ZXPlat zxplat-domain/lis | 说明 |
|------------------|------------------|------|
| `status` | `statusEnum` | `registered` / `preliminary` / `final` / `amended` / `cancelled` |
| `category` | `categoryCode` | 固定 `laboratory` |
| `code` | `codeSystem` + `code` | LOINC 或院内码；对照 `ActivityDefinition` |
| `subject` | `patientId` | 患者 |
| `encounter` | `encounterId` | 就诊 |
| `basedOn` | `serviceRequestId` | **关联医嘱**（并管时每项结果归属不同 SR，见 §2.5） |
| `specimen` | `workItemId` | 关联工单（FHIR 导出为 `Specimen` 引用） |
| `device` | `deviceId` | 产生结果的仪器实例 |
| `effectiveDateTime` | `effectiveTime` | 检测时间 |
| `value[x]` | `valueQuantity` / `valueString` / `valueCodeableConcept` | 定量/定性/阴阳性 |
| `referenceRange` | 子表或 JSON | 参考区间 |
| `interpretation` | `interpretationCode` | `N`/`H`/`L`/`HH`/`LL`/`A`… |
| `note` | `noteText` | 备注 |
| `hasMember` | 父项 `id` ← 子项 `parentObservationId` | 组合/套餐项 |

### 3.2 审核发布

- 仪器 ingress：按 `barcode` → `workItemId`，匹配 `orderLinks` → `serviceRequestId`；`statusEnum = preliminary`
- 检验师审核：按 `serviceRequestId` 或整 `LisWorkItem` 批量
- 发布：更新 `LisWorkItemOrder.linkStatusEnum` + `DiagnosticReport
- 医生站查报告：按 `serviceRequestId` 查 `DiagnosticReport；明细走 `Observation`

### 3.3 与 health 的边界

- **Observation 落在 `zxplat-domain/lis`**（表 **`lis_observation`**）
- health **只读**已发布索引（**`cln_diagnostic_report_index`** + DM-CLN-006 DiagnosticReportIndex 指针），不写结果值
- PEIS 汇聚检验结果：读 `lis_observation` → 写 **`cln_observation`**（`source_observation_id`）

---

## 四、仪器与通道 → `Device`

替代原 `LisInstrumentChannel` 中的**仪器实例**部分。

### 4.1 与 ZXPlat 现有 `DeviceDefinition` 的区别

| 概念 | ZXPlat 现有 | FHIR | LIS 应用 |
|------|------------|------|----------|
| 耗材目录 | `DeviceDefinition`（`adm_device_definition`） | DeviceDefinition（类型目录，R5） | **不用** |
| 检验仪器实例 | 无 | **Device**（序列号、型号、科室） | **zxplat-domain/lis 新建** |
| 仪器通道映射 | 无 | Device 扩展或独立配置表 | `lis_device_channel` |

> **禁止**复用 `DM-ADM-008 DeviceDefinition` 表达生化仪、血球仪——语义是耗材发放，不是检验设备。

### 4.2 Device 实体（草案）

| 字段 | FHIR 来源 | 说明 |
|------|-----------|------|
| `busNo` | `identifier` | 院内仪器编码 |
| `deviceName` | `deviceName` | 显示名 |
| `manufacturer` | `manufacturer` | 厂商 |
| `modelNumber` | `modelNumber` | 型号 |
| `serialNumber` | `serialNumber` | 序列号 |
| `locationId` | `location` | 检验科位置 |
| `statusEnum` | `status` | `active` / `inactive` / `entered-in-error` |

### 4.3 仪器检验能力（合管真源之一）

`lis_device_capability` 与通道表分工：

| 表 | 阶段 | 职责 |
|----|------|------|
| **`lis_device_capability`** | 合管分组 / 路由 | 该仪器**能接受何种试管方案、能检哪些项目** |
| **`lis_device_channel`** | RES-01 ingress | 通道号 → 项目码，落结果 |

```text
LisDeviceCapability
  deviceId
  activityId
  tubeSchemeId       -- 必须与合管后 LisWorkItem 的试管方案一致
  mergeProfileId     -- 可选，指向 lis_merge_profile
```

**配置约束**：`merge_profile_item` 中的每个 `activity_id`，至少应有一条指向 **在用** `device` 的 `capability` 行；否则合管分组失败或拆管，并提示检验科维护。

### 4.4 通道映射（遗留 `yqtd`）

单独配置实体（非 FHIR 一等资源，Industry 惯例）：

```text
lis_device_channel
  deviceId
  channelNo
  activityId / loincCode
  unitCode
  dilutionFactor
```

仪器 ingress 时：`Device` + `channelNo` + `barcode`（→ `workItemId`）→ 在 `LisWorkItem.orderLinks` 中解析 `serviceRequestId` → 写入 `Observation`。

---

## 五、危急值 → `DetectedIssue` + `Observation`

不单建扁平 `LisCriticalValue` 表为主模型，而用 **FHIR 组合 + 领域服务**：

| 层 | 资源/实体 | 职责 |
|----|-----------|------|
| 事实 | `Observation` | `interpretation` = `HH`/`LL` 等；值为危急事实源 |
| 告警 | `DetectedIssue` | 严重程度、识别时间、涉及患者/就诊、`implicated`→Observation |
| 通知 | `Communication`（可选 P2） | 电话/短信/站内信记录 |
| 闭环 | `DetectedIssue.mitigation` + 扩展 `acknowledgedBy` | 临床确认 |

### 5.1 DetectedIssue 字段（草案）

| 字段 | 说明 |
|------|------|
| `statusEnum` | `preliminary` / `final` / `mitigated` / `entered-in-error` |
| `severityEnum` | `high`（危急）/ `moderate` |
| `code` | `critical-value-lab` 固定或租户码表 |
| `patientId` / `encounterId` | 患者上下文 |
| `observationId` | 触发项 |
| `identifiedDateTime` | 识别时间 |
| `authorId` | 检验师 |
| `mitigationAction` | 临床处理说明 |
| `acknowledgedAt` / `acknowledgedBy` | 闭环 |

**规则引擎**（`CRT-01`）：参考范围 + 危急阈值配置 → 创建/更新 `DetectedIssue`，不重复存一份“影子结果”。

---

## 六、质控 → 自定义领域（参考 FHIR Observation 承载测量值）

FHIR **无**独立 QC 资源；行业常见两种做法：

### 6.1 推荐：显式 QC 领域模型

| 实体 | 职责 | 与 FHIR 关系 |
|------|------|--------------|
| `QcMaterial` | 质控品批号、水平、靶值、SD | 类似 Substance 语义，自建 |
| `QcRun` | 一次质控运行（日期、仪器、操作者） | — |
| `QcMeasurement` | 某项目在某 Run 下的测定值 | **可同步投影为** `Observation`（`subject`→QcMaterial，`device`→Device，`category`=`laboratory`，`code` 加 QC 标记） |
| `QcEvaluation` | Westgard 规则判定结果 | 领域服务输出，存规则码、是否失控 |

```text
QcRun 1──N QcMeasurement 1──1 QcEvaluation（可选）
                │
                └──► Observation（可选，用于统一查询/图表）
```

### 6.2 为何不单用 Observation

- 质控品、批号、水平、靶值 SD、Westgard 多规则 **无标准 FHIR 结构**
- 显式 `QcRun`/`QcMeasurement` 更利于 `zkcx` 对标与 Levey-Jennings 图
- 需要对外交换时再 Assembly 为 Observation Bundle

---

## 七、接口日志 → `AuditEvent`（非 Device）

遗留 `WBS_LOG` 记录 **HIS/仪器 HTTP 报文**，FHIR 对应 **`AuditEvent`**，不是 `Device`。

| AuditEvent | 用途 |
|------------|------|
| `type` | `rest` / `ex旧命名（已废止） |
| `action` | `C`/`R`/`U`/`D`/`E` |
| `recorded` | 时间 |
| `agent` | 调用方（HIS、仪器、用户） |
| `source` | LIS 节点 |
| `entity` | 关联 `ServiceRequest` / `LisWorkItem` / `Observation` |
| `outcome` | 成功/失败 |
| 扩展 `requestBody` / `responseBody` | 报文存档（注意脱敏与保留策略） |

### 7.1 若指「设备侧数据」

仪器心跳、最后联机时间、原始帧计数 → FHIR **`DeviceMetric`** 或 **`Device.property`**，与 **AuditEvent** 分工：

| 数据 | 资源 |
|------|------|
| 接口报文、对账 | AuditEvent |
| 仪器在线状态、计数器 | DeviceMetric |
| 通道最近结果时间 | Device 扩展字段或 DeviceMetric |

---

## 八、`LisLabOrder` 与 `ServiceRequest` 是否重叠？

### 8.1 结论：**语义不重叠，不宜用 ServiceRequest 单独表达 LIS 执行**

| 维度 | ServiceRequest（health） | LisWorkItem + orderLinks（lis） |
|------|--------------------------|------------------------------------------|
| **语义** | 临床医嘱意图：「要做什么检验」 | 检验执行：「这管血/这个条码怎么处理」 |
| **所有者** | 医生站 / 护士站 / 收费 | 检验科 |
| **生命周期** | DRAFT → ACTIVE → VERIFIED/COMPLETED | 采集 → 核收 → 上机 → 待审 → 已发布 |
| **粒度** | 通常 **1 医嘱行 = 1 项目** | **1 条码可对应 N 个 ServiceRequest**（一管血多项） |
| **典型字段** | 开立人、数量、医保、收费状态 | 条码、样本号、试管架位、核收人、上机时间 |
| **完成含义** | 门诊收费完成 ≠ 报告已出 | 发布 = 结果 final + DiagnosticReport |

遗留 `l_laborder` 一行 ≈ **`LisWorkItem`** + 内嵌多项（`groupCode` 的 `+`）→ **`LisWorkItemOrder`** 子表规范化。

### 8.2 若强行只用 ServiceRequest 的弊端

1. 污染 zxplat-domain（条码、样本号写入 `wkf_service_request`）
2. 一管血多项无法表达（并管必须独立聚合）
3. 重采/拒收应新建 `LisWorkItem`，不应改 SR
4. 收费完成与报告发布状态冲突
5. 外购 LIS Module 无法替换
6. 检验科与医生站权限边界混乱

### 8.3 落地方案（已定：方案 B）

```text
LisWorkItem（聚合根，zxplat-domain/lis）
  barcode, sampleNo, statusEnum, specimenDefinitionId, …
  orderLinks: LisWorkItemOrder[]（子实体，禁止跨聚合引用）

规则：
  · 并管：N ServiceRequest → 1 LisWorkItem，N 条 orderLink
  · 独管：1 ServiceRequest → 1 LisWorkItem，1 条 orderLink
  · 禁止并管时为每个 SR 单独 create LisWorkItem
  · 对外 FHIR：LisWorkItem ↔ Specimen；不暴露两套 API 主键
```

**关联规则**（与 §二一致）：

- 并管：**N `ServiceRequest` : 1 `LisWorkItem`**
- 独管：**1 `ServiceRequest` : 1 `LisWorkItem`**
- 拆管：**1 `ServiceRequest` : N `LisWorkItem`**（少见，P2）
- lis 只存 id 引用，不复制患者/收费字段

---

## 九、修订后领域锚点（§1.3 真源）

| 系统 | 聚合 / FHIR | 职责 |
|------|-------------|------|
| **health** | `ServiceRequest` | 医嘱意图、收费锚点（**一行一项**） |
| **health** | `SpecimenDefinition`、`ActivityDefinition` | 标本类型、项目定义 |
| **lis** | **`LisWorkItem`**（FHIR↔`Specimen`） | 聚合根：条码、样本号、标本物理态 |
| **lis** | **`LisWorkItemOrder`**（`orderLinks`） | 并管明细；`linkStatusEnum` |
| **lis** | `Task`（可选） | 检验科执行工作流 |
| **lis** | `Observation` | 检验结果（`workItemId` + `serviceRequestId`） |
| **lis** | `DiagnosticReport | 报告聚合（默认按 `ServiceRequest`） |
| **lis** | `Device` + **`lis_device_capability`** + `lis_device_channel` | 仪器、**检验能力**、通道 |
| **lis** | **`lis_tube_scheme`** / **`lis_merge_profile`** | 试管方案、合管方案（§2.3） |
| **lis** | **`lis_config`** | LIS 模块参数（**非** framework Setting） |
| **lis** | `DetectedIssue` | 危急值告警与闭环 |
| **lis** | `QcMaterial` / `QcRun` / `QcMeasurement` | 质控 |
| **lis** | `AuditEvent` | 接口日志；仪器遥测用 `DeviceMetric` |

---

## 十、修订记录

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.0 | 2026-06-18 | Observation/Device/DetectedIssue/QC/AuditEvent；LisLabOrder vs ServiceRequest |
| v1.1 | 2026-06-18 | §二 并管：双层状态、分组规则、仪器/报告归属 |
| v1.2 | 2026-06-18 | **定稿方案 B**：`LisWorkItem` + `LisWorkItemOrder`；表 `lis_work_item` / `lis_work_item_order` |
| v1.3 | 2026-06-18 | §2.3 合管：`lis_*` 配置表 + 仪器 capability，弃 framework Setting |
| v1.6 | 2026-06-27 | DEC 回归主文档；交叉引用改 01 §10 |
| v1.5 | 2026-06-27 | `lis_observation`、DR index、CLN 引用边界 |
