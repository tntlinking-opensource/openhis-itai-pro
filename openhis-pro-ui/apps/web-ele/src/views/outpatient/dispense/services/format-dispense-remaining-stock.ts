import type { MedicationDispenseInventoryDetailDto } from '#/api/outpatient-pharmacy-dispense/outpatient-pharmacy-dispense.types';

import { decimalAdd, decimalMul, decimalSub } from '#/utils/format/decimal-util';

/** 库存最小单位剩余量解析入参（优先 remainingQuantity） */
export type DispenseInventoryRemainingSource = Pick<
  MedicationDispenseInventoryDetailDto,
  'availableQuantity' | 'remainingQuantity'
>;

/** 发药行单位上下文：拆零量 → 满包+余量展示用 */
export interface DispenseRemainingStockUnitContext {
  doseUnitCode?: null | string;
  doseUnitText?: null | string;
  packageUnitCode?: null | string;
  packageUnitRatio?: null | number | string;
  /**
   * 包装↔拆零比例展示（服务端拼装，如 1件/120瓶）。
   * 发放单位仅为拆零/包装之一时，用于补全另一侧单位名，避免「5」+「10瓶」拼成「510瓶」。
   */
  packageSplitRatioText?: null | string;
  splitUnitCode?: null | string;
  unitCode?: null | string;
  unitText?: null | string;
}

/** 解析单行库存剩余：优先 remainingQuantity，回落 availableQuantity（均为最小单位） */
export function parseInventoryRemainingMinQty(
  inv: DispenseInventoryRemainingSource | null | undefined,
): number | undefined {
  if (!inv) return undefined;
  const raw = inv.remainingQuantity ?? inv.availableQuantity;
  if (raw === null || raw === undefined || String(raw).trim() === '') return undefined;
  const n = Number(raw);
  if (!Number.isFinite(n)) return undefined;
  return n;
}

/** 汇总候选库存最小单位剩余量 */
export function sumInventoryRemainingMinQty(
  inventoryList: DispenseInventoryRemainingSource[] | null | undefined,
): number | undefined {
  if (!Array.isArray(inventoryList) || inventoryList.length === 0) return undefined;
  let sum = 0;
  let has = false;
  // 逐批累加最小单位剩余，供包装/拆零展示
  for (const inv of inventoryList) {
    const n = parseInventoryRemainingMinQty(inv);
    if (n === undefined) continue;
    sum = decimalAdd(sum, n, 8);
    has = true;
  }
  return has ? sum : undefined;
}

/**
 * 从库存口径比例文案解析包装/拆零单位名（如 1件/120瓶 → 件、瓶）。
 * 同单位无斜杠形如 1支 时两侧同名。
 */
function parseLabelsFromPackageSplitRatioText(raw: null | string | undefined): {
  packageUnitLabel: string;
  splitUnitLabel: string;
} {
  const text = String(raw ?? '').trim();
  if (!text) {
    return { packageUnitLabel: '', splitUnitLabel: '' };
  }
  // 1件/120瓶：包装名 + 倍数 + 拆零名
  const slashMatch = /^1(.+?)\/(\d+(?:\.\d+)?)(.+)$/u.exec(text);
  if (slashMatch) {
    return {
      packageUnitLabel: String(slashMatch[1] ?? '').trim(),
      splitUnitLabel: String(slashMatch[3] ?? '').trim(),
    };
  }
  // 1支：同单位无换算斜杠
  const sameMatch = /^1(.+)$/u.exec(text);
  if (sameMatch) {
    const label = String(sameMatch[1] ?? '').trim();
    return { packageUnitLabel: label, splitUnitLabel: label };
  }
  return { packageUnitLabel: '', splitUnitLabel: '' };
}

