import type { DispensePrescriptionItemRow } from '../components/types';

import { computeRequiredTraceCount } from '#/utils/trace';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';

import { decodeInventoryPick } from './inventory-pick-codec';

export interface DispenseTraceScanInventory {
  locationId: string;
  lotNumber: string;
}

/** 从发药行已选/默认发放库房解析库取追溯码用的 locationId 与批号 */
export function resolveDispenseTraceScanInventory(
  row: DispensePrescriptionItemRow,
  inventoryPickByLineId?: Record<string, string>,
): DispenseTraceScanInventory {
  let pickRaw = inventoryPickByLineId?.[row.id];
  if (!pickRaw && row.inventoryPickOptions?.[0]) {
    pickRaw = row.inventoryPickOptions[0].value;
  }
  if (pickRaw) {
    try {
      const decoded = decodeInventoryPick(pickRaw);
      const lotFromPick = decoded.lotNumber?.trim() ?? '';
      const lotFromRow = row.batchNo === '—' ? '' : String(row.batchNo ?? '').trim();
      return {
        locationId: preserveApiIdForRequest(decoded.pharmacyOrganizationId) ?? '',
        lotNumber: lotFromPick || lotFromRow,
      };
    } catch {
      /* 无效 pick 编码时走行上 dispenseLocationId 兜底 */
    }
  }

  const locationId = preserveApiIdForRequest(row.dispenseLocationId) ?? '';
  const lotNumber = row.batchNo === '—' ? '' : String(row.batchNo ?? '').trim();
  return { locationId, lotNumber };
}

/**
 * 从发药行「数量」展示串解析数量（回落用）。
 * 优先应走 {@link resolveDispenseRequiredTraceCount} 的 quantityValue / 单位换算。
 */
export function parseDispenseLineQuantity(row: DispensePrescriptionItemRow): number {
  const raw = String(row.quantity ?? '').trim();
  if (!raw || raw === '—') return 1;
  const matched = raw.match(/^(\d+(?:\.\d+)?)/);
  if (!matched) return 1;
  const n = Math.ceil(Number(matched[1]));
  return Number.isFinite(n) && n > 0 ? n : 1;
}

export interface ResolveDispenseRequiredTraceCountOpts {
  /**
   * 贴码单位（行上已是主档优先、无主档则药典；再回落包装单位）。
   */
  traceUnitCode?: string;
  /**
   * 贴码比例（1 码单位 = N 拆零；行上主档优先否则药典）。
   */
  traceUnitRatio?: number;
}

/**
 * 门诊发药操作拆零量（盖住判定用；与后端 isRemainingSplitSufficientForDispenseOut 同口径）。
 *
 * @param row 发药行
 * @param opts 主档贴码单位/比例快照（可选）
 * @returns 拆零量；无法换算时 undefined
 */
export function resolveDispenseOperationSplitQty(
  row: DispensePrescriptionItemRow,
  opts?: ResolveDispenseRequiredTraceCountOpts,
): number | undefined {
  const opQty =
    row.quantityValue !== undefined &&
    row.quantityValue !== null &&
    Number.isFinite(Number(row.quantityValue)) &&
    Number(row.quantityValue) > 0
      ? Number(row.quantityValue)
      : parseDispenseLineQuantity(row);

  const opUnit = String(row.unitCode ?? '').trim();
  const packageUnit = String(row.packageUnitCode ?? '').trim();
  const splitUnit = String(row.splitUnitCode ?? '').trim();
  const packageRatio = Number(row.packageUnitRatio);
  const hasPackageRatio = Number.isFinite(packageRatio) && packageRatio > 0;

  let traceUnit = String(opts?.traceUnitCode ?? '').trim();
  if (!traceUnit) {
    traceUnit = String(row.traceUnitCode ?? '').trim();
  }
  const masterRatioRaw =
    opts?.traceUnitRatio !== undefined && opts?.traceUnitRatio !== null
      ? Number(opts.traceUnitRatio)
      : Number(row.traceUnitRatio);
  const hasMasterRatio = Number.isFinite(masterRatioRaw) && masterRatioRaw > 0;

  // 同阶贴码单位：操作量 × 每码拆零（主档比或包装比）
  if (opUnit && traceUnit && opUnit === traceUnit) {
    if (hasMasterRatio) return opQty * masterRatioRaw;
    if (hasPackageRatio && packageUnit && traceUnit === packageUnit) return opQty * packageRatio;
    if (splitUnit && traceUnit === splitUnit) return opQty;
    return opQty;
  }

  // 异构：先得操作拆零
  if (opUnit && splitUnit && opUnit === splitUnit) return opQty;
  if (opUnit && packageUnit && opUnit === packageUnit && hasPackageRatio)
    return opQty * packageRatio;
  if (!opUnit && splitUnit) return opQty;
  if (hasMasterRatio) return opQty;
  return undefined;
}

/**
 * 门诊/住院发药单行绑码硬上限（=弹窗「待扫」真源）：{@code ceil(操作量)}。
 *
 * <p>开 2 支→2、开 1 盒→1。码倍/拆零只用于盖住判定，<strong>不</strong>参与个数。</p>
 *
 * @param row 发药行
 * @param _opts 保留签名兼容；个数不读贴码比例
 * @returns ≥1
 */
