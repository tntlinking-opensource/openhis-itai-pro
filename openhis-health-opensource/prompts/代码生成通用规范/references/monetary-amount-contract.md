# 金钱金额契约（BR-MON-001 / BR-MON-PKG-001）

> 与代码真源对齐：`MonetaryAmountSupport`、`ChargeItemAmountScaleConstants`、`PackageUnitPriceScaleGate`。  
> 本文件为成码契约摘要；领域细则仍以 DM-ADM-010 / DM-MED-001 §9.3 为准。

## 1. 刻度总表

| 语义 | 刻度 | 舍入 | 入口示例 |
|------|------|------|----------|
| 售价单价 / 售价行额 / 支付·发票合计 | **2 位** | HALF_UP | 目录 SALE/LIMIT、批号 `LOT_NUMBER_PRICE`、收费 `unit_price`/`total_price`、折后应收 |
| 进价单价（西药/中成药/外购/耗材） | **2 位** | HALF_UP | 目录 PURCHASE、批号 `LOT_NUMBER_COST` |
| 进价单价（中药饮片 `category=4`） | **3 位** | 展示/导出 DOWN；维护闸 ≤3 | 目录采购参考价、批号进价 |
| 药品进价合计（日结/报表） | **3 位** | HALF_UP | `sumThenPurchaseSettle` |
| 行级中间量 / 库 `numeric(19,6)` | **6 位** | HALF_UP | `scaleCalc`；**不得**作为收费售价落库终态 |

## 2. 维护闸（写价拒绝超位，不拒绝除不尽）

- **售价**：录入/落库前有效小数位 ≤2（`PackageUnitPriceScaleGate` / `normalizeDefinitionPriceValue`）。
- **进价**：非中药 ≤2；中药 ≤3。
- **禁止**：因「包装售价÷包装比不能整除到两位」拒绝目录、进货、期初或结算。
- **中药进货/期初核心**：`LOT_NUMBER_COST` 可三位；`LOT_NUMBER_PRICE` **禁止**写三位——占位先 HALF_UP 到 2 位，中草药加价切面再覆盖为进价×1.25（两位）。

## 3. 收费 / 结算消化（除不尽）

- 医生站槽价、开立写收费：售价单价 `scaleSaleUnitPrice`（2 位 HALF_UP）。
- 行额优先包装锚：**满包×包装价 + 零头（包装价×余量/ratio 单步）**（BR-MON-PKG-001）；禁止「拆零单价×全量」主导路径。
- 支付/医保勾稽：结算合计 2 位；长尾不得进入收费事实。

## 4. 关键类

| 类 | 职责 |
|----|------|
| `MonetaryAmountSupport` | 刻度/校验/行额/合计 |
| `PackageUnitPriceScaleGate` | 目录/进货/期初维护位数闸 |
| `OrderChargePricingAssembler` | 开立写收费售价 2 位 |
| `ChargeItemDefinitionDomainService#toEntity` | 定价行落库归一 |
