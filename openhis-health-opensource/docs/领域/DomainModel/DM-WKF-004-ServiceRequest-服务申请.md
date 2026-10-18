# DM-WKF-004 ServiceRequest 服务申请

> **限界上下文**：工作流域 · 服务申请管理（非药品医嘱）\
> **聚合类型**：实体聚合\
> **版本**：v1.1.41

---

## 1. 概述

### 1.1 业务定义

服务申请（ServiceRequest）用于表达一次非药品诊疗请求（检验、检查、治疗、护理、转科、出院等）从开立到执行终止的全过程，是工作流域连接执行、计费、就诊流程联动的核心锚点。

### 1.2 核心职责

| 职责 | 说明 |
| --- | --- |
| 非药品医嘱生命周期管理 | 管理草稿、签发、校对、停止、取消等状态迁移 |
| 多类别诊疗请求统一承载 | 通过 `categoryEnum` 统一承载治疗/检验/检查/护理/转科/出院等 |
| 多来源建单 | 支持医生开立、护士划价、挂号登记、执行复制、自动滚费 |
| 派生链路追溯 | 通过 `basedOnTable + basedOnId` 建立来源追溯关系 |
| 成组/套餐支持 | 通过 `groupId`、`requisitionId`、`parentId` 管理成组与父子项 |
| 跨域协作锚点 | 作为 `ChargeItem`、`Procedure`、`EncounterHistory` 的关联来源 |

### 1.3 适用范围

| 场景 | 说明 |
| --- | --- |
| 医生站开立 | 医生创建/修改/签发诊疗草稿 |
| 护士站划价 | 护士划价后直接生成 **已校对（VERIFIED）** 申请，不经校对 Tab |
| 挂号登记 | 挂号场景生成服务申请并进入可执行状态 |
| 住院执行复制 | 长期医嘱按执行步次复制生成完成态记录 |
| 转科/出院管理 | 转科申请、出院申请以 ServiceRequest 统一承载 |

---

## 2. 聚合根与持久化形态

### 2.1 持久化形态

**持久化形态**：主表 + 子表。

聚合根对应主表，明细子实体对应子表，通过外键 `service_req_id` 关联。聚合边界以主表主键 `id`（服务申请标识）划分，主表一行对应子表 N 行（0 ≤ N）。

**表结构**：

- 主表：`wkf_service_request`（主键：`id`）
- 子表：`wkf_service_request_detail`（外键：`service_req_id`）
- 关系：1:N，子表行数不限，可为 0

**高频查询模式**：

- 按服务申请标识：主键查询主表，按需 JOIN 明细。
- 按就诊：主表 `encounter_id` 索引扫描。
- 按患者历史：主表 `patient_id` + 就诊过滤（排除当前就诊见读侧方法）。
- 按诊疗定义：主表 `activity_id` 索引扫描。
- 冲销与步次：按 `based_on_id` / `refund_service_id` 等字段做派生与冲销统计（以实现索引为准）。

### 2.2 聚合根标识

| 属性 | 类型 | 说明 |
| --- | --- | --- |
| 服务申请标识 | `ServiceRequestId` | 主键，雪花或序列策略以落库与交互契约为准（`id`） |

### 2.3 状态机（RequestStatus）

```text
医生开立链路

               ┌─────────────┐
               │    DRAFT    │  ← 创建/修改/退回
               └──────┬──────┘
                      │ 签发
                      ▼
               ┌─────────────┐
               │   ACTIVE    │
               └──────┬──────┘
              ┌───────┴────────────┐
     病区护士校对 ▼                  ▼  停止 / 取消
       ┌─────────────┐      ┌─────────────┐
       │   VERIFIED  │      │ STOPPED/    │
       └──────┬──────┘      │ CANCELLED   │
              │ 停止/取消/退回└─────────────┘
              ▼
       STOPPED / CANCELLED / DRAFT

     门诊收费收口（不进 VERIFIED）：
       ACTIVE ──completeServiceRequestAfterOutpatientCharge──> COMPLETED

护士划价链路：创建即 VERIFIED（不经校对 Tab；写入校对人/校对时间）
挂号登记链路：创建即 ACTIVE
执行复制/药品绑定链路：创建即 COMPLETED
```

#### 状态定义

持久化 **`status_enum`** 存 **`RequestStatus#getCode()`（英文字符串）**。下列 **`sortOrder`** 来自共享枚举 **`com.zxplat.domain.share.enums.RequestStatus`**，**仅排序/文档对照**，**禁止当作落库码**。

| sortOrder（对照） | **落库 code（String）** | 中文 display | 语义（与本聚合常见迁移） |
| ----- | ---- | ---- | ---- |
| 1 | `DRAFT` | 待签发 | 草稿态；医生开立链路起点 |
| 2 | `ACTIVE` | 已发送 | 签发后履约；挂号登记路径常创建即此态 |
| 3 | `VERIFIED` | 已校对 | **病区护士校对**：`verifyServiceRequest` → `activeToVerified`；**护士划价**：`createFromNursePricing` → `initVerified`；**医技划价**：`createFromMedicalTechPricing` → `initVerified`（不经校对 Tab） |
| 4 | `HOLD` | 暂挂 | 暂停 |
| 5 | `CANCELLED` | 已取消 | 终止（取消） |
| 6 | `STOPPED` | 已停嘱 | 终止（停嘱） |
| 7 | `COMPLETED` | 已完成 | **门诊收费收口**、执行复制/绑定等；**不是**住院校对态 |

> **状态枚举说明**：与 **`DM-WKF-003` §4.4** 同源枚举定义。本聚合 **`ServiceRequest`** 医生开立主路径常见 **`DRAFT` ↔ `ACTIVE` → `VERIFIED`（住院校对）** 或 **`ACTIVE` → `COMPLETED`（门诊收费收口）**，以及 **`STOPPED` / `CANCELLED`**（见实体方法与 **`ServiceRequestDomainService`**）。**`VERIFIED` 与 `COMPLETED` 两码事，禁止混用**（对齐 **AS-IP-NUR-003**）。

> **补充**：`DRAFT` 删除一般为物理删除或等价移除策略（以落库与交互契约为准），受已收费约束。

---

## 3. 实体

```
ServiceRequest（聚合根）
════════════════════════════════════════════════════════════

  ┌──────────────────────────────────────────────────────┐
  │                    ServiceRequest                     │
  │  - statusEnum / categoryEnum / therapyEnum           │
  │  - generateSourceEnum / intentEnum / priorityEnum    │
  │  - encounterId / patientId / activityId              │
  │  - basedOnTable / basedOnId（来源追溯）               │
  │  - requisitionId / groupId / parentId（分组/套餐）    │
  │                                                      │
  │  ┌──────────────────────────────────────────────┐    │
  │  │ ServiceRequestDetail（子实体，0..N）          │    │
  │  │ - serviceReqId / targetTable / targetId    │    │
  │  │ - remark                                     │    │
  │  └──────────────────────────────────────────────┘    │
  └──────────────────────────────────────────────────────┘
```

### 3.1 服务申请（ServiceRequest）

ServiceRequest 聚合根的属性定义，对应 `wkf_service_request` 表。

| 属性 | 字段名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| 服务申请标识 | `id` | `Long` | 是 | 主键 |
| 服务编码 | `bus_no` | `String` | 否 | 业务号 |
| 处方号 | `prescription_no` | `String` | 否 | 申请单号/处方号 |
| 来源表名 | `based_on_table` | `String` | 否 | 如 `wkf_service_request` / `med_medication_request` |
| 来源标识 | `based_on_id` | `Long` | 否 | 派生来源主键 |
| 组合标识 | `requisition_id` | `Long` | 否 | 同组申请 |
| 请求状态 | `status_enum` | `String` | 是 | 见 §2.3（存枚举 code） |
| 请求意图 | `intent_enum` | `String` | 否 | 字符串预留，见 §4.2（存枚举 code） |
| 诊疗类别 | `category_enum` | `String` | 是 | 见 §4.3（存枚举 code） |
| 分组编号 | `group_id` | `Long` | 否 | 成组 |
| 优先级 | `priority_enum` | `String` | 否 | 见 §4.4（存枚举 code） |
| 执行标志 | `perform_flag` | `Boolean` | 否 | 停止执行等标志 |
| 诊疗定义标识 | `activity_id` | `Long` | 是 | 关联 ActivityDefinition；**手术术式行 / 转科出院等无计费锚时存 `0` 占位** |
| 国临手术定义标识 | `procedure_definition_id` | `Long` | 否 | 术式行临床真源（`cln_procedure_definition.id`）；加收/麻醉/术前嘱托为空；病案/展示/筛选认本列。**加收由护士划价**；**术前嘱托 basedOn 术式 SR**（手麻开立唯一主子） |
| 请求数量 | `quantity` | `BigDecimal` | 是 | **账单计价乘数**（与 `unit_code` 同语义；长嘱为单次执行量，与药品 quantity 一致） |
| 请求单位编码 | `unit_code` | `String` | 是 | 账单计价单位 |
| 计划执行次数 | `execution_count` | `Integer` | 否 | **护士站拆解可执行上限**（门诊/临嘱）；与 `quantity` **分离**；长嘱按频次执行时可为空 |
| 患者标识 | `patient_id` | `Long` | 是 | 患者主体 |
| 就诊标识 | `encounter_id` | `Long` | 是 | 就诊锚点 |
| 开立时间 | `req_opened_time` | `DateTime` | 否 | **首次建草稿/直建落库写一次**；签发、改草稿、签退**不覆盖**；住院可由改业务时间（§6.1.8b / **IF-IP-DOR-26**）覆盖；**不**因改时变更开立人；与药品/耗材同构 |
| 签发时间 | `req_authored_time` | `DateTime` | 否 | **仅签发**（及无草稿直建）写入；列名与药/耗材对齐（原 `authored_time` 已更名）；草稿暂存**不写**本列；**仅系统追溯**，列表/报表**默认不展示**；**禁止**经改业务时间接口修改 |
| 请求发起人 | `requester_id` | `Long` | 是 | 开立人 |
| 执行者类型编码 | `performer_type_code` | `String` | 否 | 执行者类型 |
| 执行人 | `performer_id` | `Long` | 否 | 执行人 |
| 校对人 | `performer_check_id` | `Long` | 否 | 校对人 |
| 校对时间 | `check_time` | `Date` | 否 | 校对时间 |
| 执行位置标识 | `location_id` | `Long` | 否 | 执行地点 |
| 执行科室标识 | `org_id` | `Long` | 否 | 执行科室 |
| 理由文本 | `reason_text` | `String` | 否 | 临床理由 |
| 合同标识 | `contract_id` | `Long` | 否 | 费用性质/合同 |
| 预计开始时间 | `occurrence_start_time` | `Date` | 否 | 计划起 |
| 预计结束时间 | `occurrence_end_time` | `Date` | 否 | 计划止（**停嘱不写本列**） |
| 停嘱时间 | `stop_time` | `DateTime` | 否 | 长期停嘱写入；取消停嘱清空；执行 Gate / 频次右界 / 滚费止算读本列；住院可由改业务时间覆盖；**不**因改时变更停嘱人；与药/耗材同构 |
| 停嘱人标识 | `stopper_id` | `Long` | 否 | **停嘱操作人**（医务人员主键）；停嘱写入；取消停嘱清空；改停嘱时间**不**改本列；与药品同构 |
| 停嘱校对人标识 | `stop_performer_check_id` | `Long` | 否 | **停嘱校对人**（护士 Practitioner）；停嘱校对写入；取消停嘱校对/取消停嘱/新停嘱清空；改停嘱时间**保留**；空=停嘱待校或未停嘱；与药品同构 |
| 停嘱校对时间 | `stop_check_time` | `DateTime` | 否 | 与 `stop_performer_check_id` 成对；改停嘱时间**不**改本列；与药品同构 |
| 历史请求关联标识 | `relevant_history_id` | `Long` | 否 | 历史关联 |
| 冲销关联标识 | `refund_service_id` | `Long` | 否 | 冲销链 |
| 扩展内容 | `content_json` | `String` | 否 | JSON 扩展 |
| 医保类别编码 | `yb_class_enum` | `String` | 否 | 医保类别 |
| 打印次数 | `print_count` | `Integer` | 否 | 打印统计 |
| 就诊诊断绑定有序 ids 串 | `encounter_diagnosis_ids` | `String` | 否 | 有序逗号分隔的 `adm_encounter_diagnosis.id`；首位为主诊断（与药品申请同源） |

| 治疗类型 | `therapy_enum` | `String` | 否 | 见 §4.5；与 **`DM-MED-003` §4.2** 共用 **`TherapyType`**（存 `getCode()`） |
| 频次编码 | `rate_code` | `String` | 否 | 频次 |
| 父请求标识 | `parent_id` | `Long` | 否 | 套餐子项父项 |
| 生成来源 | `generate_source_enum` | `String` | 否 | 见 §4.6（存枚举 code） |
| 备注 | `remark` | `String` | 否 | 备注 |
| 嘱托内容 | `entrust_content` | `String` | 否 | 嘱托 |

> 通用审计字段（`createBy/createTime/updateBy/updateTime/tenantId/isDeleted`）继承自 `HisBaseEntity`，不在聚合属性表重复列出。

### 3.2 服务申请明细（ServiceRequestDetail）

服务申请明细子实体，对应 `wkf_service_request_detail` 表。

| 属性 | 字段名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| 明细标识 | `id` | `Long` | 是 | 主键 |
| 服务申请标识 | `service_req_id` | `Long` | 是 | 外键，关联主档 |
| 明细目标表名 | `target_table` | `String` | 是 | 如 `dia_body_structure`、`adm_specimen_definition` |
| 明细目标主键 | `target_id` | `Long` | 是 | 目标表主键 |
| 备注 | `remark` | `String` | 否 | 明细备注 |

