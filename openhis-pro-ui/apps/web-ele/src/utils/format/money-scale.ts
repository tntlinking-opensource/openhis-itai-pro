/**
 * 全局金钱刻度契约（与后端 MonetaryAmountSupport / ChargeItemAmountScaleConstants 对齐）。
 *
 * - 计算/行级：6 位 HALF_UP（单价、行金额、中间加总）
 * - 结算/展示总价：先 6 位加总，再 2 位 HALF_UP（支付、发票、页脚合计）
 * - 前端禁止用展示用 2 位单价反推行金额或提交支付；行 totalPrice 仅草稿预览，落库权威在后端
 */
import Decimal from 'decimal.js';

/** 与库 numeric(19,6) / CHARGE_MONETARY_SCALE 一致 */
export const MONEY_CALC_DP = 6;

/** 结算/支付/发票总价刻度 */
export const MONEY_SETTLE_DP = 2;

/** UI 展示刻度（与 MONEY_SETTLE_DP 同为 2；仅 format） */
export const MONEY_DISPLAY_DP = 2;

/**
 * 中药（饮片/中草药）**进价** UI 展示/录入刻度：3 位，禁止压成 2 位。
 * 售价（批号售价 / 销售参考价 / 收费）仍用 {@link MONEY_DISPLAY_DP}。
 */
export const MONEY_TCM_UNIT_PRICE_DP = 3;

/** 与医嘱域 / 库存单据一致：medication_category_code 中草药码 */
export const HERBAL_MEDICATION_CATEGORY_CODE = '4';

Decimal.set({
  precision: 28,
  rounding: Decimal.ROUND_HALF_UP,
});

type MoneyInput = Decimal | null | number | string | undefined;

const toMoneyDecimal = (value: MoneyInput): Decimal => {
  if (value instanceof Decimal) {
    return value;
  }
  if (value === null || value === undefined || value === '') {
    return new Decimal(0);
  }
  return new Decimal(value as number | string);
};

/**
 * 中药进价小数位：分类码为中草药 → 3，否则 2。
 * 仅用于**进价**（药典采购参考价、批号进价、库房单价列）；售价勿用。
 *
 * @param categoryCode medication_category_code（中草药为 {@link HERBAL_MEDICATION_CATEGORY_CODE}）
 */
export function resolveMedicationCatalogPurchasePriceDisplayDp(categoryCode: unknown): number {
  const code = String(categoryCode ?? '').trim();
  if (code === HERBAL_MEDICATION_CATEGORY_CODE) {
    return MONEY_TCM_UNIT_PRICE_DP;
  }
  return MONEY_DISPLAY_DP;
}

/**
 * @deprecated 语义已收窄为「仅进价」；请改用 {@link resolveMedicationCatalogPurchasePriceDisplayDp}
 */
export function resolveMedicationCatalogPriceDisplayDp(categoryCode: unknown): number {
  return resolveMedicationCatalogPurchasePriceDisplayDp(categoryCode);
}

/** 计算刻度：6 位 HALF_UP，返回 number */
export function scaleMoneyCalc(value: MoneyInput): number {
  return toMoneyDecimal(value).toDecimalPlaces(MONEY_CALC_DP, Decimal.ROUND_HALF_UP).toNumber();
}

/** 结算刻度：2 位 HALF_UP，返回 number */
export function scaleMoneySettle(value: MoneyInput): number {
  return toMoneyDecimal(value).toDecimalPlaces(MONEY_SETTLE_DP, Decimal.ROUND_HALF_UP).toNumber();
}

/**
 * 行金额 = 单价 × 数量（6 位）。
 * 仅用于开立前草稿预览；入账权威须以后端 ChargeItem 为准。
 */
export function calcMoneyLineTotal(unitPrice: MoneyInput, quantity: MoneyInput): number {
  return toMoneyDecimal(unitPrice)
    .mul(toMoneyDecimal(quantity))
    .toDecimalPlaces(MONEY_CALC_DP, Decimal.ROUND_HALF_UP)
    .toNumber();
}

