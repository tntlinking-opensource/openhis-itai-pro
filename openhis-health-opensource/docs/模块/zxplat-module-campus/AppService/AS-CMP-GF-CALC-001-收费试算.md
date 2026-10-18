# 公费收费试算 · 应用服务设计（AS）

> **编码**：AS-CMP-GF-CALC-001 · **版本**：v1.2 · **日期**：2026-07-19  
> **类名**：`CampusGfChargeCalcAppService` · **无页面**  
> **HTTP**：`/api/app/campusGfChargeCalc`  
> **Contracts**：`CampusChargeCalcReader`（INT-03）  
> **遗留**：`ClinicChargeGfZf` / `ClinicChargeGfYb`  
> **ADR**：[ADR-CMP-001](../ADR-CMP-001-成码前决策.md) D5/D9/D12  
> **模式定义**：[AS-CMP-GF-APR-001](./AS-CMP-GF-APR-001-比例审批.md) §1.1

---

## 1. 边界

| 在内 | 公费拆分试算/预览；比例链解析（项目→目录→甲乙丙，**同模式**）；资格严校验 |
| 在外 | 真实收银落账（health 门诊收费 AS，**首批仅 TODO**）；医保结算（CHS） |

---

## 2. 接口

| IF | 方法 |
|----|------|
| IF-CMP-CHG-07 | `previewPublicExpenseSplit` |
| IF-CMP-CHG-01 | preview 内：`SELF_PAY_GF` |
| IF-CMP-CHG-02 | preview 内：`AFTER_YB_GF` |
| IF-CMP-CHG-03 | 取比例链（随 mode） |

### `previewPublicExpenseSplit`

**Request**

| 字段 | 说明 |
|------|------|
| patientId | |
| lines[] | itemCode, catalogCode?, amount, classEnum? |
| mode | **`SELF_PAY_GF`** / **`AFTER_YB_GF`**（与收银员 `gfChargeMode` 同码） |

**Response**

| 字段 | 说明 |
|------|------|
| lines[] | itemCode, chargeModeEnum, ratio, gfAmount, selfAmount |
| totals | gfSum, selfSum |
| eligibility | 嵌套 ARCH-05；未通过则拒绝拆分，带 reasonCode/Message |

**编排**：资格校验（严）→ CFG-01 → 按 BR-CMP-GF-02 取 **同 mode** ratio → 计算。

---

## 3. health 挂接（目标态 · 首批 TODO）

对齐 ADR D5/D12：

1. 预结算请求可选 `gfChargeMode`；**空则不拆公费**  
2. 有值 → `CampusChargeCalcReader#previewSplit`  
3. 支付明细两行：`PUBLIC_EXPENSE_ACCOUNT` + `PERSONAL_CASH_ACCOUNT`  
4. 挂号：Campus 启用且有学生档案 → 幂等开公费户；教职工收费时再处理  

本迭代：落 Reader 接口 + 注释 TODO，**不改**草稿金额。

---

## 5. 异常

`GF_NOT_ELIGIBLE` / `GF_SWITCH_OFF` / `GF_CHARGE_MODE_INVALID` / `GF_LINE_INVALID`  
（资格失败优先返回 ARCH-06 的具体 reasonCode。）