> **明细建模约定**：每行仅一个 `target_table + target_id` 引用；病理等需标本+部位时写两行。批量开立共用标本/部位时，应用层 fan-out 为每条 `service_req_id` 各写一行相同目标；同批申请单以头表 `prescription_no` 关联。

---

## 4. 值对象

### 4.1 请求状态（RequestStatus）

见 **§2.3 状态定义表**：落库字段 **`status_enum`** 为 **`String`**，取值 **`RequestStatus#getCode()`**（如 `"DRAFT"`、`"ACTIVE"`）。**不得**将表中 **`sortOrder`** 数字写入库。

### 4.2 请求意图（Intent）

> **`intent_enum`** 对应实体字段类型为 **`String`**，存意图枚举 **code**（与 §3.1 一致）；若后续补充独立意图枚举类，以 **`ServiceRequest`** 源码为准。

### 4.3 诊疗类别（ActivityDefCategory）

| 编码值 | 枚举常量 | 说明 |
| --- | --- | --- |
| 21 | `TREATMENT` | 治疗 |
| 22 | `PROOF` | 检验 |
| 23 | `TEST` | 检查 |
| 24 | `PROCEDURE` | 手术 |
| 25 | `ANESTHESIA` | 麻醉 |
| 26 | `NURSING` | 护理级别 |
| 27 | `DIET` | 膳食 |
| 28 | `METACHYSIS` | 输血 |
| 29 | `OXYGEN_THERAPY` | 输氧 |
| 32 | `TRANSFER` | 转科 |
| 33 | `POSTOPERATIVE` | 术后 |
| 34 | `DISCHARGE` | 出院 |
| 35 | `TRANSFER_HOSPITAL` | 转院 |
| 36 | `DEATH` | 死亡 |
| 37 | `POSTPARTUM` | 产后 |
| 38 | `CONDITION` | 病情 |
| 39 | `NURSING_ROUTINE` | 护理常规 |
| 40 | `POSITION` | 体位 |
| 41 | `COMPANION_CARE` | 陪护 |
| 42 | `ISOLATION_LEVEL` | 隔离等级 |
| 43 | `ORAL_EXAMINATION` | 口腔检查 |
| 44 | `ENDOSCOPY` | 内镜检查 |
| 45 | `ENDOSCOPIC_THERAPY` | 内镜治疗 |
| 46 | `BASIC_SERVICE` | 基础服务 |
| 47 | `DIAGNOSIS_FEE` | 诊断费 |
| 48 | `VACCINATION_HANDLING_FEE` | 预防接种手续费 |
| 49 | `ORAL_TREATMENT` | 口腔治疗 |
| 50 | `NURSING_PROJECT` | 护理项目 |
| 51 | `BED_SERVICE` | 床位服务 |
| 52 | `ENTRUST` | 嘱托（住院嘱托医嘱；目录固定名「嘱托」；正文 `entrust_content`；见 **AS-IP-DOR-003 · BR-IP-ORD-028**） |

> **存储口径**：对应主表字段 `category_enum`，使用**字符串编码存储（存枚举 code）**。

### 4.4 优先级（PriorityLevel）

| 编码值 | 枚举常量 | code | 说明 |
| --- | --- | --- | --- |
| 1 | `NOT_URGENT` | `NU` | 不紧急 |
| 2 | `ORDINARY` | `OR` | 普通 |
| 3 | `PRIORITY` | `PR` | 优先 |
| 4 | `EMERGENCY` | `EM` | 紧急 |
| 5 | `DANGER` | `DA` | 危 |

> **存储口径**：对应主表字段 `priority_enum`，使用**字符串编码存储（存枚举 code）**。

### 4.5 治疗类型（TherapyType）

与 **`DM-MED-003` §4.2**、药品/诊疗/耗材共用 **`com.zxplat.domain.share.enums.TherapyType`**。

| value（排序号） | 枚举常量 | code（落库） | 说明 |
| --- | --- | --- | --- |
| 1 | `LONG_TERM` | `LONG_TERM` | 长期 |
| 2 | `TEMPORARY` | `TEMPORARY` | 临时 |
| 3 | `DISCHARGE_MEDICATION` | `DISCHARGE_MEDICATION` | 出院带药（**药品路径**使用；服务申请侧通常不写此 code，见 **BR-IP-ORD-029**） |

> **存储口径**：`wkf_service_request.therapy_enum` 存 **`getCode()`** 字符串（`LONG_TERM` / `TEMPORARY` / **`DISCHARGE_MEDICATION`**）。

### 4.6 生成来源（GenerateSource）

与 **`com.zxplat.domain.share.enums.GenerateSource`** 及 **`DM-MED-003` §4.5** 共用同一套英文 **code**（跨药品/诊疗/耗材/收费明细）。

| 枚举常量 | code（落库） | 说明 |
| --- | --- | --- |
| `DOCTOR_PRESCRIPTION` | `doctor-prescription` | 医生开立 |
| `NURSE_PRICING` | `nurse-pricing` | 护士划价 |
| `REGISTRATION_REGISTER` | `registration` | 挂号登记 |
| `ORDER_EXECUTE` | `order-execute` | 医嘱执行 |
| `AUTO_ROLL_FEES` | `auto-roll-fees` | 自动滚费 |

> **存储口径**：对应主表字段 `generate_source_enum`，使用上表 **英文 code 字符串** 存储（禁止数字串作身份）。

---

## 5. 领域事件

### 5.1 事件定义

| 事件名称（中文） | 英文名称 | 触发时机 | 携带数据 |
| --- | --- | --- | --- |
| 草稿已创建 | `ServiceRequestDraftCreatedEvent` | 草稿创建后 | 服务申请标识、就诊标识、患者标识 |
| 草稿已修改 | `ServiceRequestDraftEditedEvent` | 草稿修改后 | 服务申请标识、变更字段 |
| 草稿已作废 | `ServiceRequestDraftRemovedEvent` | 草稿删除后 | 服务申请标识、就诊标识 |
| 已签发 | `ServiceRequestSignedEvent` | 签发后 | 服务申请标识、签发编码、类别、`status_enum`（**ACTIVE**） |
| 已签退 | `ServiceRequestWithdrawnEvent` | 签退后 | 服务申请标识、签发编码 |
| 已校对 | `ServiceRequestVerifiedEvent` | 病区护士校对（**`verifyServiceRequest`**）或门诊收费收口（**`completeServiceRequestAfterOutpatientCharge`**，见 **AS-OP-FIN-002** **BR-OPC-015**） | 服务申请标识、校对人、`status_enum`（save 后 **`RequestStatus#getCode()`**：护士路径 **`VERIFIED`**，门诊收费路径 **`COMPLETED`**） |
| 已退回 | `ServiceRequestRejectedToDraftEvent` | 退回草稿后 | 服务申请标识、`status_enum`（save 后 **`DRAFT`** code） |
| 已停止 | `ServiceRequestStoppedEvent` | 停止后 | 服务申请标识、停嘱时间、`statusEnum`（**STOPPED**） |
| 已取消停嘱 | `ServiceRequestStopCancelledEvent` | 取消停嘱后 | 服务申请标识、恢复后 `statusEnum`（**VERIFIED** 或 **ACTIVE**） |
| 已取消 | `ServiceRequestCancelledEvent` | 取消后 | 服务申请标识、取消原因 |
| 退诊疗负向单已生成 | `ServiceOrderRefundRequestedEvent` | 申请退诊疗负向单创建后 | 负向服务申请标识、原服务申请标识（`refund_service_id`） |
| 转科申请已创建 | `TransferDepartmentRequestCreatedEvent` | 转科申请创建后 | 服务申请标识、就诊标识 |
| 出院申请已创建 | `DischargeRequestCreatedEvent` | 出院申请创建后 | 服务申请标识、就诊标识 |
| 护理医嘱已维护 | `NursingOrderUpsertedEvent` | 护理医嘱维护后 | 服务申请标识、类别 |
| 执行医嘱已生成 | `ServiceRequestExecutedCreatedEvent` | 执行复制/绑定后 | 服务申请标识、来源关系 |
| 执行派生已逻辑删除 | `ServiceRequestExecutedRemovedEvent` | 取消执行绑定诊疗回退 | 服务申请标识、`basedOnTable`、`basedOnId` |
| 护士划价已生成 | `NursePricingServiceRequestCreatedEvent` | 护士划价生成后 | 服务申请标识、就诊/患者/活动/数量/单位、补费 `based_on_*` |
| 护士划价已撤销 | `NursePricingServiceRequestRemovedEvent` | 取消护士划价软删后 | 服务申请标识、就诊标识、补费 `based_on_*` |
| 住院滚费已生成 | `AutoRollServiceRequestCreatedEvent` | 滚费落账建单后（**`createFromAutoRoll`**） | 服务申请标识、就诊/患者/活动/数量/单位、**`categoryEnum`**、`statusEnum` |
| 挂号诊疗已生成 | `OutpatientRegistrationServiceRequestCreatedEvent` | 挂号登记生成后 | 服务申请标识、就诊标识 |

### 5.2 事件触发规则

| 当前状态 | 操作 | 目标状态 | 发布事件 |
| --- | --- | --- | --- |
| — | 开立草稿 | `DRAFT` | `ServiceRequestDraftCreatedEvent` |
| `DRAFT` | 修改草稿 | `DRAFT` | `ServiceRequestDraftEditedEvent` |
| `DRAFT` | 作废草稿 | 删除 | `ServiceRequestDraftRemovedEvent` |
| `DRAFT` | 签发 | `ACTIVE` | `ServiceRequestSignedEvent` |
| `ACTIVE` | 签退 | `DRAFT` | `ServiceRequestWithdrawnEvent` |
| `ACTIVE` | 病区护士校对 | `VERIFIED` | `ServiceRequestVerifiedEvent`（**`status_enum=VERIFIED`**） |
| `ACTIVE` | 门诊正式结算收口 | `COMPLETED` | `ServiceRequestVerifiedEvent`（**`status_enum=COMPLETED`**；核对人记为收费操作人） |
| `ACTIVE`/`VERIFIED`/`COMPLETED` | 退回 | `DRAFT` | `ServiceRequestRejectedToDraftEvent`（**`status_enum=DRAFT`**） |
| `ACTIVE`/`VERIFIED`/`COMPLETED` | 停止 | `STOPPED` | `ServiceRequestStoppedEvent` |
| `STOPPED`（仅长期） | 取消停嘱 | `VERIFIED`/`ACTIVE` | `ServiceRequestStopCancelledEvent` |
| `ACTIVE`/`VERIFIED`/`COMPLETED` | 取消 | `CANCELLED` | `ServiceRequestCancelledEvent` |
| — | 申请退诊疗（负向单） | `CANCELLED`（新单） | `ServiceOrderRefundRequestedEvent` |
| — | 开立转科 | `DRAFT` | `TransferDepartmentRequestCreatedEvent` |
| — | 开立出院 | `DRAFT` | `DischargeRequestCreatedEvent` |
| — | 维护护理医嘱 | `DRAFT`/`STOPPED` | `NursingOrderUpsertedEvent` |
| — | 执行复制/药品绑定 | `COMPLETED` | `ServiceRequestExecutedCreatedEvent` |
| — | 护士划价 | `VERIFIED` | `NursePricingServiceRequestCreatedEvent` |
| `VERIFIED` | 取消护士划价（**`removeFromNursePricing`**） | 逻辑删除 | `NursePricingServiceRequestRemovedEvent`（仅 `nurse-pricing`） |
| — | 住院滚费（**`createFromAutoRoll`**） | `VERIFIED` | `AutoRollServiceRequestCreatedEvent`（`auto-roll-fees`） |
| — | 挂号登记 | `ACTIVE` | `OutpatientRegistrationServiceRequestCreatedEvent` |

### 5.3 `NursePricingServiceRequestCreatedEvent` 载荷细表（对齐代码 · **B.14**）

> **aggregateId** = `serviceRequestId`（`DomainEvent` 基类）；**须**携带 save 后 **`statusEnum`**（`VERIFIED` code）；**不**携带 `category_enum` / `requester_id` / `authored_time`——`category_enum=21`、`performer_check_id`/`check_time` 等已在 **`createFromNursePricing`** 写表落库。列表读 **`ServiceRequestQueryService#listInpatientNurseStationPricedOrders`** 等直读 `wkf_service_request`（**非** OrderInfo RM）。

| 载荷字段 | 类型 | 说明 |
| --- | --- | --- |
| `aggregateId`（`serviceRequestId`） | `Long` | 新服务申请主键 |
| `encounterId` | `Long` | 就诊标识 |
| `patientId` | `Long` | 患者标识 |
| `activityId` | `Long` | 诊疗活动定义标识 |
| `quantity` | `BigDecimal` | 请求数量 |
| `unitCode` | `String` | 请求单位 **code** |
| `basedOnTable` | `String` | 补费父锚点表名；直接划价为 **null** |
| `basedOnId` | `Long` | 补费父锚点 ID；直接划价为 **null** |
| `statusEnum` | `String` | save 后 `RequestStatus#getCode()`（护士划价路径为 **`VERIFIED`**） |

**消费方**：护士划价行已在 **`createFromNursePricing`** 落库；`generate_source_enum` 写死 **`nurse-pricing`**（**非**事件载荷）。已划价/补费列表由 **`ServiceRequestQueryService`** + 应用 **`InpatientOrderListSkeleton`** 组装。

### 5.4 `NursePricingServiceRequestRemovedEvent` 载荷细表（取消护士划价 · **B.14**）

> 与 **`NursePricingServiceRequestCreatedEvent`** 对称；仅在 **`removeFromNursePricing`** 主档与明细 `saveAll` 软删后逐条发布。列表读侧直读 `wkf_service_request` 并按 `is_deleted` 过滤（**非** OrderInfo RM）。

| 载荷字段 | 类型 | 说明 |
| --- | --- | --- |
| `aggregateId`（`serviceRequestId`） | `Long` | 已软删服务申请主键 |
| `encounterId` | `Long` | 就诊标识 |
| `basedOnTable` | `String` | 补费父锚点表名；直接划价为 **null** |
| `basedOnId` | `Long` | 补费父锚点 ID；直接划价为 **null** |

