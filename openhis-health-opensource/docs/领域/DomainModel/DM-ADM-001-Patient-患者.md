# DM-ADM-001 Patient 患者

> **限界上下文**：管理域 · 患者管理  
> **聚合类型**：实体聚合  
> **版本**：v1.3

> **边界说明**：学籍/公费学生名单已迁出 `zxplat-module-campus`（`cmp_patient_student` / [DM-CMP-STU-001](../../../模块/zxplat-module-campus/DomainModel/DM-CMP-STU-001-PatientStudent-学生.md)），本聚合**不再包含** `PatientStudent` / `adm_patient_student`。

## 1. 概述

### 1.1 业务定义

患者（Patient）是 HIS 系统中最核心的主数据聚合之一，表达一个**就诊主体**的人口学信息、身份标识与联系方式，为就诊、医嘱、费用、病历等几乎所有业务聚合提供稳定的 `患者标识(PatientId)`。

### 1.2 核心职责


| 职责       | 说明                                                |
| -------- | ------------------------------------------------- |
| 人口学信息管理  | 维护姓名、性别、出生日期、民族、婚姻状态等基础人口学信息                      |
| 身份标识管理   | 管理院内编码、身份证号及多种非身份证类标识（军官证、护照等）的全生命周期              |
| 联系方式管理   | 维护联系电话、地址、紧急联系人等通信信息                              |
| 患者合并     | 处理重复建档场景，将源患者合并至目标患者并维护合并链接                       |
| 患者生命周期管理 | 管理患者从建档、停用、重新启用、合并、废弃到逻辑删除的完整生命周期                 |
| 跨域锚点     | 作为 Encounter、Account、MedicationRequest 等聚合的基础引用主体 |


### 1.3 适用范围


| 类型   | 说明                       |
| ---- | ------------------------ |
| 门诊建档 | 挂号窗口/自助机为首次就诊患者创建档案      |
| 住院建档 | 入院登记时创建或完善患者档案           |
| 临时建档 | 特殊场景下先以最小信息创建临时档案，后续补全  |
| 档案维护 | 修改患者人口学信息、联系方式、证件信息      |
| 患者合并 | 发现重复建档后将多条记录合并为一条        |
| 逻辑删除 | 将患者从可用主数据集合中移除，保留审计与历史引用 |


### 1.4 与 Encounter 的关系

Patient 与 Encounter 是 HIS 两大核心锚点。Patient 代表"谁"，Encounter 代表"一次诊疗交互"。


| 维度   | Patient        | Encounter               |
| ---- | -------------- | ----------------------- |
| 管理范围 | 就诊主体的身份与人口学信息  | 单次诊疗交互的生命周期             |
| 生命周期 | 长期存在，跨多次就诊     | 与单次就诊绑定                 |
| 关联方式 | 被 Encounter 引用 | 通过 `患者标识(PatientId)` 关联 |


## 2. 聚合根与持久化形态

### 2.1 持久化形态

**持久化形态**：主表+子表

聚合根对应主表，子实体对应子表，通过外键 `patient_id` 关联。聚合边界以主表主键 `id`（患者标识）划分，主表一行对应子表 N 行（0 ≤ N）。

**表结构**：

- 主表：`adm_patient`（主键：`id`）
- 子表：`adm_patient_identifier`（外键：`patient_id`）
- 关系：1:N，子表行数不限，可为 0

**高频查询模式**：

- 按患者标识查询：单表主键查询主表，必要时 JOIN 子表
- 按院内编码查询：主表 `bus_no` 索引扫描
- 按身份证号查询：主表 `id_card` 索引扫描
- 按姓名拼音检索：主表 `py_str`/`wb_str` 辅助索引

### 2.2 聚合根标识


| 属性   | 类型          | 说明                 |
| ---- | ----------- | ------------------ |
| 患者标识 | `PatientId` | 全局唯一标识，系统生成（雪花 ID） |


### 2.3 状态机

```
                                  ┌──重新启用──┐
                                  │            │
                                  ▼            │
[临时] ──确认建档──> [活跃] ──停用──> [已停用]
  │                   │
  │                   └──合并──> [已合并]
  │
  └──废弃──> [已废弃]
```

#### 状态定义


| value | code      | info | 语义                                        |
| ----- | --------- | ---- | ----------------------------------------- |
| 1     | DRAFT     | 临时建档 | 信息不完整，尚未正式确认，允许确认建档、修改、停用、合并、废弃、标记死亡、逻辑删除 |
| 2     | ACTIVE    | 活跃   | 正式建档，可被就诊、医嘱等业务引用，允许修改、停用、合并、标记死亡、逻辑删除    |
| 3     | INACTIVE  | 已停用  | 已注销或停用，不可新开就诊但保留历史，允许重新启用、标记死亡、逻辑删除       |
| 4     | MERGED    | 已合并  | 重复建档已合并到目标患者，不可独立使用                       |
| 5     | CANCELLED | 已废弃  | 临时建档后未产生任何业务关联即废弃                         |


> **状态枚举说明**：持久化字段 `status_enum`（`PatientStatus` 枚举，**String 存储，存枚举 code**）；`active_flag`（Boolean）为派生字段，`status_enum == ACTIVE` 时为 `true`，其余为 `false`，与 FHIR `Patient.active` 对齐。
>
> - `DRAFT` 对应 `temp_flag = true` 的临时患者，与 Encounter 的"已计划"语义类比
> - `ACTIVE` 为正式患者，`active_flag = true`；其他状态 `active_flag = false`
> - `CANCELLED` 对齐全局约定——未产生实际效果即终止
> - `MERGED` 对应 FHIR `Patient.link` 中 `type = replaced-by` 的语义
> - **标记死亡**不改变患者状态，仅设置 `deceasedDate`，已故患者的历史记录仍然有效（与 FHIR `Patient.deceased[x]` 独立于 `Patient.active` 的设计一致）
> - **逻辑删除**（`isDeleted`）与患者状态正交，由 `BaseAuditedEntity` 统一管理

## 3. 实体

