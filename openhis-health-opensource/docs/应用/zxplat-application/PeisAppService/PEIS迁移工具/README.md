# PEIS 迁移工具 · 校医院场景

> 源：local MySQL `peis` · 目标：ZXPlat PG  
> 业务：学生新生检 / 在校年检 / 教职工体检；毕业出具健康档案；本次升级顺带理顺乱数据。

## 阶段

| 阶段 | 内容 |
|------|------|
| P1 | 目录 OD/AD/AOL/常见结果 |
| P2 | `peis_customer` → Patient |
| **P2.4** | **校医院主数据**：校区 + `peis_company`(学院) → Campus；可选按学院建部门 |
| P2.5 | 学生→`cmp_patient_student`，教职工→`cmp_patient_staff`（FK 走 P2.4 映射） |
| P2.6 | `peis_health_record*` → `peis_patient_health_archive` |
| P3 | 体检单 Encounter+SR+Obs；分科诊断；总检 DR；复查 Task |

勾选 **Campus** 时默认执行 P2.4+P2.5（`Campus.MigrateOrgMaster=true`）。  
体检类型写入 `peis_encounter_ext.package_snapshot_json.examKind`：`freshman` / `annual` / `staff` / `other`。  
跑完生成 `config/state/governance-*.json` 对账摘要。

## 前置

1. 勾选 Campus 且开启 `MigrateOrgMaster` 时，工具可自动建校区/学院/部门；关闭则需目标库已有 `DefaultCollegeId` 等  
2. `Order.StudentServiceType` / `StaffServiceType` 按老字典调整（默认学生=2、教职工=1）  
3. 建议 `MaxOrders=100` DryRun 试跑后再全量  

```powershell
cd docs/领域/PeisAppService/PEIS迁移工具/tools/PeisMigrator
dotnet run --project src/PeisMigrator.App
```

详见 [01-迁移方案.md](./01-迁移方案.md)。