**消费方**：已划价列表等读侧剔除投影；与 **GAP-IP-PRC-010** 同构——直读主表 + `is_deleted` 过滤，无独立 RM handler。

---

## 6. 领域服务

### 6.1 写侧领域服务（ServiceRequestDomainService）

#### 6.1.1 创建服务申请草稿（`createServiceRequestDraft`）

- **业务意义**：在就诊上下文中创建可编辑的非药品服务申请草稿。
- **边界说明**：仅收口本聚合与明细一致性；费用草稿、套餐子项展开、流程单、跨系统发送由应用编排。
- **入参**：就诊标识、患者标识、项目定义标识、发起人、数量、单位、类别、时长类型等。
- **出参**：服务申请标识。
- **前置条件**：最小开立要素校验通过；`quantity`/`unit_code` 须由开立载荷或目录主单位解析（**禁止**域层强制归 1）。
- **状态变化**：初始化为 `DRAFT`。
- **聚合内影响**：写入主表草稿态；按需初始化明细。数量口径：门诊按开立值；住院长期固定 `1`；住院临时按开立值。
- **事件输出**：`ServiceRequestDraftCreatedEvent`。
- **失败语义**：必填缺失、就诊状态不允许开立时拒绝。

#### 6.1.2 修改服务申请草稿（`modifyServiceRequestDraft`）

- **业务意义**：在签发前修订草稿内容。
- **边界说明**：仅更新 `DRAFT` 态可编辑字段；不直接改收费明细与执行记录；联动费用草稿刷新由用例编排。
- **入参**：服务申请标识、变更字段集合。
- **出参**：—。
- **前置条件**：当前须为 `DRAFT`；已进入计费不可改口径的字段须由用例先拦截；`quantity`/`unit_code` 变更须与 **BR-SRQ-007** 一致由载荷或目录解析。
- **状态变化**：`N/A`（仍为 `DRAFT`）。
- **聚合内影响**：主表与明细增量更新。
- **事件输出**：`ServiceRequestDraftEditedEvent`。
- **失败语义**：非草稿态、并发冲突时拒绝。

#### 6.1.3 删除服务申请草稿（`removeServiceRequestDraft`）

- **业务意义**：作废未签发草稿并释放引用。
- **边界说明**：主档与明细由本聚合同事务清理；**费用草稿、同组套餐子项**等协作对象撤销或删除由**应用用例**编排收口（见 **BR-SRQ-012**）；已收费不可删由读侧门控与用例叠加校验。
- **入参**：服务申请标识。
- **出参**：—。
- **前置条件**：当前须为 `DRAFT`；满足可删费用口径。
- **状态变化**：删除或逻辑删除（以落库与交互契约为准）。
- **聚合内影响**：主档与明细同事务清理。
- **事件输出**：`ServiceRequestDraftRemovedEvent`。
- **失败语义**：非草稿态、已收费时拒绝。

#### 6.1.4 签发服务申请（`signServiceRequest`）

- **业务意义**：将草稿签发为可执行/可校对状态。
- **边界说明**：费用状态推进、执行待办生成由订阅方/用例编排；**检验/检查类**对外跨系统申请发送**仅**在签发达到可发送状态之后触发，草稿保存不得触发（见 **BR-SRQ-013**）。
- **入参**：服务申请标识、签发编码。
- **出参**：—。
- **前置条件**：当前为 `DRAFT`；医嘱关键字段完整且通过规则校验。
- **状态变化**：`DRAFT -> ACTIVE`。
- **聚合内影响**：写入签发时间、签发编码及签发侧执行锚点字段（如执行人、地点、计划起等，以落库与交互契约为准）。
- **事件输出**：`ServiceRequestSignedEvent`。
- **失败语义**：状态非法、签发要素缺失时拒绝。

#### 6.1.5 签退服务申请（`withdrawSignedServiceRequest`）

- **业务意义**：将已签发申请退回草稿以纠错重开。
- **边界说明**：已收费或已校对不可签退；费用回退、任务撤销由用例编排；**同一批次签退**场景下若存在已校对记录须拒绝或按产品策略跳过（与 **BR-SRQ-003** 一致）。
- **入参**：服务申请标识。
- **出参**：—。
- **前置条件**：当前须为 `ACTIVE`；**已是 `DRAFT` 时幂等返回**（不抛错、不重复发事件）——覆盖皮试联动签退后同批再显式勾选 companion SR、以及二次签退；读侧 `validateServiceRequestWithdrawable` 通过（仅非草稿路径）。
- **状态变化**：`ACTIVE -> DRAFT`。
- **聚合内影响**：状态与签发相关字段回退（以落库与交互契约为准）。
- **事件输出**：`ServiceRequestWithdrawnEvent`。
- **失败语义**：已收费、已校对或状态不允许时拒绝。

#### 6.1.6 校对服务申请（`verifyServiceRequest`）

- **业务意义**：病区护士校对通过，将已发送申请推进为**已校对**态（**`VERIFIED`**，与门诊收费 **`COMPLETED`** 分离）。
- **边界说明**：执行落地、统计累计由协作方处理；**转科**（`category_enum=TRANSFER`）与**出院**（`category_enum=DISCHARGE`）大类校对完成后，就诊进入「待转科」「待出院」等状态由**编排订阅**写 `Encounter`，本方法不直接改就诊聚合。
- **入参**：服务申请标识、校对人标识、校对时间（可空则服务端当前时刻）。
- **出参**：—。
- **前置条件**：当前须为 `ACTIVE`；校对人不可空。
- **状态变化**：`ACTIVE -> VERIFIED`（实体方法 **`activeToVerified`**）。
- **聚合内影响**：写入 `performer_check_id`、`check_time`（**BR-SRQ-022**）。
- **事件输出**：`ServiceRequestVerifiedEvent`（**`status_enum`** 须为 save 后 **`VERIFIED`** code，及 **`performerCheckId`**）。
- **失败语义**：非 `ACTIVE` 时拒绝。

#### 6.1.6a 门诊收费收口完成（`completeServiceRequestAfterOutpatientCharge`）

- **业务意义**：门诊正式结算后，将仍为 **ACTIVE** 的诊疗服务申请**幂等**收口为 **COMPLETED**；**不进**住院校对 **VERIFIED** 语义。
- **边界说明**：由 **`OutpatientChargeAdviceStateSyncService`**（对齐 **AS-OP-FIN-002** IF-OPC-07 / **BR-OPC-015**）在收费项已 **SETTLED** 之后**同步**编排调用；与 **`verifyServiceRequest`** 共用 **`ServiceRequestVerifiedEvent`** 类型，区别在目标态（**`COMPLETED`**）与操作者语义（收银 vs 护士）。
- **入参**：服务申请标识、收费操作人标识（写入 `performerCheckId`）。
- **出参**：—。
- **前置条件**：当前须为 **ACTIVE**（**`ensureCompletableAfterOutpatientCharge`**）；已为 **COMPLETED** 时幂等返回。
- **状态变化**：`ACTIVE -> COMPLETED`（实体方法 **`activeToCompleted`**）。
- **聚合内影响**：写入校对时间与核对人等追溯字段（与 **BR-SRQ-022** 一致）。
- **事件输出**：`ServiceRequestVerifiedEvent`（**`status_enum`** 须为 save 后 **`COMPLETED`** code）。
- **失败语义**：状态非法时拒绝。

#### 6.1.7 退回至草稿（`rejectServiceRequestToDraft`）

- **业务意义**：将已发送/已校对/已完成申请退回草稿以修订（校对不通过返回医生修改）。
- **边界说明**：须评估费用与下游依赖，由用例编排协同回退；住院护士站 **IF-IP-AUD-03** 对 **ACTIVE/VERIFIED** 发起退回（**VERIFIED** 须 **BR-IP-AUD-013** 门控，与医生站签退同口径）。
- **入参**：服务申请标识、校对人标识、校对/退回时间、退回原因。
- **出参**：—。
- **前置条件**：当前为 `ACTIVE`、`VERIFIED` 或 `COMPLETED`（**`assertRejectable`**）。
- **状态变化**：`ACTIVE`/`VERIFIED`/`COMPLETED` -> `DRAFT`。
- **聚合内影响**：状态回退；**须**写入 `performer_check_id`、`check_time`（**BR-SRQ-022**）。
- **事件输出**：`ServiceRequestRejectedToDraftEvent`（**`status_enum=DRAFT`**）。
- **失败语义**：存在不可退回约束时拒绝。

#### 6.1.8 停止服务申请（`stopServiceRequest`）

- **业务意义**：终止仍在执行链路上的申请（停嘱）；住院长期停嘱收口时间轴右端点与停嘱人，并进入护士停嘱待校。
- **边界说明**：住院 **IF-IP-DOR-20** 仅更新选中行；**不**级联子嘱。
- **入参**：`StopServiceRequestCommand`：`id`、`stopTime`、`stopperId`/`operatorId`。
- **出参**：—。
- **前置条件**：住院路径须 **`VERIFIED`**（**禁止 ACTIVE**）；诊侧门诊路径仍允许 **`COMPLETED`**（**BR-SRQ-016** / **`assertStoppable`**）。
- **状态变化**：迁移至 `STOPPED`。
- **聚合内影响**：写 `stop_time`、`stopper_id`；清空停嘱校对两字段；**不**占用 `occurrence_end_time`。
- **事件输出**：`ServiceRequestStoppedEvent`。
- **失败语义**：状态不允许停嘱时拒绝。

#### 6.1.8a 取消服务申请停嘱（`cancelStopServiceRequest`）

- **业务意义**：撤销住院长期诊疗停嘱，恢复继续执行。
- **边界说明**：已停嘱校对须护士先取消停嘱校对；与 Procedure 解耦。
- **入参**：`CancelStopServiceRequestCommand`：`id`、`operatorId`。
- **出参**：—。
- **前置条件**：长期 + `STOPPED` + **`stop_performer_check_id` 为空**（**BR-SRQ-016a** / **`assertCancelStopAllowed`**）。
- **状态变化**：`fromStopped`：`performer_check_id` 非空 → **`VERIFIED`**，否则 → **`ACTIVE`**。
- **聚合内影响**：清空 `stop_time`、`stopper_id`、停嘱校对两字段。
- **事件输出**：`ServiceRequestStopCancelledEvent`。
- **失败语义**：非长期、非已停止、已停嘱校对 → 拒绝。
- **实现状态**：**已落地**。

#### 6.1.8b 改服务申请业务时间（`changeServiceRequestBusinessTime`）

- **业务意义**：调整开立或停嘱时间；**不**改签发时间与开立人/停嘱人/停嘱校对痕迹。
- **边界说明**：**IF-IP-DOR-26**；改停嘱**保留**停嘱校对两字段（**BR-SRQ-016b**）。
- **入参**：`ChangeServiceRequestBusinessTimeCommand`：`id`、`timeKind`、`newTime`、`operatorId`。
- **出参**：—。
- **前置条件**：`STOP` 须长期且当前 `STOPPED`。
- **状态变化**：无。
- **聚合内影响**：`OPENED`/`STOP` 分别写对应时间列。
- **失败语义**：种类非法、非已停嘱改停嘱、试图改签发 → 拒绝。
- **实现状态**：**已落地**。

#### 6.1.8c 停嘱校对（`verifyStopServiceRequest`）

- **业务意义**：护士确认长期诊疗停嘱；只记停嘱校对痕迹，**不**改 `STOPPED`。
- **边界说明**：**AS-IP-NUR-003 IF-IP-AUD-02**；临时无此命令。
- **入参**：`VerifyStopServiceRequestCommand`：`id`、`stopPerformerCheckId`、`stopCheckTime`。
- **出参**：—。
- **前置条件**：长期 + `STOPPED` + 未停嘱校对（**`assertStopVerifiable`** / **BR-SRQ-016c**）。
- **状态变化**：无（仍 `STOPPED`）。
- **聚合内影响**：写停嘱校对两字段（`markStopChecked`）。
- **失败语义**：分支非法或重复校对 → 拒绝。
- **实现状态**：**已落地**。

#### 6.1.8d 取消停嘱校对（`cancelVerifyStopServiceRequest`）

- **业务意义**：清空停嘱校对痕迹，解锁医生取消停嘱；状态仍 `STOPPED`。
- **边界说明**：**IF-IP-AUD-03**；**本期暂不校验操作人**。
- **入参**：`CancelVerifyStopServiceRequestCommand`：`id`。
- **出参**：—。
- **前置条件**：`STOPPED` 且已停嘱校对（**`assertStopVerifyCancellable`** / **BR-SRQ-016d**）。
- **状态变化**：无。
- **聚合内影响**：清停嘱校对两字段（`clearStopChecked`）。
- **失败语义**：尚未停嘱校对 → 拒绝。
- **实现状态**：**已落地**。

#### 6.1.9 取消服务申请（`cancelServiceRequest`）

- **业务意义**：取消申请并进入取消/待退终态；保留记录可追溯，不等同物理删除。
- **边界说明**：撤销链路与退费由事件与应用编排完成。**住院反结撤出院嘱不走本方法**（见 §6.1.9b 软删）。
- **入参**：服务申请标识、取消原因。
- **出参**：—。
- **前置条件**：当前为 `ACTIVE`、`VERIFIED` 或 `COMPLETED`。
- **状态变化**：迁移至 `CANCELLED`。
- **聚合内影响**：写入取消原因；取消操作人、科室、时间等追溯字段按产品线映射（见 **BR-SRQ-017**）。
- **事件输出**：`ServiceRequestCancelledEvent`。
- **失败语义**：状态不允许取消时拒绝。

#### 6.1.9b 住院反结软删出院医嘱（`softDeleteDischargeServiceRequestForReverseSettle`）