/**
 * 拆零最小单位数量 + 每包装单位价 → 行金额（BR-INV-DTL-007 / BR-MON-PKG-001，6 位）。
 * 满包 = 整包数×包装价；零头 = 包装价×余量/ratio（单步）。禁止先算拆零单价再乘数量。
 * 库房单据侧栏/列表在仅有拆零数量与包装价时使用（草稿预览；权威以后端为准）。
 */
export function calcWarehouseSplitLineTotal(
  splitQuantity: MoneyInput,
  packageUnitRatio: MoneyInput,
  packageUnitPrice: MoneyInput,
): number {
  const qty = toMoneyDecimal(splitQuantity);
  const ratioRaw = toMoneyDecimal(packageUnitRatio);
  const price = toMoneyDecimal(packageUnitPrice).toDecimalPlaces(
    MONEY_CALC_DP,
    Decimal.ROUND_HALF_UP,
  );
  let ratio = ratioRaw;
  if (!ratio.isFinite() || ratio.lte(0)) {
    ratio = new Decimal(1);
  }
  const wholePackages = qty.div(ratio).floor();
  const remainder = qty.minus(wholePackages.mul(ratio));
  // 满包：整包数 × 包装价
  let total = wholePackages.mul(price);
  // 零头：包装价 × 余量 / ratio（单步线性，不先派生拆零单价）
  if (!remainder.isZero()) {
    total = total.add(
      price.mul(remainder).div(ratio).toDecimalPlaces(MONEY_CALC_DP, Decimal.ROUND_HALF_UP),
    );
  }
  return total.toDecimalPlaces(MONEY_CALC_DP, Decimal.ROUND_HALF_UP).toNumber();
}

/** 多个行级金额加总后 settle 到 2 位（支付/页脚合计） */
export function sumMoneyThenSettle(values: MoneyInput[]): number {
  let sum = new Decimal(0);
  for (const value of values) {
    sum = sum.add(toMoneyDecimal(value));
  }
  return sum.toDecimalPlaces(MONEY_SETTLE_DP, Decimal.ROUND_HALF_UP).toNumber();
}

/** 多个行级金额加总后保持 6 位（块内小计等非支付场景） */
export function sumMoneyThenCalc(values: MoneyInput[]): number {
  let sum = new Decimal(0);
  for (const value of values) {
    sum = sum.add(toMoneyDecimal(value));
  }
  return sum.toDecimalPlaces(MONEY_CALC_DP, Decimal.ROUND_HALF_UP).toNumber();
}

/** 格式化为两位小数字符串（纯展示） */
export function formatMoneyDisplay(value: MoneyInput): string {
  const n = scaleMoneySettle(value);
  if (!Number.isFinite(n)) {
    return '0.00';
  }
  return n.toFixed(MONEY_DISPLAY_DP);
}

/**
 * 按指定小数位 format 单价（纯展示；默认 2 位）。
 * 中药**进价**传 {@link MONEY_TCM_UNIT_PRICE_DP}；售价勿传 3。
 */
export function formatMoneyUnitPriceDisplay(
  value: MoneyInput,
  displayDp: number = MONEY_DISPLAY_DP,
): string {
  if (value === null || value === undefined || value === '') {
    return '';
  }
  const n = Number(value);
  if (!Number.isFinite(n)) {
    return String(value).trim();
  }
  const dp =
    Number.isFinite(displayDp) && displayDp >= 0 ? Math.floor(displayDp) : MONEY_DISPLAY_DP;
  return toMoneyDecimal(n).toDecimalPlaces(dp, Decimal.ROUND_HALF_UP).toFixed(dp);
}

/**
 * 进价侧金额展示（单价 / 行额 / 合计 / 盈亏共用；中药全页面进价三位）。
 * - 有 categoryCode：中草药固定 3 位，其余 2 位
 * - 无 categoryCode：最多 3、至少 2（第三位尾零去掉），避免中药第三位被 `formatMoneyDisplay` 压没
 *
 * 售价 / 支付结算请继续用 {@link formatMoneyDisplay}。
 */
