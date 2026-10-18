# 部门管理 · 应用服务设计（AS）

> **编码**：AS-CMP-ORG-DEP-001 · **版本**：v1.0 · **日期**：2026-07-19  
> **类名**：`CampusDepartmentAppService` · **路由**：`/campus/org/department`  
> **HTTP**：`/api/app/campusDepartment`  
> **成稿编号**：DM-CMP-ORG-003

---

## 2. 接口总览

| IF | 方法 | 读/写 |
|----|------|:--:|
| IF-CMP-DEP-01 | `pageDepartments` | 读 |
| IF-CMP-DEP-02 | `createDepartment` / `updateDepartment` | 写 |
| IF-CMP-DEP-03 | `changeDepartmentActive` | 写 |
| IF-CMP-DEP-04 | `reparentDepartment` | 写 |
| IF-CMP-DEP-06 | `getDepartmentTree` | 读 |
| IF-CMP-DEP-08 | `listDepartments` | 读 |
| IF-CMP-DEP-09 | `getDepartmentById` | 读 |

---

## 4. 编排要点

- create：强制 `campusSiteId`；`collegeId` 可选  
- `getDepartmentTree`：返回 SITE→COLLEGE→DEPT 树（只含启用，参数 `includeInactive` 默认 false）  
- 异常：`DEPT_CODE_DUPLICATE`、`DEPT_NOT_FOUND`、`COLLEGE_CAMPUS_MISMATCH`

---

## 9. 关联

- SDD：[AS-CMP-ORG-DEP-001-SDD](../AppService-SDD/AS-CMP-ORG-DEP-001-部门管理-SDD.md)