- **业务意义**：住院反结后逻辑删除医生开立出院嘱，医生站列表不再展示；医生须重新开立出院医嘱。
- **边界说明**：**禁止**迁 `CANCELLED`（避免「已取消」留痕）；仅 **AS-IP-FIN-004** 反结编排调用；与 `cancelServiceRequest` 正交。
- **入参**：`SoftDeleteDischargeServiceRequestForReverseSettleCommand`（`serviceRequestId`、`operatorId`、可选 `auditReason`）。
- **出参**：—。
- **前置条件**：未软删；`category_enum=34`；来源 `doctor-prescription`；态 ∈ `DRAFT`/`ACTIVE`/`VERIFIED`/`COMPLETED`（`assertReverseSettleDischargeSoftDeletable`）。
- **状态变化**：主档与明细 **逻辑删除**（`is_deleted`）；状态码不迁移。
- **聚合内影响**：可选写入 `reason_text`（审计）；`markDeleted` + 明细软删。
- **事件输出**：无（读侧按软删过滤）。
- **失败语义**：非出院类/非医生开立/状态不允许时拒绝。
- **实现状态**：**已落地**。

#### 6.1.9a 申请退诊疗（负向单）（`createServiceOrderRefund`）

- **业务意义**：基于已完成原单生成退诊疗/退费用的负向服务申请行，并建立 `refund_service_id` 关联（对齐 openhis 与药/耗负向单口径）。
- **边界说明**：门诊医生站申请退费（**AS-OP-DOR-004** **BR-DRA-007**）在源单 `COMPLETED` 时调用；未完成源单走 `stopServiceRequest`，不调用本方法。
- **入参**：`CreateServiceOrderRefundCommand`（`sourceRequestId`、`refundQuantity` 正数、`reason`）。
- **出参**：新负向服务申请标识，或已存在的未完结负向单标识（幂等）。
- **前置条件**：源单 `COMPLETED` 且未软删；原单为正向单（`refund_service_id` 为空）；已有未完结负向行时幂等返回。
- **状态变化**：新单 `initAsRefundNegativeOrder()` → **`CANCELLED`**（待退）；`quantity` 取反；`prescription_no`=`T`+原号（`markAsRefundFromPrescription`）；幂等命中时不改已有行。
- **聚合内影响**：复制头表可对齐字段及未软删明细子行；原正向单保持 `COMPLETED` 不动；**已有未完结负向单时返回已有主键**；定位仅本原单。
- **事件输出**：`ServiceOrderRefundRequestedEvent`（幂等命中已有行时不重复发布）。
- **失败语义**：原单已是负向单、源单非已完成、参数非法时拒绝。

#### 6.1.10 开立转科申请（`createTransferDepartmentRequest`）

- **业务意义**：为就诊创建转科类服务申请草稿。
- **边界说明**：与出院申请互斥规则由读侧校验门控；`EncounterHistory` 流程单创建由编排层处理。
- **入参**：就诊标识、目标科室、转科原因等。
- **出参**：服务申请标识。
- **前置条件**：就诊为在院等允许开立状态；同就诊不存在 `DRAFT`/`ACTIVE` 的转科或出院申请（与 **BR-SRQ-010** 一致）。
- **状态变化**：初始化 `DRAFT`。
- **聚合内影响**：插入主表与必要明细；`category_enum` 为转科；`therapy_enum` 为临时；`reason_text` 承载转科原因。
- **事件输出**：`TransferDepartmentRequestCreatedEvent`。
- **失败语义**：互斥或参数非法时拒绝。

#### 6.1.11 开立出院申请（`createDischargeRequest`）

- **业务意义**：为就诊创建出院类服务申请草稿。
- **边界说明**：吸氧/心电监测等**时长余量**相对在院时长须在开立前通过校验（**BR-SRQ-018**）；`EncounterHistory` 等由编排层处理。
- **入参**：就诊标识、出院时间、出院描述等。
- **出参**：服务申请标识。
- **前置条件**：就诊为在院等允许开立状态；同就诊出院/转科互斥校验通过；余量类规则满足。
- **状态变化**：初始化 `DRAFT`。
- **聚合内影响**：插入主表与必要明细；`category_enum` 为出院；`therapy_enum` 为临时；`occurrence_start_time` 承载计划出院时间；`reason_text` 承载出院描述。
- **事件输出**：`DischargeRequestCreatedEvent`。
- **失败语义**：互斥或约束不满足时拒绝。

#### 6.1.12 维护护理医嘱（`upsertNursingOrder`）

- **业务意义**：按就诊与类别幂等维护护理类服务申请（护理级别、病情、膳食、体位、陪护、隔离等类别）。
- **边界说明**：护理执行记录生成由护理域协作；本动作只收口本聚合请求事实。
- **入参**：就诊标识、类别、内容等。
- **出参**：服务申请标识。
- **前置条件**：就诊为在院等允许开立状态；按 `encounter_id + category_enum` 定位已有记录（**BR-SRQ-020**）。
- **状态变化**：无同类记录则新建 `DRAFT`；同类且内容一致则幂等跳过；同类且内容不同则先将旧记录置 `STOPPED` 再新建 `DRAFT`（以实现状态码为准）。
- **聚合内影响**：更新或替换护理申请记录。
- **事件输出**：`NursingOrderUpsertedEvent`。
- **失败语义**：参数非法或并发冲突时拒绝。

#### 6.1.13 创建执行完成申请（`createExecutedServiceRequest`）

- **业务意义**：由执行复制或**药品执行绑定**路径直接生成完成态申请，沉淀「本次执行已发生」事实。
- **边界说明**：`Procedure`/`ChargeItem` 写入由用例编排；药品绑定派生时 `based_on_*` 与 `generate_source_enum` 须满足 **BR-SRQ-021**。
- **入参**：来源表名、来源标识、执行上下文等。
- **出参**：服务申请标识。
- **前置条件**：来源医嘱可定位且允许派生；分步执行场景下 `bus_no`（或产品线约定字段）须可按步次区分（**BR-SRQ-015**）。
- **状态变化**：创建即 `COMPLETED`。
- **聚合内影响**：插入完成态主表与明细；写入 `based_on_table`/`based_on_id`、执行人科室与时间等执行上下文字段（以落库与交互契约为准）。
- **事件输出**：`ServiceRequestExecutedCreatedEvent`。
- **失败语义**：来源冲突或重复派生时拒绝。

#### 6.1.14 护士划价建单（`createFromNursePricing`）

- **业务意义**：住院护士站**直接划价**或**医嘱补费**时，批量生成**已校对（`VERIFIED`）**的诊疗服务申请；不经医生草稿/签发/校对 Tab 流程。
- **边界说明**：对齐 **AS-IP-NUR-005** IF-IP-PRC-02/04；**Procedure** 由应用层 **`NurseStationInpatientBillingProcedureOrchestrator`** 在诊疗划价确认时 **create→start→挂主执行人→complete** 并挂接 Charge（**BR-IP-PRC-007** / **BR-RPT-NWL-020**；主执行人须在 COMPLETED 前挂入）；**不写入** `sign_code`（本期划价不做批次 signCode）；带子项诊疗写路径落**一条父 SR** + **父 Charge + 子项账单**（`parent_id`、落账时 SALE 冻价，**BR-IP-PRC-011**）；子项账单由 **`ActivityDefinitionPackageChargePersistSupport`** 在父 Charge 落库后补写；结算/医保展开**只认**账单子行（无子项账单则拒绝，**禁止** SALE 回落）；`ChargeItem`、耗材 `DeviceRequest`/`DeviceDispense` 由应用层同事务编排；**禁止**与直接划价/补费混批的校验在应用层（**BR-IP-PRC-004/005**）。与医嘱组套（OrderTemplate）无关。
- **入参**：`CreateFromNursePricingCommand`（含 `operatorId` + `items[]`）。每行 `CreateFromNursePricingItemCommand` 至少含：`encounterId`、`patientId`、`activityId`、`quantity`、`unitCode`、`requesterId`；**直接划价**时 `basedOnTable`/`basedOnId` **均为空**；**补费**时须成对写入父锚点（药嘱父行 → `based_on_table=med_medication_request`，诊嘱父行 → `based_on_table=wkf_service_request`，`based_on_id=父 requestId`）。
- **出参**：`List<Long>`——新服务申请主键列表，**顺序与 `items` 一致**。
- **前置条件**：`items` 非空（建议单批 ≤50 行，见 AS §7）；每行就诊/患者/活动/数量/单位完整；`basedOnTable` 与 `basedOnId` **须同时为空或同时有值**（域内成对校验）；吸氧/心电等时长 Gate 由应用层在调用前断言（**BR-IP-PRC-010**，复用读侧累计 + 本次 quantity）。
- **状态变化**：每行创建即 **`VERIFIED`**（`initVerified`）；写入 `performer_check_id`（`operatorId`）、`check_time`；`therapy_enum` 固定 **临时**（`TherapyType.TEMPORARY` / `TEMPORARY` code）。
- **聚合内影响**：`saveAll` 批量插入主表；`generate_source_enum = nurse-pricing`；每行独立 `bus_no`（按日序号）；补费行写入 `based_on_table`/`based_on_id`；**不**写 `sign_code`。
- **事件输出**：每行 **先** `saveAll` **后**逐条发布 `NursePricingServiceRequestCreatedEvent`（载荷：`serviceRequestId`、`encounterId`、`patientId`、`activityId`、`quantity`、`unitCode`、`basedOnTable`、`basedOnId`、**`statusEnum`**；**B.14**）。
- **失败语义**：`items` 为空、`based_on_*` 半填、不变式 `assertDraftCreatable` 失败等 → `SERVICE_REQUEST_INVALID_ARGUMENT`；由应用层整批事务回滚（**BR-IP-PRC-013**）。

#### 6.1.14a 医技划价建单（`createFromMedicalTechPricing`）

- **业务意义**：住院医技工作站**直接划价**诊疗时，批量生成**已校对（`VERIFIED`）**的诊疗服务申请；承接护士站对 TECH 执行科诊疗的禁划（**AS-IP-NUR-005 BR-IP-PRC-034** / **AS-MT-CHG-001** IF-MT-CHG-07）。
- **边界说明**：与 **6.1.14** 同构；**Procedure** 由应用层 **`MedicalTechChargingProcedureOrchestrator`** 在确认划价时 **create→start→挂主执行人→complete**（`Procedure.org_id`=登录 TECH）并挂接 Charge；带子项诊疗同 **BR-IP-PRC-011** 冻价子账单路径；**不**写补费 `based_on_*`（本期仅直接划价）。
- **入参**：`CreateFromMedicalTechPricingCommand`（含 `operatorId` + `items[]`）。每行至少含：`encounterId`、`patientId`、`activityId`、`quantity`、`unitCode`、`requesterId`。
- **出参**：`List<Long>`——新服务申请主键列表，**顺序与 `items` 一致**。
- **前置条件**：`items` 非空；活动定义执行科=登录 TECH 由应用层断言（**BR-MT-CHG-008**）。
- **状态变化**：每行创建即 **`VERIFIED`**；`therapy_enum` 固定临时。
- **聚合内影响**：`saveAll`；`generate_source_enum = medical-tech-pricing`；每行独立 `bus_no`；**不**写 `sign_code`。
- **事件输出**：每行 **先** `saveAll` **后**逐条发布 `MedicalTechPricingServiceRequestCreatedEvent`（载荷：`serviceRequestId`、`encounterId`、`patientId`、`activityId`、`quantity`、`unitCode`、**`statusEnum`**；**B.14**）。
- **失败语义**：`items` 为空、不变式失败 → `SERVICE_REQUEST_INVALID_ARGUMENT`；整批事务回滚。

#### 6.1.15 挂号登记建单（`createFromOutpatientRegistration`）

- **业务意义**：挂号登记场景生成已发送申请，作为号源绑定诊疗的锚点。
- **边界说明**：**不替代**挂号收费项创建（如挂号费 `ChargeItem` 等）——附加费用与执行队列由**挂号结算/登记用例**继续编排；本动作仅生成 `ServiceRequest` 事实。
- **入参**：就诊标识、项目定义、挂号上下文等。
- **出参**：服务申请标识。
- **前置条件**：门诊就诊已建立；患者、科室、诊疗项目、号源上下文完整；收费/医嘱类前置校验通过（创建即 **ACTIVE** 生效，不经草稿/签发）。
- **状态变化**：创建即 `ACTIVE`。
- **聚合内影响**：插入主表；`generate_source_enum` 为挂号登记。
- **事件输出**：`OutpatientRegistrationServiceRequestCreatedEvent`。
- **失败语义**：参数非法或重复登记策略命中时拒绝。

#### 6.1.16 增加打印次数（`increasePrintCount`）

- **业务意义**：累加打印次数用于统计与控打。
- **边界说明**：仅增量更新计数字段。
- **入参**：服务申请标识、增量值。
- **出参**：—。
- **前置条件**：记录存在且允许打印统计累加。
- **状态变化**：`N/A`。
- **聚合内影响**：`print_count` 仅允许增量累加，禁止覆盖写入。
- **事件输出**：无（以落库与交互契约为准）。
- **失败语义**：非法增量或记录不存在时拒绝。

#### 6.1.17 半退保留链正向克隆（`cloneServiceRequestForPreservedChargeRegeneration`）

