# DM-CMP-ARCH-001 ArchiveChangeLog · DomainModel-SDD

> **包**：`com.zxplat.plugin.campus.domain.archive.changelog`

---

## 1. 要点

- 仅追加写；无 update/delete（P2 保留策略另议）
- `appendBatch` 生成同一 `batchId`（UUID）
- 分页按 `change_time DESC`

## 2. 仓储

```java
Page<CampusArchiveChangeLog> findByTenantIdAndSubjectTypeEnumAndSubjectId(
    Long tenantId, String subjectType, Long subjectId, Pageable pageable);
List<CampusArchiveChangeLog> findByTenantIdAndBatchId(Long tenantId, String batchId);
```

## 3. 修订

| v1.0 | 2026-07-19 | PASS |