export function formatPurchaseMoneyDisplay(value: MoneyInput, categoryCode?: unknown): string {
  if (value === null || value === undefined || value === '') {
    return '';
  }
  const n = Number(value);
  if (!Number.isFinite(n)) {
    return String(value).trim();
  }
  const code = String(categoryCode ?? '').trim();
  if (code) {
    return formatMoneyUnitPriceDisplay(value, resolveMedicationCatalogPurchasePriceDisplayDp(code));
  }
  // 无分类：先 3 位 HALF_UP，第三位为 0 则回落两位（西药仍像 10.50）
  const rounded = toMoneyDecimal(n).toDecimalPlaces(MONEY_TCM_UNIT_PRICE_DP, Decimal.ROUND_HALF_UP);
  const fixed3 = rounded.toFixed(MONEY_TCM_UNIT_PRICE_DP);
  if (fixed3.endsWith('0')) {
    return rounded.toFixed(MONEY_DISPLAY_DP);
  }
  return fixed3;
}

/**
 * @deprecated 请改用 {@link formatPurchaseMoneyDisplay}（进价单价与金额同一口径）
 */
export function formatPurchaseUnitPriceDisplay(value: MoneyInput, categoryCode?: unknown): string {
  return formatPurchaseMoneyDisplay(value, categoryCode);
}

/** 进价侧金额 +「元」 */
export function formatPurchaseMoneyYuan(value: MoneyInput, categoryCode?: unknown): string {
  const text = formatPurchaseMoneyDisplay(value, categoryCode);
  return text ? `${text}元` : '';
}

/** 格式化为带「元」的展示文案 */
export function formatMoneyYuan(value: MoneyInput): string {
  return `${formatMoneyDisplay(value)}元`;
}

/** 格式化为带 ¥ 前缀的展示文案 */
export function formatMoneyYuanWithSymbol(value: MoneyInput): string {
  return `¥${formatMoneyDisplay(value)}`;
}

/** 在结算刻度下比较两金额是否相等 */
export function moneyEqualsAtSettle(left: MoneyInput, right: MoneyInput): boolean {
  return scaleMoneySettle(left) === scaleMoneySettle(right);
}

/** 结算刻度下两金额之差（发票微调差额等） */
export function moneyDiffAtSettle(left: MoneyInput, right: MoneyInput): number {
  return toMoneyDecimal(left)
    .sub(toMoneyDecimal(right))
    .toDecimalPlaces(MONEY_SETTLE_DP, Decimal.ROUND_HALF_UP)
    .toNumber();
}

/** 库存/单据行金额展示（6 位计算后按进价侧展示刻度）。同计量带数量×单价。 */
export function formatInventoryLineTotal(
  quantity: MoneyInput,
  unitPrice: MoneyInput,
  categoryCode?: unknown,
): string {
  const qty = Number(quantity);
  const unit = Number(unitPrice);
  if (!Number.isFinite(qty) || !Number.isFinite(unit)) {
    return '';
  }
  return formatPurchaseMoneyDisplay(calcMoneyLineTotal(unit, qty), categoryCode);
}

/** 拆零数量 + 包装价行金额展示（6 位计算后按进价侧展示刻度）。 */
export function formatWarehouseSplitLineTotal(
  splitQuantity: MoneyInput,
  packageUnitRatio: MoneyInput,
  packageUnitPrice: MoneyInput,
  categoryCode?: unknown,
): string {
  return formatPurchaseMoneyDisplay(
    calcWarehouseSplitLineTotal(splitQuantity, packageUnitRatio, packageUnitPrice),
    categoryCode,
  );
}

/** 库存行金额数值（6 位，写回行模型用）。同计量带数量×单价。 */
export function inventoryLineTotalNumber(quantity: MoneyInput, unitPrice: MoneyInput): number {
  return calcMoneyLineTotal(unitPrice, quantity);
}

/** 拆零数量 + 包装价行金额数值（6 位，写回行模型用）。 */
export function warehouseSplitLineTotalNumber(
  splitQuantity: MoneyInput,
  packageUnitRatio: MoneyInput,
  packageUnitPrice: MoneyInput,
): number {
  return calcWarehouseSplitLineTotal(splitQuantity, packageUnitRatio, packageUnitPrice);
}

export { Decimal, toMoneyDecimal };
