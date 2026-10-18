# AS-CMP-STU-001 学生档案 · SDD

> **包**：`com.zxplat.plugin.campus.application.archive.student`  
> **类**：`CampusStudentAppService`  
> **领域**：`com.zxplat.plugin.campus.domain.archive.patientstudent`

## 依赖

| 依赖 | 用途 |
|------|------|
| campus `PatientStudentDomainService` | 学籍写 |
| health Patient 查询 | 绑定 |
| College/Site Query | 校验 |
| ArchiveChangeLog / ArchiveTransfer | 变更与转院 |
| PublicExpenseProfile | 公费卡 |

**不依赖** health `PatientStudent` 领域服务。

## 写库顺序

create：`cmp_patient_student` → `cmp_pe_profile?` → `cmp_archive_change_log`  
转院：更新学籍组织列 → `cmp_archive_transfer` → change_log

| v1.3 | 2026-07-19 | 学籍表迁 campus |
| v1.2 | 2026-07-19 | transfer |
| v1.0 | 2026-07-19 | 初版 |
