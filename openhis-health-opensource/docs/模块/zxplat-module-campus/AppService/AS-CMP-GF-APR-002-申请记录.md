# 申请记录 · 应用服务设计（AS）

> **编码**：AS-CMP-GF-APR-002 · **版本**：v1.1 · **日期**：2026-07-19  
> **类名**：`CampusGfRequestRecordAppService` · **路由**：`/campus/gf/record`  
> **现状**：✅ nenu-vue-his `/gf/record`  
> **审批**：[AS-CMP-GF-APR-001](./AS-CMP-GF-APR-001-比例审批.md)

---

## 1. 边界

发起与查询比例调整申请；**必须选择收费模式**（自费公费 / 医保后公费）。

---

## 2. 接口

| IF | 方法 |
|----|------|
| IF-CMP-RATIO-03 | `createAdjustRequest` |
| IF-CMP-RATIO-06 | `pageRequests` / `getRequestDetail` |

### `createAdjustRequest`

**Request**

| 字段 | 必填 | 说明 |
|------|:--:|------|
| chargeModeEnum | **是** | `SELF_PAY_GF` \| `AFTER_YB_GF` |
| itemCode | 是 | 项目/医保编码 |
| newRatio | 是 | 0～1 |
| oldRatio | 否 | 不传则读现行同模式比例 |
| effectiveFrom / effectiveTo | 否 | P1 时效 |
| reason | 否 | |
| externalRef | 否 | 业务单号 |

**编排**：校验模式 → 取旧比例 → 落 `cmp_gf_ratio_request`（PENDING）→ **待办可见**（首批不启 Flowable，ADR D8）。

也可由 [OPEN-001](./AS-CMP-OPEN-001-OpenAPI.md) 代发（同样必填模式）。

### `pageRequests`

筛选项：`chargeModeEnum?`、状态、时间、itemCode；列表展示模式标签。

---

## 5. 异常

`GF_CHARGE_MODE_REQUIRED` / `GF_CHARGE_MODE_INVALID` / `GF_RATIO_OUT_OF_RANGE` / `ITEM_CODE_REQUIRED`
