# Campus · AppService 契约索引

> **原则**：1 主页面 = 1 AppService → `/api/app/{camelCaseAppService}/{methodName}`  
> **总表**：[../03-AppService详细设计.md](../03-AppService详细设计.md)  
> **版本**：v1.0 · **日期**：2026-07-19

| 编码 | 文档 | 类名 | P | SDD |
|------|------|------|---|-----|
| AS-CMP-ORG-CMP-001 | [校区管理](./AS-CMP-ORG-CMP-001-校区管理.md) | `CampusSiteAppService` | P0 | [SDD](../AppService-SDD/AS-CMP-ORG-CMP-001-校区管理-SDD.md) |
| AS-CMP-ORG-COL-001 | [学院管理](./AS-CMP-ORG-COL-001-学院管理.md) | `CampusCollegeAppService` | P0 | [SDD](../AppService-SDD/AS-CMP-ORG-COL-001-学院管理-SDD.md) |
| AS-CMP-ORG-DEP-001 | [部门管理](./AS-CMP-ORG-DEP-001-部门管理.md) | `CampusDepartmentAppService` | P0 | [SDD](../AppService-SDD/AS-CMP-ORG-DEP-001-部门管理-SDD.md) |
| AS-CMP-STU-001 | [学生档案](./AS-CMP-STU-001-学生档案.md) | `CampusStudentAppService` | P0 | [SDD](../AppService-SDD/AS-CMP-STU-001-学生档案-SDD.md) |
| AS-CMP-STF-001 | [教职工档案](./AS-CMP-STF-001-教职工档案.md) | `CampusStaffAppService` | P0 | [SDD](../AppService-SDD/AS-CMP-STF-001-教职工档案-SDD.md) |
| AS-CMP-ARCH-001 | [档案共通 Client](./AS-CMP-ARCH-001-档案共通.md) | `CampusArchiveQueryAppService` | P0 | [SDD](../AppService-SDD/AS-CMP-ARCH-001-档案共通-SDD.md) |
| AS-CMP-GF-CFG-001 | [公费配置](./AS-CMP-GF-CFG-001-公费配置.md) | `CampusGfConfigAppService` | P0 | [SDD](../AppService-SDD/AS-CMP-GF-CFG-001-公费配置-SDD.md) |
| AS-CMP-GF-RATIO-001 | [目录比例](./AS-CMP-GF-RATIO-001-目录比例.md) | `CampusGfRatioCatalogAppService` | P0 | 见 AS 内嵌 SDD 要点 |
| AS-CMP-GF-RATIO-002 | [项目比例](./AS-CMP-GF-RATIO-002-项目比例.md) | `CampusGfRatioItemAppService` | P0 | 见 AS |
| AS-CMP-GF-APR-001 | [比例审批](./AS-CMP-GF-APR-001-比例审批.md) | `CampusGfApprovalAppService` | P0 | 迁移 nenu-vue-his |
| AS-CMP-GF-APR-002 | [申请记录](./AS-CMP-GF-APR-002-申请记录.md) | `CampusGfRequestRecordAppService` | P0 | 迁移 nenu-vue-his |
| AS-CMP-GF-CALC-001 | [收费试算](./AS-CMP-GF-CALC-001-收费试算.md) | `CampusGfChargeCalcAppService` | P0 | |
| AS-CMP-OPEN-001 | [Open API](./AS-CMP-OPEN-001-OpenAPI.md) | `CampusGfOpenApiAppService` | P0 | 迁移 nenu-vue-his |

P1/P2（FIN/SYNC/RPT）契约待阶段启动时补；索引见 [03](../03-AppService详细设计.md)。
