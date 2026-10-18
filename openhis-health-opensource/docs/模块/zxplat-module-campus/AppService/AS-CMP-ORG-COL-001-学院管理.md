# 学院管理 · 应用服务设计（AS）

> **编码**：AS-CMP-ORG-COL-001 · **版本**：v1.0 · **日期**：2026-07-19  
> **类名**：`CampusCollegeAppService` · **路由**：`/campus/org/college`  
> **HTTP**：`/api/app/campusCollege`  
> **成稿编号**：DM-CMP-ORG-002

---

## 1. 边界

| 在内 | 学院分页/增改/启停/绑校区；Active 列表 Client |
| 在外 | Excel 导入（P1）；Peis 团检业务 |

---

## 2. 接口总览

| IF | 方法 | 读/写 |
|----|------|:--:|
| IF-CMP-COL-01 | `pageColleges` | 读 |
| IF-CMP-COL-02 | `createCollege` / `updateCollege` | 写 |
| IF-CMP-COL-03 | `changeCollegeActive` | 写 |
| IF-CMP-COL-04 | `bindCollegeCampus` | 写 |
| IF-CMP-COL-08 | `listActiveColleges` | 读 |
| IF-CMP-COL-09 | `getCollegeById` | 读 |

---

## 4. 接口编排

### `pageColleges`

Request：`keyword?`、`campusSiteId?`、`activeFlag?`、分页  
Response：含 `campusCode`/`campusName`（联查 Site）

### `createCollege` / `updateCollege`

Request：`collegeCode`（仅 create）、`collegeName`、`campusSiteId?`、`sortOrder`、`remark`  
异常：`COLLEGE_CODE_DUPLICATE`、`CAMPUS_NOT_FOUND`、`CAMPUS_INACTIVE`

### `listActiveColleges`

Request：`campusCode?`（空=全部启用；有值=该校区 + 全校通用）  
供 INT-04 / `CampusOrgQueryReader`

---

## 5. 异常码

| 码 | 场景 |
|----|------|
| `COLLEGE_CODE_DUPLICATE` | |
| `COLLEGE_NOT_FOUND` | |
| `CAMPUS_INACTIVE` | 绑定停用校区 |

---

## 9. 关联

- SDD：[AS-CMP-ORG-COL-001-SDD](../AppService-SDD/AS-CMP-ORG-COL-001-学院管理-SDD.md)
