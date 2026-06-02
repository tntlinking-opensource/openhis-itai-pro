import Decimal from 'decimal.js';

// 四舍五入 默认保留4位
Decimal.set({
  precision: 20,
  rounding: Decimal.ROUND_HALF_UP,
});

const DEFAULT_DP = 4;

type DecimalInput = Decimal | null | number | string | undefined;

const toDecimal = (value: DecimalInput): Decimal => {
  if (value instanceof Decimal) return value;
  if (value === null || value === undefined || value === '') return new Decimal(0);
  return new Decimal(value as number | string);
};

const toFixedNumber = (
  value: Decimal,
  dp = DEFAULT_DP,
  rounding: Decimal.Rounding = Decimal.ROUND_HALF_UP,
): number => {
  return value.toDecimalPlaces(dp, rounding).toNumber();
};

export const decimalAdd = (a: DecimalInput, b: DecimalInput, dp = DEFAULT_DP): number => {
  return toFixedNumber(toDecimal(a).add(toDecimal(b)), dp);
};

export const decimalSub = (a: DecimalInput, b: DecimalInput, dp = DEFAULT_DP): number => {
  return toFixedNumber(toDecimal(a).sub(toDecimal(b)), dp);
};

export const decimalMul = (a: DecimalInput, b: DecimalInput, dp = DEFAULT_DP): number => {
  return toFixedNumber(toDecimal(a).mul(toDecimal(b)), dp);
};

export const decimalDiv = (a: DecimalInput, b: DecimalInput, dp = DEFAULT_DP): number => {
  return toFixedNumber(toDecimal(a).div(toDecimal(b)), dp);
};

export const decimalToFixed = (
  value: DecimalInput,
  dp = DEFAULT_DP,
  rounding: Decimal.Rounding = Decimal.ROUND_HALF_UP,
): number => {
  return toFixedNumber(toDecimal(value), dp, rounding);
};

export { Decimal, toDecimal };
