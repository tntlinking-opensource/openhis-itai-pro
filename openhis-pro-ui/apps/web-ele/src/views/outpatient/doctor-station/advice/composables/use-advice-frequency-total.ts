import type { ComputedRef, Ref } from 'vue';

import type { AdviceDrugItem } from '../services/advice.types';

import { ref, watch } from 'vue';

import { useDebounceFn } from '@vueuse/core';

import { resolveFrequencyExecutionCount } from '../services/medication-frequency-execution';
import {
  calcMedicationTotalAmountByOrderCalcRule,
  calcMedicationTotalAmountInTargetUnit,
} from '../services/medication-order-total-calc';
import {
  doseConvertRatioSplitToDose,
  resolveAdviceLineUnitPrice,
  roundMedicationQty,
} from '../services/medication-unit-bands';

/**
 * 门诊西药自动推算总量：频次/用药天数变更时调频次模块算执行次数；总量由前端按拆分属性取整。
 */
export function useAdviceFrequencyTotal(options: {
  drugItems: Ref<AdviceDrugItem[]>;
  isTcm: ComputedRef<boolean>;
  patchRow?: (index: number, patch: Partial<AdviceDrugItem>) => void;
  setField: <K extends keyof AdviceDrugItem>(
    index: number,
    key: K,
    value: AdviceDrugItem[K],
  ) => void;
}) {
  const { drugItems, isTcm, patchRow, setField } = options;

  /** 是否按频次+用药天数自动推算总量（门诊西药默认开）。 */
  const autoCalcTotalAmount = ref(true);

  /** 防止选药/改频次后过期的异步推算覆盖医生正在手工录入的总量。 */
  const refreshSeqByIndex = new Map<number, number>();

  function isRowFrequencyRefreshStale(index: number, seqAtStart: number) {
    return (refreshSeqByIndex.get(index) ?? 0) !== seqAtStart;
  }

  /** 单次剂量（拆零 SPLIT 基准数量）；优先 singleDose，否则由剂量侧换算。 */
  function resolveRowSingleDoseSplitQty(item: AdviceDrugItem): number | undefined {
    const sd = Number(item.singleDose);
    if (Number.isFinite(sd) && sd > 0) return sd;
    const k =
      item.doseBandConvertK !== undefined &&
      Number.isFinite(item.doseBandConvertK) &&
      item.doseBandConvertK > 0
        ? item.doseBandConvertK
        : doseConvertRatioSplitToDose(item.unitBands);
    const dq = Number(item.doseConvertRatio);
    if (k !== undefined && Number.isFinite(k) && k > 0 && Number.isFinite(dq) && dq > 0) {
      return roundMedicationQty(dq / k);
    }
    return undefined;
  }

  function applyAutoCalcResult(index: number, amount: number, unitCode: string) {
    const item = drugItems.value[index];
    if (!item || item.totalAmountManualOverride) return;
    const linePrice = resolveAdviceLineUnitPrice({
      bands: item.unitBands,
      catalogUnitPrice: item.catalogUnitPrice,
      totalAmountUnit: unitCode,
    });
    const patch: Partial<AdviceDrugItem> = {
      totalAmount: amount,
      totalAmountUnit: unitCode,
    };
    if (linePrice !== undefined) {
      patch.selectedDrugPrice = linePrice;
    }
    if (patchRow) {
      patchRow(index, patch);
      return;
    }
    setField(index, 'totalAmount', amount);
    setField(index, 'totalAmountUnit', unitCode);
    if (linePrice !== undefined) {
      setField(index, 'selectedDrugPrice', linePrice);
    }
  }

  /** 切换总量单位：从单次剂量+执行次数重算，避免对当前总量做乘除丢精度。 */
  function recalcRowTotalAmountForTargetUnit(index: number, targetUnitCode: string) {
    if (isTcm.value || !autoCalcTotalAmount.value) return;
    const item = drugItems.value[index];
    if (!item?.selectedId?.trim() || item.totalAmountManualOverride) return;
    const count = Math.trunc(Number(item.frequencyExecutionCount ?? 0));
    if (count < 1) return;
    const singleMain = resolveRowSingleDoseSplitQty(item);
    if (singleMain === undefined || singleMain <= 0) return;
    const target = String(targetUnitCode ?? '').trim();
    if (!target) return;
    const out = calcMedicationTotalAmountInTargetUnit({
      orderCalcRuleCode: item.partAttributeEnum,
      singleDose: singleMain,
      executionCount: count,
      bands: item.unitBands,
      targetUnitCode: target,
      knownUnitCodes: {
        packageCode: item.totalUnitPackageCode,
        salesCode: item.totalUnitSalesCode,
        splitCode: item.totalUnitSplitCode,
      },
    });
    if (out === undefined) return;
    if (drugItems.value[index]?.totalAmountManualOverride) return;
    applyAutoCalcResult(index, out.amount, out.unitCode);
  }

  /** 用执行次数在前端重算总量（不调接口）。 */
  function recalcRowTotalAmountFromExecutionCount(index: number, executionCountOverride?: number) {
    if (isTcm.value || !autoCalcTotalAmount.value) return;
    const item = drugItems.value[index];
    if (!item?.selectedId?.trim() || item.totalAmountManualOverride) return;
    const count = Math.trunc(Number(executionCountOverride ?? item.frequencyExecutionCount ?? 0));
    if (count < 1) return;
    const singleMain = resolveRowSingleDoseSplitQty(item);
    if (singleMain === undefined || singleMain <= 0) return;
    const out = calcMedicationTotalAmountByOrderCalcRule({
      orderCalcRuleCode: item.partAttributeEnum,
      singleDose: singleMain,
      executionCount: count,
      bands: item.unitBands,
    });
    if (out === undefined) return;
    if (drugItems.value[index]?.totalAmountManualOverride) return;
    applyAutoCalcResult(index, out.amount, out.unitCode);
  }

  /** 频次或用药天数变更：调频次模块刷新执行次数并顺带重算总量。 */
  async function refreshRowFrequencyExecutionCount(index: number): Promise<number> {
    if (isTcm.value || !autoCalcTotalAmount.value) return 0;
    const item = drugItems.value[index];
    if (!item?.selectedId?.trim()) return 0;
    const rateCode = String(item.frequency ?? '').trim();
    const days = Math.trunc(Number(item.medicationDays));
    // NaN < 1 为 false；天数清空/非法时勿调频次接口
    if (!rateCode || !Number.isFinite(days) || days < 1) {
      setField(index, 'frequencyExecutionCount', undefined);
      return 0;
    }
    const seqAtStart = refreshSeqByIndex.get(index) ?? 0;
    const count = await resolveFrequencyExecutionCount({ rateCode, medicationDays: days });
    if (isRowFrequencyRefreshStale(index, seqAtStart)) return 0;
    if (isTcm.value || !autoCalcTotalAmount.value) return 0;
    if (drugItems.value[index]?.totalAmountManualOverride) return 0;
    if (count < 1) {
      setField(index, 'frequencyExecutionCount', undefined);
      return 0;
    }
    setField(index, 'frequencyExecutionCount', count);
    recalcRowTotalAmountFromExecutionCount(index, count);
    return count;
  }

  /** 单次剂量等变更：若无执行次数则先拉频次，再前端重算总量。 */
  async function ensureFrequencyCountAndRecalcTotal(index: number) {
    if (isTcm.value || !autoCalcTotalAmount.value) return;
    const item = drugItems.value[index];
    if (!item?.selectedId?.trim()) return;
    const manualOverride = Boolean(item.totalAmountManualOverride);
    const cached = Math.trunc(Number(item.frequencyExecutionCount ?? 0));
    if (cached >= 1) {
      if (!manualOverride) {
        recalcRowTotalAmountFromExecutionCount(index, cached);
      }
      return;
    }
    await refreshRowFrequencyExecutionCount(index);
  }

  const debouncedRefreshFrequencyExecutionCount = useDebounceFn((index: number) => {
    void refreshRowFrequencyExecutionCount(index);
  }, 400);

  const debouncedEnsureFrequencyCountAndRecalcTotal = useDebounceFn((index: number) => {
    void ensureFrequencyCountAndRecalcTotal(index);
  }, 400);

  function cancelDebounced(fn: unknown) {
    if (fn && typeof fn === 'object' && 'cancel' in fn && typeof fn.cancel === 'function') {
      fn.cancel();
    }
  }

  function invalidateRowFrequencyRefresh(index: number) {
    refreshSeqByIndex.set(index, (refreshSeqByIndex.get(index) ?? 0) + 1);
    cancelDebounced(debouncedRefreshFrequencyExecutionCount);
    cancelDebounced(debouncedEnsureFrequencyCountAndRecalcTotal);
  }

  /** 总量输入框获焦时提前锁定，避免异步推算与用户手工录入争抢同一字段。 */
  function markRowTotalAmountManualOverride(index: number) {
    invalidateRowFrequencyRefresh(index);
    setField(index, 'totalAmountManualOverride', true);
  }

  watch(autoCalcTotalAmount, (on) => {
    if (isTcm.value) return;
    drugItems.value.forEach((item, index) => {
      if (!item.selectedId?.trim() || !item.expanded) return;
      if (!on) {
        invalidateRowFrequencyRefresh(index);
        setField(index, 'totalAmountManualOverride', true);
        return;
      }
      invalidateRowFrequencyRefresh(index);
      setField(index, 'totalAmountManualOverride', false);
      void refreshRowFrequencyExecutionCount(index);
    });
  });

  return {
    autoCalcTotalAmount,
    debouncedRefreshFrequencyExecutionCount,
    debouncedEnsureFrequencyCountAndRecalcTotal,
    invalidateRowFrequencyRefresh,
    markRowTotalAmountManualOverride,
    refreshRowFrequencyExecutionCount,
    recalcRowTotalAmountFromExecutionCount,
    recalcRowTotalAmountForTargetUnit,
  };
}
