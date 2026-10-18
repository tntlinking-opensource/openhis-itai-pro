# Campus · Flyway 表结构真源

> **版本**：v1.4 · **日期**：2026-07-19
> **模块**：`zxplat-module-campus` · ModularFlyway **`db/migration/campus/`**  
> **history**：`flyway_schema_history_campus` · **开关**：`zxplat.modules.campus.enabled`  
> **DDL 草案**：[flyway/V1__campus_ddl.sql](./flyway/V1__campus_ddl.sql)

---

## 一、Campus 表（campus 模块）

| 表 | DM | P | Flyway |
|----|-----|---|--------|
| `cmp_campus_site` | [DM-CMP-ORG-001](./DomainModel/DM-CMP-ORG-001-CampusSite-校区.md) | P0 | **campus V1** |
| `cmp_campus_college` | [DM-CMP-ORG-002](./DomainModel/DM-CMP-ORG-002-CampusCollege-学院.md) | P0 | **campus V1** |
| `cmp_campus_department` | [DM-CMP-ORG-003](./DomainModel/DM-CMP-ORG-003-CampusDepartment-部门.md) | P0 | **campus V1** |
| **`cmp_patient_student`** | [DM-CMP-STU-001](./DomainModel/DM-CMP-STU-001-PatientStudent-学生.md) | P0 | **campus V1** |
| `cmp_patient_staff` | [DM-CMP-STF-001](./DomainModel/DM-CMP-STF-001-PatientStaff-教职工.md) | P0 | **campus V1** |
| `cmp_pe_profile` | [DM-CMP-GF-001](./DomainModel/DM-CMP-GF-001-PublicExpenseProfile-公费资格.md) | P0 | **campus V1** |
| `cmp_archive_change_log` | [DM-CMP-ARCH-001](./DomainModel/DM-CMP-ARCH-001-ArchiveChangeLog-档案变更.md) | P0 | **campus V1** |
| `cmp_archive_transfer` | [DM-CMP-ARCH-002](./DomainModel/DM-CMP-ARCH-002-ArchiveTransfer-调动记录.md) | P0 | **campus V1** |
| `cmp_gf_*` | [DM-CMP-GF-002](./DomainModel/DM-CMP-GF-002-GfRatio-公费比例.md) | P0 | **campus V1** |

P2：`cmp_archive_sync_state` 本 V1 **不建**。

---

## 二、与 health ADM 的关系

| 项 | 口径 |
|----|------|
| health ADM | **已移除** `adm_patient_student`；Patient 聚合仅保留主表 + 标识子表 |
| 学籍真源 | **`cmp_patient_student`**（本模块） |
| 患者绑定 | `cmp_patient_student.patient_id` → `adm_patient.id` |
| 公费卡 | `cmp_pe_profile`（subject_id = 本表 id） |
| 历史数据迁移 | **仅** peis（`peis_customer_student` 等）/ 遗留 HIS → `cmp_patient_student`；**不再**从 health ADM 双轨同步 |

**已取消**：在 `adm_patient_student` 上扩 `college_id`/`campus_code`（原 GAP-CMP-ADM-01/02）——字段直接落在 `cmp_patient_student`。

---

## 三、模块注册

```json
{
  "moduleId": "campus",
  "enabledProperty": "zxplat.modules.campus.enabled",
  "locations": "classpath:db/migration/campus",
  "historyTable": "flyway_schema_history_campus"
}
```

---

## 四、修订记录

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.0～v1.2 | 2026-07-19 | 组织/公费/调动；曾规划扩 ADM 学生表 |
| v1.4 | 2026-07-19 | health 已移除 `adm_patient_student`；迁移仅 peis → cmp |
| v1.3 | 2026-07-19 | **学籍迁入** `cmp_patient_student`；废止 ADM 扩展方案 |
