# 项目比例 · 应用服务设计（AS）

> **编码**：AS-CMP-GF-RATIO-002 · **版本**：v1.1 · **日期**：2026-07-19  
> **类名**：`CampusGfRatioItemAppService` · **路由**：`/campus/gf/ratio-item`  
> **HTTP**：`/api/app/campusGfRatioItem`  
> **成稿编号**：DM-CMP-GF-002

---

## 1. 边界

项目级现行比例维护与审批回写；**按收费模式分存**。

---

## 2. 接口

| IF | 方法 |
|----|------|
| IF-CMP-RATIO-02 | `pageItemRatios` / `saveItemRatio` / `disableItemRatio` |
| IF-CMP-RATIO-05 | `applyApprovedRatio`（审批通过回写；也可由 APR AS 内调领域） |

### 字段

| 字段 | 必填 | 说明 |
|------|:--:|------|
| chargeModeEnum | **是** | `SELF_PAY_GF` \| `AFTER_YB_GF` |
| itemCode | 是 | 医保/项目编码 |
| ratio | 是 | 0～1 |
| effectiveFrom / effectiveTo | 否 | |

`applyApprovedRatio`：入参含申请单 `chargeModeEnum`，只回写 **同模式** 行（BR-CMP-GF-03）。

UI：同目录比例，按模式 Tab/筛选。
