# 目录比例 · 应用服务设计（AS）

> **编码**：AS-CMP-GF-RATIO-001 · **版本**：v1.1 · **日期**：2026-07-19  
> **类名**：`CampusGfRatioCatalogAppService` · **路由**：`/campus/gf/ratio-catalog`  
> **HTTP**：`/api/app/campusGfRatioCatalog`  
> **成稿编号**：DM-CMP-GF-002

---

## 1. 边界

目录级现行比例维护；**按收费模式分存**（`SELF_PAY_GF` / `AFTER_YB_GF`）。

---

## 2. 接口

| IF | 方法 |
|----|------|
| IF-CMP-RATIO-01 | `pageCatalogRatios` / `saveCatalogRatio` / `disableCatalogRatio` |

### 字段

| 字段 | 必填 | 说明 |
|------|:--:|------|
| chargeModeEnum | **是** | `SELF_PAY_GF` \| `AFTER_YB_GF` |
| catalogCode | 是 | |
| ratio | 是 | 0～1 |
| effectiveFrom / effectiveTo | 否 | |

`pageCatalogRatios` 筛选项含 `chargeModeEnum?`（空=全部）；列表展示模式标签。

UI：Tab「自费公费 | 医保后公费」或筛选项。