```
Patient（聚合根）
════════════════════════════════════════════════════════════

  ┌──────────────────────────────────────────────────────┐
  │                     Patient                          │
  │                                                      │
  │  - 姓名 / 性别 / 出生日期 / 身份证号                │
  │  - 院内编码 / 拼音码 / 五笔码                       │
  │  - 联系电话 / 地址 / 联系人                         │
  │  - 患者状态 / 临时标记 / 建档机构标识               │
  │                                                      │
  │  ┌──────────────────────────────────────────────┐    │
  │  │ PatientIdentifier（实体，0..N）               │    │
  │  │ - 证件类型编码（非身份证）                    │    │
  │  │ - 标识号 / 标识状态                           │    │
  │  │ - 有效开始时间 / 有效结束时间                 │    │
  │  └──────────────────────────────────────────────┘    │
  │                                                      │
  └──────────────────────────────────────────────────────┘
```

### 3.1 患者（Patient）

Patient 聚合根的属性定义，对应 `adm_patient` 表。


| 属性      | 字段名                   | 类型                   | 必填  | 说明                                                                          |
| ------- | --------------------- | -------------------- | --- | --------------------------------------------------------------------------- |
| 院内编码    | `bus_no`              | `String`             | 是   | 全局唯一，由业务序列服务生成，创建后不可修改                                                      |
| 姓名      | `name`                | `String`             | 是   | 患者姓名                                                                        |
| 姓名扩展    | `name_json`           | `String`             | 否   | 多语言/别名等，JSON 结构                                                             |
| 性别      | `gender_enum`         | `Gender`（值对象）        | 是   | 见 §4.2                                                                      |
| 出生日期    | `birth_date`          | `LocalDate`          | 否   | 可由身份证号或年龄推导，不强制手填                                                           |
| 患者状态    | `status_enum`         | `PatientStatus`（值对象）  | 是   | 生命周期状态，见 §2.3                                                           |
| 是否启用    | `active_flag`         | `Boolean`                | 是   | `status_enum == ACTIVE` 时为 `true`，其他状态为 `false`；与 FHIR `Patient.active` 对齐 |
| 临时标记    | `temp_flag`           | `Boolean`            | 是   | `true`=临时建档，`false`=正式建档                                                    |
| 身份证号    | `id_card`             | `String`             | 否   | 中国公民身份证号；**可空**（无证建档）；非空时全局唯一；**禁止**按年龄合成虚假证件号写入本字段 |
| 婚姻状态    | `marital_status_enum` | `MaritalStatus`（值对象） | 否   | 见 §4.5                                                                      |
| 职业编码    | `prfs_enum`           | `Integer`            | 否   | GB 职业类型编码（`OccupationType`），如 11=国家公务员、13=专业技术人员、27=农民、31=学生、90=其他等，完整枚举见码表 |
| 联系电话    | `phone`               | `String`             | 否   | 常用联系电话                                                                      |
| 主要地址    | `address`             | `Address`（值对象）       | 否   | 见 §4.4，含结构化地址与地址全文                                                          |
| 地址扩展    | `address_extend_json` | `String`             | 否   | 其他地址，JSON 结构                                                                |
| 民族编码    | `nationality_code`    | `String`             | 否   | 字典码表，非硬编码枚举                                                                 |
| 拼音码     | `py_str`              | `String`             | 否   | 姓名拼音首字母，辅助检索                                                                |
| 五笔码     | `wb_str`              | `String`             | 否   | 姓名五笔首字母，辅助检索                                                                |
| 血型      | `blood_type`          | `BloodType`（值对象）     | 否   | 见 §4.6                                                                      |
| 工作单位    | `work_company`        | `String`             | 否   | 工作单位名称                                                                      |
| 籍贯      | `native_place`        | `String`             | 否   | 籍贯                                                                          |
| 国家/地区编码 | `country_code`        | `String`             | 否   | 籍贯所属国家或地区编码                                                                 |
| 主要联系人   | `link_name`           | `ContactPerson`（值对象） | 否   | 见 §4.3，`link_name/link_relation_code/link_telcom`                           |
| 联系人扩展   | `link_jsons`          | `String`             | 否   | 其他联系人，JSON 结构                                                               |
| 死亡时间    | `deceased_date`       | `DateTime`           | 否   | 标记死亡时设置                                                                     |
| 死亡原因    | `deceased_reason`     | `String`             | 否   | 死亡原因描述                                                                      |
| 建档机构标识  | `organization_id`     | `OrganizationId`     | 是   | 外键，关联组织架构（Organization）                                                     |
> **审计字段**：继承自 `BaseAuditedEntity`（`createdAt` / `updatedAt` / `createdBy` / `updatedBy`），不在属性表中重复列出。

### 3.2 患者标识（PatientIdentifier）

患者标识子实体，承载**非身份证类**的证件/证明信息。与 Patient 为 1:N（N 可为 0）。

> **口径说明**：中国公民身份证号只存 `adm_patient.id_card`，**不**写入本子实体表。本子实体仅承载军官证、港澳台通行证、护照、学生证等其他可作为唯一编号使用的证件。


| 属性      | 字段名             | 类型                     | 必填  | 说明                              |
| ------- | --------------- | ---------------------- | --- | ------------------------------- |
| 标识子实体标识 | `id`            | `Long`                 | 是   | 聚合内唯一，主键                        |
| 证件类型编码  | `type_code`     | `String`               | 是   | 非身份证证件类型（军官证、护照等）               |
| 标识号     | `identifier_no` | `String`               | 是   | 证件号码                            |
| 标识状态    | `state_enum`    | `IdentifierState`（值对象） | 否   | 见 §4.7；新建证件默认为 `usual`（常用）      |
| 有效开始时间  | `start_time`    | `DateTime`             | 否   | 证件有效期起                          |
| 有效结束时间  | `end_time`      | `DateTime`             | 否   | 证件有效期止                          |
| 患者标识    | `patient_id`    | `PatientId`            | 是   | 外键，关联 Patient（`adm_patient.id`） |


#### 3.2.1 标识状态生命周期

标识状态用于描述证件在业务层面的用途与有效性，状态转换如下：

```
[新建] ──默认──> [usual（常用）]
    │               │
    │               ├──标记为官方──> [official（官方）]
    │               │
    │               ├──标记为临时──> [temp（临时）]
    │               │
    │               ├──标记为次要──> [secondary（次要）]
    │               │
    │               └──过期/作废──> [old（已失效）]
```

