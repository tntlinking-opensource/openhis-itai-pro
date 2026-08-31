import type { DispenseInventoryPickOption } from '../components/types';

import type { MedicationDispenseInventoryDetailDto } from '#/api/outpatient-pharmacy-dispense/outpatient-pharmacy-dispense.types';

import {
  formatDispenseRemainingStockText,
  parseInventoryRemainingMinQty,
  type DispenseRemainingStockUnitContext,
} from './format-dispense-remaining-stock';
import { decodeInventoryPick, encodeInventoryPick } from './inventory-pick-codec';

/**
 * 仅保留发药单已锚定库房下的批号候选（禁止换发放药房）。
 *
 * @param inventoryList 接口候选库存明细
 * @param lockedLocationId 发放行 dispenseLocationId
 */
export function filterInventoryByLockedPharmacy(
  inventoryList: MedicationDispenseInventoryDetailDto[] | null | undefined,
  lockedLocationId: null | string | undefined,
): MedicationDispenseInventoryDetailDto[] {
  if (!Array.isArray(inventoryList) || inventoryList.length === 0) return [];
  const loc = String(lockedLocationId ?? '').trim();
  if (!loc) return [];
  // 只保留与发放行库房主键一致的批次
  return inventoryList.filter((inv) => String(inv.pharmacyOrganizationId ?? '').trim() === loc);
}

/**
 * 单条库存候选 → 剩余数量展示文案（最小单位换算）。
 *
 * @param inv 库房×批号库存行
 * @param unitCtx 发放行单位上下文
 */
export function formatInventoryLotRemainingText(
  inv: MedicationDispenseInventoryDetailDto,
  unitCtx: DispenseRemainingStockUnitContext,
): string {
  const minQty = parseInventoryRemainingMinQty(inv);
  return formatDispenseRemainingStockText(minQty, unitCtx);
}

/**
 * 批号下拉选项文案：批号 + 剩余（药房已锁定，不再展示库房名）。
 *
 * @param inv 库存候选
 * @param unitCtx 单位上下文
 */
export function formatLotPickOptionLabel(
  inv: MedicationDispenseInventoryDetailDto,
  unitCtx: DispenseRemainingStockUnitContext,
): string {
  const lot = inv.lotNumber?.trim() || '未指定批号';
  const remaining = formatInventoryLotRemainingText(inv, unitCtx);
  if (!remaining || remaining === '—') {
    return `批号 ${lot}`;
  }
  return `批号 ${lot}（剩余 ${remaining}）`;
}

/**
 * 锚定库房下批号选项（value 仍编码库房+批号，提交时库房以发放行为准）。
 *
 * @param inventoryList 已按库房过滤的候选
 * @param lockedLocationId 发放库房
 * @param unitCtx 单位上下文
 */
export function buildLockedPharmacyLotPickOptions(
  inventoryList: MedicationDispenseInventoryDetailDto[],
  lockedLocationId: string,
  unitCtx: DispenseRemainingStockUnitContext,
): DispenseInventoryPickOption[] {
  const loc = lockedLocationId.trim();
  if (!loc || inventoryList.length === 0) return [];
  // 逐批生成可选批号
  return inventoryList.map((inv) => {
    const remainingStockText = formatInventoryLotRemainingText(inv, unitCtx);
    return {
      value: encodeInventoryPick({
        pharmacyOrganizationId: loc,
        lotNumber: inv.lotNumber,
      }),
      label: formatLotPickOptionLabel(inv, unitCtx),
      remainingStockText,
      lotNumber: inv.lotNumber?.trim() || undefined,
    };
  });
}

/**
 * 按当前选中项解析剩余库存展示；无选中时回落行上默认文案。
 *
 * @param options 批号选项
 * @param pickValue 当前选中编码
 * @param fallback 行默认剩余文案
 */
export function resolveRemainingStockTextByPick(
  options: DispenseInventoryPickOption[] | undefined,
  pickValue: string | undefined,
  fallback: string | undefined,
): string {
  if (pickValue && options?.length) {
    const hit = options.find((o) => o.value === pickValue);
    if (hit?.remainingStockText?.trim()) {
      return hit.remainingStockText;
    }
  }
  return fallback?.trim() || '—';
}

/**
 * 按选中项解析批号展示；无选中时回落行上 batchNo。
 *
 * @param options 批号选项
 * @param pickValue 当前选中编码
 * @param fallbackBatchNo 行默认批号
 */
export function resolveBatchNoByPick(
  options: DispenseInventoryPickOption[] | undefined,
  pickValue: string | undefined,
  fallbackBatchNo: string | undefined,
): string {
  if (pickValue && options?.length) {
    const hit = options.find((o) => o.value === pickValue);
    if (hit?.lotNumber?.trim()) {
      return hit.lotNumber.trim();
    }
    try {
      const decoded = decodeInventoryPick(pickValue);
      if (decoded.lotNumber?.trim()) {
        return decoded.lotNumber.trim();
      }
    } catch {
      /* 非法编码回落 */
    }
  }
  const fb = (fallbackBatchNo ?? '').trim();
  return fb || '—';
}