- **业务意义**：门诊半退「保留行」账单重生时，按源服务申请事实在本聚合内插入**新主键**申请，供后续执行/计费锚定；与 **DM-ADM-011 §6.1.9a**、**AS-OP-DOR-004** IF-DRA-03 编排对齐。
- **边界说明**：**正向克隆**；**禁止**将本方法语义与 `cancelServiceRequest` / `stopServiceRequest` 等拟退路径混用为「再生」。
- **入参**：`CloneServiceRequestForPreservedChargeRegenerationCommand`（至少含 `sourceServiceRequestId`、`operatorId`）。
- **出参**：新服务申请主键。
- **前置条件**：源申请可加载且未逻辑删除；`status_enum` **不得**为 `CANCELLED` / `HOLD`；`CloneServiceRequestForPreservedChargeRegenerationCommand` 之 `sourceServiceRequestId` 与 `operatorId` 均非空。
- **状态变化**：新头行 `status_enum = DRAFT`（`initDraft`）。
- **聚合内影响**：头表按源复制可对齐字段；`prescription_no`、`refund_service_id`、`sign_code`、`performer_check_id`、`check_time`、`requisition_id`、`parent_id`、`print_count` 等再结算入口字段清空或复位；`based_on_table`=`wkf_service_request`、`based_on_id`=源主键；新 `bus_no`；`requester_id`=`operatorId`；`req_opened_time`=当前时刻、`req_authored_time`=空；`quantity` 按源 `therapy_enum` 走疗法规格化；**未软删**之 `wkf_service_request_detail`（`findByServiceReqId`）逐条复制为新 `service_req_id` 子行（新主键）；`is_deleted=false`；头须通过 `assertDraftCreatable`。
- **事件输出**：头与明细均持久化**之后**发布 **`ServiceRequestDraftCreatedEvent`**（**B.14**）。
- **失败语义**：`SERVICE_REQUEST_INVALID_ARGUMENT`（入参或 `operatorId` 为空、源已删、源状态为取消/暂挂、或 `assertDraftCreatable` 失败等）；`SERVICE_REQUEST_NOT_FOUND`（源不存在）。

#### 6.1.18 逻辑删除执行派生服务申请（`removeExecutedServiceRequest`）

- **业务意义**：取消执行时回退绑定诊疗派生行（**AS-IP-NUR-012 BR-IP-CXL-013**）；与 **6.1.13** `createExecutedServiceRequest` 对称。
- **边界说明**：仅 **`COMPLETED`** 态执行派生；**非** withdraw 到草稿；`Procedure`/`ChargeItem` 回退由应用编排。应用层取消编排须先按 **`generate_source_enum=order-execute`** 过滤 based_on 反查结果，**禁止**将皮试检查 companion（`doctor-prescription`）等开嘱联动行传入本命令。
- **入参**：`RemoveExecutedServiceRequestCommand`（批量 `Item`：`id`）。
- **出参**：—。
- **前置条件**：目标须 **`COMPLETED`** 且未逻辑删除；`assertExecutionDerivedRemovable`。
- **状态变化**：主档与明细逻辑删除（软删）。
- **聚合内影响**：主表 + `wkf_service_request_detail` 同事务软删。
- **事件输出**：`ServiceRequestExecutedRemovedEvent`（逐条，含 `basedOnTable`/`basedOnId`）。
- **失败语义**：非完成态、不存在或入参为空时拒绝。

#### 6.1.19 逻辑删除护士划价诊疗申请（`removeFromNursePricing`）

- **业务意义**：住院护士站**取消划价**时，整行撤销护士划价产生的诊疗服务申请（**AS-IP-NUR-005** IF-IP-PRC-06）；与 **6.1.14** `createFromNursePricing` 对称。
- **边界说明**：仅 **`VERIFIED` + `generate_source_enum=nurse-pricing`**；**非**执行派生 **`removeExecutedServiceRequest`** 路径；`ChargeItem`/`Procedure` 回退由应用编排。
- **入参**：`RemoveFromNursePricingCommand`（批量 `Item`：`id`；含 `operatorId`）。
- **出参**：—。
- **前置条件**：`items` 非空；每行须 **`VERIFIED`**、来源 **`nurse-pricing`**、未逻辑删除；`assertNursePricingRemovable`。
- **状态变化**：主档与明细逻辑删除（软删）；**不**迁移至 `CANCELLED`/`DRAFT`。
- **聚合内影响**：`saveAll` 软删主表 + `wkf_service_request_detail` 子行。
- **事件输出**：`NursePricingServiceRequestRemovedEvent`（逐条，含 `encounterId`、`basedOnTable`/`basedOnId`；**§5.4**）。
- **失败语义**：非护士划价来源、非 `VERIFIED`、不存在、已删或入参为空 → `SERVICE_REQUEST_INVALID_ARGUMENT` / `SERVICE_REQUEST_NOT_FOUND`；由应用层整批事务回滚（**BR-IP-PRC-027**）。

#### 6.1.20 住院滚费建单（`createFromAutoRoll`）

- **业务意义**：**AS-IP-NUR-013** IF-IP-ARG-08 滚费落账时创建诊疗服务申请；与 **6.1.14** 同构但 `generate_source_enum=auto-roll-fees`。
- **边界说明**：创建即 **`VERIFIED`**；须携带 `categoryEnum`（床位 **51** / 护理 **26**）与 `occurrenceTime`（billDate 口径）；**不**经护士校对 Tab；**不**写 `based_on_*`。
- **入参**：`CreateFromAutoRollCommand`（批量 `CreateFromAutoRollItemCommand` + `operatorId`）。
- **出参**：新申请主键列表（顺序与入参一致）。
- **前置条件**：`encounterId`/`patientId`/`activityId`/`categoryEnum`/`occurrenceTime` 非空；批次至少一行。
- **状态变化**：新建 **`VERIFIED`** 行；`therapy_enum=TEMPORARY`；`check_time=occurrenceTime`。
- **聚合内影响**：`saveAll` 后逐条发布事件（**B.14**）。
- **事件输出**：`AutoRollServiceRequestCreatedEvent`（逐条，含 `categoryEnum`、`statusEnum`）。
- **失败语义**：必填项缺失 → `SERVICE_REQUEST_INVALID_ARGUMENT`。

#### 6.1.21 代煎费退款后软删停嘱 companion（`softDeleteStoppedServiceRequestAfterDecoctionRefunded`）

- **业务意义**：门诊代煎费收银退款终态后，收口因申请退费而停嘱的代煎 companion，避免医生站仍认定「有效代煎」挡住再补充（**AS-OP-FIN-003 · BR-REF-025** / **AS-OP-DOR-010 · BR-TCM-RX-009**）。
- **边界说明**：仅软删 **`STOPPED`** 主档；**不**处理 `COMPLETED` 历史完成态；与 `removeServiceRequestDraft`（仅草稿）分工。
- **入参**：`SoftDeleteStoppedServiceRequestAfterDecoctionRefundCommand`（`serviceRequestIds` + `operatorId`）。
- **出参**：—。
- **前置条件**：每行须 **`STOPPED`**；`operatorId` 非空；已软删跳过。
- **状态变化**：主档与明细逻辑删除（软删）；状态码仍为 `STOPPED`。
- **聚合内影响**：`markDeleted` + 明细软删；无独立领域事件（读侧依赖软删过滤）。
- **失败语义**：非停嘱 → `SERVICE_REQUEST_INVALID_ARGUMENT`；不存在 → `SERVICE_REQUEST_NOT_FOUND`。

#### 6.1.22 撤销未完结负向退诊疗单（`voidOpenRefundServiceOrder`）

- **业务意义**：门诊退费申请撤销时，软删尚未完结的负向退诊疗服务申请（须含 `refund_service_id`）。
- **边界说明**：主档与明细同事务软删；不替代 `createServiceOrderRefund` 建单路径。
- **入参**：`VoidOpenRefundServiceOrderCommand`。
- **出参**：—。
- **前置条件**：目标为未完结负向单；操作者合法。
- **状态变化**：逻辑删除。
- **失败语义**：状态不允许或非负向单则拒绝。

#### 6.1.23 门诊退费撤销后恢复停嘱诊疗单（`resumeStoppedServiceRequestForOutpatientRefundRevoke`）

- **业务意义**：门诊退费申请撤销时，恢复因该申请而停嘱的诊疗服务申请（门诊临时单路径，不走长期取消停嘱门禁）。
- **边界说明**：与 `cancelStopServiceRequest`（长期取消停嘱）分工。
- **入参**：相关 Command（服务申请标识 + 操作者）。
- **出参**：—。
- **前置条件**：当前为因退费申请停嘱的可恢复行。
- **状态变化**：自 `STOPPED` 恢复为可继续执行的业务态（以实现为准）。
- **失败语义**：状态不允许则拒绝。

#### 6.1.24 确保自动滚床长期服务申请（`ensureAutoRollBedLongTermServiceRequest`）

- **业务意义**：住院自动滚床场景下确保存在有效长期服务申请；`category_enum` 取自命令（选品 ActivityDefinition 类目），**不**写死床位类目。
- **边界说明**：与 `createFromAutoRoll` 配合；幂等确保而非盲目新建。
- **入参**：`EnsureAutoRollBedLongTermServiceRequestCommand`。
- **出参**：服务申请标识。
- **前置条件**：就诊与类目合法。
- **失败语义**：参数非法 → 业务异常。

> **变更（2026-09-23 · 模式 B）**：按代码回写补齐 `voidOpenRefundServiceOrder` / `resumeStoppedServiceRequestForOutpatientRefundRevoke` / `ensureAutoRollBedLongTermServiceRequest`。

### 6.2 读侧查询服务（ServiceRequestQueryService）

> 查询类动作无写库，以「查询范围（读库，无写入）+ 组装规则」表达QueryService；用于列表、历史、冲销与删除/签退门控。

#### 6.2.1 按标识加载服务申请（`loadServiceRequestById`）

- **业务意义**：按主键加载单条服务申请，供列表/详情等弱依赖场景。
- **边界说明**：只读查询，不写库，不做跨域编排。
- **查询范围（读库，无写入）**：`wkf_service_request` 主表（及按需 JOIN 明细）。
- **过滤口径**：排除逻辑删除；租户可见性以落库与交互契约为准。
- **组装规则**：返回聚合QueryService或字段子集。
- **返回约束**：不存在时返回空或 Optional（以实现契约为准）。

#### 6.2.2 必取服务申请（`mustGetServiceRequest`）

- **业务意义**：按主键强依赖加载，供写路径前置加载。
- **边界说明**：只读查询，不写库，不做跨域编排。
- **查询范围（读库，无写入）**：`wkf_service_request` 主表（及按需 JOIN 明细）。
- **过滤口径**：排除逻辑删除。
- **组装规则**：返回完整聚合加载所需字段。
- **返回约束**：不存在或已删除时抛明确业务异常。

#### 6.2.3 按就诊查询服务申请列表（`listServiceRequestsByEncounter`）

- **业务意义**：查询就诊内服务申请列表，支撑医嘱工作台。
- **边界说明**：只读查询，不写库，不做跨域编排。
- **查询范围（读库，无写入）**：`wkf_service_request` 按 `encounter_id`。
- **过滤口径**：默认排除逻辑删除；可按状态子集筛选（以落库与交互契约为准）。
- **组装规则**：按开立时间（空回落签发）或组套字段排序；不内联费用明细。
- **返回约束**：列表可为空；单次查询条数上限以落库与交互契约为准。

#### 6.2.4 查询患者历史服务申请（`listServiceRequestHistoryByPatient`）

- **业务意义**：查询患者历史申请并排除当前就诊，支撑历史追溯。
- **边界说明**：只读查询，不写库，不做跨域编排。
- **查询范围（读库，无写入）**：`wkf_service_request` 按 `patient_id`。
- **过滤口径**：排除当前 `encounter_id`；排除逻辑删除。
- **组装规则**：按时间倒序；字段子集与列表页合同一致。
- **返回约束**：大数据量分页；不返回其他患者数据。

#### 6.2.5 校验是否可删除（`validateServiceRequestDeletable`）

- **业务意义**：删除前费用与状态门控。
- **边界说明**：只读断言；不写库，不做跨域编排。
- **查询范围（读库，无写入）**：主表状态与费用只读摘要（以落库与交互契约为准）。
- **过滤口径**：已收费等禁止删除条件命中则不可删。
- **组装规则**：返回布尔或可删原因码。
- **返回约束**：不通过时上层应拦截删除。

#### 6.2.6 校验是否可签退（`validateServiceRequestWithdrawable`）

- **业务意义**：签退前费用与已校对门控。
- **边界说明**：只读断言；不写库，不做跨域编排。
- **查询范围（读库，无写入）**：主表状态与费用只读摘要（以落库与交互契约为准）。
- **过滤口径**：已收费或已校对等禁止签退条件命中则不可签退。
- **组装规则**：返回布尔或原因码。
- **返回约束**：不通过时拦截签退。

#### 6.2.7 冲销数量校验（`assertOffsetVerification`）

- **业务意义**：校验可冲销数量，支撑冲销提交前置。
- **边界说明**：只读查询，不写库，不做跨域编排；数量关系须满足 **BR-SRQ-014**。
- **查询范围（读库，无写入）**：主表及历史冲销相关行（以实现汇总口径为准）。
- **过滤口径**：按冲销链路与数量汇总策略过滤。
- **组装规则**：返回可冲销余量或校验结果（`原数量 − 已冲销汇总 − 本次预冲销`）。
- **返回约束**：不满足冲销策略时拒绝本次提交。

#### 6.2.8 按长期/临时分轨查询（`listServiceRequestsByTherapyType`）

- **业务意义**：按治疗时长类型分轨查询，支撑住院长期/临时视图。
- **边界说明**：只读查询，不写库，不做跨域编排。
- **查询范围（读库，无写入）**：`wkf_service_request` 按 `therapy_enum` 等字段。
- **过滤口径**：就诊/患者维度与删除标记以落库与交互契约为准。
- **组装规则**：按列表合同排序与字段裁剪。
- **返回约束**：分轨结果互不混算。

#### 6.2.9 按生成来源查询（`listServiceRequestsByGenerateSource`）

- **业务意义**：按生成来源分轨查询，支撑医生/护士/挂号口径分离。
- **边界说明**：只读查询，不写库，不做跨域编排。
- **查询范围（读库，无写入）**：`wkf_service_request` 按 `generate_source_enum`。
- **过滤口径**：就诊/时间窗与删除标记以落库与交互契约为准。
- **组装规则**：按来源分轨返回QueryService 列表查询。
- **返回约束**：不跨来源混排除非显式查询条件允许。

#### 6.2.10 按诊疗定义查询（`getServiceRequestByActivityId`）