**状态转换规则**：


| 当前状态                                        | 可转换目标       | 触发条件                |
| ------------------------------------------- | ----------- | ------------------- |
| —（新建）                                       | `usual`     | 默认状态，新建证件自动赋值       |
| `usual`                                     | `official`  | 人工标记为最可信的官方证件       |
| `usual`                                     | `temp`      | 临时证件场景，需配合有效期字段     |
| `usual`                                     | `secondary` | 标记为辅助用途的次要证件        |
| `usual` / `official` / `temp` / `secondary` | `old`       | 证件过期、挂失、更换或账户合并     |
| `old`                                       | —           | 终止状态，不可恢复（保留用于查询追溯） |


> **状态互转约束**：
>
> - 状态一旦从 `usual` 指定为 `official`/`temp`/`secondary`，**不可双向互转**
> - 只能单向流转至 `old`，不可反向恢复
> - 若需更改证件用途，建议新建证件记录并废弃原记录

> **状态优先级**：同一患者同一证件类型下，`official` > `usual` > `secondary` > `temp`；业务校验时优先选取优先级最高的有效标识。

## 4. 值对象

### 4.1 患者状态（PatientStatus）

见 §2.3 状态定义中的枚举表。

### 4.2 性别（AdministrativeGender）


| 编码值 | 英文        | 说明  |
| --- | --------- | --- |
| 0   | `male`    | 男性  |
| 1   | `female`  | 女性  |
| 2   | `unknown` | 未知  |


> **存储口径**：对应主表字段 `gender_enum`，使用**字符串编码存储（存枚举 code）**。

### 4.3 联系人（ContactPerson）

封装紧急联系人/关系人的复合信息，避免散落的裸字段判断。


| 属性    | 类型        | 说明                                                                                  |
| ----- | --------- | ----------------------------------------------------------------------------------- |
| 联系人姓名 | `String`  | 联系人姓名                                                                               |
| 关系编码  | `Integer` | GB 家庭关系编码（`FamilyRelationshipType`），如 10=配偶、50=父母、20=子、30=女、70=兄弟姐妹、99=非亲属等，完整枚举见码表 |
| 联系电话  | `String`  | 联系人电话                                                                               |


### 4.4 地址（Address）

