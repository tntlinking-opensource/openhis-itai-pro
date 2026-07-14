import { calcMoneyLineTotal } from '#/utils/format/money-scale';

/** 数量 × 单价（6 位行金额；入账以后端为准） */
export const calcTotalPrice = (quantity: number, price: number): number =>
  calcMoneyLineTotal(price, quantity);

/**
 * 将小单位数量按拆零比、单位文案格式化展示
 * @param quantity 小单位数量（传入的永远是小单位）
 * @param partPercent 拆零比（1 大单位 = partPercent 小单位）
 * @param unitLabel 大单位文案，如 盒
 * @param minUnitLabel 小单位文案，如 片
 * @param isUnit true 展示大单位（如 1盒），false 展示小单位（如 12片）
 */
export function formatQuantityByUnit(
  quantity: number,
  partPercent: number,
  unitLabel: string,
  minUnitLabel: string,
  isUnit = false,
): string {
  const part = Number(partPercent) || 1;
  const smallUnitQuantity = Number(quantity) || 0;
  if (smallUnitQuantity === 0) {
    return isUnit ? `0${unitLabel || ''}` : `0${minUnitLabel || ''}`;
  }
  const abs = Math.abs(smallUnitQuantity);
  const unit = Math.floor(abs / part);
  const sign = smallUnitQuantity < 0 ? -1 : 1;
  if (isUnit) return `${sign * unit}${unitLabel || ''}`;
  return `${smallUnitQuantity}${minUnitLabel || ''}`;
}
