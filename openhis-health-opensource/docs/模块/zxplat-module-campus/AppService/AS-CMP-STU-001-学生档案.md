# 学生档案 · 应用服务设计（AS）

> **编码**：AS-CMP-STU-001 · **版本**：v1.3 · **日期**：2026-07-19  
> **类名**：`CampusStudentAppService` · **路由**：`/campus/student`  
> **HTTP**：`/api/app/campusStudent`  
> **表**：`cmp_patient_student` · **DM**：DM-CMP-STU-001

---

## 1. 边界

| 在内 | 学籍 CRUD（campus 表）；绑 Patient；公费 pe_profile；变更/转院 |
| 在外 | Patient 主档建档；Peis 体检/白名单；微信绑定 |

**写路径**：`PatientStudentDomainService`（campus）→ pe_profile → ChangeLog / Transfer。

---

## 2. 接口总览

| IF | 方法 | 读/写 |
|----|------|:--:|
| IF-CMP-STU-01 | `pageStudents` | 读 |
| IF-CMP-STU-02/03 | `createStudent` / `updateStudent` | 写 |
| IF-CMP-STU-04 | `getStudentDetail` | 读 |
| IF-CMP-STU-05 | `disableStudent` | 写 |
| IF-CMP-STU-13 | `bindStudentPatient` | 写 |
| IF-CMP-STU-14/14b | `saveStudentPeProfile` | 写 |
| IF-CMP-STU-17 | `resolveStudentCampus` | 读 |
| IF-CMP-STU-18/19 | `pageStudentChanges` / `listChangeBatch` | 读 |
| IF-CMP-STU-21 | `transferStudent` | 写 |
| IF-CMP-STU-22 | `pageStudentTransfers` | 读 |

### create 必填

`name`、`studentNo`、`collegeId`、`campusCode`

### `transferStudent` / `pageStudentTransfers`

同 v1.2：正式转院登记 + 详情 Tab 时间线；普通 update 改组织字段须 `UPDATE_DERIVED`。

---

## 3. Gap

| Gap | 说明 | 状态 |
|-----|------|------|
| GAP-CMP-MIG-03 | peis 学生 → `cmp_patient_student` 洗数 | ⬜ |

~~GAP-CMP-ADM-01/02/03~~ 已取消（health 已移除 ADM 学籍子表）。

---

## 5. 异常码

`STUDENT_NO_DUPLICATE` / `STUDENT_NOT_FOUND` / `COLLEGE_INACTIVE` / `CAMPUS_INACTIVE` / `TRANSFER_NO_CHANGE` / `PE_CARD_DUPLICATE`

---

## 9. 关联

- DM / SDD：学生档案  
- 调动：[DM-CMP-ARCH-002](../DomainModel/DM-CMP-ARCH-002-ArchiveTransfer-调动记录.md)
