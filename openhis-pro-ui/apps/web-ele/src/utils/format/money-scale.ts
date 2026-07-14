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
 * 拆零最小单位数量 + 每包装单位价 → 行金额（BR-INV-DTL-007，6 位）。
 * 库房单据侧栏/列表在仅有拆零数量与包装价时使用。
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
  const perSplit = price.div(ratio).toDecimalPlaces(MONEY_CALC_DP, Decimal.ROUND_HALF_UP);
  return wholePackages
    .mul(price)
    .add(remainder.mul(perSplit))
    .toDecimalPlaces(MONEY_CALC_DP, Decimal.ROUND_HALF_UP)
    .toNumber();
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

/** 库存/单据行金额展示（6 位计算后 format 2 位）。同计量带数量×单价。 */
export function formatInventoryLineTotal(quantity: MoneyInput, unitPrice: MoneyInput): string {
  const qty = Number(quantity);
  const unit = Number(unitPrice);
  if (!Number.isFinite(qty) || !Number.isFinite(unit)) {
    return '';
  }
  return formatMoneyDisplay(calcMoneyLineTotal(unit, qty));
}

/** 拆零数量 + 包装价行金额展示（6 位计算后 format 2 位）。 */
export function formatWarehouseSplitLineTotal(
  splitQuantity: MoneyInput,
  packageUnitRatio: MoneyInput,
  packageUnitPrice: MoneyInput,
): string {
  return formatMoneyDisplay(
    calcWarehouseSplitLineTotal(splitQuantity, packageUnitRatio, packageUnitPrice),
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