export function resolveDispenseMaxTraceCount(
  row: DispensePrescriptionItemRow,
  _opts?: ResolveDispenseRequiredTraceCountOpts,
): number {
  const opQty =
    row.quantityValue !== undefined &&
    row.quantityValue !== null &&
    Number.isFinite(Number(row.quantityValue)) &&
    Number(row.quantityValue) > 0
      ? Number(row.quantityValue)
      : parseDispenseLineQuantity(row);
  return Math.max(1, Math.ceil(opQty));
}

/**
 * 门诊发药「建议应扫」个数（待扫展示 / 半扫软确认；非绑码硬上限）。
 *
 * <p>贴码单位/比例优先取追溯码主表快照；操作量仍用包装/拆零把业务单位换到拆零后，
 * 再 ceil(行拆零 / 主表 ratio)。硬上限见 {@link resolveDispenseMaxTraceCount}。</p>
 */
export function resolveDispenseRequiredTraceCount(
  row: DispensePrescriptionItemRow,
  opts?: ResolveDispenseRequiredTraceCountOpts,
): number {
  const opQty =
    row.quantityValue !== undefined &&
    row.quantityValue !== null &&
    Number.isFinite(Number(row.quantityValue)) &&
    Number(row.quantityValue) > 0
      ? Number(row.quantityValue)
      : parseDispenseLineQuantity(row);

  const opUnit = String(row.unitCode ?? '').trim();
  const packageUnit = String(row.packageUnitCode ?? '').trim();
  const splitUnit = String(row.splitUnitCode ?? '').trim();
  const packageRatio = Number(row.packageUnitRatio);
  const hasPackageRatio = Number.isFinite(packageRatio) && packageRatio > 0;

  // 主档贴码：扫码反查 / opts > 行上主表快照（非目录）
  let traceUnit = String(opts?.traceUnitCode ?? '').trim();
  if (!traceUnit) {
    traceUnit = String(row.traceUnitCode ?? '').trim();
  }
  const masterRatioRaw =
    opts?.traceUnitRatio !== undefined && opts?.traceUnitRatio !== null
      ? Number(opts.traceUnitRatio)
      : Number(row.traceUnitRatio);
  const hasMasterRatio = Number.isFinite(masterRatioRaw) && masterRatioRaw > 0;

  // 有主表比例：与后端同阶/异构口径一致
  if (hasMasterRatio) {
    // 操作单位与主档贴码单位同阶：应扫 = ceil(操作量)
    if (opUnit && traceUnit && opUnit === traceUnit) {
      return (
        computeRequiredTraceCount({
          operationQty: opQty,
          operationUnitCode: opUnit,
          traceUnitCode: traceUnit,
          mode: 'outbound',
        }) ?? Math.max(1, Math.ceil(opQty))
      );
    }

    // 异构：操作量换拆零 ÷ 主表每码拆零
    let opSplit: number | undefined;
    if (opUnit && splitUnit && opUnit === splitUnit) {
      opSplit = opQty;
    } else if (opUnit && packageUnit && opUnit === packageUnit && hasPackageRatio) {
      opSplit = opQty * packageRatio;
    } else if (!opUnit && splitUnit) {
      opSplit = opQty;
    } else if (!packageUnit && !splitUnit) {
      // 缺包装/拆零单位时按展示数量理解拆零
      opSplit = opQty;
    }

    if (opSplit !== undefined) {
      return (
        computeRequiredTraceCount({
          operationQty: opQty,
          operationUnitCode: opUnit || undefined,
          traceUnitCode: traceUnit || undefined,
          operationSplitQty: opSplit,
          perTraceSplitQty: masterRatioRaw,
          mode: 'outbound',
        }) ?? Math.max(1, Math.ceil(opSplit / masterRatioRaw))
      );
    }
  }

  // 无主表快照：回落包装默认（兼容历史）
  if (!traceUnit) {
    traceUnit = packageUnit;
  }
  if (!traceUnit) {
    traceUnit = opUnit;
  }

  if (opUnit && traceUnit && opUnit === traceUnit) {
    return (
      computeRequiredTraceCount({
        operationQty: opQty,
        operationUnitCode: opUnit,
        traceUnitCode: traceUnit,
        mode: 'outbound',
      }) ?? Math.max(1, Math.ceil(opQty))
    );
  }

  if (hasPackageRatio && (opUnit || packageUnit || splitUnit)) {
    let opSplit: number | undefined;
    if (opUnit && splitUnit && opUnit === splitUnit) {
      opSplit = opQty;
    } else if (opUnit && packageUnit && opUnit === packageUnit) {
      opSplit = opQty * packageRatio;
    } else if (!opUnit && splitUnit) {
      opSplit = opQty;
    }

    let perTrace: number | undefined;
    if (traceUnit && packageUnit && traceUnit === packageUnit) {
      perTrace = packageRatio;
    } else if (traceUnit && splitUnit && traceUnit === splitUnit) {
      perTrace = 1;
    }

    if (opSplit !== undefined && perTrace !== undefined) {
      return (
        computeRequiredTraceCount({
          operationQty: opQty,
          operationUnitCode: opUnit || undefined,
          traceUnitCode: traceUnit || undefined,
          operationSplitQty: opSplit,
          perTraceSplitQty: perTrace,
          mode: 'outbound',
        }) ?? Math.max(1, Math.ceil(opSplit / perTrace))
      );
    }
  }

  return (
    computeRequiredTraceCount({
      operationQty: opQty,
      operationUnitCode: opUnit || undefined,
      traceUnitCode: traceUnit || opUnit || undefined,
      mode: 'outbound',
    }) ?? Math.max(1, Math.ceil(opQty))
  );
}