- **业务意义**：查询指定项目定义关联申请，支撑诊疗定义侧联查。
- **边界说明**：只读查询，不写库，不做跨域编排。
- **查询范围（读库，无写入）**：`wkf_service_request` 按 `activity_id`。
- **过滤口径**：就诊维度与删除标记以落库与交互契约为准。
- **组装规则**：返回单条或列表（以实现契约为准）。
- **返回约束**：无匹配时返回空集合或空值。

#### 6.2.11 校验转科/出院重复（`checkDuplicateTransferOrDischargeRequest`）

- **业务意义**：检查转科与出院互斥及重复开立。
- **边界说明**：只读查询，不写库，不做跨域编排。
- **查询范围（读库，无写入）**：同就诊下相关类别申请子集。
- **过滤口径**：按类别与状态集合判断是否冲突。
- **组装规则**：返回冲突标记与原因码。
- **返回约束**：冲突时写侧开立应拒绝或走纠错流程。

#### 6.2.12 统计来源派生步次（`countServiceRequestByBasedOnId`）

- **业务意义**：统计来源派生单数量，支撑执行复制 step 计算。
- **边界说明**：只读查询，不写库，不做跨域编排。
- **查询范围（读库，无写入）**：`wkf_service_request` 按 `based_on_id`/`based_on_table`。
- **过滤口径**：排除逻辑删除；派生类型以落库与交互契约为准。
- **组装规则**：返回计数或步次建议值。
- **返回约束**：计数非负；大数据量查询需索引支持。

#### 6.2.13 住院护士站批量按就诊+状态查询（仓储派生 · 下游消费）

- **业务意义**：**AS-IP-NUR-001** 等批量场景：与 **DM-MED-003** §6.2.7 对称，按就诊集合与 `status_enum` 加载诊疗申请行。
- **边界说明**：只读；`ServiceRequestQueryService#listByEncounterIdsAndStatusEnum` → `ServiceRequestRepository#findByEncounterIdInAndStatusEnum`；状态统一 **RequestStatus** code。
- **过滤口径**：`encounter_id IN (...)` + `status_enum`；禁止逐就诊循环查询。
- **返回约束**：允许空列表。

#### 6.2.14 批量按主键加载QueryService 列表查询（`mapListModelsByIds`）

- **业务意义**：**AS-IP-NUR-005** **IF-IP-PRC-05** 已划价列表：按 `service_request` 主键批量 **`mapListModelsByIds`**，补全名称/单位/补费锚点等展示字段。
- **边界说明**：只读；**禁止**应用层直查 Repository；空集合返回空 `Map`。
- **入参**：`Collection<Long> serviceRequestIds`。
- **出参**：`Map<Long, ServiceRequestListModel>`，键为请求主键。
- **前置条件**：无。
- **状态变化**：无。
- **聚合内影响**：无。
- **事件输出**：无。
- **失败语义**：无（缺失 ID 不出现在 Map 中）。

#### 6.2.15 解析父诊疗申请的 activity_id（`resolveActivityIdByServiceRequestId`）

- **业务意义**：**AS-IP-NUR-005** IF-IP-PRC-05 已划价列表：补费父医嘱为诊疗父行时，由 `based_on_id`（父 `wkf_service_request.id`）解析 `activity_id`，再经 **`ActivityDefinitionQueryService#findAllByIds`** 展示名。
- **边界说明**：只读；单条主键加载，不做跨域编排。
- **查询范围（读库，无写入）**：`wkf_service_request` 主表按 `id`。
- **过滤口径**：排除 `is_deleted=true`；不存在或已删返回 `null`。
- **组装规则**：返回 `activity_id` 字段值。
- **返回约束**：`serviceRequestId` 为空返回 `null`；**不**抛错（由应用层决定是否展示占位）。

#### 6.2.16 按就诊+诊疗定义汇总小时用量（`sumQuantityHoursByEncounterAndActivity`）

- **业务意义**：吸氧/心电等按时长计费项目的**已累计小时数**（**AS-IP-NUR-005** **BR-IP-PRC-010** 当日上限 Gate）；与 **DM-ADM-011** §6.2.5 目标能力同族，**本方法为现行 Java 落点**。
- **边界说明**：只读；汇总 `wkf_service_request.quantity`（小时语义由 `activity_definition` 约定）；**不**经 `adm_charge_item` 聚合。
- **入参**：`encounterId`、`activityDefinitionId` 必填；`occurrenceDate`（`LocalDate`）可选——非空时仅统计该自然日内请求行。
- **返回约束**：无匹配行返回 **0**；`encounterId` 或 `activityDefinitionId` 为空返回 **0**。

#### 6.2.17 住院医生站医嘱列表（`listInpatientDoctorStationOrders`）

- **业务意义**：**AS-IP-DOR-003** 住院医嘱 Tab：单就诊下按 `therapy_enum`（长期/临时）分轨的诊疗申请列表。
- **边界说明**：只读；装配 `ServiceRequestListModel`；与 **BR-SRQ-QRY-004** 一致。**排除** `category_enum ∈ {24 手术, 25 麻醉}`（专用页展示）；另排除**挂靠术式**的术前嘱托（`category=52` 且 `based_on` 指向本就诊术式 SR）；**禁止**一刀切排除全部嘱托（普通嘱托仍返回）。
- **入参**：`encounterId`；`therapyEnum`（`TherapyType` 持久化 code，可空表示不过滤）。
- **返回约束**：空列表合法。

#### 6.2.18 住院护士站医嘱校对列表（`listInpatientNurseStationAuditOrders`）

- **业务意义**：**AS-IP-NUR-003** IF-IP-AUD-01：待校对/已校对诊疗+药品+耗材联合列表的诊疗轨数据源（与 **DM-MED-003** / **DM-WKF-003** 列表在应用层合并）。
- **边界说明**：只读；状态、来源、类别过滤以实现与 **AS-IP-NUR-003-SDD** 编排序列一致。
- **入参**：`encounterId` 及护士站列表 Gate 参数（见 SDD）。
- **返回约束**：`List<ServiceRequestListModel>`。

#### 6.2.19 住院护士站医嘱执行列表（`listInpatientNurseStationExecuteOrders`）

- **业务意义**：**AS-IP-NUR-004** 执行 Tab 诊疗轨列表。
- **边界说明**：只读；与校对列表分轨，状态集合不同（含已校对待执行等）。
- **返回约束**：`List<ServiceRequestListModel>`。

#### 6.2.20 住院护士站补费父医嘱候选（`listInpatientNurseStationSupplementOrders`）

- **业务意义**：**AS-IP-NUR-005** IF-IP-PRC-04：补费弹窗父医嘱下拉（诊疗/药品已校对或已停嘱等，见 **DM §6.2** 与 QueryService §3.4.5）。
- **边界说明**：只读；`generate_source_enum = doctor-prescription`；状态 `VERIFIED`/`STOPPED` 等组合以实现为准。
- **返回约束**：`List<ServiceRequestListModel>`。

#### 6.2.21 住院护士站已划价列表（`listInpatientNurseStationPricedOrders`）

- **业务意义**：**AS-IP-NUR-005** IF-IP-PRC-05：护士划价已提交诊疗行回显。
- **边界说明**：只读；`generate_source_enum = nurse-pricing`；创建态 **VERIFIED**（**BR-IP-PRC-018**）。
- **返回约束**：`List<ServiceRequestListModel>`；常与 **`mapListModelsByIds`** 组合回源。

#### 6.2.22 按主键加载列表模型（`getListModelById` / `mustGetListModelById`）

- **业务意义**：住院医嘱/划价列表行按 id 回源；`mustGet` 变体在缺失时抛 `BusinessException`。
- **边界说明**：只读；**禁止**应用层直查 Repository。
- **入参**：`Long id`。
- **返回约束**：`ServiceRequestListModel`；`get` 允许 null，`mustGet` 不存在则失败。

#### 6.2.23 按来源锚点查询派生服务申请（`listServiceRequestsByBasedOnAnchor` / `listServiceRequestsByBasedOnAnchors`）

- **业务意义**：**AS-IP-NUR-012** 取消执行绑定诊疗 **based_on** 反查。
- **边界说明**：只读；`ServiceRequestRepository#findByBasedOnTableAndBasedOnId(In)` 下推；排除逻辑删除。
- **入参**：`basedOnTable` + `basedOnId`（单条）或 `basedOnIds` 集合（批量，同表名）。
- **过滤口径**：`based_on_table` + `based_on_id` 精确匹配；`basedOnTable` 空时按空串计（与 **6.2.12** 一致）。
- **组装规则**：返回写模型实体列表。
- **返回约束**：只读；空 `basedOnIds` 批量路径返回空列表。

#### 6.2.24 批量加载申请明细并解析展示名（`mapDetailListModelsByServiceRequestIds`）

- **业务意义**：**AS-OP-DOR-003** **IF-DOR-10/22**、**AS-IP-DOR-003** **IF-IP-DOR-10/24**：检验/检查/治疗申请单及统一医嘱列表中 **`ServiceRequestDetail`**（部位/样本）回显；**IF-DOR-15** LIS/PACS 外发载荷组装的前置读。
- **边界说明**：只读；`ServiceRequestDetailRepository#findByServiceReqIdIn`；排除 `is_deleted=true` 明细；**禁止**应用层直查明细仓储并自行 N+1 解析主数据展示名。
- **入参**：`Collection<Long> serviceRequestIds`（去重、忽略 null）。
- **展示名解析（读侧批量）**：
  - `target_table = dia_body_structure` → **`BodyStructureQueryService#mapNamesByIds`**
  - `target_table = adm_specimen_definition` → **`SpecimenDefinitionQueryService#listByIds`**
  - 其他 `target_table` → `targetDisplayName = null`（写侧白名单见 **BR-REQ-005** / **BR-SRQ-DTL-001**）
- **组装规则**：映射为 **`ServiceRequestDetailListModel`**（含 `targetTable`/`targetId`/`targetDisplayName`/`remark`）；按 `service_req_id` 分组；组内按明细主键升序。
- **应用消费（成码锚点）**：**`ServiceApplyFormOrchestrationSupport#listApplyForms`**（`application.outpatient.doctorstation.advice.support`）、**`DoctorStationAdviceListAssembler`** / **`InpatientDoctorStationAdviceListAssembler`**（申请单行 **`serviceRequestDetails`**）经 **`ServiceApplyFormAssembler#toDetailSummaryDtos`** 映射 HTTP DTO；**禁止**应用层绕过本方法 N+1 解析展示名。
- **返回约束**：`Map<Long, List<ServiceRequestDetailListModel>>`；无明细的服务申请不出现在 Map 中（调用方按空列表处理）。

#### 6.2.25 滚费护理级别解析（`findLastEffectiveNursingLevelForAutoRoll`）

- **业务意义**：**AS-IP-NUR-013** BR-IP-ARG-009：按 `encounterId + billDate` 解析当日最后有效校对护理级别（category **26**）。
- **边界说明**：只读；来源 **`DOCTOR_PRESCRIPTION`**；状态**仅 `VERIFIED`**（与自动计费配置 `ACTIVE` 同构；**`STOPPED` 一经停嘱即退出滚费池**，须另开新护理级别或办出院）；须 `check_time` 非空且校对自然日不晚于 billDate；同日换级取 `check_time` 最大者。
- **入参**：`encounterId`、`billDate`（均非空；空则 `SERVICE_REQUEST_INVALID_ARGUMENT`）。
- **返回约束**：`Optional<InpatientAutoRollNursingLevelQueryModel>`（`serviceRequestId`、`activityId`、`quantity`、`unitCode`、`requesterId`、`checkTime`、`statusEnum`）；无匹配时 empty。

#### 6.2.31 自动计费 Tab 护理只读列表（`listNursingLevelsForAutoRollTab`）

- **业务意义**：**AS-IP-NUR-013 IF-IP-ARG-02**：护理级别只读区列表；供展开取消已滚护理费（含历史停嘱）。
- **边界说明**：只读；category **26** + `DOCTOR_PRESCRIPTION`；状态 `VERIFIED`/`STOPPED` 且 `check_time` 非空；**始终返回全住院期行**。`billDate` 入参兼容保留，**不裁剪护理行**——按日筛费用在 **IF-IP-ARG-09** `dateFrom`/`dateTo`。按 `check_time` 倒序。
- **入参**：`encounterId`（空则 empty 列表）、`billDate`（可空，忽略）。
- **返回约束**：`List<InpatientAutoRollNursingLevelQueryModel>`；无匹配时 empty。

#### 6.2.32 护理已滚费用展开门控读（`findNursingLevelForAutoRollPostedCharges`）

- **业务意义**：**AS-IP-NUR-013 IF-IP-ARG-09**：展开护理行已滚费用前，校验护理医嘱归属本就诊。
- **边界说明**：只读；须未软删、同 `encounterId`、category **26**、`DOCTOR_PRESCRIPTION`；状态为 `VERIFIED` 或 `STOPPED`（历史停嘱可展开取消）。
- **入参**：`encounterId`、`serviceRequestId`（任一空则 empty）。
- **返回约束**：`Optional<InpatientAutoRollNursingLevelQueryModel>`；不合法时 empty。

#### 6.2.27 并发护理级别门控读（`existsConcurrentNursingLevelOrder`）

- **业务意义**：**AS-IP-DOR-003 BR-IP-ORD-026**：判断同就诊是否已有其它有效护理级别医嘱（排除指定 id）。
- **边界说明**：category **26** + `DOCTOR_PRESCRIPTION`；`STOPPED`/`CANCELLED` 不计入。
- **入参**：`encounterId`、`excludeServiceRequestId`（可 null）。
- **返回约束**：`boolean`。

#### 6.2.28 患者队列护理级别批量读（`mapNursingLevelForPatientQueueByEncounterIds`）

