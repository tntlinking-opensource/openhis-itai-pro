# 体检域 · 体检目录 · 应用服务设计（AS）

> **编码**：AS-PEIS-CFG-001 · **版本**：v1.4 · **日期**：2026-10-07  
> **菜单**：体检目录 · **类名**：`PeisCatalogConfigAppService` · **路由**：`/peis/config/catalog`

---

## 1. 场景概述

### 1.1 目标与边界

| 维度 | 说明 |
|------|------|
| **在内** | 小项(OD)、项目(AD)、套餐(AD 组合) |
| **在外** | health `ActivityDefinition`；`cln_observation_definition`；**执行科室**走 `DepartmentAppService`；**业务类型**为术语 `activity_type_code`（CFG-04 项目 / CFG-05 套餐保存时前端必选，禁止后端写死） |
| **合并** | 遗留 7 个 system 菜单 → 1 页 Tab |

### 1.2 角色

| 角色 | 动作 |
|------|------|
| **管理员** | 维护目录三层 + 套餐 |
| **系统** | 阻塞 REG-02 无有效套餐/项目 |

### 1.3 术语

| 遗留 | ZXPlat |
|------|-------|
| sys_check_package | AD 组合（children） |
| sys_check_project | AD（type=检验/检查/其他） |
| sys_check_item | ObservationDefinition |

### 1.4 范围

| P0 | CFG-03～05（**CFG-01/02 已撤销**：科室走基础管理；类型为枚举随项目选择） |
| P1 | **CFG-06/07**（本 AS Tab 扩展，不新建 AS） |

### 1.5 P1 Tab（CFG-06/07）

| IF | Tab | 语义 |
|----|-----|------|
| **IF-PEIS-CFG-06** | 申请单模板 | 检验/检查申请单打印模板绑定（PRT 场景码） |
| **IF-PEIS-CFG-07** | 检线项目分组 | 导检队列/科室分组（`ProjectGroup` 遗留对齐） |

### 1.6 BR

| 编号 | 规则 | IF |
|------|------|-----|
| **BR-CFG-01** | 小项 → `cln_observation_definition` | CFG-03 |
| **BR-CFG-02** | 项目 AD 须绑定 `type_enum`（术语 `activity_type_code`，前端必填；禁止硬编码） | CFG-04 |
| **BR-CFG-03** | 套餐 = AD children；子项须 active；套餐 `type_enum` 前端必填并透传落库（禁止写死「其他」） | CFG-05 |
| **BR-CFG-04** | 项目↔小项经 `cus_activity_observation_link` | CFG-04 |
| **BR-CFG-05** | 停用前检查在检 Encounter 引用 | 各 save |

---

## 2. 接口总览

| IF | Tab | 语义 | 读写 |
|----|-----|------|:--:|
| ~~**IF-PEIS-CFG-01**~~ | ~~科室~~ | ~~PEIS 执行科室~~ | **已撤销**（主数据在科室管理） |
| ~~**IF-PEIS-CFG-02**~~ | ~~项目类型~~ | ~~分类字典~~ | **已撤销**（类型改术语 `activity_type_code`，CFG-04/05 下拉选择） |
| **IF-PEIS-CFG-03** | 体检小项 | OD CRUD | 读/写 |
| **IF-PEIS-CFG-04** | 体检项目 | AD + 小项链接 | 读/写 |
| **IF-PEIS-CFG-05** | 体检套餐 | AD 组合 | 读/写 |
| **IF-PEIS-CFG-06** | 申请单模板 | PRT binding | 读/写 |
| **IF-PEIS-CFG-07** | 检线分组 | 项目分组字典 | 读/写 |

---

## 3. 缺口跟踪（Gap）

| Gap ID | 缺口类型 | 文件 + 章节 | 缺什么 / 不一致点 | 关闭条件 | 影响 IF | 优先级 | 状态 |
|--------|----------|-------------|-------------------|----------|---------|--------|------|
| GAP-PEIS-CFG-01 | **DM 能力** | [DM-CUS-013](./DM-CUS-013-ActivityObservationLink-项目小项绑定.md) §6 | `ActivityObservationLinkDomainService#saveLinks`（`domain.custom`） | DM §6 与领域代码一致 | CFG-04 | P0 | ✅ 已关闭（领域 + PeisCatalogConfigAppService，2026-07-19） |
| GAP-PEIS-CFG-02 | **DM 能力** | [DM-CLN-005](./DM-CLN-005-ObservationDefinition-临床观察定义.md) §6.1 | `ObservationDefinitionDomainService#save` | DM §6.1 与领域代码实现一致 | CFG-03 | P0 | ✅ 已关闭（领域 + PeisCatalogConfigAppService，2026-07-19） |

---

## 4. 编排要点

- CFG-03：写 `cln_observation_definition`（[DM-CLN-005](./DM-CLN-005-ObservationDefinition-临床观察定义.md)）
- CFG-04/05：写 health `ActivityDefinition` + children 关系
- 与 LIS/PACS：**同一 activity_id**，无 PEIS↔LIS 对照表

---

## 5. 业务异常

| 码 | 场景 |
|----|------|
| `CATALOG_IN_USE` | 在检引用不可停用 |
| `CHILDREN_EMPTY` | 套餐无子项 |

---

## 6. 事件

`PeisCatalogChangedEvent`（P2 缓存）。

---

## 7. NFR

目录变更 AuditLog；REG 读缓存 TTL（院点）。

---

## 8. 测试

- 套餐展开 REG-02 正确 SR 数
- type_enum=PROOF 可走 LIS INT
- 小项链接 DES-02 可见

---

## 9. 关联文档

- REG：[AS-PEIS-REG-001](./AS-PEIS-REG-001-个检登记.md)
- DM：[DM-CLN-005](./DM-CLN-005-ObservationDefinition-临床观察定义.md)、[DM-WKF-001](./DM-WKF-001-ActivityDefinition-诊疗项目定义.md)

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.4 | 2026-10-07 | **业务类型**：项目/套餐必填；选项术语 `activity_type_code`；套餐禁止写死 type；对齐 **DM-WKF-001 v1.0.20** |
| v1.3 | 2026-07-19 | **撤销 CFG-02**：项目类型 Tab/接口删除；类型在保存体检项目时选 `ActivityType` |
| v1.2 | 2026-07-19 | **撤销 CFG-01**：执行科室改走基础数据科室管理，本 AS 不再提供 searchDept/saveDept |
| v1.1 | 2026-06-27 | P1：CFG-06/07 并入本 AS Tab |
| v1.0 | 2026-06-27 | 首版 P0 目录 |
