# 档案共通 Client · 应用服务设计（AS）

> **编码**：AS-CMP-ARCH-001 · **版本**：v1.2 · **日期**：2026-07-19  
> **类名**：`CampusArchiveQueryAppService` · **无页面**  
> **HTTP**：`/api/app/campusArchiveQuery`  
> **DM**：ChangeLog + pe_profile + Transfer

---

## 1. 边界

| 在内 | Patient 绑定、孤儿列表、变更查询、**调动时间线查询**、公费资格、HIS 同步、组织过滤 |
| 在外 | 档案 CRUD（STU/STF）；收银拆分（CALC） |

---

## 2. 接口总览

| IF | 方法 | 读/写 |
|----|------|:--:|
| IF-CMP-ARCH-01 | `bindArchiveByIdCard` | 写 |
| IF-CMP-ARCH-02 | `pageOrphanArchives` | 读 |
| IF-CMP-ARCH-05/06 | `checkPublicExpenseEligibility` | 读 |
| IF-CMP-ARCH-07 | `resolveCampusProfileByPatientId` | 读 |
| IF-CMP-ARCH-08 | `pageArchivesByOrg` | 读 |
| IF-CMP-ARCH-09 | `listArchiveChanges` | 读 |
| IF-CMP-ARCH-11 | `syncPeProfileFromHis` | 写 |
| IF-CMP-ARCH-12 | （由 STU/STF 调领域）调动写入 | 写 |
| IF-CMP-ARCH-13 | `listArchiveTransfers` | 读 |

> ARCH-03 / ARCH-12：变更与调动由 STU/STF 写路径内聚调用；本 AS 提供统一查询 Client。

---

## 4. 关键接口

### `checkPublicExpenseEligibility`

**Request**：`patientId` 或 `(subjectType, subjectId)`  
**Response**：`passed` / `reasonCode` / `reasonMessage` / `gfStatusEnum` / `cardNo` / `medicalCode` / `campusCode`

规则见 [DM-CMP-GF-001](../DomainModel/DM-CMP-GF-001-PublicExpenseProfile-公费资格.md)。

### `listArchiveTransfers`

**Request**：`subjectType`、`subjectId`、分页  
**Response**：Transfer 时间线（对齐 STU-22 / STF-20）

### `syncPeProfileFromHis`（← 旧 `syncHisInfo`）

**Request 行**：`name?`、`studentNo`、`idCard`、`cardNo`（旧 busNo）、`gfStatus`（旧 0/1/2 或枚举）  
**编排**：匹配学生 → upsert pe_profile.cardNo + gfStatusEnum → ChangeLog（source=SYSTEM/SYNC）

### `resolveCampusProfileByPatientId`

返回 subjectType、subjectId、cardNo、gfStatusEnum、medicalCode、campusCode、collegeId/departmentId。

---

## 5. 异常码

`ARCHIVE_NOT_FOUND` / `PATIENT_NOT_FOUND` / `PE_CARD_DUPLICATE`

---

## 9. 关联

- SDD：[AS-CMP-ARCH-001-SDD](../AppService-SDD/AS-CMP-ARCH-001-档案共通-SDD.md)
- Contracts：[08](../08-Contracts与外部依赖.md)
