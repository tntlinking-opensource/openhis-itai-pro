# DM-CMP-ARCH-002 ArchiveTransfer · DomainModel-SDD

> **包**：`com.zxplat.plugin.campus.domain.archive.transfer`  
> **对应 DM**：v1.0

---

## 1. 包结构

`entity` / `command`(RecordStudentTransfer, RecordStaffTransfer, RecordDerived) / `repository` / `service` / `queries` / `event`

## 2. 仓储

```java
Page<CampusArchiveTransfer> findByTenantIdAndSubjectTypeEnumAndSubjectIdOrderByEffectiveDateDescOperateTimeDesc(
    Long tenantId, String subjectType, Long subjectId, Pageable pageable);
```

## 3. 与 ChangeLog

同事务：档案组织字段更新 → `ArchiveTransferDomainService#record*` → `ArchiveChangeLogDomainService#appendBatch`（collegeId/campusCode 或 departmentId/campusSiteId）。

## 4. 修订

| v1.0 | 2026-07-19 | PASS |
