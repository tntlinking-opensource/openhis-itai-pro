import type { DoctorStationMedicationUnitBandDto } from '#/api/doctor-station/advice.types';

import { isPackageBoundOrderCalcRule, OrderCalcRuleCode } from '#/constants/domain/order-calc-rule';
import {
  defaultTotalAmountUnitCode,
  resolveBandKindByUnitCode,
  roundMedicationQty,
  unitCodeByBandKind,
} from '#/utils/doctor-station/medication-unit-band-query';
import { convertInboundQtyBetweenBandKinds } from '#/utils/inventory/inbound-product-unit-bands';

/** 拆分属性 → 默认总量计量带：包装类规则默认 PACKAGE，拆零类默认 SPLIT；未配置时与默认取整规则一致用 SPLIT。 */
export function preferredTotalUnitBandKindFromOrderCalcRule(
  ruleCode: string | undefined,
): 'PACKAGE' | 'SPLIT' {
  if (isPackageBoundOrderCalcRule(ruleCode)) {
    return 'PACKAGE';
  }
  return 'SPLIT';
}

export interface MedicationTotalAmountCalcResult {
  amount: number;
  unitCode: string;
}

function normalizeOrderCalcRuleCode(ruleCode: string | undefined): string {
  const code = String(ruleCode ?? '').trim();
  return code || OrderCalcRuleCode.TOTAL_SPLIT_CEIL;
}

/** 规则天然输出计量带：1/3 → SPLIT，2/4 → PACKAGE（无 PACKAGE 带时回落 SALES）。 */
function ruleOutputBandKind(
  ruleCode: string,
  bands: DoctorStationMedicationUnitBandDto[] | undefined,
): string {
  if (isPackageBoundOrderCalcRule(ruleCode)) {
    if (unitCodeByBandKind(bands, 'PACKAGE')) return 'PACKAGE';
    if (unitCodeByBandKind(bands, 'SALES')) return 'SALES';
    return 'SPLIT';
  }
  return 'SPLIT';
}

function resolvePackageLikeBandKind(
  ruleCode: string,
  bands: DoctorStationMedicationUnitBandDto[] | undefined,
): string {
  const output = ruleOutputBandKind(ruleCode, bands);
  if (output === 'PACKAGE' || output === 'SALES') return output;
  return unitCodeByBandKind(bands, 'PACKAGE') ? 'PACKAGE' : 'SALES';
}

function unitCodeForBandKind(
  bands: DoctorStationMedicationUnitBandDto[] | undefined,
  kind: string,
): string {
  const k = kind.trim().toUpperCase();
  const direct = unitCodeByBandKind(bands, k);
  if (direct) return direct;
  if (k === 'SPLIT') return unitCodeByBandKind(bands, 'SPLIT') || '';
  if (k === 'PACKAGE') return unitCodeByBandKind(bands, 'PACKAGE') || '';
  if (k === 'SALES') return unitCodeByBandKind(bands, 'SALES') || '';
  return '';
}

/** 拆分属性对应的默认总量 unitCode。 */
export function naturalTotalAmountUnitCode(
  orderCalcRuleCode: string | undefined,
  bands: DoctorStationMedicationUnitBandDto[] | undefined,
): string {
  const kind = preferredTotalUnitBandKindFromOrderCalcRule(orderCalcRuleCode);
  return defaultTotalAmountUnitCode(bands, kind);
}

/** 用药数量向上取整到整单位（盒/片等），小于 1 的小数须进位为 1。 */
function ceilMedicationQty(n: number): number {
  if (!Number.isFinite(n) || n <= 0) return 0;
  return Math.ceil(n);
}