封装结构化地址信息，对应 Address` 数据类型。


| 属性   | 类型       | 说明                    |
| ---- | -------- | --------------------- |
| 地址全文 | `String` | 主联系地址全文（冗余字段，便于展示与检索） |
| 省    | `String` | 省级行政区                 |
| 市    | `String` | 市级行政区                 |
| 区    | `String` | 区/县级行政区               |
| 街道   | `String` | 街道详细地址                |


> **说明**：地址全文（`address`）与结构化字段（省/市/区/街道）并存。结构化字段用于标准化存储与统计，地址全文用于快速展示与检索。两者应保持一致。

### 4.5 婚姻状态（MaritalStatus）


| 编码值 | 编码   | 说明       |
| --- | ---- | -------- |
| 10  | `10` | 未婚       |
| 20  | `20` | 已婚       |
| 21  | `21` | 初婚       |
| 22  | `22` | 再婚       |
| 23  | `23` | 复婚       |
| 30  | `30` | 丧偶       |
| 40  | `40` | 离婚       |
| 90  | `90` | 未说明的婚姻状况 |


> **编码对齐**：采用中国国标婚姻状况编码。

### 4.6 血型（BloodType）

封装 ABO + Rh 的联合血型信息。

**ABO 血型（BloodTypeABO）**


| 编码值 | 说明   |
| --- | ---- |
| 1   | A 型  |
| 2   | B 型  |
| 3   | O 型  |
| 4   | AB 型 |
| 5   | 不详   |
| 9   | 未查   |


**Rh 血型（BloodTypeRH）**


| 编码值 | 说明    |
| --- | ----- |
| 1   | Rh 阴性 |
| 2   | Rh 阳性 |
| 3   | 不详    |
| 4   | 未查    |


> **存储口径**：主表采用双字段存储，`blood_type_abo` 存储 ABO 编码，`blood_type_rh` 存储 Rh 编码。两字段独立存储，允许任一为空（未查/不详场景）。值对象 `BloodType` 封装两字段并提供联合展示能力。

### 4.7 标识状态（IdentifierState）


| 编码值 | 英文          | FHIR值       | 说明                                      |
| --- | ----------- | ----------- | --------------------------------------- |
| 1   | `usual`     | `usual`     | 常用 — 当前推荐的标识符，用于实际交互与显示                 |
| 2   | `official`  | `official`  | 官方 — 最可信的官方标识符，有时称为"主标识"或"primary"      |
| 3   | `temp`      | `temp`      | 临时 — 临时分配的标识符，有效期有限                     |
| 4   | `secondary` | `secondary` | 次要 — 次要用途标识符，在相对上下文中识别对象，无法跨上下文一致分配     |
| 5   | `old`       | `old`       | 已失效 — 不再有效的旧标识符，保留用于搜索追溯（如证件更换、账户合并等场景） |


> **存储口径**：对应子表字段 `state_enum`，使用**字符串编码存储（存枚举 code）**。与 FHIR 交互时需进行编码转换（本地枚举 code ↔ FHIR 字符串）。

> **使用场景说明**：
>
> - `usual`：患者日常使用的主要证件标识（如护照），适用于一般业务交互
> - `official`：由权威机构颁发、最可信的标识（如军官证），适用于正式登记与核验
> - `temp`：临时证件或临时分配的标识号，有效期有限
> - `secondary`：辅助标识，如学生证在特定场景下的次要身份证明
> - `old`：已过期、已挂失或已更换的证件，保留用于历史查询与审计追溯

> **与有效期字段的配合**：
>
> - `start_time` / `end_time` 描述证件的自然有效期
> - `state_enum` 描述标识符在业务层面的用途与有效性状态
> - 当证件自然过期且未续期时，建议将状态置为 `old`
> - `old` 状态的标识符不参与"当前有效标识"的业务校验，但保留查询能力

## 5. 领域事件

### 5.1 事件定义


| 事件名称     | 英文名称                                | 触发时机             | 携带数据                             | 消费方            |
| -------- | ----------------------------------- | ---------------- | -------------------------------- | -------------- |
| 患者已建档    | `PatientRegisteredEvent`            | 建档成功（含临时建档和正式建档） | 患者标识, 院内编码, 姓名, 性别, 临时标记, 建档机构标识 | 挂号, 住院登记, 就诊   |
| 患者档案已修改  | `PatientProfileModifiedEvent`       | 修改患者人口学/联系信息成功   | 患者标识, 变更字段摘要                     | 档案同步, 医嘱       |
| 患者身份已确认  | `PatientIdentityConfirmedEvent`     | 临时建档转正式活跃成功      | 患者标识, 院内编码, 确认时间                 | 挂号, 住院登记       |
| 患者标识已同步  | `PatientIdentifiersSyncedEvent`     | 同步非身份证类证件子表成功    | 患者标识, 同步后标识数量                    | 档案同步           |
| 患者手机号已修改 | `PatientPhoneModifiedEvent`         | 快速更新手机号成功        | 患者标识, 新手机号                       | 消息通知, 预约       |
| 患者已停用    | `PatientDeactivatedEvent`           | 停用患者成功           | 患者标识, 停用原因                       | 挂号, 住院登记       |
| 患者已重新启用  | `PatientReactivatedEvent`           | 重新启用已停用患者成功      | 患者标识                             | 挂号, 住院登记       |
| 患者已合并    | `PatientMergedEvent`                | 合并患者成功           | 源患者标识, 目标患者标识, 合并时间, 合并原因, 操作人   | 挂号, 就诊, 医嘱, 费用 |
| 患者已标记死亡  | `PatientDeceasedMarkedEvent`        | 标记患者死亡成功         | 患者标识, 死亡时间, 死亡原因                 | 就诊, 医嘱, 费用, 统计 |
| 临时建档已废弃  | `PatientDraftDiscardedEvent`        | 废弃临时建档成功         | 患者标识, 院内编码                       | —              |
| 患者已逻辑删除  | `PatientDeletedEvent`               | 逻辑删除患者成功         | 患者标识, 院内编码, 操作时间                 | —              |


### 5.2 事件触发规则


| 当前状态          | 操作     | 目标状态             | 发布事件                                |
| ------------- | ------ | ---------------- | ----------------------------------- |
| —             | 临时建档   | 临时               | `PatientRegisteredEvent`            |
| —             | 正式建档   | 活跃               | `PatientRegisteredEvent`            |
| 临时            | 明确患者身份 | 活跃               | `PatientIdentityConfirmedEvent`     |
| 临时 / 活跃       | 修改档案   | 不变               | `PatientProfileModifiedEvent`       |
| 活跃            | 同步标识   | 不变               | `PatientIdentifiersSyncedEvent`     |
| 活跃            | 更新手机号  | 不变               | `PatientPhoneModifiedEvent`         |
| 活跃            | 停用     | 已停用              | `PatientDeactivatedEvent`           |
| 临时            | 停用     | 已停用              | `PatientDeactivatedEvent`           |
| 已停用           | 重新启用   | 活跃               | `PatientReactivatedEvent`           |
| 活跃            | 合并     | 已合并              | `PatientMergedEvent`                |
| 临时            | 合并     | 已合并              | `PatientMergedEvent`                |
| 活跃            | 标记死亡   | 不变               | `PatientDeceasedMarkedEvent`        |
| 临时            | 标记死亡   | 不变               | `PatientDeceasedMarkedEvent`        |
| 已停用           | 标记死亡   | 不变               | `PatientDeceasedMarkedEvent`        |
| 临时            | 废弃     | 已废弃              | `PatientDraftDiscardedEvent`        |
| 活跃 / 已停用 / 临时 | 逻辑删除   | 不变（isDeleted 置位） | `PatientDeletedEvent`               |


## 6. 领域服务

### 6.1 写侧领域服务（PatientDomainService）

#### 6.1.1 新建患者档案（`registerPatient`）

- **业务意义**：创建患者主档，生成可被全院业务稳定引用的 `PatientId` 与院内编码。
- **边界说明**：仅负责 Patient 聚合建档与聚合内初始化，不处理挂号、收费、医保等跨域编排。
- **入参**：姓名、性别、身份证号（可选）、联系电话（可选）、临时标记、建档机构标识、非身份证证件列表（可选）。
- **出参**：患者标识（`PatientId`）。
- **前置条件**：校验最小必填（姓名+性别）；身份证号非空时校验唯一性。
- **状态变化**：根据临时标记初始化为 `draft` 或 `active`。
- **聚合内影响**：写入主表；生成 `busNo`；初始化检索辅助码（拼音码/五笔码）；可同事务同步非身份证证件子表。
- **事件输出**：`PatientRegisteredEvent`。
- **失败语义**：必填缺失、身份证号冲突、机构信息非法时拒绝建档。

#### 6.1.2 修改患者档案（`modifyPatientProfile`）

- **业务意义**：维护患者人口学与联系信息，保障主档信息持续可用。
- **边界说明**：仅更新 Patient 聚合内数据，不负责编排下游业务状态变更；不改变患者状态。
- **入参**：患者标识、变更字段集合（含可选非身份证证件列表）。
- **出参**：—。
- **前置条件**：患者须为 `draft` 或 `active`；身份证号变更时需唯一；年龄与出生日期需交叉校验。
- **状态变化**：`N/A`（修改档案不改变患者状态，状态变更需调用 `confirmPatientIdentity`）。
- **聚合内影响**：更新主表字段与检索辅助码；可同事务调用 `syncPatientIdentifiers` 对齐子表。
- **事件输出**：`PatientProfileModifiedEvent`。
- **失败语义**：患者状态不允许、身份证冲突、年龄生日不一致时拒绝修改。

#### 6.1.4 明确患者身份（`confirmPatientIdentity`）

- **业务意义**：将临时建档患者转为正式活跃状态，确认其身份信息可用于就诊业务。
- **边界说明**：仅变更患者状态，不修改人口学信息；人口学信息需先通过 `modifyPatientProfile` 补全。
- **入参**：患者标识。
- **出参**：—。
- **前置条件**：患者须为 `draft`；必填字段（姓名、性别）已填写。
- **状态变化**：`draft -> active`。
- **聚合内影响**：更新主表 `status_enum` 为 `ACTIVE`；`active_flag` 置 `true`；`temp_flag` 置 `false`。
- **事件输出**：`PatientIdentityConfirmedEvent`。
- **失败语义**：状态非 `draft` 或必填字段缺失时拒绝确认。

#### 6.1.3 同步患者标识子表（`syncPatientIdentifiers`）

- **业务意义**：将非身份证证件集合与 `PatientIdentifier` 子实体对齐，保证证件口径一致。
- **边界说明**：只维护 Patient 聚合内标识子表，不处理外部系统证件同步。
- **入参**：患者标识、非身份证证件列表（含可选状态字段）。
- **出参**：—。
- **前置条件**：患者须为 `active`；证件列表不得包含身份证类型；`typeCode + identifierNo` 有效期内唯一。
- **状态变化**：
  - 新增证件：默认状态为 `usual`（常用），可根据入参指定 `official`/`temp`/`secondary`
  - 更新证件：保持原状态，或根据入参更新状态
- 删除证件：逻辑删除（`isDeleted` 置位；必要时将状态标记为 `old` 以体现历史不可逆）
  - 过期证件：若 `end_time` 已过当前时间且未手动标记，建议将状态置为 `old`
- **聚合内影响**：按策略同步子表（delete+insert 或按 typeCode upsert）；无证件项时允许清空并保持 0 条；同步时按 §3.2.1 状态优先级规则校验。
- **事件输出**：`PatientIdentifiersSyncedEvent`。
- **失败语义**：证件类型非法、证件冲突、有效期不合法、状态值非法时拒绝同步。

#### 6.1.5 更新患者手机号（`updatePatientPhone`）

- **业务意义**：快速维护主联系方式，降低全档案修改成本。
- **边界说明**：只更新 `phone` 字段，不触发档案全量更新流程。
- **入参**：患者标识、新手机号。
- **出参**：—。
- **前置条件**：患者须为 `active`；手机号格式合法。
- **状态变化**：`N/A`。
- **聚合内影响**：仅更新主表 `phone`。
- **事件输出**：`PatientPhoneModifiedEvent`。
- **失败语义**：患者非活跃或手机号格式不合法时拒绝更新。

#### 6.1.5a 更新患者地址全文（`updatePatientAddress`）

- **业务意义**：窄写主档地址全文（挂号读卡等），不触碰省市区拆分字段。
- **边界说明**：只更新嵌入 `Address.text`；无嵌入地址时新建；不走全档案 `modifyPatientProfile`。
- **入参**：`UpdatePatientAddressCommand`（患者标识、地址全文）。
- **出参**：—。
- **前置条件**：患者允许改地址（实体 `assertAddressUpdatable`）；地址全文非空。
- **状态变化**：`N/A`。
- **聚合内影响**：主表地址嵌入字段；持久化后发事件。
- **事件输出**：`PatientAddressModifiedEvent`。
- **失败语义**：地址为空 → `PATIENT_INVALID_ARGUMENT`；状态不允许则拒绝。

> **变更（2026-09-23 · 模式 B）**：按代码回写补齐 `updatePatientAddress`。

#### 6.1.6 停用患者（`deactivatePatient`）

- **业务意义**：将患者从可新发起业务的活跃集合中移出，同时保留历史数据。
- **边界说明**：只改变 Patient 状态，不直接关闭下游业务单据。
- **入参**：患者标识、停用原因。
- **出参**：—。
- **前置条件**：患者须为 `active`；当前无进行中的 Encounter（非终止态）。
- **状态变化**：`active -> inactive`。
- **聚合内影响**：更新患者状态与停用信息。
- **事件输出**：`PatientDeactivatedEvent`。
- **失败语义**：存在进行中就诊或状态不合法时拒绝停用。

#### 6.1.7 重新启用患者（`reactivatePatient`）

- **业务意义**：恢复已停用患者为可用状态。
- **边界说明**：只恢复 Patient 聚合状态，不回补停用期间外部业务数据。
- **入参**：患者标识。
- **出参**：—。
- **前置条件**：患者须为 `inactive`。
- **状态变化**：`inactive -> active`。
- **聚合内影响**：更新主表状态为活跃。
- **事件输出**：`PatientReactivatedEvent`。
- **失败语义**：状态非 `inactive` 时拒绝启用。

#### 6.1.8 合并患者（`mergePatient`）

- **业务意义**：处理重复建档，将源患者并入目标患者并保留审计轨迹。
- **边界说明**：仅负责源患者状态与合并关系记录；下游引用迁移由事件驱动异步处理。
- **入参**：源患者标识、目标患者标识。
- **出参**：—。
- **前置条件**：源/目标均须为 `active`；两者不得相同。
- **状态变化**：源患者 `active -> merged`；目标患者状态不变。
- **聚合内影响**：
  - 主表：记录合并目标与合并时间，冻结源患者独立使用能力
  - 子表：源患者 `PatientIdentifier` 子表保留原状，不迁移到目标患者（历史审计需要）；如需合并证件，需单独调用 `syncPatientIdentifiers`
- **事件输出**：`PatientMergedEvent`。
- **失败语义**：目标非法、状态不合法或自合并时拒绝执行。

#### 6.1.9 标记患者死亡（`markPatientDeceased`）

- **业务意义**：记录死亡事实用于医疗与统计语义，不改变患者主状态机。
- **边界说明**：仅维护死亡信息，不直接触发停用或删除。
- **入参**：患者标识、死亡时间、死亡原因。
- **出参**：—。
- **前置条件**：患者须为 `active`；死亡时间不得晚于当前时间。
- **状态变化**：`N/A`（不改变患者状态）。
- **聚合内影响**：更新 `deceasedDate/deceasedReason`。
- **事件输出**：`PatientDeceasedMarkedEvent`。
- **失败语义**：时间非法或状态不合法时拒绝标记。

#### 6.1.10 废弃临时建档（`discardDraftPatient`）

- **业务意义**：清理未实际投入业务的临时患者记录。
- **边界说明**：只允许处理草稿态且无业务关联的数据，不清理已使用患者。
- **入参**：患者标识。
- **出参**：—。
- **前置条件**：患者须为 `draft`；未产生任何业务关联（就诊、费用等）。
- **状态变化**：`draft -> cancelled`。
- **聚合内影响**：更新状态为终止态并保留审计信息。
- **事件输出**：`PatientDraftDiscardedEvent`。
- **失败语义**：存在业务关联或状态不合法时拒绝废弃。

#### 6.1.11 逻辑删除患者（`deletePatient`）

- **业务意义**：将患者从默认可见主数据集中移除，同时保留历史与审计可追溯性。
- **边界说明**：仅执行逻辑删除，不物理删除；跨聚合引用保护只做前置校验。
- **入参**：患者标识。
- **出参**：—。
- **前置条件**：患者不得处于 `merged/cancelled` 终止态；存在活跃引用时不可删除。
- **状态变化**：`N/A`（状态不变，`isDeleted` 置位）。
- **聚合内影响**：主表逻辑删除标记置位，默认查询不可见。
- **事件输出**：`PatientDeletedEvent`。
- **失败语义**：引用未释放、状态不允许时拒绝删除。

### 6.2 读侧查询服务（PatientQueryService）

> 查询类动作无写库，以"查询范围（读库，无写入）+ 组装规则"表达QueryService；用于 UI 初始化、列表、详情回显与资格校验门控。

#### 6.2.1 分页检索患者（`searchPatientsPaged`）

- **业务意义**：支持列表页按条件稳定检索患者主档。
- **边界说明**：只读查询，不写库，不做跨域编排。
- **查询范围（读库，无写入）**：`adm_patient` 主表，必要时批量附带非身份证标识子表行。
- **过滤口径**：遵循 BR-PAT-019/020（默认过滤逻辑删除；身份证口径以主表为准）。
- **组装规则**：主表为主，子表按需附带，不在读侧回写枚举中文。
- **返回约束**：分页返回，字段口径与主子表分工一致。

#### 6.2.2 加载患者详情（`loadPatientDetail`）

- **业务意义**：为编辑回显提供完整患者详情视图。
- **边界说明**：只读查询，不写库，不做跨域编排。
- **查询范围（读库，无写入）**：按主键查询主表与标识子表列表。
- **过滤口径**：默认排除已逻辑删除数据。
- **组装规则**：主表详情 + 非身份证标识子表合并组装。
- **返回约束**：患者不存在/已删除/无权限需抛明确异常；子表允许为空。

#### 6.2.3 建档同一人校验（`checkRegistrationDuplicatePatient`）

- **业务意义**：挂号建档前校验是否已存在同一患者，减少重复建档。
- **边界说明**：只读校验，不创建、不修改 Patient。
- **查询范围（读库，无写入）**：患者主档及必要索引字段。
- **过滤口径**：按 BR-PAT-014 组装条件（姓名+性别必选，身份证/生日/年龄非空项需一致）。
- **组装规则**：返回“是否命中重复 + 候选记录”结果（通常 0～1 条）。
- **返回约束**：命中后提示检索复用，不应直接继续 `registerPatient`。

#### 6.2.4 根据身份证号查询患者（`findPatientByIdCard`）

- **业务意义**：根据身份证号精确查找患者，用于建档前校验与患者识别场景。
- **边界说明**：只读查询，不写库，不做跨域编排。
- **查询范围（读库，无写入）**：`adm_patient` 主表，按 `id_card` 索引查询。
- **过滤口径**：排除已逻辑删除、已合并、已废弃记录。
- **组装规则**：返回患者基本信息（标识、姓名、性别、出生日期）。
- **返回约束**：未找到返回空，不抛异常；找到多条时视为主数据冲突并抛业务异常（`PATIENT_ID_CARD_CONFLICT`），禁止以“取最新一条”方式继续业务流程。

#### 6.2.5 批量加载患者详情（`batchLoadPatientDetails`）

- **业务意义**：批量获取患者详情，用于列表回显、报表导出等场景。
- **边界说明**：只读查询，不写库，不做跨域编排。
- **查询范围（读库，无写入）**：`adm_patient` 主表 + `adm_patient_identifier` 子表（IN 查询）。
- **过滤口径**：排除已逻辑删除数据；入参患者标识列表上限 100 条。
- **组装规则**：主表信息 + 子表标识列表按患者标识分组组装。
- **返回约束**：以 Map 结构返回（患者标识 -> 患者详情）；未找到的标识不出现在结果中，不抛异常。

#### 6.2.6 批量解析患者姓名（`findPatientNamesByIds`）

- **业务意义**：工作台/队列等轻量场景仅回显姓名，避免加载完整详情。
- **边界说明**：只读查询，不写库；与 **6.2.7** 分工——本方法仅 `name`，不含性别/生日/年龄。
- **查询范围（读库，无写入）**：`adm_patient` 主表，`findAllByIdIn`。
- **过滤口径**：遵循 BR-PAT-019；批量上限与系统 `BATCH_LIMIT` 一致。
- **组装规则**：`Map<患者标识, 姓名字符串>`，缺失患者无条目。
- **返回约束**：空入参返回空映射；超限抛参数异常。

#### 6.2.7 批量加载住院信息投影人口学（`mapInpatientProjectionSnapshotsByIds`）

- **业务意义**：为应用层 **`InpatientEncounterContextLoader`** / **`EncounterContextReader`** 组装 `InpatientEncounterContextQueryModel` 时提供姓名/性别/生日/年龄快照；支撑住院登记、预交金、护士站队列关键字检索。
- **边界说明**：只读查询，不写库；**不**发布领域事件；由应用层就诊上下文 Loader 调用（见 **DM-ADM-002 §6.2.19**、**§6.2.7**）。
- **查询范围（读库，无写入）**：`adm_patient` 主表列 `name`、`gender_enum`、`birth_date`。
- **过滤口径**：遵循 BR-PAT-019；批量上限与 `BATCH_LIMIT` 一致。
- **组装规则**：出参 `PatientInpatientProjectionSnapshot`（`patientName`、`genderEnum` 存枚举 **code 字符串**、`birthDate` 为日初 `LocalDateTime`、`ageText` 由 `birth_date` 派生）。
- **返回约束**：`Map<患者标识, 快照>`；**不含** `id_card`（身份证仍仅存主表，QueryService无列，见 **BR-PAT-023**）；未找到的 ID 无条目。

#### 6.2.8 就诊列表关键字反查患者主键（`listPatientIdsByEncounterListKeyword`）

- **业务意义**：门诊/住院就诊列表按患者姓名、院内编码、身份证、拼音/五笔关键字检索时，先解析 `patient_id` 集合供应用层收窄 `adm_encounter`（**EncounterContextReader** 编排）。
- **边界说明**：只读；`Specification` OR 匹配 `name`/`bus_no`/`id_card`/`py_str`/`wb_str`；不写库。
- **入参**：关键字；空白返回空列表。
- **出参**：`List<Long>` 患者主键。

### 6.3 领域边界说明

- `Patient` 领域动作只描述 `Patient` 聚合根及其子实体 `PatientIdentifier` 的业务行为。
- 不把支付、账户流水、号源释放等跨域步骤写入患者领域动作；跨域步骤由对应领域文档与应用层编排说明。
- 查询类动作无写库操作，以QueryService表达，遵循 CQRS 分离原则。

## 7. 不变式


| 编号         | 不变式                                                                                                               | 说明          |
| ---------- | ----------------------------------------------------------------------------------------------------------------- | ----------- |
| BR-PAT-001 | 建档时必须提供 `姓名` 和 `性别`，`出生日期` 可由身份证号或年龄推导，不要求手填                                                                      | 最小必填约束      |
| BR-PAT-002 | `身份证号` 在系统范围内唯一（含已逻辑删除记录），不允许与其他患者冲突；读侧按身份证查询若命中多条须抛冲突异常，禁止降级取最新                                                                               | 身份唯一性       |
| BR-PAT-003 | `院内编码(busNo)` 全局唯一，由业务序列服务生成，创建后不可修改；是否绑定固定序列枚举以当期配置为准                                                            | 院内主索引唯一性    |
| BR-PAT-004 | 身份证号只存主表 `idCard`，子表 `PatientIdentifier` 不得包含身份证类型记录                                                              | 主子表分工不变式    |
| BR-PAT-005 | 子表同一 `typeCode + identifierNo`（有效期内）不得与其他患者冲突                                                                     | 证件唯一性       |
| BR-PAT-006 | 标识有效期若 `startTime/endTime` 同时存在，须 `startTime <= endTime`；状态为 `old` 的标识不参与"当前有效标识"匹配                               | 有效期合法性      |
| BR-PAT-007 | 同一 `patientId` 下同一 `typeCode` 仅保留一条状态为 `usual`/`official` 的有效记录；状态优先级：`official` > `usual` > `secondary` > `temp` | 同类型标识幂等与优先级 |
| BR-PAT-008 | `已合并` 和 `已废弃` 为终止状态，终止后不可恢复到非终止状态                                                                                 | 终止态不可逆      |
| BR-PAT-009 | 被其他聚合（Encounter、MedicationRequest 等）活跃引用的患者不得物理删除                                                                 | 引用完整性保护     |
| BR-PAT-010 | 合并操作的目标患者须为 `active` 状态，且源患者不得与目标相同                                                                               | 合并目标合法性     |
| BR-PAT-011 | 标记死亡时 `deceasedDate` 不得晚于当前时间，且死亡后不自动变更患者状态（对齐 FHIR `Patient.deceased[x]` 独立于 `Patient.active` 的设计）               | 死亡标记合法性     |
| BR-PAT-012 | 联系电话格式须符合预设规则（长度、前缀等），避免明显脏数据                                                                                     | 联系方式格式校验    |
| BR-PAT-014 | 挂号建档前同一人校验：姓名+性别必参与，身份证号/生日/年龄非空项须全部一致方可视为同一人；仅姓名+性别相同但无可比对项或比对不一致 → 不视为同一人                                       | 重复建档防控      |
| BR-PAT-015 | 子表落库条件：仅当用户选择并填写至少一种非身份证证件时，才向 `adm_patient_identifier` 写入或同步；仅身份证建档则不插入子表                                        | 子表写入前置条件    |
| BR-PAT-016 | 年龄与生日一致性：若输入年龄，应折算并校验与 `birthDate` 合理一致（允许一定误差），避免明显错误                                                            | 年龄生日交叉校验    |
| BR-PAT-017 | 联系人完整性：如配置要求（儿童/老年等），必须提供至少一个联系人（姓名+关系+电话）                                                                        | 联系人条件约束     |
| BR-PAT-018 | 逻辑删除语义：患者删除为逻辑删除（`isDeleted`），不物理删除，默认查询过滤 `isDeleted = '0'`                                                      | 删除语义约束      |
| BR-PAT-019 | 默认查询可见性：读动作默认仅包含未逻辑删除主档；特殊审计/运维只读场景以落库与交互契约为准                                                                          | 查询口径约束      |
| BR-PAT-020 | 标识子表读口径：身份证以主表 `idCard` 为准；子表仅表达非身份证证件，允许为空；历史误写入子表的身份证类型行的展示/清洗以落库与交互契约为准                                             | 查询服务口径约束     |
| BR-PAT-021 | 标识状态有效性：新建证件默认状态为 `usual`；状态为 `old` 的标识不可恢复为其他状态，仅保留用于历史查询                                                        | 标识状态不可逆     |
| BR-PAT-022 | 标识状态与有效期配合：当证件 `endTime` 已过当前时间且状态非 `old` 时，系统可自动将其标记为 `old`；状态为 `temp` 的证件须填写有效期                                 | 标识状态自动化规则   |
| BR-PAT-023 | 住院就诊上下文人口学：`patient_name`/`gender_enum`/`birth_date`/`age_text` 由应用层经 **§6.2.7** `mapInpatientProjectionSnapshotsByIds` 回查 `adm_patient` 写入 `InpatientEncounterContextQueryModel`；**不**经 Encounter 事件载荷携带；`id_card` **不**写入上下文模型（卡片/详情由 App 按 `patient_id` 查主档） | 读组装口径     |


## 8. 上下文映射

### 8.1 上游依赖


| 上游上下文 | 聚合           | 关联方式                     | 说明        |
| ----- | ------------ | ------------------------ | --------- |
| 组织架构  | Organization | `机构科室标识(OrganizationId)` | 建档所属机构/科室 |


### 8.2 下游影响


| 下游上下文 | 聚合                 | 触发方式 | 说明                               |
| ----- | ------------------ | ---- | -------------------------------- |
| 就诊管理  | Encounter          | 引用   | 就诊通过 `患者标识(PatientId)` 关联患者      |
| 就诊账户  | Account            | 引用   | 账户通过 `患者标识(PatientId)` 关联患者      |
| 药品申请  | MedicationRequest  | 引用   | 药品医嘱通过 `患者标识(PatientId)` 关联患者    |
| 服务申请  | ServiceRequest     | 引用   | 服务申请通过 `患者标识(PatientId)` 关联患者    |
| 临床诊断  | Condition          | 引用   | 诊断记录通过 `患者标识(PatientId)` 关联患者    |
| 过敏管理  | AllergyIntolerance | 引用   | 过敏信息通过 `患者标识(PatientId)` 关联患者    |
| 收费管理  | ChargeItem         | 引用   | 收费明细间接通过就诊关联患者                   |
| 病历管理  | Composition        | 引用   | 病历文书通过就诊关联患者                     |
| 住院管理  | EpisodeOfCare      | 引用   | 住院全程通过 Encounter 间接关联患者          |
| 住院就诊上下文 | `EncounterContextReader` 住院队列 | 请求级/里程碑后回查 | 登记/预交金/护士站列表经 **§6.2.7** `PatientQueryService#mapInpatientProjectionSnapshotsByIds` 回填人口学列（**非** InpatientInfo RM） |
| 医保/财务 | Financial / YB     | 依赖   | 收费、医保登记以 Patient 标识作为账户/人员基础信息来源 |


