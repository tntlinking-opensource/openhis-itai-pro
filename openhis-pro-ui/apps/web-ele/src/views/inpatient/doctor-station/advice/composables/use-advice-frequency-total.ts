import type { ComputedRef, Ref } from 'vue';

import type { AdviceDrugItem, InpatientAdviceTherapyType } from '../services/advice.types';

import { ref, watch } from 'vue';

import { useDebounceFn } from '@vueuse/core';

import { httpComputeInpatientMedicationFrequencyExecutions } from '#/api/inpatient/doctor-station/inpatient-doctor-station-advice.http';
import {
  calcMedicationTotalAmountByOrderCalcRule,
  calcMedicationTotalAmountInTargetUnit,
} from '#/utils/doctor-station/medication-order-total-calc';

import { isInpatientLongTermMedicationTherapy } from '../services/advice-row';
import {
  doseConvertRatioSplitToDose,
  resolveAdviceLineUnitPrice,
  roundMedicationQty,
} from '../services/medication-unit-bands';

/** 用药计划起点：本地「次日 00:00:00」ISO 串。 */
function startAtNextLocalDayMidnightIso(): string {
  const d = new Date();
  d.setDate(d.getDate() + 1);
  d.setHours(0, 0, 0, 0);
  return d.toISOString();
}

/**
 * 住院西药临时医嘱自动推算总量：频次/用药天数变更时调住院频次接口算执行次数；总量按拆分属性取整。
 * 长期医嘱不推算总量。
 */
export function useAdviceFrequencyTotal(options: {
  drugItems: Ref<AdviceDrugItem[]>;
  isTcm: ComputedRef<boolean>;
  setField: <K extends keyof AdviceDrugItem>(
    index: number,
    key: K,
    value: AdviceDrugItem[K],
  ) => void;
  /** 顶栏长临选择：长期医嘱不推算总量 */
  therapyType?: Ref<InpatientAdviceTherapyType>;
}) {
  const { drugItems, isTcm, setField, therapyType } = options;

  /** 是否按频次+用药天数自动推算总量（住院默认开，与门诊一致；长期医嘱不推算）。 */
  const autoCalcTotalAmount = ref(true);

  function isLongTermBlocked(): boolean {
    return Boolean(therapyType && isInpatientLongTermMedicationTherapy(therapyType.value));
  }

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
    setField(index, 'totalAmount', amount);
    setField(index, 'totalAmountUnit', unitCode);
    const item = drugItems.value[index];
    if (!item) return;
    const linePrice = resolveAdviceLineUnitPrice({
      bands: item.unitBands,
      catalogUnitPrice: item.catalogUnitPrice,
      totalAmountUnit: unitCode,
    });
    if (linePrice !== undefined) {
      setField(index, 'selectedDrugPrice', linePrice);
    }
  }

  function recalcRowTotalAmountForTargetUnit(index: number, targetUnitCode: string) {
    if (isTcm.value || !autoCalcTotalAmount.value || isLongTermBlocked()) return;
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
    applyAutoCalcResult(index, out.amount, out.unitCode);
  }

  function recalcRowTotalAmountFromExecutionCount(index: number, executionCountOverride?: number) {
    if (isTcm.value || !autoCalcTotalAmount.value || isLongTermBlocked()) return;
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
    applyAutoCalcResult(index, out.amount, out.unitCode);
  }

  async function refreshRowFrequencyExecutionCount(index: number): Promise<number> {
    if (isTcm.value || !autoCalcTotalAmount.value || isLongTermBlocked()) return 0;
    const item = drugItems.value[index];
    if (!item?.selectedId?.trim()) return 0;
    const rateCode = String(item.frequency ?? '').trim();
    const days = Math.trunc(Number(item.medicationDays));
    if (!rateCode || days < 1) {
      setField(index, 'frequencyExecutionCount', undefined);
      return 0;
    }
    try {
      const res = await httpComputeInpatientMedicationFrequencyExecutions({
        rateCode,
        medicationDays: days,
        startAt: startAtNextLocalDayMidnightIso(),
      });
      const count = Math.trunc(Number(res.executionCount ?? 0));
      if (count < 1) {
        setField(index, 'frequencyExecutionCount', undefined);
        return 0;
      }
      setField(index, 'frequencyExecutionCount', count);
      recalcRowTotalAmountFromExecutionCount(index, count);
      return count;
    } catch {
      return 0;
    }
  }

  async function ensureFrequencyCountAndRecalcTotal(index: number) {
    if (isTcm.value || !autoCalcTotalAmount.value || isLongTermBlocked()) return;
    const item = drugItems.value[index];
    if (!item?.selectedId?.trim() || item.totalAmountManualOverride) return;
    const cached = Math.trunc(Number(item.frequencyExecutionCount ?? 0));
    if (cached >= 1) {
      recalcRowTotalAmountFromExecutionCount(index, cached);
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

  /** 兼容旧调用名 */
  const debouncedRecalcFrequencyTotal = debouncedRefreshFrequencyExecutionCount;

  watch(autoCalcTotalAmount, (on) => {
    if (!on || isTcm.value || isLongTermBlocked()) return;
    drugItems.value.forEach((item, index) => {
      if (!item.selectedId?.trim() || !item.expanded) return;
      setField(index, 'totalAmountManualOverride', false);
      void refreshRowFrequencyExecutionCount(index);
    });
  });

  return {
    autoCalcTotalAmount,
    debouncedRecalcFrequencyTotal,
    debouncedRefreshFrequencyExecutionCount,
    debouncedEnsureFrequencyCountAndRecalcTotal,
    refreshRowFrequencyExecutionCount,
    recalcRowTotalAmountForTargetUnit,
    recalcRowTotalAmountFromExecutionCount,
  };
}
