# 教职工档案 · 应用服务设计（AS）

> **编码**：AS-CMP-STF-001 · **版本**：v1.2 · **日期**：2026-07-19  
> **类名**：`CampusStaffAppService` · **路由**：`/campus/staff`  
> **HTTP**：`/api/app/campusStaff`  
> **成稿编号**：DM-CMP-STF-001

---

## 2. 接口总览

| IF | 方法 | 读/写 |
|----|------|:--:|
| IF-CMP-STF-01 | `pageStaff` | 读 |
| IF-CMP-STF-02/03 | `createStaff` / `updateStaff` | 写 |
| IF-CMP-STF-04 | `getStaffDetail` | 读 |
| IF-CMP-STF-05 | `disableStaff` | 写 |
| IF-CMP-STF-12 | （create 必填）`employeeNo` | |
| IF-CMP-STF-06 | （可选）`medicalCode` | |
| IF-CMP-STF-13 | `bindStaffPatient` | 写 |
| IF-CMP-STF-16/17 | `pageStaffChanges` / `listChangeBatch` | 读 |
| IF-CMP-STF-19 | `transferStaff` | 写 |
| IF-CMP-STF-20 | `pageStaffTransfers` | 读 |

**create 必填**：`name`、`employeeNo`、`departmentId`、`campusSiteId`

### `transferStaff`（调岗）

**Request**：`staffId`、`toDepartmentId`、`toCampusSiteId`（或 campusCode）、`effectiveDate`、`reason?`、`externalRef?`  
**编排**：校验目标部门/校区 → 更新 staff → `recordStaffTransfer` → ChangeLog → 事件

### `pageStaffTransfers`

详情 Tab「调动记录」：from/to 部门·校区、类型、生效日、原因、操作人。

### `updateStaff` 组织字段

若改了 `departmentId`/`campusSiteId`：自动 `recordDerivedFromUpdate`。推荐正式调岗走 `transferStaff`。

---

## 4. 编排（普通增改）

1. Domain create/update  
2. 同步 pe_profile  
3. ChangeLog（+ 组织变更时 Transfer）  
4. bind Patient  

---

## 5. 异常码

`EMPLOYEE_NO_DUPLICATE` / `MEDICAL_CODE_DUPLICATE` / `STAFF_NOT_FOUND` / `DEPT_INACTIVE` / `TRANSFER_NO_CHANGE`

---

## 9. 关联

- SDD：[AS-CMP-STF-001-SDD](../AppService-SDD/AS-CMP-STF-001-教职工档案-SDD.md)