### 8.3 引用规则

> **统一规则**：所有需要引用患者的领域模型，遵循以下原则：
>
> - 患者引用统一使用 `患者标识(PatientId)`（对应 FHIR `subject` 字段），不使用"患者 ID"或"subject"
> - 患者为**只读引用**——下游聚合通过 ID 引用，不直接持有 Patient 对象
> - 患者合并后，下游聚合通过 `PatientMergedEvent` 异步迁移引用
> - **CQRS 领域事件后查询刷新**：住院队列展示人口学可由QueryService同步回查本聚合主表（**§6.2.7**），不要求 Encounter 事件携带姓名/性别；患者主档变更后QueryService列更新依赖再次触发住院里程碑事件重投影或 App 层请求级回填（**BR-PAT-023**）

### 8.4 事件流示意图

```
Patient                 Encounter        Account        MedicationRequest     AllergyIntolerance
    │                       │                │                 │                      │
    │── RegisteredEvent ──>│ (可开立就诊)    │                 │                      │
    │                       │                │                 │                      │
    │── MergedEvent ──────>│ (迁移引用)      │ (迁移引用)      │ (迁移引用)           │ (迁移引用)
    │                       │                │                 │                      │
    │── DeactivatedEvent ─>│ (拒绝新就诊)    │                 │                      │
    │                       │                │                 │                      │
    │── DeceasedMarked ───>│                 │                 │                      │
    │                       │                │                 │                      │
    │── DeletedEvent ─────>│ (引用保护校验)  │ (引用保护校验)  │                      │
    │                       │                │                 │                      │
```

