# DM-CMP-STF-001 PatientStaff · DomainModel-SDD

> **包**：`com.zxplat.plugin.campus.domain.archive.patientstaff`  
> **对应 DM**：v1.1

---

## 1. 包结构

`entity` / `command`(Create,Update,BindPatient,Disable) / `repository` / `service` / `queries` / `event`

## 2. 仓储

```java
Optional<PatientStaff> findByTenantIdAndEmployeeNoAndIsDeletedFalse(Long tenantId, String employeeNo);
Optional<PatientStaff> findByTenantIdAndMedicalCodeAndIsDeletedFalse(Long tenantId, String medicalCode);
Optional<PatientStaff> findByTenantIdAndPatientIdAndIsDeletedFalse(Long tenantId, Long patientId);
boolean existsByTenantIdAndEmployeeNoAndIsDeletedFalse(Long tenantId, String employeeNo);
boolean existsByTenantIdAndMedicalCodeAndIsDeletedFalse(Long tenantId, String medicalCode);
```

## 3. 跨域

绑定 Patient：AS 调 health 按身份证匹配。公费卡号在 pe_profile，不在本实体。

## 4. 修订

| v1.1 | 2026-07-19 | 工号主业务键；medicalCode 可空 |
| v1.0 | 2026-07-19 | PASS |
