# 校区管理 · 应用服务设计（AS）

> **编码**：AS-CMP-ORG-CMP-001 · **版本**：v1.0 · **日期**：2026-07-19  
> **类名**：`CampusSiteAppService` · **路由**：`/campus/org/campus`  
> **HTTP 前缀**：`/api/app/campusSite`  
> **成稿编号**：DM-CMP-ORG-001

---

## 1. 目标与边界

| 维度 | 说明 |
|------|------|
| **在内** | 校区分页、增改、启停、设主校区；Client 列表 |
| **在外** | 学院/部门维护；Peis 号源日历（P2） |

### 1.6 BR

| 编号 | 规则 | IF |
|------|------|-----|
| BR-AS-SITE-01 | 编码创建后不可改 | CMP-02 |
| BR-AS-SITE-02 | 同租户唯一 | CMP-04 |

---

## 2. 接口总览

| IF | 方法 | 读/写 |
|----|------|:--:|
| IF-CMP-CMP-01 | `pageCampusSites` | 读 |
| IF-CMP-CMP-02 | `createCampusSite` / `updateCampusSite` | 写 |
| IF-CMP-CMP-03 | `changeCampusSiteActive` | 写 |
| IF-CMP-CMP-04 | （含于 create） | 写 |
| IF-CMP-CMP-07 | `listActiveCampusSites` | 读 |
| IF-CMP-CMP-08 | `resolveCampusByCode` | 读 |
| — | `setPrimaryCampusSite` | 写 |

---

## 3. Gap

| Gap | 说明 | 状态 |
|-----|------|------|
| — | P0 无阻塞 Gap | ✅ |

---

## 4. 接口编排

### 4.1 `pageCampusSites`

**Request**：`keyword?`、`activeFlag?`、`page`/`size`  
**Response**：`Page<CampusSiteRow>`（id, campusCode, campusName, shortName, address, isPrimary, activeFlag, sortOrder）  
**编排**：`CampusSiteQueryService#page`

### 4.2 `createCampusSite` / `updateCampusSite`

**Request**：create 含 campusCode/name/…；update 含 id + 可变字段（无 code）  
**编排**：DomainService create/update  
**异常**：`CAMPUS_CODE_DUPLICATE` / `CAMPUS_NOT_FOUND`

### 4.3 `changeCampusSiteActive`

**Request**：`id`, `activeFlag`  
**编排**：`changeActive`

### 4.4 `listActiveCampusSites` / `resolveCampusByCode`

供 contracts Reader 与本 AS 复用；无页面也可被 Peis 经 Reader 调用（见 [08-Contracts](../08-Contracts与外部依赖.md)）。

---

## 5. 异常码

| 码 | 场景 |
|----|------|
| `CAMPUS_CODE_DUPLICATE` | 编码冲突 |
| `CAMPUS_NOT_FOUND` | 不存在 |
| `CAMPUS_CODE_INVALID` | 格式非法 |

---

## 9. 关联

- SDD：[AS-CMP-ORG-CMP-001-SDD](../AppService-SDD/AS-CMP-ORG-CMP-001-校区管理-SDD.md)