## 9. 补充

### 9.1 患者合并流程说明

患者合并是 HIS 中处理重复建档的核心业务流程：

1. **识别重复**：通过 `BR-PAT-014` 的同一人校验规则发现疑似重复记录
2. **确认合并**：人工确认两条记录确属同一患者后发起合并
3. **执行合并**（`mergePatient`）：源患者状态转为 `merged`，记录合并目标
4. **下游迁移**：通过 `PatientMergedEvent` 通知下游聚合异步迁移引用（就诊、医嘱、费用等仍保留历史记录，但新业务指向目标患者）
5. **审计保留**：源患者记录永久保留用于审计追溯，不物理删除

## 10. 变更记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v1.3 | 2026-07-19 | **学籍迁出 Campus**：移除 `adm_patient_student` / `PatientStudent`、相关事件/服务/BR-PAT-013；学籍真源见 `cmp_patient_student` / DM-CMP-STU-001 |
| v1.2.1 | 2026-05-28 | **M1**：§6.2.8 `listPatientIdsByEncounterListKeyword` 对齐 Java |
| v1.2 | 2026-05-28 | §6.2.9 / **BR-PAT-023** / §8.2：废止 **InpatientInfo** RM；人口学回填改 **EncounterContextFacade** + **DM-ADM-002** v1.24 |（已迁至 EncounterContextReader / application.shared.encounter）
| v1.1 | 2026-05-20 | 新增 **§6.2.8** `findPatientNamesByIds`、**§6.2.9** `mapInpatientProjectionSnapshotsByIds`；**BR-PAT-023** 住院人口学投影口径；§8.2 住院队列消费方；对齐 **DM-ADM-002** v1.18 |
| v1.0 | （既有） | 初版 |