/** 按拆分属性在指定计量带（SPLIT / PACKAGE / SALES）上计算总量。 */
function calcQtyInBandKind(params: {
  rule: string;
  singleDose: number;
  count: number;
  bands: DoctorStationMedicationUnitBandDto[] | undefined;
  outputKind: string;
}): number | undefined {
  const { singleDose, count, bands } = params;
  const rule = params.rule;
  const kind = params.outputKind.trim().toUpperCase();
  const rawSplit = singleDose * count;
  const splitCode = unitCodeByBandKind(bands, 'SPLIT');
  const packageLikeKind = resolvePackageLikeBandKind(rule, bands);
  const canUsePackageLike =
    Boolean(splitCode) && (packageLikeKind === 'PACKAGE' || packageLikeKind === 'SALES');

  if (kind === 'SPLIT') {
    switch (rule) {
      case OrderCalcRuleCode.SINGLE_SPLIT_CEIL:
        return ceilMedicationQty(singleDose) * count;
      case OrderCalcRuleCode.PACKAGE_NO_SPLIT:
        return roundMedicationQty(rawSplit);
      case OrderCalcRuleCode.PACKAGE_SINGLE_CEIL:
        return ceilMedicationQty(singleDose) * count;
      case OrderCalcRuleCode.TOTAL_SPLIT_CEIL:
      default:
        return ceilMedicationQty(rawSplit);
    }
  }

  if (kind === 'PACKAGE' || kind === 'SALES') {
    const pkgKind =
      kind === 'SALES' && unitCodeByBandKind(bands, 'SALES') ? 'SALES' : packageLikeKind;
    if (!canUsePackageLike) {
      return calcQtyInBandKind({ ...params, outputKind: 'SPLIT' });
    }
    switch (rule) {
      case OrderCalcRuleCode.SINGLE_SPLIT_CEIL: {
        const splitQty = ceilMedicationQty(singleDose) * count;
        const converted = convertInboundQtyBetweenBandKinds(
          splitQty,
          'SPLIT',
          pkgKind,
          bands ?? [],
        );
        return converted === undefined ? undefined : roundMedicationQty(converted);
      }
      case OrderCalcRuleCode.PACKAGE_NO_SPLIT: {
        const inPkg = convertInboundQtyBetweenBandKinds(rawSplit, 'SPLIT', pkgKind, bands ?? []);
        if (inPkg === undefined) return undefined;
        return ceilMedicationQty(inPkg);
      }
      case OrderCalcRuleCode.PACKAGE_SINGLE_CEIL: {
        const splitTotal = ceilMedicationQty(singleDose) * count;
        const inPkg = convertInboundQtyBetweenBandKinds(splitTotal, 'SPLIT', pkgKind, bands ?? []);
        if (inPkg === undefined) return undefined;
        return ceilMedicationQty(inPkg);
      }
      case OrderCalcRuleCode.TOTAL_SPLIT_CEIL:
      default: {
        const splitQty = ceilMedicationQty(rawSplit);
        const converted = convertInboundQtyBetweenBandKinds(
          splitQty,
          'SPLIT',
          pkgKind,
          bands ?? [],
        );
        return converted === undefined ? undefined : roundMedicationQty(converted);
      }
    }
  }

  return undefined;
}

/**
 * 按拆分属性（OrderCalcRule）计算总量，结果落在规则对应的天然计量带（1/3→片，2/4→盒）。
 * 单次剂量单位为拆零（SPLIT）；executionCount 为频次计划执行次数。
 */
export function calcMedicationTotalAmountByOrderCalcRule(params: {
  orderCalcRuleCode: string | undefined;
  singleDose: number;
  executionCount: number;
  bands: DoctorStationMedicationUnitBandDto[] | undefined;
}): MedicationTotalAmountCalcResult | undefined {
  const { singleDose, executionCount, bands } = params;
  const rule = normalizeOrderCalcRuleCode(params.orderCalcRuleCode);
  const count = Math.trunc(executionCount);
  if (count < 1 || !Number.isFinite(singleDose) || singleDose <= 0) return undefined;

  const outputKind = ruleOutputBandKind(rule, bands);
  const qtyInOutputKind = calcQtyInBandKind({ rule, singleDose, count, bands, outputKind });
  if (qtyInOutputKind === undefined) return undefined;

  const unitCode =
    unitCodeForBandKind(bands, outputKind) || naturalTotalAmountUnitCode(rule, bands);
  if (!unitCode) return undefined;

  return {
    amount: roundMedicationQty(qtyInOutputKind),
    unitCode,
  };
}

/**
 * 按拆分属性重算总量并落在指定 unitCode 对应计量带（切换总量单位时用，避免对当前总量做乘除丢精度）。
 */
export function calcMedicationTotalAmountInTargetUnit(params: {
  orderCalcRuleCode: string | undefined;
  singleDose: number;
  executionCount: number;
  bands: DoctorStationMedicationUnitBandDto[] | undefined;
  targetUnitCode: string;
  knownUnitCodes?: {
    packageCode?: string;
    salesCode?: string;
    splitCode?: string;
  };
}): MedicationTotalAmountCalcResult | undefined {
  const target = String(params.targetUnitCode ?? '').trim();
  if (!target) return undefined;

  const natural = calcMedicationTotalAmountByOrderCalcRule(params);
  if (!natural) return undefined;
  if (target === natural.unitCode) return natural;

  const rule = normalizeOrderCalcRuleCode(params.orderCalcRuleCode);
  const count = Math.trunc(params.executionCount);
  const singleDose = params.singleDose;
  if (count < 1 || !Number.isFinite(singleDose) || singleDose <= 0) return undefined;

  const targetKind = resolveBandKindByUnitCode(target, params.bands, params.knownUnitCodes);
  if (!targetKind) return undefined;

  const qty = calcQtyInBandKind({
    rule,
    singleDose,
    count,
    bands: params.bands,
    outputKind: targetKind,
  });
  if (qty === undefined) return undefined;

  return {
    amount: roundMedicationQty(qty),
    unitCode: target,
  };
}
