# DM-CMP-GF-001 PublicExpenseProfile · 公费资格

> **表名**：`cmp_pe_profile`  
> **版本**：v1.2 · **日期**：2026-07-19  
> **应用服务编号**：AS-CMP-ARCH-001
> **遗留**：`peis_customer_student.bus_no` / `gf_status`；`his_gf_person.C_MEDICAL_CODE`

---

## 1. 概述

公费医疗资格：公费卡号/二维码、医疗证号、有效期、资格状态。与 campus `PatientStudent` / `PatientStaff` 1:1。  
`subject_id`：学生 = `cmp_patient_student.id`；教职工 = `cmp_patient_staff.id`。

**命名隔离（重要）**

| 字段 | 归属 | 含义 |
|------|------|------|
| `adm_patient.bus_no` | health Patient | **院内编码**（业务序列，创建后不可改） |
| `cmp_pe_profile.card_no` | Campus 本表 | **公费卡号/学生二维码**（旧 peis `bus_no`、HIS 同步） |
| `cmp_pe_profile.medical_code` | Campus 本表 | **医疗证号**（教职工常见；学生可空） |

三者**不得混用**同一列。

---

## 2. 持久化形态

| 列 | 类型 | 说明 |
|----|------|------|
| id | int8 | PK |
| tenant_id | int8 | |
| subject_type_enum | varchar(32) | STUDENT / STAFF |
| subject_id | int8 | 学生记录 id 或 staff id |
| patient_id | int8 | 可空 |
| card_no | varchar(64) | 公费卡号/二维码 ← 旧 `bus_no` |
| medical_code | varchar(64) | 医疗证号（可空） |
| gf_status_enum | varchar(32) | 资格状态，见 §2.1 |
| category_enum | varchar(32) | 公费类别（可选细分） |
| valid_from / valid_to | timestamptz | |
| eligible_flag | bool | 档案侧总开关；与 gf_status 协同见 BR |
| remark | varchar(512) | |
| version / 审计 / 软删 | 标准列 | |

唯一：`(tenant_id, subject_type_enum, subject_id)` 未删。  
可选唯一（未删且非空）：`(tenant_id, card_no)`、`(tenant_id, medical_code)`。

### 2.1 `gf_status_enum`（对齐旧 peis / HIS）

| 码 | 旧 Integer | 含义 |
|----|------------|------|
| `NOT_ELIGIBLE` | 0 / 1 | 不享受公费 |
| `ELIGIBLE` | 2 | 享受公费 |
| `UNKNOWN` | 空/其他 | 未同步或未知 |

微信展示：旧逻辑「1 或 0 = 不享受；2 = 享受」→ 映射上表。

HIS 同步入口（旧 `syncHisInfo`）：按学号+身份证更新 `card_no` + `gf_status_enum`。

---

## 3. 字段

| 字段名 | 类型 | 必填 | 说明 |
|--------|------|:--:|------|
| id | Long | 系统 | |
| tenantId | Long | 是 | |
| subjectTypeEnum | String | 是 | |
| subjectId | Long | 是 | |
| patientId | Long | 否 | |
| cardNo | String | 否 | ← peis `bus_no` |
| medicalCode | String | 否 | |
| gfStatusEnum | String | 是 | 默认 `UNKNOWN` |
| categoryEnum | String | 否 | |
| validFrom / validTo | LocalDateTime | 否 | |
| eligibleFlag | Boolean | 是 | 默认 true |

---

## 5. 领域事件

| 事件 | 触发 |
|------|------|
| `PublicExpenseProfileChangedEvent` | 增改（含 HIS 同步） |

---

## 6. 领域服务

### 6.1 写侧 — `PublicExpenseProfileDomainService`

| 方法 | 说明 |
|------|------|
| `upsert(UpsertPublicExpenseProfileCommand)` | |
| `syncFromHis(SyncPeProfileFromHisCommand)` | 对齐旧 syncHisInfo：写 cardNo + gfStatus |
| `disable(DisablePublicExpenseProfileCommand)` | |

### 6.2 读侧

| 接口签名 | 场景 |
|----------|------|
| `getBySubject(tenantId, type, subjectId)` | |
| `findByCardNo(tenantId, cardNo)` | 微信公费码 / 读卡 |
| `checkEligibility(...): EligibilityResult` | ARCH-05/06（也可由 AS 编排） |

**EligibilityResult**：`passed` / `reasonCode` / `reasonMessage`；另可回传 `gfStatusEnum`、`cardNo`。  
失败时 **必须** 给出明确原因（[ADR-CMP-001](../ADR-CMP-001-成码前决策.md) D9），禁止笼统「无资格」。

**通过条件（NENU 默认 · 严）**

| 人员 | 条件 |
|------|------|
| 学生 | CFG-01 开；`gf_status=ELIGIBLE`；在校；有效期内；**体检合格**；学院/校区启用；已绑 Patient 时身份证一致 |
| 教职工 | CFG-01 开；`gf_status=ELIGIBLE`；在职；有效期内；部门/校区启用；有医疗证/有效期则未过期；Patient active |

**常用 reasonCode**：`SWITCH_OFF` / `GF_STATUS_NOT_ELIGIBLE` / `PHYSICAL_EXAM_NOT_PASS` / `OUT_OF_VALID_PERIOD` / `ORG_DISABLED` / `NOT_IN_SCHOOL` / `NOT_IN_SERVICE` / `PROFILE_DISABLED`

---

## 7. 业务规则

| 编号 | 规则 |
|------|------|
| **BR-CMP-PE-01** | 科目唯一 |
| **BR-CMP-PE-02** | 资格校验只读 |
| **BR-CMP-PE-03** | CFG-01 关闭 → `SWITCH_OFF` |
| **BR-CMP-PE-04** | `card_no` **不是** `Patient.bus_no` |
| **BR-CMP-PE-05** | `gf_status=NOT_ELIGIBLE` → 校验失败（`GF_STATUS_DENY`），优先于仅看有效期 |
| **BR-CMP-PE-06** | `eligible_flag=false` → 失败（`PROFILE_DISABLED`） |

---

## 8. 修订记录

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.0 | 2026-07-19 | 成码首版 |
| v1.2 | 2026-07-19 | 移除与 health `checkStudentPublicExpenseEligibility` 对齐说明；资格校验归 Campus |
| v1.1 | 2026-07-19 | 对齐 old_peis：`card_no`/`gf_status`；隔离 Patient.bus_no |
| v1.3 | 2026-07-19 | ADR-CMP-001 D9：严规则 + 明确原因码 |