<!-- MODE-B-METHOD-INVENTORY 2026-09-23 -->

## 附录 · 模式 B 已实现方法清单（2026-09-23）

> 自 zxplat-domain 对应 *DomainService / *QueryService 抽取 public 方法名，用于关闭方法白名单 DOC-DRIFT。细规仍以正文 §6 与源码为准；本表不替代九章成稿。

### DomainService：`PatientDomainService`

| 方法 |
|------|
| `registerPatient` |
| `modifyPatientProfile` |
| `syncPatientIdentifiers` |
| `confirmPatientIdentity` |
| `updatePatientPhone` |
| `updatePatientAddress` |
| `deactivatePatient` |
| `reactivatePatient` |
| `mergePatient` |
| `markPatientDeceased` |
| `discardDraftPatient` |
| `deletePatient` |

### QueryService：`PatientQueryService`

| 方法 |
|------|
| `listPatientIdsByEncounterListKeyword` |
| `listPatientIdsByNameKeyword` |
| `searchPatientsPaged` |
| `loadPatientDetail` |
| `checkRegistrationDuplicatePatient` |
| `findPatientByIdCard` |
| `batchLoadPatientDetails` |
| `findPatientNamesByIds` |
| `loadPatientEntityMapByIds` |
| `mapInpatientProjectionSnapshotsByIds` |
| `findOptionalPatientEntityById` |