- **业务意义**：**AS-IP-DOR-001 BR-IP-DOR-009** / **AS-IP-NUR-001 BR-IP-NUR-015**：医生站/护士站患者列表批量回填当前护理级别。
- **边界说明**：只读；category **26** + `DOCTOR_PRESCRIPTION`；口径：仅 **`VERIFIED`**（同 **6.2.25**；**`STOPPED` 不展示为当前级别**）；取 `referenceDate` 当日最后校对生效者。
- **入参**：`encounterIds`（非空集合）、`referenceDate`（展示参考自然日；null 时按当日）。
- **返回约束**：`Map<Long, InpatientAutoRollNursingLevelQueryModel>`；无匹配就诊不出现在 Map 中。应用层须经 **`InpatientPatientQueueNursingLevelEnrichAssembler`** join `ActivityDefinition` 名称后映射 HTTP DTO。

#### 6.2.26 护士工作量类目索引（`mapCategoryEnumByEncounterIds`）

- **业务意义**：**AS-RPT-002** Support 预解析 `serviceRequestId → category_enum`。
- **边界说明**：只读；按就诊 id 集合批量加载；排除逻辑删除。
- **返回约束**：无命中返回空 Map。

#### 6.2.29 （已废止）陈旧未签发草稿列表

- **说明**：**AS-FIN-PRC-001 v0.12** 起调价未签发草稿策略仅作用于本单物流（药/耗）项目，**不再**对诊疗 `ServiceRequest` 定时或随单调价废弃；本节原 `listStaleUnsignedDrafts` 契约废止。
- **取消口径**：医生自行删除仍走既有草稿删除/取消写路径；`assertCancelable` 含 `DRAFT` 的既有口径不变。

#### 6.2.30 批量解析出院医嘱计划时间（`mapDischargePlannedTimeByEncounterIds`）

- **业务意义**：护士站/医生站队列、病案首页、报表等**业务出院时间**真源：出院类嘱 `occurrence_start_time`（医生填写的计划出院时间）；供 `BusinessDischargeTimeResolver` 优先于 `Encounter.end_time`。
- **边界说明**：只读；仅出院类目（`category_enum=34`）；**仅** `ACTIVE`/`VERIFIED`/`COMPLETED` 生效嘱参与；软删/草稿/已取消不计（反结软删出院嘱后业务出院时间须清空，医生重新开立；对齐 **AS-IP-FIN-004 BR-IP-RVS-007**）。
- **入参**：就诊主键集合。
- **组装规则**：同就诊多条有效嘱取主键最大者（末次开立）的计划时间；无计划时间的就诊不入 Map（调用方回落 `Encounter.endTime`）。
- **返回约束**：`Map<Long, LocalDateTime>`；空集合返回空 Map。

### 6.3 领域边界说明

- `ServiceRequest` 仅收口本聚合及 `ServiceRequestDetail` 的一致性，不直接修改 `ChargeItem`、`Procedure`、`Encounter`。
- 检验/检查跨系统发送、费用推进与回退、流程单创建由应用编排与事件订阅协作完成。
- 服务申请分方能力与药品处方分方严格分轨，避免无边界混算（分方算法与批次策略本文不展开）。
- **QueryService与查询分层（与读侧方法配套）**：
  - **BR-SRQ-QRY-001**：就诊内列表优先QueryService 刷新；写前强校验须回到主表加载或 `mustGetServiceRequest`。
  - **BR-SRQ-QRY-002**：患者历史查询必须排除当前 `encounter_id`，防止历史污染。
  - **BR-SRQ-QRY-003**：查询、断言、写入严格分层；写侧不以只读投影为唯一事实源。
  - **BR-SRQ-QRY-004**：住院医嘱列表须支持 `therapy_enum` 分轨，与写侧长期/临时口径一致。
  - **BR-SRQ-QRY-005**：按 `generate_source_enum` 分轨查询，避免医生开立与护士划价数据混查。
  - **BR-SRQ-QRY-006**：挂号登记来源须显式按 `REGISTRATION_REGISTER` 过滤，与医生开立、护士划价分轨。
  - **BR-SRQ-QRY-007**：申请明细批量读须经 **`ServiceRequestQueryService#mapDetailListModelsByServiceRequestIds`**；`target_table` 展示名由读侧按 **6.2.24** 白名单批量解析，应用层不得对明细目标主数据 N+1 直查。
- **医保与事前合规（横切）**：**BR-RX-001～007** 全文以 **`DM-MED-003-MedicationRequest-药品申请.md` §9.7** 为权威；本聚合仅承载**服务侧**目录/限额/审批类前置校验的**契约入口**（由用例在开立/签发前与药品轨结果合并编排），不在此重复药品味数等规则。

---

## 7. 不变式

| 编号 | 不变式 | 说明 |
| --- | --- | --- |
| BR-SRQ-001 | 状态迁移必须通过领域动作收口 | 禁止绕开动作直接改 `status_enum` |
| BR-SRQ-002 | 已收费申请不可删除 | 删除前校验费用状态 |
| BR-SRQ-003 | 已收费或已校对申请不可签退 | 签退前校验费用与状态 |
| BR-SRQ-004 | 草稿删除须同事务清理主档与明细 | 避免孤儿数据 |
| BR-SRQ-005 | 执行复制/绑定必须写来源关系 | `based_on_table` + `based_on_id` 必填 |
| BR-SRQ-006 | 打印次数只允许增量累加 | 禁止覆盖写入 |
| BR-SRQ-007 | 开立数量与单位由前端载荷或子项 JSON 提供，缺省时由应用层按产品目录主单位解析；**禁止**域层将长期医嘱数量强制改写为 1 | 与 **BR-ORD-016**、**DM-ADM-011 BR-CHG-008** 一致 |
| BR-SRQ-008 | 挂号登记创建即 `ACTIVE` | 不经医生草稿/签发链路 |
| BR-SRQ-024 | 护士划价创建即 `VERIFIED`（`initVerified`；写入校对人/校对时间）；`generate_source_enum=nurse-pricing` **不计入** **AS-IP-NUR-001** 待校对/待执行条数（见 **BR-IP-NUR-004/005**） | 不经校对 Tab；对齐 **AS-IP-NUR-005** **BR-IP-PRC-007/018** |
| BR-SRQ-009 | 套餐子项必须关联父项 | `parent_id` 链路一致 |
| BR-SRQ-010 | 转科与出院申请互斥且不可重复 | 同一就诊下互斥校验 |
| BR-SRQ-011 | 明细必须归属主申请 | `service_req_id` 必填 |
| BR-SRQ-DTL-001 | 明细目标成对必填 | `target_table` + `target_id` 均必填 |
| BR-SRQ-DTL-003 | 每行单一目标引用 | 一行仅表达一个目标；标本+部位等多维组合拆多行 |
| BR-SRQ-012 | 草稿删除的协作清理由用例编排 | 除主档与明细外，费用草稿、同组套餐子项等撤销或删除由**同一用例**编排收口；本领域服务不承诺隐式跨域副作用 |
| BR-SRQ-013 | 检验/检查跨系统申请仅签发后触发 | 草稿保存/修改不得触发对外发送；发送编排须在签发达到可发送状态之后执行 |
| BR-SRQ-014 | 冲销数量须非负 | `原数量 − 已冲销汇总 − 本次预冲销 ≥ 0`；汇总口径与读侧 `assertOffsetVerification` 一致 |
| BR-SRQ-015 | 执行派生业务号可追溯步次 | 分步执行场景下 `bus_no`（或产品线约定字段）须能区分第几次派生，避免不可区分重复 |
| BR-SRQ-016 | 住院长期停嘱须收口时间轴右端；**禁止 ACTIVE 停嘱**（须 **VERIFIED**；门诊 **COMPLETED** 保留可停） | 进入 `STOPPED` 写 `stop_time`/`stopper_id`；清空停嘱校对两字段；**不**占用 `occurrence_end_time` |
| BR-SRQ-016a | 住院长期取消停嘱须未停嘱校对 | 须 `STOPPED` 且 `stop_performer_check_id` 空；恢复 VERIFIED/ACTIVE；清空停嘱轴与停嘱校对两字段 |
| BR-SRQ-016b | 住院可改开立/停嘱时间；改停嘱时间**保留**停嘱校对字段 | `OPENED`/`STOP` 仅写对应时间列；不改开立人/停嘱人/停嘱校对；禁止改签发 |
| BR-SRQ-016c | 停嘱校对只写痕迹、不改状态 | `verifyStopServiceRequest`：长期 + STOPPED + 未停嘱校对 → 写两字段；仍 STOPPED |
| BR-SRQ-016d | 取消停嘱校对只清痕迹；本期不校验操作人 | `cancelVerifyStopServiceRequest`：清两字段；仍 STOPPED；解锁医生取消停嘱 |
| BR-SRQ-017 | 取消须保留审计追溯 | 进入 `CANCELLED` 时写入取消主体、时间、科室等可追溯字段（以实现列为准），保留记录不等同物理删除 |
| BR-SRQ-018 | 出院申请须通过时长余量校验 | 开立前校验吸氧/心电监测等已开立时长相对在院时长之余量；未通过则拒绝 |
| BR-SRQ-019 | 停嘱子医嘱由编排联动（**废止：住院医生站 IF-IP-DOR-20 本期仅停选中行**） | 历史口径：通过 `based_on_*` 关联子嘱由编排联动；**现行**住院医生站停嘱/取消停嘱**均不**级联 |
| BR-SRQ-020 | 护理级别医嘱按就诊唯一有效 | 同一 `encounter_id + category_enum=26`（医生开立）下 **`DRAFT`/`ACTIVE`/`VERIFIED` 仅一条**；换级须 **停嘱旧单** 后再新建（**AS-IP-DOR-003 BR-IP-ORD-026**；**禁止**并发两条有效）；`upsertNursingOrder` 专用路径仍可按「停旧建新」幂等（§6.1.12） |
| BR-SRQ-021 | 药品执行派生须写药品来源 | 派生单行 `based_on_table` 指向药品申请主表名、`based_on_id` 为药品医嘱主键，`generate_source_enum` 为医嘱**执行/绑定**类来源值 |
| BR-SRQ-022 | 校对追溯字段须一致落库 | 校对动作须写入校对人、校对时间；退回草稿时追溯语义与字段策略可审计、与产品口径一致 |
| BR-SRQ-023 | 明细不参与主状态机 | `ServiceRequestDetail` 不单独承载 `RequestStatus` 迁移，仅随主档生命周期增删 |

---

## 8. 上下文映射

### 8.1 上游依赖

| 上游上下文 | 聚合 | 关联方式 | 说明 |
| --- | --- | --- | --- |
| 患者管理 | Patient | `患者标识(PatientId)` | 服务申请归属患者 |
| 就诊管理 | Encounter | `就诊标识(EncounterId)` | 服务申请归属就诊 |
| 基础数据 | ActivityDefinition | `诊疗定义标识(ActivityDefinitionId)` | 决定诊疗类别与项目定义 |
| 组织架构 | Organization | `机构科室标识(OrganizationId)` | 执行科室 |

### 8.2 下游影响

| 下游上下文 | 聚合 | 触发方式 | 说明 |
| --- | --- | --- | --- |
| 收费管理 | ChargeItem | 事件订阅 | 依据 `service_table='wkf_service_request'` + `service_id` 关联 |
| 执行管理 | Procedure | 事件订阅 | 校对/执行后生成执行记录 |
| 就诊流程 | EncounterHistory | 事件订阅 | 转科/出院流程单联动 |
| 就诊管理 | Encounter | 事件订阅 | 转科/出院校对后就诊状态联动 |
| 药品申请 | MedicationRequest | 引用 | 药品执行可派生服务申请（来源链路） |

### 8.3 引用规则

> **统一规则**：跨上下文只允许传递 `ServiceRequestId`（Long）；来源追溯统一使用 `basedOnTable` + `basedOnId`；费用关联统一使用 `serviceTable='wkf_service_request'` + `serviceId`（与物理主表 `wkf_service_request` 一致）；禁止跨上下文绕过领域契约以裸 SQL 直连读写主表。

### 8.4 事件流示意图

```text
签发操作  ──> [ServiceRequestSignedEvent]     ──> 执行待办/费用推进
校对操作  ──> [ServiceRequestVerifiedEvent]   ──> Procedure/Encounter联动
停止操作  ──> [ServiceRequestStoppedEvent]    ──> 子医嘱联动/费用回退
取消操作  ──> [ServiceRequestCancelledEvent]  ──> 撤销链路
转科申请  ──> [TransferDepartmentRequestCreatedEvent] ──> 流程单协作
出院申请  ──> [DischargeRequestCreatedEvent]  ──> 流程单协作
```

---

## 9. 补充

### 9.1 文档变更历史

