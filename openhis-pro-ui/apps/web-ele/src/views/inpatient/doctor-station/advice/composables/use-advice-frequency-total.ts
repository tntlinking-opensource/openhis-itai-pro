import type { AdviceDrugItem, InpatientAdviceTherapyType } from '../services/advice.types';

import type { ComputedRef, Ref } from 'vue';
import { ref, watch } from 'vue';

import { useDebounceFn } from '@vueuse/core';

import { httpComputeInpatientMedicationFrequencyExecutions } from '#/api/inpatient/doctor-station/inpatient-doctor-station-advice.http';

import { isInpatientLongTermMedicationTherapy } from '../services/advice-row';
import { convertSplitQtyToTotalUnitCode } from '../services/medication-unit-bands';

/** 用药计划起点：本地「次日 00:00:00」ISO 串。 */
function startAtNextLocalDayMidnightIso(): string {
  const d = new Date();
  d.setDate(d.getDate() + 1);
  d.setHours(0, 0, 0, 0);
  return d.toISOString();
}

/**
 * 频次 × 用药天数自动推算总量：依赖后端 computeMedicationFrequencyExecutions。
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

  /** 是否按频次+用药天数自动推算总量（默认关）。 */
  const autoCalcTotalAmount = ref(false);

  async function recalcRowTotalAmountByFrequencyPlan(index: number) {
    if (isTcm.value || !autoCalcTotalAmount.value) return;
    if (therapyType && isInpatientLongTermMedicationTherapy(therapyType.value)) return;
    const item = drugItems.value[index];
    if (!item?.selectedId?.trim()) return;
    const rateCode = String(item.frequency ?? '').trim();
    const days = Math.trunc(Number(item.medicationDays));
    if (!rateCode || days < 1) return;
    const singleMain = Number(item.singleDose);
    if (!Number.isFinite(singleMain) || singleMain <= 0) return;
    try {
      const res = await httpComputeInpatientMedicationFrequencyExecutions({
        rateCode,
        medicationDays: days,
        startAt: startAtNextLocalDayMidnightIso(),
      });
      const count = Math.trunc(Number(res.executionCount ?? 0));
      if (count < 1) return;
      const totalSplit = Math.ceil(count * singleMain * 1_000_000) / 1_000_000;
      const row = drugItems.value[index];
      const unitNow = String(row?.totalAmountUnit ?? '').trim();
      const out = convertSplitQtyToTotalUnitCode(totalSplit, unitNow, row?.unitBands);
      setField(index, 'totalAmount', out);
    } catch {
      /* 频次未维护时不打断录药 */
    }
  }

  const debouncedRecalcFrequencyTotal = useDebounceFn((index: number) => {
    void recalcRowTotalAmountByFrequencyPlan(index);
  }, 400);

  watch(autoCalcTotalAmount, (on) => {
    if (!on || isTcm.value) return;
    drugItems.value.forEach((item, index) => {
      if (item.selectedId?.trim() && item.expanded) {
        void recalcRowTotalAmountByFrequencyPlan(index);
      }
    });
  });

  return {
    autoCalcTotalAmount,
    debouncedRecalcFrequencyTotal,
    recalcRowTotalAmountByFrequencyPlan,
  };
}
