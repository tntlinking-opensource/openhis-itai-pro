# DM-CMP-GF-002 GfRatio · DomainModel-SDD

> **包根**：`com.zxplat.plugin.campus.domain.gf`  
> 子包：`gfswitch` / `gffreepercent` / `gfchargeratio` / `gfratiorequest`  
> **版本**：v1.1

---

## 1. 成码顺序

1. `GfSwitch` + `GfFreePercent`（CFG；`chargeModeEnum` 可空=共用）
2. `GfChargeRatio` / `Item`（RATIO；**`chargeModeEnum` 必填**）
3. `GfRatioRequest`（APR；**`chargeModeEnum` 必填**；首批状态机，不接 Flowable）

## 2. 枚举

`GfChargeModeEnum`：`SELF_PAY_GF` | `AFTER_YB_GF`（与 AS/CHG 同码）。

## 3. ratio 类型

统一 `BigDecimal`，库 `numeric(10,6)`，语义 **0～1**。

## 4. 唯一键

- 甲乙丙：`(tenantId, classEnum, chargeModeEnum)` 未删唯一  
- 目录：`(tenantId, catalogCode, chargeModeEnum)` 未删唯一  
- 项目：`(tenantId, itemCode, chargeModeEnum)` 未删唯一  
- 申请：`requestNo` 租户内唯一；列表按 mode+status 索引  

## 5. 修订

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.0 | 2026-07-19 | PASS |
| v1.1 | 2026-07-19 | 双模式字段与唯一键 |
| v1.2 | 2026-07-19 | ADR：甲乙丙必填 mode；无 Flowable |