| 版本 | 日期 | 摘要 |
| ---- | ---- | ---- |
| v1.1.42 | 2026-10-11 | **代码先行回写**：§6.1.14a `createFromMedicalTechPricing` + `MedicalTechPricingServiceRequestCreatedEvent`；对齐 **AS-MT-CHG-001 v1.3** IF-MT-CHG-07 |
| v1.1.42 | 2026-10-16 | **代码先行回写**：**§6.2.31** `listNursingLevelsForAutoRollTab`、**§6.2.32** `findNursingLevelForAutoRollPostedCharges`（自动计费 Tab 护理历史展开/取消）；对齐 **AS-IP-NUR-013 v1.6.0** |
| v1.1.41 | 2026-10-09 | **代码先行回写**：§6.1.9b `softDeleteDischargeServiceRequestForReverseSettle`（反结软删出院嘱，禁止 CANCELLED 留痕）；对齐 **AS-IP-FIN-004 v1.8 BR-IP-RVS-007** |
| v1.1.40 | 2026-10-09 | **代码先行回写**：§6.2.30 `mapDischargePlannedTimeByEncounterIds` 仅认生效态出院嘱（草稿/取消不计）；对齐 **AS-IP-FIN-004 v1.7 BR-IP-RVS-007** |
| v1.1.39 | 2026-10-09 | **代码先行回写**：§6.1.18 边界明确应用层取消编排仅传入 `order-execute` based_on 行；对齐 **AS-IP-NUR-012 v1.12 / BR-IP-CXL-013** |
| v1.1.38 | 2026-10-08 | **代码先行回写**：§6.2.17 医嘱 Tab 排除 24/25 + 挂靠术式术前嘱托（禁止全排 52）；对齐口径 v0.10 |
| v1.1.37 | 2026-10-08 | **代码先行回写**：§3.1 明确术前嘱托 basedOn 术式为手麻唯一主子；麻醉不写 basedOn；对齐住院手术口径 v0.7 |
| v1.1.36 | 2026-10-08 | **代码先行回写**：§3.1 `procedure_definition_id` 注释明确加收由护士划价、医生站开立不写；对齐住院手术口径 v0.6（开立禁止附加） |
| v1.1.35 | 2026-10-07 | **代码先行回写**：§3.1 增补 `procedure_definition_id`；`activity_id=0` 作术式/无计费锚占位；对齐住院手术口径 v0.5（术式执行不计费） |
| v1.1.34 | 2026-10-06 | **代码先行回写**：§6.1.14 带子项诊疗落子项账单、结算禁 SALE 回落；与医嘱组套区分 |
| v1.1.32 | 2026-09-16 | §6.2.29：废止诊疗陈旧草稿定时清理契约；对齐 **AS-FIN-PRC-001 v0.12** |
| v1.1.31 | 2026-09-16 | （已由 v1.1.32 取代）`listStaleUnsignedDrafts`；对齐 AS v0.11 |
| v1.1.30 | 2026-08-13 | §6.2.25/28：滚费/队列护理级别仅 **`VERIFIED`**（`STOPPED` 停嘱即退池；对齐 **AS-IP-NUR-013 v1.5.0 BR-IP-ARG-009**） |
| v1.1.30 | 2026-09-06 | §6.1.21 **`softDeleteStoppedServiceRequestAfterDecoctionRefunded`**：代煎费退款后软删 STOPPED companion；对齐 **AS-OP-FIN-003 · BR-REF-025** |
| v1.1.29 | 2026-08-08 | **停嘱校对**：§3 增补 `stop_performer_check_id`/`stop_check_time`；§6.1.8/8a/8b 门禁与清/留字段；§6.1.8c/8d 停嘱校对/取消停嘱校对；**BR-SRQ-016/016a/016b/016c/016d**；对齐 **AS-IP-NUR-003 v1.16** / **AS-IP-DOR-003 v1.39** / **DM-MED-003 v1.3.29** |
| v1.1.28 | 2026-08-01 | **退诊疗负向单幂等**：§6.1.9a 已有未完结负向单返回已有主键；仅原单已是负向单拒；对齐 **AS-OP-DOR-004 v1.2.8** |
| v1.1.27 | 2026-07-26 | §6.1.5：`withdrawSignedServiceRequest` **已是 DRAFT 幂等返回**（皮试联动 + 同批显式勾选二次签退不报错）；对齐实现与 **DM-SDD §5.1.5** / **AS-OP/IP-DOR-003-SDD** |
| v1.1.27 | 2026-07-24 | §6.1.14 套餐用时解析类名对齐 **`ActivityDefinitionPackageChildResolver`** / 医保 **`ChsActivityPackageFeeExpandSupport`** |
| v1.1.26 | 2026-07-18 | **App IF-IP-DOR-26 已落地**；§6.1.8b 实现状态与 AS v1.33 对齐 |
| v1.1.25 | 2026-07-18 | **领域落地**：`stopper_id` + `changeServiceRequestBusinessTime`；**G-IP-DOR-003-06 Closed**；App 当时仍为目标态（已被 v1.1.26 关闭） |
| v1.1.24 | 2026-07-18 | **停嘱人 `stopper_id`** + **§6.1.8b 改业务时间**；**BR-SRQ-016/016a/016b**；对齐 **DM-MED-003 v1.3.25** / **AS-IP-DOR-003 v1.31 · IF-IP-DOR-26**（领域代码待落地，**G-IP-DOR-003-06**） |
| v1.1.23 | 2026-07-16 | §4.5 增补 **`DISCHARGE_MEDICATION`（出院带药）**；与 **DM-MED-003 v1.3.24** / **AS-IP-DOR-003 · BR-IP-ORD-029** 共用枚举；关闭 **G-IP-DOR-003-05** |
| v1.1.22 | 2026-07-16 | §4.3 增补 **`52` / `ENTRUST` 嘱托**；对齐 **AS-IP-DOR-003 · BR-IP-ORD-028** |
| v1.1.21 | 2026-07-09 | **门诊申请退费（AS-OP-DOR-004 BR-DRA-007）**：§6.1.9a **`createServiceOrderRefund`**；负向单 **`CANCELLED`** + `refund_service_id` + 数量取反；§5 增补 **`ServiceOrderRefundRequestedEvent`** |
| v1.1.20 | 2026-07-03 | §6.2.26 **`mapCategoryEnumByEncounterIds`**（**AS-RPT-002** 护士工作量类目 enrich） |
| v1.1.19 | 2026-06-19 | **AS-IP-NUR-013 滚费域能力**：§6.1.20 **`createFromAutoRoll`**、§6.2.25 **`findLastEffectiveNursingLevelForAutoRoll`**、§5 **`AutoRollServiceRequestCreatedEvent`**（关闭 **GAP-IP-ARG-001/004**） |
| v1.1.18 | 2026-06-18 | **IF-IP-DOR-25 成码闭环**：§6.1.8a 标注**已落地**；入参补 `operatorId`；实体 **`assertCancelStopAllowed`** / **`fromStopped`** 与实现对齐；**G-IP-DOR-003-03 Closed**；对齐 **AS v1.17** |
| v1.1.17 | 2026-06-18 | **取消停嘱（IF-IP-DOR-25）**：§6.1.8a `cancelStopServiceRequest`；**BR-SRQ-016a**；事件 `ServiceRequestStopCancelledEvent`；**BR-SRQ-019** 与 IF-IP-DOR-20 对齐 |
| v1.1.16 | 2026-06-18 | §6.1.19 **`removeFromNursePricing`**、§5 **`NursePricingServiceRequestRemovedEvent`**（**AS-IP-NUR-005** IF-IP-PRC-06；关闭 **GAP-IP-PRC-007/010**） |
| v1.1.15 | 2026-06-16 | §6.2.24 增补应用消费锚点（`ServiceApplyFormOrchestrationSupport` / `ServiceApplyFormAssembler` / 列表 Assembler） |
| v1.1.14 | 2026-06-16 | §6.2.24 **`mapDetailListModelsByServiceRequestIds`** + **`ServiceRequestDetailListModel`**；**BR-SRQ-QRY-007**；关闭 **G-DOR-003-11** / **G-IP-DOR-003-02** |
| v1.1.13 | 2026-06-11 | **废止 `sign_code`**；医生站暂存经 Mapper 落库 **`encounter_diagnosis_id`**（与药品对称，对齐 **AS-OP-DOR-003-SDD v3.19**） |
| v1.1.12 | 2026-06-10 | §6.1.18 **`removeExecutedServiceRequest`**、§6.2.23 **`listServiceRequestsByBasedOnAnchor(s)`**、§5 **`ServiceRequestExecutedRemovedEvent`**（**AS-IP-NUR-012** Gap 005/007） |
| v1.1.10 | 2026-05-28 | §6.2.16～22：对齐 `ServiceRequestQueryService` 住院列表、`sumQuantityHoursByEncounterAndActivity`、`getListModelById`/`mustGetListModelById` |
| v1.1.9 | 2026-05-28 | **BR-SRQ-007**：撤销「长期数量固定为 1」；`quantity`/`unit_code` 由载荷或应用层目录解析；域层禁止强制归 1 |
| v1.1.8 | 2026-05-28 | ReadModel 废止：删除 **OrderInfo** / **ProductDefinition** RM 消费表述；住院医嘱列表改 **`ServiceRequestQueryService`** + **`InpatientOrderListSkeleton`** |
| v1.1.7 | 2026-05-24 | **护士划价**创建态 **`VERIFIED`**（非 ACTIVE）；**`NursePricingServiceRequestCreatedEvent`** 增补 **`statusEnum`** 载荷；**BR-SRQ-024**；挂号登记仍 **BR-SRQ-008 ACTIVE** |
| v1.1.6 | 2026-05-24 | §4.5 统一 **`TherapyType`**（`LONG_TERM`/`TEMPORARY` 落库），与 **DM-MED-003** §4.2 共用 |
| v1.1.5 | 2026-05-24 | §5.3 **`NursePricingServiceRequestCreatedEvent`** 载荷细表；§6.2.14 **`mapListModelsByIds`**（IF-IP-PRC-05 回源） |
| v1.1.4 | 2026-05-24 | §6.2.14 **`resolveActivityIdByServiceRequestId`**（已划价父医嘱展示）；§6.1.14 套餐展开口径改为子表 + 应用编排（**BR-IP-PRC-011**） |
| v1.1.3 | 2026-05-24 | **createFromNursePricing** 对齐 **AS-IP-NUR-005**：批量 `items` + `List<Long>` 出参；补费 `based_on_table`/`based_on_id`；`therapy_enum=TEMPORARY`；**不写** `sign_code`；关闭 **GAP-IP-PRC-003** |
| v1.1.2 | 2026-05-22 | **verifyServiceRequest** → **VERIFIED**（`activeToVerified`）；**completeServiceRequestAfterOutpatientCharge** → **COMPLETED** 独立门控；退回/停嘱/取消支持 **VERIFIED**；事件载荷补 `status_enum`（对齐 **AS-IP-NUR-003** Gap 关闭） |

###

<!-- MODE-B-METHOD-INVENTORY 2026-09-23 -->

## 附录 · 模式 B 已实现方法清单（2026-09-23）

> 自 zxplat-domain 对应 *DomainService / *QueryService 抽取 public 方法名，用于关闭方法白名单 DOC-DRIFT。细规仍以正文 §6 与源码为准；本表不替代九章成稿。

### DomainService：`ServiceRequestDomainService`

| 方法 |
|------|
| `createServiceRequestDraft` |
| `modifyServiceRequestDraft` |
| `removeServiceRequestDraft` |
| `signServiceRequest` |
| `withdrawSignedServiceRequest` |
| `verifyServiceRequest` |
| `completeServiceRequestAfterOutpatientCharge` |
| `rejectServiceRequestToDraft` |
| `verifyStopServiceRequest` |
| `cancelVerifyStopServiceRequest` |
| `stopServiceRequest` |
| `cancelStopServiceRequest` |
| `changeServiceRequestBusinessTime` |
| `voidOpenRefundServiceOrder` |
| `softDeleteStoppedServiceRequestAfterDecoctionRefunded` |
| `resumeStoppedServiceRequestForOutpatientRefundRevoke` |
| `cancelServiceRequest` |
| `softDeleteDischargeServiceRequestForReverseSettle` |
| `createServiceOrderRefund` |
| `createTransferDepartmentRequest` |
| `createDischargeRequest` |
| `upsertNursingOrder` |
| `createExecutedServiceRequest` |
| `removeExecutedServiceRequest` |
| `createFromNursePricing` |
| `createFromMedicalTechPricing` |
| `ensureAutoRollBedLongTermServiceRequest` |
| `removeFromNursePricing` |
| `createFromOutpatientRegistration` |
| `increasePrintCount` |
| `cloneServiceRequestForPreservedChargeRegeneration` |

### QueryService：`ServiceRequestQueryService`

| 方法 |
|------|
| `loadServiceRequestById` |
| `findAutoRollBedServiceRequestIdByRuleId` |
| `resolveActivityIdByServiceRequestId` |
| `mapListModelsByIds` |
| `mapCategoryEnumByEncounterIds` |
| `mustGetServiceRequest` |
| `listByEncounterIds` |
| `mapDischargePlannedTimeByEncounterIds` |
| `listServiceRequestsByEncounter` |
| `sumQuantityHoursByEncounterAndActivity` |
| `listDistinctEncounterIdsByPatientForDoctorStation` |
| `listServiceRequestHistoryByPatient` |
| `validateServiceRequestDeletable` |
| `validateServiceRequestWithdrawable` |
| `assertOffsetVerification` |
| `listServiceRequestsByTherapyType` |
| `listServiceRequestsByGenerateSource` |
| `mapServiceRequestsByEncounterIdsAndGenerateSource` |
| `listByEncounterAndPrescriptionNo` |
| `getServiceRequestByActivityId` |
| `existsByActivityId` |
| `existsByActivityIdIn` |
| `checkDuplicateTransferOrDischargeRequest` |
| `hasVerifiedOppositeTransferOrDischargeRequest` |
| `hasVerifiedTransferOrDischargeRequest` |
| `listByEncounterIdsAndStatusEnum` |
| `listInpatientDoctorStationOrders` |
| `listInpatientNurseStationAuditOrders` |
| `listInpatientNurseStationExecuteOrders` |
| `listInpatientNurseStationSupplementOrders` |
| `listInpatientNurseStationPricedOrders` |
| `resolveBusNoByServiceRequestId` |
| `listByRefundServiceIdIn` |
| `resolveEncounterIdByServiceRequestId` |
| `findLastEffectiveNursingLevelForAutoRoll` |
| `listNursingLevelsForAutoRollTab` |
| `findNursingLevelForAutoRollPostedCharges` |
| `existsConcurrentNursingLevelOrder` |
| `listNursingLevelOrderLinesForCaseRecord` |
| `mapNursingLevelForPatientQueueByEncounterIds` |
| `getListModelById` |
| `findActiveListModelById` |
| `findFirstActiveByPrescriptionNo` |
| `mustGetListModelById` |
| `countServiceRequestByBasedOnId` |
| `listServiceRequestsByBasedOnAnchor` |
| `listServiceRequestsByBasedOnAnchors` |
| `findActiveDetailsByServiceRequestIds` |
| `countReferencingEncounterDiagnosisId` |

