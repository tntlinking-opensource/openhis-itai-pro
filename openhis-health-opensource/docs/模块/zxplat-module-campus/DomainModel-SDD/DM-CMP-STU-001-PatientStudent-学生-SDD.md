# DM-CMP-STU-001 PatientStudent · DomainModel-SDD

> **包**：`com.zxplat.plugin.campus.domain.archive.patientstudent`  
> **对应 DM**：v1.0 · **表**：`cmp_patient_student`

---

## 1. 包结构

`entity` / `command` / `repository` / `service` / `queries` / `event`

## 2. 仓储

```java
Optional<PatientStudent> findByTenantIdAndStudentNoAndIsDeletedFalse(Long tenantId, String studentNo);
Optional<PatientStudent> findByTenantIdAndPatientIdAndIsDeletedFalse(Long tenantId, Long patientId);
boolean existsByTenantIdAndStudentNoAndIsDeletedFalse(Long tenantId, String studentNo);
```

## 3. 跨域

仅读/绑 health `Patient`（按身份证）；**不**依赖 health `PatientStudent` 领域服务。

## 4. 修订

| v1.0 | 2026-07-19 | PASS · 学籍归 campus |
