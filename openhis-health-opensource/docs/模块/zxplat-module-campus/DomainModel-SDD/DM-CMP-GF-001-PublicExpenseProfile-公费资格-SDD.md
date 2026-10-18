# DM-CMP-GF-001 PublicExpenseProfile · DomainModel-SDD

> **包**：`com.zxplat.plugin.campus.domain.gf.publicexpense`  
> **对应 DM**：v1.1

---

## 1. 实体字段（成码）

`cardNo` / `medicalCode` / `gfStatusEnum` / `eligibleFlag` / `validFrom` / `validTo` …

## 2. 仓储

```java
Optional<PublicExpenseProfile> findByTenantIdAndSubjectTypeEnumAndSubjectIdAndIsDeletedFalse(...);
Optional<PublicExpenseProfile> findByTenantIdAndCardNoAndIsDeletedFalse(Long tenantId, String cardNo);
```

## 3. 资格校验编排落点

推荐 application `CampusArchiveQueryAppService#checkPublicExpenseEligibility`；domain 提供 getBySubject / findByCardNo。

HIS 同步：`PublicExpenseProfileDomainService#syncFromHis`。

## 4. 修订

| v1.1 | 2026-07-19 | card_no / gf_status；隔离 Patient.bus_no |
| v1.0 | 2026-07-19 | PASS |
