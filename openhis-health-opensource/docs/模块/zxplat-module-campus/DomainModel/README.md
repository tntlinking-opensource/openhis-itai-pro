# Campus · DomainModel 索引

> **工程**：`zxplat-plugin/zxplat-module-campus/module-campus-domain`  
> **包根**：`com.zxplat.plugin.campus.domain`  
> **版本**：v1.4 · **日期**：2026-09-24  
> **入口**：[说明.md](./说明.md)

| 编码 | 文档 | 表 | P | SDD |
|------|------|-----|---|-----|
| **DM-CMP-ORG-001** | [CampusSite](./DM-CMP-ORG-001-CampusSite-校区.md) | `cmp_campus_site` | P0 | [SDD](../DomainModel-SDD/DM-CMP-ORG-001-CampusSite-校区-SDD.md) |
| **DM-CMP-ORG-002** | [CampusCollege](./DM-CMP-ORG-002-CampusCollege-学院.md) | `cmp_campus_college` | P0 | [SDD](../DomainModel-SDD/DM-CMP-ORG-002-CampusCollege-学院-SDD.md) |
| **DM-CMP-ORG-003** | [CampusDepartment](./DM-CMP-ORG-003-CampusDepartment-部门.md) | `cmp_campus_department` | P0 | [SDD](../DomainModel-SDD/DM-CMP-ORG-003-CampusDepartment-部门-SDD.md) |
| **DM-CMP-STU-001** | [PatientStudent 学生](./DM-CMP-STU-001-PatientStudent-学生.md) | **`cmp_patient_student`** | P0 | [SDD](../DomainModel-SDD/DM-CMP-STU-001-PatientStudent-学生-SDD.md) |
| **DM-CMP-STF-001** | [PatientStaff 教职工](./DM-CMP-STF-001-PatientStaff-教职工.md) | `cmp_patient_staff` | P0 | [SDD](../DomainModel-SDD/DM-CMP-STF-001-PatientStaff-教职工-SDD.md) |
| **DM-CMP-ARCH-001** | [ArchiveChangeLog](./DM-CMP-ARCH-001-ArchiveChangeLog-档案变更.md) | `cmp_archive_change_log` | P0 | [SDD](../DomainModel-SDD/DM-CMP-ARCH-001-ArchiveChangeLog-档案变更-SDD.md) |
| **DM-CMP-ARCH-002** | [ArchiveTransfer](./DM-CMP-ARCH-002-ArchiveTransfer-调动记录.md) | `cmp_archive_transfer` | P0 | [SDD](../DomainModel-SDD/DM-CMP-ARCH-002-ArchiveTransfer-调动记录-SDD.md) |
| **DM-CMP-GF-001** | [PublicExpenseProfile](./DM-CMP-GF-001-PublicExpenseProfile-公费资格.md) | `cmp_pe_profile` | P0 | [SDD](../DomainModel-SDD/DM-CMP-GF-001-PublicExpenseProfile-公费资格-SDD.md) |
| **DM-CMP-GF-002** | [GfRatio](./DM-CMP-GF-002-GfRatio-公费比例.md) | `cmp_gf_*` | P0 | [SDD](../DomainModel-SDD/DM-CMP-GF-002-GfRatio-公费比例-SDD.md) |

**组织树**

```text
CampusSite
  ├── CampusCollege ← PatientStudent.college_id
  │     └── CampusDepartment（可选）
  └── CampusDepartment ← PatientStaff.department_id
```