/** 从发放行单位码推断包装/拆零展示名 */
function resolvePackageSplitUnitLabels(ctx: DispenseRemainingStockUnitContext): {
  packageUnitLabel: string;
  splitUnitLabel: string;
} {
  const unitCode = String(ctx.unitCode ?? '').trim();
  const unitText = String(ctx.unitText ?? '').trim();
  const packageUnitCode = String(ctx.packageUnitCode ?? '').trim();
  const splitUnitCode = String(ctx.splitUnitCode ?? '').trim();
  const doseUnitCode = String(ctx.doseUnitCode ?? '').trim();
  const doseUnitText = String(ctx.doseUnitText ?? '').trim();

  let packageUnitLabel = '';
  let splitUnitLabel = '';
  // 发放单位即包装/拆零时，直接复用 unitText
  if (packageUnitCode && unitCode === packageUnitCode) {
    packageUnitLabel = unitText;
  }
  if (splitUnitCode && unitCode === splitUnitCode) {
    splitUnitLabel = unitText;
  }
  // 剂量单位与拆零同码时补拆零展示名
  if (!splitUnitLabel && splitUnitCode && doseUnitCode === splitUnitCode) {
    splitUnitLabel = doseUnitText;
  }
  // 仅一侧有 unitText 时（常见：发放按瓶），从 1件/120瓶 补另一侧，避免 5+10瓶→510瓶
  if (!packageUnitLabel || !splitUnitLabel) {
    const fromRatio = parseLabelsFromPackageSplitRatioText(ctx.packageSplitRatioText);
    if (!packageUnitLabel) {
      packageUnitLabel = fromRatio.packageUnitLabel;
    }
    if (!splitUnitLabel) {
      splitUnitLabel = fromRatio.splitUnitLabel;
    }
  }
  // 仍缺时回落单位码（与后端字典缺省同族），禁止裸数字拼接
  if (!packageUnitLabel && packageUnitCode) {
    packageUnitLabel = packageUnitCode;
  }
  if (!splitUnitLabel && splitUnitCode) {
    splitUnitLabel = splitUnitCode;
  }
  return { packageUnitLabel, splitUnitLabel };
}

/**
 * 最小单位剩余量 → 满包整数 + 拆零余量文案（如 4盒6片）。
 * 对齐后端 MedicationPackageSplitQuantityDisplayAssembler；无有效数量时返回「—」。
 */
export function formatDispenseRemainingStockText(
  minUnitQty: null | number | string | undefined,
  ctx: DispenseRemainingStockUnitContext,
): string {
  if (minUnitQty === null || minUnitQty === undefined || String(minUnitQty).trim() === '') {
    return '—';
  }
  const q = Number(minUnitQty);
  if (!Number.isFinite(q)) return '—';

  const { packageUnitLabel, splitUnitLabel } = resolvePackageSplitUnitLabels(ctx);
  const ratio = Number(ctx.packageUnitRatio);

  // 无有效包装倍数：整数量 + 拆零/发放单位
  if (!Number.isFinite(ratio) || ratio <= 0) {
    const unitPart = splitUnitLabel || packageUnitLabel || String(ctx.unitText ?? '').trim();
    const qtyPart = String(Math.trunc(q));
    return unitPart ? `${qtyPart}${unitPart}` : qtyPart;
  }

  // 满包向下取整，余量用拆零单位拼接（禁止小数盒）
  const packageQty = Math.floor(q / ratio);
  const usedInPackages = decimalMul(packageQty, ratio, 8);
  const remainderRaw = decimalSub(q, usedInPackages, 8);
  const remainder = Number(remainderRaw);
  const parts: string[] = [];

  if (packageQty > 0) {
    parts.push(packageUnitLabel ? `${packageQty}${packageUnitLabel}` : String(packageQty));
  } else if (!(Number.isFinite(remainder) && remainder > 1e-9)) {
    parts.push(packageUnitLabel ? `0${packageUnitLabel}` : '0');
  }

  if (Number.isFinite(remainder) && remainder > 1e-9) {
    const remText = String(Math.round(remainder));
    parts.push(splitUnitLabel ? `${remText}${splitUnitLabel}` : remText);
  }

  return parts.join('') || '—';
}

/** 候选库存列表 → 汇总换算后的剩余库存展示文案 */
export function formatDispenseRemainingStockFromInventoryList(
  inventoryList: DispenseInventoryRemainingSource[] | null | undefined,
  ctx: DispenseRemainingStockUnitContext,
): string {
  const sum = sumInventoryRemainingMinQty(inventoryList);
  if (sum === undefined) return '—';
  return formatDispenseRemainingStockText(sum, ctx);
}
